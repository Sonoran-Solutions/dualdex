package com.dualdex

/**
 * Pure JVM snapshot of an Android logical display used for companion display routing
 * and privacy-safe diagnostics reporting.
 */
data class DisplayDescriptor(
    val displayId: Int,
    val isPresentationCategory: Boolean,
    val isDefaultDisplay: Boolean,
    val widthPx: Int,
    val heightPx: Int
)

/**
 * Deterministic display-selection policy for attaching [com.dualdex.companion.CompanionPresentation]
 * across single-screen and multi-screen Android handhelds (e.g. AYN Thor, AYANEO Pocket DS).
 *
 * Invariant: this policy NEVER returns [hostDisplayId], preventing the companion Presentation
 * from covering the active game display on devices where Display 0 is also flagged with
 * `DISPLAY_CATEGORY_PRESENTATION`.
 */
object DisplaySelectionPolicy {

    /**
     * Selects the best companion display for a given [hostDisplayId] from [allDisplays],
     * or returns `null` when DualDex should fall back to single-screen vertical split mode.
     *
     * Priority:
     * 1. Valid `DISPLAY_CATEGORY_PRESENTATION` display with `displayId != hostDisplayId`
     *    (preferring non-default displays, then lowest `displayId`).
     * 2. Valid secondary non-default display with `displayId != hostDisplayId`
     *    (lowest `displayId`).
     * 3. `null` (single-screen split fallback).
     */
    fun selectCompanionDisplay(
        hostDisplayId: Int,
        allDisplays: List<DisplayDescriptor>
    ): DisplayDescriptor? {
        val candidates = allDisplays.filter { display ->
            display.displayId != hostDisplayId &&
                display.displayId >= 0 &&
                display.widthPx > 0 &&
                display.heightPx > 0
        }
        if (candidates.isEmpty()) return null

        val presentationCandidates = candidates
            .filter { it.isPresentationCategory }
            .sortedWith(compareBy<DisplayDescriptor> { it.isDefaultDisplay }.thenBy { it.displayId })
        if (presentationCandidates.isNotEmpty()) {
            return presentationCandidates.first()
        }

        val secondaryNonDefault = candidates
            .filter { !it.isDefaultDisplay }
            .sortedBy { it.displayId }
        return secondaryNonDefault.firstOrNull()
    }
}
