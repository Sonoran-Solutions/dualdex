package com.dualdex.emulator.backup

import java.security.MessageDigest
import java.io.File

/** Deterministic, bounded selection and routing rules shared by inventory, archive, and restore code. */
object QuotaBackupSelection {
    const val MAX_PAYLOAD_BYTES = 20L * 1024 * 1024
    const val CONTAINER_RESERVE_BYTES = 256L * 1024
    const val DEFAULT_BUDGET_BYTES = MAX_PAYLOAD_BYTES - CONTAINER_RESERVE_BYTES
    const val MAX_ENTRIES = 1024
    const val MAX_MANIFEST_BYTES = 1024L * 1024
    const val MAX_PATH_CHARS = 1024
    const val MAX_ENTRY_BYTES = MAX_PAYLOAD_BYTES

    enum class Category { CANONICAL_BATTERY, SAVE_METADATA, VERIFIED_RECOVERY_BATTERY, VERIFIED_LEGACY_BATTERY, STATE, THUMBNAIL }
    enum class Verdict { SUCCESS, PARTIAL, REFUSAL }
    enum class BackupRoute { CLOUD_SNAPSHOT, DEVICE_TRANSFER, REFUSAL }
    enum class RestoreRoute { STAGE_CLOUD_SNAPSHOT, STAGE_FULL_TREE_RESTORE, REJECT }

    data class Candidate(
        val romSha256: String,
        val category: Category,
        val relativePath: String,
        val sizeBytes: Long,
        val modifiedAtMillis: Long,
        val integrityValid: Boolean = true,
        val assignmentVerified: Boolean = false
    )

    data class Result(
        val selected: List<Candidate>,
        val excluded: Map<Candidate, String>,
        val selectedBytes: Long,
        val coveredRomIdentities: Set<String>,
        val unprotectedRomIdentities: Set<String>,
        val unassignableSourcePaths: Set<String>,
        val inventoryComplete: Boolean,
        val allEssentialSavesCovered: Boolean,
        val budgetExceeded: Boolean,
        val verdict: Verdict
    )

    data class SourceStamp(
        val relativePath: String,
        val sizeBytes: Long,
        val modifiedAtMillis: Long,
        val contentSha256: String
    )

    data class PortableLegacyBinding(
        val archiveEntry: String,
        val contentSha256: String,
        val romSha256: String,
        val targetFileName: String
    )


    fun checkedTotalBytes(sizes: Iterable<Long>): Long? = sumChecked(sizes)

    fun isPortableLegacyPath(path: String): Boolean = isApprovedLegacySavePath(path)

    fun isAllowedSourcePath(appFilesRoot: String, sourceFilePath: String, expectedRelativePath: String): Boolean {
        val relative = relativePathUnder(appFilesRoot, sourceFilePath) ?: return false
        if (relative != expectedRelativePath) return false
        return when {
            relative.startsWith("saves_v2/") -> relative.split('/').size >= 3
            relative.startsWith("saves/") || relative.startsWith("saves_fallback/") ->
                isApprovedLegacySavePath(relative) || relative.endsWith(".state", ignoreCase = true)
            else -> false
        }
    }

    fun portableLegacyBinding(
        appFilesRoot: String,
        sourceFilePath: String,
        contentSha256: String,
        romSha256: String,
        targetFileName: String
    ): PortableLegacyBinding? {
        val source = File(sourceFilePath)
        val archiveEntry = relativePathUnder(appFilesRoot, sourceFilePath) ?: return null
        if (!isApprovedLegacySavePath(archiveEntry) || !validSha(contentSha256) || !validSha(romSha256) ||
            !safeTargetName(targetFileName) || !source.isFile || source.length() !in 1..MAX_ENTRY_BYTES) return null
        val actualDigest = hashFileBounded(source) ?: return null
        if (!actualDigest.equals(contentSha256, ignoreCase = true)) return null
        return PortableLegacyBinding(archiveEntry, actualDigest, romSha256.lowercase(), targetFileName)
    }

