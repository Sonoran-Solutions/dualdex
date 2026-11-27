#!/usr/bin/env python3
"""Generate native/src/gamedata_configs_gen.h from the declarative gamedata/gen3/ configs.

`gamedata/gen3/*.json` is the single source of truth for every `GameMemoryConfig` the native
reader compiles. This tool resolves `base` inheritance, validates every value strictly and emits
one `static const GameMemoryConfig CONFIG_<NAME>` initializer per config.

    python3 tools/gamedata/generate_gamedata.py --check   # exit 1 if the header is stale
    python3 tools/gamedata/generate_gamedata.py --write   # regenerate the header
    python3 tools/gamedata/generate_gamedata.py --resolve unbound   # print one resolved config

Exit status: 0 success, 1 stale/missing header (--check), 2 invalid configs or usage.

Config file shape (schemaVersion 1)::

    {
      "schemaVersion": 1,
      "id": "unbound",                     # must equal the file stem
      "base": "firered_rev0",              # optional; inherit `fields` from another config
      "cSymbol": "CONFIG_UNBOUND",
      "gameId": "GAME_UNBOUND",            # GbaGameId member; exactly one config per member
      "gameName": "Pokemon Unbound",
      "evidence": ["..."],                 # required, non-empty: where the values come from
      "notes": ["..."],                    # optional free-form lines
      "waivers": {"<check-id>": "reason"}, # optional; see CONSISTENCY_CHECKS
      "fields": { "<GameMemoryConfig member>": <value>, ... }
    }

`cSymbol`, `gameId`, `gameName`, `evidence`, `notes` and `waivers` are never inherited. A waiver
names one cross-field consistency check that this config is allowed to fail, with the reason; it
is emitted into the header, and a waiver for a check that passes is itself an error. `fields` are merged
over the base's resolved fields; `null` removes an inherited field (the C member is then left zero,
which the reader treats as "not declared").

Field values carry their unit explicitly:

* EWRAM-relative offsets (`*_offset` members the header documents as EWRAM-relative) and absolute
  GBA addresses (`*_gba_address`, `*_func_ptr`) MUST be tagged objects - a bare number is refused
  because it does not say which address space it is in:
      {"ewram": "0x24284"}     EWRAM-relative offset (0x0..0x3FFFF)
      {"iwram": "0x41D8"}      IWRAM-relative offset (0x0..0x7FFF), absolute fields only
      {"gba": "0x02024284"}    absolute GBA address; converted to an EWRAM offset where needed
      {"symbol": "HNS_..."}    a macro from a pinned generated ABI header (emitted verbatim)
* Struct-relative offsets, sizes, counts, bits, widths, masks and opaque values accept a JSON
  integer, a "0x" hex string, {"value": ...} or {"symbol": ...}.
* Enums accept a member name; booleans accept true/false.
* Any tagged object may also carry "note": "...", emitted as a comment above the member.
"""
import argparse
import ast
import json
from pathlib import Path
import re
import sys

REPO_ROOT = Path(__file__).resolve().parents[2]
CONFIG_DIR = REPO_ROOT / 'gamedata' / 'gen3'
OUTPUT = REPO_ROOT / 'native' / 'src' / 'gamedata_configs_gen.h'
READER_H = REPO_ROOT / 'native' / 'include' / 'pokemon_reader.h'
STRUCTS_H = REPO_ROOT / 'native' / 'include' / 'pokemon_structs.h'
# Only these pinned generated ABI headers may be referenced through {"symbol": ...}.
SYMBOL_HEADERS = (
    'hns_battle_pokemon_layout_gen.h',
    'hns_live_battle_layout_gen.h',
    'hns_field_status_gen.h',
)
SCHEMA_VERSION = 1

EWRAM_BASE, EWRAM_SIZE = 0x02000000, 0x00040000
IWRAM_BASE, IWRAM_SIZE = 0x03000000, 0x00008000
ROM_BASE, ROM_SIZE = 0x08000000, 0x02000000
REGIONS = {
    'ewram': (EWRAM_BASE, EWRAM_SIZE),
    'iwram': (IWRAM_BASE, IWRAM_SIZE),
    'rom': (ROM_BASE, ROM_SIZE),
}
PARTY_BYTES = 6 * 100           # PARTY_SIZE x sizeof(struct Pokemon), every supported layout
BOX_MON_BYTES = 80
MAX_BOX_SLOTS = 30
GEN3_SPECIES_COUNT = 412        # includes index 0 and excludes the egg sentinel
GEN3_SPECIES_INFO_BYTES = 28
NUM_BATTLE_STATS = 8            # statStages[] length
MAX_BITS = 0x10000              # sanity bound for bit positions inside a compiled struct

TOP_LEVEL_KEYS = {'schemaVersion', 'id', 'base', 'cSymbol', 'gameId', 'gameName',
                  'evidence', 'notes', 'waivers', 'fields'}
REQUIRED_TOP_LEVEL = ('schemaVersion', 'id', 'cSymbol', 'gameId', 'gameName', 'evidence', 'fields')
# Members every resolved config must declare (directly or through `base`).
REQUIRED_FIELDS = ('player_party_offset', 'player_party_count_offset', 'enemy_party_offset',
                   'enemy_party_count_offset', 'player_party_policy', 'has_evs', 'has_ivs')
VALUE_TAGS = {'ewram', 'iwram', 'gba', 'symbol', 'value'}

