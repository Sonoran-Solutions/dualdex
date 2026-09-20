package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.pokemon.hns.HnsOptionStyle
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
 * This is the only place that may turn application state into a calculation request. Every public
 * entrypoint funnels into one authorization decision, so a caller cannot get a weaker answer by
 * choosing a different overload.
 *
 * ## What decides "trusted"
 *
 * Reading a value from the game does **not** make it untrusted, and it does not make it complete.
 * Two independent questions are asked, and they produce two independent reasons:
 *
 * | Situation | Treatment |
 * |---|---|
 * | live inputs, ROM not exact-trusted | refused: the *read* is untrusted ([CalcLimitation.LIVE_INPUTS_NOT_VERIFIED]) |
 * | live inputs, exact-trusted ROM, a required field unknown | refused: the *evidence* is incomplete ([CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN]) |
 * | live inputs, exact-trusted ROM, context complete | evaluated on its actual mechanics and inputs - the live source is not itself a reason to refuse |
 * | manual hypothetical | the policy's estimate/verified rules, with assumptions disclosed |
 *
 * Provenance is carried by the request itself ([CalcPokemonInput.origin]), so a caller cannot erase
 * it by picking a different overload; see [isFromLiveRead]. The legacy Boolean may only *add* live
 * provenance, never remove it.
 */
object CalcRequestBoundary {

