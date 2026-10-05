#!/usr/bin/env python3
"""Fail-closed Gyro Ball source mutations; requires the pinned source checkout, no ROM."""
import argparse
import shutil
import tempfile
from pathlib import Path
import generate_hns_move_effects as gen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream-dir', required=True, type=Path)
    args = parser.parse_args()
    gen.verify_gyro_speed_contract(str(args.upstream_dir))
    moves = (args.upstream_dir / 'src/data/moves_info.h').read_text()
    gen.parse_gyro_metadata(moves, {'MOVE_GYRO_BALL':360})
    mutations = {
        'src/battle_util.c': [('25 * GetBattlerTotalSpeedStat', '24 * GetBattlerTotalSpeedStat'),
            ('/ attackerSpeed) + 1', '/ attackerSpeed) + 2'), ('basePower > 150', 'basePower > 151'),
            ('attackerSpeed == 0', 'attackerSpeed == 1'), ('UQ_4_12(1.1)', 'UQ_4_12(1.2)')],
        'src/battle_main.c': [('speed *= 2;', 'speed *= 3;'), ('speed /=', 'speed *='),
            ('holdEffect != HOLD_EFFECT_UTILITY_UMBRELLA', 'holdEffect == HOLD_EFFECT_UTILITY_UMBRELLA'),
            ('(speed * 150) / 100', '(speed * 151) / 100'), ('speed /= 4;', 'speed /= 2;'),
            ('ability != ABILITY_QUICK_FEET', 'ability == ABILITY_QUICK_FEET'),
            ('SIDE_STATUS_TAILWIND', 'SIDE_STATUS_SWAMP'), ('!= GIMMICK_DYNAMAX', '== GIMMICK_DYNAMAX')],
        'src/pokemon.c': [('{10, 40}, // -6', '{10, 39}, // -6')],
        'include/config/battle.h': [('B_PARALYSIS_SPEED       GEN_3', 'B_PARALYSIS_SPEED       GEN_7')],
        'include/constants/battle.h': [('SIDE_STATUS_TAILWIND          (1 << 4)', 'SIDE_STATUS_TAILWIND          (1 << 3)')],
        'include/constants/pokemon.h': [('MAX_STAT_STAGE    12', 'MAX_STAT_STAGE    13'), ('STAT_DEF,\n    STAT_SPEED,', 'STAT_SPEED,\n    STAT_DEF,')],
        'include/pokemon.h': [('/*0x06*/ u16 speed;', '/*0x06*/ u32 speed;')],
        'src/data/items.h': [('.secondaryId = STAT_SPEED,', '.secondaryId = STAT_ATK,')],
    }
    count = 0
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        for path in mutations:
            (root/path).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(args.upstream_dir/path,root/path)
        # Match config/constants independent of alignment while retaining exact mutation targets.
        mutations['include/config/battle.h'] = [('GEN_3', 'GEN_7')]
        import re
        battle = (root/'include/constants/battle.h').read_text()
        literal = re.search(r'#define SIDE_STATUS_TAILWIND\s+\(1 << 4\)', battle).group(0)
        mutations['include/constants/battle.h'] = [(literal,literal.replace('1 << 4','1 << 3'))]
        for path, cases in mutations.items():
            original = (root/path).read_text()
            for before, after in cases:
                if path == 'src/battle_main.c':
                    offset = original.index('u32 GetBattlerTotalSpeedStat(')
                elif path == 'include/config/battle.h':
                    offset = original.index('#define B_PARALYSIS_SPEED')
                elif path == 'src/data/items.h':
                    offset = original.index('[ITEM_POWER_ANKLET]')
                else: offset = 0
                assert before in original[offset:], (path,before)
                changed = original[:offset] + original[offset:].replace(before,after,1)
                (root/path).write_text(changed)
                try: gen.verify_gyro_speed_contract(str(root))
                except ValueError: count += 1
                else: raise AssertionError(('mutation accepted',path,before))
                (root/path).write_text(original)
    for before, after in (('.ballisticMove = TRUE','.ballisticMove = FALSE'),
                           ('.makesContact = TRUE','.makesContact = FALSE'),
                           ('.power = 1,','.power = 2,')):
        begin = moves.index('[MOVE_GYRO_BALL]')
        changed = moves[:begin] + moves[begin:].replace(before,after,1)
        try: gen.parse_gyro_metadata(changed, {'MOVE_GYRO_BALL':360})
        except ValueError: count += 1
        else: raise AssertionError(('MoveInfo mutation accepted',before))
    print(f'Gyro Ball/Speed source contract: {count} mutations refused')

if __name__ == '__main__': main()
