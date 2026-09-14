package com.dualdex.romhack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ProfileLoader {

    fun loadProfilesFromAssets(context: Context): List<RomHackProfile> {
        val profiles = mutableListOf<RomHackProfile>()
        try {
            val assetManager = context.assets
            val files = assetManager.list("profiles") ?: emptyArray()
            for (filename in files) {
                if (filename.endsWith(".json")) {
                    try {
                        val jsonStr = assetManager.open("profiles/$filename").bufferedReader().use { it.readText() }
                        val profile = parseProfile(jsonStr)
                        profiles.add(profile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return profiles
    }

    fun parseProfile(jsonStr: String): RomHackProfile {
        val json = JSONObject(jsonStr)

        val id = json.getString("id")
        val name = json.getString("name")
        val baseGame = json.getString("baseGame")
        val gameId = json.optInt("gameId", 0)
        val developer = json.optString("developer", "")
        val engine = json.optString("engine", "Vanilla")
        val hasEvs = json.optBoolean("hasEvs", true)
        val hasIvs = json.optBoolean("hasIvs", true)
        val hasPhysSpecSplit = json.optBoolean("hasPhysSpecSplit", false)
        val steelResistsGhostDark = json.optBoolean("steelResistsGhostDark", true)
        val cfruOffsets = json.optBoolean("cfruOffsets", false)
        val playerPartyOffset = json.optLong("playerPartyOffset", 0L)
        val enemyPartyOffset = json.optLong("enemyPartyOffset", 0L)
        val docsUrl = if (json.has("docsUrl") && !json.isNull("docsUrl")) json.getString("docsUrl") else null

        val headerTitles = mutableListOf<String>()
        val headerArray = json.optJSONArray("headerTitles") ?: JSONArray()
        for (i in 0 until headerArray.length()) {
            headerTitles.add(headerArray.getString(i))
        }

        val sha256Hashes = mutableListOf<String>()
        val hashArray = json.optJSONArray("sha256Hashes") ?: JSONArray()
        for (i in 0 until hashArray.length()) {
            sha256Hashes.add(hashArray.getString(i).lowercase())
        }

        val customSpecies = mutableMapOf<Int, SpeciesOverride>()
        val speciesObj = json.optJSONObject("customSpecies")
        if (speciesObj != null) {
            val keys = speciesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val sId = key.toIntOrNull() ?: continue
                val sData = speciesObj.getJSONObject(key)
                val sOverride = SpeciesOverride(
                    name = sData.getString("name"),
                    type1 = sData.getString("type1"),
                    type2 = if (sData.has("type2") && !sData.isNull("type2")) sData.getString("type2") else null,
                    hp = sData.optInt("hp", 70),
                    atk = sData.optInt("atk", 70),
                    def = sData.optInt("def", 70),
                    spa = sData.optInt("spa", 70),
                    spd = sData.optInt("spd", 70),
                    spe = sData.optInt("spe", 70)
                )
                customSpecies[sId] = sOverride
            }
        }

        return RomHackProfile(
            id = id,
            name = name,
            baseGame = baseGame,
            gameId = gameId,
            developer = developer,
            engine = engine,
            hasEvs = hasEvs,
            hasIvs = hasIvs,
            hasPhysSpecSplit = hasPhysSpecSplit,
            steelResistsGhostDark = steelResistsGhostDark,
            cfruOffsets = cfruOffsets,
            playerPartyOffset = playerPartyOffset,
            enemyPartyOffset = enemyPartyOffset,
            docsUrl = docsUrl,
            headerTitles = headerTitles,
            sha256Hashes = sha256Hashes,
            customSpecies = customSpecies,
            isVerified = json.optBoolean("isVerified", false),
            interactiveControlsVerified = json.optBoolean("interactiveControlsVerified", false),
            memoryLayoutVerified = json.optBoolean("memoryLayoutVerified", false),
            battleUiVerified = json.optBoolean("battleUiVerified", false),
            commandCursorVerified = json.optBoolean("commandCursorVerified", false),
            moveCursorVerified = json.optBoolean("moveCursorVerified", false),
            partyCursorVerified = json.optBoolean("partyCursorVerified", false),
            moveSelectionVerified = json.optBoolean("moveSelectionVerified", false),
            partySwitchVerified = json.optBoolean("partySwitchVerified", false),
            partyActionMenuVerified = json.optBoolean("partyActionMenuVerified", false),
            gameDataPackId = if (json.has("gameDataPackId") && !json.isNull("gameDataPackId")) json.getString("gameDataPackId") else null
        )
    }
}
