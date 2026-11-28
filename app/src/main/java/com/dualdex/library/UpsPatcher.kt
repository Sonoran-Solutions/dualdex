package com.dualdex.library

import java.io.File

/** Forward UPS1 decoder. */
object UpsPatcher {
    fun inspect(patchBytes: ByteArray): PatchHeader =
        inspectWithBudget(patchBytes, RomPatcher.MAX_AGGREGATE_BYTES)

    internal fun inspectWithBudget(patchBytes: ByteArray, maxAggregateBytes: Long): PatchHeader =
        PatchReader.fromBytes(patchBytes, PatchFormat.UPS, maxAggregateBytes).use { it.header }

    internal fun inspect(patchFile: File, maxAggregateBytes: Long = RomPatcher.MAX_AGGREGATE_BYTES): PatchHeader =
        PatchReader.fromFile(patchFile, PatchFormat.UPS, maxAggregateBytes).use { it.header }

    fun apply(source: ByteArray, patch: ByteArray): PatchResult =
        applyWithBudget(source, patch, RomPatcher.MAX_AGGREGATE_BYTES)

    internal fun applyWithBudget(source: ByteArray, patch: ByteArray, maxAggregateBytes: Long): PatchResult =
        applyPatch(source, patch, PatchFormat.UPS, maxAggregateBytes, ::decode)

    internal fun apply(source: ByteArray, patchFile: File, maxAggregateBytes: Long = RomPatcher.MAX_AGGREGATE_BYTES): PatchResult =
        applyPatchFile(source, patchFile, PatchFormat.UPS, maxAggregateBytes, ::decode)

    private fun decode(reader: PatchReader, source: ByteArray): ByteArray {
        val target = source.copyOf(reader.header.targetSize)
        val span = maxOf(source.size, target.size)
        var cursor = 0
        while (reader.remaining > 0L) {
            if (cursor > span) throw PatchException("UPS record starts outside the ROM.")
            cursor += reader.number(span - cursor, "UPS relative offset")
            while (true) {
                val delta = reader.byte()
                if (delta == 0) {
                    cursor++
                    break
                }
                if (cursor >= span) throw PatchException("UPS XOR run outside the ROM.")
                // Shrinking patches can include XOR data for the discarded source tail.
                if (cursor < target.size) target[cursor] = (target[cursor].toInt() xor delta).toByte()
                cursor++
            }
        }
        return target
    }
}
