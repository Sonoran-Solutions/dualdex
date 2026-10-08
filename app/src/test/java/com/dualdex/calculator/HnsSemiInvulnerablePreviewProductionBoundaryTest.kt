package com.dualdex.calculator

import com.dualdex.battle.*
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HnsSemiInvulnerablePreviewProductionBoundaryTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "ci.sh").isFile }
    private val names = listOf("Fly", "Dig", "Dive", "Bounce", "Phantom Force")
    private fun observation(attacker: Boolean, ability: String = "Insomnia", item: String? = null,
                            species: String = if (attacker) "Machamp" else "Blastoise",
                            types: List<String> = if (attacker) listOf("Fighting") else listOf("Water")) =
        Baseline.observation(if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1, HeartAndSoul205DataPack.getSpeciesByName(species)?.id ?: error("$species missing from pinned data pack"), species, types,
            checkNotNull(HnsAbilityRegistry.classify(ability).abilityId),
            item?.let(HnsItemRegistry::resolveIdByName) ?: 0, ability,
            Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0), 2)
    private fun request(move: String, engineVector: Boolean = false,
                        defenderSpecies: String = if (engineVector && move == "Phantom Force") "Machamp" else if (engineVector) "Persian" else "Blastoise") = DamageCalculationRequest(
        attacker = CalcPokemonInput("Machamp", 50,
            ivs = if (engineVector) StatBlock(atk = 31) else null,
            evs = if (engineVector) StatBlock(atk = 4) else null,
            origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
        defender = CalcPokemonInput(defenderSpecies, 50,
            ivs = if (engineVector) StatBlock(def = 31) else null,
            evs = if (engineVector) StatBlock(def = if (move == "Phantom Force") 68 else 228) else null,
            origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
        move = CalcMoveInput(move), field = CalcFieldInput(gameType = "Singles"))
    private fun neutral(a: Boolean, ability: String = "Insomnia", item: String? = null,
                        rawAttack: Int = 1, rawDefense: Int = 1,
                        species: String = if (a) "Machamp" else "Blastoise",
                        types: List<String> = if (a) listOf("Fighting") else listOf("Water")) =
        observation(a, ability, item, species, types).let { it.copy(state = it.state.copy(
        rawAttack = rawAttack, rawDefense = rawDefense,
        contactReactionStateObserved = true, protectedMethod = 0)) }
    private fun build(move: String, a: BattlerRuntimeObservation = neutral(true), d: BattlerRuntimeObservation = neutral(false),
                      engineVector: Boolean = false, defenderSpecies: String = "Blastoise") =
        CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request(move, engineVector, defenderSpecies), Baseline.challengeSettings, a, d, activeBattle = true)
    private fun buildEngineVector(move: String, a: BattlerRuntimeObservation = neutral(true, rawAttack = 151),
                                  d: BattlerRuntimeObservation = neutral(false, rawDefense = 109)): CalcRequestOutcome {
        val defender = if (move == "Phantom Force") "Machamp" else "Persian"
        val types = if (move == "Phantom Force") listOf("Fighting") else listOf("Normal")
        val exactDefender = observation(false, species = defender, types = types).let { observed ->
            observed.copy(state = observed.state.copy(rawAttack = d.state.rawAttack,
                rawDefense = d.state.rawDefense ?: 109, contactReactionStateObserved = true, protectedMethod = 0))
        }
        return CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request(move, engineVector = true), Baseline.challengeSettings,
            a, exactDefender,
            activeBattle = true)
    }
    private fun production(json: String): JSONObject {
        val p = ProcessBuilder("node", File(root, "tools/calc-bundler/run_production_request.js").path,
            File(root, "app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(json) }
        val result = JSONObject(p.inputStream.bufferedReader().readText())
        assertEquals(0, p.waitFor())
        return result
    }

    @Test fun `all five exact descriptors produce labelled one strike sixteen roll previews`() {
        assertEquals(setOf(19, 91, 291, 340, 566), Hns205MoveEffects.semiInvulnerablePreviewMoveIds)
        for (name in names) {
            val outcome = build(name) as? CalcRequestOutcome.Ready ?: error("$name refused: ${build(name)}")
            val response = production(buildCalcRequestJson(outcome.request))
            assertTrue("$name: $response request=${buildCalcRequestJson(outcome.request)}", response.optBoolean("success"))
            assertEquals(CalcResultPresentation.DAMAGING_TURN_PREVIEW_SCOPE, response.getString("damageScope"))
            assertEquals(16, response.getJSONArray("damage").length())
            assertEquals(0, response.optJSONArray("repeatedStrike")?.length() ?: 0)
            assertTrue(response.getInt("maxDamage") >= response.getInt("minDamage"))
        }
    }

    @Test fun `successful immunity responses retain damaging-turn scope and source attribution`() {
        val cases = listOf(
            Triple("Phantom Force", "Snorlax", neutral(false, species = "Snorlax", types = listOf("Normal"))),
            Triple("Dig", "Pidgeot", neutral(false, species = "Pidgeot", types = listOf("Normal", "Flying"))),
            Triple("Dive", "Blastoise", neutral(false, "Water Absorb"))
        )
        for ((move, defender, observedDefender) in cases) {
            val outcome = build(move, d = observedDefender, defenderSpecies = defender)
                as? CalcRequestOutcome.Ready ?: error("$move immunity case refused: ${build(move, d = observedDefender, defenderSpecies = defender)}")
            val response = production(buildCalcRequestJson(outcome.request))
            assertTrue("$move: $response", response.optBoolean("success"))
            assertEquals(CalcResultPresentation.DAMAGING_TURN_PREVIEW_SCOPE, response.getString("damageScope"))
            assertEquals(List(16) { 0 }, (0 until response.getJSONArray("damage").length())
                .map { response.getJSONArray("damage").getInt(it) })
            assertEquals(0, response.getInt("minDamage"))
            assertEquals(0, response.getInt("maxDamage"))
            // This is the same parser and authorized execution used by the Calculator surface.
            val parsed = CalcAuthorizedExecution.calculate(outcome.verdict) {
                parseCalcResponseJson(it, response.toString())
            }
            assertTrue("$move: $parsed", parsed.success)
            assertEquals(CalcResultPresentation.DAMAGING_TURN_PREVIEW_SCOPE, parsed.damageScope)
            assertEquals(0, parsed.minDamage)
            assertEquals(0, parsed.maxDamage)
            assertEquals(listOf(0, 0), parsed.range)
            assertEquals(0.0, parsed.effectiveness!!, 0.0)
            val expectedCause = if (move == "Dive")
                CalcImmunityCause("ability", "src/battle_util.c:2448", "Water Absorb")
            else CalcImmunityCause("type", "src/data/types_info.h", "type-chart")
            assertEquals(listOf(expectedCause), parsed.immunityCauses)

            val attacker = pokemon(neutral(true))
            val battle = BattlePresentationBuilder.build(
                moveInfo = MoveDatabase.get(HeartAndSoul205DataPack.getMoveByName(move)!!.id, HeartAndSoul205DataPack),
                currentPp = 10, attacker = attacker, defender = pokemon(observedDefender),
                profile = Baseline.profile, runtimeTrust = Baseline.trust,
                hnsCalculationContext = BattleHnsCalculationContext(
                    listOf(attacker), 0, 1, Baseline.challengeSettings, neutral(true), observedDefender, true),
                calculator = BattleDamageCalculator {
                    parseCalcResponseJson(it, production(buildCalcRequestJson(it)).toString())
                }
            )
            assertEquals(battle.toString(), DamageConfidence.ESTIMATE, battle.damageConfidence)
            assertEquals(parsed.damageScope, battle.damageScope)
            assertEquals(0, battle.minDamage)
            assertEquals(0, battle.maxDamage)
            assertEquals(listOf(0, 0), battle.damageRange)
            assertEquals("${parsed.damageScope} · 0-0 (Estimate)", battle.damageDisplayText)
        }
    }

    private fun pokemon(observed: BattlerRuntimeObservation): ParsedPokemon = ParsedPokemon(
        isValid = true, isEmpty = false, pid = 0, tid = 0, sid = 0, nickname = "", otName = "",
        species = observed.state.speciesId!!, heldItem = 0, level = 50, nature = 0, natureName = "Hardy",
        isShiny = false, abilitySlot = 0, isEgg = false, friendship = 255, experience = 0,
        hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(19, 91, 291, 566), pp = intArrayOf(10, 10, 10, 10),
        currentHp = observed.state.hp, maxHp = observed.state.maxHp,
        attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1, statusCondition = 0
    )

    @Test fun `production parser rejects missing or incorrect preview scope for every family move`() {
        for (move in names) {
            val ready = build(move) as CalcRequestOutcome.Ready
            val response = production(buildCalcRequestJson(ready.request))
            assertTrue(parseCalcResponseJson(ready.request, response.toString()).success)
            for (scope in listOf(null, "", "Damaging-turn preview", JSONObject.NULL, 42)) {
                val changed = JSONObject(response.toString()).put("damageScope", scope)
                val parsed = parseCalcResponseJson(ready.request, changed.toString())
                assertFalse("$move scope=$scope: $parsed", parsed.success)
                assertEquals("Damaging-turn preview scope missing or malformed", parsed.error)
                assertNull(parsed.damageScope)
            }
        }
    }

    @Test fun `all five production roll vectors match the pinned original engine`() {
        val evidence = JSONObject(File(root, "tools/hns-damage-oracle/semi-invulnerable-evidence.json").readText())
        val vectors = evidence.getJSONArray("damageVectors")
        for (move in names) {
            val outcome = buildEngineVector(move) as? CalcRequestOutcome.Ready ?: error("$move refused: ${buildEngineVector(move)}")
            val response = production(buildCalcRequestJson(outcome.request))
            assertTrue("$move: $response", response.optBoolean("success"))
            val expected = (0 until vectors.length()).map { vectors.getJSONObject(it) }
                .first { it.getString("move") == move.uppercase().replace(' ', '_') }.getJSONArray("rolls")
            val actual = response.getJSONArray("damage")
            assertEquals("$move roll count", expected.length(), actual.length())
            for (i in 0 until expected.length()) assertEquals("$move roll $i", expected.getInt(i), actual.getInt(i))
            assertEquals(CalcResultPresentation.DAMAGING_TURN_PREVIEW_SCOPE, response.getString("damageScope"))
        }
    }

    @Test fun `active attacker or defender phase and active Power Herb remain refused`() {
        for (name in names) {
            val charging = neutral(true).let { it.copy(state = it.state.copy(multipleTurns = true)) }
            val attackerSemi = neutral(true).let { it.copy(state = it.state.copy(volatileSemiInvulnerable = HnsGroupDLayout.STATE_ON_AIR)) }
            val defenderSemi = neutral(false).let { it.copy(state = it.state.copy(volatileSemiInvulnerable = HnsGroupDLayout.STATE_UNDERGROUND)) }
            for (outcome in listOf(build(name, charging), build(name, attackerSemi), build(name, d = defenderSemi))) {
                assertTrue(outcome.toString(), outcome is CalcRequestOutcome.Refused)
            }
            val herb = neutral(true, item = "Power Herb")
            assertTrue(build(name, herb).toString(), build(name, herb) is CalcRequestOutcome.Refused)
        }
    }

    @Test fun `Bounce keeps its secondary Sheer Force and final Life Orb arithmetic`() {
        fun damage(move: String, ability: String = "Insomnia", item: String? = null): List<Int> {
            val a = neutral(true, ability, item, rawAttack = 151)
            val out = buildEngineVector(move, a) as? CalcRequestOutcome.Ready ?: error("$move refused: ${buildEngineVector(move, a)}")
            val response = production(buildCalcRequestJson(out.request))
            assertTrue(response.toString(), response.optBoolean("success"))
            assertEquals(CalcResultPresentation.DAMAGING_TURN_PREVIEW_SCOPE, response.getString("damageScope"))
            return (0 until response.getJSONArray("damage").length()).map { response.getJSONArray("damage").getInt(it) }
        }
        val plain = damage("Bounce")
        val orb = damage("Bounce", item = "Life Orb")
        val sheer = damage("Bounce", "Sheer Force")
        val sheerOrb = damage("Bounce", "Sheer Force", "Life Orb")
        assertEquals(49, plain[8])
        assertEquals(64, orb[8])
        assertEquals(64, sheer[8])
        assertEquals(83, sheerOrb[8])
        assertTrue(sheer.max() > plain.max())
        assertTrue(sheerOrb.max() > sheer.max())
        assertEquals(damage("Fly", ability = "Insomnia"), damage("Fly", ability = "Sheer Force"))
    }

    @Test fun `source contact flags feed supported Tough Claws and Fluffy authority`() {
        fun rolls(outcome: CalcRequestOutcome): List<Int> {
            val ready = outcome as? CalcRequestOutcome.Ready ?: error("contact case refused: $outcome")
            val response = production(buildCalcRequestJson(ready.request))
            assertTrue(response.toString(), response.optBoolean("success"))
            return (0 until response.getJSONArray("damage").length()).map { response.getJSONArray("damage").getInt(it) }
        }
        val neutralRolls = rolls(build("Fly"))
        val claws = rolls(build("Fly", neutral(true, "Tough Claws")))
        val fluffy = rolls(build("Fly", d = neutral(false, "Fluffy")))
        assertTrue(claws.last() > neutralRolls.last())
        assertTrue(fluffy.last() < neutralRolls.last())
        val longReach = build("Fly", neutral(true, "Long Reach"), neutral(false, "Fluffy")) as? CalcRequestOutcome.Refused
            ?: error("existing Long Reach execution blocker was relaxed")
        assertTrue(longReach.toString(), CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED in longReach.verdict.limitations)
    }

    @Test fun `QuickJS rejects forged family descriptor and caller phase claims`() {
        val ready = build("Fly") as CalcRequestOutcome.Ready
        val original = buildCalcRequestJson(ready.request)
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONObject("move").put("hnsSourcePower", 70) },
            { it.getJSONObject("move").put("hnsSourceType", "TYPE_GROUND") },
            { it.getJSONObject("move").put("hnsGravityBanned", false) },
            { it.getJSONObject("move").put("hnsPowerHerbHarmless", true) },
            { it.getJSONObject("attacker").put("hnsAttackerSemiInvulnerableState", HnsGroupDLayout.STATE_ON_AIR) },
            { it.getJSONObject("attacker").put("hnsMultipleTurns", true) }
        )
        for (change in changes) {
            val forged = JSONObject(original).also(change)
            val result = production(forged.toString())
            assertFalse(result.toString(), result.optBoolean("success"))
        }
    }
}
