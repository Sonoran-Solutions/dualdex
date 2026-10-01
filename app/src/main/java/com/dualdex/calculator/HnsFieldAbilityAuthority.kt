package com.dualdex.calculator

import com.dualdex.pokemon.hns.Hns205MoveEffects

/** Pinned battle_util.c:5044 IsAbilityOnField, restricted to two observed, living participants. */
internal class HnsFieldAbilityAuthority(private val c: HnsAbilityContextPolicy.Context) {
    fun present(abilityId: Int): Boolean? {
        val live = c.liveBattleState ?: return null
        if (c.observedBattlersCount != 2 || !c.attackerAbilityObserved || !c.defenderAbilityObserved ||
            c.attackerAbilityId == null || c.defenderAbilityId == null ||
            live.attackerHp == null || live.defenderHp == null ||
            live.attackerHp <= 0 || live.defenderHp <= 0) return null
        if (live.attackerPersistentVolatiles?.observed != true ||
            live.defenderPersistentVolatiles?.observed != true) return null
        val attackerGastro = live.attackerPersistentVolatiles.gastroAcid
        val defenderGastro = live.defenderPersistentVolatiles?.gastroAcid ?: return null
        val attackerGas = live.attackerNeutralizingGas ?: return null
        val defenderGas = live.defenderNeutralizingGas ?: return null
        // Effective identities under global Gas suppression remain outside the supported contract.
        if (attackerGas && !attackerGastro || defenderGas && !defenderGastro) return null
        if (c.attackerAbilityId == abilityId && !attackerGastro) return true
        if (c.defenderAbilityId != abilityId || defenderGastro) return false
        // Aura Break is breakable; Dark/Fairy Aura and the weather suppressors are not in
        // the pinned GEN_LATEST data. GetBattlerAbility never bypasses the attacker's own ability.
        if (abilityId == 188) {
            val itemId = c.defenderItemId ?: return null
            if (itemId == HnsGroupCPolicy.ABILITY_SHIELD_ITEM_ID) return true
            if (c.attackerAbilityId in HnsGroupCPolicy.moldBreakerAbilityIds) return false
            val moveId = c.moveId ?: return null
            val flags = Hns205MoveEffects.immunityFlagsById[moveId] ?: return null
            if ("ignoresTargetAbility" in Hns205MoveEffects.unknownImmunityFlagsById[moveId].orEmpty()) return null
            if ("ignoresTargetAbility" in flags) return false
        }
        return true
    }

    fun weatherEffective(): Boolean? {
        val cloudNine = present(13) ?: return null
        val airLock = present(76) ?: return null
        return !cloudNine && !airLock
    }
}
