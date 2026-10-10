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

/** Recursive linked-folder scan; ROMs are played in place from their SAF URIs. */
object LibraryScanner {
    fun scan(context: Context, folder: Uri, profiles: List<RomHackProfile>): List<RomItem> {
        val root = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.isDirectory } ?: return emptyList()
        val cache = RomScanCache(File(context.filesDir, "library_scan_cache.json"))
        val found = mutableListOf<DocumentFile>()
        collect(root, 0, found)
        val seen = mutableSetOf<String>()
        val items = found.mapNotNull { doc ->
            val key = doc.uri.toString()
            seen += key
            val verdict = cache.lookup(key, doc.length(), doc.lastModified())
                ?: runCatching { inspect(context, doc, profiles) }.getOrNull()?.also { cache.put(it) }
            val name = doc.name ?: return@mapNotNull null
            RomItem(
                title = name.substringBeforeLast('.'),
                fileName = name,
                uri = doc.uri,
                sizeFormatted = formatSize(doc.length()),
                status = verdict?.status,
                sha256 = verdict?.sha256.orEmpty(),
                profileName = verdict?.profileName.orEmpty()
            )
        }
        runCatching { cache.retainAndSave(seen) }
        return items.sortedBy { it.title.lowercase() }
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
        val header = ByteArray(192)
        val sums = context.contentResolver.openInputStream(doc.uri)?.let { input ->
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) break
                read += n
            }
            RomInfo.checksums(SequenceInputStream(header.copyOf(read).inputStream(), input) as InputStream)
        } ?: return null
        val compat = RomHackDetector.detectCompatibilityFromBytes(header, sums.sha256, profiles, doc.name.orEmpty())
        val profileName = if (compat.status == RomCompatibilityStatus.UNSUPPORTED) "" else compat.profile.name
        return ScanVerdict(doc.uri.toString(), doc.length(), doc.lastModified(), sums.sha256, compat.status, profileName)
    }

    fun formatSize(length: Long): String = if (length >= 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", length / (1024.0 * 1024.0))
    } else "${length / 1024} KB"
}
