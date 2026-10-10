package com.dualdex.emulator.storage

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure naming and selection rules for save-state slots, resume and shared saves (#153). */
object SaveStateFiles {
    const val SLOT_COUNT = 10
    const val AUTO_RESUME = "auto_resume.state"
    const val QUICK_SAVE = "quicksave.state"
    const val UNDO_LOAD = "undo_load.state"

    /** Touched when a resume must not restore anything older (e.g. after loading a shared save). */
    const val RESUME_BARRIER = "resume_barrier"

    fun slotState(slot: Int) = "slot_$slot.state"
    fun slotThumbnail(slot: Int) = "slot_$slot.png"

    /**
     * The state to boot into: the newest of the auto-resume state and every manual state
     * (slots + quick save), so a crash that skipped the on-pause resume write still resumes
     * from the latest manual save. States not strictly newer than [barrierMs] are ignored.
     */
    fun newestResumeState(dir: File, barrierMs: Long = 0L): File? =
        (listOf(AUTO_RESUME, QUICK_SAVE) + (1..SLOT_COUNT).map(::slotState))
            .map { File(dir, it) }
            .filter { it.isFile && it.length() > 0L && it.lastModified() > barrierMs }
            .maxByOrNull { it.lastModified() }

    /** `<rom>.backup-<yyyyMMdd-HHmmss>.<ext>` */
    fun backupName(romBase: String, ext: String, timeMs: Long): String =
        "$romBase.backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timeMs))}.$ext"

    /** File-name stem other emulators would use for this ROM: original name minus extension, no path separators. */
    fun shareBaseName(romFileName: String?, fallback: String): String {
        val name = romFileName?.substringAfterLast('/')?.substringBeforeLast('.')?.trim().orEmpty()
        return name.ifEmpty { fallback }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    /**
     * Converts a core framebuffer to opaque ARGB pixels. XRGB8888 frames are read as the
     * renderer reads them (bytes R,G,B,X); the 4th byte is junk, so alpha is forced to 0xFF
     * instead of copied (a premultiplied copy of that byte wrecks the colours).
     */
    fun opaqueArgb(bytes: ByteArray, width: Int, height: Int, pitch: Int, format: Int): IntArray {
        val out = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            out[y * width + x] = if (format == FORMAT_RGB565) {
                val o = y * pitch + x * 2
                val p = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8)
                val r = (p shr 11) and 0x1F; val g = (p shr 5) and 0x3F; val b = p and 0x1F
                argb((r shl 3) or (r shr 2), (g shl 2) or (g shr 4), (b shl 3) or (b shr 2))
            } else {
                val o = y * pitch + x * 4
                argb(bytes[o].toInt() and 0xFF, bytes[o + 1].toInt() and 0xFF, bytes[o + 2].toInt() and 0xFF)
            }
        }
        return out
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    const val FORMAT_RGB565 = 2
}
