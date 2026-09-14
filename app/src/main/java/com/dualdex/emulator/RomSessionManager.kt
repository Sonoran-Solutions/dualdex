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
    var coreBridge: LibretroCoreBridge? = null
) {
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
     * 1. Old game is flushed to canonical store before unload.
     * 2. If old flush fails, switch aborts and old game remains active.
     * 3. Old game is unloaded before new game is loaded.
     * 4. New identity and profile are ONLY published after new ROM load succeeds.
     * 5. Content-hash-specific cache prevents overwriting the currently running ROM.
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

                if (bytesCopied == 0L || !tmpIncoming.exists() || tmpIncoming.length() == 0L) {
                    if (tmpIncoming.exists()) tmpIncoming.delete()
                    return@withLock SwitchResult.Failure("Failed to read ROM stream from source")
                }

                val hash = RomIdentity.calculateSha256(tmpIncoming)
                if (hash.isEmpty() || hash.length != 64) {
                    if (tmpIncoming.exists()) tmpIncoming.delete()
                    return@withLock SwitchResult.Failure("Failed to compute SHA-256 for incoming ROM")
                }

                val cachedRomFile = File(romCacheDir, "$hash.gba")
                if (!cachedRomFile.exists() || cachedRomFile.length() != tmpIncoming.length()) {
                    tmpIncoming.renameTo(cachedRomFile)
                } else {
                    tmpIncoming.delete()
                }

                val profile = RomHackDetector.detectProfile(cachedRomFile, loadedProfiles, preferredTitle)
                val gameTitle = preferredTitle ?: profile.name.ifEmpty { "current_game" }
                val newIdentity = RomIdentity.fromFile(cachedRomFile, gameTitle)

                if (!newIdentity.isValid) {
                    return@withLock SwitchResult.Failure("Computed invalid ROM identity for ${cachedRomFile.name}")
                }

                // 2. Pause emulation and memory polling
                onEmulationPause?.invoke()
                audioDriver?.stop()
                viewModel?.stopPolling()

                // 3. Flush and unload previous ROM if present
                val oldIdentity = viewModel?.activeRomIdentity?.value ?: saveStateManager.activeIdentity
                if (oldIdentity != null && oldIdentity.isValid) {
                    val flushResult = saveStateManager.flushBatterySave(oldIdentity)
                    if (flushResult !is com.dualdex.emulator.storage.SaveWriteResult.Success) {
                        Log.e(TAG, "Failed to flush SRAM for previous game ${oldIdentity.displayName}. Aborting ROM switch!")
                        // Resume old session safely
                        audioDriver?.start()
                        viewModel?.startPolling(100L)
                        onEmulationResume?.invoke()
                        return@withLock SwitchResult.Failure("Could not safely flush previous game save. ROM switch aborted.")
                    }
                    saveStateManager.saveAutoResume(oldIdentity)
                }

                // 4. Clean unload of old game
                if (coreBridge != null) coreBridge?.unloadRom() else LibretroHost.nativeUnloadRom()
                if (coreBridge == null) LibretroHost.nativeClearAudio()

                // 5. Load prepared new ROM into core
                val ok = coreBridge?.loadRom(cachedRomFile.absolutePath) ?: LibretroHost.nativeLoadRom(cachedRomFile.absolutePath)
                if (!ok) {
                    Log.e(TAG, "mGBA core failed to load ROM: ${cachedRomFile.absolutePath}")
                    if (coreBridge != null) coreBridge?.unloadRom() else LibretroHost.nativeUnloadRom()
                    saveStateManager.activeIdentity = null
                    viewModel?.setRomIdentity(null)
                    viewModel?.stopPolling()
                    return@withLock SwitchResult.Failure("mGBA core rejected ROM file")
                }

                // 6. Post-load initialization: restore save & cheats scoped to new identity
                audioDriver?.updateSampleRate()
                audioDriver?.start()

                saveStateManager.setActiveGame(newIdentity, profile.name, profile.id)
                val restoredSave = saveStateManager.loadBatterySave(newIdentity, profile.name, profile.id)
                if (restoredSave) {
                    Log.i(TAG, "Restored existing battery save for ${newIdentity.storageKey}")
                }

                cheatManager.applyCheats(newIdentity)

                // 7. Publish new identity and resume
                settingsManager?.lastPlayedRomUri = identifierStr
                settingsManager?.lastPlayedRomTitle = gameTitle

                viewModel?.setProfile(profile)
                viewModel?.setRomIdentity(newIdentity)

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
