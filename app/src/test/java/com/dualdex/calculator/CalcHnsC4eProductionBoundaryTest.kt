package com.dualdex.calculator

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsBattlerTypeObservation
import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gap C4e: the first production-authorized exact H&S 2.0.5 live Singles request.
 *
 * These tests drive the REAL [CalcRequestBoundary] (not the policy directly). The positive
 * control is a C4d Golden-A-equivalent live state: Chikorita (Overgrow, Grass) using an ordinary
 * Normal EFFECT_HIT move against a Pidgey, with every mutable operand observed from live state.
 * Every adjacent negative removes exactly one authority and must refuse with a precise limitation.
 */
class CalcHnsC4eProductionBoundaryTest {

    private val exactSha = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"

    private fun bundledProfile(id: String): RomHackProfile {
        val dir = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles") }
            .firstOrNull { it.isDirectory }
            ?: throw AssertionError("Unable to locate bundled ROM profiles")
        val file = File(dir, "$id.json")
        assertTrue("bundled profile $id.json is missing", file.isFile)
        return ProfileLoader.parseProfile(file.readText())
    }

    private val heartAndSoul: RomHackProfile get() = bundledProfile("heart_and_soul")

    private fun trustFor(hash: String): RuntimeRomTrust {
        val hashed = heartAndSoul.copy(
            sha256Hashes = listOf(exactSha),
            isVerified = true,
            memoryLayoutVerified = true
        )
        return RuntimeRomTrust.from(
            compatibility = RomCompatibility.verified(hashed, hash),
            activeRomSha256 = hash
        )
    }

