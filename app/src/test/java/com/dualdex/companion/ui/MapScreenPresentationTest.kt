package com.dualdex.companion.ui

import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.pokemon.LocationUnavailableReason
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import com.dualdex.romhack.RomCompatibilityStatus
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Map screen presentation: live-versus-browsing state, canvas selection, and the
 * live marker gate.
 *
 * These exercise [MapScreenPresenter], which is the production logic
 * [MapScreenView] and [RegionMapView] consume, rather than a test-local copy of it.
 * The earlier revision kept a parallel `markerSection` helper in this file, which
 * could pass while the real view was wired incorrectly.
 */
class MapScreenPresentationTest {

    private val hnsProfile = RomHackProfile(
        id = "heart_and_soul",
        name = "Pokemon Heart & Soul",
        baseGame = "Emerald",
        gameId = 8,
        engine = "pokeemerald-expansion",
        hasPhysSpecSplit = true,
    )

    private val emeraldProfile = RomHackProfile(
        id = "vanilla_emerald",
        name = "Pokemon Emerald",
        baseGame = "Emerald",
        gameId = 1,
    )

    private val fireRedProfile = RomHackProfile(
        id = "vanilla_firered",
        name = "Pokemon FireRed",
        baseGame = "FireRed",
        gameId = 2,
    )

    private fun location(group: Int, num: Int, valid: Boolean = true) = PlayerLocation(
        mapGroup = group,
        mapNum = num,
        warpId = 0,
        x = 10,
        y = 12,
        localX = 10,
        localY = 12,
        escapeMapGroup = 0,
        escapeMapNum = 0,
        isIndoors = false,
        isValid = valid,
    )

    private fun live(strategy: LocationStrategy, group: Int, num: Int) =
        RegionMapDatabase.resolveLocation(strategy, location(group, num))

    private fun noRom() = RuntimeRomTrust()

    // ------------------------------------------------------------------
    // Geometry contract
    // ------------------------------------------------------------------

    /**
     * The generator emits a rectangle origin plus extent, and every consumer
     * derives the visual centre itself. Route 29 spans x=15..18 and Route 30 spans
     * y=5..9 upstream; an origin emitted at the centre shifts them by half their
     * extent and makes them overlap neighbouring maps.
     */
    @Test
    fun multiTileRectanglesUseTheirTopLeftOriginNotTheirCentre() {
        val route29 = RegionMapDatabase.getSectionById("MAPSEC_ROUTE_29")
        assertNotNull(route29)
        assertEquals(15, route29!!.gridX)
        assertEquals(10, route29.gridY)
        assertEquals(4, route29.width)
        assertEquals(1, route29.height)

        val route30 = RegionMapDatabase.getSectionById("MAPSEC_ROUTE_30")
        assertEquals(14, route30!!.gridX)
        assertEquals(5, route30.gridY)
        assertEquals(1, route30.width)
        assertEquals(5, route30.height)

        // The spans must not reach into the neighbouring town.
        val newBark = RegionMapDatabase.getSectionById("MAPSEC_NEW_BARK_TOWN")!!
        assertFalse(
            "Route 29 must not overlap New Bark Town",
            route29.gridX <= newBark.gridX && newBark.gridX < route29.gridX + route29.width
        )

        val darkCave = RegionMapDatabase.getSectionById("MAPSEC_DARK_CAVE")!!
        assertEquals(15, darkCave.gridX)
        assertEquals(4, darkCave.gridY)
        assertEquals(3, darkCave.width)
        assertEquals(2, darkCave.height)
    }

    // ------------------------------------------------------------------
    // Canvas uses the active strategy's table
    // ------------------------------------------------------------------

    /**
     * Regression: the renderer used `getSections(region)`, which returns Heart &
     * Soul geometry for Kanto regardless of the running game, so a FireRed session
     * drew H&S Pallet Town at x=4 instead of its own x=5.
     */
    @Test
    fun canvasUsesTheActiveStrategyNotAlwaysHnsGeometry() {
        val hnsKanto = MapScreenPresenter.sectionsFor(
            LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO
        )
        val fireRedKanto = MapScreenPresenter.sectionsFor(
            LocationStrategy.FIRERED, RegionId.KANTO
        )

        val hnsPallet = hnsKanto.first { it.id == "MAPSEC_PALLET_TOWN" }
        val fireRedPallet = fireRedKanto.first { it.id == "PALLET_TOWN" }

        assertEquals("H&S Pallet Town", 4, hnsPallet.gridX)
        assertEquals("FireRed Pallet Town", 5, fireRedPallet.gridX)
        assertFalse(
            "the two games must not share a canvas table",
            hnsPallet.gridX == fireRedPallet.gridX
        )
    }

