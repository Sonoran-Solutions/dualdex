package com.dualdex.calculator

import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.pokemon.hns.Hns205MoveEffects
import com.dualdex.pokemon.hns.HnsItemRegistry
import com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry

/** Request-local integrity gates for the Group C immunity layer. */
internal object HnsGroupCPolicy {
    private const val SOUNDPROOF = 43
    private const val PUNK_ROCK = 244
    private const val BULLETPROOF = 171
    private const val WIND_RIDER = 274
    private val priorityBlockers = setOf(214, 219, 296)
    internal val moldBreakerAbilityIds = setOf(104, 163, 164)
    private val moldBreakerFamilies = moldBreakerAbilityIds
    internal const val ABILITY_SHIELD_ITEM_ID = 758
    private const val abilityShieldItem = ABILITY_SHIELD_ITEM_ID
    // Pinned abilities.h breakable flags for supported defender damage branches; Heatproof,
    // Water Bubble, Dry Skin, Filter, Solid Rock, Multiscale, Punk Rock, and Ice Scales can be
    // suppressed by either Mold Breaker or a literal ignoresTargetAbility move flag, while Prism
    // Armor/Shadow Shield remain effective through both bypass sources.
    // Audit witnesses: filter_mold_breaker_unshielded, solid_rock_mold_breaker_unshielded,
    // multiscale_mold_breaker_unshielded, ice_scales_mold_breaker_unshielded,
    // fur_coat_mold_breaker_unshielded, fur_coat_ability_shield_preserves,
    // fur_coat_literal_bypass_suppressed,
    // prism_armor_mold_breaker_preserves, shadow_shield_mold_breaker_preserves,
    // filter_ability_shield_preserves, solid_rock_ability_shield_preserves,
    // multiscale_ability_shield_preserves, ice_scales_ability_shield_preserves.
    private val defenderDamageAbilitiesBreakableByMoldBreaker = setOf(63, 85, 87, 111, 116, 122, 136, 169, 199, 218, 244, 246)
    private val finalModifierAbilitiesNotBreakableByMoldBreaker = setOf(231, 232)
    private val ironBallItem = HnsItemRegistry.resolveIdByName("Iron Ball")
    private val ringTargetItem = HnsItemRegistry.resolveIdByName("Ring Target")

