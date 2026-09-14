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
        isFocusableInTouchMode = true
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
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
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
        isFocusableInTouchMode = true
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
        isFocusableInTouchMode = true
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
