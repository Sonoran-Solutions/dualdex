package com.dualdex.calculator

import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesInfo
import com.dualdex.romhack.RomHackProfile
import org.json.JSONArray
import org.json.JSONObject

/** Validated ordinary-hit Singles operands for the Expansion Tier-B UQ4.12 addon bundle. */
data class CalcExpansionTierBOperands internal constructor(
    internal val level: Int,
    internal val power: Int,
    internal val category: String,
    internal val moveType: String,
    internal val attack: Int,
    internal val spAttack: Int,
    internal val defense: Int,
    internal val spDefense: Int,
    internal val attackStage: Int,
    internal val spAttackStage: Int,
    internal val defenseStage: Int,
    internal val spDefenseStage: Int,
    internal val attackerTypes: List<String>,
    internal val defenderTypes: List<String>,
    internal val attackerBurned: Boolean,
    internal val weather: String,
    internal val isReflect: Boolean,
    internal val isLightScreen: Boolean,
    internal val isCrit: Boolean,
    internal val attackerName: String,
    internal val defenderName: String,
    internal val moveName: String,
    internal val defenderMaxHp: Int
) {
    internal fun toJson(): JSONObject = JSONObject()
        .put("typeSystem", TierBCalcInputs.EXPANSION_TYPE_SYSTEM)
        .put("field", JSONObject().put("gameType", CalcGameTypes.SINGLES))
        .put("expansion", JSONObject()
            .put("level", level)
            .put("power", power)
            .put("category", category)
            .put("moveType", moveType)
            .put("attack", attack)
            .put("spAttack", spAttack)
            .put("defense", defense)
            .put("spDefense", spDefense)
            .put("attackStage", attackStage)
            .put("spAttackStage", spAttackStage)
            .put("defenseStage", defenseStage)
            .put("spDefenseStage", spDefenseStage)
            .put("attackerTypes", JSONArray(attackerTypes))
            .put("defenderTypes", JSONArray(defenderTypes))
            .put("attackerBurned", attackerBurned)
            .put("weather", weather)
            .put("isReflect", isReflect)
            .put("isLightScreen", isLightScreen)
            .put("isCrit", isCrit))
        .put("display", JSONObject()
            .put("attackerName", attackerName)
            .put("defenderName", defenderName)
            .put("moveName", moveName)
            .put("defenderMaxHP", defenderMaxHp))
}

/**
 * Strict fail-closed operand validation and preparation for Tier-B ESTIMATED engines:
 * - [CalcRuleset.EXPANSION_TIER_B] -> `expansion_tierb_calc_bundle.js` (`typeSystem = "expansion_tierb"`)
 * - [CalcRuleset.CFRU_TIER_B] -> `unbound_calc_bundle.js` (`typeSystem = "unbound_cfru"`)
 *
 * Refuses unverified ROMs, Doubles, Status moves, zero/unknown/variable power, missing species or
 * moves, custom-species profiles, and unmodeled modifiers/fields with zero silent coercion.
 */
internal object TierBCalcInputs {
    const val EXPANSION_TYPE_SYSTEM = "expansion_tierb"

    private const val EXPANSION_MAX_POWER = 250
    private const val CFRU_MAX_POWER = 150

