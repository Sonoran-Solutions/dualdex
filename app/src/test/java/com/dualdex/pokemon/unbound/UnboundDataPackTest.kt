package com.dualdex.pokemon.unbound

import com.dualdex.companion.ui.resolveHeldItem
import com.dualdex.companion.ui.usesExpansionItems
import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.GameDataPackRegistry
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
        engine = "CFRU", hasPhysSpecSplit = true, customPackId = "unbound_exact"
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
    fun cfruMovesUseExactIdentityAndKeepInternalIds() {
        assertEquals("Psycho Boost", pack.resolveMove(354).name)
        val move = pack.resolveMove(434)
        assertEquals("Flare Blitz", move.name)
        assertEquals(434, move.id)
        assertEquals(434, pack.getMoveByName("Flare Blitz")?.id)
        assertEquals("Unknown Move #355", pack.resolveMove(355).name)
        assertNull(pack.getMove(136)) // no spelling correction for a nonmatching ROM label
        for (id in listOf(-1, 0, 923, 5000)) {
            assertEquals("Unknown Move #$id", pack.resolveMove(id).name)
        }
        assertNull(pack.getMoveByName("not a bundled move"))
        assertEquals(354, pack.getMoveByName("Psycho Boost")?.id)
        assertFalse(pack.isMoveAuthoritative(434))
    }

    @Test
    fun numericAbilitiesUseMappedCatalogueIdentityOnly() {
        assertEquals(DeclaredAbility.Declared(22, "Intimidate"), UnboundDataPack.getAbility(22))
        assertEquals(DeclaredAbility.Declared(150, "Protean"), UnboundDataPack.getAbility(150))
        for (id in listOf(-1, 0, 74, 77, 292, 293, 5000)) {
            assertSame(DeclaredAbility.Absent, UnboundDataPack.getAbility(id))
        }
    }

    @Test
    fun speciesSlotDeclarationsStayAbsent() {
        assertSame(DeclaredAbility.Absent, pack.getDeclaredAbilityForSlot(277, 0))
    }

    @Test
    fun unboundHeldItemsUseMappedIdentityInsteadOfSameNumberTables() {
        assertFalse(usesExpansionItems(9))
        assertEquals("Master Ball", resolveHeldItem(1, 9).name) // expansion ID 1 is Poke Ball
        assertEquals("Leftovers", resolveHeldItem(200, 9).name) // expansion ID 200 is Harbor Mail
        assertEquals(1, resolveHeldItem(1, 9).id)
        assertEquals("Item #102", resolveHeldItem(102, 9).name) // mismatched record ID
        assertEquals("Item #729", resolveHeldItem(729, 9).name)
        assertEquals("None", resolveHeldItem(0, 9).name)
        assertEquals("Poke Ball", resolveHeldItem(1, 8).name)
        assertEquals("Master Ball", resolveHeldItem(1, 2).name)
    }
}
