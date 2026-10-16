package com.dualdex.emulator

import com.dualdex.emulator.backup.CloudSnapshotArchive
import com.dualdex.emulator.backup.SaveBackupInventory
import com.dualdex.emulator.backup.QuotaAwareBackupAgent
import com.dualdex.emulator.backup.QuotaBackupSelection
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Exercises the same cloud operation called by onFullBackup; does not simulate a transport. */
class CloudBackupFailureTest {
    private val root = Files.createTempDirectory("dualdex-cloud-failure").toFile()
    private val cache = File(root, "cache")
    private val files = File(root, "files").apply { mkdirs() }
    private val hash = "a".repeat(64)
    private var emissions = 0
    @After fun cleanup() { root.deleteRecursively() }
    private fun validSources(): File {
        val dir = File(files, "saves_v2/$hash").apply { mkdirs() }
        File(dir, "battery.sav").writeText("synthetic battery")
        File(dir, "metadata.json").writeText("""{"sha256":"$hash","displayName":"Synthetic"}""")
        return dir
    }
    private fun refuse(quota: Long = QuotaBackupSelection.MAX_PAYLOAD_BYTES) {
        try {
            QuotaAwareBackupAgent.emitCloudSnapshot(files, cache, quota, emptyMap()) { emissions++ }
            fail("Refused operation must throw instead of completing normally")
        } catch (_: IOException) { }
        assertEquals(0, emissions)
    }

    @Test fun inventoryStampsUseTheArchiveFileTimestampView() {
        val dir = validSources()
        val battery = File(dir, "battery.sav")
        assertTrue(battery.setLastModified(1_700_000_000_123L))
        val inventory = SaveBackupInventory(files).scan()
        assertTrue(inventory.complete)
        assertEquals(battery.lastModified(), inventory.sourceStamps["saves_v2/$hash/battery.sav"]?.modifiedAtMillis)
        assertEquals(QuotaBackupSelection.Verdict.SUCCESS,
            QuotaAwareBackupAgent.emitCloudSnapshot(files, cache, QuotaBackupSelection.MAX_PAYLOAD_BYTES, emptyMap()) {})
    }

    @Test fun zeroQuotaAbortsAndPreservesPreviousLocalSnapshot() {
        validSources()
        val previous = File(files, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH).apply {
            parentFile!!.mkdirs(); writeText("previous local snapshot")
        }
        refuse(0)
        assertEquals("previous local snapshot", previous.readText())
    }

    @Test fun emptyDatasetCannotCompleteAsAnEmptySuccessfulBackup() { refuse() }

    @Test fun invalidEssentialMetadataAbortsWithoutEmittingOrChangingBattery() {
        val dir = validSources()
        File(dir, "metadata.json").writeText("{")
        refuse()
        assertEquals("synthetic battery", File(dir, "battery.sav").readText())
    }

    @Test fun snapshotConstructionFailureAbortsWithoutEmission() {
        validSources()
        cache.writeText("blocks working directory")
        refuse()
        assertFalse(File(files, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH).exists())
    }

    @Test fun successRequiresEmissionOfACompleteValidatedArchive() {
        validSources()
        val verdict = QuotaAwareBackupAgent.emitCloudSnapshot(files, cache,
            QuotaBackupSelection.MAX_PAYLOAD_BYTES, emptyMap()) {
            emissions++
            assertEquals(2, CloudSnapshotArchive.validate(it)?.entries?.size)
        }
        assertEquals(QuotaBackupSelection.Verdict.SUCCESS, verdict)
        assertEquals(1, emissions)
    }

    @Test fun emissionFailurePropagatesInsteadOfReturningSuccess() {
        validSources()
        try {
            QuotaAwareBackupAgent.emitCloudSnapshot(files, cache, QuotaBackupSelection.MAX_PAYLOAD_BYTES, emptyMap()) {
                emissions++
                throw IOException("injected output failure")
            }
            fail("Emission failure must propagate")
        } catch (failure: IOException) { assertEquals("injected output failure", failure.message) }
        assertEquals(1, emissions)
    }
}
