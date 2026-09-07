package com.dualdex.settings

import android.content.Context
import android.content.SharedPreferences
import com.dualdex.emulator.ShaderFilter

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dualdex_settings", Context.MODE_PRIVATE)

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

    var lastPlayedRomUri: String?
        get() = prefs.getString(KEY_LAST_PLAYED_ROM_URI, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_PLAYED_ROM_URI, value).apply()
        }

    var lastPlayedRomTitle: String?
        get() = prefs.getString(KEY_LAST_PLAYED_ROM_TITLE, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_PLAYED_ROM_TITLE, value).apply()
        }

    /**
     * Removes the persisted "Continue Last Game" target. Called when the stored
     * URI is no longer usable so the app never keeps presenting a lost target.
     */
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

    companion object {
        private const val KEY_SHADER_FILTER = "key_shader_filter"
        private const val KEY_FAST_FORWARD = "key_fast_forward"
        private const val KEY_AUDIO_ENABLED = "key_audio_enabled"
        private const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
        private const val KEY_STRETCH_TO_FIT = "key_stretch_to_fit"
        private const val KEY_ROMS_FOLDER_URI = "key_roms_folder_uri"
        private const val KEY_LAST_PLAYED_ROM_URI = "key_last_played_rom_uri"
        private const val KEY_LAST_PLAYED_ROM_TITLE = "key_last_played_rom_title"
        private const val KEY_GEMINI_MODEL = "key_gemini_model"
    }
}
