package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsItemCategory
import com.dualdex.pokemon.hns.Hns205MoveEffects
import com.dualdex.pokemon.hns.HnsItemRegistry
import com.dualdex.pokemon.hns.Hns205SpeciesMechanics

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
    private const val NEUTRALIZING_GAS_ABILITY_ID = 256
    private val MOLD_BREAKER_ABILITY_IDS = setOf(104, 163, 164)
    private const val PROTOSYNTHESIS_ABILITY_ID = 281
    private const val QUARK_DRIVE_ABILITY_ID = 282
    private const val DYNAMAX_GIMMICK = 4
    private const val WEATHER_RAIN_MASK = 0x00000007
    private const val WEATHER_SUN_MASK = 0x00000018

    data class Context(
        val side: HnsItemSide,
        /** Source-proven fixed single-hit move with no move/item interaction; null when the move is unknown. */
        val fixedSingleHitMove: Boolean?,
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
        val defenderMaxHp: Int?,
        val defenderAbilityId: Int? = null,
        val attackerGastroAcid: Boolean? = null,
        val defenderGastroAcid: Boolean? = null,
        val observedBattlersCount: Int? = null,
        val attackerTerrainApplicability: HnsTerrainApplicability? = null,
        val defenderTerrainApplicability: HnsTerrainApplicability? = null,
        /** True only when the pinned selected move carries the `ignoresTargetAbility` flag. */
        val moveIgnoresTargetAbility: Boolean = false,
        /** Shared source-backed effective hold-effect authority; null when state is not live. */
        val holdEffectResolution: HnsHoldEffectResolution? = null,
        /** Shared source-backed berry branch consumed by both capability policy and QuickJS. */
        val resistBerryDecision: HnsResistBerryDecision? = null,
        val attackerSpeciesId: Int? = null,
        val defenderSpeciesId: Int? = null,
        val attackerBaseSpeciesId: Int? = null,
        val defenderBaseSpeciesId: Int? = null,
        val attackerTransformed: Boolean? = null,
        val defenderCanEvolve: Boolean? = null,
        val defenderTransformed: Boolean? = null,
        val defenderTransformedSpeciesId: Int? = null,
        val attackerMetronomeItemCounter: Int? = null,
        val attackerBoosterEnergyActivated: Boolean? = null,
        val attackerParadoxBoostedStat: Int? = null,
        val defenderBoosterEnergyActivated: Boolean? = null,
        val defenderParadoxBoostedStat: Int? = null,
        val attackerSelectedGimmick: Int? = null,
        val attackerActiveGimmick: Int? = null,
        val punchingMove: Boolean? = null,
        val moveUsesDefenseStat: Boolean? = null,
        val switchInEventsSettled: Boolean? = null,
        val effectiveSpeedExact: Boolean = false,
        val moveId: Int? = null
    )

    fun assess(itemId: Int, context: Context?): HnsItemRequestDecision {
        val decision = assessRaw(itemId, context)
        val disposition = com.dualdex.pokemon.hns.HnsGroupEData.itemDispositions[itemId] ?: return decision
        val refused = decision.relevance == HnsItemRequestRelevance.UNKNOWN ||
            (decision.relevance == HnsItemRequestRelevance.RELEVANT &&
                disposition.tier == com.dualdex.pokemon.hns.HnsGroupETier.HARD_REFUSAL)
        return if (refused) decision.copy(
            relevance = HnsItemRequestRelevance.UNKNOWN,
            rule = decision.rule ?: "group_e_" + disposition.family,
            source = decision.source ?: disposition.source,
            rationale = disposition.reason + " " + decision.rationale
        ) else decision
    }

    private fun assessRaw(itemId: Int, context: Context?): HnsItemRequestDecision {
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
        // The e-Reader payload is not the catalogue NONE effect, even under an apparent
        // suppression proof. No runtime identity/effect may be invented from that record.
        if (itemId == 581) return unknown(itemId, name, side)
        val c = context ?: return unknown(itemId, name, side)
        val holdEffect = entry.data?.holdEffect ?: return unknown(itemId, name, c.side)
        val proof: Proof? = if (c.moveId in Hns205MoveEffects.fixedSingleHitSpeedPowerMoveIds &&
            holdEffect in HnsEffectiveSpeedAuthority.speedHoldEffects) {
            if (c.effectiveSpeedExact) modelled(if (c.moveId in com.dualdex.pokemon.hns.Hns205MoveEffects.fixedSingleHitElectroBallMoveIds) "electro_ball_speed_item_exact" else "gyro_ball_speed_item_exact", "src/battle_main.c:4981-4991",
                "The shared effective hold effect is applied at the source Speed item stage.") else null
        } else when (entry.familyGroup) {
            "attacker_offense" -> attackerOffense(holdEffect, itemId, c)
            "defender_defense" -> defenderDefense(holdEffect, itemId, c)
            "post_hit_or_residual" -> when {
                entry.onSwitchInActivation && c.switchInEventsSettled != true -> null
                holdEffect == "HOLD_EFFECT_BLUNDER_POLICY" || holdEffect == "HOLD_EFFECT_ROOM_SERVICE" ->
                    postHitSpeed(holdEffect, c)
                holdEffect == "HOLD_EFFECT_BOOSTER_ENERGY" -> boosterEnergy(c)
                holdEffect == "HOLD_EFFECT_BIG_ROOT" && c.moveId in
                    com.dualdex.pokemon.hns.Hns205MoveEffects.fixedSingleHitDrainMoveIds ->
                    if (c.fixedSingleHitMove == true) proof("drain_big_root_post_hit_only",
                        "src/battle_util.c:1837-1845; src/battle_move_resolution.c:2167-2240",
                        "Big Root scales recovery after measured damage and before Liquid Ooze; no healing or attacker survival is displayed.") else null
                holdEffect == "HOLD_EFFECT_BINDING_BAND" && c.moveId == 250 ->
                    if (c.fixedSingleHitMove == true) proof("whirlpool_binding_band_post_hit_only",
                        "src/battle_end_turn.c:620; src/battle_script_commands.c:2650",
                        "Binding Band changes only later wrap residual, outside Whirlpool's selected hit.") else null
                // A held item can still be waiting to execute in an active but unsettled
                // switch-in frame. Successful activation consumes it, so its live stage and
                // matching terrain cannot prove a still-held item irrelevant.
                holdEffect == "HOLD_EFFECT_TERRAIN_SEED" || holdEffect == "HOLD_EFFECT_BERSERK_GENE" -> null
                else -> proof(
                    rule = "single_hit_item_activation_outside_damage",
                    source = "src/battle_move_resolution.c:2429",
                    rationale = "This effect cannot change the current hit; any pinned switch-in activation is source-generated and must be settled before clearance. Nonordinary move mechanics remain independently refused."
                )
            }
            "turn_order" -> turnOrder(c)
            "weight_only" -> if (c.fixedSingleHitMove == true) proof(
                rule = "weight_item_ordinary_move",
                source = "src/battle_util.c:6079",
                rationale = "Float Stone is read only by GetBattlerWeight, which no ordinary move uses."
            ) else null
            "grounding" -> grounding(holdEffect, c)
            "weather_shield" -> when {
                c.fixedSingleHitMove != true || c.weatherWord == null -> null
                (c.weatherWord and (WEATHER_RAIN_MASK or WEATHER_SUN_MASK)) == 0 -> proof(
                    rule = if (c.weatherWord == 0) "umbrella_clear_weather" else "umbrella_other_weather",
                    source = if (c.weatherWord == 0) "src/battle_util.c:7436" else "src/battle_util.c:9530",
                    rationale = if (c.weatherWord == 0) {
                        "The live weather is observed clear, so Utility Umbrella has no current-hit weather consequence."
                    } else {
                        "The exact weather word contains neither Sun nor Rain; Utility Umbrella suppresses only those weather effects."
                    }
                )
                else -> modelled(
                    rule = "umbrella_sun_or_rain",
                    source = "src/battle_util.c:7440",
                    rationale = "The active Utility Umbrella's Sun/Rain damage and holder-applicability branches are reproduced at their pinned pipeline stages."
                )
            }
            "form_or_ability_changer" -> when (holdEffect) {
                "HOLD_EFFECT_ABILITY_SHIELD" -> abilityShield(c)
                "HOLD_EFFECT_MEGA_STONE", "HOLD_EFFECT_Z_CRYSTAL" -> when {
                    c.fixedSingleHitMove != true -> null
                    c.attackerSelectedGimmick == 0 && c.attackerActiveGimmick == 0 -> proof(
                        rule = "mega_z_no_selected_or_active_gimmick",
                        source = "src/battle_terastal.c:103",
                        rationale = "Both authoritative gimmick operands are GIMMICK_NONE, so this item's Mega/Z consequence is inactive for the ordinary selected hit. Any selected or active gimmick and item-dependent move semantics remain independently gated."
                    )
                    else -> null
                }
                "HOLD_EFFECT_PRIMAL_ORB" -> {
                    val speciesId = if (c.side == HnsItemSide.ATTACKER) c.attackerSpeciesId else c.defenderSpeciesId
                    when {
                        c.switchInEventsSettled != true -> unknownRule(
                            rule = "primal_orb_transition_unsettled",
                            source = "src/data/pokemon/form_change_tables.h:739",
                            rationale = "The primal form-change settlement must finish before live species/ability/weather can be treated as the complete damage authority."
                        )
                        speciesId == null -> unknownRule(
                            rule = "primal_orb_form_unobserved",
                            source = "src/data/pokemon/form_change_tables.h:739",
                            rationale = "Primal Orb has no separate selected-hit multiplier; the source-selected live species must be observed."
                        )
                        else -> modelled(
                            rule = "primal_orb_form_authoritative",
                            source = "src/data/pokemon/form_change_tables.h:739",
                            rationale = "The Red/Blue Orb source table changes live species at battle form settlement; the calculator consumes the observed species/ability/weather and applies no second Orb multiplier."
                        )
                    }
                }
                else -> null
            }
            "identity_exception" -> when (holdEffect) {
                "HOLD_EFFECT_RING_TARGET" -> defenderDefense(holdEffect, itemId, c)
                "HOLD_EFFECT_AIR_BALLOON", "HOLD_EFFECT_IRON_BALL" -> grounding(holdEffect, c)
                else -> null
            }
            else -> null
        }

        // Resolve request-local irrelevance first: a known wrong type/category/species needs no
        // suppression operand. For an effect that could change this selected hit, use the same
        // active/suppressed/unknown authority as the QuickJS item pipeline.
        if (proof?.relevance == HnsItemRequestRelevance.PROVEN_IRRELEVANT) {
            return HnsItemRequestDecision(
                itemId, name, c.side, entry.category, proof.relevance, proof.rule, proof.source, proof.rationale
            )
        }
        // Raw Mega/Z identities need the NONE-gimmick proof above independently of hold-effect
        // suppression; Magic Room/Klutz cannot prove a form or selected Z move inactive.
        if (holdEffect in setOf("HOLD_EFFECT_MEGA_STONE", "HOLD_EFFECT_Z_CRYSTAL")) {
            return unknown(itemId, name, c.side)
        }
        when (val resolution = c.holdEffectResolution) {
            null -> return HnsItemRequestDecision(
                itemId, name, c.side, entry.category, HnsItemRequestRelevance.UNKNOWN,
                rule = "effective_hold_effect_unobserved",
                source = "src/battle_util.c:GetBattlerHoldEffectInternal",
                rationale = "The effective hold effect is not bound to authoritative live suppression state."
            )
            else -> when (resolution.state) {
                HnsHoldEffectState.SUPPRESSED_NONE -> {
                    if (entry.familyGroup == "identity_exception" && holdEffect == "HOLD_EFFECT_NONE") {
                        return HnsItemRequestDecision(
                            itemId, name, c.side, entry.category, HnsItemRequestRelevance.UNKNOWN,
                            rationale = "Suppression only disables a hold effect; this item has separate raw-identity form semantics that remain unresolved."
                        )
                    }
                    return HnsItemRequestDecision(
                        itemId, name, c.side, entry.category, HnsItemRequestRelevance.PROVEN_IRRELEVANT,
                        rule = "effective_hold_effect_suppressed",
                        source = "src/battle_util.c:GetBattlerHoldEffectInternal",
                        rationale = resolution.reason
                    )
                }
                HnsHoldEffectState.UNKNOWN -> return HnsItemRequestDecision(
                    itemId, name, c.side, entry.category, HnsItemRequestRelevance.UNKNOWN,
                    rule = "effective_hold_effect_unobserved",
                    source = "src/battle_util.c:GetBattlerHoldEffectInternal",
                    rationale = resolution.reason
                )
                HnsHoldEffectState.ACTIVE_EXACT -> if (
                    resolution.itemId != itemId || resolution.effectiveHoldEffect != holdEffect
                ) return HnsItemRequestDecision(
                    itemId, name, c.side, entry.category, HnsItemRequestRelevance.UNKNOWN,
                    rule = "effective_hold_effect_unobserved",
                    source = "src/battle_util.c:GetBattlerHoldEffectInternal",
                    rationale = "The live item identity/effect does not match the audited numeric item record."
                )
            }
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
        fixedSingleHitMove: Boolean?
    ): Context {
        val live = request.hnsLiveBattleState
        val authority = HnsMoveAuthority.forRequest(request, fixedSingleHitMove)
        val moveId = com.dualdex.pokemon.hns.HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id
        val moveIgnoresTargetAbility = moveId?.let {
            "ignoresTargetAbility" in Hns205MoveEffects.immunityFlagsById[it].orEmpty()
        } == true
        return Context(
            side = side,
            moveId = moveId,
            effectiveSpeedExact = moveId in Hns205MoveEffects.fixedSingleHitSpeedPowerMoveIds &&
                HnsEffectiveSpeedAuthority.forRequest(request, if (side == HnsItemSide.ATTACKER) HnsAbilitySide.ATTACKER else HnsAbilitySide.DEFENDER).speed != null,
            fixedSingleHitMove = fixedSingleHitMove,
            moveType = authority.effectiveType,
            moveCategory = authority.category,
            fieldState = live?.fieldStatuses?.let(HnsFieldState::decode),
            weatherWord = if (live?.weatherObserved == true) live.weatherWord else null,
            attackerAbilityId = request.attacker.abilityId,
            defenderHp = live?.defenderHp,
            defenderMaxHp = live?.defenderMaxHp,
            defenderAbilityId = request.defender.abilityId,
            attackerGastroAcid = live?.attackerPersistentVolatiles
                ?.takeIf { it.observed }?.gastroAcid,
            defenderGastroAcid = live?.defenderPersistentVolatiles
                ?.takeIf { it.observed }?.gastroAcid,
            attackerTerrainApplicability = live?.attackerTerrainApplicability,
            defenderTerrainApplicability = live?.defenderTerrainApplicability,
            observedBattlersCount = live?.observedBattlersCount,
            moveIgnoresTargetAbility = moveIgnoresTargetAbility,
            holdEffectResolution = request.hnsLiveBattleState?.takeIf {
                (if (side == HnsItemSide.ATTACKER) request.attacker else request.defender).itemId != null
            }?.let {
                HnsHoldEffectAuthority.forRequest(request, side)
            },
            resistBerryDecision = HnsResistBerryAuthority.forRequest(request),
            attackerSpeciesId = live?.attackerSpeciesId,
            defenderSpeciesId = live?.defenderSpeciesId,
            attackerBaseSpeciesId = Hns205SpeciesMechanics.baseSpeciesId(live?.attackerSpeciesId),
            defenderBaseSpeciesId = Hns205SpeciesMechanics.baseSpeciesId(live?.defenderSpeciesId),
            attackerTransformed = live?.attackerTransformed,
            defenderCanEvolve = live?.let { state ->
                val selectedSpecies = when (state.defenderTransformed) {
                    true -> state.defenderTransformedMonSpecies
                    false -> state.defenderSpeciesId
                    null -> null
                }
                Hns205SpeciesMechanics.canEvolve(selectedSpecies)
            },
            defenderTransformed = live?.defenderTransformed,
            defenderTransformedSpeciesId = live?.defenderTransformedMonSpecies,
            attackerMetronomeItemCounter = live?.attackerMetronomeItemCounter,
            attackerBoosterEnergyActivated = live?.attackerBoosterEnergyActivated,
            attackerParadoxBoostedStat = live?.attackerParadoxBoostedStat,
            defenderBoosterEnergyActivated = live?.defenderBoosterEnergyActivated,
            defenderParadoxBoostedStat = live?.defenderParadoxBoostedStat,
            attackerSelectedGimmick = live?.attackerSelectedGimmick,
            attackerActiveGimmick = live?.attackerGimmick,
            punchingMove = moveId?.let { id ->
                if ("punchingMove" in Hns205MoveEffects.unknownAbilityMoveFlagsById[id].orEmpty()) null
                else "punchingMove" in Hns205MoveEffects.abilityMoveFlagsById[id].orEmpty()
            },
            moveUsesDefenseStat = when {
                authority.category == MoveCategory.PHYSICAL -> true
                moveId == null -> null
                Hns205MoveEffects.effectById[moveId] == "EFFECT_PSYSHOCK" -> true
                authority.category == MoveCategory.SPECIAL -> false
                else -> null
            },
            switchInEventsSettled = live?.switchInEventsSettled
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
            "HOLD_EFFECT_CHOICE_BAND" -> when (category) {
                MoveCategory.SPECIAL -> physicalOnlySpecialMove()
                MoveCategory.PHYSICAL -> when (c.attackerActiveGimmick) {
                    null -> null
                    DYNAMAX_GIMMICK -> proof(
                        rule = "choice_item_dynamax_inactive",
                        source = "src/battle_util.c:7178",
                        rationale = "The source explicitly omits Choice Band's Attack modifier while Dynamax is active."
                    )
                    else -> modelled(
                        rule = "physical_only_item_physical_move",
                        source = "src/battle_util.c:7178",
                        rationale = "Choice Band multiplies the Attack accumulator by UQ4.12 1.5 with the source half-down operator when the active gimmick is not Dynamax."
                    )
                }
                else -> null
            }
            "HOLD_EFFECT_MUSCLE_BAND" -> when (category) {
                MoveCategory.SPECIAL -> physicalOnlySpecialMove()
                MoveCategory.PHYSICAL -> modelled(
                    rule = "physical_only_item_physical_move",
                    source = "src/battle_util.c:6813",
                    rationale = "Muscle Band multiplies physical base power using the pinned floored-percent conversion and half-up accumulator."
                )
                else -> null
            }
            "HOLD_EFFECT_THICK_CLUB" -> when (category) {
                MoveCategory.SPECIAL -> physicalOnlySpecialMove()
                MoveCategory.PHYSICAL -> baseSpeciesQualification(
                    c, setOf(104, 105, 973), "src/battle_util.c:7165"
                )
                else -> null
            }
            "HOLD_EFFECT_CHOICE_SPECS" -> when (category) {
                MoveCategory.PHYSICAL -> specialOnlyPhysicalMove()
                MoveCategory.SPECIAL -> when (c.attackerActiveGimmick) {
                    null -> null
                    DYNAMAX_GIMMICK -> proof(
                        rule = "choice_item_dynamax_inactive",
                        source = "src/battle_util.c:7182",
                        rationale = "The source explicitly omits Choice Specs' Attack modifier while Dynamax is active."
                    )
                    else -> modelled(
                        rule = "special_only_item_special_move",
                        source = "src/battle_util.c:7182",
                        rationale = "Choice Specs multiplies the Attack accumulator by UQ4.12 1.5 with the source half-down operator when the active gimmick is not Dynamax."
                    )
                }
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
                MoveCategory.SPECIAL -> currentSpeciesQualification(c, 366, "src/battle_util.c:7168")
                else -> null
            }
            "HOLD_EFFECT_LIGHT_BALL" -> baseSpeciesQualification(c, setOf(25), "src/battle_util.c:7171")
            "HOLD_EFFECT_OGERPON_MASK" -> baseSpeciesQualification(c, setOf(1416), "src/battle_util.c:6855")
            "HOLD_EFFECT_PUNCHING_GLOVE" -> when (c.punchingMove) {
                true -> modelled(
                    rule = "punching_glove_punching_move",
                    source = "src/battle_util.c:6852",
                    rationale = "The H&S ×1.1 base-power modifier and contact suppression use the generated punching-move flag and shared active hold effect."
                )
                false -> proof(
                    rule = "punching_glove_non_punching_move",
                    source = "src/battle_util.c:6852",
                    rationale = "The authoritative move lacks the pinned punchingMove flag, so Punching Glove cannot change this hit or its contact result."
                )
                null -> unknownRule(
                    rule = "punching_flag_unobserved",
                    source = "src/battle_util.c:6852",
                    rationale = "Punching Glove requires the generated move punching flag, which is unavailable for this move."
                )
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
                    else -> modelled(
                        rule = "type_item_move_type_match",
                        source = "src/battle_util.c:6841",
                        rationale = "The matching H&S type item or Gem base-power modifier is reproduced with the generated item operand and pinned fixed-point operator."
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
                    else -> signatureSpeciesQualification(holdEffect, c)
                }
            }
            "HOLD_EFFECT_LIFE_ORB" -> if (c.fixedSingleHitMove == true) modelled(
                rule = "attacker_final_damage_item_modelled",
                source = "src/battle_util.c:7673",
                rationale = "Life Orb uses the source's exact UQ_4_12_FLOORED(1.3) value 5324 in GetOtherModifiers."
            ) else null
            "HOLD_EFFECT_EXPERT_BELT" -> if (c.fixedSingleHitMove == true) modelled(
                rule = "attacker_final_damage_item_modelled",
                source = "src/battle_util.c:7669",
                rationale = "Expert Belt uses the H&S engine's exact type-effectiveness result and UQ_4_12(1.2) in GetOtherModifiers."
            ) else null
            "HOLD_EFFECT_METRONOME" -> when {
                c.fixedSingleHitMove != true -> null
                c.attackerMetronomeItemCounter == null -> unknownRule(
                    rule = "metronome_counter_unobserved",
                    source = "src/battle_util.c:7662",
                    rationale = "Metronome's additive final multiplier requires the live metronomeItemCounter."
                )
                else -> modelled(
                    rule = "attacker_final_damage_item_modelled",
                    source = "src/battle_util.c:7662",
                    rationale = "Metronome uses the observed counter, clamped to five, and the exact source PercentToUQ4_12 additive multiplier."
                )
            }
            "HOLD_EFFECT_SCOPE_LENS", "HOLD_EFFECT_LUCKY_PUNCH", "HOLD_EFFECT_LEEK" ->
                if (c.fixedSingleHitMove == true) proof(
                    rule = "fixed_crit_stage_item",
                    source = "src/battle_util.c:8047",
                    rationale = "The item changes critical-hit odds only; the selected hit's crit flag is fixed."
                ) else null
            else -> null
        }
    }

    private fun baseSpeciesQualification(c: Context, allowed: Set<Int>, source: String): Proof? {
        val base = c.attackerBaseSpeciesId ?: return unknownRule(
            rule = "species_gated_item_species_unobserved",
            source = source,
            rationale = "The live attacker base-species identity is required by this pinned hold-effect predicate."
        )
        return if (base in allowed) modelled(
            rule = "attacker_species_qualified_item",
            source = source,
            rationale = "The pinned base-species predicate matches the source-generated live species/form mapping and the engine applies the item at its exact stage."
        ) else proof(
            rule = "attacker_species_item_mismatch",
            source = source,
            rationale = "The live base species does not match the source predicate for this item."
        )
    }

    private fun currentSpeciesQualification(c: Context, expected: Int, source: String): Proof? {
        val species = c.attackerSpeciesId ?: return unknownRule(
            rule = "species_gated_item_species_unobserved",
            source = source,
            rationale = "The exact current species identity is required by this pinned hold-effect predicate."
        )
        return if (species == expected) modelled(
            rule = "attacker_species_qualified_item",
            source = source,
            rationale = "The exact current species matches the pinned predicate and the H&S engine applies the item at its exact stage."
        ) else proof(
            rule = "attacker_species_item_mismatch",
            source = source,
            rationale = "The exact current species does not match the pinned item predicate."
        )
    }

    private fun resistBerryProof(decision: HnsResistBerryDecision): Proof = when (decision.state) {
        HnsResistBerryState.APPLIES -> when (decision.rule) {
            "resist_berry_half", "resist_berry_ripen_quarter" -> modelled(
                rule = decision.rule,
                source = "src/battle_util.c:7686-7695",
                rationale = decision.rationale
            )
            else -> unknownRule(
                rule = "resist_berry_authority_unobserved",
                source = "src/battle_util.c:7686-7695",
                rationale = "The shared authority returned an unreviewed active-berry rule: ${decision.rule}."
            )
        }
        HnsResistBerryState.NOT_APPLICABLE -> when (decision.rule) {
            "resist_berry_no_current_item",
            "resist_berry_other_item",
            "resist_berry_hold_effect_suppressed",
            "resist_berry_other_type",
            "resist_berry_condition_not_met" -> proof(
                rule = decision.rule,
                source = "src/battle_util.c:7686-7695",
                rationale = decision.rationale
            )
            else -> unknownRule(
                rule = "resist_berry_authority_unobserved",
                source = "src/battle_util.c:7686-7695",
                rationale = "The shared authority returned an unreviewed inactive-berry rule: ${decision.rule}."
            )
        }
        HnsResistBerryState.BLOCKED_BY_UNNERVE -> proof(
            rule = "resist_berry_unnerve_blocked",
            source = "src/battle_util.c:336-372, 7686-7695",
            rationale = decision.rationale
        )
        HnsResistBerryState.UNKNOWN -> unknownRule(
            rule = "resist_berry_authority_unobserved",
            source = "src/battle_util.c:7686-7695",
            rationale = decision.rationale
        )
    }

    private fun signatureSpeciesQualification(holdEffect: String, c: Context): Proof? {
        val requiredBase = when (holdEffect) {
            "HOLD_EFFECT_LUSTROUS_ORB" -> 484 // Palkia
            "HOLD_EFFECT_ADAMANT_ORB" -> 483 // Dialga
            "HOLD_EFFECT_GRISEOUS_ORB" -> 487 // Giratina
            else -> null
        }
        val speciesMatches = if (holdEffect == "HOLD_EFFECT_SOUL_DEW") {
            c.attackerSpeciesId?.let { it == 380 || it == 381 }
        } else {
            requiredBase?.let { required -> c.attackerBaseSpeciesId?.let { it == required } }
        } ?: return unknownRule(
            rule = "species_gated_item_species_unobserved",
            source = "src/battle_util.c:6822",
            rationale = "The pinned signature-item predicate requires an observed live species identity."
        )
        return if (speciesMatches) modelled(
            rule = "attacker_species_qualified_item",
            source = "src/battle_util.c:6822",
            rationale = "The pinned species and move-type predicate is reproduced with generated base-species data and the H&S base-power stage."
        ) else proof(
            rule = "attacker_species_item_mismatch",
            source = "src/battle_util.c:6822",
            rationale = "The live species does not match the pinned signature-item predicate."
        )
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
        fun selectedStatIsDefense(): Boolean? {
            // CalcDefenseStat's exact usesDefStat result also covers Psyshock and Wonder Room.
            val usesDefStatBeforeRoom = c.moveUsesDefenseStat ?: return null
            val wonderRoom = c.fieldState?.let {
                if (it.fullyDecoded) it.has(HnsFieldStatus.WONDER_ROOM) else return null
            } ?: return null
            return if (wonderRoom) !usesDefStatBeforeRoom else usesDefStatBeforeRoom
        }
        return when (holdEffect) {
            "HOLD_EFFECT_ASSAULT_VEST" -> when (selectedStatIsDefense()) {
                null -> null
                true -> proof("defense_item_stat_not_selected", "src/battle_util.c:7371",
                    "The selected hit uses Defense, while Assault Vest only multiplies Sp. Def.")
                false -> modelled("defense_stat_item_modelled", "src/battle_util.c:7371",
                    "Assault Vest's Sp. Def ×1.5 half-down modifier is reproduced at the pinned defense-stat stage.")
            }
            "HOLD_EFFECT_DEEP_SEA_SCALE" -> {
                val usesDefStat = selectedStatIsDefense() ?: return null
                when {
                    usesDefStat -> proof("defense_item_stat_not_selected", "src/battle_util.c:7354",
                        "The selected hit uses Defense, while Deep Sea Scale multiplies Sp. Def only.")
                    c.defenderSpeciesId == null -> defenderSpeciesGated()
                    c.defenderSpeciesId != 366 -> proof("defender_species_item_mismatch", "src/battle_util.c:7354",
                        "Deep Sea Scale checks exact current species Clamperl.")
                    else -> modelled("defense_stat_item_modelled", "src/battle_util.c:7354",
                        "Deep Sea Scale's Clamperl Sp. Def ×2 half-down modifier is reproduced at the pinned defense-stat stage.")
                }
            }
            "HOLD_EFFECT_METAL_POWDER" -> {
                val usesDefStat = selectedStatIsDefense() ?: return null
                when {
                    !usesDefStat -> proof("defense_item_stat_not_selected", "src/battle_util.c:7358",
                        "The selected hit uses Sp. Def, while Metal Powder multiplies Defense only.")
                    c.defenderSpeciesId == null || c.defenderTransformed == null -> defenderSpeciesGated()
                    c.defenderSpeciesId != 132 || c.defenderTransformed -> proof(
                        "defender_species_item_mismatch", "src/battle_util.c:7358",
                        "Metal Powder applies only to current Ditto while it is not transformed."
                    )
                    else -> modelled("defense_stat_item_modelled", "src/battle_util.c:7358",
                        "Metal Powder's untransformed Ditto Defense ×2 half-down modifier is reproduced at the pinned defense-stat stage.")
                }
            }
            "HOLD_EFFECT_EVIOLITE" -> when (c.defenderCanEvolve) {
                true -> modelled("defense_stat_item_modelled", "src/battle_util.c:7366",
                    "Eviolite uses generated CanEvolve data for the live or transformed species and applies its ×1.5 half-down modifier at the pinned defense-stat stage.")
                false -> proof("defender_species_not_evolvable", "src/battle_util.c:7366",
                    "The source-generated CanEvolve result is false for the species selected by the pinned transformed-species rule.")
                null -> defenderSpeciesGated()
            }
            "HOLD_EFFECT_RESIST_BERRY" -> {
                val decision = c.resistBerryDecision?.takeIf { it.itemId == itemId } ?: return unknownRule(
                    rule = "resist_berry_authority_unobserved",
                    source = "src/battle_util.c:7686-7695",
                    rationale = "The shared live resist-berry authority is absent or refers to a different current numeric item."
                )
                resistBerryProof(decision)
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
            "HOLD_EFFECT_RING_TARGET" -> modelled(
                rule = "ring_target_immunity_modelled",
                source = "src/battle_util.c:8257",
                rationale = "Ring Target replaces zero type-chart entries before ability and groundedness checks; the H&S immunity layer models this exact scope."
            )
            else -> null
        }
    }

    private fun abilityShield(c: Context): Proof? {
        if (c.fixedSingleHitMove != true || c.observedBattlersCount != 2) return null
        val attackerAbility = c.attackerAbilityId ?: return null
        val defenderAbility = c.defenderAbilityId ?: return null
        val attackerGastroAcid = c.attackerGastroAcid ?: return null
        val defenderGastroAcid = c.defenderGastroAcid ?: return null

        val holderAbility = if (c.side == HnsItemSide.ATTACKER) attackerAbility else defenderAbility
        val holderGastroAcid = if (c.side == HnsItemSide.ATTACKER) attackerGastroAcid else defenderGastroAcid
        val neutralizingGasActive =
            (attackerAbility == NEUTRALIZING_GAS_ABILITY_ID && !attackerGastroAcid) ||
                (defenderAbility == NEUTRALIZING_GAS_ABILITY_ID && !defenderGastroAcid)
        val holderAbilityCanBeSuppressed = holderGastroAcid ||
            (neutralizingGasActive && holderAbility != NEUTRALIZING_GAS_ABILITY_ID)
        val attackerCanBreakDefenderAbility = c.side == HnsItemSide.DEFENDER &&
            ((!attackerGastroAcid && attackerAbility in MOLD_BREAKER_ABILITY_IDS) ||
                c.moveIgnoresTargetAbility)
        val suppressionSource = when {
            holderGastroAcid -> "src/battle_util.c:5015"
            neutralizingGasActive && holderAbility != NEUTRALIZING_GAS_ABILITY_ID ->
                "src/battle_util.c:5018"
            c.side == HnsItemSide.DEFENDER && c.moveIgnoresTargetAbility -> "src/battle_util.c:9980"
            attackerCanBreakDefenderAbility -> "src/battle_util.c:4976"
            else -> null
        }

        return if (holderAbilityCanBeSuppressed || attackerCanBreakDefenderAbility) {
            if (c.side == HnsItemSide.DEFENDER && attackerCanBreakDefenderAbility &&
                !holderAbilityCanBeSuppressed
            ) modelled(
                rule = "ability_shield_current_suppression",
                source = suppressionSource ?: "src/battle_util.c:4976",
                rationale = "The exact current Ability Shield prevents the pinned Mold Breaker or move-level ability-bypass check for this holder; the live effective defender ability is preserved."
            ) else relevant(
                rule = "ability_shield_current_suppression",
                source = suppressionSource ?: "src/battle_util.c:4998",
                rationale = "A live Gastro Acid, Neutralizing Gas, or defender-side ability-breaking attack can change whether the holder's ability affects this hit."
            )
        } else {
            proof(
                rule = "ability_shield_no_current_suppression",
                source = "src/battle_util.c:4998",
                rationale = "The live Singles participants have no current ability-suppression source for this ordinary hit."
            )
        }
    }

    private fun turnOrder(c: Context): Proof? = when {
        c.fixedSingleHitMove != true || c.attackerAbilityId == null -> null
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

    private fun postHitSpeed(holdEffect: String, c: Context): Proof = proof(
        rule = "post_hit_speed_item_current_hit",
        source = if (holdEffect == "HOLD_EFFECT_ROOM_SERVICE")
            "src/battle_hold_effects.c:1058" else "src/battle_hold_effects.c:1109",
        rationale = if (holdEffect == "HOLD_EFFECT_ROOM_SERVICE") {
            "After switch-in settlement, Room Service writes Speed when Trick Room activates, after the current hit's order is established; the current-action Analytic authority owns any turn-order operand and unsupported moves remain independently refused."
        } else {
            "Blunder Policy writes Speed after a miss, after the current hit's order is established; the current-action Analytic authority owns any turn-order operand and unsupported moves remain independently refused."
        }
    )

    private fun grounding(holdEffect: String, c: Context): Proof? {
        // Groundedness reaches an ordinary hit through the Ground-move branches and the terrain
        // checks (IsBattlerTerrainAffected returns FALSE without a terrain bit, src/battle_util.c:5142).
        val noTerrain = c.fieldState?.let { it.fullyDecoded && !it.terrainActive } == true
        if (c.fixedSingleHitMove != true) return null
        if (c.fieldState?.let { it.fullyDecoded && it.terrainActive } == true) {
            val applicability = when (c.side) {
                HnsItemSide.ATTACKER -> c.attackerTerrainApplicability
                HnsItemSide.DEFENDER -> c.defenderTerrainApplicability
            } ?: return null
            if (applicability == HnsTerrainApplicability.UNKNOWN) return null
            if (holdEffect == "HOLD_EFFECT_IRON_BALL") {
                val speed = turnOrder(c) ?: return null
                if (speed.relevance != HnsItemRequestRelevance.PROVEN_IRRELEVANT) return speed
            }
            return modelled(
                rule = if (c.side == HnsItemSide.ATTACKER)
                    "grounding_item_attacker_terrain_authority" else "grounding_item_defender_terrain_authority",
                source = "src/battle_util.c:6021-6036,6639-6645",
                rationale = "The live Air Balloon or Iron Ball identity is an operand of the shared HnsTerrainAuthority; its exact AFFECTED/NOT_AFFECTED result controls the ordinary terrain modifier."
            )
        }
        if (!noTerrain) return null
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
            modelled(
                rule = "grounding_item_defender_ground_move",
                source = "src/battle_util.c:8392",
                rationale = "The current Air Balloon or Iron Ball is forwarded to the H&S immunity layer, which reproduces the pinned Ground-move interaction after Gravity and persistent-volatile exclusions."
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

    private fun boosterEnergy(c: Context): Proof? {
        val abilityId = (if (c.side == HnsItemSide.ATTACKER) c.attackerAbilityId else c.defenderAbilityId)
            ?: return unknownRule(
                rule = "booster_energy_boost_payload_unobserved",
                source = "src/battle_script_commands.c:13466",
                rationale = "The live effective holder ability is needed to determine whether Booster Energy can activate a modeled Paradox effect."
            )
        if (abilityId != PROTOSYNTHESIS_ABILITY_ID && abilityId != QUARK_DRIVE_ABILITY_ID) {
            return proof(
                rule = "booster_energy_non_paradox_ability",
                source = "src/battle_script_commands.c:13470",
                rationale = "The source activates Booster Energy only for Protosynthesis or Quark Drive; the holder has neither ability."
            )
        }
        val transformed = if (c.side == HnsItemSide.ATTACKER) c.attackerTransformed else c.defenderTransformed
        if (c.switchInEventsSettled != true || transformed == null) {
            return unknownRule(
                rule = "booster_energy_boost_payload_unobserved",
                source = "src/battle_hold_effects.c:64",
                rationale = "The exact switch-in settlement and Transform flag are required because TryBoosterEnergy refuses activation for a transformed battler."
            )
        }
        if (transformed) {
            return modelled(
                rule = "booster_energy_payload_modelled",
                source = "src/battle_hold_effects.c:64",
                rationale = "The pinned TryBoosterEnergy branch exits for a transformed battler, so this held item adds no Paradox activation; the calculator also suppresses the transformed ability boost."
            )
        }
        val activated = if (c.side == HnsItemSide.ATTACKER) c.attackerBoosterEnergyActivated else c.defenderBoosterEnergyActivated
        val boostedStat = if (c.side == HnsItemSide.ATTACKER) c.attackerParadoxBoostedStat else c.defenderParadoxBoostedStat
        if (c.switchInEventsSettled != true || activated == null || boostedStat == null || boostedStat !in 0..5) {
            return unknownRule(
                rule = "booster_energy_boost_payload_unobserved",
                source = "src/battle_hold_effects.c:64",
                rationale = "The settled live Booster Energy/Paradox activation payload is required; item identity alone cannot establish the selected-hit ability boost."
            )
        }
        if (activated) return unknownRule(
            rule = "booster_energy_boost_payload_unobserved",
            source = "src/battle_hold_effects.c:64",
            rationale = "The current item is still Booster Energy while the live payload says it activated; a settled source activation consumes the item, so this contradictory state fails closed."
        )
        return modelled(
            rule = "booster_energy_payload_modelled",
            source = "src/battle_hold_effects.c:64",
            rationale = "Booster Energy has no separate damage multiplier; its consumed/held state and Paradox stat payload are already represented by the live ability modifier."
        )
    }

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

    private fun unknown(id: Int, name: String, side: HnsItemSide, rationale: String? = null) = HnsItemRequestDecision(
        id, name, side, HnsItemRegistry.classify(id).category, HnsItemRequestRelevance.UNKNOWN,
        rationale = rationale ?: "Required authoritative request operand is missing or the context has no reviewed clearance rule."
    )
}
