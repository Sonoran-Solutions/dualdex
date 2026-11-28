package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel
import com.dualdex.nuzlocke.AreaEncounter
import com.dualdex.nuzlocke.EncounterOutcome
import com.dualdex.nuzlocke.FallenMon
import com.dualdex.nuzlocke.NuzlockeRunState
import com.dualdex.nuzlocke.NuzlockeStore
import com.dualdex.nuzlocke.areaKeyOf
import com.dualdex.nuzlocke.monIdentityKey
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.ItemDatabase
import com.dualdex.pokemon.unbound.UnboundDataPack
import com.dualdex.romhack.RomCompatibilityMessages
import com.dualdex.romhack.RuntimeRomTrust
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.TypeChart
import com.dualdex.pokemon.NatureTable
import com.dualdex.pokemon.resolveMove
import com.dualdex.pokemon.resolveSpecies
import java.io.File
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

    private data class NuzlockeStripKey(
        val state: NuzlockeRunState?,
        val areaKey: String?,
        val areaName: String?,
        val persistenceError: String?,
        val expanded: Boolean,
        val resetConfirmPending: Boolean
    )

    private val slotGrid: PartySlotGrid
    private val nuzlockeContainer: LinearLayout
    private val nuzlockeToggleBtn: TextView
    private val nuzlockeAreaPill: TextView
    private val nuzlockeFallenPill: TextView
    private val nuzlockeExpandBtn: TextView
    private val nuzlockeErrorText: TextView
    private val nuzlockePanel: LinearLayout
    private val nuzlockeDupesBtn: TextView
    private val nuzlockeLevelCapLabel: TextView
    private val nuzlockeCapDecBtn: TextView
    private val nuzlockeCapIncBtn: TextView
    private val nuzlockeResetBtn: TextView
    private val nuzlockeResetConfirmRow: LinearLayout
    private val nuzlockeEncountersHeader: TextView
    private val nuzlockeEncountersList: LinearLayout
    private val nuzlockeGraveyardHeader: TextView
    private val nuzlockeGraveyardList: LinearLayout
    private val detailContainer: LinearLayout
    private var detailHolder: DetailHolder? = null
    private var lastDetailEmptyKey: String? = null
    private var lastSelectedIdx = -1
    private var nuzlockeExpanded = false
    private var nuzlockeResetConfirmPending = false
    private var lastNuzlockeRenderKey: NuzlockeStripKey? = null
    private var lastNuzlockeRomSha: String? = null
    /** Selected detail page; survives live updates and switching party members. */
    private var detailPage = 0
    private var viewScope: CoroutineScope? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)
        val pad = context.dp(DualDexTheme.Spacing.compact)
        val tight = context.dp(DualDexTheme.Spacing.tight)

        runCatching { context.applicationContext?.filesDir ?: context.filesDir }
            .getOrNull()
            ?.let { dir -> viewModel.attachNuzlockeStore(NuzlockeStore(File(dir, "nuzlocke"))) }

        slotGrid = PartySlotGrid(context, ::onMemberSelected)
        addView(slotGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(pad, pad, pad, pad)
        })

        val verticalScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val scrollContent = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(pad, 0, pad, context.dp(DualDexTheme.Spacing.section))
        }

        // Inline Nuzlocke status bar & collapsible log/graveyard (zero AlertDialog/PopupWindow).
        nuzlockeContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context, elevated = false)
            setPadding(pad, tight, pad, tight)
        }
        val stripRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nuzlockeToggleBtn = buildNuzlockePill(context, "Nuzlocke: OFF") {
            val current = viewModel.nuzlockeState.value ?: return@buildNuzlockePill
            nuzlockeResetConfirmPending = false
            viewModel.setNuzlockeEnabled(!current.enabled)
            refreshUI()
        }
        nuzlockeAreaPill = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.compact
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(pad, tight, pad, tight)
        }
        nuzlockeFallenPill = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            typeface = DualDexTheme.Type.device
            isSingleLine = true
            setPadding(pad, tight, pad, tight)
            background = DualDexComponents.roundedDrawable(
                context, DualDexTheme.Color.elevatedSurface, DualDexTheme.Radius.pill, DualDexTheme.Color.border
            )
        }
        nuzlockeExpandBtn = buildNuzlockePill(context, "Log ▾") {
            nuzlockeExpanded = !nuzlockeExpanded
            if (!nuzlockeExpanded) nuzlockeResetConfirmPending = false
            updateNuzlockeStrip()
        }
        stripRow.addView(nuzlockeToggleBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        stripRow.addView(nuzlockeAreaPill, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = tight
        })
        stripRow.addView(nuzlockeFallenPill, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = tight
        })
        stripRow.addView(nuzlockeExpandBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = tight
        })
        nuzlockeContainer.addView(stripRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        nuzlockeErrorText = TextView(context).apply {
            setTextColor(DualDexTheme.Color.danger)
            textSize = DualDexTheme.Type.compact
            visibility = View.GONE
            setPadding(0, tight, 0, 0)
        }
        nuzlockeContainer.addView(nuzlockeErrorText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        nuzlockePanel = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            setPadding(0, pad, 0, tight)
        }
        nuzlockePanel.addView(DualDexComponents.divider(context), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1).coerceAtLeast(1)).apply {
            bottomMargin = pad
        })

        val controlsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nuzlockeDupesBtn = buildNuzlockePill(context, "Dupes: ON") {
            val current = viewModel.nuzlockeState.value ?: return@buildNuzlockePill
            viewModel.setNuzlockeDupesClause(!current.dupesClause)
            updateNuzlockeStrip()
        }
        nuzlockeCapDecBtn = buildNuzlockePill(context, "−") {
            val current = viewModel.nuzlockeState.value ?: return@buildNuzlockePill
            val next = when (val cap = current.levelCap) {
                null -> null
                in 1..5 -> null
                else -> cap - 5
            }
            viewModel.setNuzlockeLevelCap(next)
            updateNuzlockeStrip()
        }.apply { contentDescription = "Decrease Nuzlocke level cap" }
        nuzlockeLevelCapLabel = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.compact
            typeface = DualDexTheme.Type.device
            gravity = Gravity.CENTER
            isSingleLine = true
            setPadding(tight, 0, tight, 0)
        }
        nuzlockeCapIncBtn = buildNuzlockePill(context, "+") {
            val current = viewModel.nuzlockeState.value ?: return@buildNuzlockePill
            val next = ((current.levelCap ?: 0) + 5).coerceAtMost(100)
            viewModel.setNuzlockeLevelCap(next)
            updateNuzlockeStrip()
        }.apply { contentDescription = "Increase Nuzlocke level cap" }
        nuzlockeResetBtn = buildNuzlockePill(context, "Reset Run", DualDexButtonStyle.SECONDARY) {
            nuzlockeResetConfirmPending = true
            updateNuzlockeStrip()
        }
        controlsRow.addView(nuzlockeDupesBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        controlsRow.addView(nuzlockeCapDecBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = pad
        })
        controlsRow.addView(nuzlockeLevelCapLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        controlsRow.addView(nuzlockeCapIncBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        controlsRow.addView(View(context), LayoutParams(0, 1, 1f))
        controlsRow.addView(nuzlockeResetBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        nuzlockePanel.addView(controlsRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        nuzlockeResetConfirmRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = DualDexComponents.surface(context, elevated = true)
            setPadding(pad, tight, pad, tight)
        }
        nuzlockeResetConfirmRow.addView(TextView(context).apply {
            text = "Clear encounters and graveyard?"
            setTextColor(DualDexTheme.Color.warning)
            textSize = DualDexTheme.Type.compact
            typeface = Typeface.DEFAULT_BOLD
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        nuzlockeResetConfirmRow.addView(buildNuzlockePill(context, "Cancel", DualDexButtonStyle.SECONDARY) {
            nuzlockeResetConfirmPending = false
            updateNuzlockeStrip()
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = tight
        })
        nuzlockeResetConfirmRow.addView(buildNuzlockePill(context, "Confirm Reset", DualDexButtonStyle.DESTRUCTIVE) {
            nuzlockeResetConfirmPending = false
            viewModel.resetNuzlockeRun()
            refreshUI()
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = tight
        })
        nuzlockePanel.addView(nuzlockeResetConfirmRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = pad
        })

        nuzlockeEncountersHeader = DualDexComponents.microLabel(context, "ENCOUNTERS")
        nuzlockeEncountersList = LinearLayout(context).apply { orientation = VERTICAL }
        nuzlockeGraveyardHeader = DualDexComponents.microLabel(context, "GRAVEYARD", DualDexTheme.Color.danger)
        nuzlockeGraveyardList = LinearLayout(context).apply { orientation = VERTICAL }

        nuzlockePanel.addView(nuzlockeEncountersHeader, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = pad
            bottomMargin = tight
        })
        nuzlockePanel.addView(nuzlockeEncountersList, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        nuzlockePanel.addView(nuzlockeGraveyardHeader, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = pad
            bottomMargin = tight
        })
        nuzlockePanel.addView(nuzlockeGraveyardList, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        nuzlockeContainer.addView(nuzlockePanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        scrollContent.addView(nuzlockeContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = pad
        })

        detailContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        scrollContent.addView(detailContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        verticalScroll.addView(scrollContent)
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
            scope.launch { viewModel.nuzlockeState.collectLatest { refreshUI() } }
            scope.launch { viewModel.nuzlockePersistenceError.collectLatest { updateNuzlockeStrip() } }
            scope.launch { viewModel.playerLocation.collectLatest { updateNuzlockeStrip() } }
            scope.launch { viewModel.resolvedLocation.collectLatest { updateNuzlockeStrip() } }
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
        updateNuzlockeStrip()
        updateDetail(party, selected, viewModel.activeGameId.value)
    }

    private fun updateSlots(party: List<ParsedPokemon>, selectedIdx: Int) {
        val pack = viewModel.activeGameDataPack
        val nuzlocke = viewModel.nuzlockeState.value
        val fallenKeys = if (nuzlocke?.enabled == true) nuzlocke.fallen.keys else emptySet()
        slotGrid.slots.forEachIndexed { index, slot ->
            val mon = party.getOrNull(index)
            slot.bind(mon?.let {
                val species = pack.resolveSpecies(it.species)
                val monKey = monIdentityKey(it)
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
                    isFallenNuzlocke = monKey.isNotEmpty() && monKey in fallenKeys,
                )
            })
        }
    }

    private fun updateNuzlockeStrip() {
        val state = viewModel.nuzlockeState.value
        if (state?.romSha256 != lastNuzlockeRomSha) {
            lastNuzlockeRomSha = state?.romSha256
            nuzlockeResetConfirmPending = false
        }
        val loc = viewModel.playerLocation.value?.takeIf { it.isValid }
        val areaKey = loc?.let { areaKeyOf(it) }
        val areaName = viewModel.resolvedLocation.value?.name?.takeIf { it.isNotBlank() }
            ?: areaKey?.let { "Area $it" }
        val persistenceError = viewModel.nuzlockePersistenceError.value

        val renderKey = NuzlockeStripKey(
            state = state,
            areaKey = areaKey,
            areaName = areaName,
            persistenceError = persistenceError,
            expanded = nuzlockeExpanded,
            resetConfirmPending = nuzlockeResetConfirmPending
        )
        if (renderKey == lastNuzlockeRenderKey) return
        lastNuzlockeRenderKey = renderKey

        if (persistenceError != null) {
            nuzlockeErrorText.text = "Save failed: $persistenceError"
            nuzlockeErrorText.visibility = View.VISIBLE
        } else {
            nuzlockeErrorText.visibility = View.GONE
        }

        if (state == null) {
            nuzlockeToggleBtn.text = "Nuzlocke: —"
            nuzlockeToggleBtn.isEnabled = false
            nuzlockeToggleBtn.isSelected = false
            nuzlockeAreaPill.visibility = View.GONE
            nuzlockeFallenPill.visibility = View.GONE
            nuzlockeExpandBtn.visibility = View.GONE
            nuzlockePanel.visibility = View.GONE
            return
        }

        nuzlockeToggleBtn.isEnabled = true
        nuzlockeToggleBtn.isSelected = state.enabled
        nuzlockeToggleBtn.text = if (state.enabled) "Nuzlocke: ON" else "Nuzlocke: OFF"
        nuzlockeToggleBtn.setTextColor(
            if (state.enabled) DualDexTheme.Color.accent else DualDexTheme.Color.textSecondary
        )

        if (!state.enabled) {
            nuzlockeAreaPill.visibility = View.GONE
            nuzlockeFallenPill.visibility = View.GONE
            nuzlockeExpandBtn.visibility = View.GONE
            nuzlockePanel.visibility = View.GONE
            return
        }

        nuzlockeAreaPill.visibility = View.VISIBLE
        nuzlockeFallenPill.visibility = View.VISIBLE
        nuzlockeExpandBtn.visibility = View.VISIBLE

        val areaEncounter = areaKey?.let { state.encounters[it] }
        nuzlockeAreaPill.text = if (areaName != null) {
            val statusLabel = areaEncounter?.outcome?.label ?: "Open"
            "$areaName: $statusLabel"
        } else {
            "Area: —"
        }
        nuzlockeAreaPill.setTextColor(
            when (areaEncounter?.outcome) {
                EncounterOutcome.CAUGHT -> DualDexTheme.Color.success
                EncounterOutcome.MISSED -> DualDexTheme.Color.danger
                EncounterOutcome.MET, EncounterOutcome.PENDING -> DualDexTheme.Color.warning
                EncounterOutcome.DUPES_SKIPPED, null -> DualDexTheme.Color.textPrimary
            }
        )

        val fallenCount = state.fallen.size
        nuzlockeFallenPill.text = "Fallen: $fallenCount"
        nuzlockeFallenPill.setTextColor(
            if (fallenCount > 0) DualDexTheme.Color.danger else DualDexTheme.Color.textSecondary
        )
        nuzlockeExpandBtn.text = if (nuzlockeExpanded) "Log ▴" else "Log ▾"
        nuzlockePanel.visibility = if (nuzlockeExpanded) View.VISIBLE else View.GONE

        if (!nuzlockeExpanded) return

        nuzlockeDupesBtn.text = if (state.dupesClause) "Dupes: ON" else "Dupes: OFF"
        nuzlockeDupesBtn.isSelected = state.dupesClause
        nuzlockeLevelCapLabel.text = state.levelCap?.let { "Cap Lv$it" } ?: "Cap: Off"
        nuzlockeCapDecBtn.isEnabled = state.levelCap != null
        nuzlockeResetBtn.visibility = if (nuzlockeResetConfirmPending) View.GONE else View.VISIBLE
        nuzlockeResetConfirmRow.visibility = if (nuzlockeResetConfirmPending) View.VISIBLE else View.GONE

        rebuildEncountersList(state.encounters.values.toList())
        rebuildGraveyardList(state.fallen.values.toList())
    }

    private fun rebuildEncountersList(encounters: List<AreaEncounter>) {
        nuzlockeEncountersHeader.text = "ENCOUNTERS (${encounters.size})"
        nuzlockeEncountersList.removeAllViews()
        if (encounters.isEmpty()) {
            nuzlockeEncountersList.addView(nuzlockeEmptyRow("No wild encounters recorded yet."))
            return
        }
        val tight = context.dp(DualDexTheme.Spacing.tight)
        for (enc in encounters.asReversed()) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, tight, 0, tight)
            }
            val label = TextView(context).apply {
                val suffix = if (enc.outcome == EncounterOutcome.PENDING) " (Pending)" else ""
                text = "${enc.areaName} · ${enc.speciesName} Lv.${enc.level}$suffix"
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.compact
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            if (enc.outcome == EncounterOutcome.PENDING) {
                val caughtBtn = buildNuzlockePill(context, "Caught") {
                    viewModel.overrideNuzlockeEncounter(enc.areaKey, EncounterOutcome.CAUGHT)
                    updateNuzlockeStrip()
                }.apply { setTextColor(DualDexTheme.Color.success) }
                val missedBtn = buildNuzlockePill(context, "Missed") {
                    viewModel.overrideNuzlockeEncounter(enc.areaKey, EncounterOutcome.MISSED)
                    updateNuzlockeStrip()
                }.apply { setTextColor(DualDexTheme.Color.danger) }
                row.addView(caughtBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = tight
                })
                row.addView(missedBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = tight
                })
            } else {
                val outcomeBtn = buildNuzlockePill(context, enc.outcome.label) {
                    viewModel.overrideNuzlockeEncounter(enc.areaKey, nextEncounterOutcome(enc.outcome))
                    updateNuzlockeStrip()
                }.apply {
                    setTextColor(
                        when (enc.outcome) {
                            EncounterOutcome.CAUGHT -> DualDexTheme.Color.success
                            EncounterOutcome.MISSED -> DualDexTheme.Color.danger
                            EncounterOutcome.MET, EncounterOutcome.PENDING -> DualDexTheme.Color.warning
                            EncounterOutcome.DUPES_SKIPPED -> DualDexTheme.Color.textSecondary
                        }
                    )
                }
                row.addView(outcomeBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = tight
                })
            }
            nuzlockeEncountersList.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun rebuildGraveyardList(fallen: List<FallenMon>) {
        nuzlockeGraveyardHeader.text = "GRAVEYARD (${fallen.size})"
        nuzlockeGraveyardList.removeAllViews()
        if (fallen.isEmpty()) {
            nuzlockeGraveyardList.addView(nuzlockeEmptyRow("No fallen Pokémon yet."))
            return
        }
        val tight = context.dp(DualDexTheme.Spacing.tight)
        for (mon in fallen.asReversed()) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, tight, 0, tight)
            }
            val displayTitle = if (mon.nickname.equals(mon.speciesName, ignoreCase = true)) {
                mon.speciesName
            } else {
                "${mon.nickname} (${mon.speciesName})"
            }
            val label = TextView(context).apply {
                text = "$displayTitle Lv.${mon.level} · ${mon.areaName}"
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.compact
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            val reviveBtn = buildNuzlockePill(context, "Revive") {
                viewModel.reviveNuzlockeFallen(mon.monKey)
                refreshUI()
            }
            row.addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(reviveBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = tight
            })
            nuzlockeGraveyardList.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun nuzlockeEmptyRow(message: String): TextView = TextView(context).apply {
        text = message
        setTextColor(DualDexTheme.Color.textSecondary)
        textSize = DualDexTheme.Type.compact
        setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.tight))
    }

    private fun buildNuzlockePill(
        context: Context,
        label: String,
        style: DualDexButtonStyle = DualDexButtonStyle.SECONDARY,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        minimumHeight = context.dp(DualDexTheme.Spacing.touchTarget)
        minWidth = context.dp(DualDexTheme.Spacing.touchTarget)
        setPadding(
            context.dp(DualDexTheme.Spacing.compact),
            context.dp(DualDexTheme.Spacing.tight),
            context.dp(DualDexTheme.Spacing.compact),
            context.dp(DualDexTheme.Spacing.tight)
        )
        textSize = DualDexTheme.Type.compact
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(
            when (style) {
                DualDexButtonStyle.DESTRUCTIVE -> DualDexTheme.Color.onDanger
                DualDexButtonStyle.PRIMARY -> DualDexTheme.Color.onAccent
                DualDexButtonStyle.SECONDARY, DualDexButtonStyle.GHOST -> DualDexTheme.Color.textPrimary
            }
        )
        background = DualDexComponents.controlBackground(context, style, selected = false)
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun nextEncounterOutcome(current: EncounterOutcome): EncounterOutcome = when (current) {
        EncounterOutcome.MET, EncounterOutcome.PENDING -> EncounterOutcome.CAUGHT
        EncounterOutcome.CAUGHT -> EncounterOutcome.MISSED
        EncounterOutcome.MISSED -> EncounterOutcome.DUPES_SKIPPED
        EncounterOutcome.DUPES_SKIPPED -> EncounterOutcome.CAUGHT
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
        val item = resolveHeldItem(mon.heldItem, gameId, pack)
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

/**
 * Vanilla Emerald (1), FireRed (2) and Ghost Grey (6) keep the Gen III item table. Unbound (9) is not
 * pokeemerald-expansion either; [resolveHeldItem] uses its exact identity mapping.
 */
internal fun usesExpansionItems(gameId: Int): Boolean = gameId !in setOf(1, 2, 6, UNBOUND_GAME_ID)

private const val UNBOUND_GAME_ID = 9

/**
 * Both party screens resolve CFRU held items through the exact Unbound pack, which the registry only
 * selects for the exact ROM hash; any other Unbound build keeps an ID placeholder.
 */
internal fun resolveHeldItem(itemId: Int, gameId: Int, pack: GameDataPack): ItemDatabase.ItemInfo = when {
    gameId != UNBOUND_GAME_ID -> ItemDatabase.get(itemId, isExpansion = usesExpansionItems(gameId))
    pack === UnboundDataPack || itemId <= 0 -> UnboundDataPack.getItem(itemId)
    else -> ItemDatabase.ItemInfo(itemId, "Item #$itemId")
}
