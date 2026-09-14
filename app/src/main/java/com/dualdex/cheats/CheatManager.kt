package com.dualdex.cheats

import android.content.Context
import android.util.Log
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import org.json.JSONArray
import org.json.JSONObject

class CheatManager(
    private val context: Context? = null,
    private val memoryStorage: MutableMap<String, String>? = null,
    var coreCoordinator: LibretroCoreCoordinator = LibretroCoreCoordinator.defaultInstance
) {
    private val prefs by lazy {
        context?.getSharedPreferences("dualdex_cheats", Context.MODE_PRIVATE)
    }

    private fun getPrefString(key: String): String? {
        return memoryStorage?.get(key) ?: prefs?.getString(key, null)
    }

    private fun setPrefString(key: String, value: String) {
        if (memoryStorage != null) {
            memoryStorage[key] = value
        } else {
            prefs?.edit()?.putString(key, value)?.apply()
        }
    }

    fun getCheats(identity: RomIdentity, fallbackGameKey: String? = null): List<CheatItem> {
        if (!identity.isValid) return emptyList()

        val hashKey = identity.sha256
        val jsonStr = getPrefString("cheats_$hashKey")
        if (jsonStr == null) {
            // Defaults are a read-only view until the user explicitly saves or loads them.
            val gameContext = fallbackGameKey ?: identity.displayName
            val defaultPresets = getPresetsForGame(gameContext).map { it.copy(enabled = false) }
            return defaultPresets
        }

        return try {
            val list = ArrayList<CheatItem>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CheatItem(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        code = obj.optString("code"),
                        enabled = obj.optBoolean("enabled", false),
                        isPreset = obj.optBoolean("isPreset", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cheats for $hashKey", e)
            emptyList()
        }
    }

    fun saveCheats(identity: RomIdentity, cheats: List<CheatItem>) {
        if (!identity.isValid) return
        val hashKey = identity.sha256
        try {
            val arr = JSONArray()
            for (c in cheats) {
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("code", c.code)
                    put("enabled", c.enabled)
                    put("isPreset", c.isPreset)
                }
                arr.put(obj)
            }
            setPrefString("cheats_$hashKey", arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cheats for $hashKey", e)
        }
    }

    fun addCheat(identity: RomIdentity, cheat: CheatItem) {
        if (!identity.isValid) return
        val current = getCheats(identity).toMutableList()
        current.add(cheat)
        saveCheats(identity, current)
        applyCheats(identity)
    }

    fun updateCheat(identity: RomIdentity, updated: CheatItem) {
        if (!identity.isValid) return
        val current = getCheats(identity).toMutableList()
        val idx = current.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            current[idx] = updated
            saveCheats(identity, current)
            applyCheats(identity)
        }
    }

    fun deleteCheat(identity: RomIdentity, cheatId: String) {
        if (!identity.isValid) return
        val current = getCheats(identity).filter { it.id != cheatId }
        saveCheats(identity, current)
        applyCheats(identity)
    }

    fun toggleCheat(identity: RomIdentity, cheatId: String, enabled: Boolean) {
        if (!identity.isValid) return
        val current = getCheats(identity).toMutableList()
        val idx = current.indexOfFirst { it.id == cheatId }
        if (idx != -1) {
            current[idx] = current[idx].copy(enabled = enabled)
            saveCheats(identity, current)
            applyCheats(identity)
        }
    }

    fun resetToDefaultPresets(identity: RomIdentity, fallbackGameKey: String? = null): List<CheatItem> {
        if (!identity.isValid) return emptyList()
        val gameContext = fallbackGameKey ?: identity.displayName
        val presets = getPresetsForGame(gameContext).map { it.copy(enabled = false) }
        saveCheats(identity, presets)
        applyCheats(identity)
        return presets
    }

    fun applyCheats(identity: RomIdentity) {
        try {
            coreCoordinator.cheatReset()
            if (!identity.isValid) return

            val cheats = getCheats(identity)
            var activeIdx = 0
            for (c in cheats) {
                if (!c.enabled) continue
                val cleanLines = c.code.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
                if (cleanLines.isNotEmpty()) {
                    val codePayload = cleanLines.joinToString("\n")
                    Log.i(TAG, "Applying cheat #${activeIdx}: '${c.name}' (${cleanLines.size} lines)")
                    coreCoordinator.cheatSet(activeIdx, true, codePayload)
                    activeIdx++
                }
            }
            Log.i(TAG, "Total active cheats applied for ${identity.shortHash}: $activeIdx")
        } catch (e: Throwable) {
            Log.e(TAG, "Error applying cheats: ${e.message}", e)
        }
    }

    // ---------------------------------------------------------
    // Backward Compatibility Overloads
    // ---------------------------------------------------------

    fun getCheats(gameKey: String): List<CheatItem> {
        return getCheats(RomIdentity.create("", gameKey), gameKey)
    }

    fun saveCheats(gameKey: String, cheats: List<CheatItem>) {
        // Read-only or safe fallback: no mutation under fake empty hash
    }

    fun addCheat(gameKey: String, cheat: CheatItem) {}
    fun updateCheat(gameKey: String, updated: CheatItem) {}
    fun deleteCheat(gameKey: String, cheatId: String) {}
    fun toggleCheat(gameKey: String, cheatId: String, enabled: Boolean) {}

    fun resetToDefaultPresets(gameKey: String): List<CheatItem> {
        return getPresetsForGame(gameKey)
    }

    fun applyCheats(gameKey: String) {
        // Safe no-op if no RomIdentity available
    }

    fun getPresetsForGame(gameKey: String): List<CheatItem> {
        val lower = gameKey.lowercase()
        val isEmeraldOrHnS = lower.contains("heart") || lower.contains("soul") || lower.contains("emer")
        val isFireRed = lower.contains("fire") || lower.contains("leaf")

        val presets = mutableListOf<CheatItem>()

        if (isEmeraldOrHnS) {
            // Heart and Soul 2.0 / Emerald Action Replay & CodeBreaker codes
            presets.add(
                CheatItem(
                    name = "Heart & Soul: Master Code (Must Enable for AR)",
                    code = "D8BAE4D9 4864DCE5\nB3C94DA9 C04D368C",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Max Money (CodeBreaker)",
                    code = "82003884 0F42\n82003886 003F",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Rare Candies in PC Item Storage (CodeBreaker)",
                    code = "82003884 002C\n820257C4 002C",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Master Balls in PC Item Storage (CodeBreaker)",
                    code = "82003884 0001\n820257C4 0001",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "100% Catch Rate (Action Replay)",
                    code = "87ACF046 F75DF7BD",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Walk Through Walls (Ghost Mode)",
                    code = "7881A409 E2026E0C\n8E883EFF 92E9660D",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Unlimited PP for All Moves (CodeBreaker)",
                    code = "42024AA4 FFFF\n00000002 0002",
                    enabled = false,
                    isPreset = true
                )
            )
        } else if (isFireRed) {
            presets.add(
                CheatItem(
                    name = "FireRed: Master Code (Must Enable for AR)",
                    code = "000014D1 000A\n1003DAE6 0007",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Max Money (CodeBreaker)",
                    code = "82003884 0F42\n82003886 003F",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Rare Candies in PC Item Storage",
                    code = "82025840 0044",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Master Balls in PC Item Storage",
                    code = "82025840 0001",
                    enabled = false,
                    isPreset = true
                )
            )
            presets.add(
                CheatItem(
                    name = "Walk Through Walls (Ghost Mode)",
                    code = "509197D3 542975F4\n78DA625D 6FA79E13",
                    enabled = false,
                    isPreset = true
                )
            )
        } else {
            presets.add(
                CheatItem(
                    name = "Example Action Replay Code",
                    code = "XXXXXXXX XXXXXXXX",
                    enabled = false,
                    isPreset = true
                )
            )
        }
        return presets
    }

    companion object {
        private const val TAG = "CheatManager"
    }
}
