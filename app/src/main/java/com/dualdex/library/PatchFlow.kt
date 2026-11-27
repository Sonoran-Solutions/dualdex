package com.dualdex.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackDetector
import com.dualdex.settings.SettingsManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            val compatibility: RomCompatibility
        ) : Status
    }

    private val scope = CoroutineScope(Dispatchers.IO)
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

        val bytes = readPatch(context, uri)
        val header = RomPatcher.inspect(bytes)
        val candidates = romDocuments(root).map { doc ->
            RomPatcher.BaseRomCandidate(doc.uri, doc.length().takeIf { it > 0 }) {
                context.contentResolver.openInputStream(doc.uri)
            }
        }
        val match = RomPatcher.findMatchingBaseRom(header, candidates)
            ?: return Status.Failed("No base ROM matched ${header.sourceSize} bytes and CRC32 %08x.".format(header.sourceCrc32))
        val result = when (val outcome = RomPatcher.apply(match.bytes, bytes)) {
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
            return Status.Saved(linkedOutput.first, linkedOutput.second, folder, compatibility)
        }
        val output = savePrivately(context, stem, result.outputBytes)
        return Status.Saved(Uri.fromFile(output), output.name, null, compatibility)
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

    // Spool unknown-length SAF streams to disk to enforce the cap without doubling heap use.
    private fun readPatch(context: Context, uri: Uri): ByteArray {
        val temporary = File.createTempFile("rom-patch-", ".tmp", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the patch.")
            input.use {
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) throw IOException("Could not read the patch.")
                        total += count
                        require(total <= RomPatcher.MAX_PATCH_BYTES) { "Patch exceeds the 128 MiB size limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            return temporary.readBytes()
        } finally {
            temporary.delete()
        }
    }

    private fun outputNames(stem: String) = generateSequence(0) { it + 1 }.map {
        if (it == 0) "$stem.gba" else "$stem ($it).gba"
    }

    /** SAF creates a new document. Check its identity before opening it for write. */
    private fun saveToFolder(context: Context, root: DocumentFile, stem: String, bytes: ByteArray): Pair<Uri, String>? {
        val existing = try {
            if (!root.canWrite()) return null
            root.listFiles()
        } catch (_: SecurityException) {
            return null
        }
        val names = existing.mapNotNull { it.name?.lowercase() }.toSet()
        val name = outputNames(stem).first { it.lowercase() !in names }
        val output = try {
            root.createFile("application/octet-stream", name)
        } catch (_: SecurityException) {
            null
        } ?: return null
        if (existing.any { it.uri == output.uri }) return null

        var saved = false
        try {
            val actualName = output.name?.takeIf { it.endsWith(".gba", ignoreCase = true) } ?: return null
            val stream = context.contentResolver.openOutputStream(output.uri, "w") ?: return null
            stream.use { it.write(bytes) }
            saved = true
            return output.uri to actualName
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        } finally {
            if (!saved && !output.delete()) throw IOException("Could not remove an incomplete ROM from the linked folder.")
        }
    }

    private fun savePrivately(context: Context, stem: String, bytes: ByteArray): File {
        val dir = File(context.filesDir, "patched_roms")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create DualDex’s patched ROM folder.")
        // Each run owns an empty directory, so the atomic move cannot replace another ROM.
        val destination = Files.createTempDirectory(dir.toPath(), "patch-").toFile()
        try {
            val temporary = File(destination, ".pending")
            FileOutputStream(temporary).use {
                it.write(bytes)
                it.fd.sync()
            }
            val output = File(destination, "$stem.gba")
            Files.move(temporary.toPath(), output.toPath(), ATOMIC_MOVE)
            return output
        } finally {
            File(destination, ".pending").delete()
            destination.delete() // Only an empty, failed staging directory can be removed.
        }
    }
}
