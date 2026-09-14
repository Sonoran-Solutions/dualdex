package com.dualdex.emulator.storage

import org.json.JSONObject

enum class MirrorStatus {
    IN_SYNC,
    OUT_OF_SYNC,
    UNAVAILABLE,
    PERMISSION_REVOKED,
    FAILED
}

sealed class SaveWriteResult {
    data class Success(
        val canonicalWritten: Boolean,
        val mirrorStatus: MirrorStatus
    ) : SaveWriteResult()

    data class Failure(
        val reason: String
    ) : SaveWriteResult()
}

data class RomSaveMetadata(
    val sha256: String,
    val displayName: String,
    val lastKnownProfileId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val saveGeneration: Long = 1L,
    val mirrorStatus: MirrorStatus = MirrorStatus.UNAVAILABLE
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("sha256", sha256)
            put("displayName", displayName)
            put("lastKnownProfileId", lastKnownProfileId ?: "")
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("saveGeneration", saveGeneration)
            put("mirrorStatus", mirrorStatus.name)
        }.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): RomSaveMetadata? {
            return try {
                val obj = JSONObject(jsonStr)
                RomSaveMetadata(
                    sha256 = obj.getString("sha256"),
                    displayName = obj.optString("displayName", "Unknown Game"),
                    lastKnownProfileId = obj.optString("lastKnownProfileId").ifEmpty { null },
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    saveGeneration = obj.optLong("saveGeneration", 1L),
                    mirrorStatus = try {
                        MirrorStatus.valueOf(obj.optString("mirrorStatus", MirrorStatus.UNAVAILABLE.name))
                    } catch (_: Exception) {
                        MirrorStatus.UNAVAILABLE
                    }
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class LegacyCandidate(
    val sourceFile: java.io.File,
    val baseName: String,
    val suggestedTitle: String,
    val targetFileName: String,
    val sizeBytes: Long,
    val isAssigned: Boolean = false,
    val assignedRomHash: String? = null
)
