package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HnsRolloutProductionBoundaryTest {
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
    private fun request(move: String="Rollout")=DamageCalculationRequest(
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
    @Test fun `both source families and all power states use computed Technician threshold`() {
        for(move in listOf("Rollout","Ice Ball")) for(curl in listOf(false,true)) for(timer in 0..4) {
            fun a(ability: String)=observation(true,ability=ability).let { it.copy(state=it.state.copy(
                rolloutTimer=timer,defenseCurl=curl,multipleTurns=timer>0,lockedMove=if(move=="Rollout") 205 else 301)) }
            val ordinary=build(a("Insomnia"),r=request(move)) as CalcRequestOutcome.Ready
            val boosted=build(a("Technician"),r=request(move)) as CalcRequestOutcome.Ready
            val bp=30*(1 shl timer)*(if(curl) 2 else 1)
            assertEquals(bp,HnsRolloutAuthority.forRequest(ordinary.request).basePower)
            val neutral=rolls(buildCalcRequestJson(ordinary.request))
            val technician=rolls(buildCalcRequestJson(boosted.request))
            if(bp<=60) assertTrue(technician.last()>neutral.last()) else assertEquals(neutral,technician)
        }
        for (name in listOf("Rollout", "Ice Ball"))
            assertEquals("Variable (Defense Curl ×2)", HeartAndSoul205DataPack.getMoveByName(name)!!.powerDisplay)
        assertEquals("30", HeartAndSoul205DataPack.getMoveByName("Double Kick")!!.powerDisplay)
        assertEquals("30",com.dualdex.pokemon.MoveDatabase.getRaw(205)?.powerDisplay)
        assertEquals("40",HeartAndSoul205DataPack.getMoveByName("Thunder Shock")!!.powerDisplay)
        for(id in listOf(205,301)) {
            assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_ROLLOUT,HnsMoveMechanicsRegistry.classify(id).category)
            assertFalse(id in Hns205MoveEffects.ordinaryMoveIds)
        }
    }
    private fun refused(outcome: CalcRequestOutcome) {
        assertTrue(outcome.toString(),outcome is CalcRequestOutcome.Refused)
        var calls=0
        val result=CalcAuthorizedExecution.calculate((outcome as CalcRequestOutcome.Refused).verdict) { calls++; error("Refused request executed") }
        assertFalse(result.success);assertEquals(0,calls)
    }
    @Test fun `session reload and live snapshot invalidation discard chain authority`() {
        for (clearSession in listOf(false, true)) {
            val vm = com.dualdex.companion.CompanionViewModel()
            val active = observation(true).let { it.copy(state=it.state.copy(
                rolloutTimer=4, defenseCurl=true, multipleTurns=true, lockedMove=205)) }
            val field = vm.javaClass.getDeclaredField("_hnsBattlers").apply { isAccessible=true }
            @Suppress("UNCHECKED_CAST")
            val snapshots = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<List<BattlerRuntimeObservation>>
            snapshots.value = listOf(active, observation(false))
            assertEquals(active, vm.hnsBattlers.value.first())
            val oldRequest = (build(active) as CalcRequestOutcome.Ready).request
            if (clearSession) vm.clearRomSession() else vm.clearLiveMemoryObservations()
            assertTrue(vm.hnsBattlers.value.isEmpty())
            assertNull(vm.selectedHnsPlayerObservation(0))
            refused(CalcRequestBoundary.build(Baseline.profile, Baseline.trust, oldRequest,
                Baseline.challengeSettings, vm.hnsBattlers.value.firstOrNull(), vm.hnsBattlers.value.getOrNull(1), activeBattle=true))
        }
    }
    @Test fun `unread malformed stale locks and intermediate phases refuse without execution`() {
        val o=observation(true)
        val states=listOf(o.state.copy(rolloutTimer=null),o.state.copy(defenseCurl=null),o.state.copy(multipleTurns=null),
            o.state.copy(lockedMove=null),o.state.copy(status1=16),o.state.copy(rechargeTimer=null),o.state.copy(rechargeTimer=1),o.state.copy(rolloutTimer=-1),o.state.copy(rolloutTimer=5),o.state.copy(rolloutTimer=255),
            o.state.copy(rolloutTimer=1,multipleTurns=false),o.state.copy(rolloutTimer=0,multipleTurns=true),
            o.state.copy(rolloutTimer=1,multipleTurns=true,lockedMove=301),o.state.copy(rolloutTimer=1,multipleTurns=true,lockedMove=33),
            o.state.copy(switchInPhaseObserved=false),o.state.copy(switchInEventsSettled=false),o.state.copy(partySlot=4),
            o.state.copy(status=HnsBattlerRuntimeStatus.UNAVAILABLE))
        for(state in states) refused(build(o.copy(state=state)))
        val inactive=build(o.copy(state=o.state.copy(lockedMove=301)))
        assertTrue(inactive.toString(),inactive is CalcRequestOutcome.Ready)
        for (move in listOf("Rollout", "Ice Ball"))
            refused(build(observation(true,ability="Mold Breaker"),observation(false,ability=if(move=="Ice Ball") "Bulletproof" else "Fluffy"),request(move)))
        for (move in listOf("Rollout", "Ice Ball"))
            refused(build(o.copy(state=o.state.copy(volatileElectrified=true)),r=request(move)))
        refused(build(r=request().copy(field=CalcFieldInput(gameType="Doubles"))))
        refused(CalcRequestBoundary.build(Baseline.profile,Baseline.trust,request(),Baseline.challengeSettings,null,null,activeBattle=false))
        refused(build(d=observation(false).let {it.copy(state=it.state.copy(volatileSubstitute=true))}))
        refused(build(d=observation(false).let {it.copy(state=it.state.copy(volatileSemiInvulnerable=1))}))
        val admitted=ready().request
        val forged=admitted.copy(moveOverride=CalcMoveOverride(960,"Ice","Special"),hnsLiveBattleState=admitted.hnsLiveBattleState!!.copy(attackerRolloutState=HnsRolloutAuthority.Operands(4,true,true,205,0)))
        val rebound=build(r=forged) as CalcRequestOutcome.Ready
        assertEquals(30,HnsRolloutAuthority.forRequest(rebound.request).basePower)
        assertEquals(CalcMoveOverride(30,"Rock","Physical"),rebound.request.moveOverride)
        refused(build(o.copy(state=o.state.copy(rolloutTimer=null)),r=forged))
    }
    @Test fun `direct calculator rejects forged descriptors and state`() {
        val json=buildCalcRequestJson(ready().request)
        val mutations: List<(JSONObject)->Unit> = listOf(
            {it.getJSONObject("attacker").remove("hnsRolloutTimer");Unit},
            {it.getJSONObject("attacker").put("hnsRolloutTimer",5)},
            {it.getJSONObject("attacker").put("hnsRechargeTimer",1)},
            {it.getJSONObject("attacker").put("hnsDefenseCurl",0)},
            {it.getJSONObject("attacker").put("hnsMultipleTurns",true)},
            {it.getJSONObject("attacker").put("hnsRolloutStablePhase",false)},
            {it.getJSONObject("attacker").put("hnsRolloutTimer",1).put("hnsMultipleTurns",true).put("hnsLockedMove",301)},
            {it.getJSONObject("move").put("hnsMoveEffect","EFFECT_HIT")},
            {it.getJSONObject("move").put("hnsMoveId",301)},
            {it.getJSONObject("move").put("name","Ice Ball")},
            {it.getJSONObject("move").put("hnsMoveFamily","ORDINARY_PROVEN_EQUIVALENT")},
            {it.getJSONObject("move").put("hnsSourceType","TYPE_ICE")},
            {it.getJSONObject("move").put("hnsSourceCategory","DAMAGE_CATEGORY_SPECIAL")},
            {it.getJSONObject("move").put("hnsSourceTarget","TARGET_BOTH")},
            {it.getJSONObject("move").put("hnsSourceStrikeCount",2)},
            {it.getJSONObject("move").put("hnsMakesContact",false)},
            {it.getJSONObject("move").put("hnsSheerForceAffected",true)},
            {it.getJSONObject("move").getJSONObject("overrides").put("basePower",960)})
        for(mutate in mutations) { val changed=JSONObject(json);mutate(changed);assertFalse(calculate(changed.toString()).getBoolean("success")) }
    }
    @Test fun `Ice Ball is ballistic and Rollout is not`() {
        val d=observation(false,ability="Bulletproof")
        for(move in listOf("Rollout","Ice Ball")) {
            val result=build(d=d,r=request(move)) as CalcRequestOutcome.Ready
            val damage=rolls(buildCalcRequestJson(result.request))
            if(move=="Ice Ball") {
                assertEquals(List(16){0},damage)
                val unread=JSONObject(buildCalcRequestJson(result.request))
                unread.getJSONObject("attacker").remove("hnsRolloutTimer")
                assertFalse(calculate(unread.toString()).getBoolean("success"))
            } else assertTrue(damage.last()>0)
        }
    }    @Test fun `every admitted pinned engine vector traverses the production boundary`() {
        val entries=JSONObject(File(root,"tools/hns-damage-oracle/corpus.json").readText()).getJSONArray("entries")
        var count=0
        for(i in 0 until entries.length()) {
            val entry=entries.getJSONObject(i); val scenario=entry.getJSONObject("scenario")
            if(!scenario.getString("id").startsWith("rollout-") || scenario.getString("surface")!="modelled") continue
            val observed=entry.getJSONObject("observed"); val chain=observed.getJSONObject("rollout")
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
                        sideStatuses=if(!a && scenario.getJSONObject("field").getBoolean("reflect")) 1 else 0,
                        fieldStatuses=observed.getInt("fieldStatuses"),
                        battleWeather=when(scenario.getJSONObject("field").getString("weather")){"rain"->1;"sun"->8;else->0},
                        volatileChargeTimer=runtime.getInt("chargeTimer"),
                        rolloutTimer=if(a) chain.getInt("timer") else 0,
                        defenseCurl=a && chain.getInt("defenseCurl")==1,
                        multipleTurns=a && chain.getInt("multipleTurns")==1,
                        lockedMove=if(a) chain.getInt("lockedMove") else 0,
                        rechargeTimer=if(a) chain.getInt("rechargeTimer") else 0,
                        volatileElectrified=a && chain.getInt("electrified")==1))
                }
            }
            val r=request(scenario.getJSONObject("move").getString("label")).copy(
                attacker=request().attacker.copy(species=scenario.getJSONObject("attacker").getString("speciesLabel")),
                move=CalcMoveInput(scenario.getJSONObject("move").getString("label"),isCrit=scenario.getBoolean("crit")))
            val outcome=build(battler(true),battler(false),r)
            assertTrue("${scenario.getString("id")}: $outcome",outcome is CalcRequestOutcome.Ready)
            val bound=(outcome as CalcRequestOutcome.Ready).request
            assertEquals(chain.getInt("basePower"),HnsRolloutAuthority.forRequest(bound).basePower)
            assertEquals(scenario.getString("id"),(0..15).map {entry.getJSONArray("rolls").getInt(it)},rolls(buildCalcRequestJson(bound)))
            count++
        }
        assertEquals(68,count)
    }

}
