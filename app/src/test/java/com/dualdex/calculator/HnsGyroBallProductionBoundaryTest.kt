package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HnsGyroBallProductionBoundaryTest {
    private val root get() = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it,"ci.sh").isFile }
    private fun observation(attacker: Boolean, speed: Int=100, stage: Int=0, ability: String="Insomnia", item: String?=null) =
        Baseline.observation(if(attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if(attacker) 0 else 1, if(attacker) 68 else 9, if(attacker) "Machamp" else "Blastoise",
            if(attacker) listOf("Fighting") else listOf("Water"), checkNotNull(HnsAbilityRegistry.classify(ability).abilityId),
            item?.let(HnsItemRegistry::resolveIdByName) ?: 0, ability,
            Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0),2).let { o ->
            o.copy(state=o.state.copy(rawAttack=151,rawSpAttack=151,rawDefense=109,rawSpDefense=109,
                rawSpeed=speed,statStages=List(8) { if(it==3) stage else 0 },
                hp=if(attacker) 200 else 60000,maxHp=if(attacker) 200 else 60000)) }
    private fun request()=DamageCalculationRequest(
        attacker=CalcPokemonInput(species="Machamp",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=0),
        defender=CalcPokemonInput(species="Blastoise",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=1),
        move=CalcMoveInput("Gyro Ball"),field=CalcFieldInput(gameType="Singles"))
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
    @Test fun `shipped calculator rejects forged source descriptors and invalid speed operands`() {
        val json=checkNotNull(buildCalcRequestJson(ready().request))
        val mutations: List<(JSONObject)->Unit> = listOf(
            { it.getJSONObject("move").put("hnsMoveEffect","EFFECT_HIT") },
            { it.getJSONObject("move").put("hnsMoveFlags",org.json.JSONArray()) },
            { it.getJSONObject("attacker").getJSONObject("rawStats").put("speed",65536) },
            { it.getJSONObject("defender").getJSONArray("statStages").put(3,7) },
            { it.getJSONObject("attacker").put("hnsEffectiveAbilityId",33) },
            { it.getJSONObject("attacker").put("hnsEffectiveHoldEffect","HOLD_EFFECT_CHOICE_SCARF") },
            { it.getJSONObject("defender").remove("hnsSideStatuses"); Unit })
        for(mutate in mutations) {
            val changed=JSONObject(json);mutate(changed)
            assertFalse(calculate(changed.toString()).getBoolean("success"))
        }
    }
    @Test fun `integer power boundaries and source zero`() {
        assertEquals("Variable",HeartAndSoul205DataPack.getMoveByName("Gyro Ball")!!.powerDisplay)
        for((a,d,bp) in listOf(Triple(100L,100L,26),Triple(100L,236L,60),Triple(100L,240L,61),
            Triple(100L,596L,150),Triple(40L,500L,150),Triple(0L,100L,1),Triple(100L,0L,1),Triple(103L,237L,58)))
            assertEquals(bp,HnsEffectiveSpeedAuthority.gyroBallPower(a,d))
        val bound=ready(observation(true,1,-6)).request
        assertEquals(0L,HnsEffectiveSpeedAuthority.forRequest(bound,HnsAbilitySide.ATTACKER).speed)
        assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_GYRO_BALL,HnsMoveMechanicsRegistry.classify(360).category)
        assertFalse(360 in Hns205MoveEffects.ordinaryMoveIds)
        rolls(checkNotNull(buildCalcRequestJson(bound)))
    }
    @Test fun `boundary rejects missing invalid stale status and Unburden operands`() {
        for(attacker in listOf(true,false)) for(kind in listOf("raw","stage","status","slot","unburden")) {
            var a=observation(true);var d=observation(false)
            val o=if(attacker) a else d
            val changed=if(kind=="unburden") observation(attacker,ability="Unburden") else o.copy(state=when(kind) {
                "raw" -> o.state.copy(rawSpeed=0)
                "stage" -> o.state.copy(stagesObserved=false)
                "status" -> o.state.copy(status1=64)
                else -> o.state.copy(partySlot=7)
            })
            if(attacker) a=changed else d=changed
            val outcome=build(a,d)
            assertTrue("$attacker $kind $outcome",outcome is CalcRequestOutcome.Refused)
            assertTrue(CalcLimitation.HNS_EFFECTIVE_SPEED_UNKNOWN in (outcome as CalcRequestOutcome.Refused).verdict.blockingLimitations)
        }
        val bound=ready().request
        val forged=bound.copy(hnsLiveBattleState=bound.hnsLiveBattleState!!.copy(attackerRawStats=CalcRawStats(1,1,1,1,1)))
        val rebound=build(r=forged) as CalcRequestOutcome.Ready
        assertEquals(100,rebound.request.hnsLiveBattleState!!.attackerRawStats!!.speed)
    }
    @Test fun `Technician examines dynamic power and Bulletproof preserves ballistic flag`() {
        for(speed in listOf(236,240)) {
            val neutral=rolls(checkNotNull(buildCalcRequestJson(ready(d=observation(false,speed)).request)))
            val technician=rolls(checkNotNull(buildCalcRequestJson(ready(observation(true,ability="Technician"),observation(false,speed)).request)))
            if(speed==236) assertTrue(technician.last()>neutral.last()) else assertEquals(neutral,technician)
        }
        assertEquals(List(16) { 0 },rolls(checkNotNull(buildCalcRequestJson(ready(d=observation(false,ability="Bulletproof")).request))))
    }
    @Test fun `all admitted engine vectors traverse the real production boundary`() {
        val entries=JSONObject(File(root,"tools/hns-damage-oracle/corpus.json").readText()).getJSONArray("entries")
        var checked=0
        for(index in 0 until entries.length()) {
            val entry=entries.getJSONObject(index)
            val scenario=entry.getJSONObject("scenario")
            if(!scenario.getString("id").startsWith("gyro-ball-") || scenario.getString("surface")!="modelled") continue
            val observed=entry.getJSONObject("observed")
            val speeds=observed.getJSONObject("effectiveSpeeds")
            fun battler(role: String): BattlerRuntimeObservation {
                val a=role=="attacker"
                val source=scenario.getJSONObject(role)
                val stats=source.getJSONObject("stats")
                val o=observed.getJSONObject(role)
                val runtime=o.getJSONObject("runtime")
                val stages=source.getJSONObject("stages")
                val speed=speeds.getJSONObject(role)
                val badges=o.getJSONObject("badgeBoosts")
                val item=if(source.isNull("itemLabel")) null else source.getString("itemLabel")
                return observation(a,stats.getInt("speed"),speed.getInt("stage"),source.getString("abilityLabel"),item).let { b ->
                    b.copy(state=b.state.copy(speciesId=o.getInt("speciesId"),
                        battlerIndex=if((scenario.getString("attackerSide")=="player")==a) 0 else 1,
                        badgesObserved=true,
                        types=(0 until o.getJSONArray("types").length()).map {
                            HnsBattlerTypeObservation(true,Baseline.rawTypeId(o.getJSONArray("types").getString(it)),false) },
                        rawAttack=stats.getInt("attack"),rawDefense=stats.getInt("defense"),
                        rawSpAttack=stats.getInt("spAttack"),rawSpDefense=stats.getInt("spDefense"),
                        statStages=List(8) { when(it) { 1 -> stages.optInt("attack");2 -> stages.optInt("defense");
                            3 -> speed.getInt("stage");4 -> stages.optInt("spAttack");5 -> stages.optInt("spDefense");else -> 0 } },
                        sideStatuses=speed.getInt("sideStatuses"),fieldStatuses=observed.getInt("fieldStatuses"),
                        battleWeather=when(scenario.getJSONObject("field").getString("weather")) {"rain" -> 1;"sun" -> 8;else -> 0},
                        badgeBoostSpe=speed.getInt("badge")==1,badgeBoostAtk=badges.getBoolean("attack"),
                        badgeBoostDef=badges.getBoolean("defense"),badgeBoostSpa=badges.getBoolean("spAttack"),badgeBoostSpd=badges.getBoolean("spDefense"),
                        volatileSlowStartTimer=runtime.getInt("slowStartTimer"),volatileTransformed=runtime.getInt("transformed")==1,
                        volatileBoosterEnergyActivated=runtime.getInt("boosterEnergyActivated")==1,
                        volatileParadoxBoostedStat=runtime.getInt("paradoxBoostedStat"),volatileEmbargo=runtime.getInt("embargo")==1))
                }
            }
            val r=request().copy(attacker=request().attacker.copy(species=scenario.getJSONObject("attacker").getString("speciesLabel")),move=CalcMoveInput("Gyro Ball",isCrit=scenario.getBoolean("crit")))
            val outcome=build(battler("attacker"),battler("defender"),r)
            assertTrue("${scenario.getString("id")}: $outcome",outcome is CalcRequestOutcome.Ready)
            val bound=(outcome as CalcRequestOutcome.Ready).request
            for(side in HnsAbilitySide.entries) {
                val role=if(side==HnsAbilitySide.ATTACKER) "attacker" else "defender"
                assertEquals(scenario.getString("id"),speeds.getJSONObject(role).getLong("total"),
                    HnsEffectiveSpeedAuthority.forRequest(bound,side).speed)
            }
            assertEquals(scenario.getString("id"),(0..15).map {entry.getJSONArray("rolls").getInt(it)},
                rolls(checkNotNull(buildCalcRequestJson(bound))))
            checked++
        }
        assertTrue(checked>=60)
    }

}
