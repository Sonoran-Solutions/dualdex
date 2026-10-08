#!/usr/bin/env python3
"""Measure Slice 14 request transitions against the immutable merged Slice-13 census."""
import argparse
import collections
import gzip
import json
import subprocess
from pathlib import Path

from report_move_coverage import CENSUS, ROOT, report

START="c91f024ad50f428fdd99b537e2caa6dab3abd431"
SLICE14="d9aca830d0ebcd89cbf6d1dcb47d19e8040c8bf9"
TARGET={"Scale Shot":2}
PLAIN={"Arm Thrust":2,"Bone Rush":8,"Bullet Seed":32,"Comet Punch":2,"Double Slap":30,
    "Fury Attack":44,"Fury Swipes":48,"Icicle Spear":12,"Pin Missile":30,"Rock Blast":52,"Spike Cannon":22,"Tail Slap":2}
REPEATED=set(PLAIN)|{"Scale Shot","Twineedle"}
p=argparse.ArgumentParser();p.add_argument("--check",action="store_true");args=p.parse_args()
before=json.loads(gzip.decompress(subprocess.check_output(["git","show",f"{START}:{CENSUS}"],cwd=ROOT)))
after=json.loads(gzip.decompress(subprocess.check_output(["git","show",f"{SLICE14}:{CENSUS}"],cwd=ROOT)))
inventory=json.loads((ROOT/"tools/hns-calc-census/trainer_inventory.json").read_text())
assert inventory==json.loads(subprocess.check_output(["git","show",f"{START}:tools/hns-calc-census/trainer_inventory.json"],cwd=ROOT))
metadata=json.loads(subprocess.check_output(["git","show",f"{SLICE14}:tools/hns-move-mechanics/hns_move_damage_metadata.json"],cwd=ROOT))
out=report(before,after,inventory,metadata,"EFFECT_HIT",START,selected_moves=TARGET)
assert before["resultTiers"]=={"FULLY_MODELLED":21448,"CAVEATED_ESTIMATE":462,"REFUSED":2368}
assert len(before["requests"])==len(after["requests"])==24278
assert before["leadMatchup"]["definition"]==after["leadMatchup"]["definition"]
assert out["beforeLead"]["battlesTotal"]==out["afterLead"]["battlesTotal"]==651
assert out["beforeLead"]["pairsTotal"]==out["afterLead"]["pairsTotal"]==1302
assert not out["outsideSliceChanges"]
old=[r for r in before["requests"] if r["move"]=="Scale Shot"]
new=[r for r in after["requests"] if r["move"]=="Scale Shot"]
assert len(old)==len(new)==2 and all(r["tier"]=="REFUSED" for r in old)
assert all(r["tier"] in {"FULLY_MODELLED","REFUSED"} for r in new)
transitions=[t for t in out["transitions"] if t["key"] in {r["key"] for r in old}]
counts=collections.Counter(t["oldTier"]+" -> "+t["newTier"] for t in transitions)
assert counts.get("REFUSED -> CAVEATED_ESTIMATE",0)==0
remaining=[r for r in after["requests"] if r["move"] in REPEATED and r["tier"]=="REFUSED"]
assert len([r for r in before["requests"] if r["move"] in REPEATED])==288
def request_facts(rows):
    return [dict(key=r["key"],tier=r["tier"],limitations=r["limitations"],causes=r["causes"],
        attacker=r["attacker"],attackerAbility=r["attackerAbility"],attackerItem=r["attackerItem"],
        defender=r["defender"],defenderAbility=r["defenderAbility"],defenderItem=r["defenderItem"])
        for r in rows]
out.update(scaleShotRequestsBefore=2,scaleShotRequestsAfter=2,scaleShotTransitionCounts=dict(sorted(counts.items())),
    scaleShotRequestFactsBefore=request_facts(old),scaleShotRequestFactsAfter=request_facts(new),
    scaleShotBattlesBefore=len({r["trainer"] for r in old}),
    scaleShotLeadPairsBefore=len({(r["trainer"],r["referenceTeam"]) for r in old if r["partySlot"]==0}),
    scaleShotLeadRequestsBefore=sum(r["partySlot"]==0 for r in old),
    scaleShotNewlyFullyModelled=sum(t["oldTier"]=="REFUSED" and t["newTier"]=="FULLY_MODELLED" for t in transitions),
    scaleShotResidualRefused=[dict(key=r["key"],limitations=r["limitations"]) for r in new if r["tier"]=="REFUSED"],
    scaleShotRefusedToCaveated=counts.get("REFUSED -> CAVEATED_ESTIMATE",0),
    originalRepeatedStrikeRequests=288,remainingRepeatedStrikeRefused=len(remaining),
    remainingRepeatedStrikeRefusedByMove=dict(sorted(collections.Counter(r["move"] for r in remaining).items())),
    historicalPopulationUnchanged=True,outsideSliceChangesCount=0,
    remainingFamilyRanking=report(after,after,inventory,metadata,"EFFECT_HIT",START)["ranking"])
text=json.dumps(out,indent=2,sort_keys=True)+"\n";path=ROOT/"tools/hns-calc-census/move-coverage-slice-14.json"
if args.check: assert path.read_text()==text,"Scale Shot census evidence stale"
else:path.write_text(text)
print(json.dumps({k:out[k] for k in ("before","after","beforeLead","afterLead","scaleShotTransitionCounts","scaleShotNewlyFullyModelled","scaleShotResidualRefused","scaleShotRefusedToCaveated","remainingRepeatedStrikeRefused","remainingRepeatedStrikeRefusedByMove")},indent=2))