EWRAM_OFFSET_FIELDS = {
    'player_party_offset', 'player_party_count_offset', 'enemy_party_offset',
    'enemy_party_count_offset', 'battle_mons_offset', 'battler_party_indexes_offset',
    'battlers_count_offset', 'battle_type_flags_offset', 'battle_outcome_offset',
    'battler_positions_offset', 'absent_battler_flags_offset', 'field_statuses_offset',
    'battle_weather_offset', 'side_statuses_offset', 'battle_struct_ptr_offset',
}
# Absolute GBA address members and the regions each may point into.
GBA_ADDRESS_FIELDS = {
    'battle_current_move_gba_address': ('ewram', 'iwram'),
    'battle_turn_action_number_gba_address': ('ewram', 'iwram'),
    'battle_actions_by_turn_order_gba_address': ('ewram', 'iwram'),
    'battle_battler_by_turn_order_gba_address': ('ewram', 'iwram'),
    'battle_current_action_func_id_gba_address': ('ewram', 'iwram'),
    'battle_battler_attacker_gba_address': ('ewram', 'iwram'),
    'battle_battlers_count_gba_address': ('ewram', 'iwram'),
    'battle_main_func_gba_address': ('ewram', 'iwram'),
    'main_struct_gba_address': ('iwram',),
    'save_block1_ptr_gba_address': ('iwram',),
    'save_block1_base_gba_address': ('ewram',),
    'save_block3_ptr_gba_address': ('iwram',),
    'save_block3_base_gba_address': ('ewram',),
    'pokemon_storage_ptr_gba_address': ('iwram',),
    'pokemon_storage_base_gba_address': ('ewram',),
    'pc_box_species_info_gba_address': ('rom',),
    'pc_box_species_dex_gba_address': ('rom',),
    'action_selection_func_ptr': ('rom',),
    'run_turn_actions_func_ptr': ('rom',),
    'battle_main_cb1_func_ptr': ('rom',),
}
# Bytes the reader touches at each absolute address member. Region membership of the start
# address alone is not enough: the whole [address, address + width) span must stay inside the one
# region. The *_func_ptr members are code-pointer VALUES compared against reads, never read
# themselves, so only their first byte must be inside ROM.
GBA_ADDRESS_WIDTHS = {
    'battle_current_move_gba_address': 2,
    'battle_turn_action_number_gba_address': 1,
    'battle_actions_by_turn_order_gba_address': 4,     # HNS_LIVE_MAX_BATTLERS_COUNT
    'battle_battler_by_turn_order_gba_address': 4,     # HNS_LIVE_MAX_BATTLERS_COUNT
    'battle_current_action_func_id_gba_address': 1,
    'battle_battler_attacker_gba_address': 1,
    'battle_battlers_count_gba_address': 1,
    'battle_main_func_gba_address': 4,                 # a function pointer
    'main_struct_gba_address': 1,                      # composite reads are checked per member
    'save_block1_ptr_gba_address': 4,                  # a pointer
    'save_block1_base_gba_address': 4,
    'save_block3_ptr_gba_address': 4,                  # a pointer
    'save_block3_base_gba_address': 4,
    'pokemon_storage_ptr_gba_address': 4,
    'pokemon_storage_base_gba_address': 1,
    'pc_box_species_info_gba_address': GEN3_SPECIES_COUNT * GEN3_SPECIES_INFO_BYTES,
    'pc_box_species_dex_gba_address': (GEN3_SPECIES_COUNT - 1) * 2,
    'action_selection_func_ptr': 1,
    'run_turn_actions_func_ptr': 1,
    'battle_main_cb1_func_ptr': 1,
}
STRUCT_OFFSET_FIELDS = {
    'battle_mons_hp_offset', 'battle_mons_stat_stages_offset', 'battle_mons_ability_offset',
    'battle_mons_types_offset', 'battle_mons_item_offset', 'battle_mons_attack_offset',
    'battle_mons_defense_offset', 'battle_mons_speed_offset', 'battle_mons_spattack_offset',
    'battle_mons_spdefense_offset', 'battle_mons_max_hp_offset', 'battle_mons_status_offset',
    'battle_mons_personality_offset', 'battle_mons_volatiles_offset', 'save_block1_flags_offset',
    'save_block1_badges_offset', 'battle_struct_gimmick_offset', 'battle_struct_event_state_offset',
    'battle_struct_battler_state_offset', 'battle_gimmick_usable_offset',
    'battle_gimmick_player_select_offset', 'battle_struct_supreme_overlord_counter_offset',
    'battle_main_cb1_offset', 'battle_gimmick_active_offset', 'battle_struct_party_state_offset',
    'main_in_battle_byte_offset', 'save_block1_pos_offset', 'save_block1_location_offset',
    'save_block1_escape_warp_offset',
    'pc_box_current_offset', 'pc_box_mons_offset',
}
BYTES_FIELDS = {
    'battle_mons_size', 'battle_mons_ability_size', 'battle_mons_type_width',
    'battle_mons_item_size', 'battle_mons_attack_size', 'battle_mons_defense_size',
    'battle_mons_speed_size', 'battle_mons_spattack_size', 'battle_mons_spdefense_size',
    'battle_mons_personality_size', 'side_statuses_stride', 'battler_state_size',
    'supreme_overlord_counter_stride', 'battle_gimmick_side_stride', 'party_state_entry_size',
    'party_state_side_stride', 'save_block1_aslr_range', 'save_block1_size',
    'pokemon_storage_aslr_range', 'pokemon_storage_size', 'pc_box_mon_stride',
    'pc_box_rev1_rom_shift',
}
COUNT_FIELDS = {
    'battle_mons_type_count', 'max_battlers_count', 'switch_in_events_count',
    'supreme_overlord_counter_count', 'battle_gimmick_party_count', 'battle_gimmick_count',
    'party_state_side_count', 'party_state_party_count', 'location_max_map_group',
    'pc_box_count', 'pc_box_slots',
}
PC_STORAGE_FIELDS = (
    'pokemon_storage_ptr_gba_address', 'pokemon_storage_base_gba_address',
    'pokemon_storage_aslr_range', 'pokemon_storage_size', 'pc_box_current_offset',
    'pc_box_mons_offset', 'pc_box_count', 'pc_box_slots', 'pc_box_mon_stride',
)
PC_SPECIES_FIELDS = ('pc_box_species_info_gba_address', 'pc_box_species_dex_gba_address')
MASK_FIELDS = {'field_status_ion_deluge_mask', 'side_status_reflect_mask',
               'side_status_light_screen_mask'}
