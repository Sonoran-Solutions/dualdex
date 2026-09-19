package com.dualdex.pokemon

/**
 * A geographical region DualDex can name and, for [hasCanvas], draw.
 *
 * [SINJOH] and [ALOLA] exist because Heart & Soul 2.0.5 genuinely places the
 * player there (upstream `region_map_sections.constants.json.txt` defines an
 * explicit Sinjoh/Hisui range and an Alola range). They carry no canvas of
 * their own in DualDex today, so a location in them is named rather than
 * marked. See [RegionMapSection.presentable].
 */
enum class RegionId(val displayName: String) {
    JOHTO("Johto"),
    KANTO("Kanto"),
    SINJOH("Sinjoh"),
    ALOLA("Alola"),
    HOENN("Hoenn");

    /** True when DualDex can render a browsable region-map canvas for this region. */
    val hasCanvas: Boolean
        get() = this == JOHTO || this == KANTO || this == HOENN
}

enum class MapNodeType {
    TOWN,
    CITY,
    ROUTE,
    DUNGEON,
    LANDMARK,
    FACILITY
}

/**
 * Raw player location read from SaveBlock1 in GBA EWRAM at runtime.
 */
data class PlayerLocation(
    val mapGroup: Int,
    val mapNum: Int,
    val warpId: Int,
    val x: Int,
    val y: Int,
    val localX: Int,
    val localY: Int,
    val escapeMapGroup: Int,
    val escapeMapNum: Int,
    val isIndoors: Boolean,
    val isValid: Boolean
)

/**
 * A resolved map section for display on the companion Town Map.
 *
 * [presentable] distinguishes "DualDex understands where this is" from "DualDex
 * can draw it". A section the active game's region map does not draw (a
 * Heart & Soul location reachable only from the combined Johto/Kanto canvas, an
 * interior, a dynamic area) still resolves with a trustworthy [name] and
 * [region], but must never receive a player marker at invented coordinates.
 */
data class RegionMapSection(
    val id: String,
    val name: String,
    val region: RegionId?,
    val gridX: Int,           // 0..27 on the H&S / GBA Town Map grid
    val gridY: Int,           // 0..14 on the H&S / GBA Town Map grid
    val width: Int = 1,
    val height: Int = 1,
    val nodeType: MapNodeType = MapNodeType.ROUTE,
    val description: String = "",
    val landmarks: List<String> = emptyList(),
    val gymLeader: String? = null,
    val badge: String? = null,
    val connections: List<String> = emptyList(),
    val presentable: Boolean = true
)
