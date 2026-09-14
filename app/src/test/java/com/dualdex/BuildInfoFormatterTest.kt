package com.dualdex

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildInfoFormatterTest {
    @Test
    fun versionLabel_includesNameAndSemanticVersion() {
        assertEquals("DualDex 0.9.0-beta.1", BuildInfoFormatter.versionLabel("0.9.0-beta.1"))
        assertEquals("DualDex 1.0.0", BuildInfoFormatter.versionLabel("1.0.0"))
    }

    @Test
    fun versionCodeLabel_rendersMonotonicInteger() {
        assertEquals("versionCode 1", BuildInfoFormatter.versionCodeLabel(1))
        assertEquals("versionCode 2", BuildInfoFormatter.versionCodeLabel(2))
    }

    @Test
    fun buildTypeLabel_rendersDebugOrRelease() {
        assertEquals("build type debug", BuildInfoFormatter.buildTypeLabel("debug"))
        assertEquals("build type release", BuildInfoFormatter.buildTypeLabel("release"))
    }

    @Test
    fun buildMetaLine_combinesVersionCodeAndBuildType() {
        assertEquals("versionCode 1 · build type release", BuildInfoFormatter.buildMetaLine(1, "release"))
    }
}
