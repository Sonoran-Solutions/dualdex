package com.dualdex.calculator

import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsFieldStatusData
import com.dualdex.pokemon.hns.HnsItemCategory
import com.dualdex.pokemon.hns.HnsItemRegistry

/** Request-local field result. MODELLED means every applicable selected-hit effect is exact. */
enum class HnsFieldRequestRelevance { PROVEN_IRRELEVANT, MODELLED, RELEVANT, UNKNOWN }

/**
 * Auditable outcome for one active `gFieldStatuses` condition of a live H&S request.
 *
 * [status] is the pinned bit, or null for [rawMask] bits outside the reviewed pinned mask. Only
 * this condition's own limitation is decided here; a PROVEN_IRRELEVANT result never removes an
 * ability, item, move, weather, side-status, volatile or gimmick limitation.
 */
data class HnsFieldRequestDecision(
    val status: HnsFieldStatus?,
    val rawMask: Int,
    val relevance: HnsFieldRequestRelevance,
    val rule: String? = null,
    val source: String? = null,
    val rationale: String
) {
    /** User-facing name, e.g. `Electric Terrain` or `Unknown field bits 0x00002000`. */
    val label: String
        get() = status?.displayName ?: "Unknown field bits ${HnsFieldState.formatMask(rawMask)}"
}

/**
 * Source-backed request-local relevance rules for the live H&S field word.
 *
 * Each active bit is assessed independently. Irrelevance is proven only when every operand of a
 * reviewed predicate is authoritative; complete relevant decisions can be named as caveats by the
 * outer capability policy, while unknown relevance remains a hard refusal. The rules, their operands and pinned evidence live in
 * `tools/hns-field-status/field_audit.json`; `generate_hns_field_status.py --check` fails when a
 * rule implemented here is not reviewed there (or vice versa), when its evidence lines change, or
 * when a pinned field-status read appears that the audit does not list.
 *
 * Ion Deluge keeps its dedicated handling: when it is relevant (a Normal move) the capability policy
 * refuses with the active dynamic-type limitation, exactly as before this policy existed.
 */
object HnsFieldContextPolicy {

    data class Context(
        /** Source-proven fixed single-hit move with no move/item interaction; null when the move is unknown. */
        val fixedSingleHitMove: Boolean?,
        /** Pinned move ID, or null when the move is unknown. */
        val moveId: Int?,
        /** Authoritative type before the field/volatile rewrite ([HnsMoveAuthority.preFieldType]). */
        val preFieldMoveType: PokemonType?,
        /** Authoritative effective move type ([HnsMoveAuthority.effectiveType]). */
        val effectiveMoveType: PokemonType?,
        val attackerAbilityId: Int?,
        val defenderAbilityId: Int?,
        /** Authoritative live battle-effective item IDs; null when not authoritatively read. */
        val attackerItemId: Int?,
        val defenderItemId: Int?,
        /** Authoritative final category, or null when the selected-hit category is unknown. */
        val moveCategory: MoveCategory? = null,
        /** Authoritative raw field word used to exclude unsupported Wonder Room stat selection. */
        val fieldStatuses: Int? = null,
        val attackerTerrainApplicability: HnsTerrainApplicability? = null,
        val defenderTerrainApplicability: HnsTerrainApplicability? = null,
        val attackerHoldEffectResolution: HnsHoldEffectResolution? = null,
        val defenderHoldEffectResolution: HnsHoldEffectResolution? = null
    )

    fun assess(state: HnsFieldState, context: Context?): List<HnsFieldRequestDecision> {
        val decisions = state.active.map { status -> decide(status, context) }
        if (state.unknownMask == 0) return decisions
        return decisions + HnsFieldRequestDecision(
            status = null,
            rawMask = state.unknownMask,
            relevance = HnsFieldRequestRelevance.UNKNOWN,
            rule = "unknown_field_bits",
            source = "include/constants/battle.h:405",
            rationale = "The pinned build defines no field bit above STATUS_FIELD_FAIRY_LOCK; an unexpected bit is " +
                "preserved and never assumed harmless."
        )
    }

