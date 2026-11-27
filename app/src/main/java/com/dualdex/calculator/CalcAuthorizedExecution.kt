package com.dualdex.calculator

/** Executes an authorized request and fails closed if the engine contradicts a caveat. */
object CalcAuthorizedExecution {
    const val ECHO_FAILURE: String = "Calculator did not confirm ignored mechanics were neutralized"

    fun calculate(
        verdict: CalcCapabilityVerdict,
        calculate: (DamageCalculationRequest) -> DamageCalculationResponse
    ): DamageCalculationResponse {
        val request = verdict.request
        if (request == null || !verdict.mayRunEngine) {
            return DamageCalculationResponse(success = false, error = "Calculation refused by capability policy")
        }
        val isUnbound = verdict.ruleset == CalcRuleset.UNBOUND_CFRU
        val isCfru = isUnbound || verdict.ruleset == CalcRuleset.CFRU_TIER_B
        val isExpansionTierB = verdict.ruleset == CalcRuleset.EXPANSION_TIER_B
        val isTierB = verdict.ruleset == CalcRuleset.EXPANSION_TIER_B || verdict.ruleset == CalcRuleset.CFRU_TIER_B
        if (isCfru != (request.typeSystem == UnboundCalcInputs.TYPE_SYSTEM) ||
            (isCfru && (verdict.support != CalcSupport.ESTIMATED || request.unboundOperands == null))
        ) return DamageCalculationResponse(
            success = false,
            error = if (isUnbound) "Unbound request was not authorized" else "CFRU request was not authorized"
        )
        if (isExpansionTierB != (request.typeSystem == TierBCalcInputs.EXPANSION_TYPE_SYSTEM) ||
            (isExpansionTierB && (verdict.support != CalcSupport.ESTIMATED || request.expansionTierBOperands == null))
        ) return DamageCalculationResponse(success = false, error = "Expansion Tier-B request was not authorized")
        if (isTierB && (verdict.support != CalcSupport.ESTIMATED ||
                CalcLimitation.TIER_B_ESTIMATED_ENGINE !in verdict.limitations)
        ) return DamageCalculationResponse(success = false, error = "Tier-B request must remain explicitly estimated")

        val response = calculate(request)
        if (isUnbound && response.success &&
            !validUnboundRolls(response.range, response.minDamage, response.maxDamage)
        ) {
            return DamageCalculationResponse(success = false, error = "Invalid Unbound calculator result")
        }
        if (isTierB && response.success && (
            !validUnboundRolls(response.range, response.minDamage, response.maxDamage) ||
                !validTierBResponseMetadata(request, response)
            )
        ) {
            return DamageCalculationResponse(
                success = false,
                error = if (isCfru) "Invalid Unbound calculator result" else "Invalid addon calculator result"
            )
        }
        if (response.success && !validRepeatedStrikeResponse(request, response)) {
            return DamageCalculationResponse(success = false, error = "Calculator did not confirm repeated-strike scope")
        }
        if (!response.success || verdict.ignoredMechanics.isEmpty()) return response

        val echo = response.engineEcho
        if (echo == null || !echo.hasCompleteContract) {
            return DamageCalculationResponse(success = false, error = ECHO_FAILURE)
        }

        for (mechanic in verdict.ignoredMechanics) {
            val neutral = when (mechanic) {
                is IgnoredCalcMechanic.Ability -> when (mechanic.decision.side) {
                    HnsAbilitySide.ATTACKER -> echo.attackerAbility == "(other)"
                    HnsAbilitySide.DEFENDER -> echo.defenderAbility == "(other)"
                }
                is IgnoredCalcMechanic.Item -> when (mechanic.decision.side) {
                    HnsItemSide.ATTACKER -> echo.attackerItem == null
                    HnsItemSide.DEFENDER -> echo.defenderItem == null
                }
                is IgnoredCalcMechanic.Field -> request.hnsLiveBattleState?.fieldStatuses?.let {
                    (it and mechanic.decision.rawMask) == 0
                } == true
            }
            if (!neutral) return DamageCalculationResponse(success = false, error = ECHO_FAILURE)
        }
        return response
    }

