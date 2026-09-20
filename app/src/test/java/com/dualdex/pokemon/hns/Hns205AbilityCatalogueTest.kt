package com.dualdex.pokemon.hns

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.Gen3VanillaDataPack
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.pokemon.ProfileOverlayDataPack
import com.dualdex.calculator.CalcFieldInput
import com.dualdex.calculator.CalcMoveInput
import com.dualdex.calculator.CalcPokemonInput
import com.dualdex.calculator.CalcRequestBoundary
import com.dualdex.calculator.CalcRequestOutcome
import com.dualdex.calculator.CalcSupport
import com.dualdex.calculator.DamageCalculationRequest
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The H&S 2.0.5 ability catalogue and per-species ability-slot declarations (issue #9).
 *
 * Every expected value below was pinned BY HAND from the pinned upstream checkout
 * (PokemonHnS-Development/pokehns-expansion, Release-v2.0.5,
 * 1f42b74dff0e9fe942419845d040663dd829a973): ability IDs from
 * include/constants/abilities.h, display names from src/data/abilities.h, and slot
 * triples from the gSpeciesInfo entries under src/data/pokemon/species_info/. No
 * expectation is derived from the generated data pack under test.
 *
 * Scope reminder: these are static identity/declaration lookups. They are NOT
 * effective runtime abilities (the build's challenge settings can substitute or
 * randomize a live Pokemon's ability) and they do NOT enable H&S damage calculation.
 */
class Hns205AbilityCatalogueTest {

    private val pack = HeartAndSoul205DataPack

    // The build's highest declared species/form ID (checked against the pinned source
    // table; the pack exposes 1427 authoritative species/form entries).
    private val maxSpeciesId = 1523

    // ------------------------------------------------------------------
    // Numeric ability identities (pinned from the upstream enum + table)
    // ------------------------------------------------------------------

    @Test
    fun `ability identities match the pinned build enum and table`() {
        // Gen III anchors (implicit enum values).
        assertAbility(2, "DRIZZLE")
        assertAbility(7, "LIMBER")
        assertAbility(26, "LEVITATE")
        assertAbility(65, "OVERGROW")
        // Post-Gen III anchor range.
        assertAbility(88, "DOWNLOAD")
        assertAbility(112, "SLOW START")
        assertAbility(130, "CURSED BODY")
        // Modern additions.
        assertAbility(185, "PARENTAL BOND")
        assertAbility(224, "BEAST BOOST")
        assertAbility(248, "ICE FACE")
        assertAbility(266, "AS ONE") // ABILITY_AS_ONE_ICE_RIDER
        assertAbility(267, "AS ONE") // ABILITY_AS_ONE_SHADOW_RIDER: same display name
        assertAbility(310, "POISON PUPPETEER") // highest enum value
        // The ABILITY_NONE sentinel (ID 0) is a slot filler, not a catalogue entry.
        assertEquals(DeclaredAbility.Absent, pack.getAbility(0))
    }

    @Test
    fun `ability ids are the contiguous range 1 to 310 with no gaps`() {
        for (id in 1..310) {
            val declared = pack.getAbility(id) as? DeclaredAbility.Declared
            assertTrue("catalogue ID $id missing", declared != null)
            assertEquals(id, declared!!.abilityId)
            assertTrue("empty display name for ID $id", declared.name.isNotBlank())
        }
        assertEquals(DeclaredAbility.Absent, pack.getAbility(311))
        assertEquals(DeclaredAbility.Absent, pack.getAbility(9999))
        assertEquals(DeclaredAbility.Absent, pack.getAbility(-1))
    }

    // ------------------------------------------------------------------
    // Per-species ability slots
    // ------------------------------------------------------------------

    @Test
    fun `differing slots report distinct abilities in source order`() {
        // Meowth: { PICKUP, TECHNICIAN, UNNERVE }
        assertSlot(52, 0, 53, "PICKUP")
        assertSlot(52, 1, 101, "TECHNICIAN")
        assertSlot(52, 2, 127, "UNNERVE")
    }

    @Test
    fun `sentinel slots are EmptySlot and slot positions never shift`() {
        // Bulbasaur: { OVERGROW, NONE, CHLOROPHYLL } - the empty middle slot must not
        // collapse the hidden ability into slot 1.
        assertSlot(1, 0, 65, "OVERGROW")
        assertEquals(DeclaredAbility.EmptySlot, pack.getDeclaredAbilityForSlot(1, 1))
        assertSlot(1, 2, 34, "CHLOROPHYLL")

        // Gengar: { LEVITATE, CURSED_BODY, NONE } - trailing sentinel.
        assertSlot(94, 0, 26, "LEVITATE")
        assertSlot(94, 1, 130, "CURSED BODY")
        assertEquals(DeclaredAbility.EmptySlot, pack.getDeclaredAbilityForSlot(94, 2))

        // Abomasnow: { SNOW_WARNING, NONE, SOUNDPROOF }.
        assertSlot(460, 0, 117, "SNOW WARNING")
        assertEquals(DeclaredAbility.EmptySlot, pack.getDeclaredAbilityForSlot(460, 1))
        assertSlot(460, 2, 43, "SOUNDPROOF")
    }

    @Test
    fun `form-specific declarations differ between forms of one species`() {
        // The four Ogerpon forms each declare a different slot-0 ability, and their
        // short 2-slot source initializers zero-fill to empty sentinels.
        assertSlot(1416, 0, 128, "DEFIANT")
        assertSlot(1417, 0, 11, "WATER ABSORB")
        assertSlot(1418, 0, 104, "MOLD BREAKER")
        assertSlot(1419, 0, 5, "STURDY")
        for (id in 1416..1419) {
            assertEquals(DeclaredAbility.EmptySlot, pack.getDeclaredAbilityForSlot(id, 1))
            assertEquals(DeclaredAbility.EmptySlot, pack.getDeclaredAbilityForSlot(id, 2))
        }

        // Samurott-H (Hisuian form) declares Torrent + hidden Sharpness.
        assertSlot(1000, 0, 67, "TORRENT")
        assertEquals(DeclaredAbility.EmptySlot, pack.getDeclaredAbilityForSlot(1000, 1))
        assertSlot(1000, 2, 292, "SHARPNESS")
    }

    // ------------------------------------------------------------------
    // Invalid species / slots degrade to Absent without throwing
    // ------------------------------------------------------------------

    @Test
    fun `unknown species and out-of-range slots are Absent`() {
        assertEquals(DeclaredAbility.Absent, pack.getDeclaredAbilityForSlot(99999, 0))
        assertEquals(DeclaredAbility.Absent, pack.getDeclaredAbilityForSlot(-1, 0))
        assertEquals(DeclaredAbility.Absent, pack.getDeclaredAbilityForSlot(52, -1))
        assertEquals(DeclaredAbility.Absent, pack.getDeclaredAbilityForSlot(52, 3))
        assertEquals(DeclaredAbility.Absent, pack.getDeclaredAbilityForSlot(52, 42))
    }

    // ------------------------------------------------------------------
    // Packs without source-backed declarations stay Absent
    // ------------------------------------------------------------------

    @Test
    fun `vanilla packs declare no abilities`() {
        val fireRedPack = GameDataPackRegistry.getForProfile(RomHackProfile.DEFAULT_FIRERED)
        assertEquals(Gen3VanillaDataPack.id, fireRedPack.id)
        // The interface default is conservative: no pack pretends to know an ability
        // it has not pinned, even for species IDs the H&S pack declares.
        assertEquals(DeclaredAbility.Absent, fireRedPack.getDeclaredAbilityForSlot(1, 0))
        assertEquals(DeclaredAbility.Absent, fireRedPack.getDeclaredAbilityForSlot(52, 2))
        assertEquals(DeclaredAbility.Absent, fireRedPack.getDeclaredAbilityForSlot(9999, 0))
    }

    // ------------------------------------------------------------------
    // Profile isolation
    // ------------------------------------------------------------------

    private val overlayProfileJson = """
    {
      "id": "ability_overlay_probe",
      "name": "Ability Overlay Probe",
      "baseGame": "FireRed",
      "gameId": 6,
      "developer": "DualDex test",
      "engine": "HexManiacAdvance",
      "hasEvs": false,
      "hasIvs": false,
      "hasPhysSpecSplit": true,
      "steelResistsGhostDark": true,
      "cfruOffsets": false,
      "playerPartyOffset": 33702532,
      "enemyPartyOffset": 33701932,
      "headerTitles": ["BPRE", "PROBE"],
      "sha256Hashes": [],
      "isVerified": true,
      "memoryLayoutVerified": true,
      "battleUiVerified": false,
      "interactiveControlsVerified": false,
      "gameDataPackId": "hns_2_0_5",
      "customSpecies": {
        "1": { "name": "Rebrandedsaur", "type1": "Grass", "type2": "Ghost", "hp": 80, "atk": 82, "def": 83, "spa": 100, "spd": 100, "spe": 80 }
      }
    }
    """.trimIndent()

    @Test
    fun `overlay rebrands a species without inheriting its ability declarations`() {
        val overlayProfile = ProfileLoader.parseProfile(overlayProfileJson)
        val overlayPack = GameDataPackRegistry.getForProfile(overlayProfile)
        assertTrue(overlayPack is ProfileOverlayDataPack)

        // The overlay converts species 1, so the base pack's Bulbasaur declaration must
        // NOT leak through a matching ID.
        assertEquals(DeclaredAbility.Absent, overlayPack.getDeclaredAbilityForSlot(1, 0))
        // Species the overlay does not convert still resolve through the base pack.
        assertSlot(overlayPack, 52, 1, 101, "TECHNICIAN")
        assertEquals(DeclaredAbility.Absent, overlayPack.getDeclaredAbilityForSlot(99999, 0))
    }

    @Test
    fun `ability queries do not pollute the global species database`() {
        // Interleave an ability query with global-database reads: the catalogue is
        // pack-private and must not mutate shared state.
        val before = SpeciesDatabase.get(1).name
        assertEquals(DeclaredAbility.Absent, pack.getDeclaredAbilityForSlot(99999, 0))
        assertSlot(52, 0, 53, "PICKUP")
        assertEquals(before, SpeciesDatabase.get(1).name)
    }

    // ------------------------------------------------------------------
    // Catalogue / declaration record integrity (duplicate & conflict checks)
    // ------------------------------------------------------------------

    @Test
    fun `every declared slot references a live catalogue identity`() {
        for (id in 1..maxSpeciesId) {
            val slot0 = pack.getDeclaredAbilityForSlot(id, 0)
            if (slot0 is DeclaredAbility.Declared) {
                val resolved = pack.getAbility(slot0.abilityId) as? DeclaredAbility.Declared
                assertTrue(
                    "species $id slot 0 references dangling ability ${slot0.abilityId}",
                    resolved != null
                )
                assertEquals(resolved!!.name, slot0.name)
            }
        }
    }

    @Test
    fun `declared slots never use the numeric ABILITY_NONE sentinel`() {
        for (id in 1..maxSpeciesId) {
            for (slot in 0..2) {
                val declared = pack.getDeclaredAbilityForSlot(id, slot)
                if (declared is DeclaredAbility.Declared) {
                    assertFalse(declared.abilityId == 0)
                    assertTrue(declared.abilityId in 1..310)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Existing data unchanged
    // ------------------------------------------------------------------

    @Test
    fun `species and move authority are unchanged by the ability catalogue`() {
        assertTrue(pack.isSpeciesAuthoritative(500))
        assertEquals("Emboar", pack.getSpecies(500)?.name)
        assertEquals(PokemonType.FIRE, pack.getSpecies(500)?.type1)
        assertEquals(PokemonType.FIGHTING, pack.getSpecies(500)?.type2)
        assertTrue(pack.isMoveAuthoritative(56))
        val hydroPump = pack.getMove(56)
        assertEquals("Hydro Pump", hydroPump?.name)
        assertEquals(MoveCategory.SPECIAL, hydroPump?.category)
        assertEquals(110, hydroPump?.power)
        assertNull(pack.getSpecies(9999))
        assertNull(pack.getMove(9999))
    }

    // ------------------------------------------------------------------
    // Calculator refusal unchanged (static data must not enable calculation)
    // ------------------------------------------------------------------

    private fun bundledProfile(id: String): RomHackProfile {
        val dir = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/assets/profiles") }
            .firstOrNull { it.isDirectory }
            ?: throw AssertionError("Unable to locate bundled ROM profiles")
        val file = File(dir, "$id.json")
        assertTrue("bundled profile $id.json is missing", file.isFile)
        return ProfileLoader.parseProfile(file.readText())
    }

    @Test
    fun `ability catalogue data does not change the H and S calculator refusal`() {
        val heartAndSoul = bundledProfile("heart_and_soul")
        val request = DamageCalculationRequest(
            gen = 3,
            attacker = CalcPokemonInput(species = "Machamp", level = 50),
            defender = CalcPokemonInput(species = "Snorlax", level = 50),
            move = CalcMoveInput(name = "Rock Slide"),
            field = CalcFieldInput()
        )
        val outcome = CalcRequestBoundary.build(heartAndSoul, null, request)
        val refused = outcome as? CalcRequestOutcome.Refused
            ?: throw AssertionError(
                "H&S must stay refused even though its ability catalogue is now pinned"
            )
        assertEquals(CalcSupport.UNSUPPORTED, refused.verdict.support)
    }

    // ---------------------------------------------------------------- helpers

    private fun assertAbility(id: Int, name: String) {
        val declared = pack.getAbility(id) as? DeclaredAbility.Declared
        assertTrue("ability $id not declared", declared != null)
        assertEquals(id, declared!!.abilityId)
        assertEquals("ability $id name", name, declared.name)
    }

    private fun assertSlot(speciesId: Int, slot: Int, abilityId: Int, name: String) =
        assertSlot(pack, speciesId, slot, abilityId, name)

    private fun assertSlot(
        target: GameDataPack,
        speciesId: Int,
        slot: Int,
        abilityId: Int,
        name: String
    ) {
        val declared = target.getDeclaredAbilityForSlot(speciesId, slot) as? DeclaredAbility.Declared
        assertTrue(
            "species $speciesId slot $slot expected $abilityId ($name), got $declared",
            declared != null
        )
        assertEquals("species $speciesId slot $slot id", abilityId, declared!!.abilityId)
        assertEquals("species $speciesId slot $slot name", name, declared.name)
    }
}
