import contextlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

import rom_observe as ro

EW, IW = ro.REGIONS['ewram'], ro.REGIONS['iwram']


class FakeHost:
    """In-memory stand-in for the libretro host: RAM is a function of frame count + boot seed."""

    def __init__(self, drift_ewram=(), drift_iwram=()):
        self.frame, self.mask, self.calls, self.states = 0, 0, [], {}
        self.drift_ewram, self.drift_iwram = drift_ewram, drift_iwram

    def ram(self, address, size):
        out = bytearray(size)
        for i in range(size):
            a = address + i
            out[i] = (self.frame * 7 + 1) % 251 if a in self.drift_ewram or a in self.drift_iwram else 0x5A
        return out

    def libretro_host_read_gba_address(self, address, buffer, size):
        buffer[:size] = bytes(self.ram(address, size))
        return True

    def libretro_host_set_input_buttons(self, mask):
        self.mask = mask

    def libretro_host_step_frame(self):
        self.frame += 1

    def libretro_host_save_state(self, path):
        self.states[path] = self.frame
        return True

    def libretro_host_load_state(self, path):
        self.frame = self.states[path]
        return True

    def libretro_host_cleanup(self):
        self.calls.append('cleanup')


def boot(a, b=None):
    """Synthetic boot dump: ewram of 0x40000 zero bytes with the given byte changes {offset: value}."""
    ewram, iwram = bytearray(EW[1]), bytearray(IW[1])
    for offset, value in a.items():
        ewram[offset] = value
    for offset, value in (b or {}).items():
        iwram[offset] = value
    return {'ewram': bytes(ewram), 'iwram': bytes(iwram)}


class RamBoundsTest(unittest.TestCase):
    def test_range_check_refuses_everything_outside_bounded_ram(self):
        ok = [(0x02000000, 1), (0x0203ffff, 1), (0x03007fff, 1), (0x02000000, ro.CHUNK)]
        for address, size in ok:
            ro.check_ram_range(address, size)
        bad = [(0x02000000, 0), (0x02000000, ro.CHUNK + 1), (0x0203ffff, 2), (0x03007fff, 2),
               (0x01ffffff, 1), (0x02040000, 1), (0x08000000, 4), (0x03008000, 1)]
        for address, size in bad:
            with self.subTest(address=address, size=size), self.assertRaises(ValueError):
                ro.check_ram_range(address, size)

    def test_session_read_refuses_before_calling_core(self):
        host = FakeHost()
        host.libretro_host_read_gba_address = lambda *a: self.fail('core called')
        for address, size in [(0x02000000, 0), (0x0203ffff, 2), (0x08000000, 4)]:
            with self.assertRaises(ValueError):
                ro.Session(host).read(address, size)

    def test_dump_covers_both_regions_exactly(self):
        dump = ro.Session(FakeHost()).dump()
        self.assertEqual({k: len(v) for k, v in dump.items()}, {'ewram': EW[1], 'iwram': IW[1]})

    def test_unmapped_read_fails_closed(self):
        host = FakeHost()
        host.libretro_host_read_gba_address = lambda *a: False
        with self.assertRaises(ValueError):
            ro.Session(host).read(0x02000000, 4)


class MovieTest(unittest.TestCase):
    def test_shared_grammar_is_bounded(self):
        self.assertEqual(ro.parse_movie('run 3 8\nobserve a'), [('run', 3, 8), ('observe', 'a')])
        for text in ['run 0\nobserve a', 'run 100001\nobserve a', 'run 1 16384\nobserve a', 'run 1',
                     'restore\nobserve a', 'observe a\nobserve a', 'poke 1 2\nobserve a', 'run 5']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                ro.parse_movie(text)

    def test_executor_runs_inputs_observes_and_restores_frame_counter(self):
        host, seen = FakeHost(), []
        session = ro.Session(host)
        commands = ro.parse_movie('run 5 16\nsave\nrun 10\nobserve mid\nrestore\nobserve back\nmash 2')
        ro.execute_movie(session, commands, lambda label, s: seen.append((label, host.frame, s.frames)), 'st')
        self.assertEqual(seen, [('mid', 15, 15), ('back', 5, 5)])
        self.assertEqual(host.frame, 5 + 2 * 91)

    def test_cartridge_save_is_not_supported_generically(self):
        session = ro.Session(FakeHost())
        with self.assertRaises(ValueError):
            ro.execute_movie(session, ro.parse_movie('cartridge_save\nobserve a'), lambda *a: None, 'st')

    def test_boot_jitter_is_bounded_and_shifts_the_movie(self):
        host = FakeHost()
        dumps = ro.capture_boot(ro.Session(host), ro.parse_movie('run 4\nobserve a'), 6, 'st')
        self.assertEqual((host.frame, list(dumps)), (10, ['a']))
        for bad in (-1, ro.MAX_JITTER + 1):
            with self.assertRaises(ValueError):
                ro.capture_boot(ro.Session(FakeHost()), ro.parse_movie('observe a'), bad, 'st')


