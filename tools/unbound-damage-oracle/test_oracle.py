"""ROM/source/compiler-free integrity and regression checks."""
import copy
import json
from pathlib import Path
import unittest

import oracle
from matrix import FIELDS, TYPES, cases


class CorpusTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = (Path(__file__).parent / "corpus.json").read_bytes()
        cls.document = json.loads(cls.raw)

    def test_canonical_schema_and_complete_matrix(self):
        self.assertEqual(oracle.check(self.document), 579)
        self.assertEqual(oracle.encoded(self.document), self.raw)
        self.assertEqual(len({tuple(c["input"][k] for k in FIELDS) for c in cases()}), 579)

    def test_full_chart_and_stage_domains(self):
        operands = [entry["input"] for entry in self.document["cases"]]
        for attack_type in TYPES:
            for defense_type in TYPES:
                self.assertTrue(any(c["move_type"] == attack_type and c["defender_type_1"] == defense_type
                                    and c["defender_type_2"] == defense_type for c in operands))
        for field in FIELDS[12:16]:
            self.assertEqual({c[field] for c in operands}, set(range(13)))
        for category in (0, 1):
            for weather in (0, 1, 32):
                self.assertTrue(any(c["category"] == category and c["weather"] == weather for c in operands))

    def test_independent_plain_hit_arithmetic_canary(self):
        # L50, BP80, Atk123, Def101: floor(floor(22*80*123/101)/50)+2 = 44;
        # STAB floor(44*15/10) = 66; random floor(66*r/100), r=85..100.
        self.assertEqual(self.document["cases"][0]["rolls"],
                         [56, 56, 57, 58, 58, 59, 60, 60, 61, 62, 62, 63, 64, 64, 65, 66])

    def test_immunity_and_minimum_damage_controls(self):
        cases = self.document["cases"]
        self.assertTrue(any(c["rolls"] == [0] * 16 for c in cases))
        self.assertTrue(any(c["rolls"] == [1] * 16 for c in cases))
        # Dragon into Fairy is immune; Fairy into Dragon is damaging.
        for attack, defense, immune in ((16, 23, True), (23, 16, False)):
            selected = [c for c in cases if c["input"]["move_type"] == attack
                        and c["input"]["defender_type_1"] == defense
                        and c["input"]["defender_type_2"] == defense]
            self.assertTrue(selected)
            self.assertEqual(selected[0]["rolls"][-1] == 0, immune)

    def test_stab_either_slot_and_no_double_count(self):
        matches = {}
        for case in self.document["cases"]:
            inp = case["input"]
            if (inp["category"] == 0 and inp["move_type"] == 23 and inp["weather"] == 0
                    and inp["defender_type_1"] == inp["defender_type_2"] == 0
                    and inp["attacker_ability"] == inp["attacker_hold_effect"] == 0
                    and inp["screens"] == inp["critical"] == inp["attacker_status"] == 0):
                matches[(inp["attacker_type_1"], inp["attacker_type_2"])] = case["rolls"]
        self.assertEqual(matches[(0, 23)], matches[(23, 0)])
        self.assertEqual(matches[(0, 23)], matches[(23, 23)])
        self.assertGreater(matches[(23, 23)][-1], matches[(0, 0)][-1])

    def test_reject_corrupt_corpora(self):
        mutations = (
            lambda d: d.update(schema_version=2),
            lambda d: d["provenance"].update(cfru_commit="0" * 40),
            lambda d: d["provenance"].update(harness_sha256="0" * 64),
            lambda d: d["cases"].pop(),
            lambda d: d["cases"][0].update(id=2),
            lambda d: d["cases"][0]["rolls"].pop(),
            lambda d: d["cases"][0]["rolls"].__setitem__(0, -1),
            lambda d: d["cases"][0]["rolls"].__setitem__(0, 100),
            lambda d: d["cases"][0]["rolls"].__setitem__(0, 55),
            lambda d: d["cases"][0]["rolls"].__setitem__(0, True),
            lambda d: d["cases"][0]["input"].update(weather=4),
            lambda d: d["cases"][0]["input"].update(attack_stage=13),
            lambda d: d["cases"][0]["input"].update(move_type=9),
            lambda d: d["cases"][0]["input"].update(attacker_ability=255),
            lambda d: d["cases"][0]["input"].update(attack=0),
            lambda d: d["cases"][0]["input"].update(critical=True),
            lambda d: d["cases"][0]["input"].update(extra=0),
        )
        for index, mutate in enumerate(mutations):
            with self.subTest(mutation=index):
                document = copy.deepcopy(self.document)
                mutate(document)
                with self.assertRaises(ValueError):
                    oracle.check(document)


if __name__ == "__main__":
    unittest.main()