    fun portableLegacyDestination(
        binding: PortableLegacyBinding,
        appFilesRoot: String,
        restoredFilePath: String,
        restoredContentSha256: String
    ): String? {
        val restoredFile = File(restoredFilePath)
        val restoredArchiveEntry = relativePathUnder(appFilesRoot, restoredFilePath) ?: return null
        val actualDigest = hashFileBounded(restoredFile) ?: return null
        if (!isApprovedLegacySavePath(binding.archiveEntry) || !isApprovedLegacySavePath(restoredArchiveEntry) ||
            binding.archiveEntry != restoredArchiveEntry || !validSha(binding.contentSha256) ||
            !binding.contentSha256.equals(restoredContentSha256, ignoreCase = true) ||
            !binding.contentSha256.equals(actualDigest, ignoreCase = true) || !validSha(binding.romSha256) ||
            !safeTargetName(binding.targetFileName)) return null
        return "saves_v2/${binding.romSha256.lowercase()}/${binding.targetFileName}"
    }

    fun budgetFor(reportedQuotaBytes: Long?): Long {
        val usableQuota = when {
            reportedQuotaBytes == null -> MAX_PAYLOAD_BYTES
            reportedQuotaBytes <= 0 -> return 0
            else -> reportedQuotaBytes / 5 * 4 + reportedQuotaBytes % 5 * 4 / 5
        }
        return (minOf(MAX_PAYLOAD_BYTES, usableQuota) - CONTAINER_RESERVE_BYTES).coerceAtLeast(0)
    }

    fun dispatch(clientSideEncryption: Boolean, deviceToDeviceTransfer: Boolean): BackupRoute = when {
        deviceToDeviceTransfer -> BackupRoute.DEVICE_TRANSFER
        clientSideEncryption -> BackupRoute.CLOUD_SNAPSHOT
        else -> BackupRoute.REFUSAL
    }

    /** On API 28–34 restore callbacks lack transport flags; the unique archive path identifies new cloud payloads. */
    fun routeRestore(relativePath: String): RestoreRoute {
        if (!safeRelativePath(relativePath)) return RestoreRoute.REJECT
        return when {
        relativePath == CLOUD_SNAPSHOT_PATH -> RestoreRoute.STAGE_CLOUD_SNAPSHOT
        relativePath.startsWith("saves_v2/") || relativePath.startsWith("saves/") ||
            relativePath.startsWith("saves_fallback/") || relativePath == "sharedpref/dualdex_legacy_catalog.xml" -> RestoreRoute.STAGE_FULL_TREE_RESTORE
        else -> RestoreRoute.REJECT
        }
    }

    fun snapshotUsable(
        completionMarkerPresent: Boolean,
        selectedSources: List<SourceStamp>,
        copiedSources: List<SourceStamp>,
        expectedManifestSha256: String,
        markerManifestSha256: String,
        markerContentSetSha256: String
    ): Boolean {
        if (!completionMarkerPresent || selectedSources.isEmpty() || !validSha(expectedManifestSha256) ||
            !validSha(markerManifestSha256) || !validSha(markerContentSetSha256)) return false
        if (selectedSources.any { !validStamp(it) } || copiedSources.any { !validStamp(it) }) return false
        if (selectedSources.map { it.relativePath }.distinct().size != selectedSources.size ||
            copiedSources.map { it.relativePath }.distinct().size != copiedSources.size) return false
        if (selectedSources.sortedBy { it.relativePath } != copiedSources.sortedBy { it.relativePath }) return false
        val copiedManifest = manifestSha256(copiedSources)
        return expectedManifestSha256.equals(copiedManifest, ignoreCase = true) &&
            markerManifestSha256.equals(copiedManifest, ignoreCase = true) &&
            markerContentSetSha256.equals(contentSetSha256(copiedSources), ignoreCase = true)
    }

    fun manifestSha256(sources: List<SourceStamp>): String = sha256(
        sources.sortedBy { it.relativePath }.joinToString("\n") {
            "${it.relativePath}\t${it.sizeBytes}\t${it.modifiedAtMillis}\t${it.contentSha256.lowercase()}"
        }.toByteArray(Charsets.UTF_8)
    )

    fun contentSetSha256(sources: List<SourceStamp>): String = sha256(
        sources.sortedBy { it.relativePath }.joinToString("\n") {
            "${it.relativePath}\t${it.contentSha256.lowercase()}"
        }.toByteArray(Charsets.UTF_8)
    )

