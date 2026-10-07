package com.dualdex.calculator

import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.pokemon.hns.Hns205MoveEffects
import com.dualdex.pokemon.hns.HnsAbilityRegistry
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds

/** Count selection is resolved separately from selected-strike arithmetic and sequence stability. */
object HnsRepeatedStrikeCountAuthority {
    enum class Mode { FIXED_TWO, ORDINARY_RANDOM, SKILL_LINK, LOADED_DICE }
    data class Result(
        val nominalCounts: List<Int>?,
        val mode: Mode?,
        val limitation: CalcLimitation? = null
    )

    fun isVariableCountMove(moveId: Int): Boolean =
        moveId in Hns205MoveEffects.variableMultiHitPlainMoveIds || moveId in Hns205MoveEffects.variableMultiHitScaleShotMoveIds

    fun forRequest(request: DamageCalculationRequest): Result {
        if (request.typeSystem != "hns_2_0_5") return unknown()
        val moveId = HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return unknown()
        if (moveId in Hns205MoveEffects.fixedTwoHitPlainMoveIds)
            return Result(listOf(2), Mode.FIXED_TWO)
        if (!isVariableCountMove(moveId)) return unknown()

        val live = request.hnsLiveBattleState ?: return unknown()
        val attackerVolatiles = live.attackerPersistentVolatiles ?: return unknown()
        val defenderVolatiles = live.defenderPersistentVolatiles ?: return unknown()
        if (!attackerVolatiles.observed || !defenderVolatiles.observed ||
            live.attackerNeutralizingGas == null || live.defenderNeutralizingGas == null) return unknown()
        val abilityId = request.attacker.abilityId ?: return unknown()
        if (HnsAbilityRegistry.classify(abilityId).abilityId != abilityId) return unknown()
        // Skill Link is checked before the hold-effect branch in CancelerMultihitMoves.
        if (abilityId == SKILL_LINK_ABILITY_ID) {
            // Effective ability IDs already fold Gastro Acid to ABILITY_NONE. A still-effective
            // Skill Link paired with Gastro Acid is contradictory, so fail closed.
            if (attackerVolatiles.gastroAcid) return unknown()
            val gasActive = live.attackerNeutralizingGas && !attackerVolatiles.gastroAcid ||
                live.defenderNeutralizingGas && !defenderVolatiles.gastroAcid
            var skillLinkActive = true
            if (gasActive) {
                val abilityShield = HnsHoldEffectAuthority.abilityShieldActiveIgnoringAbilityForRequest(request, HnsItemSide.ATTACKER)
                    ?: return unknown()
                skillLinkActive = abilityShield
            }
            if (skillLinkActive) return Result(Hns205MoveEffects.variableMultiHitSkillLinkNominalCounts, Mode.SKILL_LINK)
        }

        val hold = HnsHoldEffectAuthority.forRequest(request, HnsItemSide.ATTACKER)
        if (hold.state == HnsHoldEffectState.UNKNOWN || hold.effectiveHoldEffect == null) return unknown()
        // A count override is read from GetBattlerHoldEffect; the raw item label is not authority.
        return if (hold.effectiveHoldEffect == LOADED_DICE_HOLD_EFFECT) {
            Result(Hns205MoveEffects.variableMultiHitLoadedDiceNominalCounts, Mode.LOADED_DICE)
        } else {
            ordinary()
        }
    }

    /**
     * The generic live suppression gate is cleared only when suppression changes this move's
     * count selection or resolves Gastro Acid to the boundary's effective ABILITY_NONE. Gas is
     * not a blanket waiver for other damage abilities on either battler.
     */
    fun suppressionStateExactForVariableCount(request: DamageCalculationRequest): Boolean {
        val moveId = HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return false
        if (!isVariableCountMove(moveId) || forRequest(request).nominalCounts == null) return false
        val live = request.hnsLiveBattleState ?: return false
        val attackerVolatiles = live.attackerPersistentVolatiles ?: return false
        val defenderVolatiles = live.defenderPersistentVolatiles ?: return false
        if (!attackerVolatiles.observed || !defenderVolatiles.observed ||
            live.attackerNeutralizingGas == null || live.defenderNeutralizingGas == null) return false
        val attackerId = request.attacker.abilityId ?: return false
        val defenderId = request.defender.abilityId ?: return false
        if (HnsAbilityRegistry.classify(attackerId).abilityId != attackerId ||
            HnsAbilityRegistry.classify(defenderId).abilityId != defenderId) return false
        if (attackerVolatiles.gastroAcid && attackerId != HnsBattlerRuntimeStateIds.ABILITY_NONE ||
            defenderVolatiles.gastroAcid && defenderId != HnsBattlerRuntimeStateIds.ABILITY_NONE) return false
        val gasActive = live.attackerNeutralizingGas && !attackerVolatiles.gastroAcid ||
            live.defenderNeutralizingGas && !defenderVolatiles.gastroAcid
        if (!gasActive) return true
        fun safeUnderGas(side: HnsItemSide, abilityId: Int, gastroAcid: Boolean): Boolean {
            if (gastroAcid || abilityId == HnsBattlerRuntimeStateIds.ABILITY_NONE ||
                abilityId == com.dualdex.pokemon.hns.HnsFieldStatusData.ABILITY_NEUTRALIZING_GAS ||
                side == HnsItemSide.ATTACKER && abilityId == SKILL_LINK_ABILITY_ID) return true
            return HnsHoldEffectAuthority.abilityShieldActiveIgnoringAbilityForRequest(request, side) == true
        }
        return safeUnderGas(HnsItemSide.ATTACKER, attackerId, attackerVolatiles.gastroAcid) &&
            safeUnderGas(HnsItemSide.DEFENDER, defenderId, defenderVolatiles.gastroAcid)
    }

    private fun ordinary() = Result(Hns205MoveEffects.variableMultiHitOrdinaryNominalCounts, Mode.ORDINARY_RANDOM)
    private fun unknown() = Result(null, null, CalcLimitation.HNS_REPEATED_STRIKE_STATE_UNKNOWN)

    private const val SKILL_LINK_ABILITY_ID = 92
    private const val LOADED_DICE_HOLD_EFFECT = "HOLD_EFFECT_LOADED_DICE"
}
