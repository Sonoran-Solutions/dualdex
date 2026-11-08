package com.dualdex.pokemon.unbound

import com.dualdex.companion.ui.MapHeaderState
import com.dualdex.companion.ui.MapScreenPresenter
import com.dualdex.pokemon.LocationResolver
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.pokemon.LocationUnavailableReason
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.*
import org.junit.Test

class UnboundLocationTest {
    private fun location(group: Int, num: Int, valid: Boolean = true) =
        PlayerLocation(group, num, 0, 0, 0, 0, 0, 0, 0, false, valid)

    @Test fun profileSelectsItsOwnStrategy() {
        assertEquals(LocationStrategy.UNBOUND,
            LocationStrategy.forProfile(RomHackProfile.UNSUPPORTED.copy(id = "unbound")))
        assertEquals(LocationStrategy.UNVERIFIED,
            LocationStrategy.forProfile(RomHackProfile.UNSUPPORTED.copy(name = "unbound")))
    }

    @Test fun numericTopologyMatchesVerifiedPinsAndRejectsEveryGroupBoundary() {
        assertEquals(57, UnboundMapTable.GROUP_COUNT)
        assertEquals(645, UnboundMapTable.GROUP_MAP_COUNTS.sum())
        assertEquals(0x80, UnboundMapTable.sectionId(1, 7))
        assertEquals(0x93, UnboundMapTable.sectionId(3, 6))
        for (group in UnboundMapTable.GROUP_MAP_COUNTS.indices) {
            val count = UnboundMapTable.GROUP_MAP_COUNTS[group]
            assertNotNull(UnboundMapTable.sectionId(group, count - 1))
            for (num in listOf(-1, count, Int.MAX_VALUE)) {
                assertNull(UnboundMapTable.sectionId(group, num))
                assertEquals(LocationUnavailableReason.UNKNOWN_MAP_ID,
                    LocationResolver.resolve(LocationStrategy.UNBOUND, location(group, num)).reason)
            }
        }
        for (group in listOf(-1, 57, Int.MAX_VALUE)) {
            assertEquals(LocationUnavailableReason.UNKNOWN_MAP_ID,
                LocationResolver.resolve(LocationStrategy.UNBOUND, location(group, 0)).reason)
        }
        assertEquals(LocationUnavailableReason.INVALID_READ,
            LocationResolver.resolve(LocationStrategy.UNBOUND, location(1, 7, false)).reason)
    }

    @Test fun namedAndNumericSectionsRemainVisibleWithoutGeometry() {
        val loc = location(3, 6)
        val fallback = LocationResolver.resolve(LocationStrategy.UNBOUND, loc).section!!
        assertEquals("Section 0x93", fallback.name)
        val named = RegionMapDatabase.resolveLocationDetailed(
            LocationStrategy.UNBOUND, loc, mapOf(0x93 to "Zq-9")
        ).section!!
        assertEquals("Zq-9", named.name)
        assertFalse(named.presentable)
        assertNull(named.region)
        assertTrue(MapScreenPresenter.headerState(loc, named,
            com.dualdex.romhack.RuntimeRomTrust(), null) is MapHeaderState.Live)
        assertTrue(MapScreenPresenter.canvasRegions(LocationStrategy.UNBOUND).isEmpty())
        for (region in RegionId.entries) {
            assertTrue(RegionMapDatabase.getSectionsForStrategy(LocationStrategy.UNBOUND, region).isEmpty())
        }
    }
}
