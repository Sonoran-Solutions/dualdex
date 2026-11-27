package com.dualdex

import com.dualdex.romhack.RomCompatibilityStatus

/**
 * Summary of enabled/disabled companion capabilities for diagnostics reporting.
 */
data class CompanionCapabilitiesSummary(
    val liveMemoryRead: Boolean = false,
    val battleStateRead: Boolean = false,
    val interactiveBattleControls: Boolean = false,
    val battleAutoOpen: Boolean = false,
    val smartFastForward: Boolean = false,
    val moveTileDamage: Boolean = false,
    val moveTileEffectiveness: Boolean = false
)

/**
 * Privacy-safe diagnostics payload for GitHub issue reports.
 *
 * Intentionally excludes ROM file paths/URIs, ROM filenames, save contents, user credentials,
 * Pokemon nicknames, and raw memory dumps.
 */
data class DiagnosticsSnapshot(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val androidRelease: String,
    val androidSdkInt: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val cpuAbi: String,
    val displays: List<DisplayDescriptor>,
    val activeRomSha256: String?,
    val activeProfileId: String?,
    val activeProfileName: String?,
    val compatibilityStatus: RomCompatibilityStatus?,
    val exactRuntimeVerified: Boolean,
    val engine: String?,
    val baseGame: String?,
    val capabilities: CompanionCapabilitiesSummary
)

/**
 * Pure JVM formatter producing a clean, paste-ready Markdown diagnostics block for GitHub issues.
 */
object DiagnosticsFormatter {

    private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
    private val SENSITIVE_PATH_OR_URI_REGEX = Regex(
        pattern = "(?i)(content://|file://|https?://|/storage/|/sdcard/|/data/|/mnt/|/emulated/|[a-z]:\\\\|\\.gba\\b|\\.sav\\b|\\.srm\\b|\\.ss[0-9]\\b)"
    )

    fun format(snapshot: DiagnosticsSnapshot): String {
        val versionLine = "${BuildInfoFormatter.versionLabel(sanitizeField(snapshot.versionName, "unknown"))} " +
            "(versionCode ${snapshot.versionCode}, ${sanitizeField(snapshot.buildType, "unknown")})"
        val androidLine = "${sanitizeField(snapshot.androidRelease, "unknown")} (SDK ${snapshot.androidSdkInt})"
        val manufacturer = sanitizeField(snapshot.deviceManufacturer, "unknown")
        val model = sanitizeField(snapshot.deviceModel, "unknown")
        val abi = sanitizeField(snapshot.cpuAbi, "unknown")
        val deviceLine = "$manufacturer / $model (ABI: $abi)"
        val displaysLine = formatDisplays(snapshot.displays)

        val normalizedSha = normalizeSha256(snapshot.activeRomSha256)
        val hasRom = normalizedSha != null

        val shaLine = normalizedSha ?: "none"
        val profileLine = if (!hasRom) {
            "none"
        } else {
            val id = sanitizeField(snapshot.activeProfileId, "")
            val name = sanitizeField(snapshot.activeProfileName, "")
            when {
                name.isNotEmpty() && id.isNotEmpty() -> "$name (`$id`)"
                name.isNotEmpty() -> name
                id.isNotEmpty() -> "`$id`"
                else -> "none"
            }
        }
        val compatibilityLine = if (!hasRom) {
            "none (no ROM loaded)"
        } else {
            val statusName = snapshot.compatibilityStatus?.name ?: RomCompatibilityStatus.UNSUPPORTED.name
            "$statusName (exactRuntimeVerified=${snapshot.exactRuntimeVerified})"
        }
        val engineBaseLine = if (!hasRom) {
            "none"
        } else {
            val engine = sanitizeField(snapshot.engine, "Unknown")
            val baseGame = sanitizeField(snapshot.baseGame, "Unknown")
            "$engine / $baseGame"
        }

        val capabilityEntries = listOf(
            "live_memory" to snapshot.capabilities.liveMemoryRead,
            "battle_state" to snapshot.capabilities.battleStateRead,
            "interactive_battle_controls" to snapshot.capabilities.interactiveBattleControls,
            "battle_auto_open" to snapshot.capabilities.battleAutoOpen,
            "smart_fast_forward" to snapshot.capabilities.smartFastForward,
            "move_tile_damage" to snapshot.capabilities.moveTileDamage,
            "move_tile_effectiveness" to snapshot.capabilities.moveTileEffectiveness
        )
        val enabledCaps = capabilityEntries.filter { it.second }.map { it.first }
        val disabledCaps = capabilityEntries.filterNot { it.second }.map { it.first }

        return buildString {
            appendLine("### DualDex Diagnostics")
            appendLine("- **App:** $versionLine")
            appendLine("- **Android:** $androidLine")
            appendLine("- **Device:** $deviceLine")
            appendLine("- **Displays:** $displaysLine")
            appendLine("- **ROM SHA-256:** $shaLine")
            appendLine("- **Profile:** $profileLine")
            appendLine("- **Compatibility:** $compatibilityLine")
            appendLine("- **Engine / Base Game:** $engineBaseLine")
            appendLine("- **Capabilities:**")
            appendLine("  - Enabled: ${enabledCaps.ifEmpty { listOf("none") }.joinToString(", ")}")
            append("  - Disabled: ${disabledCaps.ifEmpty { listOf("none") }.joinToString(", ")}")
        }
    }

    private fun formatDisplays(displays: List<DisplayDescriptor>): String {
        if (displays.isEmpty()) return "0 (none)"
        val summary = displays
            .sortedBy { it.displayId }
            .joinToString(", ") { display ->
                val flags = buildList {
                    if (display.isDefaultDisplay) add("default")
                    if (display.isPresentationCategory) add("presentation")
                }
                val flagSuffix = if (flags.isEmpty()) "" else " [${flags.joinToString(",")}]"
                "id=${display.displayId} ${display.widthPx}x${display.heightPx}$flagSuffix"
            }
        return "${displays.size} ($summary)"
    }

    private fun normalizeSha256(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (SHA256_REGEX.matches(trimmed)) trimmed.lowercase() else null
    }

    private fun sanitizeField(raw: String?, fallback: String): String {
        val cleaned = raw
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.trim()
            .orEmpty()
        if (cleaned.isEmpty()) return fallback
        if (SENSITIVE_PATH_OR_URI_REGEX.containsMatchIn(cleaned)) {
            return "[redacted]"
        }
        return cleaned
    }
}
