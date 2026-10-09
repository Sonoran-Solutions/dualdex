package com.dualdex.calculator

import com.dualdex.battle.*
import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Production damage parity for Belch against the pinned original engine.
 *
 * The engine artifact (`tools/hns-damage-oracle/belch-evidence.json`) is produced by
 * `belch_evidence.py run`, which plays real Stuff Cheeks consumption on the pinned engine before each
 * measured Belch hit. This test rebuilds every vector through the production boundary with the
 * engine-observed stats and `ateBerry=true`, then requires the shipped bundle's sixteen rolls to equal
 * the engine's sixteen rolls exactly.
 */
class HnsBelchEngineDifferentialTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "ci.sh").isFile }
    private val artifact by lazy { JSONObject(File(root, "tools/hns-damage-oracle/belch-evidence.json").readText()) }

    private val speciesNames = mapOf(
        "SPECIES_GREEDENT" to "Greedent", "SPECIES_MUK" to "Muk", "SPECIES_BLASTOISE" to "Blastoise",
        "SPECIES_BELLOSSOM" to "Bellossom", "SPECIES_KOFFING" to "Koffing", "SPECIES_STEELIX" to "Steelix")
    private val speciesTypes = mapOf(
        "Greedent" to listOf("Normal"), "Muk" to listOf("Poison"), "Blastoise" to listOf("Water"),
        "Bellossom" to listOf("Grass"), "Koffing" to listOf("Poison"), "Steelix" to listOf("Steel", "Ground"))
    private val abilityNames = mapOf("ABILITY_INSOMNIA" to "Insomnia", "ABILITY_FILTER" to "Filter")

    private fun observation(participant: Baseline.Participant, slot: Int, species: String, ability: String,
                            rawStat: Int, ateBerry: Boolean?): BattlerRuntimeObservation {
        val name = speciesNames.getValue(species)
        val base = Baseline.observation(participant, slot, HeartAndSoul205DataPack.getSpeciesByName(name)?.id
            ?: error("$name missing from pinned data pack"), name, speciesTypes.getValue(name),
            checkNotNull(HnsAbilityRegistry.classify(abilityNames.getValue(ability)).abilityId),
            0, abilityNames.getValue(ability), null, 2)
        return base.copy(state = base.state.copy(
            hp = 60000, maxHp = 60000, contactReactionStateObserved = true, protectedMethod = 0,
            ateBerry = ateBerry,
            rawSpAttack = if (participant == Baseline.Participant.ATTACKER) rawStat else base.state.rawSpAttack,
            rawSpDefense = if (participant == Baseline.Participant.DEFENDER) rawStat else base.state.rawSpDefense))
    }

    private fun production(json: String): JSONObject {
        val p = ProcessBuilder("node", File(root, "tools/calc-bundler/run_production_request.js").path,
            File(root, "app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(json) }
        val result = JSONObject(p.inputStream.bufferedReader().readText())
        assertEquals(0, p.waitFor())
        return result
    }

    private fun ints(array: JSONArray): List<Int> = (0 until array.length()).map { array.getInt(it) }

    @Test fun `engine lifecycle matches the source-required Belch selection and Berry-state sequence`() {
        val lifecycle = artifact.getJSONArray("lifecycle")
        val observed = (0 until lifecycle.length()).associate { i ->
            val row = lifecycle.getJSONObject(i)
            row.getString("id") to ints(row.getJSONArray("observed"))
        }
        // ITEM_ORAN_BERRY = 520. Each entry: ateBerry flag, then the item still held (or 0 when consumed).
        assertEquals(mapOf(
            "fresh-refused" to listOf(0, 520),
            "consumed-executes" to listOf(1, 0),
            "switch-slot0" to listOf(1, 0),
            "second-battle-fresh-refused" to listOf(0),
            "recycle-keeps-flag" to listOf(1, 520)), observed)
    }

    @Test fun `every engine damage vector reproduces exactly through the shipped production bundle`() {
        val vectors = artifact.getJSONArray("damageVectors")
        assertEquals(8, vectors.length())
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val id = v.getString("id")
            val a = v.getJSONObject("attacker")
            val d = v.getJSONObject("defender")
            val request = DamageCalculationRequest(
                attacker = CalcPokemonInput(speciesNames.getValue(a.getString("species")), 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
                defender = CalcPokemonInput(speciesNames.getValue(d.getString("species")), 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
                move = CalcMoveInput("Belch", isCrit = v.getBoolean("critical")),
                field = CalcFieldInput(gameType = "Singles"))
            val outcome = CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request, Baseline.challengeSettings,
                observation(Baseline.Participant.ATTACKER, 0, a.getString("species"), a.getString("ability"), a.getInt("observed"), ateBerry = true),
                observation(Baseline.Participant.DEFENDER, 1, d.getString("species"), d.getString("ability"), d.getInt("observed"), ateBerry = null),
                activeBattle = true)
            val ready = outcome as? CalcRequestOutcome.Ready ?: error("$id refused: $outcome")
            val response = production(buildCalcRequestJson(ready.request))
            assertTrue("$id: $response", response.optBoolean("success"))
            // Pinned src/battle_util.c:7777 applies `DMG_ROLL_PERCENT_HI - RandomUniform(RNG_DAMAGE_MODIFIER, ...)`,
            // so engine roll i is the (100 - i)% factor. The production array is ordered 85%..100%, so
            // engine roll i must equal production index 15 - i.
            assertEquals("$id: production rolls must equal the engine rolls",
                ints(v.getJSONArray("rolls")), ints(response.getJSONArray("damage")).reversed())
            if (v.getBoolean("immune")) assertTrue("$id: immune target must be zero damage", ints(response.getJSONArray("damage")).all { it == 0 })
        }
    }
}
