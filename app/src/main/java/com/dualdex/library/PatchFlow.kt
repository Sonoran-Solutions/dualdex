package com.dualdex.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.dualdex.emulator.storage.AtomicSaveFile
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackDetector
import com.dualdex.settings.SettingsManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class StagedDocument(val uriString: String, val name: String?)

internal interface SafDocumentTarget {
    fun canWrite(): Boolean
    fun listEntryNamesAndUris(): List<StagedDocument>
    fun createStagingDocument(displayName: String): StagedDocument?
    fun writeAll(uriString: String, bytes: ByteArray)
    fun renameDocument(uriString: String, targetName: String): StagedDocument?
    fun deleteDocument(uriString: String): Boolean
}

object PatchFlow {
    /** The hosting Activity launches OpenDocument and forwards its result to [onPatchPicked]. */
    var pickPatchRequest: (() -> Unit)? = null

    sealed interface Status {
        data object Idle : Status
        data object Patching : Status
        data class Failed(val reason: String) : Status
        data class Saved(
            val uri: Uri,
            val fileName: String,
            val linkedFolder: Uri?,
            val compatibility: RomCompatibility,
            val saveId: Long = 0L
        ) : Status
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val nextSaveId = AtomicLong(0L)
    private val mutableStatus = MutableStateFlow<Status>(Status.Idle)
    val status = mutableStatus.asStateFlow()

    /** Pass the picker result here from the Activity. A null URI means the picker was cancelled. */
    fun onPatchPicked(context: Context, uri: Uri?) {
        if (uri == null) return
        val previous = mutableStatus.value
        if (previous == Status.Patching || !mutableStatus.compareAndSet(previous, Status.Patching)) return
        val app = context.applicationContext
        scope.launch {
            mutableStatus.value = try {
                patch(app, uri)
            } catch (e: IllegalArgumentException) {
                Status.Failed(e.message ?: "Invalid patch.")
            } catch (e: IOException) {
                Status.Failed(e.message ?: "Could not read or save the ROM.")
            } catch (_: SecurityException) {
                Status.Failed("DualDex could not access the patch or ROM folder.")
            } catch (_: IllegalStateException) {
                Status.Failed("DualDex could not finish reading or saving the ROM.")
            } catch (_: OutOfMemoryError) {
                Status.Failed("DualDex ran out of memory while patching this ROM.")
            }
        }
    }

    private fun patch(context: Context, uri: Uri): Status {
        val folder = SettingsManager(context).romsFolderUri?.let(Uri::parse)
            ?: return Status.Failed("Choose a ROM folder containing the base ROM first.")
        val root = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.isDirectory }
            ?: return Status.Failed("DualDex could not open the linked ROM folder.")
        val patchName = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
        val stem = patchName.substringBeforeLast('.')
            .replace(Regex("""[\\/:*?"<>|\p{C}]"""), "_")
            .take(80).trim(' ', '.').ifEmpty { "Patched ROM" }

        val spoolFile = spoolPatch(context, uri)
        try {
            val header = RomPatcher.inspect(spoolFile)
            val candidates = romDocuments(root).map { doc ->
                RomPatcher.BaseRomCandidate(doc.uri, doc.length().takeIf { it > 0 }) {
                    context.contentResolver.openInputStream(doc.uri)
                }
            }
            val match = RomPatcher.findMatchingBaseRomWithBudget(header, candidates, spoolFile.length())
                ?: return Status.Failed("No base ROM matched ${header.sourceSize} bytes and CRC32 %08x.".format(header.sourceCrc32))
            val result = when (val outcome = RomPatcher.apply(match.bytes, spoolFile)) {
                is PatchOutcome.Failure -> return Status.Failed(outcome.reason)
                is PatchOutcome.Success -> outcome
            }
            val compatibility = RomHackDetector.detectCompatibilityFromBytes(
                headerBytes = result.outputBytes,
                sha256 = result.sha256,
                profiles = ProfileLoader.loadProfilesFromAssets(context),
                fileName = "$stem.gba",
                romLength = result.outputBytes.size.toLong()
            )
            val linkedOutput = saveToFolder(context, root, stem, result.outputBytes)
            if (linkedOutput != null) {
                return Status.Saved(linkedOutput.first, linkedOutput.second, folder, compatibility, nextSaveId.incrementAndGet())
            }
            val output = savePrivately(context, stem, result.outputBytes)
            return Status.Saved(Uri.fromFile(output), output.name, null, compatibility, nextSaveId.incrementAndGet())
        } finally {
            spoolFile.delete()
        }
    }

