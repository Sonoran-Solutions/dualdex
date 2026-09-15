package com.dualdex.pokemon

enum class PokemonType(val displayName: String, val colorHex: Long) {
    NORMAL("Normal", 0xFFA8A878),
    FIRE("Fire", 0xFFEE8130),
    WATER("Water", 0xFF6390F0),
    GRASS("Grass", 0xFF7AC74C),
    ELECTRIC("Electric", 0xFFF7D02C),
    ICE("Ice", 0xFF96D9D6),
    FIGHTING("Fighting", 0xFFC22E28),
    POISON("Poison", 0xFFA33EA1),
    GROUND("Ground", 0xFFE2BF65),
    FLYING("Flying", 0xFFA98FF3),
    PSYCHIC("Psychic", 0xFFF95587),
    BUG("Bug", 0xFFA6B91A),
    ROCK("Rock", 0xFFB6A136),
    GHOST("Ghost", 0xFF735797),
    DRAGON("Dragon", 0xFF6F35FC),
    STEEL("Steel", 0xFFB7B7CE),
    DARK("Dark", 0xFF705746),
    FAIRY("Fairy", 0xFFD685AD);

    companion object {
        fun fromString(name: String?): PokemonType? {
            if (name.isNullOrBlank()) return null
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true) }
        }

        fun fromGbaId(id: Int): PokemonType {
            return when (id) {
                0x00 -> NORMAL
                0x01 -> FIGHTING
                0x02 -> FLYING
                0x03 -> POISON
                0x04 -> GROUND
                0x05 -> ROCK
                0x06 -> BUG
                0x07 -> GHOST
                0x08 -> STEEL
                0x09 -> FIRE      // 0x09 is mystery / ??? in vanilla, but in some tables Fire starts at 0x0A
                0x0A -> FIRE
                0x0B -> WATER
                0x0C -> GRASS
                0x0D -> ELECTRIC
                0x0E -> PSYCHIC
                0x0F -> ICE
                0x10 -> DRAGON
                0x11 -> DARK
                0x17 -> FAIRY
                else -> NORMAL
            }
        }
    }
}

data class TypeDefenseProfile(
    val weaknesses4x: List<PokemonType>,
    val weaknesses2x: List<PokemonType>,
    val neutral: List<PokemonType>,
    val resistancesHalf: List<PokemonType>,
    val resistancesQuarter: List<PokemonType>,
    val immunities: List<PokemonType>
)

object TypeChart {

