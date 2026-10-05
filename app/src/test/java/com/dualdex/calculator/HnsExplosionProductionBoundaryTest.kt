package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import com.dualdex.battle.DamageBlockerPresentation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real boundary -> production JSON -> shipped bundle; assertions use measured pinned rolls. */
class HnsExplosionProductionBoundaryTest {
    private val root get() = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "ci.sh").isFile }
    private fun observation(attacker: Boolean, ability: String = "Insomnia", item: String? = null): BattlerRuntimeObservation {
        val abilityId = checkNotNull(HnsAbilityRegistry.classify(ability).abilityId)
        val itemId = item?.let(HnsItemRegistry::resolveIdByName) ?: 0
        return Baseline.observation(if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, if (attacker) 68 else 143, if (attacker) "Machamp" else "Snorlax",
            if (attacker) listOf("Fighting") else listOf("Normal"), abilityId, itemId, ability,
            Hns205ItemCatalogue.get(itemId), 2).let { o -> o.copy(state = o.state.copy(
                rawAttack = 151, rawSpAttack = 151, rawDefense = 109, rawSpDefense = 109,
                healBlockObserved = true, volatileHealBlock = false,
                rawSpeed = if (attacker) 100 else 40, hp = if(attacker) 200 else 60000, maxHp = if(attacker) 200 else 60000)) }
    }
    private fun request(move: String = "Explosion", crit: Boolean = false) = DamageCalculationRequest(
        attacker = CalcPokemonInput(species="Machamp", level=50, origin=CalcInputOrigin.LIVE_READ, partySlot=0),
        defender = CalcPokemonInput(species="Snorlax", level=50, origin=CalcInputOrigin.LIVE_READ, partySlot=1),
        move=CalcMoveInput(move, isCrit=crit), field=CalcFieldInput(gameType="Singles"))
    private fun build(r: DamageCalculationRequest = request(), a: BattlerRuntimeObservation = observation(true),
                      d: BattlerRuntimeObservation = observation(false)) = CalcRequestBoundary.build(
        Baseline.profile, Baseline.trust, r, Baseline.challengeSettings, a, d, activeBattle=true)
    private fun ready(o: CalcRequestOutcome) = (o as? CalcRequestOutcome.Ready) ?: error("Expected Ready: $o")
    private fun damage(o: CalcRequestOutcome): List<Int> {
        val r = ready(o)
        assertTrue(r.verdict.blockingLimitations.isEmpty())
        assertTrue(r.verdict.ignoredMechanics.isEmpty())
        assertTrue(DamageBlockerPresentation.from(r.verdict, observedDoubles=false).isEmpty())
        return runJson(checkNotNull(buildCalcRequestJson(r.request)))
    }
    private fun runJson(json: String): List<Int> {
        val process = ProcessBuilder("node", File(root,"tools/calc-bundler/run_production_request.js").path,
            File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write(json) }
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(text,0,process.waitFor())
        val rolls=JSONObject(text).getJSONArray("damage")
        assertEquals(16,rolls.length())
        return (0..15).map(rolls::getInt)
    }
    private fun measured(id: String): List<Int> {
        val entries=JSONObject(File(root,"tools/hns-damage-oracle/corpus.json").readText()).getJSONArray("entries")
        val e=(0 until entries.length()).map(entries::getJSONObject).first { it.getJSONObject("scenario").getString("id")==id }
        val rolls=e.getJSONArray("rolls")
        return (0..15).map(rolls::getInt)
    }

    private fun refused(o: CalcRequestOutcome, limitation: CalcLimitation? = null) {
        val r = o as? CalcRequestOutcome.Refused ?: error("Expected refusal: $o")
        assertNull(r.verdict.request)
        if (limitation != null) assertTrue(r.verdict.blockingLimitations.contains(limitation))
        if (limitation == CalcLimitation.HNS_DAMP_BLOCKS_EXPLOSION) {
            assertTrue(DamageBlockerPresentation.from(r.verdict,observedDoubles=false).any {
                it.headline == "Damp prevents this move from executing"
            })
        }
    }
    @Test fun `frozen family rebinds source power and internally derives HP zero`() {
        assertEquals(setOf(120,153),Hns205MoveEffects.fixedSingleHitExplosionMoveIds)
        for ((move,power) in listOf("Self-Destruct" to 200,"Explosion" to 250)) {
            val r=request(move).copy(moveOverride=CalcMoveOverride(basePower=1,type="Water",category="Special"))
            val out=build(r)
            assertEquals(measured("explosion-${move.lowercase()}-player"),damage(out))
            val ready=ready(out)
            assertEquals(200,ready.request.hnsLiveBattleState!!.attackerHp)
            val j=JSONObject(buildCalcRequestJson(ready.request)).getJSONObject("move")
            assertTrue(j.getBoolean("hnsIsExplosion"))
            assertTrue(j.getBoolean("hnsFixedSingleHit"))
            assertFalse(j.getBoolean("hnsIsOrdinary"))
            assertEquals(0,j.getInt("hnsExplosionUserHpAtDamage"))
            assertEquals(power,j.getJSONObject("overrides").getInt("basePower"))
        }
        for (id in listOf(120,153)) assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_EXPLOSION,HnsMoveMechanicsRegistry.classify(id).category)
    }
    @Test fun `Damp hard refusal precedes serialization and respects shared breakability and shield`() {
        for (move in listOf("Self-Destruct","Explosion")) {
            refused(build(request(move),d=observation(false,"Damp")),CalcLimitation.HNS_DAMP_BLOCKS_EXPLOSION)
            refused(build(request(move),a=observation(true,"Damp")),CalcLimitation.HNS_DAMP_BLOCKS_EXPLOSION)
            refused(build(request(move),a=observation(true,"Mold Breaker"),d=observation(false,"Damp","Ability Shield")),CalcLimitation.HNS_DAMP_BLOCKS_EXPLOSION)
        }
        for (a in listOf("Mold Breaker","Teravolt","Turboblaze")) {
            val out=build(a=observation(true,a),d=observation(false,"Damp"))
            assertEquals(measured("explosion-mold-breaker-damp"),damage(out))
        }
        for (state in listOf(observation(false,"Damp").state.copy(persistentVolatilesObserved=false),
            observation(false,"Damp").state.copy(abilityId=null),
            observation(false,"Damp").state.copy(partySlot=5))) {
            refused(build(d=observation(false,"Damp").copy(state=state)))
        }
        for (side in listOf(true,false)) {
            val o=observation(side,"Damp")
            val suppressed=o.copy(state=o.state.copy(volatileGastroAcid=true))
            refused(if(side) build(a=suppressed) else build(d=suppressed))
            val gas=o.copy(state=o.state.copy(volatileNeutralizingGas=true))
            refused(if(side) build(a=gas) else build(d=gas))
        }
        val forged=ready(build()).request
        refused(build(forged,d=observation(false,"Damp")),CalcLimitation.HNS_DAMP_BLOCKS_EXPLOSION)
        val live=forged.hnsLiveBattleState!!
        val fakeSuppression=forged.copy(hnsLiveBattleState=live.copy(
            defenderPersistentVolatiles=live.defenderPersistentVolatiles!!.copy(gastroAcid=true)))
        refused(build(fakeSuppression,d=observation(false,"Damp")),CalcLimitation.HNS_DAMP_BLOCKS_EXPLOSION)
        val a=observation(true)
        refused(build(a=a.copy(state=a.state.copy(switchInEventsSettled=false))),CalcLimitation.HNS_EXPLOSION_EXECUTION_UNKNOWN)
        refused(build(a=a.copy(state=a.state.copy(switchInPhaseObserved=false))),CalcLimitation.HNS_EXPLOSION_EXECUTION_UNKNOWN)
    }
    @Test fun `Damp has no current hit modifier for supported ordinary attacks`() {
        assertTrue(build(request("Tackle"),a=observation(true,"Damp"),d=observation(false,"Damp")) is CalcRequestOutcome.Ready)
        for (move in listOf("Surf","Fury Swipes")) {
            val out=build(request(move),a=observation(true,"Damp")) as CalcRequestOutcome.Refused
            assertTrue(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED in out.verdict.blockingLimitations)
            assertFalse(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED in out.verdict.blockingLimitations)
        }
    }
    @Test fun `source damage-time Defeatist ignores pre-action threshold and preserves live HP`() {
        val a=observation(true,"Defeatist")
        val out=build(a=a)
        assertEquals(measured("explosion-defeatist"),damage(out))
        assertEquals(200,ready(out).request.hnsLiveBattleState!!.attackerHp)
        assertEquals(damage(out),damage(build(a=a.copy(state=a.state.copy(hp=50)))))
    }
    @Test fun `modern physical defense stages screens and final modifiers match engine`() {
        assertEquals(measured("explosion-high-defense"),damage(build(d=observation(false).let { it.copy(state=it.state.copy(rawDefense=503)) })))
        assertEquals(measured("explosion-fur-coat"),damage(build(d=observation(false,"Fur Coat"))))
        assertEquals(measured("explosion-reflect"),damage(build(d=observation(false).let { it.copy(state=it.state.copy(sideStatuses=1)) })))
        assertEquals(measured("explosion-life-orb"),damage(build(a=observation(true,item="Life Orb"))))
        assertEquals(measured("explosion-crit"),damage(build(request(crit=true))))
        assertEquals(measured("explosion-parental-bond"),damage(build(a=observation(true,"Parental Bond"))))
        for (ability in listOf("Normalize","Pixilate","Aerilate","Refrigerate","Galvanize")) {
            assertEquals(measured("explosion-${ability.lowercase()}"),damage(build(a=observation(true,ability))))
        }
    }
    @Test fun `Ghost immunity and rewritten type preserve the selected hit contract`() {
        val d=observation(false).let { it.copy(state=it.state.copy(speciesId=354,types=listOf(HnsBattlerTypeObservation(true,8)))) }
        val r=request().copy(defender=request().defender.copy(species="Banette"))
        assertEquals(measured("explosion-ghost"),damage(build(r,d=d)))
        assertEquals(List(16) {0},damage(build(r,d=d)))
        assertEquals(measured("explosion-pixilate-ghost"),damage(build(r,a=observation(true,"Pixilate"),d=d)))
        refused(build(r,a=observation(true,"Pixilate"),d=d.copy(state=d.state.copy(abilityId=6))),CalcLimitation.HNS_DAMP_BLOCKS_EXPLOSION)
    }
    @Test fun `Defense stages Wonder Room and rounding composition retain the shared formula`() {
        val d=observation(false)
        assertEquals(measured("explosion-defense-stage"),damage(build(d=d.copy(state=d.state.copy(statStages=listOf(0,0,2,0,0,0,0,0))))))
        val a=observation(true)
        assertEquals(measured("explosion-attack-stages"),damage(build(a=a.copy(state=a.state.copy(statStages=listOf(0,2,0,0,0,0,0,0))))))
        val wonder=ready(build(a=a.copy(state=a.state.copy(fieldStatuses=4)),d=d.copy(state=d.state.copy(fieldStatuses=4))))
        assertTrue("Existing Wonder Room caveat must remain",wonder.verdict.ignoredMechanics.isNotEmpty())
        assertEquals(measured("explosion-rounding"),damage(build(a=observation(true,item="Life Orb").let {it.copy(state=it.state.copy(rawAttack=153))},d=d.copy(state=d.state.copy(sideStatuses=1)))))
        val out=ready(build(d=d.copy(state=d.state.copy(rawDefense=503))))
        val correct=damage(out)
        val wrong=JSONObject(buildCalcRequestJson(out.request))
        wrong.getJSONObject("defender").getJSONObject("rawStats").put("defense",251)
        assertNotEquals(correct,runJson(wrong.toString()))
    }
    @Test fun `Doubles and adjacent self damage families remain refused`() {
        for (move in listOf("Self-Destruct","Explosion")) {
            val a=observation(true);val d=observation(false);val packet=Baseline.doublesPacket(a.state,d.state)
            refused(build(request(move).copy(field=CalcFieldInput(gameType="Doubles")),
                a.copy(state=a.state.copy(battlersCount=4,doubles=packet)),d.copy(state=d.state.copy(battlersCount=4,doubles=packet))))
        }
        for (move in listOf("Misty Explosion","Final Gambit","Memento","Steel Beam","Mind Blown","Chloroblast")) refused(build(request(move)))
        for (id in listOf(1,36,71,89)) assertTrue(HnsMoveMechanicsRegistry.classify(id).category.isSupportedFixedSingleHit)
    }
}
