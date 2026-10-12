package com.dualdex.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomBoundActionTest {
    @Test
    fun cancelDoesNotMutate() {
        var mutations = 0
        val action = RomBoundAction(isStillCurrent = { true }) { mutations++ }

        action.cancel()
        action.confirm()

        assertEquals(0, mutations)
    }

    @Test
    fun confirmRunsOnlyOnceAndReopenGetsANewAction() {
        var mutations = 0
        val first = RomBoundAction(isStillCurrent = { true }) { mutations++ }
        first.confirm()
        first.confirm()
        val reopened = RomBoundAction(isStillCurrent = { true }) { mutations++ }
        reopened.confirm()

        assertEquals(2, mutations)
    }

    @Test
    fun activeRomChangeOrDetachedScreenInvalidatesConfirmation() {
        var activeRom = "rom-a"
        var attached = true
        var mutations = 0
        var staleActions = 0
        val action = RomBoundAction(
            isStillCurrent = { attached && activeRom == "rom-a" },
            onStale = { staleActions++ }
        ) { mutations++ }

        activeRom = "rom-b"
        action.confirm()
        action.confirm()
        assertEquals(0, mutations)
        assertEquals(1, staleActions)

        val dismissed = RomBoundAction(
            isStillCurrent = { attached },
            onStale = { staleActions++ }
        ) { mutations++ }
        attached = false
        dismissed.confirm()
        assertEquals(0, mutations)
        assertEquals(2, staleActions)
    }

    @Test
    fun presentationDialogsKeepPrimaryTypeAndMatchSecondaryType() {
        assertNull(DualDexComponents.presentationDialogWindowType(null))
        assertNull(DualDexComponents.presentationDialogWindowType(0))
        assertEquals(2037, DualDexComponents.presentationDialogWindowType(1))
    }
}
