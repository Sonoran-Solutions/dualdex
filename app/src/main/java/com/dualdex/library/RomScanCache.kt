package com.dualdex.library

import com.dualdex.romhack.RomCompatibilityStatus
import com.dualdex.romhack.RomHackProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** One scanned ROM's verdict. Valid only while [size] and [mtime] still match the file. */
data class ScanVerdict(
    val path: String,
    val size: Long,
    val mtime: Long,
    val sha256: String,
    val status: RomCompatibilityStatus,
    val profileName: String
)

/**
 * Per-file scan verdicts keyed by path and invalidated by size+mtime, so a rescan only hashes
 * new or changed ROMs. Profile changes invalidate all verdicts. Persisted as one small JSON file.
 */
class RomScanCache(private val file: File, profiles: List<RomHackProfile>) {
    private val profileFingerprint = fingerprint(profiles)
    private val entries = mutableMapOf<String, ScanVerdict>()

    init {
        runCatching {
            if (file.exists()) {
                val json = JSONObject(file.readText())
                if (json.optInt("version") != FORMAT_VERSION ||
                    json.optString("profileFingerprint") != profileFingerprint) return@runCatching
                val cached = json.getJSONObject("entries")
                for (key in cached.keys()) {
                    val o = cached.getJSONObject(key)
                    val status = runCatching { RomCompatibilityStatus.valueOf(o.getString("status")) }.getOrNull() ?: continue
                    entries[key] = ScanVerdict(key, o.getLong("size"), o.getLong("mtime"), o.getString("sha256"), status, o.optString("profile"))
                }
            }
        }
    }

    fun lookup(path: String, size: Long, mtime: Long): ScanVerdict? =
        entries[path]?.takeIf { it.size == size && it.mtime == mtime }

    fun put(verdict: ScanVerdict) { entries[verdict.path] = verdict }

    /** Drop entries for files that no longer exist in the scan, then write to disk. */
    fun retainAndSave(seenPaths: Set<String>) {
        entries.keys.retainAll(seenPaths)
        val json = JSONObject()
        entries.values.forEach {
            json.put(it.path, JSONObject()
                .put("size", it.size).put("mtime", it.mtime).put("sha256", it.sha256)
                .put("status", it.status.name).put("profile", it.profileName))
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject().put("version", FORMAT_VERSION).put("profileFingerprint", profileFingerprint).put("entries", json).toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    companion object {
        // Profile order is significant: detection chooses the first matching profile.
        internal fun fingerprint(profiles: List<RomHackProfile>): String {
            val identity = JSONArray(profiles.map { p ->
                JSONArray(listOf(
                    p.id, p.name, p.baseGame, JSONArray(p.headerTitles),
                    JSONArray(p.sha256Hashes.map { it.lowercase() }.sorted()), p.gameDataPackId,
                    p.isVerified, p.memoryLayoutVerified, p.interactiveControlsVerified,
                    p.battleStateReadVerified, p.battleUiVerified, p.commandCursorVerified,
                    p.moveCursorVerified, p.partyCursorVerified, p.moveSelectionVerified,
                    p.partySwitchVerified, p.partyActionMenuVerified
                ))
            }).toString()
            return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        const val FORMAT_VERSION = 1
        const val MAX_DEPTH = 3
        private val ROM_EXTENSIONS = setOf("gba", "bin", "agb")
        fun isRomName(name: String) = name.substringAfterLast('.', "").lowercase() in ROM_EXTENSIONS
    }
}
