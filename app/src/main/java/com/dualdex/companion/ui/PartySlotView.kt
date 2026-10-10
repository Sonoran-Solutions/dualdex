package com.dualdex.companion.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

/**
 * One in-game-style party slot (issue #151), drawn procedurally on the GBA pixel grid.
 *
 * The slot is rendered at one GBA pixel per bitmap pixel into a tiny bitmap, then blitted at an
 * integer scale with filtering off, so every element lands on the same pixel grid. All artwork
 * (frame, Poké Ball, font, palette) is original and drawn in code: no game graphics are bundled.
 * When per-ROM art extraction lands (issue #151 phase 2) this remains the fallback renderer.
 */
class PartySlotView(context: Context) : View(context) {

    private data class SlotPalette(val outline: Int, val top: Int, val bottom: Int, val highlight: Int, val shadow: Int)

    private var state: PartySlotState? = null
    private var index = 0
    private var scale = 1
    private var windowWidth = PartySlotModel.MIN_WINDOW_WIDTH
    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private var bobUp = false
    private val pixel = Paint().apply { isAntiAlias = false }
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val dest = Rect()

    private val bobTick = object : Runnable {
        override fun run() {
            bobUp = !bobUp
            render()
            postDelayed(this, BOB_INTERVAL_MS)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = true
        setPadding(0, 0, 0, 0)
    }

    fun setIndex(value: Int) {
        index = value
        contentDescription = PartySlotModel.accessibilityLabel(index, state)
    }

    fun setGrid(scale: Int, windowWidth: Int) {
        if (this.scale == scale && this.windowWidth == windowWidth && bitmap != null) return
        this.scale = scale
        this.windowWidth = windowWidth
        val w = windowWidth + PartySlotModel.PAD * 2
        val h = PartySlotModel.slotViewHeight
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmapCanvas = Canvas(it) }
        render()
    }

    /** Binds new data; an identical state is a no-op so a 10 Hz poll does not repaint. */
    fun bind(newState: PartySlotState?) {
        if (newState == state && bitmap != null) return
        val wasSelected = state?.selected == true
        state = newState
        val empty = newState == null
        isClickable = !empty
        isFocusable = !empty
        contentDescription = PartySlotModel.accessibilityLabel(index, newState)
        if (wasSelected != (newState?.selected == true)) updateBob()
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateBob()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(bobTick)
        super.onDetachedFromWindow()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        render()
    }

    /** The selected Poké Ball bobs like the game's selected icon, unless animations are off. */
    private fun updateBob() {
        removeCallbacks(bobTick)
        bobUp = false
        val s = state
        val animate = s != null && s.selected && s.frame != SlotFrame.SELECTED_FAINTED &&
            isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()
        if (animate) postDelayed(bobTick, BOB_INTERVAL_MS)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val w = bmp.width * scale
        val h = bmp.height * scale
        val left = (width - w) / 2
        val top = (height - h) / 2
        dest.set(left, top, left + w, top + h)
        canvas.drawBitmap(bmp, null, dest, blit)
    }

    // ---- 1× rendering -------------------------------------------------------------------------

    private fun render() {
        val c = bitmapCanvas ?: return
        c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        val s = state
        val frame = s?.frame ?: SlotFrame.EMPTY
        val pal = paletteFor(frame)
        val ox = PartySlotModel.PAD
        val oy = PartySlotModel.PAD
        val w = windowWidth
        val h = PartySlotModel.WINDOW_HEIGHT

        // Window: staircase corners, 1px outline, two bands, bevel highlight and shadow rows.
        shape(c, ox, oy, w, h, OUTER_CORNER, pal.outline, 0 until h)
        shape(c, ox + 1, oy + 1, w - 2, h - 2, INNER_CORNER, pal.bottom, 0 until h - 2)
        shape(c, ox + 1, oy + 1, w - 2, h - 2, INNER_CORNER, pal.top, 0 until BAND_SPLIT - 1)
        shape(c, ox + 1, oy + 1, w - 2, h - 2, INNER_CORNER, pal.highlight, 0 until 1)
        shape(c, ox + 1, oy + 1, w - 2, h - 2, INNER_CORNER, pal.shadow, h - 3 until h - 2)

        if (s != null) {
            val open = s.selected
            val bob = if (bobUp) -1 else 0
            drawBall(c, ox + 3, oy + 3 + bob, open)
            if (s.isEgg) {
                text(c, "Egg", ox + 4, oy + 16, TEXT, TEXT_SHADOW, w - 8)
            } else {
                drawDetails(c, s, ox, oy, w)
            }
        }

        if (isPressed && s != null) {
            pixel.color = PRESS_OVERLAY
            c.drawRect(ox.toFloat(), oy.toFloat(), (ox + w).toFloat(), (oy + h).toFloat(), pixel)
        }
        if (isFocused && s != null) drawFocusCursor(c, w + PartySlotModel.PAD * 2, h + PartySlotModel.PAD * 2)
        invalidate()
    }

