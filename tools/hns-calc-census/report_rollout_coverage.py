#!/usr/bin/env python3
"""Slice 10 starting-head population and exact request-local transitions."""
import argparse,gzip,json,subprocess,collections
from report_move_coverage import ROOT,CENSUS,report
START='f6c300e508a33e3d58c8c0d4c423febec1cbe429'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');a=p.parse_args()
before=json.loads(gzip.decompress(subprocess.check_output(['git','show',f'{START}:{CENSUS}'],cwd=ROOT)))
after=json.loads(gzip.decompress((ROOT/CENSUS).read_bytes()))
inventory=json.loads((ROOT/'tools/hns-calc-census/trainer_inventory.json').read_text())
assert inventory==json.loads(subprocess.check_output(['git','show',f'{START}:tools/hns-calc-census/trainer_inventory.json'],cwd=ROOT))
metadata=json.loads((ROOT/'tools/hns-move-mechanics/hns_move_damage_metadata.json').read_text())
out=report(before,after,inventory,metadata,'EFFECT_ROLLOUT',START)
assert len(before['requests'])==len(after['requests'])==24278
assert out['beforeLead']['battlesTotal']==out['afterLead']['battlesTotal']==651
assert before['leadMatchup']['definition']==after['leadMatchup']['definition']
assert not out['outsideSliceChanges'],'Unexplained outside-family changes'
for move in ('Gyro Ball','Electro Ball'):
    assert [r for r in before['requests'] if r['move']==move]==[r for r in after['requests'] if r['move']==move]
out['historicalSpeedRequestsUnchanged']=True
out['moveRequestCounts']={move:sum(r['move']==move for r in after['requests']) for move in ('Rollout','Ice Ball')}
out['combinedRequestCount']=sum(out['moveRequestCounts'].values())
out['transitionCounts']=dict(sorted(collections.Counter(t['oldTier']+' -> '+t['newTier'] for t in out['transitions']).items()))
out['remainingFamilyRanking']=report(after,after,inventory,metadata,'EFFECT_ROLLOUT',START)['ranking']
out['neutralChainFixture']='Explicit first-use state after neutral Celebrate: timer 0, defenseCurl false, multipleTurns false, lock 0, recharge 0. Source witness execution-rollout-chain; no production defaults.'
text=json.dumps(out,indent=2,sort_keys=True)+'\n';path=ROOT/'tools/hns-calc-census/move-coverage-slice-10.json'
if a.check: assert path.read_text()==text,'Rollout census evidence stale'
else:path.write_text(text)
print(json.dumps({k:out[k] for k in ('before','after','beforeLead','afterLead','transitionCounts','battlesGainingRequests','moveRequestCounts')},indent=2))
