package com.dualdex.calculator

import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.unbound.UnboundDataPack
import com.dualdex.pokemon.unbound.UnboundSpeciesTable
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.ProfileMatchMethod
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CalcUnboundCapabilityTest {
    private fun profile(id: String): RomHackProfile {
        val file = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles/$id.json") }
            .first { it.isFile }
        return ProfileLoader.parseProfile(file.readText())
    }

    private val unbound = profile("unbound")

    private fun exact(profile: RomHackProfile = unbound): RuntimeRomTrust {
        val hash = profile.sha256Hashes.first()
        return RuntimeRomTrust.from(RomCompatibility.verified(profile, hash), hash)
    }

    private val request = DamageCalculationRequest(
        attacker = CalcPokemonInput("Charmander"),
        defender = CalcPokemonInput("Bulbasaur"),
        move = CalcMoveInput("Flamethrower")
    )

    private fun ready(input: DamageCalculationRequest = request): CalcRequestOutcome.Ready =
        CalcRequestBoundary.build(unbound, exact(), input) as CalcRequestOutcome.Ready

    private fun assertRefused(
        input: DamageCalculationRequest,
        reason: CalcLimitation,
        trust: RuntimeRomTrust? = exact(),
        profile: RomHackProfile = unbound,
        activeBattle: Boolean = false
    ) {
        val outcome = CalcRequestBoundary.build(profile, trust, input, activeBattle = activeBattle)
        assertTrue("expected refusal for $reason, got $outcome", outcome is CalcRequestOutcome.Refused)
        val verdict = (outcome as CalcRequestOutcome.Refused).verdict
        assertEquals(CalcSupport.UNSUPPORTED, verdict.support)
        assertTrue(reason in verdict.blockingLimitations)
        assertNull(verdict.request)
        assertFalse(CalcAuthorizedExecution.calculate(verdict) { error("refusal reached the engine") }.success)
    }

    @Test
    fun `exact base form ordinary hit is explicitly estimated through the production boundary`() {
        val result = ready()
        assertFalse(UnboundDataPack.allowGlobalFallback)
        assertEquals(CalcRuleset.UNBOUND_CFRU, result.verdict.ruleset)
        assertEquals("unbound_exact", result.verdict.capability.contentSource)
        assertEquals(CalcSupport.ESTIMATED, result.verdict.capability.ceiling)
        assertEquals(CalcSupport.ESTIMATED, result.verdict.support)
        assertEquals(listOf(CalcLimitation.UNBOUND_CFRU_SOURCE_APPROXIMATE), result.verdict.limitations)
        assertTrue(result.verdict.mayRunEngine)
        val presentation = CalcResultPresentation.forVerdict(result.verdict, result.request)
        assertFalse(presentation.isVerified)
        assertTrue(presentation.headline.contains("source-approximate"))
        assertTrue(presentation.headline.contains("ordinary hits in Singles"))
        assertEquals("unbound_cfru", JSONObject(buildCalcRequestJson(result.request)).getString("typeSystem"))
    }

    @Test
    fun `hash and profile identity must match the pinned candidate`() {
        val reason = CalcLimitation.UNBOUND_ROM_NOT_EXACT_VERIFIED
        assertRefused(request, reason, trust = null)
        assertRefused(request, reason, trust = exact().copy(matchMethod = ProfileMatchMethod.DEFAULT_FALLBACK))
        assertRefused(request, reason, trust = exact().copy(activeRomSha256 = "0".repeat(64)))
        val different = unbound.copy(sha256Hashes = listOf("1".repeat(64)))
        assertRefused(request, reason, trust = exact(different), profile = different)
        assertNull(CalcCapabilityPolicy.capabilityFor(unbound.copy(gameDataPackId = null)))
        assertNull(CalcCapabilityPolicy.capabilityFor(unbound.copy(id = "other")))
    }

    @Test
    fun `unmapped species and non-base forms fail closed on either side`() {
        val alternateId = UnboundSpeciesTable.FORM_CLASS.indices.first {
            UnboundSpeciesTable.FORM_CLASS[it] != UnboundSpeciesTable.FORM_BASE &&
                UnboundDataPack.getSpecies(it) != null
        }
        val raw = CalcUnboundParticipant(alternateId, CalcRawStats(120, 100, 90, 130, 110), 150)
        val reason = CalcLimitation.SPECIES_NOT_IN_PINNED_DATA
        assertRefused(request.copy(attacker = request.attacker.copy(unbound = raw)), reason)
        assertRefused(request.copy(defender = request.defender.copy(unbound = raw)), reason)
        assertRefused(request.copy(attacker = request.attacker.copy(unbound = raw.copy(speciesId = 0))), reason)
        assertRefused(request.copy(defender = request.defender.copy(
            species = "Bulbasaur" + UnboundDataPack.FORM_UNVERIFIED_SUFFIX,
            unbound = raw.copy(speciesId = 1)
        )), reason)
    }

    @Test
    fun `unmapped moves and mapped moves outside the ordinary-hit domain fail closed`() {
        assertRefused(request.copy(move = CalcMoveInput("Move #922")), CalcLimitation.MOVE_NOT_IN_PINNED_DATA)
        for (name in listOf("Swords Dance", "Double Slap", "Seismic Toss", "Psyshock", "Eruption")) {
            assertNotNull("fixture requires a mapped move", UnboundDataPack.getMoveByName(name))
            assertRefused(request.copy(move = CalcMoveInput(name)), CalcLimitation.UNBOUND_MOVE_NOT_MODELLED)
        }
    }

    @Test
    fun `unsupported fields and numeric inputs produce no engine request`() {
        val reason = CalcLimitation.FIELD_CONDITION_NOT_MODELLED
        assertRefused(request.copy(field = CalcFieldInput(gameType = CalcGameTypes.DOUBLES)), reason)
        assertRefused(request.copy(field = CalcFieldInput(weather = "Sand")), reason)
        assertRefused(request.copy(field = CalcFieldInput(terrain = "Grassy")), reason)
        assertRefused(request, CalcLimitation.UNBOUND_LIVE_BATTLE_STATE_UNAVAILABLE, activeBattle = true)
        assertRefused(request.copy(defender = request.defender.copy(level = 101)), CalcLimitation.LEVEL_OUT_OF_RANGE)
        assertRefused(request.copy(attacker = request.attacker.copy(boosts = StatBlock(atk = -7))),
            CalcLimitation.BOOSTS_OUT_OF_RANGE)
        val raw = CalcUnboundParticipant(4, CalcRawStats(120, 100, 90, 1000, 110), 150)
        assertRefused(request.copy(attacker = request.attacker.copy(unbound = raw)), CalcLimitation.STAT_VALUES_OUT_OF_RANGE)
        assertRefused(request.copy(attacker = request.attacker.copy(unbound = raw.copy(
            stats = raw.stats.copy(spAttack = 130),
            types = listOf(PokemonType.STELLAR)
        ))), CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED)
        assertRefused(request.copy(attacker = request.attacker.copy(abilityId = 255)),
            CalcLimitation.UNBOUND_MODIFIER_NOT_MODELLED)
    }

    @Test
    fun `raw stats stages types weather screens and crit map to CFRU without generic overrides`() {
        val offense = CalcUnboundParticipant(4, CalcRawStats(123, 100, 90, 137, 110), 150,
            types = listOf(PokemonType.FIRE, PokemonType.FAIRY))
        val defense = CalcUnboundParticipant(1, CalcRawStats(100, 101, 90, 100, 119), 175,
            types = listOf(PokemonType.GRASS, PokemonType.POISON))
        val result = ready(request.copy(
            attacker = request.attacker.copy(level = 51, status = "brn", boosts = StatBlock(atk = -2, spa = 3),
                origin = CalcInputOrigin.LIVE_READ, abilityId = 0, itemId = 0, unbound = offense),
            defender = request.defender.copy(boosts = StatBlock(def = 4, spd = -1),
                origin = CalcInputOrigin.LIVE_READ, abilityId = 0, itemId = 0, unbound = defense),
            move = request.move.copy(isCrit = true),
            field = CalcFieldInput(weather = "Sun", defenderSide = SideConditions(true, true)),
            moveOverride = CalcMoveOverride(basePower = 1, type = "Ice"),
            typeSystem = "hns_2_0_5"
        ))
        val json = JSONObject(buildCalcRequestJson(result.request))
        val operands = json.getJSONObject("unbound")
        val expected = mapOf(
            "level" to 51, "power" to 90, "category" to 1, "move_type" to 10,
            "attack" to 123, "sp_attack" to 137, "defense" to 101, "sp_defense" to 119,
            "attacker_type_1" to 10, "attacker_type_2" to 23, "defender_type_1" to 12, "defender_type_2" to 3,
            "attack_stage" to 4, "sp_attack_stage" to 9, "defense_stage" to 10, "sp_defense_stage" to 5,
            "attacker_status" to 16, "weather" to 32, "screens" to 3, "critical" to 1,
            "attacker_ability" to 0, "defender_ability" to 0, "attacker_hold_effect" to 0, "defender_hold_effect" to 0
        )
        assertEquals(expected.keys, operands.keys().asSequence().toSet())
        expected.forEach { (key, value) -> assertEquals(key, value, operands.getInt(key)) }
        assertEquals("Singles", json.getJSONObject("field").getString("gameType"))
        assertEquals(175, json.getJSONObject("display").getInt("defenderMaxHP"))
        assertNull(result.request.moveOverride)
    }

    @Test
    fun `screen party preparation retains exact move identity and discloses forecast assumptions`() {
        val partyMon = ParsedPokemon(
            isValid = true, isEmpty = false, pid = 0, tid = 1, sid = 1, nickname = "", otName = "",
            species = 4, heldItem = 0, level = 50, nature = 0, natureName = "Hardy", isShiny = false,
            abilitySlot = 0, isEgg = false, friendship = 0, experience = 0,
            hpIv = 0, attackIv = 0, defenseIv = 0, speedIv = 0, spAttackIv = 0, spDefenseIv = 0,
            hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0, spAttackEv = 0, spDefenseEv = 0,
            moves = intArrayOf(53, 0, 0, 0), pp = intArrayOf(10, 0, 0, 0),
            currentHp = 123, maxHp = 123, attack = 89, defense = 90, speed = 91,
            spAttack = 105, spDefense = 92, statusCondition = 0
        )
        val attacker = CalcParticipantPresenter.attacker(listOf(partyMon), 0, { "Charmander" },
            playerStages = com.dualdex.battle.StatStages())
        val defender = CalcParticipantPresenter.benchmarkDefender("Bulbasaur")
        fun build(move: String) = CalcRequestBoundary.build(
            unbound, exact(), attacker, defender, CalcMoveInput(move), CalcFieldInput()
        )
        val result = build("Flamethrower") as CalcRequestOutcome.Ready
        assertEquals(CalcSupport.ESTIMATED, result.verdict.support)
        assertTrue(CalcLimitation.UNBOUND_PARTY_OPERANDS_ASSUMED in result.verdict.limitations)
        val operands = JSONObject(buildCalcRequestJson(result.request)).getJSONObject("unbound")
        assertEquals(105, operands.getInt("sp_attack"))
        assertTrue(build("Rock Slide") is CalcRequestOutcome.Refused)
    }

    @Test
    fun `only a valid estimated CFRU response reaches presentation`() {
        val result = ready()
        val response = """{"success":true,"typeSystem":"unbound_cfru","minDamage":56,"maxDamage":66,
            "range":[56,66],"damage":[56,56,57,58,58,59,60,60,61,62,62,63,64,64,65,66]}"""
        assertEquals(16, parseCalcResponseJson(result.request, response).range.size)
        assertFalse(parseCalcResponseJson(result.request, response.replace("56,56", "56,55")).success)
        assertFalse(parseCalcResponseJson(result.request, response.replace("unbound_cfru", "hns_2_0_5")).success)
        assertTrue(CalcAuthorizedExecution.calculate(result.verdict) {
            parseCalcResponseJson(it, response)
        }.success)
        assertFalse(CalcAuthorizedExecution.calculate(result.verdict.copy(support = CalcSupport.VERIFIED)) {
            error("verified Unbound result reached engine")
        }.success)
    }

    @Test
    fun `H and S and vanilla FireRed and Emerald retain their capability and trust behavior`() {
        val baselineRequest = DamageCalculationRequest(
            attacker = CalcPokemonInput("Machamp"), defender = CalcPokemonInput("Snorlax"), move = CalcMoveInput("Rock Slide")
        )
        val hns = profile("heart_and_soul")
        val hnsRow = CalcCapabilityPolicy.capabilityFor(hns)!!
        assertEquals(CalcRuleset.HNS_2_0_5, hnsRow.ruleset)
        assertEquals(CalcSupport.ESTIMATED, hnsRow.ceiling)
        assertEquals("hns_2_0_5", hnsRow.contentSource)
        assertEquals(3, hnsRow.mechanicsGeneration)
        assertTrue(hnsRow.alwaysLimitations.isEmpty())
        val hnsRefusal = CalcRequestBoundary.build(hns, null, baselineRequest) as CalcRequestOutcome.Refused
        assertTrue(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE in hnsRefusal.verdict.blockingLimitations)

        val unboundRequest = ready().request
        val forgedDispatch = baselineRequest.copy(typeSystem = unboundRequest.typeSystem,
            unboundOperands = unboundRequest.unboundOperands)
        for (id in listOf("vanilla_firered", "vanilla_emerald")) {
            val vanilla = profile(id)
            val result = CalcRequestBoundary.build(vanilla, exact(vanilla), forgedDispatch) as CalcRequestOutcome.Ready
            assertEquals(CalcRuleset.VANILLA_GEN3, result.verdict.ruleset)
            assertEquals(CalcSupport.VERIFIED, result.verdict.support)
            assertTrue(result.verdict.limitations.isEmpty())
            assertEquals(3, result.request.gen)
            assertNull(result.request.unboundOperands)
            assertFalse(JSONObject(buildCalcRequestJson(result.request)).has("typeSystem"))
            val untrusted = CalcRequestBoundary.build(vanilla, null, baselineRequest) as CalcRequestOutcome.Ready
            assertEquals(CalcSupport.ESTIMATED, untrusted.verdict.support)
        }
    }
}
