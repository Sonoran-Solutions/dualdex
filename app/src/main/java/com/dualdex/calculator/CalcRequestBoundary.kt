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
        challengeSettings: HnsChallengeSettingsSnapshot? = null,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        activeBattle: Boolean = false
    ): CalcRequestOutcome = authorize(
        profile = profile,
        trust = trust,
        request = request,
        liveReadHint = false,
        challengeSettings = challengeSettings,
        playerBattlerState = playerBattlerState,
        enemyBattlerState = enemyBattlerState,
        activeBattle = activeBattle
    )

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
        challengeSettings: HnsChallengeSettingsSnapshot? = null,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        activeBattle: Boolean = false
    ): CalcRequestOutcome {
        val prepared = CalcInputPreparation.prepare(
            attacker = attacker,
            defender = defender,
            move = move,
            field = field,
            gen = gen
        )
        return authorize(
            profile = profile,
            trust = trust,
            request = prepared.request,
            liveReadHint = false,
            challengeSettings = challengeSettings,
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            activeBattle = activeBattle
        )
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
        challengeSettings: HnsChallengeSettingsSnapshot? = null,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        activeBattle: Boolean = false
    ): CalcRequestOutcome = authorize(
        profile = profile,
        trust = trust,
        request = request,
        liveReadHint = inputsFromLiveRead,
        challengeSettings = challengeSettings,
        playerBattlerState = playerBattlerState,
        enemyBattlerState = enemyBattlerState,
        activeBattle = activeBattle
    )

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

    private fun reconcileParticipantAbility(
        participant: CalcPokemonInput,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        liveReadHint: Boolean,
        isExactHns: Boolean,
        isExactVerified: Boolean
    ): CalcPokemonInput {
        val isLive = participant.origin == CalcInputOrigin.LIVE_READ || liveReadHint
        if (!isExactHns || !isLive) {
            return participant
        }

        val state = observation?.state
        val identity = observation?.abilityIdentity

        // Central boundary slot provenance binding:
        // A live-read participant under exact H&S must carry partySlot, and it must match the observed battler's partySlot.
        val isSlotMatched = participant.partySlot != null &&
            state?.partySlot != null &&
            state.partySlot == participant.partySlot

        // Direct range check on abilityId against pinned ability domain
        val isDomainValid = state != null &&
            !state.abilityOutOfDomain &&
            state.abilityId != null &&
            state.abilityId in 0..com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.ABILITY_ID_MAX

        val isAuthoritativeValid = isExactVerified &&
            observation != null &&
            state != null &&
            state.status == com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED &&
            isDomainValid &&
            isSlotMatched

        if (state == null || !isAuthoritativeValid) {
            // Authoritative observation is missing, unreadable, invalid, or belongs to a different party slot.
            // A live-read participant cannot claim an ability without an authoritative runtime observation for its slot.
            val newUnknowns = if (participant.unknownFields.contains(CalcInputField.ABILITY)) {
                participant.unknownFields
            } else {
                participant.unknownFields + CalcInputField.ABILITY
            }
            return participant.copy(ability = null, abilityId = null, unknownFields = newUnknowns)
        }

        // Defense-in-depth: verify identity abilityId matches state abilityId
        val authoritativeName = if (state.abilityId == 0) {
            val declared = identity as? com.dualdex.pokemon.DeclaredAbility.Declared
            if (declared != null && declared.abilityId != 0) {
                null // ID mismatch: state is 0 but identity declares non-zero
            } else {
                "None"
            }
        } else {
            val declared = identity as? com.dualdex.pokemon.DeclaredAbility.Declared
            if (declared != null && declared.abilityId == state.abilityId && declared.name.isNotBlank()) {
                com.dualdex.pokemon.hns.HnsAbilityRegistry.canonicalTitleCaseName(declared.name) ?: declared.name
            } else {
                null // missing, blank, or ID mismatch
            }
        }

        if (authoritativeName == null) {
            val newUnknowns = if (participant.unknownFields.contains(CalcInputField.ABILITY)) {
                participant.unknownFields
            } else {
                participant.unknownFields + CalcInputField.ABILITY
            }
            return participant.copy(ability = null, abilityId = null, unknownFields = newUnknowns)
        }

        // Anti-spoofing: authoritative runtime observation wins over any caller-supplied value
        val newUnknowns = participant.unknownFields - CalcInputField.ABILITY
        return participant.copy(
            ability = authoritativeName,
            abilityId = state.abilityId,
            unknownFields = newUnknowns
        )
    }

    private fun reconcileLiveBattlerAbilities(
        request: DamageCalculationRequest,
        liveReadHint: Boolean,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactHns: Boolean,
        isExactVerified: Boolean
    ): DamageCalculationRequest {
        if (!isExactHns) return request
        val reconciledAttacker = reconcileParticipantAbility(
            participant = request.attacker,
            observation = playerBattlerState,
            liveReadHint = liveReadHint,
            isExactHns = isExactHns,
            isExactVerified = isExactVerified
        )
        val reconciledDefender = reconcileParticipantAbility(
            participant = request.defender,
            observation = enemyBattlerState,
            liveReadHint = liveReadHint,
            isExactHns = isExactHns,
            isExactVerified = isExactVerified
        )
        return request.copy(
            attacker = reconciledAttacker,
            defender = reconciledDefender
        )
    }

    /**
     * Reconciles one live H&S participant's held item against the authoritative observation.
     *
     * This is the anti-spoofing / stale-state boundary:
     * - an active-battle participant whose observed party slot matches its own [CalcPokemonInput.partySlot]
     *   takes the ENGINE'S CURRENT item (`gBattleMons[battler].item`), overriding anything the caller
     *   supplied — including a stale nonzero party item while the engine reports ITEM_NONE;
     * - a matching slot whose item read is unreadable/out of domain becomes explicitly unknown, and
     *   never falls back to the party item;
     * - a participant whose observed active slot is a different slot is on the bench and keeps its
     *   exact parsed party item — but only where a bench is meaningful ([allowBench]); an opponent
     *   whose observation names a different slot has no authoritative identity and fails closed;
     * - an active battle in which no single authoritative battler can be established (faint window,
     *   doubles ambiguity, unverified read) becomes unknown, never a party fallback.
     */
    private fun reconcileParticipantItem(
        participant: CalcPokemonInput,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        liveReadHint: Boolean,
        isExactHns: Boolean,
        isExactVerified: Boolean,
        activeBattle: Boolean,
        allowBench: Boolean
    ): CalcPokemonInput {
        val isLive = participant.origin == CalcInputOrigin.LIVE_READ || liveReadHint
        if (!isExactHns || !isLive) return participant

        if (!activeBattle) {
            // Out of battle: there is no current battle item. The parsed party structure is the
            // authoritative stored item, so it keeps party provenance and is not a battle claim.
            if (participant.itemId == null) {
                return participant.copy(
                    itemProvenance = CalcItemProvenance.UNKNOWN,
                    unknownFields = participant.unknownFields + CalcInputField.ITEM
                )
            }
            return participant.copy(
                itemProvenance = CalcItemProvenance.PARTY_STORAGE,
                itemOutOfDomain = false,
                unknownFields = participant.unknownFields - CalcInputField.ITEM
            )
        }

        val state = observation?.state
        val isSlotMatched = participant.partySlot != null &&
            state?.partySlot != null &&
            state.partySlot == participant.partySlot
        val itemDomainValid = state != null && !state.itemOutOfDomain &&
            state.itemId != null && state.itemId in 0..com.dualdex.pokemon.hns.Hns205ItemCatalogue.ITEM_ID_MAX
        val isAuthoritativeValid = isExactVerified &&
            observation != null &&
            state != null &&
            state.status == com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED &&
            isSlotMatched

        if (isAuthoritativeValid) {
            if (!itemDomainValid) {
                return participant.copy(
                    item = null,
                    itemId = null,
                    itemProvenance = CalcItemProvenance.UNKNOWN,
                    itemOutOfDomain = true,
                    unknownFields = participant.unknownFields + CalcInputField.ITEM
                )
            }
            val itemId = state!!.itemId!!
            return participant.copy(
                item = com.dualdex.pokemon.hns.Hns205ItemCatalogue.get(itemId)?.sourceName,
                itemId = itemId,
                itemProvenance = CalcItemProvenance.BATTLE_EFFECTIVE,
                itemOutOfDomain = false,
                unknownFields = participant.unknownFields - CalcInputField.ITEM
            )
        }

        // The observation names a different, authoritatively-known active slot: this participant is
        // on the bench, so the exact parsed party item is the right authority.
        val observedDifferentSlot = state != null &&
            state.status == com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED &&
            state.partySlot != null &&
            participant.partySlot != null &&
            state.partySlot != participant.partySlot
        if (observedDifferentSlot && allowBench) {
            return participant.copy(
                itemProvenance = CalcItemProvenance.PARTY_STORAGE,
                unknownFields = participant.unknownFields - CalcInputField.ITEM
            )
        }

        // No single authoritative active battler: the current item is unreadable. Never resurrect
        // a stale party item here.
        return participant.copy(
            item = null,
            itemId = null,
            itemProvenance = CalcItemProvenance.UNKNOWN,
            itemOutOfDomain = false,
            unknownFields = participant.unknownFields + CalcInputField.ITEM
        )
    }

    private fun reconcileLiveBattlerItems(
        request: DamageCalculationRequest,
        liveReadHint: Boolean,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactHns: Boolean,
        isExactVerified: Boolean,
        activeBattle: Boolean
    ): DamageCalculationRequest {
        if (!isExactHns) return request
        val reconciledAttacker = reconcileParticipantItem(
            participant = request.attacker,
            observation = playerBattlerState,
            liveReadHint = liveReadHint,
            isExactHns = isExactHns,
            isExactVerified = isExactVerified,
            activeBattle = activeBattle,
            allowBench = true
        )
        val reconciledDefender = reconcileParticipantItem(
            participant = request.defender,
            observation = enemyBattlerState,
            liveReadHint = liveReadHint,
            isExactHns = isExactHns,
            isExactVerified = isExactVerified,
            activeBattle = activeBattle,
            allowBench = false
        )
        return request.copy(
            attacker = reconciledAttacker,
            defender = reconciledDefender
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
        challengeSettings: HnsChallengeSettingsSnapshot? = null,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation? = null,
        activeBattle: Boolean = false
    ): CalcRequestOutcome {
        val isExactHns = CalcCapabilityPolicy.capabilityFor(profile)?.ruleset == CalcRuleset.HNS_2_0_5
        val readIsTrusted = CalcCapabilityPolicy.isExactRuntimeVerified(profile, trust)
        val reconciledAbilities = reconcileLiveBattlerAbilities(
            request = request,
            liveReadHint = liveReadHint,
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            isExactHns = isExactHns,
            isExactVerified = readIsTrusted
        )
        val reconciled = reconcileLiveBattlerItems(
            request = reconciledAbilities,
            liveReadHint = liveReadHint,
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            isExactHns = isExactHns,
            isExactVerified = readIsTrusted,
            activeBattle = activeBattle
        )
        val hnsRules = resolveHnsRuntimeRules(profile, trust, challengeSettings)
        val enriched = CalcDataOverrides.enrichRequest(profile, reconciled, hnsRules)
        // Live provenance is a property of the request. The hint may add it, never remove it.
        val isLiveRead = liveReadHint || enriched.isFromLiveRead()

        val base = CalcCapabilityPolicy.evaluate(profile, trust, enriched)

        if (!isLiveRead) {
            val authorised = base.request ?: return CalcRequestOutcome.Refused(base)
            return CalcRequestOutcome.Ready(request = authorised, verdict = base)
        }

        val reasons = LinkedHashSet<CalcLimitation>()

        // (1) Is the read itself trusted? Only an exact-verified ROM makes a live read trusted.
        if (!readIsTrusted) reasons.add(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED)

        // (2) Is the evidence complete? Independent of (1): a trusted ROM does not fill a field the
        // reader never carried, and an untrusted ROM does not make an unknown field less unknown.
        val evidenceIncomplete = enriched.preparationLimitations.contains(
            CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN
        ) || enriched.unknownLiveFields().isNotEmpty()
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
