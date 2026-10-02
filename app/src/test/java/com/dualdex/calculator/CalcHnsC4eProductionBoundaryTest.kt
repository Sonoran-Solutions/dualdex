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
import org.json.JSONObject
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

    private fun settings(
        randomAbilities: Boolean = false,
        optionStyle: Int = 0,
        fairyTypes: Boolean = true
    ): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = HnsChallengeField(observed = true, raw = optionStyle, outOfDomain = false),
        txModeFairyTypes = HnsChallengeField(observed = true, raw = if (fairyTypes) 1 else 0, outOfDomain = false),
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
        stagesObserved: Boolean = true,
        statStages: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0, 0),
        status1: Int = 0,
        statusObserved: Boolean = true,
        volatilesObserved: Boolean = true,
        embargo: Boolean = false,
        transformed: Boolean = false,
        metronomeItemCounter: Int = 0,
        transformedMonSpecies: Int = 0,
        groupDVolatilesObserved: Boolean = volatilesObserved,
        itemVolatilesObserved: Boolean = volatilesObserved,
        volatileNeutralizingGas: Boolean = false,
        volatileFlashFireBoosted: Boolean = false,
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
        selectedGimmick: Int = 0,
        fieldStatusesReadable: Boolean = true,
        fieldStatuses: Int = 0,
        weatherReadable: Boolean = true,
        battleWeather: Int = 0,
        sideStatusesReadable: Boolean = true,
        sideStatuses: Int = 0,
        badgesObserved: Boolean = true,
        observedBattlersCount: Int? = 2,
        itemId: Int? = 0,
        absentBattlerFlags: Int = 0,
        switchInPhaseObserved: Boolean = true,
        switchInEventsSettled: Boolean = true
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
            stagesObserved = stagesObserved,
            statStages = statStages,
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
            groupDVolatilesObserved = groupDVolatilesObserved,
            volatileNeutralizingGas = volatileNeutralizingGas,
            volatileFlashFireBoosted = volatileFlashFireBoosted,
            volatileTransformed = transformed,
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
            selectedGimmickObserved = gimmickObserved,
            selectedGimmick = selectedGimmick,
            gimmickObserved = gimmickObserved,
            activeGimmick = gimmick,
            fieldStatusesReadable = fieldStatusesReadable,
            fieldStatuses = fieldStatuses,
            weatherReadable = weatherReadable,
            battleWeather = battleWeather,
            sideStatusesReadable = sideStatusesReadable,
            sideStatuses = sideStatuses,
            switchInPhaseObserved = switchInPhaseObserved,
            switchInEventsSettled = switchInEventsSettled,
            // These fixtures represent the current 103-value native contract. Dedicated
            // unread-window cases override this flag or use HnsBattlerRuntimeStateTest tuples.
            itemVolatilesObserved = itemVolatilesObserved,
            volatileEmbargo = embargo,
            volatileMetronomeItemCounter = metronomeItemCounter,
            volatileTransformedMonSpecies = transformedMonSpecies
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
        stagesObserved: Boolean = true,
        statStages: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0, 0),
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
        selectedGimmick: Int = 0,
        fieldStatusesReadable: Boolean = true,
        fieldStatuses: Int = 0,
        weatherReadable: Boolean = true,
        battleWeather: Int = 0,
        sideStatusesReadable: Boolean = true,
        sideStatuses: Int = 0,
        statusObserved: Boolean = true,
        status1: Int = 0,
        groupDVolatilesObserved: Boolean = volatilesObserved,
        itemVolatilesObserved: Boolean = volatilesObserved,
        volatileNeutralizingGas: Boolean = false,
        observedBattlersCount: Int? = 2,
        itemId: Int? = 0,
        absentBattlerFlags: Int = 0,
        switchInPhaseObserved: Boolean = true,
        switchInEventsSettled: Boolean = true
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
            stagesObserved = stagesObserved,
            statStages = statStages,
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
            groupDVolatilesObserved = groupDVolatilesObserved,
            volatileNeutralizingGas = volatileNeutralizingGas,
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
            selectedGimmickObserved = gimmickObserved,
            selectedGimmick = selectedGimmick,
            gimmickObserved = gimmickObserved,
            activeGimmick = gimmick,
            fieldStatusesReadable = fieldStatusesReadable,
            fieldStatuses = fieldStatuses,
            weatherReadable = weatherReadable,
            battleWeather = battleWeather,
            sideStatusesReadable = sideStatusesReadable,
            sideStatuses = sideStatuses,
            switchInPhaseObserved = switchInPhaseObserved,
            switchInEventsSettled = switchInEventsSettled,
            itemVolatilesObserved = itemVolatilesObserved,
            volatileEmbargo = false,
            volatileMetronomeItemCounter = 0,
            volatileTransformedMonSpecies = 0
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
        optionStyle: Int = 0,
        fairyTypes: Boolean = true
    ): CalcRequestOutcome = CalcRequestBoundary.build(
        profile = heartAndSoul,
        trust = trust,
        request = request,
        challengeSettings = settings(randomAbilities, optionStyle, fairyTypes),
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

        val guts = build(trust, claimedDefaults,
            playerObservation(abilityId = 62, abilityName = "Guts", status1 = 0x10), enemyObservation(),
            randomAbilities = true) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("observed physical Guts with authoritative status1 must use the modeled Attack stage")
        assertEquals(62, guts.request.attacker.abilityId)
        assertFalse(guts.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))

        val defenderHarmful = build(trust, goldenARequest(move = "Ember"),
            playerObservation(abilityId = 9, abilityName = "Static"),
            enemyObservation(abilityId = 47, abilityName = "Thick Fat"), randomAbilities = true)
        val estimate = readyOf(defenderHarmful, "known relevant Thick Fat can be caveated")
        assertEquals(listOf("Foe: Thick Fat"), estimate.verdict.ignoredMechanics.map { it.presentationLine })
    }

    @Test
    fun `live stat writers use exact attacker and defender stages and missing stages fail closed`() {
        val trust = trustFor(exactSha)
        val attackerStages = listOf(0, 1, 0, 0, 0, 0, 0, 0)
        val speedStages = listOf(0, 0, 1, 0, 0, 0, 0, 0)
        val defenseStages = listOf(0, 0, 1, 0, 0, 0, 0, 0)

        val speedBoost = readyOf(
            build(trust, goldenARequest(),
                playerObservation(abilityId = 3, abilityName = "Speed Boost", statStages = speedStages),
                enemyObservation()),
            "Speed Boost's already observed attacker stage is the damage input"
        )
        assertEquals(speedStages, speedBoost.request.hnsLiveBattleState?.attackerStatStages)
        assertTrue(speedBoost.verdict.ignoredMechanics.isEmpty())

        val intimidate = readyOf(
            build(trust, goldenARequest(), playerObservation(statStages = attackerStages),
                enemyObservation(abilityId = 22, abilityName = "Intimidate", statStages = defenseStages)),
            "Intimidate's attack-stage mutation is read from the active attacker"
        )
        assertEquals(attackerStages, intimidate.request.hnsLiveBattleState?.attackerStatStages)

        for ((id, name) in listOf(80 to "Steadfast", 192 to "Stamina")) {
            val stages = if (id == 80) speedStages else defenseStages
            val ready = readyOf(
                build(trust, goldenARequest(), playerObservation(),
                    enemyObservation(abilityId = id, abilityName = name, statStages = stages)),
                "$name's defender stage is read from the active battler"
            )
            assertEquals(stages, ready.request.hnsLiveBattleState?.defenderStatStages)
            assertTrue(ready.verdict.ignoredMechanics.isEmpty())
        }

        val unread = refusedOf(
            build(trust, goldenARequest(),
                playerObservation(abilityId = 3, abilityName = "Speed Boost", stagesObserved = false),
                enemyObservation()),
            "a live ability cannot substitute for a missing stage read"
        )
        assertTrue(unread.verdict.hnsAbilityDecisions.any {
            it.abilityId == 3 && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
        })
        assertTrue(unread.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
    }

    @Test
    fun `Group B ability proofs wait for the settled switch-in event phase`() {
        val trust = trustFor(exactSha)
        val neutralStages = List(8) { 0 }
        val intimidateRequest = goldenARequest().copy(
            defender = goldenARequest().defender.copy(ability = "Intimidate", abilityId = 22)
        )
        val pendingIntimidate = refusedOf(
            build(trust, intimidateRequest,
                playerObservation(statStages = neutralStages, switchInEventsSettled = false),
                enemyObservation(abilityId = 22, abilityName = "Intimidate", switchInEventsSettled = false)),
            "neutral pre-drop stages do not prove Intimidate's entry event has run"
        )
        assertTrue(pendingIntimidate.verdict.hnsAbilityDecisions.any {
            it.abilityId == 22 && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
        })

        val settledIntimidate = readyOf(
            build(trust, intimidateRequest,
                playerObservation(statStages = neutralStages),
                enemyObservation(abilityId = 22, abilityName = "Intimidate")),
            "settled switch-in events make the exact live stage array authoritative"
        )
        assertEquals(true, settledIntimidate.request.hnsLiveBattleState?.switchInEventsSettled)

        for ((id, name) in listOf(128 to "Defiant", 172 to "Competitive")) {
            val request = goldenARequest().copy(
                defender = goldenARequest().defender.copy(ability = name, abilityId = id)
            )
            val unsettled = refusedOf(
                build(trust, request,
                    playerObservation(statStages = neutralStages, switchInEventsSettled = false),
                    enemyObservation(abilityId = id, abilityName = name,
                        statStages = neutralStages, switchInEventsSettled = false)),
                "$name can still respond to a pending Sticky Web stat drop during switch-in"
            )
            assertTrue(unsettled.verdict.hnsAbilityDecisions.any {
                it.abilityId == id && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
            })

            val settled = readyOf(
                build(trust, request,
                    playerObservation(statStages = neutralStages),
                    enemyObservation(abilityId = id, abilityName = name,
                        statStages = listOf(2, 0, 0, 0, 0, 0, 0, 0))),
                "$name clears only after the entry event pipeline has settled"
            )
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                settled.verdict.hnsAbilityDecisions.single { it.abilityId == id }.relevance)
        }

        val drizzleRequest = goldenARequest().copy(
            attacker = goldenARequest().attacker.copy(ability = "Drizzle", abilityId = 2)
        )
        val pendingDrizzle = refusedOf(
            build(trust, drizzleRequest,
                playerObservation(abilityId = 2, abilityName = "Drizzle", battleWeather = 0,
                    switchInEventsSettled = false),
                enemyObservation(battleWeather = 0, switchInEventsSettled = false)),
            "a replacement Drizzle battler with clear weather before the entry script is not a neutral proof"
        )
        assertTrue(pendingDrizzle.verdict.hnsAbilityDecisions.any {
            it.abilityId == 2 && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
        })
        val settledDrizzle = readyOf(
            build(trust, drizzleRequest,
                playerObservation(abilityId = 2, abilityName = "Drizzle", battleWeather = 1),
                enemyObservation(battleWeather = 1)),
            "settled Drizzle with observed ordinary Rain is represented by the live weather"
        )
        assertEquals("Rain", settledDrizzle.request.field.weather)

        val traceRequest = goldenARequest().copy(
            defender = goldenARequest().defender.copy(ability = "Trace", abilityId = 36)
        )
        val pendingTrace = refusedOf(
            build(trust, traceRequest, playerObservation(switchInEventsSettled = false),
                enemyObservation(abilityId = 36, abilityName = "Trace", switchInEventsSettled = false)),
            "Trace's current identity can still be replaced by its pending switch-in script"
        )
        assertTrue(pendingTrace.verdict.hnsAbilityDecisions.any {
            it.abilityId == 36 && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
        })
        readyOf(
            build(trust, traceRequest, playerObservation(),
                enemyObservation(abilityId = 36, abilityName = "Trace")),
            "settled switch-in events make the current Trace identity authoritative"
        )

        val unreadPhase = refusedOf(
            build(trust, traceRequest,
                playerObservation(switchInPhaseObserved = false),
                enemyObservation(abilityId = 36, abilityName = "Trace")),
            "an unread event phase cannot clear a Trace ability proof"
        )
        assertTrue(unreadPhase.verdict.hnsAbilityDecisions.any {
            it.abilityId == 36 && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
        })
        val unreadNeutral = readyOf(
            build(trust, goldenARequest(), playerObservation(switchInPhaseObserved = false), enemyObservation()),
            "unread switch-in state does not invent a phase value for otherwise modelled abilities"
        )
        assertNull(unreadNeutral.request.hnsLiveBattleState?.switchInEventsSettled)
    }

    @Test
    fun `settled stage writer does not duplicate an unsupported move blocker`() {
        val trust = trustFor(exactSha)
        val refused = refusedOf(
            build(trust, goldenARequest(move = "Explosion"), playerObservation(),
                enemyObservation(abilityId = 22, abilityName = "Intimidate"), randomAbilities = true),
            "the unsupported move remains independently refused"
        )
        assertTrue(refused.verdict.blockingLimitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            refused.verdict.hnsAbilityDecisions.single { it.abilityId == 22 }.relevance)
    }

    @Test
    fun `defender Speed Boost leaves Analytic turn order to the current action authority`() {
        val trust = trustFor(exactSha)
        val analytic = playerObservation(abilityId = 148, abilityName = "Analytic").let { observation ->
            observation.copy(state = observation.state.copy(
                analyticTurnOrderObserved = true,
                analyticTurnOrder = 1,
                analyticCurrentMove = 33
            ))
        }
        val ready = readyOf(
            build(trust, goldenARequest(), analytic,
                enemyObservation(abilityId = 3, abilityName = "Speed Boost"), randomAbilities = true),
            "the observed current-action Analytic order owns the Speed Boost consequence"
        )
        assertEquals("LAST_TO_MOVE", JSONObject(buildCalcRequestJson(ready.request))
            .getJSONObject("attacker").getString("hnsAnalyticTurnOrder"))
        assertTrue(ready.verdict.hnsAbilityDecisions.any {
            it.abilityId == 3 && it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
        })
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
    }

    @Test
    fun `weather speed ability does not duplicate unsupported move refusal`() {
        val refused = refusedOf(
            build(trustFor(exactSha), goldenARequest(move = "Explosion"), playerObservation(),
                enemyObservation(abilityId = 34, abilityName = "Chlorophyll"), randomAbilities = true),
            "the unsupported move remains independently refused"
        )
        assertTrue(refused.verdict.blockingLimitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
        assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            refused.verdict.hnsAbilityDecisions.single { it.abilityId == 34 }.relevance)
    }

    @Test
    fun `live current types override the species default for type rewriting abilities`() {
        val trust = trustFor(exactSha)
        val request = goldenARequest().copy(
            defender = goldenARequest().defender.copy(ability = "Color Change", abilityId = 16)
        )
        val ready = readyOf(
            build(trust, request, playerObservation(),
                enemyObservation(abilityId = 16, abilityName = "Color Change", types = listOf(6))),
            "the observed effective Rock type overrides Pidgey's static Normal/Flying types"
        )
        val liveJson = buildCalcRequestJson(ready.request)
        assertTrue(liveJson.contains("\"types\":[\"Rock\"]"))
        assertTrue(ready.verdict.ignoredMechanics.isEmpty())

        val missingTypes = refusedOf(
            build(trust, request, playerObservation(),
                enemyObservation(abilityId = 16, abilityName = "Color Change", types = emptyList())),
            "species types cannot substitute for unread current types"
        )
        assertTrue(missingTypes.verdict.hnsAbilityDecisions.any {
            it.abilityId == 16 && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
        })
    }

    @Test
    fun `Protean and Libero clear only when the current type proves no move-time rewrite is pending`() {
        val trust = trustFor(exactSha)
        for ((id, name) in listOf(168 to "Protean", 236 to "Libero")) {
            val request = goldenARequest().copy(
                attacker = goldenARequest().attacker.copy(ability = name, abilityId = id)
            )
            val sameType = readyOf(
                build(trust, request, playerObservation(abilityId = id, abilityName = name, types = listOf(1)),
                    enemyObservation()),
                "$name cannot change an already-Normal monotype before Tackle"
            )
            assertEquals(listOf("Normal"), sameType.request.hnsLiveBattleState?.attackerTypes)
            assertTrue(sameType.verdict.ignoredMechanics.isEmpty())

            val pendingTypeChange = refusedOf(
                build(trust, request, playerObservation(abilityId = id, abilityName = name, types = listOf(13)),
                    enemyObservation()),
                "$name may retype the current Grass battler to Normal before Tackle"
            )
            assertTrue(pendingTypeChange.verdict.hnsAbilityDecisions.any {
                it.abilityId == id && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
            })
            assertTrue(pendingTypeChange.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        }
    }

    @Test
    fun `live weather setter and suppression use the effective weather authority`() {
        val trust = trustFor(exactSha)
        val rain = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_RAIN_NORMAL
        val sun = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN_NORMAL
        val drizzleRequest = goldenARequest(move = "Water Gun").copy(
            attacker = goldenARequest(move = "Water Gun").attacker.copy(ability = "Drizzle", abilityId = 2)
        )
        val ready = readyOf(
            build(trust, drizzleRequest,
                playerObservation(abilityId = 2, abilityName = "Drizzle", battleWeather = rain),
                enemyObservation(battleWeather = rain)),
            "ordinary unsuppressed Rain is consumed by the damage engine"
        )
        assertEquals("Rain", ready.request.field.weather)
        assertTrue(ready.verdict.ignoredMechanics.isEmpty())

        val suppressed = readyOf(
            build(trust, drizzleRequest,
                playerObservation(abilityId = 2, abilityName = "Drizzle", battleWeather = rain),
                enemyObservation(abilityId = 13, abilityName = "Cloud Nine", battleWeather = rain)),
            "Cloud Nine suppresses the effective weather consumed by the current hit"
        )
        assertNull(suppressed.request.field.weather)
        assertTrue(suppressed.verdict.ignoredMechanics.isEmpty())

        val airLock = readyOf(build(trust, goldenARequest(move = "Flamethrower"),
            playerObservation(battleWeather = sun),
            enemyObservation(abilityId = 76, abilityName = "Air Lock", battleWeather = sun)),
            "Air Lock suppresses Sun before the selected Fire hit")
        assertNull(airLock.request.field.weather)

        val solarPower = readyOf(build(trust,
            goldenARequest(move = "Psychic").copy(attacker = goldenARequest(move = "Psychic").attacker.copy(
                ability = "Solar Power", abilityId = 94)),
            playerObservation(abilityId = 94, abilityName = "Solar Power", battleWeather = sun),
            enemyObservation(abilityId = 13, abilityName = "Cloud Nine", battleWeather = sun)),
            "Cloud Nine suppresses the Sun operand consumed by Solar Power")
        assertNull(solarPower.request.field.weather)
        assertTrue(solarPower.verdict.hnsAbilityDecisions.any {
            it.abilityId == 94 && it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
        })

        val flowerGift = readyOf(build(trust, goldenARequest(move = "Flamethrower").copy(
            attacker = goldenARequest(move = "Flamethrower").attacker.copy(ability = "Flower Gift", abilityId = 122)),
            playerObservation(abilityId = 122, abilityName = "Flower Gift", battleWeather = sun),
            enemyObservation(abilityId = 76, abilityName = "Air Lock", battleWeather = sun)),
            "Air Lock suppresses the Sun operand consumed by Flower Gift")
        assertNull(flowerGift.request.field.weather)

        val noWeather = readyOf(build(trust, goldenARequest(),
            playerObservation(abilityId = 13, abilityName = "Cloud Nine", battleWeather = 0),
            enemyObservation(battleWeather = 0)),
            "no-weather control leaves the selected request clear")
        assertNull(noWeather.request.field.weather)
    }

    @Test
    fun `held Terrain Seed and Berserk Gene stay blocked until the current item is consumed`() {
        val trust = trustFor(exactSha)
        val electricTerrain = 1 shl 8
        val pendingSeed = refusedOf(
            build(trust, goldenARequest(move = "Thunder Shock"), playerObservation(fieldStatuses = electricTerrain,
                switchInEventsSettled = false),
                enemyObservation(itemId = 451, fieldStatuses = electricTerrain,
                    statStages = listOf(0, 0, 1, 0, 0, 0, 0, 0), switchInEventsSettled = false)),
            "matching terrain and submax Defense cannot prove a held Seed already activated"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, pendingSeed.verdict.hnsItemDecisions.single().relevance)
        assertTrue(pendingSeed.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))

        val consumedSeed = readyOf(
            build(trust, goldenARequest(move = "Thunder Shock"), playerObservation(fieldStatuses = electricTerrain),
                enemyObservation(itemId = 0, fieldStatuses = electricTerrain,
                    statStages = listOf(0, 0, 1, 0, 0, 0, 0, 0))),
            "ITEM_NONE after activation is authoritative and the live stage is already included"
        )
        assertFalse(consumedSeed.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(consumedSeed.verdict.ignoredMechanics.none { it is IgnoredCalcMechanic.Field &&
            it.decision.status == HnsFieldStatus.ELECTRIC_TERRAIN })

        val unreadSeed = refusedOf(
            build(trust, goldenARequest(), playerObservation(),
                enemyObservation(itemId = 451, fieldStatuses = electricTerrain, stagesObserved = false)),
            "a held Electric Seed remains unknown when its stage array is unread"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, unreadSeed.verdict.hnsItemDecisions.single().relevance)
        assertTrue(unreadSeed.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))

        val pendingGene = refusedOf(
            build(trust, goldenARequest(),
                playerObservation(itemId = 798, switchInEventsSettled = false),
                enemyObservation(switchInEventsSettled = false)),
            "a held Berserk Gene during switch-in may still apply its Attack boost"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, pendingGene.verdict.hnsItemDecisions.single().relevance)
        assertTrue(pendingGene.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))

        val consumedGene = readyOf(
            build(trust, goldenARequest(),
                playerObservation(itemId = 0, statStages = listOf(2, 0, 0, 0, 0, 0, 0, 0)), enemyObservation()),
            "ITEM_NONE after Berserk Gene activation leaves its Attack stage in live state"
        )
        assertFalse(consumedGene.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))

        val pendingLiechi = refusedOf(
            build(trust, goldenARequest(),
                playerObservation(itemId = 567, hp = 4, maxHp = 20,
                    statStages = List(8) { 0 }, switchInEventsSettled = false),
                enemyObservation(switchInEventsSettled = false)),
            "a threshold Liechi Berry can activate after entry hazards before switch-in settles"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN, pendingLiechi.verdict.hnsItemDecisions.single().relevance)
        assertTrue(pendingLiechi.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))

        val consumedLiechi = readyOf(
            build(trust, goldenARequest(),
                playerObservation(itemId = 0, hp = 4, maxHp = 20,
                    statStages = listOf(1, 0, 0, 0, 0, 0, 0, 0)),
                enemyObservation()),
            "settled switch-in with consumed Liechi uses its live Attack stage"
        )
        assertFalse(consumedLiechi.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals(1, consumedLiechi.request.hnsLiveBattleState?.attackerStatStages?.get(0))
    }

    @Test
    fun `weather and terrain setter identities clear while independent arithmetic limits remain`() {
        val trust = trustFor(exactSha)
        data class Candidate(
            val id: Int,
            val name: String,
            val move: String = "Tackle",
            val weather: Int = 0,
            val field: Int = 0
        )
        val candidates = listOf(
            Candidate(45, "Sand Stream", weather = 1 shl 5),
            Candidate(117, "Snow Warning", weather = 1 shl 7),
            Candidate(245, "Sand Spit"),
            Candidate(189, "Primordial Sea", weather = 1 shl 1),
            Candidate(190, "Desolate Land", weather = 1 shl 4),
            Candidate(191, "Delta Stream", weather = 1 shl 9),
            Candidate(226, "Electric Surge", "Thunder Shock", field = 1 shl 8),
            Candidate(227, "Psychic Surge", "Psybeam", field = 1 shl 9),
            Candidate(228, "Misty Surge", "Dragon Breath", field = 1 shl 10),
            Candidate(229, "Grassy Surge", "Vine Whip", field = 1 shl 6),
            Candidate(269, "Seed Sower", "Vine Whip", field = 1 shl 6),
        )
        for (candidate in candidates) {
            val request = goldenARequest(move = candidate.move).copy(
                attacker = goldenARequest(move = candidate.move).attacker.copy(
                    ability = candidate.name, abilityId = candidate.id
                )
            )
            val outcome = build(trust, request,
                playerObservation(abilityId = candidate.id, abilityName = candidate.name,
                    fieldStatuses = candidate.field, battleWeather = candidate.weather),
                enemyObservation(fieldStatuses = candidate.field, battleWeather = candidate.weather))
            if (candidate.id == 45 || candidate.id == 117) {
                val refused = refusedOf(outcome, "${candidate.name} weather arithmetic remains unsupported")
                assertTrue("${candidate.name} setter is captured by live weather", refused.verdict.hnsAbilityDecisions.any {
                    it.abilityId == candidate.id && it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
                })
                assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_LIVE_WEATHER_NOT_MODELLED))
                assertFalse(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
            } else if (candidate.id in setOf(226, 227, 228, 229, 269, 245)) {
                val ready = readyOf(outcome, "${candidate.name} result is represented by the observed field state")
                assertTrue(ready.verdict.hnsAbilityDecisions.any {
                    it.abilityId == candidate.id && it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
                })
            } else {
                val refused = refusedOf(outcome, "${candidate.name} direct weather mechanics remain unresolved")
                assertTrue("${candidate.name} has an independent unresolved mechanic", refused.verdict.hnsAbilityDecisions.any {
                    it.abilityId == candidate.id && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
                })
            }
        }

        val observedSun = readyOf(build(
            trust, goldenARequest("Tackle"),
            playerObservation(abilityId = 288, abilityName = "Orichalcum Pulse", battleWeather = 1 shl 3),
            enemyObservation(battleWeather = 1 shl 3)
        ), "Orichalcum Pulse's weather setter is represented by the observed raw Sun word")
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            observedSun.verdict.hnsAbilityDecisions.single { it.abilityId == 288 }.relevance)
        val observedSunJson = JSONObject(buildCalcRequestJson(observedSun.request))
        assertEquals(1 shl 3, observedSunJson.getJSONObject("field").getInt("hnsWeatherWord"))

        for ((id, name) in listOf(277 to "Wind Power", 280 to "Electromorphosis")) {
            val request = goldenARequest(move = "Thunder Shock").copy(
                attacker = goldenARequest(move = "Thunder Shock").attacker.copy(ability = name, abilityId = id)
            )
            val charged = readyOf(build(trust, request,
                playerObservation(abilityId = id, abilityName = name, chargeTimer = 1), enemyObservation()),
                "$name writes the same observed Charge volatile")
            assertEquals(1, JSONObject(buildCalcRequestJson(charged.request))
                .getJSONObject("attacker").getInt("hnsChargeTimer"))
            assertFalse(charged.verdict.isCaveatedEstimate)
        }

        val boosterRequest = goldenARequest().copy(
            attacker = goldenARequest().attacker.copy(ability = "Protosynthesis", abilityId = 281)
        )
        val booster = readyOf(build(trust, boosterRequest,
            playerObservation(abilityId = 281, abilityName = "Protosynthesis", itemId = 764,
                battleWeather = 1 shl 3), enemyObservation(battleWeather = 1 shl 3)),
            "natural Sun leaves Booster Energy held while the existing live Paradox state owns the damage modifier")
        assertTrue(booster.verdict.hnsItemDecisions.any {
            it.itemId == 764 && it.rule == "booster_energy_payload_modelled" &&
                it.relevance == HnsItemRequestRelevance.MODELLED
        })
        assertFalse(booster.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(booster.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `effective runtime ability ID wins over a mismatched declaration identity`() {
        val trust = trustFor(exactSha)
        val base = playerObservation(abilityId = 9, abilityName = "Static")
        val replacement = base.copy(
            abilityIdentity = DeclaredAbility.Declared(65, "Overgrow")
        )
        val ready = readyOf(
            build(trust, goldenARequest(), replacement, enemyObservation()),
            "the slot-matched current effective ability ID is authoritative after a replacement"
        )
        assertEquals(9, ready.request.attacker.abilityId)
        assertEquals("Static", ready.request.attacker.ability)

        val unreadAbility = replacement.copy(
            state = replacement.state.copy(abilityId = null, abilityOutOfDomain = true)
        )
        val refused = refusedOf(
            build(trust, goldenARequest(), unreadAbility, enemyObservation()),
            "a copied/default identity cannot replace an unread current ability ID"
        )
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
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
    fun `active Wonder Room field status is named and neutralized`() {
        // STATUS_FIELD_WONDER_ROOM (bit 2) swaps Defense/Sp.Def. The named field bit is removed
        // only after policy inspection, yielding a caveated estimate from the neutral request.
        val ready = readyOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(fieldStatuses = 1 shl 2),
                enemyObservation(fieldStatuses = 1 shl 2)),
            "known Wonder Room can be explicitly ignored"
        )
        assertEquals(listOf("Field: Wonder Room"), ready.verdict.ignoredMechanics.map { it.presentationLine })
        assertEquals(0, ready.request.hnsLiveBattleState?.fieldStatuses)
    }

    @Test
    fun `active terrain field status is retained for exact supported moves`() {
        val exact = readyOf(
            build(trustFor(exactSha), goldenARequest(move = "Vine Whip"),
                playerObservation(fieldStatuses = 1 shl 6), enemyObservation(fieldStatuses = 1 shl 6)),
            "the direct Grass terrain modifier is exact"
        )
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(exact, HnsFieldStatus.GRASSY_TERRAIN).relevance)
        assertEquals(1 shl 6, exact.request.hnsLiveBattleState?.fieldStatuses)
        assertTrue(exact.verdict.ignoredMechanics.none { it is IgnoredCalcMechanic.Field })
        val ready = readyOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(fieldStatuses = 1 shl 6),
                enemyObservation(fieldStatuses = 1 shl 6)),
            "Grassy Terrain cannot change Tackle"
        )
        assertEquals("grassy_terrain_no_unmodelled_consequence", ready.verdict.hnsFieldDecisions.single().rule)
    }

    @Test
    fun `Ion Deluge remains blocked when Gravity is irrelevant to the rewritten type`() {
        // Both Ion Deluge and Gravity (bit 5) set. The single effective type proves Gravity
        // irrelevant to Electric, but the active Ion Deluge dynamic-type blocker remains.
        val outcome = refusedOf(
            build(
                trustFor(exactSha), goldenARequest(),
                playerObservation(fieldStatuses = (1 shl 10) or (1 shl 5)),
                enemyObservation(fieldStatuses = (1 shl 10) or (1 shl 5))
            ),
            "the active Ion Deluge path stays refused"
        )
        assertTrue(outcome.verdict.limitations.contains(CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED))
        assertEquals(HnsFieldRequestRelevance.PROVEN_IRRELEVANT,
            fieldDecision(outcome, HnsFieldStatus.GRAVITY).relevance)
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
    fun `active Charge with an Electric move reaches the exact pipeline`() {
        val ready = readyOf(build(trustFor(exactSha), goldenARequest(move = "Thunder Shock"),
            playerObservation(chargeTimer = 2), enemyObservation()), "observed Charge is exact")
        assertEquals(2, JSONObject(buildCalcRequestJson(ready.request))
            .getJSONObject("attacker").getInt("hnsChargeTimer"))
        assertFalse(ready.verdict.isCaveatedEstimate)
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
    fun `known relevant Adaptability calculates exact STAB after boundary identity wins`() {
        val ready = readyOf(
            build(
                trustFor(exactSha),
                goldenARequest(move = "Razor Leaf"),
                playerObservation(abilityId = 91, abilityName = "Adaptability"),
                enemyObservation()
            ),
            "known relevant Adaptability should use the exact live effective type and typings"
        )
        assertTrue(ready.verdict.ignoredMechanics.isEmpty())
        assertEquals("Adaptability", ready.request.attacker.ability)
        assertEquals(91, ready.request.attacker.abilityId)
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
    fun `attacker Truant refuses because unread execution state can invalidate the move`() {
        val outcome = refusedOf(build(trustFor(exactSha), goldenARequest(),
            playerObservation(abilityId = 54, abilityName = "Truant"),
            enemyObservation(abilityId = 308, abilityName = "Tera Shell"), randomAbilities = true),
            "Truant cannot authorize a potentially unexecutable move")
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            outcome.verdict.hnsAbilityDecisions.first { it.abilityId == 54 }.relevance)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            outcome.verdict.hnsAbilityDecisions.first { it.abilityId == 308 }.relevance)
        assertTrue(com.dualdex.battle.DamageBlockerPresentation.from(outcome.verdict, false)
            .any { it.headline.contains("Truant") && it.headline.contains("execution") })
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
        ) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("live numeric Terapagos identity permits the existing named Tera Shell caveat")
        assertTrue(fullHp.verdict.isCaveatedEstimate)
        assertTrue(fullHp.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            fullHp.verdict.hnsAbilityDecisions.single { it.abilityId == 308 }.relevance)

        val belowFull = build(
            trust, terapagosRequest, playerObservation(),
            enemyObservation(speciesId = 1432, abilityId = 308, abilityName = "Tera Shell",
                types = listOf(1), hp = 14, maxHp = 15), randomAbilities = true
        ) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("numeric live Terapagos plus below-full HP proves this request")
        assertFalse("below-full proof must clear only the Tera Shell blocker",
            belowFull.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertFalse(belowFull.verdict.limitations.contains(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA))
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

    @Test
    fun `physical Guts uses the observed status1 word and forwards it to QuickJS`() {
        val ready = build(
            trustFor(exactSha), goldenARequest(),
            playerObservation(abilityId = 62, abilityName = "Guts", status1 = 0x10),
            enemyObservation(), randomAbilities = true
        ) as? CalcRequestOutcome.Ready
            ?: throw AssertionError("physical Guts with an observed status1 must be modeled")

        assertEquals(62, ready.request.attacker.abilityId)
        assertEquals(0x10, ready.request.hnsLiveBattleState?.attackerStatus1)
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        val serialized = JSONObject(buildCalcRequestJson(ready.request))
        assertEquals(0x10, serialized.getJSONObject("attacker").getInt("status1"))
    }

    @Test
    fun `Toxic Boost special poison control passes production policy and reaches calculator`() {
        val ready = readyOf(
            build(
                trustFor(exactSha), goldenARequest(move = "Psychic"),
                playerObservation(abilityId = 137, abilityName = "Toxic Boost", status1 = 0x08),
                enemyObservation(), randomAbilities = true
            ),
            "authoritative Special category makes Toxic Boost irrelevant while poison remains a supported live status"
        )

        assertEquals("Special", ready.request.moveOverride?.category)
        assertEquals(0x08, ready.request.hnsLiveBattleState?.attackerStatus1)
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertEquals(
            HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            ready.verdict.hnsAbilityDecisions.single { it.abilityId == 137 }.relevance
        )

        var engineRequest: DamageCalculationRequest? = null
        val response = CalcAuthorizedExecution.calculate(ready.verdict) { request ->
            engineRequest = request
            val serialized = JSONObject(buildCalcRequestJson(request))
            assertEquals(0x08, serialized.getJSONObject("attacker").getInt("status1"))
            assertEquals(
                "Special",
                serialized.getJSONObject("move").getJSONObject("overrides").getString("category")
            )
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }

        assertTrue("production-authorized execution should reach the calculator", response.success)
        assertNotNull(engineRequest)
    }

    @Test
    fun `Guts missing status1 and special with active status remain fail closed`() {
        val trust = trustFor(exactSha)
        val unread = refusedOf(build(
            trust, goldenARequest(),
            playerObservation(abilityId = 62, abilityName = "Guts", statusObserved = false),
            enemyObservation(), randomAbilities = true
        ), "Guts needs an observed live status1 on physical moves")
        assertTrue(unread.verdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))
        assertTrue(unread.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
        assertEquals(
            com.dualdex.pokemon.hns.HnsAbilityCategory.MODELLED_HNS_CONDITIONAL,
            unread.verdict.hnsAbilityDecisions.single().globalCategory
        )

        val special = refusedOf(build(
            trust, goldenARequest(move = "Psychic"),
            playerObservation(abilityId = 62, abilityName = "Guts", status1 = 0x10),
            enemyObservation(), randomAbilities = true
        ), "a special move does not get Guts, and non-neutral status remains outside the production subset")
        assertTrue(special.verdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))
        assertFalse(special.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
        assertFalse(special.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `Hustle is modeled for physical hits and proven irrelevant for special hits`() {
        val trust = trustFor(exactSha)
        val physical = readyOf(build(
            trust, goldenARequest(),
            playerObservation(abilityId = 55, abilityName = "Hustle"), enemyObservation(), randomAbilities = true
        ), "physical Hustle has an authoritative category")
        assertFalse(physical.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))

        val special = readyOf(build(
            trust, goldenARequest(move = "Psychic"),
            playerObservation(abilityId = 55, abilityName = "Hustle"), enemyObservation(), randomAbilities = true
        ), "special Hustle is proven irrelevant")
        assertFalse(special.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
        assertFalse(special.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `TYPE_BASED Ghost and Dark categories drive Hustle and Guts authorization`() {
        val trust = trustFor(exactSha)
        val hustleGhost = readyOf(build(
            trust, goldenARequest(move = "Shadow Ball"),
            playerObservation(abilityId = 55, abilityName = "Hustle"), enemyObservation(),
            randomAbilities = true, optionStyle = 1
        ), "TYPE_BASED Ghost is Special, so Hustle is irrelevant")
        assertEquals("Special", hustleGhost.request.moveOverride?.category)
        assertEquals(
            com.dualdex.calculator.HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            hustleGhost.verdict.hnsAbilityDecisions.single().relevance
        )

        val hustleDark = readyOf(build(
            trust, goldenARequest(move = "Crunch"),
            playerObservation(abilityId = 55, abilityName = "Hustle"), enemyObservation(),
            randomAbilities = true, optionStyle = 1
        ), "TYPE_BASED Dark is Physical, so Hustle is modelled")
        assertEquals("Physical", hustleDark.request.moveOverride?.category)
        assertEquals(
            com.dualdex.calculator.HnsAbilityRequestRelevance.RELEVANT,
            hustleDark.verdict.hnsAbilityDecisions.single().relevance
        )

        val gutsGhost = build(
            trust, goldenARequest(move = "Shadow Ball"),
            playerObservation(abilityId = 62, abilityName = "Guts", status1 = 0x10), enemyObservation(),
            randomAbilities = true, optionStyle = 1
        )
        val ghostVerdict = when (gutsGhost) {
            is CalcRequestOutcome.Ready -> gutsGhost.verdict
            is CalcRequestOutcome.Refused -> gutsGhost.verdict
        }
        assertTrue(ghostVerdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))
        assertEquals(
            com.dualdex.calculator.HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            ghostVerdict.hnsAbilityDecisions.single().relevance
        )

        val gutsDark = readyOf(build(
            trust, goldenARequest(move = "Crunch"),
            playerObservation(abilityId = 62, abilityName = "Guts", status1 = 0x10), enemyObservation(),
            randomAbilities = true, optionStyle = 1
        ), "TYPE_BASED Dark is Physical, so statused Guts is modelled")
        assertEquals("Physical", gutsDark.request.moveOverride?.category)
        assertFalse(gutsDark.verdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))
    }

    @Test
    fun `Solar Power Defeatist and Fur Coat branches use live authority at authorized execution`() {
        val trust = trustFor(exactSha)
        val sun = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN_NORMAL

        val solar = readyOf(build(
            trust,
            goldenARequest("Psychic").copy(
                attacker = goldenARequest("Psychic").attacker.copy(curHP = 1),
                field = CalcFieldInput(weather = "Rain"),
                moveOverride = CalcMoveOverride(basePower = 40, type = "Normal", category = "Physical")
            ),
            playerObservation(abilityId = 94, abilityName = "Solar Power", hp = 10, maxHp = 20, battleWeather = sun),
            enemyObservation(battleWeather = sun), randomAbilities = true
        ), "live Solar Power, ordinary Sun, and final Special category must be admitted")
        assertEquals(94, solar.request.attacker.abilityId)
        assertEquals("Sun", solar.request.field.weather)
        assertEquals("Special", solar.request.moveOverride?.category)
        assertEquals("Psychic", solar.request.moveOverride?.type)
        assertEquals(10, solar.request.hnsLiveBattleState?.attackerHp)
        var solarReached = false
        val solarResult = CalcAuthorizedExecution.calculate(solar.verdict) { request ->
            solarReached = true
            val json = JSONObject(buildCalcRequestJson(request))
            assertEquals("Solar Power", json.getJSONObject("attacker").getString("ability"))
            assertEquals("Sun", json.getJSONObject("field").getString("weather"))
            assertEquals("Psychic", json.getJSONObject("move").getJSONObject("overrides").getString("type"))
            assertEquals("Special", json.getJSONObject("move").getJSONObject("overrides").getString("category"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }
        assertTrue(solarResult.success && solarReached)

        val defeatistRequest = goldenARequest("Tackle").copy(
            attacker = goldenARequest("Tackle").attacker.copy(curHP = 20)
        )
        val defeatist = readyOf(build(
            trust, defeatistRequest,
            playerObservation(abilityId = 129, abilityName = "Defeatist", hp = 10, maxHp = 20),
            enemyObservation(), randomAbilities = true
        ), "live HP at floor(maxHP/2) must activate Defeatist despite caller HP")
        assertEquals(10, defeatist.request.hnsLiveBattleState?.attackerHp)
        assertEquals(20, defeatist.request.hnsLiveBattleState?.attackerMaxHp)
        var defeatistReached = false
        CalcAuthorizedExecution.calculate(defeatist.verdict) { request ->
            defeatistReached = true
            assertEquals(10, JSONObject(buildCalcRequestJson(request)).getJSONObject("attacker").getInt("hp"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }
        assertTrue(defeatistReached)

        val defeatistAboveHalf = readyOf(build(
            trust,
            goldenARequest("Psychic").copy(attacker = goldenARequest("Psychic").attacker.copy(curHP = 1)),
            playerObservation(abilityId = 129, abilityName = "Defeatist", hp = 11, maxHp = 20),
            enemyObservation(), randomAbilities = true
        ), "live HP above floor(maxHP/2) must prove Defeatist inactive despite caller HP")
        assertEquals(11, defeatistAboveHalf.request.hnsLiveBattleState?.attackerHp)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            defeatistAboveHalf.verdict.hnsAbilityDecisions.single { it.abilityId == 129 }.relevance)

        val furCoatPhysical = readyOf(build(
            trust, goldenARequest("Tackle"), playerObservation(),
            enemyObservation(abilityId = 169, abilityName = "Fur Coat"), randomAbilities = true
        ), "live defender Fur Coat and ordinary Physical final category must be admitted")
        assertEquals(169, furCoatPhysical.request.defender.abilityId)
        assertEquals("Physical", furCoatPhysical.request.moveOverride?.category)
        val physicalExecution = CalcAuthorizedExecution.calculate(furCoatPhysical.verdict) { request ->
            val json = JSONObject(buildCalcRequestJson(request))
            assertEquals("Fur Coat", json.getJSONObject("defender").getString("ability"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }
        assertTrue(physicalExecution.success)

        val callerFurCoat = goldenARequest("Tackle").copy(
            defender = goldenARequest("Tackle").defender.copy(ability = "Fur Coat", abilityId = 169)
        )
        val liveNonFurCoat = readyOf(build(
            trust, callerFurCoat, playerObservation(), enemyObservation(), randomAbilities = true
        ), "caller defender Fur Coat must not replace the live Tangled Feet identity")
        assertEquals(77, liveNonFurCoat.request.defender.abilityId)
        assertFalse(liveNonFurCoat.verdict.hnsAbilityDecisions.any { it.abilityId == 169 })

        val callerOverride = goldenARequest("Psychic").copy(
            moveOverride = CalcMoveOverride(basePower = 40, type = "Normal", category = "Physical")
        )
        val specialControl = readyOf(build(
            trust, callerOverride, playerObservation(),
            enemyObservation(abilityId = 169, abilityName = "Fur Coat"), randomAbilities = true
        ), "caller Physical override must not fabricate a Defense-using category")
        assertEquals("Special", specialControl.request.moveOverride?.category)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            specialControl.verdict.hnsAbilityDecisions.single { it.abilityId == 169 }.relevance)
    }

    @Test
    fun `new Attack-stat branches ignore caller type category weather ability and item claims`() {
        val trust = trustFor(exactSha)

        val transistorRequest = goldenARequest("Tackle").copy(
            moveOverride = CalcMoveOverride(basePower = 120, type = "Electric", category = "Special")
        )
        val transistor = readyOf(build(
            trust, transistorRequest,
            playerObservation(abilityId = 262, abilityName = "Transistor"), enemyObservation(),
            randomAbilities = true
        ), "live Transistor with caller-forged Electric Tackle inputs")
        assertEquals("Normal", transistor.request.moveOverride?.type)
        assertEquals("Physical", transistor.request.moveOverride?.category)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            transistor.verdict.hnsAbilityDecisions.single { it.abilityId == 262 }.relevance)

        val orichalcumRequest = goldenARequest("Psychic").copy(
            field = CalcFieldInput(weather = "Sun"),
            moveOverride = CalcMoveOverride(basePower = 120, type = "Electric", category = "Physical"),
            attacker = goldenARequest("Psychic").attacker.copy(item = null, itemId = null)
        )
        val orichalcum = build(
            trust, orichalcumRequest,
            playerObservation(abilityId = 288, abilityName = "Orichalcum Pulse", itemId = 513),
            enemyObservation(), randomAbilities = true
        )
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            orichalcum.verdict().hnsAbilityDecisions.single { it.abilityId == 288 }.relevance)
        (orichalcum as? CalcRequestOutcome.Ready)?.let { ready ->
            assertNull(ready.request.field.weather)
            assertEquals("Psychic", ready.request.moveOverride?.type)
            assertEquals("Special", ready.request.moveOverride?.category)
            assertEquals(513, ready.request.attacker.itemId)
            val serialized = JSONObject(buildCalcRequestJson(ready.request))
            assertEquals(513, serialized.getJSONObject("attacker").getInt("hnsEffectiveItemId"))
            assertEquals(0, serialized.getJSONObject("field").getInt("hnsWeatherWord"))
            assertEquals("Psychic", serialized.getJSONObject("move").getJSONObject("overrides").getString("type"))
            assertEquals("Special", serialized.getJSONObject("move").getJSONObject("overrides").getString("category"))
        }

        val callerOmitsUmbrella = goldenARequest("Strength").copy(
            attacker = goldenARequest("Strength").attacker.copy(item = null, itemId = null)
        )
        val liveUmbrella = build(
            trust, callerOmitsUmbrella,
            playerObservation(abilityId = 288, abilityName = "Orichalcum Pulse", itemId = 513,
                battleWeather = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN_NORMAL),
            enemyObservation(battleWeather = com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN_NORMAL),
            randomAbilities = true
        )
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            liveUmbrella.verdict().hnsAbilityDecisions.single { it.abilityId == 288 }.relevance)
        (liveUmbrella as? CalcRequestOutcome.Ready)?.let { ready ->
            val serialized = JSONObject(buildCalcRequestJson(ready.request))
            assertEquals(com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_SUN_NORMAL,
                serialized.getJSONObject("field").getInt("hnsWeatherWord"))
        }
    }

    @Test
    fun `manual Hustle identity mismatch does not authorize the modeled ability`() {
        val mismatched = goldenARequest().copy(
            attacker = CalcPokemonInput(
                species = "Chikorita", level = 5, ability = "Guts", abilityId = 55,
                origin = CalcInputOrigin.MANUAL
            )
        )
        val refused = refusedOf(build(
            trustFor(exactSha), mismatched, null, null, activeBattle = false
        ), "Hustle's canonical ID cannot be paired with Guts' display identity")
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_IDENTITY_NOT_AUTHORITATIVE))
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
        // The caller claims Overgrow, but the engine reports Adaptability (91). Its exact STAB
        // branch is authorized only from the reconciled live ability and live type operands.
        val trust = trustFor(exactSha)
        val spoofed = goldenARequest().copy(
            attacker = liveInput("Chikorita", 5, 0, "Overgrow"),
            move = CalcMoveInput(name = "Razor Leaf") // STAB keeps Adaptability relevant.
        )
        val ready = readyOf(
            build(trust, spoofed, playerObservation(abilityId = 91, abilityName = "Adaptability"), enemyObservation()),
            "the live Adaptability identity wins and its exact STAB branch is modelled"
        )
        assertEquals(91, ready.verdict.hnsAbilityDecisions.single().abilityId)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT, ready.verdict.hnsAbilityDecisions.single().relevance)
        assertTrue(ready.verdict.ignoredMechanics.isEmpty())
        assertEquals("Adaptability", ready.request.attacker.ability)
        assertEquals(91, ready.request.attacker.abilityId)
        val engineInput = JSONObject(buildCalcRequestJson(ready.request))
        assertEquals("Adaptability", engineInput.getJSONObject("attacker").getString("ability"))
        assertEquals("Grass", engineInput.getJSONObject("move").getJSONObject("overrides").getString("type"))

        var reachedEngine = false
        val result = CalcAuthorizedExecution.calculate(ready.verdict) { request ->
            reachedEngine = true
            assertEquals(91, request.attacker.abilityId)
            assertTrue(JSONObject(buildCalcRequestJson(request)).getJSONObject("move")
                .getJSONObject("overrides").has("type"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = List(16) { 1 })
        }
        assertTrue("exact Adaptability STAB should reach authorized calculation", result.success && reachedEngine)
    }

    @Test
    fun `final modifier abilities use exact live operands and preserve breakability distinctions`() {
        val trust = trustFor(exactSha)
        fun relevance(outcome: CalcRequestOutcome, abilityId: Int): HnsAbilityRequestRelevance =
            readyOf(outcome, "ability $abilityId should be authorized").verdict.hnsAbilityDecisions
                .first { it.abilityId == abilityId }.relevance

        val tintedResisted = readyOf(
            build(trust, goldenARequest("Fire Punch"),
                playerObservation(abilityId = 110, abilityName = "Tinted Lens"),
                enemyObservation(speciesId = 134, types = listOf(11)), randomAbilities = true),
            "Tinted Lens reads exact live Water effectiveness"
        )
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            tintedResisted.verdict.hnsAbilityDecisions.first { it.abilityId == 110 }.relevance)

        val tintedNeutral = build(trust, goldenARequest("Tackle"),
            playerObservation(abilityId = 110, abilityName = "Tinted Lens"),
            enemyObservation(types = listOf(1)), randomAbilities = true)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, relevance(tintedNeutral, 110))

        val neuroforce = build(trust, goldenARequest("Karate Chop"),
            playerObservation(abilityId = 233, abilityName = "Neuroforce"),
            enemyObservation(speciesId = 143, types = listOf(1)), randomAbilities = true)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT, relevance(neuroforce, 233))

        val sniper = readyOf(
            build(trust, goldenARequest("Karate Chop").copy(move = CalcMoveInput("Karate Chop", isCrit = true)),
                playerObservation(abilityId = 97, abilityName = "Sniper"),
                enemyObservation(speciesId = 143, types = listOf(1)), randomAbilities = true),
            "the selected critical hit carries the live Sniper final modifier"
        )
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            sniper.verdict.hnsAbilityDecisions.first { it.abilityId == 97 }.relevance)
        assertTrue(JSONObject(buildCalcRequestJson(sniper.request)).getJSONObject("move").getBoolean("isCrit"))
        val sniperControl = build(trust, goldenARequest("Karate Chop"),
            playerObservation(abilityId = 97, abilityName = "Sniper"),
            enemyObservation(speciesId = 143, types = listOf(1)), randomAbilities = true)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, relevance(sniperControl, 97))

        for ((id, name) in listOf(111 to "Filter", 116 to "Solid Rock", 232 to "Prism Armor")) {
            val hit = build(trust, goldenARequest("Karate Chop"), playerObservation(),
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = id, abilityName = name),
                randomAbilities = true)
            assertEquals(HnsAbilityRequestRelevance.RELEVANT, relevance(hit, id))
            val neutral = build(trust, goldenARequest("Tackle"), playerObservation(),
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = id, abilityName = name),
                randomAbilities = true)
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, relevance(neutral, id))
        }

        for ((id, name) in listOf(136 to "Multiscale", 231 to "Shadow Shield")) {
            val full = readyOf(
                build(trust, goldenARequest("Tackle"), playerObservation(),
                    enemyObservation(speciesId = 143, types = listOf(1), abilityId = id, abilityName = name,
                        hp = 60000, maxHp = 60000), randomAbilities = true),
                "$name reads exact full HP"
            )
            assertEquals(HnsAbilityRequestRelevance.RELEVANT,
                full.verdict.hnsAbilityDecisions.first { it.abilityId == id }.relevance)
            val defenderJson = JSONObject(buildCalcRequestJson(full.request)).getJSONObject("defender")
            assertEquals(60000, defenderJson.getInt("hpAtHit"))
            assertEquals(60000, defenderJson.getInt("maxHpAtHit"))

            for (hp in listOf(59999, 1)) {
                val below = build(trust, goldenARequest("Tackle"), playerObservation(),
                    enemyObservation(speciesId = 143, types = listOf(1), abilityId = id, abilityName = name,
                        hp = hp, maxHp = 60000), randomAbilities = true)
                assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT, relevance(below, id))
            }
            for ((hp, maxHp) in listOf(0 to 60000, 60001 to 60000, 1 to 0)) {
                val invalid = build(trust, goldenARequest("Tackle"), playerObservation(),
                    enemyObservation(speciesId = 143, types = listOf(1), abilityId = id, abilityName = name,
                        hp = hp, maxHp = maxHp), randomAbilities = true)
                assertTrue("$name must keep invalid HP evidence unknown",
                    refusedOf(invalid, "$name invalid HP remains unknown").verdict.limitations.any {
                        it == CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED ||
                            it == CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN
                    })
            }
            val unread = build(trust, goldenARequest("Tackle"), playerObservation(),
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = id, abilityName = name,
                    hpObserved = false), randomAbilities = true)
            assertTrue("$name must fail closed when HP is unread",
                refusedOf(unread, "$name's unread HP remains unknown").verdict.limitations.any {
                    it == CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED ||
                        it == CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN
                })
        }

        val iceScales = build(trust, goldenARequest("Ember"), playerObservation(),
            enemyObservation(abilityId = 246, abilityName = "Ice Scales"), randomAbilities = true)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT, relevance(iceScales, 246))
        val iceScalesTypeBased = readyOf(
            build(trust, goldenARequest("Tackle"),
                playerObservation(abilityId = 182, abilityName = "Pixilate"),
                enemyObservation(abilityId = 246, abilityName = "Ice Scales"),
                randomAbilities = true, optionStyle = 1),
            "Ice Scales consumes the post-rewrite TYPE_BASED category"
        )
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            iceScalesTypeBased.verdict.hnsAbilityDecisions.first { it.abilityId == 246 }.relevance)
        val rewritten = JSONObject(buildCalcRequestJson(iceScalesTypeBased.request))
            .getJSONObject("move").getJSONObject("overrides")
        assertEquals("Fairy", rewritten.getString("type"))
        assertEquals("Special", rewritten.getString("category"))

        val moldBreaker = playerObservation(abilityId = 104, abilityName = "Mold Breaker")
        val moldFilter = refusedOf(
            build(trust, goldenARequest("Karate Chop"), moldBreaker,
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = 111, abilityName = "Filter"),
                randomAbilities = true),
            "Mold Breaker must retain the existing fail-closed path for breakable Filter"
        )
        assertTrue(moldFilter.verdict.limitations.contains(
            CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED))
        val moldSolidRock = refusedOf(
            build(trust, goldenARequest("Karate Chop"), moldBreaker,
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = 116, abilityName = "Solid Rock"),
                randomAbilities = true),
            "Mold Breaker must retain the existing fail-closed path for breakable Solid Rock"
        )
        assertTrue(moldSolidRock.verdict.limitations.contains(
            CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED))
        val moldMultiscale = refusedOf(
            build(trust, goldenARequest("Tackle"), moldBreaker,
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = 136, abilityName = "Multiscale",
                    hp = 15, maxHp = 15), randomAbilities = true),
            "Mold Breaker must retain the existing fail-closed path for breakable Multiscale"
        )
        assertTrue(moldMultiscale.verdict.limitations.contains(
            CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED))
        val moldIceScales = refusedOf(
            build(trust, goldenARequest("Ember"), moldBreaker,
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = 246, abilityName = "Ice Scales"),
                randomAbilities = true),
            "Mold Breaker must retain the existing fail-closed path for breakable Ice Scales"
        )
        assertTrue(moldIceScales.verdict.limitations.contains(
            CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED))

        val moldPrism = readyOf(
            build(trust, goldenARequest("Karate Chop"), moldBreaker,
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = 232, abilityName = "Prism Armor"),
                randomAbilities = true),
            "Mold Breaker cannot suppress pinned-unbreakable Prism Armor"
        )
        assertEquals("Prism Armor", JSONObject(buildCalcRequestJson(moldPrism.request))
            .getJSONObject("defender").getString("ability"))
        val moldShadow = readyOf(
            build(trust, goldenARequest("Tackle"), moldBreaker,
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = 231, abilityName = "Shadow Shield",
                    hp = 15, maxHp = 15), randomAbilities = true),
            "Mold Breaker cannot suppress pinned-unbreakable Shadow Shield"
        )
        assertEquals("Shadow Shield", JSONObject(buildCalcRequestJson(moldShadow.request))
            .getJSONObject("defender").getString("ability"))

        val shieldedFilter = readyOf(
            build(trust, goldenARequest("Karate Chop"), moldBreaker,
                enemyObservation(speciesId = 143, types = listOf(1), abilityId = 111, abilityName = "Filter",
                    itemId = 758), randomAbilities = true),
            "Ability Shield preserves a breakable Filter against Mold Breaker"
        )
        val shieldedJson = JSONObject(buildCalcRequestJson(shieldedFilter.request)).getJSONObject("defender")
        assertTrue(shieldedJson.getBoolean("hnsAbilityShield"))
        assertEquals("Filter", shieldedJson.getString("ability"))

        // Caller overrides cannot replace exact live HP/types or HnsMoveAuthority's final type and
        // category. The chosen crit bit is an explicit calculator input, carried unchanged into
        // the authorized request; no secondary caller-side modifier is introduced for Sniper.
        val spoofedOperands = goldenARequest("Karate Chop").copy(
            move = CalcMoveInput("Karate Chop", isCrit = true),
            attacker = goldenARequest().attacker.copy(curHP = 1),
            defender = goldenARequest().defender.copy(curHP = 1),
            attackerOverride = CalcSpeciesOverride(StatBlock(hp = 1, atk = 1), listOf("Fire")),
            defenderOverride = CalcSpeciesOverride(StatBlock(hp = 1, def = 1), listOf("Flying")),
            moveOverride = CalcMoveOverride(basePower = 1, type = "Electric", category = "Special")
        )
        val liveWins = readyOf(
            build(trust, spoofedOperands,
                playerObservation(abilityId = 97, abilityName = "Sniper", hp = 14, maxHp = 20, types = listOf(13)),
                enemyObservation(speciesId = 143, types = listOf(1), hp = 60000, maxHp = 60000,
                    abilityId = 232, abilityName = "Prism Armor"), randomAbilities = true),
            "all type, HP, and category values must bind from the trusted live request"
        )
        val json = JSONObject(buildCalcRequestJson(liveWins.request))
        assertEquals(60000, json.getJSONObject("defender").getInt("hpAtHit"))
        assertEquals(60000, json.getJSONObject("defender").getInt("maxHpAtHit"))
        assertEquals("Normal", json.getJSONObject("defender").getJSONObject("overrides")
            .getJSONArray("types").getString(0))
        assertEquals("Grass", json.getJSONObject("attacker").getJSONObject("overrides")
            .getJSONArray("types").getString(0))
        val authoritativeMove = json.getJSONObject("move").getJSONObject("overrides")
        assertEquals("Fighting", authoritativeMove.getString("type"))
        assertEquals("Physical", authoritativeMove.getString("category"))
        assertEquals(true, json.getJSONObject("move").getBoolean("isCrit"))
    }

    // ------------------------------------------------ held items: request-local relevance

    private fun readyOf(outcome: CalcRequestOutcome, why: String): CalcRequestOutcome.Ready =
        outcome as? CalcRequestOutcome.Ready ?: throw AssertionError("$why, got $outcome")

    private fun refusedOf(outcome: CalcRequestOutcome, why: String): CalcRequestOutcome.Refused =
        outcome as? CalcRequestOutcome.Refused ?: throw AssertionError("$why, got $outcome")

    @Test
    fun `defender Fire base-power abilities use boundary-owned final type and reach authorized execution`() {
        val trust = trustFor(exactSha)
        val fireCases = listOf(
            Triple(199, "Water Bubble", "Fire Punch"),
            Triple(85, "Heatproof", "Fire Blast"),
            Triple(87, "Dry Skin", "Fire Punch")
        )
        for ((abilityId, abilityName, move) in fireCases) {
            val ready = readyOf(build(
                trust, goldenARequest(move), playerObservation(),
                enemyObservation(abilityId = abilityId, abilityName = abilityName),
                randomAbilities = true
            ), "defender $abilityName with an authoritative Fire move")
            val decision = ready.verdict.hnsAbilityDecisions.singleOrNull { it.abilityId == abilityId }
            if (abilityId == 85 || abilityId == 199) {
                assertEquals("$abilityName should be relevant for the Fire hit",
                    HnsAbilityRequestRelevance.RELEVANT, decision?.relevance)
            }
            val response = CalcAuthorizedExecution.calculate(ready.verdict) { request ->
                val json = JSONObject(buildCalcRequestJson(request))
                assertEquals("Fire", json.getJSONObject("move").getJSONObject("overrides").getString("type"))
                DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
            }
            assertTrue("$abilityName Fire request must reach authorized execution", response.success)
        }

        for ((abilityId, abilityName) in listOf(199 to "Water Bubble", 85 to "Heatproof")) {
            val ready = readyOf(build(
                trust, goldenARequest("Tackle"), playerObservation(),
                enemyObservation(abilityId = abilityId, abilityName = abilityName),
                randomAbilities = true
            ), "defender $abilityName with a known non-Fire move")
            assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
                ready.verdict.hnsAbilityDecisions.single { it.abilityId == abilityId }.relevance)
        }

        val attackerHeatproof = readyOf(build(
            trust, goldenARequest("Fire Punch"),
            playerObservation(abilityId = 85, abilityName = "Heatproof"), enemyObservation(),
            randomAbilities = true
        ), "attacker Heatproof on an ordinary outgoing hit")
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            attackerHeatproof.verdict.hnsAbilityDecisions.single { it.abilityId == 85 }.relevance)

        // A source Fire move rewritten by the supported Normalize ability ends as Normal before
        // the defender predicate. The defender Water Bubble branch follows that final authority.
        val rewritten = readyOf(build(
            trust, goldenARequest("Fire Punch"),
            playerObservation(abilityId = 96, abilityName = "Normalize"),
            enemyObservation(abilityId = 199, abilityName = "Water Bubble"),
            randomAbilities = true
        ), "Normalize must rewrite Fire before defender ability checks")
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            rewritten.verdict.hnsAbilityDecisions.single { it.abilityId == 199 }.relevance)
        val rewrittenResponse = CalcAuthorizedExecution.calculate(rewritten.verdict) { request ->
            val json = JSONObject(buildCalcRequestJson(request))
            assertEquals("Normal", json.getJSONObject("move").getJSONObject("overrides").getString("type"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }
        assertTrue(rewrittenResponse.success)

        // A caller-provided moveOverride cannot turn source Tackle into a Fire move for policy or
        // engine serialization. The final authoritative type remains Normal.
        val spoofedTypeRequest = goldenARequest("Tackle").copy(
            moveOverride = CalcMoveOverride(basePower = 40, type = "Fire", category = "Physical")
        )
        val spoofedType = readyOf(build(
            trust, spoofedTypeRequest, playerObservation(),
            enemyObservation(abilityId = 199, abilityName = "Water Bubble"),
            randomAbilities = true
        ), "caller moveOverride cannot create a defender Fire branch")
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            spoofedType.verdict.hnsAbilityDecisions.single { it.abilityId == 199 }.relevance)
        val spoofedResponse = CalcAuthorizedExecution.calculate(spoofedType.verdict) { request ->
            val json = JSONObject(buildCalcRequestJson(request))
            assertEquals("Normal", json.getJSONObject("move").getJSONObject("overrides").getString("type"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }
        assertTrue(spoofedResponse.success)
    }

    @Test
    fun `breakable defender Fire abilities refuse Mold Breaker and Ability Shield preserves them`() {
        val trust = trustFor(exactSha)
        for ((abilityId, abilityName, moveName) in listOf(
            Triple(199, "Water Bubble", "Fire Punch"),
            Triple(85, "Heatproof", "Fire Punch"),
            Triple(218, "Fluffy", "Fire Blast")
        )) {
            val attacker = playerObservation(abilityId = 104, abilityName = "Mold Breaker")
            val fireRequest = goldenARequest(moveName)
            val unshielded = refusedOf(build(
                trust, fireRequest, attacker,
                enemyObservation(abilityId = abilityId, abilityName = abilityName),
                randomAbilities = true
            ), "unshielded Mold Breaker against defender $abilityName")
            assertTrue(unshielded.verdict.limitations.contains(
                CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED
            ))
            val refusedExecution = CalcAuthorizedExecution.calculate(unshielded.verdict) {
                throw AssertionError("a refused Mold Breaker interaction must not reach the engine")
            }
            assertFalse(refusedExecution.success)

            val shielded = readyOf(build(
                trust, fireRequest, attacker,
                enemyObservation(abilityId = abilityId, abilityName = abilityName, itemId = 758),
                randomAbilities = true
            ), "Ability Shield must preserve defender $abilityName")
            val shieldedExecution = CalcAuthorizedExecution.calculate(shielded.verdict) { request ->
                val json = JSONObject(buildCalcRequestJson(request))
                assertEquals("Fire", json.getJSONObject("move").getJSONObject("overrides").getString("type"))
                val defender = json.getJSONObject("defender")
                assertTrue(defender.getBoolean("hnsAbilityShield"))
                assertEquals(abilityName, defender.getString("ability"))
                DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
            }
            assertTrue("Ability Shield must keep the $abilityName modifier active", shieldedExecution.success)
        }
    }

    @Test
    fun `Fur Coat uses centralized breakability for Mold Breaker and literal move bypass`() {
        val trust = trustFor(exactSha)
        val attacker = playerObservation(abilityId = 104, abilityName = "Mold Breaker")
        val furCoat = enemyObservation(abilityId = 169, abilityName = "Fur Coat")
        val suppressed = refusedOf(build(
            trust, goldenARequest("Tackle"), attacker, furCoat, randomAbilities = true
        ), "unshielded Mold Breaker must retain the Fur Coat suppression limitation")
        assertTrue(suppressed.verdict.limitations.contains(CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED))

        val shielded = readyOf(build(
            trust, goldenARequest("Tackle"), attacker,
            enemyObservation(abilityId = 169, abilityName = "Fur Coat", itemId = 758), randomAbilities = true
        ), "Ability Shield preserves breakable Fur Coat")
        assertEquals(169, shielded.request.defender.abilityId)
        assertTrue(CalcAuthorizedExecution.calculate(shielded.verdict) { request ->
            val defender = JSONObject(buildCalcRequestJson(request)).getJSONObject("defender")
            assertTrue(defender.getBoolean("hnsAbilityShield"))
            assertEquals("Fur Coat", defender.getString("ability"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }.success)

        val literalBypass = readyOf(build(
            trust, goldenARequest("Sunsteel Strike"), playerObservation(), furCoat, randomAbilities = true
        ), "literal ignoresTargetAbility bypass is source-correct for breakable Fur Coat")
        assertTrue(CalcAuthorizedExecution.calculate(literalBypass.verdict) { request ->
            val move = JSONObject(buildCalcRequestJson(request)).getJSONObject("move")
            assertTrue(move.getJSONArray("hnsMoveFlags").toString().contains("ignoresTargetAbility"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }.success)

        val wonderRoom = com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM
        val wonderRoomFurCoat = refusedOf(build(
            trust, goldenARequest("Tackle"),
            playerObservation(fieldStatuses = wonderRoom),
            enemyObservation(abilityId = 169, abilityName = "Fur Coat", fieldStatuses = wonderRoom),
            randomAbilities = true
        ), "Wonder Room remains outside supported Fur Coat defense selection")
        assertTrue(wonderRoomFurCoat.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
    }

    @Test
    fun `observed attacker ability owns the dynamic type and serialized ate boost`() {
        val trust = trustFor(exactSha)
        val spoofed = goldenARequest().copy(
            attacker = goldenARequest().attacker.copy(ability = "Galvanize", abilityId = 206),
            moveOverride = CalcMoveOverride(basePower = 250, type = "Fire", category = "Special")
        )
        val pixilate = readyOf(
            build(trust, spoofed,
                playerObservation(abilityId = 182, abilityName = "Pixilate"), enemyObservation(),
                randomAbilities = true),
            "the exact live Pixilate identity must own the rewrite instead of caller ability/type overrides"
        )
        assertEquals(182, pixilate.request.attacker.abilityId)
        assertEquals("Pixilate", pixilate.request.attacker.ability)
        assertEquals(40, pixilate.request.moveOverride?.basePower)
        assertEquals("Fairy", pixilate.request.moveOverride?.type)
        assertEquals("Physical", pixilate.request.moveOverride?.category)

        val liveJson = JSONObject(buildCalcRequestJson(pixilate.request))
        val serializedMove = liveJson.getJSONObject("move")
        val serializedOverride = serializedMove.getJSONObject("overrides")
        assertEquals("Fairy", serializedOverride.getString("type"))
        assertEquals("Physical", serializedOverride.getString("category"))
        assertTrue(serializedOverride.getBoolean("ateBoost"))

        var reachedEngine = false
        val result = CalcAuthorizedExecution.calculate(pixilate.verdict) { request ->
            reachedEngine = true
            assertEquals("Fairy", JSONObject(buildCalcRequestJson(request))
                .getJSONObject("move").getJSONObject("overrides").getString("type"))
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = listOf(1, 1))
        }
        assertTrue("an authorized rewrite must reach the calculator", result.success && reachedEngine)

        val typeBased = readyOf(
            build(trust, goldenARequest(),
                playerObservation(abilityId = 182, abilityName = "Pixilate"), enemyObservation(),
                randomAbilities = true, optionStyle = 1),
            "TYPE_BASED must resolve category from the rewritten Fairy type"
        )
        val typeBasedOverride = JSONObject(buildCalcRequestJson(typeBased.request))
            .getJSONObject("move").getJSONObject("overrides")
        assertEquals("Fairy", typeBasedOverride.getString("type"))
        assertEquals("Special", typeBasedOverride.getString("category"))
    }

    @Test
    fun `all move type abilities use authoritative predicates and fail closed when operands are unknown`() {
        val trust = trustFor(exactSha)
        data class AbilityCase(
            val id: Int,
            val name: String,
            val move: String,
            val expectedType: String,
            val expectedAteBoost: Boolean
        )
        for ((id, name, move, expectedType, expectedAteBoost) in listOf(
            AbilityCase(96, "Normalize", "Tackle", "Normal", true),
            AbilityCase(174, "Refrigerate", "Tackle", "Ice", true),
            AbilityCase(182, "Pixilate", "Tackle", "Fairy", true),
            AbilityCase(184, "Aerilate", "Tackle", "Flying", true),
            AbilityCase(206, "Galvanize", "Tackle", "Electric", true),
            AbilityCase(204, "Liquid Voice", "Hyper Voice", "Water", false)
        )) {
            val ready = readyOf(
                build(trust, goldenARequest(move), playerObservation(abilityId = id, abilityName = name),
                    enemyObservation(), randomAbilities = true),
                "$name should model its pinned rewrite for $move"
            )
            val moveJson = JSONObject(buildCalcRequestJson(ready.request)).getJSONObject("move")
            val overrides = moveJson.getJSONObject("overrides")
            assertEquals(name, expectedType, overrides.getString("type"))
            assertEquals(name, expectedAteBoost, overrides.getBoolean("ateBoost"))
        }

        for ((id, name) in listOf(
            174 to "Refrigerate", 182 to "Pixilate", 184 to "Aerilate", 206 to "Galvanize"
        )) {
            val nonNormal = readyOf(
                build(trust, goldenARequest("Fire Punch"),
                    playerObservation(abilityId = id, abilityName = name), enemyObservation(), randomAbilities = true),
                "$name is proven inactive for an authoritative non-Normal source move"
            )
            val overrides = JSONObject(buildCalcRequestJson(nonNormal.request)).getJSONObject("move")
                .getJSONObject("overrides")
            assertEquals("Fire", overrides.getString("type"))
            assertFalse(overrides.getBoolean("ateBoost"))
        }

        val fairyOff = readyOf(
            build(trust, goldenARequest("Fairy Wind"),
                playerObservation(abilityId = 182, abilityName = "Pixilate"), enemyObservation(),
                randomAbilities = true, fairyTypes = false),
            "Fairy-off maps Fairy Wind to Normal before Pixilate's pinned Normal predicate"
        )
        val fairyOffMove = JSONObject(buildCalcRequestJson(fairyOff.request)).getJSONObject("move")
            .getJSONObject("overrides")
        assertEquals("Fairy", fairyOffMove.getString("type"))
        assertTrue(fairyOffMove.getBoolean("ateBoost"))

        val fairyOn = readyOf(
            build(trust, goldenARequest("Fairy Wind"),
                playerObservation(abilityId = 182, abilityName = "Pixilate"), enemyObservation(),
                randomAbilities = true, fairyTypes = true),
            "Pixilate does not rewrite a source Fairy move when Fairy types are enabled"
        )
        val fairyOnMove = JSONObject(buildCalcRequestJson(fairyOn.request)).getJSONObject("move")
            .getJSONObject("overrides")
        assertEquals("Fairy", fairyOnMove.getString("type"))
        assertFalse(fairyOnMove.getBoolean("ateBoost"))

        val liquidVoiceNonSound = readyOf(
            build(trust, goldenARequest("Tackle"),
                playerObservation(abilityId = 204, abilityName = "Liquid Voice"), enemyObservation(),
                randomAbilities = true),
            "Liquid Voice is proven irrelevant for a source-known nonsound move"
        )
        val nonSoundMove = JSONObject(buildCalcRequestJson(liquidVoiceNonSound.request)).getJSONObject("move")
            .getJSONObject("overrides")
        assertEquals("Normal", nonSoundMove.getString("type"))
        assertFalse(nonSoundMove.getBoolean("ateBoost"))

        val defenderCopy = readyOf(
            build(trust, goldenARequest(), playerObservation(),
                enemyObservation(abilityId = 204, abilityName = "Liquid Voice"), randomAbilities = true),
            "a defender-side Liquid Voice must not rewrite the attacker's move"
        )
        assertEquals("Normal", JSONObject(buildCalcRequestJson(defenderCopy.request)).getJSONObject("move")
            .getJSONObject("overrides").getString("type"))

        val unknownSound = refusedOf(
            build(trust, goldenARequest("Howl"),
                playerObservation(abilityId = 204, abilityName = "Liquid Voice"), enemyObservation(),
                randomAbilities = true),
            "conditional sound metadata on nonordinary Howl must not authorize a move"
        )
        assertTrue(unknownSound.verdict.limitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
        val conditionalSoundAuthority = HnsMoveAuthority.forRequest(
            liquidVoiceNonSound.request.copy(move = CalcMoveInput(name = "Howl")), ordinaryMove = true
        )
        assertEquals(HnsAbilityTypeRewriteOutcome.UNKNOWN, conditionalSoundAuthority.abilityRewriteOutcome)

        val electrifiedState = fairyOff.request.hnsLiveBattleState!!.copy(attackerElectrified = true)
        val stackedAuthority = HnsMoveAuthority.forRequest(
            fairyOff.request.copy(hnsLiveBattleState = electrifiedState), ordinaryMove = true
        )
        assertEquals("Fairy", stackedAuthority.preFieldType?.displayName)
        assertEquals("Electric", stackedAuthority.effectiveType?.displayName)
        assertTrue(stackedAuthority.ateBoost == true)

        val missingGimmick = refusedOf(
            build(trust, goldenARequest(),
                playerObservation(abilityId = 182, abilityName = "Pixilate", gimmickObserved = false),
                enemyObservation(), randomAbilities = true),
            "missing gimmick state leaves the dynamic type path unknown"
        )
        assertTrue(missingGimmick.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
    }

    @Test
    fun `Punk Rock and holder Steely Spirit reach authorized execution from live identities`() {
        val trust = trustFor(exactSha)
        fun execute(
            ready: CalcRequestOutcome.Ready,
            abilityId: Int,
            moveFlag: String? = null,
            side: HnsAbilitySide = HnsAbilitySide.ATTACKER
        ) {
            var reached = false
            val result = CalcAuthorizedExecution.calculate(ready.verdict) { request ->
                reached = true
                val observedAbility = if (side == HnsAbilitySide.ATTACKER) {
                    request.attacker.abilityId
                } else {
                    request.defender.abilityId
                }
                assertEquals(abilityId, observedAbility)
                val json = JSONObject(buildCalcRequestJson(request))
                val move = json.getJSONObject("move")
                val flags = move.getJSONArray("hnsMoveFlags").toString()
                if (moveFlag == null) assertFalse(flags.contains("soundMove"))
                else assertTrue("source move flag missing from authorized request: $flags", flags.contains(moveFlag))
                DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1, range = List(16) { 1 })
            }
            assertTrue("the exact live ability should reach authorized execution", result.success && reached)
        }

        val callerAbilityAndMoveSpoof = goldenARequest("Hyper Voice").copy(
            attacker = liveInput("Chikorita", 5, 9, "Static"),
            moveOverride = CalcMoveOverride(basePower = 1, type = "Fire", category = "Physical")
        )
        val punkAttacker = readyOf(
            build(trust, callerAbilityAndMoveSpoof,
                playerObservation(abilityId = 244, abilityName = "Punk Rock"), enemyObservation(),
                randomAbilities = true),
            "live attacker Punk Rock and the source sound flag should authorize"
        )
        assertEquals(244, punkAttacker.request.attacker.abilityId)
        assertEquals("Punk Rock", punkAttacker.request.attacker.ability)
        val punkMove = JSONObject(buildCalcRequestJson(punkAttacker.request)).getJSONObject("move")
        assertEquals("Normal", punkMove.getJSONObject("overrides").getString("type"))
        assertEquals("Special", punkMove.getJSONObject("overrides").getString("category"))
        assertEquals(90, punkMove.getJSONObject("overrides").getInt("basePower"))
        execute(punkAttacker, 244, "soundMove")

        val punkDefender = readyOf(
            build(trust, goldenARequest("Hyper Voice"), playerObservation(),
                enemyObservation(abilityId = 244, abilityName = "Punk Rock"), randomAbilities = true),
            "live defender Punk Rock with a source sound move should authorize"
        )
        assertEquals(244, punkDefender.request.defender.abilityId)
        execute(punkDefender, 244, "soundMove", HnsAbilitySide.DEFENDER)

        val moldBreakerPunkRock = refusedOf(
            build(trust, goldenARequest("Hyper Voice"),
                playerObservation(abilityId = 104, abilityName = "Mold Breaker"),
                enemyObservation(abilityId = 244, abilityName = "Punk Rock"), randomAbilities = true),
            "Mold Breaker suppression of defender Punk Rock must remain fail-closed"
        )
        assertTrue(moldBreakerPunkRock.verdict.limitations.contains(
            CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED
        ))

        val nonSoundRequest = goldenARequest("Tackle").copy(
            attacker = liveInput("Chikorita", 5, 244, "Punk Rock"),
            moveOverride = CalcMoveOverride(basePower = 250, type = "Steel", category = "Special")
        )
        val punkNonSound = readyOf(
            build(trust, nonSoundRequest,
                playerObservation(abilityId = 244, abilityName = "Punk Rock"), enemyObservation(),
                randomAbilities = true),
            "a caller-supplied move override cannot mark Tackle as sound"
        )
        assertTrue(punkNonSound.verdict.hnsAbilityDecisions.any {
            it.abilityId == 244 && it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
        })
        val nonSoundJson = buildCalcRequestJson(punkNonSound.request)
        assertFalse("caller data cannot fabricate source sound metadata", nonSoundJson.contains("soundMove"))
        execute(punkNonSound, 244)

        val unknownSound = refusedOf(
            build(trust, goldenARequest("Howl").copy(
                attacker = liveInput("Chikorita", 5, 244, "Punk Rock")
            ), playerObservation(abilityId = 244, abilityName = "Punk Rock"), enemyObservation(),
                randomAbilities = true),
            "computed sound metadata must not authorize Punk Rock"
        )
        assertTrue(unknownSound.verdict.limitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
        val unknownSoundContext = HnsAbilityContextPolicy.contextForRequest(
            punkAttacker.request.copy(move = CalcMoveInput("Howl")), HnsAbilitySide.ATTACKER, true
        )
        assertNull(unknownSoundContext.soundMove)
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            HnsAbilityContextPolicy.assess(244, unknownSoundContext).relevance)
        val unknownEngine = CalcAuthorizedExecution.calculate(unknownSound.verdict) {
            throw AssertionError("unknown/computed sound metadata must not reach the calculator")
        }
        assertFalse(unknownEngine.success)

        val spoofedSteel = goldenARequest("Iron Head").copy(
            attacker = liveInput("Chikorita", 5, 9, "Static"),
            moveOverride = CalcMoveOverride(basePower = 1, type = "Fire", category = "Special")
        )
        val steelyAttacker = readyOf(
            build(trust, spoofedSteel,
                playerObservation(abilityId = 252, abilityName = "Steely Spirit"), enemyObservation(),
                randomAbilities = true),
            "live holder Steely Spirit with an effective Steel move should authorize"
        )
        assertTrue(steelyAttacker.verdict.hnsAbilityDecisions.any {
            it.abilityId == 252 && it.side == HnsAbilitySide.ATTACKER &&
                it.relevance == HnsAbilityRequestRelevance.RELEVANT
        })
        val steelMove = JSONObject(buildCalcRequestJson(steelyAttacker.request)).getJSONObject("move")
        assertEquals("Steel", steelMove.getJSONObject("overrides").getString("type"))
        assertEquals("Physical", steelMove.getJSONObject("overrides").getString("category"))
        execute(steelyAttacker, 252)

        val steelyNonSteel = readyOf(
            build(trust, goldenARequest("Tackle").copy(
                attacker = liveInput("Chikorita", 5, 252, "Steely Spirit"),
                moveOverride = CalcMoveOverride(basePower = 250, type = "Steel", category = "Special")
            ), playerObservation(abilityId = 252, abilityName = "Steely Spirit"), enemyObservation(),
                randomAbilities = true),
            "caller-supplied Steel cannot override Tackle's final Normal type"
        )
        assertTrue(steelyNonSteel.verdict.hnsAbilityDecisions.any {
            it.abilityId == 252 && it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
        })
        assertEquals("Normal", JSONObject(buildCalcRequestJson(steelyNonSteel.request))
            .getJSONObject("move").getJSONObject("overrides").getString("type"))
        execute(steelyNonSteel, 252)

        // Normalize and Steely Spirit are mutually exclusive holder abilities. This ordinary Steel
        // source move still proves that #106's final Normal type is serialized after Normalize.
        val normalizedSteelMove = readyOf(
            build(trust, goldenARequest("Iron Head"),
                playerObservation(abilityId = 96, abilityName = "Normalize"), enemyObservation(),
                randomAbilities = true),
            "Normalize must rewrite the ordinary Steel source move before damage policy"
        )
        val normalized = JSONObject(buildCalcRequestJson(normalizedSteelMove.request))
            .getJSONObject("move").getJSONObject("overrides")
        assertEquals("Normal", normalized.getString("type"))

        val electrifiedSteely = refusedOf(
            build(trust, goldenARequest("Iron Head").copy(
                attacker = liveInput("Chikorita", 5, 252, "Steely Spirit")
            ), playerObservation(abilityId = 252, abilityName = "Steely Spirit", electrified = true),
                enemyObservation(), randomAbilities = true),
            "an active Electrify rewrite must remain blocked by the existing dynamic-type gate"
        )
        assertTrue(electrifiedSteely.verdict.limitations.contains(
            CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED
        ))
        assertTrue(electrifiedSteely.verdict.hnsAbilityDecisions.any {
            it.abilityId == 252 && it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
        })
    }

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
            it.globalCategory == com.dualdex.pokemon.hns.HnsItemCategory.MODELLED_HNS_SPECIFIC &&
                it.relevance == HnsItemRequestRelevance.PROVEN_IRRELEVANT
        })
        // The global category is untouched by the request-local clearance.
        assertEquals(com.dualdex.pokemon.hns.HnsItemCategory.MODELLED_HNS_SPECIFIC,
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
    fun `a relevant live item becomes a named caveat with its structured decision`() {
        val sash = readyOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(), enemyObservation(itemId = 481)),
            "a full-HP foe's Focus Sash is named as a caveat"
        )
        assertTrue(sash.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        val decision = sash.verdict.hnsItemDecisions.single()
        assertEquals(481, decision.itemId)
        assertEquals("Focus Sash", decision.itemName)
        assertEquals(HnsItemSide.DEFENDER, decision.side)
        assertEquals(HnsItemRequestRelevance.RELEVANT, decision.relevance)
        assertEquals("Foe: Focus Sash", sash.verdict.ignoredMechanics.single().presentationLine)
        val damagedFoe = build(trustFor(exactSha), goldenARequest(),
            playerObservation(), enemyObservation(itemId = 481, hp = 9, maxHp = 15))
        assertTrue("Focus Sash below max HP cannot activate", damagedFoe is CalcRequestOutcome.Ready)
    }

    @Test
    fun `Electrify supplies effective type relevance while its active path remains blocked`() {
        // Electrify's later source rewrite makes the effective type Electric, proving Charcoal
        // irrelevant; the existing dynamic-type limitation still refuses the request.
        val refused = refusedOf(
            build(trustFor(exactSha), goldenARequest(),
                playerObservation(itemId = 426, electrified = true), enemyObservation()),
            "an active Electrify rewrite must remain refused"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, refused.verdict.hnsItemDecisions.single().relevance)
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED))
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
    fun `Room Service waits for switch-in settlement while Blunder Policy does not`() {
        val trust = trustFor(exactSha)
        val trickRoom = 0x00000002
        val pendingRoomService = refusedOf(
            build(trust, goldenARequest(),
                playerObservation(itemId = 512, fieldStatuses = trickRoom,
                    switchInEventsSettled = false),
                enemyObservation(fieldStatuses = trickRoom, switchInEventsSettled = false)),
            "Room Service may lower Speed during an unsettled Trick Room switch-in"
        )
        assertEquals(HnsItemRequestRelevance.UNKNOWN,
            pendingRoomService.verdict.hnsItemDecisions.single().relevance)
        assertTrue(pendingRoomService.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))

        val settledRoomService = readyOf(
            build(trust, goldenARequest(),
                playerObservation(itemId = 512, fieldStatuses = trickRoom),
                enemyObservation(fieldStatuses = trickRoom)),
            "settled Room Service uses the existing current-hit order proof"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT,
            settledRoomService.verdict.hnsItemDecisions.single().relevance)
        assertFalse(settledRoomService.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))

        val unsettledBlunderPolicy = readyOf(
            build(trust, goldenARequest(),
                playerObservation(itemId = 511, switchInEventsSettled = false),
                enemyObservation(switchInEventsSettled = false)),
            "Blunder Policy has no on-switch-in activation and remains clear"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT,
            unsettledBlunderPolicy.verdict.hnsItemDecisions.single().relevance)
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

        val postHitItem = refusedOf(
            build(trustFor(exactSha), goldenARequest(move = "Explosion"),
                playerObservation(itemId = 502), enemyObservation()),
            "an after-hit Weakness Policy cannot change the unsupported selected hit"
        )
        assertTrue(postHitItem.verdict.blockingLimitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
        assertFalse(postHitItem.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT,
            postHitItem.verdict.hnsItemDecisions.single { it.itemId == 502 }.relevance)
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

        val estimate = readyOf(
            build(trustFor(exactSha), goldenARequest(),
                playerObservation(abilityId = 308, abilityName = "Tera Shell"),
                enemyObservation(abilityId = 54, abilityName = "Truant", itemId = 481),
                randomAbilities = true),
            "a relevant item is named beside irrelevant abilities"
        )
        assertTrue(estimate.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertFalse(estimate.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertEquals(listOf("Foe: Focus Sash"), estimate.verdict.ignoredMechanics.map { it.presentationLine })
    }

    @Test
    fun `Group A proof clears crit stage item and abilities without a caveat`() {
        val ready = readyOf(
            build(trustFor(exactSha), goldenARequest(),
                playerObservation(abilityId = 105, abilityName = "Super Luck", itemId = 471),
                enemyObservation(abilityId = 4, abilityName = "Battle Armor"),
                randomAbilities = true),
            "fixed noncritical hit must clear stage-only effects"
        )
        assertTrue(ready.verdict.hnsAbilityDecisions.all {
            it.relevance == HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
        })
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT,
            ready.verdict.hnsItemDecisions.single().relevance)
        assertTrue(ready.verdict.ignoredMechanics.isEmpty())
        assertTrue(ready.verdict.blockingLimitations.isEmpty())
        assertEquals("Super Luck", ready.request.attacker.ability)
        // The item bridge strips unmodelled held effects after policy proves this one irrelevant.
        assertNull(ready.request.attacker.item)
    }

    @Test
    fun `Group A after-hit ability clears ordinary hit but not unreviewed move shape`() {
        val ordinary = readyOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(),
                enemyObservation(abilityId = 24, abilityName = "Rough Skin"), randomAbilities = true),
            "Rough Skin acts after this hit"
        )
        assertTrue(ordinary.verdict.ignoredMechanics.isEmpty())
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            ordinary.verdict.hnsAbilityDecisions.single().relevance)

        val nonordinary = refusedOf(
            build(trustFor(exactSha), goldenARequest(move = "Explosion"), playerObservation(),
                enemyObservation(abilityId = 24, abilityName = "Rough Skin"), randomAbilities = true),
            "unsupported move shape remains refused"
        )
        assertEquals(HnsAbilityRequestRelevance.UNKNOWN,
            nonordinary.verdict.hnsAbilityDecisions.single { it.abilityId == 24 }.relevance)
    }

    @Test
    fun `anti-spoof - the live current item wins over any caller item claim`() {
        val trust = trustFor(exactSha)
        // Caller claims Charcoal (ID and name); the live current item is Silk Scarf.
        val claimed = goldenARequest().let {
            it.copy(attacker = it.attacker.copy(itemId = 426, item = "CHARCOAL",
                itemProvenance = CalcItemProvenance.PARTY_STORAGE))
        }
        val spoofed = readyOf(build(trust, claimed, playerObservation(itemId = 425), enemyObservation()),
            "a caller item cannot replace the live current item")
        assertEquals(425, spoofed.verdict.hnsItemDecisions.single().itemId)
        assertEquals(HnsItemRequestRelevance.MODELLED, spoofed.verdict.hnsItemDecisions.single().relevance)
        assertTrue(spoofed.verdict.ignoredMechanics.isEmpty())
        assertEquals(425, spoofed.request.attacker.itemId)
        assertNull(spoofed.request.attacker.item)

        // A name-only caller claim is ignored for an active battler too.
        val named = goldenARequest().let { it.copy(attacker = it.attacker.copy(item = "Charcoal")) }
        val namedLive = readyOf(build(trust, named, playerObservation(itemId = 425), enemyObservation()),
            "a caller name cannot replace the live current item")
        assertEquals(425, namedLive.verdict.hnsItemDecisions.single().itemId)
        assertEquals(HnsItemRequestRelevance.MODELLED, namedLive.verdict.hnsItemDecisions.single().relevance)
        assertTrue(namedLive.verdict.ignoredMechanics.isEmpty())

        // Consumed / knocked off: stored Silk Scarf, live ITEM_NONE -> the live word wins.
        val stored = goldenARequest().let {
            it.copy(attacker = it.attacker.copy(itemId = 425, item = "SILK SCARF",
                itemProvenance = CalcItemProvenance.PARTY_STORAGE))
        }
        val consumed = readyOf(build(trust, stored, playerObservation(itemId = 0), enemyObservation()),
            "a consumed item is represented by the live ITEM_NONE")
        assertEquals(0, consumed.request.attacker.itemId)
        assertTrue(consumed.verdict.hnsItemDecisions.isEmpty())

        // Swapped (Trick): stored Charcoal, live Choice Band on a physical move -> live modifier.
        val swapped = readyOf(build(trust, claimed, playerObservation(itemId = 442), enemyObservation()),
            "the swapped-in live Choice Band is authoritative for Tackle")
        assertEquals(442, swapped.verdict.hnsItemDecisions.single().itemId)
        assertEquals(HnsItemRequestRelevance.MODELLED, swapped.verdict.hnsItemDecisions.single().relevance)
        assertEquals(442, swapped.request.attacker.itemId)
        assertTrue(swapped.verdict.ignoredMechanics.isEmpty())

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

    private fun ignoredText(outcome: CalcRequestOutcome): String =
        com.dualdex.battle.DamageBlockerPresentation.ignoredText(
            com.dualdex.battle.DamageBlockerPresentation.ignoredFrom(outcome.verdict())
        )

    @Test
    fun `the raw field word is preserved and every active bit gets its own named decision`() {
        val word = HnsFieldStatus.WONDER_ROOM.mask or HnsFieldStatus.ELECTRIC_TERRAIN.mask or
            HnsFieldStatus.FAIRY_LOCK.mask
        val estimate = readyOf(fieldBuild(word), "known Wonder Room effect is named and neutralized")
        assertEquals(0x904, estimate.verdict.hnsFieldDiagnostics?.fieldState?.raw)
        assertEquals(
            listOf(HnsFieldStatus.WONDER_ROOM, HnsFieldStatus.ELECTRIC_TERRAIN, HnsFieldStatus.FAIRY_LOCK),
            estimate.verdict.hnsFieldDecisions.map { it.status }
        )
        assertEquals(
            listOf(HnsFieldRequestRelevance.RELEVANT, HnsFieldRequestRelevance.MODELLED,
                HnsFieldRequestRelevance.PROVEN_IRRELEVANT),
            estimate.verdict.hnsFieldDecisions.map { it.relevance }
        )
        assertEquals(
            listOf("wonder_room_swaps_defensive_stat", "electric_terrain_other_move", "fairy_lock_escape_only"),
            estimate.verdict.hnsFieldDecisions.map { it.rule }
        )
        assertTrue(estimate.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals(listOf("Field: Wonder Room"), estimate.verdict.ignoredMechanics.map { it.presentationLine })
        assertEquals("\nIgnores:\nField: Wonder Room", ignoredText(estimate))
        assertEquals(0x900, estimate.request.hnsLiveBattleState?.fieldStatuses)
        assertFalse(buildCalcRequestJson(estimate.request).contains("Wonder Room"))
    }

    @Test
    fun `a readable zero field word is clear and records no decision`() {
        val ready = readyOf(fieldBuild(0), "an observed clear field is the positive control")
        assertEquals(0, ready.verdict.hnsFieldDiagnostics?.fieldState?.raw)
        assertTrue(ready.verdict.hnsFieldDiagnostics!!.fieldState!!.isClear)
        assertTrue(ready.verdict.hnsFieldDecisions.isEmpty())
    }

    @Test
    fun `Wise Glasses with a Physical move stays clean while a known field effect is caveated`() {
        // Device regression (AYN Thor): exact H&S, ordinary Singles, attacker Wise Glasses, a non-zero
        // live field word. Tackle is authoritatively Physical under PER_MOVE_SPLIT; Wonder Room does
        // not change that, so the card shows the field condition and NOT a second Wise Glasses blocker.
        val estimate = readyOf(
            fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, attackerItem = wiseGlasses),
            "Wonder Room is ignored while Wise Glasses is proven irrelevant"
        )
        val glasses = estimate.verdict.hnsItemDecisions.single()
        assertEquals("Wise Glasses", glasses.itemName)
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, glasses.relevance)
        assertEquals("special_only_item_physical_move", glasses.rule)
        assertFalse(estimate.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(estimate.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals("\nIgnores:\nField: Wonder Room", ignoredText(estimate))
    }

    @Test
    fun `Wise Glasses with a Special move is modelled and does not block next to the field condition`() {
        val estimate = readyOf(
            fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, move = "Water Gun", attackerItem = wiseGlasses),
            "Wonder Room is ignored while Wise Glasses is modelled"
        )
        val glasses = estimate.verdict.hnsItemDecisions.single()
        assertEquals(HnsItemRequestRelevance.MODELLED, glasses.relevance)
        assertEquals("wise_glasses_special_move", glasses.rule)
        assertFalse(estimate.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(estimate.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals("\nIgnores:\nField: Wonder Room", ignoredText(estimate))
    }

    @Test
    fun `Wise Glasses category crossover under PER_MOVE_SPLIT vs TYPE_BASED`() {
        // Under PER_MOVE_SPLIT (optionStyle = 0): Dragon Claw is Physical.
        // Wise Glasses is PROVEN_IRRELEVANT (special_only_item_physical_move).
        // moveOverride.category is "Physical", engine rolls unboosted.
        val perMoveSplit = readyOf(
            fieldBuild(0, move = "Dragon Claw", attackerItem = wiseGlasses, optionStyle = 0),
            "Dragon Claw is Physical under PER_MOVE_SPLIT: Wise Glasses is proven irrelevant"
        )
        val perMoveGlasses = perMoveSplit.verdict.hnsItemDecisions.single()
        assertEquals("Wise Glasses", perMoveGlasses.itemName)
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, perMoveGlasses.relevance)
        assertEquals("special_only_item_physical_move", perMoveGlasses.rule)
        assertFalse(perMoveSplit.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals("Physical", perMoveSplit.verdict.request?.moveOverride?.category)

        // Under TYPE_BASED (optionStyle = 1): Dragon Claw (Dragon type) is Special.
        // Wise Glasses is MODELLED (wise_glasses_special_move).
        // The pinned category is explicit so the bundled Gen III category table cannot override it.
        val typeBased = readyOf(
            fieldBuild(0, move = "Dragon Claw", attackerItem = wiseGlasses, optionStyle = 1),
            "Dragon Claw is Special under TYPE_BASED: Wise Glasses is modelled"
        )
        val typeBasedGlasses = typeBased.verdict.hnsItemDecisions.single()
        assertEquals("Wise Glasses", typeBasedGlasses.itemName)
        assertEquals(HnsItemRequestRelevance.MODELLED, typeBasedGlasses.relevance)
        assertEquals("wise_glasses_special_move", typeBasedGlasses.rule)
        assertFalse(typeBased.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals("Special", typeBased.verdict.request?.moveOverride?.category)
        assertEquals("Wise Glasses", typeBased.verdict.request?.attacker?.item)
    }

    @Test
    fun `device-shaped Electric Terrain from a switch-in surge is decided per move`() {
        // The most likely Thor explanation: a Random Abilities lead with Electric Surge / Hadron
        // Engine sets Electric Terrain (0x00000100) on switch-in, before any move is chosen.
        val terrain = HnsFieldStatus.ELECTRIC_TERRAIN.mask
        val tackle = readyOf(fieldBuild(terrain, attackerItem = wiseGlasses),
            "neither Electric Terrain nor Wise Glasses can change Physical Tackle")
        assertEquals("electric_terrain_other_move", fieldDecision(tackle, HnsFieldStatus.ELECTRIC_TERRAIN).rule)
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, tackle.verdict.hnsItemDecisions.single().relevance)

        val waterGun = readyOf(fieldBuild(terrain, move = "Water Gun", attackerItem = wiseGlasses),
            "Electric Terrain is irrelevant to Water Gun and Wise Glasses is modelled")
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(waterGun, HnsFieldStatus.ELECTRIC_TERRAIN).relevance)
        assertFalse(waterGun.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertFalse(waterGun.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals(HnsItemRequestRelevance.MODELLED, waterGun.verdict.hnsItemDecisions.single().relevance)
        assertEquals("Wise Glasses", waterGun.verdict.request?.attacker?.item)

        val thunderShock = readyOf(fieldBuild(terrain, move = "Thunder Shock", attackerItem = wiseGlasses),
            "Electric Terrain is named and ignored while Wise Glasses is modelled")
        assertEquals("electric_terrain_grounded_electric_move", fieldDecision(thunderShock, HnsFieldStatus.ELECTRIC_TERRAIN).rule)
        assertFalse(thunderShock.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals("", ignoredText(thunderShock))
        assertEquals(terrain, thunderShock.request.hnsLiveBattleState?.fieldStatuses)

        // Hadron Engine now consumes the authoritative Electric Terrain word for its exact
        // Special Attack-stat branch. Electric moves remain a field caveat until the direct
        // terrain move modifier is implemented in the next slice.
        val hadron = readyOf(fieldBuild(terrain, move = "Psychic", attackerAbility = 289 to "Hadron Engine"),
            "observed Electric Terrain plus Hadron Engine models a non-Electric Special hit")
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(hadron, HnsFieldStatus.ELECTRIC_TERRAIN).relevance)
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            hadron.verdict.hnsAbilityDecisions.single { it.abilityId == 289 }.relevance)
        assertFalse(hadron.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        assertFalse(hadron.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertTrue(hadron.verdict.ignoredMechanics.isEmpty())
        assertEquals(terrain, hadron.request.hnsLiveBattleState?.fieldStatuses)
        assertEquals(terrain, JSONObject(buildCalcRequestJson(hadron.request)).getJSONObject("field")
            .getInt("hnsFieldStatuses"))

        val hadronElectric = readyOf(fieldBuild(terrain, move = "Thunderbolt", attackerAbility = 289 to "Hadron Engine"),
            "Electric Terrain and Hadron Engine compose on an Electric Special move")
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(hadronElectric, HnsFieldStatus.ELECTRIC_TERRAIN).relevance)
        assertTrue(hadronElectric.verdict.ignoredMechanics.none { it is IgnoredCalcMechanic.Field })
        assertEquals(terrain, hadronElectric.request.hnsLiveBattleState?.fieldStatuses)
    }

    @Test
    fun `MODELLED Grassy Terrain composes with Grass Pelt and keeps the live bit`() {
        val grassy = HnsFieldStatus.GRASSY_TERRAIN.mask
        val pelt = readyOf(fieldBuild(grassy, defenderAbility = 179 to "Grass Pelt"),
            "Grassy Terrain plus defender Grass Pelt exactly models a non-Grass Physical hit")
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(pelt, HnsFieldStatus.GRASSY_TERRAIN).relevance)
        assertFalse(pelt.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertTrue(pelt.verdict.ignoredMechanics.isEmpty())
        assertEquals(grassy, pelt.request.hnsLiveBattleState?.fieldStatuses)
        assertEquals(grassy, JSONObject(buildCalcRequestJson(pelt.request)).getJSONObject("field")
            .getInt("hnsFieldStatuses"))

        val peltWithItemCaveat = readyOf(
            fieldBuild(grassy, defenderItem = 481, defenderAbility = 179 to "Grass Pelt"),
            "a modelled terrain consequence does not clear an independent Focus Sash caveat"
        )
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(peltWithItemCaveat, HnsFieldStatus.GRASSY_TERRAIN).relevance)
        assertTrue(peltWithItemCaveat.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertEquals(listOf("Foe: Focus Sash"),
            peltWithItemCaveat.verdict.ignoredMechanics.map { it.presentationLine })
        assertEquals(grassy, peltWithItemCaveat.request.hnsLiveBattleState?.fieldStatuses)

        val grassMove = readyOf(fieldBuild(grassy, move = "Razor Leaf", defenderAbility = 179 to "Grass Pelt"),
            "the direct Grass boost and Grass Pelt compose exactly")
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(grassMove, HnsFieldStatus.GRASSY_TERRAIN).relevance)
        assertTrue(grassMove.verdict.ignoredMechanics.none { it is IgnoredCalcMechanic.Field })
        assertEquals(grassy, grassMove.request.hnsLiveBattleState?.fieldStatuses)
    }

    @Test
    fun `caller terrain text cannot create any live terrain modifier`() {
        val trust = trustFor(exactSha)
        val electricRequest = goldenARequest("Psychic").copy(
            field = CalcFieldInput(terrain = "Electric"),
            attacker = goldenARequest("Psychic").attacker.copy(ability = "Hadron Engine", abilityId = 289)
        )
        val electric = readyOf(build(trust, electricRequest,
            playerObservation(abilityId = 289, abilityName = "Hadron Engine", fieldStatuses = 0),
            enemyObservation(fieldStatuses = 0), randomAbilities = true),
            "caller Electric terrain cannot replace an observed zero field word")
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            electric.verdict.hnsAbilityDecisions.single { it.abilityId == 289 }.relevance)
        val electricJson = JSONObject(buildCalcRequestJson(electric.request)).getJSONObject("field")
        assertEquals(0, electricJson.getInt("hnsFieldStatuses"))
        assertEquals("Electric", electricJson.getString("terrain"))

        val grassyRequest = goldenARequest("Tackle").copy(
            field = CalcFieldInput(terrain = "Grassy"),
            defender = goldenARequest("Tackle").defender.copy(ability = "Grass Pelt", abilityId = 179)
        )
        val grassy = readyOf(build(trust, grassyRequest,
            playerObservation(fieldStatuses = 0), enemyObservation(fieldStatuses = 0, abilityId = 179,
                abilityName = "Grass Pelt"), randomAbilities = true),
            "caller Grassy terrain cannot replace an observed zero field word")
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            grassy.verdict.hnsAbilityDecisions.single { it.abilityId == 179 }.relevance)
        val grassyJson = JSONObject(buildCalcRequestJson(grassy.request)).getJSONObject("field")
        assertEquals(0, grassyJson.getInt("hnsFieldStatuses"))
        assertEquals("Grassy", grassyJson.getString("terrain"))

        for ((terrain, move) in listOf(
            "Misty" to "Dragon Breath",
            "Psychic" to "Confusion"
        )) {
            val request = goldenARequest(move).copy(field = CalcFieldInput(terrain = terrain))
            val outcome = readyOf(build(trust, request,
                playerObservation(fieldStatuses = 0), enemyObservation(fieldStatuses = 0), randomAbilities = true),
                "caller $terrain terrain cannot replace an observed zero field word")
            val field = JSONObject(buildCalcRequestJson(outcome.request)).getJSONObject("field")
            assertEquals(0, field.getInt("hnsFieldStatuses"))
            assertEquals(terrain, field.getString("terrain"))
        }
    }

    @Test
    fun `type-based category needs only effective-type authority`() {
        // TYPE_BASED: Normal Tackle is Physical from its type; Wonder Room does not change the type.
        val physical = readyOf(
            fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, attackerItem = wiseGlasses, optionStyle = 1),
            "Wonder Room is a named field caveat"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, physical.verdict.hnsItemDecisions.single().relevance)
        // Ion Deluge resolves the effective Electric type and its Special TYPE_BASED category,
        // while the existing dynamic-type blocker still refuses execution.
        val ionDeluge = refusedOf(
            fieldBuild(HnsFieldStatus.ION_DELUGE.mask, attackerItem = wiseGlasses, optionStyle = 1),
            "Ion Deluge on a Normal move stays refused"
        )
        assertEquals(HnsItemRequestRelevance.MODELLED, ionDeluge.verdict.hnsItemDecisions.single().relevance)
        assertTrue(ionDeluge.verdict.limitations.contains(CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED))
        // Under PER_MOVE_SPLIT the category does not depend on the type rewrite.
        val perMove = refusedOf(
            fieldBuild(HnsFieldStatus.ION_DELUGE.mask, attackerItem = wiseGlasses),
            "Ion Deluge on a Normal move stays refused"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, perMove.verdict.hnsItemDecisions.single().relevance)
    }

    @Test
    fun `Charcoal clears on a known non-Fire move despite an unrelated field bit`() {
        val estimate = readyOf(fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, attackerItem = 426), "Wonder Room is ignored")
        val charcoal = estimate.verdict.hnsItemDecisions.single()
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, charcoal.relevance)
        assertEquals("type_item_move_type_mismatch", charcoal.rule)
        assertFalse(estimate.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        // Missing dynamic-type authority still fails closed.
        val electrified = refusedOf(
            fieldBuild(HnsFieldStatus.TRICK_ROOM.mask, attackerItem = 426, electrified = true),
            "Electrify removes effective-type authority"
        )
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, electrified.verdict.hnsItemDecisions.single().relevance)
        assertTrue(electrified.verdict.limitations.contains(CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED))
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
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
        assertEquals(
            "Damage unavailable · 2 blockers\nField: Unknown bits 0x00002000\nYour Overgrow: The effective move type needed to decide this conditional ability is not authoritative.",
            cardText(refused)
        )
        assertEquals(
            "pinch_ability_effective_type_unverified",
            refused.verdict.hnsAbilityDecisions.single().rule
        )

        // An unknown bit also removes effective-type authority, so a co-set terrain cannot be cleared.
        val mixed = refusedOf(fieldBuild(0x2000 or HnsFieldStatus.ELECTRIC_TERRAIN.mask), "unknown bit blocks")
        assertEquals(0x2100, mixed.verdict.hnsFieldDiagnostics?.fieldState?.raw)
        assertEquals(HnsFieldRequestRelevance.UNKNOWN, fieldDecision(mixed, HnsFieldStatus.ELECTRIC_TERRAIN).relevance)
        assertEquals(
            "Damage unavailable · 3 blockers\nField: Electric Terrain (0x00000100)\nField: Unknown bits 0x00002000\nYour Overgrow: The effective move type needed to decide this conditional ability is not authoritative.",
            cardText(mixed)
        )
    }

    @Test
    fun `multiple active field bits are decided independently`() {
        val word = HnsFieldStatus.MUD_SPORT.mask or HnsFieldStatus.WATER_SPORT.mask or HnsFieldStatus.TRICK_ROOM.mask
        val estimate = readyOf(fieldBuild(word, move = "Thunder Shock"), "Mud Sport is a named field caveat")
        assertEquals(HnsFieldRequestRelevance.RELEVANT, fieldDecision(estimate, HnsFieldStatus.MUD_SPORT).relevance)
        assertEquals(HnsFieldRequestRelevance.PROVEN_IRRELEVANT, fieldDecision(estimate, HnsFieldStatus.WATER_SPORT).relevance)
        assertEquals(HnsFieldRequestRelevance.PROVEN_IRRELEVANT, fieldDecision(estimate, HnsFieldStatus.TRICK_ROOM).relevance)
        assertEquals("\nIgnores:\nField: Mud Sport", ignoredText(estimate))

        val both = readyOf(fieldBuild(word or HnsFieldStatus.WONDER_ROOM.mask, move = "Thunder Shock"), "two named field caveats")
        assertEquals("\nIgnores:\nField: Wonder Room\nField: Mud Sport", ignoredText(both))
        readyOf(fieldBuild(word, move = "Tackle"), "no active bit can change Tackle")
    }

    @Test
    fun `clearing a field bit never clears another limitation and vice versa`() {
        // Electric Terrain is irrelevant to Tackle, but the live attacker status still blocks.
        val status = refusedOf(fieldBuild(HnsFieldStatus.ELECTRIC_TERRAIN.mask, status1 = 0x10), "status blocks")
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(status, HnsFieldStatus.ELECTRIC_TERRAIN).relevance)
        assertFalse(status.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertTrue(status.verdict.limitations.contains(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED))

        // A relevant item and a relevant field condition are both kept.
        val both = readyOf(fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, defenderItem = 481), "Foe Focus Sash + Wonder Room are named caveats")
        assertTrue(both.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        assertTrue(both.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
        assertEquals(
            setOf("Field: Wonder Room", "Foe: Focus Sash"),
            both.verdict.ignoredMechanics.map { it.presentationLine }.toSet()
        )

        // The unsupported move is a hard blocker. The fully evidenced Focus Sash effect remains a
        // named soft caveat and must not be promoted into a blocker merely because execution is
        // already refused for the independent move mechanic.
        val three = readyOf(fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, move = "Water Gun",
            defenderItem = 481, attackerAbility = 62 to "Guts", status1 = 0),
            "known relevant field and item modifiers are both caveated")
        assertEquals(setOf("Field: Wonder Room", "Foe: Focus Sash"),
            three.verdict.ignoredMechanics.map { it.presentationLine }.toSet())
        assertFalse(three.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        val withMove = refusedOf(fieldBuild(HnsFieldStatus.WONDER_ROOM.mask, move = "Seismic Toss",
            defenderItem = 481, attackerAbility = 54 to "Truant"), "field + caveatable ability + item + move")
        assertEquals(
            "item decisions: ${withMove.verdict.hnsItemDecisions}",
            setOf(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED, CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED),
            withMove.verdict.blockingLimitations.toSet()
        )
        assertTrue(cardText(withMove).contains("Truant"))
        assertTrue(cardText(withMove).contains("Move effect not modelled"))

        // The ordinary hit still has an independent unread Truant execution state.
        // Field/item caveats cannot erase either that refusal or the status refusal.
        val withHardStatus = refusedOf(fieldBuild(
            HnsFieldStatus.WONDER_ROOM.mask,
            defenderItem = 481,
            attackerAbility = 54 to "Truant",
            status1 = 0x10
        ), "live status blocks beside three complete caveat decisions")
        assertEquals(setOf(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED, CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED),
            withHardStatus.verdict.blockingLimitations.toSet())
        assertTrue(cardText(withHardStatus).contains("Truant"))
        assertTrue(cardText(withHardStatus).contains("Status not modelled"))
    }

    @Test
    fun `refusal presentation lists unknown decisions but omits neighboring caveats`() {
        val mixedAbilities = refusedOf(
            fieldBuild(
                0,
                attackerAbility = 37 to "Huge Power",
                defenderAbility = 196 to "Merciless"
            ),
            "unknown Merciless blocks while attacker Huge Power has complete caveat evidence"
        )
        val abilityBlockers = com.dualdex.battle.DamageBlockerPresentation.from(mixedAbilities.verdict, false)
        assertEquals(1, abilityBlockers.size)
        assertTrue(abilityBlockers.single().detail.startsWith("Foe's Merciless:"))
        assertTrue(mixedAbilities.verdict.ignoredMechanics.any {
            it.presentationLine == "You: Huge Power"
        })

        val mixedItems = refusedOf(
            build(
                trustFor(exactSha), goldenARequest(),
                playerObservation(itemId = 426, electrified = true),
                enemyObservation(itemId = 481, hp = 15, maxHp = 15)
            ),
            "Electrify blocks execution while the foe's Focus Sash has caveat evidence"
        )
        assertTrue(mixedItems.verdict.hnsItemDecisions.any {
            it.itemName == "Charcoal" && it.relevance == HnsItemRequestRelevance.PROVEN_IRRELEVANT
        })
        assertTrue(mixedItems.verdict.limitations.contains(CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED))
        assertTrue(mixedItems.verdict.ignoredMechanics.any {
            it.presentationLine == "Foe: Focus Sash"
        })
        val itemDetails = com.dualdex.battle.DamageBlockerPresentation.detailLines(
            com.dualdex.battle.DamageBlockerPresentation.from(mixedItems.verdict, false)
        )
        assertFalse(itemDetails.any { it == "You: Charcoal" })
        assertFalse(itemDetails.any { it == "Foe: Focus Sash" })

        val mixedFields = refusedOf(
            fieldBuild(HnsFieldStatus.WONDER_ROOM.mask or 0x2000,
                attackerAbility = 0 to "None"),
            "unknown field bits block while Wonder Room has caveat evidence"
        )
        assertEquals(
            "Damage unavailable · Unknown field state 0x00002000\nField: Unknown bits 0x00002000",
            cardText(mixedFields)
        )
        assertTrue(mixedFields.verdict.ignoredMechanics.any {
            it.presentationLine == "Field: Wonder Room"
        })
    }

    @Test
    fun `Trick Room is relevant only to an Analytic attacker`() {
        val ready = readyOf(fieldBuild(HnsFieldStatus.TRICK_ROOM.mask), "Trick Room cannot change Tackle")
        assertEquals("trick_room_attacker_not_analytic", fieldDecision(ready, HnsFieldStatus.TRICK_ROOM).rule)
        val analytic = refusedOf(fieldBuild(HnsFieldStatus.TRICK_ROOM.mask, attackerAbility = 148 to "Analytic"),
            "Trick Room cannot make an unaudited Analytic ability safe")
        assertEquals("trick_room_attacker_analytic", fieldDecision(analytic, HnsFieldStatus.TRICK_ROOM).rule)
        assertTrue(analytic.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
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
            Triple(HnsFieldStatus.MISTY_TERRAIN, "Dragon Breath", "misty_terrain_ungrounded_defender_dragon"),
            Triple(HnsFieldStatus.MISTY_TERRAIN, "Tackle", "misty_terrain_non_dragon_move"),
            Triple(HnsFieldStatus.PSYCHIC_TERRAIN, "Confusion", "psychic_terrain_grounded_attacker_nonpriority_move"),
            Triple(HnsFieldStatus.PSYCHIC_TERRAIN, "Quick Attack", "psychic_terrain_priority_move"),
            Triple(HnsFieldStatus.PSYCHIC_TERRAIN, "Tackle", "psychic_terrain_non_psychic_nonpriority_move"),
            Triple(HnsFieldStatus.WATER_SPORT, "Ember", "water_sport_fire_move"),
            Triple(HnsFieldStatus.GRASSY_TERRAIN, "Vine Whip", "grassy_terrain_grounded_attacker_grass_move")
        )
        for ((status, move, rule) in cases) {
            val outcome = fieldBuild(status.mask, move = move)
            assertEquals("$status / $move", rule, fieldDecision(outcome, status).rule)
            val blocks = outcome.verdict().limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED)
            assertEquals("$status / $move",
                (status == HnsFieldStatus.PSYCHIC_TERRAIN && move == "Quick Attack") ||
                    (status == HnsFieldStatus.WATER_SPORT && move == "Ember"), blocks)
        }
        // Grass Pelt reads Grassy Terrain even for a non-Grass move.
        val pelt = fieldBuild(HnsFieldStatus.GRASSY_TERRAIN.mask, defenderAbility = 179 to "Grass Pelt")
        assertEquals("grassy_terrain_grass_pelt_physical_composition", fieldDecision(pelt, HnsFieldStatus.GRASSY_TERRAIN).rule)
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(pelt, HnsFieldStatus.GRASSY_TERRAIN).relevance)
        // Gale Wings can grant Flying priority: Psychic Terrain stays unknown.
        val gale = fieldBuild(HnsFieldStatus.PSYCHIC_TERRAIN.mask, attackerAbility = 177 to "Gale Wings")
        assertEquals(HnsFieldRequestRelevance.UNKNOWN, fieldDecision(gale, HnsFieldStatus.PSYCHIC_TERRAIN).relevance)
    }

    @Test
    fun `Misty and Psychic terrain exact cases keep their live bit without a field caveat`() {
        for ((status, move) in listOf(
            HnsFieldStatus.MISTY_TERRAIN to "Dragon Breath",
            HnsFieldStatus.PSYCHIC_TERRAIN to "Confusion"
        )) {
            val outcome = readyOf(fieldBuild(status.mask, move = move), "$status direct modifier is exact")
            assertEquals(HnsFieldRequestRelevance.MODELLED, fieldDecision(outcome, status).relevance)
            assertFalse(outcome.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))
            assertTrue(outcome.verdict.ignoredMechanics.none { it is IgnoredCalcMechanic.Field })
            assertEquals(status.mask, outcome.request.hnsLiveBattleState?.fieldStatuses)
            assertEquals(status.mask, JSONObject(buildCalcRequestJson(outcome.request)).getJSONObject("field")
                .getInt("hnsFieldStatuses"))
        }
    }

    @Test
    fun `Mold Breaker grounding uses the active Ability Shield hold effect`() {
        val trust = trustFor(exactSha)
        val terrain = HnsFieldStatus.MISTY_TERRAIN.mask
        val request = matchupRequest("Dragon Breath", "Snorlax").copy(
            attacker = liveInput("Chikorita", 5, 104, "Mold Breaker"),
            defender = liveInput("Snorlax", 3, 26, "Levitate")
        )
        val moldBreaker = playerObservation(
            abilityId = 104, abilityName = "Mold Breaker", fieldStatuses = terrain
        )
        val levitate = enemyObservation(
            speciesId = 143, types = listOf(1), abilityId = 26, abilityName = "Levitate",
            fieldStatuses = terrain
        )
        val unshielded = readyOf(
            build(trust, request, moldBreaker, levitate, randomAbilities = true),
            "Mold Breaker suppresses breakable defender Levitate when Ability Shield is absent"
        )
        assertEquals(HnsTerrainApplicability.AFFECTED,
            unshielded.request.hnsLiveBattleState?.defenderTerrainApplicability)
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(unshielded, HnsFieldStatus.MISTY_TERRAIN).relevance)
        assertFalse(unshielded.verdict.limitations.contains(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED))

        val shielded = readyOf(
            build(trust, request, moldBreaker,
                levitate.copy(state = levitate.state.copy(itemId = 758)), randomAbilities = true),
            "Ability Shield preserves defender Levitate against Mold Breaker"
        )
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            shielded.request.hnsLiveBattleState?.defenderTerrainApplicability)
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(shielded, HnsFieldStatus.MISTY_TERRAIN).relevance)
        val fieldJson = JSONObject(buildCalcRequestJson(shielded.request)).getJSONObject("field")
        assertEquals(terrain, fieldJson.getInt("hnsFieldStatuses"))
        assertFalse(fieldJson.getBoolean("hnsTerrainDefenderAffected"))
        assertTrue(JSONObject(buildCalcRequestJson(shielded.request)).getJSONObject("defender")
            .getBoolean("hnsAbilityShield"))

        val embargoSuppressesShield = readyOf(
            build(trust, request, moldBreaker,
                levitate.copy(state = levitate.state.copy(
                    itemId = 758, itemVolatilesObserved = true, volatileEmbargo = true
                )), randomAbilities = true),
            "Embargo suppresses Ability Shield, allowing Mold Breaker to suppress Levitate"
        )
        assertEquals(HnsTerrainApplicability.AFFECTED,
            embargoSuppressesShield.request.hnsLiveBattleState?.defenderTerrainApplicability)
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(embargoSuppressesShield, HnsFieldStatus.MISTY_TERRAIN).relevance)
        assertFalse(JSONObject(buildCalcRequestJson(embargoSuppressesShield.request))
            .getJSONObject("defender").getBoolean("hnsAbilityShield"))
    }

    @Test
    fun `attacker Levitate is irrelevant to a nonmatching move under Grassy Terrain`() {
        val ready = readyOf(fieldBuild(
            HnsFieldStatus.GRASSY_TERRAIN.mask,
            move = "Water Gun",
            attackerAbility = 26 to "Levitate"
        ), "attacker Levitate cannot affect a non-Grass move under Grassy Terrain")
        assertTrue(ready.verdict.hnsAbilityDecisions.none { it.abilityId == 26 })
        assertFalse(ready.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
        assertEquals(HnsFieldRequestRelevance.MODELLED,
            fieldDecision(ready, HnsFieldStatus.GRASSY_TERRAIN).relevance)
    }

    @Test
    fun `Magic Room models suppressed hold effects but remains conservative for unresolved identity`() {
        readyOf(fieldBuild(HnsFieldStatus.MAGIC_ROOM.mask), "no held items")
        val neutral = readyOf(fieldBuild(HnsFieldStatus.MAGIC_ROOM.mask, attackerItem = everstone, defenderItem = everstone),
            "Everstone's hold effect never reaches damage")
        assertEquals("magic_room_held_items_neutral", fieldDecision(neutral, HnsFieldStatus.MAGIC_ROOM).rule)
        val charcoal = readyOf(fieldBuild(HnsFieldStatus.MAGIC_ROOM.mask, attackerItem = 426),
            "Magic Room suppresses the exact Charcoal hold effect")
        assertEquals(HnsFieldRequestRelevance.MODELLED, fieldDecision(charcoal, HnsFieldStatus.MAGIC_ROOM).relevance)
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT, charcoal.verdict.hnsItemDecisions.single().relevance)
        val unresolved = refusedOf(fieldBuild(HnsFieldStatus.MAGIC_ROOM.mask, attackerItem = 581),
            "the e-Reader Enigma Berry's runtime hold effect remains unresolved")
        assertEquals(HnsFieldRequestRelevance.UNKNOWN, fieldDecision(unresolved, HnsFieldStatus.MAGIC_ROOM).relevance)
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

    @Test
    fun `known unsupported ability is a caveated estimate and every ability representation is neutralized`() {
        val ready = readyOf(
            build(
                trustFor(exactSha),
                goldenARequest(),
                playerObservation(abilityId = 37, abilityName = "Huge Power"),
                enemyObservation()
            ),
            "a known unsupported ability has a trustworthy base request"
        )
        assertTrue(ready.verdict.isCaveatedEstimate)
        assertEquals(listOf("You: Huge Power"), ready.verdict.ignoredMechanics.map { it.presentationLine })
        assertEquals("(other)", ready.request.attacker.ability)
        assertNull(ready.request.attacker.abilityId)
        assertEquals("Huge Power", ready.verdict.hnsAbilityDecisions.single().abilityName)
        val json = buildCalcRequestJson(ready.request)
        assertTrue(json.contains("\"ability\":\"(other)\""))
        assertFalse(json.contains("Huge Power"))
    }

    @Test
    fun `known unsupported item is neutralized while its identity remains in the verdict`() {
        val ready = readyOf(
            build(trustFor(exactSha), goldenARequest(), playerObservation(), enemyObservation(itemId = 481)),
            "a known unsupported item has a trustworthy base request"
        )
        assertTrue(ready.verdict.isCaveatedEstimate)
        assertEquals(listOf("Foe: Focus Sash"), ready.verdict.ignoredMechanics.map { it.presentationLine })
        assertNull(ready.request.defender.item)
        assertNull(ready.request.defender.itemId)
        assertEquals("Focus Sash", ready.verdict.hnsItemDecisions.single().itemName)
        val json = buildCalcRequestJson(ready.request)
        assertFalse(json.contains("Focus Sash"))
        assertFalse(json.contains("\"item\""))
    }

    @Test
    fun `ability and item caveats are both retained in one authorized request`() {
        val ready = readyOf(
            build(
                trustFor(exactSha),
                goldenARequest(),
                playerObservation(abilityId = 37, abilityName = "Huge Power"),
                enemyObservation(itemId = 481)
            ),
            "the ability and foe item caveats can be neutralized independently"
        )
        assertEquals(listOf("You: Huge Power", "Foe: Focus Sash"), ready.verdict.ignoredMechanics.map { it.presentationLine })
        assertEquals("(other)", ready.request.attacker.ability)
        assertNull(ready.request.attacker.abilityId)
        assertNull(ready.request.defender.item)
        assertNull(ready.request.defender.itemId)
    }

    @Test
    fun `a relevant field and unsupported ability are both named and neutralized`() {
        val ready = readyOf(
            fieldBuild(
                HnsFieldStatus.WONDER_ROOM.mask,
                move = "Tackle",
                attackerAbility = 37 to "Huge Power"
            ),
            "the known field effect and ability have separate neutral forms"
        )
        assertEquals(
            setOf("You: Huge Power", "Field: Wonder Room"),
            ready.verdict.ignoredMechanics.map { it.presentationLine }.toSet()
        )
        assertEquals("(other)", ready.request.attacker.ability)
        assertNull(ready.request.attacker.abilityId)
        assertEquals(0, ready.request.hnsLiveBattleState?.fieldStatuses)
        val json = buildCalcRequestJson(ready.request)
        assertFalse("raw field status must not reach the engine: $json", json.contains("fieldStatuses"))
        assertFalse("removed field modifier must not reach the engine: $json", json.contains("Wonder Room"))
    }

    @Test
    fun `hard move blocker wins when ability relevance cannot be established`() {
        val refused = refusedOf(
            build(
                trustFor(exactSha),
                goldenARequest(move = "Explosion"),
                playerObservation(abilityId = 105, abilityName = "Super Luck"),
                enemyObservation()
            ),
            "the unsupported move damage path remains a hard refusal"
        )
        assertNull(refused.verdict.request)
        assertTrue(refused.verdict.blockingLimitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED))
        assertTrue(refused.verdict.hnsAbilityDecisions.any {
            it.abilityName == "Super Luck" && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
        })
        assertTrue(refused.verdict.ignoredMechanics.isEmpty())
        assertEquals(
            "Damage unavailable · 2 blockers\nYour Super Luck: " +
                com.dualdex.pokemon.hns.HnsGroupEData.abilityDispositions.getValue(105).reason +
                "\nMove effect not modelled",
            cardText(refused)
        )
    }

    @Test
    fun `unknown species and Doubles remain hard with supported soft evidence`() {
        val unknownSpecies = refusedOf(
            build(
                trustFor(exactSha),
                goldenARequest().copy(defender = liveInput("Unknown species", 3, 77, "Tangled Feet")),
                playerObservation(abilityId = 37, abilityName = "Huge Power"),
                enemyObservation()
            ),
            "an unsupported species cannot become a base request"
        )
        assertTrue(unknownSpecies.verdict.blockingLimitations.any {
            it == CalcLimitation.SPECIES_NOT_IN_PINNED_DATA || it == CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN
        })
        assertEquals(listOf("You: Huge Power"), unknownSpecies.verdict.ignoredMechanics.map { it.presentationLine })
        assertFalse("a caveatable ability is not presented as an unknown-species blocker",
            cardText(unknownSpecies).contains("Huge Power"))

        val doubles = refusedOf(
            build(
                trustFor(exactSha),
                goldenARequest(),
                playerObservation(observedBattlersCount = 4),
                enemyObservation(itemId = 481, observedBattlersCount = 4)
            ),
            "unsupported Doubles arithmetic remains hard beside a known item"
        )
        assertTrue(doubles.verdict.blockingLimitations.contains(CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED))
        assertTrue(doubles.verdict.ignoredMechanics.any { it.presentationLine == "Foe: Focus Sash" })
        assertNull(doubles.verdict.request)
    }

    @Test
    fun `Calc screen caveat path sanitizes and verifies echoes before presentation`() {
        val ready = readyOf(
            build(
                trustFor(exactSha),
                goldenARequest(),
                playerObservation(abilityId = 37, abilityName = "Huge Power"),
                enemyObservation(itemId = 481)
            ),
            "Calc screen uses the same production caveat verdict"
        )
        var executed: DamageCalculationRequest? = null
        val success = CalcAuthorizedExecution.calculate(ready.verdict) { request ->
            executed = request
            DamageCalculationResponse(
                success = true,
                minDamage = 42,
                maxDamage = 50,
                range = listOf(42, 50),
                engineEcho = CalcEngineOperandEcho("(other)", "(other)", null, null, true)
            )
        }
        assertTrue(success.success)
        assertEquals("(other)", executed?.attacker?.ability)
        assertNull(executed?.attacker?.abilityId)
        assertNull(executed?.defender?.item)
        assertNull(executed?.defender?.itemId)
        assertTrue(CalcResultPresentation.forVerdict(ready.verdict, ready.request).headline.contains(
            "⚠️ Approximate — estimate ignores:\nYou: Huge Power\nFoe: Focus Sash"
        ))

        val missingEcho = CalcAuthorizedExecution.calculate(ready.verdict) {
            DamageCalculationResponse(success = true, minDamage = 42, maxDamage = 50, range = listOf(42, 50))
        }
        assertFalse(missingEcho.success)
        assertEquals(CalcAuthorizedExecution.ECHO_FAILURE, missingEcho.error)

        val contradictoryEcho = CalcAuthorizedExecution.calculate(ready.verdict) {
            DamageCalculationResponse(
                success = true,
                minDamage = 42,
                maxDamage = 50,
                range = listOf(42, 50),
                engineEcho = CalcEngineOperandEcho("Huge Power", "(other)", null, "Focus Sash", true)
            )
        }
        assertFalse(contradictoryEcho.success)
        assertEquals(CalcAuthorizedExecution.ECHO_FAILURE, contradictoryEcho.error)
    }

    @Test
    fun `manual Calc remains refused when badge applicability is not authoritative`() {
        val manualInput = goldenARequest().let { request ->
            request.copy(
                attacker = request.attacker.copy(
                    origin = CalcInputOrigin.MANUAL,
                    ability = "Huge Power",
                    abilityId = 37,
                    item = "Silk Scarf",
                    itemId = 425
                ),
                defender = request.defender.copy(origin = CalcInputOrigin.MANUAL)
            )
        }
        val refused = refusedOf(
            build(
                trustFor(exactSha),
                manualInput,
                player = null,
                enemy = null,
                activeBattle = false
            ),
            "manual H&S needs an authoritative badge observation"
        )
        assertTrue(refused.verdict.blockingLimitations.contains(CalcLimitation.BADGE_BOOST_NOT_MODELLED))
        assertEquals(
            listOf("You: Huge Power"),
            refused.verdict.ignoredMechanics.map { it.presentationLine }
        )
        assertTrue(
            CalcResultPresentation.forVerdict(refused.verdict).headline
                .startsWith(CalcResultPresentation.UNSUPPORTED_PREFIX)
        )

        var engineCalled = false
        val response = CalcAuthorizedExecution.calculate(refused.verdict) {
            engineCalled = true
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1)
        }
        assertFalse(response.success)
        assertFalse(engineCalled)
    }

    @Test
    fun `known item caveat cannot bypass unread ability state`() {
        val observed = playerObservation()
        val unreadAbility = observed.copy(
            state = observed.state.copy(abilityId = null, abilityOutOfDomain = true),
            abilityIdentity = null
        )
        val refused = refusedOf(
            build(trustFor(exactSha), goldenARequest(), unreadAbility, enemyObservation(itemId = 481)),
            "an unread effective ability stays hard even with a known item caveat"
        )
        assertNull(refused.verdict.request)
        assertTrue(refused.verdict.blockingLimitations.contains(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE))
        assertTrue(refused.verdict.ignoredMechanics.any { it.presentationLine == "Foe: Focus Sash" })
        var called = false
        val response = CalcAuthorizedExecution.calculate(refused.verdict) {
            called = true
            DamageCalculationResponse(success = true, minDamage = 1, maxDamage = 1)
        }
        assertFalse(response.success)
        assertFalse("hard unread ability state must prevent any engine call", called)
    }

    @Test
    fun `Group C modeled immunity survives boundary and authorized execution`() {
        val ready = readyOf(
            build(
                trustFor(exactSha),
                goldenARequest("Thunderbolt"),
                playerObservation(),
                enemyObservation(abilityId = 10, abilityName = "Volt Absorb"),
                randomAbilities = true
            ),
            "the observed Volt Absorb defender is a modeled Group C immunity"
        )
        var engineRequest: DamageCalculationRequest? = null
        val result = CalcAuthorizedExecution.calculate(ready.verdict) { request ->
            engineRequest = request
            val move = JSONObject(buildCalcRequestJson(request)).getJSONObject("move")
            assertEquals(85, move.getInt("hnsMoveId"))
            assertTrue(move.getJSONArray("hnsMoveFlags").length() == 0)
            DamageCalculationResponse(
                success = true,
                minDamage = 0,
                maxDamage = 0,
                range = List(16) { 0 },
                effectiveness = 0.0,
                immunityCauses = listOf(
                    CalcImmunityCause("ability", "src/battle_util.c:2438", "Volt Absorb")
                )
            )
        }
        assertTrue(result.success)
        assertEquals(16, result.range.size)
        assertTrue(result.range.all { it == 0 })
        assertEquals(0.0, result.effectiveness!!, 0.0)
        assertEquals("Volt Absorb", result.immunityCauses.single().name)
        assertEquals(10, engineRequest?.defender?.abilityId)
    }

    @Test
    fun `source move contact and Sheer Force metadata reach engine serialization`() {
        val contact = readyOf(
            build(
                trustFor(exactSha), goldenARequest("Fire Punch"), playerObservation(), enemyObservation()
            ),
            "Fire Punch is a source-derived ordinary contact move"
        ).request
        val contactJson = JSONObject(buildCalcRequestJson(contact)).getJSONObject("move")
        assertEquals(true, contactJson.getBoolean("hnsMakesContact"))
        assertTrue(contactJson.getJSONArray("hnsMoveAbilityFlags").let { flags ->
            (0 until flags.length()).any { flags.getString(it) == "punchingMove" }
        })
        assertEquals("EFFECT_HIT", contactJson.getString("hnsMoveEffect"))

        val affected = readyOf(
            build(trustFor(exactSha), goldenARequest("Scald"), playerObservation(), enemyObservation()),
            "Scald's pinned secondary-effect metadata is authoritative"
        ).request
        val affectedJson = JSONObject(buildCalcRequestJson(affected)).getJSONObject("move")
        assertEquals(true, affectedJson.getBoolean("hnsSheerForceAffected"))

        val unaffected = readyOf(
            build(trustFor(exactSha), goldenARequest("Pay Day"), playerObservation(), enemyObservation()),
            "Pay Day is not affected by Sheer Force in the pinned helper"
        ).request
        val unaffectedJson = JSONObject(buildCalcRequestJson(unaffected)).getJSONObject("move")
        assertEquals(false, unaffectedJson.getBoolean("hnsSheerForceAffected"))
    }

    @Test
    fun `effective hold effect gates item modifiers and Punching Glove contact`() {
        val gloveId = com.dualdex.pokemon.hns.HnsItemRegistry.resolveIdByName("Punching Glove")
        val active = readyOf(build(
            trustFor(exactSha), goldenARequest("Fire Punch"),
            playerObservation(abilityId = 181, abilityName = "Tough Claws", itemId = gloveId),
            enemyObservation(), randomAbilities = true
        ), "active Punching Glove boosts a punching move and removes contact")
        assertEquals(HnsItemRequestRelevance.MODELLED,
            active.verdict.hnsItemDecisions.single().relevance)
        assertEquals(
            HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            active.verdict.hnsAbilityDecisions.single { it.abilityId == 181 }.relevance
        )
        assertEquals("HOLD_EFFECT_PUNCHING_GLOVE",
            JSONObject(buildCalcRequestJson(active.request)).getJSONObject("attacker")
                .getString("hnsEffectiveHoldEffect"))

        val activeGloveFluffy = readyOf(build(
            trustFor(exactSha), goldenARequest("Fire Punch"),
            playerObservation(itemId = gloveId),
            enemyObservation(abilityId = 218, abilityName = "Fluffy"), randomAbilities = true
        ), "active Punching Glove makes Fire Punch non-contact for Fluffy")
        assertEquals(HnsAbilityRequestRelevance.RELEVANT,
            activeGloveFluffy.verdict.hnsAbilityDecisions.single { it.abilityId == 218 }.relevance)

        val embargo = readyOf(build(
            trustFor(exactSha), goldenARequest("Fire Punch"),
            playerObservation(itemId = gloveId, embargo = true),
            enemyObservation(abilityId = 218, abilityName = "Fluffy"), randomAbilities = true
        ), "Embargo removes both the glove boost and its contact suppression")
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT,
            embargo.verdict.hnsItemDecisions.single().relevance)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            embargo.verdict.hnsAbilityDecisions.single { it.abilityId == 218 }.relevance)
        val serialized = JSONObject(buildCalcRequestJson(embargo.request)).getJSONObject("attacker")
        assertEquals("SUPPRESSED_NONE", serialized.getString("hnsHoldEffectState"))
        assertEquals("HOLD_EFFECT_NONE", serialized.getString("hnsEffectiveHoldEffect"))

        val klutz = readyOf(build(
            trustFor(exactSha), goldenARequest(),
            playerObservation(abilityId = 103, abilityName = "Klutz", itemId = 426),
            enemyObservation(), randomAbilities = true
        ), "effective Klutz suppresses Charcoal without a duplicate Klutz blocker")
        assertEquals(HnsItemRequestRelevance.PROVEN_IRRELEVANT,
            klutz.verdict.hnsItemDecisions.single().relevance)
        assertEquals(HnsAbilityRequestRelevance.PROVEN_IRRELEVANT,
            klutz.verdict.hnsAbilityDecisions.single { it.abilityId == 103 }.relevance)
        assertFalse(klutz.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))

        val gastroAcid = refusedOf(build(
            trustFor(exactSha), goldenARequest(),
            playerObservation(abilityId = 103, abilityName = "Klutz", itemId = 426, gastroAcid = true),
            enemyObservation(), randomAbilities = true
        ), "Gastro Acid restores the hold effect while its independent ability blocker remains")
        assertTrue(gastroAcid.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED))
        assertFalse(gastroAcid.verdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
    }

    @Test
    fun `Mold Breaker blocks only when a defender immunity can change this hit`() {
        val trust = trustFor(exactSha)
        val moldBreaker = playerObservation(abilityId = 104, abilityName = "Mold Breaker")
        readyOf(
            build(trust, goldenARequest(), moldBreaker, enemyObservation(), randomAbilities = true),
            "Mold Breaker cannot alter a Tackle hit on Tangled Feet"
        )

        val relevant = refusedOf(
            build(
                trust,
                goldenARequest("Thunderbolt"),
                moldBreaker,
                enemyObservation(abilityId = 10, abilityName = "Volt Absorb"),
                randomAbilities = true
            ),
            "Mold Breaker suppresses the otherwise relevant Volt Absorb immunity"
        )
        assertTrue(relevant.verdict.blockingLimitations.contains(
            CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED
        ))

        readyOf(
            build(
                trust,
                goldenARequest("Thunderbolt"),
                moldBreaker,
                enemyObservation(abilityId = 10, abilityName = "Volt Absorb", itemId = 758),
                randomAbilities = true
            ),
            "the pinned Ability Shield check preserves Volt Absorb"
        )

        readyOf(
            build(
                trust,
                goldenARequest("Earth Power"),
                moldBreaker,
                enemyObservation(abilityId = 26, abilityName = "Levitate"),
                randomAbilities = true
            ),
            "Mold Breaker cannot change Pidgey's existing Ground type immunity"
        )

        val ringTargetLevitate = refusedOf(
            build(
                trust,
                goldenARequest("Earth Power"),
                moldBreaker,
                enemyObservation(abilityId = 26, abilityName = "Levitate", itemId = 499),
                randomAbilities = true
            ),
            "Ring Target removes Pidgey's type immunity, making Levitate relevant to Mold Breaker"
        )
        assertTrue(ringTargetLevitate.verdict.blockingLimitations.contains(
            CalcLimitation.HNS_MOLD_BREAKER_SUPPRESSION_NOT_MODELLED
        ))

        readyOf(
            build(
                trust,
                goldenARequest("Earth Power"),
                moldBreaker,
                enemyObservation(abilityId = 26, abilityName = "Levitate", itemId = 484),
                randomAbilities = true
            ),
            "Iron Ball grounds Pidgey, so Levitate does not change the Ground hit"
        )
    }

    @Test
    fun `priority blocking uses source priority only at the observed action selection phase`() {
        val trust = trustFor(exactSha)
        val dazzling = enemyObservation(abilityId = 219, abilityName = "Dazzling")
        val quick = readyOf(
            build(trust, goldenARequest("Quick Attack"), playerObservation(), dazzling, randomAbilities = true),
            "Quick Attack priority is source-proven during action selection"
        )
        val quickJson = JSONObject(buildCalcRequestJson(quick.request)).getJSONObject("move")
        assertEquals(1, quickJson.getInt("effectivePriority"))
        assertEquals(1, quickJson.getInt("hnsTargetClass"))
        val blockedResult = CalcAuthorizedExecution.calculate(quick.verdict) {
            DamageCalculationResponse(
                success = true, minDamage = 0, maxDamage = 0, range = List(16) { 0 },
                effectiveness = 0.0,
                immunityCauses = listOf(CalcImmunityCause(
                    "ability", "src/battle_move_resolution.c:1406", "Dazzling"
                ))
            )
        }
        assertTrue(blockedResult.success)
        assertEquals(16, blockedResult.range.size)
        assertTrue(blockedResult.range.all { it == 0 })

        readyOf(
            build(trust, goldenARequest("Tackle"), playerObservation(), dazzling, randomAbilities = true),
            "zero-priority Tackle is not blocked by Dazzling"
        )

        val unreadPhase = refusedOf(
            build(
                trust,
                goldenARequest("Quick Attack"),
                playerObservation(switchInEventsSettled = false),
                dazzling,
                randomAbilities = true
            ),
            "priority is not guessed outside the observed action-selection phase"
        )
        assertTrue(unreadPhase.verdict.blockingLimitations.contains(
            CalcLimitation.HNS_IMMUNITY_CONTEXT_UNVERIFIED
        ))
    }

    @Test
    fun `Flash Fire attacker boost uses the live activation flag`() {
        val clear = playerObservation(abilityId = 18, abilityName = "Flash Fire",
            groupDVolatilesObserved = true, volatileFlashFireBoosted = false)
        val clearReady = readyOf(
            build(trustFor(exactSha), goldenARequest("Ember"), clear, enemyObservation(), randomAbilities = true),
            "an observed false Flash Fire activation flag proves the boost inactive"
        )
        assertFalse(JSONObject(buildCalcRequestJson(clearReady.request)).getJSONObject("attacker")
            .getBoolean("hnsFlashFireBoosted"))

        val boosted = playerObservation(abilityId = 18, abilityName = "Flash Fire",
            groupDVolatilesObserved = true, volatileFlashFireBoosted = true)
        val boostedReady = readyOf(
            build(trustFor(exactSha), goldenARequest("Ember"), boosted, enemyObservation(), randomAbilities = true),
            "an observed true Flash Fire activation flag enables the modeled Attack-stat factor"
        )
        assertTrue(JSONObject(buildCalcRequestJson(boostedReady.request)).getJSONObject("attacker")
            .getBoolean("hnsFlashFireBoosted"))

        val unread = clear.copy(state = clear.state.copy(groupDVolatilesObserved = false))
        val fireMove = refusedOf(
            build(trustFor(exactSha), goldenARequest("Ember"), unread, enemyObservation(), randomAbilities = true),
            "a missing Flash Fire activation flag must remain unknown"
        )
        assertTrue(fireMove.verdict.blockingLimitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
        readyOf(
            build(
                trustFor(exactSha), goldenARequest("Water Gun"), clear, enemyObservation(),
                randomAbilities = true
            ),
            "attacker Flash Fire cannot change Water damage"
        )
    }

    @Test
    fun `move mechanics sent to the engine come from the pinned move identity`() {
        val spoofed = goldenARequest("Thunderbolt").copy(
            moveOverride = CalcMoveOverride(basePower = 40, type = "Water", category = "Special")
        )
        val ready = readyOf(
            build(trustFor(exactSha), spoofed, playerObservation(), enemyObservation()),
            "the boundary rebuilds move metadata from the selected pinned move"
        )
        val move = JSONObject(buildCalcRequestJson(ready.request)).getJSONObject("move")
        assertEquals(85, move.getInt("hnsMoveId"))
        assertFalse(move.getJSONArray("hnsMoveFlags").toString().contains("soundMove"))
        assertFalse(move.has("hnsUnknownMoveFlags"))
    }

    @Test
    fun `move level ability bypass respects pinned defender breakability`() {
        val trust = trustFor(exactSha)

        val prism = readyOf(
            build(trust, goldenARequest("Sunsteel Strike"), playerObservation(),
                enemyObservation(speciesId = 185, types = listOf(6), abilityId = 232, abilityName = "Prism Armor"),
                randomAbilities = true),
            "Sunsteel Strike must retain unbreakable Prism Armor"
        )
        val prismJson = JSONObject(buildCalcRequestJson(prism.request))
        assertEquals("Prism Armor", prismJson.getJSONObject("defender").getString("ability"))
        assertTrue(prismJson.getJSONObject("move").getJSONArray("hnsMoveFlags")
            .toString().contains("ignoresTargetAbility"))

        val shadow = readyOf(
            build(trust, goldenARequest("Moongeist Beam"), playerObservation(),
                enemyObservation(speciesId = 65, types = listOf(15), abilityId = 231, abilityName = "Shadow Shield",
                    hp = 60000, maxHp = 60000), randomAbilities = true),
            "Moongeist Beam must retain full-HP unbreakable Shadow Shield"
        )
        val shadowJson = JSONObject(buildCalcRequestJson(shadow.request))
        assertEquals("Shadow Shield", shadowJson.getJSONObject("defender").getString("ability"))
        assertEquals(60000, shadowJson.getJSONObject("defender").getInt("hpAtHit"))
        assertEquals(60000, shadowJson.getJSONObject("defender").getInt("maxHpAtHit"))
        assertTrue(shadowJson.getJSONObject("move").getJSONArray("hnsMoveFlags")
            .toString().contains("ignoresTargetAbility"))

        val filter = readyOf(
            build(trust, goldenARequest("Sunsteel Strike"), playerObservation(),
                enemyObservation(speciesId = 185, types = listOf(6), abilityId = 111, abilityName = "Filter"),
                randomAbilities = true),
            "Sunsteel Strike's move-level bypass must suppress breakable Filter"
        )
        val filterJson = JSONObject(buildCalcRequestJson(filter.request))
        assertEquals("Filter", filterJson.getJSONObject("defender").getString("ability"))
        assertTrue(filterJson.getJSONObject("move").getJSONArray("hnsMoveFlags")
            .toString().contains("ignoresTargetAbility"))
    }

    @Test
    fun `Ability Shield blocks the pinned move ability bypass and caller overrides cannot spoof it`() {
        val trust = trustFor(exactSha)
        val wonderGuard = enemyObservation(abilityId = 25, abilityName = "Wonder Guard", itemId = 758)
        val spoofedSunsteel = goldenARequest("Sunsteel Strike").copy(
            moveOverride = CalcMoveOverride(basePower = 1, type = "Normal", category = "Special")
        )
        val sunsteel = readyOf(
            build(trust, spoofedSunsteel, playerObservation(), wonderGuard, randomAbilities = true),
            "the pinned Sunsteel Strike flag reaches the engine with its protecting Ability Shield"
        )
        val sunsteelMove = JSONObject(buildCalcRequestJson(sunsteel.request)).getJSONObject("move")
        assertEquals(667, sunsteelMove.getInt("hnsMoveId"))
        assertTrue(sunsteelMove.getJSONArray("hnsMoveFlags").toString().contains("ignoresTargetAbility"))

        val spoofedTackle = goldenARequest("Tackle").copy(
            moveOverride = CalcMoveOverride(basePower = 100, type = "Steel", category = "Special")
        )
        val tackle = readyOf(
            build(trust, spoofedTackle, playerObservation(), wonderGuard, randomAbilities = true),
            "caller move metadata cannot add the pinned bypass flag to Tackle"
        )
        val tackleMove = JSONObject(buildCalcRequestJson(tackle.request)).getJSONObject("move")
        assertEquals(33, tackleMove.getInt("hnsMoveId"))
        assertFalse(tackleMove.getJSONArray("hnsMoveFlags").toString().contains("ignoresTargetAbility"))
    }

    @Test
    fun `name and numeric ability item identities must agree before neutralization`() {
        val mismatched = goldenARequest().copy(
            attacker = goldenARequest().attacker.copy(
                origin = CalcInputOrigin.MANUAL,
                ability = "Guts",
                abilityId = 105,
                item = "Scope Lens",
                itemId = 425,
                itemProvenance = CalcItemProvenance.MANUAL
            ),
            defender = goldenARequest().defender.copy(origin = CalcInputOrigin.MANUAL)
        )
        val outcome = build(trustFor(exactSha), mismatched, playerObservation(), enemyObservation())
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError("contradictory item identity must fail closed, got $outcome")
        assertNull(refused.verdict.request)
        assertTrue(refused.verdict.blockingLimitations.contains(CalcLimitation.HNS_ABILITY_IDENTITY_NOT_AUTHORITATIVE))
        assertTrue(refused.verdict.blockingLimitations.contains(CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE))
    }
    @Test
    fun `state backed operands bind live values and retain independent blockers`() {
        val trust = trustFor(exactSha)
        val gorilla = playerObservation(abilityId = 255, abilityName = "Gorilla Tactics")
        readyOf(build(trust, goldenARequest(), gorilla, enemyObservation(), randomAbilities = true),
            "observed ordinary Gorilla Tactics is exact")
        for (selected in listOf(4, 5)) {
            val pending = gorilla.copy(state = gorilla.state.copy(selectedGimmickObserved = true, selectedGimmick = selected))
            val pendingVerdict = (build(trust, goldenARequest(), pending, enemyObservation(), randomAbilities = true)
                as CalcRequestOutcome.Refused).verdict
            assertTrue("selected gimmick $selected", pendingVerdict.limitations.contains(CalcLimitation.HNS_GIMMICK_ACTIVE_NOT_MODELLED))
            assertFalse(pendingVerdict.limitations.contains(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED))
        }
        val clear = readyOf(build(trust, goldenARequest(), gorilla.copy(state = gorilla.state.copy(
            selectedGimmickObserved = true, selectedGimmick = 0, activeGimmick = 0)),
            enemyObservation(), randomAbilities = true),
            "observed selected NONE and active NONE remain clear")
        assertTrue(clear.request.hnsLiveBattleState?.attackerSelectedGimmick == 0)
        val activeTeraVerdict = (build(trust, goldenARequest(), gorilla.copy(state = gorilla.state.copy(
            selectedGimmickObserved = true, selectedGimmick = 0, activeGimmick = 5)), enemyObservation(), randomAbilities = true)
            as CalcRequestOutcome.Refused).verdict
        assertTrue(activeTeraVerdict.limitations.contains(CalcLimitation.HNS_GIMMICK_ACTIVE_NOT_MODELLED))
        val defenderPendingTeraVerdict = (build(trust, goldenARequest(), gorilla,
            enemyObservation(selectedGimmick = 5), randomAbilities = true) as CalcRequestOutcome.Refused).verdict
        assertTrue(defenderPendingTeraVerdict.limitations.contains(CalcLimitation.HNS_GIMMICK_ACTIVE_NOT_MODELLED))

        val paradoxBase = playerObservation(abilityId = 282, abilityName = "Quark Drive")
        val paradox = paradoxBase.copy(state = paradoxBase.state.copy(
            volatileBoosterEnergyActivated = true, volatileParadoxBoostedStat = 1))
        val consumed = readyOf(build(trust, goldenARequest(), paradox, enemyObservation(), randomAbilities = true),
            "consumed ITEM_NONE plus the observed activation payload is sufficient")
        val json = JSONObject(buildCalcRequestJson(consumed.request)).getJSONObject("attacker")
        assertTrue(json.getBoolean("hnsBoosterEnergyActivated"))
        assertEquals(0, json.getInt("hnsEffectiveItemId"))
        val held = paradox.copy(state = paradox.state.copy(itemId = 764))
        val heldVerdict = (build(trust, goldenARequest(), held, enemyObservation(), randomAbilities = true)
            as CalcRequestOutcome.Refused).verdict
        assertTrue(heldVerdict.limitations.contains(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED))
        val gas = paradox.copy(state = paradox.state.copy(volatileNeutralizingGas = true))
        assertTrue((build(trust, goldenARequest(), gas, enemyObservation(), randomAbilities = true)
            as CalcRequestOutcome.Refused).verdict.limitations.contains(CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED))
    }

    @Test
    fun `Neutralizing Gas remains authoritative in the Group D payload without item extensions`() {
        val trust = trustFor(exactSha)
        val request = goldenARequest().copy(
            attacker = goldenARequest().attacker.copy(
                ability = "Klutz", abilityId = 103, item = "Choice Band", itemId = 442
            )
        )
        val attacker = playerObservation(abilityId = 103, abilityName = "Klutz", itemId = 442,
            groupDVolatilesObserved = true, itemVolatilesObserved = false)
        val defender = enemyObservation(abilityId = 256, abilityName = "Neutralizing Gas",
            groupDVolatilesObserved = true, itemVolatilesObserved = false, volatileNeutralizingGas = true)
        assertTrue(attacker.state.groupDVolatilesObserved)
        assertFalse(attacker.state.itemVolatilesObserved)
        assertTrue(defender.state.groupDVolatilesObserved)
        assertFalse(defender.state.itemVolatilesObserved)
        assertTrue(defender.state.volatileNeutralizingGas)

        assertEquals(true, CalcRequestBoundary.observedNeutralizingGasOnField(attacker.state, defender.state))
        val refused = refusedOf(
            build(trust, request, attacker, defender, randomAbilities = true),
            "unavailable Embargo state independently keeps the held-item resolution conservative"
        )
        assertTrue(refused.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED))
    }

    @Test
    fun `Analytic authority belongs to this exact current move and cannot be supplied by the caller`() {
        val trust = trustFor(exactSha)
        val base = playerObservation(abilityId = 148, abilityName = "Analytic")
        val active = base.copy(state = base.state.copy(analyticTurnOrderObserved = true,
            analyticTurnOrder = 1, analyticCurrentMove = 33))
        val ready = readyOf(build(trust, goldenARequest(), active, enemyObservation(), randomAbilities = true),
            "phase-proven Tackle action may consume LAST_TO_MOVE")
        assertEquals("LAST_TO_MOVE", JSONObject(buildCalcRequestJson(ready.request))
            .getJSONObject("attacker").getString("hnsAnalyticTurnOrder"))
        val otherMove = active.copy(state = active.state.copy(analyticCurrentMove = 52))
        for (observation in listOf(base, otherMove)) {
            val forged = goldenARequest().copy(hnsLiveBattleState = ready.request.hnsLiveBattleState)
            val result = build(trust, forged, observation, enemyObservation(), randomAbilities = true)
                as CalcRequestOutcome.Refused
            assertTrue(result.verdict.limitations.contains(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED))
        }
    }

    @Test
    fun `Group E assignments run through production and retain independent blockers`() {
        for ((id, disposition) in com.dualdex.pokemon.hns.HnsGroupEData.abilityDispositions) {
            val name = com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(id).titleCaseName
            for (attacking in listOf(true, false)) {
                val outcome = build(trustFor(exactSha), goldenARequest(),
                    if (attacking) playerObservation(abilityId = id, abilityName = name) else playerObservation(),
                    if (attacking) enemyObservation() else enemyObservation(abilityId = id, abilityName = name),
                    randomAbilities = true)
                val verdict = when (outcome) {
                    is CalcRequestOutcome.Ready -> outcome.verdict
                    is CalcRequestOutcome.Refused -> outcome.verdict
                }
                for (decision in verdict.hnsAbilityDecisions.filter { it.abilityId == id }) {
                    if (decision.relevance == HnsAbilityRequestRelevance.UNKNOWN) {
                        assertFalse("$id unknown must refuse", verdict.isCalculable)
                        assertTrue(com.dualdex.battle.DamageBlockerPresentation.Ability(decision)
                            .headline.contains(disposition.reason))
                    } else if (decision.relevance == HnsAbilityRequestRelevance.RELEVANT &&
                        disposition.tier == com.dualdex.pokemon.hns.HnsGroupETier.CAVEATED_ESTIMATE) {
                        assertTrue("$id relevant estimate must be labelled", verdict.ignoredMechanics.any {
                            it is IgnoredCalcMechanic.Ability && it.decision.abilityId == id &&
                                it.presentationLine.contains(name)
                        })
                    }
                }
            }
        }
        val independent = refusedOf(build(trustFor(exactSha), goldenARequest("Thunder Shock"),
            playerObservation(abilityId = 277, abilityName = "Wind Power", chargeTimer = 1),
            enemyObservation(abilityId = 209, abilityName = "Disguise"), randomAbilities = true),
            "exact Charge cannot clear Disguise")
        assertTrue(independent.verdict.hnsAbilityDecisions.any {
            it.abilityId == 209 && it.relevance == HnsAbilityRequestRelevance.UNKNOWN
        })
        for (timer in listOf(-1, 4)) {
            refusedOf(build(trustFor(exactSha), goldenARequest("Thunder Shock"),
                playerObservation(chargeTimer = timer), enemyObservation()), "invalid Charge timer")
        }
        refusedOf(build(trustFor(exactSha), goldenARequest("Thunder Shock"),
            playerObservation(abilityId = 280, abilityName = "Electromorphosis", transientVolatilesObserved = false),
            enemyObservation()), "unread volatile cannot stand in for inactive Charge")
    }

    @Test
    fun `shared Pikachu name needs a slot matched numeric identity and cannot use species defaults`() {
        val request = goldenARequest().copy(defender = goldenARequest().defender.copy(species = "Pikachu"))
        val ready = readyOf(build(trustFor(exactSha), request, playerObservation(),
            enemyObservation(speciesId = 25)), "numeric Pikachu identity disambiguates the display name")
        assertNotNull(ready.request.defenderOverride)
        assertEquals(25, ready.request.hnsLiveBattleState?.defenderSpeciesId)
        val species = com.dualdex.pokemon.hns.HeartAndSoul205DataPack.getSpecies(25)!!
        assertEquals(species.baseHP, ready.request.defenderOverride?.baseStats?.hp)
        for (id in listOf(null, 16, 65535)) {
            val refused = refusedOf(build(trustFor(exactSha), request.copy(defenderOverride = ready.request.defenderOverride),
                playerObservation(), enemyObservation(speciesId = id)), "stale/missing ID cannot resolve Pikachu")
            assertTrue(refused.verdict.limitations.contains(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA))
        }
    }

    @Test
    fun `every Group E item uses production tiering and retains identity in its message`() {
        for ((id, disposition) in com.dualdex.pokemon.hns.HnsGroupEData.itemDispositions) {
            for (attacking in listOf(true, false)) {
                val outcome = build(trustFor(exactSha), goldenARequest(),
                    playerObservation(itemId = if (attacking) id else 0),
                    enemyObservation(itemId = if (attacking) 0 else id))
                val verdict = when (outcome) {
                    is CalcRequestOutcome.Ready -> outcome.verdict
                    is CalcRequestOutcome.Refused -> outcome.verdict
                }
                val decision = verdict.hnsItemDecisions.firstOrNull { it.itemId == id } ?: error("Missing item $id")
                if (decision.relevance == HnsItemRequestRelevance.UNKNOWN) {
                    assertFalse("$id unknown cannot estimate", verdict.isCalculable)
                    val text = com.dualdex.battle.DamageBlockerPresentation.Item(decision).headline
                    assertTrue(text, text.contains(disposition.reason) && text.contains(decision.itemName))
                } else if (decision.relevance == HnsItemRequestRelevance.RELEVANT) {
                    assertEquals(com.dualdex.pokemon.hns.HnsGroupETier.CAVEATED_ESTIMATE, disposition.tier)
                    assertTrue("item $id needs a named caveat", verdict.ignoredMechanics.any {
                        it is IgnoredCalcMechanic.Item && it.decision.itemId == id &&
                            it.presentationLine.contains(decision.itemName)
                    })
                }
            }
        }
    }

    @Test
    fun `Sturdy survival is a named base estimate and never clears move or unknown HP gates`() {
        val ready = readyOf(build(trustFor(exactSha), goldenARequest(), playerObservation(),
            enemyObservation(abilityId = 5, abilityName = "Sturdy", hp = 15, maxHp = 15)), "Sturdy base range")
        assertTrue(ready.verdict.isCaveatedEstimate)
        assertEquals(listOf("Foe: Sturdy"), ready.verdict.ignoredMechanics.map { it.presentationLine })
        assertEquals("(other)", ready.request.defender.ability)
        assertNull(ready.request.defender.abilityId)
        val below = readyOf(build(trustFor(exactSha), goldenARequest(), playerObservation(),
            enemyObservation(abilityId = 5, abilityName = "Sturdy", hp = 14, maxHp = 15)), "below full HP")
        assertFalse(below.verdict.isCaveatedEstimate)
        for (move in listOf("Fissure", "Seismic Toss")) {
            refusedOf(build(trustFor(exactSha), goldenARequest(move), playerObservation(),
                enemyObservation(abilityId = 5, abilityName = "Sturdy")), "move semantics stay independent")
        }
        refusedOf(build(trustFor(exactSha), goldenARequest(), playerObservation(),
            enemyObservation(abilityId = 5, abilityName = "Sturdy", hpObserved = false)), "unknown HP")
    }

    @Test
    fun `Mega Z and e-Reader identities cannot inherit a suppressed NONE hold effect`() {
        val ids = listOf("HOLD_EFFECT_MEGA_STONE", "HOLD_EFFECT_Z_CRYSTAL").map { effect ->
            (1..900).first { com.dualdex.pokemon.hns.HnsItemRegistry.classify(it).data?.holdEffect == effect }
        }
        for (id in ids) {
            val clear = readyOf(build(trustFor(exactSha), goldenARequest(),
                playerObservation(itemId = id, embargo = true), enemyObservation()), "both gimmicks NONE")
            assertEquals("mega_z_no_selected_or_active_gimmick", clear.verdict.hnsItemDecisions.single().rule)
            for (state in listOf(
                playerObservation(itemId = id, embargo = true, selectedGimmick = 1),
                playerObservation(itemId = id, embargo = true, gimmick = 1),
                playerObservation(itemId = id, embargo = true, gimmickObserved = false))) {
                val refused = refusedOf(build(trustFor(exactSha), goldenARequest(), state, enemyObservation()),
                    "suppression cannot authorize unknown/active gimmicks")
                assertEquals(HnsItemRequestRelevance.UNKNOWN, refused.verdict.hnsItemDecisions.single().relevance)
            }
        }
        val enigma = refusedOf(build(trustFor(exactSha), goldenARequest(),
            playerObservation(itemId = 581, embargo = true), enemyObservation()), "runtime Enigma payload unread")
        assertEquals(HnsItemRequestRelevance.UNKNOWN, enigma.verdict.hnsItemDecisions.single().relevance)
        assertTrue(enigma.verdict.hnsItemDecisions.single().rationale.contains("runtime hold effect"))
    }

}
