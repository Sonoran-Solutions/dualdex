package com.dualdex.emulator.storage

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicSaveFile {

    private const val TAG = "AtomicSaveFile"

    enum class RecoveryOutcome {
        UNAVAILABLE,
        TARGET_VALID,
        RESTORED_WITH_ORIGINAL_TIMESTAMP,
        RESTORED_WITHOUT_ORIGINAL_TIMESTAMP
    }

    @Volatile
    var syncHook: ((FileOutputStream) -> Unit)? = null

    @Volatile
    var backupHook: ((File, File) -> Boolean)? = null

    @Volatile
    var preserveTimestampHook: ((File, Long) -> Boolean)? = null

    fun backupProvenanceFile(targetFile: File): File =
        File(targetFile.parentFile, "${targetFile.name}.bak.mtime")

    fun autoLoadExcludedFile(targetFile: File): File =
        File(targetFile.parentFile, "${targetFile.name}.no_autoload")

    fun isAutoLoadExcluded(targetFile: File): Boolean =
        autoLoadExcludedFile(targetFile).exists()

    fun clearAutoLoadExcluded(targetFile: File): Boolean {
        val file = autoLoadExcludedFile(targetFile)
        return !file.exists() || file.delete()
    }

    fun isInternalSidecarName(fileName: String): Boolean =
        fileName.endsWith(".tmp") ||
            fileName.endsWith(".bak") ||
            fileName.endsWith(".bak.mtime") ||
            fileName.endsWith(".no_autoload")

    private fun applyLastModified(file: File, mtime: Long): Boolean {
        val hook = preserveTimestampHook
        return if (hook != null) {
            hook(file, mtime)
        } else {
            file.setLastModified(mtime)
        }
    }

    private fun moveFileAtomically(src: File, dst: File): Boolean = try {
        Files.move(
            src.toPath(),
            dst.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "Atomic move failed, falling back to REPLACE_EXISTING move: ${e.message}")
        try {
            Files.move(
                src.toPath(),
                dst.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (e2: Exception) {
            Log.w(TAG, "Replace move failed, falling back to rename without delete: ${e2.message}")
            // Never delete dst beforehand to avoid leaving an empty window!
            src.renameTo(dst)
        }
    }

    private fun writeSidecarAtomically(sidecarFile: File, content: String): Boolean {
        val parent = sidecarFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) return false
        val tmpFile = File(parent, "${sidecarFile.name}.tmp")
        return try {
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
            if (!tmpFile.exists() || tmpFile.length() != bytes.size.toLong()) {
                if (tmpFile.exists()) tmpFile.delete()
                return false
            }
            val moved = moveFileAtomically(tmpFile, sidecarFile)
            if (moved && sidecarFile.exists() && sidecarFile.length() == bytes.size.toLong()) {
                true
            } else {
                if (tmpFile.exists()) tmpFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write sidecar ${sidecarFile.name}: ${e.message}")
            if (tmpFile.exists()) tmpFile.delete()
            false
        }
    }

    private fun readVerifiedBackupMtime(targetFile: File, bakFile: File, expectedSize: Long): Long? {
        val provenanceFile = backupProvenanceFile(targetFile)
        if (!provenanceFile.isFile || provenanceFile.length() == 0L) return null
        val raw = runCatching { provenanceFile.readText(Charsets.UTF_8).trim() }.getOrNull() ?: return null
        val parts = raw.split(':')
        if (parts.size != 2) return null
        val recordedMtime = parts[0].toLongOrNull() ?: return null
        val recordedSize = parts[1].toLongOrNull() ?: return null
        if (recordedMtime <= 0L || recordedSize != expectedSize) return null
        if (bakFile.lastModified() != recordedMtime) return null
        return recordedMtime
    }

    /**
     * Rotates the existing canonical [targetFile] into [bakFile].
     * Deletes any stale provenance sidecar before mutating [bakFile], and publishes
     * `<target>.bak.mtime` ONLY after verifying that [targetFile] was not auto-load excluded and
     * that [bakFile] retained [targetFile]'s exact original modification timestamp.
     */
    private fun rotateBackup(targetFile: File, bakFile: File): Boolean {
        val origSize = targetFile.length()
        val origMtime = targetFile.lastModified()
        val origExcluded = isAutoLoadExcluded(targetFile)
        val provenanceFile = backupProvenanceFile(targetFile)

        if (provenanceFile.exists() && !provenanceFile.delete()) {
            Log.e(TAG, "Aborting backup rotation for ${targetFile.name}: could not clear stale backup provenance")
            return false
        }

        val backupOk = try {
            val hook = backupHook
            if (hook != null) {
                hook(targetFile, bakFile)
            } else {
                targetFile.copyTo(bakFile, overwrite = true)
                val copiedOk = bakFile.exists() && bakFile.isFile && bakFile.length() == origSize
                if (copiedOk && !origExcluded && origMtime > 0L) {
                    val mtimePreserved = applyLastModified(bakFile, origMtime) &&
                        bakFile.lastModified() == origMtime
                    if (mtimePreserved) {
                        val provenanceWritten = writeSidecarAtomically(
                            provenanceFile,
                            "$origMtime:$origSize"
                        )
                        if (!provenanceWritten && provenanceFile.exists()) {
                            provenanceFile.delete()
                        }
                    } else if (provenanceFile.exists()) {
                        provenanceFile.delete()
                    }
                }
                copiedOk
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup copy: ${e.message}")
            if (provenanceFile.exists()) provenanceFile.delete()
            false
        }
        if (!backupOk && provenanceFile.exists()) {
            provenanceFile.delete()
        }
        return backupOk
    }

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
                if (!rotateBackup(targetFile, bakFile)) {
                    Log.e(TAG, "Aborting atomic write: failed to create verified previous-good backup")
                    if (tmpFile.exists()) tmpFile.delete()
                    return false
                }
            }

            // 4. Atomically replace canonical
            val moved = moveFileAtomically(tmpFile, targetFile)

            // 5. Verify final file exists and has expected size
            if (moved && targetFile.exists() && targetFile.length() == expectedSize) {
                clearAutoLoadExcluded(targetFile)
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
                if (!rotateBackup(targetFile, bakFile)) {
                    Log.e(TAG, "Aborting atomic staging copy: failed to create verified previous-good backup")
                    if (tmpFile.exists()) tmpFile.delete()
                    return false
                }
            }

            // 4. Atomically replace canonical
            val moved = moveFileAtomically(tmpFile, targetFile)

            // 5. Verify final file
            if (moved && targetFile.exists() && targetFile.length() == expectedSize) {
                clearAutoLoadExcluded(targetFile)
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
     * Restore canonical targetFile from its .bak sibling, preserving the backup's verified
     * original modification timestamp so recovered backups never masquerade as fresher than
     * newer saves.
     *
     * Writes a durable `<target>.no_autoload` exclusion sidecar BEFORE copying any `.bak` bytes
     * into `targetFile`, and removes it ONLY after verifying both backup provenance (`.bak.mtime`)
     * and the restored file's exact original timestamp. Any backup with unknown provenance (such
     * as pre-fix backups or failed rotation timestamps) or failed restore timestamp preservation
     * stays durably excluded from automatic boot loading across all callers and reopens, while
     * remaining available for explicit user restoration.
     */
    fun restoreBackupDetailed(targetFile: File): RecoveryOutcome {
        val bakFile = File(targetFile.parentFile, "${targetFile.name}.bak")
        if (!bakFile.exists() || !bakFile.isFile || bakFile.length() == 0L) {
            return RecoveryOutcome.UNAVAILABLE
        }
        val expectedSize = bakFile.length()
        val verifiedOriginalMtime = readVerifiedBackupMtime(targetFile, bakFile, expectedSize)

        val excludeFile = autoLoadExcludedFile(targetFile)
        if (!writeSidecarAtomically(excludeFile, "1")) {
            Log.e(TAG, "Aborting backup restore for ${targetFile.name}: could not write durable auto-load exclusion marker")
            return RecoveryOutcome.UNAVAILABLE
        }

        return try {
            bakFile.copyTo(targetFile, overwrite = true)
            if (!targetFile.exists() || targetFile.length() != expectedSize) {
                return RecoveryOutcome.UNAVAILABLE
            }
            val timestampPreserved = if (verifiedOriginalMtime != null) {
                applyLastModified(targetFile, verifiedOriginalMtime) &&
                    targetFile.lastModified() == verifiedOriginalMtime
            } else {
                false
            }
            if (timestampPreserved) {
                if (clearAutoLoadExcluded(targetFile)) {
                    RecoveryOutcome.RESTORED_WITH_ORIGINAL_TIMESTAMP
                } else {
                    Log.w(TAG, "Restored ${targetFile.name} with verified timestamp but could not clear auto-load exclusion marker")
                    RecoveryOutcome.RESTORED_WITHOUT_ORIGINAL_TIMESTAMP
                }
            } else {
                Log.w(TAG, "Restored ${targetFile.name} from .bak without verified original timestamp; keeping durable auto-load exclusion")
                RecoveryOutcome.RESTORED_WITHOUT_ORIGINAL_TIMESTAMP
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup for ${targetFile.name}: ${e.message}")
            RecoveryOutcome.UNAVAILABLE
        }
    }

    /**
     * Restore canonical targetFile from its .bak sibling.
     */
    fun restoreBackup(targetFile: File): Boolean =
        restoreBackupDetailed(targetFile) != RecoveryOutcome.UNAVAILABLE

    /**
     * Detects interrupted .tmp / .bak states and recovers the last known valid canonical file,
     * reporting whether the target was already valid or restored from `.bak` with/without
     * establishing the backup's original modification timestamp.
     */
    fun recoverInterruptedDetailed(targetFile: File, expectedSize: Long? = null): RecoveryOutcome {
        val parent = targetFile.parentFile ?: return RecoveryOutcome.UNAVAILABLE
        val tmpFile = File(parent, "${targetFile.name}.tmp")
        val bakFile = File(parent, "${targetFile.name}.bak")

        if (expectedSize != null && expectedSize > 0L) {
            val isTargetInvalid = !targetFile.exists() || targetFile.length() == 0L || targetFile.length() != expectedSize
            if (isTargetInvalid) {
                if (bakFile.exists() && bakFile.isFile && bakFile.length() == expectedSize) {
                    Log.w(TAG, "Interrupted write detected for ${targetFile.name} (expected $expectedSize bytes, target has ${if (targetFile.exists()) targetFile.length() else -1}). Restoring from .bak")
                    val outcome = restoreBackupDetailed(targetFile)
                    if (tmpFile.exists()) tmpFile.delete()
                    return outcome
                }
                return RecoveryOutcome.UNAVAILABLE
            }

            // Target exists and matches expectedSize
            if (tmpFile.exists()) {
                Log.w(TAG, "Cleaning up leftover .tmp file for ${targetFile.name}")
                tmpFile.delete()
            }
            return RecoveryOutcome.TARGET_VALID
        }

        // Expected size not known: conservative fallback
        // Case 1: Target file is missing or empty, but valid .bak exists -> restore from .bak
        if (!targetFile.exists() || targetFile.length() == 0L) {
            if (bakFile.exists() && bakFile.isFile && bakFile.length() > 0L) {
                Log.w(TAG, "Interrupted write detected for ${targetFile.name}. Restoring from .bak (${bakFile.length()} bytes)")
                val outcome = restoreBackupDetailed(targetFile)
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                return outcome
            }
        }

        // Case 2: Target file is valid, clean up any lingering .tmp file
        if (targetFile.exists() && targetFile.length() > 0L) {
            if (tmpFile.exists()) {
                Log.w(TAG, "Cleaning up leftover .tmp file for ${targetFile.name}")
                tmpFile.delete()
            }
            return RecoveryOutcome.TARGET_VALID
        }

        return RecoveryOutcome.UNAVAILABLE
    }

    /**
     * Detects interrupted .tmp / .bak states and recovers the last known valid canonical file.
     * When expectedSize is provided, treats truncated or mismatched canonical files as invalid
     * and recovers from a matching .bak file.
     * Guaranteed to never silently discard a recoverable .bak file.
     */
    fun recoverInterrupted(targetFile: File, expectedSize: Long? = null): Boolean =
        recoverInterruptedDetailed(targetFile, expectedSize) != RecoveryOutcome.UNAVAILABLE
}
