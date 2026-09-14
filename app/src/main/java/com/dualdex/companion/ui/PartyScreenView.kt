package com.dualdex.companion.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.ItemDatabase
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.pokemon.TypeChart
import com.dualdex.pokemon.NatureTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Live party presentation. The selector and selected-Pokémon hierarchy are retained between
 * polling updates so a 10 Hz data stream does not interrupt touch, scroll, or controller focus.
 */
class PartyScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : LinearLayout(context) {

    private data class MemberHolder(
        val root: LinearLayout,
        val name: TextView,
        val level: TextView,
        val hpBar: ProgressBar,
        val hpText: TextView
    )

    private data class StatHolder(val value: TextView, val detail: TextView)

    private data class MoveHolder(
        val row: LinearLayout,
        val name: TextView,
        val typeContainer: LinearLayout,
        val meta: TextView
    )

    private data class DetailHolder(
        val pid: Long,
        var species: Int,
        val title: TextView,
        val level: TextView,
        val speciesLabel: TextView,
        val typeRow: LinearLayout,
        val hpLabel: TextView,
        val hpBar: ProgressBar,
        val nature: TextView,
        val heldItem: TextView,
        val stats: List<StatHolder>,
        val evTotal: TextView,
        val moves: List<MoveHolder>,
        val defenseContent: LinearLayout
    )

