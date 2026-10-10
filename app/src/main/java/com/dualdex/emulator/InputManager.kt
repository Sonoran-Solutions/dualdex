package com.dualdex.emulator

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

class InputManager {

    enum class Trigger { L2, R2 }

    /** Invoked once per physical L2 / R2 press; those keys are consumed and never reach the core. */
    @Volatile
    var onShortcut: ((Trigger) -> Unit)? = null

    private var l2Held = false
    private var r2Held = false

    companion object {
        private const val TRIGGER_PRESS = 0.6f
        private const val TRIGGER_RELEASE = 0.3f

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

    fun onKeyDown(keyCode: Int, repeatCount: Int = 0): Boolean {
        triggerFor(keyCode)?.let {
            // Consumed; key-repeat from a held button must not re-trigger a save/load.
            if (repeatCount == 0) fire(it)
            return true
        }
        val mask = mapKeyCodeToMask(keyCode)
        if (mask != 0) {
            currentMask = currentMask or mask
            ControllerInputRouter.setPhysicalMask(currentMask)
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int): Boolean {
        if (triggerFor(keyCode) != null) return true
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

            onTriggerAxes(
                maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
                maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            )
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

    /** Analog L2/R2: fire on the press edge (with hysteresis) so a held trigger fires once. */
    fun onTriggerAxes(l2: Float, r2: Float) {
        if (!l2Held && l2 > TRIGGER_PRESS) { l2Held = true; fire(Trigger.L2) }
        else if (l2Held && l2 < TRIGGER_RELEASE) l2Held = false
        if (!r2Held && r2 > TRIGGER_PRESS) { r2Held = true; fire(Trigger.R2) }
        else if (r2Held && r2 < TRIGGER_RELEASE) r2Held = false
    }

    private fun fire(s: Trigger) { onShortcut?.invoke(s) }

    private fun triggerFor(keyCode: Int): Trigger? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_L2 -> Trigger.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> Trigger.R2
        else -> null
    }

    private fun mapKeyCodeToMask(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_Z -> BTN_A
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> BTN_B
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_C -> BTN_X
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_V -> BTN_Y
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_A -> BTN_L
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_S -> BTN_R
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
