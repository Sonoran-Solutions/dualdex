package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsAbilityCategory

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
 * The registry remains the global capability authority. This policy only clears the single
 * ability blocker when its predicate is proven from request operands, most of which are rebound
 * by CalcRequestBoundary from the exact live battler observations. Anything absent or uncertain
 * returns UNKNOWN.
 */
object HnsAbilityContextPolicy {
    const val TERAPAGOS_TERASTAL_SPECIES_ID = 1432

    data class Context(
        val side: HnsAbilitySide,
        val moveType: PokemonType?,
        val moveCategory: MoveCategory?,
        val attackerTypes: Set<PokemonType>?,
        val defenderSpeciesId: Int?,
        val defenderHp: Int?,
        val defenderMaxHp: Int?,
        val attackerStatus1: Int?,
        val observedBattlersCount: Int?,
        val dynamicMoveTypeKnownNeutral: Boolean
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
        if (abilityId in setOf(4, 75) && c.side == HnsAbilitySide.DEFENDER) {
            // Keep defender-side Armor blocked. The engine result also exposes KO probability;
            // request.isCrit does not establish that critical odds are absent from that result.
            return unknown(
                abilityId, entry.titleCaseName, c.side,
                "Defender critical-hit prevention can change critical probability and KO odds; the current request does not model that distribution.",
                rule = "defender_critical_probability_unmodelled",
                source = "src/battle_util.c:8056"
            )
        }
        val proof: Proof? = when (abilityId) {
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
                c.moveCategory == null || !c.dynamicMoveTypeKnownNeutral -> null
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
                c.moveCategory == null || !c.dynamicMoveTypeKnownNeutral -> null
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
                HnsAbilitySide.DEFENDER -> null
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

    fun contextForRequest(
        request: DamageCalculationRequest,
        side: HnsAbilitySide
    ): Context {
        val live = request.hnsLiveBattleState
        val moveType = PokemonType.fromString(request.moveOverride?.type)
        val rawCategory = request.moveOverride?.category?.lowercase()
        val category = when {
            rawCategory == "status" -> MoveCategory.STATUS
            rawCategory == "physical" -> MoveCategory.PHYSICAL
            rawCategory == "special" -> MoveCategory.SPECIAL
            request.hnsRuntimeRules?.optionStyle == com.dualdex.pokemon.hns.HnsOptionStyle.TYPE_BASED ->
                moveType?.let(::categoryForType)
            else -> null
        }
        val rawAttackerTypes = live?.attackerTypes
        val attackerTypes = rawAttackerTypes?.mapNotNull { PokemonType.fromString(it) }
            ?.takeIf { types -> types.size == rawAttackerTypes.size }?.toSet()
        val dynamicNeutral = live != null && live.dynamicMoveTypeObserved &&
            live.attackerElectrified == false && live.fieldStatuses == 0
        return Context(
            side = side,
            moveType = moveType,
            moveCategory = category,
            attackerTypes = attackerTypes,
            defenderSpeciesId = live?.defenderSpeciesId,
            defenderHp = live?.defenderHp,
            defenderMaxHp = live?.defenderMaxHp,
            attackerStatus1 = live?.attackerStatus1,
            observedBattlersCount = live?.observedBattlersCount,
            dynamicMoveTypeKnownNeutral = dynamicNeutral
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

    private fun categoryForType(type: PokemonType): MoveCategory = when (type) {
        PokemonType.NORMAL, PokemonType.FIGHTING, PokemonType.FLYING, PokemonType.POISON,
        PokemonType.GROUND, PokemonType.ROCK, PokemonType.BUG, PokemonType.GHOST,
        PokemonType.STEEL -> MoveCategory.PHYSICAL
        else -> MoveCategory.SPECIAL
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
