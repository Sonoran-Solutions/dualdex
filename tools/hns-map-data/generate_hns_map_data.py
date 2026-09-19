#!/usr/bin/env python3
"""
tools/hns-map-data/generate_hns_map_data.py

Extracts the authoritative Heart & Soul 2.0.5 map-group / map-number identity and
region-map presentation table from the pinned upstream source checkout, and
generates the Kotlin Hns205MapData object consumed by the runtime resolver.

Provenance:
  Repository: PokemonHnS-Development/pokehns-expansion
  Tag:        Release-v2.0.5
  Commit:     1f42b74dff0e9fe942419845d040663dd829a973
  Extraction: data/maps/map_groups.json (effective build group order)
              data/maps/<map>/map.json (game_version, region, region_map_section)
              src/data/region_map/region_map_entries.h (IS_HNS entry table)
  ROM dependency: NONE (no ROM bytes, saves, or emulator states are read)

What "the effective build group order" means here
-------------------------------------------------
`data/maps/map_groups.json` is the single source of truth for `mapGroup`. The
mapGroup value the game stores in SaveBlock1 is the index into that file's
`group_order` array, and mapNum is the index within the named group. The
generator therefore never sorts identities: it walks `group_order` in file
order and each group's member list in file order.

Only maps whose `map.json` declares `"game_version": "hns"` are treated as
Heart & Soul locations. The same table also carries the Emerald and FireRed
map tables that DualDex models separately, and admitting them here would let a
non-H&S mapGroup appear to be a valid H&S location.

Regenerate:
  python3 tools/hns-map-data/generate_hns_map_data.py            # write
  python3 tools/hns-map-data/generate_hns_map_data.py --check    # verify byte-identical
  python3 tools/hns-map-data/generate_hns_map_data.py --print-summary
"""

import argparse
import json
import os
import re
import subprocess
import sys

PINNED_COMMIT_SHA = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"
UPSTREAM_REPO = "PokemonHnS-Development/pokehns-expansion"

DEFAULT_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_TARGET_FILE = os.path.join(
    DEFAULT_REPO_ROOT,
    "app/src/main/java/com/dualdex/pokemon/hns/Hns205MapData.kt",
)

DEFAULT_UPSTREAM_SEARCH_PATHS = [
    os.environ.get("HNS_UPSTREAM_DIR"),
    os.path.join(os.path.dirname(DEFAULT_REPO_ROOT), "upstream-hns/pokehns-expansion"),
]

# Upstream region constant -> Kotlin RegionId name.
REGION_ID_MAP = {
    "REGION_JOHTO": "JOHTO",
    "REGION_KANTO": "KANTO",
    "REGION_HISUI": "SINJOH",
    "REGION_ALOLA": "ALOLA",
}

# Sections upstream uses for areas with no geographical place in the world
# (link rooms, contest halls, battle facilities). They are never drawn on a
# region map, so they can never carry a player region.
NON_GEOGRAPHIC_SECTIONS = ("MAPSEC_DYNAMIC",)

# H&S region-map views, in the order of preference for deciding a section's
# canvas position. Keyed by the Kotlin RegionId the view belongs to; the last
# entry (the combined Johto/Kanto view) is the shared fallback canvas.
LAYOUT_VIEWS = (
    ("region_map_layout_johto.h", "JOHTO"),
    ("region_map_layout_kanto.h", "KANTO"),
    ("region_map_layout_jk.h", None),
)

CHUNK_SIZE = 8

# `region_map.c` defines the H&S region maps as MAP_WIDTH x MAP_HEIGHT tiles.
MAP_WIDTH = 28
MAP_HEIGHT = 15

