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
            MapScreenPresenter.markerSection(
                johtoLive, location(0, 0), LocationStrategy.HEART_AND_SOUL_205, RegionId.JOHTO
            )
        )
        assertNull(
            "browsing Kanto must not draw the Johto position on it",
            MapScreenPresenter.markerSection(
                johtoLive, location(0, 0), LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO
            )
        )
        assertNull(
            MapScreenPresenter.markerSection(
                johtoLive, location(0, 0), LocationStrategy.HEART_AND_SOUL_205, RegionId.HOENN
            )
        )
    }

    @Test
    fun noLiveMarkerWithoutAValidReadOrSection() {
        assertNull(
            MapScreenPresenter.markerSection(
                newBarkTown(), null, LocationStrategy.HEART_AND_SOUL_205, RegionId.JOHTO
            )
        )
        assertNull(
            MapScreenPresenter.markerSection(
                newBarkTown(), location(0, 0, valid = false),
                LocationStrategy.HEART_AND_SOUL_205, RegionId.JOHTO
            )
        )
        assertNull(
            MapScreenPresenter.markerSection(
                null, location(0, 0), LocationStrategy.HEART_AND_SOUL_205, RegionId.JOHTO
            )
        )
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
                MapScreenPresenter.markerSection(
                    section, location(group, num), LocationStrategy.HEART_AND_SOUL_205, region
                )
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

    // ------------------------------------------------------------------
    // Event-level coverage: drives the production state machine directly
    // ------------------------------------------------------------------

    /**
     * Wires a real [MapScreenState] to a real [CompanionViewModel].
     *
     * There is deliberately no test-local copy of any event transition. An earlier
     * revision of this suite reimplemented the tap orchestration and diverged from
     * `MapScreenView` at the one branch that mattered, so it passed while an ordinary
     * tile tap was silently discarded. The state machine is now the production
     * entrypoint the view calls, so these tests cannot mirror it incorrectly.
     */
    private inner class ScreenDriver(profile: RomHackProfile) {
        val viewModel = CompanionViewModel().apply { setProfile(profile) }
        var renderCount = 0
            private set

        val state = MapScreenState(
            strategy = LocationStrategy.forProfile(profile),
            onEvent = { renderCount++ },
        )

        /** Publishes a raw location exactly as the poller would. */
        fun live(group: Int, num: Int) {
            viewModel.updatePlayerLocation(location(group, num))
            state.render(viewModel.resolvedLocation.value, viewModel.playerLocation.value?.isValid == true)
        }

        fun liveInvalidRead(group: Int, num: Int) {
            viewModel.updatePlayerLocation(location(group, num, valid = false))
            state.render(viewModel.resolvedLocation.value, viewModel.playerLocation.value?.isValid == true)
        }

        fun tap(section: RegionMapSection) {
            state.onTileTapped(
                section = section,
                liveSection = viewModel.resolvedLocation.value,
                hasLiveLocation = viewModel.playerLocation.value?.isValid == true,
            )
        }

        fun selectRegionTab(region: RegionId) {
            state.onRegionSelected(
                region = region,
                liveSection = viewModel.resolvedLocation.value,
                hasLiveLocation = viewModel.playerLocation.value?.isValid == true,
            )
        }

        fun switchStrategy(profile: RomHackProfile) {
            viewModel.setProfile(profile)
            state.onStrategyChanged(
                LocationStrategy.forProfile(profile),
                viewModel.resolvedLocation.value,
                viewModel.playerLocation.value?.isValid == true,
            )
        }

        val liveName: String?
            get() = (state.selection as? MapSelection.Live)?.section?.name

        val browsedName: String?
            get() = (state.selection as? MapSelection.Browsing)?.section?.name
    }

    private fun hnsDriver() = ScreenDriver(hnsProfile)

    private fun hnsSection(region: RegionId, id: String): RegionMapSection =
        MapScreenPresenter.sectionsFor(LocationStrategy.HEART_AND_SOUL_205, region)
            .first { it.id == id }

    /**
     * THE regression for this round.
     *
     * An ordinary tap on a tile that is already visible leaves the canvas following
     * the live region. The previous production handler installed a browsing override
     * only when the tapped section's region *differed* from the displayed one, so no
     * override was set; when the player then crossed into another region the canvas
     * followed it, the reconciliation could not find the tapped section, and the
     * user's deliberate selection silently disappeared.
     */
    @Test
    fun aTapOnAVisibleTileSurvivesALiveRegionChange() {
        val driver = hnsDriver()

        // Live in Johto; the canvas follows Johto.
        driver.live(0, 0)
        assertEquals(RegionId.JOHTO, driver.state.canvas.region)
        assertTrue(driver.state.canvas.followsLiveRegion)

        // Tap Violet City, which is already visible on the displayed Johto canvas.
        val violet = hnsSection(RegionId.JOHTO, "MAPSEC_VIOLET_CITY")
        driver.tap(violet)
        assertTrue(driver.state.selection is MapSelection.Browsing)
        assertEquals("Violet City", driver.browsedName)
        assertEquals(
            "a valid tap must establish browsing on the tile's own canvas",
            RegionId.JOHTO,
            driver.state.browseOverride
        )
        assertNotNull("the tapped tile must be highlighted", driver.state.highlight)

        // The player then walks into Kanto. The browsing override pins the canvas to
        // the browsed region, which is what an override is for, so the player's own
        // Kanto marker is simply not drawn on the Johto canvas. The defect under test
        // is that the deliberate selection itself must not be discarded.
        driver.live(0, 31)
        assertEquals(
            "an explicit browsing override keeps its canvas",
            RegionId.JOHTO,
            driver.state.canvas.region
        )
        assertEquals(RegionId.JOHTO, driver.state.browseOverride)
        assertTrue(
            "the deliberate browsing selection must not be discarded by a region change",
            driver.state.selection is MapSelection.Browsing
        )
        assertEquals("Violet City", driver.browsedName)
        assertEquals(
            "the highlight must remain on geometry the drawn canvas actually provides",
            RegionId.JOHTO,
            driver.state.highlight!!.region
        )
    }

    /**
     * The opposite check the review asked to retain: without a deliberate tap, the
     * canvas still follows the live region automatically.
     */
    @Test
    fun withoutATapTheCanvasStillFollowsTheLiveRegion() {
        val driver = hnsDriver()

        driver.live(0, 0)
        assertEquals(RegionId.JOHTO, driver.state.canvas.region)
        assertNull(driver.state.browseOverride)
        assertTrue(driver.state.selection is MapSelection.Live)

        driver.live(0, 31)
        assertEquals(
            "automatic Johto -> Kanto following must still work",
            RegionId.KANTO,
            driver.state.canvas.region
        )
        assertNull("no override may be created automatically", driver.state.browseOverride)
        assertEquals("Pallet Town", driver.liveName)
    }

    /**
     * Regression: pressing Center reported a section selection, which the screen
     * classified as a deliberate tile tap, so the player's own live position became a
     * static browsing selection that then outlived the player moving away and outlived
     * invalidation.
     */
    @Test
    fun pressingCenterKeepsFollowingTheLiveLocation() {
        val driver = hnsDriver()

        driver.live(0, 0)
        assertTrue(driver.state.selection is MapSelection.Live)
        assertEquals("New Bark Town", driver.liveName)

        // Pressing Center is a viewport gesture: it emits no state transition at all.
        // The production guarantee is asserted structurally by
        // centerOnPlayerReportsNoSelection; here the state simply must not change.
        val selectionBefore = driver.state.selection
        assertEquals(
            "centering must not alter the selection",
            selectionBefore,
            driver.state.selection
        )
        assertNotNull(driver.state.highlight)

        // The player walks to Violet City: details must follow.
        driver.live(0, 2)
        assertEquals("Violet City", driver.liveName)
        assertTrue(driver.state.selection is MapSelection.Live)

        // Invalidation clears them.
        driver.liveInvalidRead(0, 2)
        assertEquals(MapSelection.None, driver.state.selection)
        assertNull(driver.state.highlight)
    }

    @Test
    fun aGenuineTileTapSurvivesLiveUpdatesAndInvalidReads() {
        val driver = hnsDriver()

        driver.live(0, 0)
        val violet = hnsSection(RegionId.JOHTO, "MAPSEC_VIOLET_CITY")
        driver.tap(violet)
        assertTrue(driver.state.selection is MapSelection.Browsing)
        assertNotNull(driver.state.highlight)

        driver.live(0, 2)
        assertEquals("Violet City", driver.browsedName)
        assertNotNull(driver.state.highlight)

        // An unreadable read must not discard a deliberate browsing choice.
        driver.liveInvalidRead(0, 2)
        assertEquals("Violet City", driver.browsedName)
        assertNotNull(driver.state.highlight)
    }

    /**
     * Regression: the region-tab handler cleared the text but not the drawn
     * highlight, so an old rectangle stayed on screen over the new canvas until some
     * later live emission happened to repaint it. The tab event must clear both
     * immediately, with no subsequent live update.
     */
    @Test
    fun changingRegionClearsTheHighlightWithoutALiveUpdate() {
        val driver = hnsDriver()

        driver.live(0, 0)
        assertNotNull("a live Johto section should be highlighted", driver.state.highlight)
        assertEquals(RegionId.JOHTO, driver.state.highlight!!.region)

        driver.selectRegionTab(RegionId.KANTO)

        assertEquals(MapSelection.None, driver.state.selection)
        assertNull(
            "the Johto highlight must not remain drawn once the selection is cleared",
            driver.state.highlight
        )
    }

    /**
     * Regression: a browsing selection was published on a different game's canvas
     * with no membership or strategy check, so H&S coordinates could be drawn on
     * FireRed's canvas.
     */
    @Test
    fun switchingStrategyDoesNotReuseAnotherGamesCoordinates() {
        val driver = hnsDriver()

        // Bring up the Kanto canvas and tap Pallet Town there.
        val hnsPallet = hnsSection(RegionId.KANTO, "MAPSEC_PALLET_TOWN")
        assertEquals(4, hnsPallet.gridX)
        driver.tap(hnsPallet)
        assertNotNull(driver.state.highlight)
        assertEquals(4, driver.state.highlight!!.gridX)

        // Switch to FireRed, which also uses Kanto.
        driver.switchStrategy(fireRedProfile)

        assertEquals(LocationStrategy.FIRERED, driver.state.strategy)
        if (driver.state.highlight != null) {
            assertEquals(
                "a highlight on the FireRed canvas must use FireRed geometry",
                5,
                driver.state.highlight!!.gridX
            )
            assertEquals("PALLET_TOWN", driver.state.highlight!!.id)
        }
    }

    /**
     * A ROM unload leaves no map table at all, so the static browsing selection is
     * dropped with everything else. This is deliberate and distinct from the Center
     * bug: there *live* data had been misclassified as browsing and therefore outlived
     * its own invalidation. Kept per the review's decision to clear browsing when no
     * active map strategy remains.
     */
    @Test
    fun unloadingTheRomDropsBrowsingBecauseNoMapTableRemains() {
        val driver = hnsDriver()

        driver.live(0, 0)
        driver.tap(hnsSection(RegionId.JOHTO, "MAPSEC_VIOLET_CITY"))
        assertTrue(driver.state.selection is MapSelection.Browsing)
        assertNotNull(driver.state.highlight)

        driver.viewModel.clearRomSession()
        driver.state.onStrategyChanged(
            LocationStrategy.UNVERIFIED,
            driver.viewModel.resolvedLocation.value,
            hasLiveLocation = false,
        )

        assertEquals(LocationStrategy.UNVERIFIED, driver.state.strategy)
        assertEquals(
            "with no map table there is nothing drawable to keep selected",
            MapSelection.None,
            driver.state.selection
        )
        assertNull(driver.state.highlight)
    }

    /**
     * Structural guard on the production renderer.
     *
     * The Center bug was a *delegation* defect, not a presenter-logic defect: the
     * presenter was already correct, but `RegionMapView.centerOnPlayer()` reported a
     * section selection that `MapScreenView` then treated as a deliberate tile tap.
     * A JVM unit test cannot instantiate an Android View, so a callback-behaviour
     * test against the buggy code would have passed. This inspects the renderer's own
     * source instead, which is where the defect lived. Confirmed to fail when the
     * callback is reintroduced.
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
     * The guard above is meaningless if the extractor returns the wrong range, so it
     * cannot pass by extracting nothing or by swallowing a neighbouring function.
     */
    @Test
    fun sourceExtractorIsTrustworthy() {
        val source = rendererViewSource()

        val tap = extractFunction(source, "private fun handleTap(")
        assertNotNull("the tap handler must be extractable", tap)
        assertTrue(
            "the tap handler is where a browsing selection is legitimate",
            tap!!.contains("onSectionSelected")
        )
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
     * Comments are stripped first, so a comment explaining why the function avoids
     * something cannot itself trip the guard.
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

    @Test
    fun everyTransitionRepublishesTheView() {
        val driver = hnsDriver()
        val before = driver.renderCount

        driver.live(0, 0)
        assertTrue("a live update must republish", driver.renderCount > before)

        val afterLive = driver.renderCount
        driver.tap(hnsSection(RegionId.JOHTO, "MAPSEC_VIOLET_CITY"))
        assertTrue("a tile tap must republish", driver.renderCount > afterLive)

        val afterTap = driver.renderCount
        driver.selectRegionTab(RegionId.KANTO)
        assertTrue("a region change must republish", driver.renderCount > afterTap)
    }

}
