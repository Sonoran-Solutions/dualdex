"""Invented bytes only; no ROM, core, save or screenshot needed."""
import ctypes as C
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

import unbound_observe as u


def invented_slot():
    raw = bytearray(100)
    struct.pack_into('<II', raw, 0, 0x12345678, 0x87654321)
    raw[8:18], raw[20:27] = b'ABCDEFGHIJ', b'TRAINER'
    struct.pack_into('<HHI', raw, 32, 0xfabc, 0xbcde, 0x98765432)
    struct.pack_into('<4H4B', raw, 44, 0x8123, 0x9234, 0xa345, 0xb456, 255, 128, 2, 1)
    raw[56:62] = bytes([255, 128, 3, 4, 5, 6])
    struct.pack_into('<I', raw, 72, 0xc1234567)
    raw[84] = 100
    struct.pack_into('<7H', raw, 86, 0, 999, 101, 102, 103, 104, 105)
    return raw

def invented_battler(slot):
    """0x58-byte CFRU BattlePokemon hypothesis built from an invented party record."""
    raw = bytearray(u.BATTLE_MON_SIZE)
    struct.pack_into('<H', raw, 0, struct.unpack_from('<H', slot, 32)[0])
    struct.pack_into('<5H', raw, 2, *struct.unpack_from('<5H', slot, 90))
    raw[12:20] = slot[44:52]
    raw[0x18:0x20] = bytes([20, 5, 6, 6, 6, 6, 6, 6])
    raw[0x24:0x28] = slot[52:56]
    struct.pack_into('<H', raw, 0x28, struct.unpack_from('<H', slot, 86)[0])
    raw[0x2a] = slot[84]
    struct.pack_into('<HH', raw, 0x2c, struct.unpack_from('<H', slot, 88)[0], struct.unpack_from('<H', slot, 34)[0])
    struct.pack_into('<IIIII', raw, 0x44, struct.unpack_from('<I', slot, 36)[0],
                     struct.unpack_from('<I', slot, 0)[0], 0, 0, struct.unpack_from('<I', slot, 4)[0])
    return bytes(raw)


