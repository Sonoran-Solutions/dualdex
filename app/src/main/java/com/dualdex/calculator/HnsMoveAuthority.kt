package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsOptionStyle

/**
 * Which move operands of a live H&S request are authoritative, each with only the evidence it
 * actually needs. Request-local ability, item and field rules consume these instead of a single
 * "everything is neutral" flag, so an unrelated live field bit cannot make an otherwise-proven
 * operand unknown.
 *
 * Pinned sources (`src/battle_main.c`, `src/battle_util.c`):
 *  - [preFieldType]: `GetDynamicMoveType` rewrites an ordinary EFFECT_HIT move only through the
 *    attacker's ability (Normalize, Refrigerate, Pixilate, Aerilate, Liquid Voice, Galvanize) or a
 *    Dynamax/Z gimmick. With an observed GIMMICK_NONE and a known non-rewriting ability it is the
 *    pinned move type.
 *  - [effectiveType]: `SetTypeBeforeUsingMove` then rewrites to Electric only for the attacker's
 *    Electrify volatile or Ion Deluge on a Normal move. No other `gFieldStatuses` bit changes the
 *    type of an ordinary move (Terrain Pulse, Weather Ball, ... are never ordinary). An unknown field
 *    bit is not assumed harmless.
 *  - [category]: `GetBattleMoveCategory` returns the move's own category under PER_MOVE_SPLIT
 *    (only Z/Max moves and the category-swapping effects, none ordinary, change it) and the
 *    effective type's category under TYPE_BASED. It therefore needs the effective type only in
 *    TYPE_BASED mode, and never the field word itself.
 */
data class HnsMoveAuthority(
    val preFieldType: PokemonType?,
    val effectiveType: PokemonType?,
    val category: MoveCategory?
) {
    companion object {
        /** Abilities read by GetDynamicMoveType (`src/battle_main.c:6382-6417`). */
        val TYPE_CHANGING_ABILITY_IDS: Set<Int> = setOf(96, 174, 182, 184, 204, 206)

        val NONE = HnsMoveAuthority(null, null, null)

        fun forRequest(request: DamageCalculationRequest, ordinaryMove: Boolean?): HnsMoveAuthority {
            val live = request.hnsLiveBattleState ?: return NONE
            if (ordinaryMove != true || live.attackerGimmick != 0) return NONE
            val abilityId = request.attacker.abilityId
            val staticType = PokemonType.fromString(request.moveOverride?.type)
            val preFieldType = staticType.takeIf { abilityId != null && abilityId !in TYPE_CHANGING_ABILITY_IDS }
            val field = live.fieldStatuses?.let(HnsFieldState::decode)
            val effectiveType = preFieldType?.takeIf {
                live.attackerElectrified == false && field != null && field.fullyDecoded &&
                    !(field.has(HnsFieldStatus.ION_DELUGE) && it == PokemonType.NORMAL)
            }
            val category = when (request.hnsRuntimeRules?.optionStyle) {
                HnsOptionStyle.PER_MOVE_SPLIT -> when (request.moveOverride?.category?.lowercase()) {
                    "physical" -> MoveCategory.PHYSICAL
                    "special" -> MoveCategory.SPECIAL
                    else -> null
                }
                HnsOptionStyle.TYPE_BASED -> effectiveType?.let(::categoryForType)
                HnsOptionStyle.UNAVAILABLE, null -> null
            }
            return HnsMoveAuthority(preFieldType, effectiveType, category)
        }

        /** `gTypesInfo[type].damageCategory` of the pinned build (TYPE_BASED option style). */
        fun categoryForType(type: PokemonType): MoveCategory = when (type) {
            PokemonType.NORMAL, PokemonType.FIGHTING, PokemonType.FLYING, PokemonType.POISON,
            PokemonType.GROUND, PokemonType.ROCK, PokemonType.BUG, PokemonType.GHOST,
            PokemonType.STEEL -> MoveCategory.PHYSICAL
            else -> MoveCategory.SPECIAL
        }
    }
}
