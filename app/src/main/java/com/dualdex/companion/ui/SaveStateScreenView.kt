package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.SaveSlotInfo
import com.dualdex.emulator.SaveStateManager
import com.dualdex.emulator.storage.LegacyCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private var isBusy = false

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

    private fun setBusyState(busy: Boolean) {
        isBusy = busy
        val hasGame = getRomIdentity() != null
        qSaveBtn.isEnabled = !busy && hasGame
        qLoadBtn.isEnabled = !busy && hasGame
        importBtn.isEnabled = !busy && hasGame
        exportBtn.isEnabled = !busy && hasGame
        chooseFolderBtn.isEnabled = !busy
        syncSafBtn.isEnabled = !busy && hasGame
    }

    private fun performAsyncOperation(
        successMsg: String,
        failureMsg: String,
        operation: suspend () -> Boolean
    ) {
        setBusyState(true)
        val scope = viewScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch(Dispatchers.IO) {
            val ok = try {
                operation()
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                if (isActive) {
                    setBusyState(false)
                    Toast.makeText(context, if (ok) successMsg else failureMsg, Toast.LENGTH_SHORT).show()
                    refreshUI()
                }
            }
        }
    }

    fun refreshUI() {
        val identity = getRomIdentity()
        val profile = viewModel.activeProfile.value

        if (identity == null || !identity.isValid) {
            gameContextCard.visibility = View.GONE
            emptyGameView.visibility = View.VISIBLE
            setBusyState(false)

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

        setBusyState(isBusy)

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

            val slots = saveStateManager.getAllSlotsInfo(identity, 5, profile.name, profile.id)
            val candidates = saveStateManager.discoverLegacyCandidates()

            withContext(Dispatchers.Main) {
                if (!isActive) return@withContext

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
                renderSlots(slots, identity, profile)

                // Legacy Candidates
                renderLegacyCandidates(candidates, identity)
            }
        }
    }

    private fun renderSlots(
        slots: List<SaveSlotInfo>,
        identity: RomIdentity,
        profile: com.dualdex.romhack.RomHackProfile
    ) {
        slotsContainer.removeAllViews()
        slots.forEachIndexed { index, slot ->
            val slotRow = LinearLayout(context).apply {
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

            val labelLayout = LinearLayout(context).apply {
                orientation = VERTICAL
                val title = TextView(context).apply {
                    text = "Slot ${slot.slotIndex}"
                    setTextColor(DualDexTheme.Color.textPrimary)
                    textSize = DualDexTheme.Type.body
                    typeface = Typeface.DEFAULT_BOLD
                }
                val subtitle = TextView(context).apply {
                    text = if (slot.exists) {
                        "${slot.formattedDate} · ${slot.sizeBytes / 1024} KB"
                    } else {
                        "Empty slot"
                    }
                    setTextColor(if (slot.exists) DualDexTheme.Color.textSecondary else DualDexTheme.Color.textDisabled)
                    textSize = DualDexTheme.Type.meta
                    setPadding(0, context.dp(DualDexTheme.Spacing.tight / 2), 0, 0)
                }
                addView(title)
                addView(subtitle)
            }
            slotRow.addView(labelLayout, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

            val loadBtn = DualDexComponents.secondaryButton(context, "Load") {
                performAsyncOperation("Slot ${slot.slotIndex} loaded!", "Load failed!") {
                    saveStateManager.loadSlot(identity, slot.slotIndex, profile.name, profile.id)
                }
            }.apply {
                isEnabled = slot.exists && !isBusy
            }
            slotRow.addView(loadBtn, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(36)).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.tight)
            })

            val saveBtn = DualDexComponents.secondaryButton(context, "Save") {
                performAsyncOperation("Slot ${slot.slotIndex} saved!", "Save failed!") {
                    saveStateManager.saveSlot(identity, slot.slotIndex)
                }
            }.apply {
                isEnabled = !isBusy
            }
            slotRow.addView(saveBtn, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(36)))

            slotsContainer.addView(slotRow, LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = if (index == slots.lastIndex) 0 else context.dp(DualDexTheme.Spacing.compact)
            })
        }
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
            }.apply {
                isEnabled = !isBusy
            }
            candRow.addView(migrateBtn, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(36)))

            legacyContainer.addView(candRow, LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = if (index == candidates.lastIndex) 0 else context.dp(DualDexTheme.Spacing.compact)
            })
        }
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
    }
}
