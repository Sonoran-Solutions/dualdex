package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsAbilityCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HnsAbilityContextPolicyTest {
    private fun context(
        side: HnsAbilitySide = HnsAbilitySide.ATTACKER,
        ordinaryMove: Boolean? = true,
        isCrit: Boolean? = false,
        attackerAbilityId: Int? = 0,
        moveType: PokemonType? = PokemonType.NORMAL,
        moveCategory: MoveCategory? = MoveCategory.PHYSICAL,
        moveId: Int? = null,
        moveBasePower: Int? = null,
        moveAbilityFlags: Set<String>? = null,
        unknownMoveAbilityFlags: Set<String>? = null,
        soundMove: Boolean? = null,
        moveAuthority: HnsMoveAuthority? = null,
        attackerTypes: Set<PokemonType>? = setOf(PokemonType.GRASS),
        defenderSpeciesId: Int? = 16,
        defenderHp: Int? = 14,
        defenderMaxHp: Int? = 15,
        attackerStatus1: Int? = 0,
        observedBattlersCount: Int? = 2,
        dynamicMoveTypeKnownNeutral: Boolean = true,
        defenderItemId: Int? = 0,
        defenderTypes: Set<PokemonType>? = setOf(PokemonType.NORMAL, PokemonType.FLYING),
        attackerStatStages: List<Int>? = List(8) { 0 },
        defenderStatStages: List<Int>? = List(8) { 0 },
        attackerAbilityObserved: Boolean = true,
        defenderAbilityObserved: Boolean = true,
        attackerHp: Int? = 14,
        attackerMaxHp: Int? = 15,
        attackerItemId: Int? = 0,
        defenderAbilityId: Int? = 0,
        weatherWord: Int? = 0,
        weatherObserved: Boolean = true,
        fieldStatuses: Int? = 0,
        switchInEventsSettled: Boolean? = true
    ) = HnsAbilityContextPolicy.Context(
        side = side,
        ordinaryMove = ordinaryMove,
        isCrit = isCrit,
        attackerAbilityId = attackerAbilityId,
        moveType = moveType,
        moveCategory = moveCategory,
        moveId = moveId,
        moveBasePower = moveBasePower,
        moveAbilityFlags = moveAbilityFlags,
        unknownMoveAbilityFlags = unknownMoveAbilityFlags,
        soundMove = soundMove,
        attackerTypes = attackerTypes,
        defenderSpeciesId = defenderSpeciesId,
        defenderHp = defenderHp,
        defenderMaxHp = defenderMaxHp,
        attackerStatus1 = attackerStatus1,
        observedBattlersCount = observedBattlersCount,
        dynamicMoveTypeKnownNeutral = dynamicMoveTypeKnownNeutral,
        defenderItemId = defenderItemId,
        defenderTypes = defenderTypes,
        attackerStatStages = attackerStatStages,
        defenderStatStages = defenderStatStages,
        attackerAbilityObserved = attackerAbilityObserved,
        defenderAbilityObserved = defenderAbilityObserved,
        attackerHp = attackerHp,
        attackerMaxHp = attackerMaxHp,
        attackerItemId = attackerItemId,
        defenderAbilityId = defenderAbilityId,
        weatherWord = weatherWord,
        weatherObserved = weatherObserved,
        fieldStatuses = fieldStatuses,
        switchInEventsSettled = switchInEventsSettled,
        moveAuthority = moveAuthority ?: if (dynamicMoveTypeKnownNeutral && moveType != null) {
            HnsMoveAuthority(
                sourceType = moveType,
                preFieldType = moveType,
                effectiveType = moveType,
                category = moveCategory,
                abilityRewriteOutcome = HnsAbilityTypeRewriteOutcome.NOT_USED,
                ateBoost = false,
                soundMove = soundMove
            )
        } else null
    )

    private fun relevance(id: Int, context: HnsAbilityContextPolicy.Context?) =
        HnsAbilityContextPolicy.assess(id, context).relevance

    @Test
    fun `Tera Shell clears only source-proven attacker non-form or below-full contexts`() {
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(308, context(side = HnsAbilitySide.ATTACKER, defenderSpeciesId = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(308, context(side = HnsAbilitySide.DEFENDER, defenderSpeciesId = 16,
                defenderHp = null, defenderMaxHp = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(308, context(side = HnsAbilitySide.DEFENDER,
                defenderSpeciesId = HnsAbilityContextPolicy.TERAPAGOS_TERASTAL_SPECIES_ID,
                defenderHp = 14, defenderMaxHp = 15)))

        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(308, context(side = HnsAbilitySide.DEFENDER,
                defenderSpeciesId = HnsAbilityContextPolicy.TERAPAGOS_TERASTAL_SPECIES_ID,
                defenderHp = 15, defenderMaxHp = 15)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(308, context(side = HnsAbilitySide.DEFENDER,
                defenderSpeciesId = HnsAbilityContextPolicy.TERAPAGOS_TERASTAL_SPECIES_ID,
                defenderHp = null, defenderMaxHp = 15)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(308, context(side = HnsAbilitySide.DEFENDER, defenderSpeciesId = null)))
    }

    @Test
    fun `Truant clears only on defender and attacker remains blocked`() {
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(54, context(side = HnsAbilitySide.DEFENDER)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(54, context(side = HnsAbilitySide.ATTACKER)))
    }

    @Test
    fun `Telepathy remains globally damage-relevant but clears only in observed Singles`() {
        val singles = HnsAbilityContextPolicy.assess(140, context(observedBattlersCount = 2))
        assertEquals(HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT, singles.globalCategory)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, singles.relevance)
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(140, context(observedBattlersCount = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(140, context(observedBattlersCount = 4)))
    }

    @Test
    fun `Levitate is routed through the modeled Group C immunity layer`() {
        assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(26).category)
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(26, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.GROUND)))
    }

    @Test
    fun `Guts uses side status and category authority`() {
        assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(62).category)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(62, context(side = HnsAbilitySide.DEFENDER, attackerStatus1 = null,
                moveCategory = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(62, context(moveCategory = MoveCategory.SPECIAL, attackerStatus1 = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(62, context(moveCategory = MoveCategory.PHYSICAL, attackerStatus1 = 0)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(62, context(moveCategory = MoveCategory.PHYSICAL, attackerStatus1 = 0x10)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(62, context(moveCategory = MoveCategory.PHYSICAL, attackerStatus1 = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(62, context(moveCategory = MoveCategory.PHYSICAL, attackerStatus1 = 1 shl 8)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(62, context(moveCategory = MoveCategory.PHYSICAL, attackerStatus1 = 1 shl 15)))
        // Category authority is its own operand (HnsMoveAuthority): without it Guts stays unknown,
        // whatever the effective-type authority says.
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(62, context(moveCategory = null, attackerStatus1 = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(62, context(moveCategory = MoveCategory.SPECIAL, attackerStatus1 = 0x10,
                moveType = null, dynamicMoveTypeKnownNeutral = false)))
    }

    @Test
    fun `base-power abilities require pinned move operands and preserve exact source conditions`() {
        fun bp(id: Int, c: HnsAbilityContextPolicy.Context) =
            HnsAbilityContextPolicy.assess(id, c).relevance

        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            bp(101, context(moveId = 1, moveBasePower = 60)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(101, context(moveId = 1, moveBasePower = 65)))
        assertEquals("one power above Technician's cutoff",
            HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(101, context(moveId = 1, moveBasePower = 61)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            bp(101, context(moveId = 1, moveBasePower = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(101, context(side = HnsAbilitySide.DEFENDER, moveBasePower = null)))

        val moveFlags = mapOf(
            89 to "punchingMove", 173 to "bitingMove", 178 to "pulseMove", 292 to "slicingMove"
        )
        for ((id, flag) in moveFlags) {
            assertEquals("positive pinned $flag", HnsAbilityRequestRelevance.RELEVANT,
                bp(id, context(moveId = 7, moveAbilityFlags = setOf(flag))))
            assertEquals("negative pinned $flag", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                bp(id, context(moveId = 44, moveAbilityFlags = emptySet())))
            assertEquals("unknown $flag", HnsAbilityRequestRelevance.UNKNOWN,
                bp(id, context(moveId = 7, moveAbilityFlags = null)))
            assertEquals("conditional source $flag", HnsAbilityRequestRelevance.UNKNOWN,
                bp(id, context(moveId = 7, moveAbilityFlags = emptySet(),
                    unknownMoveAbilityFlags = setOf(flag))))
            assertEquals("defender $flag", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                bp(id, context(side = HnsAbilitySide.DEFENDER, moveId = null)))
        }
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            bp(199, context(moveType = PokemonType.WATER)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            bp(199, context(moveType = null, dynamicMoveTypeKnownNeutral = false)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(199, context(moveType = PokemonType.FIRE)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(199, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.NORMAL)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            bp(199, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.FIRE)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            bp(199, context(side = HnsAbilitySide.DEFENDER, moveType = null,
                dynamicMoveTypeKnownNeutral = false)))
        assertEquals("an unresolved authority cannot fall back to a source Fire type",
            HnsAbilityRequestRelevance.UNKNOWN,
            bp(199, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.FIRE,
                moveAuthority = HnsMoveAuthority.NONE)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            bp(85, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.FIRE,
                moveCategory = MoveCategory.SPECIAL)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            bp(85, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.FIRE,
                moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(85, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.NORMAL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(85, context(side = HnsAbilitySide.ATTACKER, moveType = PokemonType.FIRE)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            bp(85, context(side = HnsAbilitySide.DEFENDER, moveType = null,
                dynamicMoveTypeKnownNeutral = false)))
        assertEquals("Heatproof requires the final authoritative type",
            HnsAbilityRequestRelevance.UNKNOWN,
            bp(85, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.FIRE,
                moveAuthority = HnsMoveAuthority.NONE)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            bp(85, context(side = HnsAbilitySide.DEFENDER, ordinaryMove = false,
                moveType = PokemonType.FIRE)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            bp(200, context(moveType = PokemonType.STEEL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(200, context(moveType = PokemonType.NORMAL)))

        for (status in listOf(0x08, 0x80, 0x180)) {
            assertEquals("Toxic Boost poison status $status", HnsAbilityRequestRelevance.RELEVANT,
                bp(137, context(attackerStatus1 = status)))
        }
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(137, context(attackerStatus1 = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(137, context(moveCategory = MoveCategory.SPECIAL, attackerStatus1 = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(137, context(attackerStatus1 = 0x10)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            bp(137, context(attackerStatus1 = 0x08 or 0x10)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            bp(137, context(attackerStatus1 = 0x08 or 0x80)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            bp(138, context(moveCategory = MoveCategory.SPECIAL, attackerStatus1 = 0x10)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            bp(138, context(moveCategory = MoveCategory.SPECIAL, attackerStatus1 = 0x10 or 0x08)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(138, context(moveCategory = MoveCategory.SPECIAL, attackerStatus1 = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            bp(138, context(moveCategory = MoveCategory.PHYSICAL, attackerStatus1 = null)))
    }

    @Test
    fun `Punk Rock consumes the authoritative sound fact on both sides`() {
        assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(244).category)
        fun punk(side: HnsAbilitySide, sound: Boolean?, authority: HnsMoveAuthority? = null) =
            HnsAbilityContextPolicy.assess(244, context(
                side = side,
                soundMove = sound,
                moveAuthority = authority
            )).relevance

        // This helper defaults to Physical category; Punk Rock follows IsSoundMove alone.
        assertEquals(HnsAbilityRequestRelevance.RELEVANT, punk(HnsAbilitySide.ATTACKER, true))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, punk(HnsAbilitySide.ATTACKER, false))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN, punk(HnsAbilitySide.ATTACKER, null))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT, punk(HnsAbilitySide.DEFENDER, true))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, punk(HnsAbilitySide.DEFENDER, false))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN, punk(HnsAbilitySide.DEFENDER, null))
        assertEquals("an unknown authority overrides an asserted sound flag",
            HnsAbilityRequestRelevance.UNKNOWN,
            punk(HnsAbilitySide.ATTACKER, true, HnsMoveAuthority.NONE))
        assertEquals("the move authority wins over a conflicting direct context field",
            HnsAbilityRequestRelevance.RELEVANT,
            punk(HnsAbilitySide.ATTACKER, false,
                contextMoveAuthority(PokemonType.NORMAL, soundMove = true)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(244, context(ordinaryMove = false, soundMove = true)))
    }

    @Test
    fun `Steely Spirit uses final effective type and leaves its partner branch deferred`() {
        assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(252).category)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(252, context(moveType = PokemonType.STEEL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(252, context(moveType = PokemonType.NORMAL)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(252, context(moveType = null, dynamicMoveTypeKnownNeutral = false)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(252, context(side = HnsAbilitySide.DEFENDER, moveType = null)))

        val rewrittenFromSteel = HnsMoveAuthority(
            sourceType = PokemonType.STEEL,
            preFieldType = PokemonType.STEEL,
            effectiveType = PokemonType.ELECTRIC,
            category = MoveCategory.SPECIAL,
            abilityRewriteOutcome = HnsAbilityTypeRewriteOutcome.NOT_USED,
            ateBoost = false,
            soundMove = false
        )
        assertEquals("a known post-field Electric type cannot use a static Steel source type",
            HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(252, context(moveType = PokemonType.STEEL, moveAuthority = rewrittenFromSteel)))
        assertEquals("an unknown final type cannot use the static Steel source type",
            HnsAbilityRequestRelevance.UNKNOWN,
            relevance(252, context(moveType = PokemonType.STEEL, moveAuthority = HnsMoveAuthority.NONE)))
    }

    private fun contextMoveAuthority(type: PokemonType, soundMove: Boolean) = HnsMoveAuthority(
        sourceType = type,
        preFieldType = type,
        effectiveType = type,
        category = MoveCategory.PHYSICAL,
        abilityRewriteOutcome = HnsAbilityTypeRewriteOutcome.NOT_USED,
        ateBoost = false,
        soundMove = soundMove
    )

    @Test
    fun `move flag facts are rebound from exact pinned move name rather than request data`() {
        val request = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Machamp"),
            defender = CalcPokemonInput(species = "Snorlax"),
            move = CalcMoveInput("Fire Punch")
        )
        val context = HnsAbilityContextPolicy.contextForRequest(request, HnsAbilitySide.ATTACKER, true)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            HnsAbilityContextPolicy.assess(89, context).relevance)

        val control = request.copy(move = CalcMoveInput("Tackle"))
        val controlContext = HnsAbilityContextPolicy.contextForRequest(control, HnsAbilitySide.ATTACKER, true)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            HnsAbilityContextPolicy.assess(89, controlContext).relevance)
    }

    @Test
    fun `Hustle is exact only for authoritative physical attacker category`() {
        assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(55).category)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(55, context(moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(55, context(moveCategory = MoveCategory.SPECIAL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(55, context(side = HnsAbilitySide.DEFENDER, moveCategory = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(55, context(moveCategory = null)))
    }

    @Test
    fun `Solar Power consumes final category and authoritative affected Sun prerequisites`() {
        val sun = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN_NORMAL
        assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(94).category)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(94, context(attackerAbilityId = 94, moveCategory = MoveCategory.SPECIAL,
                weatherWord = sun, attackerItemId = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(94, context(attackerAbilityId = 94, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = sun)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(94, context(attackerAbilityId = 94, moveCategory = MoveCategory.SPECIAL,
                weatherWord = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(94, context(attackerAbilityId = 94, moveCategory = MoveCategory.SPECIAL,
                weatherWord = sun, attackerItemId = 513)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(94, context(side = HnsAbilitySide.DEFENDER, attackerAbilityId = 0,
                defenderAbilityId = 94, moveCategory = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(94, context(attackerAbilityId = 94, moveCategory = null, weatherWord = sun)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(94, context(attackerAbilityId = 94, moveCategory = MoveCategory.SPECIAL,
                weatherWord = null, weatherObserved = false)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(94, context(attackerAbilityId = 94, moveCategory = MoveCategory.SPECIAL,
                weatherWord = sun, attackerItemId = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(94, context(attackerAbilityId = 94, moveCategory = MoveCategory.SPECIAL,
                weatherWord = sun, defenderAbilityId = 13)))
    }

    @Test
    fun `Defeatist uses inclusive integer half of valid live HP independent of category`() {
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(129, context(attackerAbilityId = 129, attackerHp = 10, attackerMaxHp = 20,
                moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(129, context(attackerAbilityId = 129, attackerHp = 11, attackerMaxHp = 20)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(129, context(attackerAbilityId = 129, attackerHp = 7, attackerMaxHp = 15,
                moveCategory = MoveCategory.SPECIAL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(129, context(attackerAbilityId = 129, attackerHp = 8, attackerMaxHp = 15)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(129, context(attackerAbilityId = 129, attackerHp = 1, attackerMaxHp = 20)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(129, context(attackerAbilityId = 129, attackerHp = 20, attackerMaxHp = 20)))
        for ((hp, maxHp) in listOf(null to 20, 1 to null, 21 to 20, -1 to 20, 1 to 0)) {
            assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
                relevance(129, context(attackerAbilityId = 129, attackerHp = hp, attackerMaxHp = maxHp)))
        }
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(129, context(side = HnsAbilitySide.DEFENDER, defenderAbilityId = 129)))
    }

    @Test
    fun `Fur Coat uses Defense only with known ordinary category and Wonder Room off`() {
        val wonderRoom = com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM
        assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(169).category)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(169, context(side = HnsAbilitySide.DEFENDER, defenderAbilityId = 169,
                moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(169, context(side = HnsAbilitySide.DEFENDER, defenderAbilityId = 169,
                moveCategory = MoveCategory.SPECIAL)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(169, context(side = HnsAbilitySide.DEFENDER, defenderAbilityId = 169,
                fieldStatuses = wonderRoom)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(169, context(side = HnsAbilitySide.DEFENDER, defenderAbilityId = 169,
                fieldStatuses = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(169, context(side = HnsAbilitySide.ATTACKER, attackerAbilityId = 169,
                moveCategory = null)))
    }

    @Test
    fun `final type Attack-stat abilities use authorized final type and Orichalcum GetWeather semantics`() {
        val cases = listOf(
            262 to PokemonType.ELECTRIC,
            263 to PokemonType.DRAGON,
            276 to PokemonType.ROCK
        )
        for ((id, matchingType) in cases) {
            assertEquals(HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
                com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(id).category)
            assertEquals(HnsAbilityRequestRelevance.RELEVANT,
                relevance(id, context(attackerAbilityId = id, moveType = matchingType)))
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(attackerAbilityId = id, moveType = PokemonType.FIRE)))
            assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(attackerAbilityId = id, moveType = null,
                    dynamicMoveTypeKnownNeutral = false, moveAuthority = null)))
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, defenderAbilityId = id,
                    attackerAbilityId = 0, moveType = null)))
        }

        val sun = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN_NORMAL
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = sun, attackerItemId = 0)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = sun or (1 shl 4), attackerItemId = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.SPECIAL,
                weatherWord = sun)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = sun,
                attackerItemId = com.dualdex.pokemon.hns.HnsItemRegistry.resolveIdByName("Utility Umbrella"))))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = sun, attackerItemId = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = null, weatherObserved = false)))
        // CalcAttackStat reads ctx->weather, but battle_script_commands.c initializes it through
        // GetWeather(), which returns NONE when HasWeatherEffect() is false.
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = sun, defenderAbilityId = 13)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(288, context(attackerAbilityId = 288, moveCategory = MoveCategory.PHYSICAL,
                weatherWord = sun, attackerHp = null, defenderHp = null)))
    }

    @Test
    fun `Huge and Pure Power clear defender and Special move cases only`() {
        for (id in listOf(37, 74)) {
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, moveCategory = null)))
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(moveCategory = MoveCategory.SPECIAL)))
            assertEquals(HnsAbilityRequestRelevance.RELEVANT,
                relevance(id, context(moveCategory = MoveCategory.PHYSICAL)))
            assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(moveCategory = null)))
        }
    }

    @Test
    fun `Thick Fat clears attacker and non-Fire-Ice defender cases only`() {
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(47, context(side = HnsAbilitySide.ATTACKER, moveType = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(47, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.NORMAL)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(47, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.FIRE)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(47, context(side = HnsAbilitySide.DEFENDER, moveType = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(47, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.NORMAL,
                dynamicMoveTypeKnownNeutral = false)))
    }

    @Test
    fun `Adaptability requires authoritative Singles types and STAB comparison`() {
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(91, context(side = HnsAbilitySide.DEFENDER, attackerTypes = null,
                observedBattlersCount = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(91, context(attackerAbilityId = 91, moveType = PokemonType.NORMAL,
                attackerTypes = setOf(PokemonType.GRASS))))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(91, context(attackerAbilityId = 91, moveType = PokemonType.GRASS,
                attackerTypes = setOf(PokemonType.GRASS))))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(91, context(moveType = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(91, context(moveType = PokemonType.NORMAL, attackerTypes = setOf(PokemonType.GRASS),
                dynamicMoveTypeKnownNeutral = false)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(91, context(observedBattlersCount = 4)))
    }

    @Test
    fun `live stage writers clear only with both exact stage arrays and preserve Analytic turn order`() {
        val stageWriterIds = listOf(
            3, 22, 80, 83, 86, 88, 128, 133, 141, 153, 154, 155, 172, 192, 195, 201,
            220, 224, 234, 235, 243, 264, 265, 270, 271, 275, 290
        )
        stageWriterIds.forEach { id ->
            assertEquals("ability $id", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context()))
        }
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(22, context(attackerStatStages = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(192, context(defenderStatStages = List(7) { 0 })))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(3, context(attackerAbilityObserved = false)))
        for (id in listOf(3, 80, 86, 133, 141, 155, 224, 243, 271, 290)) {
            assertEquals("speed writer $id with Analytic", HnsAbilityRequestRelevance.RELEVANT,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, attackerAbilityId = 148)))
        }
    }

    @Test
    fun `Simple doubles the delta while writing the stage and is cleared from the observed result`() {
        val decision = HnsAbilityContextPolicy.assess(86, context())
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, decision.relevance)
        assertEquals("live_stat_stages_capture_stage_writer", decision.rule)
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(86, context(defenderStatStages = null)))
    }

    @Test
    fun `ordinary Rain and Sun setters clear only with observed unsuppressed weather`() {
        for (id in listOf(2, 70)) {
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(weatherWord = 1)))
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(weatherWord = 1 shl 3)))
        }
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(2, context(weatherWord = 1 shl 5))) // Sandstorm is not applied by the engine.
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(70, context(weatherWord = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(2, context(weatherWord = 1, defenderAbilityId = 13))) // Cloud Nine.
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(2, context(weatherWord = 1, attackerHp = 0)))
    }

    @Test
    fun `type rewriters clear only with observed effective types for their side`() {
        for (id in listOf(16, 250)) {
            assertEquals("ability $id", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, defenderTypes = setOf(PokemonType.ROCK))))
        }
        for (id in listOf(168, 236)) {
            assertEquals("ability $id", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(moveType = PokemonType.GRASS, attackerTypes = setOf(PokemonType.GRASS))))
            assertEquals("ability $id must not assume its pre-damage transform already ran",
                HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(moveType = PokemonType.NORMAL, attackerTypes = setOf(PokemonType.GRASS))))
            assertEquals("ability $id can still transform a dual type before this hit",
                HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(moveType = PokemonType.GRASS,
                    attackerTypes = setOf(PokemonType.GRASS, PokemonType.FLYING))))
        }
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(16, context(side = HnsAbilitySide.DEFENDER, defenderTypes = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(250, context(attackerTypes = null)))
    }

    @Test
    fun `effective ability rewriters use only an observed runtime ability`() {
        for (id in listOf(36, 222, 223)) {
            assertEquals("ability $id", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context()))
        }
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(36, context(attackerAbilityObserved = false)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(222, context(ordinaryMove = null)))
    }

    @Test
    fun `partner abilities clear only with observed Singles topology`() {
        for (id in listOf(132, 57, 58)) {
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(observedBattlersCount = 2)))
            assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(observedBattlersCount = null)))
            assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(observedBattlersCount = 4)))
        }
    }

    @Test
    fun `critical armor clears fixed noncritical range but conflicts with critical request`() {
        for (id in listOf(4, 75)) {
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(side = HnsAbilitySide.ATTACKER)))
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(side = HnsAbilitySide.DEFENDER)))
            assertEquals(HnsAbilityRequestRelevance.RELEVANT,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, isCrit = true)))
            assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, isCrit = null)))
        }
    }

    @Test
    fun `critical stage is irrelevant but critical damage and forced critical remain relevant`() {
        for (crit in listOf(false, true)) {
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(105, context(isCrit = crit)))
        }
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(105, context(isCrit = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(97, context(isCrit = false, attackerAbilityId = 97)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(97, context(isCrit = true, attackerAbilityId = 97)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(196, context(isCrit = false, attackerStatus1 = 0x08)))
    }

    @Test
    fun `after-hit and berry recovery groups clear only ordinary hits`() {
        for (id in listOf(24, 64, 106, 124, 152, 160, 215, 221, 238, 254, 268, 139, 167, 291)) {
            assertEquals("ability $id", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context()))
            assertEquals("ability $id", HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(ordinaryMove = false)))
        }
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(192, context(defenderStatStages = null)))
    }

    @Test
    fun `Ripen clears only when the current hit has no defender resist berry path`() {
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(247, context(side = HnsAbilitySide.ATTACKER, defenderItemId = 550)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(247, context(side = HnsAbilitySide.DEFENDER, defenderItemId = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(247, context(side = HnsAbilitySide.DEFENDER, defenderItemId = 472)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(247, context(side = HnsAbilitySide.DEFENDER, defenderItemId = 550)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(247, context(side = HnsAbilitySide.DEFENDER, defenderItemId = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(247, context(side = HnsAbilitySide.DEFENDER, ordinaryMove = false, defenderItemId = 0)))
    }

    @Test
    fun `Ripen uses the current defender item identity from the request`() {
        val request = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Pikachu"),
            defender = CalcPokemonInput(species = "Bulbasaur", itemId = 550),
            move = CalcMoveInput("Tackle")
        )
        val context = HnsAbilityContextPolicy.contextForRequest(request, HnsAbilitySide.DEFENDER, true)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            HnsAbilityContextPolicy.assess(247, context).relevance)

        val unresolvedItem = request.copy(defender = request.defender.copy(item = "unknown berry", itemId = null))
        val unresolvedContext =
            HnsAbilityContextPolicy.contextForRequest(unresolvedItem, HnsAbilitySide.DEFENDER, true)
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            HnsAbilityContextPolicy.assess(247, unresolvedContext).relevance)
    }

    @Test
    fun `speed abilities clear without Analytic and keep its turn order dependency`() {
        for (id in listOf(33, 34, 84, 95, 146, 202, 259)) {
            assertEquals("ability $id", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, attackerAbilityId = 0)))
            assertEquals("ability $id", HnsAbilityRequestRelevance.RELEVANT,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, attackerAbilityId = 148)))
            assertEquals("ability $id", HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, attackerAbilityId = null)))
            assertEquals("ability $id", HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(side = HnsAbilitySide.DEFENDER, ordinaryMove = false)))
        }
    }

    @Test
    fun `switch-in writers require an observed settled event phase`() {
        val pending = context(switchInEventsSettled = false)
        val unread = context(switchInEventsSettled = null)
        val settled = context(switchInEventsSettled = true)
        for (id in listOf(22, 2, 16, 36, 222, 223)) {
            assertEquals("pending ability $id", HnsAbilityRequestRelevance.UNKNOWN, relevance(id, pending))
            assertEquals("unread phase ability $id", HnsAbilityRequestRelevance.UNKNOWN, relevance(id, unread))
        }
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(22, settled.copy(side = HnsAbilitySide.DEFENDER)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, relevance(2, settled))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(16, settled.copy(side = HnsAbilitySide.DEFENDER)))
        for (id in listOf(36, 222, 223)) {
            assertEquals("settled ability rewriter $id", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, settled.copy(side = HnsAbilitySide.DEFENDER)))
        }
    }

    @Test
    fun `unrelated ability and absent context never gain a clearance`() {
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN, relevance(308, null))
        assertTrue(HnsAbilityContextPolicy.assess(140, context(observedBattlersCount = 2))
            .globalCategory == HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT)
    }
}
