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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    var onWait: () -> Unit = {}
    var onRead: () -> Unit = {}
    var readFailures = 0
    var reads = 0
    val coreMasks = mutableListOf<Int>()
    fun read(): BattleUiSnapshot? {
        onRead()
        reads++
        if (readFailures > 0) { readFailures--; return null }
        return snapshot
    }
    val snapshot get() = BattleUiSnapshot(
        state = state, selectedActionIndex = action, selectedMoveIndex = move,
        battlerIndex = battler, playerBattlerCount = players,
        stateConfidence = DataConfidence.VERIFIED, isInputAccepted = true,
        capabilities = BattleInteractionCapabilities.FULL_VERIFIED
    )
    fun adapter() = BattleInputAdapter(this, { paused }, { frames },
        macroDispatcher = Dispatchers.Unconfined, stateReader = { read() })
    override suspend fun <T> withExclusiveAutomation(block: suspend () -> T): T =
        ControllerInputRouter.withExclusiveAutomation({ coreMasks += it }, block)
    override suspend fun sendButton(buttonMask: Int, durationMs: Long, delayMs: Long): Boolean =
        error("Move macros must use completed frames")
    override suspend fun waitFrames(frames: Int): Boolean {
        this.frames += frames
        onWait()
        return !paused
    }
    override suspend fun sendFrameButton(buttonMask: Int, settleFrames: Int): Boolean {
        onPress()
        buttons += buttonMask
        timings += 1 to (1 + settleFrames)
        frames += 2 + settleFrames
        if (failDispatch) return false
        if (!ControllerInputRouter.sendAutomatedFrameButton(buttonMask, settleFrames, { true },
                { coreMasks += it })) return false
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
                    state = if (targetSelect) BattleUiState.TARGET_SELECT else BattleUiState.TURN_RUNNING
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
        var nativeCode: Int? = 2 or (1 shl 4) or (2 shl 8) or (4 shl 12) or (1 shl 16)
        var reads = 0
        val core = object : LibretroCoreCoordinator() {
            override fun readBattleUiState(gameId: Int): Int? { reads++; return nativeCode }
            override fun readBattlePresence(gameId: Int) = 1
            override fun setInputButtons(buttonMask: Int) = Unit
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
        nativeCode = null
        vm.pollTick()
        assertEquals(cached, vm.battleUiSnapshot.value) // Failed reads retain the last UI snapshot.
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
            assertEquals(directions(from, target) + InputManager.BTN_A, input.buttons)
            assertEquals(1, input.buttons.count { it == InputManager.BTN_A })
            assertEquals(List(input.buttons.size - 1) { 1 to 21 } + (1 to 1), input.timings)
        }
    }

    @Test fun allMovesFromEveryCommandAndMoveCursor() = runBlocking {
        for (command in 0..3) for (move in 0..3) for (target in 0..3) {
            val input = MoveMenuInput(BattleUiState.COMMAND_MENU, command, move)
            assertEquals(BattleInputResult.Success, input.adapter().executeSelectMove(target, input.snapshot))
            assertEquals(target, input.selected)
            assertEquals(directions(command, 0) + InputManager.BTN_A +
                directions(move, target) + InputManager.BTN_A, input.buttons)
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
        assertTrue(changed.adapter().executeSelectMove(1, changed.snapshot) is BattleInputResult.UnsupportedProfile)
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
        assertEquals(BattleInputResult.Paused, input.adapter().executeSelectMove(1, input.snapshot))
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
        assertTrue(input.adapter().executeSelectMove(3, input.snapshot) is BattleInputResult.Timeout)
        assertFalse(input.buttons.contains(InputManager.BTN_A))
        val failed = MoveMenuInput()
        failed.failDispatch = true
        assertTrue(failed.adapter().executeSelectMove(3, failed.snapshot) is BattleInputResult.Timeout)
        assertFalse(failed.buttons.contains(InputManager.BTN_A))
    }

    @Test fun transientReadTimeoutIsRetriedBetweenFramesAndExhaustionIsTyped() = runBlocking {
        val input = MoveMenuInput()
        input.afterPress = { if (input.selected == null) input.readFailures = 1 }
        assertEquals(BattleInputResult.Success, input.adapter().executeSelectMove(3, input.snapshot))
        assertEquals(48L, input.frames) // 2 directions (44), A (2), two read retry frames.
    }

    @Test fun unavailableReadNeverBecomesUnknownStateOrConfirms() = runBlocking {
        val input = MoveMenuInput()
        input.readFailures = BattleInputAdapter.READ_ATTEMPTS
        assertEquals(BattleInputResult.Timeout("Core battle UI read unavailable after 3 attempts"),
            input.adapter().executeSelectMove(3, input.snapshot))
        assertEquals(2L, input.frames)
        assertTrue(input.buttons.isEmpty())
    }

    @Test fun readsRunOnInjectedMacroDispatcherAndResultReturnsToCaller() = runBlocking {
        val caller = Thread.currentThread()
        Executors.newSingleThreadExecutor { Thread(it, "move-macro-test") }.asCoroutineDispatcher().use { macro ->
            val input = MoveMenuInput()
            input.onRead = { assertTrue(Thread.currentThread().name.startsWith("move-macro-test")) }
            val adapter = BattleInputAdapter(input, { false }, { input.frames }, macro) { input.read() }
            assertEquals(BattleInputResult.Success, adapter.executeSelectMove(3, input.snapshot))
            assertSame(caller, Thread.currentThread())
        }
    }

    @Test fun postConfirmTextMustResolveToTurnRunningOrRefusal() = runBlocking {
        for (end in listOf(BattleUiState.TURN_RUNNING, BattleUiState.MOVE_MENU, BattleUiState.COMMAND_MENU)) {
            val input = MoveMenuInput()
            input.afterPress = { if (input.selected != null) input.state = BattleUiState.ANIMATION_OR_TEXT }
            var observedFrames = 0
            input.onWait = {
                assertEquals(1, input.buttons.count { it == InputManager.BTN_A })
                if (++observedFrames == 3) input.state = end
            }
            val expected = if (end == BattleUiState.TURN_RUNNING) BattleInputResult.Success else BattleInputResult.MoveRefused
            assertEquals(expected, input.adapter().executeSelectMove(0, input.snapshot))
            assertEquals(3, observedFrames)
            assertEquals(listOf(InputManager.BTN_A), input.buttons)
        }
        val stuck = MoveMenuInput()
        stuck.afterPress = { stuck.state = BattleUiState.ANIMATION_OR_TEXT }
        assertEquals(BattleInputResult.Timeout("Move acceptance was not observed"),
            stuck.adapter().executeSelectMove(0, stuck.snapshot))
        assertEquals(2L + BattleInputAdapter.OBSERVATION_FRAMES, stuck.frames)
    }

    @Test fun droppedConfirmReportsUnregisteredAndNeverRetriesA() = runBlocking {
        val input = MoveMenuInput()
        input.drop = InputManager.BTN_A
        assertEquals(BattleInputResult.ConfirmNotRegistered,
            input.adapter().executeSelectMove(0, input.snapshot))
        assertEquals(listOf(InputManager.BTN_A), input.buttons)
        assertNull(input.selected)
        assertEquals(2L + BattleInputAdapter.CONFIRM_REGISTRATION_FRAMES, input.frames)
    }

    @Test fun droppedConfirmWithTransientUnknownOrUnexpectedStateDoesNotImplyRefusal() = runBlocking {
        for (transient in listOf(BattleUiState.UNKNOWN, BattleUiState.PARTY_MENU, BattleUiState.BAG_MENU)) {
            val input = MoveMenuInput()
            input.drop = InputManager.BTN_A
            var frames = 0
            input.onWait = {
                input.state = if (++frames == 1) transient else BattleUiState.MOVE_MENU
            }
            assertEquals("$transient is not evidence of text/animation", BattleInputResult.ConfirmNotRegistered,
                input.adapter().executeSelectMove(0, input.snapshot))
            assertEquals(listOf(InputManager.BTN_A), input.buttons)
            assertNull(input.selected)
            assertEquals(2L + BattleInputAdapter.CONFIRM_REGISTRATION_FRAMES, input.frames)
        }
    }

    @Test fun initialMenuFramesDoNotHideDelayedTextOrAcceptance() = runBlocking {
        for (end in listOf(BattleUiState.MOVE_MENU, BattleUiState.COMMAND_MENU,
                           BattleUiState.TURN_RUNNING, BattleUiState.TARGET_SELECT)) {
            val input = MoveMenuInput()
            input.drop = InputManager.BTN_A
            var frames = 0
            input.onWait = {
                frames++
                input.state = when {
                    frames < 2 -> BattleUiState.MOVE_MENU
                    frames < 4 -> BattleUiState.ANIMATION_OR_TEXT
                    else -> end
                }
            }
            val expected = when (end) {
                BattleUiState.TURN_RUNNING -> BattleInputResult.Success
                BattleUiState.TARGET_SELECT -> BattleInputResult.TargetSelectionRequired
                else -> BattleInputResult.MoveRefused
            }
            assertEquals(expected, input.adapter().executeSelectMove(0, input.snapshot))
            assertEquals(4, frames)
            assertEquals(listOf(InputManager.BTN_A), input.buttons)
        }
    }

    @Test fun unregisteredConfirmStillChecksPlayerAuthority() = runBlocking {
        val input = MoveMenuInput()
        input.drop = InputManager.BTN_A
        input.onWait = { input.battler = 2 }
        assertTrue(input.adapter().executeSelectMove(0, input.snapshot) is BattleInputResult.UnsupportedProfile)
        assertEquals(listOf(InputManager.BTN_A), input.buttons)
    }

    @Test fun finalCursorHasOneReadImmediatelyBeforeA() = runBlocking {
        for (from in 0..3) for (target in 0..3) {
            val input = MoveMenuInput(move = from)
            var readsBeforeA = 0
            input.onPress = { readsBeforeA = input.reads }
            assertEquals(BattleInputResult.Success, input.adapter().executeSelectMove(target, input.snapshot))
            assertEquals(1 + directions(from, target).size, readsBeforeA)
        }
    }

    @Test fun heldPhysicalButtonRefusesBeforeAnyCoreInput() = runBlocking {
        ControllerInputRouter.reset()
        try {
            ControllerInputRouter.setPhysicalMask(InputManager.BTN_LEFT)
            val input = MoveMenuInput()
            assertEquals(BattleInputResult.Cancelled("Physical button held"),
                input.adapter().executeSelectMove(3, input.snapshot))
            assertTrue(input.buttons.isEmpty())
            assertTrue(input.coreMasks.isEmpty())
            assertEquals(InputManager.BTN_LEFT, ControllerInputRouter.getEffectiveMask())
        } finally {
            ControllerInputRouter.reset()
        }
    }

    @Test fun macroSuppressesPhysicalDirectionAndLatchesThroughPostAObservation() = runBlocking {
        ControllerInputRouter.reset()
        try {
            val input = MoveMenuInput()
            input.onPress = { ControllerInputRouter.setPhysicalMask(InputManager.BTN_LEFT) }
            input.afterPress = { if (input.selected != null) input.state = BattleUiState.ANIMATION_OR_TEXT }
            input.onWait = {
                assertEquals(0, ControllerInputRouter.getEffectiveMask())
                ControllerInputRouter.setPhysicalMask(InputManager.BTN_LEFT or InputManager.BTN_UP)
                assertEquals(0, ControllerInputRouter.getEffectiveMask())
                input.state = BattleUiState.TURN_RUNNING
            }
            assertEquals(BattleInputResult.Success, input.adapter().executeSelectMove(3, input.snapshot))
            assertEquals(listOf(0, InputManager.BTN_RIGHT, 0, InputManager.BTN_DOWN, 0, InputManager.BTN_A, 0, 0),
                input.coreMasks)
            val held = InputManager.BTN_LEFT or InputManager.BTN_UP
            assertEquals(held, ControllerInputRouter.getPhysicalMask())
            assertEquals(0, ControllerInputRouter.getEffectiveMask())
            ControllerInputRouter.setPhysicalMask(held)
            assertEquals(0, ControllerInputRouter.getEffectiveMask())
            ControllerInputRouter.setPhysicalMask(InputManager.BTN_UP)
            ControllerInputRouter.setPhysicalMask(held)
            assertEquals(InputManager.BTN_LEFT, ControllerInputRouter.getEffectiveMask())
            ControllerInputRouter.setPhysicalMask(0)
            ControllerInputRouter.setPhysicalMask(held)
            assertEquals(held, ControllerInputRouter.getEffectiveMask())
        } finally {
            ControllerInputRouter.reset()
        }
    }

    @Test fun sessionReleasesOnCancellationExceptionAndWatchdog() = runBlocking {
        for (failure in listOf("cancel", "exception", "watchdog")) {
            ControllerInputRouter.reset()
            val input = MoveMenuInput()
            input.onPress = {
                ControllerInputRouter.setPhysicalMask(InputManager.BTN_LEFT)
                when (failure) {
                    "cancel" -> throw CancellationException()
                    "exception" -> error("Synthetic dispatch failure")
                }
            }
            input.afterPress = { input.frames += BattleInputAdapter.WATCHDOG_FRAMES }
            try {
                val result = input.adapter().executeSelectMove(3, input.snapshot)
                assertNotEquals("Cancellation must propagate", "cancel", failure)
                assertTrue(result is BattleInputResult.Timeout)
            } catch (_: CancellationException) {
                assertEquals("cancel", failure)
            } finally {
                assertEquals(0, input.coreMasks.last())
                assertEquals(InputManager.BTN_LEFT, ControllerInputRouter.getPhysicalMask())
                assertEquals(0, ControllerInputRouter.getEffectiveMask())
                ControllerInputRouter.setPhysicalMask(0)
                ControllerInputRouter.setPhysicalMask(InputManager.BTN_LEFT)
                assertEquals(InputManager.BTN_LEFT, ControllerInputRouter.getEffectiveMask())
                ControllerInputRouter.reset()
            }
        }
    }

    @Test fun cancellationDuringHeldEdgeReleasesButtonAndSession() = runBlocking {
        ControllerInputRouter.reset()
        val masks = mutableListOf<Int>()
        try {
            ControllerInputRouter.withExclusiveAutomation({ masks += it }) {
                ControllerInputRouter.sendAutomatedFrameButton(InputManager.BTN_A, 0, {
                    ControllerInputRouter.setPhysicalMask(InputManager.BTN_LEFT)
                    throw CancellationException()
                }, { masks += it })
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf(0, InputManager.BTN_A, 0, 0), masks)
            assertEquals(InputManager.BTN_LEFT, ControllerInputRouter.getPhysicalMask())
            assertEquals(0, ControllerInputRouter.getEffectiveMask())
            ControllerInputRouter.setPhysicalMask(0)
            ControllerInputRouter.setPhysicalMask(InputManager.BTN_LEFT)
            assertEquals(InputManager.BTN_LEFT, ControllerInputRouter.getEffectiveMask())
        } finally {
            ControllerInputRouter.reset()
        }
    }

    @Test fun realUnknownStateIsNotRetriedAsLockContention() = runBlocking {
        val input = MoveMenuInput()
        val cached = input.snapshot
        input.state = BattleUiState.UNKNOWN
        assertEquals(BattleInputResult.UnexpectedTransition(BattleUiState.MOVE_MENU, BattleUiState.UNKNOWN),
            input.adapter().executeSelectMove(3, cached))
        assertEquals(1, input.reads)
        assertEquals(0L, input.frames)
        assertTrue(input.buttons.isEmpty())
    }

    @Test fun coreWriteDoesNotHoldRouterStateLockAndKeepsEdgesOrdered() = runBlocking {
        ControllerInputRouter.reset()
        try {
            val masks = mutableListOf<Int>()
            ControllerInputRouter.withExclusiveAutomation({ masks += it }) {
                assertTrue(ControllerInputRouter.sendAutomatedFrameButton(InputManager.BTN_A, 20,
                    { true }, { mask ->
                        masks += mask
                        val recorded = CountDownLatch(1)
                        val physical = Thread {
                            ControllerInputRouter.setPhysicalMask(InputManager.BTN_LEFT)
                            recorded.countDown()
                        }
                        physical.start()
                        assertTrue("Physical state cannot wait on a core write", recorded.await(5, TimeUnit.SECONDS))
                        physical.join()
                    }))
            }
            assertEquals(listOf(0, InputManager.BTN_A, 0, 0), masks)
        } finally {
            ControllerInputRouter.reset()
        }
    }

    private fun directions(from: Int, target: Int): List<Int> = buildList {
        if (from and 1 != target and 1) add(if (target and 1 == 0) InputManager.BTN_LEFT else InputManager.BTN_RIGHT)
        if (from and 2 != target and 2) add(if (target and 2 == 0) InputManager.BTN_UP else InputManager.BTN_DOWN)
    }
}
