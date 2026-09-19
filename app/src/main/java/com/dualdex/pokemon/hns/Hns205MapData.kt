package com.dualdex.pokemon.hns

import com.dualdex.pokemon.MapNodeType
import com.dualdex.pokemon.RegionId

/**
 * Exact, version-pinned map identity table for Pokemon Heart & Soul 2.0.5.
 *
 * Provenance:
 *   Repository: PokemonHnS-Development/pokehns-expansion
 *   Tag: Release-v2.0.5
 *   Commit: 1f42b74dff0e9fe942419845d040663dd829a973
 *   Extraction: data/maps/map_groups.json, data/maps/<map>/map.json,
 *               src/data/region_map/region_map_entries.h (IS_HNS table)
 *   ROM dependency: NONE
 *
 * [locationGroups] maps the raw SaveBlock1 mapGroup/mapNum pair the running game
 * stores onto the upstream map identity. Group index is the position in upstream
 * `group_order`; map number is the position inside that group. Both are build
 * configuration, so they are pinned rather than inferred.
 *
 * H&S locations: 560   H&S map sections: 120
 *
 * DO NOT EDIT DIRECTLY. Regenerate using:
 *   python3 tools/hns-map-data/generate_hns_map_data.py
 */
object Hns205MapData {
    const val UPSTREAM_COMMIT_SHA: String = "1f42b74dff0e9fe942419845d040663dd829a973"
    const val UPSTREAM_TAG: String = "Release-v2.0.5"

    /** A single H&S map identified by its raw mapGroup/mapNum pair. */
    data class HnsMapLocation(
        val mapName: String,
        val sectionId: String,
        val region: RegionId?,
    )

    /**
     * A H&S region-map section.
     *
     * [presentable] is false when no H&S region-map view draws the section.
     * Such a section keeps its region identity but has no canvas anchor
     * ([gridX] and [gridY] are -1), so a caller must never place a player
     * marker for it.
     */
    data class HnsMapSection(
        val sectionId: String,
        val displayName: String,
        val region: RegionId?,
        val nodeType: MapNodeType,
        val presentable: Boolean,
        val gridX: Int,
        val gridY: Int,
        val width: Int,
        val height: Int,
    )

    /** Highest mapGroup index that carries a H&S location. */
    const val MAX_LOCATION_GROUP: Int = 56

    val sections: List<HnsMapSection> = buildList {
        addSectionChunk1()
        addSectionChunk2()
        addSectionChunk3()
        addSectionChunk4()
        addSectionChunk5()
        addSectionChunk6()
        addSectionChunk7()
        addSectionChunk8()
        addSectionChunk9()
        addSectionChunk10()
        addSectionChunk11()
        addSectionChunk12()
        addSectionChunk13()
        addSectionChunk14()
        addSectionChunk15()
    }

