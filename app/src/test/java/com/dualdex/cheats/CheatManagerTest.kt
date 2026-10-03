package com.dualdex.cheats

import com.dualdex.emulator.*
import com.dualdex.romhack.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** No real ROM/code compatibility evidence: positive approvals and ROM bytes are synthetic. */
class CheatManagerTest {
    private val a = RomIdentity.create("1".repeat(64), "Heart Soul Emerald Fire Leaf")
    private val b = RomIdentity.create("2".repeat(64), a.displayName)
    private val custom = CheatItem(id = "custom", name = "User code", code = "82025840 0044", enabled = true)
    private val preset = custom.copy(id = "synthetic-only", isPreset = true)

    private class Bridge : LibretroCoreBridge {
        val sets = mutableListOf<String>()
        val setTargets = mutableListOf<String?>()
        var loadedPath: String? = null
        override fun loadRom(romPath: String): Boolean { loadedPath = romPath; return true }
        override fun unloadRom(): Boolean { loadedPath = null; return true }
        var resets = 0
        var onReset: (() -> Unit)? = null
        override fun flushSaveRam(savePath: String): Boolean {
            File(savePath).writeBytes(ByteArray(getSaveRamSize().toInt()))
            return true
        }
        override fun saveState(statePath: String): Boolean {
            File(statePath).writeBytes(ByteArray(getSaveStateSize().toInt()))
            return true
        }
        override fun cheatReset() { resets++; onReset?.invoke() }
        override fun cheatSet(index: Int, enabled: Boolean, code: String) {
            sets.add(code)
            setTargets.add(loadedPath)
        }
    }
    private fun bind(core: LibretroCoreCoordinator, identity: RomIdentity) = core.executeExclusive {
        core.loadRom(identity.sha256)
        core.bindCheatRom(identity)
    }

    @Test fun productionAndNameOnlyPathsNeverOfferPresets() {
        val storage = mutableMapOf<String, String>()
        val manager = CheatManager(memoryStorage = storage)
        val hns = RomIdentity.create("edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b", "Heart & Soul 2.0.5")
        for (identity in listOf(hns, b, a, RomIdentity.create("", "Emerald"))) {
            assertTrue(manager.getCheats(identity).isEmpty())
            assertTrue(manager.getPresets(identity).isEmpty())
        }
        for (name in listOf("heart", "soul", "emerald", "fire", "leaf", "unknown", "R.O.W.E.", "Unbound")) {
            assertTrue(manager.getPresetsForGame(name).isEmpty())
            assertTrue(manager.resetToDefaultPresets(name).isEmpty())
        }
        val liveVerified = RuntimeRomTrust(
            matchMethod = ProfileMatchMethod.EXACT_SHA256, detectedSha256 = hns.sha256,
            activeRomSha256 = hns.sha256, profileVerified = true, memoryLayoutVerified = true,
            profileSha256Hashes = listOf(hns.sha256)
        )
        assertTrue(liveVerified.mayReadLiveMemory)
        assertTrue(manager.getPresets(hns).isEmpty())
        assertTrue(storage.isEmpty())
    }

    @Test fun syntheticApprovalRequiresHashIdAndExactPayloadAtManagerBoundary() {
        val bridge = Bridge()
        val core = LibretroCoreCoordinator(bridge)
        bind(core, a)
        val manager = CheatManager(memoryStorage = mutableMapOf(), coreCoordinator = core,
            presetPolicy = CheatPresetPolicy(mapOf(a.sha256 to listOf(preset))))
        assertTrue(manager.loadPresets(a).accepted)
        assertTrue(manager.toggleCheat(a, preset.id, true).accepted)
        assertEquals(listOf(preset.code), bridge.sets)
        bridge.sets.clear()
        assertFalse(manager.updateCheat(a, preset.copy(code = "82025840 0001")).accepted)
        assertFalse(manager.toggleCheat(a, preset.id, true).accepted)
        assertTrue(bridge.sets.isEmpty())
        assertFalse(manager.addCheat(a, preset.copy(id = "forged")).accepted)
        bind(core, b)
        assertFalse(manager.addCheat(b, preset).accepted)
        assertTrue(bridge.sets.isEmpty())
    }

