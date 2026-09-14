package com.dualdex.settings

/** Pure resolution rule for the renamed Battle Console auto-open preference. */
internal object BattleAutoOpenPreference {
    fun resolve(
        hasCurrentValue: Boolean,
        currentValue: Boolean,
        hasLegacyValue: Boolean,
        legacyValue: Boolean
    ): Boolean = when {
        hasCurrentValue -> currentValue
        hasLegacyValue -> legacyValue
        else -> true
    }
}
