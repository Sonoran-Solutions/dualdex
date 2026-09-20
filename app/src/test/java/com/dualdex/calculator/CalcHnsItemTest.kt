package com.dualdex.calculator

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.pokemon.hns.HnsItemCategory
import com.dualdex.pokemon.hns.HnsItemRegistry
import com.dualdex.pokemon.hns.HnsOptionStyle
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Held-item authority at the production calculator boundary (issue #9, Gap C3).
 *
 * These tests drive [CalcParticipantPresenter] and [CalcRequestBoundary], the same
 * production paths the Calc screen uses. H&S intentionally stays UNSUPPORTED in C3
 * (badge boost is the next blocker), so item behaviour is asserted through the
 * per-participant limitations: an unsupported damage item adds
 * [CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED], an unreadable current item adds
 * [CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE], and ITEM_NONE adds neither.
 */
class CalcHnsItemTest {

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

    // Pinned H&S item IDs from the exact catalogue/source.
    private val itemNone = 0
    private val charcoal = 426        // HOLD_EFFECT_TYPE_POWER, x1.2
    private val choiceBand = 442      // HOLD_EFFECT_CHOICE_BAND
    private val amuletCoin = 466      // HOLD_EFFECT_DOUBLE_PRIZE (proven no-damage)

    private fun exactTrust(profile: RomHackProfile): RuntimeRomTrust = RuntimeRomTrust.from(
        compatibility = RomCompatibility.verified(profile, profile.sha256Hashes.first()),
        activeRomSha256 = profile.sha256Hashes.first()
    )

    private fun hnsSettingsSnapshot(): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txModeFairyTypes = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txRandomType = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomTypeEffectiveness = HnsChallengeField(observed = true, raw = 0, outOfDomain = false)
    )

    private fun abilityNoneObservation(
        status: HnsBattlerRuntimeStatus = HnsBattlerRuntimeStatus.OBSERVED,
        partySlot: Int = 0,
        itemId: Int? = itemNone,
        itemOutOfDomain: Boolean = false
    ): BattlerRuntimeObservation = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = status,
            battlerIndex = 0,
            partySlot = partySlot,
            abilityId = 0,
            abilityOutOfDomain = false,
            types = emptyList(),
            itemId = itemId,
            itemOutOfDomain = itemOutOfDomain
        ),
        abilityIdentity = DeclaredAbility.EmptySlot,
        itemIdentity = null
    )

    private fun dummyParsedPokemon(heldItem: Int): ParsedPokemon = ParsedPokemon(
        isValid = true,
        isEmpty = false,
        pid = 123456L,
        tid = 1000,
        sid = 2000,
        nickname = "Machamp",
        otName = "Red",
        species = 68,
        heldItem = heldItem,
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

    private fun liveInput(
        species: String,
        partySlot: Int,
        itemId: Int?,
        provenance: CalcItemProvenance,
        item: String? = null,
        itemOutOfDomain: Boolean = false,
        unknownFields: List<CalcInputField> = emptyList()
    ): CalcPokemonInput = CalcPokemonInput(
        species = species,
        level = 50,
        item = item,
        ability = "None",
        abilityId = 0,
        origin = CalcInputOrigin.LIVE_READ,
        partySlot = partySlot,
        itemId = itemId,
        itemProvenance = provenance,
        itemOutOfDomain = itemOutOfDomain,
        unknownFields = unknownFields
    )

    private fun baseRequest(
        attacker: CalcPokemonInput,
        defender: CalcPokemonInput = CalcPokemonInput(
            species = "Swampert", level = 50, ability = "None", abilityId = 0
        ),
        moveName: String = "Cross Chop"
    ): DamageCalculationRequest = DamageCalculationRequest(
        gen = 3,
        typeSystem = "hns_2_0_5",
        attacker = attacker,
        defender = defender,
        move = CalcMoveInput(name = moveName)
    )

    private fun HnsItemCategory.assertSupported() = assertTrue("$this must be supported", isSupportedForDamage)

    // ------------------------------------------------------------------
    // Presenter: stored party item vs current battle item
    // ------------------------------------------------------------------

    @Test
    fun `presenter active player uses current battle item over stored party item`() {
        val state = CalcParticipantPresenter.attacker(
            party = listOf(dummyParsedPokemon(heldItem = itemNone)),
            selectedIndex = 0,
            speciesNameOf = { "Machamp" },
            playerBattlerState = abilityNoneObservation(itemId = charcoal),
            isExactHns = true,
            activeBattle = true
        )
        assertEquals(CalcItemProvenance.BATTLE_EFFECTIVE, state.itemProvenance)
        assertEquals(charcoal, state.itemId)
    }

    @Test
    fun `presenter never resurrects a stored party item after a battle mutation`() {
        val state = CalcParticipantPresenter.attacker(
            party = listOf(dummyParsedPokemon(heldItem = charcoal)),
            selectedIndex = 0,
            speciesNameOf = { "Machamp" },
            playerBattlerState = abilityNoneObservation(itemId = itemNone),
            isExactHns = true,
            activeBattle = true
        )
        assertEquals(CalcItemProvenance.BATTLE_EFFECTIVE, state.itemProvenance)
        assertEquals(itemNone, state.itemId)
    }

    @Test
    fun `presenter bench keeps the parsed party item and ignores the active battler item`() {
        val state = CalcParticipantPresenter.attacker(
            party = listOf(dummyParsedPokemon(heldItem = itemNone), dummyParsedPokemon(heldItem = charcoal)),
            selectedIndex = 1,
            speciesNameOf = { "Machamp" },
            playerBattlerState = abilityNoneObservation(partySlot = 0, itemId = itemNone),
            isExactHns = true,
            activeBattle = true
        )
        assertEquals(CalcItemProvenance.PARTY_STORAGE, state.itemProvenance)
        assertEquals(charcoal, state.itemId)
    }

    @Test
    fun `presenter active battle with no observation fails closed`() {
        val state = CalcParticipantPresenter.attacker(
            party = listOf(dummyParsedPokemon(heldItem = charcoal)),
            selectedIndex = 0,
            speciesNameOf = { "Machamp" },
            playerBattlerState = null,
            isExactHns = true,
            activeBattle = true
        )
        assertEquals(CalcItemProvenance.UNKNOWN, state.itemProvenance)
        assertNull(state.itemId)
        assertTrue(state.unknownFields.contains(CalcInputField.ITEM))
    }

    @Test
    fun `presenter out of battle uses party storage`() {
        val state = CalcParticipantPresenter.attacker(
            party = listOf(dummyParsedPokemon(heldItem = charcoal)),
            selectedIndex = 0,
            speciesNameOf = { "Machamp" },
            playerBattlerState = null,
            isExactHns = true,
            activeBattle = false
        )
        assertEquals(CalcItemProvenance.PARTY_STORAGE, state.itemProvenance)
        assertEquals(charcoal, state.itemId)
    }

    // ------------------------------------------------------------------
    // Boundary: precedence, spoofing, ITEM_NONE
    // ------------------------------------------------------------------

    private fun outcomeFor(
        attacker: CalcPokemonInput,
        playerBattlerState: BattlerRuntimeObservation? = null,
        enemyBattlerState: BattlerRuntimeObservation? = null,
        activeBattle: Boolean = true,
        defender: CalcPokemonInput = CalcPokemonInput(species = "Swampert", level = 50, ability = "None", abilityId = 0),
        moveName: String = "Cross Chop"
    ): CalcCapabilityVerdict {
        val outcome = CalcRequestBoundary.build(
            profile = heartAndSoul,
            trust = exactTrust(heartAndSoul),
            request = baseRequest(attacker, defender, moveName),
            challengeSettings = hnsSettingsSnapshot(),
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            activeBattle = activeBattle
        )
        return when (outcome) {
            is CalcRequestOutcome.Ready -> outcome.verdict
            is CalcRequestOutcome.Refused -> outcome.verdict
        }
    }

    @Test
    fun `authoritative battle item wins over a spoofed supported caller item`() {
        // Caller claims Amulet Coin (supported) but the engine says Charcoal for the same slot.
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = amuletCoin,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "Amulet Coin"
            ),
            playerBattlerState = abilityNoneObservation(itemId = charcoal)
        )
        assertTrue(
            "the authoritative unsupported battle item must win",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED)
        )
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
    }

    @Test
    fun `authoritative ITEM_NONE after a battle mutation is not overridden by the party item`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = charcoal,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "CHARCOAL"
            ),
            playerBattlerState = abilityNoneObservation(itemId = itemNone)
        )
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED))
    }

    @Test
    fun `bench participant keeps party storage and does not copy the active item`() {
        val bench = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 1, itemId = charcoal,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "CHARCOAL"
            ),
            playerBattlerState = abilityNoneObservation(partySlot = 0, itemId = itemNone)
        )
        assertTrue(
            "the bench party item must be used, not the active battler's ITEM_NONE",
            bench.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED)
        )
    }

    @Test
    fun `active battle with unreadable current item fails closed`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = charcoal,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "CHARCOAL"
            ),
            playerBattlerState = null,
            activeBattle = true
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `numeric item id is the authority when the display name disagrees`() {
        // Numeric ID is a damage item, display name says a harmless supported item.
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = charcoal,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "Amulet Coin"
            ),
            activeBattle = false
        )
        assertTrue(
            "capability must follow the numeric ID",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED)
        )
    }

    @Test
    fun `an out of range id with a false valid flag fails closed`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = 901,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "AMULET COIN",
                itemOutOfDomain = false
            ),
            activeBattle = false
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
    }

    @Test
    fun `ITEM_NONE clears the blanket and every item specific blocker`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = itemNone,
                provenance = CalcItemProvenance.PARTY_STORAGE
            ),
            activeBattle = false
        )
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE))
    }

    // ------------------------------------------------------------------
    // Opponent
    // ------------------------------------------------------------------

    @Test
    fun `opponent matching slot accepts the current battle item`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = itemNone,
                provenance = CalcItemProvenance.BATTLE_EFFECTIVE
            ),
            defender = liveInput(
                species = "Swampert", partySlot = 0, itemId = itemNone,
                provenance = CalcItemProvenance.PARTY_STORAGE
            ),
            enemyBattlerState = abilityNoneObservation(partySlot = 0, itemId = charcoal),
            activeBattle = true
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `opponent slot mismatch fails closed rather than using the party item`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = itemNone,
                provenance = CalcItemProvenance.BATTLE_EFFECTIVE
            ),
            defender = liveInput(
                species = "Swampert", partySlot = 0, itemId = charcoal,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "CHARCOAL"
            ),
            enemyBattlerState = abilityNoneObservation(partySlot = 1, itemId = itemNone),
            activeBattle = true
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
    }

    @Test
    fun `opponent doubles ambiguity fails closed`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = itemNone,
                provenance = CalcItemProvenance.BATTLE_EFFECTIVE
            ),
            defender = liveInput(
                species = "Swampert", partySlot = 0, itemId = charcoal,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "CHARCOAL"
            ),
            enemyBattlerState = abilityNoneObservation(
                status = HnsBattlerRuntimeStatus.AMBIGUOUS, partySlot = 0, itemId = itemNone
            ),
            activeBattle = true
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
    }

    // ------------------------------------------------------------------
    // Manual input
    // ------------------------------------------------------------------

    @Test
    fun `manual item resolves through the exact catalogue and classifies by id`() {
        val unsupported = CalcCapabilityPolicy.evaluate(
            heartAndSoul,
            exactTrust(heartAndSoul),
            baseRequest(
                attacker = CalcPokemonInput(species = "Machamp", level = 50, item = "Charcoal", ability = "None"),
            ).copy(hnsRuntimeRules = CalcRequestBoundary.resolveHnsRuntimeRules(heartAndSoul, exactTrust(heartAndSoul), hnsSettingsSnapshot()))
        )
        assertTrue(unsupported.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))

        val supported = CalcCapabilityPolicy.evaluate(
            heartAndSoul,
            exactTrust(heartAndSoul),
            baseRequest(
                attacker = CalcPokemonInput(species = "Machamp", level = 50, item = "Amulet Coin", ability = "None"),
            ).copy(hnsRuntimeRules = CalcRequestBoundary.resolveHnsRuntimeRules(heartAndSoul, exactTrust(heartAndSoul), hnsSettingsSnapshot()))
        )
        assertFalse(supported.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(supported.limitations.contains(CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE))
    }

    @Test
    fun `manual item not in the exact catalogue fails closed`() {
        val verdict = CalcCapabilityPolicy.evaluate(
            heartAndSoul,
            exactTrust(heartAndSoul),
            baseRequest(
                attacker = CalcPokemonInput(species = "Machamp", level = 50, item = "Totally Fake Item", ability = "None"),
            ).copy(hnsRuntimeRules = CalcRequestBoundary.resolveHnsRuntimeRules(heartAndSoul, exactTrust(heartAndSoul), hnsSettingsSnapshot()))
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE))
    }

    // ------------------------------------------------------------------
    // Next blocker / production refusal
    // ------------------------------------------------------------------

    @Test
    fun `otherwise covered request clears item blockers and is held by the badge blocker`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = itemNone,
                provenance = CalcItemProvenance.BATTLE_EFFECTIVE
            ),
            playerBattlerState = abilityNoneObservation(itemId = itemNone)
        )
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(verdict.limitations.contains(CalcLimitation.BADGE_BOOST_NOT_MODELLED))
        assertEquals(CalcSupport.UNSUPPORTED, verdict.support)
        assertNull(verdict.request)
    }

    // ------------------------------------------------------------------
    // Contextual move/item interaction (review R1)
    // ------------------------------------------------------------------

    @Test
    fun `proven utility item with an ordinary move clears both item audits`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = amuletCoin,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "Amulet Coin"
            ),
            activeBattle = false,
            moveName = "Tackle"
        )
        assertFalse(
            "an ordinary move must not add the item-dependent-move blocker",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED)
        )
        assertFalse(
            "Amulet Coin's own hold effect is still ordinary-damage-free",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED)
        )
    }

    @Test
    fun `the same utility item with Fling is refused as item dependent`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = amuletCoin,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "Amulet Coin"
            ),
            activeBattle = false,
            moveName = "Fling"
        )
        assertTrue(
            "Fling's base power is the attacker's item identity",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED)
        )
        // The item's own hold effect is still supported; the move interaction is the reason.
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals(CalcSupport.UNSUPPORTED, verdict.support)
        assertNull(verdict.request)
    }

    @Test
    fun `ITEM_NONE with Acrobatics is refused as item dependent`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = itemNone,
                provenance = CalcItemProvenance.PARTY_STORAGE
            ),
            activeBattle = false,
            moveName = "Acrobatics"
        )
        assertTrue(
            "Acrobatics doubles when the attacker has no item, so ITEM_NONE is not context-free",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED)
        )
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `defender held utility item with Knock Off is refused as item dependent`() {
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = itemNone,
                provenance = CalcItemProvenance.PARTY_STORAGE
            ),
            defender = liveInput(
                species = "Swampert", partySlot = 0, itemId = amuletCoin,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "Amulet Coin"
            ),
            activeBattle = false,
            moveName = "Knock Off"
        )
        assertTrue(
            "Knock Off's x1.5 depends on the defender holding an item",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED)
        )
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `manual utility item with an item dependent move is refused`() {
        val verdict = outcomeFor(
            attacker = CalcPokemonInput(species = "Machamp", level = 50, item = "Amulet Coin", ability = "None"),
            activeBattle = false,
            moveName = "Fling"
        )
        assertTrue(verdict.limitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
    }

    // ------------------------------------------------------------------
    // Boundary: battle context is authoritative, not caller-declared (review R2)
    // ------------------------------------------------------------------

    @Test
    fun `live read cannot downgrade to party storage while an active battler is observed`() {
        // The raw caller claims a supported party item AND declares activeBattle = false, but hands
        // over an authoritative current battler whose item is Charcoal. The observation is itself
        // battle evidence, so the current item must win: the weaker party-storage path must not be
        // taken just because the caller omitted/declared false battle context.
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = amuletCoin,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "Amulet Coin"
            ),
            playerBattlerState = abilityNoneObservation(itemId = charcoal),
            activeBattle = false
        )
        assertTrue(
            "the authoritative current battle item must win",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED)
        )
        assertFalse(
            "the read was readable, so it must not be reported unreadable",
            verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE)
        )
        assertFalse(
            "an ordinary move must not add the interaction blocker",
            verdict.limitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED)
        )
    }

    @Test
    fun `live read with an active observation and no battle declaration still clears a stale party item`() {
        // Same loophole, mirror image: the caller declares no battle and a party Charcoal, but the
        // observation is an authoritative ITEM_NONE. The current item must win and the stale party
        // item must not be resurrected.
        val verdict = outcomeFor(
            attacker = liveInput(
                species = "Machamp", partySlot = 0, itemId = charcoal,
                provenance = CalcItemProvenance.PARTY_STORAGE, item = "CHARCOAL"
            ),
            playerBattlerState = abilityNoneObservation(itemId = itemNone),
            activeBattle = false
        )
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
    }

    // ------------------------------------------------------------------
    // Registry / audit pins and vanilla isolation
    // ------------------------------------------------------------------

    @Test
    fun `representative damage items stay unsupported including choice band`() {
        assertEquals(HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, HnsItemRegistry.classify(charcoal).category)
        assertEquals(HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, HnsItemRegistry.classify(choiceBand).category)
        // ITEM_NONE is the only always-supported identity here.
        assertEquals(HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT, HnsItemRegistry.classify(itemNone).category)
    }

    @Test
    fun `vanilla item behaviour is unchanged and H and S capability does not leak`() {
        // Vanilla Gen III Charcoal is a modelled x1.1 type booster: no H&S blocker applies.
        assertNull(CalcCapabilityPolicy.itemLimitation(CalcRuleset.VANILLA_GEN3, "Charcoal"))
        // The same name under H&S is the H&S x1.2 item and is refused.
        assertEquals(
            CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED,
            CalcCapabilityPolicy.itemLimitation(CalcRuleset.HNS_2_0_5, "Charcoal")
        )
        // Vanilla still resolves its own item names, never through the H&S catalogue.
        assertEquals("Charcoal", CalcCapabilityPolicy.canonicalItem("charcoal"))
        assertNull(CalcCapabilityPolicy.canonicalItem("Amulet Coin"))
        // The H&S no-damage subset is not accepted for vanilla either; it is simply unmodelled.
        assertEquals(
            CalcLimitation.ITEM_NOT_MODELLED,
            CalcCapabilityPolicy.itemLimitation(CalcRuleset.VANILLA_GEN3, "Amulet Coin")
        )
        assertTrue(HnsItemRegistry.classify(amuletCoin).category.isSupportedForDamage)
    }
}
