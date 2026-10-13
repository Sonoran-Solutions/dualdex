import struct
import subprocess
import sys
import tempfile
import hashlib
import json
from pathlib import Path
import unittest
from unittest.mock import patch
from rowe_observe import Observer, parse_movie, slot_fields, verify_adapter


class ObserveTest(unittest.TestCase):
    def test_synthetic_extraction_is_not_a_game_validity_verdict(self):
        data = bytearray(76)
        struct.pack_into('<II', data, 0, 0x12345678, 0x87654321)
        data[8:20] = b'ABCDEFGHIJKL'
        struct.pack_into('<III', data, 20, (1 << 31) | (12345 << 10) | 901,
                         (0xabc << 20) | (777 << 10) | 666,
                         (17 << 27) | (999 << 17) | (29 << 12) | 4001)
        data[39], data[52:56], data[60] = 0xfc, bytes([0, 127, 128, 255]), 255
        struct.pack_into('<7H', data, 62, 0, 500, 101, 102, 103, 104, 105)
        row = slot_fields(data)
        self.assertEqual((row['species'], row['form'], row['moves']), (4001, 29, [901, 666, 777, 999]))
        self.assertEqual((row['pid'], row['otid']), (0x12345678, 0x87654321))
        self.assertEqual((row['pp'], row['nature'], row['level'], row['hp']), ([0,127,128,255],31,255,0))
        self.assertEqual(row['nickname_hex'], b'ABCDEFGHIJKL'.hex())
        self.assertEqual(row['experience'], 12345)
        self.assertEqual(row['status'], 0)
        self.assertEqual(row['stats'], dict(max_hp=500, attack=101, defense=102, speed=103,
                                           sp_attack=104, sp_defense=105))
        for size in list(range(76)) + [77,100]:
            with self.assertRaises(ValueError):
                slot_fields(bytes(size))
        struct.pack_into('<I', data, 20, 0xffffffff)
        struct.pack_into('<I', data, 56, 0xfedcba98)
        struct.pack_into('<H', data, 62, 0xffff)
        boundary = slot_fields(data)
        self.assertEqual((boundary['experience'], boundary['moves'][0]), (0x1fffff, 1023))
        self.assertEqual((boundary['status'], boundary['hp']), (0xfedcba98, 65535))
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

    def test_snapshot_count_coherence_permutation_and_stale_tail(self):
        host = Observer.__new__(Observer)
        host.symbols = {'gPlayerParty': 0x02025128, 'gPlayerPartyCount': 0x02025125}
        a, b, stale = bytearray(76), bytearray(76), bytearray(76)
        for record, pid, species in [(a,0xabcdef01,661),(b,0x76543210,161),(stale,123,999)]:
            struct.pack_into('<I', record, 0, pid)
            struct.pack_into('<I', record, 28, species)
        data = bytes(a+b+stale+bytearray(228))
        host.read = unittest.mock.Mock(side_effect=[b'\x02',data,b'\x02',data])
        raw, before = host.party_snapshot('Player')
        self.assertEqual(len(raw),456)
        self.assertEqual([s['pid'] for s in before['slots']],[0xabcdef01,0x76543210])
        self.assertEqual(before['unused_species'],[999,0,0,0])
        swapped = bytes(b+a+stale+bytearray(228))
        host.read = unittest.mock.Mock(side_effect=[b'\x02',swapped,b'\x02',swapped])
        after = host.party_snapshot('Player')[1]
        self.assertEqual(after['slot_sha256'][:2], before['slot_sha256'][:2][::-1])
        for reads in [[b'\x07'],[b'\x02',data,b'\x01'],[b'\x02',data,b'\x02',swapped]]:
            host.read = unittest.mock.Mock(side_effect=reads)
            with self.assertRaises(ValueError):host.party_snapshot('Player')
        host.read = unittest.mock.Mock(side_effect=[b'\x00',data,b'\x00',data])
        self.assertEqual(host.party_snapshot('Player')[1]['slots'],[])

    def test_adapter_pin_fails_closed(self):
        import rowe_observe
        with tempfile.TemporaryDirectory() as folder:
            library, pin = Path(folder)/'adapter.bin',Path(folder)/'pin.json'
            library.write_bytes(b'invented adapter')
            expected = dict(binary_sha256=hashlib.sha256(library.read_bytes()).hexdigest(),inputs={})
            pin.write_text(json.dumps(expected))
            with patch.object(rowe_observe,'ADAPTER_PIN',pin):
                self.assertEqual(verify_adapter(library),expected)
                library.write_bytes(b'changed')
                with self.assertRaisesRegex(ValueError,'identity mismatch'):verify_adapter(library)
                library.write_bytes(b'invented adapter')
                expected['inputs']={'README.md':'0'*64}
                pin.write_text(json.dumps(expected))
                with self.assertRaisesRegex(ValueError,'source mismatch'):verify_adapter(library)
        self.assertEqual(parse_movie('cartridge_save\nobserve saved')[0],('cartridge_save',))
        with self.assertRaises(ValueError):parse_movie('cartridge_save personal.sav\nobserve bad')

    def test_committed_observation_provenance_and_ui_correlations(self):
        here = Path(__file__).resolve().parent
        evidence = json.loads((here / 'evidence/runtime-observations.json').read_text())
        self.assertEqual(evidence['full_layout'], 'NOT_VERIFIED')
        for run in evidence['runs']:
            movie = (here / 'rowe-starter.movie').read_text().replace(
                'run 180\n', f"run {run['first_run_frames']}\n", 1).encode()
            self.assertEqual(hashlib.sha256(movie).hexdigest(), run['movie_sha256'])
            # Revision-2 captures retain the original observer hash at the reviewed parent.
            self.assertEqual(run['observer_sha256'],
                             '056a9143a54bd5dde6946a70aabb04870cb6f75573ada0ac5deb59cd22f9cec3')
            for filename, key in [('rowe_symbols.py','symbols_tool_sha256')]:
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

    def test_revision3_captures_ui_permutation_persistence_and_mutation(self):
        here=Path(__file__).resolve().parent
        evidence=json.loads((here/'evidence/party-observations-v3.json').read_text())
        static=json.loads((here/'evidence/party-accessors-v3.json').read_text())
        self.assertEqual(static['inspector_sha256'],hashlib.sha256((here/'rowe_static.py').read_bytes()).hexdigest())
        self.assertEqual((static['count']['width'],static['stride']['bytes']),(8,76))
        self.assertEqual(static['fields']['species']['width'],12)
        self.assertEqual(static['fields']['experience']['width'],21)
        runs={r['id']:r for r in evidence['runs']}
        for run in runs.values():
            self.assertEqual(run['status'],'OBSERVED')
            self.assertEqual(run['runtime_layout'],'NOT_VERIFIED')
            for filename,key in [(run['movie'],'movie_sha256'),('rowe_observe.py','observer_sha256')]:
                self.assertEqual(run[key],hashlib.sha256((here/filename).read_bytes()).hexdigest())
            for o in run['observations']:
                for party in o['parties'].values():
                    self.assertEqual(party['count'],party['count_after'])
                    self.assertEqual(len(party['slots']),party['count'])
                    self.assertEqual(len(party['slot_sha256']),6)
        first=runs['cartridge-save-final']
        for name in ['cartridge-save-final','cartridge-save-final2']:
            rows={o['label']:o for o in runs[name]['observations']}
            before,after=[rows[label]['parties']['player'] for label in ['roster_before','roster_after']]
            self.assertEqual(before['count'],2)
            self.assertEqual(after['slot_sha256'][:2],before['slot_sha256'][:2][::-1])
            saved=rows['post_game_save']
            self.assertEqual(saved['reported_save_party_count'],2)
            self.assertEqual(saved['reported_save_party_copy_sha256'],saved['parties']['player']['sha256'])
            self.assertNotEqual(rows['pre_game_save']['save_blocks'],saved['save_blocks'])
        reload=runs['cartridge-reload-ui']
        self.assertEqual(reload['bootstrap'],'CARTRIDGE_RELOAD')
        self.assertNotIn('initial_state_sha256',reload)
        self.assertEqual(reload['loaded_cartridge_sha256'],first['saved_cartridge_sha256'])
        final={o['label']:o for o in reload['observations']}
        saved=first['observations'][-1]['parties']['player']
        self.assertEqual(saved['sha256'],final['reloaded_roster']['parties']['player']['sha256'])
        for slot,ui in zip(saved['slots'],evidence['ui_transcription']):
            self.assertEqual((slot['species'],slot['level'],slot['otid']&65535),(ui['dex'],ui['level'],ui['trainer_id']))
            self.assertEqual([slot['hp'],slot['stats']['max_hp']],ui['hp'])
            self.assertEqual(slot['moves'],ui['move_ids'])
            self.assertEqual(slot['pp'],ui['pp'])
            self.assertEqual(slot['nature'],ui['nature_raw'])
            self.assertEqual([slot['stats'][k] for k in ['attack','defense','speed','sp_attack','sp_defense']],ui['stats_attack_defense_speed_spattack_spdefense'])
        battle={o['label']:o for o in runs['battle-heal']['observations']}
        before,after,healed=[battle[label]['parties']['player']['slots'][0] for label in ['before_turn','after_turn','healed']]
        self.assertEqual([before['hp'],after['hp'],healed['hp']],[12,9,19])
        self.assertEqual([before['pp'][0],after['pp'][0],healed['pp'][0]],[33,32,32])
        self.assertEqual(battle['after_turn']['parties']['enemy']['count'],0)
        self.assertEqual(battle['after_turn']['parties']['enemy']['unused_species'][0],661)
        for fail in evidence['failed_scenarios']:
            if 'movie_text' in fail:
                self.assertEqual(hashlib.sha256(fail['movie_text'].encode()).hexdigest(),fail['movie_sha256'])

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
            output.unlink()
            output.hardlink_to(rom)
            command[-1] = str(output)
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