# Upstream map_type -> Kotlin MapNodeType name. A section takes the strongest
# hint among the H&S maps that resolve to it, so a town's one indoor map cannot
# downgrade the town to a generic interior.
NODE_TYPE_BY_MAP_TYPE = {
    "MAP_TYPE_TOWN": "TOWN",
    "MAP_TYPE_CITY": "CITY",
    "MAP_TYPE_ROUTE": "ROUTE",
    "MAP_TYPE_UNDERWATER": "DUNGEON",
    "MAP_TYPE_UNDERGROUND": "DUNGEON",
    "MAP_TYPE_OCEAN_ROUTE": "ROUTE",
    "MAP_TYPE_INDOOR": "FACILITY",
    "MAP_TYPE_SECRET_BASE": "FACILITY",
    "MAP_TYPE_NONE": "LANDMARK",
}
# A section's node type describes the area a player sees on the map, so the
# outdoor nature of a section outranks its interior sub-maps: Route 29 stays a
# ROUTE even though it also contains gates, and New Bark Town stays a TOWN
# rather than becoming a facility because its houses are MAP_TYPE_INDOOR.
# MAP_TYPE_CITY is upstream's catch-all "outdoor, not a route" label and is used
# for several non-city areas (Mt. Silver, Lake of Rage), so a specific town,
# dungeon or route classification always outranks it.
OUTDOOR_NODE_TYPE_PRIORITY = ("TOWN", "DUNGEON", "ROUTE", "LANDMARK", "CITY")
INTERIOR_NODE_TYPE_PRIORITY = ("FACILITY",)
NODE_TYPE_PRIORITY = OUTDOOR_NODE_TYPE_PRIORITY + INTERIOR_NODE_TYPE_PRIORITY


class GenerationError(RuntimeError):
    pass


def find_upstream_dir(provided_dir):
    for path in [provided_dir] + DEFAULT_UPSTREAM_SEARCH_PATHS:
        if path and os.path.isfile(os.path.join(path, "data/maps/map_groups.json")):
            return os.path.abspath(path)
    raise GenerationError(
        "Could not locate the pinned Heart & Soul upstream checkout. "
        "Set HNS_UPSTREAM_DIR or pass --upstream-dir."
    )


def verify_git_commit(upstream_dir):
    """Fail closed unless the checkout is the exact pinned revision."""
    try:
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=upstream_dir, capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        raise GenerationError(f"Could not read the upstream git revision: {exc}")

    if head != PINNED_COMMIT_SHA:
        raise GenerationError(
            f"Upstream checkout is at {head}, but this generator is pinned to "
            f"{PINNED_COMMIT_SHA} ({PINNED_TAG}). Refusing to generate map data "
            "from an unpinned revision."
        )
    dirty = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=upstream_dir, capture_output=True, text=True, check=True,
    ).stdout.strip()
    if dirty:
        raise GenerationError(
            "Upstream checkout has uncommitted changes; refusing to generate "
            "map data from a modified tree."
        )
    return head


def parse_layout_grid(path):
    """
    Parse a `sRegionMapSections_*` MAP_HEIGHT x MAP_WIDTH grid into bounding boxes.

    The grid is what the game actually renders and what `region_map.c` uses for
    the player marker's cursor position, so it -- not the region-map *entry*
    table -- is the authoritative H&S canvas geometry for a given view.

    `region_map_entries.h` is deliberately not used for positions: its H&S table
    carries the FireRed/Johto map canvas coordinates, and its Sinjoh and Alola
    entries are unresolved `(0, 0)` placeholders.
    """
    if not os.path.isfile(path):
        raise GenerationError(f"Missing upstream region map layout: {path}")
    src = open(path, encoding="utf-8").read()

    marker = "sRegionMapSections"
    if marker not in src:
        raise GenerationError(f"{path}: no {marker} array")
    start = src.index("{", src.index(marker))
    body = src[start + 1:src.index("};", start)]

    rows = []
    for line in body.splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        inner = line[line.index("{") + 1:line.rindex("}")]
        rows.append([cell.strip() for cell in inner.split(",") if cell.strip()])

    if len(rows) != MAP_HEIGHT:
        raise GenerationError(f"{path}: expected {MAP_HEIGHT} rows, found {len(rows)}")
    for index, row in enumerate(rows):
        if len(row) != MAP_WIDTH:
            raise GenerationError(
                f"{path}: row {index} has {len(row)} cells, expected {MAP_WIDTH}"
            )

    boxes = {}
    for y, row in enumerate(rows):
        for x, section in enumerate(row):
            if section == "MAPSEC_NONE":
                continue
            current = boxes.get(section)
            if current is None:
                boxes[section] = [x, y, x, y]
            else:
                current[0] = min(current[0], x)
                current[1] = min(current[1], y)
                current[2] = max(current[2], x)
                current[3] = max(current[3], y)

    return {
        section: (
            (box[0] + box[2]) // 2,
            (box[1] + box[3]) // 2,
            box[2] - box[0] + 1,
            box[3] - box[1] + 1,
        )
        for section, box in boxes.items()
    }


