package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.battle.*
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.romhack.RomHackProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

enum class ConsoleSubtab(val title: String) {
    BATTLE("⚔️ Battle"),
    PARTY("👥 Party"),
    FIELD("🌄 Field"),
    DETAILS("📋 Details")
}

class BattleConsoleScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : ScrollView(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

    private val headerBadge: TextView
    private val actionStatusBadge: TextView
    private val tabButtons = mutableMapOf<ConsoleSubtab, TextView>()
    private val contentContainer: LinearLayout

    private var activeSubtab = ConsoleSubtab.BATTLE
    private var actionFeedbackText: String? = null
    private var actionFeedbackResetJob: Job? = null

    private var viewScope: CoroutineScope? = null
    private var calculationJob: Job? = null
    private val damageCalculator: BattleDamageCalculator = CachingBattleDamageCalculator()

    private var lastAttacker: ParsedPokemon? = null
    private var lastDefender: ParsedPokemon? = null
    private var lastProfile: RomHackProfile? = null
    private var lastPlayerStages: StatStages? = null
    private var lastEnemyStages: StatStages? = null
    private var lastCachedMoves: List<MovePresentation> = emptyList()

    init {
        isVerticalScrollBarEnabled = true
        setBackgroundColor(0xFF121216.toInt())

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(24))
        }

        // Top bar
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(6))
        }
        val title = TextView(context).apply {
            text = "🎮 Battle Console"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        actionStatusBadge = TextView(context).apply {
            text = "Input Ready"
            setTextColor(0xFF50C878.toInt())
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = badgeDrawable(0xFF1E2B22.toInt(), 0xFF50C878.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dp(6), 0)
            }
        }
        headerBadge = TextView(context).apply {
            text = "Ready"
            setTextColor(0xFF50C878.toInt())
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = badgeDrawable(0xFF1E2B22.toInt(), 0xFF50C878.toInt())
        }
        topBar.addView(title)
        topBar.addView(actionStatusBadge)
        topBar.addView(headerBadge)
        root.addView(topBar)

        // Subtabs Navigation Bar
        val subtabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(10))
        }
        for (subtab in ConsoleSubtab.values()) {
            val btn = TextView(context).apply {
                text = subtab.title
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    if (activeSubtab != subtab) {
                        activeSubtab = subtab
                        updateSubtabButtons()
                        refreshUI()
                    }
                }
            }
            tabButtons[subtab] = btn
            subtabRow.addView(btn)
        }
        root.addView(subtabRow)
        updateSubtabButtons()

        // Dynamic Subtab Content Container
        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(contentContainer)

        addView(root)
        refreshUI()
    }

    private fun updateSubtabButtons() {
        for ((subtab, btn) in tabButtons) {
            val isSelected = subtab == activeSubtab
            btn.setTextColor(if (isSelected) 0xFF4A9EFF.toInt() else 0xFF888899.toInt())
            btn.background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (isSelected) 0xFF222636.toInt() else 0xFF181820.toInt())
                if (isSelected) {
                    setStroke(dp(1), 0xFF4A9EFF.toInt())
                }
            }
        }
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
        scope.launch { viewModel.playerStatStages.collectLatest { refreshUI() } }
        scope.launch { viewModel.enemyStatStages.collectLatest { refreshUI() } }
        scope.launch { viewModel.battleUiSnapshot.collectLatest { refreshUI() } }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        calculationJob?.cancel()
        calculationJob = null
        actionFeedbackResetJob?.cancel()
        viewScope?.cancel(null)
        viewScope = null
    }

    private fun showFeedback(msg: String, durationMs: Long = 2500L) {
        actionFeedbackText = msg
        actionStatusBadge.text = msg
        actionStatusBadge.setTextColor(0xFFFFAA33.toInt())
        actionStatusBadge.background = badgeDrawable(0xFF2E2211.toInt(), 0xFFFFAA33.toInt())

        actionFeedbackResetJob?.cancel()
        actionFeedbackResetJob = (viewScope ?: CoroutineScope(Dispatchers.Main)).launch {
            delay(durationMs)
            actionFeedbackText = null
            refreshActionStatusBadge()
        }
    }

    private fun refreshActionStatusBadge() {
        if (actionFeedbackText != null) return

        val uiSnap = viewModel.battleUiSnapshot.value
        val inBattle = viewModel.isInBattle.value
        if (!inBattle) {
            actionStatusBadge.text = "Idle"
            actionStatusBadge.setTextColor(0xFF888899.toInt())
            actionStatusBadge.background = badgeDrawable(0xFF1E1E26.toInt(), 0xFF888899.toInt())
            return
        }

        if (uiSnap.isInputAccepted) {
            actionStatusBadge.text = "Input Ready (${uiSnap.state.displayName})"
            actionStatusBadge.setTextColor(0xFF50C878.toInt())
            actionStatusBadge.background = badgeDrawable(0xFF1E2B22.toInt(), 0xFF50C878.toInt())
        } else {
            actionStatusBadge.text = "Waiting for Turn (${uiSnap.state.displayName})"
            actionStatusBadge.setTextColor(0xFFFFAA33.toInt())
            actionStatusBadge.background = badgeDrawable(0xFF2E2211.toInt(), 0xFFFFAA33.toInt())
        }
    }

    fun refreshUI() {
        val party = viewModel.playerParty.value
        val enemies = viewModel.enemyParty.value
        val selectedIdx = viewModel.selectedMemberIndex.value
        val inBattle = viewModel.isInBattle.value
        val activeEnemyIdx = viewModel.activeEnemyMemberIndex.value
        val profile = viewModel.activeProfile.value
        val playerStages = viewModel.playerStatStages.value
        val enemyStages = viewModel.enemyStatStages.value

        headerBadge.text = if (inBattle) "⚔️ Battle" else "Ready"
        headerBadge.setTextColor(if (inBattle) 0xFFFF6B6B.toInt() else 0xFF50C878.toInt())
        headerBadge.background = badgeDrawable(
            if (inBattle) 0xFF2E1A1A.toInt() else 0xFF1E2B22.toInt(),
            if (inBattle) 0xFFFF6B6B.toInt() else 0xFF50C878.toInt()
        )

        refreshActionStatusBadge()

        val attacker: ParsedPokemon? = party.getOrNull(selectedIdx)?.takeIf { !it.isEmpty && it.isValid }
        val defender: ParsedPokemon? = if (inBattle && activeEnemyIdx in enemies.indices) {
            enemies[activeEnemyIdx].takeIf { !it.isEmpty && it.isValid }
        } else {
            null
        }

        // Cache-aware move presentation calculation
        ensureMovePresentations(attacker, defender, profile, playerStages, enemyStages)

        contentContainer.removeAllViews()

        when (activeSubtab) {
            ConsoleSubtab.BATTLE -> renderBattleSubtab(attacker, defender, selectedIdx, activeEnemyIdx, inBattle, profile, playerStages, enemyStages)
            ConsoleSubtab.PARTY -> renderPartySubtab(party, selectedIdx, inBattle)
            ConsoleSubtab.FIELD -> renderFieldSubtab(attacker, defender, selectedIdx, inBattle, profile, playerStages, enemyStages)
            ConsoleSubtab.DETAILS -> renderDetailsSubtab(attacker, defender)
        }
    }

    private fun ensureMovePresentations(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        profile: RomHackProfile,
        playerStages: StatStages,
        enemyStages: StatStages
    ) {
        if (attacker == null || attacker.isEmpty) {
            calculationJob?.cancel()
            lastCachedMoves = emptyList()
            lastAttacker = null
            lastDefender = null
            lastProfile = null
            lastPlayerStages = null
            lastEnemyStages = null
            return
        }

        if (attacker == lastAttacker && defender == lastDefender && profile == lastProfile &&
            playerStages == lastPlayerStages && enemyStages == lastEnemyStages && lastCachedMoves.isNotEmpty()) {
            return
        }

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
                        calculator = damageCalculator,
                        attackerStages = playerStages,
                        defenderStages = enemyStages
                    )
                    list += pres
                }
                list
            }
            lastAttacker = attacker
            lastDefender = defender
            lastProfile = profile
            lastPlayerStages = playerStages
            lastEnemyStages = enemyStages
            lastCachedMoves = presentations
            if (activeSubtab == ConsoleSubtab.BATTLE || activeSubtab == ConsoleSubtab.DETAILS) {
                refreshUI()
            }
        }
    }

    // =========================================================================
    // SUBTAB 1: BATTLE
    // =========================================================================
    private fun renderBattleSubtab(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        selectedIdx: Int,
        activeEnemyIdx: Int,
        inBattle: Boolean,
        profile: RomHackProfile,
        playerStages: StatStages,
        enemyStages: StatStages
    ) {
        contentContainer.addView(sectionHeader("⚔️ Active Participants"))

        if (attacker == null && defender == null) {
            contentContainer.addView(emptyLabel("No battle participant data (Waiting for ROM)"))
            return
        }

        // Active participant summaries
        if (attacker != null) {
            val attackerSummary = ParticipantSummaryBuilder.build(attacker, selectedIdx, profile, playerStages)
            contentContainer.addView(participantCard(attackerSummary, "Your Pokémon", isPlayer = true))
        }

        if (inBattle && defender != null) {
            val opponentSummary = ParticipantSummaryBuilder.build(defender, activeEnemyIdx, profile, enemyStages)
            contentContainer.addView(participantCard(opponentSummary, "Opponent", isPlayer = false))
        }

        // Speed comparison banner
        if (attacker != null && defender != null) {
            val speedComp = SpeedComparison.calculate(
                playerBaseSpeed = attacker.speed,
                playerStages = playerStages,
                playerParalyzed = (attacker.statusCondition and (1L shl 6)) != 0L,
                enemyBaseSpeed = defender.speed,
                enemyStages = enemyStages,
                enemyParalyzed = (defender.statusCondition and (1L shl 6)) != 0L
            )
            contentContainer.addView(speedComparisonBanner(speedComp))
        }

        // 4-Move Interactive Grid
        contentContainer.addView(sectionHeader("🎮 Move Selection (Tap to Execute)"))
        if (lastCachedMoves.isEmpty()) {
            contentContainer.addView(emptyLabel("No moves available for current Pokémon."))
        } else {
            for (i in lastCachedMoves.indices) {
                val pres = lastCachedMoves[i]
                contentContainer.addView(interactiveMoveCard(pres, slot = i))
            }
        }
    }

    private fun speedComparisonBanner(speedComp: SpeedComparison): LinearLayout {
        return createCardLayout().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))

            val (icon, color) = when {
                speedComp.playerMovesFirst == true -> "⚡" to 0xFF50C878.toInt()
                speedComp.playerMovesFirst == false -> "🐢" to 0xFFFF8844.toInt()
                else -> "🤝" to 0xFF4A9EFF.toInt()
            }

            val badge = TextView(context).apply {
                text = icon
                textSize = 18f
                setPadding(0, 0, dp(10), 0)
            }
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val title = TextView(context).apply {
                    text = when {
                        speedComp.playerMovesFirst == true -> "Moves First"
                        speedComp.playerMovesFirst == false -> "Moves Second"
                        else -> "Speed Tie"
                    }
                    setTextColor(color)
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                }
                val exp = TextView(context).apply {
                    text = "${speedComp.explanation} (Effective: ${speedComp.playerEffectiveSpeed} vs ${speedComp.enemyEffectiveSpeed})"
                    setTextColor(0xFFCCCCCC.toInt())
                    textSize = 11f
                }
                addView(title)
                addView(exp)
            }
            addView(badge)
            addView(textCol)
        }
    }

    private fun interactiveMoveCard(pres: MovePresentation, slot: Int): LinearLayout {
        return createCardLayout().apply {
            isClickable = true
            isFocusable = true

            setOnClickListener {
                val uiSnap = viewModel.battleUiSnapshot.value
                if (!uiSnap.isInputAccepted) {
                    showFeedback("⚠️ Cannot select move: Waiting for player turn...")
                    return@setOnClickListener
                }

                showFeedback("🎮 Selecting ${pres.name}...")
                val scope = viewScope ?: CoroutineScope(Dispatchers.Main)
                scope.launch {
                    val ok = viewModel.battleInputAdapter.selectMove(slot, uiSnap)
                    if (!ok) {
                        showFeedback("❌ Move selection aborted")
                    }
                }
            }

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
            header.addView(TextView(context).apply {
                text = pres.categoryDisplay
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 11f
            })
            addView(header)

            val statsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(2))
            }
            statsRow.addView(TextView(context).apply {
                text = "BP: ${pres.powerDisplay}  ·  Acc: ${pres.accuracyDisplay}  ·  PP: ${pres.ppDisplay}"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            })
            addView(statsRow)

            val effColor = when (pres.effectivenessConfidence) {
                DataConfidence.VERIFIED -> 0xFF50C878.toInt()
                DataConfidence.ESTIMATE -> 0xFFFFAA33.toInt()
                DataConfidence.UNAVAILABLE -> 0xFF888899.toInt()
            }
            addView(fieldRow("Effectiveness", pres.effectiveness, effColor))

            if (pres.hasDamage || pres.damageConfidence != DamageConfidence.UNAVAILABLE) {
                val dmgColor = when (pres.damageConfidence) {
                    DamageConfidence.VERIFIED -> 0xFF50C878.toInt()
                    DamageConfidence.ESTIMATE -> 0xFFFFAA33.toInt()
                    DamageConfidence.UNAVAILABLE -> 0xFF888899.toInt()
                }
                addView(fieldRow("Damage", pres.damageDisplayText, dmgColor))
            }
        }
    }

    // =========================================================================
    // SUBTAB 2: PARTY
    // =========================================================================
    private fun renderPartySubtab(party: List<ParsedPokemon>, activeSlot: Int, inBattle: Boolean) {
        contentContainer.addView(sectionHeader("👥 Party Members (Tap [Switch In] to Switch)"))

        if (party.isEmpty()) {
            contentContainer.addView(emptyLabel("No party data detected."))
            return
        }

        for (i in party.indices) {
            val mon = party[i]
            if (mon.isEmpty || !mon.isValid) continue
            contentContainer.addView(partyMemberCard(mon, slot = i, activeSlot = activeSlot, inBattle = inBattle))
        }
    }

    private fun partyMemberCard(mon: ParsedPokemon, slot: Int, activeSlot: Int, inBattle: Boolean): LinearLayout {
        return createCardLayout().apply {
            val isActive = slot == activeSlot && inBattle
            val isFainted = mon.currentHp <= 0

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)

                val nameText = TextView(context).apply {
                    text = "${mon.nickname.trim().ifEmpty { "Mon #$slot" }} (Lv ${mon.level})"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                }
                val hpPercent = if (mon.maxHp > 0) (mon.currentHp * 100 / mon.maxHp) else 0
                val hpColor = when {
                    hpPercent > 50 -> 0xFF50C878.toInt()
                    hpPercent > 20 -> 0xFFFFAA33.toInt()
                    else -> 0xFFFF4444.toInt()
                }
                val hpText = TextView(context).apply {
                    text = "HP: ${mon.currentHp}/${mon.maxHp} ($hpPercent%)"
                    setTextColor(hpColor)
                    textSize = 12f
                }
                addView(nameText)
                addView(hpText)
            }
            topRow.addView(nameCol)

            val actionWidget = TextView(context).apply {
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))

                when {
                    isActive -> {
                        text = "ACTIVE"
                        setTextColor(0xFF4A9EFF.toInt())
                        background = badgeDrawable(0xFF1E2838.toInt(), 0xFF4A9EFF.toInt())
                    }
                    isFainted -> {
                        text = "FAINTED"
                        setTextColor(0xFFFF4444.toInt())
                        background = badgeDrawable(0xFF331616.toInt(), 0xFFFF4444.toInt())
                    }
                    inBattle -> {
                        text = "SWITCH IN"
                        setTextColor(0xFF50C878.toInt())
                        background = badgeDrawable(0xFF1E3824.toInt(), 0xFF50C878.toInt())
                        isClickable = true
                        setOnClickListener {
                            val uiSnap = viewModel.battleUiSnapshot.value
                            if (!uiSnap.isInputAccepted) {
                                showFeedback("⚠️ Cannot switch: Waiting for player turn...")
                                return@setOnClickListener
                            }
                            showFeedback("🎮 Switching to ${mon.nickname}...")
                            val scope = viewScope ?: CoroutineScope(Dispatchers.Main)
                            scope.launch {
                                val ok = viewModel.battleInputAdapter.switchPokemon(
                                    targetSlot = slot,
                                    currentUi = uiSnap,
                                    party = viewModel.playerParty.value,
                                    activeSlot = activeSlot
                                )
                                if (!ok) {
                                    showFeedback("❌ Switch aborted (illegal or blocked)")
                                }
                            }
                        }
                    }
                    else -> {
                        text = "READY"
                        setTextColor(0xFF888899.toInt())
                        background = badgeDrawable(0xFF1E1E26.toInt(), 0xFF888899.toInt())
                    }
                }
            }
            topRow.addView(actionWidget)
            addView(topRow)

            val statusCondition = StatusConditionDecoder.decode(mon.statusCondition)
            if (statusCondition != StatusCondition.HEALTHY) {
                addView(TextView(context).apply {
                    text = "Status: ${statusCondition.displayName}"
                    setTextColor(0xFFFFAA33.toInt())
                    textSize = 11f
                    setPadding(0, dp(4), 0, 0)
                })
            }
        }
    }

    // =========================================================================
    // SUBTAB 3: FIELD
    // =========================================================================
    private fun renderFieldSubtab(
        attacker: ParsedPokemon?,
        defender: ParsedPokemon?,
        selectedIdx: Int,
        inBattle: Boolean,
        profile: RomHackProfile,
        playerStages: StatStages,
        enemyStages: StatStages
    ) {
        contentContainer.addView(sectionHeader("🌄 Stat Stages (-6 .. +6)"))
        contentContainer.addView(statStagesTable(playerStages, enemyStages, attacker?.nickname, defender?.nickname))

        contentContainer.addView(sectionHeader("🌐 Field Conditions"))
        val fieldData = FieldStatusBuilder.build(
            inBattle = inBattle,
            attacker = attacker,
            defender = defender,
            attackerSlot = selectedIdx,
            profile = profile,
            playerStages = playerStages,
            enemyStages = enemyStages
        )

        val card = createCardLayout().apply {
            addView(fieldRow("Battle state", if (fieldData.inBattle) "⚔️ In battle" else "Ready"))
            addView(fieldRow("Weather", fieldData.weather.displayName))
            addView(fieldRow("Condition", fieldData.condition.displayName))
            addView(fieldRow("Condition verified", yesNo(fieldData.conditionVerified)))
            val effNotes = if (fieldData.effectivenessNotes.isEmpty()) "All moves neutral or verified"
            else fieldData.effectivenessNotes.joinToString(" · ")
            addView(fieldRow("Effectiveness notes", effNotes))
            if (fieldData.damageNotes.isNotEmpty()) {
                addView(fieldRow("Damage notes", fieldData.damageNotes.joinToString(" · ")))
            }
            fieldData.speedComparison?.let {
                addView(fieldRow("Speed comparison", it.explanation))
            }
        }
        contentContainer.addView(card)
    }

    private fun statStagesTable(
        playerStages: StatStages,
        enemyStages: StatStages,
        playerName: String?,
        enemyName: String?
    ): LinearLayout {
        return createCardLayout().apply {
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(6))
            }
            val pName = playerName?.trim()?.ifEmpty { "You" } ?: "You"
            val eName = enemyName?.trim()?.ifEmpty { "Opponent" } ?: "Opponent"

            headerRow.addView(tableCell("Stat", 0xFF4A9EFF.toInt(), weight = 1.0f, bold = true))
            headerRow.addView(tableCell(pName, 0xFF50C878.toInt(), weight = 1.2f, bold = true))
            headerRow.addView(tableCell(eName, 0xFFFF8844.toInt(), weight = 1.2f, bold = true))
            addView(headerRow)

            val stats = listOf(
                "Attack" to (playerStages.atk to enemyStages.atk),
                "Defense" to (playerStages.def to enemyStages.def),
                "Speed" to (playerStages.spe to enemyStages.spe),
                "Sp. Atk" to (playerStages.spa to enemyStages.spa),
                "Sp. Def" to (playerStages.spd to enemyStages.spd),
                "Accuracy" to (playerStages.acc to enemyStages.acc),
                "Evasion" to (playerStages.eva to enemyStages.eva)
            )

            for ((name, pair) in stats) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(3), 0, dp(3))
                }
                val pMult = if (name == "Accuracy" || name == "Evasion") {
                    StatStages.accuracyMultiplier(pair.first)
                } else {
                    StatStages.statMultiplier(pair.first)
                }
                val eMult = if (name == "Accuracy" || name == "Evasion") {
                    StatStages.accuracyMultiplier(pair.second)
                } else {
                    StatStages.statMultiplier(pair.second)
                }

                val pDisplay = "${StatStages.formatStage(pair.first)} (${String.format("%.2f", pMult)}x)"
                val eDisplay = "${StatStages.formatStage(pair.second)} (${String.format("%.2f", eMult)}x)"

                val pColor = when {
                    pair.first > 0 -> 0xFF50C878.toInt()
                    pair.first < 0 -> 0xFFFF6B6B.toInt()
                    else -> 0xFFCCCCCC.toInt()
                }
                val eColor = when {
                    pair.second > 0 -> 0xFF50C878.toInt()
                    pair.second < 0 -> 0xFFFF6B6B.toInt()
                    else -> 0xFFCCCCCC.toInt()
                }

                row.addView(tableCell(name, 0xFFFFFFFF.toInt(), weight = 1.0f))
                row.addView(tableCell(pDisplay, pColor, weight = 1.2f))
                row.addView(tableCell(eDisplay, eColor, weight = 1.2f))
                addView(row)
            }
        }
    }

    private fun tableCell(text: String, color: Int, weight: Float, bold: Boolean = false): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = 11.5f
            if (bold) typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }
    }

    // =========================================================================
    // SUBTAB 4: DETAILS
    // =========================================================================
    private fun renderDetailsSubtab(attacker: ParsedPokemon?, defender: ParsedPokemon?) {
        contentContainer.addView(sectionHeader("📋 Move Calculation Details"))

        if (attacker == null || attacker.isEmpty) {
            contentContainer.addView(emptyLabel("No battle participant data."))
            return
        }

        if (defender == null) {
            contentContainer.addView(emptyLabel("No opponent data. Damage predictions unavailable."))
        }

        if (lastCachedMoves.isEmpty()) {
            contentContainer.addView(emptyLabel("No usable moves detected."))
            return
        }

        for (pres in lastCachedMoves) {
            contentContainer.addView(moveDetailCard(pres))
        }
    }

    private fun participantCard(summary: BattleParticipantSummary, role: String, isPlayer: Boolean): LinearLayout {
        return createCardLayout().apply {
            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleText = TextView(context).apply {
                text = "$role · ${summary.displayName}"
                setTextColor(if (isPlayer) 0xFF50C878.toInt() else 0xFFFF8844.toInt())
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            val speedChip = TextView(context).apply {
                text = "⚡ Spe ${summary.effectiveSpeed}"
                setTextColor(0xFF4A9EFF.toInt())
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            }
            titleRow.addView(titleText)
            titleRow.addView(speedChip)
            addView(titleRow)

            addView(TextView(context).apply {
                text = "Species: ${summary.speciesName}  ·  Lv ${summary.level}  ·  HP ${summary.hpDisplay}"
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

            // Stat stage chips (e.g. +1 SpA, -1 Def)
            val chips = summary.statStages.activeStageChips()
            if (chips.isNotEmpty()) {
                val chipRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(4), 0, dp(2))
                }
                for ((stat, stage) in chips) {
                    val sign = if (stage > 0) "+$stage" else "$stage"
                    val col = if (stage > 0) 0xFF50C878.toInt() else 0xFFFF6B6B.toInt()
                    chipRow.addView(TextView(context).apply {
                        text = "$sign $stat"
                        setTextColor(col)
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = badgeDrawable(if (stage > 0) 0xFF1E2B22.toInt() else 0xFF2E1A1A.toInt(), col)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, dp(4), 0)
                        }
                    })
                }
                addView(chipRow)
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

    private fun sectionHeader(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFF4A9EFF.toInt())
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFF1E1E26.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(10))
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
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(colorHex)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dp(6), 0)
            }
        }
    }

    private fun badgeDrawable(bgColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(bgColor)
            setStroke(1, strokeColor)
        }
    }

    private fun emptyLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFF888888.toInt())
        textSize = 13f
        setPadding(dp(4), dp(6), dp(4), dp(6))
    }

    private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
}
