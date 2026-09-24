package com.dualdex.calculator

import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsFieldStatusData
import com.dualdex.pokemon.hns.HnsItemCategory
import com.dualdex.pokemon.hns.HnsItemRegistry

/** Three-valued request-local field result. Unknown never clears a blocker. */
enum class HnsFieldRequestRelevance { PROVEN_IRRELEVANT, RELEVANT, UNKNOWN }

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
 * Each active bit is assessed independently and cleared only when every operand of a reviewed
 * predicate is authoritative. The rules, their operands and pinned evidence live in
 * `tools/hns-field-status/field_audit.json`; `generate_hns_field_status.py --check` fails when a
 * rule implemented here is not reviewed there (or vice versa), when its evidence lines change, or
 * when a pinned field-status read appears that the audit does not list.
 *
 * Ion Deluge keeps its dedicated handling: when it is relevant (a Normal move) the capability policy
 * refuses with the active dynamic-type limitation, exactly as before this policy existed.
 */
object HnsFieldContextPolicy {

    data class Context(
        /** Single-hit ordinary move with no move/item interaction; null when the move is unknown. */
        val ordinaryMove: Boolean?,
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
        val defenderItemId: Int?
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
        ordinaryMove: Boolean?,
        moveId: Int?
    ): Context {
        val authority = HnsMoveAuthority.forRequest(request, ordinaryMove)
        return Context(
            ordinaryMove = ordinaryMove,
            moveId = moveId,
            preFieldMoveType = authority.preFieldType,
            effectiveMoveType = authority.effectiveType,
            attackerAbilityId = liveAbility(request.attacker),
            defenderAbilityId = liveAbility(request.defender),
            attackerItemId = liveItem(request.attacker),
            defenderItemId = liveItem(request.defender)
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
            else -> if (c == null || c.ordinaryMove != true) null else ordinary(status, c)
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
                else if (items.all { it == ITEM_NONE || HnsItemRegistry.classify(it).category ==
                        HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT }
                ) proof(
                    rule = "magic_room_held_items_neutral",
                    source = "src/battle_util.c:5827",
                    rationale = "Magic Room only turns hold effects off; both live items are absent or have a hold " +
                        "effect audited to never reach ordinary damage, and no H&S item is modelled by the engine."
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
                else -> proof(
                    rule = "gravity_non_ground_unbanned_move",
                    source = "src/battle_util.c:8379",
                    rationale = "Gravity changes only groundedness (read for Ground moves and terrain), move legality " +
                        "and accuracy; this ${type.displayName} move is not gravityBanned."
                )
            }
            HnsFieldStatus.GRASSY_TERRAIN -> when {
                attacker == HnsFieldStatusData.ABILITY_ANALYTIC -> relevant(
                    rule = "grassy_terrain_attacker_analytic",
                    source = "src/battle_main.c:5044",
                    rationale = "Grassy Glide's terrain priority can change the turn order the attacker's Analytic reads."
                )
                defender == HnsFieldStatusData.ABILITY_GRASS_PELT -> relevant(
                    rule = "grassy_terrain_grass_pelt_defender",
                    source = "src/battle_util.c:7296",
                    rationale = "The defender's Grass Pelt raises its Defense in Grassy Terrain."
                )
                type == PokemonType.GRASS -> relevant(
                    rule = "grassy_terrain_grass_move",
                    source = "src/battle_util.c:6639",
                    rationale = "Grassy Terrain boosts a grounded attacker's Grass move."
                )
                type == null || attacker == null || defender == null -> null
                else -> proof(
                    rule = "grassy_terrain_non_grass_move",
                    source = "src/battle_util.c:6639",
                    rationale = "Grassy Terrain boosts only Grass moves in an ordinary hit; neither Grass Pelt nor " +
                        "Analytic is present."
                )
            }
            HnsFieldStatus.MISTY_TERRAIN -> typeRule(
                type, PokemonType.DRAGON,
                irrelevant = "misty_terrain_non_dragon_move" to "src/battle_util.c:6641",
                matching = "misty_terrain_dragon_move" to "src/battle_util.c:6641",
                what = "Misty Terrain weakens only Dragon moves against a grounded target"
            )
            HnsFieldStatus.ELECTRIC_TERRAIN -> when {
                attacker == HnsFieldStatusData.ABILITY_QUARK_DRIVE || attacker == HnsFieldStatusData.ABILITY_HADRON_ENGINE ||
                    defender == HnsFieldStatusData.ABILITY_QUARK_DRIVE -> relevant(
                    rule = "electric_terrain_paradox_ability",
                    source = "src/battle_util.c:7098",
                    rationale = "Quark Drive / Hadron Engine read Electric Terrain in the stat modifiers of this hit."
                )
                attacker == HnsFieldStatusData.ABILITY_ANALYTIC -> relevant(
                    rule = "electric_terrain_attacker_analytic",
                    source = "src/battle_main.c:4959",
                    rationale = "Surge Surfer / Quark Drive speed can change the turn order the attacker's Analytic reads."
                )
                type == PokemonType.ELECTRIC -> relevant(
                    rule = "electric_terrain_electric_move",
                    source = "src/battle_util.c:6643",
                    rationale = "Electric Terrain boosts a grounded attacker's Electric move."
                )
                type == null || attacker == null || defender == null -> null
                else -> proof(
                    rule = "electric_terrain_non_electric_move",
                    source = "src/battle_util.c:6643",
                    rationale = "Electric Terrain boosts only Electric moves in an ordinary hit; no Quark Drive, Hadron " +
                        "Engine or Analytic is present."
                )
            }
            HnsFieldStatus.PSYCHIC_TERRAIN -> when {
                type == PokemonType.PSYCHIC -> relevant(
                    rule = "psychic_terrain_psychic_move",
                    source = "src/battle_util.c:6645",
                    rationale = "Psychic Terrain boosts a grounded attacker's Psychic move."
                )
                c.moveId == null -> null
                c.moveId in HnsFieldStatusData.positivePriorityOrdinaryMoveIds -> relevant(
                    rule = "psychic_terrain_priority_move",
                    source = "src/battle_util.c:2394",
                    rationale = "Psychic Terrain makes a positive-priority move fail against a grounded target."
                )
                type == null || attacker == null || attacker == HnsFieldStatusData.ABILITY_GALE_WINGS -> null
                else -> proof(
                    rule = "psychic_terrain_non_psychic_non_priority_move",
                    source = "src/battle_util.c:6645",
                    rationale = "The move is not Psychic and cannot gain priority (pinned priority <= 0, not a healing " +
                        "move, no Gale Wings), so Psychic Terrain neither boosts nor blocks it."
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

    private fun relevant(rule: String, source: String, rationale: String) =
        Proof(HnsFieldRequestRelevance.RELEVANT, rule, source, rationale)
}
