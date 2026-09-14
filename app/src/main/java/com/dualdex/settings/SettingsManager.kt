package com.dualdex.settings

import android.content.Context
import android.content.SharedPreferences
import com.dualdex.emulator.ShaderFilter

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
        get() = prefs.getInt(KEY_FAST_FORWARD, 2)
        set(value) {
            prefs.edit().putInt(KEY_FAST_FORWARD, value).apply()
        }

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
        private const val KEY_SHADER_FILTER = "key_shader_filter"
        private const val KEY_FAST_FORWARD = "key_fast_forward"
        private const val KEY_AUDIO_ENABLED = "key_audio_enabled"
        private const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
        private const val KEY_STRETCH_TO_FIT = "key_stretch_to_fit"
        private const val KEY_ROMS_FOLDER_URI = "key_roms_folder_uri"
        private const val KEY_SAVES_FOLDER_URI = "key_saves_folder_uri"
        private const val KEY_LAST_PLAYED_ROM_URI = "key_last_played_rom_uri"
        private const val KEY_LAST_PLAYED_ROM_TITLE = "key_last_played_rom_title"
        private const val KEY_GEMINI_MODEL = "key_gemini_model"
        private const val KEY_BATTLE_AUTO_OPEN = "key_battle_auto_open"
        private const val LEGACY_KEY_BATTLE_AUTO_OPEN = "key_battle_tab_enabled"
        private const val KEY_INTERACTIVE_BATTLE_CONTROLS_ENABLED = "key_interactive_battle_controls_enabled"
        private const val KEY_LEGACY_SAVES_CHECKED = "key_legacy_saves_checked"
    }
}
