package com.dualdex.battle

import com.dualdex.emulator.RomIdentity
import com.dualdex.romhack.RomHackProfile

/**
 * Single, deterministic authorization point for battle input.
 *
 * A setting is only user permission. It cannot establish that the loaded ROM or its battle
 * UI is safe to control.
 */
object BattleInteractionPolicy {

    fun isInteractiveVerified(
        profile: RomHackProfile,
        romIdentity: RomIdentity?
    ): Boolean {
        return profile.isVerified &&
                profile.memoryLayoutVerified &&
                profile.battleUiVerified &&
                profile.interactiveControlsVerified &&
                romIdentity != null &&
                profile.sha256Hashes.isNotEmpty() &&
                profile.sha256Hashes.any { expected ->
                    expected.isNotBlank() && expected.equals(romIdentity.sha256, ignoreCase = true)
                }
    }

    fun evaluate(
        profile: RomHackProfile,
        romIdentity: RomIdentity?,
        userEnabled: Boolean,
        inBattle: Boolean,
        uiState: BattleUiState,
        selectedActionIndex: Int? = null,
        selectedMoveIndex: Int? = null,
        selectedPartySlot: Int? = null
    ): BattleUiSnapshot {
        val verified = isInteractiveVerified(profile, romIdentity)
        val permissionGranted = userEnabled && verified
        val capabilities = if (permissionGranted) {
            BattleInteractionCapabilities.FULL_VERIFIED
        } else {
            BattleInteractionCapabilities.READ_ONLY
        }

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
        val stateConfidence = if (verified && inBattle && stateIsInteractive) {
            DataConfidence.VERIFIED
        } else {
            DataConfidence.UNAVAILABLE
        }

        val readOnlyReason = when {
            !userEnabled -> "Read-only: touch battle controls are disabled in Settings."
            !verified -> "Read-only: exact ROM and battle UI verification is unavailable."
            !inBattle -> "Read-only: no active battle detected."
            !stateIsInteractive -> "Read-only: battle UI state is unknown or transitioning."
            !cursorAvailable -> "Read-only: battle menu cursor state is unavailable."
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
