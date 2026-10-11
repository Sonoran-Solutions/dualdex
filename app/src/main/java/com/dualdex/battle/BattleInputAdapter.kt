package com.dualdex.battle

import com.dualdex.emulator.ControllerInputRouter
import com.dualdex.emulator.InputManager
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.pokemon.ParsedPokemon
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
    suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean
    suspend fun sendFrameButton(buttonMask: Int, settleFrames: Int): Boolean = false
    suspend fun waitFrames(frames: Int): Boolean = false
}

/**
 * Production dispatcher that writes button masks through ControllerInputRouter.
 * Guarantees physical controller inputs are never cleared and automated sequences are serialized.
 */
class DefaultBattleInputDispatcher(
    private val coordinator: () -> LibretroCoreCoordinator = { LibretroCoreCoordinator.defaultInstance }
) : BattleInputDispatcher {
    override suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean {
        return ControllerInputRouter.sendAutomatedButton(buttonMask, durationMs, delayMs)
    }

    override suspend fun waitFrames(frames: Int): Boolean {
        val core = coordinator()
        val start = core.completedFrames
        return withTimeoutOrNull(2000L) {
            while (core.completedFrames - start < frames) {
                if (!core.emulationRunning || coordinator() !== core) return@withTimeoutOrNull false
                delay(1L)
            }
            core.emulationRunning
        } ?: false
    }

    override suspend fun sendFrameButton(buttonMask: Int, settleFrames: Int): Boolean {
        val core = coordinator()
        return try {
            ControllerInputRouter.sendAutomatedFrameButton(buttonMask, settleFrames, ::waitFrames) { mask ->
                // Both edges start between retro_run calls: an in-flight frame already past
                // its input poll must not count as a down/up frame.
                try {
                    core.executeExclusive(50L) { core.setInputButtons(mask) }
                } catch (e: Exception) {
                    // Even a wedged core must not retain an automated held button.
                    if (mask and buttonMask == 0) core.setInputButtons(mask)
                    throw e
                }
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
 * - Physical held buttons are preserved through ControllerInputRouter
 */
class BattleInputAdapter(
    private val dispatcher: BattleInputDispatcher = DefaultBattleInputDispatcher(),
    private val isPaused: () -> Boolean = { !LibretroCoreCoordinator.defaultInstance.emulationRunning },
    private val frameReader: () -> Long = { LibretroCoreCoordinator.defaultInstance.completedFrames },
    private val stateReader: (() -> BattleUiSnapshot)? = null
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
    }

    suspend fun executeSelectMove(slot: Int, currentUi: BattleUiSnapshot): BattleInputResult {
        // A second tap is rejected, never queued against a snapshot from the previous turn.
        if (!actionMutex.tryLock()) return BattleInputResult.Busy
        try {
            return withTimeoutOrNull(20_000L) {
                try {
                    executeSelectMoveInternal(slot, currentUi)
                } catch (abort: MoveMacroAbort) {
                    abort.result
                }
            } ?: BattleInputResult.Timeout("Move-selection watchdog expired")
        } finally {
            actionMutex.unlock()
        }
    }

    private class MoveMacroAbort(val result: BattleInputResult) : RuntimeException()

    private suspend fun executeSelectMoveInternal(slot: Int, currentUi: BattleUiSnapshot): BattleInputResult {
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
            if (isPaused()) abort(BattleInputResult.Paused)
            if (frameReader() - startFrame >= WATCHDOG_FRAMES) {
                abort(BattleInputResult.Timeout("Move-selection frame watchdog expired"))
            }
        }
        fun menu(expected: BattleUiState): BattleUiSnapshot {
            running()
            val ui = reader()
            if (ui.state != expected) abort(BattleInputResult.UnexpectedTransition(expected, ui.state))
            if (ui.battlerIndex != currentUi.battlerIndex || ui.playerBattlerCount != 1 ||
                !ui.capabilities.selectMove || !ui.inputSafe) {
                abort(BattleInputResult.UnsupportedProfile("Battle input authority changed"))
            }
            if (cursor(ui) !in 0..3) abort(BattleInputResult.CursorUnavailable("Battle menu cursor is unknown"))
            return ui
        }
        suspend fun press(button: Int) {
            running()
            if (!dispatcher.sendFrameButton(button, DIRECTION_SETTLE_FRAMES)) {
                running()
                abort(BattleInputResult.Timeout("Core frames or input dispatch unavailable"))
            }
            running()
        }
        suspend fun walk(state: BattleUiState, target: Int) {
            val directions = mutableListOf(InputManager.BTN_LEFT, InputManager.BTN_UP)
            if (target and 1 != 0) directions += InputManager.BTN_RIGHT
            if (target and 2 != 0) directions += InputManager.BTN_DOWN
            for (button in directions) {
                val from = cursor(menu(state))!!
                val expected = when (button) {
                    InputManager.BTN_LEFT -> from and 2
                    InputManager.BTN_UP -> from and 1
                    InputManager.BTN_RIGHT -> from or 1
                    else -> from or 2
                }
                press(button)
                if (cursor(menu(state)) != expected) {
                    abort(BattleInputResult.CursorUnavailable("Directional press did not reach the expected cursor"))
                }
            }
            if (cursor(menu(state)) != target) abort(BattleInputResult.CursorUnavailable("Target cursor was not observed"))
        }

        if (currentUi.state == BattleUiState.COMMAND_MENU) {
            walk(BattleUiState.COMMAND_MENU, 0)
            // The command cursor is verified too: a dropped normalization must never open BAG/RUN.
            press(InputManager.BTN_A)
            var observed = reader()
            var frames = 0
            while (observed.state != BattleUiState.MOVE_MENU && frames < 120) {
                running()
                if (!dispatcher.waitFrames(1)) abort(BattleInputResult.Timeout("Move menu did not become available"))
                frames++
                observed = reader()
            }
            if (observed.state != BattleUiState.MOVE_MENU) {
                abort(BattleInputResult.UnexpectedTransition(BattleUiState.MOVE_MENU, observed.state))
            }
        }
        walk(BattleUiState.MOVE_MENU, slot)
        // Direct core read immediately before A; never authorize using the 100ms UI cache.
        if (cursor(menu(BattleUiState.MOVE_MENU)) != slot) {
            abort(BattleInputResult.CursorUnavailable("Target move cursor was not observed before confirmation"))
        }
        press(InputManager.BTN_A)
        return if (reader().state == BattleUiState.TARGET_SELECT) BattleInputResult.TargetSelectionRequired
               else BattleInputResult.Success
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
                    val actual = stateReader.invoke().state
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
            if (snapshot.state == expected) {
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
