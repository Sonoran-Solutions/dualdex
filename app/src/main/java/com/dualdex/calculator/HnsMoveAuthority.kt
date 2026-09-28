package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.pokemon.hns.Hns205MoveEffects
import com.dualdex.pokemon.hns.HnsFairyTypeMappings
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry
import com.dualdex.pokemon.hns.HnsOptionStyle

/** The request-local result of the ability portion of GetDynamicMoveType. */
enum class HnsAbilityTypeRewriteOutcome {
    /** The authoritative attacker ability does not perform a supported move-type rewrite. */
    NOT_USED,

    /** The pinned ability branch ran, including Normalize returning Normal for a Normal move. */
    APPLIED,

    /** The source predicate is known and this move does not satisfy it. */
    PROVEN_NOT_APPLICABLE,

    /** A required source, ability, sound flag, gimmick, or ordinary-move operand is unknown. */
    UNKNOWN
}

/**
 * One pinned H&S move-type decision for an ordinary live request.
 *
 * The source move identity and type always come from the pinned data pack. Caller move JSON never
 * supplies rewrite eligibility. The ordering mirrors the pinned path:
 *
 * `GetMoveType` (including the Fairy-off alternate) → `GetDynamicMoveType` ability rewrite →
 * `SetTypeBeforeUsingMove` Electrify / Ion Deluge rewrite → `GetBattleMoveType` effective type →
 * `GetBattleMoveCategory` category.
 *
 * `ateBoost` is the fact set by `GetDynamicMoveType` before the later field rewrite. It is not
 * inferred from the final type; the base-power ability stage consumes this explicit bit together
 * with the final type, matching `CalcMoveBasePowerAfterModifiers`.
 */
