package com.dualdex.battle

import com.dualdex.calculator.DamageCalculationRequest
import com.dualdex.calculator.DamageCalculationResponse
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.romhack.RomHackProfile
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

        // Standard verified matchup: Water vs Fire/Flying = 2x
        val (effLabel, confidence) = MoveEffectiveness.evaluate(waterGun.id, waterGun.category, defender, RomHackProfile.DEFAULT_FIRERED)
        assertEquals(EffectivenessLabel.SUBSTANTIAL, effLabel)
        assertEquals(DataConfidence.VERIFIED, confidence)

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

    // 5. Damage confidence
    @Test
    fun testDamageConfidence() {
        val attacker = createTestPokemon(species = 6) // Charizard
        val defender = createTestPokemon(species = 9) // Blastoise
        val firePunch = MoveDatabase.get(7)
        val swordsDance = MoveDatabase.get(14)

        // Verified vanilla profile: succeeds with VERIFIED
        val verifiedPres = BattlePresentationBuilder.build(
            moveInfo = firePunch,
            currentPp = 15,
            attacker = attacker,
            defender = defender,
            profile = RomHackProfile.DEFAULT_FIRERED,
            calculator = stubCalculator
        )
        assertEquals(DamageConfidence.VERIFIED, verifiedPres.damageConfidence)
        assertTrue(verifiedPres.hasDamage)
        assertEquals(40, verifiedPres.minDamage)
        assertEquals(48, verifiedPres.maxDamage)

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

        // Default initial active enemy
        assertEquals(0, viewModel.activeEnemyMemberIndex.value)
        val active0 = enemies.getOrNull(viewModel.activeEnemyMemberIndex.value)
        assertEquals("Pidgey", active0?.nickname)

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
        assertEquals(0, viewModel.activeEnemyMemberIndex.value)

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
}
