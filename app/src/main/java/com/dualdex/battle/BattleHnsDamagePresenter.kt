package com.dualdex.battle

import com.dualdex.calculator.CalcDataOverrides
import com.dualdex.calculator.CalcCapabilityPolicy
import com.dualdex.calculator.CalcFieldInput
import com.dualdex.calculator.CalcGameTypes
import com.dualdex.calculator.CalcHnsRuntimeRules
import com.dualdex.calculator.CalcLimitation
import com.dualdex.calculator.CalcMoveInput
import com.dualdex.calculator.CalcParticipantPresenter
import com.dualdex.calculator.CalcRequestBoundary
import com.dualdex.calculator.CalcRequestOutcome
import com.dualdex.calculator.CalcSupport
import com.dualdex.calculator.CalcCapabilityVerdict
import com.dualdex.calculator.HnsAbilityRequestDecision
import com.dualdex.calculator.HnsAbilityRequestRelevance
import com.dualdex.calculator.HnsItemRequestDecision
import com.dualdex.calculator.HnsItemRequestRelevance
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveInfo
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsFairyTypeMappings
import com.dualdex.pokemon.hns.HnsOptionStyle
import com.dualdex.pokemon.hns.normalizeHnsBattlerTypes
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust

/** Immutable ViewModel observations needed to prepare one exact H&S live calculation. */
data class BattleHnsCalculationContext(
    val playerParty: List<ParsedPokemon>,
    val activePlayerSlot: Int,
    val activeEnemySlot: Int?,
    val challengeSettings: HnsChallengeSettingsSnapshot?,
    val playerBattlerState: BattlerRuntimeObservation?,
    val enemyBattlerState: BattlerRuntimeObservation?,
    val activeBattle: Boolean
)

/** Stable, value-based participant key (the ParsedPokemon move/PP fields are primitive arrays). */
data class BattlePokemonPresentationKey(
    val species: Int,
    val level: Int,
    val heldItem: Int,
    val natureName: String,
    val currentHp: Int,
    val maxHp: Int,
    val statusCondition: Long,
    val ivs: List<Int>,
    val evs: List<Int>,
    val moves: List<Int>,
    val pp: List<Int>
) {
    companion object {
        fun from(mon: ParsedPokemon?): BattlePokemonPresentationKey? = mon?.let {
            BattlePokemonPresentationKey(
                species = it.species,
                level = it.level,
                heldItem = it.heldItem,
                natureName = it.natureName,
                currentHp = it.currentHp,
                maxHp = it.maxHp,
                statusCondition = it.statusCondition,
                ivs = listOf(it.hpIv, it.attackIv, it.defenseIv, it.spAttackIv, it.spDefenseIv, it.speedIv),
                evs = listOf(it.hpEv, it.attackEv, it.defenseEv, it.spAttackEv, it.spDefenseEv, it.speedEv),
                moves = it.moves.toList(),
                pp = it.pp.toList()
            )
        }
    }
}

/** All calculator operands the Battle move-presentation job reads, including observed H&S state. */
data class BattleMovePresentationCacheKey(
    val attacker: BattlePokemonPresentationKey?,
    val defender: BattlePokemonPresentationKey?,
    val profile: RomHackProfile,
    val runtimeTrust: RuntimeRomTrust,
    val playerStages: StatStages,
    val enemyStages: StatStages,
    val activeBattle: Boolean,
    val activePlayerSlot: Int,
    val activeEnemySlot: Int?,
    val challengeSettings: HnsChallengeSettingsSnapshot?,
    val playerBattlerState: BattlerRuntimeObservation?,
    val enemyBattlerState: BattlerRuntimeObservation?
) {
    companion object {
        fun from(
            attacker: ParsedPokemon?,
            defender: ParsedPokemon?,
            profile: RomHackProfile,
            runtimeTrust: RuntimeRomTrust,
            playerStages: StatStages,
            enemyStages: StatStages,
            context: BattleHnsCalculationContext
        ) = BattleMovePresentationCacheKey(
            attacker = BattlePokemonPresentationKey.from(attacker),
            defender = BattlePokemonPresentationKey.from(defender),
            profile = profile,
            runtimeTrust = runtimeTrust,
            playerStages = playerStages,
            enemyStages = enemyStages,
            activeBattle = context.activeBattle,
            activePlayerSlot = context.activePlayerSlot,
            activeEnemySlot = context.activeEnemySlot,
            challengeSettings = context.challengeSettings,
            playerBattlerState = context.playerBattlerState,
            enemyBattlerState = context.enemyBattlerState
        )
    }
}

