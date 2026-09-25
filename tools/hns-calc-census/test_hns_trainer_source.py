#!/usr/bin/env python3
"""Unit tests for the pinned trainer-source reader (issue #84).

These drive the REAL reader against small synthetic fixtures in the exact shapes the pinned
`src/data/trainers.h` and preprocessed `src/pokemon.c` use, so they need no upstream
checkout, no ARM toolchain, no ROM and no network. The full pinned checkout is exercised
separately by `./ci.sh source-check`.
"""

import importlib.util
import pathlib
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent


def _load(name, filename):
    spec = importlib.util.spec_from_file_location(name, HERE / filename)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


src = _load("hns_calc_census_trainer_source", "hns_trainer_source.py")
gen = _load("hns_calc_census_generator", "generate_hns_trainer_census.py")

# The generator loads the reader under its own module name, so its `SourceError` class object is
# a different one from this module's. Fail-closed assertions use the reader's class, which is the
# class the generator's errors derive from.
SourceError = src.SourceError


# --------------------------------------------------------------------------- fixtures

MOVES = {
    "MOVE_TACKLE": src.MoveRecord(33, "MOVE_TACKLE", "Tackle", "Normal", "PHYSICAL", 40),
    "MOVE_GROWL": src.MoveRecord(45, "MOVE_GROWL", "Growl", "Normal", "STATUS", 0),
    "MOVE_VINE_WHIP": src.MoveRecord(22, "MOVE_VINE_WHIP", "Vine Whip", "Grass", "PHYSICAL", 45),
    "MOVE_RAZOR_LEAF": src.MoveRecord(75, "MOVE_RAZOR_LEAF", "Razor Leaf", "Grass", "PHYSICAL", 55),
    "MOVE_SLEEP_POWDER": src.MoveRecord(79, "MOVE_SLEEP_POWDER", "Sleep Powder", "Grass", "STATUS", 0),
    "MOVE_POISON_POWDER": src.MoveRecord(77, "MOVE_POISON_POWDER", "Poison Powder", "Poison", "STATUS", 0),
    "MOVE_BODY_SLAM": src.MoveRecord(34, "MOVE_BODY_SLAM", "Body Slam", "Normal", "PHYSICAL", 85),
}

ITEMS = {
    "ITEM_NONE": {"id": 0, "source_name": None, "hold_effect": "HOLD_EFFECT_NONE"},
    "ITEM_ORAN_BERRY": {"id": 139, "source_name": "ORAN BERRY", "hold_effect": "HOLD_EFFECT_RESTORE_HP"},
}

ABILITIES = {
    "ABILITY_NONE": 0,
    "ABILITY_OVERGROW": 65,
    "ABILITY_CHLOROPHYLL": 34,
    "ABILITY_KEEN_EYE": 51,
}

# A synthetic `gSpeciesInfo`-shaped preprocessed excerpt.
SPECIES_SOURCE = """
static const struct LevelUpMove sBulbasaurLevelUpLearnset[] = {
    {.move = MOVE_TACKLE, .level = 1},
    {.move = MOVE_GROWL, .level = 1},
    {.move = MOVE_VINE_WHIP, .level = 7},
    {.move = MOVE_RAZOR_LEAF, .level = 13},
    {.move = MOVE_SLEEP_POWDER, .level = 20},
    {.move = MOVE_POISON_POWDER, .level = 27},
    {.move = MOVE_BODY_SLAM, .level = 34},
    {.move = MOVE_SLEEP_POWDER, .level = 41},
    {.move = LEVEL_UP_MOVE_END, .level = 0}
};

const struct SpeciesInfo gSpeciesInfo[] =
{
    [0] =
    {
        .speciesName = _("??????????"),
    },
    [1] =
    {
        .speciesName = _("BULBASAUR"),
        .types = { TYPE_GRASS, TYPE_POISON },
        .abilities = { ABILITY_OVERGROW, ABILITY_CHLOROPHYLL, ABILITY_NONE },
        .levelUpLearnset = sBulbasaurLevelUpLearnset,
    },
};
"""

