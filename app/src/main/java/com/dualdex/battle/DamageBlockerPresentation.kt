package com.dualdex.battle

import com.dualdex.calculator.CalcCapabilityVerdict
import com.dualdex.calculator.CalcLimitation
import com.dualdex.calculator.HnsAbilityRequestDecision
import com.dualdex.calculator.HnsAbilityRequestRelevance
import com.dualdex.calculator.HnsAbilitySide
import com.dualdex.calculator.HnsFieldRequestDecision
import com.dualdex.calculator.HnsFieldRequestRelevance
import com.dualdex.calculator.HnsItemRequestDecision
import com.dualdex.calculator.HnsItemRequestRelevance
import com.dualdex.calculator.HnsItemSide
import com.dualdex.pokemon.hns.HnsAbilityCategory
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsItemCategory

/**
 * One reason a Battle-tab damage estimate was refused, kept structured until the move card renders.
 *
 * Every blocker class is retained side by side: an ability blocker never hides an item blocker, and
 * neither hides a move-mechanic or live-state blocker. The global field word, the weather word and a
 * side's status word are separate classes, so a terrain, a weather and a screen are never reported
 * as the same condition. [headline] is the text used when it is the only blocker; [detail] is its
 * compact line in a multi-blocker list.
 */
sealed interface DamageBlockerPresentation {
    val headline: String
    val detail: String

    /**
     * One active live `gFieldStatuses` condition that was not proven irrelevant: a pinned bit, or
     * every bit outside the reviewed pinned mask. [rawWord] is the whole observed word.
     */
    data class Field(val decision: HnsFieldRequestDecision, val rawWord: Int?) : DamageBlockerPresentation {
        override val headline: String
            get() = if (decision.status == null) {
                "Unknown field state ${HnsFieldState.formatMask(decision.rawMask)}"
            } else {
                "${decision.label} not modelled"
            }

        /** The condition with its own observed mask, e.g. `Electric Terrain (0x00000100)`. */
        val condition: String
            get() = if (decision.status == null) {
                "Unknown bits ${HnsFieldState.formatMask(decision.rawMask)}"
            } else {
                "${decision.label} (${HnsFieldState.formatMask(decision.rawMask)})"
            }
        override val detail: String get() = "Field: $condition"
    }

    /** The live `gBattleWeather` word carries a weather the calculator does not model. */
    data class Weather(val word: Int?, val limitations: List<CalcLimitation>) : DamageBlockerPresentation {
        override val headline: String get() = "Weather not modelled" + word.hex(4)
        override val detail: String get() = "Weather: not modelled" + word.hex(4)
    }

    /** The defender's live `gSideStatuses` word carries a side condition other than Reflect/Light Screen. */
    data class SideStatus(val word: Int?, val limitations: List<CalcLimitation>) : DamageBlockerPresentation {
        override val headline: String get() = "Foe side status not modelled" + word.hex(8)
        override val detail: String get() = "Foe side: not modelled" + word.hex(8)
    }

    /** A globally unsupported/unresolved effective ability that was not proven irrelevant. */
    data class Ability(val decision: HnsAbilityRequestDecision) : DamageBlockerPresentation {
        private val attacker get() = decision.side == HnsAbilitySide.ATTACKER
        override val headline: String
            get() = "${possessive(attacker)} ${decision.abilityName} " +
                auditStatus(decision.globalCategory == HnsAbilityCategory.UNCLASSIFIED)
        override val detail: String get() = "${owner(attacker)}: ${decision.abilityName}"
    }

    /** A globally unsupported/unresolved live held item that was not proven irrelevant. */
    data class Item(val decision: HnsItemRequestDecision) : DamageBlockerPresentation {
        private val attacker get() = decision.side == HnsItemSide.ATTACKER
        override val headline: String
            get() = "${possessive(attacker)} ${decision.itemName} " +
                auditStatus(decision.globalCategory == HnsItemCategory.UNCLASSIFIED)
        override val detail: String get() = "${owner(attacker)}: ${decision.itemName}"
    }

    /** A move or mechanic the calculator does not model (move effect, item-dependent move, ...). */
    data class Mechanic(val reason: String, val limitations: List<CalcLimitation>) : DamageBlockerPresentation {
        override val headline: String get() = reason
        override val detail: String get() = reason
    }

    /** Live battle state that is missing or outside the modelled shape (Doubles, unread words, ...). */
    data class State(val reason: String, val limitations: List<CalcLimitation>) : DamageBlockerPresentation {
        override val headline: String get() = reason
        override val detail: String get() = reason
    }

