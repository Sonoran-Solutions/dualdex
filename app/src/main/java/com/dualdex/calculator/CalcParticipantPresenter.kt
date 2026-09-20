package com.dualdex.calculator

import com.dualdex.battle.StatStages
import com.dualdex.pokemon.ItemDatabase
import com.dualdex.pokemon.ParsedPokemon

/**
 * Turns the Calc screen's screen state into the two participants a calculation runs on.
 *
 * This exists so that the *screen's own participant selection* is production code that tests can
 * drive, rather than logic that lives inside a view and can only be exercised by hand. A regression
 * test that hand-builds a participant would not catch the screen dropping a field, which is exactly
 * how an unobserved status once reached a "Verified" result.
 */
object CalcParticipantPresenter {

    /** A benchmark attacker used when the party is unavailable, so the tab is usable with no game. */
    const val BENCHMARK_ATTACKER: String = "Salamence"

    /** A benchmark defender used when no opponent has been resolved. */
    const val BENCHMARK_LEVEL: Int = 50

    /**
     * The attacker: the selected party member when one is available, otherwise the benchmark.
     *
     * A party member is a live read, so its unread fields - its ability, and its stat stages when
     * [playerStages] was not observed - are recorded as unknown rather than defaulted. The benchmark
     * is MANUAL: the user is asserting a hypothetical, not reading the game.
     */
    fun attacker(
        party: List<ParsedPokemon>,
        selectedIndex: Int,
        speciesNameOf: (ParsedPokemon) -> String,
        playerStages: StatStages? = null,
        isExpansionItems: Boolean = false
    ): CalcParticipantState {
        val member = party.getOrNull(selectedIndex) ?: return benchmarkAttacker()
        return CalcInputPreparation.fromParsed(
            parsed = member,
            speciesName = speciesNameOf(member),
            boosts = playerStages?.toBoostStatBlock(),
            isExpansionItems = isExpansionItems
        )
    }

    /**
     * The defender: the resolved in-battle opponent when one exists, otherwise the benchmark built
     * from the autocomplete species.
     *
     * [enemyStages] must be the observed opponent stat stages, or null when they were not read.
     */
    fun defender(
        observedOpponent: ParsedPokemon?,
        chosenSpecies: String,
        enemyStages: StatStages? = null,
        isExpansionItems: Boolean = false
    ): CalcParticipantState {
        if (observedOpponent == null) return benchmarkDefender(chosenSpecies)
        return CalcInputPreparation.fromParsed(
            parsed = observedOpponent,
            speciesName = chosenSpecies,
            boosts = enemyStages?.toBoostStatBlock(),
            isExpansionItems = isExpansionItems
        )
    }

    /**
     * The benchmark attacker.
     *
     * Boosts and status are asserted as neutral because there is no game state behind them; the EV
     * spread is the conventional offensive benchmark the screen has always used.
     */
    fun benchmarkAttacker(speciesName: String = BENCHMARK_ATTACKER): CalcParticipantState =
        CalcParticipantState(
            species = speciesName,
            level = BENCHMARK_LEVEL,
            nature = "Hardy",
            boosts = CalcParticipantState.NONE,
            status = null,
            evs = StatBlock(atk = 252, spa = 252, spe = 252),
            origin = CalcInputOrigin.MANUAL
        )

    /** The benchmark defender: a neutral-bodied target at the benchmark level. */
    fun benchmarkDefender(speciesName: String): CalcParticipantState =
        CalcParticipantState(
            species = speciesName,
            level = BENCHMARK_LEVEL,
            nature = "Hardy",
            boosts = CalcParticipantState.NONE,
            status = null,
            evs = StatBlock(hp = 252, def = 252, spd = 252),
            origin = CalcInputOrigin.MANUAL
        )

    /**
     * The item id -> name conversion the screen uses.
     *
     * Kept here rather than inline in the view so the expansion/vanilla table choice is testable: the
     * vanilla table would silently mis-name an expansion id.
     */
    fun itemName(heldItem: Int, isExpansionItems: Boolean): String? =
        if (heldItem > 0) ItemDatabase.get(heldItem, isExpansionItems).name else null
}