    /**
     * Strictly reviewed ordinary single-hit fixed-power move IDs (superset of
     * [UnboundCalcInputs]'s reviewed ordinary-hit set across all 18 modern types).
     *
     * Excludes multi-hit moves (e.g. Double Kick, Double Slap, Bonemerang), defense-swap moves
     * (Psyshock, Psystrike, Secret Sword), attack-swap moves (Foul Play, Body Press), fixed/level/
     * OHKO damage, weight/speed/HP/stat-scaled or variable-power moves, recoil/explosion/drain,
     * screen-breaking moves (Brick Break), always-crit moves, and dynamic/dual-type overrides.
     */
    private val ordinaryHitMoveIds: Set<Int> = setOf(
        1, 2, 5, 7, 8, 9, 10, 15, 17, 21, 22, 25,
        29, 33, 40, 44, 51, 52, 53, 55, 56, 57, 58, 59,
        60, 61, 62, 63, 64, 65, 70, 75, 84, 85, 87, 88,
        93, 94, 98, 122, 124, 126, 127, 129, 152, 157, 161, 163,
        172, 177, 181, 183, 185, 188, 189, 196, 209, 211, 221, 223,
        224, 225, 231, 232, 238, 242, 245, 246, 247, 249, 257, 276,
        295, 296, 299, 304, 305, 309, 310, 314, 315, 317, 318, 324,
        325, 326, 330, 332, 337, 341, 342, 345, 348, 351, 352, 354,
        359, 370, 395, 396, 398, 399, 400, 401, 402, 403, 404, 405,
        406, 408, 410, 411, 412, 414, 416, 418, 420, 421, 422, 423,
        424, 425, 426, 427, 428, 430, 434, 435, 436, 437, 438, 439,
        440, 441, 442, 443, 444, 453, 454, 460, 466, 482, 488, 490,
        503, 522, 529, 534, 539, 555, 556, 572, 574, 583, 584, 585,
        586, 595, 605
    )

    /** Exact 18 modern types (excludes Stellar and any unknown/custom type). */
    private val modernTypes: Set<PokemonType> = setOf(
        PokemonType.NORMAL, PokemonType.FIGHTING, PokemonType.FLYING, PokemonType.POISON,
        PokemonType.GROUND, PokemonType.ROCK, PokemonType.BUG, PokemonType.GHOST,
        PokemonType.STEEL, PokemonType.FIRE, PokemonType.WATER, PokemonType.GRASS,
        PokemonType.ELECTRIC, PokemonType.PSYCHIC, PokemonType.ICE, PokemonType.DRAGON,
        PokemonType.DARK, PokemonType.FAIRY
    )

    private val cfruTypeIds: Map<PokemonType, Int> = ((0..8) + (10..17) + 23)
        .associateBy(PokemonType::fromGbaId)

    private val natureNames = listOf(
        "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed",
        "Impish", "Lax", "Timid", "Hasty", "Serious", "Jolly", "Naive", "Modest", "Mild",
        "Quiet", "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky"
    )

    fun isNeutralAbility(name: String?): Boolean =
        name == null || name.trim().equals("None", ignoreCase = true)

    fun isNoItem(name: String?): Boolean =
        name == null || name.trim().equals(CalcInputPreparation.NO_ITEM, ignoreCase = true)

    /**
     * Live reads on Tier-B hacks carry an unknown ability because no per-hack gBattleMons
     * effective-ability reader is required for Tier-B. Only ABILITY may be unknown; missing
     * stat stages, status, item, or other fields still fail closed.
     */
    fun hasOnlyAssumedLiveAbilities(request: DamageCalculationRequest): Boolean {
        val live = listOf(request.attacker, request.defender).filter { it.origin == CalcInputOrigin.LIVE_READ }
        return live.isNotEmpty() &&
            live.any { CalcInputField.ABILITY in it.unknownFields } &&
            live.all { participant ->
                participant.unknownFields.all { it == CalcInputField.ABILITY } &&
                    isNeutralAbility(participant.ability) &&
                    (participant.abilityId == null || participant.abilityId == 0)
            }
    }

