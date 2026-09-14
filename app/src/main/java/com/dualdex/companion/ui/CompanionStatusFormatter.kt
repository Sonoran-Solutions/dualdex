package com.dualdex.companion.ui

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import java.util.Date

/**
 * Shared formatting logic for glanceable companion shell status (clock and battery).
 */
object CompanionStatusFormatter {

    data class BatteryDisplay(
        val text: String,
        val color: Int
    )

    fun formatTime(context: Context, date: Date = Date()): String {
        return try {
            val format = android.text.format.DateFormat.getTimeFormat(context)
            format.format(date)
        } catch (_: Throwable) {
            ""
        }
    }

    fun formatBattery(intent: Intent?): BatteryDisplay {
        if (intent == null) {
            return BatteryDisplay("--%", DualDexTheme.Color.textSecondary)
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return formatBatteryLevel(level, scale, isCharging)
    }

    fun formatBatteryLevel(level: Int, scale: Int, isCharging: Boolean): BatteryDisplay {
        val pct = if (scale > 0 && level >= 0) (level * 100 / scale) else -1
        if (pct < 0) {
            return BatteryDisplay("--%", DualDexTheme.Color.textSecondary)
        }
        val text = if (isCharging) "$pct% ⚡" else "$pct%"
        val color = when {
            isCharging -> DualDexTheme.Color.textSecondary
            pct <= 10 -> DualDexTheme.Color.danger
            pct <= 20 -> DualDexTheme.Color.warning
            else -> DualDexTheme.Color.textSecondary
        }
        return BatteryDisplay(text, color)
    }
}
