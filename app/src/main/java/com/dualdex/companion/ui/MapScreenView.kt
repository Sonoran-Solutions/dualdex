package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.MapNodeType
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Redesigned Map companion screen adhering to the Quiet Handheld Companion design system.
 * Keeps the interactive canvas full-bleed while standardizing floating controls, location
 * hierarchy, and collapsible details with DualDexTheme tokens and zero emoji.
 */
class MapScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : FrameLayout(context) {

    private val locationTitleView: TextView
    private val locationSubtitleView: TextView
    private val envBadgeView: TextView

    private val regionMapView: RegionMapView = RegionMapView(context).apply {
        onSectionSelected = { section ->
            displaySectionDetails(section)
        }
    }

    // Detail card views
    private val detailNameView: TextView
    private val detailTypeBadgeView: TextView
    private val detailDescView: TextView
    private val gymInfoView: TextView
    private val poiContainer: LinearLayout
    private val connectionsView: TextView
    private val technicalCoordsView: TextView
    private val expandToggleView: TextView
    private val expandableContent: LinearLayout
    private var isSheetExpanded: Boolean = false

    private val regionButtons = mutableMapOf<RegionId, TextView>()
    private var viewScope: CoroutineScope? = null

    init {
        setBackgroundColor(DualDexTheme.Color.background)

        // 1. Full-bleed interactive map canvas
        regionMapView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(regionMapView)

        // 2. Top Floating Control Pill
        val topPill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact)
            )
            background = DualDexComponents.surface(context, elevated = true)
        }
        val topLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP
            setMargins(
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.compact),
                0
            )
        }
        addView(topPill, topLp)

        // Top Row: Location Title, Environment Badge, Action Buttons
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        locationTitleView = TextView(context).apply {
            text = "Loading Town Map..."
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
        }
        topRow.addView(locationTitleView)

        envBadgeView = TextView(context).apply {
            text = "Overworld"
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
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
        val envLp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.compact), 0)
        }
        topRow.addView(envBadgeView, envLp)

        val centerBtn = DualDexComponents.smallButton(context, "Center", DualDexButtonStyle.PRIMARY) {
            regionMapView.centerOnPlayer()
        }
        val centerLp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30)).apply {
            marginEnd = context.dp(DualDexTheme.Spacing.tight)
        }
        topRow.addView(centerBtn, centerLp)

        val resetZoomBtn = DualDexComponents.smallButton(context, "Reset", DualDexButtonStyle.SECONDARY) {
            regionMapView.resetZoom()
        }
        val resetLp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30))
        topRow.addView(resetZoomBtn, resetLp)

        topPill.addView(topRow)

        // Sub Row: Location subtitle & Region Selector Tabs
        val subRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
        }

        locationSubtitleView = TextView(context).apply {
            text = "Reading live EWRAM player position..."
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
        }
        subRow.addView(locationSubtitleView)

        val johtoBtn = createRegionTabButton("Johto", RegionId.JOHTO)
        val kantoBtn = createRegionTabButton("Kanto", RegionId.KANTO)
        val hoennBtn = createRegionTabButton("Hoenn", RegionId.HOENN)
        subRow.addView(johtoBtn)
        subRow.addView(kantoBtn)
        subRow.addView(hoennBtn)

        topPill.addView(subRow)

        // 3. Bottom Floating Collapsible Detail Sheet
        val bottomCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact)
            )
            background = DualDexComponents.surface(context, elevated = true)
        }
        val bottomLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            setMargins(
                context.dp(DualDexTheme.Spacing.compact),
                0,
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.compact)
            )
        }
        addView(bottomCard, bottomLp)

        // Header Row (tap to expand/collapse)
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(0, context.dp(DualDexTheme.Spacing.tight / 2), 0, context.dp(DualDexTheme.Spacing.tight / 2))
            setOnClickListener { toggleSheetExpansion() }
        }

        detailNameView = TextView(context).apply {
            text = "New Bark Town"
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
        }
        headerRow.addView(detailNameView)

        detailTypeBadgeView = TextView(context).apply {
            text = "Town"
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
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
        val badgeLp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, context.dp(DualDexTheme.Spacing.compact), 0)
        }
        headerRow.addView(detailTypeBadgeView, badgeLp)

        expandToggleView = TextView(context).apply {
            text = "Details ▲"
            setTextColor(DualDexTheme.Color.accent)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            setPadding(context.dp(DualDexTheme.Spacing.compact), context.dp(DualDexTheme.Spacing.tight / 2), 0, context.dp(DualDexTheme.Spacing.tight / 2))
        }
        headerRow.addView(expandToggleView)
        bottomCard.addView(headerRow)

        // Expandable Content Body
        expandableContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.tight))
        }

        detailDescView = TextView(context).apply {
            text = "The Town Where the Winds of a New Beginning Blow."
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.tight))
        }
        expandableContent.addView(detailDescView)

        gymInfoView = TextView(context).apply {
            visibility = View.GONE
            setTextColor(DualDexTheme.Color.warning)
            textSize = DualDexTheme.Type.meta
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.tight))
        }
        expandableContent.addView(gymInfoView)

        poiContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.tight))
        }
        expandableContent.addView(poiContainer)

        connectionsView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.tight))
        }
        expandableContent.addView(connectionsView)

        // Technical details demoted to the bottom of the expandable sheet
        technicalCoordsView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textDisabled)
            textSize = DualDexTheme.Type.compact
        }
        expandableContent.addView(technicalCoordsView)

        bottomCard.addView(expandableContent)

        // Initial default display
        displaySectionDetails(RegionMapDatabase.JOHTO_DEFAULT)
    }

    private fun toggleSheetExpansion() {
        isSheetExpanded = !isSheetExpanded
        expandableContent.visibility = if (isSheetExpanded) View.VISIBLE else View.GONE
        expandToggleView.text = if (isSheetExpanded) "Hide ▼" else "Details ▲"
    }

    private fun createRegionTabButton(title: String, region: RegionId): TextView {
        val btn = DualDexComponents.smallButton(
            context = context,
            text = title,
            style = if (region == RegionId.JOHTO) DualDexButtonStyle.PRIMARY else DualDexButtonStyle.GHOST
        ) {
            regionMapView.currentRegion = region
            updateRegionTabStyles(region)
            val firstSec = RegionMapDatabase.getSections(region).firstOrNull()
            if (firstSec != null) {
                displaySectionDetails(firstSec)
            }
        }.apply {
            val lp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(26)).apply {
                marginStart = context.dp(DualDexTheme.Spacing.tight / 2)
            }
            layoutParams = lp
        }
        regionButtons[region] = btn
        return btn
    }

    private fun updateRegionTabStyles(activeRegion: RegionId) {
        regionButtons.forEach { (reg, btn) ->
            val isAct = (reg == activeRegion)
            val style = if (isAct) DualDexButtonStyle.PRIMARY else DualDexButtonStyle.GHOST
            btn.background = DualDexComponents.controlBackground(context, style, selected = isAct)
            btn.setTextColor(if (isAct) DualDexTheme.Color.onAccent else DualDexTheme.Color.textSecondary)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startObserving()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun startObserving() {
        viewScope?.cancel()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { scope ->
            scope.launch {
                viewModel.playerLocation.collectLatest { loc ->
                    updatePlayerLocation(loc)
                }
            }
            scope.launch {
                viewModel.resolvedLocation.collectLatest { sec ->
                    if (regionMapView.selectedSection == null) {
                        displaySectionDetails(sec)
                        regionMapView.selectedSection = sec
                    }
                }
            }
        }
    }

    fun refreshUI() {
        val prof = viewModel.activeProfile.value
        val region = when (prof.gameId) {
            1 -> if (prof.id == "heart_and_soul" || prof.name.contains("Heart", ignoreCase = true)) RegionId.JOHTO else RegionId.HOENN
            2 -> RegionId.KANTO
            else -> RegionId.JOHTO
        }
        regionMapView.currentRegion = region
        updateRegionTabStyles(region)
        updatePlayerLocation(viewModel.playerLocation.value)
    }

    private fun updatePlayerLocation(loc: PlayerLocation?) {
        regionMapView.playerLocation = loc

        if (loc == null || !loc.isValid) {
            locationTitleView.text = "Waiting for player..."
            locationSubtitleView.text = "Location will appear when a supported game runs"
            envBadgeView.text = "Unknown"
            envBadgeView.setTextColor(DualDexTheme.Color.textDisabled)
            technicalCoordsView.text = "Technical details: None"
            return
        }

        val isHns = (regionMapView.currentRegion == RegionId.JOHTO)
        val sec = RegionMapDatabase.resolveLocation(if (isHns) 1 else 2, isHns, loc)

        // Player-facing location names as primary visible header
        locationTitleView.text = sec.name
        locationSubtitleView.text = "${sec.region.displayName} · ${sec.name}"

        if (loc.isIndoors) {
            envBadgeView.text = "Indoors"
            envBadgeView.setTextColor(DualDexTheme.Color.warning)
        } else if (sec.nodeType == MapNodeType.DUNGEON) {
            envBadgeView.text = "Cave / Dungeon"
            envBadgeView.setTextColor(DualDexTheme.Color.accent)
        } else {
            envBadgeView.text = "Overworld"
            envBadgeView.setTextColor(DualDexTheme.Color.success)
        }

        // Technical coordinates demoted to expandable section
        technicalCoordsView.text = "Tile (${loc.localX}, ${loc.localY}) · Group ${loc.mapGroup} · Map ${loc.mapNum}"
    }

    private fun displaySectionDetails(sec: RegionMapSection) {
        detailNameView.text = sec.name
        val typeName = sec.nodeType.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        detailTypeBadgeView.text = typeName

        val badgeColor = when (sec.nodeType) {
            MapNodeType.CITY -> DualDexTheme.Color.accent
            MapNodeType.TOWN -> DualDexTheme.Color.success
            MapNodeType.DUNGEON -> DualDexTheme.Color.warning
            else -> DualDexTheme.Color.textSecondary
        }
        detailTypeBadgeView.setTextColor(badgeColor)

        detailDescView.text = if (sec.description.isNotEmpty()) sec.description else "A location in the ${sec.region.displayName} region."

        if (sec.gymLeader != null) {
            gymInfoView.visibility = View.VISIBLE
            gymInfoView.text = "Gym: ${sec.gymLeader} · ${sec.badge ?: "Badge"}"
        } else {
            gymInfoView.visibility = View.GONE
        }

        poiContainer.removeAllViews()
        if (sec.landmarks.isNotEmpty()) {
            val landmarksText = TextView(context).apply {
                text = "Points of interest: ${sec.landmarks.take(3).joinToString(", ")}"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
            }
            poiContainer.addView(landmarksText)
        }

        if (sec.connections.isNotEmpty()) {
            connectionsView.visibility = View.VISIBLE
            connectionsView.text = "Connections: ${sec.connections.joinToString(", ")}"
        } else {
            connectionsView.visibility = View.GONE
        }
    }
}
