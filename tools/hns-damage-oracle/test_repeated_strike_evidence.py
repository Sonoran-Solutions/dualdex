#!/usr/bin/env python3
"""Small runnable rejection check for the additive engine trace contract."""
import contextlib
import copy
import io
import json
import repeated_strike_evidence as evidence

original=json.loads(evidence.TARGET.read_text())
rows=evidence.scenarios(); generated=evidence.source(rows)
with contextlib.redirect_stdout(io.StringIO()): evidence.check(original,rows,generated)

def event(doc,kind):
    return next(e['values'] for e in doc['cases'][0]['events'] if e['kind']==kind)

mutations=[
    lambda d:d.update(pinnedCommit='wrong'),
    lambda d:d.update(sourceSha256='wrong'),
    lambda d:d['toolchain'].update(gcc='wrong'),
    lambda d:event(d,'RNG').__setitem__(3,-1),
    lambda d:d.update(historicalCorpusSha256='wrong'),
    lambda d:d['cases'].pop(),
    lambda d:d['cases'][0].update(pass_=False, **{'pass':False}),
    lambda d:next(e['values'] for e in d['cases'][0]['events'] if e['kind']=='RNG' and e['values'][0]==7).__setitem__(1,15),
    lambda d:event(d,'HP').__setitem__(3,999),
    lambda d:event(d,'OPERANDS').__setitem__(5,1),
    lambda d:event(d,'PRE').__setitem__(1,5),
    lambda d:d['cases'][0]['events'].reverse(),
]
for mutate in mutations:
    changed=copy.deepcopy(original); mutate(changed)
    try:
        with contextlib.redirect_stdout(io.StringIO()): evidence.check(changed,rows,generated)
    except (AssertionError,StopIteration): pass
    else: raise AssertionError('mutated engine evidence accepted')
print(f'Repeated-strike trace contract: {len(mutations)} mutations refused')
