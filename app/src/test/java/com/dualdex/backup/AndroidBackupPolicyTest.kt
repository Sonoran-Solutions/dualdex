package com.dualdex.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class AndroidBackupPolicyTest {
    private data class PathRule(val domain: String, val path: String) {
        fun matches(candidateDomain: String, candidatePath: String): Boolean {
            if (domain != candidateDomain) return false
            val relative = candidatePath.trimStart('/')
            return path == "." || path == "./" ||
                if (path.endsWith('/')) relative.startsWith(path) else relative == path
        }
    }

    private data class Rules(val includes: List<PathRule>, val excludes: List<PathRule>) {
        fun allows(domain: String, path: String): Boolean {
            val included = if (includes.isEmpty()) true else includes.any { it.matches(domain, path) }
            return included && excludes.none { it.matches(domain, path) }
        }
    }

    private val root by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "ci.sh").isFile) {
            dir = dir.parentFile ?: error("Could not locate repository root from ${System.getProperty("user.dir")}")
        }
        dir
    }

    @Test
    fun canonicalBatteryAndRomIdentityPathsAreIncluded() {
        val policy = legacyRules()
        assertTrue(policy.allows("file", "saves_v2/0123456789abcdef/battery.sav"))
        assertTrue(policy.allows("file", "saves_v2/0123456789abcdef/metadata.json"))
    }

    @Test
    fun canonicalSaveStatesAndRecoveryMetadataAreIncluded() {
        val policy = legacyRules()
        listOf(
            "slot_1.state",
            "quicksave.state",
            "auto_resume.state",
            "resume_barrier",
            "metadata.json",
            "slot_1.png",
            "battery.sav.bak"
        ).forEach { file ->
            assertTrue(file, policy.allows("file", "saves_v2/rom-sha/$file"))
        }
    }

    @Test
    fun legacyAndFallbackSavesRemainAvailableForMigrationAndRecovery() {
        val policy = legacyRules()
        assertTrue(policy.allows("file", "saves/Pokemon_Emerald.sav"))
        assertTrue(policy.allows("file", "saves/Pokemon_Emerald.sav.orig.bak"))
        assertTrue(policy.allows("file", "saves_fallback/legacy_Emerald/battery.sav"))
        assertTrue(policy.allows("file", "saves_fallback/old-key.migrated.bak/slot_1.state"))
        assertTrue(policy.allows("sharedpref", "dualdex_legacy_catalog.xml"))
    }

    @Test
    fun romCachesAndFrontEndIncomingRomCopiesAreExcluded() {
        val policy = legacyRules()
        assertFalse(policy.allows("file", "rom_cache/0123456789abcdef.gba"))
        assertFalse(policy.allows("file", "rom_cache/incoming_123.tmp"))
        assertFalse(policy.allows("file", "frontend_roms/Pokemon.gba"))
    }

    @Test
    fun dedicatedSaveStagingIsExcluded() {
        val policy = legacyRules()
        assertFalse(policy.allows("file", "save_staging/rom-sha__battery.sav"))
        assertFalse(policy.allows("file", "save_staging/rom-sha__import_candidate.tmp"))
    }

    @Test
    fun sensitiveAndConveniencePreferencesAreExcluded() {
        val policy = legacyRules()
        assertFalse(policy.allows("sharedpref", "dualdex_settings.xml")) // Retired Gemini key/model and local settings.
        assertFalse(policy.allows("sharedpref", "dualdex_library.xml")) // SteamGridDB API key and library state.
        assertFalse(policy.allows("sharedpref", "dualdex_cheats.xml"))
        assertFalse(policy.allows("sharedpref", "new_preferences.xml"))
    }

    @Test
    fun debugGeneratedAndTemporaryFilesAreExcluded() {
        val policy = legacyRules()
        listOf(
            "hns-coverage/log.json",
            "library_scan_cache.json",
            "covers/rom-sha",
            "future_cache/index.json"
        ).forEach { path -> assertFalse(path, policy.allows("file", path)) }
        assertFalse(policy.allows("file", "updates/dualdex.apk"))
    }

    @Test
    fun modernCloudAndDeviceTransferUseTheSameExplicitAllowlist() {
        val document = parse(File(root, "app/src/main/res/xml/data_extraction_rules.xml"))
        val cloud = rulesFor(document, "cloud-backup")
        val transfer = rulesFor(document, "device-transfer")
        assertEquals(expectedIncludes, cloud.includes.toSet())
        assertEquals(cloud, transfer)
        assertEquals("true", section(document, "cloud-backup").getAttribute("disableIfNoEncryptionCapabilities"))

        mandatoryExclusions().forEach { (domain, path) ->
            assertFalse("cloud:$domain:$path", cloud.allows(domain, path))
            assertFalse("device-transfer:$domain:$path", transfer.allows(domain, path))
        }
    }

    @Test
    fun legacyRulesApplyEquivalentProtection() {
        val rules = legacyRules()
        assertEquals(expectedIncludes, rules.includes.toSet())
        mandatoryExclusions().forEach { (domain, path) ->
            assertFalse("legacy:$domain:$path", rules.allows(domain, path))
        }
    }

    @Test
    fun manifestBackupReferencesResolveToRealRuleResources() {
        val document = parse(File(root, "app/src/main/AndroidManifest.xml"))
        val application = document.getElementsByTagName("application").item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("true", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(android, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
        assertTrue(File(root, "app/src/main/res/xml/backup_rules.xml").isFile)
        assertTrue(File(root, "app/src/main/res/xml/data_extraction_rules.xml").isFile)
    }

    @Test
    fun futureFilesAndDomainsAreNotImplicitlyApproved() {
        val policies = listOf(
            legacyRules(),
            rulesFor(parse(File(root, "app/src/main/res/xml/data_extraction_rules.xml")), "cloud-backup"),
            rulesFor(parse(File(root, "app/src/main/res/xml/data_extraction_rules.xml")), "device-transfer")
        )
        policies.forEach { policy ->
            assertFalse(policy.allows("file", "future_feature/user-data.json"))
            assertFalse(policy.allows("database", "future.db"))
            assertFalse(policy.allows("external", "ROMs/game.gba"))
            assertFalse(policy.allows("root", "new_file.bin"))
            assertTrue(isExactAllowlist(policy))
        }
    }

    @Test
    fun unsafeBroadAllowlistFailsThePolicyCheck() {
        val unsafe = parse(
            """
            <full-backup-content>
                <include domain="file" path="." />
            </full-backup-content>
            """.trimIndent()
        )
        val unsafeRules = rulesFor(unsafe, "full-backup-content")
        assertTrue(unsafeRules.allows("file", "rom_cache/game.gba"))
        assertFalse(isExactAllowlist(unsafeRules))
    }

    private fun legacyRules() = rulesFor(parse(File(root, "app/src/main/res/xml/backup_rules.xml")), "full-backup-content")

    private fun mandatoryExclusions() = listOf(
        "file" to "rom_cache/game.gba",
        "file" to "rom_cache/incoming_123.tmp",
        "file" to "frontend_roms/Pokemon.gba",
        "file" to "save_staging/rom__battery.sav",
        "file" to "hns-coverage/log.json",
        "file" to "library_scan_cache.json",
        "sharedpref" to "dualdex_settings.xml",
        "sharedpref" to "dualdex_library.xml",
        "sharedpref" to "dualdex_cheats.xml"
    )

    private val expectedIncludes = setOf(
        PathRule("file", "saves_v2/"),
        PathRule("file", "saves/"),
        PathRule("file", "saves_fallback/"),
        PathRule("sharedpref", "dualdex_legacy_catalog.xml")
    )

    private fun isExactAllowlist(rules: Rules): Boolean =
        rules.includes.toSet() == expectedIncludes &&
            rules.includes.none { '*' in it.path || '?' in it.path } &&
            rules.excludes.isEmpty()

    private fun rulesFor(document: Document, sectionName: String): Rules {
        val parent = section(document, sectionName)
        val includes = mutableListOf<PathRule>()
        val excludes = mutableListOf<PathRule>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node !is Element) continue
            val rule = PathRule(node.getAttribute("domain"), node.getAttribute("path"))
            when (node.tagName) {
                "include" -> includes += rule
                "exclude" -> excludes += rule
            }
        }
        return Rules(includes, excludes)
    }

    private fun section(document: Document, name: String): Element {
        val nodes = document.getElementsByTagName(name)
        require(nodes.length == 1) { "Expected one <$name> section, found ${nodes.length}" }
        return nodes.item(0) as Element
    }

    private fun parse(file: File): Document = parse(file.readText())

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }
}
