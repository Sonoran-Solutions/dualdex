package com.dualdex.companion.ui

/** A confirmation/editor action that can be canceled and committed at most once. */
internal class RomBoundAction(
    private val isStillCurrent: () -> Boolean,
    private val onStale: () -> Unit = {},
    private val action: () -> Unit
) {
    private var resolved = false

    fun confirm() {
        if (resolved) return
        resolved = true
        if (isStillCurrent()) action() else onStale()
    }

    fun cancel() {
        resolved = true
    }
}
