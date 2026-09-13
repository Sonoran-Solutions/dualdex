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
    val isVerified: Boolean = true
) {
    fun isSupportedVanillaGen3(): Boolean =
        isVerified && engine.equals("Vanilla", ignoreCase = true) && !hasPhysSpecSplit && customSpecies.isEmpty() && gameId in 1..5
    companion object {
        val DEFAULT_FIRERED = RomHackProfile(
            id = "vanilla_firered",
            name = "Pokemon FireRed",
            baseGame = "FireRed",
            gameId = 2,
            hasEvs = true,
            hasIvs = true,
            hasPhysSpecSplit = false,
            steelResistsGhostDark = true
        )
    }
}
