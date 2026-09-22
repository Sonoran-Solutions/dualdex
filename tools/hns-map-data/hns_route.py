#!/usr/bin/env python3
"""
tools/hns-map-data/hns_route.py

Offline overworld route planning and cross-region edge analysis for the pinned
Heart & Soul 2.0.5 source checkout (issue #11).

It exists because the runtime half of #11 needs a *natural* map transition whose
exact destination tile is known before a controller script is written, and because
"is a cross-region route reachable?" has to be answered from the pinned build rather
than from memory of the retail games.

Provenance boundary
-------------------
The checkout is verified before anything is read, exactly as
`generate_hns_map_data.py` does, because this tool's output is used to justify a
claim about the pinned build:

  * `HEAD` must equal `PINNED_COMMIT_SHA` (`Release-v2.0.5`);
  * the tracked inputs this tool reads must be clean -- `data/maps/`,
    `data/layouts/`, `data/tilesets/`, `data/scripts/` and `include/constants/`.
    `data/layouts/` and `data/tilesets/` are included because layout dimensions,
    block data and metatile attributes are reachability evidence here.

A checkout at another revision, or with any of those paths modified or deleted, is
refused rather than silently analysed. Untracked files elsewhere are tolerated: a CI
sparse checkout leaves plenty of them and they cannot affect the analysis.

What it reads (tracked files only, from the pinned checkout):

  data/maps/map_groups.json          group_order (mapGroup) and member order (mapNum)
  data/maps/<map>/map.json           connections, warp_events, object_events,
                                     coord_events, region_map_section, game_version
  data/maps/<map>/scripts.inc        the map's own script conditionals
  data/layouts/layouts.json          layout dimensions, tilesets and blockdata path
  data/layouts/<dir>/map.bin         GBA block data; collision is bits 10-11
  data/tilesets/*/*/metatile_attributes.bin
                                     metatile behaviour, low 8 bits (global.fieldmap.h)
  src/data/tilesets/{headers,metatiles}.h
                                     tileset -> attribute-file resolution
  include/constants/metatile_behaviors.h
                                     behaviour enum values
  include/regions.h                  region ranges (documentation of the model)
  data/maps/*/scripts.inc, data/scripts/*.inc
                                     `warp`/`warpsilent`/`warpteleport` commands

What it does:

  route     Dijkstra over (map, x, y) tiles using collision 0 as walkable, the
            `connections` table for map crossings (edge-triggered: a connection fires
            while the player stands on the source map's edge tile), warp events for
            doors/arrows/stairs, and area objects as obstacles. Emits probe
            `walk`/`assert-pos` lines.
  edges     Every map-transition edge whose two sides sit in different regions, with the
            story gate its scripts declare.
  warps     Cross-region transitions implemented as `warp`/`warpsilent`/`warpteleport`
            script commands, with the file and line that contains each.
  blockers  Resolves each gate's FLAG_/VAR_ conditions back to the scripts that set them.
  inventory Emits the machine-checkable cross-region inventory as JSON. `--check`
            compares it against the committed record, so the committed classification
            cannot drift from the pinned source without a gate failing. It also fails when
            an edge the analyzer could not decide has no verdict in the record's manual
            audit section, and when a manual verdict contradicts a derived one.

How an edge is classified, and what the analyzer refuses to claim
---------------------------------------------------------------
Three outcomes, and the third exists because honesty matters more than coverage:

  FUNCTIONAL -- proven from the pinned data:

    * a warp whose trigger metatile carries a behaviour the engine's warp predicates
      accept. `src/field_control_avatar.c`: `IsWarpMetatileBehavior` accepts
      `MB_ANIMATED_DOOR`, `MB_NON_ANIMATED_DOOR`, `MB_LADDER`, `MB_CRACKED_FLOOR_HOLE`,
      `MB_LAVARIDGE_GYM_1F_WARP`, `MB_UP_ESCALATOR`, `MB_DOWN_ESCALATOR`,
      `MB_WATER_DOOR`; `TryArrowWarp` accepts the four arrow warps plus
      `MB_WATER_SOUTH_ARROW_WARP` and `MB_DEEP_SOUTH_WARP`. The behaviour is decoded
      from the layout's tileset attribute arrays per `include/global.fieldmap.h`;
    * a `connections` entry with at least one crossing tile inside the engine's
      `dest = src - offset` window and both sides walkable.

  DEAD -- proven from the pinned data: a connection whose window is empty, or whose
    border column is entirely impassable.

  UNPROVEN -- reported, not asserted:

    * a warp on a trigger metatile that is NOT one of the behaviours above. 1719 of this
      build's 3608 warp tiles sit on ordinary floor and the engine still accepts many of
      them as step-on warps, so "not a warp behaviour" is NOT evidence of death. The
      record carries the observed behaviour, the arrival tile and both tiles'
      walkability so a reader can see exactly what was measured;
    * every script-command transition. Deciding reachability needs the script call
      graph, which is far larger than this analyzer; the commands are reported as
      CANDIDATES with file and line, and `shared_include` marks the ones in a file that
      holds more than one map's script set (the department-store elevators are the known
      example, and a manual audit must reject them).

The committed evidence record therefore splits its claims in two: a `tool_derived` half
this analyzer reproduces and `--check` verifies, and a `manual_engine_source_verified`
half that decides the UNPROVEN edges and records what an analyzer without a call graph
cannot. Neither half is presented as the other's work.

No ROM, save file, save state or emulator is involved, and nothing is downloaded.
The mode of a section's region follows DualDex: REGION_JOHTO -> Johto,
REGION_KANTO -> Kanto, REGION_HISUI -> Sinjoh, REGION_ALOLA -> Alola.

Usage:

  python3 tools/hns-map-data/hns_route.py route <map> <x> <y> map <dest-map>
  python3 tools/hns-map-data/hns_route.py route <map> <x> <y> tile <x,y>
  python3 tools/hns-map-data/hns_route.py edges [--gates]
  python3 tools/hns-map-data/hns_route.py warps
  python3 tools/hns-map-data/hns_route.py blockers
  python3 tools/hns-map-data/hns_route.py inventory [--write <path> | --check <path>]

The upstream checkout is located through HNS_UPSTREAM_DIR, then the repo-relative
`upstream-hns/pokehns-expansion`, then the conventional sibling layout.
"""

import argparse
import json
import os
import re
import struct
import subprocess
import sys
from collections import deque
import argparse
import json
import os
import struct
import sys
from collections import deque

DEFAULT_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

UPSTREAM_SEARCH_PATHS = [
    os.environ.get("HNS_UPSTREAM_DIR"),
    os.path.join(DEFAULT_REPO_ROOT, "upstream-hns/pokehns-expansion"),
    os.path.join(os.path.dirname(DEFAULT_REPO_ROOT), "upstream-hns/pokehns-expansion"),
]

PINNED_COMMIT_SHA = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"

# Tracked input paths this tool reads. A modified or deleted entry under any of these refuses the
# run: layout dimensions, block data and metatile attributes are all reachability evidence here.
REQUIRED_CLEAN_SOURCES = (
    "data/maps/",
    "data/layouts/",
    "data/tilesets/",
    "data/scripts/",
    "include/constants/",
    "src/data/tilesets/",
)

REGION_BY_SECTION_PREFIX = {
    "REGION_JOHTO": "JOHTO",
    "REGION_KANTO": "KANTO",
    "REGION_HISUI": "SINJOH",
    "REGION_ALOLA": "ALOLA",
}

DIRECTIONS = {(-1, 0): "LEFT", (1, 0): "RIGHT", (0, -1): "UP", (0, 1): "DOWN"}

# The press that moves the player onto a neighbouring tile.
DIRECTIONS_TO_DELTA = {name: delta for delta, name in DIRECTIONS.items()}