    private fun drawDetails(c: Canvas, s: PartySlotState, ox: Int, oy: Int, w: Int) {
        val levelText = "Lv${s.level}"
        text(c, levelText, ox + 18, oy + 5, TEXT, TEXT_SHADOW, w - 22)
        var right = ox + w - 4
        s.gender?.let { g ->
            val gw = PixelFont.measure(g.toString())
            text(c, g.toString(), right - gw, oy + 5, if (g == '♀') FEMALE else MALE, TEXT_SHADOW, gw)
            right -= gw + 2
        }
        if (s.shiny) {
            val sx = ox + 18 + PixelFont.measure(levelText) + 3
            if (sx + 5 <= right) text(c, "★", sx, oy + 5, STAR, TEXT_SHADOW, 5)
        }
        text(c, s.name, ox + 4, oy + 16, TEXT, TEXT_SHADOW, w - 8)

        // HP label + bar.
        text(c, "HP", ox + 4, oy + 28, HP_LABEL, TEXT_SHADOW, 11)
        val barX = ox + 17
        val barW = (ox + w - 4) - barX
        val barY = oy + 29
        rect(c, barX, barY, barW, 5, BAR_OUTLINE)
        val inner = barW - 2
        rect(c, barX + 1, barY + 1, inner, 3, BAR_EMPTY)
        val filled = PartySlotModel.scaledHp(s.currentHp, s.maxHp, inner)
        val (main, hi) = when (PartySlotModel.hpBarLevel(s.currentHp, s.maxHp)) {
            HpBarLevel.GREEN -> HP_GREEN to HP_GREEN_HI
            HpBarLevel.YELLOW -> HP_YELLOW to HP_YELLOW_HI
            HpBarLevel.RED -> HP_RED to HP_RED_HI
            HpBarLevel.EMPTY -> BAR_EMPTY to BAR_EMPTY
        }
        if (filled > 0) {
            rect(c, barX + 1, barY + 1, filled, 1, hi)
            rect(c, barX + 1, barY + 2, filled, 2, main)
        }

        val hpText = "${s.currentHp}/${s.maxHp}"
        val hpW = PixelFont.measure(hpText)
        text(c, hpText, ox + w - 4 - hpW, oy + 37, TEXT, TEXT_SHADOW, hpW)

        s.status?.let { label ->
            val bw = PixelFont.measure(label) + 4
            val bx = ox + 4
            val by = oy + 36
            if (bx + bw < ox + w - 4 - hpW) {
                rect(c, bx, by, bw, 9, STATUS_OUTLINE)
                rect(c, bx + 1, by + 1, bw - 2, 7, statusColor(label))
                // Badge text uses the cap rows only, so it fits the 7px plate.
                text(c, label, bx + 2, by + 1, TEXT, TEXT_SHADOW, bw - 4, shadow = false)
            }
        }
    }

    private fun drawFocusCursor(c: Canvas, w: Int, h: Int) {
        val len = 7
        pixel.color = DualDexTheme.Color.focusRing
        // Four corner brackets in the padding: a game-style cursor, distinct from the selected frame.
        rect(c, 0, 0, len, 2, pixel.color); rect(c, 0, 0, 2, len, pixel.color)
        rect(c, w - len, 0, len, 2, pixel.color); rect(c, w - 2, 0, 2, len, pixel.color)
        rect(c, 0, h - 2, len, 2, pixel.color); rect(c, 0, h - len, 2, len, pixel.color)
        rect(c, w - len, h - 2, len, 2, pixel.color); rect(c, w - 2, h - len, 2, len, pixel.color)
    }

