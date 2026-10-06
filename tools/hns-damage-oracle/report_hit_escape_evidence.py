#!/usr/bin/env python3
"""Reconcile immutable history, exact hit vectors and separate engine execution."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
import oracle_schema as schema
import oracle_backend as backend
import oracle_matrix as matrix
ROOT=Path(__file__).resolve().parents[2]
START='27205d4a1f84521c55e038dbe3ca0db8d0657ea6'
TARGET=Path(__file__).with_name('hit-escape-evidence.json')
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--check',action='store_true')
p.add_argument('--canonical-log',type=Path)
p.add_argument('--reversed-log',type=Path)
p.add_argument('--reversed-summary',type=Path)
a=p.parse_args()
oldtext=subprocess.check_output(['git','show',START+':tools/hns-damage-oracle/corpus.json'],cwd=ROOT,text=True)
newtext=TARGET.with_name('corpus.json').read_text()
old,new=map(schema.load_corpus_text,(oldtext,newtext))
byid={e['scenario']['id']:e for e in new['entries']}
for e in old['entries']:assert byid[e['scenario']['id']]==e,e['scenario']['id']
oldlines=[l for l in oldtext.splitlines() if '"observed":' in l]
assert set(oldlines)<=set(newtext.splitlines())
added=[e for e in new['entries'] if e['scenario']['id'].startswith('hit-escape-')]
assert len(old['entries'])==2466 and len(added)==57
for move in ('u-turn','volt-switch','flip-turn'):
    base=byid[f'hit-escape-{move}-neutral']['rolls']
    tech=byid[f'hit-escape-{move}-technician']['rolls']
    if move=='flip-turn':assert all(x>y for x,y in zip(tech,base))
    else:assert tech==base
    claws=byid[f'hit-escape-{move}-tough-claws']['rolls']
    fluffy=byid[f'hit-escape-{move}-fluffy']['rolls']
    if move=='volt-switch':assert base==claws==fluffy
    else:assert all(x>y for x,y in zip(claws,base)) and all(x<y for x,y in zip(fluffy,base))
    assert byid[f'hit-escape-{move}-long-reach-fluffy']['rolls']==base
previous=json.loads(TARGET.read_text()) if TARGET.exists() else {}
names=[n for n in backend.EXECUTION_NAMES if 'hit-escape' in n]
if a.canonical_log:
    log=backend.ANSI_RE.sub('',a.canonical_log.read_text())
    for name in names:assert re.search(r'DDXO '+name+r'(?: \d+/\d+)?: PASS',log),name
    success=[list(map(int,line.split('|')[2:])) for line in re.findall(r'DDXH\|success\|[^\r\n]+',log)]
    faint=[list(map(int,line.split('|')[2:])) for line in re.findall(r'DDXH\|faint\|[^\r\n]+',log)]
    assert len(success)==9 and len(faint)==3
    for move in (369,521,740):
        rows=[r for r in success if r[0]==move];assert len(rows)==3
        assert {r[1] for r in rows}=={0,1} and len({r[3] for r in rows})==1
        assert all(len(r)==8 and r[3]==r[4]==r[7] and r[5]==60000-r[3] and r[6]==0 for r in rows)
        f=next(r for r in faint if r[0]==move)
        assert len(f)==6 and f[1]==f[2]==1 and f[3]==f[4]==0 and f[5]==rows[0][7]
    execution=dict(success=success,faint=faint,proofs=names)
else:execution=previous['execution']
assert execution['proofs']==names
assert len(execution['success'])==9 and len(execution['faint'])==3
for move,slug in ((369,'u-turn'),(521,'volt-switch'),(740,'flip-turn')):
    rows=[r for r in execution['success'] if r[0]==move]
    expected=byid[f'hit-escape-{slug}-neutral']['rolls'][-1]
    assert len(rows)==3 and {r[1] for r in rows}=={0,1}
    assert all(len(r)==8 and r[3]==r[4]==r[7]==expected and r[5]==60000-expected and r[6]==0 for r in rows)
    f=next(r for r in execution['faint'] if r[0]==move)
    assert len(f)==6 and f[1]==f[2]==1 and f[3]==f[4]==0 and f[5]==expected
if a.reversed_log:
    assert a.reversed_summary and 'byte-identical' in a.reversed_summary.read_text()
    scenarios=matrix.build_scenarios()
    records=backend.parse_runner_output(a.reversed_log.read_text(),[s['id'] for s in scenarios])
    replay=[backend.assemble_entry(s,records[s['id']]) for s in scenarios]
    assert replay==new['entries']
    reverse=dict(entries=len(replay),byteIdentical=True,corpusSha256=hashlib.sha256(newtext.encode()).hexdigest())
else:reverse=previous['reversedReplay']
assert reverse['corpusSha256']==hashlib.sha256(newtext.encode()).hexdigest()
negative=json.loads((ROOT/'tools/hns-calc-census/hit-escape-negative-control.json').read_text())
assert negative['testSha256']==hashlib.sha256((ROOT/'tools/hns-calc-census/starting-head/HnsHitEscapeStartingHeadTest.kt').read_bytes()).hexdigest()
assert negative['startingSha']==START and negative['exitStatus']==0 and negative['tests']==1 and negative['failures']==negative['errors']==negative['skipped']==0
metadata=json.loads((ROOT/'tools/hns-move-mechanics/hns_move_damage_metadata.json').read_text())
assert {int(i) for i,m in metadata['moves'].items() if m.get('fixedSingleHitEscape')}=={369,521,740}
out=dict(pinnedCommit=metadata['pinnedCommit'],pinnedMoveMetadata={i:metadata['moves'][i] for i in ('369','521','740')},startingSha=START,historicalCount=len(old['entries']),historicalObjectsAndCanonicalLinesUnchanged=True,
    historicalEntryLinesSha256=hashlib.sha256('\n'.join(oldlines).encode()).hexdigest(),
    newScenarios=len(added),newModelled=sum(e['scenario']['surface']=='modelled' for e in added),
    newEngineOnly=sum(e['scenario']['surface']=='engine-only' for e in added),total=len(new['entries']),
    modelled=sum(e['scenario']['surface']=='modelled' for e in new['entries']),engineOnly=sum(e['scenario']['surface']=='engine-only' for e in new['entries']),
    execution=execution,executionSourceSha256=hashlib.sha256(backend.HIT_ESCAPE_EXECUTION_SOURCE.encode()).hexdigest(),reversedReplay=reverse,negativeControlVerified=True,
    registeredDivergences=len(json.loads(TARGET.with_name('known_divergences.json').read_text())['divergences']),hardware='NOT_RUN')
text=json.dumps(out,indent=2,sort_keys=True)+'\n'
if a.check:assert TARGET.read_text()==text,'Hit-escape oracle evidence stale'
else:TARGET.write_text(text)
print(json.dumps({k:out[k] for k in ('historicalCount','newScenarios','newModelled','newEngineOnly','total','modelled','engineOnly')},indent=2))
