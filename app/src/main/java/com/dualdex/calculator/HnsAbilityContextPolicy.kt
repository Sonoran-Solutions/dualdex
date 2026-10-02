package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsGroupDLayout
import com.dualdex.pokemon.hns.HnsAbilityAuditData
import com.dualdex.pokemon.hns.HnsAbilityCategory
import com.dualdex.pokemon.hns.HnsItemRegistry
import com.dualdex.pokemon.hns.HnsFieldStatusData

/** Which request participant owns an effective live ability. */
enum class HnsAbilitySide { ATTACKER, DEFENDER }

/** Three-valued request-local ability result. Unknown never clears a blocker. */
enum class HnsAbilityRequestRelevance { PROVEN_IRRELEVANT, RELEVANT, UNKNOWN }

/**
 * Auditable outcome for one globally unsupported or unresolved effective ability.
 *
 * A PROVEN_IRRELEVANT result removes only this ability's limitation. Every other request
 * limitation is collected independently by CalcCapabilityPolicy.
 */
data class HnsAbilityRequestDecision(
    val abilityId: Int?,
    val abilityName: String,
    val side: HnsAbilitySide,
    val globalCategory: HnsAbilityCategory,
    val relevance: HnsAbilityRequestRelevance,
    val rule: String? = null,
    val source: String? = null,
    val rationale: String
)

/**
 * Source-backed request-local relevance rules for globally unsupported H&S abilities.
 *
 * The registry remains the global capability authority. This policy classifies the ability's
 * request-local relevance from authoritative operands, most of which are rebound by
 * CalcRequestBoundary from exact live battler observations. A proven irrelevant effect is cleared;
 * a complete relevant decision can be named as a caveat by the outer capability policy. Anything
 * absent or uncertain returns UNKNOWN and remains a hard refusal.
 */
object HnsAbilityContextPolicy {
    private data class MoveFlagAbilityRule(
        val moveFlag: String,
        val abilitySource: String,
        val moveFlagSource: String,
        val positiveRule: String,
        val negativeRule: String,
        val abilityName: String,
        val defenderRule: String
    )

    /**
     * The exact pinned SPECIES_TERAPAGOS_TERASTAL, generated from the pinned species header by
     * tools/hns-abilities/generate_hns_ability_audit.py (source-check fails on any drift).
     */
    const val TERAPAGOS_TERASTAL_SPECIES_ID = HnsAbilityAuditData.TERAPAGOS_TERASTAL_SPECIES_ID
    private const val HNS_STATUS1_DEFINED_MASK = 0x1fff
    private const val HNS_STATUS1_ANY_MASK = 0x10ff // pinned STATUS1_ANY, include/constants/battle.h:163
    private const val HNS_STATUS1_BURN_MASK = 0x10 // pinned STATUS1_BURN, include/constants/battle.h:155
    private const val HNS_STATUS1_POISON_ANY_MASK = 0x88 // STATUS1_POISON | STATUS1_TOXIC_POISON
    private const val HNS_STATUS1_TOXIC_POISON_MASK = 0x80
    private const val HNS_STATUS1_TOXIC_COUNTER_MASK = 0x0f00
    private val MODELLED_CONDITIONAL_DAMAGE_ABILITY_IDS = setOf(
        18, 55, 62, 79, 112, 148, 198, 255, 281, 282, 284, 285, 286, 287, 293,
        3, 22, 80, 83, 86, 88, 128, 133, 141, 153, 154, 155, 172, 192, 195, 201,
        220, 224, 234, 235, 243, 264, 265, 270, 271, 275, 290,
        33, 34,
        2, 45, 70, 117, 245, 226, 227, 228, 229, 269, 13, 76, 16, 36, 222, 223, 250,
        186, 187, 188, // state-backed Group D operands
        94, 129, 169, 179, 262, 263, 276, 288, 289, // Group D stat stages and field-backed stat modifiers
        89, 91, 96, 97, 101, 110, 111, 116, 136, 137, 138, 173, 174, 178, 182, 184,
        63, 122, 125, 181, 218,
        85, 199, 200, 204, 206, 231, 232, 233, 244, 246, 252, 292
        // Normalize / -ate / Liquid Voice, Group D move-type and base-power stage
    )
    private val STATE_BACKED_GROUP_D_ABILITY_IDS = setOf(
        18, 79, 112, 148, 198, 255, 281, 282, 293, 186, 187, 188, 284, 285, 286, 287
    )

    fun hasModelledConditionalDamageContext(abilityId: Int?): Boolean =
        abilityId != null && abilityId in MODELLED_CONDITIONAL_DAMAGE_ABILITY_IDS

    private fun stateBackedGroupDProof(abilityId: Int, c: Context): Proof? {
        val live = c.liveBattleState ?: return null
        if (c.ordinaryMove != true || c.observedBattlersCount != 2 ||
            !c.attackerAbilityObserved || !c.defenderAbilityObserved) return null
        val attackerRole = c.side == HnsAbilitySide.ATTACKER
        val moveType = effectiveMoveType(c)
        val category = c.moveCategory
        return when (abilityId) {
            18 -> when {
                !attackerRole -> proof("flash_fire_defender_immunity_only", "src/battle_util.c:7007; src/battle_util.c:2546",
                    "Defender Flash Fire is handled by the existing immunity path; only the attacker reads flashFireBoosted for this stat modifier.")
                moveType == null -> null
                moveType != PokemonType.FIRE -> proof("flash_fire_nonfire_move", "src/battle_util.c:7007",
                    "The authoritative final move type is not Fire, so the attacker boost is inactive.")
                live.attackerFlashFireBoosted == null -> null
                live.attackerFlashFireBoosted -> relevant("flash_fire_active_attacker_boost", "src/battle_util.c:7007",
                    "The final type is Fire and the live attacker flashFireBoosted payload is true.")
                else -> proof("flash_fire_unboosted_attacker", "src/battle_util.c:7007",
                    "The live attacker flashFireBoosted payload is false.")
            }
            112 -> when {
                !attackerRole || category == MoveCategory.SPECIAL -> proof("slow_start_irrelevant_role_or_category", "src/battle_util.c:6995",
                    "Slow Start only modifies its holder's Attack for Physical moves.")
                category == null || live.attackerSlowStartTimer !in 0..7 -> null
                live.attackerSlowStartTimer?.let { it > 0 } == true -> relevant("slow_start_positive_timer", "src/battle_util.c:6995",
                    "A positive live slowStartTimer applies the pinned half-down 0.5 Attack factor.")
                else -> proof("slow_start_zero_timer", "src/battle_util.c:6995",
                    "The observed slowStartTimer is zero, so the Physical Attack modifier is inactive.")
            }
            198 -> when {
                !attackerRole -> proof("stakeout_defender_role_irrelevant", "src/battle_util.c:7050",
                    "Stakeout is read only from the attacker's ability slot.")
                live.defenderIsFirstTurn !in 0..3 -> null
                live.defenderIsFirstTurn == 2 -> relevant("stakeout_raw_first_turn_two", "src/battle_util.c:7050",
                    "The source predicate is the raw defender isFirstTurn value exactly equal to 2.")
                else -> proof("stakeout_raw_first_turn_other", "src/battle_util.c:7050",
                    "The observed raw defender isFirstTurn value is not 2.")
            }
            79 -> when {
                !attackerRole -> proof("rivalry_defender_role_irrelevant", "src/battle_util.c:6684",
                    "Rivalry is read only from the attacker ability slot.")
                moveType == null -> null
                live.attackerGender == com.dualdex.pokemon.hns.HnsBattlerGender.UNKNOWN ||
                    live.defenderGender == com.dualdex.pokemon.hns.HnsBattlerGender.UNKNOWN -> null
                live.attackerGender == com.dualdex.pokemon.hns.HnsBattlerGender.GENDERLESS ||
                    live.defenderGender == com.dualdex.pokemon.hns.HnsBattlerGender.GENDERLESS -> proof("rivalry_genderless_neutral", "src/battle_util.c:6684",
                    "Pinned same/opposite gender helpers exclude genderless battlers, leaving Rivalry neutral.")
                live.attackerGender == live.defenderGender -> relevant("rivalry_same_gender", "src/battle_util.c:6684",
                    "Both genders are authoritative and equal; source composes UQ_4_12(1.25) into base power.")
                else -> relevant("rivalry_opposite_gender", "src/battle_util.c:6684",
                    "Both genders are authoritative and opposite; source composes UQ_4_12(0.75) into base power.")
            }
            293 -> when {
                !attackerRole -> proof("supreme_overlord_defender_role_irrelevant", "src/battle_util.c:6747; src/battle_util.c:2308",
                    "Supreme Overlord reads the stored attacker battler counter only.")
                live.attackerSupremeOverlordCounter?.let { it in 0..5 } != true -> null
                live.attackerSupremeOverlordCounter == 0 -> proof("supreme_overlord_zero_counter", "src/battle_util.c:2308",
                    "The observed stored counter is zero, producing the exact neutral UQ4.12 factor.")
                else -> relevant("supreme_overlord_stored_counter", "src/battle_util.c:2308",
                    "The observed stored BattleStruct counter is in 1..5 and is used directly.")
            }
            148 -> when {
                !attackerRole -> proof("analytic_defender_role_irrelevant", "src/battle_util.c:6691",
                    "Analytic is read only from the attacker ability slot.")
                live.attackerAnalyticTurnOrder == HnsAnalyticTurnOrder.UNKNOWN -> null
                live.attackerAnalyticTurnOrder == HnsAnalyticTurnOrder.LAST_TO_MOVE -> relevant("analytic_phase_proven_last", "src/battle_util.c:1183; src/battle_util.c:6691",
                    "The native reader published LAST_TO_MOVE only after its current-action phase and turn-order consistency checks.")
                else -> proof("analytic_phase_proven_not_last", "src/battle_util.c:1183; src/battle_util.c:6691",
                    "The native reader proved a later living use-move action in the current turn.")
            }
            255 -> when {
                !attackerRole || category == MoveCategory.SPECIAL -> proof("gorilla_tactics_irrelevant_role_or_category", "src/battle_util.c:7075",
                    "Gorilla Tactics only modifies the attacker's Physical Attack branch.")
                category == null || live.attackerSelectedGimmick == null ||
                    live.attackerSelectedGimmick !in 0 until com.dualdex.pokemon.hns.HnsGroupDLayout.GIMMICKS_COUNT ||
                    live.attackerGimmick !in 0 until com.dualdex.pokemon.hns.HnsGroupDLayout.GIMMICKS_COUNT -> null
                live.attackerSelectedGimmick == 4 || live.attackerGimmick == 4 -> proof("gorilla_tactics_gimmick_inactive_modifier", "src/battle_util.c:7075",
                    "A selected or active Dynamax disables this ability modifier; its independent unsupported gimmick blocker remains in force.")
                else -> relevant("gorilla_tactics_physical_no_dynamax", "src/battle_util.c:7075",
                    "The selected and active gimmick authorities are observed; neither is Dynamax for this Physical hit.")
            }
            281, 282 -> paradoxProof(abilityId, c, live, attackerRole)
            186, 187, 188 -> auraProof(abilityId, c, live)
            284, 285, 286, 287 -> when (HnsRuinAuthority.modifies(abilityId, c)) {
                true -> relevant("ruin_live_field_stat_modifier", "src/battle_util.c:6874; src/battle_util.c:7156; src/battle_util.c:7344",
                    "The live Ruin payload survives the source suppression checks and modifies the selected stat; the subject's own flag is clear.")
                false -> proof("ruin_inactive_or_self_excluded", "src/battle_util.c:6874; src/battle_util.c:7156; src/battle_util.c:7344",
                    "The selected stat, holder self-exclusion, or observed field payload proves this Ruin modifier inactive.")
                null -> null
            }
            else -> null
        }
    }

    private fun paradoxProof(
        abilityId: Int,
        c: Context,
        live: CalcHnsLiveBattleState,
        attackerRole: Boolean
    ): Proof? {
        val selected = if (attackerRole) live.attackerParadoxBoostedStat else live.defenderParadoxBoostedStat
        val transformed = if (attackerRole) live.attackerTransformed else live.defenderTransformed
        val booster = if (attackerRole) live.attackerBoosterEnergyActivated else live.defenderBoosterEnergyActivated
        val subjectAbility = if (attackerRole) c.attackerAbilityId else c.defenderAbilityId
        if (subjectAbility != abilityId) return proof("paradox_other_battler", "src/battle_util.c:7081-7105; src/battle_util.c:7307-7324",
            "This ability ID is not the battler role whose Attack/Defense stat is selected for this hit.")
        if (c.moveCategory == null || transformed == null || booster == null ||
            selected == null || selected !in 0 until HnsGroupDLayout.NUM_STATS) return null
        if (transformed) return proof("paradox_transformed_inactive", "src/battle_util.c:7081-7105; src/battle_util.c:7307-7324",
            "The source excludes transformed battlers from the Paradox stat modifier.")
        val active = if (booster) true else if (abilityId == 282) {
            live.fieldStatuses?.let { it and HnsFieldStatusData.STATUS_FIELD_ELECTRIC_TERRAIN != 0 }
        } else {
            if (!live.weatherObserved) null
            else if (live.weatherWord and com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN == 0) false
            else HnsFieldAbilityAuthority(c).weatherEffective()
        }
        if (active == null) return null
        if (!active) return proof("paradox_activation_inactive", "src/battle_util.c:7081-7105; src/battle_util.c:7307-7324",
            "Neither the source activation field nor the observed Booster Energy activation payload is active.")
        val expected = when {
            attackerRole && c.moveCategory == MoveCategory.PHYSICAL -> "ATK"
            attackerRole && c.moveCategory == MoveCategory.SPECIAL -> "SPATK"
            !attackerRole && c.moveCategory == MoveCategory.PHYSICAL -> "DEF"
            else -> "SPDEF"
        }
        val rawStat = if (attackerRole) live.attackerRawStats else live.defenderRawStats
        val stages = if (attackerRole) live.attackerStatStages else live.defenderStatStages
        if (selected == 0 && live.fieldStatuses == null) return null
        val exactStat = when (selected) {
            0 -> paradoxHighestStat(rawStat, stages, live.fieldStatuses?.and(HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM) != 0)
            1 -> "ATK"
            2 -> "DEF"
            3 -> "SPEED"
            4 -> "SPATK"
            5 -> "SPDEF"
            else -> null
        } ?: return null
        if (exactStat != expected) return proof("paradox_other_highest_stat", "src/battle_util.c:5224; src/battle_util.c:7081-7105; src/battle_util.c:7307-7324",
            "The authoritative selected boosted stat does not match the current hit's source category/role predicate.")
        return relevant("paradox_selected_stat_boost", "src/battle_util.c:5224; src/battle_util.c:7081-7105; src/battle_util.c:7307-7324",
            "The active ability's authoritative selected boosted stat matches the category-specific Attack/Defense stage branch.")
    }

