package com.dualdex.pokemon

import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.hns.Hns205MapData
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomCompatibilityStatus
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trust and stale-state behaviour at the location boundary.
 *
 * The exact-ROM trust gate is the prerequisite for authoritative live-location
 * presentation. Selecting the Heart & Soul static map pack is *not* trust, and a
 * profile or header that merely names Heart & Soul must not unlock anything.
 */
class HnsLocationTrustBoundaryTest {

    private val hnsProfile = RomHackProfile(
        id = "heart_and_soul",
        name = "Pokemon Heart & Soul",
        baseGame = "Emerald",
        gameId = 8,
        engine = "pokeemerald-expansion",
        hasPhysSpecSplit = true,
        headerTitles = listOf("HEARTSOUL", "HNS", "POKEHNS", "HEART", "SOUL"),
        // Deliberately still empty: H&S is recognised but not yet exact-verified.
        sha256Hashes = emptyList(),
        isVerified = true,
        memoryLayoutVerified = true,
        gameDataPackId = "hns_2_0_5",
    )

    private fun location(group: Int = 0, num: Int = 0) = PlayerLocation(
        mapGroup = group,
        mapNum = num,
        warpId = 0,
        x = 10,
        y = 12,
        localX = 10,
        localY = 12,
        escapeMapGroup = 0,
        escapeMapNum = 0,
        isIndoors = false,
        isValid = true,
    )

    // ------------------------------------------------------------------
    // Recognised-but-unverified H&S
    // ------------------------------------------------------------------

    @Test
    fun recognisedButUnverifiedHnsCannotReadLiveMemory() {
        val compatibility = RomCompatibility(
            status = RomCompatibilityStatus.RECOGNIZED_UNVERIFIED,
            profile = hnsProfile,
            detectedProfile = hnsProfile,
            matchMethod = ProfileMatchMethod.HEADER_TITLE,
            sha256 = "unknown_hns_hash",
            reason = "header title matched",
        )
        val trust = RuntimeRomTrust.from(compatibility, "unknown_hns_hash")

        assertFalse("a header match is not verification", trust.exactRuntimeVerified)
        assertFalse("no live memory may be read", trust.mayReadLiveMemory)
        assertFalse(trust.isVerified)
    }

    @Test
    fun aHeaderOrProfileNameThatMentionsHeartAndSoulDoesNotUnlockReads() {
        // Same header titles as the real profile, but not the pinned build.
        val impostor = hnsProfile.copy(
            id = "hns_remix",
            name = "Heart and Soul Remix",
            sha256Hashes = emptyList(),
        )
        val compatibility = RomCompatibility(
            status = RomCompatibilityStatus.RECOGNIZED_UNVERIFIED,
            profile = impostor,
            detectedProfile = impostor,
            matchMethod = ProfileMatchMethod.FILENAME_KEYWORD,
            sha256 = "remix_hash",
            reason = "filename keyword",
        )
        val trust = RuntimeRomTrust.from(compatibility, "remix_hash")
        assertFalse(trust.mayReadLiveMemory)
        // Selecting the H&S data pack is not trust either.
        assertEquals("hns_2_0_5", impostor.gameDataPackId)
        assertFalse(trust.exactRuntimeVerified)
    }

    @Test
    fun staticHnsMapPackAloneDoesNotEstablishTrust() {
        // The pinned static map data is usable without a ROM at all, and that must
        // never be confused with runtime trust.
        assertNotNull(Hns205MapData.findLocation(0, 0))

        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        assertEquals(LocationStrategy.HEART_AND_SOUL_205, vm.locationStrategy.value)
        assertFalse(
            "publishing the H&S profile must not grant live memory access",
            vm.runtimeRomTrust.value.mayReadLiveMemory
        )
    }

    @Test
    fun publishingAPlainProfileKeepsLiveMemoryDenied() {
        val vm = CompanionViewModel()
        vm.setRomInfo(gameId = 8, romTitle = "HEARTSOUL", profile = hnsProfile)
        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)
        assertNull(vm.resolvedLocation.value)

