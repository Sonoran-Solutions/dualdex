package com.dualdex.pokemon.hns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated pinned `gFieldStatuses` bit table and the unmasked decoder. The expected values are
 * the pinned `include/constants/battle.h` definitions of pokehns-expansion Release-v2.0.5; the
 * source-check (`generate_hns_field_status.py --check`) proves the generated table still equals them.
 */
class HnsFieldStateTest {

    private val pinned = linkedMapOf(
        HnsFieldStatus.MAGIC_ROOM to (0 to "STATUS_FIELD_MAGIC_ROOM"),
        HnsFieldStatus.TRICK_ROOM to (1 to "STATUS_FIELD_TRICK_ROOM"),
        HnsFieldStatus.WONDER_ROOM to (2 to "STATUS_FIELD_WONDER_ROOM"),
        HnsFieldStatus.MUD_SPORT to (3 to "STATUS_FIELD_MUDSPORT"),
        HnsFieldStatus.WATER_SPORT to (4 to "STATUS_FIELD_WATERSPORT"),
        HnsFieldStatus.GRAVITY to (5 to "STATUS_FIELD_GRAVITY"),
        HnsFieldStatus.GRASSY_TERRAIN to (6 to "STATUS_FIELD_GRASSY_TERRAIN"),
        HnsFieldStatus.MISTY_TERRAIN to (7 to "STATUS_FIELD_MISTY_TERRAIN"),
        HnsFieldStatus.ELECTRIC_TERRAIN to (8 to "STATUS_FIELD_ELECTRIC_TERRAIN"),
        HnsFieldStatus.PSYCHIC_TERRAIN to (9 to "STATUS_FIELD_PSYCHIC_TERRAIN"),
        HnsFieldStatus.ION_DELUGE to (10 to "STATUS_FIELD_ION_DELUGE"),
        HnsFieldStatus.FAIRY_LOCK to (11 to "STATUS_FIELD_FAIRY_LOCK")
    )

    @Test
    fun `the generated table is exactly the twelve pinned bits in order`() {
        assertEquals(pinned.keys.toList(), HnsFieldStatus.entries.toList())
        for ((status, expected) in pinned) {
            assertEquals(status.name, expected.first, status.bit)
            assertEquals(status.name, 1 shl expected.first, status.mask)
            assertEquals(status.name, expected.second, status.sourceSymbol)
        }
        assertEquals(0x00000FFF, HnsFieldStatusData.KNOWN_MASK)
        assertEquals(0x000003C0, HnsFieldStatusData.STATUS_FIELD_TERRAIN_ANY)
        assertEquals(HnsFieldStatusData.STATUS_FIELD_ION_DELUGE, HnsBattlerRuntimeStateIds.STATUS_FIELD_ION_DELUGE)
        assertEquals(HnsFieldStatusData.PINNED_COMMIT, "1f42b74dff0e9fe942419845d040663dd829a973")
    }

    @Test
    fun `every single pinned bit decodes to exactly its condition`() {
        for (status in HnsFieldStatus.entries) {
            val state = HnsFieldState.decode(status.mask)
            assertEquals(status.mask, state.raw)
            assertEquals(setOf(status), state.active)
            assertEquals(0, state.unknownMask)
            assertTrue(state.fullyDecoded)
            assertEquals("${status.displayName} (0x%08X)".format(status.mask), state.describe())
        }
    }

    @Test
    fun `representative words decode by name`() {
        assertEquals("clear (0x00000000)", HnsFieldState.decode(0).describe())
        assertTrue(HnsFieldState.decode(0).isClear)
        assertEquals("Trick Room (0x00000002)", HnsFieldState.decode(0x2).describe())
        assertEquals("Wonder Room (0x00000004)", HnsFieldState.decode(0x4).describe())
        assertEquals("Electric Terrain (0x00000100)", HnsFieldState.decode(0x100).describe())
        assertEquals("Ion Deluge (0x00000400)", HnsFieldState.decode(0x400).describe())
        assertEquals("Trick Room + Electric Terrain (0x00000102)", HnsFieldState.decode(0x102).describe())
        assertTrue(HnsFieldState.decode(0x100).terrainActive)
        assertFalse(HnsFieldState.decode(0x422).terrainActive)
    }

    @Test
    fun `bits outside the pinned mask are preserved as unknown and never masked`() {
        val state = HnsFieldState.decode(0x2000)
        assertEquals(0x2000, state.raw)
        assertTrue(state.active.isEmpty())
        assertEquals(0x2000, state.unknownMask)
        assertFalse(state.fullyDecoded)
        assertEquals("Unknown field bits 0x00002000 (0x00002000)", state.describe())

        val high = HnsFieldState.decode(0x80000000.toInt() or 0x100)
        assertEquals(0x80000100.toInt(), high.raw)
        assertEquals(setOf(HnsFieldStatus.ELECTRIC_TERRAIN), high.active)
        assertEquals(0x80000000.toInt(), high.unknownMask)
        assertEquals("Electric Terrain + Unknown field bits 0x80000000 (0x80000100)", high.describe())

        val all = HnsFieldState.decode(-1)
        assertEquals(HnsFieldStatus.entries.toSet(), all.active)
        assertEquals(HnsFieldStatusData.KNOWN_MASK.inv(), all.unknownMask)
    }
}
