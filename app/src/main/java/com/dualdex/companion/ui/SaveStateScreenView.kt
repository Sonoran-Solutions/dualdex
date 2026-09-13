package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.SaveStateManager

class SaveStateScreenView(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val onImportSaveRequested: (() -> Unit)? = null,
    private val onExportSaveRequested: (() -> Unit)? = null,
    private val onChooseSavesFolderRequested: (() -> Unit)? = null
) : LinearLayout(context) {

    private val saveStateManager = SaveStateManager(context)
    private val slotsContainer: LinearLayout
    private val quickSaveStatusView: TextView
    private val batterySaveStatusView: TextView
    private val storageStatusView: TextView
    private val romIdentityView: TextView

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
            text = "💾 Save & Migration Manager"
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

        // Storage Location Card (SAF / Fallback)
        val storageCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "📁 Save Storage Location"
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
                text = "📁 Choose Saves Folder (SAF)"
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

            val migrateBtn = Button(context).apply {
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
                    val count = saveStateManager.migrateFallbackToSaf()
                    Toast.makeText(context, if (count > 0) "Migrated $count saves to SAF folder!" else "Saves are up-to-date", Toast.LENGTH_SHORT).show()
                    refreshUI()
                }
            }
            val lp2 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(6, 0, 0, 0) }
            btnRow.addView(migrateBtn, lp2)

            addView(btnRow)
        }
        content.addView(storageCard)

        // Cartridge Battery Save (.sav) Migration Card (My Boy! & GBA)
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
                text = "Import your in-progress playthrough from My Boy! or export to PC/other emulators."
                setTextColor(0xFF9999AA.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 10)
            }
            addView(desc)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 4, 0, 8)
            }

            val importBtn = Button(context).apply {
                text = "📥 Import .sav (My Boy!)"
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
            val lp1 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(0, 0, 8, 0) }
            btnRow.addView(importBtn, lp1)

            val exportBtn = Button(context).apply {
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
            val lp2 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(8, 0, 0, 0) }
            btnRow.addView(exportBtn, lp2)

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

            val qSaveBtn = Button(context).apply {
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
                    val identity = getRomIdentity()
                    val ok = saveStateManager.quickSave(identity)
                    Toast.makeText(context, if (ok) "Quick state saved!" else "Save failed!", Toast.LENGTH_SHORT).show()
                    refreshUI()
                }
            }
            val lp1 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(0, 0, 8, 0) }
            btnRow.addView(qSaveBtn, lp1)

            val qLoadBtn = Button(context).apply {
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
                    val identity = getRomIdentity()
                    val ok = saveStateManager.quickLoad(identity)
                    Toast.makeText(context, if (ok) "Quick state loaded!" else "No quick save found!", Toast.LENGTH_SHORT).show()
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

        // Slots Card
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

        refreshUI()
    }

    private fun getRomIdentity(): RomIdentity {
        return viewModel.activeRomIdentity.value ?: RomIdentity.create("", viewModel.activeRomTitle.value.ifEmpty { "default_game" })
    }

    fun refreshUI() {
        val identity = getRomIdentity()

        // Active ROM details
        val hashDisplay = if (identity.sha256.isNotEmpty()) identity.shortHash else "N/A"
        romIdentityView.text = "• Title: ${identity.displayName}\n• ROM Hash: $hashDisplay\n• Save Folder: ${identity.storageKey}/"

        // Storage status
        val isSaf = saveStateManager.isUsingSaf()
        val dirDesc = saveStateManager.getSaveDirectoryDescription()
        storageStatusView.text = if (isSaf) {
            "Status: Active via $dirDesc\nUser-visible shared storage enabled."
        } else {
            "Status: App Private Storage ($dirDesc)\nTip: Select a shared folder so your saves are easily accessible to other emulators or sync tools."
        }

        val bInfo = saveStateManager.getBatterySaveInfo(identity)
        batterySaveStatusView.text = if (bInfo.exists) {
            "Active Battery Save: ${bInfo.sizeBytes / 1024} KB (Saved: ${bInfo.formattedDate})"
        } else {
            "No .sav file found on disk (auto-saves on in-game save / pause)."
        }

        val qFile = saveStateManager.getStagingFile(identity, "quicksave.state")
        quickSaveStatusView.text = if (qFile.exists()) {
            "Latest Quick Save: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(qFile.lastModified()))}"
        } else {
            "No Quick Save created yet."
        }

        slotsContainer.removeAllViews()
        val slots = saveStateManager.getAllSlotsInfo(identity, 5)

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
                    val ok = saveStateManager.saveSlot(identity, slot.slotIndex)
                    Toast.makeText(context, if (ok) "Slot ${slot.slotIndex} saved!" else "Save failed!", Toast.LENGTH_SHORT).show()
                    refreshUI()
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
                isEnabled = slot.exists
                background = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(if (slot.exists) 0xFF2B4A77.toInt() else 0xFF222228.toInt())
                }
                setPadding(14, 4, 14, 4)
                setOnClickListener {
                    val ok = saveStateManager.loadSlot(identity, slot.slotIndex)
                    Toast.makeText(context, if (ok) "Slot ${slot.slotIndex} loaded!" else "Load failed!", Toast.LENGTH_SHORT).show()
                }
            }
            slotRow.addView(loadBtn)

            slotsContainer.addView(slotRow)
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
