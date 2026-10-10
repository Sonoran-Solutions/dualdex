package com.dualdex.settings

import android.content.Context
import android.content.SharedPreferences
import com.dualdex.emulator.ShaderFilter

/** When the on-screen touch controls are shown (#153). */
enum class TouchOverlayMode(val label: String) { AUTO("Auto"), ALWAYS("Always"), NEVER("Never") }

/** What the physical L2/R2 triggers do (#14). */
enum class TriggerShortcutMode(val label: String) {
    QUICK_SAVE_LOAD("Quick Save / Load"),
    FAST_FORWARD("Speed Down / Up"),
    HOLD_SPEED("Hold Slow / Fast"),
    DISABLED("Disabled")
}

open class SettingsManager(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences("dualdex_settings", Context.MODE_PRIVATE)
    )

    var shaderFilter: ShaderFilter
        get() {
            val name = prefs.getString(KEY_SHADER_FILTER, ShaderFilter.NEAREST.name)
            return try {
                ShaderFilter.valueOf(name ?: ShaderFilter.NEAREST.name)
            } catch (e: Exception) {
                ShaderFilter.NEAREST
            }
        }
        set(value) {
            prefs.edit().putString(KEY_SHADER_FILTER, value.name).apply()
        }

    var fastForwardMultiplier: Int
        get() = prefs.getInt(KEY_FAST_FORWARD, MIN_SPEED).coerceIn(MIN_SPEED, MAX_SPEED)
        set(value) {
            prefs.edit().putInt(KEY_FAST_FORWARD, value).apply()
        }

    var triggerShortcutMode: TriggerShortcutMode
        get() = TriggerShortcutMode.values().firstOrNull { it.name == prefs.getString(KEY_TRIGGER_MODE, null) }
            ?: TriggerShortcutMode.QUICK_SAVE_LOAD
        set(value) {
            prefs.edit().putString(KEY_TRIGGER_MODE, value.name).apply()
        }

    var swapAB: Boolean
        get() = prefs.getBoolean(KEY_SWAP_AB, false)
        set(value) { prefs.edit().putBoolean(KEY_SWAP_AB, value).apply() }

    /**
     * One-time device default for [swapAB]: only an untouched config (no stored value) takes the
     * device default; the marker stops it ever running again.
     */
    fun migrateSwapABDefault(manufacturer: String?, brand: String?, model: String?) {
        if (prefs.getBoolean(KEY_SWAP_AB_MIGRATED, false)) return
        val e = prefs.edit().putBoolean(KEY_SWAP_AB_MIGRATED, true)
        if (!prefs.contains(KEY_SWAP_AB)) e.putBoolean(KEY_SWAP_AB, swapABByDefault(manufacturer, brand, model))
        e.apply()
    }

    var touchOverlayMode: TouchOverlayMode
        get() = TouchOverlayMode.values().firstOrNull { it.name == prefs.getString(KEY_TOUCH_OVERLAY, null) }
            ?: TouchOverlayMode.AUTO
        set(value) { prefs.edit().putString(KEY_TOUCH_OVERLAY, value.name).apply() }

    /** Speed step remembered per ROM (by storage key); falls back to the global step. */
    fun romSpeed(romKey: String?): Int =
        romKey?.let { prefs.getInt(KEY_ROM_SPEED_PREFIX + it, 0) }?.takeIf { it > 0 }?.coerceIn(MIN_SPEED, MAX_SPEED)
            ?: fastForwardMultiplier

    fun setRomSpeed(romKey: String?, speed: Int) {
        val e = prefs.edit().putInt(KEY_FAST_FORWARD, speed)
        if (romKey != null) e.putInt(KEY_ROM_SPEED_PREFIX + romKey, speed)
        e.apply()
    }

    fun registerChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregisterChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    var isSmartFastForwardEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_FAST_FORWARD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SMART_FAST_FORWARD, value).apply()
        }

    /** Smart fast-forward's learned field/battle callbacks, per ROM SHA-256. */
    fun smartFastForwardLearned(romSha256: String): String? = prefs.getString(KEY_SMART_FF_LEARNED + romSha256, null)

    fun setSmartFastForwardLearned(romSha256: String, encoded: String) =
        prefs.edit().putString(KEY_SMART_FF_LEARNED + romSha256, encoded).apply()

    var isAudioEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUDIO_ENABLED, value).apply()
        }

    var geminiApiKey: String?
        get() = prefs.getString(KEY_GEMINI_API_KEY, null)
        set(value) {
            prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()
        }

    var isStretchToFitEnabled: Boolean
        get() = prefs.getBoolean(KEY_STRETCH_TO_FIT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_STRETCH_TO_FIT, value).apply()
        }

    var romsFolderUri: String?
        get() = prefs.getString(KEY_ROMS_FOLDER_URI, null)
        set(value) {
            prefs.edit().putString(KEY_ROMS_FOLDER_URI, value).apply()
        }

    var savesFolderUri: String?
        get() = prefs.getString(KEY_SAVES_FOLDER_URI, null)
        set(value) {
            prefs.edit().putString(KEY_SAVES_FOLDER_URI, value).apply()
        }

    /** #153 opt-in: SAF tree where `<rom>.sav` is shared with other emulators; null = sharing off. */
    var shareSavesFolderUri: String?
        get() = prefs.getString(KEY_SHARE_SAVES_FOLDER_URI, null)
        set(value) {
            prefs.edit().putString(KEY_SHARE_SAVES_FOLDER_URI, value).apply()
        }

    var lastPlayedRomUri: String?
        get() = prefs.getString(KEY_LAST_PLAYED_ROM_URI, null)
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_LAST_PLAYED_ROM_URI).apply()
            } else {
                prefs.edit().putString(KEY_LAST_PLAYED_ROM_URI, value).apply()
            }
        }

    var lastPlayedRomTitle: String?
        get() = prefs.getString(KEY_LAST_PLAYED_ROM_TITLE, null)
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_LAST_PLAYED_ROM_TITLE).apply()
            } else {
                prefs.edit().putString(KEY_LAST_PLAYED_ROM_TITLE, value).apply()
            }
        }

    /** Clears recorded Continue state atomically. */
    fun clearLastPlayedRom() {
        prefs.edit()
            .remove(KEY_LAST_PLAYED_ROM_URI)
            .remove(KEY_LAST_PLAYED_ROM_TITLE)
            .apply()
    }

    var geminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, "gemini-3.8-flash") ?: "gemini-3.8-flash"
        set(value) {
            prefs.edit().putString(KEY_GEMINI_MODEL, value).apply()
        }

    /** Optional status bar above the game (#153). Off by default. */
    var isGameStatusBarEnabled: Boolean
        get() = prefs.getBoolean(KEY_GAME_STATUS_BAR, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GAME_STATUS_BAR, value).apply()
        }

    /** Whether the Battle Console opens automatically when a battle starts. */
    var isBattleAutoOpenEnabled: Boolean
        get() {
            val hasCurrentValue = prefs.contains(KEY_BATTLE_AUTO_OPEN)
            val currentValue = prefs.getBoolean(KEY_BATTLE_AUTO_OPEN, true)
            val hasLegacyValue = prefs.contains(LEGACY_KEY_BATTLE_AUTO_OPEN)
            val legacyValue = prefs.getBoolean(LEGACY_KEY_BATTLE_AUTO_OPEN, true)
            val resolvedValue = BattleAutoOpenPreference.resolve(
                hasCurrentValue = hasCurrentValue,
                currentValue = currentValue,
                hasLegacyValue = hasLegacyValue,
                legacyValue = legacyValue
            )
            // Preserve the former preference while migrating its now-correct meaning.
            if (!hasCurrentValue && hasLegacyValue) {
                prefs.edit()
                    .putBoolean(KEY_BATTLE_AUTO_OPEN, resolvedValue)
                    .remove(LEGACY_KEY_BATTLE_AUTO_OPEN)
                    .apply()
            }
            return resolvedValue
        }
        set(value) {
            prefs.edit()
                .putBoolean(KEY_BATTLE_AUTO_OPEN, value)
                .remove(LEGACY_KEY_BATTLE_AUTO_OPEN)
                .apply()
        }

    var isInteractiveBattleControlsEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTERACTIVE_BATTLE_CONTROLS_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INTERACTIVE_BATTLE_CONTROLS_ENABLED, value).apply()
        }

    var legacySavesCheckedOnFirstOpen: Boolean
        get() = prefs.getBoolean(KEY_LEGACY_SAVES_CHECKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LEGACY_SAVES_CHECKED, value).apply()
        }

    companion object {
        const val MIN_SPEED = 1
        const val MAX_SPEED = 4

        /** L2/R2 speed stepping, clamped to the Settings range (1x-4x). */
        /** Hold-to-speed rates for [TriggerShortcutMode.HOLD_SPEED]. */
        const val HOLD_SLOW_SPEED = 0.5f
        const val HOLD_FAST_SPEED = MAX_SPEED.toFloat()

        /** AYN (Odin/Thor) and Retroid handhelds label A/B Nintendo-style but report Xbox keycodes. */
        fun swapABByDefault(manufacturer: String?, brand: String?, model: String?): Boolean =
            listOf(manufacturer, brand, model).any { v ->
                val t = v?.trim()?.lowercase() ?: return@any false
                t == "ayn" || t.startsWith("ayn ") || t.startsWith("odin") || t.contains("retroid")
            }

        fun steppedSpeed(current: Int, delta: Int): Int = (current + delta).coerceIn(MIN_SPEED, MAX_SPEED)

        private const val KEY_SWAP_AB = "key_swap_ab"
        private const val KEY_SWAP_AB_MIGRATED = "key_swap_ab_device_default_applied"
        private const val KEY_TOUCH_OVERLAY = "key_touch_overlay_mode"
        private const val KEY_ROM_SPEED_PREFIX = "key_rom_speed_"
        private const val KEY_TRIGGER_MODE = "key_trigger_shortcut_mode"
        private const val KEY_SHADER_FILTER = "key_shader_filter"
        private const val KEY_FAST_FORWARD = "key_fast_forward"
        private const val KEY_SMART_FAST_FORWARD = "key_smart_fast_forward"
        private const val KEY_SMART_FF_LEARNED = "key_smart_ff_learned_"
        private const val KEY_AUDIO_ENABLED = "key_audio_enabled"
        private const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
        private const val KEY_STRETCH_TO_FIT = "key_stretch_to_fit"
        private const val KEY_ROMS_FOLDER_URI = "key_roms_folder_uri"
        private const val KEY_SAVES_FOLDER_URI = "key_saves_folder_uri"
        private const val KEY_SHARE_SAVES_FOLDER_URI = "key_share_saves_folder_uri"
        private const val KEY_LAST_PLAYED_ROM_URI = "key_last_played_rom_uri"
        private const val KEY_LAST_PLAYED_ROM_TITLE = "key_last_played_rom_title"
        private const val KEY_GEMINI_MODEL = "key_gemini_model"
        private const val KEY_BATTLE_AUTO_OPEN = "key_battle_auto_open"
        const val KEY_GAME_STATUS_BAR = "key_game_status_bar"
        private const val LEGACY_KEY_BATTLE_AUTO_OPEN = "key_battle_tab_enabled"
        private const val KEY_INTERACTIVE_BATTLE_CONTROLS_ENABLED = "key_interactive_battle_controls_enabled"
        private const val KEY_LEGACY_SAVES_CHECKED = "key_legacy_saves_checked"
    }
}
