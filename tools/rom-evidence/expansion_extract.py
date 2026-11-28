#!/usr/bin/env python3
"""Host-only, read-only pokeemerald-expansion ROM table locator and sanity verifier.

Detects the RHHEXP / RHH_EXP expansion marker, parses the GF ROM Header at
0x08000100 (file offset 0x100) for gSpeciesInfo, gMovesInfo, gItemsInfo, and
gTypesInfo / type chart pointers, and validates bounded strides and structural
sanity probes.

Clean-room MIT implementation. Emits only sanitized numeric/hash JSON metadata;
never extracts or prints species, move, item, or story text.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

from ups_identity import MAX_ROM, read_local, require

ROM_BASE = 0x08000000
ROM_END_CAP = 0x0A000000
GF_HEADER_OFFSET = 0x100
GF_HEADER_END = 0x200
EXPANSION_MARKERS = (b"RHHEXP", b"RHH_EXP")
MAX_TABLE_COUNT = 4096

# Standard GF ROM Header pointer offsets inside [0x100, 0x200).
GF_SLOT_SPECIES_INFO = 0x1BC
GF_SLOT_ITEMS_INFO = 0x1C8
GF_SLOT_MOVES_INFO = 0x1CC
GF_TYPES_INFO_CANDIDATE_SLOTS = (0x1EC, 0x1D8, 0x1DC, 0x1E0, 0x1E4, 0x1E8, 0x1F0, 0x1F4, 0x1F8, 0x1FC)

# Upstream pokeemerald-expansion `enum Type` (include/constants/pokemon.h):
# TYPE_NONE=0, TYPE_NORMAL=1, ..., TYPE_STEEL=9, TYPE_MYSTERY=10,
# TYPE_FIRE=11, TYPE_WATER=12, TYPE_GRASS=13, ..., TYPE_FAIRY=19, TYPE_STELLAR=20.
TYPE_NONE = 0
TYPE_NORMAL = 1
TYPE_FIGHTING = 2
TYPE_FLYING = 3
TYPE_POISON = 4
TYPE_GROUND = 5
TYPE_ROCK = 6
TYPE_BUG = 7
TYPE_GHOST = 8
TYPE_STEEL = 9
TYPE_MYSTERY = 10
TYPE_FIRE = 11
TYPE_WATER = 12
TYPE_GRASS = 13
TYPE_ELECTRIC = 14
TYPE_PSYCHIC = 15
TYPE_ICE = 16
TYPE_DRAGON = 17
TYPE_DARK = 18
TYPE_FAIRY = 19
TYPE_STELLAR = 20

# Vanilla Gen 3 type IDs (for GEN3_VANILLA_BYTE layout fallback only).
GEN3_TYPE_POISON = 3
GEN3_TYPE_FIRE = 10
GEN3_TYPE_GRASS = 12

LAYOUT_EXPANSION_V1_9_BITFIELD = "EXPANSION_V1_9_BITFIELD"
LAYOUT_GEN3_VANILLA_BYTE = "GEN3_VANILLA_BYTE"

# Explicitly pinned record layouts; unknown layout versions fail closed.
SUPPORTED_LAYOUTS = {
    LAYOUT_EXPANSION_V1_9_BITFIELD: {
        "description": "pokeemerald-expansion v1.9+ packed MoveInfo bitfields + pointer-backed ItemInfo",
        "move_min_stride": 16,
        "move_max_stride": 128,
        "move_name_ptr_offset": 0x00,
        "move_desc_ptr_offset": 0x04,
        "move_combat_word_offset": 0x0A,    # u16: type:5 | category:2 | power:9
        "move_accuracy_word_offset": 0x0C,  # u16: accuracy:7 | target:9
        "move_pp_offset": 0x0E,             # u8
        "item_min_stride": 44,
        "item_max_stride": 128,
        "item_name_is_pointer": True,
        "item_name_offset": 0x14,           # const u8 *name
        "item_desc_ptr_offset": 0x0C,       # const u8 *description
        "type_id_min": 1,
        "type_id_max": 20,
    },
    LAYOUT_GEN3_VANILLA_BYTE: {
        "description": "Vanilla/pre-bitfield Gen 3 BattleMove byte fields + inline-name Item",
        "move_min_stride": 12,
        "move_max_stride": 128,
        "move_power_offset": 0x01,          # u8
        "move_type_offset": 0x02,           # u8
        "move_accuracy_offset": 0x03,       # u8
        "move_pp_offset": 0x04,             # u8
        "item_min_stride": 24,
        "item_max_stride": 128,
        "item_name_is_pointer": False,
        "item_name_offset": 0x00,           # u8 name[14]
        "item_name_len": 14,
        "item_desc_ptr_offset": 0x14,       # const u8 *description
        "type_id_min": 0,
        "type_id_max": 20,
    },
}

_GEN3_GLYPHS = (
    {0x00, 0x1B, 0xAB, 0xAC, 0xAD, 0xAE, 0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB8, 0xBA, 0xF0}
    | set(range(0xA1, 0xAB))
    | set(range(0xBB, 0xD5))
    | set(range(0xD5, 0xEF))
)
_GEN3_PADDING = {0x00, 0xFF}


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _u16(rom: bytes, offset: int) -> int:
    return struct.unpack_from("<H", rom, offset)[0]


def _u32(rom: bytes, offset: int) -> int:
    return struct.unpack_from("<I", rom, offset)[0]


def is_valid_rom_pointer(address: int, rom_size: int) -> bool:
    """True iff `address` falls inside [0x08000200, 0x0A000000) and within `rom_size`."""
    return (ROM_BASE + GF_HEADER_END) <= address < ROM_END_CAP and (address - ROM_BASE) < rom_size


def _valid_gen3_terminated(raw: bytes, *, allow_empty: bool = False, allow_newline: bool = False) -> bool:
    if not raw or 0xFF not in raw:
        return False
    term = raw.index(0xFF)
    if term == 0:
        return allow_empty and all(b in _GEN3_PADDING for b in raw[1:])
    if any(b not in _GEN3_PADDING for b in raw[term + 1:]):
        return False
    glyphs = _GEN3_GLYPHS | ({0xFE} if allow_newline else set())
    return all(b in glyphs for b in raw[:term])


def _valid_gen3_pointer_text(
    rom: bytes,
    ptr: int,
    *,
    allow_empty: bool = False,
    allow_newline: bool = False,
    max_scan: int = 256,
) -> bool:
    if not is_valid_rom_pointer(ptr, len(rom)):
        return False
    off = ptr - ROM_BASE
    span = rom[off:min(len(rom), off + max_scan)]
    if 0xFF not in span:
        return False
    return _valid_gen3_terminated(
        span[:span.index(0xFF) + 1],
        allow_empty=allow_empty,
        allow_newline=allow_newline,
    )


def find_expansion_marker(rom: bytes) -> dict | None:
    """Locate the RHHEXP / RHH_EXP marker in `rom`, reporting only offset and header-window status."""
    for marker in EXPANSION_MARKERS:
        offset = rom.find(marker)
        if offset >= 0:
            return {
                "marker": marker.decode("ascii"),
                "file_offset": f"0x{offset:08x}",
                "in_rom_header_window": GF_HEADER_OFFSET <= offset < GF_HEADER_END,
            }
    return None


def _is_plausible_species_row(entry: bytes, layout_spec: dict, *, allow_zero: bool = False) -> bool:
    stats = entry[:6]
    if all(b == 0 for b in entry):
        return allow_zero
    if any(b == 0 for b in stats):
        return False
    t1_min = layout_spec["type_id_min"]
    t_max = layout_spec["type_id_max"]
    return t1_min <= entry[6] <= t_max and 0 <= entry[7] <= t_max


def _probe_species_info(rom: bytes, offset: int, layout_name: str) -> tuple[bool, int, int, str | None]:
    """Detect struct SpeciesInfo stride (28..256, 4-byte aligned) and validate species 1+."""
    layout_spec = SUPPORTED_LAYOUTS[layout_name]
    if layout_name == LAYOUT_EXPANSION_V1_9_BITFIELD:
        exp_grass, exp_poison, exp_fire = TYPE_GRASS, TYPE_POISON, TYPE_FIRE
    else:
        exp_grass, exp_poison, exp_fire = GEN3_TYPE_GRASS, GEN3_TYPE_POISON, GEN3_TYPE_FIRE

    best_stride = None
    best_score = -1

    for stride in range(28, 257, 4):
        if offset + 12 * stride > len(rom):
            continue
        rows = [rom[offset + i * stride:offset + (i + 1) * stride] for i in range(1, 11)]
        if not all(_is_plausible_species_row(r, layout_spec) for r in rows):
            continue
        # Require non-trivial stat sum for species 1.
        if sum(rows[0][:6]) < 150:
            continue
        score = 1
        # Species 1..3 Grass/Poison (Bulbasaur/Ivysaur/Venusaur) and Species 4 Fire (Charmander).
        if rows[0][6] == exp_grass and rows[0][7] == exp_poison:
            score += 4
        if rows[1][6] == exp_grass and rows[1][7] == exp_poison:
            score += 2
        if rows[2][6] == exp_grass and rows[2][7] == exp_poison:
            score += 2
        if rows[3][6] == exp_fire and rows[3][7] in (exp_fire, TYPE_NONE):
            score += 2
        if score > best_score:
            best_score = score
            best_stride = stride

    if best_stride is None or best_score < 5:
        return False, 0, 0, "species_sanity_probe_failed"

    avail = min((len(rom) - offset) // best_stride, MAX_TABLE_COUNT)
    last_nonzero = 0
    zero_run = 0
    for i in range(avail):
        entry = rom[offset + i * best_stride:offset + (i + 1) * best_stride]
        if not _is_plausible_species_row(entry, layout_spec, allow_zero=True):
            break
        if all(b == 0 for b in entry):
            if i > 0:
                zero_run += 1
                if zero_run > 32:
                    break
        else:
            zero_run = 0
            last_nonzero = i
    count = last_nonzero + 1
    if count < 10:
        return False, best_stride, 0, "count_below_minimum"
    return True, best_stride, count, None


def _decode_move_fields(rom: bytes, row: bytes, layout_name: str) -> tuple[bool, int, int, int, int, int]:
    """Decode (valid, power, move_type, category, accuracy, pp) for a pinned MoveInfo layout."""
    spec = SUPPORTED_LAYOUTS[layout_name]
    if layout_name == LAYOUT_EXPANSION_V1_9_BITFIELD:
        combat_off = spec["move_combat_word_offset"]
        acc_off = spec["move_accuracy_word_offset"]
        pp_off = spec["move_pp_offset"]
        if pp_off >= len(row) or acc_off + 2 > len(row) or combat_off + 2 > len(row):
            return False, 0, 0, 0, 0, 0
        combat_word = struct.unpack_from("<H", row, combat_off)[0]
        move_type = combat_word & 0x1F
        category = (combat_word >> 5) & 0x03
        power = (combat_word >> 7) & 0x1FF
        acc_word = struct.unpack_from("<H", row, acc_off)[0]
        accuracy = acc_word & 0x7F
        pp = row[pp_off]
        return True, power, move_type, category, accuracy, pp
    if layout_name == LAYOUT_GEN3_VANILLA_BYTE:
        p_off = spec["move_power_offset"]
        t_off = spec["move_type_offset"]
        a_off = spec["move_accuracy_offset"]
        pp_off = spec["move_pp_offset"]
        if max(p_off, t_off, a_off, pp_off) >= len(row):
            return False, 0, 0, 0, 0, 0
        power = row[p_off]
        move_type = row[t_off]
        accuracy = row[a_off]
        pp = row[pp_off]
        category = 2 if power == 0 else 0
        return True, power, move_type, category, accuracy, pp
    return False, 0, 0, 0, 0, 0


def _valid_move_row_for_layout(rom: bytes, row: bytes, layout_name: str, *, allow_zero: bool = False) -> bool:
    if all(b == 0 for b in row):
        return allow_zero
    spec = SUPPORTED_LAYOUTS[layout_name]
    ok, _, move_type, category, accuracy, pp = _decode_move_fields(rom, row, layout_name)
    if not ok:
        return False
    if not (spec["type_id_min"] <= move_type <= spec["type_id_max"]):
        return False
    if category > 2 or accuracy > 100 or pp > 64:
        return False
    if layout_name == LAYOUT_EXPANSION_V1_9_BITFIELD:
        name_ptr = struct.unpack_from("<I", row, spec["move_name_ptr_offset"])[0]
        desc_ptr = struct.unpack_from("<I", row, spec["move_desc_ptr_offset"])[0]
        if not _valid_gen3_pointer_text(rom, name_ptr, max_scan=64):
            return False
        if not _valid_gen3_pointer_text(rom, desc_ptr, allow_newline=True, max_scan=256):
            return False
    return True


def _probe_moves_info(rom: bytes, offset: int, layout_name: str) -> tuple[bool, int, int, str | None]:
    """Detect gMovesInfo / gBattleMoves stride using pinned layout rules and verify move anchors."""
    spec = SUPPORTED_LAYOUTS[layout_name]
    expected_normal = TYPE_NORMAL if layout_name == LAYOUT_EXPANSION_V1_9_BITFIELD else 0

    for stride in range(spec["move_min_stride"], spec["move_max_stride"] + 1, 4):
        if offset + 36 * stride > len(rom):
            continue

        sample_ok = True
        for idx in range(1, 36):
            row = rom[offset + idx * stride:offset + (idx + 1) * stride]
            if not _valid_move_row_for_layout(rom, row, layout_name):
                sample_ok = False
                break
        if not sample_ok:
            continue

        # Verify canonical move anchors: Pound(1,40), Swords Dance(14,0), Sand Attack(28,0), Tackle(33,40).
        anchor_ok = True
        for mid, exp_power, exp_cat in ((1, 40, 0), (14, 0, 2), (28, 0, 2), (33, 40, 0)):
            row = rom[offset + mid * stride:offset + (mid + 1) * stride]
            ok, power, move_type, category, _, _ = _decode_move_fields(rom, row, layout_name)
            if not ok or power != exp_power:
                anchor_ok = False
                break
            if layout_name == LAYOUT_EXPANSION_V1_9_BITFIELD and category != exp_cat:
                anchor_ok = False
                break
            if mid in (1, 33) and move_type != expected_normal:
                anchor_ok = False
                break
        if not anchor_ok:
            continue

        avail = min((len(rom) - offset) // stride, MAX_TABLE_COUNT)
        last_nonzero = 0
        zero_run = 0
        for i in range(avail):
            row = rom[offset + i * stride:offset + (i + 1) * stride]
            if not _valid_move_row_for_layout(rom, row, layout_name, allow_zero=(i == 0 or zero_run < 8)):
                break
            if all(b == 0 for b in row):
                if i > 0:
                    zero_run += 1
                    if zero_run > 8:
                        break
            else:
                zero_run = 0
                last_nonzero = i
        count = last_nonzero + 1
        if count >= 34:
            return True, stride, count, None

    return False, 0, 0, "moves_sanity_probe_failed"


def _valid_item_entry(rom: bytes, entry: bytes, layout_name: str, *, allow_empty: bool = False) -> bool:
    if allow_empty and all(b == 0 for b in entry):
        return True
    spec = SUPPORTED_LAYOUTS[layout_name]
    desc_off = spec["item_desc_ptr_offset"]
    if desc_off + 4 > len(entry):
        return False
    desc_ptr = struct.unpack_from("<I", entry, desc_off)[0]
    if not _valid_gen3_pointer_text(rom, desc_ptr, allow_empty=allow_empty, allow_newline=True, max_scan=256):
        return False

    if spec["item_name_is_pointer"]:
        name_off = spec["item_name_offset"]
        if name_off + 4 > len(entry):
            return False
        name_ptr = struct.unpack_from("<I", entry, name_off)[0]
        return _valid_gen3_pointer_text(rom, name_ptr, allow_empty=allow_empty, max_scan=64)

    name_off = spec["item_name_offset"]
    name_len = spec["item_name_len"]
    if name_off + name_len > len(entry):
        return False
    return _valid_gen3_terminated(entry[name_off:name_off + name_len], allow_empty=allow_empty)


def _probe_items_info(rom: bytes, offset: int, layout_name: str) -> tuple[bool, int, int, str | None]:
    spec = SUPPORTED_LAYOUTS[layout_name]
    for stride in range(spec["item_min_stride"], spec["item_max_stride"] + 1, 4):
        if offset + 16 * stride > len(rom):
            continue
        ok = sum(
            1 for i in range(1, 16)
            if _valid_item_entry(rom, rom[offset + i * stride:offset + (i + 1) * stride], layout_name)
        )
        if ok < 12:
            continue
        avail = min((len(rom) - offset) // stride, MAX_TABLE_COUNT)
        count = 0
        for i in range(avail):
            entry = rom[offset + i * stride:offset + (i + 1) * stride]
            if not _valid_item_entry(rom, entry, layout_name, allow_empty=(i == 0)):
                break
            count += 1
        if count >= 16:
            return True, stride, count, None
    return False, 0, 0, "items_sanity_probe_failed"


def _probe_types_info(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    # 1. uq4_12_t (u16) N x N effectiveness table (values in {0x0000, 0x0800, 0x1000, 0x2000}).
    valid_uq4_12 = {0x0000, 0x0800, 0x1000, 0x2000}
    for dim in range(25, 17, -1):
        byte_len = dim * dim * 2
        if offset + byte_len <= len(rom):
            vals = struct.unpack_from(f"<{dim * dim}H", rom, offset)
            last_row = vals[(dim - 1) * dim:dim * dim]
            # Expansion enum Type: Normal=1, Ghost=8 -> Normal vs Ghost at vals[1 * dim + 8] == 0x0000.
            # Also accept legacy Gen 3 indexing vals[0 * dim + 7] == 0x0000 only when non-degenerate.
            normal_ghost_zero = (vals[1 * dim + 8] == 0x0000) or (vals[7] == 0x0000)
            if (
                all(v in valid_uq4_12 for v in vals)
                and {0x0000, 0x1000} <= set(vals)
                and normal_ghost_zero
                and any(v != 0 for v in last_row)
            ):
                return True, dim * 2, dim, None

    # 2. struct TypeInfo array (stride 16..64, inline name at +0 or pointer at +0).
    for stride in range(16, 65, 4):
        if offset + 18 * stride > len(rom):
            continue
        ok = 0
        for i in range(18):
            entry = rom[offset + i * stride:offset + (i + 1) * stride]
            if _valid_gen3_terminated(entry[:min(16, stride)]):
                ok += 1
            else:
                ptr = struct.unpack_from("<I", entry, 0)[0]
                if _valid_gen3_pointer_text(rom, ptr, max_scan=32):
                    ok += 1
        if ok >= 16:
            return True, stride, 18, None

    return False, 0, 0, "types_sanity_probe_failed"


def _inspect_slot(rom: bytes, slot_offset: int, probe_fn) -> dict:
    ptr = _u32(rom, slot_offset)
    if ptr == 0:
        return {
            "gba_address": None,
            "stride": 0,
            "count_estimate": 0,
            "sanity_ok": False,
            "table_sha256": None,
            "reason": "null_pointer",
        }
    if not is_valid_rom_pointer(ptr, len(rom)):
        return {
            "gba_address": None,
            "stride": 0,
            "count_estimate": 0,
            "sanity_ok": False,
            "table_sha256": None,
            "reason": "pointer_out_of_range",
        }
    offset = ptr - ROM_BASE
    ok, stride, count, err = probe_fn(rom, offset)
    if not ok:
        return {
            "gba_address": f"0x{ptr:08x}",
            "stride": stride,
            "count_estimate": 0,
            "sanity_ok": False,
            "table_sha256": None,
            "reason": err,
        }
    span = rom[offset:offset + count * stride]
    return {
        "gba_address": f"0x{ptr:08x}",
        "stride": stride,
        "count_estimate": count,
        "sanity_ok": True,
        "table_sha256": _sha256(span),
    }


def _extract_with_layout(rom: bytes, layout_name: str, marker: dict | None, core_ptrs_valid: bool) -> dict:
    tables = {
        "gSpeciesInfo": _inspect_slot(
            rom, GF_SLOT_SPECIES_INFO, lambda r, off: _probe_species_info(r, off, layout_name)
        ),
        "gMovesInfo": _inspect_slot(
            rom, GF_SLOT_MOVES_INFO, lambda r, off: _probe_moves_info(r, off, layout_name)
        ),
        "gItemsInfo": _inspect_slot(
            rom, GF_SLOT_ITEMS_INFO, lambda r, off: _probe_items_info(r, off, layout_name)
        ),
    }

    # Optional gTypesInfo / gTypeEffectivenessTable extension slot.
    types_entry = {
        "gba_address": None,
        "stride": 0,
        "count_estimate": 0,
        "sanity_ok": False,
        "table_sha256": None,
        "reason": "not_in_gf_header",
    }
    first_bad_types = None
    for slot in GF_TYPES_INFO_CANDIDATE_SLOTS:
        ptr = _u32(rom, slot)
        if ptr == 0:
            continue
        candidate = _inspect_slot(rom, slot, _probe_types_info)
        if candidate["sanity_ok"]:
            types_entry = candidate
            break
        if first_bad_types is None:
            first_bad_types = candidate
    else:
        if first_bad_types is not None:
            types_entry = first_bad_types
    tables["gTypesInfo"] = types_entry

    gf_header_valid = marker is not None and core_ptrs_valid
    core_ok = sum(1 for k in ("gSpeciesInfo", "gMovesInfo", "gItemsInfo") if tables[k]["sanity_ok"])
    if marker is None:
        status = "NOT_EXPANSION"
    elif not core_ptrs_valid:
        status = "INVALID_HEADER"
    elif core_ok == 3:
        status = "OK"
    elif core_ok > 0:
        status = "PARTIAL"
    else:
        status = "NO_TABLES_FOUND"

    return {
        "schema_version": 1,
        "layout_version": layout_name,
        "rom_sha256": _sha256(rom),
        "rom_size": len(rom),
        "expansion_marker": marker,
        "gf_header_valid": gf_header_valid,
        "tables": tables,
        "status": status,
    }


def extract_expansion_tables(rom: bytes, *, layout: str | None = None) -> dict:
    """Inspect a local pokeemerald-expansion ROM and return sanitized numeric JSON metadata."""
    require(GF_HEADER_END <= len(rom) <= MAX_ROM, "ROM size out of bounds")
    if layout is not None:
        require(layout in SUPPORTED_LAYOUTS, f"Unsupported layout version: {layout}")

    marker = find_expansion_marker(rom)
    core_ptrs_valid = all(
        is_valid_rom_pointer(_u32(rom, slot), len(rom))
        for slot in (GF_SLOT_SPECIES_INFO, GF_SLOT_MOVES_INFO, GF_SLOT_ITEMS_INFO)
    )

    if layout is not None:
        return _extract_with_layout(rom, layout, marker, core_ptrs_valid)

    candidates = [
        _extract_with_layout(rom, candidate_layout, marker, core_ptrs_valid)
        for candidate_layout in (LAYOUT_EXPANSION_V1_9_BITFIELD, LAYOUT_GEN3_VANILLA_BYTE)
    ]
    return max(
        candidates,
        key=lambda report: (
            sum(1 for k in ("gSpeciesInfo", "gMovesInfo", "gItemsInfo") if report["tables"][k]["sanity_ok"]),
            1 if report["layout_version"] == LAYOUT_EXPANSION_V1_9_BITFIELD else 0,
        ),
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", type=Path, required=True, help="Local pokeemerald-expansion ROM (<= 64 MiB)")
    parser.add_argument(
        "--layout",
        default=None,
        help="Optional pinned layout version (EXPANSION_V1_9_BITFIELD or GEN3_VANILLA_BYTE)",
    )
    args = parser.parse_args(argv)
    try:
        rom = read_local(args.rom, MAX_ROM)
        report = extract_expansion_tables(rom, layout=args.layout)
    except (ValueError, OSError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
