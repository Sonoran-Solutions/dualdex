package com.dualdex.battle

import com.dualdex.calculator.CalcSupport
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveInfo
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.unbound.UnboundDataPack
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import kotlin.math.roundToInt

/** Whether a switch recommendation was driven by authorized calculator damage ranges or type-matchup fallback. */
enum class SwitchRecommendationSource {
    CALCULATOR,
    TYPE_FALLBACK
}

/** Honest user-facing confidence tier for a switch recommendation or individual calc direction. */
enum class SwitchConfidenceTier(val badgeLabel: String) {
    VERIFIED("Calc"),
    ESTIMATED("Est."),
    TYPE_FALLBACK("Types")
}

/** Semantic tone for the defensive matchup badge rendered in the Battle Console. */
enum class SwitchDefenseTone {
    STRONG,
    NEUTRAL,
    WEAK,
    DANGER
}

/**
 * Result of evaluating one attacking move via DualDex's authorized calculator pipeline.
 * Pure JVM value type with no Android dependencies.
 */
data class SwitchDamageEstimate(
    val minDamage: Int,
    val maxDamage: Int,
    val defenderMaxHp: Int,
    val support: CalcSupport,
    val koChanceText: String = ""
) {
    val minPercent: Double
        get() = if (defenderMaxHp > 0) (minDamage.coerceAtLeast(0) * 100.0) / defenderMaxHp else 0.0

    val maxPercent: Double
        get() = if (defenderMaxHp > 0) (maxDamage.coerceAtLeast(0) * 100.0) / defenderMaxHp else 0.0
}

/** Pure calculator callback injected into [SwitchAdvisor.rankSwitches]. */
typealias SwitchDamageEvaluator = (
    attacker: ParsedPokemon,
    defender: ParsedPokemon,
    moveId: Int
) -> SwitchDamageEstimate?

/** One ranked healthy bench Pokémon recommended as a switch-in against the active opponent. */
data class SwitchRecommendation(
    val slot: Int,
    val speciesName: String,
    val displayName: String,
    val bestMoveId: Int?,
    val bestMoveName: String?,
    val bestMoveType: PokemonType?,
    /** Min % of opponent max HP from authorized calculator, or null when using type fallback. */
    val offenseMinPercent: Double?,
    /** Max % of opponent max HP from authorized calculator, or null when using type fallback. */
    val offenseMaxPercent: Double?,
    val offenseEffectiveness: Double?,
    val offenseTier: SwitchConfidenceTier,
    val worstThreatMoveName: String?,
    /** Min % of bench mon max HP taken from opponent's worst move, or null when using type fallback. */
    val threatMinPercent: Double?,
    /** Max % of bench mon max HP taken from opponent's worst move, or null when withheld/unavailable. */
    val threatMaxPercent: Double?,
    /** Worst incoming type multiplier, or null when enemy threat coverage is incomplete. */
    val worstDefensiveMultiplier: Double?,
    val defenseTier: SwitchConfidenceTier,
    val defenseTone: SwitchDefenseTone,
    val source: SwitchRecommendationSource,
    val confidenceTier: SwitchConfidenceTier,
    val reasonTag: String,
    val offenseSummary: String,
    val defenseBadge: String,
    val score: Double,
    val hasUnmodelledEnemyThreats: Boolean = false
)

/**
 * Pure Kotlin calc-driven "Best Switch" ranking engine.
 *
 * Ranks healthy, non-egg benched party members against the active single-battle opponent.
 * Requires resolved species typing for both combatants and authorized calculator evidence from
 * [damageEvaluator]; completely refused matchups or unresolved species are omitted rather than
 * ranked, and any refused/unresolved enemy damaging move withholds "Safe Pivot" and worst-threat
 * claims.
 */
object SwitchAdvisor {

    private const val PARALYSIS_MASK = 1L shl 6

    private data class OpponentMoveScan(
        val usableDamagingMoves: List<MoveInfo>,
        val unresolvedMoveCount: Int
    )

    private data class OffenseEvaluation(
        val moveId: Int?,
        val moveName: String?,
        val moveType: PokemonType?,
        val minPercent: Double?,
        val maxPercent: Double?,
        val currentHpMinPercent: Double?,
        val currentHpMaxPercent: Double?,
        val effectiveness: Double?,
        val isStab: Boolean,
        val power: Int,
        val tier: SwitchConfidenceTier,
        val score: Double
    )

