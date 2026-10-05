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
    def test_future_sight_never_enters_analytic_ordinary_surface(self):
        future = PLAIN.replace("MOVE_POUND", "MOVE_FUTURE_SIGHT").replace("EFFECT_HIT", "EFFECT_FUTURE_SIGHT")
        effects, _, ordinary, _, _, _, _, _ = gen.parse_move_table(_table(future))
        self.assertEqual(effects["MOVE_FUTURE_SIGHT"], "EFFECT_FUTURE_SIGHT")
        self.assertNotIn("MOVE_FUTURE_SIGHT", ordinary)

    def test_plain_hit_is_ordinary(self):
        effects, targets, ordinary, unresolved, _, _, _, _ = gen.parse_move_table(_table(PLAIN))
        self.assertEqual(effects["MOVE_POUND"], "EFFECT_HIT")
        self.assertIn("MOVE_POUND", ordinary)
        self.assertEqual(unresolved, set())

    def test_multihit_hit_is_not_ordinary(self):
        _, _, ordinary, _, _, _, _, _ = gen.parse_move_table(_table(MULTI))
        self.assertNotIn("MOVE_DOUBLE_SLAP", ordinary)

    def test_explosion_hit_is_not_ordinary(self):
        _, _, ordinary, _, _, _, _, _ = gen.parse_move_table(_table(EXPLOSION))
        self.assertNotIn("MOVE_TACKLE", ordinary)

    def test_state_flag_hit_is_not_ordinary(self):
        _, _, ordinary, _, _, _, _, _ = gen.parse_move_table(_table(STATE_FLAG))
        self.assertNotIn("MOVE_ALIAS", ordinary)

    def test_target_ability_bypass_is_ordinary_and_delegated_to_group_c(self):
        _, _, ordinary, _, flags, _, _, _ = gen.parse_move_table(_table(ABILITY_BYPASS))
        self.assertIn("MOVE_POUND", ordinary)
        self.assertIn("ignoresTargetAbility", flags["MOVE_POUND"])

    def test_conditional_target_ability_bypass_stays_unclassified(self):
        entry = """    [MOVE_POUND] =
    {
        .effect = EFFECT_HIT,
    #if B_EXPANDED_MOVE_FLAGS
        .ignoresTargetAbility = TRUE,
    #endif
    },"""
        _, _, ordinary, _, flags, unknown, _, _ = gen.parse_move_table(_table(entry))
        self.assertNotIn("MOVE_POUND", ordinary)
        self.assertNotIn("ignoresTargetAbility", flags["MOVE_POUND"])
        self.assertIn("ignoresTargetAbility", unknown["MOVE_POUND"])

    def test_conditional_effect_is_unresolved(self):
        effects, targets, ordinary, unresolved, _, _, _, _ = gen.parse_move_table(_table(CONDITIONAL))
        self.assertNotIn("MOVE_STRUGGLE", effects)
        self.assertIn("MOVE_STRUGGLE", unresolved)
        self.assertNotIn("MOVE_STRUGGLE", ordinary)

    def test_computed_effect_is_unresolved(self):
        effects, _, _, unresolved, _, _, _, _ = gen.parse_move_table(_table(TERNARY))
        self.assertNotIn("MOVE_GROWTH", effects)
        self.assertIn("MOVE_GROWTH", unresolved)

    def test_contact_and_punching_facts_are_source_literals(self):
        contact, unknown, _, _ = gen.parse_contact_and_sheer_force(_table(
            PLAIN.replace('.power = 40,', '.power = 40,\n        .makesContact = TRUE,\n        .punchingMove = TRUE,')
        ))
        self.assertEqual(contact["MOVE_POUND"], {"makesContact", "punchingMove"})
        self.assertEqual(unknown["MOVE_POUND"], set())

    def test_conditional_contact_fails_closed(self):
        entry = PLAIN.replace('.power = 40,', '#if OPTION\n        .makesContact = TRUE,\n#endif\n        .power = 40,')
        contact, unknown, _, _ = gen.parse_contact_and_sheer_force(_table(entry))
        self.assertNotIn("makesContact", contact["MOVE_POUND"])
        self.assertIn("makesContact", unknown["MOVE_POUND"])

    def test_sheer_force_matches_pinned_chance_override_xor(self):
        yes = PLAIN.replace('.power = 40,', '.power = 40,\n        .additionalEffects = ADDITIONAL_EFFECTS({ .chance = 30 }),')
        versioned_positive = PLAIN.replace('MOVE_POUND', 'MOVE_FIRE_BLAST').replace(
            '.power = 40,', '.power = 40,\n        .additionalEffects = ADDITIONAL_EFFECTS({ .chance = B_UPDATED_MOVE_DATA >= GEN_2 ? 10 : 30 }),')
        overridden = PLAIN.replace('MOVE_POUND', 'MOVE_TACKLE').replace('.power = 40,', '.power = 40,\n        .additionalEffects = ADDITIONAL_EFFECTS({ .chance = 30, .sheerForceOverride = TRUE }),')
        certain = PLAIN.replace('MOVE_POUND', 'MOVE_DOUBLE_SLAP').replace('.power = 40,', '.power = 40,\n        .additionalEffects = ADDITIONAL_EFFECTS({ .chance = 0 }),')
        _, _, sheers, unknown = gen.parse_contact_and_sheer_force(_table(yes, versioned_positive, overridden, certain))
        self.assertTrue(sheers["MOVE_POUND"])
        self.assertTrue(sheers["MOVE_FIRE_BLAST"])
        self.assertFalse(sheers["MOVE_TACKLE"])
        self.assertFalse(sheers["MOVE_DOUBLE_SLAP"])
        self.assertNotIn("MOVE_POUND", unknown)

    def test_conditional_additional_effect_fails_closed(self):
        entry = PLAIN.replace('.power = 40,', '.power = 40,\n#if OPTION\n        .additionalEffects = ADDITIONAL_EFFECTS({ .chance = 30 }),\n#endif')
        _, _, sheers, unknown = gen.parse_contact_and_sheer_force(_table(entry))
        self.assertFalse(sheers)
        self.assertTrue(unknown["MOVE_POUND"])

    def test_zmove_effect_is_ignored(self):
        effects, targets, ordinary, _, _, _, _, _ = gen.parse_move_table(_table(Z_MOVE))
        self.assertEqual(effects["MOVE_SWORDS_DANCE"], "EFFECT_ATTACK_UP_2")
        # A status move is never ordinary, and the nested zMove effect must not leak in.
        self.assertNotIn("MOVE_SWORDS_DANCE", ordinary)

    def test_immunity_flags_and_literal_priority_are_source_extracted(self):
        entry = """    [MOVE_TACKLE] =
    {
        .effect = EFFECT_HIT,
        .soundMove = TRUE,
        .ballisticMove = FALSE,
        .windMove = B_EXTRAPOLATED_MOVE_FLAGS,
        .priority = 1,
    },"""
        _, _, _, _, flags, unknown, priorities, unknown_priorities = gen.parse_move_table(_table(entry))
        self.assertEqual(flags["MOVE_TACKLE"], {"soundMove"})
        self.assertEqual(unknown["MOVE_TACKLE"], {"windMove"})
        self.assertEqual(priorities["MOVE_TACKLE"], 1)
        self.assertNotIn("MOVE_TACKLE", unknown_priorities)

    def test_computed_priority_is_unknown(self):
        entry = """    [MOVE_TACKLE] =
    {
        .effect = EFFECT_HIT,
        .priority = B_UPDATED_MOVE_DATA >= GEN_8 ? -6 : 0,
    },"""
        _, _, _, _, _, _, _, unknown_priorities = gen.parse_move_table(_table(entry))
        self.assertIn("MOVE_TACKLE", unknown_priorities)

    def test_ability_move_flags_are_source_derived(self):
        entry = """    [MOVE_POUND] =
    {
        .effect = EFFECT_HIT,
        .punchingMove = TRUE,
        .bitingMove = FALSE,
        .pulseMove = 1,
    },"""
        flags, unknown = gen.parse_ability_move_flags(_table(entry))
        self.assertEqual(flags["MOVE_POUND"], {"punchingMove", "pulseMove"})
        self.assertEqual(unknown["MOVE_POUND"], set())

    def test_conditional_or_computed_ability_move_flags_remain_unknown(self):
        entry = """    [MOVE_POUND] =
    {
        .effect = EFFECT_HIT,
    #if B_EXPANDED_MOVE_FLAGS
        .slicingMove = TRUE,
    #endif
        .pulseMove = B_CUSTOM_PULSE_FLAG,
    },"""
        flags, unknown = gen.parse_ability_move_flags(_table(entry))
        self.assertNotIn("slicingMove", flags["MOVE_POUND"])
        self.assertNotIn("pulseMove", flags["MOVE_POUND"])
        self.assertEqual(unknown["MOVE_POUND"], {"slicingMove", "pulseMove"})

    def test_uninitialized_ability_flags_are_known_false(self):
        flags, unknown = gen.parse_ability_move_flags(_table(PLAIN))
        self.assertEqual(flags["MOVE_POUND"], set())
        self.assertEqual(unknown["MOVE_POUND"], set())


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
        ordinary_block = re.search(
            r"val ordinaryMoveIds: Set<Int> = setOf\((.*?)^    \)",
            cls.text,
            re.M | re.S,
        )
        if ordinary_block is None:
            raise AssertionError("generated ordinaryMoveIds block is missing")
        cls.ordinary = set(int(x) for x in re.findall(r"^        (\d+),$", ordinary_block.group(1), re.M))
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


