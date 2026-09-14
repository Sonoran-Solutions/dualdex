package com.dualdex.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleAutoOpenPreferenceTest {
    @Test
    fun defaultsToAutoOpenWhenNoPreferenceHasBeenStored() {
        assertTrue(BattleAutoOpenPreference.resolve(false, false, false, false))
    }

    @Test
    fun usesTheLegacyValueUntilTheRenamedPreferenceIsWritten() {
        assertFalse(BattleAutoOpenPreference.resolve(false, true, true, false))
        assertTrue(BattleAutoOpenPreference.resolve(false, false, true, true))
    }

    @Test
    fun currentPreferenceWinsOverTheLegacyValue() {
        assertFalse(BattleAutoOpenPreference.resolve(true, false, true, true))
    }
}
