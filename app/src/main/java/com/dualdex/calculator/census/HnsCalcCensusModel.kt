package com.dualdex.calculator.census

import com.dualdex.calculator.CalcCapabilityVerdict
import com.dualdex.calculator.CalcLimitation
import com.dualdex.calculator.CalcRuleset
import com.dualdex.calculator.CalcSupport
import com.dualdex.calculator.HnsAbilityRequestDecision
import com.dualdex.calculator.HnsFieldRequestDecision
import com.dualdex.calculator.HnsItemRequestDecision

/**
 * The three user-facing result tiers the census reports (issue #84).
 *
 * These are deliberately NOT [CalcSupport]. `CalcSupport` is the existing trust/confidence
 * ceiling (`VERIFIED` / `ESTIMATED` / `UNSUPPORTED`), and for H&S it never exceeds
 * `ESTIMATED`. The census tiers answer a different question: **what does the Battle tab
 * actually display for this matchup?**
 *
 * - [FULLY_MODELLED] — a number is displayed and every mechanic that could change it is
 *   modelled. This is the `EXACT` tier of the issue, renamed so it cannot be confused with
 *   the ROM-trust `VERIFIED` claim.
 * - [CAVEATED_ESTIMATE] — a number is displayed while named mechanics are intentionally
 *   ignored. This is the tier produced by Caveat mode when all request limitations have
 *   complete evidence and the policy authorizes the estimate.
 * - [REFUSED] — no number is displayed.
 */
enum class HnsCensusResultTier {
    FULLY_MODELLED,
    CAVEATED_ESTIMATE,
    REFUSED;

    val wireName: String
        get() = when (this) {
            FULLY_MODELLED -> "FULLY_MODELLED"
            CAVEATED_ESTIMATE -> "CAVEATED_ESTIMATE"
            REFUSED -> "REFUSED"
        }
}

/**
 * One structured reason a request could not be fully displayed.
 *
 * [kind] is the stable machine key; [identity] names the ACTUAL mechanic when the production
 * policy reported one (the ability name, the item name, the field condition), so the report
 * ranks "Super Luck" rather than only a generic `HNS_ABILITY_EFFECT_NOT_MODELLED`.
 */
data class HnsCensusBlocker(
    val kind: String,
    val limitation: CalcLimitation,
    /** Side the mechanic belongs to: `attacker`, `defender`, or null when not participant-scoped. */
    val side: String? = null,
    /** The mechanic's own pinned identity, when production reported one. */
    val identity: String? = null,
    /** The production policy's structured relevance verdict, when it produced one. */
    val relevance: String? = null,
    /** The reviewed rule that decided relevance, when production named one. */
    val rule: String? = null
) {
    /** The ranking key: mechanic identity when known, else the limitation code. */
    val rankingKey: String get() = identity ?: limitation.name
}

/**
 * What the census records for one policy evaluation.
 *
 * The raw production verdict facts are preserved verbatim ([support], [limitations]) so a
 * reader can always get from the census back to the policy decision that produced it. A refused
 * request can still carry [ignoredMechanics]: those are complete soft causes that the policy
 * would neutralize, alongside an independent hard blocker that prevents display.
 */
data class HnsCensusOutcome(
    val tier: HnsCensusResultTier,
    val support: CalcSupport,
    val limitations: List<CalcLimitation>,
    val blockers: List<HnsCensusBlocker>,
    val abilityDecisions: List<HnsAbilityRequestDecision>,
    val itemDecisions: List<HnsItemRequestDecision>,
    val fieldDecisions: List<HnsFieldRequestDecision>,
    val ignoredMechanics: List<HnsCensusBlocker> = emptyList()
) {
    val displayable: Boolean get() = tier != HnsCensusResultTier.REFUSED
}

/**
 * Classifies a production verdict into a display tier and its structured blockers.
 *
 * The classification is intentionally a thin, total mapping over the production verdict: it
 * adds no capability opinion of its own. It preserves the production split between blockers and
 * ignored mechanics even when an unrelated hard blocker refuses the request.
 */
object HnsCensusDisplayClassifier {

    fun classify(verdict: CalcCapabilityVerdict): HnsCensusOutcome {
        val limitations = verdict.limitations.distinct()
        val blockers = buildBlockers(verdict)
        val displayable = verdict.mayRunEngine
        val ignoredMechanics = buildIgnoredMechanics(verdict)
        val tier = when {
            !displayable -> HnsCensusResultTier.REFUSED
            ignoredMechanics.isEmpty() -> HnsCensusResultTier.FULLY_MODELLED
            else -> HnsCensusResultTier.CAVEATED_ESTIMATE
        }
        return HnsCensusOutcome(
            tier = tier,
            support = verdict.support,
            limitations = limitations,
            blockers = blockers,
            abilityDecisions = verdict.hnsAbilityDecisions,
            itemDecisions = verdict.hnsItemDecisions,
            fieldDecisions = verdict.hnsFieldDecisions,
            ignoredMechanics = ignoredMechanics
        )
    }

