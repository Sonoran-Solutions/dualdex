package com.dualdex.calculator

import com.dualdex.pokemon.Gen3VanillaDataPack
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.romhack.RomHackProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcDataOverridesTest {

    private val hnsProfile = RomHackProfile(
        id = "pokemon_heart_and_soul",
        name = "Pokemon Heart & Soul 2.0.5",
        baseGame = "Emerald",
        gameId = 3,
        engine = "pokeemerald-expansion",
        gameDataPackId = "hns_2_0_5",
        hasPhysSpecSplit = true
    )

    private val fireRedProfile = RomHackProfile(
        id = "firered_vanilla",
        name = "Pokemon FireRed",
        baseGame = "FireRed",
        gameId = 2,
        engine = "Vanilla",
        gameDataPackId = "gen3_vanilla",
        hasPhysSpecSplit = false
    )

    @Test
    fun `ordinary species resolves authoritative base stats and types`() {
        val override = CalcDataOverrides.buildSpeciesOverride("Bulbasaur", HeartAndSoul205DataPack)
        assertNotNull(override)
        assertEquals(StatBlock(hp = 45, atk = 49, def = 49, spa = 65, spd = 65, spe = 45), override!!.baseStats)
        assertEquals(listOf("Grass", "Poison"), override.types)
    }

    @Test
    fun `species with updated stats emits authoritative HnS stats rather than Gen 3 values`() {
        // In Gen 3, Arbok had base Atk 85. In H&S 2.0.5, Arbok has base Atk 95.
        val arbok = CalcDataOverrides.buildSpeciesOverride("Arbok", HeartAndSoul205DataPack)
        assertNotNull(arbok)
        assertEquals(95, arbok!!.baseStats.atk)
        assertEquals(StatBlock(hp = 60, atk = 95, def = 69, spa = 65, spd = 79, spe = 80), arbok.baseStats)
        assertEquals(listOf("Poison"), arbok.types)

        // In Gen 3, Butterfree had base SpA 80. In H&S 2.0.5, Butterfree has base SpA 90.
        val butterfree = CalcDataOverrides.buildSpeciesOverride("Butterfree", HeartAndSoul205DataPack)
        assertNotNull(butterfree)
        assertEquals(90, butterfree!!.baseStats.spa)
        assertEquals(StatBlock(hp = 60, atk = 45, def = 50, spa = 90, spd = 80, spe = 70), butterfree.baseStats)
        assertEquals(listOf("Bug", "Flying"), butterfree.types)

        // In Gen 3, Pidgeot had base Spe 91. In H&S 2.0.5, Pidgeot has base Spe 101.
        val pidgeot = CalcDataOverrides.buildSpeciesOverride("Pidgeot", HeartAndSoul205DataPack)
        assertNotNull(pidgeot)
        assertEquals(101, pidgeot!!.baseStats.spe)
    }

    @Test
    fun `ambiguous multi-form species fail closed and return null`() {
        // Multi-form names with differing stats/types must return null rather than guessing a form
        assertNull(CalcDataOverrides.buildSpeciesOverride("Deoxys", HeartAndSoul205DataPack))
        assertNull(CalcDataOverrides.buildSpeciesOverride("Castform", HeartAndSoul205DataPack))
        assertNull(CalcDataOverrides.buildSpeciesOverride("Eevee", HeartAndSoul205DataPack))
        assertNull(CalcDataOverrides.buildSpeciesOverride("Aegislash", HeartAndSoul205DataPack))
    }

    @Test
    fun `missing species returns null without generic fallback`() {
        assertNull(CalcDataOverrides.buildSpeciesOverride("TotallyFakePokemonName", HeartAndSoul205DataPack))
    }

    @Test
    fun `ordinary move resolves authoritative base power and category`() {
        val cut = CalcDataOverrides.buildMoveOverride("Cut", HeartAndSoul205DataPack)
        assertNotNull(cut)
        assertEquals(50, cut!!.basePower)
        assertEquals("Normal", cut.type)
        assertEquals("Physical", cut.category)
    }

    @Test
    fun `move with updated power emits authoritative HnS power`() {
        // Tackle: 35 in Gen 3, 40 in H&S 2.0.5
        val tackle = CalcDataOverrides.buildMoveOverride("Tackle", HeartAndSoul205DataPack)
        assertNotNull(tackle)
        assertEquals(40, tackle!!.basePower)
        assertEquals("Normal", tackle.type)
        assertEquals("Physical", tackle.category)

        // Vine Whip: 35 in Gen 3, 45 in H&S 2.0.5
        val vineWhip = CalcDataOverrides.buildMoveOverride("Vine Whip", HeartAndSoul205DataPack)
        assertNotNull(vineWhip)
        assertEquals(45, vineWhip!!.basePower)
        assertEquals("Grass", vineWhip.type)
    }

    @Test
    fun `modern HnS move resolves correctly`() {
        val bitterBlade = CalcDataOverrides.buildMoveOverride("Bitter Blade", HeartAndSoul205DataPack)
        assertNotNull(bitterBlade)
        assertEquals(90, bitterBlade!!.basePower)
        assertEquals("Fire", bitterBlade.type)
        assertEquals("Physical", bitterBlade.category)

        val makeItRain = CalcDataOverrides.buildMoveOverride("Make It Rain", HeartAndSoul205DataPack)
        assertNotNull(makeItRain)
        assertEquals(120, makeItRain!!.basePower)
        assertEquals("Steel", makeItRain.type)
        assertEquals("Special", makeItRain.category)
    }

    @Test
    fun `missing move returns null without generic fallback`() {
        assertNull(CalcDataOverrides.buildMoveOverride("NonExistentMove123", HeartAndSoul205DataPack))
    }

    @Test
    fun `type mapping preserves canonical names and does not map TYPE_NONE to Normal`() {
        // Monotype Grass: type2 must not become Normal
        val chikorita = CalcDataOverrides.buildSpeciesOverride("Chikorita", HeartAndSoul205DataPack)
        assertNotNull(chikorita)
        assertEquals(listOf("Grass"), chikorita!!.types)

        // Monotype Fairy: Clefairy in H&S is pure Fairy
        val clefairy = CalcDataOverrides.buildSpeciesOverride("Clefairy", HeartAndSoul205DataPack)
        assertNotNull(clefairy)
        assertEquals(listOf("Fairy"), clefairy!!.types)

        // Dual type with Fairy: Togetic is Fairy/Flying
        val togetic = CalcDataOverrides.buildSpeciesOverride("Togetic", HeartAndSoul205DataPack)
        assertNotNull(togetic)
        assertEquals(listOf("Fairy", "Flying"), togetic!!.types)
    }

    @Test
    fun `vanilla data pack produces null overrides preserving verified Gen 3 behavior`() {
        assertNull(CalcDataOverrides.buildSpeciesOverride("Bulbasaur", Gen3VanillaDataPack))
        assertNull(CalcDataOverrides.buildSpeciesOverride("Arbok", Gen3VanillaDataPack))
        assertNull(CalcDataOverrides.buildMoveOverride("Tackle", Gen3VanillaDataPack))
        assertNull(CalcDataOverrides.buildMoveOverride("Sludge Bomb", Gen3VanillaDataPack))
    }

    @Test
    fun `enrichRequest attaches authoritative overrides for HnS profile`() {
        val req = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Arbok"),
            defender = CalcPokemonInput(species = "Swampert"),
            move = CalcMoveInput(name = "Sludge Bomb")
        )

        val enriched = CalcDataOverrides.enrichRequest(hnsProfile, req)
        assertNotNull(enriched.attackerOverride)
        assertEquals(95, enriched.attackerOverride!!.baseStats.atk)
        assertEquals(listOf("Poison"), enriched.attackerOverride!!.types)

        assertNotNull(enriched.defenderOverride)
        assertEquals(listOf("Water", "Ground"), enriched.defenderOverride!!.types)

        assertNotNull(enriched.moveOverride)
        assertEquals(90, enriched.moveOverride!!.basePower)
        assertEquals("Poison", enriched.moveOverride!!.type)
        assertEquals("Special", enriched.moveOverride!!.category)
    }

    @Test
    fun `enrichRequest leaves overrides null for vanilla profile`() {
        val req = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Arbok"),
            defender = CalcPokemonInput(species = "Swampert"),
            move = CalcMoveInput(name = "Sludge Bomb")
        )

        val enriched = CalcDataOverrides.enrichRequest(fireRedProfile, req)
        assertNull(enriched.attackerOverride)
        assertNull(enriched.defenderOverride)
        assertNull(enriched.moveOverride)
    }

    @Test
    fun `enrichRequest preserves explicitly supplied overrides`() {
        val customOverride = CalcSpeciesOverride(
            baseStats = StatBlock(hp = 100, atk = 100, def = 100, spa = 100, spd = 100, spe = 100),
            types = listOf("Normal")
        )
        val req = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Arbok"),
            defender = CalcPokemonInput(species = "Swampert"),
            move = CalcMoveInput(name = "Sludge Bomb"),
            attackerOverride = customOverride
        )

        val enriched = CalcDataOverrides.enrichRequest(hnsProfile, req)
        assertEquals(customOverride, enriched.attackerOverride)
    }

    @Test
    fun `buildCalcRequestJson serializes overrides only when present`() {
        val reqWithOverrides = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Arbok"),
            defender = CalcPokemonInput(species = "Swampert"),
            move = CalcMoveInput(name = "Tackle"),
            attackerOverride = CalcSpeciesOverride(
                baseStats = StatBlock(hp = 60, atk = 95, def = 69, spa = 65, spd = 79, spe = 80),
                types = listOf("Poison")
            ),
            moveOverride = CalcMoveOverride(basePower = 40, type = "Normal", category = "Physical")
        )

        val jsonWithOverrides = JSONObject(buildCalcRequestJson(reqWithOverrides))
        val atkObj = jsonWithOverrides.getJSONObject("attacker")
        assertTrue(atkObj.has("overrides"))
        val atkOverrides = atkObj.getJSONObject("overrides")
        assertEquals(95, atkOverrides.getJSONObject("baseStats").getInt("atk"))
        assertEquals("Poison", atkOverrides.getJSONArray("types").getString(0))

        val moveObj = jsonWithOverrides.getJSONObject("move")
        assertTrue(moveObj.has("overrides"))
        val moveOverrides = moveObj.getJSONObject("overrides")
        assertEquals(40, moveOverrides.getInt("basePower"))
        assertEquals("Normal", moveOverrides.getString("type"))
        assertEquals("Physical", moveOverrides.getString("category"))

        val defObj = jsonWithOverrides.getJSONObject("defender")
        assertFalse(defObj.has("overrides"))

        val reqVanilla = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Arbok"),
            defender = CalcPokemonInput(species = "Swampert"),
            move = CalcMoveInput(name = "Tackle")
        )
        val jsonVanilla = JSONObject(buildCalcRequestJson(reqVanilla))
        assertFalse(jsonVanilla.getJSONObject("attacker").has("overrides"))
        assertFalse(jsonVanilla.getJSONObject("defender").has("overrides"))
        assertFalse(jsonVanilla.getJSONObject("move").has("overrides"))
    }
}