    private fun paradoxHighestStat(raw: CalcRawStats?, stages: List<Int>?, wonderRoom: Boolean): String? {
        if (raw == null || !validStages(stages)) return null
        val stats = listOf("ATK" to raw.attack, "DEF" to if (wonderRoom) raw.spDefense else raw.defense,
            "SPATK" to raw.spAttack, "SPDEF" to if (wonderRoom) raw.defense else raw.spDefense, "SPEED" to raw.speed)
        val indices = listOf(1, 2, 4, 5, 3)
        var winner: String? = null
        var best = Int.MIN_VALUE
        for ((i, stat) in stats.withIndex()) {
            val ratio = STAT_STAGE_RATIOS[stages!![indices[i]] + 6]
            val value = stat.second * ratio.first / ratio.second
            if (value > best) { best = value; winner = stat.first }
        }
        return winner
    }

    private fun auraProof(abilityId: Int, c: Context, live: CalcHnsLiveBattleState): Proof? {
        val type = effectiveMoveType(c) ?: return null
        val auraId = when (type) { PokemonType.DARK -> 186; PokemonType.FAIRY -> 187; else -> null }
        if (auraId == null || abilityId != 188 && abilityId != auraId) return proof(
            "aura_wrong_type_or_no_matching_aura", "src/battle_util.c:6754-6761",
            "The authoritative final type does not match this aura's field branch.")
        val authority = HnsFieldAbilityAuthority(c)
        val matching = authority.present(auraId) ?: return null
        if (!matching) return proof("aura_no_matching_field_ability", "src/battle_util.c:6754-6761",
            "Neither effective live participant ability supplies a matching aura.")
        authority.present(188) ?: return null
        return relevant("aura_matching_live_singles_aura", "src/battle_util.c:6754-6761",
            "The shared Singles field authority proves the matching aura and the Aura Break predicate.")
    }

    private val STAT_STAGE_RATIOS = listOf(
        10 to 40, 10 to 35, 10 to 30, 10 to 25, 10 to 20, 10 to 15, 10 to 10,
        15 to 10, 20 to 10, 25 to 10, 30 to 10, 35 to 10, 40 to 10
    ) // pinned src/pokemon.c:gStatStageRatios

    data class Context(
        val side: HnsAbilitySide,
        val ordinaryMove: Boolean?,
        val isCrit: Boolean?,
        val attackerAbilityId: Int?,
        val moveType: PokemonType?,
        val moveCategory: MoveCategory?,
        val attackerTypes: Set<PokemonType>?,
        val defenderSpeciesId: Int?,
        val attackerSpeciesId: Int? = null,
        val defenderHp: Int?,
        val defenderMaxHp: Int?,
        val attackerStatus1: Int?,
        val defenderStatus1: Int? = null,
        val moveId: Int? = null,
        val moveBasePower: Int? = null,
        val moveAbilityFlags: Set<String>? = null,
        val unknownMoveAbilityFlags: Set<String>? = null,
        val sheerForceAffected: Boolean? = null,
        /** Literal pinned MoveInfo.soundMove; null means source metadata is unknown. */
        val soundMove: Boolean? = null,
        val observedBattlersCount: Int?,
        val dynamicMoveTypeKnownNeutral: Boolean,
        val defenderItemId: Int?,
        val defenderTypes: Set<PokemonType>? = null,
        val attackerStatStages: List<Int>? = null,
        val defenderStatStages: List<Int>? = null,
        val attackerAbilityObserved: Boolean = false,
        val defenderAbilityObserved: Boolean = false,
        val attackerHp: Int? = null,
        val attackerMaxHp: Int? = null,
        val defenderAbilityId: Int? = null,
        val attackerItemId: Int? = null,
        /** Exact H&S chart result after live effective types and H&S grounding-item rules. */
        val typeEffectiveness: Double? = null,
        val weatherWord: Int? = null,
        val weatherObserved: Boolean = false,
        val fieldStatuses: Int? = null,
        val attackerTerrainApplicability: HnsTerrainApplicability? = null,
        val defenderTerrainApplicability: HnsTerrainApplicability? = null,
        /** Null/false while the authoritative switch-in/event driver is unread or still pending. */
        val switchInEventsSettled: Boolean? = null,
        /** The single pinned effective-type decision shared by policy and engine serialization. */
        val moveAuthority: HnsMoveAuthority? = null,
        /** All additional operands were rebound from exact live H&S observations at the boundary. */
        val liveBattleState: CalcHnsLiveBattleState? = null,
        val attackerHoldEffectResolution: HnsHoldEffectResolution? = null,
        val defenderHoldEffectResolution: HnsHoldEffectResolution? = null,
        val defenderAbilityShieldActiveIgnoringAbility: Boolean? = null,
        /** Shared request-local resist-berry decision also serialized to the damage engine. */
        val resistBerryDecision: HnsResistBerryDecision? = null
    )

    /** Abilities whose only damage-relevant effect is already reflected in live stat stages. */
    private val LIVE_STAT_STAGE_WRITER_IDS = setOf(
        3, 22, 80, 83, 86, 88, 128, 133, 141, 153, 154, 155, 172, 192, 195, 201,
        220, 224, 234, 235, 243, 264, 265, 270, 271, 275, 290
    )
    private val SPEED_STAGE_WRITER_IDS = setOf(3, 80, 86, 133, 141, 155, 224, 243, 271, 290)

