#!/usr/bin/env bash
#
# Negative self-tests for the strict H&S runtime scenario harness.
#
# The harness must never exit 0 when the scenario it was asked to reproduce did not actually happen.
# This script proves that by running deliberately-failing scenarios and asserting a NON-ZERO exit,
# plus one control that must still exit 0. It is a developer tool: it is not built by `ci.sh` and it
# needs a legally obtained ROM and a locally built mGBA libretro core.
#
# Usage:
#   ./selftest.sh <mgba_libretro.so> <hns_2.0.5.gba> [--sav <path.sav>]
#
# Scenarios that navigate the world (hunt, walk, escape) need --sav pointing at a save produced by
# ./make-save.sh; without it they are skipped. The pure-parser cases (unknown command, missing save)
# run either way.
#
set -uo pipefail
cd "$(dirname "$0")"

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <mgba_libretro.so> <hns_2.0.5.gba> [--sav <path.sav>]" >&2
  exit 2
fi

CORE="$1"; ROM="$2"; shift 2
SAV=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --sav) SAV="${2:-}"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

BIN="./runtime_battle_probe"
[ -x "$BIN" ] || ./build.sh

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

FAILURES=0
CASES=0

# expect_nonzero <name> <extra probe args...>
expect_nonzero() {
  local name="$1"; shift
  CASES=$((CASES + 1))
  "$BIN" "$CORE" "$ROM" "$@" --quiet > "$WORK/$name.log" 2>&1
  local rc=$?
  if [ "$rc" -ne 0 ]; then
    printf '  [PASS] %-24s exited %s\n' "$name" "$rc"
    grep -m1 'script error\|error:' "$WORK/$name.log" | sed 's/^/         /' || true
  else
    printf '  [FAIL] %-24s exited 0 but must fail\n' "$name"
    FAILURES=$((FAILURES + 1))
  fi
}

# expect_zero <name> <extra probe args...>
expect_zero() {
  local name="$1"; shift
  CASES=$((CASES + 1))
  "$BIN" "$CORE" "$ROM" "$@" --quiet > "$WORK/$name.log" 2>&1
  local rc=$?
  if [ "$rc" -eq 0 ]; then
    printf '  [PASS] %-24s exited 0\n' "$name"
  else
    printf '  [FAIL] %-24s exited %s but must succeed\n' "$name" "$rc"
    FAILURES=$((FAILURES + 1))
  fi
}

# NOTE ON THE BASELINE SAVE.
#
# Four cases below are POSITION-DEPENDENT and are written against a fresh starter save standing in
# New Bark Town at (10, 10) on map 0/0 -- which is exactly what make-save.sh produces:
#
#   walk-blocked-optional   walks INTO Elm's lab and must succeed
#   untilout-timeout        needs New Bark Town's callback2 to be the non-clock one
#   walk-blocked            must hit the lab's interior wall
#   escape-timeout          must exhaust a script lock at the lab's (1,9) corner
#
# Running this script against a mid-progression save (e.g. stage33) makes those four fail for the
# wrong reason: the player is somewhere else entirely, so the walls and callbacks under test are not
# the ones being exercised. Produce the correct baseline with:
#
#   ./make-save.sh <core> <rom> /tmp/hns_baseline/hns205.sav
#
# and pass that file to --sav. The failures are then meaningful, not environmental.

echo "== parser / save handling =="

printf 'wait 30\nthis-command-does-not-exist 1 2\n' > "$WORK/unknown_command.txt"
expect_nonzero unknown-command --script "$WORK/unknown_command.txt"

printf 'savload %s/definitely-missing.sav\n' "$WORK" > "$WORK/savload_failure.txt"
expect_nonzero savload-missing --script "$WORK/savload_failure.txt"

printf 'savsave %s/no-such-directory/out.sav\n' "$WORK" > "$WORK/savsave_failure.txt"
expect_nonzero savsave-failure --script "$WORK/savsave_failure.txt"

# `shot` must actually produce its output file. The command dispatcher collapsed a duplicated
# branch once (the first identical strcmp condition won with an empty body), leaving the
# write unreachable, so assert on the artifact itself, not just the exit status.
printf 'wait 30\nshot %s/shot-out.ppm\n' "$WORK" > "$WORK/shot_ok.txt"
CASES=$((CASES + 1))
"$BIN" "$CORE" "$ROM" --script "$WORK/shot_ok.txt" --quiet > "$WORK/shot-ok.log" 2>&1
rc=$?
if [ "$rc" -eq 0 ] && [ -s "$WORK/shot-out.ppm" ] && head -c 2 "$WORK/shot-out.ppm" | grep -q 'P6'; then
  printf '  [PASS] %-24s wrote %s\n' "shot-writes-file" "$WORK/shot-out.ppm"
else
  printf '  [FAIL] %-24s exited %s (shot artifact missing or not a PPM)\n' "shot-writes-file" "$rc"
  FAILURES=$((FAILURES + 1))
fi

# A --script that cannot be opened means the scenario never ran at all. The runner reports this as
# a script error and its return value is folded into the exit status, so the run cannot pass with
# zero frames executed and zero in-script errors.
expect_nonzero missing-script --script "$WORK/definitely-missing-script.txt"

