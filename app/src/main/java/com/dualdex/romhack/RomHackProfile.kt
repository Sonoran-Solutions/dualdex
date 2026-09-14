package com.dualdex.romhack

data class SpeciesOverride(
    val name: String,
    val type1: String,
    val type2: String? = null,
    val hp: Int = 70,
    val atk: Int = 70,
    val def: Int = 70,
    val spa: Int = 70,
    val spd: Int = 70,
    val spe: Int = 70
)

data class RomHackProfile(
    val id: String,
    val name: String,
    val baseGame: String,
    val gameId: Int,
    val developer: String = "",
    val engine: String = "Vanilla",
    val hasEvs: Boolean = true,
    val hasIvs: Boolean = true,
    val hasPhysSpecSplit: Boolean = false,
    val steelResistsGhostDark: Boolean = true,
    val cfruOffsets: Boolean = false,
    val playerPartyOffset: Long = 0L,
    val enemyPartyOffset: Long = 0L,
    val docsUrl: String? = null,
    val headerTitles: List<String> = emptyList(),
    val sha256Hashes: List<String> = emptyList(),
    val customSpecies: Map<Int, SpeciesOverride> = emptyMap(),
    val isVerified: Boolean = false,
    val interactiveControlsVerified: Boolean = false,
    val memoryLayoutVerified: Boolean = false,
    val battleUiVerified: Boolean = false,
    val commandCursorVerified: Boolean = false,
    val moveCursorVerified: Boolean = false,
    val partyCursorVerified: Boolean = false,
    val moveSelectionVerified: Boolean = false,
    val partySwitchVerified: Boolean = false,
    val partyActionMenuVerified: Boolean = false,
    val gameDataPackId: String? = null
) {
    fun isSupportedVanillaGen3(): Boolean =
        isVerified && memoryLayoutVerified && engine.equals("Vanilla", ignoreCase = true) && !hasPhysSpecSplit && customSpecies.isEmpty() && gameId in 1..5

    companion object {
        val DEFAULT_FIRERED = RomHackProfile(
            id = "vanilla_firered",
            name = "Pokemon FireRed",
            baseGame = "FireRed",
            gameId = 2,
            hasEvs = true,
            hasIvs = true,
            hasPhysSpecSplit = false,
            steelResistsGhostDark = true,
            isVerified = true,
            memoryLayoutVerified = true,
            battleUiVerified = false,
            interactiveControlsVerified = false,
            gameDataPackId = "gen3_vanilla"
        )

        /**
         * Explicit placeholder for a ROM DualDex cannot identify.
         *
         * This is NOT a supported profile: it claims no game ([gameId] 0 == native GAME_UNKNOWN),
         * asserts no layout or hash, and carries no hack-specific species. It exists only so
         * presentation code keeps a non-null identity instead of silently defaulting an unknown
         * ROM to FireRed. The manual calculator falls back to the conservative Gen 3 data pack.
         */
        val UNSUPPORTED = RomHackProfile(
            id = "unsupported_rom",
            name = "Unsupported ROM",
            baseGame = "Unknown",
            gameId = 0,
            developer = "",
            engine = "Unknown",
            hasEvs = true,
            hasIvs = true,
            hasPhysSpecSplit = false,
            steelResistsGhostDark = true,
            cfruOffsets = false,
            playerPartyOffset = 0L,
            enemyPartyOffset = 0L,
            headerTitles = emptyList(),
            sha256Hashes = emptyList(),
            customSpecies = emptyMap(),
            isVerified = false,
            memoryLayoutVerified = false,
            battleUiVerified = false,
            interactiveControlsVerified = false,
            gameDataPackId = "gen3_vanilla"
        )

        /** True when this profile is the explicit "not identified" placeholder. */
        fun isUnsupportedPlaceholder(profile: RomHackProfile): Boolean = profile.id == UNSUPPORTED.id
    }
}
