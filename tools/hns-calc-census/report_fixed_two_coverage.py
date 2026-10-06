#!/usr/bin/env python3
"""Slice 12 starting-head population and exact request-local transitions."""
import argparse,gzip,json,subprocess,collections
from report_move_coverage import ROOT,CENSUS,report
START='32c23e858cd9cf38867df668166f18d9167dc495'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');a=p.parse_args()
before=json.loads(gzip.decompress(subprocess.check_output(['git','show',f'{START}:{CENSUS}'],cwd=ROOT)))
after=json.loads(gzip.decompress((ROOT/CENSUS).read_bytes()))
inventory=json.loads((ROOT/'tools/hns-calc-census/trainer_inventory.json').read_text())
assert inventory==json.loads(subprocess.check_output(['git','show',f'{START}:tools/hns-calc-census/trainer_inventory.json'],cwd=ROOT))
metadata=json.loads((ROOT/'tools/hns-move-mechanics/hns_move_damage_metadata.json').read_text())
moves=('Bonemerang','Double Hit','Double Kick','Dual Chop','Dual Wingbeat','Twin Beam')
out=report(before,after,inventory,metadata,'EFFECT_HIT',START,selected_moves=moves)
assert len(before['requests'])==len(after['requests'])==24278
assert out['beforeLead']['battlesTotal']==out['afterLead']['battlesTotal']==651
assert before['leadMatchup']['definition']==after['leadMatchup']['definition']
assert not out['outsideSliceChanges'],'Unexplained outside-family changes'
for move in ('Gyro Ball','Electro Ball'):
    assert [r for r in before['requests'] if r['move']==move]==[r for r in after['requests'] if r['move']==move]
out['historicalSpeedRequestsUnchanged']=True
historical_path='tools/hns-damage-oracle/corpus.json'
historical_before=subprocess.check_output(['git','show',f'{START}:{historical_path}'],cwd=ROOT)
historical_after=(ROOT/historical_path).read_bytes()
assert historical_before==historical_after
assert json.loads(historical_before)==json.loads(historical_after)
assert historical_before.splitlines()==historical_after.splitlines()
out['historicalCorpusBytesObjectsLinesUnchanged']=True
out['moveRequestCounts']={move:sum(r['move']==move for r in after['requests']) for move in moves}
out['combinedRequestCount']=sum(out['moveRequestCounts'].values())
out['transitionCounts']=dict(sorted(collections.Counter(t['oldTier']+' -> '+t['newTier'] for t in out['transitions']).items()))
out['remainingFamilyRanking']=report(after,after,inventory,metadata,'EFFECT_HIT',START,selected_moves=moves)['ranking']
out['remainingSelectedReasons']=dict(sorted(collections.Counter(reason for r in out['selectedStillRefused'] for reason in r['reasons']).items()))
assert out['transitionCounts'].get('REFUSED -> CAVEATED_ESTIMATE',0)==0
assert all(t['oldTier']=='REFUSED' for t in out['transitions'])
text=json.dumps(out,indent=2,sort_keys=True)+'\n';path=ROOT/'tools/hns-calc-census/move-coverage-slice-12.json'
if a.check: assert path.read_text()==text,'Fixed-two census evidence stale'
else:path.write_text(text)
print(json.dumps({k:out[k] for k in ('before','after','beforeLead','afterLead','transitionCounts','battlesGainingRequests','moveRequestCounts')},indent=2))
