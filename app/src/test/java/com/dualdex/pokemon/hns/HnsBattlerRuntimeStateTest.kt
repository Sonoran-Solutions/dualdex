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
        typesInvalid: Int = 0,
        item: Int = 426,
        itemInvalid: Int = 0
    ): IntArray = intArrayOf(
        /* status */ 2, /* battler */ battler, /* slot */ slot, /* slotKnown */ 1,
        /* abilityObserved */ 1, /* abilityInvalid */ abilityInvalid, /* abilityId */ ability,
        /* typesObserved */ 1, /* typesInvalid */ typesInvalid, /* count */ types.size,
        /* types */ *(types + List(3 - types.size) { 0 }).toIntArray(),
        /* itemObserved */ 1, /* itemInvalid */ itemInvalid, /* itemId */ item
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
        assertEquals(426, st.itemId)
        assertFalse(st.itemOutOfDomain)
    }

    @Test
    fun `unavailable and malformed tuples decode to a clean default`() {
        for (raw in listOf<IntArray?>(null, intArrayOf(), intArrayOf(0), IntArray(12))) {
            val st = HnsBattlerRuntimeState.fromNativeArray(raw)
            assertEquals(HnsBattlerRuntimeStatus.UNAVAILABLE, st.status)
            assertNull(st.battlerIndex)
            assertNull(st.partySlot)
            assertNull(st.abilityId)
            assertNull(st.itemId)
            assertTrue(st.types.isEmpty())
        }
        // An explicit unavailable status with trailing zeros must not invent fields.
        val st = HnsBattlerRuntimeState.fromNativeArray(
            intArrayOf(0, 3, 2, 1, 1, 0, 66, 1, 0, 3, 11, 0, 0, 1, 0, 426)
        )
        assertEquals(HnsBattlerRuntimeStatus.UNAVAILABLE, st.status)
        assertNull(st.abilityId)
        assertNull(st.itemId)
    }

    @Test
    fun `ambiguous status carries no observation at all`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(
            intArrayOf(1, 3, 2, 1, 1, 0, 66, 1, 0, 3, 1, 2, 3, 1, 0, 426)
        )
        assertEquals(HnsBattlerRuntimeStatus.AMBIGUOUS, st.status)
        assertNull(st.battlerIndex)
        assertNull(st.abilityId)
        assertNull(st.itemId)
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
    // Gap C4e live-operand tuple decode (slots 42-59)
    // ------------------------------------------------------------------

    /**
     * A full 60-int native tuple with every Gap C4e operand independently settable. The
     * production JNI emits this layout; constructing it here (rather than building the Kotlin
     * object directly) is what exercises the exact decoder production depends on.
     */
    private fun c4eObservedTuple(
        hpObserved: Int = 1,
        hp: Int = 14,
        maxHp: Int = 20,
        statusObserved: Int = 1,
        status1: Int = 0,
        volatilesObserved: Int = 1,
        electrified: Int = 0,
        glaiveRush: Int = 0,
        minimize: Int = 0,
        semiInvulnerable: Int = 0,
        gimmickObserved: Int = 1,
        activeGimmick: Int = 0,
        fieldStatusesReadable: Int = 1,
        fieldStatuses: Int = 0,
        weatherReadable: Int = 1,
        battleWeather: Int = 0,
        sideStatusesReadable: Int = 1,
        sideStatuses: Int = 0
    ): IntArray {
        val t = IntArray(60)
        /* [0..15] identity / ability / types / item */
        t[0] = 2 // OBSERVED
        t[1] = 0
        t[2] = 0
        t[3] = 1
        t[4] = 1
        t[5] = 0
        t[6] = 66
        t[7] = 1
        t[8] = 0
        t[9] = 3
        t[10] = 11
        t[11] = 0
        t[12] = 0
        t[13] = 1
        t[14] = 0
        t[15] = 426
        /* [16..21] raw battle stats */
        t[16] = 1
        t[17] = 100
        t[18] = 80
        t[19] = 70
        t[20] = 90
        t[21] = 85
        /* [22..30] stat stages (hp..eva) */
        t[22] = 1
        for (s in 0 until 8) t[23 + s] = s - 4
        /* [31..37] badge state */
        t[31] = 1
        t[32] = 1
        t[33] = 0
        t[34] = 1
        t[35] = 0
        t[36] = 1
        t[37] = 0x91
        /* [38..41] battle-level topology */
        t[38] = 0b0010
        t[39] = 1
        t[40] = 2
        t[41] = 1
        /* [42..55] Gap C4e live operands */
        t[42] = hpObserved
        t[43] = hp
        t[44] = maxHp
        t[45] = statusObserved
        t[46] = status1
        t[47] = volatilesObserved
        t[48] = electrified
        t[49] = glaiveRush
        t[50] = minimize
        t[51] = semiInvulnerable
        t[52] = gimmickObserved
        t[53] = activeGimmick
        t[54] = fieldStatusesReadable
        t[55] = fieldStatuses
        /* [56..59] live field conditions */
        t[56] = weatherReadable
        t[57] = battleWeather
        t[58] = sideStatusesReadable
        t[59] = sideStatuses
        return t
    }

    /**
     * The correction-pass 62-int tuple: the accepted 60-int C4e layout extended with
     * `[60] chargeTimer` and `[61] tarShot`. Kept separate from [c4eObservedTuple] so the
     * accepted 60-slot decoder tests stay byte-for-byte unchanged.
     */
    private fun c4eTransientTuple(
        chargeTimer: Int = 0,
        tarShot: Int = 0,
        volatilesObserved: Int = 1
    ): IntArray = c4eObservedTuple(volatilesObserved = volatilesObserved) +
        intArrayOf(chargeTimer, tarShot)

    @Test
    fun `62-element tuple decodes the charge timer and tar shot`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(c4eTransientTuple(chargeTimer = 2, tarShot = 1))
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED, st.status)
        assertTrue(st.transientVolatilesObserved)
        assertEquals(2, st.volatileChargeTimer)
        assertTrue(st.volatileTarShot)
    }

    @Test
    fun `60-element tuple leaves charge timer and tar shot explicitly unobserved`() {
        // The accepted 60-int contract still decodes, but the correction-pass operands are
        // absent rather than defaulted to a neutral charge/tar-shot observation.
        val st = HnsBattlerRuntimeState.fromNativeArray(c4eObservedTuple())
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED, st.status)
        assertFalse(st.transientVolatilesObserved)
        assertEquals(0, st.volatileChargeTimer)
        assertFalse(st.volatileTarShot)
    }

    @Test
    fun `unread volatile window keeps charge timer and tar shot unobserved`() {
        // Payload slots hold tempting values, but the shared volatile window was not read, so
        // neither operand may be promoted to an observation.
        val st = HnsBattlerRuntimeState.fromNativeArray(
            c4eTransientTuple(chargeTimer = 3, tarShot = 1, volatilesObserved = 0)
        )
        assertFalse(st.transientVolatilesObserved)
        assertEquals(0, st.volatileChargeTimer)
        assertFalse(st.volatileTarShot)
    }

    @Test
    fun `full observed tuple decodes every C4e live operand`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(
            c4eObservedTuple(
                hp = 14,
                maxHp = 20,
                status1 = 0x10,
                electrified = 1,
                glaiveRush = 1,
                minimize = 1,
                semiInvulnerable = 3,
                activeGimmick = 5,
                fieldStatuses = 0x400,
                battleWeather = 0x7,
                sideStatuses = 0x3
            )
        )
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED, st.status)
        // Older fields decode unchanged alongside the new ones.
        assertEquals(66, st.abilityId)
        assertEquals(426, st.itemId)
        assertEquals(100, st.rawAttack)
        assertEquals(listOf(-4, -3, -2, -1, 0, 1, 2, 3), st.statStages)
        // [42..46]
        assertTrue(st.hpObserved)
        assertEquals(14, st.hp)
        assertEquals(20, st.maxHp)
        assertTrue(st.statusObserved)
        assertEquals(0x10, st.status1)
        // [47..51]
        assertTrue(st.volatilesObserved)
        assertTrue(st.volatileElectrified)
        assertTrue(st.volatileGlaiveRush)
        assertTrue(st.volatileMinimize)
        assertEquals(3, st.volatileSemiInvulnerable)
        // [52..55]
        assertTrue(st.gimmickObserved)
        assertEquals(5, st.activeGimmick)
        assertTrue(st.fieldStatusesReadable)
        assertEquals(0x400, st.fieldStatuses)
        // [56..59]
        assertTrue(st.weatherReadable)
        assertEquals(0x7, st.battleWeather)
        assertTrue(st.sideStatusesReadable)
        assertEquals(0x3, st.sideStatuses)
    }

    @Test
    fun `observed bit false keeps a nonzero payload non-authoritative`() {
        // Every readability bit clear while the payload slots hold tempting nonzero values: the
        // decoder must report the field unobserved and must NOT promote the payload.
        val st = HnsBattlerRuntimeState.fromNativeArray(
            c4eObservedTuple(
                hpObserved = 0,
                hp = 999,
                maxHp = 999,
                statusObserved = 0,
                status1 = 0x10,
                volatilesObserved = 0,
                electrified = 1,
                glaiveRush = 1,
                minimize = 1,
                semiInvulnerable = 3,
                gimmickObserved = 0,
                activeGimmick = 5,
                fieldStatusesReadable = 0,
                fieldStatuses = 0x400,
                weatherReadable = 0,
                battleWeather = 0x7,
                sideStatusesReadable = 0,
                sideStatuses = 0x3
            )
        )
        assertFalse(st.hpObserved)
        assertEquals(0, st.hp)
        assertEquals(0, st.maxHp)
        assertFalse(st.statusObserved)
        assertEquals(0, st.status1)
        assertFalse(st.volatilesObserved)
        assertFalse(st.volatileElectrified)
        assertFalse(st.volatileGlaiveRush)
        assertFalse(st.volatileMinimize)
        assertEquals(0, st.volatileSemiInvulnerable)
        assertFalse(st.gimmickObserved)
        assertEquals(0, st.activeGimmick)
        assertFalse(st.fieldStatusesReadable)
        assertEquals(0, st.fieldStatuses)
        assertFalse(st.weatherReadable)
        assertEquals(0, st.battleWeather)
        assertFalse(st.sideStatusesReadable)
        assertEquals(0, st.sideStatuses)
    }

    @Test
    fun `legacy 42-element tuple leaves every C4e operand explicitly unobserved`() {
        val legacy = c4eObservedTuple().copyOfRange(0, 42)
        val st = HnsBattlerRuntimeState.fromNativeArray(legacy)
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED, st.status)
        // The pre-C4e contract still decodes.
        assertEquals(66, st.abilityId)
        assertEquals(426, st.itemId)
        assertEquals(100, st.rawAttack)
        // Every C4e field is unobserved, not defaulted to a neutral-looking observation.
        assertFalse(st.hpObserved)
        assertEquals(0, st.hp)
        assertEquals(0, st.maxHp)
        assertFalse(st.statusObserved)
        assertEquals(0, st.status1)
        assertFalse(st.volatilesObserved)
        assertFalse(st.volatileElectrified)
        assertFalse(st.volatileGlaiveRush)
        assertFalse(st.gimmickObserved)
        assertEquals(0, st.activeGimmick)
        assertFalse(st.fieldStatusesReadable)
        assertEquals(0, st.fieldStatuses)
        assertFalse(st.weatherReadable)
        assertEquals(0, st.battleWeather)
        assertFalse(st.sideStatusesReadable)
        assertEquals(0, st.sideStatuses)
    }

    @Test
    fun `56-element tuple decodes the C4e operands but leaves weather and screens unobserved`() {
        val shorter = c4eObservedTuple(hp = 14, maxHp = 20, battleWeather = 0x7, sideStatuses = 0x3)
            .copyOfRange(0, 56)
        val st = HnsBattlerRuntimeState.fromNativeArray(shorter)
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED, st.status)
        assertTrue(st.hpObserved)
        assertEquals(14, st.hp)
        assertEquals(20, st.maxHp)
        assertTrue(st.fieldStatusesReadable)
        // The live field-condition operands are only decoded from the 60-int tuple.
        assertFalse(st.weatherReadable)
        assertEquals(0, st.battleWeather)
        assertFalse(st.sideStatusesReadable)
        assertEquals(0, st.sideStatuses)
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

    // ------------------------------------------------------------------
    // Current held-item identity resolution (naming only)
    // ------------------------------------------------------------------

    @Test
    fun `observed item id resolves through the pinned catalogue`() {
        // 426 = ITEM_CHARCOAL, 442 = ITEM_CHOICE_BAND (pinned from the upstream enum Item).
        assertEquals(
            "CHARCOAL",
            HnsBattlerRuntimeState.fromNativeArray(observedTuple(item = 426)).resolveItemIdentity()?.sourceName
        )
        assertEquals(
            "CHOICE BAND",
            HnsBattlerRuntimeState.fromNativeArray(observedTuple(item = 442)).resolveItemIdentity()?.sourceName
        )
    }

    @Test
    fun `item none is an identity, not an absence of observation`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(observedTuple(item = 0))
        assertEquals(0, st.itemId)
        assertFalse(st.itemOutOfDomain)
        assertEquals("ITEM_NONE", st.resolveItemIdentity()?.canonicalSymbol)
    }

    @Test
    fun `out-of-domain item is never named and never substituted`() {
        val st = HnsBattlerRuntimeState.fromNativeArray(
            observedTuple(item = HnsBattlerRuntimeStateIds.ITEM_ID_MAX + 1, itemInvalid = 1)
        )
        assertEquals(HnsBattlerRuntimeStatus.OBSERVED_INVALID, st.status)
        assertEquals(HnsBattlerRuntimeStateIds.ITEM_ID_MAX + 1, st.itemId)
        assertNull(st.resolveItemIdentity())
    }

    @Test
    fun `unavailable state resolves no item identity`() {
        assertNull(HnsBattlerRuntimeState.fromNativeArray(null).resolveItemIdentity())
    }
}