data class HnsMoveAuthority(
    val sourceType: PokemonType?,
    /** Type after the ability rewrite and before Electrify / Ion Deluge. */
    val preFieldType: PokemonType?,
    /** Final type after the later Electrify / Ion Deluge stage. */
    val effectiveType: PokemonType?,
    val category: MoveCategory?,
    val abilityRewriteOutcome: HnsAbilityTypeRewriteOutcome,
    val ateBoost: Boolean?,
    /** Source-derived MoveInfo.soundMove fact shared by Liquid Voice, Group C, and Punk Rock. */
    val soundMove: Boolean?
) {
    companion object {
        /** Attacker abilities handled by this pinned dynamic-type stage. */
        val TYPE_CHANGING_ABILITY_IDS: Set<Int> = setOf(96, 174, 182, 184, 204, 206)

        private val ATE_ABILITY_TYPES = mapOf(
            174 to PokemonType.ICE,      // Refrigerate
            182 to PokemonType.FAIRY,    // Pixilate
            184 to PokemonType.FLYING,   // Aerilate
            206 to PokemonType.ELECTRIC  // Galvanize
        )

        private const val NORMALIZE = 96
        private const val LIQUID_VOICE = 204

        val NONE = HnsMoveAuthority(
            sourceType = null,
            preFieldType = null,
            effectiveType = null,
            category = null,
            abilityRewriteOutcome = HnsAbilityTypeRewriteOutcome.UNKNOWN,
            ateBoost = null,
            soundMove = null
        )

        /**
         * Resolve source identity and the single effective type used by ability, field, category,
         * item, immunity, and engine-input policy. `ordinaryMove` is supplied by the source-backed
         * move-mechanics allow-list; an unsupported move never gains authorization here.
         */
        fun forRequest(request: DamageCalculationRequest, ordinaryMove: Boolean?): HnsMoveAuthority {
            if (request.typeSystem != "hns_2_0_5") return NONE

            val move = HeartAndSoul205DataPack.getMoveByName(request.move.name) ?: return NONE
            if (!HeartAndSoul205DataPack.isMoveAuthoritative(move.id)) return NONE

            val sourceType = sourceMoveType(request, move.type)
            val sourceCategory = move.category
            val sourceSoundMove = soundMove(move.id)
            val live = request.hnsLiveBattleState
            val abilityId = request.attacker.abilityId

            val rewrite = when {
                ordinaryMove != true -> Rewrite(HnsAbilityTypeRewriteOutcome.UNKNOWN, null, null)
                abilityId == null -> Rewrite(HnsAbilityTypeRewriteOutcome.UNKNOWN, null, null)
                live == null || live.attackerGimmick == null || live.attackerGimmick != 0 ->
                    Rewrite(HnsAbilityTypeRewriteOutcome.UNKNOWN, null, null)
                abilityId !in TYPE_CHANGING_ABILITY_IDS ->
                    Rewrite(HnsAbilityTypeRewriteOutcome.NOT_USED, sourceType, false)
                sourceType == null -> Rewrite(HnsAbilityTypeRewriteOutcome.UNKNOWN, null, null)
                abilityId == NORMALIZE ->
                    Rewrite(HnsAbilityTypeRewriteOutcome.APPLIED, PokemonType.NORMAL, true)
                abilityId in ATE_ABILITY_TYPES -> {
                    val target = ATE_ABILITY_TYPES.getValue(abilityId)
                    if (sourceType != PokemonType.NORMAL) {
                        Rewrite(HnsAbilityTypeRewriteOutcome.PROVEN_NOT_APPLICABLE, sourceType, false)
                    } else {
                        // Every ordinary EFFECT_HIT move bypasses TrySetAteType's effect-specific
                        // exclusions. The source target is the pinned ability's exact ate type.
                        Rewrite(HnsAbilityTypeRewriteOutcome.APPLIED, target, true)
                    }
                }
                abilityId == LIQUID_VOICE -> when (sourceSoundMove) {
                    true -> Rewrite(HnsAbilityTypeRewriteOutcome.APPLIED, PokemonType.WATER, false)
                    false -> Rewrite(HnsAbilityTypeRewriteOutcome.PROVEN_NOT_APPLICABLE, sourceType, false)
                    null -> Rewrite(HnsAbilityTypeRewriteOutcome.UNKNOWN, null, null)
                }
                else -> Rewrite(HnsAbilityTypeRewriteOutcome.UNKNOWN, null, null)
            }

            // A null attacker ability cannot prove that a move-type rewrite is absent. A known
            // non-rewriting ability can use the pinned static type, but live field operands are
            // still required before it becomes the final type.
            val preFieldType = when {
                abilityId == null -> null
                rewrite.outcome == HnsAbilityTypeRewriteOutcome.UNKNOWN -> null
                else -> rewrite.type
            }

            val effectiveType = when {
                preFieldType == null || live == null -> null
                live.attackerElectrified == true -> PokemonType.ELECTRIC
                live.attackerElectrified != false -> null
                else -> {
                    val field = live.fieldStatuses?.let(HnsFieldState::decode)
                    if (field == null || !field.fullyDecoded) {
                        null
                    } else if (field.has(HnsFieldStatus.ION_DELUGE) && preFieldType == PokemonType.NORMAL) {
                        PokemonType.ELECTRIC
                    } else {
                        preFieldType
                    }
                }
            }

            val category = when (request.hnsRuntimeRules?.optionStyle) {
                HnsOptionStyle.PER_MOVE_SPLIT -> when (sourceCategory) {
                    MoveCategory.STATUS -> MoveCategory.STATUS
                    MoveCategory.PHYSICAL -> MoveCategory.PHYSICAL
                    MoveCategory.SPECIAL -> MoveCategory.SPECIAL
                }
                HnsOptionStyle.TYPE_BASED -> effectiveType?.let(::categoryForType)
                HnsOptionStyle.UNAVAILABLE, null -> null
            }

            val ateBoost = when {
                rewrite.outcome == HnsAbilityTypeRewriteOutcome.UNKNOWN -> null
                else -> rewrite.ateBoost
            }

            return HnsMoveAuthority(
                sourceType = sourceType,
                preFieldType = preFieldType,
                effectiveType = effectiveType,
                category = category,
                abilityRewriteOutcome = rewrite.outcome,
                ateBoost = ateBoost,
                soundMove = sourceSoundMove
            )
        }

        private data class Rewrite(
            val outcome: HnsAbilityTypeRewriteOutcome,
            val type: PokemonType?,
            val ateBoost: Boolean?
        )

        /** Pinned GetMoveType first applies sFairyMoveAltTypes when the Fairy toggle is OFF. */
        private fun sourceMoveType(
            request: DamageCalculationRequest,
            pinnedType: PokemonType
        ): PokemonType? {
            if (pinnedType != PokemonType.FAIRY) return pinnedType
            return when (request.hnsRuntimeRules?.fairyTypesEnabled) {
                true -> PokemonType.FAIRY
                false -> {
                    val moveName = request.move.name
                    val alt = HnsFairyTypeMappings.getFairyMoveAltType(moveName, true) ?: "Normal"
                    PokemonType.fromString(alt)
                }
                null -> null
            }
        }

        /**
         * `IsSoundMove` reads the same source-derived MoveInfo.soundMove inventory used by Group C.
         * An omitted literal field is false by zero-initialization; conditional/computed fields are
         * represented in `unknownImmunityFlagsById` and must remain unknown.
         */
        private fun soundMove(moveId: Int): Boolean? {
            val flags = Hns205MoveEffects.immunityFlagsById[moveId].orEmpty()
            val unknown = Hns205MoveEffects.unknownImmunityFlagsById[moveId].orEmpty()
            return when {
                "soundMove" in flags -> true
                "soundMove" in unknown -> null
                else -> false
            }
        }

        /** `gTypesInfo[type].damageCategory` from the pinned H&S type table. */
        fun categoryForType(type: PokemonType): MoveCategory = when (type) {
            PokemonType.NORMAL, PokemonType.FIGHTING, PokemonType.FLYING, PokemonType.POISON,
            PokemonType.GROUND, PokemonType.ROCK, PokemonType.BUG, PokemonType.STEEL,
            PokemonType.DARK, PokemonType.STELLAR -> MoveCategory.PHYSICAL
            else -> MoveCategory.SPECIAL
        }

        /** The pinned `TYPE_MYSTERY` entry is exposed by H&S data as `???` and is Special. */
        fun categoryForTypeName(type: String?): MoveCategory? = when {
            type.equals("None", ignoreCase = true) -> MoveCategory.PHYSICAL
            type.equals("???", ignoreCase = true) || type.equals("Mystery", ignoreCase = true) ->
                MoveCategory.SPECIAL
            else -> PokemonType.fromString(type)?.let(::categoryForType)
        }
    }
}
