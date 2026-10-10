package com.dualdex.emulator.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.dualdex.emulator.RomIdentity
import com.dualdex.settings.SettingsManager
import java.io.File
import java.security.MessageDigest

/**
 * Opt-in save sharing with other emulators (#153): a plain `<rom>.sav` (or an existing `<rom>.srm`)
 * in a user-chosen folder. The SHA-256 canonical store stays the source of truth; this is a copy.
 *
 * Never-clobber rule: DualDex only overwrites the shared file when it is missing, already equal,
 * or still exactly what DualDex last wrote. If another emulator changed it, the export is skipped
 * ([ExportAction.CONFLICT]) until the user picks LOAD SAVE (import it) in the Saves tab.
 */
class SaveShareStore(private val context: Context?) {

    enum class ExportAction { WRITE, IN_SYNC, CONFLICT }

    enum class Status { OFF, IN_SYNC, WRITTEN, CONFLICT, NO_SHARED_FILE, FAILED }

    private val settings by lazy { context?.let { SettingsManager(it) } }

    fun isEnabled(): Boolean = !settings?.shareSavesFolderUri.isNullOrBlank()

    private fun folder(): DocumentFile? {
        val ctx = context ?: return null
        val uri = settings?.shareSavesFolderUri ?: return null
        return try {
            DocumentFile.fromTreeUri(ctx, Uri.parse(uri))?.takeIf { it.canWrite() }
        } catch (e: Exception) {
            Log.w(TAG, "Share folder unavailable: ${e.message}")
            null
        }
    }

    /** Stem other emulators use: the ROM's own file name (from the last-played URI) minus extension. */
    fun romBaseName(identity: RomIdentity): String {
        val name = try {
            settings?.lastPlayedRomUri?.let { DocumentFile.fromSingleUri(context!!, Uri.parse(it))?.name }
        } catch (e: Exception) {
            null
        }
        return SaveStateFiles.shareBaseName(name, identity.sanitizedTitle)
    }

    private fun sharedFile(dir: DocumentFile, base: String): DocumentFile? =
        dir.findFile("$base.sav") ?: dir.findFile("$base.srm")

    private fun lastExportFile(canonicalDir: File) = File(canonicalDir, LAST_EXPORT)

    /** Copies the canonical battery save out, unless that would clobber another emulator's newer save. */
    fun export(identity: RomIdentity, canonicalSave: File): Status {
        if (!isEnabled()) return Status.OFF
        val dir = folder() ?: return Status.FAILED
        return try {
            val base = romBaseName(identity)
            val bytes = canonicalSave.readBytes()
            val canonicalHash = sha256(bytes)
            val existing = sharedFile(dir, base)
            val sharedHash = existing?.let { f -> context!!.contentResolver.openInputStream(f.uri)?.use { sha256(it.readBytes()) } }
            val lastFile = lastExportFile(canonicalSave.parentFile!!)
            val last = lastFile.takeIf { it.exists() }?.readText()?.trim()
            when (exportAction(sharedHash, last, canonicalHash)) {
                ExportAction.IN_SYNC -> { lastFile.writeText(canonicalHash); Status.IN_SYNC }
                ExportAction.CONFLICT -> {
                    Log.w(TAG, "Shared save ${existing?.name} was changed elsewhere; not overwriting")
                    Status.CONFLICT
                }
                ExportAction.WRITE -> {
                    val target = existing ?: dir.createFile("application/octet-stream", "$base.sav") ?: return Status.FAILED
                    // "wt" truncates; a plain "w" can leave stale tail bytes on some providers.
                    context!!.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(bytes) } ?: return Status.FAILED
                    lastFile.writeText(canonicalHash)
                    Status.WRITTEN
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Share export failed: ${e.message}")
            Status.FAILED
        }
    }

    /** Reads the shared save, or null if sharing is off or there is none for this ROM. */
    fun readShared(identity: RomIdentity): Pair<String, ByteArray>? {
        val dir = folder() ?: return null
        val f = sharedFile(dir, romBaseName(identity)) ?: return null
        val bytes = context!!.contentResolver.openInputStream(f.uri)?.use { it.readBytes() } ?: return null
        return (f.name ?: "") to bytes
    }

    /** Writes [canonicalSave] to `<rom>.backup-<yyyyMMdd-HHmmss>.<ext>` next to the shared save. */
    fun writeBackup(identity: RomIdentity, canonicalSave: File, ext: String): Boolean {
        val dir = folder() ?: return false
        if (!canonicalSave.isFile || canonicalSave.length() == 0L) return true // nothing to protect
        return try {
            val name = SaveStateFiles.backupName(romBaseName(identity), ext, System.currentTimeMillis())
            val doc = dir.createFile("application/octet-stream", name) ?: return false
            val bytes = canonicalSave.readBytes()
            context!!.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(bytes) } ?: return false
            doc.length() == bytes.size.toLong()
        } catch (e: Exception) {
            Log.e(TAG, "Backup before LOAD SAVE failed: ${e.message}")
            false
        }
    }

    fun markInSync(canonicalDir: File, bytes: ByteArray) {
        lastExportFile(canonicalDir).writeText(sha256(bytes))
    }

    companion object {
        private const val TAG = "SaveShareStore"
        const val LAST_EXPORT = "shared_export.sha256"

        /** Set by the activity: launches the folder picker, starting at the given suggestion. */
        @Volatile
        var requestFolderPicker: ((Uri?) -> Unit)? = null

        private val EMULATOR_SAVE_DIRS = listOf(
            "RetroArch/saves/mGBA", "RetroArch/saves/gpSP", "RetroArch/saves/VBA-M",
            "RetroArch/saves/VBA Next", "RetroArch/saves/Beetle GBA", "RetroArch/saves",
            "mGBA", "Pizza Boy GBA", "My Boy", "Saves"
        )

        fun exportAction(sharedHash: String?, lastExportedHash: String?, canonicalHash: String): ExportAction = when {
            sharedHash == null -> ExportAction.WRITE
            sharedHash.equals(canonicalHash, true) -> ExportAction.IN_SYNC
            lastExportedHash != null && sharedHash.equals(lastExportedHash, true) -> ExportAction.WRITE
            else -> ExportAction.CONFLICT
        }

        /** Existing emulator save folders on each storage volume root, most specific first. */
        fun suggestedDirs(volumeRoots: List<File>): List<File> =
            volumeRoots.flatMap { root -> EMULATOR_SAVE_DIRS.map { File(root, it) } }.filter { it.isDirectory }

        /** `/storage/emulated/0/a/b` -> `primary:a/b`; `/storage/ABCD-1234/a` -> `ABCD-1234:a`. */
        fun externalStorageDocId(file: File): String? {
            val path = file.absolutePath
            Regex("^/storage/emulated/0/?(.*)$").find(path)?.let { return "primary:${it.groupValues[1]}" }
            Regex("^/storage/([^/]+)/?(.*)$").find(path)?.let { return "${it.groupValues[1]}:${it.groupValues[2]}" }
            return null
        }

        /** Volume roots derived from app-specific dirs (`<root>/Android/data/...`). */
        fun volumeRoots(context: Context): List<File> =
            context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
                dir.absolutePath.substringBefore("/Android/", "").takeIf { it.isNotEmpty() }?.let(::File)
            }

        fun initialPickerUri(context: Context): Uri? {
            val dir = suggestedDirs(volumeRoots(context)).firstOrNull() ?: return null
            val docId = externalStorageDocId(dir) ?: return null
            return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
