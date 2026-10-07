#!/usr/bin/env python3
"""Slice 12 starting-head population and exact request-local transitions."""
import argparse,gzip,json,subprocess,collections
from report_move_coverage import ROOT,CENSUS,report

START='32c23e858cd9cf38867df668166f18d9167dc495'
SLICE12='2d57cf8b991b04e676fd6d920ddcdf52159f1735'
SLICE13='c91f024ad50f428fdd99b537e2caa6dab3abd431'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');a=p.parse_args()
before=json.loads(gzip.decompress(subprocess.check_output(['git','show',f'{START}:{CENSUS}'],cwd=ROOT)))
current=json.loads(gzip.decompress(subprocess.check_output(['git','show',f'{SLICE13}:{CENSUS}'],cwd=ROOT)))
after=json.loads(gzip.decompress(subprocess.check_output(['git','show',f'{SLICE12}:{CENSUS}'],cwd=ROOT)))
inventory=json.loads((ROOT/'tools/hns-calc-census/trainer_inventory.json').read_text())
assert inventory==json.loads(subprocess.check_output(['git','show',f'{START}:tools/hns-calc-census/trainer_inventory.json'],cwd=ROOT))
assert inventory==json.loads(subprocess.check_output(['git','show',f'{SLICE12}:tools/hns-calc-census/trainer_inventory.json'],cwd=ROOT))
metadata=json.loads((ROOT/'tools/hns-move-mechanics/hns_move_damage_metadata.json').read_text())
moves=('Bonemerang','Double Hit','Double Kick','Dual Chop','Dual Wingbeat','Twin Beam')
slice13_moves={'Arm Thrust','Bone Rush','Bullet Seed','Comet Punch','Double Slap','Fury Attack',
               'Fury Swipes','Icicle Spear','Pin Missile','Rock Blast','Spike Cannon','Tail Slap'}
assert len(before['requests'])==len(after['requests'])==len(current['requests'])==24278
out=report(before,after,inventory,metadata,'EFFECT_HIT',START,selected_moves=moves)
assert out['beforeLead']['battlesTotal']==out['afterLead']['battlesTotal']==651
assert before['leadMatchup']['definition']==after['leadMatchup']['definition']
assert not out['outsideSliceChanges'],'Unexplained outside-family changes from the fixed-two baseline'

# Keep fixed-two request rows and residual reasons pinned to immutable Slice 12. Record the
# current Slice-13 movement as a separate additive audit instead of folding it into Slice 12.
slice13=report(after,current,inventory,metadata,'EFFECT_HIT',SLICE12,selected_moves=slice13_moves)
assert not slice13['outsideSliceChanges'],'Unexplained Slice-13 changes outside its exact family'
authorized_slice13=slice13['transitions']
assert len(authorized_slice13)==284
assert all(t['oldTier']=='REFUSED' for t in authorized_slice13)
out['authorizedSlice13Changes']=authorized_slice13
out['authorizedSlice13TransitionCounts']=dict(sorted(collections.Counter(
    t['oldTier']+' -> '+t['newTier'] for t in authorized_slice13).items()))
out['authorizedSlice13MoveRequestCount']=len(authorized_slice13)
out['postSlice13ResultTiers']=current['resultTiers']
out['postSlice13LeadMatchup']=current['leadMatchup']
for move in ('Gyro Ball','Electro Ball'):
    assert [r for r in after['requests'] if r['move']==move]==[r for r in current['requests'] if r['move']==move]
for move in moves:
    assert [r for r in after['requests'] if r['move']==move]==[r for r in current['requests'] if r['move']==move]
out['fixedTwoRequestRowsUnchanged']=True
out['slice13OutsideChangesCount']=len(slice13['outsideSliceChanges'])
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
out['remainingFamilyRanking']=report(after,after,inventory,metadata,'EFFECT_HIT',SLICE12,selected_moves=moves)['ranking']
out['remainingSelectedReasons']=dict(sorted(collections.Counter(reason for r in out['selectedStillRefused'] for reason in r['reasons']).items()))
assert out['transitionCounts'].get('REFUSED -> CAVEATED_ESTIMATE',0)==0
assert all(t['oldTier']=='REFUSED' for t in out['transitions'])
text=json.dumps(out,indent=2,sort_keys=True)+'\n';path=ROOT/'tools/hns-calc-census/move-coverage-slice-12.json'
if a.check: assert path.read_text()==text,'Fixed-two census evidence stale'
else:path.write_text(text)
print(json.dumps({k:out[k] for k in ('before','after','beforeLead','afterLead','transitionCounts','battlesGainingRequests','moveRequestCounts','authorizedSlice13TransitionCounts')},indent=2))
