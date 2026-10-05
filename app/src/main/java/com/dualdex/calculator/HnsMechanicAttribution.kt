package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsAbilityCategory
import com.dualdex.pokemon.hns.HnsItemCategory

/** Shared production causal attribution. RELEVANT means applicable, not necessarily unmodelled. */
object HnsMechanicAttribution {
    fun isModelledAbility(decision: HnsAbilityRequestDecision): Boolean =
        decision.relevance == HnsAbilityRequestRelevance.RELEVANT &&
            (decision.globalCategory.isSupportedForDamage || HnsDoublesAuthority.isExactPlusMinus(decision) ||
                HnsAbilityContextPolicy.isExactStatusDoubleComatose(decision))

    fun isModelledItem(decision: HnsItemRequestDecision): Boolean =
        decision.relevance == HnsItemRequestRelevance.MODELLED

    /** Causes of the production soft ability limitation, before ignored-mechanic attribution. */
    fun isAbilityEffectCause(decision: HnsAbilityRequestDecision): Boolean =
        decision.globalCategory == HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT &&
            decision.relevance != HnsAbilityRequestRelevance.PROVEN_IRRELEVANT &&
            !isModelledAbility(decision)

    /** Causes of the production soft item limitation, before ignored-mechanic attribution. */
    fun isItemEffectCause(decision: HnsItemRequestDecision): Boolean =
        decision.globalCategory == HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT &&
            decision.relevance != HnsItemRequestRelevance.PROVEN_IRRELEVANT &&
            !isModelledItem(decision)
}
