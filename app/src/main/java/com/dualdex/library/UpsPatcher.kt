package com.dualdex.library

/** Forward UPS1 decoder. */
object UpsPatcher {
    fun inspect(patchBytes: ByteArray): PatchHeader = PatchReader(patchBytes, PatchFormat.UPS).header

    fun apply(source: ByteArray, patch: ByteArray): PatchResult =
        applyPatch(source, patch, PatchFormat.UPS, ::decode)

    private fun decode(reader: PatchReader, source: ByteArray): ByteArray {
        val target = source.copyOf(reader.header.targetSize)
        val span = maxOf(source.size, target.size)
        var cursor = 0
        while (reader.remaining > 0) {
            require(cursor <= span) { "UPS record starts outside the ROM." }
            cursor += reader.number(span - cursor, "UPS relative offset")
            while (true) {
                val delta = reader.byte()
                if (delta == 0) {
                    cursor++
                    break
                }
                require(cursor < span) { "UPS XOR run outside the ROM." }
                // Shrinking patches can include XOR data for the discarded source tail.
                if (cursor < target.size) target[cursor] = (target[cursor].toInt() xor delta).toByte()
                cursor++
            }
        }
        return target
    }
}
