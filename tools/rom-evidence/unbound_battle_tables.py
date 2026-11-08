#!/usr/bin/env python3
"""Offline, numeric-only static battle-table evidence for one exact Unbound ROM.

Literal-pool locators and independently reviewed Thumb spans, not a source-layout
matcher. No ROM bytes, decoded text, private source, timestamps or paths are emitted.
"""
import argparse
import json
from pathlib import Path
import sys

from rowe_observe import require_private_directory
from unbound_data import ROM_BASE, TABLES, sha, span, u32
from unbound_identity import ROM_SIZE, exact_rom
from unbound_location import expect, thumb_literal
from unbound_observe import literal_references
from ups_identity import read_local, require

# Thumb instruction spans manually disassembled with Capstone 5.0.7. These hashes
# pin the complete index arithmetic/control flow, not just isolated field loads.
SPANS = {
    'move_effect': (0x08016330, 0x18, '35d9f1b086ded0ba52832918d60397a31e7e31b5f2836f8656c459a56a5ee3a6'),
    'move_power': (0x08015FEC, 0x14, 'eb4de1c47a830dc065d30e96ee65389e8d8d378ef6aa90b43da8c8fbbe60c616'),
    'move_type': (0x0800E338, 0x12, 'ea396eb0f3960e22db241d8d785b2f9a696502b3f3a26486843c9e979f58247c'),
    'move_accuracy': (0x08028A04, 0x2A, '71715ce9ee0632a1b906c73db78a33cfc46296a443a5abf97bb44d19b889587b'),
    'move_target': (0x08015F6A, 0x14, '59d6e54089638ac7f8c0c223cc5780f97660230aefffb0d7f8929eab51f23962'),
    'move_priority': (0x089BA978, 0x8E, 'dfa0e7700a36085a3274eebe50f64f0538ec0752f8bb3c248d1a8c968314127b'),
    'move_flags': (0x0801AABC, 0x12, '32a3f690134a98b5c2e3e5e35a25affd6e237f894432f4bb739276f266926492'),
    'move_split': (0x089F742A, 0x14, '2bccde847a4d0f5d00238500fbcaf421749622ea7b778f54f9c9261524f01c1a'),
    'move_z_power': (0x089D070C, 0x30, 'cf487b79fddd4dbaa35297f183f1672bd6dd4ae197c5d134aea00bb88951dfa5'),
    'special_flags_stride': (0x089A9520, 0x2A, '7f221474dda4f5cd2e499bdd26dbdce6c79d5b0d9ee2e4479c886dd291d6182d'),
    'special_flags_byte2_bit4': (0x089A5978, 0x10, '470c37c585768793875b0cbe5d14a1648e73c8bc374d9a1df18ae7b232bcfb62'),
    'chart_dimensions': (0x089CEE44, 0x4C, '1a71016f8c4b381f0d33ff0f1939f3acdf89315ddaab56e27a0bd9c5414d431d'),
    'base_stats_hp': (0x089C450E, 0x0C, 'c319a31c9552504a62f533a10a74e9ecbb84cbf6f35a370b3ad0f054f243ed6c'),
    'base_stats_other_stats': (0x089C46B2, 0xC6, 'dc128a901c885e76845a9aa1fccbfe95761f0bfcb383d92d441ddefa6104f69c'),
    'base_stats_types': (0x08012F20, 0x2E, '44b428bdf53557a71e4c3a311546c1606ab339149592bc17a21cb04a1d7568ae'),
    'base_stats_abilities': (0x08040D7A, 0x2C, 'd9c4940c7a5ed416f3759aaac6e832490a81194d54974c772fd3752ef8d6cef1'),
    'item_effect': (0x089F3360, 0x1C, 'd23b0e9b2338391675c8537d9fdc62c1435c3513ca65f12c5e9033cedbc44fa7'),
    'item_param': (0x089F3380, 0x1C, 'a93a7822567457bad6afa40eaeacb52054861ee312d55a380416482d93a52509'),
    'item_effect_hook': (0x0809A924, 0x08, '86b2cf96c8d1f9f8a5f8a83a4c1c810cae96d810f297d0e4559578d213c73630'),
    'item_param_old': (0x0809A948, 0x24, 'e3dc1b901086de995a00b4da89550f46541862ba58691b78d5b995482d1ef0ce'),
}

