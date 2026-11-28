#!/usr/bin/env python3
"""Exact-ROM numeric move/ability/item identities; never emit ROM text."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import re
import sys

import generate_unbound_species as species
from unbound_data import item_name, names, normalized, sha, span
from ups_identity import read_local, require

ROOT = species.ROOT
CONTRACT = ROOT / 'tools/rom-evidence/evidence/unbound-data-contract.json'
OUTPUT = ROOT / 'app/src/main/java/com/dualdex/pokemon/unbound/UnboundNameTables.kt'
SOURCES = {
    'MOVE': ROOT / 'app/src/main/java/com/dualdex/pokemon/MoveDatabase.kt',
    'ABILITY': ROOT / 'app/src/main/java/com/dualdex/pokemon/hns/HnsAbilityAuditData.kt',
    'ITEM': ROOT / 'app/src/main/java/com/dualdex/pokemon/ItemDatabase.kt',
}
TABLES = {'MOVE': 'move_names', 'ABILITY': 'ability_names', 'ITEM': 'items'}
PATTERNS = {
    'MOVE': r'^\s*register\((\d+), "([^"\\]+)",',
    'ABILITY': r'^\s*HnsAbilityEntry\((\d+), "[^"\\]+", "([^"\\]+)",',
    'ITEM': r'^\s*expansionMap\[(\d+)\] = ItemInfo\(\d+, "([^"\\]+)",',
}


def catalogues():
    result = {}
    for key, path in SOURCES.items():
        rows = [(int(i), name) for i, name in re.findall(PATTERNS[key], path.read_text(), re.M)]
        require(rows and len({i for i, _ in rows}) == len(rows), f'invalid {key} catalogue')
        result[key] = rows
    return result


def duplicate_name_ids(records):
    """Exclude every verified nonreserved row sharing a normalized ROM name."""
    by_name = defaultdict(list)
    for i, row in enumerate(records):
        name = normalized(row['name']) if row.get('name') else ''
        if i > 0 and row['status'] == 'VERIFIED' and name:
            by_name[name].append(i)
    return {i for ids in by_name.values() if len(ids) > 1 for i in ids}


def match_names(records, catalogue, excluded=()):
    """Unique exact normalized match only; 0 means absent/unmapped in all domains."""
    by_name = defaultdict(set)
    for app_id, name in catalogue:
        if app_id > 0 and normalized(name):
            by_name[normalized(name)].add(app_id)
    excluded = set(excluded) | duplicate_name_ids(records)
    result = []
    for i, row in enumerate(records):
        candidates = by_name.get(normalized(row['name']), set()) if row.get('name') else set()
        result.append(next(iter(candidates)) if i > 0 and i not in excluded and
                      row['status'] == 'VERIFIED' and len(candidates) == 1 else 0)
    return result


def decode_tables(rom, contract):
    """Table hash gates run before decoding; readers remain owned by rom-evidence."""
    result = {}
    for key, label in TABLES.items():
        table = contract['tables'][label]
        offset, count, stride = int(table['file_offset'], 16), table['count'], table['stride']
        raw = span(rom, offset, count * stride)
        require(sha(raw) == table['sha256'], f'table SHA-256 mismatch: {label}')
        if key != 'ITEM':
            result[key] = names(rom, offset, count, stride)
        else:
            result[key] = []
            mismatches = []
            for i in range(count):
                record = raw[i * stride:(i + 1) * stride]
                if int.from_bytes(record[14:16], 'little') != i:
                    mismatches.append(i)
                result[key].append(item_name(rom, record)[0])
            require(mismatches == table['record_id_mismatch_ids'], 'item record IDs changed')
    return result


def derive(rom, contract, catalogues):
    require(len(rom) == species.ROM_SIZE and sha(rom) == species.PINNED_ROM_SHA256,
            'refusing ROM: not the pinned Unbound candidate')
    require(contract['rom']['sha256'] == species.PINNED_ROM_SHA256, 'contract ROM mismatch')
    decoded = decode_tables(rom, contract)
    return {key: match_names(decoded[key], catalogues[key],
                            contract['tables']['items']['record_id_mismatch_ids'] if key == 'ITEM' else ())
            for key in TABLES}


def digest(rows):
    return sha(''.join(f'{i}|{value}\n' for i, value in enumerate(rows)).encode('ascii'))


def catalogue_digest(rows, catalogue):
    """Pin only used target IDs/names; each name must still be unique globally."""
    by_id = {i: normalized(name) for i, name in catalogue}
    by_name = defaultdict(set)
    for i, name in by_id.items():
        if i > 0:
            by_name[name].add(i)
    used = sorted(set(rows) - {0})
    for i in used:
        require(i in by_id, f'missing catalogue target ID {i}')
        require(by_id[i] and len(by_name[by_id[i]]) == 1,
                f'ambiguous catalogue target ID {i}')
    return sha(''.join(f'{i}|{by_id[i]}\n' for i in used).encode('ascii'))


def provenance(contract, tables, catalogues):
    result = {'ROM_SHA256': species.PINNED_ROM_SHA256,
              'CONTRACT_SHA256': sha(CONTRACT.read_bytes()),
              'GENERATOR_SHA256': sha(Path(__file__).read_bytes()),
              'READER_SHA256': sha((ROOT / 'tools/rom-evidence/unbound_data.py').read_bytes()),
              'DECODER_SHA256': sha((ROOT / 'tools/rom-evidence/rowe_text.py').read_bytes())}
    for key, label in TABLES.items():
        result[key + '_TABLE_SHA256'] = contract['tables'][label]['sha256']
        result[key + '_CATALOGUE_SHA256'] = catalogue_digest(tables[key], catalogues[key])
    return result


def render(tables, pins):
    constants = '\n'.join(f'    const val {key}: String = "{value}"' for key, value in pins.items())
    arrays = '\n\n'.join(f'    const val {key}_DIGEST: String = "{digest(rows)}"\n'
                          f'    val {key}: IntArray = intArrayOf(\n{species.int_array(rows)}\n    )'
                          for key, rows in tables.items())
    return f'''package com.dualdex.pokemon.unbound

/**
 * Generated by tools/unbound-data-pack/generate_unbound_names.py. DO NOT EDIT.
 * Numbers only: Unbound ID (index) -> bundled catalogue ID; 0 = unmapped.
 * Identity/presentation only; no Unbound mechanics or species ability-slot declarations.
 * Regenerate with --rom <private-rom>; ROM-free integrity check: --verify.
 */
internal object UnboundNameTables {{
{constants}

{arrays}
}}
'''


def verify(text, contract, catalogues):
    require(contract['rom']['sha256'] == species.PINNED_ROM_SHA256, 'contract ROM mismatch')
    tables = {}
    for key, label in TABLES.items():
        match = re.search(rf'val {key}: IntArray = intArrayOf\(\n(.*?)\n    \)', text, re.S)
        require(match is not None, f'missing {key} table')
        body = re.sub(r'/\* \d+ \*/', '', match[1])
        require(re.fullmatch(r'[\d,\s]+', body) is not None, f'non-numeric {key} table')
        rows = [int(v.strip()) for v in body.split(',') if v.strip()]
        require(len(rows) == contract['tables'][label]['count'], f'{key} count mismatch')
        valid_ids = {0} | {i for i, _ in catalogues[key]}
        require(rows[0] == 0 and all(v in valid_ids for v in rows), f'invalid {key} IDs')
        if key == 'ITEM':
            require(all(rows[i] == 0 for i in contract['tables']['items']['record_id_mismatch_ids']),
                    'mismatched item record mapped')
        tables[key] = rows
    require(text == render(tables, provenance(contract, tables, catalogues)),
            'artifact digests/provenance/output mismatch')
    return tables


def coverage(tables):
    for key, rows in tables.items():
        mapped = sum(v != 0 for v in rows)
        print(f'{key}: mapped {mapped}, unmapped {len(rows) - mapped}; unmapped IDs: ' +
              ', '.join(str(i) for i, v in enumerate(rows) if v == 0))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    parser.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    try:
        contract = json.loads(CONTRACT.read_text())
        cats = catalogues()
        if args.verify:
            tables = verify(read_local(args.output, 1024 * 1024).decode(), contract, cats)
            if args.rom:
                require(tables == derive(read_local(args.rom, species.ROM_SIZE), contract, cats),
                        'artifact differs from exact-ROM derivation')
        else:
            require(args.rom is not None, '--rom is required')
            tables = derive(read_local(args.rom, species.ROM_SIZE), contract, cats)
            args.output.write_text(render(tables, provenance(contract, tables, cats)))
        coverage(tables)
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
