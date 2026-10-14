#!/usr/bin/env python3
"""Bounded exact-ROM inspection for R.O.W.E. 2.1.9.1 data/text evidence.

The module returns extracted mappings to local callers, but its command-line
report contains only sanitized counts, classifications and region digests.
"""
import argparse
import hashlib
import json
import struct
import sys
from collections import Counter
from pathlib import Path

from rowe_symbols import COUNT as SYMBOL_COUNT
from rowe_symbols import TABLE as SYMBOL_TABLE
from rowe_symbols import symbol_table
from rowe_text import EMPTY_OR_UNSET, VALID_TEXT, decode_text
from ups_identity import MAX_ROM, identity, read_local, require


ROM_BASE = 0x08000000
ROM_SIZE = 33_554_432
ROM_SHA1 = "81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77"
ROM_SHA256 = "fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3"
SPECIES_STRIDE = 13
MOVE_STRIDE = 17
BASE_STATS_STRIDE = 64
BATTLE_MOVE_STRIDE = 56
ITEM_STRIDE = 56
TYPE_COUNT = 19
MAP_ENTRY_STRIDE = 8
MAP_ENTRY_LEAD_COUNT = 0xD5
FORM_POINTER_TABLE = 0x085E700C
FORM_SENTINEL = 0xFFFF
FORM_LIMIT = 32
STATIC_ACCESSOR_SPANS = {
    "species_name_index": (0x08016970, 0x080169AC),
    "move_name_index": (0x080782A4, 0x08078314),
    "form_species_resolution": (0x080A1874, 0x080A1908),
    "base_stats_type_fields": (0x080A192A, 0x080A1944),
    "battle_move_power_index": (0x08044060, 0x08044104),
    "battle_move_type_index": (0x0804225C, 0x08042300),
    "item_record_index": (0x08128380, 0x081283C0),
    "type_effectiveness_select": (0x0805915C, 0x08059184),
    "map_section_record_index": (0x0817ABA0, 0x0817ABBC),
}

DISPLAY_CHECKS = {
    "species": {161: "Sentret", 661: "Fletchling"},
    "moves": {
        64: "Peck", 45: "Growl", 33: "Tackle", 98: "Quick Attack",
        193: "Foresight", 111: "Defense Curl",
    },
}


def read_span(rom: bytes, address: int, length: int) -> bytes:
    """Read a bounded GBA ROM span, rejecting overflow and out-of-ROM pointers."""
    require(type(address) is int and type(length) is int, "non-integer address or length")
    require(length >= 0 and ROM_BASE <= address <= ROM_BASE + len(rom), "address outside ROM")
    offset = address - ROM_BASE
    require(length <= len(rom) - offset, "span outside ROM")
    return rom[offset:offset + length]


def read_u16(rom: bytes, address: int) -> int:
    return struct.unpack("<H", read_span(rom, address, 2))[0]


def read_u32(rom: bytes, address: int) -> int:
    return struct.unpack("<I", read_span(rom, address, 4))[0]


def rom_identity(rom: bytes) -> dict:
    require(len(rom) == ROM_SIZE, "exact diagnostic ROM size mismatch")
    result = identity(rom)
    require(result["sha1"] == ROM_SHA1, "exact diagnostic ROM SHA-1 mismatch")
    require(result["sha256"] == ROM_SHA256, "exact diagnostic ROM SHA-256 mismatch")
    return result


def load_exact_rom(path: str | Path) -> bytes:
    rom = read_local(path, MAX_ROM)
    rom_identity(rom)
    return rom


def _require_symbols(rom: bytes) -> dict:
    symbols = symbol_table(rom, SYMBOL_TABLE, SYMBOL_COUNT)
    expected = {
        "gSpeciesNames": 0x084180A4,
        "gMoveNames": 0x0841E42C,
        "gBattleMoves": 0x08435298,
        "gBaseStats": 0x08502C54,
        "gItems": 0x08F76D78,
        "gRegionMapEntries": 0x08FD278C,
    }
    require(all(symbols.get(name) == address for name, address in expected.items()),
            "exact-release data symbol mismatch")
    return symbols


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _ranges(values: list[int]) -> list[list[int]]:
    if not values:
        return []
    ranges = []
    first = previous = values[0]
    for value in values[1:]:
        if value != previous + 1:
            ranges.append([first, previous])
            first = value
        previous = value
    ranges.append([first, previous])
    return ranges


