#!/usr/bin/env python3
"""Fail closed on every fixed-two descriptor or execution-handler mutation."""
import argparse, tempfile, shutil
from pathlib import Path
import generate_hns_move_effects as gen
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--upstream-dir',required=True,type=Path);up=p.parse_args().upstream_dir
moves=(up/'src/data/moves_info.h').read_text();ids=gen.parse_move_enum((up/'include/constants/moves.h').read_text())
assert len(gen.parse_fixed_two_metadata(moves,ids))==6
count=0
def refused(call):
    global count
    try:call()
    except ValueError:count+=1
    else:raise AssertionError('source mutation accepted')
main=(up/'src/battle_main.c').read_text()
gen.verify_fixed_two_selection_lifecycle(main)
for old,new in [('gChosenMoveByBattler[battler] = MOVE_NONE;', 'gChosenMoveByBattler[battler] = MOVE_TACKLE;'),
    ('gBattleMainFunc = HandleTurnActionSelectionState;', 'gBattleMainFunc = RunTurnActionsFunctions;'),
    ('gChosenMoveByBattler[battler] = GetBattlerChosenMove(battler);', 'gChosenMoveByBattler[battler] = MOVE_NONE;')]:
    refused(lambda:gen.verify_fixed_two_selection_lifecycle(main.replace(old,new)))
for symbol in gen.FIXED_TWO_MOVE_CONTRACTS:
    start=moves.index('['+symbol+']');end=moves.index('\n    },',start)+7
    body=moves[start:end]
    for old,new in [('.power = ','.power = 1 + '),('EFFECT_HIT','EFFECT_MULTI_HIT'),('TARGET_SELECTED','TARGET_BOTH'),
        ('.type = ','.type = INVALID_'),('.category = ','.category = INVALID_'),('.accuracy = ','.accuracy = 1 + '),('.pp = ','.pp = 1 + '),
        ('.priority = 0','.priority = 1'),('.strikeCount = 2','.strikeCount = 3'),
        ('.power = ','.multiHit = TRUE, .power = '),('.power = ','.punchingMove = TRUE, .power = '),
        ('.power = ','.additionalEffects = ADDITIONAL_EFFECTS({.preAttackEffect = TRUE}), .power = '),
        ('.power = ','.ignoresTargetAbility = TRUE, .power = '),('.power = ','.makesContact = TRUE, .power = ')]:
        assert old in body
        refused(lambda:gen.parse_fixed_two_metadata(moves[:start]+body.replace(old,new,1)+moves[end:],ids))
    refused(lambda:gen.parse_fixed_two_metadata(moves,{**ids,symbol:ids[symbol]+1}))
with tempfile.TemporaryDirectory() as temp:
    root=Path(temp)
    for path in {**gen.ROLLOUT_SOURCE_CONTRACTS,**gen.FIXED_TWO_SOURCE_CONTRACTS}:
        (root/path).parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(up/path,root/path)
    for path in {**gen.ROLLOUT_SOURCE_CONTRACTS,**gen.FIXED_TWO_SOURCE_CONTRACTS}:
        original=(root/path).read_bytes();(root/path).write_bytes(original+b'\n/* semantic drift */\n')
        refused(lambda:gen.verify_fixed_two_contract(str(root)));(root/path).write_bytes(original)
print(f'Fixed-two source contract: {count} mutations refused')
