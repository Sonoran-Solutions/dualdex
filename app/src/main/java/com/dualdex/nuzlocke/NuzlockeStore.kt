package com.dualdex.nuzlocke

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Persists per-ROM [NuzlockeRunState] JSON files under [baseDir]`/<romSha256>.json`
 * using strict canonical SHA-256 validation, fsync'd write-to-temp + atomic rename +
 * parent-directory fsync, and fail-closed rejection on mismatched or corrupt files.
 */
class NuzlockeStore internal constructor(
    private val baseDir: File,
    private val fileOps: FileOps = DefaultFileOps
) {
    constructor(baseDir: File) : this(baseDir, DefaultFileOps)

    internal interface FileOps {
        fun forceFile(channel: FileChannel)
        fun atomicMove(source: Path, target: Path)
        fun forceDir(dir: Path)
    }

    internal object DefaultFileOps : FileOps {
        override fun forceFile(channel: FileChannel) {
            channel.force(true)
        }

        override fun atomicMove(source: Path, target: Path) {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }

        override fun forceDir(dir: Path) {
            FileChannel.open(dir, StandardOpenOption.READ).use { dirChannel ->
                dirChannel.force(true)
            }
        }
    }

    internal val directory: File get() = baseDir

    private val storeLock = Any()

    fun fileForRom(romSha256: String): File? {
        val canonical = canonicalSha256(romSha256) ?: return null
        return File(baseDir, "$canonical.json")
    }

    /**
     * Loads and validates the persisted state for [romSha256]. Returns `null` if the ROM hash is
     * invalid, the file is missing/corrupt, or the embedded `romSha256` does not match [romSha256].
     */
    fun loadOrNull(romSha256: String): NuzlockeRunState? {
        val canonical = canonicalSha256(romSha256) ?: return null
        val file = File(baseDir, "$canonical.json")
        if (!file.isFile) return null
        return try {
            val raw = file.readText(StandardCharsets.UTF_8)
            if (raw.isBlank()) return null
            decode(raw, expectedRomSha256 = canonical)
        } catch (_: Exception) {
            null
        }
    }

    fun load(romSha256: String): NuzlockeRunState {
        val canonical = canonicalSha256(romSha256) ?: return NuzlockeRunState(romSha256 = romSha256)
        return loadOrNull(canonical) ?: NuzlockeRunState(romSha256 = canonical)
    }

    /**
     * Atomically writes [state] strictly to the destination bound by [expectedRomSha256].
     * Refuses if [expectedRomSha256] or `state.romSha256` is not a valid canonical SHA-256 or if
     * they do not match. Never swallows fsync errors and never falls back to a non-atomic move.
     */
    fun saveResult(expectedRomSha256: String, state: NuzlockeRunState): Result<Unit> {
        val canonicalExpected = canonicalSha256(expectedRomSha256)
            ?: return Result.failure(IllegalArgumentException("Invalid target ROM SHA-256"))
        val canonicalState = canonicalSha256(state.romSha256)
            ?: return Result.failure(IllegalArgumentException("Invalid state ROM SHA-256"))
        if (canonicalState != canonicalExpected) {
            return Result.failure(
                IllegalArgumentException(
                    "State ROM SHA-256 mismatch: expected $canonicalExpected, got $canonicalState"
                )
            )
        }

        val canonicalStateObj = if (state.romSha256 == canonicalExpected) {
            state
        } else {
            state.copy(romSha256 = canonicalExpected)
        }

        synchronized(storeLock) {
            val target = File(baseDir, "$canonicalExpected.json")
            val tmp = File(baseDir, "${target.name}.tmp")
            return try {
                if (!baseDir.exists() && !baseDir.mkdirs() && !baseDir.isDirectory) {
                    return Result.failure(IOException("Unable to create Nuzlocke directory: $baseDir"))
                }
                if (!baseDir.isDirectory) {
                    return Result.failure(IOException("Nuzlocke path is not a directory: $baseDir"))
                }
                val payload = encode(canonicalStateObj).toByteArray(StandardCharsets.UTF_8)
                FileChannel.open(
                    tmp.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                ).use { channel ->
                    val buffer = ByteBuffer.wrap(payload)
                    while (buffer.hasRemaining()) {
                        channel.write(buffer)
                    }
                    fileOps.forceFile(channel)
                }
                fileOps.atomicMove(tmp.toPath(), target.toPath())
                fileOps.forceDir(baseDir.toPath())
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                runCatching { Files.deleteIfExists(tmp.toPath()) }
            }
        }
    }

    fun save(expectedRomSha256: String, state: NuzlockeRunState): Boolean =
        saveResult(expectedRomSha256, state).isSuccess

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

    internal fun decode(raw: String, expectedRomSha256: String): NuzlockeRunState? {
        val canonicalExpected = canonicalSha256(expectedRomSha256) ?: return null
        val root = JSONObject(raw)
        if (root.has("schemaVersion") && root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            return null
        }
        val embeddedSha = canonicalSha256(root.optString("romSha256", "")) ?: return null
        if (embeddedSha != canonicalExpected) return null

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
            romSha256 = canonicalExpected,
            enabled = enabled,
            dupesClause = dupesClause,
            levelCap = levelCap,
            encounters = encounters,
            fallen = fallen
        )
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

        fun canonicalSha256(romSha256: String?): String? {
            val normalized = romSha256?.trim()?.lowercase() ?: return null
            return normalized.takeIf { SHA256_REGEX.matches(it) }
        }
    }
}