/** A boundary-authorized result or refusal, ready for the move-card model. */
data class BattleHnsDamagePresentation(
    val category: MoveCategory?,
    val moveType: PokemonType? = null,
    val effectiveness: EffectivenessLabel? = null,
    val effectivenessConfidence: DataConfidence = DataConfidence.UNAVAILABLE,
    val confidence: DamageConfidence = DamageConfidence.UNAVAILABLE,
    val minDamage: Int = 0,
    val maxDamage: Int = 0,
    val range: List<Int> = emptyList(),
    val koChanceText: String = "",
    val support: CalcSupport? = null,
    val limitations: List<CalcLimitation> = emptyList(),
    /** Structured unresolved ability blockers; the UI must not truncate away blocker names. */
    val abilityBlockers: List<HnsAbilityRequestDecision> = emptyList(),
    /** Structured unresolved held-item blockers (side + exact pinned item identity). */
    val itemBlockers: List<HnsItemRequestDecision> = emptyList(),
    /** Every blocker class of a refusal, in display order; nothing is hidden behind another class. */
    val blockers: List<DamageBlockerPresentation> = emptyList(),
    val unavailableReason: String? = null
)

/**
 * Connects Battle move cards to the production authorization boundary. This class prepares live
 * inputs with the same participant presenter as CalcTab, asks CalcRequestBoundary for permission,
 * and only executes a Ready ESTIMATED H&S request. It contains no H&S mechanics policy of its own.
 */
object BattleHnsDamagePresenter {

    private data class HnsMoveTypePresentation(
        val moveType: PokemonType?,
        val effectiveness: EffectivenessLabel? = null,
        val effectivenessConfidence: DataConfidence = DataConfidence.UNAVAILABLE
    )

    fun build(
        moveInfo: MoveInfo,
        defender: ParsedPokemon?,
        profile: RomHackProfile,
        runtimeTrust: RuntimeRomTrust?,
        context: BattleHnsCalculationContext?,
        calculator: BattleDamageCalculator,
        attackerStages: StatStages,
        defenderStages: StatStages
    ): BattleHnsDamagePresentation {
        val rules = CalcRequestBoundary.resolveHnsRuntimeRules(
            profile = profile,
            trust = runtimeTrust,
            challengeSettings = context?.challengeSettings
        )
        val typePresentation = resolveTypePresentation(
            moveInfo, defender, profile, runtimeTrust, rules, context
        )
        val category = resolveCategory(moveInfo, profile, rules, typePresentation.moveType)

        if (context == null || defender == null || !context.activeBattle) {
            return BattleHnsDamagePresentation(
                category = category,
                moveType = typePresentation.moveType,
                effectiveness = typePresentation.effectiveness,
                effectivenessConfidence = typePresentation.effectivenessConfidence,
                unavailableReason = "Live battle state incomplete",
                limitations = listOf(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)
            )
        }

        val pack = GameDataPackRegistry.getForProfile(profile)
        val attackerState = CalcParticipantPresenter.attacker(
            party = context.playerParty,
            selectedIndex = context.activePlayerSlot,
            speciesNameOf = { member -> pack.getSpecies(member.species)?.name.orEmpty() },
            playerStages = attackerStages,
            isExpansionItems = profile.hasPhysSpecSplit,
            playerBattlerState = context.playerBattlerState,
            isExactHns = true,
            activeBattle = true
        )
        val defenderSpecies = pack.getSpecies(defender.species)?.name.orEmpty()
        val defenderState = CalcParticipantPresenter.defender(
            observedOpponent = defender,
            chosenSpecies = defenderSpecies,
            enemyStages = defenderStages,
            isExpansionItems = profile.hasPhysSpecSplit,
            enemyBattlerState = context.enemyBattlerState,
            isExactHns = true,
            activeEnemySlot = context.activeEnemySlot,
            activeBattle = true
        )

        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = runtimeTrust,
            attacker = attackerState,
            defender = defenderState,
            move = CalcMoveInput(name = moveInfo.name),
            // In an active H&S battle the boundary replaces all caller defaults below with
            // observed runtime state, or refuses if the corresponding word was not read.
            field = CalcFieldInput(gameType = CalcGameTypes.SINGLES),
            challengeSettings = context.challengeSettings,
            playerBattlerState = context.playerBattlerState,
            enemyBattlerState = context.enemyBattlerState,
            activeBattle = true
        )

