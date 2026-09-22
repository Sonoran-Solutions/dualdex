#!/usr/bin/env bash
# Replay controller-only checkpoints; publish a battery output only after an asserted PASS.
set -euo pipefail
HERE="$(cd -- "$(dirname -- "$0")" && pwd)"
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <mgba_libretro.so> <hns-2.0.5.gba> [first-stage: 1..20]" >&2
  exit 2
fi
CORE="$(realpath "$1")"
ROM="$(realpath "$2")"
FIRST="${3:-1}"
[[ "$FIRST" =~ ^([1-9]|1[0-9]|20)$ ]] || { echo 'invalid first stage' >&2; exit 2; }
mkdir -p /tmp/hns_baseline
WORK="$(mktemp -d /tmp/hns-issue1-replay.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
for SCRIPT in "$HERE"/[0-9][0-9]-*.txt; do
  NAME="$(basename "$SCRIPT" .txt)"
  STAGE="${NAME%%-*}"
  (( 10#$STAGE >= FIRST )) || continue
  INPUT="$(sed -n 's/^# Issue #1 legal controller progression. Input: //p' "$SCRIPT")"
  OUTPUT="$(sed -n 's/^savsave //p' "$SCRIPT")"
  [[ -f "$INPUT" && -n "$OUTPUT" ]] || { echo "Missing input for $NAME: $INPUT" >&2; exit 1; }
  sed "s|^savsave .*|savsave $WORK/candidate.sav|" "$SCRIPT" > "$WORK/scenario.txt"
  LOG="/tmp/hns_baseline/issue1-$NAME.log"
  echo "Running $NAME (log: $LOG)"
  if "$HERE/../runtime_battle_probe" "$CORE" "$ROM" --sav "$INPUT" --script "$WORK/scenario.txt" --quiet > "$LOG" 2>&1; then
    cp -- "$WORK/candidate.sav" "$OUTPUT"
  else
    echo "$NAME failed; output was not accepted. Inspect $LOG before retrying stage $((10#$STAGE))." >&2
    exit 1
  fi
done
