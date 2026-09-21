package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsChallengeField
import com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 17-field challenge-settings inventory for H&S 2.0.5 (issue #9, Gap C4a).
 *
 * Every field the runtime reader exposes must have exactly one audited row; no field may be
 * omitted or left "not investigated".
 */
class HnsChallengeSettingInventoryTest {

    private val observedOff = HnsChallengeField(observed = true, raw = 0, outOfDomain = false)

    private fun fullSnapshot(): HnsChallengeSettingsSnapshot = HnsChallengeSettingsSnapshot(
        status = com.dualdex.pokemon.hns.HnsChallengeSettingsStatus.OBSERVED,
        optionStyle = observedOff,
        txModeFairyTypes = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txRandomType = observedOff,
        txRandomTypeEffectiveness = observedOff,
        txRandomAbilities = observedOff,
        txRandomMoves = observedOff,
        txChallengesNoEvs = observedOff,
        txChallengesBaseStatEqualizer = observedOff,
        txChallengesMirror = observedOff,
        txChallengesMirrorThief = observedOff,
        txChallengesTrainerScalingIvs = observedOff,
        txChallengesTrainerScalingEvs = observedOff,
        txChallengesMaxPartyIvs = observedOff,
        txModeSturdy = HnsChallengeField(observed = true, raw = 1, outOfDomain = false),
        txChallengesLevelCap = observedOff,
        txChallengesExpMultiplier = observedOff,
        txModeLegendaryAbilities = HnsChallengeField(observed = true, raw = 1, outOfDomain = false)
    )

    @Test
    fun `every challenge setting has exactly one audited row`() {
        assertEquals(
            HnsChallengeSettingId.entries.size,
            HnsChallengeSettingInventory.entries.size
        )
        assertEquals(17, HnsChallengeSettingInventory.entries.size)
        val covered = HnsChallengeSettingInventory.entries.map { it.field }.toSet()
        assertEquals(HnsChallengeSettingId.entries.toSet(), covered)
    }

    @Test
    fun `every row has semantics, relevance and a reason`() {
        HnsChallengeSettingInventory.entries.forEach { row ->
            assertTrue("${row.field} semantics empty", row.sourceSemantics.isNotBlank())
            assertTrue("${row.field} relevance empty", row.damageRelevance.isNotBlank())
            assertTrue("${row.field} reason empty", row.reason.isNotBlank())
            when (row.disposition) {
                HnsChallengeSettingDisposition.CONDITIONAL_BLOCKER ->
                    // A conditional blocker must name the exact limitation it adds.
                    assertNotNull("${row.field} conditional blocker must name a limitation", row.blocker)
                HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM,
                HnsChallengeSettingDisposition.IRRELEVANT_TO_CURRENT_DAMAGE ->
                    // Downstream/irrelevant rows must never add a redundant blocker.
                    assertNull("${row.field} non-blocker row must not name a limitation", row.blocker)
                HnsChallengeSettingDisposition.CONSUMED_RULE ->
                    // Consumed rules may still name an active-mode blocker (e.g. RANDOM TYPES).
                    Unit
            }
        }
    }

    @Test
    fun `base stat equalizer and random moves are the only value-changing blockers`() {
        val blockers = HnsChallengeSettingInventory.entries
            .filter { it.disposition == HnsChallengeSettingDisposition.CONDITIONAL_BLOCKER }
            .map { it.field }
            .toSet()
        assertEquals(
            setOf(
                HnsChallengeSettingId.TX_CHALLENGES_BASE_STAT_EQUALIZER,
                HnsChallengeSettingId.TX_RANDOM_MOVES
            ),
            blockers
        )
    }

    @Test
    fun `downstream-captured settings never add an independent blocker`() {
        HnsChallengeSettingInventory.entries
            .filter { it.disposition == HnsChallengeSettingDisposition.CAPTURED_DOWNSTREAM }
            .forEach { row ->
                assertTrue(row.capturedDownstream)
                assertNull(row.blocker)
            }
    }

    @Test
    fun `consumed rule fields map to their policy limitations`() {
        assertEquals(
            CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED,
            HnsChallengeSettingInventory.entry(HnsChallengeSettingId.TX_RANDOM_TYPE).blocker
        )
        assertEquals(
            CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED,
            HnsChallengeSettingInventory.entry(
                HnsChallengeSettingId.TX_RANDOM_TYPE_EFFECTIVENESS
            ).blocker
        )
        assertEquals(
            CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED,
            HnsChallengeSettingInventory.entry(
                HnsChallengeSettingId.TX_CHALLENGES_BASE_STAT_EQUALIZER
            ).blocker
        )
        assertEquals(
            CalcLimitation.HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED,
            HnsChallengeSettingInventory.entry(HnsChallengeSettingId.TX_RANDOM_MOVES).blocker
        )
    }

    @Test
    fun `fieldOf reads the matching snapshot member for every row`() {
        val snapshot = fullSnapshot()
        HnsChallengeSettingInventory.entries.forEach { row ->
            assertNotNull(HnsChallengeSettingInventory.fieldOf(row.field, snapshot))
        }
        assertTrue(
            HnsChallengeSettingInventory.fieldOf(
                HnsChallengeSettingId.TX_MODE_STURDY,
                snapshot
            ).observedFlag == true
        )
    }

    @Test
    fun `sturdy is recorded irrelevant rather than as a redundant blocker`() {
        val sturdy = HnsChallengeSettingInventory.entry(HnsChallengeSettingId.TX_MODE_STURDY)
        assertEquals(HnsChallengeSettingDisposition.IRRELEVANT_TO_CURRENT_DAMAGE, sturdy.disposition)
        assertNull(sturdy.blocker)
        assertFalse(sturdy.capturedDownstream)
    }
}
