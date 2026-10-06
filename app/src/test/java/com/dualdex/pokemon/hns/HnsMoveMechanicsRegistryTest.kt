package com.dualdex.pokemon.hns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Move-mechanics capability audit for H&S 2.0.5 (issue #9, Gap C4a).
 *
 * These pin the fail-closed contract: only the source-derived ordinary `EFFECT_HIT` subset may
 * pass the gate, every representative special family is refused, and an unresolved effect is never
 * guessed.
 */
class HnsMoveMechanicsRegistryTest {

    @Test
    fun `ordinary fixed-base-power moves clear the gate`() {
        // Tackle (33) and Flamethrower (53) are EFFECT_HIT with no damage-relevant complication.
        val tackle = HnsMoveMechanicsRegistry.classify(33)
        assertEquals(HnsMoveMechanicsCategory.ORDINARY_PROVEN_EQUIVALENT, tackle.category)
        assertFalse(tackle.requiresBlock)

        val flamethrower = HnsMoveMechanicsRegistry.classify(53)
        assertEquals(HnsMoveMechanicsCategory.ORDINARY_PROVEN_EQUIVALENT, flamethrower.category)
        assertFalse(flamethrower.requiresBlock)
    }

    @Test
    fun `friendship and hidden power are state dependent and blocked`() {
        val ret = HnsMoveMechanicsRegistry.classify(216)
        assertEquals(HnsMoveMechanicsCategory.UNSUPPORTED_STATE_DEPENDENT, ret.category)
        assertTrue(ret.requiresBlock)
        assertEquals("EFFECT_RETURN", ret.effect)

        val hiddenPower = HnsMoveMechanicsRegistry.classify(237)
        assertEquals(HnsMoveMechanicsCategory.UNSUPPORTED_FORMULA_DIFFERENT, hiddenPower.category)
        assertTrue(hiddenPower.requiresBlock)
    }

    @Test
    fun `conditional effect is unresolved and blocked`() {
        // Low Kick's effect is compiled conditionally; the generator refuses to guess it.
        val lowKick = HnsMoveMechanicsRegistry.classify(67)
        assertEquals(HnsMoveMechanicsCategory.UNCLASSIFIED, lowKick.category)
        assertNull(lowKick.effect)
        assertTrue(lowKick.requiresBlock)
    }

    @Test
    fun `plain random multi-hit has selected-strike arithmetic and separate sequence authority`() {
        val bulletSeed = HnsMoveMechanicsRegistry.classify(331)
        assertEquals(HnsMoveMechanicsCategory.VARIABLE_MULTI_HIT_PLAIN, bulletSeed.category)
        assertFalse(bulletSeed.requiresBlock)
        assertEquals("EFFECT_HIT", bulletSeed.effect)
        assertTrue(bulletSeed.category.isSupportedSelectedStrike)
        assertFalse(bulletSeed.category.isSupportedFixedSingleHit)
        assertFalse(331 in Hns205MoveEffects.ordinaryMoveIds)
        assertTrue(Hns205MoveEffects.variableMultiHitPlainMoveIds.containsAll(
            setOf(3, 4, 31, 42, 131, 154, 198, 292, 331, 333, 350, 541)))
        assertEquals(12, Hns205MoveEffects.variableMultiHitPlainMoveIds.size)
        for (id in listOf(41, 24, 167, 813, 711, 251, 374)) {
            assertFalse("distinct family must not inherit variable multi-hit", id in Hns205MoveEffects.variableMultiHitPlainMoveIds)
        }
        assertFalse(727 in Hns205MoveEffects.variableMultiHitPlainMoveIds) // Scale Shot
        assertFalse(41 in Hns205MoveEffects.variableMultiHitPlainMoveIds) // Twineedle
        // Double Kick is EFFECT_HIT with strikeCount = 2.
        assertEquals(HnsMoveMechanicsCategory.FIXED_TWO_HIT_PLAIN, HnsMoveMechanicsRegistry.classify(24).category)
        assertFalse(HnsMoveMechanicsRegistry.classify(24).category.isSupportedFixedSingleHit)

        // Explosion has its separate modern-Defense family, with independent execution gates.
        assertFalse(HnsMoveMechanicsRegistry.classify(153).requiresBlock)
        assertFalse(HnsMoveMechanicsRegistry.classify(120).requiresBlock)
    }

    @Test
    fun `defence-ignoring EFFECT_HIT moves are blocked`() {
        // Sacred Sword ignores the target's defence stages.
        assertTrue(HnsMoveMechanicsRegistry.classify(533).requiresBlock)
    }

    @Test
    fun `item-dependent moves defer to the C3 audit without a duplicate blocker`() {
        val fling = HnsMoveMechanicsRegistry.classify(374)
        assertEquals(HnsMoveMechanicsCategory.ITEM_DEPENDENT_HANDLED_ELSEWHERE, fling.category)
        assertFalse(fling.requiresBlock)
    }

    @Test
    fun `unknown and null ids fail closed`() {
        val unknown = HnsMoveMechanicsRegistry.classify(999_999)
        assertEquals(HnsMoveMechanicsCategory.UNCLASSIFIED, unknown.category)
        assertTrue(unknown.requiresBlock)

        val missing = HnsMoveMechanicsRegistry.classify(null)
        assertEquals(HnsMoveMechanicsCategory.UNCLASSIFIED, missing.category)
        assertTrue(missing.requiresBlock)
    }

    @Test
    fun `the generated ordinary set is a strict subset of the effect map`() {
        assertTrue(Hns205MoveEffects.ordinaryMoveIds.isNotEmpty())
        assertTrue(Hns205MoveEffects.ordinaryMoveIds.all { it in Hns205MoveEffects.effectById })
        assertTrue(Hns205MoveEffects.ordinaryMoveIds.size < Hns205MoveEffects.effectById.size)
        assertTrue(Hns205MoveEffects.ordinaryMoveIds.all { Hns205MoveEffects.effectById[it] == "EFFECT_HIT" })
    }
}
