package com.dualdex.emulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.input.InputManager as AndroidInputManager
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.dualdex.emulator.InputManager.Companion.BTN_A
import com.dualdex.emulator.InputManager.Companion.BTN_B
import com.dualdex.emulator.InputManager.Companion.BTN_DOWN
import com.dualdex.emulator.InputManager.Companion.BTN_L
import com.dualdex.emulator.InputManager.Companion.BTN_LEFT
import com.dualdex.emulator.InputManager.Companion.BTN_R
import com.dualdex.emulator.InputManager.Companion.BTN_RIGHT
import com.dualdex.emulator.InputManager.Companion.BTN_SELECT
import com.dualdex.emulator.InputManager.Companion.BTN_START
import com.dualdex.emulator.InputManager.Companion.BTN_UP
import com.dualdex.settings.TouchOverlayMode
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** Pure touch-control math (#153), unit-tested without Android. */
object TouchControlsMath {
    /**
     * 8-way D-pad: nothing inside [deadzone] (fraction of [radius]); otherwise the 45-degree
     * sector around the touch angle, diagonals pressing both neighbouring directions.
     * Screen coordinates: +dy is down.
     */
    fun dpadMask(dx: Float, dy: Float, radius: Float, deadzone: Float = 0.25f): Int {
        if (radius <= 0f || hypot(dx, dy) < radius * deadzone) return 0
        val deg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).let { if (it < 0) it + 360 else it }
        return when (((deg + 22.5) / 45).toInt() % 8) {
            0 -> BTN_RIGHT
            1 -> BTN_RIGHT or BTN_UP
            2 -> BTN_UP
            3 -> BTN_UP or BTN_LEFT
            4 -> BTN_LEFT
            5 -> BTN_LEFT or BTN_DOWN
            6 -> BTN_DOWN
            else -> BTN_DOWN or BTN_RIGHT
        }
    }

    fun shouldShow(mode: TouchOverlayMode, hasPhysicalGamepad: Boolean): Boolean = when (mode) {
        TouchOverlayMode.ALWAYS -> true
        TouchOverlayMode.NEVER -> false
        TouchOverlayMode.AUTO -> !hasPhysicalGamepad
    }
}

/**
 * Translucent GBA controls drawn over the game. Shows itself per [mode] and re-evaluates when
 * gamepads are attached/removed. Writes held buttons through [onMask].
 */
class TouchOverlayView(context: Context, private val onMask: (Int) -> Unit) : View(context),
    AndroidInputManager.InputDeviceListener {

    var mode: TouchOverlayMode = TouchOverlayMode.AUTO
        set(value) { field = value; refreshVisibility() }

    private val im = context.getSystemService(AndroidInputManager::class.java)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private data class Btn(val rect: RectF, val bit: Int, val label: String, val round: Boolean)
    private var buttons = emptyList<Btn>()
    private var padCx = 0f
    private var padCy = 0f
    private var padR = 0f
    private var lastMask = 0

    init { contentDescription = "On-screen GBA controls" }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val u = min(w, h) / 10f
        padR = u * 1.6f; padCx = u * 0.6f + padR; padCy = h - u * 0.8f - padR
        val ay = h - u * 2.6f
        buttons = listOf(
            Btn(RectF(w - u * 1.9f, ay - u * 0.9f, w - u * 0.3f, ay + u * 0.7f), BTN_A, "A", true),
            Btn(RectF(w - u * 3.7f, ay - u * 0.1f, w - u * 2.1f, ay + u * 1.5f), BTN_B, "B", true),
            Btn(RectF(w / 2f - u * 2.3f, h - u * 0.9f, w / 2f - u * 0.3f, h - u * 0.3f), BTN_SELECT, "SELECT", false),
            Btn(RectF(w / 2f + u * 0.3f, h - u * 0.9f, w / 2f + u * 2.3f, h - u * 0.3f), BTN_START, "START", false),
            Btn(RectF(u * 0.3f, u * 0.3f, u * 2.8f, u * 1.1f), BTN_L, "L", false),
            Btn(RectF(w - u * 2.8f, u * 0.3f, w - u * 0.3f, u * 1.1f), BTN_R, "R", false),
        )
        text.textSize = u * 0.45f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(padCx, padCy, padR, fill)
        val arm = padR * 0.32f
        canvas.drawRect(padCx - arm, padCy - padR * 0.85f, padCx + arm, padCy + padR * 0.85f, fill)
        canvas.drawRect(padCx - padR * 0.85f, padCy - arm, padCx + padR * 0.85f, padCy + arm, fill)
        for (b in buttons) {
            if (b.round) canvas.drawOval(b.rect, fill) else canvas.drawRoundRect(b.rect, 999f, 999f, fill)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() + text.textSize / 3, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var mask = 0
        val ending = event.actionMasked
        for (i in 0 until event.pointerCount) {
            val up = (ending == MotionEvent.ACTION_UP || ending == MotionEvent.ACTION_CANCEL ||
                (ending == MotionEvent.ACTION_POINTER_UP && i == event.actionIndex))
            if (up) continue
            val x = event.getX(i); val y = event.getY(i)
            if (hypot(x - padCx, y - padCy) <= padR * 1.3f) {
                mask = mask or TouchControlsMath.dpadMask(x - padCx, y - padCy, padR)
            } else {
                buttons.firstOrNull { it.rect.contains(x, y) }?.let { mask = mask or it.bit }
            }
        }
        if (mask != lastMask) {
            lastMask = mask
            onMask(mask)
            if (mask != 0) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
        return true
    }

    private fun hasPhysicalGamepad(): Boolean = InputDevice.getDeviceIds().any { id ->
        val d = InputDevice.getDevice(id) ?: return@any false
        !d.isVirtual && (d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    private fun refreshVisibility() {
        val show = TouchControlsMath.shouldShow(mode, hasPhysicalGamepad())
        visibility = if (show) VISIBLE else GONE
        if (!show && lastMask != 0) { lastMask = 0; onMask(0) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        im?.registerInputDeviceListener(this, handler)
        refreshVisibility()
    }

    override fun onDetachedFromWindow() {
        im?.unregisterInputDeviceListener(this)
        if (lastMask != 0) { lastMask = 0; onMask(0) }
        super.onDetachedFromWindow()
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshVisibility()
    override fun onInputDeviceRemoved(deviceId: Int) = refreshVisibility()
    override fun onInputDeviceChanged(deviceId: Int) = refreshVisibility()
}
