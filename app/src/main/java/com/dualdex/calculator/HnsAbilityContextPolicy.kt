package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsAbilityAuditData
import com.dualdex.pokemon.hns.HnsAbilityCategory
import com.dualdex.pokemon.hns.HnsItemRegistry

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
    /**
     * The exact pinned SPECIES_TERAPAGOS_TERASTAL, generated from the pinned species header by
     * tools/hns-abilities/generate_hns_ability_audit.py (source-check fails on any drift).
     */
    const val TERAPAGOS_TERASTAL_SPECIES_ID = HnsAbilityAuditData.TERAPAGOS_TERASTAL_SPECIES_ID
    private const val HNS_STATUS1_DEFINED_MASK = 0x1fff
    private const val HNS_STATUS1_ANY_MASK = 0x10ff // pinned STATUS1_ANY, include/constants/battle.h:163
    private val MODELLED_ATTACK_STAT_ABILITY_IDS = setOf(55, 62) // Hustle / Guts, Group D stage 1

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
        val observedBattlersCount: Int?,
        val dynamicMoveTypeKnownNeutral: Boolean,
        val defenderItemId: Int?,
        val defenderTypes: Set<PokemonType>? = null,
        val attackerStatStages: List<Int>? = null,
        val defenderStatStages: List<Int>? = null,
        val attackerAbilityObserved: Boolean = false,
        val defenderAbilityObserved: Boolean = false,
        val attackerHp: Int? = null,
        val defenderAbilityId: Int? = null,
        val weatherWord: Int? = null,
        /** Null/false while the authoritative switch-in/event driver is unread or still pending. */
        val switchInEventsSettled: Boolean? = null
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
        if (entry.category != HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT &&
            !(entry.category == HnsAbilityCategory.MODELLED_HNS_CONDITIONAL && abilityId in MODELLED_ATTACK_STAT_ABILITY_IDS)
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
                c.ordinaryMove != true || c.isCrit == null -> null
                c.side == HnsAbilitySide.DEFENDER || !c.isCrit -> proof(
                    "sniper_without_attacker_critical_hit", "src/battle_util.c:7566",
                    "Sniper changes only the attacker's critical-hit damage; that condition is absent."
                )
                else -> relevant(
                    "sniper_attacker_critical_damage", "src/battle_util.c:7567",
                    "Sniper multiplies this critical hit's damage."
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
            26 -> when (c.side) {
                HnsAbilitySide.ATTACKER -> proof(
                    "attacker_levitate_does_not_change_outgoing_damage", "src/battle_util.c:8385",
                    "Levitate is checked on the defending battler in type effectiveness."
                )
                HnsAbilitySide.DEFENDER -> when {
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
                c.moveType == null || c.attackerTypes == null || !c.dynamicMoveTypeKnownNeutral ||
                    c.observedBattlersCount != 2 -> null
                c.moveType !in c.attackerTypes -> proof(
                    "adaptability_without_stab", "src/battle_util.c:7427",
                    "The observed effective attacker types do not include the effective move type."
                )
                else -> relevant(
                    "adaptability_with_stab", "src/battle_util.c:7427",
                    "Adaptability changes the STAB multiplier for a matching type."
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
        val rawAttackerTypes = live?.attackerTypes
        val rawDefenderTypes = live?.defenderTypes
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
            defenderAbilityId = request.defender.abilityId,
            weatherWord = live?.takeIf { it.weatherObserved }?.weatherWord,
            switchInEventsSettled = live?.switchInEventsSettled
        )
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
