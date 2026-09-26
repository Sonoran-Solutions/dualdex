package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsItemCategory
import com.dualdex.pokemon.hns.HnsItemRegistry

/** Which request participant holds an effective live item. */
enum class HnsItemSide { ATTACKER, DEFENDER }

/** Three-valued request-local item result (plus MODELLED for engine-supported items). Unknown never clears a blocker. */
enum class HnsItemRequestRelevance { PROVEN_IRRELEVANT, RELEVANT, UNKNOWN, MODELLED }

/**
 * Auditable outcome for one globally unsupported or unresolved held item.
 *
 * [itemName] is the pinned display name of the exact numeric [itemId] (never a caller string or a
 * generic item table). A PROVEN_IRRELEVANT result removes only this item's
 * [CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED] contribution; every other limitation, including
 * [CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED], is collected independently.
 */
data class HnsItemRequestDecision(
    val itemId: Int?,
    val itemName: String,
    val side: HnsItemSide,
    val globalCategory: HnsItemCategory,
    val relevance: HnsItemRequestRelevance,
    val rule: String? = null,
    val source: String? = null,
    val rationale: String
)

/**
 * Source-backed request-local relevance rules for globally unsupported H&S held items.
 *
 * [HnsItemRegistry] stays the global capability authority ("what can this item's own hold effect
 * change?"). This policy only answers whether that effect can change THIS request's single-hit
 * damage range. It proves irrelevance only when every operand of a reviewed predicate is
 * authoritative; a complete relevant decision can be named as a caveat by the outer capability
 * policy, while unknown relevance remains a hard refusal. The reviewed rules, their predicates and their pinned evidence live in
 * `tools/hns-items/context_rules.json`; `generate_hns_item_audit.py --check` fails when a rule named
 * here is not reviewed there or its evidence lines changed.
 *
 * Suppression (Klutz, Embargo, Magic Room) only removes a hold effect, so an irrelevance proof made
 * for the item's real hold effect also holds when it is suppressed.
 */
object HnsItemContextPolicy {

    /** ABILITY_ANALYTIC: the only ordinary-damage read of turn order (`src/battle_util.c:6690`). */
    private const val ANALYTIC_ABILITY_ID = 148

    data class Context(
        val side: HnsItemSide,
        /** Single-hit ordinary move with no move/item interaction; null when the move is unknown. */
        val ordinaryMove: Boolean?,
        /** Authoritative effective move type ([HnsMoveAuthority.effectiveType]), or null. */
        val moveType: PokemonType?,
        /** Authoritative effective category ([HnsMoveAuthority.category]), or null. */
        val moveCategory: MoveCategory?,
        /** Decoded live gFieldStatuses word, or null when unread. */
        val fieldState: HnsFieldState?,
        /** Live gBattleWeather word, or null when unread. */
        val weatherWord: Int?,
        /** Effective attacker ability ID, or null when unknown. */
        val attackerAbilityId: Int?,
        val defenderHp: Int?,
        val defenderMaxHp: Int?
    )

