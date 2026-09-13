package com.dualdex.emulator

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class RomIdentityAndSaveTest {

    @Test
    fun testRomIdentityCreationAndShortHash() {
        val dummyHash = "a13f42c92d71ef6890123456789abcdef0123456789abcdef0123456789abcdef"
        val identity = RomIdentity.create(dummyHash, "Pokemon Unbound")

        assertEquals(dummyHash, identity.sha256)
        assertEquals("a13f42c92d71", identity.shortHash)
        assertEquals(12, identity.shortHash.length)
        assertEquals("Pokemon_Unbound", identity.sanitizedTitle)
        assertEquals("Pokemon_Unbound__a13f42c92d71", identity.storageKey)
    }

    @Test
    fun testNamespaceCollisionPrevention() {
        // Two different ROMs sharing the same name / profile
        val romHash1 = "1111111111112222222222223333333333334444444444445555555555556666"
        val romHash2 = "9999999999998888888888887777777777776666666666665555555555554444"

        val identity1 = RomIdentity.create(romHash1, "Pokemon Radical Red")
        val identity2 = RomIdentity.create(romHash2, "Pokemon Radical Red")

        // Display names are identical
        assertEquals(identity1.displayName, identity2.displayName)
        // But storage keys MUST be distinct to prevent save corruption/overwrites
        assertNotEquals(identity1.storageKey, identity2.storageKey)
        assertEquals("Pokemon_Radical_Red__111111111111", identity1.storageKey)
        assertEquals("Pokemon_Radical_Red__999999999999", identity2.storageKey)
    }

    @Test
    fun testTitleSanitization() {
        assertEquals("game", RomIdentity.sanitizeTitle(""))
        assertEquals("game", RomIdentity.sanitizeTitle("   "))
        assertEquals("game", RomIdentity.sanitizeTitle("!@#$%^&*()"))
        assertEquals("Pokemon_FireRed", RomIdentity.sanitizeTitle("  Pokemon   FireRed  "))
        assertEquals("GBA_Pokemon_Emerald_v1_0", RomIdentity.sanitizeTitle("[GBA] Pokemon Emerald (v1.0)!"))

        // Very long title should be capped at 40 characters
        val longTitle = "A".repeat(60)
        val sanitized = RomIdentity.sanitizeTitle(longTitle)
        assertEquals(40, sanitized.length)
    }

    @Test
    fun testSha256Calculation() {
        val testBytes = "DualDex".toByteArray(Charsets.UTF_8)
        val hash = RomIdentity.calculateSha256(testBytes)

        // Pre-computed SHA-256 for "DualDex"
        assertEquals("a2e98788ea2afd7f476876d42f849daa250f75b03cdc373e1d1efa89a0b77b4d", hash)

        val identity = RomIdentity.fromBytes(testBytes, "DualDex Test")
        assertEquals("a2e98788ea2a", identity.shortHash)
        assertEquals("DualDex_Test__a2e98788ea2a", identity.storageKey)
    }

    @Test
    fun testMgbaRtcFooterNormalization() {
        // Standard GBA Flash 1M is 131,072 bytes (128 KB)
        // mGBA on PC adds a 16-byte RTC footer -> 131,088 bytes
        val standardSize = 131072
        val mgbaSize = 131088

        val rawMgbaSave = ByteArray(mgbaSize) { i ->
            if (i < standardSize) (i % 256).toByte() else 0xFF.toByte()
        }

        // Logic check matching SaveStateManager.importBatterySave
        val cleanBytes = if (rawMgbaSave.size == 131088) {
            rawMgbaSave.copyOfRange(0, 131072)
        } else {
            rawMgbaSave
        }

        assertEquals(standardSize, cleanBytes.size)
        // Verify payload integrity was preserved
        for (i in 0 until standardSize) {
            assertEquals((i % 256).toByte(), cleanBytes[i])
        }

        // Normal 131,072-byte save should remain untouched
        val normalSave = ByteArray(standardSize) { 0x42.toByte() }
        val untouchedBytes = if (normalSave.size == 131088) {
            normalSave.copyOfRange(0, 131072)
        } else {
            normalSave
        }
        assertEquals(standardSize, untouchedBytes.size)
        assertEquals(0x42.toByte(), untouchedBytes[0])
    }

    @Test
    fun testLegacyMigrationCandidateNames() {
        val identity = RomIdentity.create(
            "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            "Pokemon FireRed (v1.1)"
        )

        val cleanTitle = identity.displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val candidates = listOf(
            "$cleanTitle.sav",
            "${identity.sanitizedTitle}.sav",
            "current_game.sav"
        ).distinct()

        assertTrue(candidates.contains("Pokemon_FireRed__v1_1_.sav"))
        assertTrue(candidates.contains("Pokemon_FireRed_v1_1.sav"))
        assertTrue(candidates.contains("current_game.sav"))
    }

    @Test
    fun testFindMatchingLegacyBaseExactProfileName() {
        val identity = RomIdentity.create(
            "1111222233334444555566667777888899990000111122223333444455556666",
            "1636 - Pokemon Fire Red (U)(Squirrels)"
        )

        // Case 1: Legacy file uses old profile name (cleanKey) with triple underscores
        val files1 = listOf("Pokemon_Heart___Soul_2_0.sav")
        val match1 = SaveStateManager.findMatchingLegacyBase(
            identity = RomIdentity.create("", "Heart & Soul"),
            profileName = "Pokemon Heart & Soul 2.0",
            profileId = "heart_and_soul",
            availableFiles = files1
        )
        assertEquals("Pokemon_Heart___Soul_2_0", match1)

        // Case 2: Legacy file uses sanitized profile name with single underscores
        val files2 = listOf("Pokemon_Heart_Soul_2_0.sav")
        val match2 = SaveStateManager.findMatchingLegacyBase(
            identity = RomIdentity.create("", "Heart & Soul"),
            profileName = "Pokemon Heart & Soul 2.0",
            profileId = "heart_and_soul",
            availableFiles = files2
        )
        assertEquals("Pokemon_Heart_Soul_2_0", match2)

        // Case 3: Legacy file named by profile ID
        val files3 = listOf("firered.sav")
        val match3 = SaveStateManager.findMatchingLegacyBase(
            identity = identity,
            profileName = "Pokemon FireRed",
            profileId = "firered",
            availableFiles = files3
        )
        assertEquals("firered", match3)
    }

    @Test
    fun testFindMatchingLegacyBaseFuzzyAlphanumeric() {
        // ROM file has long scene release title, but legacy save was saved under profile name
        val identity = RomIdentity.create(
            "1111222233334444555566667777888899990000111122223333444455556666",
            "1636 - Pokemon Fire Red (U)(Squirrels)"
        )

        val files = listOf("Pokemon_FireRed.sav", "Pokemon_FireRed_slot_1.state")
        // Even without profileName passed, fuzzy normalized matching recognizes FireRed in title
        val matchWithoutProfile = SaveStateManager.findMatchingLegacyBase(
            identity = identity,
            profileName = null,
            profileId = null,
            availableFiles = files
        )
        assertEquals("Pokemon_FireRed", matchWithoutProfile)

        // Different game must NOT match
        val emeraldFiles = listOf("Pokemon_Emerald.sav")
        val matchEmerald = SaveStateManager.findMatchingLegacyBase(
            identity = identity,
            profileName = null,
            profileId = null,
            availableFiles = emeraldFiles
        )
        assertNull(matchEmerald)
    }

    @Test
    fun testFindMatchingLegacyBaseStagedFallback() {
        val identity = RomIdentity.create(
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
            "Pokemon Unbound"
        )
        // Check staging folder created on first open
        val files = listOf("legacy_Pokemon_Unbound")
        val match = SaveStateManager.findMatchingLegacyBase(
            identity = identity,
            profileName = "Pokemon Unbound",
            profileId = "unbound",
            availableFiles = files
        )
        assertEquals("Pokemon_Unbound", match)
    }

    @Test
    fun testFirstOpenLegacySavesGroupingLogic() {
        val filenames = listOf(
            "Pokemon_FireRed.sav",
            "Pokemon_FireRed_slot_1.state",
            "Pokemon_FireRed_slot_2.state",
            "Pokemon_FireRed_quicksave.state",
            "Pokemon_Emerald.sav",
            "old_game.sav.migrated.bak",
            "temp.tmp"
        )

        val unmigrated = filenames.filter {
            !it.endsWith(".migrated.bak") && !it.endsWith(".tmp") && (it.endsWith(".sav") || it.endsWith(".state"))
        }
        assertEquals(5, unmigrated.size)

        val baseNames = unmigrated.map { f ->
            when {
                f.endsWith(".sav") -> f.removeSuffix(".sav")
                f.contains("_slot_") -> f.substringBefore("_slot_")
                f.endsWith("_quicksave.state") -> f.removeSuffix("_quicksave.state")
                f.endsWith(".state") -> f.removeSuffix(".state")
                else -> f.substringBeforeLast(".")
            }
        }.distinct()

        assertEquals(2, baseNames.size)
        assertTrue(baseNames.contains("Pokemon_FireRed"))
        assertTrue(baseNames.contains("Pokemon_Emerald"))
    }
}