    fun prepareExpansion(
        profile: RomHackProfile,
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>
    ): CalcExpansionTierBOperands? {
        val validated = validateCommon(profile, request, EXPANSION_MAX_POWER, limitations) ?: return null
        val weatherName = when (validated.weather) {
            TierBWeather.NONE -> "None"
            TierBWeather.RAIN -> "Rain"
            TierBWeather.SUN -> "Sun"
        }
        return CalcExpansionTierBOperands(
            level = request.attacker.level,
            power = validated.movePower,
            category = if (validated.isPhysical) "Physical" else "Special",
            moveType = validated.moveType.displayName,
            attack = validated.attacker.stats.attack,
            spAttack = validated.attacker.stats.spAttack,
            defense = validated.defender.stats.defense,
            spDefense = validated.defender.stats.spDefense,
            attackStage = validated.attacker.boosts.atk,
            spAttackStage = validated.attacker.boosts.spa,
            defenseStage = validated.defender.boosts.def,
            spDefenseStage = validated.defender.boosts.spd,
            attackerTypes = validated.attacker.types.map { it.displayName },
            defenderTypes = validated.defender.types.map { it.displayName },
            attackerBurned = validated.attacker.burned,
            weather = weatherName,
            isReflect = validated.isReflect,
            isLightScreen = validated.isLightScreen,
            isCrit = request.move.isCrit,
            attackerName = validated.attacker.name,
            defenderName = validated.defender.name,
            moveName = validated.moveName,
            defenderMaxHp = validated.defender.maxHp
        )
    }

    fun prepareCfru(
        profile: RomHackProfile,
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>
    ): CalcUnboundOperands? {
        val validated = validateCommon(profile, request, CFRU_MAX_POWER, limitations) ?: return null
        val moveTypeId = cfruTypeIds[validated.moveType]
        val atkTypeIds = validated.attacker.types.mapNotNull(cfruTypeIds::get)
        val defTypeIds = validated.defender.types.mapNotNull(cfruTypeIds::get)
        if (moveTypeId == null || atkTypeIds.size != validated.attacker.types.size ||
            defTypeIds.size != validated.defender.types.size
        ) {
            limitations += CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED
            return null
        }
        val weatherCode = when (validated.weather) {
            TierBWeather.NONE -> 0
            TierBWeather.RAIN -> 1
            TierBWeather.SUN -> 32
        }
        val screens = (if (validated.isReflect) 1 else 0) + (if (validated.isLightScreen) 2 else 0)
        return CalcUnboundOperands(
            values = mapOf(
                "level" to request.attacker.level,
                "power" to validated.movePower,
                "category" to if (validated.isPhysical) 0 else 1,
                "move_type" to moveTypeId,
                "attack" to validated.attacker.stats.attack,
                "sp_attack" to validated.attacker.stats.spAttack,
                "defense" to validated.defender.stats.defense,
                "sp_defense" to validated.defender.stats.spDefense,
                "attack_stage" to (validated.attacker.boosts.atk + 6),
                "sp_attack_stage" to (validated.attacker.boosts.spa + 6),
                "defense_stage" to (validated.defender.boosts.def + 6),
                "sp_defense_stage" to (validated.defender.boosts.spd + 6),
                "attacker_type_1" to atkTypeIds.first(),
                "attacker_type_2" to atkTypeIds.last(),
                "defender_type_1" to defTypeIds.first(),
                "defender_type_2" to defTypeIds.last(),
                "attacker_status" to if (validated.attacker.burned) 16 else 0,
                "weather" to weatherCode,
                "screens" to screens,
                "critical" to if (request.move.isCrit) 1 else 0,
                "attacker_ability" to 0,
                "defender_ability" to 0,
                "attacker_hold_effect" to 0,
                "defender_hold_effect" to 0
            ),
            attackerName = validated.attacker.name,
            defenderName = validated.defender.name,
            moveName = validated.moveName,
            defenderMaxHp = validated.defender.maxHp
        )
    }

    private enum class TierBWeather { NONE, RAIN, SUN }

    private data class ValidatedParticipant(
        val name: String,
        val stats: CalcRawStats,
        val maxHp: Int,
        val types: List<PokemonType>,
        val boosts: StatBlock,
        val burned: Boolean
    )

