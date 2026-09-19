package com.dualdex.pokemon

import com.dualdex.pokemon.hns.Hns205MapData
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Heart & Soul 2.0.5 location routing.
 *
 * Covers the mapping itself, region correctness, invalid ids, unsupported
 * sub-areas and strategy isolation. These are synthetic/unit checks against the
 * pinned map table: they prove routing logic, not a live region transition.
 */
class HnsLocationRoutingTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun location(
        group: Int,
        num: Int,
        escapeGroup: Int = 0,
        escapeNum: Int = 0,
        indoors: Boolean = false,
        valid: Boolean = true,
    ) = PlayerLocation(
        mapGroup = group,
        mapNum = num,
        warpId = 0,
        x = 10,
        y = 12,
        localX = 10,
        localY = 12,
        escapeMapGroup = escapeGroup,
        escapeMapNum = escapeNum,
        isIndoors = indoors,
        isValid = valid,
    )

    private fun resolveHns(group: Int, num: Int, escapeGroup: Int = 0, escapeNum: Int = 0) =
        LocationResolver.resolve(
            LocationStrategy.HEART_AND_SOUL_205,
            location(group, num, escapeGroup, escapeNum)
        )

    private fun requireSection(group: Int, num: Int): RegionMapSection {
        val resolution = resolveHns(group, num)
        assertTrue(
            "($group, $num) should resolve, was ${resolution.reason}",
            resolution.isResolved
        )
        return resolution.section!!
    }

    // ------------------------------------------------------------------
    // A. Exact mapping
    // ------------------------------------------------------------------

    @Test
    fun johtoOutdoorIdentitiesResolve() {
        val expected = mapOf(
            0 to "MAPSEC_NEW_BARK_TOWN",
            1 to "MAPSEC_CHERRYGROVE_CITY",
            2 to "MAPSEC_VIOLET_CITY",
            3 to "MAPSEC_AZALEA_TOWN",
            4 to "MAPSEC_GOLDENROD_CITY",
            5 to "MAPSEC_ECRUTEAK_CITY",
            6 to "MAPSEC_OLIVINE_CITY",
            7 to "MAPSEC_CIANWOOD_CITY",
            9 to "MAPSEC_MAHOGANY_TOWN",
            10 to "MAPSEC_BLACKTHORN_CITY",
            11 to "MAPSEC_ROUTE_29",
            12 to "MAPSEC_ROUTE_30",
            13 to "MAPSEC_ROUTE_31",
            30 to "MAPSEC_ROUTE_48",
            70 to "MAPSEC_ROUTE_28",
        )
        for ((num, sectionId) in expected) {
            assertEquals("(0, $num)", sectionId, requireSection(0, num).id)
        }
    }

    @Test
    fun johtoInteriorsAndGatesResolveToTheirParentArea() {
        // A gate keeps the identity of the area it belongs to.
        assertEquals("MAPSEC_ROUTE_31", requireSection(22, 1).id)
        assertEquals("MAPSEC_AZALEA_TOWN", requireSection(22, 4).id)
        // A town interior resolves to the town.
        assertEquals("MAPSEC_NEW_BARK_TOWN", requireSection(1, 0).id)
        assertEquals("MAPSEC_VIOLET_CITY", requireSection(3, 5).id)
    }

    @Test
    fun johtoDungeonsResolve() {
        assertEquals("MAPSEC_DARK_CAVE", requireSection(24, 0).id)
        assertEquals("MAPSEC_SPROUT_TOWER", requireSection(24, 2).id)
        assertEquals("MAPSEC_UNION_CAVE", requireSection(24, 8).id)
        assertEquals("MAPSEC_SLOWPOKE_WELL", requireSection(24, 11).id)
        assertEquals("MAPSEC_ILEX_FOREST", requireSection(24, 13).id)
    }

    @Test
    fun kantoLocationsResolveInsideTheSameHnsTable() {
        // Kanto begins at mapNum 31 of the H&S towns-and-routes group. These are
        // the same native layout as Johto: no reader switch happens.
        assertEquals("MAPSEC_PALLET_TOWN", requireSection(0, 31).id)
        assertEquals("MAPSEC_VIRIDIAN_CITY", requireSection(0, 32).id)
        assertEquals("MAPSEC_PEWTER_CITY", requireSection(0, 33).id)
        assertEquals("MAPSEC_CERULEAN_CITY", requireSection(0, 34).id)
        assertEquals("MAPSEC_CINNABAR_ISLAND", requireSection(0, 40).id)
        assertEquals("MAPSEC_ROUTE_1", requireSection(0, 41).id)
        // Kanto interiors and routes live in their own H&S groups.
        assertEquals("MAPSEC_FUCHSIA_CITY", requireSection(19, 3).id)
        assertEquals("MAPSEC_PALLET_TOWN", requireSection(11, 0).id)
    }

    /**
     * The reported review finding: H&S group ordering diverges from the legacy
     * committed table from group 25 onward because Alola and Sinjoh sat there.
     */
    @Test
    fun reorderedRegionGroupsResolveAgainstPinnedSource() {
        // Group 25/26 are Alola, not IndoorDynamic/SpecialArea.
        assertEquals("MAPSEC_PONI_ISLAND", requireSection(25, 0).id)
        assertEquals("MAPSEC_MELEMELE_ISLAND", requireSection(26, 0).id)
        // Group 27 is the single IndoorDynamic map, not an Emerald group.
        assertEquals("MAPSEC_DYNAMIC", requireSection(27, 0).id)
        // Group 28/29 are Sinjoh, not Emerald map tables.
        assertEquals("MAPSEC_SNOWSWEPT_CAVERN", requireSection(28, 0).id)
        assertEquals("MAPSEC_SINJOH_RUINS", requireSection(29, 1).id)
        // Group 30 is the H&S special-area group.
        assertEquals("MAPSEC_TRAINER_HILL", requireSection(30, 0).id)
    }

    /**
     * Legacy group 22 was missing seven Battle Tent maps, shifting every later
     * map number by seven. Map 13 is a real map, not Trainer Hill's courtyard.
     */
    @Test
    fun indoorJohtoRouteGroupNumbersAreNotShifted() {
        assertEquals("MAPSEC_ROUTE_40", requireSection(22, 12).id)
        assertEquals("MAPSEC_TRAINER_HILL", requireSection(22, 13).id)
        assertEquals("MAPSEC_ROUTE_30", requireSection(22, 25).id)
        assertEquals("MAPSEC_MT_SILVER", requireSection(22, 34).id)
    }

    // ------------------------------------------------------------------
    // B. Region correctness
    // ------------------------------------------------------------------

    @Test
    fun kantoResolvesAsKantoNotJohto() {
        val kantoIds = listOf(
            requireSection(0, 31).id,
            requireSection(0, 32).id,
            requireSection(0, 33).id,
            requireSection(0, 34).id,
            requireSection(0, 40).id,
            requireSection(19, 3).id,
            requireSection(11, 0).id,
            requireSection(0, 38).id,
        )
        for (id in kantoIds) {
            val section = RegionMapDatabase.getSectionById(id)
            assertNotNull(id, section)
            assertEquals("$id must be Kanto", RegionId.KANTO, section!!.region)
        }
    }

    @Test
    fun johtoStaysJohto() {
        assertEquals(RegionId.JOHTO, requireSection(0, 0).region)
        assertEquals(RegionId.JOHTO, requireSection(0, 11).region)
        assertEquals(RegionId.JOHTO, requireSection(24, 0).region)
    }

    @Test
    fun emeraldAndFireRedKeepTheirOwnStrategies() {
        // Emerald: Hoenn, resolved only from mapGroup 0.
        val emerald = LocationResolver.resolve(
            LocationStrategy.EMERALD, location(0, 9)
        )
        assertTrue(emerald.isResolved)
        assertEquals("LITTLEROOT_TOWN", emerald.section!!.id)
        assertEquals(RegionId.HOENN, emerald.section!!.region)

        // FireRed: Kanto, resolved only from mapGroup 3.
        val fireRed = LocationResolver.resolve(
            LocationStrategy.FIRERED, location(3, 0)
        )
        assertTrue(fireRed.isResolved)
        assertEquals("PALLET_TOWN", fireRed.section!!.id)
        assertEquals(RegionId.KANTO, fireRed.section!!.region)
    }

    @Test
    fun aKantoHnsMapIsNotTheFireRedPalletTown() {
        // The H&S Kanto town shares Pallet Town's name but is produced by the H&S
        // strategy; the FireRed reader is never consulted for an H&S session.
        val hns = LocationResolver.resolve(
            LocationStrategy.HEART_AND_SOUL_205, location(0, 31)
        )
        val fireRed = LocationResolver.resolve(
            LocationStrategy.FIRERED, location(0, 31)
        )
        assertTrue(hns.isResolved)
        assertEquals("MAPSEC_PALLET_TOWN", hns.section!!.id)
        // FireRed has no mapGroup 0 town table entry for mapNum 31.
        assertFalse(fireRed.isResolved)
    }

    // ------------------------------------------------------------------
    // C. Invalid ids
    // ------------------------------------------------------------------

    @Test
    fun negativeIdsResolveToNothing() {
        for (group in listOf(-1, -32, Int.MIN_VALUE)) {
            for (num in listOf(-1, -8, Int.MIN_VALUE)) {
                assertNull("($group, $num)", Hns205MapData.findLocation(group, num))
            }
        }
        assertEquals(
            LocationUnavailableReason.UNKNOWN_MAP_ID,
            resolveHns(-1, -1).reason
        )
        assertEquals(
            LocationUnavailableReason.UNKNOWN_MAP_ID,
            resolveHns(0, -1).reason
        )
        assertEquals(
            LocationUnavailableReason.UNKNOWN_MAP_ID,
            resolveHns(-1, 0).reason
        )
    }

    @Test
    fun outOfRangeGroupResolvesToNothing() {
        // Beyond the highest H&S location group (56), and inside the range where
        // another game's table lives in the same ROM.
        for (group in listOf(57, 58, 68, 105, 106, 200)) {
            assertEquals(
                "group $group must not resolve",
                LocationUnavailableReason.UNKNOWN_MAP_ID,
                resolveHns(group, 0).reason
            )
        }
    }

    @Test
    fun outOfRangeMapNumberResolvesToNothing() {
        // Group 0 defines 71 maps; nothing beyond that is a location.
        for (num in listOf(71, 72, 100, 500)) {
            assertEquals(
                "mapNum $num must not resolve",
                LocationUnavailableReason.UNKNOWN_MAP_ID,
                resolveHns(0, num).reason
            )
        }
    }

    /**
     * The core fail-closed requirement: an invalid map number inside an
     * otherwise valid indoor group must not inherit that group's parent town.
     */
    @Test
    fun invalidMapNumberInsideValidIndoorGroupDoesNotInheritParentTown() {
        // Group 1 is IndoorNewBark and defines exactly 5 maps.
        val valid = resolveHns(1, 0)
        assertTrue(valid.isResolved)
        assertEquals("MAPSEC_NEW_BARK_TOWN", valid.section!!.id)

        for (num in listOf(5, 6, 20, 99)) {
            val resolution = resolveHns(1, num)
            assertFalse("(1, $num) must not resolve", resolution.isResolved)
            assertNull(resolution.section)
            assertEquals(LocationUnavailableReason.UNKNOWN_MAP_ID, resolution.reason)
        }
    }

    @Test
    fun invalidIdsNeverFabricateAKnownTown() {
        val probes = listOf(
            0 to -1, 0 to 999, 1 to 99, 3 to 99, 19 to 99, 22 to 99,
            24 to 9999, 25 to 99, 28 to 99, 30 to 999, 56 to 999, 99 to 0,
        )
        for ((group, num) in probes) {
            val resolution = resolveHns(group, num)
            assertFalse("($group, $num) fabricated a location", resolution.isResolved)
            assertNull(resolution.section)
        }
    }

    @Test
    fun invalidReadResolvesToNothing() {
        val resolution = LocationResolver.resolve(
            LocationStrategy.HEART_AND_SOUL_205,
            location(0, 0, valid = false)
        )
        assertFalse(resolution.isResolved)
        assertEquals(LocationUnavailableReason.INVALID_READ, resolution.reason)

        val absent = LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, null)
        assertFalse(absent.isResolved)
        assertEquals(LocationUnavailableReason.INVALID_READ, absent.reason)
    }

    /**
     * An arbitrary escape warp is not the current map's identity. A real H&S map
     * resolves from its own group/number; an unknown one does not borrow the
     * escape target.
     */
    @Test
    fun escapeWarpDoesNotStandInForAnUnknownMap() {
        val resolution = resolveHns(1, 42, escapeGroup = 0, escapeNum = 4)
        assertFalse(resolution.isResolved)
        assertEquals(LocationUnavailableReason.UNKNOWN_MAP_ID, resolution.reason)
    }

    // ------------------------------------------------------------------
    // D. Unsupported sub-areas
    // ------------------------------------------------------------------

    @Test
    fun sinjohResolvesWithItsOwnRegionButHasNoCanvas() {
        val section = requireSection(28, 5)
        assertEquals("MAPSEC_SINJOH_RUINS", section.id)
        assertEquals(RegionId.SINJOH, section.region)
        assertEquals("Sinjoh Ruins", section.name)
        // DualDex draws no Sinjoh canvas, so it must not place a marker there.
        assertFalse(section.presentable)
        assertEquals(-1, section.gridX)
        assertEquals(-1, section.gridY)
    }

    @Test
    fun alolaResolvesWithItsOwnRegionButHasNoCanvas() {
        for ((group, num, id) in listOf(
            Triple(25, 0, "MAPSEC_PONI_ISLAND"),
            Triple(25, 2, "MAPSEC_MELEMELE_ISLAND"),
            Triple(26, 0, "MAPSEC_MELEMELE_ISLAND"),
        )) {
            val section = requireSection(group, num)
            assertEquals(id, section.id)
            assertEquals(RegionId.ALOLA, section.region)
            assertFalse("$id has no DualDex canvas", section.presentable)
        }
    }

    @Test
    fun sinjohAndAlolaAreNotPresentedAsJohto() {
        for ((group, num) in listOf(28 to 0, 28 to 5, 29 to 1, 25 to 0, 26 to 0)) {
            val section = requireSection(group, num)
            assertNotNull(section.region)
            assertTrue(
                "($group, $num) must not be Johto",
                section.region != RegionId.JOHTO
            )
        }
    }

    @Test
    fun dynamicMapsResolveWithoutARegionOrCanvas() {
        val resolution = resolveHns(27, 0)
        assertTrue(resolution.isResolved)
        val section = resolution.section!!
        assertEquals("MAPSEC_DYNAMIC", section.id)
        assertNull("a dynamic area has no region", section.region)
        assertFalse(section.presentable)
    }

    @Test
    fun battlePyramidSquaresAreDynamicAndUnplaced() {
        // Group 56 is the Emerald indoor-dynamic group, but H&S does define its
        // own Battle Pyramid squares there.
        for (num in listOf(61, 68, 76)) {
            val section = requireSection(56, num)
            assertEquals("MAPSEC_DYNAMIC", section.id)
            assertNull(section.region)
            assertFalse(section.presentable)
        }
        // A number that group does define but that is not a H&S map stays unknown.
        assertEquals(
            LocationUnavailableReason.UNKNOWN_MAP_ID,
            resolveHns(56, 60).reason
        )
    }

    // ------------------------------------------------------------------
    // E. Strategy isolation
    // ------------------------------------------------------------------

    @Test
    fun strategiesDoNotLeakAcrossSessions() {
        // Each probe is defined by exactly one strategy's table and by no other,
        // so a resolved answer from the wrong table is impossible to miss.

        // Emerald-only: (0, 19) is Route 104 in the Hoenn table. H&S group 0 holds
        // 71 maps but mapNum 19 is Route 37, and FireRed rejects mapGroup 0.
        val emeraldOnly = location(0, 19)
        assertEquals(
            "ROUTE_104",
            LocationResolver.resolve(LocationStrategy.EMERALD, emeraldOnly).section?.id
        )
        assertEquals(
            "MAPSEC_ROUTE_37",
            LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, emeraldOnly)
                .section?.id
        )
        assertFalse(
            LocationResolver.resolve(LocationStrategy.FIRERED, emeraldOnly).isResolved
        )

        // FireRed-only: (3, 0). Emerald requires mapGroup 0; the H&S table has no
        // group 3 town entry for mapNum 0.
        val fireRedOnly = location(3, 0)
        assertEquals(
            "PALLET_TOWN",
            LocationResolver.resolve(LocationStrategy.FIRERED, fireRedOnly).section?.id
        )
        assertFalse(
            LocationResolver.resolve(LocationStrategy.EMERALD, fireRedOnly).isResolved
        )
        assertEquals(
            "MAPSEC_VIOLET_CITY",
            LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, fireRedOnly)
                .section?.id
        )

        // H&S-only Kanto block inside group 0.
        val hnsKanto = location(0, 31)
        assertEquals(
            "MAPSEC_PALLET_TOWN",
            LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, hnsKanto).section?.id
        )
        assertFalse(
            LocationResolver.resolve(LocationStrategy.FIRERED, hnsKanto).isResolved
        )
        assertFalse(
            LocationResolver.resolve(LocationStrategy.EMERALD, hnsKanto).isResolved
        )

        // H&S-only group index beyond every other table.
        val hnsOnlyGroup = location(19, 0)
        assertEquals(
            "MAPSEC_FUCHSIA_CITY",
            LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, hnsOnlyGroup)
                .section?.id
        )
        assertFalse(
            LocationResolver.resolve(LocationStrategy.EMERALD, hnsOnlyGroup).isResolved
        )
        assertFalse(
            LocationResolver.resolve(LocationStrategy.FIRERED, hnsOnlyGroup).isResolved
        )

        // A pair no strategy defines resolves to nothing under every strategy.
        val unknown = location(0, 200)
        for (strategy in LocationStrategy.entries) {
            assertFalse(
                "$strategy must not resolve an undefined pair",
                LocationResolver.resolve(strategy, unknown).isResolved
            )
        }
    }

    @Test
    fun unverifiedStrategyHasNoAuthoritativeLocation() {
        for (groupNum in listOf(0 to 0, 0 to 31, 3 to 0)) {
            val resolution = LocationResolver.resolve(
                LocationStrategy.UNVERIFIED,
                location(groupNum.first, groupNum.second)
            )
            assertFalse(resolution.isResolved)
            assertEquals(LocationUnavailableReason.NO_STRATEGY, resolution.reason)
        }
        assertFalse(LocationStrategy.UNVERIFIED.providesLiveLocation)
    }

    @Test
    fun strategySelectionUsesProfileIdentityNotProfileName() {
        val hnsByIdentity = RomHackProfile(
            id = "heart_and_soul",
            name = "Pokemon Heart & Soul",
            baseGame = "Emerald",
            gameId = 8,
        )
        assertEquals(
            LocationStrategy.HEART_AND_SOUL_205,
            LocationStrategy.forProfile(hnsByIdentity)
        )

        // A profile whose *name* mentions Heart & Soul is not the supported build.
        val impostor = RomHackProfile(
            id = "some_other_hack",
            name = "Heart and Soul Remix",
            baseGame = "Emerald",
            gameId = 8,
        )
        assertEquals(LocationStrategy.UNVERIFIED, LocationStrategy.forProfile(impostor))

        val emerald = RomHackProfile(
            id = "vanilla_emerald", name = "Pokemon Emerald", baseGame = "Emerald", gameId = 1
        )
        assertEquals(LocationStrategy.EMERALD, LocationStrategy.forProfile(emerald))

        val fireRed = RomHackProfile(
            id = "vanilla_firered", name = "Pokemon FireRed", baseGame = "FireRed", gameId = 2
        )
        assertEquals(LocationStrategy.FIRERED, LocationStrategy.forProfile(fireRed))

        // The "no ROM" placeholder must never select a map table.
        assertEquals(
            LocationStrategy.UNVERIFIED,
            LocationStrategy.forProfile(RomHackProfile.UNSUPPORTED)
        )
    }

    // ------------------------------------------------------------------
    // Legacy gameId overload
    // ------------------------------------------------------------------

    @Test
    fun legacyOverloadFailsClosedForUnknownGameId() {
        assertNull(RegionMapDatabase.resolveLocationOrNull(999, false, location(0, 0)))
        assertNull(RegionMapDatabase.resolveLocationOrNull(6, false, location(0, 0)))
        assertNull(RegionMapDatabase.resolveLocation(999, false, location(0, 0)))
    }

    @Test
    fun legacyOverloadAgreesWithTypedStrategy() {
        val loc = location(0, 31)
        assertEquals(
            LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, loc).section?.id,
            RegionMapDatabase.resolveLocationOrNull(8, true, loc)?.id
        )
        assertEquals(
            LocationResolver.resolve(LocationStrategy.EMERALD, location(0, 9)).section?.id,
            RegionMapDatabase.resolveLocationOrNull(1, false, location(0, 9))?.id
        )
        assertEquals(
            LocationResolver.resolve(LocationStrategy.FIRERED, location(3, 0)).section?.id,
            RegionMapDatabase.resolveLocationOrNull(2, false, location(3, 0))?.id
        )
    }
}
