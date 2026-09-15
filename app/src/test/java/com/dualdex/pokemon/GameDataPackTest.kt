package com.dualdex.pokemon

import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.romhack.ProfileLoader
import com.dualdex.romhack.RomHackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDataPackTest {

    private val ghostGreyJson = """
    {
      "id": "ghost_grey",
      "name": "Pokemon Ghost Grey",
      "baseGame": "FireRed",
      "gameId": 6,
      "developer": "JoeyZeed",
      "engine": "HexManiacAdvance",
      "hasEvs": false,
      "hasIvs": false,
      "hasPhysSpecSplit": true,
      "steelResistsGhostDark": true,
      "cfruOffsets": false,
      "playerPartyOffset": 33702532,
      "enemyPartyOffset": 33701932,
      "headerTitles": ["BPRE", "GHOSTGREY"],
      "sha256Hashes": [],
      "isVerified": true,
      "memoryLayoutVerified": true,
      "battleUiVerified": false,
      "interactiveControlsVerified": false,
      "gameDataPackId": "modern",
      "customSpecies": {
        "500": { "name": "Lichtoise", "type1": "Water", "type2": "Ghost", "hp": 79, "atk": 63, "def": 100, "spa": 85, "spd": 105, "spe": 78 },
        "501": { "name": "Spectrasaur", "type1": "Grass", "type2": "Ghost", "hp": 80, "atk": 82, "def": 83, "spa": 100, "spd": 100, "spe": 80 },
        "502": { "name": "Phantomander", "type1": "Fire", "type2": "Ghost", "hp": 78, "atk": 84, "def": 78, "spa": 109, "spd": 85, "spe": 100 }
      }
    }
    """.trimIndent()

    private val heartAndSoulJson = """
    {
      "id": "heart_and_soul",
      "name": "Pokemon Heart & Soul",
      "baseGame": "Emerald",
      "gameId": 8,
      "developer": "Lil Dill / PokemonHnS-Development",
      "engine": "pokeemerald-expansion",
      "hasEvs": true,
      "hasIvs": true,
      "hasPhysSpecSplit": true,
      "steelResistsGhostDark": false,
      "cfruOffsets": false,
      "playerPartyOffset": 33769320,
      "enemyPartyOffset": 33768120,
      "headerTitles": ["HEARTSOUL", "HNS"],
      "sha256Hashes": [],
      "isVerified": true,
      "memoryLayoutVerified": true,
      "battleUiVerified": false,
      "interactiveControlsVerified": false,
      "gameDataPackId": "hns_2_0_5"
    }
    """.trimIndent()

    @Test
    fun testPackRegistryResolution() {
        val hnsProfile = ProfileLoader.parseProfile(heartAndSoulJson)
        val hnsPack = GameDataPackRegistry.getForProfile(hnsProfile)
        assertEquals(HeartAndSoul205DataPack.id, hnsPack.id)
        assertTrue(hnsPack is HeartAndSoul205DataPack)

        val frProfile = RomHackProfile.DEFAULT_FIRERED
        val frPack = GameDataPackRegistry.getForProfile(frProfile)
        assertEquals(Gen3VanillaDataPack.id, frPack.id)

        val ggProfile = ProfileLoader.parseProfile(ghostGreyJson)
        val ggPack = GameDataPackRegistry.getForProfile(ggProfile)
        assertTrue(ggPack is ProfileOverlayDataPack)
        assertEquals("modern_overlay", ggPack.id)
    }

    @Test
    fun testGhostGreyVsHeartAndSoulCollision() {
        val hnsProfile = ProfileLoader.parseProfile(heartAndSoulJson)
        val hnsPack = GameDataPackRegistry.getForProfile(hnsProfile)

        val ggProfile = ProfileLoader.parseProfile(ghostGreyJson)
        val ggPack = GameDataPackRegistry.getForProfile(ggProfile)

        // 1. Under H&S 2.0.5, ID 500 is canonical Emboar
        val hns500 = hnsPack.getSpecies(500)
        assertNotNull(hns500)
        assertEquals("Emboar", hns500!!.name)
        assertEquals(PokemonType.FIRE, hns500.type1)
        assertEquals(PokemonType.FIGHTING, hns500.type2)
        assertEquals(110, hns500.baseHP)
        assertEquals(123, hns500.baseAtk)

        // 2. Under Ghost Grey, ID 500 is Lichtoise
        val gg500 = ggPack.getSpecies(500)
        assertNotNull(gg500)
        assertEquals("Lichtoise", gg500!!.name)
        assertEquals(PokemonType.WATER, gg500.type1)
        assertEquals(PokemonType.GHOST, gg500.type2)
        assertEquals(79, gg500.baseHP)
        assertEquals(63, gg500.baseAtk)

        // 3. Under global SpeciesDatabase, ID 500 is canonical Emboar (no pollution!)
        val global500 = SpeciesDatabase.get(500)
        assertEquals("Emboar", global500.name)
        assertEquals(PokemonType.FIRE, global500.type1)
        assertEquals(PokemonType.FIGHTING, global500.type2)

        // ID 501: Oshawott vs Spectrasaur
        assertEquals("Oshawott", hnsPack.getSpecies(501)?.name)
        assertEquals("Spectrasaur", ggPack.getSpecies(501)?.name)
        assertEquals("Oshawott", SpeciesDatabase.get(501).name)

        // ID 502: Dewott vs Phantomander
        assertEquals("Dewott", hnsPack.getSpecies(502)?.name)
        assertEquals("Phantomander", ggPack.getSpecies(502)?.name)
        assertEquals("Dewott", SpeciesDatabase.get(502).name)
    }

    @Test
    fun testCrossProfilePurity() {
        val hnsProfile = ProfileLoader.parseProfile(heartAndSoulJson)
        val hnsPack = GameDataPackRegistry.getForProfile(hnsProfile)

        val ggProfile = ProfileLoader.parseProfile(ghostGreyJson)
        val ggPack = GameDataPackRegistry.getForProfile(ggProfile)

        // Query sequentially multiple times in interleaved order
        assertEquals("Emboar", hnsPack.getSpecies(500)?.name)
        assertEquals("Lichtoise", ggPack.getSpecies(500)?.name)
        assertEquals("Emboar", hnsPack.getSpecies(500)?.name)
        assertEquals("Lichtoise", ggPack.getSpecies(500)?.name)
        assertEquals("Emboar", SpeciesDatabase.get(500).name)
    }

    @Test
    fun testHeartAndSoulAuthorityAndSafeDegradation() {
        // Authoritative known species
        assertTrue(HeartAndSoul205DataPack.isSpeciesAuthoritative(1))
        assertTrue(HeartAndSoul205DataPack.isSpeciesAuthoritative(500))
        assertTrue(HeartAndSoul205DataPack.isSpeciesAuthoritative(1000))

        val samurottH = HeartAndSoul205DataPack.getSpecies(1000)
        assertNotNull(samurottH)
        assertEquals("Samurott-H", samurottH!!.name)
        assertEquals(PokemonType.WATER, samurottH.type1)
        assertEquals(PokemonType.DARK, samurottH.type2)

        // Non-existent species degrades safely without throwing
        assertFalse(HeartAndSoul205DataPack.isSpeciesAuthoritative(9999))
        assertNull(HeartAndSoul205DataPack.getSpecies(9999))

        // Authoritative moves
        assertTrue(HeartAndSoul205DataPack.isMoveAuthoritative(1)) // Pound
        assertTrue(HeartAndSoul205DataPack.isMoveAuthoritative(56)) // Hydro Pump
        assertTrue(HeartAndSoul205DataPack.isMoveAuthoritative(847)) // Malignant Chain

        val hydroPump = HeartAndSoul205DataPack.getMove(56)
        assertNotNull(hydroPump)
        assertEquals("Hydro Pump", hydroPump!!.name)
        assertEquals(PokemonType.WATER, hydroPump.type)
        assertEquals(MoveCategory.SPECIAL, hydroPump.category)
        assertEquals(110, hydroPump.power)
        assertEquals(80, hydroPump.accuracy)
        assertEquals(5, hydroPump.pp)

        val malignantChain = HeartAndSoul205DataPack.getMove(847)
        assertNotNull(malignantChain)
        assertEquals("Malignant Chain", malignantChain!!.name)
        assertEquals(PokemonType.POISON, malignantChain.type)
        assertEquals(MoveCategory.SPECIAL, malignantChain.category)
        assertEquals(100, malignantChain.power)

        // Non-existent move degrades safely
        assertFalse(HeartAndSoul205DataPack.isMoveAuthoritative(9999))
        assertNull(HeartAndSoul205DataPack.getMove(9999))
    }

    @Test
    fun testHeartAndSoulMechanicsAndDefenseProfile() {
        assertEquals(8, HeartAndSoul205DataPack.generation)
        assertTrue(HeartAndSoul205DataPack.hasFairyType)
        assertTrue(HeartAndSoul205DataPack.hasStellarType)
        assertTrue(HeartAndSoul205DataPack.hasPhysicalSpecialSplit)

        // In H&S (Gen 8 mechanics), Steel does not resist Ghost or Dark
        assertEquals(1.0, HeartAndSoul205DataPack.getEffectiveness(PokemonType.GHOST, PokemonType.STEEL), 0.001)
        assertEquals(1.0, HeartAndSoul205DataPack.getEffectiveness(PokemonType.DARK, PokemonType.STEEL), 0.001)

        // In Gen 3 Vanilla, Steel DOES resist Ghost and Dark
        assertEquals(0.5, Gen3VanillaDataPack.getEffectiveness(PokemonType.GHOST, PokemonType.STEEL), 0.001)
        assertEquals(0.5, Gen3VanillaDataPack.getEffectiveness(PokemonType.DARK, PokemonType.STEEL), 0.001)

        // Defense profiles
        val hnsDefense = TypeChart.getDefenseProfile(PokemonType.STEEL, null, pack = HeartAndSoul205DataPack)
        assertFalse(hnsDefense.resistancesHalf.contains(PokemonType.GHOST))
        assertFalse(hnsDefense.resistancesHalf.contains(PokemonType.DARK))

        val gen3Defense = TypeChart.getDefenseProfile(PokemonType.STEEL, null, pack = Gen3VanillaDataPack)
        assertTrue(gen3Defense.resistancesHalf.contains(PokemonType.GHOST))
        assertTrue(gen3Defense.resistancesHalf.contains(PokemonType.DARK))
        val gen3ProfileTypes = (gen3Defense.weaknesses4x + gen3Defense.weaknesses2x +
            gen3Defense.neutral + gen3Defense.resistancesHalf + gen3Defense.resistancesQuarter +
            gen3Defense.immunities).toSet()
        assertFalse(gen3ProfileTypes.contains(PokemonType.FAIRY))
        assertFalse(gen3ProfileTypes.contains(PokemonType.STELLAR))
    }

    @Test
    fun testTeraStarstormAndStellarType() {
        val starstorm = HeartAndSoul205DataPack.getMove(834)
        assertNotNull(starstorm)
        assertEquals("Tera Starstorm", starstorm!!.name)
        // Static gMovesInfo type is NORMAL. Runtime Tera Starstorm semantics are dynamic and intentionally out of scope.
        assertEquals(PokemonType.NORMAL, starstorm.type)
        assertEquals(MoveCategory.SPECIAL, starstorm.category)
        assertEquals(120, starstorm.power)
        assertEquals(100, starstorm.accuracy)
        assertEquals(5, starstorm.pp)

        // Static effectiveness baseline for Stellar: neutral 1.0 against all types,
        // and 1.0 when attacked by all types. (Dynamic Terastallization mechanics belong to #9).
        for (defType in PokemonType.values()) {
            assertEquals(
                "Stellar attacking $defType must have 1.0 static effectiveness",
                1.0f,
                TypeChart.getEffectiveness(PokemonType.STELLAR, defType),
                0.001f
            )
            assertEquals(
                "$defType attacking Stellar must have 1.0 static effectiveness",
                1.0f,
                TypeChart.getEffectiveness(defType, PokemonType.STELLAR),
                0.001f
            )
        }
    }

    @Test
    fun testSourceDerivedMoveFixtures() {
        // 1. Old move: Pound (ID 1)
        val pound = HeartAndSoul205DataPack.getMove(1)
        assertNotNull(pound)
        assertEquals("Pound", pound!!.name)
        assertEquals(PokemonType.NORMAL, pound.type)
        assertEquals(MoveCategory.PHYSICAL, pound.category)
        assertEquals(40, pound.power)
        assertEquals(100, pound.accuracy)
        assertEquals(35, pound.pp)

        // 2. Later-gen move: Malignant Chain (ID 847)
        val malignantChain = HeartAndSoul205DataPack.getMove(847)
        assertNotNull(malignantChain)
        assertEquals("Malignant Chain", malignantChain!!.name)
        assertEquals(PokemonType.POISON, malignantChain.type)
        assertEquals(MoveCategory.SPECIAL, malignantChain.category)
        assertEquals(100, malignantChain.power)
        assertEquals(100, malignantChain.accuracy)
        assertEquals(5, malignantChain.pp)

        // 3. Fairy move: Moonblast (ID 585) & Play Rough (ID 583)
        val moonblast = HeartAndSoul205DataPack.getMove(585)
        assertNotNull(moonblast)
        assertEquals("Moonblast", moonblast!!.name)
        assertEquals(PokemonType.FAIRY, moonblast.type)
        assertEquals(MoveCategory.SPECIAL, moonblast.category)
        assertEquals(95, moonblast.power)

        val playRough = HeartAndSoul205DataPack.getMove(583)
        assertNotNull(playRough)
        assertEquals("Play Rough", playRough!!.name)
        assertEquals(PokemonType.FAIRY, playRough.type)
        assertEquals(MoveCategory.PHYSICAL, playRough.category)
        assertEquals(90, playRough.power)

        // 4. Status move: Swords Dance (ID 14), Toxic (ID 92), Curse (ID 174)
        val swordsDance = HeartAndSoul205DataPack.getMove(14)
        assertNotNull(swordsDance)
        assertEquals("Swords Dance", swordsDance!!.name)
        assertEquals(PokemonType.NORMAL, swordsDance.type)
        assertEquals(MoveCategory.STATUS, swordsDance.category)
        assertEquals(0, swordsDance.power)

        val toxic = HeartAndSoul205DataPack.getMove(92)
        assertNotNull(toxic)
        assertEquals("Toxic", toxic!!.name)
        assertEquals(PokemonType.POISON, toxic.type)
        assertEquals(MoveCategory.STATUS, toxic.category)
        assertEquals(0, toxic.power)
        assertEquals(90, toxic.accuracy)

        val curse = HeartAndSoul205DataPack.getMove(174)
        assertNotNull(curse)
        assertEquals("Curse", curse!!.name)
        assertEquals(PokemonType.GHOST, curse.type)
        assertEquals(MoveCategory.STATUS, curse.category)
        assertEquals(0, curse.power)

        // 5. Static source type for Tera Starstorm (ID 834); runtime Stellar behavior is dynamic.
        val starstorm = HeartAndSoul205DataPack.getMove(834)
        assertNotNull(starstorm)
        assertEquals(PokemonType.NORMAL, starstorm!!.type)

        // 6. High internal move ID: G-Max Rapid Flow (ID 934)
        val gmaxRapidFlow = HeartAndSoul205DataPack.getMove(934)
        assertNotNull(gmaxRapidFlow)
        assertEquals("G-Max Rapid Flow", gmaxRapidFlow!!.name)
        assertEquals(PokemonType.WATER, gmaxRapidFlow.type)
        assertEquals(MoveCategory.PHYSICAL, gmaxRapidFlow.category)
        assertEquals(10, gmaxRapidFlow.power)
    }

    @Test
    fun testExactPackBlocksGlobalFallback() {
        // Heart & Soul 2.0.5 has allowGlobalFallback = false
        assertFalse(HeartAndSoul205DataPack.allowGlobalFallback)

        // Resolving an unknown species ID on H&S returns safe unknown placeholder, never generic modern species
        val unknownSpecies = HeartAndSoul205DataPack.resolveSpecies(9999)
        assertEquals("Unknown Species #9999", unknownSpecies.name)
        assertEquals(PokemonType.NORMAL, unknownSpecies.type1)
        assertNull(unknownSpecies.type2)

        // Resolving an unknown move ID on H&S returns safe unknown placeholder, never generic modern move
        val unknownMove = HeartAndSoul205DataPack.resolveMove(9999)
        assertEquals("Unknown Move #9999", unknownMove.name)
        assertEquals(PokemonType.NORMAL, unknownMove.type)
        assertEquals(MoveCategory.PHYSICAL, unknownMove.category)

        // Verify with a custom exact pack that an ID present in global SpeciesDatabase is NOT resolved
        val syntheticExactPack = object : GameDataPack {
            override val id: String = "synthetic_exact"
            override val generation: Int = 8
            override val hasFairyType: Boolean = true
            override val hasPhysicalSpecialSplit: Boolean = true
            override val allowGlobalFallback: Boolean = false
            override fun getSpecies(id: Int): SpeciesInfo? = null
            override fun getMove(id: Int): MoveInfo? = null
            override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double = 1.0
        }

        // Bulbasaur (ID 1) exists in global SpeciesDatabase, but syntheticExactPack blocks global fallback
        val blockedSpecies = syntheticExactPack.resolveSpecies(1)
        assertEquals("Unknown Species #1", blockedSpecies.name)

        // Pound (ID 1) exists in global MoveDatabase, but syntheticExactPack blocks global fallback
        val blockedMove = syntheticExactPack.resolveMove(1)
        assertEquals("Unknown Move #1", blockedMove.name)

        // Vanilla / Modern packs have allowGlobalFallback = true and resolve successfully
        assertTrue(Gen3VanillaDataPack.allowGlobalFallback)
        assertTrue(ModernDataPack.allowGlobalFallback)

        val fallbackSpecies = Gen3VanillaDataPack.resolveSpecies(1)
        assertEquals("Bulbasaur", fallbackSpecies.name)

        val fallbackMove = Gen3VanillaDataPack.resolveMove(1)
        assertEquals("Pound", fallbackMove.name)
    }

    @Test
    fun testProfileOverlayPreservesFallbackCapability() {
        val hnsOverlay = ProfileOverlayDataPack(HeartAndSoul205DataPack, emptyMap())
        assertFalse(hnsOverlay.allowGlobalFallback)

        val modernOverlay = ProfileOverlayDataPack(ModernDataPack, emptyMap())
        assertTrue(modernOverlay.allowGlobalFallback)

        val vanillaOverlay = ProfileOverlayDataPack(Gen3VanillaDataPack, emptyMap())
        assertTrue(vanillaOverlay.allowGlobalFallback)
    }
}
