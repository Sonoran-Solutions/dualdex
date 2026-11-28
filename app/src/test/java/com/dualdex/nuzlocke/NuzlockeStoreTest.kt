package com.dualdex.nuzlocke

import com.dualdex.battle.ActiveEnemyResolution
import com.dualdex.battle.ActiveEnemyState
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.LibretroCoreBridge
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.bindSyntheticRom
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class NuzlockeStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)

    @Test
    fun strictPerRomSerializedOrderingPreventsOlderRevisionFromOverwritingNewerRevision() {
        val dir = tempFolder.newFolder("ordered_queue")
        val firstWriteEntered = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val writeCount = AtomicInteger(0)

        val store = NuzlockeStore(dir, object : NuzlockeStore.FileOps {
            override fun forceFile(channel: FileChannel) {
                if (writeCount.incrementAndGet() == 1) {
                    firstWriteEntered.countDown()
                    assertTrue(releaseFirstWrite.await(5, TimeUnit.SECONDS))
                }
                NuzlockeStore.DefaultFileOps.forceFile(channel)
            }

            override fun atomicMove(source: Path, target: Path) {
                NuzlockeStore.DefaultFileOps.atomicMove(source, target)
            }

            override fun forceDir(dirPath: Path) {
                NuzlockeStore.DefaultFileOps.forceDir(dirPath)
            }
        })

        val queue = NuzlockeWriteQueue()
        val rev1 = NuzlockeRunState(shaA, enabled = true, levelCap = 12)
        val rev2 = NuzlockeRunState(shaA, enabled = true, levelCap = 25)
        val staleRev1 = NuzlockeRunState(shaA, enabled = true, levelCap = 10)

        assertTrue(queue.enqueue(store, shaA, revision = 1L, state = rev1))
        assertTrue(firstWriteEntered.await(5, TimeUnit.SECONDS))

        // While rev 1 is still writing, enqueue rev 2; stale rev 1/0 must be rejected.
        assertTrue(queue.enqueue(store, shaA, revision = 2L, state = rev2))
        assertFalse(queue.enqueue(store, shaA, revision = 1L, state = staleRev1))
        assertFalse(queue.enqueue(store, shaA, revision = 2L, state = staleRev1))

        releaseFirstWrite.countDown()
        assertTrue(queue.awaitIdle(5_000L))
        assertEquals(25, store.load(shaA).levelCap)
    }

    @Test
    fun romSwitchFlushesAndPreservesPendingSaveForPreviousRom() {
        val dir = tempFolder.newFolder("switch_flush")
        val firstWriteEntered = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val gated = AtomicBoolean(true)

        val store = NuzlockeStore(dir, object : NuzlockeStore.FileOps {
            override fun forceFile(channel: FileChannel) {
                if (gated.compareAndSet(true, false)) {
                    firstWriteEntered.countDown()
                    assertTrue(releaseFirstWrite.await(5, TimeUnit.SECONDS))
                }
                NuzlockeStore.DefaultFileOps.forceFile(channel)
            }

            override fun atomicMove(source: Path, target: Path) {
                NuzlockeStore.DefaultFileOps.atomicMove(source, target)
            }

            override fun forceDir(dirPath: Path) {
                NuzlockeStore.DefaultFileOps.forceDir(dirPath)
            }
        })

        val hnsSha = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"
        val profileA = RomHackProfile(
            id = "heart_and_soul", name = "Pokemon Heart & Soul", baseGame = "Emerald", gameId = 8,
            sha256Hashes = listOf(hnsSha), isVerified = true, memoryLayoutVerified = true,
            battleStateReadVerified = true
        )
        val profileB = profileA.copy(id = "synth-b", name = "Synth B", gameId = 1, sha256Hashes = listOf(shaB))
        val idA = RomIdentity(hnsSha, "Pokemon Heart & Soul")
        val idB = RomIdentity(shaB, "Synth B")

        val party = arrayOf(testMon(species = 25, pid = 10L))
        val enemy = arrayOf(testMon(species = 263, pid = 20L, level = 3))
        val location = PlayerLocation(0, 16, 0, 5, 5, 5, 5, 0, 0, isIndoors = false, isValid = true)

        val core = object : LibretroCoreCoordinator(object : LibretroCoreBridge {
            override fun loadRom(romPath: String) = true
            override fun loadState(statePath: String) = true
            override fun loadSaveRam(savePath: String) = true
        }) {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? = party
            override fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? = enemy
            override fun readBattlePresence(gameId: Int): Int = 1
            override fun getActiveBattlerSlot(gameId: Int): Int = 0
            override fun resolveActiveEnemy(gameId: Int): ActiveEnemyResolution =
                ActiveEnemyResolution(ActiveEnemyState.SLOT, partySlot = 0, opponentBattlers = 1)
            override fun readPlayerLocation(gameId: Int): PlayerLocation? = location
            override fun readGbaMemory(address: Int, length: Int): ByteArray? = when (address) {
                0x020000AC -> byteArrayOf(0x04, 0x00, 0x00, 0x00)
                0x020000B0 -> byteArrayOf(0x02)
                else -> null
            }
        }

        val vm = CompanionViewModel(coreCoordinator = core, nuzlockeStore = store)
        bindSyntheticRom(core, idA)
        vm.setRomSession(RomCompatibility.verified(profileA, hnsSha), idA)

        // Enable Nuzlocke (write #1 blocks on latch), then record an encounter via pollTick (write #2 queued).
        vm.setNuzlockeEnabled(true)
        assertTrue(firstWriteEntered.await(5, TimeUnit.SECONDS))
        repeat(2) { vm.pollTick() }
        assertEquals(EncounterOutcome.MET, vm.nuzlockeState.value!!.encounters["0:16"]?.outcome)

        // Immediately switch to ROM B while ROM A's writes are still pending/in-flight!
        bindSyntheticRom(core, idB)
        vm.setRomSession(RomCompatibility.verified(profileB, shaB), idB)
        assertFalse(vm.nuzlockeState.value!!.enabled)

        releaseFirstWrite.countDown()
        assertTrue(vm.awaitNuzlockePersistence(5_000L))

        // ROM A's encounter & enabled state must have flushed completely to disk without being dropped.
        val persistedA = store.loadOrNull(hnsSha)
        assertNotNull(persistedA)
        assertTrue(persistedA!!.enabled)
        assertEquals(EncounterOutcome.MET, persistedA.encounters["0:16"]?.outcome)
    }

    @Test
    fun failedWritePreservesLastGoodFileOnDiskAndReportsFailure() {
        val dir = tempFolder.newFolder("atomic_failure")
        val failFsync = AtomicBoolean(false)
        val failMove = AtomicBoolean(false)

        val store = NuzlockeStore(dir, object : NuzlockeStore.FileOps {
            override fun forceFile(channel: FileChannel) {
                if (failFsync.get()) throw IOException("simulated fsync failure")
                NuzlockeStore.DefaultFileOps.forceFile(channel)
            }

            override fun atomicMove(source: Path, target: Path) {
                if (failMove.get()) throw IOException("simulated atomic move failure")
                NuzlockeStore.DefaultFileOps.atomicMove(source, target)
            }

            override fun forceDir(dirPath: Path) {
                NuzlockeStore.DefaultFileOps.forceDir(dirPath)
            }
        })

        val goodState = NuzlockeRunState(
            romSha256 = shaA,
            enabled = true,
            levelCap = 18,
            encounters = linkedMapOf(
                "0:16" to AreaEncounter("0:16", "Route 101", 263, "Zigzagoon", 3, EncounterOutcome.CAUGHT, 1000L)
            )
        )
        assertTrue(store.saveResult(shaA, goodState).isSuccess)
        val targetFile = store.fileForRom(shaA)!!
        val goodBytes = targetFile.readBytes()

        // 1. fsync failure must report failure, clean .tmp, and preserve last good file.
        val nextState = goodState.copy(levelCap = 42)
        failFsync.set(true)
        val fsyncFailure = store.saveResult(shaA, nextState)
        assertTrue(fsyncFailure.isFailure)
        assertFalse(store.save(shaA, nextState))
        assertFalse(File(dir, "${targetFile.name}.tmp").exists())
        assertArrayEquals(goodBytes, targetFile.readBytes())
        assertEquals(goodState, store.load(shaA))

        // 2. atomic move failure must NOT fall back to non-atomic move; preserves last good file.
        failFsync.set(false)
        failMove.set(true)
        val moveFailure = store.saveResult(shaA, nextState)
        assertTrue(moveFailure.isFailure)
        assertFalse(File(dir, "${targetFile.name}.tmp").exists())
        assertArrayEquals(goodBytes, targetFile.readBytes())
        assertEquals(goodState, store.load(shaA))

        // 3. CompanionViewModel surfaces persistence errors and clears on next successful write or ROM switch.
        val profileA = RomHackProfile(
            id = "synth-a", name = "Synth A", baseGame = "Emerald", gameId = 1,
            sha256Hashes = listOf(shaA), isVerified = true, memoryLayoutVerified = true
        )
        val idA = RomIdentity(shaA, "Synth A")
        val vm = CompanionViewModel(nuzlockeStore = store)
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), idA)

        vm.setNuzlockeLevelCap(50)
        assertTrue(vm.awaitNuzlockePersistence(5_000L))
        assertNotNull(vm.nuzlockePersistenceError.value)
        assertArrayEquals(goodBytes, targetFile.readBytes())

        // Clearing failure: next successful save clears error.
        failMove.set(false)
        vm.setNuzlockeLevelCap(55)
        assertTrue(vm.awaitNuzlockePersistence(5_000L))
        assertNull(vm.nuzlockePersistenceError.value)
        assertEquals(55, store.load(shaA).levelCap)

        // Also cleared on ROM switch.
        failMove.set(true)
        vm.setNuzlockeLevelCap(60)
        assertTrue(vm.awaitNuzlockePersistence(5_000L))
        assertNotNull(vm.nuzlockePersistenceError.value)
        vm.setRomIdentity(RomIdentity(shaB, "Synth B"))
        assertNull(vm.nuzlockePersistenceError.value)
    }

    @Test
    fun loadRejectsMismatchedEmbeddedShaAndSaveRefusesCrossRomWrite() {
        val dir = tempFolder.newFolder("cross_rom_isolation")
        val store = NuzlockeStore(dir)

        val stateB = NuzlockeRunState(
            romSha256 = shaB,
            enabled = true,
            dupesClause = false,
            levelCap = 35,
            encounters = linkedMapOf(
                "0:17" to AreaEncounter("0:17", "Route 102", 265, "Wurmple", 4, EncounterOutcome.CAUGHT, 2222L)
            )
        )
        assertTrue(store.save(shaB, stateB))
        val fileB = store.fileForRom(shaB)!!
        val originalBBytes = fileB.readBytes()

        // Tamper ROM A's file so its embedded romSha256 points to ROM B.
        val fileA = store.fileForRom(shaA)!!
        fileA.writeText(store.encode(stateB))

        // load(shaA) must reject the mismatched file and return clean default for ROM A.
        assertNull(store.loadOrNull(shaA))
        assertEquals(NuzlockeRunState(romSha256 = shaA), store.load(shaA))

        // save(shaA, stateWithShaB) must refuse without touching ROM B's file or writing ROM A.
        val tamperedBeforeSave = fileA.readBytes()
        val saveAttempt = store.saveResult(shaA, stateB)
        assertTrue(saveAttempt.isFailure)
        assertFalse(store.save(shaA, stateB))
        assertArrayEquals(originalBBytes, fileB.readBytes())
        assertArrayEquals(tamperedBeforeSave, fileA.readBytes())

        // Invalid / non-canonical SHA keys also fail closed.
        assertNull(store.fileForRom("../evil"))
        assertNull(store.loadOrNull("not-a-sha"))
        assertFalse(store.save("not-a-sha", stateB))
    }

    private fun testMon(species: Int, pid: Long, level: Int = 5) = ParsedPokemon(
        isValid = true, isEmpty = false, pid = pid, tid = 123, sid = 456,
        nickname = "", otName = "Red", species = species, heldItem = 0,
        level = level, nature = 0, natureName = "Hardy", isShiny = false, abilitySlot = 0,
        isEgg = false, friendship = 70, experience = 100L,
        hpIv = 10, attackIv = 10, defenseIv = 10, speedIv = 10, spAttackIv = 10, spDefenseIv = 10,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(33, 0, 0, 0), pp = intArrayOf(35, 0, 0, 0),
        currentHp = 20, maxHp = 20, attack = 10, defense = 10, speed = 10, spAttack = 10, spDefense = 10,
        statusCondition = 0L
    )
}
