package com.dualdex.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionStatusFormatterTest {

    @Test
    fun normalBatteryLevelShowsPercentageAndTextSecondaryColor() {
        val display82 = CompanionStatusFormatter.formatBatteryLevel(level = 82, scale = 100, isCharging = false)
        assertEquals("82%", display82.text)
        assertEquals(DualDexTheme.Color.textSecondary, display82.color)

        val display100 = CompanionStatusFormatter.formatBatteryLevel(level = 100, scale = 100, isCharging = false)
        assertEquals("100%", display100.text)
        assertEquals(DualDexTheme.Color.textSecondary, display100.color)

        val display21 = CompanionStatusFormatter.formatBatteryLevel(level = 21, scale = 100, isCharging = false)
        assertEquals("21%", display21.text)
        assertEquals(DualDexTheme.Color.textSecondary, display21.color)
    }

    @Test
    fun lowBatteryUsesSubtleWarningColorAtTwentyPercentOrBelow() {
        val display20 = CompanionStatusFormatter.formatBatteryLevel(level = 20, scale = 100, isCharging = false)
        assertEquals("20%", display20.text)
        assertEquals(DualDexTheme.Color.warning, display20.color)

        val display15 = CompanionStatusFormatter.formatBatteryLevel(level = 15, scale = 100, isCharging = false)
        assertEquals("15%", display15.text)
        assertEquals(DualDexTheme.Color.warning, display15.color)
    }

    @Test
    fun criticalBatteryUsesDangerColorAtTenPercentOrBelow() {
        val display10 = CompanionStatusFormatter.formatBatteryLevel(level = 10, scale = 100, isCharging = false)
        assertEquals("10%", display10.text)
        assertEquals(DualDexTheme.Color.danger, display10.color)

        val display5 = CompanionStatusFormatter.formatBatteryLevel(level = 5, scale = 100, isCharging = false)
        assertEquals("5%", display5.text)
        assertEquals(DualDexTheme.Color.danger, display5.color)

        val display0 = CompanionStatusFormatter.formatBatteryLevel(level = 0, scale = 100, isCharging = false)
        assertEquals("0%", display0.text)
        assertEquals(DualDexTheme.Color.danger, display0.color)
    }

    @Test
    fun chargingBatteryAppendsIconAndKeepsSecondaryColorWithoutAlarmingColor() {
        val charging82 = CompanionStatusFormatter.formatBatteryLevel(level = 82, scale = 100, isCharging = true)
        assertEquals("82% ⚡", charging82.text)
        assertEquals(DualDexTheme.Color.textSecondary, charging82.color)

        val charging5 = CompanionStatusFormatter.formatBatteryLevel(level = 5, scale = 100, isCharging = true)
        assertEquals("5% ⚡", charging5.text)
        assertEquals(DualDexTheme.Color.textSecondary, charging5.color)
    }

    @Test
    fun unavailableOrUnknownBatteryLevelGracefullyFallsBack() {
        val unknown = CompanionStatusFormatter.formatBatteryLevel(level = -1, scale = 100, isCharging = false)
        assertEquals("--%", unknown.text)
        assertEquals(DualDexTheme.Color.textSecondary, unknown.color)

        val zeroScale = CompanionStatusFormatter.formatBatteryLevel(level = 50, scale = 0, isCharging = false)
        assertEquals("--%", zeroScale.text)
        assertEquals(DualDexTheme.Color.textSecondary, zeroScale.color)

        val nullIntent = CompanionStatusFormatter.formatBattery(null)
        assertEquals("--%", nullIntent.text)
        assertEquals(DualDexTheme.Color.textSecondary, nullIntent.color)
    }
}