def find_upstream(explicit=None):
    for candidate in [explicit] + UPSTREAM_SEARCH_PATHS:
        if candidate and os.path.isfile(os.path.join(candidate, "data/maps/map_groups.json")):
            return os.path.abspath(candidate)
    raise SystemExit(
        "error: no pinned H&S upstream checkout found. Set HNS_UPSTREAM_DIR to a checkout of "
        "PokemonHnS-Development/pokehns-expansion at "
        f"{PINNED_COMMIT_SHA} ({PINNED_TAG})."
    )


def verify_provenance(upstream_dir):
    """Fail closed unless the checkout is the exact pinned revision with clean inputs.

    This tool's output is cited as evidence about the pinned 2.0.5 build, so it may not read an
    arbitrary or locally edited tree: a different revision could have different map groups, and a
    hand-edited `map.bin` or `metatile_attributes.bin` could manufacture a reachable route. The
    check mirrors `generate_hns_map_data.py`, widened to the paths this tool consumes.
    """
    try:
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=upstream_dir, capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        raise SystemExit(f"error: could not read the upstream git revision: {exc}")

    if head != PINNED_COMMIT_SHA:
        raise SystemExit(
            f"error: upstream checkout is at {head}, but this tool is pinned to "
            f"{PINNED_COMMIT_SHA} ({PINNED_TAG}). Refusing to analyse an unpinned revision."
        )

    status = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=upstream_dir, capture_output=True, text=True, check=True,
    ).stdout.strip()
    # An untracked file outside the read set cannot affect the analysis; CI's sparse checkout
    # produces plenty of them. A tracked file that is modified or deleted inside the read set can.
    blocking = [
        line for line in status.splitlines()
        if not line.startswith("??")
        and any(entry in line for entry in REQUIRED_CLEAN_SOURCES)
    ]
    if blocking:
        raise SystemExit(
            "error: upstream checkout has uncommitted changes in the files this tool reads; "
            "refusing to analyse a modified tree:\n  " + "\n  ".join(blocking)
        )
    return head



# --- metatile behaviours ---------------------------------------------------------------------
# A warp is only functional when the metatile the player triggers it from carries a behaviour the
# engine's warp predicates accept. `src/field_control_avatar.c`:
#   IsWarpMetatileBehavior   -- doors, ladders, cracked floors, escalators, water doors
#   TryArrowWarp             -- the four arrow warps plus their water/deep variants

# The primary/secondary metatile boundary the running engine uses.
#
# `include/fieldmap.h` defines NUM_METATILES_IN_PRIMARY 640 and NUM_METATILES_IN_PRIMARY_EMERALD 512,
# and `src/fieldmap.c:GetNumMetatilesInPrimary` returns 640 for both LAYOUT_VERSION_FRLG and
# LAYOUT_VERSION_HNS. Every H&S layout is one of those two, so 640 is the split the engine applies:
# 512 mis-resolves every tile in the 512..639 range to the wrong tileset, which is enough to turn a
# real arrow warp or door into ordinary floor and invert a reachability conclusion.
NUM_METATILES_IN_PRIMARY = 640

# `ELEVATION_TRANSITION` (`include/global.fieldmap.h`): a warp event with this elevation matches the
# player at any elevation, so it is a wildcard in `GetWarpEventAtPosition`.
ELEVATION_TRANSITION = 0

# The behaviour lists below are DERIVED from the pinned engine source, not transcribed from it.
#
# An earlier revision hardcoded them from the predicate helper NAMES, which was wrong in both
# directions: `MetatileBehavior_IsNonAnimDoor` also accepts `MB_DEEP_SOUTH_WARP`, and
# `MetatileBehavior_IsUnionRoomWarp` actually tests `MB_BRIDGE_OVER_OCEAN` (the helper names in this
# revision do not always match the constant they test). A transcription error there is invisible
# until it inverts a reachability conclusion, so the analyzer reads the predicates instead.
CONTROLLER_SOURCE = "src/field_control_avatar.c"
BEHAVIOUR_SOURCE = "src/metatile_behavior.c"

# Function name in `field_control_avatar.c` -> (warp path, definition signature). The signature is
# required because these functions are forward-declared at the top of the file.
# The two gate predicates, with the signatures their definitions use.
WARP_GATE_SIGNATURES = {
    "IsWarpMetatileBehavior": "static bool8 IsWarpMetatileBehavior(u16 metatileBehavior)",
    "IsArrowWarpMetatileBehavior": (
        "static bool8 IsArrowWarpMetatileBehavior(u16 metatileBehavior, enum Direction direction)"
    ),
}

ACTIVATION_FUNCTIONS = {
    "TryArrowWarp": (
        "arrow",
        "static bool8 TryArrowWarp(struct MapPosition *position, u16 metatileBehavior, "
        "enum Direction direction)",
    ),
    "TryDoorWarp": (
        "door",
        "static bool8 TryDoorWarp(struct MapPosition *position, u16 metatileBehavior, "
        "enum Direction direction)",
    ),
    "TryStartWarpEventScript": (
        "step",
        "static bool8 TryStartWarpEventScript(struct MapPosition *position, u16 metatileBehavior)",
    ),
}


