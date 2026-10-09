#!/usr/bin/env python3
"""Belch (move 562) descriptor, selection-restriction, and Berry-state writer mutations for Slice 16."""
import argparse
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

import generate_hns_move_effects as gen

UPSTREAM = None


class BelchContractTest(unittest.TestCase):
    def setUp(self):
        self.original = (UPSTREAM / "src/data/moves_info.h").read_text()
        self.ids = gen.parse_move_enum((UPSTREAM / "include/constants/moves.h").read_text())
        self.contact_symbols, _, self.sheer_symbols, _ = gen.parse_contact_and_sheer_force(
            self.original, updated_move_data_latest=True)
        self.contact = {self.ids[s]: v for s, v in self.contact_symbols.items() if s in self.ids}
        self.sheer = {self.ids[s]: v for s, v in self.sheer_symbols.items() if s in self.ids}

    def parse(self, text):
        return gen.parse_belch_metadata(text, self.ids, self.contact, self.sheer, {}, {})

    def test_frozen_descriptor_and_selection_restrictions(self):
        parsed = self.parse(self.original)
        self.assertEqual(set(parsed), {562})
        belch = parsed[562]
        self.assertEqual((belch["power"], belch["type"], belch["category"], belch["accuracy"], belch["pp"]),
                         (120, "TYPE_POISON", "DAMAGE_CATEGORY_SPECIAL", 90, 10))
        self.assertEqual(belch["effect"], "EFFECT_BELCH")
        self.assertFalse(belch["makesContact"])
        self.assertFalse(belch["punchingMove"])
        self.assertFalse(belch["ballisticMove"])
        self.assertEqual(belch["additionalEffects"], [])
        self.assertEqual(belch["selectionRestrictions"], sorted([
            "assistBanned", "copycatBanned", "instructBanned", "meFirstBanned",
            "metronomeBanned", "mimicBanned", "mirrorMoveBanned", "sleepTalkBanned"]))

    def test_every_descriptor_mutation_is_refused(self):
        # Each mutation changes the normalized MoveInfo digest, so the descriptor is refused.
        mutations = (
            (".power = 120,", ".power = 121,"),
            (".effect = EFFECT_BELCH,", ".effect = EFFECT_HIT,"),
            (".category = DAMAGE_CATEGORY_SPECIAL,", ".category = DAMAGE_CATEGORY_PHYSICAL,"),
            (".mirrorMoveBanned = TRUE,", ".mirrorMoveBanned = FALSE,"),
            (".metronomeBanned = TRUE,", ".metronomeBanned = FALSE,"),
            (".assistBanned = TRUE,", ".assistBanned = FALSE,"),
        )
        for old, new in mutations:
            with self.subTest(mutation=new):
                body_start = self.original.index("[MOVE_BELCH]")
                body_end = self.original.index("\n    },", body_start)
                mutated_body = self.original[body_start:body_end].replace(old, new, 1)
                mutated = self.original[:body_start] + mutated_body + self.original[body_end:]
                with self.assertRaises(ValueError):
                    self.parse(mutated)

    def test_phase_source_mutations_are_refused(self):
        # Mutate a temporary copy of the battle sources; the pinned checkout is never written.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "src").mkdir()
            for path in (UPSTREAM / "src").glob("battle*.c"):
                shutil.copy(path, root / "src" / path.name)
            gen.verify_belch_source_contract(root)  # unmodified copy passes
            util = root / "src/battle_util.c"
            original = util.read_text()
            util.write_text(original.replace("return !GetBattlerPartyState(battler)->ateBerry;",
                                             "return FALSE;", 1))
            with self.assertRaises(ValueError):
                gen.verify_belch_source_contract(root)
            util.write_text(original)
            hold = root / "src/battle_hold_effects.c"
            hold_text = hold.read_text()
            hold.write_text(hold_text.replace("GetBattlerPartyState(itemBattler)->ateBerry = TRUE;",
                                              "GetBattlerPartyState(itemBattler)->ateBerry = FALSE;", 1))
            with self.assertRaises(ValueError):
                gen.verify_belch_source_contract(root)


def main():
    global UPSTREAM
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream-dir", default=os.environ.get("HNS_UPSTREAM_DIR"))
    args, rest = parser.parse_known_args()
    if not args.upstream_dir:
        raise SystemExit("set HNS_UPSTREAM_DIR or pass --upstream-dir")
    UPSTREAM = Path(args.upstream_dir).resolve()
    unittest.main(argv=[sys.argv[0]] + rest, verbosity=2)


if __name__ == "__main__":
    main()
