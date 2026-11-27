package com.dualdex.nuzlocke

import com.dualdex.battle.ActiveEnemyResolution
import com.dualdex.battle.ActiveEnemyState
import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.LibretroCoreBridge
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.emulator.bindSyntheticRom
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NuzlockeTrackerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)

    private val speciesNames = mapOf(
        16 to "Pidgey",
        19 to "Rattata",
        25 to "Pikachu",
        261 to "Poochyena",
        263 to "Zigzagoon",
        265 to "Wurmple"
    )

    private val route101 = PlayerLocation(
        mapGroup = 0, mapNum = 16, warpId = 0, x = 5, y = 5, localX = 5, localY = 5,
        escapeMapGroup = 0, escapeMapNum = 0, isIndoors = false, isValid = true
    )

    private val route102 = route101.copy(mapNum = 17)

    private val invalidLocation = route101.copy(isValid = false)

    private fun mon(
        species: Int,
        pid: Long = species * 1000L,
        tid: Int = 12345,
        sid: Int = 54321,
        nickname: String = "",
        level: Int = 5,
        hp: Int = 20,
        maxHp: Int = 20,
        isEgg: Boolean = false,
        isValid: Boolean = true,
        isEmpty: Boolean = false
    ) = ParsedPokemon(
        isValid = isValid,
        isEmpty = isEmpty,
        pid = pid,
        tid = tid,
        sid = sid,
        nickname = nickname,
        otName = "Red",
        species = species,
        heldItem = 0,
        level = level,
        nature = 0,
        natureName = "Hardy",
        isShiny = false,
        abilitySlot = 0,
        isEgg = isEgg,
        friendship = 70,
        experience = 100L,
        hpIv = 10, attackIv = 10, defenseIv = 10, speedIv = 10, spAttackIv = 10, spDefenseIv = 10,
        hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
        moves = intArrayOf(33, 0, 0, 0),
        pp = intArrayOf(35, 0, 0, 0),
        currentHp = hp,
        maxHp = maxHp,
        attack = 10, defense = 10, speed = 10, spAttack = 10, spDefense = 10,
        statusCondition = 0L
    )

    private fun resolver(id: Int): String = speciesNames[id] ?: "#$id"

    @Test
    fun firstWildEncounterIsRecordedAndResolvesToCaughtOnBattleExit() {
        var now = 1_000L
        val tracker = NuzlockeTracker(NuzlockeRunState(shaA, enabled = true), clockMs = { now })
        val starter = mon(species = 25, pid = 101L, nickname = "Sparky")
        val wildZigzagoon = mon(species = 263, pid = 202L, level = 3)

        // Enter wild single battle on Route 101.
        assertTrue(
            tracker.onObservation(
                party = listOf(starter),
                enemyParty = listOf(wildZigzagoon),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.WILD_SINGLE,
                inBattle = true,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        val met = tracker.state.encounters["0:16"]
        assertNotNull(met)
        assertEquals(EncounterOutcome.MET, met!!.outcome)
        assertEquals(263, met.speciesId)
        assertEquals("Zigzagoon", met.speciesName)
        assertEquals("Route 101", met.areaName)
        assertEquals(3, met.level)
        assertEquals(1_000L, met.recordedAtMs)

        // Exit battle with newly caught Zigzagoon in party.
        now = 2_000L
        val caughtZigzagoon = mon(species = 263, pid = 202L, nickname = "Ziggy", level = 3)
        assertTrue(
            tracker.onObservation(
                party = listOf(starter, caughtZigzagoon),
                enemyParty = emptyList(),
                activeEnemyIndex = -1,
                battleKind = NuzlockeBattleKind.NONE,
                inBattle = false,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertEquals(EncounterOutcome.CAUGHT, tracker.state.encounters["0:16"]?.outcome)

        // Subsequent wild encounter in the same area does not overwrite the caught encounter.
        val wildWurmple = mon(species = 265, pid = 303L, level = 2)
        assertFalse(
            tracker.onObservation(
                party = listOf(starter, caughtZigzagoon),
                enemyParty = listOf(wildWurmple),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.WILD_SINGLE,
                inBattle = true,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertEquals(263, tracker.state.encounters["0:16"]?.speciesId)
        assertEquals(EncounterOutcome.CAUGHT, tracker.state.encounters["0:16"]?.outcome)
    }

    @Test
    fun wildEncounterResolvesToMissedWhenNotCaughtAndIgnoresTransientEmptyParty() {
        val tracker = NuzlockeTracker(NuzlockeRunState(shaA, enabled = true), clockMs = { 5_000L })
        val starter = mon(species = 25, pid = 101L)
        val wildPoochyena = mon(species = 261, pid = 404L, level = 4)

        assertTrue(
            tracker.onObservation(
                party = listOf(starter),
                enemyParty = listOf(wildPoochyena),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.WILD_SINGLE,
                inBattle = true,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertEquals(EncounterOutcome.MET, tracker.state.encounters["0:16"]?.outcome)

        // Transient empty party on battle exit fails closed (remains MET).
        assertFalse(
            tracker.onObservation(
                party = emptyList(),
                enemyParty = emptyList(),
                activeEnemyIndex = -1,
                battleKind = NuzlockeBattleKind.NONE,
                inBattle = false,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertEquals(EncounterOutcome.MET, tracker.state.encounters["0:16"]?.outcome)

        // Valid post-battle party with no newly caught Poochyena resolves to MISSED.
        assertTrue(
            tracker.onObservation(
                party = listOf(starter),
                enemyParty = emptyList(),
                activeEnemyIndex = -1,
                battleKind = NuzlockeBattleKind.NONE,
                inBattle = false,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertEquals(EncounterOutcome.MISSED, tracker.state.encounters["0:16"]?.outcome)
    }

    @Test
    fun dupesClauseSkipsOwnedSpeciesAndAllowsSecondNonDupeEncounterToCount() {
        var now = 10_000L
        val tracker = NuzlockeTracker(NuzlockeRunState(shaA, enabled = true, dupesClause = true), clockMs = { now })
        val ownedPidgey = mon(species = 16, pid = 1001L, nickname = "Birdie")
        val wildPidgey = mon(species = 16, pid = 2002L, level = 3)
        val wildRattata = mon(species = 19, pid = 3003L, level = 4)

        // First wild encounter on Route 102 is a dupe Pidgey -> DUPES_SKIPPED.
        assertTrue(
            tracker.onObservation(
                party = listOf(ownedPidgey),
                enemyParty = listOf(wildPidgey),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.WILD_SINGLE,
                inBattle = true,
                location = route102,
                areaName = "Route 102",
                speciesNameResolver = ::resolver
            )
        )
        assertEquals(EncounterOutcome.DUPES_SKIPPED, tracker.state.encounters["0:17"]?.outcome)

        // Exiting the dupe battle keeps DUPES_SKIPPED (does not lock area as MISSED).
        assertFalse(
            tracker.onObservation(
                party = listOf(ownedPidgey),
                enemyParty = emptyList(),
                activeEnemyIndex = -1,
                battleKind = NuzlockeBattleKind.NONE,
                inBattle = false,
                location = route102,
                areaName = "Route 102",
                speciesNameResolver = ::resolver
            )
        )
        assertEquals(EncounterOutcome.DUPES_SKIPPED, tracker.state.encounters["0:17"]?.outcome)

        // Second wild encounter on Route 102 is a non-dupe Rattata -> becomes official MET encounter.
        now = 20_000L
        assertTrue(
            tracker.onObservation(
                party = listOf(ownedPidgey),
                enemyParty = listOf(wildRattata),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.WILD_SINGLE,
                inBattle = true,
                location = route102,
                areaName = "Route 102",
                speciesNameResolver = ::resolver
            )
        )
        val official = tracker.state.encounters["0:17"]!!
        assertEquals(19, official.speciesId)
        assertEquals("Rattata", official.speciesName)
        assertEquals(EncounterOutcome.MET, official.outcome)

        // Catching the Rattata marks Route 102 CAUGHT.
        val caughtRattata = mon(species = 19, pid = 3003L, level = 4)
        assertTrue(
            tracker.onObservation(
                party = listOf(ownedPidgey, caughtRattata),
                enemyParty = emptyList(),
                activeEnemyIndex = -1,
                battleKind = NuzlockeBattleKind.NONE,
                inBattle = false,
                location = route102,
                areaName = "Route 102",
                speciesNameResolver = ::resolver
            )
        )
        assertEquals(EncounterOutcome.CAUGHT, tracker.state.encounters["0:17"]?.outcome)
    }

    @Test
    fun permanentFaintingPersistsAcrossPokemonCenterHealUntilManualRevive() {
        var now = 30_000L
        val tracker = NuzlockeTracker(NuzlockeRunState(shaA, enabled = true), clockMs = { now })
        val alivePikachu = mon(species = 25, pid = 777L, tid = 11, sid = 22, nickname = "Volt", level = 12, hp = 30, maxHp = 30)
        val faintedPikachu = alivePikachu.copy(currentHp = 0)
        val healedPikachu = alivePikachu.copy(currentHp = 30)
        val key = monIdentityKey(alivePikachu)

        // Observed alive first -> not fallen.
        assertFalse(
            tracker.onObservation(
                party = listOf(alivePikachu),
                enemyParty = emptyList(),
                activeEnemyIndex = -1,
                battleKind = NuzlockeBattleKind.NONE,
                inBattle = false,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertTrue(tracker.state.fallen.isEmpty())

        // Faints in battle -> permanently recorded in fallen.
        assertTrue(
            tracker.onObservation(
                party = listOf(faintedPikachu),
                enemyParty = listOf(mon(species = 19)),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.TRAINER_SINGLE,
                inBattle = true,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        val fallen = tracker.state.fallen[key]
        assertNotNull(fallen)
        assertEquals("Volt", fallen!!.nickname)
        assertEquals("Pikachu", fallen.speciesName)
        assertEquals("Route 101", fallen.areaName)
        assertEquals(12, fallen.level)

        // Healed at a Pokémon Center -> still permanently fallen!
        now = 40_000L
        assertFalse(
            tracker.onObservation(
                party = listOf(healedPikachu),
                enemyParty = emptyList(),
                activeEnemyIndex = -1,
                battleKind = NuzlockeBattleKind.NONE,
                inBattle = false,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertTrue(key in tracker.state.fallen)

        // Manual revive removes it cleanly.
        assertTrue(tracker.reviveFallen(key))
        assertTrue(tracker.state.fallen.isEmpty())
    }

    @Test
    fun eggsAndUnverifiedOrInvalidInputsFailClosed() {
        val tracker = NuzlockeTracker(NuzlockeRunState(shaA, enabled = true), clockMs = { 100L })
        val egg = mon(species = 25, pid = 555L, hp = 0, maxHp = 20, isEgg = true)
        assertEquals("", monIdentityKey(egg))

        // Fainted egg is ignored; invalid location prevents wild encounter recording.
        assertFalse(
            tracker.onObservation(
                party = listOf(egg),
                enemyParty = listOf(mon(species = 16)),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.WILD_SINGLE,
                inBattle = true,
                location = invalidLocation,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertTrue(tracker.state.fallen.isEmpty())
        assertTrue(tracker.state.encounters.isEmpty())

        // Missing location also fails closed.
        assertFalse(
            tracker.onObservation(
                party = listOf(mon(species = 25)),
                enemyParty = listOf(mon(species = 16)),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.WILD_SINGLE,
                inBattle = true,
                location = null,
                areaName = null,
                speciesNameResolver = ::resolver
            )
        )
        assertTrue(tracker.state.encounters.isEmpty())

        // Trainer and doubles battles never record wild area encounters.
        assertFalse(
            tracker.onObservation(
                party = listOf(mon(species = 25)),
                enemyParty = listOf(mon(species = 16), mon(species = 19)),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.TRAINER_SINGLE,
                inBattle = true,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertFalse(
            tracker.onObservation(
                party = listOf(mon(species = 25)),
                enemyParty = listOf(mon(species = 16)),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.DOUBLES,
                inBattle = true,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertTrue(tracker.state.encounters.isEmpty())

        // Disabled run ignores everything.
        assertTrue(tracker.setEnabled(false))
        assertFalse(
            tracker.onObservation(
                party = listOf(mon(species = 25, hp = 0)),
                enemyParty = listOf(mon(species = 16)),
                activeEnemyIndex = 0,
                battleKind = NuzlockeBattleKind.WILD_SINGLE,
                inBattle = true,
                location = route101,
                areaName = "Route 101",
                speciesNameResolver = ::resolver
            )
        )
        assertTrue(tracker.state.fallen.isEmpty())
        assertTrue(tracker.state.encounters.isEmpty())
    }

    @Test
    fun manualActionsOverrideOutcomesLevelCapAndResetRun() {
        val tracker = NuzlockeTracker(NuzlockeRunState(shaA, enabled = true), clockMs = { 500L })
        val starter = mon(species = 25, pid = 111L, hp = 0)
        tracker.onObservation(
            party = listOf(starter),
            enemyParty = listOf(mon(species = 16, pid = 222L, level = 3)),
            activeEnemyIndex = 0,
            battleKind = NuzlockeBattleKind.WILD_SINGLE,
            inBattle = true,
            location = route101,
            areaName = "Route 101",
            speciesNameResolver = ::resolver
        )
        assertTrue(tracker.setLevelCap(15))
        assertEquals(15, tracker.state.levelCap)
        assertTrue(tracker.overrideEncounterOutcome("0:16", EncounterOutcome.CAUGHT))
        assertEquals(EncounterOutcome.CAUGHT, tracker.state.encounters["0:16"]?.outcome)

        assertTrue(tracker.resetRun())
        assertTrue(tracker.state.encounters.isEmpty())
        assertTrue(tracker.state.fallen.isEmpty())
        assertTrue(tracker.state.enabled)
        assertEquals(15, tracker.state.levelCap)
    }

    @Test
    fun storePerformsAtomicJsonRoundTripAndHandlesCorruptOrMissingFiles() {
        val dir = tempFolder.newFolder("nuzlocke")
        val store = NuzlockeStore(dir)

        // Missing file returns clean default.
        val missing = store.load(shaA)
        assertEquals(NuzlockeRunState(romSha256 = shaA), missing)

        val expected = NuzlockeRunState(
            romSha256 = shaA,
            enabled = true,
            dupesClause = false,
            levelCap = 24,
            encounters = linkedMapOf(
                "0:16" to AreaEncounter("0:16", "Route 101", 263, "Zigzagoon", 3, EncounterOutcome.CAUGHT, 1234L),
                "0:17" to AreaEncounter("0:17", "Route 102", 265, "Wurmple", 4, EncounterOutcome.MISSED, 5678L)
            ),
            fallen = linkedMapOf(
                "777:355343721" to FallenMon("777:355343721", 25, "Pikachu", "Volt", 12, "Route 101", 9999L)
            )
        )
        assertTrue(store.save(expected))

        val savedFile = store.fileForRom(shaA)!!
        assertTrue(savedFile.isFile)
        assertFalse(File(dir, "${savedFile.name}.tmp").exists())

        val reloaded = store.load(shaA)
        assertEquals(expected, reloaded)

        // Corrupt JSON falls back safely without throwing.
        savedFile.writeText("{not valid json!!!")
        val fallback = store.load(shaA)
        assertEquals(NuzlockeRunState(romSha256 = shaA), fallback)
    }

    @Test
    fun companionViewModelIsolatesRunsPerRomAndFeedsOnlyVerifiedObservations() {
        val store = NuzlockeStore(tempFolder.newFolder("vm_nuzlocke"))
        val profileA = RomHackProfile(
            id = "synth-a", name = "Synth A", baseGame = "Emerald", gameId = 1,
            sha256Hashes = listOf(shaA), isVerified = true, memoryLayoutVerified = true,
            battleStateReadVerified = true
        )
        val profileB = profileA.copy(id = "synth-b", name = "Synth B", sha256Hashes = listOf(shaB))
        val idA = RomIdentity(shaA, "Synth A")
        val idB = RomIdentity(shaB, "Synth B")

        var party = arrayOf(mon(species = 25, pid = 10L, hp = 20, maxHp = 20))
        var enemyParty: Array<ParsedPokemon>? = arrayOf(mon(species = 263, pid = 20L, level = 3))
        var battlePresence = 1
        var location: PlayerLocation? = route101

        val core = object : LibretroCoreCoordinator(object : LibretroCoreBridge {
            override fun loadRom(romPath: String) = true
            override fun loadState(statePath: String) = true
            override fun loadSaveRam(savePath: String) = true
        }) {
            override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? = party
            override fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? = enemyParty
            override fun readBattlePresence(gameId: Int): Int = battlePresence
            override fun getActiveBattlerSlot(gameId: Int): Int = 0
            override fun resolveActiveEnemy(gameId: Int): ActiveEnemyResolution =
                if (battlePresence == 1) ActiveEnemyResolution(ActiveEnemyState.SLOT, partySlot = 0, opponentBattlers = 1)
                else ActiveEnemyResolution(ActiveEnemyState.NONE_ACTIVE)
            override fun readPlayerLocation(gameId: Int): PlayerLocation? = location
        }

        val vm = CompanionViewModel(coreCoordinator = core, nuzlockeStore = store)
        assertNull(vm.nuzlockeState.value)

        // Load ROM A as unverified identity first -> pollTick must fail closed.
        vm.setRomIdentity(idA)
        vm.setNuzlockeEnabled(true)
        vm.pollTick()
        assertTrue(vm.nuzlockeState.value!!.encounters.isEmpty())

        // Bind and verify ROM A -> wild battle stabilizes in 2 ticks and records Route 101 encounter.
        bindSyntheticRom(core, idA)
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), idA)
        repeat(2) { vm.pollTick() }
        assertEquals(EncounterOutcome.MET, vm.nuzlockeState.value!!.encounters["0:16"]?.outcome)

        // Player mon faints and battle ends with new Zigzagoon caught.
        party = arrayOf(
            mon(species = 25, pid = 10L, hp = 0, maxHp = 20),
            mon(species = 263, pid = 20L, level = 3, hp = 15, maxHp = 15)
        )
        enemyParty = null
        battlePresence = 0
        repeat(2) { vm.pollTick() }

        val stateA = vm.nuzlockeState.value!!
        assertEquals(EncounterOutcome.CAUGHT, stateA.encounters["0:16"]?.outcome)
        assertEquals(1, stateA.fallen.size)

        // Switching to ROM B completely isolates its run state from ROM A.
        bindSyntheticRom(core, idB)
        vm.setRomSession(RomCompatibility.verified(profileB, shaB), idB)
        val stateB = vm.nuzlockeState.value!!
        assertEquals(shaB, stateB.romSha256)
        assertFalse(stateB.enabled)
        assertTrue(stateB.encounters.isEmpty())
        assertTrue(stateB.fallen.isEmpty())

        // Switching back to ROM A reloads the persisted run from disk intact.
        bindSyntheticRom(core, idA)
        vm.setRomSession(RomCompatibility.verified(profileA, shaA), idA)
        val reloadedA = vm.nuzlockeState.value!!
        assertTrue(reloadedA.enabled)
        assertEquals(EncounterOutcome.CAUGHT, reloadedA.encounters["0:16"]?.outcome)
        assertEquals(1, reloadedA.fallen.size)
    }
}
