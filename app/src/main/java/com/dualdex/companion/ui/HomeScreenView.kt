package com.dualdex.companion.ui

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dualdex.companion.CompanionViewModel
import com.dualdex.companion.RomItem
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The ROM library. Scan-time ROM metadata intentionally stays conservative and never infers trust. */
class HomeScreenView(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val onChooseRomsFolderRequested: (() -> Unit)? = null,
    private val onRefreshRomsRequested: (() -> Unit)? = null,
    private val onPlayRomRequested: ((Uri, String) -> Unit)? = null,
    private val onOpenRomRequested: (() -> Unit)? = null
) : LinearLayout(context) {

    private val settingsManager = SettingsManager(context)
    private var viewScope: CoroutineScope? = null
    private val romsContainer: LinearLayout
    private val folderStatusText: TextView
    private val resumeCard: LinearLayout
    private val resumeGameLabel: TextView
    private val searchInput: EditText
    private var allRoms: List<RomItem> = emptyList()

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)
        setPadding(
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section),
            context.dp(DualDexTheme.Spacing.section),
            0
        )

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        scroll.addView(content)
        addView(scroll)

        content.addView(DualDexComponents.screenTitle(context, "Library"), LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.section) })

        resumeCard = LinearLayout(context).apply {
            orientation = VERTICAL
            background = DualDexComponents.surface(context, elevated = true)
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.standard),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.standard)
            )
            addView(DualDexComponents.sectionTitle(context, "Continue"))
            resumeGameLabel = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.body
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.standard))
            }
            addView(resumeGameLabel)
            addView(DualDexComponents.primaryButton(context, "Resume") {
                val uri = settingsManager.lastPlayedRomUri
                if (!uri.isNullOrBlank()) {
                    onPlayRomRequested?.invoke(Uri.parse(uri), settingsManager.lastPlayedRomTitle ?: "Game")
                } else {
                    Toast.makeText(context, "No previous game recorded", Toast.LENGTH_SHORT).show()
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(DualDexTheme.Spacing.touchTarget)))
        }
        content.addView(resumeCard, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.section) })

        val controls = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(DualDexComponents.secondaryButton(context, "Open ROM File") { onOpenRomRequested?.invoke() },
                LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                })
            addView(DualDexComponents.ghostControl(context, "ROM Folder") { onChooseRomsFolderRequested?.invoke() },
                LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                    marginEnd = context.dp(DualDexTheme.Spacing.compact)
                })
            addView(DualDexComponents.ghostControl(context, "Refresh") { onRefreshRomsRequested?.invoke() },
                LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f))
        }
        content.addView(controls)

        folderStatusText = TextView(context).apply {
            setTextColor(DualDexTheme.Color.textSecondary)
            textSize = DualDexTheme.Type.meta
            setPadding(0, context.dp(DualDexTheme.Spacing.tight), 0, context.dp(DualDexTheme.Spacing.section))
        }
        content.addView(folderStatusText)

        searchInput = DualDexComponents.styledInput(context, "Search games").apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                    filterRoms(s?.toString().orEmpty())
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        content.addView(searchInput, LayoutParams(
            LayoutParams.MATCH_PARENT,
            context.dp(DualDexTheme.Spacing.touchTarget)
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.section) })

        content.addView(DualDexComponents.sectionTitle(context, "Games"), LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = context.dp(DualDexTheme.Spacing.compact) })

        romsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.major))
        }
        content.addView(romsContainer)

        updateResumeCard()
        updateFolderStatus()
    }

    fun updateResumeCard() {
        val lastTitle = settingsManager.lastPlayedRomTitle
        val lastUri = settingsManager.lastPlayedRomUri
        if (!lastUri.isNullOrBlank() && !lastTitle.isNullOrBlank()) {
            resumeCard.visibility = View.VISIBLE
            resumeGameLabel.text = lastTitle
        } else {
            resumeCard.visibility = View.GONE
        }
    }

    fun updateFolderStatus() {
        folderStatusText.text = if (settingsManager.romsFolderUri != null) {
            "ROM folder configured · ${allRoms.size} game${if (allRoms.size == 1) "" else "s"} found"
        } else {
            "Choose a ROM folder to add games to your library."
        }
    }

    private fun filterRoms(query: String) {
        romsContainer.removeAllViews()
        val filtered = if (query.isBlank()) allRoms else allRoms.filter {
            it.title.contains(query, ignoreCase = true) || it.fileName.contains(query, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            val detail = if (allRoms.isEmpty()) {
                "Choose a ROM folder or open a ROM file to get started."
            } else {
                "Try a different search."
            }
            romsContainer.addView(DualDexComponents.emptyState(context, "No games found", detail))
            return
        }

        filtered.forEachIndexed { index, rom ->
            romsContainer.addView(DualDexComponents.menuRow(
                context = context,
                title = rom.title,
                subtitle = "${rom.fileName} · ${rom.sizeFormatted}",
                onClick = { onPlayRomRequested?.invoke(rom.uri, rom.title) }
            ))
            if (index < filtered.lastIndex) {
                romsContainer.addView(DualDexComponents.divider(context), LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    context.dp(1)
                ).apply {
                    marginStart = context.dp(DualDexTheme.Spacing.standard)
                    marginEnd = context.dp(DualDexTheme.Spacing.standard)
                })
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { scope ->
            scope.launch {
                viewModel.scannedRoms.collectLatest { roms ->
                    allRoms = roms
                    filterRoms(searchInput.text.toString())
                    updateFolderStatus()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }
}
