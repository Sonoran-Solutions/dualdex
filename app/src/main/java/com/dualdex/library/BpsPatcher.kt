package com.dualdex.library

import java.io.File

/** Forward BPS1 decoder. */
object BpsPatcher {
    fun inspect(patchBytes: ByteArray): PatchHeader =
        inspectWithBudget(patchBytes, RomPatcher.MAX_AGGREGATE_BYTES)

    internal fun inspectWithBudget(patchBytes: ByteArray, maxAggregateBytes: Long): PatchHeader =
        PatchReader.fromBytes(patchBytes, PatchFormat.BPS, maxAggregateBytes).use { it.header }

    internal fun inspect(patchFile: File, maxAggregateBytes: Long = RomPatcher.MAX_AGGREGATE_BYTES): PatchHeader =
        PatchReader.fromFile(patchFile, PatchFormat.BPS, maxAggregateBytes).use { it.header }

    fun apply(source: ByteArray, patch: ByteArray): PatchResult =
        applyWithBudget(source, patch, RomPatcher.MAX_AGGREGATE_BYTES)

    internal fun applyWithBudget(source: ByteArray, patch: ByteArray, maxAggregateBytes: Long): PatchResult =
        applyPatch(source, patch, PatchFormat.BPS, maxAggregateBytes, ::decode)

    internal fun apply(source: ByteArray, patchFile: File, maxAggregateBytes: Long = RomPatcher.MAX_AGGREGATE_BYTES): PatchResult =
        applyPatchFile(source, patchFile, PatchFormat.BPS, maxAggregateBytes, ::decode)

    private fun decode(reader: PatchReader, source: ByteArray): ByteArray {
        val target = ByteArray(reader.header.targetSize)
        var written = 0
        var sourceCursor = 0
        var targetCursor = 0
        while (reader.remaining > 0L) {
            if (written >= target.size) throw PatchException("BPS has commands after the target is complete.")
            val action = reader.number((target.size - written) * 4 - 1, "BPS command")
            val count = (action ushr 2) + 1
            when (action and 3) {
                0 -> { // SourceRead
                    if (written > source.size - count) throw PatchException("BPS SourceRead outside source ROM.")
                    source.copyInto(target, written, written, written + count)
                }
                1 -> reader.copyTo(target, written, count) // TargetRead
                2 -> { // SourceCopy
                    sourceCursor += reader.relativeOffset()
                    if (sourceCursor !in 0..source.size - count) throw PatchException("BPS SourceCopy outside source ROM.")
                    source.copyInto(target, written, sourceCursor, sourceCursor + count)
                    sourceCursor += count
                }
                3 -> { // TargetCopy can read bytes emitted by earlier iterations of this same command.
                    targetCursor += reader.relativeOffset()
                    if (targetCursor !in 0 until written) throw PatchException("BPS TargetCopy points outside written target data.")
                    for (i in 0 until count) target[written + i] = target[targetCursor++]
                }
            }
            written += count
        }
        if (written != target.size) throw PatchException("BPS commands did not fill the target ROM.")
        return target
    }

    private fun PatchReader.relativeOffset(): Int {
        val encoded = number(RomPatcher.MAX_ROM_BYTES * 2 + 1, "BPS relative offset")
        val magnitude = encoded ushr 1
        return if (encoded and 1 == 0) magnitude else -magnitude
    }
}
