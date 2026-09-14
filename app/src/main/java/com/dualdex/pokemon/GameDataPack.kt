package com.dualdex.pokemon

/**
 * Encapsulates generation- and profile-specific game data (typings, move power, physical/special split).
 * Guarantees that vanilla Gen 3 games never consume later-generation mechanics or typings (e.g. Fairy).
 */
interface GameDataPack {
    val id: String
    val generation: Int
    val hasFairyType: Boolean
    val hasPhysicalSpecialSplit: Boolean

    fun getSpecies(id: Int): SpeciesInfo?
    fun getMove(id: Int): MoveInfo?
    fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double
}

/**
 * Authoritative Generation 3 (FireRed / Emerald) data pack:
 * - Fairy type does NOT exist (Clefairy/Jigglypuff/Togepi/Marill/Snubbull/Ralts/Mawile retain pure Gen 3 typings).
 * - Physical / Special split is determined strictly by move type, not individual move.
 * - Move powers/accuracies match Gen 3 (Hydro Pump: 120, Surf: 95, Flamethrower: 95, Ice Beam: 95, Blizzard: 120).
 * - Steel resists Ghost and Dark.
 */
object Gen3VanillaDataPack : GameDataPack {
    override val id: String = "gen3_vanilla"
    override val generation: Int = 3
    override val hasFairyType: Boolean = false
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
    override val hasPhysicalSpecialSplit: Boolean = true

    override fun getSpecies(id: Int): SpeciesInfo? = SpeciesDatabase.getRaw(id)
    override fun getMove(id: Int): MoveInfo? = MoveDatabase.getRaw(id)
    override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double {
        return TypeChart.getEffectiveness(attackType, defType, steelResistsGhostDark = false).toDouble()
    }
}

object GameDataPackRegistry {
    fun getForProfile(engine: String, hasPhysSpecSplit: Boolean, customPackId: String? = null): GameDataPack {
        if (!customPackId.isNullOrBlank()) {
            return when (customPackId.lowercase()) {
                "modern", "modern_cfru", "cfru" -> ModernDataPack
                else -> Gen3VanillaDataPack
            }
        }
        return if (engine.equals("Vanilla", ignoreCase = true) && !hasPhysSpecSplit) {
            Gen3VanillaDataPack
        } else {
            ModernDataPack
        }
    }
}
