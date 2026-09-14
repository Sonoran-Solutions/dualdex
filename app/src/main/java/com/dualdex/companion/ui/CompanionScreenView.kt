package com.dualdex.companion.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.net.Uri
import android.widget.TextView
import com.dualdex.companion.CompanionTab
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.ShaderFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class CompanionScreenView(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val onOpenRomRequested: (() -> Unit)? = null,
    private val onShaderChanged: ((ShaderFilter) -> Unit)? = null,
    private val onSpeedChanged: ((Int) -> Unit)? = null,
    private val onImportSaveRequested: (() -> Unit)? = null,
    private val onExportSaveRequested: (() -> Unit)? = null,
    private val onChooseRomsFolderRequested: (() -> Unit)? = null,
    private val onRefreshRomsRequested: (() -> Unit)? = null,
    private val onPlayRomRequested: ((Uri, String) -> Unit)? = null,
    private val onStretchChanged: ((Boolean) -> Unit)? = null,
    private val onChooseSavesFolderRequested: (() -> Unit)? = null
) : LinearLayout(context) {

    private val contentContainer: FrameLayout
    private val tabButtons = mutableMapOf<CompanionTab, TextView>()
    private val timeView: TextView
    private val profileLabel: TextView
    private val battleBadge: TextView
    private val batteryView: TextView

    private val homeView: HomeScreenView by lazy { HomeScreenView(context, viewModel, onChooseRomsFolderRequested, onRefreshRomsRequested, onPlayRomRequested) }
    private val partyView: PartyScreenView by lazy { PartyScreenView(context, viewModel) }
    private val mapView: MapScreenView by lazy { MapScreenView(context, viewModel) }
    private val calcView: CalcTabScreenView by lazy { CalcTabScreenView(context, viewModel) }
    private val battleView: BattleConsoleScreenView by lazy { BattleConsoleScreenView(context, viewModel) }
    private val typesView: TypeChartScreenView by lazy { TypeChartScreenView(context, viewModel) }
    private val docsView: DocsScreenView by lazy { DocsScreenView(context, viewModel) }
    private val cheatsView: CheatsScreenView by lazy { CheatsScreenView(context, viewModel) }
    private val savesView: SaveStateScreenView by lazy { SaveStateScreenView(context, viewModel, onImportSaveRequested, onExportSaveRequested, onChooseSavesFolderRequested) }
    private val assistantView: com.dualdex.assistant.AssistantScreenView by lazy { com.dualdex.assistant.AssistantScreenView(context, viewModel) }
    private val settingsView: SettingsScreenView by lazy {
        SettingsScreenView(
            context,
            viewModel,
            onShaderChanged,
            onSpeedChanged,
            onStretchChanged,
            onTabSelected = { tab ->
                switchTab(tab)
            },
            onChooseSavesFolderRequested = onChooseSavesFolderRequested
        )
    }

    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateSystemStatus()
            postDelayed(this, 15000L)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF101014.toInt())

        // Top Status Header (Time, ROM Name, Open ROM, Battle Status, Battery)
        val headerBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xFF16161E.toInt())
        }

        // Top-left: System Clock
        timeView = TextView(context).apply {
            setTextColor(0xFFD0D0E0.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 10, 0)
        }
        headerBar.addView(timeView)

        // ROM / Profile label
        profileLabel = TextView(context).apply {
            val prof = viewModel.activeProfile.value
            text = "⚡ ${prof.name}"
            setTextColor(0xFF4A9EFF.toInt())
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        headerBar.addView(profileLabel)

        val spacer = View(context).apply {
            layoutParams = LayoutParams(0, 1, 1.0f)
        }
        headerBar.addView(spacer)

        if (onOpenRomRequested != null) {
            val openRomBtn = Button(context).apply {
                text = "📁 Open ROM"
                textSize = 11f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(0xFF2B3A55.toInt())
                }
                setPadding(12, 4, 12, 4)
                setOnClickListener { onOpenRomRequested.invoke() }
            }
            val lpRom = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 8, 0)
            }
            headerBar.addView(openRomBtn, lpRom)
        }

        battleBadge = TextView(context).apply {
            text = "Ready"
            setTextColor(0xFF50C878.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(10, 4, 10, 4)
            background = GradientDrawable().apply {
                cornerRadius = 10f
                setColor(0xFF1F2B24.toInt())
                setStroke(1, 0xFF50C878.toInt())
            }
            setOnClickListener {
                if (viewModel.isBattleTabEnabled.value) {
                    viewModel.selectTab(CompanionTab.BATTLE)
                    switchTab(CompanionTab.BATTLE)
                }
            }
        }
        val lpBadge = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 8, 0)
        }
        headerBar.addView(battleBadge, lpBadge)

        // Top-right: Battery Charge Indicator
        batteryView = TextView(context).apply {
            text = "🔋 --%"
            setTextColor(0xFF50C878.toInt())
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(10, 4, 10, 4)
            background = GradientDrawable().apply {
                cornerRadius = 10f
                setColor(0xFF1E222B.toInt())
            }
        }
        headerBar.addView(batteryView)

        addView(headerBar)
        updateSystemStatus()

        // Middle Content Container
        contentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)
        }
        addView(contentContainer)

        // Bottom Tab Navigation Bar (Scrollable to comfortably fit all tabs)
        val navScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            setBackgroundColor(0xFF16161E.toInt())
        }
        val navBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 6, 6, 8)
        }

        CompanionTab.values().forEach { tab ->
            val tabBtn = TextView(context).apply {
                text = "${tab.iconEmoji}\n${tab.title}"
                gravity = Gravity.CENTER
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(6, 6, 6, 6)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    minimumWidth = (48 * context.resources.displayMetrics.density).toInt()
                }
                if (tab == CompanionTab.BATTLE && !viewModel.isBattleTabEnabled.value) {
                    visibility = View.GONE
                }
                setOnClickListener {
                    viewModel.selectTab(tab)
                    switchTab(tab)
                }
            }
            tabButtons[tab] = tabBtn
            navBar.addView(tabBtn)
        }
        navScroll.addView(navBar)
        addView(navScroll)

        // Initial tab
        switchTab(viewModel.selectedTab.value)
    }

    private var currentTab: CompanionTab = CompanionTab.HOME
    fun switchTab(tab: CompanionTab) {
        val targetTab = if (tab == CompanionTab.BATTLE && !viewModel.isBattleTabEnabled.value) {
            CompanionTab.HOME
        } else {
            tab
        }
        currentTab = targetTab
        contentContainer.removeAllViews()

        val activeView: View = when (tab) {
            CompanionTab.HOME -> {
                homeView.updateResumeCard()
                homeView.updateFolderStatus()
                homeView
            }
            CompanionTab.PARTY -> {
                partyView.refreshUI()
                partyView
            }
            CompanionTab.MAP -> {
                mapView.refreshUI()
                mapView
            }
            CompanionTab.CALC -> {
                calcView.refreshUI()
                calcView
            }
            CompanionTab.BATTLE -> {
                battleView.refreshUI()
                battleView
            }
            CompanionTab.TYPES -> typesView
            CompanionTab.DOCS -> {
                docsView.refreshUI()
                docsView
            }
            CompanionTab.CHEATS -> {
                cheatsView.refreshUI()
                cheatsView
            }
            CompanionTab.SAVES -> {
                savesView.refreshUI()
                savesView
            }
            CompanionTab.ASSISTANT -> assistantView
            CompanionTab.SETTINGS -> settingsView
        }
        contentContainer.addView(activeView)

        tabButtons.forEach { (t, btn) ->
            val isSelected = (t == tab)
            btn.setTextColor(if (isSelected) 0xFF4A9EFF.toInt() else 0xFF888899.toInt())
            btn.background = if (isSelected) {
                GradientDrawable().apply {
                    cornerRadius = 12f
                    setColor(0xFF222B3D.toInt())
                }
            } else null
        }
    }

    fun refreshHomeScreen() {
        post {
            homeView.updateResumeCard()
            homeView.updateFolderStatus()
        }
    }

    fun notifyProfileChanged() {
        post {
            val prof = viewModel.activeProfile.value
            profileLabel.text = "⚡ ${prof.name}"
            if (viewModel.selectedTab.value == CompanionTab.HOME) homeView.updateResumeCard()
            if (viewModel.selectedTab.value == CompanionTab.PARTY) partyView.refreshUI()
            if (viewModel.selectedTab.value == CompanionTab.MAP) mapView.refreshUI()
            if (viewModel.selectedTab.value == CompanionTab.CALC) calcView.refreshUI()
            if (viewModel.selectedTab.value == CompanionTab.BATTLE) battleView.refreshUI()
            if (viewModel.selectedTab.value == CompanionTab.TYPES) typesView.updateMatchupDisplay()
            if (viewModel.selectedTab.value == CompanionTab.CHEATS) cheatsView.refreshUI()
            if (viewModel.selectedTab.value == CompanionTab.SAVES) savesView.refreshUI()
        }
    }

    fun notifyPartyUpdated() {
        post {
            val inBattle = viewModel.isInBattle.value
            battleBadge.text = if (inBattle) "⚔️ Battle" else "Ready"
            battleBadge.setTextColor(if (inBattle) 0xFFFF6B6B.toInt() else 0xFF50C878.toInt())

            if (viewModel.selectedTab.value == CompanionTab.PARTY) {
                partyView.refreshUI()
            } else if (viewModel.selectedTab.value == CompanionTab.CALC) {
                calcView.refreshUI()
            } else if (viewModel.selectedTab.value == CompanionTab.BATTLE) {
                battleView.refreshUI()
            }
        }
    }

    fun refreshSavesTab() {
        post {
            if (viewModel.selectedTab.value == CompanionTab.SAVES) {
                savesView.refreshUI()
            }
        }
    }

    fun updateSystemStatus() {
        post {
            try {
                // 1. Time (12-hour format with AM/PM)
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                timeView.text = "🕒 ${timeFormat.format(Date())}"

                // 2. Battery Percentage and Status via sticky intent
                val batteryStatus: Intent? = context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                if (batteryStatus != null) {
                    val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val pct = if (scale > 0 && level >= 0) (level * 100 / scale) else -1
                    if (pct >= 0) {
                        val icon = if (isCharging) "⚡" else if (pct <= 20) "🪫" else "🔋"
                        batteryView.text = "$icon $pct%"
                        batteryView.setTextColor(
                            if (isCharging) 0xFF4A9EFF.toInt()
                            else if (pct <= 20) 0xFFFF6B6B.toInt()
                            else 0xFF50C878.toInt()
                        )
                    } else {
                        batteryView.text = "🔋 --%"
                    }
                }
            } catch (e: Throwable) {
                // Ignore background status read errors
            }
        }
    }

    private var viewScope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSystemStatus()
        postDelayed(statusUpdateRunnable, 15000L)

        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope

        scope.launch {
            viewModel.playerParty.collectLatest {
                notifyPartyUpdated()
            }
        }
        scope.launch {
            viewModel.activeProfile.collectLatest {
                notifyProfileChanged()
            }
        }
        var wasInBattle = false
        scope.launch {
            viewModel.isInBattle.collectLatest { inBattle ->
                notifyPartyUpdated()
                if (inBattle && !wasInBattle && viewModel.isBattleTabEnabled.value) {
                    if (currentTab != CompanionTab.BATTLE) {
                        viewModel.selectTab(CompanionTab.BATTLE)
                        switchTab(CompanionTab.BATTLE)
                    }
                }
                wasInBattle = inBattle
            }
        }
        scope.launch {
            viewModel.isBattleTabEnabled.collectLatest { enabled ->
                tabButtons[CompanionTab.BATTLE]?.visibility = if (enabled) View.VISIBLE else View.GONE
                if (!enabled && currentTab == CompanionTab.BATTLE) {
                    viewModel.selectTab(CompanionTab.HOME)
                    switchTab(CompanionTab.HOME)
                }
            }
        }
        scope.launch {
            viewModel.selectedTab.collectLatest { tab ->
                if (currentTab != tab) {
                    switchTab(tab)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(statusUpdateRunnable)
        viewScope?.cancel()
        viewScope = null
    }
}

