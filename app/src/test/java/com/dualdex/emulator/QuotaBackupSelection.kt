package com.dualdex.emulator

/** Host-only design prototype. It has no Android or filesystem access. */
object QuotaBackupSelection {
    const val DEFAULT_BUDGET_BYTES = 20L * 1024 * 1024

    enum class Category { CANONICAL_BATTERY, SAVE_METADATA, VERIFIED_RECOVERY_BATTERY, VERIFIED_LEGACY_BATTERY, STATE, THUMBNAIL }
    enum class Verdict { SUCCESS, PARTIAL, REFUSAL }

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
        val allEssentialSavesCovered: Boolean,
        val budgetExceeded: Boolean,
        val verdict: Verdict
    )

    data class SourceStamp(val relativePath: String, val sizeBytes: Long, val modifiedAtMillis: Long)

    /** A partial build or source changed after selection can never be committed as a usable snapshot. */
    fun snapshotUsable(
        completionMarkerPresent: Boolean,
        selectedSources: List<SourceStamp>,
        currentSources: List<SourceStamp>
    ): Boolean = completionMarkerPresent && selectedSources.isNotEmpty() &&
        selectedSources.all { it in currentSources }

    fun select(
        expectedRomIdentities: Set<String>,
        candidates: List<Candidate>,
        budgetBytes: Long = DEFAULT_BUDGET_BYTES
    ): Result {
        require(budgetBytes >= 0) { "budget must not be negative" }
        val exclusions = linkedMapOf<Candidate, String>()
        val valid = candidates.filter { candidate ->
            val reason = invalidReason(candidate, expectedRomIdentities)
            if (reason != null) exclusions[candidate] = reason
            reason == null
        }
        val duplicateGroups = valid.groupBy { it.romSha256.lowercase() to it.relativePath }
            .filterValues { it.size > 1 }
        val duplicates = duplicateGroups.values.flatten().toSet()
        duplicates.forEach { exclusions[it] = "duplicate source record" }
        val unique = valid.filterNot { it in duplicates }

        val essentials = linkedMapOf<String, List<Candidate>>()
        val unprotected = linkedSetOf<String>()
        for (identity in expectedRomIdentities.map(String::lowercase).sorted()) {
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

        val essentialBytes = essentials.values.flatten().sumOf { it.sizeBytes }
        val allExpectedProtected = expectedRomIdentities.all { it.lowercase() in essentials }
        if (!allExpectedProtected || essentialBytes > budgetBytes) {
            unique.filter { it !in essentials.values.flatten() }.forEach {
                exclusions.putIfAbsent(it, "essential coverage refused")
            }
            essentials.values.flatten().forEach { exclusions[it] = "essential collection refused as a whole" }
            return Result(
                emptyList(), exclusions, 0, emptySet(), expectedRomIdentities.map(String::lowercase).toSortedSet(),
                false, essentialBytes > budgetBytes, Verdict.REFUSAL
            )
        }

        val selected = essentials.values.flatten().toMutableList()
        var remaining = budgetBytes - essentialBytes
        val optional = unique.asSequence().filter { it !in selected }
            .sortedWith(compareBy<Candidate>({ it.category.ordinal }, { it.romSha256.lowercase() }, { it.relativePath }, { it.modifiedAtMillis }))
        for (candidate in optional) {
            if (candidate.sizeBytes <= remaining) {
                selected += candidate
                remaining -= candidate.sizeBytes
            } else {
                exclusions[candidate] = "optional data does not fit"
            }
        }
        val selectedSorted = selected.distinct().sortedWith(candidateOrder)
        val bytes = selectedSorted.sumOf { it.sizeBytes }
        val optionalOmitted = selectedSorted.size < unique.size
        return Result(
            selectedSorted, exclusions, bytes, essentials.keys.toSortedSet(), emptySet(), true,
            bytes > budgetBytes, if (optionalOmitted) Verdict.PARTIAL else Verdict.SUCCESS
        )
    }

    private val candidateOrder = compareBy<Candidate>({ it.romSha256.lowercase() }, { it.category.ordinal }, { it.relativePath }, { it.modifiedAtMillis })

    private fun invalidReason(candidate: Candidate, expected: Set<String>): String? {
        val id = candidate.romSha256
        if (id !in expected && expected.none { it.equals(id, ignoreCase = true) }) return "unexpected ROM identity"
        if (!id.matches(Regex("[0-9a-fA-F]{64}"))) return "malformed ROM SHA-256"
        if (candidate.sizeBytes <= 0) return "invalid file size"
        if (!candidate.integrityValid) return "integrity check failed"
        val path = candidate.relativePath
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\') || '\\' in path ||
            path.split('/').any { it.isBlank() || it == "." || it == ".." }) return "unsafe relative path"
        if (path.substringBefore('/') !in setOf("saves_v2", "saves", "saves_fallback")) return "source root is not allowlisted"
        if (path.substringAfterLast('/').endsWith(".gba", ignoreCase = true) ||
            path.substringAfterLast('/').endsWith(".gb", ignoreCase = true) ||
            path.substringAfterLast('/').endsWith(".gbc", ignoreCase = true) ||
            path.substringAfterLast('/').endsWith(".nds", ignoreCase = true)) return "ROM payload forbidden"
        if (candidate.category in setOf(Category.VERIFIED_RECOVERY_BATTERY, Category.VERIFIED_LEGACY_BATTERY) && !candidate.assignmentVerified) {
            return "legacy/recovery assignment is unverified"
        }
        if (candidate.category == Category.CANONICAL_BATTERY &&
            !path.equals("saves_v2/$id/battery.sav", ignoreCase = true)) return "canonical path does not match identity"
        if (candidate.category == Category.SAVE_METADATA &&
            !path.equals("saves_v2/$id/metadata.json", ignoreCase = true)) return "metadata path does not match identity"
        if (candidate.category in setOf(Category.VERIFIED_RECOVERY_BATTERY, Category.VERIFIED_LEGACY_BATTERY) &&
            !path.endsWith(".sav", ignoreCase = true)) return "recovery candidate is not a battery save"
        if (candidate.category == Category.STATE && !path.endsWith(".state", ignoreCase = true)) return "state candidate has wrong file type"
        return null
    }
}