def _fixed_records(rom: bytes, start: int, count: int, stride: int,
                   domain: str) -> list:
    require(count >= 0 and stride > 0, "invalid record dimensions")
    raw = read_span(rom, start, count * stride)
    return [decode_text(raw[i * stride:(i + 1) * stride], domain) for i in range(count)]


def _record_count(start: int, end: int, stride: int) -> int:
    require(end >= start and stride > 0 and (end - start) % stride == 0,
            "table span does not match record stride")
    return (end - start) // stride


def _lookup_name(records: list, index: int, reserved_zero: bool = True) -> dict:
    if type(index) is not int:
        return {"status": "UNSUPPORTED_ID", "id": index, "name": None}
    if index == 0 and reserved_zero:
        return {"status": EMPTY_OR_UNSET, "id": index, "name": None}
    if index < 0 or index >= len(records):
        return {"status": "UNSUPPORTED_ID", "id": index, "name": None}
    record = records[index]
    if record.status != VALID_TEXT or not record.text:
        return {"status": "UNSUPPORTED_RECORD", "record_status": record.status,
                "id": index, "name": None}
    return {"status": "STATIC_VERIFIED", "id": index, "name": record.text}


def _resolve_form_entry(species_id: int, form_id: int, species_count: int,
                        valid_name_ids: set[int], values: list[int] | None) -> dict:
    result = {"stored_species_id": species_id, "stored_form_id": form_id,
              "resolved_species_id": None, "status": "NOT_VERIFIED",
              "runtime_status": "NOT_VERIFIED"}
    if type(species_id) is int and species_id == 0:
        result["status"] = EMPTY_OR_UNSET
        return result
    if type(species_id) is not int or not (0 < species_id < species_count):
        result["status"] = "UNSUPPORTED_SPECIES_ID"
        return result
    if species_id not in valid_name_ids:
        result["status"] = "UNSUPPORTED_SPECIES_RECORD"
        return result
    if type(form_id) is not int or not (0 <= form_id < FORM_LIMIT):
        result["status"] = "UNSUPPORTED_FORM_ID"
        return result
    if values is None:
        resolved = species_id
    elif form_id >= len(values):
        result["status"] = "UNSUPPORTED_FORM_ID"
        return result
    else:
        resolved = values[form_id]
    if resolved not in valid_name_ids:
        result["status"] = "UNSUPPORTED_RESOLVED_SPECIES"
        return result
    result["resolved_species_id"] = resolved
    result["status"] = "STATIC_VERIFIED"
    return result


def _species_records(rom: bytes, symbols: dict) -> tuple[list, dict, list[int]]:
    start, end = symbols["gSpeciesNames"], symbols["gMoveNames"]
    require(end > start,
            "species-name symbol span does not match the verified stride")
    count = _record_count(start, end, SPECIES_STRIDE)
    require(0 < count <= 4096, "species-name table exceeds stored species field")
    records = _fixed_records(rom, start, count, SPECIES_STRIDE, "species_name")
    names = {i: record.text for i, record in enumerate(records)
             if i != 0 and record.status == VALID_TEXT}
    return records, names, [i for i, record in enumerate(records)
                            if record.status == EMPTY_OR_UNSET]


def _move_records(rom: bytes, start: int) -> tuple[list, dict, int]:
    # The party accessor stores ten-bit move IDs. Audit the entire representable
    # range; the safe name prefix ends at its first structurally invalid record.
    records = _fixed_records(rom, start, 1 << 10, MOVE_STRIDE, "move_name")
    prefix = _valid_text_prefix(records)
    require(prefix > 1, "move-name table has no verified move records")
    names = {i: records[i].text for i in range(1, prefix)}
    return records, names, prefix


def _valid_text_prefix(records: list) -> int:
    prefix = 0
    for record in records:
        if record.status != VALID_TEXT:
            break
        prefix += 1
    return prefix


