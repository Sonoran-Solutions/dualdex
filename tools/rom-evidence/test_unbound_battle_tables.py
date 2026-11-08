"""Invented numeric bytes only: no ROM, source checkout, save or emulator required."""
from pathlib import Path
from contextlib import redirect_stderr
import io
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

import unbound_battle_tables as subject

BASE = subject.ROM_BASE


def fake_rom():
    rom = bytearray(1024)
    # PC literals: table, adjacent table, pointer cell. LDR at +2 uses aligned PC.
    struct.pack_into('<HHHH', rom, 0, 0x4807, 0x4908, 0x4A08, 0x4B09)
    struct.pack_into('<IIII', rom, 32, BASE + 128, BASE + 152, BASE + 64, BASE + 4096)
    struct.pack_into('<I', rom, 64, BASE + 128)
    for i in range(2):
        at = 128 + i * 12
        rom[at:at+12] = bytes((i+4, 40+i, 2, 95, 10, 0, 1, 0xFF-i, 3, 100, i, 0))
    rom[256:265] = bytes((0, 5, 20, 1, 0, 0, 5, 0, 1))
    # ldrb r0,[r3,#0x12]; ldrb r1,[r2,r4]; ldrh r0,[r0].
    struct.pack_into('<HHH', rom, 80, 0x7C98, 0x5D11, 0x8800)
    return rom


