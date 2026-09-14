package com.dualdex.companion.ui

import com.dualdex.emulator.SaveSlotInfo
import com.dualdex.emulator.ShaderFilter
import com.dualdex.settings.BattleAutoOpenPreference
import org.junit.Assert.*
import org.junit.Test

class SavesAndSettingsUiTest {

    @Test
    fun designThemeTokensAreRestrainedAndOledFriendly() {
        // Deep background and neutral elevated surfaces
        assertEquals(0xFF101116.toInt(), DualDexTheme.Color.background)
        assertEquals(0xFF181A20.toInt(), DualDexTheme.Color.surface)
        assertEquals(0xFF1F222A.toInt(), DualDexTheme.Color.elevatedSurface)
        assertEquals(0xFF3A414E.toInt(), DualDexTheme.Color.border)

        // Single primary brand accent
        assertEquals(0xFF5B9CFF.toInt(), DualDexTheme.Color.accent)

        // Standard spacing scale
        assertEquals(4, DualDexTheme.Spacing.tight)
        assertEquals(8, DualDexTheme.Spacing.compact)
        assertEquals(12, DualDexTheme.Spacing.standard)
        assertEquals(16, DualDexTheme.Spacing.section)
        assertEquals(24, DualDexTheme.Spacing.major)
        assertEquals(48, DualDexTheme.Spacing.touchTarget)

        // Standard corner radii
        assertEquals(8, DualDexTheme.Radius.control)
        assertEquals(12, DualDexTheme.Radius.surface)
    }

    @Test
    fun shaderFilterLabelsAreCleanAndSplitProperlyForSegmentedControl() {
        val filters = ShaderFilter.values()
        assertEquals(4, filters.size)

        val labels = filters.map { filter ->
            when (filter) {
                ShaderFilter.NEAREST -> "Nearest"
                ShaderFilter.SHARP_BILINEAR -> "Bilinear"
                ShaderFilter.LCD_GRID -> "LCD Grid"
                ShaderFilter.CRT_SCANLINE -> "Scanlines"
            }
        }
        assertEquals(listOf("Nearest", "Bilinear", "LCD Grid", "Scanlines"), labels)

        labels.forEach { name ->
            assertFalse("Shader label should not contain emoji: $name", name.any { it.code > 127 })
            assertTrue("Shader label should be non-empty", name.isNotBlank())
        }
    }

    @Test
    fun fastForwardSpeedLabelsMatchSupportedMultipliers() {
        val speeds = listOf(1, 2, 3, 4)
        val speedLabels = listOf("1x Normal", "2x Fast", "3x Turbo", "4x Max")
        assertEquals(speeds.size, speedLabels.size)

        speeds.forEachIndexed { index, speed ->
            val label = speedLabels[index]
            assertTrue("Label should indicate multiplier: $label", label.startsWith("${speed}x"))
            assertFalse("Label should not contain emoji: $label", label.any { it.code > 127 })
        }
    }

    @Test
    fun geminiModelOptionsAreValidIdentifiers() {
        val models = listOf("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.0-flash")
        val modelLabels = listOf("Flash 3.8", "Flash 2.5", "Flash 2.0")
        assertEquals(models.size, modelLabels.size)

        models.forEach { modelId ->
            assertTrue("Model ID must start with gemini: $modelId", modelId.startsWith("gemini-"))
            assertFalse("Model ID must not contain spaces: $modelId", modelId.contains(" "))
        }
    }

    @Test
    fun saveSlotInfoProvidesCleanDisplayStrings() {
        val occupied = SaveSlotInfo(
            slotIndex = 1,
            exists = true,
            timestampMs = 1726300000000L,
            formattedDate = "Sep 14, 2026, 9:30 AM",
            sizeBytes = 131072L
        )
        assertEquals(1, occupied.slotIndex)
        assertTrue(occupied.exists)
        assertEquals("Sep 14, 2026, 9:30 AM", occupied.formattedDate)
        assertEquals(128L, occupied.sizeBytes / 1024)

        val empty = SaveSlotInfo(
            slotIndex = 2,
            exists = false,
            timestampMs = 0L,
            formattedDate = "Empty",
            sizeBytes = 0L
        )
        assertEquals(2, empty.slotIndex)
        assertFalse(empty.exists)
        assertEquals(0L, empty.sizeBytes)
    }

    @Test
    fun preferenceAutoOpenResolvesCleanlyForUiToggle() {
        // Current value true -> resolves true
        val resolvedTrue = BattleAutoOpenPreference.resolve(
            hasCurrentValue = true,
            currentValue = true,
            hasLegacyValue = false,
            legacyValue = false
        )
        assertTrue(resolvedTrue)

        // Current value false -> resolves false
        val resolvedFalse = BattleAutoOpenPreference.resolve(
            hasCurrentValue = true,
            currentValue = false,
            hasLegacyValue = true,
            legacyValue = true
        )
        assertFalse(resolvedFalse)
    }
}
