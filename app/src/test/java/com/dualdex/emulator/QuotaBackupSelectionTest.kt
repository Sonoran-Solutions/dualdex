package com.dualdex.emulator

import org.junit.Assert.*
import org.junit.Test

class QuotaBackupSelectionTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    private fun canonical(id: String, size: Long = 100) = listOf(
        QuotaBackupSelection.Candidate(id, QuotaBackupSelection.Category.CANONICAL_BATTERY, "saves_v2/$id/battery.sav", size, 1),
        QuotaBackupSelection.Candidate(id, QuotaBackupSelection.Category.SAVE_METADATA, "saves_v2/$id/metadata.json", 10, 1)
    )

    private fun select(ids: Set<String>, records: List<QuotaBackupSelection.Candidate>, budget: Long = 1_000) =
        QuotaBackupSelection.select(ids, records, budget)

    @Test fun oneRomSmallCanonicalSaveIsCovered() {
        val result = select(setOf(a), canonical(a))
        assertEquals(QuotaBackupSelection.Verdict.SUCCESS, result.verdict)
        assertEquals(setOf(a), result.coveredRomIdentities)
        assertTrue(result.allEssentialSavesCovered)
        assertEquals(110, result.selectedBytes)
    }

    @Test fun multipleRomsAndSimilarTitlesRemainHashIsolated() {
        val result = select(setOf(a, b), canonical(a) + canonical(b))
        assertEquals(setOf(a, b), result.coveredRomIdentities)
        assertEquals(220, result.selectedBytes)
    }

    @Test fun hugeOptionalStatesAndRecoveryDoNotDisplaceEssentialSaves() {
        val extras = List(32) { index ->
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.STATE, "saves_v2/$a/slot${index + 1}.state", 1_000_000, index.toLong())
        } + listOf(
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.THUMBNAIL, "saves_v2/$a/thumb.png", 100, 1),
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.VERIFIED_RECOVERY_BATTERY, "saves_fallback/$a/battery.sav", 120_000_000, 1, assignmentVerified = true)
        )
        val result = select(setOf(a), canonical(a) + extras)
        assertEquals(setOf(a), result.coveredRomIdentities)
        assertTrue(result.selected.none { it.category == QuotaBackupSelection.Category.STATE })
        assertEquals(QuotaBackupSelection.Verdict.PARTIAL, result.verdict)
    }

    @Test fun validVerifiedRecoveryCoversMissingCanonicalButUnassignedLegacyDoesNot() {
        val recovery = QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.VERIFIED_RECOVERY_BATTERY, "saves_fallback/$a/battery.sav", 80, 1, assignmentVerified = true)
        val good = select(setOf(a), listOf(recovery) + canonical(a).filter { it.category == QuotaBackupSelection.Category.SAVE_METADATA })
        assertEquals(setOf(a), good.coveredRomIdentities)
        val unverified = recovery.copy(category = QuotaBackupSelection.Category.VERIFIED_LEGACY_BATTERY, assignmentVerified = false)
        val refused = select(setOf(a), listOf(unverified) + canonical(a).filter { it.category == QuotaBackupSelection.Category.SAVE_METADATA })
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, refused.verdict)
        assertEquals(setOf(a), refused.unprotectedRomIdentities)
    }

    @Test fun duplicateAndMalformedMetadataFailClosed() {
        val metadata = canonical(a).last()
        val duplicate = select(setOf(a), canonical(a) + metadata)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, duplicate.verdict)
        val malformed = select(setOf(a), canonical(a).map { if (it.category == QuotaBackupSelection.Category.SAVE_METADATA) it.copy(integrityValid = false) else it })
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, malformed.verdict)
    }

    @Test fun invalidAndTraversalPathsAreExcluded() {
        val bad = canonical(a).first().copy(relativePath = "saves_v2/$a/../../rom_cache/game.gba")
        val result = select(setOf(a), listOf(bad) + canonical(a).last())
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertTrue(result.excluded[bad]!!.contains("unsafe"))
    }

    @Test fun essentialDataOverBudgetRefusesEntireCollection() {
        val result = select(setOf(a), canonical(a), budget = 109)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertTrue(result.selected.isEmpty())
        assertFalse(result.allEssentialSavesCovered)
        assertTrue(result.budgetExceeded)
    }

    @Test fun exactBudgetPassesAndOneByteLessRefuses() {
        assertEquals(QuotaBackupSelection.Verdict.SUCCESS, select(setOf(a), canonical(a), budget = 110).verdict)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, select(setOf(a), canonical(a), budget = 109).verdict)
    }

    @Test fun deterministicRegardlessOfInputOrderAndNeverDropsRomForOptionalFiles() {
        val records = canonical(a) + canonical(b) + listOf(
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.STATE, "saves_v2/$a/slot1.state", 900, 2)
        )
        val forward = select(setOf(a, b), records, 220)
        val reversed = select(setOf(a, b), records.reversed(), 220)
        assertEquals(forward.selected, reversed.selected)
        assertEquals(setOf(a, b), forward.coveredRomIdentities)
        assertEquals(QuotaBackupSelection.Verdict.PARTIAL, forward.verdict)
    }

    @Test fun incompleteSnapshotAndChangedSourceAreUnusable() {
        val before = listOf(QuotaBackupSelection.SourceStamp("saves_v2/$a/battery.sav", 100, 1))
        assertFalse(QuotaBackupSelection.snapshotUsable(false, before, before))
        assertTrue(QuotaBackupSelection.snapshotUsable(true, before, before))
        assertFalse(QuotaBackupSelection.snapshotUsable(true, before, listOf(before.single().copy(sizeBytes = 101))))
    }

    @Test fun invalidIdentityAndRomPayloadAreRejected() {
        val malformed = canonical(a).first().copy(romSha256 = "same-title-not-a-hash")
        val rom = canonical(a).first().copy(relativePath = "saves_v2/$a/game.gba")
        val result = select(setOf(a), listOf(malformed, rom) + canonical(a).last())
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertTrue(result.excluded[malformed]!!.contains("identity"))
        assertEquals("ROM payload forbidden", result.excluded[rom])
    }
}