    fun select(
        expectedRomIdentities: Set<String>,
        candidates: List<Candidate>,
        inventoryComplete: Boolean,
        budgetBytes: Long = DEFAULT_BUDGET_BYTES
    ): Result {
        require(budgetBytes >= 0) { "budget must not be negative" }
        val exclusions = linkedMapOf<Candidate, String>()
        if (candidates.size > MAX_ENTRIES || expectedRomIdentities.size > MAX_ENTRIES || budgetBytes > MAX_PAYLOAD_BYTES) {
            return Result(emptyList(), emptyMap(), 0, emptySet(), expectedRomIdentities.toSortedSet(),
                emptySet(), false, false, false, Verdict.REFUSAL)
        }
        val unassignable = linkedSetOf<String>()
        val discovered = expectedRomIdentities.map(String::lowercase).toMutableSet()
        if (discovered.isEmpty() && candidates.isEmpty()) {
            return Result(emptyList(), emptyMap(), 0, emptySet(), emptySet(), emptySet(), inventoryComplete,
                false, false, Verdict.REFUSAL)
        }
        candidates.forEach { candidate ->
            canonicalDirectoryIdentity(candidate)?.let(discovered::add)
            if (candidate.category in setOf(Category.VERIFIED_RECOVERY_BATTERY, Category.VERIFIED_LEGACY_BATTERY) &&
                candidate.assignmentVerified && candidate.romSha256.matches(HASH)) discovered += candidate.romSha256.lowercase()
            if (isSaveSource(candidate) && candidate.category in setOf(Category.VERIFIED_RECOVERY_BATTERY, Category.VERIFIED_LEGACY_BATTERY) &&
                (!candidate.assignmentVerified || !candidate.romSha256.matches(HASH))) {
                unassignable += candidate.relativePath
            }
        }

        val valid = candidates.filter { candidate ->
            val reason = invalidReason(candidate, discovered)
            if (reason != null) {
                exclusions[candidate] = reason
                if (isSaveSource(candidate) && reason !in setOf("invalid file size", "integrity check failed")) {
                    unassignable += candidate.relativePath
                }
            }
            reason == null
        }
        val pathGroups = valid.groupBy { it.relativePath.lowercase() }
        val conflicts = pathGroups.filterValues { records ->
            records.size > 1 || records.map { it.romSha256.lowercase() }.distinct().size > 1
        }
        conflicts.forEach { (_, records) ->
            val reason = if (records.map { it.romSha256.lowercase() }.distinct().size > 1) "source path has conflicting ROM ownership" else "duplicate source record"
            records.forEach {
                exclusions[it] = reason
                if (isSaveSource(it)) unassignable += it.relativePath
            }
        }
        val unique = valid.filter { it.relativePath.lowercase() !in conflicts }

        val essentials = linkedMapOf<String, List<Candidate>>()
        val unprotected = linkedSetOf<String>()
        for (identity in discovered.sorted()) {
            val records = unique.filter { it.romSha256.equals(identity, ignoreCase = true) }
            val metadata = records.filter { it.category == Category.SAVE_METADATA }
            if (metadata.size != 1) {
                unprotected += identity
                metadata.forEach { exclusions[it] = "metadata missing or ambiguous" }
                continue
            }
            val canonical = records.filter { it.category == Category.CANONICAL_BATTERY }
            val recovery = records.filter {
                it.category == Category.VERIFIED_RECOVERY_BATTERY || it.category == Category.VERIFIED_LEGACY_BATTERY
            }
            val save = when {
                canonical.size == 1 -> canonical.single()
                canonical.size > 1 -> null
                recovery.isNotEmpty() -> recovery.sortedWith(candidateOrder).first()
                else -> null
            }
            if (save == null) {
                unprotected += identity
                canonical.forEach { exclusions[it] = "canonical save is ambiguous" }
                recovery.forEach { exclusions[it] = "no unambiguous essential save" }
            } else {
                essentials[identity] = listOf(save, metadata.single()).sortedWith(candidateOrder)
            }
        }

        val essentialBytes = sumChecked(essentials.values.flatten().map { it.sizeBytes })
        val essentialsComplete = discovered.all { it in essentials } && unassignable.isEmpty()
        val essentialOverBudget = essentialBytes?.let { it > budgetBytes } ?: true
        if (!inventoryComplete || !essentialsComplete || essentialOverBudget) {
            unique.filter { it !in essentials.values.flatten() }.forEach {
                exclusions.putIfAbsent(it, "essential coverage refused")
            }
            essentials.values.flatten().forEach { exclusions[it] = "essential collection refused as a whole" }
            return Result(
                emptyList(), exclusions, 0, emptySet(), discovered.toSortedSet(),
                unassignable.toSortedSet(), inventoryComplete, false, essentialOverBudget, Verdict.REFUSAL
            )
        }

        val requiredBytes = essentialBytes ?: return Result(
            emptyList(), exclusions, 0, emptySet(), discovered.toSortedSet(), unassignable.toSortedSet(),
            inventoryComplete, false, true, Verdict.REFUSAL
        )
        val selected = essentials.values.flatten().toMutableList()
        var remaining = budgetBytes - requiredBytes
        val optional = unique.asSequence().filter { it !in selected }
            .sortedWith(compareBy<Candidate>({ it.category.ordinal }, { it.romSha256.lowercase() }, { it.relativePath }, { it.modifiedAtMillis }))
        for (candidate in optional) {
            if (candidate.sizeBytes <= remaining && checkedAdd(selectedBytes(selected), candidate.sizeBytes) != null) {
                selected += candidate
                remaining -= candidate.sizeBytes
            } else {
                exclusions[candidate] = "optional data does not fit"
            }
        }
        val selectedSorted = selected.distinct().sortedWith(candidateOrder)
        val bytes = sumChecked(selectedSorted.map { it.sizeBytes }) ?: return Result(
            emptyList(), exclusions, 0, emptySet(), discovered.toSortedSet(), unassignable.toSortedSet(),
            inventoryComplete, false, true, Verdict.REFUSAL
        )
        val optionalOmitted = selectedSorted.size < unique.size || exclusions.isNotEmpty()
        return Result(
            selectedSorted, exclusions, bytes, essentials.keys.toSortedSet(), emptySet(), emptySet(), true,
            true, bytes > budgetBytes, if (optionalOmitted) Verdict.PARTIAL else Verdict.SUCCESS
        )
    }

