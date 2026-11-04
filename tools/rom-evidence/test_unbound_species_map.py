"""ROM-free invented data and sanitized metadata; no private files or sources."""
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import unbound_species_map as subject


class UnboundSpeciesMapTest(unittest.TestCase):
    def test_exact_hash_gate_precedes_all_interpretation(self):
        with patch.object(subject, "validate_layout") as layout, \
             patch.object(subject, "compare_sources") as sources, \
             patch.object(subject, "check_runtime_pins") as runtime:
            with self.assertRaisesRegex(ValueError, "size"):
                subject.inspect(b"invented", Path("missing"))
            with patch("unbound_identity.ROM_SIZE", 8):
                with self.assertRaisesRegex(ValueError, "SHA-256"):
                    subject.inspect(b"invented", Path("missing"))
            layout.assert_not_called()
            sources.assert_not_called()
            runtime.assert_not_called()

    def test_physical_table_starts_at_species_one(self):
        raw = b"PAD!" + struct.pack("<3H", 12, 513, 0) + b"suffix"
        self.assertEqual(subject.read_dex_table(raw, 4, 4), [0, 12, 513, 0])

    def test_table_bounds_and_domain_refuse(self):
        for offset, count in [(-1, 2), (True, 2), (0, 1), (0, 4097),
                              (0, True), (0, 2.0), (0, 3), (1, 2)]:
            with self.subTest(offset=offset, count=count), self.assertRaises(ValueError):
                subject.read_dex_table(b"\x01\x00", offset, count)

    def test_accessor_and_literal_mutations_refuse(self):
        raw = b"CODE" + struct.pack("<I", 123)
        with patch.object(subject, "ACCESSORS", {"test": (0, 4, subject.sha(b"CODE"))}), \
             patch.object(subject, "POINTERS", {4: 123}):
            subject.validate_layout(raw)
            with self.assertRaisesRegex(ValueError, "accessor mismatch"):
                subject.validate_layout(b"X" + raw[1:])
            with self.assertRaisesRegex(ValueError, "literal mismatch"):
                subject.validate_layout(raw[:-1] + b"X")
            with self.assertRaisesRegex(ValueError, "span outside"):
                subject.validate_layout(raw[:3])

    def test_dex_constants_identical_redefinitions_only(self):
        raw = b"#define NATIONAL_DEX_ALPHA 0x1\n#define NATIONAL_DEX_ALPHA 1\n"
        self.assertEqual(subject.parse_dex_header(raw), {"ALPHA": 1})
        for extra in [b"#define NATIONAL_DEX_ALPHA 2\n", b"#define NATIONAL_DEX_EVIL 4096\n"]:
            with self.assertRaisesRegex(ValueError, "conflicting"):
                subject.parse_dex_header(raw + extra)
        for raw in [b"", b"x" * (128 * 1024 + 1)]:
            with self.assertRaises(ValueError):
                subject.parse_dex_header(raw)

    def test_designated_initializer_includes_explicit_zero_holes(self):
        raw = b"const u16 fake[3] = { [SPECIES_ALPHA - 1] = NATIONAL_DEX_ALPHA, [2 - 1] = NATIONAL_DEX_NONE, [SPECIES_BETA - 1] = NATIONAL_DEX_BETA, };"
        self.assertEqual(subject.parse_dpe_table(raw, {1: ["ALPHA", "ALIAS"], 3: ["BETA"]},
                                               {"NONE": 0, "ALPHA": 9, "BETA": 10}),
                         {1: 9, 2: 0, 3: 10})

    def test_initializer_unknown_duplicate_hole_and_expression_refuse(self):
        for body in ["[SPECIES_OTHER - 1] = NATIONAL_DEX_ALPHA,",
                     "[1 - 1] = NATIONAL_DEX_OTHER,",
                     "[1 - 1] = NATIONAL_DEX_ALPHA, [1 - 1] = NATIONAL_DEX_ALPHA,",
                     "[2 - 1] = NATIONAL_DEX_ALPHA,",
                     "[0 - 1] = NATIONAL_DEX_ALPHA,",
                     "[1 - 1] = NATIONAL_DEX_ALPHA + 1,",
                     "[1 - 1] = NATIONAL_DEX_ALPHA, arbitrary();"]:
            with self.subTest(body=body), self.assertRaises(ValueError):
                subject.parse_dpe_table(("const u16 fake[] = {" + body + "};").encode(),
                                        {1: ["ALPHA"]}, {"ALPHA": 1})

    def test_canonical_suffixes_aliases_and_no_fuzzy_match(self):
        dex = {"ALPHA": 1, "ALPHA_BETA": 2, "GRENINJA": 3}
        self.assertEqual(subject.canonical_label("ALPHA_BETA_MEGA", dex), "ALPHA_BETA")
        self.assertIsNone(subject.canonical_label("ALPHABETA", dex))
        self.assertIsNone(subject.canonical_label("ASHGRENINJA", dex))
        self.assertEqual(subject.canonical_label("ASHGRENINJA", dex, aliases=True), "GRENINJA")
        self.assertIsNone(subject.canonical_label("SHADOW_WARRIOR", dex, aliases=True))

    def test_numeric_comparison_denominators_and_disagreements(self):
        result = subject.compare_mapping([0, 8, 9, 0], {1: 8, 2: 7, 3: 5, 6: 8})
        self.assertEqual((result["compared_ids"], result["agreeing_ids"]), (3, 1))
        self.assertEqual(result["disagreements"], [{"id": 2, "rom_dex": 9, "source_dex": 7},
                                                   {"id": 3, "rom_dex": 0, "source_dex": 5}])
        self.assertEqual(result["uncovered_ids"], [0])
        self.assertEqual(result["outside_rom_domain_ids"], [6])

    def test_all_class_buckets_and_unown_regional_collision(self):
        labels = ["NONE", "ALPHA", "ALPHA_MEGA", "ALPHA_PRIMAL", "ALPHA_GIGA",
                  "ALPHA_A", "ALPHA_G_ZEN", "ALPHA_H", "UNOWN", "UNOWN_G", "UNOWN_H",
                  "ALPHA_ORIGIN", "SHADOW_WARRIOR", "FLOETTE", "FLOETTE_BLUE", "FLOETTE_ETERNAL"]
        values = [0, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 1, 0, 3, 3, 3]
        classes = subject.classify(values, {i: [label] for i, label in enumerate(labels)},
                                   {"NONE": 0, "ALPHA": 1, "UNOWN": 2, "FLOETTE": 3})
        self.assertEqual(list(classes.values()), ["reserved_hole", "base_species", "mega_primal",
            "mega_primal", "gigantamax", "regional_form", "regional_form", "regional_form",
            "base_species", "cosmetic_form", "cosmetic_form", "other_alternate_form",
            "custom_unbound", "base_species", "cosmetic_form", "other_alternate_form"])

    def test_classification_refuses_conflicts_and_unexplained_zero(self):
        for mapping, labels in [([0, 1], {}), ([0, 0], {1: ["ALPHA"]}),
                                ([0, 2], {1: ["ALPHA"]}),
                                ([0, 1], {1: ["SHADOW_WARRIOR"]}),
                                ([0, 1], {1: ["ALPHA", "ALIAS"]})]:
            with self.subTest(mapping=mapping, labels=labels), self.assertRaises(ValueError):
                subject.classify(mapping, labels, {"ALPHA": 1})
        with self.assertRaisesRegex(ValueError, "reserved"):
            subject.classify([1, 1], {1: ["ALPHA"]}, {"ALPHA": 1})

    def test_source_hash_mismatch_fails_before_parsing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fake.h"
            path.write_bytes(b"invented source")
            with patch.object(subject, "SOURCE_FILES", {"cfru_species": ("fake.h", "0" * 64)}):
                with self.assertRaisesRegex(ValueError, "pinned source mismatch"):
                    subject.compare_sources([0, 1], [{"name": "?"}, {"name": "A"}], Path(directory))

    def test_source_comparison_synthetic_end_to_end(self):
        species = b"#define SPECIES_NONE 0\n#define SPECIES_ALPHA 1\n#define SPECIES_ALPHA_MEGA 2\n"
        dex = b"#define NATIONAL_DEX_NONE 0\n#define NATIONAL_DEX_ALPHA 1\n"
        table = b"const u16 fake[2] = {[1 - 1] = NATIONAL_DEX_ALPHA, [2 - 1] = NATIONAL_DEX_ALPHA,};"
        raw = {"cfru_species": species, "dpe_species": species,
               "cfru_dex": dex, "dpe_dex": dex, "dpe_table": table}
        with tempfile.TemporaryDirectory() as directory:
            files = {}
            for key, value in raw.items():
                relative = ("dpe" if key.startswith("dpe") else "cfru") + "/" + key
                path = Path(directory) / relative
                path.parent.mkdir(exist_ok=True)
                path.write_bytes(value)
                files[key] = (relative, subject.sha(value))
            with patch.object(subject, "SOURCE_FILES", files):
                result, classes, labels = subject.compare_sources([0, 1, 1],
                    [{"name": "?"}, {"name": "Alpha"}, {"name": "Alpha"}], Path(directory))
            self.assertEqual(result["numeric"]["dpe_table"]["agreeing_ids"], 2)
            self.assertEqual(result["numeric"]["cfru_constants"]["agreeing_ids"], 3)
            self.assertEqual(result["label_disagreements"], [])
            self.assertEqual(classes[2], "mega_primal")
            self.assertEqual(labels[2], ["ALPHA_MEGA"])

    def test_runtime_pin_reuse_binds_evidence_and_refuses_conflict(self):
        mapping = [0] * 1100
        evidence = dict(inputs={"rom": {"sha256": subject.ROM_SHA256}}, party=[])
        for index, dex in subject.RUNTIME_PINS.items():
            mapping[index] = dex
            evidence["party"].append(dict(raw_selected={"species_word": hex(index)},
                                           ui_transcription={"dex_no": dex}))
        raw = json.dumps(evidence).encode()
        result = subject.check_runtime_pins(mapping, raw)
        self.assertEqual(result["agreeing_pins"], 6)
        self.assertTrue(result["reused"])
        self.assertFalse(result["new_core_run"])
        self.assertEqual(result["sha256"], subject.sha(raw))
        mapping[942] = 999
        with self.assertRaisesRegex(ValueError, "disagreement"):
            subject.check_runtime_pins(mapping, raw)
        evidence["party"].append(evidence["party"][0])
        with self.assertRaisesRegex(ValueError, "party count"):
            subject.check_runtime_pins(mapping, json.dumps(evidence).encode())
        evidence["inputs"]["rom"]["sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "ROM mismatch"):
            subject.check_runtime_pins(mapping, json.dumps(evidence).encode())

    def test_cli_wrong_rom_no_json_and_no_source_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            rom = Path(directory) / "invented.bin"
            rom.write_bytes(b"invented")
            result = subprocess.run([sys.executable, str(Path(subject.__file__)), "--rom", str(rom),
                                     "--source-root", "missing"], capture_output=True, timeout=10)
            self.assertEqual(result.returncode, 1)
            self.assertEqual(result.stdout, b"")
            self.assertIn(b"size mismatch", result.stderr)

    def test_sanitized_evidence_counts_hashes_and_coverage(self):
        evidence = Path(subject.__file__).with_name("evidence") / "unbound-species-map-contract.json"
        report = json.loads(evidence.read_bytes())
        self.assertEqual(report["inspector_sha256"], subject.sha(Path(subject.__file__).read_bytes()))
        for filename, digest in report["dependency_sha256"].items():
            self.assertEqual(digest, subject.sha(Path(subject.__file__).with_name(filename).read_bytes()))
        self.assertEqual(report["table"]["sha256"], subject.TABLE_HASH)
        counts = report["classification"]["counts"]
        self.assertEqual(sum(counts.values()), 1294)
        self.assertEqual(counts, dict(base_species=905, cosmetic_form=102, custom_unbound=1,
            gigantamax=34, mega_primal=50, other_alternate_form=116, regional_form=59, reserved_hole=27))
        self.assertLess(len(report["representative_rows"]), 40)
        self.assertEqual(set(counts), {row["classification"] for row in report["representative_rows"]})
        self.assertEqual(report["numeric_domain"]["nonzero_rows"], 1266)
        self.assertEqual(report["reused_runtime_pins"]["agreeing_pins"], 6)
        numeric = report["source_comparisons"]["numeric"]
        self.assertEqual((numeric["dpe_table"]["compared_ids"], numeric["dpe_table"]["agreeing_ids"]), (1267, 1232))
        self.assertEqual([r["id"] for r in numeric["dpe_table"]["disagreements"]], [706, *range(1234, 1268)])
        self.assertEqual((numeric["cfru_constants"]["compared_ids"], numeric["cfru_constants"]["agreeing_ids"]), (1265, 1265))
        self.assertEqual(report["form_display_names"], "NOT_VERIFIED")
        self.assertEqual(report["production_support"], "NOT_VERIFIED")


if __name__ == "__main__":
    unittest.main()
