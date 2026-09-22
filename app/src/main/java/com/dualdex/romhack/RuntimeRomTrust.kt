package com.dualdex.romhack

/**
 * Trust for the ROM bytes that are currently running, rather than for a profile in isolation.
 * A profile may describe a verified build, but only an exact hash match makes memory-derived
 * presentation verified at runtime.
 *
 * This is the universal capability gate for memory-derived companion features: if
 * [mayReadLiveMemory] is false, no profile-dependent reader may be invoked at all. Interactive
 * battle controls additionally require their own per-capability flags (see BattleInteractionPolicy).
 */
data class RuntimeRomTrust(
    val matchMethod: ProfileMatchMethod = ProfileMatchMethod.DEFAULT_FALLBACK,
    val detectedSha256: String = "",
    val activeRomSha256: String? = null,
    val profileVerified: Boolean = false,
    val memoryLayoutVerified: Boolean = false,
    /**
     * True only when the profile's live **battle-state** addresses are proven for the retail image
     * (see [RomHackProfile.battleStateReadVerified]). Deliberately independent of
     * [exactRuntimeVerified]: repairing a profile's hashes must not be able to promote reads whose
     * absolute addresses were never proven.
     */
    val battleStateReadVerified: Boolean = false,
    val profileSha256Hashes: List<String> = emptyList(),
    val reason: String = "",
    val explicitStatus: RomCompatibilityStatus? = null
) {
    constructor(
        status: RomCompatibilityStatus,
        matchMethod: ProfileMatchMethod = ProfileMatchMethod.DEFAULT_FALLBACK,
        detectedSha256: String = "",
        activeRomSha256: String? = null,
        profileVerified: Boolean = false,
        memoryLayoutVerified: Boolean = false,
        profileSha256Hashes: List<String> = emptyList(),
        reason: String = "",
        battleStateReadVerified: Boolean = false
    ) : this(
        matchMethod = matchMethod,
        detectedSha256 = detectedSha256,
        activeRomSha256 = activeRomSha256,
        profileVerified = profileVerified,
        memoryLayoutVerified = memoryLayoutVerified,
        battleStateReadVerified = battleStateReadVerified,
        profileSha256Hashes = profileSha256Hashes,
        reason = reason,
        explicitStatus = status
    )

    /**
     * True only when the running ROM is the exact build the profile was verified against:
     * the detection was an exact SHA-256 match, the profile asserts verification and a verified
     * memory layout, and the runtime hash equals both the detected hash and a profile hash.
     */
    val exactRuntimeVerified: Boolean
        get() = (explicitStatus == null || explicitStatus == RomCompatibilityStatus.VERIFIED) &&
            matchMethod == ProfileMatchMethod.EXACT_SHA256 &&
            profileVerified &&
            memoryLayoutVerified &&
            !detectedSha256.isBlank() &&
            detectedSha256.equals(activeRomSha256, ignoreCase = true) &&
            profileSha256Hashes.any { it.equals(activeRomSha256, ignoreCase = true) }

    val status: RomCompatibilityStatus
        get() = explicitStatus ?: when {
            exactRuntimeVerified -> RomCompatibilityStatus.VERIFIED
            matchMethod != ProfileMatchMethod.DEFAULT_FALLBACK || !detectedSha256.isBlank() ->
                RomCompatibilityStatus.RECOGNIZED_UNVERIFIED
            else -> RomCompatibilityStatus.UNSUPPORTED
        }

    val isVerified: Boolean
        get() = status == RomCompatibilityStatus.VERIFIED

    /**
     * The single decision for whether any profile-dependent memory parsing may run.
     *
     * Equal to [exactRuntimeVerified] so that a trust decision can never outlive the ROM identity
     * it was made for, and so screens and the poller share exactly one gate.
     */
    val mayReadLiveMemory: Boolean
        get() = exactRuntimeVerified

    /**
     * Whether the profile-dependent readers that dereference the **battle globals** may run.
     *
     * Strictly narrower than [mayReadLiveMemory]: it also requires the profile to assert that its
     * `battle_mons_offset` and battle-lifecycle offsets are proven for the retail image. An exact
     * hash establishes *which* build is running; it does not establish that a configured address is
     * that build's address. Party and location reads do not depend on this flag because they are
     * proven structurally, so a vanilla profile still shows the player's party and map position
     * while its battle-state reads stay closed.
     */
    val mayReadBattleState: Boolean
        get() = mayReadLiveMemory && battleStateReadVerified

    /** True once a ROM identity has been published for the running session. */
    val hasActiveRom: Boolean
        get() = !activeRomSha256.isNullOrBlank()

    /**
     * Status to surface to the user. Without an active ROM the status is meaningless, so callers
     * should prefer their "no game loaded" wording; [hasActiveRom] distinguishes the two.
     */
    val activeStatus: RomCompatibilityStatus
        get() = if (hasActiveRom) status else RomCompatibilityStatus.UNSUPPORTED

    companion object {
        fun from(
            compatibility: RomCompatibility,
            activeRomSha256: String?
        ): RuntimeRomTrust = RuntimeRomTrust(
            status = compatibility.status,
            matchMethod = compatibility.matchMethod,
            detectedSha256 = compatibility.sha256,
            activeRomSha256 = activeRomSha256,
            profileVerified = compatibility.profile.isVerified,
            memoryLayoutVerified = compatibility.profile.memoryLayoutVerified,
            battleStateReadVerified = compatibility.profile.battleStateReadVerified,
            profileSha256Hashes = compatibility.profile.sha256Hashes,
            reason = compatibility.reason
        )
    }
}
