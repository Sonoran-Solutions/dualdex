package com.dualdex.calculator.census

import com.dualdex.calculator.CalcCapabilityVerdict
import com.dualdex.calculator.CalcLimitation
import com.dualdex.calculator.CalcRuleset
import com.dualdex.calculator.CalcSupport
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.hns.HnsAbilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for the issue #84 census engine, its baseline, its metric definitions and its
 * artifact gate.
 *
 * The trainer-source EXTRACTION tests (fail-closed parsing of the pinned C source) live in
 * `tools/hns-calc-census/test_hns_trainer_source.py`, where the parser is; the full pinned
 * checkout is exercised by `./ci.sh source-check`.
 */
class HnsCalcCensusTest {

    private val root: File get() = HnsCalcCensusGenerator.repositoryRoot()

    private val profile get() = HnsCalcCensusBaseline.profile

    private val pack: com.dualdex.pokemon.GameDataPack
        get() = requireNotNull(GameDataPackRegistry.getForProfile(profile))

    /**
     * The shared derivation. A full derivation drives the production policy for ~20 000 requests
     * and ~2.2 million ability trials, so every test that only READS the census reuses it; the
     * determinism test deliberately derives twice.
     */
    private fun artifacts() = HnsCalcCensusGenerator.deriveCached(root)

    // ------------------------------------------------------------------------------ baseline

    @Test
    fun `the census baseline reaches the production policy at its real exact trusted ceiling`() {
        assertTrue(
            "the census must assert exact trust through the production mechanism",
            com.dualdex.calculator.CalcCapabilityPolicy.isExactRuntimeVerified(
                profile, HnsCalcCensusBaseline.trust
            )
        )
        assertEquals(CalcRuleset.HNS_2_0_5, com.dualdex.calculator.CalcCapabilityPolicy
            .capabilityFor(profile)?.ruleset)
    }

    @Test
    fun `the census profile agrees with the bundled profile on the fields that select the ruleset`() {
        val bundled = com.dualdex.romhack.ProfileLoader.parseProfile(
            File(root, "app/src/main/assets/profiles/heart_and_soul.json").readText()
        )
        assertEquals(bundled.gameDataPackId, profile.gameDataPackId)
        assertEquals(bundled.engine, profile.engine)
        assertEquals(bundled.hasPhysSpecSplit, profile.hasPhysSpecSplit)
    }

    @Test
    fun `the neutral baseline reaches a fully modelled display with no blocker`() {
        val control = HnsCalcCensusEngine.baselinePositiveControl(pack, profile)
        assertEquals(HnsCensusResultTier.FULLY_MODELLED.wireName, control.displayTier)
        assertTrue(
            "a neutral baseline must not fabricate a blocker, got ${control.limitations}",
            control.limitations.isEmpty()
        )
    }

    // ------------------------------------------------------------------------- tier semantics

    @Test
    fun `the tier classifier refuses exactly what production refuses`() {
        val refused = HnsCensusDisplayClassifier.classify(
            verdict(CalcSupport.UNSUPPORTED, listOf(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED))
        )
        assertEquals(HnsCensusResultTier.REFUSED, refused.tier)
        assertFalse(refused.displayable)

        val displayable = HnsCensusDisplayClassifier.classify(
            verdict(CalcSupport.ESTIMATED, listOf(CalcLimitation.ROM_NOT_EXACT_VERIFIED))
        )
        assertEquals(HnsCensusResultTier.FULLY_MODELLED, displayable.tier)
        assertTrue(displayable.displayable)
    }

    @Test
    fun `a non blocking limitation never refuses and never becomes a blocker`() {
        val outcome = HnsCensusDisplayClassifier.classify(
            verdict(CalcSupport.ESTIMATED, listOf(CalcLimitation.ROM_NOT_EXACT_VERIFIED))
        )
        assertTrue(outcome.blockers.isEmpty())
    }