    @Test
    fun eachStrategyReceivesItsOwnCanvasRegions() {
        assertEquals(
            setOf(RegionId.JOHTO, RegionId.KANTO),
            MapScreenPresenter.canvasRegions(LocationStrategy.HEART_AND_SOUL_205)
        )
        assertEquals(
            setOf(RegionId.HOENN),
            MapScreenPresenter.canvasRegions(LocationStrategy.EMERALD)
        )
        assertEquals(
            setOf(RegionId.KANTO),
            MapScreenPresenter.canvasRegions(LocationStrategy.FIRERED)
        )
    }

    @Test
    fun defaultRegionComesFromTheStrategyNotAProfileNameHeuristic() {
        assertEquals(
            RegionId.JOHTO,
            MapScreenPresenter.defaultRegionFor(LocationStrategy.HEART_AND_SOUL_205)
        )
        assertEquals(
            RegionId.HOENN,
            MapScreenPresenter.defaultRegionFor(LocationStrategy.EMERALD)
        )
        assertEquals(
            RegionId.KANTO,
            MapScreenPresenter.defaultRegionFor(LocationStrategy.FIRERED)
        )
        // No fallthrough to Johto for an unknown game.
        assertEquals(
            RegionId.JOHTO,
            MapScreenPresenter.defaultRegionFor(LocationStrategy.UNVERIFIED)
        )
        assertFalse(
            LocationStrategy.UNVERIFIED.providesLiveLocation
        )
    }

    // ------------------------------------------------------------------
    // Follow-live canvas versus an explicit browsing override
    // ------------------------------------------------------------------

    private val initialCanvas = MapCanvasSelection(RegionId.JOHTO, followsLiveRegion = true)

    /**
     * Regression: the first live region was assigned to the browsing field, which
     * then never updated again, so a Johto start pinned the canvas to Johto even
     * after entering Kanto.
     */
    @Test
    fun canvasFollowsTheLiveRegionUntilTheUserBrowses() {
        val johtoLive = live(LocationStrategy.HEART_AND_SOUL_205, 0, 0)!!
        val afterJohto = MapScreenPresenter.canvasSelection(
            LocationStrategy.HEART_AND_SOUL_205, null, johtoLive, initialCanvas
        )
        assertEquals(RegionId.JOHTO, afterJohto.region)
        assertTrue(afterJohto.followsLiveRegion)

        // The player walks into Kanto: the canvas must follow without any user input.
        val kantoLive = live(LocationStrategy.HEART_AND_SOUL_205, 0, 31)!!
        val afterKanto = MapScreenPresenter.canvasSelection(
            LocationStrategy.HEART_AND_SOUL_205, null, kantoLive, afterJohto
        )
        assertEquals(
            "entering Kanto must move the canvas, not stay pinned to Johto",
            RegionId.KANTO,
            afterKanto.region
        )
        assertTrue(afterKanto.followsLiveRegion)
    }

    @Test
    fun aLiveLocationStillSelectsACanvasWhenTheScreenOpensLate() {
        // Nothing rendered yet: the canvas is on its default region.
        val late = MapScreenPresenter.canvasSelection(
            LocationStrategy.HEART_AND_SOUL_205,
            browseOverride = null,
            liveSection = live(LocationStrategy.HEART_AND_SOUL_205, 0, 31),
            current = initialCanvas,
        )
        assertEquals(
            "a location arriving after the Map tab opened must still pick its canvas",
            RegionId.KANTO,
            late.region
        )
    }

    @Test
    fun switchingStrategyReturnsTheCanvasToFollowingLive() {
        val previousBrowse = MapCanvasSelection(RegionId.JOHTO, followsLiveRegion = false)
        val emerald = MapScreenPresenter.canvasSelection(
            strategy = LocationStrategy.EMERALD,
            browseOverride = null,
            liveSection = null,
            current = previousBrowse,
        )
        assertEquals(
            "an Emerald session must not keep drawing the H&S Johto canvas",
            RegionId.HOENN,
            emerald.region
        )
        assertTrue(emerald.followsLiveRegion)
    }

    @Test
    fun explicitBrowsingOverrideWinsUntilItIsCleared() {
        val liveJohto = live(LocationStrategy.HEART_AND_SOUL_205, 0, 0)!!
        val browsed = MapScreenPresenter.canvasSelection(
            strategy = LocationStrategy.HEART_AND_SOUL_205,
            browseOverride = RegionId.KANTO,
            liveSection = liveJohto,
            current = initialCanvas,
        )
        assertEquals(RegionId.KANTO, browsed.region)
        assertFalse(browsed.followsLiveRegion)

        // Clearing the override restores following behaviour.
        val restored = MapScreenPresenter.canvasSelection(
            strategy = LocationStrategy.HEART_AND_SOUL_205,
            browseOverride = null,
            liveSection = liveJohto,
            current = browsed,
        )
        assertEquals(RegionId.JOHTO, restored.region)
        assertTrue(restored.followsLiveRegion)
    }

