package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.SaveSlotInfo
import com.dualdex.emulator.SaveStateManager
import com.dualdex.emulator.storage.LegacyCandidate
import com.dualdex.emulator.storage.MirrorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SaveStateScreenView(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val onImportSaveRequested: (() -> Unit)? = null,
    private val onExportSaveRequested: (() -> Unit)? = null,
    private val onChooseSavesFolderRequested: (() -> Unit)? = null
) : LinearLayout(context) {

    private val saveStateManager = SaveStateManager.getInstance(context)
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private val slotsContainer: LinearLayout
    private val legacyCandidatesContainer: LinearLayout
    private val quickSaveStatusView: TextView
    private val batterySaveStatusView: TextView
    private val storageStatusView: TextView
    private val romIdentityView: TextView

    private val importBtn: Button
    private val exportBtn: Button
    private val qSaveBtn: Button
    private val qLoadBtn: Button
    private val migrateBtn: Button

    private var isBusy = false

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF121216.toInt())
        setPadding(24, 24, 24, 24)

        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = true }
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        scroll.addView(content)
        addView(scroll)

        // Title
        val titleView = TextView(context).apply {
            text = "💾 Save & Storage Manager"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        content.addView(titleView)

        // Active ROM Identity Card
        val identityCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "🎮 Active ROM Identity"
                setTextColor(0xFF4A9EFF.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 4)
            }
            addView(label)

            romIdentityView = TextView(context).apply {
                setTextColor(0xFFDDDDDD.toInt())
                textSize = 12.5f
                setLineSpacing(4f, 1f)
            }
            addView(romIdentityView)
        }
        content.addView(identityCard)

        // Storage Architecture Card (Canonical Internal + SAF Mirror)
        val storageCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "📁 Storage Architecture"
                setTextColor(0xFFFFD700.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 4)
            }
            addView(label)

            storageStatusView = TextView(context).apply {
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 10)
            }
            addView(storageStatusView)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 4, 0, 0)
            }

            val chooseFolderBtn = Button(context).apply {
                text = "📁 Select SAF Mirror"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0xFF2B4A77.toInt())
                }
                setPadding(14, 8, 14, 8)
                setOnClickListener {
                    onChooseSavesFolderRequested?.invoke()
                }
            }
            val lp1 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(0, 0, 6, 0) }
            btnRow.addView(chooseFolderBtn, lp1)

            migrateBtn = Button(context).apply {
                text = "🔄 Sync to SAF"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0xFF2E6B4A.toInt())
                }
                setPadding(14, 8, 14, 8)
                setOnClickListener {
                    val identity = getRomIdentity()
                    if (identity == null || !identity.isValid) return@setOnClickListener
                    setBusyState(true)
                    uiScope.launch(Dispatchers.IO) {
                        val count = saveStateManager.syncCanonicalToSaf(identity)
                        withContext(Dispatchers.Main) {
                            setBusyState(false)
                            Toast.makeText(context, if (count > 0) "Synced $count save file(s) to SAF mirror!" else "SAF mirror up-to-date", Toast.LENGTH_SHORT).show()
                            refreshUI()
                        }
                    }
                }
            }
            val lp2 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(6, 0, 0, 0) }
            btnRow.addView(migrateBtn, lp2)

            addView(btnRow)
        }
        content.addView(storageCard)

        // Cartridge Battery Save (.sav) Card
        val batteryCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "Cartridge Battery Save (.sav)"
                setTextColor(0xFF4A9EFF.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 4)
            }
            addView(label)

            val desc = TextView(context).apply {
                text = "Internal canonical store is primary. Import validates size and SRAM before committing."
                setTextColor(0xFF9999AA.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 10)
            }
            addView(desc)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 4, 0, 8)
            }

            importBtn = Button(context).apply {
                text = "📥 Import .sav"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(0xFF2E6B4A.toInt())
                }
                setPadding(16, 10, 16, 10)
                setOnClickListener {
                    onImportSaveRequested?.invoke()
                }
            }
            val lpImport = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(0, 0, 8, 0) }
            btnRow.addView(importBtn, lpImport)

            exportBtn = Button(context).apply {
                text = "📤 Export .sav"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(0xFF2B4A77.toInt())
                }
                setPadding(16, 10, 16, 10)
                setOnClickListener {
                    onExportSaveRequested?.invoke()
                }
            }
            val lpExport = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(8, 0, 0, 0) }
            btnRow.addView(exportBtn, lpExport)

            addView(btnRow)

            batterySaveStatusView = TextView(context).apply {
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }
            addView(batterySaveStatusView)
        }
        content.addView(batteryCard)

        // Quick Save / Load Card
        val quickCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "Quick Save & Quick Load"
                setTextColor(0xFFFFD700.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 8)
            }
            addView(label)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 8, 0, 8)
            }

            qSaveBtn = Button(context).apply {
                text = "⚡ Quick Save"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(0xFF2E6B4A.toInt())
                }
                setPadding(20, 10, 20, 10)
                setOnClickListener {
                    val identity = getRomIdentity() ?: return@setOnClickListener
                    setBusyState(true)
                    uiScope.launch(Dispatchers.IO) {
                        val ok = saveStateManager.quickSave(identity)
                        withContext(Dispatchers.Main) {
                            setBusyState(false)
                            Toast.makeText(context, if (ok) "Quick state saved!" else "Save failed!", Toast.LENGTH_SHORT).show()
                            refreshUI()
                        }
                    }
                }
            }
            val lp1 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(0, 0, 8, 0) }
            btnRow.addView(qSaveBtn, lp1)

            qLoadBtn = Button(context).apply {
                text = "📂 Quick Load"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(0xFF2B4A77.toInt())
                }
                setPadding(20, 10, 20, 10)
                setOnClickListener {
                    val identity = getRomIdentity() ?: return@setOnClickListener
                    setBusyState(true)
                    uiScope.launch(Dispatchers.IO) {
                        val ok = saveStateManager.quickLoad(identity)
                        withContext(Dispatchers.Main) {
                            setBusyState(false)
                            Toast.makeText(context, if (ok) "Quick state loaded!" else "No quick save found!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            val lp2 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(8, 0, 0, 0) }
            btnRow.addView(qLoadBtn, lp2)

            addView(btnRow)

            quickSaveStatusView = TextView(context).apply {
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }
            addView(quickSaveStatusView)
        }
        content.addView(quickCard)

        // Save Slots (1 - 5) Card
        val slotsCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "Save Slots (1 - 5)"
                setTextColor(0xFF4A9EFF.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 12)
            }
            addView(label)

            slotsContainer = LinearLayout(context).apply {
                orientation = VERTICAL
            }
            addView(slotsContainer)
        }
        content.addView(slotsCard)

        // Legacy Discovery & Migration Card
        val legacyCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "📦 Discovered Legacy Saves"
                setTextColor(0xFF50C878.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 4)
            }
            addView(label)

            val desc = TextView(context).apply {
                text = "Legacy saves from older DualDex releases. Migration requires explicit selection."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 10)
            }
            addView(desc)

            legacyCandidatesContainer = LinearLayout(context).apply {
                orientation = VERTICAL
            }
            addView(legacyCandidatesContainer)
        }
        content.addView(legacyCard)

        refreshUI()
    }

    private fun getRomIdentity(): RomIdentity? {
        return viewModel.activeRomIdentity.value?.takeIf { it.isValid }
    }

    private fun setBusyState(busy: Boolean) {
        isBusy = busy
        importBtn.isEnabled = !busy && getRomIdentity() != null
        exportBtn.isEnabled = !busy && getRomIdentity() != null
        qSaveBtn.isEnabled = !busy && getRomIdentity() != null
        qLoadBtn.isEnabled = !busy && getRomIdentity() != null
        migrateBtn.isEnabled = !busy && getRomIdentity() != null
    }

    fun refreshUI() {
        val identity = getRomIdentity()
        val profile = viewModel.activeProfile.value

        if (identity == null || !identity.isValid) {
            romIdentityView.text = "• Status: No active ROM loaded.\n• Actions disabled until a game is opened."
            storageStatusView.text = "Canonical Store: App-Private (${saveStateManager.getSaveDirectoryDescription()})\nShared Mirror: Inactive"
            batterySaveStatusView.text = "No active ROM loaded."
            quickSaveStatusView.text = "No active ROM loaded."

            importBtn.isEnabled = false
            exportBtn.isEnabled = false
            qSaveBtn.isEnabled = false
            qLoadBtn.isEnabled = false
            migrateBtn.isEnabled = false

            slotsContainer.removeAllViews()
            val noRomSlot = TextView(context).apply {
                text = "No active ROM loaded."
                setTextColor(0xFF777788.toInt())
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            slotsContainer.addView(noRomSlot)

            populateLegacyCandidates(null)
            return
        }

        setBusyState(isBusy)

        // Active ROM details
        val hashDisplay = identity.shortHash
        romIdentityView.text = "• Title: ${identity.displayName}\n• Full SHA-256: ${identity.sha256}\n• Canonical Path: saves_v2/${identity.storageKey}/"

        // Storage status and slot info loaded asynchronously off the main UI thread
        storageStatusView.text = "Checking storage status..."
        batterySaveStatusView.text = "Loading battery save status..."
        quickSaveStatusView.text = "Loading quick save status..."

        uiScope.launch(Dispatchers.IO) {
            val isSaf = saveStateManager.isUsingSaf()
            val mirrorStatus = saveStateManager.getSafMirrorStatus(identity)
            val dirDesc = saveStateManager.getSaveDirectoryDescription()

            val bInfo = saveStateManager.getBatterySaveInfo(identity, profile.name, profile.id)
            val qFile = saveStateManager.getCanonicalFile(identity, "quicksave.state")
            val qLastModified = if (qFile.exists()) qFile.lastModified() else 0L

            val slots = saveStateManager.getAllSlotsInfo(identity, 5, profile.name, profile.id)
            val candidates = saveStateManager.discoverLegacyCandidates()

            withContext(Dispatchers.Main) {
                storageStatusView.text = if (isSaf) {
                    "Canonical: App-Private Internal Storage\nShared Mirror: $dirDesc (Status: $mirrorStatus)"
                } else {
                    "Canonical: App-Private Internal Storage\nShared Mirror: None configured (Select SAF Mirror to enable external access)"
                }

                batterySaveStatusView.text = if (bInfo.exists) {
                    "Active Battery Save: ${bInfo.sizeBytes / 1024} KB (Saved: ${bInfo.formattedDate})"
                } else {
                    "No .sav file found on disk (auto-saves on in-game save / pause)."
                }

                quickSaveStatusView.text = if (qLastModified > 0L) {
                    "Latest Quick Save: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(qLastModified))}"
                } else {
                    "No Quick Save created yet."
                }

                renderSlots(slots, identity, profile)
                renderLegacyCandidates(candidates, identity)
            }
        }
    }

    private fun renderSlots(slots: List<SaveSlotInfo>, identity: RomIdentity, profile: com.dualdex.romhack.RomHackProfile) {
        slotsContainer.removeAllViews()
        slots.forEach { slot ->
            val slotRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 12, 12, 12)
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0xFF16161E.toInt())
                }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 10)
                }
            }

            val slotText = TextView(context).apply {
                val sizeStr = if (slot.exists) " (${slot.sizeBytes / 1024} KB)" else ""
                text = "Slot ${slot.slotIndex}: ${slot.formattedDate}$sizeStr"
                setTextColor(if (slot.exists) Color.WHITE else 0xFF777788.toInt())
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
            }
            slotRow.addView(slotText)

            val saveBtn = Button(context).apply {
                text = "Save"
                textSize = 11f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(0xFF2E6B4A.toInt())
                }
                setPadding(14, 4, 14, 4)
                setOnClickListener {
                    setBusyState(true)
                    uiScope.launch(Dispatchers.IO) {
                        val ok = saveStateManager.saveSlot(identity, slot.slotIndex)
                        withContext(Dispatchers.Main) {
                            setBusyState(false)
                            Toast.makeText(context, if (ok) "Slot ${slot.slotIndex} saved!" else "Save failed!", Toast.LENGTH_SHORT).show()
                            refreshUI()
                        }
                    }
                }
            }
            val lpSave = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 8, 0)
            }
            slotRow.addView(saveBtn, lpSave)

            val loadBtn = Button(context).apply {
                text = "Load"
                textSize = 11f
                setTextColor(Color.WHITE)
                isEnabled = slot.exists && !isBusy
                background = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(if (slot.exists) 0xFF2B4A77.toInt() else 0xFF222228.toInt())
                }
                setPadding(14, 4, 14, 4)
                setOnClickListener {
                    setBusyState(true)
                    uiScope.launch(Dispatchers.IO) {
                        val ok = saveStateManager.loadSlot(identity, slot.slotIndex, profile.name, profile.id)
                        withContext(Dispatchers.Main) {
                            setBusyState(false)
                            Toast.makeText(context, if (ok) "Slot ${slot.slotIndex} loaded!" else "Load failed!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            slotRow.addView(loadBtn)

            slotsContainer.addView(slotRow)
        }
    }

    private fun populateLegacyCandidates(identity: RomIdentity?) {
        if (identity == null || !identity.isValid) {
            renderLegacyCandidates(emptyList(), null)
            return
        }
        uiScope.launch(Dispatchers.IO) {
            val candidates = saveStateManager.discoverLegacyCandidates()
            withContext(Dispatchers.Main) {
                renderLegacyCandidates(candidates, identity)
            }
        }
    }

    private fun renderLegacyCandidates(candidates: List<LegacyCandidate>, identity: RomIdentity?) {
        legacyCandidatesContainer.removeAllViews()

        if (candidates.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = "No unmigrated legacy saves found."
                setTextColor(0xFF888899.toInt())
                textSize = 12f
                setPadding(0, 4, 0, 4)
            }
            legacyCandidatesContainer.addView(emptyText)
            return
        }

        candidates.forEach { cand ->
            val candRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 10, 12, 10)
                background = GradientDrawable().apply {
                    cornerRadius = 12f
                    setColor(0xFF181822.toInt())
                }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 8)
                }
            }

            val infoText = TextView(context).apply {
                val sizeKb = cand.sizeBytes / 1024
                text = "${cand.sourceFile.name}\n${cand.suggestedTitle} (${sizeKb} KB)"
                setTextColor(Color.WHITE)
                textSize = 12f
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
            }
            candRow.addView(infoText)

            if (identity != null && identity.isValid) {
                val migrateBtn = Button(context).apply {
                    text = "Migrate"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = 8f
                        setColor(0xFF2E6B4A.toInt())
                    }
                    setPadding(12, 4, 12, 4)
                    setOnClickListener {
                        setBusyState(true)
                        uiScope.launch(Dispatchers.IO) {
                            val res = saveStateManager.assignLegacyCandidate(cand, identity)
                            withContext(Dispatchers.Main) {
                                setBusyState(false)
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Migrated ${cand.sourceFile.name} to ${identity.displayName}!", Toast.LENGTH_SHORT).show()
                                    refreshUI()
                                } else {
                                    Toast.makeText(context, "Migration failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                candRow.addView(migrateBtn)
            }

            legacyCandidatesContainer.addView(candRow)
        }
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(20, 18, 20, 18)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(0xFF1E1E26.toInt())
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
        }
    }
}
