package com.dualdex.companion

import com.dualdex.companion.ui.BattleMode

/** Pure mapping between any companion screen and the item highlighted in primary navigation. */
object CompanionNavigation {
    val primaryTabs = listOf(
        CompanionTab.HOME,
        CompanionTab.PARTY,
        CompanionTab.BATTLE,
        CompanionTab.MAP,
        CompanionTab.MORE
    )

    fun primaryTabFor(tab: CompanionTab): CompanionTab = when (tab) {
        CompanionTab.HOME,
        CompanionTab.PARTY,
        CompanionTab.BATTLE,
        CompanionTab.MAP -> tab
        CompanionTab.MORE,
        CompanionTab.CALC,
        CompanionTab.TYPES,
        CompanionTab.SAVES,
        CompanionTab.DOCS,
        CompanionTab.ASSISTANT,
        CompanionTab.CHEATS,
        CompanionTab.SETTINGS -> CompanionTab.MORE
    }

    /** Global Battle navigation always opens the primary Battle mode. */
    fun battleModeForGlobalDestination(tab: CompanionTab): BattleMode? =
        if (tab == CompanionTab.BATTLE) BattleMode.BATTLE else null
}

/**
 * Remembers the tab the Battle auto-open replaced so the companion can go back when the battle
 * ends. Returns only if the user is still on Battle; navigating elsewhere mid-battle cancels it.
 */
class BattleAutoOpenReturn {
    private var returnTo: CompanionTab? = null

    /** Battle started and auto-open fired from [previous]. */
    fun onAutoOpened(previous: CompanionTab) {
        returnTo = previous
    }

    /** Battle ended; the tab to navigate to, or null to stay put. */
    fun onBattleEnded(current: CompanionTab): CompanionTab? =
        returnTo.also { returnTo = null }?.takeIf { current == CompanionTab.BATTLE }
}
