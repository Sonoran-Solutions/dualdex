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
    fun `ability trial attribution separates refusal caveat and clear evidence`() {
        val relevant = com.dualdex.calculator.HnsAbilityRequestDecision(
            abilityId = 37,
            abilityName = "Huge Power",
            side = com.dualdex.calculator.HnsAbilitySide.ATTACKER,
            globalCategory = HnsAbilityRegistry.classify(37).category,
            relevance = com.dualdex.calculator.HnsAbilityRequestRelevance.RELEVANT,
            rule = "attack_stat_ability_physical_move",
            rationale = "synthetic complete evidence"
        )
        val unknown = com.dualdex.calculator.HnsAbilityRequestDecision(
            abilityId = 105,
            abilityName = "Super Luck",
            side = com.dualdex.calculator.HnsAbilitySide.DEFENDER,
            globalCategory = HnsAbilityRegistry.classify(105).category,
            relevance = com.dualdex.calculator.HnsAbilityRequestRelevance.UNKNOWN,
            rule = "unreviewed_context",
            rationale = "synthetic unknown evidence"
        )
        val verdict = CalcCapabilityVerdict(
            support = CalcSupport.UNSUPPORTED,
            capability = requireNotNull(com.dualdex.calculator.CalcCapabilityPolicy.capabilityFor(profile)),
            limitations = listOf(
                CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED,
                CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED
            ),
            request = null,
            hnsAbilityDecisions = listOf(relevant, unknown),
            ignoredMechanics = listOf(com.dualdex.calculator.IgnoredCalcMechanic.Ability(relevant))
        )
        val outcome = HnsCensusDisplayClassifier.classify(verdict)

        assertEquals(HnsCensusResultTier.REFUSED, outcome.tier)
        assertTrue("the independent move limitation must refuse", outcome.blockers.any {
            it.limitation == CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED
        })
        assertTrue("UNKNOWN Super Luck must remain a blocker", outcome.blockers.any {
            it.limitation == CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED &&
                it.side == "defender" && it.identity == "Super Luck"
        })
        assertFalse("the caveated Huge Power must not be listed as a blocker", outcome.blockers.any {
            it.limitation == CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED &&
                it.side == "attacker" && it.identity == "Huge Power"
        })
        assertTrue("the caveat evidence must survive the unrelated refusal", outcome.ignoredMechanics.any {
            it.side == "attacker" && it.identity == "Huge Power"
        })
        assertEquals(
            HnsAbilityTrialDisposition.CAVEATED,
            outcome.abilityTrialDisposition("attacker", "Huge Power")
        )
        assertEquals(
            HnsAbilityTrialDisposition.REFUSED,
            outcome.abilityTrialDisposition("defender", "Super Luck")
        )
        assertEquals(
            HnsAbilityTrialDisposition.CLEAR,
            outcome.abilityTrialDisposition("attacker", "Flash Fire")
        )
    }

    @Test
    fun `a non blocking limitation never refuses and never becomes a blocker`() {
        val outcome = HnsCensusDisplayClassifier.classify(
            verdict(CalcSupport.ESTIMATED, listOf(CalcLimitation.ROM_NOT_EXACT_VERIFIED))
        )
        assertTrue(outcome.blockers.isEmpty())
    }

    @Test
    fun `report blocker ranks use request blockers for unknown soft causes and omit caveats`() {
        val base = artifacts().run
        val template = base.requests.first()
        val abilityCode = CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED
        val itemCode = CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED
        val fieldCode = CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED
        val moveCode = CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED

        val hardAndUnknownSoft = HnsCensusOutcome(
            tier = HnsCensusResultTier.REFUSED,
            support = CalcSupport.UNSUPPORTED,
            limitations = listOf(moveCode, abilityCode, itemCode, fieldCode),
            blockers = listOf(
                HnsCensusBlocker(moveCode.name, moveCode),
                HnsCensusBlocker(abilityCode.name, abilityCode, side = "attacker", identity = "Unknown Ability"),
                HnsCensusBlocker(itemCode.name, itemCode, side = "defender", identity = "Focus Sash"),
                HnsCensusBlocker(fieldCode.name, fieldCode, identity = "Unknown field bits 0x00002000")
            ),
            abilityDecisions = emptyList(),
            itemDecisions = listOf(
                com.dualdex.calculator.HnsItemRequestDecision(
                    itemId = 481,
                    itemName = "Focus Sash",
                    side = com.dualdex.calculator.HnsItemSide.DEFENDER,
                    globalCategory = com.dualdex.pokemon.hns.HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
                    relevance = com.dualdex.calculator.HnsItemRequestRelevance.UNKNOWN,
                    rationale = "item relevance is unknown"
                ),
                com.dualdex.calculator.HnsItemRequestDecision(
                    itemId = 425,
                    itemName = "Silk Scarf",
                    side = com.dualdex.calculator.HnsItemSide.ATTACKER,
                    globalCategory = com.dualdex.pokemon.hns.HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
                    relevance = com.dualdex.calculator.HnsItemRequestRelevance.RELEVANT,
                    rationale = "complete evidence supports a named caveat"
                )
            ),
            fieldDecisions = emptyList()
        )
        val caveatedSoft = HnsCensusOutcome(
            tier = HnsCensusResultTier.CAVEATED_ESTIMATE,
            support = CalcSupport.ESTIMATED,
            limitations = listOf(abilityCode, itemCode, fieldCode),
            blockers = emptyList(),
            abilityDecisions = emptyList(),
            itemDecisions = listOf(
                com.dualdex.calculator.HnsItemRequestDecision(
                    itemId = 425,
                    itemName = "Silk Scarf",
                    side = com.dualdex.calculator.HnsItemSide.ATTACKER,
                    globalCategory = com.dualdex.pokemon.hns.HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT,
                    relevance = com.dualdex.calculator.HnsItemRequestRelevance.RELEVANT,
                    rationale = "complete evidence supports a named caveat"
                )
            ),
            fieldDecisions = emptyList(),
            ignoredMechanics = listOf(
                HnsCensusBlocker("ignored_item", itemCode, "attacker", "Silk Scarf", "RELEVANT")
            )
        )
        val run = base.copy(
            // Keep this focused report fixture small; the assertion concerns the two request
            // rows below, not the large ability-trial table emitted by the real census.
            trainers = emptyList(),
            referenceLeads = emptyList(),
            requests = listOf(
                template.copy(key = "regression-hard", trainerKey = "regression-hard",
                    outcome = hardAndUnknownSoft),
                template.copy(key = "regression-caveated", trainerKey = "regression-caveated",
                    outcome = caveatedSoft)
            ),
            excludedMoves = emptyList(),
            abilityDomain = emptyList(),
            abilityCohorts = emptyList(),
            abilityTrialExcludedAmbiguous = 0 to 0,
            abilityTrials = emptyList()
        )

        val report = org.json.JSONObject(HnsCalcCensusReport.toJson(run))
        val limitationCounts = report.getJSONArray("limitations").let { rows ->
            (0 until rows.length()).associate {
                val row = rows.getJSONObject(it)
                row.getString("limitation") to row.getInt("requests")
            }
        }
        assertEquals(1, limitationCounts[abilityCode.name])
        assertEquals(1, limitationCounts[itemCode.name])
        assertEquals(1, limitationCounts[fieldCode.name])
        assertEquals(1, limitationCounts[moveCode.name])

        val itemRanks = report.getJSONArray("itemBlockers")
        assertEquals(1, itemRanks.length())
        assertEquals("Focus Sash", itemRanks.getJSONObject(0).getString("item"))
        assertEquals("defender", itemRanks.getJSONObject(0).getString("side"))
        assertEquals(1, itemRanks.getJSONObject(0).getInt("requests"))
    }

    @Test
    fun `the census derives caveated estimates from production verdicts`() {
        assertEquals(3, HnsCensusResultTier.entries.size)
        assertEquals("CAVEATED_ESTIMATE", HnsCensusResultTier.CAVEATED_ESTIMATE.wireName)
        val run = artifacts().run
        val json = HnsCalcCensusReport.toJson(run)
        assertTrue(json.contains("\"CAVEATED_ESTIMATE\""))
        val caveated = run.requests.filter { it.outcome.tier == HnsCensusResultTier.CAVEATED_ESTIMATE }
        assertTrue("production policy should create real caveated estimates", caveated.isNotEmpty())
        assertTrue(caveated.all { it.outcome.ignoredMechanics.isNotEmpty() && it.outcome.blockers.isEmpty() })
        assertFalse(run.requests.any {
            it.outcome.tier == HnsCensusResultTier.FULLY_MODELLED && it.outcome.ignoredMechanics.isNotEmpty()
        })
        assertTrue(run.requests.filter { it.outcome.tier == HnsCensusResultTier.REFUSED }
            .all { it.outcome.blockers.isNotEmpty() })
    }

    // ---------------------------------------------------------------------------- the census

    @Test
    fun `the census enumerates every trainer battle and Pokemon of the pinned source`() {
        val run = artifacts().run
        assertEquals(651, run.trainers.size)
        assertEquals(1832, run.trainers.sumOf { it.party.size })
        assertEquals(642, run.trainers.count { it.isSingles })
        assertEquals(9, run.trainers.count { !it.isSingles })
        // The committed inventory's own sanity floors.
        val inventory = org.json.JSONObject(
            File(root, HnsCalcCensusGenerator.INVENTORY_RELATIVE_PATH).readText()
        )
        assertEquals(651, inventory.getInt("trainerBattles"))
        assertEquals(1832, inventory.getInt("trainerPokemon"))
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
    fun `only non damaging moves are excluded from the denominator`() {
        val run = artifacts().run
        assertTrue(run.excludedMoves.isNotEmpty())
        for (excluded in run.excludedMoves) {
            assertEquals(
                "the only exclusion reason is a move with no base power",
                "NON_DAMAGING_MOVE", excluded.reason
            )
            assertEquals(0, excluded.movePower)
            // The denominator and the exclusion list partition the pinned movesets exactly.
            assertFalse(
                "excluded move ${excluded.move} was also evaluated for ${excluded.trainerKey}",
                run.requests.any {
                    it.trainerKey == excluded.trainerKey &&
                        it.trainerSlot == excluded.trainerSlot &&
                        it.move == excluded.move
                }
            )
        }
        for (request in run.requests) {
            assertTrue("evaluated a status move: ${request.move}", request.movePower > 0)
        }
    }

    @Test
    fun `moves outside the ordinary subset are evaluated and refused, not excluded`() {
        // A move whose damage shape is outside the source-proven ordinary subset still deals
        // damage, so the production policy has a real verdict for it. Dropping it from the
        // denominator would hide a real refusal and overstate coverage.
        val run = artifacts().run
        val refused = run.requests.filter {
            it.outcome.limitations.contains(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED)
        }
        assertTrue("expected some move-mechanics refusals, found none", refused.isNotEmpty())
        for (request in refused) {
            assertEquals(HnsCensusResultTier.REFUSED, request.outcome.tier)
            assertTrue("a refused request must have a cause", request.outcome.blockers.isNotEmpty())
        }
        // And the specific moves the audit refuses are in the evaluated set, not the excluded one.
        val evaluatedMoves = run.requests.map { it.move }.toSet()
        val excludedMoves = run.excludedMoves.map { it.move }.toSet()
        assertTrue(
            "the ordinary-subset refusal must not be an exclusion",
            evaluatedMoves.isNotEmpty() && excludedMoves.intersect(evaluatedMoves).isEmpty()
        )
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
    fun `a globally harmless ability can never be blamed for a block`() {
        // `CalcCapabilityPolicy` only produces an ability decision for an ability the shipped
        // audit cannot prove harmless. A trial whose tested ability produced no decision must
        // therefore never be credited with a block, however the request-wide limitation stands.
        val run = artifacts().run
        val harmlessIds = run.abilityDomain.map { it.first }.filter { !HnsCalcCensusEngine.mayBlockAbility(it) }
        assertTrue("expected some provably harmless abilities", harmlessIds.isNotEmpty())
        for (trial in run.abilityTrials.filter { it.abilityId in harmlessIds }) {
            assertFalse(
                "harmless ability ${trial.abilityName} was credited with a block",
                trial.refusedByAbility
            )
            assertEquals(0, trial.refusedRequests)
            assertEquals(0, trial.refusedBattles)
        }
        // The complement is the only attributing set, and the published headline counts it.
        val attributing = run.abilityTrials.filter { HnsCalcCensusEngine.mayBlockAbility(it.abilityId) }
        assertTrue(attributing.any { it.refusedByAbility })
    }

    @Test
    fun `ability trials disclose the requests they had to set aside`() {
        val run = artifacts().run
        val (attackerSide, defenderSide) = run.abilityTrialExcludedAmbiguous
        assertTrue("expected some ambiguous attacker-side requests", attackerSide > 0)
        assertTrue("expected some ambiguous defender-side requests", defenderSide > 0)
        assertTrue("an exclusion can never exceed the census", attackerSide <= run.requests.size)
        assertTrue("an exclusion can never exceed the census", defenderSide <= run.requests.size)
        // A cohort is ambiguous for a side exactly when the opposite battler's ability cannot be
        // proven harmless, so the disclosed counts must match that rule.
        val expectedAttackerSide = run.abilityCohorts
            .filter { HnsCalcCensusEngine.mayBlockAbility(it.key.defenderAbilityId) }
            .sumOf { it.requestCount }
        val expectedDefenderSide = run.abilityCohorts
            .filter { HnsCalcCensusEngine.mayBlockAbility(it.key.attackerAbilityId) }
            .sumOf { it.requestCount }
        assertEquals(expectedAttackerSide, attackerSide)
        assertEquals(expectedDefenderSide, defenderSide)
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
        // Huge Power multiplies Attack, so it is relevant for the attacker's PHYSICAL move and
        // provably irrelevant for the SPECIAL one, and irrelevant on the defender in both.
        val hugePower = run.abilityTrials.filter { it.abilityId == 37 }
        assertEquals(4, hugePower.size)
        val attackerPhysical = hugePower.single { it.side == "attacker" && it.category == "Physical" }
        val attackerSpecial = hugePower.single { it.side == "attacker" && it.category == "Special" }
        assertEquals("RELEVANT", attackerPhysical.abilityRelevance)
        assertTrue("Huge Power must be counted as caveated on a physical attack", attackerPhysical.caveatedByAbility)
        assertTrue(attackerPhysical.caveatedRequests > 0)
        val eligiblePhysicalRequests = run.abilityCohorts
            .filter { it.key.moveCategory == "Physical" && !HnsCalcCensusEngine.mayBlockAbility(it.key.defenderAbilityId) }
            .sumOf { it.requestCount }
        assertEquals(
            "every eligible Huge Power trial must be classified from its request outcome",
            eligiblePhysicalRequests,
            attackerPhysical.refusedRequests + attackerPhysical.caveatedRequests + attackerPhysical.clearRequests
        )
        assertTrue(
            "the complete-evidence contexts must be caveated rather than counted as refusals",
            attackerPhysical.caveatedRequests > attackerPhysical.refusedRequests
        )
        assertEquals(0, attackerPhysical.clearedRequests)
        assertTrue(
            "Huge Power must be provably irrelevant for an attacker's special move somewhere",
            attackerSpecial.clearedRequests > 0
        )
        assertTrue("proven-irrelevant Huge Power trials must be clear", attackerSpecial.clearRequests > 0)
        for (row in hugePower.filter { it.side == "defender" }) {
            assertEquals("PROVEN_IRRELEVANT", row.abilityRelevance)
            assertFalse("Huge Power must not refuse as a defender", row.refusedByAbility)
            assertEquals(0, row.refusedRequests)
        }
        // Battle Armor: the mirror case, relevant only as a defender.
        val battleArmor = run.abilityTrials.filter { it.abilityId == 4 }
        assertEquals(4, battleArmor.size)
        for (row in battleArmor.filter { it.side == "attacker" }) {
            assertEquals("PROVEN_IRRELEVANT", row.abilityRelevance)
            assertFalse("Battle Armor must not refuse as an attacker", row.refusedByAbility)
        }
        assertTrue("Battle Armor UNKNOWN evidence must refuse as a defender", battleArmor
            .filter { it.side == "defender" }.all { it.refusedByAbility })

        val superLuckId = run.abilityDomain.single { it.second == "Super Luck" }.first
        val superLuckTrials = run.abilityTrials.filter { it.abilityId == superLuckId }
        assertTrue(superLuckTrials.isNotEmpty())
        assertTrue("UNKNOWN Super Luck contexts must be refused", superLuckTrials
            .filter { it.abilityRelevance == "UNKNOWN" }
            .all { it.refusedRequests > 0 && it.caveatedRequests == 0 })
    }

    @Test
    fun `contextual ability results survive into the census output`() {
        val run = artifacts().run
        val json = HnsCalcCensusReport.toJson(run)
        assertTrue("the three-valued relevance must be published", json.contains("\"relevance\""))
        assertTrue(json.contains("PROVEN_IRRELEVANT"))
        assertTrue(json.contains("RELEVANT"))
        assertTrue(json.contains("UNKNOWN"))
        val root = org.json.JSONObject(json)
        assertEquals(2, root.getInt("schemaVersion"))
        assertTrue(root.has("abilityRefusals"))
        assertTrue(root.has("abilityCaveats"))
        assertFalse("the old blocked boolean must not survive as a stale proxy", json.contains("\"blocked\""))
        val hugePower = run.abilityTrials.single {
            it.abilityId == 37 && it.side == "attacker" && it.category == "Physical"
        }
        val refusalRows = root.getJSONArray("abilityRefusals")
        val refusal = (0 until refusalRows.length()).map { refusalRows.getJSONObject(it) }
            .single { it.getInt("abilityId") == 37 && it.getString("side") == "attacker" &&
                it.getString("category") == "Physical" }
        assertEquals(hugePower.refusedRequests, refusal.getInt("refusedRequests"))
        assertEquals(hugePower.refusedBattles, refusal.getInt("refusedBattles"))
        val caveatRows = root.getJSONArray("abilityCaveats")
        val caveat = (0 until caveatRows.length()).map { caveatRows.getJSONObject(it) }
            .single { it.getInt("abilityId") == 37 && it.getString("side") == "attacker" &&
                it.getString("category") == "Physical" }
        assertEquals(hugePower.caveatedRequests, caveat.getInt("caveatedRequests"))
        assertEquals(hugePower.caveatedBattles, caveat.getInt("caveatedBattles"))
        val trialRows = root.getJSONArray("abilityTrials")
        val trial = (0 until trialRows.length()).map { trialRows.getJSONObject(it) }
            .single { it.getInt("abilityId") == 37 && it.getString("side") == "attacker" &&
                it.getString("category") == "Physical" }
        assertEquals(hugePower.clearRequests, trial.getInt("clearRequests"))
        assertEquals(hugePower.refusedRequests, trial.getInt("refusedRequests"))
        assertEquals(hugePower.caveatedRequests, trial.getInt("caveatedRequests"))
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
        data class Fingerprint(
            val jsonLength: Int,
            val jsonSha256: String,
            val markdownLength: Int,
            val markdownSha256: String,
            val gzipSha256: String
        )

        fun fingerprint(): Fingerprint {
            // Deliberately use the UNCACHED derivation: this is the property that matters. Keep
            // only compact fingerprints after each pass so a second 20 MB census run and JSON
            // string are not retained alongside the first one in the unit-test heap.
            val artifacts = HnsCalcCensusGenerator.derive(root)
            fun sha256(bytes: ByteArray): String = java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
            return Fingerprint(
                jsonLength = artifacts.json.length,
                jsonSha256 = sha256(artifacts.json.toByteArray(Charsets.UTF_8)),
                markdownLength = artifacts.markdown.length,
                markdownSha256 = sha256(artifacts.markdown.toByteArray(Charsets.UTF_8)),
                gzipSha256 = sha256(HnsCalcCensusGenerator.gzipDeterministic(artifacts.json))
            )
        }

        assertEquals(fingerprint(), fingerprint())
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
                .writeText(artifacts.markdown + "\nstale extra line\n")
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