def _form_tables(rom: bytes, species_records: list) -> tuple[dict, dict]:
    count = len(species_records)
    pointer_bytes = read_span(rom, FORM_POINTER_TABLE, count * 4)
    tables = {}
    payload = bytearray()
    mapped_ids = set()
    malformed = []
    alternate_ids = set()
    for species_id in range(count):
        pointer = struct.unpack_from("<I", pointer_bytes, species_id * 4)[0]
        if pointer == 0:
            continue
        values = []
        for form_id in range(FORM_LIMIT):
            value = read_u16(rom, pointer + form_id * 2)
            payload.extend(struct.pack("<H", value))
            if value == FORM_SENTINEL:
                break
            values.append(value)
            mapped_ids.add(value)
        else:
            malformed.append(species_id)
            continue
        tables[species_id] = values
        alternate_ids.update(values[1:])
        if not values or any(value >= count or species_records[value].status != VALID_TEXT
                             for value in values):
            malformed.append(species_id)
    return tables, {
        "pointer_table_sha256": _sha(pointer_bytes),
        "payload_sha256": _sha(bytes(payload)),
        "pointer_slots": count,
        "species_with_form_table": len(tables),
        "form_choices_including_form_zero": sum(map(len, tables.values())),
        "nonzero_form_choices": sum(max(0, len(values) - 1) for values in tables.values()),
        "unique_nonzero_form_species_ids": len(alternate_ids),
        "nonzero_form_species_id_ranges": _ranges(sorted(alternate_ids)),
        "unique_resolved_species_ids": len(mapped_ids),
        "resolved_species_id_ranges": _ranges(sorted(mapped_ids)),
        "malformed_species_ids": malformed,
    }


def resolve_form(rom: bytes, species_id: int, form_id: int,
                 species_records: list | None = None) -> dict:
    """Return a static form-table result; never claim runtime identity authority."""
    rom_identity(rom)
    if species_records is None:
        symbols = _require_symbols(rom)
        species_records, _, _ = _species_records(rom, symbols)
    count = len(species_records)
    valid_name_ids = {i for i, record in enumerate(species_records)
                      if i > 0 and record.status == VALID_TEXT and record.text}
    if (type(species_id) is not int or not (0 < species_id < count) or
            type(form_id) is not int or not (0 <= form_id < FORM_LIMIT)):
        return _resolve_form_entry(species_id, form_id, count, valid_name_ids, None)
    pointer = read_u32(rom, FORM_POINTER_TABLE + species_id * 4)
    values = None
    if pointer:
        values = []
        for index in range(FORM_LIMIT):
            value = read_u16(rom, pointer + index * 2)
            if value == FORM_SENTINEL:
                break
            values.append(value)
        else:
            return {"stored_species_id": species_id, "stored_form_id": form_id,
                    "resolved_species_id": None, "status": "UNSUPPORTED_FORM_TABLE",
                    "runtime_status": "NOT_VERIFIED"}
    return _resolve_form_entry(species_id, form_id, count, valid_name_ids, values)


def lookup_species(rom: bytes, species_id: int) -> dict:
    """Exact-ROM lookup. Unknown, reserved and hole IDs never fall back globally."""
    rom_identity(rom)
    records, _, _ = _species_records(rom, _require_symbols(rom))
    return _lookup_name(records, species_id)


def lookup_move(rom: bytes, move_id: int) -> dict:
    """Exact-ROM lookup limited to the contiguous, terminated move-name prefix."""
    rom_identity(rom)
    symbols = _require_symbols(rom)
    records, names, prefix = _move_records(rom, symbols["gMoveNames"])
    if type(move_id) is not int or move_id < 0 or move_id > 1023:
        return {"status": "UNSUPPORTED_MOVE_ID", "id": move_id, "name": None,
                "safe_max_id": prefix - 1}
    if move_id == 0:
        return {"status": EMPTY_OR_UNSET, "id": 0, "name": None}
    if move_id not in names:
        return {"status": "UNSUPPORTED_MOVE_ID", "id": move_id, "name": None,
                "safe_max_id": prefix - 1}
    return _lookup_name(records, move_id, reserved_zero=False)


def lookup_party_identity(rom: bytes, stored_species_id: int, stored_form_id: int) -> dict:
    """Keep stored values, static form result, display name and metadata key distinct."""
    rom_identity(rom)
    symbols = _require_symbols(rom)
    records, _, _ = _species_records(rom, symbols)
    form = resolve_form(rom, stored_species_id, stored_form_id, records)
    resolved = form["resolved_species_id"]
    name = _lookup_name(records, resolved) if resolved is not None else {
        "status": form["status"], "id": None, "name": None}
    return {
        "raw_stored_species_id": stored_species_id,
        "raw_form_id": stored_form_id,
        "static_resolved_species_id": resolved,
        "static_name_candidate": name["name"],
        "metadata_lookup_key": resolved,
        "static_status": form["status"],
        "runtime_effective_identity_status": "NOT_VERIFIED",
        "runtime_display_name": None,
    }


