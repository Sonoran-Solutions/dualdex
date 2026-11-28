package com.dualdex.library

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

class PatchException(message: String) : IllegalArgumentException(message)

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
    const val MAX_ROM_BYTES = 32 * 1024 * 1024
    const val MAX_PATCH_BYTES = 32 * 1024 * 1024
    const val MAX_AGGREGATE_BYTES = 96L * 1024 * 1024

    data class BaseRomCandidate<T>(val entry: T, val size: Long?, val open: () -> InputStream?)
    data class BaseRomMatch<T>(val entry: T, val bytes: ByteArray)

    fun checkAllocationBudget(
        sourceSize: Long,
        patchSize: Long,
        targetSize: Long,
        maxAggregateBytes: Long = MAX_AGGREGATE_BYTES
    ) {
        if (sourceSize !in 0L..MAX_ROM_BYTES.toLong()) {
            throw PatchException("Source ROM size exceeds the ${MAX_ROM_BYTES / (1024 * 1024)} MiB limit.")
        }
        if (targetSize !in 0L..MAX_ROM_BYTES.toLong()) {
            throw PatchException("Target ROM size exceeds the ${MAX_ROM_BYTES / (1024 * 1024)} MiB limit.")
        }
        if (patchSize !in 0L..MAX_PATCH_BYTES.toLong()) {
            throw PatchException("Patch exceeds the ${MAX_PATCH_BYTES / (1024 * 1024)} MiB size limit.")
        }
        val total = sourceSize + patchSize + targetSize
        if (total > maxAggregateBytes) {
            throw PatchException(
                "Combined patch memory budget exceeded ($total bytes > $maxAggregateBytes bytes)."
            )
        }
    }

    private fun format(patch: ByteArray): PatchFormat =
        PatchFormat.entries.firstOrNull { hasMagic(patch, it) }
            ?: throw PatchException("Unknown patch format. Choose a BPS1 or UPS1 patch.")

    private fun format(patchFile: File): PatchFormat {
        val length = patchFile.length()
        if (length > MAX_PATCH_BYTES) {
            throw PatchException("Patch exceeds the ${MAX_PATCH_BYTES / (1024 * 1024)} MiB size limit.")
        }
        val magic = ByteArray(4)
        val read = FileInputStream(patchFile).use { input ->
            var offset = 0
            while (offset < magic.size) {
                val n = input.read(magic, offset, magic.size - offset)
                if (n < 0) break
                offset += n
            }
            offset
        }
        if (read < 4) {
            throw PatchException("Unknown patch format. Choose a BPS1 or UPS1 patch.")
        }
        return format(magic)
    }

    /** Verifies the patch CRC and reads its header. Throws PatchException/IllegalArgumentException on invalid input. */
    fun inspect(patch: ByteArray): PatchHeader = inspectWithBudget(patch, MAX_AGGREGATE_BYTES)

    /** Verifies the patch CRC and reads its header from a spooled file without loading it into memory. */
    fun inspect(patchFile: File): PatchHeader = inspectFileWithBudget(patchFile, MAX_AGGREGATE_BYTES)

    internal fun inspectWithBudget(patch: ByteArray, maxAggregateBytes: Long): PatchHeader = when (format(patch)) {
        PatchFormat.BPS -> BpsPatcher.inspectWithBudget(patch, maxAggregateBytes)
        PatchFormat.UPS -> UpsPatcher.inspectWithBudget(patch, maxAggregateBytes)
    }

    internal fun inspectFileWithBudget(patchFile: File, maxAggregateBytes: Long): PatchHeader = when (format(patchFile)) {
        PatchFormat.BPS -> BpsPatcher.inspect(patchFile, maxAggregateBytes)
        PatchFormat.UPS -> UpsPatcher.inspect(patchFile, maxAggregateBytes)
    }

    /**
     * Uses known lengths to skip candidates, then checks the actual length and CRC of the bytes
     * returned to the caller. A null length means the provider did not report one.
     */
    fun <T> findMatchingBaseRom(
        header: PatchHeader,
        candidates: Sequence<BaseRomCandidate<T>>
    ): BaseRomMatch<T>? = findMatchingBaseRomWithBudget(header, candidates, patchSize = 0L)

    internal fun <T> findMatchingBaseRomWithBudget(
        header: PatchHeader,
        candidates: Sequence<BaseRomCandidate<T>>,
        patchSize: Long,
        maxAggregateBytes: Long = MAX_AGGREGATE_BYTES
    ): BaseRomMatch<T>? {
        checkAllocationBudget(
            sourceSize = header.sourceSize.toLong(),
            patchSize = patchSize,
            targetSize = header.targetSize.toLong(),
            maxAggregateBytes = maxAggregateBytes
        )
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

    fun apply(source: ByteArray, patch: ByteArray): PatchOutcome =
        applyWithBudget(source, patch, MAX_AGGREGATE_BYTES)

    fun apply(source: ByteArray, patchFile: File): PatchOutcome =
        applyFileWithBudget(source, patchFile, MAX_AGGREGATE_BYTES)

    internal fun applyWithBudget(source: ByteArray, patch: ByteArray, maxAggregateBytes: Long): PatchOutcome {
        val format = try {
            format(patch)
        } catch (e: IllegalArgumentException) {
            return PatchOutcome.Failure(e.message.orEmpty())
        }
        val result = when (format) {
            PatchFormat.BPS -> BpsPatcher.applyWithBudget(source, patch, maxAggregateBytes)
            PatchFormat.UPS -> UpsPatcher.applyWithBudget(source, patch, maxAggregateBytes)
        }
        return toOutcome(format, result)
    }

    internal fun applyFileWithBudget(source: ByteArray, patchFile: File, maxAggregateBytes: Long): PatchOutcome {
        val format = try {
            format(patchFile)
        } catch (e: IllegalArgumentException) {
            return PatchOutcome.Failure(e.message.orEmpty())
        }
        val result = when (format) {
            PatchFormat.BPS -> BpsPatcher.apply(source, patchFile, maxAggregateBytes)
            PatchFormat.UPS -> UpsPatcher.apply(source, patchFile, maxAggregateBytes)
        }
        return toOutcome(format, result)
    }

    private fun toOutcome(format: PatchFormat, result: PatchResult): PatchOutcome = when (result) {
        is PatchResult.Failure -> PatchOutcome.Failure(result.reason)
        is PatchResult.Success -> {
            val sha256 = MessageDigest.getInstance("SHA-256").digest(result.outputBytes)
                .joinToString("") { "%02x".format(it) }
            PatchOutcome.Success(result.outputBytes, sha256, result.targetCrc32, format)
        }
    }
}

