package com.dualdex.coverage

import com.dualdex.battle.BattleHnsCalculationContext
import com.dualdex.calculator.*
import com.dualdex.pokemon.*
import com.dualdex.pokemon.hns.*
import com.dualdex.romhack.RomHackProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HnsCoverageTest {
    private class Store(var value: String? = null, var fail: Boolean = false) : CoverageStore {
        override fun read(): String? = value
        override fun write(json: String) { if (fail) error("disk full"); value = json }
    }
    private fun pokemon(species: Int = 152) = ParsedPokemon(
        isValid = true, isEmpty = false, pid = 1, tid = 2, sid = 3, nickname = "PRIVATE_NICKNAME",
        otName = "PRIVATE_TRAINER", species = species, heldItem = 0, level = 50, nature = 0,
        natureName = "Hardy", isShiny = false, abilitySlot = 0, isEgg = false, friendship = 255,
        experience = 100000, hpIv = 31, attackIv = 31, defenseIv = 31, speedIv = 31,
        spAttackIv = 31, spDefenseIv = 31, hpEv = 0, attackEv = 0, defenseEv = 0, speedEv = 0,
        spAttackEv = 0, spDefenseEv = 0, moves = intArrayOf(34), pp = intArrayOf(15), currentHp = 150,
        maxHp = 150, attack = 100, defense = 100, speed = 100, spAttack = 100, spDefense = 100, statusCondition = 0)
    private val matchup = CoverageMatchup(CoverageParticipant(152, "Chikorita", 0, 0),
        CoverageParticipant(16, "Pidgey", 0, 1), 34, "Body Slam")
    private val metadata = CoverageContext(CoverageFormat.SINGLES, CoverageBattleKind.UNKNOWN, CoverageToggle.UNKNOWN)
    private val request = DamageCalculationRequest(attacker = CalcPokemonInput("Chikorita"),
        defender = CalcPokemonInput("Pidgey"), move = CalcMoveInput("Body Slam"))
    private val capability = CalcCapability(CalcRuleset.HNS_2_0_5, 3, "hns_2_0_5", CalcSupport.ESTIMATED, emptyList(), "H&S")
    private val exactVerdict = CalcCapabilityVerdict(CalcSupport.ESTIMATED, capability, emptyList(), request)
    private val exact = CalcRequestOutcome.Ready(request, exactVerdict)
    private val ability = HnsAbilityRequestDecision(209, "Disguise", HnsAbilitySide.DEFENDER,
        HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT, HnsAbilityRequestRelevance.RELEVANT,
        "disguise_shield", "src/battle_util.c:1", "shield")
    private val item = HnsItemRequestDecision(481, "Focus Sash", HnsItemSide.ATTACKER,
        HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, HnsItemRequestRelevance.RELEVANT,
        "focus_sash", "src/battle_util.c:2", "survival")
    private fun repo(store: Store = Store(), cap: Int = 2000) = CoverageRepository(store, { 1234L }, cap).also { it.battle(true) }
    private fun rows(r: CoverageRepository) = JSONObject(r.export("test")).getJSONArray("records")
    private fun observe(r: CoverageRepository, m: CoverageMatchup = matchup, o: CalcRequestOutcome = exact,
                        c: CoverageContext = metadata) = r.record(m, c, CoverageObservation.from(o))
    private fun refusal() = CalcRequestOutcome.Refused(exactVerdict.copy(support = CalcSupport.UNSUPPORTED,
        request = null, limitations = listOf(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN),
        hnsAbilityDecisions = listOf(ability), hnsItemDecisions = listOf(item)))
    private fun caveat() = CalcRequestOutcome.Ready(request, exactVerdict.copy(hnsAbilityDecisions = listOf(ability),
        ignoredMechanics = listOf(IgnoredCalcMechanic.Ability(ability))))

    @Test fun exactTier() { assertEquals(CoverageTier.EXACT, CoverageObservation.from(exact).tier) }
    @Test fun caveatedTier() { assertEquals(CoverageTier.CAVEATED_ESTIMATE, CoverageObservation.from(caveat()).tier) }
    @Test fun refusedTier() { assertEquals(CoverageTier.REFUSED, CoverageObservation.from(refusal()).tier) }
    @Test fun abilityStructure() {
        val d = CoverageObservation.from(refusal()).mechanics.single { it.kind == "ABILITY" }
        assertEquals(209, d.id); assertEquals("Disguise", d.name); assertEquals("DEFENDER", d.side)
        assertEquals("RELEVANT", d.relevance); assertEquals("disguise_shield", d.rule)
        assertEquals("src/battle_util.c:1", d.source); assertEquals("BLOCKING", d.disposition)
    }
    @Test fun itemStructure() {
        val d = CoverageObservation.from(refusal()).mechanics.single { it.kind == "ITEM" }
        assertEquals(481, d.id); assertEquals("Focus Sash", d.name); assertEquals("ATTACKER", d.side)
        assertEquals("focus_sash", d.rule); assertNotNull(d.family)
    }
    @Test fun genericLimitation() {
        assertTrue(CoverageObservation.from(refusal()).mechanics.any { it.name == "LIVE_PARTICIPANT_STATE_UNKNOWN" })
    }
    @Test fun repeatOneRow() { val r = repo(); repeat(10) { observe(r) }; assertEquals(1, rows(r).length()) }
    @Test fun repeatIncrementsCount() { val r = repo(); repeat(10) { observe(r) }; assertEquals(10L, rows(r).getJSONObject(0).getLong("seenCount")) }
    @Test fun changesRetainedInsideRow() {
        val r = repo(); observe(r); observe(r, o = caveat()); observe(r, o = refusal())
        val row = rows(r).getJSONObject(0); assertEquals(1, rows(r).length())
        CoverageTier.entries.forEach { assertEquals(1, row.getJSONObject("outcomes").getInt(it.name)) }
        assertTrue(row.getJSONArray("mechanics").toString().contains("CAVEATED"))
        assertTrue(row.getJSONArray("mechanics").toString().contains("BLOCKING"))
    }
    @Test fun newBattleNewRow() { val r = repo(); observe(r); r.battle(false); r.battle(true); observe(r); assertEquals(2, rows(r).length()) }
    @Test fun redundantBeginSameSession() { val r = repo(); observe(r); r.battle(true); observe(r); assertEquals(1, rows(r).length()) }
    @Test fun outsideBattleIgnored() { val r = repo(); r.battle(false); observe(r); assertEquals(0, rows(r).length()) }
    @Test fun directionKey() { val r = repo(); observe(r); observe(r, matchup.copy(direction = "ENEMY_TO_PLAYER")); assertEquals(2, rows(r).length()) }
    @Test fun sameSpeciesDistinctSlots() {
        val r = repo(); observe(r); observe(r, matchup.copy(defender = matchup.defender.copy(partySlot = 1, engineIndex = 3)))
        assertEquals(2, rows(r).length())
    }
    @Test fun formatMetadata() {
        val r = repo(); CoverageFormat.entries.forEach { observe(r, c = metadata.copy(format = it)) }
        assertEquals(3, rows(r).getJSONObject(0).getJSONArray("contexts").length())
    }
    @Test fun battleKindMetadata() {
        val r = repo(); CoverageBattleKind.entries.forEach { observe(r, c = metadata.copy(battleKind = it)) }
        val contexts = rows(r).getJSONObject(0).getJSONArray("contexts").toString()
        CoverageBattleKind.entries.forEach { assertTrue(contexts.contains(it.name)) }
    }
    @Test fun randomAbilityMetadata() {
        val r = repo(); CoverageToggle.entries.forEach { observe(r, c = metadata.copy(randomAbilities = it)) }
        assertEquals(3, rows(r).getJSONObject(0).getJSONArray("contexts").length())
    }
    @Test fun aggregationDistinctRows() {
        val r = repo(); observe(r, o = refusal()); observe(r, matchup.copy(moveId = 1), refusal())
        val b = JSONObject(r.export("test")).getJSONArray("blockers").getJSONObject(0)
        assertEquals(2, b.getInt("logicalRows")); assertEquals(1, b.getInt("sessions"))
    }
    @Test fun refreshNotIndependentIncident() {
        val r = repo(); repeat(100) { observe(r, o = refusal()) }
        val b = JSONObject(r.export("test")).getJSONArray("blockers").getJSONObject(0)
        assertEquals(1, b.getInt("logicalRows")); assertEquals(100, b.getInt("observations"))
    }
    @Test fun aggregationDistinctSessions() {
        val r = repo(); observe(r, o = refusal()); r.battle(false); r.battle(true); observe(r, o = refusal())
        assertEquals(2, JSONObject(r.export("test")).getJSONArray("blockers").getJSONObject(0).getInt("sessions"))
    }
    @Test fun deterministicOldestEviction() {
        val r = repo(cap = 2); observe(r); observe(r, matchup.copy(moveId = 2)); observe(r)
        observe(r, matchup.copy(moveId = 3)); assertEquals(2, rows(r).getJSONObject(0).getInt("moveId"))
        assertEquals(1, JSONObject(r.export("test")).getInt("evictedRecords"))
    }
    @Test fun corruptStoreSafe() {
        listOf("{broken", "{}", "{\"schemaVersion\":2}").forEach { assertEquals(0, rows(repo(Store(it))).length()) }
    }
    @Test fun restartPersistsAndNewSession() {
        val store = Store(); val r = repo(store); observe(r)
        val resumed = repo(store); observe(resumed); assertEquals(2, rows(resumed).length())
        assertEquals(2, rows(resumed).getJSONObject(1).getInt("battleSession"))
    }
    @Test fun stableExportAndSchema() {
        val r = repo(); observe(r, o = refusal()); assertEquals(r.export("test"), r.export("test"))
        val root = JSONObject(r.export("test")); assertEquals(1, root.getInt("schemaVersion"))
        assertEquals("debug", root.getString("buildType")); assertEquals("hns_2_0_5", root.getString("profile"))
        assertTrue(r.export("test").startsWith("{\"appVersion\":"))
    }
    @Test fun privacyAllowlist() {
        val r = repo(); observe(r); val json = r.export("test")
        listOf("romPath", "savePath", "rawMemory", "account", "/storage/", "/home/").forEach { assertFalse(json.contains(it)) }
        val root = JSONObject(json); assertEquals(setOf("attacker", "defender", "moveId", "moveName", "direction",
            "battleSession", "firstSeen", "lastSeen", "seenCount", "outcomes", "contexts", "mechanics"),
            root.getJSONArray("records").getJSONObject(0).keys().asSequence().toSet())
    }
    @Test fun storageFailureKeepsObservation() {
        val r = repo(Store(fail = true)); observe(r); assertEquals(1, rows(r).length()); assertEquals(1L, r.storageFailures)
    }
    @Test fun noOpStoresNothing() {
        assertNull(NoOpHnsCalcCoverageLogger.sessionToken()); NoOpHnsCalcCoverageLogger.battle(true)
        assertNull(NoOpHnsCalcCoverageLogger.sessionToken())
        NoOpHnsCalcCoverageLogger.record(MoveDatabase.get(34), pokemon(), RomHackProfile.DEFAULT_FIRERED,
            BattleHnsCalculationContext(emptyList(), 0, 0, null, null, null, true), exact)
    }
    @Test fun noOpHasNoExportApi() {
        assertFalse(HnsCalcCoverageLogger::class.java.methods.any { it.name in setOf("export", "clear", "initialize") })
    }
    @Test fun observerFailureDoesNotMutateOutcome() {
        val failing = object : HnsCalcCoverageLogger {
            override fun battle(active: Boolean) = Unit
            override fun record(move: MoveInfo, defender: ParsedPokemon, profile: RomHackProfile,
                context: BattleHnsCalculationContext, outcome: CalcRequestOutcome) { error("logger broke") }
        }
        val context = BattleHnsCalculationContext(emptyList(), 0, 0, null, null, null, true)
        HnsCoverage.observe(MoveDatabase.get(34), pokemon(), RomHackProfile.DEFAULT_FIRERED, context, exact, failing)
        assertSame(request, exact.request); assertSame(exactVerdict, exact.verdict)
        assertEquals(CoverageTier.EXACT, CoverageObservation.from(exact).tier)
    }
    @Test fun clearResetCountsPreservesSessionCounter() {
        val r = repo(); observe(r); r.clear(); assertEquals(0, rows(r).length()); observe(r)
        assertEquals(1, rows(r).getJSONObject(0).getInt("battleSession"))
    }
    @Test fun mechanicCapReportsDrops() {
        val r = repo(); r.record(matchup, metadata, CoverageObservation(CoverageTier.REFUSED,
            (0..CoverageRepository.MECHANIC_CAP).map { CoverageMechanic("LIMITATION", name = "test$it", disposition = "BLOCKING") }))
        assertEquals(CoverageRepository.MECHANIC_CAP, rows(r).getJSONObject(0).getJSONArray("mechanics").length())
        assertEquals(1, JSONObject(r.export("test")).getInt("droppedMechanicObservations"))
    }
    @Test fun debugObserverCapturesAllTiersAndExcludesPrivateIdentity() {
        val r = CoverageRepository(Store(), { 1234L })
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val logger = DebugHnsCalcCoverageLogger(r, worker)
            val profile = RomHackProfile.DEFAULT_FIRERED.copy(gameDataPackId = "hns_2_0_5")
            logger.battle(true)
            val context = BattleHnsCalculationContext(listOf(pokemon()), 0, 0, null, null, null, true, logger.sessionToken())
            listOf(exact, caveat(), refusal()).forEach { logger.record(MoveDatabase.get(34), pokemon(16), profile, context, it) }
            worker.submit {}.get()
            val row = rows(r).getJSONObject(0)
            assertEquals(1, rows(r).length()); CoverageTier.entries.forEach { assertEquals(1, row.getJSONObject("outcomes").getInt(it.name)) }
            assertEquals("UNKNOWN", row.getJSONArray("contexts").getJSONObject(0).getString("battleKind"))
            assertFalse(r.export("test").contains("PRIVATE_NICKNAME")); assertFalse(r.export("test").contains("PRIVATE_TRAINER"))
        } finally { worker.shutdownNow() }
    }
    @Test fun delayedJobRejectedAndSessionChangesInvalidateCache() {
        val r = CoverageRepository(Store(), { 1234L })
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val logger = DebugHnsCalcCoverageLogger(r, worker)
            val profile = RomHackProfile.DEFAULT_FIRERED.copy(gameDataPackId = "hns_2_0_5")
            logger.battle(true)
            val old = BattleHnsCalculationContext(listOf(pokemon()), 0, 0, null, null, null, true, logger.sessionToken())
            logger.battle(false); logger.battle(true)
            val fresh = old.copy(coverageSession = logger.sessionToken())
            logger.record(MoveDatabase.get(34), pokemon(16), profile, old, exact)
            logger.record(MoveDatabase.get(34), pokemon(16), profile, fresh, exact)
            worker.submit {}.get(); assertEquals(1, rows(r).length())
            assertNotEquals(old.coverageSession, fresh.coverageSession)
        } finally { worker.shutdownNow() }
    }

    @Test fun debugObserverKeepsUnreadMetadataUnknownAndRecordsObservedFormats() {
        val r = CoverageRepository(Store(), { 1234L })
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val logger = DebugHnsCalcCoverageLogger(r, worker)
            val profile = RomHackProfile.DEFAULT_FIRERED.copy(gameDataPackId = "hns_2_0_5")
            logger.battle(true)
            fun state(count: Int) = BattlerRuntimeObservation(HnsBattlerRuntimeState(
                status = HnsBattlerRuntimeStatus.OBSERVED, partySlot = 0,
                battlersCount = count, battlersCountReadable = true))
            val base = BattleHnsCalculationContext(listOf(pokemon()), 0, 0, null, null, null, true, logger.sessionToken())
            listOf(base, base.copy(playerBattlerState = state(2), enemyBattlerState = state(2),
                challengeSettings = HnsChallengeSettingsSnapshot(txRandomAbilities = HnsChallengeField(true, 0))),
                base.copy(playerBattlerState = state(4), enemyBattlerState = state(4),
                    challengeSettings = HnsChallengeSettingsSnapshot(txRandomAbilities = HnsChallengeField(true, 1))),
                base.copy(playerBattlerState = state(2), enemyBattlerState = state(4),
                    challengeSettings = HnsChallengeSettingsSnapshot(txRandomAbilities = HnsChallengeField(false, 1)))
            ).forEach { logger.record(MoveDatabase.get(34), pokemon(16), profile, it, exact) }
            worker.submit {}.get()
            val contexts = rows(r).getJSONObject(0).getJSONArray("contexts")
            assertEquals(3, contexts.length())
            val text = contexts.toString(); assertTrue(text.contains("SINGLES")); assertTrue(text.contains("DOUBLES"))
            assertTrue(text.contains("\"ON\"")); assertTrue(text.contains("\"OFF\"")); assertTrue(text.contains("UNKNOWN"))
        } finally { worker.shutdownNow() }
    }
    @Test fun timestampsMergeWithoutReplacingFirstSeen() {
        var time = 100L
        val r = CoverageRepository(Store(), { time }); r.battle(true); observe(r); time = 200; observe(r)
        val row = rows(r).getJSONObject(0)
        assertEquals(100L, row.getLong("firstSeen")); assertEquals(200L, row.getLong("lastSeen"))
    }
    @Test fun aggregationOrdersRowsThenIdentity() {
        val r = repo()
        val a = CoverageMechanic("LIMITATION", name = "A", disposition = "BLOCKING")
        val b = CoverageMechanic("LIMITATION", name = "B", disposition = "BLOCKING")
        r.record(matchup, metadata, CoverageObservation(CoverageTier.REFUSED, listOf(a, b)))
        r.record(matchup.copy(moveId = 1), metadata, CoverageObservation(CoverageTier.REFUSED, listOf(b)))
        val blockers = JSONObject(r.export("test")).getJSONArray("blockers")
        assertEquals("B", blockers.getJSONObject(0).getString("name"))
        assertEquals("A", blockers.getJSONObject(1).getString("name"))
    }

    @Test fun newlyReadableIdentityKeepsSelectedMatchupRow() {
        val r = repo()
        observe(r, matchup.copy(defender = matchup.defender.copy(speciesId = null, speciesName = "", engineIndex = null)), refusal())
        observe(r)
        assertEquals(1, rows(r).length()); assertEquals(2, rows(r).getJSONObject(0).getInt("seenCount"))
        assertEquals(16, rows(r).getJSONObject(0).getJSONObject("defender").getInt("speciesId"))
    }
    @Test fun engineIndexDistinguishesParticipantsWithoutPartySlot() {
        val r = repo(); val m = matchup.copy(defender = matchup.defender.copy(partySlot = null))
        observe(r, m); observe(r, m.copy(defender = m.defender.copy(engineIndex = 3)))
        assertEquals(2, rows(r).length())
    }

}
