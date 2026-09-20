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
     * Build the request for one participant pair, prepared through [CalcInputPreparation].
     *
     * This is the overload production callers use, because it starts from the same
     * [CalcParticipantState] values the screen holds rather than from a hand-assembled request. The
     * preparation records what a live read did not carry, and that record travels with the request
     * into [CalcCapabilityPolicy], so a verified hash cannot convert an unobserved status, ability
     * or stat stage into an authoritative one.
     */
    fun build(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        attacker: CalcParticipantState,
        defender: CalcParticipantState,
        move: CalcMoveInput,
        field: CalcFieldInput,
        gen: Int = 3
    ): CalcRequestOutcome {
        val prepared = CalcInputPreparation.prepare(
            attacker = attacker,
            defender = defender,
            move = move,
            field = field,
            gen = gen
        )
        return build(profile, trust, prepared.request, prepared.request.isFromLiveRead())
    }

    /**
     * Build the request from a calculator that observed live game state.
     *
     * A manual (hypothetical) matchup may be calculated against an unverified build, because
     * nothing was read from the running game. A calculation whose inputs came from a live memory
     * read may not: presenting an unverified read as a verified number is exactly the failure this
     * boundary exists to prevent.
     *
     * The reason reported distinguishes an incomplete read from an untrusted one. A read that is
     * missing a damage-relevant field is a different problem from a read whose values are complete
     * but not verified against the running ROM, and a caller that saw only "not verified" for both
     * would have no way to tell which one to fix.
     */
    fun build(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        request: DamageCalculationRequest,
        inputsFromLiveRead: Boolean
    ): CalcRequestOutcome {
        val outcome = build(profile, trust, request)
        if (!inputsFromLiveRead) return outcome

        // Two independent facts, reported as two reasons. A live read is never verified because the
        // values are reads, and it is separately incomplete when the reader could not carry a
        // damage-relevant field. Collapsing them would leave a caller unable to tell "this needs a
        // verified ROM" from "this needs the reader to carry more state".
        val additions = buildList {
            add(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED)
            if (request.preparationLimitations.contains(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)) {
                add(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)
            }
        }

        return when (outcome) {
            // The policy authorised it; a live read alone is enough to refuse that authorisation.
            is CalcRequestOutcome.Ready -> CalcRequestOutcome.Refused(
                outcome.verdict.copy(
                    support = CalcSupport.UNSUPPORTED,
                    request = null,
                    limitations = outcome.verdict.limitations + additions
                )
            )
            // Already refused for its own reason: keep that reason and add the live-read ones.
            is CalcRequestOutcome.Refused -> CalcRequestOutcome.Refused(
                outcome.verdict.copy(limitations = outcome.verdict.limitations + additions)
            )
        }
    }
}
