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

data class LegacyMigrationResult(
    val filesFound: Int,
    val gameTitles: List<String>,
    val message: String
)

class SaveStateManager(private val context: Context) {

    private val settingsManager by lazy { SettingsManager(context) }
    private val saveLock = Any()

    var activeIdentity: RomIdentity? = null
    var activeProfileName: String? = null
    var activeProfileId: String? = null

    fun setActiveGame(identity: RomIdentity, profileName: String? = null, profileId: String? = null) {
        activeIdentity = identity
        activeProfileName = profileName
        activeProfileId = profileId
    }

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

    fun checkAndMigrateLegacySavesOnFirstOpen(): LegacyMigrationResult = synchronized(saveLock) {
        val legacyDir = File(context.filesDir, "saves")
        if (!legacyDir.exists() || !legacyDir.isDirectory) {
            return LegacyMigrationResult(0, emptyList(), "No legacy saves directory found")
        }

        val legacyFiles = legacyDir.listFiles { file ->
            file.isFile && file.length() > 0L &&
                !file.name.endsWith(".migrated.bak") &&
                !file.name.endsWith(".tmp") &&
                (file.name.endsWith(".sav") || file.name.endsWith(".state"))
        } ?: emptyArray()

        if (legacyFiles.isEmpty()) {
            return LegacyMigrationResult(0, emptyList(), "No unmigrated legacy saves found")
        }

        val gameTitles = mutableSetOf<String>()
        val safRoot = getSafFolder()

        for (file in legacyFiles) {
            val baseName = when {
                file.name.endsWith(".sav") -> file.name.removeSuffix(".sav")
                file.name.contains("_slot_") -> file.name.substringBefore("_slot_")
                file.name.endsWith("_quicksave.state") -> file.name.removeSuffix("_quicksave.state")
                file.name.endsWith(".state") -> file.name.removeSuffix(".state")
                else -> file.name.substringBeforeLast(".")
            }
            gameTitles.add(baseName)

            val destFileName = when {
                file.name.endsWith(".sav") -> "battery.sav"
                file.name.contains("_slot_") -> "slot_${file.name.substringAfter("_slot_")}"
                file.name.endsWith("_quicksave.state") -> "quicksave.state"
                else -> file.name
            }

            // Stage in fallback directory so it is preserved and accessible immediately
            val legacyStorageKey = "legacy_$baseName"
            val fallbackRomDir = getFallbackRomDir(legacyStorageKey)
            val fallbackTarget = File(fallbackRomDir, destFileName)
            if (!fallbackTarget.exists()) {
                try {
                    file.copyTo(fallbackTarget, overwrite = true)
                } catch (e: Exception) {
                    Log.w("SaveStateManager", "Failed to stage fallback for legacy file ${file.name}: ${e.message}")
                }
            }

            // If SAF root is active, copy to SAF legacy directory as well
            if (safRoot != null) {
                try {
                    val safRomDir = getOrCreateSafRomDir(safRoot, legacyStorageKey)
                    if (safRomDir != null && safRomDir.findFile(destFileName) == null) {
                        val doc = safRomDir.createFile("application/octet-stream", destFileName)
                        if (doc != null) {
                            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                                FileInputStream(file).use { it.copyTo(out) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SaveStateManager", "Failed to copy legacy file ${file.name} to SAF: ${e.message}")
                }
            }
        }

        Log.i("SaveStateManager", "First-open check found ${legacyFiles.size} legacy save file(s) across ${gameTitles.size} game(s): $gameTitles")
        return LegacyMigrationResult(
            filesFound = legacyFiles.size,
            gameTitles = gameTitles.toList(),
            message = "Cataloged ${legacyFiles.size} legacy files for ${gameTitles.size} game(s)"
        )
    }

    fun ensureLegacyMigrated(
        identity: RomIdentity,
        profileName: String? = null,
        profileId: String? = null
    ) {
        val profName = profileName ?: activeProfileName
        val profId = profileId ?: activeProfileId
        val legacyDir = File(context.filesDir, "saves")
        val hasLegacyDir = legacyDir.exists() && legacyDir.isDirectory

        val availableFiles = mutableListOf<String>()
        if (hasLegacyDir) {
            legacyDir.list()?.let { availableFiles.addAll(it) }
        }
        fallbackBaseDir.list()?.let { availableFiles.addAll(it) }

        val matchedBase = findMatchingLegacyBase(identity, profName, profId, availableFiles) ?: return
        val fallbackLegacyDir = File(fallbackBaseDir, "legacy_$matchedBase")

        // 1. Battery save (.sav)
        if (!canonicalFileExists(identity, "battery.sav")) {
            val legacySav = if (hasLegacyDir) File(legacyDir, "$matchedBase.sav") else null
            val fallbackSav = File(fallbackLegacyDir, "battery.sav")
            val sourceFile = when {
                legacySav != null && legacySav.exists() && legacySav.length() > 0L -> legacySav
                fallbackSav.exists() && fallbackSav.length() > 0L -> fallbackSav
                else -> null
            }
            if (sourceFile != null) {
                Log.i("SaveStateManager", "Migrating legacy battery save (${sourceFile.name}) to ${identity.storageKey}/battery.sav")
                val staging = getStagingFile(identity, "battery.sav")
                sourceFile.copyTo(staging, overwrite = true)
                copyStagingToCanonical(identity, "battery.sav", staging)
                if (sourceFile == legacySav && legacySav.exists()) {
                    legacySav.renameTo(File(legacyDir, "${legacySav.name}.migrated.bak"))
                }
            }
        }

        // 2. Slot states (1..5)
        for (slot in 1..5) {
            val stateName = "slot_$slot.state"
            if (!canonicalFileExists(identity, stateName)) {
                val legacySlot = if (hasLegacyDir) File(legacyDir, "${matchedBase}_slot_$slot.state") else null
                val fallbackSlot = File(fallbackLegacyDir, stateName)
                val sourceFile = when {
                    legacySlot != null && legacySlot.exists() && legacySlot.length() > 0L -> legacySlot
                    fallbackSlot.exists() && fallbackSlot.length() > 0L -> fallbackSlot
                    else -> null
                }
                if (sourceFile != null) {
                    val slotStaging = getStagingFile(identity, stateName)
                    sourceFile.copyTo(slotStaging, overwrite = true)
                    copyStagingToCanonical(identity, stateName, slotStaging)
                    if (sourceFile == legacySlot && legacySlot.exists()) {
                        legacySlot.renameTo(File(legacyDir, "${legacySlot.name}.migrated.bak"))
                    }
                }
            }
        }

        // 3. Quick save
        val quickName = "quicksave.state"
        if (!canonicalFileExists(identity, quickName)) {
            val legacyQuick = if (hasLegacyDir) File(legacyDir, "${matchedBase}_quicksave.state") else null
            val fallbackQuick = File(fallbackLegacyDir, quickName)
            val sourceFile = when {
                legacyQuick != null && legacyQuick.exists() && legacyQuick.length() > 0L -> legacyQuick
                fallbackQuick.exists() && fallbackQuick.length() > 0L -> fallbackQuick
                else -> null
            }
            if (sourceFile != null) {
                val quickStaging = getStagingFile(identity, quickName)
                sourceFile.copyTo(quickStaging, overwrite = true)
                copyStagingToCanonical(identity, quickName, quickStaging)
                if (sourceFile == legacyQuick && legacyQuick.exists()) {
                    legacyQuick.renameTo(File(legacyDir, "${legacyQuick.name}.migrated.bak"))
                }
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

    fun loadSlot(
        identity: RomIdentity,
        slotIndex: Int,
        profileName: String? = null,
        profileId: String? = null
    ): Boolean = synchronized(saveLock) {
        ensureLegacyMigrated(identity, profileName, profileId)
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

    fun quickLoad(
        identity: RomIdentity,
        profileName: String? = null,
        profileId: String? = null
    ): Boolean = synchronized(saveLock) {
        ensureLegacyMigrated(identity, profileName, profileId)
        val fileName = "quicksave.state"
        val stagingFile = getStagingFile(identity, fileName)
        val copied = copyCanonicalToStaging(identity, fileName, stagingFile)
        if (!copied || !stagingFile.exists()) return false
        LibretroHost.nativeLoadState(stagingFile.absolutePath)
    }

    fun getSlotInfo(
        identity: RomIdentity,
        slotIndex: Int,
        profileName: String? = null,
        profileId: String? = null
    ): SaveSlotInfo {
        ensureLegacyMigrated(identity, profileName, profileId)
        val fileName = "slot_${slotIndex}.state"
        return getFileInfo(identity, fileName, slotIndex)
    }

    fun getAllSlotsInfo(
        identity: RomIdentity,
        maxSlots: Int = 5,
        profileName: String? = null,
        profileId: String? = null
    ): List<SaveSlotInfo> {
        ensureLegacyMigrated(identity, profileName, profileId)
        return (1..maxSlots).map { getSlotInfo(identity, it, profileName, profileId) }
    }

    // ---------------------------------------------------------
    // Battery Save (.sav) Management
    // ---------------------------------------------------------

    fun loadBatterySave(
        identity: RomIdentity,
        profileName: String? = null,
        profileId: String? = null
    ): Boolean = synchronized(saveLock) {
        if (profileName != null) activeProfileName = profileName
        if (profileId != null) activeProfileId = profileId
        ensureLegacyMigrated(identity, profileName, profileId)
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

    fun getBatterySaveInfo(
        identity: RomIdentity,
        profileName: String? = null,
        profileId: String? = null
    ): SaveSlotInfo {
        ensureLegacyMigrated(identity, profileName, profileId)
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

    companion object {
        fun findMatchingLegacyBase(
            identity: RomIdentity,
            profileName: String? = null,
            profileId: String? = null,
            availableFiles: List<String>
        ): String? {
            fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

            val candidateBases = mutableListOf<String>()
            if (!profileName.isNullOrBlank()) {
                candidateBases.add(profileName.replace(Regex("[^a-zA-Z0-9_-]"), "_"))
                candidateBases.add(RomIdentity.sanitizeTitle(profileName))
            }
            if (!profileId.isNullOrBlank()) {
                candidateBases.add(profileId)
            }
            candidateBases.add(identity.displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_"))
            candidateBases.add(identity.sanitizedTitle)
            candidateBases.add("current_game")

            val distinctBases = candidateBases.distinct()

            // 1. Try exact matches against available files
            for (base in distinctBases) {
                val matches = availableFiles.any { f ->
                    f == "$base.sav" ||
                        f == "${base}_quicksave.state" ||
                        (1..5).any { f == "${base}_slot_$it.state" } ||
                        f == "legacy_$base"
                }
                if (matches) return base
            }

            // 2. Fuzzy match based on alphanumeric normalized names
            val profileNorm = profileName?.let { norm(it) }.orEmpty()
            val titleNorm = norm(identity.displayName)

            for (file in availableFiles) {
                if (file.endsWith(".migrated.bak") || file.endsWith(".tmp")) continue
                val fBase = when {
                    file.endsWith(".sav") -> file.removeSuffix(".sav")
                    file.contains("_slot_") -> file.substringBefore("_slot_")
                    file.endsWith("_quicksave.state") -> file.removeSuffix("_quicksave.state")
                    file.endsWith(".state") -> file.removeSuffix(".state")
                    file.startsWith("legacy_") -> file.removePrefix("legacy_")
                    else -> null
                } ?: continue

                val baseNorm = norm(fBase)
                if (baseNorm.length >= 3 && (
                    (profileNorm.isNotEmpty() && (profileNorm.contains(baseNorm) || baseNorm.contains(profileNorm))) ||
                        (titleNorm.isNotEmpty() && (titleNorm.contains(baseNorm) || baseNorm.contains(titleNorm)))
                )) {
                    return fBase
                }
            }

            return null
        }
    }
}
