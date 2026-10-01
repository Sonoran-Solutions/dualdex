package com.dualdex.calculator

import com.dualdex.pokemon.hns.Hns205MoveEffects
import com.dualdex.pokemon.hns.HnsItemRegistry

/** Pinned IsMoveMakingContact result for the selected ordinary hit. */
enum class HnsContactAuthority { CONTACT, NON_CONTACT, UNKNOWN }

/** Shared request-local contract used by policy and the calculator serialization boundary. */
object HnsContactRules {
    fun assess(
        moveId: Int?, ordinaryMove: Boolean?, attackerAbilityId: Int?, attackerAbilityObserved: Boolean,
        attackerItemId: Int?, attackerHoldEffectResolution: HnsHoldEffectResolution? = null
    ): HnsContactAuthority {
        if (ordinaryMove != true || moveId == null) return HnsContactAuthority.UNKNOWN
        val data = Hns205MoveEffects
        if (moveId in data.unknownContactMoveIds) return HnsContactAuthority.UNKNOWN
        val makesContact = data.makesContactById[moveId] ?: return HnsContactAuthority.UNKNOWN
        if (!makesContact) return HnsContactAuthority.NON_CONTACT
        val unknownPunch = data.unknownAbilityMoveFlagsById[moveId]?.contains("punchingMove") == true
        val punch = data.abilityMoveFlagsById[moveId]?.contains("punchingMove") == true
        if (unknownPunch) return HnsContactAuthority.UNKNOWN
        if (!attackerAbilityObserved || attackerAbilityId == null) return HnsContactAuthority.UNKNOWN
        if (punch && attackerItemId == null) return HnsContactAuthority.UNKNOWN
        if (attackerItemId == HnsItemRegistry.resolveIdByName("Punching Glove") && punch) {
            when (attackerHoldEffectResolution?.state) {
                HnsHoldEffectState.ACTIVE_EXACT -> {
                    if (attackerHoldEffectResolution.effectiveHoldEffect != "HOLD_EFFECT_PUNCHING_GLOVE")
                        return HnsContactAuthority.UNKNOWN
                    return HnsContactAuthority.NON_CONTACT
                }
                HnsHoldEffectState.SUPPRESSED_NONE -> Unit
                HnsHoldEffectState.UNKNOWN, null -> return HnsContactAuthority.UNKNOWN
            }
        }
        if (attackerAbilityId == 203) return HnsContactAuthority.NON_CONTACT // Long Reach
        return HnsContactAuthority.CONTACT
    }
}
