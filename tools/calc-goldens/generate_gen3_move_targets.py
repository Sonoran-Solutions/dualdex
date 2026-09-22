#!/usr/bin/env python3
"""Regenerate ``gen3_move_targets.json`` from a pinned pret/pokeemerald checkout.

The committed file is the single machine-readable answer to "what is this Generation III move's
target class in the retail engine?", which the vanilla Doubles spread gate needs and which no other
committed artifact carries: neither the golden fixture file nor the item/ability catalogues contain
per-move target classes.

Provenance is the pinned decompilation, read from three files that together define the mapping:

  * ``src/data/battle_moves.h``  — ``.target`` and ``.power``, in ``MOVES_COUNT`` order;
  * ``include/constants/moves.h``— the ``MOVE_*`` symbol for each index;
  * ``src/data/text/move_names.h`` — the engine-facing name for each index.

Usage:
    python3 tools/calc-goldens/generate_gen3_move_targets.py \\
        --pokeemerald /path/to/pokeemerald [--check]

``--check`` re-derives the document and fails when the committed file differs, so a future edit to
the committed artifact cannot claim a provenance it does not have.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

OUTPUT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "gen3_move_targets.json")

PINNED_COMMIT = "5eff78649e7170a877b961ef0b3da13b81a16038"

SPREAD_TARGETS = ("MOVE_TARGET_BOTH", "MOVE_TARGET_FOES_AND_ALLY")


def _git_show(repo: str, path: str) -> str:
    """The bytes of `PINNED_COMMIT:path` in `repo`, or a loud failure.

    Reading through git rather than the working tree is the whole provenance guarantee: `--check`
    must not be able to certify a document derived from whatever files happen to be checked out.
    """
    out = subprocess.run(
        ["git", "-C", repo, "show", f"{PINNED_COMMIT}:{path}"],
        capture_output=True,
        text=True,
    )
    if out.returncode != 0:
        raise SystemExit(
            f"cannot read {path} at the pinned commit {PINNED_COMMIT} from {repo}:\n"
            f"{out.stderr.strip()}\n"
            "The provenance this file records is only meaningful if that exact revision is present.\n"
            "Fetch it (for example `git -C <checkout> fetch origin " + PINNED_COMMIT + "`) and retry."
        )
    return out.stdout


def verify_pin(repo: str) -> None:
    """Fail unless `repo` actually contains `PINNED_COMMIT`."""
    out = subprocess.run(
        ["git", "-C", repo, "cat-file", "-e", f"{PINNED_COMMIT}^{{commit}}"],
        capture_output=True,
        text=True,
    )
    if out.returncode != 0:
        raise SystemExit(
            f"{repo} does not contain the pinned commit {PINNED_COMMIT}; the recorded provenance "
            "would be false"
        )


def move_ids(pokeemerald: str) -> list[str]:
    """`MOVE_*` symbols in ascending index order, from `include/constants/moves.h`."""
    entries: list[tuple[int, str]] = []
    for line in _git_show(pokeemerald, "include/constants/moves.h").splitlines():
        match = re.match(r"\s*#define\s+(MOVE_[A-Z0-9_]+)\s+(\d+)\s*$", line)
        if match:
            entries.append((int(match.group(2)), match.group(1)))
    if not entries:
        raise SystemExit("no MOVE_* constants found; is --pokeemerald a pret/pokeemerald checkout?")
    return [symbol for _, symbol in sorted(entries)]


def move_names(pokeemerald: str) -> list[str]:
    """Engine-facing move names in index order, from `src/data/text/move_names.h`."""
    text = _git_show(pokeemerald, "src/data/text/move_names.h")
    return re.findall(r'_\("([^"]*)"\)', text)


def move_targets(pokeemerald: str) -> dict[str, tuple[str, int]]:
    """{symbol: (target class, base power)} parsed per `[MOVE_X] = { ... }` block.

    The split is by symbol boundary rather than a fixed closing-brace pattern, so a block whose
    closing brace is indented differently cannot silently drop out of the table.
    """
    source = _git_show(pokeemerald, "src/data/battle_moves.h")
    starts = [(m.start(), m.group(1)) for m in re.finditer(r"\[(MOVE_[A-Z0-9_]+)\]\s*=", source)]
    result: dict[str, tuple[str, int]] = {}
    for index, (position, symbol) in enumerate(starts):
        end = starts[index + 1][0] if index + 1 < len(starts) else len(source)
        body = source[position:end]
        target = re.search(r"\.target\s*=\s*([A-Z0-9_]+)", body)
        power = re.search(r"\.power\s*=\s*(\d+)", body)
        if target is None:
            continue
        result[symbol] = (target.group(1), int(power.group(1)) if power else 0)
    return result


def read_committed(path: str) -> str:
    """The committed artifact as text (only used by --check)."""
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def build_document(pokeemerald: str) -> dict:
    ids = move_ids(pokeemerald)
    names = move_names(pokeemerald)
    if len(ids) != len(names):
        raise SystemExit(
            f"move constant count ({len(ids)}) does not match move name count ({len(names)}); "
            "the pinned files are inconsistent"
        )
    targets = move_targets(pokeemerald)

    moves = []
    for symbol, name in zip(ids, names):
        if symbol == "MOVE_NONE":
            continue
        target, power = targets.get(symbol, ("UNKNOWN", 0))
        moves.append({"name": name, "constant": symbol, "target": target, "power": power})

    unknown = [move["constant"] for move in moves if move["target"] == "UNKNOWN"]
    if unknown:
        raise SystemExit(f"no target class parsed for {len(unknown)} moves, e.g. {unknown[:5]}")

    return {
        "schema": "dualdex.gen3_move_targets.v1",
        "provenance": {
            "project": "pret/pokeemerald",
            "commit": PINNED_COMMIT,
            "sources": [
                "src/data/battle_moves.h (.target, .power, in MOVES_COUNT order)",
                "include/constants/moves.h (symbol -> index)",
                "src/data/text/move_names.h (index -> engine name)",
            ],
            "derivation": (
                "One entry per Generation III move (MOVE_NONE excluded): the engine-facing name, the "
                "pinned symbol, the pinned target class, and the pinned base power. Regenerated by "
                "tools/calc-goldens/generate_gen3_move_targets.py from the pinned checkout; the "
                "consumer is native/tests/test_js_calc.c :: "
                "check_gen3_spread_move_table_matches_engine(), which recovers each move's pre-roll "
                "damage in Singles and Doubles against the shipped bundle and requires the engine to "
                "reduce exactly the MOVE_TARGET_BOTH moves with non-zero power."
            ),
        },
        "moves": moves,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--pokeemerald", required=True, help="path to a pret/pokeemerald checkout")
    parser.add_argument("--check", action="store_true", help="fail instead of writing when the file differs")
    args = parser.parse_args(argv)

    if not os.path.isdir(args.pokeemerald):
        raise SystemExit(f"--pokeemerald is not a directory: {args.pokeemerald}")
    verify_pin(args.pokeemerald)

    document = build_document(args.pokeemerald)
    rendered = json.dumps(document, indent=2) + "\n"

    if args.check:
        current = read_committed(OUTPUT_PATH) if os.path.exists(OUTPUT_PATH) else ""
        if current != rendered:
            print(
                f"{OUTPUT_PATH} does not match the pinned checkout at {args.pokeemerald}",
                file=sys.stderr,
            )
            return 1
        print(
            f"{os.path.basename(OUTPUT_PATH)} verified against the pinned checkout "
            f"({len(document['moves'])} moves, "
            f"{sum(1 for m in document['moves'] if m['target'] in SPREAD_TARGETS)} spread-capable)"
        )
        return 0

    with open(OUTPUT_PATH, "w", encoding="utf-8") as handle:
        handle.write(rendered)
    halved = sum(1 for m in document["moves"] if m["target"] == "MOVE_TARGET_BOTH" and m["power"] > 0)
    print(
        f"wrote {OUTPUT_PATH}: {len(document['moves'])} moves, "
        f"{sum(1 for m in document['moves'] if m['target'] in SPREAD_TARGETS)} spread-capable, "
        f"{halved} of them damaging"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
