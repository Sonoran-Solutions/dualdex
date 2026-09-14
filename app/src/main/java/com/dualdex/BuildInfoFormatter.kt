package com.dualdex

/**
 * Pure formatting for build/version metadata shown in About & Diagnostics.
 *
 * Kept free of Android APIs so the formatting rules can be unit-tested directly.
 * The actual values are provided by [BuildInfo], which reads Android-generated
 * BuildConfig metadata — never a hand-maintained string.
 */
object BuildInfoFormatter {
    const val APP_NAME = "DualDex"

    /** e.g. `DualDex 0.9.0-beta.1` */
    fun versionLabel(versionName: String): String = "$APP_NAME $versionName"

    /** e.g. `versionCode 1` */
    fun versionCodeLabel(versionCode: Int): String = "versionCode $versionCode"

    /** e.g. `build type debug` */
    fun buildTypeLabel(buildType: String): String = "build type $buildType"

    /** e.g. `versionCode 1 · build type debug` */
    fun buildMetaLine(versionCode: Int, buildType: String): String =
        "${versionCodeLabel(versionCode)} · ${buildTypeLabel(buildType)}"
}
