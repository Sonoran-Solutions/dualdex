package com.dualdex.companion

import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.LibretroCoreBridge
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.bindSyntheticRom
import com.dualdex.emulator.syntheticRomBridge
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.battle.*
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.hns.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RomSessionObservationSafetyTest {
    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)
    private val profileA = RomHackProfile(
        id = "synthetic-a", name = "Synthetic A", baseGame = "FireRed", gameId = 2,
        sha256Hashes = listOf(shaA), isVerified = true, memoryLayoutVerified = true
    )
    private val profileB = profileA.copy(id = "synthetic-b", name = "Synthetic B", sha256Hashes = listOf(shaB))

    private fun mon(species: Int) = ParsedPokemon(
        isValid = true, isEmpty = false, pid = species.toLong(), tid = 1, sid = 2,
        nickname = "synthetic", otName = "test", species = species, heldItem = 0,
        level = 5, nature = 0, natureName = "Hardy", isShiny = false, abilitySlot = 0,
        isEgg = false, friendship = 0, experience = 0L, hpIv = 0, attackIv = 0,
        defenseIv = 0, speedIv = 0, spAttackIv = 0, spDefenseIv = 0, hpEv = 0,
        attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(0, 0, 0, 0), pp = intArrayOf(0, 0, 0, 0), currentHp = 1,
        maxHp = 1, attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
        statusCondition = 0L
    )

    @Test fun unboundSessionCannotCallProfileReadersThenExactBindingReadsNormally() {
        val reads = AtomicInteger()
        val core = object : LibretroCoreCoordinator(bridge = syntheticRomBridge()) {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                reads.incrementAndGet()
                return arrayOf(mon(25))
            }
        }
        val vm = CompanionViewModel(coreCoordinator = core)
        val identity = RomIdentity(shaA, "Synthetic A")
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), identity)

        vm.pollTick()
        assertEquals(0, reads.get())
        assertTrue(vm.playerParty.value.isEmpty())

        bindSyntheticRom(core, identity)
        vm.pollTick()
        assertEquals(1, reads.get())
        assertEquals(25, vm.playerParty.value.single().species)
    }

    @Test fun trustRevocationDuringReaderCannotLeaveOldResultAfterSetterReturns() {
        val core = ObservationCore()
        val vm = observedSession(core)
        val enteredReader = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val setterStarted = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        core.beforeParty = {
            enteredReader.countDown()
            check(releaseReader.await(5, TimeUnit.SECONDS))
        }
        val poll = Thread {
            try { vm.pollTick() } catch (t: Throwable) { failure.set(t) }
        }
        val setter = Thread {
            try {
                setterStarted.countDown()
                vm.setProfile(RomHackProfile.UNSUPPORTED)
            } catch (t: Throwable) { failure.set(t) }
        }
        try {
            poll.start()
            assertTrue("reader was not entered", enteredReader.await(5, TimeUnit.SECONDS))
            setter.start()
            assertTrue(setterStarted.await(5, TimeUnit.SECONDS))
        } finally {
            releaseReader.countDown()
            poll.join(5000)
            setter.join(5000)
        }
        assertFalse(poll.isAlive || setter.isAlive)
        assertNull(failure.get())
        assertCleared(vm)
        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)
        val before = core.calls.toList()
        vm.pollTick()
        assertCleared(vm)
        assertEquals(before, core.calls)
    }

    @Test fun readerLockTimeoutDoesNotPublishOrInvokeReaders() {
        val reads = AtomicInteger()
        val core = object : LibretroCoreCoordinator(bridge = syntheticRomBridge()) {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                reads.incrementAndGet()
                return arrayOf(mon(25))
            }
        }
        val vm = CompanionViewModel(coreCoordinator = core)
        val identity = RomIdentity(shaA, "Synthetic A")
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), identity)
        bindSyntheticRom(core, identity)

        val pollFinished = CountDownLatch(1)
        core.lock.lock()
        try {
            Thread {
                vm.pollTick()
                pollFinished.countDown()
            }.start()
            assertTrue("poll should fail closed at the coordinator's lock timeout", pollFinished.await(5, TimeUnit.SECONDS))
        } finally {
            core.lock.unlock()
        }
        assertEquals(0, reads.get())
        assertTrue(vm.playerParty.value.isEmpty())
    }

    private val hnsProfile = profileA.copy(
        id = "synthetic-hns", baseGame = "Emerald", gameId = 8,
        battleStateReadVerified = true, battleUiVerified = true,
        commandCursorVerified = true, moveCursorVerified = true, moveSelectionVerified = true
    )
    private val identityA = RomIdentity(shaA, "Synthetic A")
    private val identityB = RomIdentity(shaB, "Synthetic B")

    private inner class ObservationCore : LibretroCoreCoordinator(object : LibretroCoreBridge {
        override fun loadRom(romPath: String) = true
        override fun loadState(statePath: String) = true
        override fun loadSaveRam(savePath: String) = true
    }) {
        val calls = mutableListOf<Pair<String, Int>>()
        var valid = true
        var species = 25
        var beforeExclusive: () -> Unit = {}
        var beforeParty: () -> Unit = {}
        var beforeBattle: () -> Unit = {}

        override fun <T> executeExclusive(timeoutMs: Long, block: () -> T): T {
            beforeExclusive()
            return super.executeExclusive(timeoutMs, block)
        }

        private fun record(name: String, gameId: Int) {
            check(lock.isHeldByCurrentThread) { "$name escaped the core transaction" }
            calls += name to gameId
        }

        override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
            record("party", gameId)
            beforeParty()
            return if (valid) arrayOf(mon(species)) else null
        }
        override fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
            record("enemy", gameId)
            return if (valid) arrayOf(mon(150)) else null
        }
        override fun readBattlePresence(gameId: Int): Int {
            record("presence", gameId)
            return 1
        }
        override fun getActiveBattlerSlot(gameId: Int): Int {
            record("slot", gameId)
            return 0
        }
        override fun resolveActiveEnemy(gameId: Int): ActiveEnemyResolution {
            record("resolution", gameId)
            return ActiveEnemyResolution(state = ActiveEnemyState.SLOT, partySlot = 0)
        }
        override fun readBattleStatStages(gameId: Int, battlerIndex: Int): IntArray {
            record("stages", gameId)
            return IntArray(7) { 7 }
        }
        override fun readBattleUiState(gameId: Int): Int {
            record("ui", gameId)
            beforeBattle()
            return 2 or (1 shl 4) or (1 shl 8) or (1 shl 12) or (1 shl 16)
        }
        override fun readPlayerLocation(gameId: Int): PlayerLocation? {
            record("location", gameId)
            return if (valid) PlayerLocation(0, 0, 0, 1, 1, 1, 1, 0, 0, false, true) else null
        }
        override fun readChallengeSettings(gameId: Int): HnsChallengeSettingsSnapshot {
            record("challenge", gameId)
            return HnsChallengeSettingsSnapshot(status = HnsChallengeSettingsStatus.OBSERVED)
        }
        override fun readBattlerRuntimeState(gameId: Int, role: Int): HnsBattlerRuntimeState {
            record("battler", gameId)
            return HnsBattlerRuntimeState(status = HnsBattlerRuntimeStatus.OBSERVED,
                battlerIndex = role, partySlot = 0, speciesId = species)
        }
        override fun readHnsBattlers(gameId: Int): List<HnsBattlerRuntimeState> {
            record("indexed battlers", gameId)
            val doubles = HnsDoublesRuntimeState(4, 0, listOf(0, 0), false,
                (0..3).map { HnsDoublesBattler(it, it, it / 2, 1, species, 1, 0,
                    false, false, false, 0, 0, 0) })
            return (0..3).map { HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED, battlerIndex = it,
                partySlot = it / 2, speciesId = species, doubles = doubles) }
        }
    }

    private fun observedSession(core: ObservationCore): CompanionViewModel {
        bindSyntheticRom(core, identityA)
        return CompanionViewModel(coreCoordinator = core).also {
            it.setRomSession(RomCompatibility.verified(hnsProfile, shaA), identityA)
            it.setInteractiveBattleControlsEnabled(true)
            repeat(2) { _ -> it.pollTick() } // Existing battle-presence stabilization.
            it.selectHnsEnemyBattler(1)
            assertEquals(1, it.selectedHnsEnemyBattler.value)
            assertObserved(it)
        }
    }

    private fun assertObserved(vm: CompanionViewModel) {
        assertFalse(vm.playerParty.value.isEmpty())
        assertFalse(vm.enemyParty.value.isEmpty())
        assertNotNull(vm.playerBattlerState.value)
        assertNotNull(vm.enemyBattlerState.value)
        assertEquals(4, vm.hnsBattlers.value.size)
        assertNotNull(vm.playerLocation.value)
        assertNotNull(vm.challengeSettings.value)
        assertTrue(vm.isInBattle.value)
        assertTrue(vm.battleUiSnapshot.value.inputSafe)
        assertFalse(vm.playerStatStages.value.isNeutral)
        assertFalse(vm.enemyStatStages.value.isNeutral)
    }

    private fun assertCleared(vm: CompanionViewModel) {
        assertTrue(vm.playerParty.value.isEmpty())
        assertTrue(vm.enemyParty.value.isEmpty())
        assertNull(vm.playerBattlerState.value)
        assertNull(vm.enemyBattlerState.value)
        assertTrue(vm.hnsBattlers.value.isEmpty())
        assertNull(vm.selectedHnsEnemyBattler.value)
        assertEquals(BattleUiSnapshot(), vm.battleUiSnapshot.value)
        assertEquals(BattlePresence.UNKNOWN, vm.battlePresence.value)
        assertFalse(vm.isInBattle.value)
        assertEquals(-1, vm.activePlayerBattlerIndex.value)
        assertEquals(-1, vm.activeEnemyMemberIndex.value)
        assertEquals(ActiveEnemyResolution(), vm.activeEnemyResolution.value)
        assertTrue(vm.playerStatStages.value.isNeutral)
        assertTrue(vm.enemyStatStages.value.isNeutral)
        assertNull(vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)
        assertNull(vm.challengeSettings.value)
    }

    private fun mismatchWithoutWatcher(transition: (ObservationCore) -> Unit) {
        val core = ObservationCore()
        val vm = observedSession(core)
        val before = core.calls.toList()
        transition(core)
        repeat(2) {
            vm.pollTick() // No startPolling() / generation watcher anywhere in this fixture.
            assertCleared(vm)
            assertEquals(before, core.calls)
        }
    }

    @Test fun unboundTransitionWithoutWatcherClearsEveryObservationAndRefusesEveryReader() =
        mismatchWithoutWatcher { assertTrue(it.loadRom("unbound")) }

    @Test fun unloadedTransitionWithoutWatcherClearsAndStaysClear() =
        mismatchWithoutWatcher { it.unloadRom() }

    @Test fun foreignRomRefusesAllReadersThenLegitimateSessionRecovers() {
        val core = ObservationCore()
        val vm = observedSession(core)
        val before = core.calls.toList()
        bindSyntheticRom(core, identityB)
        repeat(2) { vm.pollTick(); assertCleared(vm) }
        assertEquals(before, core.calls)
        val nextProfile = profileB.copy(battleStateReadVerified = true, gameId = 1)
        core.species = 6
        vm.setRomSession(RomCompatibility.verified(nextProfile, shaB), identityB)
        repeat(2) { vm.pollTick() }
        assertEquals(6, vm.playerParty.value.single().species)
        assertTrue(core.calls.drop(before.size).all { it.second == nextProfile.gameId })
        assertNotNull(vm.playerLocation.value)
        assertNotNull(vm.enemyBattlerState.value)
    }

    private fun generationTransition(transition: (ObservationCore) -> Unit) {
        val core = ObservationCore()
        val vm = observedSession(core)
        val generation = core.stateGeneration
        transition(core)
        assertTrue(core.stateGeneration > generation)
        assertEquals(shaA, core.loadedRomSnapshot().sha256)
        core.valid = false
        // Previously published party/location must not enjoy five-read retention in a new generation.
        vm.pollTick()
        assertTrue(vm.playerParty.value.isEmpty())
        assertNull(vm.playerLocation.value)
        assertEquals(BattleUiSnapshot(), vm.battleUiSnapshot.value)
        core.valid = true
        core.species = 6
        repeat(2) { vm.pollTick() }
        assertObserved(vm)
        assertEquals(6, vm.playerParty.value.single().species)
    }

    @Test fun sameHashReloadCannotRetainPreviousGenerationObservations() =
        generationTransition { bindSyntheticRom(it, identityA) }

    @Test fun stateLoadCannotRetainPreviousGenerationObservations() =
        generationTransition { assertTrue(it.loadState("synthetic.state")) }

    @Test fun sramLoadCannotRetainPreviousGenerationObservations() =
        generationTransition { assertTrue(it.loadSaveRam("synthetic.sav")) }

    @Test fun resetCannotRetainPreviousGenerationObservations() =
        generationTransition { it.resetCore() }

    @Test fun unchangedTrustedSessionRetainsTransientReadsClearsAtThresholdAndResetsAfterMismatch() {
        val core = ObservationCore()
        val vm = observedSession(core)
        core.valid = false
        repeat(LiveObservationStabilizer.DEFAULT_INVALID_READ_THRESHOLD - 1) {
            vm.pollTick()
            assertFalse(vm.playerParty.value.isEmpty())
            assertFalse(vm.enemyParty.value.isEmpty())
            assertNotNull(vm.playerLocation.value)
        }
        vm.pollTick()
        assertTrue(vm.playerParty.value.isEmpty())
        assertTrue(vm.enemyParty.value.isEmpty())
        assertNull(vm.playerLocation.value)
        core.valid = true
        vm.pollTick()
        core.loadRom("unbound")
        vm.pollTick()
        assertCleared(vm)
        bindSyntheticRom(core, identityA)
        repeat(2) { vm.pollTick() }
        core.valid = false
        vm.pollTick()
        assertFalse(vm.playerParty.value.isEmpty())
        assertNotNull(vm.playerLocation.value)
    }

    @Test fun directBattleReaderAdmitsExactHnsAndRecoversAfterEachDeniedSession() {
        val transitions = listOf<(CompanionViewModel, ObservationCore) -> Unit>(
            { _, core -> core.loadRom("unbound") },
            { _, core -> core.unloadRom() },
            { _, core -> bindSyntheticRom(core, identityB) },
            { vm, _ -> vm.setRomIdentity(identityA) }, // Drops trust while retaining profile.
            { vm, _ -> vm.setProfile(RomHackProfile.UNSUPPORTED) }
        )
        for (transition in transitions) {
            val core = ObservationCore()
            val vm = observedSession(core)
            val before = core.calls.size
            assertTrue(vm.readBattleUiSnapshot()!!.inputSafe)
            assertEquals(listOf("ui" to 8), core.calls.drop(before))
            transition(vm, core)
            val denied = core.calls.toList()
            assertEquals(BattleUiSnapshot(), vm.readBattleUiSnapshot())
            assertEquals(denied, core.calls)
            assertCleared(vm)
            bindSyntheticRom(core, identityA)
            vm.setRomSession(RomCompatibility.verified(hnsProfile, shaA), identityA)
            repeat(2) { vm.pollTick() }
            assertTrue(vm.readBattleUiSnapshot()!!.inputSafe)
        }
    }

    @Test fun directBattleReaderNewGenerationClearsOldUiBeforeReadingAgain() {
        val core = ObservationCore()
        val vm = observedSession(core)
        val generation = core.stateGeneration
        bindSyntheticRom(core, identityA)
        assertTrue(core.stateGeneration > generation)
        core.beforeBattle = { assertCleared(vm) }
        val snapshot = vm.readBattleUiSnapshot()!!
        assertEquals(BattleUiState.MOVE_MENU, snapshot.state)
        assertFalse(snapshot.inputSafe) // Battle presence needs fresh observations.
        assertCleared(vm) // The direct reader returns a value; it does not republish cached UI.
        core.beforeBattle = {}
        repeat(2) { vm.pollTick() }
        assertTrue(vm.readBattleUiSnapshot()!!.inputSafe)
    }

    @Test fun directBattleReaderTimeoutDoesNotReadOrPublishAndRecovers() {
        val core = ObservationCore()
        val vm = observedSession(core)
        val before = core.calls.toList()
        core.beforeExclusive = { throw LibretroCoreCoordinator.LockTimeoutException("synthetic") }
        assertNull(vm.readBattleUiSnapshot())
        assertEquals(before, core.calls)
        assertObserved(vm)
        core.beforeExclusive = {}
        assertTrue(vm.readBattleUiSnapshot()!!.inputSafe)
    }

    @Test fun trustRevokedWhileReaderWaitsForCoreDiscardsCapturedSession() {
        for (directBattleRead in listOf(false, true)) {
            val core = ObservationCore()
            val vm = observedSession(core)
            val before = core.calls.toList()
            val waiting = CountDownLatch(1)
            val resume = CountDownLatch(1)
            core.beforeExclusive = {
                waiting.countDown()
                check(resume.await(5, TimeUnit.SECONDS))
            }
            val failure = AtomicReference<Throwable?>()
            val reader = Thread {
                try {
                    if (directBattleRead) assertEquals(BattleUiSnapshot(), vm.readBattleUiSnapshot())
                    else vm.pollTick()
                } catch (t: Throwable) { failure.set(t) }
            }
            try {
                reader.start()
                assertTrue(waiting.await(5, TimeUnit.SECONDS))
                vm.setRomIdentity(identityA)
                assertCleared(vm)
            } finally {
                resume.countDown()
                reader.join(5000)
            }
            assertFalse(reader.isAlive)
            assertNull(failure.get())
            assertEquals(before, core.calls)
            assertCleared(vm)
        }
    }

    @Test fun activePollSerializesReloadStateSramAndResetWithoutDeadlock() {
        val transitions = listOf<(ObservationCore) -> Unit>(
            { bindSyntheticRom(it, identityA) }, { it.loadState("synthetic.state") },
            { it.loadSaveRam("synthetic.sav") }, { it.resetCore() }
        )
        for (transition in transitions) {
            val core = ObservationCore()
            val vm = observedSession(core)
            val generation = core.stateGeneration
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val mutationStarted = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            core.beforeParty = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                assertEquals(generation, core.stateGeneration)
            }
            val poll = Thread {
                try { vm.pollTick() } catch (t: Throwable) { failure.set(t) }
            }
            val mutation = Thread {
                try {
                    mutationStarted.countDown()
                    transition(core)
                } catch (t: Throwable) { failure.set(t) }
            }
            try {
                poll.start()
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                mutation.start()
                assertTrue(mutationStarted.await(5, TimeUnit.SECONDS))
            } finally {
                release.countDown()
                poll.join(5000)
                mutation.join(5000)
            }
            assertFalse(poll.isAlive || mutation.isAlive)
            assertNull(failure.get())
            assertTrue(core.stateGeneration > generation)
            core.beforeParty = {}
            core.valid = false
            vm.pollTick()
            assertTrue(vm.playerParty.value.isEmpty())
            assertNull(vm.playerLocation.value)
        }
    }

}
