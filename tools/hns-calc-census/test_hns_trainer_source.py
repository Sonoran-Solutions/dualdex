#!/usr/bin/env python3
"""Unit tests for the pinned trainer-source reader (issue #84).

These drive the REAL reader against small synthetic fixtures in the exact shapes the pinned
`src/data/trainers_hns.party` and preprocessed `src/pokemon.c` use, so they need no upstream
checkout, no ARM toolchain, no ROM and no network. The full pinned checkout is exercised
separately by `./ci.sh source-check`.
"""

import importlib.util
import pathlib
import re
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent


def _load(name, filename):
    spec = importlib.util.spec_from_file_location(name, HERE / filename)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


gen = _load("hns_calc_census_generator", "generate_hns_trainer_census.py")
# The generator loads the reader under its own module name, and its module-level lookup tables are
# populated by the generator, so the two must be the same object.
src = gen.src
SourceError = src.SourceError


# --------------------------------------------------------------------------- fixtures

MOVES = {
    "MOVE_TACKLE": src.MoveRecord(33, "MOVE_TACKLE", "Tackle", "Normal", "PHYSICAL", 40),
    "MOVE_GROWL": src.MoveRecord(45, "MOVE_GROWL", "Growl", "Normal", "STATUS", 0),
    "MOVE_VINE_WHIP": src.MoveRecord(22, "MOVE_VINE_WHIP", "Vine Whip", "Grass", "PHYSICAL", 45),
    "MOVE_RAZOR_LEAF": src.MoveRecord(75, "MOVE_RAZOR_LEAF", "Razor Leaf", "Grass", "PHYSICAL", 55),
    "MOVE_SLEEP_POWDER":
        src.MoveRecord(79, "MOVE_SLEEP_POWDER", "Sleep Powder", "Grass", "STATUS", 0),
    "MOVE_BODY_SLAM": src.MoveRecord(34, "MOVE_BODY_SLAM", "Body Slam", "Normal", "PHYSICAL", 85),
}

ITEMS = {
    "ITEM_NONE": {"id": 0, "source_name": None, "hold_effect": "HOLD_EFFECT_NONE"},
    "ITEM_ORAN_BERRY": {
        "id": 139, "source_name": "ORAN BERRY", "hold_effect": "HOLD_EFFECT_RESTORE_HP"
    },
}

ABILITIES = {
    "ABILITY_NONE": 0,
    "ABILITY_OVERGROW": 65,
    "ABILITY_CHLOROPHYLL": 34,
    "ABILITY_SUNNY_DAY": 40,
}

# A synthetic `gSpeciesInfo`-shaped preprocessed excerpt, with the level-up learnset the
# default-moveset path reads.
SPECIES_SOURCE = """
static const struct LevelUpMove sBulbasaurLevelUpLearnset[] = {
    {.move = MOVE_TACKLE, .level = 1},
    {.move = MOVE_GROWL, .level = 1},
    {.move = MOVE_VINE_WHIP, .level = 7},
    {.move = MOVE_RAZOR_LEAF, .level = 13},
    {.move = MOVE_SLEEP_POWDER, .level = 20},
    {.move = MOVE_BODY_SLAM, .level = 34},
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

SPECIES_HEADER = """
#define SPECIES_NONE                                    0
#define SPECIES_BULBASAUR                               1
#define SPECIES_EGG                                     (SPECIES_BULBASAUR + 1)
#define NUM_SPECIES SPECIES_EGG
#define SPECIES_SHINY_TAG 5000
"""

# A synthetic `.party` fixture in the pinned source's own shape.
PARTY = """/* ==================== Fixture Trainers ==================== */

=== TRAINER_RIVAL_1_HNS ===
Name: RIVAL
Class: Rival Hns
Pic: Rival
Gender: Male
Music: Rg Rival
Double Battle: No
AI: Basic Trainer

Bulbasaur @ Oran Berry
Ability: Chlorophyll
Level: 5
IVs: 0 HP / 0 Atk / 0 Def / 0 SpA / 0 SpD / 0 Spe
Moves:
- Tackle
- Growl

Bulbasaur
Level: 20
IVs: 31 HP / 31 Atk / 31 Def / 31 SpA / 31 SpD / 31 Spe

=== TRAINER_TWIN_HNS ===
Name: TWIN
Class: Twins Hns
Pic: Twins
Gender: Female
Music: Hg Twin
Double Battle: Yes
AI: Check Bad Move