SPECIES_ENUM_HEADER = """
#define SPECIES_NONE                                    0
#define SPECIES_BULBASAUR                               1
#define SPECIES_EGG                                     (SPECIES_BULBASAUR + 1)
#define NUM_SPECIES SPECIES_EGG
#define SPECIES_SHINY_TAG 5000
"""


def species_by_id():
    entries = [(str(0), '.speciesName = _("??????????"),'),
               ("1", '.speciesName = _("BULBASAUR"),\n'
                     '        .abilities = { ABILITY_OVERGROW, ABILITY_CHLOROPHYLL, ABILITY_NONE },\n'
                     '        .levelUpLearnset = sBulbasaurLevelUpLearnset,')]
    resolved, skipped = src.parse_species(entries, "fixture", src.is_placeholder_species_name)
    return resolved, skipped


def learnsets():
    return src.parse_learnsets(SPECIES_SOURCE, "fixture")


def resolve(text, display_names=None):
    resolved, _ = species_by_id()
    by_symbol = {"SPECIES_BULBASAUR": resolved[1]}
    return src.resolve_trainers(
        text,
        species_by_symbol=by_symbol,
        moves_by_symbol=MOVES,
        items_by_symbol=ITEMS,
        learnset_by_symbol=learnsets(),
        ability_ids_by_symbol=ABILITIES,
        display_names=display_names,
        where="fixture:trainers.h",
    )


TRAINER_HEADER = """
//
// DO NOT MODIFY THIS FILE! It is auto-generated from src/data/trainers.party
//
#line 1 "src/data/trainers.party"

#line 76
    [DIFFICULTY_NORMAL][TRAINER_NONE] =
    {
#line 78
        .trainerClass = TRAINER_CLASS_PKMN_TRAINER_1,
        .gender = TRAINER_GENDER_MALE,
        .battleType = TRAINER_BATTLE_TYPE_SINGLES,
        .partySize = 0,
        .party = (const struct TrainerMon[])
        {
        },
    },
#line 84
    [DIFFICULTY_NORMAL][TRAINER_RIVAL_1] =
    {
#line 85
        .trainerName = _("RIVAL"),
        .trainerClass = TRAINER_CLASS_RIVAL,
        .gender = TRAINER_GENDER_MALE,
        .battleType = TRAINER_BATTLE_TYPE_SINGLES,
        .aiFlags = AI_FLAG_BASIC_TRAINER,
        .partySize = 2,
        .party = (const struct TrainerMon[])
        {
            {
            .species = SPECIES_BULBASAUR,
            .gender = TRAINER_MON_RANDOM_GENDER,
            .iv = TRAINER_PARTY_IVS(0, 0, 0, 0, 0, 0),
            .lvl = 5,
            .ball = POKEBALL_COUNT,
            .nature = NATURE_HARDY,
            .dynamaxLevel = MAX_DYNAMAX_LEVEL,
            .heldItem = ITEM_ORAN_BERRY,
            .moves = {
                MOVE_TACKLE,
                MOVE_GROWL,
            },
            },
            {
            .species = SPECIES_BULBASAUR,
            .iv = TRAINER_PARTY_IVS(0, 0, 0, 0, 0, 0),
            .lvl = 20,
            .ball = POKEBALL_COUNT,
            .nature = NATURE_HARDY,
            .dynamaxLevel = MAX_DYNAMAX_LEVEL,
            },
        },
    },
"""


