package com.dualdex.emulator

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.dualdex.settings.SettingsManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SaveSlotInfo(
    val slotIndex: Int,
    val exists: Boolean,
    val timestampMs: Long,
    val formattedDate: String,
    val sizeBytes: Long
)

class SaveStateManager(private val context: Context) {

    private val settingsManager by lazy { SettingsManager(context) }
    private val saveLock = Any()

    private val stagingDir: File
        get() = File(context.filesDir, "save_staging").apply {
            if (!exists()) mkdirs()
        }

    private val fallbackBaseDir: File
        get() = File(context.filesDir, "saves_fallback").apply {
            if (!exists()) mkdirs()
        }

    fun isUsingSaf(): Boolean {
        return getSafFolder() != null
    }

    fun getSaveDirectoryDescription(): String {
        val safUri = settingsManager.savesFolderUri
        if (safUri != null) {
            val doc = getSafFolder()
            if (doc != null) {
                return doc.name?.let { "SAF ($it)" } ?: "SAF Folder"
            }
        }
        return "Internal App Storage (Private)"
    }

    private fun getSafFolder(): DocumentFile? {
        val uriStr = settingsManager.savesFolderUri ?: return null
        return try {
            val treeUri = Uri.parse(uriStr)
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root != null && root.canWrite()) root else null
        } catch (e: Exception) {
            Log.w("SaveStateManager", "Failed to access SAF saves folder: ${e.message}")
            null
        }
    }

    private fun getOrCreateSafRomDir(safRoot: DocumentFile, storageKey: String): DocumentFile? {
        return try {
            val rootName = safRoot.name ?: ""
            val savesRoot = when {
                rootName.equals("Saves", ignoreCase = true) -> safRoot
                rootName.equals("DualDex", ignoreCase = true) -> {
                    safRoot.findFile("Saves") ?: safRoot.createDirectory("Saves") ?: safRoot
                }
                else -> {
                    val dualDexDir = safRoot.findFile("DualDex") ?: safRoot.createDirectory("DualDex") ?: safRoot
                    dualDexDir.findFile("Saves") ?: dualDexDir.createDirectory("Saves") ?: dualDexDir
                }
            }
            savesRoot.findFile(storageKey) ?: savesRoot.createDirectory(storageKey)
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error creating SAF directory for $storageKey: ${e.message}", e)
            null
        }
    }

    private fun getFallbackRomDir(storageKey: String): File {
        return File(fallbackBaseDir, storageKey).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getStagingFile(identity: RomIdentity, fileName: String): File {
        return File(stagingDir, "${identity.storageKey}__$fileName")
    }

    private fun canonicalFileExists(identity: RomIdentity, fileName: String): Boolean {
        val safRoot = getSafFolder()
        if (safRoot != null) {
            val romDir = getOrCreateSafRomDir(safRoot, identity.storageKey)
            if (romDir != null) {
                val fileDoc = romDir.findFile(fileName)
                if (fileDoc != null && fileDoc.exists() && fileDoc.length() > 0L) {
                    return true
                }
            }
        }
        val fallbackFile = File(getFallbackRomDir(identity.storageKey), fileName)
        return fallbackFile.exists() && fallbackFile.length() > 0L
    }

    private fun copyCanonicalToStaging(identity: RomIdentity, fileName: String, stagingFile: File): Boolean {
        val safRoot = getSafFolder()
        if (safRoot != null) {
            try {
                val romDir = getOrCreateSafRomDir(safRoot, identity.storageKey)
                val doc = romDir?.findFile(fileName)
                if (doc != null && doc.exists() && doc.length() > 0L) {
                    context.contentResolver.openInputStream(doc.uri)?.use { input ->
                        FileOutputStream(stagingFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (stagingFile.exists() && stagingFile.length() > 0L) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w("SaveStateManager", "Error copying from SAF to staging: ${e.message}")
            }
        }

        // Fallback to internal storage
        val fallbackFile = File(getFallbackRomDir(identity.storageKey), fileName)
        if (fallbackFile.exists() && fallbackFile.length() > 0L) {
            fallbackFile.copyTo(stagingFile, overwrite = true)
            return stagingFile.exists() && stagingFile.length() > 0L
        }

        return false
    }

    private fun copyStagingToCanonical(identity: RomIdentity, fileName: String, stagingFile: File): Boolean {
        if (!stagingFile.exists() || stagingFile.length() == 0L) return false

        var writtenToSaf = false
        val safRoot = getSafFolder()
        if (safRoot != null) {
            try {
                val romDir = getOrCreateSafRomDir(safRoot, identity.storageKey)
                if (romDir != null) {
                    val doc = romDir.findFile(fileName) ?: romDir.createFile("application/octet-stream", fileName)
                    if (doc != null) {
                        context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                            FileInputStream(stagingFile).use { input ->
                                input.copyTo(output)
                            }
                        }
                        writtenToSaf = true
                    }
                }
            } catch (e: Exception) {
                Log.e("SaveStateManager", "Failed to write staging to SAF for $fileName: ${e.message}", e)
            }
        }

        // Always update fallback directory as well for redundancy and safe offline backup
        try {
            val fallbackDir = getFallbackRomDir(identity.storageKey)
            val tempFile = File(fallbackDir, "$fileName.tmp")
            stagingFile.copyTo(tempFile, overwrite = true)
            val targetFile = File(fallbackDir, fileName)
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Failed to update fallback copy for $fileName: ${e.message}", e)
        }

        return writtenToSaf || safRoot == null
    }

    private fun ensureLegacyMigrated(identity: RomIdentity) {
        if (canonicalFileExists(identity, "battery.sav")) return

        val legacyDir = File(context.filesDir, "saves")
        if (!legacyDir.exists() || !legacyDir.isDirectory) return

        val cleanTitle = identity.displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val candidates = listOf(
            "$cleanTitle.sav",
            "${identity.sanitizedTitle}.sav",
            "current_game.sav"
        ).distinct()

        for (candidate in candidates) {
            val legacySav = File(legacyDir, candidate)
            if (legacySav.exists() && legacySav.length() > 0L) {
                Log.i("SaveStateManager", "Migrating legacy save $candidate to ${identity.storageKey}/battery.sav")
                val staging = getStagingFile(identity, "battery.sav")
                legacySav.copyTo(staging, overwrite = true)
                copyStagingToCanonical(identity, "battery.sav", staging)

                // Migrate corresponding save states if present
                val baseName = candidate.removeSuffix(".sav")
                for (slot in 1..5) {
                    val legacySlot = File(legacyDir, "${baseName}_slot_${slot}.state")
                    if (legacySlot.exists() && legacySlot.length() > 0L) {
                        val slotStaging = getStagingFile(identity, "slot_${slot}.state")
                        legacySlot.copyTo(slotStaging, overwrite = true)
                        copyStagingToCanonical(identity, "slot_${slot}.state", slotStaging)
                        legacySlot.renameTo(File(legacyDir, "${legacySlot.name}.migrated.bak"))
                    }
                }
                val legacyQuick = File(legacyDir, "${baseName}_quicksave.state")
                if (legacyQuick.exists() && legacyQuick.length() > 0L) {
                    val quickStaging = getStagingFile(identity, "quicksave.state")
                    legacyQuick.copyTo(quickStaging, overwrite = true)
                    copyStagingToCanonical(identity, "quicksave.state", quickStaging)
                    legacyQuick.renameTo(File(legacyDir, "${legacyQuick.name}.migrated.bak"))
                }

                legacySav.renameTo(File(legacyDir, "${legacySav.name}.migrated.bak"))
                break
            }
        }
    }

    // ---------------------------------------------------------
    // Slot Save States
    // ---------------------------------------------------------

    fun saveSlot(identity: RomIdentity, slotIndex: Int): Boolean = synchronized(saveLock) {
        val fileName = "slot_${slotIndex}.state"
        val stagingFile = getStagingFile(identity, fileName)
        val ok = LibretroHost.nativeSaveState(stagingFile.absolutePath)
        if (ok && stagingFile.exists()) {
            copyStagingToCanonical(identity, fileName, stagingFile)
        } else {
            false
        }
    }

    fun loadSlot(identity: RomIdentity, slotIndex: Int): Boolean = synchronized(saveLock) {
        val fileName = "slot_${slotIndex}.state"
        val stagingFile = getStagingFile(identity, fileName)
        val copied = copyCanonicalToStaging(identity, fileName, stagingFile)
        if (!copied || !stagingFile.exists()) return false
        LibretroHost.nativeLoadState(stagingFile.absolutePath)
    }

    fun quickSave(identity: RomIdentity): Boolean = synchronized(saveLock) {
        val fileName = "quicksave.state"
        val stagingFile = getStagingFile(identity, fileName)
        val ok = LibretroHost.nativeSaveState(stagingFile.absolutePath)
        if (ok && stagingFile.exists()) {
            copyStagingToCanonical(identity, fileName, stagingFile)
        } else {
            false
        }
    }

    fun quickLoad(identity: RomIdentity): Boolean = synchronized(saveLock) {
        val fileName = "quicksave.state"
        val stagingFile = getStagingFile(identity, fileName)
        val copied = copyCanonicalToStaging(identity, fileName, stagingFile)
        if (!copied || !stagingFile.exists()) return false
        LibretroHost.nativeLoadState(stagingFile.absolutePath)
    }

    fun getSlotInfo(identity: RomIdentity, slotIndex: Int): SaveSlotInfo {
        val fileName = "slot_${slotIndex}.state"
        return getFileInfo(identity, fileName, slotIndex)
    }

    fun getAllSlotsInfo(identity: RomIdentity, maxSlots: Int = 5): List<SaveSlotInfo> {
        return (1..maxSlots).map { getSlotInfo(identity, it) }
    }

    // ---------------------------------------------------------
    // Battery Save (.sav) Management
    // ---------------------------------------------------------

    fun loadBatterySave(identity: RomIdentity): Boolean = synchronized(saveLock) {
        ensureLegacyMigrated(identity)
        val stagingFile = getStagingFile(identity, "battery.sav")
        val copied = copyCanonicalToStaging(identity, "battery.sav", stagingFile)
        if (!copied || !stagingFile.exists() || stagingFile.length() == 0L) {
            return false
        }
        LibretroHost.nativeLoadSaveRam(stagingFile.absolutePath)
    }

    fun flushBatterySave(identity: RomIdentity): Boolean = synchronized(saveLock) {
        val stagingFile = getStagingFile(identity, "battery.sav")
        val flushed = LibretroHost.nativeFlushSaveRam(stagingFile.absolutePath)
        if (flushed && stagingFile.exists() && stagingFile.length() > 0L) {
            copyStagingToCanonical(identity, "battery.sav", stagingFile)
        } else {
            false
        }
    }

    fun getBatterySaveInfo(identity: RomIdentity): SaveSlotInfo {
        ensureLegacyMigrated(identity)
        return getFileInfo(identity, "battery.sav", slotIndex = 0)
    }

    private fun getFileInfo(identity: RomIdentity, fileName: String, slotIndex: Int): SaveSlotInfo {
        val safRoot = getSafFolder()
        if (safRoot != null) {
            try {
                val romDir = getOrCreateSafRomDir(safRoot, identity.storageKey)
                val doc = romDir?.findFile(fileName)
                if (doc != null && doc.exists() && doc.length() > 0L) {
                    val ts = doc.lastModified()
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
                    return SaveSlotInfo(
                        slotIndex = slotIndex,
                        exists = true,
                        timestampMs = ts,
                        formattedDate = dateStr,
                        sizeBytes = doc.length()
                    )
                }
            } catch (e: Exception) {
                Log.w("SaveStateManager", "Error querying SAF file info: ${e.message}")
            }
        }

        val fallbackFile = File(getFallbackRomDir(identity.storageKey), fileName)
        return if (fallbackFile.exists() && fallbackFile.length() > 0L) {
            val ts = fallbackFile.lastModified()
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
            SaveSlotInfo(
                slotIndex = slotIndex,
                exists = true,
                timestampMs = ts,
                formattedDate = dateStr,
                sizeBytes = fallbackFile.length()
            )
        } else {
            SaveSlotInfo(
                slotIndex = slotIndex,
                exists = false,
                timestampMs = 0L,
                formattedDate = if (slotIndex == 0) "No .sav file" else "Empty",
                sizeBytes = 0L
            )
        }
    }

    fun importBatterySave(identity: RomIdentity, inputStream: InputStream): Boolean = synchronized(saveLock) {
        try {
            val rawBytes = inputStream.use { it.readBytes() }
            if (rawBytes.isEmpty()) return false

            // Standard GBA Flash 1M is 131,072 bytes (128 KB)
            // Standalone mGBA on PC adds a 16-byte RTC footer (131,088 bytes)
            val cleanBytes = if (rawBytes.size == 131088) {
                rawBytes.copyOfRange(0, 131072)
            } else {
                rawBytes
            }

            val stagingFile = getStagingFile(identity, "battery.sav")
            stagingFile.writeBytes(cleanBytes)
            copyStagingToCanonical(identity, "battery.sav", stagingFile)

            val loaded = LibretroHost.nativeLoadSaveRam(stagingFile.absolutePath)
            if (loaded) {
                LibretroHost.nativeResetCore()
            }
            return loaded
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error importing battery save: ${e.message}", e)
            false
        }
    }

    fun exportBatterySave(identity: RomIdentity, outputStream: OutputStream): Boolean = synchronized(saveLock) {
        try {
            flushBatterySave(identity)
            val stagingFile = getStagingFile(identity, "battery.sav")
            if (!stagingFile.exists() || stagingFile.length() == 0L) {
                copyCanonicalToStaging(identity, "battery.sav", stagingFile)
            }
            if (!stagingFile.exists() || stagingFile.length() == 0L) return false

            outputStream.use { out ->
                stagingFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error exporting battery save: ${e.message}", e)
            false
        }
    }

    fun importBatterySave(identity: RomIdentity, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                importBatterySave(identity, stream)
            } ?: false
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error opening import URI: ${e.message}", e)
            false
        }
    }

    fun exportBatterySave(identity: RomIdentity, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                exportBatterySave(identity, stream)
            } ?: false
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error opening export URI: ${e.message}", e)
            false
        }
    }

    fun migrateFallbackToSaf(): Int = synchronized(saveLock) {
        val safRoot = getSafFolder() ?: return 0
        if (!fallbackBaseDir.exists() || !fallbackBaseDir.isDirectory) return 0

        var count = 0
        val romDirs = fallbackBaseDir.listFiles { f -> f.isDirectory } ?: return 0
        for (dir in romDirs) {
            val storageKey = dir.name
            val safRomDir = getOrCreateSafRomDir(safRoot, storageKey) ?: continue
            val files = dir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") && !f.name.endsWith(".bak") } ?: continue
            for (file in files) {
                val existing = safRomDir.findFile(file.name)
                if (existing == null) {
                    val newDoc = safRomDir.createFile("application/octet-stream", file.name)
                    if (newDoc != null) {
                        try {
                            context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
                                file.inputStream().use { inp -> inp.copyTo(out) }
                            }
                            count++
                        } catch (e: Exception) {
                            Log.w("SaveStateManager", "Failed to migrate ${file.name} to SAF: ${e.message}")
                        }
                    }
                }
            }
        }
        return count
    }

    // ---------------------------------------------------------
    // Backward Compatibility Overloads (gameKey: String)
    // ---------------------------------------------------------

    private fun identityFromKey(gameKey: String): RomIdentity {
        return RomIdentity.create("", gameKey)
    }

    fun getSaveFilePath(gameKey: String, slotIndex: Int): File {
        val identity = identityFromKey(gameKey)
        return getStagingFile(identity, "slot_${slotIndex}.state")
    }

    fun getQuickSaveFilePath(gameKey: String): File {
        val identity = identityFromKey(gameKey)
        return getStagingFile(identity, "quicksave.state")
    }

    fun getBatterySaveFilePath(gameKey: String): File {
        val identity = identityFromKey(gameKey)
        return getStagingFile(identity, "battery.sav")
    }

    fun saveSlot(gameKey: String, slotIndex: Int): Boolean =
        saveSlot(identityFromKey(gameKey), slotIndex)

    fun loadSlot(gameKey: String, slotIndex: Int): Boolean =
        loadSlot(identityFromKey(gameKey), slotIndex)

    fun quickSave(gameKey: String): Boolean =
        quickSave(identityFromKey(gameKey))

    fun quickLoad(gameKey: String): Boolean =
        quickLoad(identityFromKey(gameKey))

    fun getSlotInfo(gameKey: String, slotIndex: Int): SaveSlotInfo =
        getSlotInfo(identityFromKey(gameKey), slotIndex)

    fun getAllSlotsInfo(gameKey: String, maxSlots: Int = 5): List<SaveSlotInfo> =
        getAllSlotsInfo(identityFromKey(gameKey), maxSlots)

    fun loadBatterySave(gameKey: String): Boolean =
        loadBatterySave(identityFromKey(gameKey))

    fun flushBatterySave(gameKey: String): Boolean =
        flushBatterySave(identityFromKey(gameKey))

    fun getBatterySaveInfo(gameKey: String): SaveSlotInfo =
        getBatterySaveInfo(identityFromKey(gameKey))

    fun importBatterySave(gameKey: String, inputStream: InputStream): Boolean =
        importBatterySave(identityFromKey(gameKey), inputStream)

    fun exportBatterySave(gameKey: String, outputStream: OutputStream): Boolean =
        exportBatterySave(identityFromKey(gameKey), outputStream)

    fun importBatterySave(gameKey: String, uri: Uri): Boolean =
        importBatterySave(identityFromKey(gameKey), uri)

    fun exportBatterySave(gameKey: String, uri: Uri): Boolean =
        exportBatterySave(identityFromKey(gameKey), uri)
}
