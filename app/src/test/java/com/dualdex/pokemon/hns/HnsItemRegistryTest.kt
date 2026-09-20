package com.dualdex.pokemon.hns

import com.dualdex.pokemon.ItemDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The H&S 2.0.5 held-item capability registry (issue #9, Gap C3).
 *
 * Capability is decided by the exact numeric item ID from the generated
 * [Hns205ItemCatalogue]; the generic expansion `ItemDatabase` is never an authority.
 */
class HnsItemRegistryTest {

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    @Test
    fun `item none and proven utility items are supported no-ordinary-damage`() {
        val supported = mapOf(
            0 to "ITEM_NONE",
            461 to "ITEM_EXP_SHARE",
            466 to "ITEM_AMULET_COIN",
            463 to "ITEM_SOOTHE_BELL",
            467 to "ITEM_CLEANSE_TAG",
            470 to "ITEM_LUCKY_EGG"
        )
        for ((id, symbol) in supported) {
            val entry = HnsItemRegistry.classify(id)
            assertEquals("id $id symbol", symbol, entry.data?.canonicalSymbol)
            assertEquals(
                "id $id ($symbol) must be proven no ordinary damage",
                HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT,
                entry.category
            )
            assertTrue(entry.category.isSupportedForDamage)
        }
    }

    @Test
    fun `representative damage-relevant items are unsupported`() {
        val unsupported = listOf(
            426, // Charcoal (x1.2 type booster)
            442, // Choice Band
            479, // Life Orb
            340, // Fire Gem (x1.3)
            550, // Occa Berry (x0.5 resist)
            481, // Focus Sash (KO relevant)
            472  // Leftovers (KO relevant)
        )
        for (id in unsupported) {
            val entry = HnsItemRegistry.classify(id)
            assertEquals(
                "id $id (${entry.data?.canonicalSymbol}) must be unsupported",
                HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
                entry.category
            )
            assertFalse(entry.category.isSupportedForDamage)
        }
    }

    @Test
    fun `an in-domain unregistered item is unclassified and fails closed`() {
        // 39 = ITEM_ENERGY_POWDER: a real identity with no audited damage classification.
        val entry = HnsItemRegistry.classify(39)
        assertEquals(39, entry.itemId)
        assertEquals("ITEM_ENERGY_POWDER", entry.data?.canonicalSymbol)
        assertEquals(HnsItemCategory.UNCLASSIFIED, entry.category)
        assertFalse(entry.category.isSupportedForDamage)
    }

    @Test
    fun `out of domain and null ids fail closed`() {
        for (id in listOf<Int?>(null, -1, 901, 9999)) {
            assertEquals(HnsItemCategory.UNCLASSIFIED, HnsItemRegistry.classify(id).category)
            assertFalse(HnsItemRegistry.classify(id).category.isSupportedForDamage)
        }
        assertTrue(HnsItemRegistry.isInDomain(0))
        assertTrue(HnsItemRegistry.isInDomain(900))
        assertFalse(HnsItemRegistry.isInDomain(-1))
        assertFalse(HnsItemRegistry.isInDomain(901))
    }

    // ------------------------------------------------------------------
    // Numeric ID authority
    // ------------------------------------------------------------------

    @Test
    fun `capability follows the numeric id, never a display name`() {
        // A runtime ID that is a damage item must stay unsupported no matter what the
        // paired display name claims; the registry has no name-keyed live path at all.
        val byId = HnsItemRegistry.classify(426) // Charcoal
        assertEquals(HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, byId.category)
        assertEquals("CHARCOAL", byId.data?.sourceName)

        // The manual name path resolves THROUGH the exact catalogue to the same ID.
        val byName = HnsItemRegistry.classifyByName("Charcoal")
        assertEquals(426, byName.itemId)
        assertEquals(HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, byName.category)
    }

    @Test
    fun `unknown manual names fail closed`() {
        val entry = HnsItemRegistry.classifyByName("Definitely Not An Item")
        assertNull(entry.itemId)
        assertEquals(HnsItemCategory.UNCLASSIFIED, entry.category)
        assertNull(HnsItemRegistry.resolveIdByName("Definitely Not An Item"))
        assertEquals(426, HnsItemRegistry.resolveIdByName("Charcoal"))
    }

    // ------------------------------------------------------------------
    // The generic expansion table can never authorize H&S
    // ------------------------------------------------------------------

    @Test
    fun `a mutated generic ItemDatabase cannot change authoritative H&S capability`() {
        // The generic expansion table is deliberately NOT H&S authority. Mutate it to claim
        // that the Charcoal ID is a harmless named item, then prove the H&S registry still
        // classifies that ID as the source-proven damage item.
        val expansionMap = expansionMapForTest()
        val original = expansionMap[426]
        try {
            expansionMap[426] = ItemDatabase.ItemInfo(426, "Harmless Thing", "🎒")
            assertEquals("Harmless Thing", ItemDatabase.get(426, isExpansion = true).name)

            val entry = HnsItemRegistry.classify(426)
            assertEquals("ITEM_CHARCOAL", entry.data?.canonicalSymbol)
            assertEquals(HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, entry.category)
            // The H&S catalogue does not know the spoofed name, so it cannot resolve.
            assertNull(HnsItemRegistry.resolveIdByName("Harmless Thing"))
        } finally {
            if (original != null) expansionMap[426] = original else expansionMap.remove(426)
        }
    }

    @Test
    fun `the generic expansion table is insufficient for a high H and S item id`() {
        // The generic map does not cover the full H&S domain; the exact catalogue does.
        assertEquals("Item #900", ItemDatabase.get(900, isExpansion = true).name)
        assertEquals("AZURE FLUTE", Hns205ItemCatalogue.get(900)?.sourceName)
        assertNotNull(HnsItemRegistry.classify(900))
    }

    /** Test-only reflection access to the generic expansion item table. */
    @Suppress("UNCHECKED_CAST")
    private fun expansionMapForTest(): MutableMap<Int, ItemDatabase.ItemInfo> {
        val field = ItemDatabase::class.java.getDeclaredField("expansionMap")
        field.isAccessible = true
        return field.get(ItemDatabase) as MutableMap<Int, ItemDatabase.ItemInfo>
    }
}
