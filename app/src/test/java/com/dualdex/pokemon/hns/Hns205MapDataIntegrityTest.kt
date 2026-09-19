package com.dualdex.pokemon.hns

import com.dualdex.pokemon.MapNodeType
import com.dualdex.pokemon.RegionId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Integrity of the generated Heart & Soul 2.0.5 map data.
 *
 * Every test here is canonical: it runs with no ROM, no network, and no emulator.
 * When the pinned upstream checkout is present, the tests additionally re-derive
 * the expected mapping directly from upstream source files -- an oracle
 * independent of the generator -- so these tests cannot pass by comparing the
 * generated file against itself. Without the checkout, the pinned source-derived
 * expectations below still run.
 */
class Hns205MapDataIntegrityTest {

    // ------------------------------------------------------------------
    // Independently pinned expectations (recorded from the Release-v2.0.5
    // source, not read out of the generated file).
    // ------------------------------------------------------------------

    private data class Expected(
        val group: Int,
        val num: Int,
        val upstreamName: String,
        val sectionId: String,
        val region: RegionId?,
        val displayName: String,
    )

    private val pinnedExpectations = listOf(
        Expected(0, 0, "NewBarkTown_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO, "New Bark Town"),
        Expected(0, 1, "CherrygroveCity_hns", "MAPSEC_CHERRYGROVE_CITY", RegionId.JOHTO, "Cherrygrove City"),
        Expected(0, 2, "VioletCity_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO, "Violet City"),
        Expected(0, 3, "AzaleaTown_hns", "MAPSEC_AZALEA_TOWN", RegionId.JOHTO, "Azalea Town"),
        Expected(0, 4, "GoldenrodCity_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO, "Goldenrod City"),
        Expected(0, 5, "EcruteakCity_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO, "Ecruteak City"),
        Expected(0, 6, "OlivineCity_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO, "Olivine City"),
        Expected(0, 7, "CianwoodCity_hns", "MAPSEC_CIANWOOD_CITY", RegionId.JOHTO, "Cianwood City"),
        Expected(0, 8, "SafariZoneGate_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO, "Safari Zone Gate"),
        Expected(0, 9, "Mahoganytown_hns", "MAPSEC_MAHOGANY_TOWN", RegionId.JOHTO, "Mahogany Town"),
        Expected(0, 10, "BlackthornCity_hns", "MAPSEC_BLACKTHORN_CITY", RegionId.JOHTO, "Blackthorn City"),
        Expected(0, 11, "Route29_hns", "MAPSEC_ROUTE_29", RegionId.JOHTO, "Route 29"),
        Expected(0, 12, "Route30_hns", "MAPSEC_ROUTE_30", RegionId.JOHTO, "Route 30"),
        Expected(0, 30, "Route48_hns", "MAPSEC_ROUTE_48", RegionId.JOHTO, "Route 48"),
        // Kanto begins at mapNum 31 in the H&S towns-and-routes group.
        Expected(0, 31, "PalletTown_hns", "MAPSEC_PALLET_TOWN", RegionId.KANTO, "Pallet Town"),
        Expected(0, 32, "ViridianCity_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO, "Viridian City"),
        Expected(0, 33, "PewterCity_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO, "Pewter City"),
        Expected(0, 34, "CeruleanCity_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO, "Cerulean City"),
        Expected(0, 40, "CinnabarIsland_hns", "MAPSEC_CINNABAR_ISLAND", RegionId.KANTO, "Cinnabar Island"),
        Expected(0, 41, "Route1_hns", "MAPSEC_ROUTE_1", RegionId.KANTO, "Route 1"),
        Expected(0, 70, "Route28_hns", "MAPSEC_ROUTE_28", RegionId.JOHTO, "Route 28"),
        // Interiors resolve to their parent area, not to a synthetic identity.
        Expected(1, 0, "NewBarkTown_Lab_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO, "New Bark Town"),
        Expected(3, 5, "VioletCity_Gym_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO, "Violet City"),
        Expected(7, 9, "OlivineCity_Lighthouse_hns", "MAPSEC_OLIVINE_LIGHTHOUSE", RegionId.JOHTO, "Olivine Lighthouse"),
        Expected(7, 4, "OlivineCity_House1_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO, "Olivine City"),
        // Interiors that H&S moved into a different group than a naive reading
        // of the legacy table would suggest.
        Expected(19, 6, "FuchsiaCity_Route15_Gate_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO, "Fuchsia City"),
        Expected(22, 13, "SlateportCity_BattleTentLobby_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO, "Trainer Hill"),
        Expected(22, 34, "MtSilver_PokemonCenter_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO, "Mt. Silver"),
        // Dungeons.
        Expected(24, 0, "DarkCave_SouthSide_hns", "MAPSEC_DARK_CAVE", RegionId.JOHTO, "Dark Cave"),
        Expected(24, 2, "SproutTower_1F_hns", "MAPSEC_SPROUT_TOWER", RegionId.JOHTO, "Sprout Tower"),
        Expected(24, 8, "UnionCave_1F_hns", "MAPSEC_UNION_CAVE", RegionId.JOHTO, "Union Cave"),
        Expected(24, 13, "IlexForest_hns", "MAPSEC_ILEX_FOREST", RegionId.JOHTO, "Ilex Forest"),
        // Sinjoh and Alola are their own regions, not Johto.
        Expected(25, 0, "PoniIsle_hns", "MAPSEC_PONI_ISLAND", RegionId.ALOLA, "Poni Isle"),
        Expected(25, 2, "MelemeleIsle_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA, "Melemele Isle"),
        Expected(28, 3, "Route50_hns", "MAPSEC_ROUTE_50", RegionId.SINJOH, "Route 50"),
        Expected(28, 5, "SinjohRuins_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH, "Sinjoh Ruins"),
        Expected(29, 0, "NewSinjoh_PokemonCenter_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH, "New Sinjoh"),
        // Dynamic areas carry no region.
        Expected(27, 0, "UnionRoom_hns", "MAPSEC_DYNAMIC", null, "Dynamic"),
    )

    /**
     * Pinned canvas rectangles for the H&S Johto and Kanto region maps, as
     * (x, y, width, height) with (x, y) the bounding box's top-left tile.
     *
     * Multi-tile entries among these are the ones that catch an origin/extent
     * mix-up: a rectangle emitted at its centre is shifted by half its extent.
     */
    private val pinnedCanvas = mapOf(
        "MAPSEC_NEW_BARK_TOWN" to intArrayOf(19, 10, 1, 1),
        "MAPSEC_VIOLET_CITY" to intArrayOf(12, 4, 1, 1),
        "MAPSEC_GOLDENROD_CITY" to intArrayOf(8, 8, 1, 2),
        "MAPSEC_ROUTE_29" to intArrayOf(15, 10, 4, 1),
        "MAPSEC_ROUTE_30" to intArrayOf(14, 5, 1, 5),
        "MAPSEC_ROUTE_32" to intArrayOf(12, 5, 1, 6),
        "MAPSEC_ROUTE_2" to intArrayOf(4, 5, 1, 3),
        "MAPSEC_DARK_CAVE" to intArrayOf(15, 4, 3, 2),
        "MAPSEC_PALLET_TOWN" to intArrayOf(4, 11, 1, 1),
        "MAPSEC_VIRIDIAN_CITY" to intArrayOf(4, 8, 1, 1),
        "MAPSEC_PEWTER_CITY" to intArrayOf(4, 4, 1, 1),
        "MAPSEC_FUCHSIA_CITY" to intArrayOf(12, 12, 1, 1),
        "MAPSEC_CINNABAR_ISLAND" to intArrayOf(4, 14, 1, 1),
    )

    // ------------------------------------------------------------------
    // Canonical checks
    // ------------------------------------------------------------------

    @Test
    fun provenanceIsPinnedAndNotDeveloperSpecific() {
        assertEquals("1f42b74dff0e9fe942419845d040663dd829a973", Hns205MapData.UPSTREAM_COMMIT_SHA)
        assertEquals("Release-v2.0.5", Hns205MapData.UPSTREAM_TAG)
    }

    @Test
    fun locationKeysAreUniqueAndInRange() {
        val seen = mutableSetOf<Pair<Int, Int>>()
        var count = 0
        for ((group, members) in Hns205MapData.locationGroups) {
            assertTrue("mapGroup must not be negative", group >= 0)
            members.forEachIndexed { num, location ->
                if (location == null) return@forEachIndexed
                count++
                assertTrue("mapNum must not be negative", num >= 0)
                assertTrue("duplicate key ($group, $num)", seen.add(group to num))
            }
        }
        // Pinned H&S location count for Release-v2.0.5.
        assertEquals(560, count)
    }

    @Test
    fun everyLocationResolvesToADeclaredSection() {
        for ((group, members) in Hns205MapData.locationGroups) {
            members.forEachIndexed { num, location ->
                if (location == null) return@forEachIndexed
                assertNotNull(
                    "($group, $num) ${location.mapName} references an undeclared section",
                    Hns205MapData.findSection(location.sectionId)
                )
            }
        }
    }

    @Test
    fun pinnedExpectationsHold() {
        for (expected in pinnedExpectations) {
            val location = Hns205MapData.findLocation(expected.group, expected.num)
            assertNotNull(
                "(${expected.group}, ${expected.num}) should be a H&S location",
                location
            )
            assertEquals(expected.upstreamName, location!!.mapName)
            assertEquals(expected.sectionId, location.sectionId)

            val section = Hns205MapData.findSection(location.sectionId)
            assertNotNull("${location.sectionId} should be declared", section)
            assertEquals(expected.sectionId, section!!.sectionId)
            assertEquals(expected.displayName, section.displayName)
            assertEquals(expected.region, section.region)
        }
    }

    @Test
    fun pinnedCanvasPositionsHold() {
        for ((sectionId, expected) in pinnedCanvas) {
            val section = Hns205MapData.findSection(sectionId)
            assertNotNull("$sectionId should be declared", section)
            assertTrue("$sectionId should be presentable", section!!.presentable)
            assertEquals("$sectionId gridX", expected[0], section.gridX)
            assertEquals("$sectionId gridY", expected[1], section.gridY)
            assertEquals("$sectionId width", expected[2], section.width)
            assertEquals("$sectionId height", expected[3], section.height)
        }
    }

    @Test
    fun presentableSectionsHaveUsableCoordinates() {
        for (section in Hns205MapData.sections) {
            if (!section.presentable) continue
            assertTrue("${section.sectionId} gridX", section.gridX in 0 until 28)
            assertTrue("${section.sectionId} gridY", section.gridY in 0 until 15)
            assertTrue("${section.sectionId} width", section.width >= 1)
            assertTrue("${section.sectionId} height", section.height >= 1)
            assertTrue("${section.sectionId} x+width", section.gridX + section.width <= 28)
            assertTrue("${section.sectionId} y+height", section.gridY + section.height <= 15)
        }
    }

    @Test
    fun nonPresentableSectionsCarryNoCoordinates() {
        for (section in Hns205MapData.sections) {
            if (section.presentable) continue
            assertEquals("${section.sectionId} must not claim a canvas anchor", -1, section.gridX)
            assertEquals("${section.sectionId} must not claim a canvas anchor", -1, section.gridY)
        }
    }

    @Test
    fun nodeTypesAreDeclared() {
        val declared = MapNodeType.entries.toSet()
        for (section in Hns205MapData.sections) {
            assertTrue("${section.sectionId} node type", section.nodeType in declared)
        }
    }

    /**
     * Determinism and a missing-data guard.
     *
     * The generated object is a compile-time constant, so re-reading it twice must
     * be identical; and the generated source must carry its regeneration command
     * rather than depending on any developer-machine path.
     */
    @Test
    fun generatedSourceIsSelfDescribingAndPathFree() {
        val source = generatedSourceFile()
        assertNotNull(
            "the generated map data source must be present in the repository",
            source
        )
        val text = source!!.readText()
        assertTrue(
            "generated source must document its regeneration command",
            text.contains("tools/hns-map-data/generate_hns_map_data.py")
        )
        assertTrue(
            "generated source must record the pinned commit",
            text.contains(Hns205MapData.UPSTREAM_COMMIT_SHA)
        )
        assertFalse(
            "generated source must not embed a developer-machine path",
            text.contains("/home/") || text.contains("/Users/")
        )
        assertFalse(
            "generated source must not embed a timestamp",
            Regex("""\b20\d\d-\d\d-\d\dT\d\d:\d\d""").containsMatchIn(text)
        )
    }

    /**
     * The generated file records digests of its upstream inputs and of the
     * mapping itself, so the canonical gate can prove freshness without a network
     * or an upstream checkout.
     */
    @Test
    fun generatedSourceRecordsVerifiableDigests() {
        val source = generatedSourceFile()
        assertNotNull("the generated map data source must be present", source)
        val text = source!!.readText()

        for (constant in listOf("SOURCE_DIGEST", "MAPPING_DIGEST")) {
            val match = Regex(
                "const val " + constant + ": String = \"([0-9a-f]{64})\""
            ).find(text)
            assertNotNull("generated source must record $constant", match)
            assertFalse("$constant must be a real digest", match!!.groupValues[1].isBlank())
        }

        val sourceDigest = Regex("SOURCE_DIGEST: String = \"([0-9a-f]{64})\"")
            .find(text)!!.groupValues[1]
        val mappingDigest = Regex("MAPPING_DIGEST: String = \"([0-9a-f]{64})\"")
            .find(text)!!.groupValues[1]
        assertTrue(
            "the two digests must not be interchangeable",
            sourceDigest != mappingDigest
        )
    }

    @Test
    fun sectionsAreDeterministicallyOrdered() {
        val ids = Hns205MapData.sections.map { it.sectionId }
        assertEquals("sections must be emitted in a stable order", ids.sorted(), ids)
        assertEquals("section ids must be unique", ids.size, ids.toSet().size)
    }

    // ------------------------------------------------------------------
    // Independent upstream oracle
    // ------------------------------------------------------------------

    /**
     * The independent oracle.
     *
     * This test is deliberately NOT part of the default, self-contained suite: a
     * plain DualDex checkout has no Heart & Soul source tree, and the canonical gate
     * must not depend on an external repository. It runs under the explicit
     * source-validation task (`./ci.sh source-check`), where it **fails loudly** when
     * the pinned checkout is missing or wrong rather than silently skipping, because
     * an oracle that quietly skips is indistinguishable from one that verified
     * nothing.
     */
    @Test
    fun matchesPinnedUpstreamSource() {
        if (!upstreamChecksRequested()) return
        val upstream = requireUpstreamCheckout()

        val groups = JSONObject(File(upstream, "data/maps/map_groups.json").readText())
        val groupOrder = groups.getJSONArray("group_order")

        var compared = 0
        for (groupIndex in 0 until groupOrder.length()) {
            val groupName = groupOrder.getString(groupIndex)
            val members = groups.getJSONArray(groupName)
            for (mapNumber in 0 until members.length()) {
                val mapName = members.getString(mapNumber)
                val metadata = JSONObject(
                    File(upstream, "data/maps/$mapName/map.json").readText()
                )
                val generated = Hns205MapData.findLocation(groupIndex, mapNumber)

                if (metadata.optString("game_version") != "hns") {
                    assertNull(
                        "($groupIndex, $mapNumber) $mapName is not H&S and must not be a location",
                        generated
                    )
                    continue
                }

                compared++
                assertNotNull("($groupIndex, $mapNumber) $mapName should resolve", generated)
                assertEquals(mapName, generated!!.mapName)
                assertEquals(
                    metadata.getString("region_map_section"),
                    generated.sectionId
                )
            }
        }
        assertEquals(
            "every H&S map in the pinned table must be modelled",
            Hns205MapData.locationGroups.values.sumOf { row -> row.count { it != null } },
            compared
        )
    }

    @Test
    fun matchesPinnedUpstreamCanvas() {
        if (!upstreamChecksRequested()) return
        val upstream = requireUpstreamCheckout()

        val jkBoxes = parseLayoutGrid(
            File(upstream, "src/data/region_map/region_map_layout_jk.h")
        )
        assertTrue("the pinned layout grid must parse", jkBoxes.isNotEmpty())

        // Sections that carry at least one H&S map, derived directly from source.
        val hnsSections = hnsSectionIdsFromSource(upstream)
        assertTrue("H&S sections must be found upstream", hnsSections.isNotEmpty())

        // Every section the combined Johto/Kanto canvas draws that carries a H&S
        // map must be modelled. Sections drawn there for another game (the Emerald
        // Safari Zone, for instance) are correctly absent.
        for (sectionId in jkBoxes.keys) {
            if (sectionId !in hnsSections) continue
            assertNotNull(
                "$sectionId carries H&S maps and is drawn on the H&S canvas " +
                    "but is not modelled",
                Hns205MapData.findSection(sectionId)
            )
        }

        // And the converse: every modelled section really does carry H&S maps.
        for (section in Hns205MapData.sections) {
            assertTrue(
                "${section.sectionId} is modelled but upstream uses it for no H&S map",
                section.sectionId in hnsSections
            )
        }

        // Conversely, a section DualDex marks presentable for Johto or Kanto must
        // really be drawn by the view that region resolves against, at exactly the
        // rectangle that view draws.
        val johto = parseLayoutGrid(
            File(upstream, "src/data/region_map/region_map_layout_johto.h")
        )
        val kanto = parseLayoutGrid(
            File(upstream, "src/data/region_map/region_map_layout_kanto.h")
        )

        var compared = 0
        for (section in Hns205MapData.sections) {
            if (!section.presentable) continue
            val expected = when (section.region) {
                RegionId.JOHTO -> johto[section.sectionId]
                RegionId.KANTO -> kanto[section.sectionId]
                else -> null
            }
            assertNotNull(
                "${section.sectionId} claims a ${section.region} canvas position but " +
                    "the pinned ${section.region} layout does not draw it",
                expected
            )
            compared++
            // Full bounds, not just presence: this is what catches a rectangle
            // emitted at its centre instead of its top-left origin.
            assertEquals("${section.sectionId} gridX", expected!![0], section.gridX)
            assertEquals("${section.sectionId} gridY", expected[1], section.gridY)
            assertEquals("${section.sectionId} width", expected[2], section.width)
            assertEquals("${section.sectionId} height", expected[3], section.height)
        }
        assertEquals("every presentable section must be bounds-checked", 90, compared)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun generatedSourceFile(): File? {
        val candidates = listOf(
            File("src/main/java/com/dualdex/pokemon/hns/Hns205MapData.kt"),
            File("app/src/main/java/com/dualdex/pokemon/hns/Hns205MapData.kt"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    /**
     * Locate the pinned upstream checkout, or fail with instructions.
     *
     * The search is deliberately independent of any single developer machine:
     * `HNS_UPSTREAM_DIR` (the same variable the generator honours) wins, then the
     * conventional sibling layout.
     */
    /**
     * True only when the explicit source-validation run asked for upstream checks.
     *
     * The default canonical suite is self-contained: it always performs the offline
     * digest check and the pinned-fixture assertions, and never reaches for a
     * network or an external checkout.
     */
    private fun upstreamChecksRequested(): Boolean {
        val flag = System.getProperty(UPSTREAM_CHECK_PROPERTY)
            ?: System.getenv(UPSTREAM_CHECK_ENV)
        return flag != null && flag.equals("true", ignoreCase = true)
    }

    private fun requireUpstreamCheckout(): File {
        // Kotlin unit tests run with the Gradle module directory (`<repo>/app/.`)
        // as the working directory, while the CI workflow and the generator use a
        // repo-root-relative path. Resolve both so one HNS_UPSTREAM_DIR value works
        // for `ci.sh` and for Gradle.
        val workingDir = File(".").absoluteFile
        val repoRoot = findRepoRoot(workingDir)

        val candidates = buildList {
            System.getenv("HNS_UPSTREAM_DIR")?.takeIf { it.isNotBlank() }?.let { configured ->
                add(File(configured))
                if (!File(configured).isAbsolute && repoRoot != null) {
                    add(File(repoRoot, configured))
                }
            }
            if (repoRoot != null) {
                add(File(repoRoot, "upstream-hns/pokehns-expansion"))
                repoRoot.parentFile?.let {
                    add(File(it, "upstream-hns/pokehns-expansion"))
                }
            }
            add(File(workingDir, "upstream-hns/pokehns-expansion"))
        }
        val found = candidates.firstOrNull { File(it, "data/maps/map_groups.json").isFile }
        assertNotNull(
            "The pinned Heart & Soul upstream checkout is required to cross-check " +
                "the generated map data against source. Set HNS_UPSTREAM_DIR to a " +
                "checkout of $PINNED_UPSTREAM_COMMIT at ${PINNED_UPSTREAM_TAG}. " +
                "Searched: ${candidates.joinToString { it.path }}",
            found
        )
        return found!!
    }

    /**
     * Walk up from the test working directory to the Gradle project root.
     *
     * `settings.gradle.kts` is the marker, so this does not depend on how many
     * directory levels the test JVM's working directory happens to sit below the
     * root (`<repo>/app` for this module).
     */
    private fun findRepoRoot(start: File): File? {
        var current: File? = start
        var depth = 0
        while (current != null && depth < 8) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile
            depth++
        }
        return null
    }

    private companion object {
        const val PINNED_UPSTREAM_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
        const val PINNED_UPSTREAM_TAG = "Release-v2.0.5"

        /** Set by `./ci.sh source-check`; absent in the default self-contained gate. */
        const val UPSTREAM_CHECK_PROPERTY = "dualdex.hns.upstreamCheck"
        const val UPSTREAM_CHECK_ENV = "DUALDEX_HNS_UPSTREAM_CHECK"
    }

    /** Section ids that upstream uses for at least one `game_version: hns` map. */
    private fun hnsSectionIdsFromSource(upstream: File): Set<String> {
        val groups = JSONObject(File(upstream, "data/maps/map_groups.json").readText())
        val groupOrder = groups.getJSONArray("group_order")
        val sections = mutableSetOf<String>()
        for (groupIndex in 0 until groupOrder.length()) {
            val members = groups.getJSONArray(groupOrder.getString(groupIndex))
            for (mapNumber in 0 until members.length()) {
                val mapName = members.getString(mapNumber)
                val metadata = JSONObject(
                    File(upstream, "data/maps/$mapName/map.json").readText()
                )
                if (metadata.optString("game_version") == "hns") {
                    sections.add(metadata.getString("region_map_section"))
                }
            }
        }
        return sections
    }

    /**
     * Bounding rectangle of each section in an upstream `sRegionMapSections_*`
     * grid, as (minX, minY, width, height).
     */
    private fun parseLayoutGrid(file: File): Map<String, IntArray> {
        if (!file.isFile) return emptyMap()
        val text = file.readText()
        val start = text.indexOf("sRegionMapSections")
        if (start < 0) return emptyMap()
        val open = text.indexOf('{', start)
        val close = text.indexOf("};", open)
        if (open < 0 || close < 0) return emptyMap()

        val boxes = mutableMapOf<String, IntArray>()
        var y = 0
        for (line in text.substring(open + 1, close).lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) continue
            val inner = trimmed.substringAfter('{').substringBeforeLast('}')
            val cells = inner.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            cells.forEachIndexed { x, cell ->
                if (cell == "MAPSEC_NONE") return@forEachIndexed
                val box = boxes[cell]
                if (box == null) {
                    boxes[cell] = intArrayOf(x, y, x, y)
                } else {
                    box[0] = minOf(box[0], x)
                    box[1] = minOf(box[1], y)
                    box[2] = maxOf(box[2], x)
                    box[3] = maxOf(box[3], y)
                }
            }
            y++
        }
        return boxes.mapValues { (_, box) ->
            intArrayOf(box[0], box[1], box[2] - box[0] + 1, box[3] - box[1] + 1)
        }
    }
}
