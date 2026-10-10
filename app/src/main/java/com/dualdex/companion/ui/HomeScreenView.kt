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
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.SaveStateManager
import com.dualdex.library.AppUpdater
import com.dualdex.library.CoverArt
import com.dualdex.library.LibraryPrefs
import com.dualdex.library.RomInfo
import com.dualdex.romhack.RomCompatibilityMessages
import com.dualdex.romhack.RomCompatibilityStatus
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.widget.ImageView
import kotlinx.coroutines.withContext
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
    private val libraryPrefs = LibraryPrefs(context)
    private var showHidden = false

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

        val libraryControls = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(DualDexComponents.ghostControl(context, "Show Hidden") {
                showHidden = !showHidden
                refilter()
            }, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.compact)
            })
            addView(DualDexComponents.ghostControl(context, "Check for Updates") {
                AppUpdater.check(context, userInitiated = true)
            }, LayoutParams(0, context.dp(DualDexTheme.Spacing.touchTarget), 1f))
        }
        content.addView(libraryControls, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply { topMargin = context.dp(DualDexTheme.Spacing.compact) })

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

    private fun refilter() = filterRoms(searchInput.text.toString())

    private fun displayTitle(rom: RomItem) = libraryPrefs.customName(rom.uri.toString()) ?: rom.title

    private fun filterRoms(query: String) {
        romsContainer.removeAllViews()
        val hidden = libraryPrefs.hidden()
        val visible = if (showHidden) allRoms else allRoms.filter { it.uri.toString() !in hidden }
        val filtered = if (query.isBlank()) visible else visible.filter {
            displayTitle(it).contains(query, ignoreCase = true) || it.fileName.contains(query, ignoreCase = true)
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
            val title = displayTitle(rom)
            val badge = rom.status?.let { RomCompatibilityMessages.badge(it) } ?: "Not inspected"
            val hiddenNote = if (rom.uri.toString() in hidden) " · hidden" else ""
            val row = DualDexComponents.menuRow(
                context = context,
                title = title,
                subtitle = "${rom.fileName} · ${rom.sizeFormatted} · $badge$hiddenNote",
                onClick = { requestPlay(rom) }
            )
            row.setOnLongClickListener { showRomActions(rom); true }
            row.contentDescription = "$title. Long-press for info and options."
            if (rom.sha256.isNotEmpty()) {
                val cover = CoverArt.file(context, rom.sha256)
                if (cover.exists()) {
                    BitmapFactory.decodeFile(cover.absolutePath)?.let { bmp ->
                        row.addView(ImageView(context).apply {
                            setImageBitmap(bmp)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = null
                        }, 0, LayoutParams(context.dp(40), context.dp(40)).apply {
                            marginEnd = context.dp(DualDexTheme.Spacing.compact)
                        })
                    }
                }
            }
            romsContainer.addView(row)
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

    /** Unsupported (or uninspectable) ROMs need a one-time "add anyway?" confirmation. */
    private fun requestPlay(rom: RomItem) {
        val title = displayTitle(rom)
        val supported = rom.status != null && rom.status != RomCompatibilityStatus.UNSUPPORTED
        if (supported || (rom.sha256.isNotEmpty() && libraryPrefs.isUnsupportedAccepted(rom.sha256))) {
            onPlayRomRequested?.invoke(rom.uri, title)
            return
        }
        dialog { b ->
            b.setTitle("Add anyway?")
                .setMessage("DualDex doesn't recognize \"${rom.fileName}\". It will run as plain GBA emulation with no companion data.")
                .setPositiveButton("Play anyway") { _, _ ->
                    if (rom.sha256.isNotEmpty()) libraryPrefs.acceptUnsupported(rom.sha256)
                    onPlayRomRequested?.invoke(rom.uri, title)
                }
                .setNegativeButton("Cancel", null)
        }
    }

    private fun showRomActions(rom: RomItem) {
        val key = rom.uri.toString()
        val hidden = libraryPrefs.isHidden(key)
        val actions = mutableListOf<Pair<String, () -> Unit>>(
            "Info" to { showRomInfo(rom) },
            "Rename" to { showRename(rom) },
            (if (hidden) "Unhide" else "Hide") to {
                libraryPrefs.setHidden(key, !hidden)
                filterRoms(searchInput.text.toString())
            }
        )
        if (rom.sha256.isNotEmpty()) {
            if (CoverArt.pickRequest != null) actions += "Set cover" to { CoverArt.pickRequest?.invoke(rom.sha256) }
            actions += "Fetch icon (SteamGridDB)" to { fetchIcon(rom) }
        }
        dialog { b ->
            b.setTitle(displayTitle(rom)).setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
        }
    }

    private fun showRename(rom: RomItem) {
        val input = DualDexComponents.styledInput(context, rom.title).apply { setText(displayTitle(rom)) }
        dialog { b ->
            b.setTitle("Rename").setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString().trim()
                    libraryPrefs.setCustomName(rom.uri.toString(), name.takeIf { it != rom.title })
                    filterRoms(searchInput.text.toString())
                }
                .setNeutralButton("Reset") { _, _ ->
                    libraryPrefs.setCustomName(rom.uri.toString(), null)
                    filterRoms(searchInput.text.toString())
                }
                .setNegativeButton("Cancel", null)
        }
    }

    private fun fetchIcon(rom: RomItem) {
        val key = libraryPrefs.steamGridDbKey
        if (key.isNullOrBlank()) {
            val input = DualDexComponents.styledInput(context, "SteamGridDB API key")
            dialog { b ->
                b.setTitle("SteamGridDB key")
                    .setMessage("Optional. Your own key from steamgriddb.com/profile/preferences/api. Stored only on this device.")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        libraryPrefs.steamGridDbKey = input.text.toString()
                        if (!libraryPrefs.steamGridDbKey.isNullOrBlank()) fetchIcon(rom)
                    }
                    .setNegativeButton("Cancel", null)
            }
            return
        }
        val term = rom.profileName.ifBlank { displayTitle(rom) }
        viewScope?.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { CoverArt.fetchSteamGridDbIcon(context, rom.sha256, term, key) }
            }
            result.onSuccess { filterRoms(searchInput.text.toString()) }
                .onFailure { Toast.makeText(context, "Icon fetch failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun showRomInfo(rom: RomItem) {
        Toast.makeText(context, "Reading ${rom.fileName}…", Toast.LENGTH_SHORT).show()
        viewScope?.launch {
            val text = withContext(Dispatchers.IO) { runCatching { buildRomInfo(rom) }.getOrElse { "Could not read ROM: ${it.message}" } }
            dialog { b -> b.setTitle(displayTitle(rom)).setMessage(text).setPositiveButton("Close", null) }
        }
    }

    private fun buildRomInfo(rom: RomItem): String {
        val sums = RomInfo.checksums(context.contentResolver.openInputStream(rom.uri) ?: error("cannot open"))
        val h = sums.header
        val identity = RomIdentity.create(sums.sha256, displayTitle(rom))
        val saveDir = SaveStateManager.getInstance(context).getCanonicalRomDir(identity)
        val files = saveDir.listFiles()?.filter { it.length() > 0 }.orEmpty()
        val slots = files.mapNotNull { Regex("""slot_(\d+)\.state""").matchEntire(it.name)?.groupValues?.get(1)?.toInt() }.sorted()
        val backups = files.filter { it.name.endsWith(".bak") || ".backup" in it.name }
        val resume = files.firstOrNull { it.name == "auto_resume.state" }
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return buildString {
            appendLine("Header title: ${h?.title ?: "?"}")
            appendLine("Game code: ${h?.gameCode ?: "?"}  Maker: ${h?.makerCode ?: "?"}  Revision: ${h?.revision ?: "?"}")
            appendLine("Status: ${rom.status?.let { RomCompatibilityMessages.badge(it) } ?: "Not inspected"}${rom.profileName.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}")
            appendLine("Size: ${sums.size} bytes")
            appendLine("CRC32: ${sums.crc32}")
            appendLine("SHA-1: ${sums.sha1}")
            appendLine("SHA-256: ${sums.sha256}")
            appendLine("Save chip: ${sums.saveType}")
            appendLine("Battery save: ${if (files.any { it.name == "battery.sav" }) "present" else "none"}")
            appendLine("State slots used: ${if (slots.isEmpty()) "none" else slots.joinToString()}")
            appendLine("Backups: ${if (backups.isEmpty()) "none" else backups.joinToString { it.name }}")
            append("Resume: ${resume?.let { "saved ${fmt.format(java.util.Date(it.lastModified()))}" } ?: "none"}")
        }
    }

    private fun dialog(build: (AlertDialog.Builder) -> AlertDialog.Builder) {
        runCatching { build(AlertDialog.Builder(context)).show() }
            .onFailure { Toast.makeText(context, "Could not open dialog: ${it.message}", Toast.LENGTH_SHORT).show() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope?.cancel()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { scope ->
            AppUpdater.onLibraryOpened(context)
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
