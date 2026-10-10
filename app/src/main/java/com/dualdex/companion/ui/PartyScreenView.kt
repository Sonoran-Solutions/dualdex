package com.dualdex.companion.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.ItemDatabase
import com.dualdex.romhack.RomCompatibilityMessages
import com.dualdex.romhack.RuntimeRomTrust
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.TypeChart
import com.dualdex.pokemon.NatureTable
import com.dualdex.pokemon.resolveMove
import com.dualdex.pokemon.resolveSpecies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Live party presentation: six in-game-style slots on the GBA pixel grid (#151) above a
 * Navigator detail panel with STATUS / MOVES / DEFENSE pages (#133).
 *
 * The slot views, the detail hierarchy, and the selected page are retained between polling
 * updates so a 10 Hz data stream does not interrupt touch, scroll, or controller focus.
 */
class PartyScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : LinearLayout(context) {

    private data class StatHolder(val value: TextView, val detail: TextView)

    private data class MoveHolder(
        val row: LinearLayout,
        val name: TextView,
        val typeContainer: LinearLayout,
        val meta: TextView,
        var moveId: Int = -1
    )

    private data class DetailHolder(
        val pid: Long,
        var species: Int,
        val title: TextView,
        val level: TextView,
        val speciesLabel: TextView,
        val typeRow: LinearLayout,
        val hpLabel: TextView,
        val hpMeter: SegmentedMeterView,
        val nature: TextView,
        val heldItem: TextView,
        val stats: List<StatHolder>,
        val evTotal: TextView,
        val moves: List<MoveHolder>,
        val defenseContent: LinearLayout
    )

    private val slotGrid: PartySlotGrid
    private val detailContainer: LinearLayout
    private var detailHolder: DetailHolder? = null
    private var lastDetailEmptyKey: String? = null
    private var lastSelectedIdx = -1
    /** Selected detail page; survives live updates and switching party members. */
    private var detailPage = 0
    private var viewScope: CoroutineScope? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)
        val pad = context.dp(DualDexTheme.Spacing.compact)

        slotGrid = PartySlotGrid(context, ::onMemberSelected)
        addView(slotGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(pad, pad, pad, pad)
        })

        val verticalScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        detailContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(pad, 0, pad, context.dp(DualDexTheme.Spacing.section))
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
            scope.launch { viewModel.runtimeRomTrust.collectLatest { refreshUI() } }
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
        updateSlots(party, selected)
        updateDetail(party, selected, viewModel.activeGameId.value)
    }

    private fun updateSlots(party: List<ParsedPokemon>, selectedIdx: Int) {
        val pack = viewModel.activeGameDataPack
        slotGrid.slots.forEachIndexed { index, slot ->
            val mon = party.getOrNull(index)
            slot.bind(mon?.let {
                val species = pack.resolveSpecies(it.species)
                PartySlotState(
                    name = it.nickname.ifBlank { species.name.ifBlank { "#${it.species}" } },
                    level = it.level,
                    currentHp = it.currentHp,
                    maxHp = it.maxHp,
                    status = PartySlotModel.statusLabel(it),
                    gender = PartySlotModel.gender(it.pid, species.genderRatio),
                    shiny = it.isShiny,
                    isEgg = it.isEgg,
                    selected = index == selectedIdx,
                )
            })
        }
    }

    private fun onMemberSelected(index: Int) {
        if (index == lastSelectedIdx && detailHolder != null) return
        lastSelectedIdx = index
        viewModel.selectMember(index)
        refreshUI()
    }

    private fun updateDetail(party: List<ParsedPokemon>, selectedIdx: Int, gameId: Int) {
        if (party.isEmpty()) {
            val trust = viewModel.runtimeRomTrust.value
            val (emptyTitle, emptyDetail) = resolveDetailEmptyState(trust)
            val key = "$emptyTitle|$emptyDetail"
            if (detailHolder != null || lastDetailEmptyKey != key || detailContainer.childCount == 0) {
                detailHolder = null
                lastDetailEmptyKey = key
                detailContainer.removeAllViews()
                detailContainer.addView(DualDexComponents.emptyState(context, emptyTitle, emptyDetail))
            }
            return
        }
        lastDetailEmptyKey = null

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
        val gap = context.dp(DualDexTheme.Spacing.compact)

        // Summary panel: identity, types, HP. Nature and item lead the Status page.
        val summary = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context, elevated = true)
            setPadding(context.dp(DualDexTheme.Spacing.standard), gap, context.dp(DualDexTheme.Spacing.standard), gap)
        }
        val titleRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.sectionTitle
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val speciesLabel = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            isSingleLine = true
            setPadding(gap, 0, gap, 0)
        }
        val typeRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val level = TextView(context).apply {
            setTextColor(DualDexTheme.Color.accent)
            textSize = DualDexTheme.Type.body
            typeface = DualDexTheme.Type.device
            gravity = Gravity.END
            setPadding(gap, 0, 0, 0)
        }
        titleRow.addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        titleRow.addView(speciesLabel, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(typeRow)
        titleRow.addView(level)
        summary.addView(titleRow)

        val hpRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, gap, 0, 0)
        }
        val hpLabel = TextView(context).apply {
            textSize = DualDexTheme.Type.meta
            typeface = DualDexTheme.Type.device
            isSingleLine = true
        }
        val hpMeter = SegmentedMeterView(context)
        hpRow.addView(hpLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = gap })
        hpRow.addView(hpMeter, LayoutParams(0, context.dp(10), 1f))
        summary.addView(hpRow)

        val infoRow = LinearLayout(context).apply { setPadding(0, 0, 0, gap) }
        val nature = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
        }
        val heldItem = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            gravity = Gravity.END
        }
        infoRow.addView(nature, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        infoRow.addView(heldItem, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        detailContainer.addView(summary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = gap
        })

        // Pages.
        val statusPage = LinearLayout(context).apply { orientation = VERTICAL }
        val movesPage = LinearLayout(context).apply { orientation = VERTICAL }
        val defensePage = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, gap, 0, 0)
        }
        val pages = listOf(statusPage, movesPage, defensePage)
        fun showPage(index: Int) = pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }
        detailContainer.addView(
            DualDexComponents.segmentedControl(context, listOf("Status", "Moves", "Defense"), detailPage) { index ->
                detailPage = index
                showPage(index)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = gap }
        )
        pages.forEach { detailContainer.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
        showPage(detailPage)
        statusPage.addView(infoRow)

        val statHolders = ArrayList<StatHolder>(6)
        val statLabels = listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE")
        repeat(2) { rowIndex ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            repeat(3) { columnIndex ->
                val cell = LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = DualDexComponents.surface(context)
                    setPadding(gap, context.dp(DualDexTheme.Spacing.tight), gap, context.dp(DualDexTheme.Spacing.tight))
                }
                val labelRow = LinearLayout(context).apply { gravity = Gravity.BOTTOM }
                labelRow.addView(DualDexComponents.microLabel(context, statLabels[rowIndex * 3 + columnIndex]), LayoutParams(
                    0, LayoutParams.WRAP_CONTENT, 1f
                ))
                val value = TextView(context).apply {
                    setTextColor(DualDexTheme.Color.textPrimary)
                    textSize = DualDexTheme.Type.sectionTitle
                    typeface = DualDexTheme.Type.device
                }
                labelRow.addView(value)
                val detail = TextView(context).apply {
                    setTextColor(DualDexTheme.Color.textSecondary)
                    textSize = DualDexTheme.Type.compact
                }
                cell.addView(labelRow)
                cell.addView(detail)
                row.addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (columnIndex < 2) marginEnd = context.dp(DualDexTheme.Spacing.tight)
                })
                statHolders += StatHolder(value, detail)
            }
            statusPage.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(DualDexTheme.Spacing.tight)
            })
        }
        val evTotal = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
        }
        statusPage.addView(evTotal)

        val moveHolders = ArrayList<MoveHolder>(4)
        repeat(4) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
                background = DualDexComponents.surface(context)
                setPadding(context.dp(DualDexTheme.Spacing.standard), 0, gap, 0)
            }
            val name = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            val typeContainer = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(gap, 0, gap, 0)
            }
            val meta = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.compact
                typeface = DualDexTheme.Type.device
                gravity = Gravity.END
                isSingleLine = true
            }
            row.addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(typeContainer)
            row.addView(meta)
            movesPage.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(DualDexTheme.Spacing.tight)
            })
            moveHolders += MoveHolder(row, name, typeContainer, meta)
        }

        return DetailHolder(
            mon.pid, mon.species, title, level, speciesLabel, typeRow, hpLabel, hpMeter, nature, heldItem,
            statHolders, evTotal, moveHolders, defensePage
        ).also { bindDetail(it, mon, gameId, forceTypes = true) }
    }

    private fun bindDetail(holder: DetailHolder, mon: ParsedPokemon, gameId: Int, forceTypes: Boolean = false) {
        val pack = viewModel.activeGameDataPack
        val species = pack.resolveSpecies(mon.species)
        val speciesDisplayName = if (species.name.isNotBlank()) species.name else "Unknown Species #${mon.species}"
        holder.title.text = buildString {
            append(mon.nickname.ifBlank { speciesDisplayName })
            if (mon.isShiny) append(" ★")
        }
        holder.level.text = "Lv. ${mon.level}"
        holder.speciesLabel.text = speciesDisplayName
        // Same band authority as the pixel slot (Gen 3 quantised), not the raw-ratio colour.
        val hpColor = PartySlotModel.hpColor(mon.currentHp, mon.maxHp)
        holder.hpLabel.text = "HP ${mon.currentHp}/${mon.maxHp}"
        holder.hpLabel.setTextColor(hpColor)
        holder.hpMeter.setValue(mon.currentHp, mon.maxHp, hpColor)

        val nature = NatureTable.get(mon.nature)
        val item = ItemDatabase.get(mon.heldItem, isExpansion = usesExpansionItems(gameId))
        val isHns = !pack.allowGlobalFallback || pack.id.startsWith("hns")
        holder.nature.text = "Nature  ${nature.formattedDescription}"
        holder.heldItem.text = if (mon.heldItem <= 0) {
            "Held item  None"
        } else if (isHns) {
            "Held item  ${item.name} (unverified)"
        } else {
            "Held item  ${item.name}"
        }

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
                moveHolder.moveId = moveId
                return@forEachIndexed
            }
            val move = pack.resolveMove(moveId)
            val pwrText = move.powerDisplay
            val accText = if (move.accuracy > 0) "${move.accuracy}%" else "—"
            val ppMax = if (move.pp > 0) move.pp.toString() else "—"
            moveHolder.row.visibility = View.VISIBLE
            moveHolder.name.text = move.name
            moveHolder.meta.text = "PP ${mon.pp.getOrNull(index) ?: 0}/$ppMax · Pwr $pwrText · Acc $accText"
            // Only rebuild the type badge when the move itself changes, not on every poll.
            if (moveHolder.moveId != moveId) {
                moveHolder.moveId = moveId
                moveHolder.typeContainer.removeAllViews()
                moveHolder.typeContainer.addView(DualDexComponents.typeBadge(context, move.type))
            }
        }

        if (forceTypes || holder.species != mon.species) {
            holder.species = mon.species
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
        val profile = TypeChart.getDefenseProfile(
            type1,
            type2,
            steelResistsGhostDark = gameId == 6,
            pack = viewModel.activeGameDataPack
        )
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

    companion object {
        fun resolveSelectorEmptyLabel(trust: RuntimeRomTrust): String = when {
            trust.hasActiveRom && !trust.mayReadLiveMemory -> RomCompatibilityMessages.badge(trust.status)
            trust.hasActiveRom -> "Waiting for party data"
            else -> "No game loaded"
        }

        fun resolveDetailEmptyState(trust: RuntimeRomTrust): Pair<String, String> = when {
            trust.hasActiveRom && !trust.mayReadLiveMemory ->
                RomCompatibilityMessages.badge(trust.status) to RomCompatibilityMessages.detail(trust.status)
            trust.hasActiveRom ->
                "Waiting for party data" to "Party data will appear when it can be read from the running game."
            else ->
                "No game loaded" to "Party data will appear when a supported game is running."
        }
    }
}

/** Vanilla Emerald (1), FireRed (2) and Ghost Grey (6) keep the Gen III item table. */
internal fun usesExpansionItems(gameId: Int): Boolean = gameId !in setOf(1, 2, 6)
