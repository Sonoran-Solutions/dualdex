package com.dualdex.calculator

import com.dualdex.pokemon.hns.*

/** Exact ordinary opposing-hit subset, bound only from matching live packets. */
data class CalcHnsDoublesOperands(
    val helpingHand: Int,
    val attackerPartnerAbility: Int,
    val defenderPartnerAbility: Int,
    val attackerPartnerSpecies: Int,
    val defenderPartnerSpecies: Int,
    val fieldAbilities: Set<Int>,
    val ruinFlags: Int
)

internal object HnsDoublesAuthority {
    fun isExactPlusMinus(decision: HnsAbilityRequestDecision): Boolean =
        decision.abilityId in setOf(57,58) && decision.rule == "doubles_plus_minus_exact" &&
            decision.relevance == HnsAbilityRequestRelevance.RELEVANT

    fun partnerPriorityUnresolved(request: DamageCalculationRequest): Boolean {
        val dp = request.hnsLiveBattleState?.doubles?.defenderPartnerAbility ?: return false
        if (dp !in setOf(214,219,296)) return false
        val move = HeartAndSoul205DataPack.getMoveByName(request.move.name) ?: return true
        val priority = HnsGroupCPolicy.effectivePriority(request, move.id) ?: return true
        return priority > 0
    }

    fun bind(request: DamageCalculationRequest, a: HnsBattlerRuntimeState?,
             d: HnsBattlerRuntimeState?, exact: Boolean): Pair<CalcHnsDoublesOperands?, CalcLimitation?> {
        if (!exact || a?.battlersCount != 4 || d?.battlersCount != 4) return null to null
        val packet = a.doubles ?: return null to CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN
        if (d.doubles != packet || !packet.valid() || !a.absentFlagsReadable || !d.absentFlagsReadable ||
            a.absentBattlerFlags != packet.absentFlags || d.absentBattlerFlags != packet.absentFlags)
            return null to CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN
        fun matches(s: HnsBattlerRuntimeState, slot: Int?): Boolean {
            val i = s.battlerIndex ?: return false
            val b = packet.battlers.getOrNull(i) ?: return false
            return s.status == HnsBattlerRuntimeStatus.OBSERVED && packet.alive(i) &&
                slot != null && slot == s.partySlot && slot == b.partySlot && s.speciesId == b.species &&
                s.abilityId == b.ability && s.itemId == b.item && s.hpObserved && s.hp == b.hp &&
                s.persistentVolatilesObserved && s.volatileGastroAcid == b.gastroAcid &&
                s.groupDVolatilesObserved && s.volatileNeutralizingGas == b.neutralizingGas &&
                s.volatileTransformed == b.transformed && s.volatileSemiInvulnerable == b.semiInvulnerable &&
                (if (s.volatileVesselOfRuin) 1 else 0) + (if (s.volatileSwordOfRuin) 2 else 0) +
                (if (s.volatileTabletsOfRuin) 4 else 0) + (if (s.volatileBeadsOfRuin) 8 else 0) == b.ruinFlags
        }
        if (!matches(a, request.attacker.partySlot) || !matches(d, request.defender.partySlot))
            return null to CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN
        val ai = a.battlerIndex!!; val di = d.battlerIndex!!
        if (packet.battlers[ai].position and 1 == packet.battlers[di].position and 1)
            return null to CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED
        // All count slots matter to Gas/Ruin, including fainted holders. Mold Breaker state is
        // action-global, not implied by a caller's ability label. This subset refuses active Gas
        // and suppression actions rather than manufacturing effective neutral partner abilities.
        if (packet.moldBreakerActive || packet.battlers.any { it.neutralizingGas && !it.gastroAcid } ||
            packet.battlers[ai].effectiveAbility in HnsGroupCPolicy.moldBreakerAbilityIds)
            return null to CalcLimitation.HNS_DOUBLES_SUPPRESSION_UNRESOLVED
        if (packet.pledgeMove) return null to CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED
        val live = packet.battlers.filter { packet.alive(it.index) }
        if (live.any { it.effectiveAbility == 279 || it.semiInvulnerable == 6 })
            return null to CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED
        val move = HeartAndSoul205DataPack.getMoveByName(request.move.name)
        if (move?.id?.let { "ignoresTargetAbility" in Hns205MoveEffects.immunityFlagsById[it].orEmpty() } == true)
            return null to CalcLimitation.HNS_DOUBLES_SUPPRESSION_UNRESOLVED
        val target = move?.id?.let { Hns205MoveEffects.targetClassByMoveId[it] }
        // Menu selection is a hypothetical chosen opposing target. It is exact only when no
        // observed redirection can replace it. Spread targets are independently enumerated.
        val spread = target == Hns205MoveEffects.SpreadTargetClass.TARGET_BOTH ||
            target == Hns205MoveEffects.SpreadTargetClass.TARGET_FOES_AND_ALLY
        if (!spread && (packet.followMeTimers.any { it > 0 } || live.any {
                it.effectiveAbility == 31 || it.effectiveAbility == 114 }))
            return null to CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED
        // Random targeting cannot establish the caller's selected future defender.
        if (target == 5) return null to CalcLimitation.HNS_DOUBLES_SELECTED_TARGET_UNRESOLVED
        fun partner(i: Int) = packet.battlers[i xor 2].takeIf { packet.alive(i xor 2) }
        val ap = partner(ai); val dp = partner(di)
        if (listOfNotNull(ap, dp).any { it.effectiveAbility == 122 &&
                Hns205ItemCatalogue.get(it.item)?.holdEffect == "HOLD_EFFECT_UTILITY_UMBRELLA" })
            return null to CalcLimitation.HNS_DOUBLES_PARTNER_STATE_UNKNOWN
        return CalcHnsDoublesOperands(packet.battlers[ai].helpingHand,
            ap?.effectiveAbility ?: 0, dp?.effectiveAbility ?: 0,
            ap?.species ?: 0, dp?.species ?: 0, live.map { it.effectiveAbility }.toSet(),
            packet.battlers.filter { !it.gastroAcid }.fold(0) { mask, b -> mask or b.ruinFlags }) to null
    }
}
