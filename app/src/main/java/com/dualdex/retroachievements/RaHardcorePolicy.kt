package com.dualdex.retroachievements

/** Read by the core's mutation gates and by the companion. */
data class RaHardcorePolicy(val isHardcoreActive: Boolean = false) {
    val mayLoadSaveState: Boolean get() = !isHardcoreActive
    val mayUseCheats: Boolean get() = !isHardcoreActive
    val mayUseInteractiveBattleControls: Boolean get() = !isHardcoreActive
    val mayAutoResumeState: Boolean get() = mayLoadSaveState

    companion object {
        const val SUMMARY =
            "Hardcore disables loading save states, auto-resume, cheats, and battle input assistance. Fast-forward stays available."
    }
}
