package com.dualdex.romhack

import com.dualdex.battle.BattlePresence
import com.dualdex.battle.BattleUiSnapshot
import com.dualdex.battle.StatStages
import com.dualdex.companion.CompanionViewModel
import com.dualdex.companion.LiveObservationDecision
import com.dualdex.companion.LiveObservationStabilizer
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation
import org.junit.Assert.*
import org.junit.Test

class RomCompatibilityTest {

    private val fireRedSha = "111122223333444455556666777788889999aaaabbbbccccddddeeeeffff0000"
    private val emeraldSha = "aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999"
    private val radicalRedSha = "feedfacecafebeef0123456789abcdef0123456789abcdef0123456789abcdef"

    private val fireRedProfile = RomHackProfile(
        id = "vanilla_firered",
        name = "Pokemon FireRed",
        baseGame = "FireRed",
        gameId = 2,
        headerTitles = listOf("POKEMON FIRE"),
        sha256Hashes = listOf(fireRedSha),
        isVerified = true,
        memoryLayoutVerified = true,
        playerPartyOffset = 0x02024284L,
        enemyPartyOffset = 0x0202402CL
    )

    private val emeraldProfile = RomHackProfile(
        id = "vanilla_emerald",
        name = "Pokemon Emerald",
        baseGame = "Emerald",
        gameId = 1,
        headerTitles = listOf("POKEMON EMER"),
        sha256Hashes = listOf(emeraldSha),
        isVerified = true,
        memoryLayoutVerified = true,
        playerPartyOffset = 0x020244ECL,
        enemyPartyOffset = 0x02024744L
    )

    private val radicalRedProfile = RomHackProfile(
        id = "radical_red",
        name = "Pokemon Radical Red",
        baseGame = "FireRed",
        gameId = 5,
        engine = "CFRU",
        headerTitles = listOf("RADICALRED"),
        sha256Hashes = listOf(radicalRedSha),
        isVerified = true,
        memoryLayoutVerified = true
    )

    private val testProfiles = listOf(fireRedProfile, emeraldProfile, radicalRedProfile)

    private fun makeHeader(title: String, gameCode: String): ByteArray {
        val header = ByteArray(192)
        val titleBytes = title.toByteArray(Charsets.US_ASCII)
        System.arraycopy(titleBytes, 0, header, 160, titleBytes.size.coerceAtMost(12))
        val codeBytes = gameCode.toByteArray(Charsets.US_ASCII)
        System.arraycopy(codeBytes, 0, header, 172, codeBytes.size.coerceAtMost(4))
        return header
    }

    private fun createMon(species: Int): ParsedPokemon = ParsedPokemon(
        isValid = true,
        isEmpty = false,
        pid = 100L + species,
        tid = 1,
        sid = 2,
        nickname = "Mon $species",
        otName = "Ash",
        species = species,
        heldItem = 0,
        level = 50,
        nature = 0,
        natureName = "Hardy",
        isShiny = false,
        abilitySlot = 0,
        isEgg = false,
        friendship = 70,
        experience = 1000L,
        hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(1, 2, 0, 0),
        pp = intArrayOf(35, 20, 0, 0),
        currentHp = 100,
        maxHp = 100,
        attack = 60, defense = 60, speed = 60, spAttack = 60, spDefense = 60,
        statusCondition = 0L
    )

    // Minimum requirement 1: Exact supported FireRed SHA -> VERIFIED -> trusted memory features may run
    @Test
    fun exactSupportedFireRedSha_producesVerifiedStatusAndEnablesLiveMemory() {
        val header = makeHeader("POKEMON FIRE", "BPRE")
        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = header,
            sha256 = fireRedSha,
            profiles = testProfiles
        )

        assertEquals(RomCompatibilityStatus.VERIFIED, result.status)
        assertTrue(result.isVerified)
        assertTrue(result.mayReadLiveMemory)
        assertEquals(fireRedProfile.id, result.profile.id)
        assertEquals(ProfileMatchMethod.EXACT_SHA256, result.matchMethod)

