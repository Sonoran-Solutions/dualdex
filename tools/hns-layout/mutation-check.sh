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
)
BEFORE="$(sha256sum "${MUTATED_FILES[@]}")"

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

  if ./gradlew --offline -q :app:testDebugUnitTest --tests "$FILTER" > /tmp/dualdex-mutation.log 2>&1; then
    printf '  [FAIL] %-38s suite still PASSED under the mutation\n' "$name"
    FAILURES=$((FAILURES + 1))
  else
    printf '  [PASS] %-38s suite failed as required\n' "$name"
    grep -m2 -E 'FAILED$|tests completed' /tmp/dualdex-mutation.log | sed 's/^/         /'
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

echo
if [ "$(sha256sum "${MUTATED_FILES[@]}")" != "$BEFORE" ]; then
  echo "error: a mutated file was not restored; the working tree is NOT as it was found" >&2
  exit 1
fi
echo "every mutated file was restored byte-identically"
echo "== mutation summary: $CASES mutations, $FAILURES not caught =="
[ "$FAILURES" -eq 0 ] || exit 1
echo "every mutation was caught by the suite"
