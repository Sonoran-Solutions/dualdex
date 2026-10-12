package com.dualdex.assistant

import android.content.Context
import android.util.Log
import com.dualdex.companion.CompanionViewModel
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.SpeciesDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class WebCitation(val title: String, val url: String)

data class AssistantResponse(
    val text: String,
    val searchQueries: List<String> = emptyList(),
    val citations: List<WebCitation> = emptyList(),
    val isOfflineFallback: Boolean = false,
    val error: String? = null
)

object RomHackAssistant {

    private const val TAG = "DualDexAssistant"
    private var customApiKey: String? = null
    private var activeModel: String = "gemini-3.8-flash"

    fun setApiKey(key: String?) {
        customApiKey = key
    }

    fun setModel(model: String) {
        activeModel = model
    }

    fun getModel(): String = activeModel

    suspend fun askQuestion(
        context: Context? = null,
        userQuestion: String,
        viewModel: CompanionViewModel
    ): AssistantResponse = withContext(Dispatchers.IO) {
        val profile = viewModel.activeProfile.value
        val party = viewModel.playerParty.value

        // Construct context-enriched prompt
        val promptBuilder = StringBuilder()
        promptBuilder.append("You are DualDex, an expert Pokemon and ROM hack companion on the AYN Thor dual-screen handheld.\n")
        promptBuilder.append("The user is playing: ${profile.name} (Base Game: ${profile.baseGame}, Engine: ${profile.engine}).\n")

        if (!profile.hasEvs) {
            promptBuilder.append("Crucial Mechanic: EVs and IVs are removed in this hack.\n")
        }
        if (profile.steelResistsGhostDark) {
            promptBuilder.append("Crucial Mechanic: Steel retains pre-Gen 6 resistance to Ghost and Dark.\n")
        }

        if (party.isNotEmpty()) {
            promptBuilder.append("\nPlayer's Active Party:\n")
            party.forEach { mon ->
                val sName = if (mon.nickname.isNotBlank()) mon.nickname else SpeciesDatabase.get(mon.species).name
                val moves = mon.moves.filter { it > 0 }.map { MoveDatabase.get(it).name }.joinToString(", ")
                promptBuilder.append("- $sName (Lv. ${mon.level}, Moves: $moves)\n")
            }
        }

        promptBuilder.append("\nUser Question: $userQuestion\n")
        promptBuilder.append("Provide a clear, concise, accurate answer specific to ${profile.name}. If web grounding search results are used, incorporate them directly.")

        val fullPrompt = promptBuilder.toString()
        val apiKey = customApiKey

        if (!apiKey.isNullOrBlank()) {
            try {
                return@withContext queryGeminiWithSearchGrounding(apiKey, fullPrompt, activeModel)
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API call failed, attempting fallback or smart offline: ${e.message}")
                if (activeModel != "gemini-2.5-flash") {
                    try {
                        return@withContext queryGeminiWithSearchGrounding(apiKey, fullPrompt, "gemini-2.5-flash")
                    } catch (e2: Exception) {
                        Log.w(TAG, "Gemini fallback failed: ${e2.message}")
                    }
                }
            }
        }

        // Smart offline knowledge fallback for Ghost Grey & FireRed
        return@withContext generateOfflineKnowledgeResponse(userQuestion, profile.name)
    }

