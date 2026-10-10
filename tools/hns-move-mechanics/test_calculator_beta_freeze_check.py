#!/usr/bin/env python3
"""Negative mutation tests for the frozen calculator-beta evidence inventory."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

import check_calculator_beta_freeze as freeze

REPO = Path(__file__).resolve().parents[2]


class CalculatorBetaFreezeCheckTest(unittest.TestCase):
    def test_committed_inventory_is_intact(self):
        self.assertEqual([], freeze.verify(REPO))

    def test_corrupted_evidence_artifact_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            doc = json.loads((REPO / freeze.SNAPSHOT).read_text(encoding="utf-8"))
            for entry in doc["evidenceInventory"]:
                target = root / entry["path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(REPO / entry["path"], target)
            (root / freeze.SNAPSHOT).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / freeze.SNAPSHOT, root / freeze.SNAPSHOT)
            self.assertEqual([], freeze.verify(root))
            # Corrupt the Rapid Spin lifecycle evidence by one observed HP value.
            victim = root / "tools/hns-damage-oracle/rapid-spin-evidence.json"
            text = victim.read_text(encoding="utf-8")
            self.assertIn('"targetHp": 188', text)
            victim.write_text(text.replace('"targetHp": 188', '"targetHp": 187', 1), encoding="utf-8")
            problems = freeze.verify(root)
            self.assertEqual(["sha256 mismatch: tools/hns-damage-oracle/rapid-spin-evidence.json"], problems)

    def test_missing_evidence_file_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            doc = json.loads((REPO / freeze.SNAPSHOT).read_text(encoding="utf-8"))
            (root / freeze.SNAPSHOT).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / freeze.SNAPSHOT, root / freeze.SNAPSHOT)
            problems = freeze.verify(root)
            self.assertTrue(any(p.startswith("missing: ") for p in problems))
            self.assertEqual(len(doc["evidenceInventory"]), len(problems))


if __name__ == "__main__":
    unittest.main()
