package com.dualdex.battle

import com.dualdex.calculator.CalcAuthorizedExecution
import com.dualdex.calculator.CalcCapabilityPolicy
import com.dualdex.calculator.CalcFieldInput
import com.dualdex.calculator.CalcGameTypes
import com.dualdex.calculator.CalcMoveInput
import com.dualdex.calculator.CalcParticipantPresenter
import com.dualdex.calculator.CalcRequestBoundary
import com.dualdex.calculator.CalcRequestOutcome
import com.dualdex.calculator.CalcRuleset
import com.dualdex.calculator.CalcSupport
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Value-based participant snapshot key for 10 Hz poll deduplication. */
data class SwitchParticipantKey(
    val slot: Int,
    val pokemonKey: BattlePokemonPresentationKey,
    val nickname: String,
    val isValid: Boolean,
    val isEmpty: Boolean,
    val isEgg: Boolean,
    val speed: Int
) {
    companion object {
        fun from(slot: Int, mon: ParsedPokemon): SwitchParticipantKey = SwitchParticipantKey(
            slot = slot,
            pokemonKey = requireNotNull(BattlePokemonPresentationKey.from(mon)),
            nickname = mon.nickname,
            isValid = mon.isValid,
            isEmpty = mon.isEmpty,
            isEgg = mon.isEgg,
            speed = mon.speed
        )
    }
}

/**
 * Immutable cache key covering every input that can affect [SwitchAdvisor.rankSwitches].
 * Identical 10 Hz battle polling frames produce equal keys and skip background QuickJS work.
 */
data class SwitchAdvisorCacheKey(
    val activePlayerSlot: Int,
    val activeEnemySlot: Int?,
    val opponent: SwitchParticipantKey?,
    val bench: List<SwitchParticipantKey>,
    val profile: RomHackProfile,
    val runtimeTrust: RuntimeRomTrust,
    val enemyStages: StatStages?,
    val challengeSettings: HnsChallengeSettingsSnapshot?,
    val playerBattlerState: BattlerRuntimeObservation?,
    val enemyBattlerState: BattlerRuntimeObservation?
) {
    companion object {
        fun from(input: SwitchAdvisorInput): SwitchAdvisorCacheKey {
            val benchKeys = buildList {
                input.playerParty.forEachIndexed { slot, mon ->
                    if (slot != input.activePlayerSlot && !mon.isEmpty && mon.isValid) {
                        add(SwitchParticipantKey.from(slot, mon))
                    }
                }
            }
            val opponentKey = input.opponent?.takeIf { !it.isEmpty && it.isValid }?.let {
                SwitchParticipantKey.from(input.activeEnemySlot ?: -1, it)
            }
            return SwitchAdvisorCacheKey(
                activePlayerSlot = input.activePlayerSlot,
                activeEnemySlot = input.activeEnemySlot,
                opponent = opponentKey,
                bench = benchKeys,
                profile = input.profile,
                runtimeTrust = input.runtimeTrust,
                enemyStages = input.enemyStages,
                challengeSettings = input.challengeSettings,
                playerBattlerState = input.playerBattlerState,
                enemyBattlerState = input.enemyBattlerState
            )
        }
    }
}

/** Immutable snapshot of live single-battle inputs consumed by [SwitchAdvisorPresenter]. */
data class SwitchAdvisorInput(
    val playerParty: List<ParsedPokemon>,
    val activePlayerSlot: Int,
    val opponent: ParsedPokemon?,
    val activeEnemySlot: Int?,
    val profile: RomHackProfile,
    val dataPack: GameDataPack,
    val runtimeTrust: RuntimeRomTrust,
    val enemyStages: StatStages? = null,
    val challengeSettings: HnsChallengeSettingsSnapshot? = null,
    val playerBattlerState: BattlerRuntimeObservation? = null,
    val enemyBattlerState: BattlerRuntimeObservation? = null,
    val activeBattle: Boolean = true
) {
    fun hasHealthyBench(): Boolean = playerParty.withIndex().any { (slot, mon) ->
        SwitchAdvisor.isEligibleBenchMember(slot, activePlayerSlot, mon)
    }
}

