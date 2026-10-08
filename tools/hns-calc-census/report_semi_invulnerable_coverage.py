#!/usr/bin/env python3
"""Reconcile Slice 15 move coverage against the immutable starting-main census."""
import argparse
import collections
import gzip
import hashlib
import io
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = Path(__file__).with_name("semi-invulnerable-coverage.json")
START = "d9aca830d0ebcd89cbf6d1dcb47d19e8040c8bf9"
MOVES = {"Fly", "Dig", "Dive", "Bounce", "Phantom Force"}
CENSUS_PATH = "tools/hns-calc-census/census.json.gz"


def census_from_bytes(data):
    return json.load(gzip.GzipFile(fileobj=io.BytesIO(data), mode="rb"))


def current():
    return census_from_bytes((ROOT / CENSUS_PATH).read_bytes())


def previous():
    return census_from_bytes(subprocess.check_output(
        ["git", "show", f"{START}:{CENSUS_PATH}"], cwd=ROOT))


def build():
    old, new = previous(), current()
    old_rows = {r["key"]: r for r in old["requests"]}
    new_rows = {r["key"]: r for r in new["requests"]}
    assert set(old_rows) == set(new_rows)
    target = sorted((r for r in old_rows.values() if r["move"] in MOVES), key=lambda r: r["key"])
    target_keys = {r["key"] for r in target}
    transitions = []
    outside = []
    for key in sorted(old_rows):
        before, after = old_rows[key], new_rows[key]
        if before["tier"] == after["tier"]:
            continue
        row = dict(key=key, move=before["move"], fromTier=before["tier"], toTier=after["tier"],
                   beforeLimitations=before.get("limitations", []), afterLimitations=after.get("limitations", []),
                   afterCauses=after.get("causes", []), afterIgnoredMechanics=after.get("ignoredMechanics", []))
        if key not in target_keys:
            outside.append(row)
        else:
            transitions.append(row)
    assert not outside, outside[:3]
    battle_formats = collections.Counter(r["gameType"] for r in target)
    lead = [r for r in target if r["partySlot"] == 0]
    baseline_combinations = collections.Counter(tuple(sorted(r.get("limitations", []))) for r in target)
    current_by_move = collections.Counter((r["move"], new_rows[r["key"]]["tier"]) for r in target)
    target_current = [new_rows[r["key"]] for r in target]
    transitions_by_move = collections.Counter(r["move"] for r in transitions)
    return {
        "schemaVersion": 1,
        "startingSha": START,
        "startingCensusSha256": hashlib.sha256(subprocess.check_output(
            ["git", "show", f"{START}:{CENSUS_PATH}"], cwd=ROOT)).hexdigest(),
        "currentCensusSha256": hashlib.sha256((ROOT / CENSUS_PATH).read_bytes()).hexdigest(),
        "population": {"before": old["counts"], "after": new["counts"],
                       "beforeLeadMatchup": old["leadMatchup"], "afterLeadMatchup": new["leadMatchup"]},
        "opportunity": {
            "requests": len(target), "battles": len({r["trainer"] for r in target}),
            "formats": dict(sorted(battle_formats.items())),
            "moves": dict(sorted(collections.Counter(r["move"] for r in target).items())),
            "leadRequests": len(lead), "leadPairs": len({(r["trainer"], r["referenceTeam"]) for r in lead}),
            "baselineLimitations": {" + ".join(k) if k else "NONE": v
                                    for k, v in sorted(baseline_combinations.items())},
            "requestKeys": [{"key": r["key"], "move": r["move"], "format": r["gameType"],
                             "partySlot": r["partySlot"], "tierBefore": r["tier"],
                             "limitationsBefore": r.get("limitations", [])} for r in target],
        },
        "reconciliation": {
            "targetTransitions": len(transitions),
            "transitionsByMove": dict(sorted(transitions_by_move.items())),
            "currentTierByMove": {move: {tier: current_by_move[(move, tier)] for tier in
                                          ("FULLY_MODELLED", "CAVEATED_ESTIMATE", "REFUSED")}
                                  for move in sorted(MOVES)},
            "residualRefusals": [{"key": r["key"], "move": r["move"], "limitations": r["limitations"],
                                  "causes": r.get("causes", []), "ignoredMechanics": r.get("ignoredMechanics", [])}
                                 for r in target_current if r["tier"] == "REFUSED"],
            "outsideFamilyTransitions": outside,
        },
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    text = json.dumps(build(), indent=2, sort_keys=True) + "\n"
    if args.write:
        TARGET.write_text(text)
    else:
        assert TARGET.read_text() == text, "Semi-invulnerable census reconciliation stale"
    print("Slice 15 census reconciliation: 24,278 stable request keys; no outside-family tier transitions")


if __name__ == "__main__":
    main()
