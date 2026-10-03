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
    var coreCoordinator: LibretroCoreCoordinator = LibretroCoreCoordinator.defaultInstance,
    private val presetPolicy: CheatPresetPolicy = CheatPresetPolicy.production
) {
    private val prefs by lazy { context?.getSharedPreferences("dualdex_cheats", Context.MODE_PRIVATE) }
    private fun read(key: String): String? = memoryStorage?.get(key) ?: prefs?.getString(key, null)
    private fun write(key: String, value: String) {
        if (memoryStorage != null) memoryStorage[key] = value
        else prefs?.edit()?.putString(key, value)?.apply()
    }

    /** All storage reads (including quarantine writes), edits and dispatch share one
     * transaction across instances. Always core -> storage, matching ROM switching.
     * Locked helpers never acquire another coordinator while holding storageLock. */
    private fun <T> transaction(block: (LibretroCoreCoordinator) -> T): T {
        val core = coreCoordinator
        return core.executeExclusive {
            synchronized(storageLock) { block(core) }
        }
    }

    private fun operation(block: (LibretroCoreCoordinator) -> CheatResult): CheatResult = try {
        transaction(block)
    } catch (e: Exception) {
        Log.e(TAG, "Error changing or applying cheat settings", e)
        CheatResult(false, "Cheat settings could not be changed or applied.")
    }

    fun getPresets(identity: RomIdentity): List<CheatItem> = presetPolicy.presets(identity)

    private fun sanitize(identity: RomIdentity, item: CheatItem): CheatItem {
        val reason = when {
            item.disabledReason == UNKNOWN_SOURCE -> UNKNOWN_SOURCE
            item.isPreset && !presetPolicy.approves(identity, item) -> UNVERIFIED
            CheatCodePayload.normalize(item.code) == null -> MALFORMED
            else -> null
        }
        return item.copy(enabled = item.enabled && reason == null, disabledReason = reason)
    }

    fun getCheats(identity: RomIdentity, fallbackGameKey: String? = null): List<CheatItem> = try {
        transaction { readCheatsLocked(identity) }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading cheat settings", e)
        emptyList()
    }

    /** Called only inside transaction: a read can also persist legacy quarantine. */
    private fun readCheatsLocked(identity: RomIdentity): List<CheatItem> {
        if (!identity.isValid) return emptyList()
        val key = "cheats_${identity.sha256.lowercase()}"
        val json = read(key) ?: return getPresets(identity)
        return try {
            val arr = JSONArray(json)
            val original = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                // Old explicit false is user-entered custom provenance. Anything else is
                // quarantined, retaining text and identity rather than granting custom status.
                val source = obj.opt("isPreset")
                CheatItem(
                    id = obj.optString("id"), name = obj.optString("name"),
                    code = obj.optString("code"), enabled = obj.optBoolean("enabled", false),
                    isPreset = source != false,
                    disabledReason = if (source !is Boolean) UNKNOWN_SOURCE
                        else obj.optString("disabledReason").takeIf { it.isNotBlank() }
                )
            }
            val safe = original.map { sanitize(identity, it) }
            if (safe != original) persist(identity, safe)
            safe
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cheats for ${identity.shortHash}", e)
            emptyList()
        }
    }

    private fun persist(identity: RomIdentity, cheats: List<CheatItem>) {
        val arr = JSONArray()
        cheats.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("code", c.code)
                put("enabled", c.enabled); put("isPreset", c.isPreset)
                put("disabledReason", c.disabledReason ?: "")
            })
        }
        write("cheats_${identity.sha256.lowercase()}", arr.toString())
    }

    /** Explicit whole-list replacement. Read/modify operations use the atomic methods below. */
    fun saveCheats(identity: RomIdentity, cheats: List<CheatItem>): CheatResult = operation {
        saveCheatsLocked(identity, cheats)
    }

    private fun saveCheatsLocked(identity: RomIdentity, cheats: List<CheatItem>): CheatResult {
        if (!identity.isValid) return CheatResult.stale
        // An unreadable store must not be overwritten by an apparent empty list.
        val raw = read("cheats_${identity.sha256.lowercase()}")
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) arr.getJSONObject(i)
            } catch (_: Exception) {
                return CheatResult(false, "Saved cheat data is unreadable; retained without changes.")
            }
        }
        val previous = readCheatsLocked(identity).associateBy { it.id }
        persist(identity, cheats.map { candidate ->
            val old = previous[candidate.id]
            // Editing/saving cannot launder a quarantined built-in into a custom entry.
            sanitize(identity, if (old?.isPreset == true) candidate.copy(
                isPreset = true, disabledReason = old.disabledReason
            ) else candidate)
        })
        return CheatResult(true, "Cheat settings saved.")
    }

    fun addCheat(identity: RomIdentity, cheat: CheatItem): CheatResult = operation { core ->
        if (!identity.isValid) return@operation CheatResult.stale
        val current = readCheatsLocked(identity)
        if (current.any { it.id == cheat.id }) return@operation CheatResult(false, "Cheat identity already exists.")
        val safe = sanitize(identity, cheat)
        val saved = saveCheatsLocked(identity, current + safe)
        if (!saved.accepted) return@operation saved
        val result = applyCheatsLocked(core, identity)
        if (safe.disabledReason != null) CheatResult(false, safe.disabledReason) else result
    }

    fun updateCheat(identity: RomIdentity, updated: CheatItem): CheatResult = operation { core ->
        updateCheatLocked(core, identity, updated)
    }

    private fun updateCheatLocked(
        core: LibretroCoreCoordinator, identity: RomIdentity, updated: CheatItem
    ): CheatResult {
        val current = readCheatsLocked(identity)
        if (current.none { it.id == updated.id }) return CheatResult(false, "Cheat no longer exists.")
        val saved = saveCheatsLocked(identity, current.map { if (it.id == updated.id) updated else it })
        if (!saved.accepted) return saved
        val safe = readCheatsLocked(identity).first { it.id == updated.id }
        val result = applyCheatsLocked(core, identity)
        return if (safe.disabledReason != null) CheatResult(false, safe.disabledReason) else result
    }

    fun deleteCheat(identity: RomIdentity, cheatId: String): CheatResult = operation { core ->
        val saved = saveCheatsLocked(identity, readCheatsLocked(identity).filter { it.id != cheatId })
        if (!saved.accepted) return@operation saved
        applyCheatsLocked(core, identity)
    }

    fun toggleCheat(identity: RomIdentity, cheatId: String, enabled: Boolean): CheatResult = operation { core ->
        val item = readCheatsLocked(identity).firstOrNull { it.id == cheatId }
            ?: return@operation CheatResult(false, "Cheat no longer exists.")
        updateCheatLocked(core, identity, item.copy(enabled = enabled))
    }

    fun disableAllCheats(identity: RomIdentity): CheatResult = operation { core ->
        val saved = saveCheatsLocked(identity, readCheatsLocked(identity).map { it.copy(enabled = false) })
        if (!saved.accepted) return@operation saved
        applyCheatsLocked(core, identity)
    }

    fun loadPresets(identity: RomIdentity): CheatResult = operation { core ->
        loadPresetsLocked(core, identity)
    }

    private fun loadPresetsLocked(core: LibretroCoreCoordinator, identity: RomIdentity): CheatResult {
        if (!core.isCheatRom(identity)) return CheatResult.stale
        val presets = getPresets(identity)
        if (presets.isEmpty()) return CheatResult.unavailable
        val saved = saveCheatsLocked(identity, presets)
        if (!saved.accepted) return saved
        return applyCheatsLocked(core, identity)
    }

    fun resetToDefaultPresets(identity: RomIdentity, fallbackGameKey: String? = null): List<CheatItem> = try {
        transaction { core ->
            if (loadPresetsLocked(core, identity).accepted) readCheatsLocked(identity) else emptyList()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading presets", e)
        emptyList()
    }

    fun applyCheats(identity: RomIdentity): CheatResult = operation { core ->
        applyCheatsLocked(core, identity)
    }

    /** Core and shared storage locks are already held, including during automatic ROM load. */
    private fun applyCheatsLocked(core: LibretroCoreCoordinator, identity: RomIdentity): CheatResult {
        if (!core.isCheatRom(identity)) return CheatResult.stale
        val cheats = readCheatsLocked(identity)
        core.cheatReset()
        var index = 0
        cheats.forEach { c ->
            if (c.enabled && c.disabledReason == null &&
                (!c.isPreset || presetPolicy.approves(identity, c))) {
                CheatCodePayload.normalize(c.code)?.let { payload ->
                    core.cheatSet(index++, true, payload)
                }
            }
        }
        return CheatResult(true, "Cheat settings applied ($index enabled).")
    }

    // Legacy name-only APIs have no identity or authorization; never select defaults.
    fun getPresetsForGame(gameKey: String): List<CheatItem> = emptyList()
    fun getCheats(gameKey: String): List<CheatItem> = emptyList()
    fun saveCheats(gameKey: String, cheats: List<CheatItem>) {}
    fun addCheat(gameKey: String, cheat: CheatItem) {}
    fun updateCheat(gameKey: String, updated: CheatItem) {}
    fun deleteCheat(gameKey: String, cheatId: String) {}
    fun toggleCheat(gameKey: String, cheatId: String, enabled: Boolean) {}
    fun resetToDefaultPresets(gameKey: String): List<CheatItem> = emptyList()
    fun applyCheats(gameKey: String) {}

    companion object {
        // Process-wide, not per instance/coordinator/store: screen and session managers
        // share Android preferences, and even separately injected coordinators must agree.
        private val storageLock = Any()
        private const val TAG = "CheatManager"
        const val UNVERIFIED = "Disabled: no verified built-in approval for this exact ROM and code."
        const val UNKNOWN_SOURCE = "Disabled: saved cheat provenance is missing or ambiguous."
        const val MALFORMED = "Disabled: empty, placeholder or malformed code."
    }
}
