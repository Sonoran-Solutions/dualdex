package com.dualdex.library

import java.io.File
import java.io.InputStream

/**
 * Pure helpers for frontend launches (ES-DE, Cocoon, iiSU, Daijisho). Frontends pass the ROM as
 * `intent.data` or as one of a handful of string extras; see docs/FRONTEND_LAUNCH.md.
 */
object FrontendLaunch {
    /** Extra keys checked in order after `intent.data`. */
    val ROM_EXTRA_KEYS = listOf("rom", "ROM", "path", "file", "uri")

    /** The first non-blank ROM reference: [data] wins, then extras in [ROM_EXTRA_KEYS] order. */
    fun romReference(data: String?, extras: Map<String, Any?>): String? {
        if (!data.isNullOrBlank()) return data.trim()
        for (key in ROM_EXTRA_KEYS) {
            val value = extras[key]?.toString()?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    /** A plain filesystem path for `/abs/path` or `file://` references, else null (content:// etc.). */
    fun filesystemPath(reference: String): String? = when {
        reference.startsWith("/") -> reference
        reference.startsWith("file://") -> java.net.URLDecoder.decode(reference.removePrefix("file://"), "UTF-8")
        else -> null
    }

    /** Last path segment of a reference, sanitized for use as a local file name. */
    fun displayName(reference: String): String {
        val raw = java.net.URLDecoder.decode(reference, "UTF-8").substringAfterLast('/').substringAfterLast(':')
        val clean = raw.replace(Regex("[^A-Za-z0-9._ ()\\[\\]-]"), "_").trim()
        return clean.ifEmpty { "frontend_rom.gba" }
    }

    /**
     * Copy [source] into [target] unless [target] already holds exactly the same bytes.
     * Returns true when bytes were written. Writes go through a `.part` file and a rename so a
     * crash never leaves a truncated ROM in place.
     */
    fun copyIfChanged(openSource: () -> InputStream, sourceLength: Long, target: File): Boolean {
        if (target.exists() && (sourceLength < 0 || target.length() == sourceLength) && sameBytes(openSource, target)) {
            return false
        }
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        openSource().use { input -> part.outputStream().use { input.copyTo(it) } }
        if (!part.renameTo(target)) {
            target.delete()
            check(part.renameTo(target)) { "Could not install ${target.name}" }
        }
        return true
    }

    private fun sameBytes(openSource: () -> InputStream, target: File): Boolean {
        openSource().buffered().use { a ->
            target.inputStream().buffered().use { b ->
                while (true) {
                    val x = a.read()
                    if (x != b.read()) return false
                    if (x == -1) return true
                }
            }
        }
    }
}
