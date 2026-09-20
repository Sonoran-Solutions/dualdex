#!/usr/bin/env python3
"""
Executable tests for the H&S data-pack generator's ability-enum parser (review R1).

These tests drive the REAL extraction code (`extract_abilities`, including
`run_cpp` and the gAbilitiesInfo cross-check) against tiny synthetic upstream
checkouts. The only stand-in is the preprocessor binary: test fixtures are
already in final preprocessed shape (no #include/#define remain), so the stub
"cpp" just echoes the file the generator asks it to preprocess. No real
checkout, no toolchain, no ROM, and no network are needed.

Why these cases matter: the old parser's assignment group was optional and the
match need not consume the declaration, so any assignment it could not
understand (an unresolved alias, a parenthesized initializer) silently became
"no assignment" and received the running counter - inventing a sequential ID
that contiguity checks cannot detect. The ABILITIES_COUNT_GEN* anchors are
enum members, not macros, and their symbolic aliases (ABILITY_TANGLED_FEET =
ABILITIES_COUNT_GEN3) only looked correct by coincidence with that counter.
"""

import os
import stat
import sys
import tempfile
import textwrap
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import generate_hns_data_pack as gen  # noqa: E402


# A stand-in for arm-none-eabi-cpp. The generator invokes it as
# `cpp -iquote ... -DMODERN=1 ... <src-file>`; the fixtures are already fully
# preprocessed, so the stub just writes the named source file to stdout.
STUB_CPP = textwrap.dedent("""\
    #!/usr/bin/env python3
    import sys
    with open(sys.argv[-1], "r", encoding="utf-8") as f:
        sys.stdout.write(f.read())
""")


def write_fixture(enum_body, table_body):
    """Create a synthetic upstream checkout + stub preprocessor in a temp dir.

    Returns (upstream_dir, cpp_bin), the two arguments extract_abilities takes.
    """
    tmp = tempfile.mkdtemp(prefix="hns_data_pack_test_")

    upstream = os.path.join(tmp, "upstream")
    os.makedirs(os.path.join(upstream, "include", "constants"))
    os.makedirs(os.path.join(upstream, "src", "data"))

    with open(os.path.join(upstream, "include", "constants", "abilities.h"), "w",
              encoding="utf-8") as f:
        f.write("enum __attribute__((packed)) Ability\n{\n")
        f.write(enum_body)
        f.write("};\n")

    with open(os.path.join(upstream, "src", "data", "abilities.h"), "w",
              encoding="utf-8") as f:
        f.write(
            "const struct AbilityInfo gAbilitiesInfo[ABILITIES_COUNT] =\n{\n"
            + table_body +
            "};\n"
        )

    cpp_bin = os.path.join(tmp, "stub_cpp.py")
    with open(cpp_bin, "w", encoding="utf-8") as f:
        f.write(STUB_CPP)
    os.chmod(cpp_bin, os.stat(cpp_bin).st_mode | stat.S_IXUSR)

    return upstream, cpp_bin


def table_for(*entries):
    """Build the gAbilitiesInfo body: one `[ABILITY_X] = { .name = _("Y") }` per entry."""
    return "".join(
        f'    [{constant}] = {{ .name = _("{name}") }},\n'
        for constant, name in entries
    )


BASE_TABLE = table_for(
    ("ABILITY_NONE", "-------"),
    ("ABILITY_STENCH", "STENCH"),
    ("ABILITY_DRIZZLE", "DRIZZLE"),
    ("ABILITY_ZEN_MODE", "ZEN MODE"),
    ("ABILITY_TANGLED_FEET", "TANGLED FEET"),
    ("ABILITY_MOTOR_DRIVE", "MOTOR DRIVE"),
    ("ABILITY_PICKPOCKET", "PICKPOCKET"),
    ("ABILITY_POISON_PUPPETEER", "POISON PUPPETEER"),
)


