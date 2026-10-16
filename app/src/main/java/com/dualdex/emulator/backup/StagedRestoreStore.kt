package com.dualdex.emulator.backup

import com.dualdex.emulator.storage.AtomicSaveFile
import com.dualdex.emulator.storage.RomSaveMetadata
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipFile

/** Private pending data. Only a completed, validated callback may publish a reviewable session. */
class StagedRestoreStore(private val filesRoot: File) {
    private val staging = File(filesRoot, "restore_staging")
    data class Source(
        val path: String, val target: String?, val romHash: String?, val title: String,
        val version: AtomicSaveFile.Version, val archiveVersion: AtomicSaveFile.Version? = null
    )
    data class Review(val session: String, val source: Source, val local: AtomicSaveFile.Version?) {
        val identical: Boolean get() = local?.sha256 == source.version.sha256
    }
    data class Session(val id: String, val retainedLocal: Boolean, val entries: List<Review>, val error: String? = null)
    enum class Decision { KEEP_LOCAL, IMPORT_MISSING, REPLACE_LOCAL }
    enum class Result { IMPORTED, IMPORTED_SYNC_UNCONFIRMED, KEPT_LOCAL, ALREADY_PRESENT, REVIEW_AGAIN, CLOSE_GAME, FAILED }

    /** No canonical writes. Directory publication is atomic; interrupted/incomplete sessions stay hidden. */
    fun complete(session: File): File {
        requireSafe(filesRoot, session)
        if (session.parentFile != staging || !session.name.startsWith("session-")) throw IOException("Invalid restore session")
        inventory(session)
        syncTree(session)
        val ready = File(staging, "ready-${UUID.randomUUID()}")
        Files.move(session.toPath(), ready.toPath(), StandardCopyOption.ATOMIC_MOVE)
        AtomicSaveFile.syncDirectory(staging)
        AtomicSaveFile.syncDirectory(filesRoot)
        return ready
    }

    fun review(): List<Session> {
        requireSafe(filesRoot, staging)
        if (!staging.exists()) return emptyList()
        return (staging.listFiles() ?: throw IOException("Cannot list restored data"))
            .filter { it.name.matches(READY) }.sortedBy { it.name }.map { session ->
                try {
                    requireSafe(filesRoot, session)
                    val entries = inventory(session).map { source ->
                        val local = source.target?.let { relative ->
                            val target = targetFile(relative)
                            AtomicSaveFile.version(target, BackupFileTreePolicy.MAX_D2D_FILE_BYTES)
                        }
                        Review(session.name, source, local)
                    }
                    Session(session.name, session.name.startsWith("ready-local-"), entries)
                } catch (_: Exception) {
                    Session(session.name, session.name.startsWith("ready-local-"), emptyList(),
                        "This restored set could not be validated. It remains in private storage; no saves were changed.")
                }
            }
    }

