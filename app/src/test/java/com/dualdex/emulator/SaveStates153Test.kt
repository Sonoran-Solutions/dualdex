package com.dualdex.emulator

import com.dualdex.emulator.RomSaveIntegrityTest.Companion.HASH_A
import com.dualdex.emulator.RomSaveIntegrityTest.Companion.STATE_SIZE_256K
import com.dualdex.emulator.storage.SaveShareStore
import com.dualdex.emulator.storage.SaveShareStore.ExportAction
import com.dualdex.emulator.storage.SaveStateFiles
import com.dualdex.emulator.storage.AtomicSaveFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

/** #153: resume selection, 10 slots + .bak + undo, backup naming, thumbnail alpha, share rules. */
class SaveStates153Test {
    private lateinit var dir: File
    private lateinit var bridge: RomSaveIntegrityTest.TestCoreBridge
    private lateinit var ssm: SaveStateManager
    private val rom = RomIdentity.create(HASH_A, "FireRed")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dualdex_153_").toFile()
        bridge = RomSaveIntegrityTest.TestCoreBridge()
        ssm = SaveStateManager(customBaseDir = dir, coreBridge = bridge)
        ssm.setActiveGame(rom)
    }

    @After
    fun tearDown() { AtomicSaveFile.syncHook = null; AtomicSaveFile.directorySyncHook = null; dir.deleteRecursively() }

    private fun state(name: String, mtime: Long) = ssm.getCanonicalFile(rom, name).apply {
        writeBytes(ByteArray(STATE_SIZE_256K) { 1 }); setLastModified(mtime)
    }

    @Test
    fun newestResume_picksNewestOfResumeAndManualSlots_respectingBarrier() {
        state(SaveStateFiles.AUTO_RESUME, 1_000_000L)
        state(SaveStateFiles.slotState(10), 3_000_000L)
        state(SaveStateFiles.QUICK_SAVE, 2_000_000L)
        val romDir = ssm.getCanonicalRomDir(rom)
        assertEquals("slot_10.state", SaveStateFiles.newestResumeState(romDir)?.name)
        assertNull(SaveStateFiles.newestResumeState(romDir, barrierMs = 3_000_000L))
        assertEquals("slot_10.state", ssm.loadNewestResumeState(rom))
    }

    @Test
    fun clearAutoResume_deletesResumeAndBlocksOlderStates() {
        state(SaveStateFiles.AUTO_RESUME, System.currentTimeMillis() - 60_000)
        state(SaveStateFiles.slotState(1), System.currentTimeMillis() - 60_000)
        assertTrue(ssm.clearAutoResume(rom))
        assertFalse(ssm.getCanonicalFile(rom, SaveStateFiles.AUTO_RESUME).exists())
        assertTrue("manual slot stays on disk", ssm.getCanonicalFile(rom, "slot_1.state").exists())
        assertNull(ssm.loadNewestResumeState(rom))
        assertTrue(ssm.saveSlot(rom, 2)) // newer than the barrier
        ssm.getCanonicalFile(rom, "slot_2.state").setLastModified(System.currentTimeMillis() + 60_000)
        assertEquals("slot_2.state", ssm.loadNewestResumeState(rom))
    }

    @Test
    fun futureRestoreBarrierAllowsNewLiveResumeAndManualStatesAfterRestart() {
        val future = System.currentTimeMillis() + 7 * 86400000L
        val old = state(SaveStateFiles.slotState(1), future)
        val oldBytes = old.readBytes()
        val oldResume = state(SaveStateFiles.AUTO_RESUME, future)
        val barrier = ssm.getCanonicalFile(rom, SaveStateFiles.RESUME_BARRIER).apply {
            writeText(future.toString()); check(setLastModified(future))
        }
        assertNull(ssm.loadNewestResumeState(rom))
        bridge.saveStateResult = false
        assertFalse(ssm.saveAutoResume(rom))
        assertEquals(future, oldResume.lastModified())
        assertNull(ssm.loadNewestResumeState(rom))
        bridge.saveStateResult = true
        assertTrue(ssm.saveAutoResume(rom))
        assertTrue(oldResume.lastModified() > barrier.lastModified())
        assertEquals(SaveStateFiles.AUTO_RESUME, ssm.loadNewestResumeState(rom))
        assertTrue(ssm.saveSlot(rom, 2))
        assertEquals("slot_2.state", ssm.loadNewestResumeState(rom))
        assertEquals(future, old.lastModified())
        assertArrayEquals(oldBytes, old.readBytes())
        assertEquals(future, barrier.lastModified())
        val restarted = SaveStateManager(customBaseDir = dir, coreBridge = bridge)
        restarted.setActiveGame(rom)
        assertEquals("slot_2.state", restarted.loadNewestResumeState(rom))
    }

    @Test
    fun failedBarrierPublicationPreservesFutureMarkerAndAutoResumeBytes() {
        val future = System.currentTimeMillis() + 7 * 86400000L
        val resume = state(SaveStateFiles.AUTO_RESUME, future)
        val original = resume.readBytes()
        val barrier = ssm.getCanonicalFile(rom, SaveStateFiles.RESUME_BARRIER).apply {
            writeText(future.toString()); check(setLastModified(future))
        }
        AtomicSaveFile.syncHook = { throw java.io.IOException("injected barrier write failure") }
        assertFalse(ssm.clearAutoResume(rom))
        assertEquals(future, barrier.lastModified())
        assertEquals(future.toString(), barrier.readText())
        assertArrayEquals(original, resume.readBytes())
        assertNull(ssm.loadNewestResumeState(rom))
    }

    @Test
    fun manualImportWithoutPriorBarrierAbortsBeforeBatteryCommitOnMarkerWriteFailure() {
        val original = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 3 }
        val candidate = ByteArray(original.size) { 9 }
        val canonical = ssm.getCanonicalFile(rom, SaveStateFiles.BATTERY)
        val barrier = ssm.getCanonicalFile(rom, SaveStateFiles.RESUME_BARRIER)
        val future = System.currentTimeMillis() + 7 * 86400000L
        val resume = state(SaveStateFiles.AUTO_RESUME, future)
        val resumeBytes = resume.readBytes()
        val romDir = ssm.getCanonicalRomDir(rom)
        for (hadBattery in listOf(false, true)) {
            if (hadBattery) canonical.writeBytes(original) else canonical.delete()
            bridge.currentSramBytes = original.copyOf()
            bridge.events.clear()
            assertFalse(barrier.exists())
            assertEquals(SaveStateFiles.AUTO_RESUME, SaveStateFiles.newestResumeState(romDir)?.name)
            var barrierWriteFailed = false
            AtomicSaveFile.syncHook = {
                if (File(romDir, "${SaveStateFiles.RESUME_BARRIER}.tmp").exists()) {
                    barrierWriteFailed = true
                    throw java.io.IOException("injected marker fsync failure")
                }
                it.fd.sync()
            }
            assertFalse(ssm.importBatterySave(rom, candidate.inputStream()))
            assertTrue(barrierWriteFailed)
            assertEquals(hadBattery, canonical.exists())
            if (hadBattery) assertArrayEquals(original, canonical.readBytes())
            assertArrayEquals(original, bridge.currentSramBytes)
            assertArrayEquals(resumeBytes, resume.readBytes())
            assertFalse(barrier.exists())
            assertFalse(bridge.events.contains("resetCore"))
            assertTrue("Guard failure must precede live SRAM mutation", bridge.events.none { it.startsWith("loadSaveRam:") })
        }
    }

    @Test
    fun manualImportAbortsWhenMarkerRenameCannotBeDirectorySynced() {
        val original = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 3 }
        val canonical = ssm.getCanonicalFile(rom, SaveStateFiles.BATTERY).apply { writeBytes(original) }
        val romDir = ssm.getCanonicalRomDir(rom)
        val barrier = File(romDir, SaveStateFiles.RESUME_BARRIER)
        val resume = state(SaveStateFiles.AUTO_RESUME, System.currentTimeMillis() + 7 * 86400000L)
        val resumeBytes = resume.readBytes()
        bridge.currentSramBytes = original.copyOf()
        bridge.events.clear()
        var syncs = 0
        assertFalse(barrier.exists())
        AtomicSaveFile.directorySyncHook = { parent ->
            syncs++
            assertEquals(romDir, parent)
            assertTrue("Marker was renamed before the parent sync", barrier.exists())
            assertFalse(File(romDir, "${SaveStateFiles.RESUME_BARRIER}.tmp").exists())
            throw java.io.IOException("injected directory sync failure")
        }
        assertFalse(ssm.importBatterySave(rom, ByteArray(original.size) { 9 }.inputStream()))
        assertEquals(1, syncs)
        assertArrayEquals(original, canonical.readBytes())
        assertArrayEquals(original, bridge.currentSramBytes)
        assertArrayEquals(resumeBytes, resume.readBytes())
        assertFalse(bridge.events.contains("resetCore"))
        assertTrue("Failed directory sync must precede live SRAM mutation", bridge.events.none { it.startsWith("loadSaveRam:") })
    }

    @Test
    fun manualImportPublishesAndSyncsGuardBeforeReplacingBattery() {
        val original = ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 3 }
        val candidate = ByteArray(original.size) { 9 }
        val canonical = ssm.getCanonicalFile(rom, SaveStateFiles.BATTERY).apply { writeBytes(original) }
        val romDir = ssm.getCanonicalRomDir(rom)
        val barrier = File(romDir, SaveStateFiles.RESUME_BARRIER)
        val future = System.currentTimeMillis() + 7 * 86400000L
        state(SaveStateFiles.AUTO_RESUME, future)
        val oldSlot = state(SaveStateFiles.slotState(1), future)
        bridge.currentSramBytes = original.copyOf()
        bridge.events.clear()
        var syncs = 0
        AtomicSaveFile.directorySyncHook = { parent ->
            syncs++
            assertEquals(romDir, parent)
            assertTrue(barrier.lastModified() > future)
            assertArrayEquals("Battery has not been replaced before guard sync", original, canonical.readBytes())
            assertArrayEquals("Live SRAM has not changed before guard sync", original, bridge.currentSramBytes)
            assertTrue(ssm.coreCoordinator.isExclusiveLocked)
            bridge.events.add("barrierSync")
        }
        assertTrue(ssm.importBatterySave(rom, candidate.inputStream()))
        AtomicSaveFile.directorySyncHook = null
        assertEquals(1, syncs)
        assertArrayEquals(candidate, canonical.readBytes())
        assertArrayEquals(candidate, bridge.currentSramBytes)
        assertTrue(bridge.events.indexOfFirst { it.startsWith("loadSaveRam:") && it.contains("__import_candidate") } > bridge.events.indexOf("barrierSync"))
        assertTrue(bridge.events.indexOf("resetCore") > bridge.events.indexOf("barrierSync"))
        assertFalse(File(romDir, SaveStateFiles.AUTO_RESUME).exists())
        assertEquals(future, oldSlot.lastModified())
        assertNull(ssm.loadNewestResumeState(rom))
        assertTrue(ssm.saveAutoResume(rom))
        assertEquals(SaveStateFiles.AUTO_RESUME, ssm.loadNewestResumeState(rom))
    }

    @Test
    fun liveOrderingSurvivesAndroidSecondPrecision() {
        val romDir = ssm.getCanonicalRomDir(rom)
        assertEquals(1_700_000_001_000L, SaveStateFiles.nextStateTimestamp(romDir, 1_700_000_000_123L))
        state(SaveStateFiles.AUTO_RESUME, 1_700_000_001_000L)
        assertEquals(1_700_000_002_000L, SaveStateFiles.nextStateTimestamp(romDir, 1_700_000_000_123L))
        assertThrows(java.io.IOException::class.java) { SaveStateFiles.nextStateTimestamp(romDir, Long.MAX_VALUE) }
    }

    @Test
    fun sameClockTickAndClockRollbackKeepNewLiveStatesOrdered() {
        val romDir = ssm.getCanonicalRomDir(rom)
        state(SaveStateFiles.AUTO_RESUME, 5000)
        assertEquals(6000L, SaveStateFiles.nextStateTimestamp(romDir, 5000))
        assertEquals(6000L, SaveStateFiles.nextStateTimestamp(romDir, 1000))
        assertEquals(6000L, SaveStateFiles.nextStateTimestamp(romDir, 6000))
    }

    @Test
    fun tenSlots_overwriteKeepsBak_andOutOfRangeRefused() {
        assertEquals(10, ssm.getAllSlotsInfo(rom).size)
        assertTrue(ssm.saveSlot(rom, 10))
        assertFalse(ssm.saveSlot(rom, 11))
        assertFalse(ssm.saveSlot(rom, 0))
        val slot = ssm.getCanonicalFile(rom, "slot_10.state")
        slot.writeBytes(ByteArray(STATE_SIZE_256K) { 7 })
        assertTrue(ssm.saveSlot(rom, 10))
        val bak = File(slot.parentFile, "slot_10.state.bak")
        assertEquals(7.toByte(), bak.readBytes()[0])
    }

    @Test
    fun loadSnapshotsUndo_thenUndoLoadsIt() {
        assertTrue(ssm.saveSlot(rom, 3))
        assertFalse(ssm.hasUndoLoad(rom))
        bridge.events.clear()
        assertTrue(ssm.loadSlot(rom, 3))
        assertTrue(ssm.hasUndoLoad(rom))
        val saveIdx = bridge.events.indexOfFirst { it.startsWith("saveState:") && it.contains("undo_load") }
        val loadIdx = bridge.events.indexOfFirst { it.startsWith("loadState:") && it.contains("slot_3") }
        assertTrue("undo snapshot taken before the load", saveIdx in 0 until loadIdx)
        assertTrue(ssm.undoLoad(rom))
    }

    @Test
    fun importClearsAutoResumeWithoutLoweringFutureBarrier() {
        val future = System.currentTimeMillis() + 7 * 86400000L
        state(SaveStateFiles.AUTO_RESUME, System.currentTimeMillis() - 60_000)
        val oldSlot = state(SaveStateFiles.slotState(1), future)
        val barrier = ssm.getCanonicalFile(rom, SaveStateFiles.RESUME_BARRIER).apply {
            writeText(future.toString()); check(setLastModified(future))
        }
        assertTrue(ssm.importBatterySave(rom, ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 9 }.inputStream()))
        assertTrue(barrier.lastModified() > future)
        assertEquals(future, oldSlot.lastModified())
        assertNull(ssm.loadNewestResumeState(rom))
        assertTrue(ssm.saveAutoResume(rom))
        assertEquals(SaveStateFiles.AUTO_RESUME, ssm.loadNewestResumeState(rom))
    }

    @Test
    fun backupName_format() {
        val tz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("Pokemon Emerald.backup-20261010-134502.sav",
                SaveStateFiles.backupName("Pokemon Emerald", "sav", 1_791_639_902_000L))
        } finally { TimeZone.setDefault(tz) }
        assertEquals("Emerald", SaveStateFiles.shareBaseName("content/x/Emerald.gba", "fallback"))
        assertEquals("fallback", SaveStateFiles.shareBaseName(null, "fallback"))
        assertEquals("a_b", SaveStateFiles.shareBaseName("a:b.gba", "x"))
    }

    @Test
    fun opaqueArgb_forcesAlphaAndDecodesFormats() {
        // XRGB8888 read as R,G,B,junk: junk 4th byte must not leak into alpha.
        val px = SaveStateFiles.opaqueArgb(byteArrayOf(0x10, 0x20, 0x30, 0x00), 1, 1, 4, 1)
        assertEquals(0xFF102030.toInt(), px[0])
        // RGB565 little-endian pure red (0xF800) -> 0xFFFF0000; pitch padding respected.
        val p565 = SaveStateFiles.opaqueArgb(byteArrayOf(0x00, 0xF8.toByte(), 0, 0, 0x1F, 0x00, 0, 0), 1, 2, 4, 2)
        assertEquals(0xFFFF0000.toInt(), p565[0])
        assertEquals(0xFF0000FF.toInt(), p565[1])
    }

    @Test
    fun shareExport_neverClobbersAnotherEmulatorsSave() {
        assertEquals(ExportAction.WRITE, SaveShareStore.exportAction(null, null, "c"))
        assertEquals(ExportAction.IN_SYNC, SaveShareStore.exportAction("c", null, "c"))
        assertEquals(ExportAction.WRITE, SaveShareStore.exportAction("old", "old", "c"))
        assertEquals(ExportAction.CONFLICT, SaveShareStore.exportAction("other", "old", "c"))
        assertEquals(ExportAction.CONFLICT, SaveShareStore.exportAction("other", null, "c"))
    }

    @Test
    fun shareSuggestions_andDocIds() {
        val root = File(dir, "storage/emulated/0").apply { mkdirs() }
        File(root, "RetroArch/saves/mGBA").mkdirs()
        assertEquals(listOf(File(root, "RetroArch/saves/mGBA"), File(root, "RetroArch/saves")),
            SaveShareStore.suggestedDirs(listOf(root)))
        assertEquals("primary:RetroArch/saves/mGBA",
            SaveShareStore.externalStorageDocId(File("/storage/emulated/0/RetroArch/saves/mGBA")))
        assertEquals("ABCD-1234:RetroArch", SaveShareStore.externalStorageDocId(File("/storage/ABCD-1234/RetroArch")))
        assertNull(SaveShareStore.externalStorageDocId(File("/data/x")))
    }
}