    companion object {
        private fun Int?.hex(digits: Int) = this?.let { " (0x%0${digits}X)".format(it) }.orEmpty()
        private fun possessive(attacker: Boolean) = if (attacker) "Your" else "Foe's"
        private fun owner(attacker: Boolean) = if (attacker) "You" else "Foe"
        private fun auditStatus(unclassified: Boolean) = if (unclassified) "not yet audited" else "not modelled"

        /**
         * All blockers of a refused verdict, ordered State, Field, Weather, SideStatus, Ability, Item,
         * Mechanic.
         *
         * Only blocking limitations are listed. Ability and item limitations are represented by their
         * structured decisions; the generic label is used only when no structured decision exists
         * (for example the pinch-ability path), so no limitation is dropped.
         */
        fun from(verdict: CalcCapabilityVerdict, observedDoubles: Boolean): List<DamageBlockerPresentation> {
            val limitations = verdict.limitations.distinct()
            val blocking = limitations.filter { it.blocksCalculation }.toMutableSet()
            val states = mutableListOf<DamageBlockerPresentation>()
            val mechanics = mutableListOf<DamageBlockerPresentation>()

            fun take(set: Set<CalcLimitation>): List<CalcLimitation> =
                blocking.filter { it in set }.also { blocking.removeAll(it.toSet()) }

            val doubles = take(setOf(CalcLimitation.HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED))
            val format = take(setOf(CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED))
            if (doubles.isNotEmpty() || observedDoubles) {
                states += State("Doubles not supported", doubles + format)
            } else if (format.isNotEmpty()) {
                states += State("Live battle format not supported", format)
            }
            take(
                setOf(
                    CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN,
                    CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED,
                    CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE,
                    CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE,
                    CalcLimitation.HNS_LIVE_WEATHER_UNKNOWN,
                    CalcLimitation.HNS_LIVE_SCREENS_UNKNOWN,
                    CalcLimitation.HNS_GIMMICK_STATE_UNREADABLE
                )
            ).takeIf { it.isNotEmpty() }?.let { states += State("Live battle state incomplete", it) }
            take(setOf(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE))
                .takeIf { it.isNotEmpty() }?.let { states += State("Move category unavailable", it) }
            take(
                setOf(
                    CalcLimitation.FAIRY_TOGGLE_UNREADABLE,
                    CalcLimitation.RANDOM_TYPES_UNREADABLE,
                    CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE,
                    CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED,
                    CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED
                )
            ).takeIf { it.isNotEmpty() }?.let { states += State("Challenge setting not modelled", it) }
            take(setOf(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED, CalcLimitation.STATUS_NOT_MODELLED))
                .takeIf { it.isNotEmpty() }?.let { states += State("Status not modelled", it) }
            take(setOf(CalcLimitation.FIELD_CONDITION_NOT_MODELLED))
                .takeIf { it.isNotEmpty() }?.let { states += State("Weather/terrain input not modelled", it) }

            // Live field word: one blocker per active condition that was not proven irrelevant, named
            // from the pinned bit table and carrying its observed mask. Ion Deluge on a Normal move is
            // refused through the active dynamic-type limitation, which it then explains, unless the
            // attacker is also Electrified (that cause keeps its own blocker).
            val diagnostics = verdict.hnsFieldDiagnostics
            val rawField = diagnostics?.fieldState?.raw
            val fieldDecisions = verdict.hnsFieldDecisions
                .filter { it.relevance != HnsFieldRequestRelevance.PROVEN_IRRELEVANT }
            val fields = mutableListOf<DamageBlockerPresentation>()
            val ionDeluge = fieldDecisions.filter {
                it.status == HnsFieldStatus.ION_DELUGE && it.relevance == HnsFieldRequestRelevance.RELEVANT
            }
            if (ionDeluge.isNotEmpty() && CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED in blocking) {
                fields += ionDeluge.map { Field(it, rawField) }
                if (diagnostics?.attackerElectrified == false) {
                    blocking.remove(CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED)
                }
            }
            val fieldLimitation = take(setOf(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
            val fieldBlockers = fieldDecisions.filter { it !in ionDeluge }.map { Field(it, rawField) }
            fields += fieldBlockers
            if (fieldLimitation.isNotEmpty() && fieldBlockers.isEmpty()) {
                states += State("Field condition not modelled", fieldLimitation)
            }
            val weather = take(setOf(CalcLimitation.HNS_LIVE_WEATHER_NOT_MODELLED))
            if (weather.isNotEmpty()) fields += Weather(diagnostics?.weatherWord, weather)
            val side = take(setOf(CalcLimitation.HNS_LIVE_SIDE_STATUS_NOT_MODELLED))
            if (side.isNotEmpty()) fields += SideStatus(diagnostics?.defenderSideStatuses, side)
            take(
                setOf(
                    CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_GLAIVE_RUSH_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_CHARGE_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_TAR_SHOT_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_FORESIGHT_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_MIRACLE_EYE_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_ROOST_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED,
                    CalcLimitation.HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_ENDURED_ACTIVE_NOT_MODELLED,
                    CalcLimitation.HNS_GIMMICK_ACTIVE_NOT_MODELLED
                )
            ).takeIf { it.isNotEmpty() }?.let { states += State("Battle effect not modelled", it) }
            take(setOf(CalcLimitation.BADGE_BOOST_NOT_MODELLED))
                .takeIf { it.isNotEmpty() }?.let { states += State("Badge boost not modelled", it) }
            take(setOf(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED))
                .takeIf { it.isNotEmpty() }?.let { states += State("ROM not verified", it) }

            // When a participant's live observation is missing, the unread words also surface as
            // format/status/field/badge limitations. Those are one cause, so they are shown as the
            // single live-state blocker (its limitations are all retained) rather than as a list of
            // symptoms. Ability, item and move blockers are never folded.
            if (CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN in limitations) {
                val folded = states.filterIsInstance<State>().filter { it.reason != "Doubles not supported" }
                if (folded.size > 1) {
                    states.removeAll(folded)
                    states += State("Live battle state incomplete", folded.flatMap { it.limitations })
                }
            }

            val abilities = verdict.hnsAbilityDecisions
                .filter { it.relevance != HnsAbilityRequestRelevance.PROVEN_IRRELEVANT }
                .map(::Ability)
            val abilityLimitation = take(setOf(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
            if (abilityLimitation.isNotEmpty() && abilities.isEmpty()) {
                mechanics += Mechanic("Ability effect not modelled", abilityLimitation)
            }
            val items = verdict.hnsItemDecisions
                .filter {
                    it.relevance != HnsItemRequestRelevance.PROVEN_IRRELEVANT &&
                        it.relevance != HnsItemRequestRelevance.MODELLED
                }
                .map(::Item)
            val itemLimitation = take(setOf(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
            if (itemLimitation.isNotEmpty() && items.isEmpty()) {
                mechanics += Mechanic("Item effect not modelled", itemLimitation)
            }
            take(setOf(CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE))
                .takeIf { it.isNotEmpty() }?.let { mechanics += Mechanic("Item identity not authoritative", it) }
            take(
                setOf(
                    CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED,
                    CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED
                )
            ).takeIf { it.isNotEmpty() }?.let { mechanics += Mechanic("Move effect not modelled", it) }
            if (blocking.isNotEmpty()) {
                mechanics += Mechanic("Damage interaction not modelled", blocking.toList())
            }

            val all = states + fields + abilities + items + mechanics
            return all.ifEmpty { listOf(Mechanic("Damage interaction not modelled", limitations)) }
        }

        /** The compact one-line reason: the only blocker's headline, or a typed count. */
        fun headline(blockers: List<DamageBlockerPresentation>): String? = when {
            blockers.isEmpty() -> null
            blockers.size == 1 -> blockers.single().headline
            blockers.all { it is Ability } -> "${blockers.size} ability blockers"
            blockers.all { it is Item } -> "${blockers.size} item blockers"
            blockers.all { it is Field } -> "${blockers.size} field blockers"
            else -> "${blockers.size} blockers"
        }

        /**
         * The lines shown under the headline. A multi-blocker list gets one line per blocker (field
         * lines drop their `Field:` prefix when every blocker is a field condition). A single field
         * blocker still gets its line, so the observed mask is always on screen.
         */
        fun detailLines(blockers: List<DamageBlockerPresentation>): List<String> = when {
            blockers.size == 1 -> listOfNotNull((blockers.single() as? Field)?.detail)
            blockers.all { it is Field } -> blockers.map { (it as Field).condition }
            else -> blockers.map { it.detail }
        }

        /** The complete move-card text for a refusal with these blockers. */
        fun unavailableText(blockers: List<DamageBlockerPresentation>, fallbackReason: String?): String {
            val headline = headline(blockers) ?: fallbackReason
            val lines = detailLines(blockers)
            return (listOf("Damage unavailable" + headline?.let { " · $it" }.orEmpty()) + lines).joinToString("\n")
        }
    }
}
