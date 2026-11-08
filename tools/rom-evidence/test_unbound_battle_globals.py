"""Invented Thumb instructions only; no cartridge, upstream source, core or network."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr
from io import StringIO
from unittest.mock import patch

import unbound_battle_globals as bg

BASE = bg.ROM_BASE


def halfwords(*values):
    return struct.pack('<' + 'H'*len(values), *values)


class ThumbDecodingTests(unittest.TestCase):
    def test_literal_aligns_pc_and_checks_both_bounds(self):
        rom = halfwords(0, 0x4a00) + struct.pack('<I', 0x02001234)
        self.assertEqual(bg.literal(rom, BASE+2), (2, BASE+4, 0x02001234))
        for data, pc in [(rom[:6], BASE+2), (rom, BASE-2),
                         (rom, BASE+1), (rom, BASE), (rom, BASE+8)]:
            with self.subTest(pc=pc, length=len(data)), self.assertRaises(ValueError):
                bg.literal(data, pc)

    def test_literal_immediate_scales_by_four(self):
        rom = halfwords(0x4f02, 0) + bytes(8) + struct.pack('<I', 0x03001234)
        self.assertEqual(bg.literal(rom, BASE), (7, BASE+12, 0x03001234))

    def test_bl_signed_displacement_and_pc_bias(self):
        for words, destination in [((0xf000, 0xf800), BASE+4),
                                   ((0xf000, 0xf803), BASE+10),
                                   ((0xf7ff, 0xfffc), BASE-4),
                                   ((0xf400, 0xf800), BASE+4-(1 << 22))]:
            with self.subTest(words=words):
                self.assertEqual(bg.branch_target(halfwords(*words), BASE), destination)

    def test_short_branches_signed_and_condition_codes(self):
        for word, target in [(0xe000, BASE+4), (0xe7fd, BASE-2),
                             (0xd001, BASE+6), (0xd1fc, BASE-4)]:
            self.assertEqual(bg.branch_target(halfwords(word), BASE), target)
        for words in [(0xf000,), (0xf000, 0xe800), (0xdf00,), (0xde00,), (0x4770,)]:
            with self.subTest(words=words), self.assertRaises(ValueError):
                bg.branch_target(halfwords(*words), BASE)

    def test_immediate_memory_width_and_scaled_offset(self):
        for word, op, width, offset in [(0x688b, 'ldr', 32, 8),
                                        (0x790b, 'ldrb', 8, 4),
                                        (0x888b, 'ldrh', 16, 4),
                                        (0x708b, 'strb', 8, 2),
                                        (0x808b, 'strh', 16, 4)]:
            result = bg.memory_access(halfwords(word), BASE)
            self.assertEqual((result['operation'], result['width_bits'], result['offset_bytes']),
                             (op, width, offset))
            self.assertEqual((result['base_register'], result['register']), (1, 3))

    def test_register_offset_memory_and_invalid_instruction(self):
        result = bg.memory_access(halfwords(0x584b), BASE)  # ldr r3,[r1,r1]
        self.assertEqual(result, dict(operation='ldr', width_bits=32,
                                      base_register=1, register=3, offset_register=1))
        self.assertEqual(bg.memory_access(halfwords(0x5e4b), BASE)['width_bits'], 16)
        with self.assertRaises(ValueError):
            bg.memory_access(halfwords(0x2301), BASE)

    def test_instruction_probes_fail_closed(self):
        rom = halfwords(0x230d, 0xe000)
        digest = hashlib.sha256(rom).hexdigest()
        self.assertEqual(bg.numeric_probe(rom, BASE, 2, digest),
                         dict(pc=hex(BASE), halfword_count=2, sha256=digest))
        with self.assertRaises(ValueError):
            bg.numeric_probe(halfwords(0x230c, 0xe000), BASE, 2, digest)
        self.assertNotIn('halfwords', json.dumps(bg.PROBES))

    def test_inspected_span_hash_is_checked(self):
        rom = halfwords(0x4770)
        digest = hashlib.sha256(rom).hexdigest()
        with patch.object(bg, 'ROUTINES', {'synthetic': (BASE, BASE+2, digest)}):
            self.assertEqual(bg.inspected_spans(rom)['synthetic']['sha256'], digest)
            with self.assertRaises(ValueError):
                bg.inspected_spans(halfwords(0x4700))

    def test_global_load_records_numeric_instruction_and_access(self):
        rom = halfwords(0x4900, 0x780b) + struct.pack('<I', 0x02001234)
        with patch.object(bg, 'ROUTINES', {'synthetic': (BASE, BASE+8, '')}):
            result = bg.global_reference(rom, 'synthetic', BASE, BASE+2, 12, 2)
            self.assertEqual((result['address'], result['access']['width_bits'],
                              result['stride_bytes']), ('0x2001234', 8, 12))
            self.assertEqual(result['allocation_extent_status'], 'UNVERIFIED')
            bad = rom[:4] + struct.pack('<I', 0x04000000)
            with self.assertRaises(ValueError):
                bg.global_reference(bad, 'synthetic', BASE, BASE+2)

    def test_flag_domains_and_bit_masks(self):
        self.assertEqual(bg.flag_storage(0x23d), dict(pointer_address='0x3005008',
            offset_bytes=0xf27, mask=32, width_bits=8))
        self.assertEqual(bg.flag_storage(0x2fe)['offset_bytes'], 0xf3f)
        self.assertEqual(bg.flag_storage(0x900)['address'], '0x203b174')
        self.assertEqual(bg.flag_storage(0x9f0), dict(address='0x203b192', mask=1, width_bits=8))
        self.assertEqual(bg.flag_storage(0x16e2), dict(address='0x203b330', mask=4, width_bits=8))
        for value in [0, -1, True, 0x1900, '1']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                bg.flag_storage(value)


class IdentityAndCliTests(unittest.TestCase):
    def test_identity_gate_precedes_code_and_oracle_reads(self):
        with patch.object(bg, 'inspected_spans') as code, patch.object(bg, 'read_local') as oracle:
            with self.assertRaisesRegex(ValueError, 'size'):
                bg.inspect(b'invented')
            with patch('unbound_identity.ROM_SIZE', 8):
                with self.assertRaisesRegex(ValueError, 'SHA-256'):
                    bg.inspect(b'invented')
            code.assert_not_called()
            oracle.assert_not_called()

    def run_tool(self, *args):
        return subprocess.run([sys.executable, bg.__file__, *map(str, args)],
                              capture_output=True, text=True, timeout=10)

    def test_wrong_sha_invalidates_old_report_without_host_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            rom, out = Path(temp)/'synthetic.gba', Path(temp)/'evidence.json'
            rom.write_bytes(bytes(bg.ROM_SIZE))
            out.write_text('{"status": "VERIFIED"}\n')
            result = self.run_tool('--rom', rom, '--out', out)
            self.assertEqual(result.returncode, 1)
            self.assertEqual(out.read_text(), '{"status": "FAIL"}\n')
            self.assertNotIn(temp, out.read_text())
            self.assertNotIn(temp, result.stderr)
            self.assertEqual(rom.stat().st_size, bg.ROM_SIZE)

    def test_output_must_not_overwrite_rom_or_tool_including_hardlink(self):
        with tempfile.TemporaryDirectory() as temp:
            rom = Path(temp)/'synthetic.gba'
            rom.write_bytes(b'invented')
            alias = Path(temp)/'alias.json'
            alias.hardlink_to(rom)
            for out in [rom, alias, Path(bg.__file__)]:
                result = self.run_tool('--rom', rom, '--out', out)
                self.assertEqual(result.returncode, 2)
                self.assertIn('must not overwrite', result.stderr)
            self.assertEqual(rom.read_bytes(), b'invented')

    def test_private_guard_rejects_checkout_input(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root/'.git').mkdir()
            rom = root/'synthetic.gba'
            rom.write_bytes(b'invented')
            with patch.object(sys, 'argv', [bg.__file__, '--rom', str(rom),
                                           '--out', str(root/'evidence.json')]), \
                 patch.object(bg, 'exact_rom', return_value={}), \
                 patch.object(bg, 'inspect') as inspection, redirect_stderr(StringIO()):
                self.assertEqual(bg.main(), 1)
                inspection.assert_not_called()


if __name__ == '__main__':
    unittest.main()
