package com.dualdex.emulator

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RomIdentityTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun md5UsesKnownBytesAndDoesNotChangeSha256Identity() {
        val bytes = "abc".encodeToByteArray()
        val file = temp.newFile("sample.gba").apply { writeBytes(bytes) }
        val md5 = "900150983cd24fb0d6963f7d28e17f72"
        val sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertEquals(md5, RomIdentity.calculateMd5(bytes))
        assertEquals(md5, RomIdentity.calculateMd5(file))
        assertEquals(sha256, RomIdentity.calculateSha256(file))
        val identity = RomIdentity.fromFile(file, "Sample")
        assertEquals(RomIdentity(sha256, "Sample", md5 = md5), identity)
        assertEquals(identity, RomIdentity.fromBytes(bytes, "Sample"))
        assertEquals("", RomIdentity(sha256, "Old caller").md5)
    }

    @Test
    fun streamingHashesIncludeTheTailAndKeepEmptyInputInvalid() {
        val bytes = ByteArray(8205) { (it * 17).toByte() }
        val file = temp.newFile().apply { writeBytes(bytes) }
        assertEquals(RomIdentity.calculateMd5(bytes), RomIdentity.fromFile(file).md5)
        assertEquals(RomIdentity.calculateSha256(bytes), RomIdentity.fromFile(file).sha256)
        assertEquals("", RomIdentity.calculateMd5(temp.newFile()))
        assertEquals("", RomIdentity.calculateMd5(ByteArray(0)))
    }
}
