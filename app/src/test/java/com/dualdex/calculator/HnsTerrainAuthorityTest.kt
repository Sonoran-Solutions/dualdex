package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsBattlerRuntimeState
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsBattlerTypeObservation
import com.dualdex.pokemon.hns.HnsFieldStatusData
import com.dualdex.pokemon.hns.HnsItemRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class HnsTerrainAuthorityTest {
    private fun battler(
        ability: Int = 0,
        item: Int = 0,
        types: List<HnsBattlerTypeObservation> = listOf(
            HnsBattlerTypeObservation(true, 1),
            HnsBattlerTypeObservation(true, 0),
            HnsBattlerTypeObservation(true, 0)
        ),
        semi: Int = 0,
        root: Boolean = false,
        embargo: Boolean = false
    ) = HnsBattlerRuntimeState(
        status = HnsBattlerRuntimeStatus.OBSERVED,
        abilityId = ability,
        itemId = item,
        types = types,
        volatilesObserved = true,
        groupDVolatilesObserved = true,
            selectedGimmickObserved = true,
            persistentVolatilesObserved = true,
        itemVolatilesObserved = true,
        volatileEmbargo = embargo,
        volatileSemiInvulnerable = semi,
        volatileRoot = root
    )

    private fun terrain(
        field: Int?,
        battler: HnsBattlerRuntimeState?,
        opposingAbilityId: Int? = 0,
        battlerIsDefender: Boolean = false
    ): HnsTerrainApplicability {
        if (battler == null) return HnsTerrainAuthority.resolve(field, null, opposingAbilityId, battlerIsDefender)
        val resolution = HnsHoldEffectAuthority.forObservedRuntime(battler, field, neutralizingGasOnField = false)
        val abilityShieldActive = HnsHoldEffectAuthority.abilityShieldActiveIgnoringAbility(
            battler.itemId,
            CalcItemProvenance.BATTLE_EFFECTIVE,
            field,
            battler.volatileEmbargo.takeIf { battler.itemVolatilesObserved }
        )
        return HnsTerrainAuthority.resolve(
            field, battler, opposingAbilityId, battlerIsDefender,
            holdEffectResolution = resolution,
            abilityShieldActiveIgnoringAbility = abilityShieldActive
        )
    }

    private val activeTerrain = HnsFieldStatusData.STATUS_FIELD_GRASSY_TERRAIN

    @Test
    fun `Flying Levitate and Air Balloon are ungrounded unless Gravity overrides`() {
        val flying = battler(types = listOf(
            HnsBattlerTypeObservation(true, 3),
            HnsBattlerTypeObservation(true, 0),
            HnsBattlerTypeObservation(true, 0)
        ))
        val levitate = battler(ability = 26)
        val balloon = battler(item = HnsItemRegistry.resolveIdByName("Air Balloon")!!)
        for (holder in listOf(flying, levitate, balloon)) {
            assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
                terrain(activeTerrain, holder))
            assertEquals(HnsTerrainApplicability.AFFECTED,
                terrain(activeTerrain or HnsFieldStatusData.STATUS_FIELD_GRAVITY, holder))
        }
    }

    @Test
    fun `Iron Ball grounds a Flying holder before Gravity and Flying checks`() {
        val ironBallFlying = battler(
            item = HnsItemRegistry.resolveIdByName("Iron Ball")!!,
            types = listOf(HnsBattlerTypeObservation(true, 3), HnsBattlerTypeObservation(true, 0),
                HnsBattlerTypeObservation(true, 0))
        )
        assertEquals(HnsTerrainApplicability.AFFECTED,
            terrain(activeTerrain, ironBallFlying))
    }

    @Test
    fun `missing semi-invulnerability or active grounding volatile fails closed`() {
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            terrain(activeTerrain, battler(semi = 1)))
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            terrain(activeTerrain, battler(root = true)))
    }

    @Test
    fun `Levitate requires authoritative opposing ability suppression state`() {
        val levitate = battler(ability = 26)
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            terrain(activeTerrain, levitate, opposingAbilityId = null))
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            terrain(activeTerrain, levitate, opposingAbilityId = 256))
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            terrain(activeTerrain, levitate, opposingAbilityId = 0))
    }

    @Test
    fun `Mold Breaker can suppress defender Levitate but Ability Shield preserves it`() {
        val levitate = battler(ability = 26)
        assertEquals(HnsTerrainApplicability.AFFECTED,
            terrain(activeTerrain, levitate, opposingAbilityId = 104,
                battlerIsDefender = true))
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            terrain(activeTerrain,
                battler(item = HnsItemRegistry.resolveIdByName("Ability Shield")!!, ability = 26),
                opposingAbilityId = 104, battlerIsDefender = true))
        assertEquals(HnsTerrainApplicability.AFFECTED,
            terrain(activeTerrain or HnsFieldStatusData.STATUS_FIELD_MAGIC_ROOM,
                battler(item = HnsItemRegistry.resolveIdByName("Ability Shield")!!, ability = 26),
                opposingAbilityId = 104, battlerIsDefender = true))
        assertEquals(HnsTerrainApplicability.AFFECTED,
            terrain(activeTerrain or HnsFieldStatusData.STATUS_FIELD_GRAVITY,
                levitate, opposingAbilityId = 104, battlerIsDefender = true))
        // Mold Breaker does not suppress the attacker's own Levitate.
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            terrain(activeTerrain, levitate, opposingAbilityId = 104,
                battlerIsDefender = false))
    }

    @Test
    fun `no live terrain means the battler is not terrain affected`() {
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            terrain(0, null))
    }

    @Test
    fun `Embargo and Klutz suppress grounding items through shared hold effect authority`() {
        val balloon = HnsItemRegistry.resolveIdByName("Air Balloon")!!
        val ironBall = HnsItemRegistry.resolveIdByName("Iron Ball")!!
        val flying = listOf(HnsBattlerTypeObservation(true, 3), HnsBattlerTypeObservation(true, 0),
            HnsBattlerTypeObservation(true, 0))

        // Air Balloon + Embargo is grounded, so Electric Terrain's boost applies.
        assertEquals(HnsTerrainApplicability.AFFECTED,
            terrain(activeTerrain, battler(item = balloon, embargo = true)))
        assertEquals(HnsTerrainApplicability.AFFECTED,
            terrain(activeTerrain, battler(item = balloon, ability = 103)))
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            terrain(activeTerrain, battler(item = ironBall, types = flying, embargo = true)))
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            terrain(activeTerrain, battler(item = ironBall, types = flying, ability = 103)))
    }

    @Test
    fun `Embargo suppresses Ability Shield protection from Mold Breaker Levitate suppression`() {
        val shield = HnsItemRegistry.resolveIdByName("Ability Shield")!!
        assertEquals(HnsTerrainApplicability.AFFECTED,
            terrain(activeTerrain, battler(item = shield, ability = 26, embargo = true),
                opposingAbilityId = 104, battlerIsDefender = true))
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            terrain(activeTerrain, battler(item = shield, ability = 26),
                opposingAbilityId = 104, battlerIsDefender = true))
    }

    @Test
    fun `Magic Room compositions use exact suppressed item effects`() {
        val balloon = HnsItemRegistry.resolveIdByName("Air Balloon")!!
        val ironBall = HnsItemRegistry.resolveIdByName("Iron Ball")!!
        val shield = HnsItemRegistry.resolveIdByName("Ability Shield")!!
        val flying = listOf(HnsBattlerTypeObservation(true, 3), HnsBattlerTypeObservation(true, 0),
            HnsBattlerTypeObservation(true, 0))
        val magicRoom = HnsFieldStatusData.STATUS_FIELD_MAGIC_ROOM
        assertEquals(HnsTerrainApplicability.AFFECTED, terrain(activeTerrain or magicRoom, battler(item = balloon)))
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            terrain(activeTerrain or magicRoom, battler(item = ironBall, types = flying)))
        assertEquals(HnsTerrainApplicability.AFFECTED,
            terrain(activeTerrain or magicRoom, battler(item = shield, ability = 26),
                opposingAbilityId = 104, battlerIsDefender = true))
    }
}
