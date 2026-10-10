package com.dualdex.library

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/** GBA cartridge header fields (GBATEK: title 0xA0, code 0xAC, maker 0xB0, version 0xBC). */
data class GbaHeader(val title: String, val gameCode: String, val makerCode: String, val revision: Int)

data class RomChecksums(
    val crc32: String,
    val sha1: String,
    val sha256: String,
    val size: Long,
    val header: GbaHeader?,
    val saveType: String
)

/** Pure ROM inspection for the library INFO sheet (#153). */
object RomInfo {
    const val HEADER_SIZE = 0xC0

    fun parseHeader(bytes: ByteArray): GbaHeader? {
        if (bytes.size < HEADER_SIZE) return null
        fun ascii(from: Int, len: Int) = String(bytes, from, len, Charsets.US_ASCII)
            .takeWhile { it != '\u0000' }.filter { it in ' '..'~' }.trim()
        return GbaHeader(ascii(0xA0, 12), ascii(0xAC, 4), ascii(0xB0, 2), bytes[0xBC].toInt() and 0xFF)
    }

    private val SAVE_MARKERS = listOf("FLASH1M_V", "FLASH512_V", "FLASH_V", "SRAM_F_V", "SRAM_V", "EEPROM_V")

    /** Save chip type from the library ID strings the Nintendo SDK embeds in the ROM. */
    fun saveType(found: Set<String>): String {
        fun has(m: String) = m in found
        return when {
            has("FLASH1M_V") -> "Flash 128 KB"
            has("FLASH512_V") || has("FLASH_V") -> "Flash 64 KB"
            has("SRAM_V") || has("SRAM_F_V") -> "SRAM 32 KB"
            has("EEPROM_V") -> "EEPROM"
            else -> "None detected"
        }
    }

    /** CRC32, SHA-1, SHA-256, header and save type in one streaming pass. */
    fun checksums(input: InputStream): RomChecksums {
        val crc = CRC32()
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        var size = 0L
        var header: GbaHeader? = null
        val found = mutableSetOf<String>()
        var tail = ""
        input.use {
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n); sha1.update(buf, 0, n); sha256.update(buf, 0, n)
                if (header == null && size == 0L && n >= HEADER_SIZE) header = parseHeader(buf.copyOf(n))
                size += n
                // Keep a short overlap so a marker split across two reads is still found.
                val text = tail + String(buf, 0, n, Charsets.ISO_8859_1)
                SAVE_MARKERS.filterTo(found) { m -> m in text }
                tail = text.takeLast(16)
            }
        }
        return RomChecksums("%08x".format(crc.value), hex(sha1.digest()), hex(sha256.digest()), size, header, saveType(found))
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
