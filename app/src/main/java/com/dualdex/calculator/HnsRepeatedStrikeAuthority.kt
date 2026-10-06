package com.dualdex.calculator

import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.pokemon.hns.Hns205MoveEffects
import com.dualdex.pokemon.hns.HnsAbilityRegistry

/** Sequence authorization is independent of the selected-strike arithmetic policies. */
object HnsRepeatedStrikeAuthority {
    enum class Stability { STABLE, UNSUPPORTED_TRANSITION, UNKNOWN_TRANSITION }
    data class Result(val stability: Stability, val limitation: CalcLimitation? = null)

    // Pinned battle_util.c move-end/form/status dispatch has no between-hit writer for these
    // identities. HP-sensitive attacker modifiers are safe only because no admitted reaction
    // changes attacker HP. Existing arithmetic/suppression gates still apply independently.
    fun isFamily(request: DamageCalculationRequest): Boolean {
        val moveId = HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return false
        return request.typeSystem == "hns_2_0_5" &&
            (moveId in Hns205MoveEffects.fixedTwoHitPlainMoveIds || moveId in Hns205MoveEffects.variableMultiHitPlainMoveIds)
    }

    fun forRequest(request: DamageCalculationRequest): Result {
        fun unknown() = Result(Stability.UNKNOWN_TRANSITION, CalcLimitation.HNS_REPEATED_STRIKE_STATE_UNKNOWN)
        fun unsupported() = Result(Stability.UNSUPPORTED_TRANSITION, CalcLimitation.HNS_REPEATED_STRIKE_TRANSITION_NOT_MODELLED)
        if (!isFamily(request)) return unknown()
        val moveId = HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return unknown()
        val variable = moveId in Hns205MoveEffects.variableMultiHitPlainMoveIds
        val live = request.hnsLiveBattleState ?: return unknown()
        val hp = live.defenderHp
        val maxHp = live.defenderMaxHp
        if (hp == null || maxHp == null || maxHp !in 1..65535 || hp !in 1..maxHp)
            return Result(Stability.UNKNOWN_TRANSITION, CalcLimitation.HNS_DEFENDER_HP_UNKNOWN)
        if (live.switchInEventsSettled != true || live.observedBattlersCount != 2 ||
            live.attackerHp == null || live.attackerMaxHp == null || live.attackerMaxHp !in 1..65535 || live.attackerHp !in 1..live.attackerMaxHp ||
            live.attackerPersistentVolatiles?.observed != true || live.defenderPersistentVolatiles?.observed != true ||
            live.defenderSemiInvulnerableState == null || !HnsDefenderStatus.isValid(live.defenderStatus1) || !HnsDefenderStatus.isValid(live.attackerStatus1) ||
            live.attackerNeutralizingGas == null || live.defenderNeutralizingGas == null) return unknown()
        if (live.attackerStatus1!! and 0x27 != 0 ||
            !variable && (live.attackerPersistentVolatiles.gastroAcid || live.defenderPersistentVolatiles.gastroAcid ||
                live.attackerNeutralizingGas == true || live.defenderNeutralizingGas == true) ||
            request.field.gameType != "Singles" || live.defenderSemiInvulnerableState != 0 ||
            live.attackerPersistentVolatiles.endured || live.defenderPersistentVolatiles.endured ||
            live.attackerPersistentVolatiles.substitute || live.defenderPersistentVolatiles.substitute) return unsupported()
        val contact = HnsContactRules.assess(moveId, true, request.attacker.abilityId,
            request.attacker.abilityId != null, request.attacker.itemId,
            HnsHoldEffectAuthority.forRequest(request, HnsItemSide.ATTACKER))
        if (contact == HnsContactAuthority.UNKNOWN) return unknown()
        if (contact == HnsContactAuthority.CONTACT && HnsHoldEffectAuthority.forRequest(request, HnsItemSide.ATTACKER).effectiveHoldEffect != "HOLD_EFFECT_PROTECTIVE_PADS") {
            // MOVE_NONE is observed before current-turn selection commits, not a harmless move.
            if (live.defenderChosenMove == null || live.defenderChosenMove !in 1 until Hns205MoveEffects.selectedMoveCount ||
                live.defenderProtectedMethod == null || live.defenderProtectedMethod !in 0..127) return unknown()
            if (live.defenderProtectedMethod != 0 ||
                Hns205MoveEffects.effectById[live.defenderChosenMove] == "EFFECT_BEAK_BLAST") return unsupported()
        }
        for (participant in listOf(request.attacker, request.defender)) {
            val id = participant.abilityId ?: return unknown()
            if (HnsAbilityRegistry.classify(id).abilityId != id) return unknown()
            if (id !in Hns205MoveEffects.repeatedStrikeStableAbilityIds && !(variable && id in VARIABLE_MULTI_HIT_STABLE_SUPPRESSION_ABILITY_IDS))
                return unsupported()
        }
        for (side in HnsItemSide.entries) {
            val effect = HnsHoldEffectAuthority.forRequest(request, side).effectiveHoldEffect ?: return unknown()
            if (effect !in Hns205MoveEffects.repeatedStrikeStableHoldEffects) return unsupported()
        }
        return Result(Stability.STABLE)
    }

    private val VARIABLE_MULTI_HIT_STABLE_SUPPRESSION_ABILITY_IDS = setOf(103, 256) // Klutz and Neutralizing Gas are frozen by live effective ability/item authority.
}
