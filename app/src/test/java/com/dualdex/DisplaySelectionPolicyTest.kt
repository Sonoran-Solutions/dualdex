package com.dualdex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaySelectionPolicyTest {

    @Test
    fun aynThor_hostOnDisplay0_selectsPresentationDisplay1() {
        val display0 = DisplayDescriptor(
            displayId = 0,
            isPresentationCategory = false,
            isDefaultDisplay = true,
            widthPx = 1920,
            heightPx = 1080
        )
        val display1 = DisplayDescriptor(
            displayId = 1,
            isPresentationCategory = true,
            isDefaultDisplay = false,
            widthPx = 1240,
            heightPx = 1080
        )

        val selected = DisplaySelectionPolicy.selectCompanionDisplay(
            hostDisplayId = 0,
            allDisplays = listOf(display0, display1)
        )

        assertEquals(display1, selected)
    }

    @Test
    fun ayaneoPocketDs_bothMarkedPresentation_hostOnDisplay0_neverSelectsDisplay0() {
        val display0 = DisplayDescriptor(
            displayId = 0,
            isPresentationCategory = true,
            isDefaultDisplay = true,
            widthPx = 1920,
            heightPx = 1080
        )
        val display1 = DisplayDescriptor(
            displayId = 1,
            isPresentationCategory = true,
            isDefaultDisplay = false,
            widthPx = 1600,
            heightPx = 1200
        )

        val selected = DisplaySelectionPolicy.selectCompanionDisplay(
            hostDisplayId = 0,
            allDisplays = listOf(display0, display1)
        )

        assertEquals(display1, selected)
        assertNotEquals(0, selected?.displayId)
    }

    @Test
    fun ayaneoPocketDs_launchedOnDisplay1_bothMarkedPresentation_selectsDisplay0() {
        val display0 = DisplayDescriptor(
            displayId = 0,
            isPresentationCategory = true,
            isDefaultDisplay = true,
            widthPx = 1920,
            heightPx = 1080
        )
        val display1 = DisplayDescriptor(
            displayId = 1,
            isPresentationCategory = true,
            isDefaultDisplay = false,
            widthPx = 1600,
            heightPx = 1200
        )

        val selected = DisplaySelectionPolicy.selectCompanionDisplay(
            hostDisplayId = 1,
            allDisplays = listOf(display0, display1)
        )

        assertEquals(display0, selected)
        assertNotEquals(1, selected?.displayId)
    }

    @Test
    fun singleScreenPhone_onlyDisplay0_returnsNullForSplitFallback() {
        val display0 = DisplayDescriptor(
            displayId = 0,
            isPresentationCategory = false,
            isDefaultDisplay = true,
            widthPx = 2400,
            heightPx = 1080
        )

        val selected = DisplaySelectionPolicy.selectCompanionDisplay(
            hostDisplayId = 0,
            allDisplays = listOf(display0)
        )

        assertNull(selected)
    }

    @Test
    fun singleScreenDevice_display0MarkedPresentation_stillReturnsNull() {
        val display0 = DisplayDescriptor(
            displayId = 0,
            isPresentationCategory = true,
            isDefaultDisplay = true,
            widthPx = 2400,
            heightPx = 1080
        )

        val selected = DisplaySelectionPolicy.selectCompanionDisplay(
            hostDisplayId = 0,
            allDisplays = listOf(display0)
        )

        assertNull(selected)
    }

    @Test
    fun secondaryNonPresentationDisplay_fallsBackToValidSecondaryDisplay() {
        val display0 = DisplayDescriptor(
            displayId = 0,
            isPresentationCategory = false,
            isDefaultDisplay = true,
            widthPx = 1920,
            heightPx = 1080
        )
        val display2 = DisplayDescriptor(
            displayId = 2,
            isPresentationCategory = false,
            isDefaultDisplay = false,
            widthPx = 1280,
            heightPx = 720
        )

        val selected = DisplaySelectionPolicy.selectCompanionDisplay(
            hostDisplayId = 0,
            allDisplays = listOf(display0, display2)
        )

        assertEquals(display2, selected)
    }

    @Test
    fun invalidOrZeroSizedSecondaryDisplays_areIgnored() {
        val display0 = DisplayDescriptor(
            displayId = 0,
            isPresentationCategory = false,
            isDefaultDisplay = true,
            widthPx = 1920,
            heightPx = 1080
        )
        val zeroSized = DisplayDescriptor(
            displayId = 1,
            isPresentationCategory = true,
            isDefaultDisplay = false,
            widthPx = 0,
            heightPx = 0
        )
        val negativeId = DisplayDescriptor(
            displayId = -1,
            isPresentationCategory = true,
            isDefaultDisplay = false,
            widthPx = 1280,
            heightPx = 720
        )

        val selected = DisplaySelectionPolicy.selectCompanionDisplay(
            hostDisplayId = 0,
            allDisplays = listOf(display0, zeroSized, negativeId)
        )

        assertNull(selected)
    }
}
