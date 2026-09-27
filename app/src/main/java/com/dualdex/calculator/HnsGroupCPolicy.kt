package com.dualdex.calculator

import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.pokemon.hns.Hns205MoveEffects
import com.dualdex.pokemon.hns.HnsItemRegistry

/** Request-local integrity gates for the Group C immunity layer. */
internal object HnsGroupCPolicy {
    private const val SOUNDPROOF = 43
    private const val BULLETPROOF = 171
    private const val WIND_RIDER = 274
    private val priorityBlockers = setOf(214, 219, 296)
    internal val moldBreakerAbilityIds = setOf(104, 163, 164)
    private val moldBreakerFamilies = moldBreakerAbilityIds
    private val abilityShieldItem = 758
    private const val ironBallItem = 484
    private const val ringTargetItem = 499

    /** Resolve priority only when every pinned dynamic branch can be decided from request data. */
    fun effectivePriority(request: DamageCalculationRequest, moveId: Int): Int? {
        if (moveId in Hns205MoveEffects.unknownPriorityMoveIds) return null
        val live = request.hnsLiveBattleState ?: return null
        // The exact #88 phase callback is HandleTurnActionSelectionState. TurnValuesCleanUp has
        // already cleared the previous turn's transient Quash flag before that point; without
        // this phase proof the static move priority is not necessarily the effective priority.
        if (live.switchInEventsSettled != true) return null
        var priority = Hns205MoveEffects.basePriorityById[moveId] ?: return null
        val type = PokemonType.fromString(request.moveOverride?.type) ?: return null
        val attackerAbility = abilityId(request.attacker)

        // GetBattleMovePriority: Gale Wings adds one to Flying moves when the live attacker is at
        // full HP in the pinned GEN_LATEST configuration. It is the only matching damage-move
        // ability branch; Prankster is status-only and Triage's healing moves fail the ordinary
        // move gate. Grassy Glide is handled only when the live field word proves no terrain.
        if (attackerAbility == 177 && type == PokemonType.FLYING) {
            val hp = live.attackerHp ?: return null
            val maxHp = live.attackerMaxHp ?: return null
            if (maxHp <= 0 || hp !in 0..maxHp) return null
            if (hp == maxHp) priority++
        }
        if (attackerAbility == 261 &&
            "healingMove" in Hns205MoveEffects.unknownImmunityFlagsById[moveId].orEmpty()
        ) return null
        if (attackerAbility == 261 &&
            "healingMove" in Hns205MoveEffects.immunityFlagsById[moveId].orEmpty()
        ) priority += 3
        if (Hns205MoveEffects.effectById[moveId] == "EFFECT_GRASSY_GLIDE") {
            val field = request.hnsLiveBattleState?.fieldStatuses?.let(com.dualdex.pokemon.hns.HnsFieldState::decode)
                ?: return null
            if (!field.fullyDecoded) return null
            if (field.has(com.dualdex.pokemon.hns.HnsFieldStatus.GRASSY_TERRAIN)) priority++
        }
        return priority
    }

