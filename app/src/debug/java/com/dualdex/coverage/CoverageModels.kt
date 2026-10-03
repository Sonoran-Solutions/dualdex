package com.dualdex.coverage

import com.dualdex.calculator.*
import com.dualdex.pokemon.hns.Hns205ItemCatalogue

internal enum class CoverageTier { EXACT, CAVEATED_ESTIMATE, REFUSED }
internal enum class CoverageFormat { SINGLES, DOUBLES, UNKNOWN }
internal enum class CoverageBattleKind { TRAINER, WILD, UNKNOWN }
internal enum class CoverageToggle { ON, OFF, UNKNOWN }
internal data class CoverageParticipant(val speciesId: Int?, val speciesName: String,
                                        val partySlot: Int?, val engineIndex: Int?)
internal data class CoverageMatchup(val attacker: CoverageParticipant, val defender: CoverageParticipant,
                                    val moveId: Int?, val moveName: String, val direction: String = "PLAYER_TO_ENEMY")
internal data class CoverageContext(val format: CoverageFormat, val battleKind: CoverageBattleKind,
                                    val randomAbilities: CoverageToggle)
internal data class CoverageMechanic(
    val kind: String, val side: String? = null, val id: Int? = null, val name: String,
    val relevance: String? = null, val rule: String? = null, val source: String? = null,
    val disposition: String, val family: String? = null
) {
    // Includes relevance/disposition: a caveat turning into a blocker is preserved independently.
    val identity: String get() = listOf(kind, side, id, name, rule, relevance, disposition).joinToString("|")
}
internal data class CoverageObservation(val tier: CoverageTier, val mechanics: List<CoverageMechanic>) {
    companion object {
        fun from(outcome: CalcRequestOutcome): CoverageObservation {
            val v = when (outcome) {
                is CalcRequestOutcome.Ready -> outcome.verdict
                is CalcRequestOutcome.Refused -> outcome.verdict
            }
            val tier = when {
                outcome is CalcRequestOutcome.Refused || !v.mayRunEngine -> CoverageTier.REFUSED
                v.ignoredMechanics.isNotEmpty() -> CoverageTier.CAVEATED_ESTIMATE
                else -> CoverageTier.EXACT
            }
            val mechanics = buildList {
                v.limitations.distinct().forEach {
                    add(CoverageMechanic("LIMITATION", name = it.name,
                        disposition = if (it in v.blockingLimitations) "BLOCKING" else "NON_BLOCKING"))
                }
                v.hnsAbilityDecisions.forEach { d ->
                    add(CoverageMechanic("ABILITY", d.side.name, d.abilityId, d.abilityName,
                        d.relevance.name, d.rule, d.source, when {
                            v.ignoredMechanics.any { it is IgnoredCalcMechanic.Ability && it.decision == d } -> "CAVEATED"
                            d.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT -> "IRRELEVANT"
                            HnsMechanicAttribution.isModelledAbility(d) -> "MODELLED"
                            else -> "BLOCKING"
                        }))
                }
                v.hnsItemDecisions.forEach { d ->
                    add(CoverageMechanic("ITEM", d.side.name, d.itemId, d.itemName,
                        d.relevance.name, d.rule, d.source, when {
                            v.ignoredMechanics.any { it is IgnoredCalcMechanic.Item && it.decision == d } -> "CAVEATED"
                            d.relevance == HnsItemRequestRelevance.PROVEN_IRRELEVANT -> "IRRELEVANT"
                            HnsMechanicAttribution.isModelledItem(d) -> "MODELLED"
                            else -> "BLOCKING"
                        }, d.itemId?.let { Hns205ItemCatalogue.get(it)?.holdEffect }))
                }
                v.hnsFieldDecisions.forEach { d ->
                    add(CoverageMechanic("FIELD", id = d.status?.ordinal, name = d.label,
                        relevance = d.relevance.name, rule = d.rule, source = d.source, disposition = when {
                            v.ignoredMechanics.any { it is IgnoredCalcMechanic.Field && it.decision == d } -> "CAVEATED"
                            d.relevance == HnsFieldRequestRelevance.PROVEN_IRRELEVANT -> "IRRELEVANT"
                            d.relevance == HnsFieldRequestRelevance.MODELLED -> "MODELLED"
                            else -> "BLOCKING"
                        }))
                }
            }.distinctBy { it.identity }.sortedBy { it.identity }
            return CoverageObservation(tier, mechanics)
        }
    }
}

/** Repair pre-review persisted false blockers without dropping playtest observations. */
internal fun CoverageMechanic.correctModelledDisposition(): CoverageMechanic {
    if (disposition != "BLOCKING") return this
    val modelled = when (kind) {
        "ITEM" -> relevance == HnsItemRequestRelevance.MODELLED.name
        "ABILITY" -> {
            val entry = com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(id)
            HnsMechanicAttribution.isModelledAbility(HnsAbilityRequestDecision(
                id, name, HnsAbilitySide.valueOf(requireNotNull(side)), entry.category,
                HnsAbilityRequestRelevance.valueOf(requireNotNull(relevance)), rule, source, "Persisted coverage attribution"))
        }
        else -> false
    }
    return if (modelled) copy(disposition = "MODELLED") else this
}