    @Test fun legacyTransitionIsIdempotentPreservesTextAndPreventsLaundering() {
        val storage = mutableMapOf("cheats_${a.sha256}" to """[
          {"id":"legacy","name":"Old preset","code":"82025840 0044","enabled":true,"isPreset":true},
          {"id":"custom","name":"User code","code":"82025840 0044","enabled":true,"isPreset":false},
          {"id":"ambiguous","code":"82025840 0044","enabled":true}
        ]""")
        val bridge = Bridge()
        val core = LibretroCoreCoordinator(bridge)
        bind(core, a)
        val manager = CheatManager(memoryStorage = storage, coreCoordinator = core)
        val first = manager.getCheats(a)
        val serialized = storage.toMap()
        assertEquals(first, manager.getCheats(a))
        assertEquals(serialized, storage)
        assertFalse(first[0].enabled)
        assertEquals(CheatManager.UNVERIFIED, first[0].disabledReason)
        assertEquals(CheatManager.UNKNOWN_SOURCE, first[2].disabledReason)
        assertTrue(first[1].enabled)
        assertEquals(custom.code, first[1].code)
        manager.applyCheats(a)
        assertEquals(listOf(custom.code), bridge.sets)
        bridge.sets.clear()
        assertFalse(manager.updateCheat(a, first[0].copy(isPreset = false, enabled = true)).accepted)
        assertTrue(manager.getCheats(a)[0].isPreset)
        assertEquals(listOf(custom.code), bridge.sets)
        assertTrue(manager.getCheats(b).isEmpty())
        assertEquals(CheatResult.unavailable, manager.loadPresets(a))
        assertEquals(first, manager.getCheats(a))
    }

    @Test fun ambiguousProvenanceCannotBecomeSyntheticApprovalAndMalformedStoreIsRetained() {
        val key = "cheats_${a.sha256}"
        val raw = """[{"id":"synthetic-only","name":"Ambiguous","code":"82025840 0044","enabled":true,"isPreset":"false"}]"""
        val storage = mutableMapOf(key to raw)
        val bridge = Bridge()
        val core = LibretroCoreCoordinator(bridge)
        bind(core, a)
        val manager = CheatManager(memoryStorage = storage, coreCoordinator = core,
            presetPolicy = CheatPresetPolicy(mapOf(a.sha256 to listOf(preset))))
        assertFalse(manager.getCheats(a).single().enabled)
        assertEquals(CheatManager.UNKNOWN_SOURCE, manager.getCheats(a).single().disabledReason)
        manager.applyCheats(a)
        assertTrue(bridge.sets.isEmpty())
        storage[key] = "[{broken but retained code text"
        assertFalse(manager.addCheat(a, custom).accepted)
        assertEquals("[{broken but retained code text", storage[key])
        assertFalse(manager.saveCheats(a, emptyList()).accepted)
    }

    @Test fun staleActionsCannotResetOrSetOtherRom() {
        val bridge = Bridge()
        val core = LibretroCoreCoordinator(bridge)
        val manager = CheatManager(memoryStorage = mutableMapOf(), coreCoordinator = core)
        bind(core, a)
        assertTrue(manager.addCheat(a, custom).accepted)
        bind(core, b)
        bridge.sets.clear()
        val resets = bridge.resets
        assertFalse(manager.toggleCheat(a, custom.id, true).accepted)
        assertEquals(CheatResult.stale, manager.applyCheats(a))
        assertEquals(CheatResult.stale, manager.loadPresets(a))
        assertFalse(manager.addCheat(a, custom.copy(id = "stale-dialog")).accepted)
        assertEquals(resets, bridge.resets)
        assertTrue(bridge.sets.isEmpty())
        core.unloadRom()
        assertFalse(manager.applyCheats(b).accepted)
    }

    @Test fun multilineCustomGrammarAndRejectionResults() {
        val bridge = Bridge()
        val core = LibretroCoreCoordinator(bridge)
        bind(core, a)
        val manager = CheatManager(memoryStorage = mutableMapOf(), coreCoordinator = core)
        val multiline = "# comment\n82025840\t0044\n// comment\nD8BAE4D9+4864DCE5+B3C94DA9 C04D368C"
        assertTrue(manager.addCheat(a, custom.copy(code = multiline)).accepted)
        assertEquals("82025840 0044\nD8BAE4D9 4864DCE5\nB3C94DA9 C04D368C", bridge.sets.single())
        for (bad in listOf("", "# comment", "XXXXXXXX XXXXXXXX", "82025840 00GG", "82025840", "82025840 0044 garbage")) {
            bridge.sets.clear()
            val result = manager.updateCheat(a, custom.copy(code = bad))
            assertFalse(result.accepted)
            assertEquals(CheatManager.MALFORMED, result.message)
            assertFalse(manager.getCheats(a).single().enabled)
            assertTrue(bridge.sets.isEmpty())
        }
    }

