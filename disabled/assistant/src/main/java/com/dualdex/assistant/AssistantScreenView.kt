package com.dualdex.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel
import com.dualdex.companion.ui.DualDexButtonStyle
import com.dualdex.companion.ui.DualDexComponents
import com.dualdex.companion.ui.DualDexTheme
import com.dualdex.companion.ui.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Redesigned Assistant companion screen adhering to the Quiet Handheld Companion design system.
 * Delivers focused walkthrough grounding and game Q&A with clean message bubbles, density-independent
 * spacing, resilient CoroutineScope lifecycle, and zero promotional chrome.
 */
class AssistantScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : LinearLayout(context) {

    private var viewScope: CoroutineScope? = null
    private var isSending = false
    private var thinkingCard: View? = null

    private val messagesContainer: LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.standard))
    }
    private val scroll: ScrollView = ScrollView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)
        isVerticalScrollBarEnabled = true
    }
    private val queryInput: EditText = DualDexComponents.styledInput(context, "Ask a question about this game...").apply {
        layoutParams = LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1.0f)
    }
    private val askButton: TextView = DualDexComponents.primaryButton(context, "Ask").apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
            marginStart = context.dp(DualDexTheme.Spacing.compact)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)
        setPadding(
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.compact)
        )

        // 1. Header
        val headerBar = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
        }

        val titleView = DualDexComponents.screenTitle(context, "Assistant")
        headerBar.addView(titleView)

        val activeGame = viewModel.activeProfile.value.name
        val subTitle = TextView(context).apply {
            text = if (activeGame.isNotBlank()) "Guidance and grounding for $activeGame" else "Game walkthrough and ROM hack guidance."
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            setPadding(0, context.dp(DualDexTheme.Spacing.tight / 2), 0, 0)
        }
        headerBar.addView(subTitle)
        addView(headerBar)

        // 2. Quick Suggestion Chips
        val chipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
        }
        val chipsRow = LinearLayout(context).apply { orientation = HORIZONTAL }

        listOf(
            "Evolution changes",
            "Gym leader teams",
            "Type effectiveness",
            "Item locations",
            "Where is Fly?"
        ).forEachIndexed { index, chipText ->
            val chip = DualDexComponents.smallButton(context, chipText, DualDexButtonStyle.SECONDARY) {
                queryInput.setText(chipText)
                submitQuery(chipText)
            }.apply {
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(DualDexTheme.Spacing.touchTarget)).apply {
                    if (index > 0) marginStart = context.dp(DualDexTheme.Spacing.compact)
                }
                layoutParams = lp
            }
            chipsRow.addView(chip)
        }
        chipsScroll.addView(chipsRow)
        addView(chipsScroll)

        // 3. Messages Scroll Area
        scroll.addView(messagesContainer)
        addView(scroll)

        // 4. Bottom Input Area
        val inputRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, 0)
        }

        inputRow.addView(queryInput)

        askButton.setOnClickListener {
            val q = queryInput.text.toString().trim()
            if (q.isNotEmpty()) {
                submitQuery(q)
            }
        }
        inputRow.addView(askButton)
        addView(inputRow)

        // Initial welcome message
        val welcomeText = if (activeGame.isNotBlank()) {
            "Ready for questions about $activeGame.\n\nAsk about item locations, gym movesets, evolution methods, or custom hack mechanics."
        } else {
            "Welcome to DualDex Assistant.\n\nOpen a game to receive walkthrough grounding, evolution details, and item locations."
        }
        addAssistantMessage(
            text = welcomeText,
            citations = emptyList(),
            queries = emptyList()
        )
    }

    private fun submitQuery(question: String) {
        if (isSending) return
        val scope = viewScope ?: return
        isSending = true
        addUserMessage(question)
        queryInput.setText("")
        askButton.isEnabled = false
        askButton.text = "Thinking..."

        val card = addAssistantMessage("Searching game documentation...", emptyList(), emptyList())
        thinkingCard = card

        scope.launch {
            try {
                val res = RomHackAssistant.askQuestion(context, question, viewModel)
                if (isActive && isAttachedToWindow) {
                    messagesContainer.removeView(card)
                    addAssistantMessage(res.text, res.citations, res.searchQueries, res.isOfflineFallback)
                }
            } catch (e: Exception) {
                if (isActive && isAttachedToWindow) {
                    messagesContainer.removeView(card)
                    addAssistantMessage("Unable to generate answer: ${e.message}", emptyList(), emptyList())
                }
            } finally {
                isSending = false
                thinkingCard = null
                if (isAttachedToWindow) {
                    askButton.isEnabled = true
                    askButton.text = "Ask"
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
        }
    }

    private fun addUserMessage(text: String) {
        val userCard = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.compact)
            )
            gravity = Gravity.END
            background = DualDexComponents.controlBackground(context, DualDexButtonStyle.SECONDARY, selected = true)
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                this.gravity = Gravity.END
                setMargins(context.dp(48), context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.tight))
            }
            layoutParams = lp
        }

        val msgView = TextView(context).apply {
            this.text = text
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
        }
        userCard.addView(msgView)
        messagesContainer.addView(userCard)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addAssistantMessage(
        text: String,
        citations: List<WebCitation>,
        queries: List<String>,
        isOffline: Boolean = false
    ): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.standard)
            )
            background = DualDexComponents.surface(context, elevated = false)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, context.dp(DualDexTheme.Spacing.tight), context.dp(DualDexTheme.Spacing.section), context.dp(DualDexTheme.Spacing.tight))
            }
            layoutParams = lp
        }

        // Queries executed banner
        if (queries.isNotEmpty()) {
            val qText = TextView(context).apply {
                this.text = "Searched: " + queries.joinToString(", ")
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.compact
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.tight))
            }
            card.addView(qText)
        }

        // Body
        val msgView = TextView(context).apply {
            this.text = text
            setTextColor(DualDexTheme.Color.textPrimary)
            textSize = DualDexTheme.Type.body
            setLineSpacing(4f, 1f)
        }
        card.addView(msgView)

        // Web Grounding Citations
        if (citations.isNotEmpty()) {
            val citeHeader = TextView(context).apply {
                this.text = "Sources:"
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.compact
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, context.dp(DualDexTheme.Spacing.compact), 0, context.dp(DualDexTheme.Spacing.tight / 2))
            }
            card.addView(citeHeader)

            citations.forEach { citation ->
                val citeBtn = TextView(context).apply {
                    this.text = "[Source] ${citation.title}"
                    setTextColor(DualDexTheme.Color.accent)
                    textSize = DualDexTheme.Type.compact
                    setPadding(0, context.dp(DualDexTheme.Spacing.tight / 2), 0, context.dp(DualDexTheme.Spacing.tight / 2))
                    setOnClickListener {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(citation.url))
                            context.startActivity(browserIntent)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                card.addView(citeBtn)
            }
        }

        if (isOffline) {
            val badge = TextView(context).apply {
                this.text = "Offline knowledge base"
                setTextColor(DualDexTheme.Color.textDisabled)
                textSize = DualDexTheme.Type.compact
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, 0)
            }
            card.addView(badge)
        }

        messagesContainer.addView(card)
        return card
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A cancelled request may have completed after the previous detach. Always make the
        // reusable view actionable when it is shown again.
        isSending = false
        thinkingCard?.let { messagesContainer.removeView(it) }
        thinkingCard = null
        askButton.isEnabled = true
        askButton.text = "Ask"
        viewScope?.cancel()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        thinkingCard?.let { messagesContainer.removeView(it) }
        thinkingCard = null
        isSending = false
        askButton.isEnabled = true
        askButton.text = "Ask"
        viewScope?.cancel()
        viewScope = null
    }
}