    private data class DefenseEvaluation(
        val worstThreatMoveName: String?,
        val minPercent: Double?,
        val maxPercent: Double?,
        val currentHpMaxPercent: Double?,
        val knownDangerCurMaxPercent: Double?,
        val worstMultiplier: Double?,
        val tier: SwitchConfidenceTier,
        val tone: SwitchDefenseTone,
        val score: Double,
        val hasAnyCalc: Boolean,
        val hasCompleteCalc: Boolean,
        val hasUnmodelledEnemyMoves: Boolean
    )

    fun rankSwitches(
        playerParty: List<ParsedPokemon>,
        activePlayerSlot: Int,
        opponent: ParsedPokemon?,
        profile: RomHackProfile,
        dataPack: GameDataPack,
        runtimeTrust: RuntimeRomTrust,
        damageEvaluator: SwitchDamageEvaluator
    ): List<SwitchRecommendation> {
        if (opponent == null || opponent.isEmpty || !opponent.isValid || opponent.maxHp <= 0 || opponent.currentHp <= 0) {
            return emptyList()
        }

        val opponentTypes = resolveTypes(opponent, profile, dataPack)
        if (opponentTypes.isEmpty()) {
            return emptyList()
        }
        val opponentMoveScan = collectOpponentDamagingMoves(opponent, dataPack)
        val opponentEffectiveSpeed = effectiveSpeed(opponent)

        val recommendations = ArrayList<SwitchRecommendation>(playerParty.size)

        playerParty.forEachIndexed { slot, mon ->
            if (!isEligibleBenchMember(slot, activePlayerSlot, mon)) return@forEachIndexed

            val benchTypes = resolveTypes(mon, profile, dataPack)
            if (benchTypes.isEmpty()) return@forEachIndexed

            val speciesName = resolveSpeciesName(mon, profile, dataPack)
            val displayName = mon.nickname.trim().ifEmpty { speciesName }
            val hpRatio = (mon.currentHp.toDouble() / mon.maxHp.coerceAtLeast(1)).coerceIn(0.0, 1.0)

            val offense = evaluateOffense(
                benchMon = mon,
                benchTypes = benchTypes,
                opponent = opponent,
                opponentTypes = opponentTypes,
                dataPack = dataPack,
                runtimeTrust = runtimeTrust,
                damageEvaluator = damageEvaluator
            )

            val defense = evaluateDefense(
                benchMon = mon,
                benchTypes = benchTypes,
                hpRatio = hpRatio,
                opponent = opponent,
                opponentTypes = opponentTypes,
                opponentMoveScan = opponentMoveScan,
                dataPack = dataPack,
                runtimeTrust = runtimeTrust,
                damageEvaluator = damageEvaluator
            )

            val hasAnyCalc = offense.tier != SwitchConfidenceTier.TYPE_FALLBACK || defense.hasAnyCalc
            if (!hasAnyCalc) {
                return@forEachIndexed
            }

            val source = SwitchRecommendationSource.CALCULATOR
            val confidenceTier = when {
                !defense.hasUnmodelledEnemyMoves &&
                    offense.tier == SwitchConfidenceTier.VERIFIED &&
                    defense.hasCompleteCalc &&
                    defense.tier == SwitchConfidenceTier.VERIFIED -> SwitchConfidenceTier.VERIFIED
                else -> SwitchConfidenceTier.ESTIMATED
            }

            val isFaster = effectiveSpeed(mon) > opponentEffectiveSpeed
            var compositeScore = offense.score + defense.score
            if (isFaster) {
                compositeScore += 6.0
                if (!defense.hasUnmodelledEnemyMoves &&
                    offense.currentHpMinPercent != null &&
                    offense.currentHpMinPercent >= 100.0 &&
                    (defense.currentHpMaxPercent == null || defense.currentHpMaxPercent < 100.0)
                ) {
                    compositeScore += 8.0
                }
            }
            if (defense.hasCompleteCalc) {
                compositeScore += (hpRatio - 1.0) * 5.0
            }

            val reasonTag = buildReasonTag(offense, defense)
            val offenseSummary = buildOffenseSummary(offense)
            val defenseBadge = buildDefenseBadge(defense)

            recommendations += SwitchRecommendation(
                slot = slot,
                speciesName = speciesName,
                displayName = displayName,
                bestMoveId = offense.moveId,
                bestMoveName = offense.moveName,
                bestMoveType = offense.moveType,
                offenseMinPercent = offense.minPercent,
                offenseMaxPercent = offense.maxPercent,
                offenseEffectiveness = offense.effectiveness,
                offenseTier = offense.tier,
                worstThreatMoveName = defense.worstThreatMoveName,
                threatMinPercent = defense.minPercent,
                threatMaxPercent = defense.maxPercent,
                worstDefensiveMultiplier = defense.worstMultiplier,
                defenseTier = defense.tier,
                defenseTone = defense.tone,
                source = source,
                confidenceTier = confidenceTier,
                reasonTag = reasonTag,
                offenseSummary = offenseSummary,
                defenseBadge = defenseBadge,
                score = compositeScore,
                hasUnmodelledEnemyThreats = defense.hasUnmodelledEnemyMoves
            )
        }

        return recommendations.sortedWith(
            compareByDescending<SwitchRecommendation> { it.score }.thenBy { it.slot }
        )
    }

