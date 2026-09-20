package com.dualdex.pokemon.hns

import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot.Companion.fromNativeArray
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomCompatibilityStatus
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H&S 2.0.5 runtime challenge settings (issue #9): decoding, provenance, trust
 * gating and stale-state behaviour.
 *
 * These tests exercise the production decoding path
 * ([HnsChallengeSettingsSnapshot.fromNativeArray] fed by
 * `LibretroHost.nativeReadChallengeSettings` -> `pokemon_read_challenge_settings_gba`);
 * there is no parallel test-only reader.
 */
class HnsChallengeSettingsTest {

    // ------------------------------------------------------------------
    // Tuple decoding
    // ------------------------------------------------------------------

    @Test
    fun nullOrShortNativeTupleIsUnavailable() {
        for (values in listOf(null, intArrayOf(0), intArrayOf(1, 1, 0, 0))) {
            val snap = fromNativeArray(values)
            assertEquals(HnsChallengeSettingsStatus.UNAVAILABLE, snap.status)
            assertFalse("an unavailable read observes nothing", snap.optionStyle.observed)
            assertNull("no default may be substituted", snap.optionStyleSemantics.let {
                if (it == HnsOptionStyle.UNAVAILABLE) null else it
            })
        }
    }

    @Test
    fun observedZeroIsKnownFalseNotUnknown() {
        // status OBSERVED; every field triple (observed=1, raw=0, invalid=0).
        val values = IntArray(1 + 3 * 17)
        values[0] = 1
        for (i in 0 until 17) values[1 + 3 * i] = 1

        val snap = fromNativeArray(values)
        assertEquals(HnsChallengeSettingsStatus.OBSERVED, snap.status)
        assertTrue("a zero byte read from memory IS an observation", snap.optionStyle.observed)
        assertEquals(0, snap.optionStyle.raw)
        assertEquals(HnsOptionStyle.PER_MOVE_SPLIT, snap.optionStyleSemantics)
        assertTrue(snap.txModeFairyTypes.observed && snap.txModeFairyTypes.raw == 0)
        assertEquals(false, snap.txModeFairyTypes.observedFlag)
        assertTrue(snap.txChallengesLevelCap.observed && snap.txChallengesLevelCap.raw == 0)
        assertTrue(snap.txModeLegendaryAbilities.observed && snap.txModeLegendaryAbilities.raw == 0)
        assertTrue(snap.txRandomType.observed && snap.txRandomType.raw == 0)
    }

    @Test
    fun representativeEnabledSettingsDecode() {
        // optionStyle=1, Fairy=1, RandomType=1, TypeEffectiveness=1, RandomAbilities=0,
        // RandomMoves=0, NoEVs=1, BST equalizer=3 (500), Mirror=1, MirrorThief=0,
        // ScalingIVs=2 (HARD), ScalingEVs=3, MaxPartyIVs=1, Sturdy=0,
        // LevelCap=1 (NORMAL), ExpMultiplier=2 (x2.0), Legendary=0.
        val observed = intArrayOf(
            1, 1, // optionStyle
            1, 1, // Fairy
            1, 1, // RandomType
            1, 1, // TypeEffectiveness
            1, 0, // RandomAbilities
            1, 0, // RandomMoves
            1, 1, // NoEVs
            1, 3, // BST equalizer
            1, 1, // Mirror
            1, 0, // MirrorThief
            1, 2, // ScalingIVs
            1, 3, // ScalingEVs
            1, 1, // MaxPartyIVs
            1, 0, // Sturdy
            1, 1, // LevelCap
            1, 2, // ExpMultiplier
            1, 0  // LegendaryAbilities
        )
        val values = IntArray(1 + 3 * 17)
        values[0] = 1
        for (field in 0 until 17) {
            values[1 + 3 * field] = observed[2 * field]
            values[2 + 3 * field] = observed[2 * field + 1]
        }

        val snap = fromNativeArray(values)
        assertEquals(HnsChallengeSettingsStatus.OBSERVED, snap.status)
        assertEquals(HnsOptionStyle.TYPE_BASED, snap.optionStyleSemantics)
        assertTrue(snap.txModeFairyTypes.observedFlag == true)
        assertTrue(snap.txRandomType.observedFlag == true)
        assertTrue(snap.txRandomTypeEffectiveness.observedFlag == true)
        assertTrue(snap.txChallengesNoEvs.observedFlag == true)
        assertEquals(3, snap.txChallengesBaseStatEqualizer.raw)
        assertTrue(snap.txChallengesMirror.observedFlag == true)
        assertEquals(0, snap.txChallengesMirrorThief.raw)
        assertEquals(2, snap.txChallengesTrainerScalingIvs.raw)
        assertEquals(3, snap.txChallengesTrainerScalingEvs.raw)
        assertEquals(1, snap.txChallengesMaxPartyIvs.raw)
        assertEquals(false, snap.txModeSturdy.observedFlag)
        assertEquals(1, snap.txChallengesLevelCap.raw)
        assertEquals(2, snap.txChallengesExpMultiplier.raw)
        assertEquals(false, snap.txModeLegendaryAbilities.observedFlag)
        // Fields left at zero in the same snapshot stay observed-off, not unknown.
        assertTrue(snap.txRandomAbilities.observed && snap.txRandomAbilities.raw == 0)
        assertTrue(snap.txRandomMoves.observed && snap.txRandomMoves.raw == 0)
    }

    @Test
    fun outOfDomainValueIsExplicitlyInvalid() {
        // LevelCap raw 3: the pinned source only assigns OFF/NORMAL/HARD (0..2).
        val values = IntArray(1 + 3 * 17) { idx ->
            when {
                idx == 0 -> 2 // OBSERVED_INVALID
                idx % 3 == 1 -> 1 // observed
                else -> 0
            }
        }
        // LevelCap is field index 14: triple at 1+42..44.
        values[1 + 3 * 14 + 1] = 3
        values[1 + 3 * 14 + 2] = 1

        val snap = fromNativeArray(values)
        assertEquals(HnsChallengeSettingsStatus.OBSERVED_INVALID, snap.status)
        val levelCap = snap.txChallengesLevelCap
        assertTrue("the raw value must be preserved", levelCap.observed && levelCap.raw == 3)
        assertTrue("the out-of-domain flag must be explicit", levelCap.outOfDomain)
        assertNull("an out-of-domain field must not be presented as a boolean", levelCap.observedFlag)
        // optionStyle in the same snapshot stays clean: it is a 1-bit source field.
        assertTrue(snap.optionStyle.observed && !snap.optionStyle.outOfDomain)
        assertEquals(HnsOptionStyle.PER_MOVE_SPLIT, snap.optionStyleSemantics)
    }

    @Test
    fun unknownStatusCodeIsUnavailable() {
        val values = IntArray(1 + 3 * 17)
        values[0] = 99
        assertEquals(HnsChallengeSettingsStatus.UNAVAILABLE, fromNativeArray(values).status)
    }

    // ------------------------------------------------------------------
    // Production state path: trust gating and stale-state behaviour
    // ------------------------------------------------------------------

    private val hnsProfile = RomHackProfile(
        id = "heart_and_soul",
        name = "Pokemon Heart & Soul",
        baseGame = "Emerald",
        gameId = 8,
        engine = "pokeemerald-expansion",
        hasPhysSpecSplit = true,
        headerTitles = listOf("HEARTSOUL", "HNS", "POKEHNS", "HEART", "SOUL"),
        // Deliberately still empty: H&S stays recognised-but-not-exact-verified.
        sha256Hashes = emptyList(),
        isVerified = true,
        memoryLayoutVerified = true,
        gameDataPackId = "hns_2_0_5",
    )

    private val exactHash = "e".repeat(64)

    /** Trust like today's production: H&S recognised, not exact-verified. */
    private fun recognisedUnverified(): RomCompatibility = RomCompatibility(
        status = RomCompatibilityStatus.RECOGNIZED_UNVERIFIED,
        profile = hnsProfile,
        detectedProfile = hnsProfile,
        matchMethod = ProfileMatchMethod.HEADER_TITLE,
        sha256 = "some_hns_hash",
        reason = "header title matched",
    )

    private class RecordingCoordinator : LibretroCoreCoordinator() {
        var challengeReads = 0
        var nextSnapshot: HnsChallengeSettingsSnapshot? = null

        override fun readChallengeSettings(gameId: Int): HnsChallengeSettingsSnapshot {
            challengeReads++
            return nextSnapshot ?: HnsChallengeSettingsSnapshot()
        }
    }

    private fun observedSnapshot(optionStyle: Int, fairy: Int, levelCap: Int) =
        HnsChallengeSettingsSnapshot(
            status = HnsChallengeSettingsStatus.OBSERVED,
            optionStyle = HnsChallengeField(observed = true, raw = optionStyle),
            txModeFairyTypes = HnsChallengeField(observed = true, raw = fairy),
            txChallengesLevelCap = HnsChallengeField(observed = true, raw = levelCap)
        )

    @Test
    fun recognisedButUnverifiedHnsNeverReceivesASettingsSnapshot() {
        val coordinator = RecordingCoordinator()
        val vm = CompanionViewModel(coreCoordinator = coordinator)
        // Serve an authoritative-looking snapshot if the reader were (wrongly) asked.
        coordinator.nextSnapshot = observedSnapshot(optionStyle = 1, fairy = 1, levelCap = 1)

        vm.setRomSession(recognisedUnverified(), RomIdentity("some_hns_hash", "H&S"))
        vm.pollTick()

        assertEquals(
            "a recognised-but-unverified ROM must not be read at all",
            0,
            coordinator.challengeReads
        )
        assertNull(
            "recognised-but-unverified H&S must not receive a settings snapshot",
            vm.challengeSettings.value
        )
    }

    @Test
    fun exactVerifiedSessionPublishesObservedSettings() {
        val coordinator = RecordingCoordinator()
        val vm = CompanionViewModel(coreCoordinator = coordinator)
        coordinator.nextSnapshot = observedSnapshot(optionStyle = 1, fairy = 0, levelCap = 2)

        vm.setRomSession(
            RomCompatibility.verified(
                hnsProfile.copy(sha256Hashes = listOf(exactHash)),
                exactHash
            ),
            RomIdentity(exactHash, "H&S 2.0.5")
        )
        vm.pollTick()

        assertEquals("the exact-trusted ROM must be read", 1, coordinator.challengeReads)
        val snap = vm.challengeSettings.value
        assertNotNull(snap)
        assertEquals(HnsOptionStyle.TYPE_BASED, snap!!.optionStyleSemantics)
        assertEquals(0, snap.txModeFairyTypes.raw)
        assertEquals(2, snap.txChallengesLevelCap.raw)
    }

    @Test
    fun romSwitchMustNotRetainStaleSettings() {
        val coordinator = RecordingCoordinator()
        val vm = CompanionViewModel(coreCoordinator = coordinator)
        coordinator.nextSnapshot = observedSnapshot(optionStyle = 0, fairy = 1, levelCap = 0)

        vm.setRomSession(
            RomCompatibility.verified(
                hnsProfile.copy(sha256Hashes = listOf(exactHash)),
                exactHash
            ),
            RomIdentity(exactHash, "H&S 2.0.5")
        )
        vm.pollTick()
        assertNotNull(vm.challengeSettings.value)

        // Switch to a ROM the trust layer does not exactly verify (or to no ROM at all).
        // The previous game's settings must be dropped immediately and never re-read.
        coordinator.nextSnapshot = observedSnapshot(optionStyle = 1, fairy = 0, levelCap = 1)
        vm.setRomSession(recognisedUnverified(), RomIdentity("other_hash", "Other ROM"))
        vm.pollTick()

        assertNull(
            "a ROM/profile switch must not retain the previous game's settings",
            vm.challengeSettings.value
        )
        assertEquals("the untrusted session must not be read", 1, coordinator.challengeReads)
    }

    @Test
    fun failedReadDropsTheSnapshotInsteadOfKeepingStaleValues() {
        val coordinator = RecordingCoordinator()
        val vm = CompanionViewModel(coreCoordinator = coordinator)

        vm.setRomSession(
            RomCompatibility.verified(
                hnsProfile.copy(sha256Hashes = listOf(exactHash)),
                exactHash
            ),
            RomIdentity(exactHash, "H&S 2.0.5")
        )

        coordinator.nextSnapshot = observedSnapshot(optionStyle = 1, fairy = 1, levelCap = 0)
        vm.pollTick()
        assertNotNull(vm.challengeSettings.value)

        // The reader starts failing (e.g. save-block torn down): the observation must go
        // UNAVAILABLE rather than being replaced with a source default.
        coordinator.nextSnapshot = null
        vm.pollTick()
        assertNull(
            "a failed read must not fall back to defaults or the previous observation",
            vm.challengeSettings.value
        )
    }

    @Test
    fun coordinatorDegradesToUnavailableWithoutNativeLibrary() {
        // On the JVM there is no native library: the production coordinator must degrade to
        // the UNAVAILABLE snapshot, never throw and never fabricate defaults.
        val snap = LibretroCoreCoordinator().readChallengeSettings(8)
        assertEquals(HnsChallengeSettingsStatus.UNAVAILABLE, snap.status)
        assertFalse(snap.optionStyle.observed)
    }
}