    @Test
    fun anUnpresentableLiveRegionDoesNotHijackTheCanvas() {
        // Sinjoh has a real region but no canvas DualDex draws.
        val sinjoh = live(LocationStrategy.HEART_AND_SOUL_205, 28, 5)!!
        assertEquals(RegionId.SINJOH, sinjoh.region)
        assertFalse(sinjoh.presentable)

        val chosen = MapScreenPresenter.canvasSelection(
            LocationStrategy.HEART_AND_SOUL_205, null, sinjoh, initialCanvas
        )
        assertTrue(
            "the canvas must stay on a drawable region",
            chosen.region.hasCanvas
        )
        assertEquals(RegionId.JOHTO, chosen.region)
    }

    // ------------------------------------------------------------------
    // Selection state transitions
    // ------------------------------------------------------------------

    private fun violetCity() = live(LocationStrategy.HEART_AND_SOUL_205, 0, 2)!!
    private fun newBarkTown() = live(LocationStrategy.HEART_AND_SOUL_205, 0, 0)!!

    /**
     * Regression: the live detail sheet was only displayed once, so walking from
     * New Bark Town to Violet City updated the heading while the sheet kept
     * describing New Bark Town.
     */
    @Test
    fun liveSelectionFollowsEveryLocationChange() {
        var selection: MapSelection = MapSelection.None

        selection = MapScreenPresenter.onLiveLocation(selection, newBarkTown())
        assertEquals(MapSelection.Live(newBarkTown()), selection)

        selection = MapScreenPresenter.onLiveLocation(selection, violetCity())
        assertTrue(selection is MapSelection.Live)
        assertEquals(
            "the detail sheet must describe the current location",
            "Violet City",
            (selection as MapSelection.Live).section.name
        )
        assertTrue(MapScreenPresenter.isLive(selection))
    }

    /**
     * Regression: invalidation reset a boolean but left the previous detail text and
     * the Center-highlighted selection on screen.
     */
    @Test
    fun invalidationClearsTheLiveSelection() {
        var selection: MapSelection = MapSelection.None
        selection = MapScreenPresenter.onLiveLocation(selection, newBarkTown())
        assertTrue(selection is MapSelection.Live)

        selection = MapScreenPresenter.onLiveInvalidated(selection)
        assertEquals(MapSelection.None, selection)
        assertFalse(MapScreenPresenter.isLive(selection))
    }

    /**
     * Regression: tapping a static tile set the flag false, and the next live update
     * then overwrote the user's deliberate selection.
     */
    @Test
    fun aDeliberateBrowsingSelectionSurvivesLiveUpdates() {
        val browsed = RegionMapDatabase.getSections(RegionId.KANTO)
            .first { it.id == "MAPSEC_PALLET_TOWN" }

        var selection: MapSelection = MapScreenPresenter.onBrowsed(browsed)
        assertFalse("browsing is not the player's position", MapScreenPresenter.isLive(selection))

        // A live update arrives.
        selection = MapScreenPresenter.onLiveLocation(selection, violetCity())
        assertTrue(selection is MapSelection.Browsing)
        assertEquals("Pallet Town", (selection as MapSelection.Browsing).section.name)

        // Invalidation must not erase it either: it is not live data.
        selection = MapScreenPresenter.onLiveInvalidated(selection)
        assertTrue(selection is MapSelection.Browsing)
        assertEquals("Pallet Town", (selection as MapSelection.Browsing).section.name)
    }

    /**
     * A tile tap is the screen's browsing entry point, and it must produce a
     * browsing selection. `RegionMapView` reports every deliberate tap through
     * `onSectionSelected`, which the screen routes to [MapScreenPresenter.onBrowsed];
     * a tap that left the selection empty would be silently overwritten by the next
     * live update.
     */
    @Test
    fun aTappedTileProducesABrowsingSelectionThatSurvivesLiveUpdates() {
        val tapped = RegionMapDatabase.getSections(RegionId.KANTO)
            .first { it.id == "MAPSEC_VIRIDIAN_CITY" }

        // Exactly what MapScreenView does on a tap.
        var selection: MapSelection = MapScreenPresenter.onBrowsed(tapped)
        assertTrue(selection is MapSelection.Browsing)
        assertFalse(MapScreenPresenter.isLive(selection))

        // A live update must not steal the selection.
        selection = MapScreenPresenter.onLiveLocation(selection, newBarkTown())
        assertEquals(tapped.id, (selection as MapSelection.Browsing).section.id)
    }

    @Test
    fun anEmptySelectionBecomesLiveWhenTheLocationArrives() {
        val selection = MapScreenPresenter.onLiveLocation(MapSelection.None, newBarkTown())
        assertTrue(selection is MapSelection.Live)
    }

    @Test
    fun anUnavailableLocationClearsAnEmptySelectionButKeepsBrowsing() {
        assertEquals(
            MapSelection.None,
            MapScreenPresenter.onLiveLocation(MapSelection.None, null)
        )
        val browsing = MapScreenPresenter.onBrowsed(violetCity())
        assertEquals(browsing, MapScreenPresenter.onLiveLocation(browsing, null))
    }

    // ------------------------------------------------------------------
    // Header state
    // ------------------------------------------------------------------

