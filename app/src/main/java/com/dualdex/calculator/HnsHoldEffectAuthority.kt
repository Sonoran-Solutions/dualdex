package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsItemData
import com.dualdex.pokemon.hns.HnsItemRegistry

/** The pinned engine's effective hold-effect result, kept distinct from raw item identity. */
enum class HnsHoldEffectState { ACTIVE_EXACT, SUPPRESSED_NONE, UNKNOWN }

data class HnsHoldEffectResolution(
    val itemId: Int?,
    val item: HnsItemData?,
    val state: HnsHoldEffectState,
    val effectiveHoldEffect: String?,
    val reason: String
)

/**
 * One authority for `GetBattlerHoldEffectInternal` suppression.
 *
 * Raw `itemId` remains available for identity-sensitive move/form behavior. The damage pipeline
 * consumes [effectiveHoldEffect], which is known only when the pinned item record and all three
 * suppression operands (Magic Room, Embargo, effective Klutz/Gastro Acid state) are authoritative.
 */
object HnsHoldEffectAuthority {
    private const val KLUTZ_ABILITY_ID = 103
    private val ABILITY_SHIELD_ITEM_ID = HnsItemRegistry.resolveIdByName("Ability Shield")

    fun resolve(
        itemId: Int?,
        itemProvenance: CalcItemProvenance,
        abilityId: Int?,
        fieldStatuses: Int?,
        embargo: Boolean?,
        gastroAcid: Boolean?,
        neutralizingGasOnField: Boolean? = false
    ): HnsHoldEffectResolution {
        if (itemId == 0) return HnsHoldEffectResolution(
            itemId, HnsItemRegistry.classify(0).data, HnsHoldEffectState.ACTIVE_EXACT,
            "HOLD_EFFECT_NONE", "ITEM_NONE is the authoritative current item."
        )
        val entry = itemId?.let(HnsItemRegistry::classify)
        val data = entry?.data
        if (itemId == null || itemProvenance != CalcItemProvenance.BATTLE_EFFECTIVE || data == null ||
            entry.category == com.dualdex.pokemon.hns.HnsItemCategory.UNCLASSIFIED) {
            return HnsHoldEffectResolution(
                itemId, data, HnsHoldEffectState.UNKNOWN, null,
                "Current numeric item identity or its pinned hold-effect family is unresolved."
            )
        }
        // Any one observed active suppressor is sufficient to resolve the effective hold effect
        // to NONE. To prove an effect active, all three suppression sources must be observed.
        if (fieldStatuses != null && (fieldStatuses and HnsFieldStatus.MAGIC_ROOM.mask) != 0) {
            return suppressed(itemId, data, "Magic Room")
        }
        if (embargo == true) return suppressed(itemId, data, "Embargo")
        if (fieldStatuses == null || embargo == null || abilityId == null ||
            (abilityId == KLUTZ_ABILITY_ID && gastroAcid == null)) {
            return HnsHoldEffectResolution(
                itemId, data, HnsHoldEffectState.UNKNOWN, null,
                "Magic Room, Embargo, or effective Klutz state is unobserved."
            )
        }
        if (abilityId == KLUTZ_ABILITY_ID && gastroAcid == false) {
            val abilityShield = abilityShieldActiveIgnoringAbility(itemId, fieldStatuses, embargo)
            when {
                abilityShield == true -> return suppressed(itemId, data, "Klutz")
                neutralizingGasOnField == false -> return suppressed(itemId, data, "Klutz")
                neutralizingGasOnField == true && abilityShield == false -> Unit // Gas suppresses Klutz.
                else -> return HnsHoldEffectResolution(
                    itemId, data, HnsHoldEffectState.UNKNOWN, null,
                    "Neutralizing Gas or the Ability Shield hold effect could change whether Klutz is effective."
                )
            }
        }
        return HnsHoldEffectResolution(
            itemId, data, HnsHoldEffectState.ACTIVE_EXACT, data.holdEffect,
            "Pinned hold effect is active; current numeric item identity is preserved separately."
        )
    }

    private fun suppressed(itemId: Int, data: HnsItemData, suppressor: String) = HnsHoldEffectResolution(
        itemId, data, HnsHoldEffectState.SUPPRESSED_NONE, "HOLD_EFFECT_NONE",
        "Pinned GetBattlerHoldEffectInternal suppresses the hold effect under $suppressor."
    )

