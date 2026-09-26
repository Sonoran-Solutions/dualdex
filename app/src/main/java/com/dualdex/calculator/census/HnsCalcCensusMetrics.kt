package com.dualdex.calculator.census

/**
 * The census' metrics, defined once and in one place.
 *
 * The issue's phrases "battle coverage" and "lead matchup" are ambiguous, so every denominator
 * this census publishes is computed here from an explicit rule and documented in the report.
 */
object HnsCalcCensusMetrics {

    /** Requests in the two generation directions, for the denominator table. */
    fun requestsByDirection(run: HnsCalcCensusEngine.CensusRun): Pair<Int, Int> {
        val referenceToTrainer = run.requests.count {
            it.direction == HnsCalcCensusEngine.DIRECTION_REFERENCE_TO_TRAINER
        }
        return referenceToTrainer to (run.requests.size - referenceToTrainer)
    }

    fun tierCounts(run: HnsCalcCensusEngine.CensusRun): Map<HnsCensusResultTier, Int> =
        HnsCensusResultTier.entries.associateWith { tier ->
            run.requests.count { it.outcome.tier == tier }
        }

    /**
     * The lead-matchup battle metric.
     *
     * **Definition.** For every trainer battle, the trainer lead is the trainer's pinned party
     * slot 0 (the pinned source declares no party pool and no party-index shuffling, and the
     * inventory extractor fails closed if that changes). The matchup is that lead paired with
     * each reference team lead over the eligible damaging moves of the pair, evaluated in both
     * directions. A pair *displays* only when **every** eligible request in it displays.
     *
     * A battle whose lead has no eligible damaging move (a lead whose moves are all status
     * moves, or all outside the ordinary subset) is **excluded** from the lead metric rather
     * than counted as covered or blocked: there is no damage number in question for it. Such a
     * battle is still counted in the trainer-level inventory and in the blocker counts.
     */
    data class LeadMatchup(
        val battlesIncluded: Int,
        val battlesExcludedNoEligibleMove: Int,
        val pairsTotal: Int,
        val pairsDisplayable: Int,
        val requestsTotal: Int,
        val requestsDisplayable: Int,
        /** Per-format split, because the production subset models Singles only. */
        val singlesPairsTotal: Int,
        val singlesPairsDisplayable: Int,
        val doublesPairsTotal: Int,
        val doublesPairsDisplayable: Int
    ) {
        val coveragePercent: Double
            get() = if (pairsTotal == 0) 0.0 else 100.0 * pairsDisplayable / pairsTotal

        val singlesCoveragePercent: Double
            get() = if (singlesPairsTotal == 0) 0.0
            else 100.0 * singlesPairsDisplayable / singlesPairsTotal

        val doublesCoveragePercent: Double
            get() = if (doublesPairsTotal == 0) 0.0
            else 100.0 * doublesPairsDisplayable / doublesPairsTotal
    }

    fun leadMatchup(run: HnsCalcCensusEngine.CensusRun): LeadMatchup {
        val singlesTrainers = run.trainers.filter { it.isSingles }.map { it.key }.toSet()
        val leadRequests = run.requests.filter { it.trainerSlot == 0 }
        val byBattle = leadRequests.groupBy { it.trainerKey }
        var pairsTotal = 0
        var pairsDisplayable = 0
        var requestsTotal = 0
        var requestsDisplayable = 0
        var singlesPairsTotal = 0
        var singlesPairsDisplayable = 0
        var doublesPairsTotal = 0
        var doublesPairsDisplayable = 0
        for ((trainerKey, requests) in byBattle) {
            val singles = trainerKey in singlesTrainers
            val byPair = requests.groupBy { it.referenceTeam }
            for (pair in byPair.values) {
                val displays = pair.all { it.outcome.displayable }
                pairsTotal++
                requestsTotal += pair.size
                requestsDisplayable += pair.count { it.outcome.displayable }
                if (displays) pairsDisplayable++
                if (singles) {
                    singlesPairsTotal++
                    if (displays) singlesPairsDisplayable++
                } else {
                    doublesPairsTotal++
                    if (displays) doublesPairsDisplayable++
                }
            }
        }
        val battlesWithLeadRequests = byBattle.size
        return LeadMatchup(
            battlesIncluded = battlesWithLeadRequests,
            battlesExcludedNoEligibleMove = run.trainers.size - battlesWithLeadRequests,
            pairsTotal = pairsTotal,
            pairsDisplayable = pairsDisplayable,
            requestsTotal = requestsTotal,
            requestsDisplayable = requestsDisplayable,
            singlesPairsTotal = singlesPairsTotal,
            singlesPairsDisplayable = singlesPairsDisplayable,
            doublesPairsTotal = doublesPairsTotal,
            doublesPairsDisplayable = doublesPairsDisplayable
        )
    }
}
