package com.dualdex.battle

import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust

/**
 * Single, deterministic authorization point for battle input.
 *
 * A setting is only user permission. It cannot establish that the loaded ROM or its battle
 * UI is safe to control.
 */
object BattleInteractionPolicy {

    fun isInteractiveVerified(
        profile: RomHackProfile,
        runtimeTrust: RuntimeRomTrust?
    ): Boolean {
        return runtimeTrust?.exactRuntimeVerified == true &&
                profile.sha256Hashes.any { it.equals(runtimeTrust.activeRomSha256, ignoreCase = true) } &&
                profile.battleUiVerified &&
                profile.commandCursorVerified &&
                profile.moveCursorVerified &&
                profile.moveSelectionVerified
    }

    fun evaluate(
        profile: RomHackProfile,
        runtimeTrust: RuntimeRomTrust?,
        userEnabled: Boolean,
        inBattle: Boolean,
        uiState: BattleUiState,
        selectedActionIndex: Int? = null,
        selectedMoveIndex: Int? = null,
        selectedPartySlot: Int? = null
    ): BattleUiSnapshot {
        val exactRuntimeVerified = runtimeTrust?.exactRuntimeVerified == true &&
            profile.sha256Hashes.any { it.equals(runtimeTrust.activeRomSha256, ignoreCase = true) }
        val baseUiVerified = exactRuntimeVerified && profile.battleUiVerified
        val selectMoveVerified = baseUiVerified && profile.commandCursorVerified &&
            profile.moveCursorVerified && profile.moveSelectionVerified
        // Party switching additionally requires an observed action submenu.  Do not infer that
        // SHIFT is the first party action until a ROM-specific reader proves it.
        val switchPokemonVerified = baseUiVerified && profile.commandCursorVerified &&
            profile.partyCursorVerified && profile.partySwitchVerified && profile.partyActionMenuVerified
        val permissionGranted = userEnabled && (selectMoveVerified || switchPokemonVerified)
        val capabilities = BattleInteractionCapabilities(
            readBattleState = baseUiVerified,
            readCommandCursor = baseUiVerified && profile.commandCursorVerified,
            readMoveCursor = baseUiVerified && profile.moveCursorVerified,
            readPartyCursor = baseUiVerified && profile.partyCursorVerified,
            selectMove = permissionGranted && selectMoveVerified,
            switchPokemon = permissionGranted && switchPokemonVerified,
            confidence = if (baseUiVerified) DataConfidence.VERIFIED else DataConfidence.UNAVAILABLE
        )

        val stateIsInteractive = uiState == BattleUiState.COMMAND_MENU ||
                uiState == BattleUiState.MOVE_MENU ||
                uiState == BattleUiState.PARTY_MENU
        val cursorAvailable = when (uiState) {
            BattleUiState.COMMAND_MENU -> selectedActionIndex != null
            BattleUiState.MOVE_MENU -> selectedMoveIndex != null
            BattleUiState.PARTY_MENU -> selectedPartySlot != null
            else -> false
        }
        val isInputAccepted = permissionGranted && inBattle && stateIsInteractive && cursorAvailable
        val stateConfidence = if (baseUiVerified && inBattle && stateIsInteractive) {
            DataConfidence.VERIFIED
        } else {
            DataConfidence.UNAVAILABLE
        }

        val readOnlyReason = when {
            !exactRuntimeVerified -> "Read-only: ROM/profile is not exact-verified."
            !baseUiVerified -> "Read-only: verified battle UI readers are unavailable."
            !inBattle -> "Read-only: no active battle detected."
            !stateIsInteractive -> "Read-only: battle UI state is unknown or transitioning."
            !cursorAvailable -> "Read-only: battle menu cursor state is unavailable."
            !userEnabled -> "Read-only: touch battle controls are disabled in Settings."
            else -> null
        }

        return BattleUiSnapshot(
            state = uiState,
            selectedActionIndex = selectedActionIndex,
            selectedMoveIndex = selectedMoveIndex,
            selectedPartySlot = selectedPartySlot,
            stateConfidence = stateConfidence,
            isInputAccepted = isInputAccepted,
            capabilities = capabilities,
            readOnlyReason = readOnlyReason
        )
    }
}
