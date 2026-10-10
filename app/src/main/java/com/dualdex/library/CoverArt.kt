package com.dualdex.library

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Game-info card art (#153, low priority). Covers live in `filesDir/covers/<sha256>`.
 * Sources: a manual "Set cover" image, or a SteamGridDB icon when the user supplied their own key.
 *
 * Header codes cannot tell Unbound from FireRed, so the search term comes from the SHA-256 profile
 * match made during the library scan (profile name), falling back to the file title.
 */
object CoverArt {
    /** Set by the hosting Activity: launches an image picker and calls [setFromUri] for that SHA. */
    var pickRequest: ((sha256: String) -> Unit)? = null

    fun file(context: Context, sha256: String): File = File(File(context.filesDir, "covers"), sha256.lowercase())

    fun setFromUri(context: Context, sha256: String, image: Uri): Boolean = runCatching {
        val target = file(context, sha256).apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(image)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        true
    }.getOrDefault(false)

    /** Blocking; call off the main thread. Returns the saved file or throws with a readable message. */
    fun fetchSteamGridDbIcon(context: Context, sha256: String, searchTerm: String, apiKey: String): File {
        val term = URLEncoder.encode(searchTerm, "UTF-8").replace("+", "%20")
        val games = JSONObject(get("https://www.steamgriddb.com/api/v2/search/autocomplete/$term", apiKey)).optJSONArray("data")
        val gameId = games?.optJSONObject(0)?.optLong("id") ?: error("No SteamGridDB match for \"$searchTerm\"")
        val icons = JSONObject(get("https://www.steamgriddb.com/api/v2/icons/game/$gameId", apiKey)).optJSONArray("data")
        val url = icons?.optJSONObject(0)?.optString("thumb")?.ifBlank { null }
            ?: icons?.optJSONObject(0)?.optString("url") ?: error("No icon for \"$searchTerm\"")
        val target = file(context, sha256).apply { parentFile?.mkdirs() }
        (URL(url).openConnection() as HttpURLConnection).inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    private fun get(url: String, apiKey: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("Authorization", "Bearer $apiKey")
        if (c.responseCode !in 200..299) error("SteamGridDB HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
