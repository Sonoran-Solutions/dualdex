package com.dualdex.romhack

/**
 * Trust for the ROM bytes that are currently running, rather than for a profile in isolation.
 * A profile may describe a verified build, but only an exact hash match makes memory-derived
 * presentation verified at runtime.
 */
data class RuntimeRomTrust(
    val matchMethod: ProfileMatchMethod = ProfileMatchMethod.DEFAULT_FALLBACK,
    val detectedSha256: String = "",
    val activeRomSha256: String? = null,
    val profileVerified: Boolean = false,
    val memoryLayoutVerified: Boolean = false,
    val profileSha256Hashes: List<String> = emptyList()
) {
    val exactRuntimeVerified: Boolean
        get() = matchMethod == ProfileMatchMethod.EXACT_SHA256 &&
            profileVerified &&
            memoryLayoutVerified &&
            !detectedSha256.isBlank() &&
            detectedSha256.equals(activeRomSha256, ignoreCase = true) &&
            profileSha256Hashes.any { it.equals(activeRomSha256, ignoreCase = true) }

    companion object {
        fun from(
            detection: ProfileDetectionResult,
            activeRomSha256: String?
        ): RuntimeRomTrust = RuntimeRomTrust(
            matchMethod = detection.matchMethod,
            detectedSha256 = detection.sha256,
            activeRomSha256 = activeRomSha256,
            profileVerified = detection.profile.isVerified,
            memoryLayoutVerified = detection.profile.memoryLayoutVerified,
            profileSha256Hashes = detection.profile.sha256Hashes
        )
    }
}
