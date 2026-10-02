package com.dualdex.calculator

import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsItemRegistry
import com.dualdex.pokemon.hns.HnsMoveItemInteractionRegistry
import com.dualdex.pokemon.hns.HnsMoveMechanicsCategory
import com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry

/** Exact request-local result for the defender's current resist-berry damage branch. */
enum class HnsResistBerryState { NOT_APPLICABLE, APPLIES, BLOCKED_BY_UNNERVE, UNKNOWN }

data class HnsResistBerryDecision(
    val state: HnsResistBerryState,
    val itemId: Int?,
    /** Q4.12 multiplier consumed by GetDefenderItemsModifier; null means the authority is unknown. */
    val modifierQ12: Int?,
    val rule: String,
    val rationale: String
)

/**
 * One live-state authority for resist-berry activation, Ripen and Unnerve/As One suppression.
 * It follows the exact current defender item, source move type/effectiveness, Singles topology,
 * opposing-battler liveness and effective ability IDs. Unknown operands never become neutral.
 */
object HnsResistBerryAuthority {
    private const val RIPEN_ABILITY_ID = 247
    private val UNNERVE_ABILITY_IDS = setOf(127, 266, 267)
    private const val Q12_ONE = 4096

    fun forRequest(request: DamageCalculationRequest): HnsResistBerryDecision {
        val defender = request.defender
        val itemId = defender.itemId
        if (itemId == 0 || (itemId == null && defender.origin == CalcInputOrigin.MANUAL &&
                defender.item.isNullOrBlank())) {
            return decision(
                HnsResistBerryState.NOT_APPLICABLE, itemId, Q12_ONE, "resist_berry_no_current_item",
                "The authoritative request contains no current defender item."
            )
        }
        if (itemId == null || defender.itemOutOfDomain || !HnsItemRegistry.isInDomain(itemId)) {
            return unknown(itemId, "The exact live defender item identity is unavailable or outside the pinned domain.")
        }
        val item = HnsItemRegistry.classify(itemId)
        val data = item.data ?: return unknown(itemId, "The current item has no pinned generated catalogue record.")
        if (data.holdEffect != "HOLD_EFFECT_RESIST_BERRY") {
            return decision(
                HnsResistBerryState.NOT_APPLICABLE, itemId, Q12_ONE, "resist_berry_other_item",
                "The exact current defender item is not a resist berry."
            )
        }

        val hold = HnsHoldEffectAuthority.forRequest(request, HnsItemSide.DEFENDER)
        when (hold.state) {
            HnsHoldEffectState.SUPPRESSED_NONE -> return decision(
                HnsResistBerryState.NOT_APPLICABLE, itemId, Q12_ONE, "resist_berry_hold_effect_suppressed",
                "Magic Room, Embargo or unsuppressed Klutz resolves the current resist-berry hold effect to NONE."
            )
            HnsHoldEffectState.UNKNOWN -> return unknown(itemId, "The current resist berry is not resolved against live suppression state.")
            HnsHoldEffectState.ACTIVE_EXACT -> if (hold.effectiveHoldEffect != "HOLD_EFFECT_RESIST_BERRY") {
                return unknown(itemId, "The current item's effective hold effect does not match its pinned resist-berry record.")
            }
        }

        val moveId = com.dualdex.pokemon.hns.HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id
            ?: return unknown(itemId, "The selected move has no pinned identity.")
        if (HnsMoveMechanicsRegistry.classify(moveId).category != HnsMoveMechanicsCategory.ORDINARY_PROVEN_EQUIVALENT ||
            HnsMoveItemInteractionRegistry.classify(moveId).isItemDependent
        ) return unknown(itemId, "The selected move is outside the ordinary, item-independent resist-berry contract.")

        val moveType = HnsMoveAuthority.forRequest(request, true).effectiveType
            ?: return unknown(itemId, "The selected move's effective type is not authoritative.")
        val berryTypeName = HnsItemRegistry.itemTypeName(itemId)
            ?: return unknown(itemId, "The current berry has no generated type operand.")
        val berryType = PokemonType.fromString(berryTypeName)
            ?: return unknown(itemId, "The generated resist-berry type is outside the pinned type domain.")
        if (berryType != moveType) return decision(
            HnsResistBerryState.NOT_APPLICABLE, itemId, Q12_ONE, "resist_berry_other_type",
            "The current resist berry's generated type does not match the effective move type."
        )

        val typeEffectiveness = HnsGroupCPolicy.typeEffectiveness(request, moveType)
            ?: return unknown(itemId, "The pinned type-effectiveness result needed by the berry threshold is unavailable.")
        if (moveType != PokemonType.NORMAL && typeEffectiveness < 2.0) return decision(
            HnsResistBerryState.NOT_APPLICABLE, itemId, Q12_ONE, "resist_berry_condition_not_met",
            "A non-Normal resist berry only activates at source type effectiveness of at least 2.0."
        )

        val live = request.hnsLiveBattleState ?: return unknown(itemId, "The live Singles and ability operands are unavailable.")
        if (live.observedBattlersCount != 2) return unknown(itemId, "Unnerve suppression requires authoritative two-battler Singles topology.")
        val attackerHp = live.attackerHp ?: return unknown(itemId, "The opposing battler's live HP is unavailable for the source alive check.")
        val attackerAbility = request.attacker.abilityId
            ?: return unknown(itemId, "The opposing battler's effective ability ID is unavailable.")
        val attackerPersistent = live.attackerPersistentVolatiles
        val defenderPersistent = live.defenderPersistentVolatiles
        if (attackerPersistent?.observed != true || defenderPersistent?.observed != true ||
            live.attackerNeutralizingGas == null || live.defenderNeutralizingGas == null
        ) return unknown(itemId, "Effective ability suppression operands are not fully observed.")
        if (live.attackerNeutralizingGas || live.defenderNeutralizingGas) return unknown(
            itemId, "Neutralizing Gas can change the effective opposing berry-blocking ability."
        )

        if (attackerHp > 0 && attackerAbility in UNNERVE_ABILITY_IDS) return decision(
            HnsResistBerryState.BLOCKED_BY_UNNERVE, itemId, Q12_ONE, "resist_berry_unnerve_blocked",
            "A living opposing effective Unnerve or As One ability blocks this berry exactly as IsUnnerveBlocked does."
        )
        val defenderAbility = request.defender.abilityId
            ?: return unknown(itemId, "The defender's effective ability ID is unavailable for Ripen.")
        val modifier = if (defenderAbility == RIPEN_ABILITY_ID) 1024 else 2048
        return decision(
            HnsResistBerryState.APPLIES, itemId, modifier,
            if (defenderAbility == RIPEN_ABILITY_ID) "resist_berry_ripen_quarter" else "resist_berry_half",
            if (defenderAbility == RIPEN_ABILITY_ID) {
                "Ripen changes the matching current resist berry from the source half modifier to the exact quarter modifier."
            } else {
                "The matching current resist berry applies the source half modifier."
            }
        )
    }

    private fun decision(
        state: HnsResistBerryState,
        itemId: Int?,
        modifierQ12: Int?,
        rule: String,
        rationale: String
    ) = HnsResistBerryDecision(state, itemId, modifierQ12, rule, rationale)

    private fun unknown(itemId: Int?, rationale: String) = decision(
        HnsResistBerryState.UNKNOWN, itemId, null, "resist_berry_authority_unobserved", rationale
    )
}
