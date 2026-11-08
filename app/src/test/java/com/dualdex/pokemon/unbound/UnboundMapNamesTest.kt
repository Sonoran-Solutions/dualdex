package com.dualdex.pokemon.unbound

import com.dualdex.pokemon.LocationResolver
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.*
import org.junit.Test

class UnboundMapNamesTest {
    private val exact = RuntimeRomTrust(
        matchMethod = ProfileMatchMethod.EXACT_SHA256,
        detectedSha256 = UnboundMapTable.ROM_SHA256,
        activeRomSha256 = UnboundMapTable.ROM_SHA256,
        profileVerified = true,
        memoryLayoutVerified = true,
        profileSha256Hashes = listOf(UnboundMapTable.ROM_SHA256)
    )
    private val count = UnboundMapTable.LAST_SECTION - UnboundMapTable.FIRST_SECTION + 1

    // Invented text, encoded explicitly; this is not a ROM fixture.
    private val invented = byteArrayOf(0xD4.toByte(), 0xE5.toByte(), 0xAE.toByte(),
        0xAA.toByte(), 0xBA.toByte(), 0x1B, 0xF0.toByte(), 0xFF.toByte())

    private fun table(pointer: Int = 0x08001001): ByteArray = ByteArray(count * 4) { i ->
        (pointer ushr ((i % 4) * 8)).toByte()
    }

    @Test fun decodesOncePerSessionAndResetsEvenAfterFailure() {
        val cache = UnboundMapNames()
        var reads = 0
        val reader: (Int, Int) -> ByteArray? = { address, length ->
            reads++
            when (address) {
                UnboundMapTable.NAME_TABLE_ADDRESS -> {
                    assertEquals(109 * 4, length)
                    table()
                }
                0x08001001 -> invented.copyOf(length)
                else -> error("unexpected read")
            }
        }
        val names = cache.names(exact, reader)
        assertEquals(109, names.size)
        assertEquals("Zq-9/é:", names[0x93])
        assertEquals(110, reads)
        assertEquals(names, cache.names(exact, reader))
        assertEquals(110, reads)
        cache.reset()
        assertTrue(cache.names(exact) { _, _ -> reads++; null }.isEmpty())
        assertTrue(cache.names(exact, reader).isEmpty())
        assertEquals(111, reads)
        cache.reset()
        assertEquals(names, cache.names(exact, reader))
        assertEquals(221, reads)
    }

    @Test fun nullGarbageShortUnterminatedAndThrowingReadsFallBack() {
        val readers = listOf<(Int, Int) -> ByteArray?>(
            { _, _ -> null },
            { _, _ -> byteArrayOf(0) },
            { _, _ -> table(0x02000000) },
            { _, _ -> table(0x0A000000) },
            { _, _ -> table(-1) },
            { address, length -> if (address == UnboundMapTable.NAME_TABLE_ADDRESS) table() else ByteArray(length) },
            { address, length -> if (address == UnboundMapTable.NAME_TABLE_ADDRESS) table() else ByteArray(length) { 0xFF.toByte() } },
            { address, length -> if (address == UnboundMapTable.NAME_TABLE_ADDRESS) table() else byteArrayOf(0xFC.toByte(), 0xFF.toByte()).copyOf(length) },
            { _, _ -> throw IllegalStateException("synthetic failure") },
            { _, _ -> throw UnsatisfiedLinkError("synthetic missing native reader") }
        )
        val loc = PlayerLocation(3, 6, 0, 0, 0, 0, 0, 0, 0, false, true)
        for (reader in readers) {
            val names = UnboundMapNames().names(exact, reader)
            assertTrue(names.isEmpty())
            assertEquals("Section 0x93", LocationResolver.resolve(LocationStrategy.UNBOUND, loc, names).section!!.name)
        }
    }

