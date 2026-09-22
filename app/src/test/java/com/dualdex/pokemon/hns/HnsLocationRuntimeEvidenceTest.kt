package com.dualdex.pokemon.hns

import com.dualdex.companion.ui.MapHeaderState
import com.dualdex.companion.ui.MapScreenPresenter
import com.dualdex.companion.ui.MapScreenState
import com.dualdex.companion.ui.MapSelection
import com.dualdex.pokemon.LocationResolution
import com.dualdex.pokemon.LocationStrategy
import com.dualdex.pokemon.LocationUnavailableReason
import com.dualdex.pokemon.PlayerLocation
import com.dualdex.pokemon.RegionId
import com.dualdex.pokemon.RegionMapDatabase
import com.dualdex.pokemon.RegionMapSection
import com.dualdex.pokemon.LocationResolver
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue #11: the production location and Map-screen path driven by REAL runtime observations.
 *
 * This closes the loop that issue #11 tracked as its remaining gap. The producer half is
 * `tools/hns-runtime-probe`, a developer-only tool that boots the exact official Heart & Soul 2.0.5
 * ROM on a legal save, reads the live location through the shipped native reader
 * (`pokemon_read_player_location_gba`) and asserts the raw `(mapGroup, mapNum)` pair the pinned
 * 2.0.5 table keys on. It records what it saw in
 * `tools/hns-runtime-probe/evidence/location-runtime-evidence.json`.
 *
 * The consumer half is this suite: it reads those raw pairs and pushes them through the SAME
 * production objects the app uses -- [LocationResolver], [RegionMapDatabase] and
 * [MapScreenPresenter] -- so the region, name, canvas and marker decisions made for real observed
 * locations are asserted rather than described.
 *
 * What is RUNTIME VERIFIED by this pair of artefacts: the raw identity of six legal checkpoints on
 * the official ROM, and the production interpretation of exactly those identities. What is NOT
 * runtime verified is any cross-region (Kanto, Sinjoh, Alola) transition, because the pinned build
 * gates every one of them behind the whole Johto story: see the `cross_region_transitions` block in
 * the evidence file, which is source-derived by
 * `tools/hns-map-data/hns_route.py edges --gates`.
 */
class HnsLocationRuntimeEvidenceTest {

    // ------------------------------------------------------------------ fixtures

    private fun repoFile(relative: String): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
            ?: throw AssertionError("Unable to locate $relative from ${System.getProperty("user.dir")}")

    private fun evidence(): JSONObject =
        JSONObject(
            repoFile("tools/hns-runtime-probe/evidence/location-runtime-evidence.json").readText()
        )

