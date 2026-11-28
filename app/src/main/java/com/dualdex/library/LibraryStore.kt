package com.dualdex.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.dualdex.companion.RomItem
import com.dualdex.romhack.RomCompatibilityStatus
import com.dualdex.romhack.RomHackDetector
import com.dualdex.romhack.RomHackProfile
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Locale

/** Per-ROM library preferences (hidden, custom name, cover, SteamGridDB key). Local only. */
class LibraryPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dualdex_library", Context.MODE_PRIVATE)

    fun isHidden(uri: String) = uri in hidden()
    fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet()).orEmpty()
    fun setHidden(uri: String, hide: Boolean) {
        val next = hidden().toMutableSet().apply { if (hide) add(uri) else remove(uri) }
        prefs.edit().putStringSet("hidden", next).apply()
    }

    fun customName(uri: String): String? = prefs.getString("name:$uri", null)
    fun setCustomName(uri: String, name: String?) =
        prefs.edit().apply { if (name.isNullOrBlank()) remove("name:$uri") else putString("name:$uri", name.trim()) }.apply()

    /** Explicit "play anyway" acknowledgement for an unsupported ROM, keyed by SHA-256. */
    fun isUnsupportedAccepted(sha256: String) = prefs.getBoolean("accept:$sha256", false)
    fun acceptUnsupported(sha256: String) = prefs.edit().putBoolean("accept:$sha256", true).apply()

    /** User-supplied SteamGridDB API key. Stored only in app-private prefs; never bundled. */
    var steamGridDbKey: String?
        get() = prefs.getString("steamgriddb_key", null)
        set(value) = prefs.edit().apply { if (value.isNullOrBlank()) remove("steamgriddb_key") else putString("steamgriddb_key", value.trim()) }.apply()
}

internal data class ScannedRomRecord(
    val title: String,
    val fileName: String,
    val uriString: String,
    val sizeBytes: Long,
    val status: RomCompatibilityStatus?,
    val sha256: String,
    val profileName: String
)

/**
 * Single-owner coordinator for post-patch library refreshes.
 * Ensures each distinct [PatchFlow.Status.Saved] outcome triggers at most one scan,
 * resets when a new [PatchFlow.Status.Patching] run starts, and always resolves the
 * target folder URI at refresh time rather than using a stale pre-patch URI.
 */
internal class PatchRefreshTracker(initialHandledSavedKey: Any? = null) {
    private var lastHandledKey: Any? = initialHandledSavedKey

    fun onPatchStatus(
        status: PatchFlow.Status,
        currentFolderUri: () -> String?,
        triggerScan: (currentFolderUri: String?) -> Unit
    ): Boolean = when (status) {
        is PatchFlow.Status.Patching -> onPatching()
        is PatchFlow.Status.Saved -> onSaved(status, currentFolderUri, triggerScan)
        PatchFlow.Status.Idle, is PatchFlow.Status.Failed -> false
    }

    internal fun onPatching(): Boolean {
        lastHandledKey = null
        return false
    }

    internal fun onSaved(
        savedKey: Any,
        currentFolderUri: () -> String?,
        triggerScan: (currentFolderUri: String?) -> Unit
    ): Boolean {
        if (savedKey == lastHandledKey) return false
        lastHandledKey = savedKey
        triggerScan(currentFolderUri())
        return true
    }
}

/** Recursive linked-folder scan plus app-private patched ROM enumeration. */
object LibraryScanner {
    const val PATCHED_ROMS_DIR = "patched_roms"

    fun scan(context: Context, folder: Uri?, profiles: List<RomHackProfile>): List<RomItem> {
        val cache = RomScanCache(File(context.filesDir, "library_scan_cache.json"), profiles)
        val seen = mutableSetOf<String>()

        val linkedRecords = if (folder != null) {
            val root = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.isDirectory }
            if (root != null) {
                val found = mutableListOf<DocumentFile>()
                collect(root, 0, found)
                found.mapNotNull { doc ->
                    val key = doc.uri.toString()
                    seen += key
                    val verdict = cache.lookup(key, doc.length(), doc.lastModified())
                        ?: runCatching { inspect(context, doc, profiles) }.getOrNull()?.also { cache.put(it) }
                    val name = doc.name ?: return@mapNotNull null
                    ScannedRomRecord(
                        title = name.substringBeforeLast('.'),
                        fileName = name,
                        uriString = key,
                        sizeBytes = doc.length(),
                        status = verdict?.status,
                        sha256 = verdict?.sha256.orEmpty(),
                        profileName = verdict?.profileName.orEmpty()
                    )
                }
            } else emptyList()
        } else emptyList()

        val privateRecords = scanPrivateRomDirectory(
            patchedDir = File(context.filesDir, PATCHED_ROMS_DIR),
            cache = cache,
            profiles = profiles,
            seenKeys = seen,
            uriForFile = { Uri.fromFile(it).toString() }
        )

