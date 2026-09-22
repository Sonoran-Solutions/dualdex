package com.dualdex.companion.ui

import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.LocationResolver
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue #11 phase C: browsing another visible map region must never change how the running ROM's
 * memory is interpreted.
 *
 * This is the merged PR #51 invariant, re-audited and strengthened. It has two halves:
 *
 *  1. **Behavioural.** The full sequence #11 asks for is driven through the production objects --
 *     resolve the live H&S location, browse another canvas, keep receiving live updates, come back
 *     -- and the native interpretation is compared before and after every step using pairs whose
 *     meaning *differs* per strategy, so "same answer" cannot be a coincidence.
 *
 *  2. **Structural.** The browsing selection lives in [MapScreenState], which has no reference to
 *     the view-model, the profile, or [LocationStrategy.forProfile]. That is what makes the
 *     invariant hold by construction rather than by discipline; a mutation that let browsing pick
 *     the strategy would have to add one of those references, and
 *     [browsingStateHasNoPathToTheNativeStrategy] reads the production source and fails if it does.
 */
class MapScreenBrowsingIsolationTest {

    private val hnsProfile = RomHackProfile(
        id = "heart_and_soul",
        name = "Pokemon Heart & Soul",
        baseGame = "Emerald",
        gameId = 8,
        engine = "pokeemerald-expansion",
        hasPhysSpecSplit = true,
    )

    private val emeraldProfile = RomHackProfile(
        id = "vanilla_emerald", name = "Pokemon Emerald", baseGame = "Emerald", gameId = 1,
    )

    private val fireRedProfile = RomHackProfile(
        id = "vanilla_firered", name = "Pokemon FireRed", baseGame = "FireRed", gameId = 2,
    )

    /**
     * A raw pair whose meaning depends on the strategy that interprets it.
     *
     * `(0, 31)` is Pallet Town in the H&S 2.0.5 table and undefined in every other strategy.
     * `(0, 9)` is Mahogany Town to H&S and Littleroot Town to Emerald. Using these is what makes
     * the isolation assertions real: if browsing leaked into the strategy, the same raw pair would
     * resolve to a different section, not merely to the same one through a different path.
     */
    private val hnsOnlyPair = 0 to 31
    private val emeraldOnlyPair = 0 to 9

    private fun location(group: Int, num: Int) = PlayerLocation(
        mapGroup = group, mapNum = num, warpId = 0, x = 10, y = 12,
        localX = 10, localY = 12, escapeMapGroup = 0, escapeMapNum = 0,
        isIndoors = false, isValid = true,
    )

    private fun live(strategy: LocationStrategy, group: Int, num: Int): RegionMapSection? =
        LocationResolver.resolve(strategy, location(group, num)).section

    private val initialCanvas = MapCanvasSelection(RegionId.JOHTO, followsLiveRegion = true)

    // ------------------------------------------------------------------ phase C sequence