def load_layout_views(upstream_dir):
    base = os.path.join(upstream_dir, "src/data/region_map")
    views = []
    for filename, region in LAYOUT_VIEWS:
        views.append((region, parse_layout_grid(os.path.join(base, filename)), filename))
    return views


def resolve_canvas_position(section_id, region, views):
    """
    Pick the canvas position for a section, preferring the view a player is
    most likely looking at for that region.

    Returns (gridX, gridY, width, height, view_name) or None when no H&S view
    draws the section at all.
    """
    ordered = list(views)
    if region is not None:
        ordered.sort(key=lambda view: view[0] != region)
    for view_region, boxes, filename in ordered:
        if section_id not in boxes:
            continue
        # Only a view DualDex renders as its own region canvas is usable for a
        # player marker. The combined Johto/Kanto view is not one, so its Sinjoh
        # and Alola tiles would place a marker at coordinates in a grid the app
        # never draws.
        if view_region is None:
            continue
        x, y, width, height = boxes[section_id]
        return x, y, width, height, filename
    return None


def load_map_metadata(upstream_dir, map_name):
    path = os.path.join(upstream_dir, "data/maps", map_name, "map.json")
    if not os.path.isfile(path):
        raise GenerationError(f"Missing map definition for '{map_name}': {path}")
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def format_display_name(raw_name):
    """'NEW BARK TOWN' -> 'New Bark Town'. Upstream casing is preserved as-is."""
    return " ".join(word.capitalize() for word in raw_name.split())


def collect_locations(upstream_dir):
    groups_path = os.path.join(upstream_dir, "data/maps/map_groups.json")
    with open(groups_path, encoding="utf-8") as handle:
        groups = json.load(handle)

    group_order = groups.get("group_order")
    if not group_order:
        raise GenerationError("map_groups.json has no group_order array")

    locations = {}
    for group_index, group_name in enumerate(group_order):
        members = groups.get(group_name)
        if members is None:
            raise GenerationError(f"group_order references unknown group '{group_name}'")
        for map_number, map_name in enumerate(members):
            metadata = load_map_metadata(upstream_dir, map_name)
            if metadata.get("game_version") != "hns":
                continue
            key = (group_index, map_number)
            if key in locations:
                raise GenerationError(f"duplicate location key {key} ({map_name})")
            region = metadata.get("region")
            section = metadata.get("region_map_section")
            if not section:
                raise GenerationError(f"{map_name}: missing region_map_section")
            if region is not None and region not in REGION_ID_MAP:
                raise GenerationError(f"{map_name}: unmapped region constant '{region}'")
            map_type = metadata.get("map_type")
            if map_type is not None and map_type not in NODE_TYPE_BY_MAP_TYPE:
                raise GenerationError(f"{map_name}: unmapped map_type '{map_type}'")
            locations[key] = {
                "mapName": map_name,
                "region": REGION_ID_MAP.get(region),
                "sectionId": section,
                "nodeType": NODE_TYPE_BY_MAP_TYPE.get(map_type, "LANDMARK"),
            }

    if not locations:
        raise GenerationError("No H&S locations found; upstream layout changed")
    return locations


