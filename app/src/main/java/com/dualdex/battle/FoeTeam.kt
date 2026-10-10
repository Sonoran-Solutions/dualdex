package com.dualdex.battle

import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.resolveSpecies

/** One trainer party slot as the Battle tab may show it. [speciesId] is null until revealed. */
data class FoeSlot(
    val slot: Int,
    val speciesId: Int?,
    val active: Boolean,
    val next: Boolean,
    val fainted: Boolean
) {
    val revealed: Boolean get() = speciesId != null

    /** Species name from the active ROM's own data pack (hacks renumber species), or null while hidden. */
    fun name(pack: GameDataPack): String? = speciesId?.let { pack.resolveSpecies(it).name }
}

/**
 * Trainer-team view model. A foe stays a Poké Ball until it has been the authoritatively resolved
 * active opponent (or announced as the next pick) at least once this battle; nothing is revealed
 * from the raw party read alone, because that would show the player the trainer's whole team.
 *
 * Inputs come only from the verified-profile live reads (enemy party, `gBattlerPartyIndexes`-based
 * active slot); on any other profile the enemy party is empty and [build] hides the row.
 */
class FoeTeamTracker {
    private val seen = mutableSetOf<Int>()

    /** Battle ended: forget what was revealed. */
    fun reset() = seen.clear()

    /**
     * @param enemies enemy party in slot order (as published by the live reader)
     * @param activeSlot resolved active opponent slot, or null when not authoritatively known
     * @param nextSlot the trainer's announced next pick (`gBattleStruct->monToSwitchIntoId`), or
     *   null when the profile has no verified address for it
     * @return slots to show, or empty to hide the row (not a trainer battle / nothing read)
     */
    fun build(enemies: List<ParsedPokemon>, activeSlot: Int?, nextSlot: Int?): List<FoeSlot> {
        if (enemies.size < 2) return emptyList()
        val active = activeSlot?.takeIf { it in enemies.indices }
        val next = nextSlot?.takeIf { it in enemies.indices && it != active && enemies[it].currentHp > 0 }
        active?.let { seen += it }
        next?.let { seen += it }
        return enemies.mapIndexed { i, mon ->
            val fainted = mon.currentHp <= 0
            // A fainted foe was necessarily sent out at some point, so it is no longer hidden.
            val shown = i in seen || fainted
            FoeSlot(
                slot = i,
                speciesId = if (shown) mon.species else null,
                active = i == active,
                next = i == next,
                fainted = fainted
            )
        }
    }
}
