package com.dualdex.battle

import com.dualdex.companion.CompanionViewModel
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.RomIdentity
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #1: the active opponent must come from authoritative battler state, and "unknown" must be
 * a first-class answer rather than a disguised slot 0.
 *
 * These tests pin the Kotlin half of that contract: the native tuple's encoding, the
 * non-nullable/absent representation of an unresolved slot, and the fact that the battle surfaces
 * never render an opponent that the resolution did not authorise.
 */
class ActiveEnemyResolutionTest {

    private val fireRedSha = "111122223333444455556666777788889999aaaabbbbccccddddeeeeffff0000"

    private val fireRedProfile = RomHackProfile(
        id = "vanilla_firered",
        name = "Pokemon FireRed",
        baseGame = "FireRed",
        gameId = 2,
        headerTitles = listOf("POKEMON FIRE"),
        sha256Hashes = listOf(fireRedSha),
        isVerified = true,
        memoryLayoutVerified = true,
        playerPartyOffset = 0x02024284L,
        enemyPartyOffset = 0x0202402CL
    )

    private fun mon(species: Int): ParsedPokemon = ParsedPokemon(
        isValid = true,
        isEmpty = false,
        pid = 100L + species,
        tid = 1,
        sid = 2,
        nickname = "Mon $species",
        otName = "Ash",
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
        moves = intArrayOf(1, 0, 0, 0),
        pp = intArrayOf(35, 0, 0, 0),
        currentHp = 100,
        maxHp = 100,
        attack = 100,
        defense = 100,
        speed = 100,
        spAttack = 100,
        spDefense = 100,
        statusCondition = 0L
    )

    // --- native tuple decoding -------------------------------------------------------------

    @Test
    fun nullTuple_isUnknownWithNoSlot() {
        val decoded = ActiveEnemyResolution.fromNativeArray(null)
        assertEquals(ActiveEnemyState.UNKNOWN, decoded.state)
        assertNull(decoded.partySlot)
        assertFalse(decoded.hasResolvedSlot)
    }

    @Test
    fun shortTuple_isUnknownWithNoSlot() {
        val decoded = ActiveEnemyResolution.fromNativeArray(intArrayOf(2, 1, 0))
        assertEquals(ActiveEnemyState.UNKNOWN, decoded.state)
        assertNull(decoded.partySlot)
    }

    @Test
    fun unknownCode_neverBecomesSlotZero() {
        val decoded = ActiveEnemyResolution.fromNativeArray(intArrayOf(0, -1, -1, 0, 0))
        assertEquals(ActiveEnemyState.UNKNOWN, decoded.state)
        assertNull("an unknown state must not expose slot 0", decoded.partySlot)
        assertFalse(decoded.hasResolvedSlot)
    }

    /**
     * The native tuple's UNKNOWN state (`ACTIVE_ENEMY_UNKNOWN`, code 0) with slot -1 must survive
     * decoding as UNKNOWN. Collapsing it into NONE_ACTIVE would assert knowledge the reader does
     * not have ("there is definitely no opponent"), and collapsing it into slot 0 would invent one.
     */
    @Test
    fun nativeUnknownTuple_staysUnknownAndIsNotCollapsedIntoNoneActive() {
        val decoded = ActiveEnemyResolution.fromNativeArray(intArrayOf(0, -1, -1, 0, 0))

        assertEquals(ActiveEnemyState.UNKNOWN, decoded.state)
        assertNotEquals(
            "UNKNOWN must not be converted into NONE_ACTIVE",
            ActiveEnemyState.NONE_ACTIVE,
            decoded.state
        )
        assertNull(decoded.partySlot)
        assertNull(decoded.battlerIndex)
        assertEquals(0, decoded.opponentBattlers)
        assertFalse(decoded.hasResolvedSlot)
        assertFalse(decoded.state.isPresentable)

        // The three non-presentable states must remain mutually distinct.
        assertEquals(ActiveEnemyState.NONE_ACTIVE,
            ActiveEnemyResolution.fromNativeArray(intArrayOf(1, -1, -1, 0, 0)).state)
        assertEquals(ActiveEnemyState.AMBIGUOUS,
            ActiveEnemyResolution.fromNativeArray(intArrayOf(3, -1, -1, 2, 0)).state)
    }

    /**
     * An unreadable authoritative gate surfaces as an unknown opponent with no slot and, through
     * the view model, no active enemy index -- never slot 0 and never a fabricated opponent.
     */
    @Test
    fun unreadableGate_keepsUnknownThroughTheViewModel() {
        val coordinator = EnemyResolutionCoordinator(
            resolution = ActiveEnemyResolution(
                state = ActiveEnemyState.UNKNOWN,
                partySlot = null,
                battlerIndex = null,
                opponentBattlers = 0
            ),
            enemies = arrayOf(mon(150), mon(151)),
            inBattle = true
        )
        val vm = verifiedViewModel(coordinator)
        vm.pollTick()
        vm.pollTick()

        assertEquals(ActiveEnemyState.UNKNOWN, vm.activeEnemyResolution.value.state)
        assertNotEquals(
            "the view model must not downgrade UNKNOWN to NONE_ACTIVE",
            ActiveEnemyState.NONE_ACTIVE,
            vm.activeEnemyResolution.value.state
        )
        assertNull(vm.activeEnemyResolution.value.partySlot)
        assertFalse(vm.activeEnemyResolution.value.hasResolvedSlot)
        assertEquals(-1, vm.activeEnemyMemberIndex.value)
    }

