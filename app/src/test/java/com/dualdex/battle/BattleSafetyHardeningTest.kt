package com.dualdex.battle

import com.dualdex.emulator.ControllerInputRouter
import com.dualdex.emulator.InputManager
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.Gen3VanillaDataPack
import com.dualdex.pokemon.ModernDataPack
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.romhack.RomHackProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Phase 11 safety and hardening tests for Enhanced Battle Console (EBC-1).
 * Verifies all 20 required behaviors from the hardening specification.
 */
class BattleSafetyHardeningTest {

    private class RecordingDispatcher : BattleInputDispatcher {
        val recordedButtons = mutableListOf<Int>()
        override suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean {
            recordedButtons.add(buttonMask)
            return true
        }
    }

    private fun createTestPokemon(
        species: Int = 6,
        currentHp: Int = 100,
        maxHp: Int = 100,
        speed: Int = 100
    ): ParsedPokemon {
        return ParsedPokemon(
            isValid = true,
            isEmpty = false,
            pid = 12345L,
            tid = 100,
            sid = 200,
            nickname = "TestMon",
            otName = "Ash",
            species = species,
            heldItem = 0,
            level = 50,
            nature = 0,
            natureName = "Hardy",
            isShiny = false,
            abilitySlot = 0,
            isEgg = false,
            friendship = 255,
            experience = 1000L,
            hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
            hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
            moves = intArrayOf(53, 56, 0, 0), // Flamethrower, Hydro Pump
            pp = intArrayOf(15, 5, 0, 0),
            currentHp = currentHp,
            maxHp = maxHp,
            attack = 100,
            defense = 100,
            speed = speed,
            spAttack = 100,
            spDefense = 100,
            statusCondition = 0L
        )
    }

    @Before
    fun setUp() {
        ControllerInputRouter.reset()
    }

    // 1. Unknown UI state rejects interactive actions
    @Test
    fun test1_unknownUiStateRejectsInteractiveActions() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        val unknownSnapshot = BattleUiSnapshot(
            state = BattleUiState.UNKNOWN,
            isInputAccepted = false,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        val moveResult = adapter.executeSelectMove(0, unknownSnapshot)
        assertTrue("Unknown UI state must reject moves", moveResult is BattleInputResult.UnknownUiState || moveResult is BattleInputResult.UnsupportedProfile)
        assertFalse(adapter.selectMove(0, unknownSnapshot))

        val party = listOf(createTestPokemon(species = 6), createTestPokemon(species = 9))
        val switchResult = adapter.executeSwitchPokemon(1, unknownSnapshot, party, activeSlot = 0)
        assertTrue("Unknown UI state must reject switches", switchResult is BattleInputResult.UnknownUiState || switchResult is BattleInputResult.UnsupportedProfile)
        assertFalse(adapter.switchPokemon(1, unknownSnapshot, party, activeSlot = 0))
        assertTrue(dispatcher.recordedButtons.isEmpty())
    }

    // 2. Unsupported / unverified profile rejects interactive actions
    @Test
    fun test2_unsupportedProfileRejectsInteractiveActions() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        val readOnlySnapshot = BattleUiSnapshot(
            state = BattleUiState.COMMAND_MENU,
            selectedActionIndex = 0,
            selectedMoveIndex = 0,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.READ_ONLY // selectMove = false
        )

