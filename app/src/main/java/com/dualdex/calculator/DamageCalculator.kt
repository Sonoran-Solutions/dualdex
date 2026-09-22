package com.dualdex.calculator

import android.content.Context
import com.dualdex.pokemon.ParsedPokemon
import org.json.JSONArray
import org.json.JSONObject

object DamageCalculator {

    init {
        System.loadLibrary("dualdex_native")
    }

    @Volatile private var isInitialized = false

    private external fun nativeInit(bundleJs: String): Boolean
    private external fun nativeCalculate(inputJson: String): String?
    private external fun nativeCleanup()

    fun initialize(context: Context): Boolean {
        if (isInitialized) return true

        try {
            val bundleJs = context.assets.open("calc_bundle.js").bufferedReader().use { it.readText() }
            isInitialized = nativeInit(bundleJs)
            return isInitialized
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun calculate(request: DamageCalculationRequest): DamageCalculationResponse {
        if (!isInitialized) return DamageCalculationResponse(success = false, error = "Calculator not initialized")
        val reqJson = buildCalcRequestJson(request)
        val resJsonStr = nativeCalculate(reqJson)
            ?: return DamageCalculationResponse(success = false, error = "Native calculation returned null")

        val resObj = JSONObject(resJsonStr)
        val success = resObj.optBoolean("success", false)
        if (!success) {
            return DamageCalculationResponse(
                success = false,
                error = resObj.optString("error", "Unknown error")
            )
        }

        val rangeArray = resObj.optJSONArray("range") ?: JSONArray()
        val rangeList = mutableListOf<Int>()
        for (i in 0 until rangeArray.length()) {
            rangeList.add(rangeArray.getInt(i))
        }

        return DamageCalculationResponse(
            success = true,
            minDamage = resObj.optInt("minDamage", 0),
            maxDamage = resObj.optInt("maxDamage", 0),
            range = rangeList,
            desc = resObj.optString("desc", ""),
            moveName = resObj.optString("moveName", ""),
            moveCategory = resObj.optString("moveCategory", ""),
            moveType = resObj.optString("moveType", ""),
            movePower = resObj.optInt("movePower", 0),
            attackerName = resObj.optString("attackerName", ""),
            defenderName = resObj.optString("defenderName", ""),
            defenderMaxHP = resObj.optInt("defenderMaxHP", 0),
            koChanceText = resObj.optString("koChanceText", ""),
            effectiveness = if (resObj.has("effectiveness")) resObj.optDouble("effectiveness") else null
        )
    }

    fun calculateFromParsed(
        attacker: ParsedPokemon,
        defenderSpecies: String,
        moveName: String,
        gen: Int = 3
    ): DamageCalculationResponse {
        val req = DamageCalculationRequest(
            gen = gen,
            attacker = CalcPokemonInput(
                species = com.dualdex.pokemon.SpeciesDatabase.get(attacker.species).name,
                level = attacker.level,
                nature = attacker.natureName,
                ivs = StatBlock(attacker.hpIv, attacker.attackIv, attacker.defenseIv, attacker.spAttackIv, attacker.spDefenseIv, attacker.speedIv),
                evs = StatBlock(attacker.hpEv, attacker.attackEv, attacker.defenseEv, attacker.spAttackEv, attacker.spDefenseEv, attacker.speedEv)
            ),
            defender = CalcPokemonInput(
                species = defenderSpecies,
                level = attacker.level
            ),
            move = CalcMoveInput(name = moveName)
        )
        return calculate(req)
    }
}

/**
 * Serialises [request] into the JSON contract consumed by the native QuickJS
 * engine (`js_calc_calculate`).
 *
 * Top-level on purpose: JVM unit tests can then assert exactly what the
 * application sends to the engine - including `field.gameType`, whose casing
 * selects the singles or doubles damage path (issue #29) - without triggering
 * [DamageCalculator]'s `System.loadLibrary` initialiser.
 */
internal fun buildCalcRequestJson(request: DamageCalculationRequest): String =
    JSONObject().apply {
        put("gen", request.gen)
        request.typeSystem?.let { put("typeSystem", it) }

        // Attacker
        val atkObj = JSONObject().apply {
            put("species", request.attacker.species)
            put("level", request.attacker.level)
            request.attacker.item?.let { put("item", it) }
            request.attacker.nature?.let { put("nature", it) }
            request.attacker.ability?.let { put("ability", it) }
            request.attacker.curHP?.let { put("curHP", it) }
            request.attacker.status?.let { put("status", it) }
            request.attacker.ivs?.let { ivs ->
                put("ivs", JSONObject().apply {
                    put("hp", ivs.hp)
                    put("atk", ivs.atk)
                    put("def", ivs.def)
                    put("spa", ivs.spa)
                    put("spd", ivs.spd)
                    put("spe", ivs.spe)
                })
            }
            request.attacker.evs?.let { evs ->
                put("evs", JSONObject().apply {
                    put("hp", evs.hp)
                    put("atk", evs.atk)
                    put("def", evs.def)
                    put("spa", evs.spa)
                    put("spd", evs.spd)
                    put("spe", evs.spe)
                })
            }
            request.attacker.boosts?.let { b ->
                put("boosts", JSONObject().apply {
                    put("atk", b.atk)
                    put("def", b.def)
                    put("spa", b.spa)
                    put("spd", b.spd)
                    put("spe", b.spe)
                })
            }
            request.attackerOverride?.let { override ->
                put("overrides", JSONObject().apply {
                    put("types", JSONArray(override.types))
                    put("baseStats", JSONObject().apply {
                        put("hp", override.baseStats.hp)
                        put("atk", override.baseStats.atk)
                        put("def", override.baseStats.def)
                        put("spa", override.baseStats.spa)
                        put("spd", override.baseStats.spd)
                        put("spe", override.baseStats.spe)
                    })
                })
            }
            request.hnsLiveBattleState?.let { live ->
                live.attackerHp?.let { put("hp", it) }
                live.attackerMaxHp?.let { put("maxHP", it) }
            }
            request.hnsLiveBattleState?.attackerRawStats?.let { raw ->
                put("rawStats", JSONObject().apply {
                    put("attack", raw.attack)
                    put("defense", raw.defense)
                    put("speed", raw.speed)
                    put("spAttack", raw.spAttack)
                    put("spDefense", raw.spDefense)
                })
            }
            request.hnsLiveBattleState?.attackerStatStages?.let { stages ->
                put("statStages", JSONArray(stages))
            }
            request.hnsLiveBattleState?.attackerBadgeBoosts?.let { b ->
                put("badgeBoosts", JSONObject().apply {
                    put("atk", b.atk)
                    put("def", b.def)
                    put("spe", b.spe)
                    put("spa", b.spa)
                    put("spd", b.spd)
                })
            }
        }
        put("attacker", atkObj)

        // Defender
        val defObj = JSONObject().apply {
            put("species", request.defender.species)
            put("level", request.defender.level)
            request.defender.item?.let { put("item", it) }
            request.defender.nature?.let { put("nature", it) }
            request.defender.ability?.let { put("ability", it) }
            request.defender.curHP?.let { put("curHP", it) }
            request.defender.status?.let { put("status", it) }
            request.defender.ivs?.let { ivs ->
                put("ivs", JSONObject().apply {
                    put("hp", ivs.hp)
                    put("atk", ivs.atk)
                    put("def", ivs.def)
                    put("spa", ivs.spa)
                    put("spd", ivs.spd)
                    put("spe", ivs.spe)
                })
            }
            request.defender.evs?.let { evs ->
                put("evs", JSONObject().apply {
                    put("hp", evs.hp)
                    put("atk", evs.atk)
                    put("def", evs.def)
                    put("spa", evs.spa)
                    put("spd", evs.spd)
                    put("spe", evs.spe)
                })
            }
            request.defender.boosts?.let { b ->
                put("boosts", JSONObject().apply {
                    put("atk", b.atk)
                    put("def", b.def)
                    put("spa", b.spa)
                    put("spd", b.spd)
                    put("spe", b.spe)
                })
            }
            request.defenderOverride?.let { override ->
                put("overrides", JSONObject().apply {
                    put("types", JSONArray(override.types))
                    put("baseStats", JSONObject().apply {
                        put("hp", override.baseStats.hp)
                        put("atk", override.baseStats.atk)
                        put("def", override.baseStats.def)
                        put("spa", override.baseStats.spa)
                        put("spd", override.baseStats.spd)
                        put("spe", override.baseStats.spe)
                    })
                })
            }
            request.hnsLiveBattleState?.defenderRawStats?.let { raw ->
                put("rawStats", JSONObject().apply {
                    put("attack", raw.attack)
                    put("defense", raw.defense)
                    put("speed", raw.speed)
                    put("spAttack", raw.spAttack)
                    put("spDefense", raw.spDefense)
                })
            }
            request.hnsLiveBattleState?.defenderStatStages?.let { stages ->
                put("statStages", JSONArray(stages))
            }
            request.hnsLiveBattleState?.defenderBadgeBoosts?.let { b ->
                put("badgeBoosts", JSONObject().apply {
                    put("atk", b.atk)
                    put("def", b.def)
                    put("spe", b.spe)
                    put("spa", b.spa)
                    put("spd", b.spd)
                })
            }
        }
        put("defender", defObj)

        // Move
        val moveObj = JSONObject().apply {
            put("name", request.move.name)
            put("isCrit", request.move.isCrit)
            request.moveOverride?.let { override ->
                put("overrides", JSONObject().apply {
                    put("basePower", override.basePower)
                    put("type", override.type)
                    override.category?.let { put("category", it) }
                })
            }
        }
        put("move", moveObj)

        // Field
        val fieldObj = JSONObject().apply {
            put("gameType", request.field.gameType)
            request.field.weather?.let { put("weather", it) }
            request.field.terrain?.let { put("terrain", it) }
            // Boundary-owned live target count (GetMoveTargetCount). Absent today because no reader
            // supplies it; when absent the H&S engine fails closed for Doubles spread moves.
            request.hnsLiveBattleState?.moveTargetCount?.let { put("targetCount", it) }
            request.field.defenderSide?.let { side ->
                put("defenderSide", JSONObject().apply {
                    if (side.isReflect) put("isReflect", true)
                    if (side.isLightScreen) put("isLightScreen", true)
                })
            }
        }
        put("field", fieldObj)
    }.toString()
