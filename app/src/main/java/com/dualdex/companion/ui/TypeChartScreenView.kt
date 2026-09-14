package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.TypeChart

class TypeChartScreenView(
    context: Context,
    private val viewModel: CompanionViewModel? = null
) : LinearLayout(context) {

    private var selectedType1: PokemonType = PokemonType.FIRE
    private var selectedType2: PokemonType? = PokemonType.FLYING
    private var steelResistsGhostDark = false

    private val resultContainer: LinearLayout
    private val targetSummaryView: TextView
    private val type1Buttons = mutableMapOf<PokemonType, View>()
    private val type2Buttons = mutableMapOf<PokemonType, View>()
    private val noneType2Button: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)
        setPadding(
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.standard),
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.major)
        )

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        scroll.addView(content)
        addView(scroll)

        // Title
        content.addView(DualDexComponents.screenTitle(context, "Type Matchups"), LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.standard)
        })

        // Defensive Profile Results Card
        val resCard = createSurfaceLayout().apply {
            addView(DualDexComponents.sectionTitle(context, "Defensive Profile"))

            targetSummaryView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(targetSummaryView)

            resultContainer = LinearLayout(context).apply {
                orientation = VERTICAL
            }
            addView(resultContainer)
        }
        content.addView(resCard)

        // Primary Type Selector Card
        val type1Card = createSurfaceLayout().apply {
            addView(DualDexComponents.sectionTitle(context, "Primary Type").apply {
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
            })

            val grid = createTypePickerGrid(isSecondary = false) { chosen ->
                selectedType1 = chosen
                updateButtonStates()
                updateMatchupDisplay()
            }
            addView(grid)
        }
        content.addView(type1Card)

        // Secondary Type Selector Card
        val type2Card = createSurfaceLayout().apply {
            val topRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
            }
            topRow.addView(DualDexComponents.sectionTitle(context, "Secondary Type"), LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f
            ))

            noneType2Button = TextView(context).apply {
                text = "None (Mono)"
                textSize = DualDexTheme.Type.compact
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(
                    context.dp(DualDexTheme.Spacing.standard),
                    context.dp(DualDexTheme.Spacing.tight),
                    context.dp(DualDexTheme.Spacing.standard),
                    context.dp(DualDexTheme.Spacing.tight)
                )
                isFocusable = true
                isFocusableInTouchMode = false
                isClickable = true
                setOnClickListener {
                    selectedType2 = null
                    updateButtonStates()
                    updateMatchupDisplay()
                }
            }
            topRow.addView(noneType2Button)
            addView(topRow)

            val grid = createTypePickerGrid(isSecondary = true) { chosen ->
                selectedType2 = chosen
                updateButtonStates()
                updateMatchupDisplay()
            }
            addView(grid)
        }
        content.addView(type2Card)

        updateButtonStates()
        updateMatchupDisplay()
    }

    fun setTypes(type1: PokemonType, type2: PokemonType?) {
        selectedType1 = type1
        selectedType2 = if (type2 != type1) type2 else null
        updateButtonStates()
        updateMatchupDisplay()
    }

    private fun updateButtonStates() {
        for ((type, view) in type1Buttons) {
            val isSel = type == selectedType1
            view.isSelected = isSel
            view.background = typeBadgeBackground(type, isSel)
        }
        val isMono = selectedType2 == null
        noneType2Button.isSelected = isMono
        noneType2Button.setTextColor(if (isMono) DualDexTheme.Color.textPrimary else DualDexTheme.Color.textSecondary)
        noneType2Button.background = if (isMono) {
            DualDexComponents.controlBackground(context, DualDexButtonStyle.SECONDARY, selected = true)
        } else {
            DualDexComponents.controlBackground(context, DualDexButtonStyle.GHOST, selected = false)
        }

        for ((type, view) in type2Buttons) {
            val isSel = type == selectedType2
            view.isSelected = isSel
            view.background = typeBadgeBackground(type, isSel)
        }
    }

    fun updateMatchupDisplay() {
        resultContainer.removeAllViews()

        val steelResists = viewModel?.activeProfile?.value?.steelResistsGhostDark ?: steelResistsGhostDark
        val profile = TypeChart.getDefenseProfile(selectedType1, selectedType2, steelResists)

        val t2Str = selectedType2?.let { " · ${it.displayName}" } ?: " · Mono"
        targetSummaryView.text = "${selectedType1.displayName}$t2Str"

        // 4x Weaknesses
        if (profile.weaknesses4x.isNotEmpty()) {
            addMatchupRow("4× Weak", profile.weaknesses4x)
        }

        // 2x Weaknesses
        if (profile.weaknesses2x.isNotEmpty()) {
            addMatchupRow("2× Weak", profile.weaknesses2x)
        }

        // 0.5x Resistances
        if (profile.resistancesHalf.isNotEmpty()) {
            addMatchupRow("½× Resist", profile.resistancesHalf)
        }

        // 0.25x Resistances
        if (profile.resistancesQuarter.isNotEmpty()) {
            addMatchupRow("¼× Resist", profile.resistancesQuarter)
        }

        // Immunities
        if (profile.immunities.isNotEmpty()) {
            addMatchupRow("Immune", profile.immunities)
        }

        if (resultContainer.childCount == 0) {
            resultContainer.addView(TextView(context).apply {
                text = "Neutral to all attack types."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
            })
        }
    }

    private fun addMatchupRow(label: String, types: List<PokemonType>) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.tight))
        }
        val labelView = TextView(context).apply {
            text = label
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        row.addView(labelView, LayoutParams(context.dp(68), LayoutParams.WRAP_CONTENT))

        val badgesContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        types.forEach { type ->
            badgesContainer.addView(DualDexComponents.typeBadge(context, type), LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.compact)
            })
        }
        row.addView(badgesContainer, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        resultContainer.addView(row)
    }

    private fun createTypePickerGrid(isSecondary: Boolean, onSelected: (PokemonType) -> Unit): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        val allTypes = PokemonType.values().toList()
        val targetMap = if (isSecondary) type2Buttons else type1Buttons

        // 3 rows of 6 types
        for (row in 0 until 3) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, context.dp(2), 0, context.dp(2))
            }
            for (col in 0 until 6) {
                val idx = row * 6 + col
                if (idx < allTypes.size) {
                    val t = allTypes[idx]
                    val btn = TextView(context).apply {
                        text = t.displayName
                        textSize = DualDexTheme.Type.compact
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(DualDexTheme.Color.textPrimary)
                        gravity = Gravity.CENTER
                        minimumHeight = context.dp(32)
                        setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))
                        isFocusable = true
                        isFocusableInTouchMode = false
                        isClickable = true
                        setOnClickListener { onSelected(t) }
                    }
                    targetMap[t] = btn
                    rowLayout.addView(btn, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply {
                        setMargins(context.dp(2), 0, context.dp(2), 0)
                    })
                }
            }
            container.addView(rowLayout)
        }

        return container
    }

    private fun typeBadgeBackground(type: PokemonType, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(DualDexTheme.Radius.control).toFloat()
            setColor(type.colorHex.toInt())
            if (selected) {
                setStroke(context.dp(2), DualDexTheme.Color.textPrimary)
            } else {
                setStroke(context.dp(1), DualDexTheme.Color.transparent)
            }
        }
    }

    private fun createSurfaceLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.standard)
            )
            background = DualDexComponents.surface(context)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(DualDexTheme.Spacing.section)
            }
        }
    }
}