        val runtimeTrust = RuntimeRomTrust.from(result, fireRedSha)
        assertTrue(runtimeTrust.exactRuntimeVerified)
        assertTrue(runtimeTrust.mayReadLiveMemory)
    }

    // Minimum requirement 2: Exact supported Emerald SHA -> VERIFIED -> trusted memory features may run
    @Test
    fun exactSupportedEmeraldSha_producesVerifiedStatusAndEnablesLiveMemory() {
        val header = makeHeader("POKEMON EMER", "BPEE")
        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = header,
            sha256 = emeraldSha,
            profiles = testProfiles
        )

        assertEquals(RomCompatibilityStatus.VERIFIED, result.status)
        assertTrue(result.isVerified)
        assertTrue(result.mayReadLiveMemory)
        assertEquals(emeraldProfile.id, result.profile.id)
        assertEquals(ProfileMatchMethod.EXACT_SHA256, result.matchMethod)

        val runtimeTrust = RuntimeRomTrust.from(result, emeraldSha)
        assertTrue(runtimeTrust.exactRuntimeVerified)
        assertTrue(runtimeTrust.mayReadLiveMemory)
    }

    // Minimum requirement 3: Known FireRed-looking ROM/header with an unknown SHA -> RECOGNIZED_UNVERIFIED
    @Test
    fun knownFireRedHeaderWithUnknownSha_producesRecognizedUnverifiedAndDisablesLiveMemory() {
        val unknownSha = "9999999999999999999999999999999999999999999999999999999999999999"

        // 3a. Matching by header title
        val headerWithTitle = makeHeader("POKEMON FIRE", "BPRE")
        val resultTitle = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = headerWithTitle,
            sha256 = unknownSha,
            profiles = testProfiles
        )
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, resultTitle.status)
        assertFalse(resultTitle.isVerified)
        assertFalse(resultTitle.mayReadLiveMemory)
        assertEquals("vanilla_firered", resultTitle.profile.id)
        assertEquals(ProfileMatchMethod.HEADER_TITLE, resultTitle.matchMethod)

        // 3b. Matching by base game code fallback (e.g. unknown title with BPR code)
        val headerWithCodeOnly = makeHeader("UNKNOWN CODE", "BPRE")
        val resultCode = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = headerWithCodeOnly,
            sha256 = unknownSha,
            profiles = testProfiles
        )
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, resultCode.status)
        assertFalse(resultCode.isVerified)
        assertFalse(resultCode.mayReadLiveMemory)
        assertEquals("vanilla_firered", resultCode.profile.id)
        assertEquals(ProfileMatchMethod.BASE_GAME_FALLBACK, resultCode.matchMethod)

        val runtimeTrust = RuntimeRomTrust.from(resultCode, unknownSha)
        assertFalse(runtimeTrust.exactRuntimeVerified)
        assertFalse(runtimeTrust.mayReadLiveMemory)
    }

    // Minimum requirement 4: Misleading filename containing a supported hack name but unknown bytes/hash -> NOT VERIFIED
    @Test
    fun misleadingFilenameWithUnknownHash_neverProducesVerifiedStatus() {
        val header = makeHeader("RANDOM GAME", "XXXX")
        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = header,
            sha256 = "unknown_sha_abc",
            profiles = testProfiles,
            fileName = "Pokemon Radical Red v3.02 Official.gba"
        )

        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, result.status)
        assertFalse(result.isVerified)
        assertFalse(result.mayReadLiveMemory)
        assertEquals(ProfileMatchMethod.FILENAME_KEYWORD, result.matchMethod)
        assertEquals(radicalRedProfile.id, result.profile.id)

        val runtimeTrust = RuntimeRomTrust.from(result, "unknown_sha_abc")
        assertFalse(runtimeTrust.exactRuntimeVerified)
        assertFalse(runtimeTrust.mayReadLiveMemory)
    }

    // Minimum requirement 5: Completely unknown/random GBA ROM -> UNSUPPORTED, no FireRed fallback
    @Test
    fun completelyUnknownGbaRom_producesUnsupportedStatusWithNoFireRedFallback() {
        val header = makeHeader("HOMEBREW", "ZZZZ")
        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = header,
            sha256 = "homebrew_hash",
            profiles = testProfiles,
            fileName = "cool_homebrew.gba"
        )

        assertEquals(RomCompatibilityStatus.UNSUPPORTED, result.status)
        assertFalse(result.isVerified)
        assertFalse(result.mayReadLiveMemory)
        assertEquals(RomHackProfile.UNSUPPORTED.id, result.profile.id)
        assertEquals(0, result.profile.gameId)
        assertNotEquals(fireRedProfile.id, result.profile.id)
        assertEquals("gen3_vanilla", result.profile.gameDataPackId)

        val runtimeTrust = RuntimeRomTrust.from(result, "homebrew_hash")
        assertFalse(runtimeTrust.exactRuntimeVerified)
        assertFalse(runtimeTrust.mayReadLiveMemory)
    }

    // Minimum requirement 7: Switching verified ROM A -> unsupported ROM B -> no live state survives
    @Test
    fun switchingVerifiedRomToUnsupportedRom_clearsAllObservationsImmediately() {
        val vm = CompanionViewModel()

        val verifiedCompat = RomCompatibility.verified(fireRedProfile, fireRedSha)
        val verifiedIdentity = RomIdentity(fireRedSha, "Pokemon FireRed")
        vm.setRomSession(verifiedCompat, verifiedIdentity)

        vm.updateManualParty(listOf(createMon(6), createMon(35)))
        vm.updateEnemyParty(listOf(createMon(9)))
        vm.setIsInBattle(true)
        vm.updatePlayerLocation(PlayerLocation(1, 2, 0, 3, 4, 5, 6, 7, 8, false, true))
        vm.updatePlayerStatStages(StatStages(atk = 2))

        assertEquals(2, vm.playerParty.value.size)
        assertTrue(vm.isInBattle.value)
        assertNotNull(vm.playerLocation.value)

        val unsupportedCompat = RomCompatibility.unsupported("unsupported_hash")
        val unsupportedIdentity = RomIdentity("unsupported_hash", "Unknown Homebrew")
        vm.setRomSession(unsupportedCompat, unsupportedIdentity)

        assertTrue("Player party must be cleared immediately", vm.playerParty.value.isEmpty())
        assertTrue("Enemy party must be cleared immediately", vm.enemyParty.value.isEmpty())
        assertFalse("Battle flag must be cleared", vm.isInBattle.value)
        assertEquals(BattlePresence.UNKNOWN, vm.battlePresence.value)
        assertNull("Location must be cleared immediately", vm.playerLocation.value)
        assertNull("Resolved location must be cleared", vm.resolvedLocation.value)
        assertTrue("Stat stages must be neutral", vm.playerStatStages.value.isNeutral)
        assertEquals(BattleUiSnapshot(), vm.battleUiSnapshot.value)
        assertFalse("Live memory polling must be disabled", vm.runtimeRomTrust.value.mayReadLiveMemory)
    }

    // Minimum requirement 8: Transient invalid read -> does not immediately flicker/clear valid data
    @Test
    fun transientInvalidRead_retainsObservationsWithinDebounceThreshold() {
        val stabilizer = LiveObservationStabilizer(invalidReadThreshold = 5)

        assertEquals(LiveObservationDecision.PUBLISH, stabilizer.onValidRead())
        assertEquals(0, stabilizer.consecutiveInvalidReads)

        // Cycles 1 through 4 are transient failures that must be retained
        for (i in 1..4) {
            val decision = stabilizer.onInvalidRead()
            assertEquals("Invalid read $i must retain prior observation", LiveObservationDecision.RETAIN, decision)
            assertEquals(i, stabilizer.consecutiveInvalidReads)
        }

        // Recovering with a valid read resets the counter
        assertEquals(LiveObservationDecision.PUBLISH, stabilizer.onValidRead())
        assertEquals(0, stabilizer.consecutiveInvalidReads)
    }

    // Minimum requirement 9: Sustained invalid reads -> eventually clear live data / expose unavailable state
    @Test
    fun sustainedInvalidReads_exceedsThresholdAndClearsObservation() {
        val stabilizer = LiveObservationStabilizer(invalidReadThreshold = 5)

        stabilizer.onValidRead()
        for (i in 1..4) {
            assertEquals(LiveObservationDecision.RETAIN, stabilizer.onInvalidRead())
        }

        // 5th consecutive invalid read exceeds threshold and must clear
        val decision = stabilizer.onInvalidRead()
        assertEquals(LiveObservationDecision.CLEAR, decision)
        assertEquals(5, stabilizer.consecutiveInvalidReads)
    }

    // Minimum requirement 10: Unsupported ROM -> verified ROM -> polling resumes normally
    @Test
    fun switchingUnsupportedToVerifiedRom_resumesMemoryPolling() {
        val vm = CompanionViewModel()

        val unsupportedCompat = RomCompatibility.unsupported("unsupported_hash")
        vm.setRomSession(unsupportedCompat, RomIdentity("unsupported_hash", "Unsupported Game"))
        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)

        // Polling tick on unsupported ROM does not run readers
        vm.pollTick()
        assertTrue(vm.playerParty.value.isEmpty())

        // Switch to verified ROM
        val verifiedCompat = RomCompatibility.verified(fireRedProfile, fireRedSha)
        vm.setRomSession(verifiedCompat, RomIdentity(fireRedSha, "Pokemon FireRed"))
        assertTrue(vm.runtimeRomTrust.value.mayReadLiveMemory)
        assertEquals(RomCompatibilityStatus.VERIFIED, vm.runtimeRomTrust.value.status)
    }

    // Minimum requirement E: UI user-facing wording contracts
    @Test
    fun compatibilityMessages_matchRequiredBetaSafetyWording() {
        assertEquals(
            "ROM recognized, but this exact version has not been verified. Live companion data is disabled.",
            RomCompatibilityMessages.detail(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED)
        )
        assertEquals(
            "This ROM can be played normally, but live companion features are unavailable.",
            RomCompatibilityMessages.detail(RomCompatibilityStatus.UNSUPPORTED)
        )
        assertEquals("", RomCompatibilityMessages.detail(RomCompatibilityStatus.VERIFIED))

        assertEquals("Unverified version", RomCompatibilityMessages.badge(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED))
        assertEquals("Unsupported ROM", RomCompatibilityMessages.badge(RomCompatibilityStatus.UNSUPPORTED))
        assertEquals("Verified", RomCompatibilityMessages.badge(RomCompatibilityStatus.VERIFIED))
    }
}
