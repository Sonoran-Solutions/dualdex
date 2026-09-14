package com.dualdex.romhack

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

enum class ProfileMatchMethod {
    EXACT_SHA256,
    FILENAME_KEYWORD,
    HEADER_TITLE,
    BASE_GAME_FALLBACK,
    DEFAULT_FALLBACK
}

data class ProfileDetectionResult(
    val profile: RomHackProfile,
    val matchMethod: ProfileMatchMethod,
    val sha256: String
) {
    val isExactSha256: Boolean
        get() = matchMethod == ProfileMatchMethod.EXACT_SHA256
}

object RomHackDetector {

    fun detectProfile(romFile: File, profiles: List<RomHackProfile>, preferredTitle: String? = null): RomHackProfile {
        return detectProfileWithConfidence(romFile, profiles, preferredTitle).profile
    }

    fun detectProfileWithConfidence(
        romFile: File,
        profiles: List<RomHackProfile>,
        preferredTitle: String? = null
    ): ProfileDetectionResult {
        if (!romFile.exists()) {
            return ProfileDetectionResult(RomHackProfile.DEFAULT_FIRERED, ProfileMatchMethod.DEFAULT_FALLBACK, "")
        }

        val headerBytes = ByteArray(192)
        var sha256 = ""
        try {
            FileInputStream(romFile).use { fis ->
                var offset = 0
                while (offset < headerBytes.size) {
                    val read = fis.read(headerBytes, offset, headerBytes.size - offset)
                    if (read == -1) break
                    offset += read
                }
            }
            sha256 = calculateSha256(romFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return detectProfileFromBytesWithConfidence(
            headerBytes,
            sha256,
            profiles,
            preferredTitle ?: romFile.name
        )
    }

    fun detectProfileFromBytes(
        headerBytes: ByteArray,
        sha256: String = "",
        profiles: List<RomHackProfile>,
        fileName: String = ""
    ): RomHackProfile {
        return detectProfileFromBytesWithConfidence(headerBytes, sha256, profiles, fileName).profile
    }

    fun detectProfileFromBytesWithConfidence(
        headerBytes: ByteArray,
        sha256: String = "",
        profiles: List<RomHackProfile>,
        fileName: String = ""
    ): ProfileDetectionResult {
        // Extract 12-byte ROM title at offset 0xA0 (160)
        val titleStr = if (headerBytes.size >= 172) {
            val titleBytes = headerBytes.sliceArray(160 until 172)
            String(titleBytes, Charsets.US_ASCII).trim().replace("\u0000", "")
        } else ""

        // Extract 4-byte Game Code at offset 0xAC (172)
        val codeStr = if (headerBytes.size >= 176) {
            val codeBytes = headerBytes.sliceArray(172 until 176)
            String(codeBytes, Charsets.US_ASCII).trim().replace("\u0000", "")
        } else ""

        // 1. Check SHA-256 exact match
        if (sha256.isNotBlank()) {
            val lowerHash = sha256.lowercase()
            for (p in profiles) {
                if (p.sha256Hashes.contains(lowerHash)) {
                    return ProfileDetectionResult(p, ProfileMatchMethod.EXACT_SHA256, sha256)
                }
            }
        }

        // 1.5. Check filename / preferred title for hack keywords (e.g. "Heart and Soul", "Ghost Grey")
        if (fileName.isNotBlank()) {
            for (p in profiles) {
                for (ht in p.headerTitles) {
                    if (fileName.contains(ht, ignoreCase = true)) {
                        return ProfileDetectionResult(p, ProfileMatchMethod.FILENAME_KEYWORD, sha256)
                    }
                }
            }
        }

        // 2. Check header titles and game codes
        for (p in profiles) {
            for (ht in p.headerTitles) {
                if (titleStr.contains(ht, ignoreCase = true) || codeStr.contains(ht, ignoreCase = true)) {
                    return ProfileDetectionResult(p, ProfileMatchMethod.HEADER_TITLE, sha256)
                }
            }
        }

        // 3. Fallback matching base game codes
        if (codeStr.startsWith("BPR") || titleStr.contains("FIRE", ignoreCase = true)) {
            val p = profiles.firstOrNull { it.id == "vanilla_firered" } ?: RomHackProfile.DEFAULT_FIRERED
            return ProfileDetectionResult(p, ProfileMatchMethod.BASE_GAME_FALLBACK, sha256)
        }
        if (codeStr.startsWith("BPE") || titleStr.contains("EMER", ignoreCase = true)) {
            val p = profiles.firstOrNull { it.id == "vanilla_emerald" } ?: RomHackProfile.DEFAULT_FIRERED
            return ProfileDetectionResult(p, ProfileMatchMethod.BASE_GAME_FALLBACK, sha256)
        }

        val p = profiles.firstOrNull() ?: RomHackProfile.DEFAULT_FIRERED
        return ProfileDetectionResult(p, ProfileMatchMethod.DEFAULT_FALLBACK, sha256)
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
