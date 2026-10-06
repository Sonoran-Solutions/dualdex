package com.dualdex.calculator

import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.pokemon.hns.HnsFieldStatus
import com.dualdex.pokemon.hns.HnsFieldStatusData
import com.dualdex.pokemon.hns.HnsItemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request-local H&S field relevance. For every contextual rule: an irrelevant case, a relevant case
 * and a missing-authority case. UNKNOWN never clears, and each active bit is decided on its own.
 */
class HnsFieldContextPolicyTest {

    private val tackle = 33
    private val quickAttack = 98
    private val floatyFall = 678
    private val analytic = HnsFieldStatusData.ABILITY_ANALYTIC

    private fun ctx(
        selectedStrikeModelled: Boolean? = true,
        moveId: Int? = tackle,
        preField: PokemonType? = PokemonType.NORMAL,
        effective: PokemonType? = PokemonType.NORMAL,
        attackerAbility: Int? = 65,
        defenderAbility: Int? = 77,
        attackerItem: Int? = 0,
        defenderItem: Int? = 0,
        category: MoveCategory? = MoveCategory.PHYSICAL,
        fieldStatuses: Int? = 0,
        attackerTerrain: HnsTerrainApplicability? = HnsTerrainApplicability.AFFECTED,
        defenderTerrain: HnsTerrainApplicability? = HnsTerrainApplicability.AFFECTED
    ) = HnsFieldContextPolicy.Context(
        selectedStrikeModelled, moveId, preField, effective, attackerAbility, defenderAbility, attackerItem, defenderItem,
        category, fieldStatuses, attackerTerrain, defenderTerrain,
        attackerHoldEffectResolution = holdEffectResolution(attackerItem),
        defenderHoldEffectResolution = holdEffectResolution(defenderItem)
    )

    private fun holdEffectResolution(id: Int?): HnsHoldEffectResolution? {
        if (id == null) return null
        val item = HnsItemRegistry.classify(id).data
        return HnsHoldEffectResolution(
            id, item,
            if (id == 0) HnsHoldEffectState.ACTIVE_EXACT else HnsHoldEffectState.SUPPRESSED_NONE,
            "HOLD_EFFECT_NONE", "Synthetic exact current-item observation under Magic Room."
        )
    }

    private fun decide(status: HnsFieldStatus, context: HnsFieldContextPolicy.Context?): HnsFieldRequestDecision =
        HnsFieldContextPolicy.assess(HnsFieldState.decode(status.mask), context?.copy(
            fieldStatuses = context.fieldStatuses?.or(status.mask)
        )).single()

    private val irrelevant = HnsFieldRequestRelevance.PROVEN_IRRELEVANT
    private val relevant = HnsFieldRequestRelevance.RELEVANT
    private val modelled = HnsFieldRequestRelevance.MODELLED
    private val unknown = HnsFieldRequestRelevance.UNKNOWN

    private fun assertRule(expected: HnsFieldRequestRelevance, rule: String?, decision: HnsFieldRequestDecision) {
        assertEquals("${decision.status}", expected, decision.relevance)
        assertEquals("${decision.status}", rule, decision.rule)
    }

    @Test
    fun `a clear word yields no decision and every active bit exactly one`() {
        assertTrue(HnsFieldContextPolicy.assess(HnsFieldState.decode(0), ctx()).isEmpty())
        val all = HnsFieldContextPolicy.assess(HnsFieldState.decode(HnsFieldStatusData.KNOWN_MASK), ctx())
        assertEquals(HnsFieldStatus.entries.toList(), all.map { it.status })
        assertTrue(all.all { it.rawMask == it.status!!.mask })
    }

    @Test
    fun `unknown bits are one always-unknown decision carrying the unknown mask`() {
        val decisions = HnsFieldContextPolicy.assess(HnsFieldState.decode(0x3000 or 0x100), ctx())
        val unknownBits = decisions.single { it.status == null }
        assertEquals(0x3000, unknownBits.rawMask)
        assertRule(unknown, "unknown_field_bits", unknownBits)
        assertEquals("Unknown field bits 0x00003000", unknownBits.label)
    }

