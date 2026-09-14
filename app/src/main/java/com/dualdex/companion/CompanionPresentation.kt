package com.dualdex.companion

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.net.Uri
import com.dualdex.companion.ui.CompanionScreenView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class CompanionPresentation(
    context: Context,
    display: Display,
    private val viewModel: CompanionViewModel,
    private val onOpenRomRequested: (() -> Unit)? = null,
    private val onShaderChanged: ((com.dualdex.emulator.ShaderFilter) -> Unit)? = null,
    private val onSpeedChanged: ((Int) -> Unit)? = null,
    private val onImportSaveRequested: (() -> Unit)? = null,
    private val onExportSaveRequested: (() -> Unit)? = null,
    private val onChooseRomsFolderRequested: (() -> Unit)? = null,
    private val onRefreshRomsRequested: (() -> Unit)? = null,
    private val onPlayRomRequested: ((Uri, String) -> Unit)? = null,
    private val onStretchChanged: ((Boolean) -> Unit)? = null,
    private val onChooseSavesFolderRequested: (() -> Unit)? = null
) : Presentation(context, display) {

    private var companionScreenView: CompanionScreenView? = null
    private val presentationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = CompanionScreenView(
            context = context,
            viewModel = viewModel,
            onOpenRomRequested = onOpenRomRequested,
            onShaderChanged = onShaderChanged,
            onSpeedChanged = onSpeedChanged,
            onImportSaveRequested = onImportSaveRequested,
            onExportSaveRequested = onExportSaveRequested,
            onChooseRomsFolderRequested = onChooseRomsFolderRequested,
            onRefreshRomsRequested = onRefreshRomsRequested,
            onPlayRomRequested = onPlayRomRequested,
            onStretchChanged = onStretchChanged,
            onChooseSavesFolderRequested = onChooseSavesFolderRequested
        ).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        companionScreenView = screen
        setContentView(screen)

        // Observe player party updates and notify bottom screen UI
        presentationScope.launch {
            viewModel.playerParty.collectLatest {
                companionScreenView?.notifyPartyUpdated()
            }
        }

        // Observe profile changes
        presentationScope.launch {
            viewModel.activeProfile.collectLatest {
                companionScreenView?.notifyProfileChanged()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        presentationScope.cancel()
        companionScreenView = null
    }

    fun refreshSavesTab() {
        companionScreenView?.refreshSavesTab()
    }

    fun refreshHomeScreen() {
        companionScreenView?.refreshHomeScreen()
    }
}
