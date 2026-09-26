package com.dualdex.calculator.census

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The census' committed-artifact gate (issue #84).
 *
 * Runs the whole derivation - the real production policy over the committed pinned trainer
 * inventory - and compares the result with the committed `census.json` and
 * `docs/HNS_CALC_CENSUS.md`. A stale or hand-edited artifact fails here, which is what makes the
 * committed report trustworthy.
 *
 * Regenerate with:
 *   ./gradlew testDebugUnitTest -Pdualdex.census.generate=true
 *
 * This test is self-contained: it needs no ROM, no emulator, no network and no upstream
 * checkout, because the inventory it consumes is itself re-derived from the pinned checkout by
 * `./ci.sh source-check` (tools/hns-calc-census/generate_hns_trainer_census.py --check).
 */
class HnsCalcCensusArtifactTest {

    private val root: File get() = HnsCalcCensusGenerator.repositoryRoot()

    private val generating: Boolean
        get() = System.getProperty("dualdex.census.generate") == "true"

    private fun artifacts() = HnsCalcCensusGenerator.deriveCached(root)

    @Test
    fun `census artifacts are current`() {
        val derived = artifacts()
        if (generating) {
            HnsCalcCensusGenerator.write(
                root,
                derived,
                full = System.getProperty("dualdex.census.full") == "true"
            )
            println(
                "regenerated census artifacts: " +
                    "${HnsCalcCensusGenerator.JSON_RELATIVE_PATH}, " +
                    HnsCalcCensusGenerator.DOC_RELATIVE_PATH
            )
            return
        }
        val problem = HnsCalcCensusGenerator.check(root, derived)
        assertTrue(
            "the committed census artifacts are stale: $problem",
            problem == null
        )
    }

    @Test
    fun `census artifacts exist and are not empty`() {
        // In generation mode the first test writes them; this test only asserts the committed
        // artifacts are present, so it is skipped when the run is the one producing them.
        if (generating) return
        assertTrue(File(root, HnsCalcCensusGenerator.JSON_RELATIVE_PATH).length() > 0)
        assertTrue(File(root, HnsCalcCensusGenerator.DOC_RELATIVE_PATH).length() > 0)
    }

    @Test
    fun `census covers every trainer battle and Pokemon of the pinned inventory`() {
        val derived = artifacts()
        val run = derived.run
        assertEquals(651, run.trainers.size)
        assertEquals(1832, run.trainers.sumOf { it.party.size })
        assertEquals(310, run.abilityDomain.size)
    }
}
