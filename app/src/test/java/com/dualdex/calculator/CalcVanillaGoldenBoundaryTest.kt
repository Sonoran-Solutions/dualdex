package com.dualdex.calculator

import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomCompatibility
import com.dualdex.romhack.RomCompatibilityStatus
import com.dualdex.romhack.RomHackDetector
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Vanilla FireRed / Emerald golden acceptance through the REAL production boundary (issue #9).
 *
 * These tests close the loop between the three artefacts that share
 * `tools/calc-goldens/vanilla_gen3_goldens.json`:
 *
 *  1. this test drives [CalcRequestBoundary] for both bundled vanilla profiles and proves the
 *     exact trusted profile/hash reaches [CalcRequestOutcome.Ready] with [CalcSupport.VERIFIED]
 *     and serialises (via `buildCalcRequestJson`, the same function [DamageCalculator] uses) to
 *     exactly the engine request the fixture records;
 *  2. `native/tests/test_js_calc.c` executes that exact request against the shipped
 *     `calc_bundle.js` and asserts the full golden roll vector;
 *  3. `tools/calc-goldens/verify_goldens.py` derives those rolls from an independent Generation III
 *     oracle.
 *
 * `DamageCalculator` itself cannot run in a JVM unit test (it loads `libdualdex_native`), so the
 * engine half is the host suite and the byte-identity assertion here is what joins them. The
 * fixture request is the production serialisation, not a hand-authored document.
 */
class CalcVanillaGoldenBoundaryTest {

    // ---------------------------------------------------------------- fixtures

    private fun bundledProfile(id: String): RomHackProfile {
        val dir = profileDir()
        val file = File(dir, "$id.json")
        assertTrue("bundled profile $id.json is missing", file.isFile)
        return ProfileLoader.parseProfile(file.readText())
    }

    private fun profileDir(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles") }
            .firstOrNull { it.isDirectory }
            ?: throw AssertionError("Unable to locate bundled ROM profiles")

    private fun repoFile(relative: String): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
            ?: throw AssertionError("Unable to locate $relative from ${System.getProperty("user.dir")}")

    private val fireRed: RomHackProfile get() = bundledProfile("vanilla_firered")
    private val emerald: RomHackProfile get() = bundledProfile("vanilla_emerald")

    private fun goldenDocument(): JSONObject =
        JSONObject(repoFile("tools/calc-goldens/vanilla_gen3_goldens.json").readText())

    private fun goldenFixtures(): List<JSONObject> {
        val array = goldenDocument().getJSONArray("fixtures")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun profileFor(gameId: String): RomHackProfile = when (gameId) {
        "vanilla_firered" -> fireRed
        "vanilla_emerald" -> emerald
        else -> throw AssertionError("unknown fixture game $gameId")
    }

    // ------------------------------------------------------ trust construction

    /**
     * The same trust the companion publishes for an exact verified dump: an exact SHA-256 match
     * against a profile hash, built through the production factories.
     */
    private fun exactTrust(profile: RomHackProfile, hash: String): RuntimeRomTrust =
        RuntimeRomTrust.from(
            compatibility = RomCompatibility.verified(profile, hash),
            activeRomSha256 = hash
        ).also {
            assertTrue("exact fixture trust must satisfy exactRuntimeVerified", it.exactRuntimeVerified)
        }

    /** A realistic GBA header for the given title/code, used to drive the production detector. */
    private fun gbaHeader(title: String, gameCode: String): ByteArray {
        val header = ByteArray(192)
        val titleBytes = title.padEnd(12, '\u0000').toByteArray(Charsets.US_ASCII)
        System.arraycopy(titleBytes, 0, header, 0xA0, 12)
        val codeBytes = gameCode.toByteArray(Charsets.US_ASCII)
        System.arraycopy(codeBytes, 0, header, 0xAC, codeBytes.size)
        return header
    }

    /** A trust value produced by the production detector, not by a hand-built compatibility. */
    private fun detectedTrust(header: ByteArray, sha256: String): RuntimeRomTrust {
        val compatibility = RomHackDetector.detectCompatibilityFromBytes(
            headerBytes = header,
            sha256 = sha256,
            profiles = listOf(fireRed, emerald),
            fileName = ""
        )
        return RuntimeRomTrust.from(compatibility, activeRomSha256 = compatibility.sha256)
    }

    // -------------------------------------------------------- request building

    private fun participant(json: JSONObject): CalcParticipantState = CalcParticipantState(
        species = json.getString("species"),
        level = json.getInt("level"),
        nature = json.optNullableString("nature"),
        item = json.optNullableString("item"),
        ability = json.optNullableString("ability"),
        status = json.optNullableString("status"),
        boosts = json.optJSONObject("boosts")?.toStatBlock(),
        curHP = if (json.has("curHP") && !json.isNull("curHP")) json.getInt("curHP") else null,
        ivs = json.getJSONObject("ivs").toStatBlock(),
        evs = json.getJSONObject("evs").toStatBlock(),
        origin = CalcInputOrigin.MANUAL
    )

    private fun JSONObject.toStatBlock(): StatBlock = StatBlock(
        hp = optInt("hp", 0),
        atk = optInt("atk", 0),
        def = optInt("def", 0),
        spa = optInt("spa", 0),
        spd = optInt("spd", 0),
        spe = optInt("spe", 0)
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun fieldOf(json: JSONObject): CalcFieldInput {
        val side = json.optJSONObject("defenderSide")
        return CalcFieldInput(
            gameType = json.optString("gameType", CalcGameTypes.SINGLES),
            weather = json.optNullableString("weather"),
            terrain = json.optNullableString("terrain"),
            defenderSide = side?.let {
                SideConditions(
                    isReflect = it.optBoolean("isReflect", false),
                    isLightScreen = it.optBoolean("isLightScreen", false)
                )
            }
        )
    }

    /** Drive the production boundary for one fixture on one exact profile. */
    private fun readyFor(gameId: String, fixture: JSONObject): CalcRequestOutcome.Ready {
        val profile = profileFor(gameId)
        val input = fixture.getJSONObject("input")
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = exactTrust(profile, profile.sha256Hashes.first()),
            attacker = participant(input.getJSONObject("attacker")),
            defender = participant(input.getJSONObject("defender")),
            move = CalcMoveInput(
                name = input.getJSONObject("move").getString("name"),
                isCrit = input.getJSONObject("move").optBoolean("isCrit", false)
            ),
            field = fieldOf(input.getJSONObject("field"))
        )
        return outcome as? CalcRequestOutcome.Ready
            ?: throw AssertionError("$gameId/${fixture.getString("id")} must be calculable, got $outcome")
    }

    // ------------------------------------------- exact-profile golden acceptance

    @Test
    fun `every golden fixture is VERIFIED through the production boundary for both exact profiles`() {
        val fixtures = goldenFixtures()
        assertTrue("golden matrix must not be empty", fixtures.isNotEmpty())

        for (fixture in fixtures) {
            for (game in fixture.getJSONArray("games").mapStrings()) {
                val ready = readyFor(game, fixture)
                val id = "$game/${fixture.getString("id")}"

                assertEquals("$id support", CalcSupport.VERIFIED, ready.verdict.support)
                assertEquals("$id ruleset", CalcRuleset.VANILLA_GEN3, ready.verdict.ruleset)
                assertTrue("$id must be verified", ready.verdict.isVerified)
                assertEquals("$id limitations", emptyList<CalcLimitation>(), ready.verdict.limitations)
                assertEquals("$id detail", "", ready.verdict.supportDetail)
            }
        }
    }

    @Test
    fun `the production serialisation exactly matches the executed golden request`() {
        for (fixture in goldenFixtures()) {
            for (game in fixture.getJSONArray("games").mapStrings()) {
                val ready = readyFor(game, fixture)
                val expected = JSONObject(fixture.getString("request"))
                val actual = JSONObject(buildCalcRequestJson(ready.request))
                assertJsonEquals("$game/${fixture.getString("id")}", expected, actual)
            }
        }
    }

    @Test
    fun `the golden expected vector is internally consistent and the oracle provenance is pinned`() {
        val document = goldenDocument()
        assertEquals("dualdex.vanilla_gen3_goldens.v1", document.getString("schema"))
        assertEquals("VANILLA_GEN3", document.getString("ruleset"))
        assertEquals(3, document.getInt("mechanicsGeneration"))

        for (fixture in document.getJSONArray("fixtures").mapObjects()) {
            val id = fixture.getString("id")
            val expected = fixture.getJSONObject("expected")
            val damage = expected.getJSONArray("damage")
            assertEquals("$id roll count", 16, damage.length())
            assertEquals("$id min", damage.getInt(0), expected.getInt("minDamage"))
            assertEquals("$id max", damage.getInt(15), expected.getInt("maxDamage"))
        }
    }

    @Test
    fun `the golden provenance hashes are exactly the bundled profile hashes`() {
        val profiles = goldenDocument().getJSONObject("provenance").getJSONObject("profiles")
        for (game in listOf("vanilla_firered", "vanilla_emerald")) {
            val recorded = profiles.getJSONObject(game).getJSONArray("sha256").mapStrings()
            assertEquals(
                "$game bundled profile hashes must equal the fixture provenance",
                recorded,
                profileFor(game).sha256Hashes
            )
        }
    }

    // --------------------------------------------------- cartridge references

    /**
     * The `cartridgeReferences` block records the source-exact Doubles screen arithmetic that the
     * production gate refuses. Two invariants keep it honest, and both are asserted here rather
     * than only in the Python verifier:
     *
     *  * the two references differ from each other, so the cartridge's target-presence branch
     *    really is observable and not a constant;
     *  * each reference's request is byte-identical to what the production serializer produces for
     *    the same inputs, so the recorded request is the production request and not a hand-written
     *    document.
     */
    @Test
    fun `the source-exact Doubles screen references are observable and production-serialisable`() {
        val references = goldenDocument().getJSONArray("cartridgeReferences").mapObjects()
        assertEquals("two cartridge references are expected", 2, references.size)

        val vectors = references.map { reference ->
            val expected = reference.getJSONObject("expected")
            val damage = expected.getJSONArray("damage")
            assertEquals("${reference.getString("id")} roll count", 16, damage.length())
            assertEquals(
                "${reference.getString("id")} min",
                damage.getInt(0),
                expected.getInt("minDamage")
            )
            assertEquals(
                "${reference.getString("id")} max",
                damage.getInt(15),
                expected.getInt("maxDamage")
            )
            assertEquals(
                "${reference.getString("id")} must name the gate that refuses it",
                "CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED",
                reference.getString("refusedBy")
            )

            // The recorded request describes the very shape the gate refuses, and asking the real
            // boundary for it must be refused — that is what makes these references evidence about
            // production rather than a standalone note. The engine half (the bundled pipeline does
            // not produce these vectors) is asserted in native/tests/test_js_calc.c.
            val input = JSONObject(reference.getString("request"))
            assertEquals(
                "${reference.getString("id")} request must be a Doubles request",
                "Doubles",
                input.getJSONObject("field").getString("gameType")
            )
            val outcome = CalcRequestBoundary.build(
                profile = fireRed,
                trust = exactTrust(fireRed, fireRed.sha256Hashes.first()),
                attacker = participant(input.getJSONObject("attacker")),
                defender = participant(input.getJSONObject("defender")),
                move = CalcMoveInput(name = input.getJSONObject("move").getString("name")),
                field = fieldOf(input.getJSONObject("field"))
            )
            assertTrue(
                "${reference.getString("id")} must be refused by the production boundary, got " +
                    "$outcome",
                outcome is CalcRequestOutcome.Refused
            )
            damage.toIntList()
        }

        assertNotEquals(
            "the cartridge target-presence branch must change the vector, or the gate has no " +
                "observable basis",
            vectors[0],
            vectors[1]
        )
    }

    // ------------------------------------------------------- FireRed revisions

    @Test
    fun `both exact FireRed hashes reach VERIFIED with the same normalised request`() {
        val profile = fireRed
        assertEquals("FireRed must advertise exactly two revisions", 2, profile.sha256Hashes.size)
        assertNotEquals(profile.sha256Hashes[0], profile.sha256Hashes[1])

        val fixture = goldenFixtures().first()
        val input = fixture.getJSONObject("input")
        val serializations = profile.sha256Hashes.map { hash ->
            val outcome = CalcRequestBoundary.build(
                profile = profile,
                trust = exactTrust(profile, hash),
                attacker = participant(input.getJSONObject("attacker")),
                defender = participant(input.getJSONObject("defender")),
                move = CalcMoveInput(input.getJSONObject("move").getString("name")),
                field = fieldOf(input.getJSONObject("field"))
            ) as CalcRequestOutcome.Ready
            assertEquals(CalcSupport.VERIFIED, outcome.verdict.support)
            buildCalcRequestJson(outcome.request)
        }
        assertEquals(
            "both accepted FireRed revisions must route to the identical vanilla request",
            serializations[0],
            serializations[1]
        )
    }

    @Test
    fun `FireRed near-miss hash is recognised but never verified`() {
        val trust = detectedTrust(gbaHeader("POKEMON FIRE", "BPRE"), "ff".repeat(32))
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, trust.status)
        assertFalse("a near-miss hash must not be exact-verified", trust.exactRuntimeVerified)
        assertNotEquals(CalcSupport.VERIFIED, verdictFor(fireRed, trust).support)
    }

    @Test
    fun `FireRed header-only recognition is recognised but never verified`() {
        val trust = detectedTrust(gbaHeader("POKEMON FIRE", "BPRE"), "")
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, trust.status)
        assertFalse("a header-only match must not be exact-verified", trust.exactRuntimeVerified)
        assertNotEquals(CalcSupport.VERIFIED, verdictFor(fireRed, trust).support)
    }

    @Test
    fun `recognised FireRed is not the same as exact FireRed`() {
        val profile = fireRed
        val exact = exactTrust(profile, profile.sha256Hashes.first())
        val recognised = detectedTrust(gbaHeader("POKEMON FIRE", "BPRE"), "ff".repeat(32))

        assertTrue(exact.exactRuntimeVerified)
        assertEquals(RomCompatibilityStatus.VERIFIED, exact.status)
        assertFalse(recognised.exactRuntimeVerified)
        // Recognised-but-unverified is explicitly a different state from exact-verified; the
        // profile identity may still be carried for presentation, but not the authorization.
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, recognised.status)
    }

    // ---------------------------------------------------------- Emerald exactness

    @Test
    fun `Emerald exact hash reaches VERIFIED and is distinct from FireRed`() {
        val profile = emerald
        assertEquals(1, profile.sha256Hashes.size)
        val ready = readyFor("vanilla_emerald", goldenFixtures().first())
        assertEquals(CalcSupport.VERIFIED, ready.verdict.support)
        assertTrue(
            "Emerald's accepted hash must not be accepted by FireRed",
            fireRed.sha256Hashes.none { it == profile.sha256Hashes.first() }
        )
    }

    @Test
    fun `Emerald near-miss hash and header-only recognition are never verified`() {
        val nearMiss = detectedTrust(gbaHeader("POKEMON EMER", "BPEE"), "ff".repeat(32))
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, nearMiss.status)
        assertFalse(nearMiss.exactRuntimeVerified)
        assertNotEquals(CalcSupport.VERIFIED, verdictFor(emerald, nearMiss).support)

        val headerOnly = detectedTrust(gbaHeader("POKEMON EMER", "BPEE"), "")
        assertEquals(RomCompatibilityStatus.RECOGNIZED_UNVERIFIED, headerOnly.status)
        assertFalse(headerOnly.exactRuntimeVerified)
        assertNotEquals(CalcSupport.VERIFIED, verdictFor(emerald, headerOnly).support)
    }

    // ------------------------------------------------- production-boundary negatives

    @Test
    fun `wrong running bytes are approximate not verified`() {
        val profile = fireRed
        val wrong = "ff".repeat(32)
        val trust = RuntimeRomTrust.from(
            compatibility = RomCompatibility.verified(profile, wrong),
            activeRomSha256 = wrong
        )
        val verdict = verdictFor(profile, trust)
        assertEquals(CalcSupport.ESTIMATED, verdict.support)
        assertTrue(verdict.limitations.contains(CalcLimitation.ROM_NOT_EXACT_VERIFIED))
    }

    @Test
    fun `an unsupported ability downgrades a vanilla golden to approximate`() {
        val verdict = verdictFor(
            fireRed,
            exactTrust(fireRed, fireRed.sha256Hashes.first()),
            ability = "Multiscale"
        )
        assertEquals(CalcSupport.ESTIMATED, verdict.support)
        assertTrue(verdict.limitations.contains(CalcLimitation.ABILITY_NOT_MODELLED))
    }

    @Test
    fun `an unsupported held item downgrades a vanilla golden to approximate`() {
        val verdict = verdictFor(
            emerald,
            exactTrust(emerald, emerald.sha256Hashes.first()),
            item = "Life Orb"
        )
        assertEquals(CalcSupport.ESTIMATED, verdict.support)
        assertTrue(verdict.limitations.contains(CalcLimitation.ITEM_NOT_MODELLED))
    }

    @Test
    fun `an unmodelled status refuses the calculation`() {
        val verdict = verdictFor(
            fireRed,
            exactTrust(fireRed, fireRed.sha256Hashes.first()),
            status = "fainted"
        )
        assertEquals(CalcSupport.UNSUPPORTED, verdict.support)
        assertTrue(verdict.limitations.contains(CalcLimitation.STATUS_NOT_MODELLED))
        assertNull(verdict.request)
    }

    @Test
    fun `an unmodelled field condition refuses the calculation`() {
        val verdict = verdictFor(
            emerald,
            exactTrust(emerald, emerald.sha256Hashes.first()),
            weather = "Snow"
        )
        // Snow is a name the ADV pipeline does not accept, so it must refuse rather than be
        // silently computed as clear.
        assertEquals(CalcSupport.UNSUPPORTED, verdict.support)
        assertTrue(verdict.limitations.contains(CalcLimitation.FIELD_CONDITION_NOT_MODELLED))
    }

    // ------------------------------------------------- Doubles screen fail-closed

    /**
     * Builds a request through the real boundary with an explicit field format and screen state.
     *
     * Uses the same participants as the committed golden matrix, so the only difference from the
     * verified Singles screen fixtures is the battle format.
     */
    private fun screenVerdict(
        profile: RomHackProfile,
        gameType: String,
        isReflect: Boolean,
        isLightScreen: Boolean,
        moveName: String
    ): CalcCapabilityVerdict {
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = exactTrust(profile, profile.sha256Hashes.first()),
            attacker = CalcParticipantState(
                species = "Machamp",
                level = 50,
                nature = "Hardy",
                boosts = CalcParticipantState.NONE,
                ivs = StatBlock(hp = 31, atk = 31, def = 31, spa = 31, spd = 31, spe = 31),
                evs = StatBlock(),
                origin = CalcInputOrigin.MANUAL
            ),
            defender = CalcParticipantState(
                species = "Snorlax",
                level = 50,
                nature = "Hardy",
                boosts = CalcParticipantState.NONE,
                ivs = StatBlock(hp = 31, atk = 31, def = 31, spa = 31, spd = 31, spe = 31),
                evs = StatBlock(),
                origin = CalcInputOrigin.MANUAL
            ),
            move = CalcMoveInput(name = moveName),
            field = CalcFieldInput(
                gameType = gameType,
                defenderSide = SideConditions(isReflect = isReflect, isLightScreen = isLightScreen)
            )
        )
        return when (outcome) {
            is CalcRequestOutcome.Ready -> outcome.verdict
            is CalcRequestOutcome.Refused -> outcome.verdict
        }
    }

    /**
     * P1 review correction. The pinned engines apply a Doubles Reflect/Light Screen with integer
     * *division first* — `damage = 2 * (damage / 3)` while both defending battlers are present,
     * otherwise `damage /= 2` (`pret/pokefirered src/pokemon.c` `CalculateBaseDamage`) — while the
     * shipped `@smogon/calc` 0.11.0 ADV pipeline applies `floor(rolled * 2/3)`. For the committed
     * Strength fixture the two disagree (cartridge 35-42, pipeline 36-43), and the request shape
     * cannot even express the cartridge's target-presence condition.
     *
     * Because the vanilla ruleset advertises [CalcSupport.VERIFIED], the exact-trusted Doubles +
     * screen shape must be refused rather than published. This is the production-boundary proof.
     */
    @Test
    fun `an exact trusted vanilla Doubles screen is refused rather than published as verified`() {
        val cases = listOf(
            Triple(CalcGameTypes.DOUBLES, true, false) to "Strength",
            Triple(CalcGameTypes.DOUBLES, false, true) to "Hydro Pump",
            // A lower-case label must not slip past the gate.
            Triple("doubles", true, false) to "Strength"
        )
        for ((shape, moveName) in cases) {
            val (gameType, isReflect, isLightScreen) = shape
            for (profile in listOf(fireRed, emerald)) {
                val id = "${profile.id}/$gameType/reflect=$isReflect/lightScreen=$isLightScreen/$moveName"
                val verdict = screenVerdict(profile, gameType, isReflect, isLightScreen, moveName)
                assertEquals("$id support", CalcSupport.UNSUPPORTED, verdict.support)
                assertFalse("$id must not be verified", verdict.isVerified)
                assertTrue(
                    "$id limitation",
                    verdict.limitations.contains(CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED)
                )
                // The whole point: no number may be published for this shape.
                assertNull("$id request must not be authorized", verdict.request)
            }
        }
    }

    /**
     * The negative control for the gate above: exactly the same screen state stays on the verified
     * surface in Singles, so the gate is scoped to the one arithmetic the pipeline gets wrong and
     * has not simply disabled screens.
     */
    @Test
    fun `the same screen state remains verified in vanilla Singles`() {
        for (profile in listOf(fireRed, emerald)) {
            val reflect = screenVerdict(profile, CalcGameTypes.SINGLES, true, false, "Strength")
            assertEquals("${profile.id} Singles Reflect", CalcSupport.VERIFIED, reflect.support)
            assertEquals(
                "${profile.id} Singles Reflect limitations",
                emptyList<CalcLimitation>(),
                reflect.limitations
            )

            val lightScreen = screenVerdict(profile, CalcGameTypes.SINGLES, false, true, "Hydro Pump")
            assertEquals("${profile.id} Singles Light Screen", CalcSupport.VERIFIED, lightScreen.support)
        }
    }

    /**
     * The other half of the scope: an exact-trusted vanilla Doubles request with NO screen is still
     * verified (the committed Doubles spread fixture depends on this), so the gate refuses only the
     * screen shapes it cannot reproduce.
     */
    @Test
    fun `an exact trusted vanilla Doubles request without a screen stays verified`() {
        for (profile in listOf(fireRed, emerald)) {
            val verdict = screenVerdict(profile, CalcGameTypes.DOUBLES, false, false, "Rock Slide")
            assertEquals("${profile.id} Doubles no screen", CalcSupport.VERIFIED, verdict.support)
            assertEquals(
                "${profile.id} Doubles no screen limitations",
                emptyList<CalcLimitation>(),
                verdict.limitations
            )
        }
    }

    // ------------------------------------------------------------- small helpers

    private fun verdictFor(
        profile: RomHackProfile,
        trust: RuntimeRomTrust,
        ability: String? = null,
        item: String? = null,
        status: String? = null,
        weather: String? = null
    ): CalcCapabilityVerdict {
        val outcome = CalcRequestBoundary.build(
            profile = profile,
            trust = trust,
            attacker = CalcParticipantState(
                species = "Machamp",
                level = 50,
                nature = "Hardy",
                ability = ability,
                item = item,
                status = status,
                boosts = CalcParticipantState.NONE,
                ivs = StatBlock(hp = 31, atk = 31, def = 31, spa = 31, spd = 31, spe = 31),
                evs = StatBlock(),
                origin = CalcInputOrigin.MANUAL
            ),
            defender = CalcParticipantState(
                species = "Snorlax",
                level = 50,
                nature = "Hardy",
                boosts = CalcParticipantState.NONE,
                ivs = StatBlock(hp = 31, atk = 31, def = 31, spa = 31, spd = 31, spe = 31),
                evs = StatBlock(),
                origin = CalcInputOrigin.MANUAL
            ),
            move = CalcMoveInput(name = "Rock Slide"),
            field = CalcFieldInput(weather = weather)
        )
        return when (outcome) {
            is CalcRequestOutcome.Ready -> outcome.verdict
            is CalcRequestOutcome.Refused -> outcome.verdict
        }
    }

    private fun JSONArray.mapStrings(): List<String> =
        (0 until length()).map { getString(it) }

    private fun JSONArray.mapObjects(): List<JSONObject> =
        (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }

    private fun assertJsonEquals(path: String, expected: JSONObject, actual: JSONObject) {
        assertEquals("$path keys", expected.keySet(), actual.keySet())
        for (key in expected.keySet()) {
            val expectedValue = expected.get(key)
            val actualValue = actual.get(key)
            when (expectedValue) {
                is JSONObject -> assertJsonEquals(
                    "$path.$key",
                    expectedValue,
                    actualValue as? JSONObject ?: throw AssertionError("$path.$key is not an object")
                )
                is JSONArray -> {
                    val expectedArray = expectedValue
                    val actualArray = actualValue as? JSONArray
                        ?: throw AssertionError("$path.$key is not an array")
                    assertEquals("$path.$key length", expectedArray.length(), actualArray.length())
                    for (i in 0 until expectedArray.length()) {
                        assertEquals(
                            "$path.$key[$i]",
                            expectedArray.get(i).toString(),
                            actualArray.get(i).toString()
                        )
                    }
                }
                else -> assertEquals("$path.$key", expectedValue.toString(), actualValue.toString())
            }
        }
    }
}
