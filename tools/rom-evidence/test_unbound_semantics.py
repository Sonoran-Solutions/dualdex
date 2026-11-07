"""Invented bytes only; no ROM, core, save or screenshot needed."""
import hashlib
import json
from pathlib import Path
import struct
import unittest

import unbound_semantics as s

BASE = s.ROM_BASE
EVIDENCE = Path(s.__file__).parent / 'evidence/unbound-party-semantics.json'


def bl(source, target):
    """Thumb BL halfword pair at `source` calling `target`."""
    delta = (target - (source + 4)) & 0x7FFFFF
    return struct.pack('<HH', 0xF000 | (delta >> 12), 0xF800 | ((delta >> 1) & 0x7FF))


def rom_with_base_stats(rows):
    """Smallest invented ROM image holding the base-stats pointer and selected rows."""
    rom = bytearray(s.BASE_STATS + 1300 * s.BASE_STRIDE)
    struct.pack_into('<I', rom, s.BASE_STATS_POINTER, BASE + s.BASE_STATS)
    for species, triple in rows.items():
        for off, value in zip(s.ABILITY_OFFSETS, triple):
            rom[s.BASE_STATS + species * s.BASE_STRIDE + off] = value
    return rom


def invented_record(pid, otid, species, iv_word):
    return dict(pid=pid, otid=otid, fixed_raw_gaem=dict(species_word=species, iv_word=iv_word))


class AbilityRuleTest(unittest.TestCase):
    def test_hidden_bit_wins_only_when_species_has_hidden_ability(self):
        self.assertEqual(s.predict_ability(0, 1 << 31, (11, 6, 2)), dict(selector='hidden', ability=2))
        # Hidden flag with no hidden ability falls through to the personality rule.
        self.assertEqual(s.predict_ability(1, 1 << 31, (11, 6, 0)), dict(selector='ability2', ability=6))
        self.assertEqual(s.predict_ability(0, 1 << 31, (11, 6, 0)), dict(selector='ability1', ability=11))

    def test_personality_bit0_selects_ability2_unless_empty(self):
        self.assertEqual(s.predict_ability(0xB8BF03D3, 0, (11, 120, 6))['ability'], 120)
        self.assertEqual(s.predict_ability(0xB8BF03D2, 0x7FFFFFFF, (11, 120, 6))['ability'], 11)
        self.assertEqual(s.predict_ability(3, 0, (66, 0, 22)), dict(selector='ability1', ability=66))

    def test_ability_triple_reads_stride_28_offsets_and_checks_pointer_and_domain(self):
        rom = rom_with_base_stats({646: (11, 120, 6)})
        self.assertEqual(s.ability_triple(rom, 646), (11, 120, 6))
        for bad in (0, s.SPECIES_COUNT, -1):
            with self.assertRaises(ValueError):
                s.ability_triple(rom, bad)
        struct.pack_into('<I', rom, s.BASE_STATS_POINTER, BASE)
        with self.assertRaises(ValueError):
            s.ability_triple(rom, 646)


class ShinyNatureChecksumTest(unittest.TestCase):
    def test_shiny_threshold_is_exclusive_sixteen_not_vanilla_eight(self):
        otid = 0x8E2AD05C
        tsv = (otid & 0xFFFF) ^ (otid >> 16)
        for value, shiny in ((0, True), (7, True), (8, True), (15, True), (16, False), (2596, False)):
            pid = (0x1234 ^ tsv ^ value) << 16 | 0x1234
            self.assertEqual(s.shiny_value(pid, otid), value)
            self.assertIs(s.is_shiny(pid, otid), shiny)
            self.assertIs(s.is_shiny(pid, otid, threshold=8), value < 8)

    def test_nature_and_raw_checksum(self):
        self.assertEqual(s.nature(0x97FBC982), 0x97FBC982 % 25)
        secure = struct.pack('<24H', *([0xFFFF] * 2 + [0] * 22))
        self.assertEqual(s.raw_checksum(secure), 0xFFFE)
        with self.assertRaises(ValueError):
            s.raw_checksum(bytes(47))


