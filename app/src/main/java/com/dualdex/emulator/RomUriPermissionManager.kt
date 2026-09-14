package com.dualdex.emulator

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Result of attempting to persist read permissions for a ROM URI.
 *
 * @property isDurable True if the ROM can be loaded and persisted as a durable Continue target.
 * @property isNewlyAcquired True if this operation newly persisted a permission that was not already held.
 */
data class PersistableGrantResult(
    val isDurable: Boolean,
    val isNewlyAcquired: Boolean
)

/**
 * State of a URI permission in persistedUriPermissions prior to acquisition attempt.
 */
enum class PreExistingGrantState {
    PRESENT,
    ABSENT,
    UNKNOWN
}

/**
 * Manages Storage Access Framework (SAF) persistable read permissions and hygiene
 * for individually opened ROMs, ensuring Continue works across restart/reboot.
 */
object RomUriPermissionManager {

    private const val TAG = "RomUriPermission"

    const val RECOVERY_MESSAGE =
        "DualDex can no longer access this ROM. Select the ROM again to continue. Your DualDex saves are still preserved."

    /**
     * Inspects the ContentResolver's persisted URI permissions to determine whether the URI
     * was already held prior to an acquisition attempt.
     */
    fun checkPreExistingGrantState(contentResolver: ContentResolver?, uri: Uri?): PreExistingGrantState {
        if (contentResolver == null || uri == null) return PreExistingGrantState.UNKNOWN
        return try {
            val list = contentResolver.persistedUriPermissions
            val exists = list.any { perm ->
                perm.isReadPermission && (perm.uri == uri || perm.uri.toString() == uri.toString())
            }
            if (exists) PreExistingGrantState.PRESENT else PreExistingGrantState.ABSENT
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException querying persistedUriPermissions for $uri: ${e.message}")
            PreExistingGrantState.UNKNOWN
        } catch (e: Exception) {
            Log.w(TAG, "Error querying persistedUriPermissions for $uri: ${e.message}")
            PreExistingGrantState.UNKNOWN
        }
    }

    /**
     * Checks if the given URI is already held in the ContentResolver's persisted URI permissions.
     */
    fun isUriPersisted(contentResolver: ContentResolver?, uri: Uri?): Boolean {
        return checkPreExistingGrantState(contentResolver, uri) == PreExistingGrantState.PRESENT
    }

    /**
     * Pure helper to determine the durability and newly-acquired status of a ROM grant.
     * Only local/no-scheme and file:// URIs are inherently durable.
     * Non-content, non-file schemes (e.g. http://, custom://) are not durable.
     */
    fun determineGrantResult(
        uriStr: String?,
        preExistingState: PreExistingGrantState,
        takePermissionSuccess: Boolean
    ): PersistableGrantResult {
        if (uriStr.isNullOrBlank()) {
            return PersistableGrantResult(isDurable = false, isNewlyAcquired = false)
        }
        if (isLocalOrFileUri(uriStr)) {
            // Direct file:// or local paths are inherently durable as long as the file exists on disk
            return PersistableGrantResult(isDurable = true, isNewlyAcquired = false)
        }
        if (!isContentUri(uriStr)) {
            // Arbitrary non-content, non-file schemes (e.g. http://, ftp://, custom://) are not durable
            return PersistableGrantResult(isDurable = false, isNewlyAcquired = false)
        }
        if (!takePermissionSuccess) {
            return PersistableGrantResult(isDurable = false, isNewlyAcquired = false)
        }
        return PersistableGrantResult(
            isDurable = true,
            isNewlyAcquired = (preExistingState == PreExistingGrantState.ABSENT)
        )
    }

    /**
     * Backward-compatible overload accepting a boolean indicating whether the URI was already persisted.
     */
    fun determineGrantResult(
        uriStr: String?,
        isAlreadyPersisted: Boolean,
        takePermissionSuccess: Boolean
    ): PersistableGrantResult {
        val state = if (isAlreadyPersisted) PreExistingGrantState.PRESENT else PreExistingGrantState.ABSENT
        return determineGrantResult(uriStr, state, takePermissionSuccess)
    }

