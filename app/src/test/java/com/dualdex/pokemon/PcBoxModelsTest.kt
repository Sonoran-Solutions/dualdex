package com.dualdex.pokemon

import com.dualdex.companion.CompanionTab
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.bindSyntheticRom
import com.dualdex.emulator.syntheticRomBridge
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcBoxModelsTest {
    @Test fun preservesSlotsAndSearchesOccupiedPokemon() {
        val mons = arrayOf(null, mon(25, "Sparky"), mon(19, "Tail"), mon(10, "Corrupt").copy(isValid = false))
        val metadata = metadata().apply {
            this[7] = 25
            this[8] = 9
            this[9] = 0
            this[10] = 19
        }
        val snapshot = PcBoxSnapshot.fromNative(metadata, mons)
        val speciesName: (PcBoxSlot) -> String = { it.speciesName(Gen3VanillaDataPack) }
        assertEquals(listOf(0, 1, 2, 3), snapshot.slots.map(PcBoxSlot::index))
        assertEquals(listOf(1, 2), snapshot.occupiedSlots().map(PcBoxSlot::index))
        assertEquals(listOf(1, 2), snapshot.findOccupiedSlots("", speciesName).map(PcBoxSlot::index))
        assertEquals(1, snapshot.findOccupiedSlots(" sparK ", speciesName).single().index)
        assertEquals(2, snapshot.findOccupiedSlots("rat", speciesName).single().index)
        assertNull(snapshot.slots[3].pokemon)
        assertEquals("Static", snapshot.slots[1].abilityName)
        assertEquals(20, snapshot.slots[1].estimatedLevel)
        assertTrue(snapshot.slots[1].canSendToCalc)
        assertFalse(snapshot.slots[1].copy(pokemon = mons[1]?.copy(isEgg = true)).canSendToCalc)
    }

    @Test fun distinguishesEmptyFromUnsupportedAndMalformed() {
        val empty = PcBoxSnapshot.fromNative(metadata(), arrayOfNulls(30))
        assertEquals(PcBoxAvailability.AVAILABLE, empty.availability)
        assertEquals(30, empty.slots.size)
        assertTrue(empty.occupiedSlots().isEmpty())
        assertEquals(PcBoxSnapshot.unavailable(),
            PcBoxSnapshot.fromNative(IntArray(PcBoxSnapshot.NATIVE_METADATA_WORDS), null))
        val malformed = PcBoxSnapshot.fromNative(metadata().apply { this[3] = 14 }, arrayOf(mon(25)))
        assertEquals(PcBoxAvailability.UNREADABLE, malformed.availability)
        assertTrue(malformed.slots.isEmpty())
        assertEquals(PcBoxAvailability.UNREADABLE, PcBoxSnapshot.fromNative(metadata(), null).availability)
        assertTrue(empty.copy(slots = listOf(PcBoxSlot(0, mon(25))),
            availability = PcBoxAvailability.UNSUPPORTED).occupiedSlots().isEmpty())
    }

    @Test fun estimatesLevelsUsingTheCorrectGrowthCurveAndBoundaries() {
        val level100 = listOf(1_000_000, 600_000, 1_640_000, 1_059_860, 800_000, 1_250_000)
        level100.forEachIndexed { growth, experience ->
            assertEquals(100, estimatePcBoxLevel(experience.toLong(), growth))
            assertEquals(99, estimatePcBoxLevel(experience.toLong() - 1, growth))
        }
        assertEquals(69, estimatePcBoxLevel(267_406, 1))
        assertEquals(68, estimatePcBoxLevel(267_405, 1))
        assertEquals(1, estimatePcBoxLevel(0, 3))
        assertNull(estimatePcBoxLevel(8_000, null))
    }

    @Test fun readsOnlyOnRequestAndClearsViewerAndCalculatorOnRomSwitch() = runBlocking {
        val boxed = PcBoxSlot(0, mon(25), nationalDex = 25, abilityId = 9, growthRate = 0)
        val snapshot = PcBoxSnapshot(0, 14, 0, listOf(boxed), PcBoxAvailability.AVAILABLE)
        var reads = 0
        val core = object : LibretroCoreCoordinator(bridge = syntheticRomBridge()) {
            override fun readPcBoxFromCore(gameId: Int, boxIndex: Int): PcBoxSnapshot {
                assertTrue(lock.isHeldByCurrentThread)
                assertEquals(2, gameId)
                assertEquals(PcBoxSnapshot.CURRENT_BOX, boxIndex)
                reads++
                return snapshot
            }
        }
        val viewModel = CompanionViewModel(scope = this, coreCoordinator = core)
        val first = RomIdentity("a".repeat(64), "First")
        val second = RomIdentity("b".repeat(64), "Second")

        viewModel.refreshPcBox()
        yield()
        assertEquals(0, reads)
        bindSyntheticRom(core, first)
        viewModel.setRomSession(compatibility(first), first)
        repeat(2) { viewModel.pollTick() }
        assertEquals(0, reads)

        viewModel.refreshPcBox()
        yield()
        assertEquals(1, reads)
        assertEquals(snapshot, viewModel.pcBoxSnapshot.value)
        viewModel.sendPcBoxToCalc(snapshot, boxed)
        assertEquals(CompanionTab.CALC, viewModel.selectedTab.value)
        assertEquals(boxed, viewModel.pcBoxCalcAttacker.value)
        assertTrue(viewModel.playerParty.value.isEmpty())

        viewModel.refreshPcBox()
        bindSyntheticRom(core, second)
        viewModel.setRomSession(compatibility(second), second)
        yield()
        assertEquals(1, reads)
        assertEquals(PcBoxSnapshot.unavailable(PcBoxAvailability.IDLE), viewModel.pcBoxSnapshot.value)
        assertNull(viewModel.pcBoxCalcAttacker.value)
        viewModel.selectTab(CompanionTab.PARTY)
        viewModel.sendPcBoxToCalc(snapshot, boxed)
        assertEquals(CompanionTab.PARTY, viewModel.selectedTab.value)
    }

    private fun metadata() = IntArray(PcBoxSnapshot.NATIVE_METADATA_WORDS).apply {
        this[0] = 2
        this[1] = 1
        this[2] = 14
        this[3] = 2
    }

    private fun compatibility(identity: RomIdentity): RomCompatibility {
        val profile = RomHackProfile(
            id = "synthetic-firered", name = "Synthetic FireRed", baseGame = "FireRed", gameId = 2,
            sha256Hashes = listOf(identity.sha256), isVerified = true, memoryLayoutVerified = true
        )
        return RomCompatibility.verified(profile, identity.sha256)
    }

    private fun mon(species: Int, nickname: String = "") = ParsedPokemon(
        isValid = true, isEmpty = false, pid = species.toLong(), tid = 1, sid = 2,
        nickname = nickname, otName = "", species = species, heldItem = 0,
        level = 0, nature = 0, natureName = "Hardy", isShiny = false, abilitySlot = 0,
        isEgg = false, friendship = 70, experience = 8_000, hpIv = 31, attackIv = 12,
        defenseIv = 0, speedIv = 0, spAttackIv = 0, spDefenseIv = 0, hpEv = 1,
        attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(33, 0, 0, 0), pp = intArrayOf(35, 0, 0, 0), currentHp = 0,
        maxHp = 0, attack = 0, defense = 0, speed = 0, spAttack = 0, spDefense = 0,
        statusCondition = 0L
    )
}
