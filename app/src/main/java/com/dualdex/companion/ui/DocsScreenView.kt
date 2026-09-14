package com.dualdex.companion.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dualdex.companion.CompanionViewModel

/**
 * Redesigned Docs companion screen adhering to the Quiet Handheld Companion design system.
 * Delivers maximum viewing area for web documentation, a compact navigation toolbar (Back,
 * Forward, Reload, Browser), profile-accurate offline fallback notes, and a safe cached WebView
 * lifecycle that survives tab switches without destruction.
 */
@SuppressLint("SetJavaScriptEnabled")
class DocsScreenView(
    context: Context,
    private val viewModel: CompanionViewModel
) : LinearLayout(context) {

    private val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webChromeClient = WebChromeClient()
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)
    }
    private val offlineGuideContainer: ScrollView
    private val titleView: TextView
    private val subtitleView: TextView
    private val backBtn: TextView
    private val forwardBtn: TextView
    private val reloadBtn: TextView
    private val browserBtn: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(DualDexTheme.Color.background)

        // 1. Top Bar: Screen Title, Subtitle & Navigation Toolbar
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.compact),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.compact)
            )
            setBackgroundColor(DualDexTheme.Color.surface)
        }

        val headerTextLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            titleView = DualDexComponents.screenTitle(context, "Docs").apply {
                textSize = DualDexTheme.Type.sectionTitle
            }
            subtitleView = TextView(context).apply {
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.compact
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(titleView)
            addView(subtitleView)
        }
        topBar.addView(headerTextLayout, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f))

        // Toolbar Buttons
        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        backBtn = DualDexComponents.smallButton(context, "Back", DualDexButtonStyle.SECONDARY) {
            if (webView.canGoBack()) webView.goBack()
        }.apply {
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30)).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.tight)
            }
            layoutParams = lp
        }
        toolbar.addView(backBtn)

        forwardBtn = DualDexComponents.smallButton(context, "Forward", DualDexButtonStyle.SECONDARY) {
            if (webView.canGoForward()) webView.goForward()
        }.apply {
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30)).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.tight)
            }
            layoutParams = lp
        }
        toolbar.addView(forwardBtn)

        reloadBtn = DualDexComponents.smallButton(context, "Reload", DualDexButtonStyle.SECONDARY) {
            refreshUI()
        }.apply {
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30)).apply {
                marginEnd = context.dp(DualDexTheme.Spacing.tight)
            }
            layoutParams = lp
        }
        toolbar.addView(reloadBtn)

        browserBtn = DualDexComponents.smallButton(context, "Browser", DualDexButtonStyle.GHOST) {
            val docsUrl = viewModel.activeProfile.value.docsUrl
            if (!docsUrl.isNullOrBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(docsUrl))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30))
        }
        toolbar.addView(browserBtn)

        topBar.addView(toolbar)
        addView(topBar)

        // Divider
        addView(DualDexComponents.divider(context), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1)))

        // 2. Full-height WebView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateNavButtons()
            }
        }
        addView(webView)

        // 3. Offline Guide Fallback
        offlineGuideContainer = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)
            setPadding(
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.section),
                context.dp(DualDexTheme.Spacing.major)
            )
            visibility = View.GONE
        }
        addView(offlineGuideContainer)

        refreshUI()
    }

    private fun updateNavButtons() {
        backBtn.isEnabled = webView.canGoBack()
        forwardBtn.isEnabled = webView.canGoForward()
    }

    fun refreshUI() {
        val profile = viewModel.activeProfile.value
        val docsUrl = profile.docsUrl

        if (!docsUrl.isNullOrBlank()) {
            subtitleView.text = "${profile.name} · Web Documentation"
            browserBtn.visibility = View.VISIBLE
            backBtn.visibility = View.VISIBLE
            forwardBtn.visibility = View.VISIBLE
            webView.visibility = View.VISIBLE
            offlineGuideContainer.visibility = View.GONE

            if (webView.url != docsUrl) {
                webView.loadUrl(docsUrl)
            }
            updateNavButtons()
        } else {
            subtitleView.text = if (profile.name.isNotBlank()) "${profile.name} · Offline Guide" else "Game Documentation"
            browserBtn.visibility = View.GONE
            backBtn.visibility = View.GONE
            forwardBtn.visibility = View.GONE
            webView.visibility = View.GONE
            offlineGuideContainer.visibility = View.VISIBLE
            buildOfflineGuide()
        }
    }

    private fun buildOfflineGuide() {
        val profile = viewModel.activeProfile.value
        offlineGuideContainer.removeAllViews()

        val card = DualDexComponents.surfaceCard(context, elevated = false).apply {
            val title = TextView(context).apply {
                text = "${profile.name.ifBlank { "Active ROM" }} — Reference"
                setTextColor(DualDexTheme.Color.textPrimary)
                textSize = DualDexTheme.Type.sectionTitle
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, context.dp(DualDexTheme.Spacing.compact))
            }
            addView(title)

            val baseGameText = if (profile.baseGame.isNotBlank()) profile.baseGame else "GBA"
            val devText = if (profile.developer.isNotBlank()) profile.developer else "Community"
            val engineText = if (profile.engine.isNotBlank()) profile.engine else "Standard"

            val sb = StringBuilder()
            sb.appendLine("• Base Game: $baseGameText")
            sb.appendLine("• Engine: $engineText")
            sb.appendLine("• Developer: $devText")
            sb.appendLine()
            sb.appendLine("Key Mechanics:")
            sb.appendLine("• Effort Values (EVs): ${if (profile.hasEvs) "Enabled" else "REMOVED (Flat stat system)"}")
            sb.appendLine("• Individual Values (IVs): ${if (profile.hasIvs) "Enabled" else "REMOVED (All Pokemon equal IVs)"}")
            sb.appendLine("• Physical / Special Split: ${if (profile.hasPhysSpecSplit) "Enabled (Move-specific categories)" else "Type-based (Vanilla Gen 3)"}")
            sb.appendLine("• Type Chart: ${if (profile.steelResistsGhostDark) "Pre-Gen 6 (Steel resists Ghost and Dark)" else "Modern Gen 6+"}")

            if (profile.id == "ghost_grey" || profile.name.contains("Ghost Grey", ignoreCase = true)) {
                sb.appendLine()
                sb.appendLine("Notable Regional Variants:")
                sb.appendLine("• Lichtoise (#500): Water / Ghost (Base: 79/63/100/85/105/78)")
                sb.appendLine("• Spectrasaur (#501): Grass / Ghost (Base: 80/82/83/100/100/80)")
                sb.appendLine("• Phantomander (#502): Fire / Ghost (Base: 78/84/78/109/85/100)")
            }

            sb.appendLine()
            sb.appendLine("Tip: Open the Assistant tab in More for specific item locations and moveset details.")

            val body = TextView(context).apply {
                text = sb.toString().trimEnd()
                setTextColor(DualDexTheme.Color.textSecondary)
                textSize = DualDexTheme.Type.body
                setLineSpacing(4f, 1f)
            }
            addView(body)
        }

        offlineGuideContainer.addView(card)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        webView.onResume()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Do NOT call webView.destroy() here! Calling destroy on a cached view that stays
        // in memory permanently invalidates the native WebView instance, causing crashes
        // on subsequent tab switches. Only pause and stop loading.
        try {
            webView.stopLoading()
            webView.onPause()
        } catch (e: Exception) {
            // ignore
        }
    }
}