    @Test
    fun slotCodeWithOutOfRangeSlot_failsClosedToUnknown() {
        val decoded = ActiveEnemyResolution.fromNativeArray(intArrayOf(2, 1, 9, 1, 0))
        assertEquals(ActiveEnemyState.UNKNOWN, decoded.state)
        assertNull(decoded.partySlot)
    }

    @Test
    fun slotZero_isAPresentableSlot() {
        val decoded = ActiveEnemyResolution.fromNativeArray(intArrayOf(2, 1, 0, 1, 0))
        assertEquals(ActiveEnemyState.SLOT, decoded.state)
        assertEquals(0, decoded.partySlot)
        assertEquals(1, decoded.battlerIndex)
        assertTrue(decoded.hasResolvedSlot)
    }

    @Test
    fun slotTwo_carriesTheEngineSlotAndFaintFlag() {
        val decoded = ActiveEnemyResolution.fromNativeArray(intArrayOf(2, 1, 2, 1, 1))
        assertEquals(ActiveEnemyState.SLOT, decoded.state)
        assertEquals(2, decoded.partySlot)
        assertTrue(decoded.isFainted)
    }

    @Test
    fun noneActiveAndAmbiguous_areDistinctAndCarryNoSlot() {
        val none = ActiveEnemyResolution.fromNativeArray(intArrayOf(1, -1, -1, 0, 0))
        assertEquals(ActiveEnemyState.NONE_ACTIVE, none.state)
        assertNull(none.partySlot)
        assertFalse(none.hasResolvedSlot)

        val ambiguous = ActiveEnemyResolution.fromNativeArray(intArrayOf(3, -1, -1, 2, 0))
        assertEquals(ActiveEnemyState.AMBIGUOUS, ambiguous.state)
        assertEquals(2, ambiguous.opponentBattlers)
        assertNull("a doubles battle must not expose a party slot", ambiguous.partySlot)
        assertFalse(ambiguous.hasResolvedSlot)
    }

    // --- view-model integration -------------------------------------------------------------

    private class EnemyResolutionCoordinator(
        var resolution: ActiveEnemyResolution,
        private val enemies: Array<ParsedPokemon>,
        private val inBattle: Boolean
    ) : LibretroCoreCoordinator() {
        var resolveCalls = 0
        override fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? = arrayOf()
        override fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? = enemies
        override fun readBattlePresence(gameId: Int): Int = if (inBattle) 1 else 0
        override fun resolveActiveEnemy(gameId: Int): ActiveEnemyResolution {
            resolveCalls++
            return resolution
        }
        override fun readPlayerLocation(gameId: Int): PlayerLocation? = null
    }

    private fun verifiedViewModel(coordinator: LibretroCoreCoordinator): CompanionViewModel {
        val vm = CompanionViewModel(coreCoordinator = coordinator)
        vm.setRomSession(
            RomCompatibility.verified(fireRedProfile, fireRedSha),
            RomIdentity(fireRedSha, "Pokemon FireRed")
        )
        return vm
    }

    @Test
    fun unknownResolution_reportsNoActiveEnemyIndex() {
        val coordinator = EnemyResolutionCoordinator(
            resolution = ActiveEnemyResolution(),
            enemies = arrayOf(mon(150)),
            inBattle = true
        )
        val vm = verifiedViewModel(coordinator)
        vm.pollTick()
        vm.pollTick() // presence stabilizer needs two samples to open the battle

        assertTrue(coordinator.resolveCalls > 0)
        assertEquals(-1, vm.activeEnemyMemberIndex.value)
        assertEquals(ActiveEnemyState.UNKNOWN, vm.activeEnemyResolution.value.state)
        assertNull(vm.activeEnemyResolution.value.partySlot)
    }

    @Test
    fun ambiguousResolution_reportsAmbiguityAndNoSlot() {
        val coordinator = EnemyResolutionCoordinator(
            resolution = ActiveEnemyResolution(
                state = ActiveEnemyState.AMBIGUOUS,
                opponentBattlers = 2
            ),
            enemies = arrayOf(mon(150), mon(151)),
            inBattle = true
        )
        val vm = verifiedViewModel(coordinator)
        vm.pollTick()
        vm.pollTick()

        assertEquals(-1, vm.activeEnemyMemberIndex.value)
        assertEquals(ActiveEnemyState.AMBIGUOUS, vm.activeEnemyResolution.value.state)
        assertEquals(2, vm.activeEnemyResolution.value.opponentBattlers)
        assertFalse(vm.activeEnemyResolution.value.hasResolvedSlot)
    }