    @Test fun eachSectionUsesItsOwnLittleEndianPointer() {
        val pointers = table()
        val special = 0x09002003
        val offset = (0x93 - UnboundMapTable.FIRST_SECTION) * 4
        for (byte in 0..3) pointers[offset + byte] = (special ushr (8 * byte)).toByte()
        val names = UnboundMapNames().names(exact) { address, length ->
            when (address) {
                UnboundMapTable.NAME_TABLE_ADDRESS -> pointers
                special -> byteArrayOf(0xBC.toByte(), 0xFF.toByte()).copyOf(length)
                0x08001001 -> invented.copyOf(length)
                else -> error("unexpected pointer")
            }
        }
        assertEquals("B", names[0x93])
        assertEquals("Zq-9/é:", names[0x92])
        assertEquals("Zq-9/é:", names[0x94])
    }

    @Test fun nonExactTrustNeverReadsAndDropsCachedNames() {
        val cache = UnboundMapNames()
        assertFalse(cache.names(exact) { address, length ->
            if (address == UnboundMapTable.NAME_TABLE_ADDRESS) table() else invented.copyOf(length)
        }.isEmpty())
        val other = "0".repeat(64)
        for (trust in listOf(RuntimeRomTrust(), exact.copy(matchMethod = ProfileMatchMethod.HEADER_TITLE),
                exact.copy(memoryLayoutVerified = false), exact.copy(activeRomSha256 = other),
                exact.copy(detectedSha256 = other, activeRomSha256 = other, profileSha256Hashes = listOf(other)))) {
            assertTrue(cache.names(trust) { _, _ -> fail("untrusted ROM read"); null }.isEmpty())
        }
    }

    @Test fun decoderRequiresBoundedTerminatorAndKnownGlyphs() {
        assertEquals("Zq-9/é:", Gen3Text.decode(invented))
        assertEquals("A", Gen3Text.decode(byteArrayOf(0xBB.toByte(), 0xFF.toByte())))
        assertEquals("A".repeat(31), Gen3Text.decode(ByteArray(32) { if (it == 31) 0xFF.toByte() else 0xBB.toByte() }))
        for ((start, alphabet) in listOf(0xA1 to "0123456789", 0xBB to "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                0xD5 to "abcdefghijklmnopqrstuvwxyz")) {
            val bytes = ByteArray(alphabet.length + 1) { if (it == alphabet.length) 0xFF.toByte() else (start + it).toByte() }
            assertEquals(alphabet, Gen3Text.decode(bytes))
        }
        for (raw in listOf(byteArrayOf(), byteArrayOf(0xFF.toByte()), byteArrayOf(0xBB.toByte()),
                byteArrayOf(0x00, 0xFF.toByte()), byteArrayOf(0x01, 0xFF.toByte()),
                ByteArray(33) { 0xFF.toByte() })) {
            assertNull(Gen3Text.decode(raw))
        }
    }

    @Test fun readsNeverCrossRomEndAndOneBadEntryDiscardsEntireCache() {
        var stringReads = 0
        val names = UnboundMapNames().names(exact) { address, length ->
            if (address == UnboundMapTable.NAME_TABLE_ADDRESS) table(0x09FFFFFE) else {
                assertEquals(0x09FFFFFE, address)
                assertEquals(2, length)
                stringReads++
                byteArrayOf(0xBB.toByte(), 0xFF.toByte())
            }
        }
        assertEquals(109, stringReads)
        assertEquals("A", names[0xC4])
        var reads = 0
        val failed = UnboundMapNames().names(exact) { address, length ->
            if (address == UnboundMapTable.NAME_TABLE_ADDRESS) table() else {
                reads++
                if (reads == 109) null else invented.copyOf(length)
            }
        }
        assertEquals(109, reads)
        assertTrue(failed.isEmpty())
        val singleByte = UnboundMapNames().names(exact) { address, length ->
            if (address == UnboundMapTable.NAME_TABLE_ADDRESS) table(0x09FFFFFF) else {
                assertEquals(1, length)
                byteArrayOf(0xFF.toByte())
            }
        }
        assertTrue(singleByte.isEmpty())
    }
}
