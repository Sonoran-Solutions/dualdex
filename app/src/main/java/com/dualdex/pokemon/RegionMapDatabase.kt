package com.dualdex.pokemon

/**
 * Comprehensive Region Map and Town Map database for Pokémon Heart & Soul (Johto/Kanto),
 * Emerald (Hoenn), and FireRed (Kanto).
 */
object RegionMapDatabase {

    val JOHTO_SECTIONS: Map<String, RegionMapSection> = mapOf(
        "MAPSEC_VIOLET_CITY" to RegionMapSection(
            id = "MAPSEC_VIOLET_CITY",
            name = "Violet City",
            region = RegionId.JOHTO,
            gridX = 12,
            gridY = 4,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "The City of Nostalgic Scents.",
            landmarks = listOf("Violet Gym", "Sprout Tower", "Earl's Pokémon Academy"),
            gymLeader = "Falkner (Flying)",
            badge = "Zephyr Badge",
            connections = listOf("Route 31", "Route 32", "Route 36")
        ),
        "MAPSEC_AZALEA_TOWN" to RegionMapSection(
            id = "MAPSEC_AZALEA_TOWN",
            name = "Azalea Town",
            region = RegionId.JOHTO,
            gridX = 10,
            gridY = 13,
            width = 1,
            height = 1,
            nodeType = MapNodeType.TOWN,
            description = "Where People and Pokémon Live in Happy Harmony.",
            landmarks = listOf("Azalea Gym", "Slowpoke Well", "Kurt's House", "Charcoal Kiln"),
            gymLeader = "Bugsy (Bug)",
            badge = "Hive Badge",
            connections = listOf("Route 33", "Ilex Forest")
        ),
        "MAPSEC_GOLDENROD_CITY" to RegionMapSection(
            id = "MAPSEC_GOLDENROD_CITY",
            name = "Goldenrod City",
            region = RegionId.JOHTO,
            gridX = 8,
            gridY = 8,
            width = 1,
            height = 2,
            nodeType = MapNodeType.CITY,
            description = "A Happening Big City.",
            landmarks = listOf("Goldenrod Gym", "Radio Tower", "Goldenrod Dept. Store", "Game Corner", "Magnet Train Station", "Global Terminal", "Name Rater", "Bike Shop"),
            gymLeader = "Whitney (Normal)",
            badge = "Plain Badge",
            connections = listOf("Route 34", "Route 35")
        ),
        "MAPSEC_ECRUTEAK_CITY" to RegionMapSection(
            id = "MAPSEC_ECRUTEAK_CITY",
            name = "Ecruteak City",
            region = RegionId.JOHTO,
            gridX = 10,
            gridY = 2,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "A Historical City Where the Past Meets the Present.",
            landmarks = listOf("Ecruteak Gym", "Burned Tower", "Bell Tower (Tin Tower)", "Ecruteak Dance Theater"),
            gymLeader = "Morty (Ghost)",
            badge = "Fog Badge",
            connections = listOf("Route 37", "Route 38", "Route 42")
        ),
        "MAPSEC_OLIVINE_CITY" to RegionMapSection(
            id = "MAPSEC_OLIVINE_CITY",
            name = "Olivine City",
            region = RegionId.JOHTO,
            gridX = 6,
            gridY = 4,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "The Port Closest to Foreign Lands.",
            landmarks = listOf("Olivine Gym", "Glitter Lighthouse", "Olivine Port (S.S. Aqua)"),
            gymLeader = "Jasmine (Steel)",
            badge = "Mineral Badge",
            connections = listOf("Route 39", "Route 40")
        ),
        "MAPSEC_CIANWOOD_CITY" to RegionMapSection(
            id = "MAPSEC_CIANWOOD_CITY",
            name = "Cianwood City",
            region = RegionId.JOHTO,
            gridX = 4,
            gridY = 11,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "A Port Surrounded by Rough Seas.",
            landmarks = listOf("Cianwood Gym", "500-Year-Old Pharmacy", "Kirk's House", "Photo Studio"),
            gymLeader = "Chuck (Fighting)",
            badge = "Storm Badge",
            connections = listOf("Route 41", "Cliff Edge Gate")
        ),
        "MAPSEC_SAFARI_ZONE_GATE" to RegionMapSection(
            id = "MAPSEC_SAFARI_ZONE_GATE",
            name = "Safari Zone Gate",
            region = RegionId.JOHTO,
            gridX = 2,
            gridY = 9,
            width = 1,
            height = 1,
            nodeType = MapNodeType.FACILITY,
            description = "Gateway to the vast wilderness of the Johto Safari Zone.",
            landmarks = listOf("Safari Zone Warden Baoba", "Safari Stalls"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 48")
        ),
        "MAPSEC_MAHOGANY_TOWN" to RegionMapSection(
            id = "MAPSEC_MAHOGANY_TOWN",
            name = "Mahogany Town",
            region = RegionId.JOHTO,
            gridX = 15,
            gridY = 2,
            width = 1,
            height = 1,
            nodeType = MapNodeType.TOWN,
            description = "Welcome to the Home of the Ninja!",
            landmarks = listOf("Mahogany Gym", "Team Rocket Secret Hideout", "Souvenir Shop"),
            gymLeader = "Pryce (Ice)",
            badge = "Glacier Badge",
            connections = listOf("Route 42", "Route 43", "Route 44")
        ),
        "MAPSEC_BLACKTHORN_CITY" to RegionMapSection(
            id = "MAPSEC_BLACKTHORN_CITY",
            name = "Blackthorn City",
            region = RegionId.JOHTO,
            gridX = 18,
            gridY = 2,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "A Quiet Mountain Retreat.",
            landmarks = listOf("Blackthorn Gym", "Dragon's Den", "Move Deleter & Reminder's House"),
            gymLeader = "Clair (Dragon)",
            badge = "Rising Badge",
            connections = listOf("Route 45", "Ice Path")
        ),
        "MAPSEC_CHERRYGROVE_CITY" to RegionMapSection(
            id = "MAPSEC_CHERRYGROVE_CITY",
            name = "Cherrygrove City",
            region = RegionId.JOHTO,
            gridX = 14,
            gridY = 10,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "The City of Fragrant Flowers.",
            landmarks = listOf("Guide Gent's House", "Poké Mart", "Pokémon Center"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 29", "Route 30")
        ),
        "MAPSEC_INDIGO_PLATEAU" to RegionMapSection(
            id = "MAPSEC_INDIGO_PLATEAU",
            name = "Indigo Plateau",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 3,
            width = 1,
            height = 2,
            nodeType = MapNodeType.LANDMARK,
            description = "The ultimate destination for Pokémon Trainers.",
            landmarks = listOf("Pokémon League Headquarters", "Elite Four", "Champion's Chamber"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 23", "Victory Road")
        ),
        "MAPSEC_ROUTE_26" to RegionMapSection(
            id = "MAPSEC_ROUTE_26",
            name = "Route 26",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 6,
            width = 1,
            height = 5,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_27" to RegionMapSection(
            id = "MAPSEC_ROUTE_27",
            name = "Route 27",
            region = RegionId.JOHTO,
            gridX = 20,
            gridY = 10,
            width = 4,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_28" to RegionMapSection(
            id = "MAPSEC_ROUTE_28",
            name = "Route 28",
            region = RegionId.JOHTO,
            gridX = 21,
            gridY = 5,
            width = 3,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_29" to RegionMapSection(
            id = "MAPSEC_ROUTE_29",
            name = "Route 29",
            region = RegionId.JOHTO,
            gridX = 10,
            gridY = 11,
            width = 3,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_30" to RegionMapSection(
            id = "MAPSEC_ROUTE_30",
            name = "Route 30",
            region = RegionId.JOHTO,
            gridX = 9,
            gridY = 6,
            width = 1,
            height = 5,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_31" to RegionMapSection(
            id = "MAPSEC_ROUTE_31",
            name = "Route 31",
            region = RegionId.JOHTO,
            gridX = 8,
            gridY = 5,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_32" to RegionMapSection(
            id = "MAPSEC_ROUTE_32",
            name = "Route 32",
            region = RegionId.JOHTO,
            gridX = 7,
            gridY = 6,
            width = 1,
            height = 5,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_33" to RegionMapSection(
            id = "MAPSEC_ROUTE_33",
            name = "Route 33",
            region = RegionId.JOHTO,
            gridX = 7,
            gridY = 12,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_34" to RegionMapSection(
            id = "MAPSEC_ROUTE_34",
            name = "Route 34",
            region = RegionId.JOHTO,
            gridX = 5,
            gridY = 9,
            width = 1,
            height = 2,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_35" to RegionMapSection(
            id = "MAPSEC_ROUTE_35",
            name = "Route 35",
            region = RegionId.JOHTO,
            gridX = 5,
            gridY = 6,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_36" to RegionMapSection(
            id = "MAPSEC_ROUTE_36",
            name = "Route 36",
            region = RegionId.JOHTO,
            gridX = 6,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_37" to RegionMapSection(
            id = "MAPSEC_ROUTE_37",
            name = "Route 37",
            region = RegionId.JOHTO,
            gridX = 6,
            gridY = 4,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_38" to RegionMapSection(
            id = "MAPSEC_ROUTE_38",
            name = "Route 38",
            region = RegionId.JOHTO,
            gridX = 7,
            gridY = 2,
            width = 3,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_39" to RegionMapSection(
            id = "MAPSEC_ROUTE_39",
            name = "Route 39",
            region = RegionId.JOHTO,
            gridX = 6,
            gridY = 2,
            width = 1,
            height = 2,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_40" to RegionMapSection(
            id = "MAPSEC_ROUTE_40",
            name = "Route 40",
            region = RegionId.JOHTO,
            gridX = 5,
            gridY = 4,
            width = 1,
            height = 5,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_41" to RegionMapSection(
            id = "MAPSEC_ROUTE_41",
            name = "Route 41",
            region = RegionId.JOHTO,
            gridX = 5,
            gridY = 9,
            width = 1,
            height = 3,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_42" to RegionMapSection(
            id = "MAPSEC_ROUTE_42",
            name = "Route 42",
            region = RegionId.JOHTO,
            gridX = 11,
            gridY = 2,
            width = 4,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_43" to RegionMapSection(
            id = "MAPSEC_ROUTE_43",
            name = "Route 43",
            region = RegionId.JOHTO,
            gridX = 15,
            gridY = 1,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_44" to RegionMapSection(
            id = "MAPSEC_ROUTE_44",
            name = "Route 44",
            region = RegionId.JOHTO,
            gridX = 16,
            gridY = 2,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_45" to RegionMapSection(
            id = "MAPSEC_ROUTE_45",
            name = "Route 45",
            region = RegionId.JOHTO,
            gridX = 18,
            gridY = 3,
            width = 1,
            height = 4,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_46" to RegionMapSection(
            id = "MAPSEC_ROUTE_46",
            name = "Route 46",
            region = RegionId.JOHTO,
            gridX = 17,
            gridY = 6,
            width = 1,
            height = 4,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_47" to RegionMapSection(
            id = "MAPSEC_ROUTE_47",
            name = "Route 47",
            region = RegionId.JOHTO,
            gridX = 2,
            gridY = 11,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_48" to RegionMapSection(
            id = "MAPSEC_ROUTE_48",
            name = "Route 48",
            region = RegionId.JOHTO,
            gridX = 2,
            gridY = 10,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_SAFARI_ZONE" to RegionMapSection(
            id = "MAPSEC_SAFARI_ZONE",
            name = "Safari Zone",
            region = RegionId.JOHTO,
            gridX = 0,
            gridY = 6,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_VICTORY_ROAD" to RegionMapSection(
            id = "MAPSEC_VICTORY_ROAD",
            name = "Victory Road",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_PALLET_TOWN" to RegionMapSection(
            id = "MAPSEC_PALLET_TOWN",
            name = "Pallet Town",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 11,
            width = 1,
            height = 1,
            nodeType = MapNodeType.TOWN,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_VIRIDIAN_CITY" to RegionMapSection(
            id = "MAPSEC_VIRIDIAN_CITY",
            name = "Viridian City",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 7,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_PEWTER_CITY" to RegionMapSection(
            id = "MAPSEC_PEWTER_CITY",
            name = "Pewter City",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 2,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_CERULEAN_CITY" to RegionMapSection(
            id = "MAPSEC_CERULEAN_CITY",
            name = "Cerulean City",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 2,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_LAVENDER_TOWN" to RegionMapSection(
            id = "MAPSEC_LAVENDER_TOWN",
            name = "Lavender Town",
            region = RegionId.JOHTO,
            gridX = 27,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.TOWN,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_VERMILION_CITY" to RegionMapSection(
            id = "MAPSEC_VERMILION_CITY",
            name = "Vermilion City",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 7,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_CELADON_CITY" to RegionMapSection(
            id = "MAPSEC_CELADON_CITY",
            name = "Celadon City",
            region = RegionId.JOHTO,
            gridX = 22,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_FUCHSIA_CITY" to RegionMapSection(
            id = "MAPSEC_FUCHSIA_CITY",
            name = "Fuchsia City",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 11,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_SOUTHERN_ISLAND" to RegionMapSection(
            id = "MAPSEC_SOUTHERN_ISLAND",
            name = "Southern Island",
            region = RegionId.JOHTO,
            gridX = 9,
            gridY = 14,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_FARAWAY_ISLAND" to RegionMapSection(
            id = "MAPSEC_FARAWAY_ISLAND",
            name = "Faraway Island",
            region = RegionId.JOHTO,
            gridX = 9,
            gridY = 14,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_BIRTH_ISLAND" to RegionMapSection(
            id = "MAPSEC_BIRTH_ISLAND",
            name = "Birth Island",
            region = RegionId.JOHTO,
            gridX = 9,
            gridY = 14,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_CINNABAR_ISLAND" to RegionMapSection(
            id = "MAPSEC_CINNABAR_ISLAND",
            name = "Cinnabar Island",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 13,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_SAFFRON_CITY" to RegionMapSection(
            id = "MAPSEC_SAFFRON_CITY",
            name = "Saffron City",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_10_POKECENTER" to RegionMapSection(
            id = "MAPSEC_ROUTE_10_POKECENTER",
            name = "Route 10",
            region = RegionId.JOHTO,
            gridX = 27,
            gridY = 3,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_1" to RegionMapSection(
            id = "MAPSEC_ROUTE_1",
            name = "Route 1",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 8,
            width = 1,
            height = 3,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_2" to RegionMapSection(
            id = "MAPSEC_ROUTE_2",
            name = "Route 2",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 3,
            width = 1,
            height = 4,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_3" to RegionMapSection(
            id = "MAPSEC_ROUTE_3",
            name = "Route 3",
            region = RegionId.JOHTO,
            gridX = 20,
            gridY = 2,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_4" to RegionMapSection(
            id = "MAPSEC_ROUTE_4",
            name = "Route 4",
            region = RegionId.JOHTO,
            gridX = 22,
            gridY = 2,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_5" to RegionMapSection(
            id = "MAPSEC_ROUTE_5",
            name = "Route 5",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 3,
            width = 1,
            height = 2,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_6" to RegionMapSection(
            id = "MAPSEC_ROUTE_6",
            name = "Route 6",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 6,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_7" to RegionMapSection(
            id = "MAPSEC_ROUTE_7",
            name = "Route 7",
            region = RegionId.JOHTO,
            gridX = 23,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_8" to RegionMapSection(
            id = "MAPSEC_ROUTE_8",
            name = "Route 8",
            region = RegionId.JOHTO,
            gridX = 25,
            gridY = 5,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_9" to RegionMapSection(
            id = "MAPSEC_ROUTE_9",
            name = "Route 9",
            region = RegionId.JOHTO,
            gridX = 25,
            gridY = 2,
            width = 3,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_10" to RegionMapSection(
            id = "MAPSEC_ROUTE_10",
            name = "Route 10",
            region = RegionId.JOHTO,
            gridX = 27,
            gridY = 3,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_11" to RegionMapSection(
            id = "MAPSEC_ROUTE_11",
            name = "Route 11",
            region = RegionId.JOHTO,
            gridX = 25,
            gridY = 5,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_12" to RegionMapSection(
            id = "MAPSEC_ROUTE_12",
            name = "Route 12",
            region = RegionId.JOHTO,
            gridX = 27,
            gridY = 6,
            width = 1,
            height = 4,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_13" to RegionMapSection(
            id = "MAPSEC_ROUTE_13",
            name = "Route 13",
            region = RegionId.JOHTO,
            gridX = 26,
            gridY = 10,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_14" to RegionMapSection(
            id = "MAPSEC_ROUTE_14",
            name = "Route 14",
            region = RegionId.JOHTO,
            gridX = 26,
            gridY = 11,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_15" to RegionMapSection(
            id = "MAPSEC_ROUTE_15",
            name = "Route 15",
            region = RegionId.JOHTO,
            gridX = 25,
            gridY = 11,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_16" to RegionMapSection(
            id = "MAPSEC_ROUTE_16",
            name = "Route 16",
            region = RegionId.JOHTO,
            gridX = 21,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_17" to RegionMapSection(
            id = "MAPSEC_ROUTE_17",
            name = "Route 17",
            region = RegionId.JOHTO,
            gridX = 21,
            gridY = 6,
            width = 1,
            height = 6,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_18" to RegionMapSection(
            id = "MAPSEC_ROUTE_18",
            name = "Route 18",
            region = RegionId.JOHTO,
            gridX = 22,
            gridY = 11,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_19" to RegionMapSection(
            id = "MAPSEC_ROUTE_19",
            name = "Route 19",
            region = RegionId.JOHTO,
            gridX = 22,
            gridY = 12,
            width = 3,
            height = 2,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_20" to RegionMapSection(
            id = "MAPSEC_ROUTE_20",
            name = "Route 20",
            region = RegionId.JOHTO,
            gridX = 20,
            gridY = 13,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_21" to RegionMapSection(
            id = "MAPSEC_ROUTE_21",
            name = "Route 21",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 12,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_22" to RegionMapSection(
            id = "MAPSEC_ROUTE_22",
            name = "Route 22",
            region = RegionId.JOHTO,
            gridX = 17,
            gridY = 7,
            width = 2,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_24" to RegionMapSection(
            id = "MAPSEC_ROUTE_24",
            name = "Route 24",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 0,
            width = 1,
            height = 2,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROUTE_25" to RegionMapSection(
            id = "MAPSEC_ROUTE_25",
            name = "Route 25",
            region = RegionId.JOHTO,
            gridX = 25,
            gridY = 0,
            width = 1,
            height = 1,
            nodeType = MapNodeType.ROUTE,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_VIRIDIAN_FOREST" to RegionMapSection(
            id = "MAPSEC_VIRIDIAN_FOREST",
            name = "Viridian Forest",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 4,
            width = 1,
            height = 2,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_MT_MOON" to RegionMapSection(
            id = "MAPSEC_MT_MOON",
            name = "Mt. Moon",
            region = RegionId.JOHTO,
            gridX = 21,
            gridY = 2,
            width = 2,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_S_S_ANNE" to RegionMapSection(
            id = "MAPSEC_S_S_ANNE",
            name = "S.S. Anne",
            region = RegionId.JOHTO,
            gridX = 13,
            gridY = 15,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_UNDERGROUND_PATH_2" to RegionMapSection(
            id = "MAPSEC_UNDERGROUND_PATH_2",
            name = "Underground Path",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_DIGLETTS_CAVE" to RegionMapSection(
            id = "MAPSEC_DIGLETTS_CAVE",
            name = "Diglett'S Cave",
            region = RegionId.JOHTO,
            gridX = 20,
            gridY = 3,
            width = 6,
            height = 4,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROCKET_HIDEOUT" to RegionMapSection(
            id = "MAPSEC_ROCKET_HIDEOUT",
            name = "Rocket Hideout",
            region = RegionId.JOHTO,
            gridX = 9,
            gridY = 3,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_SILPH_CO" to RegionMapSection(
            id = "MAPSEC_SILPH_CO",
            name = "Silph Co.",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_ROCK_TUNNEL" to RegionMapSection(
            id = "MAPSEC_ROCK_TUNNEL",
            name = "Rock Tunnel",
            region = RegionId.JOHTO,
            gridX = 27,
            gridY = 3,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_SEAFOAM_ISLANDS" to RegionMapSection(
            id = "MAPSEC_SEAFOAM_ISLANDS",
            name = "Seafoam Islands",
            region = RegionId.JOHTO,
            gridX = 22,
            gridY = 13,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_POKEMON_TOWER" to RegionMapSection(
            id = "MAPSEC_POKEMON_TOWER",
            name = "Pokémon Tower",
            region = RegionId.JOHTO,
            gridX = 27,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_CERULEAN_CAVE" to RegionMapSection(
            id = "MAPSEC_CERULEAN_CAVE",
            name = "Cerulean Cave",
            region = RegionId.JOHTO,
            gridX = 23,
            gridY = 1,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_POWER_PLANT" to RegionMapSection(
            id = "MAPSEC_POWER_PLANT",
            name = "Power Plant",
            region = RegionId.JOHTO,
            gridX = 27,
            gridY = 3,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_NEW_BARK_TOWN" to RegionMapSection(
            id = "MAPSEC_NEW_BARK_TOWN",
            name = "New Bark Town",
            region = RegionId.JOHTO,
            gridX = 19,
            gridY = 10,
            width = 1,
            height = 1,
            nodeType = MapNodeType.TOWN,
            description = "The Town Where the Winds of a New Beginning Blow.",
            landmarks = listOf("Prof. Elm's Pokémon Lab", "Player's House", "Ethan / Lyra's House"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 29")
        ),
        "MAPSEC_INDIGO_PLATEAU2" to RegionMapSection(
            id = "MAPSEC_INDIGO_PLATEAU2",
            name = "Indigo Plateau2",
            region = RegionId.JOHTO,
            gridX = 16,
            gridY = 2,
            width = 1,
            height = 2,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_UNDERGROUND_PATH" to RegionMapSection(
            id = "MAPSEC_UNDERGROUND_PATH",
            name = "Underground Path",
            region = RegionId.JOHTO,
            gridX = 24,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_DARK_CAVE" to RegionMapSection(
            id = "MAPSEC_DARK_CAVE",
            name = "Dark Cave",
            region = RegionId.JOHTO,
            gridX = 15,
            gridY = 4,
            width = 3,
            height = 2,
            nodeType = MapNodeType.DUNGEON,
            description = "A pitch-black cavern spanning between Route 31 and Blackthorn.",
            landmarks = listOf("Dunsparce, Wobbuffet, Teddiursa habitats"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 31", "Route 46")
        ),
        "MAPSEC_UNION_CAVE" to RegionMapSection(
            id = "MAPSEC_UNION_CAVE",
            name = "Union Cave",
            region = RegionId.JOHTO,
            gridX = 12,
            gridY = 11,
            width = 1,
            height = 2,
            nodeType = MapNodeType.DUNGEON,
            description = "A multi-level limestone cavern connecting Route 32 and Route 33.",
            landmarks = listOf("Friday Lapras spawn", "Ruins of Alph back exits"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 32", "Route 33")
        ),
        "MAPSEC_ILEX_FOREST" to RegionMapSection(
            id = "MAPSEC_ILEX_FOREST",
            name = "Ilex Forest",
            region = RegionId.JOHTO,
            gridX = 8,
            gridY = 12,
            width = 2,
            height = 2,
            nodeType = MapNodeType.DUNGEON,
            description = "A dense overgrown forest said to be protected by Celebi.",
            landmarks = listOf("Shrine of the Forest Guardian", "Headbutt Tutor", "Charcoal Apprentice"),
            gymLeader = null,
            badge = null,
            connections = listOf("Azalea Town", "Route 34")
        ),
        "MAPSEC_NATIONAL_PARK" to RegionMapSection(
            id = "MAPSEC_NATIONAL_PARK",
            name = "National Park",
            region = RegionId.JOHTO,
            gridX = 8,
            gridY = 4,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "A sprawling park featuring the famous Bug-Catching Contest.",
            landmarks = listOf("Bug-Catching Contest (Tue/Thu/Sat)", "Persian fountain"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 35", "Route 36")
        ),
        "MAPSEC_WHIRL_ISLANDS" to RegionMapSection(
            id = "MAPSEC_WHIRL_ISLANDS",
            name = "Whirl Islands",
            region = RegionId.JOHTO,
            gridX = 6,
            gridY = 11,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "Four mysterious islands surrounded by ferocious ocean whirlpools.",
            landmarks = listOf("Silver Wing chamber", "Lugia roost"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 41")
        ),
        "MAPSEC_CLIFF_CAVE" to RegionMapSection(
            id = "MAPSEC_CLIFF_CAVE",
            name = "Cliff Cave",
            region = RegionId.JOHTO,
            gridX = 3,
            gridY = 11,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_MT_MORTAR" to RegionMapSection(
            id = "MAPSEC_MT_MORTAR",
            name = "Mt Mortar",
            region = RegionId.JOHTO,
            gridX = 12,
            gridY = 1,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "A labyrinthine mountain cave with roaring waterfalls.",
            landmarks = listOf("Black Belt Kiyo (Tyrogue)", "Deep caverns"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 42")
        ),
        "MAPSEC_LAKE_OF_RAGE" to RegionMapSection(
            id = "MAPSEC_LAKE_OF_RAGE",
            name = "Lake Of Rage",
            region = RegionId.JOHTO,
            gridX = 15,
            gridY = 0,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "A tranquil lake flooded by seasonal rain and strange radio waves.",
            landmarks = listOf("Red Gyarados", "Lance encounter", "Lake souvenir house"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 43")
        ),
        "MAPSEC_ICE_PATH" to RegionMapSection(
            id = "MAPSEC_ICE_PATH",
            name = "Ice Path",
            region = RegionId.JOHTO,
            gridX = 17,
            gridY = 1,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "A treacherous icy cavern connecting Mahogany Town to Blackthorn.",
            landmarks = listOf("Swinub, Delibird, Sneasel habitats", "HM07 Waterfall"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 44", "Blackthorn City")
        ),
        "MAPSEC_MT_SILVER" to RegionMapSection(
            id = "MAPSEC_MT_SILVER",
            name = "Mt. Silver",
            region = RegionId.JOHTO,
            gridX = 14,
            gridY = 7,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "A desolate mountain peak where only the most seasoned trainers dare tread.",
            landmarks = listOf("Legendary Trainer Red", "Moltres chamber", "Larvitar habitat"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 28")
        ),
        "MAPSEC_TOHJO_FALLS" to RegionMapSection(
            id = "MAPSEC_TOHJO_FALLS",
            name = "Tohjo Falls",
            region = RegionId.JOHTO,
            gridX = 20,
            gridY = 9,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_SPROUT_TOWER" to RegionMapSection(
            id = "MAPSEC_SPROUT_TOWER",
            name = "Sprout Tower",
            region = RegionId.JOHTO,
            gridX = 12,
            gridY = 3,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_SLOWPOKE_WELL" to RegionMapSection(
            id = "MAPSEC_SLOWPOKE_WELL",
            name = "Slowpoke Well",
            region = RegionId.JOHTO,
            gridX = 10,
            gridY = 12,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "A damp cave where Slowpoke gather to drink.",
            landmarks = listOf("Team Rocket SlowpokeTail operation", "Kurt's rescue"),
            gymLeader = null,
            badge = null,
            connections = listOf("Azalea Town")
        ),
        "MAPSEC_BURNED_TOWER" to RegionMapSection(
            id = "MAPSEC_BURNED_TOWER",
            name = "Burned Tower",
            region = RegionId.JOHTO,
            gridX = 9,
            gridY = 1,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "Ruins of the Brass Tower destroyed by a mysterious lightning fire.",
            landmarks = listOf("Legendary Beasts (Raikou, Entei, Suicune)", "Eusine encounter"),
            gymLeader = null,
            badge = null,
            connections = listOf("Ecruteak City")
        ),
        "MAPSEC_TIN_TOWER" to RegionMapSection(
            id = "MAPSEC_TIN_TOWER",
            name = "Tin Tower",
            region = RegionId.JOHTO,
            gridX = 10,
            gridY = 1,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "A sacred pagoda reaching high above Ecruteak City.",
            landmarks = listOf("Rainbow Wing altar", "Ho-Oh roost"),
            gymLeader = null,
            badge = null,
            connections = listOf("Ecruteak City")
        ),
        "MAPSEC_DRAGONS_DEN" to RegionMapSection(
            id = "MAPSEC_DRAGONS_DEN",
            name = "Dragon'S Den",
            region = RegionId.JOHTO,
            gridX = 12,
            gridY = 2,
            width = 1,
            height = 1,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_RUINS_OF_ALPH" to RegionMapSection(
            id = "MAPSEC_RUINS_OF_ALPH",
            name = "Ruins Of Alph",
            region = RegionId.JOHTO,
            gridX = 6,
            gridY = 6,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "Ancient stone ruins holding the secrets of the Unown.",
            landmarks = listOf("Unown Research Center", "Stone sliding puzzles", "Chamber of Words"),
            gymLeader = null,
            badge = null,
            connections = listOf("Route 32", "Route 36")
        ),
        "MAPSEC_SS_AQUA" to RegionMapSection(
            id = "MAPSEC_SS_AQUA",
            name = "S.S. Aqua",
            region = RegionId.JOHTO,
            gridX = 14,
            gridY = 14,
            width = 10,
            height = 1,
            nodeType = MapNodeType.FACILITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_EMBEDDED_TOWER" to RegionMapSection(
            id = "MAPSEC_EMBEDDED_TOWER",
            name = "Embedded Tower",
            region = RegionId.JOHTO,
            gridX = 1,
            gridY = 10,
            width = 1,
            height = 2,
            nodeType = MapNodeType.DUNGEON,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_OLIVINE_LIGHTHOUSE" to RegionMapSection(
            id = "MAPSEC_OLIVINE_LIGHTHOUSE",
            name = "Lighthouse",
            region = RegionId.JOHTO,
            gridX = 7,
            gridY = 4,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_BATTLE_FRONTIER" to RegionMapSection(
            id = "MAPSEC_BATTLE_FRONTIER",
            name = "Battle Frontier",
            region = RegionId.JOHTO,
            gridX = 0,
            gridY = 17,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_FORTREE_CITY" to RegionMapSection(
            id = "MAPSEC_FORTREE_CITY",
            name = "Fortree City",
            region = RegionId.JOHTO,
            gridX = 1,
            gridY = 17,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_LILYCOVE_CITY" to RegionMapSection(
            id = "MAPSEC_LILYCOVE_CITY",
            name = "Contest Hall",
            region = RegionId.JOHTO,
            gridX = 2,
            gridY = 17,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_MOSSDEEP_CITY" to RegionMapSection(
            id = "MAPSEC_MOSSDEEP_CITY",
            name = "Mossdeep City",
            region = RegionId.JOHTO,
            gridX = 3,
            gridY = 17,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_SOOTOPOLIS_CITY" to RegionMapSection(
            id = "MAPSEC_SOOTOPOLIS_CITY",
            name = "Sootopolis City",
            region = RegionId.JOHTO,
            gridX = 3,
            gridY = 17,
            width = 1,
            height = 1,
            nodeType = MapNodeType.CITY,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
        "MAPSEC_TRAINER_HILL" to RegionMapSection(
            id = "MAPSEC_TRAINER_HILL",
            name = "Trainer Hill",
            region = RegionId.JOHTO,
            gridX = 2,
            gridY = 5,
            width = 1,
            height = 1,
            nodeType = MapNodeType.LANDMARK,
            description = "",
            landmarks = listOf(),
            gymLeader = null,
            badge = null,
            connections = listOf()
        ),
    )

    val JOHTO_DEFAULT: RegionMapSection = JOHTO_SECTIONS["MAPSEC_NEW_BARK_TOWN"]
        ?: RegionMapSection("MAPSEC_NEW_BARK_TOWN", "New Bark Town", RegionId.JOHTO, 19, 10, 1, 1, MapNodeType.TOWN, "The Town Where the Winds of a New Beginning Blow.")

    val HOENN_SECTIONS: Map<String, RegionMapSection> = mapOf(
        "LITTLEROOT_TOWN" to RegionMapSection("LITTLEROOT_TOWN", "Littleroot Town", RegionId.HOENN, 3, 11, 1, 1, MapNodeType.TOWN, "A town that cannot be shaded any hue.", listOf("Prof. Birch's Lab", "Player's House")),
        "OLDALE_TOWN" to RegionMapSection("OLDALE_TOWN", "Oldale Town", RegionId.HOENN, 3, 9, 1, 1, MapNodeType.TOWN, "Where things start off scarce.", listOf("Pok\u00e9 Mart", "Pok\u00e9mon Center")),
        "PETALBURG_CITY" to RegionMapSection("PETALBURG_CITY", "Petalburg City", RegionId.HOENN, 1, 9, 1, 1, MapNodeType.CITY, "Where people mingle with nature.", listOf("Petalburg Gym (Norman)", "Wally's House")),
        "RUSTBORO_CITY" to RegionMapSection("RUSTBORO_CITY", "Rustboro City", RegionId.HOENN, 0, 5, 1, 2, MapNodeType.CITY, "The city probing the integration of nature and science.", listOf("Rustboro Gym (Roxanne)", "Devon Corporation", "Pok\u00e9mon Trainer's School")),
        "DEWFORD_TOWN" to RegionMapSection("DEWFORD_TOWN", "Dewford Town", RegionId.HOENN, 2, 14, 1, 1, MapNodeType.TOWN, "A tiny island community in the azure sea.", listOf("Dewford Gym (Brawly)", "Granite Cave")),
        "SLATEPORT_CITY" to RegionMapSection("SLATEPORT_CITY", "Slateport City", RegionId.HOENN, 8, 10, 1, 2, MapNodeType.CITY, "The port where people and Pokémon travel across the sea.", listOf("Slateport Harbor", "Oceanic Museum", "Stern's Shipyard", "Contest Hall")),
        "MAUVILLE_CITY" to RegionMapSection("MAUVILLE_CITY", "Mauville City", RegionId.HOENN, 8, 6, 2, 1, MapNodeType.CITY, "The bright and bustling crossroads city.", listOf("Mauville Gym (Wattson)", "Game Corner", "Rydel's Cycles")),
        "VERDANTURF_TOWN" to RegionMapSection("VERDANTURF_TOWN", "Verdanturf Town", RegionId.HOENN, 4, 6, 1, 1, MapNodeType.TOWN, "The windswept highlands with sweet scents of grass.", listOf("Rusturf Tunnel", "Battle Tent")),
        "FALLARBOR_TOWN" to RegionMapSection("FALLARBOR_TOWN", "Fallarbor Town", RegionId.HOENN, 3, 2, 1, 1, MapNodeType.TOWN, "A farming community with volcanic ashes.", listOf("Move Tutor's House", "Prof. Cozmo's Lab")),
        "LAVARIDGE_TOWN" to RegionMapSection("LAVARIDGE_TOWN", "Lavaridge Town", RegionId.HOENN, 5, 3, 1, 1, MapNodeType.TOWN, "Pokémon Center Hot Springs!", listOf("Lavaridge Gym (Flannery)", "Herb Shop", "Hot Springs")),
        "FORTREE_CITY" to RegionMapSection("FORTREE_CITY", "Fortree City", RegionId.HOENN, 12, 0, 2, 1, MapNodeType.CITY, "The treetop city that frolics with nature.", listOf("Fortree Gym (Winona)", "Secret Base Guild")),
        "LILYCOVE_CITY" to RegionMapSection("LILYCOVE_CITY", "Lilycove City", RegionId.HOENN, 18, 3, 2, 1, MapNodeType.CITY, "Where the land ends and the sea begins.", listOf("Lilycove Dept. Store", "Art Museum", "Team Aqua / Magma Hideout")),
        "MOSSDEEP_CITY" to RegionMapSection("MOSSDEEP_CITY", "Mossdeep City", RegionId.HOENN, 24, 3, 2, 1, MapNodeType.CITY, "Our slogan: Cherish Pokémon.", listOf("Mossdeep Gym (Tate & Liza)", "Mossdeep Space Center")),
        "SOOTOPOLIS_CITY" to RegionMapSection("SOOTOPOLIS_CITY", "Sootopolis City", RegionId.HOENN, 21, 7, 1, 1, MapNodeType.CITY, "The mystical city slumbering in a crater.", listOf("Sootopolis Gym (Juan / Wallace)", "Cave of Origin")),
        "PACIFIDLOG_TOWN" to RegionMapSection("PACIFIDLOG_TOWN", "Pacifidlog Town", RegionId.HOENN, 17, 10, 1, 1, MapNodeType.TOWN, "The floating town on logs atop the sea.", listOf("Mirage Island access", "Floating Rafts")),
        "EVER_GRANDE_CITY" to RegionMapSection("EVER_GRANDE_CITY", "Ever Grande City", RegionId.HOENN, 27, 8, 1, 2, MapNodeType.CITY, "The paradise of flowers, the sea, and Pokémon.", listOf("Pok\u00e9mon League", "Victory Road")),
        "ROUTE_101" to RegionMapSection("ROUTE_101", "Route 101", RegionId.HOENN, 3, 10, 1, 1, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_102" to RegionMapSection("ROUTE_102", "Route 102", RegionId.HOENN, 2, 9, 1, 1, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_103" to RegionMapSection("ROUTE_103", "Route 103", RegionId.HOENN, 3, 8, 1, 1, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_104" to RegionMapSection("ROUTE_104", "Route 104", RegionId.HOENN, 0, 7, 1, 3, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_110" to RegionMapSection("ROUTE_110", "Route 110", RegionId.HOENN, 8, 8, 1, 2, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_111" to RegionMapSection("ROUTE_111", "Route 111", RegionId.HOENN, 8, 3, 1, 3, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_116" to RegionMapSection("ROUTE_116", "Route 116", RegionId.HOENN, 1, 5, 2, 1, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_119" to RegionMapSection("ROUTE_119", "Route 119", RegionId.HOENN, 11, 0, 1, 4, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_120" to RegionMapSection("ROUTE_120", "Route 120", RegionId.HOENN, 14, 2, 1, 2, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_121" to RegionMapSection("ROUTE_121", "Route 121", RegionId.HOENN, 15, 3, 3, 1, MapNodeType.ROUTE, "", listOf()),
    )

    val KANTO_SECTIONS: Map<String, RegionMapSection> = mapOf(
        "PALLET_TOWN" to RegionMapSection("PALLET_TOWN", "Pallet Town", RegionId.KANTO, 5, 11, 1, 1, MapNodeType.TOWN, "Shades of your journey await!", listOf("Prof. Oak's Lab", "Red's House")),
        "VIRIDIAN_CITY" to RegionMapSection("VIRIDIAN_CITY", "Viridian City", RegionId.KANTO, 5, 8, 1, 1, MapNodeType.CITY, "The eternally green paradise.", listOf("Viridian Gym", "Trainer Academy")),
        "PEWTER_CITY" to RegionMapSection("PEWTER_CITY", "Pewter City", RegionId.KANTO, 5, 3, 1, 1, MapNodeType.CITY, "Between rugged mountain peaks.", listOf("Pewter Gym (Brock)", "Museum of Science")),
        "CERULEAN_CITY" to RegionMapSection("CERULEAN_CITY", "Cerulean City", RegionId.KANTO, 15, 2, 1, 1, MapNodeType.CITY, "A mysterious, blue aura surrounds it.", listOf("Cerulean Gym (Misty)", "Bike Shop")),
        "VERMILION_CITY" to RegionMapSection("VERMILION_CITY", "Vermilion City", RegionId.KANTO, 15, 9, 1, 1, MapNodeType.CITY, "The port of exquisite sunsets.", listOf("Vermilion Gym (Lt. Surge)", "Pok\u00e9mon Fan Club", "S.S. Anne")),
        "LAVENDER_TOWN" to RegionMapSection("LAVENDER_TOWN", "Lavender Town", RegionId.KANTO, 20, 6, 1, 1, MapNodeType.TOWN, "The noble purple town of spirits.", listOf("Pok\u00e9mon Tower / Radio Tower", "Mr. Fuji's House")),
        "CELADON_CITY" to RegionMapSection("CELADON_CITY", "Celadon City", RegionId.KANTO, 12, 6, 1, 1, MapNodeType.CITY, "The city of rainbow dreams.", listOf("Celadon Gym (Erika)", "Celadon Dept. Store", "Game Corner")),
        "SAFFRON_CITY" to RegionMapSection("SAFFRON_CITY", "Saffron City", RegionId.KANTO, 15, 6, 1, 1, MapNodeType.CITY, "Shining, golden land of commerce.", listOf("Saffron Gym (Sabrina)", "Fighting Dojo", "Silph Co.")),
        "FUCHSIA_CITY" to RegionMapSection("FUCHSIA_CITY", "Fuchsia City", RegionId.KANTO, 14, 12, 1, 1, MapNodeType.CITY, "Behold! It's passion pink!", listOf("Fuchsia Gym (Koga / Janine)", "Safari Zone")),
        "CINNABAR_ISLAND" to RegionMapSection("CINNABAR_ISLAND", "Cinnabar Island", RegionId.KANTO, 5, 14, 1, 1, MapNodeType.TOWN, "The fiery town of burning desire.", listOf("Cinnabar Gym (Blaine)", "Pok\u00e9mon Lab")),
        "INDIGO_PLATEAU" to RegionMapSection("INDIGO_PLATEAU", "Indigo Plateau", RegionId.KANTO, 3, 3, 1, 1, MapNodeType.CITY, "The pinnacle of Pokémon mastery.", listOf("Pok\u00e9mon League", "Elite Four Chambers")),
        "ROUTE_1" to RegionMapSection("ROUTE_1", "Route 1", RegionId.KANTO, 5, 9, 1, 2, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_2" to RegionMapSection("ROUTE_2", "Route 2", RegionId.KANTO, 5, 5, 1, 3, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_3" to RegionMapSection("ROUTE_3", "Route 3", RegionId.KANTO, 7, 3, 4, 1, MapNodeType.ROUTE, "", listOf()),
        "ROUTE_4" to RegionMapSection("ROUTE_4", "Route 4", RegionId.KANTO, 11, 2, 4, 1, MapNodeType.ROUTE, "", listOf()),
    )

    /**
     * Return all map sections for a given region (used for rendering the Town Map canvas).
     */
    fun getSections(region: RegionId): List<RegionMapSection> {
        return when (region) {
            RegionId.JOHTO -> JOHTO_SECTIONS.values.toList()
            RegionId.HOENN -> HOENN_SECTIONS.values.toList()
            RegionId.KANTO -> KANTO_SECTIONS.values.toList()
        }
    }

    /**
     * Find a map section by ID across all regions.
     */
    fun getSectionById(id: String): RegionMapSection? {
        return JOHTO_SECTIONS[id] ?: HOENN_SECTIONS[id] ?: KANTO_SECTIONS[id]
    }

    /**
     * Resolve the player's live memory location into a high-level RegionMapSection.
     */
    fun resolveLocation(gameId: Int, isHeartAndSoul: Boolean, loc: PlayerLocation?): RegionMapSection {
        if (loc == null || !loc.isValid) {
            return if (isHeartAndSoul) JOHTO_DEFAULT else HOENN_SECTIONS.values.first()
        }
        return resolveLocationOrNull(gameId, isHeartAndSoul, loc) ?: JOHTO_DEFAULT
    }

    /** Resolve only when the active game's map-number table is known. */
    fun resolveLocationOrNull(gameId: Int, isHeartAndSoul: Boolean, loc: PlayerLocation?): RegionMapSection? {
        if (loc == null || !loc.isValid) return null
        if (isHeartAndSoul) return resolveHeartAndSoulLocation(loc)

        return when (gameId) {
            1 -> resolveEmeraldLocation(loc)
            2 -> resolveFireRedLocation(loc)
            else -> null
        }
    }

    private fun resolveHeartAndSoulLocation(loc: PlayerLocation): RegionMapSection {
        val group = loc.mapGroup
        val num = loc.mapNum

        // Group 0: Towns and Routes (71 maps)
        if (group == 0) {
            val secKey = when (num) {
                0 -> "MAPSEC_NEW_BARK_TOWN"
                1 -> "MAPSEC_CHERRYGROVE_CITY"
                2 -> "MAPSEC_VIOLET_CITY"
                3 -> "MAPSEC_AZALEA_TOWN"
                4 -> "MAPSEC_GOLDENROD_CITY"
                5 -> "MAPSEC_ECRUTEAK_CITY"
                6 -> "MAPSEC_OLIVINE_CITY"
                7 -> "MAPSEC_CIANWOOD_CITY"
                8 -> "MAPSEC_SAFARI_ZONE_GATE"
                9 -> "MAPSEC_MAHOGANY_TOWN"
                10 -> "MAPSEC_BLACKTHORN_CITY"
                11 -> "MAPSEC_ROUTE_29"
                12 -> "MAPSEC_ROUTE_30"
                13 -> "MAPSEC_ROUTE_31"
                14 -> "MAPSEC_ROUTE_32"
                15 -> "MAPSEC_ROUTE_33"
                16 -> "MAPSEC_ROUTE_34"
                17 -> "MAPSEC_ROUTE_35"
                18 -> "MAPSEC_ROUTE_36"
                19 -> "MAPSEC_ROUTE_37"
                20 -> "MAPSEC_ROUTE_38"
                21 -> "MAPSEC_ROUTE_39"
                22 -> "MAPSEC_ROUTE_40"
                23 -> "MAPSEC_ROUTE_41"
                24 -> "MAPSEC_ROUTE_42"
                25 -> "MAPSEC_ROUTE_43"
                26 -> "MAPSEC_ROUTE_44"
                27 -> "MAPSEC_ROUTE_45"
                28 -> "MAPSEC_ROUTE_46"
                29 -> "MAPSEC_ROUTE_47"
                30 -> "MAPSEC_ROUTE_48"
                31 -> "MAPSEC_PALLET_TOWN"
                32 -> "MAPSEC_VIRIDIAN_CITY"
                33 -> "MAPSEC_PEWTER_CITY"
                34 -> "MAPSEC_CERULEAN_CITY"
                35 -> "MAPSEC_VERMILION_CITY"
                36 -> "MAPSEC_LAVENDER_TOWN"
                37 -> "MAPSEC_CELADON_CITY"
                38 -> "MAPSEC_SAFFRON_CITY"
                39 -> "MAPSEC_FUCHSIA_CITY"
                40 -> "MAPSEC_CINNABAR_ISLAND"
                in 41..70 -> "MAPSEC_ROUTE_${num - 40}"
                else -> "MAPSEC_NEW_BARK_TOWN"
            }
            JOHTO_SECTIONS[secKey]?.let { return it }
        }

        // Indoor groups 1..21 (Towns & Cities)
        val indoorParentKey = when (group) {
            1 -> "MAPSEC_NEW_BARK_TOWN"
            2 -> "MAPSEC_CHERRYGROVE_CITY"
            3 -> "MAPSEC_VIOLET_CITY"
            4 -> "MAPSEC_AZALEA_TOWN"
            5 -> "MAPSEC_GOLDENROD_CITY"
            6 -> "MAPSEC_ECRUTEAK_CITY"
            7 -> "MAPSEC_OLIVINE_CITY"
            8 -> "MAPSEC_CIANWOOD_CITY"
            9 -> "MAPSEC_MAHOGANY_TOWN"
            10 -> "MAPSEC_BLACKTHORN_CITY"
            11 -> "MAPSEC_PALLET_TOWN"
            12 -> "MAPSEC_VIRIDIAN_CITY"
            13 -> "MAPSEC_PEWTER_CITY"
            14 -> "MAPSEC_CERULEAN_CITY"
            15 -> "MAPSEC_VERMILION_CITY"
            16 -> "MAPSEC_LAVENDER_TOWN"
            17 -> "MAPSEC_CELADON_CITY"
            18 -> "MAPSEC_SAFFRON_CITY"
            19 -> "MAPSEC_FUCHSIA_CITY"
            20 -> "MAPSEC_CINNABAR_ISLAND"
            21 -> "MAPSEC_INDIGO_PLATEAU"
            else -> null
        }
        if (indoorParentKey != null) {
            JOHTO_SECTIONS[indoorParentKey]?.let { return it }
        }

        // Group 24: Dungeons
        if (group == 24) {
            val dungKey = when (num) {
                0, 1 -> "MAPSEC_DARK_CAVE"
                2, 3, 4 -> "MAPSEC_SPROUT_TOWER"
                5, 6, 7, 86, 87, 88, 89 -> "MAPSEC_RUINS_OF_ALPH"
                8, 9, 10 -> "MAPSEC_UNION_CAVE"
                11, 12 -> "MAPSEC_SLOWPOKE_WELL"
                13 -> "MAPSEC_ILEX_FOREST"
                14, 15 -> "MAPSEC_NATIONAL_PARK"
                16, 17 -> "MAPSEC_BURNED_TOWER"
                18, 81 -> "MAPSEC_CLIFF_CAVE"
                19, 20, 21, 22 -> "MAPSEC_MT_MORTAR"
                23, 24 -> "MAPSEC_LAKE_OF_RAGE"
                in 25..29 -> "MAPSEC_ICE_PATH"
                30, 31, 32 -> "MAPSEC_DRAGONS_DEN"
                in 33..39 -> "MAPSEC_WHIRL_ISLANDS"
                in 40..50 -> "MAPSEC_TIN_TOWER"
                51, 52 -> "MAPSEC_TOHJO_FALLS"
                53, 54, 55 -> "MAPSEC_INDIGO_PLATEAU"
                56 -> "MAPSEC_VIRIDIAN_FOREST"
                57, 58 -> "MAPSEC_MT_MOON"
                59, 60 -> "MAPSEC_ROCK_TUNNEL"
                in 61..63 -> "MAPSEC_CERULEAN_CAVE"
                in 64..66 -> "MAPSEC_DIGLETTS_CAVE"
                in 67..70 -> "MAPSEC_SEAFOAM_ISLANDS"
                in 71..80 -> "MAPSEC_MT_SILVER"
                82 -> "MAPSEC_EMBEDDED_TOWER"
                in 83..85 -> "MAPSEC_ROCKET_HIDEOUT"
                else -> null
            }
            if (dungKey != null) {
                JOHTO_SECTIONS[dungKey]?.let { return it }
            }
        }

        // Indoor routes (Group 22, 23) -> check escape warp if available
        if (loc.escapeMapGroup == 0 && loc.escapeMapNum in 0..70) {
            return resolveHeartAndSoulLocation(loc.copy(mapGroup = 0, mapNum = loc.escapeMapNum))
        }

        return JOHTO_DEFAULT
    }

    private fun resolveEmeraldLocation(loc: PlayerLocation): RegionMapSection {
        val key = when (loc.mapNum) {
            0 -> "PETALBURG_CITY"
            1 -> "SLATEPORT_CITY"
            2 -> "MAUVILLE_CITY"
            3 -> "RUSTBORO_CITY"
            4 -> "FORTREE_CITY"
            5 -> "LILYCOVE_CITY"
            6 -> "MOSSDEEP_CITY"
            7 -> "SOOTOPOLIS_CITY"
            8 -> "EVER_GRANDE_CITY"
            9 -> "LITTLEROOT_TOWN"
            10 -> "OLDALE_TOWN"
            11 -> "DEWFORD_TOWN"
            12 -> "LAVARIDGE_TOWN"
            13 -> "FALLARBOR_TOWN"
            14 -> "VERDANTURF_TOWN"
            15 -> "PACIFIDLOG_TOWN"
            16 -> "ROUTE_101"
            17 -> "ROUTE_102"
            18 -> "ROUTE_103"
            19 -> "ROUTE_104"
            else -> "LITTLEROOT_TOWN"
        }
        return HOENN_SECTIONS[key] ?: HOENN_SECTIONS.values.first()
    }

    private fun resolveFireRedLocation(loc: PlayerLocation): RegionMapSection {
        val key = when (loc.mapNum) {
            0 -> "PALLET_TOWN"
            1 -> "VIRIDIAN_CITY"
            2 -> "PEWTER_CITY"
            3 -> "CERULEAN_CITY"
            4 -> "LAVENDER_TOWN"
            5 -> "VERMILION_CITY"
            6 -> "CELADON_CITY"
            7 -> "FUCHSIA_CITY"
            8 -> "CINNABAR_ISLAND"
            9 -> "INDIGO_PLATEAU"
            10 -> "SAFFRON_CITY"
            19 -> "ROUTE_1"
            20 -> "ROUTE_2"
            21 -> "ROUTE_3"
            22 -> "ROUTE_4"
            else -> "PALLET_TOWN"
        }
        return KANTO_SECTIONS[key] ?: KANTO_SECTIONS.values.first()
    }
}
