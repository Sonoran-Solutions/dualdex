package com.dualdex.pokemon.hns

/**
 * Authoritative source-derived Fairy mode transformations for Heart & Soul 2.0.5.
 *
 * Extracted deterministically from `PokemonHnS-Development/pokehns-expansion`
 * at commit `1f42b74dff0e9fe942419845d040663dd829a973` (Release-v2.0.5).
 *
 * Covers:
 *  - `sPreFairyTypes` (20 species) from `src/pokemon.c:5704`
 *  - `sFairyMoveAltTypes` (34 moves) from `src/pokemon.c:5748`
 *
 * In H&S, when `tx_Mode_Fairy_Types == 0` (Fairy mode disabled):
 *  1. `GetSpeciesType` looks up `sPreFairyTypes` for species types;
 *  2. `GetMoveType` looks up `sFairyMoveAltTypes` for Fairy moves, falling back to Normal.
 */
object HnsFairyTypeMappings {

    const val PINNED_COMMIT: String = "1f42b74dff0e9fe942419845d040663dd829a973"
    const val PINNED_TAG: String = "Release-v2.0.5"

    /**
     * Pinned pre-Fairy species typing (sPreFairyTypes, 20 species).
     * Maps normalized species name to effective pre-Fairy type list.
     */
    val PRE_FAIRY_SPECIES_TYPES: Map<String, List<String>> = mapOf(
        "cleffa" to listOf("Normal"), // Cleffa (SPECIES_CLEFFA)
        "clefairy" to listOf("Normal"), // Clefairy (SPECIES_CLEFAIRY)
        "clefable" to listOf("Normal"), // Clefable (SPECIES_CLEFABLE)
        "igglybuff" to listOf("Normal"), // Igglybuff (SPECIES_IGGLYBUFF)
        "jigglypuff" to listOf("Normal"), // Jigglypuff (SPECIES_JIGGLYPUFF)
        "wigglytuff" to listOf("Normal"), // Wigglytuff (SPECIES_WIGGLYTUFF)
        "togepi" to listOf("Normal"), // Togepi (SPECIES_TOGEPI)
        "togetic" to listOf("Normal", "Flying"), // Togetic (SPECIES_TOGETIC)
        "togekiss" to listOf("Normal", "Flying"), // Togekiss (SPECIES_TOGEKISS)
        "azurill" to listOf("Normal"), // Azurill (SPECIES_AZURILL)
        "marill" to listOf("Water"), // Marill (SPECIES_MARILL)
        "azumarill" to listOf("Water"), // Azumarill (SPECIES_AZUMARILL)
        "snubbull" to listOf("Normal"), // Snubbull (SPECIES_SNUBBULL)
        "granbull" to listOf("Normal"), // Granbull (SPECIES_GRANBULL)
        "ralts" to listOf("Psychic"), // Ralts (SPECIES_RALTS)
        "kirlia" to listOf("Psychic"), // Kirlia (SPECIES_KIRLIA)
        "gardevoir" to listOf("Psychic"), // Gardevoir (SPECIES_GARDEVOIR)
        "mime jr." to listOf("Psychic"), // Mime Jr. (SPECIES_MIME_JR)
        "mr. mime" to listOf("Psychic"), // Mr. Mime (SPECIES_MR_MIME)
        "mawile" to listOf("Steel"), // Mawile (SPECIES_MAWILE)
    )

