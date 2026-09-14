package com.dualdex.companion.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.dualdex.R
import com.dualdex.companion.CompanionNavigation
import com.dualdex.companion.CompanionTab
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.ShaderFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Global companion shell. Feature screens retain their own layouts while this view owns the
 * compact context strip, five primary destinations, and routing for secondary utilities.
 */
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

    private data class PrimaryDestination(val tab: CompanionTab, val iconRes: Int)

    private val primaryDestinations = listOf(
        PrimaryDestination(CompanionTab.HOME, R.drawable.ic_dualdex_library),
        PrimaryDestination(CompanionTab.PARTY, R.drawable.ic_dualdex_party),
        PrimaryDestination(CompanionTab.BATTLE, R.drawable.ic_dualdex_battle),
        PrimaryDestination(CompanionTab.MAP, R.drawable.ic_dualdex_map),
        PrimaryDestination(CompanionTab.MORE, R.drawable.ic_dualdex_more)
    )

    private val contentContainer: FrameLayout
    private val tabButtons = mutableMapOf<CompanionTab, DualDexNavigationItem>()
    private val contextBar: LinearLayout
    private val profileLabel: TextView
    private val battleIndicator: TextView
    private val timeView: TextView
    private val batteryView: TextView
    private var isReceiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> updateTime()
                Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
            }
        }
    }

    private val homeView: HomeScreenView by lazy {
        HomeScreenView(
            context,
            viewModel,
            onChooseRomsFolderRequested,
            onRefreshRomsRequested,
            onPlayRomRequested,
            onOpenRomRequested
        )
    }
    private val partyView: PartyScreenView by lazy { PartyScreenView(context, viewModel) }
    private val mapView: MapScreenView by lazy { MapScreenView(context, viewModel) }
    private val calcView: CalcTabScreenView by lazy { CalcTabScreenView(context, viewModel) }
    private val battleView: BattleConsoleScreenView by lazy {
        BattleConsoleScreenView(context, viewModel) { navigateTo(CompanionTab.CALC) }
    }
    private val typesView: TypeChartScreenView by lazy { TypeChartScreenView(context, viewModel) }
    private val docsView: DocsScreenView by lazy { DocsScreenView(context, viewModel) }
    private val cheatsView: CheatsScreenView by lazy { CheatsScreenView(context, viewModel) }
    private val savesView: SaveStateScreenView by lazy {
        SaveStateScreenView(context, viewModel, onImportSaveRequested, onExportSaveRequested, onChooseSavesFolderRequested)
    }
    private val assistantView: com.dualdex.assistant.AssistantScreenView by lazy {
        com.dualdex.assistant.AssistantScreenView(context, viewModel)
    }
    private val settingsView: SettingsScreenView by lazy {
        SettingsScreenView(
            context,
            viewModel,
            onShaderChanged,
            onSpeedChanged,
            onStretchChanged,
            onTabSelected = ::switchTab,
            onChooseSavesFolderRequested = onChooseSavesFolderRequested
        )
    }
    private val moreView: MoreScreenView by lazy { MoreScreenView(context, ::navigateTo) }

    private var currentTab: CompanionTab = CompanionTab.HOME
    private var viewScope: CoroutineScope? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)

        contextBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.compact)
            )
            setBackgroundColor(DualDexTheme.Color.surface)
        }
        profileLabel = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        battleIndicator = DualDexComponents.ghostControl(context, "Battle") {
            navigateTo(CompanionTab.BATTLE)
        }.apply {
            setTextColor(DualDexTheme.Color.accent)
            textSize = DualDexTheme.Type.compact
            visibility = View.GONE
            contentDescription = "Open Battle Console"
        }
        val statusArea = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        timeView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            maxLines = 1
        }
        batteryView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            maxLines = 1
        }
        statusArea.addView(timeView)
        statusArea.addView(
            batteryView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(DualDexTheme.Spacing.standard)
            }
        )

        contextBar.addView(profileLabel)
        contextBar.addView(
            battleIndicator,
            LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
                marginStart = context.dp(DualDexTheme.Spacing.compact)
                marginEnd = context.dp(DualDexTheme.Spacing.standard)
            }
        )
        contextBar.addView(statusArea, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(contextBar)

        updateTime()
        updateBattery(null)

        contentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(contentContainer)

        val navBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.tight),
                context.dp(DualDexTheme.Spacing.tight),
                context.dp(DualDexTheme.Spacing.tight),
                context.dp(DualDexTheme.Spacing.compact)
            )
            setBackgroundColor(DualDexTheme.Color.surface)
        }
        primaryDestinations.forEach { destination ->
            val navItem = DualDexComponents.navigationItem(
                context,
                destination.iconRes,
                destination.tab.title
            ) { navigateTo(destination.tab) }
            tabButtons[destination.tab] = navItem
            navBar.addView(navItem, LayoutParams(0, context.dp(DualDexTheme.Control.primaryNavigationHeight), 1f))
        }
        addView(navBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        updateContextBar()
        switchTab(viewModel.selectedTab.value)
    }

    fun switchTab(tab: CompanionTab) {
        if (currentTab == tab && contentContainer.childCount > 0) {
            updateNavigationSelection(tab)
            return
        }
        currentTab = tab
        contentContainer.removeAllViews()
        val activeView: View = when (tab) {
            CompanionTab.HOME -> homeView.apply {
                updateResumeCard()
                updateFolderStatus()
            }
            CompanionTab.PARTY -> partyView.apply { refreshUI() }
            CompanionTab.MAP -> mapView.apply { refreshUI() }
            CompanionTab.CALC -> calcView.apply { refreshUI() }
            CompanionTab.BATTLE -> battleView.apply { refreshUI() }
            CompanionTab.TYPES -> battleView.apply {
                selectMode(BattleMode.TYPE_MATCHUPS)
                refreshUI()
            }
            CompanionTab.DOCS -> docsView.apply { refreshUI() }
            CompanionTab.CHEATS -> cheatsView.apply { refreshUI() }
            CompanionTab.SAVES -> savesView.apply { refreshUI() }
            CompanionTab.ASSISTANT -> assistantView
            CompanionTab.SETTINGS -> settingsView
            CompanionTab.MORE -> moreView
        }
        contentContainer.addView(activeView)
        // Feature views must never leave the shared clock/battery context strip hidden.
        updateContextBar()
        updateNavigationSelection(tab)
    }

    fun refreshHomeScreen() {
        post {
            homeView.updateResumeCard()
            homeView.updateFolderStatus()
        }
    }

    fun notifyProfileChanged() {
        post {
            updateContextBar()
            when (viewModel.selectedTab.value) {
                CompanionTab.HOME -> homeView.updateResumeCard()
                CompanionTab.PARTY -> partyView.refreshUI()
                CompanionTab.MAP -> mapView.refreshUI()
                CompanionTab.CALC -> calcView.refreshUI()
                CompanionTab.BATTLE,
                CompanionTab.TYPES -> battleView.refreshUI()
                CompanionTab.CHEATS -> cheatsView.refreshUI()
                CompanionTab.SAVES -> savesView.refreshUI()
                else -> Unit
            }
        }
    }

    fun notifyPartyUpdated() {
        post {
            updateContextBar()
            when (viewModel.selectedTab.value) {
                CompanionTab.PARTY -> partyView.refreshUI()
                CompanionTab.CALC -> calcView.refreshUI()
                CompanionTab.BATTLE -> battleView.refreshUI()
                else -> Unit
            }
        }
    }

    fun refreshSavesTab() {
        post {
            if (viewModel.selectedTab.value == CompanionTab.SAVES) savesView.refreshUI()
        }
    }

    private fun navigateTo(tab: CompanionTab) {
        if (currentTab == tab) {
            updateNavigationSelection(tab)
            return
        }
        viewModel.selectTab(tab)
        switchTab(tab)
    }

    private fun updateNavigationSelection(tab: CompanionTab) {
        val primaryTab = CompanionNavigation.primaryTabFor(tab)
        tabButtons.forEach { (destination, button) ->
            button.setSelectedState(destination == primaryTab)
        }
    }

    private fun updateContextBar() {
        val identity = viewModel.activeRomIdentity.value
        val inBattle = viewModel.isInBattle.value
        if (identity != null) {
            val displayName = identity.displayName.trim().ifBlank { "Game" }
            val profileName = viewModel.activeProfile.value.name.trim()
            profileLabel.text = if (
                profileName.isNotBlank() &&
                !displayName.equals(profileName, ignoreCase = true) &&
                !displayName.contains(profileName, ignoreCase = true)
            ) {
                "$displayName · $profileName"
            } else {
                displayName
            }
            profileLabel.visibility = View.VISIBLE
        } else {
            profileLabel.text = ""
            profileLabel.visibility = View.VISIBLE
        }
        battleIndicator.visibility = if (inBattle) View.VISIBLE else View.GONE
        contextBar.visibility = View.VISIBLE
    }

    private fun updateTime() {
        timeView.text = CompanionStatusFormatter.formatTime(context)
    }

    private fun updateBattery(intent: Intent?) {
        val display = CompanionStatusFormatter.formatBattery(intent)
        batteryView.text = display.text
        batteryView.setTextColor(display.color)
    }

    internal fun getDisplayedTime(): CharSequence = timeView.text
    internal fun getDisplayedBattery(): CharSequence = batteryView.text

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope

        updateTime()
        val stickyBattery = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }
        updateBattery(stickyBattery)

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            try {
                ContextCompat.registerReceiver(
                    context,
                    statusReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isReceiverRegistered = true
            } catch (e: Throwable) {
                Log.w("DualDex", "Could not register status receiver: ${e.message}")
            }
        }

        scope.launch { viewModel.playerParty.collectLatest { notifyPartyUpdated() } }
        scope.launch { viewModel.activeProfile.collectLatest { notifyProfileChanged() } }
        scope.launch { viewModel.activeRomIdentity.collectLatest { notifyProfileChanged() } }
        var wasInBattle = false
        scope.launch {
            viewModel.isInBattle.collectLatest { inBattle ->
                notifyPartyUpdated()
                if (inBattle && !wasInBattle && viewModel.isBattleAutoOpenEnabled.value && currentTab != CompanionTab.BATTLE) {
                    navigateTo(CompanionTab.BATTLE)
                }
                wasInBattle = inBattle
            }
        }
        scope.launch {
            viewModel.selectedTab.collectLatest { tab ->
                if (currentTab != tab) switchTab(tab)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(statusReceiver)
            } catch (_: Throwable) {
            }
            isReceiverRegistered = false
        }
        viewScope?.cancel()
        viewScope = null
    }
}