    /** Builds the field context from a request whose live state was rebound by CalcRequestBoundary. */
    fun contextForRequest(
        request: DamageCalculationRequest,
        fixedSingleHitMove: Boolean?,
        moveId: Int?
    ): Context {
        val authority = HnsMoveAuthority.forRequest(request, fixedSingleHitMove)
        return Context(
            fixedSingleHitMove = fixedSingleHitMove,
            moveId = moveId,
            preFieldMoveType = authority.preFieldType,
            effectiveMoveType = authority.effectiveType,
            moveCategory = authority.category,
            fieldStatuses = request.hnsLiveBattleState?.fieldStatuses,
            attackerTerrainApplicability = request.hnsLiveBattleState?.attackerTerrainApplicability,
            defenderTerrainApplicability = request.hnsLiveBattleState?.defenderTerrainApplicability,
            attackerAbilityId = liveAbility(request.attacker),
            defenderAbilityId = liveAbility(request.defender),
            attackerItemId = liveItem(request.attacker),
            defenderItemId = liveItem(request.defender),
            attackerHoldEffectResolution = HnsHoldEffectAuthority.forRequest(request, HnsItemSide.ATTACKER),
            defenderHoldEffectResolution = HnsHoldEffectAuthority.forRequest(request, HnsItemSide.DEFENDER)
        )
    }

    private fun decide(status: HnsFieldStatus, c: Context?): HnsFieldRequestDecision {
        val proof: Proof? = when (status) {
            HnsFieldStatus.FAIRY_LOCK -> proof(
                rule = "fairy_lock_escape_only",
                source = "src/battle_util.c:5107",
                rationale = "Fairy Lock is read only by CanBattlerEscape; no damage path reads it."
            )
            HnsFieldStatus.WONDER_ROOM -> relevant(
                rule = "wonder_room_swaps_defensive_stat",
                source = "src/battle_util.c:7226",
                rationale = "Wonder Room swaps the Defense / Sp. Def used by this hit and flips every usesDefStat " +
                    "check; the swap is not modelled."
            )
            else -> if (c == null || c.fixedSingleHitMove != true) null else ordinary(status, c)
        }
        return when (proof) {
            null -> HnsFieldRequestDecision(
                status, status.mask, HnsFieldRequestRelevance.UNKNOWN,
                rationale = "Required authoritative request operand is missing or the context has no reviewed " +
                    "clearance rule."
            )
            else -> HnsFieldRequestDecision(
                status, status.mask, proof.relevance, proof.rule, proof.source, proof.rationale
            )
        }
    }

