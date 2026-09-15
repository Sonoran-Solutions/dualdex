package com.dualdex.pokemon

import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.SpeciesOverride

/**
 * Encapsulates generation- and profile-specific game data (typings, move power, physical/special split).
 * Guarantees that vanilla Gen 3 games never consume later-generation mechanics or typings (e.g. Fairy).
 */
interface GameDataPack {
    val id: String
    val generation: Int
    val hasFairyType: Boolean
    /** True only when this pack's static data and mechanics support Stellar as a type. */
    val hasStellarType: Boolean get() = false
    val hasPhysicalSpecialSplit: Boolean

    /**
     * True if callers and resolvers may fall back to global/generic databases (SpeciesDatabase,
     * MoveDatabase) on a lookup miss. Exact, version-pinned ROM hack data packs (such as Heart & Soul 2.0.5)
     * set this to false to guarantee that ROM-specific IDs are never polluted or substituted with
     * generic entries.
     */
    val allowGlobalFallback: Boolean get() = true

    fun getSpecies(id: Int): SpeciesInfo?
    fun getMove(id: Int): MoveInfo?
    fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double

    /** True only when this pack has an explicitly verified value for the entry. */
    fun isSpeciesAuthoritative(id: Int): Boolean = false

    /** True only when this pack has an explicitly verified value for the entry. */
    fun isMoveAuthoritative(id: Int): Boolean = false
}

/**
 * Generation 3 (FireRed / Emerald) compatibility data pack:
 * - Fairy type does NOT exist (Clefairy/Jigglypuff/Togepi/Marill/Snubbull/Ralts/Mawile retain pure Gen 3 typings).
 * - Physical / Special split is determined strictly by move type, not individual move.
 * - Move powers/accuracies match Gen 3 (Hydro Pump: 120, Surf: 95, Flamethrower: 95, Ice Beam: 95, Blizzard: 120).
 * - Steel resists Ghost and Dark.
 *
 * The shared databases are still used as a presentation fallback. Only entries with explicit
 * Gen 3 overrides are authoritative; callers must use the authority methods before reporting
 * VERIFIED confidence for a fallback value.
 */
object Gen3VanillaDataPack : GameDataPack {
    override val id: String = "gen3_vanilla"
    override val generation: Int = 3
    override val hasFairyType: Boolean = false
    override val hasStellarType: Boolean = false
    override val hasPhysicalSpecialSplit: Boolean = false

    private val speciesOverrides = mapOf(
        35 to Pair(PokemonType.NORMAL, null),                     // Clefairy
        36 to Pair(PokemonType.NORMAL, null),                     // Clefable
        39 to Pair(PokemonType.NORMAL, null),                     // Jigglypuff
        40 to Pair(PokemonType.NORMAL, null),                     // Wigglytuff
        122 to Pair(PokemonType.PSYCHIC, null),                   // Mr. Mime
        173 to Pair(PokemonType.NORMAL, null),                    // Cleffa
        174 to Pair(PokemonType.NORMAL, null),                    // Igglybuff
        175 to Pair(PokemonType.NORMAL, null),                    // Togepi
        176 to Pair(PokemonType.NORMAL, PokemonType.FLYING),      // Togetic
        183 to Pair(PokemonType.WATER, null),                     // Marill
        184 to Pair(PokemonType.WATER, null),                     // Azumarill
        209 to Pair(PokemonType.NORMAL, null),                    // Snubbull
        210 to Pair(PokemonType.NORMAL, null),                    // Granbull
        280 to Pair(PokemonType.PSYCHIC, null),                   // Ralts
        281 to Pair(PokemonType.PSYCHIC, null),                   // Kirlia
        282 to Pair(PokemonType.PSYCHIC, null),                   // Gardevoir
        298 to Pair(PokemonType.NORMAL, null),                    // Azurill
        303 to Pair(PokemonType.STEEL, null)                      // Mawile
    )

