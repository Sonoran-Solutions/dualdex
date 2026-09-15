package com.dualdex.pokemon

import org.junit.Assert.*
import org.junit.Test

class PokemonMechanicsTest {

    @Test
    fun testNatureTableAll25Natures() {
        val all = NatureTable.getAll()
        assertEquals(25, all.size)

        val adamant = NatureTable.getByName("Adamant")
        assertEquals(StatType.ATTACK, adamant.increasedStat)
        assertEquals(StatType.SP_ATTACK, adamant.decreasedStat)
        assertEquals(1.1f, adamant.getModifier(StatType.ATTACK), 0.001f)
        assertEquals(0.9f, adamant.getModifier(StatType.SP_ATTACK), 0.001f)
        assertEquals(1.0f, adamant.getModifier(StatType.DEFENSE), 0.001f)
        assertTrue(adamant.formattedDescription.contains("+Atk"))

        val modest = NatureTable.get(15) // Modest
        assertEquals("Modest", modest.name)
        assertEquals(StatType.SP_ATTACK, modest.increasedStat)
        assertEquals(StatType.ATTACK, modest.decreasedStat)

        val hardy = NatureTable.get(0) // Hardy
        assertTrue(hardy.isNeutral)
        assertEquals(1.0f, hardy.getModifier(StatType.ATTACK), 0.001f)
    }

    @Test
    fun testTypeChartStandardEffectiveness() {
        assertEquals(2.0f, TypeChart.getEffectiveness(PokemonType.WATER, PokemonType.FIRE), 0.01f)
        assertEquals(0.5f, TypeChart.getEffectiveness(PokemonType.FIRE, PokemonType.WATER), 0.01f)
        assertEquals(0.0f, TypeChart.getEffectiveness(PokemonType.ELECTRIC, PokemonType.GROUND), 0.01f)
        assertEquals(0.0f, TypeChart.getEffectiveness(PokemonType.NORMAL, PokemonType.GHOST), 0.01f)
        assertEquals(2.0f, TypeChart.getEffectiveness(PokemonType.FAIRY, PokemonType.DRAGON), 0.01f)
    }

    @Test
    fun testDualTypeDefensiveProfile() {
        // Charizard: Fire / Flying
        val charizardDef = TypeChart.getDefenseProfile(PokemonType.FIRE, PokemonType.FLYING)
        // Rock should be 4x weak (2.0 * 2.0)
        assertTrue(charizardDef.weaknesses4x.contains(PokemonType.ROCK))
        // Water and Electric should be 2x weak
        assertTrue(charizardDef.weaknesses2x.contains(PokemonType.WATER))
        assertTrue(charizardDef.weaknesses2x.contains(PokemonType.ELECTRIC))
        // Ground should be immune (Flying immunity 0x)
        assertTrue(charizardDef.immunities.contains(PokemonType.GROUND))
        // Grass, Bug should be quarter resist (0.5 * 0.5 = 0.25)
        assertTrue(charizardDef.resistancesQuarter.contains(PokemonType.GRASS))
        assertTrue(charizardDef.resistancesQuarter.contains(PokemonType.BUG))
    }

    @Test
    fun testGhostGreySteelResistanceOverride() {
        // In standard Gen 6+: Steel takes 1.0x from Ghost and Dark
        val standardSteelVsGhost = TypeChart.getEffectiveness(PokemonType.GHOST, PokemonType.STEEL, steelResistsGhostDark = false)
        val standardSteelVsDark = TypeChart.getEffectiveness(PokemonType.DARK, PokemonType.STEEL, steelResistsGhostDark = false)
        assertEquals(1.0f, standardSteelVsGhost, 0.01f)
        assertEquals(1.0f, standardSteelVsDark, 0.01f)

        // In Ghost Grey: Steel resists Ghost and Dark (0.5x)
        val ghostGreyVsGhost = TypeChart.getEffectiveness(PokemonType.GHOST, PokemonType.STEEL, steelResistsGhostDark = true)
        val ghostGreyVsDark = TypeChart.getEffectiveness(PokemonType.DARK, PokemonType.STEEL, steelResistsGhostDark = true)
        assertEquals(0.5f, ghostGreyVsGhost, 0.01f)
        assertEquals(0.5f, ghostGreyVsDark, 0.01f)

        // Defensive profile test for pure Steel
        val ggProfile = TypeChart.getDefenseProfile(PokemonType.STEEL, null, steelResistsGhostDark = true)
        assertTrue(ggProfile.resistancesHalf.contains(PokemonType.GHOST))
        assertTrue(ggProfile.resistancesHalf.contains(PokemonType.DARK))
    }

    @Test
    fun testSpeciesAndMoveDatabase() {
        val blaziken = SpeciesDatabase.get(257)
        assertEquals("Blaziken", blaziken.name)
        assertEquals(PokemonType.FIRE, blaziken.type1)
        assertEquals(PokemonType.FIGHTING, blaziken.type2)

        val emboar = SpeciesDatabase.get(500)
        assertEquals("Emboar", emboar.name)
        assertEquals(PokemonType.FIRE, emboar.type1)
        assertEquals(PokemonType.FIGHTING, emboar.type2)

        val hydroPump = MoveDatabase.get(56)
        assertEquals("Hydro Pump", hydroPump.name)
        assertEquals(PokemonType.WATER, hydroPump.type)
        assertEquals(MoveCategory.SPECIAL, hydroPump.category)
        assertEquals(110, hydroPump.power)

        val moonblast = MoveDatabase.get(585)
        assertEquals("Moonblast", moonblast.name)
        assertEquals(PokemonType.FAIRY, moonblast.type)
        assertEquals(95, moonblast.power)
    }
}