def _c_function_body(source, signature):
    """Text of a C function DEFINITION, by brace matching.

    Callers must pass a signature that includes the parameter list. Matching a bare name would find
    the forward declaration at the top of the file, whose "body" is a semicolon and the unrelated
    declarations that follow it, which silently yields an empty behaviour set.
    """
    start = source.index(signature)
    open_brace = source.index("{", start)
    depth = 0
    for index in range(open_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise SystemExit("error: unbalanced braces after %r" % signature)


def _behaviours_a_predicate_accepts(controller_source, behaviour_source, predicate):
    """Behaviour constants a predicate reaches, resolving its helper calls to their comparisons.

    A predicate in this revision is a chain of `MetatileBehavior_Is<Name>(...)` calls, and each
    helper is a small function comparing against one or more `MB_*` constants. Resolving the chain is
    what makes the derived lists correct where the names alone are misleading.
    """
    body = _c_function_body(controller_source, WARP_GATE_SIGNATURES[predicate])
    found = set()
    for helper in re.findall(r"MetatileBehavior_Is(\w+)\(", body):
        inner = _c_function_body(
            behaviour_source, "MetatileBehavior_Is%s(u8 metatileBehavior)" % helper
        )
        found.update(re.findall(r"==\s*(MB_[A-Z0-9_]+)", inner))
    return found


def _extract_warp_behaviours(root, behaviours):
    """`{path: frozenset(behaviour names)}` for the three field-input warp paths."""
    with open(os.path.join(root, CONTROLLER_SOURCE), encoding="utf-8") as handle:
        controller = handle.read()
    with open(os.path.join(root, BEHAVIOUR_SOURCE), encoding="utf-8") as handle:
        behaviour_source = handle.read()

    arrow = _behaviours_a_predicate_accepts(
        controller, behaviour_source, "IsArrowWarpMetatileBehavior"
    )
    # The step-on path is gated by IsWarpMetatileBehavior; TryDoorWarp adds the DIR_NORTH
    # warp-door requirement and is checked separately, so `step` is exactly the predicate's set.
    step = _behaviours_a_predicate_accepts(controller, behaviour_source, "IsWarpMetatileBehavior")
    # `TryDoorWarp` requires `MetatileBehavior_IsWarpDoor` on the tile the player walks into, which
    # resolves to MB_ANIMATED_DOOR in this revision.
    door_body = _c_function_body(controller, ACTIVATION_FUNCTIONS["TryDoorWarp"][1])
    door = set()
    for helper in re.findall(r"MetatileBehavior_IsWarpDoor\(", door_body):
        inner = _c_function_body(
            behaviour_source, "MetatileBehavior_IsWarpDoor(u8 metatileBehavior)"
        )
        door.update(re.findall(r"==\s*(MB_[A-Z0-9_]+)", inner))

    resolved = {}
    for path, constants in (("arrow", arrow), ("step", step), ("door", door or set())):
        names = set()
        for constant in constants:
            if constant not in behaviours.values:
                raise SystemExit(
                    "error: %s names %s, which is not in the pinned behaviour enum"
                    % (CONTROLLER_SOURCE, constant)
                )
            names.add(constant)
        resolved[path] = frozenset(names)
    if not resolved["arrow"] or not resolved["step"] or not resolved["door"]:
        raise SystemExit("error: could not derive the warp-behaviour sets from the pinned source")
    return resolved


def verified_source(upstream_dir=None):
    """Locate, verify and open the pinned checkout.

    Every command goes through here, so there is exactly one place that can decide what counts as
    the pinned build. Returning a `Source` without verifying would defeat the purpose, which is why
    the constructor is not called anywhere else in this module.
    """
    root = find_upstream(upstream_dir)
    verify_provenance(root)
    return Source(root)


class BehaviourSource:
    """The pinned build's metatile-behaviour tables.

    Metatile attributes are a per-tileset array; `include/global.fieldmap.h` fixes the layout as an
    8-bit behaviour in the low bits of a 16-bit word, so `attributes[id] & 0xFF` is the behaviour.
    Resolving a map tile to a behaviour therefore needs three things this class caches: the
    behaviour enum values, the tileset -> attribute-file mapping, and the decoded arrays.
    """

    def __init__(self, root):
        self.root = root
        self.names = self._parse_behaviour_enum()
        self.values = {name: value for name, value in self.names.items()}
        self._attribute_paths = self._parse_attribute_paths()
        self._tileset_attribute_constant = self._parse_tileset_headers()
        self._arrays = {}
        self.paths = _extract_warp_behaviours(root, self)

    def _parse_behaviour_enum(self):
        path = os.path.join(self.root, "include/constants/metatile_behaviors.h")
        source = open(path, encoding="utf-8").read()
        body = source[source.index("{") + 1:source.index("};")]
        names = {}
        value = -1
        for line in body.splitlines():
            line = re.sub(r"//.*", "", line).strip().rstrip(",")
            if not line:
                continue
            if "=" in line:
                name, raw = line.split("=", 1)
                value = int(raw.strip(), 0)
                names[name.strip()] = value
            else:
                value += 1
                names[line] = value
        if not names:
            raise SystemExit("error: could not parse the metatile behaviour enum")
        # Anchor the parse instead of trusting it: `MB_NORMAL` is the enum's first member and the
        # zero-fill of every attribute array, so if it did not resolve to 0 then either the enum or
        # the low-8-bit behaviour decode is wrong, and every classification downstream would be
        # built on a mis-read tile.
        if names.get("MB_NORMAL") != 0:
            raise SystemExit(
                "error: unexpected metatile behaviour enum shape (MB_NORMAL is %r, expected 0)"
                % names.get("MB_NORMAL")
            )
        if names.get("MB_TALL_GRASS") != 2:
            raise SystemExit(
                "error: unexpected metatile behaviour enum shape (MB_TALL_GRASS is %r, expected 2)"
                % names.get("MB_TALL_GRASS")
            )
        return names

    def _parse_attribute_paths(self):
        path = os.path.join(self.root, "src/data/tilesets/metatiles.h")
        source = open(path, encoding="utf-8").read()
        found = {}
        pattern = (
            r"gMetatileAttributes_([A-Za-z0-9_]+)"
            + r"\["
            + r"\]"
            + r"[^;]*?INCBIN_U16\(\"([^\"]+)\"\)"
        )
        for match in re.finditer(pattern, source):
            found[match.group(1)] = match.group(2)
        return found

    def _parse_tileset_headers(self):
        path = os.path.join(self.root, "src/data/tilesets/headers.h")
        source = open(path, encoding="utf-8").read()
        found = {}
        pattern = (
            r"const struct Tileset (gTileset_[A-Za-z0-9_]+) =\s*\{"
            + r"(.*?)\n\};"
        )
        for match in re.finditer(pattern, source, re.S):
            body = match.group(2)
            attributes = re.search(r"\.metatileAttributes\s*=\s*(\w+)", body)
            if attributes:
                found[match.group(1)] = attributes.group(1).replace(
                    "gMetatileAttributes_", ""
                )
        return found

    def behaviour_of(self, tileset, metatile_id):
        """Behaviour of a metatile in a map's tileset pair, or None when it cannot be resolved.

        `metatile_id < 512` selects the primary tileset; anything above it indexes the secondary.
        """
        constant = self._tileset_attribute_constant.get(tileset)
        if constant is None:
            return None
        if constant not in self._arrays:
            relative = self._attribute_paths.get(constant)
            if relative is None:
                self._arrays[constant] = None
            else:
                path = os.path.join(self.root, relative)
                if not os.path.isfile(path):
                    self._arrays[constant] = None
                else:
                    with open(path, "rb") as handle:
                        self._arrays[constant] = handle.read()
        data = self._arrays[constant]
        if data is None:
            return None
        if metatile_id >= NUM_METATILES_IN_PRIMARY:
            metatile_id -= NUM_METATILES_IN_PRIMARY
        offset = metatile_id * 2
        if offset + 2 > len(data):
            return None
        return struct.unpack("<H", data[offset:offset + 2])[0] & 0x00FF

    def name_of(self, behaviour):
        for name, value in self.names.items():
            if value == behaviour:
                return name
        return None

    def is_warp_behaviour(self, behaviour):
        """`IsWarpMetatileBehavior` -- the step-on path."""
        return self.name_of(behaviour) in self.paths["step"]

    def is_arrow_behaviour(self, behaviour):
        """`IsArrowWarpMetatileBehavior` -- the held-direction path."""
        return self.name_of(behaviour) in self.paths["arrow"]

    def is_door_behaviour(self, behaviour):
        """`TryDoorWarp`'s warp-door requirement for the tile the player walks into."""
        return self.name_of(behaviour) in self.paths["door"]


# --- script-command warps ---------------------------------------------------------------------
# Some cross-region transitions are `warp`/`warpsilent`/`warpteleport` script commands rather than
# map data. They are found by scanning the pinned `.inc` files; the source file is the attribution,
# and a warp inside `data/scripts/` (a shared include) is reported as shared rather than attributed
# to one map, because a shared file holds more than one script set.
WARP_COMMAND_RE = re.compile(
    r"^\s*(warp|warpsilent|warpteleport)\s+([A-Za-z0-9_]+)\s*,", re.M
)


class Source:
    """Lazily parsed view of the pinned checkout's map data."""

    def __init__(self, root):
        self.root = root
        self.behaviours = BehaviourSource(root)
        self.maps_dir = os.path.join(root, "data/maps")
        with open(os.path.join(root, "data/layouts/layouts.json"), encoding="utf-8") as handle:
            self.layouts = {entry["id"]: entry for entry in json.load(handle)["layouts"]}
        with open(os.path.join(self.maps_dir, "map_groups.json"), encoding="utf-8") as handle:
            groups = json.load(handle)
        self.meta_cache = {}
        self.collision_cache = {}
        self._gate_cache = {}

        # `mapGroup` is the index into group_order; `mapNum` is the index inside the group. Both
        # come from file order, so the identity of a raw pair is only correct if this walk is.
        self.group_of = {}
        self.num_of = {}
        self.name_by_id = {}
        self.section_of = {}
        for group_index, group_name in enumerate(groups["group_order"]):
            for num, name in enumerate(groups[group_name]):
                meta = self.meta(name)
                if not meta:
                    continue
                self.group_of[name] = group_index
                self.num_of[name] = num
                self.name_by_id[meta["id"]] = name
                self.section_of[name] = meta.get("region_map_section")

    def meta(self, name):
        if name not in self.meta_cache:
            try:
                with open(os.path.join(self.maps_dir, name, "map.json"), encoding="utf-8") as handle:
                    self.meta_cache[name] = json.load(handle)
            except (OSError, json.JSONDecodeError):
                self.meta_cache[name] = None
        return self.meta_cache[name]

    def collision(self, name):
        """(width, height, grid) where grid[y][x] is the collision bits 10-11."""
        if name not in self.collision_cache:
            meta = self.meta(name)
            if not meta:
                self.collision_cache[name] = None
            else:
                layout = self.layouts[meta["layout"]]
                width, height = layout["width"], layout["height"]
                with open(os.path.join(self.root, layout["blockdata_filepath"]), "rb") as handle:
                    raw = handle.read()
                values = struct.unpack("<%dH" % (width * height), raw[: width * height * 2])
                grid = [
                    [(values[y * width + x] >> 10) & 3 for x in range(width)]
                    for y in range(height)
                ]
                self.collision_cache[name] = (width, height, grid)
        return self.collision_cache[name]

    def region(self, name):
        """DualDex region of a map.

        The upstream `map.json` `region` field carries the build's own REGION_* constant; the
        committed `generate_hns_map_data.py` maps exactly these onto the Kotlin `RegionId` values,
        so this tool reports the same region the production resolver does.
        """
        meta = self.meta(name)
        if not meta:
            return None
        return REGION_BY_SECTION_PREFIX.get(meta.get("region"))

    def script_warps(self):
        """Cross-region `warp`/`warpsilent`/`warpteleport` commands in the pinned scripts.

        Returns `(from_file, line, command, target_map, from_region, to_region)` for every command
        whose target is a known H&S map in a different region than the file's own map. A command in
        `data/scripts/` (a shared include) is attributed to the shared file rather than to one map,
        because one shared file can hold several independent script sets and attributing it to a map
        would invent a source that the call graph does not support.
        """
        results = []
        roots = [self.maps_dir, os.path.join(self.root, "data/scripts")]
        for root in roots:
            for dirpath, _dirnames, filenames in os.walk(root):
                for filename in sorted(filenames):
                    if not filename.endswith(".inc"):
                        continue
                    path = os.path.join(dirpath, filename)
                    owner = os.path.basename(dirpath) if dirpath.startswith(self.maps_dir) else None
                    from_region = self.region(owner) if owner else None
                    with open(path, encoding="utf-8", errors="replace") as handle:
                        for number, line in enumerate(handle, start=1):
                            match = WARP_COMMAND_RE.match(line)
                            if not match:
                                continue
                            target = self.name_by_id.get(match.group(2))
                            if not target:
                                continue
                            to_region = self.region(target)
                            if not from_region or not to_region or to_region == from_region:
                                continue
                            results.append((
                                os.path.relpath(path, self.root),
                                number,
                                match.group(1),
                                target,
                                from_region,
                                to_region,
                                owner,
                            ))
        return sorted(results)

    def story_gates(self, name):
        """Story conditions the destination map's own scripts require before it lets you through.

        Reported as `(kind, subject, value, script)` tuples parsed from the map's script file:
        `goto_if_unset FLAG_X, ...`, `goto_if_set FLAG_X, ...`, `goto_if_lt VAR_X, n, ...`,
        `goto_if_ge VAR_X, n, ...`. This is a *locator*, not a simulation: it names the flags and
        variables a scenario must already have satisfied, so "is this cross-region transition
        reachable from an early legal save?" can be answered without playing the game.
        """
        if name in self._gate_cache:
            return self._gate_cache[name]
        gates = []
        path = os.path.join(self.maps_dir, name, "scripts.inc")
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as handle:
                for raw in handle:
                    line = raw.strip()
                    for opcode, kind in (
                        ("goto_if_unset", "requires unset"),
                        ("goto_if_set", "requires set"),
                        ("goto_if_lt", "requires >="),
                        ("goto_if_ge", "requires <"),
                        ("goto_if_eq", "requires !="),
                        ("goto_if_ne", "requires =="),
                    ):
                        if not line.startswith(opcode + " "):
                            continue
                        parts = [part.strip() for part in line[len(opcode) + 1:].split(",")]
                        if len(parts) < 2:
                            continue
                        subject, value = parts[0], parts[1]
                        if not (subject.startswith("FLAG_") or subject.startswith("VAR_")):
                            continue
                        if subject.startswith("VAR_TEMP"):
                            continue
                        gates.append((kind, subject, value))
        self._gate_cache[name] = gates
        return gates

    def blocked_by_object(self, name, x, y):
        meta = self.meta(name)
        if not meta:
            return False
        return any(
            obj.get("x") == x and obj.get("y") == y for obj in (meta.get("object_events") or [])
        )

    def walkable(self, name, x, y, respect_objects=True):
        grid_info = self.collision(name)
        if not grid_info:
            return False
        width, height, grid = grid_info
        if not (0 <= x < width and 0 <= y < height):
            return False
        if grid[y][x] != 0:
            return False
        return not (respect_objects and self.blocked_by_object(name, x, y))

    def behaviour_at(self, name, x, y):
        """The metatile behaviour of a tile, or None when it cannot be resolved.

        `map.bin` carries only collision and elevation; the behaviour lives in the layout's
        tileset attribute arrays, which is why an honest warp classification has to read them.
        """
        info = self.collision(name)
        meta = self.meta(name)
        if not info or not meta:
            return None
        width, height, grid = info
        if not (0 <= x < width and 0 <= y < height):
            return None
        layout = self.layouts[meta["layout"]]
        raw = self._block_values(meta["layout"])
        value = raw[y * width + x]
        metatile_id = value & 0x03FF
        tileset = (
            layout["primary_tileset"]
            if metatile_id < NUM_METATILES_IN_PRIMARY
            else layout["secondary_tileset"]
        )
        return self.behaviours.behaviour_of(tileset, metatile_id)

    def elevation_at(self, name, x, y):
        """The elevation nibble of a tile, for the warp event's elevation match."""
        info = self.collision(name)
        meta = self.meta(name)
        if not info or not meta:
            return None
        width, height, _ = info
        if not (0 <= x < width and 0 <= y < height):
            return None
        value = self._block_values(meta["layout"])[y * width + x]
        return (value >> 12) & 0x0F

    def warp_at(self, name, x, y):
        """The map's own warp event at a tile, or None.

        `GetWarpEventAtPosition` matches map coordinates and an elevation that is either the tile's
        own or `ELEVATION_TRANSITION`, so a warp whose elevation does not match is inert.
        """
        meta = self.meta(name)
        if not meta:
            return None
        elevation = self.elevation_at(name, x, y)
        for index, warp in enumerate(meta.get("warp_events") or []):
            if warp["x"] != x or warp["y"] != y:
                continue
            declared = warp.get("elevation", 0)
            if declared == elevation or declared == ELEVATION_TRANSITION:
                return index, warp
        return None

    def behaviour_name_at(self, name, x, y):
        behaviour = self.behaviour_at(name, x, y)
        if behaviour is None:
            return None
        return self.behaviours.name_of(behaviour)

    def _block_values(self, layout_id):
        if not hasattr(self, "_blocks"):
            self._blocks = {}
        if layout_id not in self._blocks:
            layout = self.layouts[layout_id]
            width, height = layout["width"], layout["height"]
            with open(os.path.join(self.root, layout["blockdata_filepath"]), "rb") as handle:
                raw = handle.read()
            self._blocks[layout_id] = struct.unpack(
                "<%dH" % (width * height), raw[: width * height * 2]
            )
        return self._blocks[layout_id]

    def _arrival_tile(self, destination, door):
        """The tile the player lands on: the destination map's own `dest_warp_id` entry."""
        index = self._warp_index(door)
        if index is None:
            return (0, 0)
        warps = self.meta(destination).get("warp_events") or []
        if index >= len(warps):
            return (0, 0)
        return (warps[index]["x"], warps[index]["y"])

    def warp_activation(self, name, x, y):
        """How, if at all, the running engine would fire the warp event at this tile.

        `src/field_control_avatar.c` has three field-input paths, and each needs a metatile behaviour
        the plain "does a warp_def exist here" question does not capture:

          `TryArrowWarp` (line 972)       player STANDS on the tile, holds the facing direction, and
                                          the tile carries `IsArrowWarpMetatileBehavior` for it;
          `TryDoorWarp` (line 1139)       player faces north and the tile IN FRONT is a warp door
                                          (`MB_ANIMATED_DOOR` / `MB_NON_ANIMATED_DOOR`) with a warp
                                          event on that front tile;
          `TryStartWarpEventScript` (1002) player STEPS ONTO the tile and the tile carries
                                          `IsWarpMetatileBehavior`.

        Every one of them also needs `GetWarpEventAtPosition` to match, which requires the warp
        event's elevation to equal the tile's or be `ELEVATION_TRANSITION`.

        A `warp_def` on a tile with none of those behaviours is an inert anchor: many entries in this
        build exist only as the destination half of a scripted `warp`, and the scripted form calls
        `DoWarp` directly without consulting any metatile behaviour.

        Returns None when the behaviour could not be resolved (the analyzer then says UNPROVEN rather
        than guessing), else a dict with `reachable`, `path`, `reason` and the observed evidence.
        """
        behaviour = self.behaviour_at(name, x, y)
        if behaviour is None:
            return None
        name_of = self.behaviours.name_of(behaviour)
        hit = self.warp_at(name, x, y)
        evidence = {
            "behaviour": name_of,
            "warp_event_present": hit is not None,
            "tile_walkable": self.walkable(name, x, y),
        }

        # Arrow warp: the player stands here and holds the direction. The behaviour has to be the
        # arrow warp for that direction, so an arrow warp event on ordinary floor is inert.
        if self.behaviours.is_arrow_behaviour(behaviour):
            if hit is None:
                return dict(evidence, reachable=False, path=None,
                            reason="the only arrow-warp behaviour here has no matching warp event")
            return dict(evidence, reachable=True, path="arrow", reason=None)

        # Step-on warp: the player steps onto this tile and it carries a warp behaviour.
        if self.behaviours.is_warp_behaviour(behaviour):
            if hit is None:
                return dict(evidence, reachable=False, path=None,
                            reason="the only warp behaviour here has no matching warp event")
            return dict(evidence, reachable=True, path="step", reason=None)

        # Door warp: the warp event lives on THIS tile and the player walks into it from the front,
        # which is the one path where the trigger tile's behaviour can be a door and the player is
        # never standing on it.
        if self.behaviours.is_door_behaviour(behaviour):
            if hit is not None and self._walkable_in_front(name, x, y):
                return dict(evidence, reachable=True, path="door", reason=None)
            if hit is None:
                return dict(evidence, reachable=False, path=None,
                            reason="a warp-door tile with no warp event on it")
            return dict(evidence, reachable=False, path=None,
                        reason="a warp door with no walkable tile in front to trigger it from")

        return dict(
            evidence,
            reachable=False,
            path=None,
            reason=(
                "%s is neither IsWarpMetatileBehavior, IsArrowWarpMetatileBehavior nor a warp door, "
                "so no field-input warp predicate fires here; a warp_def on this tile is an inert "
                "anchor (its destination is normally reached by a scripted warp instead)"
                % name_of
            ),
        )

    def _walkable_in_front(self, name, x, y):
        """True when any orthogonal neighbour is walkable, i.e. a door could be walked into."""
        return any(
            self.walkable(name, x + dx, y + dy) for dx, dy in ((0, 1), (0, -1), (-1, 0), (1, 0))
        )

    def _warp_is_functional(self, name, x, y):
        """Whether the engine's warp predicates can fire at this tile or the one in front of it.

        `TryDoorWarp` tests the metatile AHEAD of the player, and `TryArrowWarp` tests the tile the
        player stands on, so both are checked. A tile whose behaviour is missing cannot be proven
        functional, so it is reported as unproven rather than assumed live.
        """
        info = self.collision(name)
        if not info:
            return None
        width, height, grid = info
        candidates = [(x, y)]
        for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            candidates.append((x + dx, y + dy))
        seen_any = False
        for cx, cy in candidates:
            if not (0 <= cx < width and 0 <= cy < height):
                continue
            behaviour = self.behaviour_at(name, cx, cy)
            if behaviour is None:
                continue
            seen_any = True
            if self.behaviours.is_warp_behaviour(behaviour):
                return True
        # A tile that is not walkable is only reachable as a door target from the tile in front,
        # so an unproven behaviour on an impassable tile must not be reported as live.
        return False if seen_any else None

    def connection_steps(self, name, x, y):
        """Crossings available from the exact edge tile (x, y), as `(kind, direction, map, x, y)`.

        Two mechanisms cross a map boundary here, and they are NOT interchangeable:

        * a `connections` entry is **edge-triggered** -- it fires while the player stands ON the
          source map's edge tile, so the step is taken from that tile;
        * a `warp_events` entry carrying a door/arrow/stair metatile behaviour is triggered from a
          tile the player can stand on. A door in particular is entered from the tile IN FRONT of it
          (the engine tests the metatile ahead of the player), so the door tile itself may have
          collision and still be the correct destination of a `warp_def`.
        """
        grid_info = self.collision(name)
        meta = self.meta(name)
        if not grid_info or not meta:
            return
        width, height, _ = grid_info

        for door in meta.get("warp_events") or []:
            destination = self.name_by_id.get(door["dest_map"])
            target_grid = self.collision(destination) if destination else None
            if not target_grid:
                continue
            index = self._warp_index(door)
            if index is None:
                continue
            target_warps = self.meta(destination).get("warp_events") or []
            if index >= len(target_warps):
                continue
            arrival = (destination, target_warps[index]["x"], target_warps[index]["y"])
            if not self.walkable(*arrival):
                continue
            door_x, door_y = door["x"], door["y"]
            # Standing ON an arrow warp or a step-on warp, holding the direction it responds to.
            activation = self.warp_activation(name, x, y)
            if activation and activation["reachable"] and (x, y) == (door_x, door_y):
                if activation["path"] == "arrow":
                    for direction, (dx, dy) in DIRECTIONS_TO_DELTA.items():
                        yield ("warp", direction, arrival[0], arrival[1], arrival[2])
                    continue
                if activation["path"] == "step":
                    yield ("warp", "UP", arrival[0], arrival[1], arrival[2])
                    continue
            # Walking INTO a warp door from directly below it (TryDoorWarp requires DIR_NORTH), so
            # the trigger tile is (door_x, door_y + 1) and the door itself need not be walkable.
            if x == door_x and y == door_y + 1:
                front = self.behaviour_at(name, door_x, door_y)
                if front is not None and self.behaviours.is_door_behaviour(front):
                    activation = self.warp_activation(name, door_x, door_y)
                    if activation and activation["reachable"]:
                        yield ("warp", "UP", arrival[0], arrival[1], arrival[2])

        on = set()
        if x == width - 1:
            on.add("right")
        if x == 0:
            on.add("left")
        if y == 0:
            on.add("up")
        if y == height - 1:
            on.add("down")
        if not on:
            return
        for conn in meta.get("connections") or []:
            direction = conn.get("direction")
            if direction not in on:
                continue
            target = self.name_by_id.get(conn["map"])
            target_grid = self.collision(target) if target else None
            if not target_grid:
                continue
            tw, th, _ = target_grid
            offset = conn.get("offset", 0)
            if direction == "right":
                candidate = (target, 0, y - offset)
            elif direction == "left":
                candidate = (target, tw - 1, y - offset)
            elif direction == "down":
                candidate = (target, x - offset, 0)
            else:
                candidate = (target, x - offset, th - 1)
            if self.walkable(*candidate):
                yield ("connection", direction, candidate[0], candidate[1], candidate[2])

    def _warp_index(self, door):
        raw = door.get("dest_warp_id", 0)
        try:
            return int(raw)
        except (TypeError, ValueError):
            return None

    def plan(self, start_map, start, goal_test, respect_objects=True, allow_warps=True):
        """Shortest tile path as `(map, x, y, crossing)` steps, or None.

        A map crossing is a step of its own carrying `(kind, direction_to_press)`. The direction
        comes from the map data -- a `connections` entry's own direction, or the direction the player
        must face to enter a door -- never from the coordinate delta between two maps: moving off New
        Bark Town's left edge at x=0 arrives on Route 29 at x=69, so the raw delta would report RIGHT
        for a LEFTward crossing.

        Raises SystemExit for an unknown map.
        """
        if self.meta(start_map) is None:
            raise SystemExit("error: unknown map %r" % start_map)
        start_step = (start_map, start[0], start[1], None)
        dist = {start_step: 0}
        prev = {}
        queue = deque([start_step])
        goal = None
        while queue:
            cur = queue.popleft()
            if goal_test(cur[0], cur[1], cur[2]):
                goal = cur
                break
            name, x, y, _ = cur
            grid_info = self.collision(name)
            if not grid_info:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                neighbour = (name, x + dx, y + dy, None)
                if neighbour in dist or not self.walkable(name, x + dx, y + dy, respect_objects):
                    continue
                dist[neighbour] = dist[cur] + 1
                prev[neighbour] = cur
                queue.append(neighbour)

            for kind, direction, target, tx, ty in self.connection_steps(name, x, y):
                if kind == "warp" and not allow_warps:
                    continue
                crossing = (name, x, y, (kind, direction))
                arrival = (target, tx, ty, None)
                if crossing not in dist:
                    dist[crossing] = dist[cur] + 1
                    prev[crossing] = cur
                if arrival in dist:
                    continue
                dist[arrival] = dist[crossing] + 1
                prev[arrival] = crossing
                queue.append(arrival)
        if goal is None:
            return None
        path = []
        cur = goal
        while cur is not None:
            path.append(cur)
            cur = prev.get(cur)
        return path[::-1]

    def all_edges(self):
        """Every declared map-transition edge, classified by mechanism and reachability.

        Yields `(from, at, to, mechanism)` where mechanism is `connection <dir>` when the crossing is
        edge-triggered, `warp` when a warp event carries it, or `dead <reason>` when the border exists
        in the data but no engine predicate can fire it.

        A `dead` classification matters for a cross-region audit: the pinned build declares a
        `Route26North_hns --right--> Route22_hns` connection and two Kanto -> New Bark Town warps that
        the running engine can never trigger, and counting them as working crossings would overstate
        how many ways into Kanto exist.
        """
        for name in self.group_of:
            grid_info = self.collision(name)
            meta = self.meta(name)
            if not grid_info or not meta:
                continue
            width, height, grid = grid_info

            for door in meta.get("warp_events") or []:
                target = self.name_by_id.get(door["dest_map"])
                if not target:
                    continue
                x, y = door["x"], door["y"]
                in_bounds = 0 <= x < width and 0 <= y < height
                if not in_bounds:
                    yield name, (x, y), target, "dead warp outside the layout"
                    continue
                activation = self.warp_activation(name, x, y)
                if activation is None:
                    yield name, (x, y), target, "unproven warp"
                elif not activation["reachable"]:
                    yield name, (x, y), target, (
                        "dead warp (%s)" % activation["reason"]
                    )
                else:
                    yield name, (x, y), target, "warp"
            for conn in meta.get("connections") or []:
                target = self.name_by_id.get(conn["map"])
                if not target or not self.collision(target):
                    continue
                tw, th, tgrid = self.collision(target)
                offset, direction = conn.get("offset", 0), conn["direction"]
                # The engine's rule is `dest_coord = source_coord - offset`, and the crossing window
                # is empty when the offset cannot be satisfied inside the destination's extent.
                crossings = 0
                for y in range(height):
                    for x in range(width):
                        if grid[y][x] != 0:
                            continue
                        if direction == "right" and x == width - 1:
                            ty = y - offset
                            if 0 <= ty < th and tgrid[ty][0] == 0:
                                crossings += 1
                        elif direction == "left" and x == 0:
                            ty = y - offset
                            if 0 <= ty < th and tgrid[ty][tw - 1] == 0:
                                crossings += 1
                        elif direction == "down" and y == height - 1:
                            tx = x - offset
                            if 0 <= tx < tw and tgrid[0][tx] == 0:
                                crossings += 1
                        elif direction == "up" and y == 0:
                            tx = x - offset
                            if 0 <= tx < tw and tgrid[th - 1][tx] == 0:
                                crossings += 1
                if crossings == 0:
                    yield name, (0, 0), target, "dead %s connection (empty crossing window)" % direction
                else:
                    yield name, (0, 0), target, "connection %s" % direction

def emit(path):
    """Probe script lines: `walk` runs with an `assert-pos` before and after each map crossing.

    A crossing step carries the direction the player must move to trigger it, so the emitted script
    never guesses a direction from coordinates that live in two different map spaces. `assert-pos`
    is emitted BEFORE the crossing press -- the press itself moves the player onto the destination
    map, so an assertion after it would check the arrival tile, not the edge the crossing fired
    from -- and the following step's assert covers the arrival.
    """
    lines = []
    run_direction, run_tiles = None, 0

    def flush():
        nonlocal run_direction, run_tiles
        if run_direction:
            lines.append("walk %s %d" % (run_direction, run_tiles))
            run_direction, run_tiles = None, 0

    for index in range(1, len(path)):
        previous_map, previous_x, previous_y, _ = path[index - 1]
        name, x, y, crossing = path[index]

        if crossing is not None:
            flush()
            lines.append("assert-pos %d %d" % (previous_x, previous_y))
            lines.append("walk %s 1 optional" % crossing[1].upper())
            continue

        if previous_map != name:
            # Arrival tile of the crossing just emitted; the final/next assert-pos covers it.
            flush()
            continue

        direction = DIRECTIONS[(x - previous_x, y - previous_y)]
        if direction == run_direction:
            run_tiles += 1
        else:
            flush()
            run_direction, run_tiles = direction, 1
    flush()
    lines.append("assert-pos %d %d" % (path[-1][1], path[-1][2]))
    return lines



# --- machine-checkable cross-region inventory --------------------------------------------------
# `inventory` emits exactly the claims the analyzer can reproduce from the pinned tree, and
# `--check` compares them against the committed record. That is what stops the committed
# classification from drifting away from the source it claims to describe.
EVIDENCE_SCHEMA = "dualdex.hns.cross-region-inventory/1"


def build_inventory(source):
    """The analyzer's own cross-region claims, as a JSON-serialisable document."""
    functional = []
    dead = []
    for name, at, target, mechanism in source.all_edges():
        from_region, to_region = source.region(name), source.region(target)
        if not from_region or not to_region or from_region == to_region:
            continue
        if mechanism.startswith("connection "):
            functional.append({
                "kind": "connection",
                "direction": mechanism.split(" ", 1)[1],
                "from_map": name,
                "from_group_num": [source.group_of.get(name), source.num_of.get(name)],
                "from_region": from_region,
                "from_section": source.section_of.get(name),
                "to_map": target,
                "to_group_num": [source.group_of.get(target), source.num_of.get(target)],
                "to_region": to_region,
                "to_section": source.section_of.get(target),
            })
        elif mechanism == "warp":
            x, y = at
            activation = source.warp_activation(name, x, y) or {}
            functional.append({
                "kind": "warp",
                "from_map": name,
                "from_group_num": [source.group_of.get(name), source.num_of.get(name)],
                "from_region": from_region,
                "from_section": source.section_of.get(name),
                "to_map": target,
                "to_group_num": [source.group_of.get(target), source.num_of.get(target)],
                "to_region": to_region,
                "to_section": source.section_of.get(target),
                "at": [x, y],
                "behaviour": activation.get("behaviour"),
                "activation_path": activation.get("path"),
                "tile_walkable": activation.get("tile_walkable"),
                "warp_event_present": activation.get("warp_event_present"),
                "engine_predicate": ACTIVATION_PREDICATES.get(activation.get("path")),
                "gate": sorted({"%s %s %s" % g for g in source.story_gates(target)}),
                "from_gate": sorted({"%s %s %s" % g for g in source.story_gates(name)}),
            })
        elif mechanism.startswith("dead ") or mechanism.startswith("unproven "):
            record = {
                "from_map": name,
                "from_group_num": [source.group_of.get(name), source.num_of.get(name)],
                "from_region": from_region,
                "from_section": source.section_of.get(name),
                "to_map": target,
                "to_group_num": [source.group_of.get(target), source.num_of.get(target)],
                "to_region": to_region,
                "to_section": source.section_of.get(target),
                "at": list(at),
                "reason_class": mechanism,
                "reason": DEAD_REASONS.get(mechanism.split(" (")[0], mechanism),
            }
            if "warp" in mechanism:
                activation = source.warp_activation(name, at[0], at[1]) or {}
                record["trigger_behaviour"] = activation.get("behaviour")
                record["trigger_tile_walkable"] = activation.get("tile_walkable")
                record["warp_event_present"] = activation.get("warp_event_present")
                for door in source.meta(name).get("warp_events") or []:
                    if (door["x"], door["y"]) == tuple(at):
                        arrival = source._arrival_tile(target, door)
                        record["arrival_tile"] = list(arrival)
                        record["arrival_tile_walkable"] = source.walkable(target, *arrival)
                        break
            dead.append(record)

    def sort_key(entry):
        return (entry["from_region"], entry["to_region"], entry["from_map"], entry["to_map"])

    return {
        "schema": EVIDENCE_SCHEMA,
        "upstream_commit": PINNED_COMMIT_SHA,
        "upstream_tag": PINNED_TAG,
        "producer": "python3 tools/hns-map-data/hns_route.py inventory",
        "region_ranges": {
            "KANTO": "MAPSEC_PALLET_TOWN=20 .. MAPSEC_POWER_PLANT=62",
            "JOHTO": "MAPSEC_NEW_BARK_TOWN=63 .. MAPSEC_WHIRL_ISLANDS=124",
            "ALOLA": "MAPSEC_MELEMELE_ISLAND=9 .. MAPSEC_ULAULA_CAVE_2=18",
            "SINJOH": "MAPSEC_SNOWSWEPT_CAVERN=109 .. MAPSEC_SINJOH_RUINS=113",
        },
        "functional_edges": sorted(functional, key=sort_key),
        # Named for what it is: each entry is either a DEAD edge the analyzer derived, or an edge it
        # could not decide from map data. Calling the list "dead_edges" would claim more than the
        # entries support.
        "undecided_from_map_data": sorted(dead, key=sort_key),
        "script_warp_candidates": [
            {
                "file": relative,
                "line": number,
                "command": command,
                "to_map": target,
                "from_region": from_region,
                "to_region": to_region,
                "owner_map": owner,
            }
            for (relative, number, command, target, from_region, to_region, owner)
            in source.script_warps()
        ],
    }


# Which `src/field_control_avatar.c` predicate makes each activation path fire. Recorded per edge so
# a reader can check the classification against the engine without re-deriving the model.
ACTIVATION_PREDICATES = {
    "arrow": "TryArrowWarp (src/field_control_avatar.c) -- player standing on the tile, holding the "
             "facing direction, IsArrowWarpMetatileBehavior(tile) is TRUE",
    "door": "TryDoorWarp (src/field_control_avatar.c) -- player facing north (DIR_NORTH) with "
            "MetatileBehavior_IsWarpDoor on the tile in front",
    "step": "TryStartWarpEventScript (src/field_control_avatar.c) -- player steps onto the tile and "
            "IsWarpMetatileBehavior(tile) is TRUE",
}

DEAD_REASONS = {
    "dead warp outside the layout":
        "the warp_def sits outside the map's own dimensions, so no tile can trigger it",
    "dead warp":
        "no field-input warp predicate fires here. src/field_control_avatar.c reaches a warp through "
        "TryArrowWarp (player on the tile, holding the direction, IsArrowWarpMetatileBehavior), "
        "TryDoorWarp (facing north into a warp-door tile) or TryStartWarpEventScript (stepping onto "
        "a tile where IsWarpMetatileBehavior is TRUE), and this tile's metatile satisfies none of "
        "them, so its warp_def is an inert anchor: the destination is normally reached by a "
        "scripted `warp`, which calls DoWarp without consulting a metatile behaviour",
    "dead connection":
        "no crossing tile satisfies the engine's dest = src - offset window, or a border column is "
        "entirely impassable, so the connection never fires (src/fieldmap.c)",
}


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def command_inventory(source, args):
    document = build_inventory(source)
    if args.write:
        with open(args.write, "w", encoding="utf-8") as handle:
            json.dump(document, handle, indent=2, sort_keys=False)
            handle.write("\n")
        print("wrote %s" % args.write)
        return 0

    if args.check:
        committed = load_json(args.check)
        problems = []
        if committed.get("upstream_commit") != document["upstream_commit"]:
            problems.append(
                "upstream_commit is %r, expected %r"
                % (committed.get("upstream_commit"), document["upstream_commit"])
            )
        for key in ("functional_edges", "undecided_from_map_data", "script_warp_candidates"):
            want = document[key]
            got = committed.get(key)
            if got != want:
                problems.append(
                    "%s differs: committed %s entries, regenerated %s"
                    % (key, len(got) if got is not None else "no", len(want))
                )
                for entry in want:
                    if entry not in (got or []):
                        problems.append("  missing from the committed record: %s" % json.dumps(entry))
                for entry in (got or []):
                    if entry not in want:
                        problems.append("  stale in the committed record: %s" % json.dumps(entry))

        # The manually engine-source-verified half may not contradict the tool-derived half. Each
        # class the tool could not decide must appear in the manual audit with a verdict, so the
        # committed record is a partition of the edge set rather than a table the tool cannot check.
        manual = committed.get("manual_engine_source_verified", {})
        decided = {}
        for entry in manual.get("edges", []):
            key = (entry.get("from_map"), entry.get("to_map"))
            decided[key] = entry.get("verdict")
            if entry.get("verdict") not in ("functional", "dead"):
                problems.append("manual edge %s has no verdict" % (key,))
            if not entry.get("evidence"):
                problems.append("manual edge %s cites no evidence" % (key,))

        for entry in document["undecided_from_map_data"]:
            key = (entry["from_map"], entry["to_map"])
            if key not in decided:
                problems.append(
                    "edge %s was not decidable from map data and has no manual verdict" % (key,)
                )
        for entry in document["functional_edges"]:
            key = (entry["from_map"], entry["to_map"])
            verdict = decided.get(key)
            if verdict == "dead":
                problems.append(
                    "edge %s is functional from its metatile behaviour but the manual audit calls "
                    "it dead" % (key,)
                )

        if args.check_embedded:
            record = load_json(args.check_embedded)
            embedded = (
                record.get("cross_region_transitions", {}).get("tool_derived", {})
            )
            for key in ("functional_edges", "undecided_from_map_data", "script_warp_candidates"):
                if embedded.get(key) != document[key]:
                    problems.append(
                        "the record at %s embeds a %s list that differs from the verified "
                        "inventory" % (args.check_embedded, key)
                    )

        if problems:
            print("inventory --check FAILED:")
            for problem in problems:
                print("  %s" % problem)
            return 1
        print(
            "inventory --check OK (%d functional, %d undecided-from-map-data with manual verdicts, "
            "%d script-warp candidates match the pinned source)"
            % (
                len(document["functional_edges"]),
                len(document["undecided_from_map_data"]),
                len(document["script_warp_candidates"]),
            )
        )
        return 0

    print(json.dumps(document, indent=2))
    return 0

def command_warps(source, args):
    """Cross-region `warp` script commands, with the exact file and line that contains each."""
    rows = source.script_warps()
    print("cross-region warp script commands in the pinned build: %d" % len(rows))
    print()
    print("%-58s %-6s %-12s %-30s %-7s %-7s %s" % (
        "file", "line", "command", "target map", "from", "to", "attributed to",
    ))
    for relative, number, command, target, from_region, to_region, owner in rows:
        print("%-58s %-6d %-12s %-30s %-7s %-7s %s" % (
            relative, number, command, target, from_region, to_region, owner or "(shared include)",
        ))
    return 0


def command_blockers(source, args):
    """Resolve the FLAG_/VAR_ conditions that gate each cross-region transition back to setters.

    A cross-region warp is only as reachable as the events that satisfy its gate, so this reports
    both sides -- the destination map's own gate script and the source map's gate trigger -- and
    then finds every `setflag`/`setvar` for each subject. That is what turns "there is a warp" into
    "this warp needs the whole Johto story first", which is the question a bounded runtime slice has
    to answer before it promises a save.

    `VAR_RESULT` is excluded from the setter search: it is the generic script return register, set
    in over a hundred unrelated places, and a transient engine value cannot be "earned" by
    progression. A gate that depends on it is reported as a runtime prompt, not a story gate.
    """
    import subprocess

    pairs = {}
    for name, at, target, mechanism in source.all_edges():
        from_region, to_region = source.region(name), source.region(target)
        if not from_region or not to_region or from_region == to_region:
            continue
        pairs.setdefault((from_region, to_region, name, target), None)

    search_root = os.path.join(source.root, "data")
    for (from_region, to_region, name, target) in sorted(pairs):
        print("%s -> %s : %s -> %s" % (from_region, to_region, name, target))
        for side, gates in (("destination", source.story_gates(target)),
                            ("source", source.story_gates(name))):
            if not gates:
                print("    %s gate: none declared" % side)
            for gate_kind, subject, value in sorted(set(gates)):
                print("    %s gate: %s %s %s" % (side, gate_kind, subject, value))
                if subject == "VAR_RESULT":
                    print("        set by: (generic script return register -- a runtime prompt, "
                          "not a progression flag)")
                    continue
                found = subprocess.run(
                    ["grep", "-rn", "--include=*.inc", "--include=*.json",
                     "-e", "set%s" % subject, "-e", "setvar %s," % subject, search_root],
                    capture_output=True, text=True, check=False,
                )
                setters = [line for line in found.stdout.splitlines() if line.strip()]
                if not setters:
                    print("        set by: (no setter found in data/)")
                for line in setters[:8]:
                    print("        set by: %s" % line.replace(source.root + "/", ""))
                if len(setters) > 8:
                    print("        set by: ... and %d more" % (len(setters) - 8))
        print()
    return 0


def command_route(source, args):
    if args.kind == "map":
        goal_test = lambda name, x, y: name == args.target  # noqa: E731
    else:
        goal_x, goal_y = (int(part) for part in args.target.split(","))
        goal_test = (
            lambda name, x, y: name == args.map and x == goal_x and y == goal_y  # noqa: E731
        )
    path = source.plan(args.map, (args.x, args.y), goal_test, allow_warps=not args.no_warps)
    if not path:
        print("UNREACHABLE: no walkable route from %s (%d,%d)" % (args.map, args.x, args.y))
        return 1
    for line in emit(path):
        print(line)
    return 0


def command_edges(source, args):
    """Collapse the per-tile crossings into one row per crossing map PAIR.

    A single Johto -> Kanto border can be dozens of walkable edge tiles wide; what matters for
    "is there a natural cross-region transition, and what gates it?" is the pair, its region
    change and the mechanism, not each tile.
    """
    pairs = {}
    for name, at, target, mechanism in source.all_edges():
        from_region = source.region(name)
        to_region = source.region(target)
        if not from_region or not to_region or from_region == to_region:
            continue
        kind = "connection" if mechanism.startswith("connection") else "warp"
        key = (name, target, from_region, to_region, kind)
        entry = pairs.setdefault(
            key,
            {
                "tiles": 0,
                "first": at,
                "sections": (source.section_of.get(name), source.section_of.get(target)),
                "game_version": source.meta(target).get("game_version"),
            },
        )
        entry["tiles"] += 1
        if at < entry["first"]:
            entry["first"] = at

    print("cross-region map-transition pairs in the pinned 2.0.5 build: %d" % len(pairs))
    print()
    header = "%-7s %-7s %-8s %-26s %-8s %-26s %-9s %-9s %s"
    print(
        header
        % (
            "from",
            "to",
            "kind",
            "source map",
            "g/n",
            "dest map",
            "g/n",
            "tiles",
            "sections",
        )
    )
    for (name, target, from_region, to_region, kind), entry in sorted(
        pairs.items(), key=lambda item: (item[0][2], item[0][3], item[0][0], item[0][1])
    ):
        print(
            header
            % (
                from_region,
                to_region,
                kind,
                name,
                "%s/%s" % (source.group_of.get(name), source.num_of.get(name)),
                target,
                "%s/%s (%s)" % (
                    source.group_of.get(target),
                    source.num_of.get(target),
                    entry["game_version"],
                ),
                entry["tiles"],
                "%s -> %s" % entry["sections"],
            )
        )
        if args.gates:
            gates = source.story_gates(target)
            if not gates:
                print("            gate: none declared on %s" % target)
            for gate_kind, subject, value in sorted(set(gates)):
                print("            gate: %s %s %s" % (gate_kind, subject, value))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--upstream-dir", default=None)
    sub = parser.add_subparsers(dest="command", required=True)

    route = sub.add_parser("route", help="emit a probe walk script for a source-derived route")
    route.add_argument("map")
    route.add_argument("x", type=int)
    route.add_argument("y", type=int)
    route.add_argument("kind", choices=["map", "tile"])
    route.add_argument("target")
    route.add_argument(
        "--no-warps",
        action="store_true",
        help="use walkable map connections only (no door/arrow/stair warps)",
    )
    route.set_defaults(func=command_route)

    edges = sub.add_parser("edges", help="list cross-region map-transition edges")
    edges.add_argument("--cross-only", action="store_true", default=True)
    edges.add_argument(
        "--gates",
        action="store_true",
        help="also report the FLAG_/VAR_ conditions the destination map's scripts require",
    )
    edges.set_defaults(func=command_edges)

    warps = sub.add_parser(
        "warps", help="cross-region warp script commands with their file and line"
    )
    warps.set_defaults(func=command_warps)

    inventory = sub.add_parser(
        "inventory", help="emit or verify the machine-checkable cross-region inventory"
    )
    inventory.add_argument("--write", default=None, help="write the inventory to this path")
    inventory.add_argument("--check", default=None, help="verify this committed inventory")
    inventory.add_argument(
        "--check-embedded",
        default=None,
        help=(
            "also verify the copy embedded in this runtime evidence record, so the record and the "
            "verified inventory cannot drift apart"
        ),
    )
    inventory.set_defaults(func=command_inventory)

    blockers = sub.add_parser(
        "blockers", help="resolve the FLAG_/VAR_ conditions gating each cross-region transition"
    )
    blockers.set_defaults(func=command_blockers)

    args = parser.parse_args()
    return args.func(verified_source(args.upstream_dir), args)


if __name__ == "__main__":
    sys.exit(main())