# name: (byte offset, load PC, base register, proof span, table literal load PC)
MOVE_FIELDS = {
    'effect': (0, 0x0801633E, 0, 'move_effect', 0x08016330),
    'power': (1, 0x08015FFA, 4, 'move_power', 0x08015FEC),
    'type': (2, 0x0800E344, 0, 'move_type', 0x0800E338),
    'accuracy': (3, 0x08028A26, 2, 'move_accuracy', 0x08028A04),
    'target': (6, 0x08015F78, 0, 'move_target', 0x08015F6A),
    'priority': (7, 0x089BA98C, 5, 'move_priority', 0x089BA980),
    'flags': (8, 0x0801AAC6, 0, 'move_flags', 0x0801AABC),
    'z_move_power': (9, 0x089D0738, 3, 'move_z_power', 0x089D070C),
    'split': (10, 0x089F7436, 3, 'move_split', 0x089F7432),
}
BASE_FIELDS = dict(zip(('hp', 'attack', 'defense', 'speed', 'sp_attack', 'sp_defense'), range(6)),
                   type1=6, type2=7, ability1=0x16, ability2=0x17, hidden_ability=0x1A)
ITEM_FIELDS = dict(holdEffect=0x12, holdEffectParam=0x13)


def literal(rom, pc, indirect=False):
    """Reuse the Thumb literal decoder; bounds-check before each dereference."""
    span(rom, pc - ROM_BASE, 2)
    h = int.from_bytes(span(rom, pc - ROM_BASE, 2), 'little')
    pool = ((pc + 4) & ~3) + (h & 255) * 4
    span(rom, pool - ROM_BASE, 4)
    rd, at, value = thumb_literal(rom, pc)
    result = dict(load_at=hex(pc), register=rd, pool_address=hex(at), value=hex(value))
    if indirect:
        result['pointer_cell'] = hex(value)
        value = u32(rom, value - ROM_BASE)
        result['dereferenced_value'] = hex(value)
    return value, result


def locate_table(rom, pc, stride, *, end_pc=None, count=None, indirect=False):
    """Derive a span from a consumer and, where available, an adjacent-table literal.

    An adjacent-table start proves a contiguous storage extent, not a runtime ID
    validity policy. Callers separately assert the expected record domain.
    """
    require(type(stride) is int and 0 < stride <= 64, 'invalid table stride')
    address, start = literal(rom, pc, indirect)
    boundary = None
    if end_pc is not None:
        end, boundary = literal(rom, end_pc)
        require(end > address and (end - address) % stride == 0, 'invalid table boundary')
        derived = (end - address) // stride
        require(count is None or derived == count, 'table count mismatch')
        count = derived
    require(type(count) is int and 0 < count <= 4096, 'invalid table count')
    raw = span(rom, address - ROM_BASE, count * stride)
    out = dict(status='VERIFIED', file_offset=hex(address - ROM_BASE),
               rom_address=hex(address), end_address=hex(address + len(raw)),
               count=count, stride=stride, bytes=len(raw), sha256=sha(raw), locator=start)
    if boundary:
        out['adjacent_table_locator'] = boundary
    return out


def parse_records(rom, offset, count, stride, fields, signed=()):
    """Selected one-byte numeric fields only; never expose names or record bytes."""
    require(type(count) is int and 0 < count <= 4096, 'invalid record count')
    require(type(stride) is int and 0 < stride <= 64, 'invalid record stride')
    require(all(type(n) is int and 0 <= n < stride for n in fields.values()),
            'field outside record')
    require(set(signed) <= fields.keys(), 'unknown signed field')
    raw = span(rom, offset, count * stride)
    return [{name: int.from_bytes(raw[i*stride+n:i*stride+n+1], 'little', signed=name in signed)
             for name, n in fields.items()} for i in range(count)]


def parse_chart(rom, offset, dimension):
    require(type(dimension) is int and 0 < dimension <= 64, 'invalid chart dimension')
    raw = span(rom, offset, dimension * dimension)
    return [list(raw[i*dimension:(i+1)*dimension]) for i in range(dimension)]


def byte_load(rom, pc):
    """Decode Thumb-1 unsigned byte loads, returning numeric register/offset roles."""
    h = int.from_bytes(span(rom, pc - ROM_BASE, 2), 'little')
    out = dict(load_at=hex(pc), width=1, destination_register=h & 7, base_register=(h >> 3) & 7)
    if h >> 11 == 0b01111:
        out['displacement'] = (h >> 6) & 31
    elif h & 0xFE00 == 0x5C00:
        out['index_register'] = (h >> 6) & 7
    else:
        raise ValueError('expected Thumb byte load at ' + hex(pc))
    return out


def immediate(rom, pc, register):
    """Thumb-1 MOVS immediate, used by the reviewed MULS index sequences."""
    h = int.from_bytes(span(rom, pc - ROM_BASE, 2), 'little')
    require(h >> 11 == 0b00100 and (h >> 8) & 7 == register, 'expected stride constant at ' + hex(pc))
    return h & 255