    private fun romDocuments(dir: DocumentFile, depth: Int = 0): Sequence<DocumentFile> = sequence {
        for (doc in dir.listFiles()) {
            val name = doc.name ?: continue
            if (name.startsWith(".")) continue
            if (doc.isDirectory) {
                if (depth < RomScanCache.MAX_DEPTH) yieldAll(romDocuments(doc, depth + 1))
            } else if (doc.isFile && RomScanCache.isRomName(name)) {
                yield(doc)
            }
        }
    }

    // Spool unknown-length SAF streams to disk to enforce the cap and stream patching without loading patch bytes into RAM.
    private fun spoolPatch(context: Context, uri: Uri): File {
        val temporary = File.createTempFile("rom-patch-", ".tmp", context.cacheDir)
        var spooled = false
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the patch.")
            input.use {
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) throw IOException("Could not read the patch.")
                        total += count
                        if (total > RomPatcher.MAX_PATCH_BYTES) {
                            throw PatchException(
                                "Patch exceeds the ${RomPatcher.MAX_PATCH_BYTES / (1024 * 1024)} MiB size limit."
                            )
                        }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            spooled = true
            return temporary
        } finally {
            if (!spooled) temporary.delete()
        }
    }

    internal fun outputNames(stem: String) = generateSequence(0) { it + 1 }.map {
        if (it == 0) "$stem.gba" else "$stem ($it).gba"
    }

    internal fun stagingFileName(uniqueToken: String): String = ".dualdex_patch_${uniqueToken}.tmp"

    internal fun isStagingFileName(name: String): Boolean =
        name.startsWith(".dualdex_patch_") && name.endsWith(".tmp", ignoreCase = true)

    /**
     * Stages the patched ROM under a non-ROM `.tmp` filename, writes + flushes completely,
     * and atomically publishes it to `<stem>.gba` via rename only after writing succeeds.
     * Returns null (cleaning up staging) if the provider does not support safe staged rename.
     */
    internal fun publishStagedDocument(
        target: SafDocumentTarget,
        stem: String,
        bytes: ByteArray,
        uniqueToken: String = UUID.randomUUID().toString().replace("-", "")
    ): StagedDocument? {
        val existing = try {
            if (!target.canWrite()) return null
            target.listEntryNamesAndUris()
        } catch (_: SecurityException) {
            return null
        } catch (_: IllegalStateException) {
            return null
        }
        val existingNames = existing.mapNotNull { it.name?.lowercase() }.toSet()
        val existingUris = existing.map { it.uriString }.toSet()
        val finalTargetName = outputNames(stem).first { it.lowercase() !in existingNames }
        val stagingName = generateSequence(0) { it + 1 }
            .map { idx -> if (idx == 0) stagingFileName(uniqueToken) else stagingFileName("${uniqueToken}_$idx") }
            .first { it.lowercase() !in existingNames }

        val staged = try {
            target.createStagingDocument(stagingName)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalStateException) {
            null
        } ?: return null

        if (staged.uriString in existingUris) return null
        val actualStagingName = staged.name
        if (actualStagingName.isNullOrBlank() ||
            RomScanCache.isRomName(actualStagingName) ||
            actualStagingName.endsWith(".gba", ignoreCase = true)
        ) {
            runCatching { target.deleteDocument(staged.uriString) }
            return null
        }

        var publishedUri: String? = null
        try {
            target.writeAll(staged.uriString, bytes)
            val renamed = target.renameDocument(staged.uriString, finalTargetName) ?: return null
            val actualFinalName = renamed.name?.takeIf {
                it.endsWith(".gba", ignoreCase = true) && !isStagingFileName(it)
            } ?: run {
                if (renamed.uriString !in existingUris) {
                    runCatching { target.deleteDocument(renamed.uriString) }
                }
                return null
            }
            publishedUri = renamed.uriString
            return StagedDocument(renamed.uriString, actualFinalName)
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        } catch (_: UnsupportedOperationException) {
            return null
        } catch (_: IllegalStateException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        } finally {
            if (publishedUri == null) {
                runCatching { target.deleteDocument(staged.uriString) }
            }
        }
    }

    private class AndroidSafDocumentTarget(
        private val context: Context,
        private val root: DocumentFile
    ) : SafDocumentTarget {
        private val createdDocs = mutableMapOf<String, DocumentFile>()

        override fun canWrite(): Boolean = try {
            root.canWrite()
        } catch (_: SecurityException) {
            false
        }

        override fun listEntryNamesAndUris(): List<StagedDocument> =
            root.listFiles().map { StagedDocument(it.uri.toString(), it.name) }

        override fun createStagingDocument(displayName: String): StagedDocument? {
            val doc = root.createFile("application/octet-stream", displayName) ?: return null
            val uriString = doc.uri.toString()
            createdDocs[uriString] = doc
            return StagedDocument(uriString, doc.name)
        }

        override fun writeAll(uriString: String, bytes: ByteArray) {
            val docUri = Uri.parse(uriString)
            val stream = context.contentResolver.openOutputStream(docUri, "w")
                ?: throw IOException("Could not open staged SAF document for writing.")
            stream.use { out ->
                out.write(bytes)
                out.flush()
                (out as? FileOutputStream)?.fd?.let { fd ->
                    runCatching { fd.sync() }
                }
            }
        }

        override fun renameDocument(uriString: String, targetName: String): StagedDocument? {
            val docUri = Uri.parse(uriString)
            val renamedUri = DocumentsContract.renameDocument(context.contentResolver, docUri, targetName)
                ?: return null
            val renamedName = DocumentFile.fromSingleUri(context, renamedUri)?.name
            return StagedDocument(renamedUri.toString(), renamedName)
        }

        override fun deleteDocument(uriString: String): Boolean {
            val tracked = createdDocs.remove(uriString)
            if (tracked != null && tracked.delete()) return true
            val docUri = Uri.parse(uriString)
            if (DocumentFile.fromSingleUri(context, docUri)?.delete() == true) return true
            return runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }.getOrDefault(false)
        }
    }

    private fun saveToFolder(context: Context, root: DocumentFile, stem: String, bytes: ByteArray): Pair<Uri, String>? {
        val published = publishStagedDocument(AndroidSafDocumentTarget(context, root), stem, bytes) ?: return null
        val name = published.name ?: return null
        return Uri.parse(published.uriString) to name
    }

    internal fun savePrivatelyToDir(dir: File, stem: String, bytes: ByteArray): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create DualDex’s patched ROM folder.")
        val existingNames = dir.listFiles()?.map { it.name.lowercase() }?.toSet().orEmpty()
        val targetName = outputNames(stem).first {
            val lower = it.lowercase()
            lower !in existingNames && "$lower.tmp" !in existingNames
        }
        val output = File(dir, targetName)
        if (!AtomicSaveFile.writeBytes(output, bytes)) {
            throw IOException("Could not atomically write the patched ROM.")
        }
        return output
    }

    private fun savePrivately(context: Context, stem: String, bytes: ByteArray): File =
        savePrivatelyToDir(File(context.filesDir, "patched_roms"), stem, bytes)
}
