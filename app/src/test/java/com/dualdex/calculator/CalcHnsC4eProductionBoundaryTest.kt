package com.dualdex.calculator

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsBattlerTypeObservation
import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import com.dualdex.pokemon.hns.HnsChallengeSettingsStatus
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
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

    private fun settings(randomAbilities: Boolean = false, optionStyle: Int = 0): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = HnsChallengeField(observed = true, raw = optionStyle, outOfDomain = false),
        txModeFairyTypes = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txRandomType = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomTypeEffectiveness = HnsChallengeField(observed = true, raw = 0, outOfDomain = false),
        txRandomAbilities = HnsChallengeField(observed = true, raw = if (randomAbilities) 1 else 0, outOfDomain = false),
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
        speciesId: Int? = 152, // Chikorita; live BattlePokemon species/form identity
        abilityId: Int = 65,
        abilityName: String = "Overgrow",
        types: List<Int> = listOf(13), // Grass (Chikorita; pinned enum Type GRASS = 13)
        hpObserved: Boolean = true,
        hp: Int = 14,
        maxHp: Int = 20,
        status1: Int = 0,
        statusObserved: Boolean = true,
        volatilesObserved: Boolean = true,
        transientVolatilesObserved: Boolean = volatilesObserved,
        electrified: Boolean = false,
        glaiveRush: Boolean = false,
        chargeTimer: Int = 0,
        tarShot: Boolean = false,
        persistentVolatilesObserved: Boolean = volatilesObserved,
        foresight: Boolean = false,
        miracleEye: Boolean = false,
        root: Boolean = false,
        smackDown: Boolean = false,
        telekinesis: Boolean = false,
        magnetRise: Boolean = false,
        gastroAcid: Boolean = false,
        roostActive: Boolean = false,
        substitute: Boolean = false,
        endured: Boolean = false,
        gimmickObserved: Boolean = true,
        gimmick: Int = 0,
        fieldStatusesReadable: Boolean = true,
        fieldStatuses: Int = 0,
        weatherReadable: Boolean = true,
        battleWeather: Int = 0,
        sideStatusesReadable: Boolean = true,
        sideStatuses: Int = 0,
        badgesObserved: Boolean = true,
        observedBattlersCount: Int? = 2,
        itemId: Int? = 0,
        absentBattlerFlags: Int = 0
    ): BattlerRuntimeObservation = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = HnsBattlerRuntimeStatus.OBSERVED,
            battlerIndex = 0,
            partySlot = partySlot,
            speciesId = speciesId,
            abilityId = abilityId,
            abilityOutOfDomain = false,
            types = types.map { HnsBattlerTypeObservation(observed = true, raw = it, outOfDomain = false) },
            itemId = itemId,
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
            absentBattlerFlags = absentBattlerFlags,
            absentFlagsReadable = true,
            battlersCount = observedBattlersCount ?: 0,
            battlersCountReadable = observedBattlersCount != null,
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
            transientVolatilesObserved = transientVolatilesObserved,
            volatileChargeTimer = chargeTimer,
            volatileTarShot = tarShot,
            persistentVolatilesObserved = persistentVolatilesObserved,
            volatileForesight = foresight,
            volatileMiracleEye = miracleEye,
            volatileRoot = root,
            volatileSmackDown = smackDown,
            volatileTelekinesis = telekinesis,
            volatileMagnetRise = magnetRise,
            volatileGastroAcid = gastroAcid,
            volatileRoostActive = roostActive,
            volatileSubstitute = substitute,
            volatileEndured = endured,
            gimmickObserved = gimmickObserved,
            activeGimmick = gimmick,
            fieldStatusesReadable = fieldStatusesReadable,
            fieldStatuses = fieldStatuses,
            weatherReadable = weatherReadable,
            battleWeather = battleWeather,
            sideStatusesReadable = sideStatusesReadable,
            sideStatuses = sideStatuses
        ),
        abilityIdentity = DeclaredAbility.Declared(abilityId, abilityName)
    )

    private fun enemyObservation(
        partySlot: Int = 0,
        speciesId: Int? = 16, // Pidgey; live BattlePokemon species/form identity
        abilityId: Int = 77,
        abilityName: String = "Tangled Feet",
        types: List<Int> = listOf(1, 3), // Normal, Flying (Pidgey)
        hpObserved: Boolean = true,
        hp: Int = 15,
        maxHp: Int = 15,
        volatilesObserved: Boolean = true,
        transientVolatilesObserved: Boolean = volatilesObserved,
        glaiveRush: Boolean = false,
        tarShot: Boolean = false,
        persistentVolatilesObserved: Boolean = volatilesObserved,
        foresight: Boolean = false,
        miracleEye: Boolean = false,
        root: Boolean = false,
        smackDown: Boolean = false,
        telekinesis: Boolean = false,
        magnetRise: Boolean = false,
        gastroAcid: Boolean = false,
        roostActive: Boolean = false,
        substitute: Boolean = false,
        endured: Boolean = false,
        gimmickObserved: Boolean = true,
        gimmick: Int = 0,
        fieldStatusesReadable: Boolean = true,
        fieldStatuses: Int = 0,
        weatherReadable: Boolean = true,
        battleWeather: Int = 0,
        sideStatusesReadable: Boolean = true,
        sideStatuses: Int = 0,
        statusObserved: Boolean = true,
        status1: Int = 0,
        observedBattlersCount: Int? = 2,
        itemId: Int? = 0,
        absentBattlerFlags: Int = 0
    ): BattlerRuntimeObservation = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = HnsBattlerRuntimeStatus.OBSERVED,
            battlerIndex = 1,
            partySlot = partySlot,
            speciesId = speciesId,
            abilityId = abilityId,
            abilityOutOfDomain = false,
            types = types.map { HnsBattlerTypeObservation(observed = true, raw = it, outOfDomain = false) },
            itemId = itemId,
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
            absentBattlerFlags = absentBattlerFlags,
            absentFlagsReadable = true,
            battlersCount = observedBattlersCount ?: 0,
            battlersCountReadable = observedBattlersCount != null,
            hpObserved = hpObserved,
            hp = hp,
            maxHp = maxHp,
            statusObserved = statusObserved,
            status1 = status1,
            volatilesObserved = volatilesObserved,
            volatileElectrified = false,
            volatileGlaiveRush = glaiveRush,
            volatileMinimize = false,
            volatileSemiInvulnerable = 0,
            transientVolatilesObserved = transientVolatilesObserved,
            volatileChargeTimer = 0,
            volatileTarShot = tarShot,
            persistentVolatilesObserved = persistentVolatilesObserved,
            volatileForesight = foresight,
            volatileMiracleEye = miracleEye,
            volatileRoot = root,
            volatileSmackDown = smackDown,
            volatileTelekinesis = telekinesis,
            volatileMagnetRise = magnetRise,
            volatileGastroAcid = gastroAcid,
            volatileRoostActive = roostActive,
            volatileSubstitute = substitute,
            volatileEndured = endured,
            gimmickObserved = gimmickObserved,
            activeGimmick = gimmick,
            fieldStatusesReadable = fieldStatusesReadable,
            fieldStatuses = fieldStatuses,
            weatherReadable = weatherReadable,
            battleWeather = battleWeather,
            sideStatusesReadable = sideStatusesReadable,
            sideStatuses = sideStatuses
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

    /**
     * The Golden-A attacker with a chosen defender species and move, for the round-4 type-immunity
     * matchups (the observed types still have to match the pinned static record).
     */
    private fun matchupRequest(move: String, defenderSpecies: String) = DamageCalculationRequest(
        gen = 3,
        typeSystem = "hns_2_0_5",
        attacker = liveInput("Chikorita", 5, 65, "Overgrow"),
        defender = liveInput(defenderSpecies, 3, 77, "Tangled Feet"),
        move = CalcMoveInput(name = move)
    )

    private fun build(
        trust: RuntimeRomTrust,
        request: DamageCalculationRequest,
        player: BattlerRuntimeObservation?,
        enemy: BattlerRuntimeObservation?,
        activeBattle: Boolean = true,
        randomAbilities: Boolean = false,
        optionStyle: Int = 0
    ): CalcRequestOutcome = CalcRequestBoundary.build(
        profile = heartAndSoul,
        trust = trust,
        request = request,
        challengeSettings = settings(randomAbilities, optionStyle),
        playerBattlerState = player,
        enemyBattlerState = enemy,
        activeBattle = activeBattle
    )

    // ---------------------------------------------------------------- positive control

    @Test
    fun `Random Abilities verdict follows the observed effective ID rather than species or caller`() {
        val trust = trustFor(exactSha)
        val claimedDefaults = goldenARequest() // Chikorita Overgrow, Pidgey Tangled Feet
        val harmless = build(trust, claimedDefaults,
            playerObservation(abilityId = 9, abilityName = "Static"), enemyObservation(),
            randomAbilities = true) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("observed Static must permit an ordinary request")
        assertEquals(9, harmless.request.attacker.abilityId)
        assertEquals("Static", harmless.request.attacker.ability)

        val harmful = build(trust, claimedDefaults,
            playerObservation(abilityId = 62, abilityName = "Guts", status1 = 0x10), enemyObservation(),
            randomAbilities = true) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("observed Guts must refuse despite caller Overgrow")
        assertTrue(harmful.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))

        val defenderHarmful = build(trust, goldenARequest(move = "Ember"),
            playerObservation(abilityId = 9, abilityName = "Static"),
            enemyObservation(abilityId = 47, abilityName = "Thick Fat"), randomAbilities = true)
        assertTrue(defenderHarmful is CalcRequestOutcome.Refused)
    }

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
        // The persistent volatile window is observed and neutral on both battlers (review round 4).
        assertEquals(true, live.attackerPersistentVolatiles?.observed)
        assertEquals(true, live.defenderPersistentVolatiles?.observed)
        assertEquals(false, live.attackerPersistentVolatiles?.anyActive)
        assertEquals(false, live.defenderPersistentVolatiles?.anyActive)
        // The live field conditions are observed neutral and the request's field carries exactly
        // that observed neutral state (not a caller default that merely looks neutral).
        assertTrue(live.weatherObserved)
        assertEquals(0, live.weatherWord)
        assertTrue(live.defenderScreensObserved)
        assertEquals(0, live.defenderSideStatuses)
        assertNull("observed clear weather must bind to no weather", ready.request.field.weather)
        assertNull("observed no screens must bind to no defender side", ready.request.field.defenderSide)
        assertEquals("Overgrow", ready.request.attacker.ability)
        assertEquals(65, ready.request.attacker.abilityId)

        val json = buildCalcRequestJson(ready.request)
        assertTrue("live HP must reach the engine: $json", json.contains("\"hp\":14"))
        assertTrue("live maxHP must reach the engine: $json", json.contains("\"maxHP\":20"))
        assertTrue("the effective move override must be the pinned Tackle: $json", json.contains("\"basePower\":40"))
        assertFalse("observed clear weather must not put a weather key in the engine JSON: $json", json.contains("\"weather\""))
        assertFalse("observed no screens must not put a defenderSide key in the engine JSON: $json", json.contains("\"defenderSide\""))
    }

    // ------------------------------------------------- live battle format (review round 5)

    /**
     * The live battle format is boundary-owned. The native observation carries the real
     * topology (`gBattlersCount`: 2 Singles / 4 Doubles); the caller/UI `field.gameType` label
     * must agree with it or the whole live calculation refuses, because H&S selects different
     * arithmetic by format (screens x0.5 vs x0.667, spread reduction, partner-dependent
     * branches). These tests drive the REAL boundary, including the late-Doubles shape where
     * both per-side observations resolve (one present battler each) while `gBattlersCount`
     * stays 4.
     */
    @Test
    fun `observed Singles topology keeps the Ready positive control Ready and binds the count`() {
        val trust = trustFor(exactSha)
        val outcome = build(
            trust = trust,
            request = goldenARequest(),
            player = playerObservation(observedBattlersCount = 2),
            enemy = enemyObservation(observedBattlersCount = 2)
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("observed Singles count 2 must stay Ready, got $outcome")
        assertEquals(
            "the observed topology must be bound on the ready request",
            2, ready.request.hnsLiveBattleState?.observedBattlersCount
        )
    }

    @Test
    fun `observed four-battler Doubles topology with a caller Singles label is refused as wrong format`() {
        // Late-Doubles: both per-side observations resolve (one present battler each) while
        // gBattlersCount stays 4. A Singles label must not compute it with the Singles x0.5
        // screen multiplier; the entire calculation is under the wrong format.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED,
            player = playerObservation(observedBattlersCount = 4),
            enemy = enemyObservation(observedBattlersCount = 4)
        )
    }

    @Test
    fun `observed four-battler Doubles topology with Reflect and a caller Singles label is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED,
            player = playerObservation(observedBattlersCount = 4),
            enemy = enemyObservation(observedBattlersCount = 4, sideStatuses = 1 shl 0)
        )
    }

    @Test
    fun `player and enemy topology disagreement is refused`() {
        // A torn read: the player observation saw 4, the enemy observation saw 2. Neither side
        // can be picked; the format is unobserved and the request fails closed.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED,
            player = playerObservation(observedBattlersCount = 4),
            enemy = enemyObservation(observedBattlersCount = 2)
        )
    }

    @Test
    fun `unreadable battle topology is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED,
            player = playerObservation(observedBattlersCount = null),
            enemy = enemyObservation(observedBattlersCount = null)
        )
    }

    @Test
    fun `topology unreadable on one side alone is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED,
            player = playerObservation(observedBattlersCount = 2),
            enemy = enemyObservation(observedBattlersCount = null)
        )
    }

    @Test
    fun `caller-crafted Singles cannot override an observed four-battler topology`() {
        // The caller asserts Singles and crafts a favourable live count of 2. The boundary owns
        // hnsLiveBattleState and strips the crafted value, so the runtime count 4 still refuses.
        val trust = trustFor(exactSha)
        val crafted = goldenARequest().copy(
            field = CalcFieldInput(gameType = CalcGameTypes.SINGLES),
            hnsLiveBattleState = CalcHnsLiveBattleState(
                observedBattlersCount = 2,
                moveTargetCount = 2
            )
        )
        val outcome = build(
            trust = trust,
            request = crafted,
            player = playerObservation(observedBattlersCount = 4),
            enemy = enemyObservation(observedBattlersCount = 4)
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError(
                "a caller-crafted Singles label/count must not override the runtime topology, got $outcome"
            )
        assertTrue(
            "the runtime count 4 must win over the crafted Singles state: ${refused.verdict.limitations}",
            refused.verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED)
        )
    }

    @Test
    fun `caller-crafted Doubles against an observed Singles topology fails closed`() {
        // The caller asserts Doubles while the runtime topology is Singles. The label must not
        // redefine reality: the request fails closed instead of computing a Doubles spread.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED,
            request = goldenARequest().copy(field = CalcFieldInput(gameType = CalcGameTypes.DOUBLES)),
            player = playerObservation(observedBattlersCount = 2),
            enemy = enemyObservation(observedBattlersCount = 2)
        )
    }

    @Test
    fun `observed four-battler Doubles with late-Doubles absent flags and TARGET_BOTH move is refused as wrong format`() {
        // Decisive regression: battlers count is 4, but partner battlers are absent (0b1100),
        // so authoritativeMoveTargetCount resolves count = 1 for a TARGET_BOTH ordinary move
        // (Razor Leaf). The Doubles target-count gate clears, but the format gate must refuse:
        // C4e production authorization is Singles-only and does not observe Doubles-only live
        // operands (e.g. Helping Hand).
        val trust = trustFor(exactSha)
        val request = goldenARequest(move = "Razor Leaf").copy(
            field = CalcFieldInput(gameType = CalcGameTypes.DOUBLES)
        )
        val outcome = build(
            trust = trust,
            request = request,
            player = playerObservation(
                observedBattlersCount = 4,
                absentBattlerFlags = 0b1100
            ),
            enemy = enemyObservation(
                observedBattlersCount = 4,
                absentBattlerFlags = 0b1100
            )
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("late-Doubles with target count 1 must never reach Ready, got $outcome")
        assertNull("a refusal must never expose a request", refused.verdict.request)
        assertTrue(
            "late-Doubles must be refused by HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED: ${refused.verdict.limitations}",
            refused.verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED)
        )
        assertFalse(
            "target count was authorized (= 1), so HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED should not block: ${refused.verdict.limitations}",
            refused.verdict.limitations.contains(CalcLimitation.HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED)
        )
    }

    @Test
    fun `observed four-battler Doubles with full presence and TARGET_BOTH move is refused as wrong format`() {
        // Full four-battler Doubles: absentBattlerFlags = 0, so authoritativeMoveTargetCount
        // resolves count = 2 for TARGET_BOTH (Razor Leaf). Even with an authoritative target
        // count of 2, all live Doubles remain outside C4e and refuse with HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED.
        val trust = trustFor(exactSha)
        val request = goldenARequest(move = "Razor Leaf").copy(
            field = CalcFieldInput(gameType = CalcGameTypes.DOUBLES)
        )
        val outcome = build(
            trust = trust,
            request = request,
            player = playerObservation(
                observedBattlersCount = 4,
                absentBattlerFlags = 0
            ),
            enemy = enemyObservation(
                observedBattlersCount = 4,
                absentBattlerFlags = 0
            )
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("Doubles with target count 2 must never reach Ready, got $outcome")
        assertNull("a refusal must never expose a request", refused.verdict.request)
        assertTrue(
            "Doubles must be refused by HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED: ${refused.verdict.limitations}",
            refused.verdict.limitations.contains(CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED)
        )
        assertFalse(
            "target count was authorized (= 2), so HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED should not block: ${refused.verdict.limitations}",
            refused.verdict.limitations.contains(CalcLimitation.HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED)
        )
    }

    // ------------------------------------------------- live field conditions (weather / screens)

    @Test
    fun `live weather unknown refuses - clear cannot be assumed`() {
        // The reader could not deliver gBattleWeather: a live Rain battle must not compute as
        // clear, so the request fails closed with the precise weather-limit limitation.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_WEATHER_UNKNOWN,
            player = playerObservation(weatherReadable = false),
            enemy = enemyObservation(weatherReadable = false)
        )
    }

    @Test
    fun `live screens unknown refuses - screenless cannot be assumed`() {
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_SCREENS_UNKNOWN,
            player = playerObservation(sideStatusesReadable = false),
            enemy = enemyObservation(sideStatusesReadable = false)
        )
    }

    @Test
    fun `live weather that disagrees between the two observations refuses`() {
        // Weather is battle-global: a torn/one-sided read is not authoritative.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_WEATHER_UNKNOWN,
            player = playerObservation(battleWeather = 1 shl 0),
            enemy = enemyObservation(battleWeather = 1 shl 3)
        )
    }

    @Test
    fun `observed Rain is bound into the request`() {
        val trust = trustFor(exactSha)
        val rain = 1 shl 0
        val outcome = build(
            trust = trust,
            request = goldenARequest(),
            player = playerObservation(battleWeather = rain),
            enemy = enemyObservation(battleWeather = rain)
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("observed Rain must be calculable, got $outcome")
        assertEquals("Rain", ready.request.field.weather)
        assertEquals(rain, ready.request.hnsLiveBattleState?.weatherWord)
        val json = buildCalcRequestJson(ready.request)
        assertTrue("observed Rain must reach the engine: $json", json.contains("\"weather\":\"Rain\""))
    }

    @Test
    fun `observed defender Reflect is bound into the request`() {
        val trust = trustFor(exactSha)
        val reflect = 1 shl 0
        val outcome = build(
            trust = trust,
            request = goldenARequest(),
            player = playerObservation(),
            enemy = enemyObservation(sideStatuses = reflect)
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("observed defender Reflect must be calculable, got $outcome")
        assertEquals(true, ready.request.field.defenderSide?.isReflect)
        assertEquals(false, ready.request.field.defenderSide?.isLightScreen)
        val json = buildCalcRequestJson(ready.request)
        assertTrue("observed Reflect must reach the engine: $json", json.contains("\"isReflect\":true"))
        assertFalse("only Reflect was observed: $json", json.contains("\"isLightScreen\""))
    }

    @Test
    fun `observed defender Light Screen is bound into the request`() {
        val trust = trustFor(exactSha)
        val lightScreen = 1 shl 1
        val outcome = build(
            trust = trust,
            request = goldenARequest(),
            player = playerObservation(),
            enemy = enemyObservation(sideStatuses = lightScreen)
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("observed defender Light Screen must be calculable, got $outcome")
        assertEquals(false, ready.request.field.defenderSide?.isReflect)
        assertEquals(true, ready.request.field.defenderSide?.isLightScreen)
        val json = buildCalcRequestJson(ready.request)
        assertTrue("observed Light Screen must reach the engine: $json", json.contains("\"isLightScreen\":true"))
    }

    @Test
    fun `observed unmodelled weather refuses`() {
        // Sandstorm is observed, but the ordinary arithmetic models only Rain/Sun.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_WEATHER_NOT_MODELLED,
            player = playerObservation(battleWeather = 1 shl 5),
            enemy = enemyObservation(battleWeather = 1 shl 5)
        )
    }

    @Test
    fun `observed primal rain refuses instead of collapsing to ordinary Rain`() {
        // B_WEATHER_RAIN = 0x7 includes the Primal (Primordial Sea) bit 1. The engine blocks
        // Water moves under it, so it must never be treated as the ordinary Rain modifier.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_WEATHER_NOT_MODELLED,
            player = playerObservation(battleWeather = 1 shl 1),
            enemy = enemyObservation(battleWeather = 1 shl 1)
        )
    }

    @Test
    fun `observed primal sun refuses instead of collapsing to ordinary Sun`() {
        // B_WEATHER_SUN = 0x18 includes the Primal (Desolate Land) bit 4. The engine blocks Fire
        // moves under it, so it must never be treated as the ordinary Sun modifier.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_WEATHER_NOT_MODELLED,
            player = playerObservation(battleWeather = 1 shl 4),
            enemy = enemyObservation(battleWeather = 1 shl 4)
        )
    }

    @Test
    fun `ordinary rain combined with a primal bit still refuses`() {
        // The ordinary bit must not launder the primal bit past the mask.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_WEATHER_NOT_MODELLED,
            player = playerObservation(battleWeather = (1 shl 0) or (1 shl 1)),
            enemy = enemyObservation(battleWeather = (1 shl 0) or (1 shl 1))
        )
    }

    @Test
    fun `observed unmodelled defender side status refuses`() {
        // Aurora Veil (bit 5) halves damage and is not modelled by this subset.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_SIDE_STATUS_NOT_MODELLED,
            player = playerObservation(),
            enemy = enemyObservation(sideStatuses = 1 shl 5)
        )
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
    fun `active Wonder Room field status is refused`() {
        // STATUS_FIELD_WONDER_ROOM (bit 2) swaps Defense/Sp.Def inside CalcDefenseStat. It must
        // fail closed instead of clearing the Ion Deluge check and using the unswapped stat.
        refusedWith(
            expected = CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED,
            player = playerObservation(fieldStatuses = 1 shl 2),
            enemy = enemyObservation(fieldStatuses = 1 shl 2)
        )
    }

    @Test
    fun `active terrain field status is refused for the boosted type only`() {
        // Grassy Terrain (bit 6) applies a x1.3 modifier to a grounded attacker's Grass move; the
        // ordinary arithmetic does not model terrain, so a Grass move must fail closed.
        refusedWith(
            expected = CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED,
            request = goldenARequest(move = "Vine Whip"),
            player = playerObservation(fieldStatuses = 1 shl 6),
            enemy = enemyObservation(fieldStatuses = 1 shl 6)
        )
        // Normal Tackle is untouched by Grassy Terrain (no Grass Pelt, no Analytic): the field
        // decision is proven irrelevant and the request stays Ready.
        val ready = readyOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(fieldStatuses = 1 shl 6),
                enemyObservation(fieldStatuses = 1 shl 6)),
            "Grassy Terrain cannot change Tackle"
        )
        assertEquals("grassy_terrain_non_grass_move", ready.verdict.hnsFieldDecisions.single().rule)
    }

    @Test
    fun `an unmodelled field status does not launder the Ion Deluge bit`() {
        // Both Ion Deluge and Gravity (bit 5) set: the unsupported-bit gate must still refuse,
        // even though Ion Deluge on a Normal move is handled separately.
        refusedWith(
            expected = CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED,
            player = playerObservation(fieldStatuses = (1 shl 10) or (1 shl 5)),
            enemy = enemyObservation(fieldStatuses = (1 shl 10) or (1 shl 5))
        )
    }

    @Test
    fun `unreadable extended transient volatiles are refused`() {
        // The volatile window was read but the tuple does not carry chargeTimer/tarShot, so the
        // "transient state complete" claim cannot be made and the request fails closed.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED,
            player = playerObservation(transientVolatilesObserved = false),
            enemy = enemyObservation(transientVolatilesObserved = false)
        )
    }

    @Test
    fun `active Charge with an Electric move is refused`() {
        // chargeTimer > 0 doubles Thunder Shock (an ordinary EFFECT_HIT Electric move).
        refusedWith(
            expected = CalcLimitation.HNS_CHARGE_ACTIVE_NOT_MODELLED,
            request = goldenARequest(move = "Thunder Shock"),
            player = playerObservation(chargeTimer = 2)
        )
    }

    @Test
    fun `active Charge with an irrelevant move type is not refused`() {
        // Charge only doubles Electric moves; a Normal move is unaffected and stays Ready.
        val trust = trustFor(exactSha)
        val outcome = build(
            trust = trust,
            request = goldenARequest(move = "Tackle"),
            player = playerObservation(chargeTimer = 2),
            enemy = enemyObservation()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("an irrelevant Charge must not refuse, got $outcome")
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
    }

    @Test
    fun `active Tar Shot with a Fire move is refused`() {
        // The defender's tarShot doubles Ember (an ordinary EFFECT_HIT Fire move).
        refusedWith(
            expected = CalcLimitation.HNS_TAR_SHOT_ACTIVE_NOT_MODELLED,
            request = goldenARequest(move = "Ember"),
            enemy = enemyObservation(tarShot = true)
        )
    }

    @Test
    fun `active Tar Shot with an irrelevant move type is not refused`() {
        // Tar Shot only doubles Fire moves; a Normal move is unaffected and stays Ready.
        val trust = trustFor(exactSha)
        val outcome = build(
            trust = trust,
            request = goldenARequest(move = "Tackle"),
            player = playerObservation(),
            enemy = enemyObservation(tarShot = true)
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("an irrelevant Tar Shot must not refuse, got $outcome")
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
    }

    @Test
    fun `active Glaive Rush volatile is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_GLAIVE_RUSH_ACTIVE_NOT_MODELLED,
            enemy = enemyObservation(glaiveRush = true)
        )
    }

    // ---------------------------------------- persistent volatile state (review round 4)

    /**
     * Review round 4: the pinned ordinary-damage path reads persistent volatiles on ordinary
     * EFFECT_HIT moves. The first production subset does not model their positive behavior, so an
     * observed-active bit on either battler refuses with the precise limitation, while the
     * all-neutral Golden-A-equivalent path stays Ready (the positive control above).
     */
    @Test
    fun `foresight active on the defender refuses`() {
        // Real wrong-Ready: a Normal move vs a Ghost defender is static 0, but the pinned
        // MulByTypeEffectiveness returns 1.0 under volatiles.foresight.
        refusedWith(
            expected = CalcLimitation.HNS_FORESIGHT_ACTIVE_NOT_MODELLED,
            request = matchupRequest("Tackle", "Misdreavus"),
            enemy = enemyObservation(types = listOf(8), foresight = true)
        )
    }

    @Test
    fun `miracle eye active on the defender refuses`() {
        // Real wrong-Ready: a Psychic move vs a Dark defender is static 0, but the pinned
        // MulByTypeEffectiveness returns 1.0 under volatiles.miracleEye.
        refusedWith(
            expected = CalcLimitation.HNS_MIRACLE_EYE_ACTIVE_NOT_MODELLED,
            request = matchupRequest("Confusion", "Poochyena"),
            enemy = enemyObservation(types = listOf(18), miracleEye = true)
        )
    }

    @Test
    fun `smack down active on the defender refuses`() {
        // Real wrong-Ready: a Ground move vs a Flying defender is static 0, but IsBattlerGrounded
        // returns true under volatiles.smackDown.
        refusedWith(
            expected = CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED,
            request = matchupRequest("Earth Power", "Pidgey"),
            enemy = enemyObservation(smackDown = true)
        )
    }

    @Test
    fun `ingrain root active on the defender refuses`() {
        // Real wrong-Ready: Ingrain grounds the Flying defender, so a Ground move hits.
        refusedWith(
            expected = CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED,
            request = matchupRequest("Mud Shot", "Pidgey"),
            enemy = enemyObservation(root = true)
        )
    }

    @Test
    fun `telekinesis active on the defender refuses`() {
        // volatiles.telekinesis ungrounds the defender, changing Ground immunity.
        refusedWith(
            expected = CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED,
            request = matchupRequest("Earth Power", "Pidgey"),
            enemy = enemyObservation(telekinesis = true)
        )
    }

    @Test
    fun `magnet rise active on the defender refuses`() {
        // volatiles.magnetRise ungrounds the defender, changing Ground immunity.
        refusedWith(
            expected = CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED,
            request = matchupRequest("Earth Power", "Pidgey"),
            enemy = enemyObservation(magnetRise = true)
        )
    }

    @Test
    fun `roost active on the defender refuses`() {
        refusedWith(
            expected = CalcLimitation.HNS_ROOST_ACTIVE_NOT_MODELLED,
            enemy = enemyObservation(roostActive = true)
        )
    }

    @Test
    fun `gastro acid suppression on the attacker refuses`() {
        // Chikorita raw ability=Overgrow, Gastro Acid active, HP <= 1/3, Razor Leaf. The engine's
        // effective ability is ABILITY_NONE, so the x1.5 pinch boost must not apply; the request
        // fails closed precisely rather than computing the boost.
        val request = DamageCalculationRequest(
            gen = 3,
            typeSystem = "hns_2_0_5",
            attacker = liveInput("Chikorita", 6, 65, "Overgrow"),
            defender = liveInput("Pidgey", 3, 77, "Tangled Feet"),
            move = CalcMoveInput(name = "Razor Leaf")
        )
        refusedWith(
            expected = CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED,
            request = request,
            player = playerObservation(hp = 5, maxHp = 20, gastroAcid = true)
        )
    }

    @Test
    fun `substitute on the defender refuses`() {
        refusedWith(
            expected = CalcLimitation.HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED,
            enemy = enemyObservation(substitute = true)
        )
    }

    @Test
    fun `endure active on the defender refuses`() {
        refusedWith(
            expected = CalcLimitation.HNS_ENDURED_ACTIVE_NOT_MODELLED,
            enemy = enemyObservation(endured = true)
        )
    }

    @Test
    fun `unread persistent volatile window is refused`() {
        // The volatile window was read but the tuple cannot carry the review-round-4 operands, so
        // the completeness claim cannot be made and the request fails closed.
        refusedWith(
            expected = CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED,
            player = playerObservation(persistentVolatilesObserved = false),
            enemy = enemyObservation(persistentVolatilesObserved = false)
        )
    }

    @Test
    fun `anti-spoof - a crafted neutral persistent window cannot clear any observed active bit`() {
        // The caller crafts a live state asserting every persistent volatile observed neutral. The
        // boundary strips it and rebinds from the runtime observations, each of which carries one
        // active bit, so every craft is refused with the bit's precise limitation.
        data class BitCase(
            val name: String,
            val player: BattlerRuntimeObservation,
            val enemy: BattlerRuntimeObservation,
            val expected: CalcLimitation
        )
        val cases = listOf(
            BitCase("foresight", playerObservation(), enemyObservation(foresight = true),
                CalcLimitation.HNS_FORESIGHT_ACTIVE_NOT_MODELLED),
            BitCase("miracleEye", playerObservation(), enemyObservation(miracleEye = true),
                CalcLimitation.HNS_MIRACLE_EYE_ACTIVE_NOT_MODELLED),
            BitCase("root", playerObservation(), enemyObservation(root = true),
                CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED),
            BitCase("smackDown", playerObservation(), enemyObservation(smackDown = true),
                CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED),
            BitCase("telekinesis", playerObservation(), enemyObservation(telekinesis = true),
                CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED),
            BitCase("magnetRise", playerObservation(), enemyObservation(magnetRise = true),
                CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED),
            BitCase("roostActive", playerObservation(), enemyObservation(roostActive = true),
                CalcLimitation.HNS_ROOST_ACTIVE_NOT_MODELLED),
            BitCase("gastroAcid", playerObservation(gastroAcid = true), enemyObservation(),
                CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED),
            BitCase("substitute", playerObservation(), enemyObservation(substitute = true),
                CalcLimitation.HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED),
            BitCase("endured", playerObservation(), enemyObservation(endured = true),
                CalcLimitation.HNS_ENDURED_ACTIVE_NOT_MODELLED)
        )
        val crafted = goldenARequest().copy(
            hnsLiveBattleState = CalcHnsLiveBattleState(
                attackerPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true),
                defenderPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true)
            )
        )
        for (case in cases) {
            refusedWith(
                expected = case.expected,
                request = crafted,
                player = case.player,
                enemy = case.enemy
            )
        }
    }

    @Test
    fun `anti-spoof - a crafted active persistent bit cannot refuse when the runtime window is neutral`() {
        // The inverse craft: the caller asserts an active bit to force a refusal, but the runtime
        // observations are all neutral, so the request must stay Ready.
        val crafted = goldenARequest().copy(
            hnsLiveBattleState = CalcHnsLiveBattleState(
                attackerPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true, endured = true),
                defenderPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true, substitute = true)
            )
        )
        val outcome = build(
            trust = trustFor(exactSha),
            request = crafted,
            player = playerObservation(),
            enemy = enemyObservation()
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("a crafted active persistent bit must be stripped, got $outcome")
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertEquals(false, ready.request.hnsLiveBattleState?.attackerPersistentVolatiles?.endured)
        assertEquals(false, ready.request.hnsLiveBattleState?.defenderPersistentVolatiles?.substitute)
    }

    @Test
    fun `unsupported ability is refused`() {
        refusedWith(
            expected = CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED,
            request = goldenARequest(move = "Razor Leaf"),
            player = playerObservation(abilityId = 91, abilityName = "Adaptability")
        )
    }

    @Test
    fun `randomized attacker Tera Shell and defender Truant clear only their request-local blockers`() {
        val trust = trustFor(exactSha)
        val outcome = build(
            trust = trust,
            request = goldenARequest(), // Caller claims Chikorita Overgrow and Pidgey Tangled Feet.
            player = playerObservation(abilityId = 308, abilityName = "Tera Shell"),
            enemy = enemyObservation(abilityId = 54, abilityName = "Truant"),
            randomAbilities = true
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("live attacker Tera Shell + defender Truant should estimate, got $outcome")
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertEquals(308, ready.request.attacker.abilityId)
        assertEquals(54, ready.request.defender.abilityId)
        assertEquals(listOf(308, 54), ready.verdict.hnsAbilityDecisions.map { it.abilityId })
        assertTrue(ready.verdict.hnsAbilityDecisions.all {
            it.globalCategory == com.dualdex.pokemon.hns.HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT &&
                it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
        })
        assertEquals(com.dualdex.pokemon.hns.HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(308).category)
        assertEquals(com.dualdex.pokemon.hns.HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(54).category)
    }

    @Test
    fun `swapping Truant to attacker remains refused`() {
        val outcome = build(
            trust = trustFor(exactSha),
            request = goldenARequest(),
            player = playerObservation(abilityId = 54, abilityName = "Truant"),
            enemy = enemyObservation(abilityId = 308, abilityName = "Tera Shell"),
            randomAbilities = true
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("attacker Truant must remain blocked without truantCounter authority")
        assertTrue(outcome.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            outcome.verdict.hnsAbilityDecisions.first { it.abilityId == 54 }.relevance)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            outcome.verdict.hnsAbilityDecisions.first { it.abilityId == 308 }.relevance)
    }

    @Test
    fun `defender Tera Shell clears only with live species and HP proof`() {
        val trust = trustFor(exactSha)
        val regularSpecies = build(
            trust, goldenARequest(), playerObservation(),
            enemyObservation(abilityId = 308, abilityName = "Tera Shell"), randomAbilities = true
        )
        assertTrue("non-Terapagos Tera Shell is irrelevant", regularSpecies is CalcRequestOutcome.Ready)

        val terapagosRequest = goldenARequest().copy(
            defender = liveInput("Terapagos", 3, 77, "Tangled Feet")
        )
        val fullHp = build(
            trust, terapagosRequest, playerObservation(),
            enemyObservation(speciesId = 1432, abilityId = 308, abilityName = "Tera Shell",
                types = listOf(1), hp = 15, maxHp = 15), randomAbilities = true
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("full-HP Terapagos-Terastal with Tera Shell must remain blocked")
        assertTrue(fullHp.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            fullHp.verdict.hnsAbilityDecisions.single { it.abilityId == 308 }.relevance)

        val belowFull = build(
            trust, terapagosRequest, playerObservation(),
            enemyObservation(speciesId = 1432, abilityId = 308, abilityName = "Tera Shell",
                types = listOf(1), hp = 14, maxHp = 15), randomAbilities = true
        ) as? CalcRequestOutcome.Refused
            ?: throw AssertionError("the ambiguous Terapagos display name still fails the independent species gate")
        assertFalse("below-full proof must clear only the Tera Shell blocker",
            belowFull.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertTrue(belowFull.verdict.limitations.contains(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            belowFull.verdict.hnsAbilityDecisions.single { it.abilityId == 308 }.relevance)

        for (missing in listOf(
            enemyObservation(speciesId = 1432, abilityId = 308, abilityName = "Tera Shell",
                types = listOf(1), hpObserved = false),
            enemyObservation(speciesId = null, abilityId = 308, abilityName = "Tera Shell",
                types = listOf(1)),
            enemyObservation(speciesId = 65535, abilityId = 308, abilityName = "Tera Shell",
                types = listOf(1))
        )) {
            val refused = build(trust, terapagosRequest, playerObservation(), missing, randomAbilities = true)
                as? CalcRequestOutcome.Refused
                ?: throw AssertionError("missing species/HP authority must not clear Tera Shell")
            assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
            assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
                refused.verdict.hnsAbilityDecisions.single { it.abilityId == 308 }.relevance)
        }
    }

    @Test
    fun `Telepathy stays globally unsupported but is irrelevant in observed Singles`() {
        val ready = build(
            trustFor(exactSha), goldenARequest(),
            playerObservation(abilityId = 9, abilityName = "Static"),
            enemyObservation(abilityId = 140, abilityName = "Telepathy"), randomAbilities = true
        ) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("Telepathy cannot affect ordinary opponent damage in observed Singles")
        val telepathy = ready.verdict.hnsAbilityDecisions.single { it.abilityId == 140 }
        assertEquals(com.dualdex.pokemon.hns.HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            telepathy.globalCategory)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, telepathy.relevance)
        assertEquals(com.dualdex.pokemon.hns.HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(140).category)

        for (count in listOf(null, 4)) {
            val refused = build(
                trustFor(exactSha), goldenARequest(),
                playerObservation(abilityId = 9, abilityName = "Static", observedBattlersCount = count),
                enemyObservation(abilityId = 140, abilityName = "Telepathy", observedBattlersCount = count),
                randomAbilities = true
            ) as? CalcRequestOutcome.Refused
                ?: throw AssertionError("unknown or Doubles topology must not clear Telepathy")
            assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
            assertNotEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                refused.verdict.hnsAbilityDecisions.single { it.abilityId == 140 }.relevance)
        }
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
    fun `caller-supplied neutral weather and screens cannot spoof an unobserved live state`() {
        // The caller supplies a neutral field (no weather / no screens) AND a crafted live state
        // that claims the words were observed neutral. The runtime observations did not read
        // either word, so the boundary must strip both crafts and refuse with the precise
        // unknown-field limitations - a caller default must never stand in for a live read.
        val trust = trustFor(exactSha)
        val crafted = goldenARequest().copy(
            field = CalcFieldInput(weather = null, defenderSide = null),
            hnsLiveBattleState = CalcHnsLiveBattleState(
                weatherObserved = true,
                weatherWord = 0,
                defenderScreensObserved = true,
                defenderSideStatuses = 0
            )
        )
        val outcome = build(
            trust = trust,
            request = crafted,
            player = playerObservation(weatherReadable = false, sideStatusesReadable = false),
            enemy = enemyObservation(weatherReadable = false, sideStatusesReadable = false)
        )
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("a crafted neutral live field condition must not authorize, got $outcome")
        assertNull(refused.verdict.request)
        assertTrue(
            "an unread live weather word must refuse: ${refused.verdict.limitations}",
            refused.verdict.limitations.contains(CalcLimitation.HNS_LIVE_WEATHER_UNKNOWN)
        )
        assertTrue(
            "an unread live side-status word must refuse: ${refused.verdict.limitations}",
            refused.verdict.limitations.contains(CalcLimitation.HNS_LIVE_SCREENS_UNKNOWN)
        )
    }

    @Test
    fun `caller-supplied weather and screens are rebound from the observed words`() {
        // The caller asserts Rain + both screens; the engine reports clear weather and no screens.
        // The engine JSON must carry the observed neutral state, never the caller's assertion.
        val trust = trustFor(exactSha)
        val crafted = goldenARequest().copy(
            field = CalcFieldInput(
                weather = "Rain",
                defenderSide = SideConditions(isReflect = true, isLightScreen = true)
            )
        )
        val outcome = build(
            trust = trust,
            request = crafted,
            player = playerObservation(battleWeather = 0, sideStatuses = 0),
            enemy = enemyObservation(battleWeather = 0, sideStatuses = 0)
        )
        val ready = outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("observed neutral weather/screens must be calculable, got $outcome")
        assertNull("the caller's Rain must be stripped", ready.request.field.weather)
        assertNull("the caller's screens must be stripped", ready.request.field.defenderSide)
        val json = buildCalcRequestJson(ready.request)
        assertFalse("the caller's Rain must not reach the engine: $json", json.contains("\"weather\""))
        assertFalse("the caller's screens must not reach the engine: $json", json.contains("\"defenderSide\""))
    }

    @Test
    fun `caller-spoofed ability is overridden by the authoritative runtime ID`() {
        // The caller claims Overgrow, but the engine reports Adaptability (91, unsupported).
        val trust = trustFor(exactSha)
        val spoofed = goldenARequest().copy(
            attacker = liveInput("Chikorita", 5, 0, "Overgrow"),
            move = CalcMoveInput(name = "Razor Leaf") // STAB keeps Adaptability relevant.
        )
        refusedWith(
            expected = CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED,
            request = spoofed,
            player = playerObservation(abilityId = 91, abilityName = "Adaptability")
        )
    }

    // ------------------------------------------------ held items: request-local relevance

    private fun readyOf(outcome: CalcRequestOutcome, why: String): CalcRequestOutcome.Ready =
        outcome as? CalcRequestOutcome.Ready ?: throw AssertionError("$why, got $outcome")

    private fun refusedOf(outcome: CalcRequestOutcome, why: String): CalcRequestOutcome.Refused =
        outcome as? CalcRequestOutcome.Refused ?: throw AssertionError("$why, got $outcome")

    @Test
    fun `live items proven irrelevant clear only their own item blockers and are stripped`() {
        // Your Charcoal cannot boost Normal Tackle; the foe's Choice Band is read only when it attacks.
        val ready = readyOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(itemId = 426), enemyObservation(itemId = 442)),
            "irrelevant live items must not refuse an otherwise supported request"
        )
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals(listOf(426, 442), ready.verdict.hnsItemDecisions.map { it.itemId })
        assertEquals(
            listOf("type_item_move_type_mismatch", "defender_holds_attacker_only_item"),
            ready.verdict.hnsItemDecisions.map { it.rule }
        )
        assertTrue(ready.verdict.hnsItemDecisions.all {
            it.globalCategory == com.dualdex.pokemon.hns.HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT &&
                it.relevance == HnsItemRequestRelevance.PROVEN_IRRELEVANT
        })
        // The global category is untouched by the request-local clearance.
        assertEquals(com.dualdex.pokemon.hns.HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
            com.dualdex.pokemon.hns.HnsItemRegistry.classify(426).category)
        // Live numeric IDs are bound, and the engine receives no item name at all.
        assertEquals(426, ready.request.attacker.itemId)
        assertEquals(442, ready.request.defender.itemId)
        assertNull(ready.request.attacker.item)
        assertNull(ready.request.defender.item)
        val json = buildCalcRequestJson(ready.request)
        assertFalse("no H&S item name may reach the engine: $json", json.contains("\"item\""))
    }

    @Test
    fun `a relevant live item stays refused with its structured decision`() {
        val refused = refusedOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(itemId = 425), enemyObservation()),
            "Silk Scarf boosts Normal Tackle"
        )
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        val decision = refused.verdict.hnsItemDecisions.single()
        assertEquals(425, decision.itemId)
        assertEquals("Silk Scarf", decision.itemName)
        assertEquals(HnsItemSide.ATTACKER, decision.side)
        assertEquals(HnsItemRequestRelevance.RELEVANT, decision.relevance)

        val sash = refusedOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(), enemyObservation(itemId = 481)),
            "a full-HP foe's Focus Sash can change the HP lost"
        )
        assertEquals(HnsItemRequestRelevance.RELEVANT, sash.verdict.hnsItemDecisions.single().relevance)
        val damagedFoe = build(trustFor(exactSha), goldenARequest(),
            playerObservation(), enemyObservation(itemId = 481, hp = 9, maxHp = 15))
        assertTrue("Focus Sash below max HP cannot activate", damagedFoe is CalcRequestOutcome.Ready)
    }

    @Test
    fun `missing effective-type authority keeps a type item unknown and blocked`() {
        // Electrify rewrites the move to Electric, so the effective type is not authoritative and
        // Charcoal cannot be cleared even though Tackle's pinned type is Normal.
        val refused = refusedOf(
            build(trustFor(exactSha), goldenARequest(),
                playerObservation(itemId = 426, electrified = true), enemyObservation()),
            "an unproven effective type must not clear a type item"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, refused.verdict.hnsItemDecisions.single().relevance)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        // An unknown field bit also removes effective-type authority (it is never assumed harmless).
        val unknownBit = refusedOf(
            build(trustFor(exactSha), goldenARequest(),
                playerObservation(itemId = 426, fieldStatuses = 1 shl 13), enemyObservation(fieldStatuses = 1 shl 13)),
            "an unknown field bit must not be assumed type-neutral"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, unknownBit.verdict.hnsItemDecisions.single().relevance)
    }

    @Test
    fun `a live Rusted Sword or Shield is refused as a battle form-change identity`() {
        for ((player, enemy) in listOf(playerObservation(itemId = 288) to enemyObservation(),
            playerObservation() to enemyObservation(itemId = 289))) {
            val refused = refusedOf(build(trustFor(exactSha), goldenARequest(), player, enemy),
                "a Rusted item must never inherit HOLD_EFFECT_NONE neutrality")
            assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
            val decision = refused.verdict.hnsItemDecisions.single()
            assertEquals(com.dualdex.pokemon.hns.HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, decision.globalCategory)
            assertEquals(HnsItemRequestRelevance.UNKNOWN, decision.relevance)
        }
    }

    @Test
    fun `clearing an item never clears another limitation`() {
        val refused = refusedOf(
            build(trustFor(exactSha), goldenARequest(),
                playerObservation(status1 = 0x10), enemyObservation(itemId = 442)),
            "an active live status stays refused"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, refused.verdict.hnsItemDecisions.single().relevance)
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))
    }

    @Test
    fun `the move-item interaction gate stays independent of item relevance`() {
        // Knock Off reads the foe's item presence even though Choice Band's own effect is irrelevant.
        val refused = refusedOf(
            build(trustFor(exactSha), goldenARequest(move = "Knock Off"), playerObservation(), enemyObservation(itemId = 442)),
            "Knock Off is item-dependent"
        )
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        // A non-ordinary move is never an operand for clearing an item that needs the ordinary path.
        val scarf = refusedOf(
            build(trustFor(exactSha), goldenARequest(move = "Knock Off"), playerObservation(itemId = 444), enemyObservation()),
            "Choice Scarf needs an ordinary move"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, scarf.verdict.hnsItemDecisions.single().relevance)
    }

    @Test
    fun `item and ability contextual policies compose`() {
        val ready = readyOf(
            build(trustFor(exactSha), goldenARequest(),
                playerObservation(abilityId = 308, abilityName = "Tera Shell", itemId = 472), // Leftovers
                enemyObservation(abilityId = 54, abilityName = "Truant", itemId = 442),
                randomAbilities = true),
            "irrelevant abilities and items together must estimate"
        )
        assertEquals(2, ready.verdict.hnsAbilityDecisions.size)
        assertEquals(2, ready.verdict.hnsItemDecisions.size)
        assertEquals("single_hit_item_activation_outside_damage", ready.verdict.hnsItemDecisions.first().rule)

        val refused = refusedOf(
            build(trustFor(exactSha), goldenARequest(),
                playerObservation(abilityId = 308, abilityName = "Tera Shell"),
                enemyObservation(abilityId = 54, abilityName = "Truant", itemId = 481),
                randomAbilities = true),
            "a relevant item is not hidden by irrelevant abilities"
        )
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `anti-spoof - the live current item wins over any caller item claim`() {
        val trust = trustFor(exactSha)
        // Caller claims an irrelevant Charcoal (ID and name); the engine's current item is Silk Scarf.
        val claimed = goldenARequest().let {
            it.copy(attacker = it.attacker.copy(itemId = 426, item = "CHARCOAL",
                itemProvenance = CalcItemProvenance.PARTY_STORAGE))
        }
        val spoofed = refusedOf(build(trust, claimed, playerObservation(itemId = 425), enemyObservation()),
            "a caller item cannot replace the live current item")
        assertEquals(425, spoofed.verdict.hnsItemDecisions.single().itemId)

        // A name-only caller claim is ignored for an active battler too.
        val named = goldenARequest().let { it.copy(attacker = it.attacker.copy(item = "Charcoal")) }
        assertEquals(425, refusedOf(build(trust, named, playerObservation(itemId = 425), enemyObservation()),
            "a caller name cannot replace the live current item").verdict.hnsItemDecisions.single().itemId)

        // Consumed / knocked off: stored Silk Scarf, live ITEM_NONE -> the live word wins.
        val stored = goldenARequest().let {
            it.copy(attacker = it.attacker.copy(itemId = 425, item = "SILK SCARF",
                itemProvenance = CalcItemProvenance.PARTY_STORAGE))
        }
        val consumed = readyOf(build(trust, stored, playerObservation(itemId = 0), enemyObservation()),
            "a consumed item is represented by the live ITEM_NONE")
        assertEquals(0, consumed.request.attacker.itemId)
        assertTrue(consumed.verdict.hnsItemDecisions.isEmpty())

        // Swapped (Trick): stored Charcoal, live Choice Band on a physical move -> the live item refuses.
        val swapped = refusedOf(build(trust, claimed, playerObservation(itemId = 442), enemyObservation()),
            "the swapped-in live Choice Band is relevant to Tackle")
        assertEquals(442, swapped.verdict.hnsItemDecisions.single().itemId)
        assertEquals(HnsItemRequestRelevance.RELEVANT, swapped.verdict.hnsItemDecisions.single().relevance)

        // Unread current item: never falls back to the stored irrelevant item.
        val unread = refusedOf(build(trust, claimed, playerObservation(itemId = null), enemyObservation()),
            "an unread live item must not fall back to the stored item")
        assertTrue(unread.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE))
        assertTrue(unread.verdict.hnsItemDecisions.isEmpty())

        // The opponent item comes only from the resolved active opponent's live word.
        val foeStored = goldenARequest().let {
            it.copy(defender = it.defender.copy(itemId = 481, item = "FOCUS SASH",
                itemProvenance = CalcItemProvenance.PARTY_STORAGE))
        }
        val foe = readyOf(build(trust, foeStored, playerObservation(), enemyObservation(itemId = 442)),
            "the foe's live Choice Band, not its stored Focus Sash, is the effective item")
        assertEquals(listOf(442), foe.verdict.hnsItemDecisions.map { it.itemId })
    }

    // ------------------------------------- live field status: per-bit request relevance

    private val wiseGlasses = 476
    private val everstone = 245 // globally PROVEN_NO_ORDINARY_DAMAGE_EFFECT

    /** Golden-A with the same battle-global field word on both observations. */
    private fun fieldBuild(
        word: Int,
        move: String = "Tackle",
        attackerItem: Int? = 0,
        defenderItem: Int? = 0,
        attackerAbility: Pair<Int, String> = 65 to "Overgrow",
        defenderAbility: Pair<Int, String> = 77 to "Tangled Feet",
        status1: Int = 0,
        electrified: Boolean = false,
        optionStyle: Int = 0
    ): CalcRequestOutcome = build(
        trustFor(exactSha), goldenARequest(move),
        playerObservation(fieldStatuses = word, itemId = attackerItem, abilityId = attackerAbility.first,
            abilityName = attackerAbility.second, status1 = status1, electrified = electrified),
        enemyObservation(fieldStatuses = word, itemId = defenderItem, abilityId = defenderAbility.first,
            abilityName = defenderAbility.second),
        randomAbilities = true,
        optionStyle = optionStyle
    )

    private fun CalcRequestOutcome.verdict(): CalcCapabilityVerdict = when (this) {
        is CalcRequestOutcome.Ready -> verdict
        is CalcRequestOutcome.Refused -> verdict
    }

    private fun fieldDecision(outcome: CalcRequestOutcome, status: HnsFieldStatus): HnsFieldRequestDecision =
        outcome.verdict().hnsFieldDecisions.single { it.status == status }

    private fun cardText(outcome: CalcRequestOutcome): String {
        val blockers = com.dualdex.battle.DamageBlockerPresentation.from(outcome.verdict(), observedDoubles = false)
        return com.dualdex.battle.DamageBlockerPresentation.unavailableText(
            blockers, com.dualdex.battle.DamageBlockerPresentation.headline(blockers)
        )
    }

    @Test
    fun `the raw field word is preserved and every active bit gets its own named decision`() {
        val word = HnsFieldStatus.WONDER_ROOM.mask or HnsFieldStatus.ELECTRIC_TERRAIN.mask or
            HnsFieldStatus.FAIRY_LOCK.mask
        val refused = refusedOf(fieldBuild(word), "Wonder Room always blocks")
        assertEquals(0x904, refused.verdict.hnsFieldDiagnostics?.fieldState?.raw)
        assertEquals(
            listOf(HnsFieldStatus.WONDER_ROOM, HnsFieldStatus.ELECTRIC_TERRAIN, HnsFieldStatus.FAIRY_LOCK),
            refused.verdict.hnsFieldDecisions.map { it.status }
        )
        assertEquals(
            listOf(HnsFieldRequestRelevance.RELEVANT, HnsFieldRequestRelevance.PROVEN_IRRELEVANT,
                HnsFieldRequestRelevance.PROVEN_IRRELEVANT),
            refused.verdict.hnsFieldDecisions.map { it.relevance }
        )
        assertEquals(
            listOf("wonder_room_swaps_defensive_stat", "electric_terrain_non_electric_move", "fairy_lock_escape_only"),
            refused.verdict.hnsFieldDecisions.map { it.rule }
        )
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals("Damage unavailable · Wonder Room not modelled\nField: Wonder Room (0x00000004)", cardText(refused))
    }

    @Test
    fun `a readable zero field word is clear and records no decision`() {
        val ready = readyOf(fieldBuild(0), "an observed clear field is the positive control")
        assertEquals(0, ready.verdict.hnsFieldDiagnostics?.fieldState?.raw)
        assertTrue(ready.verdict.hnsFieldDiagnostics!!.fieldState!!.isClear)
        assertTrue(ready.verdict.hnsFieldDecisions.isEmpty())
    }

    @Test
    fun `Wise Glasses with a Physical move clears while an unrelated field condition blocks alone`() {
        // Device regression (AYN Thor): exact H&S, ordinary Singles, attacker Wise Glasses, a non-zero
        // live field word. Tackle is authoritatively Physical under PER_MOVE_SPLIT; Wonder Room does
        // not change that, so the card shows the field condition and NOT a second Wise Glasses blocker.
        val refused = refusedOf(
            fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, attackerItem = wiseGlasses),
            "Wonder Room independently blocks"
        )
        val glasses = refused.verdict.hnsItemDecisions.single()
        assertEquals("Wise Glasses", glasses.itemName)
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, glasses.relevance)
        assertEquals("special_only_item_physical_move", glasses.rule)
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals("Damage unavailable · Wonder Room not modelled\nField: Wonder Room (0x00000004)", cardText(refused))
    }

    @Test
    fun `Wise Glasses with a Special move stays a real blocker next to the field condition`() {
        val refused = refusedOf(
            fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, move = "Water Gun", attackerItem = wiseGlasses),
            "Wise Glasses boosts Special Water Gun"
        )
        val glasses = refused.verdict.hnsItemDecisions.single()
        assertEquals(HnsItemRequestRelevance.RELEVANT, glasses.relevance)
        assertEquals("special_only_item_special_move", glasses.rule)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals(
            "Damage unavailable · 2 blockers\nField: Wonder Room (0x00000004)\nYou: Wise Glasses",
            cardText(refused)
        )
    }

    @Test
    fun `device-shaped Electric Terrain from a switch-in surge is decided per move`() {
        // The most likely Thor explanation: a Random Abilities lead with Electric Surge / Hadron
        // Engine sets Electric Terrain (0x00000100) on switch-in, before any move is chosen.
        val terrain = HnsFieldStatus.ELECTRIC_TERRAIN.mask
        val tackle = readyOf(fieldBuild(terrain, attackerItem = wiseGlasses),
            "neither Electric Terrain nor Wise Glasses can change Physical Tackle")
        assertEquals("electric_terrain_non_electric_move", fieldDecision(tackle, HnsFieldStatus.ELECTRIC_TERRAIN).rule)
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, tackle.verdict.hnsItemDecisions.single().relevance)

        val waterGun = refusedOf(fieldBuild(terrain, move = "Water Gun", attackerItem = wiseGlasses),
            "Wise Glasses is relevant to a Special move")
        assertEquals(HnsFieldRequestRelevance.PROVEN_IRRELEVANT,
            fieldDecision(waterGun, HnsFieldStatus.ELECTRIC_TERRAIN).relevance)
        assertFalse(waterGun.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals("Damage unavailable · Your Wise Glasses not modelled", cardText(waterGun))

        val thunderShock = refusedOf(fieldBuild(terrain, move = "Thunder Shock", attackerItem = wiseGlasses),
            "Electric Terrain boosts an Electric move")
        assertEquals("electric_terrain_electric_move", fieldDecision(thunderShock, HnsFieldStatus.ELECTRIC_TERRAIN).rule)
        assertEquals(
            "Damage unavailable · 2 blockers\nField: Electric Terrain (0x00000100)\nYou: Wise Glasses",
            cardText(thunderShock)
        )

        // A Hadron Engine attacker keeps its own ability blocker AND the terrain it reads.
        val hadron = refusedOf(fieldBuild(terrain, attackerAbility = 289 to "Hadron Engine"),
            "Hadron Engine reads Electric Terrain")
        assertEquals("electric_terrain_paradox_ability", fieldDecision(hadron, HnsFieldStatus.ELECTRIC_TERRAIN).rule)
        assertTrue(hadron.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertTrue(hadron.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals(
            "Damage unavailable · 2 blockers\nField: Electric Terrain (0x00000100)\nYou: Hadron Engine",
            cardText(hadron)
        )
    }

    @Test
    fun `type-based category needs only effective-type authority`() {
        // TYPE_BASED: Normal Tackle is Physical from its type; Wonder Room does not change the type.
        val physical = refusedOf(
            fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, attackerItem = wiseGlasses, optionStyle = 1),
            "Wonder Room blocks"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, physical.verdict.hnsItemDecisions.single().relevance)
        // Ion Deluge rewrites Normal Tackle to Electric: type (and so TYPE_BASED category) is unknown.
        val ionDeluge = refusedOf(
            fieldBuild(HnsFieldStatus.ION_DELUGE.mask, attackerItem = wiseGlasses, optionStyle = 1),
            "Ion Deluge on a Normal move stays refused"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, ionDeluge.verdict.hnsItemDecisions.single().relevance)
        // Under PER_MOVE_SPLIT the category does not depend on the type rewrite.
        val perMove = refusedOf(
            fieldBuild(HnsFieldStatus.ION_DELUGE.mask, attackerItem = wiseGlasses),
            "Ion Deluge on a Normal move stays refused"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, perMove.verdict.hnsItemDecisions.single().relevance)
    }

    @Test
    fun `Charcoal clears on a known non-Fire move despite an unrelated field bit`() {
        val refused = refusedOf(fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, attackerItem = 426), "Wonder Room blocks")
        val charcoal = refused.verdict.hnsItemDecisions.single()
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, charcoal.relevance)
        assertEquals("type_item_move_type_mismatch", charcoal.rule)
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        // Missing dynamic-type authority still fails closed.
        val electrified = refusedOf(
            fieldBuild(HnsFieldStatus.TRICK_ROOM.mask, attackerItem = 426, electrified = true),
            "Electrify removes effective-type authority"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, electrified.verdict.hnsItemDecisions.single().relevance)
    }

    @Test
    fun `an unknown field bit blocks with its preserved mask`() {
        val refused = refusedOf(fieldBuild(0x2000), "a bit outside the pinned mask must block")
        val decision = refused.verdict.hnsFieldDecisions.single()
        assertNull(decision.status)
        assertEquals(0x2000, decision.rawMask)
        assertEquals(HnsFieldRequestRelevance.UNKNOWN, decision.relevance)
        assertEquals("unknown_field_bits", decision.rule)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals("Damage unavailable · Unknown field state 0x00002000\nField: Unknown bits 0x00002000", cardText(refused))

        // An unknown bit also removes effective-type authority, so a co-set terrain cannot be cleared.
        val mixed = refusedOf(fieldBuild(0x2000 or HnsFieldStatus.ELECTRIC_TERRAIN.mask), "unknown bit blocks")
        assertEquals(0x2100, mixed.verdict.hnsFieldDiagnostics?.fieldState?.raw)
        assertEquals(HnsFieldRequestRelevance.UNKNOWN, fieldDecision(mixed, HnsFieldStatus.ELECTRIC_TERRAIN).relevance)
        assertEquals(
            "Damage unavailable · 2 field blockers\nElectric Terrain (0x00000100)\nUnknown bits 0x00002000",
            cardText(mixed)
        )
    }

    @Test
    fun `multiple active field bits are decided independently`() {
        val word = HnsFieldStatus.MUD_SPORT.mask or HnsFieldStatus.WATER_SPORT.mask or HnsFieldStatus.TRICK_ROOM.mask
        val refused = refusedOf(fieldBuild(word, move = "Thunder Shock"), "Mud Sport weakens Electric moves")
        assertEquals(HnsFieldRequestRelevance.RELEVANT, fieldDecision(refused, HnsFieldStatus.MUD_SPORT).relevance)
        assertEquals(HnsFieldRequestRelevance.PROVEN_IRRELEVANT, fieldDecision(refused, HnsFieldStatus.WATER_SPORT).relevance)
        assertEquals(HnsFieldRequestRelevance.PROVEN_IRRELEVANT, fieldDecision(refused, HnsFieldStatus.TRICK_ROOM).relevance)
        assertEquals("Damage unavailable · Mud Sport not modelled\nField: Mud Sport (0x00000008)", cardText(refused))

        val both = refusedOf(fieldBuild(word or HnsFieldStatus.WONDER_ROOM.mask, move = "Thunder Shock"), "two blockers")
        assertEquals(
            "Damage unavailable · 2 field blockers\nWonder Room (0x00000004)\nMud Sport (0x00000008)",
            cardText(both)
        )
        readyOf(fieldBuild(word, move = "Tackle"), "no active bit can change Tackle")
    }

    @Test
    fun `clearing a field bit never clears another limitation and vice versa`() {
        // Electric Terrain is irrelevant to Tackle, but the live attacker status still blocks.
        val status = refusedOf(fieldBuild(HnsFieldStatus.ELECTRIC_TERRAIN.mask, status1 = 0x10), "status blocks")
        assertEquals(HnsFieldRequestRelevance.PROVEN_IRRELEVANT,
            fieldDecision(status, HnsFieldStatus.ELECTRIC_TERRAIN).relevance)
        assertFalse(status.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertTrue(status.verdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))

        // A relevant item and a relevant field condition are both kept.
        val both = refusedOf(fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, attackerItem = 425), "Silk Scarf + Wonder Room")
        assertTrue(both.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(both.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals(
            "Damage unavailable · 2 blockers\nField: Wonder Room (0x00000004)\nYou: Silk Scarf",
            cardText(both)
        )

        // Field + item + unsupported move effect: every blocker stays visible.
        val three = refusedOf(fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, move = "Water Gun",
            attackerItem = wiseGlasses, attackerAbility = 62 to "Guts", status1 = 0), "three blocker classes")
        assertTrue(three.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertTrue(three.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        val withMove = refusedOf(fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, move = "Seismic Toss",
            attackerItem = 425, attackerAbility = 148 to "Analytic"), "field + ability + item + move")
        assertEquals(
            "Damage unavailable · 4 blockers\nField: Wonder Room (0x00000004)\nYou: Analytic\nYou: Silk Scarf\nMove effect not modelled",
            cardText(withMove)
        )
    }

    @Test
    fun `Trick Room is relevant only to an Analytic attacker`() {
        val ready = readyOf(fieldBuild(HnsFieldStatus.TRICK_ROOM.mask), "Trick Room cannot change Tackle")
        assertEquals("trick_room_attacker_not_analytic", fieldDecision(ready, HnsFieldStatus.TRICK_ROOM).rule)
        val analytic = refusedOf(fieldBuild(HnsFieldStatus.TRICK_ROOM.mask, attackerAbility = 148 to "Analytic"),
            "Analytic reads turn order")
        assertEquals("trick_room_attacker_analytic", fieldDecision(analytic, HnsFieldStatus.TRICK_ROOM).rule)
        assertTrue(analytic.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertTrue(analytic.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
    }

    @Test
    fun `Gravity clears only non-Ground moves that it does not ban`() {
        val ready = readyOf(fieldBuild(HnsFieldStatus.GRAVITY.mask), "Gravity cannot change Tackle")
        assertEquals("gravity_non_ground_unbanned_move", fieldDecision(ready, HnsFieldStatus.GRAVITY).rule)
        val ground = fieldBuild(HnsFieldStatus.GRAVITY.mask, move = "Mud-Slap")
        assertEquals("gravity_ground_move", fieldDecision(ground, HnsFieldStatus.GRAVITY).rule)
        assertTrue(ground.verdict().limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        val banned = fieldBuild(HnsFieldStatus.GRAVITY.mask, move = "Floaty Fall")
        assertEquals("gravity_banned_move", fieldDecision(banned, HnsFieldStatus.GRAVITY).rule)
    }

    @Test
    fun `terrain type rules block the boosted or weakened type and clear the rest`() {
        val cases = listOf(
            Triple(HnsFieldStatus.MISTY_TERRAIN, "Dragon Breath", "misty_terrain_dragon_move"),
            Triple(HnsFieldStatus.MISTY_TERRAIN, "Tackle", "misty_terrain_non_dragon_move"),
            Triple(HnsFieldStatus.PSYCHIC_TERRAIN, "Confusion", "psychic_terrain_psychic_move"),
            Triple(HnsFieldStatus.PSYCHIC_TERRAIN, "Quick Attack", "psychic_terrain_priority_move"),
            Triple(HnsFieldStatus.PSYCHIC_TERRAIN, "Tackle", "psychic_terrain_non_psychic_non_priority_move"),
            Triple(HnsFieldStatus.WATER_SPORT, "Ember", "water_sport_fire_move"),
            Triple(HnsFieldStatus.GRASSY_TERRAIN, "Vine Whip", "grassy_terrain_grass_move")
        )
        for ((status, move, rule) in cases) {
            val outcome = fieldBuild(status.mask, move = move)
            assertEquals("$status / $move", rule, fieldDecision(outcome, status).rule)
            val blocks = outcome.verdict().limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED)
            assertEquals("$status / $move", !rule.contains("non_"), blocks)
        }
        // Grass Pelt reads Grassy Terrain even for a non-Grass move.
        val pelt = fieldBuild(HnsFieldStatus.GRASSY_TERRAIN.mask, defenderAbility = 179 to "Grass Pelt")
        assertEquals("grassy_terrain_grass_pelt_defender", fieldDecision(pelt, HnsFieldStatus.GRASSY_TERRAIN).rule)
        // Gale Wings can grant Flying priority: Psychic Terrain stays unknown.
        val gale = fieldBuild(HnsFieldStatus.PSYCHIC_TERRAIN.mask, attackerAbility = 177 to "Gale Wings")
        assertEquals(HnsFieldRequestRelevance.UNKNOWN, fieldDecision(gale, HnsFieldStatus.PSYCHIC_TERRAIN).relevance)
    }

    @Test
    fun `Magic Room clears only when both live items are absent or globally neutral`() {
        readyOf(fieldBuild(HnsFieldStatus.MAGIC_ROOM.mask), "no held items")
        val neutral = readyOf(fieldBuild(HnsFieldStatus.MAGIC_ROOM.mask, attackerItem = everstone, defenderItem = everstone),
            "Everstone's hold effect never reaches damage")
        assertEquals("magic_room_held_items_neutral", fieldDecision(neutral, HnsFieldStatus.MAGIC_ROOM).rule)
        // Charcoal is itself irrelevant to Tackle, but its suppression is not modelled: Magic Room stays.
        val charcoal = refusedOf(fieldBuild(HnsFieldStatus.MAGIC_ROOM.mask, attackerItem = 426), "unsupported item held")
        assertEquals(HnsFieldRequestRelevance.UNKNOWN, fieldDecision(charcoal, HnsFieldStatus.MAGIC_ROOM).relevance)
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, charcoal.verdict.hnsItemDecisions.single().relevance)
        assertEquals("Damage unavailable · Magic Room not modelled\nField: Magic Room (0x00000001)", cardText(charcoal))
    }

    @Test
    fun `Ion Deluge keeps its dynamic-type refusal and is named on the card`() {
        val refused = refusedOf(fieldBuild(HnsFieldStatus.ION_DELUGE.mask), "Ion Deluge on Normal Tackle")
        assertEquals("ion_deluge_normal_move", fieldDecision(refused, HnsFieldStatus.ION_DELUGE).rule)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals("Damage unavailable · Ion Deluge not modelled\nField: Ion Deluge (0x00000400)", cardText(refused))
        // Electrify is a second, independent cause that keeps its own blocker.
        val electrified = refusedOf(fieldBuild(HnsFieldStatus.ION_DELUGE.mask, electrified = true), "both causes")
        assertEquals(
            "Damage unavailable · 2 blockers\nBattle effect not modelled\nField: Ion Deluge (0x00000400)",
            cardText(electrified)
        )
        val waterGun = readyOf(fieldBuild(HnsFieldStatus.ION_DELUGE.mask, move = "Water Gun"), "Ion Deluge ignores Water")
        assertEquals("ion_deluge_non_normal_move", fieldDecision(waterGun, HnsFieldStatus.ION_DELUGE).rule)
    }
}