/**
 * Coordinates background switch-in ranking for [com.dualdex.companion.ui.BattleConsoleScreenView].
 *
 * - Deduplicates 10 Hz polls via [SwitchAdvisorCacheKey] so QuickJS is only invoked when
 *   battle participants or trust/profile/stage inputs actually change.
 * - Routes every damage evaluation through [CalcRequestBoundary] and [CalcAuthorizedExecution]
 *   so fail-closed rules, H&S beta freeze guarantees, and confidence tiers remain intact.
 */
class SwitchAdvisorPresenter(
    private val calculator: BattleDamageCalculator = CachingBattleDamageCalculator(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val evaluatorOverride: ((SwitchAdvisorInput) -> SwitchDamageEvaluator)? = null
) {
    private var latestRequestedKey: SwitchAdvisorCacheKey? = null
    private var requestToken: Long = 0L
    private var lastCompletedKey: SwitchAdvisorCacheKey? = null
    private var lastCompletedRecommendations: List<SwitchRecommendation> = emptyList()
    private var inFlightKey: SwitchAdvisorCacheKey? = null
    private var activeJob: Job? = null

    var cachedRecommendations: List<SwitchRecommendation> = emptyList()
        private set

    /**
     * Requests a cache-aware update on [scope]. Returns the current cached list immediately when
     * the immutable key matches the last completed evaluation (zero coroutine / QuickJS work).
     */
    fun requestUpdate(
        scope: CoroutineScope,
        input: SwitchAdvisorInput,
        onResult: (List<SwitchRecommendation>) -> Unit
    ): List<SwitchRecommendation> {
        val opponent = input.opponent
        if (!input.activeBattle ||
            opponent == null ||
            opponent.isEmpty ||
            !opponent.isValid ||
            opponent.currentHp <= 0 ||
            !input.hasHealthyBench()
        ) {
            clear()
            return emptyList()
        }

        val key = SwitchAdvisorCacheKey.from(input)

        if (key == lastCompletedKey) {
            // Update latest key and supersede any in-flight work BEFORE early return so an
            // obsolete background job (e.g. A -> B (slow) -> A) can never publish over A.
            if (latestRequestedKey != key || activeJob?.isActive == true) {
                requestToken++
                latestRequestedKey = key
                activeJob?.cancel()
                activeJob = null
                inFlightKey = null
            }
            cachedRecommendations = lastCompletedRecommendations
            return cachedRecommendations
        }

        if (key == latestRequestedKey && key == inFlightKey && activeJob?.isActive == true) {
            return cachedRecommendations
        }

        val token = ++requestToken
        latestRequestedKey = key

        // Drop stale displayed rows immediately whenever the full calculation key changes;
        // completed rows are retained only on an exact cache hit above.
        cachedRecommendations = emptyList()

        inFlightKey = key
        activeJob?.cancel()
        activeJob = scope.launch {
            val ranked = withContext(dispatcher) {
                evaluateSync(input)
            }
            if (latestRequestedKey != key || requestToken != token) return@launch
            lastCompletedKey = key
            lastCompletedRecommendations = ranked
            cachedRecommendations = ranked
            inFlightKey = null
            onResult(ranked)
        }
        return cachedRecommendations
    }

    /** Runs one synchronous ranking pass using the authorized calculator boundary. */
    fun evaluateSync(input: SwitchAdvisorInput): List<SwitchRecommendation> {
        val evaluator = evaluatorOverride?.invoke(input) ?: buildAuthorizedEvaluator(input)
        return SwitchAdvisor.rankSwitches(
            playerParty = input.playerParty,
            activePlayerSlot = input.activePlayerSlot,
            opponent = input.opponent,
            profile = input.profile,
            dataPack = input.dataPack,
            runtimeTrust = input.runtimeTrust,
            damageEvaluator = evaluator
        )
    }

    fun cancel() {
        requestToken++
        latestRequestedKey = null
        activeJob?.cancel()
        activeJob = null
        inFlightKey = null
    }

    fun clear() {
        cancel()
        lastCompletedKey = null
        lastCompletedRecommendations = emptyList()
        cachedRecommendations = emptyList()
    }

    internal fun buildAuthorizedEvaluator(input: SwitchAdvisorInput): SwitchDamageEvaluator {
        val capability = CalcCapabilityPolicy.capabilityFor(input.profile)
            ?: return { _, _, _ -> null }
        val isExactHns = capability.ruleset == CalcRuleset.HNS_2_0_5
        val isExpansionItems = input.profile.hasPhysSpecSplit
        val opponent = input.opponent ?: return { _, _, _ -> null }

        fun speciesNameOf(mon: ParsedPokemon): String =
            input.profile.customSpecies[mon.species]?.name
                ?: input.dataPack.getSpecies(mon.species)?.name
                ?: ""

        fun partySlotOf(mon: ParsedPokemon): Int? {
            for (i in input.playerParty.indices) {
                if (input.playerParty[i] === mon) return i
            }
            return input.playerParty.indexOfFirst { it == mon }.takeIf { it >= 0 }
        }

        return evaluator@{ attacker, defender, moveId ->
            val moveInfo = input.dataPack.getMove(moveId) ?: return@evaluator null
            if (moveInfo.category == MoveCategory.STATUS) return@evaluator null

            val isOpponentAttacker = when {
                attacker === opponent -> true
                defender === opponent -> false
                else -> attacker == opponent
            }
            val benchMon = if (isOpponentAttacker) defender else attacker
            val benchSlot = partySlotOf(benchMon) ?: return@evaluator null
            val opponentSpecies = speciesNameOf(opponent)
            val benchSpecies = speciesNameOf(benchMon)
            if (opponentSpecies.isBlank() || benchSpecies.isBlank()) return@evaluator null

            // A benched mon switching in starts with neutral stat stages (0); the active opponent
            // retains its observed in-battle stages.
            val benchState = CalcParticipantPresenter.attacker(
                party = input.playerParty,
                selectedIndex = benchSlot,
                speciesNameOf = ::speciesNameOf,
                playerStages = StatStages(),
                isExpansionItems = isExpansionItems,
                playerBattlerState = input.playerBattlerState,
                isExactHns = isExactHns,
                activeBattle = input.activeBattle
            )
            val opponentState = CalcParticipantPresenter.defender(
                observedOpponent = opponent,
                chosenSpecies = opponentSpecies,
                enemyStages = input.enemyStages,
                isExpansionItems = isExpansionItems,
                enemyBattlerState = input.enemyBattlerState,
                isExactHns = isExactHns,
                activeEnemySlot = input.activeEnemySlot,
                activeBattle = input.activeBattle
            )

            val attackerState = if (isOpponentAttacker) opponentState else benchState
            val defenderState = if (isOpponentAttacker) benchState else opponentState
            val attackerObservation = if (isOpponentAttacker) input.enemyBattlerState else input.playerBattlerState
            val defenderObservation = if (isOpponentAttacker) input.playerBattlerState else input.enemyBattlerState

            val outcome = CalcRequestBoundary.build(
                profile = input.profile,
                trust = input.runtimeTrust,
                attacker = attackerState,
                defender = defenderState,
                move = CalcMoveInput(name = moveInfo.name),
                field = CalcFieldInput(gameType = CalcGameTypes.SINGLES),
                challengeSettings = input.challengeSettings,
                playerBattlerState = attackerObservation,
                enemyBattlerState = defenderObservation,
                activeBattle = input.activeBattle,
                battlePresence = if (input.activeBattle) BattlePresence.OBSERVED else BattlePresence.UNKNOWN
            )

            val ready = outcome as? CalcRequestOutcome.Ready ?: return@evaluator null
            if (ready.verdict.support == CalcSupport.UNSUPPORTED) return@evaluator null

            val response = CalcAuthorizedExecution.calculate(ready.verdict) { request ->
                calculator.calculate(request)
            }
            if (!response.success) return@evaluator null

            val targetMaxHp = response.defenderMaxHP.takeIf { it > 0 } ?: defender.maxHp
            if (targetMaxHp <= 0) return@evaluator null

            SwitchDamageEstimate(
                minDamage = response.minDamage,
                maxDamage = response.maxDamage,
                defenderMaxHp = targetMaxHp,
                support = ready.verdict.support,
                koChanceText = response.koChanceText
            )
        }
    }
}
