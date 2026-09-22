#!/usr/bin/env bash
#
# Mutation controls for the issue #11 map/location regression suite.
#
# Each mutation applies a small, deliberate defect to PRODUCTION code, runs the canonical Kotlin
# suite, and requires it to FAIL. A mutation that passes means the test that is supposed to catch it
# is not actually asserting anything. Every mutated file is backed up before the edit and restored
# afterwards, and the script verifies at the end that the working tree is byte-identical to how it
# found it, so a failed restore cannot be mistaken for a clean run.
#
# Developer tool. It shells out to Gradle and is NOT run by `ci.sh` (it mutates working-tree files);
# run it before recommending an issue closure, not on every build.
#
# Usage:
#   tools/hns-layout/mutation-check.sh [gradle-test-filter]
#
# Requires JAVA_HOME to point at a JDK 17.

set -uo pipefail
cd "$(dirname "$0")/../.."

FILTER="${1:-com.dualdex.pokemon.hns.*,com.dualdex.companion.ui.*}"

if [ -z "${JAVA_HOME:-}" ]; then
  echo "error: JAVA_HOME must point at a JDK 17" >&2
  exit 2
fi

FAILURES=0
CASES=0
BACKUP_DIR="$(mktemp -d)"
trap 'rm -rf "$BACKUP_DIR"' EXIT

# A hash of every file any mutation touches, taken before the run, so the end state can be proven
# identical instead of assumed.
MUTATED_FILES=(
  "app/src/main/java/com/dualdex/pokemon/LocationResolver.kt"
  "app/src/main/java/com/dualdex/pokemon/hns/Hns205MapData.kt"
  "app/src/main/java/com/dualdex/companion/ui/MapScreenPresenter.kt"
  "tools/hns-runtime-probe/evidence/hns-cross-region-inventory.json"
  "tools/hns-map-data/hns_route.py"
  "tools/hns-map-data/test_hns_route.py"
  "tools/hns-runtime-probe/evidence/location-runtime-evidence.json"
)
for file in "${MUTATED_FILES[@]}"; do
  [ -f "$file" ] || { echo "error: mutated file $file does not exist" >&2; exit 2; }
done
BEFORE="$(sha256sum "${MUTATED_FILES[@]}")"
# A per-file copy of the exact pre-run content, so restoration can be proven against what this run
# actually started from rather than against a hash that a previous failed run could have poisoned.
for file in "${MUTATED_FILES[@]}"; do
  cp -- "$file" "$BACKUP_DIR/pristine-$(echo "$file" | tr '/' '_')"
done

# mutate <name> <file> <python-replacement-script>
mutate() {
  local name="$1" file="$2" script="$3"
  CASES=$((CASES + 1))
  local backup="$BACKUP_DIR/$(basename "$file")"

  cp -- "$file" "$backup" || { echo "cannot back up $file" >&2; exit 2; }
  if ! python3 -c "$script" "$file"; then
    echo "  [FAIL] $name: the mutation did not apply (production code changed?)" >&2
    cp -- "$backup" "$file"
    FAILURES=$((FAILURES + 1))
    return
  fi

  # Two independent gates, because they catch different halves of the contract:
  #   * the Kotlin suite owns the production map/location behaviour and the embedded evidence table;
  #   * the analyzer owns its own provenance boundary and its agreement with the pinned source,
  #     which a JVM test cannot express.
  local kotlin_rc=0 tool_rc=0
  ./gradlew --offline -q :app:testDebugUnitTest --tests "$FILTER" > /tmp/dualdex-mutation.log 2>&1 || kotlin_rc=1
  python3 tools/hns-map-data/hns_route.py inventory \
    --check tools/hns-runtime-probe/evidence/hns-cross-region-inventory.json \
    --check-embedded tools/hns-runtime-probe/evidence/location-runtime-evidence.json \
    > /tmp/dualdex-mutation-tool.log 2>&1 || tool_rc=1
  python3 -m unittest discover -s tools/hns-map-data -p 'test_hns_route.py' -t tools/hns-map-data \
    >> /tmp/dualdex-mutation-tool.log 2>&1 || tool_rc=1

  if [ "$kotlin_rc" -eq 0 ] && [ "$tool_rc" -eq 0 ]; then
    printf '  [FAIL] %-38s every gate still PASSED under the mutation\n' "$name"
    FAILURES=$((FAILURES + 1))
  else
    printf '  [PASS] %-38s caught (kotlin=%s tool=%s)\n' \
      "$name" "$([ "$kotlin_rc" -ne 0 ] && echo fail || echo pass)" \
      "$([ "$tool_rc" -ne 0 ] && echo fail || echo pass)"
    grep -m2 -E 'FAILED$|tests completed' /tmp/dualdex-mutation.log | sed 's/^/         /' || true
    grep -m2 -E 'FAILED|failed|Refusing|refusing|error:' /tmp/dualdex-mutation-tool.log \
      | sed 's/^/         /' || true
  fi

  cp -- "$backup" "$file"
}