internal fun crc32(bytes: ByteArray, length: Int = bytes.size): Long =
    CRC32().apply { update(bytes, 0, length) }.value

private fun hasMagic(patch: ByteArray, format: PatchFormat): Boolean =
    patch.size >= format.magic.length && format.magic.indices.all { patch[it].toInt() == format.magic[it].code }

private fun readLittleEndian32(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in 0..3) value = value or ((bytes[offset + i].toLong() and 0xff) shl (8 * i))
    return value
}

internal interface PatchInput {
    val size: Long
    fun readMagic(): ByteArray
    fun readFooterAndVerifyCrc(): ByteArray
    fun openBodyStream(): InputStream
}

private class ByteArrayPatchInput(private val patch: ByteArray) : PatchInput {
    override val size: Long get() = patch.size.toLong()

    override fun readMagic(): ByteArray = patch.copyOfRange(0, minOf(4, patch.size))

    override fun readFooterAndVerifyCrc(): ByteArray {
        val footer = patch.copyOfRange(patch.size - 12, patch.size)
        val patchCrc = readLittleEndian32(footer, 8)
        if (crc32(patch, patch.size - 4) != patchCrc) {
            throw PatchException("Patch CRC32 mismatch.")
        }
        return footer
    }

    override fun openBodyStream(): InputStream =
        ByteArrayInputStream(patch, 4, (patch.size - 16).coerceAtLeast(0))
}

private class FilePatchInput(private val file: File) : PatchInput {
    override val size: Long = file.length()

