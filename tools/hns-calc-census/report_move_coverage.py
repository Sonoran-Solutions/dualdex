#!/usr/bin/env python3
"""Rank move blockers and compare real production census verdicts; no policy simulation.

Potential counts are ONLY an upper bound obtained by removing the move gate from
already-recorded reasons. Ability/item proofs can change with admission. Actual
transitions are exclusively measured from the regenerated production census.
"""
import argparse
import collections
import gzip
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
START = '3821066e898652509b826526bb8418e8c42adbe2'
CENSUS = 'tools/hns-calc-census/census.json.gz'


def report(before, after, inventory, metadata):
    ids = {m['name']: m['id'] for m in inventory['moves']}
    families = collections.defaultdict(list)
    for r in before['requests']:
        if 'HNS_MOVE_MECHANICS_NOT_MODELLED' in r['limitations']:
            md = metadata['moves'].get(str(ids[r['move']]), {})
            effect = md.get('effect', 'UNKNOWN')
            if effect == 'EFFECT_HIT':
                flags = md.get('damageFlags', {})
                shape = 'repeated strikes' if 'multiHit' in flags or 'strikeCount' in flags else next(iter(flags), 'unresolved shape')
                effect += ' / ' + shape
            families[effect].append(r)
    ranking = []
    for effect, rows in sorted(families.items(), key=lambda x: (-len(x[1]), x[0])):
        overlaps = collections.Counter(reason for r in rows for reason in r['limitations']
                                       if reason != 'HNS_MOVE_MECHANICS_NOT_MODELLED')
        ranking.append(dict(effect=effect, moves=[dict(id=ids[n], name=n, sourceMetadata=metadata["moves"].get(str(ids[n]), {})) for n in sorted({r['move'] for r in rows})],
            battles=len({r['trainer'] for r in rows}), blockedRequests=len(rows),
            leadRequests=sum(r['partySlot'] == 0 for r in rows),
            leadPairs=len({(r['trainer'], r['referenceTeam']) for r in rows if r['partySlot'] == 0}),
            potentialUpperBound=sum(r['limitations'] == ['HNS_MOVE_MECHANICS_NOT_MODELLED'] for r in rows),
            overlappingBlockers=dict(sorted(overlaps.items()))))
    old = {r['key']: r for r in before['requests']}
    new = {r['key']: r for r in after['requests']}
    assert old.keys() == new.keys(), 'Census population changed'
    for key in old:
        for field in ('trainer', 'partySlot', 'referenceTeam', 'direction', 'move', 'gameType'):
            assert old[key][field] == new[key][field], (key, field)
    assert before['referenceTeams'] == after['referenceTeams']
    changes = [dict(key=k, oldTier=old[k]['tier'], newTier=new[k]['tier'],
                    oldReasons=old[k]['limitations'], newReasons=new[k]['limitations'],
                    oldCauses=old[k]['causes'], newCauses=new[k]['causes']) for k in sorted(old)
               if (old[k]['tier'], old[k]['limitations'], old[k]['causes']) !=
                  (new[k]['tier'], new[k]['limitations'], new[k]['causes'])]
    gaining = {old[r['key']]['trainer'] for r in changes if r['oldTier']=='REFUSED' and r['newTier']!='REFUSED'}
    selected = {m['name'] for f in ranking if f['effect']=='EFFECT_RECOIL' for m in f['moves']}
    still = [dict(key=r['key'], reasons=r['limitations'], causes=r['causes']) for r in after['requests']
             if r['move'] in selected and r['tier']=='REFUSED']
    def pairs(c):
        d=collections.defaultdict(list)
        for r in c['requests']:
            if r['partySlot']==0:d[(r['trainer'],r['referenceTeam'])].append(r)
        return {k for k,rs in d.items() if all(r['tier']!='REFUSED' for r in rs)}
    def blank_battles(c):
        displaying = {r['trainer'] for r in c['requests'] if r['tier'] != 'REFUSED'}
        return len({r['trainer'] for r in c['requests']} - displaying)
    return dict(blankBattlesBefore=blank_battles(before), blankBattlesAfter=blank_battles(after), startingSha=START, potentialCounts='Unproven upper bounds; NOT production authorization',
                ranking=ranking, before=before['resultTiers'], after=after['resultTiers'],
                beforeLead=before['leadMatchup'], afterLead=after['leadMatchup'],
                newlyDisplayableLeadPairs=[list(k) for k in sorted(pairs(after)-pairs(before))],
                battlesGainingRequests=len(gaining), transitions=changes, selectedStillRefused=still,
                outsideSliceChanges=[r for r in changes if old[r['key']]['move'] not in selected])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, default=ROOT/'tools/hns-calc-census/move-coverage-slice-1.json')
    p.add_argument('--check',action='store_true')
    a=p.parse_args()
    before=json.loads(gzip.decompress(subprocess.check_output(['git','show',f'{START}:{CENSUS}'],cwd=ROOT)))
    after=json.loads(gzip.decompress((ROOT/CENSUS).read_bytes()))
    out=report(before,after,json.loads((ROOT/'tools/hns-calc-census/trainer_inventory.json').read_text()),
               json.loads((ROOT/'tools/hns-move-mechanics/hns_move_damage_metadata.json').read_text()))
    text=json.dumps(out,indent=2,sort_keys=True)+'\n'
    if a.check:
        assert a.output.read_text()==text, 'Move coverage report stale'
    else:a.output.write_text(text)
    print(json.dumps({k:out[k] for k in ('before','after','battlesGainingRequests')},indent=2))
    print('New lead pairs:',len(out['newlyDisplayableLeadPairs']))

if __name__=='__main__':main()