class AbilityEnumParserTests(unittest.TestCase):
    """parse_ability_enum/extract_abilities must fail on unsupported assignments."""

    # ------------------------------------------------------------------
    # Required case 1: an unresolved alias must FAIL, never become a
    # sequential ID. (Old behavior: `ABILITY_STENCH = UNRESOLVED_ALIAS,`
    # matched as bare `ABILITY_STENCH` and silently got the counter.)
    # ------------------------------------------------------------------

    def test_unresolved_alias_fails(self):
        upstream, cpp = write_fixture(
            "    ABILITY_NONE = 0,\n"
            "    ABILITY_STENCH = UNRESOLVED_ALIAS,\n",
            BASE_TABLE,
        )
        with self.assertRaises(ValueError) as ctx:
            gen.extract_abilities(cpp, upstream)
        self.assertIn("refusing to invent a sequential ID", str(ctx.exception))

    # ------------------------------------------------------------------
    # Required case 2: a parenthesized initializer must FAIL, never become a
    # sequential ID. (Old behavior: `ABILITY_SPEED_BOOST = (1),` got the
    # counter.)
    # ------------------------------------------------------------------

    def test_parenthesized_initializer_fails(self):
        upstream, cpp = write_fixture(
            "    ABILITY_NONE = 0,\n"
            "    ABILITY_SPEED_BOOST = (1),\n",
            BASE_TABLE,
        )
        with self.assertRaises(ValueError) as ctx:
            gen.extract_abilities(cpp, upstream)
        self.assertIn("refusing to invent a sequential ID", str(ctx.exception))

    # Same class of defect: any other unsupported expression must fail too.
    def test_arithmetic_assignment_fails(self):
        upstream, cpp = write_fixture(
            "    ABILITY_NONE = 0,\n"
            "    ABILITY_STENCH = 1 + 1,\n",
            BASE_TABLE,
        )
        with self.assertRaises(ValueError) as ctx:
            gen.extract_abilities(cpp, upstream)
        self.assertIn("refusing to invent a sequential ID", str(ctx.exception))

    def test_implicit_value_before_any_explicit_value_fails(self):
        upstream, cpp = write_fixture(
            "    ABILITY_STENCH,\n",
            BASE_TABLE,
        )
        with self.assertRaises(ValueError) as ctx:
            gen.extract_abilities(cpp, upstream)
        self.assertIn("implicit value", str(ctx.exception))

    # ------------------------------------------------------------------
    # Required case 3: the ABILITIES_COUNT_GEN* anchor pattern of the pinned
    # header. The anchors are enum members, not macros: an implicit count
    # member is followed by a symbolic alias (`ABILITY_TANGLED_FEET =
    # ABILITIES_COUNT_GEN3`), and the count total is itself an alias. The
    # explicit `ABILITIES_COUNT_GEN4 = 500` breaks the old parser's counter
    # coincidence: if aliases were resolved from the running counter,
    # ABILITY_PICKPOCKET would come out as 163 instead of 500.
    # ------------------------------------------------------------------

    def test_count_anchor_and_alias_pattern_resolves_explicitly(self):
        upstream, cpp = write_fixture(
            "    ABILITY_NONE = 0,\n"
            "    ABILITY_STENCH = 1,\n"
            "    ABILITY_DRIZZLE = 2,\n"
            "    ABILITY_ZEN_MODE = 161,\n"
            "    ABILITIES_COUNT_GEN3,\n"                          # implicit: 162
            "    ABILITY_TANGLED_FEET = ABILITIES_COUNT_GEN3,\n"   # alias: 162
            "    ABILITY_MOTOR_DRIVE = 163,\n"
            "    ABILITIES_COUNT_GEN4 = 500,\n"                    # explicit, not counter-adjacent
            "    ABILITY_PICKPOCKET = ABILITIES_COUNT_GEN4,\n"     # alias: must be 500, not 164
            "    ABILITY_POISON_PUPPETEER = 600,\n"
            "    ABILITIES_COUNT_GEN9,\n"                          # implicit: 601
            "    ABILITIES_COUNT = ABILITIES_COUNT_GEN9,\n"        # alias: 601
            ,
            BASE_TABLE,
        )
        abilities = gen.extract_abilities(cpp, upstream)
        self.assertEqual(
            {1: "STENCH", 2: "DRIZZLE", 161: "ZEN MODE",
             162: "TANGLED FEET", 163: "MOTOR DRIVE",
             500: "PICKPOCKET", 600: "POISON PUPPETEER"},
            {aid: a["name"] for aid, a in abilities.items()},
        )
        # The alias resolved through the anchor, not the counter.
        self.assertEqual(500, abilities[500]["id"])
        self.assertEqual("ABILITY_PICKPOCKET", abilities[500]["constant"])
        # Count anchors are bookkeeping members and never catalogue identities.
        for anchor in gen.ABILITY_COUNT_ANCHORS:
            self.assertNotIn(
                anchor,
                {a["constant"] for a in abilities.values()},
                f"count anchor {anchor} must not be a catalogue entry",
            )

    # The anchor pattern must also hold for the REAL header's shape, where the
    # anchor is implicit and every following alias duplicates it: the resolved
    # values must match the values the actual pinned build assigns (each
    # ABILITY_X = ABILITIES_COUNT_GENN member equals the anchor, and the next
    # explicit member continues after the last pre-anchor ability).
    def test_real_header_gen3_boundary_shape(self):
        upstream, cpp = write_fixture(
            "    ABILITY_NONE = 0,\n"
            "    ABILITY_STENCH = 1,\n"
            "    ABILITY_DRIZZLE = 2,\n"
            "    ABILITY_ZEN_MODE = 161,\n"
            "    ABILITIES_COUNT_GEN3,\n"                          # implicit: 162
            "    ABILITY_TANGLED_FEET = ABILITIES_COUNT_GEN3,\n"   # alias: 162
            "    ABILITY_MOTOR_DRIVE = 163,\n"
            "    ABILITIES_COUNT_GEN4,\n"                          # implicit: 164
            "    ABILITY_PICKPOCKET = ABILITIES_COUNT_GEN4,\n"     # alias: 164
            "    ABILITY_POISON_PUPPETEER = 165,\n"
            "    ABILITIES_COUNT_GEN9,\n"                          # implicit: 166
            "    ABILITIES_COUNT = ABILITIES_COUNT_GEN9,\n"        # alias: 166
            ,
            BASE_TABLE,
        )
        abilities = gen.extract_abilities(cpp, upstream)
        self.assertEqual(
            {1: "STENCH", 2: "DRIZZLE", 161: "ZEN MODE", 162: "TANGLED FEET",
             163: "MOTOR DRIVE", 164: "PICKPOCKET", 165: "POISON PUPPETEER"},
            {aid: a["name"] for aid, a in abilities.items()},
        )

    def test_count_anchor_must_agree_with_extracted_data(self):
        # ABILITIES_COUNT is parsed and cross-checked, not discarded: if the
        # declared total disagrees with the extracted enum, that is drift.
        upstream, cpp = write_fixture(
            "    ABILITY_NONE = 0,\n"
            "    ABILITY_STENCH = 1,\n"
            "    ABILITY_DRIZZLE = 2,\n"
            "    ABILITIES_COUNT = 999,\n",
            table_for(
                ("ABILITY_NONE", "-------"),
                ("ABILITY_STENCH", "STENCH"),
                ("ABILITY_DRIZZLE", "DRIZZLE"),
            ),
        )
        with self.assertRaises(ValueError) as ctx:
            gen.extract_abilities(cpp, upstream)
        self.assertIn("ABILITIES_COUNT", str(ctx.exception))

    def test_enum_table_drift_still_fails(self):
        # Regression guard for the pre-existing cross-checks the parser
        # rewrite must not weaken.
        upstream, cpp = write_fixture(
            "    ABILITY_NONE = 0,\n"
            "    ABILITY_STENCH = 1,\n",
            table_for(("ABILITY_NONE", "-------"), ("ABILITY_DRIZZLE", "DRIZZLE")),
        )
        with self.assertRaises(ValueError) as ctx:
            gen.extract_abilities(cpp, upstream)
        self.assertIn("not in gAbilitiesInfo", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