    /**
     * Build the request for a manual or already-assembled calculation on [profile].
     *
     * Live provenance is read from the request itself. A request whose participants declare
     * [CalcInputOrigin.LIVE_READ] is treated as a live read here even though this overload has no
     * flag, so a caller cannot bypass the live-read rules by calling the three-argument form.
     */
    fun build(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        request: DamageCalculationRequest,
        challengeSettings: HnsChallengeSettingsSnapshot? = null
    ): CalcRequestOutcome = authorize(profile, trust, request, liveReadHint = false, challengeSettings = challengeSettings)

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
        gen: Int = 3,
        challengeSettings: HnsChallengeSettingsSnapshot? = null
    ): CalcRequestOutcome {
        val prepared = CalcInputPreparation.prepare(
            attacker = attacker,
            defender = defender,
            move = move,
            field = field,
            gen = gen
        )
        return authorize(profile, trust, prepared.request, liveReadHint = false, challengeSettings = challengeSettings)
    }

    /**
     * Build the request, with an explicit live-read flag for callers that cannot set
     * [CalcPokemonInput.origin].
     *
     * [inputsFromLiveRead] is a *hint*: it can only add live provenance. Passing `false` for a
     * request whose participants already declare a live read does not make that request manual.
     */
    fun build(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        request: DamageCalculationRequest,
        inputsFromLiveRead: Boolean,
        challengeSettings: HnsChallengeSettingsSnapshot? = null
    ): CalcRequestOutcome = authorize(profile, trust, request, liveReadHint = inputsFromLiveRead, challengeSettings = challengeSettings)

    /**
     * Resolves calculator-owned [CalcHnsRuntimeRules] from [challengeSettings] under strict trust.
     *
     * Trust Contract:
     * 1. Profile must resolve to exact H&S 2.0.5 capability.
     * 2. Runtime trust must be exactRuntimeVerified for this profile.
     * 3. Snapshot must be non-null and have status OBSERVED (OBSERVED_INVALID or UNAVAILABLE fails closed).
     * 4. Each field must be observed and in-domain (outOfDomain fails closed to unknown).
     *
     * Source defaults are NEVER substituted. Non-H&S profiles or unverified trust produce null.
     */
    fun resolveHnsRuntimeRules(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        challengeSettings: HnsChallengeSettingsSnapshot?
    ): CalcHnsRuntimeRules? {
        val capability = CalcCapabilityPolicy.capabilityFor(profile) ?: return null
        if (capability.ruleset != CalcRuleset.HNS_2_0_5) return null
        if (!CalcCapabilityPolicy.isExactRuntimeVerified(profile, trust)) return null
        if (challengeSettings == null || challengeSettings.status != HnsChallengeSettingsStatus.OBSERVED) return null

        val optionStyle = if (challengeSettings.optionStyle.observed && !challengeSettings.optionStyle.outOfDomain) {
            challengeSettings.optionStyleSemantics
        } else {
            HnsOptionStyle.UNAVAILABLE
        }

        val fairy = if (challengeSettings.txModeFairyTypes.observed && !challengeSettings.txModeFairyTypes.outOfDomain) {
            challengeSettings.txModeFairyTypes.observedFlag
        } else {
            null
        }

        val randomTypes = if (challengeSettings.txRandomType.observed && !challengeSettings.txRandomType.outOfDomain) {
            challengeSettings.txRandomType.observedFlag
        } else {
            null
        }

        val randomEffectiveness = if (challengeSettings.txRandomTypeEffectiveness.observed && !challengeSettings.txRandomTypeEffectiveness.outOfDomain) {
            challengeSettings.txRandomTypeEffectiveness.observedFlag
        } else {
            null
        }

        return CalcHnsRuntimeRules(
            optionStyle = optionStyle,
            fairyTypesEnabled = fairy,
            randomTypesEnabled = randomTypes,
            randomTypeEffectivenessEnabled = randomEffectiveness
        )
    }

    /**
     * The single authorization decision every entrypoint above funnels into.
     *
     * Trust and completeness are evaluated separately because they are separate defects with
     * separate fixes: a caller told only "not verified" cannot tell whether it needs a verified ROM
     * or a reader that carries more state.
     */
    private fun authorize(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        request: DamageCalculationRequest,
        liveReadHint: Boolean,
        challengeSettings: HnsChallengeSettingsSnapshot? = null
    ): CalcRequestOutcome {
        val hnsRules = resolveHnsRuntimeRules(profile, trust, challengeSettings)
        val enriched = CalcDataOverrides.enrichRequest(profile, request, hnsRules)
        // Live provenance is a property of the request. The hint may add it, never remove it.
        val isLiveRead = liveReadHint || enriched.isFromLiveRead()

        val base = CalcCapabilityPolicy.evaluate(profile, trust, enriched)

        if (!isLiveRead) {
            val authorised = base.request ?: return CalcRequestOutcome.Refused(base)
            return CalcRequestOutcome.Ready(request = authorised, verdict = base)
        }

        val reasons = LinkedHashSet<CalcLimitation>()

        // (1) Is the read itself trusted? Only an exact-verified ROM makes a live read trusted.
        val readIsTrusted = CalcCapabilityPolicy.isExactRuntimeVerified(profile, trust)
        if (!readIsTrusted) reasons.add(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED)

        // (2) Is the evidence complete? Independent of (1): a trusted ROM does not fill a field the
        // reader never carried, and an untrusted ROM does not make an unknown field less unknown.
        val evidenceIncomplete = request.preparationLimitations.contains(
            CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN
        ) || request.unknownLiveFields().isNotEmpty()
        if (evidenceIncomplete) reasons.add(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)

        return when (val authorised = base.request) {
            // The policy already refused. Keep its reasons and add whichever live-read problems apply.
            null -> CalcRequestOutcome.Refused(
                base.copy(limitations = base.limitations + reasons)
            )
            // The policy authorised it. A live read is only refused for a reason that actually
            // applies: an untrusted read, or missing evidence. With both satisfied the request
            // stands on its own mechanics and inputs, and the live source is not itself a defect.
            else -> if (reasons.isEmpty()) {
                CalcRequestOutcome.Ready(request = authorised, verdict = base)
            } else {
                CalcRequestOutcome.Refused(
                    base.copy(
                        support = CalcSupport.UNSUPPORTED,
                        request = null,
                        limitations = base.limitations + reasons
                    )
                )
            }
        }
    }
}