def parse_section_display_names(upstream_dir):
    """
    Read the H&S `gRegionMapEntries` display strings, used only for names.

    Positions from this table are deliberately ignored (see parse_layout_grid).
    """
    path = os.path.join(upstream_dir, "src/data/region_map/region_map_entries.h")
    if not os.path.isfile(path):
        raise GenerationError(f"Missing upstream region map entry table: {path}")
    src = open(path, encoding="utf-8").read()

    marker = "const struct RegionMapLocation gRegionMapEntries[] = {"
    start = src.find(marker)
    if start < 0:
        raise GenerationError("Could not find the H&S gRegionMapEntries table")
    body = src[start + len(marker):src.index("};", start)]

    names = {}
    for match in re.finditer(r"\[(\w+)\]\s*=\s*\{([^}]*)\}", body, re.S):
        name_match = re.search(
            r'\.name\s*=\s*COMPOUND_STRING\("([^"]*)"\)', match.group(2)
        )
        if name_match:
            names[match.group(1)] = name_match.group(1)
    if not names:
        raise GenerationError("H&S region map entry names parsed empty")
    return names


def section_is_geographic(section_id):
    """False for upstream sections that denote no place in the world."""
    return not any(section_id.startswith(p) for p in NON_GEOGRAPHIC_SECTIONS)


def derive_sections(display_names, views, locations):
    """
    Build the section table DualDex may present, keyed by section id.

    Region identity and canvas presentation are decided independently:

      * region comes from the H&S locations that use the section, and only when
        they all agree (a disagreement is a generator error, not a guess);
      * presentation comes from whether an H&S region-map view actually draws
        the section.

    A section that is not drawn keeps its region identity but is marked
    not presentable, so the resolver can name the area without placing a marker
    at invented coordinates. Sinjoh and the Alola Isles are exactly this case:
    they are real H&S areas that appear only on the combined Johto/Kanto canvas,
    which DualDex does not render as its own region view.
    """
    regions_by_section = {}
    node_types_by_section = {}
    for loc in locations.values():
        regions_by_section.setdefault(loc["sectionId"], set()).add(loc["region"])
        node_types_by_section.setdefault(loc["sectionId"], set()).add(loc["nodeType"])

    sections = {}
    for section_id in sorted(regions_by_section):
        observed = regions_by_section[section_id]
        observed_node_types = node_types_by_section.get(section_id, set())
        node_type = next(
            (candidate for candidate in NODE_TYPE_PRIORITY if candidate in observed_node_types),
            "LANDMARK",
        )

        if not section_is_geographic(section_id):
            region = None
        else:
            resolved = {r for r in observed if r is not None}
            if len(resolved) > 1:
                raise GenerationError(
                    f"{section_id}: H&S locations disagree on region {sorted(resolved)}"
                )
            region = next(iter(resolved)) if resolved else None

        position = (
            resolve_canvas_position(section_id, region, views)
            if section_is_geographic(section_id)
            else None
        )
        raw_name = display_names.get(section_id)
        if raw_name is None:
            raw_name = section_id.replace("MAPSEC_", "").replace("_", " ")

        sections[section_id] = {
            "sectionId": section_id,
            "displayName": format_display_name(raw_name),
            "region": region,
            "nodeType": node_type,
            "presentable": position is not None,
            # A section no H&S view draws has no canvas anchor at all. -1 is an
            # explicit "absent" sentinel so it can never be mistaken for a
            # position at the top-left corner of the map.
            "x": position[0] if position else -1,
            "y": position[1] if position else -1,
            "width": position[2] if position else 0,
            "height": position[3] if position else 0,
            "view": position[4] if position else None,
        }
    return sections