    @Test
    fun headerReportsNoLiveLocationWhenNothingIsRead() {
        val header = MapScreenPresenter.headerState(
            location = null,
            section = null,
            trust = noRom(),
            reason = null,
        )
        assertTrue(header is MapHeaderState.NoLiveLocation)
    }

    @Test
    fun headerReportsUnavailableForAnUnknownMapId() {
        val header = MapScreenPresenter.headerState(
            location = location(0, 4242),
            section = null,
            trust = noRom(),
            reason = LocationUnavailableReason.UNKNOWN_MAP_ID,
        )
        assertTrue(header is MapHeaderState.Unavailable)
        assertEquals(
            LocationUnavailableReason.UNKNOWN_MAP_ID,
            (header as MapHeaderState.Unavailable).reason
        )
    }

    @Test
    fun headerReportsLiveOnlyWithAValidReadAndASection() {
        val header = MapScreenPresenter.headerState(
            location = location(0, 0),
            section = newBarkTown(),
            trust = noRom(),
            reason = null,
        )
        assertTrue(header is MapHeaderState.Live)
        assertEquals("New Bark Town", (header as MapHeaderState.Live).section.name)

        // An invalid read must never produce a live header, even with a section.
        val invalid = MapScreenPresenter.headerState(
            location = location(0, 0, valid = false),
            section = newBarkTown(),
            trust = noRom(),
            reason = LocationUnavailableReason.INVALID_READ,
        )
        assertTrue(invalid is MapHeaderState.NoLiveLocation)
    }

    // ------------------------------------------------------------------
    // Live marker gate (the production gate the view consumes)
    // ------------------------------------------------------------------

    @Test
    fun liveMarkerIsDrawnOnlyOnItsOwnRegionCanvas() {
        val johtoLive = newBarkTown()
        assertNotNull(
            MapScreenPresenter.markerSection(johtoLive, location(0, 0), RegionId.JOHTO)
        )
        assertNull(
            "browsing Kanto must not draw the Johto position on it",
            MapScreenPresenter.markerSection(johtoLive, location(0, 0), RegionId.KANTO)
        )
        assertNull(
            MapScreenPresenter.markerSection(johtoLive, location(0, 0), RegionId.HOENN)
        )
    }

    @Test
    fun noLiveMarkerWithoutAValidReadOrSection() {
        assertNull(MapScreenPresenter.markerSection(newBarkTown(), null, RegionId.JOHTO))
        assertNull(
            MapScreenPresenter.markerSection(
                newBarkTown(), location(0, 0, valid = false), RegionId.JOHTO
            )
        )
        assertNull(MapScreenPresenter.markerSection(null, location(0, 0), RegionId.JOHTO))
    }

    @Test
    fun unpresentableSectionsNeverGetALiveMarker() {
        for ((group, num, region) in listOf(
            Triple(28, 5, RegionId.SINJOH),
            Triple(25, 0, RegionId.ALOLA),
            Triple(27, 0, RegionId.JOHTO),
        )) {
            val section = live(LocationStrategy.HEART_AND_SOUL_205, group, num)!!
            assertFalse(section.presentable)
            assertNull(
                MapScreenPresenter.markerSection(section, location(group, num), region)
            )
        }
    }

    // ------------------------------------------------------------------
    // View-model integration: invalidation and strategy changes
    // ------------------------------------------------------------------

