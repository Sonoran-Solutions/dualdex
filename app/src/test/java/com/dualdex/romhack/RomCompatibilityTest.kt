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
        battleStateReadVerified = true,
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
        battleStateReadVerified = true,
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

    // Follow-up 1: Verified ROM with empty/unavailable party data must show "Waiting for party data", NOT bare "Verified"
    @Test
    fun verifiedRom_emptyParty_showsWaitingForPartyData_notBareVerifiedBadge() {
        val verifiedTrust = RuntimeRomTrust.from(
            RomCompatibility.verified(fireRedProfile, fireRedSha),
            fireRedSha
        )

        val selectorLabel = com.dualdex.companion.ui.PartyScreenView.resolveSelectorEmptyLabel(verifiedTrust)
        assertEquals("Waiting for party data", selectorLabel)

        val (detailTitle, detailBody) = com.dualdex.companion.ui.PartyScreenView.resolveDetailEmptyState(verifiedTrust)
        assertEquals("Waiting for party data", detailTitle)
        assertEquals("Party data will appear when it can be read from the running game.", detailBody)
    }

    @Test
    fun unverifiedAndUnsupportedRom_partyEmptyState_showsCompatibilityBadgeAndDetail() {
        val unverifiedTrust = RuntimeRomTrust.from(
            RomCompatibility.recognizedUnverified(fireRedProfile, "unverified_sha", ProfileMatchMethod.HEADER_TITLE, "Header matched"),
            "unverified_sha"
        )
        assertEquals("Unverified version", com.dualdex.companion.ui.PartyScreenView.resolveSelectorEmptyLabel(unverifiedTrust))
        val (unverifiedTitle, unverifiedDetail) = com.dualdex.companion.ui.PartyScreenView.resolveDetailEmptyState(unverifiedTrust)
        assertEquals("Unverified version", unverifiedTitle)
        assertEquals(RomCompatibilityMessages.detail(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED), unverifiedDetail)

        val unsupportedTrust = RuntimeRomTrust.from(
            RomCompatibility.unsupported("unsupported_sha"),
            "unsupported_sha"
        )
        assertEquals("Unsupported ROM", com.dualdex.companion.ui.PartyScreenView.resolveSelectorEmptyLabel(unsupportedTrust))
        val (unsupportedTitle, unsupportedDetail) = com.dualdex.companion.ui.PartyScreenView.resolveDetailEmptyState(unsupportedTrust)
        assertEquals("Unsupported ROM", unsupportedTitle)
        assertEquals(RomCompatibilityMessages.detail(RomCompatibilityStatus.UNSUPPORTED), unsupportedDetail)

        val noRomTrust = RuntimeRomTrust()
        assertEquals("No game loaded", com.dualdex.companion.ui.PartyScreenView.resolveSelectorEmptyLabel(noRomTrust))
        val (noRomTitle, noRomDetail) = com.dualdex.companion.ui.PartyScreenView.resolveDetailEmptyState(noRomTrust)
        assertEquals("No game loaded", noRomTitle)
        assertEquals("Party data will appear when a supported game is running.", noRomDetail)
    }

    // Follow-up 2: Assert that pollTick() under RECOGNIZED_UNVERIFIED / UNSUPPORTED never calls core reader path
    @Test
    fun pollTick_underUnverifiedOrUnsupported_neverInvokesLiveMemoryReaders() {
        var readPartyCalls = 0
        var readEnemyPartyCalls = 0
        var readLocationCalls = 0
        var readPresenceCalls = 0

        val trackingCoordinator = object : LibretroCoreCoordinator() {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                readPartyCalls++
                return null
            }
            override fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                readEnemyPartyCalls++
                return null
            }
            override fun readPlayerLocation(gameId: Int): PlayerLocation? {
                readLocationCalls++
                return null
            }
            override fun readBattlePresence(gameId: Int): Int {
                readPresenceCalls++
                return 0
            }
        }

        val vm = CompanionViewModel(coreCoordinator = trackingCoordinator)

        // 1. RECOGNIZED_UNVERIFIED
        val unverifiedCompat = RomCompatibility.recognizedUnverified(fireRedProfile, "unverified_sha", ProfileMatchMethod.HEADER_TITLE, "Header matched")
        vm.setRomSession(unverifiedCompat, RomIdentity("unverified_sha", "Pokemon FireRed Hack"))
        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)

        vm.pollTick()
        assertEquals(0, readPartyCalls)
        assertEquals(0, readEnemyPartyCalls)
        assertEquals(0, readLocationCalls)
        assertEquals(0, readPresenceCalls)

        // 2. UNSUPPORTED
        val unsupportedCompat = RomCompatibility.unsupported("unsupported_sha")
        vm.setRomSession(unsupportedCompat, RomIdentity("unsupported_sha", "Unknown Hack"))
        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)

        vm.pollTick()
        assertEquals(0, readPartyCalls)
        assertEquals(0, readEnemyPartyCalls)
        assertEquals(0, readLocationCalls)
        assertEquals(0, readPresenceCalls)

        // 3. Switch to VERIFIED -> readers must now be invoked
        val verifiedCompat = RomCompatibility.verified(fireRedProfile, fireRedSha)
        vm.setRomSession(verifiedCompat, RomIdentity(fireRedSha, "Pokemon FireRed"))
        assertTrue(vm.runtimeRomTrust.value.mayReadLiveMemory)

        vm.pollTick()
        assertTrue("readPartyFromCore must be called when verified", readPartyCalls > 0)
        assertTrue("readEnemyPartyFromCore must be called when verified", readEnemyPartyCalls > 0)
        assertTrue("readPlayerLocation must be called when verified", readLocationCalls > 0)
        assertTrue("readBattlePresence must be called when verified", readPresenceCalls > 0)
    }

    // Follow-up 3: Enemy read failure (null) debounces via stabilizer, whereas legitimate empty read clears immediately
    @Test
    fun enemyReadSemantics_distinguishFailureNullFromLegitimateEmptyArray() {
        var enemyPartyToReturn: Array<ParsedPokemon>? = null

        val trackingCoordinator = object : LibretroCoreCoordinator() {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? = arrayOf(createMon(1))
            override fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? = enemyPartyToReturn
            override fun readBattlePresence(gameId: Int): Int = 1 // In battle
        }

        val vm = CompanionViewModel(coreCoordinator = trackingCoordinator)
        val verifiedCompat = RomCompatibility.verified(fireRedProfile, fireRedSha)
        vm.setRomSession(verifiedCompat, RomIdentity(fireRedSha, "Pokemon FireRed"))

        // Establish an initial observed enemy
        enemyPartyToReturn = arrayOf(createMon(150))
        vm.pollTick()
        assertEquals(1, vm.enemyParty.value.size)
        assertEquals(150, vm.enemyParty.value[0].species)

        // Reader failure (returns null): stabilizer retains observed enemy for 1..4 ticks
        enemyPartyToReturn = null
        for (i in 1..4) {
            vm.pollTick()
            assertEquals("Enemy must be retained during transient read failure tick $i", 1, vm.enemyParty.value.size)
        }

        // 5th consecutive failure tick clears
        vm.pollTick()
        assertTrue("Sustained read failure must clear enemy", vm.enemyParty.value.isEmpty())

        // Re-establish active enemy
        enemyPartyToReturn = arrayOf(createMon(150))
        vm.pollTick()
        assertEquals(1, vm.enemyParty.value.size)

        // Legitimate empty array (reader succeeded, found 0 enemies): clears immediately without debouncing
        enemyPartyToReturn = emptyArray()
        vm.pollTick()
        assertTrue("Legitimate empty read must publish empty immediately", vm.enemyParty.value.isEmpty())
    }

    // ------------------------------------------------------------------
    // Heart & Soul 2.0.5 (issue #40, first implementation phase)
    //
    // The H&S profile now carries offsets taken from the exact upstream 2.0.5 build, but its
    // sha256Hashes list is still empty because no H&S ROM has been promoted to VERIFIED. These
    // tests pin that boundary: better-evidenced addresses must never widen trust on their own.
    // ------------------------------------------------------------------

    private val heartAndSoulProfile = RomHackProfile(
        id = "heart_and_soul",
        name = "Pokemon Heart & Soul",
        baseGame = "Emerald",
        gameId = 8,
        engine = "pokeemerald-expansion",
        headerTitles = listOf("HEARTSOUL", "HNS", "POKEHNS", "HEART", "SOUL"),
        sha256Hashes = emptyList(),
        isVerified = true,
        memoryLayoutVerified = true,
        battleStateReadVerified = true,
        playerPartyOffset = 0x02034768L,
        enemyPartyOffset = 0x020342B8L,
        battleUiVerified = false,
        interactiveControlsVerified = false
    )

    @Test
    fun heartAndSoulWithMatchingHeaderButEmptyHashList_staysRecognizedUnverified() {
        // The real shipped header title from the upstream `make hns` build (gbafix -t"POKEMON HNS").
        val header = makeHeader("POKEMON HNS", "BPEE")
        val unknownSha = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"

        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = header,
            sha256 = unknownSha,
            profiles = listOf(heartAndSoulProfile)
        )

        assertEquals("heart_and_soul", result.profile.id)
        assertEquals(ProfileMatchMethod.HEADER_TITLE, result.matchMethod)
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, result.status)
        assertFalse("An empty hash list can never verify a ROM", result.isVerified)
        assertFalse(result.mayReadLiveMemory)

        val trust = RuntimeRomTrust.from(result, unknownSha)
        assertFalse(trust.exactRuntimeVerified)
        assertFalse("H&S must not unlock live-memory reads yet", trust.mayReadLiveMemory)
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, trust.status)
    }

    @Test
    fun heartAndSoulIsUnreachableForUnknownAndUnsupportedRoms() {
        // A ROM that merely shares the BPEE game code must not inherit the H&S layout.
        val genericEmeraldHeader = makeHeader("SOME HACK", "BPEE")
        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = genericEmeraldHeader,
            sha256 = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            profiles = listOf(heartAndSoulProfile)
        )

        assertNotEquals("heart_and_soul", result.profile.id)
        assertFalse(result.mayReadLiveMemory)
        assertEquals(RomCompatibilityStatus.UNSUPPORTED, result.status)

        val trust = RuntimeRomTrust.from(result, "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        assertFalse(trust.mayReadLiveMemory)
        assertEquals(RomCompatibilityStatus.UNSUPPORTED, trust.status)
    }

    // ------------------------------------------------------------------
    // Gap C4e: the bundled H&S profile now carries the exact 2.0.5 SHA that C4d /
    // C4e runtime evidence established. Trust is pinned to that exact bit string:
    // a single flipped bit stays RECOGNIZED_UNVERIFIED and never unlocks live memory.
    // ------------------------------------------------------------------

    private val c4eExactHnsSha = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"

    private fun bundledHeartAndSoul(): RomHackProfile {
        val dir = generateSequence(java.io.File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { java.io.File(it, "app/src/main/assets/profiles") }
            .firstOrNull { it.isDirectory }
            ?: throw AssertionError("Unable to locate bundled ROM profiles")
        val file = java.io.File(dir, "heart_and_soul.json")
        assertTrue("bundled heart_and_soul.json is missing", file.isFile)
        return ProfileLoader.parseProfile(file.readText())
    }

    @Test
    fun bundledHeartAndSoulExactPromotedHashUnlocksLiveMemory() {
        val profile = bundledHeartAndSoul()
        assertEquals(listOf(c4eExactHnsSha), profile.sha256Hashes)
        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = makeHeader("POKEMON HNS", "BPEE"),
            sha256 = c4eExactHnsSha,
            profiles = listOf(profile)
        )
        assertEquals("heart_and_soul", result.profile.id)
        assertEquals(ProfileMatchMethod.EXACT_SHA256, result.matchMethod)
        assertEquals(RomCompatibilityStatus.VERIFIED, result.status)
        assertTrue("the exact promoted SHA must unlock live memory", result.mayReadLiveMemory)

        val trust = RuntimeRomTrust.from(result, c4eExactHnsSha)
        assertTrue(trust.exactRuntimeVerified)
        assertTrue(trust.mayReadLiveMemory)
        // The independent capability flags are unchanged by the hash promotion.
        assertFalse(profile.battleUiVerified)
        assertFalse(profile.interactiveControlsVerified)
    }

    // ------------------------------------------------------- battle-state scope

    /**
     * The exact-hash gate and the battle-state gate are separate decisions.
     *
     * Repairing a profile's SHA-256 values makes `exactRuntimeVerified` reachable, and that flag is
     * the universal live-memory gate — so a corrected hash must not be able to promote a read whose
     * configured address was never proven for the retail image. `gBattleMons` is exactly such an
     * address: it is a linker-ordered EWRAM placement inside the section that also holds asset
     * arrays, so only the retail build can prove it (see
     * `tools/calc-goldens/audit_vanilla_layout.py --firered-rom`).
     *
     * This test drives the real poller for an exact-trusted vanilla ROM and asserts that the
     * structurally-proven reads still run while the battle-state readers do not.
     */
    @Test
    fun vanillaBattleStateReadsStayClosedWhilePartyAndLocationStayOpen() {
        var readPartyCalls = 0
        var readEnemyPartyCalls = 0
        var readLocationCalls = 0
        var readPresenceCalls = 0
        var readStatStageCalls = 0

        val coordinator = object : LibretroCoreCoordinator() {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                readPartyCalls++
                return arrayOf(createMon(1))
            }

            override fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                readEnemyPartyCalls++
                return arrayOf(createMon(2))
            }

            override fun readPlayerLocation(gameId: Int): PlayerLocation? {
                readLocationCalls++
                return null
            }

            override fun readBattlePresence(gameId: Int): Int {
                readPresenceCalls++
                return 1
            }

            override fun readBattleStatStages(gameId: Int, battler: Int): IntArray {
                readStatStageCalls++
                return IntArray(7)
            }
        }

        // A template that claims the hash and the layout but NOT the battle-state addresses.
        val vanillaProfile = fireRedProfile.copy(battleStateReadVerified = false)
        val vm = CompanionViewModel(coreCoordinator = coordinator)
        vm.setRomSession(
            RomCompatibility.verified(vanillaProfile, fireRedSha),
            RomIdentity(fireRedSha, "Pokemon FireRed")
        )

        val trust = vm.runtimeRomTrust.value
        assertTrue("an exact hash must still authorize live memory", trust.mayReadLiveMemory)
        assertFalse(
            "an exact hash must not authorize battle-state reads whose addresses are unproven",
            trust.mayReadBattleState
        )

        vm.pollTick()

        assertTrue("party reads must still run", readPartyCalls > 0)
        assertTrue("location reads must still run", readLocationCalls > 0)
        assertEquals("enemy party reads depend on the battle base", 0, readEnemyPartyCalls)
        assertEquals("battle presence dereferences the battle base", 0, readPresenceCalls)
        assertEquals("stat stages dereference the battle base", 0, readStatStageCalls)
        assertTrue(
            "the battle presence must be withheld, not answered as 'no battle'",
            vm.battlePresence.value == com.dualdex.battle.BattlePresence.UNKNOWN
        )
        assertFalse("no battle may be reported from an unreadable base", vm.isInBattle.value)
    }

    /** The other half: a profile that DOES prove its battle offsets keeps the full scope. */
    @Test
    fun provenBattleStateAddressesKeepTheBattleReadsOpen() {
        var readPresenceCalls = 0
        val coordinator = object : LibretroCoreCoordinator() {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? = arrayOf(createMon(1))

            override fun readBattlePresence(gameId: Int): Int {
                readPresenceCalls++
                return 1
            }
        }
        val provenProfile = fireRedProfile.copy(battleStateReadVerified = true)
        val vm = CompanionViewModel(coreCoordinator = coordinator)
        vm.setRomSession(
            RomCompatibility.verified(provenProfile, fireRedSha),
            RomIdentity(fireRedSha, "Pokemon FireRed")
        )
        assertTrue(vm.runtimeRomTrust.value.mayReadBattleState)
        vm.pollTick()
        assertTrue("presence must be read when the addresses are proven", readPresenceCalls > 0)
    }

    @Test
    fun bundledVanillaProfilesDoNotClaimProvenBattleStateAddresses() {
        // The bundled FireRed dump's `gBattleMons` base 0x02023F90 does not appear anywhere in the
        // retail image, while the party addresses it sits beside do (audit evidence, §7.3), so the
        // profile must not assert this until the address is actually proven.
        val dir = generateSequence(java.io.File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { java.io.File(it, "app/src/main/assets/profiles") }
            .firstOrNull { it.isDirectory }
            ?: throw AssertionError("Unable to locate bundled ROM profiles")
        for (id in listOf("vanilla_firered", "vanilla_emerald")) {
            val profile = ProfileLoader.parseProfile(java.io.File(dir, "$id.json").readText())
            assertTrue("$id must still be an exactly supported build", profile.isSupportedVanillaGen3())
            assertFalse(
                "$id must not claim proven battle-state addresses until they are proven",
                profile.battleStateReadVerified
            )
        }
        // H&S 2.0.5 does: its battle globals are compiled-symbol verified and runtime cross-checked.
        val hns = bundledHeartAndSoul()
        assertTrue("heart_and_soul battle offsets are proven", hns.battleStateReadVerified)
    }

    @Test
    fun bundledHeartAndSoulOneBitDifferentHashStaysUnverified() {
        val profile = bundledHeartAndSoul()
        // Flip the final hex digit only.
        val oneBitOff = c4eExactHnsSha.dropLast(1) + if (c4eExactHnsSha.last() == 'b') "c" else "b"
        assertNotEquals(c4eExactHnsSha, oneBitOff)
        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = makeHeader("POKEMON HNS", "BPEE"),
            sha256 = oneBitOff,
            profiles = listOf(profile)
        )
        assertEquals(ProfileMatchMethod.HEADER_TITLE, result.matchMethod)
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, result.status)
        assertFalse(result.mayReadLiveMemory)

        val trust = RuntimeRomTrust.from(result, oneBitOff)
        assertFalse(trust.exactRuntimeVerified)
        assertFalse("a near-miss hash must never unlock live memory", trust.mayReadLiveMemory)
    }

    @Test
    fun bundledHeartAndSoulRecognizedHeaderWithoutExactHashStaysUnverified() {
        val profile = bundledHeartAndSoul()
        val unknown = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
        val result = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = makeHeader("POKEMON HNS", "BPEE"),
            sha256 = unknown,
            profiles = listOf(profile)
        )
        assertEquals("heart_and_soul", result.profile.id)
        assertEquals(ProfileMatchMethod.HEADER_TITLE, result.matchMethod)
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, result.status)
        assertFalse(result.mayReadLiveMemory)
        assertFalse(RuntimeRomTrust.from(result, unknown).mayReadLiveMemory)
    }

    @Test
    fun heartAndSoulOffsetsAreCompiledEvidenceValues() {
        // Guards against the stale 0x340F4 / 0x345A4 pair returning: those were scan-derived for an
        // older H&S build and match neither the tagged source nor the compiled 2.0.5 image.
        assertEquals(0x02034768L, heartAndSoulProfile.playerPartyOffset) // gPlayerParty
        assertEquals(0x020342B8L, heartAndSoulProfile.enemyPartyOffset)  // gEnemyParty
        assertNotEquals(0x020340F4L, heartAndSoulProfile.playerPartyOffset)
        assertNotEquals(0x020345A4L, heartAndSoulProfile.enemyPartyOffset)
        assertTrue("sha256Hashes must stay empty until runtime validation", heartAndSoulProfile.sha256Hashes.isEmpty())
        assertFalse(heartAndSoulProfile.battleUiVerified)
        assertFalse(heartAndSoulProfile.interactiveControlsVerified)
    }
}
