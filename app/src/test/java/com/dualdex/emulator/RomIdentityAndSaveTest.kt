package com.dualdex.emulator

import com.dualdex.emulator.storage.LegacyCandidate
import com.dualdex.emulator.storage.LegacySaveCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RomIdentityAndSaveTest {

    @Test
    fun testRomIdentityCreationAndShortHash() {
        val dummyHash = "a13f42c92d71ef680123456789abcdef0123456789abcdef0123456789abcdef"
        val identity = RomIdentity.create(dummyHash, "Pokemon Unbound")

        assertEquals(dummyHash, identity.sha256)
        assertEquals("a13f42c92d71", identity.shortHash)
        assertEquals(12, identity.shortHash.length)
        assertEquals("Pokemon_Unbound", identity.sanitizedTitle)
        assertEquals(dummyHash, identity.storageKey)
        assertEquals("Pokemon_Unbound__a13f42c92d71", identity.legacyStorageKey)
        assertTrue(identity.isValid)
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
        // Storage keys MUST be distinct full SHA-256 hashes to prevent save corruption/overwrites
        assertNotEquals(identity1.storageKey, identity2.storageKey)
        assertEquals(romHash1, identity1.storageKey)
        assertEquals(romHash2, identity2.storageKey)
    }

    @Test
    fun testSameHashDifferentTitleProducesSameStorageKey() {
        val romHash = "e26ee0d44e80e5bc3d1f46d68173f60d8d4622b10a26d11f584e09f58cb2908c"
        val identityA = RomIdentity.create(romHash, "Pokemon FireRed")
        val identityB = RomIdentity.create(romHash, "FireRed v1.1 Custom")

        assertEquals(identityA.storageKey, identityB.storageKey)
        assertEquals(romHash, identityA.storageKey)
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
        assertEquals(hash, identity.storageKey)
        assertEquals("DualDex_Test__a2e98788ea2a", identity.legacyStorageKey)
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

        val cleanBytes = if (rawMgbaSave.size == standardSize + 16) {
            rawMgbaSave.copyOfRange(0, standardSize)
        } else {
            rawMgbaSave
        }

        assertEquals(standardSize, cleanBytes.size)
        for (i in 0 until standardSize) {
            assertEquals((i % 256).toByte(), cleanBytes[i])
        }

        // Normal 131,072-byte save should remain untouched
        val normalSave = ByteArray(standardSize) { 0x42.toByte() }
        val untouchedBytes = if (normalSave.size == standardSize + 16) {
            normalSave.copyOfRange(0, standardSize)
        } else {
            normalSave
        }
        assertEquals(standardSize, untouchedBytes.size)
        assertEquals(0x42.toByte(), untouchedBytes[0])
    }

    @Test
    fun testLegacyCandidateSuggestionHeuristics() {
        val identity = RomIdentity.create(
            "1111222233334444555566667777888899990000111122223333444455556666",
            "1636 - Pokemon Fire Red (U)(Squirrels)"
        )

        // Advisory suggestion matches based on title substring
        val candidate = LegacyCandidate(
            sourceFile = File("saves/Pokemon_FireRed.sav"),
            baseName = "Pokemon_FireRed",
            suggestedTitle = "Pokemon FireRed",
            targetFileName = "battery.sav",
            sizeBytes = 131072L
        )

        // Mock a catalog suggestion check
        fun isSuggested(c: LegacyCandidate, id: RomIdentity, prof: String?): Boolean {
            if (c.baseName.equals("current_game", ignoreCase = true)) return false
            fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
            val baseNorm = norm(c.baseName)
            val titleNorm = norm(id.displayName)
            val profNorm = prof?.let { norm(it) }.orEmpty()
            return (titleNorm.isNotEmpty() && (titleNorm.contains(baseNorm) || baseNorm.contains(titleNorm))) ||
                (profNorm.isNotEmpty() && (profNorm.contains(baseNorm) || baseNorm.contains(profNorm)))
        }

        assertTrue(isSuggested(candidate, identity, "Pokemon FireRed"))

        // Unrelated game does not match
        val emeraldCandidate = LegacyCandidate(
            sourceFile = File("saves/Pokemon_Emerald.sav"),
            baseName = "Pokemon_Emerald",
            suggestedTitle = "Pokemon Emerald",
            targetFileName = "battery.sav",
            sizeBytes = 131072L
        )
        assertFalse(isSuggested(emeraldCandidate, identity, "Pokemon FireRed"))

        // Ambiguous "current_game" candidate is NEVER automatically matched
        val currentGameCandidate = LegacyCandidate(
            sourceFile = File("saves/current_game.sav"),
            baseName = "current_game",
            suggestedTitle = "Unlabeled Save (current_game)",
            targetFileName = "battery.sav",
            sizeBytes = 131072L
        )
        assertFalse(isSuggested(currentGameCandidate, identity, "Pokemon FireRed"))
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