class FixedSingleHitRecoilTest(unittest.TestCase):
    def test_recoil_is_separate_and_fail_closed(self):
        entry = """[MOVE_TAKE_DOWN] =
        {
            .effect = EFFECT_RECOIL,
            .power = 90,
            .type = TYPE_NORMAL,
            .category = DAMAGE_CATEGORY_PHYSICAL,
            .target = TARGET_SELECTED,
        },"""
        self.assertEqual({"MOVE_TAKE_DOWN"}, gen.parse_recoil_symbols(_table(entry)))
        self.assertNotIn("MOVE_TAKE_DOWN", gen.parse_move_table(_table(entry))[2])
        for field in (*gen.STATE_DEPENDENT_FLAGS, "multiHit", "explosion", "ignoresTargetAbility", "gravityBanned", "healingMove"):
            mutated = entry.replace(".power = 90,", f".power = 90,\n            .{field} = TRUE,")
            self.assertEqual(set(), gen.parse_recoil_symbols(_table(mutated)), field)
        for mutation in (entry.replace(".power = 90,", ""),
                         entry.replace(".type = TYPE_NORMAL,", ""),
                         entry.replace(".power = 90,", ".power = 90,\n            .priority = 1,"),
                         entry.replace(".power = 90,", ".power = 90,\n            .priority = UNRESOLVED,"),
                         entry.replace("EFFECT_RECOIL", "EFFECT_RECOIL_IF_MISS"),
                         entry.replace("EFFECT_RECOIL", "SOME_UNRESOLVED_EFFECT"),
                         entry.replace("TARGET_SELECTED", "TARGET_BOTH"),
                         entry.replace(".power = 90,", ".strikeCount = 2,"),
                         entry.replace(".power = 90,", ".strikeCount = UNKNOWN,")):
            self.assertEqual(set(), gen.parse_recoil_symbols(_table(mutation)))

    def test_exact_committed_family(self):
        path = os.path.join(gen.DEFAULT_REPO_ROOT, "app/src/main/java/com/dualdex/pokemon/hns/Hns205MoveEffects.kt")
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        body = re.search(r"val fixedSingleHitRecoilMoveIds: Set<Int> = setOf\((.*?)\)", text, re.S).group(1)
        self.assertEqual({36,38,66,344,394,413,452,457,528,543,617,762}, set(map(int,re.findall(r"\d+",body))))


