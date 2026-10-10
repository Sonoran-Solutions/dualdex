package com.dualdex.emulator

import android.view.KeyEvent
import com.dualdex.emulator.InputManager.Companion.BTN_A
import com.dualdex.emulator.InputManager.Companion.BTN_B
import com.dualdex.emulator.InputManager.Companion.BTN_DOWN
import com.dualdex.emulator.InputManager.Companion.BTN_LEFT
import com.dualdex.emulator.InputManager.Companion.BTN_R
import com.dualdex.emulator.InputManager.Companion.BTN_RIGHT
import com.dualdex.emulator.InputManager.Companion.BTN_SELECT
import com.dualdex.emulator.InputManager.Companion.BTN_UP
import com.dualdex.settings.SettingsManager
import com.dualdex.settings.TouchOverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputQuickWinsTest {

    private val chord = ChordMatcher.Chord(BTN_SELECT or BTN_R, "ff")

    @Test
    fun chordFiresOnLastKeyAndWithholdsOnlyThatKey() {
        val m = ChordMatcher(listOf(chord))
        assertNull(m.down(BTN_SELECT))
        assertEquals(BTN_SELECT, m.gameMask) // SELECT alone reaches the game
        assertEquals(chord, m.down(BTN_R))
        assertEquals(BTN_SELECT, m.gameMask) // R withheld while completing the chord
        m.up(BTN_R)
        assertEquals(BTN_SELECT, m.gameMask)
        assertEquals(chord, m.down(BTN_R)) // tapping R again while SELECT is held re-fires
        assertNull(m.down(BTN_R)) // a repeat while held does not
        m.up(BTN_R); m.up(BTN_SELECT)
        assertNull(m.down(BTN_R))
        assertEquals(BTN_R, m.gameMask) // R alone is a game button
    }

    @Test
    fun reversedOrderWithholdsSelect() {
        val m = ChordMatcher(listOf(chord))
        m.down(BTN_R)
        assertEquals(chord, m.down(BTN_SELECT))
        assertEquals(BTN_R, m.gameMask)
    }

    @Test
    fun inputManagerChordAndSwap() {
        val fired = mutableListOf<String>()
        val im = InputManager().apply { onChord = { fired += it.id } }
        im.onKeyDown(KeyEvent.KEYCODE_BUTTON_SELECT)
        im.onKeyDown(KeyEvent.KEYCODE_BUTTON_R1)
        assertEquals(listOf(InputManager.CHORD_TOGGLE_FAST_FORWARD), fired)
        assertEquals(BTN_SELECT, im.getCurrentMask())
        im.onKeyUp(KeyEvent.KEYCODE_BUTTON_R1); im.onKeyUp(KeyEvent.KEYCODE_BUTTON_SELECT)
        im.swapAB = true
        im.onKeyDown(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(BTN_B, im.getCurrentMask())
        im.onKeyUp(KeyEvent.KEYCODE_BUTTON_A)
        im.onKeyDown(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(BTN_A, im.getCurrentMask())
    }

    @Test
    fun triggerHoldMergesKeyAndAxis() {
        val log = mutableListOf<Pair<InputManager.Trigger, Boolean>>()
        val im = InputManager().apply { onTriggerHold = { t, h -> log += t to h } }
        im.onKeyDown(KeyEvent.KEYCODE_BUTTON_R2)
        im.onTriggerAxes(0f, 0.9f)
        im.onKeyUp(KeyEvent.KEYCODE_BUTTON_R2) // axis still held
        im.onTriggerAxes(0f, 0.1f)
        assertEquals(listOf(InputManager.Trigger.R2 to true, InputManager.Trigger.R2 to false), log)
    }

    @Test
    fun deviceDefaultSwap() {
        assertTrue(SettingsManager.swapABByDefault("AYN", "AYN", "Thor"))
        assertTrue(SettingsManager.swapABByDefault("Moorechip", "Retroid", "Retroid Pocket 5"))
        assertTrue(SettingsManager.swapABByDefault(null, null, "Odin2"))
        assertFalse(SettingsManager.swapABByDefault("Google", "google", "Pixel 8"))
        assertFalse(SettingsManager.swapABByDefault("Kayne", null, "Rayneo"))
    }

    @Test
    fun dpadDeadzoneAndDiagonals() {
        val r = 100f
        assertEquals(0, TouchControlsMath.dpadMask(10f, 10f, r))
        assertEquals(BTN_RIGHT, TouchControlsMath.dpadMask(80f, 5f, r))
        assertEquals(BTN_UP, TouchControlsMath.dpadMask(0f, -80f, r))
        assertEquals(BTN_DOWN, TouchControlsMath.dpadMask(0f, 80f, r))
        assertEquals(BTN_LEFT, TouchControlsMath.dpadMask(-80f, 0f, r))
        assertEquals(BTN_UP or BTN_RIGHT, TouchControlsMath.dpadMask(60f, -60f, r))
        assertEquals(BTN_DOWN or BTN_LEFT, TouchControlsMath.dpadMask(-60f, 60f, r))
        assertEquals(BTN_DOWN or BTN_RIGHT, TouchControlsMath.dpadMask(60f, 60f, r))
    }

    @Test
    fun overlayVisibility() {
        assertTrue(TouchControlsMath.shouldShow(TouchOverlayMode.AUTO, hasPhysicalGamepad = false))
        assertFalse(TouchControlsMath.shouldShow(TouchOverlayMode.AUTO, hasPhysicalGamepad = true))
        assertTrue(TouchControlsMath.shouldShow(TouchOverlayMode.ALWAYS, hasPhysicalGamepad = true))
        assertFalse(TouchControlsMath.shouldShow(TouchOverlayMode.NEVER, hasPhysicalGamepad = false))
    }

    @Test
    fun swapMigrationOnlyTouchesUntouchedConfigsOnce() {
        val fresh = SettingsManager(RomDurableResumeTest.FakeSharedPreferences())
        fresh.migrateSwapABDefault("AYN", "AYN", "Thor")
        assertTrue(fresh.swapAB)
        fresh.swapAB = false
        fresh.migrateSwapABDefault("AYN", "AYN", "Thor")
        assertFalse(fresh.swapAB) // marker: never re-applied

        val touched = SettingsManager(RomDurableResumeTest.FakeSharedPreferences())
        touched.swapAB = false
        touched.migrateSwapABDefault("AYN", "AYN", "Thor")
        assertFalse(touched.swapAB)
    }

    @Test
    fun speedRememberedPerRom() {
        val s = SettingsManager(RomDurableResumeTest.FakeSharedPreferences())
        s.setRomSpeed("aaa", 3)
        s.setRomSpeed("bbb", 2)
        assertEquals(3, s.romSpeed("aaa"))
        assertEquals(2, s.romSpeed("ccc")) // unknown ROM falls back to last global step
    }
}