    /** Return hard blockers where missing move metadata or Mold Breaker would hide an immunity. */
    fun integrityLimitations(request: DamageCalculationRequest): Set<CalcLimitation> {
        if (request.typeSystem != "hns_2_0_5") return emptySet()
        val move = HeartAndSoul205DataPack.getMoveByName(request.move.name) ?: return emptySet()
        val moveId = move.id
        val moveType = PokemonType.fromString(request.moveOverride?.type) ?: return emptySet()
        val defenderAbility = abilityId(request.defender)
        val attackerAbility = abilityId(request.attacker)
        val defenderItem = itemId(request.defender)
        val flags = Hns205MoveEffects.immunityFlagsById[moveId].orEmpty()
        val unknownFlags = Hns205MoveEffects.unknownImmunityFlagsById[moveId].orEmpty()
        val damagingOrdinary = Hns205MoveEffects.ordinaryMoveIds.contains(moveId) && move.power > 0
        if (!damagingOrdinary) return emptySet() // the independent move gate already refuses it

        val matchingFlag = when (defenderAbility) {
            SOUNDPROOF -> "soundMove"
            BULLETPROOF -> "ballisticMove"
            WIND_RIDER -> "windMove"
            else -> null
        }
        if (matchingFlag != null && matchingFlag in unknownFlags && matchingFlag !in flags) {
            return setOf(CalcLimitation.HNS_IMMUNITY_CONTEXT_UNVERIFIED)
        }

        if (defenderAbility in priorityBlockers) {
            val targetClass = Hns205MoveEffects.targetClassByMoveId[moveId]
                ?: return setOf(CalcLimitation.HNS_IMMUNITY_CONTEXT_UNVERIFIED)
            if (targetClass != Hns205MoveEffects.SpreadTargetClass.TARGET_FIELD &&
                targetClass != Hns205MoveEffects.SpreadTargetClass.TARGET_OPPONENTS_FIELD &&
                effectivePriority(request, moveId) == null
            ) return setOf(CalcLimitation.HNS_IMMUNITY_CONTEXT_UNVERIFIED)
        }

        val moveFlagModelsSuppression = "ignoresTargetAbility" in flags && defenderItem != abilityShieldItem
        if (attackerAbility in moldBreakerFamilies && defenderAbility != null && !moveFlagModelsSuppression &&
            defenderAbilityWouldChangeHit(request, defenderAbility, moveType, flags, defenderItem) &&
            defenderItem != abilityShieldItem
        ) {
            return setOf(CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED)
        }
        return emptySet()
    }

    private fun defenderAbilityWouldChangeHit(
        request: DamageCalculationRequest,
        abilityId: Int,
        moveType: PokemonType,
        flags: Set<String>,
        defenderItemId: Int?
    ): Boolean {
        // A suppressed ability cannot change a hit that the pinned type chart already
        // guarantees will miss. Ring Target and Iron Ball are included by this resolver, so
        // a rewritten chart zero still proceeds to the ability-specific check below.
        if (typeEffectiveness(request, moveType) == 0.0) return false
        val typeMatch = when (abilityId) {
            10, 31, 78 -> moveType == PokemonType.ELECTRIC
            11, 114, 87 -> moveType == PokemonType.WATER || (abilityId == 87 && moveType == PokemonType.FIRE)
            157 -> moveType == PokemonType.GRASS
            297 -> moveType == PokemonType.GROUND
            273, 18 -> moveType == PokemonType.FIRE
            26 -> moveType == PokemonType.GROUND && defenderItemId != ironBallItem
            25 -> typeEffectiveness(request, moveType)?.let { it <= 1.0 } ?: true
            SOUNDPROOF -> "soundMove" in flags
            BULLETPROOF -> "ballisticMove" in flags
            WIND_RIDER -> "windMove" in flags
            in priorityBlockers -> effectivePriority(
                request, HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return true
            )?.let { it > 0 } ?: true
            else -> false
        }
        return typeMatch
    }

    /** Pinned H&S type chart plus Ring Target's per-cell zero-to-neutral rewrite. */
    private fun typeEffectiveness(request: DamageCalculationRequest, moveType: PokemonType): Double? {
        val pack = HeartAndSoul205DataPack
        val liveTypes = request.hnsLiveBattleState?.defenderTypes
        val rawTypes = liveTypes ?: request.defenderOverride?.types ?: run {
            val species = pack.getSpeciesByName(request.defender.species) ?: return null
            listOfNotNull(species.type1.displayName, species.type2?.displayName)
        }
        if (rawTypes.isEmpty()) return null
        val parsed = rawTypes.map { PokemonType.fromString(it) ?: return null }
        val item = itemId(request.defender)
        if (moveType == PokemonType.GROUND && item == ironBallItem && PokemonType.FLYING in parsed) return 1.0
        return parsed.fold(1.0) { acc, type ->
            val cell = pack.getEffectiveness(moveType, type)
            acc * if (cell == 0.0 && item == ringTargetItem) 1.0 else cell
        }
    }

    private fun abilityId(input: CalcPokemonInput): Int? = input.abilityId
        ?: input.ability?.let { com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(it).abilityId }

    private fun itemId(input: CalcPokemonInput): Int? = input.itemId
        ?: input.item?.let(HnsItemRegistry::resolveIdByName)
}
