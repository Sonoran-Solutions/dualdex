package com.dualdex

/**
 * Build/version metadata surfaced in About & Diagnostics.
 *
 * Every value is sourced from Android-generated [BuildConfig], which is itself
 * generated from the Gradle configuration in `app/build.gradle.kts`. Do NOT
 * hard-code version strings in the UI; use these values so the UI cannot drift
 * from the Gradle versioning configuration.
 */
object BuildInfo {
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
    val buildType: String = BuildConfig.BUILD_TYPE
    val applicationId: String = BuildConfig.APPLICATION_ID
    val isDebuggable: Boolean = BuildConfig.DEBUG
}
