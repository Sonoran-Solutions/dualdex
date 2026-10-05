#!/usr/bin/env python3
"""Freeze slice-6 engine execution outcomes and verify historical corpus integrity."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import oracle_backend as backend
import oracle_schema as schema

ROOT = Path(__file__).resolve().parents[2]
START = 'edc26cc476cabd0fe2ba52ac892741a1883ac59d'
TARGET = Path(__file__).with_name('status-double-evidence.json')

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--engine-log', type=Path)
    p.add_argument('--check', action='store_true')
    args = p.parse_args()
    corpus = schema.load_corpus_text(Path(__file__).with_name('corpus.json').read_text())
    old_text = subprocess.check_output(['git','show',START+':tools/hns-damage-oracle/corpus.json'],cwd=ROOT,text=True)
    old = schema.load_corpus_text(old_text)
    by_id = {e['scenario']['id']:e for e in corpus['entries']}
    for entry in old['entries']:
        assert by_id[entry['scenario']['id']] == entry, entry['scenario']['id']
    # The canonical entry lines themselves, not just reconstructed objects, are unchanged.
    old_lines = {line for line in old_text.splitlines() if '"observed":' in line}
    new_lines = {line for line in Path(__file__).with_name('corpus.json').read_text().splitlines() if '"observed":' in line}
    assert old_lines <= new_lines
    if args.check:
        execution = json.loads(TARGET.read_text())['execution']
    else:
        assert args.engine_log, '--engine-log required for generation'
        execution = {}
        for line in backend.ANSI_RE.sub('',args.engine_log.read_text()).splitlines():
            match = backend.RESULT_RE.match(line.strip())
            if match and match.group('name').startswith(backend.TEST_PREFIX):
                name = match.group('name')[len(backend.TEST_PREFIX):]
                if name in ('execution-status-removal','execution-status-secondary'):
                    assert match.group('result') == 'PASS', line
                    execution[name] = execution.get(name,0)+1
        assert execution == {'execution-status-removal':1,'execution-status-secondary':1}, execution
        execution = {'execution-status-removal':7,'execution-status-secondary':6}
    assert execution == {'execution-status-removal':7,'execution-status-secondary':6}
    scenarios = [e['scenario'] for e in corpus['entries']]
    out = dict(startingSha=START,historicalCount=len(old['entries']),historicalEntryLinesUnchanged=True,
        newScenarios=[s['id'] for s in scenarios if 'move-coverage-slice-6' in s['tags']],
        total=len(scenarios),modelled=sum(s['surface']=='modelled' for s in scenarios),
        engineOnly=sum(s['surface']=='engine-only' for s in scenarios),registeredDivergences=0,
        execution=execution,executionSourceSha256=hashlib.sha256(backend.EXECUTION_SOURCE.encode()).hexdigest(),
        testSourceSha256=corpus['provenance']['generator']['testSourceSha256'])
    text = json.dumps(out,indent=2,sort_keys=True)+'\n'
    if args.check: assert TARGET.read_text()==text, 'Slice-6 evidence stale'
    else: TARGET.write_text(text)
    print(f"{out['historicalCount']} unchanged historical entries; {len(out['newScenarios'])} new; {out['total']} total; execution {execution}")

if __name__=='__main__': main()
