#!/usr/bin/env python3
"""
tools/hns-map-data/hns_route.py

Offline overworld route planning and cross-region edge analysis for the pinned
Heart & Soul 2.0.5 source checkout (issue #11).

It exists because the runtime half of #11 needs a *natural* map transition whose
exact destination tile is known before a controller script is written, and because
"safely reachable?" has to be answered from the pinned build rather than from
memory of the retail games.

What it reads (tracked files only, from the pinned checkout):

  data/maps/map_groups.json          group_order (mapGroup) and member order (mapNum)
  data/maps/<map>/map.json           connections, warp_events, object_events,
                                     coord_events, region_map_section, game_version
  data/layouts/layouts.json          layout dimensions and blockdata path
  data/layouts/<dir>/map.bin         GBA block data; collision is bits 10-11

What it does:

  route    Dijkstra over (map, x, y) tiles using collision 0 as walkable, the
           `connections` table for map crossings (edge-triggered: a connection fires
           while the player stands on the source map's edge tile) and area objects as
           obstacles. Emits probe `walk`/`assert-pos` lines.
  edges    Every DECLARED map-transition edge whose two sides sit in different regions, with
           the story gate its scripts declare.

           Scope limit, stated because it matters for a cross-region audit: this tool reads tile
           geometry and script conditionals only. It does not decode metatile BEHAVIOURS, so it
           cannot decide whether a declared warp's metatile is one the engine's warp predicates
           accept, and it cannot see an NPC-driven `warp` script command. Two consequences:

             * a warp whose tile is `MB_OCEAN_WATER` or `MB_NORMAL` on non-zero collision is
               reported as a `dead warp`, but a warp on a walkable tile with a non-warp behaviour is
               reported as live when the engine would never fire it;
             * the authoritative classification for the pinned build lives in the issue #11 evidence
               record (`tools/hns-runtime-probe/evidence/location-runtime-evidence.json`), which
               names each edge functional or dead from the engine's own predicates.

No ROM, save file, save state or emulator is involved, and nothing is downloaded.
The mode of a section's region follows DualDex: REGION_JOHTO -> Johto,
REGION_KANTO -> Kanto, REGION_HISUI -> Sinjoh, REGION_ALOLA -> Alola.

Usage:

  python3 tools/hns-map-data/hns_route.py route <map> <x> <y> map <dest-map>
  python3 tools/hns-map-data/hns_route.py route <map> <x> <y> tile <x,y>
  python3 tools/hns-map-data/hns_route.py edges [--cross-only]

The upstream checkout is located through HNS_UPSTREAM_DIR, then the repo-relative
`upstream-hns/pokehns-expansion`, then the conventional sibling layout.
"""

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
        "1f42b74dff0e9fe942419845d040663dd829a973 (Release-v2.0.5)."
    )


class Source:
    """Lazily parsed view of the pinned checkout's map data."""

    def __init__(self, root):
        self.root = root
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
            target_warp = (self.meta(destination).get("warp_events") or [])[index]
            arrival = (destination, target_warp["x"], target_warp["y"])
            if not self.walkable(*arrival):
                continue
            for direction, (dx, dy) in DIRECTIONS_TO_DELTA.items():
                approach = (x + dx, y + dy)
                if approach == (door["x"], door["y"]) and self.walkable(name, x, y):
                    yield ("warp", direction, arrival[0], arrival[1], arrival[2])

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
                # A door/arrow warp needs a walkable approach tile or a walkable warp tile, and the
                # metatile behaviour must be one the engine's warp predicates accept. Without the
                # behaviours table this tool can only check the tile geometry, so a warp whose tile
                # and approach are both impassable is reported dead and anything else as a warp.
                approachable = any(
                    self.walkable(name, x + dx, y + dy)
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                )
                if grid[y][x] != 0 and not approachable:
                    yield name, (x, y), target, "dead warp (no walkable approach)"
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

    blockers = sub.add_parser(
        "blockers", help="resolve the FLAG_/VAR_ conditions gating each cross-region transition"
    )
    blockers.set_defaults(func=command_blockers)

    args = parser.parse_args()
    return args.func(Source(find_upstream(args.upstream_dir)), args)


if __name__ == "__main__":
    sys.exit(main())
