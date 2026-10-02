#!/usr/bin/env python3
"""ROM-free checks of retained official-ROM Group D transition logs.

These checks validate the recorded observations; they do not run an emulator or
claim a new runtime measurement. Reproduce with the documented input scenarios.
"""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = Path(__file__).with_name('evidence') / 'group-d'
ITEM_EVIDENCE = Path(__file__).with_name('evidence') / 'group-d-items'
ROM_SHA256 = 'edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b'
UPSTREAM_COMMIT = '1f42b74dff0e9fe942419845d040663dd829a973'


def check_evidence():
    provenance = json.loads((EVIDENCE / 'provenance.json').read_text())
    if provenance['romSha256'] != ROM_SHA256:
        raise ValueError('provenance ROM identity differs')
    cases = {}
    for name in ['slow','flash','paradox','supreme','gorilla']:
        text = (EVIDENCE / (name + '.log')).read_text()
        if hashlib.sha256(text.encode()).hexdigest() != provenance['cases'][name]['logSha256']:
            raise ValueError(f'{name}: retained log differs from provenance')
        for required in ['runtime invariant violations  : 0','script errors                 : 0',
                         'result                        : PASS']:
            if required not in text: raise ValueError(f'{name}: missing successful probe summary')
        rows = [dict(re.findall(r'(\w+)=([^ ]+)', line))
                for line in text.splitlines() if line.startswith('[GROUP-D]')]
        if not rows or any(row['rom'] != ROM_SHA256 for row in rows):
            raise ValueError(f'{name}: wrong or absent release identity')
        cases[name] = rows
    proofs = {}

    def witness(case, label, **conditions):
        for row in cases[case]:
            if row['status'] == '2' and all(row.get(key) == str(value) for key, value in conditions.items()):
                proofs[label] = {'case':case,'frame':int(row['frame']),**conditions}
                return row
        raise ValueError(f'{case}: no observed witness for {label}: {conditions}')

    for timer in range(6): witness('slow',f'slowTimer{timer}',role=0,ability=112,slow=timer,volatiles=1)
    for raw in range(3): witness('slow',f'firstTurn{raw}',role=0,first=f'1/{raw}')
    witness('flash','flashNeutral',ability=18,flash=0,volatiles=1)
    witness('flash','flashActivated',ability=18,flash=1,volatiles=1)
    witness('paradox','boosterHeld',species=1384,item='1/764',booster=0)
    witness('paradox','boosterConsumed',species=1384,item='1/0',booster=1,selector=2)
    witness('paradox','replacementClearsParadox',species=1397,booster=0,selector=0)
    witness('paradox','ruinNeutral',species=1397,ruin='0,0,0,0')
    witness('paradox','ruinActivated',species=1397,ruin='0,0,0,1')
    witness('supreme','storedCounterZero',species=1375,supreme='1/0')
    witness('supreme','storedCounterOne',species=1375,supreme='1/1')
    witness('gorilla','selectedAndActiveNeutral',ability=255,selected='1/0',active='1/0')
    witness('slow','analyticLast',analytic='1/1')
    witness('slow','analyticNotLast',analytic='1/2')
    witness('slow','analyticOrdinaryMove',move=33,analytic='1/2')
    witness('slow','analyticMenuUnknown',callback='08088DED',analytic='0/0')
    all_rows = [row for rows in cases.values() for row in rows]
    positives = [row for row in all_rows if row['analytic'].startswith('1/')]
    for row in positives:
        if not (row['callback']=='0808AA35' and row['main1']=='080822F1' and row['action']=='10'
                and row['attacker']==row['battler'] and int(row['move']) > 0):
            raise ValueError(f'Analytic accepted an invalid phase: {row}')
    if any(row['analytic']!='0/0' for row in all_rows if row['callback']=='08088DED'):
        raise ValueError('menu accepted stale turn order')
    # At least one positive action is followed by an UNKNOWN menu and a new positive action.
    slow = cases['slow']
    if not any(any(r['analytic'].startswith('1/') for r in slow[:i]) and
               any(r['analytic'].startswith('1/') for r in slow[i+1:])
               for i,row in enumerate(slow) if row['callback']=='08088DED'):
        raise ValueError('no action -> new-turn menu -> new action transition')
    proofs.update(check_item_evidence())
    return proofs