class EarthquakeContractTest(unittest.TestCase):
    def body(self, bulldoze=False):
        return _table("""    [MOVE_%s] =
    {
        .effect = EFFECT_EARTHQUAKE,
        .power = %s,
        .type = TYPE_GROUND,
        .target = TARGET_FOES_AND_ALLY,
        .priority = 0,
        .category = DAMAGE_CATEGORY_PHYSICAL,
        %s
    },""" % ("BULLDOZE" if bulldoze else "EARTHQUAKE", 60 if bulldoze else 100,
        ".additionalEffects = ADDITIONAL_EFFECTS({ .moveEffect = MOVE_EFFECT_SPD_MINUS_1, .chance = 100, })," if bulldoze else
        ".damagesUnderground = B_UPDATED_MOVE_FLAGS >= GEN_2,"))
    def test_exact_frozen_contract_and_mutations(self):
        for bulldoze in (False,True):
            original=self.body(bulldoze)
            self.assertEqual({"MOVE_BULLDOZE" if bulldoze else "MOVE_EARTHQUAKE": not bulldoze},gen.parse_earthquake_metadata(original,True))
            for old,new in (("EFFECT_EARTHQUAKE","EFFECT_MAGNITUDE"),("TARGET_FOES_AND_ALLY","TARGET_SELECTED"),
                            (".priority = 0",".priority = 1"),("TYPE_GROUND","TYPE_NORMAL"),
                            ("DAMAGE_CATEGORY_PHYSICAL","DAMAGE_CATEGORY_SPECIAL")):
                self.assertFalse(gen.parse_earthquake_metadata(original.replace(old,new),True))
            for flag in ("multiHit = TRUE", "strikeCount = 2", "explosion = TRUE", "damagesUnderwater = TRUE",
                         "unreviewedDamageFlag = TRUE", "alwaysCriticalHit = TRUE", "makesContact = TRUE", "punchingMove = unresolved"):
                self.assertFalse(gen.parse_earthquake_metadata(original.replace('.priority = 0,',f'.priority = 0,\n        .{flag},'),True),flag)
            if bulldoze:
                self.assertFalse(gen.parse_earthquake_metadata(original.replace('.priority = 0,','.priority = 0,\n        .damagesUnderground = TRUE,'),True))
            else:
                for replacement in ("FALSE","TRUE","unresolved"):
                    self.assertFalse(gen.parse_earthquake_metadata(original.replace('B_UPDATED_MOVE_FLAGS >= GEN_2',replacement),True))
                self.assertFalse(gen.parse_earthquake_metadata(original,False))


