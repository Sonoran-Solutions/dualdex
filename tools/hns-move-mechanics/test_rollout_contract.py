#!/usr/bin/env python3
"""Rollout MoveInfo, lifecycle/phase/layout and modifier-order drift checks."""
import argparse
import shutil
import tempfile
from pathlib import Path
import generate_hns_move_effects as gen

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--upstream-dir',required=True,type=Path)
up=p.parse_args().upstream_dir
moves=(up/'src/data/moves_info.h').read_text()
ids={'MOVE_ROLLOUT':205,'MOVE_ICE_BALL':301}
gen.parse_rollout_metadata(moves,ids); gen.verify_rollout_contract(str(up))
count=0
def refused(call):
    global count
    try: call()
    except ValueError: count+=1
    else: raise AssertionError('source mutation accepted')
for symbol in ids:
    start=moves.index('['+symbol+']')
    for old,new in (('EFFECT_ROLLOUT','EFFECT_HIT'),('.power = 30','.power = 31'),('.accuracy = 90','.accuracy = 100'),
        ('TARGET_SELECTED','TARGET_USER'),('DAMAGE_CATEGORY_PHYSICAL','DAMAGE_CATEGORY_SPECIAL'),
        ('.makesContact = TRUE','.makesContact = FALSE'),('.priority = 0','.priority = 1'),
        ('.pp = 20','.pp = 10'),('.power = 30','.power = 30, .punchingMove = TRUE'),
        ('.power = 30','.power = 30, .additionalEffects = ADDITIONAL_EFFECTS({.preAttackEffect = TRUE})')):
        assert old in moves[start:]
        changed=moves[:start]+moves[start:].replace(old,new,1)
        refused(lambda:gen.parse_rollout_metadata(changed,ids))
    refused(lambda:gen.parse_rollout_metadata(moves,{**ids,symbol:ids[symbol]+1}))
refused(lambda:gen.parse_rollout_metadata(moves.replace('.ballisticMove = TRUE,\n        .instructBanned = TRUE', '.ballisticMove = FALSE,\n        .instructBanned = TRUE'),ids))
mutations={
 'src/battle_util.c':[('basePower *= 2;','basePower *= 3;'),('volatiles.defenseCurl)','volatiles.defenseCurl == FALSE)'),('basePower <= 60','basePower < 60'),('case EFFECT_ROLLOUT:','case EFFECT_HIT:')],
 'src/battle_move_resolution.c':[('IsAnyTargetAffected()','TRUE'),('!gBattleStruct->unableToUseMove','TRUE'),('== gCurrentMove','!= gCurrentMove'),('rolloutTimer < 5','rolloutTimer < 6'),('gLockedMoves[gBattlerAttacker] = gCurrentMove','gLockedMoves[gBattlerAttacker] = MOVE_ROLLOUT'),('SetSameMoveTurnValues(moveEffect);','SetSameMoveTurnValues(EFFECT_HIT);')],
 'src/battle_main.c':[('gBattleMainFunc = HandleTurnActionSelectionState;','gBattleMainFunc = RunTurnActionsFunctions;'),('memset(&gBattleMons[battler].volatiles, 0, sizeof(struct Volatiles));',';')],
 'include/constants/battle.h':[('rolloutTimer,                  (u32, UINT8_MAX)','rolloutTimer,                  (u32, 7)'),('defenseCurl,                   (u32, 1)','defenseCurl,                   (u32, 2)')],
}
with tempfile.TemporaryDirectory() as temp:
    root=Path(temp)
    for path in gen.ROLLOUT_SOURCE_CONTRACTS:
        (root/path).parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(up/path,root/path)
    for path,changes in mutations.items():
        original=(up/path).read_text()
        for old,new in changes:
            assert old in original,(path,old)
            (root/path).write_text(original.replace(old,new,1))
            refused(lambda:gen.verify_rollout_contract(str(root)))
            (root/path).write_text(original)
print(f'Rollout source contract: {count} mutations refused')
