package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.battle.BattleDamageCalculator
import com.dualdex.battle.BattleParticipantSummary
import com.dualdex.battle.BattlePresentationBuilder
import com.dualdex.battle.CachingBattleDamageCalculator
import com.dualdex.battle.DamageConfidence
import com.dualdex.battle.DataConfidence
import com.dualdex.battle.FieldStatusBuilder
import com.dualdex.battle.FieldStatusData
import com.dualdex.battle.MoveEffectiveness
import com.dualdex.battle.MovePresentation
import com.dualdex.battle.ParticipantSummaryBuilder
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.romhack.RomHackProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BattleConsoleScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : ScrollView(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

    private val headerBadge: TextView
    private val movesContainer: LinearLayout
    private val detailsContainer: LinearLayout
    private val fieldContainer: LinearLayout

    private var viewScope: CoroutineScope? = null
    private var calculationJob: Job? = null
    private val damageCalculator: BattleDamageCalculator = CachingBattleDamageCalculator()

    private var lastAttacker: ParsedPokemon? = null
    private var lastDefender: ParsedPokemon? = null
    private var lastProfile: RomHackProfile? = null
    private var lastCachedMoves: List<MovePresentation> = emptyList()

    init {
        isVerticalScrollBarEnabled = true
        setBackgroundColor(0xFF121216.toInt())

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(24))
        }

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(8))
        }
        val title = TextView(context).apply {
            text = "🎮 Battle Console"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        headerBadge = TextView(context).apply {
            text = "Ready"
            setTextColor(0xFF50C878.toInt())
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFF1E2B22.toInt())
                setStroke(1, 0xFF50C878.toInt())
            }
        }
        topBar.addView(title)
        topBar.addView(headerBadge)
        root.addView(topBar)

        root.addView(TextView(context).apply {
            text = "Read-only experimental console. No inputs are written to the game."
            setTextColor(0xFF888899.toInt())
            textSize = 10.5f
            setPadding(0, 0, 0, dp(6))
        })

        movesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(sectionHeader("⚔️ Active Participants"))
        root.addView(movesContainer)

        detailsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(sectionHeader("📋 Selected Move Details"))
        root.addView(detailsContainer)

        fieldContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(sectionHeader("🌄 Field / Status"))
        root.addView(fieldContainer)

        addView(root)

        refreshUI()
    }

    private fun sectionHeader(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFF4A9EFF.toInt())
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(12), 0, dp(4))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel(null)
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope
        scope.launch { viewModel.playerParty.collectLatest { refreshUI() } }
        scope.launch { viewModel.enemyParty.collectLatest { refreshUI() } }
        scope.launch { viewModel.isInBattle.collectLatest { refreshUI() } }
        scope.launch { viewModel.selectedMemberIndex.collectLatest { refreshUI() } }
        scope.launch { viewModel.activeEnemyMemberIndex.collectLatest { refreshUI() } }
        scope.launch { viewModel.activeProfile.collectLatest { refreshUI() } }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        calculationJob?.cancel()
        calculationJob = null
        viewScope?.cancel(null)
        viewScope = null
    }

    fun refreshUI() {
        val party = viewModel.playerParty.value
        val enemies = viewModel.enemyParty.value
        val selectedIdx = viewModel.selectedMemberIndex.value
        val inBattle = viewModel.isInBattle.value
        val activeEnemyIdx = viewModel.activeEnemyMemberIndex.value
        val profile = viewModel.activeProfile.value

        headerBadge.text = if (inBattle) "⚔️ Battle" else "Ready"
        headerBadge.setTextColor(if (inBattle) 0xFFFF6B6B.toInt() else 0xFF50C878.toInt())

        val attacker: ParsedPokemon? = party.getOrNull(selectedIdx)?.takeIf { !it.isEmpty && it.isValid }
        val defender: ParsedPokemon? = if (inBattle && activeEnemyIdx in enemies.indices) {
            enemies[activeEnemyIdx].takeIf { !it.isEmpty && it.isValid }
        } else {
            null
        }

        renderParticipants(attacker, defender, selectedIdx, activeEnemyIdx, inBattle, profile)
        renderMoveDetails(attacker, defender, profile)
        renderField(attacker, defender, selectedIdx, inBattle, profile)
    }

    private fun renderParticipants(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        selectedIdx: Int,
        activeEnemyIdx: Int,
        inBattle: Boolean,
        profile: RomHackProfile
    ) {
        movesContainer.removeAllViews()

        if (attacker == null && defender == null) {
            movesContainer.addView(emptyLabel("No battle participant data (Waiting for ROM)"))
            return
        }

        if (attacker != null) {
            val attackerSummary = ParticipantSummaryBuilder.build(attacker, selectedIdx, profile)
            movesContainer.addView(participantCard(attackerSummary, "Attacker"))
        } else {
            movesContainer.addView(emptyLabel("No player participant selected."))
        }

        if (inBattle) {
            if (defender != null) {
                val opponentSummary = ParticipantSummaryBuilder.build(defender, activeEnemyIdx, profile)
                movesContainer.addView(participantCard(opponentSummary, "Opponent"))
            } else {
                movesContainer.addView(emptyLabel("Active opponent identity unavailable."))
            }
        } else {
            movesContainer.addView(emptyLabel("Not currently in battle."))
        }
    }

    private fun renderMoveDetails(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        profile: RomHackProfile
    ) {
        if (attacker == null || attacker.isEmpty) {
            calculationJob?.cancel()
            lastCachedMoves = emptyList()
            lastAttacker = null
            lastDefender = null
            lastProfile = null
            detailsContainer.removeAllViews()
            detailsContainer.addView(emptyLabel("No battle participant data (Waiting for ROM)"))
            return
        }

        // If inputs haven't changed, reuse cached presentation views
        if (attacker == lastAttacker && defender == lastDefender && profile == lastProfile && lastCachedMoves.isNotEmpty()) {
            displayMovePresentations(lastCachedMoves, defender)
            return
        }

        // Cancel previous calculation if running
        calculationJob?.cancel()

        val scope = viewScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob()).also { viewScope = it }
        calculationJob = scope.launch {
            val presentations = withContext(Dispatchers.Default) {
                val list = mutableListOf<MovePresentation>()
                for (i in attacker.moves.indices) {
                    val moveId = attacker.moves[i]
                    if (moveId <= 0) continue
                    val info = MoveDatabase.get(moveId)
                    val pres = BattlePresentationBuilder.build(
                        moveInfo = info,
                        currentPp = attacker.pp.getOrNull(i)?.takeIf { it >= 0 },
                        attacker = attacker,
                        defender = defender,
                        profile = profile,
                        calculator = damageCalculator
                    )
                    list += pres
                }
                list
            }
            lastAttacker = attacker
            lastDefender = defender
            lastProfile = profile
            lastCachedMoves = presentations
            displayMovePresentations(presentations, defender)
        }
    }

    private fun displayMovePresentations(presentations: List<MovePresentation>, defender: ParsedPokemon?) {
        detailsContainer.removeAllViews()
        if (defender == null) {
            detailsContainer.addView(emptyLabel("No opponent data. Effectiveness and damage unavailable."))
        }
        if (presentations.isEmpty()) {
            detailsContainer.addView(emptyLabel("No usable moves detected for the selected participant."))
            return
        }
        for (pres in presentations) {
            detailsContainer.addView(moveDetailCard(pres))
        }
    }

    private fun renderField(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        selectedIdx: Int,
        inBattle: Boolean,
        profile: RomHackProfile
    ) {
        fieldContainer.removeAllViews()
        val data: FieldStatusData = FieldStatusBuilder.build(inBattle, attacker, defender, selectedIdx, profile)

        val card = createCardLayout().apply {
            addView(fieldRow("Battle state", if (data.inBattle) "⚔️ In battle" else "Ready"))
            addView(fieldRow("Participant", "${data.participant.displayName} (Lv ${data.participant.level})"))
            addView(fieldRow("Opponent", data.opponent.displayName))
            addView(fieldRow("Condition", data.condition.displayName))
            addView(fieldRow("Condition verified", yesNo(data.conditionVerified)))
            val effNotes = if (data.effectivenessNotes.isEmpty()) "All moves neutral or verified"
            else data.effectivenessNotes.joinToString(" · ")
            addView(fieldRow("Effectiveness notes", effNotes))
            if (data.damageNotes.isNotEmpty()) {
                addView(fieldRow("Damage notes", data.damageNotes.joinToString(" · ")))
            }
        }
        fieldContainer.addView(card)
    }

    private fun participantCard(summary: BattleParticipantSummary, role: String): LinearLayout {
        return createCardLayout().apply {
            addView(TextView(context).apply {
                text = "$role · ${summary.displayName}"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = "Species ${summary.speciesName} · Lv ${summary.level} · HP ${summary.hpDisplay}"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 12f
            })
            if (summary.typeNames.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = "Types: ${summary.typeNames.joinToString("/")}"
                    setTextColor(0xFF888899.toInt())
                    textSize = 11f
                })
            }
            if (summary.statNames.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = summary.statNames.joinToString(" · ") { "${it.first} ${it.second}" }
                    setTextColor(0xFFCCCCCC.toInt())
                    textSize = 11f
                })
            }
            if (summary.moveNames.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = "Moves: ${summary.moveNames.joinToString(" · ")}"
                    setTextColor(0xFFCCCCCC.toInt())
                    textSize = 11f
                })
            }
            val (statusText, statusColor) = when (summary.confidence) {
                DataConfidence.VERIFIED -> "Data: Verified" to 0xFF50C878.toInt()
                DataConfidence.ESTIMATE -> "Data: Estimate" to 0xFFFFAA33.toInt()
                DataConfidence.UNAVAILABLE -> "Data: Unavailable" to 0xFF888899.toInt()
            }
            addView(TextView(context).apply {
                text = statusText
                setTextColor(statusColor)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            })
        }
    }

    private fun moveDetailCard(pres: MovePresentation): LinearLayout {
        return createCardLayout().apply {
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(createTypeBadge(pres.typeName))
            header.addView(TextView(context).apply {
                text = " ${pres.name}"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            })
            val catLabel = when (pres.category) {
                MoveCategory.PHYSICAL -> "Phys"
                MoveCategory.SPECIAL -> "Spec"
                MoveCategory.STATUS -> "Status"
                null -> "—"
            }
            header.addView(TextView(context).apply {
                text = catLabel
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 11f
            })
            addView(header)

            addView(fieldRow("Base Power", pres.powerDisplay))
            addView(fieldRow("Accuracy", pres.accuracyDisplay))
            addView(fieldRow("PP", pres.ppDisplay))
            addView(fieldRow("Description", pres.description))
            val effColor = when (pres.effectivenessConfidence) {
                DataConfidence.VERIFIED -> 0xFF50C878.toInt()
                DataConfidence.ESTIMATE -> 0xFFFFAA33.toInt()
                DataConfidence.UNAVAILABLE -> 0xFF888899.toInt()
            }
            addView(fieldRow("Effectiveness", pres.effectiveness, effColor))
            val dmgLabel = when (pres.damageConfidence) {
                DamageConfidence.VERIFIED -> "Verified damage range"
                DamageConfidence.ESTIMATE -> "Estimated damage range"
                DamageConfidence.UNAVAILABLE -> "Damage range"
            }
            val dmgColor = when (pres.damageConfidence) {
                DamageConfidence.VERIFIED -> 0xFF50C878.toInt()
                DamageConfidence.ESTIMATE -> 0xFFFFAA33.toInt()
                DamageConfidence.UNAVAILABLE -> 0xFF888899.toInt()
            }
            addView(fieldRow(dmgLabel, pres.damageDisplayText, dmgColor))
        }
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xFF1E1E26.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }
    }

    private fun fieldRow(label: String, value: String, colorOverride: Int? = null): TextView {
        return TextView(context).apply {
            text = "$label · $value"
            setTextColor(colorOverride ?: 0xFFCCCCCC.toInt())
            textSize = 12f
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun createTypeBadge(typeName: String): TextView {
        val type = enumValues<PokemonType>().firstOrNull { it.displayName == typeName }
        val colorHex = type?.colorHex?.toInt() ?: 0xFF555555.toInt()
        return TextView(context).apply {
            text = typeName
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(colorHex)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dp(6), 0)
            }
        }
    }

    private fun emptyLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFF888888.toInt())
        textSize = 14f
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
}