    private val moveOverrides = mapOf(
        33 to MoveInfo(33, "Tackle", PokemonType.NORMAL, MoveCategory.PHYSICAL, 35, 95, 35),
        53 to MoveInfo(53, "Flamethrower", PokemonType.FIRE, MoveCategory.SPECIAL, 95, 100, 15),
        56 to MoveInfo(56, "Hydro Pump", PokemonType.WATER, MoveCategory.SPECIAL, 120, 80, 5),
        57 to MoveInfo(57, "Surf", PokemonType.WATER, MoveCategory.SPECIAL, 95, 100, 15),
        58 to MoveInfo(58, "Ice Beam", PokemonType.ICE, MoveCategory.SPECIAL, 95, 100, 10),
        59 to MoveInfo(59, "Blizzard", PokemonType.ICE, MoveCategory.SPECIAL, 120, 70, 5),
        85 to MoveInfo(85, "Thunderbolt", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 95, 100, 15),
        87 to MoveInfo(87, "Thunder", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 120, 70, 10),
        126 to MoveInfo(126, "Fire Blast", PokemonType.FIRE, MoveCategory.SPECIAL, 120, 85, 5)
    )

    // The backing databases contain later-generation values for entries not listed here. Keep
    // those useful for presentation, but never describe them as authoritative Gen 3 data.
    private val authoritativeSpeciesIds = speciesOverrides.keys
    private val authoritativeMoveIds = moveOverrides.keys

    override fun getSpecies(id: Int): SpeciesInfo? {
        val base = SpeciesDatabase.getRaw(id) ?: return null
        val override = speciesOverrides[id] ?: return base
        return base.copy(type1 = override.first, type2 = override.second)
    }

    override fun getMove(id: Int): MoveInfo? {
        val raw = moveOverrides[id] ?: MoveDatabase.getRaw(id) ?: return null
        if (raw.category == MoveCategory.STATUS) return raw

        // In Gen 3 without Phys/Spec split, category is strictly governed by move Type:
        val category = when (raw.type) {
            PokemonType.NORMAL, PokemonType.FIGHTING, PokemonType.FLYING,
            PokemonType.POISON, PokemonType.GROUND, PokemonType.ROCK,
            PokemonType.BUG, PokemonType.GHOST, PokemonType.STEEL -> MoveCategory.PHYSICAL
            else -> MoveCategory.SPECIAL
        }
        return raw.copy(category = category)
    }

    override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double {
        if (attackType == PokemonType.FAIRY || defType == PokemonType.FAIRY) return 1.0

        // Gen 3 Steel resistances: Steel resisted Ghost and Dark in Gen 2-5
        if (defType == PokemonType.STEEL && (attackType == PokemonType.GHOST || attackType == PokemonType.DARK)) {
            return 0.5
        }
        return TypeChart.getEffectiveness(attackType, defType, steelResistsGhostDark = true).toDouble()
    }

    override fun isSpeciesAuthoritative(id: Int): Boolean = id in authoritativeSpeciesIds

    override fun isMoveAuthoritative(id: Int): Boolean = id in authoritativeMoveIds
}

/**
 * Modern data pack for ROM hacks using CFRU, pokeemerald-expansion, etc.:
 * - Includes Fairy type.
 * - Includes Physical/Special move split.
 * - Steel no longer resists Ghost/Dark.
 */
object ModernDataPack : GameDataPack {
    override val id: String = "modern"
    override val generation: Int = 8
    override val hasFairyType: Boolean = true
    // This generic Gen-8 fallback is not a Gen-9/Tera-aware data pack.
    override val hasStellarType: Boolean = false
    override val hasPhysicalSpecialSplit: Boolean = true

    override fun getSpecies(id: Int): SpeciesInfo? = SpeciesDatabase.getRaw(id)
    override fun getMove(id: Int): MoveInfo? = MoveDatabase.getRaw(id)
    override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double {
        return TypeChart.getEffectiveness(attackType, defType, steelResistsGhostDark = false).toDouble()
    }

    // ModernDataPack is a mechanics fallback, not a ROM-specific verified data source.
    override fun isSpeciesAuthoritative(id: Int): Boolean = false

    override fun isMoveAuthoritative(id: Int): Boolean = false
}

/**
 * Decorates a [basePack] with profile-specific custom species definitions (e.g. Ghost Grey variants).
 * Custom species definitions strictly override base entries for this profile without polluting
 * or mutating global or other profile data packs.
 */
