package com.dualdex.calculator

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsAbilityCategory
import com.dualdex.pokemon.hns.HnsAbilityRegistry
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.pokemon.hns.HnsOptionStyle
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcHnsAbilityTest {

    private val hnsSha256 = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"

    private val heartAndSoul = RomHackProfile(
        id = "pokemon_heart_and_soul",
        name = "Pokemon Heart & Soul 2.0.5",
        baseGame = "Emerald",
        gameId = 3,
        engine = "pokeemerald-expansion",
        gameDataPackId = "hns_2_0_5",
        sha256Hashes = listOf(hnsSha256),
        isVerified = true,
        memoryLayoutVerified = true,
        hasPhysSpecSplit = true
    )

    private val fireRed = RomHackProfile(
        id = "firered_vanilla",
        name = "Pokemon FireRed",
        baseGame = "FireRed",
        gameId = 2,
        engine = "Vanilla",
        gameDataPackId = "gen3_vanilla",
        sha256Hashes = listOf("b123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
        isVerified = true,
        memoryLayoutVerified = true,
        hasPhysSpecSplit = false
    )

    private fun exactTrust(profile: RomHackProfile): RuntimeRomTrust {
        val hash = profile.sha256Hashes.first()
        return RuntimeRomTrust.from(
            compatibility = com.dualdex.romhack.RomCompatibility.verified(
                profile = profile,
                sha256 = hash
            ),
            activeRomSha256 = hash
        )
    }

    private fun hnsSettingsSnapshot(
        status: HnsChallengeSettingsStatus = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle: Int = 0,
        fairyTypes: Int = 1,
        randomTypes: Int = 0,
        randomEffectiveness: Int = 0
    ): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = status,
        optionStyle = HnsChallengeField(observed = true, raw = optionStyle, outOfDomain = false),
        txModeFairyTypes = HnsChallengeField(observed = true, raw = fairyTypes, outOfDomain = false),
        txRandomType = HnsChallengeField(observed = true, raw = randomTypes, outOfDomain = false),
        txRandomTypeEffectiveness = HnsChallengeField(observed = true, raw = randomEffectiveness, outOfDomain = false)
    )

    private fun battlerObservation(
        status: HnsBattlerRuntimeStatus = HnsBattlerRuntimeStatus.OBSERVED,
        partySlot: Int = 0,
        abilityId: Int = 62, // GUTS
        abilityOutOfDomain: Boolean = false,
        declaredName: String = "Guts"
    ): BattlerRuntimeObservation = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = status,
            battlerIndex = 0,
            partySlot = partySlot,
            abilityId = abilityId,
            abilityOutOfDomain = abilityOutOfDomain,
            types = emptyList()
        ),
        abilityIdentity = if (abilityId == 0) {
            DeclaredAbility.EmptySlot
        } else {
            DeclaredAbility.Declared(abilityId = abilityId, name = declaredName)
        }
    )

    private fun dummyParsedPokemon(species: Int = 68): ParsedPokemon = ParsedPokemon(
        isValid = true,
        isEmpty = false,
        pid = 123456L,
        tid = 1000,
        sid = 2000,
        nickname = "Machamp",
        otName = "Red",
        species = species,
        heldItem = 0,
        level = 50,
        nature = 0,
        natureName = "Hardy",
        isShiny = false,
        abilitySlot = 0,
        isEgg = false,
        friendship = 255,
        experience = 100000L,
        hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(223, 0, 0, 0),
        pp = intArrayOf(5, 0, 0, 0),
        currentHp = 165,
        maxHp = 165,
        attack = 100,
        defense = 100,
        speed = 100,
        spAttack = 100,
        spDefense = 100,
        statusCondition = 0L
    )

    // ---------------------------------------------------------------------
    // 1. HnsAbilityRegistry classification & naming tests
    // ---------------------------------------------------------------------

    @Test
    fun `HnsAbilityRegistry classifies proven zero damage effect abilities`() {
        val none = HnsAbilityRegistry.classify(0)
        assertEquals(HnsAbilityCategory.PROVEN_NO_DAMAGE_EFFECT, none.category)
        assertTrue(none.category.isSupportedForDamage)
        assertEquals("None", HnsAbilityRegistry.canonicalTitleCaseName(0))

        val keenEye = HnsAbilityRegistry.classify(51)
        assertEquals(HnsAbilityCategory.PROVEN_NO_DAMAGE_EFFECT, keenEye.category)
        assertTrue(keenEye.category.isSupportedForDamage)
        assertEquals("Keen Eye", HnsAbilityRegistry.canonicalTitleCaseName(51))

        val insomnia = HnsAbilityRegistry.classify(15)
        assertEquals(HnsAbilityCategory.PROVEN_NO_DAMAGE_EFFECT, insomnia.category)
        assertTrue(insomnia.category.isSupportedForDamage)
        assertEquals("Insomnia", HnsAbilityRegistry.canonicalTitleCaseName(15))
    }

    @Test
    fun `HnsAbilityRegistry classifies Guts, Thick Fat, Huge Power, Pure Power as temporarily unsupported`() {
        val guts = HnsAbilityRegistry.classify(62)
        assertEquals(HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT, guts.category)
        assertFalse(guts.category.isSupportedForDamage)
        assertEquals("Guts", HnsAbilityRegistry.canonicalTitleCaseName(62))

        val thickFat = HnsAbilityRegistry.classify(47)
        assertEquals(HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT, thickFat.category)
        assertFalse(thickFat.category.isSupportedForDamage)
        assertEquals("Thick Fat", HnsAbilityRegistry.canonicalTitleCaseName(47))

        val hugePower = HnsAbilityRegistry.classify(37)
        assertEquals(HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT, hugePower.category)
        assertFalse(hugePower.category.isSupportedForDamage)
        assertEquals("Huge Power", HnsAbilityRegistry.canonicalTitleCaseName(37))

        val purePower = HnsAbilityRegistry.classify(74)
        assertEquals(HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT, purePower.category)
        assertFalse(purePower.category.isSupportedForDamage)
        assertEquals("Pure Power", HnsAbilityRegistry.canonicalTitleCaseName(74))
    }

    @Test
    fun `HnsAbilityRegistry classifies starter pinch and unmodelled abilities as unsupported`() {
        // Starters pinch abilities modify Attack stat in H&S vs Base Power in ADV
        listOf(65, 66, 67, 68).forEach { starterAbilityId ->
            val entry = HnsAbilityRegistry.classify(starterAbilityId)
            assertEquals(
                "Ability $starterAbilityId must be UNSUPPORTED_DAMAGE_RELEVANT",
                HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
                entry.category
            )
            assertFalse(entry.category.isSupportedForDamage)
        }

        // Modern ability (e.g. Adaptability = 91)
        val adaptability = HnsAbilityRegistry.classify(91)
        assertEquals(HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT, adaptability.category)
        assertFalse(adaptability.category.isSupportedForDamage)
    }

    // ---------------------------------------------------------------------
    // 2. resolveEffectiveAbility presenter tests
    // ---------------------------------------------------------------------

    @Test
    fun `resolveEffectiveAbility returns ObservedAbility on matching slot and observed status`() {
        val obs = battlerObservation(partySlot = 2, abilityId = 51, declaredName = "Keen Eye")
        val resolution = CalcParticipantPresenter.resolveEffectiveAbility(
            observation = obs,
            expectedPartySlot = 2,
            isExactHns = true
        )
        assertTrue(resolution is EffectiveAbilityResolution.ObservedAbility)
        val observed = resolution as EffectiveAbilityResolution.ObservedAbility
        assertEquals("Keen Eye", observed.name)
        assertEquals(51, observed.abilityId)
    }

    @Test
    fun `resolveEffectiveAbility returns ObservedNone when abilityId is zero`() {
        val obs = battlerObservation(partySlot = 0, abilityId = 0)
        val resolution = CalcParticipantPresenter.resolveEffectiveAbility(
            observation = obs,
            expectedPartySlot = 0,
            isExactHns = true
        )
        assertEquals(EffectiveAbilityResolution.ObservedNone, resolution)
    }

    @Test
    fun `resolveEffectiveAbility fails closed to UnknownAbility on party slot mismatch`() {
        val obs = battlerObservation(partySlot = 1, abilityId = 51)
        val resolution = CalcParticipantPresenter.resolveEffectiveAbility(
            observation = obs,
            expectedPartySlot = 0,
            isExactHns = true
        )
        assertEquals(EffectiveAbilityResolution.UnknownAbility, resolution)
    }

    @Test
    fun `resolveEffectiveAbility fails closed to UnknownAbility on non-OBSERVED status`() {
        listOf(
            HnsBattlerRuntimeStatus.UNAVAILABLE,
            HnsBattlerRuntimeStatus.AMBIGUOUS,
            HnsBattlerRuntimeStatus.OBSERVED_INVALID
        ).forEach { status ->
            val obs = battlerObservation(status = status, partySlot = 0, abilityId = 51)
            val resolution = CalcParticipantPresenter.resolveEffectiveAbility(
                observation = obs,
                expectedPartySlot = 0,
                isExactHns = true
            )
            assertEquals("Status $status must fail closed to UnknownAbility", EffectiveAbilityResolution.UnknownAbility, resolution)
        }
    }

    @Test
    fun `resolveEffectiveAbility fails closed to UnknownAbility when abilityOutOfDomain is true`() {
        val obs = battlerObservation(partySlot = 0, abilityId = 999, abilityOutOfDomain = true)
        val resolution = CalcParticipantPresenter.resolveEffectiveAbility(
            observation = obs,
            expectedPartySlot = 0,
            isExactHns = true
        )
        assertEquals(EffectiveAbilityResolution.UnknownAbility, resolution)
    }

    @Test
    fun `resolveEffectiveAbility fails closed to UnknownAbility when isExactHns is false`() {
        val obs = battlerObservation(partySlot = 0, abilityId = 51)
        val resolution = CalcParticipantPresenter.resolveEffectiveAbility(
            observation = obs,
            expectedPartySlot = 0,
            isExactHns = false
        )
        assertEquals(EffectiveAbilityResolution.UnknownAbility, resolution)
    }

    @Test
    fun `resolveEffectiveAbility fails closed to UnknownAbility when observation or slot is null`() {
        assertEquals(
            EffectiveAbilityResolution.UnknownAbility,
            CalcParticipantPresenter.resolveEffectiveAbility(null, 0, true)
        )
        val obs = battlerObservation(partySlot = 0, abilityId = 51)
        assertEquals(
            EffectiveAbilityResolution.UnknownAbility,
            CalcParticipantPresenter.resolveEffectiveAbility(obs, null, true)
        )
    }

    @Test
    fun `resolveEffectiveAbility defense-in-depth rejects ability identity ID mismatch`() {
        // state.abilityId = 65 (Overgrow) but identity = Declared(51, "Keen Eye")
        val mismatchedObs = BattlerRuntimeObservation(
            state = HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED,
                partySlot = 0,
                abilityId = 65,
                abilityOutOfDomain = false,
                types = emptyList()
            ),
            abilityIdentity = DeclaredAbility.Declared(abilityId = 51, name = "Keen Eye")
        )
        val res = CalcParticipantPresenter.resolveEffectiveAbility(
            observation = mismatchedObs,
            expectedPartySlot = 0,
            isExactHns = true
        )
        assertEquals(EffectiveAbilityResolution.UnknownAbility, res)

        // state.abilityId = 0 (NONE) but identity = Declared(51, "Keen Eye")
        val zeroMismatchObs = BattlerRuntimeObservation(
            state = HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED,
                partySlot = 0,
                abilityId = 0,
                abilityOutOfDomain = false,
                types = emptyList()
            ),
            abilityIdentity = DeclaredAbility.Declared(abilityId = 51, name = "Keen Eye")
        )
        val resZero = CalcParticipantPresenter.resolveEffectiveAbility(
            observation = zeroMismatchObs,
            expectedPartySlot = 0,
            isExactHns = true
        )
        assertEquals(EffectiveAbilityResolution.UnknownAbility, resZero)
    }

    // ---------------------------------------------------------------------
    // 3. CalcInputPreparation & Presenter integration tests
    // ---------------------------------------------------------------------

    @Test
    fun `CalcInputPreparation fromParsed populates ability for ObservedAbility without ABILITY unknownField`() {
        val parsed = dummyParsedPokemon()
        val participant = CalcInputPreparation.fromParsed(
            parsed = parsed,
            speciesName = "Pidgeot",
            effectiveAbility = EffectiveAbilityResolution.ObservedAbility("Keen Eye", 51),
            partySlot = 0
        )
        assertEquals("Keen Eye", participant.ability)
        assertEquals(51, participant.abilityId)
        assertEquals(0, participant.partySlot)
        assertFalse(participant.unknownFields.contains(CalcInputField.ABILITY))
    }

    @Test
    fun `CalcInputPreparation fromParsed populates None for ObservedNone without ABILITY unknownField`() {
        val parsed = dummyParsedPokemon()
        val participant = CalcInputPreparation.fromParsed(
            parsed = parsed,
            speciesName = "Machamp",
            effectiveAbility = EffectiveAbilityResolution.ObservedNone,
            partySlot = 1
        )
        assertEquals("None", participant.ability)
        assertEquals(0, participant.abilityId)
        assertEquals(1, participant.partySlot)
        assertFalse(participant.unknownFields.contains(CalcInputField.ABILITY))
    }

    @Test
    fun `CalcInputPreparation fromParsed leaves ability null and includes ABILITY unknownField for UnknownAbility`() {
        val parsed = dummyParsedPokemon()
        val participant = CalcInputPreparation.fromParsed(
            parsed = parsed,
            speciesName = "Machamp",
            effectiveAbility = EffectiveAbilityResolution.UnknownAbility,
            partySlot = 0
        )
        assertNull(participant.ability)
        assertEquals(0, participant.partySlot)
        assertTrue(participant.unknownFields.contains(CalcInputField.ABILITY))
    }

    @Test
    fun `CalcParticipantPresenter attacker integrates live battler state with slot matching and partySlot provenance`() {
        val party = listOf(dummyParsedPokemon(68), dummyParsedPokemon(18))
        val obs = battlerObservation(partySlot = 1, abilityId = 51, declaredName = "Keen Eye")

        // Selected slot 1 matches battler partySlot 1 -> Keen Eye resolved, partySlot = 1
        val activeAttacker = CalcParticipantPresenter.attacker(
            party = party,
            selectedIndex = 1,
            speciesNameOf = { "Pidgeot" },
            playerBattlerState = obs,
            isExactHns = true
        )
        assertEquals("Keen Eye", activeAttacker.ability)
        assertEquals(1, activeAttacker.partySlot)
        assertFalse(activeAttacker.unknownFields.contains(CalcInputField.ABILITY))

        // Selected slot 0 mismatches battler partySlot 1 -> UnknownAbility, partySlot = 0
        val benchedAttacker = CalcParticipantPresenter.attacker(
            party = party,
            selectedIndex = 0,
            speciesNameOf = { "Machamp" },
            playerBattlerState = obs,
            isExactHns = true
        )
        assertNull(benchedAttacker.ability)
        assertEquals(0, benchedAttacker.partySlot)
        assertTrue(benchedAttacker.unknownFields.contains(CalcInputField.ABILITY))
    }

    // ---------------------------------------------------------------------
    // 4. Policy evaluation & boundary anti-spoofing tests
    // ---------------------------------------------------------------------

    @Test
    fun `supported abilities clear ability blockers and leave the next mechanics blocker`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Test with Keen Eye (PROVEN_NO_DAMAGE_EFFECT)
        val keenEyeReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(species = "Pidgeot", level = 50, ability = "Keen Eye"),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Wing Attack")
        )
        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = keenEyeReq,
            challengeSettings = snapshot
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("H&S supported ability must reach Ready under C4b, got $outcome")

        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertNotNull(ready.request)
        // No ability blockers present
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
        // Gap C3: no held item supplied here, so no item-specific blocker is present ...
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
        // Gap C4b: badge and modifier order blockers are cleared
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.BADGE_BOOST_NOT_MODELLED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED))
    }

    @Test
    fun `unsupported damage-relevant ability triggers HNS_ABILITY_EFFECT_NOT_MODELLED`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Test Guts (temporarily unsupported in C2 due to modifier composition & stat stages ordering)
        val gutsReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(species = "Machamp", level = 50, ability = "Guts"),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Cross Chop")
        )
        val outcomeGuts = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = gutsReq,
            challengeSettings = snapshot
        )
        val refusedGuts = outcomeGuts as CalcRequestOutcome.Refused
        assertTrue(refusedGuts.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))

        // Test Blaze
        val blazeReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(species = "Charizard", level = 50, ability = "Blaze"),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Flamethrower")
        )
        val outcomeBlaze = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = blazeReq,
            challengeSettings = snapshot
        )
        val refusedBlaze = outcomeBlaze as CalcRequestOutcome.Refused
        assertTrue(refusedBlaze.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `unreadable ability triggers HNS_EFFECTIVE_ABILITY_UNREADABLE`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        val unreadableReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Machamp",
                level = 50,
                ability = null,
                origin = CalcInputOrigin.LIVE_READ,
                unknownFields = listOf(CalcInputField.ABILITY),
                partySlot = 0
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Cross Chop")
        )
        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = unreadableReq,
            challengeSettings = snapshot
        )
        val refused = outcome as CalcRequestOutcome.Refused
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
    }

    @Test
    fun `CalcRequestBoundary anti-spoofing overrides caller-supplied ability with authoritative live read on matching slot`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Authoritative observation reports Keen Eye (51) on partySlot 0
        val authoritativeObs = battlerObservation(partySlot = 0, abilityId = 51, declaredName = "Keen Eye")

        // Caller attempts to spoof "Blaze" on a LIVE_READ participant with matching partySlot 0
        val spoofedReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Pidgeot",
                level = 50,
                ability = "Blaze",
                origin = CalcInputOrigin.LIVE_READ,
                partySlot = 0
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Wing Attack")
        )

        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = spoofedReq,
            challengeSettings = snapshot,
            playerBattlerState = authoritativeObs
        )
        val refused = outcome as CalcRequestOutcome.Refused
        // Verify that the boundary reconciled with Keen Eye (supported), not the spoofed Blaze (unsupported)
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
    }

    @Test
    fun `CalcRequestBoundary rejects observation and marks unreadable on partySlot mismatch`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Active battler is partySlot 0 with Keen Eye
        val activeBattlerObs = battlerObservation(partySlot = 0, abilityId = 51, declaredName = "Keen Eye")

        // Participant claims partySlot 1 (bench) and supplies ability = "Keen Eye"
        val benchedReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Pidgeot",
                level = 50,
                ability = "Keen Eye",
                origin = CalcInputOrigin.LIVE_READ,
                partySlot = 1
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Wing Attack")
        )

        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = benchedReq,
            challengeSettings = snapshot,
            playerBattlerState = activeBattlerObs
        )
        val refused = outcome as CalcRequestOutcome.Refused
        // Boundary must reject the slot mismatch, wipe ability to null, and flag unreadable
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN))
    }

    @Test
    fun `CalcRequestBoundary rejects observation when LIVE_READ participant has null partySlot provenance`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        val activeBattlerObs = battlerObservation(partySlot = 0, abilityId = 51, declaredName = "Keen Eye")

        // LIVE_READ participant with no partySlot provenance
        val unprovenancedReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Pidgeot",
                level = 50,
                ability = "Keen Eye",
                origin = CalcInputOrigin.LIVE_READ,
                partySlot = null
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Wing Attack")
        )

        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = unprovenancedReq,
            challengeSettings = snapshot,
            playerBattlerState = activeBattlerObs
        )
        val refused = outcome as CalcRequestOutcome.Refused
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
    }

    @Test
    fun `CalcRequestBoundary defense-in-depth rejects ability identity ID mismatch`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Malformed observation: state says Overgrow (65) but identity says Keen Eye (51)
        val malformedObs = BattlerRuntimeObservation(
            state = HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED,
                partySlot = 0,
                abilityId = 65,
                abilityOutOfDomain = false,
                types = emptyList()
            ),
            abilityIdentity = DeclaredAbility.Declared(abilityId = 51, name = "Keen Eye")
        )

        val req = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Pidgeot",
                level = 50,
                ability = "Keen Eye",
                origin = CalcInputOrigin.LIVE_READ,
                partySlot = 0
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Wing Attack")
        )

        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = req,
            challengeSettings = snapshot,
            playerBattlerState = malformedObs
        )
        val refused = outcome as CalcRequestOutcome.Refused
        // Must fail closed to unreadable because identity ID does not match state ID
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
    }

    @Test
    fun `CalcRequestBoundary anti-spoofing wipes ability when authoritative read is absent or invalid`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Caller claims Keen Eye on LIVE_READ, but authoritative state is UNAVAILABLE
        val invalidObs = battlerObservation(status = HnsBattlerRuntimeStatus.UNAVAILABLE)

        val spoofedReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Pidgeot",
                level = 50,
                ability = "Keen Eye",
                origin = CalcInputOrigin.LIVE_READ,
                partySlot = 0
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Wing Attack")
        )

        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = spoofedReq,
            challengeSettings = snapshot,
            playerBattlerState = invalidObs
        )
        val refused = outcome as CalcRequestOutcome.Refused
        // Ability must have failed closed to unreadable
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN))
    }

    @Test
    fun `live read capability verdict derives from authoritative abilityId not identity name`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Malformed observation where runtime ID is 65 (Overgrow, unsupported)
        // but identity declared name is "Keen Eye" (supported no-damage name) with matching ID 65
        val malformedObs = BattlerRuntimeObservation(
            state = HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED,
                battlerIndex = 0,
                partySlot = 0,
                abilityId = 65,
                abilityOutOfDomain = false,
                types = emptyList()
            ),
            abilityIdentity = DeclaredAbility.Declared(
                abilityId = 65,
                name = "Keen Eye"
            )
        )

        val req = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Pidgeot",
                level = 50,
                origin = CalcInputOrigin.LIVE_READ,
                partySlot = 0
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Wing Attack")
        )

        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = req,
            challengeSettings = snapshot,
            playerBattlerState = malformedObs
        )

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("Must be refused due to unsupported Overgrow ability")

        // Capability verdict MUST be chosen from the authoritative numeric ID (65 -> Overgrow -> UNSUPPORTED),
        // NEVER from the identity display name string ("Keen Eye")
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `live read abilityId directly range-checked against pinned ability domain`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Observation with abilityId > ABILITY_ID_MAX (311) but abilityOutOfDomain erroneously false
        val outOfDomainObs = BattlerRuntimeObservation(
            state = HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED,
                battlerIndex = 0,
                partySlot = 0,
                abilityId = HnsBattlerRuntimeStateIds.ABILITY_ID_MAX + 1,
                abilityOutOfDomain = false,
                types = emptyList()
            ),
            abilityIdentity = DeclaredAbility.Declared(
                abilityId = HnsBattlerRuntimeStateIds.ABILITY_ID_MAX + 1,
                name = "UnknownOverMax"
            )
        )

        val req = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Pidgeot",
                level = 50,
                origin = CalcInputOrigin.LIVE_READ,
                partySlot = 0
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Wing Attack")
        )

        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = req,
            challengeSettings = snapshot,
            playerBattlerState = outOfDomainObs
        )

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("Must be refused due to out-of-domain ability ID")

        // Must fail closed to unreadable because abilityId exceeds pinned domain
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
    }
}
