package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import com.dualdex.battle.DamageBlockerPresentation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real boundary -> production JSON -> shipped bundle; assertions use measured pinned rolls. */
class HnsEarthquakeProductionBoundaryTest {
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
                rawSpeed = if (attacker) 40 else 100, hp = if(attacker) 200 else 60000, maxHp = if(attacker) 200 else 60000)) }
    }
    private fun request(move: String = "Earthquake", crit: Boolean = false) = DamageCalculationRequest(
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

    private fun field(o: BattlerRuntimeObservation, grassy: Boolean = false, semi: Int = 0) =
        o.copy(state=o.state.copy(fieldStatuses=if(grassy) 0x40 else 0, volatileSemiInvulnerable=semi))
    @Test fun `two moves serialize separate category and match measured neutral grassy and Sheer Force rolls`() {
        assertEquals(setOf(89,523),Hns205MoveEffects.fixedSingleHitEarthquakeMoveIds)
        for(move in listOf("Earthquake","Bulldoze")) {
            val out=build(request(move))
            assertEquals(measured("earthquake-${move.lowercase()}-player"),damage(out))
            val json=JSONObject(buildCalcRequestJson(ready(out).request)).getJSONObject("move")
            assertFalse(json.getBoolean("hnsIsOrdinary"))
            assertFalse(json.getBoolean("hnsIsDrain"))
            assertTrue(json.getBoolean("hnsIsEarthquake"))
            assertTrue(json.getBoolean("hnsFixedSingleHit"))
            assertEquals(move=="Earthquake",json.getBoolean("hnsDamagesUnderground"))
            assertEquals("EFFECT_EARTHQUAKE",json.getString("hnsMoveEffect"))
            assertEquals(measured("earthquake-${move.lowercase()}-grassy"),damage(build(request(move),field(observation(true),true),field(observation(false),true))))
            assertEquals(measured("earthquake-${move.lowercase()}-sheer-force"),damage(build(request(move),observation(true,"Sheer Force"))))
        }
    }
    @Test fun `underground is separate from raw Grassy reduction and uses final accumulator`() {
        data class Case(val id:String,val grassy:Boolean=false,val reflect:Boolean=false,val item:String?=null,val crit:Boolean=false)
        for((id,grassy,reflect,item,crit) in listOf(
            Case("underground"),
            Case("underground-grassy",grassy=true),
            Case("underground-reflect",reflect=true),
            Case("underground-life-orb",item="Life Orb"),
            Case("underground-crit",crit=true))) {
            val a=field(observation(true,item=item),grassy)
            val d=field(observation(false),grassy,1).let { it.copy(state=it.state.copy(sideStatuses=if(reflect) 1 else 0)) }
            assertEquals(measured("earthquake-$id"),damage(build(request(crit=crit),a,d)))
        }
        assertEquals(damage(build(d=field(observation(false),semi=1))),
            damage(build(a=field(observation(true),true),d=field(observation(false),true,1))))
    }
    @Test fun `unsupported semi states unknown forged stale and Doubles fail closed`() {
        for(move in listOf("Earthquake","Bulldoze")) {
            for(semi in (if(move=="Earthquake") 2..6 else 1..6)) {
                val out=build(request(move),d=field(observation(false),semi=semi)) as CalcRequestOutcome.Refused
                assertTrue(CalcLimitation.HNS_SEMI_INVULNERABLE_EXECUTION_NOT_MODELLED in out.verdict.blockingLimitations)
            }
            val d=observation(false)
            for(state in listOf(d.state.copy(volatilesObserved=false),d.state.copy(volatileSemiInvulnerable=7))) {
                val out=build(request(move),d=d.copy(state=state)) as CalcRequestOutcome.Refused
                assertTrue(CalcLimitation.HNS_SEMI_INVULNERABLE_STATE_UNKNOWN in out.verdict.blockingLimitations)
            }
            val forged=ready(build(request(move))).request.let { it.copy(hnsLiveBattleState=it.hnsLiveBattleState!!.copy(defenderSemiInvulnerableState=0)) }
            assertTrue(build(forged,d=field(d,semi=3)) is CalcRequestOutcome.Refused)
            assertTrue(build(request(move),d=d.copy(state=d.state.copy(partySlot=4))) is CalcRequestOutcome.Refused)
            val a=observation(true); val packet=Baseline.doublesPacket(a.state,d.state)
            assertTrue(build(request(move).copy(field=CalcFieldInput(gameType="Doubles")),
                a.copy(state=a.state.copy(battlersCount=4,doubles=packet)),
                d.copy(state=d.state.copy(battlersCount=4,doubles=packet))) is CalcRequestOutcome.Refused)
        }
        assertTrue(CalcRequestBoundary.build(Baseline.profile,null,request(),Baseline.challengeSettings,
            observation(true),observation(false),activeBattle=true) is CalcRequestOutcome.Refused)
        for(move in listOf("Magnitude","Fissure")) {
            assertTrue(move,build(request(move)) is CalcRequestOutcome.Refused)
        }
        assertEquals(HnsMoveMechanicsCategory.ORDINARY_PROVEN_EQUIVALENT,HnsMoveMechanicsRegistry.classify(125).category)
    }
    @Test fun `raw Grassy rule remains exact for ungrounded defender after Normalize`() {
        val d=field(observation(false),true).let { it.copy(state=it.state.copy(speciesId=18,
            types=listOf(HnsBattlerTypeObservation(true,1),HnsBattlerTypeObservation(true,3),HnsBattlerTypeObservation(true,10)))) }
        for(move in listOf("Earthquake","Bulldoze")) {
            val r=request(move).copy(defender=request(move).defender.copy(species="Pidgeot"))
            val out=build(r,field(observation(true,"Normalize"),true),d)
            assertEquals(HnsTerrainApplicability.NOT_AFFECTED,ready(out).request.hnsLiveBattleState!!.defenderTerrainApplicability)
            assertEquals(measured("earthquake-${move.lowercase()}-flying-grassy"),damage(out))
        }
    }
    @Test fun `underground rounding composition differs from doubling base power`() {
        val a=observation(true,item="Life Orb").let { it.copy(state=it.state.copy(rawAttack=153)) }
        val d=field(observation(false,"Filter"),semi=1).let { it.copy(state=it.state.copy(
            speciesId=59,sideStatuses=1,types=listOf(HnsBattlerTypeObservation(true,11)))) }
        val r=request().copy(defender=request().defender.copy(species="Arcanine"))
        val out=build(r,a,d)
        val expected=measured("earthquake-underground-rounding")
        assertEquals(expected,damage(out))
        val wrong=JSONObject(buildCalcRequestJson(ready(out).request))
        wrong.getJSONObject("move").put("hnsIsEarthquake",false).getJSONObject("overrides").put("basePower",200)
        assertNotEquals("Wrong BP-stage doubling must change a measured roll",expected,runJson(wrong.toString()))
    }
    @Test fun `underground never bypasses Ground immunity`() {
        for(ability in listOf("Levitate","Earth Eater")) {
            val out=build(d=observation(false,ability))
            assertEquals(measured("earthquake-${ability.lowercase().replace(' ','-')}"),damage(out))
            assertEquals(List(16) {0},damage(build(d=field(observation(false,ability),semi=1))))
        }
        assertEquals(measured("earthquake-air-balloon"),damage(build(d=observation(false,item="Air Balloon"))))
        val a=observation(true); val d=observation(false)
        val unknownAbility=a.copy(state=a.state.copy(abilityId=null))
        assertTrue(build(request("Bulldoze"),unknownAbility,d) is CalcRequestOutcome.Refused)
        assertTrue(build(request("Bulldoze"),a.copy(state=a.state.copy(volatileGastroAcid=true)),d) is CalcRequestOutcome.Refused)
    }

    @Test fun `Grassy BP accumulator keeps source power for Technician and composes with Sheer Force`() {
        assertEquals(measured("earthquake-grassy-technician"),damage(build(a=field(observation(true,"Technician"),true),d=field(observation(false),true))))
        assertEquals(measured("earthquake-grassy-sheer-force"),damage(build(request("Bulldoze"),field(observation(true,"Sheer Force"),true),field(observation(false),true))))
    }

}