VALUE_FIELDS = {'battle_gimmick_dynamax_value', 'battle_action_use_move_value',
                'battle_action_exec_script_value'}
ENUM_FIELDS = {'storage_layout': 'PokemonStorageLayout',
               'player_party_policy': 'PartyDiscoveryPolicy'}
BOOL_FIELDS = {'has_evs', 'has_ivs'}
TOP_LEVEL_FIELDS = {'game_id', 'game_name'}     # set through `gameId` / `gameName`


def classify(name):
    """Unit kind of a GameMemoryConfig member, or None when this tool does not know it."""
    if name in TOP_LEVEL_FIELDS:
        return 'top'
    if name in EWRAM_OFFSET_FIELDS:
        return 'ewram_offset'
    if name in GBA_ADDRESS_FIELDS:
        return 'gba_address'
    for kind, names in (('struct_offset', STRUCT_OFFSET_FIELDS), ('bytes', BYTES_FIELDS),
                        ('count', COUNT_FIELDS), ('mask', MASK_FIELDS), ('value', VALUE_FIELDS)):
        if name in names:
            return kind
    if name in ENUM_FIELDS:
        return 'enum'
    if name in BOOL_FIELDS:
        return 'bool'
    if name.endswith('_bit'):
        return 'bit'
    if name.endswith('_width'):
        return 'width'
    return None


class GamedataError(ValueError):
    pass


def fail(where, message):
    raise GamedataError(f'{where}: {message}')


# ---------------------------------------------------------------------------------------------
# C header facts (read-only)
# ---------------------------------------------------------------------------------------------

def strip_c_comments(text):
    text = re.sub(r'/\*.*?\*/', ' ', text, flags=re.S)
    return re.sub(r'//[^\n]*', '', text)


def parse_struct_fields(header_text):
    """Ordered (name, C type) members of `GameMemoryConfig`."""
    match = re.search(r'typedef\s+struct\s*\{([^{}]*)\}\s*GameMemoryConfig\s*;',
                      strip_c_comments(header_text), re.S)
    if not match:
        raise GamedataError('GameMemoryConfig typedef not found in pokemon_reader.h')
    fields = []
    for decl in match.group(1).split(';'):
        decl = ' '.join(decl.split())
        if not decl:
            continue
        member = re.fullmatch(r'(const char\s*\*|[A-Za-z_][A-Za-z0-9_]*)\s*([A-Za-z_][A-Za-z0-9_]*)', decl)
        if not member:
            raise GamedataError(f'unsupported GameMemoryConfig member declaration: {decl!r}')
        fields.append((member.group(2), member.group(1).replace(' ', '')))
    return fields


def parse_enum(header_text, name):
    """Ordered member names of `typedef enum { ... } name;`."""
    match = re.search(r'typedef\s+enum\s*\{([^{}]*)\}\s*' + re.escape(name) + r'\s*;',
                      strip_c_comments(header_text), re.S)
    if not match:
        raise GamedataError(f'enum {name} not found')
    members = []
    for item in match.group(1).split(','):
        item = item.strip()
        if item:
            members.append(item.split('=')[0].strip())
    return members


_NUMBER = re.compile(r'\b(0[xX][0-9A-Fa-f]+|[0-9]+)[uUlL]*\b')


def evaluate_c_integer(expression):
    """Evaluate the small integer-constant subset used by the pinned generated headers."""
    tree = ast.parse(_NUMBER.sub(lambda m: str(int(m.group(1), 0)), expression), mode='eval')

    def walk(node):
        if isinstance(node, ast.Expression):
            return walk(node.body)
        if isinstance(node, ast.Constant) and type(node.value) is int:
            return node.value
        if isinstance(node, ast.BinOp) and isinstance(node.op, (ast.LShift, ast.BitOr, ast.Add)):
            left, right = walk(node.left), walk(node.right)
            if isinstance(node.op, ast.LShift):
                return left << right
            return left | right if isinstance(node.op, ast.BitOr) else left + right
        raise GamedataError(f'unsupported macro expression {expression!r}')
    return walk(tree)


def parse_symbols(header_dir):
    """{macro: (value, header file name)} for the allowlisted generated ABI headers."""
    symbols = {}
    for name in SYMBOL_HEADERS:
        text = (header_dir / name).read_text(encoding='utf-8')
        for line in strip_c_comments(text).splitlines():
            define = re.match(r'\s*#\s*define\s+([A-Z][A-Z0-9_]*)\s+(\S.*?)\s*$', line)
            if define and not define.group(1).endswith('_H'):
                try:
                    value = evaluate_c_integer(define.group(2))
                except (GamedataError, SyntaxError):
                    continue   # non-integer macros cannot be referenced
                symbols[define.group(1)] = (value, name)
    return symbols


