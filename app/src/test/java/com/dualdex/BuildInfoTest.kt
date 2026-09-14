package com.dualdex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the app identity/versioning and that the About/diagnostics UI values
 * are sourced from Android-generated BuildConfig rather than a duplicated
 * hand-maintained string.
 */
class BuildInfoTest {
    @Test
    fun versionName_isFirstPublicBeta() {
        assertEquals("0.9.0-beta.1", BuildInfo.versionName)
    }

    @Test
    fun versionCode_isPositiveMonotonicStartingPoint() {
        assertEquals(1, BuildInfo.versionCode)
        assertTrue(BuildInfo.versionCode >= 1)
    }

    @Test
    fun buildInfo_isSourcedFromGeneratedBuildConfig() {
        // BuildInfo must never duplicate the Gradle version configuration by hand.
        assertEquals(BuildConfig.VERSION_NAME, BuildInfo.versionName)
        assertEquals(BuildConfig.VERSION_CODE, BuildInfo.versionCode)
        assertEquals(BuildConfig.BUILD_TYPE, BuildInfo.buildType)
        assertEquals(BuildConfig.APPLICATION_ID, BuildInfo.applicationId)
        assertEquals(BuildConfig.DEBUG, BuildInfo.isDebuggable)
    }

    @Test
    fun applicationId_isPermanentPublicIdentity() {
        assertEquals("com.dualdex", BuildInfo.applicationId)
    }
}