    fun assess(itemId: Int, context: Context?): HnsItemRequestDecision {
        val entry = HnsItemRegistry.classify(itemId)
        val name = HnsItemRegistry.displayName(itemId) ?: "Item #$itemId"
        val side = context?.side ?: HnsItemSide.ATTACKER
        if (entry.category != HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT &&
            entry.category != HnsItemCategory.MODELLED_HNS_SPECIFIC) {
            return HnsItemRequestDecision(
                itemId, name, side, entry.category, HnsItemRequestRelevance.UNKNOWN,
                rationale = if (entry.category == HnsItemCategory.UNCLASSIFIED) {
                    "Unresolved global item classification always fails closed."
                } else {
                    "Context rules are only evaluated for globally unsupported or modelled items."
                }
            )
        }
        val c = context ?: return unknown(itemId, name, side)
        val holdEffect = entry.data?.holdEffect ?: return unknown(itemId, name, c.side)

        val proof: Proof? = when (entry.familyGroup) {
            "attacker_offense" -> attackerOffense(holdEffect, itemId, c)
            "defender_defense" -> defenderDefense(holdEffect, itemId, c)
            "post_hit_or_residual" -> if (c.ordinaryMove == true) proof(
                rule = "single_hit_item_activation_outside_damage",
                source = "src/battle_move_resolution.c:2429",
                rationale = "Every activation of this hold effect runs after damage (MoveEnd), at end of turn, " +
                    "at switch-in or from an event script; it is never read by a single hit's damage."
            ) else null
            "turn_order" -> turnOrder(c)
            "weight_only" -> if (c.ordinaryMove == true) proof(
                rule = "weight_item_ordinary_move",
                source = "src/battle_util.c:6079",
                rationale = "Float Stone is read only by GetBattlerWeight, which no ordinary move uses."
            ) else null
            "grounding" -> grounding(holdEffect, c)
            "weather_shield" -> when {
                c.ordinaryMove != true || c.weatherWord == null -> null
                c.weatherWord == 0 -> proof(
                    rule = "umbrella_clear_weather",
                    source = "src/battle_util.c:7436",
                    rationale = "Every Utility Umbrella read requires sun or rain; live weather is observed clear."
                )
                else -> relevant(
                    rule = "umbrella_sun_or_rain",
                    source = "src/battle_util.c:7440",
                    rationale = "The holder ignores the live weather modifier."
                )
            }
            else -> null
        }

        return when (proof) {
            null -> unknown(itemId, name, c.side)
            else -> HnsItemRequestDecision(
                itemId, name, c.side, entry.category, proof.relevance, proof.rule, proof.source, proof.rationale
            )
        }
    }

    /**
     * Builds the item context from a request whose live state was rebound by CalcRequestBoundary.
     *
     * The effective move type and category come from [HnsMoveAuthority], which requires only the
     * evidence each operand needs: an unrelated live field bit (a terrain, Trick Room, Wonder Room,
     * Gravity, ...) does not make a category or type unknown. Item rules that do depend on a field
     * bit (Wonder Room for the defensive-stat items, terrain for the grounding items) read the
     * decoded [Context.fieldState] themselves.
     */
    fun contextForRequest(
        request: DamageCalculationRequest,
        side: HnsItemSide,
        ordinaryMove: Boolean?
    ): Context {
        val live = request.hnsLiveBattleState
        val authority = HnsMoveAuthority.forRequest(request, ordinaryMove)
        return Context(
            side = side,
            ordinaryMove = ordinaryMove,
            moveType = authority.effectiveType,
            moveCategory = authority.category,
            fieldState = live?.fieldStatuses?.let(HnsFieldState::decode),
            weatherWord = if (live?.weatherObserved == true) live.weatherWord else null,
            attackerAbilityId = request.attacker.abilityId,
            defenderHp = live?.defenderHp,
            defenderMaxHp = live?.defenderMaxHp
        )
    }

