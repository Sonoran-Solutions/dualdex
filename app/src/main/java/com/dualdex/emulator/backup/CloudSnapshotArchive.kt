package com.dualdex.emulator.backup

import com.dualdex.emulator.storage.RomSaveMetadata
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Deterministic, bounded ZIP codec; validates ownership without consulting live emulator state. */
object CloudSnapshotArchive {
    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.bin"
    const val COMPLETION_ENTRY = "complete.bin"
    private const val MAX_ARCHIVE_BYTES = QuotaBackupSelection.MAX_PAYLOAD_BYTES
    private const val MAGIC = 0x44445831 // DDX1

    data class Entry(
        val source: File,
        val stamp: QuotaBackupSelection.SourceStamp,
        val romSha256: String,
        val category: String,
        val legacyBinding: QuotaBackupSelection.PortableLegacyBinding? = null
    )
    data class ValidatedEntry(
        val path: String,
        val size: Long,
        val sha256: String,
        val romSha256: String,
        val category: String,
        val legacyBinding: QuotaBackupSelection.PortableLegacyBinding? = null
    )
    data class Snapshot(val manifestSha256: String, val contentSetSha256: String, val entries: List<ValidatedEntry>)
    private data class Ownership(
        val path: String,
        val identity: String,
        val category: String,
        val binding: QuotaBackupSelection.PortableLegacyBinding?
    )

