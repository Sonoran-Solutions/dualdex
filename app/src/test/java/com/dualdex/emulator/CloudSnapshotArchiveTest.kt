package com.dualdex.emulator

import com.dualdex.emulator.backup.CloudSnapshotArchive
import com.dualdex.emulator.backup.QuotaBackupSelection
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class CloudSnapshotArchiveTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun archiveBuildValidatesAndIsReproducible() {
        val base = Files.createTempDirectory("dualdex-backup-test").toFile()
        try {
            val app = File(base, "files")
            val save = File(app, "saves_v2/${"a".repeat(64)}/battery.sav")
            save.parentFile.mkdirs(); save.writeBytes(byteArrayOf(1, 2, 3, 4))
            val path = "saves_v2/${"a".repeat(64)}/battery.sav"
            val stamp = QuotaBackupSelection.SourceStamp(path, save.length(), save.lastModified(), sha(save.readBytes()))
            val input = listOf(CloudSnapshotArchive.Entry(save, stamp, "a".repeat(64), "CANONICAL_BATTERY"))
            val first = File(app, "cloud_backup/first.zip")
            val second = File(app, "cloud_backup/second.zip")
            val work = File(base, "cache")
            assertNotNull(CloudSnapshotArchive.build(input, app, work, first))
            assertNotNull(CloudSnapshotArchive.build(input, app, work, second))
            assertArrayEquals(first.readBytes(), second.readBytes())
            assertEquals(1, CloudSnapshotArchive.validate(first)?.entries?.size)
            val altered = File(base, "tampered.zip")
            java.util.zip.ZipFile(first).use { source ->
                java.util.zip.ZipOutputStream(altered.outputStream()).use { output ->
                    val entries = source.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        var bytes = source.getInputStream(entry).use { it.readBytes() }
                        if (entry.name.startsWith("data/")) bytes = bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
                        output.putNextEntry(java.util.zip.ZipEntry(entry.name)); output.write(bytes); output.closeEntry()
                    }
                }
            }
            assertNull(CloudSnapshotArchive.validate(altered))
            first.writeBytes(first.readBytes().dropLast(20).toByteArray())
            assertNull(CloudSnapshotArchive.validate(first))
        } finally { base.deleteRecursively() }
    }

    @Test fun builderRejectsWrongRootAndSameSizeContentMutation() {
        val base = Files.createTempDirectory("dualdex-backup-test").toFile()
        try {
            val app = File(base, "files"); val outside = File(base, "outside.sav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val path = "saves_v2/${"b".repeat(64)}/battery.sav"
            val stamp = QuotaBackupSelection.SourceStamp(path, 3, outside.lastModified(), sha(outside.readBytes()))
            val dest = File(app, "cloud_backup/snapshot.zip")
            assertNull(CloudSnapshotArchive.build(listOf(CloudSnapshotArchive.Entry(outside, stamp, "b".repeat(64), "CANONICAL_BATTERY")),
                app, File(base, "cache"), dest))
            val source = File(app, path).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val selected = QuotaBackupSelection.SourceStamp(path, 3, source.lastModified(), sha(source.readBytes()))
            source.writeBytes(byteArrayOf(3, 2, 1))
            val result = CloudSnapshotArchive.build(listOf(CloudSnapshotArchive.Entry(source, selected, "b".repeat(64), "CANONICAL_BATTERY")),
                app, File(base, "cache"), dest)
            assertNull(result)
            assertFalse(dest.exists())
        } finally { base.deleteRecursively() }
    }
}