    fun isEligibleBenchMember(slot: Int, activePlayerSlot: Int, mon: ParsedPokemon): Boolean =
        slot != activePlayerSlot &&
            !mon.isEmpty &&
            mon.isValid &&
            !mon.isEgg &&
            mon.currentHp > 0 &&
            mon.maxHp > 0

    private fun evaluateOffense(
        benchMon: ParsedPokemon,
        benchTypes: List<PokemonType>,
        opponent: ParsedPokemon,
        opponentTypes: List<PokemonType>,
        dataPack: GameDataPack,
        runtimeTrust: RuntimeRomTrust,
        damageEvaluator: SwitchDamageEvaluator
    ): OffenseEvaluation {
        var bestCalcMove: MoveInfo? = null
        var bestCalcEstimate: SwitchDamageEstimate? = null
        var bestCalcEff = 1.0
        var bestCalcTier = SwitchConfidenceTier.TYPE_FALLBACK

        var bestFallbackMove: MoveInfo? = null
        var bestFallbackEff = 0.0
        var bestFallbackStab = false
        var bestFallbackMetric = Double.NEGATIVE_INFINITY

        for (i in benchMon.moves.indices) {
            val moveId = benchMon.moves[i]
            if (moveId <= 0) continue
            val pp = benchMon.pp.getOrNull(i) ?: 0
            if (pp <= 0) continue

            val moveInfo = dataPack.getMove(moveId) ?: continue
            if (moveInfo.category == MoveCategory.STATUS) continue

            val eff = typeEffectiveness(moveInfo.type, opponentTypes, dataPack) ?: continue
            val isStab = moveInfo.type in benchTypes

            val estimate = damageEvaluator(benchMon, opponent, moveId)
                ?.takeIf { it.support != CalcSupport.UNSUPPORTED && it.defenderMaxHp > 0 && it.maxDamage >= 0 }

            if (estimate != null) {
                val tier = tierFromSupport(estimate.support, runtimeTrust)
                val prev = bestCalcEstimate
                val better = prev == null ||
                    estimate.maxPercent > prev.maxPercent ||
                    (estimate.maxPercent == prev.maxPercent && estimate.minPercent > prev.minPercent)
                if (better) {
                    bestCalcMove = moveInfo
                    bestCalcEstimate = estimate
                    bestCalcEff = eff
                    bestCalcTier = tier
                }
            }

            val effectivePower = if (moveInfo.power > 0) moveInfo.power.toDouble() else 50.0
            val fallbackMetric = eff * (if (isStab) 1.5 else 1.0) * effectivePower
            if (fallbackMetric > bestFallbackMetric) {
                bestFallbackMetric = fallbackMetric
                bestFallbackMove = moveInfo
                bestFallbackEff = eff
                bestFallbackStab = isStab
            }
        }

        if (bestCalcMove != null && bestCalcEstimate != null) {
            val oppCurHp = opponent.currentHp.coerceAtLeast(1)
            val curMinPct = (bestCalcEstimate.minDamage.coerceAtLeast(0) * 100.0) / oppCurHp
            val curMaxPct = (bestCalcEstimate.maxDamage.coerceAtLeast(0) * 100.0) / oppCurHp
            val avgPct = ((curMinPct + curMaxPct) / 2.0).coerceIn(0.0, 150.0)
            val koBonus = when {
                curMinPct >= 100.0 -> 35.0
                curMaxPct >= 100.0 -> 20.0
                curMinPct >= 50.0 -> 12.0
                curMaxPct >= 50.0 -> 6.0
                else -> 0.0
            }
            val score = avgPct * 0.60 + koBonus
            return OffenseEvaluation(
                moveId = bestCalcMove.id,
                moveName = bestCalcMove.name,
                moveType = bestCalcMove.type,
                minPercent = bestCalcEstimate.minPercent,
                maxPercent = bestCalcEstimate.maxPercent,
                currentHpMinPercent = curMinPct,
                currentHpMaxPercent = curMaxPct,
                effectiveness = bestCalcEff,
                isStab = bestCalcMove.type in benchTypes,
                power = bestCalcMove.power,
                tier = bestCalcTier,
                score = score
            )
        }

        if (bestFallbackMove != null) {
            val effScore = when {
                bestFallbackEff <= 0.0 -> -35.0
                bestFallbackEff < 0.5 -> -18.0
                bestFallbackEff < 1.0 -> -8.0
                bestFallbackEff < 2.0 -> 10.0
                bestFallbackEff < 4.0 -> 34.0
                else -> 52.0
            }
            val stabBonus = if (bestFallbackStab && bestFallbackEff > 0.0) 8.0 else 0.0
            val powerBonus = if (bestFallbackEff > 0.0) (bestFallbackMove.power.coerceIn(0, 120) / 15.0) else 0.0
            return OffenseEvaluation(
                moveId = bestFallbackMove.id,
                moveName = bestFallbackMove.name,
                moveType = bestFallbackMove.type,
                minPercent = null,
                maxPercent = null,
                currentHpMinPercent = null,
                currentHpMaxPercent = null,
                effectiveness = bestFallbackEff,
                isStab = bestFallbackStab,
                power = bestFallbackMove.power,
                tier = SwitchConfidenceTier.TYPE_FALLBACK,
                score = effScore + stabBonus + powerBonus
            )
        }

        return OffenseEvaluation(
            moveId = null,
            moveName = null,
            moveType = null,
            minPercent = null,
            maxPercent = null,
            currentHpMinPercent = null,
            currentHpMaxPercent = null,
            effectiveness = null,
            isStab = false,
            power = 0,
            tier = SwitchConfidenceTier.TYPE_FALLBACK,
            score = -20.0
        )
    }