    internal fun validRepeatedStrikeResponse(request: DamageCalculationRequest, response: DamageCalculationResponse): Boolean {
        if (!HnsRepeatedStrikeAuthority.isFamily(request)) return response.repeatedStrike == null
        if (HnsRepeatedStrikeAuthority.forRequest(request).stability != HnsRepeatedStrikeAuthority.Stability.STABLE) return false
        val countAuthority = HnsRepeatedStrikeCountAuthority.forRequest(request)
        val expectedCounts = countAuthority.nominalCounts ?: return false
        val sequence = response.repeatedStrike ?: return false
        val hp = request.hnsLiveBattleState?.defenderHp ?: return false
        val expectedAssumptions = listOf(
            if (request.move.isCrit) "All executed strikes critical" else "All executed strikes noncritical",
            "Conditional on successful connection from unchanged observed operands, including chosen move/protection; no intervening action before the selected move",
            "Independent damage rolls; interval endpoints do not imply every interior value is reachable"
        )
        val rolls = sequence.firstStrikeRolls
        if (sequence.nominalCounts != expectedCounts || rolls.size != 16 || rolls.any { it < 0 } ||
            rolls != rolls.sorted() || sequence.totals.map { it.nominalCount } != expectedCounts ||
            sequence.assumptions != expectedAssumptions ||
            sequence.totalUnavailableReasons.isNotEmpty() || response.range.isNotEmpty() || response.koChanceText.isNotEmpty()) return false
        val lo = rolls.first()
        val hi = rolls.last()
        val totals = expectedCounts.map { count ->
            val n = count.toLong()
            val minHits = if (hi == 0) 0 else minOf(n, ceilDiv(hp.toLong(), hi.toLong())).toInt()
            val maxHits = when {
                hi == 0 -> 0
                lo == 0 -> count
                else -> minOf(n, ceilDiv(hp.toLong(), lo.toLong())).toInt()
            }
            RepeatedStrikeTotal(count, minOf(hp.toLong(), n * lo).toInt(),
                minOf(hp.toLong(), n * hi).toInt(), minHits, maxHits)
        }
        return sequence.totals == totals && response.minDamage == totals.minOf { it.minHpLoss } &&
            response.maxDamage == totals.maxOf { it.maxHpLoss }
    }

    private fun ceilDiv(numerator: Long, denominator: Long): Long =
        if (denominator == 0L) 0L else 1L + (numerator - 1L) / denominator
}

internal fun validUnboundRolls(rolls: List<Int>, min: Int, max: Int): Boolean =
    rolls.size == 16 && rolls.first() == min && rolls.last() == max && rolls.first() >= 0 &&
        (rolls.first() > 0 || rolls.last() == 0) && rolls.zipWithNext().all { (low, high) -> low <= high }

internal fun validTierBResponseMetadata(
    request: DamageCalculationRequest,
    response: DamageCalculationResponse
): Boolean {
    val expected = expectedAddonDisplayMetadata(request) ?: return false
    if (response.attackerName.isBlank() || response.defenderName.isBlank() || response.moveName.isBlank()) {
        return false
    }
    if (response.attackerName != expected.attackerName ||
        response.defenderName != expected.defenderName ||
        response.moveName != expected.moveName ||
        response.defenderMaxHP != expected.defenderMaxHp
    ) return false
    if (request.typeSystem == TierBCalcInputs.EXPANSION_TYPE_SYSTEM) {
        val eff = response.effectiveness ?: return false
        if (!eff.isFinite()) return false
        val consistent = (eff == 0.0 && response.range.all { it == 0 }) ||
            (eff > 0.0 && response.range.all { it >= 1 })
        if (!consistent) return false
    }
    return true
}
