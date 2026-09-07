package com.dualdex.companion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the Continue Last Game card state. The card is presented
 * only when a stored ROM URI and title are both present; any partial or cleared
 * target (for example after a stale URI is removed on a failed resume) must
 * never be offered to the user.
 *
 * Pure-JVM: HomeScreenView.shouldShowResumeCard depends only on Strings and
 * performs no Android work.
 */
class HomeScreenViewTest {

    @Test
    fun `card is shown when uri and title are both present`() {
        assertTrue(
            HomeScreenView.shouldShowResumeCard(
                "content://com.android.externalstorage.documents/doc/1%3Arom%2Fgame.gba",
                "Pokemon Red"
            )
        )
    }

    @Test
    fun `card is hidden when uri is null`() {
        assertFalse(HomeScreenView.shouldShowResumeCard(null, "Pokemon Red"))
    }

    @Test
    fun `card is hidden when title is null`() {
        assertFalse(HomeScreenView.shouldShowResumeCard("content://.../game.gba", null))
    }

    @Test
    fun `card is hidden when both are null`() {
        assertFalse(HomeScreenView.shouldShowResumeCard(null, null))
    }

    @Test
    fun `card is hidden when uri is empty`() {
        assertFalse(HomeScreenView.shouldShowResumeCard("", "Pokemon Red"))
    }

    @Test
    fun `card is hidden when title is empty`() {
        assertFalse(HomeScreenView.shouldShowResumeCard("content://.../game.gba", ""))
    }

    @Test
    fun `card is hidden when both are empty`() {
        assertFalse(HomeScreenView.shouldShowResumeCard("", ""))
    }

    @Test
    fun `card is hidden when values are blank`() {
        assertFalse(HomeScreenView.shouldShowResumeCard("   ", ""))
    }

    @Test
    fun `cleared target from a failed resume keeps the card hidden`() {
        // Simulates updateResumeCard() after SettingsManager.clearLastPlayedRom():
        // both stored values read back as null, so the card must hide instead of
        // re-presenting the lost ROM.
        val lastUri: String? = null   // settingsManager.lastPlayedRomUri
        val lastTitle: String? = null // settingsManager.lastPlayedRomTitle
        assertFalse(HomeScreenView.shouldShowResumeCard(lastUri, lastTitle))
    }
}
