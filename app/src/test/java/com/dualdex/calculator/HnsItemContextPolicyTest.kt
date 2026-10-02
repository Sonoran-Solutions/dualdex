package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsItemAuditData
import com.dualdex.pokemon.hns.HnsItemCategory
import com.dualdex.pokemon.hns.HnsItemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request-local H&S item relevance. For every contextual family: an irrelevant case, a relevant
 * case, and a missing-authority case. UNKNOWN never clears.
 */
class HnsItemContextPolicyTest {

    // Pinned H&S item IDs (Hns205ItemCatalogue).
    private val charcoal = 426
    private val silkScarf = 425
    private val fireGem = 340
    private val flamePlate = 250
    private val choiceBand = 442
    private val choiceSpecs = 443
    private val muscleBand = 475
    private val wiseGlasses = 476
    private val lifeOrb = 479
    private val expertBelt = 477
    private val scopeLens = 471
    private val leek = 393
    private val luckyPunch = 395
    private val thickClub = 394
    private val blunderPolicy = 511
    private val roomService = 512
    private val boosterEnergy = 764
    private val metronome = 483
    private val lightBall = HnsItemRegistry.resolveIdByName("Light Ball")!!
    private val deepSeaScale = HnsItemRegistry.resolveIdByName("Deep Sea Scale")!!
    private val punchingGlove = HnsItemRegistry.resolveIdByName("Punching Glove")!!
    private val terrainSeed = 451
    private val assaultVest = 503
    private val eviolite = 494
    private val metalPowder = 396
    private val occaBerry = 550
    private val focusSash = 481
    private val leftovers = 472
    private val sitrusBerry = 523
    private val rockyHelmet = 496
    private val choiceScarf = 444
    private val quickClaw = 462
    private val airBalloon = 497
    private val ironBall = 484
    private val floatStone = 495
    private val umbrella = 513
    private val redOrb = 290

    private fun ctx(
        side: HnsItemSide,
        ordinaryMove: Boolean? = true,
        moveType: PokemonType? = PokemonType.NORMAL,
        moveCategory: MoveCategory? = MoveCategory.PHYSICAL,
        fieldStatuses: Int? = 0,
        weatherWord: Int? = 0,
        attackerAbilityId: Int? = 0,
        defenderHp: Int? = 10,
        defenderMaxHp: Int? = 20,
        defenderAbilityId: Int? = null,
        attackerGastroAcid: Boolean? = null,
        defenderGastroAcid: Boolean? = null,
        observedBattlersCount: Int? = null,
        attackerTerrainApplicability: HnsTerrainApplicability? = null,
        defenderTerrainApplicability: HnsTerrainApplicability? = null,
        attackerSpeciesId: Int? = null,
        defenderSpeciesId: Int? = null,
        attackerBaseSpeciesId: Int? = null,
        defenderBaseSpeciesId: Int? = null,
        attackerTransformed: Boolean? = false,
        defenderCanEvolve: Boolean? = null,
        defenderTransformed: Boolean? = false,
        attackerMetronomeItemCounter: Int? = 0,
        attackerBoosterEnergyActivated: Boolean? = false,
        attackerParadoxBoostedStat: Int? = 0,
        defenderBoosterEnergyActivated: Boolean? = false,
        defenderParadoxBoostedStat: Int? = 0,
        attackerSelectedGimmick: Int? = 0,
        attackerActiveGimmick: Int? = 0,
        switchInEventsSettled: Boolean? = true,
        moveUsesDefenseStat: Boolean? = moveCategory?.let { it == MoveCategory.PHYSICAL },
        punchingMove: Boolean? = null
    ) = HnsItemContextPolicy.Context(
        side = side,
        ordinaryMove = ordinaryMove,
        moveType = moveType,
        moveCategory = moveCategory,
        fieldState = fieldStatuses?.let(HnsFieldState::decode),
        weatherWord = weatherWord,
        attackerAbilityId = attackerAbilityId,
        defenderHp = defenderHp,
        defenderMaxHp = defenderMaxHp,
        defenderAbilityId = defenderAbilityId,
        attackerGastroAcid = attackerGastroAcid,
        defenderGastroAcid = defenderGastroAcid,
        observedBattlersCount = observedBattlersCount,
        attackerTerrainApplicability = attackerTerrainApplicability,
        defenderTerrainApplicability = defenderTerrainApplicability,
        attackerSpeciesId = attackerSpeciesId,
        defenderSpeciesId = defenderSpeciesId,
        attackerBaseSpeciesId = attackerBaseSpeciesId,
        defenderBaseSpeciesId = defenderBaseSpeciesId,
        attackerTransformed = attackerTransformed,
        defenderCanEvolve = defenderCanEvolve,
        defenderTransformed = defenderTransformed,
        attackerMetronomeItemCounter = attackerMetronomeItemCounter,
        attackerBoosterEnergyActivated = attackerBoosterEnergyActivated,
        attackerParadoxBoostedStat = attackerParadoxBoostedStat,
        defenderBoosterEnergyActivated = defenderBoosterEnergyActivated,
        defenderParadoxBoostedStat = defenderParadoxBoostedStat,
        attackerSelectedGimmick = attackerSelectedGimmick,
        attackerActiveGimmick = attackerActiveGimmick,
        moveUsesDefenseStat = moveUsesDefenseStat,
        punchingMove = punchingMove,
        switchInEventsSettled = switchInEventsSettled
    )

