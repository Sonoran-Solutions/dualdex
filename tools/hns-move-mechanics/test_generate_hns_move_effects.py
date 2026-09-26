#!/usr/bin/env python3
"""Self-contained tests for the H&S move-effect generator (issue #9, Gap C4a).

These drive the real parsing code against tiny synthetic headers, so they need no upstream
checkout, no ARM toolchain, no ROM and no network. They pin the fail-closed contract: an
effect that is conditional or computed must be unresolved (never guessed), and an
``EFFECT_HIT`` move that carries a damage-relevant complication must not be treated as
ordinary.
"""

import os
import re
import unittest

import generate_hns_move_effects as gen

MOVES_HEADER = """
enum __attribute__((packed)) Move
{
    MOVE_NONE = 0,
    MOVE_POUND = 1,
    MOVE_TACKLE = 2,
    MOVE_DOUBLE_SLAP = 3,
    MOVES_COUNT_GEN1,
    MOVE_ALIAS = MOVES_COUNT_GEN1,
};
"""


def _table(*entries):
    body = "\n".join(entries)
    return (
        "const struct MoveInfo gMovesInfo[MOVES_COUNT_ALL] =\n{\n"
        + body
        + "\n};\n"
    )


PLAIN = """    [MOVE_POUND] =
    {
        .name = COMPOUND_STRING("POUND"),
        .effect = EFFECT_HIT,
        .power = 40,
    },"""

MULTI = """    [MOVE_DOUBLE_SLAP] =
    {
        .name = COMPOUND_STRING("DOUBLE SLAP"),
        .effect = EFFECT_HIT,
        .power = 15,
        .multiHit = TRUE,
    },"""

EXPLOSION = """    [MOVE_TACKLE] =
    {
        .name = COMPOUND_STRING("TACKLE"),
        .effect = EFFECT_HIT,
        .power = 40,
        .explosion = TRUE,
    },"""

STATE_FLAG = """    [MOVE_ALIAS] =
    {
        .name = COMPOUND_STRING("ALIAS"),
        .effect = EFFECT_HIT,
        .power = 40,
        .ignoresTargetDefenseEvasionStages = TRUE,
    },"""

ABILITY_BYPASS = """    [MOVE_POUND] =
    {
        .name = COMPOUND_STRING("POUND"),
        .effect = EFFECT_HIT,
        .power = 40,
        .ignoresTargetAbility = TRUE,
    },"""

CONDITIONAL = """    [MOVE_STRUGGLE] =
    {
        .name = COMPOUND_STRING("STRUGGLE"),
#if B_UPDATED_MOVE_DATA >= GEN_3
        .effect = EFFECT_STRUGGLE,
#else
        .effect = EFFECT_RECOIL,
#endif
        .power = 50,
    },"""

TERNARY = """    [MOVE_GROWTH] =
    {
        .name = COMPOUND_STRING("GROWTH"),
        .effect = B_GROWTH_STAT_RAISE >= GEN_5 ? EFFECT_GROWTH : EFFECT_SPECIAL_ATTACK_UP,
        .power = 0,
    },"""

Z_MOVE = """    [MOVE_SWORDS_DANCE] =
    {
        .name = COMPOUND_STRING("SWORDS DANCE"),
        .effect = EFFECT_ATTACK_UP_2,
        .zMove = { .effect = Z_EFFECT_RESET_STATS },
    },"""


class ParseMoveEnumTest(unittest.TestCase):
    def test_explicit_aliases_and_implicit_progression(self):
        ids = gen.parse_move_enum(MOVES_HEADER)
        self.assertEqual(ids["MOVE_NONE"], 0)
        self.assertEqual(ids["MOVE_POUND"], 1)
        self.assertEqual(ids["MOVE_TACKLE"], 2)
        self.assertEqual(ids["MOVE_DOUBLE_SLAP"], 3)
        self.assertEqual(ids["MOVES_COUNT_GEN1"], 4)
        self.assertEqual(ids["MOVE_ALIAS"], 4)

    def test_missing_enum_fails(self):
        with self.assertRaises(ValueError):
            gen.parse_move_enum("int x;")


