package com.dualdex.emulator

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import com.dualdex.companion.CompanionViewModel
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

/** Pure formatting rules for the optional status bar (#153). */
object GameStatusBarModel {
    const val MAX_MONEY = 999_999

    /**
     * Gen III stores money XOR-ed with SaveBlock2's encryption key. A value above the in-game cap
     * means the pointer/key pair was not a real save (mid-load, wrong layout): show nothing.
     */
    fun decodeMoney(raw: Long, key: Long): Int? =
        ((raw xor key) and 0xFFFFFFFFL).takeIf { it <= MAX_MONEY }?.toInt()

    fun formatClock(hour: Int, minute: Int, is24Hour: Boolean): String =
        if (is24Hour) "%02d:%02d".format(hour, minute)
        else "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")

    enum class BatteryTone { NORMAL, LOW, CHARGING }

    fun batteryTone(percent: Int, charging: Boolean): BatteryTone = when {
        charging -> BatteryTone.CHARGING
        percent <= 15 -> BatteryTone.LOW
        else -> BatteryTone.NORMAL
    }

    /** Game rect inside [w]x[h] minus a bar of [barH]: 3:2, as large as fits. Returns (gameW, gameH). */
    fun gameSize(w: Int, h: Int, barH: Int): Pair<Int, Int> {
        val availH = (h - barH).coerceAtLeast(0)
        val gameW = minOf(w, availH * 3 / 2)
        return gameW to gameW * 2 / 3
    }
}

/**
 * Hosts the emulator surface and, when enabled in Settings, a status bar exactly as wide as the
 * game directly above it, with the game held at 3:2. Disabled, the surface fills the frame as before.
 */
class GameFrameLayout(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val settings: SettingsManager = SettingsManager(context)
) : ViewGroup(context) {

    private val bar = StatusBarView(context, viewModel)
    private val barHeight = (20 * resources.displayMetrics.density).toInt()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsManager.KEY_GAME_STATUS_BAR) applyEnabled()
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(bar)
        applyEnabled()
    }

    fun setGameView(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        if (childCount > 1) removeViewAt(1)
        addView(view, 1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun applyEnabled() {
        bar.visibility = if (settings.isGameStatusBarEnabled) View.VISIBLE else View.GONE
        requestLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        settings.registerChangeListener(prefsListener)
        applyEnabled()
    }

    override fun onDetachedFromWindow() {
        settings.unregisterChangeListener(prefsListener)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val game = getChildAt(1) ?: return
        if (bar.visibility == View.GONE) {
            game.measure(exactly(w), exactly(h))
            return
        }
        val (gw, gh) = GameStatusBarModel.gameSize(w, h, barHeight)
        bar.measure(exactly(gw), exactly(barHeight))
        game.measure(exactly(gw), exactly(gh))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val game = getChildAt(1) ?: return
        val w = r - l
        val h = b - t
        if (bar.visibility == View.GONE) {
            game.layout(0, 0, w, h)
            return
        }
        val left = (w - game.measuredWidth) / 2
        val top = (h - barHeight - game.measuredHeight) / 2
        bar.layout(left, top, left + bar.measuredWidth, top + barHeight)
        game.layout(left, top + barHeight, left + game.measuredWidth, top + barHeight + game.measuredHeight)
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
}

private class StatusBarView(context: Context, private val viewModel: CompanionViewModel) : View(context) {
    private val density = resources.displayMetrics.density
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11 * density
        typeface = Typeface.DEFAULT_BOLD
    }
    private val fill = Paint()
    private var scope: CoroutineScope? = null

    private var title = ""
    private var location: String? = null
    // ponytail: money stays null until the native reader exposes verified SaveBlock1.money /
    // SaveBlock2.encryptionKey addresses; then feed GameStatusBarModel.decodeMoney here.
    private var money: Int? = null
    private var batteryPercent = -1
    private var charging = false

    private val tick = object : Runnable {
        override fun run() {
            readBattery()
            invalidate()
            postDelayed(this, 30_000L)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            combine(viewModel.activeRomTitle, viewModel.resolvedLocation) { t, loc -> t to loc?.name }
                .collect { (t, loc) ->
                    title = t
                    location = loc
                    invalidate()
                }
        }
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun readBattery() {
        val intent: Intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        } ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun onDraw(canvas: Canvas) {
        val pad = 4 * density
        val baseline = height / 2f - (text.ascent() + text.descent()) / 2f
        var right = width - pad

        // Pixel battery: 2px-unit outline, nub, level fill.
        if (batteryPercent >= 0) {
            val u = (density * 1.5f).coerceAtLeast(1f)
            val bw = 12 * u
            val bh = 6 * u
            val top = (height - bh) / 2f
            val left = right - bw - u
            fill.color = Color.WHITE
            canvas.drawRect(left, top, left + bw, top + u, fill)
            canvas.drawRect(left, top + bh - u, left + bw, top + bh, fill)
            canvas.drawRect(left, top, left + u, top + bh, fill)
            canvas.drawRect(left + bw - u, top, left + bw, top + bh, fill)
            canvas.drawRect(left + bw, top + 2 * u, left + bw + u, top + bh - 2 * u, fill)
            fill.color = when (GameStatusBarModel.batteryTone(batteryPercent, charging)) {
                GameStatusBarModel.BatteryTone.CHARGING -> Color.rgb(0x4C, 0xD9, 0x64)
                GameStatusBarModel.BatteryTone.LOW -> Color.rgb(0xF0, 0x40, 0x40)
                GameStatusBarModel.BatteryTone.NORMAL -> Color.WHITE
            }
            val inner = bw - 4 * u
            canvas.drawRect(left + 2 * u, top + 2 * u,
                left + 2 * u + inner * batteryPercent.coerceIn(0, 100) / 100f, top + bh - 2 * u, fill)
            right = left - pad
        }

        val now = Calendar.getInstance()
        val clock = GameStatusBarModel.formatClock(
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(context))
        right = drawRight(canvas, clock, right, baseline) - pad
        money?.let { right = drawRight(canvas, "₽$it", right, baseline) - pad }

        // Left: game name, then map pin + location, both ellipsized into what is left.
        var left = pad
        val space = right - left
        val loc = location
        val titleRoom = if (loc == null) space else space * 0.45f
        if (title.isNotEmpty() && titleRoom > 0) {
            val t = TextUtils.ellipsize(title, text, titleRoom, TextUtils.TruncateAt.END).toString()
            canvas.drawText(t, left, baseline, text)
            left += text.measureText(t) + 2 * pad
        }
        if (loc != null && right - left > 0) {
            val l = TextUtils.ellipsize("📍$loc", text, right - left, TextUtils.TruncateAt.END).toString()
            canvas.drawText(l, left, baseline, text)
        }
    }

    private fun drawRight(canvas: Canvas, s: String, right: Float, baseline: Float): Float {
        val x = right - text.measureText(s)
        canvas.drawText(s, x, baseline, text)
        return x
    }
}
