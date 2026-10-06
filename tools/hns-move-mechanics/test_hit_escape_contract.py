#!/usr/bin/env python3
"""Frozen hit-escape descriptor mutation checks."""
import argparse
import shutil
import tempfile
from pathlib import Path
import generate_hns_move_effects as gen

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--upstream-dir',required=True,type=Path)
up=p.parse_args().upstream_dir
moves=(up/'src/data/moves_info.h').read_text()
ids={'MOVE_U_TURN':369,'MOVE_VOLT_SWITCH':521,'MOVE_FLIP_TURN':740}
gen.parse_hit_escape_metadata(moves,ids); gen.verify_hit_escape_contract(str(up))
count=0
def refused(call):
    global count
    try: call()
    except ValueError: count+=1
    else: raise AssertionError('source mutation accepted')
for symbol in ids:
    start=moves.index('['+symbol+']')
    for old,new in (('EFFECT_HIT_ESCAPE','EFFECT_HIT'),('.power = ','.power = 1 + '),('.accuracy = 100','.accuracy = 90'),
        ('TARGET_SELECTED','TARGET_USER'),('.category = ','.category = INVALID_'),
        ('.target = ','.makesContact = FALSE, .target = '),('.priority = 0','.priority = 1'),
        ('.pp = 20','.pp = 10'),('.power = ','.punchingMove = TRUE, .power = '),
        ('.power = ','.additionalEffects = ADDITIONAL_EFFECTS({.preAttackEffect = TRUE}), .power = ')):
        assert old in moves[start:]
        changed=moves[:start]+moves[start:].replace(old,new,1)
        refused(lambda:gen.parse_hit_escape_metadata(changed,ids))
    refused(lambda:gen.parse_hit_escape_metadata(moves,{**ids,symbol:ids[symbol]+1}))

with tempfile.TemporaryDirectory() as temp:
    root=Path(temp)
    for path in gen.ROLLOUT_SOURCE_CONTRACTS:
        (root/path).parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(up/path,root/path)
    for path,old,new in (
        ('src/battle_util.c','basePower <= 60','basePower < 60'),
        ('src/battle_util.c','ABILITY_LONG_REACH','ABILITY_INSOMNIA'),
        ('src/battle_move_resolution.c','IsBattlerTurnDamaged(gBattlerTarget, INCLUDING_SUBSTITUTES)','TRUE'),
        ('data/battle_scripts_1.s','datahpupdate BS_TARGET, MOVE_DAMAGE_HP_UPDATE','pause 1'),
        ('data/battle_scripts_1.s','SWITCH_IGNORE_ESCAPE_PREVENTION | BS_ATTACKER','BS_ATTACKER'),
        ('src/data/battle_move_effects.h','[EFFECT_HIT_ESCAPE]','[EFFECT_HIT]')):
        original=(up/path).read_text();assert old in original
        (root/path).write_text(original.replace(old,new,1))
        refused(lambda:gen.verify_hit_escape_contract(str(root)))
        (root/path).write_text(original)
print(f'Hit-escape source contract: {count} mutations refused')