    @Test
    fun `Fairy Lock is always irrelevant and Wonder Room always relevant`() {
        for (context in listOf(ctx(), null, ctx(selectedStrikeModelled = false, effective = null))) {
            assertRule(irrelevant, "fairy_lock_escape_only", decide(HnsFieldStatus.FAIRY_LOCK, context))
            assertRule(relevant, "wonder_room_swaps_defensive_stat", decide(HnsFieldStatus.WONDER_ROOM, context))
        }
    }

    @Test
    fun `non-ordinary or missing moves never clear a contextual bit`() {
        val contextual = HnsFieldStatus.entries - HnsFieldStatus.FAIRY_LOCK - HnsFieldStatus.WONDER_ROOM
        for (status in contextual) {
            assertEquals("$status", unknown, decide(status, ctx(selectedStrikeModelled = false)).relevance)
            assertEquals("$status", unknown, decide(status, ctx(selectedStrikeModelled = null)).relevance)
            assertEquals("$status", unknown, decide(status, null).relevance)
        }
    }

    @Test
    fun `Trick Room needs a known non-Analytic attacker`() {
        assertRule(irrelevant, "trick_room_attacker_not_analytic", decide(HnsFieldStatus.TRICK_ROOM, ctx()))
        assertRule(relevant, "trick_room_attacker_analytic",
            decide(HnsFieldStatus.TRICK_ROOM, ctx(attackerAbility = analytic)))
        assertRule(unknown, null, decide(HnsFieldStatus.TRICK_ROOM, ctx(attackerAbility = null)))
    }

    @Test
    fun `Mud Sport and Water Sport need the effective type`() {
        assertRule(irrelevant, "mud_sport_non_electric_move", decide(HnsFieldStatus.MUD_SPORT, ctx()))
        assertRule(relevant, "mud_sport_electric_move",
            decide(HnsFieldStatus.MUD_SPORT, ctx(effective = PokemonType.ELECTRIC)))
        assertRule(unknown, null, decide(HnsFieldStatus.MUD_SPORT, ctx(effective = null)))
        assertRule(irrelevant, "water_sport_non_fire_move", decide(HnsFieldStatus.WATER_SPORT, ctx()))
        assertRule(relevant, "water_sport_fire_move",
            decide(HnsFieldStatus.WATER_SPORT, ctx(effective = PokemonType.FIRE)))
        assertRule(unknown, null, decide(HnsFieldStatus.WATER_SPORT, ctx(effective = null)))
    }

    @Test
    fun `Gravity models its terrain grounding contribution when terrain is exact`() {
        assertEquals(setOf(floatyFall), HnsFieldStatusData.gravityBannedOrdinaryMoveIds)
        assertRule(irrelevant, "gravity_non_ground_unbanned_move", decide(HnsFieldStatus.GRAVITY, ctx()))
        assertRule(relevant, "gravity_ground_move", decide(HnsFieldStatus.GRAVITY, ctx(effective = PokemonType.GROUND)))
        assertRule(relevant, "gravity_banned_move", decide(HnsFieldStatus.GRAVITY, ctx(moveId = floatyFall)))
        assertRule(unknown, null, decide(HnsFieldStatus.GRAVITY, ctx(effective = null)))
        assertRule(unknown, null, decide(HnsFieldStatus.GRAVITY, ctx(moveId = null)))
        assertRule(modelled, "gravity_terrain_modifier_modelled", HnsFieldContextPolicy.assess(
            HnsFieldState.decode(HnsFieldStatus.GRAVITY.mask),
            ctx(effective = PokemonType.GRASS, fieldStatuses = HnsFieldStatus.GRAVITY.mask or
                HnsFieldStatus.GRASSY_TERRAIN.mask)
        ).single())
    }

