package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsFieldStatusData
import com.dualdex.pokemon.hns.normalizeHnsBattlerTypes

/** The pinned per-battler result used by every ordinary-hit terrain decision. */
enum class HnsTerrainApplicability { AFFECTED, NOT_AFFECTED, UNKNOWN }

/**
 * Single source-backed authority for H&S `IsBattlerTerrainAffected` grounding.
 *
 * The live field word decides whether a terrain exists. Caller terrain strings and setter abilities
 * never enter this class. Positive grounding volatiles remain unknown because the existing
 * capability policy intentionally blocks them. Observed underwater state proves terrain
 * inapplicability independently of grounding; other positive semi-states retain the existing
 * conservative result. Move execution authority is a separate policy.
 */
object HnsTerrainAuthority {
    fun resolve(
        fieldStatuses: Int?,
        battler: HnsBattlerRuntimeState?,
        opposingAbilityId: Int? = 0,
        battlerIsDefender: Boolean = false,
        holdEffectResolution: HnsHoldEffectResolution? = null,
        abilityShieldActiveIgnoringAbility: Boolean? = null
    ): HnsTerrainApplicability {
        val field = fieldStatuses ?: return HnsTerrainApplicability.UNKNOWN
        if (field and HnsFieldStatusData.STATUS_FIELD_TERRAIN_ANY == 0) {
            return HnsTerrainApplicability.NOT_AFFECTED
        }
        val live = battler ?: return HnsTerrainApplicability.UNKNOWN
        if (live.status != HnsBattlerRuntimeStatus.OBSERVED || live.abilityId == null ||
            live.abilityOutOfDomain || live.itemId == null || live.itemOutOfDomain ||
            !live.persistentVolatilesObserved || !live.volatilesObserved ||
            !live.types.all { it.observed && !it.outOfDomain }) {
            return HnsTerrainApplicability.UNKNOWN
        }
        // Keep the established positive-volatile hard gate; these states are not inferred away.
        if (live.volatileRoot || live.volatileSmackDown || live.volatileTelekinesis ||
            live.volatileMagnetRise || live.volatileGastroAcid || live.volatileRoostActive) {
            return HnsTerrainApplicability.UNKNOWN
        }
        // IsBattlerTerrainAffected checks semi-state before grounding. This is not execution authority.
        if (live.volatileSemiInvulnerable == com.dualdex.pokemon.hns.HnsGroupDLayout.STATE_UNDERWATER)
            return HnsTerrainApplicability.NOT_AFFECTED
        if (live.volatileSemiInvulnerable != 0) return HnsTerrainApplicability.UNKNOWN
        if (live.abilityId == HnsFieldStatusData.ABILITY_LEVITATE &&
            (opposingAbilityId == null || opposingAbilityId == HnsFieldStatusData.ABILITY_NEUTRALIZING_GAS)) {
            return HnsTerrainApplicability.UNKNOWN
        }

        val holdEffect = holdEffectResolution?.takeIf { it.itemId == live.itemId }
            ?: return HnsTerrainApplicability.UNKNOWN
        val ironBall = when {
            holdEffect.state == HnsHoldEffectState.UNKNOWN -> return HnsTerrainApplicability.UNKNOWN
            holdEffect.state == HnsHoldEffectState.ACTIVE_EXACT ->
                holdEffect.effectiveHoldEffect == "HOLD_EFFECT_IRON_BALL"
            else -> false
        }
        val airBalloon = when {
            holdEffect.state == HnsHoldEffectState.UNKNOWN -> return HnsTerrainApplicability.UNKNOWN
            holdEffect.state == HnsHoldEffectState.ACTIVE_EXACT ->
                holdEffect.effectiveHoldEffect == "HOLD_EFFECT_AIR_BALLOON"
            else -> false
        }
        // Pinned IsBattlerGrounded order: Iron Ball, Gravity, rooted, Smack Down, ungrounding
        // sources (Telekinesis, Magnet Rise, Air Balloon, Levitate), Flying, otherwise grounded.
        if (ironBall || field and HnsFieldStatusData.STATUS_FIELD_GRAVITY != 0) {
            return HnsTerrainApplicability.AFFECTED
        }
        if (battlerIsDefender && live.abilityId == HnsFieldStatusData.ABILITY_LEVITATE &&
            opposingAbilityId in MOLD_BREAKER_FAMILY_IDS
        ) {
            // CalcDamage's ctx->abilityDef uses GetBattlerAbility(defender), which returns NONE
            // when Mold Breaker suppresses breakable Levitate. Ability Shield preserves Levitate
            // only when its separate GetBattlerHoldEffectIgnoreAbility lookup remains active.
            when (abilityShieldActiveIgnoringAbility) {
                true -> return HnsTerrainApplicability.NOT_AFFECTED
                false -> return HnsTerrainApplicability.AFFECTED
                null -> return HnsTerrainApplicability.UNKNOWN
            }
        }
        if (airBalloon || live.abilityId == HnsFieldStatusData.ABILITY_LEVITATE) {
            return HnsTerrainApplicability.NOT_AFFECTED
        }
        val types = normalizeHnsBattlerTypes(live.types) ?: return HnsTerrainApplicability.UNKNOWN
        if ("Flying" in types) return HnsTerrainApplicability.NOT_AFFECTED
        return HnsTerrainApplicability.AFFECTED
    }

    private val MOLD_BREAKER_FAMILY_IDS = setOf(104, 163, 164)
}
