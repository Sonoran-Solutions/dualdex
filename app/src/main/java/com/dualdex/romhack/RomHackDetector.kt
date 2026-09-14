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

object RomHackDetector {

    /**
     * Structural inspection of a ROM that decides the runtime compatibility status.
     *
     * Only an exact SHA-256 match against a supported profile whose verification flags are set can
     * produce [RomCompatibilityStatus.VERIFIED]. Filename, header title, and base-game heuristics
     * can recognize a ROM ([RomCompatibilityStatus.RECOGNIZED_UNVERIFIED]) but never grant the same
     * trust: memory layouts differ between builds of the same hack, so bytes are the only evidence
     * DualDex accepts. Anything else is [RomCompatibilityStatus.UNSUPPORTED] and is never mapped
     * onto a FireRed profile.
     */
    fun detectCompatibility(
        romFile: File,
        profiles: List<RomHackProfile>,
        preferredTitle: String? = null
    ): RomCompatibility {
        if (!romFile.exists()) {
            return RomCompatibility.unsupported(
                sha256 = "",
                reason = "ROM file does not exist"
            )
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

        return detectCompatibilityFromBytes(
            headerBytes,
            sha256,
            profiles,
            preferredTitle ?: romFile.name
        )
    }

    fun detectCompatibilityFromBytes(
        headerBytes: ByteArray,
        sha256: String = "",
        profiles: List<RomHackProfile>,
        fileName: String = ""
    ): RomCompatibility {
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

        // 1. Exact SHA-256 is the only path to VERIFIED.
        if (sha256.isNotBlank()) {
            val lowerHash = sha256.lowercase()
            val exact = profiles.firstOrNull { it.sha256Hashes.any { h -> h.equals(lowerHash, ignoreCase = true) } }
            if (exact != null) {
                return if (exact.isVerified && exact.memoryLayoutVerified) {
                    RomCompatibility.verified(exact, sha256)
                } else {
                    RomCompatibility.recognizedUnverified(
                        profile = exact,
                        sha256 = sha256,
                        matchMethod = ProfileMatchMethod.EXACT_SHA256,
                        reason = "Exact SHA-256 matched ${exact.id}, but its verification flags are incomplete"
                    )
                }
            }
        }

        // 2. Filename / preferred title keywords (e.g. "Heart and Soul", "Ghost Grey").
        //    Recognition only: an unknown build of a known hack must not inherit its layout trust.
        if (fileName.isNotBlank()) {
            for (p in profiles) {
                val matches = p.headerTitles.any { ht -> fileName.contains(ht, ignoreCase = true) } ||
                    (p.name.isNotBlank() && fileName.contains(p.name, ignoreCase = true))
                if (matches) {
                    return RomCompatibility.recognizedUnverified(
                        profile = p,
                        sha256 = sha256,
                        matchMethod = ProfileMatchMethod.FILENAME_KEYWORD,
                        reason = "Filename suggests ${p.id}, but the exact ROM bytes are not verified"
                    )
                }
            }
        }

        // 3. Header titles and game codes embedded in the ROM header.
        for (p in profiles) {
            for (ht in p.headerTitles) {
                if (titleStr.contains(ht, ignoreCase = true) || codeStr.contains(ht, ignoreCase = true)) {
                    return RomCompatibility.recognizedUnverified(
                        profile = p,
                        sha256 = sha256,
                        matchMethod = ProfileMatchMethod.HEADER_TITLE,
                        reason = "ROM header suggests ${p.id}, but the exact ROM bytes are not verified"
                    )
                }
            }
        }

        // 4. Base-game identification. This states which base game the ROM appears to be derived
        //    from; it never selects a hack profile and never unlocks a memory layout.
        val baseGameProfile = when {
            codeStr.startsWith("BPR") || titleStr.contains("FIRE", ignoreCase = true) ->
                profiles.firstOrNull { it.id == "vanilla_firered" }
            codeStr.startsWith("BPE") || titleStr.contains("EMER", ignoreCase = true) ->
                profiles.firstOrNull { it.id == "vanilla_emerald" }
            else -> null
        }
        if (baseGameProfile != null) {
            return RomCompatibility.recognizedUnverified(
                profile = baseGameProfile,
                sha256 = sha256,
                matchMethod = ProfileMatchMethod.BASE_GAME_FALLBACK,
                reason = "ROM header matches the ${baseGameProfile.baseGame} base game, but the exact ROM bytes are not verified"
            )
        }

        // 5. Nothing trustworthy. Never substitute the first loaded profile or a FireRed default.
        return RomCompatibility.unsupported(
            sha256 = sha256,
            matchMethod = ProfileMatchMethod.DEFAULT_FALLBACK
        )
    }

    /**
     * Presentation-only convenience for callers that just need a display identity.
     *
     * Returns [RomHackProfile.UNSUPPORTED] for unknown ROMs. Callers that gate memory access must
     * use [detectCompatibility] and [RomCompatibility.mayReadLiveMemory] instead.
     */
    fun detectProfile(romFile: File, profiles: List<RomHackProfile>, preferredTitle: String? = null): RomHackProfile {
        return detectCompatibility(romFile, profiles, preferredTitle).profile
    }

    @Deprecated("Use detectCompatibility; compatibility status, not a profile, is the trust decision")
    fun detectProfileWithConfidence(
        romFile: File,
        profiles: List<RomHackProfile>,
        preferredTitle: String? = null
    ): RomCompatibility = detectCompatibility(romFile, profiles, preferredTitle)

    @Deprecated("Use detectCompatibilityFromBytes; compatibility status, not a profile, is the trust decision")
    fun detectProfileFromBytes(
        headerBytes: ByteArray,
        sha256: String = "",
        profiles: List<RomHackProfile>,
        fileName: String = ""
    ): RomHackProfile {
        return detectCompatibilityFromBytes(headerBytes, sha256, profiles, fileName).profile
    }

    @Deprecated("Use detectCompatibilityFromBytes; compatibility status, not a profile, is the trust decision")
    fun detectProfileFromBytesWithConfidence(
        headerBytes: ByteArray,
        sha256: String = "",
        profiles: List<RomHackProfile>,
        fileName: String = ""
    ): RomCompatibility = detectCompatibilityFromBytes(headerBytes, sha256, profiles, fileName)

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