    /**
     * Calculates single-type effectiveness against a defending type.
     * @param atk The attacking move type
     * @param def The defending Pokemon type
     * @param steelResistsGhostDark True for Gen 2-5 or Ghost Grey ROM hack
     */
    fun getEffectiveness(atk: PokemonType, def: PokemonType, steelResistsGhostDark: Boolean = false): Float {
        // Steel special rule
        if (def == PokemonType.STEEL && steelResistsGhostDark) {
            if (atk == PokemonType.GHOST || atk == PokemonType.DARK) {
                return 0.5f
            }
        }

        return when (atk) {
            PokemonType.NORMAL -> when (def) {
                PokemonType.ROCK, PokemonType.STEEL -> 0.5f
                PokemonType.GHOST -> 0.0f
                else -> 1.0f
            }
            PokemonType.FIRE -> when (def) {
                PokemonType.FIRE, PokemonType.WATER, PokemonType.ROCK, PokemonType.DRAGON -> 0.5f
                PokemonType.GRASS, PokemonType.ICE, PokemonType.BUG, PokemonType.STEEL -> 2.0f
                else -> 1.0f
            }
            PokemonType.WATER -> when (def) {
                PokemonType.WATER, PokemonType.GRASS, PokemonType.DRAGON -> 0.5f
                PokemonType.FIRE, PokemonType.GROUND, PokemonType.ROCK -> 2.0f
                else -> 1.0f
            }
            PokemonType.GRASS -> when (def) {
                PokemonType.FIRE, PokemonType.GRASS, PokemonType.POISON, PokemonType.FLYING, PokemonType.BUG, PokemonType.DRAGON, PokemonType.STEEL -> 0.5f
                PokemonType.WATER, PokemonType.GROUND, PokemonType.ROCK -> 2.0f
                else -> 1.0f
            }
            PokemonType.ELECTRIC -> when (def) {
                PokemonType.ELECTRIC, PokemonType.GRASS, PokemonType.DRAGON -> 0.5f
                PokemonType.WATER, PokemonType.FLYING -> 2.0f
                PokemonType.GROUND -> 0.0f
                else -> 1.0f
            }
            PokemonType.ICE -> when (def) {
                PokemonType.FIRE, PokemonType.WATER, PokemonType.ICE, PokemonType.STEEL -> 0.5f
                PokemonType.GRASS, PokemonType.GROUND, PokemonType.FLYING, PokemonType.DRAGON -> 2.0f
                else -> 1.0f
            }
            PokemonType.FIGHTING -> when (def) {
                PokemonType.POISON, PokemonType.FLYING, PokemonType.PSYCHIC, PokemonType.BUG, PokemonType.FAIRY -> 0.5f
                PokemonType.NORMAL, PokemonType.ICE, PokemonType.ROCK, PokemonType.DARK, PokemonType.STEEL -> 2.0f
                PokemonType.GHOST -> 0.0f
                else -> 1.0f
            }
            PokemonType.POISON -> when (def) {
                PokemonType.POISON, PokemonType.GROUND, PokemonType.ROCK, PokemonType.GHOST -> 0.5f
                PokemonType.GRASS, PokemonType.FAIRY -> 2.0f
                PokemonType.STEEL -> 0.0f
                else -> 1.0f
            }
            PokemonType.GROUND -> when (def) {
                PokemonType.GRASS, PokemonType.BUG -> 0.5f
                PokemonType.FIRE, PokemonType.ELECTRIC, PokemonType.POISON, PokemonType.ROCK, PokemonType.STEEL -> 2.0f
                PokemonType.FLYING -> 0.0f
                else -> 1.0f
            }
            PokemonType.FLYING -> when (def) {
                PokemonType.ELECTRIC, PokemonType.ROCK, PokemonType.STEEL -> 0.5f
                PokemonType.GRASS, PokemonType.FIGHTING, PokemonType.BUG -> 2.0f
                else -> 1.0f
            }
            PokemonType.PSYCHIC -> when (def) {
                PokemonType.PSYCHIC, PokemonType.STEEL -> 0.5f
                PokemonType.FIGHTING, PokemonType.POISON -> 2.0f
                PokemonType.DARK -> 0.0f
                else -> 1.0f
            }
            PokemonType.BUG -> when (def) {
                PokemonType.FIRE, PokemonType.FIGHTING, PokemonType.POISON, PokemonType.FLYING, PokemonType.GHOST, PokemonType.STEEL, PokemonType.FAIRY -> 0.5f
                PokemonType.GRASS, PokemonType.PSYCHIC, PokemonType.DARK -> 2.0f
                else -> 1.0f
            }
            PokemonType.ROCK -> when (def) {
                PokemonType.FIGHTING, PokemonType.GROUND, PokemonType.STEEL -> 0.5f
                PokemonType.FIRE, PokemonType.ICE, PokemonType.FLYING, PokemonType.BUG -> 2.0f
                else -> 1.0f
            }
            PokemonType.GHOST -> when (def) {
                PokemonType.DARK -> 0.5f
                PokemonType.PSYCHIC, PokemonType.GHOST -> 2.0f
                PokemonType.NORMAL -> 0.0f
                else -> 1.0f
            }
            PokemonType.DRAGON -> when (def) {
                PokemonType.STEEL -> 0.5f
                PokemonType.DRAGON -> 2.0f
                PokemonType.FAIRY -> 0.0f
                else -> 1.0f
            }
            PokemonType.STEEL -> when (def) {
                PokemonType.FIRE, PokemonType.WATER, PokemonType.ELECTRIC, PokemonType.STEEL -> 0.5f
                PokemonType.ICE, PokemonType.ROCK, PokemonType.FAIRY -> 2.0f
                else -> 1.0f
            }
            PokemonType.DARK -> when (def) {
                PokemonType.FIGHTING, PokemonType.DARK, PokemonType.FAIRY -> 0.5f
                PokemonType.PSYCHIC, PokemonType.GHOST -> 2.0f
                else -> 1.0f
            }
            PokemonType.FAIRY -> when (def) {
                PokemonType.FIRE, PokemonType.POISON, PokemonType.STEEL -> 0.5f
                PokemonType.FIGHTING, PokemonType.DRAGON, PokemonType.DARK -> 2.0f
                else -> 1.0f
            }
        }
    }

    /**
     * Compute dual-type defensive matchup profile.
     */
    fun getDefenseProfile(
        type1: PokemonType,
        type2: PokemonType? = null,
        steelResistsGhostDark: Boolean = false,
        pack: GameDataPack? = null
    ): TypeDefenseProfile {
        val w4 = mutableListOf<PokemonType>()
        val w2 = mutableListOf<PokemonType>()
        val n = mutableListOf<PokemonType>()
        val rHalf = mutableListOf<PokemonType>()
        val rQuarter = mutableListOf<PokemonType>()
        val im = mutableListOf<PokemonType>()

        val attackingTypes = if (pack != null && !pack.hasFairyType) {
            PokemonType.values().filter { it != PokemonType.FAIRY }
        } else {
            PokemonType.values().toList()
        }

        for (atk in attackingTypes) {
            val mult1 = pack?.getEffectiveness(atk, type1)?.toFloat()
                ?: getEffectiveness(atk, type1, steelResistsGhostDark)
            val mult2 = if (type2 != null && type2 != type1) {
                pack?.getEffectiveness(atk, type2)?.toFloat()
                    ?: getEffectiveness(atk, type2, steelResistsGhostDark)
            } else 1.0f
            val total = mult1 * mult2

            when {
                total == 0.0f -> im.add(atk)
                total >= 4.0f -> w4.add(atk)
                total >= 2.0f -> w2.add(atk)
                total <= 0.25f -> rQuarter.add(atk)
                total <= 0.5f -> rHalf.add(atk)
                else -> n.add(atk)
            }
        }

        return TypeDefenseProfile(
            weaknesses4x = w4,
            weaknesses2x = w2,
            neutral = n,
            resistancesHalf = rHalf,
            resistancesQuarter = rQuarter,
            immunities = im
        )
    }
}
