package com.dualdex.pokemon.hns

/**
 * How one H&S 2.0.5 move's damage semantics map onto the generation III (ADV) pipeline that
 * `@smogon/calc` 0.11.0 executes (issue #9, Gap C4a).
 *
 * The bridge forwards only `power` / `type` / `category`, which fully describes an ordinary
 * fixed-base-power attack and nothing else. H&S scales base power, reads battle state, hits
 * multiple times or deals fixed damage through a move's effect ID, so a request that clears the
 * effect gate is the only kind that may reach the engine.
 */
enum class HnsMoveMechanicsCategory {
    /**
     * `EFFECT_HIT` with no multi-hit, explosion, always-crit or state-dependent damage flag: the
     * pinned source's generic damage path, which the generation III pipeline reproduces.
     */
    ORDINARY_PROVEN_EQUIVALENT,

    /**
     * The move reads battle state the request cannot express (current HP, friendship, weight,
     * speed, consecutive use, airborne/underground target, chosen move). Its damage is not a
     * function of the request fields the calculator carries.
     */
    UNSUPPORTED_STATE_DEPENDENT,

    /**
     * The move computes damage through a different formula than the ordinary path (fixed damage,
     * OHKO, level/percent damage, defence selection, or a per-hit sequence).
     */
    UNSUPPORTED_FORMULA_DIFFERENT,

    /**
     * The move's damage reads held-item state and is refused by the dedicated C3 interaction
     * audit. This registry intentionally does not add a second, competing blocker for it.
     */
    ITEM_DEPENDENT_HANDLED_ELSEWHERE,

    /**
     * The move's effect could not be resolved from the pinned source, the move is absent from the
     * generated map, or the move is otherwise not audited. Fails closed.
     */
    UNCLASSIFIED;

    /** True when a request may pass the move-mechanics gate with no blocker from this registry. */
    val isSupportedForOrdinaryDamage: Boolean
        get() = this == ORDINARY_PROVEN_EQUIVALENT
}

/**
 * The audited mechanics decision for one exact H&S move.
 *
 * [moveId] is the build's own numeric `enum Move` value and is the only lookup key.
 * [effect] is the exact `enum BattleMoveEffects` symbol when the pinned source declares one
 * unambiguously; null means the effect was conditional/computed and must be treated as unknown.
 * [category] and [rationale] are for diagnostics and documentation.
 */
data class HnsMoveMechanicsEntry(
    val moveId: Int?,
    val effect: String?,
    val category: HnsMoveMechanicsCategory,
    val rationale: String
) {
    /**
     * True when this registry must add `HNS_MOVE_MECHANICS_NOT_MODELLED`. An item-dependent move
     * is excluded because the C3 item audit already refuses it.
     */
    val requiresBlock: Boolean
        get() = !category.isSupportedForOrdinaryDamage &&
            category != HnsMoveMechanicsCategory.ITEM_DEPENDENT_HANDLED_ELSEWHERE
}

/**
 * Capability audit of every H&S 2.0.5 move's damage semantics against the generation III pipeline.
 *
 * Source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`.
 *
 * The ordinary set is generated from the pinned source by
 * `tools/hns-move-mechanics/generate_hns_move_effects.py`; it is derived from the exact
 * `src/data/moves_info.h`, never from a hand-maintained list of move names. The registry is
 * deliberately a strict allow-list: any move not in [Hns205MoveEffects.ordinaryMoveIds] is
 * refused, including item-dependent moves already refused by the C3 audit.
 */
object HnsMoveMechanicsRegistry {