echo "== mutation controls: every mutation must break the suite =="
echo

# 1. Restore the legacy fabricated default: an unknown H&S pair becomes New Bark Town again.
mutate "restore-else-johto-default" \
  "app/src/main/java/com/dualdex/pokemon/LocationResolver.kt" '
import sys
p = sys.argv[1]
s = open(p).read()
old = """        val generated = Hns205MapData.findSection(map.sectionId)
            ?: return LocationResolution.unavailable(LocationUnavailableReason.UNKNOWN_MAP_ID)"""
new = """        val generated = Hns205MapData.findSection(map.sectionId)
            ?: return LocationResolution.resolved(
                RegionMapDatabase.curatedSection("MAPSEC_NEW_BARK_TOWN")!!
            )"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 2. Map one Alola group to Johto.
mutate "alola-group-mapped-to-johto" \
  "app/src/main/java/com/dualdex/pokemon/LocationResolver.kt" '
import sys
p = sys.argv[1]
s = open(p).read()
old = "                region = generated.region,"
new = """                region = if (location.mapGroup == 25) RegionId.JOHTO else generated.region,"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 3. Let a browsed region become the native strategy: browsing Kanto selects FIRERED.
mutate "browsing-changes-location-strategy" \
  "app/src/main/java/com/dualdex/companion/ui/MapScreenPresenter.kt" '
import sys
p = sys.argv[1]
s = open(p).read()
old = """    fun onRegionSelected(region: RegionId, liveSection: RegionMapSection?, hasLiveLocation: Boolean) {
        val drawable = MapScreenPresenter.canvasRegions(strategy).contains(region)"""
new = """    fun onRegionSelected(region: RegionId, liveSection: RegionMapSection?, hasLiveLocation: Boolean) {
        strategy = if (region == RegionId.KANTO) LocationStrategy.FIRERED else strategy
        val drawable = MapScreenPresenter.canvasRegions(strategy).contains(region)"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 4. Give Sinjoh and Alola invented coordinates so a marker can be placed for them.
mutate "sinjoh-alola-fabricated-marker" \
  "app/src/main/java/com/dualdex/pokemon/LocationResolver.kt" '
import sys
p = sys.argv[1]
s = open(p).read()
old = "                presentable = generated.presentable && generated.region?.hasCanvas == true"
new = "                presentable = true"
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 5. Let an unknown map id inherit a valid indoor group parent town.
mutate "unknown-pair-inherits-group-town" \
  "app/src/main/java/com/dualdex/pokemon/hns/Hns205MapData.kt" '
import sys
p = sys.argv[1]
s = open(p).read()
old = "        return locationGroups[mapGroup]?.getOrNull(mapNum)"
new = """        val group = locationGroups[mapGroup] ?: return null
        return group.getOrNull(mapNum) ?: group.firstOrNull { it != null }"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 6. Expose an authoritative live location for an unverified ROM.
