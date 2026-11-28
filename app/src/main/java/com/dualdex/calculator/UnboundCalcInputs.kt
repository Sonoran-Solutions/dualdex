package com.dualdex.calculator

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.pokemon.SpeciesInfo
import com.dualdex.pokemon.unbound.UnboundDataPack
import com.dualdex.pokemon.unbound.UnboundSpeciesTable
import org.json.JSONObject

/** Numeric CFRU operands produced by the exact-ROM capability policy. */
class CalcUnboundOperands internal constructor(
    private val values: Map<String, Int>,
    private val attackerName: String,
    private val defenderName: String,
    private val moveName: String,
    private val defenderMaxHp: Int
) {
    internal fun toJson(): JSONObject = JSONObject()
        .put("typeSystem", UnboundCalcInputs.TYPE_SYSTEM)
        .put("field", JSONObject().put("gameType", CalcGameTypes.SINGLES))
        .put("unbound", JSONObject(values))
        .put("display", JSONObject()
            .put("attackerName", attackerName)
            .put("defenderName", defenderName)
            .put("moveName", moveName)
            .put("defenderMaxHP", defenderMaxHp))
}

/** Adapts reviewed ordinary-hit operands to the bounded schema in unbound-cfru.js. */
internal object UnboundCalcInputs {
    const val TYPE_SYSTEM = "unbound_cfru"

    // Exact-pack move IDs. Admitted as ordinary hits under the forecast's field assumptions.
    // This list excludes variable power, cross-stat routing, multiple hits, fixed damage and
    // special execution. Power, type and category are the pack's catalogue values.
    private val ordinaryMoves = setOf(
        1, 5, 7, 8, 9, 10, 15, 17, 33, 52, 53, 55, 56, 57, 58,
        84, 85, 98, 129, 157, 163, 231, 247, 337
    )
    private val typeIds = ((0..8) + (10..17) + 23).associateBy(PokemonType::fromGbaId)
    private val attackerAbilities = setOf(0, 37, 55, 62, 82, 85, 88, 99)
    private val defenderAbilities = setOf(0, 47, 97, 148, 248)
    private val abilityNames = (attackerAbilities + defenderAbilities).mapNotNull { id ->
        val ability = UnboundDataPack.getAbility(id) as? DeclaredAbility.Declared
        ability?.name?.lowercase()?.let { it to id }
    }.toMap()

    // The global catalogue supplies a National Dex identity only. Every lookup must resolve to
    // a unique mapped base-form ID in the pinned pack before its data may be used.
    private val baseIdByDex = UnboundSpeciesTable.NATIONAL_DEX.indices
        .filter { isBaseSpecies(it) && UnboundSpeciesTable.NATIONAL_DEX[it] > 0 }
        .groupBy { UnboundSpeciesTable.NATIONAL_DEX[it] }
        .mapValues { (_, ids) -> ids.singleOrNull() }

    private fun isBaseSpecies(id: Int): Boolean =
        UnboundSpeciesTable.FORM_CLASS.getOrNull(id) == UnboundSpeciesTable.FORM_BASE

    fun isSupportedAbilityId(id: Int): Boolean = id in attackerAbilities || id in defenderAbilities

    fun abilityId(name: String): Int? =
        if (name.equals("None", ignoreCase = true)) 0 else abilityNames[name.lowercase()]

    fun isNoItem(name: String?): Boolean =
        name == null || name.equals(CalcInputPreparation.NO_ITEM, ignoreCase = true)

    fun prepare(
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>
    ): CalcUnboundOperands? {
        fun refuse(reason: CalcLimitation): Nothing? {
            limitations += reason
            return null
        }

        if (request.field.gameType != CalcGameTypes.SINGLES || request.field.terrain != null) {
            return refuse(CalcLimitation.FIELD_CONDITION_NOT_MODELLED)
        }
        val weather = when (request.field.weather) {
            null -> 0
            "Rain" -> 1
            "Sun" -> 32
            else -> return refuse(CalcLimitation.FIELD_CONDITION_NOT_MODELLED)
        }
        val move = UnboundDataPack.getMoveByName(request.move.name)
            ?: return refuse(CalcLimitation.MOVE_NOT_IN_PINNED_DATA)
        if (request.attacker.unbound?.partyMoveIds?.let { move.id !in it } == true) {
            return refuse(CalcLimitation.MOVE_NOT_IN_PINNED_DATA)
        }
        val moveType = typeIds[move.type]
        if (move.id !in ordinaryMoves || move.category == MoveCategory.STATUS ||
            move.power !in 1..150 || moveType == null
        ) return refuse(CalcLimitation.UNBOUND_MOVE_NOT_MODELLED)

        val attacker = participant(request.attacker, attackerAbilities, limitations)
        val defender = participant(request.defender, defenderAbilities, limitations)
        if (attacker == null || defender == null) return null

        val side = request.field.defenderSide
        val screens = (if (side?.isReflect == true) 1 else 0) + (if (side?.isLightScreen == true) 2 else 0)
        return CalcUnboundOperands(
            values = mapOf(
                "level" to request.attacker.level,
                "power" to move.power,
                "category" to if (move.category == MoveCategory.PHYSICAL) 0 else 1,
                "move_type" to moveType,
                "attack" to attacker.stats.attack,
                "sp_attack" to attacker.stats.spAttack,
                "defense" to defender.stats.defense,
                "sp_defense" to defender.stats.spDefense,
                "attack_stage" to (attacker.boosts.atk + 6),
                "sp_attack_stage" to (attacker.boosts.spa + 6),
                "defense_stage" to (defender.boosts.def + 6),
                "sp_defense_stage" to (defender.boosts.spd + 6),
                "attacker_type_1" to attacker.types.first(),
                "attacker_type_2" to attacker.types.last(),
                "defender_type_1" to defender.types.first(),
                "defender_type_2" to defender.types.last(),
                "attacker_status" to attacker.status,
                "weather" to weather,
                "screens" to screens,
                "critical" to if (request.move.isCrit) 1 else 0,
                "attacker_ability" to attacker.ability,
                "defender_ability" to defender.ability,
                "attacker_hold_effect" to 0,
                "defender_hold_effect" to 0
            ),
            attackerName = attacker.name,
            defenderName = defender.name,
            moveName = move.name,
            defenderMaxHp = defender.maxHp
        )
    }

