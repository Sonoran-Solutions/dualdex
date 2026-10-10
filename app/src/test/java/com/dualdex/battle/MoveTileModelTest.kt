package com.dualdex.battle

import com.dualdex.calculator.CalcSupport
import com.dualdex.pokemon.MoveCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveTileModelTest {

    private fun pres(
        category: MoveCategory = MoveCategory.SPECIAL,
        currentPp: Int? = 12,
        min: Int = 58,
        max: Int = 69,
        effectiveness: String = EffectivenessLabel.SUPER_EFFECTIVE.displayName,
        confidence: DamageConfidence = DamageConfidence.VERIFIED,
        support: CalcSupport? = null
    ) = MovePresentation(
        moveId = 53, name = "Flamethrower", typeName = "Fire", category = category,
        basePower = 90, accuracy = 100, maxPp = 15, currentPp = currentPp, description = "",
        effectiveness = effectiveness, effectivenessConfidence = DataConfidence.VERIFIED,
        damageConfidence = confidence, minDamage = min, maxDamage = max,
        damageRange = emptyList(), koChanceText = "", calculatorSupport = support
    )

    @Test
    fun `tile shows damage percent of defender max HP and short effectiveness`() {
        val tile = MoveTileModels.from(pres(), defenderMaxHp = 100, showDamage = true, showEffectiveness = true, selectionAllowed = true)
        assertEquals("58–69%", tile.damageText)
        assertEquals("Super effective ×2", tile.effectivenessText)
        assertEquals(EffectivenessTone.STRONG, tile.effectivenessTone)
        assertEquals("12/15", tile.ppText)
        assertTrue(tile.selectable)
        assertEquals(
            "Flamethrower, Fire, PP 12 of 15, Super effective ×2, 58 to 69 percent",
            tile.contentDescription
        )
    }

    @Test
    fun `settings hide damage and effectiveness independently`() {
        val noDamage = MoveTileModels.from(pres(), 100, showDamage = false, showEffectiveness = true, selectionAllowed = false)
        assertNull(noDamage.damageText)
        assertEquals("Super effective ×2", noDamage.effectivenessText)

        val noEff = MoveTileModels.from(pres(), 100, showDamage = true, showEffectiveness = false, selectionAllowed = false)
        assertEquals("58–69%", noEff.damageText)
        assertNull(noEff.effectivenessText)
    }

    @Test
    fun `zero PP is never selectable even when input is allowed`() {
        val tile = MoveTileModels.from(pres(currentPp = 0), 100, true, true, selectionAllowed = true)
        assertTrue(tile.outOfPp)
        assertFalse(tile.selectable)
        assertTrue(tile.contentDescription.endsWith("no PP left"))
    }

    @Test
    fun `read-only gate keeps tiles unselectable`() {
        assertFalse(MoveTileModels.from(pres(), 100, true, true, selectionAllowed = false).selectable)
    }

    @Test
    fun `compact damage covers status, estimates, unknown HP and unavailable`() {
        assertEquals("Status", MoveTileModels.compactDamageText(pres(category = MoveCategory.STATUS, min = 0, max = 0), 100))
        assertEquals("~58–69%", MoveTileModels.compactDamageText(pres(support = CalcSupport.ESTIMATED), 100))
        assertEquals("~58–69%", MoveTileModels.compactDamageText(pres(confidence = DamageConfidence.ESTIMATE), 100))
        assertEquals("58–69 HP", MoveTileModels.compactDamageText(pres(), null))
        assertEquals("Damage n/a", MoveTileModels.compactDamageText(pres(min = 0, max = 0, confidence = DamageConfidence.UNAVAILABLE), 100))
    }

    @Test
    fun `unknown effectiveness label is hidden rather than guessed`() {
        val tile = MoveTileModels.from(pres(effectiveness = MoveEffectiveness.UNAVAILABLE), 100, true, true, false)
        assertNull(tile.effectivenessText)
        assertEquals(EffectivenessTone.UNKNOWN, tile.effectivenessTone)
    }

    @Test
    fun `full damage text keeps the HP range for the details sheet`() {
        assertEquals("58–69 HP · 58–69%", MoveTileModels.fullDamageText(pres(), 100))
    }
}