class BattleTablesTests(unittest.TestCase):
    def setUp(self):
        self.rom = fake_rom()

    def test_locator_derives_count_and_digest_from_two_literals(self):
        table = subject.locate_table(self.rom, BASE, 12, end_pc=BASE+2, count=2)
        self.assertEqual((table['file_offset'], table['rom_address'], table['count'], table['bytes']),
                         ('0x80', '0x8000080', 2, 24))
        self.assertEqual(table['sha256'], subject.sha(self.rom[128:152]))
        self.assertEqual(table['adjacent_table_locator']['pool_address'], hex(BASE+36))
        self.rom[128] ^= 1
        self.assertNotEqual(subject.locate_table(self.rom, BASE, 12, count=2)['sha256'], table['sha256'])

    def test_indirect_locator_follows_only_bounded_rom_cell(self):
        table = subject.locate_table(self.rom, BASE+4, 12, count=2, indirect=True)
        self.assertEqual(table['locator']['pointer_cell'], hex(BASE+64))
        self.assertEqual(table['rom_address'], hex(BASE+128))
        struct.pack_into('<I', self.rom, 40, 0x02000000)
        with self.assertRaisesRegex(ValueError, 'outside ROM'):
            subject.locate_table(self.rom, BASE+4, 12, count=2, indirect=True)

    def test_locator_refuses_truncation_dimensions_and_inconsistent_boundary(self):
        for kwargs in ({'count': 0}, {'count': 4097}, {'count': True}, {'count': 100},
                       {'end_pc': BASE+2, 'count': 3}, {'end_pc': BASE+4}, {'end_pc': BASE}):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                subject.locate_table(self.rom, BASE, 12, **kwargs)
        for stride in (0, -1, 65, True):
            with self.subTest(stride=stride), self.assertRaises(ValueError):
                subject.locate_table(self.rom, BASE, stride, count=1)
        for pc in (BASE-2, BASE+6, BASE+1024):
            with self.subTest(pc=pc), self.assertRaises(ValueError):
                subject.locate_table(self.rom, pc, 12, count=1)
        with self.assertRaisesRegex(ValueError, 'outside ROM'):
            subject.literal(self.rom[:33], BASE)

    def test_move_parser_reads_proven_offsets_and_signed_priority(self):
        fields = {name: spec[0] for name, spec in subject.MOVE_FIELDS.items()}
        rows = subject.parse_records(self.rom, 128, 2, 12, fields, ('priority',))
        self.assertEqual(rows[0], dict(effect=4, power=40, type=2, accuracy=95, target=1,
                                      priority=-1, flags=3, z_move_power=100, split=0))
        self.assertEqual((rows[1]['effect'], rows[1]['priority'], rows[1]['split']), (5, -2, 1))

    def test_numeric_stats_items_and_raw_flag_byte_parsing(self):
        self.rom[320:348] = bytes(range(28))
        self.rom[400+0x12:400+0x14] = bytes((230, 17))
        stats = subject.parse_records(self.rom, 320, 1, 28, subject.BASE_FIELDS)[0]
        self.assertEqual([stats[n] for n in ('hp', 'attack', 'defense', 'speed', 'sp_attack', 'sp_defense')],
                         list(range(6)))
        self.assertEqual((stats['type1'], stats['ability2'], stats['hidden_ability']), (6, 23, 26))
        self.assertEqual(subject.parse_records(self.rom, 400, 1, 44, subject.ITEM_FIELDS),
                         [dict(holdEffect=230, holdEffectParam=17)])
        self.rom[500:514] = bytes(range(14))
        self.assertEqual(subject.parse_records(self.rom, 500, 2, 7, {'byte6': 6}),
                         [{'byte6': 6}, {'byte6': 13}])

    def test_parser_bounds_are_fail_closed(self):
        for offset, count, stride, fields in ((-1, 1, 12, {'x': 0}), (1000, 3, 12, {'x': 0}),
                                              (0, 1, 12, {'x': 12}), (0, 1, 12, {'x': -1}),
                                              (0, 1, 0, {'x': 0}), (0, 0, 12, {'x': 0})):
            with self.subTest(offset=offset, count=count, stride=stride), self.assertRaises(ValueError):
                subject.parse_records(self.rom, offset, count, stride, fields)
        with self.assertRaisesRegex(ValueError, 'unknown signed'):
            subject.parse_records(self.rom, 128, 1, 12, {'x': 0}, ('y',))

    def test_chart_is_square_and_row_major_without_multiplier_inference(self):
        self.assertEqual(subject.parse_chart(self.rom, 256, 3), [[0, 5, 20], [1, 0, 0], [5, 0, 1]])
        for offset, dimension in ((1020, 3), (-1, 3), (256, 0), (256, 65), (256, True)):
            with self.subTest(offset=offset, dimension=dimension), self.assertRaises(ValueError):
                subject.parse_chart(self.rom, offset, dimension)

    def test_instruction_decoding_and_consumer_hash_refuse_mutation(self):
        immediate = subject.byte_load(self.rom, BASE+80)
        self.assertEqual((immediate['base_register'], immediate['destination_register'], immediate['displacement']),
                         (3, 0, 0x12))
        register = subject.byte_load(self.rom, BASE+82)
        self.assertEqual((register['base_register'], register['index_register']), (2, 4))
        with self.assertRaisesRegex(ValueError, 'expected Thumb byte load'):
            subject.byte_load(self.rom, BASE+84)
        struct.pack_into('<H', self.rom, 88, 0x220C)  # movs r2,#12
        self.assertEqual(subject.immediate(self.rom, BASE+88, 2), 12)
        with self.assertRaisesRegex(ValueError, 'expected stride constant'):
            subject.immediate(self.rom, BASE+88, 3)
        proof = {'synthetic': (BASE+80, 4, subject.sha(self.rom[80:84]))}
        with patch.object(subject, 'SPANS', proof):
            subject.checked_spans(self.rom)
            self.assertEqual(subject.field_proof(self.rom, 0x12, BASE+80, 3, 'synthetic')['status'], 'VERIFIED')
            with self.assertRaisesRegex(ValueError, 'offset/index'):
                subject.field_proof(self.rom, 0x13, BASE+80, 3, 'synthetic')
            self.rom[80] ^= 1
            with self.assertRaisesRegex(ValueError, 'consumer span mismatch'):
                subject.checked_spans(self.rom)

    def test_identity_size_and_sha_refusal_precede_all_interpretation(self):
        with patch.object(subject, 'checked_spans') as checked:
            with self.assertRaisesRegex(ValueError, 'size mismatch'):
                subject.inspect(bytes(self.rom))
            with patch('unbound_identity.ROM_SIZE', len(self.rom)):
                with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
                    subject.inspect(bytes(self.rom))
            checked.assert_not_called()

    def test_cli_refusal_preserves_existing_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            rom, out = Path(tmp)/'synthetic.bin', Path(tmp)/'evidence.json'
            rom.write_bytes(self.rom)
            out.write_text('existing report\n')
            stderr = io.StringIO()
            # The task scratch directory can itself carry a Git guard marker.
            # This invented fixture tests identity refusal; test the guard below.
            with patch.object(sys, 'argv', [subject.__file__, '--rom', str(rom), '--out', str(out)]), \
                 patch.object(subject, 'require_private_directory'), redirect_stderr(stderr):
                self.assertEqual(subject.main(), 1)
            self.assertIn('size mismatch', stderr.getvalue())
            self.assertEqual(out.read_text(), 'existing report\n')

    def test_private_guard_rejects_checkout_inputs(self):
        with self.assertRaisesRegex(ValueError, 'outside Git checkouts'):
            subject.require_private_directory(Path(subject.__file__).parent)


if __name__ == '__main__':
    unittest.main()
