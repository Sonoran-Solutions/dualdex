"""ROM-free invented bytes; no upstream source fixture, ROM, save or network."""
import hashlib
import struct
import unittest
from unittest.mock import patch

import unbound_data as subject


def field(text, width):
    # Synthetic uppercase ASCII letters encoded with the Gen III alphabet.
    raw = bytes(0xBB + ord(c) - ord('A') for c in text) + b'\xff'
    return raw.ljust(width, b'\xff')


class UnboundDataTest(unittest.TestCase):
    def test_gate_precedes_layout_or_source_reads(self):
        with patch.object(subject, 'validate_layout') as layout:
            with self.assertRaisesRegex(ValueError, 'size'):
                subject.inspect(b'invented')
            layout.assert_not_called()
        with patch('unbound_identity.ROM_SIZE', 8):
            with self.assertRaisesRegex(ValueError, 'SHA-256'):
                subject.inspect(b'invented')

    def test_bounded_spans(self):
        self.assertEqual(subject.span(b'ABCD', 1, 2), b'BC')
        self.assertEqual(subject.span(b'ABCD', 4, 0), b'')
        for offset, length in [(-1, 1), (4, 1), (0, -1), (3, 2), (True, 1), (1, 1.0)]:
            with self.subTest(offset=offset, length=length), self.assertRaises(ValueError):
                subject.span(b'ABCD', offset, length)

    def test_fixed_text_termination_padding_and_unknowns(self):
        self.assertEqual(subject.fixed_name(field('TEST', 11)),
                         {'status': 'VERIFIED', 'name': 'TEST'})
        for raw in [b'\xbb' * 11, b'\xff\xbb', b'\x01\xff', b'\xfc\xff', b'\xff']:
            with self.subTest(raw=raw):
                result = subject.fixed_name(raw)
                self.assertEqual(result['status'], 'NOT_VERIFIED')
                self.assertIsNone(result['name'])
        with self.assertRaises(ValueError):
            subject.fixed_name(b'\xff' * 65)

    def test_name_table_dimensions_and_truncation(self):
        raw = field('ALPHA', 11) + field('BETA', 11)
        self.assertEqual([x['name'] for x in subject.names(raw, 0, 2, 11)], ['ALPHA', 'BETA'])
        for args in [(0, 3, 11), (0, 0, 11), (0, 4097, 11), (0, 1, 0)]:
            with self.subTest(args=args), self.assertRaises(ValueError):
                subject.names(raw, *args)

    def test_reserved_holes_and_out_of_domain_refuse(self):
        records = [{'status': 'VERIFIED', 'name': 'TEST'} for _ in range(1294)]
        for index in [0, 252, 253, 254, 276, 412, 1294, -1, True, '1']:
            with self.subTest(index=index):
                self.assertIsNone(subject.lookup_species(records, index)['name'])
        self.assertEqual(subject.lookup_species(records, 1293)['name'], 'TEST')
        records[277] = {'status': 'NOT_VERIFIED', 'name': None, 'reason': 'unknown'}
        self.assertIsNone(subject.lookup_species(records, 277)['name'])

    def test_inline_and_pointer_item_names(self):
        record = field('ITEM', 14).ljust(44, b'\x00')
        result, storage = subject.item_name(b'', record)
        self.assertEqual((result['name'], storage), ('ITEM', 'inline'))
        rom = b'\x00' * 8 + field('REMOTE', 12)
        pointer = struct.pack('<I', subject.ROM_BASE + 8).ljust(44, b'\x00')
        result, storage = subject.item_name(rom, pointer)
        self.assertEqual((result['name'], storage), ('REMOTE', 'pointer'))
        # Byte 3 outside the ROM window is not an excuse to dereference RAM.
        ram_pointer = struct.pack('<I', 0x02000000).ljust(44, b'\x00')
        self.assertEqual(subject.item_name(rom, ram_pointer)[0]['status'], 'NOT_VERIFIED')

    def test_item_pointer_bounds_and_name_limit(self):
        pointer = struct.pack('<I', subject.ROM_BASE + 8).ljust(44, b'\x00')
        for rom in [b'\x00' * 8, b'\x00' * 8 + b'\xbb' * 64 + b'\xff']:
            with self.subTest(size=len(rom)), self.assertRaises(ValueError):
                subject.item_name(rom, pointer)
        with self.assertRaises(ValueError):
            subject.item_name(b'', b'\x00' * 43)

    def test_accessor_hash_and_pointer_guards(self):
        raw = struct.pack('<I', subject.ROM_BASE + 4) + b'CODE'
        with patch.object(subject, 'POINTERS', {0: subject.ROM_BASE + 4}), \
             patch.object(subject, 'ACCESSORS', {'test': (4, 4, hashlib.sha256(b'CODE').hexdigest())}), \
             patch.object(subject, 'SPECIES_COUNT', subject.ROM_BASE + 4), \
             patch.object(subject, 'u32', side_effect=lambda data, off: struct.unpack('<I', data[:4])[0]):
            subject.validate_layout(raw)
            with self.assertRaisesRegex(ValueError, 'accessor mismatch'):
                subject.validate_layout(raw[:-1] + b'X')
            with self.assertRaisesRegex(ValueError, 'pointer mismatch'):
                subject.validate_layout(b'\x00' * 4 + b'CODE')

    def test_numeric_source_parser_preserves_aliases(self):
        source = b'#define SPECIES_ALPHA 0x1\n#define SPECIES_ALIAS 1\n#define NUM_SPECIES 99\n'
        self.assertEqual(subject.parse_species_header(source), {1: ['ALPHA', 'ALIAS']})
        for raw in [b'', b'#define SPECIES_EVIL 0x1000', b'x' * (128 * 1024 + 1)]:
            with self.assertRaises(ValueError):
                subject.parse_species_header(raw)

    def test_comparison_denominators_aliases_and_no_fuzzy_fallback(self):
        records = [{'name': name} for name in ['??????', 'Alpha', 'Beta', 'Gamm', 'Delta']]
        values = {0: ['NONE'], 1: ['WRONG', 'ALPHA'], 2: ['BETA_MEGA_X'],
                  3: ['GAMMA'], 8: ['OUTSIDE']}
        result = subject.compare_source(records, values)
        self.assertEqual(result['literal'], 1)
        self.assertEqual(result['decorated_base_name_only'], 1)
        self.assertEqual(result['source_covered_fraction'], '2/4')
        self.assertEqual(result['complete_domain_fraction'], '2/5')
        self.assertEqual(result['mismatch_ids'], [0, 3])
        self.assertEqual(result['source_uncovered_rom_ids'], [4])
        self.assertEqual(result['source_out_of_domain_ids'], [8])
        self.assertEqual(subject.normalized('Flabébé'), 'FLABEBE')
        self.assertEqual(subject.normalized('Nidoran♀'), 'NIDORANF')


if __name__ == '__main__':
    unittest.main()