    override fun readMagic(): ByteArray {
        val magic = ByteArray(4)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < magic.size) {
                val n = input.read(magic, offset, magic.size - offset)
                if (n < 0) break
                offset += n
            }
            return magic.copyOf(offset)
        }
    }

    override fun readFooterAndVerifyCrc(): ByteArray {
        val crc = CRC32()
        val buffer = ByteArray(64 * 1024)
        val footer = ByteArray(12)
        val crcLimit = size - 4L
        val footerStartOffset = size - 12L
        var offset = 0L
        var footerBytesRead = 0
        FileInputStream(file).use { input ->
            while (offset < size) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n == 0) throw PatchException("Could not read patch file.")
                val hashEnd = minOf(offset + n, crcLimit)
                if (hashEnd > offset) {
                    crc.update(buffer, 0, (hashEnd - offset).toInt())
                }
                val chunkEnd = offset + n
                val copyStart = maxOf(offset, footerStartOffset)
                val copyEnd = minOf(chunkEnd, size)
                if (copyEnd > copyStart) {
                    val srcOffset = (copyStart - offset).toInt()
                    val dstOffset = (copyStart - footerStartOffset).toInt()
                    val count = (copyEnd - copyStart).toInt()
                    System.arraycopy(buffer, srcOffset, footer, dstOffset, count)
                    footerBytesRead += count
                }
                offset = chunkEnd
            }
        }
        if (offset != size || footerBytesRead != 12) {
            throw PatchException("Truncated patch data.")
        }
        val patchCrc = readLittleEndian32(footer, 8)
        if (crc.value != patchCrc) {
            throw PatchException("Patch CRC32 mismatch.")
        }
        return footer
    }

    override fun openBodyStream(): InputStream {
        val stream = BufferedInputStream(FileInputStream(file), 64 * 1024)
        try {
            var skipped = 0
            while (skipped < 4) {
                val b = stream.read()
                if (b < 0) throw PatchException("Truncated patch data.")
                skipped++
            }
            return stream
        } catch (t: Throwable) {
            runCatching { stream.close() }
            throw t
        }
    }
}

