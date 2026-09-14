package com.dualdex.emulator.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.dualdex.emulator.RomIdentity
import com.dualdex.settings.SettingsManager
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

open class SafMirrorStore(private val context: Context? = null) {

    private val settingsManager by lazy { context?.let { SettingsManager(it) } }

    open fun getSafFolder(): DocumentFile? {
        val uriStr = settingsManager?.savesFolderUri ?: return null
        val ctx = context ?: return null
        return try {
            val treeUri = Uri.parse(uriStr)
            val root = DocumentFile.fromTreeUri(ctx, treeUri)
            if (root != null && root.canWrite()) root else null
        } catch (e: SecurityException) {
            Log.w(TAG, "SAF permission revoked: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to access SAF saves folder: ${e.message}")
            null
        }
    }

    open fun isSafConfigured(): Boolean {
        return settingsManager?.savesFolderUri != null
    }

    private fun getOrCreateSafRomDir(safRoot: DocumentFile, storageKey: String): DocumentFile? {
        return try {
            val rootName = safRoot.name ?: ""
            val savesRoot = when {
                rootName.equals("Saves", ignoreCase = true) -> safRoot
                rootName.equals("DualDex", ignoreCase = true) -> {
                    safRoot.findFile("Saves") ?: safRoot.createDirectory("Saves") ?: safRoot
                }
                else -> {
                    val dualDexDir = safRoot.findFile("DualDex") ?: safRoot.createDirectory("DualDex") ?: safRoot
                    dualDexDir.findFile("Saves") ?: dualDexDir.createDirectory("Saves") ?: dualDexDir
                }
            }
            savesRoot.findFile(storageKey) ?: savesRoot.createDirectory(storageKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating SAF directory for $storageKey: ${e.message}", e)
            null
        }
    }

    /**
     * Mirror a single canonical file to SAF storage.
     */
    open fun mirrorFile(identity: RomIdentity, fileName: String, canonicalFile: File): MirrorStatus {
        if (!isSafConfigured()) return MirrorStatus.UNAVAILABLE

        val safRoot = try {
            getSafFolder()
        } catch (e: SecurityException) {
            return MirrorStatus.PERMISSION_REVOKED
        }

        if (safRoot == null) {
            return if (isSafConfigured()) MirrorStatus.PERMISSION_REVOKED else MirrorStatus.UNAVAILABLE
        }

        if (!canonicalFile.exists() || canonicalFile.length() == 0L) {
            return MirrorStatus.OUT_OF_SYNC
        }

        return try {
            val romDir = getOrCreateSafRomDir(safRoot, identity.storageKey)
                ?: return MirrorStatus.FAILED

            val doc = romDir.findFile(fileName)
                ?: romDir.createFile("application/octet-stream", fileName)
                ?: return MirrorStatus.FAILED

            val resolver = context?.contentResolver ?: return MirrorStatus.FAILED
            resolver.openOutputStream(doc.uri)?.use { output ->
                FileInputStream(canonicalFile).use { input ->
                    input.copyTo(output)
                }
            }

            if (doc.length() == canonicalFile.length()) {
                MirrorStatus.IN_SYNC
            } else {
                Log.w(TAG, "SAF mirror size mismatch for $fileName: doc=${doc.length()}, canonical=${canonicalFile.length()}")
                MirrorStatus.FAILED
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SAF permission error mirroring $fileName: ${e.message}")
            MirrorStatus.PERMISSION_REVOKED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mirror $fileName to SAF: ${e.message}", e)
            MirrorStatus.FAILED
        }
    }

    open fun calculateStreamSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Check current sync status between canonical and SAF mirror.
     * Compares both file length and SHA-256 content digest so equal-sized different saves are OUT_OF_SYNC.
     */
    open fun checkMirrorStatus(identity: RomIdentity, fileName: String, canonicalFile: File): MirrorStatus {
        if (!isSafConfigured()) return MirrorStatus.UNAVAILABLE

        val safRoot = getSafFolder() ?: return MirrorStatus.UNAVAILABLE
        if (!canonicalFile.exists()) return MirrorStatus.IN_SYNC

        return try {
            val romDir = getOrCreateSafRomDir(safRoot, identity.storageKey)
                ?: return MirrorStatus.OUT_OF_SYNC
            val doc = romDir.findFile(fileName) ?: return MirrorStatus.OUT_OF_SYNC

            if (doc.exists() && doc.length() == canonicalFile.length()) {
                if (canonicalFile.length() == 0L) return MirrorStatus.IN_SYNC
                val canonicalHash = canonicalFile.inputStream().use { calculateStreamSha256(it) }
                val docStream = context?.contentResolver?.openInputStream(doc.uri) ?: return MirrorStatus.OUT_OF_SYNC
                val docHash = docStream.use { calculateStreamSha256(it) }
                if (canonicalHash.equals(docHash, ignoreCase = true)) {
                    MirrorStatus.IN_SYNC
                } else {
                    MirrorStatus.OUT_OF_SYNC
                }
            } else {
                MirrorStatus.OUT_OF_SYNC
            }
        } catch (e: Exception) {
            MirrorStatus.OUT_OF_SYNC
        }
    }

    /**
     * Push all canonical files in canonicalDir to SAF mirror.
     */
    open fun syncCanonicalToSaf(identity: RomIdentity, canonicalDir: File): Int {
        val safRoot = getSafFolder() ?: return 0
        if (!canonicalDir.exists() || !canonicalDir.isDirectory) return 0

        val romDir = getOrCreateSafRomDir(safRoot, identity.storageKey) ?: return 0
        var count = 0

        val files = canonicalDir.listFiles { f ->
            f.isFile && !f.name.endsWith(".tmp") && !f.name.endsWith(".bak")
        } ?: return 0

        for (file in files) {
            try {
                val doc = romDir.findFile(file.name)
                    ?: romDir.createFile("application/octet-stream", file.name)

                if (doc != null) {
                    context?.contentResolver?.openOutputStream(doc.uri)?.use { out ->
                        FileInputStream(file).use { inp ->
                            inp.copyTo(out)
                        }
                    }
                    if (doc.length() == file.length()) {
                        count++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync ${file.name} to SAF: ${e.message}")
            }
        }

        return count
    }

    companion object {
        private const val TAG = "SafMirrorStore"
    }
}