class UnboundBattleTests(unittest.TestCase):
    def test_literal_reference_counter_reads_thumb_pc_loads_widths_and_strides(self):
        rom = bytearray(64)
        struct.pack_into('<HHH', rom, 0, 0x4901, 0x7808, 0x2258)  # ldr r1,[pc,#4]; ldrb r0,[r1]; movs r2,#0x58
        struct.pack_into('<I', rom, 8, 0x02023be4)
        struct.pack_into('<I', rom, 30, 0x02023be4)  # unaligned copy: not a literal
        struct.pack_into('<HH', rom, 16, 0x4b00, 0x881a)  # ldr r3,[pc,#0]; ldrh r2,[r3]
        struct.pack_into('<I', rom, 20, 0x02023bce)
        out = u.literal_references(bytes(rom), dict(mons=0x02023be4, indexes=0x02023bce, absent=0x02024028))
        self.assertEqual(out['mons']['pc_literal_loads'], 1)
        self.assertEqual(out['mons']['first_access'], {'ldrb': 1})
        self.assertEqual(out['mons']['nearby_stride_constants']['0x58'], 1)
        self.assertEqual((out['indexes']['pc_literal_loads'], out['indexes']['first_access']), (1, {'ldrh': 1}))
        self.assertEqual(out['absent']['pc_literal_loads'], 0)

    def test_battle_mon_fields_are_raw_and_size_bounded(self):
        mon = u.battle_mon_fields(invented_battler(invented_slot()))
        self.assertEqual((mon['species_word'], mon['item_word'], mon['level_byte'], mon['max_hp_word']),
                         (0xfabc, 0xbcde, 100, 999))
        self.assertEqual((mon['byte_18'], mon['stat_stage_bytes']), (20, [5, 6, 6, 6, 6, 6, 6]))
        self.assertTrue(all(u.compare_battler(mon, bytes(invented_slot())).values()))
        changed = bytearray(invented_slot()); changed[86] = 7
        self.assertFalse(u.compare_battler(mon, bytes(changed))['hp'])
        for size in (0, 0x57, 0x59, 100):
            with self.subTest(size=size), self.assertRaises(ValueError):
                u.battle_mon_fields(bytes(size))

    def snapshot(self, memory, enemy):
        observer = u.Observer.__new__(u.Observer)
        observer.engine = Mock()
        observer.engine.read = Mock(side_effect=lambda address, size: memory[address][:size])
        with tempfile.TemporaryDirectory() as folder, \
             patch.object(u, 'native_fields', return_value={'accepted': True}):
            row = observer.battle_snapshot('t', Path(folder), dict(player=bytes(invented_slot())*6, enemy=enemy))
            self.assertEqual((Path(folder)/'t_battle.mons').stat().st_size, 4*u.BATTLE_MON_SIZE)
        return row

    def battle_memory(self, count=2, indexes=(2, 0, 0, 0)):
        b, slot = u.BATTLE_SYMBOLS, bytes(invented_slot())
        return {b['gBattleTypeFlags']: struct.pack('<I', 4), b['gBattlersCount']: bytes([count]),
                b['gActiveBattler']: b'\x02', b['gBattlerPartyIndexes']: struct.pack('<4H', *indexes),
                b['gBattlerPositions']: bytes([0, 1, 255, 255]), b['gBattleOutcome']: b'\x00',
                0x02024028: b'\x00', 0x0202402a: b'\x00',
                b['gBattleMons']: invented_battler(slot)*2 + bytes(2*u.BATTLE_MON_SIZE)}

    def test_battle_snapshot_maps_battlers_to_party_index_and_skips_zeroed_enemy_records(self):
        zeroed = bytearray(100); zeroed[85] = 0xff
        enemy = bytes(invented_slot()) + bytes(zeroed)*5
        row = self.snapshot(self.battle_memory(), enemy)
        self.assertEqual([(m['side'], m['party_index']) for m in row['battlers']], [('player', 2), ('enemy', 0)])
        self.assertTrue(all(all(m['matches_party_record'].values()) for m in row['battlers']))
        self.assertEqual([s['slot'] for s in row['enemy_box_nonzero_slots']], [0])
        self.assertEqual(row['enemy_count_bytes'], {'0x2024028': 0, '0x202402a': 0})
        row = self.snapshot(self.battle_memory(count=5), enemy)
        self.assertEqual((row['battlers_status'], row['battlers']), ('COUNT_OUT_OF_RANGE', []))
        row = self.snapshot(self.battle_memory(indexes=(6, 0, 0, 0)), enemy)
        self.assertNotIn('matches_party_record', row['battlers'][0])

    def test_battle_snapshot_refuses_incoherent_reads(self):
        memory, calls = self.battle_memory(), []
        observer = u.Observer.__new__(u.Observer)
        observer.engine = Mock()
        def read(address, size):
            calls.append(address)
            if address == u.BATTLE_SYMBOLS['gBattlersCount'] and calls.count(address) == 2:
                return b'\x01'
            return memory[address][:size]
        observer.engine.read = Mock(side_effect=read)
        with tempfile.TemporaryDirectory() as folder, self.assertRaisesRegex(ValueError, 'incoherent'):
            observer.battle_snapshot('t', Path(folder), dict(player=bytes(600), enemy=bytes(600)))

    def test_committed_battle_movie_and_evidence_are_bound_and_sanitized(self):
        here = Path(u.__file__).parent
        movie = (here/'unbound-battle-wild-run.movie').read_bytes()
        commands = u.parse_movie(movie.decode('ascii'))
        self.assertTrue(all(c[0] in ('run', 'mash', 'observe') for c in commands))
        labels = [c[1] for c in commands if c[0] == 'observe']
        evidence = json.loads((here/'evidence/unbound-runtime-battle.json').read_text())
        self.assertEqual(evidence['support_verdict'], 'NONE')
        self.assertEqual(evidence['inputs']['rom_sha256'], u.ROM_SHA256)
        self.assertEqual(evidence['inputs']['battery_sha256'], u.SAVE_SHA256)
        self.assertEqual(evidence['inputs']['core_sha256'], u.rowe.CORE_SHA256)
        self.assertEqual(evidence['inputs']['observer_sha256'], u.rowe.digest(Path(u.__file__).read_bytes()))
        self.assertEqual(evidence['inputs']['movie_sha256'], u.rowe.digest(movie))
        self.assertEqual([t['label'] for t in evidence['timeline']], labels)
        self.assertEqual(evidence['addresses'], {k: hex(v) for k, v in u.BATTLE_SYMBOLS.items()})
        timeline = {t['label']: t for t in evidence['timeline']}
        for label, row in timeline.items():
            with self.subTest(label=label):
                self.assertEqual(row['enemy_count_bytes'], {'0x2024028': 0, '0x202402a': 0})
                for battler in row['battlers']:
                    self.assertTrue(all(battler['matches_party_record'].values()))
        self.assertEqual([b['party_index'] for b in timeline['after_move']['battlers']], [0, 0])
        self.assertEqual([b['party_index'] for b in timeline['after_switch']['battlers']], [2, 0])
        self.assertEqual(timeline['after_switch']['battlers'][0]['species_word'],
                         evidence['player_party_species_words'][2])
        self.assertEqual(timeline['pre_battle_overworld']['battlers'], [])
        # Teardown: the post-exit overworld still holds the last battle's globals.
        self.assertEqual(timeline['overworld_later']['gBattleMons_sha256'],
                         timeline['got_away']['gBattleMons_sha256'])
        self.assertEqual(timeline['overworld_later']['outcome'], 4)
        def strings(node):
            if isinstance(node, dict):
                for value in node.values(): yield from strings(value)
            elif isinstance(node, list):
                for value in node: yield from strings(value)
            elif isinstance(node, str):
                yield node
        self.assertFalse([t for t in strings(evidence) if len(t) > 64 and all(c in '0123456789abcdef' for c in t)])