    @Test
    fun `the schema can represent a caveated estimate without issue 86 being implemented`() {
        // The tier exists, is distinct from the other two, and carries a wire name the artifacts
        // already publish. Issue #86 only has to start producing it; nothing here has to change.
        assertEquals(3, HnsCensusResultTier.entries.size)
        assertEquals("CAVEATED_ESTIMATE", HnsCensusResultTier.CAVEATED_ESTIMATE.wireName)
        val json = HnsCalcCensusReport.toJson(artifacts().run)
        assertTrue(json.contains("\"CAVEATED_ESTIMATE\""))
        assertEquals(
            "issue #86 is not implemented, so the census must contain no caveated estimate",
            0,
            artifacts().run.requests.count {
                it.outcome.tier == HnsCensusResultTier.CAVEATED_ESTIMATE
            }
        )
    }

    // ---------------------------------------------------------------------------- the census

    @Test
    fun `the census enumerates every trainer battle and Pokemon of the pinned source`() {
        val run = artifacts().run
        assertEquals(854, run.trainers.size)
        assertEquals(1825, run.trainers.sumOf { it.party.size })
        assertEquals(777, run.trainers.count { it.isSingles })
        assertEquals(77, run.trainers.count { !it.isSingles })
        // The committed inventory's own sanity floors.
        val inventory = org.json.JSONObject(
            File(root, HnsCalcCensusGenerator.INVENTORY_RELATIVE_PATH).readText()
        )
        assertEquals(854, inventory.getInt("trainerBattles"))
        assertEquals(1825, inventory.getInt("trainerPokemon"))
        assertEquals(
            HnsCalcCensusBaseline.PINNED_COMMIT,
            inventory.getString("pinnedCommit")
        )
    }

    @Test
    fun `every resolver output is right - species, level, ability, item and moves`() {
        val run = artifacts().run
        val movesByMon = run.trainers.flatMap { trainer -> trainer.party.map { trainer to it } }
        for ((trainer, mon) in movesByMon) {
            val species = requireNotNull(pack.getSpecies(mon.speciesId)) {
                "${trainer.key} slot ${mon.partySlot} species ID ${mon.speciesId}"
            }
            assertEquals(species.name, mon.species)
            assertTrue("level out of range for ${trainer.key}", mon.level in 1..100)
            assertTrue("no moves for ${trainer.key} slot ${mon.partySlot}", mon.moves.isNotEmpty())
            assertTrue("more than four moves for ${trainer.key}", mon.moves.size <= 4)
            assertNotNull(
                "unknown ability for ${trainer.key}",
                HnsAbilityRegistry.classify(mon.abilityId).abilityId
            )
            assertTrue(
                "item ${mon.itemId} out of the pinned catalogue for ${trainer.key}",
                com.dualdex.pokemon.hns.Hns205ItemCatalogue.get(mon.itemId) != null
            )
            assertEquals(
                "the census must record where a moveset came from",
                true, mon.movesSource == "party-entry" || mon.movesSource == "level-up-learnset"
            )
        }
    }

    @Test
    fun `both attacker directions are generated for every trainer Pokemon`() {
        val run = artifacts().run
        val byTrainerSlot = run.requests.groupBy { it.trainerKey to it.trainerSlot }
        for (trainer in run.trainers) {
            for (mon in trainer.party) {
                val requests = byTrainerSlot[trainer.key to mon.partySlot] ?: emptyList()
                assertTrue(
                    "no requests for ${trainer.key} slot ${mon.partySlot}",
                    requests.isNotEmpty()
                )
                assertTrue(
                    "the reference -> trainer direction is missing for ${trainer.key}",
                    requests.any {
                        it.direction == HnsCalcCensusEngine.DIRECTION_REFERENCE_TO_TRAINER
                    } || requests.all { it.attackerSpecies != "Chikorita" && it.attackerSpecies != "Cyndaquil" }
                )
            }
        }
        // At the census level both directions exist in every reference team.
        for (team in run.referenceLeads) {
            val teamRequests = run.requests.filter { it.referenceTeam == team.teamId }
            assertTrue(
                "${team.teamId}: no reference -> trainer requests",
                teamRequests.any {
                    it.direction == HnsCalcCensusEngine.DIRECTION_REFERENCE_TO_TRAINER
                }
            )
            assertTrue(
                "${team.teamId}: no trainer -> reference requests",
                teamRequests.any {
                    it.direction == HnsCalcCensusEngine.DIRECTION_TRAINER_TO_REFERENCE
                }
            )
            // The fixture lead must always be the attacker in direction one and the defender in
            // direction two; a swapped direction would silently measure the wrong thing.
            teamRequests
                .filter { it.direction == HnsCalcCensusEngine.DIRECTION_REFERENCE_TO_TRAINER }
                .forEach { assertEquals(team.species, it.attackerSpecies) }
            teamRequests
                .filter { it.direction == HnsCalcCensusEngine.DIRECTION_TRAINER_TO_REFERENCE }
                .forEach { assertEquals(team.species, it.defenderSpecies) }
        }
    }

