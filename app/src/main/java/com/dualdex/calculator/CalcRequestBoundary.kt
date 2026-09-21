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
            randomTypeEffectivenessEnabled = randomEffectiveness,
            randomAbilitiesEnabled = observedFlag(challengeSettings.txRandomAbilities),
            randomMovesEnabled = observedFlag(challengeSettings.txRandomMoves),
            noEvsEnabled = observedFlag(challengeSettings.txChallengesNoEvs),
            baseStatEqualizerMode = observedRaw(challengeSettings.txChallengesBaseStatEqualizer),
            mirrorEnabled = observedFlag(challengeSettings.txChallengesMirror),
            mirrorThiefEnabled = observedFlag(challengeSettings.txChallengesMirrorThief),
            trainerScalingIvsMode = observedRaw(challengeSettings.txChallengesTrainerScalingIvs),
            trainerScalingEvsMode = observedRaw(challengeSettings.txChallengesTrainerScalingEvs),
            maxPartyIvsMode = observedRaw(challengeSettings.txChallengesMaxPartyIvs),
            sturdyEnabled = observedFlag(challengeSettings.txModeSturdy),
            levelCapMode = observedRaw(challengeSettings.txChallengesLevelCap),
            expMultiplierMode = observedRaw(challengeSettings.txChallengesExpMultiplier),
            legendaryAbilitiesEnabled = observedFlag(challengeSettings.txModeLegendaryAbilities)
        )
    }

    /** The boolean meaning of a 1-bit field, or null when it was unobserved / out of domain. */
    private fun observedFlag(field: com.dualdex.pokemon.hns.HnsChallengeField): Boolean? =
        if (field.observed && !field.outOfDomain) field.observedFlag else null

    /** The raw value of a multi-bit field, or null when it was unobserved / out of domain. */
    private fun observedRaw(field: com.dualdex.pokemon.hns.HnsChallengeField): Int? =
        if (field.observed && !field.outOfDomain) field.raw else null

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
     *
     * [activeBattle] is a HINT, not the authority: [reconcileLiveBattlerItems] treats any supplied
     * runtime observation as evidence that a battle is active, so this function is never called with
     * `activeBattle = false` while an authoritative battler exists. The party-storage branch is
     * therefore reachable only when no battle evidence is present at all.
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
        // Battle context is authoritative runtime provenance, not a caller declaration. A supplied
        // runtime observation is itself evidence that a battle is active, so a LIVE_READ caller
        // cannot pass `activeBattle = false` while handing over a current battler and thereby
        // downgrade to the party-stored item. When no observation exists the flag is the only
        // signal, and `false` then legitimately means the out-of-battle party-storage case.
        val battleContext = activeBattle || playerBattlerState != null || enemyBattlerState != null
        val reconciledAttacker = reconcileParticipantItem(
            participant = request.attacker,
            observation = playerBattlerState,
            liveReadHint = liveReadHint,
            isExactHns = isExactHns,
            isExactVerified = isExactVerified,
            activeBattle = battleContext,
            allowBench = true
        )
        val reconciledDefender = reconcileParticipantItem(
            participant = request.defender,
            observation = enemyBattlerState,
            liveReadHint = liveReadHint,
            isExactHns = isExactHns,
            isExactVerified = isExactVerified,
            activeBattle = battleContext,
            allowBench = false
        )
        return request.copy(
            attacker = reconciledAttacker,
            defender = reconciledDefender
        )
    }

    /**
     * Reconciles a live H&S participant's current HP and status against the authoritative live
     * `gBattleMons[battler].hp` / `.status1` words.
     *
     * Both are live battle state, not stored party snapshots: HP changes every turn and a battler
     * can be poisoned, burned or asleep while the party structure still reads healthy (or vice
     * versa). A caller-crafted `curHP` is rebound whenever the engine HP was read so a favorable
     * crafted value can never satisfy a pinch-ability threshold. For the supported ordinary subset
     * the observed status must be neutral, so a neutral live `status1` clears a stale party status
     * to null; a non-zero live status is left for the policy to refuse precisely (it is never
     * silently converted to a modelled status string).
     */
    private fun reconcileLiveBattlerHpStatus(
        request: DamageCalculationRequest,
        liveReadHint: Boolean,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactHns: Boolean,
        isExactVerified: Boolean,
        activeBattle: Boolean
    ): DamageCalculationRequest {
        if (!isExactHns) return request
        val battleContext = activeBattle || playerBattlerState != null || enemyBattlerState != null
        if (!battleContext) return request

        fun fix(
            participant: CalcPokemonInput,
            observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?
        ): CalcPokemonInput {
            val isLive = participant.origin == CalcInputOrigin.LIVE_READ || liveReadHint
            if (!isLive || !isExactVerified) return participant
            val state = observation?.state ?: return participant
            if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return participant
            if (participant.partySlot == null || state.partySlot == null ||
                state.partySlot != participant.partySlot
            ) {
                return participant
            }
            // Current HP is live battle state, not the stored party snapshot: rebind it from the
            // engine whenever it was read (a caller-crafted curHP must never survive).
            val withHp = if (state.hpObserved && state.hp >= 0) {
                participant.copy(curHP = state.hp)
            } else {
                participant
            }
            // The live status word is the authority for "has a status"; a neutral live word
            // clears a stale party status. A non-zero live word is left for the policy to refuse.
            return if (state.statusObserved && state.status1 == 0) {
                withHp.copy(status = null)
            } else {
                withHp
            }
        }

        return request.copy(
            attacker = fix(request.attacker, playerBattlerState),
            defender = fix(request.defender, enemyBattlerState)
        )
    }

    /**
     * Binds the authoritative live H&S battle state for [request], or null when this is not an
     * active battle.
     *
     * Battle context is authoritative evidence, not a caller declaration: an explicit
     * [activeBattle] hint, or any supplied runtime observation (which is itself evidence a battle
     * is active). Out of battle there is no mutable battle state, so the live-state gate does not
     * apply and null is correct.
     *
     * Only the current effective types have a runtime reader (PR #56); they are bound for a
     * slot-matched, OBSERVED, in-domain observation. Battle stat words, the dynamic move type,
     * transient state, and the runtime move target count have no reader yet, so the corresponding
     * authority flags/count stay unobserved and the policy fails closed until Gap C4b binds them.
     */
    private fun bindHnsLiveBattleState(
        request: DamageCalculationRequest,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        activeBattle: Boolean,
        isExactVerified: Boolean
    ): CalcHnsLiveBattleState? {
        val battleContext = activeBattle || playerBattlerState != null || enemyBattlerState != null
        if (!battleContext) return null
        val attackerRaw = authoritativeObservedRawStats(
            participantPartySlot = request.attacker.partySlot,
            observation = playerBattlerState,
            isExactVerified = isExactVerified
        )
        val defenderRaw = authoritativeObservedRawStats(
            participantPartySlot = request.defender.partySlot,
            observation = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        val attackerStages = authoritativeObservedStatStages(
            participantPartySlot = request.attacker.partySlot,
            observation = playerBattlerState,
            isExactVerified = isExactVerified
        )
        val defenderStages = authoritativeObservedStatStages(
            participantPartySlot = request.defender.partySlot,
            observation = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        val attackerBadges = authoritativeObservedBadgeBoosts(
            participantPartySlot = request.attacker.partySlot,
            observation = playerBattlerState,
            isExactVerified = isExactVerified
        )
        val defenderBadges = authoritativeObservedBadgeBoosts(
            participantPartySlot = request.defender.partySlot,
            observation = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        val attackerHpPair = authoritativeObservedHp(
            participantPartySlot = request.attacker.partySlot,
            observation = playerBattlerState,
            isExactVerified = isExactVerified
        )
        val attackerStatus1 = authoritativeObservedStatus1(
            participantPartySlot = request.attacker.partySlot,
            observation = playerBattlerState,
            isExactVerified = isExactVerified
        )
        val fieldStatuses = authoritativeObservedFieldStatuses(
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        val weather = authoritativeObservedWeather(
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        val defenderSideStatuses = authoritativeObservedDefenderSideStatuses(
            participantPartySlot = request.defender.partySlot,
            observation = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        val attackerElectrified = authoritativeObservedElectrified(
            participantPartySlot = request.attacker.partySlot,
            observation = playerBattlerState,
            isExactVerified = isExactVerified
        )
        val defenderGlaiveRush = authoritativeObservedGlaiveRush(
            participantPartySlot = request.defender.partySlot,
            observation = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        val attackerChargeTimer = authoritativeObservedChargeTimer(
            participantPartySlot = request.attacker.partySlot,
            observation = playerBattlerState,
            isExactVerified = isExactVerified
        )
        val defenderTarShot = authoritativeObservedTarShot(
            participantPartySlot = request.defender.partySlot,
            observation = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        val attackerGimmick = authoritativeObservedGimmick(
            participantPartySlot = request.attacker.partySlot,
            observation = playerBattlerState,
            isExactVerified = isExactVerified
        )
        val defenderGimmick = authoritativeObservedGimmick(
            participantPartySlot = request.defender.partySlot,
            observation = enemyBattlerState,
            isExactVerified = isExactVerified
        )
        return CalcHnsLiveBattleState(
            attackerTypes = authoritativeObservedTypes(
                participantPartySlot = request.attacker.partySlot,
                observation = playerBattlerState,
                isExactVerified = isExactVerified
            ),
            defenderTypes = authoritativeObservedTypes(
                participantPartySlot = request.defender.partySlot,
                observation = enemyBattlerState,
                isExactVerified = isExactVerified
            ),
            attackerBattleStatWordsObserved = attackerRaw != null,
            defenderBattleStatWordsObserved = defenderRaw != null,
            // Dynamic move type (Ion Deluge / Electrify via SetTypeBeforeUsingMove). The C4d
            // audit proved these are the only remaining relevant causes for the ordinary
            // EFFECT_HIT subset; both are now observed. The boolean is true only when BOTH the
            // battle-global field word and the attacker volatile were read; the actual observed
            // values are preserved on the state, so the policy can refuse an active retype.
            dynamicMoveTypeObserved = fieldStatuses != null && attackerElectrified != null,
            // Transient damage state. Glaive Rush (`GetGlaiveRushModifier`) doubles any incoming
            // move; Charge's non-zero `chargeTimer` doubles an Electric move and Tar Shot doubles
            // a Fire move (`src/battle_util.c`). Minimize and the semi-invulnerable states are
            // reachable only through move flags the ordinary allow-list excludes
            // (tools/hns-move-mechanics STATE_DEPENDENT_FLAGS), so no reader is needed for them.
            // The boolean is true only when all three damage-relevant volatiles were read from the
            // same window; a positive value is refused precisely by the policy.
            transientStateObserved = defenderGlaiveRush != null &&
                attackerChargeTimer != null && defenderTarShot != null,
            attackerRawStats = attackerRaw,
            defenderRawStats = defenderRaw,
            attackerStatStages = attackerStages,
            defenderStatStages = defenderStages,
            attackerBadgeBoosts = attackerBadges,
            defenderBadgeBoosts = defenderBadges,
            // `GetMoveTargetCount(ctx)` computed from authoritative gAbsentBattlerFlags,
            // gBattlersCount, and the move's static target class. Only available when both
            // observations are present and the battler indices can be resolved.
            moveTargetCount = authoritativeMoveTargetCount(
                request = request,
                playerBattlerState = playerBattlerState,
                enemyBattlerState = enemyBattlerState,
                isExactVerified = isExactVerified
            ),
            fieldStatuses = fieldStatuses,
            attackerElectrified = attackerElectrified,
            defenderGlaiveRush = defenderGlaiveRush,
            attackerChargeTimer = attackerChargeTimer,
            defenderTarShot = defenderTarShot,
            attackerGimmick = attackerGimmick,
            defenderGimmick = defenderGimmick,
            attackerHp = attackerHpPair?.first,
            attackerMaxHp = attackerHpPair?.second,
            attackerStatus1 = attackerStatus1,
            weatherObserved = weather != null,
            weatherWord = weather ?: 0,
            defenderScreensObserved = defenderSideStatuses != null,
            defenderSideStatuses = defenderSideStatuses ?: 0
        )
    }

    /**
     * Rebinds `field.weather` and `field.defenderSide` from the boundary-owned live observation.
     *
     * In an active exact-H&S battle the field conditions are live state, not user input: a
     * caller-supplied "clear weather / no screens" (or any other value) must not stand in for a
     * word the reader did not deliver. Only the engine's **ordinary** Rain / Sun bits map to the
     * engine's exact weather names; the primal Rain/Sun bits (Primordial Sea / Desolate Land)
     * are deliberately left unmapped so the policy refuses them, and an unobserved word clears
     * the field and refuses via [CalcHnsLiveBattleState.weatherObserved] /
     * [CalcHnsLiveBattleState.defenderScreensObserved]. A word carrying an unmodelled bit
     * (Sand/Hail/Snow/Fog/Strong Winds, the primal weathers, or Aurora Veil and other side
     * statuses) is preserved on the live state so the policy refuses it precisely.
     */
    private fun reconcileLiveFieldConditions(
        request: DamageCalculationRequest,
        live: CalcHnsLiveBattleState?
    ): DamageCalculationRequest {
        if (live == null) return request
        val ids = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds
        val weatherName = when {
            !live.weatherObserved || live.weatherWord == 0 -> null
            live.weatherWord and ids.B_WEATHER_RAIN_NORMAL != 0 -> "Rain"
            live.weatherWord and ids.B_WEATHER_SUN_NORMAL != 0 -> "Sun"
            else -> null
        }
        val defenderSide = if (live.defenderScreensObserved &&
            live.defenderSideStatuses and
            ids.SIDE_STATUS_MODELLED != 0
        ) {
            SideConditions(
                isReflect = live.defenderSideStatuses and ids.SIDE_STATUS_REFLECT != 0,
                isLightScreen = live.defenderSideStatuses and ids.SIDE_STATUS_LIGHTSCREEN != 0
            )
        } else {
            null
        }
        return request.copy(
            field = request.field.copy(weather = weatherName, defenderSide = defenderSide)
        )
    }

    private fun authoritativeObservedRawStats(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): CalcRawStats? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (participantPartySlot == null || state.partySlot == null ||
            state.partySlot != participantPartySlot
        ) {
            return null
        }
        if (!state.statsObserved) return null
        val rawAttack = state.rawAttack ?: return null
        val rawDefense = state.rawDefense ?: return null
        val rawSpeed = state.rawSpeed ?: return null
        val rawSpAttack = state.rawSpAttack ?: return null
        val rawSpDefense = state.rawSpDefense ?: return null
        if (rawAttack <= 0 || rawDefense <= 0 || rawSpeed <= 0 ||
            rawSpAttack <= 0 || rawSpDefense <= 0
        ) {
            return null
        }
        return CalcRawStats(
            attack = rawAttack,
            defense = rawDefense,
            speed = rawSpeed,
            spAttack = rawSpAttack,
            spDefense = rawSpDefense
        )
    }

    private fun authoritativeObservedStatStages(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): List<Int>? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (participantPartySlot == null || state.partySlot == null ||
            state.partySlot != participantPartySlot
        ) {
            return null
        }
        if (!state.stagesObserved) return null
        if (state.statStages.size != 8) return null
        if (state.statStages.any { it !in -6..6 }) return null
        return state.statStages
    }

    private fun authoritativeObservedBadgeBoosts(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): CalcBadgeBoosts? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (participantPartySlot == null || state.partySlot == null ||
            state.partySlot != participantPartySlot
        ) {
            return null
        }
        if (!state.badgesObserved) return null
        return CalcBadgeBoosts(
            atk = state.badgeBoostAtk,
            def = state.badgeBoostDef,
            spe = state.badgeBoostSpe,
            spa = state.badgeBoostSpa,
            spd = state.badgeBoostSpd
        )
    }

    /**
     * The engine's current effective types for one authoritative active battler, or null when they
     * were not observed.
     *
     * The empty-slot sentinel (`TYPE_NONE`) is dropped, never named "Normal"; an out-of-domain
     * value, a non-OBSERVED status, a failed exact-trust check, or a party-slot mismatch yields
     * null (unobserved) rather than a static fallback or a coerced type. A third non-empty type is
     * preserved here so the policy can reject it instead of truncating it.
     */
    private fun authoritativeObservedTypes(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): List<String>? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (participantPartySlot == null || state.partySlot == null ||
            state.partySlot != participantPartySlot
        ) {
            return null
        }
        if (state.typesOutOfDomain) return null
        val names = state.types
            .filter { it.observed && !it.outOfDomain && !it.isTypeNoneSentinel }
            .mapNotNull { it.name }
        return names.ifEmpty { null }
    }

    /**
     * Compute the authoritative runtime target count for an H&S Doubles spread move,
     * equivalent to `GetMoveTargetCount(ctx)` from the pinned source.
     *
     * Every operand is bound to an OBSERVED runtime value; nothing is inferred from
     * the request shape:
     *
     * - exact trust is required;
     * - the request field must be a Doubles battle (static context only — the
     *   observed battler count is never substituted for it or vice versa);
     * - BOTH battle-level observations (player side and enemy side) must be present
     *   with the authoritative battle-level state readable:
     *     * `absentFlagsReadable` on both — a flags value of 0 is only authoritative
     *       when the read actually ran;
     *     * `battlersCountReadable` on both — the observed gBattlersCount is battle
     *       state carried with the observation, never topology inferred from
     *       `field.gameType` (that inference is exactly what the C4c authority rule
     *       forbids);
     *     * the two observations must AGREE on both `absentBattlerFlags` and
     *       `battlersCount` — a single-word disagreement is a torn read and fails
     *       closed;
     *     * the agreed observed count must be exactly 4 (the OBSERVED Doubles value).
     *       A count of 2 means the live battle is not a four-battler doubles battle,
     *       so no spread count can be computed from it — fail closed rather than
     *       trusting the request's game type label.
     *
     * The move's static target class is consumed as the internal
     * [com.dualdex.pokemon.hns.Hns205MoveEffects.SpreadTargetClass] values (exact pinned
     * `enum MoveTarget` numbers): BOTH=6 and FOES_AND_ALLY=11 compute the spread
     * count from the observed absent flags; OPPONENTS_FIELD=13 is always 1. Every
     * other class — TARGET_SELECTED=1, TARGET_RANDOM=5, TARGET_USER=7, ... and any
     * unknown value — fails closed with null: upstream `GetMoveTargetCount` returns
     * `IsBattlerAlive(...)` for those, which requires per-battler HP state this
     * boundary does not read, so it must not fabricate "1".
     *
     * For the supported ordinary EFFECT_HIT subset, the target class is purely static:
     * `GetBattlerMoveTargetType` only adds dynamic overrides for EFFECT_CURSE, terrain,
     * and Tera Starstorm -- none of which apply to EFFECT_HIT.
     */
    private fun authoritativeMoveTargetCount(
        request: DamageCalculationRequest,
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Int? {
        if (!isExactVerified) return null
        if (!request.field.gameType.equals(CalcGameTypes.DOUBLES, ignoreCase = true)) {
            // Singles: no spread reduction possible, count is irrelevant.
            return null
        }

        val playerState = playerBattlerState?.state ?: return null
        val enemyState = enemyBattlerState?.state ?: return null
        if (playerState.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (enemyState.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null

        val attackerBattler = playerState.battlerIndex ?: return null
        val defenderBattler = enemyState.battlerIndex ?: return null

        /* P1 (round 1 + round 2): absent_flags is only authoritative when the native
         * reader actually read the field (absentFlagsReadable); 0 otherwise means
         * 'unreadable', not 'none absent'. Both battle-level observations must agree. */
        if (!playerState.absentFlagsReadable) return null
        if (!enemyState.absentFlagsReadable) return null
        if (playerState.absentBattlerFlags != enemyState.absentBattlerFlags) return null
        val absentFlags = playerState.absentBattlerFlags

        /* P1 (round 2): gBattlersCount is battle-level state carried with each
         * observation, never inferred from field.gameType. Both observations must
         * have READ the count, must agree on it, and the agreed OBSERVED value must
         * be 4 (four-battler doubles). A 2-battler (singles) count or an unreadable
         * count fails closed. */
        if (!playerState.battlersCountReadable) return null
        if (!enemyState.battlersCountReadable) return null
        val battlersCount = playerState.battlersCount
        if (battlersCount != enemyState.battlersCount) return null
        if (battlersCount != 4) return null

        if (attackerBattler !in 0..3 || defenderBattler !in 0..3) return null

        // Get the move's static target class from the pinned source data.
        // For the ordinary EFFECT_HIT subset, the target class is purely static.
        val moveData = com.dualdex.pokemon.hns.HeartAndSoul205DataPack.getMoveByName(request.move.name)
            ?: return null // move not in pinned H&S data
        val moveTargetClass = com.dualdex.pokemon.hns.Hns205MoveEffects.targetClassByMoveId[moveData.id]
            ?: return null // unknown move target class

        // Compute the target count using the same logic as the native
        // pokemon_compute_hns_target_count. The classes are the internal
        // SpreadTargetClass values, carried with the exact pinned `enum MoveTarget`
        // numbers: BOTH=6, FOES_AND_ALLY=11, OPPONENTS_FIELD=13.
        // Unknown/unsupported classes fail closed (null), never a fabricated count.
        val bitFlank = 2
        return when (moveTargetClass) {
            com.dualdex.pokemon.hns.Hns205MoveEffects.SpreadTargetClass.TARGET_BOTH,
            com.dualdex.pokemon.hns.Hns205MoveEffects.SpreadTargetClass.TARGET_FOES_AND_ALLY -> {
                val defPresent = if (absentFlags and (1 shl defenderBattler) == 0) 1 else 0
                val defPartner = defenderBattler xor bitFlank
                val defPartnerPresent = if (defPartner < battlersCount &&
                    absentFlags and (1 shl defPartner) == 0) 1 else 0
                var count = defPresent + defPartnerPresent
                if (moveTargetClass ==
                    com.dualdex.pokemon.hns.Hns205MoveEffects.SpreadTargetClass.TARGET_FOES_AND_ALLY
                ) {
                    val atkPartner = attackerBattler xor bitFlank
                    if (atkPartner < battlersCount &&
                        absentFlags and (1 shl atkPartner) == 0) {
                        count++
                    }
                }
                count
            }
            com.dualdex.pokemon.hns.Hns205MoveEffects.SpreadTargetClass.TARGET_OPPONENTS_FIELD ->
                1 // always 1 (Spikes/Toxic Spikes/Stealth Rock)
            else -> {
                /* Every other class fails closed. TARGET_SELECTED (1),
                 * TARGET_DEPENDS (3), TARGET_OPPONENT (4), TARGET_RANDOM (5),
                 * TARGET_USER (7) and friends dispatch to IsBattlerAlive(...) in
                 * upstream GetMoveTargetCount, which needs per-battler HP state this
                 * boundary does not read. A missing HP read must not be papered over
                 * with "1": null (unobserved) is the only honest answer. This
                 * matches the native reader, whose same classes return 0. */
                null
            }
        }
    }

    /**
     * The battle-global `gFieldStatuses` word, or null unless BOTH authoritative battle-level
     * observations read it and agree.
     *
     * Field statuses are battle-global (the same word for both sides), so disagreement is a torn
     * read and must fail closed rather than pick a side. A read that produced 0 is an observed
     * neutral word, distinct from `null` (never read).
     */
    private fun authoritativeObservedFieldStatuses(
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Int? {
        if (!isExactVerified) return null
        val player = playerBattlerState?.state ?: return null
        val enemy = enemyBattlerState?.state ?: return null
        if (player.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (enemy.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!player.fieldStatusesReadable || !enemy.fieldStatusesReadable) return null
        if (player.fieldStatuses != enemy.fieldStatuses) return null
        return player.fieldStatuses
    }

    /**
     * The battle-global `gBattleWeather` flags word, or null unless BOTH authoritative
     * battle-level observations read it and agree.
     *
     * Weather is battle-global, so a one-sided read is a torn read and must fail closed rather
     * than pick a side. A read that produced 0 is an observed clear weather, distinct from `null`
     * (never read). The boundary maps the word to `field.weather`; the policy refuses an unread
     * word or a bit the ordinary arithmetic does not model.
     */
    private fun authoritativeObservedWeather(
        playerBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        enemyBattlerState: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Int? {
        if (!isExactVerified) return null
        val player = playerBattlerState?.state ?: return null
        val enemy = enemyBattlerState?.state ?: return null
        if (player.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (enemy.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!player.weatherReadable || !enemy.weatherReadable) return null
        if (player.battleWeather != enemy.battleWeather) return null
        return player.battleWeather
    }

    /**
     * The defensive side's `gSideStatuses[side]` word, or null when the slot-matched enemy
     * observation did not read it.
     *
     * The enemy observation is the single authoritative opponent, i.e. the defender in the
     * supported Singles subset, so its own side word is the defender side. Reflect / Light Screen
     * are the only bits the ordinary arithmetic models; the policy refuses any unmodelled bit.
     */
    private fun authoritativeObservedDefenderSideStatuses(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Int? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!slotMatches(participantPartySlot, state)) return null
        if (!state.sideStatusesReadable) return null
        return state.sideStatuses
    }

    /** `gBattleMons[battler].volatiles.electrified`, or null when the slot-matched dev word was not read. */
    private fun authoritativeObservedElectrified(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Boolean? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!slotMatches(participantPartySlot, state)) return null
        if (!state.volatilesObserved) return null
        return state.volatileElectrified
    }

    /** `gBattleMons[battler].volatiles.glaiveRush`, or null when the slot-matched word was not read. */
    private fun authoritativeObservedGlaiveRush(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Boolean? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!slotMatches(participantPartySlot, state)) return null
        if (!state.volatilesObserved) return null
        return state.volatileGlaiveRush
    }

    /**
     * `gBattleMons[battler].volatiles.chargeTimer`, or null when the extended volatile window
     * was not read. `0` is an observed "not charging"; a positive value doubles an Electric move.
     */
    private fun authoritativeObservedChargeTimer(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Int? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!slotMatches(participantPartySlot, state)) return null
        if (!state.transientVolatilesObserved) return null
        return state.volatileChargeTimer
    }

    /**
     * `gBattleMons[battler].volatiles.tarShot`, or null when the extended volatile window was not
     * read. True doubles a Fire move against this battler.
     */
    private fun authoritativeObservedTarShot(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Boolean? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!slotMatches(participantPartySlot, state)) return null
        if (!state.transientVolatilesObserved) return null
        return state.volatileTarShot
    }

    /** `gBattleStruct->gimmick.activeGimmick[side][slot]`, or null when the slot-matched value was not read. */
    private fun authoritativeObservedGimmick(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Int? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!slotMatches(participantPartySlot, state)) return null
        if (!state.gimmickObserved) return null
        if (state.activeGimmick !in 0..5) return null
        return state.activeGimmick
    }

    /** The slot-matched live HP/maxHP pair, or null when either was not read. */
    private fun authoritativeObservedHp(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Pair<Int, Int>? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!slotMatches(participantPartySlot, state)) return null
        if (!state.hpObserved) return null
        if (state.maxHp <= 0) return null
        return state.hp to state.maxHp
    }

    /** The slot-matched live `status1` word, or null when it was not read. 0 is an observed neutral. */
    private fun authoritativeObservedStatus1(
        participantPartySlot: Int?,
        observation: com.dualdex.pokemon.hns.BattlerRuntimeObservation?,
        isExactVerified: Boolean
    ): Int? {
        if (!isExactVerified) return null
        val state = observation?.state ?: return null
        if (state.status != com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!slotMatches(participantPartySlot, state)) return null
        if (!state.statusObserved) return null
        return state.status1
    }

    private fun slotMatches(
        participantPartySlot: Int?,
        state: com.dualdex.pokemon.hns.HnsBattlerRuntimeState
    ): Boolean = participantPartySlot != null && state.partySlot != null &&
        state.partySlot == participantPartySlot

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
        val reconciledStatus = reconcileLiveBattlerHpStatus(
            request = reconciled,
            liveReadHint = liveReadHint,
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            isExactHns = isExactHns,
            isExactVerified = readIsTrusted,
            activeBattle = activeBattle
        )
        val hnsRules = resolveHnsRuntimeRules(profile, trust, challengeSettings)
        val enriched = CalcDataOverrides.enrichRequest(profile, reconciledStatus, hnsRules)
        // Live battle state is boundary-owned exactly like the runtime rules: the caller cannot
        // inject it, and it is re-bound here from the exact-trusted runtime observation. A request
        // in an active H&S battle whose mutable operands (current types, battle stat words, dynamic
        // move type, transient state) are not authoritatively observed gets
        // HNS_LIVE_BATTLE_STATE_NOT_MODELLED rather than a static-operand answer.
        val withLiveState = enriched.copy(
            hnsLiveBattleState = if (isExactHns) {
                bindHnsLiveBattleState(
                    request = reconciledStatus,
                    playerBattlerState = playerBattlerState,
                    enemyBattlerState = enemyBattlerState,
                    activeBattle = activeBattle,
                    isExactVerified = readIsTrusted
                )
            } else {
                null
            }
        )
        // The live field conditions (weather, defender-side screens) are boundary-owned too: in an
        // active battle they are rebound from the observed words so a caller's clear/no-screens
        // default can never stand in for an unobserved live state.
        val liveBound = reconcileLiveFieldConditions(
            request = withLiveState,
            live = withLiveState.hnsLiveBattleState
        )
        // Live provenance is a property of the request. The hint may add it, never remove it.
        val isLiveRead = liveReadHint || liveBound.isFromLiveRead()

        val base = CalcCapabilityPolicy.evaluate(profile, trust, liveBound)

        if (!isLiveRead) {
            val authorised = base.request ?: return CalcRequestOutcome.Refused(base)
            return CalcRequestOutcome.Ready(request = authorised, verdict = base)
        }

        val reasons = LinkedHashSet<CalcLimitation>()

        // (1) Is the read itself trusted? Only an exact-verified ROM makes a live read trusted.
        if (!readIsTrusted) reasons.add(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED)

        // (2) Is the evidence complete? Independent of (1): a trusted ROM does not fill a field the
        // reader never carried, and an untrusted ROM does not make an unknown field less unknown.
        val evidenceIncomplete = liveBound.preparationLimitations.contains(
            CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN
        ) || liveBound.unknownLiveFields().isNotEmpty()
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