def normalize_location_regions(locations, sections):
    """
    Make the section the single source of truth for a location's region.

    Upstream carries a few internally inconsistent pairs, all preserved here as
    documented provenance rather than silently "fixed" in the source:

      * UnionRoom_hns (27,0)   region=REGION_KANTO, section=MAPSEC_DYNAMIC
      * LilycoveCity_ContestHall_hns (30,93) and LilycoveCity_ContestLobby_hns
        (30,94)                region=None,        section=MAPSEC_VIRIDIAN_CITY

    None of the three is an overworld area a player can stand in and be
    geographically placed, so the section-derived answer (regionless for
    MAPSEC_DYNAMIC, Kanto for MAPSEC_VIRIDIAN_CITY) is the defensible one.
    Location map names stay verbatim so the upstream identity is inspectable.
    """
    adjustments = []
    for key, loc in locations.items():
        section = sections[loc["sectionId"]]
        if loc["region"] != section["region"]:
            adjustments.append((key, loc["mapName"], loc["region"], section["region"]))
            loc["region"] = section["region"]
    return sorted(adjustments)


def validate(locations, sections, views):
    if len(sections) != len(set(sections)):
        raise GenerationError("duplicate section ids generated")

    for (group, number), loc in locations.items():
        if group < 0 or number < 0:
            raise GenerationError(f"negative location key {(group, number)}")
        if loc["sectionId"] not in sections:
            raise GenerationError(
                f"{(group, number)} {loc['mapName']}: unresolved section {loc['sectionId']}"
            )
        if loc["nodeType"] not in NODE_TYPE_PRIORITY:
            raise GenerationError(
                f"{(group, number)} {loc['mapName']}: unknown node type {loc['nodeType']}"
            )
        section = sections[loc["sectionId"]]
        if loc["region"] != section["region"]:
            raise GenerationError(
                f"{(group, number)} {loc['mapName']}: location region {loc['region']} "
                f"does not match section {loc['sectionId']} region {section['region']}"
            )

    # A presentable section must have usable canvas geometry that lies inside
    # the H&S region map it was taken from.
    for section in sections.values():
        if not section["presentable"]:
            continue
        if section["width"] < 1 or section["height"] < 1:
            raise GenerationError(f"{section['sectionId']}: non-positive canvas extent")
        if not (0 <= section["x"] < MAP_WIDTH and 0 <= section["y"] < MAP_HEIGHT):
            raise GenerationError(
                f"{section['sectionId']}: canvas origin ({section['x']}, {section['y']}) "
                f"outside the {MAP_WIDTH}x{MAP_HEIGHT} H&S region map"
            )
        if section["x"] + section["width"] > MAP_WIDTH:
            raise GenerationError(f"{section['sectionId']}: canvas extent exceeds map width")
        if section["y"] + section["height"] > MAP_HEIGHT:
            raise GenerationError(f"{section['sectionId']}: canvas extent exceeds map height")
        if section["view"] is None:
            raise GenerationError(f"{section['sectionId']}: presentable without a source view")

    # Every section drawn on a canvas DualDex actually renders must be marked
    # presentable; otherwise a real position would silently become pin-less.
    drawn = set()
    for view_region, boxes, _ in views:
        if view_region is None:
            continue
        drawn |= set(boxes)
    for section_id in sorted(drawn):
        if section_id in sections and not sections[section_id]["presentable"]:
            raise GenerationError(
                f"{section_id}: drawn by a rendered H&S view but marked not presentable"
            )


def kotlin_string(value):
    if value is None:
        return "null"
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def kotlin_region(region):
    return f"RegionId.{region}" if region else "null"


