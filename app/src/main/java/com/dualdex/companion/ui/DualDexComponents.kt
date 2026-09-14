package com.dualdex.companion.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.ProgressBar
import com.dualdex.pokemon.PokemonType
import androidx.annotation.DrawableRes

enum class DualDexButtonStyle { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE }

object DualDexComponents {
    private data class ControlStateColors(
        val normal: Int,
        val pressed: Int,
        val selected: Int,
        val focused: Int,
        val disabled: Int,
        val stroke: Int
    )

    fun surface(context: Context, elevated: Boolean = false): GradientDrawable = roundedDrawable(
        context = context,
        color = if (elevated) DualDexTheme.Color.elevatedSurface else DualDexTheme.Color.surface,
        radiusDp = DualDexTheme.Radius.surface,
        strokeColor = DualDexTheme.Color.border
    )

    fun surfaceCard(context: Context, elevated: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = surface(context, elevated)
        setPadding(
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section)
        )
    }

    fun primaryButton(context: Context, text: CharSequence, onClick: (() -> Unit)? = null): TextView =
        button(context, text, DualDexButtonStyle.PRIMARY, onClick)

    fun secondaryButton(context: Context, text: CharSequence, onClick: (() -> Unit)? = null): TextView =
        button(context, text, DualDexButtonStyle.SECONDARY, onClick)

    fun ghostControl(context: Context, text: CharSequence, onClick: (() -> Unit)? = null): TextView =
        button(context, text, DualDexButtonStyle.GHOST, onClick)

    fun destructiveButton(context: Context, text: CharSequence, onClick: (() -> Unit)? = null): TextView =
        button(context, text, DualDexButtonStyle.DESTRUCTIVE, onClick)

    fun styledInput(context: Context, hint: CharSequence): EditText = EditText(context).apply {
        this.hint = hint
        minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
        textSize = DualDexTheme.Type.body
        setTextColor(DualDexTheme.Color.textPrimary)
        setHintTextColor(DualDexTheme.Color.textSecondary)
        setPadding(
            context.dp(DualDexTheme.Spacing.standard),
            0,
            context.dp(DualDexTheme.Spacing.standard),
            0
        )
        background = controlBackground(context, DualDexButtonStyle.SECONDARY, selected = false)
    }

    fun screenTitle(context: Context, text: CharSequence): TextView = TextView(context).apply {
        this.text = text
        setTextColor(DualDexTheme.Color.textPrimary)
        textSize = DualDexTheme.Type.screenTitle
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    fun sectionTitle(context: Context, text: CharSequence): TextView = TextView(context).apply {
        this.text = text
        setTextColor(DualDexTheme.Color.textPrimary)
        textSize = DualDexTheme.Type.sectionTitle
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(DualDexTheme.Color.border)
    }

    fun emptyState(context: Context, title: CharSequence, detail: CharSequence): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.major),
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.major)
        )
        addView(TextView(context).apply {
            text = title
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = detail
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            gravity = Gravity.CENTER
            setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, 0)
        })
    }

    fun typeBadge(context: Context, type: PokemonType, textSizeSp: Float = DualDexTheme.Type.compact): TextView = TextView(context).apply {
        text = type.displayName
        setTextColor(DualDexTheme.Color.textPrimary)
        textSize = textSizeSp
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(
            context.dp(DualDexTheme.Spacing.compact),
            context.dp(DualDexTheme.Spacing.tight),
            context.dp(DualDexTheme.Spacing.compact),
            context.dp(DualDexTheme.Spacing.tight)
        )
        background = roundedDrawable(context, type.colorHex.toInt(), DualDexTheme.Radius.pill)
    }

    fun menuRow(
        context: Context,
        title: CharSequence,
        subtitle: CharSequence,
        onClick: (() -> Unit)?
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
        setPadding(
            context.dp(DualDexTheme.Spacing.standard),
            context.dp(DualDexTheme.Spacing.compact),
            context.dp(DualDexTheme.Spacing.compact),
            context.dp(DualDexTheme.Spacing.compact)
        )
        background = controlBackground(context, DualDexButtonStyle.GHOST, selected = false)
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = onClick != null
        contentDescription = title
        onClick?.let { setOnClickListener { it() } }

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                typeface = Typeface.DEFAULT_BOLD
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "›"
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.chevron
            gravity = Gravity.CENTER
            contentDescription = null
        }, LinearLayout.LayoutParams(context.dp(DualDexTheme.Spacing.major), LinearLayout.LayoutParams.MATCH_PARENT))
    }

    fun navigationItem(
        context: Context,
        @DrawableRes iconRes: Int,
        label: String,
        onClick: () -> Unit
    ): DualDexNavigationItem = DualDexNavigationItem(context, iconRes, label, onClick)

    fun segmentedControl(
        context: Context,
        items: List<String>,
        initialIndex: Int = 0,
        onItemSelected: (Int) -> Unit
    ): DualDexSegmentedControl = DualDexSegmentedControl(context, items, initialIndex, onItemSelected)

    fun createHpBar(context: Context): ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressDrawable = hpProgressDrawable(context, DualDexTheme.Color.success)
        tag = DualDexTheme.Color.success
    }

    fun updateHpBar(bar: ProgressBar, current: Int, maximum: Int) {
        val ratio = if (maximum > 0) current.toFloat() / maximum else 0f
        val color = hpColor(current, maximum)
        bar.progress = (ratio.coerceIn(0f, 1f) * 1000).toInt()
        if (bar.tag != color) {
            bar.progressTintList = ColorStateList.valueOf(color)
            bar.tag = color
        }
    }

    fun hpColor(current: Int, maximum: Int): Int {
        val ratio = if (maximum > 0) current.toFloat() / maximum else 0f
        return when {
            ratio > 0.5f -> DualDexTheme.Color.success
            ratio > 0.2f -> DualDexTheme.Color.warning
            else -> DualDexTheme.Color.danger
        }
    }

    private fun hpProgressDrawable(context: Context, color: Int): LayerDrawable {
        val track = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(DualDexTheme.Radius.pill).toFloat()
            setColor(DualDexTheme.Color.surfaceDisabled)
        }
        val fill = ClipDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(DualDexTheme.Radius.pill).toFloat()
            setColor(color)
        }, Gravity.START, ClipDrawable.HORIZONTAL)
        return LayerDrawable(arrayOf(track, fill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    private fun button(
        context: Context,
        text: CharSequence,
        style: DualDexButtonStyle,
        onClick: (() -> Unit)?
    ): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
        minWidth = context.dp(DualDexTheme.Spacing.touchTarget)
        setPadding(context.dp(DualDexTheme.Spacing.standard), 0, context.dp(DualDexTheme.Spacing.standard), 0)
        textSize = DualDexTheme.Type.meta
        typeface = Typeface.DEFAULT_BOLD
        val foreground = when (style) {
            DualDexButtonStyle.PRIMARY -> DualDexTheme.Color.onAccent
            DualDexButtonStyle.DESTRUCTIVE -> DualDexTheme.Color.onDanger
            DualDexButtonStyle.SECONDARY, DualDexButtonStyle.GHOST -> DualDexTheme.Color.textPrimary
        }
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(DualDexTheme.Color.textDisabled, foreground)
        ))
        background = controlBackground(context, style, selected = false)
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = onClick != null
        onClick?.let { setOnClickListener { it() } }
    }

    internal fun controlBackground(
        context: Context,
        style: DualDexButtonStyle,
        selected: Boolean
    ): StateListDrawable {
        val colors = when (style) {
            DualDexButtonStyle.PRIMARY -> ControlStateColors(
                DualDexTheme.Color.accent, DualDexTheme.Color.accentPressed, DualDexTheme.Color.accent,
                DualDexTheme.Color.accentFocused, DualDexTheme.Color.surfaceDisabled, DualDexTheme.Color.accent
            )
            DualDexButtonStyle.SECONDARY -> ControlStateColors(
                DualDexTheme.Color.elevatedSurface, DualDexTheme.Color.surfacePressed, DualDexTheme.Color.surfaceSelected,
                DualDexTheme.Color.surfaceFocused, DualDexTheme.Color.surfaceDisabled, DualDexTheme.Color.border
            )
            DualDexButtonStyle.GHOST -> ControlStateColors(
                DualDexTheme.Color.transparent, DualDexTheme.Color.surfacePressed, DualDexTheme.Color.surfaceSelected,
                DualDexTheme.Color.surfaceFocused, DualDexTheme.Color.transparent, DualDexTheme.Color.transparent
            )
            DualDexButtonStyle.DESTRUCTIVE -> ControlStateColors(
                DualDexTheme.Color.danger, DualDexTheme.Color.dangerPressed, DualDexTheme.Color.danger,
                DualDexTheme.Color.dangerFocused, DualDexTheme.Color.surfaceDisabled, DualDexTheme.Color.danger
            )
        }
        return StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), roundedDrawable(context, colors.disabled, DualDexTheme.Radius.control, DualDexTheme.Color.border))
            addState(intArrayOf(android.R.attr.state_pressed), roundedDrawable(context, colors.pressed, DualDexTheme.Radius.control, colors.stroke))
            addState(intArrayOf(android.R.attr.state_focused), roundedDrawable(context, colors.focused, DualDexTheme.Radius.control, DualDexTheme.Color.focusRing, DualDexTheme.Control.focusStroke))
            addState(intArrayOf(android.R.attr.state_selected), roundedDrawable(context, colors.selected, DualDexTheme.Radius.control, DualDexTheme.Color.accent, DualDexTheme.Control.defaultStroke))
            addState(intArrayOf(), roundedDrawable(context, if (selected) colors.selected else colors.normal, DualDexTheme.Radius.control, colors.stroke))
        }
    }

    internal fun roundedDrawable(
        context: Context,
        color: Int,
        radiusDp: Int,
        strokeColor: Int = DualDexTheme.Color.transparent,
        strokeDp: Int = DualDexTheme.Control.defaultStroke
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(radiusDp).toFloat()
        if (strokeColor != DualDexTheme.Color.transparent) {
            setStroke(context.dp(strokeDp), strokeColor)
        }
    }
}