    @Test
    fun `non damaging moves are excluded from the denominator with a reason and never refused`() {
        val run = artifacts().run
        assertTrue(run.excludedMoves.isNotEmpty())
        for (excluded in run.excludedMoves) {
            assertTrue(
                "an excluded move must carry a reason",
                excluded.reason == "NON_DAMAGING_MOVE" ||
                    excluded.reason == "MOVE_DAMAGE_SHAPE_NOT_IN_ORDINARY_SUBSET"
            )
            if (excluded.reason == "NON_DAMAGING_MOVE") {
                assertEquals(0, excluded.movePower)
            } else {
                assertTrue(excluded.movePower > 0)
            }
            // Nothing that was excluded may also be evaluated: the denominator and the exclusion
            // list must partition the pinned movesets.
            assertFalse(
                "excluded move ${excluded.move} was also evaluated for ${excluded.trainerKey}",
                run.requests.any {
                    it.trainerKey == excluded.trainerKey &&
                        it.trainerSlot == excluded.trainerSlot &&
                        it.move == excluded.move
                }
            )
        }
        // Every evaluated request must be a damaging, ordinary move.
        for (request in run.requests) {
            assertTrue("evaluated a status move: ${request.move}", request.movePower > 0)
        }
    }

    @Test
    fun `blocker counts never double count a battle`() {
        val run = artifacts().run
        val json = HnsCalcCensusReport.toJson(run)
        val root = org.json.JSONObject(json)
        val blockers = root.getJSONArray("blockers")
        val battleKeysByTrainer = run.trainers.map { it.key }.toSet()
        for (index in 0 until blockers.length()) {
            val blocker = blockers.getJSONObject(index)
            val battles = blocker.getInt("battles")
            val requests = blocker.getInt("requests")
            assertTrue("battles must be positive", battles > 0)
            assertTrue(
                "requests ($requests) can never be fewer than the battles ($battles) they span",
                requests >= battles
            )
            assertTrue("battles can never exceed the census", battles <= battleKeysByTrainer.size)
        }
        // A direct cross-check of one blocker: recompute its distinct battles from the requests.
        val top = blockers.getJSONObject(0)
        val kind = top.getString("limitation")
        val side = if (top.isNull("side")) null else top.getString("side")
        val mechanic = if (top.isNull("mechanic")) null else top.getString("mechanic")
        val recomputed = run.requests
            .filter { request ->
                request.outcome.blockers.any {
                    it.limitation.name == kind && it.side == side && it.identity == mechanic
                }
            }
            .map { it.trainerKey }
            .distinct()
            .size
        assertEquals(recomputed, top.getInt("battles"))
    }

    // ------------------------------------------------------------------ Random Abilities view

    @Test
    fun `the Random Abilities view covers the complete pinned ability domain`() {
        val run = artifacts().run
        assertEquals(310, run.abilityDomain.size)
        assertEquals(
            "the domain must be the pinned range 1..ABILITY_ID_MAX",
            (1..com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.ABILITY_ID_MAX).toList(),
            run.abilityDomain.map { it.first }
        )
        val ids = run.abilityTrials.map { it.abilityId }.distinct()
        assertEquals(run.abilityDomain.size, ids.size)
    }