    @Test
    fun resolvedSlot_isSurfacedWithItsState() {
        val coordinator = EnemyResolutionCoordinator(
            resolution = ActiveEnemyResolution(
                state = ActiveEnemyState.SLOT,
                partySlot = 1,
                battlerIndex = 1,
                opponentBattlers = 1
            ),
            enemies = arrayOf(mon(150), mon(151)),
            inBattle = true
        )
        val vm = verifiedViewModel(coordinator)
        vm.pollTick()
        vm.pollTick()

        assertEquals(1, vm.activeEnemyMemberIndex.value)
        assertEquals(ActiveEnemyState.SLOT, vm.activeEnemyResolution.value.state)
        assertEquals(1, vm.activeEnemyResolution.value.partySlot)
        assertTrue(vm.activeEnemyResolution.value.hasResolvedSlot)
    }

    /**
     * A fainted opponent whose replacement has not been resolved yet must degrade to unknown
     * instead of keeping the fainted Pokémon on screen.
     */
    @Test
    fun faintedOpponentDuringTransition_degradesToUnknownInsteadOfKeepingTheFaintedMon() {
        val coordinator = EnemyResolutionCoordinator(
            resolution = ActiveEnemyResolution(
                state = ActiveEnemyState.SLOT,
                partySlot = 1,
                battlerIndex = 1,
                opponentBattlers = 1,
                isFainted = true
            ),
            enemies = arrayOf(mon(150), mon(151)),
            inBattle = true
        )
        val vm = verifiedViewModel(coordinator)
        vm.pollTick()
        vm.pollTick()

        assertEquals(-1, vm.activeEnemyMemberIndex.value)
        assertEquals(ActiveEnemyState.UNKNOWN, vm.activeEnemyResolution.value.state)
        assertNull(vm.activeEnemyResolution.value.partySlot)
        assertFalse(vm.activeEnemyResolution.value.hasResolvedSlot)
    }

    @Test
    fun battleExit_clearsActiveEnemyResolution() {
        val coordinator = EnemyResolutionCoordinator(
            resolution = ActiveEnemyResolution(
                state = ActiveEnemyState.SLOT,
                partySlot = 1,
                battlerIndex = 1,
                opponentBattlers = 1
            ),
            enemies = arrayOf(mon(150), mon(151)),
            inBattle = true
        )
        val vm = verifiedViewModel(coordinator)
        vm.pollTick()
        vm.pollTick()
        assertEquals(1, vm.activeEnemyMemberIndex.value)

        vm.setIsInBattle(false)
        assertEquals(-1, vm.activeEnemyMemberIndex.value)
        assertEquals(ActiveEnemyState.UNKNOWN, vm.activeEnemyResolution.value.state)
        assertNull(vm.activeEnemyResolution.value.partySlot)
    }

    @Test
    fun trustLoss_clearsActiveEnemyResolution() {
        val coordinator = EnemyResolutionCoordinator(
            resolution = ActiveEnemyResolution(
                state = ActiveEnemyState.SLOT,
                partySlot = 0,
                battlerIndex = 1,
                opponentBattlers = 1
            ),
            enemies = arrayOf(mon(150)),
            inBattle = true
        )
        val vm = verifiedViewModel(coordinator)
        vm.pollTick()
        vm.pollTick()
        assertTrue(vm.activeEnemyResolution.value.hasResolvedSlot)

        // Unverifiable ROM: live reads are no longer permitted and nothing may remain on screen.
        vm.setRomSession(
            RomCompatibility.recognizedUnverified(
                fireRedProfile,
                "other_sha",
                ProfileMatchMethod.HEADER_TITLE,
                "Header matched"
            ),
            RomIdentity("other_sha", "Pokemon FireRed (other revision)")
        )
        vm.pollTick()

        assertFalse(vm.runtimeRomTrust.value.mayReadLiveMemory)
        assertEquals(-1, vm.activeEnemyMemberIndex.value)
        assertNull(vm.activeEnemyResolution.value.partySlot)
        assertFalse(vm.activeEnemyResolution.value.hasResolvedSlot)
    }

    @Test
    fun manualSelection_mapsToAnExplicitSlot() {
        val coordinator = EnemyResolutionCoordinator(
            resolution = ActiveEnemyResolution(),
            enemies = arrayOf(mon(150), mon(151)),
            inBattle = true
        )
        val vm = verifiedViewModel(coordinator)

        vm.setActiveEnemyMemberIndex(2)
        assertEquals(ActiveEnemyState.SLOT, vm.activeEnemyResolution.value.state)
        assertEquals(2, vm.activeEnemyResolution.value.partySlot)

        vm.setActiveEnemyMemberIndex(-1)
        assertEquals(ActiveEnemyState.UNKNOWN, vm.activeEnemyResolution.value.state)
        assertNull(vm.activeEnemyResolution.value.partySlot)
    }
}
