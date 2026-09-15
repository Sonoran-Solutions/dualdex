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
SCRIPT="$WORK/fresh-rom.txt"
cat scenarios/00-fresh-rom-to-starter-save.txt > "$SCRIPT"
echo "savsave $OUT" >> "$SCRIPT"

"$BIN" "$CORE" "$ROM" --script "$SCRIPT" --quiet

if [ ! -s "$OUT" ]; then
  echo "error: the run finished without writing $OUT" >&2
  exit 1
fi

echo "wrote $OUT ($(wc -c < "$OUT") bytes, sha256 $(sha256sum "$OUT" | cut -d' ' -f1))"
