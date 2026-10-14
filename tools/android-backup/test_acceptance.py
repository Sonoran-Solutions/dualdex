"""Small host checks for the acceptance runner's destructive-operation boundaries."""
import unittest
from acceptance import PACKAGE, backup_result, require_disposable, restore_tokens


class AcceptanceBoundaryTest(unittest.TestCase):
    def test_only_explicit_matching_disposable_avd_can_reset(self):
        require_disposable('emulator-5584', True, 'dualdex194-retention', 'dualdex194-retention')
        require_disposable('emulator-5584', True, 'disposable', 'disposable', True, True)
        for args in [('thor', True, 'test', 'test'), ('emulator-5584', False, 'test', 'test'),
                     ('emulator-5584', True, None, 'test'), ('emulator-5584', True, 'test', 'other'),
                     ('emulator-5584', True, 'test', 'test', True, False)]:
            with self.subTest(args=args), self.assertRaises(ValueError): require_disposable(*args)

    def test_package_rejection_is_separate_from_aggregate_success(self):
        text=f'Package {PACKAGE} with result: Rejected\nBackup finished with result: Success\n'
        self.assertEqual(backup_result(text), {'package':'Rejected', 'aggregate':'Success'})
        for invalid in ['', 'Backup finished with result: Success\n', text+text]:
            with self.subTest(text=invalid), self.assertRaises(RuntimeError): backup_result(invalid)

    def test_restore_token_must_be_unambiguous(self):
        self.assertEqual(restore_tokens('  1 : Local disk image\n'), '1')
        for invalid in ['', 'No restore sets', '  1 : current\n  2 : old\n']:
            with self.subTest(text=invalid), self.assertRaises(RuntimeError): restore_tokens(invalid)


if __name__ == '__main__': unittest.main()
