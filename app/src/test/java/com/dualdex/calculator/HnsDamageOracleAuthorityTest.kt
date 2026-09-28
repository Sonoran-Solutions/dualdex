package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.pokemon.hns.Hns205ItemCatalogue
import com.dualdex.pokemon.hns.HnsMoveMechanicsCategory
import com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry
import com.dualdex.pokemon.hns.HnsOptionStyle
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomHackProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue #90: the differential damage oracle records, for every scenario, the identity and damage
 * authority the pinned H&S battle engine actually used (species ID, battle types, base stats, move
 * ID/type/power/category, ability and item IDs, badge-boost verdicts). The host differential test
 * feeds those observed operands to the QuickJS engine; this test closes the other half of the loop by
 * proving that DualDex's own production sources for the same operands agree with the engine:
 *
 *  - [HeartAndSoul205DataPack] species/move identity and the [CalcDataOverrides] species and move
 *    overrides production sends (including the Fairy-off typings and move retypes);
 *  - [CalcDataOverrides.resolveHnsMoveCategory] under both option styles;
 *  - the ability names and [Hns205ItemCatalogue] identities;
 *  - the badge-flag -> boosted-stat contract the native reader implements
 *    (`pokemon_read_hns_badge_state_gba`: badge 1 Atk, 6 Def, 7 SpA and SpD; player side only).
 *
 * It reads only the committed corpus: no ROM, no upstream checkout.
 */
class HnsDamageOracleAuthorityTest {

    private fun repoFile(relative: String): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
            ?: throw AssertionError("Unable to locate $relative from ${System.getProperty("user.dir")}")

    private val heartAndSoul: RomHackProfile by lazy {
        ProfileLoader.parseProfile(repoFile("app/src/main/assets/profiles/heart_and_soul.json").readText())
    }