    private fun drawBall(c: Canvas, x: Int, y: Int, open: Boolean) {
        BALL.forEachIndexed { row, line ->
            // An open ball lifts its top half two pixels, like a ball popping open.
            val dy = if (open && row <= BALL_SPLIT_ROW) -2 else 0
            line.forEachIndexed { col, ch ->
                val color = BALL_COLORS[ch] ?: return@forEachIndexed
                rect(c, x + col, y + row + dy, 1, 1, color)
            }
        }
    }

    private fun text(
        c: Canvas, value: String, x: Int, y: Int, color: Int, shadowColor: Int, maxWidth: Int,
        shadow: Boolean = true
    ) {
        val fitted = PixelFont.fit(value, maxWidth)
        if (shadow) {
            pixel.color = shadowColor
            PixelFont.forEachInk(fitted, x, y) { px, py ->
                c.drawRect(px + 1f, py.toFloat(), px + 2f, py + 2f, pixel)
                c.drawRect(px.toFloat(), py + 1f, px + 1f, py + 2f, pixel)
            }
        }
        pixel.color = color
        PixelFont.forEachInk(fitted, x, y) { px, py -> c.drawRect(px.toFloat(), py.toFloat(), px + 1f, py + 1f, pixel) }
    }

    private fun rect(c: Canvas, x: Int, y: Int, w: Int, h: Int, color: Int) {
        if (w <= 0 || h <= 0) return
        pixel.color = color
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), pixel)
    }

    /** Fills the rows [rows] of a w×h shape whose corners step in by [corner] per row. */
    private fun shape(c: Canvas, x: Int, y: Int, w: Int, h: Int, corner: IntArray, color: Int, rows: IntRange) {
        pixel.color = color
        for (r in rows) {
            if (r !in 0 until h) continue
            val inset = corner.getOrElse(minOf(r, h - 1 - r)) { 0 }
            c.drawRect((x + inset).toFloat(), (y + r).toFloat(), (x + w - inset).toFloat(), (y + r + 1).toFloat(), pixel)
        }
    }

    private fun paletteFor(frame: SlotFrame): SlotPalette = when (frame) {
        SlotFrame.NORMAL -> NORMAL
        SlotFrame.SELECTED -> SELECTED
        SlotFrame.FAINTED -> FAINTED
        SlotFrame.SELECTED_FAINTED -> SELECTED_FAINTED
        SlotFrame.NO_HP -> NORMAL.copy(bottom = NORMAL.top)
        SlotFrame.NO_HP_SELECTED -> SELECTED.copy(bottom = SELECTED.top)
        SlotFrame.EMPTY -> EMPTY
    }

    private fun statusColor(label: String): Int = when (label) {
        "PSN" -> 0xFFA35BC4.toInt()
        "TOX" -> 0xFF7A3E9C.toInt()
        "PAR" -> 0xFFB08A10.toInt()
        "SLP" -> 0xFF6E7A8A.toInt()
        "FRZ" -> 0xFF3F97C6.toInt()
        "BRN" -> 0xFFD0602F.toInt()
        "FNT" -> 0xFFC23B3B.toInt()
        else -> 0xFF5A5A5A.toInt()
    }

    private companion object {
        const val BOB_INTERVAL_MS = 300L
        const val BAND_SPLIT = 26
        val OUTER_CORNER = intArrayOf(2, 1)
        val INNER_CORNER = intArrayOf(1)

        // Original palette, tuned to sit on the Navigator LCD without outshining the game.
        val NORMAL = SlotPalette(0xFF0E2A3C.toInt(), 0xFF3D7DB5.toInt(), 0xFF2F6898.toInt(), 0xFF86C4EE.toInt(), 0xFF22507A.toInt())
        val SELECTED = SlotPalette(0xFFF8F8F8.toInt(), 0xFF5AAAE2.toInt(), 0xFF4A93CB.toInt(), 0xFFC0E8FF.toInt(), 0xFF3473A8.toInt())
        val FAINTED = SlotPalette(0xFF3A121C.toInt(), 0xFFA84A5A.toInt(), 0xFF8E3A4A.toInt(), 0xFFE29098.toInt(), 0xFF6E2A38.toInt())
        val SELECTED_FAINTED = SlotPalette(0xFFF8F8F8.toInt(), 0xFFC8626F.toInt(), 0xFFB04F5D.toInt(), 0xFFF8B8C0.toInt(), 0xFF8A3846.toInt())
        val EMPTY = SlotPalette(0xFF1C4652.toInt(), 0xFF0C303B.toInt(), 0xFF0C303B.toInt(), 0xFF0C303B.toInt(), 0xFF0C303B.toInt())

        val TEXT = 0xFFF8F8F8.toInt()
        val TEXT_SHADOW = 0xFF1A2A3A.toInt()
        val HP_LABEL = 0xFFF8D048.toInt()
        val MALE = 0xFF9CC4FF.toInt()
        val FEMALE = 0xFFFFA8B4.toInt()
        val STAR = 0xFFFFE178.toInt()
        val BAR_OUTLINE = 0xFF1A2430.toInt()
        val BAR_EMPTY = 0xFF3A4652.toInt()
        val HP_GREEN = 0xFF4CC878.toInt()
        val HP_GREEN_HI = 0xFF9CF0B4.toInt()
        val HP_YELLOW = 0xFFF0C030.toInt()
        val HP_YELLOW_HI = 0xFFFFE890.toInt()
        val HP_RED = 0xFFF05040.toInt()
        val HP_RED_HI = 0xFFFFA090.toInt()
        val STATUS_OUTLINE = 0xFF1A2430.toInt()
        val PRESS_OVERLAY = 0x30FFFFFF

        /** Original 12×12 Poké Ball sprite. k outline, r/R red and highlight, w/g white and shade. */
        val BALL = listOf(
            "....kkkk....",
            "..kkrrrrkk..",
            ".kRRrrrrrrk.",
            ".kRrrrrrrrk.",
            "krrrkkkkrrrk",
            "kkkkwwwwkkkk",
            "kkkkwwwwkkkk",
            "kwwwkkkkwwwk",
            ".kwwwwwwwwk.",
            ".kwwwwwwwgk.",
            "..kkggggkk..",
            "....kkkk....",
        )
        const val BALL_SPLIT_ROW = 4
        val BALL_COLORS = mapOf(
            'k' to 0xFF202028.toInt(),
            'r' to 0xFFE83838.toInt(),
            'R' to 0xFFF8A0A0.toInt(),
            'w' to 0xFFF8F8F8.toInt(),
            'g' to 0xFFB8B8C8.toInt(),
        )
    }
}