class DualDexNavigationItem(
    context: Context,
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) : LinearLayout(context) {
    private val icon = ImageView(context)
    private val labelView = TextView(context)
    private var selectedStyle = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
        setPadding(context.dp(DualDexTheme.Spacing.tight), context.dp(DualDexTheme.Spacing.tight), context.dp(DualDexTheme.Spacing.tight), context.dp(DualDexTheme.Spacing.tight))
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = true
        contentDescription = label
        setOnClickListener { onClick() }

        icon.setImageResource(iconRes)
        addView(icon, LayoutParams(context.dp(DualDexTheme.Control.navigationIcon), context.dp(DualDexTheme.Control.navigationIcon)))
        labelView.apply {
            text = label
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(0, context.dp(DualDexTheme.Spacing.tight / 2), 0, 0)
        }
        addView(labelView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setSelectedState(false)
    }

    fun setSelectedState(selected: Boolean) {
        selectedStyle = selected
        isSelected = selected
        background = DualDexComponents.controlBackground(context, DualDexButtonStyle.GHOST, selected)
        applyContentColor()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        applyContentColor()
    }

    private fun applyContentColor() {
        val color = when {
            !isEnabled -> DualDexTheme.Color.textDisabled
            selectedStyle -> DualDexTheme.Color.accent
            else -> DualDexTheme.Color.textSecondary
        }
        icon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        labelView.setTextColor(color)
    }
}