def field_proof(rom, offset, pc, base, proof, *, index=None, signed=False):
    start, size, _ = SPANS[proof]
    require(start <= pc <= start + size - 2, 'load outside proof span')
    decoded = byte_load(rom, pc)
    require(decoded['base_register'] == base, 'field base register mismatch')
    require((decoded.get('displacement') == offset if index is None else
             decoded.get('index_register') == index), 'field offset/index mismatch')
    return dict(status='VERIFIED', offset=offset, width=1, signed=signed,
                proof_span=proof, instruction=decoded)


def checked_spans(rom):
    out = {}
    for name, (address, size, expected) in SPANS.items():
        actual = sha(span(rom, address - ROM_BASE, size))
        require(actual == expected, 'consumer span mismatch: ' + name)
        out[name] = dict(status='VERIFIED', rom_address=hex(address), bytes=size, sha256=actual)
    return out


def inspect(rom):
    identity = exact_rom(rom)  # Before any offsets, decoders, locators or table reads.
    proofs = checked_spans(rom)
    moves = locate_table(rom, 0x0800E338, immediate(rom, 0x089BA97A, 2), end_pc=0x089C74B6, count=923)
    moves['stride_constant_at'] = hex(0x089BA97A)
    move_address = int(moves['rom_address'], 16)
    require(u32(rom, 0x1CC) == move_address, 'move header pointer mismatch')
    moves['header_pointer_address'] = hex(ROM_BASE + 0x1CC)
    moves['fields'] = {}
    for name, (offset, pc, base, proof, ref) in MOVE_FIELDS.items():
        address, loc = literal(rom, ref)
        require(address == move_address, 'move consumer table mismatch')
        moves['fields'][name] = dict(field_proof(rom, offset, pc, base, proof, signed=name == 'priority'),
                                      table_literal=loc)
    rows = parse_records(rom, move_address - ROM_BASE, 923, 12,
                         {k: v[0] for k, v in MOVE_FIELDS.items()}, ('priority',))
    require(all(row['type'] < 24 and row['accuracy'] <= 100 and row['split'] <= 2 for row in rows),
            'move numeric domain mismatch')
    moves['split_value_set'] = sorted({row['split'] for row in rows})
    moves['priority_range'] = [min(row['priority'] for row in rows), max(row['priority'] for row in rows)]

    flags = locate_table(rom, 0x089A952E, immediate(rom, 0x089A9528, 1), end_pc=0x089B7730, count=923)
    flags['stride_constant_at'] = hex(0x089A9528)
    flags['layout'] = dict(status='VERIFIED', bytes_per_record=7,
                           proof_span='special_flags_stride',
                           bit_accesses=[dict(field_proof(rom, 3, 0x089A9532, 3, 'special_flags_stride'), bit=7),
                                         dict(field_proof(rom, 2, 0x089A5982, 3, 'special_flags_byte2_bit4'), bit=4)])
    require(literal(rom, 0x089A597E)[0] == int(flags['rom_address'], 16), 'flags consumer mismatch')
    flags['named_bit_mapping'] = dict(status='UNVERIFIED')

    chart = locate_table(rom, 0x089CEE4E, immediate(rom, 0x089CEE46, 6), end_pc=0x089D377A, count=24)
    chart['stride_constant_at'] = hex(0x089CEE46)
    matrix = parse_chart(rom, int(chart['file_offset'], 16), chart['stride'])
    require(sorted({v for row in matrix for v in row}) == [0, 1, 5, 20], 'chart value set mismatch')
    require(not any(matrix[0x14]) and not any(row[0x14] for row in matrix), 'blank type is not empty')
    chart.update(entry_size=1, rows=24, columns=24, proof_span='chart_dimensions',
                 numeric_value_set=[0, 1, 5, 20],
                 blank_type=dict(status='VERIFIED', id=0x14, row_value_set=[0], column_value_set=[0]),
                 fairy_type=dict(status='SOURCE_APPROXIMATE', id=23,
                                 row_index_status='VERIFIED', column_index_status='VERIFIED'))

    stats = locate_table(rom, 0x08040D80, immediate(rom, 0x089C4512, 3),
                         count=TABLES['base_stats'][1], indirect=True)
    stats['stride_constant_at'] = hex(0x089C4512)
    require(int(stats['file_offset'], 16) == TABLES['base_stats'][0], 'base stats table mismatch')
    stats['fields'] = {'hp': field_proof(rom, 0, 0x089C4518, 1, 'base_stats_hp', index=2)}
    # +1..+5: r7=28*species, r4=1..5; r2=r7+r4; r3=*table_cell.
    # The loop's destination selector is r4+0x3a (0x3b..0x3f).
    for name, offset in list(BASE_FIELDS.items())[1:6]:
        stats['fields'][name] = dict(field_proof(rom, offset, 0x089C472C, 2, 'base_stats_other_stats', index=3),
                                     loop_index=offset, destination_selector=offset + 0x3A)
    for name, offset, pc, base, proof in (
            ('type1', 6, 0x08012F2C, 0, 'base_stats_types'),
            ('type2', 7, 0x08012F48, 0, 'base_stats_types'),
            ('ability1', 0x16, 0x08040D9E, 3, 'base_stats_abilities'),
            ('ability2', 0x17, 0x08040D96, 3, 'base_stats_abilities'),
            ('hidden_ability', 0x1A, 0x08040D8A, 3, 'base_stats_abilities')):
        stats['fields'][name] = field_proof(rom, offset, pc, base, proof)
    for pc, indirect in ((0x089C450E, True), (0x089C46C0, True), (0x08012F28, False), (0x08012F44, False)):
        require(literal(rom, pc, indirect)[0] == int(stats['rom_address'], 16), 'stats consumer mismatch')
    stats['ability_resolution'] = dict(status='UNVERIFIED')  # Declared bytes only.
    parse_records(rom, int(stats['file_offset'], 16), stats['count'], 28, BASE_FIELDS)

    max_item = immediate(rom, 0x089F3360, 3) << 2
    expect(rom, 0x089F3364, (0x009B,), 'item upper-bound shift')
    require(max_item + 1 == TABLES['items'][1], 'item count mismatch')
    items = locate_table(rom, 0x089F336C, immediate(rom, 0x089F3370, 3), count=max_item + 1, indirect=True)
    items['stride_constant_at'] = hex(0x089F3370)
    require(int(items['file_offset'], 16) == TABLES['items'][0], 'item table mismatch')
    require(literal(rom, 0x089F338C, True)[0] == int(items['rom_address'], 16), 'item consumer mismatch')
    require(literal(rom, 0x0809A924)[0] == 0x089F3361, 'item hook mismatch')
    require(literal(rom, 0x0809A94E)[0] == int(items['rom_address'], 16), 'legacy item consumer mismatch')
    items['fields'] = dict(holdEffect=field_proof(rom, 0x12, 0x089F3378, 0, 'item_effect'),
                          holdEffectParam=field_proof(rom, 0x13, 0x089F3398, 0, 'item_param'))
    items['additional_param_load'] = byte_load(rom, 0x0809A95E)
    items['max_valid_id'] = max_item
    parse_records(rom, int(items['file_offset'], 16), items['count'], 44, ITEM_FIELDS)

    # Extra executable checks for signed priority, record strides and stat-loop bounds.
    expect(rom, 0x089BA9CC, (0x0620, 0x1600), 'priority sign extension')
    expect(rom, 0x089CEE46, (0x2618,), 'type row stride')
    expect(rom, 0x089A9528, (0x2107,), 'special flags stride')
    expect(rom, 0x089C4726, (0x2401,), 'stat loop start')
    expect(rom, 0x089C4774, (0x2C06, 0xD1D7), 'stat loop end')
    tables = dict(gBattleMoves=moves, gSpecialMoveFlags=flags, gTypeEffectiveness=chart,
                  base_stats=stats, items=items)
    refs = literal_references(rom, {name: int(table['rom_address'], 16) for name, table in tables.items()})
    for name, table in tables.items():
        table['pc_literal_load_count'] = refs[name]['pc_literal_loads']
    return dict(schema_version=1, status='VERIFIED', scope='STATIC_SPANS_AND_SELECTED_FIELD_OFFSETS',
                rom=identity, tables=tables, consumer_spans=proofs,
                source_hypothesis=dict(status='SOURCE_APPROXIMATE',
                                       cfru_commit='b637a27898b14e25dd24d0f69a3e302f0069deb8'),
                runtime_damage_semantics=dict(status='UNVERIFIED'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    try:
        require_private_directory(args.rom.resolve().parent)
        require(args.out.resolve() != args.rom.resolve() and
                not (args.out.exists() and args.out.samefile(args.rom)), 'output must not overwrite ROM')
        # Only JSON reports may be overwritten, never tools or private inputs.
        require(args.out.suffix == '.json', 'output must have .json suffix')
        report = inspect(read_local(args.rom, ROM_SIZE))
        args.out.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    except (ValueError, OSError) as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
