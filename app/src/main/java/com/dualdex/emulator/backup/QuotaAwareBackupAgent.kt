package com.dualdex.emulator.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import com.dualdex.emulator.storage.RomSaveMetadata
import com.dualdex.emulator.storage.LegacySaveCatalog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Candidate file-based agent. It is deliberately not registered in AndroidManifest.xml until
 * disposable transport and restore-import acceptance is complete.
 */
class QuotaAwareBackupAgent : BackupAgent() {
    private val tag = "QuotaBackupAgent"
    private val restoreLock = Any()
    private val restoredPaths = linkedSetOf<String>()
    private var restoredBytes = 0L
    private val restoreSession by lazy { File(filesDir, "restore_staging/session-${System.nanoTime()}") }

    /** Key/value backup is intentionally empty; this agent only emits its explicit file allowlist. */
    override fun onBackup(oldState: ParcelFileDescriptor, data: BackupDataOutput, newState: ParcelFileDescriptor) = Unit

    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        BackupStatusStore.set(this, BackupStatusStore.Status.PREPARING)
        when (QuotaBackupSelection.dispatch(
            data.transportFlags and FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED != 0,
            data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER != 0
        )) {
            QuotaBackupSelection.BackupRoute.REFUSAL -> {
                BackupStatusStore.set(this, BackupStatusStore.Status.REFUSED_OR_INCOMPLETE, "Transport not verified")
            }
            QuotaBackupSelection.BackupRoute.DEVICE_TRANSFER -> backupDeviceTransfer(data)
            QuotaBackupSelection.BackupRoute.CLOUD_SNAPSHOT -> backupCloudSnapshot(data)
        }
    }

    private fun backupDeviceTransfer(data: FullBackupDataOutput) {
        try {
            BackupFileTreePolicy.emit(filesDir, legacyCatalogFile()) { entry -> fullBackupFile(entry.file, data) }
            BackupStatusStore.set(this, BackupStatusStore.Status.ESSENTIAL_SELECTED, "Device transfer prepared")
        } catch (e: Exception) {
            Log.w(tag, "Device transfer inventory or emission failed", e)
            BackupStatusStore.set(this, BackupStatusStore.Status.REFUSED_OR_INCOMPLETE, "Device transfer inventory incomplete")
            throw IOException("Device transfer backup refused because its allowlisted file set was incomplete", e)
        }
    }


    private fun verifiedLegacyBindings(): Map<String, QuotaBackupSelection.PortableLegacyBinding> {
        val catalog = LegacySaveCatalog(this)
        val result = linkedMapOf<String, QuotaBackupSelection.PortableLegacyBinding>()
        for (candidate in catalog.discoverCandidates()) {
            val source = candidate.sourceFile
            val identity = candidate.assignedRomHash?.lowercase() ?: continue
            if (!candidate.isAssigned || !identity.matches(Regex("[0-9a-f]{64}")) ||
                !source.name.endsWith(".sav", ignoreCase = true)) continue
            val rel = runCatching { filesDir.canonicalFile.toPath().relativize(source.canonicalFile.toPath())
                .toString().replace(File.separatorChar, '/') }.getOrNull() ?: continue
            val stamp = captureStamp(source, rel) ?: continue
            val binding = QuotaBackupSelection.portableLegacyBinding(
                filesDir.absolutePath, source.absolutePath, stamp.contentSha256, identity, "battery.sav"
            ) ?: continue
            result[source.canonicalPath] = binding
        }
        return result
    }

    private fun backupCloudSnapshot(data: FullBackupDataOutput) {
        val bindings = verifiedLegacyBindings()
        val inventory = SaveBackupInventory(filesDir).scan(bindings)
        val budget = QuotaBackupSelection.budgetFor(data.quota)
        val selection = QuotaBackupSelection.select(inventory.identities, inventory.candidates, inventory.complete, budget)
        if (selection.verdict == QuotaBackupSelection.Verdict.REFUSAL || selection.selected.isEmpty()) {
            BackupStatusStore.set(this, BackupStatusStore.Status.REFUSED_OR_INCOMPLETE, "Essential save coverage unavailable")
            return
        }
        val entries = selection.selected.map { candidate ->
            val source = File(filesDir, candidate.relativePath)
            val stamp = inventory.sourceStamps[candidate.relativePath] ?: run {
                BackupStatusStore.set(this, BackupStatusStore.Status.REFUSED_OR_INCOMPLETE, "Save inventory changed")
                return
            }
            CloudSnapshotArchive.Entry(source, stamp, candidate.romSha256.lowercase(), candidate.category.name,
                bindings[source.canonicalPath])
        }
        val destination = File(filesDir, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH)
        val archive = CloudSnapshotArchive.build(entries, filesDir, cacheDir, destination)
        if (archive == null) {
            BackupStatusStore.set(this, BackupStatusStore.Status.REFUSED_OR_INCOMPLETE, "Snapshot validation failed")
            return
        }
        fullBackupFile(destination, data)
        BackupStatusStore.set(this, if (selection.verdict == QuotaBackupSelection.Verdict.PARTIAL)
            BackupStatusStore.Status.OPTIONAL_OMITTED else BackupStatusStore.Status.ESSENTIAL_SELECTED)
    }

    override fun onQuotaExceeded(backupDataBytes: Long, quotaBytes: Long) {
        BackupStatusStore.set(this, BackupStatusStore.Status.REFUSED_OR_INCOMPLETE, "Transport quota exceeded")
        Log.w(tag, "Transport rejected backup payload (bytes=$backupDataBytes, quota=$quotaBytes)")
    }

    /** Redirect every restore file to private staging. Never use BackupAgent's direct-to-destination default. */
    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        val relative = restoreRelativePath(destination) ?: throw IOException("Restore destination is outside the allowlist")
        if (!allowedRestorePath(relative, type)) throw IOException("Restore path is not allowlisted")
        val staged = File(restoreSession, relative)
        synchronized(restoreLock) {
            if (!restoredPaths.add(relative)) throw IOException("Duplicate restore destination")
            if (restoredPaths.size > BackupFileTreePolicy.MAX_D2D_ENTRIES) throw IOException("Restore entry limit exceeded")
            if (type == TYPE_DIRECTORY) {
                if (size != 0L || !safeStagingPath(staged) || !staged.mkdirs() && !staged.isDirectory)
                    throw IOException("Invalid restore directory")
                return
            }
            if (type != TYPE_FILE) throw IOException("Invalid restore file type")
            if (relative == QuotaBackupSelection.CLOUD_SNAPSHOT_PATH) {
                if (size !in 1..QuotaBackupSelection.MAX_PAYLOAD_BYTES ||
                    restoredBytes > BackupFileTreePolicy.MAX_D2D_STAGING_BYTES - size) throw IOException("Restore size limit exceeded")
            } else {
                BackupFileTreePolicy.checkFileAdmission(relative, size, restoredBytes, restoredPaths.size)
            }
            val parent = staged.parentFile ?: throw IOException("Invalid restore path")
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create restore staging directory")
            if (!safeStagingPath(staged)) throw IOException("Symlink in restore staging")
            val temp = File(parent, "${staged.name}.part-${System.nanoTime()}")
            try {
                ParcelFileDescriptor.dup(data.fileDescriptor).use { duplicate ->
                    FileInputStream(duplicate.fileDescriptor).use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(8192); var remaining = size
                            while (remaining > 0) {
                                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (read < 0) throw IOException("Truncated restore file")
                                output.write(buffer, 0, read); remaining -= read
                            }
                            output.fd.sync()
                        }
                    }
                }
                if (temp.length() != size || !temp.renameTo(staged)) throw IOException("Restore staging write failed")
                restoredBytes += size
            } finally { if (temp.exists()) temp.delete() }
        }
    }

    override fun onRestoreFinished() {
        val files = mutableListOf<Pair<String, File>>()
        if (restoreSession.exists()) {
            Files.walkFileTree(restoreSession.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                    if (Files.isSymbolicLink(file) || !attrs.isRegularFile) throw IOException("Unsafe staged restore entry")
                    val relative = restoreSession.toPath().relativize(file).toString().replace(File.separatorChar, '/')
                    files += relative to file.toFile()
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            })
        }
        if (files.isEmpty()) {
            BackupStatusStore.set(this, BackupStatusStore.Status.NOT_VERIFIED)
            return
        }
        val cloud = files.singleOrNull { it.first == "cloud_backup/dualdex-snapshot-v1.zip" }
        if (cloud != null) {
            if (files.size != 1 || CloudSnapshotArchive.validate(cloud.second) == null) {
                BackupStatusStore.set(this, BackupStatusStore.Status.RESTORE_VALIDATION_FAILED)
                return
            }
            BackupStatusStore.set(this, BackupStatusStore.Status.RESTORE_PENDING, "Verified data is staged; import requires a later user review")
            return
        }
        if (files.all { validFullTreeEntry(it.first, it.second) }) {
            BackupStatusStore.set(this, BackupStatusStore.Status.RESTORE_PENDING, "Files staged for review; no saves changed")
        } else {
            BackupStatusStore.set(this, BackupStatusStore.Status.RESTORE_VALIDATION_FAILED)
        }
    }


    private fun safeStagingPath(target: File): Boolean = runCatching {
        val storageRoot = filesDir.toPath().toAbsolutePath().normalize()
        val base = restoreSession.toPath().toAbsolutePath().normalize()
        val normalized = target.toPath().toAbsolutePath().normalize()
        if (!base.startsWith(storageRoot) || !normalized.startsWith(base)) return@runCatching false
        var current = storageRoot
        if (Files.isSymbolicLink(current)) return@runCatching false
        for (part in storageRoot.relativize(normalized)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) return@runCatching false
        }
        true
    }.getOrDefault(false)


    private fun legacyCatalogFile(): File = File(File(filesDir.parentFile, "shared_prefs"), "dualdex_legacy_catalog.xml")

    private fun restoreRelativePath(destination: File): String? {
        val fileRoot = filesDir.canonicalFile.toPath()
        val target = destination.canonicalFile.toPath()
        if (target.startsWith(fileRoot)) return fileRoot.relativize(target).toString().replace(File.separatorChar, '/')
        val catalog = legacyCatalogFile().canonicalFile.toPath()
        if (target == catalog) return "sharedpref/dualdex_legacy_catalog.xml"
        return null
    }

    private fun allowedRestorePath(path: String, type: Int): Boolean {
        if (type == TYPE_DIRECTORY) return path == "cloud_backup" || BackupFileTreePolicy.isAllowedPath(path, directory = true)
        if (type != TYPE_FILE) return false
        return path == QuotaBackupSelection.CLOUD_SNAPSHOT_PATH || BackupFileTreePolicy.isAllowedPath(path, directory = false)
    }

    private fun validFullTreeEntry(path: String, file: File): Boolean {
        if (path == "cloud_backup/dualdex-snapshot-v1.zip") return CloudSnapshotArchive.validate(file) != null
        if (runCatching { BackupFileTreePolicy.checkFileAdmission(path, file.length(), 0, 1) }.isFailure) return false
        val parts = path.split('/')
        return if (parts.firstOrNull() == "saves_v2" && parts.lastOrNull() == "metadata.json") {
            runCatching { RomSaveMetadata.fromJson(file.readText())?.sha256?.equals(parts[1], true) == true }.getOrDefault(false)
        } else true
    }

    private fun captureStamp(file: File, path: String): QuotaBackupSelection.SourceStamp? {
        if (!file.isFile || file.length() !in 1..QuotaBackupSelection.MAX_ENTRY_BYTES) return null
        val size = file.length(); val modified = file.lastModified()
        val digest = FileInputStream(file).use { input ->
            val md = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192); var total = 0L
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                if (total > size - count) return null
                total += count; md.update(buffer, 0, count)
            }
            if (total != size || file.length() != size || file.lastModified() != modified) return null
            md.digest().joinToString("") { "%02x".format(it) }
        }
        return QuotaBackupSelection.SourceStamp(path, size, modified, digest)
    }
}