class StabilityTest(unittest.TestCase):
    def test_identical_boots_have_no_drift(self):
        self.assertEqual(ro.drift_ranges([boot({5: 1})['ewram']] * 3), [])

    def test_ranges_merge_adjacent_bytes_and_catch_any_boot_pair(self):
        a, b, c = boot({10: 1, 11: 1}), boot({10: 1, 11: 1}), boot({10: 2, 11: 1, 100: 9})
        self.assertEqual(ro.drift_ranges([a['ewram'], b['ewram'], c['ewram']]), [(10, 1), (100, 1)])
        # Drift visible only between boots 2 and 3 is still found (not only versus boot 1).
        self.assertEqual(ro.drift_ranges([boot({})['ewram'], boot({7: 3})['ewram'], boot({7: 4})['ewram']]), [(7, 1)])
        self.assertEqual(ro.drift_ranges([boot({0: 1, 1: 2, 2: 3})['ewram'], boot({})['ewram']]), [(0, 3)])

    def test_edges_of_region_are_covered(self):
        last = EW[1] - 1
        self.assertEqual(ro.drift_ranges([boot({0: 1})['ewram'], boot({last: 1})['ewram']]), [(0, 1), (last, 1)])

    def test_dump_shape_is_validated(self):
        for dumps in ([b'ab'], [b'ab', b'abc'], []):
            with self.assertRaises(ValueError):
                ro.drift_ranges(dumps)

    def test_compare_boots_reports_ranges_counts_probes_and_never_ram_bytes(self):
        boots = [{'x': boot({0x24284: 1, 0x24285: 1}, {0x41D8: 7})},
                 {'x': boot({0x24284: 2, 0x24285: 2}, {0x41D8: 7})},
                 {'x': boot({0x24284: 3, 0x24285: 3}, {0x41D8: 7})}]
        probes = [('drift', 0x02024284, 4), ('still', 0x02000000, 4), ('iw', 0x030041D8, 1),
                  ('near', 0x02024286, 2)]
        report = ro.compare_boots(boots, probes)['x']
        ew = report['regions']['ewram']
        self.assertEqual((ew['drifting_bytes'], ew['stable_bytes'], ew['drifting_range_count']), (2, EW[1] - 2, 1))
        self.assertEqual(ew['drifting_ranges'], [dict(address='0x02024284', length=2)])
        self.assertEqual(report['regions']['iwram']['drifting_bytes'], 0)
        self.assertEqual({p['name']: p['verdict'] for p in report['probes']},
                         {'drift': 'drifting', 'still': 'stable', 'iw': 'stable', 'near': 'stable'})
        self.assertNotIn('data', json.dumps(report))
        self.assertEqual(len(ew['boot_sha256']), 3)

    def test_range_list_is_capped_but_counts_stay_exact(self):
        changes = {i * 2: 1 for i in range(ro.MAX_RANGES + 10)}
        ew = ro.compare_boots([{'x': boot(changes)}, {'x': boot({})}])['x']['regions']['ewram']
        self.assertEqual((ew['drifting_range_count'], len(ew['drifting_ranges']), ew['drifting_ranges_truncated']),
                         (ro.MAX_RANGES + 10, ro.MAX_RANGES, True))
        self.assertEqual(ew['drifting_bytes'], ro.MAX_RANGES + 10)

    def test_boot_count_and_label_mismatch_fail_closed(self):
        with self.assertRaises(ValueError):
            ro.compare_boots([{'x': boot({})}])
        with self.assertRaises(ValueError):
            ro.compare_boots([{'x': boot({})}] * (ro.MAX_BOOTS + 1))
        with self.assertRaises(ValueError):
            ro.compare_boots([{'x': boot({})}, {'y': boot({})}])

    def test_probe_parsing(self):
        self.assertEqual(ro.parse_probe('sb1=0x03005008:4'), ('sb1', 0x03005008, 4))
        self.assertEqual(ro.parse_probe('p=0x02000000:0x10'), ('p', 0x02000000, 16))
        for bad in ('x', 'x=0x02000000', '=0x02000000:4', 'a b=0x02000000:4', 'x=0x08000000:4',
                    'x=0x0203fffe:4', 'x=0x02000000:0'):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                ro.parse_probe(bad)

    def test_end_to_end_with_fake_host_detects_timer_like_drift(self):
        drift = {0x02000100, 0x02000101}
        boots = []
        for index in range(3):
            host = FakeHost(drift_ewram=drift)
            boots.append(ro.capture_boot(ro.Session(host), ro.parse_movie('run 3\nobserve a'), index * 7, 'st'))
        report = ro.compare_boots(boots, [('timer', 0x02000100, 2), ('flag', 0x02000200, 2)])['a']
        self.assertEqual([p['verdict'] for p in report['probes']], ['drifting', 'stable'])
        self.assertEqual(report['regions']['ewram']['drifting_ranges'], [dict(address='0x02000100', length=2)])


