package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import com.dualdex.battle.DamageBlockerPresentation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real boundary -> production JSON -> shipped bundle; assertions use measured pinned rolls. */
class HnsDrainProductionBoundaryTest {
    private val root get() = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "ci.sh").isFile }
    private fun observation(attacker: Boolean, ability: String = "Insomnia", item: String? = null): BattlerRuntimeObservation {
        val abilityId = mapOf("Insomnia" to 15, "Reckless" to 120, "Rock Head" to 69, "Magic Guard" to 98,
            "Technician" to 101, "Tough Claws" to 181, "Long Reach" to 203, "Sheer Force" to 125, "Guts" to 62, "Triage" to 205, "Iron Fist" to 89, "Liquid Ooze" to 64, "Fluffy" to 218, "Dazzling" to 214, "Analytic" to 148).getValue(ability)
        val itemId = item?.let(HnsItemRegistry::resolveIdByName) ?: 0
        return Baseline.observation(if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, if (attacker) 68 else 143, if (attacker) "Machamp" else "Snorlax",
            if (attacker) listOf("Fighting") else listOf("Normal"), abilityId, itemId, ability,
            Hns205ItemCatalogue.get(itemId), 2).let { o -> o.copy(state = o.state.copy(
                rawAttack = 151, rawSpAttack = 151, rawDefense = 109, rawSpDefense = 109,
                healBlockObserved = true, volatileHealBlock = false,
                rawSpeed = if (attacker) 80 else 40, hp = if(attacker) 30000 else 60000, maxHp = 60000)) }
    }
    private fun request(move: String = "Drain Punch", crit: Boolean = false) = DamageCalculationRequest(
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
        val process = ProcessBuilder("node", File(root,"tools/calc-bundler/run_production_request.js").path,
            File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write(checkNotNull(buildCalcRequestJson(r.request))) }
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

    @Test fun `seven drain moves reach shipped calculator with exact pinned rolls`() {
        val moves=listOf("Absorb","Mega Drain","Leech Life","Giga Drain","Drain Punch","Horn Leech","Draining Kiss")
        assertEquals(setOf(71,72,141,202,409,532,577), Hns205MoveEffects.fixedSingleHitDrainMoveIds)
        for (move in moves) {
            assertEquals(move,measured("drain-${move.lowercase().replace(' ','-')}-player"),damage(build(request(move))))
            val json=JSONObject(buildCalcRequestJson(ready(build(request(move))).request)).getJSONObject("move")
            assertFalse(json.getBoolean("hnsIsOrdinary"))
            assertTrue(json.getBoolean("hnsFixedSingleHit"))
            assertTrue(json.getBoolean("hnsIsDrain"))
            assertEquals("EFFECT_ABSORB",json.getString("hnsMoveEffect"))
        }
    }
    @Test fun `Heal Block active unread stale and forged state refuse before damage`() {
        val a=observation(true)
        for ((state,reason) in listOf(a.state.copy(volatileHealBlock=true) to CalcLimitation.HNS_HEAL_BLOCK_ACTIVE,
            a.state.copy(healBlockObserved=false) to CalcLimitation.HNS_HEAL_BLOCK_STATE_UNKNOWN)) {
            val out=build(a=a.copy(state=state)) as CalcRequestOutcome.Refused
            assertTrue(reason in out.verdict.blockingLimitations)
        }
        val ready=ready(build())
        val forged=ready.request.copy(hnsLiveBattleState=ready.request.hnsLiveBattleState!!.copy(attackerHealBlock=false))
        assertTrue(build(forged,a.copy(state=a.state.copy(healBlockObserved=false))) is CalcRequestOutcome.Refused)
        assertTrue(build(a=a.copy(state=a.state.copy(partySlot=99))) is CalcRequestOutcome.Refused)
        assertTrue(CalcRequestBoundary.build(Baseline.profile,null,request(),Baseline.challengeSettings,a,observation(false),activeBattle=true) is CalcRequestOutcome.Refused)
    }
    @Test fun `Triage is exact priority and pre-hit priority blockers refuse`() {
        val r=ready(build(a=observation(true,"Triage")))
        assertEquals(3,HnsGroupCPolicy.effectivePriority(r.request,409))
        assertEquals(measured("drain-triage"),damage(build(a=observation(true,"Triage"))))
        val blocked=build(a=observation(true,"Triage"),d=observation(false,"Dazzling")) as CalcRequestOutcome.Refused
        assertTrue(CalcLimitation.HNS_PRIORITY_BLOCKED in blocked.verdict.blockingLimitations)
        val a=observation(true,"Triage").let { it.copy(state=it.state.copy(fieldStatuses=1 shl 9)) }
        val d=observation(false).let { it.copy(state=it.state.copy(fieldStatuses=1 shl 9)) }
        assertTrue(build(a=a,d=d) is CalcRequestOutcome.Refused)
        assertTrue(build(a=observation(true,"Analytic")) is CalcRequestOutcome.Refused)
        assertTrue(build(a=observation(true,"Triage"),d=observation(false).let { it.copy(state=it.state.copy(abilityId=null)) }) is CalcRequestOutcome.Refused)
    }
    @Test fun `post-hit mechanics preserve selected hit and drain contact modifiers are exact`() {
        for (ability in listOf("Iron Fist","Tough Claws","Technician")) {
            val move=if(ability=="Technician") "Absorb" else "Drain Punch"
            assertEquals(measured("drain-${ability.lowercase().replace(' ','-')}"),damage(build(request(move),observation(true,ability))))
        }
        assertEquals(measured("drain-big-root"),damage(build(a=observation(true,item="Big Root"))))
        assertEquals(measured("drain-liquid-ooze"),damage(build(d=observation(false,"Liquid Ooze"))))
        assertEquals(measured("drain-big-root-liquid-ooze"),damage(build(a=observation(true,item="Big Root"),d=observation(false,"Liquid Ooze"))))
        assertEquals(measured("drain-fluffy-contact"),damage(build(a=observation(true,"Tough Claws"),d=observation(false,"Fluffy"))))
        assertEquals(measured("drain-fluffy-noncontact"),damage(build(request("Giga Drain"),d=observation(false,"Fluffy"))))
        assertEquals(measured("drain-crit"),damage(build(request(crit=true))))
        assertEquals(measured("drain-life-orb"),damage(build(a=observation(true,item="Life Orb"))))
        assertEquals(measured("drain-big-root-kiss"),damage(build(request("Draining Kiss"),observation(true,item="Big Root"))))
        assertEquals(measured("drain-defender-triage"),damage(build(d=observation(false,"Triage"))))
    }
    @Test fun `adjacent healing and Doubles cannot inherit drain admission`() {
        for(move in listOf("Dream Eater","Strength Sap","Leech Seed","Aqua Ring","Parabolic Charge","Oblivion Wing","Bitter Blade")) {
            assertTrue(move,build(request(move),observation(true,"Triage", "Big Root"),observation(false,"Liquid Ooze")) is CalcRequestOutcome.Refused)
        }
        val a=observation(true); val d=observation(false); val packet=Baseline.doublesPacket(a.state,d.state)
        assertTrue(build(request().copy(field=CalcFieldInput(gameType="Doubles")),
            a.copy(state=a.state.copy(battlersCount=4,doubles=packet)),d.copy(state=d.state.copy(battlersCount=4,doubles=packet))) is CalcRequestOutcome.Refused)
        val r=ready(build());val forged=r.request.copy(moveOverride=r.request.moveOverride!!.copy(basePower=999))
        assertEquals(damage(build()),damage(build(forged)))
    }
    @Test fun `missing independent operands remain refused`() {
        val a=observation(true)
        for(state in listOf(a.state.copy(abilityId=null),a.state.copy(itemId=null),
            a.state.copy(statsObserved=false),a.state.copy(statusObserved=false),
            a.state.copy(types=emptyList()),a.state.copy(rawAttack=0),a.state.copy(groupDVolatilesObserved=false))) {
            assertTrue(build(a=a.copy(state=state)) is CalcRequestOutcome.Refused)
        }
        assertTrue(build(a=observation(true,"Long Reach")) is CalcRequestOutcome.Refused)
        val gas=a.copy(state=a.state.copy(volatileGastroAcid=true))
        assertTrue(build(a=gas) is CalcRequestOutcome.Refused)
    }

}