class CFacts:
    """Everything the generator reads from the committed C headers."""

    def __init__(self, root=REPO_ROOT):
        reader = (root / 'native' / 'include' / 'pokemon_reader.h').read_text(encoding='utf-8')
        structs = (root / 'native' / 'include' / 'pokemon_structs.h').read_text(encoding='utf-8')
        self.fields = parse_struct_fields(reader)
        self.types = dict(self.fields)
        self.enums = {
            'GbaGameId': parse_enum(reader, 'GbaGameId'),
            'PartyDiscoveryPolicy': parse_enum(reader, 'PartyDiscoveryPolicy'),
            'PokemonStorageLayout': parse_enum(structs, 'PokemonStorageLayout'),
        }
        self.symbols = parse_symbols(root / 'native' / 'src')
        unknown = [name for name, _ in self.fields if classify(name) is None]
        if unknown:
            raise GamedataError('GameMemoryConfig members without a unit classification in '
                                'generate_gamedata.py (classify them before use): ' + ', '.join(unknown))
        known = (TOP_LEVEL_FIELDS | EWRAM_OFFSET_FIELDS | set(GBA_ADDRESS_FIELDS) | STRUCT_OFFSET_FIELDS
                 | BYTES_FIELDS | COUNT_FIELDS | MASK_FIELDS | VALUE_FIELDS | set(ENUM_FIELDS) | BOOL_FIELDS)
        stale = sorted(known - set(self.types))
        if stale:
            raise GamedataError('generate_gamedata.py classifies members GameMemoryConfig no longer '
                                'has: ' + ', '.join(stale))

    def type_max(self, name):
        return 0xFF if self.types[name] == 'uint8_t' else 0xFFFFFFFF


# ---------------------------------------------------------------------------------------------
# Loading and inheritance
# ---------------------------------------------------------------------------------------------

def check_text(where, value, *, comment, allow_empty=False):
    if not isinstance(value, str) or (not value and not allow_empty) or any(not (0x20 <= ord(c) <= 0x7E) for c in value):
        fail(where, 'must be a non-empty printable-ASCII string')
    if comment and ('/*' in value or '*/' in value or value.endswith('\\')):
        fail(where, 'must not contain "/*", "*/" or end in a backslash (emitted as a C comment)')
    return value


def check_lines(where, value, required):
    if value is None and not required:
        return []
    if not isinstance(value, list) or (required and not value):
        fail(where, 'must be a ' + ('non-empty ' if required else '') + 'list of strings')
    # Evidence entries must say something; notes may contain blank spacer lines.
    return [check_text(f'{where}[{i}]', line, comment=True, allow_empty=not required)
            for i, line in enumerate(value)]


def load_config_dir(config_dir):
    """{id: raw config dict} for every *.json in config_dir, with top-level validation."""
    configs = {}
    paths = sorted(Path(config_dir).glob('*.json'))
    if not paths:
        raise GamedataError(f'no configs found in {config_dir}')
    for path in paths:
        where = path.name
        try:
            raw = json.loads(path.read_text(encoding='utf-8'),
                             object_pairs_hook=_reject_duplicate_keys(where))
        except json.JSONDecodeError as error:
            fail(where, f'invalid JSON: {error}')
        if not isinstance(raw, dict):
            fail(where, 'top level must be an object')
        unknown = sorted(set(raw) - TOP_LEVEL_KEYS)
        if unknown:
            fail(where, 'unknown top-level key(s): ' + ', '.join(unknown))
        for key in REQUIRED_TOP_LEVEL:
            if key not in raw:
                fail(where, f'missing required key {key!r}')
        if type(raw['schemaVersion']) is not int or raw['schemaVersion'] != SCHEMA_VERSION:
            fail(where, f'schemaVersion must be {SCHEMA_VERSION}')
        if not isinstance(raw['id'], str) or raw['id'] != path.stem:
            fail(where, f'id must equal the file stem {path.stem!r}')
        if not re.fullmatch(r'[a-z][a-z0-9_]*', path.stem):
            fail(where, 'config ids must match [a-z][a-z0-9_]*')
        base = raw.get('base')
        if base is not None and not isinstance(base, str):
            fail(where, 'base must be a config id string or null')
        if not isinstance(raw['cSymbol'], str) or not re.fullmatch(r'CONFIG_[A-Z0-9_]+', raw['cSymbol']):
            fail(where, 'cSymbol must match CONFIG_[A-Z0-9_]+')
        check_text(f'{where}: gameName', raw['gameName'], comment=False)
        check_lines(f'{where}: evidence', raw['evidence'], required=True)
        check_lines(f'{where}: notes', raw.get('notes'), required=False)
        waivers = raw.get('waivers', {})
        if not isinstance(waivers, dict):
            fail(where, 'waivers must be an object of {check-id: reason}')
        for check, reason in waivers.items():
            if check not in CONSISTENCY_CHECKS:
                fail(where, f'unknown waiver {check!r} (waivable: {", ".join(sorted(CONSISTENCY_CHECKS))})')
            check_text(f'{where}: waivers.{check}', reason, comment=True)
        if not isinstance(raw['fields'], dict):
            fail(where, 'fields must be an object')
        configs[path.stem] = raw
    return configs


def _reject_duplicate_keys(where):
    def hook(pairs):
        seen = {}
        for key, value in pairs:
            if key in seen:
                fail(where, f'duplicate key {key!r}')
            seen[key] = value
        return seen
    return hook


def base_chain(configs, config_id):
    """[config_id, base, base-of-base, ...]; refuses missing bases and cycles."""
    chain = [config_id]
    while True:
        base = configs[chain[-1]].get('base')
        if base is None:
            return chain
        if base not in configs:
            fail(f'{chain[-1]}.json', f'base {base!r} does not exist')
        if base in chain:
            fail(f'{config_id}.json', 'circular base chain: ' + ' -> '.join(chain + [base]))
        chain.append(base)


