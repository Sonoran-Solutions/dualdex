package com.dualdex.battle

import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.romhack.ProfileDetectionResult
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSessionStateTest {

    private val hashB = "b".repeat(64)

    private fun mon(species: Int): ParsedPokemon = ParsedPokemon(
        isValid = true,
        isEmpty = false,
        pid = 1L,
        tid = 2,
        sid = 3,
        nickname = "Mon",
        otName = "OT",
        species = species,
        heldItem = 0,
        level = 50,
        nature = 0,
        natureName = "Hardy",
        isShiny = false,
        abilitySlot = 0,
        isEgg = false,
        friendship = 0,
        experience = 0L,
        hpIv = 0,
        attackIv = 0,
        defenseIv = 0,
        speedIv = 0,
        spAttackIv = 0,
        spDefenseIv = 0,
        hpEv = 0,
        attackEv = 0,
        defenseEv = 0,
        speedEv = 0,
        spAttackEv = 0,
        spDefenseEv = 0,
        moves = intArrayOf(0, 0, 0, 0),
        pp = intArrayOf(0, 0, 0, 0),
        currentHp = 100,
        maxHp = 100,
        attack = 50,
        defense = 50,
        speed = 50,
        spAttack = 50,
        spDefense = 50,
        statusCondition = 0L
    )

    private fun profile(hash: String) = RomHackProfile.DEFAULT_FIRERED.copy(
        sha256Hashes = listOf(hash)
    )

    @Test
    fun manualPartySelectionNeverChangesObservedActiveBattler() {
        val vm = CompanionViewModel()

        vm.selectMember(3)
        assertEquals(3, vm.selectedMemberIndex.value)
        assertEquals(-1, vm.activePlayerBattlerIndex.value)

        vm.setActivePlayerBattlerIndex(1)
        vm.selectMember(3)
        assertEquals(3, vm.selectedMemberIndex.value)
        assertEquals(1, vm.activePlayerBattlerIndex.value)
    }

    @Test
    fun newRomSessionClearsAllPreviousBattleObservations() {
        val vm = CompanionViewModel()
        vm.updateManualParty(listOf(mon(6), mon(35)))
        vm.updateEnemyParty(listOf(mon(9), mon(39)))
        vm.selectMember(3)
        vm.setActivePlayerBattlerIndex(1)
        vm.setActiveEnemyMemberIndex(1)
        vm.setIsInBattle(true)
        vm.updatePlayerStatStages(StatStages(atk = 2, spe = -1))
        vm.updateEnemyStatStages(StatStages(def = -2))
        vm.updateBattleUiSnapshot(
            BattleUiSnapshot(
                state = BattleUiState.COMMAND_MENU,
                selectedActionIndex = 1,
                stateConfidence = DataConfidence.VERIFIED,
                isInputAccepted = true,
                capabilities = BattleInteractionCapabilities.FULL_VERIFIED
            )
        )
        vm.updatePlayerLocation(
            PlayerLocation(1, 2, 0, 3, 4, 5, 6, 7, 8, false, true)
        )

        val profileB = profile(hashB)
        vm.setRomSession(
            profile = profileB,
            identity = RomIdentity(hashB, "ROM B"),
            detection = ProfileDetectionResult(profileB, ProfileMatchMethod.EXACT_SHA256, hashB)
        )

        assertTrue(vm.playerParty.value.isEmpty())
        assertTrue(vm.enemyParty.value.isEmpty())
        assertEquals(0, vm.selectedMemberIndex.value)
        assertEquals(-1, vm.activePlayerBattlerIndex.value)
        assertEquals(-1, vm.activeEnemyMemberIndex.value)
        assertFalse(vm.isInBattle.value)
        assertEquals(BattlePresence.UNKNOWN, vm.battlePresence.value)
        assertTrue(vm.playerStatStages.value.isNeutral)
        assertTrue(vm.enemyStatStages.value.isNeutral)
        assertEquals(BattleUiSnapshot(), vm.battleUiSnapshot.value)
        assertNull(vm.playerLocation.value)
        assertEquals(profileB, vm.activeProfile.value)
        assertEquals(hashB, vm.activeRomIdentity.value?.sha256)
        assertTrue(vm.runtimeRomTrust.value.exactRuntimeVerified)
    }
}
