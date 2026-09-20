package com.dualdex.pokemon.hns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The H&S 2.0.5 move/item interaction audit (issue #9, Gap C3, review R1).
 *
 * Every expectation is the exact result of auditing the pinned damage path
 * (`pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`) and is
 * cross-checked against the exact `HeartAndSoul205DataPack`, so a moved or renamed
 * move fails here rather than silently dropping out of the gate.
 */
class HnsMoveItemInteractionTest {

    /**
     * The complete audited set, keyed by the build's own numeric `enum Move` value.
     * These IDs are the national move IDs the pinned pack reproduces.
     */
    private val expected: Map<Int, Pair<String, HnsItemInteractionKind>> = mapOf(
        374 to ("Fling" to HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY),
        363 to ("Natural Gift" to HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY),
        512 to ("Acrobatics" to HnsItemInteractionKind.ATTACKER_ITEM_ABSENCE),
        282 to ("Knock Off" to HnsItemInteractionKind.DEFENDER_ITEM_PRESENCE),
        737 to ("Poltergeist" to HnsItemInteractionKind.DEFENDER_ITEM_PRESENCE),
        449 to ("Judgment" to HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY),
        546 to ("Techno Blast" to HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY),
        672 to ("Multi-Attack" to HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY)
    )

    @Test
    fun `the audited set is exactly the source-proven item-dependent moves`() {
        assertEquals(expected.keys, HnsMoveItemInteractionRegistry.auditedMoveIds())
    }

    @Test
    fun `every audited move still resolves to its exact H and S pack identity`() {
        for ((id, spec) in expected) {
            val (name, kind) = spec
            val packed = HeartAndSoul205DataPack.getMove(id)
            assertTrue("move $id must exist in the exact H&S pack", packed != null)
            assertEquals("move $id name", name, packed!!.name)

            val interaction = HnsMoveItemInteractionRegistry.classify(id)
            assertEquals("move $id kind", kind, interaction.kind)
            assertEquals("move $id name", name, interaction.moveName)
            assertTrue("move $id must be item-dependent", interaction.isItemDependent)
            assertTrue("no interaction is modelled yet, so all must block", interaction.requiresBlock)
            assertFalse("move $id must not be silently modelled", interaction.modelled)
            assertTrue("move $id rationale", interaction.rationale.isNotBlank())
        }
    }

    @Test
    fun `an ordinary or unknown move is not item-dependent`() {
        val tackle = HeartAndSoul205DataPack.getMove(33)
        assertTrue(tackle != null)
        val ordinary = HnsMoveItemInteractionRegistry.classify(tackle!!.id)
        assertEquals(HnsItemInteractionKind.NONE, ordinary.kind)
        assertFalse(ordinary.isItemDependent)
        assertFalse(ordinary.requiresBlock)

        for (unknown in listOf<Int?>(null, -1, 99999, 0)) {
            val interaction = HnsMoveItemInteractionRegistry.classify(unknown)
            assertEquals(HnsItemInteractionKind.NONE, interaction.kind)
            assertFalse(interaction.requiresBlock)
        }
    }

    @Test
    fun `defender-presence and attacker-absence interactions are distinguished`() {
        // Knock Off and Poltergeist read the DEFENDER's item; Acrobatics reads the
        // ATTACKER's item absence. Collapsing these would lose the semantics the gate
        // is meant to protect.
        assertEquals(
            HnsItemInteractionKind.DEFENDER_ITEM_PRESENCE,
            HnsMoveItemInteractionRegistry.classify(282).kind
        )
        assertEquals(
            HnsItemInteractionKind.DEFENDER_ITEM_PRESENCE,
            HnsMoveItemInteractionRegistry.classify(737).kind
        )
        assertEquals(
            HnsItemInteractionKind.ATTACKER_ITEM_ABSENCE,
            HnsMoveItemInteractionRegistry.classify(512).kind
        )
        assertEquals(
            HnsItemInteractionKind.ATTACKER_ITEM_IDENTITY,
            HnsMoveItemInteractionRegistry.classify(374).kind
        )
    }
}