def merged_fields(configs, config_id):
    """Raw field entries after applying the base chain (root first, `null` removes)."""
    merged = {}
    for link in reversed(base_chain(configs, config_id)):
        for name, value in configs[link]['fields'].items():
            if value is None:
                merged.pop(name, None)
            else:
                merged[name] = (value, link)
    return merged


# ---------------------------------------------------------------------------------------------
# Value parsing
# ---------------------------------------------------------------------------------------------

def parse_int(where, value):
    if type(value) is int:
        number = value
    elif isinstance(value, str) and re.fullmatch(r'0x[0-9A-Fa-f]+', value):
        number = int(value, 16)
    else:
        fail(where, f'expected a JSON integer or "0x" hex string, got {value!r}')
    if number < 0:
        fail(where, 'must not be negative')
    return number


def in_region(address, region):
    start, size = REGIONS[region]
    return start <= address < start + size


class Value:
    """A resolved member value: the integer used for validation and the C text emitted."""

    def __init__(self, number, text, note=None):
        self.number, self.text, self.note = number, text, note


def parse_field(facts, where, name, raw):
    kind = classify(name)
    note = None
    if isinstance(raw, dict):
        extra = set(raw) - VALUE_TAGS - {'note'}
        tags = sorted(set(raw) & VALUE_TAGS)
        if extra:
            fail(where, 'unknown key(s) in value object: ' + ', '.join(sorted(extra)))
        if len(tags) != 1:
            fail(where, 'a value object needs exactly one of ' + ', '.join(sorted(VALUE_TAGS)))
        if 'note' in raw:
            note = check_text(f'{where}.note', raw['note'], comment=True)
        tag, payload = tags[0], raw[tags[0]]
    else:
        tag, payload = None, raw

    if kind == 'bool':
        if tag not in (None, 'value') or type(payload) is not bool:
            fail(where, 'expected true or false')
        return Value(int(payload), 'true' if payload else 'false', note)
    if kind == 'enum':
        members = facts.enums[ENUM_FIELDS[name]]
        if tag not in (None, 'value') or payload not in members:
            fail(where, f'expected one of {", ".join(members)}')
        return Value(members.index(payload), payload, note)

    if tag == 'symbol':
        if not isinstance(payload, str) or payload not in facts.symbols:
            fail(where, f'unknown symbol {payload!r} (allowed headers: {", ".join(SYMBOL_HEADERS)})')
        # Emitted verbatim, so the macro's own value must already be in the member's unit; the
        # range checks below refuse e.g. an absolute address used for an EWRAM-relative member.
        number, text = facts.symbols[payload][0], payload
    elif kind == 'ewram_offset':
        if tag == 'ewram':
            number = parse_int(where, payload)
        elif tag == 'gba':
            absolute = parse_int(where, payload)
            if not in_region(absolute, 'ewram'):
                fail(where, f'0x{absolute:08X} is not an EWRAM address (0x02000000..0x0203FFFF)')
            number = absolute - EWRAM_BASE
        else:
            fail(where, 'EWRAM-relative member needs an explicit {"ewram": ...} or {"gba": ...} tag')
        text = f'0x{number:X}'
    elif kind == 'gba_address':
        regions = GBA_ADDRESS_FIELDS[name]
        if tag == 'gba':
            number = parse_int(where, payload)
        elif tag in ('ewram', 'iwram') and tag in regions:
            offset = parse_int(where, payload)
            base, size = REGIONS[tag]
            if offset >= size:
                fail(where, f'{tag} offset 0x{offset:X} is outside {tag.upper()} (size 0x{size:X})')
            number = base + offset
        else:
            fail(where, 'absolute GBA address member needs an explicit {"gba": ...}' +
                 ''.join(f' or {{"{r}": ...}}' for r in regions if r != 'rom') + ' tag')
        text = f'0x{number:08X}'
    else:
        if tag not in (None, 'value'):
            fail(where, f'{tag!r} tag is only valid for address members; use a plain number')
        number = parse_int(where, payload)
        text = f'0x{number:X}' if kind in ('struct_offset', 'mask') else str(number)

    # Region and range checks apply to every spelling, including symbols.
    if kind == 'ewram_offset':
        if number == 0:
            fail(where, 'offset 0 means "undeclared" to the reader; use null to leave it undeclared')
        if number >= EWRAM_SIZE:
            fail(where, f'EWRAM offset 0x{number:X} is outside EWRAM (size 0x{EWRAM_SIZE:X})')
    elif kind == 'gba_address':
        regions = GBA_ADDRESS_FIELDS[name]
        if number == 0:
            fail(where, 'address 0 means "undeclared" to the reader; use null to leave it undeclared')
        owner = [region for region in regions if in_region(number, region)]
        if not owner:
            fail(where, f'0x{number:08X} is outside the allowed region(s) ' + '/'.join(r.upper() for r in regions))
        width = GBA_ADDRESS_WIDTHS[name]
        if not in_region(number + width - 1, owner[0]):
            fail(where, f'0x{number:08X} + {width} bytes runs past the end of {owner[0].upper()}')
    elif kind == 'bit' and number >= MAX_BITS:
        fail(where, f'bit position {number} is implausibly large')
    elif kind == 'mask' and number == 0:
        fail(where, 'a mask must have at least one bit set; use null to leave it undeclared')
    if number > facts.type_max(name):
        fail(where, f'value 0x{number:X} does not fit {facts.types[name]}')
    return Value(number, text, note)


