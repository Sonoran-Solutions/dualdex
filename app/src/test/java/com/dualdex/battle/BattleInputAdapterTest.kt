package com.dualdex.battle

import com.dualdex.emulator.ControllerInputRouter
import com.dualdex.emulator.InputManager
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.companion.CompanionViewModel
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** ROM-free 2x2 menu; presses advance completed frames, never wall-clock time. */
internal class MoveMenuInput(
    var state: BattleUiState = BattleUiState.MOVE_MENU,
    var action: Int = 0,
    var move: Int = 0
) : BattleInputDispatcher {
    val buttons = mutableListOf<Int>()
    val timings = mutableListOf<Pair<Int, Int>>()
    var frames = 0L
    var paused = false
    var players: Int? = 1
    var battler = 0
    var drop: Int? = null
    var openMoveMenu = true
    var targetSelect = false
    var failDispatch = false
    var selected: Int? = null
    var onPress: suspend () -> Unit = {}
    var afterPress: () -> Unit = {}
    val snapshot get() = BattleUiSnapshot(
        state = state, selectedActionIndex = action, selectedMoveIndex = move,
        battlerIndex = battler, playerBattlerCount = players,
        stateConfidence = DataConfidence.VERIFIED, isInputAccepted = true,
        capabilities = BattleInteractionCapabilities.FULL_VERIFIED
    )
    fun adapter() = BattleInputAdapter(this, { paused }, { frames }, { snapshot })
    override suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean =
        error("Move macros must use completed frames")
    override suspend fun waitFrames(frames: Int): Boolean {
        this.frames += frames
        return !paused
    }
    override suspend fun sendFrameButton(buttonMask: Int, settleFrames: Int): Boolean {
        onPress()
        buttons += buttonMask
        timings += 1 to (1 + settleFrames)
        frames += 2 + settleFrames
        if (failDispatch) return false
        if (drop != buttonMask) {
            var cursor = if (state == BattleUiState.COMMAND_MENU) action else move
            when (buttonMask) {
                InputManager.BTN_LEFT -> cursor = cursor and 2
                InputManager.BTN_UP -> cursor = cursor and 1
                InputManager.BTN_RIGHT -> cursor = cursor or 1
                InputManager.BTN_DOWN -> cursor = cursor or 2
                InputManager.BTN_A -> if (state == BattleUiState.COMMAND_MENU) {
                    check(action == 0) { "Would have opened a different command!" }
                    if (openMoveMenu) state = BattleUiState.MOVE_MENU
                } else {
                    selected = move
                    state = if (targetSelect) BattleUiState.TARGET_SELECT else BattleUiState.ANIMATION_OR_TEXT
                }
            }
            if (state == BattleUiState.COMMAND_MENU) action = cursor
            else if (buttonMask != InputManager.BTN_A) move = cursor
        }
        afterPress()
        return !paused
    }
}

class BattleInputAdapterTest {
    @Test fun pollerPassesPackedCursorsAndMacroReaderDoesNotUseItsCache() = runBlocking {
        var nativeCode = 2 or (1 shl 4) or (2 shl 8) or (4 shl 12) or (1 shl 16)
        var reads = 0
        val core = object : LibretroCoreCoordinator() {
            override fun readBattleUiState(gameId: Int): Int { reads++; return nativeCode }
            override fun readBattlePresence(gameId: Int) = 1
        }
        val hash = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"
        val profile = RomHackProfile(
            id = "test_hns", name = "H&S test", baseGame = "Emerald", gameId = 8,
            isVerified = true, memoryLayoutVerified = true, battleStateReadVerified = true,
            sha256Hashes = listOf(hash), battleUiVerified = true, commandCursorVerified = true,
            moveCursorVerified = true, moveSelectionVerified = true
        )
        val vm = CompanionViewModel(coreCoordinator = core)
        vm.setRomSession(RomCompatibility.verified(profile, hash), RomIdentity(hash, "H&S test"))
        vm.setInteractiveBattleControlsEnabled(true)
        vm.pollTick()
        vm.pollTick()
        val cached = vm.battleUiSnapshot.value
        assertEquals(1, cached.selectedActionIndex)
        assertEquals(3, cached.selectedMoveIndex)
        assertTrue(cached.inputSafe)
        nativeCode = 4 // Core changed while the 100ms UI snapshot still says MOVE_MENU.
        core.emulationRunning = true
        val before = reads
        assertTrue(vm.battleInputAdapter.executeSelectMove(3, cached) is BattleInputResult.UnexpectedTransition)
        assertTrue(reads > before)
        assertEquals(BattleUiState.MOVE_MENU, vm.battleUiSnapshot.value.state)
    }

