package com.dualdex.pokemon.unbound

import com.dualdex.romhack.RuntimeRomTrust

/** Session-local ROM text only; failed extraction is attempted just once, too. */
internal class UnboundMapNames {
    private var attempted = false
    private var cached: Map<Int, String> = emptyMap()

    fun reset() {
        attempted = false
        cached = emptyMap()
    }

    /** Called under the poller's core/session locks, after checking the loaded image identity. */
    fun names(trust: RuntimeRomTrust, read: (Int, Int) -> ByteArray?): Map<Int, String> {
        if (!trust.mayReadLiveMemory ||
            !UnboundMapTable.ROM_SHA256.equals(trust.activeRomSha256, ignoreCase = true)
        ) {
            reset()
            return emptyMap()
        }
        if (!attempted) {
            attempted = true
            cached = try {
                extract(read) ?: emptyMap()
            } catch (_: UnsatisfiedLinkError) {
                emptyMap()
            } catch (_: Exception) {
                emptyMap()
            }
        }
        return cached
    }

    private fun extract(read: (Int, Int) -> ByteArray?): Map<Int, String>? {
        val count = UnboundMapTable.LAST_SECTION - UnboundMapTable.FIRST_SECTION + 1
        val pointers = read(UnboundMapTable.NAME_TABLE_ADDRESS, count * 4)
            ?.takeIf { it.size == count * 4 } ?: return null
        val names = mutableMapOf<Int, String>()
        for (i in 0 until count) {
            var address = 0
            for (byte in 0..3) {
                address = address or ((pointers[i * 4 + byte].toInt() and 0xFF) shl (byte * 8))
            }
            if (address !in 0x08000000..0x09FFFFFF) return null
            val length = minOf(32, 0x0A000000 - address)
            val raw = read(address, length)?.takeIf { it.size == length } ?: return null
            // One undecodable name falls back to its numeric label; the others stay named.
            names[UnboundMapTable.FIRST_SECTION + i] = Gen3Text.decode(raw) ?: continue
        }
        return names
    }
}

/** Plain Gen III English name glyphs; controls and unknown bytes fail closed. */
internal object Gen3Text {
    fun decode(raw: ByteArray): String? {
        val end = raw.indexOf(0xFF.toByte())
        if (end <= 0 || raw.size > 32) return null
        val text = StringBuilder()
        for (i in 0 until end) {
            val code = raw[i].toInt() and 0xFF
            text.append(when (code) {
                0x00 -> ' '
                0x1B -> 'é'
                in 0xA1..0xAA -> '0' + (code - 0xA1)
                in 0xBB..0xD4 -> 'A' + (code - 0xBB)
                in 0xD5..0xEE -> 'a' + (code - 0xD5)
                0xAB -> '!'
                0xAC -> '?'
                0xAD -> '.'
                0xAE -> '-'
                0xAF -> '·'
                0xB0 -> '…'
                0xB1 -> '“'
                0xB2 -> '”'
                0xB3 -> '‘'
                0xB4 -> '’'
                0xB5 -> '♂'
                0xB6 -> '♀'
                0xB7 -> '$'
                0xB8 -> ','
                0xB9 -> '×'
                0xBA -> '/'
                0xF0 -> ':'
                // ponytail: plain names only; add explicit glyph/control handling if needed.
                else -> return null
            })
        }
        return text.toString().takeIf { it.isNotBlank() }
    }
}
