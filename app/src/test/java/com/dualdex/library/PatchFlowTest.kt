package com.dualdex.library

import com.dualdex.romhack.RomCompatibilityStatus
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatchFlowTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeSafTarget(
        var writable: Boolean = true,
        val initialEntries: MutableMap<String, Pair<String, ByteArray>> = linkedMapOf(),
        var forcedCreateName: String? = null,
        var failWrite: Boolean = false,
        var renameMode: RenameMode = RenameMode.SUCCEED
    ) : SafDocumentTarget {
        enum class RenameMode { SUCCEED, RETURN_NULL, THROW_UNSUPPORTED, RETURN_NON_GBA }

        val calls = mutableListOf<String>()
        val entries = initialEntries.toMutableMap()
        private var nextId = 1

        override fun canWrite(): Boolean = writable

        override fun listEntryNamesAndUris(): List<StagedDocument> =
            entries.map { (uri, pair) -> StagedDocument(uri, pair.first) }

        override fun createStagingDocument(displayName: String): StagedDocument? {
            calls += "create:$displayName"
            val actualName = forcedCreateName ?: displayName
            val uri = "content://saf/doc_${nextId++}"
            entries[uri] = actualName to ByteArray(0)
            return StagedDocument(uri, actualName)
        }

        override fun writeAll(uriString: String, bytes: ByteArray) {
            calls += "write:$uriString"
            if (failWrite) throw IOException("simulated write failure")
            val current = entries[uriString] ?: error("missing staged doc $uriString")
            entries[uriString] = current.first to bytes.copyOf()
        }

        override fun renameDocument(uriString: String, targetName: String): StagedDocument? {
            calls += "rename:$uriString->$targetName"
            return when (renameMode) {
                RenameMode.RETURN_NULL -> null
                RenameMode.THROW_UNSUPPORTED -> throw UnsupportedOperationException("rename unsupported")
                RenameMode.RETURN_NON_GBA -> {
                    val newUri = "content://saf/renamed_${nextId++}"
                    val data = entries.remove(uriString)?.second ?: ByteArray(0)
                    entries[newUri] = "bad.tmp" to data
                    StagedDocument(newUri, "bad.tmp")
                }
                RenameMode.SUCCEED -> {
                    val newUri = "content://saf/published_${nextId++}"
                    val data = entries.remove(uriString)?.second ?: ByteArray(0)
                    entries[newUri] = targetName to data
                    StagedDocument(newUri, targetName)
                }
            }
        }

        override fun deleteDocument(uriString: String): Boolean {
            calls += "delete:$uriString"
            return entries.remove(uriString) != null
        }
    }

    @Test
    fun safPublicationStagesUnderNonRomTmpNameWritesFlushesThenRenamesToGba() {
        val target = FakeSafTarget(
            initialEntries = linkedMapOf("content://saf/existing" to ("Emerald.gba" to byteArrayOf(1)))
        )
        val payload = "GBA_PATCHED".toByteArray()

        val published = PatchFlow.publishStagedDocument(target, "Emerald", payload, uniqueToken = "tok1")
        assertNotNull(published)
        assertEquals("Emerald (1).gba", published!!.name)

        // Staging doc must be created under .dualdex_patch_*.tmp, written, and only then renamed.
        assertEquals(
            listOf(
                "create:.dualdex_patch_tok1.tmp",
                "write:content://saf/doc_1",
                "rename:content://saf/doc_1->Emerald (1).gba"
            ),
            target.calls
        )
        assertArrayEquals(payload, target.entries[published.uriString]?.second)
        assertFalse(target.entries.values.any { it.first.endsWith(".tmp") })
    }

    @Test
    fun safPublicationCleansUpStagingAndNeverExposesGbaOnWriteFailureOrUnsupportedRename() {
        val writeFailTarget = FakeSafTarget(failWrite = true)
        assertNull(PatchFlow.publishStagedDocument(writeFailTarget, "Hack", byteArrayOf(1, 2), uniqueToken = "wfail"))
        assertFalse(writeFailTarget.calls.any { it.startsWith("rename:") })
        assertTrue(writeFailTarget.calls.any { it.startsWith("delete:") })
        assertTrue(writeFailTarget.entries.isEmpty())

        for (mode in listOf(
            FakeSafTarget.RenameMode.RETURN_NULL,
            FakeSafTarget.RenameMode.THROW_UNSUPPORTED,
            FakeSafTarget.RenameMode.RETURN_NON_GBA
        )) {
            val renameFailTarget = FakeSafTarget(renameMode = mode)
            assertNull(PatchFlow.publishStagedDocument(renameFailTarget, "Hack", byteArrayOf(1, 2), uniqueToken = "rfail"))
            assertTrue(renameFailTarget.entries.isEmpty())
            assertTrue(renameFailTarget.calls.any { it.startsWith("delete:") })
        }

        // If provider forces a ROM extension (.bin or .gba) onto the staging document, never write bytes to it.
        val romExtTarget = FakeSafTarget(forcedCreateName = ".dualdex_patch_bad.tmp.bin")
        assertNull(PatchFlow.publishStagedDocument(romExtTarget, "Hack", byteArrayOf(1, 2), uniqueToken = "badext"))
        assertFalse(romExtTarget.calls.any { it.startsWith("write:") })
        assertFalse(romExtTarget.calls.any { it.startsWith("rename:") })
        assertTrue(romExtTarget.entries.isEmpty())
    }

    @Test
    fun privateFallbackPersistsAcrossRestartsAndScannerIgnoresTmpStagingFiles() {
        val patchedDir = File(tmp.root, LibraryScanner.PATCHED_ROMS_DIR)
        val romA = ByteArray(0x200).also { "FIRE RED".toByteArray().copyInto(it, 0xA0) }
        val romB = ByteArray(0x200).also { "EMERALD".toByteArray().copyInto(it, 0xA0) }

        val savedA = PatchFlow.savePrivatelyToDir(patchedDir, "My Hack", romA)
        val savedB = PatchFlow.savePrivatelyToDir(patchedDir, "My Hack", romB)
        assertEquals("My Hack.gba", savedA.name)
        assertEquals("My Hack (1).gba", savedB.name)
        assertEquals(patchedDir, savedA.parentFile)

        // Plant staging, temp, backup, hidden, empty, and non-ROM files that must be ignored.
        File(patchedDir, "incomplete.gba.tmp").writeBytes(romA)
        File(patchedDir, ".dualdex_patch_orphan.tmp").writeBytes(romA)
        File(patchedDir, "My Hack.gba.bak").writeBytes(romA)
        File(patchedDir, ".hidden.gba").writeBytes(romA)
        File(patchedDir, "empty.gba").writeBytes(ByteArray(0))
        File(patchedDir, "readme.txt").writeText("ignore me")

        val enumerated = LibraryScanner.collectPrivateRomFiles(patchedDir)
        assertEquals(listOf("My Hack (1).gba", "My Hack.gba"), enumerated.map { it.name })

        val cacheFile = File(tmp.root, "library_scan_cache.json")
        val firstScanCache = RomScanCache(cacheFile, emptyList())
        val seen1 = mutableSetOf<String>()
        val firstPass = LibraryScanner.scanPrivateRomDirectory(patchedDir, firstScanCache, emptyList(), seen1)
        firstScanCache.retainAndSave(seen1)
        assertEquals(2, firstPass.size)
        assertTrue(firstPass.all { it.sha256.length == 64 })

        // Simulate app restart with a fresh RomScanCache instance backed by the same cache file.
        val restartedCache = RomScanCache(cacheFile, emptyList())
        val secondPass = LibraryScanner.scanPrivateRomDirectory(patchedDir, restartedCache, emptyList())
        assertEquals(firstPass.map { it.fileName to it.sha256 }, secondPass.map { it.fileName to it.sha256 })
    }

    @Test
    fun mergesAndDeduplicatesLinkedAndPrivateRomsByUriAndSha256() {
        val linked = listOf(
            ScannedRomRecord("Alpha", "Alpha.gba", "content://saf/alpha", 1024, RomCompatibilityStatus.VERIFIED, "AA11", "Emerald"),
            ScannedRomRecord("Beta", "Beta.gba", "content://saf/beta", 1024, RomCompatibilityStatus.UNSUPPORTED, "BB22", ""),
            ScannedRomRecord("UninspectedA", "UninspectedA.gba", "content://saf/u1", 1024, null, "", "")
        )
        val private = listOf(
            ScannedRomRecord("Alpha Copy", "Alpha Copy.gba", "file:///private/alpha.gba", 1024, RomCompatibilityStatus.VERIFIED, "aa11", "Emerald"),
            ScannedRomRecord("Beta Same Uri", "Beta.gba", "content://saf/beta", 1024, RomCompatibilityStatus.UNSUPPORTED, "BB22", ""),
            ScannedRomRecord("Gamma", "Gamma.gba", "file:///private/gamma.gba", 2048, RomCompatibilityStatus.VERIFIED, "CC33", "FireRed"),
            ScannedRomRecord("UninspectedB", "UninspectedB.gba", "file:///private/u2.gba", 1024, null, "", "")
        )

        val merged = LibraryScanner.mergeAndDeduplicateRoms(linked, private)
        assertEquals(
            listOf("Alpha", "Beta", "Gamma", "UninspectedA", "UninspectedB"),
            merged.map { it.title }
        )
        assertEquals("content://saf/alpha", merged.first { it.title == "Alpha" }.uriString)
    }

    @Test
    fun patchRefreshTrackerRunsOncePerSaveAndResolvesCurrentFolderUri() {
        val tracker = PatchRefreshTracker()
        var currentFolder: String? = "content://saf/folderA"
        val triggeredWith = mutableListOf<String?>()

        assertFalse(tracker.onPatchStatus(PatchFlow.Status.Idle, { currentFolder }) { triggeredWith += it })
        assertFalse(tracker.onPatchStatus(PatchFlow.Status.Failed("boom"), { currentFolder }) { triggeredWith += it })

        // Saved event after folder switch triggers once using the CURRENT folder URI.
        currentFolder = "content://saf/folderB"
        assertTrue(tracker.onSaved("save-1", { currentFolder }) { triggeredWith += it })
        assertFalse(tracker.onSaved("save-1", { currentFolder }) { triggeredWith += it })
        assertEquals(listOf("content://saf/folderB"), triggeredWith)

        // New patch run resets tracker; private fallback (no linked folder) still triggers refresh.
        assertFalse(tracker.onPatchStatus(PatchFlow.Status.Patching, { currentFolder }) { triggeredWith += it })
        currentFolder = null
        assertTrue(tracker.onSaved("save-2", { currentFolder }) { triggeredWith += it })
        assertEquals(listOf("content://saf/folderB", null), triggeredWith)

        // Activity recreation initialized with already-handled Saved key does not trigger a duplicate scan.
        val recreatedTracker = PatchRefreshTracker(initialHandledSavedKey = "save-2")
        assertFalse(recreatedTracker.onSaved("save-2", { currentFolder }) { triggeredWith += it })
    }
}