    @Test fun allMovesFromEveryStartingMoveCursor() = runBlocking {
        for (from in 0..3) for (target in 0..3) {
            val input = MoveMenuInput(move = from)
            assertEquals("$from -> $target", BattleInputResult.Success,
                input.adapter().executeSelectMove(target, input.snapshot))
            assertEquals(target, input.selected)
            assertEquals(listOf(InputManager.BTN_LEFT, InputManager.BTN_UP), input.buttons.take(2))
            assertEquals(InputManager.BTN_A, input.buttons.last())
            assertEquals(1, input.buttons.count { it == InputManager.BTN_A })
            assertTrue(input.timings.all { (down, up) -> down >= 1 && up >= 21 })
        }
    }

    @Test fun allMovesFromEveryCommandAndMoveCursor() = runBlocking {
        for (command in 0..3) for (move in 0..3) for (target in 0..3) {
            val input = MoveMenuInput(BattleUiState.COMMAND_MENU, command, move)
            assertEquals(BattleInputResult.Success, input.adapter().executeSelectMove(target, input.snapshot))
            assertEquals(target, input.selected)
            assertEquals(listOf(InputManager.BTN_LEFT, InputManager.BTN_UP, InputManager.BTN_A), input.buttons.take(3))
            assertEquals(2, input.buttons.count { it == InputManager.BTN_A })
        }
    }

    @Test fun droppedDirectionNeverConfirms() = runBlocking {
        val input = MoveMenuInput()
        input.drop = InputManager.BTN_RIGHT
        assertTrue(input.adapter().executeSelectMove(1, input.snapshot) is BattleInputResult.CursorUnavailable)
        assertFalse(input.buttons.contains(InputManager.BTN_A))
        val command = MoveMenuInput(BattleUiState.COMMAND_MENU, action = 3)
        command.drop = InputManager.BTN_LEFT
        assertTrue(command.adapter().executeSelectMove(0, command.snapshot) is BattleInputResult.CursorUnavailable)
        assertFalse(command.buttons.contains(InputManager.BTN_A))
    }

    @Test fun stateOrAuthorityChangesMidWalkAbort() = runBlocking {
        val input = MoveMenuInput(move = 3)
        input.afterPress = { input.state = BattleUiState.ANIMATION_OR_TEXT }
        assertEquals(BattleInputResult.UnexpectedTransition(BattleUiState.MOVE_MENU, BattleUiState.ANIMATION_OR_TEXT),
            input.adapter().executeSelectMove(0, input.snapshot))
        assertFalse(input.buttons.contains(InputManager.BTN_A))
        val changed = MoveMenuInput()
        changed.afterPress = { changed.battler = 2 }
        assertTrue(changed.adapter().executeSelectMove(0, changed.snapshot) is BattleInputResult.UnsupportedProfile)
        assertFalse(changed.buttons.contains(InputManager.BTN_A))
    }

    @Test fun pauseAndDoublesAndUnknownTopologyAreRefused() = runBlocking {
        val input = MoveMenuInput()
        input.paused = true
        assertEquals(BattleInputResult.Paused, input.adapter().executeSelectMove(0, input.snapshot))
        assertTrue(input.buttons.isEmpty())
        input.paused = false
        for (players in listOf(2, null)) {
            input.players = players
            assertTrue(input.adapter().executeSelectMove(0, input.snapshot) is BattleInputResult.UnsupportedProfile)
            assertTrue(input.buttons.isEmpty())
        }
        input.players = 1
        input.afterPress = { input.paused = true }
        assertEquals(BattleInputResult.Paused, input.adapter().executeSelectMove(0, input.snapshot))
        assertFalse(input.buttons.contains(InputManager.BTN_A))
    }