    private fun attackerOffense(holdEffect: String, itemId: Int, c: Context): Proof? {
        if (c.side == HnsItemSide.DEFENDER) {
            return proof(
                rule = "defender_holds_attacker_only_item",
                source = "src/battle_util.c:6811",
                rationale = "This hold effect is read only as the attacker's (holdEffectAtk); the defender's copy " +
                    "cannot change incoming damage."
            )
        }
        val category = c.moveCategory
        return when (holdEffect) {
            "HOLD_EFFECT_CHOICE_BAND", "HOLD_EFFECT_MUSCLE_BAND" -> when (category) {
                MoveCategory.SPECIAL -> physicalOnlySpecialMove()
                MoveCategory.PHYSICAL -> relevant(
                    rule = "physical_only_item_physical_move",
                    source = "src/battle_util.c:7178",
                    rationale = "The physical modifier applies to this physical move and is not modelled."
                )
                else -> null
            }
            "HOLD_EFFECT_THICK_CLUB" -> when (category) {
                MoveCategory.SPECIAL -> physicalOnlySpecialMove()
                MoveCategory.PHYSICAL -> speciesGated()
                else -> null
            }
            "HOLD_EFFECT_CHOICE_SPECS" -> when (category) {
                MoveCategory.PHYSICAL -> specialOnlyPhysicalMove()
                MoveCategory.SPECIAL -> relevant(
                    rule = "special_only_item_special_move",
                    source = "src/battle_util.c:7182",
                    rationale = "The special modifier applies to this special move and is not modelled."
                )
                else -> null
            }
            "HOLD_EFFECT_WISE_GLASSES" -> when (category) {
                MoveCategory.PHYSICAL -> specialOnlyPhysicalMove()
                MoveCategory.SPECIAL -> if (HnsItemRegistry.isSupportedForDamage(itemId)) {
                    modelled(
                        rule = "wise_glasses_special_move",
                        source = "src/battle_util.c:6818",
                        rationale = "Wise Glasses multiplies base power by UQ4.12 4505 (1.0 + 10% floored) per pinned H&S src/battle_util.c:6818 and is modelled in the QuickJS calculator."
                    )
                } else {
                    relevant(
                        rule = "special_only_item_special_move",
                        source = "src/battle_util.c:7182",
                        rationale = "The special modifier applies to this special move and is not modelled."
                    )
                }
                else -> null
            }
            "HOLD_EFFECT_DEEP_SEA_TOOTH" -> when (category) {
                MoveCategory.PHYSICAL -> specialOnlyPhysicalMove()
                MoveCategory.SPECIAL -> speciesGated()
                else -> null
            }
            "HOLD_EFFECT_TYPE_POWER", "HOLD_EFFECT_PLATE", "HOLD_EFFECT_GEMS" -> {
                val itemType = HnsItemRegistry.itemTypeName(itemId)?.let(PokemonType::fromString)
                val moveType = c.moveType
                when {
                    itemType == null || moveType == null -> null
                    itemType != moveType -> proof(
                        rule = "type_item_move_type_mismatch",
                        source = "src/battle_util.c:6841",
                        rationale = "The item boosts only ${itemType.displayName} moves; the effective move type is " +
                            "${moveType.displayName}."
                    )
                    else -> relevant(
                        rule = "type_item_move_type_match",
                        source = "src/battle_util.c:6841",
                        rationale = "The effective move type matches the item's boosted type."
                    )
                }
            }
            "HOLD_EFFECT_LUSTROUS_ORB", "HOLD_EFFECT_ADAMANT_ORB",
            "HOLD_EFFECT_GRISEOUS_ORB", "HOLD_EFFECT_SOUL_DEW" -> {
                val boosted = when (holdEffect) {
                    "HOLD_EFFECT_LUSTROUS_ORB" -> setOf(PokemonType.WATER, PokemonType.DRAGON)
                    "HOLD_EFFECT_ADAMANT_ORB" -> setOf(PokemonType.STEEL, PokemonType.DRAGON)
                    "HOLD_EFFECT_GRISEOUS_ORB" -> setOf(PokemonType.GHOST, PokemonType.DRAGON)
                    else -> setOf(PokemonType.PSYCHIC, PokemonType.DRAGON)
                }
                when (val moveType = c.moveType) {
                    null -> null
                    !in boosted -> proof(
                        rule = "signature_orb_move_type_outside_boost",
                        source = "src/battle_util.c:6822",
                        rationale = "The item boosts only ${boosted.joinToString("/") { it.displayName }} moves; " +
                            "the effective move type is ${moveType.displayName}."
                    )
                    else -> speciesGated()
                }
            }
            "HOLD_EFFECT_LIGHT_BALL", "HOLD_EFFECT_OGERPON_MASK" -> speciesGated()
            "HOLD_EFFECT_LIFE_ORB", "HOLD_EFFECT_EXPERT_BELT", "HOLD_EFFECT_METRONOME" -> relevant(
                rule = "attacker_final_modifier_item",
                source = "src/battle_util.c:7660",
                rationale = "GetAttackerItemsModifier applies this attacker item's final damage modifier."
            )
            "HOLD_EFFECT_SCOPE_LENS", "HOLD_EFFECT_LUCKY_PUNCH", "HOLD_EFFECT_LEEK" -> relevant(
                rule = "attacker_critical_stage_item",
                source = "src/battle_util.c:8047",
                rationale = "The attacker's critical-hit stage changes; critical odds are not modelled."
            )
            "HOLD_EFFECT_PUNCHING_GLOVE" -> unknownRule(
                rule = "punching_flag_unobserved",
                source = "src/battle_util.c:6844",
                rationale = "Punching Glove depends on the move's punching flag, which the request does not carry."
            )
            else -> null
        }
    }

