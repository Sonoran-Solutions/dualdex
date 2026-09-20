package com.dualdex.calculator

import com.dualdex.pokemon.Gen3VanillaDataPack
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.hasMoveByName
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.pokemon.hns.HnsOptionStyle
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression coverage for the calculator-capability policy and the production request boundary
 * (issue #9).
 *
 * These assert the decisions the Calc screen and the engine gate actually consume - the
 * [CalcRequestOutcome] and [CalcCapabilityVerdict] that [CalcTabScreenView] renders - rather than a
 * test-local reimplementation of the rules. The profiles under test are the *bundled* profiles
 * parsed by the production loader, so a profile edit that silently widens calculator trust fails
 * here.
 */
class CalcCapabilityPolicyTest {

    // ---------------------------------------------------------------- fixtures

    private fun bundledProfile(id: String): RomHackProfile {
        val dir = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles") }
            .firstOrNull { it.isDirectory }
            ?: throw AssertionError("Unable to locate bundled ROM profiles")
        val file = File(dir, "$id.json")
        assertTrue("bundled profile $id.json is missing", file.isFile)
        return ProfileLoader.parseProfile(file.readText())
    }

    private val fireRed: RomHackProfile get() = bundledProfile("vanilla_firered")
    private val emerald: RomHackProfile get() = bundledProfile("vanilla_emerald")
    private val heartAndSoul: RomHackProfile get() = bundledProfile("heart_and_soul")

    /**
     * The same [RuntimeRomTrust] the companion publishes for an exact verified ROM, built through
     * the production factory so the policy is tested against the real trust shape.
     */
    private fun exactTrust(profile: RomHackProfile): RuntimeRomTrust {
        val hash = profile.sha256Hashes.first()
        return RuntimeRomTrust.from(
            compatibility = com.dualdex.romhack.RomCompatibility.verified(
                profile = profile,
                sha256 = hash
            ),
            activeRomSha256 = hash
        ).also {
            assertTrue(
                "fixture trust must satisfy exactRuntimeVerified",
                it.exactRuntimeVerified
            )
        }
    }

    private fun trustFor(profile: RomHackProfile, hashes: List<String>): RuntimeRomTrust =
        RuntimeRomTrust.from(
            compatibility = com.dualdex.romhack.RomCompatibility.verified(
                profile = profile.copy(sha256Hashes = hashes),
                sha256 = hashes.first()
            ),
            activeRomSha256 = hashes.first()
        )

    private fun exactHnsProfile(): Pair<RomHackProfile, RuntimeRomTrust> {
        val hnsSha256 = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"
        val hashed = heartAndSoul.copy(
            sha256Hashes = listOf(hnsSha256),
            isVerified = true,
            memoryLayoutVerified = true
        )
        return hashed to exactTrust(hashed)
    }

    private fun hnsSettingsSnapshot(
        status: HnsChallengeSettingsStatus = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle: Int = 0,
        optionStyleOutOfDomain: Boolean = false,
        fairyTypes: Int = 1,
        fairyTypesOutOfDomain: Boolean = false,
        randomTypes: Int = 0,
        randomTypesOutOfDomain: Boolean = false,
        randomEffectiveness: Int = 0,
        randomEffectivenessOutOfDomain: Boolean = false
    ): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = status,
        optionStyle = HnsChallengeField(observed = true, raw = optionStyle, outOfDomain = optionStyleOutOfDomain),
        txModeFairyTypes = HnsChallengeField(observed = true, raw = fairyTypes, outOfDomain = fairyTypesOutOfDomain),
        txRandomType = HnsChallengeField(observed = true, raw = randomTypes, outOfDomain = randomTypesOutOfDomain),
        txRandomTypeEffectiveness = HnsChallengeField(observed = true, raw = randomEffectiveness, outOfDomain = randomEffectivenessOutOfDomain)
    )

    private fun request(
        gen: Int = 3,
        attacker: CalcPokemonInput = CalcPokemonInput(species = "Machamp", level = 50),
        defender: CalcPokemonInput = CalcPokemonInput(species = "Snorlax", level = 50),
        move: CalcMoveInput = CalcMoveInput(name = "Rock Slide"),
        field: CalcFieldInput = CalcFieldInput()
    ) = DamageCalculationRequest(
        gen = gen,
        attacker = attacker,
        defender = defender,
        move = move,
        field = field
    )

    // ------------------------------------------------ live-read production fixtures

    /**
     * The same Machamp the native suite pins: Hardy L50, 31 IVs, 0 EVs, so its Rock Slide against the
     * benchmark Snorlax is 51-60 plain, 102-120 on a critical hit, and 75-89 while burned with Guts.
     *
     * [statusCondition] uses the reader's bit layout: bit 4 is burn.
     */
    private fun machamp(statusCondition: Long = 0L): ParsedPokemon = ParsedPokemon(
        isValid = true,
        isEmpty = false,
        pid = 123456L,
        tid = 1000,
        sid = 2000,
        nickname = "Machamp",
        otName = "Red",
        species = 68,
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
        moves = intArrayOf(1, 0, 0, 0),
        pp = intArrayOf(30, 0, 0, 0),
        currentHp = 150,
        maxHp = 150,
        attack = 150,
        defense = 100,
        speed = 100,
        spAttack = 85,
        spDefense = 100,
        statusCondition = statusCondition
    )

    /**
     * Runs a real live read through the SAME presenter the Calc screen uses.
     *
     * This is the connection the original tests missed: they validated a hand-authored request, so a
     * screen that dropped a participant's status or stat stages still passed. Driving
     * [CalcParticipantPresenter] means the screen's participant selection is under test.
     */
    private fun screenAttacker(
        member: ParsedPokemon,
        stages: com.dualdex.battle.StatStages? = null
    ): CalcParticipantState = CalcParticipantPresenter.attacker(
        party = listOf(member),
        selectedIndex = 0,
        speciesNameOf = { "Machamp" },
        playerStages = stages
    )

    private fun screenDefender(opponent: ParsedPokemon?): CalcParticipantState =
        CalcParticipantPresenter.defender(
            observedOpponent = opponent,
            chosenSpecies = "Snorlax",
            enemyStages = null
        )

    private fun manualMachamp(origin: CalcInputOrigin = CalcInputOrigin.MANUAL) = CalcParticipantState(
        species = "Machamp",
        level = 50,
        nature = "Hardy",
        boosts = CalcParticipantState.NONE,
        ivs = StatBlock(hp = 31, atk = 31, def = 31, spa = 31, spd = 31, spe = 31),
        evs = StatBlock(),
        curHP = 150,
        origin = origin
    )

    /**
     * A live read that carries every field it can and declares no unknown ones.
     *
     * `origin` is LIVE_READ and the ability is supplied explicitly, so this is the "trusted and
     * complete live read" case. It exists so the live-read rules can be tested on their merits
     * rather than only through the always-unknown ability of the real reader.
     */
    private fun completeLiveRead(): CalcParticipantPreparation = CalcInputPreparation.prepare(
        attacker = manualMachamp(origin = CalcInputOrigin.LIVE_READ).copy(
            ability = "Guts",
            unknownFields = emptyList()
        ),
        defender = manualSnorlax(),
        move = CalcMoveInput(name = "Rock Slide"),
        field = CalcFieldInput()
    )

    /**
     * The production presenter applied to a parsed participant whose status bits are all known, so
     * the run carries no STATUS gap - used as the positive control for status handling.
     */
    private fun CalcParticipantPreparationForTest(parsed: ParsedPokemon): CalcParticipantState =
        CalcParticipantPresenter.attacker(
            party = listOf(parsed),
            selectedIndex = 0,
            speciesNameOf = { "Machamp" }
        )

    private fun manualSnorlax() = CalcParticipantState(
        species = "Snorlax",
        level = 50,
        nature = "Hardy",
        boosts = CalcParticipantState.NONE,
        ivs = StatBlock(hp = 31, atk = 31, def = 31, spa = 31, spd = 31, spe = 31),
        evs = StatBlock(),
        curHP = 235
    )

    // ---------------------------------------- the verified vanilla Gen III decision

    @Test
    fun `verified FireRed calculation is authorised as verified`() {
        val profile = fireRed
        val outcome = CalcRequestBoundary.build(profile, exactTrust(profile), request())

        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("verified FireRed must be calculable, got $outcome")

        assertTrue(ready.verdict.isVerified)
        assertEquals(CalcSupport.VERIFIED, ready.verdict.support)
        assertEquals(CalcRuleset.VANILLA_GEN3, ready.verdict.ruleset)
        assertEquals(3, ready.request.gen)
        assertEquals(emptyList<CalcLimitation>(), ready.verdict.limitations)
        assertEquals("", ready.verdict.supportDetail)
    }

    @Test
    fun `verified Emerald calculation is authorised as verified`() {
        val profile = emerald
        val outcome = CalcRequestBoundary.build(profile, exactTrust(profile), request())

        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("verified Emerald must be calculable, got $outcome")
        assertTrue(ready.verdict.isVerified)
    }

    @Test
    fun `verified vanilla presentation states the build and never claims approximation`() {
        val profile = fireRed
        val ready = CalcRequestBoundary.build(profile, exactTrust(profile), request())
            as CalcRequestOutcome.Ready

        val presentation = CalcResultPresentation.forVerdict(ready.verdict)
        assertTrue(presentation.isVerified)
        assertTrue(presentation.headline.startsWith(CalcResultPresentation.VERIFIED_PREFIX))
        assertTrue(presentation.headline.contains("FireRed"))
    }

    @Test
    fun `a verified headline discloses the badge-boost scope it cannot enforce`() {
        // The request shape cannot express the generation III badge boost, so a verified number is
        // about 10% low for a badged player. Documenting that in a design note did not make it true
        // on screen, so the limitation now travels with the claim.
        val profile = fireRed
        val ready = CalcRequestBoundary.build(profile, exactTrust(profile), request())
            as CalcRequestOutcome.Ready

        val headline = CalcResultPresentation.forVerdict(ready.verdict).headline
        assertTrue(
            "a verified headline must state its scope, was: $headline",
            headline.contains(CalcResultPresentation.BADGE_BOOST_NOTE)
        )
        assertTrue(headline.contains("badge boost"))
    }

    // ------------------------------------------- trust caps without changing hashes

    @Test
    fun `same build but different running bytes is approximate not verified`() {
        val profile = fireRed
        // A profile that was verified against this hash, but the running ROM matches a different
        // one: the mechanics are known, the bytes are not.
        val other = "0000000000000000000000000000000000000000000000000000000000000000"
        val trust = trustFor(profile, listOf(other, profile.sha256Hashes.first()))
            .copy(activeRomSha256 = profile.sha256Hashes.last())

        val outcome = CalcRequestBoundary.build(profile, trust, request())
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("an unverified-Vanilla request may still be calculated")

        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertFalse(ready.verdict.isVerified)
        assertTrue(ready.verdict.limitations.contains(CalcLimitation.ROM_NOT_EXACT_VERIFIED))
    }

    @Test
    fun `absent runtime trust never produces a verified result`() {
        val ready = CalcRequestBoundary.build(fireRed, null, request()) as CalcRequestOutcome.Ready
        assertFalse(ready.verdict.isVerified)
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertTrue(ready.verdict.limitations.contains(CalcLimitation.ROM_NOT_EXACT_VERIFIED))
    }

    @Test
    fun `unverified presentation never claims verified`() {
        val ready = CalcRequestBoundary.build(fireRed, null, request()) as CalcRequestOutcome.Ready
        val presentation = CalcResultPresentation.forVerdict(ready.verdict)

        assertFalse(presentation.isVerified)
        assertTrue(presentation.headline.startsWith(CalcResultPresentation.ESTIMATED_PREFIX))
        assertTrue(
            "an approximate result must say why: ${presentation.headline}",
            presentation.headline.contains("exact verified build")
        )
    }

    // ------------------------------------------------- unsupported ROMs fail closed

    @Test
    fun `unsupported placeholder profile is refused`() {
        val outcome = CalcRequestBoundary.build(RomHackProfile.UNSUPPORTED, null, request())
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("an unidentified ROM must not be calculable")

        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull(refused.verdict.request)
        assertTrue(refused.verdict.supportDetail.isNotBlank())
    }

    @Test
    fun `recognised but unverified build with no capability row is refused`() {
        // A CFRU hack: a real bundled profile, but not one of the two supported beta targets.
        val radicalRed = bundledProfile("radical_red")
        val outcome = CalcRequestBoundary.build(radicalRed, null, request())

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a hack with no capability row must be refused")
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull(refused.verdict.request)
    }

    @Test
    fun `split mechanics vanilla build is refused rather than assumed Gen III`() {
        val splitVanilla = fireRed.copy(id = "vanilla_split", hasPhysSpecSplit = true)
        val outcome = CalcRequestBoundary.build(splitVanilla, exactTrust(fireRed), request())

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a split-mechanics vanilla build must not inherit Gen III rules")
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
    }

    @Test
    fun `profile with custom species is refused rather than defaulted to Gen III`() {
        val ghostGrey = bundledProfile("ghost_grey")
        val outcome = CalcRequestBoundary.build(ghostGrey, null, request())

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a custom-species profile must be refused")
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
    }

    @Test
    fun `unsupported presentation says no result`() {
        val refused = CalcRequestBoundary.build(RomHackProfile.UNSUPPORTED, null, request())
            as CalcRequestOutcome.Refused
        val presentation = CalcResultPresentation.forVerdict(refused.verdict)

        assertFalse(presentation.isVerified)
        assertTrue(presentation.headline.startsWith(CalcResultPresentation.UNSUPPORTED_PREFIX))
        assertTrue(presentation.headline.contains("no result"))
    }

    // ------------------------------------------------------- Heart & Soul 2.0.5

    @Test
    fun `H and S resolves to its own ruleset row and never to Gen III vanilla`() {
        val capability = CalcCapabilityPolicy.capabilityFor(heartAndSoul)
            ?: throw AssertionError("Heart & Soul must have a documented capability row")

        assertEquals(CalcRuleset.HNS_2_0_5, capability.ruleset)
        assertEquals(CalcSupport.ESTIMATED, capability.ceiling)
        assertEquals(CalcCapabilityPolicy.HNS_DATA_PACK_ID, capability.contentSource)
        // The hack keeps the generation III damage arithmetic.
        assertEquals(3, capability.mechanicsGeneration)
        assertTrue(capability.label.contains(CalcCapabilityPolicy.HNS_PINNED_COMMIT))
    }

    @Test
    fun `H and S is refused while its damage rules are unreadable`() {
        val outcome = CalcRequestBoundary.build(heartAndSoul, null, request())
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("H&S must not produce damage while its rule toggles are unread")

        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull(refused.verdict.request)
    }

    @Test
    fun `H and S refusal names every unread damage rule`() {
        val refused = CalcRequestBoundary.build(heartAndSoul, null, request())
            as CalcRequestOutcome.Refused

        val expected = listOf(
            CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE,
            CalcLimitation.FAIRY_TOGGLE_UNREADABLE,
            CalcLimitation.RANDOM_TYPES_UNREADABLE,
            CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE
        )
        expected.forEach { limitation ->
            assertTrue(
                "$limitation must be reported for H&S, got ${refused.verdict.limitations}",
                refused.verdict.limitations.contains(limitation)
            )
        }
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.BUILDS_NOT_HASH_VERIFIED))
    }

    @Test
    fun `H and S refusal is presented as no result with its reason`() {
        val refused = CalcRequestBoundary.build(heartAndSoul, null, request())
            as CalcRequestOutcome.Refused
        val presentation = CalcResultPresentation.forVerdict(refused.verdict)

        assertFalse(presentation.isVerified)
        assertTrue(presentation.headline.startsWith(CalcResultPresentation.UNSUPPORTED_PREFIX))
        assertTrue(
            "the refusal must name the unread toggle: ${presentation.headline}",
            presentation.headline.contains("switch between per-move and per-type damage categories")
        )
    }

    @Test
    fun `H and S carrying a published ROM hash still cannot reach verified`() {
        // Even if the exact 2.0.5 hash were added to the profile and matched at runtime, the
        // unread damage-rule toggles keep the result out of VERIFIED. This pins the reason the
        // profile's sha256Hashes stay empty: adding them must not buy calculator trust.
        val hnsSha256 = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"
        val hashed = heartAndSoul.copy(
            sha256Hashes = listOf(hnsSha256),
            isVerified = true,
            memoryLayoutVerified = true
        )
        val outcome = CalcRequestBoundary.build(hashed, exactTrust(hashed), request())

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a hash alone must not make H&S verified")
        assertFalse(refused.verdict.isVerified)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE))
    }

    // ----------------------------------------------- content must be this build's

    @Test
    fun `species outside the pinned data is refused`() {
        val outcome = CalcRequestBoundary.build(
            fireRed,
            exactTrust(fireRed),
            request(attacker = CalcPokemonInput(species = "Terapagos", level = 50))
        )

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a species absent from the build must be refused")
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA))
    }

    @Test
    fun `a pinned build refuses a name outside its own data even when the engine knows it`() {
        // The embedded engine carries its own later-generation move data, so a Gen IX move name
        // resolves inside the engine. A build whose pinned pack blocks global fallback must refuse
        // any name its own data does not contain, because the engine would otherwise compute a
        // confident number from a record that is not this build's.
        val malignantChain = "Malignant Chain"
        assertNotNull(
            "the shared database must know this move, or this test proves nothing",
            com.dualdex.pokemon.MoveDatabase.getByName(malignantChain)
        )
        // The pinned pack owns this move, so this name is NOT the refusal case for H&S.
        assertNotNull(HeartAndSoul205DataPack.getMoveByName(malignantChain))
        // The pinned pack does not own this name, and its fallback is disabled.
        assertNull(HeartAndSoul205DataPack.getMoveByName("No Such Move Anywhere"))

        val hns = CalcRequestBoundary.build(
            heartAndSoul,
            null,
            request(move = CalcMoveInput(name = "No Such Move Anywhere"))
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("H&S must refuse a move outside its pinned pack")
        assertTrue(hns.verdict.limitations.contains(CalcLimitation.MOVE_NOT_IN_PINNED_DATA))
    }

    @Test
    fun `no data source knows the name so even vanilla refuses it`() {
        // Vanilla Gen III has no pinned name index of its own, so it accepts a name the shared
        // generation III database knows. A name no source knows is refused rather than sent to an
        // engine that would substitute its own record.
        val profile = fireRed
        assertTrue(
            "vanilla must accept a generation III move the shared database knows",
            com.dualdex.pokemon.Gen3VanillaDataPack.hasMoveByName("Rock Slide")
        )

        val outcome = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(move = CalcMoveInput(name = "Definitely Not A Move"))
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a name no data source knows must be refused")
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.MOVE_NOT_IN_PINNED_DATA))
    }

    @Test
    fun `H and S pinned species and moves resolve through the pack name index`() {
        // The bridge selects content by name, so the pinned pack must be able to prove that a name
        // belongs to this build. Names the pack cannot vouch for are refused, never substituted
        // with the shared dex's same-named entry.
        assertNotNull(HeartAndSoul205DataPack.getSpeciesByName("Bulbasaur"))
        assertNotNull(HeartAndSoul205DataPack.getSpeciesByName("bULBASAUR"))
        // Gen IX content that only this build's pinned pack carries.
        assertNotNull(HeartAndSoul205DataPack.getMoveByName("Tera Starstorm"))
        assertNotNull(HeartAndSoul205DataPack.getMoveByName("Malignant Chain"))
        assertNull(HeartAndSoul205DataPack.getSpeciesByName("Definitely Not A Pokemon"))
        assertNull(HeartAndSoul205DataPack.getMoveByName("Definitely Not A Move"))
    }

    @Test
    fun `H and S resolves a name the shared dex does not know at all`() {
        // This is the regression that makes the pack name index necessary: the shared Gen III dex
        // does not contain every species name the hack ships, so resolving by name against it
        // would refuse valid hack content.
        val hackOnlyName = HeartAndSoul205DataPack.getSpecies(1433)?.name
        assertNotNull("pack must carry species 1433", hackOnlyName)
        assertNull(
            "the shared dex must not resolve this name, or this test proves nothing",
            com.dualdex.pokemon.SpeciesDatabase.getByName(hackOnlyName!!)
        )
    }

    @Test
    fun `multi-form species resolve by id and are refused by ambiguous name`() {
        // 'Eevee' names both the base species and a hack-added form with different stats. A
        // name-only request cannot say which one is meant, so the name must not resolve.
        assertNotNull(HeartAndSoul205DataPack.getSpecies(133))
        assertNull(HeartAndSoul205DataPack.getSpeciesByName("Eevee"))
        assertTrue(HeartAndSoul205DataPack.multiFormNames.contains("eevee"))

        val outcome = CalcRequestBoundary.build(
            heartAndSoul,
            null,
            request(defender = CalcPokemonInput(species = "Eevee", level = 50))
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("an ambiguous form name must be refused")
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA))
    }

    // ---------------------------------------------- per-request capability limits

    @Test
    fun `Gen III modelled ability keeps a vanilla calculation verified`() {
        val profile = fireRed
        val outcome = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(defender = CalcPokemonInput(species = "Snorlax", level = 50, ability = "Thick Fat"))
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("Thick Fat is modelled for Gen III and must be calculable")
        assertTrue(ready.verdict.isVerified)
    }

    @Test
    fun `unmodelled ability downgrades vanilla to approximate`() {
        val profile = fireRed
        val outcome = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(defender = CalcPokemonInput(species = "Snorlax", level = 50, ability = "Multiscale"))
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("an unmodelled ability is a labelled estimate, not a refusal")

        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertTrue(ready.verdict.limitations.contains(CalcLimitation.ABILITY_NOT_MODELLED))
    }

    @Test
    fun `Gen III modelled held item keeps a vanilla calculation verified`() {
        val profile = fireRed
        val outcome = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(attacker = CalcPokemonInput(species = "Machamp", level = 50, item = "Choice Band"))
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("Choice Band is modelled for Gen III")
        assertTrue(ready.verdict.isVerified)
    }

    @Test
    fun `unknown held item downgrades vanilla to approximate`() {
        val profile = fireRed
        val outcome = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(attacker = CalcPokemonInput(species = "Machamp", level = 50, item = "Life Orb"))
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("an unmodelled item is a labelled estimate, not a refusal")

        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertTrue(ready.verdict.limitations.contains(CalcLimitation.ITEM_NOT_MODELLED))
    }

    @Test
    fun `out of range stat values refuse the request`() {
        val profile = fireRed
        val impossible = request(
            attacker = CalcPokemonInput(
                species = "Machamp",
                level = 50,
                ivs = StatBlock(hp = 31, atk = 99, def = 31, spa = 31, spd = 31, spe = 31)
            )
        )
        val refused = CalcRequestBoundary.build(profile, exactTrust(profile), impossible)
            as? CalcRequestOutcome.Refused
            ?: throw AssertionError("IVs above 31 are impossible and must be refused")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.STAT_VALUES_OUT_OF_RANGE))
        assertNull(refused.verdict.request)
    }

    @Test
    fun `out of range boosts refuse the request`() {
        val profile = fireRed
        val impossible = request(
            attacker = CalcPokemonInput(
                species = "Machamp",
                level = 50,
                boosts = StatBlock(atk = 7)
            )
        )
        val refused = CalcRequestBoundary.build(profile, exactTrust(profile), impossible)
            as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a +7 stat stage is impossible and must be refused")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.BOOSTS_OUT_OF_RANGE))
    }

    @Test
    fun `impossible level refuses the request`() {
        val profile = fireRed
        val refused = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(attacker = CalcPokemonInput(species = "Machamp", level = 999))
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a level the games cannot produce must be refused")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.LEVEL_OUT_OF_RANGE))
    }

    @Test
    fun `modelled status keeps a vanilla calculation verified`() {
        val profile = fireRed
        val ready = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(attacker = CalcPokemonInput(species = "Machamp", level = 50, status = "brn"))
        ) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("brn is a modelled status")

        assertTrue(ready.verdict.isVerified)
    }

    @Test
    fun `unmodelled status refuses the request`() {
        // The engine stores an unknown status verbatim and then treats the Pokemon as merely
        // "has a status", which enables Guts and Marvel Scale while skipping the burn halving.
        // "BRN" is exactly that trap: it reads as burn to a human but not to the engine.
        val profile = fireRed
        listOf("BRN", "confused", "badly poisoned").forEach { status ->
            val refused = CalcRequestBoundary.build(
                profile,
                exactTrust(profile),
                request(attacker = CalcPokemonInput(species = "Machamp", level = 50, status = status))
            ) as? CalcRequestOutcome.Refused
                ?: throw AssertionError("'$status' must be refused, not reinterpreted")

            assertTrue(refused.verdict.limitations.contains(CalcLimitation.STATUS_NOT_MODELLED))
        }
    }

    @Test
    fun `modelled weather keeps a vanilla calculation verified`() {
        val profile = fireRed
        CalcCapabilityPolicy.MODELLED_WEATHER.forEach { weather ->
            val ready = CalcRequestBoundary.build(
                profile,
                exactTrust(profile),
                request(field = CalcFieldInput(weather = weather))
            ) as? CalcRequestOutcome.Ready
                ?: throw AssertionError("$weather is a modelled weather")
            assertTrue("$weather must stay verified", ready.verdict.isVerified)
        }
    }

    @Test
    fun `weather spelling the engine would ignore is canonicalised in the authorised request`() {
        // Regression for the R2 review finding. The validator accepted weather case-insensitively
        // while normaliseNames only canonicalised abilities and items, so "rain" was approved and
        // then forwarded verbatim. The engine's Field.hasWeather is `weathers.includes(this.weather)`,
        // so "rain" behaves exactly like NO weather - the same validation-versus-execution mismatch
        // as the singles/Singles defect.
        val profile = fireRed
        val accepted = mapOf(
            "Rain" to "Rain",
            "rain" to "Rain",
            "RAIN" to "Rain",
            " rain " to "Rain",
            "sun" to "Sun",
            "HAIL" to "Hail"
        )

        accepted.forEach { (supplied, canonical) ->
            val ready = CalcRequestBoundary.build(
                profile,
                exactTrust(profile),
                request(field = CalcFieldInput(weather = supplied))
            ) as? CalcRequestOutcome.Ready
                ?: throw AssertionError("'$supplied' should be accepted and canonicalised")

            assertTrue("'$supplied' must stay verified", ready.verdict.isVerified)
            assertEquals(
                "'$supplied' must be sent as '$canonical' or the engine ignores it",
                canonical,
                ready.request.field.weather
            )
            assertEquals(
                "the serialised request is what the engine reads",
                canonical,
                JSONObject(buildCalcRequestJson(ready.request)).getJSONObject("field").getString("weather")
            )
        }
    }

    @Test
    fun `canonicalised weather is weather the engine actually applies`() {
        // The point of the rewrite, stated as the engine's own behaviour: the canonical spelling
        // changes the number, and the lowercase one that used to be forwarded does not.
        val canonical = CalcCapabilityPolicy.canonicalWeather("rain")
        val lowered = CalcCapabilityPolicy.canonicalWeather("RAIN")
        assertEquals("Rain", canonical)
        assertEquals(canonical, lowered)

        // What the policy accepts is always a canonical spelling, so a forwarded value can never be
        // one the engine silently drops.
        listOf("Sun", "rain", "RAIN", "sand", "hail").forEach { supplied ->
            val resolved = CalcCapabilityPolicy.canonicalWeather(supplied)
            assertNotNull("'$supplied' must resolve", resolved)
            assertTrue(
                "'$supplied' resolved to '$resolved', which is not a modelled spelling",
                CalcCapabilityPolicy.MODELLED_WEATHER.contains(resolved!!)
            )
        }
        assertNull(CalcCapabilityPolicy.canonicalWeather("sunny"))
    }

    @Test
    fun `snow is refused rather than silently computed as no weather`() {
        // Snow is a real, reachable condition in Heart & Soul 2.0.5 (Ice Defense x1.5, no chip
        // damage, distinct from Hail). The engine compares weather exactly and would treat this as
        // no weather at all, so it must be refused.
        val profile = fireRed
        val refused = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(field = CalcFieldInput(weather = "Snow"))
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("Snow must be refused, not ignored")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.FIELD_CONDITION_NOT_MODELLED))
    }

    @Test
    fun `terrain is refused because the pipeline ignores it`() {
        val profile = fireRed
        val refused = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(field = CalcFieldInput(terrain = "Electric"))
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("terrain must be refused, not silently ignored")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.FIELD_CONDITION_NOT_MODELLED))
    }

    @Test
    fun `near miss weather spelling is refused too`() {
        val profile = fireRed
        val refused = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(field = CalcFieldInput(weather = "sunny"))
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a weather name the engine ignores must be refused")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.FIELD_CONDITION_NOT_MODELLED))
    }

    // ------------------------------------------------- request normalisation

    @Test
    fun `boundary normalises a wrong mechanics generation instead of trusting it`() {
        val profile = fireRed
        val outcome = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(gen = 8)
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("a wrong generation is a limitation, not a refusal")

        assertEquals(3, ready.request.gen)
        assertTrue(ready.verdict.limitations.contains(CalcLimitation.MECHANICS_GENERATION_MISMATCH))
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
    }

    @Test
    fun `boundary carries the request inputs through unchanged`() {
        val profile = fireRed
        val original = request(
            attacker = CalcPokemonInput(
                species = "Machamp",
                level = 62,
                nature = "Adamant",
                ivs = StatBlock(31, 31, 31, 31, 31, 31),
                evs = StatBlock(252, 252, 0, 0, 0, 6)
            ),
            defender = CalcPokemonInput(species = "Skarmory", level = 55, curHP = 140),
            move = CalcMoveInput(name = "Rock Slide", isCrit = true),
            field = CalcFieldInput(
                gameType = CalcGameTypes.SINGLES,
                weather = "Sand",
                defenderSide = SideConditions(isReflect = true)
            )
        )

        val ready = CalcRequestBoundary.build(profile, exactTrust(profile), original)
            as CalcRequestOutcome.Ready

        assertEquals("Machamp", ready.request.attacker.species)
        assertEquals(62, ready.request.attacker.level)
        assertEquals("Adamant", ready.request.attacker.nature)
        assertEquals("Skarmory", ready.request.defender.species)
        assertEquals(140, ready.request.defender.curHP)
        assertTrue(ready.request.move.isCrit)
        assertEquals("Sand", ready.request.field.weather)
        assertEquals(CalcGameTypes.SINGLES, ready.request.field.gameType)
        assertEquals(true, ready.request.field.defenderSide?.isReflect)
    }

    // ------------------------------------------------------ live-read boundary

    @Test
    fun `an untrusted live read is refused because the read is untrusted`() {
        // Situation 1: live inputs, ROM not exact-trusted.
        val profile = fireRed
        val prepared = completeLiveRead()

        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = null,
            request = prepared.request,
            inputsFromLiveRead = true
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("an untrusted read must be refused")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED))
        assertFalse(
            "the read is complete, so incompleteness is not also a reason",
            refused.verdict.limitations.contains(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)
        )
    }

    @Test
    fun `a trusted but incomplete live read is refused for the missing evidence`() {
        // Situation 2: exact-trusted ROM, but a required field is unknown. The ROM being verified
        // does not fill a field the reader never carried.
        val profile = fireRed
        val prepared = CalcInputPreparation.prepare(
            attacker = screenAttacker(machamp()),
            defender = screenDefender(null),
            move = CalcMoveInput(name = "Rock Slide"),
            field = CalcFieldInput()
        )

        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = exactTrust(profile),
            request = prepared.request,
            inputsFromLiveRead = true
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("missing evidence must refuse the calculation")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN))
        assertFalse(
            "the ROM is exact-trusted, so the read is not itself untrusted",
            refused.verdict.limitations.contains(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED)
        )
    }

    @Test
    fun `a trusted and complete live read is authorised on its actual inputs`() {
        // Situation 3, the regression the previous revision got wrong: it refused every live read
        // unconditionally, so supplying the correct ability and every other field still failed.
        // Reading a value from the game does not make it untrusted, and it must not be a permanent
        // prohibition on live calculations.
        val profile = fireRed
        val prepared = completeLiveRead()

        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = exactTrust(profile),
            request = prepared.request,
            inputsFromLiveRead = true
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("a trusted, complete live read must be calculable, got $outcome")

        assertTrue(ready.verdict.isVerified)
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN))
    }

    @Test
    fun `a verified hypothetical is disclosed as asserted, not observed`() {
        // A manual matchup on an exact-verified ROM is fully supported, but its values were asserted
        // by the user rather than read from the game. It must not read as a verified observation of
        // the battle in front of the player.
        val profile = fireRed
        val manual = CalcRequestBoundary.build(profile, exactTrust(profile), request())
            as CalcRequestOutcome.Ready

        val observed = CalcResultPresentation.forVerdict(manual.verdict, manual.request)
        assertTrue(
            "asserted values must be disclosed, was: ${observed.headline}",
            observed.headline.contains(CalcResultPresentation.ASSUMED_INPUTS_NOTE)
        )
        assertFalse(
            "a hypothetical must not present as an observed verified result",
            observed.isVerified
        )

        // The same verdict rendered without the request keeps the observation wording, so the
        // distinction is driven by provenance and not by the support level.
        assertTrue(CalcResultPresentation.forVerdict(manual.verdict).isVerified)
    }

    @Test
    fun `every public entrypoint makes the same authorization decision`() {
        // Request provenance must not disappear because a caller picks a different overload, and the
        // legacy Boolean must not be able to *erase* a live origin.
        val profile = fireRed
        val live = completeLiveRead().request
        assertEquals(CalcInputOrigin.LIVE_READ, live.attacker.origin)

        // Missing trust: refused through every entrypoint.
        listOf(
            "three-arg" to CalcRequestBoundary.build(profile, null, live),
            "flag true" to CalcRequestBoundary.build(profile, null, live, true),
            "flag false" to CalcRequestBoundary.build(profile, null, live, false)
        ).forEach { (name, outcome) ->
            val refused = outcome as? CalcRequestOutcome.Refused
                ?: throw AssertionError("$name must refuse an untrusted live read")
            assertTrue(
                "$name must report the untrusted read",
                refused.verdict.limitations.contains(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED)
            )
        }

        // Exact trust: authorised through every entrypoint, including the three-argument form.
        listOf(
            "three-arg" to CalcRequestBoundary.build(profile, exactTrust(profile), live),
            "flag true" to CalcRequestBoundary.build(profile, exactTrust(profile), live, true),
            "flag false" to CalcRequestBoundary.build(profile, exactTrust(profile), live, false)
        ).forEach { (name, outcome) ->
            val ready = outcome as? CalcRequestOutcome.Ready
                ?: throw AssertionError("$name must authorise a trusted complete live read")
            assertTrue(ready.verdict.isVerified)
        }

        // Mismatched trust: the profile is verified, the running bytes are not.
        val mismatch = trustFor(profile, listOf("00".repeat(32)))
        val refused = CalcRequestBoundary.build(profile, mismatch, live, true)
            as? CalcRequestOutcome.Refused
            ?: throw AssertionError("mismatched trust must refuse")
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.LIVE_INPUTS_NOT_VERIFIED))
    }

    @Test
    fun `the legacy flag cannot erase a live origin`() {
        // A caller that cannot set `origin` may add live provenance with the flag; passing false
        // must not convert an explicitly live request into a manual one.
        val profile = fireRed
        val live = completeLiveRead().request

        val viaFalse = CalcRequestBoundary.build(profile, null, live, false)
        val viaThreeArg = CalcRequestBoundary.build(profile, null, live)

        assertEquals(viaThreeArg::class, viaFalse::class)
        assertEquals(
            (viaThreeArg as CalcRequestOutcome.Refused).verdict.limitations,
            (viaFalse as CalcRequestOutcome.Refused).verdict.limitations
        )
    }

    @Test
    fun `an unrecognised live status is refused and is not silently treated as healthy`() {
        // R3: statusNameOf returned the sentinel, but toInput dropped it, so an unrecognised non-zero
        // condition became "no status" and the policy never saw anything to reject.
        val profile = fireRed
        // Bit 8 is outside every modelled position (bits 0-2 sleep, 3 psn, 4 brn, 5 frz,
        // 6 par, 7 tox) and the low three bits are clear, so nothing claims it.
        val unrecognised = machamp(statusCondition = 1L shl 8)

        assertEquals(
            CalcInputPreparation.UNKNOWN_STATUS,
            CalcInputPreparation.statusNameOf(unrecognised)
        )

        val prepared = CalcInputPreparation.prepare(
            attacker = CalcParticipantPreparationForTest(unrecognised),
            defender = screenDefender(null),
            move = CalcMoveInput(name = "Rock Slide"),
            field = CalcFieldInput()
        )

        // The sentinel must survive into the request, and the field must be recorded as unknown.
        assertEquals(CalcInputPreparation.UNKNOWN_STATUS, prepared.request.attacker.status)
        assertTrue(prepared.unknownLiveFields.contains(CalcInputField.STATUS))

        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = exactTrust(profile),
            request = prepared.request,
            inputsFromLiveRead = true
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("an unrecognised status must refuse the calculation")
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.STATUS_NOT_MODELLED))
    }

    @Test
    fun `an unrecognised status is refused even when the ability is known`() {
        // Positive control for the test above: it must fail because status information was lost,
        // not merely because the ability is always unknown for a live read. Here the ability is
        // supplied, so only the status can be the reason.
        val profile = fireRed
        val attacker = CalcParticipantState(
            species = "Machamp",
            level = 50,
            nature = "Hardy",
            ability = "Guts",
            item = null,
            status = CalcInputPreparation.UNKNOWN_STATUS,
            boosts = CalcParticipantState.NONE,
            curHP = 150,
            ivs = StatBlock(hp = 31, atk = 31, def = 31, spa = 31, spd = 31, spe = 31),
            evs = StatBlock(),
            origin = CalcInputOrigin.MANUAL,
            unknownFields = emptyList()
        )
        val prepared = CalcInputPreparation.prepare(
            attacker = attacker,
            defender = screenDefender(null),
            move = CalcMoveInput(name = "Rock Slide"),
            field = CalcFieldInput()
        )

        assertFalse(
            "ability is supplied, so incompleteness must not be the reason",
            prepared.preparationLimitations.contains(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)
        )

        val refused = CalcRequestBoundary.build(
            profile = profile,
            trust = exactTrust(profile),
            request = prepared.request
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("the lost status must be the reason it is refused")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.STATUS_NOT_MODELLED))
    }

    @Test
    fun `a zero status remains a known healthy observation`() {
        // The positive control for the whole status question: no status bits is information, not a
        // gap, so it must neither be recorded as unknown nor refused.
        val healthy = screenAttacker(machamp(statusCondition = 0L))

        assertNull(healthy.status)
        assertFalse(healthy.unknownFields.contains(CalcInputField.STATUS))

        val prepared = CalcInputPreparation.prepare(
            attacker = healthy.copy(ability = "Guts"),
            defender = screenDefender(null),
            move = CalcMoveInput(name = "Rock Slide"),
            field = CalcFieldInput()
        )
        assertNull(prepared.request.attacker.status)
        assertFalse(prepared.unknownLiveFields.contains(CalcInputField.STATUS))
    }

    @Test
    fun `manual matchup on an unverified ROM is still calculable as approximate`() {
        val outcome = CalcRequestBoundary.build(
            profile = fireRed,
            trust = null,
            request = request(),
            inputsFromLiveRead = false
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("a manual matchup reads no live state and may be estimated")
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
    }

    // --------------------------------------------- engine request serialisation

    @Test
    fun `authorised request serialises the boundary's generation`() {
        val profile = fireRed
        val ready = CalcRequestBoundary.build(profile, exactTrust(profile), request(gen = 8))
            as CalcRequestOutcome.Ready

        val serialised = JSONObject(buildCalcRequestJson(ready.request))
        assertEquals(3, serialised.getInt("gen"))
        assertEquals("Singles", serialised.getJSONObject("field").getString("gameType"))
    }

    @Test
    fun `the number the engine would apply the ability for is what gets serialised`() {
        // End of the chain: a leniently-spelled ability must arrive at the engine in the spelling
        // the engine matches, or the "verified" verdict would describe a calculation nobody ran.
        val profile = fireRed
        val ready = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(
                attacker = CalcPokemonInput(
                    species = "Machamp",
                    level = 50,
                    ability = "guts",
                    item = "choice band"
                ),
                defender = CalcPokemonInput(species = "Snorlax", level = 50, ability = "THICK FAT")
            )
        ) as CalcRequestOutcome.Ready

        val serialised = JSONObject(buildCalcRequestJson(ready.request))
        assertEquals("Guts", serialised.getJSONObject("attacker").getString("ability"))
        assertEquals("Choice Band", serialised.getJSONObject("attacker").getString("item"))
        assertEquals("Thick Fat", serialised.getJSONObject("defender").getString("ability"))
    }

    // ------------------------------------------------------- capability matrix

    @Test
    fun `blocking and non blocking limitations are classified explicitly`() {
        CalcLimitation.values().forEach { limitation ->
            when (limitation) {
                CalcLimitation.SPECIES_NOT_IN_PINNED_DATA,
                CalcLimitation.MOVE_NOT_IN_PINNED_DATA,
                CalcLimitation.BOOSTS_OUT_OF_RANGE,
                CalcLimitation.STAT_VALUES_OUT_OF_RANGE,
                CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE,
                CalcLimitation.FAIRY_TOGGLE_UNREADABLE,
                CalcLimitation.RANDOM_TYPES_UNREADABLE,
                CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE,
                CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED,
                CalcLimitation.HNS_ABILITY_SYSTEM_NOT_MODELLED,
                CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE,
                CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED,
                CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED,
                CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED,
                CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED,
                CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED,
                CalcLimitation.LIVE_INPUTS_NOT_VERIFIED,
                CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN,
                CalcLimitation.LEVEL_OUT_OF_RANGE,
                CalcLimitation.STATUS_NOT_MODELLED,
                CalcLimitation.FIELD_CONDITION_NOT_MODELLED ->
                    assertTrue("$limitation must block", limitation.blocksCalculation)
                else ->
                    assertFalse("$limitation must not block", limitation.blocksCalculation)
            }
        }
    }

    @Test
    fun `every limitation has user-facing wording`() {
        CalcLimitation.values().forEach { limitation ->
            assertTrue(
                "$limitation must describe itself",
                CalcCapabilityVerdict.describe(limitation).isNotBlank()
            )
        }
    }

    @Test
    fun `vanilla capability row models only abilities the engine applies`() {
        // Real Gen III abilities that the ADV pipeline does not model must not be whitelisted,
        // because the engine would silently ignore them.
        assertFalse(CalcCapabilityPolicy.GEN3_MODELLED_ABILITIES.contains("Multiscale"))
        assertFalse(CalcCapabilityPolicy.GEN3_MODELLED_ABILITIES.contains("Adaptability"))
        assertTrue(CalcCapabilityPolicy.GEN3_MODELLED_ABILITIES.contains("Thick Fat"))
        // H&S uses conditional ability support (Gap C2). Thick Fat and Guts are modelled equivalents; Overgrow is unsupported.
        assertTrue(CalcCapabilityPolicy.isAbilityModelled(CalcRuleset.HNS_2_0_5, "Thick Fat"))
        assertTrue(CalcCapabilityPolicy.isAbilityModelled(CalcRuleset.HNS_2_0_5, "Guts"))
        assertFalse(CalcCapabilityPolicy.isAbilityModelled(CalcRuleset.HNS_2_0_5, "Overgrow"))
        assertFalse(CalcCapabilityPolicy.isAbilityModelled(CalcRuleset.HNS_2_0_5, "Adaptability"))
        assertTrue(CalcCapabilityPolicy.isAbilityModelled(CalcRuleset.VANILLA_GEN3, "Thick Fat"))
        assertTrue(CalcCapabilityPolicy.isAbilityModelled(CalcRuleset.VANILLA_GEN3, "thick fat"))
        // The engine compares exactly, so a differently-cased name is only usable once it has been
        // rewritten to the spelling the engine matches.
        assertEquals("Thick Fat", CalcCapabilityPolicy.canonicalAbility("THICK FAT"))
        assertNull(CalcCapabilityPolicy.canonicalAbility("Multiscale"))
    }

    @Test
    fun `leniently spelled ability is authorised and sent in the engine's spelling`() {
        // Accepting "GUTS" is only safe because the request that reaches the engine says "Guts".
        // Passing "GUTS" through would make the engine silently ignore the ability and return an
        // unscaled number, which must never be reported as verified.
        val profile = fireRed
        val ready = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(attacker = CalcPokemonInput(species = "Machamp", level = 50, ability = "GUTS"))
        ) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("a recognisable ability spelling must be calculable")

        assertTrue(ready.verdict.isVerified)
        assertEquals("Guts", ready.request.attacker.ability)
    }

    @Test
    fun `leniently spelled item is authorised and sent in the engine's spelling`() {
        val profile = fireRed
        val ready = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(attacker = CalcPokemonInput(species = "Machamp", level = 50, item = "choice band"))
        ) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("a recognisable item spelling must be calculable")

        assertTrue(ready.verdict.isVerified)
        assertEquals("Choice Band", ready.request.attacker.item)
    }

    @Test
    fun `an ability the engine does not know is never rewritten`() {
        // Unknown names must pass through unchanged (and be refused elsewhere), never be silently
        // mapped onto something the engine does apply.
        val profile = fireRed
        val ready = CalcRequestBoundary.build(
            profile,
            exactTrust(profile),
            request(attacker = CalcPokemonInput(species = "Machamp", level = 50, ability = "Gutsy"))
        ) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("an unknown ability is a labelled estimate, not a refusal")

        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertTrue(ready.verdict.limitations.contains(CalcLimitation.ABILITY_NOT_MODELLED))
        assertEquals("Gutsy", ready.request.attacker.ability)
    }

    @Test
    fun `H and S required rule reads are all blocking`() {
        // These four are what makes an H&S number impossible to mistake for a Gen III one. If any
        // of them were demoted to non-blocking, H&S would start showing a number whose rule is
        // unknown, so the demotion cannot happen silently.
        CalcCapabilityPolicy.HNS_REQUIRED_RULE_READS.forEach { limitation ->
            assertTrue("$limitation must block", limitation.blocksCalculation)
        }
    }

    // ------------------------------------------ Gap B policy regression assertions

    @Test
    fun `H and S request with complete species and move overrides remains refused as UNSUPPORTED`() {
        val profile = heartAndSoul
        val baseReq = request(
            attacker = CalcPokemonInput(species = "Arbok", level = 50),
            defender = CalcPokemonInput(species = "Snorlax", level = 50),
            move = CalcMoveInput(name = "Tackle")
        )

        // Enrich through CalcDataOverrides (authoritative H&S overrides populated)
        val enriched = CalcDataOverrides.enrichRequest(profile, baseReq)
        assertNotNull(enriched.attackerOverride)
        assertNotNull(enriched.defenderOverride)
        assertNotNull(enriched.moveOverride)

        // Pass through CalcRequestBoundary
        val outcome = CalcRequestBoundary.build(profile, null, enriched)
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("H&S request with complete overrides must still be refused")

        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull("refused verdict must never expose an executable request", refused.verdict.request)
        CalcCapabilityPolicy.HNS_REQUIRED_RULE_READS.forEach { limitation ->
            assertTrue(
                "refusal must report blocking limitation $limitation",
                refused.verdict.limitations.contains(limitation)
            )
        }
    }

    @Test
    fun `exact runtime verified trust does not promote H and S with overrides to supported`() {
        val hnsSha256 = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"
        val hashed = heartAndSoul.copy(
            sha256Hashes = listOf(hnsSha256),
            isVerified = true,
            memoryLayoutVerified = true
        )
        val req = request(
            attacker = CalcPokemonInput(species = "Charizard", level = 50),
            defender = CalcPokemonInput(species = "Blastoise", level = 50),
            move = CalcMoveInput(name = "Flamethrower")
        )

        val outcome = CalcRequestBoundary.build(hashed, exactTrust(hashed), req)
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("exact verified trust must NOT promote H&S to supported")

        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertFalse(refused.verdict.isVerified)
        assertNull(refused.verdict.request)
    }

    @Test
    fun `vanilla FireRed and Emerald calculations remain unmodified and do not receive H and S overrides`() {
        listOf(fireRed, emerald).forEach { profile ->
            val req = request(
                attacker = CalcPokemonInput(species = "Machamp", level = 50),
                defender = CalcPokemonInput(species = "Snorlax", level = 50),
                move = CalcMoveInput(name = "Rock Slide")
            )
            val outcome = CalcRequestBoundary.build(profile, exactTrust(profile), req)
            val ready = outcome as? CalcRequestOutcome.Ready
                ?: throw AssertionError("${profile.id} must be ready")

            assertEquals(CalcSupport.VERIFIED, ready.verdict.support)
            assertTrue(ready.verdict.isVerified)
            assertNull("vanilla profile must not have attackerOverride", ready.request.attackerOverride)
            assertNull("vanilla profile must not have defenderOverride", ready.request.defenderOverride)
            assertNull("vanilla profile must not have moveOverride", ready.request.moveOverride)
        }
    }

    @Test
    fun `injected FireRed and Emerald overrides cannot survive into a VERIFIED request`() {
        listOf(fireRed, emerald).forEach { profile ->
            val maliciousReq = request(
                attacker = CalcPokemonInput(species = "Machamp", level = 50),
                defender = CalcPokemonInput(species = "Snorlax", level = 50),
                move = CalcMoveInput(name = "Rock Slide")
            ).copy(
                attackerOverride = CalcSpeciesOverride(
                    baseStats = StatBlock(hp = 999, atk = 999, def = 999, spa = 999, spd = 999, spe = 999),
                    types = listOf("Dragon")
                ),
                defenderOverride = CalcSpeciesOverride(
                    baseStats = StatBlock(hp = 1, atk = 1, def = 1, spa = 1, spd = 1, spe = 1),
                    types = listOf("Ghost")
                ),
                moveOverride = CalcMoveOverride(basePower = 999, type = "Dragon", category = "Physical")
            )

            val outcome = CalcRequestBoundary.build(profile, exactTrust(profile), maliciousReq)
            val ready = outcome as? CalcRequestOutcome.Ready
                ?: throw AssertionError("${profile.id} must be authorized")

            assertTrue(ready.verdict.isVerified)
            assertEquals(CalcSupport.VERIFIED, ready.verdict.support)
            assertNull("injected attackerOverride must be stripped by boundary", ready.request.attackerOverride)
            assertNull("injected defenderOverride must be stripped by boundary", ready.request.defenderOverride)
            assertNull("injected moveOverride must be stripped by boundary", ready.request.moveOverride)
        }
    }

    @Test
    fun `bogus H and S overrides get replaced by the pinned values`() {
        val profile = heartAndSoul
        val bogusReq = request(
            attacker = CalcPokemonInput(species = "Arbok", level = 50),
            defender = CalcPokemonInput(species = "Snorlax", level = 50),
            move = CalcMoveInput(name = "Tackle")
        ).copy(
            attackerOverride = CalcSpeciesOverride(
                baseStats = StatBlock(hp = 999, atk = 999, def = 999, spa = 999, spd = 999, spe = 999),
                types = listOf("Ghost")
            ),
            moveOverride = CalcMoveOverride(basePower = 999, type = "Fire", category = "Status")
        )

        val enriched = CalcDataOverrides.enrichRequest(profile, bogusReq)
        assertEquals(95, enriched.attackerOverride!!.baseStats.atk)
        assertEquals(listOf("Poison"), enriched.attackerOverride!!.types)
        assertEquals(40, enriched.moveOverride!!.basePower)
        assertEquals("Normal", enriched.moveOverride!!.type)
        assertEquals("Physical", enriched.moveOverride!!.category)
    }

    @Test
    fun `ambiguous H and S species cannot smuggle a supplied override through the boundary`() {
        val profile = heartAndSoul
        val smuggledOverride = CalcSpeciesOverride(
            baseStats = StatBlock(hp = 100, atk = 100, def = 100, spa = 100, spd = 100, spe = 100),
            types = listOf("Normal")
        )
        val smuggledReq = request(
            defender = CalcPokemonInput(species = "Eevee", level = 50)
        ).copy(defenderOverride = smuggledOverride)

        val outcome = CalcRequestBoundary.build(profile, null, smuggledReq)
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("ambiguous form name Eevee must be refused even if override is supplied")

        assertTrue(refused.verdict.limitations.contains(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA))
        assertNull("refused verdict must never expose an executable request", refused.verdict.request)
    }

    // ------------------------------------------ Gap A runtime rules tests

    @Test
    fun `1 Missing settings exact-style H and S profile retains unreadable limitations and no request`() {
        val (profile, trust) = exactHnsProfile()
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(
                attacker = CalcPokemonInput(species = "Charizard", level = 50),
                defender = CalcPokemonInput(species = "Blastoise", level = 50),
                move = CalcMoveInput(name = "Flamethrower")
            ),
            challengeSettings = null
        )

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("missing challenge settings must refuse calculation")

        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull("refused verdict must never expose an executable request", refused.verdict.request)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.FAIRY_TOGGLE_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPES_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED))
    }

    @Test
    fun `2 Fully observed ordinary settings clears unreadable blockers but H and S remains UNSUPPORTED due to Gap C`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(
            optionStyle = 0,
            fairyTypes = 1,
            randomTypes = 0,
            randomEffectiveness = 0
        )
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(
                attacker = CalcPokemonInput(species = "Charizard", level = 50),
                defender = CalcPokemonInput(species = "Blastoise", level = 50),
                move = CalcMoveInput(name = "Flamethrower")
            ),
            challengeSettings = snapshot
        )

        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("H&S must remain refused due to Gap C type-chart incompatibility")

        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull("refused verdict must never expose an executable request", refused.verdict.request)

        // All unreadable limitations must be gone
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.FAIRY_TOGGLE_UNREADABLE))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPES_UNREADABLE))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE))

        // Gap C1 closed: HNS_TYPE_CHART_NOT_MODELLED is cleared under observed rules and representable types
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED))

        // Gap C3 blocker: HNS_HELD_ITEM_SYSTEM_NOT_MODELLED keeps H&S strictly UNSUPPORTED
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_SYSTEM_NOT_MODELLED))
    }

    @Test
    fun `3 Observed zero semantics raw 0 is authoritative observation not unknown`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(
            optionStyle = 0,
            fairyTypes = 0, // Observed Fairy OFF
            randomTypes = 0, // Observed Random Types OFF
            randomEffectiveness = 0 // Observed Random Effectiveness OFF
        )
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(),
            challengeSettings = snapshot
        )

        val refused = outcome as CalcRequestOutcome.Refused
        // None of the fields observed as raw=0 should be marked unreadable
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.FAIRY_TOGGLE_UNREADABLE))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPES_UNREADABLE))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE))
        // And neither of the random toggles is active, so active blockers must not be present
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED))
    }

    @Test
    fun `4 PER_MOVE_SPLIT optionStyle 0 retains authoritative move category`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(optionStyle = 0)
        // Crunch in H&S data pack is Physical (power 80, Dark). In Gen 3, Dark is type-based Special.
        val baseReq = request(
            attacker = CalcPokemonInput(species = "Tyranitar", level = 50),
            defender = CalcPokemonInput(species = "Snorlax", level = 50),
            move = CalcMoveInput(name = "Crunch")
        )

        val rules = CalcRequestBoundary.resolveHnsRuntimeRules(profile, trust, snapshot)
        assertNotNull(rules)
        assertEquals(HnsOptionStyle.PER_MOVE_SPLIT, rules!!.optionStyle)

        val enriched = CalcDataOverrides.enrichRequest(profile, baseReq, rules)
        assertNotNull(enriched.moveOverride)
        assertEquals("Dark", enriched.moveOverride!!.type)
        assertEquals("Physical", enriched.moveOverride!!.category)

        val json = JSONObject(buildCalcRequestJson(enriched))
        val moveObj = json.getJSONObject("move").getJSONObject("overrides")
        assertEquals("Physical", moveObj.getString("category"))
    }

    @Test
    fun `5 TYPE_BASED optionStyle 1 omits move category so engine derives category from move type`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(optionStyle = 1)
        val baseReq = request(
            attacker = CalcPokemonInput(species = "Tyranitar", level = 50),
            defender = CalcPokemonInput(species = "Snorlax", level = 50),
            move = CalcMoveInput(name = "Crunch")
        )

        val rules = CalcRequestBoundary.resolveHnsRuntimeRules(profile, trust, snapshot)
        assertNotNull(rules)
        assertEquals(HnsOptionStyle.TYPE_BASED, rules!!.optionStyle)

        val enriched = CalcDataOverrides.enrichRequest(profile, baseReq, rules)
        assertNotNull(enriched.moveOverride)
        assertEquals("Dark", enriched.moveOverride!!.type)
        assertNull("TYPE_BASED optionStyle must omit category in move override", enriched.moveOverride!!.category)

        val json = JSONObject(buildCalcRequestJson(enriched))
        val moveObj = json.getJSONObject("move").getJSONObject("overrides")
        assertFalse("category must be omitted from move overrides JSON", moveObj.has("category"))
    }

    @Test
    fun `6 Random Types active observed raw 1 adds RANDOM_TYPES_ACTIVE_NOT_MODELLED and blocks`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(randomTypes = 1)
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(),
            challengeSettings = snapshot
        )

        val refused = outcome as CalcRequestOutcome.Refused
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull(refused.verdict.request)
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPES_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED))
    }

    @Test
    fun `7 Random Type Effectiveness active observed raw 1 adds RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED and blocks`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(randomEffectiveness = 1)
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(),
            challengeSettings = snapshot
        )

        val refused = outcome as CalcRequestOutcome.Refused
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull(refused.verdict.request)
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED))
    }

    @Test
    fun `8 Invalid settings snapshot or out of domain field fails closed`() {
        val (profile, trust) = exactHnsProfile()

        // 8a. Snapshot marked OBSERVED_INVALID
        val invalidStatusSnapshot = hnsSettingsSnapshot(
            status = HnsChallengeSettingsStatus.OBSERVED_INVALID
        )
        val outcomeInvalid = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(),
            challengeSettings = invalidStatusSnapshot
        )
        val refusedInvalid = outcomeInvalid as CalcRequestOutcome.Refused
        assertTrue(refusedInvalid.verdict.limitations.contains(CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE))
        assertTrue(refusedInvalid.verdict.limitations.contains(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE))

        // 8b. Single field marked outOfDomain
        val outOfDomainSnapshot = hnsSettingsSnapshot(
            optionStyleOutOfDomain = true
        )
        val outcomeOod = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(),
            challengeSettings = outOfDomainSnapshot
        )
        val refusedOod = outcomeOod as CalcRequestOutcome.Refused
        assertTrue(refusedOod.verdict.limitations.contains(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE))
    }

    @Test
    fun `9 Trust gate recognized unverified H and S with valid snapshot does NOT consume it`() {
        val unverifiedProfile = heartAndSoul
        val snapshot = hnsSettingsSnapshot(
            optionStyle = 0,
            fairyTypes = 1,
            randomTypes = 0,
            randomEffectiveness = 0
        )
        val outcome = CalcRequestBoundary.build(
            profile = unverifiedProfile,
            trust = null,
            request = request(),
            challengeSettings = snapshot
        )

        val refused = outcome as CalcRequestOutcome.Refused
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull(refused.verdict.request)
        // Must retain all unreadable limitations because trust is not exact-verified
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.FAIRY_TOGGLE_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPES_UNREADABLE))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE))
    }

    @Test
    fun `10 Wrong profile vanilla FireRed and Emerald ignore supplied H and S settings snapshot`() {
        val snapshot = hnsSettingsSnapshot(optionStyle = 0)
        listOf(fireRed, emerald).forEach { profile ->
            val outcome = CalcRequestBoundary.build(
                profile = profile,
                trust = exactTrust(profile),
                request = request(
                    attacker = CalcPokemonInput(species = "Machamp", level = 50),
                    defender = CalcPokemonInput(species = "Snorlax", level = 50),
                    move = CalcMoveInput(name = "Rock Slide")
                ),
                challengeSettings = snapshot
            )

            val ready = outcome as? CalcRequestOutcome.Ready
                ?: throw AssertionError("${profile.id} must be ready and ignore H&S settings")
            assertEquals(CalcSupport.VERIFIED, ready.verdict.support)
            assertTrue(ready.verdict.isVerified)
            assertNull("vanilla request must not carry hnsRuntimeRules", ready.request.hnsRuntimeRules)
        }
    }

    @Test
    fun `11 Caller override regression caller supplied species move overrides and runtime rules are discarded`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(optionStyle = 1)
        val spoofedRules = CalcHnsRuntimeRules(optionStyle = HnsOptionStyle.PER_MOVE_SPLIT)
        val spoofedOverride = CalcMoveOverride(basePower = 999, type = "Normal", category = "Special")
        val req = request(
            attacker = CalcPokemonInput(species = "Tyranitar", level = 50),
            defender = CalcPokemonInput(species = "Snorlax", level = 50),
            move = CalcMoveInput(name = "Crunch")
        ).copy(
            moveOverride = spoofedOverride,
            hnsRuntimeRules = spoofedRules
        )

        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = req,
            challengeSettings = snapshot
        )
        val refused = outcome as CalcRequestOutcome.Refused
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertNull("refused verdict must never expose an executable request", refused.verdict.request)

        // Boundary must have rebuilt rules from snapshot (TYPE_BASED), NOT spoofedRules (PER_MOVE_SPLIT)
        // And rebuild moveOverride from HeartAndSoul205DataPack with power 80, NOT 999!
        val enriched = CalcDataOverrides.enrichRequest(
            profile,
            req,
            CalcRequestBoundary.resolveHnsRuntimeRules(profile, trust, snapshot)
        )
        assertEquals(80, enriched.moveOverride!!.basePower)
        assertNull("TYPE_BASED from authoritative snapshot must omit category", enriched.moveOverride!!.category)
        assertEquals(HnsOptionStyle.TYPE_BASED, enriched.hnsRuntimeRules!!.optionStyle)
    }

    @Test
    fun `12 Gap C1 unrepresentable type triggers UNREPRESENTABLE_TYPE_NOT_MODELLED and keeps HNS_TYPE_CHART_NOT_MODELLED`() {
        val (profile, trust) = exactHnsProfile()
        val rules = CalcHnsRuntimeRules(
            optionStyle = HnsOptionStyle.PER_MOVE_SPLIT,
            fairyTypesEnabled = true,
            randomTypesEnabled = false,
            randomTypeEffectivenessEnabled = false
        )
        val req = request(
            attacker = CalcPokemonInput(species = "Charizard", level = 50),
            defender = CalcPokemonInput(species = "Blastoise", level = 50),
            move = CalcMoveInput(name = "Flamethrower")
        ).copy(
            hnsRuntimeRules = rules,
            attackerOverride = CalcSpeciesOverride(
                baseStats = StatBlock(78, 84, 78, 109, 85, 100),
                types = listOf("FakeType")
            )
        )

        val verdict = CalcCapabilityPolicy.evaluate(profile, trust, req)
        assertEquals(CalcSupport.UNSUPPORTED, verdict.support)
        assertTrue(verdict.limitations.contains(CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED))
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED))
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED))
    }

    @Test
    fun `13 Gap C1 random types active keeps HNS_TYPE_CHART_NOT_MODELLED`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(
            optionStyle = 0,
            fairyTypes = 1,
            randomTypes = 1, // Random Types ON
            randomEffectiveness = 0
        )
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(
                attacker = CalcPokemonInput(species = "Charizard", level = 50),
                defender = CalcPokemonInput(species = "Blastoise", level = 50),
                move = CalcMoveInput(name = "Flamethrower")
            ),
            challengeSettings = snapshot
        )
        val refused = outcome as CalcRequestOutcome.Refused
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED))
    }

    @Test
    fun `14 Gap C1 random effectiveness active keeps HNS_TYPE_CHART_NOT_MODELLED`() {
        val (profile, trust) = exactHnsProfile()
        val snapshot = hnsSettingsSnapshot(
            optionStyle = 0,
            fairyTypes = 1,
            randomTypes = 0,
            randomEffectiveness = 1 // Random Type Effectiveness ON
        )
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = request(
                attacker = CalcPokemonInput(species = "Charizard", level = 50),
                defender = CalcPokemonInput(species = "Blastoise", level = 50),
                move = CalcMoveInput(name = "Flamethrower")
            ),
            challengeSettings = snapshot
        )
        val refused = outcome as CalcRequestOutcome.Refused
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED))
    }

    @Test
    fun `15 Gap C1 exact HnS request with missing or wrong typeSystem retains HNS_TYPE_CHART_NOT_MODELLED`() {
        val (profile, trust) = exactHnsProfile()
        val rules = CalcHnsRuntimeRules(
            optionStyle = HnsOptionStyle.PER_MOVE_SPLIT,
            fairyTypesEnabled = true,
            randomTypesEnabled = false,
            randomTypeEffectivenessEnabled = false
        )
        val baseReq = request(
            attacker = CalcPokemonInput(species = "Charizard", level = 50),
            defender = CalcPokemonInput(species = "Blastoise", level = 50),
            move = CalcMoveInput(name = "Flamethrower")
        ).copy(hnsRuntimeRules = rules)

        // Case A: typeSystem is null
        val reqNullTypeSystem = baseReq.copy(typeSystem = null)
        val verdictNull = CalcCapabilityPolicy.evaluate(profile, trust, reqNullTypeSystem)
        assertTrue(
            "missing typeSystem must retain HNS_TYPE_CHART_NOT_MODELLED",
            verdictNull.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED)
        )

        // Case B: typeSystem is wrong / arbitrary string
        val reqWrongTypeSystem = baseReq.copy(typeSystem = "wrong_system")
        val verdictWrong = CalcCapabilityPolicy.evaluate(profile, trust, reqWrongTypeSystem)
        assertTrue(
            "wrong typeSystem must retain HNS_TYPE_CHART_NOT_MODELLED",
            verdictWrong.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED)
        )

        // Case C: typeSystem is correctly "hns_2_0_5"
        val reqCorrectTypeSystem = baseReq.copy(typeSystem = "hns_2_0_5")
        val verdictCorrect = CalcCapabilityPolicy.evaluate(profile, trust, reqCorrectTypeSystem)
        assertFalse(
            "correct typeSystem hns_2_0_5 clears HNS_TYPE_CHART_NOT_MODELLED",
            verdictCorrect.limitations.contains(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED)
        )
    }
}
