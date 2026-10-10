package com.dualdex.emulator

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dualdex.emulator.storage.AtomicSaveFile
import com.dualdex.emulator.storage.LegacyCandidate
import com.dualdex.emulator.storage.LegacySaveCatalog
import com.dualdex.emulator.storage.MigrationResult
import com.dualdex.emulator.storage.MirrorStatus
import com.dualdex.emulator.storage.RomSaveMetadata
import com.dualdex.emulator.storage.SafMirrorStore
import com.dualdex.emulator.storage.SaveShareStore
import com.dualdex.emulator.storage.SaveStateFiles
import com.dualdex.emulator.storage.SaveWriteResult
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.launch
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
    coreBridge: LibretroCoreBridge? = null,
    customCoordinator: LibretroCoreCoordinator? = null
) {
    var coreCoordinator: LibretroCoreCoordinator = customCoordinator
        ?: (if (coreBridge != null) LibretroCoreCoordinator(coreBridge) else LibretroCoreCoordinator.defaultInstance)

    var coreBridge: LibretroCoreBridge? = coreBridge
        set(value) {
            field = value
            coreCoordinator.bridge = value
        }

    init {
        if (coreBridge != null) {
            coreCoordinator.bridge = coreBridge
        }
    }

    private val settingsManager by lazy { context?.let { SettingsManager(it) } }
    private val safMirrorStore by lazy { customSafStore ?: SafMirrorStore(context) }
    private val legacyCatalog by lazy { customLegacyCatalog ?: LegacySaveCatalog(context) }
    val saveShareStore by lazy { SaveShareStore(context) }

    var activeIdentity: RomIdentity? = null
    var activeProfileName: String? = null
    var activeProfileId: String? = null

    private fun nativeLoadSaveRam(path: String): Boolean = coreCoordinator.loadSaveRam(path)
    private fun nativeFlushSaveRam(path: String): Boolean = coreCoordinator.flushSaveRam(path)
    private fun nativeGetSaveRamSize(): Long = coreCoordinator.getSaveRamSize()
    private fun nativeGetSaveStateSize(): Long = coreCoordinator.getSaveStateSize()
    private fun nativeSaveState(path: String): Boolean = coreCoordinator.saveState(path)
    private fun nativeLoadState(path: String): Boolean = coreCoordinator.loadState(path)
    private fun nativeResetCore() { coreCoordinator.resetCore() }

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

    private fun recordMetadata(
        identity: RomIdentity,
        profileId: String?,
        explicitStatus: MirrorStatus? = null
    ) {
        if (!identity.isValid) return
        val metaFile = getCanonicalFile(identity, "metadata.json")
        val existing = if (metaFile.exists()) {
            RomSaveMetadata.fromJson(metaFile.readText())
        } else null

        val finalStatus = explicitStatus ?: if (safMirrorStore.isSafConfigured()) {
            safMirrorStore.checkMirrorStatus(identity, "battery.sav", getCanonicalFile(identity, "battery.sav"))
        } else {
            MirrorStatus.UNAVAILABLE
        }

        val updated = RomSaveMetadata(
            sha256 = identity.sha256,
            displayName = identity.displayName,
            lastKnownProfileId = profileId ?: existing?.lastKnownProfileId,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            saveGeneration = (existing?.saveGeneration ?: 0L) + 1L,
            mirrorStatus = finalStatus
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
    // Save States (Slots 1..SLOT_COUNT)
    // ---------------------------------------------------------

    /**
     * Must be called with [globalSaveLock] held (a ROM switch holds it for its whole transaction).
     * A deferred caller (e.g. a controller shortcut) may carry an identity captured before a ROM switch;
     * the core would then hold a different ROM and the state would land in the wrong ROM's files.
     */
    private fun isLoadedRom(identity: RomIdentity): Boolean =
        activeIdentity?.sha256.equals(identity.sha256, ignoreCase = true)

    private fun refuse(op: String, identity: RomIdentity): Boolean {
        if (!identity.isValid) {
            Log.e(TAG, "Refusing $op on invalid RomIdentity")
            return true
        }
        if (!isLoadedRom(identity)) {
            Log.w(TAG, "Refusing $op: ${identity.displayName} is not the loaded ROM")
            return true
        }
        return false
    }

    /** Serializes the core into [fileName] via staging; the previous file is kept as `.bak` by [AtomicSaveFile]. */
    private fun writeState(identity: RomIdentity, fileName: String): File? {
        val stagingFile = getStagingFile(identity, fileName)
        val ok = nativeSaveState(stagingFile.absolutePath)
        if (!ok || !stagingFile.exists() || stagingFile.length() == 0L) return null
        val canonicalFile = getCanonicalFile(identity, fileName)
        return canonicalFile.takeIf { AtomicSaveFile.copyFromStaging(stagingFile, it) }
    }

    /**
     * Validates and loads [fileName]. With [snapshotUndo] the current state is first written to
     * `undo_load.state`, so an accidental load can be reverted with [undoLoad].
     */
    private fun loadStateFile(identity: RomIdentity, fileName: String, snapshotUndo: Boolean): Boolean {
        val canonicalFile = getCanonicalFile(identity, fileName)
        val expectedSize = nativeGetSaveStateSize()
        AtomicSaveFile.recoverInterrupted(canonicalFile, if (expectedSize > 0) expectedSize else null)
        if (!canonicalFile.exists() || canonicalFile.length() == 0L) return false
        if (expectedSize > 0 && canonicalFile.length() != expectedSize) {
            Log.e(TAG, "$fileName size mismatch: expected $expectedSize, got ${canonicalFile.length()}")
            return false
        }
        val stagingFile = getStagingFile(identity, fileName)
        canonicalFile.copyTo(stagingFile, overwrite = true)
        if (!stagingFile.exists() || stagingFile.length() == 0L) return false
        if (snapshotUndo && writeState(identity, SaveStateFiles.UNDO_LOAD) == null) {
            Log.w(TAG, "Could not snapshot undo state before loading $fileName")
        }
        return nativeLoadState(stagingFile.absolutePath)
    }

    fun saveSlot(identity: RomIdentity, slotIndex: Int): Boolean = synchronized(globalSaveLock) {
        if (refuse("saveSlot", identity)) return false
        if (slotIndex !in 1..SaveStateFiles.SLOT_COUNT) return false
        val fileName = SaveStateFiles.slotState(slotIndex)
        val canonicalFile = writeState(identity, fileName) ?: return false
        writeThumbnail(identity, slotIndex)
        safMirrorStore.mirrorFile(identity, fileName, canonicalFile)
        recordMetadata(identity, activeProfileId)
        return true
    }

    fun loadSlot(
        identity: RomIdentity,
        slotIndex: Int,
        profileName: String? = null,
        profileId: String? = null
    ): Boolean = synchronized(globalSaveLock) {
        if (refuse("loadSlot", identity)) return false
        return loadStateFile(identity, SaveStateFiles.slotState(slotIndex), snapshotUndo = true)
    }

    /** Reverts the most recent slot/quick load by restoring the state captured just before it. */
    fun undoLoad(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (refuse("undoLoad", identity)) return false
        return loadStateFile(identity, SaveStateFiles.UNDO_LOAD, snapshotUndo = false)
    }

    fun hasUndoLoad(identity: RomIdentity): Boolean =
        identity.isValid && getCanonicalFile(identity, SaveStateFiles.UNDO_LOAD).let { it.isFile && it.length() > 0L }

    fun getSlotThumbnail(identity: RomIdentity, slotIndex: Int): File? =
        getCanonicalFile(identity, SaveStateFiles.slotThumbnail(slotIndex)).takeIf { it.isFile && it.length() > 0L }

    /** Best effort: a missing thumbnail never fails the save. Skipped off-device (no context / native host). */
    private fun writeThumbnail(identity: RomIdentity, slotIndex: Int) {
        if (context == null) return
        try {
            val buffer = java.nio.ByteBuffer.allocateDirect(512 * 512 * 4)
            val meta = IntArray(4) // width, height, pitch, pixelFormat
            if (!coreCoordinator.getVideoFrame(buffer, meta)) return
            val (w, h, pitch, fmt) = meta.toList()
            if (w <= 0 || h <= 0 || pitch <= 0 || pitch.toLong() * h > buffer.capacity()) return
            val bytes = ByteArray(pitch * h).also { buffer.position(0); buffer.get(it) }
            val bitmap = android.graphics.Bitmap.createBitmap(
                SaveStateFiles.opaqueArgb(bytes, w, h, pitch, fmt), w, h, android.graphics.Bitmap.Config.ARGB_8888
            ) ?: return
            val png = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png)
            bitmap.recycle()
            AtomicSaveFile.writeBytes(getCanonicalFile(identity, SaveStateFiles.slotThumbnail(slotIndex)), png.toByteArray())
        } catch (t: Throwable) {
            Log.w(TAG, "Slot thumbnail skipped: ${t.message}")
        }
    }

    // ---------------------------------------------------------
    // Manual Quick Save vs Auto Resume State
    // ---------------------------------------------------------

    fun quickSave(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (refuse("quickSave", identity)) return false
        val canonicalFile = writeState(identity, SaveStateFiles.QUICK_SAVE) ?: return false
        safMirrorStore.mirrorFile(identity, SaveStateFiles.QUICK_SAVE, canonicalFile)
        recordMetadata(identity, activeProfileId)
        return true
    }

    fun quickLoad(
        identity: RomIdentity,
        profileName: String? = null,
        profileId: String? = null
    ): Boolean = synchronized(globalSaveLock) {
        if (refuse("quickLoad", identity)) return false
        return loadStateFile(identity, SaveStateFiles.QUICK_SAVE, snapshotUndo = true)
    }

    fun saveAutoResume(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) return false
        return writeState(identity, SaveStateFiles.AUTO_RESUME) != null
    }

    fun loadAutoResume(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) return false
        return loadStateFile(identity, SaveStateFiles.AUTO_RESUME, snapshotUndo = false)
    }

    /**
     * Boot-time resume: loads the newest of auto-resume and the manual states (see
     * [SaveStateFiles.newestResumeState]), honouring the barrier set by [clearAutoResume].
     * Returns the loaded file name, or null when nothing was loaded (the game boots normally).
     */
    fun loadNewestResumeState(identity: RomIdentity): String? = synchronized(globalSaveLock) {
        if (refuse("loadNewestResumeState", identity)) return null
        val dir = getCanonicalRomDir(identity)
        val barrier = File(dir, SaveStateFiles.RESUME_BARRIER).lastModified() // 0 when absent
        val newest = SaveStateFiles.newestResumeState(dir, barrier) ?: return null
        return newest.name.takeIf { loadStateFile(identity, it, snapshotUndo = false) }
    }

    /**
     * Deletes the auto-resume state and makes every existing state ineligible for boot-time
     * resume, so a stale state can't roll back a battery save that was just replaced.
     * Manual slots stay on disk and remain loadable by hand.
     */
    fun clearAutoResume(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (!identity.isValid) return false
        val dir = getCanonicalRomDir(identity)
        File(dir, SaveStateFiles.AUTO_RESUME).delete()
        val barrier = File(dir, SaveStateFiles.RESUME_BARRIER)
        return try {
            barrier.writeText(System.currentTimeMillis().toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write resume barrier: ${e.message}")
            false
        }
    }

    // ---------------------------------------------------------
    // Restart / Close
    // ---------------------------------------------------------

    /** Flushes the battery save, then resets the core. Refuses (no reset) if the flush fails. */
    fun restartGame(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (refuse("restartGame", identity)) return false
        if (flushBatterySave(identity) !is SaveWriteResult.Success) return false
        nativeResetCore()
        return true
    }

    /** Flushes the battery save and resume state, then unloads the ROM. Refuses if the flush fails. */
    fun closeGame(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (refuse("closeGame", identity)) return false
        if (flushBatterySave(identity) !is SaveWriteResult.Success) return false
        saveAutoResume(identity)
        coreCoordinator.unloadRom()
        activeIdentity = null
        return true
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
        maxSlots: Int = SaveStateFiles.SLOT_COUNT,
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
        val expectedSize = if (fileName == "battery.sav") {
            nativeGetSaveRamSize().takeIf { it > 0 }
        } else if (fileName.endsWith(".state")) {
            nativeGetSaveStateSize().takeIf { it > 0 }
        } else null
        AtomicSaveFile.recoverInterrupted(canonicalFile, expectedSize)
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
        val expectedRamSize = nativeGetSaveRamSize()
        AtomicSaveFile.recoverInterrupted(canonicalFile, if (expectedRamSize > 0) expectedRamSize else null)
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

    fun flushBatterySave(
        identity: RomIdentity,
        mirrorSafAsync: Boolean = false
    ): SaveWriteResult = synchronized(globalSaveLock) {
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

        val initialStatus = if (!mirrorSafAsync) {
            val status = safMirrorStore.mirrorFile(identity, "battery.sav", canonicalFile)
            recordMetadata(identity, activeProfileId, explicitStatus = status)
            saveShareStore.export(identity, canonicalFile)
            status
        } else {
            val pendingStatus = if (safMirrorStore.isSafConfigured()) MirrorStatus.PENDING else MirrorStatus.UNAVAILABLE
            // Record metadata with PENDING status immediately without blocking on SAF hashing
            recordMetadata(identity, activeProfileId, explicitStatus = pendingStatus)

            // Asynchronous SAF mirror to avoid blocking on slow cloud/SAF document providers
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val finalStatus = safMirrorStore.mirrorFile(identity, "battery.sav", canonicalFile)
                    recordMetadata(identity, activeProfileId, explicitStatus = finalStatus)
                    synchronized(globalSaveLock) { saveShareStore.export(identity, canonicalFile) }
                } catch (e: Exception) {
                    Log.w(TAG, "Background SAF mirror failed: ${e.message}")
                    recordMetadata(identity, activeProfileId, explicitStatus = MirrorStatus.FAILED)
                }
            }
            pendingStatus
        }

        return SaveWriteResult.Success(
            canonicalWritten = true,
            mirrorStatus = initialStatus
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

            // 1. Determine active core's expected SRAM size (FAIL CLOSED if not determinable)
            val coreRamSize = nativeGetSaveRamSize()
            if (coreRamSize <= 0) {
                Log.e(TAG, "Import rejected: loaded core SRAM size cannot be determined ($coreRamSize)")
                return false
            }
            val expectedRamSize = coreRamSize.toInt()

            // 2. Normalize known emulator footer formats ONLY when appropriate
            // Standalone mGBA on PC adds a 16-byte RTC footer (expectedRamSize + 16)
            val cleanBytes = if (rawBytes.size == expectedRamSize + 16) {
                Log.i(TAG, "Stripping 16-byte mGBA RTC footer from import (${rawBytes.size} -> $expectedRamSize)")
                rawBytes.copyOfRange(0, expectedRamSize)
            } else {
                rawBytes
            }

            // 3. Require normalized size to match active game's expected SRAM size
            if (cleanBytes.size != expectedRamSize) {
                Log.e(TAG, "Import rejected: candidate file size ${cleanBytes.size} != expected SRAM size $expectedRamSize")
                return false
            }

            val sramBackup = File(stagingDir, "${identity.storageKey}__import_rollback.sav")
            val tempCandidate = File(stagingDir, "${identity.storageKey}__import_candidate.tmp")
            val canonicalFile = getCanonicalFile(identity, "battery.sav")

            val transactionSuccess = try {
                coreCoordinator.executeExclusive {
                    // 4. Capture current SRAM from active core to rollback file before mutating
                    val backupOk = nativeFlushSaveRam(sramBackup.absolutePath)
                    if (!backupOk || !sramBackup.exists() || sramBackup.length() != expectedRamSize.toLong()) {
                        Log.e(TAG, "Import aborted: failed to capture reliable rollback backup of live SRAM")
                        return@executeExclusive false
                    }

                    // 5. Test load candidate into live core from temp file
                    tempCandidate.writeBytes(cleanBytes)

                    val loaded = nativeLoadSaveRam(tempCandidate.absolutePath)
                    if (!loaded) {
                        Log.e(TAG, "Import rejected: core failed to load candidate SRAM payload")
                        nativeLoadSaveRam(sramBackup.absolutePath) // Rollback live SRAM
                        return@executeExclusive false
                    }

                    // 6. Commit candidate atomically to canonical store
                    val committed = AtomicSaveFile.writeBytes(canonicalFile, cleanBytes)
                    if (!committed) {
                        Log.e(TAG, "Import failed: atomic canonical commit failed, rolling back live SRAM")
                        nativeLoadSaveRam(sramBackup.absolutePath) // Rollback live SRAM
                        return@executeExclusive false
                    }

                    // 8. Reset core only after entire transaction succeeds
                    nativeResetCore()
                    true
                }
            } finally {
                if (tempCandidate.exists()) tempCandidate.delete()
                if (sramBackup.exists()) sramBackup.delete()
            }

            if (!transactionSuccess) {
                return false
            }

            // 7. Mirror to SAF and record metadata
            safMirrorStore.mirrorFile(identity, "battery.sav", canonicalFile)
            recordMetadata(identity, activeProfileId)
            // A resume state carries its own SRAM; booting into an older one would roll back this import.
            clearAutoResume(identity)

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

    /**
     * "LOAD SAVE" from the shared folder (#153): backs the current save up as
     * `<rom>.backup-<yyyyMMdd-HHmmss>.<ext>` beside it (aborting if that fails), then imports the
     * shared file through [importBatterySave] (size-validated, rollback-safe, clears auto-resume).
     */
    fun loadSharedSave(identity: RomIdentity): Boolean = synchronized(globalSaveLock) {
        if (refuse("loadSharedSave", identity)) return false
        val (name, bytes) = saveShareStore.readShared(identity) ?: return false
        val canonicalFile = getCanonicalFile(identity, "battery.sav")
        if (flushBatterySave(identity) !is SaveWriteResult.Success) return false
        if (!saveShareStore.writeBackup(identity, canonicalFile, name.substringAfterLast('.', "sav"))) return false
        if (!importBatterySave(identity, bytes.inputStream())) return false
        saveShareStore.markInSync(getCanonicalRomDir(identity), bytes)
        return true
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

    fun assignLegacyCandidate(candidate: LegacyCandidate, targetIdentity: RomIdentity): MigrationResult = synchronized(globalSaveLock) {
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

    fun getAllSlotsInfo(gameKey: String, maxSlots: Int = SaveStateFiles.SLOT_COUNT): List<SaveSlotInfo> =
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
