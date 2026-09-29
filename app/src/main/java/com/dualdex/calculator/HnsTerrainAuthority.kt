package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsFieldStatusData
import com.dualdex.pokemon.hns.HnsItemRegistry
import com.dualdex.pokemon.hns.normalizeHnsBattlerTypes

/** The pinned per-battler result used by every ordinary-hit terrain decision. */
enum class HnsTerrainApplicability { AFFECTED, NOT_AFFECTED, UNKNOWN }

/**
 * Single source-backed authority for H&S `IsBattlerTerrainAffected` grounding.
 *
 * The live field word decides whether a terrain exists. Caller terrain strings and setter abilities
 * never enter this class. Positive grounding volatiles remain unknown because the existing
 * capability policy intentionally blocks them. Semi-invulnerability also fails closed: the
 * ordinary-move allow-list does not prove that the defending battler cannot be in a semi-invulnerable
 * state left by another turn.
 */
object HnsTerrainAuthority {
    fun resolve(
        fieldStatuses: Int?,
        battler: HnsBattlerRuntimeState?,
        opposingAbilityId: Int? = 0
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
        if (live.volatileSemiInvulnerable != 0) return HnsTerrainApplicability.UNKNOWN
        if (live.abilityId == HnsFieldStatusData.ABILITY_LEVITATE &&
            (opposingAbilityId == null || opposingAbilityId == HnsFieldStatusData.ABILITY_NEUTRALIZING_GAS)) {
            return HnsTerrainApplicability.UNKNOWN
        }

        val magicRoom = field and HnsFieldStatusData.STATUS_FIELD_MAGIC_ROOM != 0
        val ironBall = live.itemId == IRON_BALL_ID && !magicRoom
        val airBalloon = live.itemId == AIR_BALLOON_ID && !magicRoom
        // Pinned IsBattlerGrounded order: Iron Ball, Gravity, rooted, Smack Down, ungrounding
        // sources (Telekinesis, Magnet Rise, Air Balloon, Levitate), Flying, otherwise grounded.
        if (ironBall || field and HnsFieldStatusData.STATUS_FIELD_GRAVITY != 0) {
            return HnsTerrainApplicability.AFFECTED
        }
        if (magicRoom && live.itemId in setOf(IRON_BALL_ID, AIR_BALLOON_ID)) {
            return HnsTerrainApplicability.UNKNOWN
        }
        if (airBalloon || live.abilityId == HnsFieldStatusData.ABILITY_LEVITATE) {
            return HnsTerrainApplicability.NOT_AFFECTED
        }
        val types = normalizeHnsBattlerTypes(live.types) ?: return HnsTerrainApplicability.UNKNOWN
        if ("Flying" in types) return HnsTerrainApplicability.NOT_AFFECTED
        return HnsTerrainApplicability.AFFECTED
    }

    private val IRON_BALL_ID = HnsItemRegistry.resolveIdByName("Iron Ball")
    private val AIR_BALLOON_ID = HnsItemRegistry.resolveIdByName("Air Balloon")
}
