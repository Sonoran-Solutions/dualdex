package com.dualdex.emulator
import com.dualdex.emulator.backup.QuotaBackupSelection

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
        val sourceRoot = java.nio.file.Files.createTempDirectory("legacy-source").toFile()
        val targetRoot = java.nio.file.Files.createTempDirectory("legacy-target").toFile()
        try {
            val sourceFile = java.io.File(sourceRoot, "saves/FireRed.sav").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val restoredFile = java.io.File(targetRoot, "saves/FireRed.sav").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(sourceFile.readBytes())
                .joinToString("") { "%02x".format(it) }
            val binding = QuotaBackupSelection.portableLegacyBinding(sourceRoot.path, sourceFile.path, digest, a, "battery.sav")!!
            assertNotEquals(sourceFile.path, restoredFile.path)
            val destination = QuotaBackupSelection.portableLegacyDestination(binding, targetRoot.path, restoredFile.path, digest)
            assertEquals("saves_v2/$a/battery.sav", destination)
            assertNull(QuotaBackupSelection.portableLegacyDestination(binding, targetRoot.path,
                java.io.File(targetRoot, "saves/Emerald.sav").path, digest))
            assertNull(QuotaBackupSelection.portableLegacyDestination(binding, targetRoot.path, restoredFile.path, hash2))
            assertNull(QuotaBackupSelection.portableLegacyBinding(sourceRoot.path,
                java.io.File(sourceRoot, "rom_cache/game.sav").path, digest, a, "battery.sav"))
            assertNull(QuotaBackupSelection.portableLegacyBinding(sourceRoot.path,
                java.io.File(sourceRoot, "random.dat").path, digest, a, "battery.sav"))
            assertNull(QuotaBackupSelection.portableLegacyBinding(sourceRoot.path, sourceFile.path, "bad", a, "battery.sav"))
            assertNull(QuotaBackupSelection.portableLegacyBinding(sourceRoot.path, sourceFile.path, digest, a, "slot1.state"))
            sourceFile.writeBytes(byteArrayOf(9, 9, 9))
            assertNull(QuotaBackupSelection.portableLegacyBinding(sourceRoot.path, sourceFile.path, digest, a, "battery.sav"))
        } finally { sourceRoot.deleteRecursively(); targetRoot.deleteRecursively() }
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
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, result.verdict)
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

    @Test fun explicitZeroQuotaRefusesButUnknownQuotaUsesConservativeCap() {
        assertEquals(0L, QuotaBackupSelection.budgetFor(0L))
        assertEquals(0L, QuotaBackupSelection.budgetFor(-1L))
        assertEquals(QuotaBackupSelection.DEFAULT_BUDGET_BYTES, QuotaBackupSelection.budgetFor(null))
    }

    @Test fun checkedTotalsRejectOverflowAndOptionalSelectionStaysWithinBudget() {
        assertEquals(Long.MAX_VALUE, QuotaBackupSelection.checkedTotalBytes(listOf(Long.MAX_VALUE)))
        assertNull(QuotaBackupSelection.checkedTotalBytes(listOf(Long.MAX_VALUE, 1L)))
        val result = select(setOf(a), canonical(a) + listOf(
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.STATE, "saves_v2/$a/one.state", 900, 1),
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.STATE, "saves_v2/$a/two.state", 900, 2)
        ), budget = 1_000)
        assertTrue(result.selectedBytes <= 1_000)
        assertTrue(result.allEssentialSavesCovered)
    }

    @Test fun malformedSizesAndExcessiveEntriesFailClosed() {
        val huge = canonical(a, Long.MAX_VALUE)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, select(setOf(a), huge).verdict)
        val empty = canonical(a, 0)
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, select(setOf(a), empty).verdict)
        val tooMany = (0..QuotaBackupSelection.MAX_ENTRIES).map { index ->
            QuotaBackupSelection.Candidate(a, QuotaBackupSelection.Category.STATE, "saves_v2/$a/$index.state", 1, index.toLong())
        }
        assertEquals(QuotaBackupSelection.Verdict.REFUSAL, select(setOf(a), canonical(a) + tooMany).verdict)
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
