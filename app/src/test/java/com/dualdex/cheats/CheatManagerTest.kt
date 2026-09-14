package com.dualdex.cheats

import com.dualdex.emulator.RomIdentity
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class CheatManagerTest {
    @Test
    fun getCheatsReturnsDefaultsWithoutPersistingThem() {
        val storage = mutableMapOf<String, String>()
        val manager = CheatManager(memoryStorage = storage)
        val identity = RomIdentity.create("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", "Pokemon Emerald")

        val cheats = manager.getCheats(identity)

        assertTrue(cheats.isNotEmpty())
        assertFalse(storage.containsKey("cheats_${identity.sha256}"))
    }
}