    private fun MutableList<HnsMapSection>.addSectionChunk1() {
        add(HnsMapSection("MAPSEC_ABANDONED_SHIP", "Abandoned Ship", RegionId.JOHTO, MapNodeType.LANDMARK, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_AKALA_CAVE", "Akala Cave", RegionId.ALOLA, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_AKALA_FOREST", "Akala Forest", RegionId.ALOLA, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_AKALA_ISLAND", "Akala Isle", RegionId.ALOLA, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_ALOLA_OCEAN", "Melemele Sea", RegionId.ALOLA, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_AZALEA_TOWN", "Azalea Town", RegionId.JOHTO, MapNodeType.TOWN, true, 10, 13, 1, 1))
        add(HnsMapSection("MAPSEC_BATTLE_FRONTIER", "Battle Frontier", null, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_BIRTH_ISLAND", "Birth Island", null, MapNodeType.ROUTE, false, -1, -1, 0, 0))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk2() {
        add(HnsMapSection("MAPSEC_BLACKTHORN_CITY", "Blackthorn City", RegionId.JOHTO, MapNodeType.CITY, true, 18, 2, 1, 1))
        add(HnsMapSection("MAPSEC_BURNED_TOWER", "Burned Tower", RegionId.JOHTO, MapNodeType.DUNGEON, true, 9, 1, 1, 1))
        add(HnsMapSection("MAPSEC_CELADON_CITY", "Celadon City", RegionId.KANTO, MapNodeType.ROUTE, true, 11, 6, 1, 1))
        add(HnsMapSection("MAPSEC_CERULEAN_CAVE", "Cerulean Cave", RegionId.KANTO, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_CERULEAN_CITY", "Cerulean City", RegionId.KANTO, MapNodeType.CITY, true, 14, 3, 1, 1))
        add(HnsMapSection("MAPSEC_CHERRYGROVE_CITY", "Cherrygrove City", RegionId.JOHTO, MapNodeType.CITY, true, 14, 10, 1, 1))
        add(HnsMapSection("MAPSEC_CIANWOOD_CITY", "Cianwood City", RegionId.JOHTO, MapNodeType.CITY, true, 4, 11, 1, 1))
        add(HnsMapSection("MAPSEC_CINNABAR_ISLAND", "Cinnabar Island", RegionId.KANTO, MapNodeType.ROUTE, true, 4, 14, 1, 1))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk3() {
        add(HnsMapSection("MAPSEC_CLIFF_CAVE", "Cliff Cave", RegionId.JOHTO, MapNodeType.DUNGEON, true, 3, 11, 1, 1))
        add(HnsMapSection("MAPSEC_DARK_CAVE", "Dark Cave", RegionId.JOHTO, MapNodeType.DUNGEON, true, 16, 4, 3, 2))
        add(HnsMapSection("MAPSEC_DIGLETTS_CAVE", "Diglett's Cave", RegionId.KANTO, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_DRAGONS_DEN", "Dragon's Den", RegionId.JOHTO, MapNodeType.DUNGEON, true, 18, 1, 1, 1))
        add(HnsMapSection("MAPSEC_DYNAMIC", "Dynamic", null, MapNodeType.FACILITY, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_ECRUTEAK_CITY", "Ecruteak City", RegionId.JOHTO, MapNodeType.ROUTE, true, 10, 2, 1, 1))
        add(HnsMapSection("MAPSEC_EMBEDDED_TOWER", "Embedded Tower", RegionId.JOHTO, MapNodeType.DUNGEON, true, 1, 10, 1, 2))
        add(HnsMapSection("MAPSEC_FARAWAY_ISLAND", "Faraway Island", null, MapNodeType.FACILITY, false, -1, -1, 0, 0))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk4() {
        add(HnsMapSection("MAPSEC_FUCHSIA_CITY", "Fuchsia City", RegionId.KANTO, MapNodeType.DUNGEON, true, 12, 12, 1, 1))
        add(HnsMapSection("MAPSEC_GOLDENROD_CITY", "Goldenrod City", RegionId.JOHTO, MapNodeType.CITY, true, 8, 8, 1, 2))
        add(HnsMapSection("MAPSEC_ICE_PATH", "Ice Path", RegionId.JOHTO, MapNodeType.DUNGEON, true, 17, 1, 1, 1))
        add(HnsMapSection("MAPSEC_ILEX_FOREST", "Ilex Forest", RegionId.JOHTO, MapNodeType.ROUTE, true, 8, 12, 2, 2))
        add(HnsMapSection("MAPSEC_INDIGO_PLATEAU", "Indigo Plateau", RegionId.JOHTO, MapNodeType.TOWN, true, 24, 3, 1, 2))
        add(HnsMapSection("MAPSEC_LAKE_OF_RAGE", "Lake Of Rage", RegionId.JOHTO, MapNodeType.ROUTE, true, 15, 0, 1, 1))
        add(HnsMapSection("MAPSEC_LAVENDER_TOWN", "Lavender Town", RegionId.KANTO, MapNodeType.TOWN, true, 18, 6, 1, 1))
        add(HnsMapSection("MAPSEC_MAHOGANY_TOWN", "Mahogany Town", RegionId.JOHTO, MapNodeType.TOWN, true, 15, 2, 1, 1))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk5() {
        add(HnsMapSection("MAPSEC_MELEMELE_ISLAND", "Melemele Isle", RegionId.ALOLA, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_MT_MOON", "Mt. Moon", RegionId.KANTO, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_MT_MORTAR", "Mt. Mortar", RegionId.JOHTO, MapNodeType.DUNGEON, true, 12, 1, 1, 1))
        add(HnsMapSection("MAPSEC_MT_SILVER", "Mt. Silver", RegionId.JOHTO, MapNodeType.DUNGEON, true, 20, 5, 1, 1))
        add(HnsMapSection("MAPSEC_NATIONAL_PARK", "National Park", RegionId.JOHTO, MapNodeType.ROUTE, true, 8, 4, 1, 1))
        add(HnsMapSection("MAPSEC_NEW_BARK_TOWN", "New Bark Town", RegionId.JOHTO, MapNodeType.TOWN, true, 19, 10, 1, 1))
        add(HnsMapSection("MAPSEC_NEW_SINJOH", "New Sinjoh", RegionId.SINJOH, MapNodeType.TOWN, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_OLIVINE_CITY", "Olivine City", RegionId.JOHTO, MapNodeType.ROUTE, true, 6, 4, 1, 1))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk6() {
        add(HnsMapSection("MAPSEC_OLIVINE_LIGHTHOUSE", "Olivine Lighthouse", RegionId.JOHTO, MapNodeType.FACILITY, true, 7, 4, 1, 1))
        add(HnsMapSection("MAPSEC_PALLET_TOWN", "Pallet Town", RegionId.KANTO, MapNodeType.TOWN, true, 4, 11, 1, 1))
        add(HnsMapSection("MAPSEC_PEWTER_CITY", "Pewter City", RegionId.KANTO, MapNodeType.CITY, true, 4, 4, 1, 1))
        add(HnsMapSection("MAPSEC_PONI_CAVE", "Poni Cave", RegionId.ALOLA, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_PONI_ISLAND", "Poni Isle", RegionId.ALOLA, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_POWER_PLANT", "Power Plant", RegionId.KANTO, MapNodeType.FACILITY, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_ROCKET_HIDEOUT_HNS", "Rocket Hideout", RegionId.JOHTO, MapNodeType.FACILITY, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_ROCK_TUNNEL", "Rock Tunnel", RegionId.KANTO, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk7() {
        add(HnsMapSection("MAPSEC_ROUTE_1", "Route 1", RegionId.KANTO, MapNodeType.ROUTE, true, 4, 9, 1, 2))
        add(HnsMapSection("MAPSEC_ROUTE_10", "Route 10", RegionId.KANTO, MapNodeType.ROUTE, true, 18, 4, 1, 2))
        add(HnsMapSection("MAPSEC_ROUTE_11", "Route 11", RegionId.KANTO, MapNodeType.ROUTE, true, 16, 9, 3, 1))
        add(HnsMapSection("MAPSEC_ROUTE_12", "Route 12", RegionId.KANTO, MapNodeType.ROUTE, true, 18, 9, 1, 5))
        add(HnsMapSection("MAPSEC_ROUTE_13", "Route 13", RegionId.KANTO, MapNodeType.ROUTE, true, 16, 11, 2, 1))
        add(HnsMapSection("MAPSEC_ROUTE_14", "Route 14", RegionId.KANTO, MapNodeType.ROUTE, true, 15, 11, 1, 2))
        add(HnsMapSection("MAPSEC_ROUTE_15", "Route 15", RegionId.KANTO, MapNodeType.ROUTE, true, 13, 12, 2, 1))
        add(HnsMapSection("MAPSEC_ROUTE_16", "Route 16", RegionId.KANTO, MapNodeType.ROUTE, true, 8, 6, 4, 1))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk8() {
        add(HnsMapSection("MAPSEC_ROUTE_17", "Route 17", RegionId.KANTO, MapNodeType.ROUTE, true, 7, 9, 1, 5))
        add(HnsMapSection("MAPSEC_ROUTE_18", "Route 18", RegionId.KANTO, MapNodeType.ROUTE, true, 9, 12, 5, 1))
        add(HnsMapSection("MAPSEC_ROUTE_19", "Route 19", RegionId.KANTO, MapNodeType.DUNGEON, true, 12, 13, 1, 2))
        add(HnsMapSection("MAPSEC_ROUTE_2", "Route 2", RegionId.KANTO, MapNodeType.ROUTE, true, 4, 6, 1, 3))
        add(HnsMapSection("MAPSEC_ROUTE_20", "Route 20", RegionId.KANTO, MapNodeType.ROUTE, true, 8, 14, 7, 1))
        add(HnsMapSection("MAPSEC_ROUTE_21", "Route 21", RegionId.KANTO, MapNodeType.ROUTE, true, 4, 12, 1, 2))
        add(HnsMapSection("MAPSEC_ROUTE_22", "Route 22", RegionId.KANTO, MapNodeType.ROUTE, true, 2, 8, 2, 1))
        add(HnsMapSection("MAPSEC_ROUTE_24", "Route 24", RegionId.KANTO, MapNodeType.ROUTE, true, 14, 1, 1, 2))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk9() {
        add(HnsMapSection("MAPSEC_ROUTE_25", "Route 25", RegionId.KANTO, MapNodeType.ROUTE, true, 15, 1, 2, 1))
        add(HnsMapSection("MAPSEC_ROUTE_26", "Route 26", RegionId.JOHTO, MapNodeType.ROUTE, true, 24, 8, 1, 5))
        add(HnsMapSection("MAPSEC_ROUTE_27", "Route 27", RegionId.JOHTO, MapNodeType.ROUTE, true, 21, 10, 4, 1))
        add(HnsMapSection("MAPSEC_ROUTE_28", "Route 28", RegionId.JOHTO, MapNodeType.ROUTE, true, 22, 5, 3, 1))
        add(HnsMapSection("MAPSEC_ROUTE_29", "Route 29", RegionId.JOHTO, MapNodeType.ROUTE, true, 16, 10, 4, 1))
        add(HnsMapSection("MAPSEC_ROUTE_3", "Route 3", RegionId.KANTO, MapNodeType.ROUTE, true, 6, 4, 4, 1))
        add(HnsMapSection("MAPSEC_ROUTE_30", "Route 30", RegionId.JOHTO, MapNodeType.ROUTE, true, 14, 7, 1, 5))
        add(HnsMapSection("MAPSEC_ROUTE_31", "Route 31", RegionId.JOHTO, MapNodeType.ROUTE, true, 13, 4, 2, 1))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk10() {
        add(HnsMapSection("MAPSEC_ROUTE_32", "Route 32", RegionId.JOHTO, MapNodeType.ROUTE, true, 12, 7, 1, 6))
        add(HnsMapSection("MAPSEC_ROUTE_33", "Route 33", RegionId.JOHTO, MapNodeType.ROUTE, true, 11, 13, 2, 1))
        add(HnsMapSection("MAPSEC_ROUTE_34", "Route 34", RegionId.JOHTO, MapNodeType.ROUTE, true, 8, 10, 1, 2))
        add(HnsMapSection("MAPSEC_ROUTE_35", "Route 35", RegionId.JOHTO, MapNodeType.ROUTE, true, 8, 6, 1, 3))
        add(HnsMapSection("MAPSEC_ROUTE_36", "Route 36", RegionId.JOHTO, MapNodeType.ROUTE, true, 10, 4, 3, 1))
        add(HnsMapSection("MAPSEC_ROUTE_37", "Route 37", RegionId.JOHTO, MapNodeType.ROUTE, true, 10, 3, 1, 1))
        add(HnsMapSection("MAPSEC_ROUTE_38", "Route 38", RegionId.JOHTO, MapNodeType.ROUTE, true, 8, 2, 3, 1))
        add(HnsMapSection("MAPSEC_ROUTE_39", "Route 39", RegionId.JOHTO, MapNodeType.ROUTE, true, 6, 2, 1, 2))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk11() {
        add(HnsMapSection("MAPSEC_ROUTE_4", "Route 4", RegionId.KANTO, MapNodeType.ROUTE, true, 11, 3, 5, 1))
        add(HnsMapSection("MAPSEC_ROUTE_40", "Route 40", RegionId.JOHTO, MapNodeType.ROUTE, true, 5, 6, 1, 5))
        add(HnsMapSection("MAPSEC_ROUTE_41", "Route 41", RegionId.JOHTO, MapNodeType.ROUTE, true, 5, 10, 1, 3))
        add(HnsMapSection("MAPSEC_ROUTE_42", "Route 42", RegionId.JOHTO, MapNodeType.ROUTE, true, 12, 2, 4, 1))
        add(HnsMapSection("MAPSEC_ROUTE_43", "Route 43", RegionId.JOHTO, MapNodeType.ROUTE, true, 15, 1, 1, 1))
        add(HnsMapSection("MAPSEC_ROUTE_44", "Route 44", RegionId.JOHTO, MapNodeType.ROUTE, true, 16, 2, 2, 1))
        add(HnsMapSection("MAPSEC_ROUTE_45", "Route 45", RegionId.JOHTO, MapNodeType.ROUTE, true, 18, 4, 1, 4))
        add(HnsMapSection("MAPSEC_ROUTE_46", "Route 46", RegionId.JOHTO, MapNodeType.ROUTE, true, 17, 7, 1, 4))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk12() {
        add(HnsMapSection("MAPSEC_ROUTE_47", "Route 47", RegionId.JOHTO, MapNodeType.ROUTE, true, 2, 11, 1, 1))
        add(HnsMapSection("MAPSEC_ROUTE_48", "Route 48", RegionId.JOHTO, MapNodeType.ROUTE, true, 2, 10, 1, 1))
        add(HnsMapSection("MAPSEC_ROUTE_49", "Route 49", RegionId.SINJOH, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_ROUTE_5", "Route 5", RegionId.KANTO, MapNodeType.ROUTE, true, 14, 4, 1, 2))
        add(HnsMapSection("MAPSEC_ROUTE_50", "Route 50", RegionId.SINJOH, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_ROUTE_6", "Route 6", RegionId.KANTO, MapNodeType.ROUTE, true, 14, 7, 1, 2))
        add(HnsMapSection("MAPSEC_ROUTE_7", "Route 7", RegionId.KANTO, MapNodeType.ROUTE, true, 12, 6, 2, 1))
        add(HnsMapSection("MAPSEC_ROUTE_8", "Route 8", RegionId.KANTO, MapNodeType.ROUTE, true, 16, 6, 3, 1))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk13() {
        add(HnsMapSection("MAPSEC_ROUTE_9", "Route 9", RegionId.KANTO, MapNodeType.ROUTE, true, 16, 3, 3, 1))
        add(HnsMapSection("MAPSEC_RUINS_OF_ALPH", "Ruins Of Alph", RegionId.JOHTO, MapNodeType.DUNGEON, true, 10, 5, 2, 2))
        add(HnsMapSection("MAPSEC_SAFARI_ZONE_GATE", "Safari Zone Gate", RegionId.JOHTO, MapNodeType.TOWN, true, 2, 9, 1, 1))
        add(HnsMapSection("MAPSEC_SAFFRON_CITY", "Saffron City", RegionId.KANTO, MapNodeType.CITY, true, 14, 6, 1, 1))
        add(HnsMapSection("MAPSEC_SEAFOAM_ISLANDS", "Seafoam Islands", RegionId.KANTO, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_SINJOH_RUINS", "Sinjoh Ruins", RegionId.SINJOH, MapNodeType.TOWN, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_SLOWPOKE_WELL", "Slowpoke Well", RegionId.JOHTO, MapNodeType.DUNGEON, true, 10, 12, 1, 1))
        add(HnsMapSection("MAPSEC_SNOWSWEPT_CAVERN", "Snowswept Cavern", RegionId.SINJOH, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk14() {
        add(HnsMapSection("MAPSEC_SOUTHERN_ISLAND", "Southern Island", null, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_SPROUT_TOWER", "Sprout Tower", RegionId.JOHTO, MapNodeType.FACILITY, true, 12, 3, 1, 1))
        add(HnsMapSection("MAPSEC_SS_AQUA", "S.s. Aqua", RegionId.JOHTO, MapNodeType.FACILITY, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_TIN_TOWER", "Tin Tower", RegionId.JOHTO, MapNodeType.DUNGEON, true, 10, 1, 1, 1))
        add(HnsMapSection("MAPSEC_TOHJO_FALLS", "Tohjo Falls", RegionId.JOHTO, MapNodeType.DUNGEON, true, 20, 9, 1, 1))
        add(HnsMapSection("MAPSEC_TRAINER_HILL", "Trainer Hill", RegionId.JOHTO, MapNodeType.ROUTE, true, 5, 3, 1, 1))
        add(HnsMapSection("MAPSEC_ULAULA_CAVE", "Mt. Lanikala", RegionId.ALOLA, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_ULAULA_CAVE_2", "Ula'ula Cave", RegionId.ALOLA, MapNodeType.DUNGEON, false, -1, -1, 0, 0))
    }

    private fun MutableList<HnsMapSection>.addSectionChunk15() {
        add(HnsMapSection("MAPSEC_ULAULA_ISLAND", "Ula'ula Isle", RegionId.ALOLA, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_UNION_CAVE", "Union Cave", RegionId.JOHTO, MapNodeType.DUNGEON, true, 12, 11, 1, 2))
        add(HnsMapSection("MAPSEC_VERMILION_CITY", "Vermilion City", RegionId.KANTO, MapNodeType.ROUTE, true, 14, 9, 1, 1))
        add(HnsMapSection("MAPSEC_VICTORY_ROAD_HNS", "Victory Road", RegionId.JOHTO, MapNodeType.DUNGEON, true, 24, 5, 1, 1))
        add(HnsMapSection("MAPSEC_VIOLET_CITY", "Violet City", RegionId.JOHTO, MapNodeType.CITY, true, 12, 4, 1, 1))
        add(HnsMapSection("MAPSEC_VIRIDIAN_CITY", "Viridian City", RegionId.KANTO, MapNodeType.LANDMARK, true, 4, 8, 1, 1))
        add(HnsMapSection("MAPSEC_VIRIDIAN_FOREST", "Viridian Forest", RegionId.KANTO, MapNodeType.ROUTE, false, -1, -1, 0, 0))
        add(HnsMapSection("MAPSEC_WHIRL_ISLANDS", "Whirl Islands", RegionId.JOHTO, MapNodeType.DUNGEON, true, 6, 11, 1, 1))
    }

    /** Section lookup by upstream section id. */
    val sectionsById: Map<String, HnsMapSection> = sections.associateBy { it.sectionId }

    /** mapGroup -> (mapNum -> location). A gap is an explicit non-H&S map number. */
    val locationGroups: Map<Int, List<HnsMapLocation?>> = buildMap {
        put(0, group0())
        put(1, group1())
        put(2, group2())
        put(3, group3())
        put(4, group4())
        put(5, group5())
        put(6, group6())
        put(7, group7())
        put(8, group8())
        put(9, group9())
        put(10, group10())
        put(11, group11())
        put(12, group12())
        put(13, group13())
        put(14, group14())
        put(15, group15())
        put(16, group16())
        put(17, group17())
        put(18, group18())
        put(19, group19())
        put(20, group20())
        put(21, group21())
        put(22, group22())
        put(23, group23())
        put(24, group24())
        put(25, group25())
        put(26, group26())
        put(27, group27())
        put(28, group28())
        put(29, group29())
        put(30, group30())
        put(56, group56())
    }

    private fun group0(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("NewBarkTown_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("CherrygroveCity_hns", "MAPSEC_CHERRYGROVE_CITY", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("VioletCity_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("AzaleaTown_hns", "MAPSEC_AZALEA_TOWN", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("GoldenrodCity_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("EcruteakCity_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 5
        HnsMapLocation("OlivineCity_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 6
        HnsMapLocation("CianwoodCity_hns", "MAPSEC_CIANWOOD_CITY", RegionId.JOHTO), // mapNum 7
        HnsMapLocation("SafariZoneGate_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 8
        HnsMapLocation("Mahoganytown_hns", "MAPSEC_MAHOGANY_TOWN", RegionId.JOHTO), // mapNum 9
        HnsMapLocation("BlackthornCity_hns", "MAPSEC_BLACKTHORN_CITY", RegionId.JOHTO), // mapNum 10
        HnsMapLocation("Route29_hns", "MAPSEC_ROUTE_29", RegionId.JOHTO), // mapNum 11
        HnsMapLocation("Route30_hns", "MAPSEC_ROUTE_30", RegionId.JOHTO), // mapNum 12
        HnsMapLocation("Route31_hns", "MAPSEC_ROUTE_31", RegionId.JOHTO), // mapNum 13
        HnsMapLocation("Route32_hns", "MAPSEC_ROUTE_32", RegionId.JOHTO), // mapNum 14
        HnsMapLocation("Route33_hns", "MAPSEC_ROUTE_33", RegionId.JOHTO), // mapNum 15
        HnsMapLocation("Route34_hns", "MAPSEC_ROUTE_34", RegionId.JOHTO), // mapNum 16
        HnsMapLocation("Route35_hns", "MAPSEC_ROUTE_35", RegionId.JOHTO), // mapNum 17
        HnsMapLocation("Route36_hns", "MAPSEC_ROUTE_36", RegionId.JOHTO), // mapNum 18
        HnsMapLocation("Route37_hns", "MAPSEC_ROUTE_37", RegionId.JOHTO), // mapNum 19
        HnsMapLocation("Route38_hns", "MAPSEC_ROUTE_38", RegionId.JOHTO), // mapNum 20
        HnsMapLocation("Route39_hns", "MAPSEC_ROUTE_39", RegionId.JOHTO), // mapNum 21
        HnsMapLocation("Route40_hns", "MAPSEC_ROUTE_40", RegionId.JOHTO), // mapNum 22
        HnsMapLocation("Route41_hns", "MAPSEC_ROUTE_41", RegionId.JOHTO), // mapNum 23
        HnsMapLocation("Route42_hns", "MAPSEC_ROUTE_42", RegionId.JOHTO), // mapNum 24
        HnsMapLocation("Route43_hns", "MAPSEC_ROUTE_43", RegionId.JOHTO), // mapNum 25
        HnsMapLocation("Route44_hns", "MAPSEC_ROUTE_44", RegionId.JOHTO), // mapNum 26
        HnsMapLocation("Route45_hns", "MAPSEC_ROUTE_45", RegionId.JOHTO), // mapNum 27
        HnsMapLocation("Route46_hns", "MAPSEC_ROUTE_46", RegionId.JOHTO), // mapNum 28
        HnsMapLocation("Route47_hns", "MAPSEC_ROUTE_47", RegionId.JOHTO), // mapNum 29
        HnsMapLocation("Route48_hns", "MAPSEC_ROUTE_48", RegionId.JOHTO), // mapNum 30
        HnsMapLocation("PalletTown_hns", "MAPSEC_PALLET_TOWN", RegionId.KANTO), // mapNum 31
        HnsMapLocation("ViridianCity_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO), // mapNum 32
        HnsMapLocation("PewterCity_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO), // mapNum 33
        HnsMapLocation("CeruleanCity_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO), // mapNum 34
        HnsMapLocation("VermilionCity_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 35
        HnsMapLocation("LavenderTown_hns", "MAPSEC_LAVENDER_TOWN", RegionId.KANTO), // mapNum 36
        HnsMapLocation("CeladonCity_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 37
        HnsMapLocation("SaffronCity_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 38
        HnsMapLocation("FuchsiaCity_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 39
        HnsMapLocation("CinnabarIsland_hns", "MAPSEC_CINNABAR_ISLAND", RegionId.KANTO), // mapNum 40
        HnsMapLocation("Route1_hns", "MAPSEC_ROUTE_1", RegionId.KANTO), // mapNum 41
        HnsMapLocation("Route2_hns", "MAPSEC_ROUTE_2", RegionId.KANTO), // mapNum 42
        HnsMapLocation("Route3_hns", "MAPSEC_ROUTE_3", RegionId.KANTO), // mapNum 43
        HnsMapLocation("Route4_hns", "MAPSEC_ROUTE_4", RegionId.KANTO), // mapNum 44
        HnsMapLocation("Route5_hns", "MAPSEC_ROUTE_5", RegionId.KANTO), // mapNum 45
        HnsMapLocation("Route6_hns", "MAPSEC_ROUTE_6", RegionId.KANTO), // mapNum 46
        HnsMapLocation("Route7_hns", "MAPSEC_ROUTE_7", RegionId.KANTO), // mapNum 47
        HnsMapLocation("Route8_hns", "MAPSEC_ROUTE_8", RegionId.KANTO), // mapNum 48
        HnsMapLocation("Route9_hns", "MAPSEC_ROUTE_9", RegionId.KANTO), // mapNum 49
        HnsMapLocation("Route10_hns", "MAPSEC_ROUTE_10", RegionId.KANTO), // mapNum 50
        HnsMapLocation("Route11_hns", "MAPSEC_ROUTE_11", RegionId.KANTO), // mapNum 51
        HnsMapLocation("Route12_hns", "MAPSEC_ROUTE_12", RegionId.KANTO), // mapNum 52
        HnsMapLocation("Route13_hns", "MAPSEC_ROUTE_13", RegionId.KANTO), // mapNum 53
        HnsMapLocation("Route14_hns", "MAPSEC_ROUTE_14", RegionId.KANTO), // mapNum 54
        HnsMapLocation("Route15_hns", "MAPSEC_ROUTE_15", RegionId.KANTO), // mapNum 55
        HnsMapLocation("Route16_hns", "MAPSEC_ROUTE_16", RegionId.KANTO), // mapNum 56
        HnsMapLocation("Route17_hns", "MAPSEC_ROUTE_17", RegionId.KANTO), // mapNum 57
        HnsMapLocation("Route18_hns", "MAPSEC_ROUTE_18", RegionId.KANTO), // mapNum 58
        HnsMapLocation("Route19_hns", "MAPSEC_ROUTE_19", RegionId.KANTO), // mapNum 59
        HnsMapLocation("Route20_hns", "MAPSEC_ROUTE_20", RegionId.KANTO), // mapNum 60
        HnsMapLocation("Route21_hns", "MAPSEC_ROUTE_21", RegionId.KANTO), // mapNum 61
        HnsMapLocation("Route22_hns", "MAPSEC_ROUTE_22", RegionId.KANTO), // mapNum 62
        HnsMapLocation("Route23_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 63
        HnsMapLocation("IndigoPlateau_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 64
        HnsMapLocation("Route24_hns", "MAPSEC_ROUTE_24", RegionId.KANTO), // mapNum 65
        HnsMapLocation("Route25_hns", "MAPSEC_ROUTE_25", RegionId.KANTO), // mapNum 66
        HnsMapLocation("Route26_hns", "MAPSEC_ROUTE_26", RegionId.JOHTO), // mapNum 67
        HnsMapLocation("Route26North_hns", "MAPSEC_ROUTE_26", RegionId.JOHTO), // mapNum 68
        HnsMapLocation("Route27_hns", "MAPSEC_ROUTE_27", RegionId.JOHTO), // mapNum 69
        HnsMapLocation("Route28_hns", "MAPSEC_ROUTE_28", RegionId.JOHTO), // mapNum 70
    )

    private fun group1(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("NewBarkTown_Lab_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("NewBarkTown_House2_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("NewBarkTown_House1_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("NewBarkTown_PlayersHouse_1F_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("NewBarkTown_PlayersHouse_2F_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO), // mapNum 4
    )

    private fun group2(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("CherrygroveCity_PokemonCenter_hns", "MAPSEC_CHERRYGROVE_CITY", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("CherrygroveCity_Mart_hns", "MAPSEC_CHERRYGROVE_CITY", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("CherrygroveCity_House1_hns", "MAPSEC_CHERRYGROVE_CITY", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("CherrygroveCity_House2_hns", "MAPSEC_CHERRYGROVE_CITY", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("CherrygroveCity_House3_hns", "MAPSEC_CHERRYGROVE_CITY", RegionId.JOHTO), // mapNum 4
    )

    private fun group3(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("VioletCity_PokemonCenter_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("VioletCity_Mart_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("VioletCity_TrainerSchool_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("VioletCity_House1_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("VioletCity_House2_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("VioletCity_Gym_hns", "MAPSEC_VIOLET_CITY", RegionId.JOHTO), // mapNum 5
    )

    private fun group4(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("AzaleaTown_PokemonCenter_hns", "MAPSEC_AZALEA_TOWN", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("AzaleaTown_Mart_hns", "MAPSEC_AZALEA_TOWN", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("AzaleaTown_KurtsHouse_hns", "MAPSEC_AZALEA_TOWN", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("AzaleaTown_House1_hns", "MAPSEC_AZALEA_TOWN", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("AzaleaTown_Gym_hns", "MAPSEC_AZALEA_TOWN", RegionId.JOHTO), // mapNum 4
    )

    private fun group5(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("GoldenrodCity_PokemonCenter_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("GoldenrodCity_UndergroundEntrance_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("GoldenrodCity_UndergroundTunnel_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("GoldenrodCity_UndergroundSwitches_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("GoldenrodCity_UndergroundStorage_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("GoldenrodCity_DepartmentStoreBasement_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 5
        HnsMapLocation("GoldenrodCity_Gym_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 6
        HnsMapLocation("GoldenrodCity_FlowerShop_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 7
        HnsMapLocation("GoldenrodCity_House1_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 8
        HnsMapLocation("GoldenrodCity_House2_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 9
        HnsMapLocation("GoldenrodCity_House3_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 10
        HnsMapLocation("GoldenrodCity_BillsHouse_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 11
        HnsMapLocation("GoldenrodCity_BikeShop_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 12
        HnsMapLocation("GoldenrodCity_GameCorner_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 13
        HnsMapLocation("GoldenrodCity_TrainStation_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 14
        HnsMapLocation("GoldenrodCity_RadioTower_1F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 15
        HnsMapLocation("GoldenrodCity_RadioTower_2F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 16
        HnsMapLocation("GoldenrodCity_RadioTower_3F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 17
        HnsMapLocation("GoldenrodCity_RadioTower_4F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 18
        HnsMapLocation("GoldenrodCity_RadioTower_5F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 19
        HnsMapLocation("GoldenrodCity_DepartmentStore_1F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 20
        HnsMapLocation("GoldenrodCity_DepartmentStore_2F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 21
        HnsMapLocation("GoldenrodCity_DepartmentStore_3F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 22
        HnsMapLocation("GoldenrodCity_DepartmentStore_4F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 23
        HnsMapLocation("GoldenrodCity_DepartmentStore_5F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 24
        HnsMapLocation("GoldenrodCity_DepartmentStore_6F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 25
        HnsMapLocation("GoldenrodCity_DepartmentStore_7F_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 26
        HnsMapLocation("GoldenrodCity_DepartmentStore_7FNight_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 27
        HnsMapLocation("GoldenrodCity_DepartmentStoreElevator_hns", "MAPSEC_GOLDENROD_CITY", RegionId.JOHTO), // mapNum 28
    )

    private fun group6(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("EcruteakCity_PokemonCenter_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("EcruteakCity_Mart_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("EcruteakCity_Theater_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("EcruteakCity_House1_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("EcruteakCity_House2_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("EcruteakCity_SageOffice1_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 5
        HnsMapLocation("EcruteakCity_SageOffice2_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 6
        HnsMapLocation("BellchimeTrail_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 7
        HnsMapLocation("EcruteakCity_Gym_hns", "MAPSEC_ECRUTEAK_CITY", RegionId.JOHTO), // mapNum 8
    )

    private fun group7(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("OlivineCity_PokemonCenter_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("OlivineCity_Gym_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("OlivineCity_Cafe_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("OlivineCity_Mart_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("OlivineCity_House1_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("OlivineCity_House2_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 5
        HnsMapLocation("OlivineCity_House3_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 6
        HnsMapLocation("OlivineCity_PortOutside_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 7
        HnsMapLocation("OlivineCity_PortInside_hns", "MAPSEC_OLIVINE_CITY", RegionId.JOHTO), // mapNum 8
        HnsMapLocation("OlivineCity_Lighthouse_hns", "MAPSEC_OLIVINE_LIGHTHOUSE", RegionId.JOHTO), // mapNum 9
    )

    private fun group8(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("CianwoodPokecenter_hns", "MAPSEC_CIANWOOD_CITY", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("CianwoodHouse1_hns", "MAPSEC_CIANWOOD_CITY", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("CianwoodHouse2_hns", "MAPSEC_CIANWOOD_CITY", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("CianwoodHouse3_hns", "MAPSEC_CIANWOOD_CITY", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("CianwoodShop_hns", "MAPSEC_CIANWOOD_CITY", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("CianwoodGym_hns", "MAPSEC_CIANWOOD_CITY", RegionId.JOHTO), // mapNum 5
    )

    private fun group9(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("MahoganyTown_PokemonCenter_hns", "MAPSEC_MAHOGANY_TOWN", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("MahoganyTown_Gym_hns", "MAPSEC_MAHOGANY_TOWN", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("MahoganyTown_Shop_hns", "MAPSEC_MAHOGANY_TOWN", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("MahoganyTown_House1_hns", "MAPSEC_MAHOGANY_TOWN", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("LakeOfRage_House1_hns", "MAPSEC_LAKE_OF_RAGE", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("LakeOfRage_House2_hns", "MAPSEC_LAKE_OF_RAGE", RegionId.JOHTO), // mapNum 5
    )

    private fun group10(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("BlackthornCity_PokemonCenter_hns", "MAPSEC_BLACKTHORN_CITY", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("BlackthornCity_Mart_hns", "MAPSEC_BLACKTHORN_CITY", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("BlackthornCity_Gym_hns", "MAPSEC_BLACKTHORN_CITY", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("BlackthornCity_House1_hns", "MAPSEC_BLACKTHORN_CITY", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("BlackthornCity_House2_hns", "MAPSEC_BLACKTHORN_CITY", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("BlackthornCity_House3_hns", "MAPSEC_BLACKTHORN_CITY", RegionId.JOHTO), // mapNum 5
    )

    private fun group11(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("PalletTown_RedsHouse_1F_hns", "MAPSEC_PALLET_TOWN", RegionId.KANTO), // mapNum 0
        HnsMapLocation("PalletTown_RedsHouse_2F_hns", "MAPSEC_PALLET_TOWN", RegionId.KANTO), // mapNum 1
        HnsMapLocation("PalletTown_House2_hns", "MAPSEC_PALLET_TOWN", RegionId.KANTO), // mapNum 2
        HnsMapLocation("PalletTown_House3_hns", "MAPSEC_PALLET_TOWN", RegionId.KANTO), // mapNum 3
        HnsMapLocation("PalletTown_Lab_hns", "MAPSEC_PALLET_TOWN", RegionId.KANTO), // mapNum 4
    )

    private fun group12(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("ViridianCity_PokemonCenter_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO), // mapNum 0
        HnsMapLocation("ViridianCity_Mart_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO), // mapNum 1
        HnsMapLocation("ViridianCity_House1_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO), // mapNum 2
        HnsMapLocation("ViridianCity_House2_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO), // mapNum 3
        HnsMapLocation("ViridianCity_Gym_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO), // mapNum 4
    )

    private fun group13(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("PewterCity_PokemonCenter_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO), // mapNum 0
        HnsMapLocation("PewterCity_Mart_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO), // mapNum 1
        HnsMapLocation("PewterCity_House1_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO), // mapNum 2
        HnsMapLocation("PewterCity_House2_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO), // mapNum 3
        HnsMapLocation("PewterCity_Gym_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO), // mapNum 4
        HnsMapLocation("PewterCity_Museum_1F_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO), // mapNum 5
        HnsMapLocation("PewterCity_Museum_2F_hns", "MAPSEC_PEWTER_CITY", RegionId.KANTO), // mapNum 6
    )

    private fun group14(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("CeruleanCity_PokemonCenter_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO), // mapNum 0
        HnsMapLocation("CeruleanCity_Mart_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO), // mapNum 1
        HnsMapLocation("CeruleanCity_House2_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO), // mapNum 2
        HnsMapLocation("CeruleanCity_House3_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO), // mapNum 3
        HnsMapLocation("CeruleanCity_BikeShop_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO), // mapNum 4
        HnsMapLocation("CeruleanCity_Gym_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO), // mapNum 5
    )

    private fun group15(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("VermilionCity_PokemonCenter_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 0
        HnsMapLocation("VermilionCity_Mart_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 1
        HnsMapLocation("VermilionCity_PortOutside_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 2
        HnsMapLocation("VermilionCity_PortInside_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 3
        HnsMapLocation("VermilionCity_Gym_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 4
        HnsMapLocation("VermilionCity_FanClub_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 5
        HnsMapLocation("VermilionCity_House1_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 6
        HnsMapLocation("VermilionCity_House2_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 7
        HnsMapLocation("VermilionCity_House3_hns", "MAPSEC_VERMILION_CITY", RegionId.KANTO), // mapNum 8
    )

    private fun group16(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("LavenderTown_PokemonCenter_hns", "MAPSEC_LAVENDER_TOWN", RegionId.KANTO), // mapNum 0
        HnsMapLocation("LavenderTown_Mart_hns", "MAPSEC_LAVENDER_TOWN", RegionId.KANTO), // mapNum 1
        HnsMapLocation("LavenderTown_House1_hns", "MAPSEC_LAVENDER_TOWN", RegionId.KANTO), // mapNum 2
        HnsMapLocation("LavenderTown_House2_hns", "MAPSEC_LAVENDER_TOWN", RegionId.KANTO), // mapNum 3
        HnsMapLocation("LavenderTown_House3_hns", "MAPSEC_LAVENDER_TOWN", RegionId.KANTO), // mapNum 4
        HnsMapLocation("LavenderTown_RadioStation_hns", "MAPSEC_LAVENDER_TOWN", RegionId.KANTO), // mapNum 5
        HnsMapLocation("LavenderTown_SoulHouse_hns", "MAPSEC_LAVENDER_TOWN", RegionId.KANTO), // mapNum 6
    )

    private fun group17(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("CeladonCity_PokemonCenter_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 0
        HnsMapLocation("CeladonCity_House1_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 1
        HnsMapLocation("CeladonCity_House2_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 2
        HnsMapLocation("CeladonCity_Apartments_1F_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 3
        HnsMapLocation("CeladonCity_Apartments_2F_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 4
        HnsMapLocation("CeladonCity_Apartments_3F_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 5
        HnsMapLocation("CeladonCity_Apartments_RoofDay_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 6
        HnsMapLocation("CeladonCity_Apartments_RoofNight_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 7
        HnsMapLocation("CeladonCity_Apartments_RoofHouse_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 8
        HnsMapLocation("CeladonCity_GameCorner_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 9
        HnsMapLocation("CeladonCity_DepartmentStore_1F_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 10
        HnsMapLocation("CeladonCity_DepartmentStore_2F_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 11
        HnsMapLocation("CeladonCity_DepartmentStore_3F_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 12
        HnsMapLocation("CeladonCity_DepartmentStore_4F_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 13
        HnsMapLocation("CeladonCity_DepartmentStore_5F_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 14
        HnsMapLocation("CeladonCity_DepartmentStore_RoofDay_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 15
        HnsMapLocation("CeladonCity_DepartmentStore_RoofNight_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 16
        HnsMapLocation("CeladonCity_Gym_hns", "MAPSEC_CELADON_CITY", RegionId.KANTO), // mapNum 17
    )

    private fun group18(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("SaffronCity_PokemonCenter_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 0
        HnsMapLocation("SaffronCity_Mart_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 1
        HnsMapLocation("SaffronCity_TrainStation_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 2
        HnsMapLocation("SaffronCity_FightingDojo_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 3
        HnsMapLocation("SaffronCity_FightingDojoVIP_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 4
        HnsMapLocation("SaffronCity_Gym_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 5
        HnsMapLocation("SaffronCity_SilphCo_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 6
        HnsMapLocation("SaffronCity_CopyCatsHouse_1F_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 7
        HnsMapLocation("SaffronCity_CopyCatsHouse_2F_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 8
        HnsMapLocation("SaffronCity_House1_hns", "MAPSEC_SAFFRON_CITY", RegionId.KANTO), // mapNum 9
    )

    private fun group19(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("FuchsiaCity_PokemonCenter_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 0
        HnsMapLocation("FuchsiaCity_Mart_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 1
        HnsMapLocation("FuchsiaCity_House1_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 2
        HnsMapLocation("FuchsiaCity_House2_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 3
        HnsMapLocation("FuchsiaCity_Gym_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 4
        HnsMapLocation("FuchsiaCity_Route19_Gate_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 5
        HnsMapLocation("FuchsiaCity_Route15_Gate_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 6
    )

    private fun group20(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("CinnabarIsland_PokemonCenter_hns", "MAPSEC_CINNABAR_ISLAND", RegionId.KANTO), // mapNum 0
    )

    private fun group21(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("IndigoPlateau_PokemonCenter_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("PokemonLeague_WillsRoom_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("PokemonLeague_KogasRoom_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("PokemonLeague_BrunosRoom_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("PokemonLeague_KarensRoom_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("PokemonLeague_ChampionsRoom_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 5
        HnsMapLocation("PokemonLeague_HallOfFame_hns", "MAPSEC_INDIGO_PLATEAU", RegionId.JOHTO), // mapNum 6
    )

    private fun group22(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("Gate_Route29_Route46_hns", "MAPSEC_ROUTE_29", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("Gate_Route31_VioletCity_hns", "MAPSEC_ROUTE_31", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("Gate_RuinsOfAlph_Route32_hns", "MAPSEC_ROUTE_32", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("Gate_RuinsOfAlph_Route36_hns", "MAPSEC_ROUTE_36", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("Gate_AzaleaTown_IlexForest_hns", "MAPSEC_AZALEA_TOWN", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("Gate_IlexForest_Route34_hns", "MAPSEC_ROUTE_34", RegionId.JOHTO), // mapNum 5
        HnsMapLocation("Gate_GoldenrodCity_Route35_hns", "MAPSEC_ROUTE_35", RegionId.JOHTO), // mapNum 6
        HnsMapLocation("Gate_NationalPark_hns", "MAPSEC_NATIONAL_PARK", RegionId.JOHTO), // mapNum 7
        HnsMapLocation("Gate_EcruteakCity_Route38_hns", "MAPSEC_ROUTE_38", RegionId.JOHTO), // mapNum 8
        HnsMapLocation("Gate_EcruteakCity_Route42_hns", "MAPSEC_ROUTE_42", RegionId.JOHTO), // mapNum 9
        HnsMapLocation("Gate_MahoganyTown_Route43_hns", "MAPSEC_ROUTE_43", RegionId.JOHTO), // mapNum 10
        HnsMapLocation("Gate_Route43_hns", "MAPSEC_ROUTE_43", RegionId.JOHTO), // mapNum 11
        HnsMapLocation("Gate_Route40_TrainerHill_Courtyard_hns", "MAPSEC_ROUTE_40", RegionId.JOHTO), // mapNum 12
        HnsMapLocation("SlateportCity_BattleTentLobby_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 13
        HnsMapLocation("SlateportCity_BattleTentCorridor_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 14
        HnsMapLocation("SlateportCity_BattleTentBattleRoom_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 15
        HnsMapLocation("VerdanturfTown_BattleTentLobby_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 16
        HnsMapLocation("VerdanturfTown_BattleTentCorridor_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 17
        HnsMapLocation("VerdanturfTown_BattleTentBattleRoom_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 18
        HnsMapLocation("FallarborTown_BattleTentLobby_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 19
        HnsMapLocation("FallarborTown_BattleTentCorridor_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 20
        HnsMapLocation("FallarborTown_BattleTentBattleRoom_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 21
        HnsMapLocation("ReceptionGate_hns", "MAPSEC_VICTORY_ROAD_HNS", RegionId.JOHTO), // mapNum 22
        HnsMapLocation("Route39_FarmHouse_hns", "MAPSEC_ROUTE_39", RegionId.JOHTO), // mapNum 23
        HnsMapLocation("Route39_Barn_hns", "MAPSEC_ROUTE_39", RegionId.JOHTO), // mapNum 24
        HnsMapLocation("Route30_House_hns", "MAPSEC_ROUTE_30", RegionId.JOHTO), // mapNum 25
        HnsMapLocation("Route30_MrPokemonsHouse_hns", "MAPSEC_ROUTE_30", RegionId.JOHTO), // mapNum 26
        HnsMapLocation("Route32_PokemonCenter_hns", "MAPSEC_ROUTE_32", RegionId.JOHTO), // mapNum 27
        HnsMapLocation("RuinsOfAlph_Lab_hns", "MAPSEC_RUINS_OF_ALPH", RegionId.JOHTO), // mapNum 28
        HnsMapLocation("Route34_DayCare_hns", "MAPSEC_ROUTE_34", RegionId.JOHTO), // mapNum 29
        HnsMapLocation("Route28_House_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 30
        HnsMapLocation("SafariZoneGate_PokemonCenter_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 31
        HnsMapLocation("SafariZoneGate_SafariZoneEntrance_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 32
        HnsMapLocation("Route27_House_hns", "MAPSEC_ROUTE_27", RegionId.JOHTO), // mapNum 33
        HnsMapLocation("MtSilver_PokemonCenter_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 34
    )

    private fun group23(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("Route26_House1_hns", "MAPSEC_ROUTE_26", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("Route26_House2_hns", "MAPSEC_ROUTE_26", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("Gate_Route2_ViridianForest_hns", "MAPSEC_ROUTE_2", RegionId.KANTO), // mapNum 2
        HnsMapLocation("Gate_ViridianForest_Route2_hns", "MAPSEC_ROUTE_2", RegionId.KANTO), // mapNum 3
        HnsMapLocation("Gate_Route2_hns", "MAPSEC_ROUTE_2", RegionId.KANTO), // mapNum 4
        HnsMapLocation("Route2_House_hns", "MAPSEC_ROUTE_2", RegionId.KANTO), // mapNum 5
        HnsMapLocation("MtMoon_Shop_hns", "MAPSEC_MT_MOON", RegionId.KANTO), // mapNum 6
        HnsMapLocation("CeruleanCity_House1_hns", "MAPSEC_CERULEAN_CITY", RegionId.KANTO), // mapNum 7
        HnsMapLocation("Route5_House_hns", "MAPSEC_ROUTE_5", RegionId.KANTO), // mapNum 8
        HnsMapLocation("Route5_TunnelEntrance_hns", "MAPSEC_ROUTE_5", RegionId.KANTO), // mapNum 9
        HnsMapLocation("Gate_SaffronCity_Route5_hns", "MAPSEC_ROUTE_5", RegionId.KANTO), // mapNum 10
        HnsMapLocation("SaffronCity_Tunnel_NS_hns", "MAPSEC_ROUTE_5", RegionId.KANTO), // mapNum 11
        HnsMapLocation("Route6_TunnelEntrance_hns", "MAPSEC_ROUTE_6", RegionId.KANTO), // mapNum 12
        HnsMapLocation("Gate_SaffronCity_Route6_hns", "MAPSEC_ROUTE_6", RegionId.KANTO), // mapNum 13
        HnsMapLocation("Gate_SaffronCity_Route7_hns", "MAPSEC_ROUTE_7", RegionId.KANTO), // mapNum 14
        HnsMapLocation("Route7_TunnelEntrance_hns", "MAPSEC_ROUTE_7", RegionId.KANTO), // mapNum 15
        HnsMapLocation("SaffronCity_Tunnel_SW_hns", "MAPSEC_ROUTE_7", RegionId.KANTO), // mapNum 16
        HnsMapLocation("Route8_TunnelEntrance_hns", "MAPSEC_ROUTE_8", RegionId.KANTO), // mapNum 17
        HnsMapLocation("Gate_SaffronCity_Route8_hns", "MAPSEC_ROUTE_8", RegionId.KANTO), // mapNum 18
        HnsMapLocation("Route9_PokemonCenter_hns", "MAPSEC_ROUTE_9", RegionId.KANTO), // mapNum 19
        HnsMapLocation("Route10_PowerPlantEntrance_hns", "MAPSEC_POWER_PLANT", RegionId.KANTO), // mapNum 20
        HnsMapLocation("Route10_PowerPlantBackRoom_hns", "MAPSEC_POWER_PLANT", RegionId.KANTO), // mapNum 21
        HnsMapLocation("Route16_House_hns", "MAPSEC_ROUTE_16", RegionId.KANTO), // mapNum 22
        HnsMapLocation("Gate_CeladonCity_Route16_hns", "MAPSEC_ROUTE_16", RegionId.KANTO), // mapNum 23
        HnsMapLocation("Gate_FuchsiaCity_Route18_hns", "MAPSEC_ROUTE_16", RegionId.KANTO), // mapNum 24
        HnsMapLocation("Route12_House_hns", "MAPSEC_ROUTE_12", RegionId.KANTO), // mapNum 25
        HnsMapLocation("Route4_PokemonCenter_hns", "MAPSEC_ROUTE_4", RegionId.KANTO), // mapNum 26
        HnsMapLocation("Route25_BillsHouse_hns", "MAPSEC_ROUTE_25", RegionId.KANTO), // mapNum 27
    )

    private fun group24(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("DarkCave_SouthSide_hns", "MAPSEC_DARK_CAVE", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("DarkCave_NorthSide_hns", "MAPSEC_DARK_CAVE", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("SproutTower_1F_hns", "MAPSEC_SPROUT_TOWER", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("SproutTower_2F_hns", "MAPSEC_SPROUT_TOWER", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("SproutTower_3F_hns", "MAPSEC_SPROUT_TOWER", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("RuinsOfAlph_Outside_hns", "MAPSEC_RUINS_OF_ALPH", RegionId.JOHTO), // mapNum 5
        HnsMapLocation("RuinsOfAlph_B1F_hns", "MAPSEC_RUINS_OF_ALPH", RegionId.JOHTO), // mapNum 6
        HnsMapLocation("RuinsOfAlph_PuzzleAndRewardChambers_hns", "MAPSEC_RUINS_OF_ALPH", RegionId.JOHTO), // mapNum 7
        HnsMapLocation("UnionCave_1F_hns", "MAPSEC_UNION_CAVE", RegionId.JOHTO), // mapNum 8
        HnsMapLocation("UnionCave_B1F_hns", "MAPSEC_UNION_CAVE", RegionId.JOHTO), // mapNum 9
        HnsMapLocation("UnionCave_B2F_hns", "MAPSEC_UNION_CAVE", RegionId.JOHTO), // mapNum 10
        HnsMapLocation("SlowpokeWell_B1F_hns", "MAPSEC_SLOWPOKE_WELL", RegionId.JOHTO), // mapNum 11
        HnsMapLocation("SlowpokeWell_B2F_hns", "MAPSEC_SLOWPOKE_WELL", RegionId.JOHTO), // mapNum 12
        HnsMapLocation("IlexForest_hns", "MAPSEC_ILEX_FOREST", RegionId.JOHTO), // mapNum 13
        HnsMapLocation("NationalPark_Normal_hns", "MAPSEC_NATIONAL_PARK", RegionId.JOHTO), // mapNum 14
        HnsMapLocation("NationalPark_BugContest_hns", "MAPSEC_NATIONAL_PARK", RegionId.JOHTO), // mapNum 15
        HnsMapLocation("BurnedTower_1F_hns", "MAPSEC_BURNED_TOWER", RegionId.JOHTO), // mapNum 16
        HnsMapLocation("BurnedTower_B1F_hns", "MAPSEC_BURNED_TOWER", RegionId.JOHTO), // mapNum 17
        HnsMapLocation("CliffEdgeGate_hns", "MAPSEC_CLIFF_CAVE", RegionId.JOHTO), // mapNum 18
        HnsMapLocation("MtMortar_1F_South_hns", "MAPSEC_MT_MORTAR", RegionId.JOHTO), // mapNum 19
        HnsMapLocation("MtMortar_1F_North_hns", "MAPSEC_MT_MORTAR", RegionId.JOHTO), // mapNum 20
        HnsMapLocation("MtMortar_2F_hns", "MAPSEC_MT_MORTAR", RegionId.JOHTO), // mapNum 21
        HnsMapLocation("MtMortar_B1F_hns", "MAPSEC_MT_MORTAR", RegionId.JOHTO), // mapNum 22
        HnsMapLocation("LakeOfRage_hns", "MAPSEC_LAKE_OF_RAGE", RegionId.JOHTO), // mapNum 23
        HnsMapLocation("LakeOfRageLowTide_hns", "MAPSEC_LAKE_OF_RAGE", RegionId.JOHTO), // mapNum 24
        HnsMapLocation("IcePath_1F_hns", "MAPSEC_ICE_PATH", RegionId.JOHTO), // mapNum 25
        HnsMapLocation("IcePath_B1F_hns", "MAPSEC_ICE_PATH", RegionId.JOHTO), // mapNum 26
        HnsMapLocation("IcePath_B2F_hns", "MAPSEC_ICE_PATH", RegionId.JOHTO), // mapNum 27
        HnsMapLocation("IcePath_B3F_hns", "MAPSEC_ICE_PATH", RegionId.JOHTO), // mapNum 28
        HnsMapLocation("IcePath_B4F_hns", "MAPSEC_ICE_PATH", RegionId.JOHTO), // mapNum 29
        HnsMapLocation("DragonsDen_Entrance_hns", "MAPSEC_DRAGONS_DEN", RegionId.JOHTO), // mapNum 30
        HnsMapLocation("DragonsDen_Cavern_hns", "MAPSEC_DRAGONS_DEN", RegionId.JOHTO), // mapNum 31
        HnsMapLocation("DragonsDen_Shrine_hns", "MAPSEC_DRAGONS_DEN", RegionId.JOHTO), // mapNum 32
        HnsMapLocation("WhirlIslands_1F_hns", "MAPSEC_WHIRL_ISLANDS", RegionId.JOHTO), // mapNum 33
        HnsMapLocation("WhirlIslands_B1F_hns", "MAPSEC_WHIRL_ISLANDS", RegionId.JOHTO), // mapNum 34
        HnsMapLocation("WhirlIslands_B1F_Inner_hns", "MAPSEC_WHIRL_ISLANDS", RegionId.JOHTO), // mapNum 35
        HnsMapLocation("WhirlIslands_B2F_hns", "MAPSEC_WHIRL_ISLANDS", RegionId.JOHTO), // mapNum 36
        HnsMapLocation("WhirlIslands_B3F_hns", "MAPSEC_WHIRL_ISLANDS", RegionId.JOHTO), // mapNum 37
        HnsMapLocation("WhirlIslands_Descent_hns", "MAPSEC_WHIRL_ISLANDS", RegionId.JOHTO), // mapNum 38
        HnsMapLocation("WhirlIslands_LugiaChamber_hns", "MAPSEC_WHIRL_ISLANDS", RegionId.JOHTO), // mapNum 39
        HnsMapLocation("TinTower_1F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 40
        HnsMapLocation("TinTower_2F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 41
        HnsMapLocation("TinTower_3F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 42
        HnsMapLocation("TinTower_4F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 43
        HnsMapLocation("TinTower_5F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 44
        HnsMapLocation("TinTower_6F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 45
        HnsMapLocation("TinTower_7F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 46
        HnsMapLocation("TinTower_8F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 47
        HnsMapLocation("TinTower_9F_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 48
        HnsMapLocation("TinTower_RoofDay_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 49
        HnsMapLocation("TinTower_RoofNight_hns", "MAPSEC_TIN_TOWER", RegionId.JOHTO), // mapNum 50
        HnsMapLocation("TohjoFalls_Cavern_hns", "MAPSEC_TOHJO_FALLS", RegionId.JOHTO), // mapNum 51
        HnsMapLocation("TohjoFalls_GiovanniRoom_hns", "MAPSEC_TOHJO_FALLS", RegionId.JOHTO), // mapNum 52
        HnsMapLocation("VictoryRoadKanto_B2F_hns", "MAPSEC_VICTORY_ROAD_HNS", RegionId.JOHTO), // mapNum 53
        HnsMapLocation("VictoryRoadKanto_B1F_hns", "MAPSEC_VICTORY_ROAD_HNS", RegionId.JOHTO), // mapNum 54
        HnsMapLocation("VictoryRoadKanto_1F_hns", "MAPSEC_VICTORY_ROAD_HNS", RegionId.JOHTO), // mapNum 55
        HnsMapLocation("ViridianForest_hns", "MAPSEC_VIRIDIAN_FOREST", RegionId.KANTO), // mapNum 56
        HnsMapLocation("MtMoon_Cave_hns", "MAPSEC_MT_MOON", RegionId.KANTO), // mapNum 57
        HnsMapLocation("MtMoon_Outside_hns", "MAPSEC_MT_MOON", RegionId.KANTO), // mapNum 58
        HnsMapLocation("RockTunnel_B1F_hns", "MAPSEC_ROCK_TUNNEL", RegionId.KANTO), // mapNum 59
        HnsMapLocation("RockTunnel_1F_hns", "MAPSEC_ROCK_TUNNEL", RegionId.KANTO), // mapNum 60
        HnsMapLocation("CeruleanCave_1F_hns", "MAPSEC_CERULEAN_CAVE", RegionId.KANTO), // mapNum 61
        HnsMapLocation("CeruleanCave_B1F_hns", "MAPSEC_CERULEAN_CAVE", RegionId.KANTO), // mapNum 62
        HnsMapLocation("CeruleanCave_B2F_hns", "MAPSEC_CERULEAN_CAVE", RegionId.KANTO), // mapNum 63
        HnsMapLocation("DiglettsCave_EntranceNorth_hns", "MAPSEC_DIGLETTS_CAVE", RegionId.KANTO), // mapNum 64
        HnsMapLocation("DiglettsCave_EntranceSouth_hns", "MAPSEC_DIGLETTS_CAVE", RegionId.KANTO), // mapNum 65
        HnsMapLocation("DiglettsCave_Tunnel_hns", "MAPSEC_DIGLETTS_CAVE", RegionId.KANTO), // mapNum 66
        HnsMapLocation("SeafoamIslands_1F_hns", "MAPSEC_SEAFOAM_ISLANDS", RegionId.KANTO), // mapNum 67
        HnsMapLocation("SeafoamIslands_Gym_hns", "MAPSEC_SEAFOAM_ISLANDS", RegionId.KANTO), // mapNum 68
        HnsMapLocation("SeafoamIslands_B1F_hns", "MAPSEC_SEAFOAM_ISLANDS", RegionId.KANTO), // mapNum 69
        HnsMapLocation("SeafoamIslands_SecretCave_hns", "MAPSEC_SEAFOAM_ISLANDS", RegionId.KANTO), // mapNum 70
        HnsMapLocation("MtSilver_Outside_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 71
        HnsMapLocation("MtSilver_1F_ItemRoom_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 72
        HnsMapLocation("MtSilver_1F_WaterfallRoom_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 73
        HnsMapLocation("MtSilver_1F_MoltresRoom_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 74
        HnsMapLocation("MtSilver_MountainSide_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 75
        HnsMapLocation("MtSilver_2F_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 76
        HnsMapLocation("MtSilver_3F_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 77
        HnsMapLocation("MtSilver_Snow_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 78
        HnsMapLocation("MtSilver_SummitDay_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 79
        HnsMapLocation("MtSilver_SummitNight_hns", "MAPSEC_MT_SILVER", RegionId.JOHTO), // mapNum 80
        HnsMapLocation("CliffEdgeCave_hns", "MAPSEC_CLIFF_CAVE", RegionId.JOHTO), // mapNum 81
        HnsMapLocation("EmbeddedTower_hns", "MAPSEC_EMBEDDED_TOWER", RegionId.JOHTO), // mapNum 82
        HnsMapLocation("RocketHideout_B1F_hns", "MAPSEC_ROCKET_HIDEOUT_HNS", RegionId.JOHTO), // mapNum 83
        HnsMapLocation("RocketHideout_B2F_hns", "MAPSEC_ROCKET_HIDEOUT_HNS", RegionId.JOHTO), // mapNum 84
        HnsMapLocation("RocketHideout_B3F_hns", "MAPSEC_ROCKET_HIDEOUT_HNS", RegionId.JOHTO), // mapNum 85
        HnsMapLocation("RuinsOfAlph_WordsRoom1_hns", "MAPSEC_RUINS_OF_ALPH", RegionId.JOHTO), // mapNum 86
        HnsMapLocation("RuinsOfAlph_WordsRoom2_hns", "MAPSEC_RUINS_OF_ALPH", RegionId.JOHTO), // mapNum 87
        HnsMapLocation("RuinsOfAlph_WordsRoom3_hns", "MAPSEC_RUINS_OF_ALPH", RegionId.JOHTO), // mapNum 88
        HnsMapLocation("RuinsOfAlph_WordsRoom4_hns", "MAPSEC_RUINS_OF_ALPH", RegionId.JOHTO), // mapNum 89
        HnsMapLocation("Route19_Cave_hns", "MAPSEC_ROUTE_19", RegionId.KANTO), // mapNum 90
    )

    private fun group25(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("PoniIsle_hns", "MAPSEC_PONI_ISLAND", RegionId.ALOLA), // mapNum 0
        HnsMapLocation("AkalaIsle_hns", "MAPSEC_AKALA_ISLAND", RegionId.ALOLA), // mapNum 1
        HnsMapLocation("MelemeleIsle_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA), // mapNum 2
        HnsMapLocation("AlolaWater_hns", "MAPSEC_ALOLA_OCEAN", RegionId.ALOLA), // mapNum 3
        HnsMapLocation("UlaulaIsle_hns", "MAPSEC_ULAULA_ISLAND", RegionId.ALOLA), // mapNum 4
    )

    private fun group26(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("Melemele_PlayerHouse_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA), // mapNum 0
        HnsMapLocation("Melemele_House_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA), // mapNum 1
        HnsMapLocation("Melemele_House_2_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA), // mapNum 2
        HnsMapLocation("Melemele_House_3_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA), // mapNum 3
        HnsMapLocation("Melemele_House_4_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA), // mapNum 4
        HnsMapLocation("Melemele_House_5_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA), // mapNum 5
        HnsMapLocation("Melemele_House_6_hns", "MAPSEC_MELEMELE_ISLAND", RegionId.ALOLA), // mapNum 6
        HnsMapLocation("Akala_House_hns", "MAPSEC_AKALA_ISLAND", RegionId.ALOLA), // mapNum 7
        HnsMapLocation("UlaUla_House_hns", "MAPSEC_ULAULA_ISLAND", RegionId.ALOLA), // mapNum 8
        HnsMapLocation("Poni_Cave_hns", "MAPSEC_PONI_CAVE", RegionId.ALOLA), // mapNum 9
        HnsMapLocation("Akala_Forest_hns", "MAPSEC_AKALA_FOREST", RegionId.ALOLA), // mapNum 10
        HnsMapLocation("UlaUla_Cave_hns", "MAPSEC_ULAULA_CAVE", RegionId.ALOLA), // mapNum 11
        HnsMapLocation("UlaUla_Forest_hns", "MAPSEC_ULAULA_ISLAND", RegionId.ALOLA), // mapNum 12
        HnsMapLocation("UlaUla_Cave_2_hns", "MAPSEC_ULAULA_CAVE_2", RegionId.ALOLA), // mapNum 13
        HnsMapLocation("Akala_Cave_hns", "MAPSEC_AKALA_CAVE", RegionId.ALOLA), // mapNum 14
    )

    private fun group27(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("UnionRoom_hns", "MAPSEC_DYNAMIC", null), // mapNum 0
    )

    private fun group28(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("SnowsweptCavern_hns", "MAPSEC_SNOWSWEPT_CAVERN", RegionId.SINJOH), // mapNum 0
        HnsMapLocation("NewSinjoh_HotSprings_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH), // mapNum 1
        HnsMapLocation("Route49_hns", "MAPSEC_ROUTE_49", RegionId.SINJOH), // mapNum 2
        HnsMapLocation("Route50_hns", "MAPSEC_ROUTE_50", RegionId.SINJOH), // mapNum 3
        HnsMapLocation("NewSinjoh_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH), // mapNum 4
        HnsMapLocation("SinjohRuins_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 5
    )

    private fun group29(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("NewSinjoh_PokemonCenter_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH), // mapNum 0
        HnsMapLocation("SinjohRuins_Temple_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 1
        HnsMapLocation("SinjohRuins_RegirockRoom_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 2
        HnsMapLocation("SinjohRuins_RegiceRoom_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 3
        HnsMapLocation("SinjohRuins_RegisteelRoom_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 4
        HnsMapLocation("SinjohRuins_RegidracoRoom_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 5
        HnsMapLocation("SinjohRuins_RegielekiRoom_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 6
        HnsMapLocation("SinjohRuins_RegigigasRoom_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 7
        HnsMapLocation("SinjohRuins_ArceusRoom_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 8
        HnsMapLocation("NewSinjoh_House1_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH), // mapNum 9
        HnsMapLocation("NewSinjoh_House2_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH), // mapNum 10
        HnsMapLocation("NewSinjoh_House3_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH), // mapNum 11
        HnsMapLocation("NewSinjoh_House4_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH), // mapNum 12
        HnsMapLocation("NewSinjoh_KimonoHideout_hns", "MAPSEC_NEW_SINJOH", RegionId.SINJOH), // mapNum 13
        HnsMapLocation("SinjohRuins_House1_hns", "MAPSEC_SINJOH_RUINS", RegionId.SINJOH), // mapNum 14
    )

    private fun group30(): List<HnsMapLocation?> = listOf(
        HnsMapLocation("TrainerHill_Courtyard_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 0
        HnsMapLocation("TrainerHill_Entrance_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 1
        HnsMapLocation("TrainerHill_1F_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 2
        HnsMapLocation("TrainerHill_2F_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 3
        HnsMapLocation("TrainerHill_3F_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 4
        HnsMapLocation("TrainerHill_4F_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 5
        HnsMapLocation("TrainerHill_Roof_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 6
        HnsMapLocation("TrainerHill_Elevator_hns", "MAPSEC_TRAINER_HILL", RegionId.JOHTO), // mapNum 7
        HnsMapLocation("SSAqua_1F_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 8
        HnsMapLocation("SSAqua_B1F_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 9
        HnsMapLocation("SSAqua_CaptainsRoom_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 10
        HnsMapLocation("SSAqua_PlayersRoom_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 11
        HnsMapLocation("SSAqua_RoomNW_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 12
        HnsMapLocation("SSAqua_RoomNE_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 13
        HnsMapLocation("SSAqua_RoomNNE_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 14
        HnsMapLocation("SSAqua_RoomSSW_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 15
        HnsMapLocation("SSAqua_RoomSSE_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 16
        HnsMapLocation("SSAqua_RoomSE_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 17
        HnsMapLocation("SSAqua_RoomSW_hns", "MAPSEC_SS_AQUA", RegionId.JOHTO), // mapNum 18
        HnsMapLocation("SafariZone_Top_Left_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 19
        HnsMapLocation("SafariZone_Low_Mid_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 20
        HnsMapLocation("SafariZone_Enterance_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 21
        HnsMapLocation("SafariZone_Low_Left_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 22
        HnsMapLocation("SafariZone_Low_Right_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 23
        HnsMapLocation("SafariZone_Top_Mid_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 24
        HnsMapLocation("SafariZone_Top_Right_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 25
        HnsMapLocation("Trees_hns", "MAPSEC_ABANDONED_SHIP", RegionId.JOHTO), // mapNum 26
        HnsMapLocation("TestMap1_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO), // mapNum 27
        HnsMapLocation("TestMap2_hns", "MAPSEC_NEW_BARK_TOWN", RegionId.JOHTO), // mapNum 28
        HnsMapLocation("SafariZone1_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 29
        HnsMapLocation("SafariZone2_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 30
        HnsMapLocation("SafariZone3_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 31
        HnsMapLocation("SafariZoneIndoor_hns", "MAPSEC_SAFARI_ZONE_GATE", RegionId.JOHTO), // mapNum 32
        HnsMapLocation("Saffron_Temp_hns", "MAPSEC_ABANDONED_SHIP", RegionId.JOHTO), // mapNum 33
        HnsMapLocation("SouthernIsland_Exterior_hns", "MAPSEC_SOUTHERN_ISLAND", null), // mapNum 34
        HnsMapLocation("SouthernIsland_Interior_hns", "MAPSEC_SOUTHERN_ISLAND", null), // mapNum 35
        HnsMapLocation("FarawayIsland_Entrance_hns", "MAPSEC_FARAWAY_ISLAND", null), // mapNum 36
        HnsMapLocation("FarawayIsland_Interior_hns", "MAPSEC_FARAWAY_ISLAND", null), // mapNum 37
        HnsMapLocation("BirthIsland_Exterior_hns", "MAPSEC_BIRTH_ISLAND", null), // mapNum 38
        HnsMapLocation("BirthIsland_Harbor_hns", "MAPSEC_BIRTH_ISLAND", null), // mapNum 39
        HnsMapLocation("BattleFrontier_OutsideWest_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 40
        HnsMapLocation("BattleFrontier_BattleTowerLobby_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 41
        HnsMapLocation("BattleFrontier_BattleTowerElevator_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 42
        HnsMapLocation("BattleFrontier_BattleTowerCorridor_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 43
        HnsMapLocation("BattleFrontier_BattleTowerBattleRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 44
        HnsMapLocation("BattleFrontier_OutsideEast_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 45
        HnsMapLocation("BattleFrontier_BattleTowerMultiPartnerRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 46
        HnsMapLocation("BattleFrontier_BattleTowerMultiCorridor_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 47
        HnsMapLocation("BattleFrontier_BattleTowerMultiBattleRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 48
        HnsMapLocation("BattleFrontier_BattleDomeLobby_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 49
        HnsMapLocation("BattleFrontier_BattleDomeCorridor_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 50
        HnsMapLocation("BattleFrontier_BattleDomePreBattleRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 51
        HnsMapLocation("BattleFrontier_BattleDomeBattleRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 52
        HnsMapLocation("BattleFrontier_BattlePalaceLobby_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 53
        HnsMapLocation("BattleFrontier_BattlePalaceCorridor_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 54
        HnsMapLocation("BattleFrontier_BattlePalaceBattleRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 55
        HnsMapLocation("BattleFrontier_BattlePyramidLobby_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 56
        HnsMapLocation("BattleFrontier_BattlePyramidFloor_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 57
        HnsMapLocation("BattleFrontier_BattlePyramidTop_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 58
        HnsMapLocation("BattleFrontier_BattleArenaLobby_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 59
        HnsMapLocation("BattleFrontier_BattleArenaCorridor_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 60
        HnsMapLocation("BattleFrontier_BattleArenaBattleRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 61
        HnsMapLocation("BattleFrontier_BattleFactoryLobby_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 62
        HnsMapLocation("BattleFrontier_BattleFactoryPreBattleRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 63
        HnsMapLocation("BattleFrontier_BattleFactoryBattleRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 64
        HnsMapLocation("BattleFrontier_BattlePikeLobby_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 65
        HnsMapLocation("BattleFrontier_BattlePikeCorridor_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 66
        HnsMapLocation("BattleFrontier_BattlePikeThreePathRoom_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 67
        HnsMapLocation("BattleFrontier_BattlePikeRoomNormal_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 68
        HnsMapLocation("BattleFrontier_BattlePikeRoomFinal_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 69
        HnsMapLocation("BattleFrontier_BattlePikeRoomWildMons_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 70
        HnsMapLocation("BattleFrontier_RankingHall_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 71
        HnsMapLocation("BattleFrontier_Lounge1_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 72
        HnsMapLocation("BattleFrontier_ExchangeServiceCorner_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 73
        HnsMapLocation("BattleFrontier_Lounge2_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 74
        HnsMapLocation("BattleFrontier_Lounge3_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 75
        HnsMapLocation("BattleFrontier_Lounge4_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 76
        HnsMapLocation("BattleFrontier_ScottsHouse_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 77
        HnsMapLocation("BattleFrontier_Lounge5_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 78
        HnsMapLocation("BattleFrontier_Lounge6_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 79
        HnsMapLocation("BattleFrontier_Lounge7_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 80
        HnsMapLocation("BattleFrontier_ReceptionGate_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 81
        HnsMapLocation("BattleFrontier_Lounge8_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 82
        HnsMapLocation("BattleFrontier_Lounge9_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 83
        HnsMapLocation("BattleFrontier_PokemonCenter_1F_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 84
        HnsMapLocation("BattleFrontier_PokemonCenter_2F_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 85
        HnsMapLocation("BattleFrontier_Mart_hns", "MAPSEC_BATTLE_FRONTIER", null), // mapNum 86
        HnsMapLocation("ContestHall_hns", "MAPSEC_DYNAMIC", null), // mapNum 87
        HnsMapLocation("ContestHallBeauty_hns", "MAPSEC_DYNAMIC", null), // mapNum 88
        HnsMapLocation("ContestHallCool_hns", "MAPSEC_DYNAMIC", null), // mapNum 89
        HnsMapLocation("ContestHallCute_hns", "MAPSEC_DYNAMIC", null), // mapNum 90
        HnsMapLocation("ContestHallSmart_hns", "MAPSEC_DYNAMIC", null), // mapNum 91
        HnsMapLocation("ContestHallTough_hns", "MAPSEC_DYNAMIC", null), // mapNum 92
        HnsMapLocation("LilycoveCity_ContestHall_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO), // mapNum 93
        HnsMapLocation("LilycoveCity_ContestLobby_hns", "MAPSEC_VIRIDIAN_CITY", RegionId.KANTO), // mapNum 94
        HnsMapLocation("UnusedContestHall1_hns", "MAPSEC_DYNAMIC", null), // mapNum 95
        HnsMapLocation("UnusedContestHall2_hns", "MAPSEC_DYNAMIC", null), // mapNum 96
        HnsMapLocation("UnusedContestHall3_hns", "MAPSEC_DYNAMIC", null), // mapNum 97
        HnsMapLocation("UnusedContestHall4_hns", "MAPSEC_DYNAMIC", null), // mapNum 98
        HnsMapLocation("UnusedContestHall5_hns", "MAPSEC_DYNAMIC", null), // mapNum 99
        HnsMapLocation("UnusedContestHall6_hns", "MAPSEC_DYNAMIC", null), // mapNum 100
        HnsMapLocation("TradeCenter_hns", "MAPSEC_DYNAMIC", null), // mapNum 101
        HnsMapLocation("BattleColosseum_2P_hns", "MAPSEC_DYNAMIC", null), // mapNum 102
        HnsMapLocation("FuchsiaCity_SafariZoneEntrance_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 103
        HnsMapLocation("FuchsiaCity_SafariZoneBeach_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 104
        HnsMapLocation("FuchsiaCity_SafariZoneBrush_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 105
        HnsMapLocation("FuchsiaCity_SafariZoneMountain_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 106
        HnsMapLocation("FuchsiaCity_SafariZoneCave_hns", "MAPSEC_FUCHSIA_CITY", RegionId.KANTO), // mapNum 107
    )

    private fun group56(): List<HnsMapLocation?> = listOf(
        null, // mapNum 0: not a Heart & Soul map
        null, // mapNum 1: not a Heart & Soul map
        null, // mapNum 2: not a Heart & Soul map
        null, // mapNum 3: not a Heart & Soul map
        null, // mapNum 4: not a Heart & Soul map
        null, // mapNum 5: not a Heart & Soul map
        null, // mapNum 6: not a Heart & Soul map
        null, // mapNum 7: not a Heart & Soul map
        null, // mapNum 8: not a Heart & Soul map
        null, // mapNum 9: not a Heart & Soul map
        null, // mapNum 10: not a Heart & Soul map
        null, // mapNum 11: not a Heart & Soul map
        null, // mapNum 12: not a Heart & Soul map
        null, // mapNum 13: not a Heart & Soul map
        null, // mapNum 14: not a Heart & Soul map
        null, // mapNum 15: not a Heart & Soul map
        null, // mapNum 16: not a Heart & Soul map
        null, // mapNum 17: not a Heart & Soul map
        null, // mapNum 18: not a Heart & Soul map
        null, // mapNum 19: not a Heart & Soul map
        null, // mapNum 20: not a Heart & Soul map
        null, // mapNum 21: not a Heart & Soul map
        null, // mapNum 22: not a Heart & Soul map
        null, // mapNum 23: not a Heart & Soul map
        null, // mapNum 24: not a Heart & Soul map
        null, // mapNum 25: not a Heart & Soul map
        null, // mapNum 26: not a Heart & Soul map
        null, // mapNum 27: not a Heart & Soul map
        null, // mapNum 28: not a Heart & Soul map
        null, // mapNum 29: not a Heart & Soul map
        null, // mapNum 30: not a Heart & Soul map
        null, // mapNum 31: not a Heart & Soul map
        null, // mapNum 32: not a Heart & Soul map
        null, // mapNum 33: not a Heart & Soul map
        null, // mapNum 34: not a Heart & Soul map
        null, // mapNum 35: not a Heart & Soul map
        null, // mapNum 36: not a Heart & Soul map
        null, // mapNum 37: not a Heart & Soul map
        null, // mapNum 38: not a Heart & Soul map
        null, // mapNum 39: not a Heart & Soul map
        null, // mapNum 40: not a Heart & Soul map
        null, // mapNum 41: not a Heart & Soul map
        null, // mapNum 42: not a Heart & Soul map
        null, // mapNum 43: not a Heart & Soul map
        null, // mapNum 44: not a Heart & Soul map
        null, // mapNum 45: not a Heart & Soul map
        null, // mapNum 46: not a Heart & Soul map
        null, // mapNum 47: not a Heart & Soul map
        null, // mapNum 48: not a Heart & Soul map
        null, // mapNum 49: not a Heart & Soul map
        null, // mapNum 50: not a Heart & Soul map
        null, // mapNum 51: not a Heart & Soul map
        null, // mapNum 52: not a Heart & Soul map
        null, // mapNum 53: not a Heart & Soul map
        null, // mapNum 54: not a Heart & Soul map
        null, // mapNum 55: not a Heart & Soul map
        null, // mapNum 56: not a Heart & Soul map
        null, // mapNum 57: not a Heart & Soul map
        null, // mapNum 58: not a Heart & Soul map
        null, // mapNum 59: not a Heart & Soul map
        null, // mapNum 60: not a Heart & Soul map
        HnsMapLocation("BattlePyramidSquare01_hns", "MAPSEC_DYNAMIC", null), // mapNum 61
        HnsMapLocation("BattlePyramidSquare02_hns", "MAPSEC_DYNAMIC", null), // mapNum 62
        HnsMapLocation("BattlePyramidSquare03_hns", "MAPSEC_DYNAMIC", null), // mapNum 63
        HnsMapLocation("BattlePyramidSquare04_hns", "MAPSEC_DYNAMIC", null), // mapNum 64
        HnsMapLocation("BattlePyramidSquare05_hns", "MAPSEC_DYNAMIC", null), // mapNum 65
        HnsMapLocation("BattlePyramidSquare06_hns", "MAPSEC_DYNAMIC", null), // mapNum 66
        HnsMapLocation("BattlePyramidSquare07_hns", "MAPSEC_DYNAMIC", null), // mapNum 67
        HnsMapLocation("BattlePyramidSquare08_hns", "MAPSEC_DYNAMIC", null), // mapNum 68
        HnsMapLocation("BattlePyramidSquare09_hns", "MAPSEC_DYNAMIC", null), // mapNum 69
        HnsMapLocation("BattlePyramidSquare10_hns", "MAPSEC_DYNAMIC", null), // mapNum 70
        HnsMapLocation("BattlePyramidSquare11_hns", "MAPSEC_DYNAMIC", null), // mapNum 71
        HnsMapLocation("BattlePyramidSquare12_hns", "MAPSEC_DYNAMIC", null), // mapNum 72
        HnsMapLocation("BattlePyramidSquare13_hns", "MAPSEC_DYNAMIC", null), // mapNum 73
        HnsMapLocation("BattlePyramidSquare14_hns", "MAPSEC_DYNAMIC", null), // mapNum 74
        HnsMapLocation("BattlePyramidSquare15_hns", "MAPSEC_DYNAMIC", null), // mapNum 75
        HnsMapLocation("BattlePyramidSquare16_hns", "MAPSEC_DYNAMIC", null), // mapNum 76
    )

    /** Resolve a raw mapGroup/mapNum pair, or null when H&S defines no such map. */
    fun findLocation(mapGroup: Int, mapNum: Int): HnsMapLocation? {
        if (mapGroup < 0 || mapNum < 0) return null
        return locationGroups[mapGroup]?.getOrNull(mapNum)
    }

    fun findSection(sectionId: String): HnsMapSection? = sectionsById[sectionId]
}
