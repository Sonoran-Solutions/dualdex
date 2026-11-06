"""Invented bytes only; no ROM, core, save or screenshot needed."""
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest

import unbound_location as ul

ROM = ul.ROM_BASE


def put16(rom, address, *halfwords):
    for i, h in enumerate(halfwords):
        struct.pack_into('<H', rom, address - ROM + 2*i, h)


def put32(rom, address, *words):
    for i, w in enumerate(words):
        struct.pack_into('<I', rom, address - ROM + 4*i, w)


def synthetic_rom():
    rom = bytearray(ul.ROM_SIZE)
    a = ul.SET_SAVE_BLOCKS_POINTERS  # push; ldr r4,=SB1ptr; ldr r5,[r4]; bl Random; offset = 0
    put16(rom, a, 0xb530, 0x4c0c, 0x6825, 0xf7f8, 0xff33, 0x2100, 0x2100, 0x4a0a, 0x480a, 0x1808,
          0x6010, 0x480a, 0x1808, 0x6020, 0x4a09, 0x480a, 0x1809, 0x6011)
    put32(rom, a+0x34, 0x03005008, 0x0300500c, 0x02024588, 0x0202552c, 0x03005010, 0x02029314)
    for address, offset in ((ul.GET_CURRENT_MAPSEC, 4), (ul.GET_SAVED_WARP_MAPSEC, 0x14)):
        put16(rom, address, 0xb500, 0x4808, 0x6801, 0x2000 | offset, 0x5608, 0x0400, 0x0c00,
              0x7809 | (offset+1) << 6, 0x0609, 0x1609, 0x0409, 0x0c09, 0xf7fe, 0xffde, 0x7d00)
        put32(rom, address+0x24, 0x03005008)
    hook, table, key = 0x09000100, 0x09000200, 0x00a5a5a4
    put16(rom, ul.GET_MAP_HEADER, 0x4a00, 0x4710)
    put32(rom, ul.GET_MAP_HEADER+4, hook | 1)
    put16(rom, hook, 0x4b03, 0x0080, 0x58c2, 0x4b03, 0x0089, 0x4053, 0x58c8, 0x4770)
    put32(rom, hook+0x10, table, key)
    # Two groups (2 and 1 maps); third entry decodes outside ROM and ends the table.
    lists, headers = (0x09000300, 0x09000308), (0x09000400, 0x09000420, 0x09000440)
    put32(rom, table, lists[0] ^ key, lists[1] ^ key, 0xffffffff ^ key)
    put32(rom, lists[0], headers[0], headers[1])
    put32(rom, lists[1], headers[2], 0)
    for header, mapsec in zip(headers, (0x80, 0x93, 0x58)):
        put32(rom, header, 0x09000500)
        rom[header - ROM + 0x14] = mapsec
    n = ul.GET_MAP_NAME
    put16(rom, n, 0xb570, 0x1c06, 0x0409, 0x0412, 0x0c14, 0x0c08, 0x4a05, 0x1889, 0x0c0d, 0x2d02,
          0xd816, 0xf7ff, 0xffd7, 0x2801, 0xd106, 0x4902)
    put32(rom, n+0x24, 0xffa80000, 0x09000700)
    put16(rom, n+0x2c, 0x4802, 0x00a9, 0x1809, 0x6809)
    put32(rom, n+0x38, 0x09000600)
    put32(rom, 0x09000600, 0x09000610, 0x09000620, 0x09000630)
    for i, p in enumerate((0x09000610, 0x09000620, 0x09000630)):
        rom[p-ROM:p-ROM+3+i] = bytes(range(1, 3+i)) + b'\xff'
    return rom


def sb1_head(group, num, x=3, y=4):
    data = bytearray(ul.SB1_HEAD)
    struct.pack_into('<hhbbbxhh', data, 0, x, y, group, num, 2, -1, -1)
    struct.pack_into('<bb', data, 0x24, 3, 105)
    return bytes(data)


class UnboundLocationStaticTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rom = synthetic_rom()

    def test_thumb_literal_uses_aligned_pc(self):
        rom = bytearray(16)
        struct.pack_into('<H', rom, 2, 0x4a00)  # ldr r2,[pc,#0] at +2 -> aligned +4
        struct.pack_into('<I', rom, 4, 0x0202552c)
        self.assertEqual(ul.thumb_literal(bytes(rom), ROM+2), (2, ROM+4, 0x0202552c))
        with self.assertRaises(ValueError):
            ul.thumb_literal(bytes(rom), ROM+4)

    def test_save_blocks_offset_must_be_forced_zero(self):
        out = ul.inspect_save_blocks(self.rom)
        self.assertEqual((out['offset_register'], out['save_block1'], out['gSaveBlock1Ptr']),
                         ('CONSTANT_ZERO', '0x202552c', '0x3005008'))
        randomized = bytearray(self.rom)
        put16(randomized, ul.SET_SAVE_BLOCKS_POINTERS+10, 0x217c, 0x4001)  # movs r1,#0x7c; ands r1,r0
        with self.assertRaises(ValueError):
            ul.inspect_save_blocks(randomized)

    def test_mapsec_getters_read_warp_group_num_and_header_byte(self):
        out = ul.inspect_mapsec_getters(self.rom, 0x03005008)
        self.assertEqual((out['current']['group_offset'], out['current']['num_offset']), ('0x4', '0x5'))
        self.assertEqual(out['saved_warp']['group_offset'], '0x14')
        with self.assertRaises(ValueError):
            ul.inspect_mapsec_getters(self.rom, 0x03005004)

    def test_xor_group_table_resolves_headers_and_enumerates(self):
        path = ul.inspect_map_header_path(self.rom)
        table, key = int(path['map_groups_table'], 16), int(path['group_pointer_xor_key'], 16)
        self.assertEqual((table, key, path['hook']), (0x09000200, 0x00a5a5a4, '0x9000100'))
        self.assertEqual(ul.map_header(self.rom, table, key, 0, 1), 0x09000420)
        summary, rows = ul.enumerate_maps(self.rom, table, key)
        self.assertEqual((summary['groups'], summary['maps_per_group'], summary['maps']), (2, [2, 1], 3))
        self.assertEqual(rows, [(0, 0, 0x80), (0, 1, 0x93), (1, 0, 0x58)])
        self.assertEqual((summary['mapsec_min'], summary['mapsec_max'], summary['max_map_num']), ('0x58', '0x93', 1))
        unhooked = bytearray(self.rom)
        put16(unhooked, ul.GET_MAP_HEADER+2, 0x4718)  # bx r3: wrong register
        with self.assertRaises(ValueError):
            ul.inspect_map_header_path(unhooked)

    def test_map_name_table_bounds_and_hashes_without_text(self):
        out = ul.inspect_map_names(self.rom)
        self.assertEqual((out['first_mapsec'], out['last_mapsec'], out['count'], out['table']),
                         ('0x58', '0x5a', 3, '0x9000600'))
        self.assertEqual((out['name_length_min'], out['name_length_max']), (2, 4))
        self.assertNotIn('names', out)
        broken = bytearray(self.rom)
        broken[0x09000630-ROM:0x09000630-ROM+80] = b'\x01'*80
        with self.assertRaises(ValueError):
            ul.inspect_map_names(broken)

    def test_inspect_rom_refuses_any_other_rom(self):
        with self.assertRaises(ValueError):
            ul.inspect_rom(bytes(self.rom))


class UnboundLocationDecodeTests(unittest.TestCase):
    def test_sb1_head_decodes_signed_warp_fields(self):
        head = ul.decode_sb1_head(sb1_head(3, 6))
        self.assertEqual(head['pos'], dict(x=3, y=4))
        self.assertEqual(head['location'], dict(map_group=3, map_num=6, warp_id=2, x=-1, y=-1))
        self.assertEqual((head['escape_warp']['map_group'], head['escape_warp']['map_num']), (3, 105))

    def test_native_mirror_keeps_production_bounds(self):
        self.assertEqual(ul.native_decode(sb1_head(3, 6), 0, 4, 0x24)['map_num'], 6)
        self.assertFalse(ul.native_decode(sb1_head(36, 0), 0, 4, 0x24)['is_valid'])
        self.assertFalse(ul.native_decode(sb1_head(-1, 0), 0, 4, 0x24)['is_valid'])
        # Zeroed RAM decodes as a valid (0, 0): why the legacy fallback address matters.
        self.assertEqual(ul.native_decode(bytes(ul.SB1_HEAD), 0, 4, 0x24)['is_valid'], True)
        self.assertEqual(0x02000000 + ul.LEGACY_PARTY_OFFSET - ul.LEGACY_SB1_PARTY, 0x0202404c)


class UnboundLocationCliTests(unittest.TestCase):
    def run_tool(self, *args):
        return subprocess.run([sys.executable, str(Path(ul.__file__)), *args],
                              capture_output=True, text=True, timeout=60)

    def test_output_cannot_overwrite_tool_and_runtime_needs_inputs(self):
        result = self.run_tool('--rom', 'x.gba', '--static-only', '--output', ul.__file__)
        self.assertEqual(result.returncode, 2)
        self.assertIn('must not overwrite', result.stderr)
        with tempfile.TemporaryDirectory() as temp:
            result = self.run_tool('--rom', 'x.gba', '--output', str(Path(temp)/'r.json'))
            self.assertEqual(result.returncode, 2)
            self.assertIn('runtime needs', result.stderr)

    def test_wrong_rom_fails_closed_with_report(self):
        with tempfile.TemporaryDirectory() as temp:
            rom, out = Path(temp)/'x.gba', Path(temp)/'r.json'
            rom.write_bytes(b'\0'*1024)
            result = self.run_tool('--rom', str(rom), '--static-only', '--output', str(out))
            self.assertEqual(result.returncode, 1)
            self.assertIn('"status": "FAIL"', out.read_text())

    def test_committed_movie_parses_within_budget(self):
        import unbound_observe as uo
        movie = Path(ul.__file__).with_name('unbound-location-grotto-warp.movie').read_text()
        commands = uo.parse_movie(movie)
        self.assertEqual(sum(c[0] == 'observe' for c in commands), 13)
        self.assertNotIn(('cartridge_save',), commands)


if __name__ == '__main__':
    unittest.main()
