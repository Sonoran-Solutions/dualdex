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
     * Resolves the authoritative effective ability from [observation] when [isExactHns] is true
     * and the observation's observed `partySlot` matches [expectedPartySlot].
     *
     * Fails closed to [EffectiveAbilityResolution.UnknownAbility] on:
     * - non-H&S profile ([isExactHns] == false)
     * - missing or null observation
     * - non-OBSERVED status (UNAVAILABLE, AMBIGUOUS, OBSERVED_INVALID)
     * - out-of-domain ability ID
     * - party slot mismatch (bench Pokemon or stale battler)
     */
    fun resolveEffectiveAbility(
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        expectedPartySlot: Int?,
        isExactHns: Boolean
    ): EffectiveAbilityResolution {
        if (!isExactHns || observation == null || expectedPartySlot == null) {
            return EffectiveAbilityResolution.UnknownAbility
        }
        val state = observation.state
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) {
            return EffectiveAbilityResolution.UnknownAbility
        }
        if (state.abilityOutOfDomain || state.abilityId == null ||
            state.abilityId !in 0..com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.ABILITY_ID_MAX
        ) {
            return EffectiveAbilityResolution.UnknownAbility
        }
        if (state.partySlot != expectedPartySlot) {
            return EffectiveAbilityResolution.UnknownAbility
        }
        val declared = observation.abilityIdentity as? com.dualdex.pokemon.DeclaredAbility.Declared
        if (state.abilityId == 0) {
            // Defense-in-depth: if identity is declared with non-zero ID, reject the mismatch
            return if (declared != null && declared.abilityId != 0) {
                EffectiveAbilityResolution.UnknownAbility
            } else {
                EffectiveAbilityResolution.ObservedNone
            }
        }
        // Defense-in-depth: verify identity abilityId matches state abilityId
        return if (declared != null && declared.abilityId == state.abilityId && declared.name.isNotBlank()) {
            EffectiveAbilityResolution.ObservedAbility(declared.name, state.abilityId)
        } else {
            EffectiveAbilityResolution.UnknownAbility
        }
    }

    /**
     * Resolves the authoritative current item from [observation] when [isExactHns] is true.
     *
     * The rules encode the stored-party-item vs current-battle-item distinction:
     * - not exact H&S -> [EffectiveItemResolution.GenericDatabase] (legacy behaviour);
     * - no active battle -> the parsed party item is authoritative ([EffectiveItemResolution.PartyStored]);
     * - active battle and the observation's party slot matches [expectedPartySlot] -> the battle
     *   engine's current item wins ([EffectiveItemResolution.BattleEffective]);
     * - active battle and the slot does not match -> the selected member is on the bench, so the
     *   exact parsed party item is used;
     * - active battle and the slot cannot be authoritatively established -> unknown, never a
     *   fallback to the party item (a consumed/Knocked-Off item must not be resurrected).
     */
    fun resolveEffectiveItem(
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        expectedPartySlot: Int?,
        parsedHeldItem: Int,
        isExactHns: Boolean,
        activeBattle: Boolean
    ): EffectiveItemResolution {
        if (!isExactHns) return EffectiveItemResolution.GenericDatabase
        if (!activeBattle) return EffectiveItemResolution.PartyStored(parsedHeldItem)
        if (expectedPartySlot == null) return EffectiveItemResolution.UnknownItem
        val state = observation?.state ?: return EffectiveItemResolution.UnknownItem
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) {
            return EffectiveItemResolution.UnknownItem
        }
        if (state.partySlot != expectedPartySlot) {
            return EffectiveItemResolution.PartyStored(parsedHeldItem)
        }
        if (state.itemOutOfDomain || state.itemId == null) {
            return EffectiveItemResolution.UnknownItem
        }
        return EffectiveItemResolution.BattleEffective(state.itemId)
    }

    /**
     * The attacker: the selected party member when one is available, otherwise the benchmark.
     *
     * A party member is a live read. Under exact H&S, its effective ability is resolved from
     * [playerBattlerState] only when the observation's observed active party slot matches [selectedIndex].
     * Otherwise, ability remains unknown. Stat stages are recorded as unknown when [playerStages] was
     * not observed. The benchmark is MANUAL: the user is asserting a hypothetical, not reading the game.
     */
    fun attacker(
        party: List<ParsedPokemon>,
        selectedIndex: Int,
        speciesNameOf: (ParsedPokemon) -> String,
        playerStages: StatStages? = null,
        isExpansionItems: Boolean = false,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        isExactHns: Boolean = false,
        activeBattle: Boolean = false
    ): CalcParticipantState {
        val member = party.getOrNull(selectedIndex) ?: return benchmarkAttacker()
        val effectiveAbility = resolveEffectiveAbility(
            observation = playerBattlerState,
            expectedPartySlot = selectedIndex,
            isExactHns = isExactHns
        )
        val effectiveItem = resolveEffectiveItem(
            observation = playerBattlerState,
            expectedPartySlot = selectedIndex,
            parsedHeldItem = member.heldItem,
            isExactHns = isExactHns,
            activeBattle = activeBattle
        )
        return CalcInputPreparation.fromParsed(
            parsed = member,
            speciesName = speciesNameOf(member),
            boosts = playerStages?.toBoostStatBlock(),
            isExpansionItems = isExpansionItems,
            effectiveAbility = effectiveAbility,
            partySlot = selectedIndex,
            effectiveItem = effectiveItem
        )
    }

    /**
     * The defender: the resolved in-battle opponent when one exists, otherwise the benchmark built
     * from the autocomplete species.
     *
     * Under exact H&S, the opponent's effective ability and current item are resolved from
     * [enemyBattlerState] only when the observation's observed active party slot matches
     * [activeEnemySlot]. [enemyStages] must be the observed opponent stat stages, or null when they
     * were not read.
     */
    fun defender(
        observedOpponent: ParsedPokemon?,
        chosenSpecies: String,
        enemyStages: StatStages? = null,
        isExpansionItems: Boolean = false,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        isExactHns: Boolean = false,
        activeEnemySlot: Int? = null,
        activeBattle: Boolean = false
    ): CalcParticipantState {
        if (observedOpponent == null) return benchmarkDefender(chosenSpecies)
        val effectiveAbility = resolveEffectiveAbility(
            observation = enemyBattlerState,
            expectedPartySlot = activeEnemySlot,
            isExactHns = isExactHns
        )
        val effectiveItem = resolveEffectiveItem(
            observation = enemyBattlerState,
            expectedPartySlot = activeEnemySlot,
            parsedHeldItem = observedOpponent.heldItem,
            isExactHns = isExactHns,
            activeBattle = activeBattle
        )
        return CalcInputPreparation.fromParsed(
            parsed = observedOpponent,
            speciesName = chosenSpecies,
            boosts = enemyStages?.toBoostStatBlock(),
            isExpansionItems = isExpansionItems,
            effectiveAbility = effectiveAbility,
            partySlot = activeEnemySlot,
            effectiveItem = effectiveItem
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
