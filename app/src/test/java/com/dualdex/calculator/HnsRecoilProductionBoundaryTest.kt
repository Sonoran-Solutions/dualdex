package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import com.dualdex.battle.DamageBlockerPresentation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real boundary -> production JSON -> shipped bundle; assertions use measured pinned rolls. */
class HnsRecoilProductionBoundaryTest {
    private val root get() = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "ci.sh").isFile }
    private fun observation(attacker: Boolean, ability: String = "Insomnia", item: String? = null): BattlerRuntimeObservation {
        val abilityId = mapOf("Insomnia" to 15, "Reckless" to 120, "Rock Head" to 69, "Magic Guard" to 98,
            "Technician" to 101, "Tough Claws" to 181, "Long Reach" to 203, "Sheer Force" to 125, "Guts" to 62).getValue(ability)
        val itemId = item?.let(HnsItemRegistry::resolveIdByName) ?: 0
        return Baseline.observation(if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, if (attacker) 68 else 143, if (attacker) "Machamp" else "Snorlax",
            if (attacker) listOf("Fighting") else listOf("Normal"), abilityId, itemId, ability,
            Hns205ItemCatalogue.get(itemId), 2).let { o -> o.copy(state = o.state.copy(
                rawAttack = 151, rawSpAttack = 151, rawDefense = 109, rawSpDefense = 109,
                rawSpeed = if (attacker) 80 else 40, hp = 60000, maxHp = 60000)) }
    }
    private fun request(move: String = "Take Down", crit: Boolean = false) = DamageCalculationRequest(
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
    @Test fun `every admitted recoil move reaches shipped calculator with exact pinned rolls`() {
        val moves=listOf("Take Down","Double-Edge","Submission","Volt Tackle","Flare Blitz","Brave Bird",
            "Wood Hammer","Head Smash","Wild Charge","Head Charge","Light of Ruin","Wave Crash")
        for (move in moves) {
            val slug=move.lowercase().replace(' ','-')
            val actual = damage(build(request(move)))
            assertEquals(move,measured("recoil-$slug-player"),actual)
            val json=JSONObject(buildCalcRequestJson(ready(build(request(move))).request)).getJSONObject("move")
            assertFalse(json.getBoolean("hnsIsOrdinary"))
            assertEquals("EFFECT_RECOIL",json.getString("hnsMoveEffect"))
        }
    }
    @Test fun `recoil ability and item contexts use exact modifiers and attribution`() {
        for (ability in listOf("Reckless","Rock Head","Magic Guard","Technician","Tough Claws")) {
            if (ability=="Reckless") {
                val decision=ready(build(a=observation(true,ability))).verdict.hnsAbilityDecisions.single { it.abilityId==120 }
                assertEquals("reckless_recoil_bp",decision.rule)
                assertTrue(HnsMechanicAttribution.isModelledAbility(decision))
                assertFalse(HnsMechanicAttribution.isAbilityEffectCause(decision))
            }
            val slug=ability.lowercase().replace(' ','-')
            assertEquals(ability,measured("recoil-$slug"),damage(build(a=observation(true,ability))))
        }
        for (move in listOf("Flare Blitz","Volt Tackle","Brave Bird")) {
            assertEquals(measured("recoil-sheer-force-${move.lowercase().replace(' ','-')}"),
                damage(build(request(move),observation(true,"Sheer Force"))))
        }
        assertEquals(measured("recoil-reckless-crit"),damage(build(request(crit=true),observation(true,"Reckless"))))
    }
    @Test fun `unknown live operands and independent blockers cannot inherit recoil authority`() {
        val a=observation(true,"Reckless")
        for (state in listOf(a.state.copy(abilityId=null),a.state.copy(statsObserved=false),
                a.state.copy(rawAttack=0),a.state.copy(partySlot=99),a.state.copy(statusObserved=false))) {
            assertTrue("Missing/invalid operand: $state",build(a=a.copy(state=state)) is CalcRequestOutcome.Refused)
        }
        val longReach=build(a=observation(true,"Long Reach")) as CalcRequestOutcome.Refused
        assertTrue(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED in longReach.verdict.blockingLimitations)
        for (move in listOf("Jump Kick","Struggle","Chloroblast","Double Slap","Magnitude")) {
            val o=build(request(move)) as CalcRequestOutcome.Refused
            assertTrue(move, CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED in o.verdict.blockingLimitations)
        }
        val d=observation(false)
        val p=Baseline.doublesPacket(a.state,d.state)
        val doubles=build(request().copy(field=CalcFieldInput(gameType="Doubles")),
            a.copy(state=a.state.copy(battlersCount=4,doubles=p)),d.copy(state=d.state.copy(battlersCount=4,doubles=p)))
        assertTrue(doubles is CalcRequestOutcome.Refused)
    }
    @Test fun `recoil suppression and self-thaw exclusions remain named refusals`() {
        val a=observation(true,"Guts")
        for(status in listOf(0x20,0x1000)) {
            val out=build(request("Flare Blitz"),a.copy(state=a.state.copy(status1=status))) as CalcRequestOutcome.Refused
            assertTrue(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED in out.verdict.blockingLimitations)
        }
        val suppressed=build(a=observation(true,"Reckless").let { it.copy(state=it.state.copy(volatileGastroAcid=true)) }) as CalcRequestOutcome.Refused
        assertTrue(CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED in suppressed.verdict.blockingLimitations)
        assertEquals(measured("recoil-burn"),damage(build(a=a.copy(state=a.state.copy(status1=0x10)))))
    }
    @Test fun `forged overrides are rebound to observed recoil source and invalid identity refuses`() {
        val approved=ready(build())
        val forged=approved.request.copy(moveOverride=approved.request.moveOverride!!.copy(basePower=999))
        assertEquals(damage(build()),damage(build(forged)))
        assertTrue(build(request().copy(move=CalcMoveInput("unrecognised recoil move"))) is CalcRequestOutcome.Refused)
    }
    @Test fun `real Aaron Ivysaur Take Down census request now displays`() {
        fun obs(attack: Boolean): BattlerRuntimeObservation {
            val name=if(attack) "Ivysaur" else "Chikorita"
            val sp=HeartAndSoul205DataPack.getSpeciesByName(name)!!
            return Baseline.observation(if(attack) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
                if(attack) 0 else 1,sp.id,name, listOfNotNull(sp.type1.displayName,sp.type2?.displayName),
                65,0,"Overgrow",Hns205ItemCatalogue.get(0),2).let { o->o.copy(state=o.state.copy(
                    rawAttack=151,rawSpAttack=151,rawDefense=109,rawSpDefense=109,rawSpeed=80,hp=60000,maxHp=60000)) }
        }
        val r=request().copy(attacker=request().attacker.copy(species="Ivysaur"),defender=request().defender.copy(species="Chikorita"))
        damage(build(r,obs(true),obs(false)))
    }
}
