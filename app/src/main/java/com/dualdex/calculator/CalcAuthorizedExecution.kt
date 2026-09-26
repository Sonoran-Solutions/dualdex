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
}
