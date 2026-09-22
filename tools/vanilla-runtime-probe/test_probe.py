#!/usr/bin/env python3
"""ROM-free failure controls for the exact same runtime verdict functions."""
import copy
import json
import unittest
from unittest.mock import patch
import probe


class ProbeControls(unittest.TestCase):
    def setUp(self):
        self.row = probe.fixture('physical')
        self.hit = dict(preHP=235, postHP=176, damage=59, engineDamage=59,
                        attacker=0, target=1, critMultiplier=1, move=157)

    def test_positive_control(self):
        probe.validate_hit(self.row, self.hit)
        probe.fixture('special')

    def test_committed_runtime_records(self):
        records = list((probe.HERE / 'evidence').glob('*.json'))
        self.assertEqual(len(records), 4)
        for game, build in probe.BUILDS.items():
            profile = json.loads((probe.ROOT / 'app/src/main/assets/profiles' /
                                  (build['profile'] + '.json')).read_text())
            self.assertFalse(profile['battleStateReadVerified'])
            for case in probe.CASES:
                with self.subTest(game=game, case=case):
                    record = json.loads((probe.HERE / 'evidence' / f'{game}-{case}.json').read_text())
                    row = probe.fixture(case)
                    self.assertEqual(record['verdict'], 'PASS')
                    self.assertEqual(record['profile'], build['profile'])
                    self.assertEqual(record['romSHA256'], build['sha'])
                    self.assertIn(record['romSHA256'], profile['sha256Hashes'])
                    self.assertEqual(record['fixture'], row['id'])
                    self.assertEqual(record['request'], json.loads(row['request']))
                    self.assertEqual(record['expectedDamage'], row['expected']['damage'])
                    self.assertEqual(record['goldensSHA256'], probe.digest(probe.GOLDENS.read_bytes()))
                    self.assertEqual(record['probeSHA256'], probe.digest(probe.Path(probe.__file__).read_bytes()))
                    self.assertEqual(record['scenarioSHA256'], probe.digest(
                        (probe.HERE / 'scenarios' / f'{game}.txt').read_bytes()))
                    self.assertEqual(record['checkpoint']['menuSHA256'], build['menu'])
                    self.assertEqual(record['diagnosticBattleMonsAddress'], hex(0x02000000+build['base']))
                    probe.validate_hit(row, record['hit'])
                    for side, who in enumerate(('attacker', 'defender')):
                        p = record['request'][who]
                        stats = probe.oracle.raw_stats(p['species'], p['level'], p['ivs'], p['evs'])
                        mon = record['combatants'][side]
                        self.assertEqual(mon['stats'], dict(atk=stats['atk'], defense=stats['def'],
                            speed=stats['spe'], spa=stats['spa'], spd=stats['spd']))
                        self.assertEqual(mon['hp'], stats['hp'])
                        self.assertEqual(mon['maxHP'], stats['hp'])
                        self.assertEqual(mon['speciesId'], [68,143][side])
                        self.assertEqual(mon['abilityId'], [62,17][side])
                        self.assertEqual(mon['types'], [[1,1],[0,0]][side])
                        self.assertEqual(mon['moves'], [probe.CASES[case][1] if side == 0 else 150,0,0,0])
                        self.assertEqual(mon['stages'], [6]*8)
                        self.assertEqual(mon['level'], 50)
                        for neutral in ('personality', 'status1', 'status2', 'item'):
                            self.assertEqual(mon[neutral], 0)

    def test_wrong_rom_before_core(self):
        with self.assertRaisesRegex(ValueError, 'ROM identity mismatch'):
            probe.identity(b'wrong ROM', probe.BUILDS['firered'])

    def test_profile_hash_is_required_even_for_known_digest(self):
        with patch.object(probe, 'digest', return_value=probe.BUILDS['firered']['sha']), \
             patch.object(probe.Path, 'read_text', return_value=json.dumps(
                 {'sha256Hashes': [], 'battleStateReadVerified': False})), \
             self.assertRaisesRegex(ValueError, 'ROM identity mismatch'):
            probe.identity(b'', probe.BUILDS['firered'])

    def test_wrong_expected_vector(self):
        row = copy.deepcopy(self.row)
        row['expected']['damage'] = [58]*16
        with self.assertRaisesRegex(ValueError, 'outside committed vector'):
            probe.validate_hit(row, self.hit)

    def test_changed_golden_roll_detected_even_when_hit_still_in_range(self):
        document = json.loads(probe.GOLDENS.read_text())
        row = next(r for r in document['fixtures'] if r['id'] == self.row['id'])
        row['expected']['damage'][0] += 1
        with self.assertRaisesRegex(ValueError, 'independent oracle'):
            probe.fixture('physical', document)

    def test_altered_oracle(self):
        def altered(request):
            result = probe.oracle.calculate(request)
            result['damage'] = [x + 1 for x in result['damage']]
            return result
        with self.assertRaisesRegex(ValueError, 'independent oracle'):
            probe.fixture('physical', calculate=altered)

    def test_changed_request(self):
        document = json.loads(probe.GOLDENS.read_text())
        row = next(r for r in document['fixtures'] if r['id'] == self.row['id'])
        request = json.loads(row['request'])
        request['attacker']['level'] = 51
        row['request'] = json.dumps(request)
        with self.assertRaisesRegex(ValueError, 'fixture operands'):
            probe.fixture('physical', document)

    def test_hp_damage_and_attribution_mutations(self):
        for field, wrong in [('postHP',235), ('postHP',0), ('damage',58),
                             ('engineDamage',58), ('attacker',1), ('target',0),
                             ('critMultiplier',2), ('move',33)]:
            with self.subTest(field=field, value=wrong), self.assertRaises(ValueError):
                probe.validate_hit(self.row, dict(self.hit, **{field: wrong}))

    def test_timeout(self):
        runtime = object.__new__(probe.Runtime)
        runtime.build = probe.BUILDS['firered']
        with patch.object(runtime, 'run'), patch.object(runtime, 'value', return_value=235), \
             self.assertRaisesRegex(ValueError, 'frame budget'):
            runtime.observe([], budget=2)

    def test_unknown_script_command(self):
        runtime = object.__new__(probe.Runtime)
        with patch.object(probe.Path, 'read_text', return_value='typo 100'), \
             self.assertRaisesRegex(ValueError, 'unknown scenario command'):
            runtime.route(probe.Path('unused'))

    def test_writes_sealed(self):
        runtime = object.__new__(probe.Runtime)
        runtime.sealed = True
        with self.assertRaisesRegex(ValueError, 'writes sealed'):
            runtime.stage('physical')


if __name__ == '__main__':
    unittest.main(verbosity=2)