    /** GetBattlerAbilityInternal checks Ability Shield through GetBattlerHoldEffectIgnoreAbility. */
    private fun abilityShieldActiveIgnoringAbility(itemId: Int, fieldStatuses: Int?, embargo: Boolean?): Boolean? {
        if (itemId != HnsItemRegistry.resolveIdByName("Ability Shield")) return false
        if (fieldStatuses == null || embargo == null) return null
        if (fieldStatuses and HnsFieldStatus.MAGIC_ROOM.mask != 0 || embargo == true) return false
        return true
    }

    fun forRequest(request: DamageCalculationRequest, side: HnsItemSide): HnsHoldEffectResolution {
        val live = request.hnsLiveBattleState
        val attacker = side == HnsItemSide.ATTACKER
        val participant = if (attacker) request.attacker else request.defender
        val attackerGas = live?.attackerNeutralizingGas
        val defenderGas = live?.defenderNeutralizingGas
        val neutralizingGasOnField = when {
            attackerGas == true || defenderGas == true -> true
            attackerGas == false && defenderGas == false -> false
            else -> null
        }
        return resolve(
            itemId = participant.itemId,
            itemProvenance = participant.itemProvenance,
            abilityId = participant.abilityId,
            fieldStatuses = live?.fieldStatuses,
            embargo = if (attacker) live?.attackerEmbargo else live?.defenderEmbargo,
            gastroAcid = (if (attacker) live?.attackerPersistentVolatiles else live?.defenderPersistentVolatiles)
                ?.takeIf { it.observed }?.gastroAcid,
            neutralizingGasOnField = neutralizingGasOnField
        )
    }

    /** Resolves the hold effect for a slot-matched exact runtime observation at the boundary. */
    fun forObservedRuntime(
        battler: com.dualdex.pokemon.hns.HnsBattlerRuntimeState,
        fieldStatuses: Int?,
        neutralizingGasOnField: Boolean?
    ): HnsHoldEffectResolution = resolve(
        itemId = battler.itemId.takeUnless { battler.itemOutOfDomain },
        itemProvenance = if (battler.status == com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED &&
            battler.itemId != null && !battler.itemOutOfDomain
        ) CalcItemProvenance.BATTLE_EFFECTIVE else CalcItemProvenance.UNKNOWN,
        abilityId = battler.effectiveAbilityId.takeUnless { battler.abilityOutOfDomain },
        fieldStatuses = fieldStatuses,
        embargo = battler.volatileEmbargo.takeIf { battler.itemVolatilesObserved },
        gastroAcid = battler.volatileGastroAcid.takeIf { battler.persistentVolatilesObserved },
        neutralizingGasOnField = neutralizingGasOnField
    )

    /**
     * Ability Shield is queried through GetBattlerHoldEffectIgnoreAbility, so Klutz cannot
     * suppress this interaction. Magic Room and Embargo still suppress it. Null means the
     * current item or a suppression operand is not authoritative.
     */
    fun abilityShieldActiveIgnoringAbility(
        itemId: Int?,
        itemProvenance: CalcItemProvenance,
        fieldStatuses: Int?,
        embargo: Boolean?
    ): Boolean? {
        if (itemId == null || itemProvenance != CalcItemProvenance.BATTLE_EFFECTIVE) return null
        if (itemId != ABILITY_SHIELD_ITEM_ID) return false
        if (fieldStatuses == null || embargo == null) return null
        return fieldStatuses and HnsFieldStatus.MAGIC_ROOM.mask == 0 && !embargo
    }

    fun abilityShieldActiveIgnoringAbilityForRequest(
        request: DamageCalculationRequest,
        side: HnsItemSide
    ): Boolean? {
        val participant = if (side == HnsItemSide.ATTACKER) request.attacker else request.defender
        val live = request.hnsLiveBattleState ?: return null
        val embargo = if (side == HnsItemSide.ATTACKER) live.attackerEmbargo else live.defenderEmbargo
        return abilityShieldActiveIgnoringAbility(
            participant.itemId,
            participant.itemProvenance,
            live.fieldStatuses,
            embargo
        )
    }
}
