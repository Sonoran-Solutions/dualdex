package com.dualdex.emulator

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputManagerShortcutTest {
    private fun manager(log: MutableList<InputManager.Trigger>) =
        InputManager().apply { onShortcut = { log += it }; nanoClock = { t += 1_000_000_000L; t } }
    private var t = 0L

    @Test
    fun l2R2KeysFireOnceAndAreConsumed() {
        val log = mutableListOf<InputManager.Trigger>()
        val m = manager(log)
        assertTrue(m.onKeyDown(KeyEvent.KEYCODE_BUTTON_L2))
        assertTrue(m.onKeyDown(KeyEvent.KEYCODE_BUTTON_L2, repeatCount = 3))
        assertTrue(m.onKeyUp(KeyEvent.KEYCODE_BUTTON_L2))
        assertTrue(m.onKeyDown(KeyEvent.KEYCODE_BUTTON_R2))
        assertEquals(listOf(InputManager.Trigger.L2, InputManager.Trigger.R2), log)
        assertEquals(0, m.getCurrentMask())
    }

    @Test
    fun analogTriggersFireOncePerPress() {
        val log = mutableListOf<InputManager.Trigger>()
        val m = manager(log)
        m.onTriggerAxes(0.8f, 0f); m.onTriggerAxes(1f, 0f); m.onTriggerAxes(0.5f, 0f)
        m.onTriggerAxes(0f, 0f); m.onTriggerAxes(0.9f, 0f)
        m.onTriggerAxes(0f, 0.9f)
        assertEquals(
            listOf(InputManager.Trigger.L2, InputManager.Trigger.L2, InputManager.Trigger.R2),
            log
        )
    }

    @Test
    fun keyAndAxisFromOnePressFireOnce() {
        val log = mutableListOf<InputManager.Trigger>()
        var now = 0L
        val m = InputManager().apply { onShortcut = { log += it }; nanoClock = { now } }
        m.onKeyDown(KeyEvent.KEYCODE_BUTTON_R2)
        now += 20_000_000L          // axis crosses ~20 ms later for the same press
        m.onTriggerAxes(0f, 0.9f)
        assertEquals(listOf(InputManager.Trigger.R2), log)
        now += 400_000_000L         // a genuinely new press after the cooldown
        m.onTriggerAxes(0f, 0f); m.onKeyUp(KeyEvent.KEYCODE_BUTTON_R2)
        m.onKeyDown(KeyEvent.KEYCODE_BUTTON_R2)
        assertEquals(2, log.size)
    }

    @Test
    fun lightPressBelowThresholdDoesNotFire() {
        val log = mutableListOf<InputManager.Trigger>()
        val m = manager(log)
        m.onTriggerAxes(0.5f, 0.6f)
        assertEquals(emptyList<InputManager.Trigger>(), log)
    }
}
