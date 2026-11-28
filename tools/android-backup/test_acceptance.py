"""Small host checks for the acceptance runner's destructive-operation boundaries."""
import types
import unittest
from acceptance import (
    PACKAGE,
    backup_result,
    local_archive_path,
    logcat_delta,
    require_disposable,
    restore_tokens,
    validate_args,
    verify_acceptance_manifest,
    verify_intended_refusal,
)


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
        self.assertEqual(local_archive_path('1'), '/data/user/0/com.android.localtransport/files/1/_full/' + PACKAGE)
        for token in ['2', 'a123', '../1', '01', '']:
            with self.subTest(token=token), self.assertRaises(RuntimeError): local_archive_path(token)
        for invalid in ['', 'No restore sets', '  1 : current\n  2 : old\n']:
            with self.subTest(text=invalid), self.assertRaises(RuntimeError): restore_tokens(invalid)

    def test_d2d_retention_mode_is_rejected_before_device_operations(self):
        validate_args(types.SimpleNamespace(transport='d2d-test', baseline_only=True, retention_matrix=False))
        validate_args(types.SimpleNamespace(transport='local', baseline_only=False, retention_matrix=True))
        validate_args(types.SimpleNamespace(transport='local', baseline_only=False, retention_matrix=False))
        for bad in [
            types.SimpleNamespace(transport='d2d-test', baseline_only=False, retention_matrix=False),
            types.SimpleNamespace(transport='d2d-test', baseline_only=False, retention_matrix=True),
            types.SimpleNamespace(transport='local', baseline_only=True, retention_matrix=True),
        ]:
            with self.subTest(bad=bad), self.assertRaises(ValueError): validate_args(bad)

    def test_intended_refusal_requires_parsed_package_result_and_callback_evidence(self):
        refused_output = (
            f'Package {PACKAGE} with result: Transport rejected package because it wasn\'t able to process it at the time\n'
            'Backup finished with result: Success\n'
        )
        refusal_log = (
            'I/DualDexAcceptance( 100): callback onFullBackup flags=1 quota=26214400 call=1\n'
            'I/DualDexAcceptance( 100): callback onFullBackup aborted IOException: Essential save coverage unavailable\n'
        )
        parsed = verify_intended_refusal(refused_output, refusal_log)
        self.assertNotEqual(parsed['package'], 'Success')
        for bad_out, bad_log in [
            ('', refusal_log),
            ('Backup finished with result: Success\n', refusal_log),
            (f'Package {PACKAGE} with result: Success\nBackup finished with result: Success\n', refusal_log),
            (refused_output, ''),
            (refused_output, 'callback onFullBackup aborted IOException: Transport not verified\n'),
            (refused_output, refusal_log + 'I/DualDexAcceptance( 100): callback onFullBackup returned\n'),
        ]:
            with self.subTest(bad_out=bad_out, bad_log=bad_log), self.assertRaises(RuntimeError):
                verify_intended_refusal(bad_out, bad_log)

    def test_logcat_delta_extracts_only_new_lines(self):
        before = ['line1', 'line2']
        after = ['line1', 'line2', 'line3']
        self.assertEqual(logcat_delta(before, after), 'line3')
        with self.assertRaises(RuntimeError):
            logcat_delta(before, ['other', 'line2', 'line3'])

    def test_manifest_check_accepts_acceptance_apk_and_rejects_ordinary_manifest(self):
        valid = (
            f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PACKAGE}">'
            '<application android:testOnly="true" android:fullBackupOnly="true" '
            'android:backupAgent="com.dualdex.acceptance.ObservedBackupAgent" />'
            f'<instrumentation android:targetPackage="{PACKAGE}" />'
            '</manifest>'
        )
        verify_acceptance_manifest(valid)
        ordinary = (
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.dualdex">'
            '<application android:allowBackup="true" />'
            '</manifest>'
        )
        with self.assertRaises(AssertionError):
            verify_acceptance_manifest(ordinary)


if __name__ == '__main__': unittest.main()