class CliTest(unittest.TestCase):
    def test_failure_writes_fail_report_and_never_overwrites_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for name in ('rom', 'core', 'lib'):
                (folder / name).write_bytes(b'x')
            (folder / 'm.movie').write_text('run 1\n')  # no observation: invalid
            out = folder / 'out.json'
            argv = ['--rom', str(folder / 'rom'), '--core', str(folder / 'core'), '--library', str(folder / 'lib'),
                    '--movie', str(folder / 'm.movie'), '--output', str(out)]
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(ro.main(argv), 1)
                self.assertEqual(json.loads(out.read_text())['status'], 'FAIL')
                with self.assertRaises(SystemExit):
                    ro.main(argv[:-1] + [str(folder / 'rom')])
            self.assertEqual((folder / 'rom').read_bytes(), b'x')

    def test_hard_linked_output_is_refused_before_any_write(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for name in ('rom', 'core', 'lib'):
                (folder / name).write_bytes(b'x')
            (folder / 'm.movie').write_text('run 1\nobserve a\n')
            for target in ('rom', 'core', 'lib', 'm.movie'):
                link = folder / f'link-{target}.json'
                os.link(folder / target, link)
                before = (folder / target).read_bytes()
                argv = ['--rom', str(folder / 'rom'), '--core', str(folder / 'core'),
                        '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                        '--output', str(link)]
                with self.subTest(target=target), contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit):
                        ro.main(argv)
                    self.assertEqual((folder / target).read_bytes(), before)

    def test_oversized_observation_count_is_refused_before_any_boot(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for name in ('rom', 'core', 'lib'):
                (folder / name).write_bytes(b'x')
            movie = ''.join(f'observe o{i}\n' for i in range(ro.MAX_OBSERVATIONS + 1))
            (folder / 'm.movie').write_text(movie)
            out = folder / 'out.json'
            argv = ['--rom', str(folder / 'rom'), '--core', str(folder / 'core'), '--library', str(folder / 'lib'),
                    '--movie', str(folder / 'm.movie'), '--output', str(out)]
            with patch.object(ro.Session, 'open') as opened, contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(ro.main(argv), 1)
            opened.assert_not_called()
            report = json.loads(out.read_text())
            self.assertEqual(report['status'], 'FAIL')
            self.assertIn('observations', report['error'])
            (folder / 'm.movie').write_text(movie.replace(f'observe o{ro.MAX_OBSERVATIONS}\n', ''))
            hosts = iter([FakeHost(), FakeHost()])
            with patch.object(ro.Session, 'open', side_effect=lambda *a: ro.Session(next(hosts))):
                self.assertEqual(ro.main(argv), 0)

    def test_success_path_with_patched_session(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for name in ('rom', 'core', 'lib'):
                (folder / name).write_bytes(b'x')
            (folder / 'm.movie').write_text('run 2\nobserve a\n')
            out = folder / 'out.json'
            hosts = [FakeHost(drift_iwram={0x03000010}) for _ in range(2)]
            opened = iter(hosts)
            with patch.object(ro.Session, 'open', side_effect=lambda *a: ro.Session(next(opened))):
                code = ro.main(['--rom', str(folder / 'rom'), '--core', str(folder / 'core'),
                                '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                                '--output', str(out), '--probe', 'x=0x03000010:1'])
            report = json.loads(out.read_text())
            self.assertEqual((code, report['status'], report['boots']), (0, 'OBSERVED', 2))
            self.assertEqual(report['observations']['a']['probes'][0]['verdict'], 'drifting')
            self.assertEqual([h.calls for h in hosts], [['cleanup'], ['cleanup']])

    def test_zero_or_nonpositive_boot_jitter_is_refused_before_any_boot(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for name in ('rom', 'core', 'lib'):
                (folder / name).write_bytes(b'x')
            (folder / 'm.movie').write_text('run 1\nobserve a\n')
            out = folder / 'out.json'
            for bad_jitter, boots in (('0', '2'), ('-3', '2'), (str(ro.MAX_JITTER), '3')):
                with self.subTest(bad_jitter=bad_jitter, boots=boots):
                    argv = ['--rom', str(folder / 'rom'), '--core', str(folder / 'core'),
                            '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                            '--output', str(out), '--boots', boots, '--boot-jitter', bad_jitter]
                    with patch.object(ro.Session, 'open') as opened, contextlib.redirect_stderr(io.StringIO()):
                        self.assertEqual(ro.main(argv), 1)
                    opened.assert_not_called()
                    report = json.loads(out.read_text())
                    self.assertEqual(report['status'], 'FAIL')
                    self.assertIn('jitter', report['error'])

    def test_missing_private_input_writes_path_free_error_in_json(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for name in ('rom', 'core', 'lib'):
                (folder / name).write_bytes(b'x')
            (folder / 'm.movie').write_text('run 1\nobserve a\n')
            secret_dir = folder / 'private-secret-vault'
            missing_rom = secret_dir / 'secret-project.gba'
            out = folder / 'out.json'
            argv = ['--rom', str(missing_rom), '--core', str(folder / 'core'),
                    '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                    '--output', str(out)]
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                code = ro.main(argv)
            raw_json = out.read_text()
            report = json.loads(raw_json)
            self.assertEqual((code, report['status']), (1, 'FAIL'))
            self.assertNotIn('private-secret-vault', raw_json)
            self.assertNotIn('secret-project.gba', raw_json)
            self.assertNotIn('/', report['error'])
            self.assertIn('ENOENT', report['error'])
            self.assertNotIn('private-secret-vault', err.getvalue())
            # Also verify OSError without errno (e.g. ctypes.CDLL load failure) does not leak paths.
            with patch.object(ro.Session, 'open', side_effect=OSError(f'{missing_rom}: cannot open')), \
                    contextlib.redirect_stderr(io.StringIO()):
                code = ro.main(['--rom', str(folder / 'rom'), '--core', str(folder / 'core'),
                                '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                                '--output', str(out)])
            report2 = json.loads(out.read_text())
            self.assertEqual((code, report2['status']), (1, 'FAIL'))
            self.assertNotIn('private-secret-vault', out.read_text())
            self.assertNotIn('/', report2['error'])

    def test_missing_symbol_attribute_error_is_sanitized_in_json_and_stderr(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for name in ('rom', 'core', 'lib'):
                (folder / name).write_bytes(b'x')
            (folder / 'm.movie').write_text('run 1\nobserve a\n')
            out = folder / 'out.json'
            secret_lib = folder / 'private-secret-vault' / 'missing-symbol-host.so'
            argv = ['--rom', str(folder / 'rom'), '--core', str(folder / 'core'),
                    '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                    '--output', str(out)]
            err = io.StringIO()
            with patch.object(ro.Session, 'open',
                              side_effect=AttributeError(f'{secret_lib}: undefined symbol: libretro_host_init')), \
                    contextlib.redirect_stderr(err):
                code = ro.main(argv)
            raw_json = out.read_text()
            report = json.loads(raw_json)
            self.assertEqual((code, report['status']), (1, 'FAIL'))
            self.assertIn('AttributeError', report['error'])
            self.assertNotIn('private-secret-vault', raw_json)
            self.assertNotIn('missing-symbol-host.so', raw_json)
            self.assertNotIn('/', report['error'])
            self.assertNotIn('private-secret-vault', err.getvalue())
            self.assertNotIn('missing-symbol-host.so', err.getvalue())

            # Also verify Session.open itself raising AttributeError when CDLL lacks a symbol.
            class IncompleteLibrary:
                pass

            with patch('rom_observe.C.CDLL', side_effect=lambda *a: IncompleteLibrary()), \
                    contextlib.redirect_stderr(io.StringIO()) as err2:
                code2 = ro.main(argv)
            report2 = json.loads(out.read_text())
            self.assertEqual((code2, report2['status']), (1, 'FAIL'))
            self.assertIn('libretro_host_init', report2['error'])
            self.assertNotIn('/', report2['error'])
            self.assertIn('FAIL:', err2.getvalue())

    def test_output_write_failures_sanitize_paths_and_return_nonzero(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for name in ('rom', 'core', 'lib'):
                (folder / name).write_bytes(b'x')
            (folder / 'm.movie').write_text('run 1\nobserve a\n')

            # 1. Initial output invalidation write failure (missing secret parent dir) must not leak path.
            missing_out = folder / 'private-secret-dir' / 'secret-out.json'
            argv_missing = ['--rom', str(folder / 'rom'), '--core', str(folder / 'core'),
                            '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                            '--output', str(missing_out)]
            err = io.StringIO()
            with patch.object(ro.Session, 'open') as opened, contextlib.redirect_stderr(err):
                code = ro.main(argv_missing)
            opened.assert_not_called()
            self.assertEqual(code, 1)
            self.assertIn('FAIL:', err.getvalue())
            self.assertIn('ENOENT', err.getvalue())
            self.assertNotIn('private-secret-dir', err.getvalue())
            self.assertNotIn('secret-out.json', err.getvalue())

            # 2. Final report write failure after successful observation must sanitize path and fail closed.
            out = folder / 'private-secret-out.json'
            argv_ok = ['--rom', str(folder / 'rom'), '--core', str(folder / 'core'),
                       '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                       '--output', str(out)]
            write_calls = [0]
            real_write_text = Path.write_text

            def flaky_write_text(path_self, data, *args, **kwargs):
                write_calls[0] += 1
                if path_self == out and write_calls[0] == 2:
                    raise PermissionError(13, 'Permission denied', str(out))
                return real_write_text(path_self, data, *args, **kwargs)

            err2 = io.StringIO()
            hosts = iter([FakeHost(), FakeHost()])
            with patch.object(ro.Session, 'open', side_effect=lambda *a: ro.Session(next(hosts))), \
                    patch.object(Path, 'write_text', flaky_write_text), \
                    contextlib.redirect_stderr(err2):
                code2 = ro.main(argv_ok)
            self.assertEqual(code2, 1)
            self.assertIn('FAIL:', err2.getvalue())
            self.assertIn('EACCES', err2.getvalue())
            self.assertNotIn('private-secret-out.json', err2.getvalue())
            self.assertNotIn('/', err2.getvalue())

            # 3. Symlink loop on output path raises RuntimeError in Path.resolve() on Python 3.12;
            #    must be sanitized without leaking the host path or crashing with an uncaught traceback.
            loop_a = folder / 'private-loop-a.json'
            loop_b = folder / 'private-loop-b.json'
            loop_a.symlink_to(loop_b)
            loop_b.symlink_to(loop_a)
            argv_loop = ['--rom', str(folder / 'rom'), '--core', str(folder / 'core'),
                         '--library', str(folder / 'lib'), '--movie', str(folder / 'm.movie'),
                         '--output', str(loop_a)]
            err3 = io.StringIO()
            with patch.object(ro.Session, 'open') as opened3, contextlib.redirect_stderr(err3):
                code3 = ro.main(argv_loop)
            opened3.assert_not_called()
            self.assertEqual(code3, 1)
            self.assertIn('FAIL:', err3.getvalue())
            self.assertNotIn('private-loop-a.json', err3.getvalue())
            self.assertNotIn('private-loop-b.json', err3.getvalue())
            self.assertNotIn('/', err3.getvalue())


if __name__ == '__main__':
    unittest.main()
