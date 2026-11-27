package com.dualdex.library

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

enum class PatchFormat(internal val magic: String) { BPS("BPS1"), UPS("UPS1") }

data class PatchHeader(
    val format: PatchFormat,
    val sourceSize: Int,
    val targetSize: Int,
    val sourceCrc32: Long,
    val targetCrc32: Long,
    val patchCrc32: Long
)

sealed interface PatchResult {
    data class Success(val outputBytes: ByteArray, val targetCrc32: Long) : PatchResult
    data class Failure(val reason: String) : PatchResult
}

sealed interface PatchOutcome {
    data class Success(
        val outputBytes: ByteArray,
        val sha256: String,
        val targetCrc32: Long,
        val format: PatchFormat
    ) : PatchOutcome

    data class Failure(val reason: String) : PatchOutcome
}

object RomPatcher {
    const val MAX_ROM_BYTES = 64 * 1024 * 1024
    const val MAX_PATCH_BYTES = 128 * 1024 * 1024

    data class BaseRomCandidate<T>(val entry: T, val size: Long?, val open: () -> InputStream?)
    data class BaseRomMatch<T>(val entry: T, val bytes: ByteArray)

    private fun format(patch: ByteArray): PatchFormat =
        PatchFormat.entries.firstOrNull { hasMagic(patch, it) }
            ?: throw IllegalArgumentException("Unknown patch format. Choose a BPS1 or UPS1 patch.")

    /** Verifies the patch CRC and reads its header. Throws IllegalArgumentException on invalid input. */
    fun inspect(patch: ByteArray): PatchHeader = when (format(patch)) {
        PatchFormat.BPS -> BpsPatcher.inspect(patch)
        PatchFormat.UPS -> UpsPatcher.inspect(patch)
    }

    /**
     * Uses known lengths to skip candidates, then checks the actual length and CRC of the bytes
     * returned to the caller. A null length means the provider did not report one.
     */
    fun <T> findMatchingBaseRom(
        header: PatchHeader,
        candidates: Sequence<BaseRomCandidate<T>>
    ): BaseRomMatch<T>? {
        require(header.sourceSize in 0..MAX_ROM_BYTES) { "Source ROM exceeds the size limit." }
        val expectedSize = header.sourceSize.toLong()
        val bytes by lazy { ByteArray(header.sourceSize) }
        for (candidate in candidates) {
            if (candidate.size != null && candidate.size != expectedSize) continue
            val matches = try {
                candidate.open()?.use { input ->
                    var offset = 0
                    while (offset < bytes.size) {
                        val count = input.read(bytes, offset, bytes.size - offset)
                        if (count <= 0) break
                        offset += count
                    }
                    offset == bytes.size && input.read() == -1 && crc32(bytes) == header.sourceCrc32
                } == true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
            if (matches) return BaseRomMatch(candidate.entry, bytes)
        }
        return null
    }

    fun apply(source: ByteArray, patch: ByteArray): PatchOutcome {
        val format = try {
            format(patch)
        } catch (e: IllegalArgumentException) {
            return PatchOutcome.Failure(e.message.orEmpty())
        }
        val result = when (format) {
            PatchFormat.BPS -> BpsPatcher.apply(source, patch)
            PatchFormat.UPS -> UpsPatcher.apply(source, patch)
        }
        return when (result) {
            is PatchResult.Failure -> PatchOutcome.Failure(result.reason)
            is PatchResult.Success -> {
                val sha256 = MessageDigest.getInstance("SHA-256").digest(result.outputBytes)
                    .joinToString("") { "%02x".format(it) }
                PatchOutcome.Success(result.outputBytes, sha256, result.targetCrc32, format)
            }
        }
    }
}

internal fun crc32(bytes: ByteArray, length: Int = bytes.size): Long =
    CRC32().apply { update(bytes, 0, length) }.value

private fun hasMagic(patch: ByteArray, format: PatchFormat): Boolean =
    patch.size >= format.magic.length && format.magic.indices.all { patch[it].toInt() == format.magic[it].code }

/** Shared BPS1 and UPS1 framing. The command reader cannot enter the CRC footer. */
internal class PatchReader(private val patch: ByteArray, format: PatchFormat) {
    private val end = patch.size - 12
    private var position = 4
    val header: PatchHeader
    val remaining: Int get() = end - position

    init {
        require(patch.size <= RomPatcher.MAX_PATCH_BYTES) { "Patch exceeds the 128 MiB size limit." }
        require(end >= position && hasMagic(patch, format)) { "Invalid ${format.magic} header." }
        val patchCrc = readCrc(end + 8)
        require(crc32(patch, patch.size - 4) == patchCrc) { "Patch CRC32 mismatch." }
        val sourceSize = number(RomPatcher.MAX_ROM_BYTES, "Source ROM size")
        val targetSize = number(RomPatcher.MAX_ROM_BYTES, "Target ROM size")
        if (format == PatchFormat.BPS) {
            val metadataSize = number(remaining, "BPS metadata size")
            require(metadataSize <= remaining) { "Truncated BPS metadata." }
            position += metadataSize
        }
        header = PatchHeader(format, sourceSize, targetSize, readCrc(end), readCrc(end + 4), patchCrc)
    }

    private fun readCrc(offset: Int): Long {
        var value = 0L
        for (i in 0..3) value = value or ((patch[offset + i].toLong() and 0xff) shl (8 * i))
        return value
    }

    fun byte(): Int {
        require(remaining > 0) { "Truncated patch data." }
        return patch[position++].toInt() and 0xff
    }

    fun number(limit: Int, field: String): Int {
        var value = 0L
        var unit = 1L
        while (true) {
            val digit = byte()
            value += (digit and 0x7f) * unit
            require(value <= limit) { "$field exceeds its limit." }
            if (digit and 0x80 != 0) return value.toInt()
            // A continuation adds at least one unit of the next digit, even when that digit is zero.
            unit *= 128
            value += unit
            require(value <= limit) { "$field exceeds its limit." }
        }
    }

    fun copyTo(target: ByteArray, offset: Int, length: Int) {
        require(length <= remaining) { "Truncated BPS literal data." }
        patch.copyInto(target, offset, position, position + length)
        position += length
    }
}

internal inline fun applyPatch(
    source: ByteArray,
    patch: ByteArray,
    format: PatchFormat,
    decode: (PatchReader, ByteArray) -> ByteArray
): PatchResult = try {
    val reader = PatchReader(patch, format)
    val header = reader.header
    require(source.size == header.sourceSize) { "Source ROM size mismatch." }
    require(crc32(source) == header.sourceCrc32) { "Source ROM CRC32 mismatch." }
    val target = decode(reader, source)
    require(crc32(target) == header.targetCrc32) { "Target ROM CRC32 mismatch." }
    PatchResult.Success(target, header.targetCrc32)
} catch (e: IllegalArgumentException) {
    PatchResult.Failure(e.message.orEmpty())
}
