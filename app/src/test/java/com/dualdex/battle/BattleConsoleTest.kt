package com.dualdex.battle

import com.dualdex.calculator.DamageCalculationRequest
import com.dualdex.calculator.DamageCalculationResponse
import com.dualdex.calculator.CalcLimitation
import com.dualdex.calculator.CalcSupport
import com.dualdex.calculator.CalcEngineOperandEcho
import com.dualdex.calculator.HnsAbilitySide
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsBattlerTypeObservation
import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.emulator.InputManager
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RuntimeRomTrust
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BattleConsoleTest {

    private fun createTestPokemon(
        species: Int = 6, // Charizard
        level: Int = 50,
        nickname: String = "Charizard",
        natureName: String = "Hardy",
        moves: IntArray = intArrayOf(7, 247, 14, 0), // Fire Punch, Shadow Ball, Swords Dance, None
        pp: IntArray = intArrayOf(15, 15, 30, 0),
        currentHp: Int = 150,
        maxHp: Int = 150,
        statusCondition: Long = 0L,
        isValid: Boolean = true,
        isEmpty: Boolean = false
    ): ParsedPokemon {
        return ParsedPokemon(
            isValid = isValid,
            isEmpty = isEmpty,
            pid = 123456L,
            tid = 1000,
            sid = 2000,
            nickname = nickname,
            otName = "Red",
            species = species,
            heldItem = 0,
            level = level,
            nature = 0,
            natureName = natureName,
            isShiny = false,
            abilitySlot = 0,
            isEgg = false,
            friendship = 255,
            experience = 100000L,
            hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
            hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
            moves = moves,
            pp = pp,
            currentHp = currentHp,
            maxHp = maxHp,
            attack = 100,
            defense = 100,
            speed = 100,
            spAttack = 100,
            spDefense = 100,
            statusCondition = statusCondition
        )
    }

    private val stubCalculator = BattleDamageCalculator { _ ->
        DamageCalculationResponse(
            success = true,
            minDamage = 40,
            maxDamage = 48,
            range = listOf(40, 42, 44, 46, 48),
            koChanceText = "guaranteed 3HKO"
        )
    }

    private val radicalRedProfile = RomHackProfile(
        id = "radical_red",
        name = "Pokemon Radical Red",
        baseGame = "FireRed",
        gameId = 7,
        engine = "CFRU",
        hasPhysSpecSplit = true,
        steelResistsGhostDark = false,
        isVerified = true
    )

    private val unverifiedProfile = RomHackProfile(
        id = "custom_unknown",
        name = "Custom Unknown",
        baseGame = "FireRed",
        gameId = 0,
        engine = "Custom",
        hasPhysSpecSplit = false,
        isVerified = false
    )

    private val exactHash = "c".repeat(64)
    private val bundledHnsHash = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"

    private fun exactProfile(profile: RomHackProfile = RomHackProfile.DEFAULT_FIRERED): RomHackProfile =
        profile.copy(sha256Hashes = listOf(exactHash))

    private fun exactTrust(
        profile: RomHackProfile,
        method: ProfileMatchMethod = ProfileMatchMethod.EXACT_SHA256,
        romHash: String = exactHash
    ): RuntimeRomTrust =
        RuntimeRomTrust(
            matchMethod = method,
            detectedSha256 = romHash,
            activeRomSha256 = romHash,
            profileVerified = profile.isVerified,
            memoryLayoutVerified = profile.memoryLayoutVerified,
            profileSha256Hashes = profile.sha256Hashes
        )

    private val hnsProfile by lazy {
        RomHackProfile(
            id = "heart_and_soul",
            name = "Pokemon Heart & Soul",
            baseGame = "Emerald",
            gameId = 8,
            developer = "Lil Dill / PokemonHnS-Development",
            engine = "pokeemerald-expansion",
            hasEvs = true,
            hasIvs = true,
            hasPhysSpecSplit = true,
            steelResistsGhostDark = false,
            cfruOffsets = false,
            playerPartyOffset = 33769316,
            enemyPartyOffset = 33768116,
            docsUrl = "https://pokemonhns-development.github.io/pokehns-expansion-documentation/",
            headerTitles = listOf("HEARTSOUL", "HNS", "POKEHNS", "HEART", "SOUL"),
            sha256Hashes = listOf(bundledHnsHash),
            isVerified = true,
            interactiveControlsVerified = false,
            memoryLayoutVerified = true,
            battleStateReadVerified = true,
            battleUiVerified = false,
            gameDataPackId = "hns_2_0_5",
        )
    }

    private val hnsTrust by lazy { exactTrust(hnsProfile, romHash = bundledHnsHash) }

    private fun hnsSettings(optionStyle: Int = 0, fairyTypesEnabled: Boolean = true,
                            randomAbilities: Boolean = false) = HnsChallengeSettingsSnapshot(
        status = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = HnsChallengeField(true, optionStyle, false),
        txModeFairyTypes = HnsChallengeField(true, if (fairyTypesEnabled) 1 else 0, false),
        txRandomType = HnsChallengeField(true, 0, false),
        txRandomTypeEffectiveness = HnsChallengeField(true, 0, false),
        txRandomAbilities = HnsChallengeField(true, if (randomAbilities) 1 else 0, false),
        txRandomMoves = HnsChallengeField(true, 0, false),
        txChallengesNoEvs = HnsChallengeField(true, 0, false),
        txChallengesBaseStatEqualizer = HnsChallengeField(true, 0, false),
        txChallengesMirror = HnsChallengeField(true, 0, false),
        txChallengesMirrorThief = HnsChallengeField(true, 0, false),
        txChallengesTrainerScalingIvs = HnsChallengeField(true, 0, false),
        txChallengesTrainerScalingEvs = HnsChallengeField(true, 0, false),
        txChallengesMaxPartyIvs = HnsChallengeField(true, 0, false),
        txModeSturdy = HnsChallengeField(true, 1, false),
        txChallengesLevelCap = HnsChallengeField(true, 0, false),
        txChallengesExpMultiplier = HnsChallengeField(true, 0, false),
        txModeLegendaryAbilities = HnsChallengeField(true, 1, false)
    )

    private fun hnsBattler(
        partySlot: Int,
        battlerIndex: Int,
        typeIds: List<Int>,
        abilityId: Int = 0,
        abilityName: String = "None",
        itemId: Int = 0,
        battlersCount: Int = 2,
        hp: Int = 20,
        maxHp: Int = 20,
        speciesId: Int? = if (battlerIndex == 0) 152 else 16,
        status1: Int = 0,
        fieldStatuses: Int = 0,
        battleWeather: Int = 0,
        sideStatuses: Int = 0
    ) = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = HnsBattlerRuntimeStatus.OBSERVED,
            battlerIndex = battlerIndex,
            partySlot = partySlot,
            speciesId = speciesId,
            abilityId = abilityId,
            abilityOutOfDomain = false,
            types = typeIds.map { HnsBattlerTypeObservation(observed = true, raw = it, outOfDomain = false) },
            itemId = itemId,
            itemOutOfDomain = false,
            statsObserved = true,
            rawAttack = 45,
            rawDefense = 35,
            rawSpeed = 30,
            rawSpAttack = 40,
            rawSpDefense = 40,
            stagesObserved = true,
            statStages = List(8) { 0 },
            badgesObserved = true,
            badgeBoostAtk = false,
            badgeBoostDef = false,
            badgeBoostSpe = false,
            badgeBoostSpa = false,
            badgeBoostSpd = false,
            rawBadgesByte = 0,
            absentBattlerFlags = 0,
            absentFlagsReadable = true,
            battlersCount = battlersCount,
            battlersCountReadable = true,
            hpObserved = true,
            hp = hp,
            maxHp = maxHp,
            statusObserved = true,
            status1 = status1,
            volatilesObserved = true,
            transientVolatilesObserved = true,
            persistentVolatilesObserved = true,
            gimmickObserved = true,
            activeGimmick = 0,
            fieldStatusesReadable = true,
            fieldStatuses = fieldStatuses,
            weatherReadable = true,
            battleWeather = battleWeather,
            sideStatusesReadable = true,
            sideStatuses = sideStatuses
        ),
        abilityIdentity = if (abilityId == 0) DeclaredAbility.EmptySlot else DeclaredAbility.Declared(abilityId, abilityName)
    )

    private fun hnsContext(
        optionStyle: Int = 0,
        playerAbilityId: Int = 0,
        playerAbilityName: String = "None",
        playerItemId: Int = 0,
        fairyTypesEnabled: Boolean = true,
        randomAbilities: Boolean = false,
        battlersCount: Int = 2,
        playerObservation: BattlerRuntimeObservation? = null,
        enemyObservation: BattlerRuntimeObservation? = null
    ): BattleHnsCalculationContext {
        val party = listOf(createTestPokemon(species = 152, moves = intArrayOf(33, 129, 0, 0), pp = intArrayOf(35, 25, 0, 0)))
        return BattleHnsCalculationContext(
            playerParty = party,
            activePlayerSlot = 0,
            activeEnemySlot = 0,
            challengeSettings = hnsSettings(optionStyle, fairyTypesEnabled, randomAbilities),
            playerBattlerState = playerObservation ?: hnsBattler(
                partySlot = 0,
                battlerIndex = 0,
                typeIds = listOf(13), // Grass
                abilityId = playerAbilityId,
                abilityName = playerAbilityName,
                itemId = playerItemId,
                battlersCount = battlersCount
            ),
            enemyBattlerState = enemyObservation ?: hnsBattler(
                partySlot = 0,
                battlerIndex = 1,
                typeIds = listOf(1, 3), // Normal / Flying
                battlersCount = battlersCount
            ),
            activeBattle = true
        )
    }

    private fun buildHnsPresentation(
        moveId: Int,
        context: BattleHnsCalculationContext,
        calculator: BattleDamageCalculator = BattleDamageCalculator { request ->
            DamageCalculationResponse(
                success = true,
                minDamage = 8,
                maxDamage = 12,
                range = listOf(8, 10, 12),
                moveCategory = request.moveOverride?.category ?: "Physical"
            )
        },
        defender: ParsedPokemon = createTestPokemon(species = 16, nickname = "Pidgey")
    ): MovePresentation {
        val attacker = context.playerParty[context.activePlayerSlot]
        val pack = GameDataPackRegistry.getForProfile(
            hnsProfile.engine,
            hnsProfile.hasPhysSpecSplit,
            hnsProfile.gameDataPackId
        )
        return BattlePresentationBuilder.build(
            moveInfo = MoveDatabase.get(moveId, pack),
            currentPp = attacker.pp.firstOrNull(),
            attacker = attacker,
            defender = defender,
            profile = hnsProfile,
            runtimeTrust = hnsTrust,
            calculator = calculator,
            attackerStages = StatStages(),
            defenderStages = StatStages(),
            hnsCalculationContext = context
        )
    }

    // 1. Move metadata unavailable / fallback behavior
    @Test
    fun testMoveMetadataUnavailable_fallbackBehavior() {
        val unknownMoveId = 9999
        assertFalse("Move 9999 must not be known", MoveDatabase.isKnown(unknownMoveId))

        val attacker = createTestPokemon(moves = intArrayOf(unknownMoveId, 0, 0, 0), pp = intArrayOf(10, 0, 0, 0))
        val defender = createTestPokemon(species = 9) // Blastoise

        val pres = BattlePresentationBuilder.build(
            moveInfo = MoveDatabase.get(unknownMoveId),
            currentPp = 10,
            attacker = attacker,
            defender = defender,
            profile = RomHackProfile.DEFAULT_FIRERED,
            calculator = stubCalculator
        )

        assertFalse(pres.isKnown)
        assertNull("Unknown move base power must be null instead of fabricating 50", pres.basePower)
        assertEquals("—", pres.powerDisplay)
        assertNull("Unknown move accuracy must be null instead of fabricating 100", pres.accuracy)
        assertEquals("—", pres.accuracyDisplay)
        assertNull("Unknown move max PP must be null instead of fabricating 20", pres.maxPp)
        assertEquals("10/—", pres.ppDisplay)
        assertNull("Unknown move category must be null", pres.category)
        assertEquals("—", pres.categoryDisplay)
        assertEquals(MoveEffectiveness.UNAVAILABLE, pres.effectiveness)
        assertEquals(DataConfidence.UNAVAILABLE, pres.effectivenessConfidence)
        assertEquals(DamageConfidence.UNAVAILABLE, pres.damageConfidence)
        assertEquals("Damage unavailable for this ROM/profile", pres.damageDisplayText)
        assertEquals("Move metadata unavailable for this ROM/profile", pres.description)
    }

    // 2. Unknown species: does not fabricate data or Normal typing
    @Test
    fun testUnknownSpecies_doesNotFabricateDataOrNormalTyping() {
        val unknownSpeciesId = 8888
        assertFalse("Species 8888 must not be known", SpeciesDatabase.isKnown(unknownSpeciesId))

        val unknownMon = createTestPokemon(species = unknownSpeciesId, nickname = "")
        val summary = ParticipantSummaryBuilder.build(unknownMon, 0, RomHackProfile.DEFAULT_FIRERED)

        assertFalse("Unknown species must not be marked verified", summary.isVerified)
        assertEquals(DataConfidence.UNAVAILABLE, summary.confidence)
        assertTrue("Species name should reflect unknown status", summary.speciesName.contains("Unknown"))
        assertTrue("Type names must be empty rather than fabricating Normal typing", summary.typeNames.isEmpty())

        val (defT1, defT2) = MoveEffectiveness.defenderTypesOf(unknownMon, RomHackProfile.DEFAULT_FIRERED)
        assertNull("Defender type 1 must be null for unknown species", defT1)
        assertNull("Defender type 2 must be null for unknown species", defT2)

        val attacker = createTestPokemon(species = 6)
        val pres = BattlePresentationBuilder.build(
            moveInfo = MoveDatabase.get(7), // Fire Punch
            currentPp = 15,
            attacker = attacker,
            defender = unknownMon,
            profile = RomHackProfile.DEFAULT_FIRERED,
            calculator = stubCalculator
        )
        assertEquals(DataConfidence.UNAVAILABLE, pres.effectivenessConfidence)
        assertEquals(DamageConfidence.UNAVAILABLE, pres.damageConfidence)
        assertEquals("Damage unavailable for this ROM/profile", pres.damageDisplayText)
    }

    @Test
    fun participantSpeciesUsesProfileAwareGen3AndModernMetadata() {
        val fireRed = exactProfile()
        val fireRedTrust = exactTrust(fireRed)
        val clefairy = ParticipantSummaryBuilder.build(
            createTestPokemon(species = 35, nickname = ""), 0, fireRed, runtimeTrust = fireRedTrust
        )
        val jigglypuff = ParticipantSummaryBuilder.build(
            createTestPokemon(species = 39, nickname = ""), 0, fireRed, runtimeTrust = fireRedTrust
        )

        assertEquals(listOf("Normal"), clefairy.typeNames)
        assertEquals(DataConfidence.VERIFIED, clefairy.confidence)
        assertEquals(listOf("Normal"), jigglypuff.typeNames)
        assertEquals(DataConfidence.VERIFIED, jigglypuff.confidence)

        val modern = exactProfile(
            RomHackProfile(
                id = "modern",
                name = "Modern Hack",
                baseGame = "FireRed",
                gameId = 7,
                engine = "CFRU",
                hasPhysSpecSplit = true,
                isVerified = true,
                memoryLayoutVerified = true,
                gameDataPackId = "modern"
            )
        )
        val modernClefairy = ParticipantSummaryBuilder.build(
            createTestPokemon(species = 35, nickname = ""), 0, modern, runtimeTrust = exactTrust(modern)
        )
        assertEquals(listOf("Fairy"), modernClefairy.typeNames)
    }

    @Test
    fun participantConfidenceRequiresAuthoritativeMetadataAndExactRuntime() {
        val fireRed = exactProfile()
        val fallbackSpecies = ParticipantSummaryBuilder.build(
            createTestPokemon(species = 6), 0, fireRed, runtimeTrust = exactTrust(fireRed)
        )
        assertEquals(DataConfidence.ESTIMATE, fallbackSpecies.confidence)
        assertFalse(fallbackSpecies.isVerified)

        val filenameDetected = ParticipantSummaryBuilder.build(
            createTestPokemon(species = 35), 0, fireRed,
            runtimeTrust = exactTrust(fireRed, ProfileMatchMethod.FILENAME_KEYWORD)
        )
        assertEquals(DataConfidence.ESTIMATE, filenameDetected.confidence)
        assertFalse(filenameDetected.isVerified)

        val unknown = ParticipantSummaryBuilder.build(
            createTestPokemon(species = 8888), 0, fireRed, runtimeTrust = exactTrust(fireRed)
        )
        assertEquals(DataConfidence.UNAVAILABLE, unknown.confidence)
        assertTrue(unknown.typeNames.isEmpty())

        val customProfile = fireRed.copy(
            customSpecies = mapOf(35 to com.dualdex.romhack.SpeciesOverride("Clefairy", "Normal"))
        )
        val custom = ParticipantSummaryBuilder.build(
            createTestPokemon(species = 35), 0, customProfile, runtimeTrust = exactTrust(customProfile)
        )
        assertEquals(DataConfidence.VERIFIED, custom.confidence)

        val statusData = FieldStatusBuilder.build(
            inBattle = true,
            attacker = createTestPokemon(species = 6, statusCondition = 1L shl 4),
            defender = createTestPokemon(species = 9),
            profile = fireRed,
            runtimeTrust = exactTrust(fireRed)
        )
        assertTrue("Directly observed status bytes retain memory confidence", statusData.conditionVerified)
    }

    // 3. Profile physical/special split behavior
    @Test
    fun testProfilePhysicalSpecialSplitBehavior() {
        val firePunch = MoveDatabase.get(7) // Fire type
        val shadowBall = MoveDatabase.get(247) // Ghost type
        val swordsDance = MoveDatabase.get(14) // Normal status move

        // Vanilla Gen 3: type-based category
        val vanilla = RomHackProfile.DEFAULT_FIRERED
        assertFalse(vanilla.hasPhysSpecSplit)
        assertEquals(MoveCategory.SPECIAL, MoveEffectiveness.resolveMoveCategory(firePunch, vanilla))
        assertEquals(MoveCategory.PHYSICAL, MoveEffectiveness.resolveMoveCategory(shadowBall, vanilla))
        assertEquals(MoveCategory.STATUS, MoveEffectiveness.resolveMoveCategory(swordsDance, vanilla))

        // Split enabled (e.g. Radical Red)
        val splitProfile = radicalRedProfile
        assertTrue(splitProfile.hasPhysSpecSplit)
        assertEquals(MoveCategory.PHYSICAL, MoveEffectiveness.resolveMoveCategory(firePunch, splitProfile))
        assertEquals(MoveCategory.SPECIAL, MoveEffectiveness.resolveMoveCategory(shadowBall, splitProfile))
        assertEquals(MoveCategory.STATUS, MoveEffectiveness.resolveMoveCategory(swordsDance, splitProfile))
    }

    // 4. Effectiveness confidence
    @Test
    fun testEffectivenessConfidence() {
        val defender = createTestPokemon(species = 6) // Charizard (Fire/Flying)
        val waterGun = MoveDatabase.get(55) // Water Gun (Water)
        val swordsDance = MoveDatabase.get(14) // Status move

        // Known type matchup: Water vs Fire/Flying = 2x, with conservative data confidence.
        val (effLabel, confidence) = MoveEffectiveness.evaluate(waterGun.id, waterGun.category, defender, RomHackProfile.DEFAULT_FIRERED)
        assertEquals(EffectivenessLabel.SUPER_EFFECTIVE, effLabel)
        // Water Gun falls through the shared database; the Gen 3 pack exposes the value for
        // presentation but does not claim it is an explicitly verified Gen 3 entry.
        assertEquals(DataConfidence.ESTIMATE, confidence)

        // Status move has unavailable effectiveness
        val (statusLabel, statusConfidence) = MoveEffectiveness.evaluate(swordsDance.id, MoveCategory.STATUS, defender, RomHackProfile.DEFAULT_FIRERED)
        assertNull(statusLabel)
        assertEquals(DataConfidence.UNAVAILABLE, statusConfidence)

        // Null defender has unavailable effectiveness
        val (nullDefLabel, nullDefConfidence) = MoveEffectiveness.evaluate(waterGun.id, waterGun.category, null, RomHackProfile.DEFAULT_FIRERED)
        assertNull(nullDefLabel)
        assertEquals(DataConfidence.UNAVAILABLE, nullDefConfidence)

        // Steel resists Ghost in Gen 3 vanilla, but neutral when steelResistsGhostDark = false
        val magnemite = createTestPokemon(species = 81) // Electric/Steel
        val shadowBall = MoveDatabase.get(247) // Ghost
        val (steelGen3Label, _) = MoveEffectiveness.evaluate(shadowBall.id, MoveCategory.PHYSICAL, magnemite, RomHackProfile.DEFAULT_FIRERED)
        assertEquals(EffectivenessLabel.NOT_VERY_EFFECTIVE, steelGen3Label)

        val (steelGen6Label, _) = MoveEffectiveness.evaluate(shadowBall.id, MoveCategory.SPECIAL, magnemite, radicalRedProfile)
        assertEquals(EffectivenessLabel.NEUTRAL, steelGen6Label)
    }

    @Test
    fun effectivenessUsesNaturalPokemonLanguageAtTwoAndFourTimes() {
        val twoTimes = MoveEffectiveness.confidence(
            moveType = PokemonType.WATER,
            defenderType1 = PokemonType.FIRE
        )
        val fourTimes = MoveEffectiveness.confidence(
            moveType = PokemonType.WATER,
            defenderType1 = PokemonType.FIRE,
            defenderType2 = PokemonType.GROUND
        )

        assertEquals("Super Effective (2x)", twoTimes?.displayName)
        assertEquals("Extremely Effective (4x)", fourTimes?.displayName)
    }

    // 5. Damage confidence
    @Test
    fun testDamageConfidence() {
        val attacker = createTestPokemon(species = 6) // Charizard
        val defender = createTestPokemon(species = 9) // Blastoise
        val firePunch = MoveDatabase.get(7)
        val swordsDance = MoveDatabase.get(14)

        // The calculator can still produce a range, but Fire Punch is not an explicitly
        // verified Gen 3 pack entry, so the range is conservative.
        val verifiedPres = BattlePresentationBuilder.build(
            moveInfo = firePunch,
            currentPp = 15,
            attacker = attacker,
            defender = defender,
            profile = RomHackProfile.DEFAULT_FIRERED,
            calculator = stubCalculator
        )
        assertEquals(DamageConfidence.ESTIMATE, verifiedPres.damageConfidence)
        assertFalse(verifiedPres.hasDamage)
        assertEquals(40, verifiedPres.minDamage)
        assertEquals(48, verifiedPres.maxDamage)

        val context = hnsContext()
        val fireRedWithLiveSnapshots = BattlePresentationBuilder.build(
            moveInfo = firePunch,
            currentPp = 15,
            attacker = attacker,
            defender = defender,
            profile = RomHackProfile.DEFAULT_FIRERED,
            calculator = stubCalculator,
            hnsCalculationContext = context
        )
        assertEquals(verifiedPres, fireRedWithLiveSnapshots)

        val emerald = RomHackProfile.DEFAULT_FIRERED.copy(
            id = "vanilla_emerald_test",
            name = "Pokemon Emerald",
            baseGame = "Emerald",
            gameId = 3
        )
        val emeraldPres = BattlePresentationBuilder.build(
            moveInfo = firePunch,
            currentPp = 15,
            attacker = attacker,
            defender = defender,
            profile = emerald,
            calculator = stubCalculator,
            hnsCalculationContext = context
        )
        assertEquals(DamageConfidence.ESTIMATE, emeraldPres.damageConfidence)
        assertEquals(verifiedPres.minDamage, emeraldPres.minDamage)
        assertEquals(verifiedPres.maxDamage, emeraldPres.maxDamage)

        // Split/CFRU profile: Gen 3 calc cannot produce verified range
        val hackPres = BattlePresentationBuilder.build(
            moveInfo = firePunch,
            currentPp = 15,
            attacker = attacker,
            defender = defender,
            profile = radicalRedProfile,
            calculator = stubCalculator
        )
        assertEquals(DamageConfidence.UNAVAILABLE, hackPres.damageConfidence)
        assertEquals("Damage unavailable for this ROM/profile", hackPres.damageDisplayText)

        // Status move: damage unavailable
        val statusPres = BattlePresentationBuilder.build(
            moveInfo = swordsDance,
            currentPp = 30,
            attacker = attacker,
            defender = defender,
            profile = RomHackProfile.DEFAULT_FIRERED,
            calculator = stubCalculator
        )
        assertEquals(DamageConfidence.UNAVAILABLE, statusPres.damageConfidence)

        // Missing defender: damage unavailable
        val noDefPres = BattlePresentationBuilder.build(
            moveInfo = firePunch,
            currentPp = 15,
            attacker = attacker,
            defender = null,
            profile = RomHackProfile.DEFAULT_FIRERED,
            calculator = stubCalculator
        )
        assertEquals(DamageConfidence.UNAVAILABLE, noDefPres.damageConfidence)
    }

    @Test
    fun exactHnsBattlePresentationExecutesOnlyBoundaryAuthorizedEstimatedRequests() {
        val sentRequests = mutableListOf<DamageCalculationRequest>()
        val calculator = BattleDamageCalculator { request ->
            sentRequests += request
            DamageCalculationResponse(
                success = true,
                minDamage = 10,
                maxDamage = 14,
                range = listOf(10, 12, 14),
                moveCategory = request.moveOverride?.category ?: "Physical"
            )
        }
        val supportedContext = hnsContext()
        val attacker = supportedContext.playerParty[0]
        val defender = createTestPokemon(species = 16, nickname = "Pidgey")
        val supported = buildHnsPresentation(33, supportedContext, calculator)

        assertEquals(DamageConfidence.ESTIMATE, supported.damageConfidence)
        assertEquals(CalcSupport.ESTIMATED, supported.calculatorSupport)
        assertTrue(supported.minDamage > 0)
        assertTrue(supported.maxDamage >= supported.minDamage)
        assertEquals(MoveCategory.PHYSICAL, supported.category)
        assertEquals(1, sentRequests.size)
        assertEquals("hns_2_0_5", sentRequests.single().typeSystem)
        assertEquals("Tackle", sentRequests.single().move.name)
        assertEquals(CalcSupport.ESTIMATED, supported.calculatorSupport)
        assertFalse(supported.damageConfidence == DamageConfidence.VERIFIED)

        // A runtime ability change invalidates the presentation key and the production boundary
        // refuses the new state instead of retaining the prior neutral-state range.
        val changedContext = hnsContext(
            playerAbilityId = 62,
            playerAbilityName = "Guts",
            playerObservation = hnsBattler(0, 0, listOf(13), 62, "Guts", status1 = 0x10)
        )
        val oldKey = BattleMovePresentationCacheKey.from(
            attacker, defender, hnsProfile, hnsTrust, StatStages(), StatStages(), supportedContext
        )
        val changedKey = BattleMovePresentationCacheKey.from(
            attacker, defender, hnsProfile, hnsTrust, StatStages(), StatStages(), changedContext
        )
        assertNotEquals(oldKey, changedKey)

        val refused = buildHnsPresentation(33, changedContext, calculator)
        assertEquals(DamageConfidence.UNAVAILABLE, refused.damageConfidence)
        assertTrue(refused.damageLimitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        // The statused Guts holder is refused for two independent reasons; neither hides the other.
        assertEquals("2 blockers", refused.damageUnavailableReason)
        assertEquals(listOf("Status not modelled", "You: Guts"), refused.damageBlockers.map { it.detail })
        assertEquals("the refusal must never reach the calculator", 1, sentRequests.size)
    }

    @Test
    fun randomAbilityUsesObservedEffectiveIdAndNamesTheBlockingSide() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val calculator = BattleDamageCalculator { request ->
            sent += request
            DamageCalculationResponse(success = true, minDamage = 8, maxDamage = 12,
                range = listOf(8, 10, 12), moveCategory = "Physical")
        }
        // Chikorita normally has Overgrow. The observed randomized Static ID wins.
        val safe = hnsContext(playerAbilityId = 9, playerAbilityName = "Static", randomAbilities = true)
        val estimated = buildHnsPresentation(33, safe, calculator)
        assertEquals(DamageConfidence.ESTIMATE, estimated.damageConfidence)
        assertEquals(1, sent.size)
        assertEquals(9, sent.single().attacker.abilityId)

        val harmful = hnsContext(
            playerAbilityId = 62,
            playerAbilityName = "Guts",
            randomAbilities = true,
            playerObservation = hnsBattler(0, 0, listOf(13), 62, "Guts", status1 = 0x10)
        )
        val refused = buildHnsPresentation(33, harmful, calculator)
        assertEquals(DamageConfidence.UNAVAILABLE, refused.damageConfidence)
        assertEquals("Your Guts not modelled",
            refused.damageBlockers.filterIsInstance<DamageBlockerPresentation.Ability>().single().headline)
        assertEquals("2 blockers", refused.damageUnavailableReason)
        assertEquals(1, sent.size)

        val defenderIrrelevant = hnsContext(playerAbilityId = 9, playerAbilityName = "Static", randomAbilities = true,
            enemyObservation = hnsBattler(0, 1, listOf(1, 3), 26, "Levitate"))
        val opponentEstimated = buildHnsPresentation(33, defenderIrrelevant, calculator)
        assertEquals(DamageConfidence.ESTIMATE, opponentEstimated.damageConfidence)
        assertEquals(2, sent.size)

        val defenderHarmful = hnsContext(playerAbilityId = 9, playerAbilityName = "Static", randomAbilities = true,
            enemyObservation = hnsBattler(0, 1, listOf(1, 3), 26, "Levitate"))
        val opponentRefused = buildHnsPresentation(89, defenderHarmful, calculator) // Earthquake
        assertEquals(DamageConfidence.UNAVAILABLE, opponentRefused.damageConfidence)
        // Earthquake is also outside the ordinary move subset; both reasons stay visible.
        assertEquals("2 blockers", opponentRefused.damageUnavailableReason)
        assertEquals(listOf("Foe: Levitate", "Move effect not modelled"), opponentRefused.damageBlockers.map { it.detail })
        assertEquals("Foe's Levitate not modelled", opponentRefused.damageBlockers.first().headline)
        assertEquals(2, sent.size)

        val unresolved = buildHnsPresentation(33,
            hnsContext(playerAbilityId = 80, playerAbilityName = "Steadfast", randomAbilities = true), calculator)
        assertEquals(DamageConfidence.UNAVAILABLE, unresolved.damageConfidence)
        assertEquals("Your Steadfast not yet audited", unresolved.damageUnavailableReason)
        assertEquals(2, sent.size)
    }

    @Test
    fun `random Tera Shell attacker and Truant defender reach an estimated battle card`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val calculator = BattleDamageCalculator { request ->
            sent += request
            DamageCalculationResponse(success = true, minDamage = 8, maxDamage = 12,
                range = listOf(8, 10, 12), moveCategory = "Physical")
        }
        val context = hnsContext(
            playerAbilityId = 308,
            playerAbilityName = "Tera Shell",
            randomAbilities = true,
            enemyObservation = hnsBattler(0, 1, listOf(1, 3), 54, "Truant")
        )
        val result = buildHnsPresentation(33, context, calculator)
        assertEquals(DamageConfidence.ESTIMATE, result.damageConfidence)
        assertTrue(result.damageLimitations.none {
            it == CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED
        })
        assertTrue(result.damageAbilityBlockers.isEmpty())
        assertEquals(1, sent.size)
        assertEquals(308, sent.single().attacker.abilityId)
        assertEquals(54, sent.single().defender.abilityId)
    }

    @Test
    fun `relevant abilities retain structured multi-blocker presentation and never call calculator`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val calculator = BattleDamageCalculator { request ->
            sent += request
            DamageCalculationResponse(success = true, minDamage = 8, maxDamage = 12,
                range = listOf(8, 10, 12), moveCategory = "Physical")
        }
        val context = hnsContext(
            playerAbilityId = 37,
            playerAbilityName = "Huge Power",
            randomAbilities = true,
            enemyObservation = hnsBattler(
                0, 1, listOf(1), 308, "Tera Shell", hp = 20, maxHp = 20, speciesId = 1432
            )
        )
        val defender = createTestPokemon(species = 1432, nickname = "Terapagos")
        val result = buildHnsPresentation(33, context, calculator, defender)
        assertEquals(DamageConfidence.UNAVAILABLE, result.damageConfidence)
        // Every blocker class stays visible: the live-state gap does not hide either ability.
        assertEquals("${result.damageBlockers.size} blockers", result.damageUnavailableReason)
        assertTrue(result.damageBlockers.map { it.detail }.containsAll(
            listOf("Live battle state incomplete", "You: Huge Power", "Foe: Tera Shell")
        ))
        assertTrue(result.damageUnavailableText.startsWith("Damage unavailable · ${result.damageBlockers.size} blockers\n"))
        assertEquals(2, result.damageAbilityBlockers.size)
        assertEquals(listOf(HnsAbilitySide.ATTACKER, HnsAbilitySide.DEFENDER),
            result.damageAbilityBlockers.map { it.side })
        assertEquals(listOf("Huge Power", "Tera Shell"),
            result.damageAbilityBlockers.map { it.abilityName })
        assertEquals(0, sent.size)
    }

    @Test
    fun hnsThirdSlotMysteryIsIgnoredForLiveDamageAndEffectiveness() {
        val sentRequests = mutableListOf<DamageCalculationRequest>()
        val calculator = BattleDamageCalculator { request ->
            sentRequests += request
            DamageCalculationResponse(
                success = true,
                minDamage = 10,
                maxDamage = 14,
                range = listOf(10, 12, 14),
                moveCategory = request.moveOverride?.category ?: "Physical"
            )
        }
        // These are the raw H&S engine tuples observed for Chikorita and Hoothoot. Slot 2's
        // Mystery is the engine's ordinary no-added-third-type state.
        val realRuntimeTuples = hnsContext().copy(
            playerBattlerState = hnsBattler(0, 0, listOf(13, 13, 10)), // Chikorita: Grass/Grass/Mystery
            enemyBattlerState = hnsBattler(0, 1, listOf(1, 3, 10)) // Hoothoot: Normal/Flying/Mystery
        )

        val supported = buildHnsPresentation(33, realRuntimeTuples, calculator)
        assertEquals(DamageConfidence.ESTIMATE, supported.damageConfidence)
        assertEquals("Normal", supported.typeName)
        assertEquals("Neutral (1x)", supported.effectiveness)
        assertEquals(1, sentRequests.size)

        // A real third type remains unrepresentable. Neither the calculator nor effectiveness
        // presentation may silently truncate it to the first two slots.
        val thirdType = buildHnsPresentation(
            33,
            hnsContext().copy(
                playerBattlerState = hnsBattler(0, 0, listOf(13, 13, 10)),
                enemyBattlerState = hnsBattler(0, 1, listOf(1, 3, 12)) // Normal/Flying/Water
            ),
            calculator
        )
        assertEquals(DamageConfidence.UNAVAILABLE, thirdType.damageConfidence)
        assertTrue(thirdType.damageLimitations.contains(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED))
        assertEquals(MoveEffectiveness.UNAVAILABLE, thirdType.effectiveness)
        assertEquals("a refused third type must never reach the calculator", 1, sentRequests.size)
    }

    @Test
    fun hnsBattlePresentationKeepsRepresentativeBoundaryRefusalsClosed() {
        val calculatorCalls = mutableListOf<DamageCalculationRequest>()
        val calculator = BattleDamageCalculator { request ->
            calculatorCalls += request
            DamageCalculationResponse(
                success = true,
                minDamage = 5,
                maxDamage = 7,
                range = listOf(5, 7),
                engineEcho = CalcEngineOperandEcho("(other)", "(other)", null, null, true)
            )
        }

        val doubles = buildHnsPresentation(33, hnsContext(battlersCount = 4), calculator)
        assertEquals(DamageConfidence.UNAVAILABLE, doubles.damageConfidence)
        assertTrue(doubles.damageLimitations.contains(CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED))
        assertEquals("Doubles not supported", doubles.damageUnavailableReason)

        // Silk Scarf (425) boosts Normal moves and Tackle is Normal: the item is relevant.
        val unsupportedItem = buildHnsPresentation(33, hnsContext(playerItemId = 425), calculator)
        assertEquals(DamageConfidence.ESTIMATE, unsupportedItem.damageConfidence)
        assertTrue(unsupportedItem.damageLimitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals(listOf("You: Silk Scarf"), unsupportedItem.damageIgnoredMechanics.map { it.detail })
        assertNull(calculatorCalls.single().attacker.item)
        assertNull(calculatorCalls.single().attacker.itemId)

        val missingPlayerState = hnsBattler(0, 0, listOf(13)).copy(
            state = hnsBattler(0, 0, listOf(13)).state.copy(status = HnsBattlerRuntimeStatus.UNAVAILABLE)
        )
        val incomplete = buildHnsPresentation(
            33,
            hnsContext(playerObservation = missingPlayerState),
            calculator
        )
        assertEquals(DamageConfidence.UNAVAILABLE, incomplete.damageConfidence)
        assertTrue(incomplete.damageLimitations.isNotEmpty())
        assertEquals("Live battle state incomplete", incomplete.damageUnavailableReason)
        assertEquals(listOf("Live battle state incomplete"), incomplete.damageBlockers.map { it.detail })
        assertEquals("only the sanitized estimate may execute", 1, calculatorCalls.size)
    }

    @Test
    fun hnsBattleCardCategoryMatchesTheAuthorizedRequestInBothOptionStyles() {
        val requests = mutableListOf<DamageCalculationRequest>()
        val calculator = BattleDamageCalculator { request ->
            requests += request
            val category = request.moveOverride?.category ?: "Physical" // Swift is Normal under TYPE_BASED.
            DamageCalculationResponse(
                success = true,
                minDamage = 7,
                maxDamage = 11,
                range = listOf(7, 11),
                moveCategory = category
            )
        }

        val perMove = buildHnsPresentation(129, hnsContext(optionStyle = 0), calculator) // Swift: per-move Special.
        assertEquals(MoveCategory.SPECIAL, perMove.category)
        assertEquals("Special", requests[0].moveOverride?.category)
        assertEquals(CalcSupport.ESTIMATED, perMove.calculatorSupport)

        val typeBased = buildHnsPresentation(129, hnsContext(optionStyle = 1), calculator)
        assertEquals(MoveCategory.PHYSICAL, typeBased.category)
        assertNull(requests[1].moveOverride?.category)
        assertEquals("Normal", requests[1].moveOverride?.type)
        assertEquals(CalcSupport.ESTIMATED, typeBased.calculatorSupport)

        val electrifiedObservation = hnsBattler(0, 0, listOf(13)).copy(
            state = hnsBattler(0, 0, listOf(13)).state.copy(volatileElectrified = true)
        )
        val electrified = buildHnsPresentation(
            129,
            hnsContext(optionStyle = 1, playerObservation = electrifiedObservation),
            calculator
        )
        assertEquals("Electrify changes the type-based category to Electric/Special", MoveCategory.SPECIAL, electrified.category)
        assertEquals(DamageConfidence.UNAVAILABLE, electrified.damageConfidence)

        val unreadVolatileObservation = hnsBattler(0, 0, listOf(13)).copy(
            state = hnsBattler(0, 0, listOf(13)).state.copy(volatilesObserved = false)
        )
        val categoryUnknown = buildHnsPresentation(
            129,
            hnsContext(optionStyle = 1, playerObservation = unreadVolatileObservation),
            calculator
        )
        assertNull("an unreadable dynamic type must not fall back to the static category", categoryUnknown.category)

        val statusMove = buildHnsPresentation(14, hnsContext(optionStyle = 1), calculator)
        assertEquals(MoveCategory.STATUS, statusMove.category)
        assertEquals(DamageConfidence.UNAVAILABLE, statusMove.damageConfidence)
    }

    @Test
    fun fairyOffHnsBattleCardUsesTheEffectiveMoveAndDefenderTypes() {
        val pack = GameDataPackRegistry.getForProfile(
            hnsProfile.engine,
            hnsProfile.hasPhysSpecSplit,
            hnsProfile.gameDataPackId
        )
        val dazzlingGleam = requireNotNull(pack.getMoveByName("Dazzling Gleam"))
        val gengar = requireNotNull(pack.getSpeciesByName("Gengar"))
        val dazzlingRequests = mutableListOf<DamageCalculationRequest>()
        val fairyOffMoveContext = hnsContext(optionStyle = 1, fairyTypesEnabled = false).copy(
            playerParty = listOf(createTestPokemon(
                species = 152,
                moves = intArrayOf(dazzlingGleam.id, 0, 0, 0),
                pp = intArrayOf(10, 0, 0, 0)
            )),
            enemyBattlerState = hnsBattler(partySlot = 0, battlerIndex = 1, typeIds = listOf(8, 4))
        )
        val dazzlingPresentation = buildHnsPresentation(
            moveId = dazzlingGleam.id,
            context = fairyOffMoveContext,
            calculator = BattleDamageCalculator { request ->
                dazzlingRequests += request
                DamageCalculationResponse(success = false, error = "fixture response")
            },
            defender = createTestPokemon(species = gengar.id, nickname = "Gengar")
        )

        assertEquals("Normal", dazzlingPresentation.typeName)
        assertEquals("No Effect (0x)", dazzlingPresentation.effectiveness)
        assertEquals(MoveCategory.PHYSICAL, dazzlingPresentation.category)
        assertEquals(1, dazzlingRequests.size)
        assertEquals("Normal", dazzlingRequests.single().moveOverride?.type)
        assertNull(dazzlingRequests.single().moveOverride?.category)
        assertEquals(listOf("Ghost", "Poison"), dazzlingRequests.single().defenderOverride?.types)

        val darkPulse = requireNotNull(pack.getMoveByName("Dark Pulse"))
        val clefable = requireNotNull(pack.getSpeciesByName("Clefable"))
        val defenderRequests = mutableListOf<DamageCalculationRequest>()
        val fairyOffDefenderContext = hnsContext(optionStyle = 0, fairyTypesEnabled = false).copy(
            playerParty = listOf(createTestPokemon(
                species = 152,
                moves = intArrayOf(darkPulse.id, 0, 0, 0),
                pp = intArrayOf(15, 0, 0, 0)
            )),
            enemyBattlerState = hnsBattler(partySlot = 0, battlerIndex = 1, typeIds = listOf(1))
        )
        val defenderPresentation = buildHnsPresentation(
            moveId = darkPulse.id,
            context = fairyOffDefenderContext,
            calculator = BattleDamageCalculator { request ->
                defenderRequests += request
                DamageCalculationResponse(success = false, error = "fixture response")
            },
            defender = createTestPokemon(species = clefable.id, nickname = "Clefable")
        )

        assertEquals("Dark", defenderPresentation.typeName)
        assertEquals("Neutral (1x)", defenderPresentation.effectiveness)
        assertEquals(listOf("Normal"), defenderRequests.single().defenderOverride?.types)
    }

    // 6. Battle false while party remains populated
    @Test
    fun testBattleFalseWhilePartyPopulated() {
        val playerMon = createTestPokemon(species = 6)
        val fieldData = FieldStatusBuilder.build(
            inBattle = false,
            attacker = playerMon,
            defender = null,
            attackerSlot = 0,
            profile = RomHackProfile.DEFAULT_FIRERED
        )

        assertFalse("inBattle must be false despite populated player party", fieldData.inBattle)
        assertFalse("Opponent should be missing when not in battle", fieldData.opponent.isVerified)

        val battleActiveData = FieldStatusBuilder.build(
            inBattle = true,
            attacker = playerMon,
            defender = createTestPokemon(species = 9),
            attackerSlot = 0,
            profile = RomHackProfile.DEFAULT_FIRERED
        )
        assertTrue("inBattle must be true when authoritative flag is true", battleActiveData.inBattle)
    }

    // 7. Active enemy switching
    @Test
    fun testActiveEnemySwitching() {
        val viewModel = CompanionViewModel()
        val enemy0 = createTestPokemon(species = 16, nickname = "Pidgey")
        val enemy1 = createTestPokemon(species = 19, nickname = "Rattata")
        val enemies = listOf(enemy0, enemy1)

        viewModel.updateEnemyParty(enemies)
        viewModel.setIsInBattle(true)

        // No synthetic active opponent is allowed until native observation supplies a slot.
        assertEquals(-1, viewModel.activeEnemyMemberIndex.value)

        // Switch to slot 1
        viewModel.setActiveEnemyMemberIndex(1)
        assertEquals(1, viewModel.activeEnemyMemberIndex.value)
        val active1 = enemies.getOrNull(viewModel.activeEnemyMemberIndex.value)
        assertEquals("Rattata", active1?.nickname)
    }

    // 8. Enemy disappearance / battle end
    @Test
    fun testEnemyDisappearanceAndBattleEnd() {
        val viewModel = CompanionViewModel()
        val enemy0 = createTestPokemon(species = 16, nickname = "Pidgey")
        viewModel.updateEnemyParty(listOf(enemy0))
        viewModel.setIsInBattle(true)

        assertTrue(viewModel.isInBattle.value)
        assertEquals(-1, viewModel.activeEnemyMemberIndex.value)

        // Battle ends: enemies disappear
        viewModel.updateEnemyParty(emptyList())

        assertFalse("isInBattle must be false when enemies disappear", viewModel.isInBattle.value)
        assertEquals(-1, viewModel.activeEnemyMemberIndex.value)

        // Opponent summary when battle has ended
        val summary = ParticipantSummaryBuilder.build(null, -1, RomHackProfile.DEFAULT_FIRERED)
        assertTrue(summary.isMissing)
        assertFalse(summary.isVerified)
        assertEquals("?", summary.displayName)
    }

    // 9. Toxic status decoding
    @Test
    fun testToxicStatusDecoding() {
        // Bit 7: Bad Poison (Toxic)
        val toxicVal = 1L shl 7
        assertEquals(StatusCondition.BAD_POISON, StatusConditionDecoder.decode(toxicVal))

        // Both bit 7 and bit 3 set: Toxic should take precedence
        val toxicWithPoisonBit = (1L shl 7) or (1L shl 3)
        assertEquals(StatusCondition.BAD_POISON, StatusConditionDecoder.decode(toxicWithPoisonBit))

        // Bit 3 only: regular Poison
        val poisonVal = 1L shl 3
        assertEquals(StatusCondition.POISON, StatusConditionDecoder.decode(poisonVal))

        // Other bits
        assertEquals(StatusCondition.SLEEP, StatusConditionDecoder.decode(3L))
        assertEquals(StatusCondition.BURN, StatusConditionDecoder.decode(1L shl 4))
        assertEquals(StatusCondition.FREEZE, StatusConditionDecoder.decode(1L shl 5))
        assertEquals(StatusCondition.PARALYSIS, StatusConditionDecoder.decode(1L shl 6))
        assertEquals(StatusCondition.HEALTHY, StatusConditionDecoder.decode(0L))
    }

    // 10. Unsupported / unverified profile behavior
    @Test
    fun testUnsupportedUnverifiedProfileBehavior() {
        val attacker = createTestPokemon(species = 6)
        val defender = createTestPokemon(species = 9)

        // Unverified profile summary
        val summary = ParticipantSummaryBuilder.build(attacker, 0, unverifiedProfile)
        assertFalse("Unverified profile participant must not be marked verified", summary.isVerified)
        assertEquals(DataConfidence.UNAVAILABLE, summary.confidence)

        // Unverified profile damage presentation
        val pres = BattlePresentationBuilder.build(
            moveInfo = MoveDatabase.get(7),
            currentPp = 15,
            attacker = attacker,
            defender = defender,
            profile = unverifiedProfile,
            calculator = stubCalculator
        )
        assertFalse(pres.damageVerified)
        assertEquals(DamageConfidence.UNAVAILABLE, pres.damageConfidence)
        assertEquals("Damage unavailable for this ROM/profile", pres.damageDisplayText)
    }

    private class RecordingInputDispatcher : BattleInputDispatcher {
        val recordedButtons = mutableListOf<Int>()
        override suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean {
            recordedButtons.add(buttonMask)
            return true
        }
    }

    // 11. Stat stages: clamping, multipliers, accuracy
    @Test
    fun testStatStages_multipliersAndClamping() {
        val defaultStages = StatStages()
        assertTrue(defaultStages.isNeutral)
        assertEquals(0, defaultStages.atk)

        // Test clamping via fromRawArray
        val clamped = StatStages.fromRawArray(intArrayOf(-10, 10, 0, -4, 3, 7, -8))
        assertEquals(-6, clamped.atk)
        assertEquals(6, clamped.def)
        assertEquals(0, clamped.spe)
        assertEquals(-4, clamped.spa)
        assertEquals(3, clamped.spd)
        assertEquals(6, clamped.acc)
        assertEquals(-6, clamped.eva)

        // Stat multipliers
        assertEquals(1.0, StatStages.statMultiplier(0), 0.001)
        assertEquals(1.5, StatStages.statMultiplier(1), 0.001)
        assertEquals(2.0, StatStages.statMultiplier(2), 0.001)
        assertEquals(4.0, StatStages.statMultiplier(6), 0.001)
        assertEquals(2.0 / 3.0, StatStages.statMultiplier(-1), 0.001)
        assertEquals(0.5, StatStages.statMultiplier(-2), 0.001)
        assertEquals(0.25, StatStages.statMultiplier(-6), 0.001)

        // Accuracy multipliers
        assertEquals(1.0, StatStages.accuracyMultiplier(0), 0.001)
        assertEquals(4.0 / 3.0, StatStages.accuracyMultiplier(1), 0.001)
        assertEquals(0.75, StatStages.accuracyMultiplier(-1), 0.001)
    }

    // 12. Speed comparison: stages, paralysis, tailwind, trick room, priority
    @Test
    fun testSpeedComparison_calculations() {
        // Player faster
        val normal = SpeedComparison.calculate(
            playerBaseSpeed = 100,
            enemyBaseSpeed = 80
        )
        assertEquals(100, normal.playerEffectiveSpeed)
        assertEquals(80, normal.enemyEffectiveSpeed)
        assertEquals(true, normal.playerMovesFirst)
        assertFalse(normal.isSpeedTie)

        // Enemy faster
        val enemyFaster = SpeedComparison.calculate(
            playerBaseSpeed = 80,
            enemyBaseSpeed = 100
        )
        assertEquals(false, enemyFaster.playerMovesFirst)

        // Speed tie
        val tie = SpeedComparison.calculate(
            playerBaseSpeed = 100,
            enemyBaseSpeed = 100
        )
        assertTrue(tie.isSpeedTie)
        assertNull(tie.playerMovesFirst)

        // Gen 3 Paralysis reduces speed by 75% (0.25x)
        val paralyzed = SpeedComparison.calculate(
            playerBaseSpeed = 120,
            playerParalyzed = true,
            enemyBaseSpeed = 50
        )
        assertEquals(30, paralyzed.playerEffectiveSpeed)
        assertEquals(50, paralyzed.enemyEffectiveSpeed)
        assertEquals(false, paralyzed.playerMovesFirst)

        // Tailwind doubles speed (2x)
        val tailwind = SpeedComparison.calculate(
            playerBaseSpeed = 60,
            playerTailwind = true,
            enemyBaseSpeed = 100
        )
        assertEquals(120, tailwind.playerEffectiveSpeed)
        assertEquals(100, tailwind.enemyEffectiveSpeed)
        assertEquals(true, tailwind.playerMovesFirst)

        // Trick Room inverts speed order (lower speed moves first)
        val trickRoom = SpeedComparison.calculate(
            playerBaseSpeed = 100,
            enemyBaseSpeed = 60,
            trickRoom = true
        )
        assertEquals(false, trickRoom.playerMovesFirst)
        assertTrue(trickRoom.explanation.contains("Trick Room"))

        // Move Priority overrides speed order
        val priority = SpeedComparison.calculate(
            playerBaseSpeed = 50,
            enemyBaseSpeed = 150,
            playerMovePriority = 1,
            enemyMovePriority = 0
        )
        assertEquals(true, priority.playerMovesFirst)
        assertTrue(priority.explanation.contains("priority"))
    }

    // 13. buildDamageRequest incorporates boosts, weather, status, and side conditions
    @Test
    fun testBuildDamageRequest_boostsWeatherAndStatus() {
        val attacker = createTestPokemon(species = 6, statusCondition = 1L shl 4) // Burned
        val defender = createTestPokemon(species = 9, statusCondition = 1L shl 6) // Paralyzed

        val playerStages = StatStages(atk = 2, spa = 1)
        val enemyStages = StatStages(def = -1)
        val weather = WeatherType.RAIN
        val defenderSide = SideEffects(reflect = true)

        val req = buildDamageRequest(
            attacker = attacker,
            defender = defender,
            moveName = "Surf",
            natureName = "Hardy",
            attackerStages = playerStages,
            defenderStages = enemyStages,
            weather = weather,
            defenderSide = defenderSide
        )

        assertEquals("brn", req.attacker.status)
        assertEquals(2, req.attacker.boosts?.atk)
        assertEquals(1, req.attacker.boosts?.spa)

        assertEquals("par", req.defender.status)
        assertEquals(-1, req.defender.boosts?.def)

        assertEquals("Rain", req.field.weather)
        assertEquals(true, req.field.defenderSide?.isReflect)
        assertEquals(false, req.field.defenderSide?.isLightScreen)
    }

    // 14. BattleUiSnapshot state and confidence
    @Test
    fun testBattleUiSnapshot_stateAndConfidence() {
        val cmdSnapshot = BattleUiSnapshot(state = BattleUiState.COMMAND_MENU, isInputAccepted = true)
        assertTrue(cmdSnapshot.isInputAccepted)
        assertEquals(BattleUiState.COMMAND_MENU, cmdSnapshot.state)

        val moveSnapshot = BattleUiSnapshot(state = BattleUiState.MOVE_MENU, isInputAccepted = true)
        assertTrue(moveSnapshot.isInputAccepted)

        val partySnapshot = BattleUiSnapshot(state = BattleUiState.PARTY_MENU, isInputAccepted = true)
        assertTrue(partySnapshot.isInputAccepted)

        val animSnapshot = BattleUiSnapshot(state = BattleUiState.ANIMATION_OR_TEXT, isInputAccepted = false)
        assertFalse(animSnapshot.isInputAccepted)
    }

    // 15. BattleInputAdapter.selectMove from COMMAND_MENU emits exact button macros
    @Test
    fun testBattleInputAdapter_selectMove_fromCommandMenu() = runBlocking {
        val dispatcher = RecordingInputDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        val ui = BattleUiSnapshot(
            state = BattleUiState.COMMAND_MENU,
            selectedActionIndex = 0,
            selectedMoveIndex = 0,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        // Slot 0 (top-left): A (Fight) -> A (Move 0)
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.selectMove(0, ui))
        assertEquals(listOf(InputManager.BTN_A, InputManager.BTN_A), dispatcher.recordedButtons)

        // Slot 1 (top-right): A (Fight) -> RIGHT -> A (Move 1)
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.selectMove(1, ui))
        assertEquals(listOf(InputManager.BTN_A, InputManager.BTN_RIGHT, InputManager.BTN_A), dispatcher.recordedButtons)

        // Slot 2 (bottom-left): A (Fight) -> DOWN -> A (Move 2)
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.selectMove(2, ui))
        assertEquals(listOf(InputManager.BTN_A, InputManager.BTN_DOWN, InputManager.BTN_A), dispatcher.recordedButtons)

        // Slot 3 (bottom-right): A (Fight) -> RIGHT -> DOWN -> A (Move 3)
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.selectMove(3, ui))
        assertEquals(listOf(InputManager.BTN_A, InputManager.BTN_RIGHT, InputManager.BTN_DOWN, InputManager.BTN_A), dispatcher.recordedButtons)
    }

    // 16. BattleInputAdapter.selectMove from MOVE_MENU with cursor navigation
    @Test
    fun testBattleInputAdapter_selectMove_fromMoveMenu() = runBlocking {
        val dispatcher = RecordingInputDispatcher()
        val adapter = BattleInputAdapter(dispatcher)

        // Cursor at 0, target 3: RIGHT -> DOWN -> A
        val uiAt0 = BattleUiSnapshot(
            state = BattleUiState.MOVE_MENU,
            selectedMoveIndex = 0,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.selectMove(3, uiAt0))
        assertEquals(listOf(InputManager.BTN_RIGHT, InputManager.BTN_DOWN, InputManager.BTN_A), dispatcher.recordedButtons)

        // Cursor at 3, target 0: LEFT -> UP -> A
        val uiAt3 = BattleUiSnapshot(
            state = BattleUiState.MOVE_MENU,
            selectedMoveIndex = 3,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.selectMove(0, uiAt3))
        assertEquals(listOf(InputManager.BTN_LEFT, InputManager.BTN_UP, InputManager.BTN_A), dispatcher.recordedButtons)

        // Cursor at 1, target 2: LEFT -> DOWN -> A
        val uiAt1 = BattleUiSnapshot(
            state = BattleUiState.MOVE_MENU,
            selectedMoveIndex = 1,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.selectMove(2, uiAt1))
        assertEquals(listOf(InputManager.BTN_LEFT, InputManager.BTN_DOWN, InputManager.BTN_A), dispatcher.recordedButtons)

        // Cursor already at target: direct A
        val uiAt2 = BattleUiSnapshot(
            state = BattleUiState.MOVE_MENU,
            selectedMoveIndex = 2,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.selectMove(2, uiAt2))
        assertEquals(listOf(InputManager.BTN_A), dispatcher.recordedButtons)
    }

    // 17. BattleInputAdapter.selectMove fails closed when input is not accepted
    @Test
    fun testBattleInputAdapter_selectMove_safetyAbort() = runBlocking {
        val dispatcher = RecordingInputDispatcher()
        val adapter = BattleInputAdapter(dispatcher)

        val busyUi = BattleUiSnapshot(state = BattleUiState.ANIMATION_OR_TEXT, isInputAccepted = false)
        assertFalse(adapter.selectMove(0, busyUi))
        assertTrue(dispatcher.recordedButtons.isEmpty())

        val unknownUi = BattleUiSnapshot(state = BattleUiState.UNKNOWN, isInputAccepted = false)
        assertFalse(adapter.selectMove(1, unknownUi))
        assertTrue(dispatcher.recordedButtons.isEmpty())

        // Invalid slot bounds
        val readyUi = BattleUiSnapshot(
            state = BattleUiState.COMMAND_MENU,
            selectedActionIndex = 0,
            selectedMoveIndex = 0,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )
        assertFalse(adapter.selectMove(4, readyUi))
        assertFalse(adapter.selectMove(-1, readyUi))
        assertTrue(dispatcher.recordedButtons.isEmpty())
    }

    // 18. BattleInputAdapter.switchPokemon validates legality and emits correct macros
    @Test
    fun testBattleInputAdapter_switchPokemon_legalityAndMacro() = runBlocking {
        val dispatcher = RecordingInputDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        val ui = BattleUiSnapshot(
            state = BattleUiState.COMMAND_MENU,
            selectedActionIndex = 0,
            selectedPartySlot = 0,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        val mon0 = createTestPokemon(nickname = "LeadMon", currentHp = 100)
        val mon1 = createTestPokemon(nickname = "Candidate1", currentHp = 100)
        val mon2 = createTestPokemon(nickname = "FaintedMon", currentHp = 0)
        val mon3 = createTestPokemon(nickname = "Candidate3", currentHp = 50)
        val party = listOf(mon0, mon1, mon2, mon3)

        // Cannot switch to active Pokémon (slot 0)
        assertFalse("Cannot switch to active Pokémon", adapter.switchPokemon(0, ui, party, activeSlot = 0))
        assertTrue(dispatcher.recordedButtons.isEmpty())

        // Cannot switch to fainted Pokémon (slot 2)
        assertFalse("Cannot switch to fainted Pokémon", adapter.switchPokemon(2, ui, party, activeSlot = 0))
        assertTrue(dispatcher.recordedButtons.isEmpty())

        // Cannot switch during animation/text
        val busyUi = BattleUiSnapshot(state = BattleUiState.ANIMATION_OR_TEXT, isInputAccepted = false)
        assertFalse("Cannot switch when UI is busy", adapter.switchPokemon(1, busyUi, party, activeSlot = 0))
        assertTrue(dispatcher.recordedButtons.isEmpty())

        // Valid switch to slot 1 from COMMAND_MENU:
        // Down (to Pokemon) -> A (open party) -> Down (to slot 1) -> A (action menu) -> A (SHIFT)
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.switchPokemon(1, ui, party, activeSlot = 0))
        assertEquals(
            listOf(InputManager.BTN_DOWN, InputManager.BTN_A, InputManager.BTN_DOWN, InputManager.BTN_A, InputManager.BTN_A),
            dispatcher.recordedButtons
        )

        // Valid switch to slot 3 from COMMAND_MENU:
        // Down (to Pokemon) -> A (open party) -> Down x3 -> A -> A
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.switchPokemon(3, ui, party, activeSlot = 0))
        assertEquals(
            listOf(
                InputManager.BTN_DOWN, InputManager.BTN_A,
                InputManager.BTN_DOWN, InputManager.BTN_DOWN, InputManager.BTN_DOWN,
                InputManager.BTN_A, InputManager.BTN_A
            ),
            dispatcher.recordedButtons
        )

        // Valid switch from forced faint PARTY_MENU state:
        // Starts in party screen at slot 0: Down -> A -> A
        val partyUi = BattleUiSnapshot(
            state = BattleUiState.PARTY_MENU,
            selectedPartySlot = 0,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )
        dispatcher.recordedButtons.clear()
        assertTrue(adapter.switchPokemon(1, partyUi, party, activeSlot = 0))
        assertEquals(
            listOf(InputManager.BTN_DOWN, InputManager.BTN_A, InputManager.BTN_A),
            dispatcher.recordedButtons
        )
    }

    // 19. BattleInputAdapter.cancel emits BTN_B
    @Test
    fun testBattleInputAdapter_cancel() = runBlocking {
        val dispatcher = RecordingInputDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        assertTrue(adapter.cancel())
        assertEquals(listOf(InputManager.BTN_B), dispatcher.recordedButtons)
    }

    // 20. ParticipantSummaryBuilder computes effectiveSpeed factoring stages & paralysis
    @Test
    fun testParticipantSummary_effectiveSpeedAndStages() {
        val mon = createTestPokemon(species = 6) // Speed = 100
        val summaryNeutral = ParticipantSummaryBuilder.build(mon, 0, RomHackProfile.DEFAULT_FIRERED)
        assertEquals(100, summaryNeutral.effectiveSpeed)
        assertTrue(summaryNeutral.statStages.isNeutral)

        // With +2 speed stage: 100 * 2.0 = 200
        val summaryBoosted = ParticipantSummaryBuilder.build(
            mon, 0, RomHackProfile.DEFAULT_FIRERED,
            statStages = StatStages(spe = 2, spa = 1)
        )
        assertEquals(200, summaryBoosted.effectiveSpeed)
        assertEquals(2, summaryBoosted.statStages.spe)
        assertEquals(1, summaryBoosted.statStages.spa)

        // Paralyzed with -1 speed stage: 100 * (2/3) * 0.25 = 16
        val monParalyzed = createTestPokemon(species = 6, statusCondition = 1L shl 6)
        val summaryParalyzed = ParticipantSummaryBuilder.build(
            monParalyzed, 0, RomHackProfile.DEFAULT_FIRERED,
            statStages = StatStages(spe = -1)
        )
        assertEquals(16, summaryParalyzed.effectiveSpeed)
    }

    // 21. Battle auto-open defaults on while verified touch controls default safely off.
    @Test
    fun testCompanionViewModel_battleAutoOpenAndInteractiveDefaults() {
        val vm = CompanionViewModel()
        assertTrue("Battle Console should auto-open by default", vm.isBattleAutoOpenEnabled.value)
        assertFalse("Verified touch-control permission defaults to disabled", vm.isInteractiveBattleControlsEnabled.value)
    }

    @Test
    fun testCompanionViewModel_battleAutoOpenCanBeDisabledWithoutRemovingBattle() {
        val vm = CompanionViewModel()
        vm.setBattleAutoOpenEnabled(false)
        assertFalse(vm.isBattleAutoOpenEnabled.value)
        vm.setBattleAutoOpenEnabled(true)
        assertTrue(vm.isBattleAutoOpenEnabled.value)
    }

    // 22. CompanionViewModel allows toggling interactive battle controls
    @Test
    fun testCompanionViewModel_interactiveBattleControlsToggle() {
        val vm = CompanionViewModel()
        vm.setInteractiveBattleControlsEnabled(false)
        assertFalse("Interactive controls should be disabled when set to false", vm.isInteractiveBattleControlsEnabled.value)

        vm.setInteractiveBattleControlsEnabled(true)
        assertTrue("Interactive controls should be enabled when set to true", vm.isInteractiveBattleControlsEnabled.value)
    }

    // ------------------------------------------------------------------
    // H&S held items: request-local relevance and structured item blockers
    // ------------------------------------------------------------------

    private fun recordingCalculator(sent: MutableList<DamageCalculationRequest>) = BattleDamageCalculator { request ->
        sent += request
        DamageCalculationResponse(success = true, minDamage = 8, maxDamage = 12,
            range = listOf(8, 10, 12), moveCategory = request.moveOverride?.category ?: "Physical",
            engineEcho = CalcEngineOperandEcho("(other)", "(other)", null, null, true))
    }

    @Test
    fun `known relevant live ability reaches Battle estimate after neutralization`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val context = hnsContext(
            playerAbilityId = 37,
            playerAbilityName = "Huge Power",
            randomAbilities = true
        )
        val result = buildHnsPresentation(33, context, recordingCalculator(sent))
        assertEquals(DamageConfidence.ESTIMATE, result.damageConfidence)
        assertEquals(listOf("You: Huge Power"), result.damageIgnoredMechanics.map { it.detail })
        assertTrue(result.damageDisplayText.contains("(Estimate)"))
        assertTrue(result.damageDisplayText.contains("Ignores:\nYou: Huge Power"))
        assertEquals(1, sent.size)
        assertEquals("(other)", sent.single().attacker.ability)
        assertNull(sent.single().attacker.abilityId)
    }

    @Test
    fun `live items proven irrelevant to this request reach an estimate and are stripped before the engine`() {
        // Real-device shape: exact H&S Singles, otherwise supported, where live items used to
        // refuse with HNS_ITEM_EFFECT_NOT_MODELLED. Your Charcoal cannot boost Normal Tackle and the
        // foe's Choice Band is read only when the foe attacks.
        val sent = mutableListOf<DamageCalculationRequest>()
        val context = hnsContext(
            playerItemId = 426,
            enemyObservation = hnsBattler(0, 1, listOf(1, 3), itemId = 442)
        )
        val result = buildHnsPresentation(33, context, recordingCalculator(sent))
        assertEquals(DamageConfidence.ESTIMATE, result.damageConfidence)
        assertEquals(CalcSupport.ESTIMATED, result.calculatorSupport)
        assertTrue(result.damageLimitations.none { it == CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED })
        assertTrue(result.damageItemBlockers.isEmpty())
        assertTrue(result.damageBlockers.isEmpty())
        assertEquals(1, sent.size)
        // The engine never receives an H&S item name: a proven-irrelevant item is forwarded as none.
        assertNull(sent.single().attacker.item)
        assertNull(sent.single().defender.item)
        assertEquals(426, sent.single().attacker.itemId)
        assertEquals(442, sent.single().defender.itemId)
    }

    @Test
    fun `Choice Band on a special move is irrelevant and on a physical move is a caveat`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val special = buildHnsPresentation(52, hnsContext(playerItemId = 442), recordingCalculator(sent)) // Ember
        assertEquals(MoveCategory.SPECIAL, special.category)
        assertEquals(DamageConfidence.ESTIMATE, special.damageConfidence)
        assertEquals(1, sent.size)

        val physical = buildHnsPresentation(33, hnsContext(playerItemId = 442), recordingCalculator(sent)) // Tackle
        assertEquals(DamageConfidence.ESTIMATE, physical.damageConfidence)
        assertEquals(listOf("You: Choice Band"), physical.damageIgnoredMechanics.map { it.detail })
        assertTrue(physical.damageDisplayText.contains("Ignores:\nYou: Choice Band"))
        assertEquals(2, sent.size)
        assertNull(sent.last().attacker.item)
        assertNull(sent.last().attacker.itemId)
    }

    @Test
    fun `relevant and unaudited live items name the side and exact pinned identity`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val calculator = recordingCalculator(sent)

        val silkScarf = buildHnsPresentation(33, hnsContext(playerItemId = 425), calculator)
        assertEquals(DamageConfidence.ESTIMATE, silkScarf.damageConfidence)
        val itemBlocker = silkScarf.damageIgnoredMechanics.single() as DamageBlockerPresentation.Item
        assertEquals(425, itemBlocker.decision.itemId)
        assertEquals(com.dualdex.calculator.HnsItemSide.ATTACKER, itemBlocker.decision.side)
        assertEquals(com.dualdex.calculator.HnsItemRequestRelevance.RELEVANT, itemBlocker.decision.relevance)
        assertEquals("type_item_move_type_match", itemBlocker.decision.rule)
        assertTrue(itemBlocker.ignored)
        assertNull(sent.last().attacker.item)
        assertNull(sent.last().attacker.itemId)

        val foeSash = buildHnsPresentation(33,
            hnsContext(enemyObservation = hnsBattler(0, 1, listOf(1, 3), itemId = 481, hp = 20, maxHp = 20)),
            calculator)
        assertEquals(DamageConfidence.ESTIMATE, foeSash.damageConfidence)
        assertEquals(com.dualdex.calculator.HnsItemSide.DEFENDER,
            (foeSash.damageIgnoredMechanics.single() as DamageBlockerPresentation.Item).decision.side)
        assertNull(sent.last().defender.item)
        assertNull(sent.last().defender.itemId)

        val unaudited = buildHnsPresentation(33, hnsContext(playerItemId = 290), calculator) // Red Orb
        assertEquals("Your Red Orb not yet audited", unaudited.damageUnavailableReason)
        assertEquals(2, sent.size)

        // The same Focus Sash on a damaged foe cannot activate (it requires hp == maxHP).
        val damagedFoe = buildHnsPresentation(33,
            hnsContext(enemyObservation = hnsBattler(0, 1, listOf(1, 3), itemId = 481, hp = 12, maxHp = 20)),
            calculator)
        assertEquals(DamageConfidence.ESTIMATE, damagedFoe.damageConfidence)
        assertEquals(3, sent.size)
    }

    @Test
    fun `several ignored items are listed as caveats by side`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val result = buildHnsPresentation(33,
            hnsContext(playerItemId = 425, enemyObservation = hnsBattler(0, 1, listOf(1, 3), itemId = 481)),
            recordingCalculator(sent))
        assertEquals(DamageConfidence.ESTIMATE, result.damageConfidence)
        assertEquals(listOf("You: Silk Scarf", "Foe: Focus Sash"), result.damageIgnoredMechanics.map { it.detail })
        assertEquals(
            "Ignores:\nYou: Silk Scarf\nFoe: Focus Sash",
            result.damageDisplayText.substringAfter("(Estimate)").trim()
        )
        assertEquals(1, sent.size)
        assertNull(sent.single().attacker.item)
        assertNull(sent.single().attacker.itemId)
        assertNull(sent.single().defender.item)
        assertNull(sent.single().defender.itemId)
    }

    @Test
    fun `ability item and mechanic blockers all remain visible together`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val context = hnsContext(
            playerAbilityId = 37,
            playerAbilityName = "Huge Power",
            playerItemId = 442,
            randomAbilities = true
        )
        val result = buildHnsPresentation(67, context, recordingCalculator(sent)) // Low Kick: weight-based
        assertEquals(DamageConfidence.UNAVAILABLE, result.damageConfidence)
        assertEquals("3 blockers", result.damageUnavailableReason)
        assertEquals(
            listOf("You: Huge Power", "You: Choice Band", "Move effect not modelled"),
            result.damageBlockers.map { it.detail }
        )
        assertTrue(result.damageBlockers[0] is DamageBlockerPresentation.Ability)
        assertTrue(result.damageBlockers[1] is DamageBlockerPresentation.Item)
        assertTrue(result.damageBlockers[2] is DamageBlockerPresentation.Mechanic)
        assertTrue(result.damageLimitations.containsAll(listOf(
            CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED,
            CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED,
            CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED
        )))
        assertEquals(0, sent.size)
    }

    @Test
    fun `an item-dependent move stays refused even when the live item is proven irrelevant`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        // Knock Off reads the foe's item presence; the foe's Choice Band is irrelevant as a hold
        // effect, but the move-item interaction is an independent, still-unmodelled gate.
        val result = buildHnsPresentation(282,
            hnsContext(enemyObservation = hnsBattler(0, 1, listOf(1, 3), itemId = 442)),
            recordingCalculator(sent))
        assertEquals(DamageConfidence.UNAVAILABLE, result.damageConfidence)
        assertTrue(result.damageLimitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED))
        assertFalse(result.damageLimitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals("Move effect not modelled", result.damageUnavailableReason)
        assertEquals(0, sent.size)
    }

    // ------------------------------------------------ live field state on the Battle tab

    private fun fieldContext(
        field: Int,
        playerItemId: Int = 0,
        weather: Int = 0,
        foeSide: Int = 0
    ) = hnsContext(
        playerObservation = hnsBattler(0, 0, listOf(13), itemId = playerItemId, fieldStatuses = field,
            battleWeather = weather),
        enemyObservation = hnsBattler(0, 1, listOf(1, 3), fieldStatuses = field, battleWeather = weather,
            sideStatuses = foeSide)
    )

    @Test
    fun `device regression - Wise Glasses on a Physical move is not a second blocker beside the field`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val tackle = buildHnsPresentation(33, fieldContext(field = 0x4, playerItemId = 476), recordingCalculator(sent))
        assertEquals(DamageConfidence.ESTIMATE, tackle.damageConfidence)
        assertTrue(tackle.damageDisplayText.contains("Ignores:\nField: Wonder Room"))
        assertTrue(tackle.damageBlockers.isEmpty())
        assertTrue(tackle.damageItemBlockers.isEmpty())

        // Special Water Gun with Wise Glasses keeps its modeled item and names Wonder Room.
        val waterGun = buildHnsPresentation(55, fieldContext(field = 0x4, playerItemId = 476), recordingCalculator(sent))
        assertEquals(DamageConfidence.ESTIMATE, waterGun.damageConfidence)
        assertTrue(waterGun.damageDisplayText.contains("Ignores:\nField: Wonder Room"))
        assertTrue(waterGun.damageItemBlockers.isEmpty())
        assertEquals(2, sent.size)

        // Choice Specs and Wonder Room are both named, independently neutralized caveats.
        val choiceSpecs = buildHnsPresentation(55, fieldContext(field = 0x4, playerItemId = 443), recordingCalculator(sent))
        assertEquals(DamageConfidence.ESTIMATE, choiceSpecs.damageConfidence)
        assertEquals(listOf("You: Choice Specs", "Field: Wonder Room"),
            choiceSpecs.damageIgnoredMechanics.map { it.detail })
        assertTrue(choiceSpecs.damageDisplayText.contains("Ignores:\nYou: Choice Specs\nField: Wonder Room"))
        assertEquals(listOf(443), choiceSpecs.damageIgnoredMechanics.filterIsInstance<DamageBlockerPresentation.Item>().map { it.decision.itemId })
        assertEquals(3, sent.size)
    }

    @Test
    fun `an irrelevant live terrain lets the ordinary move reach an estimate`() {
        val sent = mutableListOf<DamageCalculationRequest>()
        val result = buildHnsPresentation(33, fieldContext(field = 0x100, playerItemId = 476), recordingCalculator(sent))
        assertEquals(DamageConfidence.ESTIMATE, result.damageConfidence)
        assertEquals(1, sent.size)
        assertEquals(0x100, sent.single().hnsLiveBattleState?.fieldStatuses)

        // Special Water Gun with Wise Glasses also reaches an estimate
        val specialResult = buildHnsPresentation(55, fieldContext(field = 0x100, playerItemId = 476), recordingCalculator(sent))
        assertEquals(DamageConfidence.ESTIMATE, specialResult.damageConfidence)
        assertEquals(2, sent.size)
        assertEquals("Wise Glasses", sent.last().attacker.item)
    }

    @Test
    fun `field, weather and foe side status are distinct Battle-tab blockers`() {
        // Wonder Room (field), an unmodelled weather bit and an unmodelled foe side-status bit: three
        // separate classes, never one generic "Field condition not modelled".
        val result = buildHnsPresentation(33, fieldContext(field = 0x4, weather = 0x20, foeSide = 0x100))
        assertEquals("3 blockers", result.damageUnavailableReason)
        assertEquals(
            listOf("Field: Wonder Room (0x00000004)", "Weather: not modelled (0x0020)", "Foe side: not modelled (0x00000100)"),
            result.damageBlockers.map { it.detail }
        )
        assertTrue(result.damageBlockers[0] is DamageBlockerPresentation.Field)
        assertTrue(result.damageBlockers[1] is DamageBlockerPresentation.Weather)
        assertTrue(result.damageBlockers[2] is DamageBlockerPresentation.SideStatus)
        assertFalse(result.damageUnavailableText.contains("Field condition not modelled"))
    }

    @Test
    fun `the Battle status row shows the raw decoded field word`() {
        val context = fieldContext(field = 0x102)
        val state = HnsFieldDiagnostics.observedFieldState(hnsProfile, hnsTrust, context.playerBattlerState,
            context.enemyBattlerState)
        assertEquals("Trick Room + Electric Terrain (0x00000102)", HnsFieldDiagnostics.statusRow(state))
        val clear = fieldContext(field = 0)
        assertEquals("clear (0x00000000)", HnsFieldDiagnostics.statusRow(HnsFieldDiagnostics.observedFieldState(
            hnsProfile, hnsTrust, clear.playerBattlerState, clear.enemyBattlerState)))
        // A torn read (the two observations disagree) is shown as unread, never as clear.
        val torn = HnsFieldDiagnostics.observedFieldState(hnsProfile, hnsTrust,
            hnsBattler(0, 0, listOf(13), fieldStatuses = 0x100), hnsBattler(0, 1, listOf(1, 3), fieldStatuses = 0))
        assertEquals("Unread", HnsFieldDiagnostics.statusRow(torn))
    }
}
