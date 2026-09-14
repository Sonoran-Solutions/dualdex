package com.dualdex.companion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextLabelFormatterTest {
    @Test
    fun suppressesEquivalentNamesWithPunctuationAndAmpersandDifferences() {
        assertTrue(
            ContextLabelFormatter.namesDescribeSameGame(
                "Pokemon - Heart and Soul v2.0",
                "Heart & Soul"
            )
        )
    }

    @Test
    fun keepsDistinctGameNames() {
        assertFalse(ContextLabelFormatter.namesDescribeSameGame("Pokemon FireRed", "Pokemon Emerald"))
    }
}