    private fun assessForTest(id: Int, context: HnsItemContextPolicy.Context?): HnsItemRequestDecision {
        val item = HnsItemRegistry.classify(id).data
        val authoritative = context?.let { c ->
            if (c.holdEffectResolution != null) c else c.copy(
                holdEffectResolution = HnsHoldEffectResolution(
                    id, item, HnsHoldEffectState.ACTIVE_EXACT, item?.holdEffect,
                    "Unit fixture supplies an exact, unsuppressed live item observation."
                ),
                resistBerryDecision = c.resistBerryDecision ?: if (item?.holdEffect == "HOLD_EFFECT_RESIST_BERRY") {
                    val berryType = HnsItemRegistry.itemTypeName(id)?.let(PokemonType::fromString)
                    when {
                        berryType == null || c.moveType == null -> HnsResistBerryDecision(
                            HnsResistBerryState.UNKNOWN, id, null, "resist_berry_authority_unobserved",
                            "The test fixture omitted the berry or move type operand."
                        )
                        berryType != c.moveType -> HnsResistBerryDecision(
                            HnsResistBerryState.NOT_APPLICABLE, id, 4096, "resist_berry_other_type",
                            "The generated berry type differs from the effective move type."
                        )
                        else -> HnsResistBerryDecision(
                            HnsResistBerryState.APPLIES, id,
                            if (c.defenderAbilityId == 247) 1024 else 2048,
                            if (c.defenderAbilityId == 247) "resist_berry_ripen_quarter" else "resist_berry_half",
                            "The unit fixture supplies an exact matching berry activation."
                        )
                    }
                } else c.resistBerryDecision
            )
        }
        return HnsItemContextPolicy.assess(id, authoritative)
    }

    private fun relevance(id: Int, context: HnsItemContextPolicy.Context?) =
        assessForTest(id, context).relevance

    private val irrelevant = HnsItemRequestRelevance.PROVEN_IRRELEVANT
    private val relevant = HnsItemRequestRelevance.RELEVANT
    private val unknown = HnsItemRequestRelevance.UNKNOWN
    private val modelled = HnsItemRequestRelevance.MODELLED

    @Test
    fun `attacker-only items are irrelevant on the defender whatever the move`() {
        for (id in listOf(charcoal, fireGem, flamePlate, choiceBand, choiceSpecs, muscleBand, wiseGlasses,
            lifeOrb, expertBelt, scopeLens)) {
            val decision = assessForTest(id, ctx(HnsItemSide.DEFENDER, ordinaryMove = null, moveType = null))
            assertEquals("item $id", irrelevant, decision.relevance)
            assertEquals("defender_holds_attacker_only_item", decision.rule)
        }
    }