    @Test fun unknownCursorAndMissingDirectReaderNeverDispatch() = runBlocking {
        val input = MoveMenuInput()
        assertTrue(input.adapter().executeSelectMove(0, input.snapshot.copy(selectedMoveIndex = 4)) is BattleInputResult.CursorUnavailable)
        val adapter = BattleInputAdapter(input, { false }, { 0L })
        assertTrue(adapter.executeSelectMove(0, input.snapshot) is BattleInputResult.CursorUnavailable)
        assertTrue(input.buttons.isEmpty())
    }

    @Test fun cachedInteractiveStateCannotAuthorizeCurrentBusyCore() = runBlocking {
        val input = MoveMenuInput()
        val cached = input.snapshot
        input.state = BattleUiState.ANIMATION_OR_TEXT
        assertTrue(input.adapter().executeSelectMove(0, cached) is BattleInputResult.UnexpectedTransition)
        assertTrue(input.buttons.isEmpty())
        input.state = BattleUiState.MOVE_MENU
        input.move = 3
        assertEquals(BattleInputResult.Success, input.adapter().executeSelectMove(0, cached))
        assertEquals(0, input.selected)
    }

    @Test fun targetSelectionReportsAndSendsNoFurtherInput() = runBlocking {
        val input = MoveMenuInput()
        input.targetSelect = true
        assertEquals(BattleInputResult.TargetSelectionRequired, input.adapter().executeSelectMove(3, input.snapshot))
        assertEquals(1, input.buttons.count { it == InputManager.BTN_A })
        assertEquals(3, input.selected)
    }

    @Test fun frameWatchdogAndFailedDispatcherNeverConfirm() = runBlocking {
        val input = MoveMenuInput()
        input.afterPress = { input.frames += BattleInputAdapter.WATCHDOG_FRAMES }
        assertTrue(input.adapter().executeSelectMove(0, input.snapshot) is BattleInputResult.Timeout)
        assertFalse(input.buttons.contains(InputManager.BTN_A))
        val failed = MoveMenuInput()
        failed.failDispatch = true
        assertTrue(failed.adapter().executeSelectMove(0, failed.snapshot) is BattleInputResult.Timeout)
        assertFalse(failed.buttons.contains(InputManager.BTN_A))
    }

    @Test fun routerFramesPreservePhysicalInputAndReleaseOnFailureOrCancellation() = runBlocking {
        ControllerInputRouter.reset()
        ControllerInputRouter.setPhysicalMask(InputManager.BTN_L)
        val masks = mutableListOf<Int>()
        val waits = mutableListOf<Int>()
        assertTrue(ControllerInputRouter.sendAutomatedFrameButton(InputManager.BTN_A, 20, {
            waits += it
            true
        }, { masks += it }))
        assertEquals(listOf(1, 21), waits)
        assertEquals(listOf(InputManager.BTN_L or InputManager.BTN_A, InputManager.BTN_L), masks)
        assertFalse(ControllerInputRouter.sendAutomatedFrameButton(InputManager.BTN_A, 20, { false }, {}))
        assertEquals(InputManager.BTN_L, ControllerInputRouter.getEffectiveMask())
        ControllerInputRouter.setPhysicalMask(InputManager.BTN_A)
        assertFalse(ControllerInputRouter.sendAutomatedFrameButton(InputManager.BTN_A, 20,
            { fail("Physically held A must not dispatch another edge"); true }, {}))
        assertEquals(InputManager.BTN_A, ControllerInputRouter.getEffectiveMask())
        ControllerInputRouter.setPhysicalMask(InputManager.BTN_L)
        try {
            ControllerInputRouter.sendAutomatedFrameButton(InputManager.BTN_A, 20, { throw CancellationException() }, {})
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(InputManager.BTN_L, ControllerInputRouter.getEffectiveMask())
        } finally {
            ControllerInputRouter.reset()
        }
    }
}
