#!/usr/bin/env python3
"""Self-contained tests for the H&S field-status audit generator.

They drive the real parsing and checking functions against small synthetic fixtures, so they need no
upstream checkout, no toolchain and no network. They pin the fail-closed contract: a moved, renamed,
added or removed `STATUS_FIELD_*` bit, a changed `STATUS_FIELD_TERRAIN_ANY`, a new or moved pinned
field reference, stale evidence, or an unreviewed rule must fail; and the committed generated Kotlin
and C header must agree with the committed reviewed bit table.
"""
import copy
import importlib.util
import json
import pathlib
import re
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("gen", HERE / "generate_hns_field_status.py")
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)

PINNED_BATTLE_H = """
#define STATUS_FIELD_MAGIC_ROOM                     (1 << 0)
#define STATUS_FIELD_TRICK_ROOM                     (1 << 1)
#define STATUS_FIELD_WONDER_ROOM                    (1 << 2)
#define STATUS_FIELD_MUDSPORT                       (1 << 3)
#define STATUS_FIELD_WATERSPORT                     (1 << 4)
#define STATUS_FIELD_GRAVITY                        (1 << 5)
#define STATUS_FIELD_GRASSY_TERRAIN                 (1 << 6)
#define STATUS_FIELD_MISTY_TERRAIN                  (1 << 7)
#define STATUS_FIELD_ELECTRIC_TERRAIN               (1 << 8)
#define STATUS_FIELD_PSYCHIC_TERRAIN                (1 << 9)
#define STATUS_FIELD_ION_DELUGE                     (1 << 10)
#define STATUS_FIELD_FAIRY_LOCK                     (1 << 11)

#define STATUS_FIELD_TERRAIN_ANY        (STATUS_FIELD_GRASSY_TERRAIN | STATUS_FIELD_MISTY_TERRAIN | STATUS_FIELD_ELECTRIC_TERRAIN | STATUS_FIELD_PSYCHIC_TERRAIN)
"""


def audit():
    return json.loads((HERE / "field_audit.json").read_text())


class BitTableTests(unittest.TestCase):
    def check(self, text):
        bits, compositions = gen.parse_field_constants(text)
        gen.check_bit_table(bits, compositions, audit())
        return bits

    def test_pinned_table_matches_review(self):
        bits = self.check(PINNED_BATTLE_H)
        self.assertEqual(12, len(bits))
        self.assertEqual(10, bits["STATUS_FIELD_ION_DELUGE"])

    def test_moved_bit_fails(self):
        moved = PINNED_BATTLE_H.replace("STATUS_FIELD_GRAVITY                        (1 << 5)",
                                        "STATUS_FIELD_GRAVITY                        (1 << 12)")
        with self.assertRaisesRegex(gen.AuditError, "moved"):
            self.check(moved)

    def test_swapped_bits_fail(self):
        swapped = PINNED_BATTLE_H.replace("(1 << 8)", "(1 << X)").replace("(1 << 9)", "(1 << 8)").replace("(1 << X)", "(1 << 9)")
        with self.assertRaisesRegex(gen.AuditError, "moved"):
            self.check(swapped)

    def test_added_bit_fails(self):
        added = PINNED_BATTLE_H + "#define STATUS_FIELD_NEW_ROOM (1 << 12)\n"
        with self.assertRaisesRegex(gen.AuditError, "unreviewed"):
            self.check(added)

    def test_removed_or_renamed_bit_fails(self):
        renamed = PINNED_BATTLE_H.replace("STATUS_FIELD_FAIRY_LOCK ", "STATUS_FIELD_FAIRY_LOCKED ")
        with self.assertRaisesRegex(gen.AuditError, "changed"):
            self.check(renamed)

    def test_terrain_composition_change_fails(self):
        changed = PINNED_BATTLE_H.replace(" | STATUS_FIELD_PSYCHIC_TERRAIN)", ")")
        with self.assertRaisesRegex(gen.AuditError, "compositions"):
            self.check(changed)

    def test_unrecognised_definition_form_fails(self):
        with self.assertRaisesRegex(gen.AuditError, "unrecognised"):
            gen.parse_field_constants(PINNED_BATTLE_H + "#define STATUS_FIELD_WEIRD 0x4000\n")

    def test_shared_bit_fails(self):
        with self.assertRaisesRegex(gen.AuditError, "share"):
            gen.parse_field_constants(PINNED_BATTLE_H + "#define STATUS_FIELD_ALIAS (1 << 3)\n")


