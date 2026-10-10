package com.dualdex.emulator

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputManagerShortcutTest {
    private fun manager(log: MutableList<InputManager.Trigger>) =
        InputManager().apply { onShortcut = { log += it } }

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
}