    private fun defenderDefense(holdEffect: String, itemId: Int, c: Context): Proof? {
        if (c.side == HnsItemSide.ATTACKER) {
            return proof(
                rule = "attacker_holds_defender_only_item",
                source = "src/battle_util.c:7351",
                rationale = "This hold effect is read only as the defender's (holdEffectDef); the attacker's copy " +
                    "cannot change its outgoing damage."
            )
        }
        val category = c.moveCategory
        // usesDefStat follows the category unless Wonder Room swaps it (src/battle_util.c:7226).
        val noWonderRoom = c.fieldState?.let { it.fullyDecoded && !it.has(HnsFieldStatus.WONDER_ROOM) } == true
        return when (holdEffect) {
            "HOLD_EFFECT_ASSAULT_VEST" -> when {
                category == MoveCategory.PHYSICAL && noWonderRoom -> specialDefensePhysicalMove()
                category == MoveCategory.SPECIAL -> relevant(
                    rule = "special_defense_item_special_move",
                    source = "src/battle_util.c:7371",
                    rationale = "The x1.5 Sp. Def modifier applies to this special move and is not modelled."
                )
                else -> null
            }
            "HOLD_EFFECT_DEEP_SEA_SCALE" -> when {
                category == MoveCategory.PHYSICAL && noWonderRoom -> specialDefensePhysicalMove()
                category == MoveCategory.SPECIAL -> defenderSpeciesGated()
                else -> null
            }
            "HOLD_EFFECT_METAL_POWDER" -> when {
                category == MoveCategory.SPECIAL && noWonderRoom -> proof(
                    rule = "defense_item_special_move",
                    source = "src/battle_util.c:7358",
                    rationale = "Metal Powder applies only when the move uses Defense; this special move uses Sp. Def " +
                        "and no Wonder Room is active."
                )
                category == MoveCategory.PHYSICAL -> defenderSpeciesGated()
                else -> null
            }
            "HOLD_EFFECT_EVIOLITE" -> defenderSpeciesGated()
            "HOLD_EFFECT_RESIST_BERRY" -> {
                val berryType = HnsItemRegistry.itemTypeName(itemId)?.let(PokemonType::fromString)
                val moveType = c.moveType
                when {
                    berryType == null || moveType == null -> null
                    berryType != moveType -> proof(
                        rule = "resist_berry_other_type",
                        source = "src/battle_util.c:7689",
                        rationale = "The berry weakens only ${berryType.displayName} moves; the effective move type " +
                            "is ${moveType.displayName}."
                    )
                    else -> relevant(
                        rule = "resist_berry_matching_type",
                        source = "src/battle_util.c:7689",
                        rationale = "The effective move type matches the berry; its effectiveness condition is not proven."
                    )
                }
            }
            "HOLD_EFFECT_FOCUS_SASH" -> {
                val hp = c.defenderHp
                val maxHp = c.defenderMaxHp
                when {
                    hp == null || maxHp == null || maxHp <= 0 || hp < 0 || hp > maxHp -> null
                    hp < maxHp -> proof(
                        rule = "focus_sash_defender_below_max_hp",
                        source = "src/battle_util.c:8200",
                        rationale = "Focus Sash endures only at full HP; the live defender HP is below max HP."
                    )
                    else -> relevant(
                        rule = "focus_sash_defender_at_max_hp",
                        source = "src/battle_util.c:8200",
                        rationale = "The live defender is at full HP, so Focus Sash can leave it at 1 HP."
                    )
                }
            }
            "HOLD_EFFECT_FOCUS_BAND" -> relevant(
                rule = "focus_band_random_survival",
                source = "src/battle_util.c:8193",
                rationale = "Focus Band may randomly leave the defender at 1 HP."
            )
            "HOLD_EFFECT_RING_TARGET" -> unknownRule(
                rule = "ring_target_immunity_unmodelled",
                source = "src/battle_util.c:8257",
                rationale = "Ring Target can turn a type immunity into damage; the immunity is not proven absent."
            )
            else -> null
        }
    }

