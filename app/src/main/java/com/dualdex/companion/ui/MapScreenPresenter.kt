package com.dualdex.companion.ui

import com.dualdex.pokemon.LocationUnavailableReason
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.romhack.RuntimeRomTrust

/**
 * What the Map screen is currently showing in its location header and detail sheet.
 *
 * These are mutually exclusive, and keeping them as one value is what prevents the
 * two bugs this type was introduced to fix:
 *
 *  * a live location could be shown in the heading while the detail sheet kept
 *    describing an older location, because "is this live?" was a bare boolean that
 *    was only ever set once;
 *  * tapping a static tile and then receiving a live update silently replaced the
 *    user's deliberate selection.
 */
sealed interface MapSelection {
    /** Nothing is selected; the sheet must not imply a location. */
    data object None : MapSelection

    /**
     * The detail sheet is following the player's own resolved location, so it must
     * update on every location change and clear when the location is invalidated.
     */
    data class Live(val section: RegionMapSection) : MapSelection

    /**
     * The user deliberately selected a static map section. It survives live updates
     * and is never presented as the player's current position.
     */
    data class Browsing(val section: RegionMapSection) : MapSelection
}

/** Which canvas the map is drawing, and why. */
data class MapCanvasSelection(
    val region: RegionId,
    /**
     * True while the canvas follows the player's own region. An automatic choice
     * must not silently become a permanent user override, or entering Kanto (or
     * switching to another game) would leave the wrong canvas showing.
     */
    val followsLiveRegion: Boolean,
)

/** How the location header and environment badge should read. */
sealed interface MapHeaderState {
    /** No ROM, or no location read yet. */
    data class NoLiveLocation(val trust: RuntimeRomTrust) : MapHeaderState

    /** A raw read exists but cannot be turned into a trustworthy section. */
    data class Unavailable(
        val location: PlayerLocation,
        val reason: LocationUnavailableReason?,
        val mayReadLiveMemory: Boolean,
    ) : MapHeaderState

    /** An authoritative live location. */
    data class Live(
        val section: RegionMapSection,
        val location: PlayerLocation,
    ) : MapHeaderState {
        val isIndoors: Boolean get() = location.isIndoors
    }
}

/**
 * Presentation decisions for the Map screen, derived from view-model state.
 *
 * This is real production logic rather than a test double: [MapScreenView] consumes
 * these results to drive its views, and the tests exercise this same object, so the
 * two cannot drift apart.
 */
object MapScreenPresenter {

    /**
     * Resolve what the header should show.
     *
     * A live section is only authoritative when a valid read produced it. Otherwise
     * the screen must say why it cannot name the location.
     */
    fun headerState(
        location: PlayerLocation?,
        section: RegionMapSection?,
        trust: RuntimeRomTrust,
        reason: LocationUnavailableReason?,
    ): MapHeaderState = when {
        location == null || !location.isValid -> MapHeaderState.NoLiveLocation(trust)
        section == null -> MapHeaderState.Unavailable(location, reason, trust.mayReadLiveMemory)
        else -> MapHeaderState.Live(section, location)
    }

    /**
     * The canvas to draw, given the live region and the user's explicit overrides.
     *
     * [browseOverride] is non-null only after the user taps a region tab. While it
     * is null the canvas tracks the player's own region, so a later Johto -> Kanto
     * move (or a different game's region) is followed automatically instead of
     * being pinned to whichever region happened to be current first.
     */
    fun canvasSelection(
        strategy: LocationStrategy,
        browseOverride: RegionId?,
        liveSection: RegionMapSection?,
        current: MapCanvasSelection,
    ): MapCanvasSelection {
        if (browseOverride != null) {
            return MapCanvasSelection(browseOverride, followsLiveRegion = false)
        }

        val liveRegion = liveSection?.region
        val usable = liveRegion != null &&
            liveRegion.hasCanvas &&
            liveSection.presentable &&
            MapScreenPresenter.canvasRegions(strategy).contains(liveRegion)

        if (usable && liveRegion != null) {
            return MapCanvasSelection(liveRegion, followsLiveRegion = true)
        }

        // Nothing live to follow: keep drawing whatever canvas is already up, but
        // fall back to a region this strategy can actually render.
        val fallback = current.region.takeIf { canvasRegions(strategy).contains(it) }
            ?: defaultRegionFor(strategy)
        return MapCanvasSelection(fallback, followsLiveRegion = true)
    }

