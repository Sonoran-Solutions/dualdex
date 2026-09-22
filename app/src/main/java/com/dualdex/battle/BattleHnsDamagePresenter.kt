package com.dualdex.battle

import com.dualdex.calculator.CalcDataOverrides
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
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveInfo
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsOptionStyle
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
    val confidence: DamageConfidence = DamageConfidence.UNAVAILABLE,
    val minDamage: Int = 0,
    val maxDamage: Int = 0,
    val range: List<Int> = emptyList(),
    val koChanceText: String = "",
    val support: CalcSupport? = null,
    val limitations: List<CalcLimitation> = emptyList(),
    val unavailableReason: String? = null
)

/**
 * Connects Battle move cards to the production authorization boundary. This class prepares live
 * inputs with the same participant presenter as CalcTab, asks CalcRequestBoundary for permission,
 * and only executes a Ready ESTIMATED H&S request. It contains no H&S mechanics policy of its own.
 */
object BattleHnsDamagePresenter {

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
        val category = resolveCategory(moveInfo, profile, rules, context)

        if (context == null || defender == null || !context.activeBattle) {
            return BattleHnsDamagePresentation(
                category = category,
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

        return when (outcome) {
            is CalcRequestOutcome.Refused -> BattleHnsDamagePresentation(
                category = category,
                support = outcome.verdict.support,
                limitations = outcome.verdict.limitations,
                unavailableReason = refusalReason(outcome.verdict, context)
            )
            is CalcRequestOutcome.Ready -> {
                val authorizedCategory = categoryFromRequest(outcome.request)
                if (outcome.verdict.support != CalcSupport.ESTIMATED ||
                    authorizedCategory == MoveCategory.STATUS ||
                    (outcome.request.moveOverride?.basePower ?: moveInfo.power) <= 0
                ) {
                    BattleHnsDamagePresentation(
                        category = authorizedCategory ?: category,
                        support = outcome.verdict.support,
                        limitations = outcome.verdict.limitations,
                        unavailableReason = if (outcome.verdict.support == CalcSupport.ESTIMATED) null else "Calculation not supported"
                    )
                } else {
                    val response = calculator.calculate(outcome.request)
                    if (response.success && response.maxDamage > 0) {
                        BattleHnsDamagePresentation(
                            category = parseCategory(response.moveCategory) ?: authorizedCategory ?: category,
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
        context: BattleHnsCalculationContext?
    ): MoveCategory? {
        val category = CalcDataOverrides.resolveHnsMoveCategory(moveInfo.name, profile, rules)
        if (category == MoveCategory.STATUS || rules?.optionStyle != HnsOptionStyle.TYPE_BASED) {
            return category
        }

        val move = CalcDataOverrides.buildHnsMoveOverride(moveInfo.name, profile, rules) ?: return null
        val pinnedMove = GameDataPackRegistry.getForProfile(profile).getMoveByName(moveInfo.name) ?: return null
        if (pinnedMove.type == PokemonType.FAIRY && rules.fairyTypesEnabled == null) return null

        // Type-based category follows the live effective move type. The same exact observations
        // the boundary uses distinguish a static type from Electrify / Ion Deluge; an unreadable
        // operand cannot be replaced with the static type just for the card label.
        if (context?.activeBattle != true) return category
        val player = context.playerBattlerState?.state ?: return null
        val enemy = context.enemyBattlerState?.state ?: return null
        val livePairMatches = player.status == HnsBattlerRuntimeStatus.OBSERVED &&
            enemy.status == HnsBattlerRuntimeStatus.OBSERVED &&
            player.partySlot == context.activePlayerSlot &&
            enemy.partySlot == context.activeEnemySlot &&
            player.volatilesObserved &&
            player.fieldStatusesReadable && enemy.fieldStatusesReadable &&
            player.fieldStatuses == enemy.fieldStatuses
        if (!livePairMatches) return null

        val staticType = PokemonType.fromString(move.type) ?: return null
        val effectiveType = if (player.volatileElectrified ||
            (staticType == PokemonType.NORMAL &&
                (player.fieldStatuses and HnsBattlerRuntimeStateIds.STATUS_FIELD_ION_DELUGE) != 0)
        ) {
            PokemonType.ELECTRIC
        } else {
            staticType
        }
        return MoveEffectiveness.categoryForType(effectiveType)
    }

    private fun parseCategory(category: String?): MoveCategory? = when (category?.lowercase()) {
        "physical" -> MoveCategory.PHYSICAL
        "special" -> MoveCategory.SPECIAL
        "status" -> MoveCategory.STATUS
        else -> null
    }

    private fun refusalReason(
        verdict: CalcCapabilityVerdict,
        context: BattleHnsCalculationContext?
    ): String {
        val playerState = context?.playerBattlerState?.state
        val enemyState = context?.enemyBattlerState?.state
        val observedDoubles = playerState?.battlersCountReadable == true &&
            enemyState?.battlersCountReadable == true &&
            playerState.battlersCount == 4 && enemyState.battlersCount == 4
        return when {
            CalcLimitation.HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED in verdict.limitations || observedDoubles ->
                "Doubles not supported"
            CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED in verdict.limitations -> "Ability effect not modelled"
            CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED in verdict.limitations ||
                CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE in verdict.limitations -> "Item effect not modelled"
            CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED in verdict.limitations ||
                CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED in verdict.limitations -> "Move effect not modelled"
            CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE in verdict.limitations -> "Move category unavailable"
            CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN in verdict.limitations ||
                CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED in verdict.limitations ||
                CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED in verdict.limitations ||
                CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE in verdict.limitations ||
                CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE in verdict.limitations -> "Live battle state incomplete"
            CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE in verdict.limitations -> "Challenge settings unavailable"
            else -> "Damage interaction not modelled"
        }
    }
}