    /**
     * Effects whose damage depends on battle state the request shape cannot express. Used only to
     * give a precise rationale; the allow-list decision is [Hns205MoveEffects.ordinaryMoveIds].
     */
    private val stateDependentEffects: Set<String> = setOf(
        "EFFECT_FACADE",
        "EFFECT_BRINE",
        "EFFECT_RETURN",
        "EFFECT_FRUSTRATION",
        "EFFECT_FLAIL",
        "EFFECT_WEATHER_BALL",
        "EFFECT_SOLAR_BEAM",
        "EFFECT_PURSUIT",
        "EFFECT_ASSURANCE",
        "EFFECT_REVENGE",
        "EFFECT_PUNISHMENT",
        "EFFECT_POWER_BASED_ON_USER_HP",
        "EFFECT_MAGNITUDE",
        "EFFECT_PRESENT",
        "EFFECT_SPIT_UP",
        "EFFECT_TRUMP_CARD",
        "EFFECT_FURY_CUTTER",
        "EFFECT_ROLLOUT",
        "EFFECT_STORED_POWER",
        "EFFECT_ELECTRO_BALL",
        "EFFECT_GYRO_BALL",
        "EFFECT_LOW_KICK",
        "EFFECT_HEAT_CRASH",
        "EFFECT_NATURE_POWER",
        "EFFECT_BEAT_UP",
        "EFFECT_BIDE",
        "EFFECT_REFLECT_DAMAGE",
        "EFFECT_MULTI_HIT",
        "EFFECT_DOUBLE_HIT",
        "EFFECT_TRIPLE_KICK"
    )

    /** Effects whose damage uses a different formula than the ordinary path. */
    private val formulaDifferentEffects: Set<String> = setOf(
        "EFFECT_HIDDEN_POWER",
        "EFFECT_FOUL_PLAY",
        "EFFECT_BODY_PRESS",
        "EFFECT_PSYSHOCK",
        "EFFECT_FIXED_HP_DAMAGE",
        "EFFECT_LEVEL_DAMAGE",
        "EFFECT_FIXED_PERCENT_DAMAGE",
        "EFFECT_ENDEAVOR",
        "EFFECT_FINAL_GAMBIT",
        "EFFECT_OHKO"
    )

    /**
     * Classifies [moveId]'s damage mechanics. A null, absent or unresolved ID is
     * [HnsMoveMechanicsCategory.UNCLASSIFIED] and requires a blocker (fail closed).
     */
    fun classify(moveId: Int?): HnsMoveMechanicsEntry {
        if (moveId == null) {
            return HnsMoveMechanicsEntry(
                moveId = null,
                effect = null,
                category = HnsMoveMechanicsCategory.UNCLASSIFIED,
                rationale = "Move ID is absent; damage mechanics cannot be proven."
            )
        }

        if (moveId in HnsMoveItemInteractionRegistry.auditedMoveIds()) {
            return HnsMoveMechanicsEntry(
                moveId = moveId,
                effect = Hns205MoveEffects.effectById[moveId],
                category = HnsMoveMechanicsCategory.ITEM_DEPENDENT_HANDLED_ELSEWHERE,
                rationale = "Move reads held-item state; refused by the C3 item-interaction audit."
            )
        }

        if (moveId in Hns205MoveEffects.ordinaryMoveIds) {
            return HnsMoveMechanicsEntry(
                moveId = moveId,
                effect = "EFFECT_HIT",
                category = HnsMoveMechanicsCategory.ORDINARY_PROVEN_EQUIVALENT,
                rationale = "EFFECT_HIT with no multi-hit, explosion, always-crit or " +
                    "state-dependent damage flag in the pinned source."
            )
        }

        val effect = Hns205MoveEffects.effectById[moveId]
        if (effect == null) {
            return HnsMoveMechanicsEntry(
                moveId = moveId,
                effect = null,
                category = HnsMoveMechanicsCategory.UNCLASSIFIED,
                rationale = "Effect is absent, conditional or computed in the pinned source; " +
                    "the build's active branch is not resolved, so this move fails closed."
            )
        }
        val category = when (effect) {
            in stateDependentEffects -> HnsMoveMechanicsCategory.UNSUPPORTED_STATE_DEPENDENT
            in formulaDifferentEffects -> HnsMoveMechanicsCategory.UNSUPPORTED_FORMULA_DIFFERENT
            else -> HnsMoveMechanicsCategory.UNCLASSIFIED
        }
        return HnsMoveMechanicsEntry(
            moveId = moveId,
            effect = effect,
            category = category,
            rationale = "Pinned effect $effect is not the ordinary EFFECT_HIT damage path."
        )
    }
}
