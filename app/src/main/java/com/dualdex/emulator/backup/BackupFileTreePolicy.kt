package com.dualdex.emulator.backup

import com.dualdex.emulator.storage.RomSaveMetadata
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** One allowlist for files emitted during D2D backup and accepted into restore staging. */
object BackupFileTreePolicy {
    const val MAX_PATH_CHARS = 4096
    const val MAX_D2D_ENTRIES = 16_384
    const val MAX_D2D_FILE_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_D2D_STAGING_BYTES = 16L * 1024 * 1024 * 1024
    const val MAX_CATALOG_BYTES = 1024L * 1024
    // ponytail: metadata is capped at 1 MiB; larger schemas need bounded streaming parsing on both sides.
    const val MAX_METADATA_BYTES = 1024L * 1024

    data class BackupFile(val relativePath: String, val file: File, val sizeBytes: Long)

    /**
     * Shared sender/receiver admission. Temporary .tmp files and recovery .bak / migration
     * copies are retained for staged review under the same bounds as canonical saves.
     * Empty or oversized entries refuse the entire transfer; no recovery data is silently omitted.
     */
    @Throws(IOException::class)
    fun checkFileAdmission(path: String, size: Long, totalBytes: Long, entryCount: Int) {
        val maxBytes = when {
            path == LEGACY_CATALOG_PATH -> MAX_CATALOG_BYTES
            isMetadata(path) -> MAX_METADATA_BYTES
            else -> MAX_D2D_FILE_BYTES
        }
        if (!isAllowedPath(path, directory = false) || size !in 1..maxBytes ||
            totalBytes < 0 || totalBytes > MAX_D2D_STAGING_BYTES - size ||
            entryCount !in 1..MAX_D2D_ENTRIES) throw IOException("Device transfer admission limit exceeded")
    }

    /** Canonical metadata must be parseable and owned by its ROM directory on both sides. */
    @Throws(IOException::class)
    fun checkFileContents(path: String, file: File) {
        if (!isMetadata(path)) return
        val valid = runCatching {
            // Bound the read itself as well as admission, in case the source grows during preflight.
            val bytes = ByteArray(MAX_METADATA_BYTES.toInt() + 1)
            val count = file.inputStream().use { input ->
                var total = 0
                while (total < bytes.size) {
                    val read = input.read(bytes, total, bytes.size - total)
                    if (read < 0) break
                    total += read
                }
                total
            }
            count <= MAX_METADATA_BYTES && RomSaveMetadata.fromJson(String(bytes, 0, count, Charsets.UTF_8))
                ?.sha256?.equals(path.split('/')[1], ignoreCase = true) == true
        }.getOrDefault(false)
        if (!valid) throw IOException("Invalid device transfer metadata or ROM ownership")
    }

    /** Walk only PR #185's three file roots plus the exact legacy catalog file. */
    @Throws(IOException::class)
    fun collect(filesRoot: File, legacyCatalog: File): List<BackupFile> {
        val root = filesRoot.toPath().toAbsolutePath().normalize()
        val result = mutableListOf<BackupFile>()
        var totalBytes = 0L
        fun add(path: String, file: File, size: Long) {
            checkFileAdmission(path, size, totalBytes, result.size + 1)
            checkFileContents(path, file)
            result += BackupFile(path, file, size)
            totalBytes += size
        }
        for (name in SAVE_ROOTS) {
            val tree = root.resolve(name)
            if (!Files.exists(tree, LinkOption.NOFOLLOW_LINKS)) continue
            if (Files.isSymbolicLink(tree) || !Files.isDirectory(tree, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(tree)) {
                throw IOException("Invalid save-tree root")
            }
            Files.walkFileTree(tree, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (Files.isSymbolicLink(dir) || !attrs.isDirectory || !Files.isReadable(dir)) throw IOException("Unsafe save-tree directory")
                    if (dir != tree && !isAllowedPath(relative(root, dir), directory = true)) {
                        throw IOException("Save-tree directory is outside the transfer policy")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val relative = relative(root, file)
                    if (Files.isSymbolicLink(file) || !attrs.isRegularFile || !Files.isReadable(file) || !isAllowedPath(relative, directory = false)) {
                        throw IOException("Unexpected save-tree entry")
                    }
                    add(relative, file.toFile(), attrs.size())
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw IOException("Could not read save tree", exc)

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw IOException("Could not finish reading save tree", exc)
                    return FileVisitResult.CONTINUE
                }
            })
        }

        if (Files.exists(legacyCatalog.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            val catalogPath = legacyCatalog.toPath()
            if (Files.isSymbolicLink(catalogPath) || !Files.isRegularFile(catalogPath, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(catalogPath)) {
                throw IOException("Invalid legacy catalog")
            }
            add(LEGACY_CATALOG_PATH, legacyCatalog, Files.size(catalogPath))
        }
        return result.sortedBy { it.relativePath }
    }

    @Throws(IOException::class)
    fun emit(filesRoot: File, legacyCatalog: File, emitFile: (BackupFile) -> Unit) {
        collect(filesRoot, legacyCatalog).forEach { entry ->
            val path = entry.file.toPath()
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
                !Files.isReadable(path) || Files.size(path) != entry.sizeBytes) {
                throw IOException("Save-tree entry changed before emission")
            }
            checkFileContents(entry.relativePath, entry.file)
            emitFile(entry)
        }
    }

    fun isAllowedPath(path: String, directory: Boolean): Boolean {
        if (path.isBlank() || path.length > MAX_PATH_CHARS || path.startsWith('/') || '\\' in path ||
            path.split('/').any { it.isBlank() || it == "." || it == ".." }) return false
        if (path == LEGACY_CATALOG_PATH) return !directory
        if (path == "sharedpref") return directory
        val parts = path.split('/')
        // Also reject ROMs disguised as temporary or recovery copies (e.g. game.gba.bak).
        // Directory names can be legacy ROM names; only the payload file name is checked.
        if (!directory && ROM_FILE.containsMatchIn(parts.last())) return false
        return when (parts.firstOrNull()) {
            "saves_v2" -> parts.size == 1 && directory ||
                parts.size >= 2 && parts[1].matches(ROM_HASH) &&
                (parts.size == 2 && directory || parts.size >= 3)
            "saves", "saves_fallback" -> parts.size == 1 && directory || parts.size >= 2
            else -> false
        }
    }

    private fun relative(root: Path, path: Path): String {
        if (!path.toAbsolutePath().normalize().startsWith(root)) throw IOException("Save-tree path escaped files root")
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/')
    }

    private val SAVE_ROOTS = listOf("saves_v2", "saves", "saves_fallback")
    private fun isMetadata(path: String) = path.startsWith("saves_v2/") && path.endsWith("/metadata.json")
    private val ROM_HASH = Regex("[0-9a-fA-F]{64}")
    private val ROM_FILE = Regex("\\.(gba|agb|gb|gbc|nds)(?:\\.(?:tmp|bak|orig|migrated))*$", RegexOption.IGNORE_CASE)
    const val LEGACY_CATALOG_PATH = "sharedpref/dualdex_legacy_catalog.xml"
}
