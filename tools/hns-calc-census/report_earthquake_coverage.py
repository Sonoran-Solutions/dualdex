#!/usr/bin/env python3
"""Slice 3 unchanged-population comparison against immutable post-#125 main."""
import argparse
import gzip
import json
import subprocess
from report_move_coverage import ROOT, CENSUS, report
START = "3703d94fd94c06b63656aa5a46f193b6273a0df1"

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check',action='store_true')
    args=p.parse_args()
    before=json.loads(gzip.decompress(subprocess.check_output(['git','show',f'{START}:{CENSUS}'],cwd=ROOT)))
    after=json.loads(gzip.decompress((ROOT/CENSUS).read_bytes()))
    inventory=json.loads((ROOT/'tools/hns-calc-census/trainer_inventory.json').read_text())
    old_inventory=json.loads(subprocess.check_output(['git','show',f'{START}:tools/hns-calc-census/trainer_inventory.json'],cwd=ROOT))
    assert inventory==old_inventory, 'Trainer inventory changed'
    metadata=json.loads((ROOT/'tools/hns-move-mechanics/hns_move_damage_metadata.json').read_text())
    out=report(before,after,inventory,metadata,'EFFECT_EARTHQUAKE',START)
    assert before['leadMatchup']['definition'] == after['leadMatchup']['definition']
    out['remainingFamilyRanking'] = report(after,after,inventory,metadata,'EFFECT_EARTHQUAKE',START)['ranking']
    assert not out['outsideSliceChanges'], 'Outside Earthquake family transitions require investigation'
    out['transitionCounts']={}
    for row in out['transitions']:
        key=row['oldTier']+' -> '+row['newTier']
        out['transitionCounts'][key]=out['transitionCounts'].get(key,0)+1
    out['earthquakeRequestCount']=sum(r['move'] in {'Earthquake','Bulldoze'} for r in after['requests'])
    text=json.dumps(out,indent=2,sort_keys=True)+'\n'
    path=ROOT/'tools/hns-calc-census/move-coverage-slice-3.json'
    if args.check: assert path.read_text()==text, 'Earthquake report stale'
    else: path.write_text(text)
    print(json.dumps({k:out[k] for k in ('before','after','beforeLead','afterLead','transitionCounts','battlesGainingRequests','earthquakeRequestCount')},indent=2))

if __name__=='__main__':main()
