package com.dualdex.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic IWRAM fixtures only; no ROM data. */
class SmartFastForwardTest {
    private val field = 0x08085A31
    private val battle = 0x08011101
    private val bag = 0x081A0005
    private val mainOff = 0x5BD8

    private fun put(b: ByteArray, o: Int, v: Int) {
        for (i in 0..3) b[o + i] = (v ushr (8 * i)).toByte()
    }

    private fun snapshot(counter: Int, counterOffset: Int = 0x20): ByteArray {
        val b = ByteArray(SmartFastForward.IWRAM_SIZE)
        put(b, mainOff + 4, field)
        put(b, mainOff + 0xC, 0x0800ABCD)
        if (counterOffset == 0x24) put(b, mainOff + 0x20, 0x03001000)
        put(b, mainOff + counterOffset, counter)
        // Decoy: ROM pointers but a counter that advanced by 15, and a static ROM-pointer table.
        put(b, 0x100 + 4, field); put(b, 0x100 + 0xC, field); put(b, 0x100 + 0x20, counter / 2 * 15 / 8)
        put(b, 0x200 + 4, field); put(b, 0x200 + 0xC, field); put(b, 0x200 + 0x20, 77)
        return b
    }

    @Test
    fun findsMainFromTwoSnapshots() {
        assertEquals(
            SmartFastForward.Main(0x03000000 + mainOff, 0x20),
            SmartFastForward.findMain(snapshot(1000), snapshot(1016), 16)
        )
        assertEquals(
            SmartFastForward.Main(0x03000000 + mainOff, 0x24),
            SmartFastForward.findMain(snapshot(1000, 0x24), snapshot(1016, 0x24), 16)
        )
        assertNull(SmartFastForward.findMain(snapshot(1000), snapshot(1017), 16))
    }

    @Test
    fun ambiguousCandidatesAreRejected() {
        val a = snapshot(1000); val b = snapshot(1016)
        put(a, 0x400 + 4, field); put(a, 0x400 + 0xC, field); put(a, 0x400 + 0x20, 5)
        put(b, 0x400 + 4, field); put(b, 0x400 + 0xC, field); put(b, 0x400 + 0x20, 21)
        assertNull(SmartFastForward.findMain(a, b, 16))
    }

    /** Fake emulated gMain driven frame by frame. */
    private class Game {
        var cb2 = 0x08085A31
        var inBattle = false
        var counter = 500
        var x = 0
        val mem = SmartFastForward.Memory { address, length ->
            val b = ByteArray(0x440)
            put4(b, 4, cb2); put4(b, 0xC, 0x0800ABCD); put4(b, 0x20, counter)
            b[0x439] = if (inBattle) 2 else 0
            val off = address - 0x03005BD8
            if (off < 0 || off + length > b.size) return@Memory null
            b.copyOfRange(off, off + length)
        }
        private fun put4(b: ByteArray, o: Int, v: Int) { for (i in 0..3) b[o + i] = (v ushr (8 * i)).toByte() }
    }

    @Test
    fun learnsCallbacksAndSlowsOnlyForMenus() {
        val g = Game()
        var saved: SmartFastForward.Learned? = null
        val ff = SmartFastForward(g.mem, 0x03005BD8, SmartFastForward.Learned(), { g.x.toLong() }, { saved = it })
        fun run(n: Int, step: () -> Unit = {}): Boolean {
            var r = false
            repeat(n) { step(); r = ff.onFrame(); g.counter++ }
            return r
        }
        // Before learning nothing slows, even with a menu callback.
        g.cb2 = bag
        assertFalse(run(10))
        g.cb2 = field
        run(100) { g.x++ } // walking teaches the field callback
        assertEquals(field, ff.learned.field)
        assertFalse(run(5))
        g.cb2 = bag
        assertTrue(run(5)) // menu out of battle -> 1x

        g.cb2 = battle; g.inBattle = true
        run(200)
        assertEquals(battle, ff.learned.battle)
        assertFalse(run(5))
        g.cb2 = bag
        assertTrue(run(5)) // bag inside battle -> 1x
        assertEquals(SmartFastForward.Learned(field, battle), saved)
        assertEquals(saved, SmartFastForward.Learned.decode(saved!!.encode()))
    }

    @Test
    fun offDoesNoMemoryReadsAtAll() {
        val g = Game()
        var reads = 0
        val counting = SmartFastForward.Memory { a, l -> reads++; g.mem.read(a, l) }
        var on = false
        val ff = SmartFastForward(counting, 0, SmartFastForward.Learned(), { reads++; null }, isEnabled = { on })
        repeat(500) { assertFalse(ff.onFrame()); g.counter++ }
        assertEquals(0, reads)
        on = true
        repeat(20) { ff.onFrame(); g.counter++ }
        assertTrue(reads > 0)
    }

    @Test
    fun failedReverifyDropsMain() {
        val g = Game()
        val ff = SmartFastForward(g.mem, 0x03005BD8, SmartFastForward.Learned(), { null })
        repeat(10) { ff.onFrame(); g.counter++ }
        g.counter += 1000 // e.g. a save-state load or an impostor counter
        repeat(400) { ff.onFrame(); g.counter++ }
        assertNull(ff.main)
    }
}
