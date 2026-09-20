package com.dualdex.calculator

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsAbilityCategory
import com.dualdex.pokemon.hns.HnsAbilityRegistry
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
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
    fun `HnsAbilityRegistry classifies modelled equivalent abilities`() {
        val guts = HnsAbilityRegistry.classify(62)
        assertEquals(HnsAbilityCategory.MODELLED_EQUIVALENT, guts.category)
        assertTrue(guts.category.isSupportedForDamage)
        assertEquals("Guts", HnsAbilityRegistry.canonicalTitleCaseName(62))

        val thickFat = HnsAbilityRegistry.classify(47)
        assertEquals(HnsAbilityCategory.MODELLED_EQUIVALENT, thickFat.category)
        assertTrue(thickFat.category.isSupportedForDamage)
        assertEquals("Thick Fat", HnsAbilityRegistry.canonicalTitleCaseName(47))

        val hugePower = HnsAbilityRegistry.classify(37)
        assertEquals(HnsAbilityCategory.MODELLED_EQUIVALENT, hugePower.category)
        assertTrue(hugePower.category.isSupportedForDamage)
        assertEquals("Huge Power", HnsAbilityRegistry.canonicalTitleCaseName(37))

        val purePower = HnsAbilityRegistry.classify(74)
        assertEquals(HnsAbilityCategory.MODELLED_EQUIVALENT, purePower.category)
        assertTrue(purePower.category.isSupportedForDamage)
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
        val obs = battlerObservation(partySlot = 2, abilityId = 62, declaredName = "Guts")
        val resolution = CalcParticipantPresenter.resolveEffectiveAbility(
            observation = obs,
            expectedPartySlot = 2,
            isExactHns = true
        )
        assertTrue(resolution is EffectiveAbilityResolution.ObservedAbility)
        assertEquals("Guts", (resolution as EffectiveAbilityResolution.ObservedAbility).name)
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
        val obs = battlerObservation(partySlot = 1, abilityId = 62)
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
            val obs = battlerObservation(status = status, partySlot = 0, abilityId = 62)
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
        val obs = battlerObservation(partySlot = 0, abilityId = 62)
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
        val obs = battlerObservation(partySlot = 0, abilityId = 62)
        assertEquals(
            EffectiveAbilityResolution.UnknownAbility,
            CalcParticipantPresenter.resolveEffectiveAbility(obs, null, true)
        )
    }

    // ---------------------------------------------------------------------
    // 3. CalcInputPreparation & Presenter integration tests
    // ---------------------------------------------------------------------

    @Test
    fun `CalcInputPreparation fromParsed populates ability for ObservedAbility without ABILITY unknownField`() {
        val parsed = dummyParsedPokemon()
        val participant = CalcInputPreparation.fromParsed(
            parsed = parsed,
            speciesName = "Machamp",
            effectiveAbility = EffectiveAbilityResolution.ObservedAbility("Guts")
        )
        assertEquals("Guts", participant.ability)
        assertFalse(participant.unknownFields.contains(CalcInputField.ABILITY))
    }

    @Test
    fun `CalcInputPreparation fromParsed populates None for ObservedNone without ABILITY unknownField`() {
        val parsed = dummyParsedPokemon()
        val participant = CalcInputPreparation.fromParsed(
            parsed = parsed,
            speciesName = "Machamp",
            effectiveAbility = EffectiveAbilityResolution.ObservedNone
        )
        assertEquals("None", participant.ability)
        assertFalse(participant.unknownFields.contains(CalcInputField.ABILITY))
    }

    @Test
    fun `CalcInputPreparation fromParsed leaves ability null and includes ABILITY unknownField for UnknownAbility`() {
        val parsed = dummyParsedPokemon()
        val participant = CalcInputPreparation.fromParsed(
            parsed = parsed,
            speciesName = "Machamp",
            effectiveAbility = EffectiveAbilityResolution.UnknownAbility
        )
        assertNull(participant.ability)
        assertTrue(participant.unknownFields.contains(CalcInputField.ABILITY))
    }

    @Test
    fun `CalcParticipantPresenter attacker integrates live battler state with slot matching`() {
        val party = listOf(dummyParsedPokemon(68), dummyParsedPokemon(143))
        val obs = battlerObservation(partySlot = 1, abilityId = 47, declaredName = "Thick Fat")

        // Selected slot 1 matches battler partySlot 1 -> Thick Fat resolved
        val activeAttacker = CalcParticipantPresenter.attacker(
            party = party,
            selectedIndex = 1,
            speciesNameOf = { "Snorlax" },
            playerBattlerState = obs,
            isExactHns = true
        )
        assertEquals("Thick Fat", activeAttacker.ability)
        assertFalse(activeAttacker.unknownFields.contains(CalcInputField.ABILITY))

        // Selected slot 0 mismatches battler partySlot 1 -> UnknownAbility
        val benchedAttacker = CalcParticipantPresenter.attacker(
            party = party,
            selectedIndex = 0,
            speciesNameOf = { "Machamp" },
            playerBattlerState = obs,
            isExactHns = true
        )
        assertNull(benchedAttacker.ability)
        assertTrue(benchedAttacker.unknownFields.contains(CalcInputField.ABILITY))
    }

    // ---------------------------------------------------------------------
    // 4. Policy evaluation & boundary anti-spoofing tests
    // ---------------------------------------------------------------------

    @Test
    fun `supported abilities clear ability blockers and fail closed to held item blocker`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Test with Guts (MODELLED_EQUIVALENT)
        val gutsReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(species = "Machamp", level = 50, ability = "Guts"),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Cross Chop")
        )
        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = gutsReq,
            challengeSettings = snapshot
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("H&S must remain refused via HNS_HELD_ITEM_SYSTEM_NOT_MODELLED")

        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull(refused.verdict.request)
        // No ability blockers present
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
        // Held item blocker present
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED))
    }

    @Test
    fun `unsupported damage-relevant ability triggers HNS_ABILITY_EFFECT_NOT_MODELLED`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        val blazeReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(species = "Charizard", level = 50, ability = "Blaze"),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Flamethrower")
        )
        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = blazeReq,
            challengeSettings = snapshot
        )
        val refused = outcome as CalcRequestOutcome.Refused
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
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
                unknownFields = listOf(CalcInputField.ABILITY)
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
    fun `CalcRequestBoundary anti-spoofing overrides caller-supplied ability with authoritative live read`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Authoritative observation reports Guts (62)
        val authoritativeObs = battlerObservation(partySlot = 0, abilityId = 62, declaredName = "Guts")

        // Caller attempts to spoof "Huge Power" on a LIVE_READ participant
        val spoofedReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Machamp",
                level = 50,
                ability = "Huge Power",
                origin = CalcInputOrigin.LIVE_READ
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Cross Chop")
        )

        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = trust,
            request = spoofedReq,
            challengeSettings = snapshot,
            playerBattlerState = authoritativeObs
        )
        val refused = outcome as CalcRequestOutcome.Refused
        // Verify that the policy evaluated with Guts, not the spoofed Huge Power
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
    }

    @Test
    fun `CalcRequestBoundary anti-spoofing wipes ability when authoritative read is absent or invalid`() {
        val trust = exactTrust(heartAndSoul)
        val snapshot = hnsSettingsSnapshot()

        // Caller claims Guts on LIVE_READ, but authoritative state is UNAVAILABLE
        val invalidObs = battlerObservation(status = HnsBattlerRuntimeStatus.UNAVAILABLE)

        val spoofedReq = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = CalcPokemonInput(
                species = "Machamp",
                level = 50,
                ability = "Guts",
                origin = CalcInputOrigin.LIVE_READ
            ),
            defender = CalcPokemonInput(species = "Swampert", level = 50, ability = "None"),
            move = CalcMoveInput(name = "Cross Chop")
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
}