        val verdict = when (outcome) {
            is CalcRequestOutcome.Refused -> outcome.verdict
            is CalcRequestOutcome.Ready -> outcome.verdict
        }
        HnsFieldDiagnostics.record(verdict.hnsFieldDiagnostics?.fieldState)

        return when (outcome) {
            is CalcRequestOutcome.Refused -> {
                val abilityBlockers = outcome.verdict.hnsAbilityDecisions.filter {
                    it.relevance != HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
                }
                val itemBlockers = outcome.verdict.hnsItemDecisions.filter {
                    it.relevance != HnsItemRequestRelevance.PROVEN_IRRELEVANT &&
                        it.relevance != HnsItemRequestRelevance.MODELLED
                }
                val blockers = DamageBlockerPresentation.from(outcome.verdict, observedDoubles(context))
                BattleHnsDamagePresentation(
                    category = category,
                    moveType = typePresentation.moveType,
                    effectiveness = typePresentation.effectiveness,
                    effectivenessConfidence = typePresentation.effectivenessConfidence,
                    support = outcome.verdict.support,
                    limitations = outcome.verdict.limitations,
                    abilityBlockers = abilityBlockers,
                    itemBlockers = itemBlockers,
                    blockers = blockers,
                    unavailableReason = DamageBlockerPresentation.headline(blockers)
                )
            }
            is CalcRequestOutcome.Ready -> {
                val requestTypePresentation = resolveTypePresentation(
                    moveInfo = moveInfo,
                    defender = defender,
                    profile = profile,
                    runtimeTrust = runtimeTrust,
                    rules = rules,
                    context = context,
                    request = outcome.request
                )
                val authorizedCategory = categoryFromRequest(outcome.request)
                    ?: resolveCategory(moveInfo, profile, rules, requestTypePresentation.moveType)
                if (outcome.verdict.support != CalcSupport.ESTIMATED ||
                    authorizedCategory == MoveCategory.STATUS ||
                    (outcome.request.moveOverride?.basePower ?: moveInfo.power) <= 0
                ) {
                    BattleHnsDamagePresentation(
                        category = authorizedCategory ?: category,
                        moveType = requestTypePresentation.moveType,
                        effectiveness = requestTypePresentation.effectiveness,
                        effectivenessConfidence = requestTypePresentation.effectivenessConfidence,
                        support = outcome.verdict.support,
                        limitations = outcome.verdict.limitations,
                        unavailableReason = if (outcome.verdict.support == CalcSupport.ESTIMATED) null else "Calculation not supported"
                    )
                } else {
                    val response = calculator.calculate(outcome.request)
                    if (response.success && response.maxDamage > 0) {
                        BattleHnsDamagePresentation(
                            category = parseCategory(response.moveCategory) ?: authorizedCategory ?: category,
                            moveType = requestTypePresentation.moveType,
                            effectiveness = requestTypePresentation.effectiveness,
                            effectivenessConfidence = requestTypePresentation.effectivenessConfidence,
                            confidence = DamageConfidence.ESTIMATE,
                            minDamage = response.minDamage,
                            maxDamage = response.maxDamage,
                            range = response.range,
                            koChanceText = response.koChanceText,
                            support = outcome.verdict.support
                        )
                    } else {
                        BattleHnsDamagePresentation(
                            category = parseCategory(response.moveCategory) ?: authorizedCategory ?: category,
                            moveType = requestTypePresentation.moveType,
                            effectiveness = requestTypePresentation.effectiveness,
                            effectivenessConfidence = requestTypePresentation.effectivenessConfidence,
                            support = outcome.verdict.support,
                            unavailableReason = "Calculation unavailable"
                        )
                    }
                }
            }
        }
    }

    private fun categoryFromRequest(request: com.dualdex.calculator.DamageCalculationRequest): MoveCategory? {
        val override = request.moveOverride ?: return null
        parseCategory(override.category)?.let { return it }
        if (request.hnsRuntimeRules?.optionStyle != HnsOptionStyle.TYPE_BASED) return null
        val type = PokemonType.fromString(override.type) ?: return null
        return MoveEffectiveness.categoryForType(type)
    }

    private fun resolveCategory(
        moveInfo: MoveInfo,
        profile: RomHackProfile,
        rules: CalcHnsRuntimeRules?,
        effectiveMoveType: PokemonType?
    ): MoveCategory? {
        val pack = GameDataPackRegistry.getForProfile(profile)
        val pinnedMove = pack.getMoveByName(moveInfo.name) ?: return null
        if (pinnedMove.category == MoveCategory.STATUS) return MoveCategory.STATUS
        return when (rules?.optionStyle) {
            HnsOptionStyle.PER_MOVE_SPLIT -> CalcDataOverrides.resolveHnsMoveCategory(moveInfo.name, profile, rules)
            HnsOptionStyle.TYPE_BASED -> effectiveMoveType?.let(MoveEffectiveness::categoryForType)
            else -> null
        }
    }

    private fun resolveTypePresentation(
        moveInfo: MoveInfo,
        defender: ParsedPokemon?,
        profile: RomHackProfile,
        runtimeTrust: RuntimeRomTrust?,
        rules: CalcHnsRuntimeRules?,
        context: BattleHnsCalculationContext?,
        request: com.dualdex.calculator.DamageCalculationRequest? = null
    ): HnsMoveTypePresentation {
        val pack = GameDataPackRegistry.getForProfile(profile)
        val pinnedMove = pack.getMoveByName(moveInfo.name) ?: return HnsMoveTypePresentation(null)
        val moveOverride = request?.moveOverride
            ?: CalcDataOverrides.buildHnsMoveOverride(moveInfo.name, profile, rules)
            ?: return HnsMoveTypePresentation(null)
        if (pinnedMove.type == PokemonType.FAIRY && rules?.fairyTypesEnabled == null) {
            return HnsMoveTypePresentation(null)
        }
        var effectiveMoveType = PokemonType.fromString(moveOverride.type)
            ?: return HnsMoveTypePresentation(null)

        if (context?.activeBattle == true) {
            val observations = matchingLiveObservations(profile, runtimeTrust, context)
                ?: return HnsMoveTypePresentation(null)
            val player = observations.first
            if (player.volatileElectrified ||
                (effectiveMoveType == PokemonType.NORMAL &&
                    (player.fieldStatuses and HnsBattlerRuntimeStateIds.STATUS_FIELD_ION_DELUGE) != 0)
            ) {
                effectiveMoveType = PokemonType.ELECTRIC
            }
        }

        if (pinnedMove.category == MoveCategory.STATUS || defender == null || defender.isEmpty || !defender.isValid) {
            return HnsMoveTypePresentation(effectiveMoveType)
        }

        val defenderName = pack.getSpecies(defender.species)?.name
            ?: return HnsMoveTypePresentation(effectiveMoveType)
        if (rules == null || rules.randomTypesEnabled != false || rules.randomTypeEffectivenessEnabled != false) {
            return HnsMoveTypePresentation(effectiveMoveType)
        }
        if (rules.fairyTypesEnabled == null &&
            HnsFairyTypeMappings.getPreFairyTypes(defenderName) != null
        ) {
            return HnsMoveTypePresentation(effectiveMoveType)
        }

        val defenderTypeNames = request?.defenderOverride?.types
            ?: CalcDataOverrides.buildSpeciesOverride(defenderName, pack, rules.fairyTypesEnabled)?.types
            ?: return HnsMoveTypePresentation(effectiveMoveType)
        val defenderTypes = defenderTypeNames.map { PokemonType.fromString(it) ?: return HnsMoveTypePresentation(effectiveMoveType) }
        if (defenderTypes.isEmpty() || defenderTypes.size > 2) return HnsMoveTypePresentation(effectiveMoveType)

        if (context?.activeBattle == true) {
            val observations = matchingLiveObservations(profile, runtimeTrust, context)
                ?: return HnsMoveTypePresentation(effectiveMoveType)
            val enemy = observations.second
            if (!enemy.persistentVolatilesObserved || enemy.volatileRoostActive) {
                return HnsMoveTypePresentation(effectiveMoveType)
            }
            val observedTypeNames = normalizeHnsBattlerTypes(enemy.types)
                ?: return HnsMoveTypePresentation(effectiveMoveType)
            val observedTypes = observedTypeNames.map { typeName ->
                PokemonType.fromString(typeName) ?: return HnsMoveTypePresentation(effectiveMoveType)
            }
            if (observedTypes.isEmpty() || observedTypes.size > 2 || observedTypes.toSet() != defenderTypes.toSet()) {
                return HnsMoveTypePresentation(effectiveMoveType)
            }
        }

        val label = MoveEffectiveness.confidence(
            moveType = effectiveMoveType,
            defenderType1 = defenderTypes[0],
            defenderType2 = defenderTypes.getOrNull(1),
            steelResistsGhostDark = profile.steelResistsGhostDark,
            dataPack = pack
        ) ?: return HnsMoveTypePresentation(effectiveMoveType)
        val confidence = if (CalcCapabilityPolicy.isExactRuntimeVerified(profile, runtimeTrust) &&
            pack.isMoveAuthoritative(moveInfo.id) && pack.isSpeciesAuthoritative(defender.species)
        ) DataConfidence.VERIFIED else DataConfidence.ESTIMATE
        return HnsMoveTypePresentation(effectiveMoveType, label, confidence)
    }

    private fun matchingLiveObservations(
        profile: RomHackProfile,
        runtimeTrust: RuntimeRomTrust?,
        context: BattleHnsCalculationContext
    ): Pair<com.dualdex.pokemon.hns.HnsBattlerRuntimeState, com.dualdex.pokemon.hns.HnsBattlerRuntimeState>? {
        if (!CalcCapabilityPolicy.isExactRuntimeVerified(profile, runtimeTrust)) return null
        val player = context.playerBattlerState?.state ?: return null
        val enemy = context.enemyBattlerState?.state ?: return null
        val pairMatches = player.status == HnsBattlerRuntimeStatus.OBSERVED &&
            enemy.status == HnsBattlerRuntimeStatus.OBSERVED &&
            player.partySlot == context.activePlayerSlot &&
            context.activeEnemySlot != null && enemy.partySlot == context.activeEnemySlot &&
            player.volatilesObserved &&
            player.fieldStatusesReadable && enemy.fieldStatusesReadable &&
            player.fieldStatuses == enemy.fieldStatuses
        return if (pairMatches) player to enemy else null
    }

    private fun parseCategory(category: String?): MoveCategory? = when (category?.lowercase()) {
        "physical" -> MoveCategory.PHYSICAL
        "special" -> MoveCategory.SPECIAL
        "status" -> MoveCategory.STATUS
        else -> null
    }

    private fun observedDoubles(context: BattleHnsCalculationContext?): Boolean {
        val playerState = context?.playerBattlerState?.state
        val enemyState = context?.enemyBattlerState?.state
        return playerState?.battlersCountReadable == true &&
            enemyState?.battlersCountReadable == true &&
            playerState.battlersCount == 4 && enemyState.battlersCount == 4
    }
}
