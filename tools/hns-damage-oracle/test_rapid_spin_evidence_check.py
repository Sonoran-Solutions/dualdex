#!/usr/bin/env python3
"""Negative mutation tests for the committed Rapid Spin lifecycle observations."""
import json
import tempfile
import unittest
from pathlib import Path

import rapid_spin_evidence as rs

TARGET = rs.TARGET


class RapidSpinEvidenceCheckTest(unittest.TestCase):
    def test_committed_artifact_passes(self):
        rs.check_artifact(TARGET)

    def test_corrupted_lifecycle_observation_fails(self):
        doc = json.loads(TARGET.read_text(encoding="utf-8"))
        for row in doc["lifecycle"]:
            if row["id"] == "sheer-force-suppresses":
                row["observed"]["targetHp"] = 187
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "mutated.json"
            path.write_text(json.dumps(doc), encoding="utf-8")
            with self.assertRaises(AssertionError):
                rs.check_artifact(path)

    def test_leech_seed_state_mutation_fails(self):
        doc = json.loads(TARGET.read_text(encoding="utf-8"))
        for row in doc["lifecycle"]:
            if row["id"] == "cleanup-leech":
                row["observed"]["userLeechSeed"] = True
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "mutated.json"
            path.write_text(json.dumps(doc), encoding="utf-8")
            with self.assertRaises(AssertionError):
                rs.check_artifact(path)


if __name__ == "__main__":
    unittest.main()