class StaticInspectionTest(unittest.TestCase):
    def setUp(self):
        self.rom = bytearray(0x400)
        struct.pack_into('<HH', self.rom, 0x10, 0x4900, 0x4708)  # ldr r1,[pc,#0]; bx r1
        struct.pack_into('<I', self.rom, 0x14, 0x08000101)

    def test_ldr_literal_and_reference_census(self):
        self.assertEqual(s.ldr_literal(self.rom, BASE + 0x10), 0x08000101)
        with self.assertRaises(ValueError):
            s.ldr_literal(self.rom, BASE + 0x12)
        self.rom[0x40:0x44] = bl(BASE + 0x40, BASE + 0x180)
        self.rom[0x300:0x304] = bl(BASE + 0x300, BASE + 0x180)  # backward call
        struct.pack_into('<I', self.rom, 0x200, BASE + 0x181)
        struct.pack_into('<I', self.rom, 0x222, BASE + 0x181)      # unaligned: ignored
        calls, lits = s.references(bytes(self.rom), BASE + 0x180)
        self.assertEqual(calls, [BASE + 0x40, BASE + 0x300])
        self.assertEqual(lits, [BASE + 0x200])

    def test_callasm_sites_need_the_opcode_byte_at_any_alignment(self):
        rom = bytearray(64)
        rom[5] = 0x23
        struct.pack_into('<I', rom, 6, 0x08825A31)
        struct.pack_into('<I', rom, 21, 0x08825A31)    # preceded by 0x00: not callasm
        rom[32] = 0x23
        struct.pack_into('<I', rom, 33, 0x08825A31)
        self.assertEqual(s.callasm_sites(bytes(rom), 0x08825A30), [BASE + 5, BASE + 32])

    def test_inspection_refuses_changed_spans_and_literals(self):
        rom = bytearray(s.rom_offset(s.BOX_POINTER_TABLE) + 4 * s.BOX_COUNT)
        struct.pack_into('<HH', rom, s.rom_offset(s.SHINY_CMP), 0x2A0F, 0xD801)
        rom[0x10:0x18] = self.rom[0x10:0x18]
        sha = hashlib.sha256(bytes(rom[0x10:0x18])).hexdigest()
        spans = {'t': ((BASE + 0x10, BASE + 0x18, sha, 'invented'),)}
        lits = {BASE + 0x10: (0x08000101, 'invented hook')}
        out = s.inspect_semantics(bytes(rom), spans, lits)
        self.assertEqual(out['shiny_threshold_exclusive'], 16)
        self.assertEqual(out['literal_loads'][0]['value'], '0x8000101')
        self.assertEqual(out['box_storage']['record_bytes'], 58)
        with self.assertRaises(ValueError):
            s.inspect_semantics(bytes(rom), spans, {BASE + 0x10: (0x08000105, 'wrong')})
        rom[0x16] ^= 1
        with self.assertRaises(ValueError):
            s.inspect_semantics(bytes(rom), spans, lits)
        rom[0x16] ^= 1
        struct.pack_into('<H', rom, s.rom_offset(s.SHINY_CMP), 0x2B07)  # cmp r3,#7
        with self.assertRaises(ValueError):
            s.inspect_semantics(bytes(rom), spans, lits)


class BattleCheckTest(unittest.TestCase):
    def report(self, enemy_ability):
        player = invented_record(0x1AAE814C, 0x8E2AD05C, 186, 0xBFFFFFFF)
        enemy = invented_record(0xB8BF03D3, 0x12345678, 646, 0x0FFFFFFF)
        battlers = [dict(side='player', party_index=1, fields=dict(personality=player['pid'], ability_byte=2)),
                    dict(side='enemy', party_index=0, fields=dict(personality=enemy['pid'],
                                                                   ability_byte=enemy_ability))]
        obs = dict(label='turn', parties=dict(player=dict(slots=[{}, player])),
                   battle=dict(battlers=battlers, enemy_box_nonzero_slots=[dict(enemy, slot=0)]))
        return dict(observations=[obs, dict(obs, label='later')])

    def test_compares_each_distinct_battler_with_rule_and_vanilla_reading(self):
        rom = rom_with_base_stats({186: (11, 6, 2), 646: (11, 120, 6)})
        rows = s.battle_check(self.report(120), rom)
        self.assertEqual([(r['side'], r['predicted_selector'], r['matches'], r['observations'])
                          for r in rows], [('player', 'hidden', True, 2), ('enemy', 'ability2', True, 2)])
        self.assertEqual([r['vanilla_bit31_slot_ability'] for r in rows], [6, 11])
        self.assertFalse(s.battle_check(self.report(11), rom)[1]['matches'])

    def test_refuses_personality_mismatch_and_changing_rows(self):
        rom = rom_with_base_stats({186: (11, 6, 2), 646: (11, 120, 6)})
        report = self.report(120)
        report['observations'][1] = json.loads(json.dumps(report['observations'][1]))
        report['observations'][1]['battle']['battlers'][1]['fields']['ability_byte'] = 11
        with self.assertRaises(ValueError):
            s.battle_check(report, rom)
        report = self.report(120)
        report['observations'][0]['battle']['battlers'][0]['fields']['personality'] ^= 1
        with self.assertRaises(ValueError):
            s.battle_check(report, rom)


class CommittedEvidenceTest(unittest.TestCase):
    def test_committed_semantics_evidence_is_self_consistent_and_numeric(self):
        evidence = json.loads(EVIDENCE.read_text())
        self.assertEqual(evidence['rom_sha256'], s.ROM_SHA256)
        self.assertEqual(evidence['static']['shiny_threshold_exclusive'], 16)
        for row in evidence['party'] + evidence['battle']:
            pid, iv = row['pid_bit0'], row['iv_bit31'] << 31
            pred = s.predict_ability(pid, iv, tuple(row['base_ability_triple']))
            self.assertEqual((pred['selector'], pred['ability']),
                             (row['predicted_selector'], row['predicted_ability']))
        for row in evidence['party']:
            self.assertEqual(row['predicted_shiny'], row['shiny_value'] < 16)
            self.assertTrue(row['shiny_matches_ui'] and row['predicted_default_name_equals_ui'])
        for row in evidence['battle']:
            self.assertEqual(row['matches'], row['predicted_ability'] == row['battle_ability_byte'])
            self.assertTrue(row['matches'])
        # Discriminating cases: personality bit 0 -> ability2, bit 31 -> hidden, 15 -> shiny.
        self.assertIn('ability2', [r['predicted_selector'] for r in evidence['battle']])
        self.assertIn(15, [r['shiny_value'] for r in evidence['party'] if r['ui_shiny_star']])
        self.assertEqual({q['status'] for q in evidence['verdicts'].values()} - {'VERIFIED', 'NOT_VERIFIED'},
                         set())
        # Numbers, hashes, booleans and fixed descriptions only: no nickname/OT/game-text keys.
        text = json.dumps(evidence)
        for forbidden in ('nickname', 'ot_name', 'species_name', 'ui_transcription', 'ability_name'):
            self.assertNotIn(forbidden, text)


if __name__ == '__main__':
    unittest.main()
