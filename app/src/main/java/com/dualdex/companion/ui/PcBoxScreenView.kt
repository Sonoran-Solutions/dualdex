package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.PcBoxAvailability
import com.dualdex.pokemon.PcBoxSlot
import com.dualdex.pokemon.PcBoxSnapshot
import com.dualdex.pokemon.resolveMove

internal class PcBoxScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : LinearLayout(context) {
    private val gap = context.dp(4)
    private var snapshot = viewModel.pcBoxSnapshot.value
    private var selectedSlot: Int? = null
    private var requestedBox = PcBoxSnapshot.CURRENT_BOX

    private val previous = DualDexComponents.secondaryButton(context, "‹") { load(snapshot.boxIndex - 1) }
    private val next = DualDexComponents.secondaryButton(context, "›") { load(snapshot.boxIndex + 1) }
    private val refresh = DualDexComponents.secondaryButton(context, "Refresh") { load(requestedBox) }
    private val boxLabel = label("").apply {
        gravity = Gravity.CENTER
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val status = label("").apply {
        setPadding(0, gap, 0, gap)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    private val cells: List<TextView>
    private val detail = DualDexComponents.surfaceCard(context).apply { visibility = View.GONE }

    init {
        orientation = VERTICAL
        val pad = context.dp(DualDexTheme.Spacing.compact)
        setPadding(pad, 0, pad, pad)

        val controls = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        previous.contentDescription = "Previous box"
        next.contentDescription = "Next box"
        controls.addView(previous)
        controls.addView(boxLabel, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(next)
        controls.addView(refresh, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = gap
        })
        addView(controls)

        cells = buildList {
            repeat(5) { row ->
                val line = LinearLayout(context)
                repeat(6) { column ->
                    val index = row * 6 + column
                    val cell = label("").apply {
                        gravity = Gravity.CENTER
                        textSize = DualDexTheme.Type.micro
                        minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                        background = DualDexComponents.controlBackground(
                            context, DualDexButtonStyle.SECONDARY, selected = false
                        )
                        setOnClickListener {
                            selectedSlot = index
                            updateSelection()
                            detail.post {
                                detail.requestRectangleOnScreen(Rect(0, 0, detail.width, context.dp(48)), false)
                            }
                        }
                    }
                    add(cell)
                    line.addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                        if (column > 0) marginStart = context.dp(2)
                    })
                }
                grid.addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = gap
                })
            }
        }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(status)
            addView(grid)
            addView(detail)
        }
        addView(ScrollView(context).apply { addView(content) }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        render(snapshot)
    }

    fun open() {
        val current = viewModel.pcBoxSnapshot.value
        load(if (current.availability == PcBoxAvailability.AVAILABLE) current.boxIndex else PcBoxSnapshot.CURRENT_BOX)
    }

    fun render(newSnapshot: PcBoxSnapshot) {
        if (snapshot.boxIndex != newSnapshot.boxIndex) selectedSlot = null
        snapshot = newSnapshot
        val available = snapshot.availability == PcBoxAvailability.AVAILABLE
        val loading = snapshot.availability == PcBoxAvailability.LOADING
        if (available) requestedBox = snapshot.boxIndex
        if (snapshot.availability == PcBoxAvailability.IDLE || snapshot.availability == PcBoxAvailability.UNSUPPORTED) {
            requestedBox = PcBoxSnapshot.CURRENT_BOX
        }
        val gameBox = if (snapshot.currentBoxIndex == snapshot.boxIndex) " · Current" else ""
        boxLabel.text = if (available) "Box ${snapshot.boxIndex + 1} / ${snapshot.boxCount}$gameBox" else "PC Boxes"
        previous.isEnabled = available && snapshot.boxIndex > 0
        next.isEnabled = available && snapshot.boxIndex < snapshot.boxCount - 1
        val trusted = viewModel.runtimeRomTrust.value.mayReadLiveMemory
        refresh.isEnabled = !loading && trusted
        grid.visibility = if (available) View.VISIBLE else View.GONE
        status.text = when (snapshot.availability) {
            PcBoxAvailability.IDLE ->
                if (trusted) "Press Refresh to read PC Boxes." else "PC Boxes unavailable for this ROM build"
            PcBoxAvailability.UNSUPPORTED -> "PC Boxes unavailable for this ROM build"
            PcBoxAvailability.UNREADABLE -> "Couldn’t read PC Boxes. Press Refresh to try again."
            PcBoxAvailability.LOADING -> "Reading PC Boxes…"
            PcBoxAvailability.AVAILABLE -> if (snapshot.occupiedSlots().isEmpty()) "This box is empty." else ""
        }
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE

        val pack = viewModel.activeGameDataPack
        if (available) cells.forEachIndexed { index, cell ->
            val slot = snapshot.slots.getOrNull(index)
            val mon = slot?.pokemon
            val name = slot?.takeIf { it.isOccupied }?.speciesName(pack)
            val occupied = name != null
            cell.isEnabled = occupied
            cell.isFocusable = occupied
            cell.text = if (name == null || mon == null) "—" else buildString {
                append(name)
                if (mon.isEgg || mon.isShiny) {
                    append('\n')
                    if (mon.isEgg) append("Egg")
                    if (mon.isShiny) append(if (mon.isEgg) " ★" else "★")
                }
            }
            cell.contentDescription = buildString {
                append("Slot ${index + 1}, ")
                append(name ?: "empty")
                if (mon?.isEgg == true) append(", egg")
                if (mon?.isShiny == true) append(", shiny")
            }
        }
        updateSelection()
    }

    private fun load(boxIndex: Int) {
        requestedBox = boxIndex
        viewModel.refreshPcBox(boxIndex)
    }

    private fun updateSelection() {
        val slot = selectedSlot?.let(snapshot.slots::getOrNull)?.takeIf { it.isOccupied }
        cells.forEachIndexed { index, cell -> cell.isSelected = index == slot?.index }
        detail.removeAllViews()
        detail.visibility = View.GONE
        val mon = slot?.pokemon ?: return
        val pack = viewModel.activeGameDataPack
        val name = slot.speciesName(pack)
        val title = mon.nickname.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
            ?.let { "$it · $name" } ?: name
        val level = when {
            mon.isEgg -> "Egg"
            else -> slot.estimatedLevel?.let { "Lv. $it" } ?: "Level unavailable"
        }
        detail.addView(DualDexComponents.sectionTitle(context, "$title · $level"))
        if (!mon.isEgg) detail.addView(label(PcBoxSnapshot.LEVEL_NOTE))
        detail.addView(label("Nature  ${mon.natureName}"))
        detail.addView(label("Ability  ${slot.abilityName ?: "Unavailable"}"))
        val item = resolveHeldItem(mon.heldItem, viewModel.activeGameId.value, pack)
        detail.addView(label("Held item  ${item.name}"))
        val moves = mon.moves.filter { it > 0 }.joinToString(" · ") { pack.resolveMove(it).name }
        detail.addView(label("Moves  ${moves.ifEmpty { "None" }}"))
        detail.addView(label("IVs and EVs").apply { setPadding(0, gap, 0, 0) })
        val stats = listOf(
            Triple("HP", mon.hpIv, mon.hpEv), Triple("Attack", mon.attackIv, mon.attackEv),
            Triple("Defense", mon.defenseIv, mon.defenseEv), Triple("Sp. Atk", mon.spAttackIv, mon.spAttackEv),
            Triple("Sp. Def", mon.spDefenseIv, mon.spDefenseEv), Triple("Speed", mon.speedIv, mon.speedEv)
        )
        stats.chunked(2).forEach { pair ->
            detail.addView(LinearLayout(context).apply {
                pair.forEach { (stat, iv, ev) ->
                    addView(label("$stat  $iv IV · $ev EV"), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                }
            })
        }
        val displayed = snapshot
        detail.addView(DualDexComponents.primaryButton(context, "Send to Calc") {
            viewModel.sendPcBoxToCalc(displayed, slot)
        }.apply { isEnabled = slot.canSendToCalc })
        detail.visibility = View.VISIBLE
    }

    private fun label(value: String) = TextView(context).apply {
        text = value
        textSize = DualDexTheme.Type.compact
        setTextColor(DualDexTheme.Color.textPrimary)
    }
}
