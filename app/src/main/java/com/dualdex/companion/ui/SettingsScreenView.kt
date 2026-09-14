package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dualdex.assistant.RomHackAssistant
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.ShaderFilter
import com.dualdex.settings.SettingsManager

/**
 * Redesigned Settings screen adhering to the Quiet Handheld Companion design system.
 * Groups settings logically (Display, Emulation, Save Storage, Assistant, Preferences,
 * Controls, and About/Diagnostics) using neutral surfaces, segmented controls, and
 * density-independent spacing.
 */
class SettingsScreenView(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val onShaderChanged: ((ShaderFilter) -> Unit)? = null,
    private val onSpeedChanged: ((Int) -> Unit)? = null,
    private val onStretchChanged: ((Boolean) -> Unit)? = null,
    private val onTabSelected: ((com.dualdex.companion.CompanionTab) -> Unit)? = null,
    private val onChooseSavesFolderRequested: (() -> Unit)? = null
) : LinearLayout(context) {

    private val settingsManager = SettingsManager(context)
    private val apiKeyInput: EditText

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
            DualDexComponents.screenTitle(context, "Settings"),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(DualDexTheme.Spacing.section)
            }
        )

        // 2. Display Section
        val displayCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "Display"))

            // Shader Filter
            val filterLabel = TextView(context).apply {
                text = "Shader Filter"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.tight))
            }
            addView(filterLabel)

            val filters = ShaderFilter.values()
            val filterNames = filters.map { filter ->
                when (filter) {
                    ShaderFilter.NEAREST -> "Nearest"
                    ShaderFilter.SHARP_BILINEAR -> "Bilinear"
                    ShaderFilter.LCD_GRID -> "LCD Grid"
                    ShaderFilter.CRT_SCANLINE -> "Scanlines"
                }
            }
            val curFilter = settingsManager.shaderFilter
            val initialFilterIdx = filters.indexOf(curFilter).coerceAtLeast(0)

            val shaderDescView = TextView(context).apply {
                text = "${curFilter.displayName} · ${curFilter.description}"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.compact
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.standard))
            }

            val shaderSegment = DualDexComponents.segmentedControl(
                context = context,
                items = filterNames,
                initialIndex = initialFilterIdx,
                onItemSelected = { idx ->
                    val chosen = filters[idx]
                    settingsManager.shaderFilter = chosen
                    onShaderChanged?.invoke(chosen)
                    shaderDescView.text = "${chosen.displayName} · ${chosen.description}"
                }
            )
            addView(shaderSegment)
            addView(shaderDescView)

            // Aspect Ratio / Scaling
            val aspectLabel = TextView(context).apply {
                text = "Aspect Ratio"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.tight))
            }
            addView(aspectLabel)

            val isStretch = settingsManager.isStretchToFitEnabled
            val aspectSegment = DualDexComponents.segmentedControl(
                context = context,
                items = listOf("3:2 Standard (Letterbox)", "Stretch to Fill"),
                initialIndex = if (isStretch) 1 else 0,
                onItemSelected = { idx ->
                    val stretch = (idx == 1)
                    settingsManager.isStretchToFitEnabled = stretch
                    onStretchChanged?.invoke(stretch)
                }
            )
            addView(aspectSegment)
        }
        content.addView(displayCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 3. Emulation Section
        val emulationCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "Emulation"))

            val speedLabel = TextView(context).apply {
                text = "Fast-Forward Speed"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.tight))
            }
            addView(speedLabel)

            val speeds = listOf(1, 2, 3, 4)
            val speedLabels = listOf("1x Normal", "2x Fast", "3x Turbo", "4x Max")
            val currentSpeed = settingsManager.fastForwardMultiplier
            val initialSpeedIdx = speeds.indexOf(currentSpeed).coerceIn(0, speeds.size - 1)

            val speedSegment = DualDexComponents.segmentedControl(
                context = context,
                items = speedLabels,
                initialIndex = initialSpeedIdx,
                onItemSelected = { idx ->
                    val chosen = speeds[idx]
                    settingsManager.fastForwardMultiplier = chosen
                    onSpeedChanged?.invoke(chosen)
                }
            )
            addView(speedSegment)
        }
        content.addView(emulationCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 4. Save Storage Section
        val storageCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "Save Storage"))

            val desc = TextView(context).apply {
                text = "DualDex saves directly to private internal storage. You can optionally mirror saves to a shared folder (such as Documents) to sync saves with PC or other devices."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(desc)

            val storageDescView = TextView(context).apply {
                val current = settingsManager.savesFolderUri
                text = if (!current.isNullOrBlank()) "Active Location: Custom Shared Folder" else "Active Location: Internal App Storage (Private)"
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.standard))
            }
            addView(storageDescView)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                val pickFolderBtn = DualDexComponents.secondaryButton(context, "Choose Folder") {
                    onChooseSavesFolderRequested?.invoke()
                }
                addView(pickFolderBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                })

                val resetBtn = DualDexComponents.ghostControl(context, "Reset to Internal") {
                    settingsManager.savesFolderUri = null
                    storageDescView.text = "Active Location: Internal App Storage (Private)"
                    Toast.makeText(context, "Reset saves storage to internal app storage", Toast.LENGTH_SHORT).show()
                }
                addView(resetBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f))
            }
            addView(btnRow)
        }
        content.addView(storageCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 5. Assistant Section (AI)
        val assistantCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "Assistant"))

            val desc = TextView(context).apply {
                text = "Optional Google Gemini integration for walkthrough questions and ROM hack grounding."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(desc)

            apiKeyInput = DualDexComponents.styledInput(context, "Gemini API Key (AIzaSy...)").apply {
                setText(settingsManager.geminiApiKey.orEmpty())
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            addView(apiKeyInput, LayoutParams(
                LayoutParams.MATCH_PARENT,
                context.dp(DualDexTheme.Spacing.touchTarget)
            ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })

            val keyBtnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                val saveKeyBtn = DualDexComponents.primaryButton(context, "Save Key") {
                    val key = apiKeyInput.text.toString().trim()
                    settingsManager.geminiApiKey = if (key.isNotEmpty()) key else null
                    RomHackAssistant.setApiKey(settingsManager.geminiApiKey)
                    Toast.makeText(context, if (key.isNotEmpty()) "Gemini API key saved" else "API key cleared", Toast.LENGTH_SHORT).show()
                }
                addView(saveKeyBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                })

                val clearKeyBtn = DualDexComponents.ghostControl(context, "Clear Key") {
                    apiKeyInput.setText("")
                    settingsManager.geminiApiKey = null
                    RomHackAssistant.setApiKey(null)
                    Toast.makeText(context, "API key cleared", Toast.LENGTH_SHORT).show()
                }
                addView(clearKeyBtn, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f))
            }
            addView(keyBtnRow)

            val modelLabel = TextView(context).apply {
                text = "Active AI Model"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.standard), 0, context.dp(DualDexTheme.Spacing.tight))
            }
            addView(modelLabel)

            val models = listOf("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.0-flash")
            val modelLabels = listOf("Flash 3.8", "Flash 2.5", "Flash 2.0")
            val currentModel = settingsManager.geminiModel
            val initialModelIdx = models.indexOf(currentModel).coerceAtLeast(0)

            val modelSegment = DualDexComponents.segmentedControl(
                context = context,
                items = modelLabels,
                initialIndex = initialModelIdx,
                onItemSelected = { idx ->
                    val chosen = models[idx]
                    settingsManager.geminiModel = chosen
                    RomHackAssistant.setModel(chosen)
                    Toast.makeText(context, "Set model to ${modelLabels[idx]}", Toast.LENGTH_SHORT).show()
                }
            )
            addView(modelSegment)
        }
        content.addView(assistantCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 6. Preferences & Experimental Section
        val preferencesCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "Preferences"))

            // Battle Console Auto-Open
            val autoOpenLabel = TextView(context).apply {
                text = "Auto-Open Battle Console on Battle Start"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.tight))
            }
            addView(autoOpenLabel)

            val isAutoOpen = settingsManager.isBattleAutoOpenEnabled
            val autoOpenSegment = DualDexComponents.segmentedControl(
                context = context,
                items = listOf("Off", "On"),
                initialIndex = if (isAutoOpen) 1 else 0,
                onItemSelected = { idx ->
                    val enabled = (idx == 1)
                    settingsManager.isBattleAutoOpenEnabled = enabled
                    viewModel.setBattleAutoOpenEnabled(enabled)
                    Toast.makeText(
                        context,
                        if (enabled) "Battle Console will open when battle starts" else "Auto-open disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            addView(autoOpenSegment)

            // Interactive Touch Controls
            val touchControlsLabel = TextView(context).apply {
                text = "Interactive Touch Controls (Experimental)"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.standard), 0, context.dp(DualDexTheme.Spacing.tight))
            }
            addView(touchControlsLabel)

            val isInteractive = settingsManager.isInteractiveBattleControlsEnabled
            val touchSegment = DualDexComponents.segmentedControl(
                context = context,
                items = listOf("Read-Only (Default)", "Allowed"),
                initialIndex = if (isInteractive) 1 else 0,
                onItemSelected = { idx ->
                    val enabled = (idx == 1)
                    settingsManager.isInteractiveBattleControlsEnabled = enabled
                    viewModel.setInteractiveBattleControlsEnabled(enabled)
                    Toast.makeText(
                        context,
                        if (enabled) "Verified touch controls allowed when supported" else "Touch controls set to read-only",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            addView(touchSegment)

            val touchDisclaimer = TextView(context).apply {
                text = "Touch controls require exact ROM profiles with verified cursor and party menu readers. Safe read-only mode is active for all standard profiles."
                setTextColor(DualDexTheme.Color.textDisabled)
                textSize = DualDexTheme.Type.compact
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
            }
            addView(touchDisclaimer)
        }
        content.addView(preferencesCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 7. Controls Reference Section (AYN Thor Hardware Mapping)
        val controlsCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "Controls"))

            val subtitle = TextView(context).apply {
                text = "AYN Thor physical hardware controller mapping."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(subtitle)

            val mappingRows = listOf(
                "D-Pad / Left Stick" to "GBA Directional Movement",
                "Button A / B" to "A: Confirm / B: Cancel or Run",
                "Button X / Y" to "Turbo / Menu Shortcut",
                "L1 / R1" to "GBA Left / Right Triggers",
                "L2 / R2" to "Quick Save (L2) / Quick Load (R2)",
                "Start / Select" to "GBA Start / Select Buttons"
            )

            mappingRows.forEachIndexed { index, (key, value) ->
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = context.dp(28)
                    val keyTv = TextView(context).apply {
                        text = key
                        setTextColor(DualDexTheme.Color.textPrimary)
                        textSize = DualDexTheme.Type.body
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val valTv = TextView(context).apply {
                        text = value
                        setTextColor(DualDexTheme.Color.textSecondary)
                        textSize = DualDexTheme.Type.body
                        gravity = Gravity.END
                    }
                    addView(keyTv, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.1f))
                    addView(valTv, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.4f))
                }
                addView(row)
                if (index < mappingRows.lastIndex) {
                    addView(DualDexComponents.divider(context), LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        context.dp(1)
                    ).apply {
                        topMargin = context.dp(DualDexTheme.Spacing.tight)
                        bottomMargin = context.dp(DualDexTheme.Spacing.tight)
                    })
                }
            }
        }
        content.addView(controlsCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // 8. About & Diagnostics Section
        val aboutCard = DualDexComponents.surfaceCard(context, elevated = false).apply {
            addView(DualDexComponents.sectionTitle(context, "About & Diagnostics"))

            val versionTv = TextView(context).apply {
                text = "DualDex 0.9.0-beta.1 · Handheld Edition"
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, 0)
            }
            addView(versionTv)

            val diagnosticsTv = TextView(context).apply {
                text = "Target: 59.7 FPS · EWRAM Poller: 10 Hz (<0.05 ms latency) · Audio: 32,768 Hz stereo PCM"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(diagnosticsTv)

            val noticesTv = TextView(context).apply {
                text = "Core: mGBA (MPL-2.0) · Calculator: @smogon/calc via QuickJS-NG\nNot affiliated with Nintendo, The Pokémon Company, or Game Freak."
                setTextColor(DualDexTheme.Color.textDisabled)
                textSize = DualDexTheme.Type.compact
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
            }
            addView(noticesTv)
        }
        content.addView(aboutCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}
