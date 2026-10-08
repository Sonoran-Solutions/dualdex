package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import com.dualdex.battle.DamageBlockerPresentation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real boundary -> production JSON -> shipped bundle; assertions use measured pinned rolls. */
class HnsUnderwaterProductionBoundaryTest {
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
    private fun request(move: String = "Surf", crit: Boolean = false) = DamageCalculationRequest(
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

    private fun semi(o: BattlerRuntimeObservation, state: Int) = o.copy(state=o.state.copy(volatileSemiInvulnerable=state))
    @Test fun `frozen family neutral underwater and Sheer Force match all measured rolls`() {
        assertEquals(setOf(57,250),Hns205MoveEffects.fixedSingleHitUnderwaterMoveIds)
        for(move in listOf("Surf","Whirlpool")) {
            val id=if(move=="Surf") 57 else 250
            assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_UNDERWATER,HnsMoveMechanicsRegistry.classify(id).category)
            for(state in listOf(0,2)) {
                val out=build(request(move),d=semi(observation(false),state))
                assertEquals(measured("underwater-${move.lowercase()}-${if(state==2) "dive-" else ""}player"),damage(out))
                val json=JSONObject(buildCalcRequestJson(ready(out).request)).getJSONObject("move")
                assertEquals("FIXED_SINGLE_HIT_UNDERWATER",json.getString("hnsMoveFamily"))
                assertTrue(json.getBoolean("hnsIsUnderwater"))
                assertTrue(json.getBoolean("hnsDamagesUnderwater"))
                assertTrue(json.getBoolean("hnsFixedSingleHit"))
                assertFalse(json.getBoolean("hnsIsOrdinary"))
                assertFalse(json.getBoolean("hnsSheerForceAffected"))
                assertFalse(json.getBoolean("hnsMakesContact"))
            }
            val forged=request(move).copy(moveOverride=CalcMoveOverride(basePower=999,type="Fire",category="Physical"))
            assertEquals(damage(build(request(move))),damage(build(forged)))
            assertEquals(measured("underwater-${move.lowercase()}-sheer-force"),damage(build(request(move),observation(true,"Sheer Force"))))
        }
    }
    @Test fun `positive unadmitted unknown invalid stale and forged states refuse`() {
        for(move in listOf("Surf","Whirlpool")) {
            for(state in listOf(1,3,4,5,6)) {
                val out=build(request(move),d=semi(observation(false),state)) as CalcRequestOutcome.Refused
                assertTrue(CalcLimitation.HNS_SEMI_INVULNERABLE_EXECUTION_NOT_MODELLED in out.verdict.blockingLimitations)
            }
            val d=observation(false)
            for(state in listOf(d.state.copy(volatilesObserved=false),d.state.copy(volatileSemiInvulnerable=7),d.state.copy(volatileSemiInvulnerable=-1))) {
                val out=build(request(move),d=d.copy(state=state)) as CalcRequestOutcome.Refused
                assertTrue(CalcLimitation.HNS_SEMI_INVULNERABLE_STATE_UNKNOWN in out.verdict.blockingLimitations)
            }
            for(fake in listOf(0,2)) {
                val r=ready(build(request(move))).request
                assertTrue(build(r.copy(hnsLiveBattleState=r.hnsLiveBattleState!!.copy(defenderSemiInvulnerableState=fake)),d=semi(d,3)) is CalcRequestOutcome.Refused)
            }
            assertTrue(build(request(move),d=d.copy(state=d.state.copy(partySlot=4))) is CalcRequestOutcome.Refused)
            val a=observation(true); val packet=Baseline.doublesPacket(a.state,d.state)
            assertTrue(build(request(move).copy(field=CalcFieldInput(gameType="Doubles")),
                a.copy(state=a.state.copy(battlersCount=4,doubles=packet)),
                d.copy(state=d.state.copy(battlersCount=4,doubles=packet))) is CalcRequestOutcome.Refused)
        }
        for(move in listOf("Fury Swipes","Water Spout")) assertTrue(move,build(request(move)) is CalcRequestOutcome.Refused)
        assertTrue(CalcRequestBoundary.build(Baseline.profile,null,request(),Baseline.challengeSettings,observation(true),observation(false),activeBattle=true) is CalcRequestOutcome.Refused)
    }
    @Test fun `underwater screens weather stages crit items and rounding compose at final stage`() {
        for(move in listOf("Surf","Whirlpool")) {
            for(name in listOf("light-screen","rain","sun","life-orb","crit","water-bubble","mystic-water","rain-umbrella","stages","crit-stages")) {
                var a=observation(true,if(name=="water-bubble") "Water Bubble" else "Insomnia",
                    when(name) { "life-orb" -> "Life Orb"; "mystic-water" -> "Mystic Water"; "rain-umbrella" -> "Utility Umbrella"; else -> null })
                var d=semi(observation(false),2)
                val weather=when(name) { "rain","rain-umbrella" -> 1; "sun" -> 8; else -> 0 }
                a=a.copy(state=a.state.copy(battleWeather=weather))
                d=d.copy(state=d.state.copy(battleWeather=weather,sideStatuses=if(name=="light-screen") 2 else 0))
                if(name in listOf("stages","crit-stages")) {
                    val crit=name=="crit-stages"
                    a=a.copy(state=a.state.copy(statStages=a.state.statStages.toMutableList().also { it[4]=if(crit) -1 else 1 }))
                    d=d.copy(state=d.state.copy(statStages=d.state.statStages.toMutableList().also { it[5]=if(crit) 1 else -1 }))
                }
                assertEquals(name,measured("underwater-${move.lowercase()}-dive-$name"),damage(build(request(move,name in listOf("crit","crit-stages")),a,d)))
            }
            val a=observation(true,item="Life Orb").let { it.copy(state=it.state.copy(rawSpAttack=153)) }
            val d=semi(observation(false,"Filter"),2).let { it.copy(state=it.state.copy(speciesId=59,sideStatuses=2,types=listOf(HnsBattlerTypeObservation(true,11)))) }
            val out=build(request(move).copy(defender=request().defender.copy(species="Arcanine")),a,d)
            val expected=measured("underwater-${move.lowercase()}-dive-rounding")
            assertEquals(expected,damage(out))
            val wrong=JSONObject(buildCalcRequestJson(ready(out).request))
            wrong.getJSONObject("move").put("hnsMoveId",55).put("hnsIsUnderwater",false).put("hnsDamagesUnderwater",false).getJSONObject("overrides").put("basePower",if(move=="Surf") 180 else 70)
            wrong.getJSONObject("move").remove("hnsMoveFamily")
            assertNotEquals(expected,runJson(wrong.toString()))
        }
    }
    @Test fun `underwater execution preserves independent immunity and suppression boundaries`() {
        for(move in listOf("Surf","Whirlpool")) {
            for(ability in listOf("Water Absorb","Dry Skin","Storm Drain")) {
                val out=build(request(move),d=semi(observation(false,ability),2))
                assertEquals(measured("underwater-${move.lowercase()}-${ability.lowercase().replace(' ','-')}-dive"),damage(out))
                assertEquals(List(16) {0},damage(out))
            }
            assertTrue(build(request(move),observation(true,"Mold Breaker"),semi(observation(false,"Water Absorb"),2)) is CalcRequestOutcome.Refused)
            assertEquals(List(16) {0},damage(build(request(move),observation(true,"Mold Breaker"),semi(observation(false,"Water Absorb","Ability Shield"),2))))
            for(flag in listOf("gas","gastro")) {
                val d=semi(observation(false,"Water Absorb"),2)
                assertTrue(build(request(move),d=d.copy(state=d.state.copy(volatileGastroAcid=flag=="gastro",volatileNeutralizingGas=flag=="gas"))) is CalcRequestOutcome.Refused)
            }
        }
    }
    @Test fun `Whirlpool initial hit excludes duration residual and escape mechanics`() {
        for(item in listOf("Binding Band","Grip Claw","Shed Shell")) {
            val out=build(request("Whirlpool"),observation(true,item=item))
            assertEquals(damage(build(request("Whirlpool"))),damage(out))
            if(item=="Binding Band") assertTrue(ready(out).verdict.hnsItemDecisions.any { it.rule=="whirlpool_binding_band_post_hit_only" })
        }
        assertEquals(measured("underwater-whirlpool-magic-guard"),damage(build(request("Whirlpool"),d=observation(false,"Magic Guard"))))
        val wrap=ready(build(request("Wrap"),observation(true,item="Binding Band")))
        assertFalse(wrap.verdict.hnsItemDecisions.any { it.rule=="whirlpool_binding_band_post_hit_only" })
        assertTrue(build(request("Dive"),observation(true,item="Binding Band").let { it.copy(state=it.state.copy(
            contactReactionStateObserved=true, protectedMethod=0)) }) is CalcRequestOutcome.Ready)
    }
    @Test fun `noncontact source flags keep contact modifiers irrelevant`() {
        for(move in listOf("Surf","Whirlpool")) {
            val neutral=damage(build(request(move)))
            assertEquals(neutral,damage(build(request(move),observation(true,"Tough Claws"))))
            assertEquals(neutral,damage(build(request(move),d=observation(false,"Fluffy"))))
        }
    }
    @Test fun `underwater terrain applicability is false without inventing ungroundedness`() {
        val a=observation(true).let { it.copy(state=it.state.copy(fieldStatuses=0x40)) }
        val d=semi(observation(false),2).let { it.copy(state=it.state.copy(fieldStatuses=0x40)) }
        val out=build(a=a,d=d)
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,ready(out).request.hnsLiveBattleState!!.defenderTerrainApplicability)
        assertEquals(measured("underwater-surf-dive-grassy"),damage(out))
    }
    @Test fun `shipped engine rejects forged flag wrong ID missing state and unsupported family descriptors`() {
        val json=JSONObject(buildCalcRequestJson(ready(build()).request))
        for(kind in listOf("id","effect","flag","family","state","missing","doubles")) {
            val fake=JSONObject(json.toString())
            when(kind) {
                "id" -> fake.getJSONObject("move").put("hnsMoveId",55)
                "effect" -> fake.getJSONObject("move").put("hnsMoveEffect","EFFECT_BRINE")
                "flag" -> fake.getJSONObject("move").put("hnsIsUnderwater",false)
                "family" -> fake.getJSONObject("move").remove("hnsMoveFamily")
                "state" -> fake.getJSONObject("defender").put("hnsSemiInvulnerableState",3)
                "missing" -> fake.getJSONObject("defender").remove("hnsSemiInvulnerableState")
                "doubles" -> fake.getJSONObject("field").put("gameType","Doubles")
            }
            val process=ProcessBuilder("node",File(root,"tools/calc-bundler/run_production_request.js").path,File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
            process.outputStream.bufferedWriter().use { it.write(fake.toString()) }
            val text=process.inputStream.bufferedReader().readText()
            assertEquals(kind+text,0,process.waitFor())
            assertFalse(kind+text,JSONObject(text).getBoolean("success"))
        }
    }
}
