package com.dualdex.calculator

import com.dualdex.pokemon.hns.HnsMoveMechanicsCategory
import com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Pre-beta calculator feature freeze guard (tools/hns-move-mechanics/calculator_beta_freeze.json).
 *
 * Compares the production registry's admitted H&S move IDs and families with the frozen snapshot.
 * Correctness fixes that keep an admitted move in the same family pass unchanged; any new admission
 * (a new ID, a changed family, or a removed admission) fails until a reviewed exception updates the
 * snapshot. Set DUALDEX_FREEZE_RECORD=true only to regenerate the admitted map for a reviewed change.
 */
class HnsCalculatorBetaFreezeGuardTest {
    private val root get() = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "ci.sh").isFile }
    private val snapshotFile get() = File(root, "tools/hns-move-mechanics/calculator_beta_freeze.json")

    /** Categories that are NOT admitted. Everything else in the registry is an admitted family. */
    private val notAdmitted = setOf(
        HnsMoveMechanicsCategory.UNSUPPORTED_STATE_DEPENDENT,
        HnsMoveMechanicsCategory.UNSUPPORTED_FORMULA_DIFFERENT,
        HnsMoveMechanicsCategory.ITEM_DEPENDENT_HANDLED_ELSEWHERE,
        HnsMoveMechanicsCategory.UNCLASSIFIED,
    )

    private fun observedAdmissions(): Map<Int, String> =
        (0..1000).mapNotNull { id ->
            val entry = HnsMoveMechanicsRegistry.classify(id)
            if (entry.category in notAdmitted) null else id to entry.category.name
        }.toMap()

    @Test fun `admitted H and S move families match the frozen beta snapshot`() {
        val observed = observedAdmissions()
        if (System.getenv("DUALDEX_FREEZE_RECORD") == "true") {
            val doc = JSONObject(snapshotFile.readText())
            val map = JSONObject()
            observed.toSortedMap().forEach { (id, category) -> map.put(id.toString(), category) }
            doc.put("admittedMoves", map)
            snapshotFile.writeText(doc.toString(2) + "\n")
            return
        }
        val frozen = JSONObject(snapshotFile.readText()).getJSONObject("admittedMoves")
        val frozenMap = frozen.keys().asSequence().associate { it.toInt() to frozen.getString(it) }
        assertEquals("Frozen admitted move families changed; a reviewed exception is required",
            frozenMap.toSortedMap(), observed.toSortedMap())
    }
}
