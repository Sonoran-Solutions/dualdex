"""ROM-free invented table bytes, matching failures and committed-artifact integrity."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import generate_unbound_names as gen


def field(text, width):
    return (bytes(0xBB + ord(c) - ord('A') for c in text) + b'\xff').ljust(width, b'\xff')


def fixture():
    chunks, tables, offset = [], {}, 0
    for key, stride in [('MOVE', 13), ('ABILITY', 17), ('ITEM', 44)]:
        if key == 'ITEM':
            # One valid row, one valid name with a mismatched record ID.
            raw = b''.join(field('ALPHA', 14) + i.to_bytes(2, 'little') + bytes(28)
                           for i in (0, 1, 1))
        else:
            raw = field('ALPHA', stride) + field('BETA', stride) + field('GAMMA', stride)
        tables[gen.TABLES[key]] = dict(file_offset=hex(offset), count=3, stride=stride,
                                       sha256=gen.sha(raw))
        chunks.append(raw)
        offset += len(raw)
    tables['items']['record_id_mismatch_ids'] = [2]
    return b''.join(chunks), dict(rom={'sha256': gen.species.PINNED_ROM_SHA256}, tables=tables)


class GenerateUnboundNamesTest(unittest.TestCase):
    def test_normalization_ambiguity_missing_invalid_and_reserved(self):
        rows = [{'status': 'VERIFIED', 'name': s} for s in
                ('Alpha', 'bÉ-ta', 'GAMMA', 'Missing', 'Alpha')]
        rows.append({'status': 'NOT_VERIFIED', 'name': 'Alpha'})
        cats = [(1, 'Alpha'), (2, 'BETA'), (3, 'Gam-ma'), (4, 'Gamma')]
        self.assertEqual([0, 2, 0, 0, 0, 0], gen.match_names(rows, cats, excluded=[4]))

    def test_real_readers_decode_synthetic_tables_and_exclude_mismatched_items(self):
        rom, contract = fixture()
        cats = {key: [(7, 'Alpha'), (8, 'Beta'), (9, 'Gamma')] for key in gen.TABLES}
        with patch.object(gen.species, 'ROM_SIZE', len(rom)), \
             patch.object(gen.species, 'PINNED_ROM_SHA256', gen.sha(rom)):
            contract['rom']['sha256'] = gen.sha(rom)
            result = gen.derive(rom, contract, cats)
        self.assertEqual({'MOVE': [0, 8, 9], 'ABILITY': [0, 8, 9], 'ITEM': [0, 7, 0]}, result)

    def test_wrong_rom_hash_refused_before_decode(self):
        with patch.object(gen, 'decode_tables') as decode, patch.object(gen.species, 'ROM_SIZE', 8):
            with self.assertRaisesRegex(ValueError, 'refusing ROM'):
                gen.derive(b'invented', {}, {})
            decode.assert_not_called()

    def test_table_hash_mismatch_refused_before_decode(self):
        for label in gen.TABLES.values():
            rom, contract = fixture()
            contract['tables'][label]['sha256'] = '0' * 64
            with self.subTest(table=label), self.assertRaisesRegex(ValueError, 'table SHA-256 mismatch'):
                gen.decode_tables(rom, contract)

    def test_item_record_mismatches_must_equal_contract(self):
        rom, contract = fixture()
        contract['tables']['items']['record_id_mismatch_ids'] = []
        with self.assertRaisesRegex(ValueError, 'item record IDs changed'):
            gen.decode_tables(rom, contract)

    def test_round_trip_tampering_and_provenance(self):
        _, contract = fixture()
        cats = {key: [(7, 'Alpha'), (8, 'Beta'), (9, 'Gamma')] for key in gen.TABLES}
        tables = {'MOVE': [0, 8, 9], 'ABILITY': [0, 8, 9], 'ITEM': [0, 7, 0]}
        pins = gen.provenance(contract)
        text = gen.render(tables, pins)
        self.assertEqual(tables, gen.verify(text, contract, cats))
        contract['rom']['sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'contract ROM mismatch'):
            gen.verify(text, contract, cats)
        contract['rom']['sha256'] = gen.species.PINNED_ROM_SHA256
        for bad in (text.replace('/* 0 */ 0, 8, 9,', '/* 0 */ 0, 9, 8,', 1),
                    text.replace(pins['ROM_SHA256'], '0' * 64),
                    text.replace('/* 0 */ 0, 7, 0,', '/* 0 */ 0, 7, 7,'),
                    text.replace('/* 0 */ 0, 8, 9,', '/* 0 */ 0, 8, 9999,'),
                    text.replace('internal object', 'internal  object')):
            with self.subTest(), self.assertRaises(ValueError):
                gen.verify(bad, contract, cats)

    def test_cli_verifies_without_rom_and_refuses_wrong_rom(self):
        script = Path(gen.__file__)
        verified = subprocess.run([sys.executable, str(script), '--verify'],
                                  capture_output=True, timeout=10)
        self.assertEqual(0, verified.returncode, verified.stderr)
        with tempfile.TemporaryDirectory() as directory:
            wrong = Path(directory) / 'invented.bin'
            wrong.write_bytes(b'invented')
            output = Path(directory) / 'table.kt'
            refused = subprocess.run([sys.executable, str(script), '--rom', str(wrong),
                                      '--output', str(output)], capture_output=True, timeout=10)
            self.assertEqual(1, refused.returncode)
            self.assertIn(b'refusing ROM', refused.stderr)
            self.assertFalse(output.exists())

    def test_committed_artifact_and_catalogue_sources(self):
        cats = gen.catalogues()
        self.assertGreater(len(cats['MOVE']), 350)
        self.assertGreater(len(cats['ABILITY']), 250)
        self.assertGreater(len(cats['ITEM']), 500)
        gen.verify(gen.OUTPUT.read_text(), json.loads(gen.CONTRACT.read_text()), cats)


if __name__ == '__main__':
    unittest.main()