# ---------------------------------------------------------------------------------------------
# Resolution and cross-field consistency
# ---------------------------------------------------------------------------------------------

class Resolved:
    def __init__(self, raw, chain, values):
        self.id = raw['id']
        self.c_symbol = raw['cSymbol']
        self.game_id = raw['gameId']
        self.game_name = raw['gameName']
        self.evidence = list(raw['evidence'])
        self.notes = list(raw.get('notes') or [])
        self.waivers = dict(raw.get('waivers') or {})
        self.chain = chain
        self.values = values     # {member: Value}, only declared members

    def get(self, name):
        value = self.values.get(name)
        return None if value is None else value.number

    def as_json(self):
        return dict(id=self.id, chain=self.chain, cSymbol=self.c_symbol, gameId=self.game_id,
                    gameName=self.game_name,
                    fields={name: value.text for name, value in self.values.items()})


def resolve(facts, configs, config_id):
    raw = configs[config_id]
    where = f'{config_id}.json'
    chain = base_chain(configs, config_id)
    if raw['gameId'] not in facts.enums['GbaGameId'] or raw['gameId'] == 'GAME_UNKNOWN':
        fail(where, 'gameId must be a GbaGameId member other than GAME_UNKNOWN')
    # Every key of every config in the chain is validated BEFORE `null` removes anything, so a
    # misspelled member can never hide behind `"typo": null`.
    for link in base_chain(configs, config_id):
        for name in configs[link]['fields']:
            field_where = f'{link}.json: fields.{name}'
            kind = classify(name) if name in facts.types else None
            if kind is None:
                fail(field_where, 'unknown GameMemoryConfig member')
            if kind == 'top':
                fail(field_where, f'{name} is set through the top-level gameId/gameName keys')
    values = {}
    for name, (entry, origin) in merged_fields(configs, config_id).items():
        values[name] = parse_field(facts, f'{origin}.json: fields.{name}', name, entry)
    for name in REQUIRED_FIELDS:
        if name not in values:
            fail(where, f'missing required field {name!r} (declare it or inherit it through base)')
    resolved = Resolved(raw, chain, values)
    check_consistency(where, resolved)
    check_pc_storage(where, resolved, raw['fields'])
    return resolved


# Cross-field checks that a config may waive explicitly (with a reason) for legacy values whose
# bit-for-bit preservation matters more than tidiness. Range, region and type checks never waive.
CONSISTENCY_CHECKS = {
    'ewram-extent': 'a declared EWRAM object runs past the end of EWRAM',
    'party-overlap': 'the player and enemy party arrays overlap',
    'party-count-inside-party': 'a party count byte lies inside a party array',
    'party-counts-shared': 'the player and enemy counts share one byte',
    'battle-mons-declaration': 'gBattleMons offset/size/hp are not declared together',
    'battle-mons-member-extent': 'a BattlePokemon member does not fit inside battle_mons_size',
    'struct-bit-extent': 'a bitfield position does not fit inside its compiled struct',
    'main-in-battle': 'the gMain.inBattle declaration is incomplete or not a bit of one byte',
    'save-block1-window': 'the SaveBlock1 pointer/base/window/size declaration is inconsistent',
    'save-block1-member-extent': 'a SaveBlock1 member does not lie inside save_block1_size',
    'save-block3-declaration': 'the SaveBlock3 pointer and base are not declared together',
}


