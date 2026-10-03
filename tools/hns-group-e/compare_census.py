#!/usr/bin/env python3
"""Reproduce Group E before/after evidence from immutable starting main and current census."""
import collections
import gzip
import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
START = '929f075bd25a99756ea0d2fe1158e69e8386424e'
PATH = 'tools/hns-calc-census/census.json.gz'


def summary(doc):
    trials = doc['abilityTrials']
    ids = collections.defaultdict(lambda: [0, 0, 0])
    for t in trials:
        for i, key in enumerate(('refusedRequests', 'caveatedRequests', 'clearRequests')):
            ids[t['abilityId']][i] += t[key]
    battles = collections.defaultdict(list)
    causes = collections.Counter()
    for r in doc['requests']:
        battles[r['trainer']].append(r)
        if r['tier'] == 'REFUSED': causes.update(r['causes'])
    no_result = [{'trainer': k, 'requests': len(v), 'format': v[0]['gameType'],
                  'reasons': sorted({c for r in v for c in r['causes']})}
                 for k, v in sorted(battles.items()) if all(r['tier'] == 'REFUSED' for r in v)]
    return {'tiers': doc['resultTiers'], 'noResultBattles': no_result,
            'leadMatchup': doc['leadMatchup'], 'refusedCauses': dict(causes.most_common()),
            'randomAbilities': {'refused': sum(x[0] for x in ids.values()),
                                'caveated': sum(x[1] for x in ids.values()),
                                'clear': sum(x[2] for x in ids.values()),
                                'identitiesAnyRefusal': sum(x[0] > 0 for x in ids.values()),
                                'identitiesCaveated': sum(x[1] > 0 for x in ids.values()),
                                'identitiesClearOnly': sum(x[0] == 0 and x[1] == 0 for x in ids.values())}}


def main():
    before = json.loads(gzip.decompress(subprocess.check_output(['git', 'show', START + ':' + PATH], cwd=ROOT)))
    after = json.loads(gzip.decompress((ROOT / PATH).read_bytes()))
    assert before['counts']['trainerBattles'] == after['counts']['trainerBattles'] == 651
    assert [r['key'] for r in before['requests']] == [r['key'] for r in after['requests']]
    result = {'startingMain': START, 'before': summary(before), 'after': summary(after)}
    target = ROOT / 'tools/hns-group-e/census-comparison.json'
    target.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: {key: value for key, value in v.items() if key in ('tiers','randomAbilities')}
                      for k,v in result.items() if k != 'startingMain'}, indent=2))
    print('No-result battles:', len(result['before']['noResultBattles']), '->', len(result['after']['noResultBattles']))


if __name__ == '__main__':
    main()
