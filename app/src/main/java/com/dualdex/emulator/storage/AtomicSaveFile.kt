package com.dualdex.emulator.storage

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicSaveFile {

    private const val TAG = "AtomicSaveFile"

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
                try {
                    fos.fd.sync()
                } catch (e: Exception) {
                    Log.w(TAG, "fsync warning on tmp file: ${e.message}")
                }
            }

            // 2. Verify temp file bytes
            if (!tmpFile.exists() || tmpFile.length() != expectedSize) {
                Log.e(TAG, "Temp write verification failed for ${tmpFile.name}: expected $expectedSize, got ${tmpFile.length()}")
                if (tmpFile.exists()) tmpFile.delete()
                return false
            }

            // 3. Retain/rotate previous canonical file as .bak
            if (targetFile.exists() && targetFile.length() > 0L) {
                try {
                    targetFile.copyTo(bakFile, overwrite = true)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create backup copy: ${e.message}")
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
                Log.w(TAG, "Atomic move failed, falling back to rename: ${e.message}")
                if (targetFile.exists()) targetFile.delete()
                tmpFile.renameTo(targetFile)
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
                    try {
                        output.fd.sync()
                    } catch (e: Exception) {
                        Log.w(TAG, "fsync warning on tmp file: ${e.message}")
                    }
                }
            }

            // 2. Verify temp file
            if (!tmpFile.exists() || tmpFile.length() != expectedSize) {
                Log.e(TAG, "Temp staging copy verification failed for ${tmpFile.name}: expected $expectedSize, got ${tmpFile.length()}")
                if (tmpFile.exists()) tmpFile.delete()
                return false
            }

            // 3. Retain/rotate previous canonical file as .bak
            if (targetFile.exists() && targetFile.length() > 0L) {
                try {
                    targetFile.copyTo(bakFile, overwrite = true)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create backup copy: ${e.message}")
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
                Log.w(TAG, "Atomic move failed, falling back to rename: ${e.message}")
                if (targetFile.exists()) targetFile.delete()
                tmpFile.renameTo(targetFile)
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
}
