package com.dualdex.companion

import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.LibretroCoreBridge
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.bindSyntheticRom
import com.dualdex.emulator.syntheticRomBridge
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RomSessionObservationSafetyTest {
    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)
    private val profileA = RomHackProfile(
        id = "synthetic-a", name = "Synthetic A", baseGame = "FireRed", gameId = 2,
        sha256Hashes = listOf(shaA), isVerified = true, memoryLayoutVerified = true
    )
    private val profileB = profileA.copy(id = "synthetic-b", name = "Synthetic B", sha256Hashes = listOf(shaB))

    private fun mon(species: Int) = ParsedPokemon(
        isValid = true, isEmpty = false, pid = species.toLong(), tid = 1, sid = 2,
        nickname = "synthetic", otName = "test", species = species, heldItem = 0,
        level = 5, nature = 0, natureName = "Hardy", isShiny = false, abilitySlot = 0,
        isEgg = false, friendship = 0, experience = 0L, hpIv = 0, attackIv = 0,
        defenseIv = 0, speedIv = 0, spAttackIv = 0, spDefenseIv = 0, hpEv = 0,
        attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(0, 0, 0, 0), pp = intArrayOf(0, 0, 0, 0), currentHp = 1,
        maxHp = 1, attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
        statusCondition = 0L
    )

    @Test fun unboundSessionCannotCallProfileReadersThenExactBindingReadsNormally() {
        val reads = AtomicInteger()
        val core = object : LibretroCoreCoordinator(bridge = syntheticRomBridge()) {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                reads.incrementAndGet()
                return arrayOf(mon(25))
            }
        }
        val vm = CompanionViewModel(coreCoordinator = core)
        val identity = RomIdentity(shaA, "Synthetic A")
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), identity)

        vm.pollTick()
        assertEquals(0, reads.get())
        assertTrue(vm.playerParty.value.isEmpty())

        bindSyntheticRom(core, identity)
        vm.pollTick()
        assertEquals(1, reads.get())
        assertEquals(25, vm.playerParty.value.single().species)
    }

    @Test fun trustRevocationDuringReaderCannotLeaveOldResultAfterSetterReturns() {
        val enteredReader = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val setterStarted = CountDownLatch(1)
        val setterDone = CountDownLatch(1)
        val core = object : LibretroCoreCoordinator(bridge = syntheticRomBridge()) {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                enteredReader.countDown()
                check(releaseReader.await(5, TimeUnit.SECONDS))
                return arrayOf(mon(6))
            }
        }
        val vm = CompanionViewModel(coreCoordinator = core)
        val identity = RomIdentity(shaA, "Synthetic A")
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), identity)
        bindSyntheticRom(core, identity)

        val poll = Thread { vm.pollTick() }.apply { start() }
        assertTrue("reader was not entered", enteredReader.await(5, TimeUnit.SECONDS))
        val setter = Thread {
            setterStarted.countDown()
            vm.setProfile(RomHackProfile.UNSUPPORTED)
            setterDone.countDown()
        }.apply { start() }
        assertTrue(setterStarted.await(5, TimeUnit.SECONDS))

        releaseReader.countDown()
        poll.join(5000)
        setter.join(5000)
        assertTrue(!poll.isAlive && !setter.isAlive)
        assertTrue(setterDone.count == 0L)
        assertTrue(vm.playerParty.value.isEmpty())
        assertTrue(!vm.runtimeRomTrust.value.mayReadLiveMemory)
    }

    @Test fun sameHashReloadAdvancesGenerationAndNextObservationUsesNewState() {
        val species = AtomicInteger(1)
        val core = object : LibretroCoreCoordinator(bridge = object : LibretroCoreBridge {
            override fun loadRom(romPath: String) = true
            override fun loadState(statePath: String) = true
        }) {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? = arrayOf(mon(species.get()))
        }
        val vm = CompanionViewModel(coreCoordinator = core)
        val identity = RomIdentity(shaA, "Synthetic A")
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), identity)
        bindSyntheticRom(core, identity)
        vm.pollTick()
        val oldGeneration = core.loadedRomSnapshot().coreGeneration
        assertEquals(1, vm.playerParty.value.single().species)

        species.set(2)
        core.loadState("synthetic.state")
        assertEquals(shaA, core.loadedRomSnapshot().sha256)
        assertTrue(core.loadedRomSnapshot().coreGeneration > oldGeneration)
        vm.pollTick()
        assertEquals(2, vm.playerParty.value.single().species)

        val beforeReload = core.loadedRomSnapshot().coreGeneration
        species.set(3)
        assertTrue(core.loadVerifiedRom("same-hash-reload.gba", identity))
        assertEquals(shaA, core.loadedRomSnapshot().sha256)
        assertTrue(core.loadedRomSnapshot().coreGeneration > beforeReload)
        vm.pollTick()
        assertEquals(3, vm.playerParty.value.single().species)
    }

    @Test fun readerLockTimeoutDoesNotPublishOrInvokeReaders() {
        val reads = AtomicInteger()
        val core = object : LibretroCoreCoordinator(bridge = syntheticRomBridge()) {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? {
                reads.incrementAndGet()
                return arrayOf(mon(25))
            }
        }
        val vm = CompanionViewModel(coreCoordinator = core)
        val identity = RomIdentity(shaA, "Synthetic A")
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), identity)
        bindSyntheticRom(core, identity)

        val pollFinished = CountDownLatch(1)
        core.lock.lock()
        try {
            Thread {
                vm.pollTick()
                pollFinished.countDown()
            }.start()
            assertTrue("poll should fail closed at the coordinator's lock timeout", pollFinished.await(5, TimeUnit.SECONDS))
        } finally {
            core.lock.unlock()
        }
        assertEquals(0, reads.get())
        assertTrue(vm.playerParty.value.isEmpty())
    }
}
