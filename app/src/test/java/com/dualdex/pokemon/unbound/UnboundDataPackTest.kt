package com.dualdex.pokemon.unbound

import com.dualdex.companion.ui.resolveHeldItem
import com.dualdex.companion.ui.usesExpansionItems
import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.hasMoveByName
import com.dualdex.pokemon.hasSpeciesByName
import com.dualdex.pokemon.resolveMove
import com.dualdex.pokemon.resolveSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Pins the exact-ROM Unbound internal-ID mapping (issue #26) and its fail-closed placeholders. */
class UnboundDataPackTest {

    private val pack = GameDataPackRegistry.getForProfile(
        engine = "CFRU", hasPhysSpecSplit = true, customPackId = "unbound_exact", romSha256 = UnboundSpeciesTable.ROM_SHA256
    )

    private fun name(id: Int) = pack.resolveSpecies(id).name

    @Test
    fun selectableByProfileIdWithoutGlobalFallback() {
        assertSame(UnboundDataPack, pack)
        assertFalse(pack.allowGlobalFallback)
    }

    @Test
    fun generatedTableIsBoundToTheExactRom() {
        assertEquals("7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7", UnboundSpeciesTable.ROM_SHA256)
        assertEquals(1294, UnboundSpeciesTable.SPECIES_COUNT)
        assertEquals(1294, UnboundSpeciesTable.NATIONAL_DEX.size)
        assertEquals(1294, UnboundSpeciesTable.FORM_CLASS.size)
    }

    @Test
    fun baseSpeciesUseTheMappedNationalDexName() {
        mapOf(277 to "Treecko", 440 to "Turtwig", 942 to "Litten", 1002 to "Tapu Koko", 1102 to "Grookey")
            .forEach { (internal, expected) ->
                val species = pack.resolveSpecies(internal)
                assertEquals("internal $internal", expected, species.name)
                assertEquals("internal ID is kept", internal, species.id)
            }
    }

    @Test
    fun formsKeepTheBaseNameWithAnUnverifiedMarker() {
        assertEquals("Muk (form unverified)", name(1035))      // Alolan Muk, regional class
        assertEquals("Venusaur (form unverified)", name(869))  // mega candidate
        assertEquals(0, pack.resolveSpecies(1035).baseAtk)     // no base-form stats inherited
    }

    @Test
    fun customReservedAndOutOfRangeIdsAreDiagnosablePlaceholders() {
        for (id in listOf(706, 0, 252, 253, 254, 276, 412, 1294, 5000, -1)) {
            assertNull("internal $id", pack.getSpecies(id))
            assertEquals("Unknown Species #$id", name(id))
        }
        assertNull("names do not map back to internal IDs", pack.getSpeciesByName("Treecko"))
        assertFalse(pack.hasSpeciesByName("Cresselia"))
    }

    @Test
    fun cfruInternalMovesAboveGen3AreNeverSameNumberLookups() {
        assertEquals("Psycho Boost", pack.resolveMove(354).name)
        assertEquals("Unknown Move #434", pack.resolveMove(434).name) // internal Flare Blitz, not Draco Meteor
        assertEquals("Unknown Move #355", pack.resolveMove(355).name)
        assertNull(pack.getMoveByName("Draco Meteor"))
        assertFalse(pack.hasMoveByName("Draco Meteor"))
        assertEquals(354, pack.getMoveByName("Psycho Boost")?.id)
    }

    @Test
    fun abilitiesStayAbsent() {
        assertSame(DeclaredAbility.Absent, pack.getDeclaredAbilityForSlot(277, 0))
    }

    @Test
    fun unboundHeldItemsDoNotUseTheExpansionOrGen3Table() {
        assertFalse(usesExpansionItems(9))
        assertEquals("Item #1", resolveHeldItem(1, 9).name)      // expansion: Poke Ball, Gen III: Master Ball
        assertEquals("Item #200", resolveHeldItem(200, 9).name)  // expansion: Harbor Mail, Gen III: Leftovers
        assertEquals("None", resolveHeldItem(0, 9).name)
        assertEquals("Poke Ball", resolveHeldItem(1, 8).name)
        assertEquals("Master Ball", resolveHeldItem(1, 2).name)
    }
}
