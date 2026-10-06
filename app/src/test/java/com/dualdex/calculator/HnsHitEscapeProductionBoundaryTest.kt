package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HnsHitEscapeProductionBoundaryTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it,"ci.sh").isFile }
    private fun observation(attacker: Boolean, speed: Int=100, stage: Int=0, ability: String="Insomnia", item: String?=null) =
        Baseline.observation(if(attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if(attacker) 0 else 1, if(attacker) 68 else 9, if(attacker) "Machamp" else "Blastoise",
            if(attacker) listOf("Fighting") else listOf("Water"), checkNotNull(HnsAbilityRegistry.classify(ability).abilityId),
            item?.let(HnsItemRegistry::resolveIdByName) ?: 0, ability,
            Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0),2).let { o ->
            o.copy(state=o.state.copy(rawAttack=151,rawSpAttack=151,rawDefense=109,rawSpDefense=109,
                rawSpeed=speed,statStages=List(8) { if(it==3) stage else 0 },
                hp=if(attacker) 200 else 60000,maxHp=if(attacker) 200 else 60000)) }
    private fun request(move: String="U-Turn")=DamageCalculationRequest(
        attacker=CalcPokemonInput(species="Machamp",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=0),
        defender=CalcPokemonInput(species="Blastoise",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=1),
        move=CalcMoveInput(move),field=CalcFieldInput(gameType="Singles"))
    private fun build(a: BattlerRuntimeObservation=observation(true),d: BattlerRuntimeObservation=observation(false),r: DamageCalculationRequest=request()) =
        CalcRequestBoundary.build(Baseline.profile,Baseline.trust,r,Baseline.challengeSettings,a,d,activeBattle=true)
    private fun ready(a: BattlerRuntimeObservation=observation(true),d: BattlerRuntimeObservation=observation(false)) =
        (build(a,d) as? CalcRequestOutcome.Ready) ?: error("Unexpected refusal: ${build(a,d)}")
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
    @Test fun `three pinned hits preserve power Technician contact and pivot boundary`() {
        for ((move,id,bp) in listOf(Triple("U-Turn",369,70),Triple("Volt Switch",521,70),Triple("Flip Turn",740,60))) {
            fun admitted(ability: String="Insomnia",defender: String="Insomnia") =
                build(observation(true,ability=ability),observation(false,ability=defender),request(move)) as CalcRequestOutcome.Ready
            val base=admitted().request
            val json=buildCalcRequestJson(base)
            val m=JSONObject(json).getJSONObject("move")
            assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_ESCAPE,HnsMoveMechanicsRegistry.classify(id).category)
            assertFalse(id in Hns205MoveEffects.ordinaryMoveIds)
            assertEquals("FIXED_SINGLE_HIT_ESCAPE",m.getString("hnsMoveFamily"))
            assertFalse(m.getBoolean("hnsIsOrdinary"))
            assertEquals(bp,m.getJSONObject("overrides").getInt("basePower"))
            assertEquals((id!=521),m.getBoolean("hnsMakesContact"))
            assertEquals(bp.toString(),HeartAndSoul205DataPack.getMoveByName(move)!!.powerDisplay)
            val neutral=rolls(json)
            val tech=rolls(buildCalcRequestJson(admitted("Technician").request))
            if(id==740) assertTrue(tech.last()>neutral.last()) else assertEquals(neutral,tech)
            val claws=rolls(buildCalcRequestJson(admitted("Tough Claws").request))
            if(id==521) assertEquals(neutral,claws) else assertTrue(claws.last()>neutral.last())
            val fluffy=rolls(buildCalcRequestJson(admitted(defender="Fluffy").request))
            if(id==521) assertEquals(neutral,fluffy) else assertTrue(fluffy.last()<neutral.last())
            assertEquals(neutral,rolls(buildCalcRequestJson(admitted("Long Reach","Fluffy").request)))
            assertEquals(neutral,rolls(buildCalcRequestJson(admitted("Sheer Force").request)))
            val forged=build(r=base.copy(moveOverride=CalcMoveOverride(999,"Ice","Special"))) as CalcRequestOutcome.Ready
            assertEquals(bp,forged.request.moveOverride!!.basePower)
            val unknownSemi=JSONObject(json)
            unknownSemi.getJSONObject("defender").remove("hnsSemiInvulnerableState")
            assertFalse(calculate(unknownSemi.toString()).getBoolean("success"))
            val switchData=JSONObject(json).put("bench",listOf("Pikachu")).put("replacement","Blastoise").put("switchInAbility","Drizzle")
            assertEquals(neutral,rolls(switchData.toString()))
            for (mutate in listOf<(JSONObject)->Unit>(
                {it.put("hnsMoveFamily","ORDINARY_PROVEN_EQUIVALENT")},
                {it.put("hnsMoveEffect","EFFECT_HIT")},
                {it.put("name","Tackle")},
                {it.put("hnsMoveId",33)},
                {it.put("hnsFixedSingleHit",false)},
                {it.put("hnsIsOrdinary",true)},
                {it.put("hnsMakesContact",id==521)},
                {it.put("hnsUnknownContact",true)},
                {it.put("hnsSheerForceAffected",true)},
                {it.put("hnsMoveAbilityFlags",org.json.JSONArray(listOf("punchingMove")))},
                {it.put("hnsMoveFlags",org.json.JSONArray(listOf("ignoresTargetAbility")))},
                {it.getJSONObject("overrides").put("basePower",999)})) {
                val changed=JSONObject(json);mutate(changed.getJSONObject("move"))
                assertFalse(calculate(changed.toString()).getBoolean("success"))
            }
        }
    }
    @Test fun `existing refusals stay closed`() {
        for(move in listOf("U-Turn","Volt Switch","Flip Turn")) {
            assertTrue(build(r=request(move).copy(field=CalcFieldInput(gameType="Doubles"))) is CalcRequestOutcome.Refused)
            val doubles=build(observation(true).let {it.copy(state=it.state.copy(battlersCount=4))},
                observation(false).let {it.copy(state=it.state.copy(battlersCount=4))},
                request(move).copy(field=CalcFieldInput(gameType="Doubles"))) as CalcRequestOutcome.Refused
            assertTrue(doubles.verdict.blockingLimitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
            for(d in listOf(observation(false).let {it.copy(state=it.state.copy(volatileSubstitute=true))},
                observation(false).let {it.copy(state=it.state.copy(volatileSemiInvulnerable=1))},
                observation(false).let {it.copy(state=it.state.copy(volatilesObserved=false))},
                observation(false).let {it.copy(state=it.state.copy(volatileGastroAcid=true))}))
                assertTrue("$move ${d.state}: ${build(d=d,r=request(move))}",build(d=d,r=request(move)) is CalcRequestOutcome.Refused)
            assertTrue(build(observation(true).let {it.copy(state=it.state.copy(volatileElectrified=true))},r=request(move)) is CalcRequestOutcome.Refused)
            assertTrue(build(observation(true,ability="Mold Breaker"),observation(false,ability=if(move=="Volt Switch") "Volt Absorb" else "Fluffy"),request(move)) is CalcRequestOutcome.Refused)
        }
        for(move in listOf("Double Kick","Fly","Fissure","Pursuit"))
            assertTrue(build(r=request(move)) is CalcRequestOutcome.Refused)
        assertTrue(build(r=request("Tackle")) is CalcRequestOutcome.Ready)
    }
    @Test fun `every admitted pinned engine vector traverses the production boundary`() {
        val entries=JSONObject(File(root,"tools/hns-damage-oracle/corpus.json").readText()).getJSONArray("entries")
        var count=0
        for(i in 0 until entries.length()) {
            val entry=entries.getJSONObject(i); val scenario=entry.getJSONObject("scenario")
            if(!scenario.getString("id").startsWith("hit-escape-") || scenario.getString("surface")!="modelled") continue
            val observed=entry.getJSONObject("observed")
            fun battler(a: Boolean): BattlerRuntimeObservation {
                val role=if(a) "attacker" else "defender"
                val source=scenario.getJSONObject(role); val o=observed.getJSONObject(role)
                val stats=source.getJSONObject("stats"); val stages=source.getJSONObject("stages");val runtime=o.getJSONObject("runtime")
                val item=if(source.isNull("itemLabel")) null else source.getString("itemLabel")
                return observation(a,stats.getInt("speed"),ability=source.getString("abilityLabel"),item=item).let { b ->
                    b.copy(state=b.state.copy(speciesId=o.getInt("speciesId"),
                        types=(0 until o.getJSONArray("types").length()).map { HnsBattlerTypeObservation(true,Baseline.rawTypeId(o.getJSONArray("types").getString(it)),false) },
                        rawAttack=stats.getInt("attack"),rawDefense=stats.getInt("defense"),rawSpAttack=stats.getInt("spAttack"),rawSpDefense=stats.getInt("spDefense"),
                        hp=stats.getInt("hp"),maxHp=stats.getInt("maxHp"),
                        statStages=List(8) {when(it) {1->stages.optInt("attack");2->stages.optInt("defense");4->stages.optInt("spAttack");5->stages.optInt("spDefense");else->0}},
                        sideStatuses=if(a) 0 else (if(scenario.getJSONObject("field").getBoolean("reflect")) 1 else 0) or (if(scenario.getJSONObject("field").getBoolean("lightScreen")) 2 else 0),
                        fieldStatuses=observed.getInt("fieldStatuses"),
                        battleWeather=when(scenario.getJSONObject("field").getString("weather")){"rain"->1;"sun"->8;else->0},
                        volatileChargeTimer=runtime.getInt("chargeTimer")))
                }
            }
            val r=request(scenario.getJSONObject("move").getString("label")).copy(
                attacker=request().attacker.copy(species=scenario.getJSONObject("attacker").getString("speciesLabel")),
                defender=request().defender.copy(species=scenario.getJSONObject("defender").getString("speciesLabel")),
                move=CalcMoveInput(scenario.getJSONObject("move").getString("label"),isCrit=scenario.getBoolean("crit")))
            val outcome=build(battler(true),battler(false),r)
            assertTrue("${scenario.getString("id")}: $outcome",outcome is CalcRequestOutcome.Ready)
            val bound=(outcome as CalcRequestOutcome.Ready).request
            assertEquals(scenario.getString("id"),(0..15).map {entry.getJSONArray("rolls").getInt(it)},rolls(buildCalcRequestJson(bound)))
            count++
        }
        assertEquals(57,count)
    }

}
