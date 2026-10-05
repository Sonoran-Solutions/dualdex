package com.dualdex.calculator

import com.dualdex.pokemon.hns.Hns205MoveEffects

/** Current selected hit only. Inactive stale lock storage is deliberately ignored. */
object HnsRolloutAuthority {
    data class Operands(val timer: Int, val defenseCurl: Boolean, val multipleTurns: Boolean, val lockedMove: Int, val rechargeTimer: Int)
    data class Result(val operands: Operands? = null, val limitation: CalcLimitation? = null) {
        val basePower: Int? get() = operands?.let { 30 * (1 shl it.timer) * if (it.defenseCurl) 2 else 1 }
    }
    fun forRequest(request: DamageCalculationRequest): Result = resolve(
        request.typeSystem,
        com.dualdex.pokemon.hns.HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id,
        request.field.gameType, request.hnsLiveBattleState)

    internal fun resolve(typeSystem: String?, moveId: Int?, format: String?, live: CalcHnsLiveBattleState?): Result {
        if (typeSystem != "hns_2_0_5" || moveId !in Hns205MoveEffects.fixedSingleHitRolloutMoveIds ||
            format != "Singles" || live?.observedBattlersCount != 2)
            return Result(limitation = CalcLimitation.HNS_ROLLOUT_STATE_UNKNOWN)
        if (live.switchInEventsSettled != true)
            return Result(limitation = CalcLimitation.HNS_ROLLOUT_PHASE_OR_LOCK_INCONSISTENT)
        val s = live.attackerRolloutState ?: return Result(limitation = CalcLimitation.HNS_ROLLOUT_STATE_UNKNOWN)
        if (s.rechargeTimer != 0 || s.timer !in 0..4 || s.lockedMove !in 0..65535 ||
            s.multipleTurns != (s.timer > 0) || s.multipleTurns && s.lockedMove != moveId)
            return Result(limitation = CalcLimitation.HNS_ROLLOUT_PHASE_OR_LOCK_INCONSISTENT)
        return Result(operands = s)
    }
}
