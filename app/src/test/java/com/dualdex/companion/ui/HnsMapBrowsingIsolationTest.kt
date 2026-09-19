package com.dualdex.companion.ui

import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.pokemon.MapNodeType
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI-level separation between live location and static map browsing.
 *
 * This is deliberately not an instrumented UI test: it exercises the exact
 * decisions the Map screen and region-map canvas make, through the same
 * production objects, so the separation is provable without an emulator.
 */
class HnsMapBrowsingIsolationTest {

    private val hnsProfile = RomHackProfile(
        id = "heart_and_soul",
        name = "Pokemon Heart & Soul",
        baseGame = "Emerald",
        gameId = 8,
        engine = "pokeemerald-expansion",
        hasPhysSpecSplit = true,
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

    private fun liveSection(group: Int, num: Int): RegionMapSection {
        val section = RegionMapDatabase.resolveLocation(
            LocationStrategy.HEART_AND_SOUL_205, location(group, num)
        )
        assertNotNull(section)
        return section!!
    }

    /** Mirrors `RegionMapView.liveMarkerSection`, the single marker gate. */
    private fun markerSection(
        section: RegionMapSection?,
        playerLocation: PlayerLocation?,
        browsedRegion: RegionId,
    ): RegionMapSection? {
        if (playerLocation?.isValid != true) return null
        if (section == null) return null
        if (!section.presentable) return null
        if (section.region != browsedRegion) return null
        if (section.gridX < 0 || section.gridY < 0) return null
        return section
    }

    // ------------------------------------------------------------------
    // Browsing never changes memory-layout selection
    // ------------------------------------------------------------------

    @Test
    fun changingTheBrowsedRegionDoesNotChangeRomIdentityTrustOrStrategy() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 0))

        val strategyBefore = vm.locationStrategy.value
        val profileBefore = vm.activeProfile.value
        val gameIdBefore = vm.activeGameId.value
        val romIdentityBefore = vm.activeRomIdentity.value
        val trustBefore = vm.runtimeRomTrust.value

        // Browse every canvas DualDex offers.
        for (region in RegionId.entries) {
            RegionMapDatabase.getSections(region)
        }

        assertEquals(strategyBefore, vm.locationStrategy.value)
        assertEquals(profileBefore, vm.activeProfile.value)
        assertEquals(gameIdBefore, vm.activeGameId.value)
        assertEquals(romIdentityBefore, vm.activeRomIdentity.value)
        assertEquals(trustBefore, vm.runtimeRomTrust.value)
        assertEquals(LocationStrategy.HEART_AND_SOUL_205, vm.locationStrategy.value)
    }

    @Test
    fun browsingKantoInAHnsSessionStillUsesTheHnsTable() {
        // Browsing Kanto is a static canvas choice; resolving the player's own
        // position still goes through the H&S strategy, never the FireRed reader.
        val kantoSection = liveSection(0, 31)
        assertEquals(RegionId.KANTO, kantoSection.region)

        val stillHns = RegionMapDatabase.resolveLocation(
            LocationStrategy.HEART_AND_SOUL_205, location(0, 0)
        )
        assertEquals("MAPSEC_NEW_BARK_TOWN", stillHns!!.id)
        assertEquals(RegionId.JOHTO, stillHns.region)

        // The FireRed strategy remains a different, independent answer.
        val fireRed = RegionMapDatabase.resolveLocation(
            LocationStrategy.FIRERED, location(3, 0)
        )
        assertEquals("PALLET_TOWN", fireRed!!.id)
    }

    @Test
    fun onlyScanvasBackedRegionsCanBeBrowsed() {
        assertTrue(RegionId.JOHTO.hasCanvas)
        assertTrue(RegionId.KANTO.hasCanvas)
        assertTrue(RegionId.HOENN.hasCanvas)
        // Sinjoh and Alola are named live regions with no canvas to browse.
        assertFalse(RegionId.SINJOH.hasCanvas)
        assertFalse(RegionId.ALOLA.hasCanvas)
    }

    // ------------------------------------------------------------------
    // Live marker gating
    // ------------------------------------------------------------------

    @Test
    fun liveMarkerIsDrawnOnlyOnItsOwnRegionCanvas() {
        val johtoLive = liveSection(0, 0)
        assertEquals(RegionId.JOHTO, johtoLive.region)

        assertNotNull(
            "the live marker belongs on the Johto canvas",
            markerSection(johtoLive, location(0, 0), RegionId.JOHTO)
        )
        assertNull(
            "browsing Kanto must not draw the Johto position on it",
            markerSection(johtoLive, location(0, 0), RegionId.KANTO)
        )
        assertNull(
            "browsing Hoenn must not draw the Johto position on it",
            markerSection(johtoLive, location(0, 0), RegionId.HOENN)
        )
    }

    @Test
    fun noLiveMarkerWithoutAValidLocation() {
        val johtoLive = liveSection(0, 0)
        assertNull(markerSection(johtoLive, null, RegionId.JOHTO))
        assertNull(markerSection(johtoLive, location(0, 0, valid = false), RegionId.JOHTO))
        assertNull(markerSection(null, location(0, 0), RegionId.JOHTO))
    }

    @Test
    fun unpresentableSectionsNeverGetALiveMarker() {
        // Sinjoh resolves with a true identity and region, but DualDex draws no
        // Sinjoh canvas, so no marker may be placed.
        val sinjoh = liveSection(28, 5)
        assertFalse(sinjoh.presentable)
        assertNull(markerSection(sinjoh, location(28, 5), RegionId.SINJOH))

        val alola = liveSection(25, 0)
        assertFalse(alola.presentable)
        assertNull(markerSection(alola, location(25, 0), RegionId.ALOLA))

        // A dynamic area has no canvas anchor at all.
        val dynamic = liveSection(27, 0)
        assertFalse(dynamic.presentable)
        assertNull(markerSection(dynamic, location(27, 0), RegionId.JOHTO))
    }

    // ------------------------------------------------------------------
    // Stale live state on invalidation
    // ------------------------------------------------------------------

    @Test
    fun invalidationClearsLiveState() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 0))
        assertNotNull(vm.playerLocation.value)

        // ROM unloaded / session torn down.
        vm.clearRomSession()
        assertNull("the live marker source must be cleared", vm.playerLocation.value)
        assertNull("the live name/region must be cleared", vm.resolvedLocation.value)
        assertEquals(LocationStrategy.UNVERIFIED, vm.locationStrategy.value)
    }

    @Test
    fun invalidationClearsLiveStateWhenTheProfileChanges() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 31))
        assertNotNull(vm.resolvedLocation.value)

        vm.setProfile(
            RomHackProfile(
                id = "vanilla_firered",
                name = "Pokemon FireRed",
                baseGame = "FireRed",
                gameId = 2,
            )
        )
        assertNull(vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)
    }

    @Test
    fun staticBrowsingStateIsIndependentOfLiveState() {
        // A browsed section is a plain value the screen keeps; dropping live state
        // must not require discarding it, and keeping it must not imply it is live.
        val browsed = RegionMapDatabase.getSections(RegionId.KANTO)
            .first { it.id == "MAPSEC_PALLET_TOWN" }
        assertEquals(RegionId.KANTO, browsed.region)
        assertTrue(browsed.presentable)

        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 0))
        vm.clearRomSession()

        // Browsing data survives; live data does not.
        assertNull(vm.resolvedLocation.value)
        assertEquals("MAPSEC_PALLET_TOWN", browsed.id)
        assertNull(markerSection(browsed, vm.playerLocation.value, RegionId.KANTO))
    }

    @Test
    fun hnsKantoCanvasUsesPinnedGeometryNotFireRedCoordinates() {
        val pallet = RegionMapDatabase.getSections(RegionId.KANTO)
            .first { it.id == "MAPSEC_PALLET_TOWN" }
        // Pinned H&S Kanto canvas position.
        assertEquals(4, pallet.gridX)
        assertEquals(11, pallet.gridY)

        // The FireRed table keeps its own separate position for the same name.
        val fireRedPallet = RegionMapDatabase.getSectionsForStrategy(
            LocationStrategy.FIRERED, RegionId.KANTO
        ).first { it.id == "PALLET_TOWN" }
        assertEquals(5, fireRedPallet.gridX)
        assertEquals(11, fireRedPallet.gridY)
    }

    @Test
    fun liveDetailSelectionIsDistinguishedFromBrowsing() {
        // The screen marks a live selection only for the player's own section;
        // tapping a tile yields a browsing selection.
        val live = liveSection(0, 2)
        assertEquals(MapNodeType.CITY, live.nodeType)
        assertEquals("Violet City", live.name)

        val browsed = RegionMapDatabase.getSections(RegionId.KANTO)
            .first { it.id == "MAPSEC_VIRIDIAN_CITY" }
        assertEquals("Viridian City", browsed.name)
        assertFalse(
            "a browsed Kanto tile is not the player's live location",
            browsed.id == live.id
        )
    }
}
