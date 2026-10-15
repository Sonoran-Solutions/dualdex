"""Invented bytes only. Static equality is never runtime confirmation."""
import struct
import unittest
from contextlib import ExitStack
from types import SimpleNamespace
from unittest.mock import patch

import rowe_data as data
import rowe_save_identity as evidence
from rowe_text import VALID_TEXT, EMPTY_OR_UNSET


class SaveIdentityTest(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        for module in (data, evidence):
            self.stack.enter_context(patch.object(module, 'rom_identity', return_value={}))
        self.stack.enter_context(patch.object(data, 'FORM_POINTER_TABLE', 0x08000000))
        self.stack.enter_context(patch.object(data, '_require_symbols', return_value={'gBaseStats': 0x08002000}))
        self.stack.enter_context(patch.object(evidence, '_require_symbols', return_value={'gBaseStats': 0x08002000}))
        records = [SimpleNamespace(status=EMPTY_OR_UNSET, text='') for _ in range(1562)]
        for i in (52, 1560): records[i] = SimpleNamespace(status=VALID_TEXT, text='Invented')
        self.stack.enter_context(patch.object(data, '_species_records', return_value=(records, {}, [])))
        self.rom = bytearray(110000)
        for i in (52, 1560): struct.pack_into('<I', self.rom, i*4, 0x08001880)
        struct.pack_into('<3H', self.rom, 0x1880, 52, 1560, 65535)
        self.before = bytearray((i*7+3) % 256 for i in range(76))
        struct.pack_into('<I', self.before, 28, 1560 | (1 << 12) | (44 << 17))
        self.after = self.before.copy()
        struct.pack_into('<I', self.after, 28, 52 | (1 << 12) | (44 << 17))
        self.party = bytes(self.before) + bytes(380)
        self.save = bytearray(131072)
        self.sector(0)

    def sector(self, base):
        self.save[base+0x234] = 1
        self.save[base+0x238:base+0x400] = self.party
        total = sum(struct.unpack_from('<1021I', self.save, base)) & 0xffffffff
        checksum = ((total & 65535) + (total >> 16)) & 65535
        struct.pack_into('<HHII', self.save, base+0xff4, 1, checksum, 0x08012025, 3)

    def test_both_raw_representations_and_stable_fields(self):
        result = evidence.compare_records(self.rom, self.before, self.after)
        self.assertEqual(result['changed_offsets'], [28, 29])
        self.assertTrue(result['same_static_identity'])
        self.assertTrue(result['stable_decoded_fields_except_species'])
        self.assertEqual([x['raw_stored_species_id'] for x in result['raw_identities']], [1560, 52])
        for row in result['raw_identities']:
            self.assertEqual((row['raw_form_id'], row['metadata_lookup_key']), (1, 1560))
        self.assertEqual(result['runtime_confirmation'], 'NOT_VERIFIED')
        self.after[0] ^= 1
        self.assertFalse(evidence.compare_records(self.rom, self.before, self.after)['stable_decoded_fields_except_species'])

    def test_full_record_changes_and_truncation(self):
        for offset in range(76):
            changed = self.before.copy(); changed[offset] ^= 128
            if offset in (28, 29, 30): continue  # may produce an unsupported identity
            result = evidence.compare_records(self.rom, self.before, changed)
            self.assertEqual(result['changed_offsets'], [offset])
        with self.assertRaises(ValueError): evidence.compare_records(self.rom, self.before[:-1], self.after)

    def test_no_fallback_holes_unknown_invalid_form_and_form_zero(self):
        for species, form in [(1561, 1), (4095, 1), (52, 2), (52, 31)]:
            changed = self.before.copy()
            struct.pack_into('<I', changed, 28, species | (form << 12))
            with self.assertRaises(ValueError): evidence.compare_records(self.rom, self.before, changed)
        changed = self.before.copy(); struct.pack_into('<I', changed, 28, 52)
        self.assertFalse(evidence.compare_records(self.rom, self.before, changed)['same_static_identity'])
        self.rom[0x1880:0x18c0] = b'\x34\x00'*32
        with self.assertRaises(ValueError): evidence.compare_records(self.rom, self.before, self.after)

    def test_serialized_record_is_independent_of_loaded_record(self):
        result = evidence.inspect_cartridge(self.rom, self.save, self.party, 1)
        self.assertEqual(result['occupied'][0]['species'], 1560)
        self.assertEqual(result['loader_selection_status'], 'NOT_VERIFIED')
        with self.assertRaises(ValueError):
            evidence.inspect_cartridge(self.rom, self.save, bytes(self.after)+bytes(380), 1)

    def test_missing_ambiguous_and_corrupt_evidence_refused(self):
        self.sector(0x1000)
        with self.assertRaises(ValueError): evidence.inspect_cartridge(self.rom, self.save, self.party, 1)
        self.save[0x1000+0xff4] = 2
        for offset in (0, 0xff6, 0xff8, 0x234):
            damaged = self.save.copy(); damaged[offset] ^= 1
            with self.assertRaises(ValueError): evidence.inspect_cartridge(self.rom, damaged, self.party, 1)
        for party, count in [(self.party[:-1], 1), (self.party, 0), (self.party, True)]:
            with self.assertRaises(ValueError): evidence.inspect_cartridge(self.rom, self.save, party, count)

    def test_wrong_rom_refused_before_any_evidence(self):
        self.stack.close()
        with self.assertRaises(ValueError): evidence.compare_records(b'wrong', self.before, self.after)
        with self.assertRaises(ValueError): evidence.inspect_cartridge(b'wrong', self.save, self.party, 1)


if __name__ == '__main__':
    unittest.main()
