package com.dualdex.calculator

import com.dualdex.battle.BattleDamageCalculator
import com.dualdex.battle.StatStages
import com.dualdex.battle.SwitchAdvisorInput
import com.dualdex.battle.SwitchAdvisorPresenter
import com.dualdex.battle.SwitchConfidenceTier
import com.dualdex.battle.SwitchRecommendationSource
import com.dualdex.pokemon.ModernDataPack
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PokemonType
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

    private fun assertRefused(
        profile: RomHackProfile,
        input: DamageCalculationRequest,
        trust: RuntimeRomTrust? = if (profile.sha256Hashes.isNotEmpty()) exactTrust(profile) else null,
        expectedReason: CalcLimitation? = null
    ) {
        val outcome = CalcRequestBoundary.build(profile, trust, input)
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
    fun `Tier-B can never execute as VERIFIED or with malformed addon responses`() {
        val ready = CalcRequestBoundary.build(
            verifiedExpansionProfile,
            exactTrust(verifiedExpansionProfile),
            baselineRequest
        ) as CalcRequestOutcome.Ready

        val validJson = """{"success":true,"typeSystem":"expansion_tierb","minDamage":146,"maxDamage":174,
            "range":[146,174],"damage":[146,146,150,152,152,156,156,158,158,162,164,164,168,168,170,174]}"""
        assertTrue(parseCalcResponseJson(ready.request, validJson).success)
        assertFalse(parseCalcResponseJson(ready.request, validJson.replace("expansion_tierb", "unbound_cfru")).success)
        assertFalse(parseCalcResponseJson(ready.request, validJson.replace("146,146", "146,145")).success)
        assertTrue(
            CalcAuthorizedExecution.calculate(ready.verdict) { parseCalcResponseJson(it, validJson) }.success
        )
        // Attempting to raise Tier-B to VERIFIED or strip TIER_B_ESTIMATED_ENGINE fails closed.
        assertFalse(
            CalcAuthorizedExecution.calculate(ready.verdict.copy(support = CalcSupport.VERIFIED)) {
                error("verified Tier-B result must never reach engine")
            }.success
        )
        assertFalse(
            CalcAuthorizedExecution.calculate(ready.verdict.copy(limitations = emptyList())) {
                error("Tier-B without TIER_B_ESTIMATED_ENGINE must never reach engine")
            }.success
        )
    }

    @Test
    fun `SwitchAdvisorPresenter gets ESTIMATED calc-driven rankings on verified Tier-B hacks`() {
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
        val bench = partyMon(9, "Blastoise", 57) // Surf
        val opponent = partyMon(6, "Charizard", 53) // Flamethrower

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
                val rolls = (0 until 16).map { 80 + it }
                DamageCalculationResponse(
                    success = true,
                    minDamage = rolls.first(),
                    maxDamage = rolls.last(),
                    range = rolls,
                    defenderMaxHP = 160
                )
            }
            val presenter = SwitchAdvisorPresenter(calculator = stubCalc)
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
                    activeBattle = true
                )
            )
            assertTrue("Expected Tier-B SwitchAdvisor to invoke calculator", calls.get() > 0)
            assertEquals(1, ranked.size)
            assertEquals(SwitchRecommendationSource.CALCULATOR, ranked[0].source)
            assertEquals(SwitchConfidenceTier.ESTIMATED, ranked[0].confidenceTier)
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
