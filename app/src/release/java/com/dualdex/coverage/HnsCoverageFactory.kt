package com.dualdex.coverage

import android.content.Context
import android.widget.LinearLayout

/** No storage, export, executor or share implementation is compiled into release. */
object HnsCoverageFactory {
    fun create(): HnsCalcCoverageLogger = NoOpHnsCalcCoverageLogger
    fun initialize(context: Context) = Unit
    fun addSettingsActions(container: LinearLayout) = Unit
}
