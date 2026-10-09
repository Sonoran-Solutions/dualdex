package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HnsRapidSpinProductionBoundaryTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it,"ci.sh").isFile }
    private fun observation(attacker: Boolean, ability: String="Insomnia", item: String?=null) =
        Baseline.observation(if(attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if(attacker) 0 else 1, if(attacker) 68 else 9, if(attacker) "Machamp" else "Blastoise",
            if(attacker) listOf("Fighting") else listOf("Water"), checkNotNull(HnsAbilityRegistry.classify(ability).abilityId),
            item?.let(HnsItemRegistry::resolveIdByName) ?: 0, ability,
            Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0),2).let { o ->
            o.copy(state=o.state.copy(rawAttack=151,rawSpAttack=151,rawDefense=109,rawSpDefense=109,
                rawSpeed=100,statStages=List(8) { 0 },
                hp=if(attacker) 200 else 60000,maxHp=if(attacker) 200 else 60000)) }
    private fun request(move: String="Rapid Spin")=DamageCalculationRequest(
        attacker=CalcPokemonInput(species="Machamp",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=0),
        defender=CalcPokemonInput(species="Blastoise",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=1),
        move=CalcMoveInput(move),field=CalcFieldInput(gameType="Singles"))
    private fun build(a: BattlerRuntimeObservation=observation(true),d: BattlerRuntimeObservation=observation(false),r: DamageCalculationRequest=request()) =
        CalcRequestBoundary.build(Baseline.profile,Baseline.trust,r,Baseline.challengeSettings,a,d,activeBattle=true)
    private fun admitted(ability: String="Insomnia") =
        (build(observation(true,ability=ability)) as? CalcRequestOutcome.Ready) ?: error("Unexpected refusal for $ability")
    private fun calculate(json: String): JSONObject {
        val p=ProcessBuilder("node",File(root,"tools/calc-bundler/run_production_request.js").path,
            File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(json) }
        val result=JSONObject(p.inputStream.bufferedReader().readText())
        assertEquals(0,p.waitFor())
        return result
    }
    private fun rolls(json: String): List<Int> {
        val result=calculate(json)
        assertTrue(result.toString(),result.getBoolean("success"))
        return (0..15).map { result.getJSONArray("damage").getInt(it) }
    }

    @Test fun `pinned Rapid Spin is one Singles selected hit with source Sheer Force predicate true`() {
        assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_RAPID_SPIN,HnsMoveMechanicsRegistry.classify(229).category)
        assertTrue(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_RAPID_SPIN.isSupportedFixedSingleHit)
        assertFalse(229 in Hns205MoveEffects.ordinaryMoveIds)
        assertFalse(229 in Hns205MoveEffects.unknownSheerForceMoveIds)
        assertEquals(true,Hns205MoveEffects.sheerForceAffectedById[229])
        assertEquals(50,HeartAndSoul205DataPack.getMoveByName("Rapid Spin")!!.powerDisplay.toInt())
        val m=JSONObject(buildCalcRequestJson(admitted().request)).getJSONObject("move")
        assertEquals("FIXED_SINGLE_HIT_RAPID_SPIN",m.getString("hnsMoveFamily"))
        assertEquals("EFFECT_RAPID_SPIN",m.getString("hnsMoveEffect"))
        assertFalse(m.getBoolean("hnsIsOrdinary"))
        assertTrue(m.getBoolean("hnsSheerForceAffected"))
        assertFalse(m.getBoolean("hnsUnknownSheerForce"))
        assertTrue(m.getBoolean("hnsMakesContact"))
        assertEquals(50,m.getJSONObject("overrides").getInt("basePower"))
    }

    @Test fun `Sheer Force boosts the selected hit and Technician keeps power 50 eligible`() {
        val neutral=rolls(buildCalcRequestJson(admitted().request))
        val sheer=rolls(buildCalcRequestJson(admitted("Sheer Force").request))
        assertTrue("Sheer Force must apply the source ×1.3 modifier", sheer.last()>neutral.last())
        val tech=rolls(buildCalcRequestJson(admitted("Technician").request))
        assertTrue("Technician applies to source power 50 (≤60)", tech.last()>neutral.last())
    }

    @Test fun `forged or unresolved Rapid Spin metadata fails closed in QuickJS`() {
        val json=buildCalcRequestJson(admitted().request)
        for (mutate in listOf<(JSONObject)->Unit>(
            {it.put("hnsMoveFamily","ORDINARY_PROVEN_EQUIVALENT")},
            {it.put("hnsMoveEffect","EFFECT_HIT")},
            {it.put("name","Tackle")},
            {it.put("hnsMoveId",33)},
            {it.put("hnsFixedSingleHit",false)},
            {it.put("hnsIsOrdinary",true)},
            {it.put("hnsSheerForceAffected",false)},
            {it.put("hnsUnknownSheerForce",true)},
            {it.put("hnsMakesContact",false)},
            {it.put("hnsUnknownContact",true)},
        )) {
            val forged=JSONObject(json).getJSONObject("move")
            mutate(forged)
            val root=JSONObject(json).put("move",forged)
            assertFalse(root.toString(),calculate(root.toString()).getBoolean("success"))
        }
        val unknownSemi=JSONObject(json)
        unknownSemi.getJSONObject("defender").remove("hnsSemiInvulnerableState")
        assertFalse(calculate(unknownSemi.toString()).getBoolean("success"))
    }

    @Test fun `forged base power cannot override the admitted move`() {
        val forged=build(r=request().copy(moveOverride=CalcMoveOverride(999,"Ice","Special"))) as CalcRequestOutcome.Ready
        assertEquals(50,forged.request.moveOverride!!.basePower)
    }
}