    /**
     * Checks whether a URI string refers to a local file (file:// scheme or no-scheme path).
     */
    fun isLocalOrFileUri(uriStr: String?): Boolean {
        if (uriStr == null) return false
        if (!uriStr.contains("://")) return true
        return uriStr.startsWith("file://", ignoreCase = true)
    }

    /**
     * Attempts to take a persistable read URI permission for an opened ROM URI.
     * Returns [PersistableGrantResult] indicating whether persistable access is confirmed (or local file URI)
     * and whether the grant was newly acquired during this attempt.
     */
    fun takePersistableReadPermission(
        contentResolver: ContentResolver?,
        uri: Uri?,
        preExistingStateChecker: ((Uri) -> PreExistingGrantState)? = null
    ): PersistableGrantResult {
        if (uri == null) return PersistableGrantResult(isDurable = false, isNewlyAcquired = false)
        val scheme = uri.scheme

        // Direct file:// or local paths are inherently durable as long as the file exists on disk
        if (scheme == null || scheme == "file") {
            return PersistableGrantResult(isDurable = true, isNewlyAcquired = false)
        }

        if (scheme != "content") {
            Log.w(TAG, "Unsupported non-content scheme '$scheme' for ROM URI: $uri; not durable")
            return PersistableGrantResult(isDurable = false, isNewlyAcquired = false)
        }

        if (contentResolver == null) {
            Log.w(TAG, "ContentResolver is null; cannot take persistable URI permission for $uri")
            return PersistableGrantResult(isDurable = false, isNewlyAcquired = false)
        }

        val preExistingState = preExistingStateChecker?.invoke(uri)
            ?: checkPreExistingGrantState(contentResolver, uri)

        return try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.i(TAG, "Successfully persisted read URI permission for $uri (preExistingState=$preExistingState)")
            determineGrantResult(
                uriStr = uri.toString(),
                preExistingState = preExistingState,
                takePermissionSuccess = true
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Document provider does not support persistable URI permissions for $uri: ${e.message}")
            determineGrantResult(
                uriStr = uri.toString(),
                preExistingState = preExistingState,
                takePermissionSuccess = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Recoverable error taking persistable URI permission for $uri: ${e.message}")
            determineGrantResult(
                uriStr = uri.toString(),
                preExistingState = preExistingState,
                takePermissionSuccess = false
            )
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

    /**
     * Determines whether a candidate ROM URI's persistable grant should be released when its switch fails.
     * Conservative policy:
     * - Only releases if the grant was newly acquired during this exact direct-open attempt.
     * - Never releases the currently active Continue target URI.
     * - Never releases protected URIs (such as ROMs folder tree or saves folder tree).
     * - Never releases tree URIs.
     * - Never releases non-content URIs.
     */
    fun shouldReleaseFailedCandidateUri(
        candidateUriStr: String?,
        currentContinueUriStr: String?,
        protectedUris: Set<String>,
        isNewlyAcquired: Boolean
    ): Boolean {
        if (!isNewlyAcquired) return false
        if (candidateUriStr.isNullOrBlank()) return false
        if (candidateUriStr == currentContinueUriStr) return false
        if (protectedUris.contains(candidateUriStr)) return false
        if (!isContentUri(candidateUriStr)) return false
        if (isTreeUri(candidateUriStr)) return false
        return true
    }

    /**
     * Releases persistable read permission for a failed candidate ROM URI if it was newly acquired
     * and safe to revoke. Best-effort and non-fatal.
     */
    fun releasePersistableReadPermissionOnFailure(
        contentResolver: ContentResolver?,
        candidateUriStr: String?,
        currentContinueUriStr: String?,
        protectedUris: Set<String>,
        isNewlyAcquired: Boolean
    ) {
        if (!shouldReleaseFailedCandidateUri(candidateUriStr, currentContinueUriStr, protectedUris, isNewlyAcquired)) return
        val uri = try {
            Uri.parse(candidateUriStr)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot parse candidate URI to release: $candidateUriStr")
            return
        } ?: return

        try {
            contentResolver?.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.i(TAG, "Released newly acquired persistable read permission for failed candidate ROM: $candidateUriStr")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException releasing permission for failed candidate $candidateUriStr: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed releasing permission for failed candidate $candidateUriStr: ${e.message}")
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