    private val candidateOrder = compareBy<Candidate>({ it.romSha256.lowercase() }, { it.category.ordinal }, { it.relativePath }, { it.modifiedAtMillis })

    private fun canonicalDirectoryIdentity(candidate: Candidate): String? {
        val parts = candidate.relativePath.split('/')
        return if (candidate.category == Category.CANONICAL_BATTERY && parts.size == 3 && parts[0] == "saves_v2" &&
            parts[2] == "battery.sav" && parts[1].matches(HASH)) parts[1].lowercase() else null
    }

    private fun isSaveSource(candidate: Candidate): Boolean = candidate.category in setOf(
        Category.CANONICAL_BATTERY, Category.VERIFIED_RECOVERY_BATTERY, Category.VERIFIED_LEGACY_BATTERY, Category.STATE
    ) || candidate.relativePath.endsWith(".sav", ignoreCase = true) || candidate.relativePath.endsWith(".state", ignoreCase = true)

    private fun invalidReason(candidate: Candidate, expected: Set<String>): String? {
        val id = candidate.romSha256
        if (!id.matches(HASH)) return "malformed ROM SHA-256"
        if (candidate.category in setOf(Category.VERIFIED_RECOVERY_BATTERY, Category.VERIFIED_LEGACY_BATTERY) && !candidate.assignmentVerified) {
            return "legacy/recovery assignment is unverified"
        }
        if (candidate.sizeBytes <= 0 || candidate.sizeBytes > MAX_ENTRY_BYTES) return "invalid file size"
        if (!candidate.integrityValid) return "integrity check failed"
        val path = candidate.relativePath
        if (path.length > MAX_PATH_CHARS) return "path exceeds limit"
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\') || '\\' in path ||
            path.split('/').any { it.isBlank() || it == "." || it == ".." }) return "unsafe relative path"
        if (path.substringBefore('/') !in setOf("saves_v2", "saves", "saves_fallback")) return "source root is not allowlisted"
        if (path.substringAfterLast('/').endsWithAny(".gba", ".gb", ".gbc", ".nds")) return "ROM payload forbidden"
        val parts = path.split('/')
        if (path.startsWith("saves_v2/") && (parts.size < 3 || !parts[1].equals(id, ignoreCase = true))) {
            return "source path does not match ROM identity"
        }
        if (candidate.category == Category.CANONICAL_BATTERY &&
            !path.equals("saves_v2/$id/battery.sav", ignoreCase = true)) return "canonical path does not match identity"
        if (candidate.category == Category.SAVE_METADATA &&
            !path.equals("saves_v2/$id/metadata.json", ignoreCase = true)) return "metadata path does not match identity"
        if (candidate.category in setOf(Category.VERIFIED_RECOVERY_BATTERY, Category.VERIFIED_LEGACY_BATTERY) &&
            !path.endsWith(".sav", ignoreCase = true)) return "recovery candidate is not a battery save"
        if (candidate.category == Category.STATE && (!path.endsWith(".state", ignoreCase = true) ||
                (path.startsWith("saves_v2/") && parts.size != 3))) return "state candidate has wrong file type"
        if (candidate.category == Category.STATE && !path.startsWith("saves_v2/") && !candidate.assignmentVerified) {
            return "legacy state assignment is unverified"
        }
        if (candidate.category == Category.THUMBNAIL && (!path.substringAfterLast('/').endsWithAny(".png", ".jpg", ".jpeg", ".webp") ||
                (path.startsWith("saves_v2/") && parts.size != 3))) {
            return "thumbnail candidate has wrong file type"
        }
        if (id.lowercase() !in expected) return "unexpected ROM identity"
        return null
    }

    private fun validStamp(source: SourceStamp): Boolean = source.sizeBytes in 1..MAX_ENTRY_BYTES &&
        validSha(source.contentSha256) && source.relativePath.length <= MAX_PATH_CHARS && safeRelativePath(source.relativePath)

    private fun safeRelativePath(path: String): Boolean = path.isNotBlank() && path.length <= MAX_PATH_CHARS && !path.startsWith('/') && !path.startsWith('\\') &&
        '\\' !in path && path.split('/').none { it.isBlank() || it == "." || it == ".." }

    private fun safeTargetName(name: String): Boolean = name.isNotBlank() && name != "." && name != ".." &&
        '/' !in name && '\\' !in name && name.none { it == '\u0000' } && name == "battery.sav"

    private fun relativePathUnder(root: String, file: String): String? = runCatching {
        val rootFile = File(root).absoluteFile.normalize()
        val fileFile = File(file).absoluteFile.normalize()
        if (hasSymlinkBetween(rootFile, fileFile)) return@runCatching null
        val rootPath = rootFile.canonicalFile.toPath()
        val filePath = fileFile.canonicalFile.toPath()
        if (!filePath.startsWith(rootPath)) null
        else rootPath.relativize(filePath).toString().replace(File.separatorChar, '/')
    }.getOrNull()?.takeIf(::safeRelativePath)

    private fun hasSymlinkBetween(root: File, file: File): Boolean = runCatching {
        val rootPath = root.toPath()
        val filePath = file.toPath()
        if (!filePath.startsWith(rootPath)) return@runCatching true
        var current = rootPath
        for (part in rootPath.relativize(filePath)) {
            current = current.resolve(part)
            if (java.nio.file.Files.isSymbolicLink(current)) return@runCatching true
        }
        false
    }.getOrDefault(true)

    private fun isApprovedLegacySavePath(path: String): Boolean = safeRelativePath(path) &&
        (path.startsWith("saves/") || path.startsWith("saves_fallback/")) &&
        path.substringAfterLast('/').endsWith(".sav", ignoreCase = true) &&
        path.split('/').size in 2..8

    private fun checkedAdd(left: Long, right: Long): Long? =
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) null else left + right

    private fun sumChecked(values: Iterable<Long>): Long? {
        var total = 0L
        for (value in values) total = checkedAdd(total, value) ?: return null
        return total
    }

    private fun selectedBytes(values: List<Candidate>): Long = sumChecked(values.map { it.sizeBytes }) ?: Long.MAX_VALUE


    private fun hashFileBounded(file: File): String? = runCatching {
        val expected = file.length()
        if (expected !in 1..MAX_ENTRY_BYTES) return null
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                total = checkedAdd(total, count.toLong()) ?: return null
                if (total > expected || total > MAX_ENTRY_BYTES) return null
                md.update(buffer, 0, count)
            }
        }
        if (total == expected && file.length() == expected) md.digest().toHex() else null
    }.getOrNull()

    private fun validSha(value: String): Boolean = value.matches(HASH)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.endsWithAny(vararg endings: String): Boolean = endings.any { endsWith(it, ignoreCase = true) }

    private val HASH = Regex("[0-9a-fA-F]{64}")
    const val CLOUD_SNAPSHOT_PATH = "cloud_backup/dualdex-snapshot-v1.zip"
}