    private data class ValidatedCommon(
        val attacker: ValidatedParticipant,
        val defender: ValidatedParticipant,
        val moveName: String,
        val movePower: Int,
        val moveType: PokemonType,
        val isPhysical: Boolean,
        val weather: TierBWeather,
        val isReflect: Boolean,
        val isLightScreen: Boolean
    )

    private fun validateCommon(
        profile: RomHackProfile,
        request: DamageCalculationRequest,
        maxPower: Int,
        limitations: MutableSet<CalcLimitation>
    ): ValidatedCommon? {
        fun refuse(reason: CalcLimitation): Nothing? {
            limitations += reason
            return null
        }

        if (profile.customSpecies.isNotEmpty()) {
            return refuse(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA)
        }

        if (request.field.gameType != CalcGameTypes.SINGLES || !request.field.terrain.isNullOrBlank()) {
            return refuse(CalcLimitation.FIELD_CONDITION_NOT_MODELLED)
        }

        val weather = when (request.field.weather) {
            null -> TierBWeather.NONE
            "Rain" -> TierBWeather.RAIN
            "Sun" -> TierBWeather.SUN
            else -> return refuse(CalcLimitation.FIELD_CONDITION_NOT_MODELLED)
        }

        val pack = GameDataPackRegistry.getForProfile(profile)
        val move = pack.getMoveByName(request.move.name)
            ?: return refuse(CalcLimitation.MOVE_NOT_IN_PINNED_DATA)

        // Strictly gate on the reviewed ordinary single-hit allowlist; status, zero-power,
        // variable-power, multi-hit, defense-swap, recoil, and unreviewed moves fail closed.
        if (move.id !in ordinaryHitMoveIds || move.category == MoveCategory.STATUS || move.power !in 2..maxPower) {
            return refuse(CalcLimitation.TIER_B_MOVE_NOT_MODELLED)
        }
        if (move.type !in modernTypes) {
            return refuse(CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED)
        }

        val attacker = validateParticipant(request.attacker, pack, limitations)
        val defender = validateParticipant(request.defender, pack, limitations)
        if (attacker == null || defender == null) return null

        val side = request.field.defenderSide
        return ValidatedCommon(
            attacker = attacker,
            defender = defender,
            moveName = move.name,
            movePower = move.power,
            moveType = move.type,
            isPhysical = move.category == MoveCategory.PHYSICAL,
            weather = weather,
            isReflect = side?.isReflect == true,
            isLightScreen = side?.isLightScreen == true
        )
    }

