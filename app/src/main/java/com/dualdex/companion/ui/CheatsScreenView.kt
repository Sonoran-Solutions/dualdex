package com.dualdex.companion.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dualdex.cheats.CheatItem
import com.dualdex.cheats.CheatManager
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.RomIdentity

/**
 * Redesigned Cheats screen adhering to the Quiet Handheld Companion design system.
 * Organizes cheats as flat, scannable list rows with inline enable/disable toggles,
 * tap-to-expand monospaced code details, compact toolbar actions, and clean empty states.
 */
class CheatsScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : LinearLayout(context) {

    private val cheatManager = CheatManager(context)
    private val cheatsListContainer: LinearLayout
    private val activeRomLabel: TextView
    private val addBtn: TextView
    private val presetBtn: TextView
    private val disableAllBtn: TextView

    // Track which cheat rows are currently expanded
    private val expandedCheatIds = mutableSetOf<String>()

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
        val mainContent = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.major))
        }
        scroll.addView(mainContent)
        addView(scroll)

        // 1. Header
        val headerCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.screenTitle(context, "Cheats"))

            activeRomLabel = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight / 2), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(activeRomLabel)

            val descView = TextView(context).apply {
                text = "Supports Action Replay v3, GameShark, and CodeBreaker codes. Injected directly into the core and scoped strictly to this game."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.standard))
            }
            addView(descView)

            // Toolbar: Add Cheat, Load Presets, Disable All
            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
            }

            addBtn = DualDexComponents.secondaryButton(context, "Add Cheat") {
                showAddCheatDialog()
            }
            val lpAdd = LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1.0f).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.compact)
            }
            btnRow.addView(addBtn, lpAdd)

            presetBtn = DualDexComponents.secondaryButton(context, "Load Presets") {
                val identity = getActiveRomIdentity()
                if (identity != null && identity.isValid) {
                    cheatManager.resetToDefaultPresets(identity)
                    refreshUI()
                    Toast.makeText(context, "Loaded presets for ${identity.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
            val lpPreset = LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1.0f).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.compact)
            }
            btnRow.addView(presetBtn, lpPreset)

            disableAllBtn = DualDexComponents.ghostControl(context, "Disable All") {
                val identity = getActiveRomIdentity()
                if (identity != null && identity.isValid) {
                    val cheats = cheatManager.getCheats(identity)
                    val updated = cheats.map { it.copy(enabled = false) }
                    cheatManager.saveCheats(identity, updated)
                    cheatManager.applyCheats(identity)
                    refreshUI()
                    Toast.makeText(context, "All cheats disabled", Toast.LENGTH_SHORT).show()
                }
            }
            val lpDisable = LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1.0f)
            btnRow.addView(disableAllBtn, lpDisable)

            addView(btnRow)
        }
        mainContent.addView(headerCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 2. Cheats List Container
        cheatsListContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        mainContent.addView(cheatsListContainer)

        refreshUI()
    }

    private fun getActiveRomIdentity(): RomIdentity? = viewModel.activeRomIdentity.value?.takeIf { it.isValid }

    fun refreshUI() {
        val identity = getActiveRomIdentity()
        cheatsListContainer.removeAllViews()

        if (identity == null || !identity.isValid) {
            activeRomLabel.text = "No active game loaded"
            addBtn.isEnabled = false
            presetBtn.isEnabled = false
            disableAllBtn.isEnabled = false

            cheatsListContainer.addView(
                DualDexComponents.emptyState(
                    context,
                    "No game loaded",
                    "Open a game from your Library to view and configure cheats."
                )
            )
            return
        }

        activeRomLabel.text = "Game: ${identity.displayName} (${identity.shortHash})"
        addBtn.isEnabled = true
        presetBtn.isEnabled = true
        disableAllBtn.isEnabled = true

        val cheats = cheatManager.getCheats(identity)
        if (cheats.isEmpty()) {
            cheatsListContainer.addView(
                DualDexComponents.emptyState(
                    context,
                    "No cheats configured",
                    "Tap 'Load Presets' to get standard codes for this game, or 'Add Cheat' to enter custom codes."
                )
            )
            return
        }

        cheats.forEach { cheat ->
            val isExpanded = expandedCheatIds.contains(cheat.id)

            val cheatCard = LinearLayout(context).apply {
                orientation = VERTICAL
                background = DualDexComponents.surface(context, elevated = false)
                setPadding(
                    context.dp(DualDexTheme.Spacing.standard),
                    context.dp(DualDexTheme.Spacing.compact),
                    context.dp(DualDexTheme.Spacing.standard),
                    context.dp(DualDexTheme.Spacing.compact)
                )
            }

            // Clickable Header Row
            val titleRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (isExpanded) expandedCheatIds.remove(cheat.id) else expandedCheatIds.add(cheat.id)
                    refreshUI()
                }
            }

            val nameView = TextView(context).apply {
                text = cheat.name
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                typeface = Typeface.DEFAULT_BOLD
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
            }
            titleRow.addView(nameView)

            if (cheat.isPreset) {
                val presetBadge = TextView(context).apply {
                    text = "PRESET"
                    textSize = DualDexTheme.Type.compact
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(DualDexTheme.Color.success)
                    setPadding(
                        context.dp(DualDexTheme.Spacing.compact),
                        context.dp(DualDexTheme.Spacing.tight / 2),
                        context.dp(DualDexTheme.Spacing.compact),
                        context.dp(DualDexTheme.Spacing.tight / 2)
                    )
                    background = DualDexComponents.roundedDrawable(
                        context = context,
                        color = DualDexTheme.Color.surfaceDisabled,
                        radiusDp = DualDexTheme.Radius.pill,
                        strokeColor = DualDexTheme.Color.border
                    )
                }
                val lpBadge = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                }
                titleRow.addView(presetBadge, lpBadge)
            }

            // Quick Toggle Button
            val toggleBtn = DualDexComponents.smallButton(
                context = context,
                text = if (cheat.enabled) "Active" else "Off",
                style = if (cheat.enabled) DualDexButtonStyle.PRIMARY else DualDexButtonStyle.GHOST
            ) {
                val newState = !cheat.enabled
                cheatManager.toggleCheat(identity, cheat.id, newState)
                refreshUI()
                Toast.makeText(context, "${cheat.name}: ${if (newState) "Active" else "Off"}", Toast.LENGTH_SHORT).show()
            }
            val lpToggle = LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30)).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.compact)
            }
            titleRow.addView(toggleBtn, lpToggle)

            val chevron = TextView(context).apply {
                text = if (isExpanded) "▼" else "›"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = if (isExpanded) DualDexTheme.Type.compact else DualDexTheme.Type.chevron
                gravity = Gravity.CENTER
            }
            titleRow.addView(chevron)
            cheatCard.addView(titleRow)

            // Expandable Content (Code and Delete)
            if (isExpanded) {
                val detailsLayout = LinearLayout(context).apply {
                    orientation = VERTICAL
                    setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.tight))
                }

                // Monospaced code snippet view
                val codeView = TextView(context).apply {
                    text = cheat.code
                    setTextColor(DualDexTheme.Color.textSecondary)
                    textSize = DualDexTheme.Type.compact
                    typeface = Typeface.MONOSPACE
                    setPadding(
                        context.dp(DualDexTheme.Spacing.standard),
                        context.dp(DualDexTheme.Spacing.compact),
                        context.dp(DualDexTheme.Spacing.standard),
                        context.dp(DualDexTheme.Spacing.compact)
                    )
                    background = DualDexComponents.roundedDrawable(
                        context = context,
                        color = DualDexTheme.Color.surfaceDisabled,
                        radiusDp = DualDexTheme.Radius.control
                    )
                }
                detailsLayout.addView(codeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = context.dp(DualDexTheme.Spacing.compact)
                })

                // Action Row (Delete button)
                val actionRow = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.END
                }
                val deleteBtn = DualDexComponents.smallButton(
                    context = context,
                    text = "Delete",
                    style = DualDexButtonStyle.DESTRUCTIVE
                ) {
                    cheatManager.deleteCheat(identity, cheat.id)
                    expandedCheatIds.remove(cheat.id)
                    refreshUI()
                }
                actionRow.addView(deleteBtn)
                detailsLayout.addView(actionRow)

                cheatCard.addView(detailsLayout)
            }

            cheatsListContainer.addView(cheatCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(DualDexTheme.Spacing.compact)
            })
        }
    }

    private fun showAddCheatDialog() {
        val identity = getActiveRomIdentity() ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Add Cheat Code")

        val dialogContent = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.compact)
            )
        }

        val nameLabel = TextView(context).apply {
            text = "Cheat Name:"
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
        }
        dialogContent.addView(nameLabel)

        val nameInput = DualDexComponents.styledInput(context, "e.g. 999 Rare Candies")
        dialogContent.addView(nameInput, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.compact)
        })

        val codeLabel = TextView(context).apply {
            text = "Cheat Code:"
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
        }
        dialogContent.addView(codeLabel)

        val codeInput = EditText(context).apply {
            hint = "XXXXXXXX XXXXXXXX\nYYYYYYYY YYYYYYYY"
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.MONOSPACE
            minLines = 4
            setTextColor(DualDexTheme.Color.textPrimary)
            setHintTextColor(DualDexTheme.Color.textDisabled)
            background = DualDexComponents.controlBackground(context, DualDexButtonStyle.SECONDARY, selected = false)
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact)
            )
        }
        dialogContent.addView(codeInput, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.compact)
        })

        val helperText = TextView(context).apply {
            text = "Enter standard Action Replay v3 (16 hex chars per line) or CodeBreaker (8+4 hex chars). Multiple lines supported."
            setTextColor(DualDexTheme.Color.textDisabled)
            textSize = DualDexTheme.Type.compact
        }
        dialogContent.addView(helperText)

        builder.setView(dialogContent)

        builder.setPositiveButton("Add & Enable") { _, _ ->
            val name = nameInput.text.toString().trim()
            val code = codeInput.text.toString().trim()
            if (code.isNotBlank()) {
                val cheat = CheatItem(
                    name = if (name.isNotBlank()) name else "Custom Cheat",
                    code = code,
                    enabled = true,
                    isPreset = false
                )
                cheatManager.addCheat(identity, cheat)
                refreshUI()
                Toast.makeText(context, "Added cheat: ${cheat.name}", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
}
