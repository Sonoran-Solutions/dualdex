package com.dualdex.library

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.CRC32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RomPatcherTest {
    private val source = "abcdef".toByteArray()

    @Test
    fun bpsDecodesAllActionsAndOverlappingTargetCopyWithSignedOffsets() {
        val target = "abXefeXXXXXab".toByteArray()
        val patch = bps(source, target, metadata = ByteArray(140) { 0x55 }) {
            action(0, 2)                       // ab
            action(1, 1); write('X'.code)      // X
            action(2, 2); number(4 * 2)        // ef from source offset 4
            action(2, 1); number(2 * 2 + 1)    // e from source offset 4 again
            action(1, 1); write('X'.code)      // seed the overlapping copy
            action(3, 4); number(6 * 2)        // XXXX
            action(3, 2); number(10 * 2 + 1)   // ab by rewinding the target read cursor
        }
        assertTarget(source, patch, target, PatchFormat.BPS)
    }

    @Test
    fun bpsShrinksTarget() {
        val target = "ab".toByteArray()
        assertTarget(source, bps(source, target) { action(0, 2) }, target, PatchFormat.BPS)
    }

    @Test
    fun upsAppliesMultipleXorRunsAndExtendsTheSource() {
        val base = byteArrayOf(10, 20, 30, 40)
        val target = byteArrayOf(11, 20, 25, 40, 7, 8)
        val patch = ups(base, target) {
            number(0); write(byteArrayOf(1, 0))
            number(0); write(byteArrayOf(7, 0))
            number(0); write(byteArrayOf(7, 8, 0))
        }
        assertTarget(base, patch, target, PatchFormat.UPS)
    }

    @Test
    fun upsShrinksTargetAndValidatesTheDiscardedTailRun() {
        val base = byteArrayOf(1, 2, 3, 4, 5)
        val target = byteArrayOf(1, 8, 3)
        val patch = ups(base, target) {
            number(1); write(byteArrayOf(10, 0))
            number(0); write(byteArrayOf(4, 5, 0))
        }
        assertTarget(base, patch, target, PatchFormat.UPS)
    }

    @Test
    fun upsSupportsMultibyteRelativeOffsets() {
        val base = ByteArray(300)
        val target = base.copyOf().also { it[270] = 99 }
        val patch = ups(base, target) {
            number(270); write(byteArrayOf(99, 0))
        }
        assertTarget(base, patch, target, PatchFormat.UPS)
    }

    @Test
    fun computesExpectedSha256ForBothFormats() {
        val base = byteArrayOf()
        val target = "abc".toByteArray()
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val bps = bps(base, target) { action(1, 3); write(target) }
        val ups = ups(base, target) { number(0); write(target); write(0) }
        assertEquals(expected, assertTarget(base, bps, target, PatchFormat.BPS).sha256)
        assertEquals(expected, assertTarget(base, ups, target, PatchFormat.UPS).sha256)
    }

    @Test
    fun bothFormatsRejectCorruptPatchFooter() {
        for (patch in identityPatches()) {
            patch[patch.lastIndex] = (patch.last().toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { RomPatcher.inspect(patch) }
            assertFailure(source, patch, "Patch CRC32")
        }
    }

    @Test
    fun bothFormatsCheckSourceSizeAndSourceCrc32() {
        for (patch in identityPatches()) {
            assertFailure(source.copyOf(2), patch, "Source ROM size")
            assertFailure("ghijkl".toByteArray(), patch, "Source ROM CRC32")
        }
    }

    @Test
    fun bothFormatsCheckTargetCrc32IndependentlyOfValidPatchCrc() {
        for (patch in identityPatches()) {
            val withoutPatchCrc = patch.copyOf(patch.size - 4)
            val targetCrcOffset = withoutPatchCrc.size - 4
            withoutPatchCrc[targetCrcOffset] = (withoutPatchCrc[targetCrcOffset].toInt() xor 1).toByte()
            val resealed = withoutPatchCrc + littleEndian(checksum(withoutPatchCrc))
            RomPatcher.inspect(resealed)
            assertFailure(source, resealed, "Target ROM CRC32")
        }
    }

    @Test
    fun bothFormatsRejectTruncatedAndExcessiveVarintsWithValidPatchCrc() {
        for (magic in listOf("UPS1", "BPS1")) {
            assertFailure(source, seal(magic, byteArrayOf(0)), "Truncated")
            assertFailure(source, seal(magic, ByteArray(12) { 0 }), "exceeds")
        }
    }

    @Test
    fun bothFormatsRejectOversizedDeclaredRomSizes() {
        for (magic in listOf("UPS1", "BPS1")) {
            for (oversizedSource in listOf(false, true)) {
                val body = ByteArrayOutputStream().apply {
                    val oversizedSize = RomPatcher.MAX_ROM_BYTES + 1
                    number(if (oversizedSource) oversizedSize else source.size)
                    number(if (oversizedSource) source.size else oversizedSize)
                    if (magic == "BPS1") number(0)
                }.toByteArray()
                val patch = seal(magic, body)
                assertThrows(IllegalArgumentException::class.java) { RomPatcher.inspect(patch) }
                assertFailure(source, patch, if (oversizedSource) "Source ROM size" else "Target ROM size")
            }
        }
    }

    @Test
    fun bpsRejectsOutOfBoundsReadsAndCopies() {
        val target = byteArrayOf(0)
        assertFailure(source, bps(source, target) { action(2, 1); number(3) }, "SourceCopy")
        assertFailure(source, bps(source, target) { action(2, 1); number(12) }, "SourceCopy")
        assertFailure(source, bps(source, target) { action(3, 1); number(0) }, "TargetCopy")
        assertFailure(source, bps(source, ByteArray(7)) { action(0, 7) }, "SourceRead")
    }

    @Test
    fun bpsRejectsTruncatedMetadataAndMalformedActionStreams() {
        val target = "a".toByteArray()
        val truncatedMetadata = ByteArrayOutputStream().apply {
            number(source.size); number(target.size); number(2); write(0)
        }.toByteArray()
        assertFailure(source, seal("BPS1", truncatedMetadata), "Truncated BPS metadata")
        assertFailure(source, bps(source, target) { action(1, 1) }, "Truncated BPS literal")
        assertFailure(source, bps(source, target) {}, "did not fill")
        assertFailure(source, bps(source, target) { action(0, 1); action(0, 1) }, "commands after")
    }

    @Test
    fun upsRejectsOutOfBoundsOffsetsRunsAndMissingTerminator() {
        assertFailure(source, ups(source, source) { number(7); write(0) }, "UPS relative offset")
        assertFailure(source, ups(source, source) { number(6); write(byteArrayOf(1, 0)) }, "UPS XOR run")
        assertFailure(source, ups(source, source) { number(0); write(1) }, "Truncated")
    }

    @Test
    fun rejectsUnknownMagic() {
        assertFailure(source, seal("IPS1", byteArrayOf()), "Unknown patch format")
    }

    @Test
    fun matcherUsesActualLengthAndCrcAndReturnsTheExactCandidate() {
        val header = RomPatcher.inspect(identityPatches().first())
        var openedWrongSize = false
        val candidates = sequenceOf(
            RomPatcher.BaseRomCandidate("known wrong size", 1) {
                openedWrongSize = true
                source.inputStream()
            },
            RomPatcher.BaseRomCandidate("unreadable", source.size.toLong()) { throw IOException() },
            RomPatcher.BaseRomCandidate("misreported size", source.size.toLong()) {
                (source + byteArrayOf(1)).inputStream()
            },
            RomPatcher.BaseRomCandidate("short stream", null) { source.copyOf(5).inputStream() },
            RomPatcher.BaseRomCandidate("wrong CRC", source.size.toLong()) { "abcdefg".takeLast(6).byteInputStream() },
            RomPatcher.BaseRomCandidate("exact", null) { source.inputStream() },
            RomPatcher.BaseRomCandidate("later exact", source.size.toLong()) { error("Opened after a match") }
        )
        assertNull(RomPatcher.findMatchingBaseRom(header, candidates.take(5)))
        val match = checkNotNull(RomPatcher.findMatchingBaseRom(header, candidates))
        assertEquals("exact", match.entry)
        assertArrayEquals(source, match.bytes)
        assertFalse(openedWrongSize)
    }

    private fun identityPatches() = listOf(bps(source, source) { action(0, source.size) }, ups(source, source) {})

    private fun assertTarget(
        source: ByteArray,
        patch: ByteArray,
        expected: ByteArray,
        format: PatchFormat
    ): PatchOutcome.Success {
        val header = RomPatcher.inspect(patch)
        assertEquals(format, header.format)
        assertEquals(source.size, header.sourceSize)
        assertEquals(expected.size, header.targetSize)
        assertEquals(checksum(source), header.sourceCrc32)
        assertEquals(checksum(expected), header.targetCrc32)
        assertEquals(checksum(patch.copyOf(patch.size - 4)), header.patchCrc32)
        val outcome = RomPatcher.apply(source, patch)
        assertTrue("Expected success, got $outcome", outcome is PatchOutcome.Success)
        val success = outcome as PatchOutcome.Success
        assertEquals(format, success.format)
        assertArrayEquals(expected, success.outputBytes)
        assertEquals(checksum(expected), success.targetCrc32)
        return success
    }

    private fun assertFailure(source: ByteArray, patch: ByteArray, reason: String) {
        val result = RomPatcher.apply(source, patch)
        assertTrue("Expected failure, got $result", result is PatchOutcome.Failure)
        assertTrue("Expected '$reason' in $result", (result as PatchOutcome.Failure).reason.contains(reason))
    }

    private fun bps(
        source: ByteArray,
        target: ByteArray,
        metadata: ByteArray = byteArrayOf(),
        commands: ByteArrayOutputStream.() -> Unit
    ): ByteArray = seal("BPS1", ByteArrayOutputStream().apply {
        number(source.size)
        number(target.size)
        number(metadata.size)
        write(metadata)
        commands()
    }.toByteArray(), source, target)

    private fun ups(source: ByteArray, target: ByteArray, records: ByteArrayOutputStream.() -> Unit): ByteArray =
        seal("UPS1", ByteArrayOutputStream().apply {
            number(source.size)
            number(target.size)
            records()
        }.toByteArray(), source, target)

    private fun seal(magic: String, body: ByteArray, source: ByteArray = this.source, target: ByteArray = source): ByteArray {
        val payload = magic.toByteArray() + body + littleEndian(checksum(source)) + littleEndian(checksum(target))
        return payload + littleEndian(checksum(payload))
    }

    private fun checksum(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    private fun littleEndian(value: Long) = ByteArray(4) { index -> (value ushr (8 * index)).toByte() }

    private fun ByteArrayOutputStream.action(opcode: Int, length: Int) = number(((length - 1) shl 2) or opcode)

    private fun ByteArrayOutputStream.number(value: Int) {
        var remaining = value
        while (remaining >= 128) {
            write(remaining and 127)
            remaining = (remaining ushr 7) - 1
        }
        write(remaining or 128)
    }
}
