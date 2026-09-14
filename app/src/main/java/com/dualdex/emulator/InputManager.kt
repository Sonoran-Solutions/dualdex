package com.dualdex.emulator

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

class InputManager {

    companion object {
        const val BTN_B: Int      = 1 shl 0
        const val BTN_Y: Int      = 1 shl 1
        const val BTN_SELECT: Int = 1 shl 2
        const val BTN_START: Int  = 1 shl 3
        const val BTN_UP: Int     = 1 shl 4
        const val BTN_DOWN: Int   = 1 shl 5
        const val BTN_LEFT: Int   = 1 shl 6
        const val BTN_RIGHT: Int  = 1 shl 7
        const val BTN_A: Int      = 1 shl 8
        const val BTN_X: Int      = 1 shl 9
        const val BTN_L: Int      = 1 shl 10
        const val BTN_R: Int      = 1 shl 11
        const val BTN_L2: Int     = 1 shl 12
        const val BTN_R2: Int     = 1 shl 13
    }

    @Volatile
    private var currentMask: Int = 0

    fun getCurrentMask(): Int = currentMask

    fun onKeyDown(keyCode: Int): Boolean {
        val mask = mapKeyCodeToMask(keyCode)
        if (mask != 0) {
            currentMask = currentMask or mask
            ControllerInputRouter.setPhysicalMask(currentMask)
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int): Boolean {
        val mask = mapKeyCodeToMask(keyCode)
        if (mask != 0) {
            currentMask = currentMask and mask.inv()
            ControllerInputRouter.setPhysicalMask(currentMask)
            return true
        }
        return false
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != 0 ||
            (event.source and InputDevice.SOURCE_GAMEPAD) != 0) {

            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val stickX = event.getAxisValue(MotionEvent.AXIS_X)
            val stickY = event.getAxisValue(MotionEvent.AXIS_Y)

            val dx = if (abs(hatX) > 0.2f) hatX else stickX
            val dy = if (abs(hatY) > 0.2f) hatY else stickY

            var mask = currentMask and (BTN_UP or BTN_DOWN or BTN_LEFT or BTN_RIGHT).inv()

            if (dx < -0.4f) mask = mask or BTN_LEFT
            if (dx > 0.4f) mask = mask or BTN_RIGHT
            if (dy < -0.4f) mask = mask or BTN_UP
            if (dy > 0.4f) mask = mask or BTN_DOWN

            currentMask = mask
            ControllerInputRouter.setPhysicalMask(currentMask)
            return true
        }
        return false
    }

    private fun mapKeyCodeToMask(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_Z -> BTN_A
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> BTN_B
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_C -> BTN_X
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_V -> BTN_Y
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_A -> BTN_L
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_S -> BTN_R
            KeyEvent.KEYCODE_BUTTON_L2 -> BTN_L2
            KeyEvent.KEYCODE_BUTTON_R2 -> BTN_R2
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER -> BTN_START
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_SPACE -> BTN_SELECT
            KeyEvent.KEYCODE_DPAD_UP -> BTN_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> BTN_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> BTN_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> BTN_RIGHT
            else -> 0
        }
    }
}
