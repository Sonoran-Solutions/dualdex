"""ROM-free tests: invented tables and the committed artifact only; no private files."""
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import generate_unbound_species as gen  # noqa: E402

PROVENANCE = dict(rom_sha256=gen.PINNED_ROM_SHA256, table_file_offset=gen.species_map.TABLE_OFFSET,
                  table_rom_address=gen.ROM_BASE + gen.species_map.TABLE_OFFSET,
                  table_sha256=gen.species_map.TABLE_HASH, generator_sha256="1" * 64,
                  inspector_sha256="2" * 64, cfru_species_sha256="3" * 64)


def invented_rows():
    """A synthetic table with the pinned zero set and class counts; not real ROM data."""
    zero = gen.RESERVED | gen.HOLES | gen.CUSTOM_IDS
    rows, next_dex = [], 1
    extra = [code for code, name in enumerate(gen.FORM_CLASSES)
             for _ in range(gen.EXPECTED_COUNTS[name]) if name not in ("base", "custom", "reserved")]
    for i in range(gen.SPECIES_COUNT):
        if i in zero:
            rows.append((0, gen.FORM_CLASSES.index("custom" if i in gen.CUSTOM_IDS else "reserved")))
        elif next_dex <= gen.MAX_DEX:
            rows.append((next_dex, 0))
            next_dex += 1
        else:
            rows.append((1, extra.pop()))
    return rows


class GenerateUnboundSpeciesTest(unittest.TestCase):
    def test_wrong_rom_refused_before_any_interpretation(self):
        with patch.object(gen.species_map, "inspect") as inspect:
            for rom in (b"invented", b"\0" * gen.ROM_SIZE):
                with self.subTest(size=len(rom)), self.assertRaisesRegex(ValueError, "refusing ROM"):
                    gen.derive(rom, Path("missing"))
            inspect.assert_not_called()

    def test_derive_maps_inspector_classes_to_codes(self):
        rows = invented_rows()
        mapping = [n for n, _ in rows]
        evidence = {v: k for k, v in gen.EVIDENCE_CLASS.items()}
        classes = {i: evidence[gen.FORM_CLASSES[c]] for i, (_, c) in enumerate(rows)}
        counts = {evidence[k]: v for k, v in gen.EXPECTED_COUNTS.items()}
        report = dict(classification=dict(status="OBSERVED", counts=counts), rom=dict(sha256="4" * 64),
                      table=dict(file_offset="0x01a41fec", sha256="5" * 64), inspector_sha256="6" * 64)
        source = b"#define SPECIES_X 1\n"
        digests = {b"R": gen.PINNED_ROM_SHA256, source: gen.species_map.SOURCE_FILES["cfru_species"][1],
                   b"D": gen.species_map.SOURCE_FILES["cfru_dex"][1]}
        with patch.object(gen, "sha", side_effect=lambda data: digests.get(data, "7" * 64)), \
             patch.object(gen, "ROM_SIZE", 1), \
             patch.object(gen, "read_local", side_effect=lambda path, _: source if "species" in str(path) else b"D"), \
             patch.object(gen.species_map, "inspect", return_value=report) as inspect, \
             patch.object(gen.species_map, "read_dex_table", return_value=mapping) as table, \
             patch.object(gen.species_map, "parse_species_header", return_value="labels"), \
             patch.object(gen.species_map, "parse_dex_header", return_value="dex"), \
             patch.object(gen.species_map, "classify", return_value=classes) as classify:
            derived, provenance = gen.derive(b"R", Path("root"))
        self.assertEqual(derived, rows)
        inspect.assert_called_once_with(b"R", Path("root"))
        table.assert_called_once_with(b"R", 0x01A41FEC, gen.SPECIES_COUNT)
        classify.assert_called_once_with(mapping, "labels", "dex")
        self.assertEqual(provenance["table_rom_address"], 0x09A41FEC)
        self.assertEqual(provenance["inspector_sha256"], "6" * 64)

    def test_round_trip_and_hand_edits_refused(self):
        text = gen.render(invented_rows(), PROVENANCE)
        rows, digest = gen.verify(text)
        self.assertEqual(rows, invented_rows())
        self.assertEqual(digest, gen.mapping_digest(rows))
        edits = {
            "dex value": text.replace("/* 0 */ 0, 1, 2,", "/* 0 */ 0, 1, 3,", 1),
            "rom pin": text.replace(gen.PINNED_ROM_SHA256, "0" * 64),
            "table location": text.replace("0x01A41FEC", "0x01A41FEE"),
            "digest": text.replace(digest, "0" * 64),
            "non-numeric": text.replace("/* 16 */ 16,", "/* 16 */ 0x10,", 1),
            "formatting": text.replace("internal object", "internal  object"),
            "truncated": text.replace(text[text.index("/* 1280 */"):text.index("\n", text.index("/* 1280 */")) + 1], "", 1),
        }
        for label, edited in edits.items():
            with self.subTest(label), self.assertRaises(ValueError):
                gen.verify(edited)

    def test_structural_invariants_refuse(self):
        base = invented_rows()
        cases = {
            "reserved with dex": {252: (5, 7)},
            "custom zero must stay custom": {706: (0, 7)},
            "base without dex": {1: (0, 0)},
            "dex above domain": {1: (906, 0)},
            "unknown class": {1: (1, 8)},
            "form before base": {1293: (1, 0)},
        }
        for label, changes in cases.items():
            rows = list(base)
            for i, row in changes.items():
                rows[i] = row
            with self.subTest(label), self.assertRaises(ValueError):
                gen.check_rows(rows)
        with self.assertRaisesRegex(ValueError, "row count"):
            gen.check_rows(base[:-1])

    def test_committed_artifact_pins(self):
        rows, _ = gen.verify(gen.DEFAULT_OUTPUT.read_text(encoding="utf-8"))
        name = {i: (n, gen.FORM_CLASSES[c]) for i, (n, c) in enumerate(rows)}
        expected = {277: (252, "base"), 440: (387, "base"), 942: (725, "base"), 1002: (785, "base"),
                    1102: (810, "base"), 1035: (89, "regional"), 869: (3, "mega_primal"),
                    706: (0, "custom"), 253: (0, "reserved"), 254: (0, "reserved"),
                    130: (130, "base"), 230: (230, "base"), 186: (186, "base")}
        for i, value in expected.items():
            self.assertEqual(name[i], value, i)

    def test_cli(self):
        script = str(Path(gen.__file__))
        ok = subprocess.run([sys.executable, script, "--verify"], capture_output=True, text=True)
        self.assertEqual(ok.returncode, 0, ok.stderr)
        self.assertIn("1294 rows", ok.stdout)
        missing = subprocess.run([sys.executable, script, "--rom", "missing.gba"], capture_output=True, text=True)
        self.assertEqual(missing.returncode, 1)
        self.assertIn("--source-root", missing.stderr)


if __name__ == "__main__":
    unittest.main()
