package com.dualdex.pokemon.hns

import com.dualdex.pokemon.PokemonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Completeness of the reviewed H&S 2.0.5 held-item audit (tools/hns-items).
 *
 * `./ci.sh source-check` re-derives the audit from the pinned source; these self-contained tests
 * prove the committed Kotlin data, the committed reviewable inventory and the registry agree over
 * the whole 0..900 domain without an upstream checkout.
 */
class HnsItemAuditTest {

    private val domain = 0..Hns205ItemCatalogue.ITEM_ID_MAX

    @Test
    fun `the exact pinned domain is 901 contiguous unique identities`() {
        assertEquals(901, Hns205ItemCatalogue.ITEM_COUNT)
        assertEquals(900, Hns205ItemCatalogue.ITEM_ID_MAX)
        val data = domain.map { assertNotNull("item $it", Hns205ItemCatalogue.get(it)); Hns205ItemCatalogue.get(it)!! }
        assertEquals(901, data.map { it.canonicalSymbol }.toSet().size)
        assertEquals(domain.toList(), data.map { it.itemId })
        assertNull(Hns205ItemCatalogue.get(901))
    }

    @Test
    fun `every pinned hold effect has exactly one reviewed family decision`() {
        val used = domain.map { Hns205ItemCatalogue.get(it)!!.holdEffect }.toSet()
        assertEquals(used, HnsItemAuditData.families.keys)
        assertEquals(130, used.size)
        HnsItemAuditData.families.forEach { (key, decision) ->
            assertEquals(key, decision.key)
            assertTrue("$key rationale", decision.rationale.isNotBlank())
        }
    }

    @Test
    fun `identity exceptions refer to real identities and override their family`() {
        assertEquals(setOf(288, 289, 581), HnsItemAuditData.identityExceptions.keys)
        val enigma = Hns205ItemCatalogue.get(581)!!
        assertEquals("ITEM_ENIGMA_BERRY_E_READER", enigma.canonicalSymbol)
        assertEquals(HnsItemCategory.UNCLASSIFIED, HnsItemRegistry.classify(581).category)
    }

    @Test
    fun `Rusted Sword and Shield never inherit HOLD_EFFECT_NONE neutrality`() {
        // FORM_CHANGE_BEGIN_BATTLE (src/data/pokemon/form_change_tables.h) crowns a holding
        // Zacian/Zamazenta and swaps Iron Head for Behemoth Blade/Bash: battle-relevant item
        // identity that the NONE hold effect does not describe.
        for ((id, symbol) in listOf(288 to "ITEM_RUSTED_SWORD", 289 to "ITEM_RUSTED_SHIELD")) {
            val entry = HnsItemRegistry.classify(id)
            assertEquals(symbol, entry.data?.canonicalSymbol)
            assertEquals("HOLD_EFFECT_NONE", entry.data?.holdEffect)
            assertEquals(HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, entry.category)
            assertEquals("identity_exception", entry.familyGroup)
            assertFalse(HnsItemRegistry.isSupportedForDamage(id))
            assertNull(HnsItemRegistry.engineItemName(id))
        }
    }

    @Test
    fun `every item is classified and the totals match the generated audit`() {
        val counts = domain.groupingBy { HnsItemRegistry.classify(it).category }.eachCount()
        val expected = HnsItemAuditData.categoryCounts.filterValues { it > 0 }
        assertEquals(expected, counts)
        assertEquals(585, counts[HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT])
        assertEquals(312, counts[HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT])
        assertEquals(3, counts[HnsItemCategory.UNCLASSIFIED])
        assertNull("nothing is MODELLED_EQUIVALENT", counts[HnsItemCategory.MODELLED_EQUIVALENT])
        assertEquals(1, counts[HnsItemCategory.MODELLED_HNS_SPECIFIC])
        assertEquals(HnsItemCategory.MODELLED_HNS_SPECIFIC, HnsItemRegistry.classify(476).category)
        assertEquals("Wise Glasses", HnsItemRegistry.engineItemName(476))
        assertTrue(HnsItemRegistry.isSupportedForDamage(476))
    }

    @Test
    fun `only modelled items are forwarded to the engine`() {
        for (id in domain) {
            if (id == 476) {
                assertEquals("Wise Glasses", HnsItemRegistry.engineItemName(id))
            } else {
                assertNull("item $id", HnsItemRegistry.engineItemName(id))
            }
        }
        assertNull(HnsItemRegistry.engineItemName(null))
        assertNull(HnsItemRegistry.engineItemName(901))
        // Supported for damage means proven-neutral families or modelled items with engine adapters.
        for (id in domain) {
            val expectedSupported = (id == 476) ||
                (HnsItemRegistry.classify(id).category == HnsItemCategory.PROVEN_NO_ORDINARY_DAMAGE_EFFECT)
            assertEquals(
                "item $id",
                expectedSupported,
                HnsItemRegistry.isSupportedForDamage(id)
            )
        }
    }

    @Test
    fun `type operands exist only for type-matched families and name real types`() {
        val typed = setOf("HOLD_EFFECT_TYPE_POWER", "HOLD_EFFECT_PLATE", "HOLD_EFFECT_GEMS", "HOLD_EFFECT_RESIST_BERRY")
        for (id in domain) {
            val he = Hns205ItemCatalogue.get(id)!!.holdEffect
            val type = HnsItemRegistry.itemTypeName(id)
            if (he in typed) {
                assertNotNull("item $id ($he) needs a pinned type operand", type)
                assertNotNull("item $id type $type", PokemonType.fromString(type))
            } else {
                assertNull("item $id ($he) must not carry a type operand", type)
            }
        }
        assertEquals("FIRE", HnsItemRegistry.itemTypeName(426)) // Charcoal
        assertEquals("FIRE", HnsItemRegistry.itemTypeName(550)) // Occa Berry param
        assertEquals("NORMAL", HnsItemRegistry.itemTypeName(549)) // Chilan Berry param
    }

    @Test
    fun `the committed reviewable inventory agrees with the registry row by row`() {
        val file = listOf(File("../tools/hns-items/item_inventory.tsv"), File("tools/hns-items/item_inventory.tsv"))
            .firstOrNull { it.isFile } ?: throw AssertionError("tools/hns-items/item_inventory.tsv not found")
        val rows = file.readLines().drop(1).filter { it.isNotBlank() }.map { it.split('\t') }
        assertEquals(901, rows.size)
        for (row in rows) {
            val id = row[0].toInt()
            val data = Hns205ItemCatalogue.get(id)!!
            assertEquals(data.canonicalSymbol, row[1])
            assertEquals(HnsItemRegistry.displayName(id), row[2])
            assertEquals(data.holdEffect, row[3])
            assertEquals(data.holdEffectParam, row[4].toInt())
            assertEquals(HnsItemRegistry.itemTypeName(id) ?: "", row[5])
            assertEquals(HnsItemRegistry.classify(id).category.name, row[6])
            assertEquals(HnsItemRegistry.classify(id).familyGroup, row[7])
        }
    }

    @Test
    fun `unknown and out-of-domain identities fail closed`() {
        for (id in listOf(-1, 901, 65535)) {
            assertEquals(HnsItemCategory.UNCLASSIFIED, HnsItemRegistry.classify(id).category)
            assertFalse(HnsItemRegistry.isSupportedForDamage(id))
            assertNull(HnsItemRegistry.displayName(id))
        }
        assertFalse(HnsItemRegistry.isSupportedForDamage(null))
    }
}