    private data class Participant(
        val name: String,
        val stats: CalcRawStats,
        val maxHp: Int,
        val types: List<Int>,
        val boosts: StatBlock,
        val status: Int,
        val ability: Int
    )

    private fun participant(
        input: CalcPokemonInput,
        abilities: Set<Int>,
        limitations: MutableSet<CalcLimitation>
    ): Participant? {
        fun refuse(reason: CalcLimitation): Nothing? {
            limitations += reason
            return null
        }

        val observed = input.unbound
        val live = input.origin == CalcInputOrigin.LIVE_READ
        val party = live && observed?.partyMoveIds != null
        if (live && (observed == null || input.boosts == null ||
                input.unknownFields.isNotEmpty() ||
                input.abilityId == null ||
                (!party && (observed.types == null || input.itemId == null)))
        ) return refuse(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)

        val speciesId = observed?.speciesId ?: SpeciesDatabase.getByName(input.species)?.id?.let(baseIdByDex::get)
        val species = speciesId?.takeIf(::isBaseSpecies)?.let(UnboundDataPack::getSpecies)
        if (species == null || input.species.trim().endsWith(UnboundDataPack.FORM_UNVERIFIED_SUFFIX, ignoreCase = true)) {
            return refuse(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA)
        }
        if (input.level !in CalcCapabilityPolicy.LEVEL_RANGE) return refuse(CalcLimitation.LEVEL_OUT_OF_RANGE)

        val boosts = input.boosts ?: StatBlock()
        if (boosts.hp != 0 || listOf(boosts.atk, boosts.def, boosts.spa, boosts.spd, boosts.spe)
                .any { it !in CalcCapabilityPolicy.BOOST_RANGE }
        ) return refuse(CalcLimitation.BOOSTS_OUT_OF_RANGE)

        val types = observed?.types ?: listOfNotNull(species.type1, species.type2)
        val ids = types.mapNotNull(typeIds::get)
        if (types.size !in 1..2 || ids.size != types.size) {
            return refuse(CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED)
        }

        val (stats, maxHp) = if (observed != null) observed.stats to observed.maxHp
            else benchmarkStats(input, species) ?: return refuse(CalcLimitation.STAT_VALUES_OUT_OF_RANGE)
        if (listOf(stats.attack, stats.spAttack, stats.defense, stats.spDefense, maxHp).any { it !in 1..999 } ||
            (input.curHP != null && input.curHP !in 1..maxHp)
        ) return refuse(CalcLimitation.STAT_VALUES_OUT_OF_RANGE)

        val status = if (party) observed?.partyStatus else when (input.status) {
            null -> 0L
            "brn" -> 16L
            else -> null
        }
        val statusWord = when (status) {
            0L -> 0
            16L -> 16
            else -> return refuse(CalcLimitation.STATUS_NOT_MODELLED)
        }

        if (input.itemOutOfDomain || !isNoItem(input.item) || (input.itemId ?: 0) != 0 ||
            (party && observed?.partyHeldItemId != 0)
        ) return refuse(CalcLimitation.UNBOUND_MODIFIER_NOT_MODELLED)
        val namedAbilityId = input.ability?.let(::abilityId)
        val ability = when {
            live -> input.abilityId ?: return refuse(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)
            input.abilityId != null -> input.abilityId
            input.ability != null -> namedAbilityId ?: return refuse(CalcLimitation.UNBOUND_MODIFIER_NOT_MODELLED)
            else -> 0
        }
        if (ability !in abilities || (input.ability != null && namedAbilityId != ability)) {
            return refuse(CalcLimitation.UNBOUND_MODIFIER_NOT_MODELLED)
        }
        if (party) limitations += CalcLimitation.UNBOUND_PARTY_OPERANDS_ASSUMED

        return Participant(species.name, stats, maxHp, ids, boosts, statusWord, ability)
    }

    private val natureNames = listOf(
        "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed",
        "Impish", "Lax", "Timid", "Hasty", "Serious", "Jolly", "Naive", "Modest", "Mild",
        "Quiet", "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky"
    )

    private fun benchmarkStats(input: CalcPokemonInput, species: SpeciesInfo): Pair<CalcRawStats, Int>? {
        val nature = natureNames.indexOfFirst { it.equals(input.nature ?: "Hardy", ignoreCase = true) }
        val ivs = input.ivs ?: StatBlock(31, 31, 31, 31, 31, 31)
        val evs = input.evs ?: StatBlock()
        if (nature < 0 ||
            listOf(ivs.hp, ivs.atk, ivs.def, ivs.spa, ivs.spd, ivs.spe).any { it !in CalcCapabilityPolicy.IV_RANGE } ||
            listOf(evs.hp, evs.atk, evs.def, evs.spa, evs.spd, evs.spe).any { it !in CalcCapabilityPolicy.EV_RANGE }
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