def _verify_static_accessors(rom: bytes, symbols: dict) -> None:
    """Pin the exact Thumb load/index instructions behind claimed record fields."""
    require(read_u16(rom, 0x08016970) == 0x200D and
            read_u16(rom, 0x08016972) == 0x4341 and
            read_u32(rom, 0x080169A8) == symbols["gSpeciesNames"],
            "species-name accessor stride/table pointer changed")
    require(read_u16(rom, 0x080782CC) == 0x0101 and
            read_u16(rom, 0x080782CE) == 0x1809 and
            read_u32(rom, 0x08078310) == symbols["gMoveNames"],
            "move-name accessor stride/table pointer changed")

    require(read_u32(rom, 0x080A1890) == FORM_POINTER_TABLE and
            read_span(rom, 0x080A1880, 10) == bytes.fromhex("900040180168002904d1") and
            read_span(rom, 0x080A1894, 6) == bytes.fromhex("580040180088"),
            "form-species accessor pointer/stride changed")

    require(read_u32(rom, 0x080A1940) == symbols["gBaseStats"],
            "base-stats accessor table pointer mismatch")
    require(read_span(rom, 0x080A192C, 10) == bytes.fromhex("a1010918087ba8420fd0") and
            read_u16(rom, 0x080A1936) == 0x7B48,
            "base-stats accessor stride/type offsets changed")

    require(read_u32(rom, 0x08044100) == symbols["gBattleMoves"],
            "battle-move power accessor table pointer mismatch")
    require([read_u16(rom, address) for address in
             (0x080440A2, 0x080440A4, 0x080440A6, 0x080440A8, 0x080440AA)] ==
            [0x00F0, 0x1B80, 0x00C0, 0x1840, 0x7884],
        "battle-move power accessor stride/offset changed")
    require(read_u32(rom, 0x080422F4) == symbols["gBattleMoves"],
            "battle-move type accessor table pointer mismatch")
    require([read_u16(rom, address) for address in
             (0x0804225E, 0x08042260, 0x08042262, 0x08042264, 0x08042266)] ==
            [0x00E0, 0x1B00, 0x00C0, 0x1840, 0x78C4],
            "battle-move type accessor stride/offset changed")

    require(read_u16(rom, 0x08128390) == 999 and
            read_u32(rom, 0x081283BC) == symbols["gItems"] and
            [read_u16(rom, address) for address in
             (0x081283AC, 0x081283AE, 0x081283B0, 0x081283B2, 0x081283B4)] ==
            [0x00C8, 0x1A40, 0x00C0, 0x4902, 0x1840],
            "item accessor clamp/stride/table pointer changed")


