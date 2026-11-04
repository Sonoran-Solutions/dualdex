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
            return True
        host.pokemon_parse_single.side_effect = parse
        row = u.native_fields(host, invented_slot())
        self.assertEqual((row['species'], row['held_item'], row['moves']), (777, 1023, [1, 2, 3, 4]))
        self.assertEqual((row['hp'], row['stats']['max_hp'], row['ivs'][0], row['evs'][5]), (30, 99, 31, 255))

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
        self.assertEqual(evidence['runtime_status'], 'BLOCKED')
        self.assertEqual(evidence['static']['runtime_layout'], 'NOT_VERIFIED')
        self.assertEqual(evidence['static']['rom']['sha256'], u.ROM_SHA256)
        self.assertEqual(evidence['static']['player_stride_bytes'], 100)
        self.assertEqual(evidence['observer_sha256'], u.rowe.digest(Path(u.__file__).read_bytes()))
        self.assertEqual([s['sha256'] for s in evidence['static']['spans']], [s[2] for s in u.STATIC_SPANS])

    def test_private_output_guard_and_exclusive_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); repo = root/'repo'; repo.mkdir(); (repo/'.git').write_text('gitdir: invented')
            with self.assertRaises(ValueError): u.rowe.require_private_directory(repo/'screens')
            link = root/'alias'; link.symlink_to(repo)
            with self.assertRaises(ValueError): u.rowe.require_private_directory(link/'screens')
            private = root/'private'; private.mkdir()
            with self.assertRaises(FileExistsError): private.mkdir(exist_ok=False)


if __name__ == '__main__':
    unittest.main()