    @Test
    fun `the Random Abilities view distinguishes attacker from defender`() {
        val run = artifacts().run
        val sides = run.abilityTrials.map { it.side }.distinct().sorted()
        assertEquals(listOf("attacker", "defender"), sides)
        // Both categories are represented, so a category-conditional rule is measurable.
        val categories = run.abilityTrials.map { it.category }.distinct().sorted()
        assertEquals(listOf("Physical", "Special"), categories)
        // A category-conditional reviewed rule must actually differ between the two categories:
        // Huge Power is provably irrelevant for a special move and relevant for a physical one.
        val hugePower = run.abilityTrials.filter { it.abilityId == 37 && it.side == "attacker" }
        assertEquals(2, hugePower.size)
        val physical = hugePower.single { it.category == "Physical" }
        val special = hugePower.single { it.category == "Special" }
        assertTrue("Huge Power must block an attacker's physical move", physical.blockedByAbility)
        assertFalse("Huge Power must not block an attacker's special move", special.blockedByAbility)
        assertEquals("PROVEN_IRRELEVANT", special.abilityRelevance)
    }

    @Test
    fun `contextual ability results survive into the census output`() {
        val run = artifacts().run
        val json = HnsCalcCensusReport.toJson(run)
        assertTrue("the three-valued relevance must be published", json.contains("\"relevance\""))
        assertTrue(json.contains("PROVEN_IRRELEVANT"))
        assertTrue(json.contains("RELEVANT"))
        assertTrue(json.contains("UNKNOWN"))
        // A reviewed context rule must be named, not flattened to a generic code.
        assertTrue(
            "a reviewed rule name must appear in the artifact",
            json.contains("thick_fat_other_move_type") ||
                json.contains("attack_stat_ability_special_move")
        )
        // Every non-cleared production limitation must be attributable from the request table.
        for (request in run.requests) {
            if (request.outcome.tier == HnsCensusResultTier.REFUSED) {
                assertTrue(
                    "a refused request must name at least one cause: ${request.key}",
                    request.outcome.blockers.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun `contextual item results survive into the census output`() {
        val run = artifacts().run
        val json = HnsCalcCensusReport.toJson(run)
        // Wise Glasses is the one held item the production calculator models; the fixture holds it
        // for the whole census, so if the census flattened item decisions to a generic code this
        // item would appear as a blocker. It must not.
        assertFalse(
            "a modelled item must never be reported as a blocker",
            org.json.JSONObject(json).getJSONArray("itemBlockers").let { items ->
                (0 until items.length()).any {
                    items.getJSONObject(it).getString("item") == "WISE GLASSES"
                }
            }
        )
        // And the item decisions themselves must be preserved per request.
        assertTrue(
            run.requests.any { request -> request.outcome.itemDecisions.isNotEmpty() }
        )
    }

    @Test
    fun `the ability cohorts partition every eligible request exactly once`() {
        val run = artifacts().run
        val cohortTotal = run.abilityCohorts.sumOf { it.requestCount }
        assertEquals(
            "cohorts must partition the eligible requests",
            run.requests.size,
            cohortTotal
        )
        val allCohortRequests = run.abilityCohorts.flatMap { it.requestKeys }
        assertEquals(
            "no request may appear in two cohorts",
            run.requests.size,
            allCohortRequests.distinct().size
        )
    }

    // ------------------------------------------------------------------------- determinism

    @Test
    fun `the census is deterministic - two derivations produce identical artifacts`() {
        // Deliberately the UNCACHED derivation: this is the property that matters.
        val first = HnsCalcCensusGenerator.derive(root)
        val second = HnsCalcCensusGenerator.derive(root)
        assertEquals(first.json, second.json)
        assertEquals(first.markdown, second.markdown)
        assertTrue(
            "the gzip framing must be byte identical too",
            HnsCalcCensusGenerator.gzipDeterministic(first.json)
                .contentEquals(HnsCalcCensusGenerator.gzipDeterministic(second.json))
        )
    }

    @Test
    fun `the artifacts carry no timestamp and no absolute path`() {
        val artifacts = artifacts()
        for ((name, text) in listOf("census.json" to artifacts.json, "doc" to artifacts.markdown)) {
            assertFalse("$name must not contain an absolute repo path", text.contains(root.absolutePath))
            assertFalse("$name must not contain a home directory", text.contains(System.getProperty("user.home") ?: "/nonexistent"))
            // A generation timestamp would appear as an ISO-8601 date, never as the word.
            val isoDate = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")
            assertFalse("$name must not contain a generation timestamp", isoDate.containsMatchIn(text))
            for (marker in listOf("generatedAt", "generated_at", "Instant.now", "System.currentTimeMillis")) {
                assertFalse("$name must not contain $marker", text.contains(marker))
            }
        }
    }

    @Test
    fun `stale artifacts fail the check with a usable explanation`() {
        val artifacts = artifacts()
        val scratch = java.nio.file.Files.createTempDirectory("census-check").toFile()
        try {
            File(scratch, "tools/hns-calc-census").mkdirs()
            File(scratch, "docs").mkdirs()
            // Nothing committed yet: both artifacts must be reported missing by name.
            val missing = HnsCalcCensusGenerator.check(scratch, artifacts)
            assertNotNull(missing)
            assertTrue(missing!!.contains(HnsCalcCensusGenerator.JSON_RELATIVE_PATH))
            assertTrue(missing.contains(HnsCalcCensusGenerator.DOC_RELATIVE_PATH))

            // A stale JSON must be reported with the first differing line.
            HnsCalcCensusGenerator.write(scratch, artifacts)
            assertNull(HnsCalcCensusGenerator.check(scratch, artifacts))
            val jsonFile = File(scratch, HnsCalcCensusGenerator.JSON_RELATIVE_PATH)
            val bytes = jsonFile.readBytes()
            File(scratch, HnsCalcCensusGenerator.DOC_RELATIVE_PATH)
                .writeText(artifacts.markdown.replace("854", "853"))
            val stale = HnsCalcCensusGenerator.check(scratch, artifacts)
            assertNotNull(stale)
            assertTrue(stale!!.contains("stale"))
            assertTrue(stale.contains(HnsCalcCensusGenerator.DOC_RELATIVE_PATH))
            // Deliberately stale, but still valid JSON, so the check must diff it by line.
            val tampered = org.json.JSONObject(artifacts.json).apply {
                getJSONObject("counts").put("eligibleRequests", 1)
            }
            jsonFile.writeBytes(HnsCalcCensusGenerator.gzipDeterministic(tampered.toString()))
            val staleJson = HnsCalcCensusGenerator.check(scratch, artifacts)
            assertNotNull(staleJson)
            assertTrue(staleJson!!.contains("stale"))
            assertTrue("the diff must point at a line", staleJson.contains("line "))
            // Sanity: the untouched bytes we started from are the committed ones.
            assertTrue(bytes.isNotEmpty())
        } finally {
            scratch.deleteRecursively()
        }
    }

    @Test
    fun `the pinned ability domain helper is the catalogue's own domain`() {
        val domain = HnsAbilityRegistry.pinnedAbilityDomain()
        assertEquals(311, domain.size)
        assertEquals(0, domain.first().first)
        assertEquals(
            com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.ABILITY_ID_MAX,
            domain.last().first
        )
        assertEquals("Overgrow", domain.single { it.first == 65 }.second)
    }

    private fun verdict(
        support: CalcSupport,
        limitations: List<CalcLimitation>
    ): CalcCapabilityVerdict = CalcCapabilityVerdict(
        support = support,
        capability = requireNotNull(com.dualdex.calculator.CalcCapabilityPolicy.capabilityFor(profile)),
        limitations = limitations,
        request = if (support == CalcSupport.UNSUPPORTED) {
            null
        } else {
            com.dualdex.calculator.DamageCalculationRequest(
                attacker = com.dualdex.calculator.CalcPokemonInput(species = "Chikorita"),
                defender = com.dualdex.calculator.CalcPokemonInput(species = "Pidgey"),
                move = com.dualdex.calculator.CalcMoveInput(name = "Tackle")
            )
        }
    )
}
