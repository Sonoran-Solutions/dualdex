package com.dualdex.companion.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import com.dualdex.retroachievements.RaAccountStatus
import com.dualdex.retroachievements.RaAchievement
import com.dualdex.retroachievements.RaGameStatus
import com.dualdex.retroachievements.RaHardcorePolicy
import com.dualdex.retroachievements.RaSessionState
import com.dualdex.retroachievements.RetroAchievementsService
import kotlin.math.roundToInt

/** Account controls and the game list share the companion's scrollable view. */
class AchievementsScreenView(
    context: Context,
    private val service: RetroAchievementsService,
    onBack: () -> Unit
) : LinearLayout(context) {
    private val username = field("RetroAchievements username", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    private val password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
    private val signIn = DualDexComponents.primaryButton(context, "Sign in", ::submit)
    private val signOut = DualDexComponents.secondaryButton(context, "Sign out", service::signOut)
    private val retry = DualDexComponents.secondaryButton(context, "Retry saved sign-in", service::retrySignIn)
    private val accountText = label("")
    private val gameText = label("")
    private val notice = label("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private val leaderboard = label("")
    private var rendering = false
    private val hardcore = setting("Hardcore", service::setHardcore)
    private val banners = setting("Show companion unlock banners", service::setBanners)
    private val sound = setting("Play an unlock sound", service::setUnlockSound)
    private val achievements = AchievementAdapter()

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)
        password.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        password.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_GO) false else {
                submit()
                true
            }
        }

        val header = LinearLayout(context).apply {
            orientation = VERTICAL
            val inset = context.dp(DualDexTheme.Spacing.section)
            setPadding(inset, inset, inset, inset)
            addView(DualDexComponents.ghostControl(context, "‹ More", onBack), fullRow())
            addView(label("Achievements", DualDexTheme.Type.screenTitle).apply {
                typeface = Typeface.DEFAULT_BOLD
                isAccessibilityHeading = true
            })
            addView(accountText)
            addView(username, fullRow())
            addView(password, fullRow())
            addView(signIn, fullRow())
            addView(retry, fullRow())
            addView(signOut, fullRow())
            addView(hardcore, fullRow())
            addView(label(RaHardcorePolicy.SUMMARY))
            addView(label("Enabling Hardcore restarts a supported game once its achievement set loads."))
            addView(banners, fullRow())
            addView(sound, fullRow())
            addView(gameText)
            addView(notice)
            addView(leaderboard)
        }

        addView(ListView(context).apply {
            divider = null
            itemsCanFocus = true
            addHeaderView(header, null, false)
            adapter = achievements
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        render(service.state.value)
    }

    fun render(state: RaSessionState) {
        rendering = true
        val signedOut = state.account == RaAccountStatus.SIGNED_OUT
        username.visibility = if (signedOut) View.VISIBLE else View.GONE
        password.visibility = username.visibility
        signIn.visibility = username.visibility
        retry.visibility = if (state.canRetrySignIn) View.VISIBLE else View.GONE
        signOut.visibility = if (!signedOut || state.canRetrySignIn) View.VISIBLE else View.GONE
        accountText.text = when (state.account) {
            RaAccountStatus.SIGNED_OUT -> "Sign in to RetroAchievements"
            RaAccountStatus.SIGNING_IN -> "Signing in…"
            RaAccountStatus.SIGNED_IN -> "Signed in as ${state.username}"
        }
        hardcore.isChecked = state.hardcoreRequested
        banners.isChecked = state.showBanners
        sound.isChecked = state.playUnlockSound
        val mode = if (state.hardcorePolicy.isHardcoreActive) "Hardcore" else "Softcore"
        gameText.text = when (state.game) {
            RaGameStatus.NONE -> if (state.account == RaAccountStatus.SIGNED_IN) "Open a GBA game to check for achievements." else ""
            RaGameStatus.LOADING -> "Looking up achievement set…"
            RaGameStatus.ACTIVE -> "${state.gameTitle.orEmpty()} · $mode"
            RaGameStatus.NO_SET -> "No RetroAchievements set for this ROM."
            RaGameStatus.UNAVAILABLE -> "Achievement set unavailable. Retrying…"
        }
        gameText.visibility = if (gameText.text.isEmpty()) View.GONE else View.VISIBLE
        notice.text = state.message ?: if (state.offline) "Offline. Pending submissions will retry automatically." else ""
        notice.visibility = if (notice.text.isEmpty()) View.GONE else View.VISIBLE
        leaderboard.text = state.attempts.joinToString("\n") { "${it.title} · ${it.value}" }
        leaderboard.visibility = if (state.attempts.isEmpty()) View.GONE else View.VISIBLE
        achievements.update(state.achievements)
        rendering = false
    }

    private fun submit() {
        val name = username.text.toString()
        if (name.isBlank() || password.text.isEmpty()) {
            val message = if (name.isBlank()) "Enter your username" else "Enter your password"
            notice.text = message
            notice.visibility = View.VISIBLE
            notice.announceForAccessibility(message)
            return
        }
        val secret = password.text.toString().encodeToByteArray()
        password.text.clear()
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
        service.signIn(name, secret)
    }

    override fun onDetachedFromWindow() {
        password.text.clear()
        super.onDetachedFromWindow()
    }

    private fun field(prompt: String, type: Int) = EditText(context).apply {
        hint = prompt
        inputType = type
        setSingleLine(true)
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        setTextColor(DualDexTheme.Color.textPrimary)
        setHintTextColor(DualDexTheme.Color.textSecondary)
    }

    private fun setting(title: String, changed: (Boolean) -> Unit) = Switch(context).apply {
        text = title
        minHeight = context.dp(DualDexTheme.Spacing.touchTarget)
        setTextColor(DualDexTheme.Color.textPrimary)
        setOnCheckedChangeListener { _, checked -> if (!rendering) changed(checked) }
    }

    private fun label(value: CharSequence, size: Float = DualDexTheme.Type.body) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(DualDexTheme.Color.textPrimary)
        setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.tight))
    }

    private fun fullRow() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = context.dp(DualDexTheme.Spacing.tight)
        bottomMargin = context.dp(DualDexTheme.Spacing.tight)
    }

    private sealed interface Row {
        data class Group(val text: String) : Row
        data class Entry(val achievement: RaAchievement) : Row
    }

    private inner class AchievementAdapter : BaseAdapter() {
        private var previous = emptyList<RaAchievement>()
        private var rows = emptyList<Row>()

        fun update(list: List<RaAchievement>) {
            if (previous == list) return
            previous = list
            rows = if (list.isEmpty()) emptyList() else buildList {
                val (unlocked, locked) = list.partition { it.unlocked }
                add(Row.Group("Unlocked (${unlocked.size})"))
                unlocked.forEach { add(Row.Entry(it)) }
                add(Row.Group("Locked (${locked.size})"))
                locked.forEach { add(Row.Entry(it)) }
            }
            notifyDataSetChanged()
        }

        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = when (val row = rows[position]) {
            is Row.Group -> if (position == 0) -2L else -1L
            is Row.Entry -> row.achievement.id
        }
        override fun hasStableIds() = true
        override fun isEnabled(position: Int) = false
        override fun areAllItemsEnabled() = false
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Group) 0 else 1

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = when (val row = rows[position]) {
            is Row.Group -> (convertView as? TextView ?: label("").apply {
                typeface = Typeface.DEFAULT_BOLD
                isAccessibilityHeading = true
                val inset = context.dp(DualDexTheme.Spacing.section)
                setPadding(inset, inset, inset, context.dp(DualDexTheme.Spacing.compact))
            }).apply { text = row.text }
            is Row.Entry -> (convertView as? AchievementRow ?: AchievementRow()).apply { bind(row.achievement) }
        }
    }

    private inner class AchievementRow : LinearLayout(context) {
        private val title = label("").apply { typeface = Typeface.DEFAULT_BOLD }
        private val description = label("").apply { setTextColor(DualDexTheme.Color.textSecondary) }
        private val missable = label("MISSABLE", DualDexTheme.Type.compact).apply {
            val inset = context.dp(DualDexTheme.Spacing.compact)
            setPadding(inset, inset / 2, inset, inset / 2)
            setTextColor(DualDexTheme.Color.warning)
            background = GradientDrawable().apply {
                cornerRadius = context.dp(12).toFloat()
                setColor(DualDexTheme.Color.elevatedSurface)
            }
        }
        private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(DualDexTheme.Color.accent)
        }

        init {
            orientation = VERTICAL
            val inset = context.dp(DualDexTheme.Spacing.section)
            setPadding(inset, 0, inset, context.dp(DualDexTheme.Spacing.compact))
            addView(title)
            addView(description)
            addView(missable, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(progress, fullRow())
        }

        fun bind(achievement: RaAchievement) {
            title.text = "${achievement.title} · ${achievement.points} pts"
            description.text = achievement.description
            missable.visibility = if (achievement.isMissable) View.VISIBLE else View.GONE
            val measured = achievement.measuredProgress?.roundToInt()
            progress.visibility = if (measured != null && !achievement.unlocked) View.VISIBLE else View.GONE
            progress.progress = measured ?: 0
            progress.contentDescription = measured?.let { "$it percent complete" }
        }
    }
}
