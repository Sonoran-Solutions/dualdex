package com.dualdex.calculator

import com.dualdex.pokemon.hns.Hns205MoveEffects as Status

/** Validate without normalizing the source word; sleep is a three-bit turn count. */
internal object HnsDefenderStatus {
    fun isValid(word: Int?): Boolean {
        if (word == null || word < 0 || (word and (Status.STATUS1_ANY or Status.STATUS1_TOXIC_COUNTER).inv()) != 0) return false
        if ((word and Status.STATUS1_TOXIC_COUNTER) != 0 && (word and Status.STATUS1_TOXIC_POISON) == 0) return false
        val primary = word and Status.STATUS1_ANY
        val sleep = primary and Status.STATUS1_SLEEP
        val other = primary and Status.STATUS1_SLEEP.inv()
        return if (sleep != 0) other == 0 else Integer.bitCount(other) <= 1
    }
}