    private fun validateParticipant(
        input: CalcPokemonInput,
        pack: com.dualdex.pokemon.GameDataPack,
        limitations: MutableSet<CalcLimitation>
    ): ValidatedParticipant? {
        fun refuse(reason: CalcLimitation): Nothing? {
            limitations += reason
            return null
        }

        val observed = input.unbound
        val isLive = input.origin == CalcInputOrigin.LIVE_READ
        if (isLive && (input.boosts == null ||
                input.unknownFields.any { it != CalcInputField.ABILITY })
        ) {
            return refuse(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)
        }

        val species = if (observed != null) {
            val byId = pack.getSpecies(observed.speciesId)
            if (byId == null || !byId.name.equals(input.species.trim(), ignoreCase = true)) {
                return refuse(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA)
            }
            byId
        } else {
            pack.getSpeciesByName(input.species)
                ?: return refuse(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA)
        }

        if (input.level !in CalcCapabilityPolicy.LEVEL_RANGE) {
            return refuse(CalcLimitation.LEVEL_OUT_OF_RANGE)
        }

        val boosts = input.boosts ?: StatBlock()
        if (boosts.hp != 0 ||
            listOf(boosts.atk, boosts.def, boosts.spa, boosts.spd, boosts.spe)
                .any { it !in CalcCapabilityPolicy.BOOST_RANGE }
        ) {
            return refuse(CalcLimitation.BOOSTS_OUT_OF_RANGE)
        }

        val types = observed?.types ?: listOfNotNull(species.type1, species.type2)
        if (types.size !in 1..2 || types.any { it !in modernTypes }) {
            return refuse(CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED)
        }

        val (stats, maxHp) = if (observed != null) {
            observed.stats to observed.maxHp
        } else {
            computeStats(input, species) ?: return refuse(CalcLimitation.STAT_VALUES_OUT_OF_RANGE)
        }
        if (listOf(stats.attack, stats.spAttack, stats.defense, stats.spDefense, maxHp)
                .any { it !in 1..999 } ||
            (input.curHP != null && input.curHP !in 1..maxHp)
        ) {
            return refuse(CalcLimitation.STAT_VALUES_OUT_OF_RANGE)
        }

        val burned = when {
            observed?.partyStatus != null -> when (observed.partyStatus) {
                0L -> if (input.status == null) false else return refuse(CalcLimitation.STATUS_NOT_MODELLED)
                16L -> if (input.status == "brn") true else return refuse(CalcLimitation.STATUS_NOT_MODELLED)
                else -> return refuse(CalcLimitation.STATUS_NOT_MODELLED)
            }
            input.status == null -> false
            input.status == "brn" -> true
            else -> return refuse(CalcLimitation.STATUS_NOT_MODELLED)
        }

        if (!isNeutralAbility(input.ability) || (input.abilityId != null && input.abilityId != 0)) {
            return refuse(CalcLimitation.TIER_B_MODIFIER_NOT_MODELLED)
        }
        if (input.itemOutOfDomain || !isNoItem(input.item) ||
            (input.itemId != null && input.itemId != 0) ||
            (observed?.partyHeldItemId != null && observed.partyHeldItemId != 0)
        ) {
            return refuse(CalcLimitation.TIER_B_MODIFIER_NOT_MODELLED)
        }

        return ValidatedParticipant(
            name = species.name,
            stats = stats,
            maxHp = maxHp,
            types = types,
            boosts = boosts,
            burned = burned
        )
    }

    private fun computeStats(input: CalcPokemonInput, species: SpeciesInfo): Pair<CalcRawStats, Int>? {
        val nature = natureNames.indexOfFirst { it.equals(input.nature ?: "Hardy", ignoreCase = true) }
        val ivs = input.ivs ?: StatBlock(31, 31, 31, 31, 31, 31)
        val evs = input.evs ?: StatBlock()
        if (nature < 0 ||
            listOf(ivs.hp, ivs.atk, ivs.def, ivs.spa, ivs.spd, ivs.spe)
                .any { it !in CalcCapabilityPolicy.IV_RANGE } ||
            listOf(evs.hp, evs.atk, evs.def, evs.spa, evs.spd, evs.spe)
                .any { it !in CalcCapabilityPolicy.EV_RANGE }
        ) return null

        fun scaled(base: Int, iv: Int, ev: Int): Int = (2 * base + iv + ev / 4) * input.level / 100
        fun stat(base: Int, iv: Int, ev: Int, index: Int): Int {
            val raised = nature / 5
            val lowered = nature % 5
            val modifier = if (raised == lowered) 100 else when (index) {
                raised -> 110
                lowered -> 90
                else -> 100
            }
            return (scaled(base, iv, ev) + 5) * modifier / 100
        }

        val hp = if (species.baseHP == 1) 1 else scaled(species.baseHP, ivs.hp, evs.hp) + input.level + 10
        return CalcRawStats(
            attack = stat(species.baseAtk, ivs.atk, evs.atk, 0),
            defense = stat(species.baseDef, ivs.def, evs.def, 1),
            speed = stat(species.baseSpe, ivs.spe, evs.spe, 2),
            spAttack = stat(species.baseSpA, ivs.spa, evs.spa, 3),
            spDefense = stat(species.baseSpD, ivs.spd, evs.spd, 4)
        ) to hp
    }
}