class ParseMoveTableTest(unittest.TestCase):
    def test_plain_hit_is_ordinary(self):
        effects, targets, ordinary, unresolved = gen.parse_move_table(_table(PLAIN))
        self.assertEqual(effects["MOVE_POUND"], "EFFECT_HIT")
        self.assertIn("MOVE_POUND", ordinary)
        self.assertEqual(unresolved, set())

    def test_multihit_hit_is_not_ordinary(self):
        _, _, ordinary, _ = gen.parse_move_table(_table(MULTI))
        self.assertNotIn("MOVE_DOUBLE_SLAP", ordinary)

    def test_explosion_hit_is_not_ordinary(self):
        _, _, ordinary, _ = gen.parse_move_table(_table(EXPLOSION))
        self.assertNotIn("MOVE_TACKLE", ordinary)

    def test_state_flag_hit_is_not_ordinary(self):
        _, _, ordinary, _ = gen.parse_move_table(_table(STATE_FLAG))
        self.assertNotIn("MOVE_ALIAS", ordinary)

    def test_target_ability_bypass_is_not_ordinary(self):
        _, _, ordinary, _ = gen.parse_move_table(_table(ABILITY_BYPASS))
        self.assertNotIn("MOVE_POUND", ordinary)

    def test_conditional_effect_is_unresolved(self):
        effects, targets, ordinary, unresolved = gen.parse_move_table(_table(CONDITIONAL))
        self.assertNotIn("MOVE_STRUGGLE", effects)
        self.assertIn("MOVE_STRUGGLE", unresolved)
        self.assertNotIn("MOVE_STRUGGLE", ordinary)

    def test_computed_effect_is_unresolved(self):
        effects, _, _, unresolved = gen.parse_move_table(_table(TERNARY))
        self.assertNotIn("MOVE_GROWTH", effects)
        self.assertIn("MOVE_GROWTH", unresolved)

    def test_zmove_effect_is_ignored(self):
        effects, targets, ordinary, _ = gen.parse_move_table(_table(Z_MOVE))
        self.assertEqual(effects["MOVE_SWORDS_DANCE"], "EFFECT_ATTACK_UP_2")
        # A status move is never ordinary, and the nested zMove effect must not leak in.
        self.assertNotIn("MOVE_SWORDS_DANCE", ordinary)


class CommittedArtifactTest(unittest.TestCase):
    """The committed Kotlin artifact must reflect the pinned ordinary set invariants."""

    ARTIFACT = os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
        "app/src/main/java/com/dualdex/pokemon/hns/Hns205MoveEffects.kt",
    )

    @classmethod
    def setUpClass(cls):
        with open(cls.ARTIFACT, encoding="utf-8") as handle:
            cls.text = handle.read()
        cls.ordinary = set(int(x) for x in re.findall(r"^        (\d+),$", cls.text, re.M))
        cls.effects = dict(
            (int(i), e)
            for i, e in re.findall(r'put\((\d+), "([A-Z0-9_]+)"\)', cls.text)
        )

    def test_ordinary_is_nonempty_and_bounded(self):
        self.assertGreater(len(self.ordinary), 300)
        self.assertLess(len(self.ordinary), len(self.effects))

    def test_tackle_is_ordinary(self):
        self.assertEqual(self.effects[33], "EFFECT_HIT")
        self.assertIn(33, self.ordinary)

    def test_special_moves_are_not_ordinary(self):
        # Return / Hidden Power / Low Kick: non-HIT or unresolved effects.
        self.assertNotIn(216, self.ordinary)
        self.assertNotIn(237, self.ordinary)
        self.assertNotIn(67, self.ordinary)
        # Bullet Seed / Double Kick: multi-hit hidden behind EFFECT_HIT.
        self.assertNotIn(331, self.ordinary)
        self.assertNotIn(24, self.ordinary)
        # Explosion / Self-Destruct: H&S does not halve Defence, ADV does.
        self.assertNotIn(153, self.ordinary)
        self.assertNotIn(120, self.ordinary)

    def test_unresolved_moves_are_absent_from_effect_map(self):
        # Low Kick and Struggle have conditional effects and must not be resolved.
        for move_id in (67, 165):
            self.assertNotIn(move_id, self.effects)


if __name__ == "__main__":
    unittest.main()
