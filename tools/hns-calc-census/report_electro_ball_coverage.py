#!/usr/bin/env python3
"""Slice 9 unchanged-population comparison against immutable post-#138 main."""
import argparse
import gzip
import json
import subprocess
from report_move_coverage import ROOT, CENSUS, report
START = "9ab694b99915f13956f0cf652c792bdc92d10409"

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
    out=report(before,after,inventory,metadata,'EFFECT_ELECTRO_BALL',START)
    assert before['leadMatchup']['definition'] == after['leadMatchup']['definition']
    out['remainingFamilyRanking'] = report(after,after,inventory,metadata,'EFFECT_ELECTRO_BALL',START)['ranking']
    assert not out['outsideSliceChanges'], 'Outside Electro Ball family transitions require investigation'
    assert len(before['requests']) == len(after['requests']) == 24278
    assert out['beforeLead']['battlesTotal'] == out['afterLead']['battlesTotal'] == 651
    out['transitionCounts']={'REFUSED -> FULLY_MODELLED':0, 'REFUSED -> CAVEATED_ESTIMATE':0, 'REFUSED -> REFUSED':0}
    for row in out['transitions']:
        key=row['oldTier']+' -> '+row['newTier']
        out['transitionCounts'][key]=out['transitionCounts'].get(key,0)+1
    assert [r for r in before['requests'] if r['move']=='Gyro Ball'] == [r for r in after['requests'] if r['move']=='Gyro Ball'], 'Gyro census changed'
    out['gyroRequestsUnchanged'] = True
    out['electroBallRequestCount']=sum(r['move'] in {'Electro Ball'} for r in after['requests'])
    text=json.dumps(out,indent=2,sort_keys=True)+'\n'
    path=ROOT/'tools/hns-calc-census/move-coverage-slice-9.json'
    if args.check: assert path.read_text()==text, 'Electro Ball report stale'
    else: path.write_text(text)
    print(json.dumps({k:out[k] for k in ('before','after','beforeLead','afterLead','transitionCounts','battlesGainingRequests','electroBallRequestCount')},indent=2))

if __name__=='__main__':main()