    private val memberSelectorLayout: LinearLayout
    private val detailContainer: LinearLayout
    private val chipHolders = ArrayList<MemberHolder>()
    private var detailHolder: DetailHolder? = null
    private var selectorShowsEmpty = false
    private var lastSelectedIdx = -1
    private var viewScope: CoroutineScope? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)

        addView(DualDexComponents.screenTitle(context, "Party"), LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = context.dp(DualDexTheme.Spacing.section)
            marginEnd = context.dp(DualDexTheme.Spacing.section)
            topMargin = context.dp(DualDexTheme.Spacing.section)
            bottomMargin = context.dp(DualDexTheme.Spacing.compact)
        })

        val horizontalScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.tight),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.standard)
            )
        }
        memberSelectorLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        horizontalScroll.addView(memberSelectorLayout)
        addView(horizontalScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val verticalScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        detailContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                0,
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.major)
            )
        }
        verticalScroll.addView(detailContainer)
        addView(verticalScroll)

        refreshUI()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { scope ->
            scope.launch { viewModel.playerParty.collectLatest { refreshUI() } }
            scope.launch { viewModel.selectedMemberIndex.collectLatest { refreshUI() } }
            scope.launch {
                viewModel.activePlayerBattlerIndex.collectLatest { activeIndex ->
                    if (viewModel.isInBattle.value && activeIndex in viewModel.playerParty.value.indices) {
                        viewModel.selectMember(activeIndex)
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    fun refreshUI() {
        val party = viewModel.playerParty.value
        val selected = viewModel.selectedMemberIndex.value.coerceIn(0, (party.size - 1).coerceAtLeast(0))
        updateSelector(party, selected)
        updateDetail(party, selected, viewModel.activeGameId.value)
    }

    private fun updateSelector(party: List<ParsedPokemon>, selectedIdx: Int) {
        if (party.isEmpty()) {
            if (!selectorShowsEmpty) {
                selectorShowsEmpty = true
                chipHolders.clear()
                memberSelectorLayout.removeAllViews()
                memberSelectorLayout.addView(TextView(context).apply {
                    text = "No game loaded"
                    setTextColor(DualDexTheme.Color.textSecondary)
                    textSize = DualDexTheme.Type.meta
                    setPadding(0, context.dp(DualDexTheme.Spacing.standard), 0, context.dp(DualDexTheme.Spacing.standard))
                })
            }
            return
        }

        if (selectorShowsEmpty || chipHolders.size != party.size) {
            selectorShowsEmpty = false
            chipHolders.clear()
            memberSelectorLayout.removeAllViews()
            party.forEachIndexed { index, mon ->
                val holder = createMemberSlot(index, index == selectedIdx)
                chipHolders += holder
                memberSelectorLayout.addView(holder.root, LayoutParams(
                    context.dp(88),
                    context.dp(72)
                ).apply {
                    marginEnd = if (index == party.lastIndex) 0 else context.dp(DualDexTheme.Spacing.compact)
                })
                bindMember(holder, mon, index == selectedIdx)
            }
            return
        }

        party.forEachIndexed { index, mon -> bindMember(chipHolders[index], mon, index == selectedIdx) }
    }

    private fun createMemberSlot(index: Int, selected: Boolean): MemberHolder {
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
            setPadding(
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.tight),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.tight)
            )
            background = DualDexComponents.controlBackground(context, DualDexButtonStyle.SECONDARY, selected = false)
            isSelected = selected
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = false
            contentDescription = "Party member ${index + 1}"
            installTapWithoutSwipeSelection(this)
            setOnClickListener { onMemberSelected(index) }
        }
        val name = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val level = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            gravity = Gravity.CENTER
        }
        val hpBar = createHpBar()
        val hpText = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            gravity = Gravity.CENTER
        }
        root.addView(name, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        root.addView(level, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        root.addView(hpBar, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(4)).apply {
            topMargin = context.dp(DualDexTheme.Spacing.tight)
        })
        root.addView(hpText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(2)
        })
        return MemberHolder(root, name, level, hpBar, hpText)
    }

    private fun installTapWithoutSwipeSelection(view: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var moved = false
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    touched.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (kotlin.math.abs(event.rawX - downX) > touchSlop || kotlin.math.abs(event.rawY - downY) > touchSlop)) {
                        moved = true
                        touched.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touched.parent?.requestDisallowInterceptTouchEvent(false)
                    if (moved) return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun bindMember(holder: MemberHolder, mon: ParsedPokemon, selected: Boolean) {
        val species = SpeciesDatabase.get(mon.species)
        holder.root.isSelected = selected
        holder.name.text = mon.nickname.ifBlank { species.name }
        holder.name.setTextColor(if (selected) DualDexTheme.Color.accent else DualDexTheme.Color.textPrimary)
        holder.level.text = "Lv. ${mon.level}"
        holder.hpText.text = "${mon.currentHp}/${mon.maxHp}"
        updateHpBar(holder.hpBar, mon.currentHp, mon.maxHp)
    }

    private fun onMemberSelected(index: Int) {
        if (index == lastSelectedIdx && detailHolder != null) return
        lastSelectedIdx = index
        viewModel.selectMember(index)
        refreshUI()
    }

    private fun updateDetail(party: List<ParsedPokemon>, selectedIdx: Int, gameId: Int) {
        if (party.isEmpty()) {
            if (detailHolder != null || detailContainer.childCount == 0) {
                detailHolder = null
                detailContainer.removeAllViews()
                detailContainer.addView(DualDexComponents.emptyState(
                    context,
                    "No game loaded",
                    "Party data will appear when a supported game is running."
                ))
            }
            return
        }

        val mon = party[selectedIdx]
        val holder = detailHolder
        if (holder == null || holder.pid != mon.pid) {
            detailHolder = createDetail(mon, gameId)
        } else {
            bindDetail(holder, mon, gameId)
        }
    }

    private fun createDetail(mon: ParsedPokemon, gameId: Int): DetailHolder {
        detailContainer.removeAllViews()

        val summary = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context, elevated = true)
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.section)
            )
        }
        val titleRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.screenTitle
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val level = TextView(context).apply {
            setTextColor(DualDexTheme.Color.accent)
            textSize = DualDexTheme.Type.sectionTitle
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }
        titleRow.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(level)
        summary.addView(titleRow)

        val speciesLabel = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
        }
        summary.addView(speciesLabel)

        val typeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, context.dp(DualDexTheme.Spacing.standard), 0, 0)
        }
        summary.addView(typeRow)

        val hpLabel = TextView(context).apply {
            textSize = DualDexTheme.Type.body
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, context.dp(DualDexTheme.Spacing.section), 0, context.dp(DualDexTheme.Spacing.tight))
        }
        summary.addView(hpLabel)
        val hpBar = createHpBar()
        summary.addView(hpBar, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(8)))

        val nature = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            setPadding(0, context.dp(DualDexTheme.Spacing.section), 0, context.dp(DualDexTheme.Spacing.tight))
        }
        val heldItem = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
        }
        summary.addView(nature)
        summary.addView(heldItem)
        detailContainer.addView(summary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        detailContainer.addView(DualDexComponents.sectionTitle(context, "Stats"), LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })
        val statsGrid = LinearLayout(context).apply { orientation = VERTICAL }
        val statHolders = ArrayList<StatHolder>(6)
        val statLabels = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")
        repeat(2) { rowIndex ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            repeat(3) { columnIndex ->
                val cell = LinearLayout(context).apply {
                    orientation = VERTICAL
                    setPadding(
                        context.dp(DualDexTheme.Spacing.compact),
                        context.dp(DualDexTheme.Spacing.compact),
                        context.dp(DualDexTheme.Spacing.compact),
                        context.dp(DualDexTheme.Spacing.compact)
                    )
                }
                val label = TextView(context).apply {
                    text = statLabels[rowIndex * 3 + columnIndex]
                    setTextColor(DualDexTheme.Color.textSecondary)
                    textSize = DualDexTheme.Type.compact
                    typeface = Typeface.DEFAULT_BOLD
                }
                val value = TextView(context).apply {
                    setTextColor(DualDexTheme.Color.textPrimary)
                    textSize = DualDexTheme.Type.sectionTitle
                    typeface = Typeface.DEFAULT_BOLD
                }
                val detail = TextView(context).apply {
                    setTextColor(DualDexTheme.Color.textSecondary)
                    textSize = DualDexTheme.Type.compact
                }
                cell.addView(label)
                cell.addView(value)
                cell.addView(detail)
                row.addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                statHolders += StatHolder(value, detail)
            }
            statsGrid.addView(row)
        }
        detailContainer.addView(statsGrid)
        val evTotal = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.section))
        }
        detailContainer.addView(evTotal)
        detailContainer.addView(DualDexComponents.divider(context), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1)).apply {
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        detailContainer.addView(DualDexComponents.sectionTitle(context, "Moves"), LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })
        val moveHolders = ArrayList<MoveHolder>(4)
        repeat(4) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
            }
            val name = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                typeface = Typeface.DEFAULT_BOLD
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            val typeContainer = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.compact), 0)
            }
            val meta = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.compact
                gravity = Gravity.END
                isSingleLine = true
            }
            row.addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(typeContainer)
            row.addView(meta)
            detailContainer.addView(row)
            moveHolders += MoveHolder(row, name, typeContainer, meta)
        }
        detailContainer.addView(DualDexComponents.divider(context), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1)).apply {
            topMargin = context.dp(DualDexTheme.Spacing.compact)
            bottomMargin = context.dp(DualDexTheme.Spacing.section)
        })

        detailContainer.addView(DualDexComponents.sectionTitle(context, "Type defenses"), LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })
        val defenseContent = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.section))
        }
        detailContainer.addView(defenseContent)

        return DetailHolder(
            mon.pid, mon.species, title, level, speciesLabel, typeRow, hpLabel, hpBar, nature, heldItem,
            statHolders, evTotal, moveHolders, defenseContent
        ).also { bindDetail(it, mon, gameId) }
    }

    private fun bindDetail(holder: DetailHolder, mon: ParsedPokemon, gameId: Int) {
        val species = SpeciesDatabase.get(mon.species)
        holder.title.text = buildString {
            append(mon.nickname.ifBlank { species.name })
            if (mon.isShiny) append(" ★")
        }
        holder.level.text = "Lv. ${mon.level}"
        holder.speciesLabel.text = species.name
        holder.hpLabel.text = "HP  ${mon.currentHp} / ${mon.maxHp}"
        holder.hpLabel.setTextColor(hpColor(mon.currentHp, mon.maxHp))
        updateHpBar(holder.hpBar, mon.currentHp, mon.maxHp)

        val nature = NatureTable.get(mon.nature)
        val isExpansion = gameId != 2 && gameId != 6
        val item = ItemDatabase.get(mon.heldItem, isExpansion = isExpansion)
        holder.nature.text = "Nature  ${nature.formattedDescription}"
        holder.heldItem.text = "Held item  ${if (mon.heldItem > 0) item.name else "None"}"

        val values = listOf(mon.maxHp, mon.attack, mon.defense, mon.spAttack, mon.spDefense, mon.speed)
        val ivs = listOf(mon.hpIv, mon.attackIv, mon.defenseIv, mon.spAttackIv, mon.spDefenseIv, mon.speedIv)
        val evs = listOf(mon.hpEv, mon.attackEv, mon.defenseEv, mon.spAttackEv, mon.spDefenseEv, mon.speedEv)
        holder.stats.forEachIndexed { index, stat ->
            stat.value.text = values[index].toString()
            stat.detail.text = "IV ${ivs[index]} · EV ${evs[index]}"
        }
        holder.evTotal.text = "EV total  ${mon.totalEvs} / 510"

        holder.moves.forEachIndexed { index, moveHolder ->
            val moveId = mon.moves.getOrNull(index) ?: 0
            if (moveId <= 0) {
                moveHolder.row.visibility = View.GONE
                return@forEachIndexed
            }
            val move = MoveDatabase.get(moveId)
            moveHolder.row.visibility = View.VISIBLE
            moveHolder.name.text = move.name
            moveHolder.meta.text = "PP ${mon.pp.getOrNull(index) ?: 0}/${move.pp} · Pwr ${move.power.takeIf { it > 0 } ?: "—"} · Acc ${move.accuracy}%"
            moveHolder.typeContainer.removeAllViews()
            moveHolder.typeContainer.addView(DualDexComponents.typeBadge(context, move.type))
        }

        if (holder.species != mon.species) {
            holder.species = mon.species
            renderTypeRow(holder.typeRow, species.type1, species.type2)
            renderDefenseSummary(holder.defenseContent, species.type1, species.type2, gameId)
        } else if (holder.typeRow.childCount == 0) {
            renderTypeRow(holder.typeRow, species.type1, species.type2)
            renderDefenseSummary(holder.defenseContent, species.type1, species.type2, gameId)
        }
    }

    private fun renderTypeRow(row: LinearLayout, type1: PokemonType, type2: PokemonType?) {
        row.removeAllViews()
        row.addView(DualDexComponents.typeBadge(context, type1))
        type2?.let {
            row.addView(DualDexComponents.typeBadge(context, it), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = context.dp(DualDexTheme.Spacing.tight) })
        }
    }

    private fun renderDefenseSummary(container: LinearLayout, type1: PokemonType, type2: PokemonType?, gameId: Int) {
        container.removeAllViews()
        val profile = TypeChart.getDefenseProfile(type1, type2, steelResistsGhostDark = gameId == 6)
        addDefenseRow(container, "Weak", profile.weaknesses4x + profile.weaknesses2x)
        addDefenseRow(container, "Resists", profile.resistancesHalf + profile.resistancesQuarter)
        addDefenseRow(container, "Immune", profile.immunities)
        if (container.childCount == 0) {
            container.addView(TextView(context).apply {
                text = "No special type modifiers"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.meta
            })
        }
    }

    private fun addDefenseRow(container: LinearLayout, label: String, types: List<PokemonType>) {
        if (types.isEmpty()) return
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
        }, LinearLayout.LayoutParams(context.dp(56), LinearLayout.LayoutParams.WRAP_CONTENT))
        types.forEach { type ->
            row.addView(DualDexComponents.typeBadge(context, type), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = context.dp(DualDexTheme.Spacing.tight) })
        }
        container.addView(row)
    }

    private fun createHpBar(): ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressDrawable = hpProgressDrawable(DualDexTheme.Color.success)
        tag = DualDexTheme.Color.success
    }

    private fun updateHpBar(bar: ProgressBar, current: Int, maximum: Int) {
        val ratio = if (maximum > 0) current.toFloat() / maximum else 0f
        val color = hpColor(current, maximum)
        bar.progress = (ratio.coerceIn(0f, 1f) * 1000).toInt()
        if (bar.tag != color) {
            bar.progressTintList = ColorStateList.valueOf(color)
            bar.tag = color
        }
    }

    private fun hpProgressDrawable(color: Int): LayerDrawable {
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

    private fun hpColor(current: Int, maximum: Int): Int {
        val ratio = if (maximum > 0) current.toFloat() / maximum else 0f
        return when {
            ratio > 0.5f -> DualDexTheme.Color.success
            ratio > 0.2f -> DualDexTheme.Color.warning
            else -> DualDexTheme.Color.danger
        }
    }
}
