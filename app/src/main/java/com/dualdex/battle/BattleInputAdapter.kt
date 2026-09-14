package com.dualdex.battle

import com.dualdex.emulator.ControllerInputRouter
import com.dualdex.emulator.InputManager
import com.dualdex.pokemon.ParsedPokemon
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Interface for dispatching emulated controller button presses.
 * Decoupled to enable 100% deterministic testing without an Android device or emulator.
 */
fun interface BattleInputDispatcher {
    suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean
}

/**
 * Production dispatcher that writes button masks through ControllerInputRouter.
 * Guarantees physical controller inputs are never cleared and automated sequences are serialized.
 */
class DefaultBattleInputDispatcher : BattleInputDispatcher {
    override suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean {
        return ControllerInputRouter.sendAutomatedButton(buttonMask, durationMs, delayMs)
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
    private val stateReader: (() -> BattleUiSnapshot)? = null
) {
    private val actionMutex = Mutex()

    companion object {
        const val BTN_PRESS_DURATION_MS = 50L
        const val BTN_INTER_DELAY_MS = 60L
        const val MENU_TRANSITION_DELAY_MS = 100L
        const val TRANSITION_POLL_INTERVAL_MS = 25L
        const val TRANSITION_TIMEOUT_MS = 500L
    }

    suspend fun executeSelectMove(slot: Int, currentUi: BattleUiSnapshot): BattleInputResult {
        return actionMutex.withLock {
            executeSelectMoveInternal(slot, currentUi)
        }
    }

    private suspend fun executeSelectMoveInternal(slot: Int, currentUi: BattleUiSnapshot): BattleInputResult {
        if (slot !in 0..3) {
            return BattleInputResult.IllegalTarget("Move slot $slot out of bounds (0..3)")
        }

        if (currentUi.state == BattleUiState.ANIMATION_OR_TEXT) {
            return BattleInputResult.Busy
        }

        if (!currentUi.capabilities.selectMove) {
            return BattleInputResult.UnsupportedProfile(
                "Move selection is not verified or supported for this ROM/profile"
            )
        }

        if (!currentUi.inputSafe) {
            return BattleInputResult.UnknownUiState(currentUi.state)
        }

        if (currentUi.state != BattleUiState.COMMAND_MENU && currentUi.state != BattleUiState.MOVE_MENU) {
            return BattleInputResult.UnknownUiState(currentUi.state)
        }

        if (currentUi.state == BattleUiState.COMMAND_MENU) {
            val commandCursor = currentUi.selectedActionIndex
                ?: return BattleInputResult.CursorUnavailable("Command menu cursor is unknown")

            // Command menu layout:
            // (0,0) FIGHT    (1,0) BAG
            // (0,1) POKEMON  (1,1) RUN
            // Navigate from current command cursor to FIGHT (slot 0)
            navigateToCommandSlot(fromSlot = commandCursor, toSlot = 0)

            // Press A to open Move Menu
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, MENU_TRANSITION_DELAY_MS)

            // Validate state transition if a state reader is configured
            val observedMoveCursor: Int = if (stateReader != null) {
                val transitionedUi = waitForUiState(BattleUiState.MOVE_MENU, TRANSITION_TIMEOUT_MS)
                if (transitionedUi == null) {
                    val actual = stateReader.invoke().state
                    return BattleInputResult.UnexpectedTransition(BattleUiState.MOVE_MENU, actual)
                }
                transitionedUi.selectedMoveIndex
                    ?: return BattleInputResult.CursorUnavailable("Move menu cursor is unknown after transition")
            } else {
                currentUi.selectedMoveIndex
                    ?: return BattleInputResult.CursorUnavailable("Move menu cursor is unknown")
            }

            // In Move Menu, navigate from observed cursor to target slot
            navigateToMoveSlot(fromSlot = observedMoveCursor, toSlot = slot)

            // Confirm move with A button
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            return BattleInputResult.Success
        }

        if (currentUi.state == BattleUiState.MOVE_MENU) {
            val moveCursor = currentUi.selectedMoveIndex
                ?: return BattleInputResult.CursorUnavailable("Move menu cursor is unknown")

            navigateToMoveSlot(fromSlot = moveCursor.coerceIn(0, 3), toSlot = slot)

            // Confirm move with A button
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            return BattleInputResult.Success
        }

        return BattleInputResult.UnknownUiState(currentUi.state)
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

    private suspend fun navigateToMoveSlot(fromSlot: Int, toSlot: Int) {
        if (fromSlot == toSlot) return
        val fromX = fromSlot % 2
        val fromY = fromSlot / 2
        val toX = toSlot % 2
        val toY = toSlot / 2

        if (toX > fromX) {
            dispatcher.sendButton(InputManager.BTN_RIGHT, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
        } else if (toX < fromX) {
            dispatcher.sendButton(InputManager.BTN_LEFT, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
        }

        if (toY > fromY) {
            dispatcher.sendButton(InputManager.BTN_DOWN, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
        } else if (toY < fromY) {
            dispatcher.sendButton(InputManager.BTN_UP, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
        }
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