    @Test fun automaticLoadingUsesNewHashBeforeViewModelPublication() = runBlocking {
        val dir = Files.createTempDirectory("cheat-session-test").toFile()
        try {
            val fileA = File(dir, "synthetic-a.bin").apply { writeBytes(ByteArray(1024) { 17 }) }
            val fileB = File(dir, "synthetic-b.bin").apply { writeBytes(ByteArray(1024) { 34 }) }
            val identityA = RomIdentity.fromFile(fileA, "A")
            val identityB = RomIdentity.fromFile(fileB, "B")
            val bridge = Bridge()
            val core = LibretroCoreCoordinator(bridge)
            val manager = CheatManager(memoryStorage = mutableMapOf(), coreCoordinator = core)
            manager.saveCheats(identityA, listOf(preset))
            manager.saveCheats(identityB, listOf(custom))
            val saves = SaveStateManager(customBaseDir = File(dir, "saves"), coreBridge = bridge,
                customCoordinator = core)
            val sessions = RomSessionManager(saveStateManager = saves, cheatManager = manager,
                customCoordinator = core, coreBridge = bridge, customRomCacheDir = File(dir, "cache"))
            assertTrue(sessions.switchRomFile(fileA, emptyList(), "A") is SwitchResult.Success)
            assertTrue(bridge.sets.isEmpty())
            assertFalse(manager.getCheats(identityA).single().enabled)
            assertTrue(sessions.switchRomFile(fileB, emptyList(), "B") is SwitchResult.Success)
            assertEquals(listOf(custom.code), bridge.sets)
            bridge.sets.clear()
            assertFalse(manager.applyCheats(identityA).accepted)
            assertTrue(sessions.switchRomFile(fileA, emptyList(), "A") is SwitchResult.Success)
            assertTrue(bridge.sets.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    /** Pause the actual whole-list migration write, rather than sleeping around a race.
     * Two different coordinators force this to exercise the shared store lock as well as
     * the production screen/session case where both managers share one coordinator. */
    private class PausedMigrationStore(
        private val backing: MutableMap<String, String> = java.util.concurrent.ConcurrentHashMap()
    ) : MutableMap<String, String> by backing {
        val beforeMigrationWrite = CountDownLatch(1)
        val resumeMigrationWrite = CountDownLatch(1)
        @Volatile var migrationThread: Thread? = null
        private val pauseOnce = java.util.concurrent.atomic.AtomicBoolean(true)

        override fun put(key: String, value: String): String? {
            if (Thread.currentThread() === migrationThread && pauseOnce.compareAndSet(true, false)) {
                beforeMigrationWrite.countDown()
                check(resumeMigrationWrite.await(5, TimeUnit.SECONDS)) { "migration release timed out" }
            }
            return backing.put(key, value)
        }

    }

    private fun quarantineAgainstEdit(
        sharedCoordinator: Boolean = false,
        edit: (CheatManager) -> CheatResult,
        verify: (List<CheatItem>, Bridge) -> Unit
    ) {
        val store = PausedMigrationStore()
        store["cheats_${a.sha256}"] = """[
          {"id":"legacy","name":"Old preset","code":"82025840 0001","enabled":true,"isPreset":true},
          {"id":"custom","name":"User code","code":"82025840 0044","enabled":true,"isPreset":false}
        ]"""
        val reloadBridge = Bridge()
        val reloadCore = LibretroCoreCoordinator(reloadBridge)
        val editBridge = if (sharedCoordinator) reloadBridge else Bridge()
        val editCore = if (sharedCoordinator) reloadCore else LibretroCoreCoordinator(editBridge)
        bind(reloadCore, a)
        if (!sharedCoordinator) bind(editCore, a)
        val reloadManager = CheatManager(memoryStorage = store, coreCoordinator = reloadCore)
        val editManager = CheatManager(memoryStorage = store, coreCoordinator = editCore)
        val reloadResult = java.util.concurrent.atomic.AtomicReference<CheatResult>()
        val editResult = java.util.concurrent.atomic.AtomicReference<CheatResult>()
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val edited = CountDownLatch(1)
        val reload = Thread {
            try { reloadResult.set(reloadManager.applyCheats(a)) }
            catch (t: Throwable) { failures.add(t) }
        }
        val editor = Thread {
            try { editResult.set(edit(editManager)) }
            catch (t: Throwable) { failures.add(t) }
            finally { edited.countDown() }
        }
        store.migrationThread = reload
        try {
            reload.start()
            assertTrue("migration never reached its write", store.beforeMigrationWrite.await(5, TimeUnit.SECONDS))
            editor.start()
            // On the reviewed buggy head, a separate-core edit completes while the old
            // migration is paused. With the fix it blocks on the shared storage monitor.
            // With one core it queues on that core. Release only once one of those states
            // is observed, so final-state assertions deterministically expose stale writes.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (edited.count != 0L &&
                !(editor.state == Thread.State.BLOCKED && editCore.lock.isLocked) &&
                !editCore.lock.hasQueuedThread(editor) && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertTrue("edit neither completed nor queued at the transaction boundary",
                edited.count == 0L || editor.state == Thread.State.BLOCKED || editCore.lock.hasQueuedThread(editor))
        } finally {
            store.resumeMigrationWrite.countDown()
            reload.join(5000)
            if (editor.state != Thread.State.NEW) editor.join(5000)
        }
        assertFalse("reload deadlocked", reload.isAlive)
        assertFalse("edit deadlocked", editor.isAlive)
        assertTrue("worker failures: $failures", failures.isEmpty())
        assertTrue(reloadResult.get().accepted)
        assertTrue(editResult.get().accepted)
        assertFalse(reloadBridge.sets.contains("82025840 0001"))
        assertFalse(editBridge.sets.contains("82025840 0001"))
        // Ignore any dispatch that preceded the completed edit. Every later application
        // must observe that edit, including when called through the other manager.
        reloadBridge.sets.clear()
        editBridge.sets.clear()
        assertTrue(reloadManager.applyCheats(a).accepted)
        verify(reloadManager.getCheats(a), reloadBridge)
        assertTrue(editManager.applyCheats(a).accepted)
        verify(editManager.getCheats(a), editBridge)
    }

    @Test fun quarantineCannotUndoCompletedDisableAcrossManagers() = quarantineAgainstEdit(
        edit = { it.toggleCheat(a, custom.id, false) },
        verify = { entries, bridge ->
            assertFalse(entries.first { it.id == custom.id }.enabled)
            assertTrue(bridge.sets.isEmpty())
        }
    )

    @Test fun quarantineCannotUndoDisableWithSharedCore() = quarantineAgainstEdit(
        sharedCoordinator = true,
        edit = { it.toggleCheat(a, custom.id, false) },
        verify = { entries, bridge ->
            assertFalse(entries.first { it.id == custom.id }.enabled)
            assertTrue(bridge.sets.isEmpty())
        }
    )

    @Test fun quarantineCannotUndoDisableAll() = quarantineAgainstEdit(
        edit = { it.disableAllCheats(a) },
        verify = { entries, bridge ->
            assertTrue(entries.none { it.enabled })
            assertTrue(bridge.sets.isEmpty())
        }
    )

    @Test fun quarantineCannotResurrectDeletedEntry() = quarantineAgainstEdit(
        edit = { it.deleteCheat(a, custom.id) },
        verify = { entries, bridge ->
            assertTrue(entries.none { it.id == custom.id })
            assertTrue(bridge.sets.isEmpty())
        }
    )

    @Test fun quarantineCannotLoseAddedEntry() = quarantineAgainstEdit(
        edit = { it.addCheat(a, custom.copy(id = "added", code = "82025840 0045")) },
        verify = { entries, bridge ->
            assertEquals("82025840 0045", entries.first { it.id == "added" }.code)
            assertTrue(bridge.sets.contains("82025840 0045"))
        }
    )

    @Test fun quarantineCannotUndoUpdatedEntry() = quarantineAgainstEdit(
        edit = { it.updateCheat(a, custom.copy(name = "Edited", code = "82025840 0046")) },
        verify = { entries, bridge ->
            assertEquals("Edited", entries.first { it.id == custom.id }.name)
            assertEquals("82025840 0046", entries.first { it.id == custom.id }.code)
            assertEquals(listOf("82025840 0046"), bridge.sets)
        }
    )

    @Test fun resetAndSetCannotInterleaveWithRomSwitch() {
        val bridge = Bridge()
        val core = LibretroCoreCoordinator(bridge)
        bind(core, a)
        val manager = CheatManager(memoryStorage = mutableMapOf(), coreCoordinator = core)
        manager.saveCheats(a, listOf(custom))
        val resetting = CountDownLatch(1)
        val switchAttempted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val switched = CountDownLatch(1)
        bridge.onReset = {
            resetting.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
        }
        val applied = java.util.concurrent.atomic.AtomicReference<CheatResult>()
        val apply = Thread { applied.set(manager.applyCheats(a)) }
        val switch = Thread {
            switchAttempted.countDown()
            bind(core, b)
            switched.countDown()
        }
        apply.start()
        assertTrue(resetting.await(5, TimeUnit.SECONDS))
        switch.start()
        assertTrue(switchAttempted.await(5, TimeUnit.SECONDS))
        assertFalse(switched.await(100, TimeUnit.MILLISECONDS))
        release.countDown()
        apply.join(5000); switch.join(5000)
        assertFalse(apply.isAlive); assertFalse(switch.isAlive)
        assertTrue(applied.get().accepted)
        assertEquals(0L, switched.count)
        assertEquals(listOf(a.sha256), bridge.setTargets)
        assertEquals(listOf(custom.code), bridge.sets)
        assertFalse(manager.applyCheats(a).accepted)
    }
}
