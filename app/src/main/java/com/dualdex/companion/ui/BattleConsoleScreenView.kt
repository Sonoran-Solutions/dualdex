package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.battle.*
import com.dualdex.companion.CompanionViewModel
import com.dualdex.calculator.CalcSupport
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RomCompatibilityMessages
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

enum class BattleMode(val title: String) {
    BATTLE("Battle"),
    TYPE_MATCHUPS("Type Matchups"),
    DETAILS("Details")
}

@Deprecated("Use BattleMode instead", ReplaceWith("BattleMode"))
typealias ConsoleSubtab = BattleMode

/**
 * Redesigned Battle Console. Built as a quiet handheld companion instrument panel.
 * View hierarchy is retained across 10 Hz polling updates to preserve touch, scroll,
 * and controller focus states without creating emulator fast-forward hitches.
 */
class BattleConsoleScreenView(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val onOpenCalculatorRequested: (() -> Unit)? = null
) : LinearLayout(context) {

    // View Holders for incremental in-place updates
    private class CombatantHolder(
        val root: LinearLayout,
        val roleLabel: TextView,
        val nameView: TextView,
        val levelView: TextView,
        val typesRow: LinearLayout,
        val hpBar: ProgressBar,
        val hpTextView: TextView,
        val statusBadge: TextView,
        val statChipsRow: LinearLayout
    )

    private class MoveCardHolder(
        val root: LinearLayout,
        val typeBadgeContainer: LinearLayout,
        val nameView: TextView,
        val ppView: TextView,
        val bpAccView: TextView,
        val categoryView: TextView,
        val damageView: TextView,
        val effectivenessView: TextView,
        val actionPill: TextView
    )

    private class PartyMemberRowHolder(
        val root: LinearLayout,
        val nameView: TextView,
        val levelView: TextView,
        val hpBar: ProgressBar,
        val hpTextView: TextView,
        val statusBadge: TextView,
        val actionWidget: TextView,
        val divider: View
    )

    private class StatStageRowHolder(
        val statNameView: TextView,
        val playerValueView: TextView,
        val enemyValueView: TextView
    )

    private val modeSegmentedControl: DualDexSegmentedControl
    private var activeMode = BattleMode.BATTLE

    private val battleScrollView: ScrollView
    private val battleModeContainer: LinearLayout
    private val emptyStateContainer: LinearLayout
    private val liveBattleContainer: LinearLayout

    private val typeMatchupsView: TypeChartScreenView
    private val detailsScrollView: ScrollView
    private val detailsModeContainer: LinearLayout

    private val emptyStateTitleView: TextView
    private val emptyStateDetailView: TextView

    // Live Battle elements
    private lateinit var playerCombatantHolder: CombatantHolder
    private lateinit var enemyCombatantHolder: CombatantHolder
    private lateinit var speedBannerView: LinearLayout
    private lateinit var speedBannerTitle: TextView
    private lateinit var speedBannerDetail: TextView
    private val readOnlyNoticeView: LinearLayout
    private val readOnlyNoticeText: TextView
    private val actionFeedbackText: TextView
    private val moveHolders = ArrayList<MoveCardHolder>(4)
    private val partyRowHolders = ArrayList<PartyMemberRowHolder>(6)
    private val partyRowsContainer: LinearLayout

    // Details elements
    private val statStageRows = ArrayList<StatStageRowHolder>(7)
    private lateinit var fieldStatusContainer: LinearLayout
    private lateinit var moveDetailsContainer: LinearLayout

    private var actionFeedbackMessage: String? = null
    private var actionFeedbackResetJob: Job? = null

    private var viewScope: CoroutineScope? = null
    private var calculationJob: Job? = null
    private val damageCalculator: BattleDamageCalculator = CachingBattleDamageCalculator()
    private var lastMovePresentationKey: BattleMovePresentationCacheKey? = null
    private var latestMovePresentationKey: BattleMovePresentationCacheKey? = null
    private var lastCachedMoves: List<MovePresentation> = emptyList()

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)

        // Fixed Top Chrome: Screen Title + Calculator Action + Mode Switch
        val topChrome = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.tight)
            )
        }

        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(DualDexComponents.screenTitle(context, "Battle"), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(DualDexComponents.ghostControl(context, "Calculator") {
            onOpenCalculatorRequested?.invoke()
        })
        topChrome.addView(titleRow)

        modeSegmentedControl = DualDexComponents.segmentedControl(
            context = context,
            items = listOf("Battle", "Type Matchups", "Details"),
            initialIndex = 0
        ) { index ->
            val newMode = BattleMode.values()[index]
            if (activeMode != newMode) {
                selectMode(newMode)
            }
        }.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(DualDexTheme.Spacing.compact)
            }
        }
        topChrome.addView(modeSegmentedControl)
        addView(topChrome)

        // Main content switcher area
        val contentArea = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // 1. Mode: BATTLE
        battleScrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        battleModeContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.major)
            )
        }
        battleScrollView.addView(battleModeContainer)

        // Empty state inside battleModeContainer
        val emptyStateView = DualDexComponents.emptyState(
            context,
            "No battle in progress",
            "Matchup, moves, and damage predictions will appear when a battle starts."
        )
        emptyStateTitleView = emptyStateView.getChildAt(0) as TextView
        emptyStateDetailView = emptyStateView.getChildAt(1) as TextView
        emptyStateContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(emptyStateView)
            addView(DualDexComponents.secondaryButton(context, "Open Calculator") {
                onOpenCalculatorRequested?.invoke()
            }, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
        }
        battleModeContainer.addView(emptyStateContainer)

        // Live battle hierarchy
        liveBattleContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
        }
        battleModeContainer.addView(liveBattleContainer)

        // Build Matchup Card
        buildMatchupSection(liveBattleContainer)

        // Read-only notice
        readOnlyNoticeView = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = DualDexComponents.roundedDrawable(
                context,
                DualDexTheme.Color.surfaceDisabled,
                DualDexTheme.Radius.control,
                DualDexTheme.Color.border
            )
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact)
            )
            visibility = View.GONE
        }
        readOnlyNoticeText = TextView(context).apply {
            setTextColor(DualDexTheme.Color.warning)
            textSize = DualDexTheme.Type.meta
            typeface = Typeface.DEFAULT_BOLD
        }
        readOnlyNoticeView.addView(readOnlyNoticeText)
        liveBattleContainer.addView(readOnlyNoticeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.standard)
        })

        // Action feedback pill
        actionFeedbackText = TextView(context).apply {
            setTextColor(DualDexTheme.Color.accent)
            textSize = DualDexTheme.Type.meta
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
        }
        liveBattleContainer.addView(actionFeedbackText)

        // Moves Section
        liveBattleContainer.addView(DualDexComponents.sectionTitle(context, "Moves"), LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })

        for (i in 0 until 4) {
            val holder = createMoveCardHolder()
            moveHolders += holder
            liveBattleContainer.addView(holder.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(DualDexTheme.Spacing.compact)
            })
        }

        // Party Switching Section
        liveBattleContainer.addView(DualDexComponents.sectionTitle(context, "Party"), LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = context.dp(DualDexTheme.Spacing.standard)
            bottomMargin = context.dp(DualDexTheme.Spacing.compact)
        })

        partyRowsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context)
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.tight),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.tight)
            )
        }
        liveBattleContainer.addView(partyRowsContainer)

        contentArea.addView(battleScrollView)

        // 2. Mode: TYPE_MATCHUPS
        typeMatchupsView = TypeChartScreenView(context, viewModel).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        contentArea.addView(typeMatchupsView)

        // 3. Mode: DETAILS
        detailsScrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        detailsModeContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.major)
            )
        }
        detailsScrollView.addView(detailsModeContainer)

        buildDetailsSection(detailsModeContainer)
        contentArea.addView(detailsScrollView)

        addView(contentArea)
        refreshUI()
    }

    fun selectMode(mode: BattleMode) {
        activeMode = mode
        modeSegmentedControl.setSelectedIndex(mode.ordinal)
        battleScrollView.visibility = if (mode == BattleMode.BATTLE) View.VISIBLE else View.GONE
        typeMatchupsView.visibility = if (mode == BattleMode.TYPE_MATCHUPS) View.VISIBLE else View.GONE
        detailsScrollView.visibility = if (mode == BattleMode.DETAILS) View.VISIBLE else View.GONE

        if (mode == BattleMode.TYPE_MATCHUPS) {
            val defender = viewModel.enemyParty.value.getOrNull(viewModel.activeEnemyMemberIndex.value)
            val profile = viewModel.activeProfile.value
            if (defender != null && !defender.isEmpty && defender.isValid) {
                val (t1, t2) = MoveEffectiveness.defenderTypesOf(defender, profile)
                if (t1 != null) {
                    typeMatchupsView.setTypes(t1, t2)
                }
            }
            typeMatchupsView.updateMatchupDisplay()
        } else if (mode == BattleMode.DETAILS) {
            updateDetailsMode()
        }
    }

    private fun buildMatchupSection(container: LinearLayout) {
        val matchupSurface = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context, elevated = true)
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard)
            )
        }

        val combatantsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.TOP
        }

        playerCombatantHolder = createCombatantHolder("Player")
        enemyCombatantHolder = createCombatantHolder("Opponent")

        combatantsRow.addView(playerCombatantHolder.root, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        // Center VS Divider
        val vsCol = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(context.dp(DualDexTheme.Spacing.tight), context.dp(DualDexTheme.Spacing.section), context.dp(DualDexTheme.Spacing.tight), 0)
        }
        vsCol.addView(TextView(context).apply {
            text = "vs"
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
        })
        combatantsRow.addView(vsCol)

        combatantsRow.addView(enemyCombatantHolder.root, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        matchupSurface.addView(combatantsRow)

        // Divider
        matchupSurface.addView(DualDexComponents.divider(context), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1)).apply {
            topMargin = context.dp(DualDexTheme.Spacing.standard)
            bottomMargin = context.dp(DualDexTheme.Spacing.compact)
        })

        // Speed comparison row
        speedBannerView = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        speedBannerTitle = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.meta
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        speedBannerDetail = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            gravity = Gravity.END
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        speedBannerView.addView(speedBannerTitle, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        speedBannerView.addView(speedBannerDetail)
        matchupSurface.addView(speedBannerView)

        container.addView(matchupSurface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })
    }

    private fun createCombatantHolder(role: String): CombatantHolder {
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        val roleLabel = TextView(context).apply {
            text = role
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(roleLabel)

        val nameRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(2), 0, context.dp(2))
        }
        val nameView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val levelView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.accent)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            setPadding(context.dp(DualDexTheme.Spacing.tight), 0, 0, 0)
        }
        nameRow.addView(nameView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        nameRow.addView(levelView)
        root.addView(nameRow)

        val typesRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.tight))
        }
        root.addView(typesRow)

        val hpBar = DualDexComponents.createHpBar(context)
        root.addView(hpBar, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(6)))

        val hpRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(2), 0, 0)
        }
        val hpTextView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
        }
        val statusBadge = TextView(context).apply {
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(context.dp(4), context.dp(1), context.dp(4), context.dp(1))
            visibility = View.GONE
        }
        hpRow.addView(hpTextView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        hpRow.addView(statusBadge)
        root.addView(hpRow)

        val statChipsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
        }
        root.addView(statChipsRow)

        return CombatantHolder(root, roleLabel, nameView, levelView, typesRow, hpBar, hpTextView, statusBadge, statChipsRow)
    }

    private fun createMoveCardHolder(): MoveCardHolder {
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context)
            minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact)
            )
            isClickable = false
            isFocusable = false
        }

        // Header Row: Type Badge | Name | PP | BP/Acc | USE pill
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val typeBadgeContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        header.addView(typeBadgeContainer)

        val nameView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.compact), 0)
        }
        header.addView(nameView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val bpAccView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            setPadding(0, 0, context.dp(DualDexTheme.Spacing.compact), 0)
        }
        header.addView(bpAccView)

        val ppView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            setPadding(0, 0, context.dp(DualDexTheme.Spacing.compact), 0)
        }
        header.addView(ppView)

        val actionPill = TextView(context).apply {
            text = "USE"
            setTextColor(DualDexTheme.Color.onAccent)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(context.dp(DualDexTheme.Spacing.compact), context.dp(2), context.dp(DualDexTheme.Spacing.compact), context.dp(2))
            background = DualDexComponents.roundedDrawable(context, DualDexTheme.Color.accent, DualDexTheme.Radius.pill)
            visibility = View.GONE
        }
        header.addView(actionPill)
        root.addView(header)

        // Sub Row: Category | Damage Range (%) | Effectiveness
        val subRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
        }

        val categoryView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            setPadding(0, 0, context.dp(DualDexTheme.Spacing.compact), 0)
        }
        subRow.addView(categoryView)

        val damageView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = false
            ellipsize = null
        }
        subRow.addView(damageView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val effectivenessView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
        }
        subRow.addView(effectivenessView)
        root.addView(subRow)

        return MoveCardHolder(root, typeBadgeContainer, nameView, ppView, bpAccView, categoryView, damageView, effectivenessView, actionPill)
    }

    private fun createPartyRowHolder(): PartyMemberRowHolder {
        val root = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.tight))
        }

        val infoCol = LinearLayout(context).apply {
            orientation = VERTICAL
        }

        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val levelView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            setPadding(context.dp(DualDexTheme.Spacing.compact), 0, 0, 0)
        }
        val statusBadge = TextView(context).apply {
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(context.dp(4), context.dp(1), context.dp(4), context.dp(1))
            visibility = View.GONE
        }
        titleRow.addView(nameView)
        titleRow.addView(levelView)
        titleRow.addView(statusBadge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(DualDexTheme.Spacing.compact)
        })
        infoCol.addView(titleRow)

        val hpRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(2), 0, 0)
        }
        val hpBar = DualDexComponents.createHpBar(context)
        hpRow.addView(hpBar, LayoutParams(context.dp(80), context.dp(4)))

        val hpTextView = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            setPadding(context.dp(DualDexTheme.Spacing.compact), 0, 0, 0)
        }
        hpRow.addView(hpTextView)
        infoCol.addView(hpRow)

        root.addView(infoCol, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val actionWidget = TextView(context).apply {
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = context.dp(72)
            minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
            setPadding(context.dp(DualDexTheme.Spacing.compact), context.dp(4), context.dp(DualDexTheme.Spacing.compact), context.dp(4))
            background = DualDexComponents.controlBackground(context, DualDexButtonStyle.SECONDARY, selected = false)
        }
        root.addView(actionWidget)

        val divider = DualDexComponents.divider(context)

        return PartyMemberRowHolder(root, nameView, levelView, hpBar, hpTextView, statusBadge, actionWidget, divider)
    }

    private fun buildDetailsSection(container: LinearLayout) {
        // Stat Stages Table
        container.addView(DualDexComponents.sectionTitle(context, "Stat Stages (-6 .. +6)"), LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })

        val statTable = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context)
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact)
            )
        }

        val tableHeader = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
        }
        tableHeader.addView(createTableCell("Stat", isHeader = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        tableHeader.addView(createTableCell("Player", isHeader = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.2f))
        tableHeader.addView(createTableCell("Opponent", isHeader = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.2f))
        statTable.addView(tableHeader)

        val statNames = listOf("Attack", "Defense", "Speed", "Sp. Atk", "Sp. Def", "Accuracy", "Evasion")
        statStageRows.clear()
        statNames.forEach { name ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, context.dp(2), 0, context.dp(2))
            }
            val nameCell = createTableCell(name)
            val playerCell = createTableCell("0 (1.00×)")
            val enemyCell = createTableCell("0 (1.00×)")
            row.addView(nameCell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(playerCell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.2f))
            row.addView(enemyCell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.2f))
            statTable.addView(row)
            statStageRows += StatStageRowHolder(nameCell, playerCell, enemyCell)
        }
        container.addView(statTable, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // Field Conditions
        container.addView(DualDexComponents.sectionTitle(context, "Field & Diagnostics"), LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })

        fieldStatusContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context)
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard)
            )
        }
        container.addView(fieldStatusContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // Detailed Move Calculation Formulas
        container.addView(DualDexComponents.sectionTitle(context, "Move Calculations"), LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })

        moveDetailsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        container.addView(moveDetailsContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        // Manual Calculator route button
        container.addView(DualDexComponents.secondaryButton(context, "Open Manual Calculator") {
            onOpenCalculatorRequested?.invoke()
        }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(DualDexTheme.Spacing.touchTarget)))
    }

    private fun createTableCell(text: String, isHeader: Boolean = false): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(if (isHeader) DualDexTheme.Color.accent else DualDexTheme.Color.textPrimary)
            textSize = if (isHeader) DualDexTheme.Type.compact else DualDexTheme.Type.meta
            if (isHeader) typeface = Typeface.DEFAULT_BOLD
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope

        scope.launch { viewModel.playerParty.collectLatest { refreshUI() } }
        scope.launch { viewModel.enemyParty.collectLatest { refreshUI() } }
        scope.launch { viewModel.isInBattle.collectLatest { refreshUI() } }
        scope.launch { viewModel.activePlayerBattlerIndex.collectLatest { refreshUI() } }
        scope.launch { viewModel.activeEnemyMemberIndex.collectLatest { refreshUI() } }
        scope.launch { viewModel.activeProfile.collectLatest { refreshUI() } }
        scope.launch { viewModel.runtimeRomTrust.collectLatest { refreshUI() } }
        scope.launch { viewModel.playerStatStages.collectLatest { refreshUI() } }
        scope.launch { viewModel.enemyStatStages.collectLatest { refreshUI() } }
        scope.launch { viewModel.challengeSettings.collectLatest { refreshUI() } }
        scope.launch { viewModel.playerBattlerState.collectLatest { refreshUI() } }
        scope.launch { viewModel.enemyBattlerState.collectLatest { refreshUI() } }
        scope.launch { viewModel.battleUiSnapshot.collectLatest { refreshUI() } }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        calculationJob?.cancel()
        calculationJob = null
        actionFeedbackResetJob?.cancel()
        viewScope?.cancel()
        viewScope = null
    }

    private fun showFeedback(msg: String, durationMs: Long = 2500L) {
        actionFeedbackMessage = msg
        actionFeedbackText.text = msg
        actionFeedbackText.visibility = View.VISIBLE

        actionFeedbackResetJob?.cancel()
        actionFeedbackResetJob = (viewScope ?: CoroutineScope(Dispatchers.Main)).launch {
            delay(durationMs)
            actionFeedbackMessage = null
            actionFeedbackText.visibility = View.GONE
        }
    }

    fun refreshUI() {
        val party = viewModel.playerParty.value
        val enemies = viewModel.enemyParty.value
        val inBattle = viewModel.isInBattle.value
        val activePlayerIdx = viewModel.activePlayerBattlerIndex.value
        val activeEnemyIdx = viewModel.activeEnemyMemberIndex.value
        val enemyResolution = viewModel.activeEnemyResolution.value
        val profile = viewModel.activeProfile.value
        val runtimeTrust = viewModel.runtimeRomTrust.value
        val playerStages = viewModel.playerStatStages.value
        val enemyStages = viewModel.enemyStatStages.value
        val challengeSettings = viewModel.challengeSettings.value
        val playerBattlerState = viewModel.playerBattlerState.value
        val enemyBattlerState = viewModel.enemyBattlerState.value
        val uiSnap = viewModel.battleUiSnapshot.value

        val attacker: ParsedPokemon? = if (inBattle) {
            party.getOrNull(activePlayerIdx)?.takeIf { !it.isEmpty && it.isValid }
        } else {
            null
        }
        val defender: ParsedPokemon? = if (inBattle &&
            enemyResolution.hasResolvedSlot &&
            activeEnemyIdx in enemies.indices
        ) {
            enemies[activeEnemyIdx].takeIf { !it.isEmpty && it.isValid }
        } else {
            null
        }

        val hnsCalculationContext = BattleHnsCalculationContext(
            playerParty = party.toList(),
            activePlayerSlot = activePlayerIdx,
            activeEnemySlot = activeEnemyIdx.takeIf { enemyResolution.hasResolvedSlot },
            challengeSettings = challengeSettings,
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            activeBattle = inBattle
        )

        // Cache-aware move presentation calculation
        ensureMovePresentations(attacker, defender, profile, runtimeTrust, playerStages, enemyStages, hnsCalculationContext)

        if (!inBattle || attacker == null) {
            val (title, detail) = if (runtimeTrust.hasActiveRom && !runtimeTrust.mayReadLiveMemory) {
                RomCompatibilityMessages.badge(runtimeTrust.status) to RomCompatibilityMessages.detail(runtimeTrust.status)
            } else {
                "No battle in progress" to "Matchup, moves, and damage predictions will appear when a battle starts."
            }
            emptyStateTitleView.text = title
            emptyStateDetailView.text = detail
            emptyStateContainer.visibility = View.VISIBLE
            liveBattleContainer.visibility = View.GONE
        } else {
            emptyStateContainer.visibility = View.GONE
            liveBattleContainer.visibility = View.VISIBLE

            // Update Matchup
            val attackerSummary = ParticipantSummaryBuilder.build(attacker, activePlayerIdx, profile, playerStages, runtimeTrust)
            bindCombatant(playerCombatantHolder, attackerSummary, attacker)

            if (defender != null) {
                val opponentSummary = ParticipantSummaryBuilder.build(defender, activeEnemyIdx, profile, enemyStages, runtimeTrust)
                bindCombatant(enemyCombatantHolder, opponentSummary, defender)
            } else {
                val emptyOpponent = BattleParticipantSummary.UNVERIFIED
                bindCombatant(enemyCombatantHolder, emptyOpponent, null)
            }

            // Speed comparison
            if (defender != null) {
                val speedComp = SpeedComparison.calculate(
                    playerBaseSpeed = attacker.speed,
                    playerStages = playerStages,
                    playerParalyzed = (attacker.statusCondition and (1L shl 6)) != 0L,
                    enemyBaseSpeed = defender.speed,
                    enemyStages = enemyStages,
                    enemyParalyzed = (defender.statusCondition and (1L shl 6)) != 0L
                )
                speedBannerTitle.text = speedComp.orderLabel
                speedBannerTitle.setTextColor(when {
                    speedComp.playerMovesFirst == true -> DualDexTheme.Color.success
                    speedComp.playerMovesFirst == false -> DualDexTheme.Color.warning
                    else -> DualDexTheme.Color.accent
                })
                speedBannerDetail.text = "Eff Spe: ${speedComp.playerEffectiveSpeed} vs ${speedComp.enemyEffectiveSpeed}"
                speedBannerView.visibility = View.VISIBLE
            } else {
                speedBannerView.visibility = View.GONE
            }

            // Read-only warning, plus the honest degradation notice when no single opponent can
            // be named. The opponent slot is only honoured when the native resolution says SLOT,
            // so a doubles battle or an unresolved transition can never show the wrong enemy.
            val opponentNotice = when {
                enemyResolution.state == com.dualdex.battle.ActiveEnemyState.AMBIGUOUS ->
                    "${enemyResolution.state.displayName}: ${enemyResolution.state.detail}"
                defender == null -> "Opponent not identified: ${enemyResolution.state.detail}"
                else -> null
            }
            when {
                !uiSnap.inputSafe -> {
                    readOnlyNoticeText.text = uiSnap.readOnlyReason
                        ?: "Read-only: interactive battle controls are unavailable."
                    readOnlyNoticeView.visibility = View.VISIBLE
                }
                opponentNotice != null -> {
                    readOnlyNoticeText.text = opponentNotice
                    readOnlyNoticeView.visibility = View.VISIBLE
                }
                else -> readOnlyNoticeView.visibility = View.GONE
            }

            // Update Move Cards
            bindMoves(defender, uiSnap)

            // Update Party Rows
            bindParty(party, activePlayerIdx, inBattle, uiSnap)
        }

        if (activeMode == BattleMode.DETAILS) {
            updateDetailsMode()
        }
    }

    private fun bindCombatant(holder: CombatantHolder, summary: BattleParticipantSummary, mon: ParsedPokemon?) {
        holder.nameView.text = summary.displayName
        holder.levelView.text = if (summary.level > 0) "Lv. ${summary.level}" else ""

        // Types
        holder.typesRow.removeAllViews()
        summary.typeNames.forEach { typeName ->
            val type = PokemonType.fromString(typeName)
            if (type != null) {
                holder.typesRow.addView(DualDexComponents.typeBadge(context, type), LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.tight)
                })
            }
        }

        DualDexComponents.updateHpBar(holder.hpBar, summary.currentHp, summary.maxHp)
        holder.hpTextView.text = "${summary.currentHp}/${summary.maxHp}"

        // Status condition
        val statusCondition = StatusConditionDecoder.decode(mon?.statusCondition ?: 0L)
        if (statusCondition != StatusCondition.HEALTHY && statusCondition != StatusCondition.UNKNOWN) {
            holder.statusBadge.text = statusCondition.displayName
            val isSevere = statusCondition == StatusCondition.BAD_POISON || statusCondition == StatusCondition.FREEZE
            val color = if (isSevere) DualDexTheme.Color.danger else DualDexTheme.Color.warning
            holder.statusBadge.setTextColor(color)
            holder.statusBadge.background = DualDexComponents.roundedDrawable(context, DualDexTheme.Color.surfaceDisabled, DualDexTheme.Radius.pill, color)
            holder.statusBadge.visibility = View.VISIBLE
        } else {
            holder.statusBadge.visibility = View.GONE
        }

        // Active stat stages chips (e.g. +1 Atk, -1 Def)
        holder.statChipsRow.removeAllViews()
        val chips = summary.statStages.activeStageChips()
        chips.forEach { (stat, stage) ->
            val sign = if (stage > 0) "+$stage" else "$stage"
            val color = if (stage > 0) DualDexTheme.Color.success else DualDexTheme.Color.danger
            holder.statChipsRow.addView(TextView(context).apply {
                text = "$sign $stat"
                setTextColor(color)
                textSize = DualDexTheme.Type.compact
                typeface = Typeface.DEFAULT_BOLD
                setPadding(context.dp(4), context.dp(1), context.dp(4), context.dp(1))
                background = DualDexComponents.roundedDrawable(context, DualDexTheme.Color.surfaceDisabled, DualDexTheme.Radius.control, color)
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.tight)
            })
        }
    }

    private fun bindMoves(defender: ParsedPokemon?, uiSnap: BattleUiSnapshot) {
        val canSelectMoves = uiSnap.inputSafe && uiSnap.capabilities.selectMove

        for (i in 0 until 4) {
            val holder = moveHolders[i]
            val pres = lastCachedMoves.getOrNull(i)

            if (pres == null || pres.moveId <= 0) {
                holder.root.visibility = View.GONE
                continue
            }

            holder.root.visibility = View.VISIBLE
            holder.nameView.text = pres.name

            holder.typeBadgeContainer.removeAllViews()
            val type = PokemonType.fromString(pres.typeName)
            if (type != null) {
                holder.typeBadgeContainer.addView(DualDexComponents.typeBadge(context, type))
            }

            holder.bpAccView.text = "BP ${pres.powerDisplay} · Acc ${pres.accuracyDisplay}"
            holder.ppView.text = "PP ${pres.ppDisplay}"
            holder.categoryView.text = pres.categoryDisplay

            // Damage range & percentage
            val estimateSuffix = if (pres.calculatorSupport == CalcSupport.ESTIMATED) " · Estimate" else ""
            val damageText = when {
                pres.isStatMove -> "Status move"
                pres.maxDamage > 0 && defender != null && defender.maxHp > 0 -> {
                    val minPct = (pres.minDamage * 100) / defender.maxHp
                    val maxPct = (pres.maxDamage * 100) / defender.maxHp
                    "${pres.minDamage}–${pres.maxDamage} HP · $minPct–$maxPct%" +
                            (if (pres.koChanceText.isNotBlank()) " · ${pres.koChanceText}" else "") + estimateSuffix
                }
                pres.maxDamage > 0 -> {
                    "${pres.minDamage}–${pres.maxDamage} HP" +
                            (if (pres.koChanceText.isNotBlank()) " · ${pres.koChanceText}" else "") + estimateSuffix
                }
                pres.damageConfidence == DamageConfidence.UNAVAILABLE ->
                    if (pres.damageAbilityBlockers.size > 1) {
                        val details = pres.damageAbilityBlockers.joinToString("\n") { blocker ->
                            val owner = if (blocker.side == com.dualdex.calculator.HnsAbilitySide.ATTACKER) {
                                "You"
                            } else {
                                "Foe"
                            }
                            "$owner: ${blocker.abilityName}"
                        }
                        "Damage unavailable · ${pres.damageAbilityBlockers.size} ability blockers\n$details"
                    } else {
                        "Damage unavailable" + pres.damageUnavailableReason?.let { " · $it" }.orEmpty()
                    }
                else -> pres.damageDisplayText
            }
            holder.damageView.text = damageText

            val effText = if (pres.effectiveness != MoveEffectiveness.UNAVAILABLE) pres.effectiveness else ""
            holder.effectivenessView.text = effText

            if (canSelectMoves) {
                holder.root.isClickable = true
                holder.root.isFocusable = true
                holder.actionPill.visibility = View.VISIBLE
                holder.root.background = DualDexComponents.controlBackground(context, DualDexButtonStyle.SECONDARY, selected = false)
                holder.root.setOnClickListener {
                    val currentUi = viewModel.battleUiSnapshot.value
                    if (!currentUi.inputSafe) {
                        showFeedback("Selection unavailable: ${currentUi.readOnlyReason ?: "Read-only"}")
                        return@setOnClickListener
                    }
                    showFeedback("Selecting ${pres.name}...")
                    val scope = viewScope ?: CoroutineScope(Dispatchers.Main)
                    scope.launch {
                        val ok = viewModel.battleInputAdapter.selectMove(i, currentUi)
                        if (!ok) {
                            showFeedback("Move selection aborted")
                        }
                    }
                }
            } else {
                holder.root.isClickable = false
                holder.root.isFocusable = false
                holder.actionPill.visibility = View.GONE
                holder.root.background = DualDexComponents.surface(context)
                holder.root.setOnClickListener(null)
            }
        }
    }

    private fun bindParty(party: List<ParsedPokemon>, activeSlot: Int, inBattle: Boolean, uiSnap: BattleUiSnapshot) {
        val validMembers = party.filter { !it.isEmpty && it.isValid }

        // Ensure we have enough row holders
        while (partyRowHolders.size < validMembers.size) {
            val newHolder = createPartyRowHolder()
            partyRowHolders += newHolder
            partyRowsContainer.addView(newHolder.root)
            partyRowsContainer.addView(newHolder.divider)
        }

        // Hide extra row holders
        for (i in validMembers.size until partyRowHolders.size) {
            partyRowHolders[i].root.visibility = View.GONE
            partyRowHolders[i].divider.visibility = View.GONE
        }

        validMembers.forEachIndexed { slot, mon ->
            val holder = partyRowHolders[slot]
            holder.root.visibility = View.VISIBLE
            holder.divider.visibility = if (slot < validMembers.lastIndex) View.VISIBLE else View.GONE

            holder.nameView.text = mon.nickname.trim().ifEmpty { "Mon #${slot + 1}" }
            holder.levelView.text = "Lv. ${mon.level}"
            DualDexComponents.updateHpBar(holder.hpBar, mon.currentHp, mon.maxHp)
            holder.hpTextView.text = "${mon.currentHp}/${mon.maxHp}"

            val statusCondition = StatusConditionDecoder.decode(mon.statusCondition)
            if (statusCondition != StatusCondition.HEALTHY && statusCondition != StatusCondition.UNKNOWN) {
                holder.statusBadge.text = statusCondition.displayName
                holder.statusBadge.setTextColor(DualDexTheme.Color.warning)
                holder.statusBadge.visibility = View.VISIBLE
            } else {
                holder.statusBadge.visibility = View.GONE
            }

            val isActive = slot == activeSlot && inBattle
            val isFainted = mon.currentHp <= 0

            when {
                isActive -> {
                    holder.actionWidget.text = "Active"
                    holder.actionWidget.setTextColor(DualDexTheme.Color.accent)
                    holder.actionWidget.background = DualDexComponents.roundedDrawable(context, DualDexTheme.Color.surfaceSelected, DualDexTheme.Radius.control)
                    holder.actionWidget.isClickable = false
                    holder.actionWidget.isFocusable = false
                }
                isFainted -> {
                    holder.actionWidget.text = "Fainted"
                    holder.actionWidget.setTextColor(DualDexTheme.Color.danger)
                    holder.actionWidget.background = DualDexComponents.roundedDrawable(context, DualDexTheme.Color.surfaceDisabled, DualDexTheme.Radius.control)
                    holder.actionWidget.isClickable = false
                    holder.actionWidget.isFocusable = false
                }
                inBattle && uiSnap.inputSafe && uiSnap.capabilities.switchPokemon -> {
                    holder.actionWidget.text = "Switch In"
                    holder.actionWidget.setTextColor(DualDexTheme.Color.textPrimary)
                    holder.actionWidget.background = DualDexComponents.controlBackground(context, DualDexButtonStyle.SECONDARY, selected = false)
                    holder.actionWidget.isClickable = true
                    holder.actionWidget.isFocusable = true
                    holder.actionWidget.setOnClickListener {
                        val currentSnap = viewModel.battleUiSnapshot.value
                        if (!currentSnap.inputSafe) {
                            showFeedback("Switch unavailable: ${currentSnap.readOnlyReason ?: "Read-only"}")
                            return@setOnClickListener
                        }
                        showFeedback("Switching to ${mon.nickname}...")
                        val scope = viewScope ?: CoroutineScope(Dispatchers.Main)
                        scope.launch {
                            val ok = viewModel.battleInputAdapter.switchPokemon(
                                targetSlot = slot,
                                currentUi = currentSnap,
                                party = viewModel.playerParty.value,
                                activeSlot = activeSlot
                            )
                            if (!ok) {
                                showFeedback("Switch aborted (illegal or blocked)")
                            }
                        }
                    }
                }
                else -> {
                    holder.actionWidget.text = "In Party"
                    holder.actionWidget.setTextColor(DualDexTheme.Color.textSecondary)
                    holder.actionWidget.background = DualDexComponents.roundedDrawable(context, DualDexTheme.Color.surfaceDisabled, DualDexTheme.Radius.control)
                    holder.actionWidget.isClickable = false
                    holder.actionWidget.isFocusable = false
                }
            }
        }
    }

    private fun updateDetailsMode() {
        val playerStages = viewModel.playerStatStages.value
        val enemyStages = viewModel.enemyStatStages.value
        val inBattle = viewModel.isInBattle.value
        val attacker = viewModel.playerParty.value.getOrNull(viewModel.activePlayerBattlerIndex.value)
        val defender = viewModel.enemyParty.value.getOrNull(viewModel.activeEnemyMemberIndex.value)
        val profile = viewModel.activeProfile.value
        val runtimeTrust = viewModel.runtimeRomTrust.value

        // Update Stat Stages Table
        val stagesList = listOf(
            playerStages.atk to enemyStages.atk,
            playerStages.def to enemyStages.def,
            playerStages.spe to enemyStages.spe,
            playerStages.spa to enemyStages.spa,
            playerStages.spd to enemyStages.spd,
            playerStages.acc to enemyStages.acc,
            playerStages.eva to enemyStages.eva
        )

        stagesList.forEachIndexed { i, (pStage, eStage) ->
            val holder = statStageRows[i]
            val isAccEva = i >= 5
            val pMult = if (isAccEva) StatStages.accuracyMultiplier(pStage) else StatStages.statMultiplier(pStage)
            val eMult = if (isAccEva) StatStages.accuracyMultiplier(eStage) else StatStages.statMultiplier(eStage)

            holder.playerValueView.text = "${StatStages.formatStage(pStage)} (${String.format("%.2f", pMult)}×)"
            holder.playerValueView.setTextColor(when {
                pStage > 0 -> DualDexTheme.Color.success
                pStage < 0 -> DualDexTheme.Color.danger
                else -> DualDexTheme.Color.textPrimary
            })

            holder.enemyValueView.text = "${StatStages.formatStage(eStage)} (${String.format("%.2f", eMult)}×)"
            holder.enemyValueView.setTextColor(when {
                eStage > 0 -> DualDexTheme.Color.success
                eStage < 0 -> DualDexTheme.Color.danger
                else -> DualDexTheme.Color.textPrimary
            })
        }

        // Update Field Status
        fieldStatusContainer.removeAllViews()
        val fieldData = FieldStatusBuilder.build(
            inBattle = inBattle,
            attacker = attacker,
            defender = defender,
            attackerSlot = viewModel.activePlayerBattlerIndex.value,
            profile = profile,
            runtimeTrust = runtimeTrust,
            playerStages = playerStages,
            enemyStages = enemyStages
        )

        addDetailRow(fieldStatusContainer, "Battle Status", if (inBattle) "Active Battle" else "Outside Battle")
        val weatherDisplay = if (fieldData.weather == WeatherType.UNKNOWN) "Clear / Normal" else fieldData.weather.displayName
        addDetailRow(fieldStatusContainer, "Weather", weatherDisplay)
        addDetailRow(fieldStatusContainer, "Condition", fieldData.condition.displayName)
        addDetailRow(fieldStatusContainer, "ROM Profile", "${profile.name} (${if (profile.isVerified) "Verified" else "Unverified"})")
        addDetailRow(fieldStatusContainer, "Engine", profile.engine)
        addDetailRow(fieldStatusContainer, "Phys/Spec Split", if (profile.hasPhysSpecSplit) "Enabled" else "Standard Gen 3")

        // Update Detailed Move Calculations
        moveDetailsContainer.removeAllViews()
        if (lastCachedMoves.isEmpty()) {
            moveDetailsContainer.addView(TextView(context).apply {
                text = "No move calculations available."
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
            })
        } else {
            lastCachedMoves.forEach { pres ->
                val card = LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = DualDexComponents.surface(context)
                    setPadding(
                        context.dp(DualDexTheme.Spacing.standard),
                        context.dp(DualDexTheme.Spacing.compact),
                        context.dp(DualDexTheme.Spacing.standard),
                        context.dp(DualDexTheme.Spacing.compact)
                    )
                }
                val header = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val type = PokemonType.fromString(pres.typeName)
                if (type != null) {
                    header.addView(DualDexComponents.typeBadge(context, type))
                }
                header.addView(TextView(context).apply {
                    text = "  ${pres.name}"
                    setTextColor(DualDexTheme.Color.textPrimary)
                    textSize = DualDexTheme.Type.body
                    typeface = Typeface.DEFAULT_BOLD
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                header.addView(TextView(context).apply {
                    text = pres.categoryDisplay
                    setTextColor(DualDexTheme.Color.textSecondary)
                    textSize = DualDexTheme.Type.compact
                })
                card.addView(header)

                addDetailRow(card, "Power", pres.powerDisplay)
                addDetailRow(card, "Accuracy", pres.accuracyDisplay)
                addDetailRow(card, "PP", pres.ppDisplay)
                addDetailRow(card, "Type Matchup", pres.effectiveness)
                addDetailRow(card, "Damage Formula", pres.damageDisplayText)

                moveDetailsContainer.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = context.dp(DualDexTheme.Spacing.compact)
                })
            }
        }
    }

    private fun addDetailRow(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(2), 0, context.dp(2))
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(context).apply {
            text = value
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.meta
            typeface = Typeface.DEFAULT_BOLD
        })
        container.addView(row)
    }

    private fun ensureMovePresentations(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        profile: RomHackProfile,
        runtimeTrust: com.dualdex.romhack.RuntimeRomTrust,
        playerStages: StatStages,
        enemyStages: StatStages,
        hnsCalculationContext: BattleHnsCalculationContext
    ) {
        if (attacker == null || attacker.isEmpty) {
            calculationJob?.cancel()
            lastCachedMoves = emptyList()
            lastMovePresentationKey = null
            latestMovePresentationKey = null
            return
        }

        val key = BattleMovePresentationCacheKey.from(
            attacker = attacker,
            defender = defender,
            profile = profile,
            runtimeTrust = runtimeTrust,
            playerStages = playerStages,
            enemyStages = enemyStages,
            context = hnsCalculationContext
        )
        if (key == lastMovePresentationKey) {
            return
        }
        if (key == latestMovePresentationKey && calculationJob?.isActive == true) {
            return
        }

        latestMovePresentationKey = key
        // Do not leave a previous frame's range or category visible while the new live state is
        // being prepared. refreshUI binds this empty list before the background job completes.
        lastCachedMoves = emptyList()
        calculationJob?.cancel()
        val scope = viewScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob()).also { viewScope = it }
        val dataPack = viewModel.activeGameDataPack
        calculationJob = scope.launch {
            val presentations = withContext(Dispatchers.Default) {
                val list = mutableListOf<MovePresentation>()
                for (i in attacker.moves.indices) {
                    val moveId = attacker.moves[i]
                    if (moveId <= 0) continue
                    val info = MoveDatabase.get(moveId, dataPack)
                    val pres = BattlePresentationBuilder.build(
                        moveInfo = info,
                        currentPp = attacker.pp.getOrNull(i)?.takeIf { it >= 0 },
                        attacker = attacker,
                        defender = defender,
                        profile = profile,
                        runtimeTrust = runtimeTrust,
                        calculator = damageCalculator,
                        attackerStages = playerStages,
                        defenderStages = enemyStages,
                        hnsCalculationContext = hnsCalculationContext
                    )
                    list += pres
                }
                list
            }
            if (latestMovePresentationKey != key) return@launch
            lastMovePresentationKey = key
            lastCachedMoves = presentations

            if (activeMode == BattleMode.BATTLE) {
                bindMoves(defender, viewModel.battleUiSnapshot.value)
            } else if (activeMode == BattleMode.DETAILS) {
                updateDetailsMode()
            }
        }
    }
}
