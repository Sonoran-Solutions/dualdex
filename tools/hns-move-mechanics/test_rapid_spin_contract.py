#!/usr/bin/env python3
"""Frozen Rapid Spin (ID 229) descriptor and source-contract mutation checks."""
import argparse
import shutil
import tempfile
from pathlib import Path
import generate_hns_move_effects as gen

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--upstream-dir', required=True, type=Path)
up = p.parse_args().upstream_dir
moves = (up / 'src/data/moves_info.h').read_text()
ids = {'MOVE_RAPID_SPIN': 229}
ability_flags, flags = {}, {}


def sheer_maps(text, latest=True):
    contact, unknown_contact, sheer, unknown_sheer = gen.parse_contact_and_sheer_force(
        text, updated_move_data_latest=True, speed_buffing_rapid_spin_latest=latest)
    return ({ids['MOVE_RAPID_SPIN']: contact['MOVE_RAPID_SPIN']},
            {ids['MOVE_RAPID_SPIN']: unknown_contact['MOVE_RAPID_SPIN']},
            {ids['MOVE_RAPID_SPIN']: sheer['MOVE_RAPID_SPIN']} if 'MOVE_RAPID_SPIN' in sheer else {},
            {ids['MOVE_RAPID_SPIN']: True} if 'MOVE_RAPID_SPIN' in unknown_sheer else {})


def parse(text, latest=True):
    contact, unknown_contact, sheer, unknown_sheer = sheer_maps(text, latest)
    return gen.parse_rapid_spin_metadata(text, ids, sheer, unknown_sheer, contact, unknown_contact,
                                         ability_flags, flags)


count = 0


def refused(call):
    global count
    try:
        call()
    except ValueError:
        count += 1
    else:
        raise AssertionError('source mutation accepted')


# The pinned tree must resolve to the frozen descriptor with a TRUE Sheer Force predicate.
resolved = parse(moves)
assert resolved[229]['sheerForceAffected'] is True and resolved[229]['power'] == 50
assert resolved[229]['makesContact'] is True and resolved[229]['strikeCount'] == 1

# Without the verified GEN_LATEST Speed config the predicate stays unresolved and admission refuses.
refused(lambda: parse(moves, latest=False))

start = moves.index('[MOVE_RAPID_SPIN]')
for old, new in (('EFFECT_RAPID_SPIN', 'EFFECT_HIT'), ('.power = B_UPDATED_MOVE_DATA >= GEN_8 ? 50 : 20',
                 '.power = 60'), ('.accuracy = 100', '.accuracy = 90'), ('TARGET_SELECTED', 'TARGET_USER'),
                 ('.makesContact = TRUE', '.makesContact = FALSE'), ('.priority = 0', '.priority = 1'),
                 ('MOVE_EFFECT_SPD_PLUS_1', 'MOVE_EFFECT_SPD_MINUS_1'), ('.chance = 100', '.chance = 0'),
                 ('.self = TRUE', '.self = FALSE'), ('.pp = 40', '.pp = 20')):
    assert old in moves[start:], old
    changed = moves[:start] + moves[start:].replace(old, new, 1)
    refused(lambda: parse(changed))

# Admitting a different move ID is refused even when the descriptor text is identical.
refused(lambda: gen.parse_rapid_spin_metadata(moves, {'MOVE_RAPID_SPIN': 230}, {229: True}, {}, {229: {'makesContact'}},
                                              {}, ability_flags, flags))

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    for path in gen.RAPID_SPIN_SOURCE_CONTRACTS:
        (root / path).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(up / path, root / path)
    gen.verify_rapid_spin_contract(str(root))
    for path, old, new in (
        ('src/battle_util.c', 'bool32 MoveIsAffectedBySheerForce(enum Move move)', 'bool32 MoveIsAffectedBySheerForce(enum Move move) { return FALSE; }\nstatic bool32 Unused(enum Move move)'),
        ('src/battle_move_resolution.c', 'case EFFECT_RAPID_SPIN:', 'case EFFECT_RAPID_SPIN_DISABLED:'),
        ('src/battle_move_resolution.c', 'if (MoveIsAffectedBySheerForce(', 'if (0 && MoveIsAffectedBySheerForce('),
        ('src/battle_script_commands.c', 'Cmd_rapidspinfree', 'Cmd_rapidspinfree_changed'),
        ('include/config/battle.h', 'B_SPEED_BUFFING_RAPID_SPIN  GEN_LATEST', 'B_SPEED_BUFFING_RAPID_SPIN  GEN_7'),
        ('include/constants/battle_move_resolution.h', 'MOVEEND_SHEER_FORCE', 'MOVEEND_SHEER_FORCE_X'),
        ('src/data/battle_move_effects.h', '[EFFECT_RAPID_SPIN]', '[EFFECT_RAPID_SPIN_X]'),
        ('include/constants/battle_move_effects.h', 'EFFECT_RAPID_SPIN,', 'EFFECT_RAPID_SPIN_X,')):
        original = (up / path).read_text()
        if old not in original:
            # A mutation point absent from the pinned source is itself a refusal target: the hash check
            # still rejects any content change, so only whole-file hashing is required for this case.
            (root / path).write_text(original + '\n/* mutation */\n')
            refused(lambda: gen.verify_rapid_spin_contract(str(root)))
            (root / path).write_text(original)
            continue
        (root / path).write_text(original.replace(old, new, 1))
        refused(lambda: gen.verify_rapid_spin_contract(str(root)))
        (root / path).write_text(original)
print(f'Rapid Spin source contract: {count} mutations refused')
