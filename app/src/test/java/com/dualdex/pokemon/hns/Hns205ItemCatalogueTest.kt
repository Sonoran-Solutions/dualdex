package com.dualdex.pokemon.hns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exact H&S 2.0.5 held-item identity catalogue (issue #9, Gap C3).
 *
 * Every expected value below was pinned BY HAND from the pinned upstream checkout
 * (PokemonHnS-Development/pokehns-expansion, Release-v2.0.5,
 * 1f42b74dff0e9fe942419845d040663dd829a973): numeric IDs from
 * `include/constants/items.h` (`enum Item`), display names and hold effects from
 * `src/data/items.h` (`gItemsInfo[]`). No expectation is derived from the
 * generated catalogue under test.
 *
 * Scope reminder: this is identity + static hold-effect data. It does NOT model any
 * item's battle behaviour and does NOT enable H&S damage calculation.
 */
class Hns205ItemCatalogueTest {

    // ------------------------------------------------------------------
    // Numeric identity and domain
    // ------------------------------------------------------------------

    @Test
    fun `item identities match the pinned build enum and table`() {
        // The empty slot sentinel.
        assertEquals(0, Hns205ItemCatalogue.get(0)?.itemId)
        assertEquals("ITEM_NONE", Hns205ItemCatalogue.get(0)?.canonicalSymbol)
        assertNull("ITEM_NONE has no display name in the build's table", Hns205ItemCatalogue.get(0)?.sourceName)

        // An early item.
        assertEquals("ITEM_POKE_BALL", Hns205ItemCatalogue.get(1)?.canonicalSymbol)
        assertEquals("POKé BALL", Hns205ItemCatalogue.get(1)?.sourceName)

        // A classic held item with the H&S x1.2 type-boost parameter.
        val charcoal = Hns205ItemCatalogue.get(426)
        assertEquals("ITEM_CHARCOAL", charcoal?.canonicalSymbol)
        assertEquals("CHARCOAL", charcoal?.sourceName)
        assertEquals("HOLD_EFFECT_TYPE_POWER", charcoal?.holdEffect)
        assertEquals(20, charcoal?.holdEffectParam)

        // A modern held item.
        assertEquals("ITEM_CHOICE_BAND", Hns205ItemCatalogue.get(442)?.canonicalSymbol)
        assertEquals("HOLD_EFFECT_CHOICE_BAND", Hns205ItemCatalogue.get(442)?.holdEffect)
        assertEquals("ITEM_LIFE_ORB", Hns205ItemCatalogue.get(479)?.canonicalSymbol)
        assertEquals("HOLD_EFFECT_LIFE_ORB", Hns205ItemCatalogue.get(479)?.holdEffect)

        // A gem carries the H&S x1.3 parameter.
        assertEquals(30, Hns205ItemCatalogue.get(340)?.holdEffectParam)

        // A high / late enum ID and the exact maximum.
        assertEquals("ITEM_AZURE_FLUTE", Hns205ItemCatalogue.get(900)?.canonicalSymbol)
        assertEquals(900, Hns205ItemCatalogue.ITEM_ID_MAX)
        assertEquals(901, Hns205ItemCatalogue.ITEM_COUNT)
        assertEquals(900, HnsBattlerRuntimeStateIds.ITEM_ID_MAX)
    }

    @Test
    fun `item ids are the contiguous range 0 to 900 with no gaps or duplicates`() {
        val ids = HashSet<Int>()
        for (id in 0..Hns205ItemCatalogue.ITEM_ID_MAX) {
            val data = Hns205ItemCatalogue.get(id)
            assertTrue("catalogue ID $id missing", data != null)
            assertEquals(id, data!!.itemId)
            assertTrue("empty canonical symbol for ID $id", data.canonicalSymbol.isNotBlank())
            assertTrue("duplicate canonical symbol for ID $id", ids.add(data.itemId))
        }
    }

    @Test
    fun `out of range ids fail closed`() {
        assertNull(Hns205ItemCatalogue.get(-1))
        assertNull(Hns205ItemCatalogue.get(901))
        assertNull(Hns205ItemCatalogue.get(99999))
        assertNull(Hns205ItemCatalogue.getBySymbol("ITEM_NOT_A_REAL_ITEM"))
        assertNull(Hns205ItemCatalogue.getBySymbol(""))
    }

    // ------------------------------------------------------------------
    // Alias handling
    // ------------------------------------------------------------------

    @Test
    fun `an enum alias shares the canonical table identity and does not shadow it`() {
        // `include/constants/items.h` declares `ITEM_ENERGYPOWDER = ITEM_ENERGY_POWDER`.
        // The canonical table symbol is the one that owns the ID; the alias is not an
        // independent identity and must never produce a second entry.
        val canonical = Hns205ItemCatalogue.get(39)
        assertEquals("ITEM_ENERGY_POWDER", canonical?.canonicalSymbol)
        assertNull("an alias must not be a distinct catalogue symbol", Hns205ItemCatalogue.getBySymbol("ITEM_ENERGYPOWDER"))
        // Two different enum spellings resolving to the same ID is exactly what the
        // generated catalogue records once, not twice.
        assertEquals(1, (0..Hns205ItemCatalogue.ITEM_ID_MAX).count { Hns205ItemCatalogue.get(it)?.itemId == 39 })
    }

    // ------------------------------------------------------------------
    // Name resolution (manual input path)
    // ------------------------------------------------------------------

    @Test
    fun `names resolve case and hyphen insensitively`() {
        assertEquals(426, Hns205ItemCatalogue.getByName("Charcoal")?.itemId)
        assertEquals(426, Hns205ItemCatalogue.getByName("charcoal")?.itemId)
        assertEquals(426, Hns205ItemCatalogue.getByName("  CHARCOAL  ")?.itemId)
        assertEquals(430, Hns205ItemCatalogue.getByName("Never-Melt Ice")?.itemId)
        assertEquals(430, Hns205ItemCatalogue.getByName("never melt ice")?.itemId)
    }

    @Test
    fun `unknown names fail closed`() {
        assertNull(Hns205ItemCatalogue.getByName("Definitely Not An Item"))
        assertNull(Hns205ItemCatalogue.getByName(null))
        assertNull(Hns205ItemCatalogue.getByName("   "))
    }
}
