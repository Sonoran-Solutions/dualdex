#!/usr/bin/env python3
"""Self-contained checks on the committed ``gen3_move_targets.json``.

The file is generated from a pinned pret/pokeemerald checkout by
``generate_gen3_move_targets.py``, which the canonical gate cannot run (it needs the upstream
checkout). This entry point checks the committed artifact on its own, so a hand edit that breaks the
invariants the vanilla Doubles spread gate depends on fails ``./ci.sh test``:

  * every entry has the engine-facing name, a ``MOVE_*`` symbol, a known target class and a
    non-negative power, and names are unique;
  * the target classes are exactly the set the pinned header defines;
  * the damaged ``MOVE_TARGET_BOTH`` set - the moves the shipped pipeline reduces in Doubles, and
    therefore the moves ``Gen3DoublesSpreadMoves`` gates - is non-empty and stable in size, so a
    regeneration that silently dropped moves is caught here rather than in production.

The engine half of the proof (that the shipped bundle reduces exactly that set, over the whole move
list) lives in ``native/tests/test_js_calc.c``.
"""

from __future__ import annotations

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PATH = os.path.join(HERE, "gen3_move_targets.json")

PINNED_COMMIT = "5eff78649e7170a877b961ef0b3da18b81a16038"

KNOWN_TARGETS = {
    "MOVE_TARGET_SELECTED",
    "MOVE_TARGET_DEPENDS",
    "MOVE_TARGET_USER",
    "MOVE_TARGET_RANDOM",
    "MOVE_TARGET_BOTH",
    "MOVE_TARGET_FOES_AND_ALLY",
    "MOVE_TARGET_OPPONENTS_FIELD",
}

# The set the vanilla Doubles spread gate must cover. Pinned by count and by membership of its most
# familiar members: a regeneration that dropped one would otherwise silently un-gate it.
#
# Names are compared in the pinned source's own spelling (the file is generated from
# `src/data/text/move_names.h`, which is upper case), so the expectation is written that way too
# rather than mixed-case, which would never match.
EXPECTED_DAMAGING_BOTH_COUNT = 17
EXPECTED_DAMAGING_BOTH_MEMBERS = {
    "ROCK SLIDE", "SURF", "BLIZZARD", "SWIFT", "ERUPTION", "HYPER VOICE",
}


def main() -> int:
    with open(PATH, encoding="utf-8") as handle:
        document = json.load(handle)

    errors: list[str] = []

    if document.get("schema") != "dualdex.gen3_move_targets.v1":
        errors.append(f"unexpected schema {document.get('schema')!r}")

    provenance = document.get("provenance", {})
    if provenance.get("commit") != PINNED_COMMIT:
        errors.append(
            f"provenance commit is {provenance.get('commit')!r}, expected {PINNED_COMMIT!r}")

    moves = document.get("moves", [])
    if not moves:
        errors.append("no moves in the document")
        return _report(errors, 0, 0)

    names: set[str] = set()
    for move in moves:
        name = move.get("name")
        symbol = move.get("constant")
        target = move.get("target")
        power = move.get("power")
        if not name or not symbol or not isinstance(power, int) or power < 0:
            errors.append(f"malformed entry: {move!r}")
            continue
        if not symbol.startswith("MOVE_"):
            errors.append(f"{name}: constant {symbol!r} is not a MOVE_* symbol")
        if target not in KNOWN_TARGETS:
            errors.append(f"{name}: unknown target class {target!r}")
        if name in names:
            errors.append(f"duplicate move name {name!r}")
        names.add(name)

    damaging_both = {
        move["name"]
        for move in moves
        if move.get("target") == "MOVE_TARGET_BOTH" and move.get("power", 0) > 0
    }
    if len(damaging_both) != EXPECTED_DAMAGING_BOTH_COUNT:
        errors.append(
            f"{len(damaging_both)} damaging MOVE_TARGET_BOTH moves, expected "
            f"{EXPECTED_DAMAGING_BOTH_COUNT}")
    missing = EXPECTED_DAMAGING_BOTH_MEMBERS - damaging_both
    if missing:
        errors.append(f"the damaging MOVE_TARGET_BOTH set lost {sorted(missing)}")
    # Earthquake/Explosion are spread-capable in the games but must NOT be in this set: the shipped
    # pipeline does not reduce them, so gating them would refuse a correct calculation.
    for name in ("EARTHQUAKE", "EXPLOSION"):
        move = next((m for m in moves if m.get("name") == name), None)
        if move is None:
            errors.append(f"{name} is missing from the move list")
        elif move.get("target") == "MOVE_TARGET_BOTH":
            errors.append(
                f"{name} must be FOES_AND_ALLY, not BOTH: the shipped pipeline does not reduce it")

    return _report(errors, len(moves), len(damaging_both))


def _report(errors: list[str], total: int, damaging_both: int) -> int:
    if errors:
        print("GEN III MOVE TARGET TABLE CHECK FAILED:")
        for error in errors:
            print(f"  - {error}")
        return 1
    print(
        f"gen3_move_targets.json verified: {total} moves, {damaging_both} damaging "
        "MOVE_TARGET_BOTH moves (the set the vanilla Doubles spread gate covers)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
