package com.dualdex.emulator

import com.dualdex.emulator.backup.BackupFileTreePolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class BackupFileTreePolicyTest {
    @Test fun emittedInventoryIsEveryRegularFileInTheApprovedTrees() {
        val base = Files.createTempDirectory("dualdex-d2d-policy").toFile()
        try {
            val files = File(base, "files")
            val id = "a".repeat(64)
            val expected = listOf(
                "saves/$id/Pokemon_Emerald.sav.orig.bak",
                "saves/$id/Pokemon_Emerald.sav.migrated.bak",
                "saves_fallback/old-key.migrated.bak/slot_1.state",
                "saves_v2/$id/battery.sav",
                "saves_v2/$id/battery.sav.bak",
                "saves_v2/$id/battery.sav.tmp",
                "saves_v2/$id/metadata.json",
                "saves_v2/$id/resume_barrier",
                "saves_v2/$id/slot_1.png",
                "saves_v2/$id/slot_1.state.bak"
            )
            expected.forEach { path ->
                File(files, path).apply { checkNotNull(parentFile).mkdirs(); writeText(path) }
            }
            File(files, "rom_cache/game.gba").apply { checkNotNull(parentFile).mkdirs(); writeBytes(byteArrayOf(1)) }
            val catalog = File(base, "shared_prefs/dualdex_legacy_catalog.xml").apply {
                checkNotNull(parentFile).mkdirs(); writeText("catalog")
            }

            val emitted = mutableListOf<BackupFileTreePolicy.BackupFile>()
            BackupFileTreePolicy.emit(files, catalog, emitted::add)
            assertEquals(expected.toSet() + BackupFileTreePolicy.LEGACY_CATALOG_PATH, emitted.map { it.relativePath }.toSet())
            assertEquals(emitted.map { it.relativePath }.sorted(), emitted.map { it.relativePath })
            assertFalse(emitted.any { it.file.isDirectory })
            var receivedBytes = 0L
            emitted.forEachIndexed { index, entry ->
                assertTrue(entry.file.isFile)
                BackupFileTreePolicy.checkFileAdmission(entry.relativePath, entry.sizeBytes, receivedBytes, index + 1)
                receivedBytes += entry.sizeBytes
            }
        } finally { base.deleteRecursively() }
    }

    @Test fun treeTraversalFailsClosedOnSymlinksAndRestoreUsesTheSamePolicy() {
        val base = Files.createTempDirectory("dualdex-d2d-policy").toFile()
        try {
            val files = File(base, "files")
            val id = "b".repeat(64)
            File(files, "saves_v2/$id/battery.sav").apply {
                checkNotNull(parentFile).mkdirs(); writeBytes(byteArrayOf(1))
            }
            val outside = File(base, "outside.sav").apply { writeBytes(byteArrayOf(2)) }
            Files.createSymbolicLink(File(files, "saves_v2/$id/escape.sav").toPath(), outside.toPath())
            assertThrows(IOException::class.java) {
                BackupFileTreePolicy.collect(files, File(base, "missing-catalog"))
            }

            assertTrue(BackupFileTreePolicy.isAllowedPath("saves_v2/$id/battery.sav.bak", directory = false))
            assertTrue(BackupFileTreePolicy.isAllowedPath("saves_v2/$id/resume_barrier", directory = false))
            assertTrue(BackupFileTreePolicy.isAllowedPath("saves/Pokemon_Emerald.sav.orig.bak", directory = false))
            assertTrue(BackupFileTreePolicy.isAllowedPath("saves_fallback/old-key.migrated.bak/slot_1.state", directory = false))
            assertFalse(BackupFileTreePolicy.isAllowedPath("saves_v2/$id/../rom_cache/game.gba", directory = false))
            assertFalse(BackupFileTreePolicy.isAllowedPath("rom_cache/game.gba", directory = false))
        } finally { base.deleteRecursively() }
    }

    @Test fun D2DRecoveryFilesCanExceedCloudEntryLimitWithinSeparateRestoreBounds() {
        val base = Files.createTempDirectory("dualdex-d2d-size").toFile()
        try {
            val files = File(base, "files")
            val id = "c".repeat(64)
            val large = File(files, "saves_v2/$id/battery.sav.bak").apply {
                checkNotNull(parentFile).mkdirs()
                java.io.RandomAccessFile(this, "rw").use { it.setLength(QuotaCloudLimit + 1) }
            }
            assertTrue(large.length() > QuotaCloudLimit)
            assertTrue(BackupFileTreePolicy.collect(files, File(base, "missing-catalog"))
                .any { it.relativePath == "saves_v2/$id/battery.sav.bak" })
            assertTrue(large.length() <= BackupFileTreePolicy.MAX_D2D_FILE_BYTES)
        } finally { base.deleteRecursively() }
    }

    @Test fun emptyCanonicalTemporaryAndRecoveryFilesRefuseBeforeAnyEmission() {
        val base = Files.createTempDirectory("dualdex-d2d-empty").toFile()
        try {
            val files = File(base, "files")
            File(files, "saves/a.sav").apply { checkNotNull(parentFile).mkdirs(); writeText("save") }
            for (name in listOf("battery.sav", "battery.sav.tmp", "battery.sav.bak", "old.sav.orig.bak", "old.sav.migrated.bak", "resume_barrier")) {
                val path = "saves/z/$name"
                val empty = File(files, path).apply { checkNotNull(parentFile).mkdirs(); writeBytes(byteArrayOf()) }
                assertThrows(IOException::class.java) { BackupFileTreePolicy.checkFileAdmission(path, 0, 0, 1) }
                var emitted = 0
                assertThrows(IOException::class.java) {
                    BackupFileTreePolicy.emit(files, File(base, "missing-catalog")) { emitted++ }
                }
                assertEquals(0, emitted)
                assertTrue(empty.delete())
            }
        } finally { base.deleteRecursively() }
    }

    @Test fun senderAndReceiverAgreeAtFileTotalAndCatalogLimits() {
        val base = Files.createTempDirectory("dualdex-d2d-limits").toFile()
        try {
            val files = File(base, "files")
            val catalog = File(base, "catalog")
            val limit = BackupFileTreePolicy.MAX_D2D_FILE_BYTES
            val totalLimit = BackupFileTreePolicy.MAX_D2D_STAGING_BYTES
            val first = sparseFile(files, "saves/1.sav.bak", limit + 1)
            assertThrows(IOException::class.java) {
                BackupFileTreePolicy.checkFileAdmission("saves/1.sav.bak", limit + 1, 0, 1)
            }
            var emitted = 0
            assertThrows(IOException::class.java) { BackupFileTreePolicy.emit(files, catalog) { emitted++ } }
            assertEquals(0, emitted)
            java.io.RandomAccessFile(first, "rw").use { it.setLength(limit) }
            (2..4).forEach { sparseFile(files, "saves/$it.sav.bak", limit) }
            var receivedBytes = 0L
            BackupFileTreePolicy.emit(files, catalog) { entry ->
                BackupFileTreePolicy.checkFileAdmission(entry.relativePath, entry.sizeBytes, receivedBytes, ++emitted)
                receivedBytes += entry.sizeBytes
            }
            assertEquals(totalLimit, receivedBytes)
            assertEquals(4, emitted)

            // The catalog counts toward the transfer total too, before the first file is sent.
            catalog.writeText("x")
            assertThrows(IOException::class.java) {
                BackupFileTreePolicy.checkFileAdmission(BackupFileTreePolicy.LEGACY_CATALOG_PATH, 1, totalLimit, 5)
            }
            emitted = 0
            assertThrows(IOException::class.java) { BackupFileTreePolicy.emit(files, catalog) { emitted++ } }
            assertEquals(0, emitted)
            assertTrue(files.deleteRecursively())
            java.io.RandomAccessFile(catalog, "rw").use { it.setLength(BackupFileTreePolicy.MAX_CATALOG_BYTES) }
            assertEquals(1, BackupFileTreePolicy.collect(files, catalog).size)
            java.io.RandomAccessFile(catalog, "rw").use { it.setLength(BackupFileTreePolicy.MAX_CATALOG_BYTES + 1) }
            assertThrows(IOException::class.java) { BackupFileTreePolicy.collect(files, catalog) }
            assertThrows(IOException::class.java) {
                BackupFileTreePolicy.checkFileAdmission(BackupFileTreePolicy.LEGACY_CATALOG_PATH, catalog.length(), 0, 1)
            }
        } finally { base.deleteRecursively() }
    }

    @Test fun receiverAdmissionRejectsInvalidCountsAndOverflowingSizes() {
        val path = "saves/old.sav.migrated.bak"
        BackupFileTreePolicy.checkFileAdmission(path, 1, 0, BackupFileTreePolicy.MAX_D2D_ENTRIES)
        for (count in listOf(0, BackupFileTreePolicy.MAX_D2D_ENTRIES + 1)) {
            assertThrows(IOException::class.java) { BackupFileTreePolicy.checkFileAdmission(path, 1, 0, count) }
        }
        for (size in listOf(-1L, Long.MAX_VALUE)) {
            assertThrows(IOException::class.java) { BackupFileTreePolicy.checkFileAdmission(path, size, 0, 1) }
        }
        assertThrows(IOException::class.java) { BackupFileTreePolicy.checkFileAdmission(path, 1, Long.MAX_VALUE, 1) }
    }

    @Test fun changingFileSizeAfterPreflightRefusesEmission() {
        val base = Files.createTempDirectory("dualdex-d2d-changing").toFile()
        try {
            val files = File(base, "files")
            sparseFile(files, "saves/1.sav", 1)
            val later = sparseFile(files, "saves/2.sav.tmp", 1)
            var emitted = 0
            assertThrows(IOException::class.java) {
                BackupFileTreePolicy.emit(files, File(base, "missing-catalog")) {
                    emitted++
                    java.io.RandomAccessFile(later, "rw").use { it.setLength(BackupFileTreePolicy.MAX_D2D_FILE_BYTES + 1) }
                }
            }
            assertEquals(1, emitted)
        } finally { base.deleteRecursively() }
    }

    @Test fun ROMExtensionsAreRejectedInEverySaveRootIncludingRecoveryDisguises() {
        val base = Files.createTempDirectory("dualdex-d2d-rom-policy").toFile()
        try {
            val files = File(base, "files")
            for (root in listOf("saves", "saves_fallback", "saves_v2/${"d".repeat(64)}")) {
                for (name in listOf("game.gba", "game.GB", "game.GbC", "game.nds", "game.agb", "game.GBA.tmp", "game.gba.orig.bak", "game.gbc.migrated.bak")) {
                    val path = "$root/nested/$name"
                    val payload = sparseFile(files, path, 1) // Synthetic bytes only; no ROM data.
                    assertFalse(path, BackupFileTreePolicy.isAllowedPath(path, directory = false))
                    assertThrows(IOException::class.java) { BackupFileTreePolicy.checkFileAdmission(path, 1, 0, 1) }
                    var emitted = 0
                    assertThrows(IOException::class.java) {
                        BackupFileTreePolicy.emit(files, File(base, "missing-catalog")) { emitted++ }
                    }
                    assertEquals(0, emitted)
                    assertTrue(payload.delete())
                }
            }
            assertTrue(BackupFileTreePolicy.isAllowedPath("saves/game.gba/battery.sav", directory = false))
            assertTrue(BackupFileTreePolicy.isAllowedPath("saves/game.gba.sav.bak", directory = false))
        } finally { base.deleteRecursively() }
    }

    private fun sparseFile(root: File, path: String, size: Long): File = File(root, path).apply {
        checkNotNull(parentFile).mkdirs()
        java.io.RandomAccessFile(this, "rw").use { it.setLength(size) }
    }

    private companion object {
        const val QuotaCloudLimit = 20L * 1024 * 1024
    }
}
