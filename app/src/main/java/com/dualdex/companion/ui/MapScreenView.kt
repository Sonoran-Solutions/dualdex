package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.LocationUnavailableReason
import com.dualdex.pokemon.MapNodeType
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import com.dualdex.romhack.RomCompatibilityMessages
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
            // A tap on a map tile is a deliberate browsing selection: it must
            // survive later live updates instead of being overwritten by them.
            selection = MapScreenPresenter.onBrowsed(section)
            renderDetailSheet()
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

    /**
     * The region the user explicitly chose to browse, or null while the canvas
     * follows the player's own region. Deliberately separate from the active ROM
     * and its location strategy: browsing never selects a memory layout, and an
     * automatic follow must not harden into a permanent manual override.
     */
    private var browseOverride: RegionId? = null

    /**
     * What the header and detail sheet are showing. One value rather than a pair of
     * flags, so a live heading can never be paired with a stale detail sheet.
     */
    private var selection: MapSelection = MapSelection.None

    /** Which canvas is drawn, and whether it still follows the live region. */
    private var canvas: MapCanvasSelection = MapCanvasSelection(
        region = RegionId.JOHTO,
        followsLiveRegion = true,
    )

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
        val centerLp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
            marginEnd = context.dp(DualDexTheme.Spacing.tight)
        }
        topRow.addView(centerBtn, centerLp)

        val resetZoomBtn = DualDexComponents.smallButton(context, "Reset", DualDexButtonStyle.SECONDARY) {
            regionMapView.resetZoom()
        }
        val resetLp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(DualDexTheme.Spacing.touchTarget))
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

        // No live location is known yet, so show nothing that could be mistaken
        // for one.
        displayNoSelection()
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
            // Browsing only. This mutates which static canvas is drawn; it must
            // never touch the active ROM, its trust, or the location strategy.
            // An explicit tap becomes the user's override until they switch games.
            browseOverride = region
            canvas = MapScreenPresenter.canvasSelection(
                strategy = viewModel.locationStrategy.value,
                browseOverride = browseOverride,
                liveSection = viewModel.resolvedLocation.value,
                current = canvas,
            )
            applyCanvas()
            selection = MapSelection.None
            renderDetailSheet()
        }.apply {
            val lp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
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
            // Every live input funnels through renderLiveState so the screen can
            // only ever show one consistent snapshot, never a mixture of a new
            // ROM with a location read from the previous one.
            scope.launch { viewModel.playerLocation.collectLatest { renderLiveState() } }
            scope.launch { viewModel.runtimeRomTrust.collectLatest { renderLiveState() } }
            scope.launch { viewModel.resolvedLocation.collectLatest { renderLiveState() } }
            scope.launch { viewModel.locationStrategy.collectLatest { onStrategyChanged() } }
        }
    }

    /**
     * Republish the whole screen from view-model state.
     *
     * Public so the host can force a refresh; the state itself lives in
     * [MapScreenPresenter] so it is exercised by tests without an Android view.
     */
    fun refreshUI() {
        renderLiveState()
    }

    /**
     * A different game's map table is now active.
     *
     * The previous browsing region belonged to the old game's canvas, so the
     * override is dropped and the canvas returns to following live state. This is
     * the only place browsing is reset, and it never writes back to the ROM.
     */
    private fun onStrategyChanged() {
        browseOverride = null
        canvas = MapCanvasSelection(
            region = canvas.region,
            followsLiveRegion = true,
        )
        renderLiveState()
    }

    private fun renderLiveState() {
        val strategy = viewModel.locationStrategy.value
        val loc = viewModel.playerLocation.value
        val section = viewModel.resolvedLocation.value
        val trust = viewModel.runtimeRomTrust.value

        val header = MapScreenPresenter.headerState(
            location = loc,
            section = section,
            trust = trust,
            reason = viewModel.locationUnavailableReason.value,
        )

        // The renderer must use the same table the resolver did.
        regionMapView.strategy = strategy
        regionMapView.resolvedLocation = section
        regionMapView.playerLocation = loc

        canvas = MapScreenPresenter.canvasSelection(
            strategy = strategy,
            browseOverride = browseOverride,
            liveSection = section,
            current = canvas,
        )
        applyCanvas()

        when (header) {
            is MapHeaderState.Live -> {
                selection = MapScreenPresenter.onLiveLocation(selection, header.section)
                renderLiveHeader(header)
            }
            is MapHeaderState.Unavailable -> {
                selection = MapScreenPresenter.onLiveInvalidated(selection)
                renderUnavailableHeader(header)
            }
            is MapHeaderState.NoLiveLocation -> {
                selection = MapScreenPresenter.onLiveInvalidated(selection)
                renderNoLiveHeader(header.trust)
            }
        }

        // A live selection must never be marked on a canvas that is not drawing
        // the region that position belongs to.
        regionMapView.selectedSection = when (val current = selection) {
            is MapSelection.Live ->
                current.section.takeIf { it.region == canvas.region }
            is MapSelection.Browsing -> current.section
            MapSelection.None -> null
        }

        renderDetailSheet()
    }

    /** Applies the canvas choice and keeps the region tabs consistent. */
    private fun applyCanvas() {
        if (regionMapView.currentRegion != canvas.region) {
            regionMapView.currentRegion = canvas.region
        }
        if (regionMapView.strategy != viewModel.locationStrategy.value) {
            regionMapView.strategy = viewModel.locationStrategy.value
        }
        updateRegionTabStyles(canvas.region)
    }

    private fun renderLiveHeader(header: MapHeaderState.Live) {
        val section = header.section
        locationTitleView.text = section.name
        val subtitle = if (section.region != null) {
            "${section.region.displayName} · ${section.name}"
        } else {
            section.name
        }
        // Named and region-known, but DualDex has no canvas that draws it, so no
        // marker is placed and the sheet must not claim it is current.
        locationSubtitleView.text = if (section.presentable) {
            subtitle
        } else {
            "$subtitle · No map for this area"
        }

        when {
            header.isIndoors -> {
                envBadgeView.text = "Indoors"
                envBadgeView.setTextColor(DualDexTheme.Color.warning)
            }
            section.nodeType == MapNodeType.DUNGEON -> {
                envBadgeView.text = "Cave / Dungeon"
                envBadgeView.setTextColor(DualDexTheme.Color.accent)
            }
            else -> {
                envBadgeView.text = "Overworld"
                envBadgeView.setTextColor(DualDexTheme.Color.success)
            }
        }

        val loc = header.location
        technicalCoordsView.text =
            "Tile (${loc.localX}, ${loc.localY}) · Group ${loc.mapGroup} · Map ${loc.mapNum}"
    }

    private fun renderNoLiveHeader(trust: com.dualdex.romhack.RuntimeRomTrust) {
        if (trust.hasActiveRom && !trust.mayReadLiveMemory) {
            locationTitleView.text = RomCompatibilityMessages.badge(trust.status)
            locationSubtitleView.text = RomCompatibilityMessages.detail(trust.status)
        } else {
            locationTitleView.text = "Waiting for player..."
            locationSubtitleView.text = "Location will appear when a supported game runs"
        }
        envBadgeView.text = "Unknown"
        envBadgeView.setTextColor(DualDexTheme.Color.textDisabled)
        technicalCoordsView.text = "Technical details: None"
    }

    /**
     * A raw location was read but cannot be turned into a trustworthy section.
     * The screen must say so instead of showing a plausible town.
     */
    private fun renderUnavailableHeader(header: MapHeaderState.Unavailable) {
        val loc = header.location
        locationTitleView.text = "Location unavailable"
        locationSubtitleView.text = when (header.reason) {
            LocationUnavailableReason.NO_STRATEGY ->
                "This profile does not expose a supported map table"
            LocationUnavailableReason.UNKNOWN_MAP_ID ->
                "Group ${loc.mapGroup} · Map ${loc.mapNum} is not in the verified map table"
            LocationUnavailableReason.INVALID_READ ->
                "The location could not be read"
            LocationUnavailableReason.NOT_PRESENTABLE ->
                "This area has no map presentation"
            null -> "This profile does not expose a supported map table"
        }
        envBadgeView.text = "Unknown"
        envBadgeView.setTextColor(DualDexTheme.Color.textDisabled)
        technicalCoordsView.text = if (header.mayReadLiveMemory) {
            "Tile (${loc.localX}, ${loc.localY}) · Group ${loc.mapGroup} · Map ${loc.mapNum}"
        } else {
            "Technical details: None"
        }
    }

    /**
     * Repaint the detail sheet from [selection].
     *
     * Called on every state change, so a live selection always describes the current
     * location and an invalidated one is cleared rather than left on screen.
     */
    private fun renderDetailSheet() {
        when (val current = selection) {
            is MapSelection.Live -> displaySectionDetails(current.section)
            is MapSelection.Browsing -> displaySectionDetails(current.section)
            MapSelection.None -> displayNoSelection()
        }
    }

    /** Clears the detail sheet so it cannot imply a current location. */
    private fun displayNoSelection() {
        detailNameView.text = "No location selected"
        detailTypeBadgeView.text = "Unknown"
        detailTypeBadgeView.setTextColor(DualDexTheme.Color.textDisabled)
        detailDescView.text = "Tap a map tile to browse it, or load a supported game to see your location."
        gymInfoView.visibility = View.GONE
        poiContainer.removeAllViews()
        connectionsView.visibility = View.GONE
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

        detailDescView.text = when {
            sec.description.isNotEmpty() -> sec.description
            sec.region != null -> "A location in the ${sec.region.displayName} region."
            else -> "This area has no known region."
        }

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
