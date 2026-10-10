package com.dualdex.emulator

import com.dualdex.emulator.RomSaveIntegrityTest.Companion.HASH_A
import com.dualdex.emulator.RomSaveIntegrityTest.Companion.STATE_SIZE_256K
import com.dualdex.emulator.storage.SaveShareStore
import com.dualdex.emulator.storage.SaveShareStore.ExportAction
import com.dualdex.emulator.storage.SaveStateFiles
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
    fun tearDown() { dir.deleteRecursively() }

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
    fun importClearsAutoResume() {
        state(SaveStateFiles.AUTO_RESUME, System.currentTimeMillis() - 60_000)
        assertTrue(ssm.importBatterySave(rom, ByteArray(RomSaveIntegrityTest.SRAM_SIZE_128K) { 9 }.inputStream()))
        assertNull(ssm.loadNewestResumeState(rom))
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
