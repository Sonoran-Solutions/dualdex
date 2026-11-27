package com.dualdex.nuzlocke

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persists per-ROM [NuzlockeRunState] JSON files under [baseDir]`/<romSha256>.json`
 * using atomic write-to-temp + rename and fail-safe fallback on missing or corrupt files.
 */
class NuzlockeStore(private val baseDir: File) {

    fun fileForRom(romSha256: String): File? {
        val safeKey = sanitizeRomKey(romSha256) ?: return null
        return File(baseDir, "$safeKey.json")
    }

    fun load(romSha256: String): NuzlockeRunState {
        val defaultState = NuzlockeRunState(romSha256 = romSha256)
        val file = fileForRom(romSha256) ?: return defaultState
        if (!file.isFile) return defaultState
        return try {
            val raw = file.readText(StandardCharsets.UTF_8)
            if (raw.isBlank()) return defaultState
            decode(raw, fallbackRomSha256 = romSha256)
        } catch (_: Exception) {
            defaultState
        }
    }

    fun save(state: NuzlockeRunState): Boolean {
        val target = fileForRom(state.romSha256) ?: return false
        return try {
            if (!baseDir.exists() && !baseDir.mkdirs() && !baseDir.isDirectory) {
                return false
            }
            val tmp = File(baseDir, "${target.name}.tmp")
            val payload = encode(state).toByteArray(StandardCharsets.UTF_8)
            FileOutputStream(tmp).use { out ->
                out.write(payload)
                out.flush()
                runCatching { out.fd.sync() }
            }
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                try {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                        return false
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun encode(state: NuzlockeRunState): String {
        val root = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("romSha256", state.romSha256)
            put("enabled", state.enabled)
            put("dupesClause", state.dupesClause)
            if (state.levelCap != null) {
                put("levelCap", state.levelCap)
            } else {
                put("levelCap", JSONObject.NULL)
            }
            put("encounters", JSONArray().apply {
                for (enc in state.encounters.values) {
                    put(JSONObject().apply {
                        put("areaKey", enc.areaKey)
                        put("areaName", enc.areaName)
                        put("speciesId", enc.speciesId)
                        put("speciesName", enc.speciesName)
                        put("level", enc.level)
                        put("outcome", enc.outcome.name)
                        put("recordedAtMs", enc.recordedAtMs)
                    })
                }
            })
            put("fallen", JSONArray().apply {
                for (mon in state.fallen.values) {
                    put(JSONObject().apply {
                        put("monKey", mon.monKey)
                        put("speciesId", mon.speciesId)
                        put("speciesName", mon.speciesName)
                        put("nickname", mon.nickname)
                        put("level", mon.level)
                        put("areaName", mon.areaName)
                        put("fallenAtMs", mon.fallenAtMs)
                    })
                }
            })
        }
        return root.toString(2)
    }

    internal fun decode(raw: String, fallbackRomSha256: String): NuzlockeRunState {
        val root = JSONObject(raw)
        val romSha = root.optString("romSha256", "").ifBlank { fallbackRomSha256 }
        val enabled = root.optBoolean("enabled", false)
        val dupesClause = root.optBoolean("dupesClause", true)
        val levelCap = if (root.isNull("levelCap")) null else root.optInt("levelCap", 0).takeIf { it > 0 }

        val encounters = LinkedHashMap<String, AreaEncounter>()
        val encountersArr = root.optJSONArray("encounters")
        if (encountersArr != null) {
            for (i in 0 until encountersArr.length()) {
                val obj = encountersArr.optJSONObject(i) ?: continue
                val areaKey = obj.optString("areaKey", "")
                val speciesId = obj.optInt("speciesId", 0)
                if (areaKey.isBlank() || speciesId <= 0) continue
                val outcomeName = obj.optString("outcome", "")
                val outcome = EncounterOutcome.values().firstOrNull { it.name == outcomeName } ?: continue
                encounters[areaKey] = AreaEncounter(
                    areaKey = areaKey,
                    areaName = obj.optString("areaName", "Area $areaKey"),
                    speciesId = speciesId,
                    speciesName = obj.optString("speciesName", "#$speciesId"),
                    level = obj.optInt("level", 0).coerceAtLeast(0),
                    outcome = outcome,
                    recordedAtMs = obj.optLong("recordedAtMs", 0L)
                )
            }
        }

        val fallen = LinkedHashMap<String, FallenMon>()
        val fallenArr = root.optJSONArray("fallen")
        if (fallenArr != null) {
            for (i in 0 until fallenArr.length()) {
                val obj = fallenArr.optJSONObject(i) ?: continue
                val monKey = obj.optString("monKey", "")
                val speciesId = obj.optInt("speciesId", 0)
                if (monKey.isBlank() || speciesId <= 0) continue
                val speciesName = obj.optString("speciesName", "#$speciesId")
                fallen[monKey] = FallenMon(
                    monKey = monKey,
                    speciesId = speciesId,
                    speciesName = speciesName,
                    nickname = obj.optString("nickname", speciesName).ifBlank { speciesName },
                    level = obj.optInt("level", 0).coerceAtLeast(0),
                    areaName = obj.optString("areaName", "Unknown Area"),
                    fallenAtMs = obj.optLong("fallenAtMs", 0L)
                )
            }
        }

        return NuzlockeRunState(
            romSha256 = romSha,
            enabled = enabled,
            dupesClause = dupesClause,
            levelCap = levelCap,
            encounters = encounters,
            fallen = fallen
        )
    }

    private fun sanitizeRomKey(romSha256: String): String? {
        val cleaned = romSha256.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }
        return cleaned.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
