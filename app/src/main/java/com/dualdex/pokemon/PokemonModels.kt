package com.dualdex.pokemon

/**
 * Kotlin data class representing a parsed Pokémon from memory or save state.
 */
data class ParsedPokemon(
    val isValid: Boolean,
    val isEmpty: Boolean,
    val pid: Long,
    val tid: Int,
    val sid: Int,
    val nickname: String,
    val otName: String,
    val species: Int,
    val heldItem: Int,
    val level: Int,
    val nature: Int,
    val natureName: String,
    val isShiny: Boolean,
    val abilitySlot: Int,
    val isEgg: Boolean,
    val friendship: Int,
    val experience: Long,
    // IVs (0 - 31)
    val hpIv: Int,
    val attackIv: Int,
    val defenseIv: Int,
    val speedIv: Int,
    val spAttackIv: Int,
    val spDefenseIv: Int,
    // EVs (0 - 255)
    val hpEv: Int,
    val attackEv: Int,
    val defenseEv: Int,
    val speedEv: Int,
    val spAttackEv: Int,
    val spDefenseEv: Int,
    // Moves & PP
    val moves: IntArray,
    val pp: IntArray,
    // Runtime battle stats
    val currentHp: Int,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val spAttack: Int,
    val spDefense: Int,
    val statusCondition: Long,
    /**
     * Stat-effective ("mint") nature for pokeemerald-expansion games. Heart & Soul 2.0.5
     * computes stats from `GetMonData(mon, MON_DATA_HIDDEN_NATURE)`, which is
     * `(pid % 25) ^ hiddenNatureModifier`, while [nature] stays the displayed `GetNature()`.
     * Equal to [nature] for vanilla Gen 3 and for any Pokémon without a mint applied.
     */
    val hiddenNature: Int = nature,
    /** True when a mint changed the stat-effective nature away from [nature]. */
    val natureModified: Boolean = false,
    /**
     * pokeemerald-expansion-only flag. In the vanilla Gen 3 layout this bit position is the
     * ability slot, so a game must declare the expansion storage layout before it is read.
     */
    val gigantamaxFactor: Boolean = false,
    /**
     * Tri-state shiny verdict: [SHINY_UNKNOWN], [SHINY_NO] or [SHINY_YES].
     *
     * Heart & Soul 2.0.5 computes shininess as `(shinyValue < shinyOdds) ^ shinyModifier`, where
     * `shinyOdds` comes from `SaveBlock3.challengeSettings`. DualDex does not read those settings
     * yet, so a value in the ambiguous band is reported as [SHINY_UNKNOWN] instead of guessed.
     * [isShiny] is only ever true for an exact [SHINY_YES].
     */
    val shinyState: Int = if (isShiny) SHINY_YES else SHINY_NO,
    /** Raw `shinyModifier` bit; pokeemerald-expansion games only, otherwise 0. */
    val shinyModifier: Int = 0
) {
    /** True when the shiny verdict is exactly known, false when it depends on unread settings. */
    val shinyIsKnown: Boolean get() = shinyState != SHINY_UNKNOWN

    companion object {
        const val SHINY_UNKNOWN = 0
        const val SHINY_NO = 1
        const val SHINY_YES = 2
    }

    val totalEvs: Int get() = hpEv + attackEv + defenseEv + speedEv + spAttackEv + spDefenseEv
    val totalIvs: Int get() = hpIv + attackIv + defenseIv + speedIv + spAttackIv + spDefenseIv

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsedPokemon

        if (isValid != other.isValid) return false
        if (isEmpty != other.isEmpty) return false
        if (pid != other.pid) return false
        if (tid != other.tid) return false
        if (sid != other.sid) return false
        if (nickname != other.nickname) return false
        if (otName != other.otName) return false
        if (species != other.species) return false
        if (heldItem != other.heldItem) return false
        if (level != other.level) return false
        if (nature != other.nature) return false
        if (natureName != other.natureName) return false
        if (isShiny != other.isShiny) return false
        if (abilitySlot != other.abilitySlot) return false
        if (isEgg != other.isEgg) return false
        if (friendship != other.friendship) return false
        if (experience != other.experience) return false
        if (hpIv != other.hpIv) return false
        if (attackIv != other.attackIv) return false
        if (defenseIv != other.defenseIv) return false
        if (speedIv != other.speedIv) return false
        if (spAttackIv != other.spAttackIv) return false
        if (spDefenseIv != other.spDefenseIv) return false
        if (hpEv != other.hpEv) return false
        if (attackEv != other.attackEv) return false
        if (defenseEv != other.defenseEv) return false
        if (speedEv != other.speedEv) return false
        if (spAttackEv != other.spAttackEv) return false
        if (spDefenseEv != other.spDefenseEv) return false
        if (!moves.contentEquals(other.moves)) return false
        if (!pp.contentEquals(other.pp)) return false
        if (currentHp != other.currentHp) return false
        if (maxHp != other.maxHp) return false
        if (attack != other.attack) return false
        if (defense != other.defense) return false
        if (speed != other.speed) return false
        if (spAttack != other.spAttack) return false
        if (spDefense != other.spDefense) return false
        if (statusCondition != other.statusCondition) return false
        if (hiddenNature != other.hiddenNature) return false
        if (natureModified != other.natureModified) return false
        if (gigantamaxFactor != other.gigantamaxFactor) return false
        if (shinyState != other.shinyState) return false
        if (shinyModifier != other.shinyModifier) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isValid.hashCode()
        result = 31 * result + isEmpty.hashCode()
        result = 31 * result + pid.hashCode()
        result = 31 * result + tid
        result = 31 * result + sid
        result = 31 * result + nickname.hashCode()
        result = 31 * result + otName.hashCode()
        result = 31 * result + species
        result = 31 * result + heldItem
        result = 31 * result + level
        result = 31 * result + nature
        result = 31 * result + natureName.hashCode()
        result = 31 * result + isShiny.hashCode()
        result = 31 * result + abilitySlot
        result = 31 * result + isEgg.hashCode()
        result = 31 * result + friendship
        result = 31 * result + experience.hashCode()
        result = 31 * result + hpIv
        result = 31 * result + attackIv
        result = 31 * result + defenseIv
        result = 31 * result + speedIv
        result = 31 * result + spAttackIv
        result = 31 * result + spDefenseIv
        result = 31 * result + hpEv
        result = 31 * result + attackEv
        result = 31 * result + defenseEv
        result = 31 * result + speedEv
        result = 31 * result + spAttackEv
        result = 31 * result + spDefenseEv
        result = 31 * result + moves.contentHashCode()
        result = 31 * result + pp.contentHashCode()
        result = 31 * result + currentHp
        result = 31 * result + maxHp
        result = 31 * result + attack
        result = 31 * result + defense
        result = 31 * result + speed
        result = 31 * result + spAttack
        result = 31 * result + spDefense
        result = 31 * result + statusCondition.hashCode()
        result = 31 * result + hiddenNature
        result = 31 * result + natureModified.hashCode()
        result = 31 * result + gigantamaxFactor.hashCode()
        result = 31 * result + shinyState
        result = 31 * result + shinyModifier
        return result
    }
}

enum class GbaGame(val id: Int, val title: String) {
    UNKNOWN(0, "Unknown"),
    EMERALD(1, "Pokemon Emerald"),
    FIRERED(2, "Pokemon FireRed"),
    LEAFGREEN(3, "Pokemon LeafGreen"),
    RUBY(4, "Pokemon Ruby"),
    SAPPHIRE(5, "Pokemon Sapphire"),
    GHOST_GREY(6, "Pokemon Ghost Grey"),
    RADICAL_RED(7, "Pokemon Radical Red"),
    HEART_AND_SOUL(8, "Pokemon Heart & Soul"),
    UNBOUND(9, "Pokemon Unbound");

    companion object {
        fun fromId(id: Int): GbaGame = values().firstOrNull { it.id == id } ?: UNKNOWN
    }
}