    /**
     * Selection state after a live location change.
     *
     * A live selection follows every change; a browsing selection is preserved; an
     * empty selection becomes live as soon as a section is available.
     */
    fun onLiveLocation(
        current: MapSelection,
        section: RegionMapSection?,
    ): MapSelection = when {
        current is MapSelection.Browsing -> current
        section == null -> MapSelection.None
        else -> MapSelection.Live(section)
    }

    /**
     * Selection state after the live location is invalidated (unreadable location,
     * trust loss, ROM unload or switch).
     *
     * The live selection and its detail content are dropped. A deliberate browsing
     * selection is kept, because it is not live data and erasing it would throw away
     * a view the user chose.
     */
    fun onLiveInvalidated(current: MapSelection): MapSelection =
        if (current is MapSelection.Browsing) current else MapSelection.None

    /** Selection state after the user taps a map tile or a region tab. */
    fun onBrowsed(section: RegionMapSection): MapSelection = MapSelection.Browsing(section)

    /** True when the detail sheet's content describes the player's own position. */
    fun isLive(selection: MapSelection): Boolean = selection is MapSelection.Live

    /** Regions this strategy can actually draw. */
    fun canvasRegions(strategy: LocationStrategy): Set<RegionId> =
        RegionId.entries.filter { region ->
            region.hasCanvas &&
                RegionMapDatabase.getSectionsForStrategy(strategy, region).isNotEmpty()
        }.toSet()

    /**
     * The canvas a strategy should open on when there is no live location yet.
     *
     * This replaces the legacy `gameId`/profile-name heuristics with the typed
     * strategy, and never falls through to Johto for an unknown game.
     */
    fun defaultRegionFor(strategy: LocationStrategy): RegionId = when (strategy) {
        LocationStrategy.HEART_AND_SOUL_205 -> RegionId.JOHTO
        LocationStrategy.EMERALD -> RegionId.HOENN
        LocationStrategy.FIRERED -> RegionId.KANTO
        LocationStrategy.UNVERIFIED -> RegionId.JOHTO
    }

    /**
     * Canvas sections for a strategy and region.
     *
     * Thin delegation so the screen has exactly one place to ask, and the tests can
     * assert what the screen will draw without rebuilding the view.
     */
    fun sectionsFor(strategy: LocationStrategy, region: RegionId): List<RegionMapSection> =
        RegionMapDatabase.getSectionsForStrategy(strategy, region)

    /**
     * The section that may carry a live player marker, or null when none may.
     *
     * This is the single marker gate, consumed by the view itself: a marker needs a
     * valid read, a section the active canvas actually draws, a region matching the
     * canvas being drawn, and a real canvas anchor. Browsing a Kanto canvas can
     * therefore never draw the player's Johto position over it, and a section DualDex
     * cannot place (Sinjoh, Alola, a dynamic area) never gets one at all.
     */
    fun markerSection(
        resolved: RegionMapSection?,
        playerLocation: PlayerLocation?,
        canvasRegion: RegionId,
    ): RegionMapSection? {
        if (playerLocation?.isValid != true) return null
        val section = resolved ?: return null
        if (!section.presentable) return null
        if (section.region != canvasRegion) return null
        if (section.gridX < 0 || section.gridY < 0) return null
        if (section.width < 1 || section.height < 1) return null
        return section
    }
}
