#!/usr/bin/env python3
"""ROM-free regressions: literal address use is not battle-symbol identity."""
import contextlib
import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import audit_vanilla_layout as audit


class RetailAuditTest(unittest.TestCase):
    def setUp(self):
        self.config = audit.reader_config('EMERALD')
        self.profile = audit.load_profile('vanilla_emerald.json')
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'synthetic.bin'
        self.parties = [self.config['player_party_offset'], self.config['enemy_party_offset']]

    def write_literals(self, offsets):
        # Synthetic address words only, never cartridge data. The real hash/read/scan path runs.
        data = bytes(audit.ROM_BODY_START) + b''.join(
            (audit.EWRAM_BASE + offset).to_bytes(4, 'little') for offset in offsets)
        self.path.write_bytes(data)
        self.profile['sha256Hashes'] = [hashlib.sha256(data).hexdigest()]

    def check(self):
        return audit.check_retail_rom('Emerald (synthetic)', str(self.path), self.profile, self.config)

    def test_known_wrong_emerald_literal_cannot_pass_cli(self):
        self.assertEqual(audit.EWRAM_BASE + self.config['battle_mons_offset'], 0x02024064)
        for count in (1, 1147):
            with self.subTest(literal_count=count):
                self.write_literals(self.parties + [self.config['battle_mons_offset']] * count)
                output = io.StringIO()
                with patch.object(audit, 'load_profile', return_value=self.profile), \
                        contextlib.redirect_stdout(output):
                    result = audit.main(['--emerald-rom', str(self.path)])
                report = output.getvalue()
                self.assertEqual(result, 1)
                self.assertIn('[OK] Emerald (retail): positive control gPlayerParty', report)
                self.assertIn('[OK] Emerald (retail): positive control gEnemyParty', report)
                self.assertIn('[UNPROVEN] Emerald (retail): battle_mons_offset', report)
                self.assertIn(f'0x02024064 appears as a literal at {count} ROM offset(s)', report)
                self.assertIn('Independent symbol evidence is required', report)
                self.assertNotIn('[OK] Emerald (retail): battle_mons_offset', report)

    def test_missing_battle_literal_remains_unproven(self):
        self.write_literals(self.parties)
        notes, problems = self.check()
        self.assertEqual(sum('positive control' in note for note in notes), 2)
        self.assertEqual(len(problems), 1)
        self.assertIn('no literal', problems[0])
        self.assertIn('UNPROVEN', problems[0])

    def test_other_battle_literals_cannot_gain_symbol_identity(self):
        fields = ('battlers_count_offset', 'battle_type_flags_offset', 'battle_outcome_offset',
                  'battler_party_indexes_offset', 'battler_positions_offset',
                  'absent_battler_flags_offset', 'side_statuses_offset')
        for i, field in enumerate(fields):
            self.config[field] = 0x25000 + 4*i  # synthetic candidates, not proposed offsets
        self.write_literals(self.parties + [self.config['battle_mons_offset']] +
                            [self.config[field] for field in fields])
        _, problems = self.check()
        self.assertEqual(len(problems), 1 + len(fields))
        for problem in problems:
            self.assertIn('appears as a literal', problem)
            self.assertIn('UNPROVEN', problem)

    def test_wrong_identity_fails_before_literal_scan(self):
        self.write_literals(self.parties + [self.config['battle_mons_offset']])
        self.profile['sha256Hashes'] = []
        with patch.object(audit, 'find_literal_references') as scan:
            notes, problems = self.check()
        scan.assert_not_called()
        self.assertEqual(notes, [])
        self.assertEqual(len(problems), 1)
        self.assertIn('profile does not accept', problems[0])


if __name__ == '__main__':
    unittest.main(verbosity=2)