    /** SaveStateManager holds the global save lock and refuses imports while a game is loaded. */
    internal fun import(review: Review, decision: Decision, beforeCommit: (() -> Unit)? = null): Result {
        if (decision == Decision.KEEP_LOCAL) return Result.KEPT_LOCAL
        var work: File? = null
        try {
            if (!review.session.matches(READY)) return Result.FAILED
            val session = File(staging, review.session)
            requireSafe(filesRoot, session)
            val source = inventory(session).singleOrNull { it.path == review.source.path } ?: return Result.REVIEW_AGAIN
            if (source != review.source) return Result.REVIEW_AGAIN
            val relative = source.target ?: return Result.FAILED
            val target = targetFile(relative)
            val current = AtomicSaveFile.version(target, BackupFileTreePolicy.MAX_D2D_FILE_BYTES)
            if (current != review.local) return Result.REVIEW_AGAIN
            if (review.identical) return Result.ALREADY_PRESENT
            if (current != null && decision != Decision.REPLACE_LOCAL ||
                current == null && decision != Decision.IMPORT_MISSING) return Result.REVIEW_AGAIN

            val candidate = if (source.archiveVersion == null) File(session, source.path) else {
                val archive = File(session, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH)
                work = File(staging, "work-${UUID.randomUUID()}").apply { if (!mkdirs()) throw IOException("Cannot stage import") }
                val extracted = File(work, "candidate")
                ZipFile(archive).use { zip ->
                    val entry = zip.getEntry("data/${source.path}") ?: throw IOException("Missing restored entry")
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(extracted).use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (total > source.version.size - count) throw IOException("Restored entry grew")
                                output.write(buffer, 0, count); total += count
                            }
                            if (total != source.version.size || !extracted.setLastModified(source.version.modified))
                                throw IOException("Invalid restored entry")
                            output.fd.sync()
                        }
                    }
                }
                if (AtomicSaveFile.version(archive, QuotaBackupSelection.MAX_PAYLOAD_BYTES) != source.archiveVersion)
                    return Result.REVIEW_AGAIN
                extracted
            }
            requireSafe(if (work == null) session else checkNotNull(work), candidate)
            val retainedSession = File(staging, "retaining-${UUID.randomUUID()}")
            val retained = File(retainedSession, relative)
            val synced = AtomicSaveFile.commitRestore(candidate, target, source.version, current, retained, {
                // Retention is durable and discoverable before replacement. A failed import can leave an extra copy.
                if (current != null) {
                    syncTree(retainedSession)
                    Files.move(retainedSession.toPath(), File(staging, "ready-local-${UUID.randomUUID()}").toPath(),
                        StandardCopyOption.ATOMIC_MOVE)
                    AtomicSaveFile.syncDirectory(staging)
                }
                blockOldAutoResume(relative, source.version.modified)
                beforeCommit?.invoke()
                if (source.archiveVersion != null && AtomicSaveFile.version(
                        File(session, QuotaBackupSelection.CLOUD_SNAPSHOT_PATH), QuotaBackupSelection.MAX_PAYLOAD_BYTES
                    ) != source.archiveVersion) throw IOException("Snapshot changed before commit")
                var parent = target.parentFile
                while (parent != null && parent != filesRoot) {
                    AtomicSaveFile.syncDirectory(parent)
                    parent = parent.parentFile
                }
                AtomicSaveFile.syncDirectory(filesRoot)
                requireSafe(filesRoot, target)
                requireSafe(if (work == null) session else checkNotNull(work), candidate)
            })
            return if (synced) Result.IMPORTED else Result.IMPORTED_SYNC_UNCONFIRMED
        } catch (_: Exception) {
            return Result.FAILED
        } finally {
            work?.deleteRecursively()
        }
    }

    private fun inventory(session: File): List<Source> {
        requireSafe(filesRoot, session)
        val paths = mutableListOf<String>()
        Files.walkFileTree(session.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun preVisitDirectory(dir: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                requireSafe(session, dir.toFile())
                return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                if (!attrs.isRegularFile || Files.isSymbolicLink(file)) throw IOException("Unsafe restored entry")
                paths += session.toPath().relativize(file).toString().replace(File.separatorChar, '/')
                if (paths.size > BackupFileTreePolicy.MAX_D2D_ENTRIES) throw IOException("Too many restored entries")
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
        if (paths.isEmpty()) throw IOException("Empty restore")
        if (QuotaBackupSelection.CLOUD_SNAPSHOT_PATH in paths) {
            if (paths.size != 1) throw IOException("Mixed restore formats")
            val archive = File(session, paths.single())
            val version = AtomicSaveFile.version(archive, QuotaBackupSelection.MAX_PAYLOAD_BYTES) ?: throw IOException("Missing snapshot")
            val snapshot = CloudSnapshotArchive.validate(archive) ?: throw IOException("Invalid snapshot")
            val titles = ZipFile(archive).use { zip ->
                snapshot.entries.filter { it.category == QuotaBackupSelection.Category.SAVE_METADATA.name }.associate {
                    it.romSha256 to (metadataTitle(zip.getInputStream(zip.getEntry("data/${it.path}")), QuotaBackupSelection.MAX_PAYLOAD_BYTES) ?: "Restored game")
                }
            }
            if (AtomicSaveFile.version(archive, QuotaBackupSelection.MAX_PAYLOAD_BYTES) != version) throw IOException("Snapshot changed")
            return snapshot.entries.map { entry ->
                val parts = entry.path.split('/')
                val target = entry.legacyBinding?.let { "saves_v2/${it.romSha256.lowercase()}/${it.targetFileName}" }
                    ?: (listOf("saves_v2", parts[1].lowercase()) + parts.drop(2)).joinToString("/")
                Source(entry.path, target, entry.romSha256, titles[entry.romSha256].orEmpty().take(160),
                    AtomicSaveFile.Version(entry.size, 0, entry.sha256), version)
            }
        }
        val files = BackupFileTreePolicy.collect(session, File(session, BackupFileTreePolicy.LEGACY_CATALOG_PATH))
        if (files.map { it.relativePath }.toSet() != paths.toSet()) throw IOException("Unapproved restored data")
        return files.map { entry ->
            val parts = entry.relativePath.split('/')
            val hash = parts.getOrNull(1)?.lowercase()?.takeIf { parts[0] == "saves_v2" }
            val metadata = hash?.let { File(session, "saves_v2/${parts[1]}/metadata.json") }
            val title = if (metadata?.isFile == true) metadataTitle(metadata.inputStream(), BackupFileTreePolicy.MAX_METADATA_BYTES) else null
            val target = when {
                entry.relativePath == BackupFileTreePolicy.LEGACY_CATALOG_PATH -> null // Absolute-path assignment keys are not portable authority.
                hash != null && parts.size == 3 && parts.last() == "resume_barrier" -> null // Managed by the local import transaction.
                hash != null -> (listOf("saves_v2", hash) + parts.drop(2)).joinToString("/")
                else -> entry.relativePath // Legacy files remain unassigned in their original recovery locations.
            }
            Source(entry.relativePath, target, hash, title?.take(160) ?: if (hash == null) "Unassigned legacy data" else "Restored game",
                AtomicSaveFile.version(entry.file, BackupFileTreePolicy.MAX_D2D_FILE_BYTES) ?: throw IOException("Missing restored file"))
        }
    }

    private fun metadataTitle(input: InputStream, limit: Long): String? = input.use {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            if (bytes.size().toLong() > limit - count) throw IOException("Metadata grew during review")
            bytes.write(buffer, 0, count)
        }
        RomSaveMetadata.fromJson(bytes.toString("UTF-8"))?.displayName
    }

    private fun targetFile(relative: String): File {
        if (!BackupFileTreePolicy.isAllowedPath(relative, false) || relative.startsWith("sharedpref/")) throw IOException("Invalid import target")
        return File(filesRoot, relative).also { requireSafe(filesRoot, it) }
    }

    /** A failed attempt may conservatively suppress auto-resume; it never removes a save/state. */
    private fun blockOldAutoResume(relative: String, restoredTime: Long) {
        val parts = relative.split('/')
        if (parts.size != 3 || parts[0] != "saves_v2" || !Regex("\\.(sav|state)(\\.|$)").containsMatchIn(parts.last())) return
        val dir = File(filesRoot, "saves_v2/${parts[1]}")
        val barrier = File(dir, "resume_barrier")
        requireSafe(filesRoot, barrier)
        val states = dir.listFiles() ?: throw IOException("Cannot inspect resume states")
        val newest = states.filter { it.name.endsWith(".state") || it.name == "resume_barrier" }
            .onEach { requireSafe(filesRoot, it) }.maxOfOrNull { it.lastModified() } ?: 0L
        val time = maxOf(System.currentTimeMillis(), newest, restoredTime)
        val temp = File(dir, ".resume-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(time.toString().toByteArray())
                if (!temp.setLastModified(time)) throw IOException("Cannot set resume barrier")
                output.fd.sync()
            }
            Files.move(temp.toPath(), barrier.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            AtomicSaveFile.syncDirectory(dir)
        } finally { temp.delete() }
    }

    private fun requireSafe(root: File, target: File) {
        val base = root.toPath().toAbsolutePath().normalize()
        val path = target.toPath().toAbsolutePath().normalize()
        if (!path.startsWith(base)) throw IOException("Restore path escaped storage")
        var current = base
        if (Files.isSymbolicLink(current)) throw IOException("Symlink in restore storage")
        for (part in base.relativize(path)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) throw IOException("Symlink in restore storage")
        }
    }

    private fun syncTree(root: File) {
        Files.walkFileTree(root.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun postVisitDirectory(dir: java.nio.file.Path, exc: IOException?): java.nio.file.FileVisitResult {
                if (exc != null) throw exc
                AtomicSaveFile.syncDirectory(dir.toFile())
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
    }

    private companion object { val READY = Regex("ready-(local-)?[0-9a-f-]{36}") }
}