    @Test
    fun invalidationClearsLiveStateAndTheSelection() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 0))
        assertNotNull(vm.resolvedLocation.value)

        var selection: MapSelection = MapSelection.None
        selection = MapScreenPresenter.onLiveLocation(selection, vm.resolvedLocation.value)

        vm.clearRomSession()
        selection = MapScreenPresenter.onLiveInvalidated(selection)

        assertNull(vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)
        assertEquals(MapSelection.None, selection)
        assertEquals(LocationStrategy.UNVERIFIED, vm.locationStrategy.value)
    }

    @Test
    fun changingTheStrategyClearsLiveStateAndResetsBrowsing() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 31))
        assertNotNull(vm.resolvedLocation.value)

        // Simulate the screen's strategy-change path.
        var browseOverride: RegionId? = RegionId.JOHTO
        vm.setProfile(emeraldProfile)
        browseOverride = null
        val canvas = MapScreenPresenter.canvasSelection(
            strategy = vm.locationStrategy.value,
            browseOverride = browseOverride,
            liveSection = vm.resolvedLocation.value,
            current = MapCanvasSelection(RegionId.KANTO, followsLiveRegion = false),
        )

        assertNull(vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)
        assertEquals(LocationStrategy.EMERALD, vm.locationStrategy.value)
        assertEquals(
            "an Emerald session must open on the Hoenn canvas",
            RegionId.HOENN,
            canvas.region
        )
    }

    @Test
    fun browsingNeverChangesRomIdentityTrustOrStrategy() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 0))

        val strategyBefore = vm.locationStrategy.value
        val profileBefore = vm.activeProfile.value
        val gameIdBefore = vm.activeGameId.value
        val romIdentityBefore = vm.activeRomIdentity.value
        val trustBefore = vm.runtimeRomTrust.value

        // The screen's browsing action: canvas selection plus section lookup only.
        for (region in RegionId.entries) {
            MapScreenPresenter.sectionsFor(vm.locationStrategy.value, region)
        }
        MapScreenPresenter.canvasSelection(
            vm.locationStrategy.value, RegionId.KANTO, vm.resolvedLocation.value, initialCanvas
        )

        assertEquals(strategyBefore, vm.locationStrategy.value)
        assertEquals(profileBefore, vm.activeProfile.value)
        assertEquals(gameIdBefore, vm.activeGameId.value)
        assertEquals(romIdentityBefore, vm.activeRomIdentity.value)
        assertEquals(trustBefore, vm.runtimeRomTrust.value)
    }

    @Test
    fun onlyCanvasBackedRegionsAreBrowsable() {
        assertTrue(RegionId.JOHTO.hasCanvas)
        assertTrue(RegionId.KANTO.hasCanvas)
        assertTrue(RegionId.HOENN.hasCanvas)
        assertFalse(RegionId.SINJOH.hasCanvas)
        assertFalse(RegionId.ALOLA.hasCanvas)
    }

    @Test
    fun recognisedButUnverifiedProfileGrantsNoLivePresentation() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)

        val header = MapScreenPresenter.headerState(
            location = null,
            section = vm.resolvedLocation.value,
            trust = vm.runtimeRomTrust.value,
            reason = null,
        )
        assertTrue(header is MapHeaderState.NoLiveLocation)
        assertEquals(
            RomCompatibilityStatus.UNSUPPORTED,
            (header as MapHeaderState.NoLiveLocation).trust.status
        )
    }

    // ------------------------------------------------------------------
    // Event-level coverage
    // ------------------------------------------------------------------

    /**
     * Drives the exact sequence of production calls `MapScreenView` makes for each
     * screen event, so the *wiring* is covered and not only the presenter functions.
     *
     * Every method below mirrors one handler in `MapScreenView` call-for-call; a
     * change to the order or the arguments in the real handler should be reflected
     * here, and the invariants asserted at the end of the event tests are the ones
     * the review found broken:
     *
     *  * pressing Center must not convert a live location into a static selection;
     *  * a canvas change must not leave a highlight or details from another canvas;
     *  * a game switch must not reuse another game's coordinates.
     */
    private class ScreenEventHarness(
        strategy: LocationStrategy,
        val viewModel: CompanionViewModel,
    ) {
        var selection: MapSelection = MapSelection.None
            private set
        var canvas: MapCanvasSelection = MapCanvasSelection(
            region = MapScreenPresenter.defaultRegionFor(strategy),
            followsLiveRegion = true,
        )
            private set
        private var browseOverride: RegionId? = null

        /** The currently highlighted section, recomputed exactly as the view does. */
        var highlight: RegionMapSection? = null
            private set

        /** Mirrors `MapScreenView.renderLiveState()`. */
        fun liveState() {
            val strategy = viewModel.locationStrategy.value
            val section = viewModel.resolvedLocation.value

            canvas = MapScreenPresenter.canvasSelection(
                strategy = strategy,
                browseOverride = browseOverride,
                liveSection = section,
                current = canvas,
            )

            selection = when (
                MapScreenPresenter.headerState(
                    location = viewModel.playerLocation.value,
                    section = section,
                    trust = viewModel.runtimeRomTrust.value,
                    reason = viewModel.locationUnavailableReason.value,
                )
            ) {
                is MapHeaderState.Live ->
                    MapScreenPresenter.onLiveLocation(selection, section)
                else -> MapScreenPresenter.onLiveInvalidated(selection)
            }

            publishSelection()
        }

        /**
         * Mirrors `RegionMapView.centerOnPlayer()`: a viewport change that reports no
         * section. That the renderer really reports nothing is enforced structurally
         * by [centerOnPlayerReportsNoSelection].
         */
        fun center() {
            publishSelection()
        }

        /**
         * Mirrors `MapScreenView`'s `onSectionSelected` handler, which treats every
         * reported section as a deliberate browsing gesture.
         */
        private fun tapLikeEvent(section: RegionMapSection) {
            section.region?.takeIf { it.hasCanvas }?.let { region ->
                browseOverride = region
                canvas = MapScreenPresenter.canvasSelection(
                    strategy = viewModel.locationStrategy.value,
                    browseOverride = browseOverride,
                    liveSection = viewModel.resolvedLocation.value,
                    current = canvas,
                )
            }
            selection = MapScreenPresenter.onBrowsed(section)
        }

        /**
         * Mirrors `RegionMapView.handleTap()` -> `onSectionSelected`.
         *
         * Tapping a tile on a canvas can only produce a section that canvas draws,
         * so the browsed canvas is the section's own region. `MapScreenView` keeps
         * them consistent so a deliberate tap is never silently discarded.
         */
        fun tapTile(section: RegionMapSection) {
            MapScreenPresenter.sectionReportedByTap(section)?.let { tapLikeEvent(it) }
            publishSelection()
        }

        /** Mirrors the region-tab handler. */
        fun selectRegionTab(region: RegionId) {
            browseOverride = region
            canvas = MapScreenPresenter.canvasSelection(
                strategy = viewModel.locationStrategy.value,
                browseOverride = browseOverride,
                liveSection = viewModel.resolvedLocation.value,
                current = canvas,
            )
            selection = MapSelection.None
            publishSelection()
        }

        /** Mirrors `MapScreenView.onStrategyChanged()`. */
        fun strategyChanged() {
            browseOverride = null
            canvas = MapCanvasSelection(canvas.region, followsLiveRegion = true)
            liveState()
        }

        /** Mirrors `MapScreenView.publishSelection()`. */
        private fun publishSelection() {
            val strategy = viewModel.locationStrategy.value
            selection = MapScreenPresenter.reconcileSelection(selection, strategy, canvas.region)
            highlight = MapScreenPresenter.drawableHighlight(selection, strategy, canvas.region)
        }
    }

    private fun harnessFor(profile: RomHackProfile): ScreenEventHarness {
        val vm = CompanionViewModel()
        vm.setProfile(profile)
        return ScreenEventHarness(LocationStrategy.forProfile(profile), vm)
    }

    /**
     * Regression: pressing Center reported a section selection, so the screen
     * converted the player's own live position into a static browsing selection.
     * Details then froze at that location and survived invalidation, because
     * browsing is intentionally preserved.
     */
    @Test
    fun pressingCenterKeepsFollowingTheLiveLocation() {
        val harness = harnessFor(hnsProfile)
        val vm = harness.viewModel

        vm.updatePlayerLocation(location(0, 0))
        harness.liveState()
        assertTrue("New Bark Town should be live", harness.selection is MapSelection.Live)
        assertEquals("New Bark Town", liveName(harness))

        // Press Center: viewport only.
        harness.center()
        assertTrue(
            "Center must not turn the live position into a browsing selection",
            harness.selection is MapSelection.Live
        )

        // The player then walks to Violet City.
        vm.updatePlayerLocation(location(0, 2))
        harness.liveState()
        assertEquals(
            "details must follow the player after Center",
            "Violet City",
            liveName(harness)
        )
        assertTrue(harness.selection is MapSelection.Live)

        // And invalidation must clear them.
        vm.clearRomSession()
        harness.liveState()
        assertEquals(MapSelection.None, harness.selection)
        assertNull("the highlight must be cleared too", harness.highlight)
    }

    /**
     * A genuine tile tap must still behave as browsing: live updates from the same
     * game must not steal it, and it keeps its highlight.
     */
    @Test
    fun aGenuineTileTapStillBrowsesAndSurvivesLiveUpdates() {
        val harness = harnessFor(hnsProfile)
        val vm = harness.viewModel

        vm.updatePlayerLocation(location(0, 0))
        harness.liveState()

        val browsed = MapScreenPresenter.sectionsFor(
            LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO
        ).first { it.id == "MAPSEC_PALLET_TOWN" }

        harness.tapTile(browsed)
        assertTrue(harness.selection is MapSelection.Browsing)
        assertEquals("Pallet Town", browsedName(harness))
        assertEquals(
            "the browsed section must be highlighted on the canvas that draws it",
            RegionId.KANTO,
            harness.canvas.region
        )
        assertNotNull(harness.highlight)

        // A live update in the same game must not steal it.
        vm.updatePlayerLocation(location(0, 2))
        harness.liveState()
        assertTrue(harness.selection is MapSelection.Browsing)
        assertEquals("Pallet Town", browsedName(harness))
        assertNotNull(harness.highlight)

        // Nor must an invalid read, as long as the game is still loaded.
        vm.updatePlayerLocation(location(0, 2, valid = false))
        harness.liveState()
        assertTrue(harness.selection is MapSelection.Browsing)
        assertEquals("Pallet Town", browsedName(harness))
    }

    /**
     * Unloading the ROM leaves no map table at all, so the static browsing selection
     * is dropped with everything else. This is intentional and distinct from the
     * Center bug: there the *live* position had been misclassified as browsing and
     * therefore outlived its own invalidation.
     */
    @Test
    fun unloadingTheRomDropsBrowsingBecauseNoMapTableRemains() {
        val harness = harnessFor(hnsProfile)
        val vm = harness.viewModel

        vm.updatePlayerLocation(location(0, 0))
        harness.liveState()
        val browsed = MapScreenPresenter.sectionsFor(
            LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO
        ).first { it.id == "MAPSEC_PALLET_TOWN" }
        harness.tapTile(browsed)
        assertTrue(harness.selection is MapSelection.Browsing)

        vm.clearRomSession()
        harness.liveState()

        assertEquals(LocationStrategy.UNVERIFIED, vm.locationStrategy.value)
        assertEquals(
            "with no map table there is nothing drawable to keep selected",
            MapSelection.None,
            harness.selection
        )
        assertNull(harness.highlight)
    }

    /**
     * Regression: the region-tab handler cleared the text selection but not the
     * renderer's `selectedSection`, so a Johto rectangle stayed highlighted over
     * Kanto until some later live emission happened to repaint it.
     */
    @Test
    fun changingRegionClearsTheHighlightWithoutWaitingForALiveUpdate() {
        val harness = harnessFor(hnsProfile)
        val vm = harness.viewModel

        vm.updatePlayerLocation(location(0, 0))
        harness.liveState()
        assertNotNull("a live Johto section should be highlighted", harness.highlight)
        assertEquals(RegionId.JOHTO, harness.highlight!!.region)

        // Tab to Kanto: no live update follows.
        harness.selectRegionTab(RegionId.KANTO)

        assertEquals(RegionId.KANTO, harness.canvas.region)
        assertEquals(MapSelection.None, harness.selection)
        assertNull(
            "the Johto highlight must not remain drawn over the Kanto canvas",
            harness.highlight
        )
    }

    /**
     * Regression: a browsing selection was published on a different game's canvas
     * with no membership check, so H&S Pallet Town's (4,11) highlight could be drawn
     * over FireRed's Kanto canvas, where Pallet Town is at (5,11).
     */
    @Test
    fun switchingStrategyDoesNotReuseAnotherGamesCoordinates() {
        val harness = harnessFor(hnsProfile)
        val vm = harness.viewModel

        val hnsPallet = MapScreenPresenter.sectionsFor(
            LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO
        ).first { it.id == "MAPSEC_PALLET_TOWN" }
        assertEquals(4, hnsPallet.gridX)

        harness.selectRegionTab(RegionId.KANTO)
        harness.tapTile(hnsPallet)
        assertNotNull(harness.highlight)
        assertEquals(4, harness.highlight!!.gridX)

        // Switch to FireRed, which also uses the Kanto region.
        vm.setProfile(fireRedProfile)
        harness.strategyChanged()

        assertEquals(LocationStrategy.FIRERED, vm.locationStrategy.value)
        assertEquals(RegionId.KANTO, harness.canvas.region)
        if (harness.highlight != null) {
            assertEquals(
                "a highlight on the FireRed canvas must use FireRed geometry",
                5,
                harness.highlight!!.gridX
            )
            assertEquals("PALLET_TOWN", harness.highlight!!.id)
        }
    }

    @Test
    fun aBrowsingSelectionOnAnotherCanvasIsDroppedNotReused() {
        val hnsJohto = MapScreenPresenter.sectionsFor(
            LocationStrategy.HEART_AND_SOUL_205, RegionId.JOHTO
        ).first { it.id == "MAPSEC_NEW_BARK_TOWN" }

        // A Johto browsing selection cannot be drawn on the FireRed Kanto canvas.
        val reconciled = MapScreenPresenter.reconcileSelection(
            MapSelection.Browsing(hnsJohto),
            LocationStrategy.FIRERED,
            RegionId.KANTO,
        )
        assertEquals(MapSelection.None, reconciled)

        assertNull(
            MapScreenPresenter.drawableHighlight(
                MapSelection.Browsing(hnsJohto),
                LocationStrategy.FIRERED,
                RegionId.KANTO,
            )
        )
    }

    /**
     * The two games express Kanto differently (`MAPSEC_*` in Heart & Soul,
     * region-agnostic ids in FireRed), so a browsing selection does **not** transfer
     * between them. It must be dropped rather than re-resolved onto coordinates it
     * does not own.
     */
    @Test
    fun aBrowsingSelectionIsDroppedWhenTheCanvasIdentityDiffers() {
        val hnsPallet = MapScreenPresenter.sectionsFor(
            LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO
        ).first { it.id == "MAPSEC_PALLET_TOWN" }
        assertEquals(4, hnsPallet.gridX)

        val reconciled = MapScreenPresenter.reconcileSelection(
            MapSelection.Browsing(hnsPallet),
            LocationStrategy.FIRERED,
            RegionId.KANTO,
        )
        assertEquals(
            "the H&S identity is not on the FireRed canvas and must not be reused",
            MapSelection.None,
            reconciled
        )
        assertNull(
            MapScreenPresenter.drawableHighlight(
                reconciled, LocationStrategy.FIRERED, RegionId.KANTO
            )
        )
    }

    @Test
    fun aBrowsingSelectionIsPreservedWhenTheSameCanvasStillDrawsIt() {
        val hnsPallet = MapScreenPresenter.sectionsFor(
            LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO
        ).first { it.id == "MAPSEC_PALLET_TOWN" }

        // Same strategy, same canvas: the selection is unchanged and still drawable.
        val reconciled = MapScreenPresenter.reconcileSelection(
            MapSelection.Browsing(hnsPallet),
            LocationStrategy.HEART_AND_SOUL_205,
            RegionId.KANTO,
        )
        assertTrue(reconciled is MapSelection.Browsing)
        assertEquals(4, (reconciled as MapSelection.Browsing).section.gridX)
        assertEquals(
            4,
            MapScreenPresenter.drawableHighlight(
                reconciled, LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO
            )!!.gridX
        )
    }

    @Test
    fun highlightIsEmptyForEveryUnpresentableOrForeignSelection() {
        for ((group, num, region) in listOf(
            Triple(28, 5, RegionId.SINJOH),
            Triple(25, 0, RegionId.ALOLA),
            Triple(27, 0, RegionId.JOHTO),
        )) {
            val section = live(LocationStrategy.HEART_AND_SOUL_205, group, num)!!
            assertNull(
                MapScreenPresenter.drawableHighlight(
                    MapSelection.Live(section), LocationStrategy.HEART_AND_SOUL_205, region
                )
            )
            assertNull(
                MapScreenPresenter.drawableHighlight(
                    MapSelection.Browsing(section),
                    LocationStrategy.HEART_AND_SOUL_205,
                    RegionId.JOHTO,
                )
            )
        }
        assertNull(
            MapScreenPresenter.drawableHighlight(
                MapSelection.None, LocationStrategy.HEART_AND_SOUL_205, RegionId.JOHTO
            )
        )
    }

    /**
     * Structural guard on the production renderer.
     *
     * The Center bug was a *delegation* defect, not a presenter-logic defect: the
     * presenter was already correct, but `RegionMapView.centerOnPlayer()` reported a
     * section selection that `MapScreenView` then treated as a deliberate tile tap.
     * A JVM unit test cannot instantiate an Android View, so a callback-behaviour
     * test against the buggy code would have passed. This inspects the renderer's own
     * source instead, which is where the defect lived.
     *
     * It is a structural check, so it guarantees exactly one thing: `centerOnPlayer`
     * does not reach the browsing callback or the drawn selection. The event tests
     * above cover the resulting behaviour.
     */
    @Test
    fun centerOnPlayerReportsNoSelection() {
        val source = rendererViewSource()

        val centering = extractFunction(source, "fun centerOnPlayer(")
        assertNotNull("RegionMapView.centerOnPlayer must still exist", centering)
        assertFalse(
            "centerOnPlayer must not invoke the browsing callback; pressing Center is " +
                "a viewport gesture, and reporting a section makes the screen freeze " +
                "the player's own live position as a static browsing selection",
            centering!!.contains("onSectionSelected")
        )
        assertFalse(
            "centerOnPlayer must not assign the drawn selection either",
            centering.contains("selectedSection")
        )
        assertTrue(
            "centerOnPlayer must still centre on the live marker and reposition the canvas",
            centering.contains("liveMarkerSection") && centering.contains("clampTranslation")
        )
    }

    /**
     * Self-check for [extractFunction].
     *
     * The guard above is meaningless if the extractor returns the wrong range. These
     * assertions run it against known functions, so it cannot pass by extracting
     * nothing or by swallowing a neighbouring function.
     */
    @Test
    fun sourceExtractorIsTrustworthy() {
        val source = rendererViewSource()

        // The tap path legitimately reports a browsing selection.
        val tap = extractFunction(source, "private fun handleTap(")
        assertNotNull("the tap handler must be extractable", tap)
        assertTrue(
            "the tap handler is where a browsing selection is legitimate",
            tap!!.contains("onSectionSelected")
        )
        assertTrue(tap.contains("sectionReportedByTap"))
        assertFalse(
            "extraction must stop at the end of the function",
            tap.contains("fun resetZoom(")
        )
        assertNull(
            "an absent function must extract as null",
            extractFunction(source, "fun noSuchFunctionExistsHere(")
        )
    }

    private fun rendererViewSource(): String {
        val candidates = listOf(
            java.io.File("src/main/java/com/dualdex/companion/ui/RegionMapView.kt"),
            java.io.File("app/src/main/java/com/dualdex/companion/ui/RegionMapView.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
        assertNotNull(
            "RegionMapView source must be readable to guard the centering event; " +
                "looked in ${candidates.joinToString { it.path }}",
            file
        )
        return file!!.readText()
    }

    /**
     * Extracts one function's *code* by brace matching from its declaration.
     *
     * Line and block comments are removed first, so a comment that explains why the
     * function avoids something cannot itself trip the guard.
     */
    private fun extractFunction(source: String, declaration: String): String? {
        val start = source.indexOf(declaration)
        if (start < 0) return null
        val open = source.indexOf('{', start)
        if (open < 0) return null
        var depth = 0
        var end = -1
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = index + 1
                        break
                    }
                }
            }
        }
        if (end < 0) return null
        return stripComments(source.substring(start, end))
    }

    private fun stripComments(code: String): String = code
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("//[^\\n]*"), " ")

    private fun liveName(harness: ScreenEventHarness): String =
        (harness.selection as MapSelection.Live).section.name

    private fun browsedName(harness: ScreenEventHarness): String =
        (harness.selection as MapSelection.Browsing).section.name
}