    @Test
    fun `Grassy Terrain models the direct modifier and Grass Pelt composition`() {
        assertRule(modelled, "grassy_terrain_no_unmodelled_consequence", decide(HnsFieldStatus.GRASSY_TERRAIN, ctx()))
        assertRule(modelled, "grassy_terrain_grounded_attacker_grass_move",
            decide(HnsFieldStatus.GRASSY_TERRAIN, ctx(effective = PokemonType.GRASS)))
        assertRule(modelled, "grassy_terrain_grass_pelt_physical_composition",
            decide(HnsFieldStatus.GRASSY_TERRAIN, ctx(defenderAbility = HnsFieldStatusData.ABILITY_GRASS_PELT)))
        assertRule(modelled, "grassy_terrain_grass_pelt_special_no_effect",
            decide(HnsFieldStatus.GRASSY_TERRAIN, ctx(
                defenderAbility = HnsFieldStatusData.ABILITY_GRASS_PELT, category = MoveCategory.SPECIAL
            )))
        assertRule(unknown, null,
            decide(HnsFieldStatus.GRASSY_TERRAIN, ctx(
                defenderAbility = HnsFieldStatusData.ABILITY_GRASS_PELT,
                fieldStatuses = HnsFieldStatusData.STATUS_FIELD_WONDER_ROOM
            )))
        assertRule(relevant, "grassy_terrain_attacker_analytic",
            decide(HnsFieldStatus.GRASSY_TERRAIN, ctx(
                attackerAbility = analytic, moveId = HnsFieldStatusData.GRASSY_GLIDE_MOVE_ID
            )))
        assertRule(modelled, "grassy_terrain_no_unmodelled_consequence",
            decide(HnsFieldStatus.GRASSY_TERRAIN, ctx(attackerAbility = analytic)))
        for (missing in listOf(ctx(effective = null), ctx(attackerAbility = null), ctx(defenderAbility = null))) {
            assertRule(unknown, null, decide(HnsFieldStatus.GRASSY_TERRAIN, missing))
        }
    }

    @Test
    fun `Misty Terrain uses defender applicability for Dragon moves`() {
        assertRule(irrelevant, "misty_terrain_non_dragon_move", decide(HnsFieldStatus.MISTY_TERRAIN, ctx()))
        assertRule(modelled, "misty_terrain_grounded_defender_dragon",
            decide(HnsFieldStatus.MISTY_TERRAIN, ctx(effective = PokemonType.DRAGON)))
        assertRule(modelled, "misty_terrain_ungrounded_defender_dragon",
            decide(HnsFieldStatus.MISTY_TERRAIN, ctx(
                effective = PokemonType.DRAGON, defenderTerrain = HnsTerrainApplicability.NOT_AFFECTED
            )))
        assertRule(unknown, null, decide(HnsFieldStatus.MISTY_TERRAIN, ctx(
            effective = PokemonType.DRAGON, defenderTerrain = HnsTerrainApplicability.UNKNOWN
        )))
        assertRule(unknown, null, decide(HnsFieldStatus.MISTY_TERRAIN, ctx(effective = null)))
    }

