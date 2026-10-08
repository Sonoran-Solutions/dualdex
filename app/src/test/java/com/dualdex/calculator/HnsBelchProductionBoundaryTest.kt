package com.dualdex.calculator

import com.dualdex.battle.*
import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Belch (move 562) admission. Eligibility comes only from the attacker party member's observed
 * `PartyState.ateBerry`; these tests exercise the production boundary and the shipped QuickJS bundle.
 */
class HnsBelchProductionBoundaryTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "ci.sh").isFile }

    private fun observation(attacker: Boolean, item: String? = null, species: String = if (attacker) "Machamp" else "Blastoise",
                            types: List<String> = if (attacker) listOf("Fighting") else listOf("Water")) =
        Baseline.observation(if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, HeartAndSoul205DataPack.getSpeciesByName(species)?.id ?: error("$species missing"), species, types,
            checkNotNull(HnsAbilityRegistry.classify("Insomnia").abilityId),
            item?.let(HnsItemRegistry::resolveIdByName) ?: 0, "Insomnia",
            Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0), 2)

    /** A neutral observation; [ateBerry] is the party-member flag under test (null = unread). */
    private fun attacker(ateBerry: Boolean?, item: String? = null): BattlerRuntimeObservation =
        observation(true, item).let { it.copy(state = it.state.copy(rawAttack = 1, rawDefense = 1,
            contactReactionStateObserved = true, protectedMethod = 0, ateBerry = ateBerry)) }

    private fun defender(species: String = "Blastoise", types: List<String> = listOf("Water")): BattlerRuntimeObservation =
        observation(false, species = species, types = types).let { it.copy(state = it.state.copy(rawAttack = 1, rawDefense = 1,
            contactReactionStateObserved = true, protectedMethod = 0)) }

    private fun request(defenderSpecies: String = "Blastoise") = DamageCalculationRequest(
        attacker = CalcPokemonInput("Machamp", 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
        defender = CalcPokemonInput(defenderSpecies, 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
        move = CalcMoveInput("Belch"), field = CalcFieldInput(gameType = "Singles"))

    private fun build(a: BattlerRuntimeObservation, d: BattlerRuntimeObservation = defender(), defenderSpecies: String = "Blastoise") =
        CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request(defenderSpecies), Baseline.challengeSettings, a, d, activeBattle = true)

    private fun production(json: String): JSONObject {
        val p = ProcessBuilder("node", File(root, "tools/calc-bundler/run_production_request.js").path,
            File(root, "app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(json) }
        val result = JSONObject(p.inputStream.bufferedReader().readText())
        assertEquals(0, p.waitFor())
        return result
    }

    private fun limitations(outcome: CalcRequestOutcome) = when (outcome) {
        is CalcRequestOutcome.Ready -> outcome.verdict.limitations
        is CalcRequestOutcome.Refused -> outcome.verdict.limitations
    }

    @Test fun `Belch is exactly the source-generated single-member family`() {
        assertEquals(setOf(562), Hns205MoveEffects.fixedSingleHitBelchMoveIds)
        assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_BELCH, HnsMoveMechanicsRegistry.classify(562).category)
        assertEquals("EFFECT_BELCH", Hns205MoveEffects.effectById[562])
        assertTrue(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_BELCH.isSupportedFixedSingleHit)
    }

    @Test fun `observed ateBerry true on the authoritative slot is eligible with no held item`() {
        val outcome = build(attacker(ateBerry = true, item = null))
        assertTrue("refused: ${limitations(outcome)}", outcome is CalcRequestOutcome.Ready)
        assertFalse(limitations(outcome).any { it.name.startsWith("HNS_BELCH") })
    }

    @Test fun `observed ateBerry false refuses as not eaten and never as ready`() {
        val outcome = build(attacker(ateBerry = false))
        assertTrue(outcome is CalcRequestOutcome.Refused)
        assertTrue(CalcLimitation.HNS_BELCH_BERRY_NOT_EATEN in limitations(outcome))
    }

    @Test fun `unread ateBerry is unknown and refuses`() {
        val outcome = build(attacker(ateBerry = null))
        assertTrue(outcome is CalcRequestOutcome.Refused)
        assertTrue(CalcLimitation.HNS_BELCH_BERRY_STATE_UNKNOWN in limitations(outcome))
        assertFalse(CalcLimitation.HNS_BELCH_BERRY_NOT_EATEN in limitations(outcome))
    }

    @Test fun `a held Berry does not prove consumption`() {
        val outcome = build(attacker(ateBerry = false, item = "Oran Berry"))
        assertTrue(outcome is CalcRequestOutcome.Refused)
        assertTrue(CalcLimitation.HNS_BELCH_BERRY_NOT_EATEN in limitations(outcome))
    }

    @Test fun `ateBerry true remains eligible when the current held item is none`() {
        assertTrue(build(attacker(ateBerry = true, item = null)) is CalcRequestOutcome.Ready)
    }

    @Test fun `a successful Belch returns sixteen normal rolls from the shipped engine`() {
        val outcome = build(attacker(ateBerry = true)) as? CalcRequestOutcome.Ready ?: error("Belch refused")
        val response = production(buildCalcRequestJson(outcome.request))
        assertTrue("$response", response.optBoolean("success"))
        assertEquals(16, response.getJSONArray("damage").length())
        assertEquals(0, response.optJSONArray("repeatedStrike")?.length() ?: 0)
        assertTrue(response.getInt("maxDamage") > 0)
    }

    @Test fun `an immune target with established eligibility is zero damage on sixteen rolls`() {
        val outcome = build(attacker(ateBerry = true), defender("Steelix", listOf("Steel", "Ground")), "Steelix") as? CalcRequestOutcome.Ready
            ?: error("immune Belch refused")
        val response = production(buildCalcRequestJson(outcome.request))
        assertTrue("$response", response.optBoolean("success"))
        assertEquals(16, response.getJSONArray("damage").length())
        assertEquals(0, response.getInt("maxDamage"))
    }

    @Test fun `forged ateBerry or descriptor packets are rejected by the shipped bundle`() {
        val outcome = build(attacker(ateBerry = true)) as? CalcRequestOutcome.Ready ?: error("Belch refused")
        val json = JSONObject(buildCalcRequestJson(outcome.request))
        val forgedFalse = JSONObject(json.toString())
        forgedFalse.getJSONObject("attacker").put("hnsAttackerAteBerry", false)
        assertFalse(production(forgedFalse.toString()).optBoolean("success"))
        val missing = JSONObject(json.toString())
        missing.getJSONObject("attacker").remove("hnsAttackerAteBerry")
        assertFalse(production(missing.toString()).optBoolean("success"))
        val wrongDescriptor = JSONObject(json.toString())
        wrongDescriptor.getJSONObject("move").put("hnsDescriptorSha256", "0".repeat(64))
        assertFalse(production(wrongDescriptor.toString()).optBoolean("success"))
    }

    @Test fun `the native tuple decodes ateBerry only from its versioned tail`() {
        // raw[0] = 2 is OBSERVED; any other status returns no per-field operands at all.
        val raw = IntArray(180).also { it[0] = 2 }
        assertNull(HnsBattlerRuntimeState.fromNativeArray(raw).ateBerry)
        assertNull(HnsBattlerRuntimeState.fromNativeArray(IntArray(180) { 0 }.also { it[0] = 0; it[178] = 1; it[179] = 1 }).ateBerry)
        raw[178] = 1; raw[179] = 1
        assertEquals(true, HnsBattlerRuntimeState.fromNativeArray(raw).ateBerry)
        raw[179] = 0
        assertEquals(false, HnsBattlerRuntimeState.fromNativeArray(raw).ateBerry)
        raw[178] = 0
        assertNull(HnsBattlerRuntimeState.fromNativeArray(raw).ateBerry)
        raw[178] = 1; raw[179] = 2
        assertNull(HnsBattlerRuntimeState.fromNativeArray(raw).ateBerry)
        assertNull(HnsBattlerRuntimeState.fromNativeArray(IntArray(178) { if (it == 0) 2 else 1 }).ateBerry)
    }
}
