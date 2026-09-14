package com.dualdex.emulator

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dualdex.cheats.CheatManager
import com.dualdex.companion.CompanionViewModel
import com.dualdex.romhack.RomHackDetector
import com.dualdex.romhack.RomHackProfile
import com.dualdex.settings.SettingsManager
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class SwitchResult {
    data class Success(val identity: RomIdentity, val profile: RomHackProfile) : SwitchResult()
    data class Failure(val reason: String) : SwitchResult()
}

class RomSessionManager(
    private val context: Context? = null,
    private val viewModel: CompanionViewModel? = null,
    private val saveStateManager: SaveStateManager = context?.let { SaveStateManager.getInstance(it) } ?: SaveStateManager(),
    private val settingsManager: SettingsManager? = context?.let { SettingsManager(it) },
    private val cheatManager: CheatManager = CheatManager(context),
    private val audioDriver: AudioDriver? = null,
    private val customRomCacheDir: File? = null,
    coreBridge: LibretroCoreBridge? = null,
    customCoordinator: LibretroCoreCoordinator? = null
) {
    var coreCoordinator: LibretroCoreCoordinator = customCoordinator
        ?: saveStateManager.coreCoordinator

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

    private val sessionMutex = kotlinx.coroutines.sync.Mutex()
    private val romCacheDir: File
        get() = (customRomCacheDir ?: File(context?.filesDir ?: File("build/test_rom_cache"), "rom_cache")).apply {
            if (!exists()) mkdirs()
        }

    private fun openRomStream(uri: Uri): java.io.InputStream? {
        return if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: uri.toString()
            val f = File(path)
            if (f.exists()) f.inputStream() else null
        } else {
            context?.contentResolver?.openInputStream(uri)
        }
    }

    /**
     * Executes a safe, serialized ROM switch transaction.
     * Guarantees:
     * 1. Incoming ROM is fully copied, verified by size and SHA-256 before touching active session.
     * 2. Old game is flushed to canonical store before unload.
     * 3. If old flush fails, switch aborts and old game remains active.
     * 4. Old game is unloaded before new game is loaded under core exclusive lock.
     * 5. New identity and profile are ONLY published after new ROM load succeeds.
     */
    suspend fun switchRom(
        uri: Uri?,
        loadedProfiles: List<RomHackProfile>,
        preferredTitle: String? = null,
        onEmulationPause: (() -> Unit)? = null,
        onEmulationResume: (() -> Unit)? = null
    ): SwitchResult {
        if (uri == null) return SwitchResult.Failure("ROM URI is null")
        return executeSwitch(
            streamProvider = { openRomStream(uri) },
            identifierStr = uri.toString(),
            loadedProfiles = loadedProfiles,
            preferredTitle = preferredTitle,
            onEmulationPause = onEmulationPause,
            onEmulationResume = onEmulationResume
        )
    }

    suspend fun switchRomFile(
        file: File,
        loadedProfiles: List<RomHackProfile>,
        preferredTitle: String? = null,
        onEmulationPause: (() -> Unit)? = null,
        onEmulationResume: (() -> Unit)? = null
    ): SwitchResult {
        return executeSwitch(
            streamProvider = { if (file.exists()) file.inputStream() else null },
            identifierStr = file.absolutePath,
            loadedProfiles = loadedProfiles,
            preferredTitle = preferredTitle,
            onEmulationPause = onEmulationPause,
            onEmulationResume = onEmulationResume
        )
    }

    private suspend fun executeSwitch(
        streamProvider: () -> java.io.InputStream?,
        identifierStr: String,
        loadedProfiles: List<RomHackProfile>,
        preferredTitle: String? = null,
        onEmulationPause: (() -> Unit)? = null,
        onEmulationResume: (() -> Unit)? = null
    ): SwitchResult = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            try {
                // 1. Prepare new ROM in cache before touching running session
                val tmpIncoming = File(romCacheDir, "incoming_${System.currentTimeMillis()}.tmp")
                val bytesCopied = streamProvider()?.use { input ->
                    FileOutputStream(tmpIncoming).use { output ->
                        input.copyTo(output)
                    }
                } ?: 0L

                val incomingLength = tmpIncoming.length()
                if (bytesCopied == 0L || !tmpIncoming.exists() || incomingLength == 0L) {
                    if (tmpIncoming.exists()) tmpIncoming.delete()
                    return@withLock SwitchResult.Failure("Failed to read ROM stream from source")
                }

                val hash = RomIdentity.calculateSha256(tmpIncoming)
                if (hash.isEmpty() || hash.length != 64) {
                    if (tmpIncoming.exists()) tmpIncoming.delete()
                    return@withLock SwitchResult.Failure("Failed to compute SHA-256 for incoming ROM")
                }

                val cachedRomFile = File(romCacheDir, "$hash.gba")
                if (!cachedRomFile.exists() || cachedRomFile.length() != incomingLength) {
                    val installed = try {
                        Files.move(
                            tmpIncoming.toPath(),
                            cachedRomFile.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        true
                    } catch (e: Exception) {
                        try {
                            Files.move(
                                tmpIncoming.toPath(),
                                cachedRomFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                            true
                        } catch (e2: Exception) {
                            tmpIncoming.renameTo(cachedRomFile)
                        }
                    }
                    if (!installed) {
                        if (tmpIncoming.exists()) tmpIncoming.delete()
                        return@withLock SwitchResult.Failure("Failed to install ROM into cache: ${cachedRomFile.name}")
                    }
                } else {
                    tmpIncoming.delete()
                }

                // Verify cache installation: existence, size, and re-hash
                if (!cachedRomFile.exists()) {
                    return@withLock SwitchResult.Failure("Cached ROM file does not exist after install: ${cachedRomFile.name}")
                }
                if (cachedRomFile.length() != incomingLength) {
                    return@withLock SwitchResult.Failure("Cached ROM file size mismatch: expected $incomingLength, got ${cachedRomFile.length()}")
                }
                val finalHash = RomIdentity.calculateSha256(cachedRomFile)
                if (!finalHash.equals(hash, ignoreCase = true)) {
                    return@withLock SwitchResult.Failure("Cached ROM SHA-256 verification failed: expected $hash, got $finalHash")
                }

                val detection = RomHackDetector.detectProfileWithConfidence(cachedRomFile, loadedProfiles, preferredTitle)
                val profile = detection.profile
                val gameTitle = preferredTitle ?: profile.name.ifEmpty { "current_game" }
                val newIdentity = RomIdentity.fromFile(cachedRomFile, gameTitle)

                if (!newIdentity.isValid) {
                    return@withLock SwitchResult.Failure("Computed invalid ROM identity for ${cachedRomFile.name}")
                }

                // 2. Pause emulation and memory polling
                onEmulationPause?.invoke()
                audioDriver?.stop()
                viewModel?.stopPolling()

                // 3. Execute the entire core-sensitive switch sequence in ONE continuous exclusive transaction
                val oldIdentity = viewModel?.activeRomIdentity?.value ?: saveStateManager.activeIdentity
                var switchAbortedDueToFlush = false

                val switchSuccess = synchronized(SaveStateManager.globalSaveLock) {
                    coreCoordinator.executeExclusive {
                        if (oldIdentity != null && oldIdentity.isValid) {
                            val flushResult = saveStateManager.flushBatterySave(oldIdentity)
                            if (flushResult !is com.dualdex.emulator.storage.SaveWriteResult.Success) {
                                Log.e(TAG, "Failed to flush SRAM for previous game ${oldIdentity.displayName}. Aborting ROM switch!")
                                switchAbortedDueToFlush = true
                                return@executeExclusive false
                            }
                            saveStateManager.saveAutoResume(oldIdentity)
                        }

                        coreCoordinator.unloadRom()
                        coreCoordinator.clearAudio()

                        val ok = coreCoordinator.loadRom(cachedRomFile.absolutePath)
                        if (!ok) {
                            Log.e(TAG, "Core failed to load ROM: ${cachedRomFile.absolutePath}")
                            coreCoordinator.unloadRom()
                            return@executeExclusive false
                        }

                        saveStateManager.setActiveGame(newIdentity, profile.name, profile.id)
                        val restoredSave = saveStateManager.loadBatterySave(newIdentity, profile.name, profile.id)
                        if (restoredSave) {
                            Log.i(TAG, "Restored existing battery save for ${newIdentity.storageKey}")
                        }

                        cheatManager.applyCheats(newIdentity)
                        true
                    }
                }

                if (switchAbortedDueToFlush) {
                    audioDriver?.start()
                    viewModel?.startPolling(100L)
                    onEmulationResume?.invoke()
                    return@withLock SwitchResult.Failure("Could not safely flush previous game save. ROM switch aborted.")
                }

                if (!switchSuccess) {
                    saveStateManager.activeIdentity = null
                    viewModel?.setRomIdentity(null)
                    viewModel?.stopPolling()
                    return@withLock SwitchResult.Failure("Core rejected ROM file")
                }

                // 4. Post-switch initialization: audio and settings
                audioDriver?.updateSampleRate()
                audioDriver?.start()

                // 5. Publish new identity and resume emulation outside the core transaction
                settingsManager?.lastPlayedRomUri = identifierStr
                settingsManager?.lastPlayedRomTitle = gameTitle

                viewModel?.setRomSession(profile, newIdentity, detection)

                viewModel?.startPolling(100L)
                onEmulationResume?.invoke()

                Log.i(TAG, "ROM switch completed successfully to ${newIdentity.displayName} (${newIdentity.storageKey})")
                return@withLock SwitchResult.Success(newIdentity, profile)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during ROM switch: ${e.message}", e)
                return@withLock SwitchResult.Failure("Exception during ROM switch: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "RomSessionManager"
    }
}
