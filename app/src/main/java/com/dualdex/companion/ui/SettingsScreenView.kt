package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import com.dualdex.assistant.RomHackAssistant
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.ShaderFilter
import com.dualdex.settings.SettingsManager

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
    private val shaderDescView = TextView(context).apply {
        setTextColor(0xFFAAAAAA.toInt())
        textSize = 12f
        setPadding(0, 2, 0, 0)
    }
    private val apiKeyInput: EditText

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF121216.toInt())
        setPadding(20, 20, 20, 20)

        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = true }
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        scroll.addView(content)
        addView(scroll)

        // Title
        val titleView = TextView(context).apply {
            text = "⚙️ DualDex Settings & Configuration"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        content.addView(titleView)

        // 1. Display & Retro Shaders Card
        val shaderCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "Retro Display Shaders & Scaling"
                setTextColor(0xFF4A9EFF.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 8)
            }
            addView(label)

            val shaderRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 4, 0, 8)
            }

            val curFilter = settingsManager.shaderFilter
            ShaderFilter.values().forEach { filter ->
                val btn = Button(context).apply {
                    text = filter.displayName.split(" ")[0] // short name
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = 12f
                        setColor(if (filter == curFilter) 0xFF4A9EFF.toInt() else 0xFF282834.toInt())
                    }
                    setPadding(12, 6, 12, 6)
                    setOnClickListener {
                        settingsManager.shaderFilter = filter
                        onShaderChanged?.invoke(filter)
                        updateShaderButtons(shaderRow, filter)
                        shaderDescView.text = "${filter.displayName}: ${filter.description}"
                    }
                }
                val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(0, 0, 6, 0)
                }
                shaderRow.addView(btn, lp)
            }
            addView(shaderRow)

            shaderDescView.text = "${curFilter.displayName}: ${curFilter.description}"
            addView(shaderDescView)

            // Stretch to fill screen toggle (My Boy! mode)
            val isStretch = settingsManager.isStretchToFitEnabled
            val stretchBtn = Button(context).apply {
                text = if (isStretch) "📱 Aspect Ratio: Stretch to Fill Screen" else "📺 Aspect Ratio: 3:2 Standard (Letterbox)"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 12f
                    setColor(if (isStretch) 0xFF2E6B4A.toInt() else 0xFF2B3A55.toInt())
                }
                setPadding(14, 10, 14, 10)
                setOnClickListener {
                    val newState = !settingsManager.isStretchToFitEnabled
                    settingsManager.isStretchToFitEnabled = newState
                    onStretchChanged?.invoke(newState)
                    text = if (newState) "📱 Aspect Ratio: Stretch to Fill Screen" else "📺 Aspect Ratio: 3:2 Standard (Letterbox)"
                    (background as? GradientDrawable)?.setColor(if (newState) 0xFF2E6B4A.toInt() else 0xFF2B3A55.toInt())
                }
            }
            val lpStretch = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 10, 0, 0)
            }
            addView(stretchBtn, lpStretch)
        }
        content.addView(shaderCard)

        // 2. Emulation Speed & Fast-Forward Card
        val speedCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "Emulation Speed & Fast-Forward"
                setTextColor(0xFFFFD700.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 8)
            }
            addView(label)

            val speedRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 4, 0, 8)
            }

            listOf(1 to "1x Normal", 2 to "2x Fast", 3 to "3x Turbo", 4 to "4x Max").forEach { (speed, speedTitle) ->
                val btn = Button(context).apply {
                    text = speedTitle
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = 12f
                        setColor(if (speed == settingsManager.fastForwardMultiplier) 0xFF4A9EFF.toInt() else 0xFF282834.toInt())
                    }
                    setPadding(10, 6, 10, 6)
                    setOnClickListener {
                        settingsManager.fastForwardMultiplier = speed
                        onSpeedChanged?.invoke(speed)
                        updateSpeedButtons(speedRow, speed)
                    }
                }
                val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(0, 0, 6, 0)
                }
                speedRow.addView(btn, lp)
            }
            addView(speedRow)

            val perfInfo = TextView(context).apply {
                text = "Performance: 59.7 FPS target | EWRAM Poller: 10Hz (latency <0.05ms) | Audio: 32768Hz"
                setTextColor(0xFF50C878.toInt())
                textSize = 11.5f
                setPadding(0, 4, 0, 0)
            }
            addView(perfInfo)
        }
        content.addView(speedCard)

        // 3. AYN Thor Physical Controls Mapping Card
        val controlsCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "AYN Thor Controller Mapping"
                setTextColor(0xFF50C878.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 8)
            }
            addView(label)

            val mapText = TextView(context).apply {
                text = "• D-Pad / Left Stick: GBA Directional Movement\n" +
                       "• Button A: GBA Button A (Confirm / Talk)\n" +
                       "• Button B: GBA Button B (Cancel / Run)\n" +
                       "• Button X / Y: Quick Turbo / Menu Shortcut\n" +
                       "• L1 / R1: GBA Left / Right Shoulder Triggers\n" +
                       "• L2 / R2: Quick Save State (L2) / Quick Load State (R2)\n" +
                       "• Start / Select: GBA Start / Select Buttons"
                setTextColor(0xFFDDDDDD.toInt())
                textSize = 13f
                setLineSpacing(5f, 1f)
            }
            addView(mapText)
        }
        content.addView(controlsCard)

        // 4. Gemini API Key Configuration
        val apiCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "Google Gemini API Key (Assistant Grounding)"
                setTextColor(0xFFE2BF65.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 8)
            }
            addView(label)

            val desc = TextView(context).apply {
                text = "Your Google AI Pro subscription provides Cloud credits. Enter your Google AI Studio API key here to activate live Google Search grounding."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 8)
            }
            addView(desc)

            apiKeyInput = EditText(context).apply {
                hint = "Enter Gemini API Key (AIzaSy...)"
                setHintTextColor(0xFF777788.toInt())
                setTextColor(Color.WHITE)
                textSize = 13f
                setText(settingsManager.geminiApiKey ?: "")
                background = GradientDrawable().apply {
                    cornerRadius = 12f
                    setColor(0xFF16161E.toInt())
                    setStroke(2, 0xFF333348.toInt())
                }
                setPadding(16, 10, 16, 10)
            }
            addView(apiKeyInput)

            val modelLabel = TextView(context).apply {
                text = "Active AI Model"
                setTextColor(0xFF4A9EFF.toInt())
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 10, 0, 4)
            }
            addView(modelLabel)

            val modelRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 0, 0, 10)
            }

            val models = listOf("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.0-flash")
            val modelLabels = listOf("Flash 3.8", "Flash 2.5", "Flash 2.0")

            fun updateModelRow() {
                modelRow.removeAllViews()
                val currentModel = settingsManager.geminiModel
                models.forEachIndexed { i, m ->
                    val isSel = (m == currentModel)
                    val mBtn = Button(context).apply {
                        text = modelLabels[i]
                        textSize = 11f
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            cornerRadius = 10f
                            setColor(if (isSel) 0xFF4A9EFF.toInt() else 0xFF282834.toInt())
                        }
                        setPadding(10, 6, 10, 6)
                        setOnClickListener {
                            settingsManager.geminiModel = m
                            RomHackAssistant.setModel(m)
                            updateModelRow()
                            Toast.makeText(context, "Set model to $m", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val lpM = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply {
                        setMargins(0, 0, if (i < models.size - 1) 6 else 0, 0)
                    }
                    modelRow.addView(mBtn, lpM)
                }
            }
            updateModelRow()
            addView(modelRow)

            val saveKeyBtn = Button(context).apply {
                text = "Save API Key"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0xFF4A9EFF.toInt())
                }
                setPadding(18, 8, 18, 8)
                setOnClickListener {
                    val key = apiKeyInput.text.toString().trim()
                    settingsManager.geminiApiKey = if (key.isNotEmpty()) key else null
                    RomHackAssistant.setApiKey(settingsManager.geminiApiKey)
                    Toast.makeText(context, if (key.isNotEmpty()) "Gemini API key saved!" else "API key cleared", Toast.LENGTH_SHORT).show()
                }
            }
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 10, 0, 0)
            }
            addView(saveKeyBtn, lp)
        }
        content.addView(apiCard)

        // 5. ROM Saves Storage Directory Card (SAF & Fallback)
        val savesStorageCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "💾 ROM Saves Storage Directory"
                setTextColor(0xFF4A9EFF.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 4)
            }
            addView(label)

            val desc = TextView(context).apply {
                text = "DualDex supports Scoped Storage via Android SAF. Select a user-visible folder (like Documents) so your .sav and .state files can be synced with PC or other emulators."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 8)
            }
            addView(desc)

            val storageDescView = TextView(context).apply {
                val current = settingsManager.savesFolderUri
                text = if (!current.isNullOrBlank()) "Active Location: Custom SAF Folder" else "Active Location: Internal App Storage (Private)"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 10)
            }
            addView(storageDescView)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 4, 0, 0)
            }

            val pickFolderBtn = Button(context).apply {
                text = "📁 Select Saves Folder"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 12f
                    setColor(0xFF2B4A77.toInt())
                }
                setPadding(14, 8, 14, 8)
                setOnClickListener {
                    onChooseSavesFolderRequested?.invoke()
                }
            }
            val lp1 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(0, 0, 6, 0) }
            btnRow.addView(pickFolderBtn, lp1)

            val resetBtn = Button(context).apply {
                text = "🔄 Reset to Internal"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 12f
                    setColor(0xFF44222A.toInt())
                }
                setPadding(14, 8, 14, 8)
                setOnClickListener {
                    settingsManager.savesFolderUri = null
                    storageDescView.text = "Active Location: Internal App Storage (Private)"
                    Toast.makeText(context, "Reset saves storage to internal app storage", Toast.LENGTH_SHORT).show()
                }
            }
            val lp2 = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(6, 0, 0, 0) }
            btnRow.addView(resetBtn, lp2)

            addView(btnRow)
        }
        content.addView(savesStorageCard)

        // Cheats Quick Access Card
        val cheatCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "⚡ Action Replay & Cheats"
                setTextColor(0xFFFFD700.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 6)
            }
            addView(label)

            val desc = TextView(context).apply {
                text = "Input, toggle, and manage Action Replay, GameShark, and CodeBreaker cheats for the active game."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 10)
            }
            addView(desc)

            val openCheatsBtn = Button(context).apply {
                text = "⚡ Open Cheats Manager"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0xFF2E6B4A.toInt())
                }
                setPadding(18, 8, 18, 8)
                setOnClickListener {
                    viewModel.selectTab(com.dualdex.companion.CompanionTab.CHEATS)
                    onTabSelected?.invoke(com.dualdex.companion.CompanionTab.CHEATS)
                }
            }
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            addView(openCheatsBtn, lp)
        }
        content.addView(cheatCard)

        // Experimental Features Card
        val experimentalCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "🧪 Experimental Features"
                setTextColor(0xFFFF8844.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 6)
            }
            addView(label)

            val desc = TextView(context).apply {
                text = "Toggle early-access development features for battle companions and touch controls."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 10)
            }
            addView(desc)

            var autoOpenBattle = settingsManager.isBattleAutoOpenEnabled
            val toggleBattleBtn = Button(context).apply {
                fun updateText() {
                    text = if (autoOpenBattle) "Auto-open Battle Console: On" else "Auto-open Battle Console: Off"
                    background = GradientDrawable().apply {
                        cornerRadius = 14f
                        setColor(if (autoOpenBattle) 0xFF2E6B4A.toInt() else 0xFF3E3E4E.toInt())
                    }
                }
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(18, 8, 18, 8)
                updateText()
                setOnClickListener {
                    autoOpenBattle = !autoOpenBattle
                    settingsManager.isBattleAutoOpenEnabled = autoOpenBattle
                    viewModel.setBattleAutoOpenEnabled(autoOpenBattle)
                    updateText()
                    Toast.makeText(context, if (autoOpenBattle) "Battle Console will open when a battle begins" else "Battle Console auto-open disabled", Toast.LENGTH_SHORT).show()
                }
            }
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            addView(toggleBattleBtn, lp)

            var isInteractiveControls = settingsManager.isInteractiveBattleControlsEnabled
            val toggleInteractiveBtn = Button(context).apply {
                fun updateText() {
                    text = if (isInteractiveControls) "🎮 Verified Touch Controls: Allowed" else "🎮 Verified Touch Controls: Disabled (Read-Only)"
                    background = GradientDrawable().apply {
                        cornerRadius = 14f
                        setColor(if (isInteractiveControls) 0xFF2E6B4A.toInt() else 0xFF3E3E4E.toInt())
                    }
                }
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(18, 8, 18, 8)
                updateText()
                setOnClickListener {
                    isInteractiveControls = !isInteractiveControls
                    settingsManager.isInteractiveBattleControlsEnabled = isInteractiveControls
                    viewModel.setInteractiveBattleControlsEnabled(isInteractiveControls)
                    updateText()
                    Toast.makeText(context, if (isInteractiveControls) "Verified touch controls allowed when supported" else "Touch battle controls disabled (Read-only)", Toast.LENGTH_SHORT).show()
                }
            }
            val lpInteractive = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 0, 0)
            }
            addView(toggleInteractiveBtn, lpInteractive)
            addView(TextView(context).apply {
                text = "Only exact ROM builds with verified battle-menu and cursor readers can use touch controls. No bundled profile currently supports interactive controls."
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 11f
                setPadding(4, 6, 4, 0)
            })
        }
        content.addView(experimentalCard)
    }

    private fun updateShaderButtons(row: LinearLayout, selected: ShaderFilter) {
        for (i in 0 until row.childCount) {
            val btn = row.getChildAt(i) as? Button ?: continue
            val filter = ShaderFilter.values()[i]
            btn.background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(if (filter == selected) 0xFF4A9EFF.toInt() else 0xFF282834.toInt())
            }
        }
    }

    private fun updateSpeedButtons(row: LinearLayout, selectedSpeed: Int) {
        val speeds = listOf(1, 2, 3, 4)
        for (i in 0 until row.childCount) {
            val btn = row.getChildAt(i) as? Button ?: continue
            val spd = speeds[i]
            btn.background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(if (spd == selectedSpeed) 0xFF4A9EFF.toInt() else 0xFF282834.toInt())
            }
        }
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(18, 16, 18, 16)
            background = GradientDrawable().apply {
                cornerRadius = 18f
                setColor(0xFF1E1E26.toInt())
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
        }
    }
}