    /** Rules for an ordinary move; [c] is known to describe an ordinary single-hit move. */
    private fun ordinary(status: HnsFieldStatus, c: Context): Proof? {
        val type = c.effectiveMoveType
        val attacker = c.attackerAbilityId
        val defender = c.defenderAbilityId
        return when (status) {
            HnsFieldStatus.MAGIC_ROOM -> {
                val items = listOf(c.attackerItemId, c.defenderItemId)
                if (items.any { it == null }) null
                else if (items.any { HnsItemRegistry.classify(it).category == HnsItemCategory.UNCLASSIFIED }) null
                else if (items.zip(listOf(c.attackerHoldEffectResolution, c.defenderHoldEffectResolution))
                    .all { (id, resolution) ->
                        val item = HnsItemRegistry.classify(id)
                        resolution != null && resolution.itemId == id &&
                            resolution.state != HnsHoldEffectState.UNKNOWN &&
                            resolution.effectiveHoldEffect == "HOLD_EFFECT_NONE" &&
                            !(item.familyGroup == "identity_exception" &&
                                item.data?.holdEffect == "HOLD_EFFECT_NONE" && id != 0)
                    }
                ) modelled(
                    rule = "magic_room_held_items_neutral",
                    source = "src/battle_util.c:5827",
                    rationale = "Both authoritative current items resolve to HOLD_EFFECT_NONE under Magic Room, and no unresolved identity-only NONE exception remains. The shared item policy preserves raw item identity and independent move-interaction gates."
                ) else null
            }
            HnsFieldStatus.TRICK_ROOM -> when (attacker) {
                null -> null
                HnsFieldStatusData.ABILITY_ANALYTIC -> relevant(
                    rule = "trick_room_attacker_analytic",
                    source = "src/battle_util.c:6691",
                    rationale = "The attacker's Analytic depends on turn order, which Trick Room reverses."
                )
                else -> proof(
                    rule = "trick_room_attacker_not_analytic",
                    source = "src/battle_main.c:5091",
                    rationale = "Trick Room only reverses turn order, which reaches an ordinary hit only through the " +
                        "attacker's Analytic."
                )
            }
            HnsFieldStatus.MUD_SPORT -> typeRule(
                type, PokemonType.ELECTRIC,
                irrelevant = "mud_sport_non_electric_move" to "src/battle_util.c:6283",
                matching = "mud_sport_electric_move" to "src/battle_util.c:6286",
                what = "Mud Sport weakens only Electric moves"
            )
            HnsFieldStatus.WATER_SPORT -> typeRule(
                type, PokemonType.FIRE,
                irrelevant = "water_sport_non_fire_move" to "src/battle_util.c:6303",
                matching = "water_sport_fire_move" to "src/battle_util.c:6306",
                what = "Water Sport weakens only Fire moves"
            )
            HnsFieldStatus.GRAVITY -> when {
                c.moveId == null -> null
                c.moveId in HnsFieldStatusData.gravityBannedOrdinaryMoveIds -> relevant(
                    rule = "gravity_banned_move",
                    source = "src/battle_move_resolution.c:334",
                    rationale = "This move is gravityBanned, so it fails under Gravity."
                )
                type == null -> null
                type == PokemonType.GROUND -> relevant(
                    rule = "gravity_ground_move",
                    source = "src/battle_util.c:8379",
                    rationale = "Gravity grounds every battler, which can remove a Ground move's immunity."
                )
                gravityTerrainModifierIsModelled(c) -> modelled(
                    rule = "gravity_terrain_modifier_modelled",
                    source = "src/battle_util.c:6021-6036,6639-6645",
                    rationale = "Gravity's groundedness override is the exact reason the active terrain modifier applies; the same live terrain authority is used by the terrain bit decision."
                )
                else -> proof(
                    rule = "gravity_non_ground_unbanned_move",
                    source = "src/battle_util.c:8379",
                    rationale = "Gravity changes only groundedness (read for Ground moves and terrain), move legality " +
                        "and accuracy; this ${type.displayName} move is not gravityBanned."
                )
            }
            HnsFieldStatus.GRASSY_TERRAIN -> when {
                attacker == HnsFieldStatusData.ABILITY_ANALYTIC &&
                    c.moveId == HnsFieldStatusData.GRASSY_GLIDE_MOVE_ID -> relevant(
                    rule = "grassy_terrain_attacker_analytic",
                    source = "src/battle_main.c:5044",
                    rationale = "Grassy Glide's terrain priority can change the turn order the attacker's Analytic reads."
                )
                type == null || attacker == null || defender == null -> null
                else -> grassyTerrainProof(c, type, defender)
            }
            HnsFieldStatus.MISTY_TERRAIN -> when {
                type == null -> null
                type != PokemonType.DRAGON -> proof(
                    "misty_terrain_non_dragon_move", "src/battle_util.c:6641",
                    "Only Dragon moves query Misty Terrain for ordinary selected-hit damage."
                )
                c.defenderTerrainApplicability == null ||
                    c.defenderTerrainApplicability == HnsTerrainApplicability.UNKNOWN -> null
                else -> modelled(
                    if (c.defenderTerrainApplicability == HnsTerrainApplicability.AFFECTED)
                        "misty_terrain_grounded_defender_dragon" else "misty_terrain_ungrounded_defender_dragon",
                    "src/battle_util.c:6641",
                    "Misty Terrain checks the defender's authoritative IsBattlerTerrainAffected result; the Dragon reduction is applied only when it is AFFECTED."
                )
            }
            HnsFieldStatus.ELECTRIC_TERRAIN -> when {
                attacker == HnsFieldStatusData.ABILITY_QUARK_DRIVE ||
                    defender == HnsFieldStatusData.ABILITY_QUARK_DRIVE -> relevant(
                    rule = "electric_terrain_paradox_ability",
                    source = "src/battle_util.c:7098",
                    rationale = "Quark Drive reads Electric Terrain in a stat modifier whose selected stat depends on unobserved volatile/stat-choice state."
                )
                attacker == HnsFieldStatusData.ABILITY_ANALYTIC -> relevant(
                    rule = "electric_terrain_attacker_analytic",
                    source = "src/battle_main.c:4959",
                    rationale = "Surge Surfer / Quark Drive speed can change the turn order the attacker's Analytic reads."
                )
                type == null || attacker == null || defender == null -> null
                type == PokemonType.ELECTRIC && c.attackerTerrainApplicability == null -> null
                type == PokemonType.ELECTRIC && c.attackerTerrainApplicability == HnsTerrainApplicability.UNKNOWN -> null
                attacker == HnsFieldStatusData.ABILITY_HADRON_ENGINE && c.moveCategory == null -> null
                attacker == HnsFieldStatusData.ABILITY_HADRON_ENGINE && c.moveCategory == MoveCategory.STATUS -> null
                attacker == HnsFieldStatusData.ABILITY_HADRON_ENGINE &&
                    c.moveCategory == MoveCategory.PHYSICAL && type != PokemonType.ELECTRIC -> proof(
                    "electric_terrain_hadron_engine_physical", "src/battle_util.c:7111",
                    "Hadron Engine's Attack-stat branch applies only to Special moves, and this move is not Electric."
                )
                else -> modelled(
                    when {
                        type == PokemonType.ELECTRIC && c.attackerTerrainApplicability == HnsTerrainApplicability.AFFECTED &&
                            attacker == HnsFieldStatusData.ABILITY_HADRON_ENGINE && c.moveCategory == MoveCategory.SPECIAL ->
                            "electric_terrain_grounded_electric_hadron_special"
                        type == PokemonType.ELECTRIC && c.attackerTerrainApplicability == HnsTerrainApplicability.AFFECTED ->
                            "electric_terrain_grounded_electric_move"
                        type == PokemonType.ELECTRIC -> "electric_terrain_ungrounded_electric_move"
                        attacker == HnsFieldStatusData.ABILITY_HADRON_ENGINE && c.moveCategory == MoveCategory.SPECIAL ->
                            "electric_terrain_hadron_engine_special"
                        attacker == HnsFieldStatusData.ABILITY_HADRON_ENGINE -> "electric_terrain_hadron_engine_physical"
                        else -> "electric_terrain_other_move"
                    },
                    "src/battle_util.c:6643",
                    "The ordinary Electric Terrain move modifier uses attacker terrain applicability; Hadron Engine independently reads the live Electric Terrain bit for Special moves."
                )
            }
            HnsFieldStatus.PSYCHIC_TERRAIN -> when {
                c.moveId == null -> null
                attacker == 205 && c.moveId in com.dualdex.pokemon.hns.Hns205MoveEffects.fixedSingleHitDrainMoveIds ->
                    when(c.defenderTerrainApplicability) {
                        HnsTerrainApplicability.NOT_AFFECTED -> proof(
                            "psychic_terrain_drain_priority_ungrounded_target", "src/battle_util.c:2394-2395",
                            "Triage's +3 priority does not fail against a source-proven terrain-unaffected target.")
                        HnsTerrainApplicability.AFFECTED -> relevant(
                            "psychic_terrain_priority_move", "src/battle_util.c:2394-2395",
                            "Psychic Terrain prevents this Triage drain against the observed grounded target.")
                        else -> null
                    }
                c.moveId in HnsFieldStatusData.positivePriorityOrdinaryMoveIds -> relevant(
                    rule = "psychic_terrain_priority_move",
                    source = "src/battle_util.c:2394",
                    rationale = "Psychic Terrain makes a positive-priority move fail against a grounded target."
                )
                type == null || attacker == null || attacker == HnsFieldStatusData.ABILITY_GALE_WINGS -> null
                type == PokemonType.PSYCHIC && (c.attackerTerrainApplicability == null ||
                    c.attackerTerrainApplicability == HnsTerrainApplicability.UNKNOWN) -> null
                else -> if (type == PokemonType.PSYCHIC &&
                    c.attackerTerrainApplicability == HnsTerrainApplicability.AFFECTED) {
                    modelled(
                        "psychic_terrain_grounded_attacker_nonpriority_move", "src/battle_util.c:6645",
                        "The attacker is terrain-affected and the ordinary Psychic move has pinned non-positive priority, so the boost applies and priority blocking is proven irrelevant."
                    )
                } else proof(
                    "psychic_terrain_non_psychic_nonpriority_move", "src/battle_util.c:2394",
                    "The move cannot be blocked by Psychic Terrain and is either non-Psychic or its attacker is proven ungrounded."
                )
            }
            HnsFieldStatus.ION_DELUGE -> when (val preField = c.preFieldMoveType) {
                null -> null
                PokemonType.NORMAL -> relevant(
                    rule = "ion_deluge_normal_move",
                    source = "src/battle_main.c:6437",
                    rationale = "Ion Deluge turns this Normal move Electric; the active rewrite is not modelled."
                )
                else -> proof(
                    rule = "ion_deluge_non_normal_move",
                    source = "src/battle_main.c:6437",
                    rationale = "Ion Deluge rewrites only Normal moves; this move is ${preField.displayName}."
                )
            }
            HnsFieldStatus.WONDER_ROOM, HnsFieldStatus.FAIRY_LOCK -> null
        }
    }