if __name__ == "__main__":
    unittest.main()


class DrainMetadataTests(unittest.TestCase):
    BODY = """[MOVE_ABSORB] =
    {
        .effect = EFFECT_ABSORB,
        .power = 20,
        .type = TYPE_GRASS,
        .category = DAMAGE_CATEGORY_SPECIAL,
        .target = TARGET_SELECTED,
        .priority = 0,
        .healingMove = B_HEAL_BLOCKING >= GEN_6,
        .argument = { .absorbPercentage = 50 },
    },"""

    def test_positive_requires_resolved_configuration(self):
        self.assertEqual(gen.parse_drain_metadata(self.BODY, True), {"MOVE_ABSORB": 50})
        self.assertEqual(gen.parse_drain_metadata(self.BODY), {})

    def test_mutations_fail_closed(self):
        mutations = [("EFFECT_ABSORB", "EFFECT_DREAM_EATER"),
                     ("EFFECT_ABSORB", "CONDITIONAL_EFFECT"),
                     ("TARGET_SELECTED", "TARGET_BOTH"), (".priority = 0", ".priority = 1"),
                     (".priority = 0", ".priority = UNKNOWN"),
                     (".power = 20", ".power = UNKNOWN"),
                     (".healingMove = B_HEAL_BLOCKING >= GEN_6", ".healingMove = UNKNOWN"),
                     (".absorbPercentage = 50", ".absorbPercentage = UNKNOWN"),
                     (".absorbPercentage = 50", ".otherArgument = 50"),
                     (".type = TYPE_GRASS", ".otherType = TYPE_GRASS"),
                     (".power = 20,", ".power = 20,\n .makesContact = UNKNOWN,"),
                     (".power = 20,", ".power = 20,\n .punchingMove = UNKNOWN,"),
                     (".category = DAMAGE_CATEGORY_SPECIAL", ".otherCategory = DAMAGE_CATEGORY_SPECIAL")]
        for old, new in mutations:
            with self.subTest(new=new):
                self.assertEqual(gen.parse_drain_metadata(self.BODY.replace(old,new), True), {})
        for flag in (*gen.STATE_DEPENDENT_FLAGS, "multiHit", "explosion", "ignoresTargetAbility", "gravityBanned"):
            with self.subTest(flag=flag):
                self.assertEqual(gen.parse_drain_metadata(self.BODY.replace('.power = 20,', f'.power = 20,\n .{flag} = TRUE,'),True), {})
        for strike in ('2', 'UNKNOWN'):
            self.assertEqual(gen.parse_drain_metadata(self.BODY.replace('.power = 20,',f'.power = 20,\n .strikeCount = {strike},'),True),{})

    def test_adjacent_effect_members_do_not_expand_frozen_family(self):
        for symbol in ('MOVE_OBLIVION_WING','MOVE_BITTER_BLADE','MOVE_PARABOLIC_CHARGE'):
            self.assertEqual(gen.parse_drain_metadata(self.BODY.replace('MOVE_ABSORB',symbol),True),{})
