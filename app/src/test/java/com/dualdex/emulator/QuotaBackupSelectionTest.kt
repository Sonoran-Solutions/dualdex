package com.dualdex.emulator

import org.junit.Assert.*
import org.junit.Test

class QuotaBackupSelectionTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private val hash1 = "1".repeat(64)
    private val hash2 = "2".repeat(64)

    private fun canonical(id: String, size: Long = 100) = listOf(
        QuotaBackupSelection.Candidate(id, QuotaBackupSelection.Category.CANONICAL_BATTERY, "saves_v2/$id/battery.sav", size, 1),
        QuotaBackupSelection.Candidate(id, QuotaBackupSelection.Category.SAVE_METADATA, "saves_v2/$id/metadata.json", 10, 1)
    )

    private fun select(
        ids: Set<String>, records: List<QuotaBackupSelection.Candidate>, budget: Long = 1_000, inventoryComplete: Boolean = true
    ) = QuotaBackupSelection.select(ids, records, inventoryComplete, budget)

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

    @Test fun orphanCanonicalIsDiscoveredWithoutMetadataAndCannotReportSuccess() {
        val orphan = canonical(a).first()
        val result = select(emptySet(), listOf(orphan))
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertEquals(setOf(a), result.unprotectedRomIdentities)
        assertFalse(result.allEssentialSavesCovered)
    }

    @Test fun incompleteInventoryNeverReportsSuccessfulCoverage() {
        val result = select(emptySet(), emptyList(), inventoryComplete = false)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertFalse(result.inventoryComplete)
        assertFalse(result.allEssentialSavesCovered)
    }

    @Test fun unverifiedLegacySaveIsExplicitlyUnassignableAndRefused() {
        val legacy = QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.VERIFIED_LEGACY_BATTERY, "saves/FireRed.sav", 80, 1)
        val result = select(emptySet(), listOf(legacy))
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertEquals(setOf("saves/FireRed.sav"), result.unassignableSourcePaths)
        assertEquals("legacy/recovery assignment is unverified", result.excluded[legacy])
    }

    @Test fun verifiedLegacyBindingRestoresAcrossDifferentAppRoots() {
        val sourceRoot = "/data/user/0/com.dualdex/files"
        val targetRoot = "/data/user/12/com.dualdex/files"
        val sourcePath = "$sourceRoot/saves/FireRed.sav"
        val restoredPath = "$targetRoot/saves/FireRed.sav"
        assertNotEquals(sourcePath, restoredPath)
        val binding = QuotaBackupSelection.portableLegacyBinding(sourceRoot, sourcePath, hash1, a, "battery.sav")!!
        val destination = QuotaBackupSelection.portableLegacyDestination(binding, targetRoot, restoredPath, hash1)
        assertEquals("saves_v2/$a/battery.sav", destination)
        assertNull(QuotaBackupSelection.portableLegacyDestination(binding, targetRoot, "$targetRoot/saves/Emerald.sav", hash1))
        assertNull(QuotaBackupSelection.portableLegacyDestination(binding, targetRoot, restoredPath, hash2))
        assertNull(QuotaBackupSelection.portableLegacyBinding(sourceRoot, "/elsewhere/FireRed.sav", hash1, a, "battery.sav"))
    }

    @Test fun manyLargeOptionalStatesAndRecoveryDoNotDisplaceEssentials() {
        val extras = List(32) { index ->
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.STATE, "saves_v2/$a/slot${index + 1}.state", 1_000_000, index.toLong())
        } + QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.THUMBNAIL, "saves_v2/$a/thumb.png", 100, 1) +
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.VERIFIED_RECOVERY_BATTERY, "saves_fallback/$a/battery.sav", 120_000_000, 1, assignmentVerified = true)
        val result = select(setOf(a), canonical(a) + extras)
        assertEquals(setOf(a), result.coveredRomIdentities)
        assertTrue(result.selected.none { it.category == QuotaBackupSelection.Category.STATE })
        assertEquals(QuotaBackupSelection.Verdict.PARTIAL, result.verdict)
    }

    @Test fun validVerifiedRecoveryCoversMissingCanonicalButUnassignedLegacyDoesNot() {
        val recovery = QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.VERIFIED_RECOVERY_BATTERY, "saves_fallback/$a/battery.sav", 80, 1, assignmentVerified = true)
        val metadata = canonical(a).last()
        val good = select(setOf(a), listOf(recovery, metadata))
        assertEquals(setOf(a), good.coveredRomIdentities)
        assertEquals(setOf(a), select(emptySet(), listOf(recovery, metadata)).coveredRomIdentities)
        val unverified = recovery.copy(category = QuotaBackupSelection.Category.VERIFIED_LEGACY_BATTERY, assignmentVerified = false)
        val refused = select(setOf(a), listOf(unverified, metadata))
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, refused.verdict)
        assertEquals(setOf(unverified.relativePath), refused.unassignableSourcePaths)
    }

    @Test fun duplicateAndMalformedMetadataFailClosed() {
        val metadata = canonical(a).last()
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, select(setOf(a), canonical(a) + metadata).verdict)
        val malformed = select(setOf(a), canonical(a).map {
            if (it.category == QuotaBackupSelection.Category.SAVE_METADATA) it.copy(integrityValid = false) else it
        })
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, malformed.verdict)
    }

    @Test fun invalidAndTraversalPathsAreExcluded() {
        val bad = canonical(a).first().copy(relativePath = "saves_v2/$a/../../rom_cache/game.gba")
        val result = select(setOf(a), listOf(bad) + canonical(a).last())
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertTrue(result.excluded[bad]!!.contains("unsafe"))
    }

    @Test fun recoverySourceCannotBeClaimedByTwoRomIdentities() {
        val sharedPath = "saves_fallback/recovery.sav"
        val first = QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.VERIFIED_RECOVERY_BATTERY, sharedPath, 80, 1, assignmentVerified = true)
        val second = first.copy(romSha256 = b)
        val records = listOf(first, second) + canonical(a).last() + canonical(b).last()
        val result = select(setOf(a, b), records)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertEquals("source path has conflicting ROM ownership", result.excluded[first])
        assertEquals("source path has conflicting ROM ownership", result.excluded[second])
    }

    @Test fun stateInAnotherRomDirectoryIsRejected() {
        val state = QuotaBackupSelection.Candidate(b, QuotaBackupSelection.Category.STATE, "saves_v2/$a/slot1.state", 50, 1)
        val result = select(setOf(a), canonical(a) + state)
        assertEquals("source path does not match ROM identity", result.excluded[state])
        assertEquals(QuotaBackupSelection.Verdict.PARTIAL, result.verdict)
    }

    @Test fun essentialDataOverBudgetRefusesEntireCollection() {
        val result = select(setOf(a), canonical(a), budget = 109)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
        assertTrue(result.selected.isEmpty())
        assertFalse(result.allEssentialSavesCovered)
        assertEquals(setOf(a), result.unprotectedRomIdentities)
        assertTrue(result.budgetExceeded)
    }

    @Test fun exactBudgetPassesAndOneByteLessRefuses() {
        assertEquals(QuotaBackupSelection.Verdict.SUCCESS, select(setOf(a), canonical(a), budget = 110).verdict)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, select(setOf(a), canonical(a), budget = 109).verdict)
    }

    @Test fun budgetCalculatorIncludesReserveAndAdaptsToQuota() {
        assertEquals(QuotaBackupSelection.MAX_PAYLOAD_BYTES - QuotaBackupSelection.CONTAINER_RESERVE_BYTES,
            QuotaBackupSelection.budgetFor(null))
        assertEquals(20_000_000L * 4 / 5 - QuotaBackupSelection.CONTAINER_RESERVE_BYTES,
            QuotaBackupSelection.budgetFor(20_000_000L))
        assertEquals(0L, QuotaBackupSelection.budgetFor(1L))
    }

    @Test fun transportDispatchCoversAllFlagCombinations() {
        assertEquals(QuotaBackupSelection.BackupRoute.REFUSAL, QuotaBackupSelection.dispatch(false, false))
        assertEquals(QuotaBackupSelection.BackupRoute.CLOUD_SNAPSHOT, QuotaBackupSelection.dispatch(true, false))
        assertEquals(QuotaBackupSelection.BackupRoute.DEVICE_TRANSFER, QuotaBackupSelection.dispatch(false, true))
        assertEquals(QuotaBackupSelection.BackupRoute.DEVICE_TRANSFER, QuotaBackupSelection.dispatch(true, true))
    }

    @Test fun restorePathStagesCloudArchiveAndFullTreeWithoutDirectingUnknownFilesToDestinations() {
        assertEquals(QuotaBackupSelection.RestoreRoute.STAGE_CLOUD_SNAPSHOT,
            QuotaBackupSelection.routeRestore(QuotaBackupSelection.CLOUD_SNAPSHOT_PATH))
        assertEquals(QuotaBackupSelection.RestoreRoute.STAGE_FULL_TREE_RESTORE,
            QuotaBackupSelection.routeRestore("saves_v2/$a/battery.sav"))
        assertEquals(QuotaBackupSelection.RestoreRoute.STAGE_FULL_TREE_RESTORE,
            QuotaBackupSelection.routeRestore("sharedpref/dualdex_legacy_catalog.xml"))
        assertEquals(QuotaBackupSelection.RestoreRoute.REJECT, QuotaBackupSelection.routeRestore("rom_cache/game.gba"))
    }

    @Test fun inputOrderDoesNotChangeSelectionAndNoRomIsDroppedForOptionalData() {
        val records = canonical(a) + canonical(b) + QuotaBackupSelection.Candidate(
            a, QuotaBackupSelection.Category.STATE, "saves_v2/$a/slot1.state", 900, 2
        )
        val forward = select(setOf(a, b), records, 220)
        val reversed = select(setOf(a, b), records.reversed(), 220)
        assertEquals(forward.selected, reversed.selected)
        assertEquals(setOf(a, b), forward.coveredRomIdentities)
        assertEquals(QuotaBackupSelection.Verdict.PARTIAL, forward.verdict)
    }

    @Test fun snapshotNeedsContentHashesExactManifestAndBoundCompletionMarker() {
        val before = listOf(QuotaBackupSelection.SourceStamp("saves_v2/$a/battery.sav", 100, 1, hash1))
        val manifest = QuotaBackupSelection.manifestSha256(before)
        val contentSet = QuotaBackupSelection.contentSetSha256(before)
        assertTrue(QuotaBackupSelection.snapshotUsable(true, before, before, manifest, manifest, contentSet))
        assertFalse(QuotaBackupSelection.snapshotUsable(false, before, before, manifest, manifest, contentSet))

        val changedSameSizeAndTime = listOf(before.single().copy(contentSha256 = hash2))
        assertFalse(QuotaBackupSelection.snapshotUsable(
            true, before, changedSameSizeAndTime, manifest, manifest, contentSet
        ))
        assertFalse(QuotaBackupSelection.snapshotUsable(
            true, before, before, manifest, hash2, contentSet
        ))
        assertFalse(QuotaBackupSelection.snapshotUsable(
            true, before, before, manifest, manifest, hash2
        ))
    }
}
