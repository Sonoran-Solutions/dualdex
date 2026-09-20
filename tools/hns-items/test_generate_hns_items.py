#!/usr/bin/env python3
"""Executable tests for the H&S 2.0.5 item-catalogue generator (issue #9, Gap C3).

These tests drive the REAL extraction code (the `enum Item` parser, the
`gItemsInfo[]` table parser, the alias/count cross-check, and the Kotlin
renderer) against tiny synthetic upstream checkouts. The only stand-in is the
preprocessor binary: fixtures are already in final preprocessed shape, so the
stub "cpp" just echoes the file the generator asks it to preprocess. No real
checkout, no toolchain, no ROM, and no network are needed.

Why these cases matter: the item enum mixes explicit integer assignments,
aliases of already-defined members, implicit previous+1 members, and
preprocessor macro expansions that emit many comma-separated declarations on a
single physical line (the TM/HM lists). An assignment the parser cannot resolve
must be a hard error, never a sequential ID invented from the running counter.
"""

import os
import stat
import sys
import tempfile
import textwrap
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import generate_hns_items as gen  # noqa: E402


STUB_CPP = textwrap.dedent("""\
    #!/usr/bin/env python3
    import sys
    with open(sys.argv[-1], "r", encoding="utf-8") as f:
        sys.stdout.write(f.read())
""")


def write_fixture(enum_body, table_body, extra_c=""):
    """Create a synthetic upstream checkout + stub preprocessor in a temp dir.

    Both the enum and the table are read from the same `src/item.c` fixture,
    exactly as the real generator preprocesses them from one translation unit.
    `extra_c` is inserted between them (e.g. a separate enum the table references).
    Returns (upstream_dir, cpp_bin).
    """
    tmp = tempfile.mkdtemp(prefix="hns_items_test_")
    upstream = os.path.join(tmp, "upstream")
    os.makedirs(os.path.join(upstream, "src"))
    os.makedirs(os.path.join(upstream, "include"))

    with open(os.path.join(upstream, "src", "item.c"), "w", encoding="utf-8") as f:
        f.write("enum __attribute__((packed)) Item\n{\n")
        f.write(enum_body)
        f.write("};\n\n")
        f.write(extra_c)
        f.write("\nconst struct ItemInfo gItemsInfo[] =\n{\n")
        f.write(table_body)
        f.write("};\n")

    cpp_bin = os.path.join(tmp, "stub_cpp.py")
    with open(cpp_bin, "w", encoding="utf-8") as f:
        f.write(STUB_CPP)
    os.chmod(cpp_bin, os.stat(cpp_bin).st_mode | stat.S_IXUSR)
    return upstream, cpp_bin


def table_for(item_by_id):
    """Build a gItemsInfo body from {id: (symbol, name, effect, param)}."""
    out = []
    for item_id in sorted(item_by_id):
        symbol, name, effect, param = item_by_id[item_id]
        name_src = "gQuestionMarksItemName" if name is None else f'ITEM_NAME("{name}")'
        param_src = f', .holdEffectParam = {param}' if param is not None else ""
        out.append(
            f"    [{symbol}] = {{ .name = {name_src}, .holdEffect = {effect}{param_src} }},\n"
        )
    return "".join(out)


BASE_ITEMS = {
    0: ("ITEM_NONE", None, "HOLD_EFFECT_NONE", None),
    1: ("ITEM_POKE_BALL", "POKé BALL", "HOLD_EFFECT_NONE", None),
    2: ("ITEM_CHARCOAL", "CHARCOAL", "HOLD_EFFECT_TYPE_POWER", 20),
    3: ("ITEM_CHOICE_BAND", "CHOICE BAND", "HOLD_EFFECT_CHOICE_BAND", None),
}

BASE_ENUM = (
    "    ITEM_NONE = 0,\n"
    "    ITEM_POKE_BALL = 1,\n"
    "    ITEM_CHARCOAL = 2,\n"
    "    ITEM_CHOICE_BAND = 3,\n"
    "    ITEMS_COUNT,\n"
    "    ITEM_FIELD_ARROW = ITEMS_COUNT,\n"
)


def parse_enum(enum_body):
    """Parse an `enum Item` body the way the real generator does (full enum text)."""
    return gen.parse_item_enum(
        "enum __attribute__((packed)) Item\n{\n" + enum_body + "};\n"
    )