class ReferenceSiteTests(unittest.TestCase):
    def upstream(self, files):
        root = pathlib.Path(tempfile.mkdtemp())
        for rel, text in files.items():
            path = root / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        return root

    def fixture(self):
        return {
            "src/battle_util.c": "static u32 Foo(void)\n{\n    if (gFieldStatuses & STATUS_FIELD_GRAVITY)\n        return 1;\n}\n",
            "src/battle_ai_main.c": "u32 Ai(void)\n{\n    return gFieldStatuses & STATUS_FIELD_TRICK_ROOM;\n}\n",
        }

    def reviewed(self, sites):
        return {"reference_sites": {k: {**v, "use": "test", "damage": "none", "statuses": []} for k, v in sites.items()},
                "statuses": []}

    def test_scan_groups_by_definition_and_excludes_ai(self):
        sites = gen.scan_reference_sites(self.upstream(self.fixture()))
        self.assertEqual({"src/battle_util.c:Foo": {"lines": [3], "tokens": ["STATUS_FIELD_GRAVITY", "gFieldStatuses"]}}, sites)

    def test_new_or_moved_reference_fails(self):
        files = self.fixture()
        review = self.reviewed(gen.scan_reference_sites(self.upstream(files)))
        files["src/battle_util.c"] = "static u32 Foo(void)\n{\n\n    if (gFieldStatuses & STATUS_FIELD_GRAVITY)\n        return 1;\n}\n"
        with self.assertRaisesRegex(gen.AuditError, "lines/tokens changed"):
            gen.check_reference_sites(gen.scan_reference_sites(self.upstream(files)), review)
        files["src/battle_main.c"] = "u32 Bar(void)\n{\n    return IsBattlerGrounded(0, 0, 0);\n}\n"
        with self.assertRaisesRegex(gen.AuditError, "reference sites changed"):
            gen.check_reference_sites(gen.scan_reference_sites(self.upstream(files)), review)

    def test_site_needs_a_disposition(self):
        sites = gen.scan_reference_sites(self.upstream(self.fixture()))
        review = self.reviewed(sites)
        review["reference_sites"]["src/battle_util.c:Foo"]["damage"] = "maybe"
        with self.assertRaisesRegex(gen.AuditError, "disposition"):
            gen.check_reference_sites(sites, review)

    def test_stale_evidence_fails(self):
        root = self.upstream(self.fixture())
        gen.check_evidence(root, "r", [{"source": "src/battle_util.c:3", "contains": "STATUS_FIELD_GRAVITY"}])
        with self.assertRaisesRegex(gen.AuditError, "evidence changed"):
            gen.check_evidence(root, "r", [{"source": "src/battle_util.c:4", "contains": "STATUS_FIELD_GRAVITY"}])


class RuleReviewTests(unittest.TestCase):
    def test_unreviewed_policy_rule_fails(self):
        data = audit()
        data["statuses"] = []
        data["reference_sites"] = {}
        data["unknown_bits"]["evidence"] = []
        policy = 'rule = "unknown_field_bits"\nrule = "sneaky_clearance"\n'
        with self.assertRaises(gen.AuditError):
            gen.check_statuses(pathlib.Path("/nonexistent"), data, policy)

    def test_unimplemented_reviewed_rule_fails(self):
        data = audit()
        with self.assertRaisesRegex(gen.AuditError, "not implemented"):
            gen.check_statuses(pathlib.Path("/nonexistent"), data, "")

    def test_every_status_records_every_affects_flag(self):
        for status in audit()["statuses"]:
            self.assertEqual(set(gen.AFFECTS), set(status["affects"]), status["symbol"])


