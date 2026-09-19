package com.dualdex.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Region-map section sourcing and the live-location resolver.
 *
 * Rewritten for the fail-closed contract: resolution returns null rather than a
 * default location, and the Heart & Soul canvases come from the pinned 2.0.5
 * table rather than the legacy hand-written Johto table.
 */
class RegionMapDatabaseTest {

    private fun location(
        group: Int,
        num: Int,
        valid: Boolean = true,
    ) = PlayerLocation(
        mapGroup = group,
        mapNum = num,
        warpId = 0,
        x = 10,
        y = 12,
        localX = 14,
        localY = 10,
        escapeMapGroup = 0,
        escapeMapNum = 0,
        isIndoors = false,
        isValid = valid,
    )

    private fun resolveHns(group: Int, num: Int): RegionMapSection? =
        RegionMapDatabase.resolveLocation(
            LocationStrategy.HEART_AND_SOUL_205,
            location(group, num)
        )

    @Test
    fun johtoTownsAndRoutesResolution() {
        val newBark = resolveHns(0, 0)
        assertNotNull(newBark)
        assertEquals("MAPSEC_NEW_BARK_TOWN", newBark!!.id)
        assertEquals("New Bark Town", newBark.name)
        assertEquals(RegionId.JOHTO, newBark.region)
        assertEquals(19, newBark.gridX)
        assertEquals(10, newBark.gridY)
        assertEquals(MapNodeType.TOWN, newBark.nodeType)

        val cherry = resolveHns(0, 1)
        assertEquals("MAPSEC_CHERRYGROVE_CITY", cherry!!.id)

        val violet = resolveHns(0, 2)
        assertEquals("MAPSEC_VIOLET_CITY", violet!!.id)
        assertEquals(MapNodeType.CITY, violet.nodeType)
        assertNotNull(violet.gymLeader)
        assertTrue(violet.gymLeader!!.contains("Falkner"))

        val goldenrod = resolveHns(0, 4)
        assertEquals("MAPSEC_GOLDENROD_CITY", goldenrod!!.id)
        assertEquals(2, goldenrod.height)

        val route29 = resolveHns(0, 11)
        assertEquals("MAPSEC_ROUTE_29", route29!!.id)
        assertEquals(MapNodeType.ROUTE, route29.nodeType)
        // Pinned H&S Johto canvas geometry, not the legacy hand-written value.
        assertEquals(15, route29.gridX)
        assertEquals(10, route29.gridY)
        assertEquals(4, route29.width)
    }

    @Test
    fun johtoIndoorGroupResolution() {
        // Group 1 is IndoorNewBark.
        assertEquals("MAPSEC_NEW_BARK_TOWN", resolveHns(1, 2)!!.id)
        // Group 5 is IndoorGoldenrod.
        assertEquals("MAPSEC_GOLDENROD_CITY", resolveHns(5, 12)!!.id)
    }

    @Test
    fun johtoDungeonResolution() {
        val darkCave = resolveHns(24, 0)
        assertNotNull(darkCave)
        assertEquals("MAPSEC_DARK_CAVE", darkCave!!.id)
        assertEquals(MapNodeType.DUNGEON, darkCave.nodeType)
        assertEquals(15, darkCave.gridX)
        assertEquals(4, darkCave.gridY)
        assertEquals(3, darkCave.width)
        assertEquals(2, darkCave.height)

        assertEquals("MAPSEC_SPROUT_TOWER", resolveHns(24, 2)!!.id)
        assertEquals("MAPSEC_LAKE_OF_RAGE", resolveHns(24, 23)!!.id)
    }

    @Test
    fun hoennAndKantoResolution() {
        // Emerald Hoenn.
        val littleroot = RegionMapDatabase.resolveLocation(
            LocationStrategy.EMERALD, location(0, 9)
        )
        assertNotNull(littleroot)
        assertEquals("LITTLEROOT_TOWN", littleroot!!.id)
        assertEquals(RegionId.HOENN, littleroot.region)

        // FireRed Kanto.
        val pallet = RegionMapDatabase.resolveLocation(
            LocationStrategy.FIRERED, location(3, 0)
        )
        assertNotNull(pallet)
        assertEquals("PALLET_TOWN", pallet!!.id)
        assertEquals(RegionId.KANTO, pallet.region)
    }

    @Test
    fun unsupportedProfileDoesNotBorrowAnotherGamesMapTable() {
        assertNull(
            RegionMapDatabase.resolveLocation(
                LocationStrategy.UNVERIFIED, location(3, 0)
            )
        )
        assertNull(RegionMapDatabase.resolveLocationOrNull(6, false, location(3, 0)))
        assertEquals(
            "PALLET_TOWN",
            RegionMapDatabase.resolveLocationOrNull(2, false, location(3, 0))?.id
        )
        assertEquals(
            "PALLET_TOWN",
            RegionMapDatabase.resolveLocation(
                LocationStrategy.FIRERED, location(3, 0)
            )?.id
        )
    }

