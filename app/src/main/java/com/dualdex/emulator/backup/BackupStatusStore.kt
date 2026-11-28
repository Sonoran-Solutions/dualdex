package com.dualdex.emulator.backup

import android.content.Context

/** Non-sensitive lifecycle status for the Saves screen; never records paths, identities, or save contents. */
object BackupStatusStore {
    private const val PREFS = "dualdex_backup_status"
    private const val KEY = "status"
    private const val DETAIL = "detail"

    enum class Status {
        NOT_VERIFIED, PREPARING, ESSENTIAL_SELECTED, OPTIONAL_OMITTED, REFUSED_OR_INCOMPLETE,
        RESTORE_PENDING, RESTORE_VALIDATION_FAILED, RESTORED_DATA_READY
    }

    fun set(context: Context, status: Status, detail: String = "") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, status.name).putString(DETAIL, detail.take(160)).apply()
    }

    fun get(context: Context): Pair<Status, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val status = runCatching { Status.valueOf(prefs.getString(KEY, Status.NOT_VERIFIED.name)!!) }
            .getOrDefault(Status.NOT_VERIFIED)
        return status to (prefs.getString(DETAIL, "") ?: "")
    }
}
