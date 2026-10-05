#!/usr/bin/env python3
"""Slice 6 unchanged-population comparison against immutable post-#131 main."""
import argparse
import gzip
import json
import subprocess
from report_move_coverage import ROOT, CENSUS, report
START = "edc26cc476cabd0fe2ba52ac892741a1883ac59d"

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
    out=report(before,after,inventory,metadata,'EFFECT_DOUBLE_POWER_ON_ARG_STATUS',START)
    assert before['leadMatchup']['definition'] == after['leadMatchup']['definition']
    out['remainingFamilyRanking'] = report(after,after,inventory,metadata,'EFFECT_DOUBLE_POWER_ON_ARG_STATUS',START)['ranking']
    assert not out['outsideSliceChanges'], 'Outside status-double family transitions require investigation'
    out['transitionCounts']={}
    for row in out['transitions']:
        key=row['oldTier']+' -> '+row['newTier']
        out['transitionCounts'][key]=out['transitionCounts'].get(key,0)+1
    out['statusDoubleRequestCount']=sum(r['move'] in {'Smelling Salts','Wake-Up Slap','Venoshock','Hex','Barb Barrage','Infernal Parade'} for r in after['requests'])
    text=json.dumps(out,indent=2,sort_keys=True)+'\n'
    path=ROOT/'tools/hns-calc-census/move-coverage-slice-6.json'
    if args.check: assert path.read_text()==text, 'Status-double report stale'
    else: path.write_text(text)
    print(json.dumps({k:out[k] for k in ('before','after','beforeLead','afterLead','transitionCounts','battlesGainingRequests','statusDoubleRequestCount')},indent=2))

if __name__=='__main__':main()
