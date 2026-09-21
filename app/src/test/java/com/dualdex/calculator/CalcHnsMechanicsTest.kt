package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Boundary-level regression for the Gap C4a mechanics gates: base-stat equalizer, random moves,
 * move mechanics, and the still-present badge blocker (issue #9).
 */
class CalcHnsMechanicsTest {

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
    private val fireRed: RomHackProfile get() = bundledProfile("vanilla_firered")

    private fun exactTrust(profile: RomHackProfile): RuntimeRomTrust {
        val hash = profile.sha256Hashes.first()
        return RuntimeRomTrust.from(
            compatibility = com.dualdex.romhack.RomCompatibility.verified(profile, hash),
            activeRomSha256 = hash
        )
    }

    private fun exactHns(): Pair<RomHackProfile, RuntimeRomTrust> {
        val hashed = heartAndSoul.copy(
            sha256Hashes = listOf("edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"),
            isVerified = true,
            memoryLayoutVerified = true
        )
        return hashed to exactTrust(hashed)
    }

    private fun settings(
        baseStatEqualizer: HnsChallengeField = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        randomMoves: HnsChallengeField = HnsChallengeField(observed = true, raw = 0, outOfDomain = false)
    ): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txModeFairyTypes = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txRandomType = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomTypeEffectiveness = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomAbilities = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomMoves = randomMoves,
        txChallengesNoEvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesBaseStatEqualizer = baseStatEqualizer,
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

    private fun request(
        move: String,
        attackerSpecies: String = "Machamp",
        defenderSpecies: String = "Snorlax",
        isCrit: Boolean = false,
        attackerAbility: String? = "None",
        typeSystem: String? = "hns_2_0_5",
        attackerBoosts: StatBlock? = null,
        defenderBoosts: StatBlock? = null
    ) = DamageCalculationRequest(
        gen = 3,
        typeSystem = typeSystem,
        attacker = CalcPokemonInput(
            species = attackerSpecies,
            level = 50,
            ability = attackerAbility,
            boosts = attackerBoosts
        ),
        defender = CalcPokemonInput(species = defenderSpecies, level = 50, ability = "None", boosts = defenderBoosts),
        move = CalcMoveInput(name = move, isCrit = isCrit)
    )

    private fun refused(
        profile: RomHackProfile,
        trust: RuntimeRomTrust,
        move: String,
        snapshot: HnsChallengeSettingsSnapshot = settings(),
        attackerSpecies: String = "Machamp",
        defenderSpecies: String = "Snorlax",
        isCrit: Boolean = false
    ): CalcCapabilityVerdict {
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(move, attackerSpecies, defenderSpecies, isCrit),
            challengeSettings = snapshot
        )
        val value = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("expected refusal for $move, got $outcome")
        assertNull(value.verdict.request)
        return value.verdict
    }

    @Test
    fun `base stat equalizer active adds its own blocker`() {
        val (profile, trust) = exactHns()
        val verdict = refused(
            profile,
            trust,
            "Tackle",
            settings(baseStatEqualizer = HnsChallengeField(observed = true, raw = 1, outOfDomain = false))
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED))
        assertEquals(CalcSupport.UNSUPPORTED, verdict.support)
    }

    @Test
    fun `base stat equalizer unreadable records CHALLENGE_SETTINGS_UNREADABLE`() {
        val (profile, trust) = exactHns()
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Tackle"),
            challengeSettings = settings(baseStatEqualizer = HnsChallengeField(observed = true, outOfDomain = true))
        )
        val verdict = when (outcome) {
            is CalcRequestOutcome.Ready -> outcome.verdict
            is CalcRequestOutcome.Refused -> outcome.verdict
        }
        assertTrue(verdict.limitations.contains(CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED))
    }

    @Test
    fun `random moves active adds its own blocker`() {
        val (profile, trust) = exactHns()
        val verdict = refused(
            profile,
            trust,
            "Tackle",
            settings(randomMoves = HnsChallengeField(observed = true, raw = 1, outOfDomain = false))
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED))
    }

    @Test
    fun `ordinary Tackle clears the move mechanics gate and reaches Ready under C4b`() {
        val (profile, trust) = exactHns()
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Tackle"),
            challengeSettings = settings()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("Tackle should reach Ready under C4b, got $outcome")
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
    }

    @Test
    fun `Return and Hidden Power are refused by the move mechanics gate`() {
        val (profile, trust) = exactHns()
        listOf("Return", "Hidden Power", "Low Kick", "Bullet Seed").forEach { move ->
            val verdict = refused(profile, trust, move)
            assertTrue(
                "$move must be refused by HNS_MOVE_MECHANICS_NOT_MODELLED",
                verdict.limitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED)
            )
        }
    }

    @Test
    fun `item-dependent Fling uses the C3 blocker and not the mechanics blocker`() {
        val (profile, trust) = exactHns()
        val verdict = refused(profile, trust, "Fling")
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
    }

    @Test
    fun `neutral non-STAB ordinary move clears the modifier-order gate`() {
        val (profile, trust) = exactHns()
        // Machamp (Fighting) Tackle (Normal) vs Snorlax (Normal): no STAB, 1x, no modifier.
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Tackle"),
            challengeSettings = settings()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("Tackle should reach Ready under C4b, got $outcome")
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED))
    }

    @Test
    fun `STAB and type effectiveness clear the modifier-order gate under C4b`() {
        val (profile, trust) = exactHns()
        // Charizard (Fire/Flying) Flamethrower (Fire) is STAB.
        val stabOutcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Flamethrower", attackerSpecies = "Charizard"),
            challengeSettings = settings()
        )
        val stabReady = stabOutcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("expected Ready for STAB, got $stabOutcome")
        assertFalse(stabReady.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED))

        // Machamp (Fighting) Karate Chop (Fighting) is STAB and 2x vs Snorlax.
        val seOutcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Karate Chop"),
            challengeSettings = settings()
        )
        val seReady = seOutcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("expected Ready for super effective, got $seOutcome")
        assertFalse(seReady.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED))
    }

    @Test
    fun `critical hit clears the modifier-order gate under C4b`() {
        val (profile, trust) = exactHns()
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Tackle", isCrit = true),
            challengeSettings = settings()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("expected Ready for crit, got $outcome")
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED))
    }

    @Test
    fun `valid stat stages clear the modifier-order gate under C4b`() {
        val (profile, trust) = exactHns()
        val cases = linkedMapOf(
            "attacker Atk +1" to request("Tackle", attackerBoosts = StatBlock(atk = 1)),
            "attacker Atk -1" to request("Tackle", attackerBoosts = StatBlock(atk = -1)),
            "attacker Def +1" to request("Tackle", attackerBoosts = StatBlock(def = 1)),
            "attacker Def -1" to request("Tackle", attackerBoosts = StatBlock(def = -1)),
            "defender Atk +1" to request("Tackle", defenderBoosts = StatBlock(atk = 1)),
            "defender Def -1" to request("Tackle", defenderBoosts = StatBlock(def = -1)),
            "special SpA +2" to request("Tackle", attackerBoosts = StatBlock(spa = 2)),
            "special SpD -2" to request("Tackle", defenderBoosts = StatBlock(spd = -2)),
            "speed stage" to request("Tackle", attackerBoosts = StatBlock(spe = 1))
        )
        cases.forEach { (label, req) ->
            val ready = (CalcRequestBoundary.build(
                profile = profile,
                trust = trust,
                request = req,
                challengeSettings = settings()
            ) as? CalcRequestOutcome.Ready)
                ?: throw AssertionError("$label must reach Ready under C4b")
            assertFalse(
                "$label must clear modifier order gate: ${ready.verdict.limitations}",
                ready.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED)
            )
        }
    }

    @Test
    fun `unsupported modifier order fails closed with HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`() {
        val (profile, trust) = exactHns()
        // Out-of-range stage (+7)
        val outOfRangeOutcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Tackle", attackerBoosts = StatBlock(atk = 7)),
            challengeSettings = settings()
        )
        val outOfRangeRefused = outOfRangeOutcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("expected refusal for out-of-range stage")
        assertTrue(outOfRangeRefused.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED))

        // Unsupported weather (Hail)
        val hailOutcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Tackle").copy(field = CalcFieldInput(weather = "Hail")),
            challengeSettings = settings()
        )
        val hailRefused = hailOutcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("expected refusal for unsupported weather")
        assertTrue(hailRefused.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED))
    }

    @Test
    fun `neutral stat stages clear the modifier-order gate`() {
        val (profile, trust) = exactHns()
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(
                "Tackle",
                attackerBoosts = StatBlock(),
                defenderBoosts = StatBlock()
            ),
            challengeSettings = settings()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("neutral stat stages should reach Ready")
        assertFalse(
            "an explicitly neutral stage block is not a divergence: ${ready.verdict.limitations}",
            ready.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED)
        )
    }

    @Test
    fun `most-covered request reaches ESTIMATED under Gap C4b`() {
        val (profile, trust) = exactHns()
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request("Tackle"),
            challengeSettings = settings()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("most-covered request must reach Ready under C4b, got $outcome")
        val verdict = ready.verdict

        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.BADGE_BOOST_NOT_MODELLED))
        assertEquals(CalcSupport.ESTIMATED, verdict.support)
        assertNotNull(verdict.request)
    }

    @Test
    fun `vanilla requests are unaffected by the H and S mechanics gates`() {
        val profile = fireRed.copy(isVerified = true, memoryLayoutVerified = true)
        val trust = exactTrust(profile)
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = DamageCalculationRequest(
                gen = 3,
                attacker = CalcPokemonInput(species = "Machamp", level = 50, ability = "Guts"),
                defender = CalcPokemonInput(species = "Snorlax", level = 50),
                move = CalcMoveInput(name = "Return")
            )
        )
        val verdict = (outcome as CalcRequestOutcome.Ready).verdict
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.BADGE_BOOST_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED))
    }
}
