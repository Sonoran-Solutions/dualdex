package com.dualdex.calculator

import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hns.HnsBattlerGender
import com.dualdex.pokemon.hns.HnsGenderAuthority
import org.junit.Assert.*
import org.junit.Test

class HnsStateBackedGroupDTest {
    private val neutral = CalcHnsLiveBattleState(
        attackerHp = 100, defenderHp = 100, weatherObserved = true, weatherWord = 0, fieldStatuses = 0,
        attackerPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true),
        defenderPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true),
        attackerNeutralizingGas = false, defenderNeutralizingGas = false,
        attackerRawStats = CalcRawStats(151, 100, 80, 100, 100),
        defenderRawStats = CalcRawStats(100, 151, 80, 100, 100),
        attackerStatStages = List(8) { 0 }, defenderStatStages = List(8) { 0 },
        attackerTransformed = false, defenderTransformed = false,
        attackerSelectedGimmick = 0, defenderSelectedGimmick = 0,
        attackerBoosterEnergyActivated = false, defenderBoosterEnergyActivated = false,
        attackerParadoxBoostedStat = 0, defenderParadoxBoostedStat = 0,
        attackerVesselOfRuin = false, defenderVesselOfRuin = false,
        attackerSwordOfRuin = false, defenderSwordOfRuin = false,
        attackerTabletsOfRuin = false, defenderTabletsOfRuin = false,
        attackerBeadsOfRuin = false, defenderBeadsOfRuin = false
    )
    private fun context(id: Int, live: CalcHnsLiveBattleState = neutral,
                        side: HnsAbilitySide = HnsAbilitySide.ATTACKER,
                        type: PokemonType = PokemonType.NORMAL,
                        category: MoveCategory? = MoveCategory.PHYSICAL,
                        opponent: Int = 15) = HnsAbilityContextPolicy.Context(
        side = side, ordinaryMove = true, isCrit = false, moveId = 33,
        attackerAbilityId = if (side == HnsAbilitySide.ATTACKER) id else opponent,
        defenderAbilityId = if (side == HnsAbilitySide.DEFENDER) id else opponent,
        attackerAbilityObserved = true, defenderAbilityObserved = true,
        moveType = type, moveCategory = category, attackerTypes = setOf(PokemonType.NORMAL),
        defenderSpeciesId = 143, defenderHp = 100, defenderMaxHp = 100, attackerStatus1 = 0,
        observedBattlersCount = 2, dynamicMoveTypeKnownNeutral = true,
        attackerItemId = 0, defenderItemId = 0, liveBattleState = live,
        fieldStatuses = live.fieldStatuses, weatherObserved = live.weatherObserved, weatherWord = live.weatherWord
    )
    private fun decision(id: Int, c: HnsAbilityContextPolicy.Context) = HnsAbilityContextPolicy.assess(id, c).relevance
    private val active = HnsAbilityRequestRelevance.RELEVANT
    private val inactive = HnsAbilityRequestRelevance.PROVEN_IRRELEVANT
    private val unknown = HnsAbilityRequestRelevance.UNKNOWN

    @Test fun `simple state branches retain positive neutral and unknown domains`() {
        assertEquals(active, decision(18, context(18, neutral.copy(attackerFlashFireBoosted = true), type = PokemonType.FIRE)))
        assertEquals(inactive, decision(18, context(18, neutral.copy(attackerFlashFireBoosted = false), type = PokemonType.FIRE)))
        assertEquals(unknown, decision(18, context(18, type = PokemonType.FIRE)))
        assertEquals(inactive, decision(18, context(18)))
        for (timer in 0..7) assertEquals(if (timer == 0) inactive else active,
            decision(112, context(112, neutral.copy(attackerSlowStartTimer = timer))))
        assertEquals(unknown, decision(112, context(112)))
        assertEquals(unknown, decision(112, context(112, neutral.copy(attackerSlowStartTimer = 1), category = null)))
        assertEquals(inactive, decision(112, context(112, category = MoveCategory.SPECIAL)))
        for (raw in 0..3) assertEquals(if (raw == 2) active else inactive,
            decision(198, context(198, neutral.copy(defenderIsFirstTurn = raw))))
        assertEquals(unknown, decision(198, context(198, neutral.copy(defenderIsFirstTurn = 4))))
        for (count in 0..5) assertEquals(if (count == 0) inactive else active,
            decision(293, context(293, neutral.copy(attackerSupremeOverlordCounter = count))))
        for (count in listOf(null, -1, 6)) assertEquals(unknown,
            decision(293, context(293, neutral.copy(attackerSupremeOverlordCounter = count))))
    }

    @Test fun `gender is derived from current species ratio and personality low byte`() {
        assertEquals(HnsBattlerGender.UNKNOWN, HnsGenderAuthority.resolve(null, 0, 127))
        assertEquals(HnsBattlerGender.UNKNOWN, HnsGenderAuthority.resolve(1, null, 255))
        assertEquals(HnsBattlerGender.FEMALE, HnsGenderAuthority.resolve(1, 126, 127))
        assertEquals(HnsBattlerGender.MALE, HnsGenderAuthority.resolve(1, 127, 127))
        assertEquals(HnsBattlerGender.FEMALE, HnsGenderAuthority.resolve(1, 0x12345600, 127))
        assertEquals(HnsBattlerGender.MALE, HnsGenderAuthority.resolve(1, -1, 127))
        for ((ratio, gender) in listOf(0 to HnsBattlerGender.MALE, 254 to HnsBattlerGender.FEMALE, 255 to HnsBattlerGender.GENDERLESS))
            assertEquals(gender, HnsGenderAuthority.resolve(1, 0, ratio))
        for (a in HnsBattlerGender.values()) for (d in HnsBattlerGender.values()) {
            val expected = if (a == HnsBattlerGender.UNKNOWN || d == HnsBattlerGender.UNKNOWN) unknown
                else if (a == HnsBattlerGender.GENDERLESS || d == HnsBattlerGender.GENDERLESS) inactive else active
            assertEquals(expected, decision(79, context(79, neutral.copy(attackerGender = a, defenderGender = d))))
        }
    }

    @Test fun `Gorilla and Analytic require their own observed state`() {
        assertEquals(unknown, decision(255, context(255)))
        for (gimmick in 0..5) assertEquals(if (gimmick == 4) inactive else active,
            decision(255, context(255, neutral.copy(attackerSelectedGimmick = 0, attackerGimmick = gimmick))))
        assertEquals(inactive, decision(255, context(255, neutral.copy(attackerSelectedGimmick = 4, attackerGimmick = 0))))
        assertEquals(active, decision(255, context(255, neutral.copy(attackerSelectedGimmick = 5, attackerGimmick = 0))))
        for (order in HnsAnalyticTurnOrder.values()) assertEquals(when(order) {
            HnsAnalyticTurnOrder.LAST_TO_MOVE -> active
            HnsAnalyticTurnOrder.NOT_LAST_TO_MOVE -> inactive
            else -> unknown
        }, decision(148, context(148, neutral.copy(attackerAnalyticTurnOrder = order))))
    }

    @Test fun `Paradox missing activation is unknown and ties preserve Attack`() {
        for (id in listOf(281, 282)) {
            assertEquals(unknown, decision(id, context(id, neutral.copy(attackerParadoxBoostedStat = 6))))
            assertEquals(inactive, decision(id, context(id)))
            assertEquals(active, decision(id, context(id, neutral.copy(attackerBoosterEnergyActivated = true))))
            assertEquals(inactive, decision(id, context(id, neutral.copy(attackerBoosterEnergyActivated = true, attackerTransformed = true))))
            assertEquals(active, decision(id, context(id, neutral.copy(attackerBoosterEnergyActivated = true,
                attackerRawStats = CalcRawStats(151, 151, 151, 151, 151)))))
            assertEquals(inactive, decision(id, context(id, neutral.copy(attackerBoosterEnergyActivated = true, attackerParadoxBoostedStat = 3))))
            assertEquals(active, decision(id, context(id, neutral.copy(defenderBoosterEnergyActivated = true), side = HnsAbilitySide.DEFENDER)))
        }
        assertEquals(unknown, decision(281, context(281, neutral.copy(weatherObserved = false))))
        assertEquals(unknown, decision(282, context(282, neutral.copy(fieldStatuses = null))))
        assertEquals(active, decision(281, context(281, neutral.copy(weatherWord = 8))))
        assertEquals(inactive, decision(281, context(281, neutral.copy(weatherWord = 8), opponent = 13)))
        assertEquals(active, decision(282, context(282, neutral.copy(fieldStatuses = 256))))
    }

    @Test fun `Aura requires shared field liveness and suppression authority`() {
        for ((id, type) in listOf(186 to PokemonType.DARK, 187 to PokemonType.FAIRY)) {
            assertEquals(active, decision(id, context(id, type = type)))
            assertEquals(active, decision(id, context(id, type = type, side = HnsAbilitySide.DEFENDER)))
            assertEquals(inactive, decision(id, context(id)))
            assertEquals(unknown, decision(id, context(id, neutral.copy(defenderNeutralizingGas = true), type = type)))
            assertEquals(unknown, decision(id, context(id, neutral.copy(defenderHp = null), type = type)))
        }
        val bypass = context(188, side = HnsAbilitySide.DEFENDER, opponent = 104)
        assertEquals(false, HnsFieldAbilityAuthority(bypass).present(188))
        assertEquals(true, HnsFieldAbilityAuthority(bypass.copy(
            defenderItemId = 758, defenderAbilityShieldActiveIgnoringAbility = true
        )).present(188))
        assertEquals(true, HnsFieldAbilityAuthority(context(188)).present(188))
        assertEquals(inactive, decision(188, context(188, type = PokemonType.DARK)))
        assertEquals(active, decision(188, context(188, type = PokemonType.DARK, opponent = 186)))
    }

    @Test fun `Ruin flags control field effects independently of the raw ability label`() {
        val sword = neutral.copy(attackerSwordOfRuin = true)
        assertEquals(true, HnsRuinAuthority.modifies(285, context(15, sword)))
        assertEquals(false, HnsRuinAuthority.modifies(285, context(15, sword.copy(defenderSwordOfRuin = true))))
        assertEquals(false, HnsRuinAuthority.modifies(285, context(15, sword, category = MoveCategory.SPECIAL)))
        assertEquals(true, HnsRuinAuthority.modifies(285, context(15, sword.copy(fieldStatuses = 4), category = MoveCategory.SPECIAL)))
        assertEquals(false, HnsRuinAuthority.modifies(285, context(15, sword.copy(
            attackerPersistentVolatiles = CalcHnsPersistentVolatiles(observed = true, gastroAcid = true)))))
        assertEquals(false, HnsRuinAuthority.modifies(285, context(15, sword.copy(defenderNeutralizingGas = true), opponent = 256)))
        assertEquals(null, HnsRuinAuthority.modifies(285, context(15, sword.copy(defenderNeutralizingGas = null))))
        assertEquals(active, decision(284, context(284, neutral.copy(defenderVesselOfRuin = true),
            side = HnsAbilitySide.DEFENDER, category = MoveCategory.SPECIAL)))
        assertEquals(active, decision(286, context(286, neutral.copy(defenderTabletsOfRuin = true), side = HnsAbilitySide.DEFENDER)))
        assertEquals(active, decision(287, context(287, neutral.copy(attackerBeadsOfRuin = true), category = MoveCategory.SPECIAL)))
    }
}
