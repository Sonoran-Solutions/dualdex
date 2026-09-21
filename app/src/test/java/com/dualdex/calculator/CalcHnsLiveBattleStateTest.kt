package com.dualdex.calculator

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsBattlerTypeObservation
import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gap C4a R1: the "ordinary safe path" must not treat static species/move operands as the live
 * battle truth. H&S mutates those operands during battle (`SET_BATTLER_TYPE`, Power Trick,
 * `SetTypeBeforeUsingMove`, transient state), so an active battle whose mutable classes are not
 * authoritatively observed is refused by [CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED].
 *
 * These tests drive the production [CalcRequestBoundary], so a screen that drops the runtime
 * observation fails here rather than silently calculating from the static record.
 */
class CalcHnsLiveBattleStateTest {

    private fun bundledProfile(id: String): RomHackProfile {
        val dir = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles") }
            .firstOrNull { it.isDirectory }
            ?: throw AssertionError("Unable to locate bundled ROM profiles")
        val file = File(dir, "$id.json")
        assertTrue("bundled profile $id.json is missing", file.isFile)
        return ProfileLoader.parseProfile(file.readText())
    }

    private val heartAndSoul: RomHackProfile get() = bundledProfile("heart_and_soul")

    private fun exactHns(): Pair<RomHackProfile, RuntimeRomTrust> {
        val hashed = heartAndSoul.copy(
            sha256Hashes = listOf("edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"),
            isVerified = true,
            memoryLayoutVerified = true
        )
        val trust = RuntimeRomTrust.from(
            compatibility = com.dualdex.romhack.RomCompatibility.verified(hashed, hashed.sha256Hashes.first()),
            activeRomSha256 = hashed.sha256Hashes.first()
        )
        return hashed to trust
    }

