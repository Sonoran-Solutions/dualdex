import struct
import subprocess
import sys
import tempfile
import hashlib
import json
from pathlib import Path
import unittest
from rowe_observe import Observer, parse_movie, slot_fields


class ObserveTest(unittest.TestCase):
    def test_synthetic_extraction_is_not_a_game_validity_verdict(self):
        data = bytearray(76)
        struct.pack_into('<II', data, 0, 0x12345678, 0x87654321)
        data[8:20] = b'ABCDEFGHIJKL'
        struct.pack_into('<III', data, 20, (12345 << 10) | 901,
                         (0xabc << 20) | (777 << 10) | 666,
                         (17 << 27) | (999 << 17) | (29 << 12) | 4001)
        data[39], data[52:56], data[60] = 0xfc, bytes([0, 127, 128, 255]), 255
        struct.pack_into('<7H', data, 62, 0, 500, 101, 102, 103, 104, 105)
        row = slot_fields(data)
        self.assertEqual((row['species'], row['form'], row['moves']), (4001, 29, [901, 666, 777, 999]))
        self.assertEqual((row['pid'], row['otid']), (0x12345678, 0x87654321))
        self.assertEqual((row['pp'], row['nature'], row['level'], row['hp']), ([0,127,128,255],31,255,0))
        self.assertEqual(row['nickname_hex'], b'ABCDEFGHIJKL'.hex())
        self.assertEqual(row['stats'], dict(max_hp=500, attack=101, defense=102, speed=103,
                                           sp_attack=104, sp_defense=105))
        for size in list(range(76)) + [77,100]:
            with self.assertRaises(ValueError):
                slot_fields(bytes(size))
        # This is raw measurement, deliberately not the PR #189 plausibility decoder.
        self.assertEqual(slot_fields(bytes(76))['species'], 0)

    def test_bounded_input_grammar(self):
        self.assertEqual(parse_movie('run 10\nmash 1\nsave\nrestore\nobserve first'),
                         [('run',10,0),('mash',1,256),('save',),('restore',),('observe','first')])
        for text in ['run 0', 'run 100001', 'mash 6000', 'run 1 16384', 'write 0 1',
                     'restore', 'save path', 'observe ../../escape', 'observe a\nobserve a',
                     'run 1 1 1', 'observe é', 'run -1', 'run 1']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse_movie(text)

    def test_committed_observation_provenance_and_ui_correlations(self):
        here = Path(__file__).resolve().parent
        evidence = json.loads((here / 'evidence/runtime-observations.json').read_text())
        self.assertEqual(evidence['full_layout'], 'NOT_VERIFIED')
        for run in evidence['runs']:
            movie = (here / 'rowe-starter.movie').read_text().replace(
                'run 180\n', f"run {run['first_run_frames']}\n", 1).encode()
            self.assertEqual(hashlib.sha256(movie).hexdigest(), run['movie_sha256'])
            for filename, key in [('rowe_observe.py','observer_sha256'),
                                  ('rowe_symbols.py','symbols_tool_sha256')]:
                self.assertEqual(hashlib.sha256((here / filename).read_bytes()).hexdigest(), run[key])
            slot, ui = run['slot_zero_at_info'], run['ui_transcription']
            self.assertEqual(slot['species'], ui['dex'])
            self.assertEqual(slot['level'], ui['level'])
            self.assertEqual(slot['otid'] & 65535, ui['trainer_id'])
            self.assertEqual([slot['hp'],slot['stats']['max_hp']], ui['hp'])
            self.assertEqual([slot['stats'][k] for k in
                              ('attack','defense','speed','sp_attack','sp_defense')],
                             ui['stats_attack_defense_speed_spattack_spdefense'])
            self.assertEqual(slot['pp'], ui['pp'])
            rows = {r['label']:r for r in run['checkpoints']}
            self.assertEqual(rows['boot']['player_count'], 0)
            self.assertEqual(rows['info']['player_count'], 1)
            self.assertTrue(run['restored_parties_identical'])
            self.assertEqual(rows['moves']['player_party_sha256'],
                             rows['restored_moves']['player_party_sha256'])
            if ui['species'] == 'Throh':
                for label, order in [('after_move12_input',[1,0,2,3]),
                                     ('after_move24_input',[1,3,2,0]),
                                     ('after_move43_input',[1,3,0,2])]:
                    for field in ('moves','pp'):
                        self.assertEqual(rows[label][field], [slot[field][i] for i in order])

    def test_cli_wrong_rom_fails_before_core_and_output_cannot_destroy_input(self):
        tool = Path(__file__).with_name('rowe_observe.py')
        with tempfile.TemporaryDirectory() as folder:
            rom, output = Path(folder) / 'invented.bin', Path(folder) / 'result.json'
            rom.write_bytes(b'invented, not a ROM')
            output.write_text('{"status":"OBSERVED"}')
            command = [sys.executable,str(tool),'--rom',str(rom),'--core','missing-core',
                       '--movie','missing-movie','--output',str(output)]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(result.returncode, 1)
            self.assertIn('ROM identity mismatch', result.stderr)
            self.assertEqual(json.loads(output.read_text())['status'], 'FAIL')
            command[-1] = str(rom)
            self.assertEqual(subprocess.run(command, capture_output=True).returncode, 2)
            self.assertEqual(rom.read_bytes(), b'invented, not a ROM')

    def test_reads_refuse_before_calling_core(self):
        host = Observer.__new__(Observer)
        for address, size in [(0x02000000,0),(0x02000000,457),(0x0203ffff,2),
                              (0x03007fff,2),(0x08000000,76)]:
            with self.assertRaises(ValueError):
                host.read(address,size)


if __name__ == '__main__':
    unittest.main()
