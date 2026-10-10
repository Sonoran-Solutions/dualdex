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
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.lifecycleScope
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dualdex.assistant.RomHackAssistant
import com.dualdex.calculator.DamageCalculator
import com.dualdex.companion.CompanionPresentation
import com.dualdex.companion.CompanionTab
import com.dualdex.companion.CompanionViewModel
import com.dualdex.companion.ui.CompanionScreenView
import com.dualdex.companion.ui.DualDexTheme
import com.dualdex.emulator.AudioDriver
import com.dualdex.emulator.CoreOwner
import com.dualdex.emulator.EmulatorSurfaceView
import com.dualdex.emulator.InputManager
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.LibretroHost
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.SmartFastForward
import com.dualdex.emulator.SaveStateManager
import com.dualdex.emulator.ShaderFilter
import com.dualdex.emulator.RomUriPermissionManager
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomHackDetector
import com.dualdex.romhack.RomHackProfile
import com.dualdex.settings.SettingsManager
import com.dualdex.settings.TriggerShortcutMode
import com.dualdex.emulator.TouchOverlayView
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), DisplayManager.DisplayListener {

    private val viewModel = CompanionViewModel()
    private val saveStateManager by lazy { SaveStateManager.getInstance(this) }
    private val settingsManager by lazy { SettingsManager(this) }
    private val romSessionManager by lazy {
        com.dualdex.emulator.RomSessionManager(
            context = this,
            viewModel = viewModel,
            saveStateManager = saveStateManager,
            settingsManager = settingsManager,
            audioDriver = audioDriver
        )
    }
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
            val oldLastPlayed = settingsManager.lastPlayedRomUri
            val grantResult = RomUriPermissionManager.takePersistableReadPermission(contentResolver, uri)
            val isDurable = grantResult.isDurable
            // Capture previous individual URI candidate for release, but ONLY release it
            // after the new ROM switch transaction completes successfully.
            val previousDurableUriToRelease = if (isDurable) oldLastPlayed else null
            handleSelectedRom(
                uri = uri,
                isDurable = isDurable,
                previousDurableUriToRelease = previousDurableUriToRelease,
                newlyAcquiredPersistableGrant = grantResult.isNewlyAcquired
            )
        }
    }

    /** L2/R2 (#14): behaviour follows the Settings "L2 / R2 Behavior" mode. */
    private fun onControllerShortcut(trigger: InputManager.Trigger) {
        when (settingsManager.triggerShortcutMode) {
            TriggerShortcutMode.DISABLED, TriggerShortcutMode.HOLD_SPEED -> Unit
            TriggerShortcutMode.FAST_FORWARD -> {
                val emu = emulatorView ?: return
                val delta = if (trigger == InputManager.Trigger.L2) -1 else 1
                val speed = SettingsManager.steppedSpeed(emu.getSpeedMultiplier(), delta)
                applySpeedStep(speed) // Settings screen follows via its prefs listener
                Toast.makeText(this, "Speed ${speed}x", Toast.LENGTH_SHORT).show()
            }
            TriggerShortcutMode.QUICK_SAVE_LOAD -> quickStateShortcut(trigger == InputManager.Trigger.L2)
        }
    }

    private fun quickStateShortcut(save: Boolean) {
        val identity = viewModel.activeRomIdentity.value?.takeIf { it.isValid } ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val ok = try {
                if (save) saveStateManager.quickSave(identity) else saveStateManager.quickLoad(identity)
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                val msg = when {
                    save -> if (ok) "Quick saved" else "Quick save failed"
                    else -> if (ok) "Quick loaded" else "No quick save found"
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
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

    private val chooseSavesFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("DualDex", "Could not take persistable URI permission for saves: ${e.message}")
            }
            settingsManager.savesFolderUri = uri.toString()
            CoroutineScope(Dispatchers.IO).launch {
                val identity = viewModel.activeRomIdentity.value
                val count = if (identity != null && identity.isValid) {
                    saveStateManager.syncCanonicalToSaf(identity)
                } else 0
                withContext(Dispatchers.Main) {
                    if (count > 0) {
                        Toast.makeText(this@MainActivity, "Synced $count save file(s) to selected folder!", Toast.LENGTH_SHORT).show()
                    }
                    companionPresentation?.refreshSavesTab()
                    currentCompanionScreenView?.refreshSavesTab()
                }
            }
        }
    }

    /** #153 save sharing: opt-in folder where `<rom>.sav` is shared with other emulators. */
    private val chooseShareSavesFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                settingsManager.shareSavesFolderUri = uri.toString()
                Toast.makeText(this, "Save sharing on: saves are copied as <rom>.sav", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not get write access to that folder", Toast.LENGTH_LONG).show()
            }
            companionPresentation?.refreshSavesTab()
            currentCompanionScreenView?.refreshSavesTab()
        }
    }

    private val importSaveLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val identity = viewModel.activeRomIdentity.value
            if (identity == null || !identity.isValid) {
                Toast.makeText(this@MainActivity, "No active ROM loaded", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            CoroutineScope(Dispatchers.IO).launch {
                val success = saveStateManager.importBatterySave(identity, uri)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@MainActivity, "Imported battery save! Core reset to load save.", Toast.LENGTH_LONG).show()
                        companionPresentation?.refreshSavesTab()
                        currentCompanionScreenView?.refreshSavesTab()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to import battery save (.sav): invalid format or size", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val exportSaveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            val identity = viewModel.activeRomIdentity.value
            if (identity == null || !identity.isValid) {
                Toast.makeText(this@MainActivity, "No active ROM loaded", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            CoroutineScope(Dispatchers.IO).launch {
                val success = saveStateManager.exportBatterySave(identity, uri)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@MainActivity, "Exported battery save successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to export battery save: SRAM capture failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun scanRomsDirectory(folderUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val romList = com.dualdex.library.LibraryScanner.scan(applicationContext, folderUri, loadedProfiles)
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

    private val pickCoverLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val sha = pendingCoverSha ?: return@registerForActivityResult
        pendingCoverSha = null
        if (uri != null && com.dualdex.library.CoverArt.setFromUri(this, sha, uri)) {
            settingsManager.romsFolderUri?.let { scanRomsDirectory(Uri.parse(it)) }
        }
    }
    private var pendingCoverSha: String? = null

    /** True after a [FrontendLaunchActivity] hand-off: Back then returns to the frontend. */
    private val backToFrontend = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { moveTaskToBack(true) }
    }

    /** Plays a ROM forwarded by [FrontendLaunchActivity]; any other intent ends frontend mode. */
    private fun handleLaunchIntent(intent: Intent) {
        val path = intent.getStringExtra(FrontendLaunchActivity.EXTRA_ROM_PATH)
        backToFrontend.isEnabled = path != null
        if (path != null) handleSelectedRom(Uri.fromFile(File(path)), intent.getStringExtra(FrontendLaunchActivity.EXTRA_TITLE))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoreOwner.process.claim(this)
        onBackPressedDispatcher.addCallback(this, backToFrontend)
        viewModel.pauseEmulation = { emulatorView?.pauseEmulationLoop() }
        viewModel.resumeEmulation = { emulatorView?.resumeEmulationLoop() }

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
                val loaded = LibretroCoreCoordinator.defaultInstance.loadCore(coreFile.absolutePath)
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

            // 5. Apply saved Gemini settings and feature gates
            val apiKey = settingsManager.geminiApiKey
            if (!apiKey.isNullOrBlank()) {
                RomHackAssistant.setApiKey(apiKey)
            }
            RomHackAssistant.setModel(settingsManager.geminiModel)
            viewModel.setBattleAutoOpenEnabled(settingsManager.isBattleAutoOpenEnabled)
            viewModel.setInteractiveBattleControlsEnabled(settingsManager.isInteractiveBattleControlsEnabled)

            // 5.5 Input defaults (#153): one-time AYN/Retroid A/B swap for untouched configs
            settingsManager.migrateSwapABDefault(Build.MANUFACTURER, Build.BRAND, Build.MODEL)
            settingsManager.registerChangeListener(inputPrefsListener)
            com.dualdex.library.CoverArt.pickRequest = { sha ->
                pendingCoverSha = sha
                pickCoverLauncher.launch(arrayOf("image/*"))
            }
            com.dualdex.emulator.storage.SaveShareStore.requestFolderPicker = { initial ->
                runOnUiThread { chooseShareSavesFolderLauncher.launch(initial) }
            }

            // 6. Setup display UI
            DualDexTheme.style = settingsManager.companionVisualStyle
            setupDisplays()

            // Re-apply the remembered speed step whenever the active ROM changes.
            lifecycleScope.launch {
                viewModel.activeRomIdentity.collect { id ->
                    emulatorView?.setSpeedMultiplier(settingsManager.romSpeed(id?.takeIf { it.isValid }?.storageKey))
                }
            }

            // 7. Scan saved ROMs folder if available
            val savedFolder = settingsManager.romsFolderUri
            if (!savedFolder.isNullOrBlank()) {
                try {
                    scanRomsDirectory(Uri.parse(savedFolder))
                } catch (e: Exception) {
                    Log.w("DualDex", "Failed to scan saved ROMs folder: ${e.message}")
                }
            }

            // 7.5 Check and migrate legacy saves from previous app version on first open
            if (!settingsManager.legacySavesCheckedOnFirstOpen) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val migration = saveStateManager.checkAndMigrateLegacySavesOnFirstOpen()
                        settingsManager.legacySavesCheckedOnFirstOpen = true
                        if (migration.filesFound > 0) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "DualDex found ${migration.filesFound} legacy save file(s) across ${migration.gameTitles.size} game(s). Ready to play!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DualDex", "Error checking legacy saves on startup: ${e.message}", e)
                    }
                }
            }

            // 8. Start background memory poller (10Hz)
            viewModel.startPolling(100L)

            if (savedInstanceState == null) handleLaunchIntent(intent)
        } catch (e: Throwable) {
            Log.e("DualDex", "Fatal error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createSmartFastForward(identity: RomIdentity, gameId: Int): SmartFastForward? {
        if (!identity.isValid) return null
        val core = LibretroCoreCoordinator.defaultInstance
        val sha = identity.sha256.lowercase()
        return SmartFastForward(
            memory = { address, length -> core.readGbaMemory(address, length) },
            knownMainAddress = core.mainStructAddress(gameId),
            learned = SmartFastForward.Learned.decode(settingsManager.smartFastForwardLearned(sha)),
            location = {
                core.readPlayerLocation(gameId)?.let { SmartFastForward.packLocation(it.mapGroup, it.mapNum, it.x, it.y) }
            },
            onLearned = { settingsManager.setSmartFastForwardLearned(sha, it.encode()) },
            isEnabled = { settingsManager.isSmartFastForwardEnabled },
        )
    }

    private fun handleSelectedRom(
        uri: Uri,
        preferredTitle: String? = null,
        isDurable: Boolean = true,
        previousDurableUriToRelease: String? = null,
        newlyAcquiredPersistableGrant: Boolean = false
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = romSessionManager.switchRom(
                uri = uri,
                loadedProfiles = loadedProfiles,
                preferredTitle = preferredTitle,
                isDurable = isDurable,
                onEmulationPause = {
                    // Only the stepping loop is suspended here. Calling the GLSurfaceView
                    // onPause()/onResume() pair instead would tear down the render thread for the
                    // whole switch; because opening a ROM does not pause the Activity, the
                    // matching onResume() never arrives and the top screen stays black.
                    runOnUiThread { emulatorView?.pauseEmulationLoop() }
                },
                onEmulationResume = {
                    runOnUiThread {
                        emulatorView?.resumeEmulationLoop()
                        viewModel.selectTab(CompanionTab.PARTY)
                        companionPresentation?.refreshSavesTab()
                        currentCompanionScreenView?.refreshSavesTab()
                        companionPresentation?.refreshHomeScreen()
                        currentCompanionScreenView?.refreshHomeScreen()
                    }
                }
            )

            withContext(Dispatchers.Main) {
                when (result) {
                    is com.dualdex.emulator.SwitchResult.Success -> {
                        if (previousDurableUriToRelease != null) {
                            RomUriPermissionManager.releasePersistableReadPermissionIfRedundant(
                                contentResolver = contentResolver,
                                oldUriStr = previousDurableUriToRelease,
                                newUriStr = uri.toString(),
                                protectedUris = setOfNotNull(settingsManager.romsFolderUri, settingsManager.savesFolderUri)
                            )
                        }
                        emulatorView?.smartFastForward = createSmartFastForward(result.identity, result.profile.gameId)
                        Toast.makeText(
                            this@MainActivity,
                            "Loaded: ${result.profile.name} (${result.profile.engine})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is com.dualdex.emulator.SwitchResult.Failure -> {
                        if (newlyAcquiredPersistableGrant) {
                            RomUriPermissionManager.releasePersistableReadPermissionOnFailure(
                                contentResolver = contentResolver,
                                candidateUriStr = uri.toString(),
                                currentContinueUriStr = settingsManager.lastPlayedRomUri,
                                protectedUris = setOfNotNull(settingsManager.romsFolderUri, settingsManager.savesFolderUri),
                                isNewlyAcquired = true
                            )
                        }
                        val wasContinueTarget = settingsManager.lastPlayedRomUri == uri.toString()
                        if (wasContinueTarget && result.isAccessError) {
                            settingsManager.clearLastPlayedRom()
                            companionPresentation?.refreshHomeScreen()
                            currentCompanionScreenView?.refreshHomeScreen()
                            Toast.makeText(
                                this@MainActivity,
                                RomUriPermissionManager.RECOVERY_MESSAGE,
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (result.isAccessError) {
                            Toast.makeText(
                                this@MainActivity,
                                RomUriPermissionManager.RECOVERY_MESSAGE,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Error opening ROM: ${result.reason}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private var gameFrame: FrameLayout? = null
    private var touchOverlay: TouchOverlayView? = null

    /** The game surface plus the touch overlay above it, created once and re-parented on layout changes. */
    private fun gameContainer(): FrameLayout {
        gameFrame?.let { (it.parent as? ViewGroup)?.removeView(it); return it }
        val emu = EmulatorSurfaceView(this).apply {
            setStretchToFit(settingsManager.isStretchToFitEnabled)
            setSpeedMultiplier(settingsManager.romSpeed(viewModel.activeRomIdentity.value?.storageKey))
            setSwapAB(settingsManager.swapAB)
            setShortcutHandler(::onControllerShortcut)
            setTriggerHoldHandler(::onTriggerHold)
            setChordHandler { chord ->
                if (chord.id == InputManager.CHORD_TOGGLE_FAST_FORWARD) post {
                    val speed = toggleFastForward()
                    Toast.makeText(this@MainActivity, "Speed ${speed}x", Toast.LENGTH_SHORT).show()
                }
            }
        }
        emulatorView = emu
        val overlay = TouchOverlayView(this) { mask -> emu.setTouchMask(mask) }.apply {
            mode = settingsManager.touchOverlayMode
        }
        touchOverlay = overlay
        val match = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        return FrameLayout(this).apply {
            addView(emu, match)
            addView(overlay, FrameLayout.LayoutParams(match))
        }.also { gameFrame = it }
    }

    private fun onTriggerHold(trigger: InputManager.Trigger, held: Boolean) {
        if (settingsManager.triggerShortcutMode != TriggerShortcutMode.HOLD_SPEED) return
        val emu = emulatorView ?: return
        emu.holdSpeed = when {
            held && trigger == InputManager.Trigger.L2 -> SettingsManager.HOLD_SLOW_SPEED
            held -> SettingsManager.HOLD_FAST_SPEED
            else -> 0f
        }
    }

    /** Explicit speed step: applied now and remembered for this ROM (and as the global default). */
    private fun applySpeedStep(speed: Int) {
        emulatorView?.setSpeedMultiplier(speed)
        settingsManager.setRomSpeed(viewModel.activeRomIdentity.value?.takeIf { it.isValid }?.storageKey, speed)
    }

    /** Tokens are read at view construction, so a style change rebuilds the companion shell. */
    private fun rebuildCompanionForStyleChange() {
        val presentation = companionPresentation
        if (presentation?.isShowing == true) {
            presentation.rebuildContent()
        } else {
            currentCompanionScreenView?.release()
            currentCompanionScreenView = null
            setupDisplays()
        }
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

            topRoot.addView(gameFrameFor(gameContainer()), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

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

    /** Emulator surface inside the frame that adds the optional status bar (#153). */
    private fun gameFrameFor(emu: View): com.dualdex.emulator.GameFrameLayout {
        val frame = (emu.parent as? com.dualdex.emulator.GameFrameLayout)
            ?: com.dualdex.emulator.GameFrameLayout(this, viewModel, settingsManager)
        (frame.parent as? ViewGroup)?.removeView(frame)
        frame.setGameView(emu)
        return frame
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

        splitLayout.addView(gameFrameFor(gameContainer()), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ))

        val companionView = CompanionScreenView(
            context = this,
            viewModel = viewModel,
            onOpenRomRequested = { openRomLauncher.launch(arrayOf("*/*")) },
            onShaderChanged = { filter: ShaderFilter -> emulatorView?.setShaderFilter(filter) },
            onSpeedChanged = { speed: Int -> applySpeedStep(speed) },
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
                handleSelectedRom(uri, title)
            },
            onStretchChanged = { stretch: Boolean ->
                emulatorView?.setStretchToFit(stretch)
            },
            onChooseSavesFolderRequested = { chooseSavesFolderLauncher.launch(null) },
            onVisualStyleChanged = ::rebuildCompanionForStyleChange
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
                onSpeedChanged = { speed: Int -> applySpeedStep(speed) },
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
                    handleSelectedRom(uri, title)
                },
                onStretchChanged = { stretch: Boolean ->
                    emulatorView?.setStretchToFit(stretch)
                },
                onChooseSavesFolderRequested = { chooseSavesFolderLauncher.launch(null) },
            onVisualStyleChanged = ::rebuildCompanionForStyleChange
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
        // Auto-flush cartridge battery save (.sav) and save auto-resume state on pause
        // Offload SAF mirroring asynchronously to prevent blocking on slow cloud DocumentProviders
        val identity = viewModel.activeRomIdentity.value
        // Only the core owner writes per-ROM files; the save manager also refuses a stale identity.
        if (identity != null && identity.isValid && CoreOwner.process.isOwner(this)) {
            saveStateManager.flushBatterySave(identity, mirrorSafAsync = true)
            saveStateManager.saveAutoResume(identity)
        }
    }

    override fun onResume() {
        super.onResume()
        LibretroCoreCoordinator.defaultInstance.clearAudio()
        emulatorView?.onResume()
        audioDriver.start()
        com.dualdex.library.AppUpdater.onHostResume(this)

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

    private val inputPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        emulatorView?.setSwapAB(settingsManager.swapAB)
        touchOverlay?.mode = settingsManager.touchOverlayMode
        if (settingsManager.triggerShortcutMode != TriggerShortcutMode.HOLD_SPEED) emulatorView?.holdSpeed = 0f
    }

    override fun onDestroy() {
        settingsManager.unregisterChangeListener(inputPrefsListener)
        currentCompanionScreenView?.release()
        com.dualdex.emulator.storage.SaveShareStore.requestFolderPicker = null
        super.onDestroy()
        val owner = CoreOwner.process.release(this)
        val identity = viewModel.activeRomIdentity.value
        if (owner && identity != null && identity.isValid) {
            saveStateManager.flushBatterySave(identity, mirrorSafAsync = true)
        }
        audioDriver.stop()
        viewModel.stopPolling()
        displayManager?.unregisterDisplayListener(this)
        companionPresentation?.dismiss()
        companionPresentation = null
        currentCompanionScreenView = null
        emulatorView?.onPause()
        // A newer activity already re-initialised the core; tearing it down would kill its game.
        if (owner) LibretroCoreCoordinator.defaultInstance.cleanup()
    }
}