class ProfileOverlayDataPack(
    val basePack: GameDataPack,
    val customSpecies: Map<Int, SpeciesOverride>
) : GameDataPack {
    override val id: String = "${basePack.id}_overlay"
    override val generation: Int get() = basePack.generation
    override val hasFairyType: Boolean get() = basePack.hasFairyType
    override val hasStellarType: Boolean get() = basePack.hasStellarType
    override val hasPhysicalSpecialSplit: Boolean get() = basePack.hasPhysicalSpecialSplit
    override val allowGlobalFallback: Boolean get() = basePack.allowGlobalFallback

    private val convertedSpecies: Map<Int, SpeciesInfo> = customSpecies.mapValues { (id, override) ->
        SpeciesInfo(
            id = id,
            name = override.name,
            type1 = PokemonType.fromString(override.type1) ?: PokemonType.NORMAL,
            type2 = override.type2?.let { PokemonType.fromString(it) },
            baseHP = override.hp,
            baseAtk = override.atk,
            baseDef = override.def,
            baseSpA = override.spa,
            baseSpD = override.spd,
            baseSpe = override.spe
        )
    }

    override fun getSpecies(id: Int): SpeciesInfo? {
        return convertedSpecies[id] ?: basePack.getSpecies(id)
    }

    override fun getMove(id: Int): MoveInfo? = basePack.getMove(id)

    override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double =
        basePack.getEffectiveness(attackType, defType)

    override fun isSpeciesAuthoritative(id: Int): Boolean =
        id in convertedSpecies || basePack.isSpeciesAuthoritative(id)

    override fun isMoveAuthoritative(id: Int): Boolean =
        basePack.isMoveAuthoritative(id)
}

object GameDataPackRegistry {
    fun getForProfile(profile: RomHackProfile): GameDataPack {
        return getForProfile(
            engine = profile.engine,
            hasPhysSpecSplit = profile.hasPhysSpecSplit,
            customPackId = profile.gameDataPackId,
            customSpecies = profile.customSpecies
        )
    }

    fun getForProfile(
        engine: String,
        hasPhysSpecSplit: Boolean,
        customPackId: String? = null,
        customSpecies: Map<Int, SpeciesOverride> = emptyMap()
    ): GameDataPack {
        val basePack: GameDataPack = if (!customPackId.isNullOrBlank()) {
            when (customPackId.lowercase()) {
                "hns_2_0_5", "heart_and_soul", "hns" -> HeartAndSoul205DataPack
                "modern", "modern_cfru", "cfru" -> ModernDataPack
                "gen3_vanilla", "vanilla" -> Gen3VanillaDataPack
                else -> Gen3VanillaDataPack
            }
        } else if (engine.equals("Vanilla", ignoreCase = true) && !hasPhysSpecSplit) {
            Gen3VanillaDataPack
        } else {
            ModernDataPack
        }

        return if (customSpecies.isNotEmpty()) {
            ProfileOverlayDataPack(basePack, customSpecies)
        } else {
            basePack
        }
    }
}

/**
 * Safely resolves a [SpeciesInfo] from this data pack.
 * If the species is absent:
 * - Falls back to [SpeciesDatabase.get] only if [GameDataPack.allowGlobalFallback] is true.
 * - Otherwise returns a safe placeholder "Unknown Species #<id>" without substituting
 *   a same-numbered generic modern species.
 */
fun GameDataPack.resolveSpecies(id: Int): SpeciesInfo {
    val found = getSpecies(id)
    if (found != null) return found
    if (allowGlobalFallback) {
        return SpeciesDatabase.get(id, this)
    }
    return SpeciesInfo(
        id = id,
        name = "Unknown Species #$id",
        type1 = PokemonType.NORMAL,
        type2 = null,
        baseHP = 0,
        baseAtk = 0,
        baseDef = 0,
        baseSpA = 0,
        baseSpD = 0,
        baseSpe = 0
    )
}

/**
 * Safely resolves a [MoveInfo] from this data pack.
 * If the move is absent:
 * - Falls back to [MoveDatabase.get] only if [GameDataPack.allowGlobalFallback] is true.
 * - Otherwise returns a safe placeholder "Unknown Move #<id>" without substituting
 *   a same-numbered generic modern move.
 */
fun GameDataPack.resolveMove(id: Int): MoveInfo {
    val found = getMove(id)
    if (found != null) return found
    if (allowGlobalFallback) {
        return MoveDatabase.get(id, this)
    }
    return MoveInfo(
        id = id,
        name = "Unknown Move #$id",
        type = PokemonType.NORMAL,
        category = MoveCategory.PHYSICAL,
        power = 0,
        accuracy = 0,
        pp = 0
    )
}
