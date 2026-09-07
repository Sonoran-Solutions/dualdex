package com.dualdex

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dualdex.assistant.RomHackAssistant
import com.dualdex.calculator.DamageCalculator
import com.dualdex.companion.CompanionPresentation
import com.dualdex.companion.CompanionTab
import com.dualdex.companion.CompanionViewModel
import com.dualdex.companion.RomItem
import com.dualdex.companion.ui.CompanionScreenView
import com.dualdex.emulator.AudioDriver
import com.dualdex.emulator.EmulatorSurfaceView
import com.dualdex.emulator.LibretroHost
import com.dualdex.emulator.SaveStateManager
import com.dualdex.emulator.ShaderFilter
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomHackDetector
import com.dualdex.romhack.RomHackProfile
import com.dualdex.settings.SettingsManager
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), DisplayManager.DisplayListener {

    private val viewModel = CompanionViewModel()
    private val saveStateManager by lazy { SaveStateManager(this) }
    private val settingsManager by lazy { SettingsManager(this) }
    private var emulatorView: EmulatorSurfaceView? = null
    private var companionPresentation: CompanionPresentation? = null
    private var currentCompanionScreenView: CompanionScreenView? = null
    private var displayManager: DisplayManager? = null
    private var restoreBottomScreenBtn: TextView? = null
    private var loadedProfiles: List<RomHackProfile> = emptyList()
    private val audioDriver = AudioDriver(32768)

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()


    private val openRomLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // OpenDocument grants are transient unless persisted explicitly. Take a
            // persistable READ permission so this ROM can survive a process kill or
            // reboot as the "Continue Last Game" target. Some providers refuse
            // non-persistable grants — log and proceed; a failed resume later clears
            // the stale target in handleSelectedRom.
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("DualDex", "Could not take persistable URI permission: ${e.message}")
            }
            handleSelectedRom(uri)
        }
    }

    private val chooseFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("DualDex", "Could not take persistable URI permission: ${e.message}")
            }
            settingsManager.romsFolderUri = uri.toString()
            scanRomsDirectory(uri)
        }
    }

    private val importSaveLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val gameKey = viewModel.activeRomTitle.value.ifEmpty { "current_game" }
            val success = saveStateManager.importBatterySave(gameKey, uri)
            if (success) {
                Toast.makeText(this@MainActivity, "Imported battery save! Game reset to load save.", Toast.LENGTH_LONG).show()
                companionPresentation?.refreshSavesTab()
                currentCompanionScreenView?.refreshSavesTab()
            } else {
                Toast.makeText(this@MainActivity, "Failed to import battery save (.sav)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val exportSaveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            val gameKey = viewModel.activeRomTitle.value.ifEmpty { "current_game" }
            saveStateManager.flushBatterySave(gameKey)
            val success = saveStateManager.exportBatterySave(gameKey, uri)
            if (success) {
                Toast.makeText(this@MainActivity, "Exported battery save successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Failed to export battery save", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun scanRomsDirectory(folderUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rootDoc = DocumentFile.fromTreeUri(applicationContext, folderUri)
                if (rootDoc == null || !rootDoc.isDirectory) {
                    Log.w("DualDex", "Selected URI is not a valid directory: $folderUri")
                    return@launch
                }

                val romList = mutableListOf<RomItem>()
                val files = rootDoc.listFiles()
                for (file in files) {
                    val name = file.name ?: continue
                    if (name.endsWith(".gba", ignoreCase = true) || name.endsWith(".bin", ignoreCase = true)) {
                        val title = name.substringBeforeLast(".")
                        val length = file.length()
                        val formattedSize = if (length >= 1024 * 1024) {
                            String.format(Locale.US, "%.1f MB", length / (1024.0 * 1024.0))
                        } else {
                            "${length / 1024} KB"
                        }
                        romList.add(
                            RomItem(
                                title = title,
                                fileName = name,
                                uri = file.uri,
                                sizeFormatted = formattedSize
                            )
                        )
                    }
                }
                romList.sortBy { it.title.lowercase() }

                withContext(Dispatchers.Main) {
                    viewModel.setScannedRoms(romList)
                    companionPresentation?.refreshHomeScreen()
                    currentCompanionScreenView?.refreshHomeScreen()
                }
            } catch (e: Exception) {
                Log.e("DualDex", "Error scanning ROMs directory: ${e.message}", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // 1. Initialize QuickJS damage calculator engine in background
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    DamageCalculator.initialize(applicationContext)
                } catch (e: Throwable) {
                    Log.e("DualDex", "Failed to init DamageCalculator: ${e.message}")
                }
            }

            // 2. Load ROM Hack JSON profiles from assets
            loadedProfiles = ProfileLoader.loadProfilesFromAssets(this)
            Log.i("DualDex", "Loaded ${loadedProfiles.size} ROM hack profiles.")

            // 3. Initialize mGBA Libretro core
            val libDir = applicationInfo.nativeLibraryDir
            val coreFile = listOf(
                File(libDir, "libmgba_libretro.so"),
                File(libDir, "mgba_libretro.so")
            ).firstOrNull { it.exists() }

            if (coreFile != null) {
                val loaded = LibretroHost.nativeLoadCore(coreFile.absolutePath)
                Log.i("DualDex", "Loaded mGBA Libretro core: $loaded (path=${coreFile.absolutePath})")
                if (loaded) {
                    audioDriver.start()
                }
            } else {
                Log.w("DualDex", "Core file not found in $libDir")
            }

            // 4. Register DisplayManager listener for AYN Thor secondary display
            displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayManager?.registerDisplayListener(this, null)

            // 5. Apply saved Gemini settings
            val apiKey = settingsManager.geminiApiKey
            if (!apiKey.isNullOrBlank()) {
                RomHackAssistant.setApiKey(apiKey)
            }
            RomHackAssistant.setModel(settingsManager.geminiModel)

            // 6. Setup display UI
            setupDisplays()

            // 7. Scan saved ROMs folder if available
            val savedFolder = settingsManager.romsFolderUri
            if (!savedFolder.isNullOrBlank()) {
                try {
                    scanRomsDirectory(Uri.parse(savedFolder))
                } catch (e: Exception) {
                    Log.w("DualDex", "Failed to scan saved ROMs folder: ${e.message}")
                }
            }

            // 8. Start background memory poller (10Hz)
            viewModel.startPolling(100L)
        } catch (e: Throwable) {
            Log.e("DualDex", "Fatal error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleSelectedRom(uri: Uri, preferredTitle: String? = null, fromContinue: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            // True once the URI stream was successfully copied to local storage.
            // Failures before this point mean the source URI was unusable, which is
            // exactly when a stale "Continue Last Game" target should be cleared.
            var romReadLocally = false
            try {
                // Copy URI stream to a local cache file for Libretro dlopen/fopen access
                val romsDir = File(filesDir, "roms").apply { if (!exists()) mkdirs() }
                val localRomFile = File(romsDir, "current_game.gba")
                if (localRomFile.exists()) localRomFile.delete()

                val bytesCopied = contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localRomFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: 0L

                if (!localRomFile.exists() || localRomFile.length() == 0L || bytesCopied == 0L) {
                    withContext(Dispatchers.Main) {
                        if (fromContinue) {
                            handleStaleContinueTarget(null)
                        } else {
                            Toast.makeText(this@MainActivity, "Failed to read ROM file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }
                romReadLocally = true

                // Detect ROM Hack Profile via SHA-256 and header title
                val profile = RomHackDetector.detectProfile(localRomFile, loadedProfiles, preferredTitle)
                val gameTitle = preferredTitle ?: profile.name.ifEmpty { "current_game" }
                settingsManager.lastPlayedRomUri = uri.toString()
                settingsManager.lastPlayedRomTitle = gameTitle

                withContext(Dispatchers.Main) {
                    viewModel.setProfile(profile)
                }
                Log.i("DualDex", "Detected ROM Hack Profile: ${profile.name} (Engine: ${profile.engine}, GameId: ${profile.gameId})")

                // Load ROM into mGBA core
                val ok = LibretroHost.nativeLoadRom(localRomFile.absolutePath)
                if (ok) {
                    LibretroHost.nativeClearAudio()
                    audioDriver.updateSampleRate()
                    // Auto-load cartridge battery save (.sav) if present
                    val gameKey = profile.name.ifEmpty { "current_game" }
                    val loadedSave = saveStateManager.loadBatterySave(gameKey)
                    if (loadedSave) {
                        Log.i("DualDex", "Restored existing battery save for $gameKey")
                    }
                    // Auto-apply active cheats for this game
                    val cheatManager = com.dualdex.cheats.CheatManager(this@MainActivity)
                    cheatManager.applyCheats(gameKey)
                }
                withContext(Dispatchers.Main) {
                    if (ok) {
                        emulatorView?.startEmulation()
                        Toast.makeText(this@MainActivity, "Loaded: ${profile.name} (${profile.engine})", Toast.LENGTH_LONG).show()
                        viewModel.selectTab(CompanionTab.PARTY)
                        companionPresentation?.refreshSavesTab()
                        currentCompanionScreenView?.refreshSavesTab()
                        companionPresentation?.refreshHomeScreen()
                        currentCompanionScreenView?.refreshHomeScreen()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to load ROM in mGBA core", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (fromContinue && !romReadLocally) {
                        handleStaleContinueTarget(e.message)
                    } else {
                        Toast.makeText(this@MainActivity, "Error opening ROM: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Companion play entry point. The Resume button passes the stored
     * lastPlayedRomUri verbatim, so a URI that no longer exists reaches the
     * recoverable Continue-failure path. It may also match a ROM listed from a
     * folder, which is still a safe target to clear on failure. Other URIs are
     * opened exactly as before.
     */
    private fun playStoredOrResumedRom(uri: Uri, title: String) {
        val fromContinue = settingsManager.lastPlayedRomUri != null && uri.toString() == settingsManager.lastPlayedRomUri
        handleSelectedRom(uri, title, fromContinue)
    }

    /**
     * A resume attempt failed because the stored URI was not readable (ROM deleted,
     * moved, or access revoked). Notify the user, clear the stale target so it is
     * never presented indefinitely, and refresh the home screens so the resume card
     * hides via HomeScreenView.shouldShowResumeCard().
     */
    private fun handleStaleContinueTarget(detail: String?) {
        settingsManager.clearLastPlayedRom()
        val message = if (detail.isNullOrBlank()) {
            "Could not resume last game. The ROM may have been moved, deleted, or lost access, so it was removed from Continue."
        } else {
            "Could not resume last game (${detail}). It was removed from Continue."
        }
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        companionPresentation?.refreshHomeScreen()
        currentCompanionScreenView?.refreshHomeScreen()
    }

    private fun setupDisplays() {
        val dm = displayManager ?: run {
            setupSplitScreen()
            return
        }

        val presentationDisplays = try {
            dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } catch (e: Throwable) {
            emptyArray()
        }

        if (presentationDisplays.isNotEmpty()) {
            // Dual-screen mode (AYN Thor detected)
            Log.i("DualDex", "AYN Thor dual-screen detected. Attaching CompanionPresentation to Display 1.")

            val topRoot = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val emu = emulatorView ?: EmulatorSurfaceView(this).apply {
                setStretchToFit(settingsManager.isStretchToFitEnabled)
            }
            emulatorView = emu
            (emu.parent as? ViewGroup)?.removeView(emu)
            emu.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            topRoot.addView(emu)

            val restoreBtn = TextView(this).apply {
                text = "📱 Restore Companion"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#D9121624"))
                    cornerRadius = dpToPx(16).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#806366F1"))
                }
                elevation = dpToPx(8).toFloat()
                visibility = if (companionPresentation?.isShowing == true) View.GONE else View.VISIBLE
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dpToPx(14)
                    marginEnd = dpToPx(14)
                }
                layoutParams = lp
                setOnClickListener {
                    val displays = try {
                        displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                    } catch (e: Throwable) { emptyArray() }
                    if (!displays.isNullOrEmpty()) {
                        showPresentation(displays[0])
                        Toast.makeText(this@MainActivity, "Restored companion screen", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Secondary display not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            restoreBottomScreenBtn = restoreBtn
            topRoot.addView(restoreBtn)

            setContentView(topRoot)

            showPresentation(presentationDisplays[0])
        } else {
            setupSplitScreen()
        }
    }

    private fun setupSplitScreen() {
        Log.i("DualDex", "Running in split-screen fallback mode.")
        restoreBottomScreenBtn?.visibility = View.GONE
        val splitLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val emu = emulatorView ?: EmulatorSurfaceView(this).apply {
            setStretchToFit(settingsManager.isStretchToFitEnabled)
        }
        emulatorView = emu
        (emu.parent as? ViewGroup)?.removeView(emu)
        emu.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        )
        splitLayout.addView(emu)

        val companionView = CompanionScreenView(
            context = this,
            viewModel = viewModel,
            onOpenRomRequested = { openRomLauncher.launch(arrayOf("*/*")) },
            onShaderChanged = { filter: ShaderFilter -> emulatorView?.setShaderFilter(filter) },
            onSpeedChanged = { speed: Int -> emulatorView?.setSpeedMultiplier(speed) },
            onImportSaveRequested = { importSaveLauncher.launch(arrayOf("*/*", "application/octet-stream")) },
            onExportSaveRequested = {
                val key = viewModel.activeRomTitle.value.ifEmpty { "current_game" }.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                exportSaveLauncher.launch("$key.sav")
            },
            onChooseRomsFolderRequested = { chooseFolderLauncher.launch(null) },
            onRefreshRomsRequested = {
                val folder = settingsManager.romsFolderUri
                if (!folder.isNullOrBlank()) {
                    scanRomsDirectory(Uri.parse(folder))
                } else {
                    chooseFolderLauncher.launch(null)
                }
            },
            onPlayRomRequested = { uri: Uri, title: String ->
                playStoredOrResumedRom(uri, title)
            },
            onStretchChanged = { stretch: Boolean ->
                emulatorView?.setStretchToFit(stretch)
            }
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        currentCompanionScreenView = companionView
        splitLayout.addView(companionView)

        setContentView(splitLayout)
    }

    private fun showPresentation(display: Display) {
        try {
            companionPresentation?.dismiss()
            companionPresentation = CompanionPresentation(
                context = this,
                display = display,
                viewModel = viewModel,
                onOpenRomRequested = { openRomLauncher.launch(arrayOf("*/*")) },
                onShaderChanged = { filter: ShaderFilter -> emulatorView?.setShaderFilter(filter) },
                onSpeedChanged = { speed: Int -> emulatorView?.setSpeedMultiplier(speed) },
                onImportSaveRequested = { importSaveLauncher.launch(arrayOf("*/*", "application/octet-stream")) },
                onExportSaveRequested = {
                    val key = viewModel.activeRomTitle.value.ifEmpty { "current_game" }.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                    exportSaveLauncher.launch("$key.sav")
                },
                onChooseRomsFolderRequested = { chooseFolderLauncher.launch(null) },
                onRefreshRomsRequested = {
                    val folder = settingsManager.romsFolderUri
                    if (!folder.isNullOrBlank()) {
                        scanRomsDirectory(Uri.parse(folder))
                    } else {
                        chooseFolderLauncher.launch(null)
                    }
                },
                onPlayRomRequested = { uri: Uri, title: String ->
                    playStoredOrResumedRom(uri, title)
                },
                onStretchChanged = { stretch: Boolean ->
                    emulatorView?.setStretchToFit(stretch)
                }
            ).apply {
                setOnDismissListener {
                    runOnUiThread {
                        restoreBottomScreenBtn?.visibility = View.VISIBLE
                    }
                }
                show()
            }
            restoreBottomScreenBtn?.visibility = View.GONE
        } catch (e: Throwable) {
            Log.e("DualDex", "Error showing CompanionPresentation: ${e.message}, falling back to split screen", e)
            restoreBottomScreenBtn?.visibility = View.VISIBLE
            setupSplitScreen()
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        setupDisplays()
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (companionPresentation?.display?.displayId == displayId) {
            companionPresentation?.dismiss()
            companionPresentation = null
            setupDisplays()
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        val presentationDisplays = try {
            displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } catch (e: Throwable) { emptyArray() }
        if (!presentationDisplays.isNullOrEmpty()) {
            if (companionPresentation?.isShowing != true) {
                runOnUiThread {
                    restoreBottomScreenBtn?.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        emulatorView?.onPause()
        audioDriver.stop()
        // Auto-flush cartridge battery save (.sav) and save quick state on pause
        val gameKey = viewModel.activeRomTitle.value.ifEmpty { "current_game" }
        if (gameKey.isNotBlank()) {
            saveStateManager.flushBatterySave(gameKey)
            saveStateManager.quickSave(gameKey)
        }
    }

    override fun onResume() {
        super.onResume()
        LibretroHost.nativeClearAudio()
        emulatorView?.onResume()
        audioDriver.start()

        val presentationDisplays = try {
            displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        } catch (e: Throwable) { emptyArray() }
        if (!presentationDisplays.isNullOrEmpty()) {
            if (companionPresentation?.isShowing != true) {
                restoreBottomScreenBtn?.visibility = View.VISIBLE
            } else {
                restoreBottomScreenBtn?.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val gameKey = viewModel.activeRomTitle.value.ifEmpty { "current_game" }
        if (gameKey.isNotBlank()) {
            saveStateManager.flushBatterySave(gameKey)
        }
        audioDriver.stop()
        viewModel.stopPolling()
        displayManager?.unregisterDisplayListener(this)
        companionPresentation?.dismiss()
        companionPresentation = null
        currentCompanionScreenView = null
        emulatorView?.onPause()
        LibretroHost.nativeCleanup()
    }
}