class DualDexSegmentedControl(
    context: Context,
    items: List<String>,
    initialIndex: Int = 0,
    private val onItemSelected: (Int) -> Unit
) : LinearLayout(context) {
    private val itemViews = ArrayList<TextView>()
    private var selectedIndex = initialIndex

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = DualDexComponents.roundedDrawable(
            context,
            DualDexTheme.Color.surface,
            DualDexTheme.Radius.control,
            DualDexTheme.Color.border
        )
        setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))

        items.forEachIndexed { index, title ->
            val tv = TextView(context).apply {
                text = title
                textSize = DualDexTheme.Type.compact
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                minimumHeight = context.dp(36)
                setPadding(context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.compact), 0)
                isFocusable = true
                isFocusableInTouchMode = false
                isClickable = true
                setOnClickListener {
                    setSelectedIndex(index)
                    onItemSelected(index)
                }
            }
            itemViews += tv
            addView(tv, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        applySelectionStyles()
    }

    fun setSelectedIndex(index: Int) {
        if (index in itemViews.indices && index != selectedIndex) {
            selectedIndex = index
            applySelectionStyles()
        }
    }

    fun getSelectedIndex(): Int = selectedIndex

    private fun applySelectionStyles() {
        itemViews.forEachIndexed { index, tv ->
            val isSelected = index == selectedIndex
            tv.isSelected = isSelected
            tv.setTextColor(if (isSelected) DualDexTheme.Color.textPrimary else DualDexTheme.Color.textSecondary)
            tv.background = if (isSelected) {
                DualDexComponents.controlBackground(context, DualDexButtonStyle.SECONDARY, selected = true)
            } else {
                DualDexComponents.controlBackground(context, DualDexButtonStyle.GHOST, selected = false)
            }
        }
    }
}
