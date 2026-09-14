package com.dualdex.emulator

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Manages Storage Access Framework (SAF) persistable read permissions and hygiene
 * for individually opened ROMs, ensuring Continue works across restart/reboot.
 */
object RomUriPermissionManager {

    private const val TAG = "RomUriPermission"

    const val RECOVERY_MESSAGE =
        "DualDex can no longer access this ROM. Select the ROM again to continue. Your DualDex saves are still preserved."

    /**
     * Attempts to take a persistable read URI permission for an opened ROM URI.
     * Returns true if persistable access is confirmed (or local file URI), false if provider refuses or fails.
     */
    fun takePersistableReadPermission(contentResolver: ContentResolver?, uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme

        // Direct file:// or local paths are inherently durable as long as the file exists on disk
        if (scheme == null || scheme == "file") {
            return true
        }

        if (contentResolver == null) {
            Log.w(TAG, "ContentResolver is null; cannot take persistable URI permission for $uri")
            return false
        }

        return try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.i(TAG, "Successfully persisted read URI permission for $uri")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Document provider does not support persistable URI permissions for $uri: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Recoverable error taking persistable URI permission for $uri: ${e.message}")
            false
        }
    }

    /**
     * Determines whether an older ROM URI grant can safely be released.
     * Conservative policy:
     * - Never releases protected URIs (such as ROMs folder tree or saves folder tree).
     * - Never releases tree URIs.
     * - Never releases non-content URIs.
     * - Never releases if old URI matches the new URI.
     */
    fun shouldReleaseOldUri(
        oldUriStr: String?,
        newUriStr: String?,
        protectedUris: Set<String>
    ): Boolean {
        if (oldUriStr.isNullOrBlank()) return false
        if (oldUriStr == newUriStr) return false
        if (protectedUris.contains(oldUriStr)) return false
        if (!isContentUri(oldUriStr)) return false
        if (isTreeUri(oldUriStr)) return false
        return true
    }

    /**
     * Releases persistable read permission for an older individually opened ROM URI if redundant.
     */
    fun releasePersistableReadPermissionIfRedundant(
        contentResolver: ContentResolver?,
        oldUriStr: String?,
        newUriStr: String?,
        protectedUris: Set<String>
    ) {
        if (!shouldReleaseOldUri(oldUriStr, newUriStr, protectedUris)) return
        val oldUri = Uri.parse(oldUriStr) ?: return

        try {
            contentResolver?.releasePersistableUriPermission(
                oldUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.i(TAG, "Released redundant persistable read permission for old ROM: $oldUriStr")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException releasing permission for $oldUriStr: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed releasing permission for $oldUriStr: ${e.message}")
        }
    }

    fun isContentUri(uriStr: String?): Boolean {
        return uriStr?.startsWith("content://", ignoreCase = true) == true
    }

    fun isTreeUri(uriStr: String?): Boolean {
        if (uriStr == null) return false
        return uriStr.contains("/tree/", ignoreCase = true) || uriStr.contains("%2Ftree%2F", ignoreCase = true)
    }
}
