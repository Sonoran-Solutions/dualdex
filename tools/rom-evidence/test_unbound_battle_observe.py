"""Invented bytes only; no ROM, core, save or screenshot needed."""
import json
from pathlib import Path
import struct
import unittest
from unittest.mock import Mock

import unbound_battle_observe as ub
import unbound_observe as u

HERE = Path(__file__).resolve().parent
TWO_OPPONENT_DOUBLE = 0x20000d


def fake_observer(memory):
    observer = ub.BattleObserver.__new__(ub.BattleObserver)
    observer.engine = Mock()
    observer.engine.read = Mock(side_effect=lambda address, size: memory(address, size))
    return observer


class BattleObserveTest(unittest.TestCase):
    def test_type_flag_names_split_named_bits_and_residue(self):
        self.assertEqual(ub.type_flag_names(TWO_OPPONENT_DOUBLE),
                         dict(names=['DOUBLE', 'IS_MASTER', 'TRAINER', 'TWO_OPPONENTS'], unnamed_bits='0x0'))
        self.assertEqual(ub.type_flag_names(0x4), dict(names=['IS_MASTER'], unnamed_bits='0x0'))
        self.assertEqual(ub.type_flag_names(0x100004), dict(names=['IS_MASTER'], unnamed_bits='0x100000'))
        self.assertEqual(ub.type_flag_names(0)['names'], [])

    def test_battler_map_applies_cfru_party_ranges(self):
        rows = ub.battler_map(4, [0, 1, 2, 3], [2, 1, 1, 5], TWO_OPPONENT_DOUBLE)
        self.assertEqual([(r['position'], r['owner'], r['slot_range'], r['index_in_owner_range']) for r in rows],
                         [('PLAYER_LEFT', 'player', [0, 6], True), ('OPPONENT_LEFT', 'opponent_a', [0, 3], True),
                          ('PLAYER_RIGHT', 'player', [0, 6], True), ('OPPONENT_RIGHT', 'opponent_b', [3, 6], True)])
        # A right-flank opponent index outside 3..5 is flagged, not silently remapped.
        self.assertFalse(ub.battler_map(4, [0, 1, 2, 3], [0, 0, 1, 1], TWO_OPPONENT_DOUBLE)[3]['index_in_owner_range'])
        # Ordinary doubles (one trainer or wild) share 0..5; in-game partner splits the player side.
        self.assertEqual(ub.battler_map(4, [0, 1, 2, 3], [0, 0, 1, 1], 0xd)[3]['slot_range'], [0, 6])
        partner = ub.battler_map(4, [0, 1, 2, 3], [0, 0, 3, 1], 0x40000d)
        self.assertEqual((partner[2]['owner'], partner[2]['slot_range']), ('partner', [3, 6]))
        wild = ub.battler_map(2, [0, 1, 255, 255], [2, 0, 0, 0], 0x4)
        self.assertEqual([(r['side'], r['flank'], r['party_index']) for r in wild],
                         [('player', 'left', 2), ('opponent', 'left', 0)])
        with self.assertRaisesRegex(ValueError, 'position'):
            ub.battler_map(3, [0, 1, 255, 255], [0, 0, 0, 0], 0x4)
        with self.assertRaisesRegex(ValueError, 'four'):
            ub.battler_map(5, [0, 1, 2, 3], [0, 0, 0, 0], 0x4)

    def lifecycle_memory(self, in_battle=True, flicker=None):
        calls = []
        values = {0x030030f0: struct.pack('<I', 0x080565b5),
                  0x030030f4: struct.pack('<I', ub.BATTLE_MAIN_CB2),
                  0x030030f0 + ub.MAIN_IN_BATTLE_BYTE: bytes([0x2 if in_battle else 0x1]),
                  0x03004f84: struct.pack('<I', 0x08014041), 0x02023d70: b'\x02',
                  0x020386ae: struct.pack('<H', 341)}
        def memory(address, size):
            calls.append(address)
            if address == flicker and calls.count(address) == 2:
                return b'\x00' * size
            return values[address][:size]
        return memory

    def test_lifecycle_snapshot_reads_in_battle_bit_and_refuses_incoherence(self):
        row = fake_observer(self.lifecycle_memory()).lifecycle_snapshot()
        self.assertEqual(row, dict(main_callback1=0x080565b5, main_callback2=ub.BATTLE_MAIN_CB2, main_in_battle=1,
                                   battle_main_func=0x08014041, absent_battler_flags=2,
                                   trainer_opponent_a=341, callback2_is_battle_main=True))
        # oamLoadDisabled (bit 0) alone is not inBattle.
        self.assertEqual(fake_observer(self.lifecycle_memory(in_battle=False)).lifecycle_snapshot()['main_in_battle'], 0)
        with self.assertRaisesRegex(ValueError, 'incoherent'):
            fake_observer(self.lifecycle_memory(flicker=0x02023d70)).lifecycle_snapshot()

    def test_enemy_snapshot_counts_by_verified_byte_and_bounds_it(self):
        party = bytes(range(256)) * 3
        def memory(count):
            def read(address, size):
                self.assertNotEqual(address, 0x02024028)  # the historical candidate is never the count
                if address == 0x0202402a:
                    return bytes([count])
                return party[address-0x0202402c:address-0x0202402c+size]
            return read
        data, row = fake_observer(memory(0)).party_snapshot('Enemy')
        self.assertEqual((data, row['count'], row['count_address']), (party[:600], 0, '0x202402a'))
        self.assertEqual(len(row['slot_sha256']), 6)
        with self.assertRaisesRegex(ValueError, 'exceeds six'):
            fake_observer(memory(7)).party_snapshot('Enemy')

    def test_summary_is_numeric_and_drops_text_fields(self):
        slot = bytearray(100); struct.pack_into('<H', slot, 32, 353); slot[8:18] = b'NICKNAMEXX'
        rec = dict(u.slot_fields(bytes(slot)), slot=0, production=dict(accepted=True, nickname='NICKNAMEXX'))
        mon = u.battle_mon_fields(bytes(ub.base.BATTLE_MON_SIZE))
        battle = dict(flags=TWO_OPPONENT_DOUBLE, count=1, active=4, party_indexes=[0, 0, 0, 0],
                      positions=[1, 0, 0, 0], outcome=0, enemy_count_bytes={'0x2024028': 0, '0x202402a': 0},
                      gBattleMons_sha256='0'*64, enemy_box_nonzero_slots=[rec],
                      battlers=[dict(battler=0, side='enemy', party_index=0, fields=mon,
                                     matches_party_record=dict(species=False))])
        obs = dict(label='t', frame=1, battle=battle, lifecycle={}, type_flags=ub.type_flag_names(battle['flags']),
                   parties=dict(player=dict(count=6, sha256='1'*64), enemy=dict(count=0, sha256='2'*64)),
                   rgb565_sha256='3'*64)
        report = dict(status='OBSERVED', observations=[obs], rom=dict(sha256='a'), core_sha256='b',
                      adapter_pin=dict(binary_sha256='c', source_revision='d'), bootstrap_input=dict(sha256='e'),
                      bootstrap='OWNER_BATTERY_COPY', static=dict(battle_spans=[], battle_literal_references={},
                                                                 lifecycle_literal_references={}),
                      **{k: k for k in ('observer_sha256', 'battle_observer_sha256', 'helper_sha256',
                                        'identity_helper_sha256', 'movie_sha256')})
        summary = ub.summarize(report, 'f')
        self.assertNotIn('NICKNAME', json.dumps(summary))
        self.assertNotIn('nickname', json.dumps(summary))
        self.assertEqual(summary['timeline'][0]['enemy_records'][0]['species_word'], 353)
        with self.assertRaisesRegex(ValueError, 'not a successful'):
            ub.summarize(dict(report, status='FAIL'), 'f')

    def test_repeat_comparison_ignores_only_screen_hashes(self):
        obs = lambda frame, screen: dict(label='p', frame=frame, battle=dict(flags=4), lifecycle=dict(a=1),
                                         parties=dict(player=dict(sha256='x'), enemy=dict(sha256='y')),
                                         rgb565_sha256=screen)
        same = ub.repeat_comparison(dict(observations=[obs(1, 'a')]), dict(observations=[obs(1, 'b')]))
        self.assertEqual(same, dict(observations=1, battle_party_lifecycle_identical=True,
                                    screen_hash_differences=['p']))
        moved = ub.repeat_comparison(dict(observations=[obs(1, 'a')]), dict(observations=[obs(2, 'a')]))
        self.assertFalse(moved['battle_party_lifecycle_identical'])

    def test_committed_movie_and_manifest_are_bound(self):
        movie = (HERE/'unbound-battle-trainer-double.movie').read_bytes()
        commands = u.parse_movie(movie.decode('ascii'))
        self.assertTrue(all(c[0] in ('run', 'mash', 'observe') for c in commands))
        evidence = json.loads((HERE/'evidence/unbound-runtime-battle-trainer.json').read_text())
        self.assertEqual(evidence['support_verdict'], 'NONE')
        inputs = evidence['inputs']
        self.assertEqual(inputs['movie_sha256'], u.rowe.digest(movie))
        # A tool change needs a fresh capture (the manifest names the producing tool).
        self.assertEqual(inputs['battle_observer_sha256'], u.rowe.digest(Path(ub.__file__).read_bytes()))
        self.assertTrue(evidence['repeat_run']['battle_party_lifecycle_identical'])
        self.assertEqual((inputs['rom_sha256'], inputs['bootstrap_sha256'], inputs['core_sha256']),
                         (u.ROM_SHA256, u.SAVE_SHA256, u.rowe.CORE_SHA256))
        self.assertEqual([t['label'] for t in evidence['timeline']], [c[1] for c in commands if c[0] == 'observe'])
        self.assertEqual(evidence['lifecycle_addresses'], {k: hex(v) for k, v in ub.LIFECYCLE_SYMBOLS.items()})
        t = {row['label']: row for row in evidence['timeline']}
        self.assertEqual(t['first_turn_menu']['type_flag_names'], ['DOUBLE', 'IS_MASTER', 'TRAINER', 'TWO_OPPONENTS'])
        self.assertEqual(t['first_turn_menu']['gBattlerPositions'], [0, 1, 2, 3])
        self.assertEqual(t['replacements_sent']['gBattlerPartyIndexes'], [0, 1, 1, 5])
        self.assertEqual(t['after_switch_turn2']['gBattlerPartyIndexes'][0], 2)
        for row in evidence['timeline']:
            with self.subTest(label=row['label']):
                live = row['lifecycle']['main_in_battle'] == 1
                self.assertEqual(live, row['label'] not in (
                    'grotto_overworld', 'outside_grotto', 'water_start', 'cave_before_trainers',
                    'trainers_spotted', 'overworld_after_battle', 'overworld_later'))
                self.assertEqual(row['enemy_count_bytes']['0x202402a'], 0)
                if live and row['label'] != 'battle_start':
                    self.assertTrue(all(all(b['matches_party_record'].values()) for b in row['battlers']))
                    self.assertTrue(all(m['index_in_owner_range'] for m in row['battler_map']))
        # Teardown leaves flags/count/indexes stale; only gMain.inBattle drops.
        self.assertEqual(t['overworld_later']['gBattleTypeFlags'], hex(TWO_OPPONENT_DOUBLE))
        self.assertEqual(t['overworld_later']['gBattleMons_sha256'], t['battle_won']['gBattleMons_sha256'])


if __name__ == '__main__':
    unittest.main()
