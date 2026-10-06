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

        val response = calculate(request)
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
        val sequence = response.repeatedStrike ?: return false
        val hp = request.hnsLiveBattleState?.defenderHp ?: return false
        val expectedAssumptions = listOf(
            if (request.move.isCrit) "All executed strikes critical" else "All executed strikes noncritical",
            "Conditional on successful connection from unchanged observed operands, including chosen move/protection; no intervening action before the selected move",
            "Independent damage rolls; interval endpoints do not imply every interior value is reachable"
        )
        val rolls = sequence.firstStrikeRolls
        if (sequence.nominalCounts != listOf(2) || rolls.size != 16 || rolls.any { it < 0 } ||
            rolls != rolls.sorted() || sequence.totals.size != 1 || sequence.assumptions != expectedAssumptions ||
            sequence.totalUnavailableReasons.isNotEmpty() || response.range.isNotEmpty() || response.koChanceText.isNotEmpty()) return false
        val lo = rolls.first()
        val hi = rolls.last()
        val total = sequence.totals.single()
        return total == RepeatedStrikeTotal(2, minOf(hp.toLong(), 2L * lo).toInt(), minOf(hp.toLong(), 2L * hi).toInt(),
            if (hi == 0) 0 else if (hp <= hi) 1 else 2,
            if (hi == 0) 0 else if (hp <= lo) 1 else 2) &&
            response.minDamage == total.minHpLoss && response.maxDamage == total.maxHpLoss
    }
}
