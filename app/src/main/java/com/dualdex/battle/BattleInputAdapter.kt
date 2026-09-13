package com.dualdex.battle

import com.dualdex.emulator.InputManager
import com.dualdex.pokemon.ParsedPokemon
import kotlinx.coroutines.delay

/**
 * Interface for dispatching emulated controller button presses.
 * Decoupled to enable 100% deterministic testing without an Android device or emulator.
 */
fun interface BattleInputDispatcher {
    suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean
}

/**
 * Production dispatcher that writes button masks to LibretroHost.
 */
class DefaultBattleInputDispatcher(
    private val setButtons: (Int) -> Unit = { mask ->
        com.dualdex.emulator.LibretroHost.nativeSetInputButtons(mask)
    }
) : BattleInputDispatcher {
    override suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean {
        setButtons(buttonMask)
        delay(durationMs)
        setButtons(0)
        if (delayMs > 0) {
            delay(delayMs)
        }
        return true
    }
}

/**
 * Controller-driven adapter for Enhanced Battle Console interactive actions.
 * Translates UI taps on moves and party members into standard emulated button sequences (D-pad, A, B).
 *
 * Never writes directly to game memory; the original game's battle engine remains authoritative.
 */
class BattleInputAdapter(
    private val dispatcher: BattleInputDispatcher = DefaultBattleInputDispatcher()
) {
    companion object {
        const val BTN_PRESS_DURATION_MS = 50L
        const val BTN_INTER_DELAY_MS = 60L
        const val MENU_TRANSITION_DELAY_MS = 100L
    }

    /**
     * Selects one of the 4 moves (slots 0..3) using standard controller D-pad and A button presses.
     * Returns true if the macro was successfully dispatched; false if aborted for safety.
     */
    suspend fun selectMove(slot: Int, currentUi: BattleUiSnapshot): Boolean {
        if (slot !in 0..3) return false
        if (!currentUi.isInputAccepted) return false
        if (currentUi.state != BattleUiState.COMMAND_MENU && currentUi.state != BattleUiState.MOVE_MENU) {
            return false
        }

        if (currentUi.state == BattleUiState.COMMAND_MENU) {
            // Command menu layout:
            // (0,0) FIGHT    (1,0) BAG
            // (0,1) POKEMON  (1,1) RUN
            // Cursor starts at FIGHT (0,0). Press A to enter Move Menu.
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, MENU_TRANSITION_DELAY_MS)

            // In Move Menu, cursor starts at Move 0 (0,0).
            // Navigate 2x2 grid to slot:
            navigateToMoveSlot(fromSlot = 0, toSlot = slot)

            // Confirm move with A button
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            return true
        }

        if (currentUi.state == BattleUiState.MOVE_MENU) {
            // Already in Move Menu: navigate from current cursor to target slot
            navigateToMoveSlot(fromSlot = currentUi.selectedMoveIndex.coerceIn(0, 3), toSlot = slot)

            // Confirm move with A button
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            return true
        }

        return false
    }

    /**
     * Switches to a party Pokémon (targetSlot 0..5).
     * Validates legality: target must not be active, must not be fainted, and UI must accept input.
     * Returns true if switch sequence was dispatched; false if rejected.
     */
    suspend fun switchPokemon(
        targetSlot: Int,
        currentUi: BattleUiSnapshot,
        party: List<ParsedPokemon>,
        activeSlot: Int = 0
    ): Boolean {
        if (targetSlot !in 0..5) return false
        if (!currentUi.isInputAccepted) return false
        if (currentUi.state != BattleUiState.COMMAND_MENU && currentUi.state != BattleUiState.PARTY_MENU) {
            return false
        }

        // Target cannot be active Pokémon
        if (targetSlot == activeSlot) return false

        // Target cannot be fainted or empty
        val targetMon = party.getOrNull(targetSlot)
        if (targetMon == null || targetMon.isEmpty || !targetMon.isValid || targetMon.currentHp <= 0) {
            return false
        }

        if (currentUi.state == BattleUiState.COMMAND_MENU) {
            // Command menu:
            // (0,0) FIGHT    (1,0) BAG
            // (0,1) POKEMON  (1,1) RUN
            // From FIGHT (0,0): navigate DOWN to POKEMON (0,1)
            dispatcher.sendButton(InputManager.BTN_DOWN, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)

            // Open Party Screen with A
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, MENU_TRANSITION_DELAY_MS)

            // In Party Screen, cursor starts at slot 0 (active).
            // Navigate down to targetSlot:
            repeat(targetSlot) {
                dispatcher.sendButton(InputManager.BTN_DOWN, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            }

            // Press A to open action menu
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)

            // Press A to select SHIFT (top option)
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            return true
        }

        if (currentUi.state == BattleUiState.PARTY_MENU) {
            // Forced switch (e.g. active Pokémon fainted): cursor starts at active or slot 0.
            val startSlot = currentUi.selectedPartySlot.coerceIn(0, 5)
            if (targetSlot > startSlot) {
                repeat(targetSlot - startSlot) {
                    dispatcher.sendButton(InputManager.BTN_DOWN, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
                }
            } else if (targetSlot < startSlot) {
                repeat(startSlot - targetSlot) {
                    dispatcher.sendButton(InputManager.BTN_UP, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
                }
            }

            // Press A to open action menu
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)

            // Press A to confirm SHIFT
            dispatcher.sendButton(InputManager.BTN_A, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
            return true
        }

        return false
    }

    /**
     * Cancels current submenu and returns toward Command Menu.
     */
    suspend fun cancel(): Boolean {
        return dispatcher.sendButton(InputManager.BTN_B, BTN_PRESS_DURATION_MS, BTN_INTER_DELAY_MS)
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
}