    @Test
    fun `Electric Terrain models its direct modifier and Hadron Engine composition`() {
        assertRule(modelled, "electric_terrain_other_move", decide(HnsFieldStatus.ELECTRIC_TERRAIN, ctx()))
        assertRule(modelled, "electric_terrain_grounded_electric_move",
            decide(HnsFieldStatus.ELECTRIC_TERRAIN, ctx(effective = PokemonType.ELECTRIC)))
        assertRule(modelled, "electric_terrain_hadron_engine_special", decide(HnsFieldStatus.ELECTRIC_TERRAIN,
            ctx(attackerAbility = HnsFieldStatusData.ABILITY_HADRON_ENGINE, category = MoveCategory.SPECIAL)))
        assertRule(irrelevant, "electric_terrain_hadron_engine_physical", decide(HnsFieldStatus.ELECTRIC_TERRAIN,
            ctx(attackerAbility = HnsFieldStatusData.ABILITY_HADRON_ENGINE, category = MoveCategory.PHYSICAL)))
        assertRule(modelled, "electric_terrain_grounded_electric_hadron_special",
            decide(HnsFieldStatus.ELECTRIC_TERRAIN, ctx(effective = PokemonType.ELECTRIC,
                attackerAbility = HnsFieldStatusData.ABILITY_HADRON_ENGINE, category = MoveCategory.SPECIAL)))
        assertRule(modelled, "electric_terrain_ungrounded_electric_move",
            decide(HnsFieldStatus.ELECTRIC_TERRAIN, ctx(effective = PokemonType.ELECTRIC,
                attackerTerrain = HnsTerrainApplicability.NOT_AFFECTED)))
        for (paradox in listOf(
            ctx(attackerAbility = HnsFieldStatusData.ABILITY_QUARK_DRIVE),
            ctx(defenderAbility = HnsFieldStatusData.ABILITY_QUARK_DRIVE)
        )) {
            assertRule(relevant, "electric_terrain_paradox_ability", decide(HnsFieldStatus.ELECTRIC_TERRAIN, paradox))
        }
        // A defender's Hadron Engine only boosts its own attacks.
        assertRule(modelled, "electric_terrain_other_move",
            decide(HnsFieldStatus.ELECTRIC_TERRAIN, ctx(defenderAbility = HnsFieldStatusData.ABILITY_HADRON_ENGINE)))
        assertRule(relevant, "electric_terrain_attacker_analytic",
            decide(HnsFieldStatus.ELECTRIC_TERRAIN, ctx(attackerAbility = analytic)))
        for (missing in listOf(ctx(effective = null), ctx(attackerAbility = null), ctx(defenderAbility = null))) {
            assertRule(unknown, null, decide(HnsFieldStatus.ELECTRIC_TERRAIN, missing))
        }
    }

    @Test
    fun `Psychic Terrain needs the type, the pinned priority and the attacker ability`() {
        assertTrue(quickAttack in HnsFieldStatusData.positivePriorityOrdinaryMoveIds)
        assertRule(irrelevant, "psychic_terrain_non_psychic_nonpriority_move",
            decide(HnsFieldStatus.PSYCHIC_TERRAIN, ctx()))
        assertRule(modelled, "psychic_terrain_grounded_attacker_nonpriority_move",
            decide(HnsFieldStatus.PSYCHIC_TERRAIN, ctx(effective = PokemonType.PSYCHIC)))
        assertRule(relevant, "psychic_terrain_priority_move",
            decide(HnsFieldStatus.PSYCHIC_TERRAIN, ctx(moveId = quickAttack)))
        for (missing in listOf(
            ctx(effective = null), ctx(moveId = null), ctx(attackerAbility = null),
            ctx(attackerAbility = HnsFieldStatusData.ABILITY_GALE_WINGS)
        )) {
            assertRule(unknown, null, decide(HnsFieldStatus.PSYCHIC_TERRAIN, missing))
        }
    }

    @Test
    fun `Ion Deluge reads the pre-field type`() {
        assertRule(relevant, "ion_deluge_normal_move", decide(HnsFieldStatus.ION_DELUGE, ctx(effective = null)))
        assertRule(irrelevant, "ion_deluge_non_normal_move",
            decide(HnsFieldStatus.ION_DELUGE, ctx(preField = PokemonType.WATER, effective = PokemonType.WATER)))
        assertRule(unknown, null, decide(HnsFieldStatus.ION_DELUGE, ctx(preField = null, effective = null)))
    }

    @Test
    fun `Magic Room models exact hold-effect suppression but keeps identity exceptions conservative`() {
        val everstone = 245
        val charcoal = 426
        assertRule(modelled, "magic_room_held_items_neutral", decide(HnsFieldStatus.MAGIC_ROOM, ctx()))
        assertRule(modelled, "magic_room_held_items_neutral",
            decide(HnsFieldStatus.MAGIC_ROOM, ctx(attackerItem = everstone, defenderItem = 0)))
        assertRule(modelled, "magic_room_held_items_neutral",
            decide(HnsFieldStatus.MAGIC_ROOM, ctx(attackerItem = charcoal)))
        assertRule(unknown, null, decide(HnsFieldStatus.MAGIC_ROOM, ctx(defenderItem = null)))
        assertRule(unknown, null, decide(HnsFieldStatus.MAGIC_ROOM, ctx(attackerItem = 581)))
        assertRule(unknown, null, decide(HnsFieldStatus.MAGIC_ROOM, ctx(attackerItem = 288)))
    }