    private fun queryGeminiWithSearchGrounding(apiKey: String, prompt: String, model: String = "gemini-3.8-flash"): AssistantResponse {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection

        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            // Payload with Google Search Grounding tool
            val requestJson = JSONObject().apply {
                val contentsArr = JSONArray().apply {
                    put(JSONObject().apply {
                        val partsArr = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", partsArr)
                    })
                }
                put("contents", contentsArr)

                // Enable Google Search Tool Grounding
                val toolsArr = JSONArray().apply {
                    put(JSONObject().apply {
                        put("google_search", JSONObject())
                    })
                }
                put("tools", toolsArr)
            }

            OutputStreamWriter(conn.outputStream).use { os ->
                os.write(requestJson.toString())
                os.flush()
            }

            val code = conn.responseCode
            if (code != 200) {
                val errStream = conn.errorStream ?: conn.inputStream
                val errStr = BufferedReader(InputStreamReader(errStream)).use { it.readText() }
                return AssistantResponse(
                    text = "Error connecting to Gemini API: HTTP $code ($errStr)",
                    error = "HTTP $code"
                )
            }

            val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val resObj = JSONObject(responseStr)

            val candidates = resObj.optJSONArray("candidates") ?: JSONArray()
            if (candidates.length() == 0) {
                return AssistantResponse(text = "No response generated by model.")
            }

            val candidate = candidates.getJSONObject(0)
            val contentObj = candidate.optJSONObject("content")
            val partsArr = contentObj?.optJSONArray("parts") ?: JSONArray()
            val textBuilder = StringBuilder()
            for (i in 0 until partsArr.length()) {
                textBuilder.append(partsArr.getJSONObject(i).optString("text", ""))
            }

            val queries = mutableListOf<String>()
            val citations = mutableListOf<WebCitation>()

            val grounding = candidate.optJSONObject("groundingMetadata")
            if (grounding != null) {
                val webQueries = grounding.optJSONArray("webSearchQueries") ?: JSONArray()
                for (i in 0 until webQueries.length()) {
                    queries.add(webQueries.getString(i))
                }

                val chunks = grounding.optJSONArray("groundingChunks") ?: JSONArray()
                for (i in 0 until chunks.length()) {
                    val chunk = chunks.getJSONObject(i)
                    val web = chunk.optJSONObject("web")
                    if (web != null) {
                        val uri = web.optString("uri", "")
                        val title = web.optString("title", uri)
                        if (uri.isNotBlank()) {
                            citations.add(WebCitation(title, uri))
                        }
                    }
                }
            }

            return AssistantResponse(
                text = textBuilder.toString(),
                searchQueries = queries,
                citations = citations,
                isOfflineFallback = false
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun generateOfflineKnowledgeResponse(question: String, gameName: String): AssistantResponse {
        val q = question.lowercase()

        val answerText = when {
            q.contains("fly") || q.contains("hm02") -> {
                "In FireRed-based hacks like $gameName, HM02 Fly is obtained on Route 16 (west of Celadon City). Cut the tree above the Snorlax sleeping spot, walk through the gatehouse into the secret house, and talk to the girl inside."
            }
            q.contains("surf") || q.contains("hm03") -> {
                "HM03 Surf is located deep in the Safari Zone (Area 4, Secret House) in Fuchsia City. Speak to the attendant in the secret house to obtain it."
            }
            q.contains("strength") || q.contains("hm04") -> {
                "HM04 Strength is obtained from the Warden in Fuchsia City after finding his Gold Teeth in the Safari Zone."
            }
            q.contains("ev") || q.contains("iv") -> {
                if (gameName.contains("Ghost Grey", ignoreCase = true)) {
                    "In Pokemon Ghost Grey, EVs and IVs are completely removed from gameplay! All Pokemon fight with pure base stats, levels, and natures. You do not need to EV train."
                } else {
                    "In standard FireRed / Radical Red, Pokemon earn Effort Values (EVs) up to 252 per stat (510 total). The DualDex Party tab displays your live IVs and EVs in real-time."
                }
            }
            q.contains("steel") || q.contains("dark") || q.contains("ghost") -> {
                if (gameName.contains("Ghost Grey", ignoreCase = true)) {
                    "In Pokemon Ghost Grey, the Steel type retains its pre-Gen 6 resistances! Steel takes 0.5x half damage from Ghost and Dark attacks."
                } else {
                    "In Gen 6+ type mechanics, Steel takes 1.0x neutral damage from Ghost and Dark."
                }
            }
            q.contains("lichtoise") || q.contains("squirtle") -> {
                "Lichtoise is the Water/Ghost custom regional final evolution in Ghost Grey, boasting 100 Def and 105 SpD with Shell Armor and powerful Ghost/Water STAB moves."
            }
            q.contains("spectrasaur") || q.contains("bulbasaur") -> {
                "Spectrasaur is the Grass/Ghost custom regional final evolution in Ghost Grey, featuring 100 SpA and 100 SpD with Ghost STAB Shadow Ball and Giga Drain."
            }
            q.contains("phantomander") || q.contains("charmander") -> {
                "Phantomander is the Fire/Ghost custom regional final evolution in Ghost Grey, with 109 SpA and 100 Speed."
            }
            q.contains("exp share") || q.contains("exp. share") -> {
                "In FireRed-based hacks, the Exp. Share is given by Professor Oak's aide on Route 15 (east of Fuchsia City) gatehouse upstairs once you have registered at least 50 Pokemon in your Pokedex."
            }
            else -> {
                "Here is the guidance for '$question' in $gameName:\n" +
                "DualDex is operating in offline mode. If you provide a Google AI Studio API key in the Assistant tab, DualDex will use Gemini 2.5 Flash with live Google Search grounding to retrieve exact patch notes, PokeCommunity threads, and Reddit walkthroughs!"
            }
        }

        return AssistantResponse(
            text = answerText,
            searchQueries = emptyList(),
            citations = emptyList(),
            isOfflineFallback = true
        )
    }
}
