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

# Gen 3 / pokeemerald-expansion type IDs for Bulbasaur (Grass=12, Poison=3) & Charmander (Fire=10).
TYPE_GRASS = 12
TYPE_POISON = 3
TYPE_FIRE = 10

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


def _valid_gen3_terminated(raw: bytes, *, allow_empty: bool = False) -> bool:
    if not raw or 0xFF not in raw:
        return False
    term = raw.index(0xFF)
    if term == 0:
        return allow_empty and all(b in _GEN3_PADDING for b in raw[1:])
    if any(b not in _GEN3_PADDING for b in raw[term + 1:]):
        return False
    return all(b in _GEN3_GLYPHS for b in raw[:term])


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


def _is_plausible_species_row(entry: bytes, *, allow_zero: bool = False) -> bool:
    stats = entry[:6]
    if all(b == 0 for b in entry):
        return allow_zero
    if any(b == 0 for b in stats):
        return False
    return entry[6] < 32 and entry[7] < 32


def _probe_species_info(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    """Detect struct SpeciesInfo stride (28..256, 4-byte aligned) and validate species 1+."""
    best_stride = None
    best_score = -1

    for stride in range(28, 257, 4):
        if offset + 12 * stride > len(rom):
            continue
        rows = [rom[offset + i * stride:offset + (i + 1) * stride] for i in range(1, 11)]
        if not all(_is_plausible_species_row(r) for r in rows):
            continue
        # Require non-trivial stat sum for species 1.
        if sum(rows[0][:6]) < 150:
            continue
        score = 1
        # Species 1..3 Grass/Poison (Bulbasaur/Ivysaur/Venusaur) and Species 4 Fire (Charmander).
        if rows[0][6] == TYPE_GRASS and rows[0][7] == TYPE_POISON:
            score += 4
        if rows[1][6] == TYPE_GRASS and rows[1][7] == TYPE_POISON:
            score += 2
        if rows[2][6] == TYPE_GRASS and rows[2][7] == TYPE_POISON:
            score += 2
        if rows[3][6] == TYPE_FIRE and rows[3][7] == TYPE_FIRE:
            score += 2
        if score > best_score:
            best_score = score
            best_stride = stride

    if best_stride is None:
        return False, 0, 0, "species_sanity_probe_failed"

    avail = min((len(rom) - offset) // best_stride, MAX_TABLE_COUNT)
    last_nonzero = 0
    zero_run = 0
    for i in range(avail):
        entry = rom[offset + i * best_stride:offset + (i + 1) * best_stride]
        if not _is_plausible_species_row(entry, allow_zero=True):
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


def _probe_moves_info(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    """Detect gMovesInfo / gBattleMoves stride and verify move 33 (Tackle, power 40)."""
    for stride in range(12, 129, 4):
        if offset + 36 * stride > len(rom):
            continue

        # Candidate field layouts inside `stride`:
        # (a) Classic/compact BattleMove: power(+1), type(+2), accuracy(+3), pp(+4)
        # (b) Pointer-prefixed MoveInfo: check small offsets where power==40 at move 1, 10, 33 and 0 at 14, 28
        candidate_offsets = [(1, 2, 3, 4)]
        if stride >= 24:
            for p_off in range(4, min(stride - 4, 48)):
                if (
                    rom[offset + 33 * stride + p_off] == 40
                    and rom[offset + 1 * stride + p_off] == 40
                    and rom[offset + 14 * stride + p_off] == 0
                    and rom[offset + 28 * stride + p_off] == 0
                ):
                    for t_off in range(0, min(stride, 48)):
                        if t_off == p_off:
                            continue
                        if rom[offset + 33 * stride + t_off] < 32:
                            a_off = min(p_off + 2, stride - 2)
                            pp_off = min(p_off + 3, stride - 1)
                            candidate_offsets.append((p_off, t_off, a_off, pp_off))
                            break

        for p_off, t_off, a_off, pp_off in candidate_offsets:
            move33 = rom[offset + 33 * stride:offset + 34 * stride]
            if move33[p_off] != 40:
                continue
            sample_ok = True
            for idx in range(1, 36):
                row = rom[offset + idx * stride:offset + (idx + 1) * stride]
                if row[t_off] >= 32 or row[a_off] > 100 or row[pp_off] > 64:
                    sample_ok = False
                    break
            if not sample_ok:
                continue

            avail = min((len(rom) - offset) // stride, MAX_TABLE_COUNT)
            last_nonzero = 0
            zero_run = 0
            for i in range(avail):
                row = rom[offset + i * stride:offset + (i + 1) * stride]
                if row[t_off] >= 32 or row[a_off] > 100 or row[pp_off] > 64:
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


def _valid_item_entry(rom: bytes, entry: bytes, *, allow_empty: bool = False) -> bool:
    if allow_empty and all(b == 0 for b in entry):
        return True
    # Inline 14..20 byte name at +0, or pointer-backed name in first 16 bytes.
    if _valid_gen3_terminated(entry[:min(20, len(entry))], allow_empty=allow_empty):
        return True
    for ptr_off in (0, 4, 8, 12):
        if ptr_off + 4 <= len(entry):
            ptr = struct.unpack_from("<I", entry, ptr_off)[0]
            if is_valid_rom_pointer(ptr, len(rom)):
                name_off = ptr - ROM_BASE
                span = rom[name_off:min(len(rom), name_off + 64)]
                if 0xFF in span and _valid_gen3_terminated(span[:span.index(0xFF) + 1], allow_empty=allow_empty):
                    return True
    return False


def _probe_items_info(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    for stride in range(24, 129, 4):
        if offset + 16 * stride > len(rom):
            continue
        ok = sum(
            1 for i in range(1, 16)
            if _valid_item_entry(rom, rom[offset + i * stride:offset + (i + 1) * stride])
        )
        if ok < 12:
            continue
        avail = min((len(rom) - offset) // stride, MAX_TABLE_COUNT)
        count = 0
        for i in range(avail):
            entry = rom[offset + i * stride:offset + (i + 1) * stride]
            if not _valid_item_entry(rom, entry, allow_empty=(i == 0)):
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
            if all(v in valid_uq4_12 for v in vals) and vals[7] == 0x0000 and any(v != 0 for v in last_row):
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
                if is_valid_rom_pointer(ptr, len(rom)):
                    name_off = ptr - ROM_BASE
                    span = rom[name_off:min(len(rom), name_off + 32)]
                    if 0xFF in span and _valid_gen3_terminated(span[:span.index(0xFF) + 1]):
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


def extract_expansion_tables(rom: bytes) -> dict:
    """Inspect a local pokeemerald-expansion ROM and return sanitized numeric JSON metadata."""
    require(GF_HEADER_END <= len(rom) <= MAX_ROM, "ROM size out of bounds")

    marker = find_expansion_marker(rom)
    tables = {
        "gSpeciesInfo": _inspect_slot(rom, GF_SLOT_SPECIES_INFO, _probe_species_info),
        "gMovesInfo": _inspect_slot(rom, GF_SLOT_MOVES_INFO, _probe_moves_info),
        "gItemsInfo": _inspect_slot(rom, GF_SLOT_ITEMS_INFO, _probe_items_info),
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

    core_ptrs_valid = all(
        is_valid_rom_pointer(_u32(rom, slot), len(rom))
        for slot in (GF_SLOT_SPECIES_INFO, GF_SLOT_MOVES_INFO, GF_SLOT_ITEMS_INFO)
    )
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
        "rom_sha256": _sha256(rom),
        "rom_size": len(rom),
        "expansion_marker": marker,
        "gf_header_valid": gf_header_valid,
        "tables": tables,
        "status": status,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", type=Path, required=True, help="Local pokeemerald-expansion ROM (<= 64 MiB)")
    args = parser.parse_args(argv)
    try:
        rom = read_local(args.rom, MAX_ROM)
        report = extract_expansion_tables(rom)
    except (ValueError, OSError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