    @Test
    fun `category items clear only for the opposite category and stay unknown without authority`() {
        assertEquals(irrelevant, relevance(choiceBand, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.SPECIAL)))
        assertEquals(modelled, relevance(choiceBand, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(unknown, relevance(choiceBand, ctx(HnsItemSide.ATTACKER, moveCategory = null)))
        assertEquals(irrelevant, relevance(muscleBand, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.SPECIAL)))

        assertEquals(irrelevant, relevance(choiceSpecs, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(modelled, relevance(choiceSpecs, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.SPECIAL)))
        assertEquals(unknown, relevance(choiceSpecs, ctx(HnsItemSide.ATTACKER, moveCategory = null)))

        assertEquals(irrelevant, relevance(wiseGlasses, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(modelled, relevance(wiseGlasses, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.SPECIAL)))
        assertEquals(unknown, relevance(wiseGlasses, ctx(HnsItemSide.ATTACKER, moveCategory = null)))

        val specialDecision = assessForTest(wiseGlasses, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.SPECIAL))
        assertEquals("wise_glasses_special_move", specialDecision.rule)
        assertEquals(modelled, specialDecision.relevance)
        assertEquals(HnsItemCategory.MODELLED_HNS_SPECIFIC, specialDecision.globalCategory)
    }

    @Test
    fun `Thick Club follows the pinned Alolan Marowak base species predicate`() {
        assertEquals(modelled, relevance(thickClub, ctx(
            HnsItemSide.ATTACKER,
            moveCategory = MoveCategory.PHYSICAL,
            attackerSpeciesId = 973,
            attackerBaseSpeciesId = 973
        )))
        assertEquals(irrelevant, relevance(thickClub, ctx(
            HnsItemSide.ATTACKER,
            moveCategory = MoveCategory.PHYSICAL,
            attackerSpeciesId = 143,
            attackerBaseSpeciesId = 143
        )))
        assertEquals(unknown, relevance(thickClub, ctx(
            HnsItemSide.ATTACKER,
            moveCategory = MoveCategory.PHYSICAL,
            attackerSpeciesId = 973,
            attackerBaseSpeciesId = null
        )))
    }

    @Test
    fun `type boosters gems and plates compare the pinned item type with the effective move type`() {
        for (id in listOf(charcoal, fireGem, flamePlate)) {
            assertEquals("item $id", irrelevant, relevance(id, ctx(HnsItemSide.ATTACKER, moveType = PokemonType.WATER)))
            assertEquals("item $id", modelled, relevance(id, ctx(HnsItemSide.ATTACKER, moveType = PokemonType.FIRE)))
            assertEquals("item $id", unknown, relevance(id, ctx(HnsItemSide.ATTACKER, moveType = null)))
        }
        assertEquals(modelled, relevance(silkScarf, ctx(HnsItemSide.ATTACKER, moveType = PokemonType.NORMAL)))
    }

    @Test
    fun `attacker final modifiers remain relevant while crit stage items clear fixed hit`() {
        for (id in listOf(lifeOrb, expertBelt)) {
            assertEquals("item $id", modelled, relevance(id, ctx(HnsItemSide.ATTACKER)))
        }
        for (id in listOf(scopeLens, leek, luckyPunch)) {
            assertEquals("item $id", irrelevant, relevance(id, ctx(HnsItemSide.ATTACKER)))
            assertEquals("item $id", unknown, relevance(id, ctx(HnsItemSide.ATTACKER, ordinaryMove = false)))
        }
    }

    @Test
    fun `defender-only items are irrelevant on the attacker`() {
        for (id in listOf(assaultVest, eviolite, metalPowder, occaBerry, focusSash)) {
            val decision = assessForTest(id, ctx(HnsItemSide.ATTACKER, ordinaryMove = null, moveType = null))
            assertEquals("item $id", irrelevant, decision.relevance)
            assertEquals("attacker_holds_defender_only_item", decision.rule)
        }
    }

    @Test
    fun `Assault Vest and Metal Powder need the category and an observed inactive Wonder Room`() {
        assertEquals(irrelevant, relevance(assaultVest, ctx(HnsItemSide.DEFENDER, moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(modelled, relevance(assaultVest, ctx(HnsItemSide.DEFENDER, moveCategory = MoveCategory.SPECIAL)))
        // Under observed Wonder Room, a physical hit selects Sp. Def and Assault Vest applies exactly.
        assertEquals(modelled, relevance(assaultVest, ctx(HnsItemSide.DEFENDER, fieldStatuses = 4)))
        assertEquals(unknown, relevance(assaultVest, ctx(HnsItemSide.DEFENDER, fieldStatuses = null)))
        assertEquals(unknown, relevance(assaultVest, ctx(HnsItemSide.DEFENDER, fieldStatuses = 1 shl 12)))
        assertEquals(unknown, relevance(metalPowder, ctx(HnsItemSide.DEFENDER, moveCategory = MoveCategory.SPECIAL,
            fieldStatuses = 4)))
        // Any other field bit (a terrain, Trick Room, Gravity) leaves usesDefStat unchanged.
        for (unrelated in listOf(1 shl 1, 1 shl 5, 1 shl 8, (1 shl 6) or (1 shl 11))) {
            assertEquals(irrelevant, relevance(assaultVest, ctx(HnsItemSide.DEFENDER,
                moveCategory = MoveCategory.PHYSICAL, fieldStatuses = unrelated)))
            assertEquals(irrelevant, relevance(metalPowder, ctx(HnsItemSide.DEFENDER,
                moveCategory = MoveCategory.SPECIAL, fieldStatuses = unrelated)))
        }

        assertEquals(irrelevant, relevance(metalPowder, ctx(HnsItemSide.DEFENDER, moveCategory = MoveCategory.SPECIAL)))
        assertEquals(unknown, relevance(metalPowder, ctx(HnsItemSide.DEFENDER, moveCategory = MoveCategory.PHYSICAL)))
        assertEquals(unknown, relevance(eviolite, ctx(HnsItemSide.DEFENDER)))
    }

    @Test
    fun `resist berries clear only for a different effective type`() {
        assertEquals(irrelevant, relevance(occaBerry, ctx(HnsItemSide.DEFENDER, moveType = PokemonType.WATER)))
        assertEquals(modelled, relevance(occaBerry, ctx(HnsItemSide.DEFENDER, moveType = PokemonType.FIRE)))
        assertEquals(unknown, relevance(occaBerry, ctx(HnsItemSide.DEFENDER, moveType = null)))
    }

    @Test
    fun `Focus Sash clears only when the live defender is below max HP`() {
        assertEquals(irrelevant, relevance(focusSash, ctx(HnsItemSide.DEFENDER, defenderHp = 19, defenderMaxHp = 20)))
        assertEquals(relevant, relevance(focusSash, ctx(HnsItemSide.DEFENDER, defenderHp = 20, defenderMaxHp = 20)))
        assertEquals(unknown, relevance(focusSash, ctx(HnsItemSide.DEFENDER, defenderHp = null)))
        assertEquals(unknown, relevance(focusSash, ctx(HnsItemSide.DEFENDER, defenderHp = 21, defenderMaxHp = 20)))
    }

    @Test
    fun `post-hit and residual items clear only for a known ordinary single-hit move`() {
        for (id in listOf(leftovers, sitrusBerry, rockyHelmet)) {
            for (side in HnsItemSide.values()) {
                assertEquals("item $id $side", irrelevant, relevance(id, ctx(side)))
                assertEquals("item $id $side", unknown, relevance(id, ctx(side, ordinaryMove = false)))
                assertEquals("item $id $side", unknown, relevance(id, ctx(side, ordinaryMove = null)))
            }
        }
        assertEquals(irrelevant, relevance(boosterEnergy, ctx(HnsItemSide.ATTACKER)))
        assertEquals(unknown, relevance(terrainSeed, ctx(HnsItemSide.DEFENDER)))
        assertEquals(unknown, relevance(758, ctx(HnsItemSide.DEFENDER))) // Ability Shield preserves ability.
    }

    @Test
    fun `Blunder Policy and Room Service retain only the Analytic speed dependency`() {
        for (id in listOf(511, 512)) {
            val ordinary = assessForTest(id, ctx(
                HnsItemSide.ATTACKER,
                attackerAbilityId = 0
            ))
            assertEquals("item $id", irrelevant, ordinary.relevance)
            assertEquals("post_hit_speed_item_ordinary_move", ordinary.rule)

            val analytic = assessForTest(id, ctx(
                HnsItemSide.ATTACKER,
                attackerAbilityId = 148
            ))
            assertEquals("item $id", relevant, analytic.relevance)
            assertEquals("post_hit_speed_item_attacker_analytic", analytic.rule)

            assertEquals(unknown, relevance(id, ctx(
                HnsItemSide.ATTACKER,
                ordinaryMove = null,
                attackerAbilityId = 0
            )))
        }
    }

    @Test
    fun `held Terrain Seed and Berserk Gene never clear from stage snapshots`() {
        val matchingTerrainAndStages = ctx(
            HnsItemSide.ATTACKER,
            fieldStatuses = com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_ELECTRIC_TERRAIN,
            observedBattlersCount = 2
        )
        val seed = assessForTest(terrainSeed, matchingTerrainAndStages)
        assertEquals(unknown, seed.relevance)
        assertEquals(null, seed.rule)

        val gene = assessForTest(798, matchingTerrainAndStages)
        assertEquals(unknown, gene.relevance)
        assertEquals(null, gene.rule)
    }

    @Test
    fun `Booster Energy follows the live Paradox payload and switch-in settlement`() {
        val decision = assessForTest(boosterEnergy, ctx(
            HnsItemSide.ATTACKER,
            observedBattlersCount = 2
        ))
        assertEquals(irrelevant, decision.relevance)
        assertEquals("booster_energy_non_paradox_ability", decision.rule)

        val activeButContradictory = ctx(HnsItemSide.ATTACKER, attackerAbilityId = 281,
            attackerBoosterEnergyActivated = true, attackerParadoxBoostedStat = 1)
        assertEquals(unknown, relevance(boosterEnergy, activeButContradictory))
        assertEquals(unknown, relevance(boosterEnergy, activeButContradictory.copy(switchInEventsSettled = false)))
        assertEquals(unknown, relevance(boosterEnergy, activeButContradictory.copy(attackerBoosterEnergyActivated = null)))
        assertEquals(modelled, relevance(boosterEnergy, ctx(HnsItemSide.DEFENDER,
            defenderAbilityId = 282, defenderBoosterEnergyActivated = false, defenderParadoxBoostedStat = 0)))

        // Natural Sun can leave Booster Energy held; the existing live ability payload still decides damage.
        val heldNaturalWeather = activeButContradictory.copy(
            attackerBoosterEnergyActivated = false, attackerParadoxBoostedStat = 0
        )
        assertEquals(modelled, relevance(boosterEnergy, heldNaturalWeather))
        assertEquals("booster_energy_payload_modelled", assessForTest(boosterEnergy, heldNaturalWeather).rule)
        assertEquals(unknown, relevance(boosterEnergy, heldNaturalWeather.copy(attackerTransformed = null)))
    }

    @Test
    fun `Ability Shield clears only when suppression cannot affect this hit`() {
        val clearContext = ctx(HnsItemSide.DEFENDER, attackerAbilityId = 0, defenderAbilityId = 0,
            attackerGastroAcid = false, defenderGastroAcid = false, observedBattlersCount = 2)
        assertEquals(irrelevant, relevance(758, clearContext))
        assertEquals(irrelevant, relevance(758, clearContext.copy(side = HnsItemSide.ATTACKER)))

        assertEquals(relevant, relevance(758, clearContext.copy(attackerAbilityId = 256)))
        assertEquals(relevant, relevance(758, clearContext.copy(defenderGastroAcid = true)))
        assertEquals(relevant, relevance(758, clearContext.copy(side = HnsItemSide.ATTACKER,
            attackerGastroAcid = true)))
        assertEquals(HnsItemRequestRelevance.MODELLED, relevance(758, clearContext.copy(attackerAbilityId = 104)))
        assertEquals(HnsItemRequestRelevance.MODELLED,
            relevance(758, clearContext.copy(moveIgnoresTargetAbility = true)))
        assertEquals(unknown, relevance(758, ctx(HnsItemSide.DEFENDER, attackerAbilityId = 0,
            defenderAbilityId = 0, attackerGastroAcid = false, defenderGastroAcid = false)))
        assertEquals(unknown, relevance(758, clearContext.copy(ordinaryMove = false)))
    }

    @Test
    fun `Ability Shield context uses only observed live suppression operands`() {
        val live = CalcHnsLiveBattleState(
            observedBattlersCount = 2,
            attackerPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true),
            defenderPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true)
        )
        val request = DamageCalculationRequest(
            attacker = CalcPokemonInput(species = "Pikachu", abilityId = 0),
            defender = CalcPokemonInput(species = "Bulbasaur", abilityId = 0),
            move = CalcMoveInput("Tackle"),
            hnsLiveBattleState = live
        )
        val context = HnsItemContextPolicy.contextForRequest(request, HnsItemSide.DEFENDER, true)
        assertEquals(irrelevant, relevance(758, context))

        val sunsteel = request.copy(move = CalcMoveInput("Sunsteel Strike"))
        val sunsteelContext = HnsItemContextPolicy.contextForRequest(sunsteel, HnsItemSide.DEFENDER, true)
        assertEquals(HnsItemRequestRelevance.MODELLED,
            relevance(758, sunsteelContext))
        assertEquals(false, context.moveIgnoresTargetAbility)
        assertEquals(true, sunsteelContext.moveIgnoresTargetAbility)

        val unobservedVolatiles = request.copy(hnsLiveBattleState = live.copy(defenderPersistentVolatiles = null))
        assertEquals(unknown, relevance(758,
            HnsItemContextPolicy.contextForRequest(unobservedVolatiles, HnsItemSide.DEFENDER, true)))
    }

    @Test
    fun `speed items need an ordinary move and a known non-Analytic attacker`() {
        for (id in listOf(choiceScarf, quickClaw, blunderPolicy, roomService)) {
            assertEquals(irrelevant, relevance(id, ctx(HnsItemSide.DEFENDER)))
            assertEquals(relevant, relevance(id, ctx(HnsItemSide.ATTACKER, attackerAbilityId = 148)))
            assertEquals(unknown, relevance(id, ctx(HnsItemSide.ATTACKER, attackerAbilityId = null)))
            assertEquals(unknown, relevance(id, ctx(HnsItemSide.ATTACKER, ordinaryMove = false)))
        }
        assertEquals(irrelevant, relevance(floatStone, ctx(HnsItemSide.DEFENDER)))
        assertEquals(unknown, relevance(floatStone, ctx(HnsItemSide.DEFENDER, ordinaryMove = false)))
    }

    @Test
    fun `grounding items use authoritative terrain applicability`() {
        assertEquals(irrelevant, relevance(airBalloon, ctx(HnsItemSide.DEFENDER, moveType = PokemonType.WATER)))
        assertEquals(modelled, relevance(airBalloon, ctx(HnsItemSide.DEFENDER, moveType = PokemonType.GROUND)))
        // A terrain reads groundedness; an unread word or an unknown bit is not assumed terrain-free.
        for (terrain in listOf(1 shl 6, 1 shl 7, 1 shl 8, 1 shl 9)) {
            assertEquals(unknown, relevance(airBalloon, ctx(HnsItemSide.DEFENDER, fieldStatuses = terrain)))
            assertEquals(unknown, relevance(airBalloon, ctx(HnsItemSide.ATTACKER, fieldStatuses = terrain)))
            assertEquals(modelled, relevance(airBalloon, ctx(HnsItemSide.DEFENDER, fieldStatuses = terrain,
                defenderTerrainApplicability = HnsTerrainApplicability.NOT_AFFECTED)))
            assertEquals(modelled, relevance(airBalloon, ctx(HnsItemSide.ATTACKER, fieldStatuses = terrain,
                attackerTerrainApplicability = HnsTerrainApplicability.NOT_AFFECTED)))
        }
        assertEquals(unknown, relevance(airBalloon, ctx(HnsItemSide.DEFENDER, fieldStatuses = null)))
        assertEquals(unknown, relevance(airBalloon, ctx(HnsItemSide.DEFENDER, fieldStatuses = 1 shl 13)))
        // Gravity or Trick Room is not a terrain: the grounding item is still irrelevant.
        assertEquals(irrelevant, relevance(airBalloon, ctx(HnsItemSide.DEFENDER, fieldStatuses = 1 shl 5)))
        assertEquals(irrelevant, relevance(airBalloon, ctx(HnsItemSide.ATTACKER, fieldStatuses = 1 shl 1)))
        assertEquals(irrelevant, relevance(airBalloon, ctx(HnsItemSide.ATTACKER, moveType = null)))
        // Iron Ball also needs the turn-order predicate.
        assertEquals(irrelevant, relevance(ironBall, ctx(HnsItemSide.DEFENDER, moveType = PokemonType.WATER)))
        assertEquals(relevant, relevance(ironBall, ctx(HnsItemSide.DEFENDER, attackerAbilityId = 148)))
    }

    @Test
    fun `Utility Umbrella needs observed clear weather`() {
        assertEquals(irrelevant, relevance(umbrella, ctx(HnsItemSide.DEFENDER, weatherWord = 0)))
        assertEquals("umbrella_clear_weather", assessForTest(umbrella,
            ctx(HnsItemSide.DEFENDER, weatherWord = 0)).rule)
        assertEquals(modelled, relevance(umbrella, ctx(HnsItemSide.DEFENDER, weatherWord = 1)))
        assertEquals(irrelevant, relevance(umbrella, ctx(HnsItemSide.DEFENDER, weatherWord = 1 shl 5)))
        assertEquals("umbrella_other_weather", assessForTest(umbrella,
            ctx(HnsItemSide.DEFENDER, weatherWord = 1 shl 5)).rule)
        assertEquals(unknown, relevance(umbrella, ctx(HnsItemSide.DEFENDER, weatherWord = null)))
    }

    @Test
    fun `Rusted Sword and Shield are never cleared by any request context`() {
        for (id in listOf(288, 289)) for (side in HnsItemSide.values()) for (type in PokemonType.values()) {
            for (category in MoveCategory.values()) for (hp in listOf(10, 20)) {
                val decision = assessForTest(id, ctx(side, true, type, category, 0, 0, 0, hp, 20))
                assertEquals("item $id $side $type $category", unknown, decision.relevance)
                assertEquals(HnsItemCategory.UNSUPPORTED_DAMAGE_RELEVANT, decision.globalCategory)
            }
        }
        assertEquals("Rusted Sword", assessForTest(288, ctx(HnsItemSide.ATTACKER)).itemName)
    }

    @Test
    fun `unclassified and supported items are never cleared by context`() {
        val red = assessForTest(redOrb, ctx(HnsItemSide.DEFENDER))
        assertEquals(HnsItemCategory.MODELLED_HNS_SPECIFIC, red.globalCategory)
        assertEquals(unknown, red.relevance)
        assertEquals("Red Orb", red.itemName)
        assertEquals(unknown, relevance(0, ctx(HnsItemSide.ATTACKER)))
        assertEquals(unknown, relevance(charcoal, null))
    }

    @Test
    fun `every reviewed rule name is produced by the policy and no unreviewed rule exists`() {
        val produced = mutableSetOf<String>()
        val types = PokemonType.values().toList() + listOf<PokemonType?>(null)
        // One representative per hold-effect family, plus every type-operand item.
        val representatives = (0..com.dualdex.pokemon.hns.Hns205ItemCatalogue.ITEM_ID_MAX)
            .groupBy { com.dualdex.pokemon.hns.Hns205ItemCatalogue.get(it)!!.holdEffect }
            .values.map { it.first() } + HnsItemAuditData.itemTypes.keys
        for (id in representatives) {
            for (side in HnsItemSide.values()) for (type in types) for (category in MoveCategory.values()) {
                for (hp in listOf(10, 20)) for (ability in listOf(0, 148)) for (weather in listOf(0, 1)) {
                    assessForTest(id, ctx(side, true, type, category, 0, weather, ability, hp, 20,
                        defenderAbilityId = 0, attackerGastroAcid = false, defenderGastroAcid = false,
                        observedBattlersCount = 2)).rule?.let(produced::add)
                }
            }
        }
        listOf(
            ctx(HnsItemSide.DEFENDER, attackerAbilityId = 256, defenderAbilityId = 0,
                attackerGastroAcid = false, defenderGastroAcid = false, observedBattlersCount = 2),
            ctx(HnsItemSide.DEFENDER, attackerAbilityId = 104, defenderAbilityId = 0,
                attackerGastroAcid = false, defenderGastroAcid = false, observedBattlersCount = 2),
            ctx(HnsItemSide.DEFENDER, attackerAbilityId = 0, defenderAbilityId = 0,
                attackerGastroAcid = false, defenderGastroAcid = true, observedBattlersCount = 2),
            ctx(HnsItemSide.ATTACKER, attackerAbilityId = 0, defenderAbilityId = 0,
                attackerGastroAcid = true, defenderGastroAcid = false, observedBattlersCount = 2)
        ).forEach { assessForTest(758, it).rule?.let(produced::add) }
        listOf(
            assessForTest(airBalloon, ctx(
                HnsItemSide.ATTACKER,
                fieldStatuses = com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_GRASSY_TERRAIN,
                attackerTerrainApplicability = HnsTerrainApplicability.NOT_AFFECTED,
                observedBattlersCount = 2
            )),
            assessForTest(airBalloon, ctx(
                HnsItemSide.DEFENDER,
                fieldStatuses = com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_MISTY_TERRAIN,
                defenderTerrainApplicability = HnsTerrainApplicability.AFFECTED,
                observedBattlersCount = 2
            )),
            assessForTest(terrainSeed, ctx(
                HnsItemSide.ATTACKER,
                fieldStatuses = com.dualdex.pokemon.hns.HnsFieldStatusData.STATUS_FIELD_ELECTRIC_TERRAIN,
                observedBattlersCount = 2
            )),
            assessForTest(798, ctx(
                HnsItemSide.ATTACKER,
                observedBattlersCount = 2
            ))
        ).forEach { it.rule?.let(produced::add) }
        assessForTest(choiceBand, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.PHYSICAL,
            attackerActiveGimmick = 4)).rule?.let(produced::add)
        assessForTest(choiceSpecs, ctx(HnsItemSide.ATTACKER, moveCategory = MoveCategory.SPECIAL,
            attackerActiveGimmick = 4)).rule?.let(produced::add)
        assessForTest(boosterEnergy, ctx(HnsItemSide.ATTACKER, attackerAbilityId = 281,
            attackerBoosterEnergyActivated = false, attackerParadoxBoostedStat = 0)).rule?.let(produced::add)
        assessForTest(boosterEnergy, ctx(HnsItemSide.ATTACKER, attackerAbilityId = null))
            .rule?.let(produced::add)
        assessForTest(redOrb, ctx(HnsItemSide.ATTACKER, switchInEventsSettled = false)).rule?.let(produced::add)
        assessForTest(redOrb, ctx(HnsItemSide.ATTACKER, attackerSpeciesId = 1007)).rule?.let(produced::add)
        val matchingFireItem = ctx(HnsItemSide.ATTACKER, moveType = PokemonType.FIRE)
        assessForTest(426, matchingFireItem.copy(
            holdEffectResolution = HnsHoldEffectResolution(
                426, HnsItemRegistry.classify(426).data, HnsHoldEffectState.SUPPRESSED_NONE,
                "HOLD_EFFECT_NONE", "observed Embargo"
            )
        )).rule?.let(produced::add)
        val berryRules = listOf(
            HnsResistBerryDecision(HnsResistBerryState.NOT_APPLICABLE, 550, 4096,
                "resist_berry_no_current_item", "ITEM_NONE is current."),
            HnsResistBerryDecision(HnsResistBerryState.NOT_APPLICABLE, 550, 4096,
                "resist_berry_other_item", "The current item differs."),
            HnsResistBerryDecision(HnsResistBerryState.NOT_APPLICABLE, 550, 4096,
                "resist_berry_hold_effect_suppressed", "Embargo suppresses the effect."),
            HnsResistBerryDecision(HnsResistBerryState.NOT_APPLICABLE, 550, 4096,
                "resist_berry_condition_not_met", "The type effectiveness is below the threshold."),
            HnsResistBerryDecision(HnsResistBerryState.BLOCKED_BY_UNNERVE, 550, 4096,
                "resist_berry_unnerve_blocked", "A living opponent blocks the berry."),
            HnsResistBerryDecision(HnsResistBerryState.APPLIES, 550, 2048,
                "resist_berry_half", "The ordinary half modifier applies."),
            HnsResistBerryDecision(HnsResistBerryState.APPLIES, 550, 1024,
                "resist_berry_ripen_quarter", "Ripen applies the quarter modifier."),
            HnsResistBerryDecision(HnsResistBerryState.UNKNOWN, 550, null,
                "resist_berry_authority_unobserved", "The live operand is unread.")
        )
        val matchingFireBerry = matchingFireItem.copy(side = HnsItemSide.DEFENDER)
        berryRules.forEach { berry ->
            assessForTest(550, matchingFireBerry.copy(resistBerryDecision = berry)).rule?.let(produced::add)
        }
        assessForTest(426, matchingFireItem.copy(
            holdEffectResolution = HnsHoldEffectResolution(
                426, HnsItemRegistry.classify(426).data, HnsHoldEffectState.UNKNOWN,
                null, "Embargo was not observed"
            )
        )).rule?.let(produced::add)
        assessForTest(lightBall, ctx(HnsItemSide.ATTACKER, attackerBaseSpeciesId = 25))
            .rule?.let(produced::add)
        assessForTest(lightBall, ctx(HnsItemSide.ATTACKER, attackerBaseSpeciesId = 26))
            .rule?.let(produced::add)
        assessForTest(punchingGlove, ctx(HnsItemSide.ATTACKER, punchingMove = true))
            .rule?.let(produced::add)
        assessForTest(punchingGlove, ctx(HnsItemSide.ATTACKER, punchingMove = false))
            .rule?.let(produced::add)
        assessForTest(metronome, ctx(HnsItemSide.ATTACKER, attackerMetronomeItemCounter = null))
            .rule?.let(produced::add)
        assessForTest(deepSeaScale, ctx(HnsItemSide.DEFENDER, moveCategory = MoveCategory.SPECIAL,
            defenderSpeciesId = 132))
            .rule?.let(produced::add)
        assessForTest(eviolite, ctx(HnsItemSide.DEFENDER, defenderCanEvolve = false))
            .rule?.let(produced::add)
        assessForTest(umbrella, ctx(HnsItemSide.ATTACKER, weatherWord = 1 shl 5))
            .rule?.let(produced::add)
        assertEquals(HnsItemAuditData.contextRuleNames, produced)
        assertTrue(produced.size >= 30)
    }
}