class TrainerSourceTest(unittest.TestCase):

    def test_parses_every_trainer_entry_including_the_empty_placeholder(self):
        trainers = resolve(TRAINER_HEADER)
        self.assertEqual(["TRAINER_NONE", "TRAINER_RIVAL_1"], [t.key for t in trainers])
        self.assertFalse(trainers[0].is_battle)
        self.assertTrue(trainers[1].is_battle)
        self.assertEqual(2, len(trainers[1].party))

    def test_explicit_moves_and_item_are_used_verbatim(self):
        mon = resolve(TRAINER_HEADER)[1].party[0]
        self.assertEqual(("Tackle", "Growl"), mon.moves)
        self.assertEqual("party-entry", mon.moves_source)
        self.assertEqual(139, mon.item_id)
        self.assertEqual("ORAN BERRY", mon.item_name)

    def test_omitted_moves_resolve_through_the_level_up_learnset(self):
        # `GiveBoxMonInitialMoveset` keeps the four most recently learned distinct moves at or
        # below the level: Tackle, Growl, Vine Whip, Razor Leaf, Sleep Powder, Poison Powder,
        # Body Slam are all <= 34, so the window keeps the LAST four.
        mon = resolve(TRAINER_HEADER)[1].party[1]
        self.assertEqual("level-up-learnset", mon.moves_source)
        self.assertEqual(
            ("Growl", "Vine Whip", "Razor Leaf", "Sleep Powder"), mon.moves
        )

    def test_learnset_window_stops_at_the_mon_level(self):
        # At level 13 only Tackle/Growl/Vine Whip/Razor Leaf are reachable, so nothing is dropped.
        self.assertEqual(
            ("MOVE_TACKLE", "MOVE_GROWL", "MOVE_VINE_WHIP", "MOVE_RAZOR_LEAF"),
            src.initial_moveset(learnsets()["sBulbasaurLevelUpLearnset"], 13),
        )

    def test_learnset_window_skips_duplicates_and_level_zero(self):
        learnset = ((0, "MOVE_TACKLE"), (1, "MOVE_TACKLE"), (2, "MOVE_GROWL"),
                    (3, "MOVE_GROWL"), (4, "MOVE_VINE_WHIP"))
        self.assertEqual(("MOVE_TACKLE", "MOVE_GROWL", "MOVE_VINE_WHIP"),
                         src.initial_moveset(learnset, 10))

    def test_omitted_ability_is_species_slot_zero_not_zero(self):
        mon = resolve(TRAINER_HEADER)[1].party[1]
        self.assertEqual(65, mon.ability_id)
        self.assertEqual("ABILITY_OVERGROW", mon.ability_symbol)
        self.assertEqual("species-slot-0", mon.ability_source)

    def test_display_name_comes_from_the_caller_supplied_pinned_table(self):
        mon = resolve(TRAINER_HEADER, display_names={1: "Bulbasaur"})[1].party[0]
        self.assertEqual("Bulbasaur", mon.species_name)
        self.assertEqual("BULBASAUR", mon.species_name_raw)

    def test_missing_display_name_fails_closed(self):
        with self.assertRaises(SourceError):
            resolve(TRAINER_HEADER, display_names={})