    private fun evaluateDefense(
        benchMon: ParsedPokemon,
        benchTypes: List<PokemonType>,
        hpRatio: Double,
        opponent: ParsedPokemon,
        opponentTypes: List<PokemonType>,
        opponentMoveScan: OpponentMoveScan,
        dataPack: GameDataPack,
        runtimeTrust: RuntimeRomTrust,
        damageEvaluator: SwitchDamageEvaluator
    ): DefenseEvaluation {
        var evaluatedCount = 0
        var refusedCount = opponentMoveScan.unresolvedMoveCount
        var worstCalcMove: MoveInfo? = null
        var worstCalcEstimate: SwitchDamageEstimate? = null
        var worstCalcEff: Double? = null
        var allDefenseVerified = true

        for (moveInfo in opponentMoveScan.usableDamagingMoves) {
            val estimate = damageEvaluator(opponent, benchMon, moveInfo.id)
                ?.takeIf { it.support != CalcSupport.UNSUPPORTED && it.defenderMaxHp > 0 && it.maxDamage >= 0 }
            if (estimate == null) {
                refusedCount++
                continue
            }

            evaluatedCount++
            val moveTier = tierFromSupport(estimate.support, runtimeTrust)
            if (moveTier != SwitchConfidenceTier.VERIFIED) {
                allDefenseVerified = false
            }

            val prev = worstCalcEstimate
            val worse = prev == null ||
                estimate.maxPercent > prev.maxPercent ||
                (estimate.maxPercent == prev.maxPercent && estimate.minPercent > prev.minPercent)
            if (worse) {
                worstCalcMove = moveInfo
                worstCalcEstimate = estimate
                worstCalcEff = typeEffectiveness(moveInfo.type, benchTypes, dataPack)
            }
        }

        if (refusedCount > 0) {
            val benchCurHp = benchMon.currentHp.coerceAtLeast(1)
            val knownDangerCurMaxPct = worstCalcEstimate?.let {
                (it.maxDamage.coerceAtLeast(0) * 100.0) / benchCurHp
            }
            val tone = when {
                knownDangerCurMaxPct != null && knownDangerCurMaxPct >= 100.0 -> SwitchDefenseTone.DANGER
                knownDangerCurMaxPct != null && knownDangerCurMaxPct >= 50.0 -> SwitchDefenseTone.WEAK
                else -> SwitchDefenseTone.NEUTRAL
            }
            val score = when {
                knownDangerCurMaxPct != null && knownDangerCurMaxPct >= 100.0 -> -45.0
                knownDangerCurMaxPct != null && knownDangerCurMaxPct >= 50.0 -> -15.0
                else -> 0.0
            }
            return DefenseEvaluation(
                worstThreatMoveName = null,
                minPercent = null,
                maxPercent = null,
                currentHpMaxPercent = null,
                knownDangerCurMaxPercent = knownDangerCurMaxPct,
                worstMultiplier = null,
                tier = if (evaluatedCount > 0) SwitchConfidenceTier.ESTIMATED else SwitchConfidenceTier.TYPE_FALLBACK,
                tone = tone,
                score = score,
                hasAnyCalc = evaluatedCount > 0,
                hasCompleteCalc = false,
                hasUnmodelledEnemyMoves = true
            )
        }

        if (worstCalcMove != null && worstCalcEstimate != null) {
            val benchCurHp = benchMon.currentHp.coerceAtLeast(1)
            val incomingCurMaxPct = (worstCalcEstimate.maxDamage.coerceAtLeast(0) * 100.0) / benchCurHp
            val survivalBonus = when {
                incomingCurMaxPct <= 0.0 -> 30.0
                incomingCurMaxPct < 25.0 -> 18.0
                incomingCurMaxPct < 45.0 -> 8.0
                incomingCurMaxPct >= 100.0 -> -45.0
                incomingCurMaxPct >= 50.0 -> -15.0
                else -> 0.0
            }
            val score = (50.0 - incomingCurMaxPct.coerceIn(0.0, 150.0) * 0.70) + survivalBonus
            val tone = when {
                incomingCurMaxPct < 30.0 -> SwitchDefenseTone.STRONG
                incomingCurMaxPct < 50.0 -> SwitchDefenseTone.NEUTRAL
                incomingCurMaxPct < 100.0 -> SwitchDefenseTone.WEAK
                else -> SwitchDefenseTone.DANGER
            }
            val worstTier = if (allDefenseVerified) SwitchConfidenceTier.VERIFIED else SwitchConfidenceTier.ESTIMATED
            return DefenseEvaluation(
                worstThreatMoveName = worstCalcMove.name,
                minPercent = worstCalcEstimate.minPercent,
                maxPercent = worstCalcEstimate.maxPercent,
                currentHpMaxPercent = incomingCurMaxPct,
                knownDangerCurMaxPercent = incomingCurMaxPct,
                worstMultiplier = worstCalcEff,
                tier = worstTier,
                tone = tone,
                score = score,
                hasAnyCalc = true,
                hasCompleteCalc = true,
                hasUnmodelledEnemyMoves = false
            )
        }

        val worstStabMultiplier = opponentTypes
            .mapNotNull { stabType -> typeEffectiveness(stabType, benchTypes, dataPack) }
            .maxOrNull()
        if (worstStabMultiplier == null) {
            return DefenseEvaluation(
                worstThreatMoveName = null,
                minPercent = null,
                maxPercent = null,
                currentHpMaxPercent = null,
                knownDangerCurMaxPercent = null,
                worstMultiplier = null,
                tier = SwitchConfidenceTier.TYPE_FALLBACK,
                tone = SwitchDefenseTone.NEUTRAL,
                score = 0.0,
                hasAnyCalc = false,
                hasCompleteCalc = false,
                hasUnmodelledEnemyMoves = true
            )
        }

        val defEffScore = when {
            worstStabMultiplier <= 0.0 -> 55.0
            worstStabMultiplier <= 0.25 -> 40.0
            worstStabMultiplier <= 0.5 -> 26.0
            worstStabMultiplier <= 1.0 -> 0.0
            worstStabMultiplier < 4.0 -> -30.0
            else -> -55.0
        }
        val hpPenalty = (hpRatio - 1.0) * 20.0
        val tone = when {
            worstStabMultiplier <= 0.5 -> SwitchDefenseTone.STRONG
            worstStabMultiplier <= 1.0 -> SwitchDefenseTone.NEUTRAL
            worstStabMultiplier < 4.0 -> SwitchDefenseTone.WEAK
            else -> SwitchDefenseTone.DANGER
        }
        return DefenseEvaluation(
            worstThreatMoveName = null,
            minPercent = null,
            maxPercent = null,
            currentHpMaxPercent = null,
            knownDangerCurMaxPercent = null,
            worstMultiplier = worstStabMultiplier,
            tier = SwitchConfidenceTier.TYPE_FALLBACK,
            tone = tone,
            score = defEffScore + hpPenalty,
            hasAnyCalc = false,
            hasCompleteCalc = false,
            hasUnmodelledEnemyMoves = false
        )
    }

