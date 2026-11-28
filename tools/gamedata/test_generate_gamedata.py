#!/usr/bin/env python3
"""Host-only tests for the gamedata/gen3 -> gamedata_configs_gen.h generator (no ROM, no C build)."""
import contextlib
import io
import json
from pathlib import Path
import shutil
import tempfile
import unittest

import generate_gamedata as g


class GamedataFixture(unittest.TestCase):
    """A writable copy of the committed configs that each test mutates."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name) / 'gen3'
        shutil.copytree(g.CONFIG_DIR, self.dir)

    def edit(self, config_id, change):
        path = self.dir / f'{config_id}.json'
        data = json.loads(path.read_text())
        change(data)
        path.write_text(json.dumps(data, indent=2))

    def set_field(self, config_id, name, value):
        self.edit(config_id, lambda data: data['fields'].__setitem__(name, value))

    def generate(self):
        return g.generate(self.dir)

    def resolved(self, config_id):
        facts = g.CFacts()
        configs = g.load_config_dir(self.dir)
        return g.resolve(facts, configs, config_id)

    def assert_rejected(self, pattern):
        with self.assertRaisesRegex(g.GamedataError, pattern):
            self.generate()


class CommittedOutputTest(GamedataFixture):
    def test_committed_header_is_current(self):
        self.assertEqual(g.generate(), g.OUTPUT.read_text(encoding='utf-8'))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(g.main(['--check']), 0)

    def test_every_game_is_generated_once_in_enum_order(self):
        text = self.generate()
        order = [name for name in g.CFacts().enums['GbaGameId'] if name != 'GAME_UNKNOWN']
        positions = [text.index(f'.game_id = {name},') for name in order]
        self.assertEqual(positions, sorted(positions))
        self.assertEqual(text.count('static const GameMemoryConfig CONFIG_'), len(order))

    def test_output_is_deterministic_and_independent_of_key_order(self):
        first = self.generate()
        self.assertEqual(first, self.generate())
        for config_id in ('heart_and_soul_2_0_5', 'unbound'):
            self.edit(config_id, lambda data: data.__setitem__(
                'fields', dict(reversed(list(data['fields'].items())))))
        self.assertEqual(first, self.generate())

    def test_audit_scraper_shape_is_preserved(self):
        # tools/calc-goldens/audit_vanilla_layout.py reads `.member = <int>,` lines from each
        # `static const GameMemoryConfig CONFIG_<NAME> = {...\n};` block.
        import re
        text = self.generate()
        body = re.search(r'static const GameMemoryConfig CONFIG_EMERALD\s*=\s*\{(.*?)\n\};', text, re.S).group(1)
        fields = {m.group(1): int(m.group(2), 0)
                  for m in re.finditer(r'\.([a-z0-9_]+)\s*=\s*(0x[0-9A-Fa-f]+|\d+)\s*,', body)}
        self.assertEqual(fields['player_party_offset'], 0x244EC)
        self.assertEqual(fields['battle_mons_offset'], 0x24064)


class InheritanceTest(GamedataFixture):
    def test_child_inherits_and_overrides(self):
        unbound = self.resolved('unbound')
        self.assertEqual(unbound.chain, ['unbound', 'firered_rev0'])
        self.assertEqual(unbound.get('player_party_offset'), 0x24284)      # inherited
        self.assertEqual(unbound.get('battle_mons_size'), 88)              # inherited
        self.assertEqual(unbound.get('battle_mons_offset'), 0x23BE4)       # overridden ({"gba": ...})
        self.assertEqual(unbound.game_id, 'GAME_UNBOUND')                  # never inherited

    def test_null_removes_an_inherited_member(self):
        ghost = self.resolved('ghost_grey')
        self.assertIsNone(ghost.get('battle_mons_offset'))
        self.assertNotIn('.battle_mons_offset', self.generate().split('CONFIG_GHOST_GREY = {')[1].split('};')[0])

    def test_null_cannot_hide_an_unknown_member(self):
        self.set_field('ghost_grey', 'nonexistent_member', None)
        self.assert_rejected(r'ghost_grey\.json: fields\.nonexistent_member: unknown GameMemoryConfig member')
        self.set_field('ghost_grey', 'nonexistent_member', 5)
        self.assert_rejected(r'unknown GameMemoryConfig member')

    def test_null_cannot_hide_an_unknown_member_in_a_base(self):
        self.set_field('firered_rev0', 'nonexistent_member', None)
        self.assert_rejected(r'firered_rev0\.json: fields\.nonexistent_member: unknown GameMemoryConfig member')

    def test_null_cannot_hide_identity_members(self):
        self.set_field('ghost_grey', 'game_name', None)
        self.assert_rejected(r'set through the top-level gameId/gameName keys')

    def test_base_changes_propagate_through_multi_level_chain(self):
        self.edit('unbound', lambda data: data.__setitem__('base', 'leafgreen'))
        self.set_field('firered_rev0', 'player_party_offset', {'ewram': '0x24288'})
        unbound = self.resolved('unbound')
        self.assertEqual(unbound.chain, ['unbound', 'leafgreen', 'firered_rev0'])
        self.assertEqual(unbound.get('player_party_offset'), 0x24288)
        self.assertEqual(unbound.values['player_party_policy'].text, 'PARTY_DISCOVERY_HEURISTIC')

    def test_missing_required_field_without_base_is_refused(self):
        self.edit('ruby', lambda data: data['fields'].pop('player_party_offset'))
        self.assert_rejected(r"ruby\.json: missing required field 'player_party_offset'")

    def test_required_field_satisfied_through_base(self):
        self.assertNotIn('player_party_offset', json.loads((self.dir / 'leafgreen.json').read_text())['fields'])
        self.assertEqual(self.resolved('leafgreen').get('player_party_offset'), 0x24284)

    def test_null_cannot_remove_a_required_field(self):
        self.set_field('leafgreen', 'enemy_party_offset', None)
        self.assert_rejected(r"leafgreen\.json: missing required field 'enemy_party_offset'")

    def test_circular_base_is_refused(self):
        self.edit('firered_rev0', lambda data: data.__setitem__('base', 'unbound'))
        self.assert_rejected(r'circular base chain: (firered_rev0 -> unbound -> firered_rev0|unbound -> firered_rev0 -> unbound)')

    def test_self_base_is_refused(self):
        self.edit('ruby', lambda data: data.__setitem__('base', 'ruby'))
        self.assert_rejected(r'circular base chain: ruby -> ruby')

    def test_missing_base_is_refused(self):
        self.edit('unbound', lambda data: data.__setitem__('base', 'firered_rev9'))
        self.assert_rejected(r"base 'firered_rev9' does not exist")


class StrictSchemaTest(GamedataFixture):
    def test_unknown_top_level_key(self):
        self.edit('emerald', lambda data: data.__setitem__('offsets', {}))
        self.assert_rejected(r'emerald\.json: unknown top-level key\(s\): offsets')

    def test_unknown_member(self):
        self.set_field('emerald', 'player_party_ofset', {'ewram': '0x244EC'})
        self.assert_rejected(r'fields\.player_party_ofset: unknown GameMemoryConfig member')

    def test_unknown_key_in_value_object(self):
        self.set_field('emerald', 'battle_mons_size', {'value': 88, 'comment': 'x'})
        self.assert_rejected(r'unknown key\(s\) in value object: comment')

    def test_identity_is_top_level_only(self):
        self.set_field('emerald', 'game_id', 'GAME_EMERALD')
        self.assert_rejected(r'set through the top-level gameId/gameName keys')

    def test_schema_version_and_id(self):
        self.edit('emerald', lambda data: data.__setitem__('schemaVersion', 2))
        self.assert_rejected(r'schemaVersion must be 1')
        self.edit('emerald', lambda data: data.update(schemaVersion=1, id='emerald_us'))
        self.assert_rejected(r"id must equal the file stem 'emerald'")

    def test_duplicate_json_key(self):
        path = self.dir / 'ruby.json'
        path.write_text(path.read_text().replace('"gameName"', '"cSymbol": "CONFIG_X",\n  "gameName"', 1))
        self.assert_rejected(r"ruby\.json: duplicate key 'cSymbol'")

    def test_duplicate_game_and_symbol(self):
        self.edit('sapphire', lambda data: data.__setitem__('gameId', 'GAME_RUBY'))
        self.assert_rejected(r'gameId GAME_RUBY is already used by ruby\.json')
        self.edit('sapphire', lambda data: data.update(gameId='GAME_SAPPHIRE', cSymbol='CONFIG_RUBY'))
        self.assert_rejected(r'cSymbol CONFIG_RUBY is already used')

    def test_every_game_id_needs_a_config_and_unknown_is_refused(self):
        (self.dir / 'sapphire.json').unlink()
        self.assert_rejected(r'without a gamedata config: GAME_SAPPHIRE')
        shutil.copy(g.CONFIG_DIR / 'sapphire.json', self.dir / 'sapphire.json')
        self.edit('sapphire', lambda data: data.__setitem__('gameId', 'GAME_UNKNOWN'))
        self.assert_rejected(r'other than GAME_UNKNOWN')

    def test_evidence_is_mandatory(self):
        self.edit('ruby', lambda data: data.__setitem__('evidence', []))
        self.assert_rejected(r'evidence: must be a non-empty list')

    def test_comment_breaking_text_is_refused(self):
        self.edit('ruby', lambda data: data.__setitem__('notes', ['ends the comment */ early']))
        self.assert_rejected(r'must not contain')
        self.edit('ruby', lambda data: data.__setitem__('notes', ['non-ascii — dash']))
        self.assert_rejected(r'printable-ASCII')


class AddressAndRangeTest(GamedataFixture):
    def test_out_of_range_ewram_offset(self):
        self.set_field('emerald', 'battle_mons_offset', {'ewram': '0x40000'})
        self.assert_rejected(r'EWRAM offset 0x40000 is outside EWRAM')

    def test_absolute_ewram_address_is_converted_and_checked(self):
        self.set_field('emerald', 'battle_mons_offset', {'gba': '0x02024064'})
        self.assertEqual(self.resolved('emerald').get('battle_mons_offset'), 0x24064)
        self.set_field('emerald', 'battle_mons_offset', {'gba': '0x03024064'})
        self.assert_rejected(r'0x03024064 is not an EWRAM address')

    def test_bare_number_is_refused_for_address_members(self):
        for bare in ('0x244EC', 0x244EC):
            self.set_field('emerald', 'player_party_offset', bare)
            self.assert_rejected(r'needs an explicit \{"ewram": \.\.\.\} or \{"gba": \.\.\.\} tag')

    def test_iwram_member_refuses_ewram_address(self):
        self.set_field('unbound', 'main_struct_gba_address', {'gba': '0x020030F0'})
        self.assert_rejected(r'0x020030F0 is outside the allowed region\(s\) IWRAM')
        self.set_field('unbound', 'main_struct_gba_address', {'ewram': '0x30F0'})
        self.assert_rejected(r'needs an explicit \{"gba": \.\.\.\} or \{"iwram": \.\.\.\} tag')
        self.set_field('unbound', 'main_struct_gba_address', {'iwram': '0x30F0'})
        self.assertEqual(self.resolved('unbound').get('main_struct_gba_address'), 0x030030F0)
        self.set_field('unbound', 'main_struct_gba_address', {'iwram': '0x8000'})
        self.assert_rejected(r'iwram offset 0x8000 is outside IWRAM')

    def test_rom_pointer_must_be_in_rom(self):
        self.set_field('unbound', 'action_selection_func_ptr', {'gba': '0x0A000001'})
        self.assert_rejected(r'0x0A000001 is outside the allowed region\(s\) ROM')

    def test_pointer_read_must_fit_inside_its_region(self):
        # A 4-byte pointer whose first byte is the last IWRAM byte reads past the region.
        self.set_field('unbound', 'save_block1_ptr_gba_address', {'gba': '0x03007FFF'})
        self.assert_rejected(r'0x03007FFF \+ 4 bytes runs past the end of IWRAM')
        self.set_field('unbound', 'save_block1_ptr_gba_address', {'gba': '0x03007FFC'})
        self.assertEqual(self.resolved('unbound').get('save_block1_ptr_gba_address'), 0x03007FFC)
        self.set_field('unbound', 'save_block3_ptr_gba_address', {'gba': '0x03007FFD'})
        self.assert_rejected(r'save_block3_ptr_gba_address: 0x03007FFD \+ 4 bytes runs past the end of IWRAM')

    def test_multi_byte_span_must_fit_inside_ewram(self):
        self.set_field('unbound', 'battle_current_move_gba_address', {'gba': '0x0203FFFF'})
        self.assert_rejected(r'0x0203FFFF \+ 2 bytes runs past the end of EWRAM')

    def test_save_block3_base_challenge_settings_span_must_fit_inside_ewram(self):
        # SaveBlock3 challengeSettings sits at offset 16 and spans 32 bytes (48 bytes total).
        # 0x0203FFD1 + 48 = 0x02040001 runs 1 byte past EWRAM (0x02040000) and cannot be waived.
        self.set_field('heart_and_soul_2_0_5', 'save_block3_base_gba_address', {'gba': '0x0203FFD1'})
        self.assert_rejected(r'save_block3_base_gba_address: 0x0203FFD1 \+ 48 bytes runs past the end of EWRAM')
        self.edit('heart_and_soul_2_0_5', lambda data: data.__setitem__('waivers', {'ewram-extent': 'attempt'}))
        self.assert_rejected(r"unknown waiver 'ewram-extent'")
        shutil.copy(g.CONFIG_DIR / 'heart_and_soul_2_0_5.json', self.dir / 'heart_and_soul_2_0_5.json')
        # Exact boundary 0x0203FFD0 + 48 = 0x02040000 fits wholly inside EWRAM.
        self.set_field('heart_and_soul_2_0_5', 'save_block3_base_gba_address', {'ewram': '0x3FFD0'})
        self.assertEqual(self.resolved('heart_and_soul_2_0_5').get('save_block3_base_gba_address'), 0x0203FFD0)

    def test_ewram_offset_read_width_must_fit_and_is_not_waivable(self):
        # 4-byte battle_type_flags_offset starting at final EWRAM byte (0x0203FFFF) must fail,
        # even when a waiver is attempted.
        self.set_field('unbound', 'battle_type_flags_offset', {'gba': '0x0203FFFF'})
        self.assert_rejected(r'EWRAM offset 0x3FFFF \+ 4 bytes runs past the end of EWRAM')
        self.edit('unbound', lambda data: data.__setitem__('waivers', {'ewram-extent': 'attempt'}))
        self.assert_rejected(r"unknown waiver 'ewram-extent'")
        self.edit('unbound', lambda data: data.pop('waivers'))
        self.set_field('unbound', 'battle_type_flags_offset', {'ewram': '0x3FFFC'})
        self.assertEqual(self.resolved('unbound').get('battle_type_flags_offset'), 0x3FFFC)
        # Other multi-byte EWRAM offset fields must also fit their read widths.
        for field, bad_off in (('battler_party_indexes_offset', '0x3FFF9'),
                               ('battler_positions_offset', '0x3FFFD'),
                               ('field_statuses_offset', '0x3FFFD'),
                               ('battle_weather_offset', '0x3FFFF'),
                               ('side_statuses_offset', '0x3FFFD'),
                               ('battle_struct_ptr_offset', '0x3FFFD'),
                               ('player_party_offset', '0x3FDA9')):
            with self.subTest(field=field):
                shutil.copy(g.CONFIG_DIR / 'heart_and_soul_2_0_5.json', self.dir / 'heart_and_soul_2_0_5.json')
                self.set_field('heart_and_soul_2_0_5', field, {'ewram': bad_off})
                self.assert_rejected(r'runs past the end of EWRAM')

    def test_main_struct_member_reads_must_fit_inside_iwram(self):
        for name, offset in (('main_in_battle_byte_offset', 0x439), ('battle_main_cb1_offset', 0x439)):
            with self.subTest(member=name):
                config = 'heart_and_soul_2_0_5'
                self.set_field(config, 'main_struct_gba_address', {'gba': '0x03007BF0'})
                self.set_field(config, 'main_in_battle_byte_offset', 0x10)
                self.set_field(config, 'battle_main_cb1_offset', 0x10)
                self.set_field(config, name, offset)
                self.assert_rejected(r'main_struct_gba_address 0x03007BF0 \+ ' + name + r' 0x439 \+ \d bytes '
                                     r'runs past the end of IWRAM')
        # The last byte of IWRAM is still readable for the one-byte inBattle member.
        config = 'heart_and_soul_2_0_5'
        self.set_field(config, 'battle_main_cb1_offset', None)
        self.set_field(config, 'main_struct_gba_address', {'gba': '0x03007BC7'})
        self.set_field(config, 'main_in_battle_byte_offset', 0x438)
        self.assertEqual(self.resolved(config).get('main_in_battle_byte_offset'), 0x438)

    def test_zero_address_means_null(self):
        self.set_field('emerald', 'battle_mons_offset', {'ewram': '0x0'})
        self.assert_rejected(r'use null to leave it undeclared')

    def test_malformed_numbers(self):
        for bad in ('40', '0x', '0xZZ', ' 0x18', -1, 1.5, True, '0X18'):
            with self.subTest(value=bad):
                self.set_field('emerald', 'battle_mons_stat_stages_offset', bad)
                self.assert_rejected(r'battle_mons_stat_stages_offset: (expected a JSON integer|must not be negative)')

    def test_type_width(self):
        self.set_field('unbound', 'location_max_map_group', 256)
        self.assert_rejected(r'value 0x100 does not fit uint8_t')

    def test_enum_and_bool_values(self):
        self.set_field('emerald', 'player_party_policy', 'PARTY_DISCOVERY_GUESS')
        self.assert_rejected(r'expected one of PARTY_DISCOVERY_HEURISTIC, PARTY_DISCOVERY_AUTHORITATIVE_STATIC')
        self.set_field('emerald', 'player_party_policy', 'PARTY_DISCOVERY_HEURISTIC')
        self.set_field('emerald', 'has_evs', 1)
        self.assert_rejected(r'has_evs: expected true or false')

    def test_symbols(self):
        self.set_field('unbound', 'battle_mons_ability_offset', {'symbol': 'HNS_NOT_A_MACRO'})
        self.assert_rejected(r"unknown symbol 'HNS_NOT_A_MACRO'")
        # An absolute-address macro cannot stand in for an EWRAM-relative member.
        self.set_field('unbound', 'battle_mons_ability_offset', None)
        self.set_field('unbound', 'battle_outcome_offset', {'symbol': 'HNS_LIVE_GCURRENTMOVE_GBA_ADDRESS'})
        self.assert_rejected(r'EWRAM offset 0x20003A0 is outside EWRAM')

    def test_symbol_is_emitted_verbatim_and_header_included(self):
        text = self.generate()
        self.assertIn('.battle_mons_ability_offset = HNS_BATTLE_POKEMON_ABILITY_OFFSET,', text)
        self.assertIn('#include "hns_live_battle_layout_gen.h"', text)

    def test_macro_evaluator(self):
        self.assertEqual(g.evaluate_c_integer('(1u << 10)'), 1024)
        self.assertEqual(g.evaluate_c_integer('0x03002F74u'), 0x03002F74)
        for bad in ('sizeof(int)', '1 - 2', 'FOO'):
            with self.subTest(expression=bad), self.assertRaises(g.GamedataError):
                g.evaluate_c_integer(bad)


class ConsistencyTest(GamedataFixture):
    def test_battle_mons_member_outside_struct(self):
        self.set_field('emerald', 'battle_mons_hp_offset', 87)
        self.assert_rejected(r'\[battle-mons-member-extent\] battle_mons_hp_offset 0x57 \+ 2 does not fit battle_mons_size 88')

    def test_battle_mons_needs_size(self):
        self.set_field('emerald', 'battle_mons_size', None)
        self.assert_rejected(r'\[battle-mons-declaration\]')

    def test_volatile_bit_outside_struct(self):
        self.set_field('heart_and_soul_2_0_5', 'battle_mons_volatile_endured_bit', 416)
        self.assert_rejected(r'\[struct-bit-extent\] battle_mons_volatile_endured_bit 416')

    def test_save_block1_member_outside_struct(self):
        self.set_field('unbound', 'save_block1_location_offset', '0x3D68')
        self.assert_rejected(r'\[save-block1-member-extent\] save_block1_location_offset')

    def test_save_block1_warp_read_width_must_fit_and_is_not_waivable(self):
        # An 8-byte SaveBlock1 warp field starting at offset 4 of a 5-byte SaveBlock1 must fail,
        # even when a waiver is attempted.
        for field in ('save_block1_location_offset', 'save_block1_escape_warp_offset'):
            with self.subTest(field=field):
                shutil.copy(g.CONFIG_DIR / 'unbound.json', self.dir / 'unbound.json')
                self.set_field('unbound', 'save_block1_size', 5)
                self.set_field('unbound', 'save_block1_pos_offset', None)
                self.set_field('unbound', 'save_block1_location_offset', None)
                self.set_field('unbound', 'save_block1_escape_warp_offset', None)
                self.set_field('unbound', field, 4)
                self.assert_rejected(r'\[save-block1-member-extent\] ' + field + r' 0x4 \+ 8 does not fit save_block1_size 5')
                self.edit('unbound', lambda data: data.__setitem__('waivers', {'save-block1-member-extent': 'attempt'}))
                self.assert_rejected(r"unknown waiver 'save-block1-member-extent'")

    def test_physical_bounds_cannot_be_waived(self):
        self.set_field('unbound', 'save_block1_base_gba_address', {'gba': '0x0203FFF0'})
        self.set_field('unbound', 'save_block1_aslr_range', 4)
        self.set_field('unbound', 'save_block1_size', 32)
        self.edit('unbound', lambda data: data.__setitem__('waivers', {'save-block1-window': 'attempt'}))
        self.assert_rejected(r'SaveBlock1 base \+ ASLR window \+ size runs past the end of EWRAM')

    def test_save_block1_window_must_be_complete(self):
        self.set_field('unbound', 'save_block1_aslr_range', None)
        self.assert_rejected(r'\[save-block1-window\]')

    def test_party_arrays_must_not_overlap(self):
        self.set_field('emerald', 'enemy_party_offset', {'ewram': '0x24600'})
        self.assert_rejected(r'\[party-overlap\]')

    def test_waiver_is_required_explicit_and_not_stale(self):
        self.edit('ruby', lambda data: data.pop('waivers'))
        self.assert_rejected(r'ruby\.json: \[party-count-inside-party\] enemy_party_count_offset 0x246E4')
        shutil.copy(g.CONFIG_DIR / 'ruby.json', self.dir / 'ruby.json')
        self.edit('emerald', lambda data: data.__setitem__('waivers', {'party-overlap': 'not needed'}))
        self.assert_rejected(r'emerald\.json: waiver\(s\) for checks that pass; remove them: party-overlap')
        self.edit('emerald', lambda data: data.__setitem__('waivers', {'everything': 'x'}))
        self.assert_rejected(r"unknown waiver 'everything'")

    def test_waivers_are_not_inherited_and_are_emitted(self):
        sapphire_block = self.generate().split('CONFIG_SAPPHIRE - gamedata')[1].split('};')[0]
        self.assertIn('party-count-inside-party: Legacy unverified value', sapphire_block)
        self.edit('sapphire', lambda data: data.pop('waivers'))
        self.assert_rejected(r'sapphire\.json: \[party-count-inside-party\]')


class PcStorageTest(GamedataFixture):
    def test_verified_pc_layouts_and_explicit_opt_outs(self):
        for game, pointer, base in (
            ('firered_rev0', 0x03005010, 0x02029314),
            ('emerald', 0x03005D94, 0x02029808),
        ):
            with self.subTest(game=game):
                cfg = self.resolved(game)
                self.assertEqual(tuple(cfg.get(name) for name in g.PC_STORAGE_FIELDS),
                                 (pointer, base, 128, 0x83D0, 0, 4, 14, 30, 80))
        for game in ('unbound', 'ghost_grey', 'radical_red', 'leafgreen', 'heart_and_soul_2_0_5'):
            with self.subTest(game=game):
                self.assertIsNone(self.resolved(game).get('pokemon_storage_ptr_gba_address'))

    def test_pc_layout_cannot_be_inherited_implicitly(self):
        self.edit('ghost_grey', lambda cfg: cfg['fields'].pop('pokemon_storage_ptr_gba_address'))
        self.assert_rejected('PC storage must be explicitly declared')

    def test_pc_pointer_must_fit_four_bytes_in_iwram(self):
        self.set_field('emerald', 'pokemon_storage_ptr_gba_address', {'iwram': '0x7FFD'})
        self.assert_rejected('4 bytes runs past the end of IWRAM')

    def test_pc_storage_stride_and_spans(self):
        for field, value, error in (
            ('pc_box_mon_stride', 79, 'pc_box_mon_stride must be 80'),
            ('pokemon_storage_size', 33603, 'fit inside pokemon_storage_size'),
            ('pc_box_count', 0, 'PC box dimensions must be positive'),
            ('pc_box_slots', 31, 'at most 30 slots'),
            ('pc_box_current_offset', 4, 'fit inside pokemon_storage_size'),
            ('pokemon_storage_base_gba_address', {'ewram': '0x38000'}, 'runs past the end of EWRAM'),
            ('pc_box_species_info_gba_address', {'gba': '0x09FFD2B0'}, 'revision shift must fit inside ROM'),
        ):
            with self.subTest(field=field):
                original = json.loads((self.dir / 'firered_rev0.json').read_text())['fields'][field]
                self.set_field('firered_rev0', field, value)
                try:
                    self.assert_rejected(error)
                finally:
                    self.set_field('firered_rev0', field, original)


class HeaderGateTest(unittest.TestCase):
    def make_root(self, reader_change=None):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        for relative in ['native/include/pokemon_reader.h', 'native/include/pokemon_structs.h',
                         *(f'native/src/{name}' for name in g.SYMBOL_HEADERS)]:
            (root / relative).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(g.REPO_ROOT / relative, root / relative)
        if reader_change:
            path = root / 'native/include/pokemon_reader.h'
            path.write_text(reader_change(path.read_text()))
        return root

    def test_new_struct_member_must_be_classified(self):
        root = self.make_root(lambda text: text.replace(
            '} GameMemoryConfig;', '    uint32_t mystery_thing;\n} GameMemoryConfig;'))
        with self.assertRaisesRegex(g.GamedataError, 'without a unit classification.*mystery_thing'):
            g.CFacts(root)

    def test_removed_struct_member_is_detected(self):
        root = self.make_root(lambda text: text.replace('    uint32_t battle_weather_offset;', ''))
        with self.assertRaisesRegex(g.GamedataError, 'no longer has: battle_weather_offset'):
            g.CFacts(root)


class CliTest(GamedataFixture):
    def run_main(self, *argv):
        err = io.StringIO()
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(err):
            code = g.main(list(argv))
        return code, err.getvalue()

    def test_check_detects_drift_and_hand_edits(self):
        output = Path(self.tmp.name) / 'gen.h'
        args = ('--config-dir', str(self.dir), '--output', str(output))
        self.assertEqual(self.run_main('--check', *args)[0], 1)          # missing
        self.assertEqual(self.run_main('--write', *args)[0], 0)
        self.assertEqual(self.run_main('--check', *args)[0], 0)
        output.write_text(output.read_text().replace('0x244EC', '0x244ED', 1))   # hand edit
        code, err = self.run_main('--check', *args)
        self.assertEqual(code, 1)
        self.assertIn('stale or hand-edited', err)
        self.assertEqual(self.run_main('--write', *args)[0], 0)
        self.set_field('emerald', 'battle_mons_size', 90)                # source drift
        self.assertEqual(self.run_main('--check', *args)[0], 1)

    def test_check_is_byte_exact_so_crlf_only_edits_are_stale(self):
        output = Path(self.tmp.name) / 'gen.h'
        args = ('--config-dir', str(self.dir), '--output', str(output))
        self.assertEqual(self.run_main('--write', *args)[0], 0)
        output.write_bytes(output.read_bytes().replace(b'\n', b'\r\n'))
        code, err = self.run_main('--check', *args)
        self.assertEqual(code, 1)
        self.assertIn('stale or hand-edited', err)
        self.assertEqual(self.run_main('--write', *args)[0], 0)
        self.assertNotIn(b'\r', output.read_bytes())
        self.assertEqual(self.run_main('--check', *args)[0], 0)

    def test_invalid_config_exits_2_and_never_writes(self):
        output = Path(self.tmp.name) / 'gen.h'
        self.set_field('emerald', 'battle_mons_offset', {'ewram': '0x40000'})
        code, err = self.run_main('--write', '--config-dir', str(self.dir), '--output', str(output))
        self.assertEqual(code, 2)
        self.assertIn('outside EWRAM', err)
        self.assertFalse(output.exists())

    def test_resolve_prints_inherited_view(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(g.main(['--resolve', 'leafgreen', '--config-dir', str(self.dir)]), 0)
        resolved = json.loads(out.getvalue())
        self.assertEqual(resolved['chain'], ['leafgreen', 'firered_rev0'])
        self.assertEqual(resolved['fields']['player_party_offset'], '0x24284')


if __name__ == '__main__':
    unittest.main(verbosity=2)