mutate "unverified-rom-publishes-location" \
  "app/src/main/java/com/dualdex/pokemon/LocationResolver.kt" '
import sys
p = sys.argv[1]
s = open(p).read()
old = """            LocationStrategy.UNVERIFIED ->
                LocationResolution.unavailable(LocationUnavailableReason.NO_STRATEGY)"""
new = """            LocationStrategy.UNVERIFIED ->
                resolveHeartAndSoul205(location)"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 7. Weaken the analyzer's provenance boundary so it would accept an unpinned or modified checkout.
#    Only the analyzer's own gate can catch this: a JVM test cannot express a git-revision check.
mutate "provenance-boundary-weakened" \
  "tools/hns-map-data/hns_route.py" '
import sys
p = sys.argv[1]
s = open(p).read()
old = "    if head != PINNED_COMMIT_SHA:"
new = "    if False:"
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 8. Hand-edit the committed cross-region inventory so a region claim disagrees with the pinned
#    source. Both gates catch it: the analyzer re-derives the file, and the Kotlin suite embeds the
#    same table, so the "no bounded legal route reaches Kanto" claim cannot rest on a table nobody
#    re-derives.
mutate "committed-inventory-region-edited" \
  "tools/hns-runtime-probe/evidence/hns-cross-region-inventory.json" '
import json, sys
p = sys.argv[1]
d = json.load(open(p))
assert d["functional_edges"], "no functional edges to edit"
edge = d["functional_edges"][0]
edge["to_region"] = "KANTO" if edge["to_region"] != "KANTO" else "JOHTO"
json.dump(d, open(p, "w"), indent=2)
'

# 9. Drop an undecided edge from the record so an edge with no manual verdict would go unnoticed --
#    exactly the "hand-edited table" failure the tool/manual split exists to prevent.
mutate "undecided-edge-dropped" \
  "tools/hns-runtime-probe/evidence/hns-cross-region-inventory.json" '
import json, sys
p = sys.argv[1]
d = json.load(open(p))
assert d["undecided_from_map_data"], "no undecided edges to drop"
d["undecided_from_map_data"].pop()
json.dump(d, open(p, "w"), indent=2)
'

# 10. Weaken the provenance regression itself so it stops asserting anything, which would let the
#     boundary rot while the gate stayed green.
mutate "provenance-test-neutered" \
  "tools/hns-map-data/test_hns_route.py" '
import sys
p = sys.argv[1]
s = open(p).read()
old = "        with self.assertRaises(SystemExit) as raised:\n            hns_route.verify_provenance(self.repo)\n        message = str(raised.exception)"
new = "        hns_route.verify_provenance(self.repo)\n        message = \"\""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 11. Edit the copy embedded in the runtime evidence record so the record and the verified inventory
#     disagree. The Kotlin suite reads the inventory file directly, so this is the tool's invariant.
mutate "evidence-record-embedded-edit" \
  "tools/hns-runtime-probe/evidence/location-runtime-evidence.json" '
import json, sys
p = sys.argv[1]
d = json.load(open(p))
edges = d["cross_region_transitions"]["tool_derived"]["functional_edges"]
assert edges, "no embedded functional edges"
edge = edges[0]
edge["to_region"] = "KANTO" if edge["to_region"] != "KANTO" else "JOHTO"
json.dump(d, open(p, "w"), indent=2)
'

# 12. Restore the wrong primary/secondary metatile boundary (512 instead of the 640 H&S layouts use),
#     which mis-reads every tile in the 512..639 range and turned a real arrow warp into ordinary
#     floor in an earlier revision of this work.
mutate "wrong-metatile-boundary-512" \
  "tools/hns-map-data/hns_route.py" '
import sys
p = sys.argv[1]
s = open(p).read()
old = "NUM_METATILES_IN_PRIMARY = 640"
new = "NUM_METATILES_IN_PRIMARY = 512"
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 13. Re-introduce a transcribed behaviour list: accept MB_NORMAL as a step-on warp behaviour, which
#     is exactly the shape of the original name-based transcription error.
mutate "normal-floor-accepted-as-warp" \
  "tools/hns-map-data/hns_route.py" '
import sys
p = sys.argv[1]
s = open(p).read()
old = "        return self.name_of(behaviour) in self.paths[\"step\"]"
new = "        return self.name_of(behaviour) in self.paths[\"step\"] | {\"MB_NORMAL\"}"
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 14. Let a warp be classified without checking the predicate at all, i.e. treat a warp_def as an
#     executable transition on its own.
mutate "warp-def-alone-is-functional" \
  "tools/hns-map-data/hns_route.py" '
import sys
p = sys.argv[1]
s = open(p).read()
old = """        behaviour = self.behaviour_at(name, x, y)
        if behaviour is None:
            return None
        name_of = self.behaviours.name_of(behaviour)
        hit = self.warp_at(name, x, y)"""
new = """        behaviour = self.behaviour_at(name, x, y)
        if behaviour is None:
            return None
        name_of = self.behaviours.name_of(behaviour)
        hit = self.warp_at(name, x, y)
        if hit is not None:
            return {"behaviour": name_of, "warp_event_present": True,
                    "tile_walkable": self.walkable(name, x, y), "reachable": True,
                    "path": "step", "reason": None}"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 15. Let the step-on path apply to an impassable tile, which is the impossible label the second
