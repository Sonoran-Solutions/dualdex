package com.dualdex.emulator.backup

import com.dualdex.emulator.storage.RomSaveMetadata
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** Read-only inventory for backup selection. Any traversal or listing error makes it incomplete. */
class SaveBackupInventory(private val filesDir: File) {
    data class Result(
        val identities: Set<String>,
        val candidates: List<QuotaBackupSelection.Candidate>,
        val sourceStamps: Map<String, QuotaBackupSelection.SourceStamp>,
        val complete: Boolean,
        val unassignablePaths: Set<String>,
        val problems: List<String>
    )

    /** Bindings must be created from a locally verified, exact catalog assignment and content digest. */
    fun scan(verifiedLegacyBindings: Map<String, QuotaBackupSelection.PortableLegacyBinding> = emptyMap()): Result {
        val identities = linkedSetOf<String>()
        val candidates = mutableListOf<QuotaBackupSelection.Candidate>()
        val sourceStamps = linkedMapOf<String, QuotaBackupSelection.SourceStamp>()
        val unassignable = linkedSetOf<String>()
        val problems = mutableListOf<String>()
        var complete = true
        val roots = listOf("saves_v2", "saves", "saves_fallback")
        for (rootName in roots) {
            val root = File(filesDir, rootName)
            if (!root.exists()) continue
            if (Files.isSymbolicLink(root.toPath()) || !root.isDirectory) {
                complete = false; problems += "invalid save root"; continue
            }
            val rootPath = root.toPath().toAbsolutePath().normalize()
            try {
                Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (Files.isSymbolicLink(dir)) {
                            complete = false; problems += "symbolic link in save tree"; return FileVisitResult.SKIP_SUBTREE
                        }
                        val rel = relative(dir)
                        if (rootName == "saves_v2" && dir.toAbsolutePath().normalize() != rootPath && !rel.contains('/')) {
                            if (rel.matches(Regex("[0-9a-fA-F]{64}"))) identities += rel.lowercase()
                            else { complete = false; problems += "invalid ROM identity directory" }
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val rel = relative(file)
                        if (Files.isSymbolicLink(file) || !attrs.isRegularFile) {
                            if (isSaveLike(rel)) { complete = false; unassignable += rel; problems += "unsafe filesystem entry" }
                            return FileVisitResult.CONTINUE
                        }
                        val name = file.fileName.toString()
                        val identity = if (rootName == "saves_v2") file.parent.fileName.toString().lowercase() else ""
                        val size = attrs.size()
                        val modified = attrs.lastModifiedTime().toMillis()
                        val category = when {
                            rootName == "saves_v2" && name == "battery.sav" -> QuotaBackupSelection.Category.CANONICAL_BATTERY
                            rootName == "saves_v2" && name == "metadata.json" -> QuotaBackupSelection.Category.SAVE_METADATA
                            name.endsWith(".state", true) -> QuotaBackupSelection.Category.STATE
                            name.endsWith(".sav", true) && rootName != "saves_v2" -> QuotaBackupSelection.Category.VERIFIED_LEGACY_BATTERY
                            name.endsWithAny(".png", ".jpg", ".jpeg", ".webp") -> QuotaBackupSelection.Category.THUMBNAIL
                            isSaveLike(rel) -> { complete = false; unassignable += rel; return FileVisitResult.CONTINUE }
                            else -> return FileVisitResult.CONTINUE
                        }
                        if (rootName == "saves_v2" && identity.matches(Regex("[0-9a-f]{64}"))) identities += identity
                        var assigned = false
                        var valid = attrs.isRegularFile && size > 0 && size <= QuotaBackupSelection.MAX_ENTRY_BYTES
                        val sourceStamp = if (valid) captureStamp(file, rel, size, modified) else null
                        if (valid && sourceStamp == null) {
                            valid = false; complete = false
                            problems += "save file changed or could not be hashed during inventory"
                            if (isSaveLike(rel)) unassignable += rel
                        } else if (sourceStamp != null) sourceStamps[rel] = sourceStamp
                        var romId = identity
                        if (category == QuotaBackupSelection.Category.SAVE_METADATA) {
                            val metadata = if (valid) runCatching { RomSaveMetadata.fromJson(file.toFile().readText()) }.getOrNull() else null
                            valid = valid && metadata?.sha256?.equals(identity, true) == true
                        }
                        if (category == QuotaBackupSelection.Category.VERIFIED_LEGACY_BATTERY) {
                            val binding = verifiedLegacyBindings[file.toFile().canonicalPath] ?: verifiedLegacyBindings[file.toAbsolutePath().normalize().toString()]
                            val digest = sourceStamp?.contentSha256
                            val rootPath = filesDir.canonicalPath
                            if (binding != null && digest != null &&
                                QuotaBackupSelection.portableLegacyDestination(binding, rootPath, file.toFile().canonicalPath, digest) != null) {
                                romId = binding.romSha256.lowercase(); assigned = true; identities += romId
                            } else {
                                valid = false; unassignable += rel
                            }
                        }
                        if (romId.isBlank()) {
                            complete = false; unassignable += rel
                            candidates += QuotaBackupSelection.Candidate("", category, rel, size, modified, false, false)
                        } else {
                            candidates += QuotaBackupSelection.Candidate(romId, category, rel, size, modified, valid, assigned)
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                        complete = false; problems += "save inventory could not read an entry"
                        val rel = relative(file); if (isSaveLike(rel)) unassignable += rel
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                        if (exc != null) { complete = false; problems += "save inventory could not finish a directory" }
                        return FileVisitResult.CONTINUE
                    }

                    private fun relative(path: Path): String = filesDir.toPath().toAbsolutePath().normalize()
                        .relativize(path.toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/')
                })
            } catch (_: Exception) {
                complete = false; problems += "save inventory failed"
            }
        }
        return Result(identities, candidates, sourceStamps, complete, unassignable, problems.distinct())
    }


    private fun captureStamp(path: Path, relative: String, expectedSize: Long, expectedModified: Long): QuotaBackupSelection.SourceStamp? = runCatching {
        if (expectedSize !in 1..QuotaBackupSelection.MAX_ENTRY_BYTES || Files.isSymbolicLink(path)) return null
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                if (total > expectedSize - count) return null
                total += count; digest.update(buffer, 0, count)
            }
        }
        val after = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (total != expectedSize || after.size() != expectedSize || after.lastModifiedTime().toMillis() != expectedModified ||
            Files.isSymbolicLink(path)) return null
        QuotaBackupSelection.SourceStamp(relative, total, expectedModified, digest.digest().joinToString("") { "%02x".format(it) })
    }.getOrNull()

    private fun isSaveLike(path: String): Boolean = path.endsWith(".sav", true) || path.endsWith(".state", true) || path == "metadata.json"
    private fun String.endsWithAny(vararg endings: String) = endings.any { endsWith(it, true) }
}