    /** Every rule-changing setting observed at its ordinary value; no challenge is active. */
    private fun settings(): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txModeFairyTypes = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txRandomType = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomTypeEffectiveness = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomAbilities = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomMoves = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesNoEvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesBaseStatEqualizer = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesMirror = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesMirrorThief = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesTrainerScalingIvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesTrainerScalingEvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesMaxPartyIvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txModeSturdy = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txChallengesLevelCap = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesExpMultiplier = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txModeLegendaryAbilities = HnsChallengeField(observed = true, raw = 1, outOfDomain = false)
    )

    private fun observation(
        partySlot: Int,
        types: List<Int>,
        status: HnsBattlerRuntimeStatus = HnsBattlerRuntimeStatus.OBSERVED
    ): BattlerRuntimeObservation = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = status,
            battlerIndex = 0,
            partySlot = partySlot,
            abilityId = 0,
            abilityOutOfDomain = false,
            types = types.map { HnsBattlerTypeObservation(observed = true, raw = it, outOfDomain = false) },
            itemId = 0,
            itemOutOfDomain = false
        ),
        abilityIdentity = DeclaredAbility.EmptySlot,
        itemIdentity = null
    )

    private fun liveInput(species: String, partySlot: Int) = CalcPokemonInput(
        species = species,
        level = 50,
        ability = "None",
        abilityId = 0,
        origin = CalcInputOrigin.LIVE_READ,
        partySlot = partySlot
    )

    private fun request(attackerSpecies: String = "Machamp", defenderSpecies: String = "Snorlax", move: String = "Tackle") =
        DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = liveInput(attackerSpecies, 0),
            defender = liveInput(defenderSpecies, 0),
            move = CalcMoveInput(name = move)
        )

    private fun refused(
        request: DamageCalculationRequest,
        playerBattlerState: BattlerRuntimeObservation? = null,
        enemyBattlerState: BattlerRuntimeObservation? = null
    ): CalcCapabilityVerdict {
        val (profile, trust) = exactHns()
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request,
            challengeSettings = settings(),
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            activeBattle = true
        )
        val value = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("expected refusal in an active battle, got $outcome")
        assertNull(value.verdict.request)
        return value.verdict
    }

    // ---------------------------------------------------------------- R1 gate

    @Test
    fun `active battle without an authoritative observation adds the live battle state blocker`() {
        val verdict = refused(request())
        assertTrue(
            "an unobserved active battler must fail closed: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.BADGE_BOOST_NOT_MODELLED))
    }

    @Test
    fun `observed live types that match the static record still block while battle stat words are unobserved`() {
        // Machamp is Fighting (raw 2); Snorlax is Normal (raw 1). The type class is provably
        // neutral, but Power Trick can still have rewritten the raw battle stat words with
        // unchanged stages, so the request cannot clear the live-state gate.
        val verdict = refused(
            request = request(),
            playerBattlerState = observation(partySlot = 0, types = listOf(2)),
            enemyBattlerState = observation(partySlot = 0, types = listOf(1))
        )
        assertTrue(
            "unobserved battle stat words must fail closed: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
        )
    }

    @Test
    fun `observed live Water defender after Soak cannot clear the ordinary-safe path`() {
        // Static Snorlax is Normal and Thunder Punch (Electric) is 1x; a live Water typing makes
        // the real H&S calculation 2x. The gate must block, and the modifier-order detector must
        // see the live typing rather than the static 1x.
        val verdict = refused(
            request = request(move = "Thunder Punch"),
            enemyBattlerState = observation(partySlot = 0, types = listOf(12)) // Water
        )
        assertTrue(
            "a live type that differs from the static record must block: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
        )
        assertFalse(
            "under C4b, 2x type effectiveness is modeled by calculateHnsDamage: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED)
        )
    }

    @Test
    fun `a third non-empty live type blocks because the calculator carries at most two`() {
        val verdict = refused(
            request = request(),
            enemyBattlerState = observation(partySlot = 0, types = listOf(1, 3, 12)) // Normal/Flying/Water
        )
        assertTrue(
            "a third non-empty live type cannot be represented: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
        )
    }

    @Test
    fun `a slot-mismatched observation is unobserved and blocks`() {
        // The observation names battler party slot 1 while the participant is slot 0: it is not
        // this participant's live state, so no class is observed.
        val verdict = refused(
            request = request(),
            playerBattlerState = observation(partySlot = 1, types = listOf(2)),
            enemyBattlerState = observation(partySlot = 0, types = listOf(1))
        )
        assertTrue(
            "a stale/bench observation must not count as this participant's live state: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
        )
    }

    @Test
    fun `an out-of-battle manual hypothetical does not trip the live battle state gate`() {
        val (profile, trust) = exactHns()
        val manual = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(species = "Machamp", level = 50, ability = "None"),
            defender = CalcPokemonInput(species = "Snorlax", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Tackle")
        )
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = manual,
            challengeSettings = settings(),
            activeBattle = false
        )
        val verdict = when (outcome) {
            is CalcRequestOutcome.Ready -> outcome.verdict
            is CalcRequestOutcome.Refused -> outcome.verdict
        }
        assertFalse(
            "a manual hypothetical is not an active battle: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
        )
    }

    // ------------------------------------------------- per-class policy behaviour

    @Test
    fun `a fully authoritatively observed live battle state clears the live battle state blocker`() {
        // Policy-level positive control: the gate is per mutable class, not an unconditional
        // "live read" prohibition. Battle stat words / dynamic move type / transient state have
        // no runtime reader yet (Gap C4b), so this shape is not reachable through the boundary
        // today, but the policy must clear precisely when every class is observed.
        val (profile, trust) = exactHns()
        val rules = CalcRequestBoundary.resolveHnsRuntimeRules(profile, trust, settings())
        var enriched = CalcDataOverrides.enrichRequest(profile, request(), rules)
        enriched = enriched.copy(
            hnsLiveBattleState = CalcHnsLiveBattleState(
                attackerTypes = enriched.attackerOverride?.types,
                defenderTypes = enriched.defenderOverride?.types,
                attackerBattleStatWordsObserved = true,
                defenderBattleStatWordsObserved = true,
                dynamicMoveTypeObserved = true,
                transientStateObserved = true
            )
        )
        val verdict = CalcCapabilityPolicy.evaluate(profile, trust, enriched)
        assertFalse(
            "every mutable class is observed, so the live-state gate must clear: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
        )
        // The badge blocker is independent and must remain.
        assertTrue(verdict.limitations.contains(CalcLimitation.BADGE_BOOST_NOT_MODELLED))
    }

    @Test
    fun `a live battler type id outside the pinned domain blocks rather than resolving`() {
        val outOfDomain = BattlerRuntimeObservation(
            state = HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED,
                battlerIndex = 0,
                partySlot = 0,
                abilityId = 0,
                types = listOf(
                    HnsBattlerTypeObservation(
                        observed = true,
                        raw = HnsBattlerRuntimeStateIds.TYPE_ID_MAX + 5,
                        outOfDomain = true
                    )
                ),
                itemId = 0
            ),
            abilityIdentity = DeclaredAbility.EmptySlot
        )
        val verdict = refused(request = request(), enemyBattlerState = outOfDomain)
        assertTrue(
            "an out-of-domain live type is unobserved and must block: ${verdict.limitations}",
            verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
        )
    }
}
