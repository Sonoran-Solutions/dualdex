package com.dualdex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dualdex.library.FrontendLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Launch target for frontends (ES-DE, Cocoon, iiSU, Daijisho). Exported with no intent filter so
 * DualDex never shows up twice in "Open with"; frontends address it by component name.
 *
 * A thin trampoline: it resolves (and if needed copies) the ROM, hands the readable path to the
 * single [MainActivity] instance, and finishes. MainActivity is the only core owner, so a frontend
 * launch can never run a second activity against the process-wide core and save manager. Back in
 * MainActivity after a frontend launch moves the task to the back, which returns to the frontend.
 * See docs/FRONTEND_LAUNCH.md.
 */
class FrontendLaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchFrom(intent)
    }

    /** Forwards a readable ROM file to the owning activity (a path, not a file:// Uri, which StrictMode rejects). */
    private fun playRom(file: File, title: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_ROM_PATH, file.absolutePath)
                .putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finishAndRemoveTask()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchFrom(intent)
    }

    private fun launchFrom(intent: Intent) {
        @Suppress("DEPRECATION")
        val extras = intent.extras?.let { b -> b.keySet().associateWith { b.get(it) } }.orEmpty()
        val reference = FrontendLaunch.romReference(intent.dataString, extras)
        if (reference == null) {
            Toast.makeText(this, "No ROM was passed by the frontend", Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }
        val name = FrontendLaunch.displayName(reference)
        val title = name.substringBeforeLast('.')
        val realPath = FrontendLaunch.filesystemPath(reference)?.let(::File)?.takeIf { it.isFile && it.canRead() }
        if (realPath != null) {
            playRom(realPath, title)
            return
        }
        // Not a readable path (content:// or a path we lack permission for): copy once, then play.
        val source = FrontendLaunch.filesystemPath(reference)?.let { Uri.fromFile(File(it)) } ?: Uri.parse(reference)
        lifecycleScope.launch {
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(File(filesDir, "frontend_roms"), name)
                    val length = contentResolver.openAssetFileDescriptor(source, "r")?.use { it.length } ?: -1L
                    FrontendLaunch.copyIfChanged(
                        openSource = { contentResolver.openInputStream(source) ?: error("cannot open $source") },
                        sourceLength = length,
                        target = target
                    )
                    target
                }
            }
            copy.onSuccess { playRom(it, title) }.onFailure {
                Log.w("DualDex", "Frontend ROM copy failed: ${it.message}")
                Toast.makeText(this@FrontendLaunchActivity, "Cannot read ROM: ${it.message}", Toast.LENGTH_LONG).show()
                finishAndRemoveTask()
            }
        }
    }

    companion object {
        const val EXTRA_ROM_PATH = "com.dualdex.extra.FRONTEND_ROM_PATH"
        const val EXTRA_TITLE = "com.dualdex.extra.FRONTEND_TITLE"
    }
}