    private fun collectOpponentDamagingMoves(opponent: ParsedPokemon, dataPack: GameDataPack): OpponentMoveScan {
        val hasPopulatedPp = opponent.pp.any { it > 0 }
        val moves = ArrayList<MoveInfo>(opponent.moves.size)
        var unresolvedCount = 0
        for (i in opponent.moves.indices) {
            val moveId = opponent.moves[i]
            if (moveId <= 0) continue
            if (hasPopulatedPp && (opponent.pp.getOrNull(i) ?: 0) <= 0) continue
            val moveInfo = dataPack.getMove(moveId)
            if (moveInfo == null) {
                unresolvedCount++
                continue
            }
            if (moveInfo.category == MoveCategory.STATUS) continue
            moves += moveInfo
        }
        return OpponentMoveScan(usableDamagingMoves = moves, unresolvedMoveCount = unresolvedCount)
    }

    private fun tierFromSupport(support: CalcSupport, runtimeTrust: RuntimeRomTrust): SwitchConfidenceTier = when {
        support == CalcSupport.VERIFIED && runtimeTrust.exactRuntimeVerified -> SwitchConfidenceTier.VERIFIED
        support == CalcSupport.VERIFIED || support == CalcSupport.ESTIMATED -> SwitchConfidenceTier.ESTIMATED
        else -> SwitchConfidenceTier.TYPE_FALLBACK
    }

