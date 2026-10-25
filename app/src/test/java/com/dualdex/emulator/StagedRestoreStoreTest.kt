package com.dualdex.emulator

import com.dualdex.emulator.backup.BackupFileTreePolicy
import com.dualdex.emulator.backup.CloudSnapshotArchive
import com.dualdex.emulator.backup.QuotaBackupSelection
import com.dualdex.emulator.backup.StagedRestoreStore
import com.dualdex.emulator.backup.StagedRestoreStore.Decision.*
import com.dualdex.emulator.backup.StagedRestoreStore.Result.*
import com.dualdex.emulator.storage.AtomicSaveFile
import com.dualdex.emulator.storage.SaveStateFiles
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Synthetic bytes only. No Android transport or native-core compatibility is implied. */
class StagedRestoreStoreTest {
    private val root = Files.createTempDirectory("dualdex-restore").toFile()
    private val store = StagedRestoreStore(root)
    private val hash = "a".repeat(64)
    private val path = "saves_v2/$hash/battery.sav"
    private fun put(base: File, path: String, text: String, time: Long = 1000): File = File(base, path).apply {
        parentFile!!.mkdirs(); writeText(text); check(setLastModified(time))
    }
    private fun pending() = File(root, "restore_staging/session-${UUID.randomUUID()}")
    private fun ready(text: String = "restored", target: String = path): File = pending().let {
        put(it, target, text); store.complete(it)
    }
    private fun review(path: String = this.path) = store.review().filterNot { it.retainedLocal }
        .flatMap { it.entries }.single { it.source.path == path }
    private fun version(file: File) = checkNotNull(AtomicSaveFile.version(file, BackupFileTreePolicy.MAX_D2D_FILE_BYTES))
    private fun mustFail(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IOException) { }
    }
    @After fun cleanup() { AtomicSaveFile.syncHook = null; root.deleteRecursively() }

    @Test fun onlyCompleteSessionsSurviveRestartAsReviewableWithoutCanonicalWrites() {
        val pending = pending()
        put(pending, path, "restored")
        assertTrue(store.review().isEmpty())
        val published = store.complete(pending)
        assertFalse(pending.exists())
        assertEquals(1, StagedRestoreStore(root).review().single().entries.size)
        assertTrue(File(published, path).exists())
        assertFalse(File(root, "saves_v2").exists())
    }

    @Test fun malformedMetadataCorruptArchivesAndMixedFormatsRemainIsolated() {
        val bad = pending()
        put(bad, path, "usable battery")
        put(bad, "saves_v2/$hash/metadata.json", "{")
        mustFail { store.complete(bad) }
        val corrupt = pending()
        put(corrupt, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH, "not a zip")
        mustFail { store.complete(corrupt) }
        val mixed = cloud()
        put(mixed, path, "unexpected tree")
        mustFail { store.complete(mixed) }
        assertTrue(store.review().isEmpty())
        assertFalse(File(root, path).exists())
        assertTrue(File(bad, path).exists())
    }

    @Test fun missingTargetRequiresExplicitImportAndRetainsSource() {
        val ready = ready()
        val review = review()
        assertEquals(KEPT_LOCAL, store.import(review, KEEP_LOCAL))
        assertFalse(File(root, path).exists())
        assertEquals(IMPORTED, store.import(review, IMPORT_MISSING))
        assertEquals("restored", File(root, path).readText())
        assertEquals("restored", File(ready, path).readText())
        assertEquals(ALREADY_PRESENT, store.import(review(), REPLACE_LOCAL))
    }

    @Test fun everyExistingLocalFileRequiresReplacementRegardlessOfDates() {
        ready()
        for (time in listOf(500L, 1000L, 2000L)) {
            val local = put(root, path, "local-$time", time)
            val review = review()
            assertEquals(REVIEW_AGAIN, store.import(review, IMPORT_MISSING))
            assertEquals(KEPT_LOCAL, store.import(review, KEEP_LOCAL))
            assertEquals("local-$time", local.readText())
        }
    }

    @Test fun explicitReplacementRetainsLocalAndDoesNotRotateExistingBackup() {
        val ready = ready()
        put(root, path, "newer-local", 2000)
        val backup = put(root, "$path.bak", "previous-recovery", 900)
        assertEquals(IMPORTED, store.import(review(), REPLACE_LOCAL))
        assertEquals("restored", File(root, path).readText())
        assertEquals("previous-recovery", backup.readText())
        val retained = store.review().single { it.retainedLocal }
        assertEquals("newer-local", File(root, "restore_staging/${retained.id}/$path").readText())
        assertEquals("restored", File(ready, path).readText())
        assertEquals(IMPORTED, store.import(retained.entries.single(), REPLACE_LOCAL))
        assertEquals("newer-local", File(root, path).readText())
        assertEquals(2, store.review().count { it.retainedLocal })
    }

    @Test fun sameSizeSameTimeLocalMutationInvalidatesReview() {
        ready()
        val local = put(root, path, "first", 2000)
        val review = review()
        put(root, path, "other", 2000)
        assertEquals(REVIEW_AGAIN, store.import(review, REPLACE_LOCAL))
        assertEquals("other", local.readText())
    }

    @Test fun destinationCreatedOrDeletedSinceReviewCannotBeOverwritten() {
        ready()
        val missing = review()
        val local = put(root, path, "appeared")
        assertEquals(REVIEW_AGAIN, store.import(missing, IMPORT_MISSING))
        val existing = review()
        local.delete()
        assertEquals(REVIEW_AGAIN, store.import(existing, REPLACE_LOCAL))
        assertFalse(local.exists())
    }

    @Test fun sameSizeSameTimeSourceMutationInvalidatesReview() {
        val ready = ready("first")
        val review = review()
        put(ready, path, "other")
        assertEquals(REVIEW_AGAIN, store.import(review, IMPORT_MISSING))
        assertFalse(File(root, path).exists())
    }

    @Test fun mutationsAtCommitBoundaryStopReplacement() {
        val ready = ready("first")
        val local = put(root, path, "local", 2000)
        assertEquals(FAILED, store.import(review(), REPLACE_LOCAL) { put(root, path, "raced", 2000) })
        assertEquals("raced", local.readText())
        assertEquals(FAILED, store.import(review(), REPLACE_LOCAL) { put(ready, path, "other") })
        assertEquals("raced", local.readText())
        assertEquals("other", File(ready, path).readText())
    }

    @Test fun interruptedCopyAndRetentionFailurePreserveLocalAndAllowRetry() {
        ready()
        val local = put(root, path, "local")
        val review = review()
        AtomicSaveFile.syncHook = { throw IOException("disk full") }
        assertEquals(FAILED, store.import(review, REPLACE_LOCAL))
        assertEquals("local", local.readText())
        var syncs = 0
        AtomicSaveFile.syncHook = { if (++syncs == 2) throw IOException("retention failed") else it.fd.sync() }
        assertEquals(FAILED, store.import(review, REPLACE_LOCAL))
        assertEquals("local", local.readText())
        AtomicSaveFile.syncHook = null
        assertEquals(IMPORTED, store.import(review, REPLACE_LOCAL))
        assertTrue(local.parentFile!!.listFiles()!!.none { it.name.startsWith(".battery.sav.restore-") })
    }

    @Test fun unsupportedAtomicMoveHasNoFallbackAndKeepsBothCopies() {
        val source = put(root, "source", "restored")
        val local = put(root, "target", "local")
        val retained = File(root, "retained")
        mustFail {
            AtomicSaveFile.commitRestore(source, local, version(source), version(local), retained, {}, { _, _ ->
                throw AtomicMoveNotSupportedException("source", "target", "injected")
            })
        }
        assertEquals("local", local.readText())
        assertEquals("local", retained.readText())
        assertEquals("restored", source.readText())
    }

    @Test fun crashAfterRetentionBeforeCommitLeavesCanonicalAndReviewableCopies() {
        ready()
        val local = put(root, path, "local")
        assertEquals(FAILED, store.import(review(), REPLACE_LOCAL) { throw IOException("interrupted") })
        assertEquals("local", local.readText())
        assertEquals(2, StagedRestoreStore(root).review().size)
        assertTrue(store.review().all { it.error == null })
        assertEquals(IMPORTED, store.import(review(), REPLACE_LOCAL))
    }

    @Test fun importsBlockEvenFutureDatedAutoResumeWithoutDeletingStates() {
        val future = System.currentTimeMillis() + 86400000L
        val dir = File(root, "saves_v2/$hash")
        val state = put(root, "saves_v2/$hash/auto_resume.state", "existing state", future)
        ready()
        assertEquals(IMPORTED, store.import(review(), IMPORT_MISSING))
        val barrier = File(dir, "resume_barrier")
        assertTrue(barrier.lastModified() >= future)
        assertNull(SaveStateFiles.newestResumeState(dir, barrier.lastModified()))
        assertEquals("existing state", state.readText())
        val manager = SaveStateManager(customBaseDir = root, coreBridge = RomSaveIntegrityTest.TestCoreBridge())
        val rom = RomIdentity.create(hash, "Synthetic")
        manager.setActiveGame(rom)
        assertTrue(manager.saveAutoResume(rom))
        assertEquals(SaveStateFiles.AUTO_RESUME, manager.loadNewestResumeState(rom))
        assertTrue(manager.saveSlot(rom, 2))
        assertEquals("slot_2.state", manager.loadNewestResumeState(rom))
    }

    @Test fun stateImportIsManualAndLegacyAssignmentsAreReferenceOnly() {
        val session = pending()
        val statePath = "saves_v2/$hash/slot_1.state"
        put(session, statePath, "synthetic state")
        put(session, "saves_v2/$hash/resume_barrier", "1")
        put(session, BackupFileTreePolicy.LEGACY_CATALOG_PATH, "<map/>")
        put(session, "saves/SameTitle.sav", "unassigned")
        store.complete(session)
        val entries = store.review().single().entries
        assertNull(entries.single { it.source.path.endsWith("resume_barrier") }.source.target)
        assertNull(entries.single { it.source.path == BackupFileTreePolicy.LEGACY_CATALOG_PATH }.source.target)
        val legacy = entries.single { it.source.path == "saves/SameTitle.sav" }
        assertNull(legacy.source.romHash)
        assertEquals("saves/SameTitle.sav", legacy.source.target)
        assertEquals(IMPORTED, store.import(review(statePath), IMPORT_MISSING))
        val dir = File(root, "saves_v2/$hash")
        assertNull(SaveStateFiles.newestResumeState(dir, File(dir, "resume_barrier").lastModified()))
        assertFalse(File(root, path).exists())
    }

    @Test fun sameTitleNeverAssociatesDifferentRomHashes() {
        val session = pending()
        val other = "b".repeat(64)
        for (id in listOf(hash, other)) {
            put(session, "saves_v2/$id/metadata.json", """{"sha256":"$id","displayName":"Same title"}""")
            put(session, "saves_v2/$id/battery.sav", id)
        }
        store.complete(session)
        assertEquals(IMPORTED, store.import(review(), IMPORT_MISSING))
        assertEquals(hash, File(root, path).readText())
        assertFalse(File(root, "saves_v2/$other").exists())
    }

    @Test fun sourceAndTargetSymlinksAndForgedReviewPathsAreRejected() {
        val session = pending()
        val source = put(session, path, "restored")
        val outside = put(root, "outside", "untouched")
        source.delete(); Files.createSymbolicLink(source.toPath(), outside.toPath())
        mustFail { store.complete(session) }
        session.deleteRecursively()
        ready()
        val review = review()
        File(root, "saves_v2").mkdirs()
        val alternate = File(root, "alternate").apply { mkdirs() }
        Files.createSymbolicLink(File(root, "saves_v2/$hash").toPath(), alternate.toPath())
        assertEquals(FAILED, store.import(review, IMPORT_MISSING))
        assertEquals("untouched", outside.readText())
        assertEquals(FAILED, store.import(review.copy(session = "../../outside"), IMPORT_MISSING))
    }

    private fun cloud(): File {
        val app = File(root, "cloud-source-${UUID.randomUUID()}")
        val sources = listOf(put(app, path, "cloud-save"),
            put(app, "saves_v2/$hash/metadata.json", """{"sha256":"$hash","displayName":"Cloud"}"""))
        val entries = sources.map { source ->
            val version = version(source)
            val relative = app.toPath().relativize(source.toPath()).toString()
            CloudSnapshotArchive.Entry(source, QuotaBackupSelection.SourceStamp(relative, version.size, version.modified, version.sha256),
                hash, if (source.name == "battery.sav") "CANONICAL_BATTERY" else "SAVE_METADATA")
        }
        val session = pending()
        assertNotNull(CloudSnapshotArchive.build(entries, app, File(root, "cache"), File(session, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH)))
        return session
    }

    @Test fun verifiedCloudImportRetainsArchiveAndRequiresExplicitLocalReplacement() {
        val ready = store.complete(cloud())
        val local = put(root, path, "newer local")
        val review = review()
        assertEquals(0L, review.source.version.modified)
        assertEquals(REVIEW_AGAIN, store.import(review, IMPORT_MISSING))
        assertEquals(IMPORTED, store.import(review, REPLACE_LOCAL))
        assertEquals("cloud-save", local.readText())
        assertNotNull(CloudSnapshotArchive.validate(File(ready, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH)))
        assertTrue(store.review().any { it.retainedLocal })
    }

    @Test fun cloudLegacyBindingUsesVerifiedHashAndCanonicalDestination() {
        val app = File(root, "legacy-source")
        val legacy = put(app, "saves/SameTitle.sav", "bound save")
        val metadata = put(app, "saves_v2/$hash/metadata.json", """{"sha256":"$hash","displayName":"SameTitle"}""")
        val binding = checkNotNull(QuotaBackupSelection.portableLegacyBinding(
            app.absolutePath, legacy.absolutePath, version(legacy).sha256, hash, "battery.sav"))
        val entries = listOf(legacy, metadata).map { source ->
            val stamp = version(source)
            val relative = app.toPath().relativize(source.toPath()).toString()
            CloudSnapshotArchive.Entry(source, QuotaBackupSelection.SourceStamp(relative, stamp.size, stamp.modified, stamp.sha256),
                hash, if (source == legacy) "VERIFIED_LEGACY_BATTERY" else "SAVE_METADATA", if (source == legacy) binding else null)
        }
        val session = pending()
        assertNotNull(CloudSnapshotArchive.build(entries, app, File(root, "cache"), File(session, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH)))
        store.complete(session)
        val review = review("saves/SameTitle.sav")
        assertEquals(path, review.source.target)
        assertEquals(hash, review.source.romHash)
        assertEquals(IMPORTED, store.import(review, IMPORT_MISSING))
        assertEquals("bound save", File(root, path).readText())
        assertFalse(File(root, "saves/SameTitle.sav").exists())
    }

    @Test fun evenAnEmptyLocalFileRequiresExplicitReplacement() {
        ready()
        val local = put(root, path, "")
        val review = review()
        assertEquals(REVIEW_AGAIN, store.import(review, IMPORT_MISSING))
        assertEquals(0L, local.length())
        assertEquals(IMPORTED, store.import(review, REPLACE_LOCAL))
        assertEquals("restored", local.readText())
    }

    @Test fun cloudMutationAtCommitBoundaryCannotChangeLocal() {
        val ready = store.complete(cloud())
        val archive = File(ready, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH)
        val local = put(root, path, "local")
        assertEquals(FAILED, store.import(review(), REPLACE_LOCAL) { archive.appendText("mutation") })
        assertEquals("local", local.readText())
    }

    @Test fun loadedOrRunningGameRefusesImportAndConcurrentReviewsCannotOverwrite() {
        ready()
        val coordinator = LibretroCoreCoordinator()
        val manager = SaveStateManager(customBaseDir = root, customCoordinator = coordinator)
        val review = manager.reviewRestoredSaves().single().entries.single()
        manager.activeIdentity = RomIdentity.create(hash, "Synthetic")
        assertEquals(CLOSE_GAME, manager.importRestoredSave(review, IMPORT_MISSING))
        manager.activeIdentity = null
        coordinator.emulationRunning = true
        assertEquals(CLOSE_GAME, manager.importRestoredSave(review, IMPORT_MISSING))
        coordinator.emulationRunning = false
        assertFalse(File(root, path).exists())
        val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map { Callable { manager.importRestoredSave(review, IMPORT_MISSING) } }
            val results = pool.invokeAll(tasks).map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it == IMPORTED })
            assertEquals(1, results.count { it == REVIEW_AGAIN })
            assertEquals("restored", File(root, path).readText())
        } finally { pool.shutdownNow() }
    }
}