def _inspect_secondary(rom: bytes, symbols: dict, species_records: list,
                       species_names: dict, move_names: dict) -> dict:
    species_count = len(species_records)
    base_span = read_span(rom, symbols["gBaseStats"], species_count * BASE_STATS_STRIDE)
    type_ids = Counter()
    for species_id, name in species_names.items():
        record = base_span[species_id * BASE_STATS_STRIDE:(species_id + 1) * BASE_STATS_STRIDE]
        type_pair = (record[0x0C], record[0x0D])
        require(all(type_id < TYPE_COUNT for type_id in type_pair),
                f"base-stat type field outside 19-type space at species ID {species_id}")
        type_ids.update(type_pair)

    move_count = len(move_names) + 1
    battle_span = read_span(rom, symbols["gBattleMoves"], move_count * BATTLE_MOVE_STRIDE)
    move_type_counts = Counter()
    for move_id in move_names:
        record = battle_span[move_id * BATTLE_MOVE_STRIDE:(move_id + 1) * BATTLE_MOVE_STRIDE]
        require(record[3] < TYPE_COUNT, f"move type outside 19-type space at move ID {move_id}")
        move_type_counts[record[3]] += 1

    # Exact-ROM accessor at 0x0808128380 clamps item IDs above 999 and scales
    # accepted IDs by 56. Validate that entire bounded data span and samples.
    item_count = 1000
    item_span = read_span(rom, symbols["gItems"], item_count * ITEM_STRIDE)
    item_checks = {}
    item_id_matches = []
    item_name_statuses = Counter()
    for item_id in range(item_count):
        record = item_span[item_id * ITEM_STRIDE:(item_id + 1) * ITEM_STRIDE]
        if struct.unpack_from("<H", record, 0x12)[0] == item_id:
            item_id_matches.append(item_id)
        item_name_statuses[decode_text(record[:18], "item_name").status] += 1
    for item_id in (1, 28, 90, 214, 467):
        record = item_span[item_id * ITEM_STRIDE:(item_id + 1) * ITEM_STRIDE]
        name = decode_text(record[:18], "item_name")
        desc_ptr = struct.unpack_from("<I", record, 0x1C)[0]
        pocket = record[0x22]
        require(struct.unpack_from("<H", record, 0x12)[0] == item_id,
                f"item ID field mismatch at item ID {item_id}")
        require(ROM_BASE <= desc_ptr < ROM_BASE + ROM_SIZE,
                f"item description pointer outside ROM at item ID {item_id}")
        item_checks[str(item_id)] = {
            "name_status": name.status,
            "record_id_matches_index": True,
            "description_pointer_in_rom": True,
            "pocket_value": pocket,
        }

    # The table reference is used by the battle accessor at 0x0805915c.
    chart_address = 0x0842C182
    chart_bytes = TYPE_COUNT * TYPE_COUNT * 2
    normal_chart = read_span(rom, chart_address, chart_bytes)
    alternate_chart = read_span(rom, chart_address + chart_bytes, chart_bytes)
    chart_values = Counter(struct.unpack_from("<H", normal_chart, i)[0]
                           for i in range(0, chart_bytes, 2))

    # Region-map record count is retained as the pinned third-party lead. The
    # extractor validates pointers in that window without publishing names.
    map_start = symbols["gRegionMapEntries"]
    map_bytes = read_span(rom, map_start, MAP_ENTRY_LEAD_COUNT * MAP_ENTRY_STRIDE)
    map_pointer_count = 0
    map_pointer_invalid = 0
    map_name_status = Counter()
    map_unsupported_ids = []
    map_name_payload = bytearray()
    for index in range(MAP_ENTRY_LEAD_COUNT):
        pointer = struct.unpack_from("<I", map_bytes, index * MAP_ENTRY_STRIDE + 4)[0]
        if pointer:
            if ROM_BASE <= pointer < ROM_BASE + ROM_SIZE:
                map_pointer_count += 1
                offset = pointer - ROM_BASE
                end = rom.find(b"\xff", offset, min(len(rom), offset + 64))
                if end < 0:
                    map_name_status["INVALID_TERMINATION"] += 1
                    map_name_ids["INVALID_TERMINATION"].append(index)
                else:
                    encoded = rom[offset:end + 1]
                    decoded = decode_text(encoded, "map_section_name")
                    map_name_status[decoded.status] += 1
                    if decoded.status != VALID_TEXT:
                        map_unsupported_ids.append(index)
                    map_name_payload.extend(struct.pack("<H", index))
                    map_name_payload.extend(encoded)
            else:
                map_pointer_invalid += 1

    return {
        "base_stats": {
            "status": "STATIC_VERIFIED",
            "start": f"0x{symbols['gBaseStats']:08x}",
            "record_index_bounds": [0, species_count - 1],
            "record_stride": BASE_STATS_STRIDE,
            "record_count_bound": species_count,
            "type_offsets": [12, 13],
            "named_species_type_fields_checked": len(species_names),
            "type_id_values": sorted(type_ids),
            "region_sha256": _sha(base_span),
            "ability_live_slot_semantics": "NOT_VERIFIED",
        },
        "battle_moves": {
            "status": "STATIC_VERIFIED_NAMED_PREFIX",
            "start": f"0x{symbols['gBattleMoves']:08x}",
            "named_move_id_bounds": [1, max(move_names)],
            "record_stride": BATTLE_MOVE_STRIDE,
            "name_matched_records": len(move_names),
            "static_power_offset": 2,
            "static_type_offset": 3,
            "type_values": sorted(move_type_counts),
            "region_sha256": _sha(battle_span),
            "effective_battle_values": "NOT_VERIFIED",
        },
        "items": {
            "status": "STATIC_VERIFIED_BOUNDED_SUBSET",
            "start": f"0x{symbols['gItems']:08x}",
            "accessor_index_bounds": [0, item_count - 1],
            "record_stride": ITEM_STRIDE,
            "name_width": 18,
            "item_id_offset": 18,
            "description_pointer_offset": 28,
            "pocket_offset": 34,
            "item_ids_matching_record_index": len(item_id_matches),
            "item_id_mismatch_ranges": _ranges(sorted(set(range(item_count)) - set(item_id_matches))),
            "inline_name_status_counts": dict(sorted(item_name_statuses.items())),
            "sample_checks": item_checks,
            "region_sha256": _sha(item_span),
            "item_effects": "NOT_VERIFIED",
        },
        "type_effectiveness": {
            "status": "STATIC_VERIFIED_TABLE_AND_CONDITIONAL_ALTERNATE",
            "normal_start": f"0x{chart_address:08x}",
            "alternate_start": f"0x{chart_address + chart_bytes:08x}",
            "dimensions": [TYPE_COUNT, TYPE_COUNT],
            "cell_format": "u16 Q4.12",
            "normal_region_sha256": _sha(normal_chart),
            "alternate_region_sha256": _sha(alternate_chart),
            "normal_value_counts": {f"0x{k:04x}": v for k, v in sorted(chart_values.items())},
            "alternate_path_trigger": "NOT_VERIFIED",
            "calculator_claim": "NOT_IN_SCOPE",
        },
        "map_sections": {
            "status": "THIRD_PARTY_LEAD_WITH_BOUNDED_POINTER_CHECKS",
            "start": f"0x{map_start:08x}",
            "candidate_record_count": MAP_ENTRY_LEAD_COUNT,
            "record_stride": MAP_ENTRY_STRIDE,
            "nonzero_name_pointers_in_rom": map_pointer_count,
            "invalid_name_pointers": map_pointer_invalid,
            "bounded_name_status_counts": dict(sorted(map_name_status.items())),
            "unsupported_name_entry_ids": map_unsupported_ids,
            "bounded_name_payload_sha256": _sha(bytes(map_name_payload)),
            "region_sha256": _sha(map_bytes),
            "region_mode_and_runtime_selection": "NOT_VERIFIED",
        },
    }


