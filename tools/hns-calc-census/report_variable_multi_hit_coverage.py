#!/usr/bin/env python3
"""Measure Slice 13 admissions and residual blockers against the frozen Slice-12 census."""
import argparse
import collections
import gzip
import json
import subprocess
from pathlib import Path

from report_move_coverage import CENSUS, ROOT, report

START = "2d57cf8b991b04e676fd6d920ddcdf52159f1735"
SLICE13 = "c91f024ad50f428fdd99b537e2caa6dab3abd431"
TARGET = {
    "Arm Thrust": 2, "Bone Rush": 8, "Bullet Seed": 32, "Comet Punch": 2,
    "Double Slap": 30, "Fury Attack": 44, "Fury Swipes": 48, "Icicle Spear": 12,
    "Pin Missile": 30, "Rock Blast": 52, "Spike Cannon": 22, "Tail Slap": 2,
}
MOVE_GATE = "HNS_MOVE_MECHANICS_NOT_MODELLED"

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--check", action="store_true")
a = p.parse_args()
before = json.loads(gzip.decompress(subprocess.check_output(["git", "show", f"{START}:{CENSUS}"], cwd=ROOT)))
after = json.loads(gzip.decompress(subprocess.check_output(["git", "show", f"{SLICE13}:{CENSUS}"], cwd=ROOT)))
inventory = json.loads((ROOT / "tools/hns-calc-census/trainer_inventory.json").read_text())
assert inventory == json.loads(subprocess.check_output(
    ["git", "show", f"{START}:tools/hns-calc-census/trainer_inventory.json"], cwd=ROOT))
metadata = json.loads((ROOT / "tools/hns-move-mechanics/hns_move_damage_metadata.json").read_text())
out = report(before, after, inventory, metadata, "EFFECT_HIT", START, selected_moves=TARGET)

assert before["resultTiers"] == {"FULLY_MODELLED": 21440, "CAVEATED_ESTIMATE": 462, "REFUSED": 2376}
assert len(before["requests"]) == len(after["requests"]) == 24278
assert out["beforeLead"]["battlesTotal"] == out["afterLead"]["battlesTotal"] == 651
assert out["beforeLead"]["pairsTotal"] == out["afterLead"]["pairsTotal"] == 1302
assert out["beforeLead"]["requestsDisplayable"] == 7548
assert out["beforeLead"]["eligibleRequests"] == 8450
assert before["leadMatchup"]["definition"] == after["leadMatchup"]["definition"]
assert not out["outsideSliceChanges"], "Unexplained outside-slice transitions"

before_rows = [r for r in before["requests"] if r["move"] in TARGET]
after_rows = [r for r in after["requests"] if r["move"] in TARGET]
assert len(before_rows) == len(after_rows) == sum(TARGET.values()) == 284
counts = {move: sum(r["move"] == move for r in before_rows) for move in TARGET}
assert counts == TARGET
assert all(MOVE_GATE in r["limitations"] and r["tier"] == "REFUSED" for r in before_rows)

move_only = [r for r in before_rows if r["limitations"] == [MOVE_GATE]]
overlaps = collections.Counter(reason for r in before_rows for reason in r["limitations"] if reason != MOVE_GATE)
transitions = [t for t in out["transitions"] if t["key"] in {r["key"] for r in before_rows}]
transition_counts = collections.Counter(t["oldTier"] + " -> " + t["newTier"] for t in transitions)
assert transition_counts.get("REFUSED -> CAVEATED_ESTIMATE", 0) == 0
assert all(t["oldTier"] == "REFUSED" for t in transitions)
residual = [r for r in after_rows if r["tier"] == "REFUSED"]
residual_reasons = collections.Counter(reason for r in residual for reason in r["limitations"])

# The frozen repeated EFFECT_HIT opportunity includes this plain family plus the two deliberately
# excluded move-level/secondary-effect families. Fixed-two requests have already left this gate.
remaining_ids = {"Scale Shot", "Twineedle"}
remaining = [r for r in before["requests"] if r["move"] in TARGET or r["move"] in remaining_ids]
assert len(remaining) == 288
assert sum(r["move"] == "Scale Shot" for r in remaining) == 2
assert sum(r["move"] == "Twineedle" for r in remaining) == 2

out.update(
    targetMoveRequestCounts=counts,
    targetRequests=284,
    targetBattles=len({r["trainer"] for r in before_rows}),
    targetLeadPairs=len({(r["trainer"], r["referenceTeam"]) for r in before_rows if r["partySlot"] == 0}),
    targetLeadRequests=sum(r["partySlot"] == 0 for r in before_rows),
    targetMoveOnlyUpperBound=len(move_only),
    targetOverlappingBlockers=dict(sorted(overlaps.items())),
    targetTransitionCounts=dict(sorted(transition_counts.items())),
    targetNewlyFullyModelled=sum(t["oldTier"] == "REFUSED" and t["newTier"] == "FULLY_MODELLED" for t in transitions),
    targetResidualRefused=len(residual),
    targetResidualReasons=dict(sorted(residual_reasons.items())),
    targetResidualRequests=[dict(key=r["key"], move=r["move"], trainer=r["trainer"],
                                 partySlot=r["partySlot"], limitations=r["limitations"], causes=r["causes"])
                            for r in residual],
    repeatedEffectHitRequestsAfterSlice12=288,
    repeatedEffectHitResidualMoveRequests={
        "plainVariableSlice13": 284, "Scale Shot": 2, "Twineedle": 2
    },
    targetRefusedToCaveated=0,
    historicalPopulationPreserved=True,
    outsideSliceChangesCount=0,
    remainingFamilyRanking=report(after, after, inventory, metadata, "EFFECT_HIT", START,
                                  selected_moves=TARGET)["ranking"],
)
text = json.dumps(out, indent=2, sort_keys=True) + "\n"
path = ROOT / "tools/hns-calc-census/move-coverage-slice-13.json"
if a.check:
    assert path.read_text() == text, "Variable multi-hit census evidence stale"
else:
    path.write_text(text)
print(json.dumps({k: out[k] for k in (
    "before", "after", "beforeLead", "afterLead", "battlesGainingRequests",
    "targetRequests", "targetBattles", "targetLeadPairs", "targetLeadRequests",
    "targetMoveOnlyUpperBound", "targetTransitionCounts", "targetNewlyFullyModelled",
    "targetResidualRefused", "targetResidualReasons", "targetRefusedToCaveated"
)}, indent=2))
