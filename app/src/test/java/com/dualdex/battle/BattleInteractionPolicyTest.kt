package com.dualdex.battle

import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.*
import org.junit.Test

class BattleInteractionPolicyTest {

    private val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun runtimeTrust(
        method: ProfileMatchMethod = ProfileMatchMethod.EXACT_SHA256,
        detectedSha: String = hash,
        activeSha: String = hash
    ) = RuntimeRomTrust(
        matchMethod = method,
        detectedSha256 = detectedSha,
        activeRomSha256 = activeSha,
        profileVerified = true,
        memoryLayoutVerified = true,
        profileSha256Hashes = listOf(hash)
    )

    private fun profile(
        sha256Hashes: List<String> = listOf(hash),
        interactiveControlsVerified: Boolean = true,
        battleUiVerified: Boolean = true,
        cursorAndActionReadersVerified: Boolean = true
    ) = RomHackProfile(
        id = "test_firered",
        name = "Test FireRed",
        baseGame = "FireRed",
        gameId = 2,
        sha256Hashes = sha256Hashes,
        isVerified = true,
        memoryLayoutVerified = true,
        battleUiVerified = battleUiVerified,
        interactiveControlsVerified = interactiveControlsVerified,
        commandCursorVerified = cursorAndActionReadersVerified,
        moveCursorVerified = cursorAndActionReadersVerified,
        partyCursorVerified = cursorAndActionReadersVerified,
        moveSelectionVerified = cursorAndActionReadersVerified,
        partySwitchVerified = cursorAndActionReadersVerified,
        partyActionMenuVerified = cursorAndActionReadersVerified
    )

    private fun evaluate(
        profile: RomHackProfile,
        runtimeTrust: RuntimeRomTrust? = runtimeTrust(),
        userEnabled: Boolean = true,
        state: BattleUiState = BattleUiState.COMMAND_MENU,
        commandCursor: Int? = 1,
        moveCursor: Int? = null,
        partyCursor: Int? = null
    ) = BattleInteractionPolicy.evaluate(
        profile = profile,
        runtimeTrust = runtimeTrust,
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
    fun granularCursorVerificationIsRequired() {
        val snapshot = evaluate(profile(interactiveControlsVerified = true, cursorAndActionReadersVerified = false))

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
        assertEquals("Read-only: touch battle controls are disabled in Settings.", snapshot.readOnlyReason)
    }

    @Test
    fun structuralBlockersTakePriorityOverDisabledPreference() {
        assertEquals(
            "Read-only: verified battle UI readers are unavailable.",
            evaluate(profile(battleUiVerified = false), userEnabled = false).readOnlyReason
        )
        assertEquals(
            "Read-only: ROM/profile is not exact-verified.",
            evaluate(profile(), runtimeTrust = runtimeTrust(method = ProfileMatchMethod.FILENAME_KEYWORD), userEnabled = false).readOnlyReason
        )
    }

    @Test
    fun verifiedInteractiveProfileReportsUnknownUiBeforePreference() {
        assertEquals(
            "Read-only: battle UI state is unknown or transitioning.",
            evaluate(profile(), userEnabled = true, state = BattleUiState.UNKNOWN).readOnlyReason
        )
        assertEquals(
            "Read-only: battle menu cursor state is unavailable.",
            evaluate(profile(), userEnabled = true, commandCursor = null).readOnlyReason
        )
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

    @Test
    fun onlyExactShaMatchCanEstablishRuntimeTrust() {
        ProfileMatchMethod.entries.filterNot { it == ProfileMatchMethod.EXACT_SHA256 }.forEach { method ->
            assertFalse("$method must not establish runtime trust", runtimeTrust(method = method).exactRuntimeVerified)
        }
        assertTrue(runtimeTrust().exactRuntimeVerified)
        assertFalse(runtimeTrust(detectedSha = "different").exactRuntimeVerified)
    }

    @Test
    fun genericInteractiveFlagCannotEnableCapabilities() {
        val snapshot = evaluate(profile(cursorAndActionReadersVerified = false), runtimeTrust = runtimeTrust())
        assertFalse(snapshot.capabilities.selectMove)
        assertFalse(snapshot.capabilities.switchPokemon)
        assertFalse(snapshot.inputSafe)
    }

    @Test
    fun presenceStabilizerRejectsSingleFrameNoiseAndRetainsUnknownState() {
        val stabilizer = BattlePresenceStabilizer()
        assertFalse(stabilizer.update(BattlePresence.OBSERVED))
        assertTrue(stabilizer.update(BattlePresence.OBSERVED))
        assertTrue(stabilizer.update(BattlePresence.NOT_OBSERVED))
        assertTrue(stabilizer.update(BattlePresence.UNKNOWN))
        assertTrue(stabilizer.update(BattlePresence.NOT_OBSERVED))
        assertFalse(stabilizer.update(BattlePresence.NOT_OBSERVED))
    }
}