class UnboundObserveTests(unittest.TestCase):
    def test_raw_fields_preserve_width_and_do_not_translate_ids(self):
        row = u.slot_fields(invented_slot())
        fixed = row['fixed_raw_gaem']
        self.assertEqual((row['pid'], row['otid'], row['checksum_word']),
                         (0x12345678, 0x87654321, 0))
        self.assertEqual((fixed['species_word'], fixed['item_word'], fixed['experience_word']),
                         (0xfabc, 0xbcde, 0x98765432))
        self.assertEqual(fixed['moves_words'], [0x8123, 0x9234, 0xa345, 0xb456])
        self.assertEqual(fixed['pp_bytes'], [255, 128, 2, 1])
        self.assertEqual(fixed['ev_bytes'], [255, 128, 3, 4, 5, 6])
        self.assertEqual(fixed['iv_word'], 0xc1234567)
        self.assertEqual(fixed['ability_bit31_hypothesis'], 1)
        self.assertEqual(row['nickname_hex'], b'ABCDEFGHIJ'.hex())
        self.assertEqual(row['ot_name_hex'], b'TRAINER'.hex())
        self.assertEqual((row['level_byte'], row['hp_word'], row['stats_words']['max_hp']), (100, 0, 999))
        for size in list(range(100)) + [101, 456, 600]:
            with self.subTest(size=size), self.assertRaises(ValueError):
                u.slot_fields(bytes(size))

    def test_all_permutations_and_xor_checksums(self):
        original = invented_slot()
        blocks = {name: original[32+i*12:44+i*12] for i, name in enumerate('GAEM')}
        for pid, order in enumerate(u.ORDERS):
            raw = bytearray(original)
            struct.pack_into('<I', raw, 0, pid)
            shuffled = b''.join(blocks[n] for n in order)
            checksum = sum(struct.unpack('<24H', shuffled)) & 65535
            struct.pack_into('<H', raw, 28, checksum)
            key = pid ^ struct.unpack_from('<I', raw, 4)[0]
            raw[32:80] = b''.join(struct.pack('<I', w ^ key) for (w,) in struct.iter_unpack('<I', shuffled))
            row = u.slot_fields(raw)
            self.assertTrue(row['xor_checksum_matches'])
            self.assertEqual(row['pid_order_xor'], u.sub_fields(original[32:80], 'GAEM'))
            raw[32:80] = shuffled
            self.assertTrue(u.slot_fields(raw)['raw_checksum_matches'])
            self.assertEqual(u.slot_fields(raw)['pid_order_raw'], row['pid_order_xor'])
        self.assertEqual(u.slot_fields(bytes(100))['raw_sum16'], 0)

    def test_bounded_movie_refuses_writes_and_oversized_captures(self):
        self.assertEqual(u.parse_movie('run 10\nmash 1\nsave\nrestore\nobserve boot')[-1], ('observe', 'boot'))
        for movie in ['cartridge_save\nobserve a', 'write 1 2\nobserve a', 'restore\nobserve a',
                      'run 500001\nobserve a', 'observe a\nobserve a', 'observe ../../escape',
                      '\n'.join('observe a'+str(n) for n in range(65)),
                      'save\n'*4096+'observe a']:
            with self.subTest(movie=movie[:60]), self.assertRaises(ValueError):
                u.parse_movie(movie)

    def test_snapshot_coherence_count_bounds_and_stale_excluded_slots(self):
        observer = u.Observer.__new__(u.Observer)
        observer.engine = Mock()
        raw = bytes(invented_slot())*6
        reads = [b'\x02', raw[:300], raw[300:], b'\x02', raw[:300], raw[300:], b'\x02']
        observer.engine.read = Mock(side_effect=reads)
        with patch.object(u, 'native_fields', return_value={'accepted': False}) as native:
            data, row = observer.party_snapshot('Player')
        self.assertEqual(data, raw)
        self.assertEqual((row['count'], row['count_after'], row['count_final']), (2, 2, 2))
        self.assertEqual(len(row['slots']), 2)
        self.assertEqual(len(row['slot_sha256']), 6)
        self.assertEqual(native.call_count, 2)
        for position, replacement in [(0, b'\x07'), (3, b'\x01'), (4, bytes(300)), (6, b'\x01')]:
            changed = reads.copy(); changed[position] = replacement
            observer.engine.read = Mock(side_effect=changed)
            with self.subTest(position=position), self.assertRaises(ValueError):
                observer.party_snapshot('Player')
        observer.engine.read = Mock(side_effect=[b'\x00',raw[:300],raw[300:],b'\x00',raw[:300],raw[300:],b'\x00'])
        with patch.object(u, 'native_fields') as native:
            self.assertEqual(observer.party_snapshot('Player')[1]['slots'], [])
            native.assert_not_called()

    def test_native_failure_never_publishes_partial_fields(self):
        host = Mock()
        host.pokemon_parse_single.return_value = False
        self.assertEqual(u.native_fields(host, invented_slot()), {'accepted': False})
        with self.assertRaises(ValueError): u.native_fields(host, bytes(99))

    def test_native_result_uses_declared_abi(self):
        host = Mock()
        def parse(raw, party, pointer):
            out = C.cast(pointer, C.POINTER(u.Parsed)).contents
            out.species, out.held_item, out.level, out.current_hp = 777, 1023, 42, 30
            out.max_hp = 99
            out.moves[:] = [1, 2, 3, 4]
            out.hp_iv, out.sp_defense_ev = 31, 255
            out.nickname, out.tid, out.is_shiny, out.shiny_value = b'GARY', 53340, False, 15
            return True
        host.pokemon_parse_single.side_effect = parse
        row = u.native_fields(host, invented_slot())
        self.assertEqual((row['species'], row['held_item'], row['moves']), (777, 1023, [1, 2, 3, 4]))
        self.assertEqual((row['hp'], row['stats']['max_hp'], row['ivs'][0], row['evs'][5]), (30, 99, 31, 255))
        self.assertEqual((row['nickname'], row['tid'], row['is_shiny'], row['shiny_value']),
                         ('GARY', 53340, False, 15))

    def test_save_block_party_copy_is_bounded_and_compared_per_counted_slot(self):
        observer = u.Observer.__new__(u.Observer)
        observer.engine = Mock()
        raw = bytes(invented_slot())*6
        live = dict(count=2, slot_sha256=[u.rowe.digest(raw[i*100:(i+1)*100]) for i in range(6)])
        observer.engine.read = Mock(side_effect=[raw[:300], raw[300:], b'\x02'])
        row = observer.save_party_copy(0x0202552c, live)
        self.assertEqual((row['count'], row['counted_slots_equal_live']), (2, True))
        observer.engine.read.assert_any_call(0x0202552c + 0x38, 300)
        observer.engine.read.assert_any_call(0x0202552c + 0x34, 1)
        changed = bytearray(raw); changed[150] ^= 1
        observer.engine.read = Mock(side_effect=[bytes(changed[:300]), bytes(changed[300:]), b'\x02'])
        self.assertFalse(observer.save_party_copy(0x0202552c, live)['counted_slots_equal_live'])
        for pointer in (0, 0x01ffffff, 0x02040000 - 0x38 - 599, 0x03000000):
            observer.engine.read = Mock()
            self.assertEqual(observer.save_party_copy(pointer, live), {'status': 'POINTER_OUT_OF_RANGE'})
            observer.engine.read.assert_not_called()

    def test_committed_route_movie_is_bounded_and_matches_evidence(self):
        movie = (Path(u.__file__).parent/'unbound-continue-summary.movie').read_bytes()
        commands = u.parse_movie(movie.decode('ascii'))
        labels = [c[1] for c in commands if c[0] == 'observe']
        self.assertEqual(len(labels), 26)
        self.assertTrue(all(c[0] in ('run', 'mash', 'observe') for c in commands))
        self.assertEqual([l for l in labels if l.endswith('_info')], ['s%d_info' % n for n in range(1, 7)])
        evidence = json.loads((Path(u.__file__).parent/'evidence/unbound-runtime-observation.json').read_text())
        self.assertEqual(evidence['battery_run']['movie_sha256'], u.rowe.digest(movie))
        self.assertEqual(evidence['battery_run']['observations'], len(labels))

    def test_committed_runtime_observation_is_self_consistent_and_sanitized(self):
        evidence = json.loads((Path(u.__file__).parent/'evidence/unbound-runtime-observation.json').read_text())
        self.assertEqual(evidence['support_verdict'], 'NONE')
        self.assertEqual(evidence['inputs']['rom']['sha256'], u.ROM_SHA256)
        self.assertEqual(evidence['inputs']['battery']['sha256'], u.SAVE_SHA256)
        self.assertEqual(evidence['inputs']['quicksave']['sha256'], u.STATE_SHA256)
        self.assertEqual(evidence['inputs']['core_sha256'], u.rowe.CORE_SHA256)
        current_observer = u.rowe.digest(Path(u.__file__).read_bytes())
        if evidence['inputs']['observer_sha256'] != current_observer:
            self.assertEqual(evidence['inputs']['observer_sha256'],
                             '0c57a1bdc98666a808228c8b708cae04b686a4acf0d1e0c50a1745614b9c474a')
            battle = json.loads((Path(u.__file__).parent/'evidence/unbound-runtime-battle.json').read_text())
            self.assertEqual(evidence['revalidated'], dict(
                observer_sha256=current_observer,
                private_report_sha256=battle['regression_of_attempt3_route']['private_report_sha256'],
                scope='battery route only; quicksave not rerun', evidence='unbound-runtime-battle.json'))
        self.assertTrue(evidence['battery_run']['all_observations_same_player_sha256'])
        natures = ('Hardy Lonely Brave Adamant Naughty Bold Docile Relaxed Impish Lax Timid Hasty Serious '
                   'Jolly Naive Modest Mild Quiet Bashful Rash Calm Gentle Sassy Careful Quirky').split()
        slots = evidence['party']
        self.assertEqual(len(slots), evidence['battery_run']['timeline']['s1_info']['player_count'])
        self.assertGreaterEqual(len(slots), 2)
        for slot in slots:
            ui, raw, native = slot['ui_transcription'], slot['raw_selected'], slot['production_native']
            with self.subTest(slot=slot['slot']):
                # Recomputed here from raw fields, independent of the stored 'checks'.
                self.assertEqual(raw['checksum_word'], '0x0000')
                self.assertNotIn(raw['checksum_word'], (raw['raw_sum16'], raw['xor_sum16']))
                self.assertEqual(ui['level'], raw['level_byte'])
                self.assertEqual(ui['hp'], [raw['hp_word'], raw['stats_words']['max_hp']])
                for name in ('attack', 'defense', 'sp_attack', 'sp_defense', 'speed'):
                    self.assertEqual(ui[name], raw['stats_words'][name])
                self.assertEqual(ui['exp'], raw['experience_word'])
                self.assertEqual([m[1] for m in ui['moves']], raw['pp_bytes'])
                self.assertEqual(ui['nature'], natures[raw['pid_mod25']])
                self.assertEqual(ui['id_no'], int(raw['otid'], 16) & 0xffff)
                self.assertEqual(bytes.fromhex(raw['nickname_hex']).split(b'\xff')[0],
                                 bytes(0xbb + ord(c) - 65 if c.isupper() else 0xd5 + ord(c) - 97
                                       for c in ui['nickname']))
                self.assertEqual(native['species'], int(raw['species_word'], 16) & 0x7ff)
                self.assertTrue(native['accepted'])
        # Sanitized: selected fields only; no 100-byte record or longer raw hex blobs.
        def strings(node):
            if isinstance(node, dict):
                for value in node.values(): yield from strings(value)
            elif isinstance(node, list):
                for value in node: yield from strings(value)
            elif isinstance(node, str):
                yield node
        self.assertFalse([t for t in strings(evidence) if len(t) > 64 and all(c in '0123456789abcdef' for c in t)])

    def test_identity_requires_both_length_and_hash(self):
        raw = b'invented'
        self.assertEqual(u.pinned(raw, u.rowe.digest(raw), 8, 'test')['bytes'], 8)
        for sha, size in [('0'*64, 8), (u.rowe.digest(raw), 9)]:
            with self.assertRaisesRegex(ValueError, 'identity mismatch'): u.pinned(raw, sha, size, 'test')

    def test_bad_core_or_bootstrap_is_refused_before_emulation(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for filename, data in [('rom', b'invented'), ('core', b'invented'),
                                   ('movie', b'observe first'), ('battery', b'wrong save')]:
                (root/filename).write_bytes(data)
            argv = ['unbound_observe', '--rom', str(root/'rom'), '--core', str(root/'core'),
                    '--movie', str(root/'movie'), '--battery-save', str(root/'battery'),
                    '--private-dir', str(root/'private'), '--output', str(root/'out.json')]
            original_pin = u.pinned
            def pin(data, sha, size, label):
                return {} if label == 'ROM' else original_pin(data, sha, size, label)
            for core_pin, expected in [('0'*64, 'core identity mismatch'),
                                       (u.rowe.digest(b'invented'), 'save identity mismatch')]:
                with patch.object(sys, 'argv', argv), patch.object(u, 'pinned', side_effect=pin), \
                     patch.object(u, 'inspect_rom', return_value={}), \
                     patch.object(u.rowe, 'CORE_SHA256', core_pin), \
                     patch.object(u.rowe, 'verify_adapter', return_value={}), \
                     patch.object(u.rowe, 'require_private_directory'), patch.object(u, 'Observer') as host:
                    self.assertEqual(u.main(), 1)
                    host.assert_not_called()
                self.assertIn(expected, json.loads((root/'out.json').read_text())['error'])
                self.assertFalse((root/'private').exists())

    def test_constructor_cleans_up_partial_host_initialization(self):
        with patch.object(u.rowe, 'Observer', side_effect=ValueError('ROM load failed')), \
             patch.object(u.C, 'CDLL') as library:
            with self.assertRaisesRegex(ValueError, 'ROM load failed'): u.Observer('adapter', 'core', 'rom')
            library.return_value.libretro_host_cleanup.assert_called_once()

    def test_cli_wrong_rom_invalidates_stale_success_before_core(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); rom = root/'rom'; output = root/'out.json'
            rom.write_bytes(b'invented'); output.write_text('{"status":"OBSERVED"}')
            command = [sys.executable, str(Path(u.__file__)), '--rom', str(rom), '--core', 'missing',
                       '--movie', 'missing', '--battery-save', 'missing', '--private-dir', str(root/'private'),
                       '--output', str(output)]
            result = subprocess.run(command, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 1)
            self.assertIn('ROM identity mismatch', result.stderr)
            self.assertEqual(json.loads(output.read_text())['status'], 'FAIL')
            self.assertFalse((root/'private').exists())
            output.unlink(); output.hardlink_to(rom)
            result = subprocess.run(command, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(rom.read_bytes(), b'invented')

    def test_late_failure_clears_observations_and_cleans_up(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            output = root/'out.json'
            argv = ['unbound_observe', '--rom', str(root/'rom'), '--core', str(root/'core'),
                    '--movie', str(root/'movie'), '--battery-save', str(root/'battery'),
                    '--private-dir', str(root/'private'), '--output', str(output)]
            host = Mock()
            host.engine.host.libretro_host_load_save_ram.return_value = True
            host.observe.side_effect = [{'label': 'first'}, ValueError('incoherent snapshot')]
            def read(path, limit):
                return b'observe first\nobserve second' if Path(path).name == 'movie' else b'invented'
            with patch.object(sys, 'argv', argv), patch.object(u, 'read_local', side_effect=read), \
                 patch.object(u, 'pinned', return_value={}), patch.object(u, 'inspect_rom', return_value={}), \
                 patch.object(u.rowe, 'CORE_SHA256', u.rowe.digest(b'invented')), \
                 patch.object(u.rowe, 'verify_adapter', return_value={}), patch.object(u, 'Observer', return_value=host), \
                 patch.object(u.rowe, 'require_private_directory') as guard:
                self.assertEqual(u.main(), 1)
                guard.assert_called_once_with(root/'private')
            result = json.loads(output.read_text())
            self.assertEqual((result['status'], result['observations']), ('FAIL', []))
            host.engine.host.libretro_host_cleanup.assert_called_once()

    def test_static_inspection_refuses_wrong_rom_and_changed_span(self):
        with self.assertRaisesRegex(ValueError, 'ROM identity mismatch'): u.inspect_rom(b'invented')
        with patch.object(u, 'pinned', return_value={}):
            with self.assertRaisesRegex(ValueError, 'ROM span mismatch'): u.inspect_rom(bytes(0x41000))

    def test_committed_static_manifest_has_no_runtime_claim(self):
        evidence = json.loads((Path(u.__file__).parent/'evidence/unbound-runtime-static.json').read_text())
        self.assertEqual(evidence['runtime_status'], 'SEE_RUNTIME_OBSERVATION')
        self.assertEqual(evidence['static']['runtime_layout'], 'NOT_VERIFIED')
        self.assertEqual(evidence['static']['rom']['sha256'], u.ROM_SHA256)
        self.assertEqual(evidence['static']['player_stride_bytes'], 100)
        current_observer = u.rowe.digest(Path(u.__file__).read_bytes())
        if evidence['observer_sha256'] != current_observer:
            self.assertEqual(evidence['observer_sha256'],
                             '0c57a1bdc98666a808228c8b708cae04b686a4acf0d1e0c50a1745614b9c474a')
            self.assertEqual(evidence['static_provenance']['battle_evidence'], 'unbound-runtime-battle.json')
            battle = json.loads((Path(u.__file__).parent/'evidence/unbound-runtime-battle.json').read_text())
            self.assertEqual(battle['inputs']['observer_sha256'], current_observer)
        self.assertEqual([s['sha256'] for s in evidence['static']['spans']], [s[2] for s in u.STATIC_SPANS])

    def test_private_output_guard_and_exclusive_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); repo = root/'repo'; repo.mkdir(); (repo/'.git').write_text('gitdir: invented')
            with self.assertRaises(ValueError): u.rowe.require_private_directory(repo/'screens')
            link = root/'alias'; link.symlink_to(repo)
            with self.assertRaises(ValueError): u.rowe.require_private_directory(link/'screens')
            private = root/'private'; private.mkdir()
            output = root/'out.json'
            argv = ['unbound_observe', '--rom', str(root/'rom'), '--core', str(root/'core'),
                    '--movie', str(root/'movie'), '--battery-save', str(root/'battery'),
                    '--private-dir', str(private), '--output', str(output)]
            def read(path, limit):
                return b'observe first' if Path(path).name == 'movie' else b'invented'
            with patch.object(sys, 'argv', argv), patch.object(u, 'read_local', side_effect=read), \
                 patch.object(u, 'pinned', return_value={}), patch.object(u, 'inspect_rom', return_value={}), \
                 patch.object(u.rowe, 'CORE_SHA256', u.rowe.digest(b'invented')), \
                 patch.object(u.rowe, 'verify_adapter', return_value={}), \
                 patch.object(u.rowe, 'require_private_directory') as guard, patch.object(u, 'Observer') as host:
                self.assertEqual(u.main(), 1)
                guard.assert_called_once_with(private)
                host.assert_not_called()
            result = json.loads(output.read_text())
            self.assertEqual((result['status'], result['observations']), ('FAIL', []))
            self.assertIn('File exists', result['error'])
            self.assertFalse((private/'cartridge.gba').exists())
            self.assertEqual(list(private.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
