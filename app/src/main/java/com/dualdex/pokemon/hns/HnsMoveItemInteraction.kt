package com.dualdex.pokemon.hns

/**
 * How one H&S 2.0.5 move's damage result depends on held-item state.
 *
 * This is deliberately a *move* property, not an item property: an item whose own
 * hold effect never touches the ordinary damage path can still change the result of
 * a move that reads item identity, presence or absence. The static
 * [HnsItemRegistry] audit and this interaction audit are combined into the final,
 * contextual item capability by [com.dualdex.calculator.CalcCapabilityPolicy].
 *
 * Source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`.
 */
enum class HnsItemInteractionKind {
    /** The move's damage does not read attacker or defender item state. */
    NONE,

    /**
     * The attacker's held-item IDENTITY selects the move's base power and/or type
     * (Fling, Natural Gift, Judgment, Techno Blast, Multi-Attack).
     */
    ATTACKER_ITEM_IDENTITY,

    /**
     * The attacker's item ABSENCE changes the move's base power (Acrobatics).
     */
    ATTACKER_ITEM_ABSENCE,

    /**
     * The defender HOLDING an item changes the move's base power (Knock Off) or is
     * required for the move to work at all (Poltergeist).
     */
    DEFENDER_ITEM_PRESENCE
}

/**
 * The audited interaction between one exact H&S move and held-item state.
 *
 * [moveId] is the build's own numeric `enum Move` value and is the only lookup key.
 * [moveName] is the canonical pinned display name and is used by tests to prove the
 * ID still belongs to the exact H&S pack; it never authorizes capability.
 *
 * [modelled] is false while the calculator does not explicitly reproduce the
 * interaction. No item-dependent interaction is modelled today, so every audited
 * move is blocked; the field exists so a future model can clear exactly one move
 * without weakening the rest.
 */
data class HnsMoveItemInteraction(
    val moveId: Int?,
    val moveName: String,
    val kind: HnsItemInteractionKind,
    val modelled: Boolean,
    val rationale: String
) {
    /** True when the move reads attacker/defender item identity, presence or absence. */
    val isItemDependent: Boolean
        get() = kind != HnsItemInteractionKind.NONE

    /** True when the interaction is item-dependent and not explicitly reproduced. */
    val requiresBlock: Boolean
        get() = isItemDependent && !modelled
}

/**
 * The exact H&S 2.0.5 moves whose damage calculation reads held-item state.
 *
 * The set is the complete result of auditing the pinned damage path, not just the
 * three examples in review R1:
 *
 * - `CalcMoveBasePower` (`src/battle_util.c`): `EFFECT_FLING` (`:6344`),
 *   `EFFECT_NATURAL_GIFT` (`:6395`), `EFFECT_ACROBATICS` (`:6421`);
 * - `CalcMoveBasePowerAfterModifiers` (`src/battle_util.c`): `EFFECT_KNOCK_OFF`
 *   (`:6619`);
 * - `GetDynamicMoveType` (`src/battle_main.c`): `EFFECT_CHANGE_TYPE_ON_ITEM`
 *   (`:6284`) and `EFFECT_NATURAL_GIFT` (`:6327`) — the move's own TYPE, hence
 *   type effectiveness and category, depends on the item;
 * - move resolution (`src/battle_move_resolution.c`): `EFFECT_POLTERGEIST`
 *   (`:1301`) fails outright when the defender holds no item.
 *
 * Deliberately NOT included, with reasons:
 * - `EFFECT_WEATHER_BALL`: its only item read is `holdEffect != HOLD_EFFECT_UTILITY_UMBRELLA`
 *   (`src/battle_main.c:6212-6240`). Utility Umbrella's hold effect is not the
 *   hold effect of any item in the supported subset, so the identity that is stripped
 *   before the engine can never change Weather Ball's type; a request holding Utility
 *   Umbrella is already refused by the static item gate.
 * - `EFFECT_LOW_KICK` / `EFFECT_HEAT_CRASH`: they call `GetBattlerWeight`
 *   (`src/battle_util.c:6053-6087`), whose only item read is `holdEffect == HOLD_EFFECT_FLOAT_STONE`.
 *   Float Stone is not in the supported subset and is refused statically, so no supported item can
 *   change a weight-based move's power through this path.
 * - `EFFECT_PLUCK` / `EFFECT_BUG_BITE` / `EFFECT_THIEF` / `EFFECT_COVET`: they move or
 *   consume a berry after the damage formula, but the formula itself does not read the
 *   item.
 * - `EFFECT_SUCKER_PUNCH`: it reads the defender's chosen MOVE, never an item.
 * - gem/type-plate/choice/pinch-berry hold effects: they multiply an ORDINARY move's
 *   damage and are already modelled by the static item audit (all unsupported), so they
 *   are not move-specific item interactions.
 */
