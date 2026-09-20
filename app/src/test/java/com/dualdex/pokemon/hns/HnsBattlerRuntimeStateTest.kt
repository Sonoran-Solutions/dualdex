package com.dualdex.pokemon.hns

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.Gen3VanillaDataPack
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.ProfileOverlayDataPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live H&S 2.0.5 battler runtime state at the Kotlin/production boundary (issue #9).
 *
 * The native reader owns the memory observation; these tests pin the tuple decode,
 * the ability-catalogue naming boundary, and the sentinel/domain honesty of the
 * type observations. Ability IDs below are pinned from the upstream
 * include/constants/abilities.h (Release-v2.0.5); type IDs from
 * include/constants/pokemon.h.
 *
 * Scope: this is observability only. A resolved ability identity is a NAME for an
 * observed ID — never evidence that the damage calculator models the ability.
 */
class HnsBattlerRuntimeStateTest {

    // ------------------------------------------------------------------
    // Tuple decode
    // ------------------------------------------------------------------

    private fun observedTuple(
        battler: Int = 0,
        slot: Int = 0,
        ability: Int = 66,
        abilityInvalid: Int = 0,
        types: List<Int> = listOf(11, 0, 0),
        typesInvalid: Int = 0
    ): IntArray = intArrayOf(
        /* status */ 2, /* battler */ battler, /* slot */ slot, /* slotKnown */ 1,
        /* abilityObserved */ 1, /* abilityInvalid */ abilityInvalid, /* abilityId */ ability,
        /* typesObserved */ 1, /* typesInvalid */ typesInvalid, /* count */ types.size,
        /* types */ *(types + List(3 - types.size) { 0 }).toIntArray()
    )

    @Test
    fun `observed tuple decodes with authoritative provenance`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(observedTuple())
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED, st.status)
        assertEquals(0, st.battlerIndex)
        assertEquals(0, st.partySlot)
        assertEquals(66, st.abilityId)
        assertFalse(st.abilityOutOfDomain)
        assertEquals(3, st.types.size)
        assertEquals(11, st.types[0].raw)
        assertTrue(st.types[0].observed)
        assertFalse(st.typesOutOfDomain)
    }

    @Test
    fun `unavailable and malformed tuples decode to a clean default`() {
        for (raw in listOf<IntArray?>(null, intArrayOf(), intArrayOf(0), IntArray(12))) {
            val st = HnsBattlerRuntimeState.fromNativeArray(raw)
            assertEquals(HnsBattlerRuntimeStatus.UNAVAILABLE, st.status)
            assertNull(st.battlerIndex)
            assertNull(st.partySlot)
            assertNull(st.abilityId)
            assertTrue(st.types.isEmpty())
        }
        // An explicit unavailable status with trailing zeros must not invent fields.
        val st = HnsBattlerRuntimeState.fromNativeArray(intArrayOf(0, 3, 2, 1, 1, 0, 66, 1, 0, 3, 11, 0, 0))
        assertEquals(HnsBattlerRuntimeStatus.UNAVAILABLE, st.status)
        assertNull(st.abilityId)
    }

    @Test
    fun `ambiguous status carries no observation at all`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(intArrayOf(1, 3, 2, 1, 1, 0, 66, 1, 0, 3, 1, 2, 3))
        assertEquals(HnsBattlerRuntimeStatus.AMBIGUOUS, st.status)
        assertNull(st.battlerIndex)
        assertNull(st.abilityId)
        assertTrue(st.types.isEmpty())
    }

    @Test
    fun `unknown party slot is never coerced to slot 0`() {
        val raw = observedTuple(slot = -1)
        raw[3] = 0 // partySlotKnown = false
        val st = HnsBattlerRuntimeState.fromNativeArray(raw)
        assertNull(st.partySlot)
    }

    // ------------------------------------------------------------------
    // Ability identity resolution (naming only)
    // ------------------------------------------------------------------

    @Test
    fun `observed ability id resolves through the pinned catalogue`() {
        val pack = HeartAndSoul205DataPack
        val st = HnsBattlerRuntimeState.fromNativeArray(observedTuple(ability = 66))
        val identity = st.resolveAbilityIdentity(pack)
        assertEquals(DeclaredAbility.Declared(66, "BLAZE"), identity)
        // A second, independently pinned ID: 51 = KEEN_EYE.
        assertEquals(
            DeclaredAbility.Declared(51, "KEEN EYE"),
            HnsBattlerRuntimeState.fromNativeArray(observedTuple(ability = 51))
                .resolveAbilityIdentity(pack)
        )
    }

    @Test
    fun `runtime-observed ids resolve through the pinned catalogue`() {
        // Pinned from the upstream enum; these exact IDs were also observed live on the
        // official 2.0.5 release ROM (see the compatibility evidence document).
        val pack = HeartAndSoul205DataPack
        val cases = mapOf(
            65 to "OVERGROW",   // Chikorita declared slot 0, wild battle
            62 to "GUTS",       // trainer Rattata, effective slot 1
            51 to "KEEN EYE",   // wild Hoothoot, effective slot 1 (differs from naive slot 0)
            15 to "INSOMNIA"    // trainer Hoothoot, declared slot 0
        )
        for ((id, name) in cases) {
            val identity = HnsBattlerRuntimeState.fromNativeArray(observedTuple(ability = id))
                .resolveAbilityIdentity(pack)
            assertEquals(DeclaredAbility.Declared(id, name), identity)
        }
    }

    @Test
    fun `unresolved in-domain id stays explicitly absent, never substituted`() {
        // ABILITY_NONE (0) is part of the pinned enum: a legitimate observed state
        // whose catalogue identity is an explicit absence.
        val st = HnsBattlerRuntimeState.fromNativeArray(observedTuple(ability = 0))
        assertEquals(DeclaredAbility.Absent, st.resolveAbilityIdentity(HeartAndSoul205DataPack))
    }

    @Test
    fun `out-of-domain ability is never named and never substituted`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(
            observedTuple(ability = HnsBattlerRuntimeStateIds.ABILITY_ID_MAX + 5, abilityInvalid = 1)
        )
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED_INVALID, st.status)
        assertEquals(HnsBattlerRuntimeStateIds.ABILITY_ID_MAX + 5, st.abilityId)
        assertNull(st.resolveAbilityIdentity(HeartAndSoul205DataPack))
    }

    @Test
    fun `identity requires the exact hns catalogue`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(observedTuple(ability = 66))
        assertNull(st.resolveAbilityIdentity(Gen3VanillaDataPack))
        assertNull(st.resolveAbilityIdentity(null))
        // A profile overlay wrapping the H&S pack still exposes the pinned catalogue.
        val overlay = ProfileOverlayDataPack(HeartAndSoul205DataPack, emptyMap())
        assertEquals(DeclaredAbility.Declared(66, "BLAZE"), st.resolveAbilityIdentity(overlay))
    }

    @Test
    fun `unavailable state resolves no identity`() {
        assertNull(HnsBattlerRuntimeState.fromNativeArray(null).resolveAbilityIdentity(HeartAndSoul205DataPack))
    }

    // ------------------------------------------------------------------
    // Type observations
    // ------------------------------------------------------------------

    @Test
    fun `type names follow the pinned hns type ids, not the vanilla gba ordering`() {
        // In the pinned enum, 1 = NORMAL (the vanilla GBA ordering maps 0x01 to FIGHTING).
        assertEquals("Normal", hnsRuntimeTypeName(1))
        assertEquals("Fighting", hnsRuntimeTypeName(2))
        assertEquals("Flying", hnsRuntimeTypeName(3))
        assertEquals("Fairy", hnsRuntimeTypeName(19))
        assertEquals("Stellar", hnsRuntimeTypeName(20))
    }

    @Test
    fun `empty-slot sentinel is explicitly null, never Normal`() {
        assertNull(hnsRuntimeTypeName(0))
        val st = HnsBattlerRuntimeState.fromNativeArray(observedTuple(types = listOf(13, 4, 0)))
        assertEquals("Grass", st.types[0].name)
        assertEquals("Poison", st.types[1].name)
        assertNull(st.types[2].name)
        assertTrue(st.types[2].isTypeNoneSentinel)
        assertFalse(st.types[0].isTypeNoneSentinel)
    }

    @Test
    fun `battle-only typeless value keeps its own name`() {
        assertEquals("Mystery", hnsRuntimeTypeName(10))
        val st = HnsBattlerRuntimeState.fromNativeArray(observedTuple(types = listOf(10, 10, 10)))
        assertTrue(st.types.all { it.name == "Mystery" && !it.outOfDomain })
    }

    @Test
    fun `out-of-domain type stays raw and unnamed`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(
            observedTuple(types = listOf(13, HnsBattlerRuntimeStateIds.TYPE_ID_MAX + 1, 0), typesInvalid = 1)
        )
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED_INVALID, st.status)
        assertTrue(st.typesOutOfDomain)
        assertEquals(HnsBattlerRuntimeStateIds.TYPE_ID_MAX + 1, st.types[1].raw)
        assertNull(st.types[1].name)
        // In-domain slots are still observed normally.
        assertEquals("Grass", st.types[0].name)
    }
}