        val result = adapter.executeSelectMove(0, readOnlySnapshot)
        assertTrue("Read-only / unverified capabilities must reject moves", result is BattleInputResult.UnsupportedProfile)
        assertFalse(adapter.selectMove(0, readOnlySnapshot))
        assertTrue(dispatcher.recordedButtons.isEmpty())
    }

    // 3. Profile with non-matching SHA-256 rejects interactive actions even if profile name matches
    @Test
    fun test3_sha256MismatchRejectsInteractiveActions() {
        val verifiedProfile = RomHackProfile(
            id = "vanilla_firered",
            name = "Pokemon FireRed",
            baseGame = "FireRed",
            gameId = 2,
            sha256Hashes = listOf("e26ee0d44e80e5bc3d1f46d68173f60d8d4622b10a26d11f584e09f58cb2908c"),
            isVerified = true,
            memoryLayoutVerified = true,
            battleUiVerified = true,
            interactiveControlsVerified = true
        )

        val romWithDifferentHash = RomIdentity(
            sha256 = "1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff",
            displayName = "Pokemon FireRed",
            storageKey = "Pokemon_FireRed_111122223333"
        )

        val isInteractiveVerified = BattleInteractionPolicy.isInteractiveVerified(
            verifiedProfile,
            romWithDifferentHash
        )

        assertFalse("Interactive controls must NOT be verified when SHA-256 mismatches", isInteractiveVerified)
    }

    // 4. Busy/animation state rejects moves and switches
    @Test
    fun test4_busyAnimationStateRejectsMovesAndSwitches() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        val busySnapshot = BattleUiSnapshot(
            state = BattleUiState.ANIMATION_OR_TEXT,
            isInputAccepted = false,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        val moveResult = adapter.executeSelectMove(0, busySnapshot)
        assertTrue("Busy state must reject moves", moveResult is BattleInputResult.Busy || moveResult is BattleInputResult.UnsupportedProfile)

        val party = listOf(createTestPokemon(species = 6), createTestPokemon(species = 9))
        val switchResult = adapter.executeSwitchPokemon(1, busySnapshot, party, activeSlot = 0)
        assertTrue("Busy state must reject switches", switchResult is BattleInputResult.Busy || switchResult is BattleInputResult.UnsupportedProfile)
        assertTrue(dispatcher.recordedButtons.isEmpty())
    }

    // 5. Unknown command cursor rejects selectMove
    @Test
    fun test5_unknownCommandCursorRejectsSelectMove() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        val snapshotWithNullCommandCursor = BattleUiSnapshot(
            state = BattleUiState.COMMAND_MENU,
            selectedActionIndex = null, // Unknown cursor!
            selectedMoveIndex = 0,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        val result = adapter.executeSelectMove(0, snapshotWithNullCommandCursor)
        assertTrue("Null command cursor must fail closed with CursorUnavailable", result is BattleInputResult.CursorUnavailable)
        assertTrue(dispatcher.recordedButtons.isEmpty())
    }

    // 6. Unknown move cursor rejects selectMove
    @Test
    fun test6_unknownMoveCursorRejectsSelectMove() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        val snapshotWithNullMoveCursor = BattleUiSnapshot(
            state = BattleUiState.MOVE_MENU,
            selectedActionIndex = 0,
            selectedMoveIndex = null, // Unknown cursor!
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        val result = adapter.executeSelectMove(2, snapshotWithNullMoveCursor)
        assertTrue("Null move cursor must fail closed with CursorUnavailable", result is BattleInputResult.CursorUnavailable)
        assertTrue(dispatcher.recordedButtons.isEmpty())
    }

    // 7. Unknown party cursor rejects switchPokemon
    @Test
    fun test7_unknownPartyCursorRejectsSwitchPokemon() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val adapter = BattleInputAdapter(dispatcher)
        val party = listOf(createTestPokemon(species = 6), createTestPokemon(species = 9))
        val snapshotWithNullPartyCursor = BattleUiSnapshot(
            state = BattleUiState.PARTY_MENU,
            selectedPartySlot = null, // Unknown party cursor!
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        val result = adapter.executeSwitchPokemon(1, snapshotWithNullPartyCursor, party, activeSlot = 0)
        assertTrue("Null party cursor must fail closed with CursorUnavailable", result is BattleInputResult.CursorUnavailable)
        assertTrue(dispatcher.recordedButtons.isEmpty())
    }

    // 8. Transition timeout returns UnexpectedTransition or Timeout
    @Test
    fun test8_transitionTimeoutReturnsUnexpectedTransition() = runBlocking {
        val dispatcher = RecordingDispatcher()
        // stateReader persistently returns COMMAND_MENU instead of expected MOVE_MENU
        val adapter = BattleInputAdapter(dispatcher) {
            BattleUiSnapshot(
                state = BattleUiState.COMMAND_MENU,
                selectedActionIndex = 0,
                selectedMoveIndex = 0,
                stateConfidence = DataConfidence.VERIFIED,
                isInputAccepted = true,
                capabilities = BattleInteractionCapabilities.FULL_VERIFIED
            )
        }

        val initialUi = BattleUiSnapshot(
            state = BattleUiState.COMMAND_MENU,
            selectedActionIndex = 0,
            selectedMoveIndex = 0,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        val result = adapter.executeSelectMove(1, initialUi)
        assertTrue("Unsuccessful state transition must return UnexpectedTransition", result is BattleInputResult.UnexpectedTransition)
        val transitionError = result as BattleInputResult.UnexpectedTransition
        assertEquals(BattleUiState.MOVE_MENU, transitionError.expected)
        assertEquals(BattleUiState.COMMAND_MENU, transitionError.actual)
    }

    // 9. Physical button press during automated macro is preserved
    @Test
    fun test9_physicalButtonPreservedDuringAutomatedMacro() = runBlocking {
        // User is holding UP on physical D-pad
        ControllerInputRouter.setPhysicalMask(InputManager.BTN_UP)
        assertEquals(InputManager.BTN_UP, ControllerInputRouter.getEffectiveMask())

        // Automated press of A
        ControllerInputRouter.sendAutomatedButton(InputManager.BTN_A, durationMs = 15L, delayMs = 5L)

        // After automated button finishes and releases, UP must still be pressed
        assertEquals("Physical held button UP must remain active after automated macro completes",
            InputManager.BTN_UP, ControllerInputRouter.getEffectiveMask())
    }

    // 10. Physical button release during automated macro only clears physical button bit
    @Test
    fun test10_physicalButtonReleaseOnlyClearsPhysicalBit() {
        ControllerInputRouter.setPhysicalMask(InputManager.BTN_B or InputManager.BTN_RIGHT)
        assertEquals(InputManager.BTN_B or InputManager.BTN_RIGHT, ControllerInputRouter.getEffectiveMask())

        // Release BTN_B physically
        ControllerInputRouter.setPhysicalMask(InputManager.BTN_RIGHT)
        assertEquals(InputManager.BTN_RIGHT, ControllerInputRouter.getEffectiveMask())

        // Release D-pad
        ControllerInputRouter.setPhysicalMask(0)
        assertEquals(0, ControllerInputRouter.getEffectiveMask())
    }

    // 11. Two concurrent macro requests are serialized by mutex, not interleaved
    @Test
    fun test11_concurrentMacrosSerializedByMutex() = runBlocking {
        val executionOrder = mutableListOf<String>()

        val job1 = async(Dispatchers.Default) {
            ControllerInputRouter.withRouterLock {
                executionOrder.add("job1_start")
                delay(30L)
                executionOrder.add("job1_end")
            }
        }

        val job2 = async(Dispatchers.Default) {
            delay(5L) // Ensure job1 acquires mutex first
            ControllerInputRouter.withRouterLock {
                executionOrder.add("job2_start")
                delay(20L)
                executionOrder.add("job2_end")
            }
        }

        job1.await()
        job2.await()

        // Mutex must strictly serialize job1 and job2
        assertEquals(listOf("job1_start", "job1_end", "job2_start", "job2_end"), executionOrder)
    }

    // 12. Full BattleInputAdapter actions are serialized, not just individual button presses
    @Test
    fun test12_concurrentBattleActionsCannotInterleave() = runBlocking {
        val events = mutableListOf<String>()
        var dispatchActive = false
        var interleaved = false
        val dispatcher = BattleInputDispatcher { button, _, _ ->
            synchronized(events) {
                if (dispatchActive) interleaved = true
                dispatchActive = true
                events += "start:$button"
            }
            delay(5L)
            synchronized(events) {
                events += "end:$button"
                dispatchActive = false
            }
            true
        }
        val adapter = BattleInputAdapter(dispatcher)
        val ui = BattleUiSnapshot(
            state = BattleUiState.MOVE_MENU,
            selectedMoveIndex = 0,
            stateConfidence = DataConfidence.VERIFIED,
            isInputAccepted = true,
            capabilities = BattleInteractionCapabilities.FULL_VERIFIED
        )

        val first = async(Dispatchers.Default) { adapter.executeSelectMove(3, ui) }
        val second = async(Dispatchers.Default) { adapter.executeSelectMove(0, ui) }

        assertEquals(BattleInputResult.Success, first.await())
        assertEquals(BattleInputResult.Success, second.await())
        assertFalse("A second action must not enter while the first is emitting buttons", interleaved)
        assertEquals(events.count { it.startsWith("start:") }, events.count { it.startsWith("end:") })
    }

    // 13. Gen 3 vanilla: Clefairy is Normal type (NOT Fairy)
    @Test
    fun test12_gen3VanillaClefairyIsNormalType() {
        val clefairy = Gen3VanillaDataPack.getSpecies(35)
        assertNotNull("Clefairy must exist in Gen 3 vanilla pack", clefairy)
        assertEquals("Clefairy type1 must be NORMAL in Gen 3", PokemonType.NORMAL, clefairy!!.type1)
        assertNull("Clefairy type2 must be null in Gen 3", clefairy.type2)
    }

    // 13. Gen 3 vanilla: Jigglypuff is Normal type (NOT Normal/Fairy)
    @Test
    fun test13_gen3VanillaJigglypuffIsNormalType() {
        val jigglypuff = Gen3VanillaDataPack.getSpecies(39)
        assertNotNull("Jigglypuff must exist in Gen 3 vanilla pack", jigglypuff)
        assertEquals("Jigglypuff type1 must be NORMAL in Gen 3", PokemonType.NORMAL, jigglypuff!!.type1)
        assertNull("Jigglypuff type2 must be null in Gen 3 (no Fairy)", jigglypuff.type2)
    }

    // 14. Gen 3 vanilla: Hydro Pump power is 120
    @Test
    fun test14_gen3VanillaHydroPumpPowerIs120() {
        val hydroPump = Gen3VanillaDataPack.getMove(56)
        assertNotNull("Hydro Pump must exist", hydroPump)
        assertEquals("Hydro Pump power must be 120 in Gen 3 vanilla", 120, hydroPump!!.power)
    }

    // 15. Gen 3 vanilla: Flamethrower power is 95
    @Test
    fun test15_gen3VanillaFlamethrowerPowerIs95() {
        val flamethrower = Gen3VanillaDataPack.getMove(53)
        assertNotNull("Flamethrower must exist", flamethrower)
        assertEquals("Flamethrower power must be 95 in Gen 3 vanilla", 95, flamethrower!!.power)

        val surf = Gen3VanillaDataPack.getMove(57)
        assertEquals("Surf power must be 95 in Gen 3 vanilla", 95, surf!!.power)

        val iceBeam = Gen3VanillaDataPack.getMove(58)
        assertEquals("Ice Beam power must be 95 in Gen 3 vanilla", 95, iceBeam!!.power)
    }

    // 16. Modern profile (e.g. Radical Red): Clefairy is Fairy type
    @Test
    fun test16_modernProfileClefairyIsFairyType() {
        val clefairy = ModernDataPack.getSpecies(35)
        assertNotNull("Clefairy must exist in Modern pack", clefairy)
        assertEquals("Clefairy type1 must be FAIRY in modern games", PokemonType.FAIRY, clefairy!!.type1)
    }

    // 17. Modern profile: Hydro Pump power is 110
    @Test
    fun test17_modernProfileHydroPumpPowerIs110() {
        val hydroPump = ModernDataPack.getMove(56)
        assertNotNull("Hydro Pump must exist in Modern pack", hydroPump)
        assertEquals("Hydro Pump power must be 110 in modern generations", 110, hydroPump!!.power)
    }

    // 18. Weather NONE/UNKNOWN displays as "Unknown / Not Observed" or "Clear" only when explicitly observed
    @Test
    fun test18_weatherUnknownNotObservedPresentation() {
        assertEquals("Unknown / Not Observed", WeatherType.UNKNOWN.displayName)
        val field = FieldStatusBuilder.build(
            inBattle = true,
            attacker = createTestPokemon(),
            defender = createTestPokemon(),
            weather = WeatherType.UNKNOWN
        )
        assertEquals(WeatherType.UNKNOWN, field.weather)
        assertEquals("Unknown / Not Observed", field.weather.displayName)
    }

    // 19. Side conditions marked as unobserved when not read from memory
    @Test
    fun test19_sideConditionsMarkedUnobserved() {
        val side = SideEffects()
        assertFalse("Side conditions default to unobserved without memory reader", side.isObserved)
    }

    // 20. Speed estimation banner produces correct conservative wording based on known stats and stages
    @Test
    fun test20_speedEstimationConservativeWording() {
        val comp = SpeedComparison.calculate(
            playerBaseSpeed = 100,
            playerStages = StatStages(spe = 1),
            playerParalyzed = false,
            enemyBaseSpeed = 80,
            enemyStages = StatStages(),
            enemyParalyzed = false
        )
        assertFalse("Speed comparison must not claim definitive order", comp.isDefinitive)
        assertTrue("Speed explanation must state estimate based on known stats and stages",
            comp.explanation.contains("based on known stats and stages"))
        assertEquals(true, comp.playerMovesFirst)
        assertEquals("Faster (known modifiers)", comp.orderLabel)
    }
}