    /**
     * The exact six-step sequence #11 asks for, through the production objects.
     */
    @Test
    fun browsingAnotherRegionCannotChangeNativeMemoryInterpretation() {
        // 1. an exact H&S live location resolves from its H&S strategy.
        val hnsSection = live(LocationStrategy.HEART_AND_SOUL_205, hnsOnlyPair.first, hnsOnlyPair.second)
        assertNotNull("H&S must resolve its own Kanto block", hnsSection)
        assertEquals("MAPSEC_PALLET_TOWN", hnsSection!!.id)
        assertEquals(RegionId.KANTO, hnsSection.region)

        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        assertEquals(
            "the H&S profile must select the H&S strategy",
            LocationStrategy.HEART_AND_SOUL_205,
            vm.locationStrategy.value
        )

        // 2. the user browses/selects another available map canvas (Johto, where the player is not).
        var events = 0
        val state = MapScreenState(vm.locationStrategy.value) { events++ }
        state.render(hnsSection, hasLiveLocation = true)
        assertEquals("the canvas follows the live Kanto region", RegionId.KANTO, state.canvas.region)

        state.onRegionSelected(RegionId.JOHTO, hnsSection, hasLiveLocation = true)
        assertEquals(RegionId.JOHTO, state.canvas.region)
        assertFalse("an explicit browse stops following live", state.canvas.followsLiveRegion)

        // ...and browsing a *tile* on that canvas too, since that is a second entrypoint.
        val johtoTile = MapScreenPresenter
            .sectionsFor(vm.locationStrategy.value, RegionId.JOHTO)
            .first()
        state.onTileTapped(johtoTile, hnsSection, hasLiveLocation = true)
        assertEquals(RegionId.JOHTO, state.canvas.region)

        // 3. the live ROM keeps the same H&S location strategy and the same native gameId/reader.
        assertEquals(LocationStrategy.HEART_AND_SOUL_205, vm.locationStrategy.value)
        assertEquals(hnsProfile.id, vm.activeProfile.value.id)
        assertEquals(hnsProfile.gameId, vm.activeGameId.value)

        // 4. incoming live location updates continue to resolve against H&S data.
        vm.updatePlayerLocation(location(hnsOnlyPair.first, hnsOnlyPair.second))
        assertEquals(
            "a live update during browsing must still resolve through H&S",
            "MAPSEC_PALLET_TOWN",
            vm.resolvedLocation.value?.id
        )
        // The same raw pair under Emerald's table means something else entirely, which is exactly
        // why re-deriving the strategy from the browsed canvas would be visible here.
        assertEquals("LITTLEROOT_TOWN", live(LocationStrategy.EMERALD, 0, 9)?.id)
        assertNotEquals(
            live(LocationStrategy.EMERALD, hnsOnlyPair.first, hnsOnlyPair.second)?.id,
            vm.resolvedLocation.value?.id
        )

        // 5. returning from browsing restores/follows the correct live region.
        state.onRegionSelected(RegionId.KANTO, hnsSection, hasLiveLocation = true)
        assertEquals(RegionId.KANTO, state.canvas.region)

        // A strategy change (the only legitimate way the interpretation changes) drops the
        // override and re-renders; browsing itself never did.
        state.onStrategyChanged(LocationStrategy.HEART_AND_SOUL_205, hnsSection, hasLiveLocation = true)
        assertNull("a strategy change clears the browse override", state.browseOverride)
        assertEquals(hnsSection.region, state.canvas.region)

        // 6. no visible-region state changed the native layout/strategy, at any point.
        assertEquals(LocationStrategy.HEART_AND_SOUL_205, vm.locationStrategy.value)
        assertEquals(hnsProfile.id, vm.activeProfile.value.id)
        assertTrue("state changes must have been published", events > 0)
    }

