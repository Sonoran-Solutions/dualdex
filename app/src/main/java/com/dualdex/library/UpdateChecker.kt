package com.dualdex.library

import org.json.JSONArray

/** A published GitHub release that carries an installable APK. */
data class AvailableUpdate(
    val tag: String,
    val name: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long
)

/** Pure release selection for the GitHub Releases updater (#153). */
object UpdateChecker {
    const val REPO = "Sonoran-Solutions/dualdex"
    const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases?per_page=20"

    /**
     * Compare two semver-ish versions, ignoring a leading `v`. Numeric core compared field by field;
     * a version with a pre-release suffix (`-beta.1`, `-dev`) sorts before the same core without one.
     */
    fun compareVersions(a: String, b: String): Int {
        val (coreA, preA) = split(a)
        val (coreB, preB) = split(b)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val c = coreA.getOrElse(i) { 0 }.compareTo(coreB.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return when {
            preA == null && preB == null -> 0
            preA == null -> 1
            preB == null -> -1
            else -> preA.compareTo(preB)
        }
    }

    private fun split(version: String): Pair<List<Int>, String?> {
        val v = version.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val core = v.substringBefore('-')
        val pre = if ('-' in v) v.substringAfter('-') else null
        return core.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 } to pre
    }

    /**
     * Newest non-draft, non-prerelease release with an `.apk` asset that is strictly newer than
     * [currentVersion]; null when up to date.
     */
    fun selectUpdate(releasesJson: String, currentVersion: String): AvailableUpdate? {
        val releases = JSONArray(releasesJson)
        var best: AvailableUpdate? = null
        for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val tag = r.optString("tag_name")
            if (tag.isBlank()) continue
            val assets = r.optJSONArray("assets") ?: continue
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: continue
            if (compareVersions(tag, currentVersion) <= 0) continue
            if (best != null && compareVersions(tag, best.tag) <= 0) continue
            best = AvailableUpdate(
                tag = tag,
                name = r.optString("name").ifBlank { tag },
                notes = r.optString("body"),
                apkName = apk.getString("name"),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size", -1L)
            )
        }
        return best
    }
}
