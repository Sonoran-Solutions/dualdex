package com.dualdex.library

import com.dualdex.romhack.RomCompatibilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    // --- updater ---

    @Test fun semverCompare() {
        assertTrue(UpdateChecker.compareVersions("v1.0.0", "0.9.0") > 0)
        assertEquals(0, UpdateChecker.compareVersions("v1.2", "1.2.0"))
        assertTrue(UpdateChecker.compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(UpdateChecker.compareVersions("0.9.0", "0.9.0-dev") > 0)
        assertTrue(UpdateChecker.compareVersions("v0.9.0-beta.1", "0.9.0") < 0)
    }

    private fun release(tag: String, draft: Boolean = false, pre: Boolean = false, apk: Boolean = true) = """
        {"tag_name":"$tag","name":"$tag","body":"notes","draft":$draft,"prerelease":$pre,
         "assets":[${if (apk) """{"name":"dualdex-$tag.apk","browser_download_url":"https://x/$tag.apk","size":42}""" else """{"name":"src.zip","browser_download_url":"u","size":1}"""}]}
    """

    @Test fun selectsNewestStableReleaseWithApk() {
        val json = "[" + listOf(
            release("v2.0.0", draft = true),
            release("v1.5.0", pre = true),
            release("v1.4.0", apk = false),
            release("v1.2.0"),
            release("v1.3.0"),
            release("v0.8.0")
        ).joinToString(",") + "]"
        val update = UpdateChecker.selectUpdate(json, "0.9.0-dev")!!
        assertEquals("v1.3.0", update.tag)
        assertEquals("dualdex-v1.3.0.apk", update.apkName)
        assertEquals(42L, update.apkSize)
        assertNull(UpdateChecker.selectUpdate(json, "1.3.0"))
    }

    // --- frontend extras ---

    @Test fun frontendReferencePrefersDataThenExtrasInOrder() {
        assertEquals("content://a", FrontendLaunch.romReference("content://a", mapOf("rom" to "/b")))
        assertEquals("/r", FrontendLaunch.romReference(null, mapOf("path" to "/p", "rom" to "/r")))
        assertEquals("/p", FrontendLaunch.romReference("", mapOf("ROM" to " ", "path" to "/p")))
        assertNull(FrontendLaunch.romReference(null, mapOf("other" to "/x")))
        assertEquals("/sd/My Game.gba", FrontendLaunch.filesystemPath("file:///sd/My%20Game.gba"))
        assertNull(FrontendLaunch.filesystemPath("content://x/y"))
        assertEquals("Emerald.gba", FrontendLaunch.displayName("content://prov/tree/primary%3AROMs%2FEmerald.gba"))
    }

    @Test fun copyIfChangedSkipsMatchingBytes() {
        val target = File(tmp.root, "out/rom.gba")
        val a = byteArrayOf(1, 2, 3)
        assertTrue(FrontendLaunch.copyIfChanged({ a.inputStream() }, 3, target))
        assertFalse(FrontendLaunch.copyIfChanged({ a.inputStream() }, 3, target))
        assertTrue(FrontendLaunch.copyIfChanged({ byteArrayOf(1, 2, 4).inputStream() }, 3, target))
        assertEquals(listOf<Byte>(1, 2, 4), target.readBytes().toList())
    }

    // --- scan cache ---

    @Test fun scanCacheInvalidatesOnSizeOrMtimeAndPrunes() {
        val file = File(tmp.root, "cache.json")
        RomScanCache(file).apply {
            put(ScanVerdict("p1", 10, 100, "aa", RomCompatibilityStatus.VERIFIED, "Emerald"))
            put(ScanVerdict("p2", 10, 100, "bb", RomCompatibilityStatus.UNSUPPORTED, ""))
            retainAndSave(setOf("p1"))
        }
        val reloaded = RomScanCache(file)
        assertEquals("Emerald", reloaded.lookup("p1", 10, 100)?.profileName)
        assertNull(reloaded.lookup("p1", 11, 100))
        assertNull(reloaded.lookup("p1", 10, 101))
        assertNull(reloaded.lookup("p2", 10, 100))
        assertTrue(RomScanCache.isRomName("x.GBA") && !RomScanCache.isRomName("x.sav"))
    }

    // --- ROM info ---

    @Test fun headerAndChecksums() {
        val rom = ByteArray(0x200)
        "POKEMON EMER".toByteArray().copyInto(rom, 0xA0)
        "BPEE".toByteArray().copyInto(rom, 0xAC)
        "01".toByteArray().copyInto(rom, 0xB0)
        rom[0xBC] = 1
        "FLASH1M_V103".toByteArray().copyInto(rom, 0x1F0 - 4)
        val sums = RomInfo.checksums(rom.inputStream())
        assertEquals(GbaHeader("POKEMON EMER", "BPEE", "01", 1), sums.header)
        assertEquals("Flash 128 KB", sums.saveType)
        assertEquals(0x200L, sums.size)
        val crc = java.util.zip.CRC32().apply { update(rom) }.value
        assertEquals("%08x".format(crc), sums.crc32)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(rom).joinToString("") { "%02x".format(it) }
        assertEquals(sha, sums.sha256)
        assertEquals(40, sums.sha1.length)
        assertEquals("None detected", RomInfo.saveType(emptySet()))
    }
}