    /** Builds away from backup-visible roots, validates the complete archive, then atomically publishes it. */
    // The optional callback is an internal fault-injection seam for source-generation tests.
    internal fun build(
        entries: List<Entry>, appFilesRoot: File, workingDirectory: File, destination: File,
        afterSourceRead: ((Int) -> Unit)? = null
    ): Snapshot? {
        if (entries.isEmpty() || entries.size > QuotaBackupSelection.MAX_ENTRIES) return null
        if (!workingDirectory.isDirectory && !workingDirectory.mkdirs()) return null
        val ordered = entries.sortedBy { it.stamp.relativePath }
        if (ordered.map { it.stamp.relativePath }.distinct().size != ordered.size) return null
        if (!validOwnershipSet(ordered.map { Ownership(it.stamp.relativePath, it.romSha256.lowercase(), it.category, it.legacyBinding) })) return null
        var total = 0L
        for (entry in ordered) {
            val stamp = entry.stamp
            if (!validIdentity(entry.romSha256) || !validLegacyBinding(entry) || !QuotaBackupSelection.isAllowedSourcePath(
                    appFilesRoot.absolutePath, entry.source.absolutePath, stamp.relativePath
                ) || stamp.sizeBytes !in 1..QuotaBackupSelection.MAX_ENTRY_BYTES ||
                stamp.relativePath.length > QuotaBackupSelection.MAX_PATH_CHARS ||
                !safeArchivePath(stamp.relativePath) || !validSha(stamp.contentSha256)) return null
            total = checkedAdd(total, stamp.sizeBytes) ?: return null
            if (total > MAX_ARCHIVE_BYTES) return null
            if (!entry.source.isFile || entry.source.length() != stamp.sizeBytes || entry.source.lastModified() != stamp.modifiedAtMillis) return null
        }
        val temp = File(workingDirectory, "snapshot-${System.nanoTime()}.tmp")
        val expected = ArrayList<ValidatedEntry>(ordered.size)
        try {
            // Rebuild with stored entries after reading each bounded source to a stable byte array.
            temp.delete()
            val content = ordered.mapIndexed { index, entry ->
                val data = entry.source.readBytesBounded(entry.stamp.sizeBytes) ?: return null
                val digest = sha256(data)
                if (digest != entry.stamp.contentSha256.lowercase()) return null
                val after = stamp(entry.source, entry.stamp.relativePath) ?: return null
                if (after != entry.stamp) return null
                afterSourceRead?.invoke(index)
                Triple(entry, data, digest)
            }
            val ownership = ordered.map { Ownership(it.stamp.relativePath, it.romSha256.lowercase(), it.category, it.legacyBinding) }
            if (!validMetadataBytes(content.associate { it.first.stamp.relativePath to it.second }, ownership)) return null
            val manifestBytes = encodeManifest(content.map { (e, _, digest) ->
                ValidatedEntry(e.stamp.relativePath, e.stamp.sizeBytes, digest, e.romSha256.lowercase(), e.category, e.legacyBinding)
            }) ?: return null
            if (manifestBytes.size.toLong() > QuotaBackupSelection.MAX_MANIFEST_BYTES) return null
            val manifestDigest = sha256(manifestBytes)
            val contentDigest = contentSetDigest(content.map { (e, _, digest) -> e.stamp.relativePath to digest })
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
                for ((entry, bytes, digest) in content) {
                    val name = "data/${entry.stamp.relativePath}"
                    zip.putNextEntry(storedEntry(name, bytes))
                    zip.write(bytes)
                    zip.closeEntry()
                    expected += ValidatedEntry(entry.stamp.relativePath, bytes.size.toLong(), digest, entry.romSha256.lowercase(), entry.category, entry.legacyBinding)
                }
                zip.putNextEntry(storedEntry(MANIFEST_ENTRY, manifestBytes)); zip.write(manifestBytes); zip.closeEntry()
                val marker = encodeMarker(manifestDigest, contentDigest)
                zip.putNextEntry(storedEntry(COMPLETION_ENTRY, marker)); zip.write(marker); zip.closeEntry()
            }
            if (temp.length() <= 0 || temp.length() > MAX_ARCHIVE_BYTES) return null
            val verified = validate(temp) ?: return null
            // Ensure no earlier source changed while later sources were copied or the archive was validated.
            if (ordered.any { stamp(it.source, it.stamp.relativePath) != it.stamp }) return null
            destination.parentFile?.let { if (!it.exists() && !it.mkdirs()) return null }
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return verified
        } catch (_: Exception) {
            return null
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun validate(file: File): Snapshot? {
        if (!file.isFile || file.length() <= 0 || file.length() > MAX_ARCHIVE_BYTES) return null
        return try {
            ZipFile(file).use { zip ->
                val items = zip.entries().toList()
                if (items.size !in 3..(QuotaBackupSelection.MAX_ENTRIES + 2)) return null
                val names = items.map { it.name }
                if (names.distinct().size != names.size || names.count { it == MANIFEST_ENTRY } != 1 ||
                    names.count { it == COMPLETION_ENTRY } != 1) return null
                var expanded = 0L
                for (item in items) {
                    if (item.isDirectory || item.size < 0 || item.size > QuotaBackupSelection.MAX_ENTRY_BYTES) return null
                    expanded = checkedAdd(expanded, item.size) ?: return null
                    if (expanded > MAX_ARCHIVE_BYTES + QuotaBackupSelection.MAX_MANIFEST_BYTES + 4096) return null
                    if (item.name.startsWith("data/") && !safeArchivePath(item.name.removePrefix("data/"))) return null
                    if (item.name !in setOf(MANIFEST_ENTRY, COMPLETION_ENTRY) && !item.name.startsWith("data/")) return null
                }
                val manifest = zip.getInputStream(zip.getEntry(MANIFEST_ENTRY)).use { it.readBounded(QuotaBackupSelection.MAX_MANIFEST_BYTES.toInt()) } ?: return null
                val records = decodeManifest(manifest) ?: return null
                if (records.size > QuotaBackupSelection.MAX_ENTRIES || records.size != names.count { it.startsWith("data/") }) return null
                val manifestDigest = sha256(manifest)
                val actual = ArrayList<ValidatedEntry>(records.size)
                val contentByPath = linkedMapOf<String, ByteArray>()
                var dataTotal = 0L
                for (record in records) {
                    val ze = zip.getEntry("data/${record.path}") ?: return null
                    if (ze.size != record.size || record.size !in 1..QuotaBackupSelection.MAX_ENTRY_BYTES) return null
                    dataTotal = checkedAdd(dataTotal, record.size) ?: return null
                    if (dataTotal > MAX_ARCHIVE_BYTES) return null
                    val bytes = zip.getInputStream(ze).use { it.readBounded(record.size.toInt()) } ?: return null
                    val digest = sha256(bytes)
                    if (bytes.size.toLong() != record.size || digest != record.sha256) return null
                    actual += record
                    contentByPath[record.path] = bytes
                }
                val ownership = records.map { Ownership(it.path, it.romSha256, it.category, it.legacyBinding) }
                if (!validOwnershipSet(ownership) || !validMetadataBytes(contentByPath, ownership)) return null
                val marker = zip.getInputStream(zip.getEntry(COMPLETION_ENTRY)).use { it.readBounded(256) } ?: return null
                val parsed = decodeMarker(marker) ?: return null
                val contentDigest = contentSetDigest(actual.map { it.path to it.sha256 })
                if (parsed.first != manifestDigest || parsed.second != contentDigest) return null
                Snapshot(manifestDigest, contentDigest, actual)
            }
        } catch (_: Exception) { null }
    }


    private fun validLegacyBinding(entry: Entry): Boolean = validLegacyBinding(
        entry.stamp.relativePath, entry.stamp.contentSha256, entry.romSha256, entry.category, entry.legacyBinding
    )

    private fun validLegacyBinding(
        path: String, hash: String, identity: String, category: String,
        binding: QuotaBackupSelection.PortableLegacyBinding?
    ): Boolean {
        val isLegacy = category == QuotaBackupSelection.Category.VERIFIED_LEGACY_BATTERY.name ||
            category == QuotaBackupSelection.Category.VERIFIED_RECOVERY_BATTERY.name
        if (!isLegacy) return binding == null
        return binding != null && QuotaBackupSelection.isPortableLegacyPath(binding.archiveEntry) &&
            binding.archiveEntry == path && binding.contentSha256 == hash &&
            binding.romSha256 == identity && binding.targetFileName == "battery.sav"
    }

    private fun validEntryOwnership(
        path: String,
        identity: String,
        category: String,
        binding: QuotaBackupSelection.PortableLegacyBinding?
    ): Boolean {
        val parts = path.split('/')
        val canonical = parts.size == 3 && parts[0] == "saves_v2" && parts[1].equals(identity, true)
        return when (category) {
            QuotaBackupSelection.Category.CANONICAL_BATTERY.name -> canonical && parts[2] == "battery.sav" && binding == null
            QuotaBackupSelection.Category.SAVE_METADATA.name -> canonical && parts[2] == "metadata.json" && binding == null
            QuotaBackupSelection.Category.VERIFIED_RECOVERY_BATTERY.name,
            QuotaBackupSelection.Category.VERIFIED_LEGACY_BATTERY.name ->
                (path.startsWith("saves/") || path.startsWith("saves_fallback/")) &&
                    QuotaBackupSelection.isPortableLegacyPath(path) && parts.last().endsWith(".sav", true) &&
                    binding != null && validLegacyBinding(path, binding.contentSha256, identity, category, binding)
            QuotaBackupSelection.Category.STATE.name -> canonical && parts[2].endsWith(".state", true) && binding == null
            QuotaBackupSelection.Category.THUMBNAIL.name -> canonical &&
                parts[2].endsWithAny(".png", ".jpg", ".jpeg", ".webp") && binding == null
            else -> false
        }
    }

    private fun validOwnershipSet(entries: List<Ownership>): Boolean {
        if (entries.isEmpty()) return false
        if (entries.any { !validIdentity(it.identity) || !validEntryOwnership(it.path, it.identity, it.category, it.binding) }) return false
        return entries.groupBy { it.identity.lowercase() }.values.all { romEntries ->
            val categories = romEntries.map { it.category }
            categories.count { it == QuotaBackupSelection.Category.SAVE_METADATA.name } == 1 &&
                categories.count { it == QuotaBackupSelection.Category.CANONICAL_BATTERY.name } <= 1 &&
                categories.any { it in ESSENTIAL_BATTERY_CATEGORIES }
        }
    }

    private fun validMetadataBytes(contentByPath: Map<String, ByteArray>, entries: List<Ownership>): Boolean =
        entries.filter { it.category == QuotaBackupSelection.Category.SAVE_METADATA.name }.all { entry ->
            val json = contentByPath[entry.path]?.toString(Charsets.UTF_8) ?: return@all false
            RomSaveMetadata.fromJson(json)?.sha256?.equals(entry.identity, true) == true
        }

    private val ESSENTIAL_BATTERY_CATEGORIES = setOf(
        QuotaBackupSelection.Category.CANONICAL_BATTERY.name,
        QuotaBackupSelection.Category.VERIFIED_RECOVERY_BATTERY.name,
        QuotaBackupSelection.Category.VERIFIED_LEGACY_BATTERY.name
    )

    private fun encodeManifest(entries: List<ValidatedEntry>): ByteArray? = try {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC); out.writeInt(FORMAT_VERSION); out.writeInt(entries.size)
            entries.forEach { e ->
                out.writeUTF(e.path); out.writeUTF(e.romSha256); out.writeUTF(e.category)
                out.writeLong(e.size); out.writeUTF(e.sha256)
                out.writeBoolean(e.legacyBinding != null)
                e.legacyBinding?.let { binding ->
                    out.writeUTF(binding.archiveEntry); out.writeUTF(binding.contentSha256)
                    out.writeUTF(binding.romSha256); out.writeUTF(binding.targetFileName)
                }
            }
        }
        bytes.toByteArray().takeIf { it.size <= QuotaBackupSelection.MAX_MANIFEST_BYTES }
    } catch (_: Exception) { null }

    private fun decodeManifest(bytes: ByteArray): List<ValidatedEntry>? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) return null
            val count = input.readInt()
            if (count !in 1..QuotaBackupSelection.MAX_ENTRIES) return null
            val result = List(count) {
                val path = input.readUTF(); val id = input.readUTF(); val category = input.readUTF()
                val size = input.readLong(); val hash = input.readUTF()
                val binding = if (input.readBoolean()) QuotaBackupSelection.PortableLegacyBinding(
                    input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF()
                ) else null
                if (!safeArchivePath(path) || !validIdentity(id) || !validSha(hash) || size !in 1..QuotaBackupSelection.MAX_ENTRY_BYTES ||
                    !validEntryOwnership(path, id, category, binding) || !validLegacyBinding(path, hash, id, category, binding)) return null
                ValidatedEntry(path, size, hash, id, category, binding)
            }
            if (input.available() != 0 || result.map { it.path }.distinct().size != result.size ||
                !validOwnershipSet(result.map { Ownership(it.path, it.romSha256, it.category, it.legacyBinding) })) null else result
        }
    } catch (_: Exception) { null }

    private fun encodeMarker(manifest: String, contents: String): ByteArray = "$manifest\n$contents\n".toByteArray(Charsets.US_ASCII)
    private fun decodeMarker(bytes: ByteArray): Pair<String, String>? {
        val parts = bytes.toString(Charsets.US_ASCII).split('\n')
        if (parts.size != 3 || !validSha(parts[0]) || !validSha(parts[1]) || parts[2].isNotEmpty()) return null
        return parts[0] to parts[1]
    }
    private fun contentSetDigest(entries: List<Pair<String, String>>): String = sha256(entries.sortedBy { it.first }
        .joinToString("\n") { "${it.first}\t${it.second}" }.toByteArray(Charsets.UTF_8))
    private fun storedEntry(name: String, bytes: ByteArray) = ZipEntry(name).apply {
        method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = bytes.size.toLong(); time = 0L
        val checksum = java.util.zip.CRC32(); checksum.update(bytes); crc = checksum.value
    }
    private fun stamp(file: File, path: String): QuotaBackupSelection.SourceStamp? {
        if (!file.isFile || file.length() <= 0) return null
        val beforeSize = file.length(); val beforeTime = file.lastModified()
        val digest = FileInputStream(file).use { input -> digestStream(input, beforeSize) } ?: return null
        if (file.length() != beforeSize || file.lastModified() != beforeTime) return null
        return QuotaBackupSelection.SourceStamp(path, beforeSize, beforeTime, digest)
    }
    private fun digestStream(input: java.io.InputStream, limit: Long): String? {
        val md = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192); var total = 0L
        while (true) {
            val n = input.read(buffer); if (n < 0) break
            total = checkedAdd(total, n.toLong()) ?: return null
            if (total > limit || total > QuotaBackupSelection.MAX_ENTRY_BYTES) return null
            md.update(buffer, 0, n)
        }
        return if (total == limit) md.digest().toHex() else null
    }
    private fun File.readBytesBounded(expected: Long): ByteArray? {
        if (expected !in 1..QuotaBackupSelection.MAX_ENTRY_BYTES) return null
        return inputStream().use { it.readBounded(expected.toInt())?.takeIf { bytes -> bytes.size.toLong() == expected } }
    }
    private fun java.io.InputStream.readBounded(max: Int): ByteArray? {
        val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0L
        while (true) {
            val n = read(buffer); if (n < 0) break
            total = checkedAdd(total, n.toLong()) ?: return null
            if (total > max.toLong()) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
    private fun safeArchivePath(path: String) = path.length in 1..QuotaBackupSelection.MAX_PATH_CHARS &&
        !path.startsWith('/') && '\\' !in path && path.split('/').none { it.isBlank() || it == "." || it == ".." }
    private fun validIdentity(value: String) = value.matches(Regex("[0-9a-f]{64}"))
    private fun validSha(value: String) = value.matches(Regex("[0-9a-f]{64}"))
    private fun String.endsWithAny(vararg endings: String) = endings.any { endsWith(it, ignoreCase = true) }
    private fun checkedAdd(a: Long, b: Long): Long? = if (a < 0 || b < 0 || a > Long.MAX_VALUE - b) null else a + b
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
