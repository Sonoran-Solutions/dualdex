"""ROM-free numeric artifact and synthetic helper tests; no game text fixtures."""
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_unbound_map_data as gen  # noqa: E402


class GenerateUnboundMapDataTest(unittest.TestCase):
    def test_wrong_rom_refused_before_interpreting_offsets(self):
        with patch.object(gen.location, 'inspect_map_header_path') as inspect:
            for rom in (b'invented', b'\0' * gen.ROM_SIZE):
                with self.subTest(size=len(rom)), self.assertRaises(ValueError):
                    gen.derive(rom)
            inspect.assert_not_called()

    def test_committed_artifact_and_spot_pins(self):
        counts, sections = gen.verify(gen.OUTPUT.read_text())
        rows = gen.rows_for(counts, sections)
        self.assertEqual((len(counts), len(rows)), (57, 645))
        self.assertTrue(all(0x58 <= section <= 0xC4 for _, _, section in rows))
        self.assertEqual(gen.rows_sha256(rows), gen.ROWS_SHA256)
        by_pair = {(g, n): s for g, n, s in rows}
        self.assertEqual(by_pair[1, 7], 0x80)
        self.assertEqual(by_pair[3, 6], 0x93)

    def test_derivation_reuses_inspector_and_header_helpers(self):
        counts, sections = gen.verify(gen.OUTPUT.read_text())
        summary = dict(maps_per_group=counts, rows_sha256=gen.ROWS_SHA256)
        names = dict(table=hex(gen.NAME_TABLE_ADDRESS), first_mapsec='0x58', last_mapsec='0xc4')
        rom = bytearray(64)
        rom[20], rom[24] = 0x80, 0x93
        with patch.object(gen, 'exact_rom') as identity, \
             patch.object(gen.location, 'inspect_map_header_path', return_value=dict(
                 map_groups_table='0x08000100', group_pointer_xor_key='0x1234')) as path, \
             patch.object(gen.location, 'enumerate_maps', return_value=(summary, gen.rows_for(counts, sections))) as enumerate_maps, \
             patch.object(gen.location, 'inspect_map_names', return_value=names) as inspect_names, \
             patch.object(gen.location, 'map_header', side_effect=[0x08000000, 0x08000004]) as header:
            self.assertEqual(gen.derive(rom), (counts, sections))
        identity.assert_called_once_with(rom)
        path.assert_called_once_with(rom)
        enumerate_maps.assert_called_once_with(rom, 0x08000100, 0x1234)
        inspect_names.assert_called_once_with(rom)
        self.assertEqual(header.call_count, 2)

    def test_reformatted_changed_or_rehashed_rows_are_refused(self):
        text = gen.OUTPUT.read_text()
        counts, sections = gen.verify(text)
        changed = list(sections)
        changed[0] = 0x58 if changed[0] != 0x58 else 0x59
        altered_counts = list(counts)
        altered_counts[0] += 1
        altered_counts[1] -= 1
        for edited in (text + '\n', text.replace(gen.ROM_SHA256, '0' * 64),
                       text.replace(gen.ROWS_SHA256, '0' * 64),
                       text.replace('0x083F1CAC', '0x083F1CB0'),
                       text.replace('const val FIRST_SECTION: Int = 0x58', 'const val FIRST_SECTION: Int = 0x59'),
                       text.replace('private val SECTIONS', 'val SECTIONS'),
                       gen.render(counts, changed), gen.render(altered_counts, sections)):
            with self.subTest(edit=edited[:40]), self.assertRaises(ValueError):
                gen.verify(edited)

    def test_structural_bounds(self):
        counts, sections = gen.verify(gen.OUTPUT.read_text())
        for bad_counts, bad_sections in ((counts[:-1], sections), (counts, sections[:-1]),
                                         ([0] + counts[1:], sections),
                                         (counts, [0x57] + sections[1:]),
                                         (counts, [0xC5] + sections[1:])):
            with self.subTest(counts=len(bad_counts)), self.assertRaises(ValueError):
                gen.rows_for(bad_counts, bad_sections)

    def test_cli_rom_free(self):
        result = subprocess.run([sys.executable, gen.__file__, '--verify'],
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('57 groups, 645 rows', result.stdout)


if __name__ == '__main__':
    unittest.main()
