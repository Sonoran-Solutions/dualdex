package com.dualdex.retroachievements

import android.content.Context
import android.content.SharedPreferences

internal class RaCredentials(val username: String, val token: String) {
    override fun toString() = "RaCredentials([redacted])"
}

internal class RaTokenStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences("dualdex_retroachievements_account", Context.MODE_PRIVATE)
    )

    fun read(): RaCredentials? {
        val username = prefs.getString(USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return RaCredentials(username, token)
    }

    fun save(credentials: RaCredentials) {
        prefs.edit().putString(USERNAME, credentials.username).putString(TOKEN, credentials.token).apply()
    }

    fun clear() {
        prefs.edit().remove(USERNAME).remove(TOKEN).apply()
    }

    override fun toString() = "RaTokenStore([redacted])"

    private companion object {
        const val USERNAME = "username"
        const val TOKEN = "token"
    }
}
