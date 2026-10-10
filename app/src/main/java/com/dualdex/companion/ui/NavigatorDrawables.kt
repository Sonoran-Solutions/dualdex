package com.dualdex.companion.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Thin outlined LCD panel with clipped corners (issue #133). The top-left and bottom-right
 * corners take the full [chamferPx] cut; the other two take a small one, which reads as a
 * device panel rather than a generic rounded card. An optional [headerColor] draws a short
 * strip embedded in the top border.
 */
class NavigatorPanelDrawable(
    private val fillColor: Int,
    private val strokeColor: Int,
    private val strokePx: Float,
    private val chamferPx: Float,
    private val headerColor: Int? = null,
    /** When set, everything outside the chamfered outline is painted in this colour, so the
     *  drawable works as a foreground that cuts the corners of rectangular children. */
    private val maskColor: Int? = null,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = fillColor }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = strokeColor
        strokeWidth = strokePx
    }
    private val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val inset = strokePx / 2f
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        val big = chamferPx.coerceAtMost(minOf(rect.width(), rect.height()) / 3f)
        val small = big / 3f
        path.reset()
        path.moveTo(rect.left + big, rect.top)
        path.lineTo(rect.right - small, rect.top)
        path.lineTo(rect.right, rect.top + small)
        path.lineTo(rect.right, rect.bottom - big)
        path.lineTo(rect.right - big, rect.bottom)
        path.lineTo(rect.left + small, rect.bottom)
        path.lineTo(rect.left, rect.bottom - small)
        path.lineTo(rect.left, rect.top + big)
        path.close()
        if (fill.color != android.graphics.Color.TRANSPARENT) canvas.drawPath(path, fill)
        maskColor?.let {
            header.color = it
            path.fillType = Path.FillType.INVERSE_WINDING
            canvas.save()
            canvas.clipRect(b)
            canvas.drawPath(path, header)
            canvas.restore()
            path.fillType = Path.FillType.WINDING
        }
        headerColor?.let {
            header.color = it
            val h = strokePx * 3f
            canvas.drawRect(rect.left + big + strokePx * 2f, rect.top - inset, rect.left + big + rect.width() * 0.22f, rect.top - inset + h, header)
        }
        if (strokePx > 0f && stroke.color != android.graphics.Color.TRANSPARENT) canvas.drawPath(path, stroke)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        stroke.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        stroke.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * The one-device frame behind the whole companion: a thin light shell rim with a bevel highlight,
 * and a dark bezel inset by [shellPx] that carries the status strip, viewport, and soft keys.
 */
class NavigatorFrameDrawable(
    private val shellPx: Float,
    private val bezelRadiusPx: Float,
) : Drawable() {
    private val shell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DualDexTheme.Color.shell }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DualDexTheme.Color.shellHighlight
        style = Paint.Style.STROKE
        strokeWidth = (shellPx / 4f).coerceAtLeast(1f)
    }
    private val bezel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DualDexTheme.Color.bezel }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), shell)
        val h = highlight.strokeWidth / 2f
        rect.set(b.left + h, b.top + h, b.right - h, b.bottom - h)
        canvas.drawRect(rect, highlight)
        rect.set(b.left + shellPx, b.top + shellPx, b.right - shellPx, b.bottom - shellPx)
        canvas.drawRoundRect(rect, bezelRadiusPx, bezelRadiusPx, bezel)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