    private fun turnOrder(c: Context): Proof? = when {
        c.ordinaryMove != true || c.attackerAbilityId == null -> null
        c.attackerAbilityId == ANALYTIC_ABILITY_ID -> relevant(
            rule = "turn_order_item_attacker_analytic",
            source = "src/battle_util.c:6691",
            rationale = "The attacker's Analytic depends on turn order, which this speed item can change."
        )
        else -> proof(
            rule = "turn_order_item_ordinary_move",
            source = "src/battle_util.c:6455",
            rationale = "Speed and turn order reach an ordinary move's damage only through Analytic, which the " +
                "attacker does not have."
        )
    }

    private fun grounding(holdEffect: String, c: Context): Proof? {
        // Groundedness reaches an ordinary hit through the Ground-move branches and the terrain
        // checks (IsBattlerTerrainAffected returns FALSE without a terrain bit, src/battle_util.c:5142).
        val noTerrain = c.fieldState?.let { it.fullyDecoded && !it.terrainActive } == true
        if (c.ordinaryMove != true || !noTerrain) return null
        if (holdEffect == "HOLD_EFFECT_IRON_BALL") {
            val speed = turnOrder(c) ?: return null
            if (speed.relevance != HnsItemRequestRelevance.PROVEN_IRRELEVANT) return speed
        }
        if (c.side == HnsItemSide.ATTACKER) {
            return proof(
                rule = "grounding_item_attacker_no_terrain",
                source = "src/battle_util.c:5142",
                rationale = "The attacker's groundedness is read only by terrain checks, and no terrain is active."
            )
        }
        val moveType = c.moveType ?: return null
        return if (moveType == PokemonType.GROUND) {
            relevant(
                rule = "grounding_item_defender_ground_move",
                source = "src/battle_util.c:8392",
                rationale = "The defender's grounding item changes a Ground move's effectiveness."
            )
        } else {
            proof(
                rule = "grounding_item_defender_non_ground_move_no_terrain",
                source = "src/battle_util.c:8379",
                rationale = "The defender's grounding item is read only for Ground moves and terrain; neither applies."
            )
        }
    }

    private fun physicalOnlySpecialMove() = proof(
        rule = "physical_only_item_special_move",
        source = "src/battle_util.c:7178",
        rationale = "This item modifies physical moves only; the effective category is Special."
    )

    private fun specialOnlyPhysicalMove() = proof(
        rule = "special_only_item_physical_move",
        source = "src/battle_util.c:7182",
        rationale = "This item modifies special moves only; the effective category is Physical."
    )

    private fun specialDefensePhysicalMove() = proof(
        rule = "special_defense_item_physical_move",
        source = "src/battle_util.c:7371",
        rationale = "This item modifies Sp. Def only; this physical move uses Defense and Wonder Room is observed inactive."
    )

    private fun speciesGated() = unknownRule(
        rule = "species_gated_item_species_unobserved",
        source = "src/battle_util.c:7174",
        rationale = "Whether this item applies depends on the holder's species, which the item context does not carry."
    )

    private fun defenderSpeciesGated() = unknownRule(
        rule = "defender_species_gated_item",
        source = "src/battle_util.c:7361",
        rationale = "Whether this item applies depends on the defender's species/evolution state, which is not proven here."
    )

    private data class Proof(
        val relevance: HnsItemRequestRelevance,
        val rule: String,
        val source: String,
        val rationale: String
    )

    private fun proof(rule: String, source: String, rationale: String) =
        Proof(HnsItemRequestRelevance.PROVEN_IRRELEVANT, rule, source, rationale)

    private fun relevant(rule: String, source: String, rationale: String) =
        Proof(HnsItemRequestRelevance.RELEVANT, rule, source, rationale)

    private fun modelled(rule: String, source: String, rationale: String) =
        Proof(HnsItemRequestRelevance.MODELLED, rule, source, rationale)

    private fun unknownRule(rule: String, source: String, rationale: String) =
        Proof(HnsItemRequestRelevance.UNKNOWN, rule, source, rationale)

    private fun unknown(id: Int, name: String, side: HnsItemSide) = HnsItemRequestDecision(
        id, name, side, HnsItemRegistry.classify(id).category, HnsItemRequestRelevance.UNKNOWN,
        rationale = "Required authoritative request operand is missing or the context has no reviewed clearance rule."
    )
}