        vm.setRomIdentity(RomIdentity("some_hash", "HEARTSOUL"))
        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)
        assertNull(vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)
    }

    @Test
    fun unsupportedRomWithAHnsLookingHeaderSelectsNoStrategy() {
        val vm = CompanionViewModel()
        vm.setRomInfo(gameId = 8, romTitle = "POKEHNS", profile = RomHackProfile.UNSUPPORTED)
        assertEquals(LocationStrategy.UNVERIFIED, vm.locationStrategy.value)

        vm.updatePlayerLocation(location(0, 0))
        assertNull(
            "an unsupported ROM must not present a live location",
            vm.resolvedLocation.value
        )
    }

    // ------------------------------------------------------------------
    // Strategy isolation and stale state
    // ------------------------------------------------------------------

    @Test
    fun switchingStrategiesClearsLiveLocationState() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 0))
        assertEquals(LocationStrategy.HEART_AND_SOUL_205, vm.locationStrategy.value)
        assertNotNull(vm.playerLocation.value)

        // Switching to a different game must not leave H&S position on screen.
        val emerald = RomHackProfile(
            id = "vanilla_emerald",
            name = "Pokemon Emerald",
            baseGame = "Emerald",
            gameId = 1,
        )
        vm.setProfile(emerald)
        assertEquals(LocationStrategy.EMERALD, vm.locationStrategy.value)
        assertNull("H&S location must not survive a strategy change", vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)
    }

    @Test
    fun hnsToFireRedToEmeraldTransitionsDoNotLeakState() {
        val vm = CompanionViewModel()

        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 0))
        assertNotNull(vm.playerLocation.value)

        val fireRed = RomHackProfile(
            id = "vanilla_firered",
            name = "Pokemon FireRed",
            baseGame = "FireRed",
            gameId = 2,
        )
        vm.setProfile(fireRed)
        assertEquals(LocationStrategy.FIRERED, vm.locationStrategy.value)
        assertNull(vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)

        val emerald = RomHackProfile(
            id = "vanilla_emerald",
            name = "Pokemon Emerald",
            baseGame = "Emerald",
            gameId = 1,
        )
        vm.setProfile(emerald)
        assertEquals(LocationStrategy.EMERALD, vm.locationStrategy.value)

        vm.setProfile(hnsProfile)
        assertEquals(LocationStrategy.HEART_AND_SOUL_205, vm.locationStrategy.value)
        assertNull("no cached location may reappear", vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)
    }

    @Test
    fun clearingTheRomSessionDropsLocationState() {
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        vm.updatePlayerLocation(location(0, 0))
        assertNotNull(vm.playerLocation.value)

        vm.clearRomSession()
        assertEquals(LocationStrategy.UNVERIFIED, vm.locationStrategy.value)
        assertNull(vm.playerLocation.value)
        assertNull(vm.resolvedLocation.value)
        assertNull(vm.locationUnavailableReason.value)
    }

    @Test
    fun verifiedHnsProfileStillNeedsAnExactHashToReadMemory() {
        val verifiedHash = "a".repeat(64)
        val verifiedProfile = hnsProfile.copy(sha256Hashes = listOf(verifiedHash))
        val compatibility = RomCompatibility(
            status = RomCompatibilityStatus.VERIFIED,
            profile = verifiedProfile,
            detectedProfile = verifiedProfile,
            matchMethod = ProfileMatchMethod.EXACT_SHA256,
            sha256 = verifiedHash,
            reason = "exact hash match",
        )

        // Exact hash present and matching: reads allowed.
        val trusted = RuntimeRomTrust.from(compatibility, verifiedHash)
        assertTrue(trusted.mayReadLiveMemory)

        // A different running ROM hash must not inherit that trust.
        val mismatched = RuntimeRomTrust.from(compatibility, "b".repeat(64))
        assertFalse(
            "trust must not outlive the ROM identity it was granted for",
            mismatched.mayReadLiveMemory
        )

        // No active ROM at all.
        val absent = RuntimeRomTrust.from(compatibility, null)
        assertFalse(absent.mayReadLiveMemory)
    }

    @Test
    fun deniedReadsAndDeniedPresentationAreSeparateBoundaries() {
        // A denied read: the poller never runs, so nothing is published.
        val vm = CompanionViewModel()
        vm.setProfile(hnsProfile)
        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)
        assertNull(vm.resolvedLocation.value)

        // A denied presentation: a raw location exists but the strategy cannot
        // name it, so the UI has nothing authoritative to show.
        val resolution = LocationResolver.resolve(
            LocationStrategy.HEART_AND_SOUL_205, location(0, 4242)
        )
        assertFalse(resolution.isResolved)
        assertEquals(LocationUnavailableReason.UNKNOWN_MAP_ID, resolution.reason)
    }
}