    private fun settings(): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txModeFairyTypes = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txRandomType = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomTypeEffectiveness = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomAbilities = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomMoves = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesNoEvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesBaseStatEqualizer = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesMirror = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesMirrorThief = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesTrainerScalingIvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesTrainerScalingEvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesMaxPartyIvs = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txModeSturdy = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txChallengesLevelCap = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txChallengesExpMultiplier = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txModeLegendaryAbilities = HnsChallengeField(observed = true, raw = 1, outOfDomain = false)
    )

    /**
     * C4d Golden-A-equivalent live state, with individual authorities toggled for the negative
     * cases. Defaults are the observed neutral values used by the first production subset.
     */
    private fun playerObservation(
        partySlot: Int = 0,
        abilityId: Int = 65,
        abilityName: String = "Overgrow",
        types: List<Int> = listOf(13), // Grass (Chikorita; pinned enum Type GRASS = 13)
        hpObserved: Boolean = true,
        hp: Int = 14,
        maxHp: Int = 20,
        status1: Int = 0,
        statusObserved: Boolean = true,
        volatilesObserved: Boolean = true,
        electrified: Boolean = false,
        glaiveRush: Boolean = false,
        gimmickObserved: Boolean = true,
        gimmick: Int = 0,
        fieldStatusesReadable: Boolean = true,
        fieldStatuses: Int = 0,
        badgesObserved: Boolean = true
    ): BattlerRuntimeObservation = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = HnsBattlerRuntimeStatus.OBSERVED,
            battlerIndex = 0,
            partySlot = partySlot,
            abilityId = abilityId,
            abilityOutOfDomain = false,
            types = types.map { HnsBattlerTypeObservation(observed = true, raw = it, outOfDomain = false) },
            itemId = 0,
            itemOutOfDomain = false,
            statsObserved = true,
            rawAttack = 12,
            rawDefense = 12,
            rawSpeed = 8,
            rawSpAttack = 11,
            rawSpDefense = 11,
            stagesObserved = true,
            statStages = listOf(0, 0, 0, 0, 0, 0, 0, 0),
            badgesObserved = badgesObserved,
            badgeBoostAtk = false,
            badgeBoostDef = false,
            badgeBoostSpe = false,
            badgeBoostSpa = false,
            badgeBoostSpd = false,
            rawBadgesByte = 0,
            absentBattlerFlags = 0,
            absentFlagsReadable = true,
            battlersCount = 2,
            battlersCountReadable = true,
            hpObserved = hpObserved,
            hp = hp,
            maxHp = maxHp,
            statusObserved = statusObserved,
            status1 = status1,
            volatilesObserved = volatilesObserved,
            volatileElectrified = electrified,
            volatileGlaiveRush = glaiveRush,
            volatileMinimize = false,
            volatileSemiInvulnerable = 0,
            gimmickObserved = gimmickObserved,
            activeGimmick = gimmick,
            fieldStatusesReadable = fieldStatusesReadable,
            fieldStatuses = fieldStatuses
        ),
        abilityIdentity = DeclaredAbility.Declared(abilityId, abilityName)
    )

    private fun enemyObservation(
        partySlot: Int = 0,
        abilityId: Int = 77,
        abilityName: String = "Tangled Feet",
        types: List<Int> = listOf(1, 3), // Normal, Flying (Pidgey)
        volatilesObserved: Boolean = true,
        glaiveRush: Boolean = false,
        gimmickObserved: Boolean = true,
        gimmick: Int = 0,
        fieldStatusesReadable: Boolean = true,
        fieldStatuses: Int = 0,
        statusObserved: Boolean = true,
        status1: Int = 0
    ): BattlerRuntimeObservation = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = HnsBattlerRuntimeStatus.OBSERVED,
            battlerIndex = 1,
            partySlot = partySlot,
            abilityId = abilityId,
            abilityOutOfDomain = false,
            types = types.map { HnsBattlerTypeObservation(observed = true, raw = it, outOfDomain = false) },
            itemId = 0,
            itemOutOfDomain = false,
            statsObserved = true,
            rawAttack = 8,
            rawDefense = 7,
            rawSpeed = 9,
            rawSpAttack = 7,
            rawSpDefense = 7,
            stagesObserved = true,
            statStages = listOf(0, 0, 0, 0, 0, 0, 0, 0),
            badgesObserved = false,
            absentBattlerFlags = 0,
            absentFlagsReadable = true,
            battlersCount = 2,
            battlersCountReadable = true,
            hpObserved = true,
            hp = 15,
            maxHp = 15,
            statusObserved = statusObserved,
            status1 = status1,
            volatilesObserved = volatilesObserved,
            volatileElectrified = false,
            volatileGlaiveRush = glaiveRush,
            volatileMinimize = false,
            volatileSemiInvulnerable = 0,
            gimmickObserved = gimmickObserved,
            activeGimmick = gimmick,
            fieldStatusesReadable = fieldStatusesReadable,
            fieldStatuses = fieldStatuses
        ),
        abilityIdentity = DeclaredAbility.Declared(abilityId, abilityName)
    )

    private fun liveInput(
        species: String,
        level: Int,
        abilityId: Int,
        ability: String,
        partySlot: Int = 0
    ) = CalcPokemonInput(
        species = species,
        level = level,
        ability = ability,
        abilityId = abilityId,
        origin = CalcInputOrigin.LIVE_READ,
        partySlot = partySlot
    )

    private fun goldenARequest(move: String = "Tackle") = DamageCalculationRequest(
        gen = 3,
        typeSystem = "hns_2_0_5",
        attacker = liveInput("Chikorita", 5, 65, "Overgrow"),
        defender = liveInput("Pidgey", 3, 77, "Tangled Feet"),
        move = CalcMoveInput(name = move)
    )

    private fun build(
        trust: RuntimeRomTrust,
        request: DamageCalculationRequest,
        player: BattlerRuntimeObservation?,
        enemy: BattlerRuntimeObservation?,
        activeBattle: Boolean = true
    ): CalcRequestOutcome = CalcRequestBoundary.build(
        profile = heartAndSoul,
        trust = trust,
        request = request,
        challengeSettings = settings(),
        playerBattlerState = player,
        enemyBattlerState = enemy,
        activeBattle = activeBattle
    )

    // ---------------------------------------------------------------- positive control

    @Test
    fun `exact trusted live Singles ordinary request reaches Ready with ESTIMATED`() {
        val trust = trustFor(exactSha)
        val outcome = build(
            trust = trust,
            request = goldenARequest(),
            player = playerObservation(),
            enemy = enemyObservation()
        )

        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("the fully observed Golden-A-equivalent request must be Ready, got $outcome")
        assertNotNull("a Ready outcome must expose the executable request", ready.request)
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertFalse("H&S may never be presented as verified", ready.verdict.isVerified)

        // The bound request carries the engine's live HP/maxHP and the exact authoritative
        // ability, and the emitted JSON sends those live operands rather than a party guess.
        val live = ready.request.hnsLiveBattleState
        assertNotNull(live)
        assertEquals(14, live!!.attackerHp)
        assertEquals(20, live.attackerMaxHp)
        assertEquals(0, live.attackerStatus1)
        assertEquals(false, live.attackerElectrified)
        assertEquals(false, live.defenderGlaiveRush)
        assertEquals(0, live.attackerGimmick)
        assertEquals(0, live.defenderGimmick)
        assertEquals("Overgrow", ready.request.attacker.ability)
        assertEquals(65, ready.request.attacker.abilityId)

        val json = buildCalcRequestJson(ready.request)
        assertTrue("live HP must reach the engine: $json", json.contains("\"hp\":14"))
        assertTrue("live maxHP must reach the engine: $json", json.contains("\"maxHP\":20"))
        assertTrue("the effective move override must be the pinned Tackle: $json", json.contains("\"basePower\":40"))
    }

    @Test
    fun `pinch ability inactive but relevant uses authoritative live HP`() {
        // Overgrow + Razor Leaf (Grass) at 11/23: relevant type, condition authoritatively inactive.
        val trust = trustFor(exactSha)
        val request = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = liveInput("Chikorita", 6, 65, "Overgrow"),
            defender = liveInput("Pidgey", 3, 77, "Tangled Feet"),
            move = CalcMoveInput(name = "Razor Leaf")
        )
        val outcome = build(
            trust = trust,
            request = request,
            player = playerObservation(hp = 11, maxHp = 23),
            enemy = enemyObservation()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("inactive-but-relevant Overgrow must be calculable, got $outcome")
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
    }

    // ---------------------------------------------------------------- adjacent negatives

    private fun refusedWith(
        expected: CalcLimitation,
        trust: RuntimeRomTrust = trustFor(exactSha),
        request: DamageCalculationRequest = goldenARequest(),
        player: BattlerRuntimeObservation? = playerObservation(),
        enemy: BattlerRuntimeObservation? = enemyObservation()
    ) {
        val outcome = build(trust, request, player, enemy)
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("expected refusal for $expected, got $outcome")
        assertNull("a refusal must never expose a request", refused.verdict.request)
        assertTrue(
            "expected $expected in ${refused.verdict.limitations}",
            refused.verdict.limitations.contains(expected)
        )
    }

    @Test
    fun `wrong ROM hash is refused`() {
        refusedWith(
            expected = CalcLimitation.LIVE_INPUTS_NOT_VERIFIED,
            trust = trustFor("0".repeat(64))
        )
    }

    @Test
    fun `unreadable attacker volatile is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED,
            player = playerObservation(volatilesObserved = false)
        )
    }

    @Test
    fun `unreadable field status is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED,
            player = playerObservation(fieldStatusesReadable = false),
            enemy = enemyObservation(fieldStatusesReadable = false)
        )
    }

    @Test
    fun `unreadable gimmick state is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_GIMMICK_STATE_UNREADABLE,
            player = playerObservation(gimmickObserved = false),
            enemy = enemyObservation(gimmickObserved = false)
        )
    }

    @Test
    fun `active gimmick is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_GIMMICK_ACTIVE_NOT_MODELLED,
            player = playerObservation(gimmick = 5) // GIMMICK_TERA
        )
    }

    @Test
    fun `active electrified volatile is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED,
            player = playerObservation(electrified = true)
        )
    }

    @Test
    fun `active ion deluge against a Normal move is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED,
            player = playerObservation(fieldStatuses = 1 shl 10),
            enemy = enemyObservation(fieldStatuses = 1 shl 10)
        )
    }

    @Test
    fun `active Glaive Rush volatile is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_GLAIVE_RUSH_ACTIVE_NOT_MODELLED,
            enemy = enemyObservation(glaiveRush = true)
        )
    }

    @Test
    fun `unsupported ability is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED,
            player = playerObservation(abilityId = 91, abilityName = "Adaptability")
        )
    }

    @Test
    fun `unverified pinch HP is refused when the condition is relevant`() {
        // Overgrow + Razor Leaf (relevant), but the live HP was not read: the condition cannot be
        // assumed inactive, so the request must fail closed.
        val request = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = liveInput("Chikorita", 6, 65, "Overgrow"),
            defender = liveInput("Pidgey", 3, 77, "Tangled Feet"),
            move = CalcMoveInput(name = "Razor Leaf")
        )
        refusedWith(
            expected = CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED,
            request = request,
            player = playerObservation(hpObserved = false)
        )
    }

    @Test
    fun `stale participant slot is refused`() {
        refusedWith(
            expected = CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN,
            player = playerObservation(partySlot = 3)
        )
    }

    @Test
    fun `unobserved badge state is refused`() {
        refusedWith(
            expected = CalcLimitation.BADGE_BOOST_NOT_MODELLED,
            player = playerObservation(badgesObserved = false)
        )
    }

    @Test
    fun `unsupported move is refused`() {
        // Explosion is EFFECT_HIT but carries the `explosion` damage flag the generator excludes
        // from the ordinary set (H&S keeps B_EXPLOSION_DEFENSE at GEN_LATEST while ADV halves
        // Defense), so it must fail closed rather than be computed through the ADV pipeline.
        refusedWith(
            expected = CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED,
            request = goldenARequest(move = "Explosion")
        )
    }

    @Test
    fun `active live status is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED,
            player = playerObservation(status1 = 0x10) // poison
        )
    }

    // ---------------------------------------------------------------- anti-spoofing

    @Test
    fun `caller-crafted live state cannot authorize a request whose runtime gimmick is unread`() {
        // The caller supplies a fully favourable live state (neutral electrified/Glaive
        // Rush/gimmick/HP). The boundary must strip it and rebind from the runtime observations,
        // which do not carry the gimmick, so the craft cannot clear the gimmick gate.
        val trust = trustFor(exactSha)
        val crafted = goldenARequest().copy(
            hnsLiveBattleState = CalcHnsLiveBattleState(
                dynamicMoveTypeObserved = true,
                transientStateObserved = true,
                attackerElectrified = false,
                fieldStatuses = 0,
                defenderGlaiveRush = false,
                attackerGimmick = 0,
                defenderGimmick = 0,
                attackerHp = 1,
                attackerMaxHp = 1,
                attackerStatus1 = 0
            )
        )
        refusedWith(
            expected = CalcLimitation.HNS_GIMMICK_STATE_UNREADABLE,
            request = crafted,
            player = playerObservation(gimmickObserved = false),
            enemy = enemyObservation(gimmickObserved = false)
        )
    }

    @Test
    fun `caller-crafted HP cannot spoof the pinch condition`() {
        // The caller writes curHP = 1 (which would look like an active pinch), but the engine's
        // live HP is 14/20 (inactive). The JSON the engine receives must carry the live value.
        val trust = trustFor(exactSha)
        val request = goldenARequest().copy(
            attacker = goldenARequest().attacker.copy(curHP = 1)
        )
        val outcome = build(
            trust = trust,
            request = request,
            player = playerObservation(hp = 14, maxHp = 20),
            enemy = enemyObservation()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("the live HP must be bound, got $outcome")
        val json = buildCalcRequestJson(ready.request)
        assertTrue("the live HP must be sent: $json", json.contains("\"hp\":14"))
        assertFalse("the caller's crafted HP must not be sent: $json", json.contains("\"hp\":1,"))
        // curHP is likewise rebound to the live value.
        assertEquals(14, ready.request.attacker.curHP)
    }

    @Test
    fun `caller-spoofed ability is overridden by the authoritative runtime ID`() {
        // The caller claims Overgrow, but the engine reports Adaptability (91, unsupported).
        val trust = trustFor(exactSha)
        val spoofed = goldenARequest().copy(
            attacker = liveInput("Chikorita", 5, 0, "Overgrow")
        )
        refusedWith(
            expected = CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED,
            request = spoofed,
            player = playerObservation(abilityId = 91, abilityName = "Adaptability")
        )
    }
}