Bulbasaur
Level: 34
"""

SPECIES_BY_NAME = {"bulbasaur": "SPECIES_BULBASAUR"}
SPECIES_IDS_BY_SYMBOL = {"SPECIES_BULBASAUR": 1}


class FixtureLookupMixin(unittest.TestCase):
    """Install the pinned-identity lookup the reader resolves species through."""

    def setUp(self):
        # The generator populates these module-level lookup tables from the pinned tables; here
        # they are installed from the fixtures.
        self._install(src.SPECIES_ID_BY_SYMBOL, dict(SPECIES_IDS_BY_SYMBOL))
        self._install(
            src.ABILITY_SYMBOL_BY_NAME,
            {re.sub(r"[^a-z0-9]", "", k[len("ABILITY_"):].lower()): k for k in ABILITIES},
        )
        self._install(
            src.HNS_ABILITY_TITLE_CASE,
            {
                "ABILITY_NONE": "None",
                "ABILITY_OVERGROW": "Overgrow",
                "ABILITY_CHLOROPHYLL": "Chlorophyll",
                "ABILITY_SUNNY_DAY": "Sunny Day",
            },
        )
        self._install(
            src.ITEM_SYMBOL_BY_NAME,
            {
                re.sub(r"[^a-z0-9]", "", (v["source_name"] or "").lower()): k
                for k, v in ITEMS.items()
                if v["source_name"]
            },
        )
        self._install(
            src.MOVE_SYMBOL_BY_NAME,
            {re.sub(r"[^a-z0-9]", "", v.name.lower()): k for k, v in MOVES.items()},
        )

    def _install(self, target, values):
        target.clear()
        target.update(values)
        self.addCleanup(target.clear)


def species_by_id():
    entries = [
        ("0", '.speciesName = _("??????????"),'),
        ("1", '.speciesName = _("BULBASAUR"),\n'
              '        .abilities = { ABILITY_OVERGROW, ABILITY_CHLOROPHYLL, ABILITY_NONE },\n'
              '        .levelUpLearnset = sBulbasaurLevelUpLearnset,'),
    ]
    resolved, _ = src.parse_species(entries, "fixture", src.is_placeholder_species_name)
    return resolved


def learnsets():
    return src.parse_learnsets(SPECIES_SOURCE, "fixture")


def resolve(text, display_names=None):
    return src.resolve_trainers(
        text,
        species_by_id=species_by_id(),
        species_symbols_by_name=SPECIES_BY_NAME,
        moves_by_symbol=MOVES,
        items_by_symbol=ITEMS,
        learnset_by_symbol=learnsets(),
        ability_ids_by_symbol=ABILITIES,
        display_names=display_names,
        where="fixture:trainers.party",
    )


class PartyReaderTest(FixtureLookupMixin):

    def test_parses_every_trainer_block_of_the_fixture(self):
        trainers = resolve(PARTY)
        self.assertEqual(["TRAINER_RIVAL_1_HNS", "TRAINER_TWIN_HNS"], [t.key for t in trainers])
        self.assertEqual(2, len(trainers[0].party))
        self.assertEqual("RIVAL", trainers[0].name)
        self.assertEqual("Rival Hns", trainers[0].trainer_class)
        self.assertEqual("TRAINER_BATTLE_TYPE_SINGLES", trainers[0].battle_type)
        self.assertEqual("TRAINER_BATTLE_TYPE_DOUBLES", trainers[1].battle_type)

    def test_party_order_is_the_file_order(self):
        self.assertEqual([0, 1], [m.party_slot for m in resolve(PARTY)[0].party])

    def test_species_line_carries_the_held_item_and_it_resolves_to_the_pinned_identity(self):
        mon = resolve(PARTY)[0].party[0]
        # `speciesRaw` keeps the source line verbatim, item suffix included.
        self.assertEqual("Bulbasaur @ Oran Berry", mon.species_name_raw)
        self.assertEqual("SPECIES_BULBASAUR", mon.species_symbol)
        self.assertEqual(1, mon.species_id)
        self.assertEqual(139, mon.item_id)
        self.assertEqual("ORAN BERRY", mon.item_name)

    def test_an_omitted_item_is_ITEM_NONE_not_unknown(self):
        mon = resolve(PARTY)[0].party[1]
        self.assertEqual(0, mon.item_id)
        self.assertEqual("ITEM_NONE", mon.item_symbol)

    def test_declared_ability_is_used_and_attributed_to_the_party_entry(self):
        mon = resolve(PARTY)[0].party[0]
        self.assertEqual(34, mon.ability_id)
        self.assertEqual("ABILITY_CHLOROPHYLL", mon.ability_symbol)
        self.assertEqual("party-entry", mon.ability_source)

    def test_omitted_ability_is_species_slot_zero_not_zero(self):
        mon = resolve(PARTY)[0].party[1]
        self.assertEqual(65, mon.ability_id)
        self.assertEqual("ABILITY_OVERGROW", mon.ability_symbol)
        self.assertEqual("species-slot-0", mon.ability_source)

    def test_explicit_moves_are_used_verbatim(self):
        mon = resolve(PARTY)[0].party[0]
        self.assertEqual(("Tackle", "Growl"), mon.moves)
        self.assertEqual("party-entry", mon.moves_source)

    def test_omitted_moves_resolve_through_the_level_up_learnset(self):
        # `GiveBoxMonInitialMoveset` keeps the four most recently learned distinct moves at or
        # below the level: at level 20 that is Growl, Vine Whip, Razor Leaf, Sleep Powder.
        mon = resolve(PARTY)[0].party[1]
        self.assertEqual("level-up-learnset", mon.moves_source)
        self.assertEqual(("Growl", "Vine Whip", "Razor Leaf", "Sleep Powder"), mon.moves)

    def test_learnset_window_stops_at_the_mon_level(self):
        self.assertEqual(
            ("MOVE_TACKLE", "MOVE_GROWL", "MOVE_VINE_WHIP", "MOVE_RAZOR_LEAF"),
            src.initial_moveset(learnsets()["sBulbasaurLevelUpLearnset"], 13),
        )

    def test_learnset_window_skips_duplicates_and_drops_the_oldest_move(self):
        learnset = (
            (1, "MOVE_TACKLE"), (1, "MOVE_GROWL"), (2, "MOVE_GROWL"),
            (3, "MOVE_VINE_WHIP"), (4, "MOVE_RAZOR_LEAF"), (5, "MOVE_BODY_SLAM"),
        )
        # Growl repeats and is not added twice; the fifth distinct move pushes Tackle out.
        self.assertEqual(
            ("MOVE_GROWL", "MOVE_VINE_WHIP", "MOVE_RAZOR_LEAF", "MOVE_BODY_SLAM"),
            src.initial_moveset(learnset, 10),
        )

    def test_display_name_comes_from_the_caller_supplied_pinned_table(self):
        mon = resolve(PARTY, display_names={1: "Bulbasaur"})[0].party[0]
        self.assertEqual("Bulbasaur", mon.species_name)

    def test_missing_display_name_fails_closed(self):
        with self.assertRaises(SourceError):
            resolve(PARTY, display_names={})


class PartyHelpersTest(FixtureLookupMixin):

    def test_species_key_strips_the_item_and_the_gender_marker(self):
        self.assertEqual("absol", src.party_species_key("Absol @ Leftovers"))
        self.assertEqual("blastoise", src.party_species_key("Blastoise (M)"))
        self.assertEqual("exeggutoralola", src.party_species_key("Exeggutor-Alola"))
        self.assertEqual("mrmime", src.party_species_key("Mr. Mime"))

    def test_species_item_split_refuses_more_than_one_at_sign(self):
        with self.assertRaises(SourceError):
            src.split_species_and_item("Absol @ Leftovers @ Extra", "fixture")
        with self.assertRaises(SourceError):
            src.split_species_and_item("Absol @", "fixture")

    def test_ivs_accepts_every_spelling_the_pinned_source_uses(self):
        self.assertEqual(
            (0, 0, 0, 0, 0, 0),
            src._parse_ivs("0 HP / 0 Atk / 0 Def / 0 SpA / 0 SpD / 0 Spe", "x"),
        )
        self.assertEqual((31,) * 6, src._parse_ivs("31", "x"))
        self.assertEqual((31,) * 6, src._parse_ivs("31 Spe", "x"))
        self.assertEqual([0, 31], sorted(set(src._parse_ivs("0 Atk / 31 Spe", "x"))))

    def test_ivs_refuses_an_unparsable_or_out_of_range_value(self):
        for bad in ("lots", "32", "0 Wings", "0 Atk / 0 Atk", ""):
            with self.assertRaises(SourceError):
                src._parse_ivs(bad, "x")

    def test_symbol_index_prefers_the_unqualified_base_identity(self):
        constants = {
            "SPECIES_PIKACHU": 25,
            "SPECIES_PIKACHU_COSPLAY": 1009,
            "SPECIES_DUDUNSPARCE_TWO_SEGMENT": 1373,
        }
        index = src.build_species_symbols_by_name(constants, {25: None, 1009: None, 1373: None})
        self.assertEqual("SPECIES_PIKACHU", index.get("pikachu"))
        self.assertEqual("SPECIES_PIKACHU_COSPLAY", index.get("pikachucosplay"))

    def test_an_identity_with_no_unqualified_base_stays_unresolvable(self):
        constants = {"SPECIES_UNOWN_A": 1, "SPECIES_UNOWN_B": 2}
        index = src.build_species_symbols_by_name(constants, {1: None, 2: None})
        self.assertNotIn("unown", index)


class FailClosedTest(FixtureLookupMixin):
    """Every shape the reader was not written for must raise, never be skipped."""

    def test_content_before_the_first_trainer_block_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve("stray text\n" + PARTY)
        self.assertIn("before the first trainer block", str(ctx.exception))

    def test_duplicate_trainer_block_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY + PARTY)
        self.assertIn("duplicate trainer block", str(ctx.exception))

    def test_unknown_trainer_property_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("AI: Basic Trainer", "AI: Basic Trainer\nColour: Blue"))
        self.assertIn("Colour", str(ctx.exception))

    def test_unknown_party_member_property_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Level: 5", "Level: 5\nSpecialForm: Yes"))
        self.assertIn("SpecialForm", str(ctx.exception))

    def test_missing_double_battle_format_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Double Battle: No\n", ""))
        self.assertIn("Double Battle", str(ctx.exception))

    def test_unrecognised_double_battle_value_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Double Battle: No", "Double Battle: Maybe"))
        self.assertIn("Double Battle", str(ctx.exception))

    def test_missing_name_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Name: RIVAL\n", ""))
        self.assertIn("Name", str(ctx.exception))

    def test_missing_level_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Level: 34\n", ""))
        self.assertIn("Level", str(ctx.exception))

    def test_non_numeric_level_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Level: 34", "Level: very"))
        self.assertIn("non-numeric Level", str(ctx.exception))

    def test_unknown_species_spelling_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Bulbasaur\nLevel: 34\n", "Missingno\nLevel: 34\n"))
        self.assertIn("Missingno", str(ctx.exception))

    def test_unknown_item_spelling_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("@ Oran Berry", "@ Mystery Berry"))
        self.assertIn("Mystery Berry", str(ctx.exception))

    def test_unknown_ability_spelling_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Ability: Chlorophyll", "Ability: Wonder Guard"))
        self.assertIn("Wonder Guard", str(ctx.exception))

    def test_ability_outside_the_species_slots_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Ability: Chlorophyll", "Ability: Sunny Day"))
        self.assertIn("Sunny Day", str(ctx.exception))

    def test_unknown_move_spelling_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("- Growl", "- Hyper Beam"))
        self.assertIn("Hyper Beam", str(ctx.exception))

    def test_unknown_nature_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Level: 34", "Modest Nature\nLevel: 34").replace(
                "Modest Nature", "Grumpy Nature"
            ))
        self.assertIn("nature", str(ctx.exception))

    def test_a_known_nature_is_accepted(self):
        trainers = resolve(PARTY.replace("Level: 34", "Modest Nature\nLevel: 34"))
        self.assertEqual(34, trainers[1].party[0].level)

    def test_a_trailing_comment_is_refused(self):
        # The pinned source carries no inline comments, and a value the reader cannot parse
        # verbatim is refused rather than trimmed into a plausible-looking number.
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace(
                "Bulbasaur\nLevel: 34\n", "Bulbasaur\nLevel: 34  // thirty four\n"
            ))
        self.assertIn("non-numeric Level", str(ctx.exception))

    def test_a_bare_bullet_list_is_read_as_moves(self):
        # The pinned source writes a moveset either as `Moves:` followed by bullets or as the
        # bullets alone, so both shapes must resolve to the party entry's moveset.
        trainers = resolve(PARTY.replace(
            "Bulbasaur\nLevel: 34\n", "Bulbasaur\nLevel: 34\n- Tackle\n- Growl\n"
        ))
        self.assertEqual(("Tackle", "Growl"), trainers[1].party[0].moves)
        self.assertEqual("party-entry", trainers[1].party[0].moves_source)

    def test_a_trainer_with_no_party_member_is_refused(self):
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("Bulbasaur\nLevel: 34\n", ""))
        self.assertIn("no party member", str(ctx.exception))

    def test_a_party_larger_than_PARTY_SIZE_is_refused(self):
        block = "".join("\nBulbasaur\nLevel: 5\n" for _ in range(7))
        with self.assertRaises(SourceError) as ctx:
            resolve(PARTY.replace("\nBulbasaur\nLevel: 34\n", block))
        self.assertIn("PARTY_SIZE", str(ctx.exception))

    def test_empty_party_source_is_refused(self):
        with self.assertRaises(SourceError):
            src.read_party_blocks("", "fixture")

    def test_unbalanced_braces_are_refused(self):
        with self.assertRaises(SourceError):
            src.match_brace("{ { }", 0)

    def test_empty_species_table_is_refused(self):
        with self.assertRaises(SourceError):
            src.parse_species([], "fixture")

    def test_learnset_without_entries_is_refused(self):
        with self.assertRaises(SourceError):
            src.parse_learnsets(
                "static const struct LevelUpMove sXLevelUpLearnset[] = {\n};", "fixture"
            )

    def test_species_ability_slot_that_is_not_a_constant_is_refused(self):
        with self.assertRaises(SourceError):
            src.parse_species(
                [("1", '.speciesName = _("X"),\n'
                     '        .abilities = { SOME_MACRO(1) },\n'
                     '        .levelUpLearnset = sXLevelUpLearnset,')],
                "fixture",
            )


class SpeciesConstantsTest(unittest.TestCase):

    def test_resolves_plain_defines(self):
        constants = gen.parse_species_constants(SPECIES_HEADER, "fixture")
        self.assertEqual(0, constants["SPECIES_NONE"])
        self.assertEqual(1, constants["SPECIES_BULBASAUR"])
        # The count and the out-of-domain tag are defines too; the IDENTITY INDEX is what keeps
        # them out of the species domain, via the `species_by_id` filter.
        self.assertEqual(5000, constants["SPECIES_SHINY_TAG"])
        self.assertNotIn("SPECIES_EGG", constants)

    def test_resolves_alias_defines_to_a_fixpoint(self):
        text = SPECIES_HEADER + (
            "#define SPECIES_DUDUNSPARCE_TWO_SEGMENT 7\n"
            "#define SPECIES_DUDUNSPARCE SPECIES_DUDUNSPARCE_TWO_SEGMENT\n"
            "#define SPECIES_DUDUNSPARCE_ALIAS SPECIES_DUDUNSPARCE\n"
        )
        constants = gen.parse_species_constants(text, "fixture")
        self.assertEqual(7, constants["SPECIES_DUDUNSPARCE"])
        self.assertEqual(7, constants["SPECIES_DUDUNSPARCE_ALIAS"])

    def test_an_alias_of_an_undefined_symbol_is_refused(self):
        text = SPECIES_HEADER + "#define SPECIES_ALIAS SPECIES_NOT_THERE\n"
        with self.assertRaises(Exception) as ctx:
            gen.parse_species_constants(text, "fixture")
        self.assertIn("refusing to invent an ID", str(ctx.exception))

    def test_conflicting_values_are_refused(self):
        text = SPECIES_HEADER + "#define SPECIES_BULBASAUR 99\n"
        with self.assertRaises(Exception) as ctx:
            gen.parse_species_constants(text, "fixture")
        self.assertIn("conflicting values", str(ctx.exception))

    def test_a_header_with_no_identity_is_refused(self):
        with self.assertRaises(Exception) as ctx:
            gen.parse_species_constants("#define SOMETHING_ELSE 1\n", "fixture")
        self.assertIn("SPECIES_* identity", str(ctx.exception))


class CanonicalJsonTest(unittest.TestCase):

    def test_json_is_byte_stable_and_keeps_declaration_order(self):
        payload = {"b": 1, "a": [1, 2], "c": {"z": True}}
        rendered = gen.canonical_json(payload)
        self.assertEqual(rendered, gen.canonical_json(payload))
        self.assertLess(rendered.index('"b"'), rendered.index('"a"'))
        self.assertTrue(rendered.endswith("\n"))


if __name__ == "__main__":
    unittest.main()
