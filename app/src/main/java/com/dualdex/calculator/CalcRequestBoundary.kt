package com.dualdex.calculator

import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust

/**
 * Outcome of building a production damage-calculation request.
 *
 * The boundary either produces a request *and* the verdict that authorises it, or it produces no
 * request at all. There is deliberately no third state, and no path that returns a request without
 * a verdict, so a caller cannot compute damage without also knowing what the result is worth.
 */
sealed class CalcRequestOutcome {

    /** A request may be sent, with [verdict] describing exactly what the result is worth. */
    data class Ready(
        val request: DamageCalculationRequest,
        val verdict: CalcCapabilityVerdict
    ) : CalcRequestOutcome()

    /** No request may be sent. [verdict] is always [CalcSupport.UNSUPPORTED]. */
    data class Refused(val verdict: CalcCapabilityVerdict) : CalcRequestOutcome()
}

/**
 * The production request-building boundary between the companion UI and the embedded calculator.
 *
 * This is the only place that may turn application state into a calculation request. It exists so
 * that the Calc screen, the battle console, and any future caller all make the *same* decision, and
 * so that "unsupported" is produced here rather than being discovered as a wrong number later.
 *
 * The boundary is intentionally thin: it resolves the build's capability row, validates the
 * request against the pinned data for that build, and refuses anything it cannot vouch for. It
 * changes no trust hash and reads no live memory.
 */
object CalcRequestBoundary {

    /**
     * Build the request for a manual calculation on [profile].
     *
     * [request.gen] is advisory: the boundary normalises it to the resolved build's mechanics
     * generation, so a caller's Gen III default can never select another damage pipeline for a
     * modern build. Passing the wrong generation is recorded as a limitation rather than trusted.
     */
    fun build(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        request: DamageCalculationRequest
    ): CalcRequestOutcome {
        val verdict = CalcCapabilityPolicy.evaluate(profile, trust, request)
        val authorised = verdict.request
            ?: return CalcRequestOutcome.Refused(verdict)
        return CalcRequestOutcome.Ready(request = authorised, verdict = verdict)
    }

    /**
     * Build the request from a calculator that observed live game state.
     *
     * A manual (hypothetical) matchup may be calculated against an unverified build, because
     * nothing was read from the running game. A calculation whose inputs came from a live memory
     * read may not: presenting an unverified read as a verified number is exactly the failure this
     * boundary exists to prevent.
     */
    fun build(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        request: DamageCalculationRequest,
        inputsFromLiveRead: Boolean
    ): CalcRequestOutcome {
        val outcome = build(profile, trust, request)
        if (!inputsFromLiveRead) return outcome
        if (outcome is CalcRequestOutcome.Ready && outcome.verdict.isVerified) return outcome

        val verdict = when (outcome) {
            is CalcRequestOutcome.Ready -> outcome.verdict.copy(
                support = CalcSupport.UNSUPPORTED,
                request = null,
                limitations = outcome.verdict.limitations + CalcLimitation.LIVE_INPUTS_NOT_VERIFIED
            )
            is CalcRequestOutcome.Refused -> outcome.verdict.copy(
                limitations = outcome.verdict.limitations + CalcLimitation.LIVE_INPUTS_NOT_VERIFIED
            )
        }
        return CalcRequestOutcome.Refused(verdict)
    }
}
