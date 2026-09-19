package com.dualdex.companion.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.dualdex.pokemon.MapNodeType
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import kotlin.math.max
import kotlin.math.min

class RegionMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Which static canvas is drawn. Changing this is browsing only: it does not
     * touch the active ROM, its trust, or the location strategy, and it clears the
     * live marker because the live position belongs to whichever region it is in.
     */
    var currentRegion: RegionId = RegionId.JOHTO
        set(value) {
            field = value
            sections = RegionMapDatabase.getSections(value)
            invalidate()
        }

    var playerLocation: PlayerLocation? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Resolved live position from the active game/profile. Never inferred from
     * [currentRegion], and never used to place a marker unless it is presentable
     * and belongs to the region currently being drawn.
     */
    var resolvedLocation: RegionMapSection? = null
        set(value) {
            field = value
            invalidate()
        }

    /** The section the detail sheet is describing. */
    var selectedSection: RegionMapSection? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * The section that may carry a live player marker.
     *
     * Presentation, region and the browsed canvas must all agree, so browsing a
     * Kanto canvas can never draw the player's Johto position over it.
     */
    val liveMarkerSection: RegionMapSection?
        get() {
            if (playerLocation?.isValid != true) return null
            val section = resolvedLocation ?: return null
            if (!section.presentable) return null
            if (section.region != currentRegion) return null
            if (section.gridX < 0 || section.gridY < 0) return null
            return section
        }

    /** [isLive] is true only for the player's own resolved position. */
    var onSectionSelected: ((RegionMapSection, Boolean) -> Unit)? = null

    private var sections: List<RegionMapSection> = RegionMapDatabase.getSections(currentRegion)

    // Standard GBA Town Map is 28 columns x 15 rows
    private val gridCols = 28
    private val gridRows = 15

    // Transform State (Pan & Zoom)
    private var scaleFactor = 1.0f
    private val minScale = 0.8f
    private val maxScale = 3.5f
    private var translationX = 0f
    private var translationY = 0f

    private val baseRect = RectF()
    private val tempRect = RectF()

    // Paints (pre-allocated)
    private val bgPaint = Paint().apply {
        color = 0xFF0D1B2A.toInt() // Deep ocean blue
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = 0x1A4A9EFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val landPaint = Paint().apply {
        color = 0xFF1E293B.toInt()
        style = Paint.Style.FILL
    }

    private val routePaint = Paint().apply {
        color = 0xFF475569.toInt()
        style = Paint.Style.FILL
    }

    private val routeStrokePaint = Paint().apply {
        color = 0xFF94A3B8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val cityPaint = Paint().apply {
        color = 0xFF3B82F6.toInt() // Blue for Cities
        style = Paint.Style.FILL
    }

    private val townPaint = Paint().apply {
        color = 0xFF10B981.toInt() // Emerald green for Towns
        style = Paint.Style.FILL
    }

    private val dungeonPaint = Paint().apply {
        color = 0xFF8B5CF6.toInt() // Purple for Dungeons / Caves
        style = Paint.Style.FILL
    }

    private val facilityPaint = Paint().apply {
        color = 0xFFF59E0B.toInt() // Amber for Facilities
        style = Paint.Style.FILL
    }

    private val nodeStrokePaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
    }

    private val selectionPaint = Paint().apply {
        color = 0xFFFFD700.toInt() // Gold highlight
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = 0xFFF8FAFC.toInt()
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, 0xFF000000.toInt())
    }

    private val playerPulsePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val playerDotPaint = Paint().apply {
        color = 0xFFEF4444.toInt() // Vibrant red
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val playerBadgePaint = Paint().apply {
        color = 0xEE1E293B.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val playerTextPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val textBgPaint = Paint().apply {
        color = 0xDE0B132B.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textStrokePaint = Paint().apply {
        color = 0x8838BDF8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        isAntiAlias = true
    }

    // Touch & Gesture Detection
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = scaleFactor
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)

            // Zoom centered around gesture focus
            val focusX = detector.focusX
            val focusY = detector.focusY
            translationX += (focusX - translationX) * (1f - scaleFactor / prevScale)
            translationY += (focusY - translationY) * (1f - scaleFactor / prevScale)

            clampTranslation()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }
    })

    init {
        setWillNotDraw(false)
    }

    /**
     * Centre the canvas on the live player position.
     *
     * A no-op unless a presentable live position exists on the region currently
     * being drawn, so the control can never imply a position DualDex does not have.
     */
    fun canCenterOnPlayer(): Boolean = liveMarkerSection != null

    fun centerOnPlayer() {
        val sec = liveMarkerSection ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val tileSize = min(w / gridCols, h / gridRows)
        val mapW = gridCols * tileSize
        val mapH = gridRows * tileSize
        val startX = (w - mapW) / 2f
        val startY = (h - mapH) / 2f

        scaleFactor = 1.8f
        val targetX = startX + (sec.gridX + sec.width / 2f) * tileSize
        val targetY = startY + (sec.gridY + sec.height / 2f) * tileSize

        translationX = (w / 2f) - targetX * scaleFactor
        translationY = (h / 2f) - targetY * scaleFactor

        clampTranslation()
        selectedSection = sec
        onSectionSelected?.invoke(sec, true)
        invalidate()
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        translationX = 0f
        translationY = 0f
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (kotlin.math.abs(dx) > 3f || kotlin.math.abs(dy) > 3f) {
                        isDragging = true
                        translationX += dx
                        translationY += dy
                        clampTranslation()
                        invalidate()
                    }
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
            }
        }
        return true
    }

    private fun handleTap(touchX: Float, touchY: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val tileSize = min(w / gridCols, h / gridRows)
        val mapW = gridCols * tileSize
        val mapH = gridRows * tileSize
        val startX = (w - mapW) / 2f
        val startY = (h - mapH) / 2f

        // Convert touch screen coords to map grid coords
        val transformedX = (touchX - translationX) / scaleFactor
        val transformedY = (touchY - translationY) / scaleFactor

        val mapRelX = transformedX - startX
        val mapRelY = transformedY - startY

        val tapGridX = (mapRelX / tileSize).toInt()
        val tapGridY = (mapRelY / tileSize).toInt()

        // Find clicked section (check cities and landmarks first for priority over overlapping routes)
        val sorted = sections.sortedByDescending { it.nodeType != MapNodeType.ROUTE }
        val hit = sorted.firstOrNull { sec ->
            tapGridX >= sec.gridX && tapGridX < (sec.gridX + sec.width) &&
            tapGridY >= sec.gridY && tapGridY < (sec.gridY + sec.height)
        }

        if (hit != null) {
            selectedSection = hit
            onSectionSelected?.invoke(hit, false)
            invalidate()
        }
    }

    private fun clampTranslation() {
        val w = width.toFloat()
        val h = height.toFloat()
        val maxTransX = w * (scaleFactor - 0.5f)
        val maxTransY = h * (scaleFactor - 0.5f)
        translationX = translationX.coerceIn(-maxTransX, maxTransX)
        translationY = translationY.coerceIn(-maxTransY, maxTransY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Fill ocean background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Compute map tile scale and centering
        val tileSize = min(w / gridCols, h / gridRows)
        val mapW = gridCols * tileSize
        val mapH = gridRows * tileSize
        val startX = (w - mapW) / 2f
        val startY = (h - mapH) / 2f

        canvas.save()
        canvas.translate(translationX, translationY)
        canvas.scale(scaleFactor, scaleFactor)

        // Draw soft grid lines
        for (c in 0..gridCols) {
            val gx = startX + c * tileSize
            canvas.drawLine(gx, startY, gx, startY + mapH, gridPaint)
        }
        for (r in 0..gridRows) {
            val gy = startY + r * tileSize
            canvas.drawLine(startX, gy, startX + mapW, gy, gridPaint)
        }

        // 3. Draw Routes & Paths first (Layer 1)
        sections.filter { it.nodeType == MapNodeType.ROUTE }.forEach { sec ->
            drawRouteNode(canvas, sec, startX, startY, tileSize)
        }

        // 4. Draw Towns, Cities, Facilities & Dungeons (Layer 2)
        sections.filter { it.nodeType != MapNodeType.ROUTE }.forEach { sec ->
            drawLocationNode(canvas, sec, startX, startY, tileSize)
        }

        // 5. Draw Selected Location Highlight
        selectedSection?.let { sel ->
            val selL = startX + sel.gridX * tileSize - 3f
            val selT = startY + sel.gridY * tileSize - 3f
            val selR = selL + sel.width * tileSize + 6f
            val selB = selT + sel.height * tileSize + 6f
            tempRect.set(selL, selT, selR, selB)
            canvas.drawRoundRect(tempRect, 10f, 10f, selectionPaint)
        }

        // 6. Draw Live Player Marker (Layer 3)
        drawPlayerMarker(canvas, startX, startY, tileSize)

        // 7. Draw Clean Uncluttered Location Labels (Layer 4)
        drawLocationLabels(canvas, startX, startY, tileSize)

        canvas.restore()

        // Keep radar pulse animating smoothly
        postInvalidateDelayed(50L)
    }

    private fun drawRouteNode(canvas: Canvas, sec: RegionMapSection, startX: Float, startY: Float, tileSize: Float) {
        val l = startX + sec.gridX * tileSize + 2f
        val t = startY + sec.gridY * tileSize + 2f
        val r = l + sec.width * tileSize - 4f
        val b = t + sec.height * tileSize - 4f

        tempRect.set(l, t, r, b)
        val radius = min(tempRect.width(), tempRect.height()) * 0.35f
        canvas.drawRoundRect(tempRect, radius, radius, routePaint)
        canvas.drawRoundRect(tempRect, radius, radius, routeStrokePaint)
    }

    private fun drawLocationNode(canvas: Canvas, sec: RegionMapSection, startX: Float, startY: Float, tileSize: Float) {
        val l = startX + sec.gridX * tileSize + 3f
        val t = startY + sec.gridY * tileSize + 3f
        val r = l + sec.width * tileSize - 6f
        val b = t + sec.height * tileSize - 6f

        tempRect.set(l, t, r, b)
        val radius = 8f

        val paint = when (sec.nodeType) {
            MapNodeType.CITY -> cityPaint
            MapNodeType.TOWN -> townPaint
            MapNodeType.DUNGEON -> dungeonPaint
            MapNodeType.FACILITY -> facilityPaint
            else -> cityPaint
        }

        canvas.drawRoundRect(tempRect, radius, radius, paint)
        canvas.drawRoundRect(tempRect, radius, radius, nodeStrokePaint)
    }

    private fun drawLocationLabels(canvas: Canvas, startX: Float, startY: Float, tileSize: Float) {
        val density = context.resources.displayMetrics.density
        val baseFontSize = 11.5f * density
        // Prevent font from growing too large on zoom; keep it around 8-13sp in visual screen coordinates
        textPaint.textSize = (baseFontSize / scaleFactor).coerceIn(7.5f * density, 13f * density)

        val sel = selectedSection

        sections.forEach { sec ->
            val isSelected = (sel?.name == sec.name)
            val isCity = (sec.nodeType == MapNodeType.CITY)
            val isTown = (sec.nodeType == MapNodeType.TOWN)

            // Show label if:
            // 1. It is selected (always)
            // 2. It is a Major City (if scaleFactor >= 0.9f)
            // 3. It is a Town (if scaleFactor >= 1.4f)
            val shouldShow = isSelected || (isCity && scaleFactor >= 0.9f) || (isTown && scaleFactor >= 1.4f)
            if (!shouldShow) return@forEach

            val l = startX + sec.gridX * tileSize + 3f
            val t = startY + sec.gridY * tileSize + 3f
            val r = l + sec.width * tileSize - 6f
            val cx = (l + r) / 2f
            val topY = t

            val text = if (isSelected) sec.name else sec.name.replace(" City", "").replace(" Town", "")
            val textWidth = textPaint.measureText(text)
            val padH = 6f / scaleFactor * density
            val padV = 3f / scaleFactor * density
            val badgeW = textWidth + padH * 2f

            val fontMetrics = textPaint.fontMetrics
            val textH = fontMetrics.descent - fontMetrics.ascent
            val badgeH = textH + padV * 2f

            val badgeL = cx - badgeW / 2f
            val badgeB = topY - (4f / scaleFactor * density)
            val badgeT = badgeB - badgeH
            tempRect.set(badgeL, badgeT, badgeL + badgeW, badgeB)

            val radius = 4f / scaleFactor * density
            textBgPaint.color = if (isSelected) 0xF21E293B.toInt() else 0xD00F172A.toInt()
            canvas.drawRoundRect(tempRect, radius, radius, textBgPaint)

            if (isSelected) {
                textStrokePaint.color = 0xFFFFD700.toInt()
                textStrokePaint.strokeWidth = 1.8f / scaleFactor * density
                canvas.drawRoundRect(tempRect, radius, radius, textStrokePaint)
            } else if (isCity) {
                textStrokePaint.color = 0x6638BDF8.toInt()
                textStrokePaint.strokeWidth = 1.0f / scaleFactor * density
                canvas.drawRoundRect(tempRect, radius, radius, textStrokePaint)
            }

            val textBaseline = badgeT + padV - fontMetrics.ascent
            textPaint.color = if (isSelected) 0xFFFFD700.toInt() else Color.WHITE
            canvas.drawText(text, cx, textBaseline, textPaint)
        }
    }

    private fun drawPlayerMarker(canvas: Canvas, startX: Float, startY: Float, tileSize: Float) {
        val currentSec = liveMarkerSection ?: return

        val px = startX + (currentSec.gridX + currentSec.width / 2f) * tileSize
        val py = startY + (currentSec.gridY + currentSec.height / 2f) * tileSize
        val density = context.resources.displayMetrics.density
        val contentScale = scaleFactor.coerceAtLeast(0.01f)
        val markerRadius = min(tileSize * 0.35f, 10f * density / contentScale)

        // Animated Radar Ping Wave
        val now = System.currentTimeMillis()
        val pulseFraction = ((now % 1600L).toFloat() / 1600f)
        val pulseRadius = markerRadius * (1.5f + pulseFraction * 1.5f)
        val alpha = ((1.0f - pulseFraction) * 230).toInt().coerceIn(0, 255)

        playerPulsePaint.color = Color.argb(alpha, 239, 68, 68)
        canvas.drawCircle(px, py, pulseRadius, playerPulsePaint)

        // Solid Player Center Dot
        val dotRadius = markerRadius
        val outline = 2f * density / contentScale
        canvas.drawCircle(px, py, dotRadius + outline, nodeStrokePaint)
        canvas.drawCircle(px, py, dotRadius, playerDotPaint)

        // Keep the marker readable in screen pixels while the map content zooms underneath it.
        val badgeW = 52f * density / contentScale
        val badgeH = 22f * density / contentScale
        val badgeX = px - badgeW / 2f
        val badgeY = py - markerRadius - badgeH - 6f * density / contentScale

        tempRect.set(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
        val badgeRadius = 6f * density / contentScale
        canvas.drawRoundRect(tempRect, badgeRadius, badgeRadius, playerBadgePaint)
        canvas.drawRoundRect(tempRect, badgeRadius, badgeRadius, nodeStrokePaint)
        playerTextPaint.textSize = 12f * density / contentScale
        val metrics = playerTextPaint.fontMetrics
        val baseline = badgeY + badgeH / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText("YOU", px, baseline, playerTextPaint)
    }
}
