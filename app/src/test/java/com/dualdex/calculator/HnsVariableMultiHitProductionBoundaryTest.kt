package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HnsVariableMultiHitProductionBoundaryTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "ci.sh").isFile }
    private val moves = listOf("Arm Thrust", "Bone Rush", "Bullet Seed", "Comet Punch", "Double Slap", "Fury Attack",
        "Fury Swipes", "Icicle Spear", "Pin Missile", "Rock Blast", "Spike Cannon", "Tail Slap")

    private fun observation(
        attacker: Boolean,
        ability: String = "Insomnia",
        item: String? = null,
        hp: Int = 60000,
        gastroAcid: Boolean = false,
        neutralizingGas: Boolean = false,
        embargo: Boolean = false,
        magicRoom: Boolean = false,
        sourceSpecies: String? = null,
        sourceTypes: List<String>? = null,
        sourceSpeciesId: Int? = null
    ) = Baseline.observation(
        if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
        if (attacker) 0 else 1,
        sourceSpeciesId ?: if (attacker) 68 else 143,
        sourceSpecies ?: if (attacker) "Machamp" else "Snorlax",
        sourceTypes ?: if (attacker) listOf("Fighting") else listOf("Normal"),
        checkNotNull(HnsAbilityRegistry.classify(ability).abilityId),
        item?.let(HnsItemRegistry::resolveIdByName) ?: 0,
        ability,
        Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0),
        2
    ).let { o ->
        o.copy(state = o.state.copy(
            rawAttack = 151, rawSpAttack = 151, rawDefense = 109, rawSpDefense = 109,
            rawSpeed = if (attacker) 200 else 100,
            hp = if (attacker) 200 else hp, maxHp = if (attacker) 200 else hp,
            contactReactionStateObserved = true, chosenMove = 33, protectedMethod = 0,
            persistentVolatilesObserved = true, volatileGastroAcid = gastroAcid,
            groupDVolatilesObserved = true, volatileNeutralizingGas = neutralizingGas,
            itemVolatilesObserved = true, volatileEmbargo = embargo,
            fieldStatuses = if (magicRoom) HnsFieldStatusData.STATUS_FIELD_MAGIC_ROOM else 0
        ))
    }

    private fun request(move: String, defenderSpecies: String = "Snorlax") = DamageCalculationRequest(
        attacker = CalcPokemonInput(species = "Machamp", level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
        defender = CalcPokemonInput(species = defenderSpecies, level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
        move = CalcMoveInput(move), field = CalcFieldInput(gameType = "Singles")
    )

    private fun build(
        move: String,
        a: BattlerRuntimeObservation = observation(true),
        d: BattlerRuntimeObservation = observation(false),
        r: DamageCalculationRequest = request(move)
    ) = CalcRequestBoundary.build(Baseline.profile, Baseline.trust, r, Baseline.challengeSettings, a, d, activeBattle = true)

    private fun ready(move: String, a: BattlerRuntimeObservation = observation(true), d: BattlerRuntimeObservation = observation(false), r: DamageCalculationRequest = request(move)) =
        (build(move, a, d, r) as? CalcRequestOutcome.Ready) ?: error("Unexpected refusal: ${build(move, a, d, r)}")

    private fun calculate(json: String): JSONObject {
        val p = ProcessBuilder("node", File(root, "tools/calc-bundler/run_production_request.js").path,
            File(root, "app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(json) }
        val result = JSONObject(p.inputStream.bufferedReader().readText())
        assertEquals(0, p.waitFor())
        return result
    }

    private fun production(request: DamageCalculationRequest): JSONObject = calculate(buildCalcRequestJson(request))
        .also { assertTrue(it.toString(), it.getBoolean("success")) }

    @Test fun `twelve frozen descriptors serialize and branch by every source-authorized count`() {
        val metadata = JSONObject(File(root, "tools/hns-move-mechanics/hns_move_damage_metadata.json").readText()).getJSONObject("moves")
        for (name in moves) {
            val bound = ready(name).request
            val info = HeartAndSoul205DataPack.getMoveByName(name)!!
            val id = info.id
            val source = metadata.getJSONObject(id.toString())
            val moveJson = JSONObject(buildCalcRequestJson(bound)).getJSONObject("move")
            assertTrue(id.toString(), id in Hns205MoveEffects.variableMultiHitPlainMoveIds)
            assertEquals("VARIABLE_MULTI_HIT_PLAIN", moveJson.getString("hnsMoveFamily"))
            assertEquals(source.getString("descriptorSha256"), moveJson.getString("hnsDescriptorSha256"))
            assertEquals(source.getInt("power"), moveJson.getInt("hnsSourcePower"))
            assertEquals(source.getInt("accuracy"), moveJson.getInt("hnsSourceAccuracy"))
            assertEquals(source.getInt("pp"), moveJson.getInt("hnsSourcePp"))
            assertEquals(source.getBoolean("makesContact"), moveJson.getBoolean("hnsMakesContact"))
            assertEquals(source.getBoolean("punchingMove"), moveJson.getJSONArray("hnsMoveAbilityFlags").length() > 0)
            assertEquals("EFFECT_HIT", Hns205MoveEffects.effectById[id])
            assertEquals(HnsMoveMechanicsCategory.VARIABLE_MULTI_HIT_PLAIN, HnsMoveMechanicsRegistry.classify(id).category)
            assertEquals(listOf(2, 3, 4, 5), HnsRepeatedStrikeCountAuthority.forRequest(bound).nominalCounts)
            val response = production(bound)
            val sequence = parseRepeatedStrikeResult(response)!!
            val rolls = sequence.firstStrikeRolls
            assertEquals(16, rolls.size)
            assertEquals(listOf(2, 3, 4, 5), sequence.nominalCounts)
            assertEquals(listOf(2, 3, 4, 5), sequence.totals.map { it.nominalCount })
            for (total in sequence.totals) {
                assertEquals(total.nominalCount * rolls.first(), total.minHpLoss)
                assertEquals(total.nominalCount * rolls.last(), total.maxHpLoss)
                assertEquals(total.nominalCount, total.minExecutedHits)
                assertEquals(total.nominalCount, total.maxExecutedHits)
            }
            assertTrue(sequence.presentation.contains("2 hits:"))
            assertTrue(sequence.presentation.contains("5 hits:"))
            assertTrue(sequence.presentation.contains("Across possible hit counts:"))
            assertFalse(response.getJSONArray("damage").length() > 0)
            assertEquals("", response.getString("koChanceText"))
        }
    }

    @Test fun `Skill Link Loaded Dice and suppression select exact count alternatives`() {
        fun count(a: BattlerRuntimeObservation, d: BattlerRuntimeObservation = observation(false)) =
            HnsRepeatedStrikeCountAuthority.forRequest(ready("Bullet Seed", a, d).request)
        assertEquals(listOf(5), count(observation(true, "Skill Link")).nominalCounts)
        assertEquals(listOf(4, 5), count(observation(true, item = "Loaded Dice")).nominalCounts)
        assertEquals(listOf(5), count(observation(true, "Skill Link", "Loaded Dice")).nominalCounts)
        assertEquals(listOf(2, 3, 4, 5), count(observation(true, "Skill Link", gastroAcid = true)).nominalCounts)
        assertEquals(listOf(2, 3, 4, 5), count(
            observation(true, item = "Loaded Dice", magicRoom = true), observation(false, magicRoom = true)
        ).nominalCounts)
        assertEquals(listOf(2, 3, 4, 5), count(observation(true, item = "Loaded Dice", embargo = true)).nominalCounts)
        assertEquals(listOf(2, 3, 4, 5), count(observation(true, "Klutz", "Loaded Dice")).nominalCounts)
        val neutralizingGas = observation(false, "Neutralizing Gas", neutralizingGas = true)
        assertEquals(listOf(2, 3, 4, 5), count(observation(true, "Skill Link"), neutralizingGas).nominalCounts)
        assertEquals(listOf(4, 5), count(observation(true, "Skill Link", "Loaded Dice"), neutralizingGas).nominalCounts)
        assertEquals(listOf(5), count(observation(true, "Skill Link", "Ability Shield"), neutralizingGas).nominalCounts)
        val linkRequest = ready("Bullet Seed", observation(true, "Skill Link")).request
        val linkUnknown = linkRequest.copy(hnsLiveBattleState = linkRequest.hnsLiveBattleState!!.copy(attackerNeutralizingGas = null))
        assertNull(HnsRepeatedStrikeCountAuthority.forRequest(linkUnknown).nominalCounts)
        val diceRequest = ready("Bullet Seed", observation(true, item = "Loaded Dice")).request
        val diceUnknown = diceRequest.copy(hnsLiveBattleState = diceRequest.hnsLiveBattleState!!.copy(fieldStatuses = null))
        assertNull(HnsRepeatedStrikeCountAuthority.forRequest(diceUnknown).nominalCounts)
    }

    @Test fun `conditional branches clamp HP and zero-damage immunity has zero executed hits`() {
        val lowHp = ready("Bullet Seed", d = observation(false, hp = 30)).request
        val lowHpResult = parseRepeatedStrikeResult(production(lowHp))!!
        val lo = lowHpResult.firstStrikeRolls.minOrNull()!!
        val hi = lowHpResult.firstStrikeRolls.maxOrNull()!!
        for (total in lowHpResult.totals) {
            val n = total.nominalCount
            assertEquals(minOf(30, n * lo), total.minHpLoss)
            assertEquals(minOf(30, n * hi), total.maxHpLoss)
            assertEquals(minOf(n, (30 + hi - 1) / hi), total.minExecutedHits)
            assertEquals(minOf(n, (30 + lo - 1) / lo), total.maxExecutedHits)
        }

        val immune = ready("Double Slap", d = observation(false, hp = 600, sourceSpecies = "Gengar", sourceTypes = listOf("Ghost"), sourceSpeciesId = 94),
            r = request("Double Slap", defenderSpecies = "Gengar")).request
        val immuneResult = parseRepeatedStrikeResult(production(immune))!!
        assertEquals(List(16) { 0 }, immuneResult.firstStrikeRolls)
        for (total in immuneResult.totals) {
            assertEquals(0, total.minHpLoss)
            assertEquals(0, total.maxHpLoss)
            assertEquals(0, total.minExecutedHits)
            assertEquals(0, total.maxExecutedHits)
        }
    }

    @Test fun `fixed two and distinct repeated families remain outside variable count path`() {
        val fixed = ready("Double Kick", observation(true, "Skill Link", "Loaded Dice")).request
        assertEquals(listOf(2), HnsRepeatedStrikeCountAuthority.forRequest(fixed).nominalCounts)
        for (move in listOf("Scale Shot", "Twineedle", "Triple Kick", "Triple Axel", "Population Bomb", "Beat Up"))
            assertTrue(move, build(move) is CalcRequestOutcome.Refused)
        assertTrue("Parental Bond-created extra hit", build("Bullet Seed", a = observation(true, "Parental Bond")) is CalcRequestOutcome.Refused)
    }

    @Test fun `QuickJS rejects forged count ability item suppression stability and descriptor claims`() {
        val bound = ready("Bullet Seed").request
        val original = JSONObject(buildCalcRequestJson(bound))
        assertTrue(calculate(original.toString()).getBoolean("success"))
        fun rejected(label: String, mutate: (JSONObject) -> Unit) {
            val forged = JSONObject(original.toString())
            mutate(forged)
            val response = calculate(forged.toString())
            assertFalse("$label: $response", response.optBoolean("success", false))
        }
        rejected("fake nominal counts") { it.put("hnsRepeatedStrikeNominalCounts", org.json.JSONArray(listOf(5))) }
        rejected("fake count mode") { it.put("hnsRepeatedStrikeCountMode", "SKILL_LINK") }
        rejected("fake distribution") { it.put("hnsRepeatedStrikeDistribution", JSONObject().put("2", 100)) }
        rejected("fake Skill Link identity") { it.getJSONObject("attacker").put("hnsEffectiveAbilityId", 92) }
        rejected("fake Loaded Dice effect") { it.getJSONObject("attacker").put("hnsEffectiveHoldEffect", "HOLD_EFFECT_LOADED_DICE") }
        rejected("fake suppression") { it.getJSONObject("attacker").put("hnsGastroAcid", true) }
        rejected("fake Neutralizing Gas suppression") { it.getJSONObject("attacker").put("hnsNeutralizingGas", true) }
        rejected("fake stability") { it.put("hnsRepeatedStrikeStable", true) }
        rejected("forged source descriptor") { it.getJSONObject("move").put("hnsDescriptorSha256", "0".repeat(64)) }
        rejected("Scale Shot relabelled as plain") {
            val move = it.getJSONObject("move")
            move.put("name", "Scale Shot").put("hnsMoveId", 727)
                .put("hnsMoveFamily", "VARIABLE_MULTI_HIT_PLAIN")
        }
        rejected("fake executable totals") { it.put("repeatedStrike", JSONObject().put("totals", org.json.JSONArray())) }
    }

    @Test fun `contact reaction witness is move-specific and MOVE_NONE remains unknown`() {
        val contactUnknown = observation(false).let { it.copy(state = it.state.copy(chosenMove = 0)) }
        val beakBlast = observation(false).let { it.copy(state = it.state.copy(chosenMove = 653)) }
        val contactMoves = listOf("Arm Thrust", "Comet Punch", "Double Slap", "Fury Attack", "Fury Swipes", "Tail Slap")
        val nonContactMoves = listOf("Bone Rush", "Bullet Seed", "Icicle Spear", "Pin Missile", "Rock Blast", "Spike Cannon")
        for (move in contactMoves) {
            assertTrue("$move needs committed defender move", build(move, d = contactUnknown) is CalcRequestOutcome.Refused)
            assertTrue("$move selected Beak Blast", build(move, d = beakBlast) is CalcRequestOutcome.Refused)
            assertTrue("$move Long Reach", build(move, a = observation(true, "Long Reach"), d = contactUnknown) is CalcRequestOutcome.Ready)
            assertTrue("$move Protective Pads", build(move, a = observation(true, item = "Protective Pads"), d = contactUnknown) is CalcRequestOutcome.Ready)
        }
        for (move in nonContactMoves) {
            assertTrue("$move has no contact witness", build(move, d = contactUnknown) is CalcRequestOutcome.Ready)
            assertTrue("$move ignores contact Beak Blast witness", build(move, d = beakBlast) is CalcRequestOutcome.Ready)
        }
    }
}