    private val entries: List<JSONObject> by lazy {
        val corpus = JSONObject(repoFile("tools/hns-damage-oracle/corpus.json").readText())
        assertEquals(
            "corpus must come from the pinned H&S commit",
            "1f42b74dff0e9fe942419845d040663dd829a973",
            corpus.getJSONObject("provenance").getJSONObject("hnsUpstream").getString("commit")
        )
        val array = corpus.getJSONArray("entries")
        (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun checkBattler(id: String, scenario: JSONObject, observed: JSONObject, fairy: Boolean) {
        val label = scenario.getString("speciesLabel")
        val species = HeartAndSoul205DataPack.getSpeciesByName(label)
            ?: throw AssertionError("$id: data pack has no species named $label")
        assertEquals("$id: $label species ID", observed.getInt("speciesId"), species.id)
        val override = CalcDataOverrides.buildSpeciesOverride(label, HeartAndSoul205DataPack, fairy)
            ?: throw AssertionError("$id: no production species override for $label")
        assertEquals("$id: $label battle types (fairy=$fairy)", observed.getJSONArray("types").strings(), override.types)
        val base = observed.getJSONObject("baseStats")
        assertEquals("$id: $label base HP", base.getInt("hp"), override.baseStats.hp)
        assertEquals("$id: $label base Atk", base.getInt("attack"), override.baseStats.atk)
        assertEquals("$id: $label base Def", base.getInt("defense"), override.baseStats.def)
        assertEquals("$id: $label base SpA", base.getInt("spAttack"), override.baseStats.spa)
        assertEquals("$id: $label base SpD", base.getInt("spDefense"), override.baseStats.spd)
        assertEquals("$id: $label base Spe", base.getInt("speed"), override.baseStats.spe)

        // The pinned ability table spells names in upper case (e.g. INSOMNIA).
        val ability = HeartAndSoul205DataPack.getAbility(observed.getInt("abilityId"))
        assertTrue(
            "$id: ability ${observed.getInt("abilityId")} is $ability, expected ${scenario.getString("abilityLabel")}",
            ability is com.dualdex.pokemon.DeclaredAbility.Declared &&
                ability.name.equals(scenario.getString("abilityLabel"), ignoreCase = true)
        )
        val itemId = observed.getInt("itemId")
        if (scenario.getString("item") == "ITEM_NONE") {
            assertEquals("$id: no held item", 0, itemId)
        } else {
            val item = Hns205ItemCatalogue.get(itemId)
                ?: throw AssertionError("$id: item $itemId missing from the H&S item catalogue")
            assertEquals("$id: item symbol", scenario.getString("item"), item.canonicalSymbol)
            // The pinned item table spells names in upper case (e.g. BLACK BELT).
            assertTrue(
                "$id: item name ${item.sourceName} != ${scenario.getString("itemLabel")}",
                item.sourceName.equals(scenario.getString("itemLabel"), ignoreCase = true)
            )
        }
    }

    @Test
    fun productionAuthorityAgreesWithThePinnedEngineForEveryScenario() {
        assertTrue("the corpus must hold at least ~1000 scenarios", entries.size >= 1000)
        for (entry in entries) {
            val scenario = entry.getJSONObject("scenario")
            val observed = entry.getJSONObject("observed")
            val id = scenario.getString("id")
            val rules = scenario.getJSONObject("rules")
            val fairy = rules.getBoolean("fairyTypes")
            checkBattler(id, scenario.getJSONObject("attacker"), observed.getJSONObject("attacker"), fairy)
            checkBattler(id, scenario.getJSONObject("defender"), observed.getJSONObject("defender"), fairy)

            val moveLabel = scenario.getJSONObject("move").getString("label")
            val oMove = observed.getJSONObject("move")
            val move = HeartAndSoul205DataPack.getMoveByName(moveLabel)
                ?: throw AssertionError("$id: data pack has no move named $moveLabel")
            assertEquals("$id: $moveLabel move ID", oMove.getInt("id"), move.id)
            val moveOverride = CalcDataOverrides.buildMoveOverride(moveLabel, HeartAndSoul205DataPack, fairy)
                ?: throw AssertionError("$id: no production move override for $moveLabel")
            assertEquals("$id: $moveLabel power", oMove.getInt("power"), moveOverride.basePower)

            val ordinaryMove = HnsMoveMechanicsRegistry.classify(move.id).category ==
                HnsMoveMechanicsCategory.ORDINARY_PROVEN_EQUIVALENT
            val sourceType = moveOverride.type
            val attackerAbilityId = observed.getJSONObject("attacker").getInt("abilityId")
            val flags = oMove.getJSONArray("flags").strings().toSet()
            val expectedEffectiveType = when (attackerAbilityId) {
                96 -> if (ordinaryMove) "Normal" else sourceType
                174 -> if (ordinaryMove && sourceType == "Normal") "Ice" else sourceType
                182 -> if (ordinaryMove && sourceType == "Normal") "Fairy" else sourceType
                184 -> if (ordinaryMove && sourceType == "Normal") "Flying" else sourceType
                204 -> if (ordinaryMove && "soundMove" in flags) "Water" else sourceType
                206 -> if (ordinaryMove && sourceType == "Normal") "Electric" else sourceType
                else -> sourceType
            }
            assertEquals("$id: $moveLabel effective type (fairy=$fairy)",
                expectedEffectiveType, oMove.getString("type"))
            val expectedAteBoost = ordinaryMove && when (attackerAbilityId) {
                96 -> true
                174, 182, 184, 206 -> sourceType == "Normal"
                else -> false
            }
            assertEquals("$id: $moveLabel ateBoost", expectedAteBoost, oMove.getBoolean("ateBoost"))

            val style = when (rules.getString("optionStyle")) {
                "perMoveSplit" -> HnsOptionStyle.PER_MOVE_SPLIT
                "typeBased" -> HnsOptionStyle.TYPE_BASED
                else -> throw AssertionError("$id: unknown option style")
            }
            val expected = when (style) {
                HnsOptionStyle.PER_MOVE_SPLIT -> {
                    val perMoveCategory = CalcDataOverrides.resolveHnsMoveCategory(
                        moveLabel, heartAndSoul,
                        CalcHnsRuntimeRules(optionStyle = style, fairyTypesEnabled = fairy)
                    )
                    assertEquals("$id: $moveLabel PER_MOVE_SPLIT retains its own category",
                        move.category, perMoveCategory)
                    perMoveCategory
                }
                HnsOptionStyle.TYPE_BASED -> HnsMoveAuthority.categoryForTypeName(expectedEffectiveType)
                else -> null
            }
            assertEquals("$id: $moveLabel pinned category under $style",
                expected?.displayName?.lowercase(), oMove.getString("category"))
        }
    }

    @Test
    fun badgeBoostVerdictsMatchTheNativeReaderContract() {
        var checked = 0
        for (entry in entries) {
            val scenario = entry.getJSONObject("scenario")
            val observed = entry.getJSONObject("observed")
            val badges = scenario.getJSONArray("badges").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }
            val attackerIsPlayer = scenario.getString("attackerSide") == "player"
            for ((role, isPlayer) in listOf("attacker" to attackerIsPlayer, "defender" to !attackerIsPlayer)) {
                val boosts = observed.getJSONObject(role).getJSONObject("badgeBoosts")
                val id = scenario.getString("id")
                assertEquals("$id $role Atk badge", isPlayer && 1 in badges, boosts.getBoolean("attack"))
                assertEquals("$id $role Def badge", isPlayer && 6 in badges, boosts.getBoolean("defense"))
                assertEquals("$id $role SpA badge", isPlayer && 7 in badges, boosts.getBoolean("spAttack"))
                assertEquals("$id $role SpD badge", isPlayer && 7 in badges, boosts.getBoolean("spDefense"))
                checked++
            }
        }
        assertTrue(checked >= 2000)
    }
}