class CommittedArtifactTests(unittest.TestCase):
    """The committed generated artifacts must carry exactly the reviewed pinned bit table."""

    def test_kotlin_enum_matches_review(self):
        text = gen.KOTLIN_OUT.read_text()
        for status in audit()["statuses"]:
            line = f'    {status["kotlin_name"]}({status["bit"]}, "{status["symbol"]}", "{status["display_name"]}")'
            self.assertIn(line, text)
            self.assertIn(f'const val {status["symbol"]}: Int = 0x{1 << status["bit"]:08X}', text)
        self.assertIn("const val KNOWN_MASK: Int = 0x00000FFF", text)
        self.assertIn("const val STATUS_FIELD_TERRAIN_ANY: Int = 0x000003C0", text)

    def test_native_header_matches_review(self):
        text = gen.HEADER_OUT.read_text()
        for status in audit()["statuses"]:
            self.assertIn(f'#define HNS_{status["symbol"]} (1u << {status["bit"]})', text)
        self.assertIn("#define HNS_STATUS_FIELD_KNOWN_MASK 0x00000FFFu", text)

    def test_rendering_is_deterministic_for_the_pinned_table(self):
        bits, compositions = gen.parse_field_constants(PINNED_BATTLE_H)
        header = gen.render_header(audit(), bits, compositions)
        self.assertEqual(gen.HEADER_OUT.read_text(), header)
        kotlin = gen.render_kotlin(audit(), bits, compositions,
                                   self.committed_abilities(), self.committed_set("gravityBannedOrdinaryMoveIds"),
                                   self.committed_set("positivePriorityOrdinaryMoveIds"), self.committed_rules())
        self.assertEqual(gen.KOTLIN_OUT.read_text(), kotlin)

    @staticmethod
    def committed_abilities():
        return {m.group(1): int(m.group(2)) for m in
                re.finditer(r"const val (ABILITY_[A-Z_]+): Int = (\d+)", gen.KOTLIN_OUT.read_text())}

    @staticmethod
    def committed_set(name):
        match = re.search(rf"val {name}: Set<Int> = setOf\(([^)]*)\)", gen.KOTLIN_OUT.read_text())
        return [int(x) for x in match.group(1).split(",") if x.strip()]

    @staticmethod
    def committed_rules():
        block = gen.KOTLIN_OUT.read_text().split("val contextRuleNames: Set<String> = setOf(")[1]
        return re.findall(r'"([a-z0-9_]+)"', block)


class MoveFactTests(unittest.TestCase):
    def test_ordinary_move_facts(self):
        root = pathlib.Path(tempfile.mkdtemp())
        (root / "include/constants").mkdir(parents=True)
        (root / "src/data").mkdir(parents=True)
        (root / "include/constants/moves.h").write_text(
            "enum Move\n{\n    MOVE_NONE = 0,\n    MOVE_A,\n    MOVE_B,\n    MOVE_C,\n    MOVE_D,\n    MOVE_E,\n};\n")
        entry = "    [{sym}] =\n    {{\n        .effect = {eff},\n{extra}    }},\n"
        table = "const struct MoveInfo gMovesInfo[MOVES_COUNT_ALL] =\n{\n" + "".join([
            entry.format(sym="MOVE_A", eff="EFFECT_HIT", extra="        .priority = 1,\n"),
            entry.format(sym="MOVE_B", eff="EFFECT_HIT", extra="        .gravityBanned = TRUE,\n"),
            entry.format(sym="MOVE_C", eff="EFFECT_HIT", extra="        .priority = B_X ? 2 : 0,\n"),
            entry.format(sym="MOVE_D", eff="EFFECT_HIT", extra="        .healingMove = TRUE,\n        .priority = -1,\n"),
            entry.format(sym="MOVE_E", eff="EFFECT_GRAV_APPLE", extra="        .gravityBanned = TRUE,\n"),
        ]) + "};\n"
        (root / "src/data/moves_info.h").write_text(table)
        gravity, priority, terrain = gen.move_facts(root)
        self.assertEqual([2], gravity)          # MOVE_E is not ordinary
        self.assertEqual([1, 3, 4], priority)   # literal +1, unprovable expression, Triage healing move
        self.assertEqual([], terrain)


if __name__ == "__main__":
    unittest.main()