    /**
     * Pinned alternate move typing for Fairy moves when Fairy is disabled (sFairyMoveAltTypes, 34 moves).
     * Maps normalized move name to effective alternate type name.
     */
    val FAIRY_MOVE_ALT_TYPES: Map<String, String> = mapOf(
        "fairy wind" to "Normal", // Fairy Wind (MOVE_FAIRY_WIND)
        "disarming voice" to "Normal", // Disarming Voice (MOVE_DISARMING_VOICE)
        "dazzling gleam" to "Normal", // Dazzling Gleam (MOVE_DAZZLING_GLEAM)
        "moonblast" to "Dark", // Moonblast (MOVE_MOONBLAST)
        "draining kiss" to "Normal", // Draining Kiss (MOVE_DRAINING_KISS)
        "play rough" to "Normal", // Play Rough (MOVE_PLAY_ROUGH)
        "baby-doll eyes" to "Normal", // Baby-Doll Eyes (MOVE_BABY_DOLL_EYES)
        "aromatic mist" to "Normal", // Aromatic Mist (MOVE_AROMATIC_MIST)
        "crafty shield" to "Normal", // Crafty Shield (MOVE_CRAFTY_SHIELD)
        "fairy lock" to "Normal", // Fairy Lock (MOVE_FAIRY_LOCK)
        "flower shield" to "Grass", // Flower Shield (MOVE_FLOWER_SHIELD)
        "floral healing" to "Grass", // Floral Healing (MOVE_FLORAL_HEALING)
        "misty terrain" to "Normal", // Misty Terrain (MOVE_MISTY_TERRAIN)
        "misty explosion" to "Normal", // Misty Explosion (MOVE_MISTY_EXPLOSION)
        "geomancy" to "Psychic", // Geomancy (MOVE_GEOMANCY)
        "light of ruin" to "Normal", // Light Of Ruin (MOVE_LIGHT_OF_RUIN)
        "fleur cannon" to "Grass", // Fleur Cannon (MOVE_FLEUR_CANNON)
        "spirit break" to "Fighting", // Spirit Break (MOVE_SPIRIT_BREAK)
        "nature's madness" to "Normal", // Nature's Madness (MOVE_NATURES_MADNESS)
        "decorate" to "Normal", // Decorate (MOVE_DECORATE)
        "strange steam" to "Normal", // Strange Steam (MOVE_STRANGE_STEAM)
        "sparkly swirl" to "Normal", // Sparkly Swirl (MOVE_SPARKLY_SWIRL)
        "alluring voice" to "Normal", // Alluring Voice (MOVE_ALLURING_VOICE)
        "springtide storm" to "Flying", // Springtide Storm (MOVE_SPRINGTIDE_STORM)
        "guardian of alola" to "Normal", // Guardian Of Alola (MOVE_GUARDIAN_OF_ALOLA)
        "let's snuggle forever" to "Normal", // Let's Snuggle Forever (MOVE_LETS_SNUGGLE_FOREVER)
        "twinkle tackle" to "Normal", // Twinkle Tackle (MOVE_TWINKLE_TACKLE)
        "max starfall" to "Normal", // Max Starfall (MOVE_MAX_STARFALL)
        "g-max finale" to "Normal", // G-Max Finale (MOVE_G_MAX_FINALE)
        "g-max smite" to "Normal", // G-Max Smite (MOVE_G_MAX_SMITE)
        "magical torque" to "Psychic", // Magical Torque (MOVE_MAGICAL_TORQUE)
        "sweet kiss" to "Normal", // Sweet Kiss (MOVE_SWEET_KISS)
        "charm" to "Normal", // Charm (MOVE_CHARM)
        "moonlight" to "Dark", // Moonlight (MOVE_MOONLIGHT)
    )

    /**
     * Returns the pre-Fairy types for [speciesName] if [speciesName] is retyped when Fairy is OFF,
     * or null if this species does not have a pre-Fairy retyping.
     */
    fun getPreFairyTypes(speciesName: String): List<String>? =
        PRE_FAIRY_SPECIES_TYPES[speciesName.trim().lowercase()]

    /**
     * Returns the alternate type for [moveName] when Fairy is OFF.
     * In H&S line 5795: unlisted Fairy moves fall back to "Normal".
     * If [moveIsFairy] is true, returns the mapped alternate type, or "Normal" if unlisted.
     * If [moveIsFairy] is false, returns null.
     */
    fun getFairyMoveAltType(moveName: String, moveIsFairy: Boolean): String? {
        if (!moveIsFairy) return null
        return FAIRY_MOVE_ALT_TYPES[moveName.trim().lowercase()] ?: "Normal"
    }
}
