package com.dualdex.companion.ui

import com.dualdex.battle.SpeedComparison
import com.dualdex.battle.StatStages
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.TypeChart
import org.junit.Assert.*
import org.junit.Test

class BattleUiRedesignTest {

    @Test
    fun hpColorThresholdsMatchSemanticDesignTokens() {
        // > 50% HP -> success
        assertEquals(DualDexTheme.Color.success, DualDexComponents.hpColor(current = 100, maximum = 100))
        assertEquals(DualDexTheme.Color.success, DualDexComponents.hpColor(current = 51, maximum = 100))

        // 21% .. 50% HP -> warning
        assertEquals(DualDexTheme.Color.warning, DualDexComponents.hpColor(current = 50, maximum = 100))
        assertEquals(DualDexTheme.Color.warning, DualDexComponents.hpColor(current = 21, maximum = 100))

        // <= 20% HP -> danger
        assertEquals(DualDexTheme.Color.danger, DualDexComponents.hpColor(current = 20, maximum = 100))
        assertEquals(DualDexTheme.Color.danger, DualDexComponents.hpColor(current = 1, maximum = 100))
        assertEquals(DualDexTheme.Color.danger, DualDexComponents.hpColor(current = 0, maximum = 100))

        // zero maximum HP gracefully returns danger
        assertEquals(DualDexTheme.Color.danger, DualDexComponents.hpColor(current = 0, maximum = 0))
    }

    @Test
    fun battleModesDefineExpectedThreeDestinationsWithoutEmoji() {
        val modes = BattleMode.values()
        assertEquals(3, modes.size)
        assertEquals(BattleMode.BATTLE, modes[0])
        assertEquals(BattleMode.TYPE_MATCHUPS, modes[1])
        assertEquals(BattleMode.DETAILS, modes[2])

        assertEquals("Battle", BattleMode.BATTLE.title)
        assertEquals("Type Matchups", BattleMode.TYPE_MATCHUPS.title)
        assertEquals("Details", BattleMode.DETAILS.title)

        modes.forEach { mode ->
            assertFalse("Mode title must not contain emoji: ${mode.title}", mode.title.any { it.code > 127 })
        }
    }

    @Test
    fun typeMatchupDefenseProfileProducesAccurateMultipliers() {
        // Fire / Flying (Charizard)
        val charizardProfile = TypeChart.getDefenseProfile(
            type1 = PokemonType.FIRE,
            type2 = PokemonType.FLYING,
            steelResistsGhostDark = false
        )

        assertEquals(listOf(PokemonType.ROCK), charizardProfile.weaknesses4x)
        assertTrue(charizardProfile.weaknesses2x.contains(PokemonType.WATER))
        assertTrue(charizardProfile.weaknesses2x.contains(PokemonType.ELECTRIC))
        assertEquals(2, charizardProfile.weaknesses2x.size)

        assertTrue(charizardProfile.resistancesHalf.contains(PokemonType.FIRE))
        assertTrue(charizardProfile.resistancesHalf.contains(PokemonType.FIGHTING))
        assertTrue(charizardProfile.resistancesHalf.contains(PokemonType.STEEL))
        assertTrue(charizardProfile.resistancesHalf.contains(PokemonType.FAIRY))

        assertTrue(charizardProfile.resistancesQuarter.contains(PokemonType.GRASS))
        assertTrue(charizardProfile.resistancesQuarter.contains(PokemonType.BUG))

        assertEquals(listOf(PokemonType.GROUND), charizardProfile.immunities)
    }

    @Test
    fun speedComparisonProvidesCleanCompactLabels() {
        val faster = SpeedComparison.calculate(playerBaseSpeed = 120, enemyBaseSpeed = 80)
        assertEquals("Faster (known modifiers)", faster.orderLabel)
        assertEquals(true, faster.playerMovesFirst)

        val slower = SpeedComparison.calculate(playerBaseSpeed = 70, enemyBaseSpeed = 100)
        assertEquals("Slower (known modifiers)", slower.orderLabel)
        assertEquals(false, slower.playerMovesFirst)

        val tie = SpeedComparison.calculate(playerBaseSpeed = 100, enemyBaseSpeed = 100)
        assertEquals("Speed Tie (known modifiers)", tie.orderLabel)
        assertNull(tie.playerMovesFirst)
    }

    @Test
    fun statStagesFormattingDisplaysSignsAndAccurateMultipliers() {
        val stages = StatStages(atk = 2, def = -1, spe = 0)
        assertEquals("+2", StatStages.formatStage(stages.atk))
        assertEquals("-1", StatStages.formatStage(stages.def))
        assertEquals("0", StatStages.formatStage(stages.spe))

        assertEquals(2.0, StatStages.statMultiplier(stages.atk), 0.001)
        assertEquals(2.0 / 3.0, StatStages.statMultiplier(stages.def), 0.001)
        assertEquals(1.0, StatStages.statMultiplier(stages.spe), 0.001)

        val chips = stages.activeStageChips()
        assertEquals(2, chips.size)
        assertEquals("Atk" to 2, chips[0])
        assertEquals("Def" to -1, chips[1])
    }
}
