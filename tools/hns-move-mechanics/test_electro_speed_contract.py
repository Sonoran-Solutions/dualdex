#!/usr/bin/env python3
"""Pinned Electro Ball drift checks, including the unguarded division contract."""
import argparse
import shutil
import tempfile
from pathlib import Path
import generate_hns_move_effects as gen


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--upstream-dir', required=True, type=Path)
    upstream = p.parse_args().upstream_dir
    gen.verify_gyro_speed_contract(str(upstream))
    moves = (upstream/'src/data/moves_info.h').read_text()
    gen.parse_electro_metadata(moves, {'MOVE_ELECTRO_BALL':486})
    count = 0
    def refused(call):
        nonlocal count
        try: call()
        except ValueError: count += 1
        else: raise AssertionError('source mutation accepted')
    refused(lambda: gen.parse_electro_metadata(moves, {'MOVE_ELECTRO_BALL':487}))
    begin = moves.index('[MOVE_ELECTRO_BALL]')
    for before,after in (('EFFECT_ELECTRO_BALL','EFFECT_HIT'),('TYPE_ELECTRIC','TYPE_NORMAL'),
        ('DAMAGE_CATEGORY_SPECIAL','DAMAGE_CATEGORY_PHYSICAL'),('.power = 1,','.power = 2,'),
        ('.ballisticMove = TRUE','.ballisticMove = FALSE'),('.ballisticMove = TRUE','.ballisticMove = TRUE, .makesContact = TRUE'),
        ('.ballisticMove = TRUE','.ballisticMove = TRUE, .additionalEffects = ADDITIONAL_EFFECTS({.moveEffect = MOVE_EFFECT_PARALYSIS, .chance = 10})')):
        assert before in moves[begin:]
        changed = moves[:begin]+moves[begin:].replace(before,after,1)
        refused(lambda: gen.parse_electro_metadata(changed, {'MOVE_ELECTRO_BALL':486}))
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        for path in set(path for path,name in gen.GYRO_SPEED_HELPERS):
            (root/path).parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(upstream/path,root/path)
        path = root/'src/battle_util.c'
        original = path.read_text()
        begin = original.index('case EFFECT_ELECTRO_BALL:')
        branch = original[begin:original.index('case EFFECT_GYRO_BALL:',begin)]
        for changed in (branch.replace(' / ', ' * '),
            branch.replace('battlerAtk, ctx->abilityAtk, ctx->holdEffectAtk','SWAP').replace('battlerDef, ctx->abilityDef, ctx->holdEffectDef','battlerAtk, ctx->abilityAtk, ctx->holdEffectAtk').replace('SWAP','battlerDef, ctx->abilityDef, ctx->holdEffectDef'),
            branch.replace('battlerAtk, ctx->abilityAtk, ctx->holdEffectAtk','battlerDef, ctx->abilityDef, ctx->holdEffectDef',1),
            branch.replace('battlerDef, ctx->abilityDef, ctx->holdEffectDef','battlerAtk, ctx->abilityAtk, ctx->holdEffectAtk',1),
            branch.replace(' - 1',' - 2'),branch.replace('[speed]','[speed + 1]'),
            branch.replace('EFFECT_ELECTRO_BALL','EFFECT_HIT'),branch.replace('>= ARRAY_COUNT','> ARRAY_COUNT')):
            assert changed != branch
            path.write_text(original.replace(branch,changed,1))
            refused(lambda: gen.verify_gyro_speed_contract(str(root)))
        table = original.replace('{40, 60, 80, 120, 150}', '{40, 60, 81, 120, 150}',1)
        assert table != original
        refused(lambda: gen.electro_power_table(table))
    print(f'Electro Ball source contract: {count} mutations refused')


if __name__ == '__main__': main()