# The scenario parser gained a fourth argument slot. Three invariants matter:
#   1. an existing three-argument command parses EXACTLY as before, even with trailing garbage;
#   2. a four-argument command that is missing its fourth argument must FAIL, not silently use a
#      default (otherwise "no budget" and "the event never happened" would look identical);
#   3. an unknown command still fails.
# These are position-independent: (1) uses assert-map against the save's real map is not possible
# without pinning the save, so it asserts on a command whose success does not depend on where the
# player is standing.
if [ -n "$SAV" ]; then
  printf 'wait 30\nassert-battle inactive x y z\n' > "$WORK/parser_extra_args.txt"
  expect_zero parser-trailing-args-ignored --sav "$SAV" --script "$WORK/parser_extra_args.txt"

  printf 'wait 30\nawait-enemy-voluntary-switch 0 1\n' > "$WORK/parser_missing_arg.txt"
  expect_nonzero parser-missing-fourth-arg --sav "$SAV" --script "$WORK/parser_missing_arg.txt"

  printf 'wait 30\nawait-enemy-voluntary-switch 0 0 165 10\n' > "$WORK/parser_same_slot.txt"
  expect_nonzero parser-same-slot-rejected --sav "$SAV" --script "$WORK/parser_same_slot.txt"

  printf 'wait 30\nawait-enemy-voluntary-switch 0 1 165 0\n' > "$WORK/parser_zero_budget.txt"
  expect_nonzero parser-zero-budget-rejected --sav "$SAV" --script "$WORK/parser_zero_budget.txt"
else
  echo "  [skip] parser argument cases need --sav"
fi

# A --sav that cannot be loaded must abort before the scenario runs.
if [ -n "$SAV" ]; then
  expect_nonzero cli-sav-missing --sav "$WORK/definitely-missing.sav"
else
  CASES=$((CASES + 1))
  "$BIN" "$CORE" "$ROM" --sav "$WORK/definitely-missing.sav" --quiet > /dev/null 2>&1
  if [ "$?" -ne 0 ]; then
    printf '  [PASS] %-24s exited non-zero\n' "cli-sav-missing"
  else
    printf '  [FAIL] %-24s exited 0 but must fail\n' "cli-sav-missing"
    FAILURES=$((FAILURES + 1))
  fi
fi

echo "== harness pure state-machine unit tests =="

CASES=$((CASES + 1))
"$BIN" --selftest > "$WORK/pure_tracker_selftest.log" 2>&1
rc=$?
if [ "$rc" -eq 0 ]; then
  printf '  [PASS] %-24s exited 0\n' "pure-tracker-selftest"
else
  printf '  [FAIL] %-24s exited %s but must succeed\n' "pure-tracker-selftest" "$rc"
  cat "$WORK/pure_tracker_selftest.log"
  FAILURES=$((FAILURES + 1))
fi

echo "== assertion handling =="

printf 'wait 120\nmash 800\nwait 200\nassert-battle active\n' > "$WORK/assert_failure.txt"
printf 'wait 120\nmash 800\nwait 200\nawait 0xDEADBEEF 3\n' > "$WORK/await_timeout.txt"
printf 'wait 120\nmash 800\nwait 200\nuntilout 0x0819A01D 3\n' > "$WORK/untilout_timeout.txt"
printf 'wait 120\nmash 800\nwait 200\nautobattle 100\n' > "$WORK/autobattle_never_enters.txt"
printf 'wait 120\nmash 800\nwait 200\nawait-enemy-replacement 0 1 100\n' > "$WORK/replacement_timeout.txt"
# The strict player faint -> forced replacement command must not pass on a budget that ends before
# the transition completes, even though the trainer battle it starts in is perfectly healthy.
printf 'wait 120\nmash 800\nwait 200\nawait-player-forced-replacement 0 1 100\n' > "$WORK/player_replacement_timeout.txt"

if [ -n "$SAV" ]; then
  expect_nonzero assert-false --sav "$SAV" --script "$WORK/assert_failure.txt"
  expect_nonzero await-timeout --sav "$SAV" --script "$WORK/await_timeout.txt"
  expect_nonzero untilout-timeout --sav "$SAV" --script "$WORK/untilout_timeout.txt"
  expect_nonzero autobattle-never-enters --sav "$SAV" --script "$WORK/autobattle_never_enters.txt"
  expect_nonzero replacement-timeout --sav "$SAV" --script "$WORK/replacement_timeout.txt"
  expect_nonzero player-replacement-timeout --sav "$SAV" --script "$WORK/player_replacement_timeout.txt"
else
  echo "  [skip] assertion cases need --sav"
fi

echo "== navigation handling =="

# New Bark Town (10,10) -> UP enters Elm's lab at (6,12); RIGHT from there hits the lab wall.
cat > "$WORK/walk_blocked.txt" <<'EOF'
wait 120
mash 800
wait 200
walk UP 1
wait 200
walk RIGHT 20
EOF
cat > "$WORK/walk_blocked_optional.txt" <<'EOF'
wait 120
mash 800
wait 200
walk UP 1
wait 200
walk RIGHT 20 optional
EOF
# (1,9) is the lab's interior corner: UP and LEFT are walls on every iteration.
cat > "$WORK/escape_timeout.txt" <<'EOF'
wait 120
mash 800
wait 200
walk UP 1
wait 200
walk LEFT 4
walk UP 3
walk LEFT 1
escape UP 3
EOF
# New Bark Town has no tall grass, so a hunt there can only time out.
cat > "$WORK/hunt_timeout.txt" <<'EOF'
wait 120
mash 800
wait 200
hunt 25
EOF

if [ -n "$SAV" ]; then
  expect_nonzero walk-blocked --sav "$SAV" --script "$WORK/walk_blocked.txt"
  expect_zero    walk-blocked-optional --sav "$SAV" --script "$WORK/walk_blocked_optional.txt"
  expect_nonzero escape-timeout --sav "$SAV" --script "$WORK/escape_timeout.txt"
  expect_nonzero hunt-timeout --sav "$SAV" --script "$WORK/hunt_timeout.txt"
else
  echo "  [skip] navigation cases need --sav"
fi

echo
echo "== selftest summary: $CASES cases, $FAILURES failures =="
[ "$FAILURES" -eq 0 ] || exit 1
echo "all strictness self-tests behaved correctly"
