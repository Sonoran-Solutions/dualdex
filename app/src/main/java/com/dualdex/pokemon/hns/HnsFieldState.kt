package com.dualdex.pokemon.hns

/**
 * The decoded battle-global `gFieldStatuses` word of an exact H&S 2.0.5 battle.
 *
 * [raw] is the authoritative word exactly as the native reader observed it; it is never masked,
 * so an unexpected bit survives decoding and stays visible in diagnostics. [active] lists the
 * reviewed pinned bits that are set ([HnsFieldStatus], generated from `include/constants/battle.h`)
 * and [unknownMask] is every set bit outside that reviewed mask. Unknown bits always fail closed.
 */
data class HnsFieldState(
    val raw: Int,
    val active: Set<HnsFieldStatus>,
    val unknownMask: Int
) {
    val isClear: Boolean get() = raw == 0

    /** True only when every set bit is a reviewed pinned bit. */
    val fullyDecoded: Boolean get() = unknownMask == 0

    fun has(status: HnsFieldStatus): Boolean = raw and status.mask != 0

    /** True when any of the four terrain bits (`STATUS_FIELD_TERRAIN_ANY`) is set. */
    val terrainActive: Boolean get() = raw and HnsFieldStatusData.STATUS_FIELD_TERRAIN_ANY != 0

    /** Compact diagnostic, e.g. `Electric Terrain (0x00000100)` or `clear (0x00000000)`. */
    fun describe(): String {
        val names = active.map { it.displayName } +
            listOfNotNull(unknownMask.takeIf { it != 0 }?.let { "Unknown field bits ${formatMask(it)}" })
        val label = if (names.isEmpty()) "clear" else names.joinToString(" + ")
        return "$label (${formatMask(raw)})"
    }

    companion object {
        /** Decodes [raw] without masking it. */
        fun decode(raw: Int): HnsFieldState = HnsFieldState(
            raw = raw,
            active = HnsFieldStatus.entries.filterTo(LinkedHashSet()) { raw and it.mask != 0 },
            unknownMask = raw and HnsFieldStatusData.KNOWN_MASK.inv()
        )

        /** Eight-digit hexadecimal spelling of a field mask, e.g. `0x00000100`. */
        fun formatMask(mask: Int): String = "0x%08X".format(mask)
    }
}
