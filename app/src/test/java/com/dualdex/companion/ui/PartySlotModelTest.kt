package com.dualdex.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartySlotModelTest {

    private fun slotState(
        name: String = "Pikachu",
        level: Int = 25,
        currentHp: Int = 55,
        maxHp: Int = 55,
        status: String? = null,
        gender: Char? = '♂',
        shiny: Boolean = false,
        isEgg: Boolean = false,
        selected: Boolean = false,
        isFallenNuzlocke: Boolean = false
    ) = PartySlotState(
        name = name,
        level = level,
        currentHp = currentHp,
        maxHp = maxHp,
        status = status,
        gender = gender,
        shiny = shiny,
        isEgg = isEgg,
        selected = selected,
        isFallenNuzlocke = isFallenNuzlocke
    )

    @Test
    fun fallenNuzlockeDefaultsToFalseForBackwardCompatibility() {
        val legacyState = PartySlotState(
            name = "Mudkip",
            level = 5,
            currentHp = 20,
            maxHp = 20,
            status = null,
            gender = '♂',
            shiny = false,
            isEgg = false,
            selected = false
        )
        assertFalse(legacyState.isFallenNuzlocke)
        assertEquals(
            "Party slot 1, Mudkip, level 5, HP 20 of 20",
            PartySlotModel.accessibilityLabel(0, legacyState)
        )
    }

    @Test
    fun fallenNuzlockeParticipatesInStateEquality() {
        val alive = slotState(isFallenNuzlocke = false)
        val fallen = slotState(isFallenNuzlocke = true)
        assertNotEquals(alive, fallen)
        assertEquals(fallen, alive.copy(isFallenNuzlocke = true))
        assertTrue(fallen.isFallenNuzlocke)
    }

    @Test
    fun accessibilityLabelIncludesFallenNuzlockeWhenFlagged() {
        val fallenHealed = slotState(
            name = "Sparky",
            level = 18,
            currentHp = 45,
            maxHp = 45,
            status = null,
            selected = false,
            isFallenNuzlocke = true
        )
        assertEquals(
            "Party slot 2, Sparky, level 18, HP 45 of 45, fallen (Nuzlocke)",
            PartySlotModel.accessibilityLabel(1, fallenHealed)
        )

        val fallenFaintedSelected = slotState(
            name = "Sparky",
            level = 18,
            currentHp = 0,
            maxHp = 45,
            status = "FNT",
            selected = true,
            isFallenNuzlocke = true
        )
        assertEquals(
            "Party slot 1, Sparky, level 18, HP 0 of 45, fainted, fallen (Nuzlocke), selected",
            PartySlotModel.accessibilityLabel(0, fallenFaintedSelected)
        )

        val egg = slotState(isEgg = true, isFallenNuzlocke = true, selected = true)
        assertEquals(
            "Party slot 3, Egg, selected",
            PartySlotModel.accessibilityLabel(2, egg)
        )
    }
}
