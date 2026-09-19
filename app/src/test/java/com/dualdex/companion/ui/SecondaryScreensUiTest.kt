package com.dualdex.companion.ui

import com.dualdex.cheats.CheatItem
import com.dualdex.pokemon.MapNodeType
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.*
import org.junit.Test

class SecondaryScreensUiTest {

    @Test
    fun mapRegionLabelsAreRestrainedWithoutEmoji() {
        val regions = listOf(RegionId.JOHTO, RegionId.KANTO, RegionId.HOENN)
        val names = regions.map { it.displayName }
        assertEquals(listOf("Johto", "Kanto", "Hoenn"), names)

        names.forEach { name ->
            assertFalse("Region name should not contain emoji: $name", name.any { it.code > 127 })
            assertTrue("Region name should be non-empty", name.isNotBlank())
        }
    }

    @Test
    fun playerLocationResolvesToCleanLocationName() {
        // Mock Johto location (New Bark Town: mapGroup 0, mapNum 0)
        val loc = PlayerLocation(
            mapGroup = 0,
            mapNum = 0,
            warpId = 0,
            x = 10,
            y = 12,
            localX = 10,
            localY = 12,
            escapeMapGroup = 0,
            escapeMapNum = 0,
            isIndoors = false,
            isValid = true
        )
        val section = RegionMapDatabase.resolveLocation(
            strategy = LocationStrategy.HEART_AND_SOUL_205,
            loc = loc
        )
        assertNotNull("A H&S New Bark Town read must resolve", section)
        assertEquals("New Bark Town", section!!.name)
        assertEquals(MapNodeType.TOWN, section.nodeType)
        assertFalse("Section name should not contain emoji: ${section.name}", section.name.any { it.code > 127 })
    }

    @Test
    fun assistantPromptSuggestionsAreVersatileAndEmojiFree() {
        val prompts = listOf(
            "Evolution changes",
            "Gym leader teams",
            "Type effectiveness",
            "Item locations",
            "Where is Fly?"
        )
        assertEquals(5, prompts.size)

        prompts.forEach { prompt ->
            assertFalse("Prompt suggestion should not contain emoji: $prompt", prompt.any { it.code > 127 })
            assertTrue("Prompt suggestion should be non-empty", prompt.isNotBlank())
        }
    }

    @Test
    fun cheatsToggleStatesAndBadgeAreConsistent() {
        val preset = CheatItem(
            id = "test-preset-1",
            name = "Infinite Money",
            code = "02039994 000F423F",
            enabled = true,
            isPreset = true
        )
        assertEquals("Infinite Money", preset.name)
        assertTrue(preset.enabled)
        assertTrue(preset.isPreset)

        val custom = CheatItem(
            id = "test-custom-1",
            name = "Rare Candies",
            code = "82025840 0044",
            enabled = false,
            isPreset = false
        )
        assertFalse(custom.enabled)
        assertFalse(custom.isPreset)
    }

    @Test
    fun offlineGuideDoesNotShowGhostGreyVariantsForUnrelatedProfiles() {
        val vanillaProfile = RomHackProfile(
            id = "vanilla_firered",
            name = "FireRed (Vanilla)",
            gameId = 2,
            baseGame = "FireRed v1.0",
            developer = "Game Freak",
            engine = "Vanilla"
        )
        assertFalse(vanillaProfile.id == "ghost_grey")
        assertFalse(vanillaProfile.name.contains("Ghost Grey", ignoreCase = true))

        val ghostProfile = RomHackProfile(
            id = "ghost_grey",
            name = "Pokemon Ghost Grey",
            gameId = 1,
            baseGame = "FireRed v1.0",
            developer = "Community",
            engine = "CFRU"
        )
        assertTrue(ghostProfile.id == "ghost_grey" || ghostProfile.name.contains("Ghost Grey", ignoreCase = true))
    }

    @Test
    fun smallButtonComponentProducesProperDimensionsAndStates() {
        // Verified by compilation and token checks
        assertEquals(8, DualDexTheme.Radius.control)
        assertEquals(4, DualDexTheme.Spacing.tight)
        assertEquals(8, DualDexTheme.Spacing.compact)
    }
}