    @Test
    fun `every implemented rule is reviewed in the generated audit`() {
        val produced = mutableSetOf<String>()
        val contexts = listOf(
            ctx(moveId=89,effective=PokemonType.GROUND).copy(defenderSemiInvulnerableState=0),
            ctx(), ctx(effective = PokemonType.ELECTRIC), ctx(effective = PokemonType.FIRE),
            ctx(effective = PokemonType.GROUND), ctx(moveId = floatyFall), ctx(effective = PokemonType.GRASS),
            ctx(defenderAbility = HnsFieldStatusData.ABILITY_GRASS_PELT),
            ctx(defenderAbility = HnsFieldStatusData.ABILITY_GRASS_PELT, category = MoveCategory.SPECIAL),
            ctx(attackerAbility = analytic),
            ctx(effective = PokemonType.DRAGON), ctx(attackerAbility = HnsFieldStatusData.ABILITY_QUARK_DRIVE),
            ctx(attackerAbility = HnsFieldStatusData.ABILITY_HADRON_ENGINE, category = MoveCategory.SPECIAL),
            ctx(attackerAbility = HnsFieldStatusData.ABILITY_HADRON_ENGINE, category = MoveCategory.PHYSICAL),
            ctx(effective = PokemonType.ELECTRIC, attackerAbility = HnsFieldStatusData.ABILITY_HADRON_ENGINE,
                category = MoveCategory.SPECIAL),
            ctx(effective = PokemonType.ELECTRIC, attackerTerrain = HnsTerrainApplicability.NOT_AFFECTED),
            ctx(effective = PokemonType.DRAGON, defenderTerrain = HnsTerrainApplicability.NOT_AFFECTED),
            ctx(effective = PokemonType.GRASS, fieldStatuses = HnsFieldStatus.GRASSY_TERRAIN.mask or
                HnsFieldStatus.GRAVITY.mask),
            ctx(moveId = HnsFieldStatusData.GRASSY_GLIDE_MOVE_ID, attackerAbility = analytic),
            ctx(effective = PokemonType.PSYCHIC), ctx(moveId = quickAttack),
            ctx(moveId = 409, attackerAbility = 205, defenderTerrain = HnsTerrainApplicability.NOT_AFFECTED),
            ctx(preField = PokemonType.WATER, effective = PokemonType.WATER),
            ctx(moveId=486,effective=PokemonType.ELECTRIC,category=MoveCategory.SPECIAL).copy(effectiveSpeedExact=true)
        )
        for (context in contexts) {
            HnsFieldContextPolicy.assess(HnsFieldState.decode(-1), context).mapNotNullTo(produced) { it.rule }
        }
        assertEquals(HnsFieldStatusData.contextRuleNames, produced)
    }
    @Test fun `Electro Ball terrain requires both Speed and damage authority`() {
        val exact=ctx(moveId=486,effective=PokemonType.ELECTRIC,category=MoveCategory.SPECIAL)
            .copy(effectiveSpeedExact=true)
        assertRule(modelled,"electro_ball_electric_terrain_speed_exact",decide(HnsFieldStatus.ELECTRIC_TERRAIN,exact))
        for(missing in listOf(exact.copy(effectiveSpeedExact=false),
            exact.copy(attackerTerrainApplicability=HnsTerrainApplicability.UNKNOWN),exact.copy(moveCategory=null)))
            assertEquals(unknown,decide(HnsFieldStatus.ELECTRIC_TERRAIN,missing).relevance)
    }
    @Test fun `Earthquake Grassy effect uses observed semi state independent of terrain grounding`() {
        val base=ctx(moveId=89,effective=PokemonType.GROUND,defenderTerrain=HnsTerrainApplicability.UNKNOWN)
        for (semi in listOf(0,1)) {
            assertRule(modelled,"grassy_terrain_earthquake_semi_state",
                decide(HnsFieldStatus.GRASSY_TERRAIN,base.copy(defenderSemiInvulnerableState=semi)))
        }
        assertRule(unknown,null,decide(HnsFieldStatus.GRASSY_TERRAIN,base))
    }

}