    /** Resolve priority only when every pinned dynamic branch can be decided from request data. */
    fun effectivePriority(request: DamageCalculationRequest, moveId: Int): Int? {
        if (moveId in Hns205MoveEffects.unknownPriorityMoveIds) return null
        val live = request.hnsLiveBattleState ?: return null
        // The exact #88 phase callback is HandleTurnActionSelectionState. TurnValuesCleanUp has
        // already cleared the previous turn's transient Quash flag before that point; without
        // this phase proof the static move priority is not necessarily the effective priority.
        if (live.switchInEventsSettled != true) return null
        var priority = Hns205MoveEffects.basePriorityById[moveId] ?: return null
        val type = HnsMoveAuthority.forRequest(request, fixedSingleHitDamageMove(moveId)).effectiveType ?: return null
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

    /** Return hard blockers where missing move metadata or Mold Breaker would hide a target effect. */
    fun integrityLimitations(request: DamageCalculationRequest): Set<CalcLimitation> {
        if (request.typeSystem != "hns_2_0_5") return emptySet()
        val move = HeartAndSoul205DataPack.getMoveByName(request.move.name) ?: return emptySet()
        val moveId = move.id
        val moveAuthority = HnsMoveAuthority.forRequest(request, fixedSingleHitDamageMove(moveId))
        val moveType = moveAuthority.effectiveType ?: return emptySet()
        val defenderAbility = abilityId(request.defender)
        val attackerAbility = abilityId(request.attacker)
        val defenderItem = itemId(request.defender)
        val flags = Hns205MoveEffects.immunityFlagsById[moveId].orEmpty()
        val unknownFlags = Hns205MoveEffects.unknownImmunityFlagsById[moveId].orEmpty()
        val damagingOrdinary = HnsMoveMechanicsRegistry.classify(moveId).category.isSupportedFixedSingleHit && move.power > 0
        if (!damagingOrdinary) return emptySet() // the independent move gate already refuses it

        val matchingFlag = when (defenderAbility) {
            SOUNDPROOF -> "soundMove"
            BULLETPROOF -> "ballisticMove"
            WIND_RIDER -> "windMove"
            else -> null
        }
        if (defenderAbility == SOUNDPROOF && moveAuthority.soundMove == null) {
            return setOf(CalcLimitation.HNS_IMMUNITY_CONTEXT_UNVERIFIED)
        }
        if (matchingFlag != null && matchingFlag != "soundMove" &&
            matchingFlag in unknownFlags && matchingFlag !in flags
        ) {
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

        val abilityShieldActive = if (defenderItem == abilityShieldItem) {
            HnsHoldEffectAuthority.abilityShieldActiveIgnoringAbilityForRequest(request, HnsItemSide.DEFENDER)
        } else false
        if (defenderItem == abilityShieldItem && abilityShieldActive == null &&
            attackerAbility in moldBreakerFamilies && defenderAbility != null
        ) return setOf(CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED)
        val moveFlagModelsSuppression = "ignoresTargetAbility" in flags && abilityShieldActive != true
        val defenderFinalAbilityIsPinnedUnbreakable = defenderAbility in finalModifierAbilitiesNotBreakableByMoldBreaker
        val defenderFinalAbilityIsPinnedBreakable = defenderAbility in defenderDamageAbilitiesBreakableByMoldBreaker
        if (attackerAbility in moldBreakerFamilies && defenderAbility != null && !moveFlagModelsSuppression &&
            defenderAbilityWouldChangeHit(
                request, defenderAbility, moveType, flags, moveAuthority.soundMove
            ) &&
            abilityShieldActive != true &&
            (defenderFinalAbilityIsPinnedBreakable || !defenderFinalAbilityIsPinnedUnbreakable)
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
        soundMove: Boolean?
    ): Boolean {
        // A suppressed ability cannot change a hit that the pinned type chart already
        // guarantees will miss. Ring Target and Iron Ball are included by this resolver, so
        // a rewritten chart zero still proceeds to the ability-specific check below.
        if (typeEffectiveness(request, moveType) == 0.0) return false
        val typeMatch = when (abilityId) {
            10, 31, 78 -> moveType == PokemonType.ELECTRIC
            11, 114 -> moveType == PokemonType.WATER
            87 -> moveType == PokemonType.WATER || moveType == PokemonType.FIRE
            85, 199 -> moveType == PokemonType.FIRE
            157 -> moveType == PokemonType.GRASS
            297 -> moveType == PokemonType.GROUND
            273, 18 -> moveType == PokemonType.FIRE
            26 -> moveType == PokemonType.GROUND &&
                activeHoldEffect(request, HnsItemSide.DEFENDER, "HOLD_EFFECT_IRON_BALL") != true
            25 -> typeEffectiveness(request, moveType)?.let { it <= 1.0 } ?: true
            SOUNDPROOF -> soundMove == true
            PUNK_ROCK -> soundMove == true
            111, 116, 232 -> typeEffectiveness(request, moveType)?.let { it >= 2.0 } ?: true
            136, 231 -> {
                val hp = request.hnsLiveBattleState?.defenderHp
                val maxHp = request.hnsLiveBattleState?.defenderMaxHp
                when {
                    hp == null || maxHp == null || maxHp <= 0 || hp !in 1..maxHp -> true
                    else -> hp == maxHp
                }
            }
            63 -> marvelScaleWouldChangeHit(request)
            122 -> flowerGiftWouldChangeHit(request)
            169 -> furCoatWouldChangeHit(request)
            218 -> fluffyWouldChangeHit(request)
            246 -> {
                val moveId = HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return true
                HnsMoveAuthority.forRequest(request, fixedSingleHitDamageMove(moveId)).category
                    ?.let { it == com.dualdex.pokemon.MoveCategory.SPECIAL } ?: true
            }
            BULLETPROOF -> "ballisticMove" in flags
            WIND_RIDER -> "windMove" in flags
            in priorityBlockers -> effectivePriority(
                request, HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return true
            )?.let { it > 0 } ?: true
            else -> false
        }
        return typeMatch
    }

    private fun furCoatWouldChangeHit(request: DamageCalculationRequest): Boolean {
        val live = request.hnsLiveBattleState ?: return true
        val field = live.fieldStatuses ?: return true
        val wonderRoom = com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM
        if (field and wonderRoom != 0) return true
        val moveId = com.dualdex.pokemon.hns.HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id
            ?: return true
        val category = HnsMoveAuthority.forRequest(request, fixedSingleHitDamageMove(moveId)).category
        return category == null || category == com.dualdex.pokemon.MoveCategory.PHYSICAL
    }

    private fun marvelScaleWouldChangeHit(request: DamageCalculationRequest): Boolean {
        val live = request.hnsLiveBattleState ?: return true
        val field = live.fieldStatuses ?: return true
        if (field and com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM != 0) return true
        val id = HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return true
        if (HnsMoveAuthority.forRequest(request, fixedSingleHitDamageMove(id)).category != com.dualdex.pokemon.MoveCategory.PHYSICAL) return false
        val status = live.defenderStatus1 ?: return true
        if (status and 0x1fff.inv() != 0) return true
        return status and 0x10ff != 0
    }

    private fun flowerGiftWouldChangeHit(request: DamageCalculationRequest): Boolean {
        val live = request.hnsLiveBattleState ?: return true
        val species = live.defenderSpeciesId ?: return true
        if (species != com.dualdex.pokemon.hns.HnsAbilityAuditData.CHERRIM_SUNSHINE_SPECIES_ID) return false
        val id = HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return true
        val authority = HnsMoveAuthority.forRequest(request, fixedSingleHitDamageMove(id))
        if (authority.category == null) return true
        if (authority.category == com.dualdex.pokemon.MoveCategory.PHYSICAL) return false
        val field = live.fieldStatuses ?: return true
        if (field and com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM != 0) return true
        val weather = live.weatherWord.takeIf { live.weatherObserved } ?: return true
        if (weather and com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN == 0) return false
        if ((live.attackerHp ?: return true) <= 0 || (live.defenderHp ?: return true) <= 0) return true
        if (abilityId(request.attacker) in setOf(13, 76) || abilityId(request.defender) in setOf(13, 76)) return false
        return activeHoldEffect(request, HnsItemSide.DEFENDER, "HOLD_EFFECT_UTILITY_UMBRELLA") != true
    }

    private fun fluffyWouldChangeHit(request: DamageCalculationRequest): Boolean {
        val id = HeartAndSoul205DataPack.getMoveByName(request.move.name)?.id ?: return true
        val type = HnsMoveAuthority.forRequest(request, fixedSingleHitDamageMove(id)).effectiveType ?: return true
        val live = request.hnsLiveBattleState ?: return true
        val contact = HnsContactRules.assess(
            id, fixedSingleHitDamageMove(id), abilityId(request.attacker),
            request.attacker.origin == CalcInputOrigin.LIVE_READ && abilityId(request.attacker) != null,
            itemId(request.attacker),
            HnsHoldEffectAuthority.forRequest(request, HnsItemSide.ATTACKER)
        )
        return when (contact) {
            HnsContactAuthority.UNKNOWN -> true
            HnsContactAuthority.CONTACT -> type != PokemonType.FIRE
            HnsContactAuthority.NON_CONTACT -> type == PokemonType.FIRE
        }
    }

    /** Exact pinned chart after HnsMoveAuthority's final type and observed H&S item rewrites. */
    internal fun typeEffectiveness(request: DamageCalculationRequest, moveType: PokemonType): Double? {
        val pack = HeartAndSoul205DataPack
        val liveTypes = request.hnsLiveBattleState?.defenderTypes
        val rawTypes = liveTypes ?: request.defenderOverride?.types ?: run {
            val species = pack.getSpeciesByName(request.defender.species) ?: return null
            listOfNotNull(species.type1.displayName, species.type2?.displayName)
        }
        if (rawTypes.isEmpty()) return null
        val parsed = rawTypes.map { PokemonType.fromString(it) ?: return null }
        val item = itemId(request.defender)
        if (moveType == PokemonType.GROUND && PokemonType.FLYING in parsed) {
            val ironBallActive = activeHoldEffect(request, HnsItemSide.DEFENDER, "HOLD_EFFECT_IRON_BALL")
            if (item == ironBallItem && ironBallActive == null) return null
            if (ironBallActive == true) return 1.0
        }
        val ringTargetActive = activeHoldEffect(request, HnsItemSide.DEFENDER, "HOLD_EFFECT_RING_TARGET")
        if (item == ringTargetItem && ringTargetActive == null) return null
        return parsed.fold(1.0) { acc, type ->
            val cell = pack.getEffectiveness(moveType, type)
            acc * if (cell == 0.0 && ringTargetActive == true) 1.0 else cell
        }
    }

    private fun activeHoldEffect(
        request: DamageCalculationRequest,
        side: HnsItemSide,
        expectedEffect: String
    ): Boolean? {
        val resolution = HnsHoldEffectAuthority.forRequest(request, side)
        return when {
            resolution.state == HnsHoldEffectState.UNKNOWN -> null
            resolution.state == HnsHoldEffectState.SUPPRESSED_NONE -> false
            else -> resolution.effectiveHoldEffect == expectedEffect
        }
    }

    private fun abilityId(input: CalcPokemonInput): Int? = input.abilityId
        ?: input.ability?.let { com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(it).abilityId }

    private fun itemId(input: CalcPokemonInput): Int? = input.itemId
        ?: input.item?.let(HnsItemRegistry::resolveIdByName)

    private fun fixedSingleHitDamageMove(moveId: Int): Boolean =
        HeartAndSoul205DataPack.getMove(moveId)?.let { move ->
            move.power > 0 && HnsMoveMechanicsRegistry.classify(moveId).category.isSupportedFixedSingleHit
        } == true
}
