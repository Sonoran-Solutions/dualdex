package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface

/**
 * Companion visual styles (issue #133). [NAVIGATOR] is the PokéNav-inspired device identity;
 * [QUIET_HANDHELD] is the original neutral theme, kept selectable in Settings for A/B comparison
 * until the physical-device gate is recorded.
 */
enum class CompanionVisualStyle { QUIET_HANDHELD, NAVIGATOR }

/** Small, View-friendly visual vocabulary shared by companion chrome. */
object DualDexTheme {
    /**
     * Active style. Views read tokens when they are built, so a change only takes effect for views
     * created afterwards; the host rebuilds the companion shell when the setting changes.
     */
    @Volatile
    var style: CompanionVisualStyle = CompanionVisualStyle.NAVIGATOR

    val isNavigator: Boolean get() = style == CompanionVisualStyle.NAVIGATOR

    /** One semantic palette per style; screens ask for roles, never for a style. */
    internal data class Palette(
        val background: Int,
        val surface: Int,
        val elevatedSurface: Int,
        val surfacePressed: Int,
        val surfaceSelected: Int,
        val surfaceFocused: Int,
        val surfaceDisabled: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textDisabled: Int,
        val border: Int,
        val accent: Int,
        val accentPressed: Int,
        val accentFocused: Int,
        val onAccent: Int,
        val success: Int,
        val warning: Int,
        val danger: Int,
        val dangerPressed: Int,
        val dangerFocused: Int,
        val onDanger: Int,
        val focusRing: Int,
        val shell: Int,
        val shellHighlight: Int,
        val bezel: Int,
    )

    internal val quietPalette = Palette(
        background = 0xFF101116.toInt(),
        surface = 0xFF181A20.toInt(),
        elevatedSurface = 0xFF1F222A.toInt(),
        surfacePressed = 0xFF282C36.toInt(),
        surfaceSelected = 0xFF1D314F.toInt(),
        surfaceFocused = 0xFF263C5C.toInt(),
        surfaceDisabled = 0xFF242831.toInt(),
        textPrimary = 0xFFF4F5F7.toInt(),
        textSecondary = 0xFF9AA0AA.toInt(),
        textDisabled = 0xFF858C98.toInt(),
        border = 0xFF3A414E.toInt(),
        accent = 0xFF5B9CFF.toInt(),
        accentPressed = 0xFF397DDD.toInt(),
        accentFocused = 0xFF396EB4.toInt(),
        onAccent = 0xFF081A33.toInt(),
        success = 0xFF55C878.toInt(),
        warning = 0xFFE5B84B.toInt(),
        danger = 0xFFEF6262.toInt(),
        dangerPressed = 0xFFC64A4A.toInt(),
        dangerFocused = 0xFFB73F49.toInt(),
        onDanger = 0xFF26080C.toInt(),
        focusRing = 0xFFB7D2FF.toInt(),
        shell = 0xFF181A20.toInt(),
        shellHighlight = 0xFF181A20.toInt(),
        bezel = 0xFF181A20.toInt(),
    )

    // Starting values from issue #133. Controller focus uses the yellow selection cursor so it is
    // never confused with the cyan "selected" plate.
    internal val navigatorPalette = Palette(
        background = 0xFF0A2E3A.toInt(),
        surface = 0xFF0D3541.toInt(),
        elevatedSurface = 0xFF103F4B.toInt(),
        surfacePressed = 0xFF1A5966.toInt(),
        surfaceSelected = 0xFF15505C.toInt(),
        surfaceFocused = 0xFF1C6270.toInt(),
        surfaceDisabled = 0xFF0C2C36.toInt(),
        textPrimary = 0xFFEAF9F5.toInt(),
        textSecondary = 0xFFAFC3C9.toInt(),
        textDisabled = 0xFF7D959C.toInt(),
        border = 0xFF2A6572.toInt(),
        accent = 0xFF6BE4E8.toInt(),
        accentPressed = 0xFF49C3C8.toInt(),
        accentFocused = 0xFF8FEEF0.toInt(),
        onAccent = 0xFF04222A.toInt(),
        success = 0xFF72D69C.toInt(),
        warning = 0xFFF4C35A.toInt(),
        danger = 0xFFFF7B7B.toInt(),
        dangerPressed = 0xFFE05E5E.toInt(),
        dangerFocused = 0xFFFF9A9A.toInt(),
        onDanger = 0xFF2A0809.toInt(),
        focusRing = 0xFFFFE178.toInt(),
        shell = 0xFFC9D2DD.toInt(),
        shellHighlight = 0xFFE9EEF4.toInt(),
        bezel = 0xFF172436.toInt(),
    )

    internal val palette: Palette
        get() = if (isNavigator) navigatorPalette else quietPalette

    object Color {
        val background get() = palette.background
        val surface get() = palette.surface
        val elevatedSurface get() = palette.elevatedSurface
        val surfacePressed get() = palette.surfacePressed
        val surfaceSelected get() = palette.surfaceSelected
        val surfaceFocused get() = palette.surfaceFocused
        val surfaceDisabled get() = palette.surfaceDisabled
        val textPrimary get() = palette.textPrimary
        val textSecondary get() = palette.textSecondary
        val textDisabled get() = palette.textDisabled
        val border get() = palette.border
        val accent get() = palette.accent
        val accentPressed get() = palette.accentPressed
        val accentFocused get() = palette.accentFocused
        val onAccent get() = palette.onAccent
        val success get() = palette.success
        val warning get() = palette.warning
        val danger get() = palette.danger
        val dangerPressed get() = palette.dangerPressed
        val dangerFocused get() = palette.dangerFocused
        val onDanger get() = palette.onDanger
        val focusRing get() = palette.focusRing
        /** Navigator device shell (outer rim). Equals [surface] in the quiet style. */
        val shell get() = palette.shell
        val shellHighlight get() = palette.shellHighlight
        /** Navigator bezel behind the status strip and soft keys. Equals [surface] in the quiet style. */
        val bezel get() = palette.bezel
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
        val control get() = if (isNavigator) 3 else 8
        val surface get() = if (isNavigator) 6 else 12
        val pill get() = if (isNavigator) 3 else 999
    }

    object Control {
        val primaryNavigationHeight get() = if (isNavigator) 60 else 56
        const val navigationIcon = 20
        const val focusStroke = 2
        const val defaultStroke = 1
        /** Navigator outer shell and bezel thickness (dp), per the #133 spike bounds. */
        const val shellInset = 6
        const val bezelInset = 3
        /** Chamfer cut for Navigator LCD panels (dp). */
        const val chamfer = 6
    }

    object Type {
        const val screenTitle = 22f
        const val sectionTitle = 16f
        const val body = 14f
        const val meta = 12f
        const val compact = 11f
        const val micro = 9f
        const val chevron = 24f

        /** Condensed device face for short labels, values, and soft keys; body text stays sans. */
        val device: Typeface get() = if (isNavigator) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.DEFAULT_BOLD
    }
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