class ItemEnumParserTests(unittest.TestCase):
    def test_explicit_alias_and_implicit_values_resolve(self):
        enum_body = (
            "    ITEM_NONE = 0,\n"
            "    ITEM_REAL = 5,\n"
            "    ITEM_ALIAS = ITEM_REAL,\n"
            "    ITEM_NEXT,\n"
            "    ITEMS_COUNT,\n"
        )
        ids, count = parse_enum(enum_body)
        self.assertEqual(0, ids["ITEM_NONE"])
        self.assertEqual(5, ids["ITEM_REAL"])
        self.assertEqual(5, ids["ITEM_ALIAS"])
        self.assertEqual(6, ids["ITEM_NEXT"])
        self.assertEqual(7, count)

    def test_macro_expanded_comma_list_resolves(self):
        # Mirrors the preprocessor's FOREACH_TM expansion: many declarations on one
        # physical line, separated by commas.
        enum_body = (
            "    ITEM_NONE = 0,\n"
            "    ITEM_TM01 = 10, ITEM_TM_A = ITEM_TM01, ITEM_TM02 = 11, ITEM_TM_B = ITEM_TM02,\n"
            "    ITEMS_COUNT,\n"
        )
        ids, count = parse_enum(enum_body)
        self.assertEqual(10, ids["ITEM_TM_A"])
        self.assertEqual(11, ids["ITEM_TM_B"])
        self.assertEqual(12, count)

    def test_bookkeeping_index_anchors_are_tolerated_but_not_identities(self):
        enum_body = (
            "    ITEM_NONE = 0,\n"
            "    ITEM_A = 1,\n"
            "    FIRST_MAIL_INDEX = 2,\n"
            "    ITEM_MAIL = FIRST_MAIL_INDEX,\n"
            "    FIRST_BERRY_INDEX = 3,\n"
            "    ITEM_BERRY = FIRST_BERRY_INDEX,\n"
            "    LAST_BERRY_INDEX = ITEM_BERRY,\n"
            "    ITEMS_COUNT,\n"
            "    ITEM_FIELD_ARROW = ITEMS_COUNT,\n"
        )
        ids, count = parse_enum(enum_body)
        self.assertEqual(2, ids["ITEM_MAIL"])
        self.assertEqual(3, ids["ITEM_BERRY"])
        self.assertEqual(4, count)

    def test_an_unknown_bookkeeping_anchor_fails_closed(self):
        # A new non-ITEM_* enumerator must be added to the parser deliberately, never
        # silently accepted with an invented sequential ID.
        with self.assertRaises(gen.GenerationError):
            parse_enum(
                "    ITEM_NONE = 0,\n    ITEM_A = 1,\n    NEW_ANCHOR = 2,\n"
                "    ITEM_B = NEW_ANCHOR,\n    ITEMS_COUNT,\n"
            )

    def test_unresolved_alias_fails(self):
        enum_body = (
            "    ITEM_NONE = 0,\n"
            "    ITEM_BAD = ITEM_DOES_NOT_EXIST,\n"
            "    ITEMS_COUNT,\n"
        )
        with self.assertRaises(gen.GenerationError):
            parse_enum(enum_body)

    def test_parenthesized_and_arithmetic_assignments_fail(self):
        for bad in ("(1)", "1 + 1", "1 << 2"):
            enum_body = f"    ITEM_NONE = 0,\n    ITEM_BAD = {bad},\n    ITEMS_COUNT,\n"
            with self.assertRaises(gen.GenerationError):
                parse_enum(enum_body)

    def test_item_none_must_be_zero(self):
        with self.assertRaises(gen.GenerationError):
            parse_enum("    ITEM_NONE = 1,\n    ITEMS_COUNT,\n")

    def test_count_must_directly_follow_the_highest_identity(self):
        # ITEMS_COUNT = 5 but the highest identity is 2 -> the header drifted.
        with self.assertRaises(gen.GenerationError):
            parse_enum(
                "    ITEM_NONE = 0,\n    ITEM_A = 1,\n    ITEM_B = 2,\n"
                "    ITEMS_COUNT = 5,\n"
            )


