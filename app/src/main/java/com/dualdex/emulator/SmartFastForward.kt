package com.dualdex.emulator

/**
 * Smart fast-forward: tells the emulation loop to drop to 1x while a menu screen (party, bag,
 * summary, PC, dex, region map, options...) is open, and to stay fast on the overworld and in
 * battles. Pure logic; all emulator access goes through [Memory] and [location].
 *
 * Rule (Gen III decomp engines): the overworld and the battle each run one fixed
 * `gMain.callback2`; every full-screen menu switches callback2 to its own ROM function, while the
 * START menu and dialogue are overlays that keep the field's. So "menu open" = callback2 is a ROM
 * pointer that is neither the learned field nor the learned battle callback. In battle we only
 * slow down once the battle callback is known, i.e. for the bag/party sub-screens.
 *
 * `gMain` comes from the verified profile when there is one, else it is discovered from two IWRAM
 * snapshots [DISCOVERY_GAP] frames apart (see [findMain]). It is re-verified every
 * [VERIFY_INTERVAL] frames because a task's frame counter once impersonated it.
 */
class SmartFastForward(
    private val memory: Memory,
    knownMainAddress: Int,
    learned: Learned,
    /** Packed player position (map/x/y); null when unreadable. Polled every [LOCATION_INTERVAL] frames. */
    private val location: () -> Long?,
    private val onLearned: (Learned) -> Unit = {},
    /** Read each frame so the settings toggle applies live; while off nothing is read or learned. */
    private val isEnabled: () -> Boolean = { true },
) {
    fun interface Memory { fun read(address: Int, length: Int): ByteArray? }

    /** Learned callbacks per ROM; 0 = not learned yet. */
    data class Learned(val field: Int = 0, val battle: Int = 0) {
        fun encode() = "${field.toUInt()},${battle.toUInt()}"
        companion object {
            fun decode(s: String?): Learned {
                val p = s?.split(',')?.mapNotNull { it.toUIntOrNull()?.toInt() } ?: return Learned()
                return if (p.size == 2) Learned(p[0], p[1]) else Learned()
            }
        }
    }

    /** A located `gMain`: absolute address and the offset of its vblank counter (+0x20, FireRed +0x24). */
    data class Main(val address: Int, val counterOffset: Int)

    var learned: Learned = learned
        private set
    var main: Main? = knownMainAddress.takeIf { it != 0 }?.let { Main(it, 0x20) }
        private set

    private var frame = 0L
    private var firstSnapshot: ByteArray? = null
    private var firstSnapshotFrame = 0L
    private var lastVerifyFrame = 0L
    private var lastVerifyCounter: Long = -1
    private var lastLocation: Long? = null
    private var lastLocationCb2 = 0
    private var battleCb2 = 0
    private var battleStableFrames = 0
    private var battleLearnDone = false

    /** Call once per emulated frame. Returns true when the game should run at 1x. */
    fun onFrame(): Boolean {
        // Off means no memory reads at all; re-baseline the counter check when switched back on.
        if (!isEnabled()) { lastVerifyCounter = -1; return false }
        frame++
        val m = main ?: run { discover(); return false }
        if (frame - lastVerifyFrame >= VERIFY_INTERVAL || lastVerifyCounter < 0) {
            if (!verify(m)) return false
        }
        val cb2 = u32(memory.read(m.address + 4, 4) ?: return false, 0)
        val flags = memory.read(m.address + IN_BATTLE_BYTE + (m.counterOffset - 0x20), 1) ?: return false
        val inBattle = (flags[0].toInt() shr IN_BATTLE_BIT) and 1 == 1
        learn(cb2, inBattle)
        return isMenu(cb2, inBattle)
    }

    private fun isMenu(cb2: Int, inBattle: Boolean): Boolean {
        val l = learned
        if (!isRomPointer(cb2) || l.field == 0 || cb2 == l.field || cb2 == l.battle) return false
        return !inBattle || l.battle != 0
    }

    private fun learn(cb2: Int, inBattle: Boolean) {
        if (inBattle) {
            if (!battleLearnDone) {
                battleStableFrames = if (cb2 == battleCb2) battleStableFrames + 1 else 0
                battleCb2 = cb2
                // The first callback that stays put for BATTLE_STABLE_FRAMES after the battle starts
                // is the battle main loop (intro alone outlasts the brief init callbacks; no menu
                // can be opened before it), so learn it once per battle.
                if (battleStableFrames >= BATTLE_STABLE_FRAMES) {
                    battleLearnDone = true
                    if (isRomPointer(cb2) && cb2 != learned.field && cb2 != learned.battle) {
                        update(learned.copy(battle = cb2))
                    }
                }
            }
            lastLocation = null
            return
        }
        battleLearnDone = false
        battleStableFrames = 0
        if (frame % LOCATION_INTERVAL != 0L) return
        val loc = location()
        // The player only moves while the field callback runs; require the same callback at both
        // samples so a menu opened between them is never learned.
        if (loc != null && lastLocation != null && loc != lastLocation && cb2 == lastLocationCb2 &&
            isRomPointer(cb2) && cb2 != learned.field
        ) {
            update(learned.copy(field = cb2))
        }
        lastLocation = loc
        lastLocationCb2 = cb2
    }

    private fun update(l: Learned) {
        learned = l
        onLearned(l)
    }

    private fun verify(m: Main): Boolean {
        val bytes = memory.read(m.address, 0x28)
        val counter = bytes?.let { u32(it, m.counterOffset).toLong() and 0xFFFFFFFFL }
        val ok = bytes != null && isRomPointer(u32(bytes, 4)) &&
            (lastVerifyCounter < 0 || counter!! - lastVerifyCounter == frame - lastVerifyFrame)
        if (!ok) {
            main = null
            lastVerifyCounter = -1
            firstSnapshot = null
            return false
        }
        lastVerifyCounter = counter!!
        lastVerifyFrame = frame
        return true
    }

    private fun discover() {
        val first = firstSnapshot
        if (first == null) {
            firstSnapshot = memory.read(IWRAM_BASE, IWRAM_SIZE)
            firstSnapshotFrame = frame
            return
        }
        if (frame - firstSnapshotFrame < DISCOVERY_GAP) return
        firstSnapshot = null
        val second = memory.read(IWRAM_BASE, IWRAM_SIZE) ?: return
        findMain(first, second, DISCOVERY_GAP.toInt())?.let {
            main = it
            lastVerifyCounter = -1
        }
    }

    companion object {
        const val IWRAM_BASE = 0x03000000
        const val IWRAM_SIZE = 0x8000
        const val DISCOVERY_GAP = 16L
        const val VERIFY_INTERVAL = 300L
        const val LOCATION_INTERVAL = 30L
        const val BATTLE_STABLE_FRAMES = 120
        // pokeemerald/pokefirered struct Main: `u8 state` at 0x438, then the bitfield byte whose
        // bit 1 is inBattle. ponytail: FireRed's layout is assumed to shift by the same 4 bytes as
        // its counter; if a hack disagrees, battle sub-screen slow-down is all that misbehaves.
        private const val IN_BATTLE_BYTE = 0x439
        private const val IN_BATTLE_BIT = 1

        fun isRomPointer(v: Int) = (v ushr 24) in 0x08..0x09

        fun packLocation(mapGroup: Int, mapNum: Int, x: Int, y: Int): Long =
            ((mapGroup.toLong() and 0xFF) shl 48) or ((mapNum.toLong() and 0xFF) shl 40) or
                ((x.toLong() and 0xFFFF) shl 16) or (y.toLong() and 0xFFFF)

        /**
         * Finds `gMain` in two IWRAM snapshots taken [frames] frames apart: the unique 4-aligned
         * offset where +4 (callback2) and +0xC (vblankCallback) are ROM pointers in both snapshots
         * and the vblank counter at +0x20 advanced by exactly [frames] (or at +0x24, FireRed, where
         * +0x20 holds a pointer). Returns null when there is no single candidate.
         */
        fun findMain(a: ByteArray, b: ByteArray, frames: Int): Main? {
            var found: Main? = null
            val end = minOf(a.size, b.size) - 0x28
            var o = 0
            while (o <= end) {
                if (isRomPointer(u32(a, o + 4)) && isRomPointer(u32(b, o + 4)) &&
                    isRomPointer(u32(a, o + 0xC)) && isRomPointer(u32(b, o + 0xC))
                ) {
                    val counterOffset = when {
                        u32(b, o + 0x20) - u32(a, o + 0x20) == frames -> 0x20
                        isPointer(u32(b, o + 0x20)) && u32(b, o + 0x24) - u32(a, o + 0x24) == frames -> 0x24
                        else -> 0
                    }
                    if (counterOffset != 0) {
                        if (found != null) return null
                        found = Main(IWRAM_BASE + o, counterOffset)
                    }
                }
                o += 4
            }
            return found
        }

        private fun isPointer(v: Int) = (v ushr 24).let { it == 0x02 || it == 0x03 || it == 0x08 || it == 0x09 }

        internal fun u32(b: ByteArray, o: Int): Int =
            (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
                ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
    }
}