class FailClosedTest(unittest.TestCase):
    """Every shape the reader was not written for must raise, never be skipped."""

    def _mutate(self, old, new):
        self.assertIn(old, TRAINER_HEADER)
        return resolve(TRAINER_HEADER.replace(old, new))

    def test_unknown_trainer_field_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate(".aiFlags = AI_FLAG_BASIC_TRAINER,",
                         ".aiFlags = AI_FLAG_BASIC_TRAINER,\n        .mysteryField = 3,")
        self.assertIn("mysteryField", str(ctx.exception))

    def test_unknown_party_field_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate(".lvl = 5,", ".lvl = 5,\n            .newFangledField = 1,")
        self.assertIn("newFangledField", str(ctx.exception))

    def test_party_pool_is_refused_because_it_changes_the_lead(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate(".aiFlags = AI_FLAG_BASIC_TRAINER,",
                         ".aiFlags = AI_FLAG_BASIC_TRAINER,\n        .poolSize = 3,")
        self.assertIn("poolSize", str(ctx.exception))

    def test_party_order_ai_flag_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate("AI_FLAG_BASIC_TRAINER", "AI_FLAG_BASIC_TRAINER | AI_FLAG_RANDOMIZE_PARTY_INDICES")
        self.assertIn("AI_FLAG_RANDOMIZE_PARTY_INDICES", str(ctx.exception))

    def test_party_size_disagreement_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate(".partySize = 2,", ".partySize = 3,")
        self.assertIn("partySize", str(ctx.exception))

    def test_unknown_species_symbol_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate(".species = SPECIES_BULBASAUR,", ".species = SPECIES_MISSINGNO,")
        self.assertIn("SPECIES_MISSINGNO", str(ctx.exception))

    def test_unknown_item_symbol_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate(".heldItem = ITEM_ORAN_BERRY,", ".heldItem = ITEM_NOT_IN_PINNED,")
        self.assertIn("ITEM_NOT_IN_PINNED", str(ctx.exception))

    def test_non_constant_move_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate("MOVE_TACKLE,", "MOVE_TACKLE + 1,")
        self.assertIn("move constant", str(ctx.exception))

    def test_declared_ability_outside_the_species_slots_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate(".lvl = 20,", ".lvl = 20,\n            .ability = ABILITY_KEEN_EYE,")
        self.assertIn("ABILITY_KEEN_EYE", str(ctx.exception))

    def test_preprocessor_directive_other_than_line_marker_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            self._mutate(".partySize = 0,", "#if SOMETHING\n        .partySize = 0,")
        self.assertIn("preprocessor directive", str(ctx.exception))

    def test_unbalanced_braces_are_refused(self):
        with self.assertRaises(SourceError):
            src.match_brace("{ { }", 0)

    def test_empty_species_table_is_refused(self):
        with self.assertRaises(SourceError):
            src.parse_species([], "fixture")

    def test_learnset_without_entries_is_refused(self):
        with self.assertRaises(SourceError):
            src.parse_learnsets("static const struct LevelUpMove sXLevelUpLearnset[] = {\n};", "fixture")

    def test_species_ability_slot_that_is_not_a_constant_is_refused(self):
        with self.assertRaises(SourceError):
            src.parse_species(
                [("1", '.speciesName = _("X"),\n'
                     '        .abilities = { SOME_MACRO(1) },\n'
                     '        .levelUpLearnset = sXLevelUpLearnset,')],
                "fixture",
            )


class SpeciesDefinesTest(unittest.TestCase):

    def test_resolves_plain_aliases_and_the_egg_arithmetic(self):
        ids = gen.parse_species_defines(SPECIES_ENUM_HEADER, "fixture")
        self.assertEqual(0, ids["SPECIES_NONE"])
        self.assertEqual(1, ids["SPECIES_BULBASAUR"])
        # SPECIES_EGG is the count, so it is one past the domain, like SPECIES_SHINY_TAG.
        self.assertNotIn("SPECIES_EGG", ids)
        self.assertNotIn("SPECIES_SHINY_TAG", ids)

    def test_forward_alias_declared_before_its_target_is_resolved(self):
        # The pinned header declares `SPECIES_MINIOR SPECIES_MINIOR_METEOR` before the target.
        text = (
            "#define SPECIES_NONE 0\n"
            "#define SPECIES_ALIAS SPECIES_REAL\n"
            "#define SPECIES_REAL 7\n"
            "#define SPECIES_EGG (SPECIES_REAL + 1)\n"
            "#define NUM_SPECIES SPECIES_EGG\n"
        )
        ids = gen.parse_species_defines(text, "fixture")
        self.assertEqual(7, ids["SPECIES_ALIAS"])
        self.assertEqual(7, ids["SPECIES_REAL"])

    def test_unresolvable_alias_is_refused(self):
        text = (
            "#define SPECIES_NONE 0\n"
            "#define SPECIES_ALIAS SPECIES_NOT_THERE\n"
            "#define SPECIES_EGG (SPECIES_NONE + 1)\n"
            "#define NUM_SPECIES SPECIES_EGG\n"
        )
        # The generator loads the reader under its own module name, so the class identity of its
        # SourceError differs; the assertion is on the fail-closed behaviour and its message.
        with self.assertRaises(Exception) as ctx:
            gen.parse_species_defines(text, "fixture")
        self.assertIn("refusing to invent an ID", str(ctx.exception))

    def test_missing_num_species_is_refused(self):
        with self.assertRaises(Exception) as ctx:
            gen.parse_species_defines("#define SPECIES_NONE 0\n", "fixture")
        self.assertIn("NUM_SPECIES", str(ctx.exception))


class CanonicalJsonTest(unittest.TestCase):

    def test_json_is_byte_stable_and_key_order_is_declaration_order(self):
        payload = {"b": 1, "a": [1, 2], "c": {"z": True}}
        rendered = gen.canonical_json(payload)
        self.assertEqual(rendered, gen.canonical_json(payload))
        self.assertLess(rendered.index('"b"'), rendered.index('"a"'))
        self.assertTrue(rendered.endswith("\n"))


if __name__ == "__main__":
    unittest.main()
