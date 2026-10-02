package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsItemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HnsHoldEffectAuthorityTest {
    private val charcoal = 426

    private fun resolve(
        itemId: Int = charcoal,
        abilityId: Int? = 0,
        fieldStatuses: Int? = 0,
        embargo: Boolean? = false,
        gastroAcid: Boolean? = false,
        neutralizingGasOnField: Boolean? = false
    ) = HnsHoldEffectAuthority.resolve(
        itemId = itemId,
        itemProvenance = CalcItemProvenance.BATTLE_EFFECTIVE,
        abilityId = abilityId,
        fieldStatuses = fieldStatuses,
        embargo = embargo,
        gastroAcid = gastroAcid,
        neutralizingGasOnField = neutralizingGasOnField
    )

    @Test
    fun `active result preserves raw item and exact generated effect`() {
        val item = HnsItemRegistry.classify(charcoal).data
        val result = resolve()
        assertEquals(charcoal, result.itemId)
        assertEquals(item, result.item)
        assertEquals(HnsHoldEffectState.ACTIVE_EXACT, result.state)
        assertEquals(item?.holdEffect, result.effectiveHoldEffect)
    }

    @Test
    fun `Magic Room Embargo and unsuppressed Klutz suppress only effect`() {
        listOf(
            resolve(fieldStatuses = HnsFieldStatus.MAGIC_ROOM.mask),
            resolve(embargo = true),
            resolve(abilityId = 103, gastroAcid = false)
        ).forEach { result ->
            assertEquals(charcoal, result.itemId)
            assertEquals(HnsHoldEffectState.SUPPRESSED_NONE, result.state)
            assertEquals("HOLD_EFFECT_NONE", result.effectiveHoldEffect)
        }
        assertEquals(HnsHoldEffectState.ACTIVE_EXACT, resolve(abilityId = 103, gastroAcid = true).state)
        assertEquals(HnsHoldEffectState.ACTIVE_EXACT,
            resolve(abilityId = 103, gastroAcid = false, neutralizingGasOnField = true).state)
        assertEquals(HnsHoldEffectState.SUPPRESSED_NONE,
            resolve(itemId = HnsItemRegistry.resolveIdByName("Ability Shield")!!,
                abilityId = 103, gastroAcid = false, neutralizingGasOnField = true).state)
        assertEquals(HnsHoldEffectState.UNKNOWN,
            resolve(abilityId = 103, gastroAcid = false, neutralizingGasOnField = null).state)
    }

    @Test
    fun `missing suppression operands remain unknown`() {
        assertEquals(HnsHoldEffectState.UNKNOWN, resolve(fieldStatuses = null).state)
        assertEquals(HnsHoldEffectState.UNKNOWN, resolve(embargo = null).state)
        assertEquals(HnsHoldEffectState.UNKNOWN, resolve(abilityId = null).state)
        assertEquals(HnsHoldEffectState.UNKNOWN, resolve(abilityId = 103, gastroAcid = null).state)
        assertNull(resolve(fieldStatuses = null).effectiveHoldEffect)
    }
}
