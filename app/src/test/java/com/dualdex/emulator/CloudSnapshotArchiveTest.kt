package com.dualdex.emulator

import com.dualdex.emulator.backup.CloudSnapshotArchive
import com.dualdex.emulator.backup.QuotaBackupSelection
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CloudSnapshotArchiveTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun snapshotEntries(app: File, id: String): List<CloudSnapshotArchive.Entry> {
        val battery = File(app, "saves_v2/$id/battery.sav").apply { checkNotNull(parentFile).mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val metadata = File(app, "saves_v2/$id/metadata.json").apply {
            writeText("""{"sha256":"$id","displayName":"Synthetic"}""")
        }
        return listOf(battery, metadata).map { source ->
            val path = app.toPath().relativize(source.toPath()).toString().replace(File.separatorChar, '/')
            val stamp = QuotaBackupSelection.SourceStamp(path, source.length(), source.lastModified(), sha(source.readBytes()))
            CloudSnapshotArchive.Entry(source, stamp, id, if (source == battery) "CANONICAL_BATTERY" else "SAVE_METADATA")
        }
    }

    private data class RawEntry(val path: String, val identity: String, val category: String, val bytes: ByteArray)

    /** Constructs a fully checksummed archive so tests exercise semantic validation, not digest rejection. */
    private fun forgedArchive(file: File, entries: List<RawEntry>) {
        val manifestBytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(0x44445831); out.writeInt(CloudSnapshotArchive.FORMAT_VERSION); out.writeInt(entries.size)
                entries.forEach { entry ->
                    out.writeUTF(entry.path); out.writeUTF(entry.identity); out.writeUTF(entry.category)
                    out.writeLong(entry.bytes.size.toLong()); out.writeUTF(sha(entry.bytes)); out.writeBoolean(false)
                }
            }
        }.toByteArray()
        val manifestHash = sha(manifestBytes)
        val contentHash = sha(entries.sortedBy { it.path }.joinToString("\n") { "${it.path}\t${sha(it.bytes)}" }.toByteArray())
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry("data/${entry.path}")); zip.write(entry.bytes); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(CloudSnapshotArchive.MANIFEST_ENTRY)); zip.write(manifestBytes); zip.closeEntry()
            zip.putNextEntry(ZipEntry(CloudSnapshotArchive.COMPLETION_ENTRY))
            zip.write("$manifestHash\n$contentHash\n".toByteArray()); zip.closeEntry()
        }
    }

    @Test fun archiveBuildValidatesAndIsReproducible() {
        val base = Files.createTempDirectory("dualdex-backup-test").toFile()
        try {
            val app = File(base, "files")
            val input = snapshotEntries(app, "a".repeat(64))
            val first = File(app, "cloud_backup/first.zip")
            val second = File(app, "cloud_backup/second.zip")
            val work = File(base, "cache")
            assertNotNull(CloudSnapshotArchive.build(input, app, work, first))
            assertNotNull(CloudSnapshotArchive.build(input, app, work, second))
            assertArrayEquals(first.readBytes(), second.readBytes())
            assertEquals(2, CloudSnapshotArchive.validate(first)?.entries?.size)
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
            val valid = snapshotEntries(app, "b".repeat(64))
            val battery = valid.first { it.category == "CANONICAL_BATTERY" }
            val stamp = battery.stamp.copy(modifiedAtMillis = outside.lastModified(), sizeBytes = outside.length(), contentSha256 = sha(outside.readBytes()))
            val dest = File(app, "cloud_backup/snapshot.zip")
            assertNull(CloudSnapshotArchive.build(valid.map { if (it.category == "CANONICAL_BATTERY") it.copy(source = outside, stamp = stamp) else it },
                app, File(base, "cache"), dest))
            val source = battery.source
            val selected = battery.stamp
            source.writeBytes(byteArrayOf(3, 2, 1, 0))
            source.setLastModified(selected.modifiedAtMillis)
            val result = CloudSnapshotArchive.build(valid.map { if (it.category == "CANONICAL_BATTERY") it.copy(stamp = selected) else it }, app, File(base, "cache"), dest)
            assertNull(result)
            assertFalse(dest.exists())
        } finally { base.deleteRecursively() }
    }

    @Test fun rechecksEverySourceAfterCopyingTheSet() {
        val base = Files.createTempDirectory("dualdex-backup-generation").toFile()
        try {
            val app = File(base, "files")
            val entries = snapshotEntries(app, "d".repeat(64))
            val earlier = entries.first { it.category == "CANONICAL_BATTERY" }
            val originalTime = earlier.source.lastModified()
            val result = CloudSnapshotArchive.build(entries, app, File(base, "cache"), File(app, "cloud_backup/snapshot.zip")) { index ->
                if (index == 0) {
                    earlier.source.writeBytes(byteArrayOf(8, 7, 6, 5))
                    earlier.source.setLastModified(originalTime)
                }
            }
            assertNull(result)
            assertFalse(File(app, "cloud_backup/snapshot.zip").exists())
        } finally { base.deleteRecursively() }
    }

    @Test fun rejectsInternallyConsistentArchivesWithInvalidOwnershipOrUnknownCategory() {
        val base = Files.createTempDirectory("dualdex-backup-ownership").toFile()
        try {
            val a = "a".repeat(64); val b = "b".repeat(64)
            val metadata = """{"sha256":"$b","displayName":"Synthetic"}""".toByteArray()
            val validRelation = RawEntry("saves_v2/$b/metadata.json", b, "SAVE_METADATA", metadata)
            val mismatchedPath = RawEntry("saves_v2/$a/battery.sav", b, "CANONICAL_BATTERY", byteArrayOf(1, 2, 3))
            val badPath = File(base, "bad-owner.zip")
            forgedArchive(badPath, listOf(validRelation, mismatchedPath))
            assertNull(CloudSnapshotArchive.validate(badPath))

            val badCategory = File(base, "bad-category.zip")
            forgedArchive(badCategory, listOf(
                validRelation,
                RawEntry("saves_v2/$b/battery.sav", b, "FUTURE_UNKNOWN", byteArrayOf(4, 5, 6))
            ))
            assertNull(CloudSnapshotArchive.validate(badCategory))

            val absentEssential = File(base, "missing-battery.zip")
            forgedArchive(absentEssential, listOf(validRelation))
            assertNull(CloudSnapshotArchive.validate(absentEssential))
        } finally { base.deleteRecursively() }
    }
}
