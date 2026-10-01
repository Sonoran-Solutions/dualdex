package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.hns.HnsFieldStatusData
import com.dualdex.pokemon.hns.HnsItemRegistry

/** Pinned battle_util.c:6874 IsRuinStatusActive and its four stat-stage callers. */
internal object HnsRuinAuthority {
    fun modifies(abilityId: Int, c: HnsAbilityContextPolicy.Context): Boolean? {
        val live = c.liveBattleState ?: return null
        if (c.observedBattlersCount != 2 || !c.attackerAbilityObserved || !c.defenderAbilityObserved ||
            c.attackerAbilityId == null || c.defenderAbilityId == null ||
            live.attackerHp == null || live.defenderHp == null ||
            live.attackerHp <= 0 || live.defenderHp <= 0) return null
        if (live.attackerPersistentVolatiles?.observed != true ||
            live.defenderPersistentVolatiles?.observed != true) return null
        val category = c.moveCategory ?: return null
        val field = live.fieldStatuses ?: return null
        val usesDef = (category == MoveCategory.PHYSICAL) xor
            (field and HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM != 0)
        val flags = when (abilityId) {
            284 -> live.attackerVesselOfRuin to live.defenderVesselOfRuin
            285 -> live.attackerSwordOfRuin to live.defenderSwordOfRuin
            286 -> live.attackerTabletsOfRuin to live.defenderTabletsOfRuin
            287 -> live.attackerBeadsOfRuin to live.defenderBeadsOfRuin
            else -> return null
        }
        val relevantStat = when (abilityId) {
            284 -> category == MoveCategory.SPECIAL
            286 -> category == MoveCategory.PHYSICAL
            285 -> usesDef
            else -> !usesDef
        }
        if (!relevantStat) return false
        val self = if (abilityId == 284 || abilityId == 286) flags.first else flags.second
        if (self == null) return null
        if (self) return false
        val gastro = listOf(live.attackerPersistentVolatiles?.gastroAcid,
            live.defenderPersistentVolatiles?.gastroAcid)
        val gases = listOf(live.attackerNeutralizingGas, live.defenderNeutralizingGas)
        if (gastro.any { it == null } || gases.any { it == null }) return null
        val gas = (0..1).any { gases[it] == true && gastro[it] == false }
        val ids = listOf(c.attackerAbilityId, c.defenderAbilityId)
        val items = listOf(c.attackerItemId, c.defenderItemId)
        var unknown = false
        for ((i, flag) in listOf(flags.first, flags.second).withIndex()) {
            if (gastro[i] == true || flag == false) continue
            if (gas && ids[i] != 256) {
                val itemId = items[i]
                if (itemId == null) { unknown = true; continue }
                val item = HnsItemRegistry.classify(itemId).data
                if (item == null) { unknown = true; continue }
                if (item.holdEffect != "HOLD_EFFECT_ABILITY_SHIELD") continue
            }
            if (flag == true) return true
            unknown = true
        }
        return if (unknown) null else false
    }
}
