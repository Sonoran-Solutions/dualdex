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
        attackerTypes: Set<PokemonType>? = setOf(PokemonType.GRASS),
        defenderSpeciesId: Int? = 16,
        defenderHp: Int? = 14,
        defenderMaxHp: Int? = 15,
        attackerStatus1: Int? = 0,
        observedBattlersCount: Int? = 2,
        dynamicMoveTypeKnownNeutral: Boolean = true
    ) = HnsAbilityContextPolicy.Context(
        side = side,
        ordinaryMove = ordinaryMove,
        isCrit = isCrit,
        attackerAbilityId = attackerAbilityId,
        moveType = moveType,
        moveCategory = moveCategory,
        attackerTypes = attackerTypes,
        defenderSpeciesId = defenderSpeciesId,
        defenderHp = defenderHp,
        defenderMaxHp = defenderMaxHp,
        attackerStatus1 = attackerStatus1,
        observedBattlersCount = observedBattlersCount,
        dynamicMoveTypeKnownNeutral = dynamicMoveTypeKnownNeutral
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
    fun `Levitate clears attacker and non-Ground defender only with known effective type`() {
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(26, context(side = HnsAbilitySide.ATTACKER, moveType = null)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(26, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.NORMAL)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(26, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.GROUND)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(26, context(side = HnsAbilitySide.DEFENDER, moveType = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(26, context(side = HnsAbilitySide.DEFENDER, moveType = PokemonType.NORMAL,
                dynamicMoveTypeKnownNeutral = false)))
    }

    @Test
    fun `Guts uses side status and category authority`() {
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
        // Category authority is its own operand (HnsMoveAuthority): without it Guts stays unknown,
        // whatever the effective-type authority says.
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(62, context(moveCategory = null, attackerStatus1 = 0)))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            relevance(62, context(moveCategory = MoveCategory.SPECIAL, attackerStatus1 = 0x10,
                moveType = null, dynamicMoveTypeKnownNeutral = false)))
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
            relevance(91, context(moveType = PokemonType.NORMAL, attackerTypes = setOf(PokemonType.GRASS))))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(91, context(moveType = PokemonType.GRASS, attackerTypes = setOf(PokemonType.GRASS))))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(91, context(moveType = null)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(91, context(moveType = PokemonType.NORMAL, attackerTypes = setOf(PokemonType.GRASS),
                dynamicMoveTypeKnownNeutral = false)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(91, context(observedBattlersCount = 4)))
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
            relevance(97, context(isCrit = false)))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            relevance(97, context(isCrit = true)))
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(196, context(isCrit = false, attackerStatus1 = 0x08)))
    }

    @Test
    fun `after-hit and berry recovery groups clear only ordinary hits`() {
        for (id in listOf(24, 64, 106, 124, 152, 160, 215, 238, 254, 268, 139, 167, 291)) {
            assertEquals("ability $id", HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                relevance(id, context()))
            assertEquals("ability $id", HnsAbilityRequestRelevance.UNKNOWN,
                relevance(id, context(ordinaryMove = false)))
        }
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            relevance(192, context()))
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
    fun `unrelated ability and absent context never gain a clearance`() {
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN, relevance(308, null))
        assertTrue(HnsAbilityContextPolicy.assess(140, context(observedBattlersCount = 2))
            .globalCategory == HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT)
    }
}
