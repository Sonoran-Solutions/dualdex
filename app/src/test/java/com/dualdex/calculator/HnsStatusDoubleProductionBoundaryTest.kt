package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import com.dualdex.battle.DamageBlockerPresentation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real boundary -> production JSON -> shipped bundle; assertions use measured pinned rolls. */
class HnsStatusDoubleProductionBoundaryTest {
    private val root get() = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "ci.sh").isFile }
    private fun observation(attacker: Boolean, ability: String = "Insomnia", item: String? = null): BattlerRuntimeObservation {
        val abilityId = checkNotNull(HnsAbilityRegistry.classify(ability).abilityId)
        val itemId = item?.let(HnsItemRegistry::resolveIdByName) ?: 0
        return Baseline.observation(if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, if (attacker) 68 else 9, if (attacker) "Machamp" else "Blastoise",
            if (attacker) listOf("Fighting") else listOf("Water"), abilityId, itemId, ability,
            Hns205ItemCatalogue.get(itemId), 2).let { o -> o.copy(state = o.state.copy(
                rawAttack = 151, rawSpAttack = 151, rawDefense = 109, rawSpDefense = 109,
                healBlockObserved = true, volatileHealBlock = false,
                rawSpeed = if (attacker) 40 else 100, hp = if(attacker) 200 else 60000, maxHp = if(attacker) 200 else 60000)) }
    }
    private fun request(move: String = "Surf", crit: Boolean = false) = DamageCalculationRequest(
        attacker = CalcPokemonInput(species="Machamp", level=50, origin=CalcInputOrigin.LIVE_READ, partySlot=0),
        defender = CalcPokemonInput(species="Blastoise", level=50, origin=CalcInputOrigin.LIVE_READ, partySlot=1),
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
    private fun runResponse(json: String): JSONObject {
        val process = ProcessBuilder("node", File(root,"tools/calc-bundler/run_production_request.js").path,
            File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write(json) }
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(text,0,process.waitFor())
        return JSONObject(text)
    }
    private fun runJson(json: String): List<Int> {
        val response = runResponse(json)
        assertTrue(response.toString(), response.getBoolean("success"))
        val rolls=response.getJSONArray("damage")
        assertEquals(16,rolls.length())
        return (0..15).map(rolls::getInt)
    }
    private fun measured(id: String): List<Int> {
        val entries=JSONObject(File(root,"tools/hns-damage-oracle/corpus.json").readText()).getJSONArray("entries")
        val e=(0 until entries.length()).map(entries::getJSONObject).first { it.getJSONObject("scenario").getString("id")==id }
        val rolls=e.getJSONArray("rolls")
        return (0..15).map(rolls::getInt)
    }

    private val moves = mapOf("Smelling Salts" to 64, "Wake-Up Slap" to 3, "Venoshock" to 8,
        "Hex" to 16, "Barb Barrage" to 8, "Infernal Parade" to 16)
    private fun status(word: Int, ability: String = "Insomnia") = observation(false, ability).let {
        it.copy(state=it.state.copy(statusObserved=true, status1=word))
    }
    private fun id(move: String, suffix: String) = "status-double-${move.lowercase().replace(' ','-')}-$suffix"

    @Test fun `exact six source predicates and modifier order match pinned rolls`() {
        assertEquals(setOf(265,358,474,506,767,772),Hns205MoveEffects.fixedSingleHitStatusDoubleMoveIds)
        for((move,word) in moves) {
            val moveId=HeartAndSoul205DataPack.getMoveByName(move)!!.id
            assertEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_STATUS_DOUBLE,HnsMoveMechanicsRegistry.classify(moveId).category)
            assertFalse(moveId in Hns205MoveEffects.ordinaryMoveIds)
            for((suffix,raw) in listOf("neutral-player" to 0,"matching-player" to word,
                "sleep" to 3,"poison" to 8,"toxic" to 0x380,"burn" to 16,"freeze" to 32,"paralysis" to 64,"frostbite" to 4096)) {
                val out=build(request(move),d=status(raw))
                assertEquals(id(move,suffix),measured(id(move,suffix)),damage(out))
                val json=JSONObject(buildCalcRequestJson(ready(out).request)).getJSONObject("move")
                assertEquals("FIXED_SINGLE_HIT_STATUS_DOUBLE",json.getString("hnsMoveFamily"))
                assertEquals(Hns205MoveEffects.statusDoublePowerMaskById[moveId],json.getInt("hnsStatusDoubleMask"))
                assertFalse(json.getBoolean("hnsIsOrdinary"))
            }
            assertEquals(measured(id(move,"comatose")),damage(build(request(move),d=status(0,"Comatose"))))
            for(ability in listOf("Technician","Sheer Force")) for(raw in listOf(0,word)) {
                val suffix="${ability.lowercase().replace(' ','-')}-${if(raw==0) "neutral" else "matching"}"
                assertEquals(measured(id(move,suffix)),damage(build(request(move),observation(true,ability),status(raw))))
            }
            val neutral=ready(build(request(move))).request
            val fakeLive=neutral.copy(hnsLiveBattleState=neutral.hnsLiveBattleState!!.copy(defenderStatus1=word))
            assertEquals(damage(build(request(move))),damage(build(fakeLive)))
            val forged=request(move).copy(moveOverride=CalcMoveOverride(basePower=999,type="Fire",category="Physical"),
                defender=request(move).defender.copy(status="psn"))
            assertEquals(damage(build(request(move))),damage(build(forged)))
        }
    }
    @Test fun `status power composes with screens crit stages final items and effective type`() {
        for((move,word) in moves) for(name in listOf("screen","crit","life-orb","normalize","rounding")) {
            var a=observation(true,if(name=="normalize") "Normalize" else "Insomnia",
                if(name in listOf("life-orb","rounding")) "Life Orb" else null)
            var d=status(word)
            d=d.copy(state=d.state.copy(sideStatuses=when(name) { "screen" -> 3; "rounding" -> 2; else -> 0 }))
            if(name=="rounding") {
                a=a.copy(state=a.state.copy(statStages=a.state.statStages.toMutableList().also { it[1]=1; it[4]=1 }))
                d=d.copy(state=d.state.copy(statStages=d.state.statStages.toMutableList().also { it[2]=1; it[5]=1 }))
            }
            assertEquals(id(move,name),measured(id(move,name)),damage(build(request(move,name=="crit"),a,d)))
        }
        val d=status(64).let { it.copy(state=it.state.copy(speciesId=94,types=listOf(HnsBattlerTypeObservation(true,8),HnsBattlerTypeObservation(true,4)))) }
        assertEquals(List(16) { 0 },damage(build(request("Smelling Salts").copy(defender=request().defender.copy(species="Gengar")),d=d)))
    }

    @Test fun `source descriptors cannot be forged at QuickJS`() {
        for(move in moves.keys) {
            val original=JSONObject(buildCalcRequestJson(ready(build(request(move))).request))
            for(field in listOf("mask","family","id","effect","power","status","ability","ordinary","substitute","format")) {
                val fake=JSONObject(original.toString())
                when(field) {
                    "mask" -> fake.getJSONObject("move").put("hnsStatusDoubleMask",1)
                    "family" -> fake.getJSONObject("move").put("hnsMoveFamily","ORDINARY_PROVEN_EQUIVALENT")
                    "id" -> fake.getJSONObject("move").put("hnsMoveId",33)
                    "effect" -> fake.getJSONObject("move").put("hnsMoveEffect","EFFECT_HIT")
                    "power" -> fake.getJSONObject("move").getJSONObject("overrides").put("basePower",999)
                    "status" -> fake.getJSONObject("defender").put("status1",4294967296L)
                    "ability" -> fake.getJSONObject("defender").remove("hnsEffectiveAbilityId")
                    "ordinary" -> fake.getJSONObject("move").put("hnsIsOrdinary",true)
                    "substitute" -> fake.getJSONObject("defender").put("hnsSubstitute",true)
                    "format" -> fake.getJSONObject("field").put("gameType","Doubles")
                }
                val refused = runResponse(fake.toString())
                assertFalse("accepted forged $field: $refused", refused.getBoolean("success"))
                assertTrue(refused.getString("error").isNotBlank())
            }
            assertEquals(damage(build(request(move))),damage(build(request(move),observation(true,"Comatose"))))
        }
    }

    @Test fun `required status words refuse without weakening other live gates`() {
        for(move in moves.keys) {
            for(word in listOf(-1,8192,0x100,0x18,0x48,0x81,0xffff)) {
                val out=build(request(move),d=status(word)) as CalcRequestOutcome.Refused
                assertTrue(CalcLimitation.HNS_DEFENDER_STATUS_UNKNOWN in out.verdict.blockingLimitations)
            }
            val unknownAbility=status(0).let { it.copy(state=it.state.copy(abilityId=null)) }
            assertTrue(build(request(move),d=unknownAbility) is CalcRequestOutcome.Refused)
            val unread=status(0).let { it.copy(state=it.state.copy(statusObserved=false)) }
            assertTrue(CalcLimitation.HNS_DEFENDER_STATUS_UNKNOWN in (build(request(move),d=unread) as CalcRequestOutcome.Refused).verdict.blockingLimitations)
            val substitute=status(64).let { it.copy(state=it.state.copy(volatileSubstitute=true)) }
            assertTrue(CalcLimitation.HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED in (build(request(move),d=substitute) as CalcRequestOutcome.Refused).verdict.blockingLimitations)
            assertTrue(build(request(move).copy(field=CalcFieldInput(gameType="Doubles"))) is CalcRequestOutcome.Refused)
            assertTrue(build(request(move),d=status(0).let { it.copy(state=it.state.copy(partySlot=0)) }) is CalcRequestOutcome.Refused)
        }
        for(word in listOf(0,1,2,3,4,5,6,7,8,16,32,64,128,0xf80,4096)) assertTrue(HnsDefenderStatus.isValid(word))
        for(move in listOf("Brine","Double Slap","Gyro Ball","Facade")) {
            val entry=HnsMoveMechanicsRegistry.classify(HeartAndSoul205DataPack.getMoveByName(move)!!.id)
            assertNotEquals(HnsMoveMechanicsCategory.FIXED_SINGLE_HIT_STATUS_DOUBLE,entry.category)
            assertTrue(build(request(move)) is CalcRequestOutcome.Refused)
        }
    }
}
