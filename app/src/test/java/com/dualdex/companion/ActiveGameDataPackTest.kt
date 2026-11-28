package com.dualdex.companion

import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.Gen3VanillaDataPack
import com.dualdex.pokemon.ModernDataPack
import com.dualdex.pokemon.unbound.UnboundDataPack
import com.dualdex.pokemon.unbound.UnboundSpeciesTable
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ActiveGameDataPackTest {
    private val profile = RomHackProfile.DEFAULT_FIRERED.copy(gameDataPackId = "unbound_exact")
    private val identity = RomIdentity(UnboundSpeciesTable.ROM_SHA256, "Renamed ROM")

    @Test fun everyProfileAndIdentitySetterPublishesTheSameResolvedPack() {
        val vm = CompanionViewModel()
        fun assertCurrent() {
            val expected = GameDataPackRegistry.getForProfile(vm.activeProfile.value, vm.activeRomIdentity.value?.sha256)
            assertSame(expected, vm.activeGameDataPack)
        }
        assertCurrent()
        vm.setProfile(profile)
        assertCurrent()
        assertNull(vm.activeGameDataPack.getSpecies(277))
        vm.setRomIdentity(identity)
        assertCurrent()
        assertSame(UnboundDataPack, vm.activeGameDataPack)
        vm.setRomIdentity(identity.copy(sha256 = "0".repeat(64)))
        assertCurrent()
        assertNull(vm.activeGameDataPack.getSpecies(277))
        vm.setRomIdentity(identity)
        vm.setRomInfo(9, "Unbound", profile)
        assertCurrent()
        assertSame(UnboundDataPack, vm.activeGameDataPack)
        vm.setRomInfo(2, "FireRed", RomHackProfile.DEFAULT_FIRERED)
        assertCurrent()
        assertSame(Gen3VanillaDataPack, vm.activeGameDataPack)
        vm.setProfile(profile.copy(gameDataPackId = "modern"))
        assertCurrent()
        assertSame(ModernDataPack, vm.activeGameDataPack)
        vm.setRomSession(
            RomCompatibility.recognizedUnverified(profile, identity.sha256, ProfileMatchMethod.HEADER_TITLE, "unverified"),
            identity
        )
        assertCurrent()
        assertSame(UnboundDataPack, vm.activeGameDataPack)
        vm.setRomIdentity(null)
        assertCurrent()
        assertEquals(RomHackProfile.UNSUPPORTED, vm.activeProfile.value)
        vm.setRomSession(
            RomCompatibility.recognizedUnverified(profile, identity.sha256, ProfileMatchMethod.HEADER_TITLE, "unverified"),
            identity
        )
        vm.clearRomSession()
        assertCurrent()
        assertNull(vm.activeRomIdentity.value)
        vm.setProfile(profile)
        assertNull(vm.activeGameDataPack.getSpecies(277))
    }

    @Test fun packReadDoesNotWaitForObservationLock() {
        val vm = CompanionViewModel()
        vm.setProfile(profile)
        vm.setRomIdentity(identity)
        val lockField = CompanionViewModel::class.java.getDeclaredField("observationSessionLock").apply {
            isAccessible = true
        }
        val reader = Executors.newSingleThreadExecutor()
        try {
            synchronized(lockField.get(vm)) {
                assertSame(UnboundDataPack, reader.submit<com.dualdex.pokemon.GameDataPack> {
                    vm.activeGameDataPack
                }.get(2, TimeUnit.SECONDS))
            }
        } finally {
            reader.shutdownNow()
            assertTrue(reader.awaitTermination(2, TimeUnit.SECONDS))
        }
    }
}
