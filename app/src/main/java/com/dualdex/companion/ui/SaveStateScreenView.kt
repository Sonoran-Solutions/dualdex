package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.SaveSlotInfo
import com.dualdex.emulator.SaveStateManager
import com.dualdex.emulator.storage.LegacyCandidate
import com.dualdex.emulator.storage.SaveShareStore
import com.dualdex.emulator.storage.SaveStateFiles
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Redesigned Saves screen adhering to the Quiet Handheld Companion design system.
 * Provides fast quick-save/load, compact manual slot management, cartridge battery save
 * import/export, and plain-language storage mirror status.
 */
class SaveStateScreenView(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val onImportSaveRequested: (() -> Unit)? = null,
    private val onExportSaveRequested: (() -> Unit)? = null,
    private val onChooseSavesFolderRequested: (() -> Unit)? = null
) : LinearLayout(context) {

    private val saveStateManager = SaveStateManager.getInstance(context)
    private var viewScope: CoroutineScope? = null
    private val busyDependentButtons = mutableListOf<Pair<TextView, Boolean>>()
    private val activeOperationIds = mutableSetOf<Long>()
    private var nextOperationId = 0L
    private var refreshGeneration = 0L

    // Active Game Context Views
    private val emptyGameView: LinearLayout
    private val gameContextCard: LinearLayout
    private val gameTitleView: TextView
    private val gameMetaView: TextView
    private val gameStorageKeyView: TextView

    // Quick Save Views
    private val quickSaveCard: LinearLayout
    private val quickSaveStatusView: TextView
    private val qSaveBtn: TextView
    private val qLoadBtn: TextView
    private val undoLoadBtn: TextView
    private var hasUndo = false

    // Save Slots Views
    private val slotsSection: LinearLayout
    private val slotsContainer: LinearLayout

    // Battery Save Views
    private val batterySaveCard: LinearLayout
    private val batterySaveStatusView: TextView
    private val importBtn: TextView
    private val exportBtn: TextView

    // Storage Views
    private val storageCard: LinearLayout
    private val storageLocationView: TextView
    private val storageMirrorStatusView: TextView
    private val chooseFolderBtn: TextView
    private val syncSafBtn: TextView

    // Save sharing with other emulators (#153, opt-in)
    private val shareStatusView: TextView
    private val shareFolderBtn: TextView
    private val loadSharedBtn: TextView
    private val settingsManager = SettingsManager(context)

    // Legacy Migration Views
    private val legacySection: LinearLayout
    private val legacyContainer: LinearLayout

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)
        setPadding(
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section),
            0
        )

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.major))
        }
        scroll.addView(content)
        addView(scroll)

        // 1. Screen Title
        content.addView(
            DualDexComponents.screenTitle(context, "Saves"),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(DualDexTheme.Spacing.section)
            }
        )

        // 2. Active Game Context Card
        gameContextCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            gameTitleView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.sectionTitle
                typeface = Typeface.DEFAULT_BOLD
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(gameTitleView)

            gameMetaView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
            }
            addView(gameMetaView)

            gameStorageKeyView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textDisabled)
                textSize = DualDexTheme.Type.compact
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
            }
            addView(gameStorageKeyView)
        }
        content.addView(gameContextCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // Empty state when no game is active
        emptyGameView = DualDexComponents.emptyState(
            context,
            "No game loaded",
            "Open a game from your Library to manage save states and cartridge saves."
        ).apply {
            background = DualDexComponents.surface(context, elevated = false)
            visibility = View.GONE
        }
        content.addView(emptyGameView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 3. Quick Save Section
        quickSaveCard = DualDexComponents.surfaceCard(context, elevated = true).apply {
            addView(DualDexComponents.sectionTitle(context, "Quick Save"))

            quickSaveStatusView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.standard))
            }
            addView(quickSaveStatusView)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                qSaveBtn = DualDexComponents.primaryButton(context, "Quick Save") {
                    val identity = getRomIdentity() ?: return@primaryButton
                    performAsyncOperation("Quick state saved!", "Save failed!") {
                        saveStateManager.quickSave(identity)
                    }
                }
                addView(qSaveBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                })

                qLoadBtn = DualDexComponents.secondaryButton(context, "Quick Load") {
                    val identity = getRomIdentity() ?: return@secondaryButton
                    performAsyncOperation("Quick state loaded!", "No quick save found!") {
                        saveStateManager.quickLoad(identity)
                    }
                }
                addView(qLoadBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f))
            }
            addView(btnRow)

            undoLoadBtn = DualDexComponents.ghostControl(context, "Undo Last Load") {
                val identity = getRomIdentity() ?: return@ghostControl
                performAsyncOperation("Restored the game from before the last load", "Nothing to undo") {
                    saveStateManager.undoLoad(identity)
                }
            }
            addView(undoLoadBtn, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
                topMargin = context.dp(DualDexTheme.Spacing.compact)
            })
        }
        content.addView(quickSaveCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 4. Save Slots Section (1 - 5)
        slotsSection = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(DualDexComponents.sectionTitle(context, "Save Slots"), LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })

            slotsContainer = LinearLayout(context).apply {
                orientation = VERTICAL
            }
            addView(slotsContainer)
        }
        content.addView(slotsSection, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 5. Battery Save (.sav) Section
        batterySaveCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "Battery Save (.sav)"))

            val desc = TextView(context).apply {
                text = "In-game cartridge saves are automatically preserved in protected app storage."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(desc)

            batterySaveStatusView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.standard))
            }
            addView(batterySaveStatusView)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                importBtn = DualDexComponents.secondaryButton(context, "Import .sav") {
                    onImportSaveRequested?.invoke()
                }
                addView(importBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                })

                exportBtn = DualDexComponents.secondaryButton(context, "Export .sav") {
                    onExportSaveRequested?.invoke()
                }
                addView(exportBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f))
            }
            addView(btnRow)
        }
        content.addView(batterySaveCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 6. Save Storage & Mirror Section
        storageCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "Save Storage"))

            val desc = TextView(context).apply {
                text = "DualDex saves directly to private internal storage. You can optionally mirror saves to a shared folder for PC transfer or external backup."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(desc)

            storageLocationView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
            }
            addView(storageLocationView)

            storageMirrorStatusView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.standard))
            }
            addView(storageMirrorStatusView)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                chooseFolderBtn = DualDexComponents.secondaryButton(context, "Choose Folder") {
                    onChooseSavesFolderRequested?.invoke()
                }
                addView(chooseFolderBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                })

                syncSafBtn = DualDexComponents.secondaryButton(context, "Sync to Folder") {
                    val identity = getRomIdentity() ?: return@secondaryButton
                    performAsyncOperation("Saves synced to mirror folder", "Mirror folder up to date") {
                        val count = saveStateManager.syncCanonicalToSaf(identity)
                        count > 0
                    }
                }
                addView(syncSafBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f))
            }
            addView(btnRow)

            addView(DualDexComponents.sectionTitle(context, "Share With Other Emulators"), LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
            ).apply { topMargin = context.dp(DualDexTheme.Spacing.section) })
            shareStatusView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.standard))
            }
            addView(shareStatusView)

            val shareRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                shareFolderBtn = DualDexComponents.secondaryButton(context, "Share Folder") {
                    if (settingsManager.shareSavesFolderUri != null) {
                        DualDexComponents.confirm(context, "Stop sharing saves?",
                            "DualDex stops copying <rom>.sav to the shared folder. Files already there are left alone.",
                            "Stop Sharing") {
                            settingsManager.shareSavesFolderUri = null
                            refreshUI()
                        }
                    } else {
                        SaveShareStore.requestFolderPicker?.invoke(SaveShareStore.initialPickerUri(context))
                    }
                }
                addView(shareFolderBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                })

                loadSharedBtn = DualDexComponents.secondaryButton(context, "LOAD SAVE") {
                    val identity = getRomIdentity() ?: return@secondaryButton
                    DualDexComponents.confirm(context, "Load the shared save?",
                        "Your current save is backed up as <rom>.backup-<date>.sav in the shared folder, then the " +
                            "shared <rom>.sav replaces it and the game restarts. Auto-resume is cleared so an old " +
                            "state can't overwrite it.",
                        "Load Save") {
                        performAsyncOperation("Shared save loaded", "Could not load shared save (missing, wrong size, or backup failed)") {
                            saveStateManager.loadSharedSave(identity)
                        }
                    }
                }
                addView(loadSharedBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f))
            }
            addView(shareRow)
        }
        content.addView(storageCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 7. Legacy Saves Section (Hidden by default, shown only if unmigrated saves exist)
        legacySection = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            addView(DualDexComponents.sectionTitle(context, "Discovered Legacy Saves"), LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.tight) })

            val desc = TextView(context).apply {
                text = "Unmigrated saves found from earlier DualDex versions. Selecting migrate will associate them with the active game."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(desc)

            legacyContainer = LinearLayout(context).apply { orientation = VERTICAL }
            addView(legacyContainer)
        }
        content.addView(legacySection, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        refreshUI()
    }

    private fun getRomIdentity(): RomIdentity? = viewModel.activeRomIdentity.value?.takeIf { it.isValid }

    private val isBusy: Boolean
        get() = activeOperationIds.isNotEmpty()

    private fun renderBusyState() {
        val hasGame = getRomIdentity() != null
        qSaveBtn.isEnabled = !isBusy && hasGame
        qLoadBtn.isEnabled = !isBusy && hasGame
        undoLoadBtn.isEnabled = !isBusy && hasGame && hasUndo
        shareFolderBtn.isEnabled = !isBusy
        loadSharedBtn.isEnabled = !isBusy && hasGame && settingsManager.shareSavesFolderUri != null
        importBtn.isEnabled = !isBusy && hasGame
        exportBtn.isEnabled = !isBusy && hasGame
        chooseFolderBtn.isEnabled = !isBusy
        syncSafBtn.isEnabled = !isBusy && hasGame
        busyDependentButtons.forEach { (button, enabledWithoutBusy) ->
            button.isEnabled = !isBusy && enabledWithoutBusy
        }
    }

    private fun beginBusyOperation(): Long {
        val id = ++nextOperationId
        activeOperationIds += id
        renderBusyState()
        return id
    }

    private fun endBusyOperation(id: Long) {
        if (activeOperationIds.remove(id)) renderBusyState()
    }

    private fun performAsyncOperation(
        successMsg: String,
        failureMsg: String,
        operation: suspend () -> Boolean
    ) {
        val scope = viewScope ?: return
        val operationId = beginBusyOperation()
        scope.launch(Dispatchers.IO) {
            val ok = try {
                operation()
            } catch (e: Exception) {
                false
            }
            withContext(NonCancellable + Dispatchers.Main) {
                endBusyOperation(operationId)
                if (isAttachedToWindow) {
                    Toast.makeText(context, if (ok) successMsg else failureMsg, Toast.LENGTH_SHORT).show()
                    refreshUI()
                }
            }
        }
    }

    fun refreshUI() {
        val myGeneration = ++refreshGeneration
        val identity = getRomIdentity()
        val profile = viewModel.activeProfile.value

        val sharing = settingsManager.shareSavesFolderUri != null
        shareFolderBtn.text = if (sharing) "Stop Sharing" else "Share Folder"
        shareStatusView.text = if (sharing) {
            "On: in-game saves are also copied to <rom>.sav in the shared folder. A save changed by another " +
                "emulator is never overwritten; use LOAD SAVE to bring it in."
        } else {
            "Off. Pick a folder (e.g. RetroArch/saves/mGBA) to keep a plain <rom>.sav that other emulators can use."
        }
        if (identity == null || !identity.isValid) {
            hasUndo = false
            gameContextCard.visibility = View.GONE
            emptyGameView.visibility = View.VISIBLE
            busyDependentButtons.clear()
            renderBusyState()

            quickSaveStatusView.text = "No active game loaded."
            batterySaveStatusView.text = "No active game loaded."
            storageLocationView.text = "Internal Storage: Active (Private)"
            storageMirrorStatusView.text = "Shared Mirror: Inactive"

            slotsContainer.removeAllViews()
            val emptySlotText = TextView(context).apply {
                text = "No save slots available without an active game."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            slotsContainer.addView(emptySlotText)

            legacySection.visibility = View.GONE
            legacyContainer.removeAllViews()
            return
        }

        emptyGameView.visibility = View.GONE
        gameContextCard.visibility = View.VISIBLE
        gameTitleView.text = identity.displayName
        gameMetaView.text = "Build ${identity.shortHash} · ${profile.name.ifBlank { "Standard GBA" }}"
        gameStorageKeyView.text = "Storage Key: ${identity.storageKey}"

        renderBusyState()

        quickSaveStatusView.text = "Checking quick save status..."
        batterySaveStatusView.text = "Checking battery save status..."
        storageMirrorStatusView.text = "Checking mirror status..."

        val scope = viewScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch(Dispatchers.IO) {
            val isSaf = saveStateManager.isUsingSaf()
            val mirrorStatus = saveStateManager.getSafMirrorStatus(identity)
            val dirDesc = saveStateManager.getSaveDirectoryDescription()

            val bInfo = saveStateManager.getBatterySaveInfo(identity, profile.name, profile.id)
            val qFile = saveStateManager.getCanonicalFile(identity, "quicksave.state")
            val qLastModified = if (qFile.exists()) qFile.lastModified() else 0L

            val slots = saveStateManager.getAllSlotsInfo(identity, SaveStateFiles.SLOT_COUNT, profile.name, profile.id)
            val thumbs = slots.associate { slot ->
                slot.slotIndex to saveStateManager.getSlotThumbnail(identity, slot.slotIndex)
                    ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
            }
            val undoAvailable = saveStateManager.hasUndoLoad(identity)
            val candidates = saveStateManager.discoverLegacyCandidates()

            withContext(Dispatchers.Main) {
                if (!isActive || !isAttachedToWindow || myGeneration != refreshGeneration) return@withContext
                if (getRomIdentity() != identity || viewModel.activeProfile.value != profile) return@withContext

                // Quick Save status
                quickSaveStatusView.text = if (qLastModified > 0L) {
                    val sdf = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
                    "Saved: ${sdf.format(Date(qLastModified))}"
                } else {
                    "No quick save created yet."
                }

                // Battery Save status
                batterySaveStatusView.text = if (bInfo.exists) {
                    val sizeKb = bInfo.sizeBytes / 1024
                    "Active Save: ${sizeKb} KB · ${bInfo.formattedDate}"
                } else {
                    "No .sav file found on disk (auto-saves on in-game save / pause)."
                }

                // Storage status
                storageLocationView.text = "Internal Storage: Active (Private)"
                storageMirrorStatusView.text = if (isSaf) {
                    "Shared Mirror: $dirDesc (${mirrorStatus})"
                } else {
                    "Shared Mirror: None configured (select a folder to enable PC sync)"
                }

                // Slots
                hasUndo = undoAvailable
                renderSlots(slots, thumbs, identity, profile)

                // Legacy Candidates
                renderLegacyCandidates(candidates, identity)
            }
        }
    }

    /**
     * Thumbnail grid, two per row. Tap a filled tile to load it; SAVE writes the slot (asking first
     * when it would overwrite). The most recently written slot is highlighted as the current one.
     */
    private fun renderSlots(
        slots: List<SaveSlotInfo>,
        thumbs: Map<Int, Bitmap?>,
        identity: RomIdentity,
        profile: com.dualdex.romhack.RomHackProfile
    ) {
        busyDependentButtons.clear()
        slotsContainer.removeAllViews()
        val current = slots.filter { it.exists }.maxByOrNull { it.timestampMs }?.slotIndex
        slots.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            pair.forEachIndexed { i, slot ->
                row.addView(slotTile(slot, thumbs[slot.slotIndex], slot.slotIndex == current, identity, profile),
                    LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (i == 0) marginEnd = context.dp(DualDexTheme.Spacing.compact)
                    })
            }
            if (pair.size == 1) row.addView(View(context), LayoutParams(0, 0, 1f))
            slotsContainer.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (rowIndex > 0) topMargin = context.dp(DualDexTheme.Spacing.compact)
            })
        }
        renderBusyState()
    }

    private fun slotTile(
        slot: SaveSlotInfo,
        thumb: Bitmap?,
        isCurrent: Boolean,
        identity: RomIdentity,
        profile: com.dualdex.romhack.RomHackProfile
    ): View = LinearLayout(context).apply {
        orientation = VERTICAL
        background = if (isCurrent) {
            DualDexComponents.lcdPanel(context, strokeColor = DualDexTheme.Color.accent)
        } else {
            DualDexComponents.surface(context)
        }
        val pad = context.dp(DualDexTheme.Spacing.compact)
        setPadding(pad, pad, pad, pad)
        contentDescription = "Slot ${slot.slotIndex}" + if (slot.exists) ", saved ${slot.formattedDate}" else ", empty"

        // GBA aspect (3:2) from the tile width.
        val image = object : ImageView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val w = MeasureSpec.getSize(widthMeasureSpec)
                setMeasuredDimension(w, w * 2 / 3)
            }
        }.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(DualDexTheme.Color.background)
            if (thumb != null) {
                setImageBitmap(thumb)
                drawable?.isFilterBitmap = false // pixel art: no smoothing when scaled up
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(TextView(context).apply {
            text = "Slot ${slot.slotIndex}" + if (isCurrent) " · current" else ""
            setTextColor(if (isCurrent) DualDexTheme.Color.accent else DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
        })
        addView(TextView(context).apply {
            text = if (slot.exists) slot.formattedDate else "Empty"
            setTextColor(if (slot.exists) DualDexTheme.Color.textSecondary else DualDexTheme.Color.textDisabled)
            textSize = DualDexTheme.Type.meta
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        })

        if (slot.exists) {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (isBusy) return@setOnClickListener
                performAsyncOperation("Slot ${slot.slotIndex} loaded (Undo Last Load reverts)", "Load failed!") {
                    saveStateManager.loadSlot(identity, slot.slotIndex, profile.name, profile.id)
                }
            }
        }

        val saveBtn = DualDexComponents.secondaryButton(context, "SAVE") {
            val doSave = {
                performAsyncOperation("Slot ${slot.slotIndex} saved!", "Save failed!") {
                    saveStateManager.saveSlot(identity, slot.slotIndex)
                }
            }
            if (slot.exists) {
                DualDexComponents.confirm(context, "Overwrite slot ${slot.slotIndex}?",
                    "Saved ${slot.formattedDate}. The previous state is kept as a .bak until the next overwrite.",
                    "Overwrite") { doSave() }
            } else {
                doSave()
            }
        }
        busyDependentButtons += saveBtn to true
        addView(saveBtn, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
            topMargin = context.dp(DualDexTheme.Spacing.tight)
        })
    }

    private fun renderLegacyCandidates(candidates: List<LegacyCandidate>, identity: RomIdentity) {
        legacyContainer.removeAllViews()
        if (candidates.isEmpty()) {
            legacySection.visibility = View.GONE
            return
        }

        legacySection.visibility = View.VISIBLE
        candidates.forEachIndexed { index, cand ->
            val candRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
                background = DualDexComponents.surface(context, elevated = false)
                setPadding(
                    context.dp(DualDexTheme.Spacing.standard),
                    context.dp(DualDexTheme.Spacing.compact),
                    context.dp(DualDexTheme.Spacing.compact),
                    context.dp(DualDexTheme.Spacing.compact)
                )
            }

            val textLayout = LinearLayout(context).apply {
                orientation = VERTICAL
                val title = TextView(context).apply {
                    text = cand.sourceFile.name
                    setTextColor(DualDexTheme.Color.textPrimary)
                    textSize = DualDexTheme.Type.body
                    typeface = Typeface.DEFAULT_BOLD
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                val meta = TextView(context).apply {
                    val sizeKb = cand.sizeBytes / 1024
                    text = "${cand.suggestedTitle} · ${sizeKb} KB"
                    setTextColor(DualDexTheme.Color.textSecondary)
                    textSize = DualDexTheme.Type.meta
                    setPadding(0, context.dp(DualDexTheme.Spacing.tight / 2), 0, 0)
                }
                addView(title)
                addView(meta)
            }
            candRow.addView(textLayout, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

            val migrateBtn = DualDexComponents.secondaryButton(context, "Migrate") {
                performAsyncOperation("Migrated ${cand.sourceFile.name}!", "Migration failed") {
                    val res = saveStateManager.assignLegacyCandidate(cand, identity)
                    res.isSuccess
                }
            }
            busyDependentButtons += migrateBtn to true
            candRow.addView(migrateBtn, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(DualDexTheme.Spacing.touchTarget)))

            legacyContainer.addView(candRow, LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = if (index == candidates.lastIndex) 0 else context.dp(DualDexTheme.Spacing.compact)
            })
        }
        renderBusyState()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { scope ->
            scope.launch {
                viewModel.activeRomIdentity.collectLatest { refreshUI() }
            }
            scope.launch {
                viewModel.activeProfile.collectLatest { refreshUI() }
            }
        }
        refreshUI()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
        refreshGeneration++
        activeOperationIds.clear()
        busyDependentButtons.clear()
        renderBusyState()
    }
}