def check_consistency(where, config):
    get = config.get
    used = set()

    def problem(check, message):
        if check in config.waivers:
            used.add(check)
        else:
            fail(where, f'[{check}] {message}')

    def extent(name, size):
        start = get(name)
        if start is not None and start + size > EWRAM_SIZE:
            problem('ewram-extent', f'{name} 0x{start:X} + {size} bytes runs past the end of EWRAM')

    # Party arrays and their count bytes must not overlap one another.
    extent('player_party_offset', PARTY_BYTES)
    extent('enemy_party_offset', PARTY_BYTES)
    player, enemy = get('player_party_offset'), get('enemy_party_offset')
    if abs(player - enemy) < PARTY_BYTES:
        problem('party-overlap', 'player_party_offset and enemy_party_offset overlap (6 x 100-byte parties)')
    for count in ('player_party_count_offset', 'enemy_party_count_offset'):
        for party in (player, enemy):
            if party <= get(count) < party + PARTY_BYTES:
                problem('party-count-inside-party',
                        f'{count} 0x{get(count):X} lies inside the party array at 0x{party:X}')
    if get('player_party_count_offset') == get('enemy_party_count_offset'):
        problem('party-counts-shared', 'player and enemy party counts share one byte')

    # struct BattlePokemon members must fit inside the declared struct size.
    size = get('battle_mons_size')
    if (get('battle_mons_offset') is None) != (size is None) or size == 0:
        problem('battle-mons-declaration', 'battle_mons_offset and a non-zero battle_mons_size '
                                           'must be declared together')
    if size and get('battle_mons_offset') is not None:
        extent('battle_mons_offset', size * 4)
        if get('battle_mons_hp_offset') is None:
            problem('battle-mons-declaration', 'battle_mons_hp_offset is required when gBattleMons is declared')
    members = [('battle_mons_hp_offset', 2), ('battle_mons_stat_stages_offset', NUM_BATTLE_STATS),
               ('battle_mons_max_hp_offset', 2), ('battle_mons_status_offset', 4)]
    for member in ('ability', 'item', 'attack', 'defense', 'speed', 'spattack', 'spdefense', 'personality'):
        members.append((f'battle_mons_{member}_offset', get(f'battle_mons_{member}_size') or 1))
    if get('battle_mons_types_offset') is not None:
        members.append(('battle_mons_types_offset',
                        (get('battle_mons_type_count') or 1) * (get('battle_mons_type_width') or 1)))
    if get('battle_mons_volatiles_offset') is not None:
        members.append(('battle_mons_volatiles_offset', 1))
    for name, width in members:
        offset = get(name)
        if offset is not None and (not size or offset + width > size):
            problem('battle-mons-member-extent',
                    f'{name} 0x{offset:X} + {width} does not fit battle_mons_size {size}')
    volatiles = get('battle_mons_volatiles_offset')
    for name in config.values:
        if name.startswith('battle_mons_volatile_') and name.endswith('_bit'):
            width = get(name[:-4] + '_width') or 1
            if volatiles is None or not size or get(name) + width > (size - volatiles) * 8:
                problem('struct-bit-extent',
                        f'{name} {get(name)} + {width} bits does not fit after battle_mons_volatiles_offset')
    battler_state = get('battler_state_size')
    for name in ('battler_state_switch_in_bit', 'battler_state_is_first_turn_bit'):
        if get(name) is not None:
            width = get(name[:-4] + '_width') or 1
            if battler_state is None or get(name) + width > battler_state * 8:
                problem('struct-bit-extent', f'{name} does not fit inside battler_state_size')
    party_state = get('party_state_entry_size')
    if get('party_state_ate_berry_bit') is not None and (
            party_state is None or get('party_state_ate_berry_bit') >= party_state * 8):
        problem('struct-bit-extent', 'party_state_ate_berry_bit does not fit inside party_state_entry_size')

    # Composite reads at gMain + member offset must stay inside IWRAM (never waivable).
    main = get('main_struct_gba_address')
    if main is not None:
        iwram_end = IWRAM_BASE + IWRAM_SIZE
        for name, width in (('main_in_battle_byte_offset', 1), ('battle_main_cb1_offset', 4)):
            offset = get(name)
            if offset is not None and main + offset + width > iwram_end:
                fail(where, f'main_struct_gba_address 0x{main:08X} + {name} 0x{offset:X} + {width} '
                            f'bytes runs past the end of IWRAM (0x{iwram_end:08X})')

    # gMain.inBattle is one bit of one byte.
    if get('main_in_battle_bit') is not None and get('main_in_battle_bit') >= 8:
        problem('main-in-battle', 'main_in_battle_bit must be 0..7')
    if get('main_in_battle_byte_offset') is not None and get('main_struct_gba_address') is None:
        problem('main-in-battle', 'main_in_battle_byte_offset requires main_struct_gba_address')

    # SaveBlock1 window and struct-relative members.
    if get('save_block1_ptr_gba_address') is not None:
        base, window, sb1 = (get('save_block1_base_gba_address'), get('save_block1_aslr_range'),
                             get('save_block1_size'))
        if None in (base, window, sb1):
            problem('save-block1-window', 'save_block1_ptr_gba_address requires save_block1_base_gba_address, '
                                          'save_block1_aslr_range and save_block1_size')
        elif base % 4 or window == 0 or window % 4:
            problem('save-block1-window', 'save_block1_base_gba_address and save_block1_aslr_range '
                                          'must be 4-byte aligned and non-zero')
        elif base + window + sb1 > EWRAM_BASE + EWRAM_SIZE:
            problem('save-block1-window', 'SaveBlock1 base + ASLR window + size runs past the end of EWRAM')
    sb1 = get('save_block1_size')
    for name in ('save_block1_pos_offset', 'save_block1_location_offset',
                 'save_block1_escape_warp_offset', 'save_block1_flags_offset', 'save_block1_badges_offset'):
        if get(name) is not None and (sb1 is None or get(name) >= sb1):
            problem('save-block1-member-extent', f'{name} must lie inside save_block1_size')
    if (get('save_block3_ptr_gba_address') is None) != (get('save_block3_base_gba_address') is None):
        problem('save-block3-declaration',
                'save_block3_ptr_gba_address and save_block3_base_gba_address must be declared together')

    stale = sorted(set(config.waivers) - used)
    if stale:
        fail(where, 'waiver(s) for checks that pass; remove them: ' + ', '.join(stale))


def check_pc_storage(where, config, own_fields):
    if not any(name in config.values for name in (*PC_STORAGE_FIELDS, *PC_SPECIES_FIELDS,
                                                   'pc_box_rev1_rom_shift')):
        return
    if 'pokemon_storage_ptr_gba_address' not in own_fields:
        fail(where, 'PC storage must be explicitly declared for this build')
    pointer, base, window, size, current, mons, count, slots, stride = (
        config.get(name) for name in PC_STORAGE_FIELDS
    )
    if None in (pointer, base, window, size, current, mons, count, slots, stride):
        fail(where, 'PC storage fields must be declared together')
    if pointer % 4 or base % 4 or window < 4 or window % 4:
        fail(where, 'PC storage pointer, base and ASLR window must be 4-byte aligned')
    if stride != BOX_MON_BYTES:
        fail(where, 'pc_box_mon_stride must be 80')
    if not count or not 1 <= slots <= MAX_BOX_SLOTS:
        fail(where, f'PC box dimensions must be positive with at most {MAX_BOX_SLOTS} slots')
    if current >= mons or mons % 4 or mons + count * slots * stride > size:
        fail(where, 'PC box dimensions and offsets must fit inside pokemon_storage_size')
    if base + window - 4 + size > EWRAM_BASE + EWRAM_SIZE:
        fail(where, 'PC storage base + ASLR shift + size runs past the end of EWRAM')
    shift = config.get('pc_box_rev1_rom_shift') or 0
    if shift and config.game_id != 'GAME_FIRERED':
        fail(where, 'pc_box_rev1_rom_shift is only supported for FireRed')
    for name in PC_SPECIES_FIELDS:
        address = config.get(name)
        if address is None or address % 2 or not in_region(
            address + shift + GBA_ADDRESS_WIDTHS[name] - 1, 'rom'
        ):
            fail(where, f'{name} and its revision shift must fit inside ROM')