    @Test
    fun invalidReadResolvesToNull() {
        assertNull(
            RegionMapDatabase.resolveLocation(
                LocationStrategy.HEART_AND_SOUL_205, location(0, 0, valid = false)
            )
        )
        assertNull(
            RegionMapDatabase.resolveLocation(LocationStrategy.HEART_AND_SOUL_205, null)
        )
    }

    /**
     * Regression for the old behaviour: an unknown Heart & Soul map used to be
     * reported as New Bark Town (JOHTO_DEFAULT), which meant an unreadable or
     * unmapped location looked like a confident, correct answer.
     */
    @Test
    fun unknownHnsMapDoesNotBecomeNewBarkTown() {
        val newBark = RegionMapDatabase.getSectionById("MAPSEC_NEW_BARK_TOWN")
        assertNotNull(newBark)

        val unknownPairs = listOf(
            0 to 71,
            1 to 5,
            24 to 12_345,
            25 to 5,
            27 to 1,
            30 to 999,
            40 to 0,
            106 to 0,
            -1 to -1,
        )
        for ((group, num) in unknownPairs) {
            val resolved = resolveHns(group, num)
            assertNull("($group, $num) must not resolve", resolved)
            assertFalse(
                "($group, $num) must not be reported as ${newBark!!.name}",
                resolved == newBark
            )
        }
    }

    @Test
    fun invalidMapNumberInValidIndoorGroupDoesNotInheritParentTown() {
        // Group 3 is IndoorViolet and defines exactly six maps.
        assertEquals("MAPSEC_VIOLET_CITY", resolveHns(3, 0)!!.id)
        for (num in listOf(6, 7, 50)) {
            assertNull("(3, $num)", resolveHns(3, num))
        }
    }

    @Test
    fun sectionsComeFromThePinnedHnsTable() {
        val johto = RegionMapDatabase.getSections(RegionId.JOHTO)
        // The legacy table reported 100+ sections by mixing Hoenn and FireRed
        // sections into Johto; the pinned H&S Johto canvas draws 56.
        assertEquals(56, johto.size)
        assertTrue(johto.all { it.region == RegionId.JOHTO })
        assertTrue(johto.all { it.presentable })
        assertTrue(johto.any { it.id == "MAPSEC_NEW_BARK_TOWN" })

        val kanto = RegionMapDatabase.getSections(RegionId.KANTO)
        assertEquals(34, kanto.size)
        assertTrue(kanto.all { it.region == RegionId.KANTO })
        // Kanto sections are Kanto, not Johto-and-therefore-wrong.
        assertTrue(kanto.any { it.id == "MAPSEC_PALLET_TOWN" })
        assertFalse(kanto.any { it.id.startsWith("MAPSEC_ROUTE_26") })

        val hoenn = RegionMapDatabase.getSections(RegionId.HOENN)
        assertTrue(hoenn.isNotEmpty())
        assertTrue(hoenn.all { it.region == RegionId.HOENN })

        // Sinjoh and Alola are regions without a DualDex canvas yet.
        assertTrue(RegionMapDatabase.getSections(RegionId.SINJOH).isEmpty())
        assertTrue(RegionMapDatabase.getSections(RegionId.ALOLA).isEmpty())
    }

    @Test
    fun nonHnsStrategyDoesNotUseTheHnsCanvas() {
        // An Emerald session has no Johto canvas at all, and a Heart & Soul session
        // has no Hoenn canvas: each strategy renders only the regions its own map
        // table provides, so neither can draw the other's geometry.
        assertTrue(
            RegionMapDatabase.getSectionsForStrategy(
                LocationStrategy.EMERALD, RegionId.JOHTO
            ).isEmpty()
        )
        assertTrue(
            RegionMapDatabase.getSectionsForStrategy(
                LocationStrategy.HEART_AND_SOUL_205, RegionId.HOENN
            ).isEmpty()
        )
        assertTrue(
            RegionMapDatabase.getSectionsForStrategy(
                LocationStrategy.FIRERED, RegionId.JOHTO
            ).isEmpty()
        )
        assertTrue(
            RegionMapDatabase.getSectionsForStrategy(
                LocationStrategy.UNVERIFIED, RegionId.JOHTO
            ).isEmpty()
        )

        assertEquals(
            56,
            RegionMapDatabase.getSectionsForStrategy(
                LocationStrategy.HEART_AND_SOUL_205, RegionId.JOHTO
            ).size
        )
        assertTrue(
            RegionMapDatabase.getSectionsForStrategy(
                LocationStrategy.EMERALD, RegionId.HOENN
            ).isNotEmpty()
        )
        assertTrue(
            RegionMapDatabase.getSectionsForStrategy(
                LocationStrategy.FIRERED, RegionId.KANTO
            ).isNotEmpty()
        )
    }

    @Test
    fun sectionLookupFindsBothHnsAndLegacySections() {
        assertNotNull(RegionMapDatabase.getSectionById("MAPSEC_NEW_BARK_TOWN"))
        assertNotNull(RegionMapDatabase.getSectionById("LITTLEROOT_TOWN"))
        assertNull(RegionMapDatabase.getSectionById("MAPSEC_NOT_A_REAL_SECTION"))
    }
}