    private fun checkpoints(): List<JSONObject> =
        evidence().getJSONArray("checkpoints").let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }
        }

    private fun unknownControls(): List<JSONObject> =
        evidence().getJSONArray("unknown_map_controls").let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }
        }

    private fun crossRegionBlock(): JSONObject = evidence().getJSONObject("cross_region_transitions")

    private fun toolDerived(): JSONObject = crossRegionBlock().getJSONObject("tool_derived")

    /**
     * The standalone, verbatim analyzer output.
     *
     * This is the artefact `hns_route.py inventory --check` verifies in `./ci.sh source-check`, so
     * reading it here means the Kotlin suite and the source gate agree on the same tool-produced
     * document rather than on a copy of it.
     */
    private fun toolInventory(): JSONObject =
        JSONObject(
            repoFile("tools/hns-runtime-probe/evidence/hns-cross-region-inventory.json").readText()
        )

    private fun objects(array: org.json.JSONArray): List<JSONObject> =
        (0 until array.length()).map { array.getJSONObject(it) }

    /** Edges the analyzer could prove FUNCTIONAL from metatile behaviours and connection windows. */
    private fun toolFunctionalEdges(): List<JSONObject> =
        objects(toolInventory().getJSONArray("functional_edges"))

    /** Edges the analyzer could NOT decide from map data, reported with what it observed. */
    private fun toolUndecidedEdges(): List<JSONObject> =
        objects(toolInventory().getJSONArray("undecided_from_map_data"))

    private fun toolScriptCandidates(): List<JSONObject> =
        objects(toolInventory().getJSONArray("script_warp_candidates"))

    private fun manualVerdicts(): List<JSONObject> =
        objects(crossRegionBlock().getJSONObject("manual_engine_source_verified").getJSONArray("edges"))

    private fun manualScriptTransitions(): List<JSONObject> =
        objects(
            crossRegionBlock().getJSONObject("manual_engine_source_verified")
                .getJSONArray("script_command_transitions")
        )

    private fun edgeKey(edge: JSONObject): String =
        "${edge.getString("from_map")} -> ${edge.getString("to_map")}"

    private val exactRomSha256 = "edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"

    private fun hnsProfile(): RomHackProfile =
        ProfileLoader.parseProfile(repoFile("app/src/main/assets/profiles/heart_and_soul.json").readText())

    /** The trust the companion publishes for the exact verified dump, built through production. */
    private fun exactTrust(): RuntimeRomTrust =
        RuntimeRomTrust.from(
            compatibility = RomCompatibility.verified(hnsProfile(), exactRomSha256),
            activeRomSha256 = exactRomSha256
        )

    /** Rebuild the raw [PlayerLocation] the probe observed, field for field. */
    private fun rawLocation(checkpoint: JSONObject): PlayerLocation {
        val raw = checkpoint.getJSONObject("raw")
        return PlayerLocation(
            mapGroup = raw.getInt("map_group"),
            mapNum = raw.getInt("map_num"),
            warpId = raw.getInt("warp_id"),
            x = raw.getInt("x"),
            y = raw.getInt("y"),
            localX = raw.getInt("local_x"),
            localY = raw.getInt("local_y"),
            escapeMapGroup = 0,
            escapeMapNum = 0,
            isIndoors = raw.getBoolean("indoors"),
            isValid = true,
        )
    }

    private fun expected(checkpoint: JSONObject): JSONObject = checkpoint.getJSONObject("expected")

    private fun curatedName(section: RegionMapSection): String = section.name

    // ------------------------------------------------------------------ provenance

    @Test
    fun evidenceIsBoundToTheExactSupportedRomAndPinnedSource() {
        val provenance = evidence().getJSONObject("provenance")

        assertEquals(exactRomSha256, provenance.getString("rom_sha256"))
        assertEquals(
            "the runtime evidence must come from the same pinned upstream commit as the map table",
            Hns205MapData.UPSTREAM_COMMIT_SHA,
            provenance.getString("commit")
        )
        assertEquals(Hns205MapData.UPSTREAM_TAG, provenance.getString("tag"))
        assertEquals("pokemon_read_player_location_gba", provenance.getString("reader"))

        // The ROM the evidence names must be the one the bundled H&S profile trusts, otherwise a
        // capture from a different build could be described as evidence about the supported one.
        assertTrue(
            "the bundled heart_and_soul profile must trust the evidence ROM hash",
            hnsProfile().sha256Hashes.contains(exactRomSha256)
        )

        // No copyrighted artefact may ride along with the evidence.
        val evidenceDir = repoFile("tools/hns-runtime-probe/evidence/location-runtime-evidence.json")
            .parentFile!!
        val forbidden = listOf(".gba", ".sav", ".ss0", ".ss1", ".state", ".png", ".jpg", ".ppm")
        val offenders = evidenceDir.listFiles()
            .orEmpty()
            .filter { file -> forbidden.any { file.name.lowercase().endsWith(it) } }
            .map { it.name }
        assertTrue("no ROM/save/state/image belongs in the evidence directory, found $offenders",
            offenders.isEmpty())
    }

    // -------------------------------------------------- production interpretation

    /**
     * Every observed checkpoint must resolve, through the production resolver, to the section,
     * region, name and presentability the pinned 2.0.5 table declares.
     *
     * This is the assertion that would fail if the H&S strategy fell through a default, if a group
     * were reordered, or if an observed pair were silently mapped to some other region.
     */
    @Test
    fun everyObservedCheckpointResolvesThroughTheProductionResolver() {
        val observed = checkpoints()
        assertTrue("the evidence must carry runtime checkpoints", observed.size >= 6)

        for (checkpoint in observed) {
            val label = checkpoint.getString("label")
            val location = rawLocation(checkpoint)
            val want = expected(checkpoint)

            val resolution = LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, location)
            assertTrue(
                "$label (${location.mapGroup}/${location.mapNum}) must resolve, was ${resolution.reason}",
                resolution.isResolved
            )
            val section = resolution.section!!

            assertEquals("$label section id", want.getString("section_id"), section.id)
            assertEquals("$label region", RegionId.valueOf(want.getString("region")), section.region)
            assertEquals("$label presentable", want.getBoolean("presentable"), section.presentable)

            // The display name the app shows comes from curated metadata when present, and the
            // curated name must be the pinned source's own name, not an invented one.
            assertEquals(
                "$label display name",
                want.getString("display_name"),
                curatedName(section)
            )
        }
    }

    /** A resolved live checkpoint must open the Map tab on its own region with a real marker. */
    @Test
    fun everyObservedCheckpointPresentsOnItsOwnRegionCanvasWithAMarker() {
        for (checkpoint in checkpoints()) {
            val label = checkpoint.getString("label")
            val location = rawLocation(checkpoint)
            val resolution = resolveHns(location)
            val section = resolution.section!!

            val header = MapScreenPresenter.headerState(
                location = location,
                section = section,
                trust = exactTrust(),
                reason = resolution.reason,
            )
            assertTrue("$label must present a live header, was $header",
                header is MapHeaderState.Live)

            val canvas = MapScreenPresenter.canvasSelection(
                strategy = LocationStrategy.HEART_AND_SOUL_205,
                browseOverride = null,
                liveSection = section,
                current = com.dualdex.companion.ui.MapCanvasSelection(
                    region = RegionId.JOHTO, followsLiveRegion = true
                ),
            )
            assertEquals("$label canvas region", section.region, canvas.region)
            assertTrue("$label canvas must follow the live region", canvas.followsLiveRegion)

            val marker = MapScreenPresenter.markerSection(
                resolved = section,
                playerLocation = location,
                strategy = LocationStrategy.HEART_AND_SOUL_205,
                canvasRegion = canvas.region!!,
            )
            assertNotNull("$label must receive a marker on its own canvas", marker)
            assertEquals("$label marker identity", section.id, marker!!.id)
            assertTrue("$label marker anchor must be real", marker.gridX >= 0 && marker.gridY >= 0)
        }
    }

    // -------------------------------------------------------- unknown / unmapped

    /**
     * The fail-closed contract, checked on the raw shapes the probe can actually produce.
     *
     * The regression this guards is the legacy `else -> Johto` default: an unknown H&S pair used to
     * become New Bark Town. A resolver that regressed would resolve one of these and this test
     * would see a confident location where the product must show none.
     */
    @Test
    fun unknownMapIdsNeverFabricateAJohtoLocation() {
        val newBarkTown = RegionMapDatabase.getSectionById("MAPSEC_NEW_BARK_TOWN")
        assertNotNull(newBarkTown)

        for (control in unknownControls()) {
            val name = control.getString("name")
            val raw = control.getJSONObject("raw")
            val location = PlayerLocation(
                mapGroup = raw.getInt("map_group"),
                mapNum = raw.getInt("map_num"),
                warpId = 0, x = 0, y = 0, localX = 0, localY = 0,
                escapeMapGroup = 0, escapeMapNum = 0, isIndoors = false, isValid = true,
            )

            val resolution = LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, location)
            assertFalse(
                "$name (${location.mapGroup}/${location.mapNum}) must not resolve to anything",
                resolution.isResolved
            )
            assertNull("$name must carry no section", resolution.section)
            assertEquals(
                "$name must report an unknown map id",
                LocationUnavailableReason.UNKNOWN_MAP_ID,
                resolution.reason
            )

            // And the screen must not imply a location for it.
            val header = MapScreenPresenter.headerState(
                location = location,
                section = resolution.section,
                trust = exactTrust(),
                reason = resolution.reason,
            )
            assertTrue("$name must not present a live header, was $header",
                header is MapHeaderState.Unavailable)
            assertNull(
                "$name must not receive a marker",
                MapScreenPresenter.markerSection(
                    resolved = resolution.section,
                    playerLocation = location,
                    strategy = LocationStrategy.HEART_AND_SOUL_205,
                    canvasRegion = RegionId.JOHTO,
                )
            )
        }
    }

    // ------------------------------------------------------------ cross-region

    /**
     * The cross-region inventory must describe one consistent, reachable-by-nothing-early shape.
     *
     * #11 wants Sinjoh and Alola to be either intentionally presented or explicitly identified as a
     * known unsupported sub-area. This asserts the evidence records the *explicit* outcome: no
     * cross-region transition is runtime verified, every one is gated, and Alola has no transition
     * to the rest of the world at all.
     */
    @Test
    fun crossRegionEvidenceIsSplitByWhoDerivedIt() {
        val block = crossRegionBlock()
        val tool = toolDerived()
        val manual = block.getJSONObject("manual_engine_source_verified")

        // The tool-derived half must name its producer and say what it can and cannot prove, so a
        // reader can tell which claims are reproducible and which are an audit.
        assertTrue(tool.getString("producer").contains("hns_route.py"))
        assertTrue(tool.getString("check_command").contains("inventory --check"))
        assertTrue("the tool must state its own limits", tool.getJSONArray("what_it_cannot_prove").length() > 0)
        assertTrue("the tool must state what it does prove", tool.getJSONArray("what_it_can_prove").length() > 0)
        assertTrue(
            "the manual half must state that it is not tool-produced",
            manual.getString("note").contains("NOT produced by hns_route.py")
        )

        // The quoted checker must be the one the repository actually runs, or the claim of
        // reproducibility would point at a command nobody executes.
        val ci = repoFile("ci.sh").readText()
        assertTrue(
            "ci.sh must run the inventory check the evidence names",
            ci.contains("hns_route.py") && ci.contains("inventory") && ci.contains("--check")
        )

        // Every edge the tool could not decide must have a manual verdict, and a manual verdict may
        // not contradict what the tool did prove. This is the partition that stops "no bounded route
        // reaches Kanto" from resting on an unchecked table.
        val undecided = toolUndecidedEdges()
        assertTrue("the inventory must contain undecided edges", undecided.isNotEmpty())
        val verdicts = manualVerdicts().associateBy { edgeKey(it) }
        for (edge in undecided) {
            val key = edgeKey(edge)
            val verdict = verdicts[key]
            assertNotNull("$key was undecided from map data and has no manual verdict", verdict)
            assertTrue(
                "$key has an invalid manual verdict ${verdict!!.getString("verdict")}",
                verdict.getString("verdict") in setOf("functional", "dead")
            )
            assertTrue(
                "$key must cite the source that supports its verdict",
                verdict.getJSONArray("evidence").length() > 0
            )
        }
        for (edge in toolFunctionalEdges()) {
            val verdict = verdicts[edgeKey(edge)]
            assertFalse(
                "${edgeKey(edge)} is functional from its own metatile behaviour, so a manual " +
                    "'dead' verdict contradicts the tool",
                verdict?.optString("verdict") == "dead"
            )
        }
    }

    @Test
    fun theInventoryCannotDriftFromThePinnedSource() {
        val inventory = toolInventory()

        // The artefact the source gate verifies must be bound to the same pinned revision as the
        // map table, and must be produced by the tool rather than hand-shaped.
        assertEquals(Hns205MapData.UPSTREAM_COMMIT_SHA, inventory.getString("upstream_commit"))
        assertEquals(Hns205MapData.UPSTREAM_TAG, inventory.getString("upstream_tag"))
        assertTrue(
            "the inventory must name the analyzer as its producer",
            inventory.getString("producer").contains("hns_route.py inventory")
        )
        assertEquals(
            "the evidence record must embed the same functional edges as the verified inventory",
            toolFunctionalEdges().toString(),
            objects(toolDerived().getJSONArray("functional_edges")).toString()
        )
        assertEquals(
            "the evidence record must embed the same undecided edges as the verified inventory",
            toolUndecidedEdges().toString(),
            objects(toolDerived().getJSONArray("undecided_from_map_data")).toString()
        )
        assertEquals(
            "the evidence record must embed the same script-warp candidates as the verified inventory",
            toolScriptCandidates().toString(),
            objects(toolDerived().getJSONArray("script_warp_candidates")).toString()
        )

        // The provenance boundary itself must be pinned: the analyzer refuses anything but this
        // revision with clean inputs, and the canonical gate runs its regression.
        val analyzer = repoFile("tools/hns-map-data/hns_route.py").readText()
        assertTrue(analyzer.contains(Hns205MapData.UPSTREAM_COMMIT_SHA))
        assertTrue(
            "the analyzer must verify provenance",
            analyzer.contains("verify_provenance")
        )
        assertTrue(
            "the provenance regression must exist and be run by the canonical gate",
            repoFile("tools/hns-map-data/test_hns_route.py").isFile &&
                repoFile("ci.sh").readText().contains("test_hns_route")
        )
    }

    @Test
    fun crossRegionInventoryIsExplicitAboutWhatIsNotRuntimeVerified() {
        val block = crossRegionBlock()
        val manual = block.getJSONObject("manual_engine_source_verified")

        // The one Johto -> Kanto crossing into the overworld must be the ReceptionGate warp, and the
        // evidence must name the exact badges/Tin Tower gate that keeps it out of a bounded slice.
        val kanto = manualVerdicts().single {
            it.getString("from_map") == "ReceptionGate_hns" &&
                it.getString("to_map") == "Route22_hns"
        }
        assertEquals("functional", kanto.getString("verdict"))

        val kantoEdges = toolUndecidedEdges().single {
            it.getString("from_map") == "ReceptionGate_hns" &&
                it.getString("to_map") == "Route22_hns"
        }
        assertTrue(
            "the analyzer must record the trigger metatile it actually observed",
            kantoEdges.getString("trigger_behaviour").isNotBlank() &&
                kantoEdges.getBoolean("trigger_tile_walkable")
        )
        // The gate itself is a manual verdict, because the tool only reports what it observed. The
        // verdict must therefore name the exact badge and story variable, and cite the script lines.
        val gateEvidence = kanto.getJSONArray("evidence").joinToString("\n") { it.toString() }
        assertTrue(
            "the Kanto gate must name the eighth badge",
            gateEvidence.contains("FLAG_BADGE08_GET")
        )
        assertTrue(
            "the Kanto gate must name the Tin Tower story variable",
            gateEvidence.contains("VAR_ECRUTEAK_CITY_THEATER")
        )
        assertTrue(
            "the Kanto gate must cite the trigger script and the warp_def",
            gateEvidence.contains("ReceptionGate_hns/scripts.inc") &&
                gateEvidence.contains("ReceptionGate_hns/events.inc")
        )
        assertTrue(
            "the cut-vertex argument must cite its evidence",
            gateEvidence.contains("(11,14)")
        )

        // Alola is unreachable from Johto by every means the two halves examined.
        val alola = objects(block.getJSONArray("regions_without_any_transition"))
            .single { it.getString("region") == "ALOLA" }
        assertTrue(
            "Alola's absence of a transition must be recorded as source evidence",
            alola.getString("evidence").contains("ZERO edges")
        )
        assertTrue(
            "the manual half must record zero Johto <-> Alola edges",
            manual.getJSONObject("alola_has_no_transition_to_johto").getString("verdict")
                .contains("ZERO")
        )

        // A cross-region audit that ignored the build's dead borders would overstate how many ways
        // into Kanto exist, so the dead ones must be recorded with a reason.
        val dead = toolUndecidedEdges().filter { it.getString("reason_class").startsWith("dead ") }
        assertTrue("the build declares dead cross-region borders", dead.isNotEmpty())
        for (edge in dead) {
            assertTrue(
                "${edgeKey(edge)} must state why it cannot fire",
                edge.getString("reason").isNotBlank()
            )
        }
        assertTrue(
            "the Route26North/Route22 connection is declared but cannot fire",
            dead.any { edgeKey(it) == "Route26North_hns -> Route22_hns" }
        )

        // Script-command transitions are recorded separately, with their file and line, because the
        // analyzer can only report them as candidates.
        val scriptTransitions = manualScriptTransitions()
        assertTrue("script-command transitions must be recorded", scriptTransitions.isNotEmpty())
        for (entry in scriptTransitions) {
            assertTrue(
                "${entry.getString("from_map")} script transition must cite a file and line",
                entry.getString("file").endsWith(".inc") && entry.getInt("line") > 0
            )
            assertEquals("functional", entry.getString("verdict"))
        }
        for (candidate in toolScriptCandidates()) {
            assertTrue(
                "every script-warp candidate must carry its file and line",
                candidate.getString("file").endsWith(".inc") && candidate.getInt("line") > 0
            )
        }
        assertTrue(
            "the shared-include false positive must be recorded as rejected",
            manual.getJSONArray("rejected_candidates").length() > 0 &&
                manual.getJSONArray("rejected_candidates").toString().contains("shared script file")
        )
    }

    /**
     * Sinjoh and Alola resolve with their own region and never borrow a Johto or Kanto anchor.
     *
     * This is the "no fabricated marker" requirement checked through the production marker gate:
     * a region the app has no canvas for must produce no marker and no invented coordinates, even
     * though its identity and name are perfectly well known.
     */
    @Test
    fun sinjohAndAlolaAreRegionKnownWithoutAFabricatedMarker() {
        // (mapGroup, mapNum) pairs that the pinned table maps into Sinjoh and Alola.
        val probes = listOf(
            Triple(28, 5, RegionId.SINJOH),   // SinjohRuins_hns
            Triple(28, 0, RegionId.SINJOH),   // SnowsweptCavern_hns
            Triple(29, 1, RegionId.SINJOH),   // SinjohRuins_Temple_hns
            Triple(25, 0, RegionId.ALOLA),    // PoniIsle_hns
            Triple(25, 2, RegionId.ALOLA),    // MelemeleIsle_hns
            Triple(26, 0, RegionId.ALOLA),    // Melemele_PlayerHouse_hns
        )

        for ((group, num, regionId) in probes) {
            val location = PlayerLocation(
                mapGroup = group, mapNum = num, warpId = 0, x = 5, y = 5,
                localX = 5, localY = 5, escapeMapGroup = 0, escapeMapNum = 0,
                isIndoors = false, isValid = true,
            )
            val resolution = LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, location)
            assertTrue("($group, $num) must resolve", resolution.isResolved)
            val section = resolution.section!!

            // Identity and region are trustworthy...
            assertEquals("($group, $num) region", regionId, section.region)
            assertTrue("($group, $num) must have a non-blank name", section.name.isNotBlank())
            // ...but nothing may be drawn for it.
            assertFalse("($group, $num) must not be presentable", section.presentable)
            assertEquals("($group, $num) must carry no canvas x", -1, section.gridX)
            assertEquals("($group, $num) must carry no canvas y", -1, section.gridY)

            // The region has no canvas at all, so the live canvas must fall back to one the
            // strategy can actually draw rather than pretending to place this section on it.
            assertFalse("$regionId has no DualDex canvas", regionId.hasCanvas)
            val canvas = MapScreenPresenter.canvasSelection(
                strategy = LocationStrategy.HEART_AND_SOUL_205,
                browseOverride = null,
                liveSection = section,
                current = com.dualdex.companion.ui.MapCanvasSelection(
                    region = RegionId.JOHTO, followsLiveRegion = true
                ),
            )
            assertTrue("the fallback canvas must be drawable",
                MapScreenPresenter.canvasRegions(LocationStrategy.HEART_AND_SOUL_205)
                    .contains(canvas.region))

            // And no marker may be produced on ANY canvas, Johto and Kanto included.
            for (canvasRegion in RegionId.entries) {
                assertNull(
                    "$section.id must not receive a marker on $canvasRegion",
                    MapScreenPresenter.markerSection(
                        resolved = section,
                        playerLocation = location,
                        strategy = LocationStrategy.HEART_AND_SOUL_205,
                        canvasRegion = canvasRegion,
                    )
                )
            }
        }
    }

    /**
     * The Map tab must not borrow Johto coordinates for a Sinjoh/Alola player.
     *
     * A stronger form of the above: the section that reaches the canvas must be reported as
     * non-presentable, and reconciling it against a Johto canvas must drop it entirely rather than
     * drawing it at Johto's origin.
     */
    @Test
    fun sinjohAndAlolaBorrowNoJohtoOrKantoCoordinates() {
        val johtoIds = RegionMapDatabase
            .getSectionsForStrategy(LocationStrategy.HEART_AND_SOUL_205, RegionId.JOHTO)
            .associateBy { it.id }
        val kantoIds = RegionMapDatabase
            .getSectionsForStrategy(LocationStrategy.HEART_AND_SOUL_205, RegionId.KANTO)
            .associateBy { it.id }

        for ((group, num) in listOf(28 to 5, 25 to 0, 26 to 0)) {
            val location = PlayerLocation(
                mapGroup = group, mapNum = num, warpId = 0, x = 3, y = 4,
                localX = 3, localY = 4, escapeMapGroup = 0, escapeMapNum = 0,
                isIndoors = false, isValid = true,
            )
            val section = LocationResolver
                .resolve(LocationStrategy.HEART_AND_SOUL_205, location).section!!

            assertNull("($group, $num) must not appear on the Johto canvas",
                johtoIds[section.id])
            assertNull("($group, $num) must not appear on the Kanto canvas",
                kantoIds[section.id])

            for (canvasRegion in listOf(RegionId.JOHTO, RegionId.KANTO)) {
                assertNull(
                    "($group, $num) must not be drawable on $canvasRegion",
                    MapScreenPresenter.drawableHighlight(
                        selection = MapSelection.Live(section),
                        strategy = LocationStrategy.HEART_AND_SOUL_205,
                        canvasRegion = canvasRegion,
                    )
                )
                // And reconciling it against that canvas must clear it, not relocate it.
                assertEquals(
                    "($group, $num) must be reconciled away on $canvasRegion",
                    MapSelection.None,
                    MapScreenPresenter.reconcileSelection(
                        selection = MapSelection.Live(section),
                        strategy = LocationStrategy.HEART_AND_SOUL_205,
                        canvasRegion = canvasRegion,
                    )
                )
            }
        }
    }

    // ------------------------------------------------- browsing cannot retarget

    /**
     * Phase C of #11: browsing another visible region must not change how the running ROM's memory
     * is interpreted. See [MapScreenBrowsingIsolationTest] for the full sequence and the mutation
     * controls; this case pins the runtime-evidence side of it, on a real observed location.
     */
    @Test
    fun browsingAnotherCanvasLeavesTheObservedCheckpointResolvingAsHeartAndSoul() {
        val checkpoint = checkpoints().first { it.getString("label") == "cp2-route29" }
        val location = rawLocation(checkpoint)
        val liveSection = LocationResolver
            .resolve(LocationStrategy.HEART_AND_SOUL_205, location).section!!

        var events = 0
        val state = MapScreenState(LocationStrategy.HEART_AND_SOUL_205) { events++ }
        state.render(liveSection, hasLiveLocation = true)
        assertEquals(RegionId.JOHTO, state.canvas.region)

        // The user browses the Kanto canvas, the region a Johto player is NOT in.
        state.onRegionSelected(RegionId.KANTO, liveSection, hasLiveLocation = true)
        assertEquals(RegionId.KANTO, state.canvas.region)
        assertFalse("an explicit browse must not follow live", state.canvas.followsLiveRegion)

        // The native interpretation is untouched: the same raw pair still resolves to the same
        // H&S section and region, because browsing never reaches the strategy.
        assertEquals(
            LocationStrategy.HEART_AND_SOUL_205,
            LocationStrategy.forProfile(hnsProfile())
        )
        val again = LocationResolver.resolve(LocationStrategy.HEART_AND_SOUL_205, location)
        assertEquals(liveSection.id, again.section!!.id)
        assertEquals(RegionId.JOHTO, again.section!!.region)

        // And the live marker may not be drawn over the browsed canvas.
        assertNull(
            MapScreenPresenter.markerSection(
                resolved = liveSection,
                playerLocation = location,
                strategy = LocationStrategy.HEART_AND_SOUL_205,
                canvasRegion = state.canvas.region,
            )
        )
        assertTrue("state changes must have been published", events > 0)
    }

    // -------------------------------------------------------- mutation controls

    /**
     * Mutation control: the resolver must hold no default region.
     *
     * The mutation this catches is `restore an else -> Johto`: every behavioural test above would
     * still pass if the H&S branch kept a trailing default that the fixture pairs simply never
     * reach, so the production source itself is checked. A default is not a style question here --
     * it is the exact behaviour that turned an unknown map into New Bark Town.
     */
    @Test
    fun theProductionResolverHoldsNoDefaultRegion() {
        val source = stripComments(
            repoFile("app/src/main/java/com/dualdex/pokemon/LocationResolver.kt").readText()
        )

        for (forbidden in listOf(
            "JOHTO_DEFAULT",
            "NewBarkTown",
            "MAPSEC_NEW_BARK_TOWN",
            "RegionId.JOHTO",
        )) {
            assertFalse(
                "LocationResolver must not name $forbidden: an unknown or unverified map must " +
                    "resolve to nothing, never to a default town or region",
                source.contains(forbidden)
            )
        }

        val resolve = extractFunction(source, "fun resolve(strategy: LocationStrategy")
        assertNotNull("LocationResolver.resolve must remain readable", resolve)
        assertTrue(
            "resolve must dispatch on the typed strategy",
            resolve!!.contains("when (strategy)")
        )
        assertTrue(
            "every strategy must be named explicitly in the dispatch",
            listOf("HEART_AND_SOUL_205", "EMERALD", "FIRERED", "UNVERIFIED")
                .all { resolve.contains(it) }
        )
    }

    /**
     * Mutation control: the region of an observed pair must follow its map identity.
     *
     * The chain that makes a runtime observation meaningful is
     * `pinned upstream source -> Hns205MapData -> LocationResolver -> this suite`. The last link is
     * asserted above; this one pins the generated table to its own rule, so a change that mapped an
     * Alola group to Johto would make a location disagree with the section it names.
     */
    @Test
    fun theGeneratedTableAgreesWithItsOwnSectionsAboutRegion() {
        var checked = 0
        for ((group, records) in Hns205MapData.locationGroups) {
            records.forEachIndexed { num, location ->
                if (location == null) return@forEachIndexed
                val section = Hns205MapData.findSection(location.sectionId)
                assertNotNull("($group, $num) must name a declared section", section)
                assertEquals(
                    "($group, $num) ${location.mapName}: a location's region must be its section's",
                    section!!.region,
                    location.region
                )
                checked++
            }
        }
        assertTrue("the table must be non-trivial, saw $checked", checked > 500)
    }

    /**
     * Mutation control: the runtime evidence must not be satisfiable by one group of Johto maps,
     * and must not quietly imply coverage it does not have.
     *
     * Runtime coverage today is Johto-only, and this records that as a ratio: the observations span
     * more than one map group, and every cross-region edge is marked not runtime verified. If a
     * Kanto/Sinjoh/Alola checkpoint is ever added without a runtime-verified transition behind it,
     * the region assertion here reports it.
     */
    @Test
    fun runtimeCoverageSpansMoreThanOneMapGroupAndIsJohtoOnly() {
        val groups = checkpoints().map { it.getJSONObject("raw").getInt("map_group") }.toSet()
        assertTrue(
            "the runtime observations must span more than one map group, saw $groups",
            groups.size > 1
        )

        val regions = checkpoints().map { expected(it).getString("region") }.toSet()
        assertEquals(
            "runtime location evidence is Johto-only today; a Kanto/Sinjoh/Alola checkpoint must " +
                "arrive together with a runtime-verified cross-region transition",
            setOf("JOHTO"),
            regions
        )

        // The record states the limitation explicitly rather than merely omitting a field.
        assertFalse(
            "the tool-derived half must state that no cross-region edge is runtime verified",
            toolDerived().getBoolean("any_edge_runtime_verified")
        )
        assertFalse(
            "the manual half must state that no cross-region edge is runtime verified",
            crossRegionBlock().getJSONObject("manual_engine_source_verified")
                .getBoolean("any_edge_runtime_verified")
        )
        assertFalse(
            "the build's own debug transportation must not be recorded as runtime verified either",
            crossRegionBlock().getJSONObject("debug_transportation").getBoolean("runtime_verified")
        )
    }

    // ------------------------------------------------------------------ reader helpers

    private fun stripComments(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\n]*"), "")

    private fun extractFunction(source: String, declaration: String): String? {
        val start = source.indexOf(declaration)
        if (start < 0) return null
        val open = source.indexOf('{', start)
        if (open < 0) return null
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ helper

    /** The same production entrypoint the view-model uses to turn a raw read into a section. */
    private fun resolveHns(location: PlayerLocation): LocationResolution =
        RegionMapDatabase.resolveLocationDetailed(LocationStrategy.HEART_AND_SOUL_205, location)
}
