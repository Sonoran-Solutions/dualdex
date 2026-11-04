package com.dualdex.emulator

import com.dualdex.calculator.CalcCapabilityPolicy
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomCompatibilityStatus
import com.dualdex.romhack.RomHackDetector
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/** The session title feeds [RomIdentity.legacyStorageKey], which finds pre-SHA save folders. */
class RomSessionTitleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val profiles: List<RomHackProfile> by lazy {
        val dir = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles") }
            .first { it.isDirectory }
        dir.listFiles { f -> f.extension == "json" }!!.map { ProfileLoader.parseProfile(it.readText()) }
    }

    /** A sparse ROM of [length] bytes with a FireRed header, read through the File detection path. */
    private fun rom(length: Long): File = tmp.newFile().also { f ->
        RandomAccessFile(f, "rw").use { raf ->
            raf.setLength(length)
            raf.seek(0xA0)
            raf.write("POKEMON FIRE".toByteArray(Charsets.US_ASCII))
            raf.seek(0xAC)
            raf.write("BPRE".toByteArray(Charsets.US_ASCII))
        }
    }

    @Test
    fun expandedFireRedHackWithoutPreferredTitleKeepsItsLegacySaveKey() {
        val detection = RomHackDetector.detectCompatibility(rom(33_554_432L), profiles)
        assertFalse(profiles.any { detection.sha256 in it.sha256Hashes })

        // Still no vanilla profile, trust or calculator row ...
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, detection.status)
        assertTrue(RomHackProfile.isUnsupportedPlaceholder(detection.profile))
        assertNull(detection.detectedProfile)
        assertFalse(detection.mayReadLiveMemory)
        assertNull(CalcCapabilityPolicy.capabilityFor(detection.profile))

        // ... but the title, and so the legacy key, is what the vanilla presentation produced.
        val title = RomSessionManager.sessionTitle(null, detection)
        assertEquals("Pokemon FireRed", title)
        assertEquals(profiles.single { it.id == "vanilla_firered" }.name, title)
        val key = RomIdentity.create(detection.sha256, title).legacyStorageKey
        assertEquals("Pokemon_FireRed__" + detection.sha256.take(12), key)

        assertEquals("My Hack", RomSessionManager.sessionTitle("My Hack", detection))
    }

    @Test
    fun retailSizeAndUnsupportedTitlesAreUnchanged() {
        val retail = RomHackDetector.detectCompatibility(rom(RomHackDetector.RETAIL_ROM_SIZE), profiles)
        assertEquals("vanilla_firered", retail.profile.id)
        assertEquals("Pokemon FireRed", RomSessionManager.sessionTitle(null, retail))

        val unsupported = RomHackDetector.detectCompatibilityFromBytes(ByteArray(192), "ab", profiles)
        assertEquals(RomCompatibilityStatus.UNSUPPORTED, unsupported.status)
        assertEquals(RomHackProfile.UNSUPPORTED.name, RomSessionManager.sessionTitle(null, unsupported))
    }
}
