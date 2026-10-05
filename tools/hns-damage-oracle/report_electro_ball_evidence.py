#!/usr/bin/env python3
"""Verify historical bytes, source Electro Ball power, and safe zero-divisor evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import oracle_schema as schema
import oracle_backend as backend

START = '9ab694b99915f13956f0cf652c792bdc92d10409'
ROOT = Path(__file__).resolve().parents[2]
TARGET = Path(__file__).with_name('electro-ball-evidence.json')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check', action='store_true')
    p.add_argument('--reversed-log', type=Path)
    p.add_argument('--reversed-summary', type=Path)
    args = p.parse_args()
    old_text = subprocess.check_output(['git','show',START+':tools/hns-damage-oracle/corpus.json'],cwd=ROOT,text=True)
    new_text = TARGET.with_name('corpus.json').read_text()
    old, new = map(schema.load_corpus_text,(old_text,new_text))
    for patch in new['provenance']['backend']['harnessPatches']:
        assert patch['sha256']==hashlib.sha256((TARGET.parent/patch['path']).read_bytes()).hexdigest(), 'Harness provenance stale'
    by_id = {e['scenario']['id']:e for e in new['entries']}
    assert len(old['entries']) == 2341
    for e in old['entries']: assert by_id[e['scenario']['id']] == e, e['scenario']['id']
    old_lines = [l for l in old_text.splitlines() if '"observed":' in l]
    assert set(old_lines) <= set(new_text.splitlines())
    gyro_lines = [l for l in old_lines if '"id":"gyro-ball-' in l]
    assert len(gyro_lines) == 76
    entries = [e for e in new['entries'] if 'move-coverage-slice-9' in e['scenario']['tags']]
    assert len(new['entries']) == len(old['entries']) + len(entries)
    negative = json.loads((ROOT/'tools/hns-calc-census/electro-ball-negative-control.json').read_text())
    assert negative['startingSha']==START and negative['exitStatus']==0 and negative['failures']==0
    assert negative['testSourceSha256']==hashlib.sha256((ROOT/'app/src/test/java/com/dualdex/calculator/HnsElectroBallStartingHeadTest.kt').read_bytes()).hexdigest()
    if args.check:
        replay = json.loads(TARGET.read_text())['engineReplay']
    else:
        assert args.reversed_log and args.reversed_summary
        assert 'regenerated corpus is byte-identical' in args.reversed_summary.read_text()
        scenarios = [e['scenario'] for e in new['entries']]
        records = backend.parse_runner_output(args.reversed_log.read_text(),[s['id'] for s in scenarios])
        assert [backend.assemble_entry(s,records[s['id']]) for s in scenarios] == new['entries']
        replay = dict(canonicalEntries=len(new['entries']),reversedEntries=len(new['entries']),byteIdentical=True,
            corpusSha256=hashlib.sha256(new_text.encode()).hexdigest())
    assert replay['canonicalEntries']==replay['reversedEntries']==len(new['entries']) and replay['byteIdentical']
    assert replay['corpusSha256']==hashlib.sha256(new_text.encode()).hexdigest()
    def speeds(suffix): return by_id['electro-ball-'+suffix]['observed']['effectiveSpeeds']
    for a in (99,100,199,200,299,300,399,400,500):
        s=speeds('ratio-'+str(a))
        assert s['attacker']['total']==a and s['defender']['total']==100
        assert s['basePower']==[40,60,80,120,150][min(a//100,4)]
    zero=speeds('attacker-zero')
    assert zero['attacker']['total']==0 and zero['defender']['total']==100 and zero['basePower']==40
    unsafe=by_id['gyro-ball-defender-zero']['observed']['effectiveSpeeds']['defender']
    assert unsafe['raw']==1 and unsafe['stage']==-6 and unsafe['total']==0
    for bp,control in ((40,99),(60,100),(80,200)):
        boosted=by_id['electro-ball-technician-'+str(bp)]
        neutral=by_id['electro-ball-ratio-'+str(control)]
        assert boosted['observed']['effectiveSpeeds']['basePower']==bp
        if bp<=60: assert all(b>n for b,n in zip(boosted['rolls'],neutral['rolls']))
        else: assert boosted['rolls']==neutral['rolls']
    grounded=by_id['electro-ball-surge-surfer-grounded']
    airborne=by_id['electro-ball-surge-surfer-ungrounded']
    control=by_id['electro-ball-terrain-control']
    assert grounded['observed']['effectiveSpeeds']==airborne['observed']['effectiveSpeeds']
    assert speeds('surge-surfer-grounded')['attacker']['total']==200
    assert grounded['observed']['attacker']['terrainAffected'] and not airborne['observed']['attacker']['terrainAffected']
    assert control['observed']['attacker']['terrainAffected'] and speeds('terrain-control')['attacker']['total']==100
    assert all(g>a for g,a in zip(grounded['rolls'],airborne['rolls']))
    assert speeds('trick-room')==speeds('ratio-100')==speeds('quick-claw')==speeds('charge')
    assert speeds('charge-terrain-technician')['basePower']==60
    assert by_id['electro-ball-charge-terrain-technician']['observed']['attacker']['runtime']['chargeTimer']==1
    for s in entries: assert s['observed']['effectiveSpeeds']['defender']['total']>0
    out = dict(startingSha=START,historicalCount=len(old['entries']),historicalEntryLinesUnchanged=True,
        gyroHistoricalCount=len(gyro_lines),gyroHistoricalLinesSha256=hashlib.sha256('\n'.join(gyro_lines).encode()).hexdigest(),
        newScenarios=len(entries),newModelled=sum(e['scenario']['surface']=='modelled' for e in entries),
        newEngineOnly=sum(e['scenario']['surface']=='engine-only' for e in entries),total=len(new['entries']),
        modelled=sum(e['scenario']['surface']=='modelled' for e in new['entries']),
        engineOnly=sum(e['scenario']['surface']=='engine-only' for e in new['entries']),engineReplay=replay,
        effectiveSpeeds={e['scenario']['id']:e['observed']['effectiveSpeeds'] for e in entries},
        zeroDivisor=dict(defenderZeroSourceWitness='gyro-ball-defender-zero',sourceObservedDefender=unsafe,
            electroPowerInvoked=False,productionLimitation='HNS_ELECTRO_BALL_DEFENDER_SPEED_ZERO',
            sourceContract='Pinned CalcMoveBasePower divides by GetBattlerTotalSpeedStat(defender) with no guard; source-check pins the entire helper.'),
        negativeControlVerified=True,registeredDivergences=len(json.loads(TARGET.with_name('known_divergences.json').read_text())['divergences']))
    text=json.dumps(out,indent=2,sort_keys=True)+'\n'
    if args.check: assert TARGET.read_text()==text, 'Electro Ball evidence stale'
    else: TARGET.write_text(text)
    print(f"{out['historicalCount']} unchanged historical entries; {out['newScenarios']} new; {out['total']} total; source Speed/BP and zero-divisor distinction verified")


if __name__=='__main__': main()
