package com.dualdex.library

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.dualdex.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Distinct class so it does not collide with the debug-only FileProvider in the manifest merge. */
class UpdateFileProvider : FileProvider()

/**
 * In-app updater backed by GitHub Releases (#153). Release builds check when the library opens;
 * debug builds only on demand, since a debug APK cannot install over a release signature.
 */
object AppUpdater {
    private const val TAG = "DualDexUpdater"
    @Volatile private var autoChecked = false
    @Volatile private var pendingInstall: File? = null

    /** Called when the library screen opens; checks once per process in release builds. */
    fun onLibraryOpened(context: Context) {
        if (BuildConfig.DEBUG || autoChecked) return
        autoChecked = true
        check(context, userInitiated = false)
    }

    fun check(context: Context, userInitiated: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val update = runCatching {
                UpdateChecker.selectUpdate(httpGet(UpdateChecker.RELEASES_URL), BuildConfig.VERSION_NAME)
            }.onFailure { Log.w(TAG, "Update check failed: ${it.message}") }
            withContext(Dispatchers.Main) {
                val found = update.getOrNull()
                when {
                    found != null -> prompt(context, found)
                    !userInitiated -> Unit
                    update.isFailure -> toast(context, "Update check failed: ${update.exceptionOrNull()?.message}")
                    else -> toast(context, "DualDex ${BuildConfig.VERSION_NAME} is up to date")
                }
            }
        }
    }

    private fun prompt(context: Context, update: AvailableUpdate) {
        val debugNote = if (BuildConfig.DEBUG) "\n\nThis is a debug build; Android will refuse to install a release APK over it." else ""
        runCatching {
            com.dualdex.companion.ui.DualDexComponents.showDialog(
                AlertDialog.Builder(context)
                    .setTitle("Update available: ${update.name}")
                    .setMessage(update.notes.take(1500).ifBlank { update.tag } + debugNote)
                    .setPositiveButton("Download & install") { _, _ -> download(context, update) }
                    .setNegativeButton("Later", null)
            )
        }.onFailure { toast(context, "Update ${update.tag} available") }
    }

    private fun download(context: Context, update: AvailableUpdate) {
        val app = context.applicationContext
        toast(context, "Downloading ${update.apkName}…")
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, File(update.apkName).name)
                dir.listFiles()?.filter { it != apk }?.forEach { it.delete() }
                if (!(apk.exists() && apk.length() == update.apkSize)) {
                    val part = File(dir, apk.name + ".part")
                    open(update.apkUrl).inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                    if (update.apkSize >= 0 && part.length() != update.apkSize) {
                        part.delete()
                        error("size mismatch (${part.length()} of ${update.apkSize} bytes)")
                    }
                    apk.delete()
                    check(part.renameTo(apk)) { "could not finalize download" }
                }
                apk
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { install(app, it) }
                    .onFailure { toast(app, "Update download failed: ${it.message}") }
            }
        }
    }

    private fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingInstall = apk
            toast(context, "Allow DualDex to install updates, then return")
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        pendingInstall = null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Resume an install that was waiting on the "install unknown apps" permission. */
    fun onHostResume(context: Context) {
        val apk = pendingInstall ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            if (apk.exists()) install(context.applicationContext, apk) else pendingInstall = null
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "DualDex/${BuildConfig.VERSION_NAME}")
        if (responseCode !in 200..299) error("HTTP $responseCode")
    }

    private fun httpGet(url: String): String = open(url).inputStream.bufferedReader().use { it.readText() }

    private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}
