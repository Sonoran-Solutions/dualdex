import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest

import rom_identify as ri
from ups_identity import MAX_ROM


def synthetic(code='BPRE', title='POKEMON FIRE', size=ri.RETAIL_SIZE, revision=0, marker=None, marker_at=0x400):
    rom = bytearray(size)
    rom[0xA0:0xA0 + len(title)] = title.encode()
    rom[0xAC:0xB0] = code.encode()
    rom[0xB0:0xB2] = b'01'
    rom[0xB2] = 0x96
    rom[0xBC] = revision
    rom[0xBD] = (-(sum(rom[0xA0:0xBD]) + 0x19)) & 0xFF
    if marker:
        rom[marker_at:marker_at + len(marker)] = marker
    return bytes(rom)


class IdentifyTest(unittest.TestCase):
    def family(self, rom):
        report = ri.inspect(rom)
        return report['family'], report['base_config_id']

    def test_retail_codes_map_to_their_base_configs(self):
        for code, title, base in [('BPRE', 'POKEMON FIRE', 'firered_rev0'), ('BPGE', 'POKEMON LEAF', 'leafgreen'),
                                  ('BPEE', 'POKEMON EMER', 'emerald'), ('AXVE', 'POKEMON RUBY', 'ruby'),
                                  ('AXPE', 'POKEMON SAPP', 'sapphire')]:
            with self.subTest(code=code):
                self.assertEqual(self.family(synthetic(code, title)), ('vanilla-retail', base))

    def test_every_recommended_base_exists_in_gamedata(self):
        configs = ri.load_configs()
        for _, base in ri.RETAIL.values():
            self.assertIn(base, configs)
        self.assertIsNotNone(ri.inspect(synthetic())['base_config'])
        self.assertIn('radical_red', ri.inspect(synthetic())['sibling_configs'])

    def test_header_fields_and_checksums(self):
        header = ri.inspect(synthetic(revision=1))['header']
        self.assertEqual((header['title'], header['game_code'], header['maker'], header['revision']),
                         ('POKEMON FIRE', 'BPRE', '01', 1))
        self.assertTrue(header['fixed_byte_ok'] and header['complement_ok'])
        broken = bytearray(synthetic())
        broken[0xBD] ^= 1
        broken[0xB2] = 0
        header = ri.inspect(bytes(broken))['header']
        self.assertFalse(header['fixed_byte_ok'] or header['complement_ok'])

    def test_hashes_are_of_the_whole_image(self):
        rom = synthetic()
        import hashlib, zlib
        report = ri.inspect(rom)
        self.assertEqual(report['rom']['sha256'], hashlib.sha256(rom).hexdigest())
        self.assertEqual(report['rom']['crc32'], f'{zlib.crc32(rom):08x}')
        self.assertEqual(report['rom']['bytes'], len(rom))

    def test_cfru_inplace_is_32mib_bpre(self):
        self.assertEqual(self.family(synthetic(size=ri.CFRU_SIZE)), ('cfru-inplace', 'firered_rev0'))
        # A 32 MiB LeafGreen image is not claimed to be CFRU.
        self.assertEqual(self.family(synthetic('BPGE', 'POKEMON LEAF', ri.CFRU_SIZE))[0], 'firered-inplace')

    def test_inplace_hacks_with_retail_codes(self):
        self.assertEqual(self.family(synthetic(title='POKEMON GHOST')), ('firered-inplace', 'firered_rev0'))
        self.assertEqual(self.family(synthetic(size=ri.RETAIL_SIZE * 2 - 0x100000, title='POKEMON FIRE'))[0],
                         'firered-inplace')
        self.assertEqual(self.family(synthetic('BPEE', 'POKEMON HACK')), ('emerald-inplace', 'emerald'))
        self.assertEqual(self.family(synthetic('AXVE', 'POKEMON HACK')), ('unknown', 'ruby'))

    def test_expansion_marker_wins_and_reports_header_window(self):
        report = ri.inspect(synthetic('BPEE', 'POKEMON EMER', marker=b'RHHEXP', marker_at=0x120))
        self.assertEqual((report['family'], report['base_config_id']), ('pokeemerald-expansion', 'emerald'))
        self.assertIn('0x120', ' '.join(report['evidence']))
        self.assertIn('window', ' '.join(report['evidence']))
        far = ri.inspect(synthetic('BPEE', 'POKEMON EMER', marker=b'RHH_EXP', marker_at=0x80000))
        self.assertEqual(far['family'], 'pokeemerald-expansion')
        self.assertNotIn('window', ' '.join(far['evidence']))

    def test_unknown_code_has_no_base(self):
        report = ri.inspect(synthetic('ZZZZ', 'SOMETHING'))
        self.assertEqual((report['family'], report['base_config_id'], report['base_config']),
                         ('unknown', None, None))
        self.assertEqual(len(report['next_steps']), 1)

    def test_size_bounds_fail_closed(self):
        for rom in (b'', bytes(ri.HEADER_END - 1)):
            with self.subTest(size=len(rom)), self.assertRaises(ValueError):
                ri.inspect(rom)
        with self.assertRaises(ValueError):
            ri.inspect(bytes(MAX_ROM + 1))
        self.assertEqual(ri.inspect(synthetic(size=ri.HEADER_END))['family'], 'firered-inplace')

    def test_cli_json_human_and_failures(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'x.gba'
            path.write_bytes(synthetic())
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(ri.main([str(path), '--json']), 0)
            parsed = json.loads(out.getvalue())
            self.assertEqual((parsed['family'], parsed['verdict']), ('vanilla-retail', 'NOT_A_SUPPORT_VERDICT'))
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(ri.main([str(path)]), 0)
            self.assertIn('family   vanilla-retail', out.getvalue())
            self.assertIn('next steps:', out.getvalue())
            tiny = Path(folder) / 'tiny.gba'
            tiny.write_bytes(b'abc')
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(ri.main([str(tiny)]), 2)
                self.assertEqual(ri.main([str(Path(folder) / 'missing.gba')]), 2)

    def test_nonprintable_header_bytes_are_sanitized(self):
        rom = bytearray(synthetic())
        rom[0xA0:0xA3] = b'\x00\xff\n'
        title = ri.inspect(bytes(rom))['header']['title']
        self.assertTrue(all(32 <= ord(c) < 127 for c in title))


if __name__ == '__main__':
    unittest.main()
