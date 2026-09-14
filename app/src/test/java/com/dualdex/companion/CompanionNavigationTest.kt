package com.dualdex.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dualdex.companion.ui.BattleMode

class CompanionNavigationTest {
    @Test
    fun primaryTabsContainOnlyTheFiveGlobalDestinations() {
        assertEquals(
            listOf(CompanionTab.HOME, CompanionTab.PARTY, CompanionTab.BATTLE, CompanionTab.MAP, CompanionTab.MORE),
            CompanionNavigation.primaryTabs
        )
    }

    @Test
    fun everyScreenMapsToTheCorrectHighlightedPrimaryDestination() {
        val expected = mapOf(
            CompanionTab.HOME to CompanionTab.HOME,
            CompanionTab.PARTY to CompanionTab.PARTY,
            CompanionTab.BATTLE to CompanionTab.BATTLE,
            CompanionTab.MAP to CompanionTab.MAP,
            CompanionTab.MORE to CompanionTab.MORE,
            CompanionTab.CALC to CompanionTab.MORE,
            CompanionTab.TYPES to CompanionTab.MORE,
            CompanionTab.SAVES to CompanionTab.MORE,
            CompanionTab.DOCS to CompanionTab.MORE,
            CompanionTab.ASSISTANT to CompanionTab.MORE,
            CompanionTab.CHEATS to CompanionTab.MORE,
            CompanionTab.SETTINGS to CompanionTab.MORE
        )

        assertEquals(CompanionTab.values().toSet(), expected.keys)
        expected.forEach { (tab, primaryTab) ->
            assertEquals("Unexpected primary destination for $tab", primaryTab, CompanionNavigation.primaryTabFor(tab))
        }
        assertTrue(CompanionNavigation.primaryTabs.all { it in expected.values })
    }

    @Test
    fun globalBattleDestinationRestoresPrimaryBattleMode() {
        assertEquals(BattleMode.BATTLE, CompanionNavigation.battleModeForGlobalDestination(CompanionTab.BATTLE))
        assertEquals(null, CompanionNavigation.battleModeForGlobalDestination(CompanionTab.TYPES))
    }
}
