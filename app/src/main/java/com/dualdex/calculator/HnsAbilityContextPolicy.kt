package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
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
        55, 62, // Hustle / Guts, Group D Attack stage
        94, 129, 169, 179, 262, 263, 276, 288, 289, // Group D stat stages and field-backed stat modifiers
        89, 91, 96, 97, 101, 110, 111, 116, 136, 137, 138, 173, 174, 178, 182, 184,
        85, 199, 200, 204, 206, 231, 232, 233, 244, 246, 252, 292
        // Normalize / -ate / Liquid Voice, Group D move-type and base-power stage
    )

    fun hasModelledConditionalDamageContext(abilityId: Int?): Boolean =
        abilityId != null && abilityId in MODELLED_CONDITIONAL_DAMAGE_ABILITY_IDS

    data class Context(
        val side: HnsAbilitySide,
        val ordinaryMove: Boolean?,
        val isCrit: Boolean?,
        val attackerAbilityId: Int?,
        val moveType: PokemonType?,
        val moveCategory: MoveCategory?,
        val attackerTypes: Set<PokemonType>?,
        val defenderSpeciesId: Int?,
        val defenderHp: Int?,
        val defenderMaxHp: Int?,
        val attackerStatus1: Int?,
        val moveId: Int? = null,
        val moveBasePower: Int? = null,
        val moveAbilityFlags: Set<String>? = null,
        val unknownMoveAbilityFlags: Set<String>? = null,
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
        val moveAuthority: HnsMoveAuthority? = null
    )

    /** Abilities whose only damage-relevant effect is already reflected in live stat stages. */
    private val LIVE_STAT_STAGE_WRITER_IDS = setOf(
        3, 22, 80, 83, 86, 88, 128, 133, 141, 153, 154, 155, 172, 192, 195, 201,
        220, 224, 234, 235, 243, 264, 265, 270, 271, 275, 290
    )
    private val SPEED_STAGE_WRITER_IDS = setOf(3, 80, 86, 133, 141, 155, 224, 243, 271, 290)

    private val LIVE_RAIN_SUN_SETTER_IDS = setOf(2, 70) // Drizzle / Drought
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
                c.switchInEventsSettled == true && c.ordinaryMove == true && c.observedBattlersCount == 2 &&
                c.attackerAbilityObserved && c.defenderAbilityObserved &&
                validStages(c.attackerStatStages) && validStages(c.defenderStatStages)
            ) {
                if (c.side == HnsAbilitySide.DEFENDER && abilityId in SPEED_STAGE_WRITER_IDS &&
                    c.attackerAbilityId == 148
                ) relevant(
                    "speed_stage_writer_analytic_dependency", "src/battle_util.c:6690",
                    "This defender's speed stage may affect whether the Analytic attacker moves last; that turn-order dependency is not modelled."
                ) else proof(
                    "live_stat_stages_capture_stage_writer", statWriterSource(abilityId),
                    "This ability changes only battle stat stages; both active battlers' exact live stages are supplied to the damage engine."
                )
            } else null
            in LIVE_RAIN_SUN_SETTER_IDS -> if (
                c.switchInEventsSettled == true && weatherSetterStateProven(c)
            ) proof(
                "live_weather_setter_supported_weather", weatherSetterSource(abilityId),
                "Drizzle/Drought only establish ordinary Rain/Sun. The observed unsuppressed weather is passed to the damage engine, which applies those modifiers."
            ) else null
            in LIVE_TYPE_REWRITER_IDS -> if (
                c.switchInEventsSettled == true && c.ordinaryMove == true &&
                c.observedBattlersCount == 2 && abilityObserved(c) &&
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
            247 -> when {
                c.ordinaryMove != true -> null
                c.side == HnsAbilitySide.ATTACKER -> proof(
                    "attacker_ripen_no_current_hit_modifier", "src/battle_util.c:7695, src/battle_util.c:10558",
                    "Ripen's current-hit damage branch reads only the defender ability; its attacker-side Micle branch changes accuracy."
                )
                c.defenderItemId == null -> null
                HnsItemRegistry.classify(c.defenderItemId).data == null ||
                    HnsItemRegistry.classify(c.defenderItemId).category == com.dualdex.pokemon.hns.HnsItemCategory.UNCLASSIFIED -> null
                HnsItemRegistry.classify(c.defenderItemId).data?.holdEffect == "HOLD_EFFECT_RESIST_BERRY" ->
                    relevant(
                        "defender_ripen_resist_berry", "src/battle_util.c:7695",
                        "Ripen quarters damage instead of halving it when the defender's resist berry activates on this hit."
                    )
                else -> proof(
                    "ripen_without_defender_resist_berry", "src/battle_util.c:7695, src/battle_hold_effects.c:847",
                    "The only Ripen branch in current-hit damage is the defender resist-berry modifier; other berry effects occur outside this hit's rolls."
                )
            }
            33, 34, 84, 95, 146, 202, 259 -> when {
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
                c.attackerItemId == null -> unknownProof(
                    "solar_power_attacker_item_unknown", "src/battle_util.c:9530",
                    "IsBattlerWeatherAffected requires the attacker's exact live item to rule out Utility Umbrella."
                )
                c.attackerItemId == HnsItemRegistry.resolveIdByName("Utility Umbrella") -> proof(
                    "solar_power_utility_umbrella", "src/battle_util.c:6999, src/battle_util.c:9527-9531",
                    "Utility Umbrella shields its holder from Sun for this predicate; the item's separate unsupported-item limitation remains independent."
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
                c.attackerItemId == HnsItemRegistry.resolveIdByName("Utility Umbrella") -> proof(
                    "orichalcum_pulse_utility_umbrella", "src/battle_util.c:7106",
                    "The authoritative attacker hold effect is Utility Umbrella, which explicitly disables this ability branch; its general item limitation remains independent."
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
            defenderHp = live?.defenderHp,
            defenderMaxHp = live?.defenderMaxHp,
            attackerStatus1 = live?.attackerStatus1,
            moveId = moveId,
            moveBasePower = pinnedMove?.power,
            moveAbilityFlags = moveId?.let {
                com.dualdex.pokemon.hns.Hns205MoveEffects.abilityMoveFlagsById[it].orEmpty()
            },
            unknownMoveAbilityFlags = moveId?.let {
                com.dualdex.pokemon.hns.Hns205MoveEffects.unknownAbilityMoveFlagsById[it].orEmpty()
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
            moveAuthority = authority
        )
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

    private fun weatherSetterStateProven(c: Context): Boolean {
        if (c.ordinaryMove != true || c.observedBattlersCount != 2 || !abilityObserved(c)) return false
        if (!c.attackerAbilityObserved || !c.defenderAbilityObserved) return false
        val rawWeather = c.weatherWord ?: return false
        val weatherIds = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds
        if (rawWeather != 0 && rawWeather != weatherIds.B_WEATHER_RAIN_NORMAL &&
            rawWeather != weatherIds.B_WEATHER_SUN_NORMAL
        ) return false
        val defenderHp = c.defenderHp ?: return false
        val attackerHp = c.attackerHp ?: return false
        if (attackerHp <= 0 || defenderHp <= 0) return false
        return c.attackerAbilityId !in WEATHER_SUPPRESSOR_IDS &&
            c.defenderAbilityId !in WEATHER_SUPPRESSOR_IDS
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
        else -> "src/battle_util.c:3383"
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
