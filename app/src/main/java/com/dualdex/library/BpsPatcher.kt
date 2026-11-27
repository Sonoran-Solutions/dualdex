package com.dualdex.library

/** Forward BPS1 decoder. */
object BpsPatcher {
    fun inspect(patchBytes: ByteArray): PatchHeader = PatchReader(patchBytes, PatchFormat.BPS).header

    fun apply(source: ByteArray, patch: ByteArray): PatchResult =
        applyPatch(source, patch, PatchFormat.BPS, ::decode)

    private fun decode(reader: PatchReader, source: ByteArray): ByteArray {
        val target = ByteArray(reader.header.targetSize)
        var written = 0
        var sourceCursor = 0
        var targetCursor = 0
        while (reader.remaining > 0) {
            require(written < target.size) { "BPS has commands after the target is complete." }
            val action = reader.number((target.size - written) * 4 - 1, "BPS command")
            val count = (action ushr 2) + 1
            when (action and 3) {
                0 -> { // SourceRead
                    require(written <= source.size - count) { "BPS SourceRead outside source ROM." }
                    source.copyInto(target, written, written, written + count)
                }
                1 -> reader.copyTo(target, written, count) // TargetRead
                2 -> { // SourceCopy
                    sourceCursor += reader.relativeOffset()
                    require(sourceCursor in 0..source.size - count) { "BPS SourceCopy outside source ROM." }
                    source.copyInto(target, written, sourceCursor, sourceCursor + count)
                    sourceCursor += count
                }
                3 -> { // TargetCopy can read bytes emitted by earlier iterations of this same command.
                    targetCursor += reader.relativeOffset()
                    require(targetCursor in 0 until written) { "BPS TargetCopy points outside written target data." }
                    for (i in 0 until count) target[written + i] = target[targetCursor++]
                }
            }
            written += count
        }
        require(written == target.size) { "BPS commands did not fill the target ROM." }
        return target
    }

    private fun PatchReader.relativeOffset(): Int {
        val encoded = number(RomPatcher.MAX_ROM_BYTES * 2 + 1, "BPS relative offset")
        val magnitude = encoded ushr 1
        return if (encoded and 1 == 0) magnitude else -magnitude
    }
}
