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
    fun versionName_isCurrentDevelopmentVersion() {
        // DualDex is still pre-beta. The planned first public beta is
        // 0.9.0-beta.1 and is set at the actual release cut, not on main.
        assertEquals("0.9.0-dev", BuildInfo.versionName)
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
    fun buildType_comesFromGeneratedMetadata() {
        // Must match Android-generated metadata, never a hard-coded UI string.
        // Asserting membership (rather than a variant-specific value) keeps this
        // correct under debug and release unit-test runs alike.
        assertEquals(BuildConfig.BUILD_TYPE, BuildInfo.buildType)
        assertTrue(BuildInfo.buildType in setOf("debug", "release"))
    }

    @Test
    fun applicationId_isPermanentPublicIdentity() {
        assertEquals("com.dualdex", BuildInfo.applicationId)
    }
}
