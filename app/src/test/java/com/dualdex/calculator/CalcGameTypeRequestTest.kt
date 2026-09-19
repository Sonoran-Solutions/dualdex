package com.dualdex.calculator

import com.dualdex.battle.buildDamageRequest
import com.dualdex.pokemon.ParsedPokemon
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the battle-format (`field.gameType`) input contract.
 *
 * The embedded `@smogon/calc` engine compares `field.gameType`
 * case-sensitively against `'Singles'`/`'Doubles'`. DualDex used to default to
 * the lowercase `"singles"`, which selected the doubles damage path and halved
 * Gen III spread moves such as Rock Slide in single battles (issue #29). These
 * tests pin the canonical value the application actually serialises, not a
 * handcrafted JSON document, so the default cannot silently regress again.
 */
class CalcGameTypeRequestTest {

    private fun attacker(): ParsedPokemon = ParsedPokemon(
        isValid = true,
        isEmpty = false,
        pid = 123456L,
        tid = 1000,
        sid = 2000,
        nickname = "Machamp",
        otName = "Red",
        species = 68, // Machamp
        heldItem = 0,
        level = 50,
        nature = 0,
        natureName = "Hardy",
        isShiny = false,
        abilitySlot = 0,
        isEgg = false,
        friendship = 255,
        experience = 100000L,
        hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(1, 0, 0, 0),
        pp = intArrayOf(30, 0, 0, 0),
        currentHp = 150,
        maxHp = 150,
        attack = 100,
        defense = 100,
        speed = 100,
        spAttack = 100,
        spDefense = 100,
        statusCondition = 0L
    )

    @Test
    fun `calc field default game type is canonical Singles`() {
        assertEquals(CalcGameTypes.SINGLES, CalcFieldInput().gameType)
        assertEquals("Singles", CalcGameTypes.SINGLES)
        assertEquals("Doubles", CalcGameTypes.DOUBLES)
    }

    @Test
    fun `battle console damage request uses canonical singles`() {
        val request = buildDamageRequest(
            attacker = attacker(),
            defender = null,
            moveName = "Rock Slide",
            natureName = "Hardy"
        )

        assertEquals(
            "buildDamageRequest must send the case-sensitive 'Singles' identifier",
            CalcGameTypes.SINGLES,
            request.field.gameType
        )
    }

    @Test
    fun `serialised request carries the canonical game type`() {
        val request = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Machamp", level = 50),
            defender = CalcPokemonInput(species = "Snorlax", level = 50),
            move = CalcMoveInput(name = "Rock Slide")
        )

        val serialised = buildCalcRequestJson(request)
        val field = JSONObject(serialised).getJSONObject("field")

        assertEquals("Singles", field.getString("gameType"))
        assertFalse(
            "serialised request must not contain the legacy lowercase identifier",
            serialised.contains("\"gameType\":\"singles\"")
        )
    }

    @Test
    fun `serialised field conditions are preserved`() {
        val request = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Machamp", level = 50),
            defender = CalcPokemonInput(species = "Snorlax", level = 50),
            move = CalcMoveInput(name = "Strength"),
            field = CalcFieldInput(
                gameType = CalcGameTypes.SINGLES,
                weather = "Rain",
                defenderSide = SideConditions(isReflect = true)
            )
        )

        val field = JSONObject(buildCalcRequestJson(request)).getJSONObject("field")

        assertEquals("Rain", field.getString("weather"))
        assertEquals(true, field.getJSONObject("defenderSide").getBoolean("isReflect"))
        assertFalse(field.getJSONObject("defenderSide").optBoolean("isLightScreen", false))
    }
}
