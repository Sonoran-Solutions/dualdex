package com.dualdex.emulator.storage

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicSaveFile {

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
    fun copyFromStaging(stagingFile: File, targetFile: File): Boolean {
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