def check_item_evidence():
    """Verify retained issue #92 official-ROM volatile transitions, without running an emulator."""
    provenance = json.loads((ITEM_EVIDENCE / 'provenance.json').read_text())
    if provenance.get('romSha256') != ROM_SHA256:
        raise ValueError('item evidence provenance ROM identity differs')
    if provenance.get('upstreamCommit') != UPSTREAM_COMMIT:
        raise ValueError('item evidence provenance upstream commit differs')

    cases = {}
    for name in ['embargo', 'metronome', 'transform']:
        meta = provenance['cases'][name]
        log_path = ITEM_EVIDENCE / meta['log']
        scenario_path = ROOT / meta['scenario']
        text = log_path.read_text()
        if hashlib.sha256(text.encode()).hexdigest() != meta['logSha256']:
            raise ValueError(f'{name}: retained item log differs from provenance')
        if hashlib.sha256(scenario_path.read_bytes()).hexdigest() != meta['scenarioSha256']:
            raise ValueError(f'{name}: scenario differs from provenance')
        for required in ['runtime invariant violations  : 0', 'script errors                 : 0',
                         'result                        : PASS']:
            if required not in text:
                raise ValueError(f'{name}: missing successful probe summary')
        rows = [dict(re.findall(r'(\w+)=([^ ]+)', line))
                for line in text.splitlines() if line.startswith('[GROUP-D]')]
        if not rows or any(row.get('rom') != ROM_SHA256 for row in rows):
            raise ValueError(f'{name}: wrong or absent official release identity')
        if any('itemVolatiles' not in row or 'embargo' not in row or 'metronome' not in row
               or 'transformedMonSpecies' not in row for row in rows):
            raise ValueError(f'{name}: trace omits an issue #92 volatile operand')
        cases[name] = rows

    proofs = {}

    def witness(case, label, after=-1, **conditions):
        for row in cases[case]:
            if int(row['frame']) > after and row['status'] == '2' and row['itemVolatiles'] == '1' \
                    and all(row.get(key) == str(value) for key, value in conditions.items()):
                proofs[label] = {'case':case, 'frame':int(row['frame']), **conditions}
                return int(row['frame'])
        raise ValueError(f'{case}: no observed item-state witness for {label}: {conditions}')

    clear = witness('embargo', 'embargoClearBeforeUse', role=1, embargo=0)
    active = witness('embargo', 'embargoAppliedToTarget', after=clear, role=1, embargo=1)
    witness('embargo', 'embargoExpiresAfterFiveTurns', after=active, role=1, embargo=0)

    initial = witness('metronome', 'metronomeItemAndZeroCounter', role=0, item='1/483',
                      embargo=0, transformed=0, metronome=0)
    count_one = witness('metronome', 'metronomeRepeatedMoveCounterOne', after=initial,
                        role=0, item='1/483', move=45, metronome=1)
    count_two = witness('metronome', 'metronomeRepeatedMoveCounterTwo', after=count_one,
                        role=0, item='1/483', move=45, metronome=2)
    reset = witness('metronome', 'metronomeDifferentMoveResetsCounter', after=count_two,
                    role=0, item='1/483', move=39, metronome=0)
    witness('metronome', 'metronomeRepeatedMoveCountsAgain', after=reset,
            role=0, item='1/483', move=45, metronome=1)

    ditto = witness('transform', 'dittoUntransformed', role=0, species=132, transformed=0,
                    transformedMonSpecies=0)
    witness('transform', 'dittoTransformsAndRetainsSourceSpecies', after=ditto,
            role=0, species=161, transformed=1, transformedMonSpecies=132)
    return proofs


if __name__=='__main__':
    print(json.dumps(check_evidence(),indent=2,sort_keys=True))
