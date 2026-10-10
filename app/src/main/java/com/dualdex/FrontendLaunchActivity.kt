package com.dualdex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.dualdex.library.FrontendLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Launch target for frontends (ES-DE, Cocoon, iiSU, Daijisho). Exported with no intent filter so
 * DualDex never shows up twice in "Open with"; frontends address it by component name. Runs in
 * its own task (`taskAffinity=""`) and Back returns straight to the frontend. See
 * docs/FRONTEND_LAUNCH.md.
 */
class FrontendLaunchActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishAndRemoveTask()
        })
        if (savedInstanceState == null) launchFrom(intent)
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
            playRom(Uri.fromFile(realPath), title)
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
            copy.onSuccess { playRom(Uri.fromFile(it), title) }.onFailure {
                Log.w("DualDex", "Frontend ROM copy failed: ${it.message}")
                Toast.makeText(this@FrontendLaunchActivity, "Cannot read ROM: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
