package com.dualdex.emulator

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dualdex.emulator.storage.AtomicSaveFile
import com.dualdex.emulator.storage.LegacyCandidate
import com.dualdex.emulator.storage.LegacySaveCatalog
import com.dualdex.emulator.storage.MirrorStatus
import com.dualdex.emulator.storage.RomSaveMetadata
import com.dualdex.emulator.storage.SafMirrorStore
import com.dualdex.emulator.storage.SaveWriteResult
import com.dualdex.settings.SettingsManager
import java.io.File
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

open class SaveStateManager(
    private val context: Context? = null,
    private val customBaseDir: File? = null,
    private val customSafStore: SafMirrorStore? = null,
    private val customLegacyCatalog: LegacySaveCatalog? = null,
    var coreBridge: LibretroCoreBridge? = null
) {

    private val settingsManager by lazy { context?.let { SettingsManager(it) } }
    private val safMirrorStore by lazy { customSafStore ?: SafMirrorStore(context) }
    private val legacyCatalog by lazy { customLegacyCatalog ?: LegacySaveCatalog(context) }

    var activeIdentity: RomIdentity? = null
    var activeProfileName: String? = null
    var activeProfileId: String? = null

    private fun nativeLoadSaveRam(path: String): Boolean =
        coreBridge?.loadSaveRam(path) ?: LibretroHost.nativeLoadSaveRam(path)

    private fun nativeFlushSaveRam(path: String): Boolean =
        coreBridge?.flushSaveRam(path) ?: LibretroHost.nativeFlushSaveRam(path)

    private fun nativeGetSaveRamSize(): Long =
        coreBridge?.getSaveRamSize() ?: LibretroHost.nativeGetSaveRamSize()

    private fun nativeGetSaveStateSize(): Long =
        coreBridge?.getSaveStateSize() ?: LibretroHost.nativeGetSaveStateSize()

    private fun nativeSaveState(path: String): Boolean =
        coreBridge?.saveState(path) ?: LibretroHost.nativeSaveState(path)

    private fun nativeLoadState(path: String): Boolean =
        coreBridge?.loadState(path) ?: LibretroHost.nativeLoadState(path)

    private fun nativeResetCore() {
        if (coreBridge != null) coreBridge?.resetCore() else LibretroHost.nativeResetCore()
    }

    fun setActiveGame(identity: RomIdentity, profileName: String? = null, profileId: String? = null) {
        synchronized(globalSaveLock) {
            activeIdentity = identity
            activeProfileName = profileName
            activeProfileId = profileId
            if (identity.isValid) {
                migrateOldBranchDirIfPresent(identity)
                recordMetadata(identity, profileId)
            }
        }
    }

    private val canonicalBaseDir: File
        get() = File(customBaseDir ?: context?.filesDir ?: File("build/test_saves"), "saves_v2").apply {
            if (!exists()) mkdirs()
        }

    private val stagingDir: File
        get() = File(customBaseDir ?: context?.filesDir ?: File("build/test_saves"), "save_staging").apply {
            if (!exists()) mkdirs()
        }

    private val fallbackBaseDir: File
        get() = File(customBaseDir ?: context?.filesDir ?: File("build/test_saves"), "saves_fallback").apply {
            if (!exists()) mkdirs()
        }

    fun getCanonicalRomDir(identity: RomIdentity): File {
        return File(canonicalBaseDir, identity.storageKey).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getCanonicalFile(identity: RomIdentity, fileName: String): File {
        return File(getCanonicalRomDir(identity), fileName)
    }

    fun getStagingFile(identity: RomIdentity, fileName: String): File {
        return File(stagingDir, "${identity.storageKey}__$fileName")
    }

    fun isUsingSaf(): Boolean = safMirrorStore.isSafConfigured() && safMirrorStore.getSafFolder() != null

    fun getSafMirrorStatus(identity: RomIdentity, fileName: String = "battery.sav"): MirrorStatus {
        val canonicalFile = getCanonicalFile(identity, fileName)
        return safMirrorStore.checkMirrorStatus(identity, fileName, canonicalFile)
    }

    fun getSaveDirectoryDescription(): String {
        val safRoot = safMirrorStore.getSafFolder()
        return if (safRoot != null) {
            safRoot.name?.let { "SAF ($it)" } ?: "SAF Folder"
        } else {
            "Internal App Storage (Private)"
        }
    }

    // ---------------------------------------------------------
    // Metadata Management
    // ---------------------------------------------------------

    private fun recordMetadata(identity: RomIdentity, profileId: String?) {
        if (!identity.isValid) return
        val metaFile = getCanonicalFile(identity, "metadata.json")
        val existing = if (metaFile.exists()) {
            RomSaveMetadata.fromJson(metaFile.readText())
        } else null

        val updated = RomSaveMetadata(
            sha256 = identity.sha256,
            displayName = identity.displayName,
            lastKnownProfileId = profileId ?: existing?.lastKnownProfileId,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            saveGeneration = (existing?.saveGeneration ?: 0L) + 1L,
            mirrorStatus = safMirrorStore.checkMirrorStatus(identity, "battery.sav", getCanonicalFile(identity, "battery.sav"))
        )
        try {
            AtomicSaveFile.writeBytes(metaFile, updated.toJson().toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write metadata: ${e.message}")
        }
    }

    // ---------------------------------------------------------
    // Old Branch Title+12Hash Migration
    // ---------------------------------------------------------

    fun migrateOldBranchDirIfPresent(identity: RomIdentity): Boolean {
        if (!identity.isValid) return false
        val canonicalDir = getCanonicalRomDir(identity)
        val canonicalHasSaves = canonicalDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".sav") || f.name.endsWith(".state"))
        }?.isNotEmpty() == true

        if (canonicalHasSaves) return false

        // Check fallbackBaseDir for <sanitizedTitle>__<shortHash>
        val oldKey = identity.legacyStorageKey
        val oldDir = File(fallbackBaseDir, oldKey)
        if (!oldDir.exists() || !oldDir.isDirectory) return false

        val oldFiles = oldDir.listFiles { f ->
            f.isFile && !f.name.endsWith(".tmp") && !f.name.endsWith(".bak")
        } ?: return false

        if (oldFiles.isEmpty()) return false

        Log.i(TAG, "Migrating old branch save directory from $oldKey to canonical ${identity.storageKey}")
        var migratedAny = false
        for (file in oldFiles) {
            val target = File(canonicalDir, file.name)
            if (AtomicSaveFile.copyFromStaging(file, target)) {
                migratedAny = true
            }
        }

        if (migratedAny) {
            val bakDir = File(fallbackBaseDir, "${oldKey}.migrated.bak")
            oldDir.renameTo(bakDir)
        }
        return migratedAny
    }

    // ---------------------------------------------------------
    // Save States (Slots 1..5)
    // ---------------------------------------------------------

    fun saveSlot(identity: RomIdentity, slotIndex: Int): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) {
            Log.e(TAG, "Refusing saveSlot on invalid RomIdentity")
            return false
        }
        val fileName = "slot_${slotIndex}.state"
        val stagingFile = getStagingFile(identity, fileName)
        val ok = nativeSaveState(stagingFile.absolutePath)
        if (!ok || !stagingFile.exists() || stagingFile.length() == 0L) {
            return false
        }

        val canonicalFile = getCanonicalFile(identity, fileName)
        val committed = AtomicSaveFile.copyFromStaging(stagingFile, canonicalFile)
        if (committed) {
            safMirrorStore.mirrorFile(identity, fileName, canonicalFile)
            recordMetadata(identity, activeProfileId)
        }
        return committed
    }

    fun loadSlot(
        identity: RomIdentity,
        slotIndex: Int,
        profileName: String? = null,
        profileId: String? = null
    ): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) {
            Log.e(TAG, "Refusing loadSlot on invalid RomIdentity")
            return false
        }
        val fileName = "slot_${slotIndex}.state"
        val canonicalFile = getCanonicalFile(identity, fileName)
        if (!canonicalFile.exists() || canonicalFile.length() == 0L) {
            return false
        }

        // Validate state size before invoking native unserialize
        val expectedSize = nativeGetSaveStateSize()
        if (expectedSize > 0 && canonicalFile.length() != expectedSize) {
            Log.e(TAG, "Slot $slotIndex state size mismatch: expected $expectedSize, got ${canonicalFile.length()}")
            return false
        }

        val stagingFile = getStagingFile(identity, fileName)
        canonicalFile.copyTo(stagingFile, overwrite = true)
        if (!stagingFile.exists() || stagingFile.length() == 0L) return false

        return nativeLoadState(stagingFile.absolutePath)
    }

    // ---------------------------------------------------------
    // Manual Quick Save vs Auto Resume State
    // ---------------------------------------------------------

    fun quickSave(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) {
            Log.e(TAG, "Refusing quickSave on invalid RomIdentity")
            return false
        }
        val fileName = "quicksave.state"
        val stagingFile = getStagingFile(identity, fileName)
        val ok = nativeSaveState(stagingFile.absolutePath)
        if (!ok || !stagingFile.exists() || stagingFile.length() == 0L) {
            return false
        }

        val canonicalFile = getCanonicalFile(identity, fileName)
        val committed = AtomicSaveFile.copyFromStaging(stagingFile, canonicalFile)
        if (committed) {
            safMirrorStore.mirrorFile(identity, fileName, canonicalFile)
            recordMetadata(identity, activeProfileId)
        }
        return committed
    }

    fun quickLoad(
        identity: RomIdentity,
        profileName: String? = null,
        profileId: String? = null
    ): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) return false
        val fileName = "quicksave.state"
        val canonicalFile = getCanonicalFile(identity, fileName)
        if (!canonicalFile.exists() || canonicalFile.length() == 0L) return false

        val expectedSize = nativeGetSaveStateSize()
        if (expectedSize > 0 && canonicalFile.length() != expectedSize) {
            Log.e(TAG, "Quick save state size mismatch: expected $expectedSize, got ${canonicalFile.length()}")
            return false
        }

        val stagingFile = getStagingFile(identity, fileName)
        canonicalFile.copyTo(stagingFile, overwrite = true)
        if (!stagingFile.exists()) return false

        return nativeLoadState(stagingFile.absolutePath)
    }

    fun saveAutoResume(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) return false
        val fileName = "auto_resume.state"
        val stagingFile = getStagingFile(identity, fileName)
        val ok = nativeSaveState(stagingFile.absolutePath)
        if (!ok || !stagingFile.exists() || stagingFile.length() == 0L) {
            return false
        }

        val canonicalFile = getCanonicalFile(identity, fileName)
        return AtomicSaveFile.copyFromStaging(stagingFile, canonicalFile)
    }

    fun loadAutoResume(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) return false
        val fileName = "auto_resume.state"
        val canonicalFile = getCanonicalFile(identity, fileName)
        if (!canonicalFile.exists() || canonicalFile.length() == 0L) return false

        val stagingFile = getStagingFile(identity, fileName)
        canonicalFile.copyTo(stagingFile, overwrite = true)
        if (!stagingFile.exists()) return false

        return nativeLoadState(stagingFile.absolutePath)
    }

    // ---------------------------------------------------------
    // Slot & File Info
    // ---------------------------------------------------------

    fun getSlotInfo(
        identity: RomIdentity,
        slotIndex: Int,
        profileName: String? = null,
        profileId: String? = null
    ): SaveSlotInfo {
        if (!identity.isValid) {
            return SaveSlotInfo(slotIndex, false, 0L, "No active ROM", 0L)
        }
        val fileName = "slot_${slotIndex}.state"
        return getFileInfo(identity, fileName, slotIndex)
    }

    fun getAllSlotsInfo(
        identity: RomIdentity,
        maxSlots: Int = 5,
        profileName: String? = null,
        profileId: String? = null
    ): List<SaveSlotInfo> {
        if (!identity.isValid) {
            return (1..maxSlots).map { SaveSlotInfo(it, false, 0L, "No active ROM", 0L) }
        }
        return (1..maxSlots).map { getSlotInfo(identity, it, profileName, profileId) }
    }

    fun getBatterySaveInfo(
        identity: RomIdentity,
        profileName: String? = null,
        profileId: String? = null
    ): SaveSlotInfo {
        if (!identity.isValid) {
            return SaveSlotInfo(0, false, 0L, "No active ROM", 0L)
        }
        return getFileInfo(identity, "battery.sav", slotIndex = 0)
    }

    private fun getFileInfo(identity: RomIdentity, fileName: String, slotIndex: Int): SaveSlotInfo {
        val canonicalFile = getCanonicalFile(identity, fileName)
        return if (canonicalFile.exists() && canonicalFile.length() > 0L) {
            val ts = canonicalFile.lastModified()
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
            SaveSlotInfo(
                slotIndex = slotIndex,
                exists = true,
                timestampMs = ts,
                formattedDate = dateStr,
                sizeBytes = canonicalFile.length()
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

    // ---------------------------------------------------------
    // Cartridge Battery Save (.sav) Management
    // ---------------------------------------------------------

    fun loadBatterySave(
        identity: RomIdentity,
        profileName: String? = null,
        profileId: String? = null
    ): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) {
            Log.e(TAG, "Refusing loadBatterySave on invalid RomIdentity")
            return false
        }
        if (profileName != null) activeProfileName = profileName
        if (profileId != null) activeProfileId = profileId

        val canonicalFile = getCanonicalFile(identity, "battery.sav")
        if (!canonicalFile.exists() || canonicalFile.length() == 0L) {
            return false
        }

        val stagingFile = getStagingFile(identity, "battery.sav")
        canonicalFile.copyTo(stagingFile, overwrite = true)
        if (!stagingFile.exists() || stagingFile.length() == 0L) {
            return false
        }

        return nativeLoadSaveRam(stagingFile.absolutePath)
    }

    fun flushBatterySave(identity: RomIdentity): SaveWriteResult = synchronized(globalSaveLock) {
        if (!identity.isValid) {
            Log.e(TAG, "Refusing flushBatterySave on invalid RomIdentity")
            return SaveWriteResult.Failure("Invalid ROM identity")
        }

        val stagingFile = getStagingFile(identity, "battery.sav")
        val flushed = nativeFlushSaveRam(stagingFile.absolutePath)
        if (!flushed || !stagingFile.exists() || stagingFile.length() == 0L) {
            Log.e(TAG, "nativeFlushSaveRam failed or produced empty file")
            return SaveWriteResult.Failure("Native SRAM flush failed")
        }

        val canonicalFile = getCanonicalFile(identity, "battery.sav")
        val committed = AtomicSaveFile.copyFromStaging(stagingFile, canonicalFile)
        if (!committed) {
            Log.e(TAG, "Atomic commit to canonical battery.sav failed")
            return SaveWriteResult.Failure("Canonical commit failed")
        }

        val mirrorStatus = safMirrorStore.mirrorFile(identity, "battery.sav", canonicalFile)
        recordMetadata(identity, activeProfileId)

        return SaveWriteResult.Success(
            canonicalWritten = true,
            mirrorStatus = mirrorStatus
        )
    }

    fun importBatterySave(identity: RomIdentity, inputStream: InputStream): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) {
            Log.e(TAG, "Refusing importBatterySave on invalid RomIdentity")
            return false
        }

        try {
            val rawBytes = inputStream.use { it.readBytes() }
            if (rawBytes.isEmpty()) {
                Log.e(TAG, "Import rejected: input bytes are empty")
                return false
            }

            // Determine active core's expected SRAM size
            val coreRamSize = nativeGetSaveRamSize()
            val expectedRamSize = if (coreRamSize > 0) coreRamSize.toInt() else 131072 // Default to 128KB GBA Flash

            // Normalize known emulator footer formats ONLY when appropriate
            // Standalone mGBA on PC adds a 16-byte RTC footer (expectedRamSize + 16)
            val cleanBytes = if (rawBytes.size == expectedRamSize + 16) {
                Log.i(TAG, "Stripping 16-byte mGBA RTC footer from import (${rawBytes.size} -> $expectedRamSize)")
                rawBytes.copyOfRange(0, expectedRamSize)
            } else {
                rawBytes
            }

            // Require normalized size to match active game's expected SRAM size
            if (cleanBytes.size != expectedRamSize) {
                Log.e(TAG, "Import rejected: file size ${cleanBytes.size} != expected SRAM size $expectedRamSize")
                return false
            }

            // Backup current SRAM from active core to staging before mutating
            val sramBackup = File(stagingDir, "${identity.storageKey}__import_backup.sav")
            val hadExistingSram = nativeFlushSaveRam(sramBackup.absolutePath)

            // Test load candidate from temp file
            val tempCandidate = File(stagingDir, "${identity.storageKey}__import_candidate.tmp")
            tempCandidate.writeBytes(cleanBytes)

            val loaded = nativeLoadSaveRam(tempCandidate.absolutePath)
            if (!loaded) {
                Log.e(TAG, "Import rejected: nativeLoadSaveRam failed to load candidate")
                if (hadExistingSram && sramBackup.exists()) {
                    nativeLoadSaveRam(sramBackup.absolutePath)
                }
                if (tempCandidate.exists()) tempCandidate.delete()
                return false
            }

            // Commit candidate atomically to canonical store
            val canonicalFile = getCanonicalFile(identity, "battery.sav")
            val committed = AtomicSaveFile.writeBytes(canonicalFile, cleanBytes)
            if (!committed) {
                Log.e(TAG, "Import failed: atomic canonical commit failed, restoring prior SRAM")
                if (hadExistingSram && sramBackup.exists()) {
                    nativeLoadSaveRam(sramBackup.absolutePath)
                }
                if (tempCandidate.exists()) tempCandidate.delete()
                return false
            }

            // Mirror to SAF
            safMirrorStore.mirrorFile(identity, "battery.sav", canonicalFile)
            recordMetadata(identity, activeProfileId)

            // Reset core only after entire transaction succeeds
            nativeResetCore()
            if (tempCandidate.exists()) tempCandidate.delete()
            if (sramBackup.exists()) sramBackup.delete()

            Log.i(TAG, "Import battery save succeeded for ${identity.storageKey} ($expectedRamSize bytes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing battery save: ${e.message}", e)
            return false
        }
    }

    fun exportBatterySave(identity: RomIdentity, outputStream: OutputStream): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) {
            Log.e(TAG, "Refusing exportBatterySave on invalid RomIdentity")
            return false
        }

        try {
            // 1. Capture current SRAM into staging
            val stagingFile = getStagingFile(identity, "battery.sav")
            val flushed = nativeFlushSaveRam(stagingFile.absolutePath)
            if (!flushed || !stagingFile.exists() || stagingFile.length() == 0L) {
                Log.e(TAG, "Export aborted: failed to capture current SRAM from emulator")
                return false
            }

            // 2. Commit newly captured SRAM to canonical internal store
            val canonicalFile = getCanonicalFile(identity, "battery.sav")
            val committed = AtomicSaveFile.copyFromStaging(stagingFile, canonicalFile)
            if (!committed || !canonicalFile.exists() || canonicalFile.length() == 0L) {
                Log.e(TAG, "Export aborted: failed to commit fresh SRAM to canonical store")
                return false
            }

            // 3. Export newly committed data
            outputStream.use { out ->
                canonicalFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            Log.i(TAG, "Export battery save succeeded for ${identity.storageKey}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting battery save: ${e.message}", e)
            return false
        }
    }

    fun importBatterySave(identity: RomIdentity, uri: Uri): Boolean {
        return try {
            context?.contentResolver?.openInputStream(uri)?.use { stream ->
                importBatterySave(identity, stream)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error opening import URI: ${e.message}", e)
            false
        }
    }

    fun exportBatterySave(identity: RomIdentity, uri: Uri): Boolean {
        return try {
            context?.contentResolver?.openOutputStream(uri)?.use { stream ->
                exportBatterySave(identity, stream)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error opening export URI: ${e.message}", e)
            false
        }
    }

    // ---------------------------------------------------------
    // SAF Mirror Sync
    // ---------------------------------------------------------

    fun syncCanonicalToSaf(identity: RomIdentity): Int = synchronized(globalSaveLock) {
        if (!identity.isValid) return 0
        val canonicalDir = getCanonicalRomDir(identity)
        return safMirrorStore.syncCanonicalToSaf(identity, canonicalDir)
    }

    // ---------------------------------------------------------
    // Legacy Save Management
    // ---------------------------------------------------------

    fun discoverLegacyCandidates(): List<LegacyCandidate> {
        return legacyCatalog.discoverCandidates()
    }

    fun assignLegacyCandidate(candidate: LegacyCandidate, targetIdentity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        val canonicalDir = getCanonicalRomDir(targetIdentity)
        return legacyCatalog.assignCandidateToRom(candidate, targetIdentity, canonicalDir)
    }

    fun checkAndMigrateLegacySavesOnFirstOpen(): LegacyMigrationResult = synchronized(globalSaveLock) {
        val candidates = legacyCatalog.discoverCandidates()
        if (candidates.isEmpty()) {
            return LegacyMigrationResult(0, emptyList(), "No unmigrated legacy saves found")
        }

        val titles = candidates.map { it.suggestedTitle }.distinct()
        return LegacyMigrationResult(
            filesFound = candidates.size,
            gameTitles = titles,
            message = "Cataloged ${candidates.size} legacy files across ${titles.size} games"
        )
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
        flushBatterySave(identityFromKey(gameKey)) is SaveWriteResult.Success

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
        private const val TAG = "SaveStateManager"
        val globalSaveLock = Any()

        @Volatile
        private var instance: SaveStateManager? = null

        fun getInstance(context: Context): SaveStateManager {
            return instance ?: synchronized(globalSaveLock) {
                instance ?: SaveStateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