class ItemTableExtractionTests(unittest.TestCase):
    def test_contiguous_catalogue_is_built_with_hold_effects(self):
        upstream, cpp = write_fixture(BASE_ENUM, table_for(BASE_ITEMS))
        out = gen.run_cpp(cpp, upstream, "src/item.c")
        ids, count = gen.parse_item_enum(out)
        symbols = gen.parse_all_enum_symbols(out)
        table = gen.extract_item_table(out, symbols)
        catalogue = gen.build_catalogue(ids, count, table)

        self.assertEqual([0, 1, 2, 3], [e["id"] for e in catalogue])
        self.assertEqual("ITEM_CHARCOAL", catalogue[2]["symbol"])
        self.assertEqual("HOLD_EFFECT_TYPE_POWER", catalogue[2]["hold_effect"])
        # TYPE_BOOST_PARAM is resolved from an enum/macro symbol.
        self.assertEqual(20, catalogue[2]["hold_effect_param"])

    def test_missing_table_entry_fails(self):
        items = dict(BASE_ITEMS)
        del items[3]
        upstream, cpp = write_fixture(BASE_ENUM, table_for(items))
        out = gen.run_cpp(cpp, upstream, "src/item.c")
        ids, count = gen.parse_item_enum(out)
        table = gen.extract_item_table(out, gen.parse_all_enum_symbols(out))
        with self.assertRaises(gen.GenerationError):
            gen.build_catalogue(ids, count, table)

    def test_unknown_table_symbol_fails(self):
        items = dict(BASE_ITEMS)
        items[3] = ("ITEM_NOT_IN_ENUM", "BOGUS", "HOLD_EFFECT_NONE", None)
        upstream, cpp = write_fixture(BASE_ENUM, table_for(items))
        out = gen.run_cpp(cpp, upstream, "src/item.c")
        ids, count = gen.parse_item_enum(out)
        table = gen.extract_item_table(out, gen.parse_all_enum_symbols(out))
        with self.assertRaises(gen.GenerationError):
            gen.build_catalogue(ids, count, table)

    def test_symbolic_hold_effect_param_resolves_through_enum(self):
        items = dict(BASE_ITEMS)
        items[2] = ("ITEM_CHARCOAL", "CHARCOAL", "HOLD_EFFECT_TYPE_POWER", "TYPE_FIRE")
        enum_body = (
            "    ITEM_NONE = 0,\n    ITEM_POKE_BALL = 1,\n    ITEM_CHARCOAL = 2,\n"
            "    ITEM_CHOICE_BAND = 3,\n    ITEMS_COUNT,\n"
        )
        extra = "enum __attribute__((packed)) Type { TYPE_NONE = 0, TYPE_FIRE = 11 };\n"
        upstream, cpp = write_fixture(enum_body, table_for(items), extra_c=extra)
        out = gen.run_cpp(cpp, upstream, "src/item.c")
        ids, count = gen.parse_item_enum(out)
        table = gen.extract_item_table(out, gen.parse_all_enum_symbols(out))
        self.assertEqual(11, table["ITEM_CHARCOAL"]["hold_effect_param"])

    def test_unresolvable_hold_effect_param_fails(self):
        items = dict(BASE_ITEMS)
        items[2] = ("ITEM_CHARCOAL", "CHARCOAL", "HOLD_EFFECT_TYPE_POWER", "TYPE_NOT_DEFINED")
        upstream, cpp = write_fixture(BASE_ENUM, table_for(items))
        out = gen.run_cpp(cpp, upstream, "src/item.c")
        symbols = gen.parse_all_enum_symbols(out)
        with self.assertRaises(gen.GenerationError):
            gen.extract_item_table(out, symbols)


class KotlinRenderTests(unittest.TestCase):
    def test_render_marks_the_artifact_generated_and_pins_the_domain(self):
        upstream, cpp = write_fixture(BASE_ENUM, table_for(BASE_ITEMS))
        out = gen.run_cpp(cpp, upstream, "src/item.c")
        ids, count = gen.parse_item_enum(out)
        table = gen.extract_item_table(out, gen.parse_all_enum_symbols(out))
        catalogue = gen.build_catalogue(ids, count, table)
        kotlin = gen.generate_kotlin(catalogue, count)
        self.assertIn("GENERATED FILE - do not edit by hand", kotlin)
        self.assertIn("const val ITEM_COUNT: Int = 4", kotlin)
        self.assertIn("const val ITEM_ID_MAX: Int = 3", kotlin)
        self.assertIn('register(2, "ITEM_CHARCOAL", "CHARCOAL", "HOLD_EFFECT_TYPE_POWER", 20)', kotlin)

    def test_normalize_name_is_case_and_hyphen_insensitive(self):
        self.assertEqual("never melt ice", gen.normalize_name("Never-Melt Ice"))
        self.assertEqual("exp. share", gen.normalize_name("  EXP.   SHARE "))


if __name__ == "__main__":
    unittest.main()
