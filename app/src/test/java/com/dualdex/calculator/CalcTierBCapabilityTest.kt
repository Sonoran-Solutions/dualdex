package com.dualdex.calculator

import com.dualdex.battle.BattleDamageCalculator
import com.dualdex.battle.BattlePresence
import com.dualdex.battle.StatStages
import com.dualdex.battle.SwitchAdvisorInput
import com.dualdex.battle.SwitchAdvisorPresenter
import com.dualdex.battle.SwitchConfidenceTier
import com.dualdex.battle.SwitchRecommendationSource
import com.dualdex.companion.ui.CalcTabScreenView
import com.dualdex.pokemon.ModernDataPack
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import com.dualdex.romhack.SpeciesOverride
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class CalcTierBCapabilityTest {

    private val expansionSha = "a".repeat(64)
    private val cfruSha = "b".repeat(64)

    private val verifiedExpansionProfile = RomHackProfile(
        id = "emerald_imperium_synthetic",
        name = "Pokemon Emerald Imperium",
        baseGame = "Emerald",
        gameId = 8,
        engine = "pokeemerald-expansion",
        hasPhysSpecSplit = true,
        steelResistsGhostDark = false,
        sha256Hashes = listOf(expansionSha),
        isVerified = true,
        memoryLayoutVerified = true,
        gameDataPackId = "modern"
    )

    private val verifiedCfruProfile = RomHackProfile(
        id = "radical_red_verified_synthetic",
        name = "Pokemon Radical Red Verified",
        baseGame = "FireRed",
        gameId = 7,
        engine = "CFRU",
        hasPhysSpecSplit = true,
        steelResistsGhostDark = false,
        cfruOffsets = true,
        sha256Hashes = listOf(cfruSha),
        isVerified = true,
        memoryLayoutVerified = true,
        gameDataPackId = "modern"
    )

    private fun bundledProfile(id: String): RomHackProfile {
        val file = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles/$id.json") }
            .first { it.isFile }
        return ProfileLoader.parseProfile(file.readText())
    }

    private fun exactTrust(profile: RomHackProfile): RuntimeRomTrust {
        val hash = profile.sha256Hashes.first()
        return RuntimeRomTrust.from(RomCompatibility.verified(profile, hash), hash)
    }

    private val baselineRequest = DamageCalculationRequest(
        attacker = CalcPokemonInput("Sylveon"),
        defender = CalcPokemonInput("Dragonite"),
        move = CalcMoveInput("Moonblast")
    )

    private fun battlerObservation(
        status: HnsBattlerRuntimeStatus = HnsBattlerRuntimeStatus.OBSERVED,
        battlersCount: Int = 2,
        battlersCountReadable: Boolean = true
    ): BattlerRuntimeObservation = BattlerRuntimeObservation(
        state = HnsBattlerRuntimeState(
            status = status,
            battlersCount = battlersCount,
            battlersCountReadable = battlersCountReadable
        )
    )

    private fun assertRefused(
        profile: RomHackProfile,
        input: DamageCalculationRequest,
        trust: RuntimeRomTrust? = if (profile.sha256Hashes.isNotEmpty()) exactTrust(profile) else null,
        expectedReason: CalcLimitation? = null,
        playerBattlerState: BattlerRuntimeObservation? = null,
        enemyBattlerState: BattlerRuntimeObservation? = null,
        activeBattle: Boolean = false,
        battlePresence: BattlePresence? = null
    ) {
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            request = input,
            playerBattlerState = playerBattlerState,
            enemyBattlerState = enemyBattlerState,
            activeBattle = activeBattle,
            battlePresence = battlePresence
        )
        assertTrue("Expected refusal for $profile / $input, got $outcome", outcome is CalcRequestOutcome.Refused)
        val verdict = (outcome as CalcRequestOutcome.Refused).verdict
        assertEquals(CalcSupport.UNSUPPORTED, verdict.support)
        assertNull(verdict.request)
        assertFalse(verdict.mayRunEngine)
        if (expectedReason != null) {
            assertTrue(
                "Expected $expectedReason in ${verdict.blockingLimitations}",
                expectedReason in verdict.blockingLimitations
            )
        }
        assertFalse(
            CalcAuthorizedExecution.calculate(verdict) { error("refused Tier-B request reached engine") }.success
        )
    }

    @Test
    fun `verified synthetic pokeemerald-expansion profile resolves to EXPANSION_TIER_B with ESTIMATED ceiling`() {
        val outcome = CalcRequestBoundary.build(
            verifiedExpansionProfile,
            exactTrust(verifiedExpansionProfile),
            baselineRequest
        )
        assertTrue("Expected Ready, got $outcome", outcome is CalcRequestOutcome.Ready)
        val ready = outcome as CalcRequestOutcome.Ready
        assertEquals(CalcRuleset.EXPANSION_TIER_B, ready.verdict.ruleset)
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.capability.ceiling)
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertEquals(listOf(CalcLimitation.TIER_B_ESTIMATED_ENGINE), ready.verdict.limitations)
        assertTrue(ready.verdict.mayRunEngine)
        assertNotNull(ready.request.expansionTierBOperands)
        assertNull(ready.request.unboundOperands)

        val json = JSONObject(buildCalcRequestJson(ready.request))
        assertEquals(TierBCalcInputs.EXPANSION_TYPE_SYSTEM, json.getString("typeSystem"))
        assertEquals("Singles", json.getJSONObject("field").getString("gameType"))
        val exp = json.getJSONObject("expansion")
        assertEquals(50, exp.getInt("level"))
        assertEquals(95, exp.getInt("power"))
        assertEquals("Special", exp.getString("category"))
        assertEquals("Fairy", exp.getString("moveType"))
        assertEquals("None", exp.getString("weather"))
        assertFalse(exp.getBoolean("isCrit"))
        assertEquals("Sylveon", json.getJSONObject("display").getString("attackerName"))
        assertEquals("Dragonite", json.getJSONObject("display").getString("defenderName"))
        assertEquals("Moonblast", json.getJSONObject("display").getString("moveName"))

        val presentation = CalcResultPresentation.forVerdict(ready.verdict, ready.request)
        assertFalse(presentation.isVerified)
        assertTrue(presentation.headline.contains("Tier-B"))
    }

    @Test
    fun `verified synthetic CFRU profile resolves to CFRU_TIER_B and dispatches to unbound_cfru`() {
        val outcome = CalcRequestBoundary.build(
            verifiedCfruProfile,
            exactTrust(verifiedCfruProfile),
            baselineRequest
        )
        assertTrue("Expected Ready, got $outcome", outcome is CalcRequestOutcome.Ready)
        val ready = outcome as CalcRequestOutcome.Ready
        assertEquals(CalcRuleset.CFRU_TIER_B, ready.verdict.ruleset)
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.capability.ceiling)
        assertEquals(CalcSupport.ESTIMATED, ready.verdict.support)
        assertEquals(listOf(CalcLimitation.TIER_B_ESTIMATED_ENGINE), ready.verdict.limitations)
        assertTrue(ready.verdict.mayRunEngine)
        assertNotNull(ready.request.unboundOperands)
        assertNull(ready.request.expansionTierBOperands)

        val json = JSONObject(buildCalcRequestJson(ready.request))
        assertEquals(UnboundCalcInputs.TYPE_SYSTEM, json.getString("typeSystem"))
        assertEquals("Singles", json.getJSONObject("field").getString("gameType"))
        val cfru = json.getJSONObject("unbound")
        assertEquals(50, cfru.getInt("level"))
        assertEquals(95, cfru.getInt("power"))
        assertEquals(1, cfru.getInt("category"))
        assertEquals(23, cfru.getInt("move_type")) // Fairy
        assertEquals(23, cfru.getInt("attacker_type_1"))
        assertEquals(23, cfru.getInt("attacker_type_2"))
        assertEquals(16, cfru.getInt("defender_type_1")) // Dragon
        assertEquals(2, cfru.getInt("defender_type_2")) // Flying
        assertEquals(0, cfru.getInt("attacker_ability"))
        assertEquals(0, cfru.getInt("defender_ability"))
    }

    @Test
    fun `expansion tier-B maps stages weather screens burn crit and strips forged overrides`() {
        val attackerRaw = CalcUnboundParticipant(
            speciesId = 59,
            stats = CalcRawStats(140, 100, 115, 120, 100),
            maxHp = 165,
            types = listOf(PokemonType.FIRE)
        )
        val defenderRaw = CalcUnboundParticipant(
            speciesId = 143,
            stats = CalcRawStats(130, 100, 50, 85, 130),
            maxHp = 235,
            types = listOf(PokemonType.NORMAL)
        )
        val ready = CalcRequestBoundary.build(
            verifiedExpansionProfile,
            exactTrust(verifiedExpansionProfile),
            DamageCalculationRequest(
                attacker = CalcPokemonInput(
                    species = "Arcanine",
                    level = 55,
                    status = "brn",
                    boosts = StatBlock(atk = -2, spa = 1),
                    unbound = attackerRaw
                ),
                defender = CalcPokemonInput(
                    species = "Snorlax",
                    level = 55,
                    boosts = StatBlock(def = 2, spd = -1),
                    unbound = defenderRaw
                ),
                move = CalcMoveInput("Fire Punch", isCrit = true),
                field = CalcFieldInput(weather = "Sun", defenderSide = SideConditions(isReflect = true, isLightScreen = true)),
                moveOverride = CalcMoveOverride(basePower = 1, type = "Water"),
                typeSystem = "hns_2_0_5"
            )
        ) as CalcRequestOutcome.Ready

        assertNull(ready.request.moveOverride)
        val json = JSONObject(buildCalcRequestJson(ready.request))
        val exp = json.getJSONObject("expansion")
        assertEquals(55, exp.getInt("level"))
        assertEquals(75, exp.getInt("power"))
        assertEquals("Physical", exp.getString("category"))
        assertEquals("Fire", exp.getString("moveType"))
        assertEquals(140, exp.getInt("attack"))
        assertEquals(100, exp.getInt("defense"))
        assertEquals(-2, exp.getInt("attackStage"))
        assertEquals(2, exp.getInt("defenseStage"))
        assertTrue(exp.getBoolean("attackerBurned"))
        assertEquals("Sun", exp.getString("weather"))
        assertTrue(exp.getBoolean("isReflect"))
        assertTrue(exp.getBoolean("isLightScreen"))
        assertTrue(exp.getBoolean("isCrit"))
    }

    @Test
    fun `unverified ROMs and profiles failing verification flags are strictly UNSUPPORTED`() {
        for (profile in listOf(verifiedExpansionProfile, verifiedCfruProfile)) {
            assertRefused(profile, baselineRequest, trust = null, expectedReason = CalcLimitation.TIER_B_ROM_NOT_EXACT_VERIFIED)
            assertRefused(
                profile,
                baselineRequest,
                trust = exactTrust(profile).copy(matchMethod = ProfileMatchMethod.DEFAULT_FALLBACK),
                expectedReason = CalcLimitation.TIER_B_ROM_NOT_EXACT_VERIFIED
            )
            assertRefused(
                profile,
                baselineRequest,
                trust = exactTrust(profile).copy(activeRomSha256 = "f".repeat(64)),
                expectedReason = CalcLimitation.TIER_B_ROM_NOT_EXACT_VERIFIED
            )

            assertNull(CalcCapabilityPolicy.capabilityFor(profile.copy(isVerified = false)))
            assertNull(CalcCapabilityPolicy.capabilityFor(profile.copy(memoryLayoutVerified = false)))
            assertNull(CalcCapabilityPolicy.capabilityFor(profile.copy(sha256Hashes = emptyList())))
            assertNull(CalcCapabilityPolicy.capabilityFor(profile.copy(hasPhysSpecSplit = false)))
            assertRefused(profile.copy(isVerified = false), baselineRequest)
            assertRefused(profile.copy(memoryLayoutVerified = false), baselineRequest)
        }
    }

    @Test
    fun `custom species profiles are strictly UNSUPPORTED for Tier-B`() {
        val customMap = mapOf(500 to SpeciesOverride("CustomMon", "Fire", "Fairy"))
        for (profile in listOf(verifiedExpansionProfile, verifiedCfruProfile)) {
            val withCustom = profile.copy(customSpecies = customMap)
            assertNull(CalcCapabilityPolicy.capabilityFor(withCustom))
            assertRefused(withCustom, baselineRequest)
        }
    }

    @Test
    fun `Doubles Status zero-power missing species or move and unmodeled inputs fail closed`() {
        for (profile in listOf(verifiedExpansionProfile, verifiedCfruProfile)) {
            assertRefused(
                profile,
                baselineRequest.copy(field = CalcFieldInput(gameType = CalcGameTypes.DOUBLES)),
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(field = CalcFieldInput(weather = "Sand")),
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(field = CalcFieldInput(weather = "rain")),
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(field = CalcFieldInput(terrain = "Electric")),
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(move = CalcMoveInput("Swords Dance")),
                expectedReason = CalcLimitation.TIER_B_MOVE_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(move = CalcMoveInput("Guillotine")),
                expectedReason = CalcLimitation.TIER_B_MOVE_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(move = CalcMoveInput("NotARealMove")),
                expectedReason = CalcLimitation.MOVE_NOT_IN_PINNED_DATA
            )
            assertRefused(
                profile,
                baselineRequest.copy(attacker = CalcPokemonInput("NotARealSpecies")),
                expectedReason = CalcLimitation.SPECIES_NOT_IN_PINNED_DATA
            )
            assertRefused(
                profile,
                baselineRequest.copy(defender = CalcPokemonInput("NotARealSpecies")),
                expectedReason = CalcLimitation.SPECIES_NOT_IN_PINNED_DATA
            )
            assertRefused(
                profile,
                baselineRequest.copy(attacker = baselineRequest.attacker.copy(ability = "Huge Power")),
                expectedReason = CalcLimitation.TIER_B_MODIFIER_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(defender = baselineRequest.defender.copy(item = "Choice Band")),
                expectedReason = CalcLimitation.TIER_B_MODIFIER_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(attacker = baselineRequest.attacker.copy(status = "par")),
                expectedReason = CalcLimitation.STATUS_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(attacker = baselineRequest.attacker.copy(status = "BRN")),
                expectedReason = CalcLimitation.STATUS_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest.copy(attacker = baselineRequest.attacker.copy(level = 0)),
                expectedReason = CalcLimitation.LEVEL_OUT_OF_RANGE
            )
            assertRefused(
                profile,
                baselineRequest.copy(attacker = baselineRequest.attacker.copy(boosts = StatBlock(atk = 7))),
                expectedReason = CalcLimitation.BOOSTS_OUT_OF_RANGE
            )
            assertRefused(
                profile,
                baselineRequest.copy(
                    attacker = baselineRequest.attacker.copy(
                        unbound = CalcUnboundParticipant(
                            speciesId = 700,
                            stats = CalcRawStats(80, 80, 80, 120, 120),
                            maxHp = 160,
                            types = listOf(PokemonType.STELLAR)
                        )
                    )
                ),
                expectedReason = CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED
            )
        }
    }

    @Test
    fun `active battles without confirmed Singles topology Doubles and invalid gameType fail closed`() {
        val singlesObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 2, battlersCountReadable = true)
        val doublesObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 4, battlersCountReadable = true)
        val unreadObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 2, battlersCountReadable = false)
        val invalidObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED_INVALID, battlersCount = 2, battlersCountReadable = true)
        val ambiguousObs = battlerObservation(HnsBattlerRuntimeStatus.AMBIGUOUS, battlersCount = 4, battlersCountReadable = true)

        for (profile in listOf(verifiedExpansionProfile, verifiedCfruProfile)) {
            // Invalid, blank, lowercase, or Doubles field.gameType must always fail closed.
            for (badGameType in listOf(CalcGameTypes.DOUBLES, "", "   ", "singles", "Triples")) {
                assertRefused(
                    profile,
                    baselineRequest.copy(field = CalcFieldInput(gameType = badGameType)),
                    expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
                )
            }
            // Active battle with no observations / unverified format fails closed.
            assertRefused(
                profile,
                baselineRequest,
                activeBattle = true,
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            // Single-sided observation, unreadable count, invalid status, ambiguous, torn read, or live Doubles fail closed.
            assertRefused(
                profile,
                baselineRequest,
                playerBattlerState = singlesObs,
                enemyBattlerState = null,
                activeBattle = true,
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest,
                playerBattlerState = unreadObs,
                enemyBattlerState = unreadObs,
                activeBattle = true,
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest,
                playerBattlerState = invalidObs,
                enemyBattlerState = singlesObs,
                activeBattle = true,
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest,
                playerBattlerState = ambiguousObs,
                enemyBattlerState = ambiguousObs,
                activeBattle = true,
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest,
                playerBattlerState = singlesObs,
                enemyBattlerState = doublesObs,
                activeBattle = true,
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            assertRefused(
                profile,
                baselineRequest,
                playerBattlerState = doublesObs,
                enemyBattlerState = doublesObs,
                activeBattle = true,
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            // Confirmed Singles topology with contradicting Doubles gameType still fails closed.
            assertRefused(
                profile,
                baselineRequest.copy(field = CalcFieldInput(gameType = CalcGameTypes.DOUBLES)),
                playerBattlerState = singlesObs,
                enemyBattlerState = singlesObs,
                activeBattle = true,
                expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
            )
            // Confirmed Singles topology on an exact-verified ROM succeeds as ESTIMATED.
            val outcome = CalcRequestBoundary.build(
                profile = profile,
                trust = exactTrust(profile),
                request = baselineRequest,
                playerBattlerState = singlesObs,
                enemyBattlerState = singlesObs,
                activeBattle = true
            )
            assertTrue("Expected Ready for confirmed Singles active battle on $profile, got $outcome", outcome is CalcRequestOutcome.Ready)
            assertEquals(CalcSupport.ESTIMATED, (outcome as CalcRequestOutcome.Ready).verdict.support)
        }
    }

    @Test
    fun `live Tier-B requests require confirmed out-of-battle or two agreeing Singles observations`() {
        val liveRequest = DamageCalculationRequest(
            attacker = CalcPokemonInput(
                species = "Sylveon",
                level = 50,
                boosts = StatBlock(),
                origin = CalcInputOrigin.LIVE_READ,
                unknownFields = listOf(CalcInputField.ABILITY),
                unbound = CalcUnboundParticipant(
                    speciesId = 700,
                    stats = CalcRawStats(90, 90, 80, 145, 150),
                    maxHp = 180,
                    types = listOf(PokemonType.FAIRY)
                )
            ),
            defender = CalcPokemonInput("Dragonite"),
            move = CalcMoveInput("Moonblast")
        )
        val singlesObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 2, battlersCountReadable = true)
        val doublesObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 4, battlersCountReadable = true)

        for (profile in listOf(verifiedExpansionProfile, verifiedCfruProfile)) {
            // Unknown or omitted battlePresence with activeBattle=false refuses live requests.
            for (presence in listOf(BattlePresence.UNKNOWN, null)) {
                assertRefused(
                    profile = profile,
                    input = liveRequest,
                    activeBattle = false,
                    battlePresence = presence,
                    expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
                )
            }

            // Smoothed activeBattle=false with missing player obs and enemy reporting 4 battlers refuses.
            for (presence in listOf(BattlePresence.UNKNOWN, BattlePresence.NOT_OBSERVED, BattlePresence.OBSERVED, null)) {
                assertRefused(
                    profile = profile,
                    input = liveRequest,
                    playerBattlerState = null,
                    enemyBattlerState = doublesObs,
                    activeBattle = false,
                    battlePresence = presence,
                    expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
                )
            }

            // Contradictory battler counts (player=2/enemy=4 or player=4/enemy=2) refuse even when activeBattle=false.
            for ((pObs, eObs) in listOf(singlesObs to doublesObs, doublesObs to singlesObs)) {
                for (active in listOf(false, true)) {
                    assertRefused(
                        profile = profile,
                        input = liveRequest,
                        playerBattlerState = pObs,
                        enemyBattlerState = eObs,
                        activeBattle = active,
                        battlePresence = if (active) BattlePresence.OBSERVED else BattlePresence.UNKNOWN,
                        expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
                    )
                }
            }

            // Single-sided Singles observation (player=2/enemy=null or player=null/enemy=2) refuses.
            for ((pObs, eObs) in listOf(singlesObs to null, null to singlesObs)) {
                for (active in listOf(false, true)) {
                    assertRefused(
                        profile = profile,
                        input = liveRequest,
                        playerBattlerState = pObs,
                        enemyBattlerState = eObs,
                        activeBattle = active,
                        battlePresence = if (active) BattlePresence.OBSERVED else BattlePresence.NOT_OBSERVED,
                        expectedReason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
                    )
                }
            }

            // Two agreeing Singles observations with BattlePresence.OBSERVED authorize as ESTIMATED.
            for (active in listOf(true, false)) {
                val liveBattleReady = CalcRequestBoundary.build(
                    profile = profile,
                    trust = exactTrust(profile),
                    request = liveRequest,
                    playerBattlerState = singlesObs,
                    enemyBattlerState = singlesObs,
                    activeBattle = active,
                    battlePresence = BattlePresence.OBSERVED
                )
                assertTrue(
                    "Expected Ready for agreed Singles observations on $profile (active=$active), got $liveBattleReady",
                    liveBattleReady is CalcRequestOutcome.Ready
                )
                assertEquals(CalcSupport.ESTIMATED, (liveBattleReady as CalcRequestOutcome.Ready).verdict.support)
            }

            // Confirmed out-of-battle (NOT_OBSERVED, activeBattle=false, no battler observations) authorizes as ESTIMATED.
            val overworldReady = CalcRequestBoundary.build(
                profile = profile,
                trust = exactTrust(profile),
                request = liveRequest,
                playerBattlerState = null,
                enemyBattlerState = null,
                activeBattle = false,
                battlePresence = BattlePresence.NOT_OBSERVED
            )
            assertTrue(
                "Expected Ready for confirmed out-of-battle live Tier-B request on $profile, got $overworldReady",
                overworldReady is CalcRequestOutcome.Ready
            )
            assertEquals(CalcSupport.ESTIMATED, (overworldReady as CalcRequestOutcome.Ready).verdict.support)

            // Pure MANUAL hypothetical with BattlePresence.UNKNOWN and no battle evidence remains authorized.
            val manualReady = CalcRequestBoundary.build(
                profile = profile,
                trust = exactTrust(profile),
                request = baselineRequest,
                activeBattle = false,
                battlePresence = BattlePresence.UNKNOWN
            )
            assertTrue(manualReady is CalcRequestOutcome.Ready)
            assertEquals(CalcSupport.ESTIMATED, (manualReady as CalcRequestOutcome.Ready).verdict.support)
        }
    }

    @Test
    fun `CalcTabScreenView gameType never defaults to Singles when observations disagree or enemy reports Doubles`() {
        val singlesObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 2, battlersCountReadable = true)
        val doublesObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 4, battlersCountReadable = true)

        assertEquals(CalcGameTypes.SINGLES, CalcTabScreenView.resolveFieldGameType(null, null))
        assertEquals(CalcGameTypes.SINGLES, CalcTabScreenView.resolveFieldGameType(singlesObs, singlesObs))
        assertEquals(CalcGameTypes.DOUBLES, CalcTabScreenView.resolveFieldGameType(doublesObs, doublesObs))
        assertEquals(CalcGameTypes.DOUBLES, CalcTabScreenView.resolveFieldGameType(null, doublesObs))
        assertEquals(CalcGameTypes.DOUBLES, CalcTabScreenView.resolveFieldGameType(doublesObs, null))
        assertEquals("", CalcTabScreenView.resolveFieldGameType(singlesObs, doublesObs))
        assertEquals("", CalcTabScreenView.resolveFieldGameType(doublesObs, singlesObs))
        assertEquals("", CalcTabScreenView.resolveFieldGameType(singlesObs, null))
        assertEquals("", CalcTabScreenView.resolveFieldGameType(null, singlesObs))
    }

    @Test
    fun `Surf is refused under both EXPANSION_TIER_B and CFRU_TIER_B because submerged doubling is unmodeled`() {
        val surf = ModernDataPack.getMoveByName("Surf")
        assertNotNull("Surf must exist in ModernDataPack", surf)
        assertEquals(57, surf!!.id)

        for (profile in listOf(verifiedExpansionProfile, verifiedCfruProfile)) {
            assertRefused(
                profile = profile,
                input = baselineRequest.copy(move = CalcMoveInput("Surf")),
                expectedReason = CalcLimitation.TIER_B_MOVE_NOT_MODELLED
            )
        }
    }

    @Test
    fun `multi-hit defense-swap and non-ordinary moves including Double Kick and Psyshock fail closed`() {
        val refusedMoves = listOf(
            "Double Kick",   // multi-hit
            "Psyshock",      // targets Defense instead of SpD
            "Psystrike",     // targets Defense instead of SpD
            "Secret Sword",  // targets Defense instead of SpD
            "Double Slap",   // multi-hit
            "Bonemerang",    // multi-hit
            "Eruption",      // HP-scaled power
            "Seismic Toss",  // fixed level damage
            "Double-Edge",   // recoil
            "Gyro Ball",     // speed-scaled power
            "Grass Knot",    // weight-scaled power
            "Brick Break",   // screen-breaking before damage
            "Foul Play",     // uses defender Attack
            "Stored Power"   // boost-scaled power
        )
        for (profile in listOf(verifiedExpansionProfile, verifiedCfruProfile)) {
            for (moveName in refusedMoves) {
                assertNotNull("Fixture move $moveName must exist in ModernDataPack", ModernDataPack.getMoveByName(moveName))
                assertRefused(
                    profile,
                    baselineRequest.copy(move = CalcMoveInput(moveName)),
                    expectedReason = CalcLimitation.TIER_B_MOVE_NOT_MODELLED
                )
            }
        }
    }

    @Test
    fun `Tier-B can never execute as VERIFIED or with malformed addon responses`() {
        val expReady = CalcRequestBoundary.build(
            verifiedExpansionProfile,
            exactTrust(verifiedExpansionProfile),
            baselineRequest
        ) as CalcRequestOutcome.Ready
        val expMaxHp = expReady.request.expansionTierBOperands!!.defenderMaxHp

        val validExpJson = """{"success":true,"typeSystem":"expansion_tierb",
            "attackerName":"Sylveon","defenderName":"Dragonite","moveName":"Moonblast","defenderMaxHP":$expMaxHp,
            "effectiveness":2.0,"minDamage":146,"maxDamage":174,
            "range":[146,174],"damage":[146,146,150,152,152,156,156,158,158,162,164,164,168,168,170,174]}"""
        assertTrue(parseCalcResponseJson(expReady.request, validExpJson).success)
        assertTrue(
            CalcAuthorizedExecution.calculate(expReady.verdict) { parseCalcResponseJson(it, validExpJson) }.success
        )

        // Immune Expansion Tier-B response: effectiveness == 0.0 paired with 16 zero rolls is valid.
        val validImmuneExpJson = """{"success":true,"typeSystem":"expansion_tierb",
            "attackerName":"Sylveon","defenderName":"Dragonite","moveName":"Moonblast","defenderMaxHP":$expMaxHp,
            "effectiveness":0,"minDamage":0,"maxDamage":0,
            "range":[0,0],"damage":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]}"""
        assertTrue(parseCalcResponseJson(expReady.request, validImmuneExpJson).success)

        // Type system mismatch and non-monotonic rolls rejected.
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace("expansion_tierb", "unbound_cfru")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace("146,146", "146,145")).success)

        // Metadata-free Expansion response rejected.
        val metadataFreeExpJson = """{"success":true,"typeSystem":"expansion_tierb","effectiveness":2.0,
            "minDamage":146,"maxDamage":174,
            "range":[146,174],"damage":[146,146,150,152,152,156,156,158,158,162,164,164,168,168,170,174]}"""
        assertFalse(parseCalcResponseJson(expReady.request, metadataFreeExpJson).success)

        // Blank or mismatched display metadata and mismatched defenderMaxHP rejected.
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""attackerName":"Sylveon"""", """"attackerName":"   """")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""attackerName":"Sylveon"""", """"attackerName":"Pikachu"""")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""defenderName":"Dragonite"""", """"defenderName":"Snorlax"""")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""moveName":"Moonblast"""", """"moveName":"Tackle"""")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""defenderMaxHP":$expMaxHp""", """"defenderMaxHP":${expMaxHp + 1}""")).success)

        // Effectiveness validation: missing, non-numeric, negative, 0 with nonzero rolls, and >0 with all-zero rolls rejected.
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""effectiveness":2.0,""", "")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""effectiveness":2.0""", """"effectiveness":null""")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""effectiveness":2.0""", """"effectiveness":"2.0"""")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""effectiveness":2.0""", """"effectiveness":-1.0""")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validExpJson.replace(""""effectiveness":2.0""", """"effectiveness":0""")).success)
        assertFalse(parseCalcResponseJson(expReady.request, validImmuneExpJson.replace(""""effectiveness":0""", """"effectiveness":1.0""")).success)

        // CFRU Tier-B strict display metadata validation: metadata-free and mismatched metadata rejected.
        val cfruReady = CalcRequestBoundary.build(
            verifiedCfruProfile,
            exactTrust(verifiedCfruProfile),
            baselineRequest
        ) as CalcRequestOutcome.Ready
        val cfruMaxHp = expectedAddonDisplayMetadata(cfruReady.request)!!.defenderMaxHp
        val validCfruJson = """{"success":true,"typeSystem":"unbound_cfru",
            "attackerName":"Sylveon","defenderName":"Dragonite","moveName":"Moonblast","defenderMaxHP":$cfruMaxHp,
            "minDamage":146,"maxDamage":174,
            "range":[146,174],"damage":[146,146,150,152,152,156,156,158,158,162,164,164,168,168,170,174]}"""
        assertTrue(parseCalcResponseJson(cfruReady.request, validCfruJson).success)
        assertTrue(
            CalcAuthorizedExecution.calculate(cfruReady.verdict) { parseCalcResponseJson(it, validCfruJson) }.success
        )
        val metadataFreeCfruJson = """{"success":true,"typeSystem":"unbound_cfru","minDamage":146,"maxDamage":174,
            "range":[146,174],"damage":[146,146,150,152,152,156,156,158,158,162,164,164,168,168,170,174]}"""
        assertFalse(parseCalcResponseJson(cfruReady.request, metadataFreeCfruJson).success)
        assertFalse(parseCalcResponseJson(cfruReady.request, validCfruJson.replace(""""defenderMaxHP":$cfruMaxHp""", """"defenderMaxHP":${cfruMaxHp + 1}""")).success)
        assertFalse(parseCalcResponseJson(cfruReady.request, validCfruJson.replace(""""moveName":"Moonblast"""", """"moveName":"Tackle"""")).success)

        // CalcAuthorizedExecution also rejects mismatched metadata even if a custom calculator bypasses parseCalcResponseJson.
        assertFalse(
            CalcAuthorizedExecution.calculate(expReady.verdict) {
                DamageCalculationResponse(
                    success = true,
                    minDamage = 146,
                    maxDamage = 174,
                    range = listOf(146, 146, 150, 152, 152, 156, 156, 158, 158, 162, 164, 164, 168, 168, 170, 174),
                    attackerName = "Sylveon",
                    defenderName = "Dragonite",
                    moveName = "Moonblast",
                    defenderMaxHP = expMaxHp,
                    effectiveness = 0.0
                )
            }.success
        )

        // Attempting to raise Tier-B to VERIFIED or strip TIER_B_ESTIMATED_ENGINE fails closed.
        assertFalse(
            CalcAuthorizedExecution.calculate(expReady.verdict.copy(support = CalcSupport.VERIFIED)) {
                error("verified Tier-B result must never reach engine")
            }.success
        )
        assertFalse(
            CalcAuthorizedExecution.calculate(expReady.verdict.copy(limitations = emptyList())) {
                error("Tier-B without TIER_B_ESTIMATED_ENGINE must never reach engine")
            }.success
        )
    }

    @Test
    fun `Unbound malformed JSON preserves exact Unbound error message`() {
        val unbound = bundledProfile("unbound")
        val unboundReady = CalcRequestBoundary.build(
            unbound,
            exactTrust(unbound),
            DamageCalculationRequest(
                attacker = CalcPokemonInput("Charmander"),
                defender = CalcPokemonInput("Bulbasaur"),
                move = CalcMoveInput("Flamethrower")
            )
        ) as CalcRequestOutcome.Ready
        val parsed = parseCalcResponseJson(unboundReady.request, "{not-valid-json")
        assertFalse(parsed.success)
        assertEquals("Invalid Unbound calculator result", parsed.error)
    }

    @Test
    fun `SwitchAdvisorPresenter gets ESTIMATED calc-driven rankings on confirmed Singles Tier-B hacks and fails closed otherwise`() {
        fun partyMon(species: Int, name: String, moveId: Int) = ParsedPokemon(
            isValid = true, isEmpty = false, pid = species.toLong() * 100L, tid = 1, sid = 1,
            nickname = name, otName = "Player", species = species, heldItem = 0, level = 50,
            nature = 0, natureName = "Hardy", isShiny = false, abilitySlot = 0, isEgg = false,
            friendship = 255, experience = 100000L,
            hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31, spAttackIv = 31, spDefenseIv = 31,
            hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
            moves = intArrayOf(moveId, 0, 0, 0), pp = intArrayOf(15, 0, 0, 0),
            currentHp = 160, maxHp = 160, attack = 100, defense = 100, speed = 100,
            spAttack = 140, spDefense = 110, statusCondition = 0L
        )

        val active = partyMon(25, "Pikachu", 85) // Thunderbolt
        val bench = partyMon(9, "Blastoise", 56) // Hydro Pump
        val opponent = partyMon(6, "Charizard", 53) // Flamethrower
        val singlesObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 2, battlersCountReadable = true)
        val doublesObs = battlerObservation(HnsBattlerRuntimeStatus.OBSERVED, battlersCount = 4, battlersCountReadable = true)

        for (profile in listOf(verifiedExpansionProfile, verifiedCfruProfile)) {
            val expectedTypeSystem = if (profile == verifiedExpansionProfile) {
                TierBCalcInputs.EXPANSION_TYPE_SYSTEM
            } else {
                UnboundCalcInputs.TYPE_SYSTEM
            }
            val calls = AtomicInteger(0)
            val stubCalc = BattleDamageCalculator { req ->
                calls.incrementAndGet()
                assertEquals(expectedTypeSystem, req.typeSystem)
                val expected = expectedAddonDisplayMetadata(req)!!
                val rolls = (0 until 16).map { 80 + it }
                DamageCalculationResponse(
                    success = true,
                    minDamage = rolls.first(),
                    maxDamage = rolls.last(),
                    range = rolls,
                    attackerName = expected.attackerName,
                    defenderName = expected.defenderName,
                    moveName = expected.moveName,
                    defenderMaxHP = expected.defenderMaxHp,
                    effectiveness = if (req.typeSystem == TierBCalcInputs.EXPANSION_TYPE_SYSTEM) 1.0 else null
                )
            }
            val presenter = SwitchAdvisorPresenter(calculator = stubCalc)

            // Confirmed Singles live battle uses the Tier-B ESTIMATED calculator.
            val ranked = presenter.evaluateSync(
                SwitchAdvisorInput(
                    playerParty = listOf(active, bench),
                    activePlayerSlot = 0,
                    opponent = opponent,
                    activeEnemySlot = 0,
                    profile = profile,
                    dataPack = ModernDataPack,
                    runtimeTrust = exactTrust(profile),
                    enemyStages = StatStages(),
                    playerBattlerState = singlesObs,
                    enemyBattlerState = singlesObs,
                    activeBattle = true
                )
            )
            assertTrue("Expected Tier-B SwitchAdvisor to invoke calculator", calls.get() > 0)
            assertEquals(1, ranked.size)
            assertEquals(SwitchRecommendationSource.CALCULATOR, ranked[0].source)
            assertEquals(SwitchConfidenceTier.ESTIMATED, ranked[0].confidenceTier)

            // Unverified active battle format (missing observations) and live Doubles fail closed (no recommendations).
            for ((playerObs, enemyObs) in listOf(null to null, doublesObs to doublesObs)) {
                calls.set(0)
                val fallback = presenter.evaluateSync(
                    SwitchAdvisorInput(
                        playerParty = listOf(active, bench),
                        activePlayerSlot = 0,
                        opponent = opponent,
                        activeEnemySlot = 0,
                        profile = profile,
                        dataPack = ModernDataPack,
                        runtimeTrust = exactTrust(profile),
                        enemyStages = StatStages(),
                        playerBattlerState = playerObs,
                        enemyBattlerState = enemyObs,
                        activeBattle = true
                    )
                )
                assertEquals("Unverified/Doubles active battle must not invoke Tier-B calculator", 0, calls.get())
                assertTrue("Refused Tier-B active battle must omit unverified switch rankings", fallback.isEmpty())
            }
        }
    }

    @Test
    fun `Vanilla HnS Unbound and unverified Radical Red retain exact existing capability behavior`() {
        val vanillaReq = DamageCalculationRequest(
            attacker = CalcPokemonInput("Machamp"),
            defender = CalcPokemonInput("Snorlax"),
            move = CalcMoveInput("Rock Slide")
        )
        val tierBReady = CalcRequestBoundary.build(
            verifiedExpansionProfile,
            exactTrust(verifiedExpansionProfile),
            baselineRequest
        ) as CalcRequestOutcome.Ready
        val forgedVanillaReq = vanillaReq.copy(
            typeSystem = tierBReady.request.typeSystem,
            expansionTierBOperands = tierBReady.request.expansionTierBOperands
        )

        for (id in listOf("vanilla_firered", "vanilla_emerald")) {
            val vanilla = bundledProfile(id)
            val ready = CalcRequestBoundary.build(vanilla, exactTrust(vanilla), forgedVanillaReq)
                as CalcRequestOutcome.Ready
            assertEquals(CalcRuleset.VANILLA_GEN3, ready.verdict.ruleset)
            assertEquals(CalcSupport.VERIFIED, ready.verdict.support)
            assertTrue(ready.verdict.limitations.isEmpty())
            assertNull(ready.request.expansionTierBOperands)
            assertNull(ready.request.unboundOperands)
            assertFalse(JSONObject(buildCalcRequestJson(ready.request)).has("typeSystem"))
        }

        val hns = bundledProfile("heart_and_soul")
        val hnsCap = CalcCapabilityPolicy.capabilityFor(hns)!!
        assertEquals(CalcRuleset.HNS_2_0_5, hnsCap.ruleset)
        assertEquals(CalcSupport.ESTIMATED, hnsCap.ceiling)
        assertTrue(hnsCap.alwaysLimitations.isEmpty())
        val hnsRefusal = CalcRequestBoundary.build(hns, null, vanillaReq) as CalcRequestOutcome.Refused
        assertTrue(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE in hnsRefusal.verdict.blockingLimitations)

        val unbound = bundledProfile("unbound")
        val unboundCap = CalcCapabilityPolicy.capabilityFor(unbound)!!
        assertEquals(CalcRuleset.UNBOUND_CFRU, unboundCap.ruleset)
        assertEquals(listOf(CalcLimitation.UNBOUND_CFRU_SOURCE_APPROXIMATE), unboundCap.alwaysLimitations)
        val unboundReady = CalcRequestBoundary.build(
            unbound,
            exactTrust(unbound),
            DamageCalculationRequest(
                attacker = CalcPokemonInput("Charmander"),
                defender = CalcPokemonInput("Bulbasaur"),
                move = CalcMoveInput("Flamethrower")
            )
        ) as CalcRequestOutcome.Ready
        assertEquals(CalcRuleset.UNBOUND_CFRU, unboundReady.verdict.ruleset)
        assertEquals(CalcSupport.ESTIMATED, unboundReady.verdict.support)
        assertEquals(listOf(CalcLimitation.UNBOUND_CFRU_SOURCE_APPROXIMATE), unboundReady.verdict.limitations)

        val radicalRed = bundledProfile("radical_red")
        assertNull(CalcCapabilityPolicy.capabilityFor(radicalRed))
    }
}
