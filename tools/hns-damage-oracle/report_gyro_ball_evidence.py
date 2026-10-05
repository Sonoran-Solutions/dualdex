#!/usr/bin/env python3
"""Verify historical bytes and actual pinned-engine Gyro Ball Speed observations."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import oracle_schema as schema
import oracle_backend as backend

START = '0c821f012b0e73e8cc50a7a8074e8aa2dcb623cc'
ROOT = Path(__file__).resolve().parents[2]
TARGET = Path(__file__).with_name('gyro-ball-evidence.json')

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check', action='store_true')
    p.add_argument('--engine-log', type=Path)
    p.add_argument('--reversed-log', type=Path)
    p.add_argument('--reversed-summary', type=Path)
    args = p.parse_args()
    old_text = subprocess.check_output(['git','show',START+':tools/hns-damage-oracle/corpus.json'],cwd=ROOT,text=True)
    new_text = TARGET.with_name('corpus.json').read_text()
    old, new = map(schema.load_corpus_text,(old_text,new_text))
    by_id = {e['scenario']['id']:e for e in new['entries']}
    assert len(old['entries']) == 2265
    for e in old['entries']: assert by_id[e['scenario']['id']] == e, e['scenario']['id']
    assert {l for l in old_text.splitlines() if '"observed":' in l} <= set(new_text.splitlines())
    entries = [e for e in new['entries'] if 'move-coverage-slice-8' in e['scenario']['tags']]
    negative = json.loads((ROOT/'tools/hns-calc-census/gyro-ball-negative-control.json').read_text())
    assert negative['startingSha']==START and negative['exitStatus']==0
    assert negative['testSourceSha256']==hashlib.sha256((ROOT/'app/src/test/java/com/dualdex/calculator/HnsGyroBallStartingHeadTest.kt').read_bytes()).hexdigest()
    if args.check:
        replay = json.loads(TARGET.read_text())['engineReplay']
    else:
        assert args.engine_log and args.reversed_log and args.reversed_summary
        assert 'regenerated corpus is byte-identical' in args.reversed_summary.read_text()
        scenarios = [e['scenario'] for e in new['entries']]
        for log in (args.engine_log,args.reversed_log):
            records = backend.parse_runner_output(log.read_text(),[s['id'] for s in scenarios])
            assert [backend.assemble_entry(s,records[s['id']]) for s in scenarios] == new['entries']
        replay = dict(canonicalEntries=len(new['entries']),reversedEntries=len(new['entries']),byteIdentical=True,
            corpusSha256=hashlib.sha256(new_text.encode()).hexdigest())
    assert replay['canonicalEntries']==replay['reversedEntries']==len(new['entries']) and replay['byteIdentical']
    assert replay['corpusSha256']==hashlib.sha256(new_text.encode()).hexdigest()
    rounding = by_id['gyro-ball-tailwind-before-swamp-rounding']['observed']['effectiveSpeeds']
    assert rounding['attacker']['total']==51 and rounding['basePower']==50
    proc = by_id['gyro-ball-quick-claw-proc']['observed']
    inactive = by_id['gyro-ball-quick-claw-no-proc']['observed']
    assert proc['attacker']['runtime']['lastToMove']==0
    assert inactive['attacker']['runtime']['lastToMove']==1
    assert proc['effectiveSpeeds']==inactive['effectiveSpeeds']
    neutral = by_id['gyro-ball-equal']['observed']['effectiveSpeeds']
    for control in ('trick-room','lagging-tail','full-incense','stall','quick-draw'):
        assert by_id['gyro-ball-'+control]['observed']['effectiveSpeeds']==neutral
    out = dict(startingSha=START,historicalCount=len(old['entries']),historicalEntryLinesUnchanged=True,
        newScenarios=len(entries),total=len(new['entries']),modelled=sum(e['scenario']['surface']=='modelled' for e in new['entries']),
        engineOnly=sum(e['scenario']['surface']=='engine-only' for e in new['entries']),engineReplay=replay,
        effectiveSpeeds={e['scenario']['id']:e['observed']['effectiveSpeeds'] for e in entries},
        negativeControlVerified=True,registeredDivergences=len(json.loads(TARGET.with_name('known_divergences.json').read_text())['divergences']))
    text=json.dumps(out,indent=2,sort_keys=True)+'\n'
    if args.check: assert TARGET.read_text()==text, 'Gyro Ball evidence stale'
    else: TARGET.write_text(text)
    print(f"{out['historicalCount']} unchanged historical entries; {out['newScenarios']} new; {out['total']} total; source Speed/BP verified")

if __name__=='__main__': main()
