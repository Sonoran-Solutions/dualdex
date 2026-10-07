package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HnsScaleShotProductionBoundaryTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "ci.sh").isFile }
    private fun observation(attacker: Boolean, ability: String = "Insomnia", item: String? = null, hp: Int = 60000,
        gastro: Boolean = false, gas: Boolean = false, embargo: Boolean = false, magicRoom: Boolean = false) =
        Baseline.observation(if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, if (attacker) 68 else 143, if (attacker) "Machamp" else "Snorlax",
            if (attacker) listOf("Fighting") else listOf("Normal"), checkNotNull(HnsAbilityRegistry.classify(ability).abilityId),
            item?.let(HnsItemRegistry::resolveIdByName) ?: 0, ability,
            Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0), 2).let { o ->
            o.copy(state = o.state.copy(rawAttack=151, rawSpAttack=151, rawDefense=109, rawSpDefense=109,
                rawSpeed=if (attacker) 200 else 100, hp=if (attacker) 200 else hp, maxHp=if (attacker) 200 else hp,
                contactReactionStateObserved=true, chosenMove=33, protectedMethod=0, persistentVolatilesObserved=true,
                volatileGastroAcid=gastro, groupDVolatilesObserved=true, volatileNeutralizingGas=gas,
                itemVolatilesObserved=true, volatileEmbargo=embargo,
                fieldStatuses=if (magicRoom) HnsFieldStatusData.STATUS_FIELD_MAGIC_ROOM else 0))
        }
    private fun request() = DamageCalculationRequest(
        attacker=CalcPokemonInput("Machamp", 50, origin=CalcInputOrigin.LIVE_READ, partySlot=0),
        defender=CalcPokemonInput("Snorlax", 50, origin=CalcInputOrigin.LIVE_READ, partySlot=1),
        move=CalcMoveInput("Scale Shot"), field=CalcFieldInput(gameType="Singles"))
    private fun ready(a: BattlerRuntimeObservation=observation(true), d: BattlerRuntimeObservation=observation(false)) =
        CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request(), Baseline.challengeSettings, a, d, activeBattle=true)
            .let { (it as? CalcRequestOutcome.Ready)?.request ?: error("Unexpected refusal: $it") }
    private fun calculate(json: String): JSONObject {
        val p=ProcessBuilder("node",File(root,"tools/calc-bundler/run_production_request.js").path,
            File(root,"app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(json) }
        val result=JSONObject(p.inputStream.bufferedReader().readText()); assertEquals(0,p.waitFor()); return result
    }

    @Test fun `source descriptor has dedicated family and exact production damage result`() {
        val metadata=JSONObject(File(root,"tools/hns-move-mechanics/hns_move_damage_metadata.json").readText()).getJSONObject("moves").getJSONObject("727")
        val bound=ready(); val json=JSONObject(buildCalcRequestJson(bound)); val move=json.getJSONObject("move")
        assertEquals("VARIABLE_MULTI_HIT_SCALE_SHOT",metadata.getString("family"))
        assertEquals("cf06a2be103ecbb1f1ba4e1a47d48b8f06d45e37844514d65b7e361ee7d0f42a",metadata.getString("descriptorSha256"))
        assertEquals("VARIABLE_MULTI_HIT_SCALE_SHOT",move.getString("hnsMoveFamily"))
        assertFalse(move.optBoolean("hnsVariableMultiHitPlain", false))
        assertFalse(727 in Hns205MoveEffects.variableMultiHitPlainMoveIds)
        assertEquals(setOf(727),Hns205MoveEffects.variableMultiHitScaleShotMoveIds)
        assertEquals("MOVE_EFFECT_SCALE_SHOT",metadata.getJSONArray("additionalEffects").getJSONObject(0).getString("moveEffect"))
        assertEquals(HnsMoveMechanicsCategory.VARIABLE_MULTI_HIT_SCALE_SHOT,HnsMoveMechanicsRegistry.classify(727).category)
        val response=calculate(json.toString()); assertTrue(response.toString(),response.getBoolean("success"))
        val result=parseRepeatedStrikeResult(response)!!
        assertEquals(listOf(2,3,4,5),result.nominalCounts); assertEquals(16,result.firstStrikeRolls.size)
        assertEquals(listOf(2,3,4,5),result.totals.map { it.nominalCount })
        assertTrue(response.getJSONArray("damage").length()==0)
        assertTrue(result.presentation.contains("Across possible hit counts:"))
        for (total in result.totals) {
            assertEquals(total.nominalCount*result.firstStrikeRolls.first(),total.minHpLoss)
            assertEquals(total.nominalCount*result.firstStrikeRolls.last(),total.maxHpLoss)
            assertTrue(total.minExecutedHits in 1..total.nominalCount)
            assertTrue(total.maxExecutedHits in total.minExecutedHits..total.nominalCount)
        }
        val hpClamped=parseRepeatedStrikeResult(calculate(buildCalcRequestJson(ready(d=observation(false,hp=20)))).also {
            assertTrue(it.toString(),it.getBoolean("success"))
        })!!
        assertTrue(hpClamped.totals.all { it.maxHpLoss == 20 && it.minHpLoss <= 20 })
        val technician=parseRepeatedStrikeResult(calculate(buildCalcRequestJson(ready(observation(true,"Technician")))).also {
            assertTrue(it.toString(),it.getBoolean("success"))
        })!!
        assertEquals(16,technician.firstStrikeRolls.size)
        assertTrue(technician.firstStrikeRolls.zip(result.firstStrikeRolls).all { (boosted,baseRoll)->boosted>baseRoll })
        val beakBlast=observation(false).let { it.copy(state=it.state.copy(chosenMove=653)) }
        assertTrue("non-contact Scale Shot must not need a Beak Blast witness",ready(d=beakBlast).attacker.species.isNotEmpty())
    }

    @Test fun `shared count authority and suppression gates apply to Scale Shot`() {
        fun counts(a: BattlerRuntimeObservation,d: BattlerRuntimeObservation=observation(false)) =
            HnsRepeatedStrikeCountAuthority.forRequest(ready(a,d))
        assertEquals(listOf(5),counts(observation(true,"Skill Link")).nominalCounts)
        assertEquals(listOf(4,5),counts(observation(true,item="Loaded Dice")).nominalCounts)
        assertEquals(listOf(5),counts(observation(true,"Skill Link","Loaded Dice")).nominalCounts)
        assertEquals(listOf(2,3,4,5),counts(observation(true,"Skill Link",gastro=true)).nominalCounts)
        val gas=observation(false,"Neutralizing Gas",gas=true)
        assertEquals(listOf(2,3,4,5),counts(observation(true,"Skill Link"),gas).nominalCounts)
        assertEquals(listOf(5),counts(observation(true,"Skill Link","Ability Shield"),gas).nominalCounts)
        assertEquals(listOf(2,3,4,5),counts(observation(true,item="Loaded Dice",magicRoom=true),observation(false,magicRoom=true)).nominalCounts)
        assertEquals(listOf(2,3,4,5),counts(observation(true,item="Loaded Dice",embargo=true)).nominalCounts)
        assertEquals(listOf(2,3,4,5),counts(observation(true,"Klutz","Loaded Dice")).nominalCounts)
        val result=calculate(buildCalcRequestJson(ready(observation(true,"Skill Link")))).getJSONObject("repeatedStrike")
        assertEquals("[5]",result.getJSONArray("nominalCounts").toString())
    }

    @Test fun `QuickJS rejects descriptor family count and caller completion forgeries`() {
        val original=JSONObject(buildCalcRequestJson(ready()))
        assertTrue(calculate(original.toString()).getBoolean("success"))
        fun rejected(name:String, edit:(JSONObject)->Unit) { val x=JSONObject(original.toString());edit(x);assertFalse(name,calculate(x.toString()).optBoolean("success",false)) }
        for ((key,value) in listOf("hnsMoveFamily" to "VARIABLE_MULTI_HIT_PLAIN", "hnsDescriptorSha256" to "0".repeat(64),
            "hnsSourceName" to "Other", "hnsSourceType" to "TYPE_FIRE", "hnsSourceCategory" to "DAMAGE_CATEGORY_SPECIAL",
            "hnsMoveEffect" to "EFFECT_MULTI_HIT", "hnsSourcePower" to 26, "hnsSourceAccuracy" to 91,
            "hnsSourcePp" to 21, "hnsSourceTarget" to "TARGET_BOTH", "hnsSourcePriority" to 1,
            "hnsMultiHit" to false, "hnsFixedRepeatedStrike" to true, "hnsMakesContact" to true,
            "hnsUnknownContact" to true, "hnsUnknownPunching" to true,
            "hnsSheerForceAffected" to true, "hnsUnknownSheerForce" to true))
            rejected(key) { it.getJSONObject("move").put(key,value) }
        rejected("fixed strike count") { it.getJSONObject("move").put("hnsSourceStrikeCount",2) }
        rejected("ballistic flag") { it.getJSONObject("move").put("hnsMoveAbilityFlags",JSONArray(listOf("ballisticMove"))) }
        rejected("pre-attack effect") { it.getJSONObject("move").put("hnsSourcePreAttackEffects",JSONArray(listOf("effect"))) }
        rejected("remove additional effect") { it.getJSONObject("move").put("hnsSourceAdditionalEffects",JSONArray()) }
        rejected("caller completion claim") { it.put("hnsScaleShotCompletion",true) }
        rejected("caller post-sequence stages") { it.put("hnsPostSequenceStages",JSONObject().put("defense",-1).put("speed",1)) }
        rejected("fake counts") { it.put("hnsRepeatedStrikeNominalCounts",JSONArray(listOf(5))) }
        rejected("fake count mode") { it.put("hnsRepeatedStrikeCountMode","SKILL_LINK") }
        rejected("fake Skill Link") { it.getJSONObject("attacker").put("hnsEffectiveAbilityId",92) }
        rejected("fake Loaded Dice") { it.getJSONObject("attacker").put("hnsEffectiveHoldEffect","HOLD_EFFECT_LOADED_DICE") }
        for (name in listOf("Twineedle","Triple Kick","Triple Axel","Population Bomb","Beat Up")) {
            val r=request().copy(move=CalcMoveInput(name))
            assertTrue(name,CalcRequestBoundary.build(Baseline.profile,Baseline.trust,r,Baseline.challengeSettings,
                observation(true),observation(false),activeBattle=true) is CalcRequestOutcome.Refused)
        }
    }
}
