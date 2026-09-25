"""Self-contained tests for the H&S held-item capability audit checker.

They drive the real scanning/validation helpers of generate_hns_item_audit.py against tiny
synthetic source trees, so they need no upstream checkout, no ARM preprocessor, no ROM and no
network. They pin the fail-closed contract: excluded sources never count as reviewed references,
a changed evidence line fails, and literal item-identity reads are attributed to their enclosing
definition.
"""
import importlib.util
import json
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("hns_item_audit", HERE / "generate_hns_item_audit.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def write(root, rel, text):
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


class ScanTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        write(self.root, "src/battle_util.c",
              "static u32 CalcAttackStat(void)\n{\n    switch (ctx->holdEffectAtk)\n    {\n"
              "    case HOLD_EFFECT_CHOICE_BAND:\n        break;\n    }\n}\n"
              "enum HoldEffect GetBattlerHoldEffectInternal(u32 battler)\n{\n"
              "    if (gBattleMons[battler].item == ITEM_ENIGMA_BERRY_E_READER)\n        return 0;\n}\n")
        write(self.root, "src/battle_ai_main.c", "u32 x = HOLD_EFFECT_CHOICE_BAND; u32 y = ITEM_LEFTOVERS;\n")
        write(self.root, "src/battle_debug.c", "u32 x = HOLD_EFFECT_LEFTOVERS;\n")
        write(self.root, "src/pokemon.c", "u32 z = HOLD_EFFECT_FRIENDSHIP_UP;\n")

    def tearDown(self):
        self.tmp.cleanup()

    def test_hold_effect_refs_exclude_ai_debug_and_animation_sources(self):
        refs = audit.scan_hold_effect_refs(self.root)
        self.assertEqual(refs["HOLD_EFFECT_CHOICE_BAND"], ["src/battle_util.c:5"])
        self.assertEqual(refs["HOLD_EFFECT_FRIENDSHIP_UP"], ["src/pokemon.c:1"])
        self.assertNotIn("HOLD_EFFECT_LEFTOVERS", refs)

    def test_identity_sites_are_attributed_to_the_enclosing_definition(self):
        sites = audit.scan_identity_sites(self.root, {"ITEM_ENIGMA_BERRY_E_READER", "ITEM_LEFTOVERS"})
        self.assertEqual(sites, {
            "src/battle_util.c:GetBattlerHoldEffectInternal": {"ITEM_ENIGMA_BERRY_E_READER"}
        })

    def test_evidence_must_still_contain_the_quoted_text(self):
        audit.check_evidence(self.root, "ok", [
            {"source": "src/battle_util.c:3", "contains": "switch (ctx->holdEffectAtk)"}
        ])
        for bad in (
            [],
            [{"source": "src/battle_util.c:3", "contains": "switch (ctx->holdEffectDef)"}],
            [{"source": "src/battle_util.c:999", "contains": "x"}],
            [{"source": "src/missing.c:1", "contains": "x"}],
            [{"source": "src/battle_util.c:3", "contains": ""}],
        ):
            with self.assertRaises(audit.AuditError):
                audit.check_evidence(self.root, "bad", bad)


class FormChangeIdentityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        write(self.root, audit.FORM_CHANGE_TABLES,
              "static const struct FormChange sZacianFormChangeTable[] =\n{\n"
              "    {FORM_CHANGE_BEGIN_BATTLE, SPECIES_ZACIAN_CROWNED, ITEM_RUSTED_SWORD, MOVE_IRON_HEAD, MOVE_BEHEMOTH_BLADE},\n"
              "    {FORM_CHANGE_END_BATTLE,   SPECIES_ZACIAN_HERO,    ITEM_RUSTED_SWORD, MOVE_BEHEMOTH_BLADE, MOVE_IRON_HEAD},\n"
              "    {FORM_CHANGE_TERMINATOR},\n};\n"
              "    {FORM_CHANGE_ITEM_USE, SPECIES_SHAYMIN_SKY, ITEM_GRACIDEA, },\n")

    def tearDown(self):
        self.tmp.cleanup()

    def reviewed(self):
        return {
            "ITEM_RUSTED_SWORD": {"methods": ["FORM_CHANGE_BEGIN_BATTLE", "FORM_CHANGE_END_BATTLE"], "disposition": "x"},
            "ITEM_GRACIDEA": {"methods": ["FORM_CHANGE_ITEM_USE"], "disposition": "bag"},
        }

    def test_scan_collects_every_identity_with_its_methods(self):
        self.assertEqual(audit.scan_form_change_items(self.root), {
            "ITEM_RUSTED_SWORD": ["FORM_CHANGE_BEGIN_BATTLE", "FORM_CHANGE_END_BATTLE"],
            "ITEM_GRACIDEA": ["FORM_CHANGE_ITEM_USE"],
        })

    def test_battle_start_form_item_can_never_be_neutral(self):
        scanned = audit.scan_form_change_items(self.root)
        audit.check_form_change_identities(scanned, self.reviewed(), lambda s: "UNSUPPORTED_DAMAGE_RELEVANT")
        with self.assertRaises(audit.AuditError):
            audit.check_form_change_identities(
                scanned, self.reviewed(),
                lambda s: "PROVEN_NO_ORDINARY_DAMAGE_EFFECT" if s == "ITEM_RUSTED_SWORD" else "UNSUPPORTED_DAMAGE_RELEVANT")
        # A bag-use identity may stay neutral.
        audit.check_form_change_identities(
            scanned, self.reviewed(),
            lambda s: "PROVEN_NO_ORDINARY_DAMAGE_EFFECT" if s == "ITEM_GRACIDEA" else "UNSUPPORTED_DAMAGE_RELEVANT")

    def test_unreviewed_or_changed_form_rows_fail(self):
        scanned = audit.scan_form_change_items(self.root)
        missing = self.reviewed()
        del missing["ITEM_GRACIDEA"]
        with self.assertRaises(audit.AuditError):
            audit.check_form_change_identities(scanned, missing, lambda s: "UNSUPPORTED_DAMAGE_RELEVANT")
        changed = self.reviewed()
        changed["ITEM_RUSTED_SWORD"]["methods"] = ["FORM_CHANGE_ITEM_HOLD"]
        with self.assertRaises(audit.AuditError):
            audit.check_form_change_identities(scanned, changed, lambda s: "UNSUPPORTED_DAMAGE_RELEVANT")

    def test_committed_review_keeps_rusted_items_non_neutral(self):
        decisions = json.loads((HERE / "decisions.json").read_text())
        for symbol in ("ITEM_RUSTED_SWORD", "ITEM_RUSTED_SHIELD"):
            self.assertEqual(decisions["identity_exceptions"][symbol]["category"], "UNSUPPORTED_DAMAGE_RELEVANT")
            self.assertIn("FORM_CHANGE_BEGIN_BATTLE", decisions["form_change_item_identities"][symbol]["methods"])


class ReviewedArtifactTests(unittest.TestCase):
    """Structural checks on the committed reviewed artifacts that need no upstream checkout."""

    def setUp(self):
        self.decisions = json.loads((HERE / "decisions.json").read_text())
        self.rules = json.loads((HERE / "context_rules.json").read_text())

    def test_every_family_has_a_valid_category_group_and_rationale(self):
        for he, family in self.decisions["families"].items():
            self.assertIn(family["category"], audit.CATEGORIES, he)
            self.assertTrue(family["group"], he)
            self.assertTrue(family["rationale"], he)
        self.assertNotIn("MODELLED_EQUIVALENT", {f["category"] for f in self.decisions["families"].values()})

    def test_context_rules_only_refine_unsupported_groups(self):
        groups = {}
        for family in self.decisions["families"].values():
            groups.setdefault(family["group"], set()).add(family["category"])
        for group in self.rules["families"]:
            self.assertTrue(groups[group].issubset({"UNSUPPORTED_DAMAGE_RELEVANT", "MODELLED_HNS_SPECIFIC"}), group)

    def test_display_name_uses_the_pinned_name(self):
        self.assertEqual(audit.display_name("SILK SCARF"), "Silk Scarf")
        self.assertEqual(audit.display_name("KING'S ROCK"), "King's Rock")
        self.assertEqual(audit.display_name("HP UP"), "HP Up")
        self.assertEqual(audit.display_name(None), "None")


if __name__ == "__main__":
    unittest.main()
