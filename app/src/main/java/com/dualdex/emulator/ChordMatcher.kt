package com.dualdex.emulator

/**
 * Pure hotkey-chord matcher over GBA button bits (#153).
 *
 * A chord fires when its *last* key goes down (all of [Chord.mask] held). Only that completing
 * key is withheld from the game, and only until it is released, so SELECT alone (or SELECT held
 * while walking) still reaches the game.
 */
class ChordMatcher(private val chords: List<Chord>) {

    data class Chord(val mask: Int, val id: String)

    var held = 0
        private set
    private var withheld = 0

    /** Mask the game should see. */
    val gameMask: Int get() = held and withheld.inv()

    /** Returns the chord completed by this press, if any. */
    fun down(bit: Int): Chord? {
        if (held and bit != 0) return null // repeat / already held
        held = held or bit
        val hit = chords.firstOrNull { it.mask and bit != 0 && it.mask != bit && held and it.mask == it.mask }
        if (hit != null) withheld = withheld or bit
        return hit
    }

    fun up(bit: Int) {
        held = held and bit.inv()
        withheld = withheld and bit.inv()
    }

    /** Replace the held set wholesale (e.g. analog D-pad) without firing chords. */
    fun setHeldBits(mask: Int, bits: Int) {
        held = (held and mask.inv()) or (bits and mask)
        withheld = withheld and held
    }
}
