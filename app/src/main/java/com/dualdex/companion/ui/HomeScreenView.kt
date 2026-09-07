package com.dualdex.companion.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.dualdex.companion.CompanionViewModel
import com.dualdex.companion.RomItem
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeScreenView(
    context: Context,
    private val viewModel: CompanionViewModel,
    private val onChooseRomsFolderRequested: (() -> Unit)? = null,
    private val onRefreshRomsRequested: (() -> Unit)? = null,
    private val onPlayRomRequested: ((Uri, String) -> Unit)? = null
) : LinearLayout(context) {

    private val settingsManager = SettingsManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val romsContainer: LinearLayout
    private val folderStatusText: TextView
    private val resumeCard: LinearLayout
    private val resumeGameLabel: TextView
    private val searchInput: EditText
    private var allRoms: List<RomItem> = emptyList()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF101014.toInt())
        setPadding(20, 20, 20, 20)

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        scroll.addView(content)
        addView(scroll)

        // Header Title
        val titleView = TextView(context).apply {
            text = "🎮 DualDex Game Library"
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 4)
        }
        content.addView(titleView)

        val subTitle = TextView(context).apply {
            text = "Select a game to start playing on the top screen with companion stats below"
            setTextColor(0xFF888899.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 16)
        }
        content.addView(subTitle)

        // 1. Resume Last Played Game Card
        resumeCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "⚡ Continue Last Game"
                setTextColor(0xFFFFD700.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 6)
            }
            addView(label)

            resumeGameLabel = TextView(context).apply {
                text = "No recent game"
                setTextColor(0xFFE0E0E0.toInt())
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 10)
            }
            addView(resumeGameLabel)

            val resumeBtn = Button(context).apply {
                text = "▶️ Resume Playthrough"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0xFF2E6B4A.toInt())
                }
                setPadding(18, 10, 18, 10)
                setOnClickListener {
                    val uriStr = settingsManager.lastPlayedRomUri
                    if (!uriStr.isNullOrBlank()) {
                        val title = settingsManager.lastPlayedRomTitle ?: "Game"
                        onPlayRomRequested?.invoke(Uri.parse(uriStr), title)
                    } else {
                        Toast.makeText(context, "No previous game recorded", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            addView(resumeBtn)
        }
        content.addView(resumeCard)

        // 2. ROMs Directory Configuration Card
        val folderCard = createCardLayout().apply {
            val label = TextView(context).apply {
                text = "📁 ROMs Folder"
                setTextColor(0xFF4A9EFF.toInt())
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 6)
            }
            addView(label)

            folderStatusText = TextView(context).apply {
                text = if (settingsManager.romsFolderUri != null) "Folder configured" else "No folder selected"
                setTextColor(0xFF9999AA.toInt())
                textSize = 12.5f
                setPadding(0, 0, 0, 10)
            }
            addView(folderStatusText)

            val btnRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, 0, 0, 4)
            }

            val pickFolderBtn = Button(context).apply {
                text = "📁 Set / Change Folder"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0xFF2B3A55.toInt())
                }
                setPadding(14, 8, 14, 8)
                setOnClickListener { onChooseRomsFolderRequested?.invoke() }
            }
            val lpPick = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f).apply { setMargins(0, 0, 8, 0) }
            btnRow.addView(pickFolderBtn, lpPick)

            val refreshBtn = Button(context).apply {
                text = "🔄 Refresh"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(0xFF333348.toInt())
                }
                setPadding(14, 8, 14, 8)
                setOnClickListener { onRefreshRomsRequested?.invoke() }
            }
            val lpRef = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            btnRow.addView(refreshBtn, lpRef)

            addView(btnRow)
        }
        content.addView(folderCard)

        // Search Bar for ROMs
        searchInput = EditText(context).apply {
            hint = "🔍 Search games..."
            setHintTextColor(0xFF777788.toInt())
            setTextColor(Color.WHITE)
            textSize = 13.5f
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(0xFF1E1E28.toInt())
                setStroke(1, 0xFF3A3A4E.toInt())
            }
            setPadding(16, 10, 16, 10)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 0, 12)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterRoms(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        content.addView(searchInput)

        // 3. ROMs List Container
        romsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, 24)
        }
        content.addView(romsContainer)

        // Observe scanned ROMs from ViewModel
        scope.launch {
            viewModel.scannedRoms.collectLatest { roms ->
                allRoms = roms
                filterRoms(searchInput.text.toString())
                updateFolderStatus()
            }
        }

        updateResumeCard()
        updateFolderStatus()
    }

    fun updateResumeCard() {
        val lastTitle = settingsManager.lastPlayedRomTitle
        val lastUri = settingsManager.lastPlayedRomUri
        if (HomeScreenView.shouldShowResumeCard(lastUri, lastTitle)) {
            resumeCard.visibility = View.VISIBLE
            resumeGameLabel.text = lastTitle
        } else {
            resumeCard.visibility = View.GONE
        }
    }

    fun updateFolderStatus() {
        val uriStr = settingsManager.romsFolderUri
        if (uriStr != null) {
            folderStatusText.text = "Folder active (${allRoms.size} GBA game${if (allRoms.size == 1) "" else "s"} found)"
            folderStatusText.setTextColor(0xFF50C878.toInt())
        } else {
            folderStatusText.text = "Tap below to select your GBA ROMs directory"
            folderStatusText.setTextColor(0xFF9999AA.toInt())
        }
    }

    private fun filterRoms(query: String) {
        romsContainer.removeAllViews()

        val filtered = if (query.isBlank()) {
            allRoms
        } else {
            allRoms.filter { it.title.contains(query, ignoreCase = true) || it.fileName.contains(query, ignoreCase = true) }
        }

        if (filtered.isEmpty()) {
            val emptyView = TextView(context).apply {
                text = if (allRoms.isEmpty()) {
                    "No .gba ROMs found yet.\nSelect your ROMs folder above to populate your games!"
                } else {
                    "No games matching \"$query\""
                }
                setTextColor(0xFF888899.toInt())
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 24)
            }
            romsContainer.addView(emptyView)
            return
        }

        filtered.forEach { rom ->
            val itemCard = createCardLayout().apply {
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val infoCol = LinearLayout(context).apply {
                    orientation = VERTICAL
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
                }

                val titleText = TextView(context).apply {
                    text = rom.title
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                }
                infoCol.addView(titleText)

                val metaText = TextView(context).apply {
                    text = "${rom.fileName} • ${rom.sizeFormatted}"
                    setTextColor(0xFF8888AA.toInt())
                    textSize = 11.5f
                    setPadding(0, 2, 0, 0)
                }
                infoCol.addView(metaText)

                row.addView(infoCol)

                val playBtn = Button(context).apply {
                    text = "▶️ Play"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        cornerRadius = 12f
                        setColor(0xFF2E6B4A.toInt())
                    }
                    setPadding(16, 8, 16, 8)
                    setOnClickListener {
                        onPlayRomRequested?.invoke(rom.uri, rom.title)
                    }
                }
                val lpPlay = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(10, 0, 0, 0)
                }
                row.addView(playBtn, lpPlay)

                addView(row)
            }
            romsContainer.addView(itemCard)
        }
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(0xFF181822.toInt())
                setStroke(1, 0xFF2A2A38.toInt())
            }
            setPadding(16, 14, 16, 14)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 10)
            }
        }
    }

    companion object {
        /**
         * The Continue card is presented only when both the ROM URI and its title
         * are stored. A target with a blank URI or title is incomplete and must
         * not be offered as a game to resume — this contract also guarantees that a
         * cleared/stale target (both removed) never reappears on the home screen.
         */
        fun shouldShowResumeCard(uri: String?, title: String?): Boolean =
            !uri.isNullOrBlank() && !title.isNullOrBlank()
    }
}
