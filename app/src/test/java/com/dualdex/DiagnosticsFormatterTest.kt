package com.dualdex

import com.dualdex.romhack.RomCompatibilityStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsFormatterTest {

    private val sampleSha256 =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private val dualDisplays = listOf(
        DisplayDescriptor(
            displayId = 0,
            isPresentationCategory = false,
            isDefaultDisplay = true,
            widthPx = 1920,
            heightPx = 1080
        ),
        DisplayDescriptor(
            displayId = 1,
            isPresentationCategory = true,
            isDefaultDisplay = false,
            widthPx = 1240,
            heightPx = 1080
        )
    )

    @Test
    fun format_loadedVerifiedRom_includesRequiredFieldsAndCapabilities() {
        val snapshot = DiagnosticsSnapshot(
            versionName = "0.9.0-beta.1",
            versionCode = 7,
            buildType = "release",
            androidRelease = "14",
            androidSdkInt = 34,
            deviceManufacturer = "AYN",
            deviceModel = "Thor",
            cpuAbi = "arm64-v8a",
            displays = dualDisplays,
            activeRomSha256 = sampleSha256.uppercase(),
            activeProfileId = "heart_and_soul_205",
            activeProfileName = "Pokemon Heart & Soul 2.0.5",
            compatibilityStatus = RomCompatibilityStatus.VERIFIED,
            exactRuntimeVerified = true,
            engine = "pokeemerald-expansion",
            baseGame = "Emerald",
            capabilities = CompanionCapabilitiesSummary(
                liveMemoryRead = true,
                battleStateRead = true,
                interactiveBattleControls = true,
                battleAutoOpen = true,
                smartFastForward = false,
                moveTileDamage = true,
                moveTileEffectiveness = true
            )
        )

        val formatted = DiagnosticsFormatter.format(snapshot)

        assertTrue(formatted.contains("### DualDex Diagnostics"))
        assertTrue(formatted.contains("DualDex 0.9.0-beta.1 (versionCode 7, release)"))
        assertTrue(formatted.contains("14 (SDK 34)"))
        assertTrue(formatted.contains("AYN / Thor (ABI: arm64-v8a)"))
        assertTrue(formatted.contains("2 (id=0 1920x1080 [default], id=1 1240x1080 [presentation])"))
        assertTrue(formatted.contains("- **ROM SHA-256:** $sampleSha256"))
        assertTrue(formatted.contains("Pokemon Heart & Soul 2.0.5 (`heart_and_soul_205`)"))
        assertTrue(formatted.contains("VERIFIED (exactRuntimeVerified=true)"))
        assertTrue(formatted.contains("pokeemerald-expansion / Emerald"))
        assertTrue(
            formatted.contains(
                "Enabled: live_memory, battle_state, interactive_battle_controls, battle_auto_open, move_tile_damage, move_tile_effectiveness"
            )
        )
        assertTrue(formatted.contains("Disabled: smart_fast_forward"))
    }

    @Test
    fun format_unverifiedRom_reportsUnverifiedTrustAndDisabledMemoryCapabilities() {
        val snapshot = DiagnosticsSnapshot(
            versionName = "0.9.0-dev",
            versionCode = 1,
            buildType = "debug",
            androidRelease = "13",
            androidSdkInt = 33,
            deviceManufacturer = "AYANEO",
            deviceModel = "Pocket DS",
            cpuAbi = "arm64-v8a",
            displays = listOf(
                DisplayDescriptor(0, isPresentationCategory = true, isDefaultDisplay = true, widthPx = 1920, heightPx = 1080),
                DisplayDescriptor(1, isPresentationCategory = true, isDefaultDisplay = false, widthPx = 1600, heightPx = 1200)
            ),
            activeRomSha256 = sampleSha256,
            activeProfileId = "pokemon_unbound",
            activeProfileName = "Pokemon Unbound",
            compatibilityStatus = RomCompatibilityStatus.RECOGNIZED_UNVERIFIED,
            exactRuntimeVerified = false,
            engine = "CFRU",
            baseGame = "FireRed",
            capabilities = CompanionCapabilitiesSummary(
                liveMemoryRead = false,
                battleStateRead = false,
                interactiveBattleControls = false,
                battleAutoOpen = true,
                smartFastForward = false,
                moveTileDamage = true,
                moveTileEffectiveness = false
            )
        )

        val formatted = DiagnosticsFormatter.format(snapshot)

        assertTrue(formatted.contains("AYANEO / Pocket DS (ABI: arm64-v8a)"))
        assertTrue(formatted.contains("id=0 1920x1080 [default,presentation]"))
        assertTrue(formatted.contains("id=1 1600x1200 [presentation]"))
        assertTrue(formatted.contains("- **ROM SHA-256:** $sampleSha256"))
        assertTrue(formatted.contains("RECOGNIZED_UNVERIFIED (exactRuntimeVerified=false)"))
        assertTrue(formatted.contains("CFRU / FireRed"))
        assertTrue(formatted.contains("Enabled: battle_auto_open, move_tile_damage"))
        assertTrue(
            formatted.contains(
                "Disabled: live_memory, battle_state, interactive_battle_controls, smart_fast_forward, move_tile_effectiveness"
            )
        )
    }

    @Test
    fun format_noRomLoaded_emitsNoneWithoutLeakingPlaceholderMetadata() {
        val snapshot = DiagnosticsSnapshot(
            versionName = "0.9.0-dev",
            versionCode = 1,
            buildType = "debug",
            androidRelease = "14",
            androidSdkInt = 34,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            cpuAbi = "arm64-v8a",
            displays = listOf(
                DisplayDescriptor(0, isPresentationCategory = false, isDefaultDisplay = true, widthPx = 2400, heightPx = 1080)
            ),
            activeRomSha256 = null,
            activeProfileId = "unsupported_rom",
            activeProfileName = "Unsupported ROM",
            compatibilityStatus = null,
            exactRuntimeVerified = false,
            engine = "Unknown",
            baseGame = "Unknown",
            capabilities = CompanionCapabilitiesSummary()
        )

        val formatted = DiagnosticsFormatter.format(snapshot)

        assertTrue(formatted.contains("- **ROM SHA-256:** none"))
        assertTrue(formatted.contains("- **Profile:** none"))
        assertTrue(formatted.contains("- **Compatibility:** none (no ROM loaded)"))
        assertTrue(formatted.contains("- **Engine / Base Game:** none"))
        assertTrue(formatted.contains("Enabled: none"))
    }

    @Test
    fun format_redactsOrDropsPathAndUriValuesPassedIntoFields() {
        val leakyUri = "content://com.android.externalstorage.documents/tree/primary%3AROMs/emerald.gba"
        val leakyPath = "/storage/emulated/0/ROMs/secret_hack.gba"

        val snapshot = DiagnosticsSnapshot(
            versionName = "0.9.0-dev",
            versionCode = 1,
            buildType = "debug",
            androidRelease = "14",
            androidSdkInt = 34,
            deviceManufacturer = "AYN",
            deviceModel = "Thor",
            cpuAbi = "arm64-v8a",
            displays = dualDisplays,
            activeRomSha256 = leakyUri,
            activeProfileId = leakyPath,
            activeProfileName = "file:///sdcard/my_game.gba",
            compatibilityStatus = RomCompatibilityStatus.UNSUPPORTED,
            exactRuntimeVerified = false,
            engine = "Vanilla",
            baseGame = "Emerald",
            capabilities = CompanionCapabilitiesSummary()
        )

        val formatted = DiagnosticsFormatter.format(snapshot)

        assertFalse("Must not leak content URI", formatted.contains("content://"))
        assertFalse("Must not leak file URI", formatted.contains("file://"))
        assertFalse("Must not leak storage path", formatted.contains("/storage/"))
        assertFalse("Must not leak sdcard path", formatted.contains("/sdcard/"))
        assertFalse("Must not leak .gba filename", formatted.contains(".gba"))
        assertTrue(formatted.contains("- **ROM SHA-256:** none"))

        // Also test when SHA-256 IS valid, path-like strings in profile fields are still redacted
        val validShaWithLeakyProfile = snapshot.copy(activeRomSha256 = sampleSha256)
        val formattedWithSha = DiagnosticsFormatter.format(validShaWithLeakyProfile)
        assertFalse(formattedWithSha.contains("file://"))
        assertFalse(formattedWithSha.contains("/storage/"))
        assertFalse(formattedWithSha.contains("/sdcard/"))
        assertFalse(formattedWithSha.contains(".gba"))
        assertTrue(formattedWithSha.contains("[redacted]"))
    }
}
