import copy
import json
import unittest
from generate_closure import ROOT, HERE, rows, render


class ClosureTest(unittest.TestCase):
    def setUp(self):
        self.doc = json.loads((HERE / 'decisions.json').read_text())
        self.abilities = rows(ROOT / 'tools/hns-abilities/ability_inventory.tsv')
        self.items = rows(ROOT / 'tools/hns-items/item_inventory.tsv')
        self.decisions = json.loads((ROOT / 'tools/hns-items/decisions.json').read_text())

    def check(self):
        return render(self.doc, self.abilities, self.items, self.decisions)

    def test_current_remainder_is_complete(self):
        self.check()

    def test_new_unsupported_ability_fails_closed(self):
        self.abilities[0]['proposed_category'] = 'UNSUPPORTED_DAMAGE_RELEVANT'
        with self.assertRaises(ValueError): self.check()

    def test_unclassified_identity_cannot_inherit_a_none_family(self):
        self.items[0]['category'] = 'UNCLASSIFIED'
        with self.assertRaises(ValueError): self.check()

    def test_new_family_and_removed_assignment_fail(self):
        self.decisions['families']['HOLD_EFFECT_NEW'] = {'category': 'UNSUPPORTED_DAMAGE_RELEVANT'}
        with self.assertRaises(ValueError): self.check()
        del self.decisions['families']['HOLD_EFFECT_NEW']
        del self.doc['item_identities']['ITEM_ENIGMA_BERRY_E_READER']
        with self.assertRaises(ValueError): self.check()

    def test_invalid_tier_or_empty_reason_fails(self):
        original = copy.deepcopy(self.doc)
        self.doc['groups']['enigma']['tier'] = 'SILENT_ESTIMATE'
        with self.assertRaises(ValueError): self.check()
        self.doc = original
        self.doc['groups']['enigma']['reason'] = ''
        with self.assertRaises(ValueError): self.check()
