package com.dualdex.emulator

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class RomIdentity(
    val sha256: String,
    val displayName: String,
    val storageKey: String
) {
    val shortHash: String
        get() = if (sha256.length >= 12) sha256.take(12) else sha256.padEnd(12, '0')

    val sanitizedTitle: String
        get() = sanitizeTitle(displayName)

    companion object {
        private const val MAX_TITLE_LENGTH = 40
        private const val DEFAULT_TITLE = "game"
        private const val DEFAULT_SHORT_HASH = "000000000000"

        fun sanitizeTitle(title: String): String {
            val clean = title.trim()
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
            return if (clean.isEmpty()) DEFAULT_TITLE else clean.take(MAX_TITLE_LENGTH)
        }

        fun create(sha256: String, displayName: String): RomIdentity {
            val lowerHash = sha256.trim().lowercase()
            val cleanTitle = sanitizeTitle(displayName)
            val short = if (lowerHash.length >= 12) lowerHash.take(12) else DEFAULT_SHORT_HASH
            val key = "${cleanTitle}__${short}"
            return RomIdentity(
                sha256 = lowerHash,
                displayName = displayName.ifBlank { "Unknown Game" },
                storageKey = key
            )
        }

        fun calculateSha256(file: File): String {
            if (!file.exists() || file.length() == 0L) return ""
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun calculateSha256(bytes: ByteArray): String {
            if (bytes.isEmpty()) return ""
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            return hash.joinToString("") { "%02x".format(it) }
        }

        fun fromFile(file: File, preferredTitle: String? = null): RomIdentity {
            val hash = calculateSha256(file)
            val name = preferredTitle?.ifBlank { null } ?: file.nameWithoutExtension.ifEmpty { DEFAULT_TITLE }
            return create(hash, name)
        }

        fun fromBytes(bytes: ByteArray, preferredTitle: String? = null): RomIdentity {
            val hash = calculateSha256(bytes)
            val name = preferredTitle?.ifBlank { null } ?: DEFAULT_TITLE
            return create(hash, name)
        }
    }
}