    private fun typeRule(
        type: PokemonType?,
        affected: PokemonType,
        irrelevant: Pair<String, String>,
        matching: Pair<String, String>,
        what: String
    ): Proof? = when (type) {
        null -> null
        affected -> Proof(
            HnsFieldRequestRelevance.RELEVANT, matching.first, matching.second,
            "$what, and the effective move type is ${affected.displayName}."
        )
        else -> Proof(
            HnsFieldRequestRelevance.PROVEN_IRRELEVANT, irrelevant.first, irrelevant.second,
            "$what; the effective move type is ${type.displayName}."
        )
    }

    private fun grassyTerrainProof(
        c: Context,
        type: PokemonType,
        defender: Int
    ): Proof? {
        val directApplies = type == PokemonType.GRASS
        if (directApplies && (c.attackerTerrainApplicability == null ||
                c.attackerTerrainApplicability == HnsTerrainApplicability.UNKNOWN)) return null
        if (defender == HnsFieldStatusData.ABILITY_GRASS_PELT) {
            val category = c.moveCategory ?: return null
            val field = c.fieldStatuses ?: return null
            if (field and HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM != 0) return null
            return modelled(
                if (category == MoveCategory.PHYSICAL) "grassy_terrain_grass_pelt_physical_composition"
                else "grassy_terrain_grass_pelt_special_no_effect",
                "src/battle_util.c:6639,7296",
                "The direct Grass move modifier (when terrain affects the attacker) and Grass Pelt's Defense-stage branch are both evaluated exactly with Wonder Room inactive."
            )
        }
        return modelled(
            if (directApplies && c.attackerTerrainApplicability == HnsTerrainApplicability.AFFECTED)
                "grassy_terrain_grounded_attacker_grass_move" else "grassy_terrain_no_unmodelled_consequence",
            "src/battle_util.c:6639",
            if (directApplies)
                "Grassy Terrain checks the attacker's authoritative terrain-applicability result before applying the Grass boost."
            else
                "The move is non-Grass, or the attacker is proven not terrain-affected; the direct Grassy modifier is inactive and no supported Grass Pelt consequence applies."
        )
    }

