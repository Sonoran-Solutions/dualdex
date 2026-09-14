package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.companion.CompanionTab

/** Secondary companion destinations, kept out of the five-item primary navigation. */
class MoreScreenView(
    context: Context,
    private val onTabSelected: (CompanionTab) -> Unit
) : ScrollView(context) {
    init {
        isVerticalScrollBarEnabled = true
        setBackgroundColor(DualDexTheme.Color.background)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.major)
            )
        }
        addView(content)

        content.addView(title("More"))
        content.addView(description("Utilities and secondary companion tools."), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.section) })

        content.addView(sectionTitle("Battle tools"))
        content.addView(row("Calculator", "Manual damage calculations", CompanionTab.CALC))
        content.addView(row("Type Matchups", "Manual strengths and weaknesses", CompanionTab.TYPES))

        content.addView(sectionTitle("Utilities").apply {
            setPadding(0, context.dp(DualDexTheme.Spacing.section), 0, context.dp(DualDexTheme.Spacing.compact))
        })
        content.addView(row("Saves", "Battery saves, states, and recovery", CompanionTab.SAVES))
        content.addView(row("Docs", "Game documentation", CompanionTab.DOCS))
        content.addView(row("Assistant", "ROM hack help", CompanionTab.ASSISTANT))
        content.addView(row("Cheats", "Manage game-specific cheats", CompanionTab.CHEATS))
        content.addView(row("Settings", "Display, controls, and storage", CompanionTab.SETTINGS))
    }

    private fun title(value: String) = TextView(context).apply {
        text = value
        setTextColor(DualDexTheme.Color.textPrimary)
        textSize = DualDexTheme.Type.screenTitle
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun description(value: String) = TextView(context).apply {
        text = value
        setTextColor(DualDexTheme.Color.textSecondary)
        textSize = DualDexTheme.Type.body
        setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
    }

    private fun sectionTitle(value: String) = TextView(context).apply {
        text = value
        setTextColor(DualDexTheme.Color.textSecondary)
        textSize = DualDexTheme.Type.meta
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
    }

    private fun row(title: String, subtitle: String, tab: CompanionTab): View =
        DualDexComponents.menuRow(context, title, subtitle) { onTabSelected(tab) }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.tight) }
        }
}
