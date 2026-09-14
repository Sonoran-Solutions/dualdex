package com.dualdex.companion.ui

import android.content.Context

/** Small, View-friendly visual vocabulary shared by companion chrome. */
object DualDexTheme {
    object Color {
        const val background = 0xFF101116.toInt()
        const val surface = 0xFF181A20.toInt()
        const val elevatedSurface = 0xFF1F222A.toInt()
        const val surfacePressed = 0xFF282C36.toInt()
        const val surfaceSelected = 0xFF1D314F.toInt()
        const val surfaceFocused = 0xFF263C5C.toInt()
        const val surfaceDisabled = 0xFF242831.toInt()
        const val textPrimary = 0xFFF4F5F7.toInt()
        const val textSecondary = 0xFF9AA0AA.toInt()
        const val textDisabled = 0xFF858C98.toInt()
        const val border = 0xFF3A414E.toInt()
        const val accent = 0xFF5B9CFF.toInt()
        const val accentPressed = 0xFF397DDD.toInt()
        const val accentFocused = 0xFF396EB4.toInt()
        const val onAccent = 0xFF081A33.toInt()
        const val success = 0xFF55C878.toInt()
        const val warning = 0xFFE5B84B.toInt()
        const val danger = 0xFFEF6262.toInt()
        const val dangerPressed = 0xFFC64A4A.toInt()
        const val dangerFocused = 0xFFB73F49.toInt()
        const val onDanger = 0xFF26080C.toInt()
        const val focusRing = 0xFFB7D2FF.toInt()
        const val transparent = android.graphics.Color.TRANSPARENT
    }

    object Spacing {
        const val tight = 4
        const val compact = 8
        const val standard = 12
        const val section = 16
        const val major = 24
        const val touchTarget = 48
    }

    object Radius {
        const val control = 8
        const val surface = 12
        const val pill = 999
    }

    object Control {
        const val primaryNavigationHeight = 56
        const val navigationIcon = 20
        const val focusStroke = 2
        const val defaultStroke = 1
    }

    object Type {
        const val screenTitle = 22f
        const val sectionTitle = 16f
        const val body = 14f
        const val meta = 12f
        const val compact = 11f
        const val chevron = 24f
    }
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
