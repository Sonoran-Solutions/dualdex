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
        root: Boolean = false
    ) = HnsBattlerRuntimeState(
        status = HnsBattlerRuntimeStatus.OBSERVED,
        abilityId = ability,
        itemId = item,
        types = types,
        volatilesObserved = true,
        groupDVolatilesObserved = true,
            selectedDynamaxObserved = true,
            persistentVolatilesObserved = true,
        volatileSemiInvulnerable = semi,
        volatileRoot = root
    )

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
                HnsTerrainAuthority.resolve(activeTerrain, holder))
            assertEquals(HnsTerrainApplicability.AFFECTED,
                HnsTerrainAuthority.resolve(activeTerrain or HnsFieldStatusData.STATUS_FIELD_GRAVITY, holder))
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
            HnsTerrainAuthority.resolve(activeTerrain, ironBallFlying))
    }

    @Test
    fun `missing semi-invulnerability or active grounding volatile fails closed`() {
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            HnsTerrainAuthority.resolve(activeTerrain, battler(semi = 1)))
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            HnsTerrainAuthority.resolve(activeTerrain, battler(root = true)))
    }

    @Test
    fun `Levitate requires authoritative opposing ability suppression state`() {
        val levitate = battler(ability = 26)
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            HnsTerrainAuthority.resolve(activeTerrain, levitate, opposingAbilityId = null))
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            HnsTerrainAuthority.resolve(activeTerrain, levitate, opposingAbilityId = 256))
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            HnsTerrainAuthority.resolve(activeTerrain, levitate, opposingAbilityId = 0))
    }

    @Test
    fun `Mold Breaker can suppress defender Levitate but Ability Shield preserves it`() {
        val levitate = battler(ability = 26)
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            HnsTerrainAuthority.resolve(activeTerrain, levitate, opposingAbilityId = 104,
                battlerIsDefender = true))
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            HnsTerrainAuthority.resolve(activeTerrain,
                battler(item = HnsItemRegistry.resolveIdByName("Ability Shield")!!, ability = 26),
                opposingAbilityId = 104, battlerIsDefender = true))
        assertEquals(HnsTerrainApplicability.UNKNOWN,
            HnsTerrainAuthority.resolve(activeTerrain or HnsFieldStatusData.STATUS_FIELD_MAGIC_ROOM,
                battler(item = HnsItemRegistry.resolveIdByName("Ability Shield")!!, ability = 26),
                opposingAbilityId = 104, battlerIsDefender = true))
        assertEquals(HnsTerrainApplicability.AFFECTED,
            HnsTerrainAuthority.resolve(activeTerrain or HnsFieldStatusData.STATUS_FIELD_GRAVITY,
                levitate, opposingAbilityId = 104, battlerIsDefender = true))
        // Mold Breaker does not suppress the attacker's own Levitate.
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            HnsTerrainAuthority.resolve(activeTerrain, levitate, opposingAbilityId = 104,
                battlerIsDefender = false))
    }

    @Test
    fun `no live terrain means the battler is not terrain affected`() {
        assertEquals(HnsTerrainApplicability.NOT_AFFECTED,
            HnsTerrainAuthority.resolve(0, null))
    }
}
