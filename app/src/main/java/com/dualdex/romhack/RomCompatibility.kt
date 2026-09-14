package com.dualdex.romhack

/**
 * How far DualDex is allowed to trust the ROM bytes that are currently loaded.
 *
 * This is the single source of truth for the beta safety rule: a ROM must never receive
 * confident-looking memory-derived companion data unless DualDex has enough runtime evidence to
 * trust the relevant memory layout. Generic GBA emulation, battery saves, and save states stay
 * available in every state.
 */
enum class RomCompatibilityStatus {
    /** Exact SHA-256 matched a supported profile whose verification flags are set. */
    VERIFIED,

    /**
     * Filename/header/base-game evidence suggests a known game, engine, or profile, but the exact
     * ROM bytes are not verified. Presentation metadata may be shown; profile-dependent memory
     * parsing stays disabled.
     */
    RECOGNIZED_UNVERIFIED,

    /** No trustworthy supported profile/version. Emulation only. */
    UNSUPPORTED
}

/**
 * Result of inspecting a ROM: what DualDex believes it is, and what it may therefore read.
 *
 * [profile] is always populated so existing presentation callers keep a non-null identity, but it
 * is never a fabricated FireRed substitute: [UNSUPPORTED][RomCompatibilityStatus.UNSUPPORTED]
 * resolves to [RomHackProfile.UNSUPPORTED], which claims no game, no layout, and no hash.
 */
data class RomCompatibility(
    val status: RomCompatibilityStatus,
    /** Identity/profile used for presentation. Never grants memory access by itself. */
    val profile: RomHackProfile,
    /** Candidate profile when the ROM was recognized without an exact hash; null when unsupported. */
    val detectedProfile: RomHackProfile?,
    val matchMethod: ProfileMatchMethod,
    val sha256: String,
    /** Human-readable explanation of why this status was assigned. */
    val reason: String
) {
    val isVerified: Boolean
        get() = status == RomCompatibilityStatus.VERIFIED

    /**
     * True only when profile-dependent memory parsing is allowed.
     *
     * Fail-closed by construction: an exact-hash verification is required, and the profile must
     * still assert both its own verification and its memory layout.
     */
    val mayReadLiveMemory: Boolean
        get() = status == RomCompatibilityStatus.VERIFIED &&
            profile.isVerified &&
            profile.memoryLayoutVerified

    companion object {
        fun verified(
            profile: RomHackProfile,
            sha256: String,
            matchMethod: ProfileMatchMethod = ProfileMatchMethod.EXACT_SHA256
        ): RomCompatibility = RomCompatibility(
            status = RomCompatibilityStatus.VERIFIED,
            profile = profile,
            detectedProfile = profile,
            matchMethod = matchMethod,
            sha256 = sha256,
            reason = "Exact SHA-256 match for ${profile.id} (layout verified)"
        )

        fun recognizedUnverified(
            profile: RomHackProfile?,
            sha256: String,
            matchMethod: ProfileMatchMethod,
            reason: String
        ): RomCompatibility = RomCompatibility(
            status = RomCompatibilityStatus.RECOGNIZED_UNVERIFIED,
            profile = profile ?: RomHackProfile.UNSUPPORTED,
            detectedProfile = profile,
            matchMethod = matchMethod,
            sha256 = sha256,
            reason = reason
        )

        fun unsupported(
            sha256: String,
            matchMethod: ProfileMatchMethod = ProfileMatchMethod.DEFAULT_FALLBACK,
            reason: String = "No supported profile matched the ROM header, filename, or SHA-256"
        ): RomCompatibility = RomCompatibility(
            status = RomCompatibilityStatus.UNSUPPORTED,
            profile = RomHackProfile.UNSUPPORTED,
            detectedProfile = null,
            matchMethod = matchMethod,
            sha256 = sha256,
            reason = reason
        )
    }
}

/**
 * User-facing wording for the compatibility model.
 *
 * Kept free of Android dependencies so the exact beta wording is unit-testable and cannot drift
 * between the shell status strip and the individual tabs.
 */
object RomCompatibilityMessages {

    const val VERIFIED_BADGE: String = "Verified"

    const val RECOGNIZED_UNVERIFIED_BADGE: String = "Unverified version"

    const val UNSUPPORTED_BADGE: String = "Unsupported ROM"

    const val RECOGNIZED_UNVERIFIED_DETAIL: String =
        "ROM recognized, but this exact version has not been verified. Live companion data is disabled."

    const val UNSUPPORTED_DETAIL: String =
        "This ROM can be played normally, but live companion features are unavailable."

    /** Short, single-line reason shown in a compact status surface. */
    fun badge(status: RomCompatibilityStatus): String = when (status) {
        RomCompatibilityStatus.VERIFIED -> VERIFIED_BADGE
        RomCompatibilityStatus.RECOGNIZED_UNVERIFIED -> RECOGNIZED_UNVERIFIED_BADGE
        RomCompatibilityStatus.UNSUPPORTED -> UNSUPPORTED_BADGE
    }

    /** Full explanation shown inside an affected tab's unavailable state. */
    fun detail(status: RomCompatibilityStatus): String = when (status) {
        RomCompatibilityStatus.VERIFIED -> ""
        RomCompatibilityStatus.RECOGNIZED_UNVERIFIED -> RECOGNIZED_UNVERIFIED_DETAIL
        RomCompatibilityStatus.UNSUPPORTED -> UNSUPPORTED_DETAIL
    }
}
