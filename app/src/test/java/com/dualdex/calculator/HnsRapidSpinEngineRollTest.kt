package com.dualdex.calculator

import com.dualdex.calculator.census.HnsCalcCensusBaseline as Baseline
import com.dualdex.pokemon.hns.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Every original-engine Rapid Spin damage vector (tools/hns-damage-oracle/rapid-spin-evidence.json)
 * is replayed through the shipped production boundary and bundle; all sixteen rolls must match exactly.
 */
class HnsRapidSpinEngineRollTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "ci.sh").isFile }

    /** `SPECIES_MUK` / `ABILITY_TOUGH_CLAWS` / `ITEM_LIFE_ORB` -> `Muk` / `Tough Claws` / `Life Orb`. */
    private fun displayName(cIdentifier: String): String? {
        val words = cIdentifier.substringAfter('_').split('_').filter { it.isNotEmpty() }
        if (words.isEmpty() || cIdentifier == "ITEM_NONE") return null
        return words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }

    // Pinned Release-v2.0.5 types for the species used by the evidence vectors.
    private val SPECIES_TYPES = mapOf("Muk" to listOf("Poison"), "Greedent" to listOf("Normal"),
        "Blastoise" to listOf("Water"), "Gengar" to listOf("Ghost", "Poison"))

    private fun observation(attacker: Boolean, species: String, ability: String, item: String?, stat: Int, hp: Int) =
        Baseline.observation(
            if (attacker) Baseline.Participant.ATTACKER else Baseline.Participant.DEFENDER,
            if (attacker) 0 else 1,
            HeartAndSoul205DataPack.getSpeciesByName(species)?.id ?: error("$species missing"),
            species,
            SPECIES_TYPES[species] ?: error("$species types not pinned in this test"),
            checkNotNull(HnsAbilityRegistry.classify(ability).abilityId),
            item?.let(HnsItemRegistry::resolveIdByName) ?: 0,
            ability,
            Hns205ItemCatalogue.get(item?.let(HnsItemRegistry::resolveIdByName) ?: 0),
            2,
        ).let { o ->
            o.copy(state = o.state.copy(
                rawAttack = stat, rawDefense = stat, rawSpeed = 100,
                hp = hp, maxHp = hp, contactReactionStateObserved = true, protectedMethod = 0))
        }

    private fun production(json: String): JSONObject {
        val p = ProcessBuilder("node", File(root, "tools/calc-bundler/run_production_request.js").path,
            File(root, "app/src/main/assets/calc_bundle.js").path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(json) }
        val result = JSONObject(p.inputStream.bufferedReader().readText())
        assertEquals(0, p.waitFor())
        return result
    }

    @Test fun `every engine Rapid Spin damage vector matches the shipped production sixteen rolls`() {
        val doc = JSONObject(File(root, "tools/hns-damage-oracle/rapid-spin-evidence.json").readText())
        val vectors = doc.getJSONArray("damageVectors")
        assertEquals(10, vectors.length())
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val id = v.getString("id")
            val a = v.getJSONObject("attacker")
            val d = v.getJSONObject("defender")
            val attackerSpecies = displayName(a.getString("species"))!!
            val defenderSpecies = displayName(d.getString("species"))!!
            val request = DamageCalculationRequest(
                attacker = CalcPokemonInput(species = attackerSpecies, level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 0),
                defender = CalcPokemonInput(species = defenderSpecies, level = 50, origin = CalcInputOrigin.LIVE_READ, partySlot = 1),
                move = CalcMoveInput("Rapid Spin", isCrit = v.getBoolean("critical")),
                field = CalcFieldInput(gameType = "Singles"))
            val attackerObs = observation(true, attackerSpecies, displayName(a.getString("ability"))!!,
                displayName(a.getString("item")), a.getInt("attack"), 60000)
            // Reflect is read from the defender's observed gSideStatuses word (SIDE_STATUS_REFLECT = bit 0).
            val defenderObs = observation(false, defenderSpecies, displayName(d.getString("ability"))!!, null,
                d.getInt("defense"), 60000).let { o ->
                o.copy(state = o.state.copy(sideStatusesReadable = true,
                    sideStatuses = if (v.getBoolean("reflect")) HnsBattlerRuntimeStateIds.SIDE_STATUS_REFLECT else 0))
            }
            val outcome = CalcRequestBoundary.build(Baseline.profile, Baseline.trust, request, Baseline.challengeSettings,
                attackerObs, defenderObs, activeBattle = true)
            val ready = outcome as? CalcRequestOutcome.Ready ?: error("$id refused: $outcome")
            val response = production(buildCalcRequestJson(ready.request))
            assertTrue("$id: $response", response.optBoolean("success"))
            val rolls = v.getJSONArray("rolls")
            val production = response.getJSONArray("damage")
            assertEquals("$id roll count", 16, production.length())
            // Pinned roll order (tools/hns-damage-oracle/corpus.json rollOrder): WITH_RNG(RNG_DAMAGE_MODIFIER, i)
            // yields the production roll at index 15 - i; the shipped calculator lists rolls ascending (85%..100%).
            for (i in 0 until 16) {
                assertEquals("$id engine roll $i maps to production roll ${15 - i}", rolls.getInt(i), production.getInt(15 - i))
            }
        }
    }
}