/** Shared BPS1 and UPS1 framing. The command reader cannot enter the CRC footer. */
internal class PatchReader private constructor(
    private val input: PatchInput,
    format: PatchFormat,
    maxAggregateBytes: Long
) : Closeable {
    val patchSize: Long = input.size
    private val end: Long = patchSize - 12L
    private var position: Long = 4L
    private val stream: InputStream
    val header: PatchHeader
    val remaining: Long get() = end - position

    init {
        if (patchSize > RomPatcher.MAX_PATCH_BYTES) {
            throw PatchException("Patch exceeds the ${RomPatcher.MAX_PATCH_BYTES / (1024 * 1024)} MiB size limit.")
        }
        val magic = input.readMagic()
        if (end < position || !hasMagic(magic, format)) {
            throw PatchException("Invalid ${format.magic} header.")
        }
        val footer = input.readFooterAndVerifyCrc()
        stream = input.openBodyStream()
        try {
            val sourceSize = number(RomPatcher.MAX_ROM_BYTES, "Source ROM size")
            val targetSize = number(RomPatcher.MAX_ROM_BYTES, "Target ROM size")
            RomPatcher.checkAllocationBudget(
                sourceSize = sourceSize.toLong(),
                patchSize = patchSize,
                targetSize = targetSize.toLong(),
                maxAggregateBytes = maxAggregateBytes
            )
            if (format == PatchFormat.BPS) {
                val metadataSize = number(remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), "BPS metadata size")
                if (metadataSize.toLong() > remaining) {
                    throw PatchException("Truncated BPS metadata.")
                }
                skipMetadata(metadataSize.toLong())
            }
            header = PatchHeader(
                format = format,
                sourceSize = sourceSize,
                targetSize = targetSize,
                sourceCrc32 = readLittleEndian32(footer, 0),
                targetCrc32 = readLittleEndian32(footer, 4),
                patchCrc32 = readLittleEndian32(footer, 8)
            )
        } catch (t: Throwable) {
            runCatching { stream.close() }
            throw t
        }
    }

    private fun skipMetadata(metadataSize: Long) {
        var skipped = 0L
        while (skipped < metadataSize) {
            val n = stream.skip(metadataSize - skipped)
            if (n > 0) {
                skipped += n
            } else {
                if (stream.read() < 0) throw PatchException("Truncated BPS metadata.")
                skipped++
            }
        }
        position += metadataSize
    }

    fun byte(): Int {
        if (remaining <= 0L) throw PatchException("Truncated patch data.")
        val b = stream.read()
        if (b < 0) throw PatchException("Truncated patch data.")
        position++
        return b
    }

    fun number(limit: Int, field: String): Int {
        var value = 0L
        var unit = 1L
        while (true) {
            val digit = byte()
            value += (digit and 0x7f) * unit
            if (value > limit) throw PatchException("$field exceeds its limit.")
            if (digit and 0x80 != 0) return value.toInt()
            // A continuation adds at least one unit of the next digit, even when that digit is zero.
            unit *= 128
            value += unit
            if (value > limit) throw PatchException("$field exceeds its limit.")
        }
    }

    fun copyTo(target: ByteArray, offset: Int, length: Int) {
        if (length < 0 || length.toLong() > remaining) throw PatchException("Truncated BPS literal data.")
        var copied = 0
        while (copied < length) {
            val n = stream.read(target, offset + copied, length - copied)
            if (n <= 0) throw PatchException("Truncated BPS literal data.")
            copied += n
            position += n
        }
    }

    override fun close() {
        stream.close()
    }

    companion object {
        fun fromBytes(
            patch: ByteArray,
            format: PatchFormat,
            maxAggregateBytes: Long = RomPatcher.MAX_AGGREGATE_BYTES
        ): PatchReader = PatchReader(ByteArrayPatchInput(patch), format, maxAggregateBytes)

        fun fromFile(
            patchFile: File,
            format: PatchFormat,
            maxAggregateBytes: Long = RomPatcher.MAX_AGGREGATE_BYTES
        ): PatchReader = PatchReader(FilePatchInput(patchFile), format, maxAggregateBytes)
    }
}

internal inline fun applyPatch(
    source: ByteArray,
    patch: ByteArray,
    format: PatchFormat,
    maxAggregateBytes: Long = RomPatcher.MAX_AGGREGATE_BYTES,
    decode: (PatchReader, ByteArray) -> ByteArray
): PatchResult = try {
    PatchReader.fromBytes(patch, format, maxAggregateBytes).use { reader ->
        executeApply(source, reader, maxAggregateBytes, decode)
    }
} catch (e: IllegalArgumentException) {
    PatchResult.Failure(e.message.orEmpty())
}

internal inline fun applyPatchFile(
    source: ByteArray,
    patchFile: File,
    format: PatchFormat,
    maxAggregateBytes: Long = RomPatcher.MAX_AGGREGATE_BYTES,
    decode: (PatchReader, ByteArray) -> ByteArray
): PatchResult = try {
    PatchReader.fromFile(patchFile, format, maxAggregateBytes).use { reader ->
        executeApply(source, reader, maxAggregateBytes, decode)
    }
} catch (e: IllegalArgumentException) {
    PatchResult.Failure(e.message.orEmpty())
}

internal inline fun executeApply(
    source: ByteArray,
    reader: PatchReader,
    maxAggregateBytes: Long,
    decode: (PatchReader, ByteArray) -> ByteArray
): PatchResult {
    val header = reader.header
    if (source.size != header.sourceSize) throw PatchException("Source ROM size mismatch.")
    RomPatcher.checkAllocationBudget(
        sourceSize = source.size.toLong(),
        patchSize = reader.patchSize,
        targetSize = header.targetSize.toLong(),
        maxAggregateBytes = maxAggregateBytes
    )
    if (crc32(source) != header.sourceCrc32) throw PatchException("Source ROM CRC32 mismatch.")
    val target = decode(reader, source)
    if (crc32(target) != header.targetCrc32) throw PatchException("Target ROM CRC32 mismatch.")
    return PatchResult.Success(target, header.targetCrc32)
}