    private fun resolveTypes(
        mon: ParsedPokemon,
        profile: RomHackProfile,
        dataPack: GameDataPack
    ): List<PokemonType> {
        val custom = profile.customSpecies[mon.species]
        if (custom != null) {
            val t1 = PokemonType.fromString(custom.type1)
            val t2 = PokemonType.fromString(custom.type2)?.takeIf { it != t1 }
            return listOfNotNull(t1, t2)
        }
        val species = dataPack.getSpecies(mon.species) ?: return emptyList()
        if (species.baseHP <= 0 || species.name.trim().endsWith(UnboundDataPack.FORM_UNVERIFIED_SUFFIX, ignoreCase = true)) {
            return emptyList()
        }
        val t2 = species.type2?.takeIf { it != species.type1 }
        return listOfNotNull(species.type1, t2)
    }

    private fun resolveSpeciesName(
        mon: ParsedPokemon,
        profile: RomHackProfile,
        dataPack: GameDataPack
    ): String {
        profile.customSpecies[mon.species]?.name?.takeIf { it.isNotBlank() }?.let { return it }
        dataPack.getSpecies(mon.species)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        return "Pokemon #${mon.species}"
    }

    private fun typeEffectiveness(
        attackType: PokemonType,
        defenderTypes: List<PokemonType>,
        dataPack: GameDataPack
    ): Double? {
        if (defenderTypes.isEmpty()) return null
        var mult = 1.0
        for (defType in defenderTypes) {
            mult *= dataPack.getEffectiveness(attackType, defType)
        }
        return mult
    }

    private fun effectiveSpeed(mon: ParsedPokemon): Int {
        val base = mon.speed.coerceAtLeast(1)
        val paralyzed = (mon.statusCondition and PARALYSIS_MASK) != 0L
        return if (paralyzed) (base * 0.25).toInt().coerceAtLeast(1) else base
    }