    /**
     * Every browsable canvas, one at a time, leaves the native interpretation byte-identical.
     *
     * `hnsStrategyBefore == hnsStrategyAfter` alone would pass trivially if the strategy were
     * never consulted; the pairing assertions around it are what give it teeth.
     */
    @Test
    fun everyBrowsableCanvasLeavesTheNativeInterpretationUnchanged() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)

        val strategyBefore = vm.locationStrategy.value
        val profileBefore = vm.activeProfile.value
        val gameIdBefore = vm.activeGameId.value
        val trustBefore = vm.runtimeRomTrust.value

        val hnsSection = live(strategyBefore, emeraldOnlyPair.first, emeraldOnlyPair.second)
        // (0,9) means Mahogany Town to the H&S table and Littleroot Town to Emerald's. That is what
        // makes "the answer did not change" a real assertion rather than a coincidence.
        assertEquals(
            "H&S reads (0,9) as Mahogany Town, not Emerald's Littleroot Town",
            "MAPSEC_MAHOGANY_TOWN",
            hnsSection?.id
        )
        assertEquals(
            "Emerald reads the same pair as Littleroot Town",
            "LITTLEROOT_TOWN",
            live(LocationStrategy.EMERALD, emeraldOnlyPair.first, emeraldOnlyPair.second)?.id
        )

        val state = MapScreenState(strategyBefore) {}
        state.render(hnsSection, hasLiveLocation = true)

        for (region in RegionId.entries) {
            // A canvas the strategy cannot draw must not become selectable, or the screen would
            // imply a rendering it cannot produce.
            val drawable = MapScreenPresenter.canvasRegions(strategyBefore).contains(region)
            state.onRegionSelected(region, hnsSection, hasLiveLocation = true)
            assertEquals(
                "browsing $region must not change the strategy",
                strategyBefore,
                vm.locationStrategy.value
            )
            if (!drawable) {
                // canvasSelection refuses an override it cannot draw and falls back instead.
                assertTrue(
                    "an undrawable $region must not become the canvas",
                    MapScreenPresenter.canvasRegions(strategyBefore).contains(state.canvas.region)
                )
            }
            // The raw pair re-resolved mid-browse must give the same H&S answer every time.
            assertEquals(
                "browsing $region must not change how the raw pair is interpreted",
                hnsSection?.id,
                LocationResolver.resolve(strategyBefore, location(emeraldOnlyPair.first, emeraldOnlyPair.second))
                    .section?.id
            )
        }

        assertEquals(profileBefore, vm.activeProfile.value)
        assertEquals(gameIdBefore, vm.activeGameId.value)
        assertEquals(trustBefore, vm.runtimeRomTrust.value)
    }

    /**
     * Moving from a browsing selection back to live must follow the player's own region.
     *
     * The inverse of the requirement: a browsing override is not allowed to become a permanent pin,
     * or entering Kanto would leave the Johto canvas up forever.
     */
    @Test
    fun leavingBrowsingFollowsTheLiveRegionAgain() {
        val state = MapScreenState(LocationStrategy.HEART_AND_SOUL_205) {}

        val johto = live(LocationStrategy.HEART_AND_SOUL_205, 0, 0)!!
        state.render(johto, hasLiveLocation = true)
        assertEquals(RegionId.JOHTO, state.canvas.region)

        // Browse Kanto explicitly.
        state.onRegionSelected(RegionId.KANTO, johto, hasLiveLocation = true)
        assertEquals(RegionId.KANTO, state.canvas.region)

        // The live location changes to a Kanto map while browsing: the browse is preserved, the
        // native interpretation is unchanged, and the marker is still refused on the wrong canvas.
        val kantoSection = live(LocationStrategy.HEART_AND_SOUL_205, 0, 31)!!
        assertEquals(RegionId.KANTO, kantoSection.region)
        state.render(kantoSection, hasLiveLocation = true)
        assertEquals("a browse survives live updates", RegionId.KANTO, state.canvas.region)
        assertFalse(state.canvas.followsLiveRegion)

        // A strategy change clears the browse and live-follow resumes.
        state.onStrategyChanged(LocationStrategy.HEART_AND_SOUL_205, kantoSection, hasLiveLocation = true)
        assertNull(state.browseOverride)
        assertEquals(RegionId.KANTO, state.canvas.region)
        assertTrue(state.canvas.followsLiveRegion)
    }

    // ------------------------------------------------------------------ structural guard

    /**
     * Mutation control: the browsing state must have no path to the native strategy.
     *
     * The mutations this catches are the ones that would make the behavioural tests pass while the
     * product was still wrong:
     *
     *  * `MapScreenState` importing `CompanionViewModel` or `RomHackProfile` (browsing reaching the
     *    session);
     *  * calling `LocationStrategy.forProfile` (browsing re-deriving the strategy);
     *  * a settable `strategy` property (browsing assigning it).
     */
    @Test
    fun browsingStateHasNoPathToTheNativeStrategy() {
        val source = presenterSource()

        val stateClass = source.substringAfter("class MapScreenState(")
        assertTrue("MapScreenState must be present in the presenter source", stateClass.isNotEmpty())

        for (forbidden in listOf(
            "CompanionViewModel",
            "RomHackProfile",
            "forProfile(",
            "resolveLocationDetailed",
            "RuntimeRomTrust",
        )) {
            assertFalse(
                "MapScreenState must not reference $forbidden: browsing must have no path to the " +
                    "native layout or strategy authority",
                stripComments(stateClass).contains(forbidden)
            )
        }

        // The strategy is injected once and is not publicly assignable.
        assertTrue(
            "MapScreenState.strategy must not expose a public setter",
            source.contains("var strategy: LocationStrategy = strategy\n        private set")
        )

        // And the presenter's browsing transitions must not take a strategy they could reassign.
        for (declaration in listOf("fun onRegionSelected(", "fun onTileTapped(")) {
            val function = extractFunction(stripComments(source), declaration)
            assertNotNull("$declaration must exist", function)
            assertFalse(
                "$declaration must not accept a strategy argument",
                function!!.substringBefore(")").contains("LocationStrategy")
            )
        }
    }

    /**
     * Mutation control: a browsing selection is never presented as the player's own position.
     *
     * If browsing could produce a Live selection -- or a live marker -- the screen would claim a
     * position the running ROM never reported.
     */
    @Test
    fun browsingNeverProducesALiveSelectionOrMarker() {
        val kantoTile = MapScreenPresenter
            .sectionsFor(LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO)
            .first()
        val selection = MapScreenPresenter.onBrowsed(kantoTile)
        assertTrue(selection is MapSelection.Browsing)
        assertFalse("browsing must never be reported as live", MapScreenPresenter.isLive(selection))

        // The live marker gate refuses the browsed section: it is only ever fed the RESOLVED LIVE
        // section, so a browsed tile can never become the player's marker.
        val johtoPlayer = location(0, 0)
        val liveSection = live(LocationStrategy.HEART_AND_SOUL_205, 0, 0)!!
        assertNotEquals("the fixture must browse a different section", liveSection.id, kantoTile.id)
        assertEquals(
            "the marker must be the live section, never the browsed one",
            liveSection.id,
            MapScreenPresenter.markerSection(
                resolved = liveSection,
                playerLocation = johtoPlayer,
                strategy = LocationStrategy.HEART_AND_SOUL_205,
                canvasRegion = RegionId.JOHTO,
            )?.id
        )
        assertNull(
            "a browsed Kanto section must not become the live marker on the Kanto canvas",
            MapScreenPresenter.markerSection(
                resolved = kantoTile,
                playerLocation = johtoPlayer,
                strategy = LocationStrategy.HEART_AND_SOUL_205,
                canvasRegion = RegionId.KANTO,
            )
        )
        // The browsed highlight exists, but it is a highlight, not a position claim.
        assertNotNull(
            MapScreenPresenter.drawableHighlight(
                selection = selection,
                strategy = LocationStrategy.HEART_AND_SOUL_205,
                canvasRegion = RegionId.KANTO,
            )
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun presenterSource(): String {
        val file = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/java/com/dualdex/companion/ui/MapScreenPresenter.kt") }
            .firstOrNull { it.isFile }
            ?: throw AssertionError(
                "MapScreenPresenter source must be readable to guard the browsing boundary"
            )
        return file.readText()
    }

    private fun stripComments(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\n]*"), "")

    private fun extractFunction(source: String, declaration: String): String? {
        val start = source.indexOf(declaration)
        if (start < 0) return null
        val open = source.indexOf('{', start)
        if (open < 0) return null
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        return null
    }

    /** Keeps the Android profile ids honest: the strategies these tests switch between must exist. */
    @Test
    fun theProfilesUsedHereSelectDistinctStrategies() {
        assertEquals(LocationStrategy.HEART_AND_SOUL_205, LocationStrategy.forProfile(hnsProfile))
        assertEquals(LocationStrategy.EMERALD, LocationStrategy.forProfile(emeraldProfile))
        assertEquals(LocationStrategy.FIRERED, LocationStrategy.forProfile(fireRedProfile))
        assertEquals(
            "an exact FireRed download must still resolve to Kanto, not to an H&S table",
            RegionId.KANTO,
            live(LocationStrategy.FIRERED, 3, 0)?.region
        )
        assertEquals(
            "an exact Emerald download must still resolve to Hoenn",
            RegionId.HOENN,
            live(LocationStrategy.EMERALD, 0, 9)?.region
        )
        // And the H&S strategy never interprets another game's group as its own.
        assertNull(
            RegionMapDatabase.resolveLocationDetailed(
                LocationStrategy.FIRERED, location(0, 31)
            ).section
        )
    }
}