    /**
     * Every production limitation that can stop a number being displayed, with the structured
     * mechanic identity production attached to it.
     */
    private fun buildBlockers(verdict: CalcCapabilityVerdict): List<HnsCensusBlocker> {
        val out = mutableListOf<HnsCensusBlocker>()
        for (limitation in verdict.limitations.distinct()) {
            if (limitation !in verdict.blockingLimitations) continue
            when (limitation) {
                CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED -> {
                    val ignored = verdict.ignoredMechanics
                        .filterIsInstance<com.dualdex.calculator.IgnoredCalcMechanic.Ability>()
                        .map { it.decision }
                    val causal = verdict.hnsAbilityDecisions.filter {
                        it.relevance != com.dualdex.calculator.HnsAbilityRequestRelevance.PROVEN_IRRELEVANT &&
                            it !in ignored
                    }
                    if (causal.isEmpty()) {
                        out += HnsCensusBlocker(limitation.name, limitation)
                    } else {
                        for (decision in causal) {
                            out += HnsCensusBlocker(
                                kind = limitation.name,
                                limitation = limitation,
                                side = decision.side.name.lowercase(),
                                identity = decision.abilityName,
                                relevance = decision.relevance.name,
                                rule = decision.rule
                            )
                        }
                    }
                }

                CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED -> {
                    val ignored = verdict.ignoredMechanics
                        .filterIsInstance<com.dualdex.calculator.IgnoredCalcMechanic.Item>()
                        .map { it.decision }
                    val causal = verdict.hnsItemDecisions.filter {
                        it.relevance != com.dualdex.calculator.HnsItemRequestRelevance.PROVEN_IRRELEVANT &&
                            it.relevance != com.dualdex.calculator.HnsItemRequestRelevance.MODELLED &&
                            it !in ignored
                    }
                    if (causal.isEmpty()) {
                        out += HnsCensusBlocker(limitation.name, limitation)
                    } else {
                        for (decision in causal) {
                            out += HnsCensusBlocker(
                                kind = limitation.name,
                                limitation = limitation,
                                side = decision.side.name.lowercase(),
                                identity = decision.itemName,
                                relevance = decision.relevance.name,
                                rule = decision.rule
                            )
                        }
                    }
                }

                CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED -> {
                    val ignored = verdict.ignoredMechanics
                        .filterIsInstance<com.dualdex.calculator.IgnoredCalcMechanic.Field>()
                        .map { it.decision }
                    val causal = verdict.hnsFieldDecisions.filter {
                        it.relevance != com.dualdex.calculator.HnsFieldRequestRelevance.PROVEN_IRRELEVANT &&
                            it !in ignored
                    }
                    if (causal.isEmpty()) {
                        out += HnsCensusBlocker(limitation.name, limitation)
                    } else {
                        for (decision in causal) {
                            out += HnsCensusBlocker(
                                kind = limitation.name,
                                limitation = limitation,
                                identity = decision.label,
                                relevance = decision.relevance.name,
                                rule = decision.rule
                            )
                        }
                    }
                }

                else -> out += HnsCensusBlocker(limitation.name, limitation)
            }
        }
        return out.distinct().sortedWith(
            compareBy({ it.limitation.ordinal }, { it.side ?: "" }, { it.identity ?: "" }, { it.rule ?: "" })
        )
    }

    private fun buildIgnoredMechanics(verdict: CalcCapabilityVerdict): List<HnsCensusBlocker> =
        verdict.ignoredMechanics.map { mechanic ->
            when (mechanic) {
                is com.dualdex.calculator.IgnoredCalcMechanic.Ability -> HnsCensusBlocker(
                    kind = "ignored_ability",
                    limitation = mechanic.limitation,
                    side = mechanic.decision.side.name.lowercase(),
                    identity = mechanic.decision.abilityName,
                    relevance = mechanic.decision.relevance.name,
                    rule = mechanic.decision.rule
                )
                is com.dualdex.calculator.IgnoredCalcMechanic.Item -> HnsCensusBlocker(
                    kind = "ignored_item",
                    limitation = mechanic.limitation,
                    side = mechanic.decision.side.name.lowercase(),
                    identity = mechanic.decision.itemName,
                    relevance = mechanic.decision.relevance.name,
                    rule = mechanic.decision.rule
                )
                is com.dualdex.calculator.IgnoredCalcMechanic.Field -> HnsCensusBlocker(
                    kind = "ignored_field",
                    limitation = mechanic.limitation,
                    identity = mechanic.decision.label,
                    relevance = mechanic.decision.relevance.name,
                    rule = mechanic.decision.rule
                )
            }
        }.distinct().sortedWith(compareBy({ it.limitation.ordinal }, { it.side ?: "" }, { it.identity ?: "" }))

    /** The ruleset the census is allowed to be measuring; anything else is a harness failure. */
    @JvmField
    val REQUIRED_RULESET: CalcRuleset = CalcRuleset.HNS_2_0_5
}

/** The outcome for one installed ability, attributed from the production evidence. */
internal enum class HnsAbilityTrialDisposition { REFUSED, CAVEATED, CLEAR }

/**
 * Attribute a Random Abilities trial only when its own identity appears in the production
 * request-level blocker or ignored-mechanic evidence. Another limitation may refuse the request
 * while this ability remains a caveat; that ability is therefore CAVEATED, not REFUSED.
 */
internal fun HnsCensusOutcome.abilityTrialDisposition(
    side: String,
    abilityName: String
): HnsAbilityTrialDisposition {
    val normalizedSide = side.lowercase()
    if (blockers.any {
            it.limitation == CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED &&
                it.side == normalizedSide && it.identity == abilityName
        }
    ) return HnsAbilityTrialDisposition.REFUSED

    if (ignoredMechanics.any {
            it.kind == "ignored_ability" &&
                it.limitation == CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED &&
                it.side == normalizedSide && it.identity == abilityName
        }
    ) return HnsAbilityTrialDisposition.CAVEATED

    return HnsAbilityTrialDisposition.CLEAR
}