def resolve_all(facts, configs):
    resolved = [resolve(facts, configs, config_id) for config_id in sorted(configs)]
    for attribute, label in (('c_symbol', 'cSymbol'), ('game_id', 'gameId')):
        seen = {}
        for config in resolved:
            key = getattr(config, attribute)
            if key in seen:
                fail(f'{config.id}.json', f'{label} {key} is already used by {seen[key]}.json')
            seen[key] = config.id
    missing = [member for member in facts.enums['GbaGameId'][1:]
               if member not in {config.game_id for config in resolved}]
    if missing:
        raise GamedataError('GbaGameId member(s) without a gamedata config: ' + ', '.join(missing))
    order = facts.enums['GbaGameId']
    return sorted(resolved, key=lambda config: order.index(config.game_id))


# ---------------------------------------------------------------------------------------------
# Emission
# ---------------------------------------------------------------------------------------------

def c_string(text):
    return '"' + text.replace('\\', '\\\\').replace('"', '\\"') + '"'


def render(facts, resolved):
    used_headers = sorted({facts.symbols[value.text][1] for config in resolved
                           for value in config.values.values() if value.text in facts.symbols})
    out = [
        '/*',
        ' * GENERATED FILE - do not edit by hand.',
        ' *',
        ' * Source of truth: the gamedata/gen3/ JSON configs (schemaVersion 1)',
        ' * Generator:       tools/gamedata/generate_gamedata.py',
        ' * Regenerate:      python3 tools/gamedata/generate_gamedata.py --write',
        ' * Drift check:     python3 tools/gamedata/generate_gamedata.py --check',
        ' *',
        ' * Defines one `static const GameMemoryConfig` per supported game. Included exactly once,',
        ' * by native/src/pokemon_reader.c, whose pokemon_get_game_config() maps GbaGameId to these',
        ' * objects and returns NULL for GAME_UNKNOWN. Undeclared members stay zero, which every',
        ' * reader treats as "this layout does not declare the field" (fail closed).',
        ' */',
        '#ifndef DUALDEX_GAMEDATA_CONFIGS_GEN_H',
        '#define DUALDEX_GAMEDATA_CONFIGS_GEN_H',
        '',
        '#include "pokemon_reader.h"',
    ]
    out += [f'#include "{header}"' for header in used_headers]
    for config in resolved:
        out += ['', '/*', f' * {config.c_symbol} - gamedata/gen3/{config.id}.json']
        if len(config.chain) > 1:
            out.append(' * Inherits: ' + ' -> '.join(f'{link}.json' for link in config.chain[1:]))
        out += [' *', ' * Evidence:']
        out += [f' *   - {line}' for line in config.evidence]
        if config.waivers:
            out += [' *', ' * Validation waivers (gamedata/gen3 consistency checks deliberately waived):']
            out += [f' *   - {check}: {reason}' for check, reason in sorted(config.waivers.items())]
        if config.notes:
            out += [' *', ' * Notes:']
            out += [f' *   {line}'.rstrip() for line in config.notes]
        out += [' */', f'static const GameMemoryConfig {config.c_symbol} = {{',
                f'    .game_id = {config.game_id},',
                f'    .game_name = {c_string(config.game_name)},']
        for name, _ in facts.fields:
            value = config.values.get(name)
            if value is None:
                continue
            if value.note:
                out.append(f'    // {value.note}')
            out.append(f'    .{name} = {value.text},')
        out.append('};')
    out += ['', '#endif /* DUALDEX_GAMEDATA_CONFIGS_GEN_H */', '']
    return '\n'.join(out)


def generate(config_dir=CONFIG_DIR, root=REPO_ROOT):
    facts = CFacts(root)
    return render(facts, resolve_all(facts, load_config_dir(config_dir)))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true', help='fail if the generated header is stale')
    mode.add_argument('--write', action='store_true', help='regenerate the header')
    mode.add_argument('--resolve', metavar='ID', help='print one resolved config as JSON')
    parser.add_argument('--config-dir', type=Path, default=CONFIG_DIR)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    args = parser.parse_args(argv)
    try:
        if args.resolve:
            facts = CFacts(REPO_ROOT)
            configs = load_config_dir(args.config_dir)
            if args.resolve not in configs:
                raise GamedataError(f'no config {args.resolve!r}')
            resolve_all(facts, configs)   # validate the whole set, not one file in isolation
            print(json.dumps(resolve(facts, configs, args.resolve).as_json(), indent=2))
            return 0
        text = generate(args.config_dir)
    except (GamedataError, OSError) as error:
        print(f'error: {error}', file=sys.stderr)
        return 2
    if args.write:
        args.output.write_bytes(text.encode('utf-8'))
        print(f'wrote {args.output}')
        return 0
    # Byte-for-byte: no newline translation, so a CRLF-only edit is stale too.
    try:
        current = args.output.read_bytes()
    except OSError:
        current = None
    if current != text.encode('utf-8'):
        print(f'error: {args.output} is stale or hand-edited; run '
              'python3 tools/gamedata/generate_gamedata.py --write', file=sys.stderr)
        return 1
    print(f'{args.output} is up to date')
    return 0


if __name__ == '__main__':
    sys.exit(main())