    private fun buildReasonTag(offense: OffenseEvaluation, defense: DefenseEvaluation): String {
        val offenseTag = if (offense.tier != SwitchConfidenceTier.TYPE_FALLBACK &&
            offense.currentHpMinPercent != null && offense.currentHpMaxPercent != null
        ) {
            when {
                offense.currentHpMinPercent >= 100.0 -> "OHKO"
                offense.currentHpMaxPercent >= 100.0 -> "Poss. OHKO"
                offense.currentHpMinPercent >= 50.0 -> "2HKO"
                offense.currentHpMaxPercent >= 50.0 -> "Poss. 2HKO"
                else -> null
            }
        } else {
            val eff = offense.effectiveness ?: 0.0
            when {
                eff >= 4.0 -> "4× SE"
                eff >= 2.0 -> "2× SE"
                else -> null
            }
        }

        val defenseTag = when {
            defense.hasUnmodelledEnemyMoves -> {
                if ((defense.knownDangerCurMaxPercent ?: 0.0) >= 50.0) "Risky" else null
            }
            defense.hasCompleteCalc && defense.currentHpMaxPercent != null -> {
                when {
                    defense.currentHpMaxPercent <= 0.0 -> "Immune"
                    defense.currentHpMaxPercent >= 100.0 -> "Risky"
                    (defense.worstMultiplier ?: 1.0) <= 0.5 && defense.currentHpMaxPercent < 50.0 -> "Resists"
                    defense.currentHpMaxPercent < 35.0 -> "Safe Pivot"
                    defense.currentHpMaxPercent >= 50.0 -> "Risky"
                    else -> null
                }
            }
            else -> {
                val mult = defense.worstMultiplier
                when {
                    mult == null -> null
                    mult <= 0.0 -> "Immune"
                    mult <= 0.5 -> "Resists"
                    mult >= 2.0 -> "Risky"
                    else -> null
                }
            }
        }

        return when {
            offenseTag != null && defenseTag != null -> "$offenseTag · $defenseTag"
            offenseTag != null -> offenseTag
            defenseTag != null -> defenseTag
            defense.hasUnmodelledEnemyMoves -> "Partial"
            else -> "Neutral"
        }
    }

    private fun buildOffenseSummary(offense: OffenseEvaluation): String {
        val moveName = offense.moveName ?: return "No attack"
        if (offense.tier != SwitchConfidenceTier.TYPE_FALLBACK &&
            offense.minPercent != null && offense.maxPercent != null
        ) {
            val prefix = if (offense.tier == SwitchConfidenceTier.ESTIMATED) "~" else ""
            val minPct = offense.minPercent.roundToInt()
            val maxPct = offense.maxPercent.roundToInt()
            return "$moveName · $prefix$minPct–$maxPct%"
        }
        val effText = formatMultiplierShort(offense.effectiveness ?: 1.0)
        return "$moveName · $effText"
    }

    private fun buildDefenseBadge(defense: DefenseEvaluation): String {
        if (defense.hasUnmodelledEnemyMoves) {
            return "Threat unknown"
        }
        if (defense.hasCompleteCalc &&
            defense.minPercent != null && defense.maxPercent != null
        ) {
            val maxPct = defense.maxPercent.roundToInt()
            if (maxPct <= 0) return "Takes 0%"
            val prefix = if (defense.tier == SwitchConfidenceTier.ESTIMATED) "~" else ""
            val minPct = defense.minPercent.roundToInt()
            return "Takes $prefix$minPct–$maxPct%"
        }
        val m = defense.worstMultiplier ?: return "Threat unknown"
        return when {
            m <= 0.0 -> "Immune (0×)"
            m <= 0.25 -> "Resists (0.25×)"
            m <= 0.5 -> "Resists (0.5×)"
            m <= 1.0 -> "Neutral (1×)"
            m < 4.0 -> "Weak (2×)"
            else -> "Weak (4×)"
        }
    }

    private fun formatMultiplierShort(mult: Double): String = when {
        mult <= 0.0 -> "0×"
        mult <= 0.25 -> "0.25×"
        mult <= 0.5 -> "0.5×"
        mult < 2.0 -> "1×"
        mult < 4.0 -> "2× SE"
        else -> "4× SE"
    }
}
