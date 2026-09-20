#!/usr/bin/env python3
"""
Unit tests for the H&S 2.0.5 type system and Fairy mappings generator.

Runs self-contained without needing an external upstream repository.
"""

import json
import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate_hns_type_system as gen


class TestHnsTypeSystemGenerator(unittest.TestCase):

    def setUp(self):
        self.repo_root = Path(__file__).resolve().parent.parent.parent
        self.json_path = self.repo_root / "tools/calc-bundler/hns_type_chart.json"
        self.kt_path = self.repo_root / "app/src/main/java/com/dualdex/pokemon/hns/HnsFairyTypeMappings.kt"

    def test_committed_artifacts_exist(self):
        self.assertTrue(self.json_path.exists(), f"Missing {self.json_path}")
        self.assertTrue(self.kt_path.exists(), f"Missing {self.kt_path}")

    def test_type_chart_goldens(self):
        chart = json.loads(self.json_path.read_text(encoding="utf-8"))
        self.assertEqual(len(chart), 19, "Expected 19 attacking types")

        for atk, targets in chart.items():
            self.assertEqual(len(targets), 19, f"Attacker {atk} must have 19 defending types")

        # Pinned representative expectations
        self.assertEqual(chart["Ghost"]["Steel"], 1.0, "Ghost -> Steel must be neutral (1.0)")
        self.assertEqual(chart["Dark"]["Steel"], 1.0, "Dark -> Steel must be neutral (1.0)")
        self.assertEqual(chart["Fairy"]["Dragon"], 2.0, "Fairy -> Dragon must be super-effective (2.0)")
        self.assertEqual(chart["Dragon"]["Fairy"], 0.0, "Dragon -> Fairy must be immune (0.0)")
        self.assertEqual(chart["Fairy"]["Steel"], 0.5, "Fairy -> Steel must be resisted (0.5)")
        self.assertEqual(chart["Fairy"]["Poison"], 0.5, "Fairy -> Poison must be resisted (0.5)")
        self.assertEqual(chart["Fairy"]["Fire"], 0.5, "Fairy -> Fire must be resisted (0.5)")
        self.assertEqual(chart["Steel"]["Fairy"], 2.0, "Steel -> Fairy must be super-effective (2.0)")
        self.assertEqual(chart["Poison"]["Fairy"], 2.0, "Poison -> Fairy must be super-effective (2.0)")
        self.assertEqual(chart["Normal"]["Normal"], 1.0, "Normal -> Normal must be neutral (1.0)")
        self.assertEqual(chart["Normal"]["Rock"], 0.5, "Normal -> Rock must be resisted (0.5)")
        self.assertEqual(chart["Normal"]["Ghost"], 0.0, "Normal -> Ghost must be immune (0.0)")

    def test_pre_fairy_species_mappings(self):
        kt_content = self.kt_path.read_text(encoding="utf-8")

        # 20 species
        expected_species = {
            "cleffa": 'listOf("Normal")',
            "clefairy": 'listOf("Normal")',
            "clefable": 'listOf("Normal")',
            "igglybuff": 'listOf("Normal")',
            "jigglypuff": 'listOf("Normal")',
            "wigglytuff": 'listOf("Normal")',
            "togepi": 'listOf("Normal")',
            "togetic": 'listOf("Normal", "Flying")',
            "togekiss": 'listOf("Normal", "Flying")',
            "azurill": 'listOf("Normal")',
            "marill": 'listOf("Water")',
            "azumarill": 'listOf("Water")',
            "snubbull": 'listOf("Normal")',
            "granbull": 'listOf("Normal")',
            "ralts": 'listOf("Psychic")',
            "kirlia": 'listOf("Psychic")',
            "gardevoir": 'listOf("Psychic")',
            "mime jr.": 'listOf("Psychic")',
            "mr. mime": 'listOf("Psychic")',
            "mawile": 'listOf("Steel")',
        }

        for species, types_code in expected_species.items():
            pattern = f'"{species}" to {types_code}'
            self.assertIn(pattern, kt_content, f"Missing or wrong pre-Fairy mapping for {species}")

    def test_fairy_move_alt_mappings(self):
        kt_content = self.kt_path.read_text(encoding="utf-8")

        expected_moves = {
            "moonblast": '"Dark"',
            "dazzling gleam": '"Normal"',
            "play rough": '"Normal"',
            "flower shield": '"Grass"',
            "geomancy": '"Psychic"',
            "spirit break": '"Fighting"',
            "springtide storm": '"Flying"',
            "magical torque": '"Psychic"',
            "moonlight": '"Dark"',
        }

        for move, alt in expected_moves.items():
            pattern = f'"{move}" to {alt}'
            self.assertIn(pattern, kt_content, f"Missing or wrong alternate type for {move}")


if __name__ == "__main__":
    unittest.main()
