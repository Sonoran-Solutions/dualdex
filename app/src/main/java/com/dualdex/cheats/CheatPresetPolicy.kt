package com.dualdex.cheats

import com.dualdex.emulator.RomIdentity

/** Exact target approvals only. Live-memory/profile verification grants no cheat authority.
 * Production intentionally has no approvals: see docs/CHEAT_COMPATIBILITY.md.
 * Constructor injection is for synthetic tests; it does not install production approvals. */
class CheatPresetPolicy internal constructor(
    private val catalog: Map<String, List<CheatItem>> = emptyMap()
) {
    fun presets(identity: RomIdentity): List<CheatItem> =
        if (identity.isValid) catalog[identity.sha256.lowercase()].orEmpty().map {
            it.copy(isPreset = true, enabled = false, disabledReason = null)
        } else emptyList()

    fun approves(identity: RomIdentity, entry: CheatItem): Boolean =
        entry.isPreset && presets(identity).any { it.id == entry.id && it.code == entry.code } &&
            CheatCodePayload.normalize(entry.code) != null

    companion object {
        val production = CheatPresetPolicy()
    }
}

/** mGBA libretro GBA grammar: 8+8 or 8+4 hex digits, separated by whitespace/+.
 * Canonicalize separators for its fixed-width splitter; keep multiline code order.
 * This checks syntax only, never compatibility or cheat semantics. */
object CheatCodePayload {
    fun normalize(code: String): String? {
        val text = code.lines().filterNot {
            it.trim().startsWith("#") || it.trim().startsWith("//")
        }.joinToString(" ").trim()
        if (text.isEmpty()) return null
        val tokens = text.split(Regex("[\\s+]+"))
        if (tokens.size % 2 != 0) return null
        val pairs = tokens.chunked(2)
        if (pairs.any { (a, b) ->
            !a.matches(Regex("[0-9a-fA-F]{8}")) ||
                !b.matches(Regex("(?:[0-9a-fA-F]{4}|[0-9a-fA-F]{8})"))
        }) return null
        return pairs.joinToString("\n") { it.joinToString(" ") }
    }
}

data class CheatResult(val accepted: Boolean, val message: String) {
    companion object {
        val stale = CheatResult(false, "Cheats not applied: the loaded ROM changed or is unavailable.")
        val unavailable = CheatResult(false, "No verified built-in cheats for this exact ROM.")
    }
}
