#!/usr/bin/env python3
"""Verify frozen historical entries and actual Brine hit-boundary HP evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import oracle_schema as schema
import oracle_backend as backend

START = '9f9405c919b98dde000b349724b15ec2f3f2366d'
ROOT = Path(__file__).resolve().parents[2]
TARGET = Path(__file__).with_name('brine-evidence.json')

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check', action='store_true')
    p.add_argument("--shortcut-log", type=Path)
    p.add_argument("--engine-log", type=Path)
    p.add_argument("--reversed-log", type=Path)
    p.add_argument("--reversed-summary", type=Path)
    args = p.parse_args()
    old_text = subprocess.check_output(['git','show',START+':tools/hns-damage-oracle/corpus.json'],cwd=ROOT,text=True)
    new_text = TARGET.with_name('corpus.json').read_text()
    old = schema.load_corpus_text(old_text)
    new = schema.load_corpus_text(new_text)
    by_id = {e['scenario']['id']:e for e in new['entries']}
    for e in old['entries']: assert by_id[e['scenario']['id']] == e, e['scenario']['id']
    assert {l for l in old_text.splitlines() if '"observed":' in l} <= set(new_text.splitlines())
    entries = [e for e in new['entries'] if 'move-coverage-slice-7' in e['scenario']['tags']]
    pairs = {}
    for e in entries:
        stats=e['scenario']['defender']['stats']; observed=e['observed']['defender']
        assert (observed['hpAtHit'],observed['maxHpAtHit']) == (stats['hp'],stats['maxHp'])
        pairs[e['scenario']['id']] = dict(hp=observed['hpAtHit'],maxHp=observed['maxHpAtHit'],
            boost=observed['hpAtHit'] <= observed['maxHpAtHit']//2)
    negative = json.loads((ROOT/'tools/hns-calc-census/brine-negative-control.json').read_text())
    assert negative['startingSha']==START and negative['compiled'] and negative['failures']==1
    assert 'limitations=[HNS_MOVE_MECHANICS_NOT_MODELLED]' in negative['failureMessage']
    assert negative['testSourceSha256']==hashlib.sha256((ROOT/'app/src/test/java/com/dualdex/calculator/HnsBrineAdmissionTest.kt').read_bytes()).hexdigest()
    source=(ROOT/'tools/calc-bundler/entry.js').read_text()
    start=source.index('const basePowerModifier =')
    brine=source.index('basePowerModifier.addHalfUp(8192)',start)
    assert start < brine < source.index('doubles?.helpingHand',start) < source.index("case 'Technician'",start) < source.index('const bp = basePowerModifier.apply(moveBasePower)',start)
    shortcut_source = source.replace('    basePowerModifier.addHalfUp(8192);\n  const earthquakeFamily',
        '    moveBasePower *= 2;\n  const earthquakeFamily')
    assert shortcut_source != source
    if args.check:
        probe = json.loads(TARGET.read_text())['shortcutProbe']
    else:
        assert args.shortcut_log, '--shortcut-log from an actual temporary-source probe is required'
        log = args.shortcut_log.read_text()
        count = len(new['entries'])
        assert f'exact 16-roll matches: {count}; registered divergences: 0/0' in log and 'H&S differential damage oracle: PASS' in log
        probe = dict(exactMatches=count, brineCases=len(entries), numericalDifferences=0,
            shortcutSourceSha256=hashlib.sha256(shortcut_source.encode()).hexdigest(),
            productionSourceSha256=hashlib.sha256(source.encode()).hexdigest(),
            logSha256=hashlib.sha256(log.encode()).hexdigest())
    assert probe['exactMatches']==len(new['entries']) and probe['brineCases']==len(entries) and probe['numericalDifferences']==0
    assert probe['productionSourceSha256']==hashlib.sha256(source.encode()).hexdigest()
    assert probe['shortcutSourceSha256']==hashlib.sha256(shortcut_source.encode()).hexdigest()
    if args.check:
        replay = json.loads(TARGET.read_text())['engineReplay']
    else:
        assert args.engine_log and args.reversed_log and args.reversed_summary, 'Both actual engine logs and reversed verification summary are required'
        assert 'regenerated corpus is byte-identical to the committed corpus.json' in args.reversed_summary.read_text()
        scenarios = [e['scenario'] for e in new['entries']]
        for log in (args.engine_log, args.reversed_log):
            records = backend.parse_runner_output(log.read_text(), [s['id'] for s in scenarios])
            actual = [backend.assemble_entry(s, records[s['id']]) for s in scenarios]
            assert actual == new['entries'], 'Actual engine observations differ from committed corpus'
        replay = dict(canonicalEntries=len(new['entries']), reversedEntries=len(new['entries']),
            byteIdentical=True, callbackOnlyBrineHp=True, corpusSha256=hashlib.sha256(new_text.encode()).hexdigest())
    assert replay['canonicalEntries']==replay['reversedEntries']==len(new['entries']) and replay['byteIdentical'] and replay['callbackOnlyBrineHp']
    assert replay['corpusSha256']==hashlib.sha256(new_text.encode()).hexdigest()
    out=dict(engineReplay=replay,shortcutProbe=probe,startingSha=START,historicalCount=len(old['entries']),historicalEntryLinesUnchanged=True,
        newScenarios=len(entries),total=len(new['entries']),modelled=sum(e['scenario']['surface']=='modelled' for e in new['entries']),
        engineOnly=sum(e['scenario']['surface']=='engine-only' for e in new['entries']),hitBoundaryHp=pairs,
        sourceStage='CalcMoveBasePowerAfterModifiers: move-effect accumulator before Helping Hand/Gems/abilities/items',
        q12Modifier=8192,sourceBasePower=65,sourceStageRegression=True,
        testSourceSha256=new['provenance']['generator']['testSourceSha256'],negativeControlVerified=True,
        registeredDivergences=len(json.loads(TARGET.with_name('known_divergences.json').read_text())['divergences']))
    text=json.dumps(out,indent=2,sort_keys=True)+'\n'
    if args.check: assert TARGET.read_text()==text, 'Brine evidence stale'
    else: TARGET.write_text(text)
    print(f"{out['historicalCount']} unchanged historical entries; {out['newScenarios']} new; {out['total']} total; actual HP/maxHP verified")

if __name__=='__main__': main()
