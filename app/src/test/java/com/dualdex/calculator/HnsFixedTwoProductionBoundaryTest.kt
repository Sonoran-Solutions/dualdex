package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HnsFixedTwoProductionBoundaryTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it,"ci.sh").isFile }
    private val moves = listOf("Bonemerang","Double Hit","Double Kick","Dual Chop","Dual Wingbeat","Twin Beam")
    private fun observation(a: Boolean, ability: String="Insomnia", item: String?=null, hp: Int=60000) =
        Baseline.observation(if(a) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if(a) 0 else 1,if(a) 68 else 143,if(a) "Machamp" else "Snorlax",
            if(a) listOf("Fighting") else listOf("Normal"),checkNotNull(HnsAbilityRegistry.classify(ability).abilityId),
            item?.let(HnsItemRegistry::resolveIdByName) ?: 0,ability,
            Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0),2).let { o ->
            o.copy(state=o.state.copy(rawAttack=151,rawSpAttack=151,rawDefense=109,rawSpDefense=109,
                rawSpeed=if(a) 200 else 100, hp=if(a) 200 else hp,maxHp=if(a) 200 else hp,
                contactReactionStateObserved=true,chosenMove=33,protectedMethod=0)) }
    private fun request(move: String)=DamageCalculationRequest(
        attacker=CalcPokemonInput(species="Machamp",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=0),
        defender=CalcPokemonInput(species="Snorlax",level=50,origin=CalcInputOrigin.LIVE_READ,partySlot=1),
        move=CalcMoveInput(move),field=CalcFieldInput(gameType="Singles"))
    private fun build(move: String="Double Hit",a: BattlerRuntimeObservation=observation(true),d: BattlerRuntimeObservation=observation(false),r: DamageCalculationRequest=request(move),style: Int=0) =
        CalcRequestBoundary.build(Baseline.profile,Baseline.trust,r,Baseline.challengeSettings.copy(optionStyle=Baseline.challengeSettings.optionStyle.copy(raw=style)),a,d,activeBattle=true)
    private fun ready(move: String="Double Hit",a: BattlerRuntimeObservation=observation(true),d: BattlerRuntimeObservation=observation(false),r: DamageCalculationRequest=request(move),style: Int=0) =
        (build(move,a,d,r,style) as? CalcRequestOutcome.Ready) ?: error("Unexpected refusal: ${build(move,a,d,r,style)}")
    private fun calculate(json: String): JSONObject {
        val p=ProcessBuilder("node",File(root,"tools/calc-bundler/run_production_request.js").path,File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(json) }
        val result=JSONObject(p.inputStream.bufferedReader().readText()); assertEquals(0,p.waitFor()); return result
    }
    private fun result(r: DamageCalculationRequest): JSONObject = calculate(buildCalcRequestJson(r)).also { assertTrue(it.toString(),it.getBoolean("success")) }
    @Test fun `observed MOVE_NONE cannot authorize contact but exact suppression can`() {
        val uncommitted=observation(false).let {it.copy(state=it.state.copy(chosenMove=0))}
        for(move in listOf("Double Hit","Double Kick","Dual Chop","Dual Wingbeat")) {
            val known=ready(move).request
            assertEquals(HnsRepeatedStrikeAuthority.Stability.STABLE,HnsRepeatedStrikeAuthority.forRequest(known).stability)
            val unknown=HnsRepeatedStrikeAuthority.forRequest(known.copy(hnsLiveBattleState=known.hnsLiveBattleState!!.copy(defenderChosenMove=0)))
            assertEquals(HnsRepeatedStrikeAuthority.Stability.UNKNOWN_TRANSITION,unknown.stability)
            assertEquals(CalcLimitation.HNS_REPEATED_STRIKE_STATE_UNKNOWN,unknown.limitation)
            assertTrue(build(move,d=uncommitted) is CalcRequestOutcome.Refused)
            val beak=known.copy(hnsLiveBattleState=known.hnsLiveBattleState!!.copy(defenderChosenMove=653))
            assertEquals(HnsRepeatedStrikeAuthority.Stability.UNSUPPORTED_TRANSITION,HnsRepeatedStrikeAuthority.forRequest(beak).stability)
            for(a in listOf(observation(true,"Long Reach"),observation(true,item="Protective Pads"))) {
                val suppressed=ready(move,a=a,d=uncommitted).request
                assertEquals(HnsRepeatedStrikeAuthority.Stability.STABLE,HnsRepeatedStrikeAuthority.forRequest(suppressed).stability)
                result(suppressed)
            }
        }
        ready("Bonemerang",d=uncommitted);ready("Twin Beam",d=uncommitted)
    }
    @Test fun `six exact descriptors produce separate first strike and HP capped totals`() {
        for(move in moves) {
            val bound=ready(move).request;val j=JSONObject(buildCalcRequestJson(bound)).getJSONObject("move")
            assertEquals("FIXED_TWO_HIT_PLAIN",j.getString("hnsMoveFamily"));assertFalse(j.getBoolean("hnsFixedSingleHit"));assertFalse(j.getBoolean("hnsMultiHit"))
            val id=HeartAndSoul205DataPack.getMoveByName(move)!!.id
            assertFalse(HnsMoveMechanicsRegistry.classify(id).category.isSupportedFixedSingleHit);assertFalse(id in Hns205MoveEffects.ordinaryMoveIds)
            val response=result(bound);val sequence=parseRepeatedStrikeResult(response)!!;val total=sequence.totals.single()
            assertTrue(sequence.presentation.contains("First strike:"));assertTrue(sequence.presentation.contains("Total HP loss:"));assertTrue(sequence.presentation.contains("Nominal hits: 2"))
            assertEquals(60000,response.getInt("defenderMaxHP"));assertEquals(16,sequence.firstStrikeRolls.size);assertEquals(listOf(2),sequence.nominalCounts)
            assertEquals(2*sequence.firstStrikeRolls.first(),total.minHpLoss);assertEquals(2*sequence.firstStrikeRolls.last(),total.maxHpLoss)
            assertEquals(0,response.getJSONArray("damage").length());assertEquals(0,response.getJSONArray("range").length());assertEquals("",response.getString("koChanceText"))
            val valid=DamageCalculationResponse(success=true,repeatedStrike=sequence,minDamage=total.minHpLoss,maxDamage=total.maxHpLoss)
            assertTrue(CalcAuthorizedExecution.validRepeatedStrikeResponse(bound,valid));assertFalse(CalcAuthorizedExecution.validRepeatedStrikeResponse(bound,valid.copy(repeatedStrike=null)))
            assertFalse(CalcAuthorizedExecution.validRepeatedStrikeResponse(bound,valid.copy(minDamage=total.minHpLoss+1)))
            assertFalse(CalcAuthorizedExecution.validRepeatedStrikeResponse(bound,valid.copy(repeatedStrike=sequence.copy(assumptions=sequence.assumptions.take(1)))))
            for(hp in listOf(1,sequence.firstStrikeRolls.first(),sequence.firstStrikeRolls.last()+1,total.maxHpLoss-1)) {
                val b=ready(move,d=observation(false,hp=hp)).request;val s=parseRepeatedStrikeResult(result(b))!!;val t=s.totals.single()
                assertEquals(minOf(hp,total.minHpLoss),t.minHpLoss);assertEquals(minOf(hp,total.maxHpLoss),t.maxHpLoss)
                assertEquals(if(hp<=sequence.firstStrikeRolls.last()) 1 else 2,t.minExecutedHits)
                assertEquals(if(hp<=sequence.firstStrikeRolls.first()) 1 else 2,t.maxExecutedHits)
            }
        }
    }
    @Test fun `malformed repeated strike integers cannot be coerced`() {
        val original=result(ready().request)
        for(value in listOf<Any>(1.5,"2",Int.MAX_VALUE.toLong()+1)) {
            val changed=JSONObject(original.toString())
            changed.getJSONObject("repeatedStrike").getJSONArray("firstStrikeRolls").put(0,value)
            assertNull(parseRepeatedStrikeResult(changed))
            val total=JSONObject(original.toString())
            total.getJSONObject("repeatedStrike").getJSONArray("totals").getJSONObject(0).put("minHpLoss",value)
            assertNull(parseRepeatedStrikeResult(total))
        }
    }

    @Test fun `Ground immunity and grounded Levitate retain exact Group C authority`() {
        val levitate=observation(false,"Levitate")
        val immune=parseRepeatedStrikeResult(result(ready("Bonemerang",d=levitate).request))!!
        assertEquals(List(16) {0},immune.firstStrikeRolls);assertEquals(RepeatedStrikeTotal(2,0,0,0,0),immune.totals.single())
        val grounded=observation(false,"Levitate","Iron Ball")
        assertTrue(parseRepeatedStrikeResult(result(ready("Bonemerang",d=grounded).request))!!.firstStrikeRolls.last()>0)
        val gravityDefender=levitate.copy(state=levitate.state.copy(fieldStatuses=HnsFieldStatusData.STATUS_FIELD_GRAVITY))
        val gravityAttacker=observation(true).let {it.copy(state=it.state.copy(fieldStatuses=HnsFieldStatusData.STATUS_FIELD_GRAVITY))}
        assertTrue(build("Bonemerang",a=gravityAttacker,d=gravityDefender) is CalcRequestOutcome.Refused)
        assertTrue(build("Bonemerang",d=levitate.copy(state=levitate.state.copy(volatileSmackDown=true))) is CalcRequestOutcome.Refused)
        assertTrue(build("Bonemerang",a=observation(true,"Mold Breaker"),d=levitate) is CalcRequestOutcome.Refused)
    }

    @Test fun `screen stage and final item retain source ordered arithmetic`() {
        val a=observation(true,"Technician","Life Orb").let {it.copy(state=it.state.copy(statStages=listOf(0,2,0,0,0,0,0,0)))}
        val d=observation(false).let {it.copy(state=it.state.copy(sideStatuses=1))}
        val sequence=parseRepeatedStrikeResult(result(ready(a=a,d=d).request))!!
        val cases=JSONObject(File(root,"tools/hns-damage-oracle/repeated-strike-evidence.json").readText()).getJSONArray("cases")
        val source=(0 until cases.length()).map {cases.getJSONObject(it)}.single {it.getJSONObject("scenario").getString("id")=="screen-stage-item"}
        val events=source.getJSONArray("events")
        val high=(0 until events.length()).map {events.getJSONObject(it)}.first {it.getString("kind")=="CALC"}.getJSONArray("values").getInt(1)
        assertEquals(high,sequence.firstStrikeRolls.last());assertEquals(2*high,sequence.totals.single().maxHpLoss)
        for(ability in listOf("Poison Touch","Toxic Chain"))
            assertTrue(build(a=observation(true,ability),d=observation(false,"Marvel Scale")) is CalcRequestOutcome.Refused)
        val doublesA=observation(true).let {it.copy(state=it.state.copy(battlersCount=4))}
        val doublesD=observation(false).let {it.copy(state=it.state.copy(battlersCount=4))}
        assertTrue(build(a=doublesA,d=doublesD) is CalcRequestOutcome.Refused)
        assertTrue(build(a=observation(true).let {it.copy(state=it.state.copy(statsObserved=false))}) is CalcRequestOutcome.Refused)
        assertTrue(build(a=observation(true).let {it.copy(state=it.state.copy(abilityId=999,abilityOutOfDomain=true))}) is CalcRequestOutcome.Refused)
        assertTrue(build(a=observation(true).let {it.copy(state=it.state.copy(activeGimmick=1))}) is CalcRequestOutcome.Refused)
        val sandA=observation(true).let {it.copy(state=it.state.copy(battleWeather=32))}
        val sandD=observation(false).let {it.copy(state=it.state.copy(battleWeather=32))}
        assertTrue(build(a=sandA,d=sandD) is CalcRequestOutcome.Refused)
    }

    @Test fun `unknown active reactions and caller claims cannot authorize a sequence`() {
        for(move in moves) {
            for(ability in listOf("Poison Touch","Toxic Chain","Mold Breaker")) assertTrue(build(move,a=observation(true,ability)) is CalcRequestOutcome.Refused)
            for(ability in listOf("Multiscale","Shadow Shield","Sturdy","Stamina","Weak Armor","Flame Body","Effect Spore","Rough Skin","Mummy","Lingering Aroma","Wandering Spirit","Sand Spit","Seed Sower","Wind Power","Electromorphosis")) assertTrue(build(move,d=observation(false,ability)) is CalcRequestOutcome.Refused)
            for(item in listOf("Focus Sash","Chilan Berry","Rocky Helmet","Kee Berry","Maranga Berry")) assertTrue(build(move,d=observation(false,item=item)) is CalcRequestOutcome.Refused)
            for(d in listOf(observation(false).let { it.copy(state=it.state.copy(hpObserved=false)) },
                observation(false).let { it.copy(state=it.state.copy(volatileSubstitute=true)) },
                observation(false).let { it.copy(state=it.state.copy(volatileEndured=true)) },
                observation(false).let { it.copy(state=it.state.copy(switchInEventsSettled=false)) })) assertTrue(build(move,d=d) is CalcRequestOutcome.Refused)
        }
        val beak=observation(false).let {it.copy(state=it.state.copy(chosenMove=653))}
        assertTrue(build(d=beak) is CalcRequestOutcome.Refused)
        assertTrue(build(d=observation(false).let {it.copy(state=it.state.copy(contactReactionStateObserved=false))}) is CalcRequestOutcome.Refused)
        ready(a=observation(true,"Long Reach"),d=beak);ready(a=observation(true,item="Protective Pads"),d=beak)
        for(move in listOf("Twineedle","Triple Kick","Triple Axel","Population Bomb","Beat Up","Scale Shot")) assertTrue(build(move) is CalcRequestOutcome.Refused)
        val actual=ready().request
        val forged=actual.copy(hnsLiveBattleState=actual.hnsLiveBattleState!!.copy(defenderHp=1,defenderChosenMove=0))
        assertEquals(60000,ready(r=forged).request.hnsLiveBattleState!!.defenderHp)
        val json=buildCalcRequestJson(actual)
        for(mutate in listOf<(JSONObject)->Unit>(
            {it.getJSONObject("move").put("isCrit","true")}, {it.getJSONObject("move").put("hnsMoveId",33)}, {it.getJSONObject("move").put("name","Tackle")},
            {it.getJSONObject("move").put("hnsSourceStrikeCount",3)}, {it.getJSONObject("move").put("hnsMultiHit",true)},
            {it.getJSONObject("move").put("hnsMoveFamily","ORDINARY_PROVEN_EQUIVALENT")},
            {it.getJSONObject("move").put("hnsMakesContact",false)}, {it.getJSONObject("move").put("hnsSheerForceAffected",true)},
            {it.getJSONObject("move").getJSONObject("overrides").put("basePower",999)},
            {it.getJSONObject("move").getJSONObject("overrides").put("type","Fire")},
            {it.getJSONObject("move").getJSONObject("overrides").put("category","Special")},
            {it.getJSONObject("move").getJSONObject("overrides").put("ateBoost",true)},
            {it.getJSONObject("attacker").put("hnsSubstitute",true)},
            {it.getJSONObject("defender").put("status1",24)},
            {it.getJSONObject("field").put("hnsFieldStatuses",4294967296L)},
            {it.put("gen",8)}, {it.remove("hnsRepeatedStrikeOptionStyle")},
            {it.getJSONObject("defender").remove("hpAtHit")}, {it.getJSONObject("defender").put("hpAtHit",0)}, {it.getJSONObject("defender").put("hpAtHit",1)},
            {it.getJSONObject("defender").put("hnsChosenMove",0)}, {it.getJSONObject("defender").put("hnsChosenMove",653)}, {it.getJSONObject("defender").remove("hnsProtectedMethod")},
            {it.put("sequenceStable",true)}, {it.put("repeatedStrike",JSONObject())},
            {it.put("hnsObservedBattlersCount",4)}, {it.put("hnsRepeatedStrikePhaseSettled",false)},
            {it.getJSONObject("defender").put("hnsEffectiveHoldEffect","HOLD_EFFECT_ROCKY_HELMET")})) {
            val changed=JSONObject(json);mutate(changed);assertFalse(changed.toString(),calculate(changed.toString()).getBoolean("success"))
        }
    }
    @Test fun `original engine rolls and endpoints traverse production authority`() {
        val cases=JSONObject(File(root,"tools/hns-damage-oracle/repeated-strike-evidence.json").readText()).getJSONArray("cases")
        val selected=(0 until cases.length()).map {cases.getJSONObject(it)}.filter { c ->
            val id=c.getJSONObject("scenario").getString("id")
            moves.any {id.startsWith(it.lowercase().replace(' ','_')+"-")} || id.startsWith("early-") || id.startsWith("type-based-")
        }.groupBy { c ->
            val id=c.getJSONObject("scenario").getString("id")
            if(id.startsWith("early-")) id.split('-').take(2).joinToString("-") else id.substringBeforeLast('-')
        }
        val vectors=org.json.JSONArray();var checked=0
        for((id,group) in selected) {
            val scenario=group.first().getJSONObject("scenario")
            fun label(key: String)=scenario.getString(key).lowercase().split('_').joinToString(" ") {it.replaceFirstChar(Char::uppercase)}
            val move=label("move");val r=request(move).copy(move=CalcMoveInput(move,isCrit=scenario.getJSONArray("crits").getInt(0)==1))
            val style=scenario.getInt("style")
            val attacker=observation(true,label("attackerAbility"),label("attackerItem").takeUnless {it=="None"}).let {if(style==1) it.copy(state=it.state.copy(rawSpAttack=211)) else it}
            val defender=observation(false,label("defenderAbility"),hp=scenario.getInt("hp")).let {if(style==1) it.copy(state=it.state.copy(rawSpDefense=131)) else it}
            val b=ready(move,attacker,defender,r,style).request
            val first=group.filter {it.getJSONObject("scenario").getJSONArray("rolls").getInt(0)==it.getJSONObject("scenario").getJSONArray("rolls").getInt(1)}
                .sortedByDescending {it.getJSONObject("scenario").getJSONArray("rolls").getInt(0)}
            fun events(c: JSONObject,kind: String): List<org.json.JSONArray> {
                val es=c.getJSONArray("events")
                return (0 until es.length()).map {es.getJSONObject(it)}.filter {it.getString("kind")==kind}.map {it.getJSONArray("values")}
            }
            val expected=first.map {events(it,"CALC").first().getInt(1)}
            val losses=group.map {scenario.getInt("hp")-events(it,"FINAL").single().getInt(5)}
            val hits=group.map {events(it,"FINAL").single().getInt(0)}
            val response=result(b);val sequence=parseRepeatedStrikeResult(response)!!;val t=sequence.totals.single()
            assertEquals(id,expected,sequence.firstStrikeRolls)
            assertEquals(id,losses.minOrNull(),t.minHpLoss);assertEquals(id,losses.maxOrNull(),t.maxHpLoss)
            assertEquals(id,hits.minOrNull(),t.minExecutedHits);assertEquals(id,hits.maxOrNull(),t.maxExecutedHits)
            vectors.put(JSONObject().put("id",id).put("requestJson",buildCalcRequestJson(b))
                .put("firstStrikeRolls",org.json.JSONArray(expected)).put("minHpLoss",t.minHpLoss).put("maxHpLoss",t.maxHpLoss)
                .put("minExecutedHits",t.minExecutedHits).put("maxExecutedHits",t.maxExecutedHits))
            checked+=group.size
        }
        assertEquals(4064,checked);assertEquals(74,vectors.length())
        val text=vectors.toString()+"\n";val file=File(root,"tools/hns-damage-oracle/repeated-strike-production-vectors.json")
        if(System.getProperty("dualdex.census.generate")=="true") file.writeText(text)
        else assertEquals("production QuickJS requests must be generated through the trusted Kotlin boundary",file.readText(),text)
    }
}
