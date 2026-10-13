package com.dualdex.emulator.storage

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

object AtomicSaveFile {

    data class Version(val size: Long, val modified: Long, val sha256: String)

    /** Missing is distinct from unreadable, links, directories, and a file changing during hashing. */
    fun version(file: File, maxBytes: Long): Version? {
        val path = file.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw IOException("Unsafe save file")
        val size = Files.size(path)
        val modified = file.lastModified()
        if (size > maxBytes) throw IOException("Save is too large to review")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (total > size - count) throw IOException("Save changed while reading")
                total += count
                digest.update(buffer, 0, count)
            }
            if (total != size || Files.size(path) != size || file.lastModified() != modified)
                throw IOException("Save changed while reading")
        }
        return Version(size, modified, digest.digest().joinToString("") { "%02x".format(it) })
    }

    internal fun syncDirectory(directory: File) {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }

    /** Copy to a new private file; never replaces an existing recovery copy. */
    internal fun copyReviewed(source: File, destination: File, expected: Version) {
        val parent = checkNotNull(destination.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create restore directory")
        Files.createFile(destination.toPath())
        source.inputStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (total > expected.size - count) throw IOException("Restore source changed")
                    total += count
                    output.write(buffer, 0, count)
                }
                if (total != expected.size) throw IOException("Truncated restore source")
                if (!destination.setLastModified(expected.modified)) throw IOException("Cannot preserve restore date")
                syncHook?.invoke(output) ?: output.fd.sync()
            }
        }
        if (version(source, expected.size) != expected || version(destination, expected.size) != expected)
            throw IOException("Restore copy failed integrity verification")
        syncDirectory(parent)
    }

    /**
     * Caller holds the save lock. Rechecks the exact reviewed versions at the commit boundary.
     * An existing local file is durably retained at a unique path; ordinary .bak history is untouched.
     * No non-atomic fallback. False means committed, but final directory sync could not be confirmed.
     */
    internal fun commitRestore(
        source: File, target: File, sourceVersion: Version, localVersion: Version?, retained: File,
        beforeCommit: () -> Unit,
        move: (File, File) -> Unit = { from, to ->
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    ): Boolean {
        val parent = checkNotNull(target.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create save directory")
        val temp = File(parent, ".${target.name}.restore-${UUID.randomUUID()}.tmp")
        fun unchanged() {
            if (version(source, sourceVersion.size) != sourceVersion ||
                version(target, maxOf(sourceVersion.size, localVersion?.size ?: 0)) != localVersion)
                throw IOException("Files changed since review; review again")
        }
        try {
            unchanged()
            copyReviewed(source, temp, sourceVersion)
            if (localVersion != null) copyReviewed(target, retained, localVersion)
            beforeCommit()
            unchanged()
            if (version(temp, sourceVersion.size) != sourceVersion) throw IOException("Prepared restore changed")
            syncDirectory(parent)
            move(temp, target)
            return runCatching { syncDirectory(parent) }.isSuccess
        } finally {
            temp.delete()
        }
    }

    private const val TAG = "AtomicSaveFile"

    @Volatile
    var syncHook: ((FileOutputStream) -> Unit)? = null

    @Volatile
    var backupHook: ((File, File) -> Boolean)? = null

    /**
     * Atomically write a byte array to targetFile with durability and previous-good backup (.bak).
     */
    fun writeBytes(targetFile: File, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) {
            Log.e(TAG, "Refusing to write 0-byte file to ${targetFile.absolutePath}")
            return false
        }

        val parent = targetFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) {
            Log.e(TAG, "Failed to create directory: ${parent.absolutePath}")
            return false
        }

        val tmpFile = File(parent, "${targetFile.name}.tmp")
        val bakFile = File(parent, "${targetFile.name}.bak")
        val expectedSize = bytes.size.toLong()

        try {
            // 1. Write complete new file to temp
            FileOutputStream(tmpFile).use { fos ->
                fos.write(bytes)
                fos.flush()
                val hook = syncHook
                if (hook != null) {
                    hook(fos)
                } else {
                    fos.fd.sync()
                }
            }

            // 2. Verify temp file bytes
            if (!tmpFile.exists() || tmpFile.length() != expectedSize) {
                Log.e(TAG, "Temp write verification failed for ${tmpFile.name}: expected $expectedSize, got ${tmpFile.length()}")
                if (tmpFile.exists()) tmpFile.delete()
                return false
            }

            // 3. Retain/rotate previous canonical file as .bak (FAIL CLOSED if backup cannot be created)
            if (targetFile.exists() && targetFile.length() > 0L) {
                val origSize = targetFile.length()
                val backupOk = try {
                    val hook = backupHook
                    if (hook != null) {
                        hook(targetFile, bakFile)
                    } else {
                        targetFile.copyTo(bakFile, overwrite = true)
                        bakFile.exists() && bakFile.isFile && bakFile.length() == origSize
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create backup copy: ${e.message}")
                    false
                }
                if (!backupOk) {
                    Log.e(TAG, "Aborting atomic write: failed to create verified previous-good backup")
                    if (tmpFile.exists()) tmpFile.delete()
                    return false
                }
            }

            // 4. Atomically replace canonical
            val moved = try {
                Files.move(
                    tmpFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                true
            } catch (e: Exception) {
                Log.w(TAG, "Atomic move failed, falling back to REPLACE_EXISTING move: ${e.message}")
                try {
                    Files.move(
                        tmpFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    true
                } catch (e2: Exception) {
                    Log.w(TAG, "Replace move failed, falling back to rename without delete: ${e2.message}")
                    // Never delete targetFile beforehand to avoid leaving an empty window!
                    tmpFile.renameTo(targetFile)
                }
            }

            // 5. Verify final file exists and has expected size
            if (moved && targetFile.exists() && targetFile.length() == expectedSize) {
                return true
            }

            // If replacement failed, attempt recovery from backup
            Log.e(TAG, "Final verification failed for ${targetFile.absolutePath}")
            if (bakFile.exists() && (!targetFile.exists() || targetFile.length() == 0L)) {
                restoreBackup(targetFile)
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception during atomic write to ${targetFile.absolutePath}: ${e.message}", e)
            if (tmpFile.exists()) tmpFile.delete()
            return false
        }
    }

    /**
     * Atomically copy a staging file to targetFile with durability and previous-good backup (.bak).
     */
    fun copyFromStaging(stagingFile: File, targetFile: File, modifiedTime: Long? = null): Boolean {
        if (!stagingFile.exists() || stagingFile.length() == 0L) {
            Log.e(TAG, "Staging file missing or empty: ${stagingFile.absolutePath}")
            return false
        }

        val parent = targetFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) {
            Log.e(TAG, "Failed to create directory: ${parent.absolutePath}")
            return false
        }

        val expectedSize = stagingFile.length()
        val tmpFile = File(parent, "${targetFile.name}.tmp")
        val bakFile = File(parent, "${targetFile.name}.bak")

        try {
            // 1. Copy staging to temp file with fsync
            stagingFile.inputStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                    if (modifiedTime != null && (!tmpFile.setLastModified(modifiedTime) || tmpFile.lastModified() != modifiedTime))
                        throw IOException("Cannot persist state ordering timestamp")
                    val hook = syncHook
                    if (hook != null) {
                        hook(output)
                    } else {
                        output.fd.sync()
                    }
                }
            }

            // 2. Verify temp file
            if (!tmpFile.exists() || tmpFile.length() != expectedSize) {
                Log.e(TAG, "Temp staging copy verification failed for ${tmpFile.name}: expected $expectedSize, got ${tmpFile.length()}")
                if (tmpFile.exists()) tmpFile.delete()
                return false
            }

            // 3. Retain/rotate previous canonical file as .bak (FAIL CLOSED if backup cannot be created)
            if (targetFile.exists() && targetFile.length() > 0L) {
                val origSize = targetFile.length()
                val backupOk = try {
                    val hook = backupHook
                    if (hook != null) {
                        hook(targetFile, bakFile)
                    } else {
                        targetFile.copyTo(bakFile, overwrite = true)
                        bakFile.exists() && bakFile.isFile && bakFile.length() == origSize
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create backup copy: ${e.message}")
                    false
                }
                if (!backupOk) {
                    Log.e(TAG, "Aborting atomic staging copy: failed to create verified previous-good backup")
                    if (tmpFile.exists()) tmpFile.delete()
                    return false
                }
            }

            // 4. Atomically replace canonical
            val moved = try {
                Files.move(
                    tmpFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                true
            } catch (e: Exception) {
                Log.w(TAG, "Atomic move failed, falling back to REPLACE_EXISTING move: ${e.message}")
                try {
                    Files.move(
                        tmpFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    true
                } catch (e2: Exception) {
                    Log.w(TAG, "Replace move failed, falling back to rename without delete: ${e2.message}")
                    tmpFile.renameTo(targetFile)
                }
            }

            // 5. Verify final file
            if (moved && targetFile.exists() && targetFile.length() == expectedSize) {
                return true
            }

            Log.e(TAG, "Final verification failed for ${targetFile.absolutePath}")
            if (bakFile.exists() && (!targetFile.exists() || targetFile.length() == 0L)) {
                restoreBackup(targetFile)
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception during atomic copy to ${targetFile.absolutePath}: ${e.message}", e)
            if (tmpFile.exists()) tmpFile.delete()
            return false
        }
    }

    /**
     * Restore canonical targetFile from its .bak sibling.
     */
    fun restoreBackup(targetFile: File): Boolean {
        val bakFile = File(targetFile.parentFile, "${targetFile.name}.bak")
        if (!bakFile.exists() || bakFile.length() == 0L) {
            return false
        }
        return try {
            bakFile.copyTo(targetFile, overwrite = true)
            targetFile.exists() && targetFile.length() == bakFile.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup for ${targetFile.name}: ${e.message}")
            false
        }
    }

    /**
     * Detects interrupted .tmp / .bak states and recovers the last known valid canonical file.
     * When expectedSize is provided, treats truncated or mismatched canonical files as invalid
     * and recovers from a matching .bak file.
     * Guaranteed to never silently discard a recoverable .bak file.
     */
    fun recoverInterrupted(targetFile: File, expectedSize: Long? = null): Boolean {
        val parent = targetFile.parentFile ?: return false
        val tmpFile = File(parent, "${targetFile.name}.tmp")
        val bakFile = File(parent, "${targetFile.name}.bak")

        if (expectedSize != null && expectedSize > 0L) {
            val isTargetInvalid = !targetFile.exists() || targetFile.length() == 0L || targetFile.length() != expectedSize
            if (isTargetInvalid) {
                if (bakFile.exists() && bakFile.isFile && bakFile.length() == expectedSize) {
                    Log.w(TAG, "Interrupted write detected for ${targetFile.name} (expected $expectedSize bytes, target has ${if (targetFile.exists()) targetFile.length() else -1}). Restoring from .bak")
                    val restored = restoreBackup(targetFile)
                    if (tmpFile.exists()) tmpFile.delete()
                    return restored
                }
                return false
            }

            // Target exists and matches expectedSize
            if (tmpFile.exists()) {
                Log.w(TAG, "Cleaning up leftover .tmp file for ${targetFile.name}")
                tmpFile.delete()
            }
            return true
        }

        // Expected size not known: conservative fallback
        // Case 1: Target file is missing or empty, but valid .bak exists -> restore from .bak
        if (!targetFile.exists() || targetFile.length() == 0L) {
            if (bakFile.exists() && bakFile.isFile && bakFile.length() > 0L) {
                Log.w(TAG, "Interrupted write detected for ${targetFile.name}. Restoring from .bak (${bakFile.length()} bytes)")
                val restored = restoreBackup(targetFile)
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                return restored
            }
        }

        // Case 2: Target file is valid, clean up any lingering .tmp file
        if (targetFile.exists() && targetFile.length() > 0L) {
            if (tmpFile.exists()) {
                Log.w(TAG, "Cleaning up leftover .tmp file for ${targetFile.name}")
                tmpFile.delete()
            }
            return true
        }

        return false
    }
}
