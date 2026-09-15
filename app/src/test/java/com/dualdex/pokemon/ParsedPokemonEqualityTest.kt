package com.dualdex.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ParsedPokemonEqualityTest {

    private val baseMon = ParsedPokemon(
        isValid = true,
        isEmpty = false,
        pid = 0x12345678L,
        tid = 12345,
        sid = 54321,
        nickname = "Pikachu",
        otName = "Ash",
        species = 25,
        heldItem = 0,
        level = 50,
        nature = 0,
        natureName = "Hardy",
        isShiny = false,
        abilitySlot = 0,
        isEgg = false,
        friendship = 70,
        experience = 1000L,
        hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(1, 2, 3, 4),
        pp = intArrayOf(35, 20, 15, 10),
        currentHp = 100, maxHp = 100,
        attack = 50, defense = 50, speed = 50, spAttack = 50, spDefense = 50,
        statusCondition = 0L,
        hiddenNature = 0,
        natureModified = false,
        gigantamaxFactor = false,
        shinyState = ParsedPokemon.SHINY_NO,
        shinyModifier = 0
    )

    @Test
    fun objectsDifferingOnlyInHiddenNatureAreNotEqual() {
        val differing = baseMon.copy(hiddenNature = 4)
        assertNotEquals(baseMon, differing)
        assertNotEquals(differing, baseMon)
        assertNotEquals(baseMon.hashCode(), differing.hashCode())
    }

    @Test
    fun objectsDifferingOnlyInNatureModifiedAreNotEqual() {
        val differing = baseMon.copy(natureModified = true)
        assertNotEquals(baseMon, differing)
        assertNotEquals(differing, baseMon)
        assertNotEquals(baseMon.hashCode(), differing.hashCode())
    }

    @Test
    fun objectsDifferingOnlyInGigantamaxFactorAreNotEqual() {
        val differing = baseMon.copy(gigantamaxFactor = true)
        assertNotEquals(baseMon, differing)
        assertNotEquals(differing, baseMon)
        assertNotEquals(baseMon.hashCode(), differing.hashCode())
    }

    @Test
    fun objectsDifferingOnlyInShinyStateAreNotEqual() {
        val differing = baseMon.copy(shinyState = ParsedPokemon.SHINY_YES)
        assertNotEquals(baseMon, differing)
        assertNotEquals(differing, baseMon)
        assertNotEquals(baseMon.hashCode(), differing.hashCode())
    }

    @Test
    fun objectsDifferingOnlyInShinyModifierAreNotEqual() {
        val differing = baseMon.copy(shinyModifier = 1)
        assertNotEquals(baseMon, differing)
        assertNotEquals(differing, baseMon)
        assertNotEquals(baseMon.hashCode(), differing.hashCode())
    }

    @Test
    fun equalObjectsProduceEqualHashCodes() {
        val identicalClone = baseMon.copy(
            moves = baseMon.moves.clone(),
            pp = baseMon.pp.clone()
        )
        assertEquals(baseMon, identicalClone)
        assertEquals(identicalClone, baseMon)
        assertEquals(baseMon.hashCode(), identicalClone.hashCode())
    }

    @Test
    fun contentBasedHandlingForMovesAndPpArraysIsPreserved() {
        val differentMoves = baseMon.copy(moves = intArrayOf(99, 2, 3, 4))
        assertNotEquals(baseMon, differentMoves)
        assertNotEquals(baseMon.hashCode(), differentMoves.hashCode())

        val differentPp = baseMon.copy(pp = intArrayOf(0, 20, 15, 10))
        assertNotEquals(baseMon, differentPp)
        assertNotEquals(baseMon.hashCode(), differentPp.hashCode())
    }
}
