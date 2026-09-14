package com.dualdex.battle

import com.dualdex.emulator.RomIdentity
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.*
import org.junit.Test

class BattleInteractionPolicyTest {

    private val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val identity = RomIdentity.create(hash, "Pokemon FireRed")

    private fun profile(
        sha256Hashes: List<String> = listOf(hash),
        interactiveControlsVerified: Boolean = true,
        battleUiVerified: Boolean = true
    ) = RomHackProfile(
        id = "test_firered",
        name = "Test FireRed",
        baseGame = "FireRed",
        gameId = 2,
        sha256Hashes = sha256Hashes,
        isVerified = true,
        memoryLayoutVerified = true,
        battleUiVerified = battleUiVerified,
        interactiveControlsVerified = interactiveControlsVerified
    )

    private fun evaluate(
        profile: RomHackProfile,
        romIdentity: RomIdentity? = identity,
        userEnabled: Boolean = true,
        state: BattleUiState = BattleUiState.COMMAND_MENU,
        commandCursor: Int? = 1,
        moveCursor: Int? = null,
        partyCursor: Int? = null
    ) = BattleInteractionPolicy.evaluate(
        profile = profile,
        romIdentity = romIdentity,
        userEnabled = userEnabled,
        inBattle = true,
        uiState = state,
        selectedActionIndex = commandCursor,
        selectedMoveIndex = moveCursor,
        selectedPartySlot = partyCursor
    )

    @Test
    fun userSettingCannotAuthorizeUnverifiedProfile() {
        val snapshot = evaluate(profile(battleUiVerified = false))

        assertFalse(snapshot.capabilities.isInteractiveSupported)
        assertFalse(snapshot.isInputAccepted)
        assertEquals(DataConfidence.UNAVAILABLE, snapshot.stateConfidence)
    }

    @Test
    fun shaMismatchCannotAuthorizeInteraction() {
        val snapshot = evaluate(profile(sha256Hashes = listOf("fedcba9876543210fedcba9876543210")))

        assertFalse(snapshot.capabilities.isInteractiveSupported)
        assertFalse(snapshot.isInputAccepted)
    }

    @Test
    fun interactiveVerificationFlagIsRequired() {
        val snapshot = evaluate(profile(interactiveControlsVerified = false))

        assertFalse(snapshot.capabilities.isInteractiveSupported)
        assertFalse(snapshot.isInputAccepted)
    }

    @Test
    fun userSettingOffDisablesFullyVerifiedCapability() {
        val snapshot = evaluate(profile(), userEnabled = false)

        assertFalse(snapshot.capabilities.isInteractiveSupported)
        assertFalse(snapshot.isInputAccepted)
        // Trust comes from verified ROM/profile state, never from the preference.
        assertEquals(DataConfidence.VERIFIED, snapshot.stateConfidence)
    }

    @Test
    fun fullyVerifiedProfileStillNeedsObservedCursorForInput() {
        val snapshot = evaluate(profile(), commandCursor = null)

        assertTrue(snapshot.capabilities.isInteractiveSupported)
        assertFalse(snapshot.isInputAccepted)
        assertFalse(snapshot.inputSafe)
        assertNull(snapshot.selectedActionIndex)
        assertEquals("Read-only: battle menu cursor state is unavailable.", snapshot.readOnlyReason)
    }

    @Test
    fun unknownNativeStateRemainsUnknownAndRejectsInput() {
        val snapshot = evaluate(profile(), state = BattleUiState.UNKNOWN)

        assertEquals(BattleUiState.UNKNOWN, snapshot.state)
        assertFalse(snapshot.isInputAccepted)
        assertFalse(snapshot.inputSafe)
    }

    @Test
    fun preferenceNeverUpgradesConfidence() {
        val snapshot = evaluate(
            profile(battleUiVerified = false, interactiveControlsVerified = false),
            userEnabled = true
        )

        assertEquals(DataConfidence.UNAVAILABLE, snapshot.stateConfidence)
        assertFalse(snapshot.capabilities.isInteractiveSupported)
    }

    @Test
    fun nativeCodeMappingHasNoCommandMenuFallback() {
        assertEquals(BattleUiState.UNKNOWN, BattleUiState.fromNativeCode(0))
        assertEquals(BattleUiState.UNKNOWN, BattleUiState.fromNativeCode(5))
        assertEquals(BattleUiState.UNKNOWN, BattleUiState.fromNativeCode(999))
    }
}
