#!/usr/bin/env bash
#
# Produce a legal Heart & Soul 2.0.5 battery save from a fresh ROM by normal game progression.
#
# Developer tool only. This does NOT ship, does NOT patch the ROM, and does NOT write to emulated
# memory: it presses buttons through the bundled mGBA libretro core exactly as a player would, then
# saves the game from the in-game menu and flushes the cartridge battery RAM to a .sav file.
#
# The resulting .sav is a normal save file. It is NOT committed to this repository (see .gitignore
# guidance in tools/hns-runtime-probe/README.md) and it contains no ROM bytes.
#
# Usage:
#   ./make-save.sh <mgba_libretro.so> <hns_2.0.5.gba> <out.sav> [more-scenarios...]
#
# Example:
#   ./make-save.sh ~/mgba_libretro.so ~/hns-2.0.5.gba /tmp/hns205.sav
#
set -euo pipefail
cd "$(dirname "$0")"

if [ "$#" -lt 3 ]; then
  echo "usage: $0 <mgba_libretro.so> <hns_2.0.5.gba> <out.sav>" >&2
  exit 2
fi

CORE="$1"
ROM="$2"
OUT="$3"
shift 3

if [ ! -f "$CORE" ]; then echo "error: core not found: $CORE" >&2; exit 1; fi
if [ ! -f "$ROM" ]; then echo "error: ROM not found: $ROM" >&2; exit 1; fi

BIN="./runtime_battle_probe"
if [ ! -x "$BIN" ]; then
  ./build.sh
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Append the flush so the in-game save lands on disk as a .sav.
#
# The probe is strict: it exits non-zero if any scripted step times out, an assertion fails, a save
# operation fails, or a runtime invariant is violated. `set -e` therefore aborts this script as soon
# as the progression stops matching the scenario, instead of falling through to a "successful" run
# that never reached the save.
SCRIPT="$WORK/fresh-rom.txt"
cat scenarios/00-fresh-rom-to-starter-save.txt > "$SCRIPT"
echo "savsave $OUT" >> "$SCRIPT"

rm -f "$OUT"
"$BIN" "$CORE" "$ROM" --script "$SCRIPT" --quiet

if [ ! -f "$OUT" ]; then
  echo "error: the run finished without writing $OUT" >&2
  exit 1
fi

# A GBA battery save for this ROM is exactly 128 KiB (0x20000). Anything else means the core
# flushed a different memory region or a truncated image, which must not be handed on as evidence.
SIZE="$(wc -c < "$OUT" | tr -d '[:space:]')"
if [ "$SIZE" -ne 131072 ]; then
  echo "error: expected 131072-byte GBA battery save, got $SIZE" >&2
  exit 1
fi

# A blank battery image is all-0xFF; that is what an unwritten save looks like, so reject it rather
# than shipping a technically-131072-byte file that contains no game state.
if [ "$(tr -d '\377' < "$OUT" | wc -c | tr -d '[:space:]')" -eq 0 ]; then
  echo "error: $OUT is a blank all-0xFF battery image; the in-game save did not happen" >&2
  exit 1
fi

echo "wrote $OUT ($SIZE bytes, sha256 $(sha256sum "$OUT" | cut -d' ' -f1))"
