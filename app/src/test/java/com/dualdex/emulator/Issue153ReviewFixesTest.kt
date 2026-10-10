package com.dualdex.emulator

import com.dualdex.battle.FoeSlot
import com.dualdex.emulator.RomSaveIntegrityTest.Companion.HASH_A
import com.dualdex.emulator.RomSaveIntegrityTest.Companion.SRAM_SIZE_128K
import com.dualdex.emulator.RomSaveIntegrityTest.Companion.STATE_SIZE_256K
import com.dualdex.emulator.storage.SaveStateFiles
import com.dualdex.emulator.storage.SaveWriteResult
import com.dualdex.library.LibraryScanner
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.settings.SettingsManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/** Regression tests for the #172 senior review of the #153 quick wins. */
class Issue153ReviewFixesTest {
    private lateinit var dir: File
    private lateinit var bridge: RomSaveIntegrityTest.TestCoreBridge
    private lateinit var coordinator: LibretroCoreCoordinator
    private lateinit var ssm: SaveStateManager
    private val romA = RomIdentity.create(HASH_A, "Ruby")
    private val romB = RomIdentity.create("b".repeat(64), "Heart & Soul")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dualdex_review_").toFile()
        bridge = RomSaveIntegrityTest.TestCoreBridge()
        coordinator = LibretroCoreCoordinator(bridge)
        ssm = SaveStateManager(customBaseDir = dir, customCoordinator = coordinator)
        ssm.coreBridge = bridge
    }

    @After
    fun tearDown() { dir.deleteRecursively() }

    private fun file(id: RomIdentity, name: String) = ssm.getCanonicalFile(id, name)

    // ---- P0: one core owner, and per-ROM writers refuse a ROM that is not loaded ----

    @Test
    fun frontendLaunchOfRomB_whileAIsRunning_neverWritesBIntoA() {
        val owner = CoreOwner()
        val original = Any()   // activity that is running ROM A
        val frontend = Any()   // activity started for the frontend launch
        owner.claim(original)
        ssm.setActiveGame(romA)
        bridge.currentSramBytes = ByteArray(SRAM_SIZE_128K) { 0x0A }
        assertTrue(ssm.flushBatterySave(romA) is SaveWriteResult.Success)
        val savedA = file(romA, "battery.sav").readBytes()

        // Frontend launch: the new owner switches the core to ROM B.
        owner.claim(frontend)
        ssm.setActiveGame(romB)
        bridge.currentSramBytes = ByteArray(SRAM_SIZE_128K) { 0x0B }

        // Original activity is backgrounded, then destroyed, still holding identity A.
        assertFalse("backgrounded stale activity is not the owner", owner.isOwner(original))
        assertFalse("destroyed stale activity must not flush or clean up", owner.release(original))
        assertTrue(ssm.flushBatterySave(romA) is SaveWriteResult.Failure)
        assertFalse(ssm.saveAutoResume(romA))
        assertFalse(ssm.loadAutoResume(romA))
        assertNull(ssm.loadNewestResumeState(romA))
        assertFalse(ssm.exportBatterySave(romA, ByteArrayOutputStream()))
        assertFalse(ssm.importBatterySave(romA, ByteArrayInputStream(ByteArray(SRAM_SIZE_128K))))
        assertFalse(ssm.loadBatterySave(romA))
        assertArrayEquals("A's save still holds A's SRAM", savedA, file(romA, "battery.sav").readBytes())
        assertFalse(file(romA, SaveStateFiles.AUTO_RESUME).exists())

        // Returning to the session that owns the core keeps saving B into B.
        assertTrue(owner.isOwner(frontend))
        assertTrue(ssm.flushBatterySave(romB) is SaveWriteResult.Success)
        assertEquals(0x0B.toByte(), file(romB, "battery.sav").readBytes()[0])
        assertTrue(owner.release(frontend))
        assertFalse(owner.release(frontend))
    }

    // ---- P1: auto-resume never boots a state older than the battery save ----

    private fun state(id: RomIdentity, name: String, mtime: Long) = file(id, name).apply {
        writeBytes(ByteArray(STATE_SIZE_256K) { 1 }); setLastModified(mtime)
    }

    @Test
    fun crashAfterInGameSave_skipsOlderResumeState() {
        ssm.setActiveGame(romA)
        state(romA, SaveStateFiles.AUTO_RESUME, 1_000_000L)
        state(romA, SaveStateFiles.slotState(2), 2_000_000L)
        // The battery save was flushed later, then the app died before the resume write.
        file(romA, "battery.sav").apply { writeBytes(ByteArray(SRAM_SIZE_128K)); setLastModified(3_000_000L) }
        assertNull(ssm.loadNewestResumeState(romA))
        // Equal mtime (flush + resume write in the same second) still resumes.
        file(romA, SaveStateFiles.AUTO_RESUME).setLastModified(3_000_000L)
        assertEquals(SaveStateFiles.AUTO_RESUME, ssm.loadNewestResumeState(romA))
    }

    @Test
    fun failedResumeSnapshot_doesNotBootOldResumeOverNewerBatterySave() {
        ssm.setActiveGame(romA)
        state(romA, SaveStateFiles.AUTO_RESUME, System.currentTimeMillis() - 60_000)
        assertTrue(ssm.flushBatterySave(romA) is SaveWriteResult.Success)
        bridge.saveStateResult = false
        assertFalse(ssm.saveAutoResume(romA))
        assertNull(ssm.loadNewestResumeState(romA))
    }

    @Test
    fun saveImport_blocksResumeOfEveryOlderState() {
        ssm.setActiveGame(romA)
        val before = System.currentTimeMillis() - 60_000
        state(romA, SaveStateFiles.AUTO_RESUME, before)
        state(romA, SaveStateFiles.QUICK_SAVE, before)
        assertTrue(ssm.importBatterySave(romA, ByteArrayInputStream(ByteArray(SRAM_SIZE_128K) { 7 })))
        assertNull(ssm.loadNewestResumeState(romA))
        assertTrue("manual state stays loadable by hand", file(romA, SaveStateFiles.QUICK_SAVE).exists())
    }

    @Test
    fun bootResumeSetting_defaultsOn_andCanBeTurnedOff() {
        val prefs = RomDurableResumeTest.FakeSharedPreferences()
        assertTrue(SettingsManager(prefs).isBootResumeEnabled)
        SettingsManager(prefs).isBootResumeEnabled = false
        assertFalse(SettingsManager(prefs).isBootResumeEnabled)
    }

    @Test
    fun smartFastForward_defaultsOff() {
        val prefs = RomDurableResumeTest.FakeSharedPreferences()
        assertFalse(SettingsManager(prefs).isSmartFastForwardEnabled)
        SettingsManager(prefs).isSmartFastForwardEnabled = true
        assertTrue(SettingsManager(prefs).isSmartFastForwardEnabled)
    }

    // ---- P1: Close Game ----

    @Test
    fun closeGame_pausesFirst_unloadsOnlyAfterBothSaves_andStepFrameStopsAfterUnload() {
        ssm.setActiveGame(romA)
        val order = mutableListOf<String>()
        bridge.events.clear()
        assertTrue(ssm.closeGame(romA, { order += "pause"; bridge.events.add("pause") }, { order += "resume" }))
        assertEquals(listOf("pause"), order)
        val ev = bridge.events
        assertEquals("pause", ev.first())
        val unload = ev.indexOf("unloadRom")
        assertTrue(unload > ev.indexOfFirst { it.startsWith("flushSaveRam") })
        assertTrue(unload > ev.indexOfFirst { it.startsWith("saveState") && it.contains(SaveStateFiles.AUTO_RESUME) })
        assertNull(ssm.activeIdentity)
        assertFalse("no frame may run on an unloaded core", coordinator.stepFrame())
        assertTrue(coordinator.loadRom("x.gba"))
        assertTrue(coordinator.stepFrame())
    }

    @Test
    fun closeGame_resumeSnapshotFails_abortsAndResumes() {
        ssm.setActiveGame(romA)
        bridge.saveStateResult = false
        val order = mutableListOf<String>()
        assertFalse(ssm.closeGame(romA, { order += "pause" }, { order += "resume" }))
        assertEquals(listOf("pause", "resume"), order)
        assertFalse(bridge.events.contains("unloadRom"))
        assertEquals(romA, ssm.activeIdentity)
    }

    @Test
    fun closeGame_batteryFlushFails_abortsAndResumes() {
        ssm.setActiveGame(romA)
        bridge.flushSaveRamResult = false
        val order = mutableListOf<String>()
        assertFalse(ssm.closeGame(romA, { order += "pause" }, { order += "resume" }))
        assertEquals(listOf("pause", "resume"), order)
        assertFalse(bridge.events.contains("unloadRom"))
    }

    // ---- P2: Undo Last Load never offers a stale undo ----

    @Test
    fun failedUndoSnapshot_dropsOldUndo_butLoadStillProceeds() {
        ssm.setActiveGame(romA)
        assertTrue(ssm.saveSlot(romA, 1))
        assertTrue(ssm.loadSlot(romA, 1))
        assertTrue(ssm.hasUndoLoad(romA))
        bridge.saveStateResult = false
        bridge.events.clear()
        assertTrue("load is not blocked", ssm.loadSlot(romA, 1))
        assertTrue(bridge.events.any { it.startsWith("loadState") })
        assertFalse("no stale undo offered", ssm.hasUndoLoad(romA))
    }

    // ---- P1: foe names come from the active data pack ----

    @Test
    fun foeName_usesActiveDataPack_forHnsIdCollisions() {
        val collision = (1..2000).firstOrNull { id ->
            val hns = HeartAndSoul205DataPack.getSpecies(id) ?: return@firstOrNull false
            hns.name != SpeciesDatabase.get(id).name
        }
        assertNotNull("H&S renumbers at least one species", collision)
        val foe = FoeSlot(0, collision, active = true, next = false, fainted = false)
        assertEquals(HeartAndSoul205DataPack.getSpecies(collision!!)!!.name, foe.name(HeartAndSoul205DataPack))
        assertNotEquals(SpeciesDatabase.get(collision).name, foe.name(HeartAndSoul205DataPack))
        assertNull(FoeSlot(1, null, false, false, false).name(HeartAndSoul205DataPack))
    }

    @Test
    fun foeName_hnsSpecificSpecies_isNeverAGlobalSubstitute() {
        val hnsOnly = (1..3000).firstOrNull { id ->
            HeartAndSoul205DataPack.getSpecies(id) != null && SpeciesDatabase.get(id).name.startsWith("Pokemon #")
        }
        if (hnsOnly != null) {
            assertEquals(HeartAndSoul205DataPack.getSpecies(hnsOnly)!!.name, FoeSlot(0, hnsOnly, true, false, false).name(HeartAndSoul205DataPack))
        }
        // An id the pack does not know gets the pack's placeholder, not a global entry.
        val unknown = 0xFFFF
        assertNull(HeartAndSoul205DataPack.getSpecies(unknown))
        assertEquals("Unknown Species #$unknown", FoeSlot(0, unknown, true, false, false).name(HeartAndSoul205DataPack))
    }

    // ---- P2: library scan closes its stream ----

    private class TrackingStream(private val inner: InputStream, private val failAfter: Int = Int.MAX_VALUE) : InputStream() {
        var closed = false
        var reads = 0
        override fun read(): Int = if (++reads > failAfter) throw IOException("boom") else inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            if (++reads > failAfter) throw IOException("boom") else inner.read(b, off, len)
        override fun close() { closed = true }
    }

    @Test
    fun libraryInspect_closesStream_onSuccessAndOnFailure() {
        val ok = TrackingStream(ByteArrayInputStream(ByteArray(4096) { it.toByte() }))
        val (header, sums) = LibraryScanner.headerAndChecksums(ok)
        assertEquals(192, header.size)
        assertEquals(4096L, sums.size)
        assertTrue(ok.closed)

        val failing = TrackingStream(ByteArrayInputStream(ByteArray(4096)), failAfter = 0)
        assertThrows(IOException::class.java) { LibraryScanner.headerAndChecksums(failing) }
        assertTrue("stream closed even when the header read throws", failing.closed)
    }

    @Test
    fun normalExit_onPauseThenOnDestroyFlush_keepsResumeStateBootable() {
        ssm.setActiveGame(romA)
        // onPause: flush + resume snapshot.
        assertTrue(ssm.flushBatterySave(romA) is SaveWriteResult.Success)
        assertTrue(ssm.saveAutoResume(romA))
        file(romA, "battery.sav").setLastModified(1_000_000L)
        file(romA, SaveStateFiles.AUTO_RESUME).setLastModified(1_000_000L)
        // onDestroy: second flush with unchanged SRAM must not touch battery.sav.
        assertTrue(ssm.flushBatterySave(romA) is SaveWriteResult.Success)
        assertEquals(1_000_000L, file(romA, "battery.sav").lastModified())
        // Relaunch boots the resume state.
        assertEquals(SaveStateFiles.AUTO_RESUME, ssm.loadNewestResumeState(romA))

        // Genuinely newer SRAM is still committed, and still makes the older state stale.
        bridge.currentSramBytes = ByteArray(SRAM_SIZE_128K) { 0x77 }
        assertTrue(ssm.flushBatterySave(romA) is SaveWriteResult.Success)
        assertTrue(file(romA, "battery.sav").lastModified() > 1_000_000L)
        assertNull(ssm.loadNewestResumeState(romA))
    }
}
