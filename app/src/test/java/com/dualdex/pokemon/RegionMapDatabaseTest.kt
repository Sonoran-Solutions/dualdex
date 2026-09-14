package com.dualdex.pokemon

import org.junit.Assert.*
import org.junit.Test

class RegionMapDatabaseTest {

    @Test
    fun testJohtoTownsAndRoutesResolution() {
        // Group 0 overworld maps for Heart & Soul
        val newBarkLoc = PlayerLocation(
            mapGroup = 0, mapNum = 0, warpId = 0,
            x = 10, y = 12, localX = 14, localY = 10,
            escapeMapGroup = 0, escapeMapNum = 0,
            isIndoors = false, isValid = true
        )
        val newBarkSec = RegionMapDatabase.resolveLocation(1, true, newBarkLoc)
        assertEquals("MAPSEC_NEW_BARK_TOWN", newBarkSec.id)
        assertEquals("New Bark Town", newBarkSec.name)
        assertEquals(RegionId.JOHTO, newBarkSec.region)
        assertEquals(19, newBarkSec.gridX)
        assertEquals(10, newBarkSec.gridY)
        assertEquals(MapNodeType.TOWN, newBarkSec.nodeType)

        // Cherrygrove City (Map 1)
        val cherryLoc = newBarkLoc.copy(mapNum = 1)
        val cherrySec = RegionMapDatabase.resolveLocation(1, true, cherryLoc)
        assertEquals("MAPSEC_CHERRYGROVE_CITY", cherrySec.id)
        assertEquals("Cherrygrove City", cherrySec.name)

        // Violet City (Map 2)
        val violetLoc = newBarkLoc.copy(mapNum = 2)
        val violetSec = RegionMapDatabase.resolveLocation(1, true, violetLoc)
        assertEquals("MAPSEC_VIOLET_CITY", violetSec.id)
        assertEquals("Violet City", violetSec.name)
        assertEquals(MapNodeType.CITY, violetSec.nodeType)
        assertNotNull(violetSec.gymLeader)
        assertTrue(violetSec.gymLeader!!.contains("Falkner"))

        // Goldenrod City (Map 4)
        val goldenrodLoc = newBarkLoc.copy(mapNum = 4)
        val goldenrodSec = RegionMapDatabase.resolveLocation(1, true, goldenrodLoc)
        assertEquals("MAPSEC_GOLDENROD_CITY", goldenrodSec.id)
        assertEquals("Goldenrod City", goldenrodSec.name)
        assertEquals(2, goldenrodSec.height) // 1x2 tile city
        assertNotNull(goldenrodSec.gymLeader)
        assertTrue(goldenrodSec.gymLeader!!.contains("Whitney"))

        // Route 29 (Map 11)
        val r29Loc = newBarkLoc.copy(mapNum = 11)
        val r29Sec = RegionMapDatabase.resolveLocation(1, true, r29Loc)
        assertEquals("MAPSEC_ROUTE_29", r29Sec.id)
        assertEquals(MapNodeType.ROUTE, r29Sec.nodeType)
    }

    @Test
    fun testJohtoIndoorGroupResolution() {
        // Group 1: IndoorNewBark -> resolves to New Bark Town
        val indoorNewBarkLoc = PlayerLocation(
            mapGroup = 1, mapNum = 2, warpId = 1,
            x = 4, y = 5, localX = 4, localY = 5,
            escapeMapGroup = 0, escapeMapNum = 0,
            isIndoors = true, isValid = true
        )
        val sec = RegionMapDatabase.resolveLocation(1, true, indoorNewBarkLoc)
        assertEquals("MAPSEC_NEW_BARK_TOWN", sec.id)
        assertEquals("New Bark Town", sec.name)

        // Group 5: IndoorGoldenrod -> resolves to Goldenrod City
        val indoorGoldenrodLoc = indoorNewBarkLoc.copy(mapGroup = 5, mapNum = 12)
        val goldenrodSec = RegionMapDatabase.resolveLocation(1, true, indoorGoldenrodLoc)
        assertEquals("MAPSEC_GOLDENROD_CITY", goldenrodSec.id)
    }

    @Test
    fun testJohtoDungeonResolution() {
        // Group 24: Dungeons
        val darkCaveLoc = PlayerLocation(
            mapGroup = 24, mapNum = 0, warpId = 0,
            x = 10, y = 15, localX = 10, localY = 15,
            escapeMapGroup = 0, escapeMapNum = 13,
            isIndoors = true, isValid = true
        )
        val darkCaveSec = RegionMapDatabase.resolveLocation(1, true, darkCaveLoc)
        assertEquals("MAPSEC_DARK_CAVE", darkCaveSec.id)
        assertEquals("Dark Cave", darkCaveSec.name)
        assertEquals(MapNodeType.DUNGEON, darkCaveSec.nodeType)

        // Sprout Tower (Map 2)
        val sproutLoc = darkCaveLoc.copy(mapNum = 2)
        val sproutSec = RegionMapDatabase.resolveLocation(1, true, sproutLoc)
        assertEquals("MAPSEC_SPROUT_TOWER", sproutSec.id)

        // Lake of Rage (Map 23)
        val lakeLoc = darkCaveLoc.copy(mapNum = 23)
        val lakeSec = RegionMapDatabase.resolveLocation(1, true, lakeLoc)
        assertEquals("MAPSEC_LAKE_OF_RAGE", lakeSec.id)
        assertEquals("Lake Of Rage", lakeSec.name)
    }

    @Test
    fun testHoennAndKantoResolution() {
        // Emerald Hoenn
        val hoennLoc = PlayerLocation(
            mapGroup = 0, mapNum = 9, warpId = 0,
            x = 5, y = 5, localX = 5, localY = 5,
            escapeMapGroup = 0, escapeMapNum = 0,
            isIndoors = false, isValid = true
        )
        val littleroot = RegionMapDatabase.resolveLocation(1, false, hoennLoc)
        assertEquals("LITTLEROOT_TOWN", littleroot.id)
        assertEquals(RegionId.HOENN, littleroot.region)

        // FireRed Kanto
        val kantoLoc = PlayerLocation(
            mapGroup = 3, mapNum = 0, warpId = 0,
            x = 5, y = 5, localX = 5, localY = 5,
            escapeMapGroup = 0, escapeMapNum = 0,
            isIndoors = false, isValid = true
        )
        val pallet = RegionMapDatabase.resolveLocation(2, false, kantoLoc)
        assertEquals("PALLET_TOWN", pallet.id)
        assertEquals(RegionId.KANTO, pallet.region)
    }

    @Test
    fun unsupportedProfileDoesNotBorrowAnotherGamesMapTable() {
        val loc = PlayerLocation(
            mapGroup = 3, mapNum = 0, warpId = 0,
            x = 5, y = 5, localX = 5, localY = 5,
            escapeMapGroup = 0, escapeMapNum = 0,
            isIndoors = false, isValid = true
        )

        assertNull(RegionMapDatabase.resolveLocationOrNull(6, false, loc))
        assertEquals("PALLET_TOWN", RegionMapDatabase.resolveLocationOrNull(2, false, loc)?.id)
    }

    @Test
    fun testSectionsListNotEmpty() {
        val johto = RegionMapDatabase.getSections(RegionId.JOHTO)
        assertTrue(johto.size >= 100)

        val hoenn = RegionMapDatabase.getSections(RegionId.HOENN)
        assertTrue(hoenn.isNotEmpty())

        val kanto = RegionMapDatabase.getSections(RegionId.KANTO)
        assertTrue(kanto.isNotEmpty())
    }
}
