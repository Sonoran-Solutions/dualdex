package com.dualdex.calculator

import android.content.Context
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.hns.Hns205SpeciesMechanics
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

        val immunityCauses = mutableListOf<CalcImmunityCause>()
        val causeArray = resObj.optJSONArray("immunityCauses") ?: JSONArray()
        for (i in 0 until causeArray.length()) {
            val cause = causeArray.optJSONObject(i) ?: continue
            val kind = cause.optString("kind")
            val source = cause.optString("source")
            val name = cause.optString("name")
            if (kind.isNotBlank() && source.isNotBlank() && name.isNotBlank()) {
                immunityCauses += CalcImmunityCause(kind, source, name)
            }
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
            effectiveness = if (resObj.has("effectiveness")) resObj.optDouble("effectiveness") else null,
            immunityCauses = immunityCauses,
            engineEcho = if (resObj.has("attackerAbility") || resObj.has("defenderAbility") ||
                resObj.has("attackerItem") || resObj.has("defenderItem")
            ) {
                CalcEngineOperandEcho(
                    attackerAbility = resObj.optNullableString("attackerAbility"),
                    defenderAbility = resObj.optNullableString("defenderAbility"),
                    attackerItem = resObj.optNullableString("attackerItem"),
                    defenderItem = resObj.optNullableString("defenderItem"),
                    hasCompleteContract = listOf(
                        "attackerAbility", "defenderAbility", "attackerItem", "defenderItem"
                    ).all(resObj::has)
                )
            } else null
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
            if (request.attacker.itemProvenance == CalcItemProvenance.BATTLE_EFFECTIVE) {
                request.attacker.itemId?.let { put("hnsEffectiveItemId", it) }
            }
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
            val attackerOverride = request.attackerOverride
            val attackerTypes = request.hnsLiveBattleState?.attackerTypes ?: attackerOverride?.types
            if (attackerOverride != null || attackerTypes != null) {
                put("overrides", JSONObject().apply {
                    attackerTypes?.let { put("types", JSONArray(it)) }
                    attackerOverride?.let { override ->
                        put("baseStats", JSONObject().apply {
                            put("hp", override.baseStats.hp)
                            put("atk", override.baseStats.atk)
                            put("def", override.baseStats.def)
                            put("spa", override.baseStats.spa)
                            put("spd", override.baseStats.spd)
                            put("spe", override.baseStats.spe)
                        })
                    }
                })
            }
            request.hnsLiveBattleState?.let { live ->
                live.attackerHp?.let { put("hp", it) }
                live.attackerMaxHp?.let { put("maxHP", it) }
                live.attackerStatus1?.let { put("status1", it) }
                live.attackerSpeciesId?.let { put("hnsSpeciesId", it) }
                live.attackerNeutralizingGas?.let { put("hnsNeutralizingGas", it) }
                live.attackerPersistentVolatiles?.let { put("hnsGastroAcid", it.gastroAcid) }
                live.attackerPersonality?.let { put("hnsPersonality", it) }
                if (live.attackerGender != com.dualdex.pokemon.hns.HnsBattlerGender.UNKNOWN) {
                    put("hnsGender", live.attackerGender.name)
                }
                live.attackerSlowStartTimer?.let { put("hnsSlowStartTimer", it) }
                live.attackerChargeTimer?.let { put("hnsChargeTimer", it) }
                live.attackerFlashFireBoosted?.let { put("hnsFlashFireBoosted", it) }
                live.attackerTransformed?.let { put("hnsTransformed", it) }
                live.attackerEmbargo?.let { put("hnsEmbargo", it) }
                live.attackerMetronomeItemCounter?.let { put("hnsMetronomeItemCounter", it) }
                live.attackerTransformedMonSpecies?.let { put("hnsTransformedMonSpecies", it) }
                live.attackerBoosterEnergyActivated?.let { put("hnsBoosterEnergyActivated", it) }
                live.attackerParadoxBoostedStat?.let { put("hnsParadoxBoostedStat", it) }
                live.attackerVesselOfRuin?.let { put("hnsVesselOfRuin", it) }
                live.attackerSwordOfRuin?.let { put("hnsSwordOfRuin", it) }
                live.attackerTabletsOfRuin?.let { put("hnsTabletsOfRuin", it) }
                live.attackerBeadsOfRuin?.let { put("hnsBeadsOfRuin", it) }
                live.attackerSupremeOverlordCounter?.let { put("hnsSupremeOverlordCounter", it) }
                live.attackerGimmick?.let { put("hnsActiveGimmick", it) }
                live.attackerSelectedGimmick?.let { put("hnsSelectedGimmick", it) }
                if (live.attackerAnalyticTurnOrder != HnsAnalyticTurnOrder.UNKNOWN) {
                    put("hnsAnalyticTurnOrder", live.attackerAnalyticTurnOrder.name)
                }
            }
            if (request.typeSystem == "hns_2_0_5") {
                put(
                    "hnsAbilityShield",
                    HnsHoldEffectAuthority.forRequest(request, HnsItemSide.ATTACKER).effectiveHoldEffect ==
                        "HOLD_EFFECT_ABILITY_SHIELD"
                )
                putHnsHoldEffectDescriptor(this, request, HnsItemSide.ATTACKER)
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
            if (request.defender.itemProvenance == CalcItemProvenance.BATTLE_EFFECTIVE) {
                request.defender.itemId?.let { put("hnsEffectiveItemId", it) }
            }
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
            val defenderOverride = request.defenderOverride
            val defenderTypes = request.hnsLiveBattleState?.defenderTypes ?: defenderOverride?.types
            if (defenderOverride != null || defenderTypes != null) {
                put("overrides", JSONObject().apply {
                    defenderTypes?.let { put("types", JSONArray(it)) }
                    defenderOverride?.let { override ->
                        put("baseStats", JSONObject().apply {
                            put("hp", override.baseStats.hp)
                            put("atk", override.baseStats.atk)
                            put("def", override.baseStats.def)
                            put("spa", override.baseStats.spa)
                            put("spd", override.baseStats.spd)
                            put("spe", override.baseStats.spe)
                        })
                    }
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
            request.hnsLiveBattleState?.let { live ->
                live.defenderHp?.let { put("hpAtHit", it) }
                live.defenderMaxHp?.let { put("maxHpAtHit", it) }
                live.defenderStatus1?.let { put("status1", it) }
                live.defenderSpeciesId?.let { put("hnsSpeciesId", it) }
                live.defenderNeutralizingGas?.let { put("hnsNeutralizingGas", it) }
                live.defenderPersistentVolatiles?.let { put("hnsGastroAcid", it.gastroAcid) }
                live.defenderPersonality?.let { put("hnsPersonality", it) }
                if (live.defenderGender != com.dualdex.pokemon.hns.HnsBattlerGender.UNKNOWN) {
                    put("hnsGender", live.defenderGender.name)
                }
                live.defenderTransformed?.let { put("hnsTransformed", it) }
                live.defenderEmbargo?.let { put("hnsEmbargo", it) }
                live.defenderMetronomeItemCounter?.let { put("hnsMetronomeItemCounter", it) }
                live.defenderTransformedMonSpecies?.let { put("hnsTransformedMonSpecies", it) }
                live.defenderBoosterEnergyActivated?.let { put("hnsBoosterEnergyActivated", it) }
                live.defenderParadoxBoostedStat?.let { put("hnsParadoxBoostedStat", it) }
                live.defenderVesselOfRuin?.let { put("hnsVesselOfRuin", it) }
                live.defenderSwordOfRuin?.let { put("hnsSwordOfRuin", it) }
                live.defenderTabletsOfRuin?.let { put("hnsTabletsOfRuin", it) }
                live.defenderBeadsOfRuin?.let { put("hnsBeadsOfRuin", it) }
                live.defenderIsFirstTurn?.let { put("hnsIsFirstTurn", it) }
            }
            if (request.typeSystem == "hns_2_0_5") {
                put(
                    "hnsAbilityShield",
                    HnsHoldEffectAuthority.forRequest(request, HnsItemSide.DEFENDER).effectiveHoldEffect ==
                        "HOLD_EFFECT_ABILITY_SHIELD"
                )
                putHnsHoldEffectDescriptor(this, request, HnsItemSide.DEFENDER)
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
        val pinnedHnsMove = if (request.typeSystem == "hns_2_0_5") {
            com.dualdex.pokemon.hns.HeartAndSoul205DataPack.getMoveByName(request.move.name)
        } else {
            null
        }
        val hnsFixedSingleHitMove = pinnedHnsMove?.let { move ->
            move.power > 0 && com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry.classify(move.id).category.isSupportedFixedSingleHit
        }
        val hnsMoveAuthority = if (request.typeSystem == "hns_2_0_5") {
            HnsMoveAuthority.forRequest(request, hnsFixedSingleHitMove)
        } else {
            null
        }
        val moveObj = JSONObject().apply {
            put("name", request.move.name)
            put("isCrit", request.move.isCrit)
            if (request.typeSystem == "hns_2_0_5") {
                // Group C metadata is looked up by the exact pinned move name -> move ID. The
                // request has no caller-owned flags/priority field, so fabricated JSON operands
                // cannot create or bypass an immunity.
                pinnedHnsMove?.let { move ->
                    val moveId = move.id
                    val hnsMoveFlags = com.dualdex.pokemon.hns.Hns205MoveEffects
                        .immunityFlagsById[moveId].orEmpty().toMutableSet()
                    when (hnsMoveAuthority?.soundMove) {
                        true -> hnsMoveFlags += "soundMove"
                        false, null -> hnsMoveFlags -= "soundMove"
                    }
                    put("hnsMoveId", moveId)
                    put("hnsMoveEffect", com.dualdex.pokemon.hns.Hns205MoveEffects.effectById[moveId])
                    put("hnsIsOrdinary", moveId in com.dualdex.pokemon.hns.Hns205MoveEffects.ordinaryMoveIds)
                    put("hnsFixedSingleHit", hnsFixedSingleHitMove == true)
                    put("hnsIsDrain", moveId in com.dualdex.pokemon.hns.Hns205MoveEffects.fixedSingleHitDrainMoveIds)
                    put("hnsMoveFlags", JSONArray(
                        hnsMoveFlags.sorted()
                    ))
                    put("hnsMoveAbilityFlags", JSONArray(
                        com.dualdex.pokemon.hns.Hns205MoveEffects.abilityMoveFlagsById[moveId].orEmpty().sorted()
                    ))
                    put("hnsUnknownPunching", com.dualdex.pokemon.hns.Hns205MoveEffects
                        .unknownAbilityMoveFlagsById[moveId]?.contains("punchingMove") == true)
                    com.dualdex.pokemon.hns.Hns205MoveEffects.makesContactById[moveId]?.let {
                        put("hnsMakesContact", it)
                    }
                    put("hnsUnknownContact", moveId in com.dualdex.pokemon.hns.Hns205MoveEffects.unknownContactMoveIds)
                    com.dualdex.pokemon.hns.Hns205MoveEffects.sheerForceAffectedById[moveId]?.let {
                        put("hnsSheerForceAffected", it)
                    }
                    put("hnsUnknownSheerForce", moveId in com.dualdex.pokemon.hns.Hns205MoveEffects.unknownSheerForceMoveIds)
                    com.dualdex.pokemon.hns.Hns205MoveEffects.unknownImmunityFlagsById[moveId]?.let {
                        put("hnsUnknownMoveFlags", JSONArray(it.sorted()))
                    }
                    com.dualdex.pokemon.hns.Hns205MoveEffects.unknownAbilityMoveFlagsById[moveId]?.let {
                        put("hnsUnknownMoveAbilityFlags", JSONArray(it.sorted()))
                    }
                    com.dualdex.pokemon.hns.Hns205MoveEffects.targetClassByMoveId[moveId]?.let {
                        put("hnsTargetClass", it)
                    }
                    HnsGroupCPolicy.effectivePriority(request, moveId)?.let {
                        put("effectivePriority", it)
                    }
                }
            }
            request.moveOverride?.let { override ->
                put("overrides", JSONObject().apply {
                    put("basePower", override.basePower)
                    put("type", hnsMoveAuthority?.effectiveType?.displayName ?: override.type)
                    (hnsMoveAuthority?.category?.displayName ?: override.category)?.let {
                        put("category", it)
                    }
                    hnsMoveAuthority?.ateBoost?.let { put("ateBoost", it) }
                })
            }
        }
        put("move", moveObj)

        // Field
        val fieldObj = JSONObject().apply {
            put("gameType", request.field.gameType)
            request.field.weather?.let { put("weather", it) }
            request.hnsLiveBattleState?.takeIf { it.weatherObserved }?.let {
                put("hnsWeatherWord", it.weatherWord)
            }
            // H&S terrain abilities read ctx->fieldStatuses. This operand comes only from the
            // boundary-owned live word after capability policy has neutralized ignored bits.
            request.hnsLiveBattleState?.fieldStatuses?.let {
                put("hnsFieldStatuses", it)
            }
            request.hnsLiveBattleState?.attackerTerrainApplicability?.let {
                put("hnsTerrainAttackerAffected", it == HnsTerrainApplicability.AFFECTED)
            }
            request.hnsLiveBattleState?.defenderTerrainApplicability?.let {
                put("hnsTerrainDefenderAffected", it == HnsTerrainApplicability.AFFECTED)
            }
            request.field.terrain?.let { put("terrain", it) }
            // Boundary-owned live target count (GetMoveTargetCount). Absent today because no reader
            // supplies it; when absent the H&S engine fails closed for Doubles spread moves.
            request.hnsLiveBattleState?.moveTargetCount?.let { put("targetCount", it) }
            request.hnsLiveBattleState?.doubles?.let { doubles ->
                put("hnsDoubles", JSONObject().apply {
                    put("helpingHand", doubles.helpingHand)
                    put("attackerPartnerAbility", doubles.attackerPartnerAbility)
                    put("defenderPartnerAbility", doubles.defenderPartnerAbility)
                    put("attackerPartnerSpecies", doubles.attackerPartnerSpecies)
                    put("defenderPartnerSpecies", doubles.defenderPartnerSpecies)
                    put("fieldAbilities", JSONArray(doubles.fieldAbilities.sorted()))
                    put("ruinFlags", doubles.ruinFlags)
                })
            }
            request.field.defenderSide?.let { side ->
                put("defenderSide", JSONObject().apply {
                    if (side.isReflect) put("isReflect", true)
                    if (side.isLightScreen) put("isLightScreen", true)
                })
            }
        }
        put("field", fieldObj)
    }.toString()

private fun putHnsHoldEffectDescriptor(
    target: JSONObject,
    request: DamageCalculationRequest,
    side: HnsItemSide
) {
    val resolution = HnsHoldEffectAuthority.forRequest(request, side)
    target.put("hnsHoldEffectState", resolution.state.name)
    target.put("hnsRawItemId", resolution.itemId ?: JSONObject.NULL)
    target.put("hnsEffectiveHoldEffect", resolution.effectiveHoldEffect ?: JSONObject.NULL)
    target.put("hnsHoldEffectParam", resolution.item?.holdEffectParam ?: JSONObject.NULL)
    target.put(
        "hnsItemType",
        resolution.itemId?.let(com.dualdex.pokemon.hns.HnsItemRegistry::itemTypeName) ?: JSONObject.NULL
    )
    val live = request.hnsLiveBattleState
    val speciesId = if (side == HnsItemSide.ATTACKER) live?.attackerSpeciesId else live?.defenderSpeciesId
    val transformed = if (side == HnsItemSide.ATTACKER) live?.attackerTransformed else live?.defenderTransformed
    val transformedSpecies = if (side == HnsItemSide.ATTACKER) {
        live?.attackerTransformedMonSpecies
    } else {
        live?.defenderTransformedMonSpecies
    }
    target.put("hnsSpeciesId", speciesId ?: JSONObject.NULL)
    target.put("hnsBaseSpeciesId", Hns205SpeciesMechanics.baseSpeciesId(speciesId) ?: JSONObject.NULL)
    val defenseSpecies = when (transformed) {
        true -> transformedSpecies
        false -> speciesId
        null -> null
    }
    target.put("hnsEvioliteCanEvolve", Hns205SpeciesMechanics.canEvolve(defenseSpecies) ?: JSONObject.NULL)
    if (side == HnsItemSide.DEFENDER) {
        val berry = HnsResistBerryAuthority.forRequest(request)
        target.put("hnsResistBerryState", berry.state.name)
        target.put("hnsResistBerryItemId", berry.itemId ?: JSONObject.NULL)
        target.put("hnsResistBerryModifierQ12", berry.modifierQ12 ?: JSONObject.NULL)
        target.put("hnsResistBerryRule", berry.rule)
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)
