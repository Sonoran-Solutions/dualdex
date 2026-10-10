package com.dualdex.settings

import com.dualdex.emulator.ShaderFilter
import org.junit.Assert.*
import org.junit.Test

class SettingsManagerTest {

    @Test
    fun testShaderFilterEnumValues() {
        val filters = ShaderFilter.values()
        assertEquals(4, filters.size)

        val nearest = ShaderFilter.valueOf("NEAREST")
        assertEquals("Pixel Perfect (Nearest)", nearest.displayName)
        assertTrue(nearest.description.contains("1:1"))

        val sharp = ShaderFilter.valueOf("SHARP_BILINEAR")
        assertEquals("Sharp Bilinear", sharp.displayName)

        val lcd = ShaderFilter.valueOf("LCD_GRID")
        assertEquals("GBA LCD Grid", lcd.displayName)
        assertTrue(lcd.description.contains("LCD"))

        val crt = ShaderFilter.valueOf("CRT_SCANLINE")
        assertEquals("CRT Scanlines", crt.displayName)
        assertTrue(crt.description.contains("scanlines"))
    }

    @Test
    fun triggerSpeedSteppingClampsToOneThroughFour() {
        assertEquals(1, SettingsManager.steppedSpeed(1, -1))
        assertEquals(1, SettingsManager.steppedSpeed(2, -1))
        assertEquals(3, SettingsManager.steppedSpeed(2, 1))
        assertEquals(4, SettingsManager.steppedSpeed(4, 1))
        assertEquals(4, SettingsManager.steppedSpeed(8, 1))
    }

    @Test
    fun triggerModeDefaultsToQuickSaveLoad() {
        assertEquals(TriggerShortcutMode.QUICK_SAVE_LOAD, TriggerShortcutMode.values().first())
    }

    @Test
    fun persistedSpeedDefaultsToNormalAndIsClamped() {
        val prefs = com.dualdex.emulator.RomDurableResumeTest.FakeSharedPreferences()
        val settings = SettingsManager(prefs)
        assertEquals(1, settings.fastForwardMultiplier)   // matches the emulator's startup speed
        settings.fastForwardMultiplier = 3
        assertEquals(3, SettingsManager(prefs).fastForwardMultiplier)
        settings.fastForwardMultiplier = 99
        assertEquals(4, settings.fastForwardMultiplier)
    }
}
