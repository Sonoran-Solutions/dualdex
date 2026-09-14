package com.dualdex.emulator

import android.net.Uri
import com.dualdex.cheats.CheatItem
import com.dualdex.cheats.CheatManager
import com.dualdex.emulator.storage.AtomicSaveFile
import com.dualdex.emulator.storage.LegacyCandidate
import com.dualdex.emulator.storage.LegacySaveCatalog
import com.dualdex.emulator.storage.MigrationResult
import com.dualdex.emulator.storage.MirrorStatus
import com.dualdex.emulator.storage.SafMirrorStore
import com.dualdex.emulator.storage.SaveWriteResult
import com.dualdex.romhack.RomHackProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class RomSaveIntegrityTest {

    private lateinit var testBaseDir: File
    private lateinit var testBridge: TestCoreBridge
    private lateinit var saveStateManager: SaveStateManager

    companion object {
        const val HASH_A = "1111111111112222222222223333333333334444444444445555555555556666"
        const val HASH_B = "9999999999998888888888887777777777776666666666665555555555554444"
        const val SRAM_SIZE_128K = 131072
        const val STATE_SIZE_256K = 262144
    }

    class TestCoreBridge : LibretroCoreBridge {
        var configuredSaveRamSize: Long = SRAM_SIZE_128K.toLong()
        var configuredSaveStateSize: Long = STATE_SIZE_256K.toLong()
        var loadRomResult: Boolean = true
        var unloadRomResult: Boolean = true
        var saveStateResult: Boolean = true
        var loadStateResult: Boolean = true
        var loadSaveRamResult: Boolean = true
        var flushSaveRamResult: Boolean = true
        var rejectCandidateSram: Boolean = false
        @Volatile var isSteppingFrame: Boolean = false
        @Volatile var isRunningExclusive: Boolean = false
        @Volatile var overlapDetected: Boolean = false

        val events = mutableListOf<String>()
        var currentSramBytes: ByteArray? = null
        var lastLoadedRomPath: String? = null

        override fun stepFrame(): Boolean {
            isSteppingFrame = true
            if (isRunningExclusive) {
                overlapDetected = true
            }
            Thread.sleep(1)
            isSteppingFrame = false
            return true
        }

        override fun loadRom(romPath: String): Boolean {
            events.add("loadRom:$romPath")
            lastLoadedRomPath = romPath
            return loadRomResult
        }

        override fun unloadRom(): Boolean {
            events.add("unloadRom")
            return unloadRomResult
        }

        override fun getSaveRamSize(): Long = configuredSaveRamSize
        override fun getSaveStateSize(): Long = configuredSaveStateSize

        override fun saveState(statePath: String): Boolean {
            events.add("saveState:$statePath")
            if (saveStateResult) {
                File(statePath).writeBytes(ByteArray(configuredSaveStateSize.toInt()) { 0x33.toByte() })
            }
            return saveStateResult
        }

        override fun loadState(statePath: String): Boolean {
            events.add("loadState:$statePath")
            val f = File(statePath)
            if (!f.exists() || f.length() != configuredSaveStateSize) {
                return false
            }
            return loadStateResult
        }

        override fun loadSaveRam(savePath: String): Boolean {
            events.add("loadSaveRam:$savePath")
            val f = File(savePath)
            if (!f.exists() || f.length() == 0L) return false
            if (rejectCandidateSram && savePath.contains("__import_candidate")) return false
            if (loadSaveRamResult) {
                currentSramBytes = f.readBytes()
            }
            return loadSaveRamResult
        }

        override fun flushSaveRam(savePath: String): Boolean {
            events.add("flushSaveRam:$savePath")
            if (flushSaveRamResult) {
                val bytes = currentSramBytes ?: ByteArray(configuredSaveRamSize.toInt()) { 0x55.toByte() }
                File(savePath).writeBytes(bytes)
            }
            return flushSaveRamResult
        }

        override fun resetCore() {
            events.add("resetCore")
        }
    }

    class TestSafMirror(
        var isConfiguredValue: Boolean = false,
        var simulateRevoked: Boolean = false,
        var simulateWriteFailure: Boolean = false,
        val safDir: File? = null
    ) : SafMirrorStore() {
        override fun isSafConfigured(): Boolean = isConfiguredValue

        override fun mirrorFile(identity: RomIdentity, fileName: String, canonicalFile: File): MirrorStatus {
            if (!isConfiguredValue) return MirrorStatus.UNAVAILABLE
            if (simulateRevoked) return MirrorStatus.PERMISSION_REVOKED
            if (simulateWriteFailure) return MirrorStatus.FAILED

            val root = safDir ?: return MirrorStatus.FAILED
            val romDir = File(root, identity.storageKey).apply { mkdirs() }
            val destFile = File(romDir, fileName)
            canonicalFile.copyTo(destFile, overwrite = true)
            return if (destFile.length() == canonicalFile.length()) MirrorStatus.IN_SYNC else MirrorStatus.FAILED
        }

        override fun checkMirrorStatus(identity: RomIdentity, fileName: String, canonicalFile: File): MirrorStatus {
            if (!isConfiguredValue) return MirrorStatus.UNAVAILABLE
            if (simulateRevoked) return MirrorStatus.PERMISSION_REVOKED
            val root = safDir ?: return MirrorStatus.OUT_OF_SYNC
            val destFile = File(File(root, identity.storageKey), fileName)
            if (!canonicalFile.exists()) {
                return if (destFile.exists()) MirrorStatus.OUT_OF_SYNC else MirrorStatus.IN_SYNC
            }
            if (!destFile.exists() || destFile.length() != canonicalFile.length()) return MirrorStatus.OUT_OF_SYNC
            if (canonicalFile.length() == 0L) return MirrorStatus.IN_SYNC
            val canHash = canonicalFile.inputStream().use { calculateStreamSha256(it) }
            val destHash = destFile.inputStream().use { calculateStreamSha256(it) }
            return if (canHash.equals(destHash, ignoreCase = true)) MirrorStatus.IN_SYNC else MirrorStatus.OUT_OF_SYNC
        }

        override fun syncCanonicalToSaf(identity: RomIdentity, canonicalDir: File): Int {
            if (!isConfiguredValue || simulateRevoked) return 0
            val root = safDir ?: return 0
            val romDir = File(root, identity.storageKey).apply { mkdirs() }
            var count = 0
            canonicalDir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") && !f.name.endsWith(".bak") }?.forEach { src ->
                val dest = File(romDir, src.name)
                src.copyTo(dest, overwrite = true)
                if (dest.length() == src.length()) count++
            }
            return count
        }
    }

    @Before
    fun setUp() {
        testBaseDir = Files.createTempDirectory("dualdex_test_").toFile()
        testBridge = TestCoreBridge()
        saveStateManager = SaveStateManager(
            customBaseDir = testBaseDir,
            coreBridge = testBridge
        )
    }

    @After
    fun tearDown() {
        AtomicSaveFile.syncHook = null
        AtomicSaveFile.backupHook = null
        testBaseDir.deleteRecursively()
    }

    // ---------------------------------------------------------
    // Scenarios 1 - 3: RomIdentity & Hash Disambiguation
    // ---------------------------------------------------------

    @Test
    fun test01_sameHashDifferentTitle_sharesSaveFolderAndFiles() {
        val identity1 = RomIdentity.create(HASH_A, "Pokemon FireRed")
        val identity2 = RomIdentity.create(HASH_A, "Pokemon Fire Red (USA)")

        assertEquals(identity1.storageKey, identity2.storageKey)
        val dir1 = saveStateManager.getCanonicalRomDir(identity1)
        val dir2 = saveStateManager.getCanonicalRomDir(identity2)
        assertEquals(dir1.absolutePath, dir2.absolutePath)

        val saveFile1 = saveStateManager.getCanonicalFile(identity1, "battery.sav")
        saveFile1.writeBytes(ByteArray(100) { 0x42.toByte() })

        val saveFile2 = saveStateManager.getCanonicalFile(identity2, "battery.sav")
        assertTrue(saveFile2.exists())
        assertEquals(100L, saveFile2.length())
        assertEquals(0x42.toByte(), saveFile2.readBytes()[0])
    }

    @Test
    fun test02_differentHashSameTitle_isolatedSaveFolders() {
        val identityA = RomIdentity.create(HASH_A, "Pokemon Emerald")
        val identityB = RomIdentity.create(HASH_B, "Pokemon Emerald")

        assertNotEquals(identityA.storageKey, identityB.storageKey)
        val dirA = saveStateManager.getCanonicalRomDir(identityA)
        val dirB = saveStateManager.getCanonicalRomDir(identityB)
        assertNotEquals(dirA.absolutePath, dirB.absolutePath)

        val saveA = saveStateManager.getCanonicalFile(identityA, "battery.sav")
        saveA.writeBytes(ByteArray(100) { 0xAA.toByte() })

        val saveB = saveStateManager.getCanonicalFile(identityB, "battery.sav")
        assertFalse(saveB.exists())
    }

    @Test
    fun test03_invalidHash_rejectedNoSaveOperations() {
        val invalidHashes = listOf(
            "",
            "   ",
            "12345",
            "not_a_valid_hex_hash_at_all_123456789012345678901234567890123456",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "1111222233334444"
        )

        for (invalidHash in invalidHashes) {
            val identity = RomIdentity.create(invalidHash, "Test Game")
            assertFalse("Expected hash '$invalidHash' to be invalid", identity.isValid)

            assertFalse(saveStateManager.quickSave(identity))
            assertFalse(saveStateManager.saveSlot(identity, 1))
            assertFalse(saveStateManager.loadSlot(identity, 1))
            assertFalse(saveStateManager.saveAutoResume(identity))
            assertFalse(saveStateManager.loadAutoResume(identity))
            assertFalse(saveStateManager.loadBatterySave(identity))

            val flushRes = saveStateManager.flushBatterySave(identity)
            assertTrue(flushRes is SaveWriteResult.Failure)

            val importStream = ByteArrayInputStream(ByteArray(SRAM_SIZE_128K) { 0x11.toByte() })
            assertFalse(saveStateManager.importBatterySave(identity, importStream))

            val exportStream = ByteArrayOutputStream()
            assertFalse(saveStateManager.exportBatterySave(identity, exportStream))
        }
    }

    // ---------------------------------------------------------
    // Scenarios 4 - 7: AtomicSaveFile & Backup Retention
    // ---------------------------------------------------------

    @Test
    fun test04_canonicalSaveReplacement_atomicWriteAndBakKept() {
        val target = File(testBaseDir, "game.sav")
        val dataV1 = ByteArray(1024) { 0x01.toByte() }
        val dataV2 = ByteArray(1024) { 0x02.toByte() }

        assertTrue(AtomicSaveFile.writeBytes(target, dataV1))
        assertEquals(0x01.toByte(), target.readBytes()[0])

        assertTrue(AtomicSaveFile.writeBytes(target, dataV2))
        assertEquals(0x02.toByte(), target.readBytes()[0])

        val bak = File(testBaseDir, "game.sav.bak")
        assertTrue(bak.exists())
        assertEquals(0x01.toByte(), bak.readBytes()[0])

        // Verify no leftover .tmp files
        val tmpFiles = testBaseDir.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun test05_failedWriteToTemp_canonicalUntouchedNoTmpLeak() {
        val target = File(testBaseDir, "safe.sav")
        val originalData = ByteArray(512) { 0x77.toByte() }
        assertTrue(AtomicSaveFile.writeBytes(target, originalData))

        // Staging file does not exist -> copyFromStaging fails safely
        val corruptStaging = File(testBaseDir, "corrupt.staging")
        val copyFailed = AtomicSaveFile.copyFromStaging(corruptStaging, target)
        assertFalse(copyFailed)

        assertTrue(target.exists())
        assertEquals(512L, target.length())
        assertEquals(0x77.toByte(), target.readBytes()[0])

        val tmpFiles = testBaseDir.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun test06_atomicWriteFailure_recoveryFromBak() {
        val target = File(testBaseDir, "recoverable.sav")
        val v1 = ByteArray(256) { 0x11.toByte() }
        val v2 = ByteArray(256) { 0x22.toByte() }

        assertTrue(AtomicSaveFile.writeBytes(target, v1))
        assertTrue(AtomicSaveFile.writeBytes(target, v2))

        val bak = File(testBaseDir, "recoverable.sav.bak")
        assertTrue(bak.exists())
        assertEquals(0x11.toByte(), bak.readBytes()[0])

        // Corrupt target to 0 bytes
        target.writeBytes(ByteArray(0))
        assertEquals(0L, target.length())

        // Ensure rollback can restore from .bak
        assertTrue(AtomicSaveFile.restoreBackup(target))
        assertEquals(256L, target.length())
        assertEquals(0x11.toByte(), target.readBytes()[0])
    }

    @Test
    fun test07_bakRetention_previousValidPreservedAfterSuccess() {
        val target = File(testBaseDir, "retention.sav")
        val v1 = ByteArray(128) { 0xAA.toByte() }
        val v2 = ByteArray(128) { 0xBB.toByte() }
        val v3 = ByteArray(128) { 0xCC.toByte() }

        AtomicSaveFile.writeBytes(target, v1)
        AtomicSaveFile.writeBytes(target, v2)
        val bak = File(testBaseDir, "retention.sav.bak")
        assertEquals(0xAA.toByte(), bak.readBytes()[0])

        AtomicSaveFile.writeBytes(target, v3)
        assertEquals(0xCC.toByte(), target.readBytes()[0])
        assertEquals(0xBB.toByte(), bak.readBytes()[0])
    }

    // ---------------------------------------------------------
    // Scenarios 8 - 14: Battery Import & Export Hardening
    // ---------------------------------------------------------

    @Test
    fun test08_batteryImport_truncatedFileRejectedBeforeMutation() {
        val identity = RomIdentity.create(HASH_A, "FireRed")
        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        canonical.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x10.toByte() })

        val initialEvents = testBridge.events.size

        // Provide 32KB truncated file for a 128KB expected game
        val truncated = ByteArray(32768) { 0xFF.toByte() }
        val result = saveStateManager.importBatterySave(identity, ByteArrayInputStream(truncated))

        assertFalse("Truncated import must be rejected", result)
        assertEquals(0x10.toByte(), canonical.readBytes()[0])
        assertEquals(initialEvents, testBridge.events.size) // No core mutation invoked
    }

    @Test
    fun test09_batteryImport_oversizedFileRejectedBeforeMutation() {
        val identity = RomIdentity.create(HASH_A, "Emerald")
        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        canonical.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x20.toByte() })

        val initialEvents = testBridge.events.size

        // Provide oversized file (> 128KB + 16-byte RTC)
        val oversized = ByteArray(SRAM_SIZE_128K + 100) { 0x33.toByte() }
        val result = saveStateManager.importBatterySave(identity, ByteArrayInputStream(oversized))

        assertFalse("Oversized import must be rejected", result)
        assertEquals(0x20.toByte(), canonical.readBytes()[0])
        assertEquals(initialEvents, testBridge.events.size)
    }

    @Test
    fun test10_batteryImport_validExactSize_importedAndCanonicalUpdated() {
        val identity = RomIdentity.create(HASH_A, "Ruby")
        val validPayload = ByteArray(SRAM_SIZE_128K) { (it % 256).toByte() }

        val ok = saveStateManager.importBatterySave(identity, ByteArrayInputStream(validPayload))
        assertTrue("Valid import must succeed", ok)

        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        assertTrue(canonical.exists())
        assertEquals(SRAM_SIZE_128K.toLong(), canonical.length())
        assertArrayEquals(validPayload, canonical.readBytes())

        assertTrue(testBridge.events.contains("resetCore"))
    }

    @Test
    fun test11_batteryImport_validSizeWithRtcFooter_strippedAndImported() {
        val identity = RomIdentity.create(HASH_A, "Emerald")
        // 131,072 bytes data + 16 bytes RTC footer = 131,088 bytes
        val dataPart = ByteArray(SRAM_SIZE_128K) { 0x7E.toByte() }
        val rtcFooter = ByteArray(16) { 0xEE.toByte() }
        val mgbaSave = dataPart + rtcFooter
        assertEquals(131088, mgbaSave.size)

        val ok = saveStateManager.importBatterySave(identity, ByteArrayInputStream(mgbaSave))
        assertTrue("mGBA import with RTC footer must succeed", ok)

        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        assertEquals(SRAM_SIZE_128K.toLong(), canonical.length())
        assertArrayEquals(dataPart, canonical.readBytes())
    }

    @Test
    fun test12_batteryImport_corruptPayloadFailsNativeLoad_sramRestored() {
        val identity = RomIdentity.create(HASH_A, "LeafGreen")
        val priorData = ByteArray(SRAM_SIZE_128K) { 0x44.toByte() }
        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        canonical.writeBytes(priorData)
        testBridge.currentSramBytes = priorData

        // Set native core to reject the candidate
        testBridge.loadSaveRamResult = false

        val candidate = ByteArray(SRAM_SIZE_128K) { 0x99.toByte() }
        val ok = saveStateManager.importBatterySave(identity, ByteArrayInputStream(candidate))

        assertFalse("Import must fail when core rejects payload", ok)
        assertEquals(0x44.toByte(), canonical.readBytes()[0])
    }

    @Test
    fun test13_batteryImport_canonicalCommitFails_rollbackToPrevious() {
        val identity = RomIdentity.create(HASH_A, "FireRed")
        val priorData = ByteArray(SRAM_SIZE_128K) { 0x55.toByte() }
        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        canonical.writeBytes(priorData)
        testBridge.currentSramBytes = priorData

        // Make canonical dir non-writable or lock canonical file
        val canonicalDir = saveStateManager.getCanonicalRomDir(identity)
        canonicalDir.setWritable(false)

        try {
            val candidate = ByteArray(SRAM_SIZE_128K) { 0x88.toByte() }
            val ok = saveStateManager.importBatterySave(identity, ByteArrayInputStream(candidate))
            // Commit fails -> must return false
            assertFalse(ok)
        } finally {
            canonicalDir.setWritable(true)
        }
    }

    @Test
    fun test14_batteryExport_failedSramFlush_returnsFailureNoPartialFile() {
        val identity = RomIdentity.create(HASH_A, "FireRed")
        testBridge.flushSaveRamResult = false // Native core flush fails

        val out = ByteArrayOutputStream()
        val ok = saveStateManager.exportBatterySave(identity, out)

        assertFalse("Export must fail if native SRAM flush fails", ok)
        assertEquals(0, out.size())
    }

    // ---------------------------------------------------------
    // Scenarios 15 - 16: Quicksave vs Auto-Resume Isolation
    // ---------------------------------------------------------

    @Test
    fun test15_quicksaveAndAutoResume_mutuallyIsolated() {
        val identity = RomIdentity.create(HASH_A, "FireRed")

        // 1. Quicksave
        assertTrue(saveStateManager.quickSave(identity))
        val quickFile = saveStateManager.getCanonicalFile(identity, "quicksave.state")
        assertTrue(quickFile.exists())
        assertEquals(STATE_SIZE_256K.toLong(), quickFile.length())

        // 2. Auto-resume
        assertTrue(saveStateManager.saveAutoResume(identity))
        val autoFile = saveStateManager.getCanonicalFile(identity, "auto_resume.state")
        assertTrue(autoFile.exists())

        // Mutate quicksave to verify independence
        quickFile.writeBytes(ByteArray(STATE_SIZE_256K) { 0x11.toByte() })
        autoFile.writeBytes(ByteArray(STATE_SIZE_256K) { 0x22.toByte() })

        assertEquals(0x11.toByte(), quickFile.readBytes()[0])
        assertEquals(0x22.toByte(), autoFile.readBytes()[0])
    }

    @Test
    fun test16_onPause_updatesAutoResume_leavesQuicksaveUntouched() {
        val identity = RomIdentity.create(HASH_A, "FireRed")
        val quickFile = saveStateManager.getCanonicalFile(identity, "quicksave.state")
        val manualPayload = ByteArray(STATE_SIZE_256K) { 0x77.toByte() }
        quickFile.writeBytes(manualPayload)

        // Simulate onPause auto-resume write
        val autoSaved = saveStateManager.saveAutoResume(identity)
        assertTrue(autoSaved)

        val autoFile = saveStateManager.getCanonicalFile(identity, "auto_resume.state")
        assertTrue(autoFile.exists())
        assertEquals(0x33.toByte(), autoFile.readBytes()[0]) // From TestCoreBridge

        // Quicksave is completely untouched
        assertArrayEquals(manualPayload, quickFile.readBytes())
    }

    // ---------------------------------------------------------
    // Scenarios 17 - 21: SAF Mirroring & Graceful Degradation
    // ---------------------------------------------------------

    @Test
    fun test17_safUnavailable_internalStorageOperatesNormally() {
        val safStore = TestSafMirror(isConfiguredValue = false)
        val manager = SaveStateManager(customBaseDir = testBaseDir, customSafStore = safStore, coreBridge = testBridge)
        val identity = RomIdentity.create(HASH_A, "FireRed")

        val result = manager.flushBatterySave(identity)
        assertTrue(result is SaveWriteResult.Success)
        val success = result as SaveWriteResult.Success
        assertTrue(success.canonicalWritten)
        assertEquals(MirrorStatus.UNAVAILABLE, success.mirrorStatus)

        val canonical = manager.getCanonicalFile(identity, "battery.sav")
        assertTrue(canonical.exists())
    }

    @Test
    fun test18_safWriteFailure_internalSaveSucceedsErrorStatusReported() {
        val safStore = TestSafMirror(isConfiguredValue = true, simulateWriteFailure = true, safDir = File(testBaseDir, "saf"))
        val manager = SaveStateManager(customBaseDir = testBaseDir, customSafStore = safStore, coreBridge = testBridge)
        val identity = RomIdentity.create(HASH_A, "FireRed")

        val result = manager.flushBatterySave(identity)
        assertTrue(result is SaveWriteResult.Success)
        val success = result as SaveWriteResult.Success
        assertTrue(success.canonicalWritten)
        assertEquals(MirrorStatus.FAILED, success.mirrorStatus)

        assertTrue(manager.getCanonicalFile(identity, "battery.sav").exists())
    }

    @Test
    fun test19_staleSafFile_internalSaveRemainsAuthoritative() {
        val safDir = File(testBaseDir, "saf").apply { mkdirs() }
        val safStore = TestSafMirror(isConfiguredValue = true, safDir = safDir)
        val manager = SaveStateManager(customBaseDir = testBaseDir, customSafStore = safStore, coreBridge = testBridge)
        val identity = RomIdentity.create(HASH_A, "FireRed")

        // Create internal save
        val internalFile = manager.getCanonicalFile(identity, "battery.sav")
        internalFile.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x99.toByte() })

        // Create stale SAF file with different size
        val safRomDir = File(safDir, identity.storageKey).apply { mkdirs() }
        val safFile = File(safRomDir, "battery.sav")
        safFile.writeBytes(ByteArray(65536) { 0x11.toByte() })

        assertEquals(MirrorStatus.OUT_OF_SYNC, manager.getSafMirrorStatus(identity))

        // Normal load loads internal authoritative file
        val ok = manager.loadBatterySave(identity)
        assertTrue(ok)
        assertArrayEquals(ByteArray(SRAM_SIZE_128K) { 0x99.toByte() }, testBridge.currentSramBytes)
    }

    @Test
    fun test20_explicitSyncToSaf_copiesAndVerifiesSize() {
        val safDir = File(testBaseDir, "saf").apply { mkdirs() }
        val safStore = TestSafMirror(isConfiguredValue = true, safDir = safDir)
        val manager = SaveStateManager(customBaseDir = testBaseDir, customSafStore = safStore, coreBridge = testBridge)
        val identity = RomIdentity.create(HASH_A, "FireRed")

        val internalFile = manager.getCanonicalFile(identity, "battery.sav")
        internalFile.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x77.toByte() })

        val syncedCount = manager.syncCanonicalToSaf(identity)
        assertEquals(1, syncedCount)
        assertEquals(MirrorStatus.IN_SYNC, manager.getSafMirrorStatus(identity))

        val safFile = File(File(safDir, identity.storageKey), "battery.sav")
        assertTrue(safFile.exists())
        assertEquals(SRAM_SIZE_128K.toLong(), safFile.length())
    }

    @Test
    fun test21_revokedSafPermission_gracefulDegradation() {
        val safStore = TestSafMirror(isConfiguredValue = true, simulateRevoked = true)
        val manager = SaveStateManager(customBaseDir = testBaseDir, customSafStore = safStore, coreBridge = testBridge)
        val identity = RomIdentity.create(HASH_A, "FireRed")

        val result = manager.flushBatterySave(identity)
        assertTrue(result is SaveWriteResult.Success)
        val success = result as SaveWriteResult.Success
        assertEquals(MirrorStatus.PERMISSION_REVOKED, success.mirrorStatus)
        assertTrue(manager.getCanonicalFile(identity, "battery.sav").exists())
    }

    // ---------------------------------------------------------
    // Scenarios 22 - 27: Legacy Migration Hardening
    // ---------------------------------------------------------

    @Test
    fun test22_legacyMigration_currentGameSavNeverAutoClaimed() {
        val legacyDir = File(testBaseDir, "saves").apply { mkdirs() }
        val currentSave = File(legacyDir, "current_game.sav")
        currentSave.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x33.toByte() })

        val catalog = LegacySaveCatalog(customLegacyDir = legacyDir)
        val candidates = catalog.discoverCandidates()

        assertEquals(1, candidates.size)
        val candidate = candidates[0]
        assertEquals("current_game", candidate.baseName)
        assertEquals("Unlabeled Save (current_game)", candidate.suggestedTitle)
        assertFalse(candidate.isAssigned)

        // Opening a new ROM must NOT automatically populate its canonical battery.sav
        val identity = RomIdentity.create(HASH_A, "Pokemon Emerald")
        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        assertFalse("Legacy current_game.sav must never be auto-claimed", canonical.exists())
    }

    @Test
    fun test23_legacyMigration_heuristicMatchesCandidatesOnlyNoAutoMove() {
        val legacyDir = File(testBaseDir, "saves").apply { mkdirs() }
        val legacyFile = File(legacyDir, "Pokemon_FireRed.sav")
        legacyFile.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x55.toByte() })

        val catalog = LegacySaveCatalog(customLegacyDir = legacyDir)
        val candidates = catalog.discoverCandidates()
        assertEquals(1, candidates.size)

        val identity = RomIdentity.create(HASH_A, "Pokemon FireRed")
        assertTrue(catalog.isSuggestedMatch(candidates[0], identity, "Pokemon FireRed"))

        // Heuristic candidate match must NOT move, rename, or copy files automatically
        assertTrue(legacyFile.exists())
        assertEquals(SRAM_SIZE_128K.toLong(), legacyFile.length())
        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        assertFalse(canonical.exists())
    }

    @Test
    fun test24_legacyMigration_candidateAcceptedForRomA_isolatedFromRomB() {
        val legacyDir = File(testBaseDir, "saves").apply { mkdirs() }
        val legacyFile = File(legacyDir, "Pokemon_FireRed.sav")
        legacyFile.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x55.toByte() })

        val catalog = LegacySaveCatalog(customLegacyDir = legacyDir)
        val candidate = catalog.discoverCandidates()[0]

        val identityA = RomIdentity.create(HASH_A, "FireRed")
        val canonicalDirA = saveStateManager.getCanonicalRomDir(identityA)
        assertEquals(MigrationResult.SUCCESS, catalog.assignCandidateToRom(candidate, identityA, canonicalDirA))

        // Attempt to assign to Rom B without warning must fail closed
        val identityB = RomIdentity.create(HASH_B, "FireRed Mod")
        val canonicalDirB = saveStateManager.getCanonicalRomDir(identityB)
        assertEquals(MigrationResult.FAILURE, catalog.assignCandidateToRom(candidate, identityB, canonicalDirB))
    }

    @Test
    fun test25_legacyMigration_destinationWriteFails_sourceUntouched() {
        val legacyDir = File(testBaseDir, "saves").apply { mkdirs() }
        val legacyFile = File(legacyDir, "Pokemon_Emerald.sav")
        legacyFile.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x66.toByte() })

        val catalog = LegacySaveCatalog(customLegacyDir = legacyDir)
        val candidate = catalog.discoverCandidates()[0]

        val identity = RomIdentity.create(HASH_A, "Emerald")
        val canonicalDir = saveStateManager.getCanonicalRomDir(identity)
        canonicalDir.setWritable(false)

        try {
            val assigned = catalog.assignCandidateToRom(candidate, identity, canonicalDir)
            assertEquals(MigrationResult.FAILURE, assigned)
            // Source must be completely untouched
            assertTrue(legacyFile.exists())
            assertFalse(File(legacyDir, "Pokemon_Emerald.sav.migrated.bak").exists())
        } finally {
            canonicalDir.setWritable(true)
        }
    }

    @Test
    fun test26_legacyMigration_successfulMigration_createsBakAndVerifies() {
        val legacyDir = File(testBaseDir, "saves").apply { mkdirs() }
        val legacyFile = File(legacyDir, "Pokemon_Emerald.sav")
        val content = ByteArray(SRAM_SIZE_128K) { 0x77.toByte() }
        legacyFile.writeBytes(content)

        val catalog = LegacySaveCatalog(customLegacyDir = legacyDir)
        val candidate = catalog.discoverCandidates()[0]
        val identity = RomIdentity.create(HASH_A, "Emerald")
        val canonicalDir = saveStateManager.getCanonicalRomDir(identity)

        assertEquals(MigrationResult.SUCCESS, catalog.assignCandidateToRom(candidate, identity, canonicalDir))

        val canonicalFile = File(canonicalDir, "battery.sav")
        assertTrue(canonicalFile.exists())
        assertArrayEquals(content, canonicalFile.readBytes())

        val origBak = File(legacyDir, "Pokemon_Emerald.sav.orig.bak")
        assertTrue(origBak.exists())
        assertArrayEquals(content, origBak.readBytes())

        val migratedBak = File(legacyDir, "Pokemon_Emerald.sav.migrated.bak")
        assertTrue(migratedBak.exists())
    }

    @Test
    fun test27_oldBranchSaveMigration_title12HashMigratedToFullHash() {
        val identity = RomIdentity.create(HASH_A, "Pokemon FireRed")
        val oldKey = identity.legacyStorageKey // "Pokemon_FireRed__111111111111"

        val fallbackBase = File(testBaseDir, "saves_fallback").apply { mkdirs() }
        val oldDir = File(fallbackBase, oldKey).apply { mkdirs() }
        val oldSave = File(oldDir, "battery.sav")
        val oldData = ByteArray(SRAM_SIZE_128K) { 0x99.toByte() }
        oldSave.writeBytes(oldData)

        // Calling migrateOldBranchDirIfPresent
        assertTrue(saveStateManager.migrateOldBranchDirIfPresent(identity))

        val canonicalFile = saveStateManager.getCanonicalFile(identity, "battery.sav")
        assertTrue(canonicalFile.exists())
        assertArrayEquals(oldData, canonicalFile.readBytes())

        val backupOldDir = File(fallbackBase, "${oldKey}.migrated.bak")
        assertTrue(backupOldDir.exists())
    }

    // ---------------------------------------------------------
    // Scenarios 28 - 29: Concurrency & Lock Serialization
    // ---------------------------------------------------------

    @Test
    fun test28_concurrentSaveRequests_serializedByLock() = runBlocking(Dispatchers.Default) {
        val identity = RomIdentity.create(HASH_A, "FireRed")

        val tasks = (1..20).map { _ ->
            async {
                val ok = saveStateManager.quickSave(identity)
                assertTrue(ok)
            }
        }
        tasks.awaitAll()

        val canonical = saveStateManager.getCanonicalFile(identity, "quicksave.state")
        assertTrue(canonical.exists())
        assertEquals(STATE_SIZE_256K.toLong(), canonical.length())
    }

    @Test
    fun test29_rapidSuccessiveSaves_serializedCleanly() {
        val identity = RomIdentity.create(HASH_A, "FireRed")
        for (i in 1..25) {
            val ok = saveStateManager.quickSave(identity)
            assertTrue("Quicksave $i failed", ok)
        }
        val quickFile = saveStateManager.getCanonicalFile(identity, "quicksave.state")
        assertTrue(quickFile.exists())
        assertEquals(STATE_SIZE_256K.toLong(), quickFile.length())
    }

    // ---------------------------------------------------------
    // Scenarios 30 - 34: RomSessionManager ROM Switch Transaction
    // ---------------------------------------------------------

    @Test
    fun test30_romSwitch_sramFlushedBeforeRomBLoaded() = runBlocking {
        val cacheDir = File(testBaseDir, "rom_cache").apply { mkdirs() }
        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        // Seed running ROM A
        val romABytes = ByteArray(1024) { 0x11.toByte() }
        val hashA = RomIdentity.calculateSha256(romABytes)
        val fileA = File(cacheDir, "$hashA.gba").apply { writeBytes(romABytes) }
        assertTrue(fileA.exists())
        val identityA = RomIdentity.create(hashA, "ROM A")
        saveStateManager.activeIdentity = identityA

        // Prepare new ROM B
        val romBBytes = ByteArray(1024) { 0x22.toByte() }
        val hashB = RomIdentity.calculateSha256(romBBytes)
        val fileB = File(testBaseDir, "rom_b.gba").apply { writeBytes(romBBytes) }

        val res = sessionManager.switchRomFile(fileB, emptyList(), "ROM B")
        assertTrue(res is SwitchResult.Success)

        // Verify ordering: flushSaveRam must occur before loadRom for ROM B
        val flushIdx = testBridge.events.indexOfFirst { it.startsWith("flushSaveRam") }
        val loadBIdx = testBridge.events.indexOfFirst { it.contains(hashB) }
        assertTrue("flushSaveRam must occur", flushIdx >= 0)
        assertTrue("loadRom(B) must occur", loadBIdx >= 0)
        assertTrue("flushSaveRam must precede loadRom(B)", flushIdx < loadBIdx)
    }

    @Test
    fun test31_romSwitch_romAUnloadedBeforeRomBLoaded() = runBlocking {
        val cacheDir = File(testBaseDir, "rom_cache").apply { mkdirs() }
        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        val romABytes = ByteArray(1024) { 0x11.toByte() }
        val hashA = RomIdentity.calculateSha256(romABytes)
        File(cacheDir, "$hashA.gba").writeBytes(romABytes)
        saveStateManager.activeIdentity = RomIdentity.create(hashA, "ROM A")

        val romBBytes = ByteArray(1024) { 0x22.toByte() }
        val hashB = RomIdentity.calculateSha256(romBBytes)
        val fileB = File(testBaseDir, "rom_b.gba").apply { writeBytes(romBBytes) }

        val res = sessionManager.switchRomFile(fileB, emptyList(), "ROM B")
        assertTrue(res is SwitchResult.Success)

        val unloadIdx = testBridge.events.indexOf("unloadRom")
        val loadBIdx = testBridge.events.indexOfFirst { it.contains(hashB) }
        assertTrue("unloadRom must occur", unloadIdx >= 0)
        assertTrue("loadRom(B) must occur", loadBIdx >= 0)
        assertTrue("unloadRom must precede loadRom(B)", unloadIdx < loadBIdx)
    }

    @Test
    fun test32_romSwitch_failedLoadEntersCleanNoRomState() = runBlocking {
        val cacheDir = File(testBaseDir, "rom_cache").apply { mkdirs() }
        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        val romABytes = ByteArray(1024) { 0x11.toByte() }
        val hashA = RomIdentity.calculateSha256(romABytes)
        File(cacheDir, "$hashA.gba").writeBytes(romABytes)
        saveStateManager.activeIdentity = RomIdentity.create(hashA, "ROM A")

        // Core rejects loading ROM B
        testBridge.loadRomResult = false

        val romBBytes = ByteArray(1024) { 0x22.toByte() }
        val fileB = File(testBaseDir, "rom_b.gba").apply { writeBytes(romBBytes) }

        val res = sessionManager.switchRomFile(fileB, emptyList(), "ROM B")
        assertTrue(res is SwitchResult.Failure)

        // ROM A save was safely flushed
        val canonicalA = saveStateManager.getCanonicalFile(RomIdentity.create(hashA, "ROM A"), "battery.sav")
        assertTrue(canonicalA.exists())

        // System enters clean no-ROM state
        val lastEvent = testBridge.events.last()
        assertEquals("unloadRom", lastEvent)
    }

    @Test
    fun test33_romSwitch_ABA_restoresSramCorrectly() = runBlocking {
        val cacheDir = File(testBaseDir, "rom_cache").apply { mkdirs() }
        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        val romABytes = ByteArray(1024) { 0x11.toByte() }
        val hashA = RomIdentity.calculateSha256(romABytes)
        val fileA = File(testBaseDir, "rom_a.gba").apply { writeBytes(romABytes) }
        val identityA = RomIdentity.create(hashA, "ROM A")

        val romBBytes = ByteArray(1024) { 0x22.toByte() }
        val hashB = RomIdentity.calculateSha256(romBBytes)
        val fileB = File(testBaseDir, "rom_b.gba").apply { writeBytes(romBBytes) }
        val identityB = RomIdentity.create(hashB, "ROM B")

        // 1. Initial play A and write save data A
        sessionManager.switchRomFile(fileA, emptyList(), "ROM A")
        val sramA = ByteArray(SRAM_SIZE_128K) { 0xAA.toByte() }
        testBridge.currentSramBytes = sramA
        saveStateManager.flushBatterySave(identityA)

        // 2. Switch to B and write save data B
        sessionManager.switchRomFile(fileB, emptyList(), "ROM B")
        val sramB = ByteArray(SRAM_SIZE_128K) { 0xBB.toByte() }
        testBridge.currentSramBytes = sramB
        saveStateManager.flushBatterySave(identityB)

        // 3. Switch back to A: must reload SRAM A
        sessionManager.switchRomFile(fileA, emptyList(), "ROM A")
        assertArrayEquals(sramA, testBridge.currentSramBytes)
    }

    @Test
    fun test34_romSwitch_cacheCopyFailure_currentRomUnchanged() = runBlocking {
        val cacheDir = File(testBaseDir, "rom_cache").apply { mkdirs() }
        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        val activeIdentity = RomIdentity.create(HASH_A, "Active ROM")
        saveStateManager.activeIdentity = activeIdentity

        val initialEvents = testBridge.events.size

        // Non-existent ROM file URI
        val missingFile = File(testBaseDir, "does_not_exist.gba")
        val res = sessionManager.switchRomFile(missingFile, emptyList(), "Missing")

        assertTrue(res is SwitchResult.Failure)
        assertEquals(initialEvents, testBridge.events.size) // Core was never paused/unloaded
        assertEquals(activeIdentity, saveStateManager.activeIdentity)
    }

    // ---------------------------------------------------------
    // Scenarios 35 - 36: Cheat Scoping & Save State Size Validation
    // ---------------------------------------------------------

    @Test
    fun test35_romIdentityScopedCheats_isolatedBetweenHashes() {
        val memoryStorage = mutableMapOf<String, String>()
        val cheatManager = CheatManager(memoryStorage = memoryStorage)

        val identityA = RomIdentity.create(HASH_A, "Pokemon FireRed")
        val identityB = RomIdentity.create(HASH_B, "Pokemon FireRed")

        // Add custom cheat to ROM A
        val cheatA = CheatItem(
            id = "cheat_1",
            name = "Rare Candy",
            code = "82025840 0044",
            enabled = true,
            isPreset = false
        )
        cheatManager.addCheat(identityA, cheatA)

        val cheatsA = cheatManager.getCheats(identityA)
        val cheatsB = cheatManager.getCheats(identityB)

        assertTrue(cheatsA.any { it.name == "Rare Candy" && it.enabled })
        assertFalse(cheatsB.any { it.name == "Rare Candy" })
    }

    @Test
    fun test36_invalidSaveStateSize_rejectedWithoutCrashing() {
        val identity = RomIdentity.create(HASH_A, "Pokemon Emerald")
        val slotFile = saveStateManager.getCanonicalFile(identity, "slot_1.state")

        // Write truncated 500-byte state when 262,144 bytes expected
        slotFile.writeBytes(ByteArray(500) { 0xFF.toByte() })

        val loaded = saveStateManager.loadSlot(identity, 1)
        assertFalse("Truncated state file must be rejected before native unserialize", loaded)
    }

    // ---------------------------------------------------------
    // Scenarios 37 - 45: Hardened Invariants & Regression Suite
    // ---------------------------------------------------------

    @Test
    fun test37_frameExecution_cannotOverlapExclusiveOperation() = runBlocking {
        val coordinator = LibretroCoreCoordinator(bridge = testBridge)

        // Run stepFrame and executeExclusive concurrently across multiple threads
        val stepJob = async(Dispatchers.Default) {
            repeat(50) {
                coordinator.stepFrame()
            }
        }

        val exclusiveJob = async(Dispatchers.Default) {
            repeat(10) {
                coordinator.executeExclusive {
                    testBridge.isRunningExclusive = true
                    if (testBridge.isSteppingFrame) {
                        testBridge.overlapDetected = true
                    }
                    Thread.sleep(2)
                    if (testBridge.isSteppingFrame) {
                        testBridge.overlapDetected = true
                    }
                    testBridge.isRunningExclusive = false
                }
            }
        }

        stepJob.await()
        exclusiveJob.await()

        assertFalse("stepFrame and executeExclusive must never overlap", testBridge.overlapDetected)
    }

    @Test
    fun test38_failedUnknownSramSize_rejectsImportWithoutFallback() {
        val identity = RomIdentity.create(HASH_A, "Pokemon Emerald")
        testBridge.configuredSaveRamSize = 0L // Indeterminable SRAM size

        val candidateData = ByteArray(SRAM_SIZE_128K) { 0x44.toByte() }
        val candidateStream = ByteArrayInputStream(candidateData)

        val imported = saveStateManager.importBatterySave(identity, candidateStream)
        assertFalse("Import must fail closed when core SRAM size cannot be determined", imported)

        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        assertFalse("Canonical save must not be written when import is rejected", canonical.exists())
    }

    @Test
    fun test39_failedLiveSramBackup_abortsImportBeforeLiveMutation() {
        val identity = RomIdentity.create(HASH_A, "Pokemon Emerald")
        val initialSram = ByteArray(SRAM_SIZE_128K) { 0xAA.toByte() }
        testBridge.currentSramBytes = initialSram
        testBridge.flushSaveRamResult = false // Simulates live SRAM backup failure

        val candidateData = ByteArray(SRAM_SIZE_128K) { 0xBB.toByte() }
        val imported = saveStateManager.importBatterySave(identity, ByteArrayInputStream(candidateData))

        assertFalse("Import must abort if live SRAM backup cannot be captured", imported)
        assertArrayEquals("Live core SRAM must be unmodified", initialSram, testBridge.currentSramBytes)
        val canonical = saveStateManager.getCanonicalFile(identity, "battery.sav")
        assertFalse("Canonical save must not be written", canonical.exists())
    }

    @Test
    fun test40_importFailure_restoresLiveSramFromRollback() {
        val identity = RomIdentity.create(HASH_A, "Pokemon Emerald")
        val initialSram = ByteArray(SRAM_SIZE_128K) { 0xAA.toByte() }
        testBridge.currentSramBytes = initialSram

        // Reject candidate SRAM when core tries to load it
        testBridge.rejectCandidateSram = true

        val candidateData = ByteArray(SRAM_SIZE_128K) { 0xBB.toByte() }
        val imported = saveStateManager.importBatterySave(identity, ByteArrayInputStream(candidateData))

        assertFalse("Import must fail when candidate payload fails to load", imported)
        assertArrayEquals("Live core SRAM must be restored to prior state from backup", initialSram, testBridge.currentSramBytes)
    }

    @Test
    fun test41_interruptedAtomicWrite_recoversPreviousCanonicalData() {
        val target = File(testBaseDir, "game.sav")
        val goodData = ByteArray(1024) { 0x55.toByte() }

        // Initial good write
        assertTrue(AtomicSaveFile.writeBytes(target, goodData))
        assertEquals(1024L, target.length())

        // Subsequent write creates .bak
        val dataV2 = ByteArray(1024) { 0x66.toByte() }
        assertTrue(AtomicSaveFile.writeBytes(target, dataV2))
        val bakFile = File(testBaseDir, "game.sav.bak")
        assertTrue(bakFile.exists())
        assertArrayEquals(goodData, bakFile.readBytes())

        // Simulate crash during subsequent write: target is truncated/deleted and .tmp left behind
        val tmpFile = File(testBaseDir, "game.sav.tmp")
        tmpFile.writeBytes(ByteArray(200) { 0x00.toByte() })
        target.delete() // Canonical was wiped out or missing after crash

        // Calling recoverInterrupted must restore from .bak and delete .tmp
        assertTrue(AtomicSaveFile.recoverInterrupted(target))
        assertTrue("Canonical file must be restored", target.exists())
        assertArrayEquals("Canonical content must match .bak data", goodData, target.readBytes())
        assertFalse("Lingering .tmp must be cleaned up", tmpFile.exists())
    }

    @Test
    fun test42_failedRomCacheInstallation_leavesActiveRomUntouched() = runBlocking {
        val cacheDir = File(testBaseDir, "rom_cache").apply { mkdirs() }
        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        val romABytes = ByteArray(1024) { 0x11.toByte() }
        val hashA = RomIdentity.calculateSha256(romABytes)
        val fileA = File(testBaseDir, "rom_a.gba").apply { writeBytes(romABytes) }
        val identityA = RomIdentity.create(hashA, "ROM A")

        // Start with ROM A active
        sessionManager.switchRomFile(fileA, emptyList(), "ROM A")
        assertEquals(identityA, saveStateManager.activeIdentity)
        val eventsBefore = testBridge.events.size

        // Incoming invalid empty ROM
        val emptyRom = File(testBaseDir, "empty.gba").apply { writeBytes(ByteArray(0)) }
        val res = sessionManager.switchRomFile(emptyRom, emptyList(), "Empty ROM")

        assertTrue(res is SwitchResult.Failure)
        assertEquals("Active identity must remain untouched", identityA, saveStateManager.activeIdentity)
        assertEquals("Core events must not have executed unload or load", eventsBefore, testBridge.events.size)
    }

    @Test
    fun test43_equalSizedDifferentSafFile_reportsOutOfSync() {
        val safDir = File(testBaseDir, "saf_root").apply { mkdirs() }
        val safMirror = TestSafMirror(isConfiguredValue = true, safDir = safDir)
        val customManager = SaveStateManager(
            customBaseDir = testBaseDir,
            customSafStore = safMirror,
            coreBridge = testBridge
        )

        val identity = RomIdentity.create(HASH_A, "Pokemon Ruby")
        val canonicalFile = customManager.getCanonicalFile(identity, "battery.sav")
        canonicalFile.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x11.toByte() })

        // Write equal sized but different content to SAF mirror
        val safRomDir = File(safDir, identity.storageKey).apply { mkdirs() }
        val safFile = File(safRomDir, "battery.sav")
        safFile.writeBytes(ByteArray(SRAM_SIZE_128K) { 0x22.toByte() })

        val statusBefore = safMirror.checkMirrorStatus(identity, "battery.sav", canonicalFile)
        assertEquals("Equal sized file with different content must be reported as OUT_OF_SYNC", MirrorStatus.OUT_OF_SYNC, statusBefore)

        // After syncing, status should be IN_SYNC
        safMirror.mirrorFile(identity, "battery.sav", canonicalFile)
        val statusAfter = safMirror.checkMirrorStatus(identity, "battery.sav", canonicalFile)
        assertEquals(MirrorStatus.IN_SYNC, statusAfter)
    }

    @Test
    fun test44_legacyMigration_cannotOverwriteAnotherRomAssignment() {
        val legacyDir = File(testBaseDir, "legacy_saves").apply { mkdirs() }
        val legacyFile = File(legacyDir, "firered.sav").apply { writeBytes(ByteArray(1024) { 0x33.toByte() }) }
        val catalog = LegacySaveCatalog(customLegacyDir = legacyDir)

        val identityA = RomIdentity.create(HASH_A, "Pokemon FireRed")
        val identityB = RomIdentity.create(HASH_B, "Pokemon LeafGreen")

        val candidate = catalog.discoverCandidates()[0]

        // First assignment to identityA succeeds
        val canonicalDirA = saveStateManager.getCanonicalRomDir(identityA)
        val assignedA = catalog.assignCandidateToRom(candidate, identityA, canonicalDirA)
        assertEquals(MigrationResult.SUCCESS, assignedA)
        assertEquals(identityA.sha256, catalog.getAssignedRomHash(legacyFile))

        // Attempt second assignment of the same source file to identityB must fail
        val canonicalDirB = saveStateManager.getCanonicalRomDir(identityB)
        val assignedB = catalog.assignCandidateToRom(candidate, identityB, canonicalDirB)
        assertEquals(MigrationResult.FAILURE, assignedB)
        assertEquals(identityA.sha256, catalog.getAssignedRomHash(legacyFile))
        val canonicalFileB = File(canonicalDirB, "battery.sav")
        assertFalse("ROM B canonical destination must remain untouched", canonicalFileB.exists())
    }

    @Test
    fun test45_legacyMigration_origBakFailure_failsClosed() {
        val legacyDir = File(testBaseDir, "readonly_legacy").apply { mkdirs() }
        val legacyFile = File(legacyDir, "save.sav").apply { writeBytes(ByteArray(1024) { 0x77.toByte() }) }

        // Create a non-empty directory collision where .orig.bak should be so copyTo cannot overwrite it
        val origBakCollision = File(legacyDir, "save.sav.orig.bak").apply {
            mkdirs()
            File(this, "blocker.txt").writeBytes(ByteArray(10))
        }

        val catalog = LegacySaveCatalog(customLegacyDir = legacyDir)
        val identity = RomIdentity.create(HASH_A, "Pokemon FireRed")
        val candidate = LegacyCandidate(
            sourceFile = legacyFile,
            baseName = legacyFile.nameWithoutExtension,
            suggestedTitle = "Pokemon FireRed",
            targetFileName = "battery.sav",
            sizeBytes = legacyFile.length()
        )

        val canonicalDir = saveStateManager.getCanonicalRomDir(identity)
        val assigned = catalog.assignCandidateToRom(candidate, identity, canonicalDir)

        assertEquals(MigrationResult.FAILURE, assigned)
        val destFile = File(canonicalDir, "battery.sav")
        assertFalse("Canonical destination must not be created on backup failure", destFile.exists())
        assertFalse("Legacy source must not be recorded as assigned", catalog.isSourceAssigned(legacyFile))

        origBakCollision.deleteRecursively()
    }

    // ---------------------------------------------------------
    // Scenarios 46 - 56: Final Save / Storage Hardening Pass
    // ---------------------------------------------------------

    @Test
    fun test46_romSwitch_continuousCoreLock_preventsInterleavedStepFrame() = runBlocking {
        val cacheDir = File(testBaseDir, "rom_cache").apply { mkdirs() }
        val sessionManager = RomSessionManager(
            saveStateManager = saveStateManager,
            customRomCacheDir = cacheDir,
            coreBridge = testBridge
        )

        val romABytes = ByteArray(1024) { 0x11.toByte() }
        val hashA = RomIdentity.calculateSha256(romABytes)
        File(cacheDir, "$hashA.gba").writeBytes(romABytes)
        saveStateManager.activeIdentity = RomIdentity.create(hashA, "ROM A")

        val romBBytes = ByteArray(1024) { 0x22.toByte() }
        val hashB = RomIdentity.calculateSha256(romBBytes)
        val fileB = File(testBaseDir, "rom_b.gba").apply { writeBytes(romBBytes) }
        val identityB = RomIdentity.create(hashB, "ROM B")
        saveStateManager.getCanonicalFile(identityB, "battery.sav").writeBytes(ByteArray(SRAM_SIZE_128K) { 0x33.toByte() })

        var inSwitchTransaction = false
        var steppedDuringSwitch = false

        val instrumentedBridge = object : LibretroCoreBridge by testBridge {
            override fun flushSaveRam(savePath: String): Boolean {
                inSwitchTransaction = true
                return testBridge.flushSaveRam(savePath)
            }

            override fun loadSaveRam(savePath: String): Boolean {
                val res = testBridge.loadSaveRam(savePath)
                if (savePath.contains(identityB.storageKey)) {
                    inSwitchTransaction = false
                }
                return res
            }

            override fun stepFrame(): Boolean {
                if (inSwitchTransaction) {
                    steppedDuringSwitch = true
                }
                return testBridge.stepFrame()
            }
        }

        sessionManager.coreBridge = instrumentedBridge
        saveStateManager.coreBridge = instrumentedBridge

        val stepJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                sessionManager.coreCoordinator.stepFrame()
                Thread.yield()
            }
        }

        try {
            val res = sessionManager.switchRomFile(fileB, emptyList(), "ROM B")
            assertTrue("ROM switch must succeed", res is SwitchResult.Success)
            assertFalse("stepFrame must never execute during the continuous switch transaction", steppedDuringSwitch)
        } finally {
            stepJob.cancel()
        }
    }

    @Test
    fun test47_sramImport_continuousCoreLock_preventsInterleavedExecution() = runBlocking {
        val identity = RomIdentity.create(HASH_A, "Test Game")
        saveStateManager.setActiveGame(identity)

        val expectedSize = SRAM_SIZE_128K
        val candidateBytes = ByteArray(expectedSize) { 0x42.toByte() }
        val input = ByteArrayInputStream(candidateBytes)

        var exclusiveLockedDuringBatteryWrite = false
        // Hook writeBytes to verify lock is held during atomic write
        AtomicSaveFile.syncHook = {
            if (saveStateManager.coreCoordinator.isExclusiveLocked) {
                exclusiveLockedDuringBatteryWrite = true
            }
        }

        try {
            val imported = saveStateManager.importBatterySave(identity, input)
            assertTrue("Import must succeed", imported)
            assertTrue("Core exclusive lock must be held continuously throughout candidate write", exclusiveLockedDuringBatteryWrite)
            assertArrayEquals(candidateBytes, testBridge.currentSramBytes)
        } finally {
            AtomicSaveFile.syncHook = null
        }
    }

    @Test
    fun test48_atomicSaveFile_fsyncFailure_failsClosedAndPreservesCanonical() {
        val targetFile = File(testBaseDir, "canonical.sav")
        val originalBytes = ByteArray(1024) { 0x11.toByte() }
        assertTrue(AtomicSaveFile.writeBytes(targetFile, originalBytes))
        assertArrayEquals(originalBytes, targetFile.readBytes())

        val newBytes = ByteArray(1024) { 0x22.toByte() }
        // Inject fsync failure
        AtomicSaveFile.syncHook = {
            throw java.io.IOException("Simulated disk I/O error during fsync")
        }

        try {
            val writeSuccess = AtomicSaveFile.writeBytes(targetFile, newBytes)
            assertFalse("Write must fail closed when fsync fails", writeSuccess)
            assertArrayEquals("Canonical file must remain untouched with original bytes", originalBytes, targetFile.readBytes())
            assertFalse("Temp file must be cleaned up", File(testBaseDir, "canonical.sav.tmp").exists())
        } finally {
            AtomicSaveFile.syncHook = null
        }
    }

    @Test
    fun test49_atomicSaveFile_backupCreationFailure_abortsCanonicalReplacement() {
        val targetFile = File(testBaseDir, "canonical.sav")
        val originalBytes = ByteArray(1024) { 0x11.toByte() }
        assertTrue(AtomicSaveFile.writeBytes(targetFile, originalBytes))

        val newBytes = ByteArray(1024) { 0x22.toByte() }
        // Inject backup failure
        AtomicSaveFile.backupHook = { _, _ -> false }

        try {
            val writeSuccess = AtomicSaveFile.writeBytes(targetFile, newBytes)
            assertFalse("Atomic write must abort when backup creation fails", writeSuccess)
            assertArrayEquals("Canonical file must remain unchanged", originalBytes, targetFile.readBytes())
            assertFalse("Temp file must be cleaned up", File(testBaseDir, "canonical.sav.tmp").exists())
        } finally {
            AtomicSaveFile.backupHook = null
        }
    }

    @Test
    fun test50_atomicSaveFile_truncatedCanonicalWithMatchingBak_recoversBak() {
        val targetFile = File(testBaseDir, "canonical.sav")
        val bakFile = File(testBaseDir, "canonical.sav.bak")
        val expectedSize = SRAM_SIZE_128K.toLong()

        // Write truncated 500-byte canonical file
        targetFile.writeBytes(ByteArray(500) { 0x99.toByte() })
        // Write full expected size .bak file
        val goodBytes = ByteArray(expectedSize.toInt()) { 0x77.toByte() }
        bakFile.writeBytes(goodBytes)

        val recovered = AtomicSaveFile.recoverInterrupted(targetFile, expectedSize)
        assertTrue("Interrupted recovery must succeed when matching .bak is present", recovered)
        assertEquals("Canonical file must be restored to expected size", expectedSize, targetFile.length())
        assertArrayEquals("Canonical file content must match .bak", goodBytes, targetFile.readBytes())
    }

    @Test
    fun test51_atomicSaveFile_validCanonicalWithStaleBak_canonicalRemainsAuthoritative() {
        val targetFile = File(testBaseDir, "canonical.sav")
        val bakFile = File(testBaseDir, "canonical.sav.bak")
        val tmpFile = File(testBaseDir, "canonical.sav.tmp")
        val expectedSize = SRAM_SIZE_128K.toLong()

        // Valid canonical file
        val canonicalBytes = ByteArray(expectedSize.toInt()) { 0xAA.toByte() }
        targetFile.writeBytes(canonicalBytes)

        // Stale .bak file with different bytes
        val staleBytes = ByteArray(expectedSize.toInt()) { 0xBB.toByte() }
        bakFile.writeBytes(staleBytes)

        // Stray leftover .tmp file
        tmpFile.writeBytes(ByteArray(100) { 0x00.toByte() })

        val recovered = AtomicSaveFile.recoverInterrupted(targetFile, expectedSize)
        assertTrue(recovered)
        assertArrayEquals("Valid canonical must remain untouched and authoritative", canonicalBytes, targetFile.readBytes())
        assertFalse("Leftover .tmp file must be cleaned up", tmpFile.exists())
    }

    @Test
    fun test52_safMirrorAsync_reportsPendingRatherThanPrematureInSync() {
        val safDir = File(testBaseDir, "saf_root").apply { mkdirs() }
        val safMirror = TestSafMirror(isConfiguredValue = true, safDir = safDir)
        val mgr = SaveStateManager(
            customBaseDir = testBaseDir,
            customSafStore = safMirror,
            coreBridge = testBridge
        )

        val identity = RomIdentity.create(HASH_A, "Test Game")
        mgr.setActiveGame(identity)

        val res = mgr.flushBatterySave(identity, mirrorSafAsync = true)
        assertTrue(res is SaveWriteResult.Success)
        val success = res as SaveWriteResult.Success
        assertEquals("Async mirror must report PENDING immediately rather than premature IN_SYNC", MirrorStatus.PENDING, success.mirrorStatus)

        // Canonical metadata file should also record PENDING immediately
        val metaFile = mgr.getCanonicalFile(identity, "metadata.json")
        assertTrue(metaFile.exists())
        val meta = com.dualdex.emulator.storage.RomSaveMetadata.fromJson(metaFile.readText())
        assertNotNull(meta)
        assertEquals(MirrorStatus.PENDING, meta?.mirrorStatus)
    }

    @Test
    fun test53_safMirrorAsync_failedMirror_reportsFailedOrOutOfSync() = runBlocking {
        val safDir = File(testBaseDir, "saf_root").apply { mkdirs() }
        val safMirror = TestSafMirror(isConfiguredValue = true, simulateWriteFailure = true, safDir = safDir)
        val mgr = SaveStateManager(
            customBaseDir = testBaseDir,
            customSafStore = safMirror,
            coreBridge = testBridge
        )

        val identity = RomIdentity.create(HASH_A, "Test Game")
        mgr.setActiveGame(identity)

        val res = mgr.flushBatterySave(identity, mirrorSafAsync = true)
        assertTrue(res is SaveWriteResult.Success)
        assertEquals(MirrorStatus.PENDING, (res as SaveWriteResult.Success).mirrorStatus)

        // Wait for background mirror coroutine to complete and update metadata
        var finalMetaStatus: MirrorStatus? = null
        for (i in 1..20) {
            val metaFile = mgr.getCanonicalFile(identity, "metadata.json")
            if (metaFile.exists()) {
                val meta = com.dualdex.emulator.storage.RomSaveMetadata.fromJson(metaFile.readText())
                if (meta != null && meta.mirrorStatus != MirrorStatus.PENDING) {
                    finalMetaStatus = meta.mirrorStatus
                    break
                }
            }
            delay(20)
        }

        assertNotNull(finalMetaStatus)
        assertTrue("Final mirror status after failure must be FAILED or OUT_OF_SYNC", finalMetaStatus == MirrorStatus.FAILED || finalMetaStatus == MirrorStatus.OUT_OF_SYNC)
        assertFalse("Final mirror status must NEVER be IN_SYNC after failure", finalMetaStatus == MirrorStatus.IN_SYNC)
    }

    @Test
    fun test54_flushBatterySave_asyncDoesNotBlockOrHashSync() {
        var checkMirrorStatusCallCount = 0
        val safMirror = object : SafMirrorStore() {
            override fun isSafConfigured(): Boolean = true
            override fun checkMirrorStatus(identity: RomIdentity, fileName: String, canonicalFile: File): MirrorStatus {
                checkMirrorStatusCallCount++
                return MirrorStatus.IN_SYNC
            }
            override fun mirrorFile(identity: RomIdentity, fileName: String, canonicalFile: File): MirrorStatus {
                return MirrorStatus.IN_SYNC
            }
        }

        val mgr = SaveStateManager(
            customBaseDir = testBaseDir,
            customSafStore = safMirror,
            coreBridge = testBridge
        )
        val identity = RomIdentity.create(HASH_A, "Test Game")
        mgr.setActiveGame(identity)

        checkMirrorStatusCallCount = 0
        val res = mgr.flushBatterySave(identity, mirrorSafAsync = true)
        assertTrue(res is SaveWriteResult.Success)
        assertEquals(0, checkMirrorStatusCallCount)
        assertEquals(MirrorStatus.PENDING, (res as SaveWriteResult.Success).mirrorStatus)
    }

    @Test
    fun test55_safMirror_missingCanonicalWithSafDoc_reportsOutOfSync() {
        val safDir = File(testBaseDir, "saf_root").apply { mkdirs() }
        val safMirror = TestSafMirror(isConfiguredValue = true, safDir = safDir)

        val identity = RomIdentity.create(HASH_A, "Test Game")
        val canonicalFile = File(testBaseDir, "missing_canonical.sav")
        assertFalse(canonicalFile.exists())

        // 1. When neither canonical nor SAF file exists -> IN_SYNC
        val statusBothMissing = safMirror.checkMirrorStatus(identity, "battery.sav", canonicalFile)
        assertEquals(MirrorStatus.IN_SYNC, statusBothMissing)

        // 2. When SAF file exists but canonical is missing -> OUT_OF_SYNC
        val safRomDir = File(safDir, identity.storageKey).apply { mkdirs() }
        val safDoc = File(safRomDir, "battery.sav").apply { writeBytes(ByteArray(100) { 0x33.toByte() }) }
        assertTrue(safDoc.exists())

        val statusSafOnly = safMirror.checkMirrorStatus(identity, "battery.sav", canonicalFile)
        assertEquals(MirrorStatus.OUT_OF_SYNC, statusSafOnly)
    }

    @Test
    fun test56_legacyMigration_sourceCleanupFailure_reportsSuccessSourceCleanupFailed() {
        val legacyDir = File(testBaseDir, "legacy_saves").apply { mkdirs() }
        val legacyFile = File(legacyDir, "Pokemon_Ruby.sav").apply {
            writeBytes(ByteArray(SRAM_SIZE_128K) { 0x88.toByte() })
        }

        // Cause source cleanup to fail by creating a directory collision where .migrated.bak would be created
        val migratedBakCollision = File(legacyDir, "Pokemon_Ruby.sav.migrated.bak").apply {
            mkdirs()
            File(this, "locked_child.txt").writeBytes(ByteArray(10))
        }

        val catalog = LegacySaveCatalog(customLegacyDir = legacyDir)
        val identity = RomIdentity.create(HASH_A, "Pokemon Ruby")
        val candidate = LegacyCandidate(
            sourceFile = legacyFile,
            baseName = legacyFile.nameWithoutExtension,
            suggestedTitle = "Pokemon Ruby",
            targetFileName = "battery.sav",
            sizeBytes = legacyFile.length()
        )

        val canonicalDir = saveStateManager.getCanonicalRomDir(identity)
        val res = catalog.assignCandidateToRom(candidate, identity, canonicalDir)

        assertEquals("Should report SUCCESS_SOURCE_CLEANUP_FAILED when destination succeeds but source cleanup fails", MigrationResult.SUCCESS_SOURCE_CLEANUP_FAILED, res)
        assertTrue("isSuccess should be true", res.isSuccess)

        // Verify destination file was committed and verified
        val destFile = File(canonicalDir, "battery.sav")
        assertTrue("Canonical destination file must exist", destFile.exists())
        assertEquals(SRAM_SIZE_128K.toLong(), destFile.length())

        // Verify .orig.bak was created
        val origBak = File(legacyDir, "Pokemon_Ruby.sav.orig.bak")
        assertTrue("Original backup must exist", origBak.exists())

        // Verify association was recorded
        assertTrue("Source file must be marked as assigned", catalog.isSourceAssigned(legacyFile))
        assertEquals(identity.sha256, catalog.getAssignedRomHash(legacyFile))

        migratedBakCollision.deleteRecursively()
    }
}
