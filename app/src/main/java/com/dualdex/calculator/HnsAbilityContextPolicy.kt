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
        val defenderItemId: Int?
    )

    fun assess(abilityId: Int, context: Context?): HnsAbilityRequestDecision {
        val entry = com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(abilityId)
        val side = context?.side ?: HnsAbilitySide.ATTACKER
        if (entry.category != HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT) {
            return decision(
                entry.abilityId ?: abilityId,
                entry.titleCaseName,
                side,
                entry.category,
                HnsAbilityRequestRelevance.UNKNOWN,
                rationale = "Context rules are only evaluated for globally unsupported abilities."
            )
        }

        val c = context ?: return unknown(entry.abilityId ?: abilityId, entry.titleCaseName, side)
        val proof: Proof? = when (abilityId) {
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
                c.attackerStatus1 == 0 -> proof(
                    "guts_attacker_neutral_status", "src/battle_util.c:7056",
                    "The Guts Attack modifier requires STATUS1_ANY; live status1 is observed zero."
                )
                c.attackerStatus1 == null -> null
                else -> relevant(
                    "guts_physical_move_with_status", "src/battle_util.c:7056",
                    "A statused attacker using a physical move receives the Guts modifier."
                )
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
            null -> unknown(abilityId, entry.titleCaseName, c.side)
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
        val attackerTypes = rawAttackerTypes?.mapNotNull { PokemonType.fromString(it) }
            ?.takeIf { types -> types.size == rawAttackerTypes.size }?.toSet()
        return Context(
            side = side,
            ordinaryMove = ordinaryMove,
            isCrit = request.move.isCrit,
            attackerAbilityId = request.attacker.abilityId,
            moveType = authority.effectiveType,
            moveCategory = authority.category,
            attackerTypes = attackerTypes,
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
            }
        )
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

    private fun unknown(
        id: Int,
        name: String,
        side: HnsAbilitySide,
        rationale: String = "Required authoritative request operand is missing or the context has no reviewed clearance rule.",
        rule: String? = null,
        source: String? = null
    ) = decision(
        id, name, side, HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
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