#     senior review found: an animated door is impassable and can only be entered through
#     TryDoorWarp from the tile south of it.
mutate "step-path-allows-impassable-tile" \
  "tools/hns-map-data/hns_route.py" '
import sys
p = sys.argv[1]
s = open(p).read()
old = """        if self.behaviours.is_door_behaviour(behaviour) and not self.walkable(name, x, y):"""
new = """        if False:"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 16. Offer every direction for an arrow warp, which is how `route` would emit a controller step
#     that can never fire.
mutate "arrow-warp-offers-all-directions" \
  "tools/hns-map-data/hns_route.py" '
import sys
p = sys.argv[1]
s = open(p).read()
old = """            return dict(evidence, reachable=True, path="arrow",
                        directions=sorted(arrow_directions), reason=None)"""
new = """            return dict(evidence, reachable=True, path="arrow",
                        directions=["UP", "DOWN", "LEFT", "RIGHT"], reason=None)"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

# 17. Drop the engine sources from the clean boundary, re-opening the hole where a tampered
#     predicate source could change the classification while provenance still accepted the tree.
mutate "engine-sources-unprotected" \
  "tools/hns-map-data/hns_route.py" '
import sys
p = sys.argv[1]
s = open(p).read()
old = """    "include/",
    "src/",
)"""
new = """)"""
assert old in s, "anchor not found"
open(p, "w").write(s.replace(old, new))
'

echo
restore_failed=0
if [ "$(sha256sum "${MUTATED_FILES[@]}")" != "$BEFORE" ]; then
  restore_failed=1
fi
for file in "${MUTATED_FILES[@]}"; do
  pristine="$BACKUP_DIR/pristine-$(echo "$file" | tr '/' '_')"
  if ! cmp -s -- "$pristine" "$file"; then
    echo "error: $file does not match the content this run started from" >&2
    diff -u -- "$pristine" "$file" | head -20 >&2 || true
    restore_failed=1
  fi
done
if [ "$restore_failed" -ne 0 ]; then
  echo "error: a mutated file was not restored; the working tree is NOT as it was found" >&2
  exit 1
fi
echo "every mutated file was restored byte-identically to the content this run started from"
# `cp` moves the mtime forward, so git's cached stat info would report the restored files as
# modified even though their content matches. Refresh it, or a clean restore would look dirty.
git update-index --refresh > /dev/null 2>&1 || true
echo "== mutation summary: $CASES mutations, $FAILURES not caught =="
[ "$FAILURES" -eq 0 ] || exit 1
echo "every mutation was caught by the suite"
