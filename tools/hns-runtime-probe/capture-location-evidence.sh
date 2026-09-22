#!/usr/bin/env bash
#
# Capture the runtime half of the issue #11 location evidence.
#
# Boots each accepted legal checkpoint from the issue #1 progression chain on the exact official
# Heart & Soul 2.0.5 ROM, reads the live location through the PRODUCTION native reader
# (`pokemon_read_player_location_gba`), asserts the raw (mapGroup, mapNum) pair the pinned 2.0.5
# table keys on, and writes one machine-readable [LOCATION] record per checkpoint.
#
# Developer tool: it is not built by `ci.sh`, needs a legally obtained ROM and an mGBA libretro
# core, and is never run by CI. It writes no in-game save: the battery image is copied to a
# temporary directory first and the copy is discarded. No ROM, save file or save state is committed.
#
# Usage:
#   ./capture-location-evidence.sh <mgba_libretro.so> <hns-2.0.5.gba> <checkpoint-dir> <out-file>
#
# <checkpoint-dir> holds the `.sav` files produced by `progression-issue1/` (and the starter save
# from `make-save.sh`). A checkpoint that fails to boot, fails an assertion or reports a ROM
# mismatch aborts the whole capture, so a partial run never looks like a complete one.

set -uo pipefail
cd "$(dirname "$0")"

if [ "$#" -ne 4 ]; then
  echo "usage: $0 <mgba_libretro.so> <hns-2.0.5.gba> <checkpoint-dir> <out-file>" >&2
  exit 2
fi

CORE="$(realpath "$1")"
ROM="$(realpath "$2")"
CHECKPOINTS="$(realpath "$3")"
OUT="$4"
BIN="./runtime_battle_probe"

# The exact supported release. Any other build aborts the capture rather than producing evidence
# that claims to describe it.
ROM_SHA="edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b"

[ -x "$BIN" ] || ./build.sh
[ -f "$CORE" ] || { echo "error: no core at $CORE" >&2; exit 1; }
[ -f "$ROM" ] || { echo "error: no ROM at $ROM" >&2; exit 1; }

ACTUAL_SHA="$(sha256sum "$ROM" | cut -d' ' -f1)"
if [ "$ACTUAL_SHA" != "$ROM_SHA" ]; then
  echo "error: ROM SHA-256 is $ACTUAL_SHA, expected the exact supported release $ROM_SHA" >&2
  exit 1
fi

# checkpoint-stem|checkpoint-label|group|num|x|y|section
CHECKPOINTS_LIST=(
  "starter|cp1-starter-newbarktown|0|0|10|10|MAPSEC_NEW_BARK_TOWN"
  "stage34_don_ready|cp2-route30|0|12|19|9|MAPSEC_ROUTE_30"
  "stage44a_violet_city|cp3-violetcity|0|2|39|46|MAPSEC_VIOLET_CITY"
  "stage66_azalea_gym|cp4-azalea-town-interior|4|4|11|44|MAPSEC_AZALEA_TOWN"
)

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

: > "$OUT"
{
  echo "# Issue #11 runtime location captures, official Heart & Soul 2.0.5"
  echo "# rom_sha256=$ROM_SHA"
  echo "# reader=pokemon_read_player_location_gba (production native reader)"
  echo "# boot=bootstrap 16 START/A (fatal unless the location reader reports a loaded save)"
  echo "# A [LOCATION] line is emitted only for a checkpoint whose assertions all held."
} >> "$OUT"

FAILED=0
for ENTRY in "${CHECKPOINTS_LIST[@]}"; do
  IFS='|' read -r STEM LABEL GROUP NUM X Y SECTION <<< "$ENTRY"
  SAV="$CHECKPOINTS/$STEM.sav"
  if [ ! -f "$SAV" ]; then
    echo "error: missing checkpoint $SAV" >&2
    FAILED=1
    continue
  fi

  # Work on a copy so the original legal checkpoint is never written to.
  cp -- "$SAV" "$WORK/$STEM.sav"

  cat > "$WORK/$STEM.txt" <<EOF
assert-rom-sha256 $ROM_SHA
bootstrap 16 START/A
wait 120
assert-battle inactive
assert-location $GROUP $NUM within 120 at local $X $Y
assert-location-region-section $GROUP $NUM $SECTION
location $LABEL
EOF

  LOG="$WORK/$STEM.log"
  if "$BIN" "$CORE" "$ROM" --sav "$WORK/$STEM.sav" --script "$WORK/$STEM.txt" --quiet \
      > "$LOG" 2>&1; then
    grep '^\[LOCATION\]' "$LOG" >> "$OUT"
    printf '  [ok]   %-28s %s\n' "$STEM" "$(grep -c '^\[LOCATION\]' "$LOG") record(s)"
  else
    printf '  [FAIL] %-28s assertions did not hold; capture aborted\n' "$STEM" >&2
    grep -E 'script error|INVARIANT VIOLATION' "$LOG" | sed 's/^/         /' >&2
    FAILED=1
  fi
done

if [ "$FAILED" -ne 0 ]; then
  echo "error: at least one checkpoint failed; the capture is not accepted" >&2
  exit 1
fi

echo "wrote $OUT"
