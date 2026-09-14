package com.dualdex.emulator.storage

import android.content.Context
import android.util.Log
import com.dualdex.emulator.RomIdentity
import java.io.File
import java.util.Locale

open class LegacySaveCatalog(
    private val context: Context? = null,
    private val customLegacyDir: File? = null,
    private val customFallbackDir: File? = null
) {

    private val memoryAssignments = mutableMapOf<String, String>()
    private val prefs by lazy {
        context?.getSharedPreferences("dualdex_legacy_catalog", Context.MODE_PRIVATE)
    }

    private val legacyDir: File
        get() = customLegacyDir ?: File(context?.filesDir, "saves")

    private val fallbackDir: File
        get() = customFallbackDir ?: File(context?.filesDir, "saves_fallback")

    /**
     * Scan available legacy save files and generate suggestions without mutating or claiming them.
     */
    fun discoverCandidates(): List<LegacyCandidate> {
        val candidates = mutableListOf<LegacyCandidate>()
        val seenSources = mutableSetOf<String>()

        // 1. Scan app-internal legacy dir (filesDir/saves)
        if (legacyDir.exists() && legacyDir.isDirectory) {
            val files = legacyDir.listFiles { f ->
                f.isFile && f.length() > 0L &&
                    !f.name.endsWith(".migrated.bak") &&
                    !f.name.endsWith(".tmp") &&
                    !f.name.endsWith(".orig.bak") &&
                    (f.name.endsWith(".sav") || f.name.endsWith(".state"))
            } ?: emptyArray()

            for (file in files) {
                if (seenSources.add(file.absolutePath)) {
                    val candidate = parseCandidate(file)
                    candidates.add(candidate)
                }
            }
        }

        // 2. Scan legacy staged folders in saves_fallback
        if (fallbackDir.exists() && fallbackDir.isDirectory) {
            val legacySubdirs = fallbackDir.listFiles { f ->
                f.isDirectory && f.name.startsWith("legacy_")
            } ?: emptyArray()

            for (dir in legacySubdirs) {
                val base = dir.name.removePrefix("legacy_")
                val files = dir.listFiles { f ->
                    f.isFile && f.length() > 0L &&
                        !f.name.endsWith(".migrated.bak") &&
                        !f.name.endsWith(".tmp")
                } ?: emptyArray()

                for (file in files) {
                    if (seenSources.add(file.absolutePath)) {
                        candidates.add(
                            LegacyCandidate(
                                sourceFile = file,
                                baseName = base,
                                suggestedTitle = base.replace('_', ' '),
                                targetFileName = file.name,
                                sizeBytes = file.length(),
                                isAssigned = isSourceAssigned(file),
                                assignedRomHash = getAssignedRomHash(file)
                            )
                        )
                    }
                }
            }
        }

        return candidates
    }

    private fun parseCandidate(file: File): LegacyCandidate {
        val name = file.name
        val baseName = when {
            name.endsWith(".sav") -> name.removeSuffix(".sav")
            name.contains("_slot_") -> name.substringBefore("_slot_")
            name.endsWith("_quicksave.state") -> name.removeSuffix("_quicksave.state")
            name.endsWith(".state") -> name.removeSuffix(".state")
            else -> name.substringBeforeLast(".")
        }

        val targetFileName = when {
            name.endsWith(".sav") -> "battery.sav"
            name.contains("_slot_") -> "slot_${name.substringAfter("_slot_")}"
            name.endsWith("_quicksave.state") -> "quicksave.state"
            else -> name
        }

        val suggestedTitle = if (baseName.equals("current_game", ignoreCase = true)) {
            "Unlabeled Save (current_game)"
        } else {
            baseName.replace('_', ' ')
        }

        return LegacyCandidate(
            sourceFile = file,
            baseName = baseName,
            suggestedTitle = suggestedTitle,
            targetFileName = targetFileName,
            sizeBytes = file.length(),
            isAssigned = isSourceAssigned(file),
            assignedRomHash = getAssignedRomHash(file)
        )
    }

    /**
     * Check if a candidate file matches the given ROM identity suggestions.
     * Note: This is an advisory suggestion only, NEVER an automatic ownership claim.
     */
    fun isSuggestedMatch(candidate: LegacyCandidate, identity: RomIdentity, profileName: String? = null): Boolean {
        // current_game is ambiguous and must never be auto-suggested as a high-confidence match
        if (candidate.baseName.equals("current_game", ignoreCase = true)) {
            return false
        }

        fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        val baseNorm = norm(candidate.baseName)
        if (baseNorm.length < 3) return false

        val titleNorm = norm(identity.displayName)
        val profileNorm = profileName?.let { norm(it) }.orEmpty()

        return (titleNorm.isNotEmpty() && (titleNorm.contains(baseNorm) || baseNorm.contains(titleNorm))) ||
            (profileNorm.isNotEmpty() && (profileNorm.contains(baseNorm) || baseNorm.contains(profileNorm)))
    }

    open fun isSourceAssigned(file: File): Boolean {
        val key = "assigned_${file.absolutePath.hashCode()}"
        return memoryAssignments.containsKey(key) || prefs?.contains(key) == true
    }

    open fun getAssignedRomHash(file: File): String? {
        val key = "assigned_${file.absolutePath.hashCode()}"
        return memoryAssignments[key] ?: prefs?.getString(key, null)
    }

    /**
     * Explicitly migrate a legacy candidate to a target canonical directory.
     * Fails closed: only marks source migrated after destination commit succeeds and is verified.
     */
    open fun assignCandidateToRom(
        candidate: LegacyCandidate,
        targetRomIdentity: RomIdentity,
        canonicalDir: File
    ): Boolean {
        if (!targetRomIdentity.isValid) {
            Log.e(TAG, "Cannot migrate to invalid target ROM identity")
            return false
        }

        val source = candidate.sourceFile
        if (!source.exists() || source.length() == 0L) {
            Log.e(TAG, "Source legacy file does not exist: ${source.absolutePath}")
            return false
        }

        // Check if already claimed by a different ROM hash
        val currentAssignedHash = getAssignedRomHash(source)
        if (currentAssignedHash != null && !currentAssignedHash.equals(targetRomIdentity.sha256, ignoreCase = true)) {
            Log.e(TAG, "Source ${source.name} is already assigned to ROM $currentAssignedHash, rejecting silent overwrite to ${targetRomIdentity.sha256}")
            return false
        }

        val targetFile = File(canonicalDir, candidate.targetFileName)
        val expectedSize = source.length()

        // 1. Create durable copy in canonical store using AtomicSaveFile
        val committed = AtomicSaveFile.copyFromStaging(source, targetFile)
        if (!committed || !targetFile.exists() || targetFile.length() != expectedSize) {
            Log.e(TAG, "Failed to commit legacy candidate ${source.name} to canonical destination ${targetFile.absolutePath}")
            return false
        }

        // 2. Preserve legacy backup
        val origBak = File(source.parentFile, "${source.name}.orig.bak")
        if (!origBak.exists()) {
            try {
                source.copyTo(origBak, overwrite = true)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create .orig.bak: ${e.message}")
            }
        }

        // 3. Record association
        val key = "assigned_${source.absolutePath.hashCode()}"
        memoryAssignments[key] = targetRomIdentity.sha256
        prefs?.edit()?.putString(key, targetRomIdentity.sha256)?.apply()

        // 4. Mark legacy source as migrated only AFTER successful destination verification
        val migratedFile = File(source.parentFile, "${source.name}.migrated.bak")
        try {
            source.renameTo(migratedFile)
        } catch (e: Exception) {
            Log.w(TAG, "Could not rename source to .migrated.bak: ${e.message}")
        }

        Log.i(TAG, "Successfully migrated legacy save ${source.name} to ${targetFile.absolutePath}")
        return true
    }

    companion object {
        private const val TAG = "LegacySaveCatalog"
    }
}
