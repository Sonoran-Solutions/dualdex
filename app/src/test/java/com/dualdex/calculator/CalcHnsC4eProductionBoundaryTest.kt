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
        abilityId: Int = 77,
        abilityName: String = "Tangled Feet",
        types: List<Int> = listOf(1, 3), // Normal, Flying (Pidgey)
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
    fun `active terrain field status is refused`() {
        // Grassy Terrain (bit 6) applies a x1.3 Grass modifier; the ordinary arithmetic does not
        // model terrain, so it must fail closed.
        refusedWith(
            expected = CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED,
            player = playerObservation(fieldStatuses = 1 shl 6),
            enemy = enemyObservation(fieldStatuses = 1 shl 6)
        )
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
            attacker = liveInput("Chikorita", 5, 0, "Overgrow")
        )
        refusedWith(
            expected = CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED,
            request = spoofed,
            player = playerObservation(abilityId = 91, abilityName = "Adaptability")
        )
    }
}