        runCatching { cache.retainAndSave(seen) }
        return mergeAndDeduplicateRoms(linkedRecords, privateRecords).map { record ->
            RomItem(
                title = record.title,
                fileName = record.fileName,
                uri = Uri.parse(record.uriString),
                sizeFormatted = formatSize(record.sizeBytes),
                status = record.status,
                sha256 = record.sha256,
                profileName = record.profileName
            )
        }
    }

    internal fun isPrivateRomFileName(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        if (lower.startsWith(".") || lower.endsWith(".tmp") || lower.endsWith(".bak") || lower.endsWith(".pending")) {
            return false
        }
        return RomScanCache.isRomName(name)
    }

    internal fun collectPrivateRomFiles(patchedDir: File, depth: Int = 0): List<File> {
        if (!patchedDir.isDirectory) return emptyList()
        val children = patchedDir.listFiles()?.sortedBy { it.name.lowercase(Locale.US) } ?: return emptyList()
        val out = mutableListOf<File>()
        for (file in children) {
            val name = file.name
            if (name.startsWith(".")) continue
            if (file.isDirectory) {
                if (depth < RomScanCache.MAX_DEPTH) {
                    out += collectPrivateRomFiles(file, depth + 1)
                }
            } else if (file.isFile && file.length() > 0L && isPrivateRomFileName(name)) {
                out += file
            }
        }
        return out
    }

    internal fun scanPrivateRomDirectory(
        patchedDir: File,
        cache: RomScanCache,
        profiles: List<RomHackProfile>,
        seenKeys: MutableSet<String> = mutableSetOf(),
        uriForFile: (File) -> String = { it.toURI().toString() }
    ): List<ScannedRomRecord> {
        val files = collectPrivateRomFiles(patchedDir)
        return files.map { file ->
            val uriString = uriForFile(file)
            seenKeys += uriString
            val size = file.length()
            val mtime = file.lastModified()
            val verdict = cache.lookup(uriString, size, mtime)
                ?: runCatching {
                    val (header, sums) = headerAndChecksums(file.inputStream())
                    val compat = RomHackDetector.detectCompatibilityFromBytes(header, sums.sha256, profiles, file.name, size)
                    ScanVerdict(uriString, size, mtime, sums.sha256, compat.status, compat.detectedProfile?.name.orEmpty())
                }.getOrNull()?.also { cache.put(it) }
            ScannedRomRecord(
                title = file.name.substringBeforeLast('.'),
                fileName = file.name,
                uriString = uriString,
                sizeBytes = size,
                status = verdict?.status,
                sha256 = verdict?.sha256.orEmpty(),
                profileName = verdict?.profileName.orEmpty()
            )
        }
    }

    internal fun mergeAndDeduplicateRoms(
        linkedRoms: List<ScannedRomRecord>,
        privateRoms: List<ScannedRomRecord>
    ): List<ScannedRomRecord> {
        val seenUris = mutableSetOf<String>()
        val seenHashes = mutableSetOf<String>()
        val merged = mutableListOf<ScannedRomRecord>()
        for (record in linkedRoms + privateRoms) {
            if (!seenUris.add(record.uriString)) continue
            val normalizedHash = record.sha256.trim().lowercase(Locale.US)
            if (normalizedHash.isNotEmpty() && !seenHashes.add(normalizedHash)) continue
            merged += record
        }
        return merged.sortedBy { it.title.lowercase(Locale.US) }
    }

    private fun collect(dir: DocumentFile, depth: Int, out: MutableList<DocumentFile>) {
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (name.startsWith(".")) continue
            if (f.isDirectory) {
                if (depth < RomScanCache.MAX_DEPTH) collect(f, depth + 1, out)
            } else if (RomScanCache.isRomName(name)) {
                out += f
            }
        }
    }

    private fun inspect(context: Context, doc: DocumentFile, profiles: List<RomHackProfile>): ScanVerdict? {
        val (header, sums) = context.contentResolver.openInputStream(doc.uri)?.let(::headerAndChecksums) ?: return null
        val compat = RomHackDetector.detectCompatibilityFromBytes(header, sums.sha256, profiles, doc.name.orEmpty(), doc.length())
        // No detected profile (unsupported, or a base-game hack with no profile): no profile name, so
        // the library never labels or searches cover art by the "Unsupported ROM" placeholder.
        val profileName = compat.detectedProfile?.name.orEmpty()
        return ScanVerdict(doc.uri.toString(), doc.length(), doc.lastModified(), sums.sha256, compat.status, profileName)
    }

    /** Reads the 192-byte header and checksums the whole stream; [input] is always closed. */
    internal fun headerAndChecksums(input: InputStream): Pair<ByteArray, RomChecksums> = input.use {
        val header = ByteArray(192)
        var read = 0
        while (read < header.size) {
            val n = it.read(header, read, header.size - read)
            if (n < 0) break
            read += n
        }
        header to RomInfo.checksums(SequenceInputStream(header.copyOf(read).inputStream(), it) as InputStream)
    }

    fun formatSize(length: Long): String = if (length >= 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", length / (1024.0 * 1024.0))
    } else "${length / 1024} KB"
}
