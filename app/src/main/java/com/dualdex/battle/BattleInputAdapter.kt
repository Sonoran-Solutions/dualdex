package com.dualdex.battle

import com.dualdex.emulator.ControllerInputRouter
import com.dualdex.emulator.InputManager
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.pokemon.ParsedPokemon
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

/**
 * Interface for dispatching emulated controller button presses.
 * Decoupled to enable 100% deterministic testing without an Android device or emulator.
 */
fun interface BattleInputDispatcher {
    val stateGeneration: Long get() = 0L
    suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean
    suspend fun sendFrameButton(buttonMask: Int, settleFrames: Int): Boolean = false
    suspend fun waitFrames(frames: Int): Boolean = false
    suspend fun <T> withExclusiveAutomation(block: suspend () -> T): T =
        ControllerInputRouter.withExclusiveAutomation(block = block)
}

/**
 * Production dispatcher that writes button masks through ControllerInputRouter.
 * Move macros suppress physical edges until the session ends and held buttons are released.
 */
class DefaultBattleInputDispatcher(
    private val coordinator: () -> LibretroCoreCoordinator = { LibretroCoreCoordinator.defaultInstance }
) : BattleInputDispatcher {
    override val stateGeneration: Long get() = coordinator().stateGeneration
    // Accessed only by the macro owning the router's automation mutex.
    private var inputSession: Pair<LibretroCoreCoordinator, Long>? = null

    private companion object {
        const val WRITE_ATTEMPTS = 3
        // No button is held until the press write succeeds: allow about one 1x frame per attempt.
        const val PRESS_WRITE_TIMEOUT_MS = 17L // Three attempts: at most 51 ms of lock waits.
        // Zero also initializes the session; keep release waits short to avoid stretching a held edge.
        const val RELEASE_WRITE_TIMEOUT_MS = 2L // Three attempts: at most 6 ms, then unlocked zero.
    }

    override suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean {
        return ControllerInputRouter.sendAutomatedButton(buttonMask, durationMs, delayMs)
    }

    override suspend fun waitFrames(frames: Int): Boolean {
        require(frames >= 0)
        val core = inputSession?.first ?: coordinator()
        if (!core.emulationRunning || coordinator() !== core) return false
        val generation = inputSession?.second ?: core.stateGeneration
        if (core.stateGeneration != generation) return false
        val target = core.completedFrames + frames
        return withTimeoutOrNull(2000L) {
            core.frameCompletions.first {
                it >= target || !core.emulationRunning || coordinator() !== core || core.stateGeneration != generation
            }
            core.emulationRunning && coordinator() === core && core.stateGeneration == generation &&
                core.completedFrames >= target
        } ?: false
    }

    private fun writeMask(core: LibretroCoreCoordinator, mask: Int, generation: Long) {
        val timeoutMs = if (mask == 0) RELEASE_WRITE_TIMEOUT_MS else PRESS_WRITE_TIMEOUT_MS
        try {
            repeat(WRITE_ATTEMPTS) { attempt ->
                try {
                    core.executeExclusive(timeoutMs) {
                        // Check under the same lock as load/switch: a queued press cannot cross a restore.
                        check(mask == 0 || (coordinator() === core && core.stateGeneration == generation)) {
                            "Core state changed during automated input"
                        }
                        core.setInputButtons(mask)
                    }
                    return
                } catch (e: LibretroCoreCoordinator.LockTimeoutException) {
                    if (attempt + 1 == WRITE_ATTEMPTS) throw e
                }
            }
        } catch (e: Exception) {
            // A stalled core must not retain automation, even if its lock cannot be acquired.
            if (mask == 0) core.setInputButtons(0)
            throw e
        }
    }

    override suspend fun <T> withExclusiveAutomation(block: suspend () -> T): T {
        val core = coordinator()
        val generation = core.stateGeneration
        return ControllerInputRouter.withExclusiveAutomation({ writeMask(core, it, generation) }) {
            inputSession = core to generation
            try { block() } finally { inputSession = null }
        }
    }

    override suspend fun sendFrameButton(buttonMask: Int, settleFrames: Int): Boolean {
        val (core, generation) = inputSession ?: coordinator().let { it to it.stateGeneration }
        return try {
            ControllerInputRouter.sendAutomatedFrameButton(buttonMask, settleFrames, ::waitFrames) { mask ->
                // Router state is unlocked before waiting for a boundary between retro_run calls.
                writeMask(core, mask, generation)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Rich result types for transactional battle input operations.
 */
sealed class BattleInputResult {
    data object Success : BattleInputResult()
    data class UnsupportedProfile(val reason: String) : BattleInputResult()
    data class UnknownUiState(val state: BattleUiState) : BattleInputResult()
    data class CursorUnavailable(val requiredCursor: String) : BattleInputResult()
    data class UnexpectedTransition(val expected: BattleUiState, val actual: BattleUiState) : BattleInputResult()
    data class IllegalTarget(val reason: String) : BattleInputResult()
    data object Busy : BattleInputResult()
    data object Paused : BattleInputResult()
    data object TargetSelectionRequired : BattleInputResult()
    data object MoveRefused : BattleInputResult()
    data object ConfirmNotRegistered : BattleInputResult()
    data class Timeout(val message: String) : BattleInputResult()
    data class Cancelled(val reason: String) : BattleInputResult()

    val isSuccess: Boolean get() = this is Success
}

/**
 * Controller-driven adapter for Enhanced Battle Console interactive actions.
 * Translates UI taps on moves and party members into standard emulated button sequences (D-pad, A, B).
 *
 * Enforces strict fail-closed safety:
 * - Unknown state => read-only / rejected
 * - Unverified profile => read-only / rejected
 * - Cursor null or unobserved => rejected (never defaults to 0)
 * - Transitions must be validated before confirming actions
 * - Physical state is recorded but suppressed during the move transaction
 */
class BattleInputAdapter(
    private val dispatcher: BattleInputDispatcher = DefaultBattleInputDispatcher(),
    private val isPaused: () -> Boolean = { !LibretroCoreCoordinator.defaultInstance.emulationRunning },
    private val frameReader: () -> Long = { LibretroCoreCoordinator.defaultInstance.completedFrames },
    private val macroDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val stateReader: (() -> BattleUiSnapshot?)? = null
) {
    private val actionMutex = Mutex()

    companion object {
        const val BTN_PRESS_DURATION_MS = 50L
        const val BTN_INTER_DELAY_MS = 60L
        const val MENU_TRANSITION_DELAY_MS = 100L
        const val TRANSITION_POLL_INTERVAL_MS = 25L
        const val TRANSITION_TIMEOUT_MS = 500L
        const val DIRECTION_SETTLE_FRAMES = 20
        const val WATCHDOG_FRAMES = 1200L
        const val OBSERVATION_FRAMES = 120
        const val READ_ATTEMPTS = 3
        const val CONFIRM_REGISTRATION_FRAMES = 6L
    }

    suspend fun executeSelectMove(slot: Int, currentUi: BattleUiSnapshot): BattleInputResult {
        // A second tap is rejected, never queued against a snapshot from the previous turn.
        if (!actionMutex.tryLock()) return BattleInputResult.Busy
        try {
            val generation = dispatcher.stateGeneration
            return withContext(macroDispatcher) {
                withTimeoutOrNull(20_000L) {
                    try {
                        executeSelectMoveInternal(slot, currentUi, generation)
                    } catch (abort: MoveMacroAbort) {
                        abort.result
                    } catch (_: ControllerInputRouter.PhysicalButtonHeld) {
                        BattleInputResult.Cancelled("Physical button held")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        BattleInputResult.Timeout("Core input session unavailable")
                    }
                } ?: BattleInputResult.Timeout("Move-selection watchdog expired")
            }
        } finally {
            actionMutex.unlock()
        }
    }

    private class MoveMacroAbort(val result: BattleInputResult) : RuntimeException()

    private suspend fun executeSelectMoveInternal(
        slot: Int, currentUi: BattleUiSnapshot, generation: Long
    ): BattleInputResult {
        if (slot !in 0..3) return BattleInputResult.IllegalTarget("Move slot $slot out of bounds (0..3)")
        if (currentUi.state == BattleUiState.ANIMATION_OR_TEXT) return BattleInputResult.Busy
        if (!currentUi.capabilities.selectMove) return BattleInputResult.UnsupportedProfile("Move selection is not verified")
        if (currentUi.state != BattleUiState.COMMAND_MENU && currentUi.state != BattleUiState.MOVE_MENU) {
            return BattleInputResult.UnknownUiState(currentUi.state)
        }
        fun cursor(ui: BattleUiSnapshot) = when (ui.state) {
            BattleUiState.COMMAND_MENU -> ui.selectedActionIndex
            BattleUiState.MOVE_MENU -> ui.selectedMoveIndex
            else -> null
        }
        if (cursor(currentUi) !in 0..3) return BattleInputResult.CursorUnavailable("Battle menu cursor is unknown")
        if (!currentUi.inputSafe) return BattleInputResult.UnknownUiState(currentUi.state)
        if (currentUi.playerBattlerCount != 1) return BattleInputResult.UnsupportedProfile("Move selection requires singles")
        if (currentUi.battlerIndex !in 0..3) return BattleInputResult.CursorUnavailable("Player battler is unknown")
        if (isPaused()) return BattleInputResult.Paused
        val reader = stateReader ?: return BattleInputResult.CursorUnavailable("Direct core reader is unavailable")
        val startFrame = frameReader()
        fun abort(result: BattleInputResult): Nothing = throw MoveMacroAbort(result)
        fun running() {
            if (dispatcher.stateGeneration != generation) abort(BattleInputResult.Cancelled("Core state changed"))
            if (isPaused()) abort(BattleInputResult.Paused)
            if (frameReader() - startFrame >= WATCHDOG_FRAMES) {
                abort(BattleInputResult.Timeout("Move-selection frame watchdog expired"))
            }
        }
        suspend fun waitFrame() {
            running()
            val advanced = dispatcher.waitFrames(1)
            running()
            if (!advanced) abort(BattleInputResult.Timeout("Core frames unavailable"))
        }
        suspend fun read(): BattleUiSnapshot {
            repeat(READ_ATTEMPTS) { attempt ->
                running()
                reader()?.let { running(); return it }
                if (attempt + 1 < READ_ATTEMPTS) waitFrame()
            }
            abort(BattleInputResult.Timeout("Core battle UI read unavailable after $READ_ATTEMPTS attempts"))
        }
        fun authority(ui: BattleUiSnapshot) {
            if (ui.battlerIndex != currentUi.battlerIndex || ui.playerBattlerCount != 1 ||
                !ui.capabilities.selectMove) {
                abort(BattleInputResult.UnsupportedProfile("Battle input authority changed"))
            }
        }
        suspend fun menu(expected: BattleUiState): BattleUiSnapshot {
            val ui = read()
            if (ui.state != expected) abort(BattleInputResult.UnexpectedTransition(expected, ui.state))
            authority(ui)
            if (!ui.inputSafe) abort(BattleInputResult.UnsupportedProfile("Battle input authority changed"))
            if (cursor(ui) !in 0..3) abort(BattleInputResult.CursorUnavailable("Battle menu cursor is unknown"))
            return ui
        }
        suspend fun press(button: Int) {
            running()
            val settle = if (button == InputManager.BTN_A) 0 else DIRECTION_SETTLE_FRAMES
            if (!dispatcher.sendFrameButton(button, settle)) {
                running()
                abort(BattleInputResult.Timeout("Core frames or input dispatch unavailable"))
            }
            running()
        }
        suspend fun walk(state: BattleUiState, target: Int) {
            var ui = menu(state)
            // Use each observed cursor: only change the column/row that differs.
            for (bit in listOf(1, 2)) {
                val from = cursor(ui)!!
                if (from and bit == target and bit) continue
                val button = if (bit == 1) {
                    if (target and bit == 0) InputManager.BTN_LEFT else InputManager.BTN_RIGHT
                } else {
                    if (target and bit == 0) InputManager.BTN_UP else InputManager.BTN_DOWN
                }
                val expected = (from and bit.inv()) or (target and bit)
                press(button)
                ui = menu(state)
                if (cursor(ui) != expected) {
                    abort(BattleInputResult.CursorUnavailable("Directional press did not reach the expected cursor"))
                }
            }
            // This last direct read (initial or post-direction) is the sole authorization before A.
            if (cursor(ui) != target) abort(BattleInputResult.CursorUnavailable("Target cursor was not observed"))
        }

        return dispatcher.withExclusiveAutomation {
            if (currentUi.state == BattleUiState.COMMAND_MENU) {
                walk(BattleUiState.COMMAND_MENU, 0)
                press(InputManager.BTN_A)
                var observed = read()
                var frames = 0
                while (observed.state != BattleUiState.MOVE_MENU && frames < OBSERVATION_FRAMES) {
                    waitFrame()
                    frames++
                    observed = read()
                }
                if (observed.state != BattleUiState.MOVE_MENU) {
                    abort(BattleInputResult.UnexpectedTransition(BattleUiState.MOVE_MENU, observed.state))
                }
            }
            walk(BattleUiState.MOVE_MENU, slot)
            press(InputManager.BTN_A)
            val confirmStartFrame = frameReader()
            var nonMenuObserved = false
            for (frames in 0..OBSERVATION_FRAMES) {
                val observed = read()
                when (observed.state) {
                    BattleUiState.TURN_RUNNING -> {
                        authority(observed)
                        return@withExclusiveAutomation BattleInputResult.Success
                    }
                    BattleUiState.TARGET_SELECT -> {
                        authority(observed)
                        return@withExclusiveAutomation BattleInputResult.TargetSelectionRequired
                    }
                    BattleUiState.MOVE_MENU, BattleUiState.COMMAND_MENU -> {
                        authority(observed)
                        if (nonMenuObserved) return@withExclusiveAutomation BattleInputResult.MoveRefused
                        if (frameReader() - confirmStartFrame >= CONFIRM_REGISTRATION_FRAMES) {
                            return@withExclusiveAutomation BattleInputResult.ConfirmNotRegistered
                        }
                    }
                    BattleUiState.ANIMATION_OR_TEXT -> nonMenuObserved = true
                    else -> Unit // Unknown/unexpected state does not prove that A registered.
                }
                if (frames < OBSERVATION_FRAMES) waitFrame()
            }
            BattleInputResult.Timeout("Move acceptance was not observed")
        }
    }

    suspend fun selectMove(slot: Int, currentUi: BattleUiSnapshot): Boolean {
        return executeSelectMove(slot, currentUi).isSuccess
    }

    suspend fun executeSwitchPokemon(
        targetSlot: Int,
        currentUi: BattleUiSnapshot,
        party: List<ParsedPokemon>,
        activeSlot: Int = 0
    ): BattleInputResult {
        return actionMutex.withLock {
            executeSwitchPokemonInternal(targetSlot, currentUi, party, activeSlot)
        }
    }

    private suspend fun executeSwitchPokemonInternal(
        targetSlot: Int,
        currentUi: BattleUiSnapshot,
        party: List<ParsedPokemon>,
        activeSlot: Int
    ): BattleInputResult {
        if (targetSlot !in 0..5) {
            return BattleInputResult.IllegalTarget("Party slot $targetSlot out of bounds (0..5)")
        }

        if (targetSlot == activeSlot) {
            return BattleInputResult.IllegalTarget("Cannot switch to active Pokémon")
        }

        val targetMon = party.getOrNull(targetSlot)
        if (targetMon == null || targetMon.isEmpty || !targetMon.isValid || targetMon.currentHp <= 0) {
            return BattleInputResult.IllegalTarget("Target Pokémon is fainted or unavailable")
        }

        if (currentUi.state == BattleUiState.ANIMATION_OR_TEXT) {
            return BattleInputResult.Busy
        }

        if (!currentUi.capabilities.switchPokemon) {
            return BattleInputResult.UnsupportedProfile(
                "Party switching is not verified or supported for this ROM/profile"
            )
        }

        if (!currentUi.inputSafe) {
            return BattleInputResult.UnknownUiState(currentUi.state)
        }

        if (currentUi.state != BattleUiState.COMMAND_MENU && currentUi.state != BattleUiState.PARTY_MENU) {
            return BattleInputResult.UnknownUiState(currentUi.state)
        }

        if (currentUi.state == BattleUiState.COMMAND_MENU) {
            val commandCursor = currentUi.selectedActionIndex
                ?: return BattleInputResult.CursorUnavailable("Command menu cursor is unknown")

            // Navigate to POKEMON (slot 2 in 2x2 grid: x=0, y=1)
            navigateToCommandSlot(fromSlot = commandCursor, toSlot = 2)

            // Open Party Screen with A
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, MENU_TRANSITION_DELAY_MS)

            val observedPartyCursor: Int = if (stateReader != null) {
                val transitionedUi = waitForUiState(BattleUiState.PARTY_MENU, TRANSITION_TIMEOUT_MS)
                if (transitionedUi == null) {
                    val actual = stateReader.invoke()?.state ?: BattleUiState.UNKNOWN
                    return BattleInputResult.UnexpectedTransition(BattleUiState.PARTY_MENU, actual)
                }
                transitionedUi.selectedPartySlot
                    ?: return BattleInputResult.CursorUnavailable("Party cursor is unknown after transition")
            } else {
                currentUi.selectedPartySlot
                    ?: return BattleInputResult.CursorUnavailable("Party cursor is unknown")
            }

            navigateToPartySlot(fromSlot = observedPartyCursor, toSlot = targetSlot)

            // Press A to open action menu
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)

            // Press A to select SHIFT (top option)
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            return BattleInputResult.Success
        }

        if (currentUi.state == BattleUiState.PARTY_MENU) {
            val startSlot = currentUi.selectedPartySlot
                ?: return BattleInputResult.CursorUnavailable("Party cursor is unknown")

            navigateToPartySlot(fromSlot = startSlot, toSlot = targetSlot)

            // Press A to open action menu
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)

            // Press A to confirm SHIFT
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            return BattleInputResult.Success
        }

        return BattleInputResult.UnknownUiState(currentUi.state)
    }

    suspend fun switchPokemon(
        targetSlot: Int,
        currentUi: BattleUiSnapshot,
        party: List<ParsedPokemon>,
        activeSlot: Int = 0
    ): Boolean {
        return executeSwitchPokemon(targetSlot, currentUi, party, activeSlot).isSuccess
    }

    suspend fun cancel(): Boolean {
        return actionMutex.withLock {
            dispatcher.sendButton(InputManager.BTN_B, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
        }
    }

    private suspend fun waitForUiState(expected: BattleUiState, timeoutMs: Long): BattleUiSnapshot? {
        val reader = stateReader ?: return null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val snapshot = reader.invoke()
            if (snapshot?.state == expected) {
                return snapshot
            }
            delay(TRANSITION_POLL_INTERVAL_MS)
        }
        return null
    }

    private suspend fun navigateToCommandSlot(fromSlot: Int, toSlot: Int) {
        if (fromSlot == toSlot) return
        val fromX = fromSlot % 2
        val fromY = fromSlot / 2
        val toX = toSlot % 2
        val toY = toSlot / 2

        if (toX > fromX) dispatcher.sendButton(InputManager.BTN_RIGHT, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
        else if (toX < fromX) dispatcher.sendButton(InputManager.BTN_LEFT, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)

        if (toY > fromY) dispatcher.sendButton(InputManager.BTN_DOWN, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
        else if (toY < fromY) dispatcher.sendButton(InputManager.BTN_UP, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
    }

    private suspend fun navigateToPartySlot(fromSlot: Int, toSlot: Int) {
        if (toSlot > fromSlot) {
            repeat(toSlot - fromSlot) {
                dispatcher.sendButton(InputManager.BTN_DOWN, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            }
        } else if (toSlot < fromSlot) {
            repeat(fromSlot - toSlot) {
                dispatcher.sendButton(InputManager.BTN_UP, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            }
        }
    }
}