def inspect_rom(rom: bytes) -> dict:
    rom_info = rom_identity(rom)
    symbols = _require_symbols(rom)
    _verify_static_accessors(rom, symbols)
    species_records, species_names, empty_species = _species_records(rom, symbols)
    move_records, move_names, move_prefix = _move_records(rom, symbols["gMoveNames"])
    forms, form_summary = _form_tables(rom, species_records)

    require(not form_summary["malformed_species_ids"], "malformed form table entries")
    for species_id, expected in DISPLAY_CHECKS["species"].items():
        require(species_names.get(species_id) == expected,
                f"observed species display cross-check failed at ID {species_id}")
    for move_id, expected in DISPLAY_CHECKS["moves"].items():
        require(move_names.get(move_id) == expected,
                f"observed move display cross-check failed at ID {move_id}")

    name_start, name_end = symbols["gSpeciesNames"], symbols["gMoveNames"]
    species_region = read_span(rom, name_start, name_end - name_start)
    safe_move_region = read_span(rom, symbols["gMoveNames"], move_prefix * MOVE_STRIDE)
    forms_for_display = {
        str(species_id): resolve_form(rom, species_id, 0, species_records)
        for species_id in DISPLAY_CHECKS["species"]
    }
    identity_crosschecks = {}
    for species_id, expected_name in DISPLAY_CHECKS["species"].items():
        identity_crosschecks[str(species_id)] = {
            "raw_stored_species_id": species_id,
            "raw_form_id": 0,
            "static_resolved_species_id": forms_for_display[str(species_id)]["resolved_species_id"],
            "displayed_name": expected_name,
            "metadata_lookup_key": forms_for_display[str(species_id)]["resolved_species_id"],
            "runtime_evidence": "RUNTIME_OBSERVED_IN_PARTY_LAYOUT_EVIDENCE",
        }
    move_crosschecks = {
        str(move_id): {"raw_stored_move_id": move_id, "displayed_name": name,
                       "metadata_lookup_key": move_id,
                       "effective_battle_behavior": "NOT_VERIFIED"}
        for move_id, name in DISPLAY_CHECKS["moves"].items()
    }
    secondary = _inspect_secondary(rom, symbols, species_records, species_names, move_names)
    tool_paths = [Path(__file__), Path(__file__).with_name("rowe_text.py"),
                  Path(__file__).with_name("rowe_symbols.py")]
    text_status_counts = Counter(record.status for record in species_records)
    move_status_counts = Counter(record.status for record in move_records)
    species_names_count = len(species_names)
    return {
        "schema_version": 1,
        "release": "R.O.W.E. 2.1.9.1 Experimental",
        "status": "STATIC_VERIFIED_EXACT_ROM_EXTRACTION",
        "rom_identity": rom_info,
        "source_provenance": {
            "exact_release_source_elf": "NOT_VERIFIED",
            "table_reference": "THIRD_PARTY_LEAD",
            "pokedaisy_commit": "eddffd90979ec12e6425e1d96c7aca41d5783b61",
            "pokedaisy_license": "GPL-3.0",
        },
        "tools_sha256": {path.name: _sha(path.read_bytes()) for path in tool_paths},
        "static_accessor_spans": {
            name: {"start": f"0x{start:08x}", "end_exclusive": f"0x{end:08x}",
                   "sha256": _sha(read_span(rom, start, end - start))}
            for name, (start, end) in STATIC_ACCESSOR_SPANS.items()
        },
        "embedded_symbols": {
            "status": "STATIC_VERIFIED",
            "table_address": f"0x{SYMBOL_TABLE:08x}",
            "entry_count": SYMBOL_COUNT,
            "data_symbols": {name: f"0x{symbols[name]:08x}" for name in
                             ("gSpeciesNames", "gMoveNames", "gBattleMoves", "gBaseStats",
                              "gItems", "gRegionMapEntries")},
            "form_table_symbol_name": "NOT_PRESENT_IN_150_ENTRY_SYMBOL_TABLE",
        },
        "species_names": {
            "status": "STATIC_VERIFIED",
            "start": f"0x{name_start:08x}",
            "end_exclusive": f"0x{name_end:08x}",
            "record_stride": SPECIES_STRIDE,
            "record_count": len(species_records),
            "valid_name_count_including_reserved_id_zero": text_status_counts[VALID_TEXT],
            "record_index_bounds": [0, len(species_records) - 1],
            "safe_lookup_id_count": len(species_names),
            "empty_slot_count": len(empty_species),
            "empty_slot_ranges": _ranges(empty_species),
            "name_table_region_sha256": _sha(species_region),
            "ui_correlated_examples": {str(k): v for k, v in DISPLAY_CHECKS["species"].items()},
            "stored_id_and_form_are_separate": True,
            "identity_contract_examples": identity_crosschecks,
            "egg_identity": "NOT_VERIFIED",
        },
        "move_names": {
            "status": "STATIC_VERIFIED_SAFE_PREFIX",
            "start": f"0x{symbols['gMoveNames']:08x}",
            "record_stride": MOVE_STRIDE,
            "stored_id_width_bits": 10,
            "scanned_stored_id_range": [0, (1 << 10) - 1],
            "safe_name_ids": [1, move_prefix - 1],
            "first_invalid_record_id": move_prefix,
            "post_boundary_status_counts": {
                key: value for key, value in sorted(move_status_counts.items())
                if key != VALID_TEXT
            },
            "safe_name_region_sha256": _sha(safe_move_region),
            "ui_correlated_examples": {str(k): v for k, v in DISPLAY_CHECKS["moves"].items()},
            "identity_contract_examples": move_crosschecks,
            "stored_id_vs_effective_battle_behavior": "NOT_VERIFIED",
        },
        "forms": {
            "status": "STATIC_VERIFIED_MAPPING_ALGORITHM_RUNTIME_NOT_VERIFIED",
            "candidate_pointer_table": f"0x{FORM_POINTER_TABLE:08x}",
            **form_summary,
            "algorithm": "species_id * 4 pointer; form_id * 2 u16; 0xffff list sentinel; null pointer returns stored species ID",
            "stored_form_width_bits": 5,
            "runtime_nonzero_form_observation": "NOT_VERIFIED",
            "observed_form_zero_examples": forms_for_display,
            "name_and_metadata_key": "resolved_species_id, only when the static list entry is in the verified name range",
            "alternate_species_id_ranges": form_summary["nonzero_form_species_id_ranges"],
            "example_nonzero_form_static_mapping": {
                "raw_stored_species_id": 19,
                "raw_form_id": 1,
                "static_resolved_species_id": 1551,
                "static_name_candidate": "Rattata",
                "base_type_ids": [17, 0],
                "runtime_effective_identity_status": "NOT_VERIFIED",
            },
            "invalid_form_behavior": "REFUSE; game accessor does not bounds-check the form list",
        },
        "nickname_text": {
            "status": "STATIC_WIDTH_AND_SYNTHETIC_DECODER",
            "stored_width_bytes": 12,
            "terminator": "0xff when present; trailing 0x00/0xff padding retained as raw bytes",
            "full_width_unterminated": "accepted only for 12 recognized glyph bytes; RUNTIME_OBSERVED full-width case NOT_VERIFIED",
            "ui_max_displayed_characters": "NOT_VERIFIED",
            "observed_raw_examples": {
                "Sentret": "cdd9e2e8e6d9e8ff000000",
                "Fletchling": "c0e0d9e8d7dce0dde2dbff00",
            },
            "egg_name_handling": "NOT_VERIFIED",
            "control_sequences": "refused; no control tokens are silently removed",
            "decoder_domains": ["species_name", "move_name", "item_name", "party_nickname"],
            "decoder_statuses": ["VALID_TEXT", "EMPTY_OR_UNSET", "UNKNOWN_GLYPH",
                                 "MALFORMED_CONTROL", "INVALID_TERMINATION", "UNSUPPORTED_MODE"],
        },
        "secondary_tables": secondary,
        "stored_effective_semantics": {
            "nature": "stored 5-bit field STATIC_VERIFIED; mode-dependent effective nature NOT_VERIFIED",
            "species_form": "static mapping algorithm verified; nonzero-form runtime NOT_VERIFIED",
            "moves": "stored 10-bit IDs verified; accessor has conditional paths with unknown trigger",
            "level": "stored byte verified; accessor has conditional path with unknown trigger",
            "abilities": "live slot, suppression and mode behavior NOT_VERIFIED",
            "unsupported_mode": "REFUSE_EFFECTIVE_VALUE",
        },
        "safe_future_display": {
            "runtime_observed_species_form_zero": [161, 661],
            "runtime_observed_move_ids": sorted(DISPLAY_CHECKS["moves"]),
            "other_table_names": "static lookup only; no production global fallback",
            "nonzero_forms": "REFUSE until runtime observation",
            "unknown_or_hole_ids": "REFUSE",
            "mode_dependent_effective_values": "REFUSE",
        },
        "pokedaisy_comparison": {
            "species_name_address_and_stride": "SUPPORTED_INDEPENDENTLY",
            "move_name_address_and_stride": "SUPPORTED_INDEPENDENTLY; safe names only IDs 1..942",
            "base_stats_address_stride_and_type_offsets": "SUPPORTED_INDEPENDENTLY",
            "battle_moves_address_stride_and_power_type_offsets": "SUPPORTED_INDEPENDENTLY",
            "form_pointer_address_and_accessor_algorithm": "SUPPORTED_INDEPENDENTLY; form table absent as named embedded symbol",
            "form_output_start_1551": "CONTRADICTED; bounded nonzero-form outputs include 1501",
            "items_address_stride_and_index_clamp": "SUPPORTED_INDEPENDENTLY; individual item IDs still require record/name validation",
            "type_effectiveness_normal_table": "SUPPORTED_INDEPENDENTLY; alternate path trigger NOT_VERIFIED",
            "region_map_entry_count_213": "THIRD_PARTY_LEAD; record-count bound lacks independent accessor proof",
            "pokedaisy_stop_after_empty_names": "NOT_USED; full 10-bit move-ID space is scanned",
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", required=True)
    args = parser.parse_args()
    try:
        rom = read_local(args.rom, MAX_ROM)
        report = inspect_rom(rom)
        print(json.dumps(report, sort_keys=True, indent=2))
    except (OSError, ValueError, struct.error) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