def generate_kotlin(locations, sections, upstream_sha, max_group):
    sections_sorted = sorted(sections.values(), key=lambda s: s["sectionId"])
    lines = []
    add = lines.append

    add("package com.dualdex.pokemon.hns")
    add("")
    add("import com.dualdex.pokemon.MapNodeType")
    add("import com.dualdex.pokemon.RegionId")
    add("")
    add("/**")
    add(" * Exact, version-pinned map identity table for Pokemon Heart & Soul 2.0.5.")
    add(" *")
    add(" * Provenance:")
    add(f" *   Repository: {UPSTREAM_REPO}")
    add(f" *   Tag: {PINNED_TAG}")
    add(f" *   Commit: {upstream_sha}")
    # Avoid a literal "*/" inside the generated block comment.
    add(" *   Extraction: data/maps/map_groups.json, data/maps/<map>/map.json,")
    add(" *               src/data/region_map/region_map_entries.h (IS_HNS table)")
    add(" *   ROM dependency: NONE")
    add(" *")
    add(" * [locationGroups] maps the raw SaveBlock1 mapGroup/mapNum pair the running game")
    add(" * stores onto the upstream map identity. Group index is the position in upstream")
    add(" * `group_order`; map number is the position inside that group. Both are build")
    add(" * configuration, so they are pinned rather than inferred.")
    add(" *")
    add(f" * H&S locations: {len(locations)}   H&S map sections: {len(sections_sorted)}")
    add(" *")
    add(" * DO NOT EDIT DIRECTLY. Regenerate using:")
    add(" *   python3 tools/hns-map-data/generate_hns_map_data.py")
    add(" */")
    add("object Hns205MapData {")
    add(f"    const val UPSTREAM_COMMIT_SHA: String = {kotlin_string(upstream_sha)}")
    add(f"    const val UPSTREAM_TAG: String = {kotlin_string(PINNED_TAG)}")
    add("")
    add("    /** A single H&S map identified by its raw mapGroup/mapNum pair. */")
    add("    data class HnsMapLocation(")
    add("        val mapName: String,")
    add("        val sectionId: String,")
    add("        val region: RegionId?,")
    add("    )")
    add("")
    add("    /**")
    add("     * A H&S region-map section.")
    add("     *")
    add("     * [presentable] is false when no H&S region-map view draws the section.")
    add("     * Such a section keeps its region identity but has no canvas anchor")
    add("     * ([gridX] and [gridY] are -1), so a caller must never place a player")
    add("     * marker for it.")
    add("     */")
    add("    data class HnsMapSection(")
    add("        val sectionId: String,")
    add("        val displayName: String,")
    add("        val region: RegionId?,")
    add("        val nodeType: MapNodeType,")
    add("        val presentable: Boolean,")
    add("        val gridX: Int,")
    add("        val gridY: Int,")
    add("        val width: Int,")
    add("        val height: Int,")
    add("    )")
    add("")

    add("    /** Highest mapGroup index that carries a H&S location. */")
    add(f"    const val MAX_LOCATION_GROUP: Int = {max_group}")
    add("")

    # Sections, chunked so no single method exceeds the JVM method size limit.
    add("    val sections: List<HnsMapSection> = buildList {")
    for index in range(0, len(sections_sorted), CHUNK_SIZE):
        chunk = sections_sorted[index:index + CHUNK_SIZE]
        add(f"        addSectionChunk{index // CHUNK_SIZE + 1}()")
    add("    }")
    add("")
    for index in range(0, len(sections_sorted), CHUNK_SIZE):
        chunk = sections_sorted[index:index + CHUNK_SIZE]
        add(f"    private fun MutableList<HnsMapSection>.addSectionChunk{index // CHUNK_SIZE + 1}() {{")
        for section in chunk:
            add(
                "        add(HnsMapSection("
                f"{kotlin_string(section['sectionId'])}, {kotlin_string(section['displayName'])}, "
                f"{kotlin_region(section['region'])}, "
                f"MapNodeType.{section['nodeType']}, "
                f"{'true' if section['presentable'] else 'false'}, "
                f"{section['x'] if section['x'] is not None else 0}, "
                f"{section['y'] if section['y'] is not None else 0}, "
                f"{section['width'] if section['width'] is not None else 0}, "
                f"{section['height'] if section['height'] is not None else 0}))"
            )
        add("    }")
        add("")

    add("    /** Section lookup by upstream section id. */")
    add("    val sectionsById: Map<String, HnsMapSection> = sections.associateBy { it.sectionId }")
    add("")

    # Location table: group -> num -> location, with explicit null holes so a
    # map number the H&S build does not define can never inherit a neighbour.
    groups = {}
    for (group, number), loc in locations.items():
        groups.setdefault(group, {})[number] = loc

    add("    /** mapGroup -> (mapNum -> location). A gap is an explicit non-H&S map number. */")
    add("    val locationGroups: Map<Int, List<HnsMapLocation?>> = buildMap {")
    for group in sorted(groups):
        add(f"        put({group}, group{group}())")
    add("    }")
    add("")
    for group in sorted(groups):
        members = groups[group]
        highest = max(members)
        add(f"    private fun group{group}(): List<HnsMapLocation?> = listOf(")
        for number in range(0, highest + 1):
            loc = members.get(number)
            if loc is None:
                add(f"        null, // mapNum {number}: not a Heart & Soul map")
            else:
                add(
                    f"        HnsMapLocation({kotlin_string(loc['mapName'])}, "
                    f"{kotlin_string(loc['sectionId'])}, {kotlin_region(loc['region'])}),"
                    f" // mapNum {number}"
                )
        add("    )")
        add("")

    add("    /** Resolve a raw mapGroup/mapNum pair, or null when H&S defines no such map. */")
    add("    fun findLocation(mapGroup: Int, mapNum: Int): HnsMapLocation? {")
    add("        if (mapGroup < 0 || mapNum < 0) return null")
    add("        return locationGroups[mapGroup]?.getOrNull(mapNum)")
    add("    }")
    add("")
    add("    fun findSection(sectionId: String): HnsMapSection? = sectionsById[sectionId]")
    add("}")

    return "\n".join(lines) + "\n"


