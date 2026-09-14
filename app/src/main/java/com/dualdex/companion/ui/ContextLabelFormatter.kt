package com.dualdex.companion.ui

import java.util.Locale

/** Bounded normalization for suppressing duplicate game/profile labels in the context strip. */
object ContextLabelFormatter {
    fun namesDescribeSameGame(gameName: String, profileName: String): Boolean {
        val game = normalize(gameName)
        val profile = normalize(profileName)
        if (game.isEmpty() || profile.isEmpty()) return false
        return game == profile || game.contains(profile) || profile.contains(game)
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
