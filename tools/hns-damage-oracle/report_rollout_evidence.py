#!/usr/bin/env python3
"""Verify unchanged history, independent chain power and real lifecycle replay."""
import argparse,hashlib,json,subprocess,re
from pathlib import Path
import oracle_schema as schema
import oracle_backend as backend
import oracle_matrix as matrix
ROOT=Path(__file__).resolve().parents[2]
START='f6c300e508a33e3d58c8c0d4c423febec1cbe429'
TARGET=Path(__file__).with_name('rollout-evidence.json')
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');p.add_argument('--canonical-log',type=Path);p.add_argument('--reversed-log',type=Path);p.add_argument('--reversed-summary',type=Path);a=p.parse_args()
oldtext=subprocess.check_output(['git','show',START+':tools/hns-damage-oracle/corpus.json'],cwd=ROOT,text=True)
newtext=TARGET.with_name('corpus.json').read_text();old,new=map(schema.load_corpus_text,(oldtext,newtext))
byid={e['scenario']['id']:e for e in new['entries']}
for e in old['entries']:assert byid[e['scenario']['id']]==e,e['scenario']['id']
oldlines=[l for l in oldtext.splitlines() if '"observed":' in l]
assert set(oldlines)<=set(newtext.splitlines())
newentries=[e for e in new['entries'] if e['scenario']['id'].startswith('rollout-')]
assert len(old['entries'])==2394 and len(newentries)==72
for move in ('rollout','ice-ball'):
    for curl in (0,1):
        for timer in range(5):
            entry=byid[f'rollout-{move}-counter-{timer}-curl-{curl}'];c=entry['observed']['rollout']
            assert c['timer']==timer and c['defenseCurl']==curl
            assert c['basePower']==30*2**timer*(2 if curl else 1)
            if timer<3:
                tech=byid[f'rollout-{move}-technician-{timer}-curl-{curl}']
                if c['basePower']<=60:assert all(x>y for x,y in zip(tech['rolls'],entry['rolls']))
                else:assert tech['rolls']==entry['rolls']
    electric=byid[f'rollout-{move}-electrify-charge-terrain']
    assert electric['observed']['rollout']['electrified']==1
    assert electric['observed']['rollout']['effectiveType']==electric['observed']['move']['type']=='Electric'
    assert electric['observed']['rollout']['basePower']==60 and electric['rolls'][0]==244 and electric['rolls'][-1]==288
    normal=byid[f'rollout-{move}-normalize-charge']
    assert normal['observed']['rollout']['electrified']==0 and normal['observed']['move']['type']=='Normal'
    bullet=byid[f'rollout-{move}-bulletproof']['rolls']
    assert (bullet==[0]*16)==(move=='ice-ball')
previous=json.loads(TARGET.read_text()) if TARGET.exists() else {}
if a.canonical_log:
    log=backend.ANSI_RE.sub("",a.canonical_log.read_text());lines=re.findall(r'DDXL\|chain\|[^\r\n]+',log)
    assert len(lines)==36
    lifecycle=[]
    for line in lines:
        move,setup,index,pre,post,curl,power=map(int,line.split('|')[2:])
        assert pre==index%5 and post==(index+1)%5 and power==30*2**pre*(2 if curl else 1)
        lifecycle.append(dict(move=move,setup=setup,index=index,pre=pre,post=post,defenseCurl=curl,basePower=power))
    names=[n for n in backend.EXECUTION_NAMES if 'rollout' in n]
    for name in names:assert re.search(r'DDXO '+name+r'(?: \d+/\d+)?: PASS',log),name
else:lifecycle=previous['realLifecycle'];names=previous['executionProofs']
if a.reversed_log:
    assert a.reversed_summary and 'byte-identical' in a.reversed_summary.read_text()
    scenarios=matrix.build_scenarios();records=backend.parse_runner_output(a.reversed_log.read_text(),[s['id'] for s in scenarios])
    replay=[backend.assemble_entry(s,records[s['id']]) for s in scenarios]
    assert replay==new['entries']
    reverse=dict(entries=len(replay),byteIdentical=True,corpusSha256=hashlib.sha256(newtext.encode()).hexdigest())
else:reverse=previous['reversedReplay']
assert reverse['corpusSha256']==hashlib.sha256(newtext.encode()).hexdigest()
negative=json.loads((ROOT/'tools/hns-calc-census/rollout-negative-control.json').read_text())
assert negative['testSha256']==hashlib.sha256((ROOT/'tools/hns-calc-census/starting-head/HnsRolloutStartingHeadTest.kt').read_bytes()).hexdigest()
assert negative['startingSha']==START and negative['exitStatus']==0 and negative['tests']==1 and negative['failures']==0 and negative['errors']==0 and negative['skipped']==0
out=dict(startingSha=START,historicalCount=len(old['entries']),historicalObjectsAndCanonicalLinesUnchanged=True,
    historicalEntryLinesSha256=hashlib.sha256('\n'.join(oldlines).encode()).hexdigest(),
    newScenarios=len(newentries),newModelled=sum(e['scenario']['surface']=='modelled' for e in newentries),
    newEngineOnly=sum(e['scenario']['surface']=='engine-only' for e in newentries),total=len(new['entries']),
    modelled=sum(e['scenario']['surface']=='modelled' for e in new['entries']),engineOnly=sum(e['scenario']['surface']=='engine-only' for e in new['entries']),
    realLifecycle=lifecycle,executionProofs=names,reversedReplay=reverse,negativeControlVerified=True,
    registeredDivergences=len(json.loads(TARGET.with_name('known_divergences.json').read_text())['divergences']),hardware='NOT_RUN')
text=json.dumps(out,indent=2,sort_keys=True)+'\n'
if a.check:assert TARGET.read_text()==text,'Rollout oracle evidence stale'
else:TARGET.write_text(text)
print(json.dumps({k:out[k] for k in ('historicalCount','newScenarios','newModelled','newEngineOnly','total','modelled','engineOnly')},indent=2))