def summarize(locations, sections):
    from collections import Counter
    region_counts = Counter(loc["region"] for loc in locations.values())
    presentable = [s for s in sections.values() if s["presentable"]]
    unpresentable = sorted(
        s["sectionId"] for s in sections.values() if not s["presentable"]
    )
    view_counts = Counter(s["view"] for s in presentable)
    grouped = sorted({group for group, _ in locations})
    print(f"upstream commit: {PINNED_COMMIT_SHA} ({PINNED_TAG})")
    print(f"H&S locations:   {len(locations)}")
    print(f"H&S groups used: {grouped[0]}..{grouped[-1]} ({len(grouped)} groups)")
    print(f"H&S sections:    {len(sections)} ({len(presentable)} presentable)")
    for view, count in sorted(view_counts.items(), key=lambda kv: str(kv[0])):
        print(f"  canvas {view}: {count} sections")
    if unpresentable:
        print(f"  not drawn by any H&S region view ({len(unpresentable)}):")
        for section_id in unpresentable:
            section = sections[section_id]
            print(f"    {section_id} (region {section['region'] or 'NONE'})")
    for region, count in sorted(region_counts.items(), key=lambda kv: str(kv[0])):
        print(f"  region {region or 'NONE'}: {count} locations")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-dir", default=None)
    parser.add_argument("--output", default=DEFAULT_TARGET_FILE)
    parser.add_argument("--check", action="store_true",
                        help="Verify the on-disk file matches regenerated output.")
    parser.add_argument("--print-summary", action="store_true")
    args = parser.parse_args()

    upstream_dir = find_upstream_dir(args.upstream_dir)
    sha = verify_git_commit(upstream_dir)
    display_names = parse_section_display_names(upstream_dir)
    views = load_layout_views(upstream_dir)
    locations = collect_locations(upstream_dir)
    sections = derive_sections(display_names, views, locations)
    normalize_location_regions(locations, sections)
    validate(locations, sections, views)

    if args.print_summary:
        summarize(locations, sections)

    max_group = max(group for group, _ in locations)
    content = generate_kotlin(locations, sections, sha, max_group)

    if args.check:
        if not os.path.isfile(args.output):
            print(f"error: {args.output} does not exist", file=sys.stderr)
            return 1
        existing = open(args.output, encoding="utf-8").read()
        if existing != content:
            print(
                "error: generated map data is stale; run "
                "python3 tools/hns-map-data/generate_hns_map_data.py",
                file=sys.stderr,
            )
            return 1
        print("H&S map data is up to date with the pinned upstream revision.")
        return 0

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as handle:
        handle.write(content)
    print(f"Wrote {args.output}")
    if not args.print_summary:
        summarize(locations, sections)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except GenerationError as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