/**
 * Lays out the six [PartySlotView]s at the largest integer scale that fits the width and
 * [maxHeightFraction] of the height it is offered (3×2 on the Thor's wide bottom screen).
 */
class PartySlotGrid(context: Context, onSlotClicked: (Int) -> Unit) : ViewGroup(context) {
    val slots: List<PartySlotView> = List(PartySlotModel.SLOT_COUNT) { i ->
        PartySlotView(context).apply {
            setIndex(i)
            setOnClickListener { onSlotClicked(i) }
        }
    }
    private val gap = context.dp(DualDexTheme.Spacing.tight)
    private var spec = PartyGridSpec(3, 2, 1, PartySlotModel.MIN_WINDOW_WIDTH)
    var maxHeightFraction = 0.55f

    init {
        slots.forEach { addView(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val offered = MeasureSpec.getSize(heightMeasureSpec)
        val maxHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || offered == 0) {
            resources.displayMetrics.heightPixels / 2
        } else {
            (offered * maxHeightFraction).toInt()
        }
        spec = PartySlotModel.chooseGrid(width, maxHeight, gap)
        val cellW = (width - gap * (spec.columns - 1)) / spec.columns
        val cellH = PartySlotModel.slotViewHeight * spec.scale
        slots.forEach {
            it.setGrid(spec.scale, spec.windowWidth)
            it.measure(MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(width, cellH * spec.rows + gap * (spec.rows - 1))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        slots.forEachIndexed { i, slot ->
            val col = i % spec.columns
            val row = i / spec.columns
            val x = col * (slot.measuredWidth + gap)
            val y = row * (slot.measuredHeight + gap)
            slot.layout(x, y, x + slot.measuredWidth, y + slot.measuredHeight)
        }
    }
}
