package com.dualdex.companion.ui

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dualdex.emulator.SaveStateManager
import com.dualdex.emulator.backup.StagedRestoreStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Explicit, per-file choices; closing any dialog keeps all local and pending data. */
internal object RestoreReviewDialog {
    fun show(context: Context, owner: View, scope: CoroutineScope, manager: SaveStateManager,
             sessions: List<StagedRestoreStore.Session>, refresh: () -> Unit, page: Int = 0) {
        val entries = sessions.flatMap { session -> session.entries.map { session.retainedLocal to it } }
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun label(text: String) = TextView(context).apply {
            this.text = text
            setTextColor(DualDexTheme.Color.textPrimary)
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        }
        body.addView(label("Nothing is imported automatically. Close the current game before importing. " +
            "Backup files and retained local copies stay here after import; no ROM is needed to review them."))
        sessions.mapNotNull { it.error }.forEach { body.addView(label(it)) }
        if (entries.isEmpty()) body.addView(label("No complete restored files are available to import."))
        var listDialog: AlertDialog? = null
        val from = page * 30
        entries.drop(from).take(30).forEach { (retained, review) ->
            val status = when {
                review.source.target == null -> "Reference record — retained with backup"
                review.identical -> "Already present locally"
                review.local == null -> "No local file"
                else -> "Local file exists — keep or explicitly replace"
            }
            val button = DualDexComponents.secondaryButton(context,
                "${if (retained) "Retained local copy" else "Restored backup"}: ${review.source.title}\n${review.source.path}\n$status") {
                listDialog?.dismiss()
                details(context, owner, scope, manager, review, refresh)
            }
            body.addView(button)
        }
        if (entries.isNotEmpty()) body.addView(label("Files ${from + 1}–${minOf(from + 30, entries.size)} of ${entries.size}"))
        val builder = AlertDialog.Builder(context).setTitle("Review restored saves")
            .setView(ScrollView(context).apply { addView(body) }).setNegativeButton("Close", null)
        if (from + 30 < entries.size) builder.setNeutralButton("Next") { _, _ -> show(context, owner, scope, manager, sessions, refresh, page + 1) }
        if (page > 0) builder.setPositiveButton("Previous") { _, _ -> show(context, owner, scope, manager, sessions, refresh, page - 1) }
        listDialog = DualDexComponents.showDialog(builder, owner)
    }

    private fun details(context: Context, owner: View, scope: CoroutineScope, manager: SaveStateManager,
                        review: StagedRestoreStore.Review, refresh: () -> Unit) {
        fun date(value: Long) = if (value <= 0) "Date unavailable" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
        val source = review.source
        val local = review.local
        val message = buildString {
            append("${source.title}\n\nROM SHA-256: ${source.romHash ?: "Unassigned; no ROM identity is inferred from the filename"}\n\n")
            append("Restored file: ${source.path}\n${source.version.size} bytes • ${date(source.version.modified)}\n\n")
            append(if (local == null) "No local file at this destination.\n" else "Local file: ${local.size} bytes • ${date(local.modified)}\n")
            if (local != null && local.modified > source.version.modified && source.version.modified > 0) append("The local file has a newer date.\n")
            append("Dates alone do not determine which save is correct.\n\n")
            if (source.target == null) append("This reference record stays with the backup. Legacy assignments and automatic-resume safety are managed locally.")
            else append("Import destination: ${source.target}\n\nClose the current game first. Imported states are not loaded automatically. Open the matching ROM and choose a state manually; incompatible states may be refused. Legacy files remain unassigned.")
        }
        val builder = AlertDialog.Builder(context).setTitle("Restored file")
            .setMessage(message).setNegativeButton(if (local == null) "Keep staged" else "Keep local", null)
        if (source.target != null && !review.identical) {
            val replacing = local != null
            builder.setPositiveButton(if (replacing) "Review replacement…" else "Import…") { _, _ ->
                DualDexComponents.confirm(context, if (replacing) "Replace this local file?" else "Import this restored file?",
                    "${source.path}\nROM: ${source.romHash ?: "Unassigned legacy data"}\n\n" +
                        if (replacing) "The current local file will be retained as a separate copy in this review list. Existing recovery copies are preserved. If either file changed since review, import stops."
                        else "This creates the file only if the destination is still missing. The backup copy remains available for another review.",
                    if (replacing) "Replace and retain local" else "Import file", owner = owner) {
                    val progress = DualDexComponents.showDialog(AlertDialog.Builder(context)
                        .setTitle("Importing restored file")
                        .setMessage("Checking the reviewed files and retaining local data. Large backups may take a while.")
                        .setCancelable(false), owner)
                    scope.launch {
                        val result = try {
                            withContext(Dispatchers.IO + NonCancellable) {
                                runCatching { manager.importRestoredSave(review, if (replacing) StagedRestoreStore.Decision.REPLACE_LOCAL else StagedRestoreStore.Decision.IMPORT_MISSING) }
                                    .getOrDefault(StagedRestoreStore.Result.FAILED)
                            }
                        } finally { progress.dismiss() }
                        if (owner.isAttachedToWindow) {
                            val text = when (result) {
                                StagedRestoreStore.Result.IMPORTED -> "File imported. Backup and retained local copies remain available."
                                StagedRestoreStore.Result.IMPORTED_SYNC_UNCONFIRMED -> "File imported, but storage sync was not confirmed. Keep the retained copies and check storage."
                                StagedRestoreStore.Result.CLOSE_GAME -> "Close the current game in Settings, then review and import again."
                                StagedRestoreStore.Result.REVIEW_AGAIN -> "Files changed since review. Review the current versions before importing."
                                StagedRestoreStore.Result.ALREADY_PRESENT -> "The same file is already present locally."
                                StagedRestoreStore.Result.KEPT_LOCAL -> "Local file kept."
                                StagedRestoreStore.Result.FAILED -> "Import could not complete. Review again; staged data and retained copies remain available."
                            }
                            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                            refresh()
                        }
                    }
                }
            }
        }
        DualDexComponents.showDialog(builder, owner)
    }
}