    private fun gravityTerrainModifierIsModelled(c: Context): Boolean {
        val field = c.fieldStatuses ?: return false
        val type = c.effectiveMoveType ?: return false
        return when (type) {
            PokemonType.GRASS -> field and HnsFieldStatusData.STATUS_FIELD_GRASSY_TERRAIN != 0 &&
                c.attackerTerrainApplicability == HnsTerrainApplicability.AFFECTED
            PokemonType.ELECTRIC -> field and HnsFieldStatusData.STATUS_FIELD_ELECTRIC_TERRAIN != 0 &&
                c.attackerTerrainApplicability == HnsTerrainApplicability.AFFECTED
            PokemonType.PSYCHIC -> field and HnsFieldStatusData.STATUS_FIELD_PSYCHIC_TERRAIN != 0 &&
                c.attackerTerrainApplicability == HnsTerrainApplicability.AFFECTED
            PokemonType.DRAGON -> field and HnsFieldStatusData.STATUS_FIELD_MISTY_TERRAIN != 0 &&
                c.defenderTerrainApplicability == HnsTerrainApplicability.AFFECTED
            else -> false
        }
    }

    private const val ITEM_NONE = 0

    private fun liveAbility(input: CalcPokemonInput): Int? =
        input.abilityId.takeIf { input.origin == CalcInputOrigin.LIVE_READ && CalcInputField.ABILITY !in input.unknownFields }

    private fun liveItem(input: CalcPokemonInput): Int? {
        if (input.origin != CalcInputOrigin.LIVE_READ || input.itemProvenance != CalcItemProvenance.BATTLE_EFFECTIVE) {
            return null
        }
        val id = input.itemId ?: return null
        return id.takeIf { !input.itemOutOfDomain && HnsItemRegistry.isInDomain(it) }
    }

    private data class Proof(
        val relevance: HnsFieldRequestRelevance,
        val rule: String,
        val source: String,
        val rationale: String
    )

    private fun proof(rule: String, source: String, rationale: String) =
        Proof(HnsFieldRequestRelevance.PROVEN_IRRELEVANT, rule, source, rationale)

    private fun modelled(rule: String, source: String, rationale: String) =
        Proof(HnsFieldRequestRelevance.MODELLED, rule, source, rationale)

    private fun relevant(rule: String, source: String, rationale: String) =
        Proof(HnsFieldRequestRelevance.RELEVANT, rule, source, rationale)
}