    private val LIVE_WEATHER_SETTER_IDS = setOf(2, 45, 70, 117, 245) // Drizzle / Sand Stream / Drought / Snow Warning / Sand Spit
    private val SWITCH_IN_WEATHER_SETTER_IDS = setOf(2, 45, 70, 117)
    private val LIVE_TERRAIN_SETTER_IDS = setOf(226, 227, 228, 229, 269)
    private val SWITCH_IN_TERRAIN_SETTER_IDS = setOf(226, 227, 228, 229)
    private val LIVE_TYPE_REWRITER_IDS = setOf(16, 168, 236, 250) // Color Change / Protean / Libero / Mimicry
    private val MOVE_TIME_TYPE_REWRITER_IDS = setOf(168, 236) // Protean / Libero
    private val LIVE_ABILITY_REWRITER_IDS = setOf(36, 222, 223) // Trace / Receiver / Power of Alchemy
    private val WEATHER_SUPPRESSOR_IDS = setOf(13, 76) // Cloud Nine / Air Lock
    fun assess(abilityId: Int, context: Context?): HnsAbilityRequestDecision {
        val entry = com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(abilityId)
        val side = context?.side ?: HnsAbilitySide.ATTACKER
        if (abilityId == 26 && context != null) {
            attackerLevitateTerrainProof(context)?.let { proof ->
                return decision(
                    entry.abilityId ?: abilityId,
                    entry.titleCaseName,
                    side,
                    entry.category,
                    proof.relevance,
                    proof.rule,
                    proof.source,
                    proof.rationale
                )
            }
        }
        if (entry.category != HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT &&
            !(entry.category == HnsAbilityCategory.MODELLED_HNS_CONDITIONAL &&
                abilityId in MODELLED_CONDITIONAL_DAMAGE_ABILITY_IDS)
        ) {
            return decision(
                entry.abilityId ?: abilityId,
                entry.titleCaseName,
                side,
                entry.category,
                HnsAbilityRequestRelevance.UNKNOWN,
            rationale = "No reviewed request-local rule applies to this ability classification."
            )
        }

        val c = context ?: return unknown(entry.abilityId ?: abilityId, entry.titleCaseName, side, entry.category)
        val proof: Proof? = when (abilityId) {
            in LIVE_STAT_STAGE_WRITER_IDS -> if (
                c.switchInEventsSettled == true &&
                c.attackerAbilityObserved && c.defenderAbilityObserved &&
                validStages(c.attackerStatStages) && validStages(c.defenderStatStages)
            ) {
                proof(
                    "live_stat_stages_capture_stage_writer", statWriterSource(abilityId),
                    "This ability changes only battle stat stages; both active battlers' exact live stages are supplied to the damage engine."
                )
            } else null
            in LIVE_WEATHER_SETTER_IDS -> if (
                (abilityId !in SWITCH_IN_WEATHER_SETTER_IDS || c.switchInEventsSettled == true) &&
                observedWeatherStateProven(c)
            ) proof(
                "live_weather_setter_state_observed", weatherSetterSource(abilityId),
                "This setter's weather write is completed before the selected hit (or, for Sand Spit, after the hit that triggers it); authoritative live weather is the input for supported arithmetic and retains any independent unsupported-weather refusal."
            ) else null
            in LIVE_TERRAIN_SETTER_IDS -> if (
                (abilityId !in SWITCH_IN_TERRAIN_SETTER_IDS || c.switchInEventsSettled == true) &&
                c.fieldStatuses != null &&
                c.attackerAbilityObserved && c.defenderAbilityObserved
            ) proof(
                "live_terrain_setter_state_observed", terrainSetterSource(abilityId),
                "The completed terrain write is represented by the authoritative gFieldStatuses word consumed by the terrain applicability and damage authorities; a triggering Seed Sower hit precedes its terrain write."
            ) else null
            13, 76 -> if (
                abilityObserved(c) &&
                c.attackerAbilityObserved && c.defenderAbilityObserved &&
                c.attackerHp != null && c.defenderHp != null &&
                c.weatherObserved && c.weatherWord != null
            ) proof(
                "effective_weather_suppressed_by_live_ability", "src/battle_util.c:10053-10071",
                "HasWeatherEffect suppresses the live weather for Cloud Nine/Air Lock, and the production boundary applies that effective weather to the selected-hit field input; unsupported weather retains its independent weather refusal."
            ) else null
            in LIVE_TYPE_REWRITER_IDS -> if (
                c.switchInEventsSettled == true && c.ordinaryMove == true &&
                abilityObserved(c) &&
                c.dynamicMoveTypeKnownNeutral && c.moveType != null && liveTypesForSide(c) != null
            ) {
                val liveTypes = liveTypesForSide(c)
                if (abilityId in MOVE_TIME_TYPE_REWRITER_IDS && liveTypes != setOf(c.moveType)) {
                    // Protean/Libero execute before this move's damage and may change the type
                    // after the current battle-state snapshot. `usedProteanLibero` is not in the
                    // runtime tuple, so only the already-monotyped same-type case proves that no
                    // pending transform can change this hit's STAB/type effectiveness.
                    null
                } else proof(
                    "live_effective_types_capture_type_rewriter", typeRewriterSource(abilityId),
                    if (abilityId in MOVE_TIME_TYPE_REWRITER_IDS) {
                        "Protean/Libero cannot change this hit when the observed battler is already monotyped to the move's effective type; that exact live type is passed to the damage engine."
                    } else {
                        "The ability's type change is already the active battler's observed effective type, which is passed to the damage engine."
                    }
                )
            } else null
            in LIVE_ABILITY_REWRITER_IDS -> if (
                c.switchInEventsSettled == true && c.ordinaryMove == true && abilityObserved(c)
            ) proof(
                "live_effective_ability_capture_ability_rewriter", abilityRewriterSource(abilityId),
                "The active battler's effective runtime ability ID is authoritative; a copied/replaced ability is evaluated under that current ID."
            ) else null
            105 -> if (c.ordinaryMove == true && c.isCrit != null) proof(
                "fixed_crit_stage_only", "src/battle_util.c:8049",
                "Super Luck changes only critical-hit odds; this hit's critical flag is fixed."
            ) else null
            97 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "sniper_defender_side", "src/battle_util.c:7566",
                    "Sniper is read only from the attacking ability slot."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != abilityId || c.isCrit == null ->
                    unknownProof(
                        "sniper_crit_unknown", "src/battle_util.c:7567",
                        "Sniper requires an authoritative ordinary move, live attacker ability, and fixed critical flag."
                    )
                !c.isCrit -> proof(
                    "sniper_noncritical_hit", "src/battle_util.c:7566",
                    "The selected hit is not critical, so Sniper is inactive."
                )
                else -> relevant(
                    "sniper_critical_hit", "src/battle_util.c:7567",
                    "The selected critical hit receives Sniper's additional 1.5 final attacker modifier after the ordinary critical stage."
                )
            }
            24, 64, 106, 124, 152, 160, 215, 221, 238, 254, 268 ->
                if (c.ordinaryMove == true) proof(
                    "after_hit_ability_outside_single_hit", afterHitSource(abilityId),
                    "The pinned effect executes after damage or on a nonordinary draining move; it cannot change this hit's rolls."
                ) else null
            139, 167, 291 -> if (c.ordinaryMove == true) proof(
                "berry_recovery_outside_single_hit", berrySource(abilityId),
                "The berry recovery or reuse acts after the current hit, using the already observed item state."
            ) else null
            103 -> when {
                c.ordinaryMove != true || !abilityObserved(c) -> null
                (if (c.side == HnsAbilitySide.ATTACKER) c.attackerItemId else c.defenderItemId) == null ->
                    unknownProof(
                        "klutz_item_identity_unobserved", "src/battle_util.c:5829",
                        "The exact current item is needed to keep Klutz's hold-effect suppression aligned with the shared item policy."
                    )
                (if (c.side == HnsAbilitySide.ATTACKER) c.attackerItemId else c.defenderItemId) == 0 -> proof(
                    "klutz_no_current_item", "src/battle_util.c:5829",
                    "The authoritative current item is ITEM_NONE, so Klutz has no selected-hit item consequence."
                )
                (if (c.side == HnsAbilitySide.ATTACKER) c.attackerHoldEffectResolution else c.defenderHoldEffectResolution)
                    ?.state == HnsHoldEffectState.SUPPRESSED_NONE -> proof(
                    "klutz_item_suppression_shared", "src/battle_util.c:5829",
                    "Klutz resolves the held effect to NONE through the shared authority; the item's own request-local rule handles the resulting damage consequence."
                )
                (if (c.side == HnsAbilitySide.ATTACKER) c.attackerHoldEffectResolution else c.defenderHoldEffectResolution)
                    ?.state == HnsHoldEffectState.ACTIVE_EXACT -> proof(
                    "klutz_suppressed_by_gastro_acid", "src/battle_util.c:5829",
                    "The item hold effect is active, so Klutz is suppressed (for example by Gastro Acid); independent ability-suppression and item rules retain their own checks."
                )
                else -> unknownProof(
                    "klutz_hold_effect_unobserved", "src/battle_util.c:5829",
                    "The shared effective-hold-effect authority cannot determine whether Klutz disables the current item."
                )
            }
            127, 266, 267 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "unnerve_defender_side", "src/battle_util.c:336-372",
                    "Unnerve and As One block opposing berry consumption; a defender-side copy cannot block the incoming hit's defender-held resist berry."
                )
                c.ordinaryMove != true -> null
                c.defenderItemId == null -> unknownProof(
                    "unnerve_berry_authority_unobserved", "src/battle_util.c:336-372, 7686-7695",
                    "The opposing defender's exact current item must be observed before the berry block can be cleared."
                )
                c.resistBerryDecision?.itemId != c.defenderItemId -> unknownProof(
                    "unnerve_berry_authority_unobserved", "src/battle_util.c:336-372, 7686-7695",
                    "The shared berry decision is not bound to the authoritative defender item identity."
                )
                c.resistBerryDecision?.state == HnsResistBerryState.UNKNOWN -> unknownProof(
                    "unnerve_berry_authority_unobserved", "src/battle_util.c:336-372, 7686-7695",
                    c.resistBerryDecision?.rationale ?: "The shared berry decision is unavailable."
                )
                c.resistBerryDecision?.state == HnsResistBerryState.BLOCKED_BY_UNNERVE -> proof(
                    "unnerve_resist_berry_block_modelled", "src/battle_util.c:336-372, 7686-7695",
                    "The exact active opposing Unnerve/As One ability is represented by the shared neutral resist-berry decision; its post-hit stat-boost trigger cannot change this hit."
                )
                else -> proof(
                    "unnerve_without_current_berry_modifier", "src/battle_util.c:336-372, 7686-7695",
                    "The current ordinary hit has no resist-berry modifier for this ability to block; post-hit stat-boost triggers are outside these rolls."
                )
            }
            247 -> when {
                c.ordinaryMove != true -> null
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    "attacker_ripen_no_current_hit_modifier", "src/battle_util.c:7695, src/battle_util.c:10558",
                    "Ripen's current-hit damage branch reads only the defender ability; its attacker-side Micle branch changes accuracy."
                )
                c.defenderItemId == null || c.resistBerryDecision?.itemId != c.defenderItemId ->
                    unknownProof("ripen_berry_authority_unobserved", "src/battle_util.c:7695",
                        "Ripen is cleared only when the shared live resist-berry decision is bound to the exact defender item.")
                c.resistBerryDecision?.state == HnsResistBerryState.UNKNOWN ->
                    unknownProof("ripen_berry_authority_unobserved", "src/battle_util.c:7695",
                        c.resistBerryDecision?.rationale ?: "The shared berry decision is unavailable.")
                else -> proof(
                    if (c.resistBerryDecision?.state == HnsResistBerryState.APPLIES) "ripen_resist_berry_modelled"
                    else "ripen_without_active_resist_berry",
                    "src/battle_util.c:7695",
                    "The shared final-item authority applies the exact Ripen quarter modifier when the berry activates; otherwise Ripen has no current-hit damage effect. Other berry interactions remain outside this rule."
                )
            }
            33, 34 -> when {
                c.ordinaryMove != true -> proof(
                    "speed_ability_unsupported_move_independent", speedSource(abilityId),
                    "This ability changes only speed in a request whose selected move is independently outside the ordinary damage surface; that move remains refused, so its unresolved turn-order mechanics are not a second ability blocker."
                )
                c.attackerAbilityId == null -> null
                c.side == HnsAbilitySide.DEFENDER && c.attackerAbilityId == 148 ->
                    when (c.liveBattleState?.attackerAnalyticTurnOrder) {
                        null, HnsAnalyticTurnOrder.UNKNOWN -> null
                        else -> proof(
                        "speed_ability_analytic_order_authoritative", "src/battle_util.c:6691",
                        "The current-action Analytic turn-order authority already incorporates the defender's live speed; this ability does not add a separate damage modifier."
                    )
                    }
                else -> proof(
                    "speed_ability_without_analytic", speedSource(abilityId),
                    "This effect changes speed, and the attacker has no Analytic dependency."
                )
            }
            84, 95, 146, 202, 259 -> when {
                c.ordinaryMove != true || c.attackerAbilityId == null -> null
                c.side == HnsAbilitySide.DEFENDER && c.attackerAbilityId == 148 -> relevant(
                    "speed_ability_attacker_analytic", "src/battle_util.c:6691",
                    "Changing turn order can change the attacker's Analytic damage modifier."
                )
                else -> proof(
                    "speed_ability_without_analytic", speedSource(abilityId),
                    "This effect changes speed or priority, and the attacker has no Analytic dependency."
                )
            }
            308 -> when (c.side) {
                HnsAbilitySide.ATTACKER -> proof(
                    "attacker_always_irrelevant", "src/battle_util.c:327",
                    "ShouldTeraShellDistortTypeMatchups reads only the defender's ability."
                )
                HnsAbilitySide.DEFENDER -> when (val species = c.defenderSpeciesId) {
                    null -> null
                    TERAPAGOS_TERASTAL_SPECIES_ID -> {
                        val hp = c.defenderHp
                        val maxHp = c.defenderMaxHp
                        when {
                            hp == null || maxHp == null || maxHp <= 0 || hp < 0 -> null
                            hp < maxHp -> proof(
                            "terapagos_below_full_hp", "src/battle_util.c:328",
                                "Tera Shell's distortion requires defender HP to equal maxHP."
                            )
                            hp == maxHp -> relevant(
                                "terapagos_full_hp_relevant", "src/battle_util.c:328",
                                "Full-HP Terapagos-Terastal satisfies Tera Shell's source predicate."
                            )
                            else -> null
                        }
                    }
                    else -> if (species > 0) proof(
                        "defender_not_terapagos_terastal", "src/battle_util.c:327",
                        "Tera Shell's distortion requires SPECIES_TERAPAGOS_TERASTAL."
                    ) else null
                }
            }
            54 -> if (c.side == HnsAbilitySide.DEFENDER) proof(
                "defender_truant_does_not_change_incoming_damage", "src/battle_move_resolution.c:251",
                "CancelerTruant gates only whether its holder executes its own move this turn."
            ) else relevant(
                "attacker_move_execution_state_unobserved", "src/battle_move_resolution.c:251",
                "Attacker Truant depends on truantCounter, which this request does not observe."
            )
            140 -> singlesProof(c, "telepathy_singles_no_partner", "src/battle_util.c:8422",
                "Telepathy zeroes damage only when its holder is the attacker's battle partner.")
            89, 173, 178, 292 -> moveFlagAbilityProof(abilityId, c)
            96, 174, 182, 184, 204, 206 -> moveTypeRewriteAbilityProof(abilityId, c)
            244 -> punkRockProof(c)
            252 -> steelySpiritProof(c)
            101 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "technician_defender_side", "src/battle_util.c:6655",
                    "Technician is checked only for abilityAtk and cannot modify the selected incoming hit."
                )
                c.ordinaryMove != true || c.moveId == null -> null
                c.moveBasePower == null || c.moveBasePower !in 1..255 -> null
                c.moveBasePower <= 60 -> relevant(
                    "technician_attacker_bp_at_most_60", "src/battle_util.c:6655",
                    "The pinned ordinary EFFECT_HIT path preserves source move power before Technician's <=60 check; this move receives the modeled 1.5 base-power modifier."
                )
                else -> proof(
                    "technician_attacker_bp_over_60", "src/battle_util.c:6655",
                    "The authoritative ordinary move power entering the ability stage exceeds 60, so Technician does not modify this hit."
                )
            }
            85 -> when {
                c.ordinaryMove != true -> null
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    "heatproof_attacker_direct_hit_irrelevant", "src/battle_util.c:6785",
                    "CalcMoveBasePowerAfterModifiers switches on abilityDef, so attacker Heatproof has no selected outgoing direct-hit modifier; burn residual damage is outside this result."
                )
                effectiveMoveType(c) == null -> null
                effectiveMoveType(c) == PokemonType.FIRE -> relevant(
                    "heatproof_defender_fire_move", "src/battle_util.c:6787",
                    "A defender Heatproof and authoritative final Fire move use the pinned 0.5 base-power modifier; other burn and residual behavior remains outside this selected-hit result."
                )
                else -> proof(
                    "heatproof_defender_nonfire_move", "src/battle_util.c:6789",
                    "The final authoritative move type is not Fire, so the defender Heatproof base-power branch is inactive."
                )
            }
            94 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "solar_power_defender_side", "src/battle_util.c:6998",
                    "Solar Power is checked only in the attacker ability slot for this selected hit."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != abilityId ||
                    !c.attackerAbilityObserved || !c.defenderAbilityObserved || c.observedBattlersCount != 2 ->
                    unknownProof(
                        "solar_power_live_ability_unknown", "src/battle_util.c:6998",
                        "Solar Power requires an authoritative ordinary move and both active effective abilities."
                    )
                c.moveCategory == null -> unknownProof(
                    "solar_power_category_unknown", "src/battle_util.c:6998",
                    "Solar Power consumes HnsMoveAuthority's final category, not the move's static category."
                )
                c.moveCategory == MoveCategory.PHYSICAL -> proof(
                    "solar_power_physical_move", "src/battle_util.c:6998",
                    "The authoritative final category is Physical; Solar Power's Special-only Attack-stat branch is inactive."
                )
                !c.weatherObserved || c.weatherWord == null -> unknownProof(
                    "solar_power_weather_unknown", "src/battle_util.c:6998, src/battle_util.c:3792",
                    "The live weather word and the prerequisites of IsBattlerWeatherAffected must be authoritative."
                )
                !ordinarySunObserved(c.weatherWord) -> proof(
                    "solar_power_without_ordinary_sun", "src/battle_util.c:6998",
                    "The live weather word proves ordinary Sun is inactive; primal and unsupported weather remain governed by their own blockers."
                )
                c.attackerHp == null || c.attackerHp <= 0 || c.defenderHp == null || c.defenderHp <= 0 -> unknownProof(
                    "solar_power_battler_liveness_unknown", "src/battle_util.c:10053",
                    "HasWeatherEffect ignores fainted battlers; positive live HP is required to prove the active suppression set."
                )
                c.attackerAbilityId in WEATHER_SUPPRESSOR_IDS || c.defenderAbilityId in WEATHER_SUPPRESSOR_IDS -> proof(
                    "solar_power_weather_suppressed", "src/battle_util.c:6999, src/battle_util.c:10053-10069",
                    "Cloud Nine or Air Lock makes HasWeatherEffect false; that suppressor's independent capability rule still applies."
                )
                c.attackerItemId == null || activeUtilityUmbrella(c, HnsAbilitySide.ATTACKER) == null -> unknownProof(
                    "solar_power_attacker_item_unknown", "src/battle_util.c:9530",
                    "IsBattlerWeatherAffected requires the attacker's exact live item to rule out Utility Umbrella."
                )
                activeUtilityUmbrella(c, HnsAbilitySide.ATTACKER) == true -> proof(
                    "solar_power_utility_umbrella", "src/battle_util.c:6999, src/battle_util.c:9527-9531",
                    "The authoritative active Utility Umbrella hold effect shields its holder from Sun; the shared item pipeline models the item's selected-hit consequence."
                )
                else -> relevant(
                    "solar_power_special_move_in_sun", "src/battle_util.c:6998",
                    "The authoritative final category is Special and the live ordinary Sun affects the attacker; Solar Power multiplies the selected Sp. Atk stat. Residual HP loss is outside this selected-hit result."
                )
            }
            262, 263, 276 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "attack_stat_type_ability_defender_side", "src/battle_util.c:7060-7079",
                    "Transistor, Dragon's Maw, and Rocky Payload are read only from CalcAttackStat's attacker ability slot."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != abilityId ||
                    !c.attackerAbilityObserved || !c.defenderAbilityObserved || c.observedBattlersCount != 2 ->
                    unknownProof(
                        "attack_stat_type_ability_live_state_unknown", "src/battle_util.c:7058-7080",
                        "The type-based Attack-stat branch requires an authoritative ordinary move and live effective abilities for both battlers."
                    )
                effectiveMoveType(c) == null -> unknownProof(
                    "attack_stat_type_ability_move_type_unknown", "src/battle_util.c:7060-7080",
                    "The branch consumes HnsMoveAuthority's final effective move type."
                )
                effectiveMoveType(c) != attackStatAbilityType(abilityId) -> proof(
                    "attack_stat_type_ability_nonmatching_type", "src/battle_util.c:7060-7080",
                    "The authoritative final move type does not match this ability's exact CalcAttackStat type predicate."
                )
                else -> relevant(
                    "attack_stat_type_ability_matching_type", "src/battle_util.c:7060-7080",
                    "The authoritative final move type matches this Attack-stat ability's pinned branch."
                )
            }
            288 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "orichalcum_pulse_defender_side", "src/battle_util.c:7105-7107",
                    "Orichalcum Pulse is read only from CalcAttackStat's attacker ability slot."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != abilityId ||
                    !c.attackerAbilityObserved || !c.defenderAbilityObserved || c.observedBattlersCount != 2 ->
                    unknownProof(
                        "orichalcum_pulse_live_state_unknown", "src/battle_util.c:7105-7107",
                        "The branch requires an authoritative ordinary move and live effective abilities for both battlers."
                    )
                c.moveCategory == null -> unknownProof(
                    "orichalcum_pulse_category_unknown", "src/battle_util.c:7105-7106",
                    "Orichalcum Pulse consumes HnsMoveAuthority's final category."
                )
                !c.weatherObserved || c.weatherWord == null -> unknownProof(
                    "orichalcum_pulse_weather_unknown", "src/battle_script_commands.c:1300, src/battle_util.c:7105",
                    "The authoritative live weather word supplied to GetWeather is required; the ability branch reads ctx->weather directly rather than calling IsBattlerWeatherAffected."
                )
                c.attackerItemId == null -> unknownProof(
                    "orichalcum_pulse_attacker_item_unknown", "src/battle_util.c:7106",
                    "The authoritative current attacker item is required to evaluate the Utility Umbrella exclusion."
                )
                c.moveCategory != MoveCategory.PHYSICAL -> proof(
                    "orichalcum_pulse_special_move", "src/battle_util.c:7105-7106",
                    "The authoritative final category is Special; Orichalcum Pulse is Physical-only."
                )
                (c.weatherWord and com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN) == 0 -> proof(
                    "orichalcum_pulse_without_raw_sun", "src/battle_util.c:7105",
                    "The observed weather supplied to GetWeather has no B_WEATHER_SUN bit, so the Attack-stat branch is inactive."
                )
                c.attackerHp == null || c.attackerHp <= 0 || c.defenderHp == null || c.defenderHp <= 0 -> unknownProof(
                    "orichalcum_pulse_battler_liveness_unknown", "src/battle_script_commands.c:1300, src/battle_util.c:10053-10069",
                    "GetWeather calls HasWeatherEffect, which ignores fainted battlers; positive live HP is required to evaluate Cloud Nine/Air Lock suppression."
                )
                c.attackerAbilityId in WEATHER_SUPPRESSOR_IDS || c.defenderAbilityId in WEATHER_SUPPRESSOR_IDS -> proof(
                    "orichalcum_pulse_weather_suppressed", "src/battle_script_commands.c:1300, src/battle_util.c:10053-10069",
                    "GetWeather returns B_WEATHER_NONE when a live Cloud Nine or Air Lock makes HasWeatherEffect false, before CalcAttackStat reads ctx->weather; the suppressor's independent capability rule still applies."
                )
                activeUtilityUmbrella(c, HnsAbilitySide.ATTACKER) == true -> proof(
                    "orichalcum_pulse_utility_umbrella", "src/battle_util.c:7106",
                    "The authoritative active attacker Utility Umbrella disables this ability branch; the shared item pipeline models its selected-hit consequence."
                )
                else -> relevant(
                    "orichalcum_pulse_physical_raw_sun", "src/battle_util.c:7105-7106",
                    "The final category is Physical, GetWeather supplies Sun after HasWeatherEffect checks, and the attacker does not hold Utility Umbrella. The separate weather-setting event is not inferred."
                )
            }
            129 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "defeatist_defender_side", "src/battle_util.c:7002",
                    "Defeatist changes only its holder's selected attacking stat, not incoming damage."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != abilityId -> null
                c.attackerHp == null || c.attackerMaxHp == null || c.attackerMaxHp <= 0 ||
                    c.attackerHp !in 0..c.attackerMaxHp -> unknownProof(
                    "defeatist_live_hp_unknown", "src/battle_util.c:7002",
                    "Defeatist requires valid authoritative live attacker HP and max HP; caller HP and percentages are not accepted."
                )
                c.attackerHp <= c.attackerMaxHp / 2 -> relevant(
                    "defeatist_at_or_below_integer_half", "src/battle_util.c:7002",
                    "Live attacker HP is at or below integer floor(maxHP/2); Defeatist halves either selected attacking stat."
                )
                else -> proof(
                    "defeatist_above_integer_half", "src/battle_util.c:7002",
                    "Live attacker HP is above integer floor(maxHP/2), so Defeatist is inactive."
                )
            }
            169 -> when {
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    "fur_coat_attacker_side", "src/battle_util.c:7287",
                    "Fur Coat is read only in CalcDefenseStat for the defender."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.defenderAbilityId != abilityId ||
                    c.observedBattlersCount != 2 || c.moveCategory == null || c.fieldStatuses == null ->
                    unknownProof(
                        "fur_coat_defense_selection_unknown", "src/battle_util.c:7226, src/battle_util.c:7287",
                        "Fur Coat requires an authoritative ordinary move, final category, live defender ability, and observed field word."
                    )
                c.fieldStatuses and com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM != 0 ->
                    unknownProof(
                        "fur_coat_wonder_room_active", "src/battle_util.c:7226, src/battle_util.c:7287",
                        "Wonder Room changes usesDefStat selection and remains outside the supported shape."
                    )
                c.moveCategory == MoveCategory.PHYSICAL -> relevant(
                    "fur_coat_physical_uses_defense", "src/battle_util.c:7287",
                    "With Wonder Room inactive, this supported ordinary Physical hit uses Defense; Fur Coat doubles that Defense-stage operand."
                )
                else -> proof(
                    "fur_coat_special_uses_spdef", "src/battle_util.c:7287",
                    "With Wonder Room inactive, this supported ordinary Special hit uses Sp. Def, so the usesDefStat Fur Coat branch is inactive."
                )
            }
            179 -> when {
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    "grass_pelt_attacker_side", "src/battle_util.c:7296",
                    "Grass Pelt is read only from abilityDef in CalcDefenseStat."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.defenderAbilityId != abilityId ||
                    c.moveCategory == null || c.fieldStatuses == null -> unknownProof(
                    "grass_pelt_defense_selection_unknown", "src/battle_util.c:7226, src/battle_util.c:7296",
                    "Grass Pelt requires an authoritative ordinary move, final category, effective defender ability and live field word."
                )
                c.fieldStatuses and com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM != 0 ->
                    unknownProof(
                        "grass_pelt_wonder_room_active", "src/battle_util.c:7226, src/battle_util.c:7296",
                        "Wonder Room changes both the selected defensive stat and usesDefStat; this slice does not clear that independent field blocker."
                    )
                c.fieldStatuses and com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_GRASSY_TERRAIN == 0 ->
                    proof(
                        "grass_pelt_without_grassy_terrain", "src/battle_util.c:7296",
                        "The authoritative field word has no Grassy Terrain bit, so Grass Pelt's predicate is false."
                    )
                c.moveCategory == MoveCategory.PHYSICAL -> relevant(
                    "grass_pelt_grassy_terrain_physical", "src/battle_util.c:7296",
                    "With Wonder Room inactive, this ordinary Physical hit uses Defense and Grass Pelt's Grassy Terrain modifier applies in the implemented Defense-stage accumulator."
                )
                else -> proof(
                    "grass_pelt_special_uses_spdef", "src/battle_util.c:7296",
                    "With Wonder Room inactive, this ordinary Special hit uses Sp. Def, so usesDefStat is false and Grass Pelt is inactive."
                )
            }
            289 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "hadron_engine_defender_side", "src/battle_util.c:7111",
                    "Hadron Engine is read only from abilityAtk in CalcAttackStat."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != abilityId ||
                    c.moveCategory == null || c.fieldStatuses == null -> unknownProof(
                    "hadron_engine_operands_unknown", "src/battle_util.c:7111",
                    "Hadron Engine requires an authoritative ordinary move, effective attacker ability, final category and live field word."
                )
                c.fieldStatuses and com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_ELECTRIC_TERRAIN == 0 ->
                    proof(
                        "hadron_engine_without_electric_terrain", "src/battle_util.c:7111",
                        "The observed field word has no Electric Terrain bit; Hadron Engine never sets terrain in this calculator."
                    )
                c.moveCategory == MoveCategory.PHYSICAL -> proof(
                    "hadron_engine_physical_move", "src/battle_util.c:7111",
                    "Hadron Engine's pinned Attack-stat branch applies only to Special moves."
                )
                else -> relevant(
                    "hadron_engine_electric_terrain_special", "src/battle_util.c:7111",
                    "The observed Electric Terrain bit and authoritative final Special category satisfy Hadron Engine's implemented Attack-stat branch."
                )
            }
            in STATE_BACKED_GROUP_D_ABILITY_IDS -> stateBackedGroupDProof(abilityId, c)
            199 -> when {
                c.ordinaryMove != true -> null
                effectiveMoveType(c) == null -> null
                c.side == HnsAbilitySide.DEFENDER && effectiveMoveType(c) == PokemonType.FIRE -> relevant(
                    "water_bubble_defender_fire_move", "src/battle_util.c:6788",
                    "A defender Water Bubble and authoritative final Fire move use the pinned 0.5 base-power modifier; burn prevention and status clearing remain separately deferred."
                )
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "water_bubble_defender_nonfire_move", "src/battle_util.c:6789",
                    "The final authoritative move type is not Fire, so the defender Water Bubble base-power branch is inactive; burn prevention and status clearing remain separately deferred."
                )
                effectiveMoveType(c) == PokemonType.WATER -> relevant(
                    "water_bubble_attacker_water_move", "src/battle_util.c:6706",
                    "An attacker Water Bubble and an authoritative Water move take the modeled offensive 2.0 base-power branch."
                )
                else -> proof(
                    "water_bubble_attacker_nonwater_move", "src/battle_util.c:6706",
                    "The attacker's offensive Water Bubble modifier is proven inactive for this authoritative non-Water move."
                )
            }
            200 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "steelworker_defender_side", "src/battle_util.c:6710",
                    "Steelworker is checked only for abilityAtk and cannot modify the selected incoming hit."
                )
                c.ordinaryMove != true || !c.dynamicMoveTypeKnownNeutral || c.moveType == null -> null
                c.moveType == PokemonType.STEEL -> relevant(
                    "steelworker_attacker_steel_move", "src/battle_util.c:6710",
                    "Steelworker's attacker-side 1.5 base-power branch uses the authoritative effective move type."
                )
                else -> proof(
                    "steelworker_attacker_nonsteel_move", "src/battle_util.c:6710",
                    "The authoritative effective move type is not Steel, so Steelworker's base-power branch is inactive."
                )
            }
            137 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "toxic_boost_defender_side", "src/battle_util.c:6663",
                    "Toxic Boost is checked only for the attacker and cannot modify incoming damage."
                )
                c.ordinaryMove != true -> null
                c.moveCategory == null -> null
                c.moveCategory != MoveCategory.PHYSICAL -> proof(
                    "toxic_boost_special_move", "src/battle_util.c:6663",
                    "Toxic Boost's pinned branch requires a Physical move; the authoritative category is not Physical."
                )
                c.attackerStatus1 == null || !validStatusWord(c.attackerStatus1) -> null
                hasPoisonOnlyStatus(c.attackerStatus1) -> relevant(
                    "toxic_boost_physical_poison", "src/battle_util.c:6663",
                    "The exact STATUS1_PSN_ANY mask is set on the attacker and the authoritative category is Physical."
                )
                c.attackerStatus1 and HNS_STATUS1_POISON_ANY_MASK != 0 -> null
                else -> proof(
                    "toxic_boost_physical_without_poison", "src/battle_util.c:6663",
                    "No bit in the pinned STATUS1_PSN_ANY mask is set, so Toxic Boost is inactive for this Physical move."
                )
            }
            138 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "flare_boost_defender_side", "src/battle_util.c:6659",
                    "Flare Boost is checked only for the attacker and cannot modify incoming damage."
                )
                c.ordinaryMove != true -> null
                c.moveCategory == null -> null
                c.moveCategory != MoveCategory.SPECIAL -> proof(
                    "flare_boost_physical_move", "src/battle_util.c:6659",
                    "Flare Boost's pinned branch requires a Special move; the authoritative category is not Special."
                )
                c.attackerStatus1 == null || !validStatusWord(c.attackerStatus1) -> null
                hasBurnOnlyStatus(c.attackerStatus1) -> relevant(
                    "flare_boost_special_burn", "src/battle_util.c:6659",
                    "The exact STATUS1_BURN bit is set on the attacker and the authoritative category is Special."
                )
                c.attackerStatus1 and HNS_STATUS1_BURN_MASK != 0 -> null
                else -> proof(
                    "flare_boost_special_without_burn", "src/battle_util.c:6659",
                    "The pinned STATUS1_BURN bit is clear, so Flare Boost is inactive for this Special move."
                )
            }
            26 -> when (c.side) {
                HnsAbilitySide.ATTACKER -> when {
                    c.fieldStatuses == null -> null
                    c.fieldStatuses and HnsFieldStatusData.STATUS_FIELD_GRAVITY != 0 -> proof(
                        "levitate_attacker_gravity_override", "src/battle_util.c:6021-6036",
                        "Gravity returns grounded before the pinned Levitate ungrounding check; the shared terrain authority therefore reports the attacker terrain-affected."
                    )
                    c.fieldStatuses and (HnsFieldStatusData.STATUS_FIELD_GRASSY_TERRAIN or
                        HnsFieldStatusData.STATUS_FIELD_ELECTRIC_TERRAIN or
                        HnsFieldStatusData.STATUS_FIELD_PSYCHIC_TERRAIN) != 0 &&
                        c.attackerTerrainApplicability == HnsTerrainApplicability.NOT_AFFECTED &&
                        c.moveType in setOf(PokemonType.GRASS, PokemonType.ELECTRIC, PokemonType.PSYCHIC) -> proof(
                        "levitate_attacker_terrain_unaffected", "src/battle_util.c:6028-6034,6639-6645",
                        "The live Levitate identity is consumed by HnsTerrainAuthority, which proves the attacker ungrounded and suppresses the matching attacker-side terrain modifier."
                    )
                    c.fieldStatuses and HnsFieldStatusData.STATUS_FIELD_TERRAIN_ANY == 0 -> proof(
                        "attacker_levitate_no_terrain", "src/battle_util.c:5141",
                        "No terrain bit is active, so Levitate's groundedness cannot affect this ordinary selected hit."
                    )
                    !attackerLevitateTerrainModifierCanApply(c) -> proof(
                        "levitate_attacker_no_matching_terrain_modifier", "src/battle_util.c:6028-6034,6639-6645",
                        "Levitate only changes attacker-side Grassy, Electric, or Psychic modifiers when the final move type matches that active terrain; no such branch applies to this request."
                    )
                    else -> null
                }
                HnsAbilitySide.DEFENDER -> when {
                    c.fieldStatuses == null -> null
                    c.fieldStatuses and HnsFieldStatusData.STATUS_FIELD_GRAVITY != 0 -> proof(
                        "levitate_defender_gravity_override", "src/battle_util.c:6021-6036",
                        "Gravity returns grounded before the pinned Levitate ungrounding check; the shared terrain authority reports the defender terrain-affected."
                    )
                    c.moveType == PokemonType.DRAGON &&
                        c.fieldStatuses and HnsFieldStatusData.STATUS_FIELD_MISTY_TERRAIN != 0 &&
                        c.defenderTerrainApplicability == HnsTerrainApplicability.NOT_AFFECTED -> proof(
                        "levitate_defender_misty_unaffected", "src/battle_util.c:6028-6034,6641",
                        "The live Levitate identity is consumed by HnsTerrainAuthority, which proves the defender ungrounded and suppresses Misty Terrain's Dragon reduction."
                    )
                    c.fieldStatuses and HnsFieldStatusData.STATUS_FIELD_TERRAIN_ANY == 0 -> proof(
                        "defender_levitate_no_terrain", "src/battle_util.c:5141",
                        "No terrain bit is active; Levitate retains its existing Ground-move-only type-effectiveness behavior."
                    )
                    c.moveType == null || !c.dynamicMoveTypeKnownNeutral -> null
                    c.moveType != PokemonType.GROUND -> proof(
                        "defender_levitate_non_ground_move", "src/battle_util.c:8385",
                        "Levitate's type-effectiveness branch requires a Ground move."
                    )
                    else -> relevant(
                        "defender_levitate_ground_move", "src/battle_util.c:8385",
                        "A Ground move may be zeroed by the defender's Levitate."
                    )
                }
            }
            55 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "defender_hustle_does_not_modify_incoming_damage", "src/battle_util.c:7048",
                    "Hustle is read only in the attacker's Attack-stat modifier path."
                )
                c.moveCategory == null -> null
                c.moveCategory == MoveCategory.SPECIAL -> proof(
                    "hustle_special_move", "src/battle_util.c:7049",
                    "Hustle's Attack modifier requires a physical move."
                )
                else -> relevant(
                    "hustle_physical_move", "src/battle_util.c:7049-7050",
                    "A physical move receives the Hustle Attack-stat modifier."
                )
            }
            62 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "defender_guts_does_not_modify_incoming_damage", "src/battle_util.c:7056",
                    "Guts is applied only in the attacker's Attack-stat modifier path."
                )
                c.moveCategory == null -> null
                c.moveCategory == MoveCategory.SPECIAL -> proof(
                    "guts_special_move", "src/battle_util.c:7056",
                    "Guts modifies Attack only for physical moves."
                )
                c.attackerStatus1 == null -> null
                (c.attackerStatus1 and HNS_STATUS1_DEFINED_MASK.inv()) != 0 -> null
                c.attackerStatus1 == 0 -> proof(
                    "guts_attacker_neutral_status", "src/battle_util.c:7056",
                    "The Guts Attack modifier requires STATUS1_ANY; live status1 is observed zero."
                )
                (c.attackerStatus1 and HNS_STATUS1_ANY_MASK) != 0 -> relevant(
                    "guts_physical_move_with_status", "src/battle_util.c:7056",
                    "The observed status1 intersects pinned STATUS1_ANY, and a physical move receives the Guts modifier."
                )
                else -> null
            }
            37, 74 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "defender_attack_stat_ability", "src/battle_util.c:6989",
                    "Huge Power and Pure Power modify the holder's Attack stat only."
                )
                c.moveCategory == null -> null
                c.moveCategory == MoveCategory.SPECIAL -> proof(
                    "attack_stat_ability_special_move", "src/battle_util.c:6989",
                    "Huge Power and Pure Power multiply Attack only for physical moves."
                )
                else -> relevant(
                    "attack_stat_ability_physical_move", "src/battle_util.c:6989",
                    "The Attack multiplier applies to physical moves."
                )
            }
            47 -> when {
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    "attacker_thick_fat_does_not_mitigate_incoming_damage", "src/battle_util.c:7121",
                    "Thick Fat is read only from the defender-side ability."
                )
                c.moveType == null || !c.dynamicMoveTypeKnownNeutral -> null
                c.moveType != PokemonType.FIRE && c.moveType != PokemonType.ICE -> proof(
                    "thick_fat_other_move_type", "src/battle_util.c:7121",
                    "Thick Fat halves only Fire- and Ice-type move damage."
                )
                else -> relevant(
                    "thick_fat_fire_or_ice_move", "src/battle_util.c:7121",
                    "The effective move type is Fire or Ice."
                )
            }
            91 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    "defender_adaptability_does_not_boost_incoming_damage", "src/battle_util.c:7427",
                    "Adaptability is read only from the attacking battler's STAB modifier."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != 91 ||
                    c.moveType == null || c.attackerTypes == null || c.moveAuthority?.effectiveType == null ||
                    c.observedBattlersCount != 2 -> unknownProof(
                    "adaptability_stab_operands_unknown", "src/battle_util.c:7424-7430",
                    "Adaptability requires an ordinary move, the live effective ability, exact final move type, live attacker types, and observed Singles topology."
                )
                c.moveType !in c.attackerTypes -> proof(
                    "adaptability_without_stab", "src/battle_util.c:7427",
                    "The exact live attacker types do not include HnsMoveAuthority.effectiveType, so the pinned STAB multiplier is 1.0."
                )
                else -> relevant(
                    "adaptability_with_stab", "src/battle_util.c:7427",
                    "The exact live attacker types include HnsMoveAuthority.effectiveType; Adaptability uses the pinned 2.0 STAB multiplier."
                )
            }
            110, 233 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof(
                    if (abilityId == 110) "tinted_lens_defender_side" else "neuroforce_defender_side",
                    if (abilityId == 110) "src/battle_util.c:7570" else "src/battle_util.c:7562",
                    "${entry.titleCaseName} is read only from the attacking ability slot."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != abilityId ||
                    c.typeEffectiveness == null -> unknownProof(
                    if (abilityId == 110) "tinted_lens_effectiveness_unknown" else "neuroforce_effectiveness_unknown",
                    if (abilityId == 110) "src/battle_util.c:7571" else "src/battle_util.c:7563",
                    "${entry.titleCaseName} requires an authoritative ordinary move, live attacker ability, final move type, live defender types, and exact H&S effectiveness."
                )
                abilityId == 110 && c.typeEffectiveness <= 0.5 -> relevant(
                    "tinted_lens_resisted_hit", "src/battle_util.c:7570",
                    "The exact H&S effectiveness is at most 0.5; Tinted Lens applies its 2.0 final attacker modifier. Immunity remains zero before this stage."
                )
                abilityId == 233 && c.typeEffectiveness >= 2.0 -> relevant(
                    "neuroforce_super_effective_hit", "src/battle_util.c:7562",
                    "The exact H&S effectiveness is at least 2.0; Neuroforce applies its 1.25 final attacker modifier."
                )
                else -> proof(
                    if (abilityId == 110) "tinted_lens_not_resisted" else "neuroforce_not_super_effective",
                    if (abilityId == 110) "src/battle_util.c:7570" else "src/battle_util.c:7562",
                    if (abilityId == 110) "The exact H&S effectiveness is neutral or super-effective, so Tinted Lens is inactive."
                    else "The exact H&S effectiveness is below 2.0, so Neuroforce is inactive."
                )
            }
            111, 116, 232 -> when {
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    when (abilityId) {
                        111 -> "filter_attacker_side"
                        116 -> "solid_rock_attacker_side"
                        else -> "prism_armor_attacker_side"
                    }, "src/battle_util.c:7595-7597",
                    "${entry.titleCaseName} is read only from the defender ability slot."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.defenderAbilityId != abilityId ||
                    c.typeEffectiveness == null -> unknownProof(
                    when (abilityId) {
                        111 -> "filter_effectiveness_unknown"
                        116 -> "solid_rock_effectiveness_unknown"
                        else -> "prism_armor_effectiveness_unknown"
                    }, "src/battle_util.c:7598",
                    "${entry.titleCaseName} requires an authoritative ordinary move, live defender ability, final move type, live defender types, and exact H&S effectiveness."
                )
                c.typeEffectiveness >= 2.0 -> relevant(
                    when (abilityId) {
                        111 -> "filter_super_effective_hit"
                        116 -> "solid_rock_super_effective_hit"
                        else -> "prism_armor_super_effective_hit"
                    }, "src/battle_util.c:7595-7597",
                    "The exact H&S effectiveness is at least 2.0; ${entry.titleCaseName} applies its 0.75 final defender modifier."
                )
                else -> proof(
                    when (abilityId) {
                        111 -> "filter_not_super_effective"
                        116 -> "solid_rock_not_super_effective"
                        else -> "prism_armor_not_super_effective"
                    }, "src/battle_util.c:7595-7597",
                    "The exact H&S effectiveness is below 2.0, so ${entry.titleCaseName} is inactive."
                )
            }
            136, 231 -> when {
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    if (abilityId == 136) "multiscale_attacker_side" else "shadow_shield_attacker_side",
                    if (abilityId == 136) "src/battle_util.c:7587" else "src/battle_util.c:7588",
                    "${entry.titleCaseName} is read only from the defender ability slot."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.defenderAbilityId != abilityId -> unknownProof(
                    if (abilityId == 136) "multiscale_hp_unknown" else "shadow_shield_hp_unknown",
                    if (abilityId == 136) "src/battle_util.c:7589" else "src/battle_util.c:7589",
                    "${entry.titleCaseName} requires an authoritative ordinary move and exact live defender ability."
                )
                c.defenderHp == null || c.defenderMaxHp == null || c.defenderMaxHp <= 0 ||
                    c.defenderHp !in 1..c.defenderMaxHp -> unknownProof(
                    if (abilityId == 136) "multiscale_hp_unknown" else "shadow_shield_hp_unknown",
                    if (abilityId == 136) "src/battle_util.c:7589" else "src/battle_util.c:7589",
                    "Full HP cannot be inferred: exact live defender HP and positive max HP are required."
                )
                c.defenderHp == c.defenderMaxHp -> relevant(
                    if (abilityId == 136) "multiscale_full_hp" else "shadow_shield_full_hp",
                    if (abilityId == 136) "src/battle_util.c:7587" else "src/battle_util.c:7588",
                    "Authoritative live defender HP equals max HP; ${entry.titleCaseName} applies its 0.5 final defender modifier."
                )
                else -> proof(
                    if (abilityId == 136) "multiscale_below_full_hp" else "shadow_shield_below_full_hp",
                    if (abilityId == 136) "src/battle_util.c:7587" else "src/battle_util.c:7588",
                    "Authoritative live defender HP is below max HP, so ${entry.titleCaseName} is inactive."
                )
            }
            246 -> when {
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    "ice_scales_attacker_side", "src/battle_util.c:7623",
                    "Ice Scales is read only from the defender ability slot."
                )
                c.ordinaryMove != true || !abilityObserved(c) || c.defenderAbilityId != abilityId ||
                    c.moveCategory == null -> unknownProof(
                    "ice_scales_category_unknown", "src/battle_util.c:7624",
                    "Ice Scales requires an authoritative ordinary move, live defender ability, and HnsMoveAuthority's final category."
                )
                c.moveCategory == MoveCategory.SPECIAL -> relevant(
                    "ice_scales_special_move", "src/battle_util.c:7623",
                    "HnsMoveAuthority's final move category is Special; Ice Scales applies its 0.5 final defender modifier."
                )
                else -> proof(
                    "ice_scales_physical_move", "src/battle_util.c:7623",
                    "HnsMoveAuthority's final move category is Physical, so Ice Scales is inactive."
                )
            }
            4, 75 -> when (c.side) {
                HnsAbilitySide.ATTACKER -> proof(
                    "attacker_critical_hit_armor", "src/battle_util.c:8056",
                    "The critical-hit prevention check reads only the defender's ability."
                )
                HnsAbilitySide.DEFENDER -> when {
                    c.ordinaryMove != true || c.isCrit == null -> null
                    !c.isCrit -> proof(
                        "defender_armor_fixed_noncritical_hit", "src/battle_util.c:8056",
                        "Critical-hit prevention cannot change an explicitly noncritical hit's rolls."
                    )
                    else -> relevant(
                        "defender_armor_critical_hit_conflict", "src/battle_util.c:8056",
                        "A critical request conflicts with the defender's critical-hit prevention."
                    )
                }
            }
            132 -> singlesProof(c, "friend_guard_singles_no_partner", "src/battle_util.c:7647",
                "Friend Guard modifies damage to an ally; an authoritative Singles battle has none.")
            57, 58 -> singlesProof(c, "plus_minus_singles_no_partner", "src/battle_util.c:7026",
                "Plus/Minus boosts the holder's Special Attack only with an active partner.")
            63 -> when {
                c.side == HnsAbilitySide.ATTACKER -> proof("marvel_scale_attacker_side", "src/battle_util.c:7280",
                    "Marvel Scale is read only in CalcDefenseStat for the defender.")
                c.ordinaryMove != true || !abilityObserved(c) || c.defenderAbilityId != 63 || c.moveCategory == null || c.fieldStatuses == null ->
                    unknownProof("marvel_scale_operands_unknown", "src/battle_util.c:7226, src/battle_util.c:7280-7284",
                        "Marvel Scale requires the selected-hit defense-stat choice, raw defender status1, and observed field word.")
                c.fieldStatuses and com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM != 0 ->
                    unknownProof("marvel_scale_wonder_room_active", "src/battle_util.c:7226, src/battle_util.c:7280-7284",
                        "Wonder Room changes usesDefStat and remains blocked by the field policy.")
                c.moveCategory != MoveCategory.PHYSICAL -> proof("marvel_scale_special_uses_spdef", "src/battle_util.c:7280-7284",
                    "With Wonder Room inactive, a Special hit selects Sp. Def and Marvel Scale's usesDefStat predicate is false.")
                c.defenderStatus1 == null || (c.defenderStatus1 and HNS_STATUS1_DEFINED_MASK.inv()) != 0 ->
                    unknownProof("marvel_scale_status_unknown", "src/battle_util.c:7280-7284",
                        "The raw defender status1 word is unread or invalid; display text is not accepted.")
                (c.defenderStatus1 and HNS_STATUS1_ANY_MASK) == 0 -> proof("marvel_scale_no_status", "src/battle_util.c:7280-7284",
                    "The valid raw defender status1 word has no STATUS1_ANY bit.")
                else -> relevant("marvel_scale_physical_status", "src/battle_util.c:7280-7284",
                    "Physical selects Defense with Wonder Room clear, and raw defender status1 has STATUS1_ANY; the ×1.5 Defense-stage modifier is modelled.")
            }
            122 -> when {
                !abilityObserved(c) || c.ordinaryMove != true || c.moveCategory == null || c.fieldStatuses == null ->
                    unknownProof("flower_gift_operands_unknown", "src/battle_util.c:7044-7046, 7303-7305",
                        "Flower Gift requires authoritative ordinary move category, live current form, field word, weather and holder item.")
                c.fieldStatuses and com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM != 0 && c.side == HnsAbilitySide.DEFENDER ->
                    unknownProof("flower_gift_wonder_room_active", "src/battle_util.c:7226, 7303-7305",
                        "Wonder Room changes the defender usesDefStat selection and remains blocked.")
                c.side == HnsAbilitySide.ATTACKER && c.attackerAbilityId != 122 -> proof("flower_gift_attacker_side", "src/battle_util.c:7044-7046",
                    "The request ability is not the attacker-side Flower Gift holder.")
                c.side == HnsAbilitySide.DEFENDER && c.defenderAbilityId != 122 -> proof("flower_gift_defender_side", "src/battle_util.c:7303-7305",
                    "The request ability is not the defender-side Flower Gift holder.")
                (if (c.side == HnsAbilitySide.ATTACKER) c.attackerSpeciesId else c.defenderSpeciesId) == null ->
                    unknownProof("flower_gift_form_unknown", "src/battle_util.c:7044-7046, 7303-7305",
                        "The holder's exact live battle species/form ID is required.")
                (if (c.side == HnsAbilitySide.ATTACKER) c.attackerSpeciesId else c.defenderSpeciesId) != HnsAbilityAuditData.CHERRIM_SUNSHINE_SPECIES_ID ->
                    proof("flower_gift_not_cherrim_sunshine", "src/battle_util.c:7044-7046, 7303-7305",
                        "The live current holder form is not SPECIES_CHERRIM_SUNSHINE.")
                c.side == HnsAbilitySide.ATTACKER && c.moveCategory != MoveCategory.PHYSICAL -> proof("flower_gift_attacker_special", "src/battle_util.c:7044-7046",
                    "The attacker-side Flower Gift branch applies only to Physical moves.")
                c.side == HnsAbilitySide.DEFENDER && c.moveCategory == MoveCategory.PHYSICAL -> proof("flower_gift_defender_physical", "src/battle_util.c:7303-7305",
                    "With Wonder Room clear, Physical selects Defense, so the defender's !usesDefStat branch is false.")
                !c.weatherObserved || c.weatherWord == null -> unknownProof("flower_gift_weather_unknown", "src/battle_util.c:9530-9535",
                    "The live weather and HasWeatherEffect prerequisites must be authoritative.")
                (c.weatherWord and com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN) == 0 -> proof("flower_gift_without_sun", "src/battle_util.c:9525-9535",
                    "The observed raw weather has no B_WEATHER_SUN bit.")
                c.attackerHp == null || c.defenderHp == null || c.attackerHp <= 0 || c.defenderHp <= 0 -> unknownProof("flower_gift_liveness_unknown", "src/battle_util.c:9525-9535",
                    "HasWeatherEffect excludes fainted battlers; positive live HP is required to decide weather suppression.")
                c.attackerAbilityId in WEATHER_SUPPRESSOR_IDS || c.defenderAbilityId in WEATHER_SUPPRESSOR_IDS -> proof("flower_gift_weather_suppressed", "src/battle_util.c:9525-9535",
                    "Cloud Nine or Air Lock suppresses HasWeatherEffect for all battlers.")
                (if (c.side == HnsAbilitySide.ATTACKER) c.attackerItemId else c.defenderItemId) == null -> unknownProof("flower_gift_holder_item_unknown", "src/battle_util.c:9525-9535",
                    "Utility Umbrella must be checked on the actual Flower Gift holder.")
                (if (c.side == HnsAbilitySide.ATTACKER) c.attackerItemId else c.defenderItemId) == null ||
                    activeUtilityUmbrella(c, c.side) == null -> unknownProof("flower_gift_holder_item_unknown", "src/battle_util.c:9530",
                    "The Flower Gift holder's current effective hold effect is required to decide whether Utility Umbrella suppresses Sun.")
                activeUtilityUmbrella(c, c.side) == true -> proof("flower_gift_holder_umbrella", "src/battle_util.c:9525-9535",
                    "The Flower Gift holder's authoritative active Utility Umbrella suppresses its weather applicability; the shared item pipeline models that consequence.")
                else -> relevant(if (c.side == HnsAbilitySide.ATTACKER) "flower_gift_attacker_sun_physical" else "flower_gift_defender_sun_special",
                    if (c.side == HnsAbilitySide.ATTACKER) "src/battle_util.c:7044-7046" else "src/battle_util.c:7303-7305",
                    "The live Cherrim-Sunshine holder is affected by Sun; the applicable holder-side Singles stat modifier is implemented. Partner/Doubles behavior remains unsupported.")
            }
            125 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof("sheer_force_defender_side", "src/battle_util.c:6675-6677",
                    "Sheer Force is read only in the attacker base-power ability slot.")
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != 125 -> null
                c.sheerForceAffected == null || c.moveId in com.dualdex.pokemon.hns.Hns205MoveEffects.unknownSheerForceMoveIds ->
                    unknownProof("sheer_force_predicate_unknown", "src/battle_util.c:6675-6677, 9757-9773",
                        "The exact source-generated MoveIsAffectedBySheerForce result is unresolved.")
                c.sheerForceAffected == true -> relevant("sheer_force_source_predicate_true", "src/battle_util.c:6675-6677, 9757-9773",
                    "The pinned helper predicate is true for this move; its ×1.3 base-power factor is modelled.")
                else -> proof("sheer_force_source_predicate_false", "src/battle_util.c:6675-6677, 9757-9773",
                    "The source-derived helper predicate is false; generic secondary-effect presence is not used as a substitute.")
            }
            181 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof("tough_claws_defender_side", "src/battle_util.c:6694-6696",
                    "Tough Claws is read only in the attacker base-power ability slot.")
                c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != 181 -> null
                else -> when (HnsContactRules.assess(c.moveId, c.ordinaryMove, c.attackerAbilityId,
                    c.attackerAbilityObserved, c.attackerItemId, c.attackerHoldEffectResolution)) {
                    HnsContactAuthority.CONTACT -> relevant("tough_claws_contact", "src/battle_util.c:6694-6696, 5868-5885",
                        "The shared pinned contact authority returns CONTACT; Tough Claws applies ×1.3 base power.")
                    HnsContactAuthority.NON_CONTACT -> proof("tough_claws_noncontact", "src/battle_util.c:6694-6696, 5868-5885",
                        "The shared pinned contact authority returns NON_CONTACT; Tough Claws is inactive.")
                    HnsContactAuthority.UNKNOWN -> unknownProof("tough_claws_contact_unknown", "src/battle_util.c:5868-5885",
                        "Pinned contact metadata or current effective Long Reach/Punching Glove operands are unknown.")
                }
            }
            218 -> when {
                c.side == HnsAbilitySide.ATTACKER -> proof("fluffy_attacker_side", "src/battle_util.c:7604-7614",
                    "Fluffy is read only from the defender ability slot.")
                c.ordinaryMove != true || !abilityObserved(c) || !c.attackerAbilityObserved || c.defenderAbilityId != 218 || effectiveMoveType(c) == null ->
                    unknownProof("fluffy_operands_unknown", "src/battle_util.c:7604-7614",
                        "Fluffy requires the selected ordinary hit's final effective type and shared contact authority.")
                else -> when (HnsContactRules.assess(c.moveId, c.ordinaryMove, c.attackerAbilityId,
                    c.attackerAbilityObserved, c.attackerItemId, c.attackerHoldEffectResolution)) {
                    HnsContactAuthority.UNKNOWN -> unknownProof("fluffy_contact_unknown", "src/battle_util.c:5868-5885, 7604-7614",
                        "Pinned contact metadata or current effective Long Reach/Punching Glove operands are unknown.")
                    HnsContactAuthority.CONTACT -> if (effectiveMoveType(c) == PokemonType.FIRE)
                        proof("fluffy_fire_contact_neutral", "src/battle_util.c:7604-7614", "Fire + contact is neutral under the pinned Fluffy matrix.")
                    else relevant("fluffy_nonfire_contact_half", "src/battle_util.c:7604-7614", "Non-Fire + contact uses the pinned ×0.5 defender final modifier.")
                    HnsContactAuthority.NON_CONTACT -> if (effectiveMoveType(c) == PokemonType.FIRE)
                        relevant("fluffy_fire_noncontact_double", "src/battle_util.c:7604-7614", "Fire + non-contact uses the pinned ×2.0 defender final modifier.")
                    else proof("fluffy_nonfire_noncontact_neutral", "src/battle_util.c:7604-7614", "Non-Fire + non-contact is neutral under the pinned Fluffy matrix.")
                }
            }
            120 -> if (c.side == HnsAbilitySide.DEFENDER || c.ordinaryMove == true)
                proof("reckless_ordinary_move_not_recoil", "src/battle_util.c:6667-6670",
                    "The exact ordinary move surface contains only EFFECT_HIT; recoil effects remain refused by the independent move gate.") else null
            159 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof("sand_force_defender_side", "src/battle_util.c:6679-6682", "Sand Force is read only in the attacker base-power slot.")
                c.ordinaryMove != true || effectiveMoveType(c) == null -> null
                !c.weatherObserved || c.weatherWord == null -> unknownProof("sand_force_weather_unknown", "src/battle_util.c:6679-6682", "The authoritative raw weather word is required.")
                effectiveMoveType(c) !in setOf(PokemonType.STEEL, PokemonType.ROCK, PokemonType.GROUND) -> proof("sand_force_nonmatching_type", "src/battle_util.c:6679-6682", "The final move type is outside Steel/Rock/Ground, so Sand Force is inactive.")
                (c.weatherWord and 0x20) == 0 -> proof("sand_force_without_sandstorm", "src/battle_util.c:6679-6682", "The observed raw weather has no B_WEATHER_SANDSTORM bit.")
                else -> relevant("sand_force_sandstorm_matching_type", "src/battle_util.c:6679-6682", "Sand Force's base-power branch is modelled; independent Sandstorm damage limitations still refuse production execution.")
            }
            217, 249 -> when {
                c.side == HnsAbilitySide.DEFENDER -> proof("partner_ability_defender_side", "src/battle_util.c:6763-6774", "Battery and Power Spot are read only from the living attacker partner.")
                c.observedBattlersCount == 2 -> proof("partner_ability_singles_no_partner", "src/battle_util.c:6763-6774", "An authoritative Singles battle has no living attacker partner; the holder's own ability cannot boost its move.")
                else -> null
            }
            else -> null
        }

        return when (proof) {
            null -> unknown(abilityId, entry.titleCaseName, c.side, entry.category)
            else -> decision(
                abilityId, entry.titleCaseName, c.side, entry.category,
                proof.relevance, proof.rule, proof.source, proof.rationale
            )
        }
    }

    /** Prefer the one request-resolved final type; only authority-free unit fixtures use the fallback. */
    private fun effectiveMoveType(c: Context): PokemonType? = if (c.moveAuthority != null) {
        c.moveAuthority.effectiveType
    } else {
        c.moveType.takeIf { c.dynamicMoveTypeKnownNeutral }
    }

    private fun attackStatAbilityType(abilityId: Int): PokemonType = when (abilityId) {
        262 -> PokemonType.ELECTRIC
        263 -> PokemonType.DRAGON
        276 -> PokemonType.ROCK
        else -> error("No type predicate for attack-stat ability $abilityId")
    }

    /**
     * Builds the ability context from a request whose live state was rebound by CalcRequestBoundary.
     *
     * The effective move type and category come from [HnsMoveAuthority]: each needs only its own
     * evidence, so an unrelated live field bit (terrain, a room, Gravity, ...) does not make them
     * unknown. [Context.moveType] is null whenever the effective type is not authoritative, and
     * [Context.dynamicMoveTypeKnownNeutral] is true exactly when it is.
     */
    fun contextForRequest(
        request: DamageCalculationRequest,
        side: HnsAbilitySide,
        ordinaryMove: Boolean?
    ): Context {
        val live = request.hnsLiveBattleState
        val authority = HnsMoveAuthority.forRequest(request, ordinaryMove)
        val pinnedMove = com.dualdex.pokemon.hns.HeartAndSoul205DataPack.getMoveByName(request.move.name)
        val moveId = pinnedMove?.id
        val rawAttackerTypes = live?.attackerTypes
        val rawDefenderTypes = live?.defenderTypes
        val effectiveMoveType = authority.effectiveType
        val exactTypeEffectiveness = if (effectiveMoveType != null && parsedTypes(rawDefenderTypes) != null) {
            HnsGroupCPolicy.typeEffectiveness(request, effectiveMoveType)
        } else {
            null
        }
        return Context(
            side = side,
            ordinaryMove = ordinaryMove,
            isCrit = request.move.isCrit,
            attackerAbilityId = request.attacker.abilityId,
            moveType = authority.effectiveType,
            moveCategory = authority.category,
            attackerTypes = parsedTypes(rawAttackerTypes),
            defenderSpeciesId = live?.defenderSpeciesId,
            attackerSpeciesId = live?.attackerSpeciesId,
            defenderHp = live?.defenderHp,
            defenderMaxHp = live?.defenderMaxHp,
            attackerStatus1 = live?.attackerStatus1,
            defenderStatus1 = live?.defenderStatus1,
            moveId = moveId,
            moveBasePower = pinnedMove?.power,
            moveAbilityFlags = moveId?.let {
                com.dualdex.pokemon.hns.Hns205MoveEffects.abilityMoveFlagsById[it].orEmpty()
            },
            unknownMoveAbilityFlags = moveId?.let {
                com.dualdex.pokemon.hns.Hns205MoveEffects.unknownAbilityMoveFlagsById[it].orEmpty()
            },
            sheerForceAffected = moveId?.let {
                com.dualdex.pokemon.hns.Hns205MoveEffects.sheerForceAffectedById[it]
            },
            soundMove = authority.soundMove,
            observedBattlersCount = live?.observedBattlersCount,
            dynamicMoveTypeKnownNeutral = authority.effectiveType != null,
            defenderItemId = request.defender.itemId ?: when {
                !request.defender.item.isNullOrBlank() ->
                    HnsItemRegistry.resolveIdByName(request.defender.item)
                request.defender.origin == CalcInputOrigin.MANUAL -> 0
                else -> null
            },
            defenderTypes = parsedTypes(rawDefenderTypes),
            attackerStatStages = live?.attackerStatStages,
            defenderStatStages = live?.defenderStatStages,
            attackerAbilityObserved = hasAuthoritativeLiveAbility(request.attacker),
            defenderAbilityObserved = hasAuthoritativeLiveAbility(request.defender),
            attackerHp = live?.attackerHp,
            attackerMaxHp = live?.attackerMaxHp,
            defenderAbilityId = request.defender.abilityId,
            attackerItemId = request.attacker.itemId ?: when {
                !request.attacker.item.isNullOrBlank() -> HnsItemRegistry.resolveIdByName(request.attacker.item)
                request.attacker.origin == CalcInputOrigin.MANUAL -> 0
                else -> null
            },
            typeEffectiveness = exactTypeEffectiveness,
            weatherWord = live?.takeIf { it.weatherObserved }?.weatherWord,
            weatherObserved = live?.weatherObserved == true,
            fieldStatuses = live?.fieldStatuses,
            attackerTerrainApplicability = live?.attackerTerrainApplicability,
            defenderTerrainApplicability = live?.defenderTerrainApplicability,
            switchInEventsSettled = live?.switchInEventsSettled,
            moveAuthority = authority,
            liveBattleState = live,
            attackerHoldEffectResolution = HnsHoldEffectAuthority.forRequest(request, HnsItemSide.ATTACKER),
            defenderHoldEffectResolution = HnsHoldEffectAuthority.forRequest(request, HnsItemSide.DEFENDER),
            defenderAbilityShieldActiveIgnoringAbility = HnsHoldEffectAuthority
                .abilityShieldActiveIgnoringAbilityForRequest(request, HnsItemSide.DEFENDER),
            resistBerryDecision = HnsResistBerryAuthority.forRequest(request)
        )
    }

    private fun activeUtilityUmbrella(c: Context, side: HnsAbilitySide): Boolean? {
        val itemId = (if (side == HnsAbilitySide.ATTACKER) c.attackerItemId else c.defenderItemId)
            ?: return null
        if (itemId == 0) return false
        val resolution = if (side == HnsAbilitySide.ATTACKER) {
            c.attackerHoldEffectResolution
        } else {
            c.defenderHoldEffectResolution
        }
        return when (resolution?.state) {
            HnsHoldEffectState.ACTIVE_EXACT -> resolution.effectiveHoldEffect == "HOLD_EFFECT_UTILITY_UMBRELLA"
            HnsHoldEffectState.SUPPRESSED_NONE -> false
            HnsHoldEffectState.UNKNOWN -> null
            null -> HnsItemRegistry.classify(itemId).data?.holdEffect == "HOLD_EFFECT_UTILITY_UMBRELLA"
        }
    }

    private fun moveTypeRewriteAbilityProof(abilityId: Int, c: Context): Proof? {
        if (c.side == HnsAbilitySide.DEFENDER) return proof(
            "move_type_rewriter_defender_side",
            "src/battle_main.c:6428",
            "SetTypeBeforeUsingMove passes the selected attacker battler to GetDynamicMoveType; a defender copy does not rewrite the attacker's move."
        )
        if (c.ordinaryMove != true || !abilityObserved(c) || c.attackerAbilityId != abilityId) return null
        val authority = c.moveAuthority ?: return null
        return when (authority.abilityRewriteOutcome) {
            HnsAbilityTypeRewriteOutcome.APPLIED -> relevant(
                moveTypeRewriteRule(abilityId, relevant = true),
                moveTypeRewriteSource(abilityId),
                moveTypeRewriteRationale(abilityId)
            )
            HnsAbilityTypeRewriteOutcome.PROVEN_NOT_APPLICABLE -> proof(
                moveTypeRewriteRule(abilityId, relevant = false),
                moveTypeRewriteSource(abilityId),
                moveTypeRewriteIrrelevantRationale(abilityId)
            )
            HnsAbilityTypeRewriteOutcome.NOT_USED,
            HnsAbilityTypeRewriteOutcome.UNKNOWN -> null
        }
    }

    private fun moveTypeRewriteRule(abilityId: Int, relevant: Boolean): String = when (abilityId) {
        96 -> if (relevant) "normalize_ordinary_move_rewrite" else "normalize_unresolved_move_rewrite"
        174 -> if (relevant) "refrigerate_normal_move_rewrite" else "refrigerate_non_normal_move"
        182 -> if (relevant) "pixilate_normal_move_rewrite" else "pixilate_non_normal_move"
        184 -> if (relevant) "aerilate_normal_move_rewrite" else "aerilate_non_normal_move"
        204 -> if (relevant) "liquid_voice_sound_move_rewrite" else "liquid_voice_non_sound_move"
        else -> if (relevant) "galvanize_normal_move_rewrite" else "galvanize_non_normal_move"
    }

    private fun moveTypeRewriteSource(abilityId: Int): String = when (abilityId) {
        96 -> "src/battle_main.c:6407-6412"
        174 -> "src/battle_main.c:6158-6159, src/battle_main.c:6392-6400"
        182 -> "src/battle_main.c:6155-6156, src/battle_main.c:6392-6400"
        184 -> "src/battle_main.c:6161-6162, src/battle_main.c:6392-6400"
        204 -> "include/move.h:358-360, src/battle_main.c:6382-6384"
        else -> "src/battle_main.c:6164-6165, src/battle_main.c:6392-6400"
    }

    private fun moveTypeRewriteRationale(abilityId: Int): String = when (abilityId) {
        96 -> "Normalize rewrites every supported ordinary hit to Normal and sets ateBoost; the pinned Gen-Latest config applies the later x1.2 branch."
        174 -> "A source Normal ordinary hit becomes Ice, sets ateBoost, and receives the pinned Gen-Latest x1.2 base-power modifier."
        182 -> "A source Normal ordinary hit becomes Fairy, sets ateBoost, and receives the pinned Gen-Latest x1.2 base-power modifier."
        184 -> "A source Normal ordinary hit becomes Flying, sets ateBoost, and receives the pinned Gen-Latest x1.2 base-power modifier."
        204 -> "A source-derived sound ordinary hit becomes Water; the pinned base-power stage has no Liquid Voice modifier."
        else -> "A source Normal ordinary hit becomes Electric, sets ateBoost, and receives the pinned Gen-Latest x1.2 base-power modifier."
    }

    private fun moveTypeRewriteIrrelevantRationale(abilityId: Int): String = when (abilityId) {
        174 -> "The pinned Refrigerate branch runs only when the source move type is Normal."
        182 -> "The pinned Pixilate branch runs only when the source move type is Normal."
        184 -> "The pinned Aerilate branch runs only when the source move type is Normal."
        204 -> "The pinned Liquid Voice branch runs only when the source-derived MoveInfo.soundMove flag is true."
        else -> "The pinned Galvanize branch runs only when the source move type is Normal."
    }

    private fun moveFlagAbilityProof(abilityId: Int, c: Context): Proof? {
        val rule = when (abilityId) {
            89 -> MoveFlagAbilityRule("punchingMove", "src/battle_util.c:6671", "include/move.h:343",
                "iron_fist_punching_move", "iron_fist_nonpunching_move", "Iron Fist", "iron_fist_defender_side")
            173 -> MoveFlagAbilityRule("bitingMove", "src/battle_util.c:6698", "include/move.h:348",
                "strong_jaw_biting_move", "strong_jaw_nonbiting_move", "Strong Jaw", "strong_jaw_defender_side")
            178 -> MoveFlagAbilityRule("pulseMove", "src/battle_util.c:6702", "include/move.h:353",
                "mega_launcher_pulse_move", "mega_launcher_nonpulse_move", "Mega Launcher", "mega_launcher_defender_side")
            else -> MoveFlagAbilityRule("slicingMove", "src/battle_util.c:6742", "include/move.h:383",
                "sharpness_slicing_move", "sharpness_nonslicing_move", "Sharpness", "sharpness_defender_side")
        }
        val (moveFlag, abilitySource, moveFlagSource, positiveRule, negativeRule, abilityName, defenderRule) = rule
        if (c.side == HnsAbilitySide.DEFENDER) return proof(
            defenderRule, abilitySource,
            "$abilityName is checked only for the attacker and cannot modify the selected incoming hit."
        )
        if (c.ordinaryMove != true || c.moveId == null) return null
        if (c.unknownMoveAbilityFlags?.contains(moveFlag) == true) return null
        val flags = c.moveAbilityFlags ?: return null
        return if (moveFlag in flags) relevant(
            positiveRule, "$abilitySource; $moveFlagSource",
            "The pinned $moveFlag MoveInfo bit is set for this authoritative ordinary move; $abilityName's base-power branch is modeled."
        ) else proof(
            negativeRule, "$abilitySource; $moveFlagSource",
            "The pinned $moveFlag MoveInfo bit is clear for this authoritative ordinary move, so $abilityName's base-power branch is inactive."
        )
    }

    private fun punkRockProof(c: Context): Proof? {
        if (c.ordinaryMove != true) return null
        val soundMove = if (c.moveAuthority != null) c.moveAuthority.soundMove else c.soundMove
        if (soundMove == null) return null
        val branchSource = if (c.side == HnsAbilitySide.ATTACKER) {
            "src/battle_util.c:6735"
        } else {
            "src/battle_util.c:7617"
        }
        val source = "include/move.h:358-360, $branchSource"
        return when {
            c.side == HnsAbilitySide.ATTACKER && soundMove -> relevant(
                "punk_rock_attacker_sound_move", source,
                "Pinned IsSoundMove is true for this source move; Punk Rock composes the attacker's 1.3 base-power modifier."
            )
            c.side == HnsAbilitySide.ATTACKER -> proof(
                "punk_rock_attacker_nonsound_move", source,
                "Pinned IsSoundMove is false for this source move, so the attacker's base-power branch is inactive."
            )
            soundMove -> relevant(
                "punk_rock_defender_sound_move", source,
                "Pinned IsSoundMove is true for this incoming move; defender Punk Rock applies its 0.5 final-damage modifier."
            )
            else -> proof(
                "punk_rock_defender_nonsound_move", source,
                "Pinned IsSoundMove is false for this incoming move, so defender Punk Rock is inactive."
            )
        }
    }

    private fun steelySpiritProof(c: Context): Proof? {
        // "steely_spirit_attacker_partner_deferred": partner identity/topology is not an operand in
        // this Singles request, so this rule remains documentation and a production-format gate.
        if (c.ordinaryMove != true) return null
        if (c.side == HnsAbilitySide.DEFENDER) return proof(
            "steely_spirit_defender_singles_irrelevant", "src/battle_util.c:6738, src/battle_util.c:6775",
            "The holder-only branch modifies its own Steel attacks; the partner branch is deferred with unsupported Doubles topology and cannot affect this defender's incoming Singles hit."
        )
        val effectiveType = if (c.moveAuthority != null) {
            c.moveAuthority.effectiveType
        } else {
            c.moveType
        }
        if (!c.dynamicMoveTypeKnownNeutral || effectiveType == null) return null
        return if (effectiveType == PokemonType.STEEL) relevant(
            "steely_spirit_holder_effective_steel_move", "src/battle_util.c:6739",
            "The final HnsMoveAuthority effective type is Steel; Steely Spirit applies its holder-side 1.5 base-power modifier."
        ) else proof(
            "steely_spirit_holder_effective_nonsteel_move", "src/battle_util.c:6739",
            "The final HnsMoveAuthority effective type is not Steel, so the holder-side base-power branch is inactive."
        )
    }

    private fun validStatusWord(status1: Int): Boolean =
        status1 >= 0 && (status1 and HNS_STATUS1_DEFINED_MASK.inv()) == 0

    private fun hasPoisonOnlyStatus(status1: Int): Boolean {
        val poisonBits = status1 and HNS_STATUS1_POISON_ANY_MASK
        if (!validStatusWord(status1) || poisonBits !in setOf(0x08, HNS_STATUS1_TOXIC_POISON_MASK)) return false
        if ((status1 and HNS_STATUS1_ANY_MASK and HNS_STATUS1_POISON_ANY_MASK.inv()) != 0) return false
        val allowedToxicCounter = if (status1 and HNS_STATUS1_TOXIC_POISON_MASK != 0) {
            HNS_STATUS1_TOXIC_COUNTER_MASK
        } else {
            0
        }
        return (status1 and HNS_STATUS1_POISON_ANY_MASK.inv() and allowedToxicCounter.inv()) == 0
    }

    private fun hasBurnOnlyStatus(status1: Int): Boolean {
        if (!validStatusWord(status1) || (status1 and HNS_STATUS1_BURN_MASK) == 0) return false
        if ((status1 and HNS_STATUS1_ANY_MASK and HNS_STATUS1_BURN_MASK.inv()) != 0) return false
        val toxicCounterWithoutToxic =
            (status1 and HNS_STATUS1_TOXIC_COUNTER_MASK) != 0 &&
                (status1 and HNS_STATUS1_TOXIC_POISON_MASK) == 0
        return !toxicCounterWithoutToxic
    }

    private fun attackerLevitateTerrainModifierCanApply(c: Context): Boolean {
        val field = c.fieldStatuses ?: return true
        val type = c.moveType ?: return true
        return (field and HnsFieldStatusData.STATUS_FIELD_GRASSY_TERRAIN != 0 && type == PokemonType.GRASS) ||
            (field and HnsFieldStatusData.STATUS_FIELD_ELECTRIC_TERRAIN != 0 && type == PokemonType.ELECTRIC) ||
            (field and HnsFieldStatusData.STATUS_FIELD_PSYCHIC_TERRAIN != 0 && type == PokemonType.PSYCHIC)
    }

    /** Levitate only changes the three attacker-side direct terrain checks on an ordinary hit. */
    private fun attackerLevitateTerrainProof(c: Context): Proof? {
        if (c.side != HnsAbilitySide.ATTACKER || c.ordinaryMove != true) return null
        val field = c.fieldStatuses ?: return null
        if (field and HnsFieldStatusData.KNOWN_MASK.inv() != 0) return null
        val type = effectiveMoveType(c) ?: return null
        val grassy = field and HnsFieldStatusData.STATUS_FIELD_GRASSY_TERRAIN != 0 && type == PokemonType.GRASS
        val electric = field and HnsFieldStatusData.STATUS_FIELD_ELECTRIC_TERRAIN != 0 && type == PokemonType.ELECTRIC
        val psychic = field and HnsFieldStatusData.STATUS_FIELD_PSYCHIC_TERRAIN != 0 && type == PokemonType.PSYCHIC
        if (!grassy && !electric && !psychic) return proof(
            "levitate_attacker_no_matching_terrain_modifier", "src/battle_util.c:5142,6639-6645",
            "Levitate only changes attacker-side Grassy, Electric, or Psychic terrain modifiers when the final move type matches that active terrain; this request has no matching branch."
        )
        return when (c.attackerTerrainApplicability) {
            HnsTerrainApplicability.AFFECTED -> proof(
                "levitate_attacker_terrain_grounded", "src/battle_util.c:6021-6036,6639-6645",
                "The shared terrain authority proves the attacker affected, so the matching direct terrain modifier is applied exactly."
            )
            HnsTerrainApplicability.NOT_AFFECTED -> proof(
                "levitate_attacker_terrain_unaffected", "src/battle_util.c:6028-6034,6639-6645",
                "The shared terrain authority proves the attacker ungrounded, so the matching direct terrain modifier is omitted exactly."
            )
            HnsTerrainApplicability.UNKNOWN, null -> null
        }
    }

    private fun parsedTypes(rawTypes: List<String>?): Set<PokemonType>? {
        if (rawTypes == null || rawTypes.isEmpty() || rawTypes.size > 2) return null
        val parsed = rawTypes.map { PokemonType.fromString(it) ?: return null }
        if (parsed.any { it.displayName !in CalcCapabilityPolicy.HNS_REPRESENTABLE_TYPES }) return null
        return parsed.toSet()
    }

    private fun hasAuthoritativeLiveAbility(input: CalcPokemonInput): Boolean =
        input.origin == CalcInputOrigin.LIVE_READ && input.abilityId != null &&
            input.abilityId in 0..com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.ABILITY_ID_MAX &&
            CalcInputField.ABILITY !in input.unknownFields

    private fun abilityObserved(c: Context): Boolean = when (c.side) {
        HnsAbilitySide.ATTACKER -> c.attackerAbilityObserved
        HnsAbilitySide.DEFENDER -> c.defenderAbilityObserved
    }

    private fun liveTypesForSide(c: Context): Set<PokemonType>? = when (c.side) {
        HnsAbilitySide.ATTACKER -> c.attackerTypes
        HnsAbilitySide.DEFENDER -> c.defenderTypes
    }

    private fun validStages(stages: List<Int>?): Boolean =
        stages != null && stages.size == 8 && stages.all { it in -6..6 }

    private fun observedWeatherStateProven(c: Context): Boolean {
        if (!abilityObserved(c)) return false
        if (!c.attackerAbilityObserved || !c.defenderAbilityObserved) return false
        return c.weatherObserved && c.weatherWord != null
    }

    private fun ordinarySunObserved(rawWeather: Int): Boolean =
        rawWeather == com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN_NORMAL

    private data class Proof(
        val relevance: HnsAbilityRequestRelevance,
        val rule: String,
        val source: String,
        val rationale: String
    )

    private fun proof(rule: String, source: String, rationale: String) = Proof(
        HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, rule, source, rationale
    )

    private fun relevant(rule: String, source: String, rationale: String) = Proof(
        HnsAbilityRequestRelevance.RELEVANT, rule, source, rationale
    )

    private fun unknownProof(rule: String, source: String, rationale: String) = Proof(
        HnsAbilityRequestRelevance.UNKNOWN, rule, source, rationale
    )

    private fun singlesProof(c: Context, rule: String, source: String, rationale: String): Proof? =
        if (c.observedBattlersCount == 2) proof(rule, source, rationale) else null

    private fun afterHitSource(id: Int) = when (id) {
        64 -> "src/battle_move_resolution.c:2170"
        124 -> "src/battle_move_resolution.c:3490"
        24 -> "src/battle_util.c:4047"
        160 -> "src/battle_util.c:4048"
        106 -> "src/battle_util.c:4067"
        215 -> "src/battle_util.c:4087"
        152 -> "src/battle_util.c:3972"
        268 -> "src/battle_util.c:3971"
        254 -> "src/battle_util.c:3995"
        221 -> "src/battle_util.c:4034"
        else -> "src/battle_util.c:4231"
    }

    private fun berrySource(id: Int) = when (id) {
        139 -> "src/battle_end_turn.c:1273"
        291 -> "src/battle_end_turn.c:1269"
        else -> "src/battle_script_commands.c:6573"
    }

    private fun speedSource(id: Int) = when (id) {
        33 -> "src/battle_main.c:4946"
        34 -> "src/battle_main.c:4948"
        146 -> "src/battle_main.c:4950"
        202 -> "src/battle_main.c:4952"
        95 -> "src/battle_main.c:4957"
        84 -> "src/battle_main.c:4967"
        else -> "src/battle_main.c:5470"
    }

    private fun statWriterSource(id: Int) = when (id) {
        3 -> "src/battle_util.c:3738"
        22 -> "src/battle_util.c:3451"
        80 -> "data/battle_scripts_1.s:5726"
        83 -> "src/battle_util.c:4022"
        86 -> "src/battle_script_commands.c:7792"
        88 -> "src/battle_util.c:3269"
        128 -> "src/battle_util.c:1275"
        133 -> "src/battle_util.c:3942"
        141 -> "src/battle_util.c:3748"
        153 -> "src/battle_util.c:4553"
        154 -> "src/battle_util.c:3894"
        155 -> "src/battle_util.c:3906"
        172 -> "src/battle_util.c:1205"
        192 -> "src/battle_util.c:3930"
        195 -> "src/battle_util.c:3918"
        201 -> "src/battle_util.c:3865"
        220 -> "src/battle_script_commands.c:14154"
        224 -> "src/battle_util.c:4558"
        234 -> "src/battle_util.c:3488"
        235 -> "src/battle_util.c:3502"
        243 -> "src/battle_util.c:4240"
        264, 265 -> "src/battle_util.c:4579"
        270 -> "src/battle_util.c:4304"
        271 -> "src/battle_util.c:3877"
        275 -> "src/battle_script_commands.c:13365"
        else -> "src/battle_util.c:4648"
    }

    private fun weatherSetterSource(id: Int) = when (id) {
        2 -> "src/battle_util.c:3354"
        45 -> "src/battle_util.c:3368"
        70 -> "src/battle_util.c:3383"
        117 -> "src/battle_util.c:3397"
        else -> "src/battle_util.c:4252"
    }

    private fun terrainSetterSource(id: Int) = when (id) {
        226 -> "src/battle_util.c:3414"
        227 -> "src/battle_util.c:3442"
        228 -> "src/battle_util.c:3433"
        229 -> "src/battle_util.c:3424"
        else -> "src/battle_util.c:4288"
    }

    private fun typeRewriterSource(id: Int) = when (id) {
        16 -> "src/battle_util.c:3850"
        168 -> "src/battle_script_commands.c:944"
        236 -> "src/battle_script_commands.c:944"
        else -> "src/battle_util.c:4896"
    }

    private fun abilityRewriterSource(id: Int) = when (id) {
        36 -> "src/battle_util.c:3097"
        else -> "src/battle_script_commands.c:14126"
    }

    private fun unknown(
        id: Int,
        name: String,
        side: HnsAbilitySide,
        category: HnsAbilityCategory,
        rationale: String = "Required authoritative request operand is missing or the context has no reviewed clearance rule.",
        rule: String? = null,
        source: String? = null
    ) = decision(
        id, name, side, category,
        HnsAbilityRequestRelevance.UNKNOWN,
        rule = rule,
        source = source,
        rationale = rationale
    )

    private fun decision(
        id: Int,
        name: String,
        side: HnsAbilitySide,
        category: HnsAbilityCategory,
        relevance: HnsAbilityRequestRelevance,
        rule: String? = null,
        source: String? = null,
        rationale: String
    ) = HnsAbilityRequestDecision(id, name, side, category, relevance, rule, source, rationale)
}