object HnsMoveItemInteractionRegistry {

    private val audited = LinkedHashMap<Int, HnsMoveItemInteraction>()

    private val none = HnsMoveItemInteraction(
        moveId = null,
        moveName = "",
        kind = HnsItemInteractionKind.NONE,
        modelled = true,
        rationale = "Move does not read held-item identity, presence or absence in the pinned damage path."
    )

    private fun register(
        moveId: Int,
        moveName: String,
        kind: HnsItemInteractionKind,
        rationale: String
    ) {
        audited[moveId] = HnsMoveItemInteraction(
            moveId = moveId,
            moveName = moveName,
            kind = kind,
            modelled = false,
            rationale = rationale
        )
    }

    init {
        register(
            moveId = 374,
            moveName = "Fling",
            kind = HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY,
            rationale = "CalcMoveBasePower EFFECT_FLING: basePower = GetFlingPowerFromItemId(gBattleMons[battlerAtk].item) " +
                "[src/battle_util.c:6344-6346]."
        )
        register(
            moveId = 363,
            moveName = "Natural Gift",
            kind = HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY,
            rationale = "CalcMoveBasePower EFFECT_NATURAL_GIFT reads gNaturalGiftTable of the attacker's held berry " +
                "[src/battle_util.c:6395-6397]; GetDynamicMoveType derives the move TYPE from the same item " +
                "[src/battle_main.c:6327-6330]."
        )
        register(
            moveId = 512,
            moveName = "Acrobatics",
            kind = HnsItemInteractionKind.ATTACKER_ITEM_ABSENCE,
            rationale = "CalcMoveBasePower EFFECT_ACROBATICS doubles base power when gBattleMons[battlerAtk].item == ITEM_NONE " +
                "[src/battle_util.c:6421-6424]."
        )
        register(
            moveId = 282,
            moveName = "Knock Off",
            kind = HnsItemInteractionKind.DEFENDER_ITEM_PRESENCE,
            rationale = "CalcMoveBasePowerAfterModifiers EFFECT_KNOCK_OFF applies x1.5 when gBattleMons[battlerDef].item != ITEM_NONE " +
                "[src/battle_util.c:6619-6623] (B_KNOCK_OFF_DMG >= GEN_6)."
        )
        register(
            moveId = 737,
            moveName = "Poltergeist",
            kind = HnsItemInteractionKind.DEFENDER_ITEM_PRESENCE,
            rationale = "Move resolution EFFECT_POLTERGEIST fails when gBattleMons[battlerDef].item == ITEM_NONE " +
                "[src/battle_move_resolution.c:1301-1305]."
        )
        register(
            moveId = 449,
            moveName = "Judgment",
            kind = HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY,
            rationale = "GetDynamicMoveType EFFECT_CHANGE_TYPE_ON_ITEM returns GetItemSecondaryId(heldItem) when the plate's " +
                "hold effect matches [src/battle_main.c:6284-6286]."
        )
        register(
            moveId = 546,
            moveName = "Techno Blast",
            kind = HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY,
            rationale = "GetDynamicMoveType EFFECT_CHANGE_TYPE_ON_ITEM: the held Drive selects the move type " +
                "[src/battle_main.c:6284-6286]."
        )
        register(
            moveId = 672,
            moveName = "Multi-Attack",
            kind = HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY,
            rationale = "GetDynamicMoveType EFFECT_CHANGE_TYPE_ON_ITEM: the held Memory selects the move type " +
                "[src/battle_main.c:6284-6286]."
        )
    }

    /**
     * Classifies [moveId]'s item interaction. Unknown, null and out-of-domain IDs are
     * [HnsItemInteractionKind.NONE]: this registry is an audit of the item-dependent
     * moves in the exact pack, so a move outside it is not item-dependent. A move that
     * is missing from the pinned pack is refused by policy before this lookup matters.
     */
    fun classify(moveId: Int?): HnsMoveItemInteraction =
        if (moveId == null) none else audited[moveId] ?: none

    /** The exact numeric move IDs this audit covers. */
    fun auditedMoveIds(): Set<Int> = audited.keys.toSet()

    /** Every audited interaction, for docs/tests and diagnostics. */
    fun audited(): List<HnsMoveItemInteraction> = audited.values.toList()
}
