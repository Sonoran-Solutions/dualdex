#!/usr/bin/env python3
"""Host-only, read-only CFRU FireRed ROM table locator and sanity verifier.

Discovers repointed CFRU ROM tables (gSpeciesNames, gBaseStats, gBattleMoves,
gItems, gTypeEffectiveness, gMonIconTable) by comparing a local 32 MiB CFRU
FireRed ROM against a local 16 MiB vanilla FireRed rev 0 ROM or known FireRed
rev 0 literal-pool anchor offsets.

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
MIN_ROM_HEADER = 0xC0
MAX_TABLE_COUNT = 4096
GF_HEADER_WINDOW = (0x08000100, 0x08000200)

# Minimal Gen 3 glyph set for structural terminator/text validation only (no text is emitted).
_GEN3_GLYPHS = (
    {0x00, 0x1B, 0xAB, 0xAC, 0xAD, 0xAE, 0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB8, 0xBA, 0xF0}
    | set(range(0xA1, 0xAB))
    | set(range(0xBB, 0xD5))
    | set(range(0xD5, 0xEF))
)
_GEN3_PADDING = {0x00, 0xFF}

# Known FireRed rev 0 literal-pool word file offsets and Thumb `ldr rd, [pc, #imm]` PCs.
ANCHORS = {
    "gSpeciesNames": {
        "literal_pool_offsets": (0x040FEC,),
        "thumb_ldr_pcs": (0x040FD2,),
        "header_offsets": (0x000144,),
        "stride": 11,
    },
    "gBaseStats": {
        "literal_pool_offsets": (0x03F7C8,),
        "thumb_ldr_pcs": (0x012F28, 0x040D80),
        "header_offsets": (0x0001BC,),
        "stride": 28,
    },
    "gBattleMoves": {
        "literal_pool_offsets": (0x00E358, 0x016010, 0x016354),
        "thumb_ldr_pcs": (0x00E338, 0x015FEC, 0x016330, 0x015F6A, 0x028A04),
        "header_offsets": (0x0001CC,),
        "stride": 12,
    },
    "gItems": {
        "literal_pool_offsets": (0x09A958,),
        "thumb_ldr_pcs": (0x09A94E,),
        "header_offsets": (0x0001C8,),
        "stride": 44,
    },
    "gTypeEffectiveness": {
        "literal_pool_offsets": (0x01E300, 0x01E280),
        "thumb_ldr_pcs": (0x01E2A0,),
        "header_offsets": (),
        "stride": None,
    },
    "gMonIconTable": {
        "literal_pool_offsets": (0x096C68, 0x096CFC),
        "thumb_ldr_pcs": (0x096C50,),
        "header_offsets": (),
        "stride": 4,
    },
}


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _u16(rom: bytes, offset: int) -> int:
    return struct.unpack_from("<H", rom, offset)[0]


def _u32(rom: bytes, offset: int) -> int:
    return struct.unpack_from("<I", rom, offset)[0]


def is_valid_rom_pointer(address: int, rom_size: int) -> bool:
    """True iff `address` falls inside [0x08000000, 0x0A000000) and within `rom_size`."""
    return ROM_BASE <= address < ROM_END_CAP and (address - ROM_BASE) < rom_size


def _decode_thumb_ldr_pool_offset(rom: bytes, pc_offset: int) -> int | None:
    """Return literal-pool file offset for a Thumb `ldr rd, [pc, #imm]` at `pc_offset`, or None."""
    if pc_offset < 0 or pc_offset + 2 > len(rom) or (pc_offset & 1):
        return None
    insn = _u16(rom, pc_offset)
    if (insn >> 11) != 0b01001:
        return None
    pool_offset = ((pc_offset + 4) & ~3) + (insn & 0xFF) * 4
    if pool_offset + 4 > len(rom):
        return None
    return pool_offset


def _valid_gen3_terminated(raw: bytes, *, allow_empty: bool = False) -> bool:
    """True iff `raw` holds a bounded 0xFF-terminated Gen 3 string with only padding after 0xFF."""
    if not raw or 0xFF not in raw:
        return False
    term = raw.index(0xFF)
    if term == 0:
        return allow_empty and all(b in _GEN3_PADDING for b in raw[1:])
    if any(b not in _GEN3_PADDING for b in raw[term + 1:]):
        return False
    return all(b in _GEN3_GLYPHS for b in raw[:term])


def _validate_species_names(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    stride = 11
    avail = (len(rom) - offset) // stride
    if avail < 16:
        return False, stride, 0, "insufficient_bytes"
    sample_ok = sum(
        1 for i in range(1, min(16, avail))
        if _valid_gen3_terminated(rom[offset + i * stride:offset + (i + 1) * stride])
    )
    if sample_ok < 12:
        return False, stride, 0, "invalid_gen3_names"
    count = 0
    limit = min(avail, MAX_TABLE_COUNT)
    for i in range(limit):
        entry = rom[offset + i * stride:offset + (i + 1) * stride]
        if not _valid_gen3_terminated(entry, allow_empty=(i == 0)):
            break
        count += 1
    if count < 16:
        return False, stride, 0, "count_below_minimum"
    return True, stride, count, None


def _valid_base_stat_row(entry: bytes, *, allow_zero: bool = False) -> bool:
    if len(entry) != 28:
        return False
    stats = entry[:6]
    if all(b == 0 for b in entry):
        return allow_zero
    if any(b == 0 for b in stats):
        return False
    t1, t2 = entry[6], entry[7]
    return t1 < 32 and t2 < 32


def _validate_base_stats(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    stride = 28
    avail = (len(rom) - offset) // stride
    if avail < 16:
        return False, stride, 0, "insufficient_bytes"
    sample_ok = sum(
        1 for i in range(1, min(11, avail))
        if _valid_base_stat_row(rom[offset + i * stride:offset + (i + 1) * stride])
    )
    if sample_ok < 9:
        return False, stride, 0, "invalid_base_stats"
    last_nonzero = 0
    zero_run = 0
    limit = min(avail, MAX_TABLE_COUNT)
    for i in range(limit):
        entry = rom[offset + i * stride:offset + (i + 1) * stride]
        if not _valid_base_stat_row(entry, allow_zero=True):
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
    if count < 16:
        return False, stride, 0, "count_below_minimum"
    return True, stride, count, None


def _valid_battle_move_row(entry: bytes) -> bool:
    if len(entry) != 12:
        return False
    move_type, accuracy, pp, split = entry[2], entry[3], entry[4], entry[10]
    return move_type < 32 and accuracy <= 100 and pp <= 64 and split <= 2


def _validate_battle_moves(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    stride = 12
    avail = (len(rom) - offset) // stride
    if avail < 16:
        return False, stride, 0, "insufficient_bytes"
    sample_rows = [rom[offset + i * stride:offset + (i + 1) * stride] for i in range(1, min(36, avail))]
    if not all(_valid_battle_move_row(r) for r in sample_rows):
        return False, stride, 0, "invalid_move_fields"
    if not any(10 <= r[1] <= 250 for r in sample_rows):
        return False, stride, 0, "no_plausible_move_powers"
    last_nonzero = 0
    zero_run = 0
    limit = min(avail, MAX_TABLE_COUNT)
    for i in range(limit):
        entry = rom[offset + i * stride:offset + (i + 1) * stride]
        if not _valid_battle_move_row(entry):
            break
        if all(b == 0 for b in entry):
            if i > 0:
                zero_run += 1
                if zero_run > 8:
                    break
        else:
            zero_run = 0
            last_nonzero = i
    count = last_nonzero + 1
    if count < 16:
        return False, stride, 0, "count_below_minimum"
    return True, stride, count, None


def _valid_item_row(rom: bytes, entry: bytes, *, allow_empty: bool = False) -> bool:
    if len(entry) != 44:
        return False
    item_id = struct.unpack_from("<H", entry, 14)[0]
    if item_id >= MAX_TABLE_COUNT:
        return False
    if entry[3] in (0x08, 0x09):
        ptr = struct.unpack_from("<I", entry, 0)[0]
        if not is_valid_rom_pointer(ptr, len(rom)):
            return False
        name_off = ptr - ROM_BASE
        span = rom[name_off:min(len(rom), name_off + 64)]
        return 0xFF in span and _valid_gen3_terminated(span[:span.index(0xFF) + 1], allow_empty=allow_empty)
    return _valid_gen3_terminated(entry[:14], allow_empty=allow_empty)


def _validate_items(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    stride = 44
    avail = (len(rom) - offset) // stride
    if avail < 16:
        return False, stride, 0, "insufficient_bytes"
    sample_ok = sum(
        1 for i in range(1, min(16, avail))
        if _valid_item_row(rom, rom[offset + i * stride:offset + (i + 1) * stride])
    )
    if sample_ok < 12:
        return False, stride, 0, "invalid_item_records"
    count = 0
    limit = min(avail, MAX_TABLE_COUNT)
    for i in range(limit):
        entry = rom[offset + i * stride:offset + (i + 1) * stride]
        if not _valid_item_row(rom, entry, allow_empty=(i == 0)):
            break
        count += 1
    if count < 16:
        return False, stride, 0, "count_below_minimum"
    return True, stride, count, None


def _validate_type_effectiveness(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    # 1. Check CFRU N x N matrix layout first (e.g., 24 x 24, 19 x 19, 18 x 18).
    for dim in (24, 20, 19, 18):
        total = dim * dim
        if offset + total <= len(rom):
            block = rom[offset:offset + total]
            if all(b in (0, 1, 5, 10, 20) for b in block) and block[7] == 0:
                return True, dim, dim, None

    # 2. Check vanilla-style 3-byte triple table terminated by (0xFF, ...).
    stride = 3
    avail = (len(rom) - offset) // stride
    if avail >= 30:
        count = 0
        for i in range(min(avail, 256)):
            atk, dfn, mult = rom[offset + i * 3:offset + i * 3 + 3]
            if atk == 0xFF:
                if count >= 20:
                    return True, stride, count, None
                break
            if (atk >= 32 and atk != 0xFE) or (dfn >= 32 and dfn != 0xFE) or mult not in (0, 5, 10, 20):
                break
            count += 1
    return False, 0, 0, "invalid_type_effectiveness"


def _validate_mon_icon_table(rom: bytes, offset: int) -> tuple[bool, int, int, str | None]:
    stride = 4
    avail = (len(rom) - offset) // stride
    if avail < 16:
        return False, stride, 0, "insufficient_bytes"
    for i in range(1, 16):
        ptr = _u32(rom, offset + i * 4)
        if not is_valid_rom_pointer(ptr, len(rom)) or (ptr % 4) != 0:
            return False, stride, 0, "invalid_icon_pointer"
    count = 0
    limit = min(avail, MAX_TABLE_COUNT)
    for i in range(limit):
        ptr = _u32(rom, offset + i * 4)
        if i == 0 and ptr == 0:
            count += 1
            continue
        if not is_valid_rom_pointer(ptr, len(rom)) or (ptr % 4) != 0:
            break
        count += 1
    if count < 16:
        return False, stride, 0, "count_below_minimum"
    return True, stride, count, None


VALIDATORS = {
    "gSpeciesNames": _validate_species_names,
    "gBaseStats": _validate_base_stats,
    "gBattleMoves": _validate_battle_moves,
    "gItems": _validate_items,
    "gTypeEffectiveness": _validate_type_effectiveness,
    "gMonIconTable": _validate_mon_icon_table,
}


def _candidate_sites_for_table(table_name: str, rom: bytes, base: bytes | None) -> list[int]:
    spec = ANCHORS[table_name]
    sites: list[int] = []
    seen: set[int] = set()

    def add_site(offset: int | None) -> None:
        if offset is not None and 0 <= offset <= len(rom) - 4 and offset not in seen:
            seen.add(offset)
            sites.append(offset)

    # 1. Explicit FireRed rev 0 literal-pool offsets.
    for off in spec["literal_pool_offsets"]:
        add_site(off)

    # 2. Thumb `ldr rd, [pc, #imm]` PCs in ROM (and in base if supplied).
    for pc in spec["thumb_ldr_pcs"]:
        add_site(_decode_thumb_ldr_pool_offset(rom, pc))
        if base is not None:
            add_site(_decode_thumb_ldr_pool_offset(base, pc))

    # 3. If a 16 MiB vanilla base ROM is provided, discover all literal-pool words in `base`
    #    matching the vanilla table pointer found at the anchor sites, then inspect those sites in `rom`.
    if base is not None:
        base_ptrs: set[int] = set()
        for off in list(sites):
            if off + 4 <= len(base):
                val = _u32(base, off)
                if is_valid_rom_pointer(val, len(base)):
                    base_ptrs.add(val)
        if base_ptrs:
            aligned_end = (len(base) // 4) * 4
            for off in range(0, aligned_end, 4):
                val = _u32(base, off)
                if val in base_ptrs:
                    add_site(off)

    # 4. CFRU extended ROM header pointer fallback slots.
    for off in spec["header_offsets"]:
        add_site(off)

    return sites


def _resolve_candidate_pointer(rom: bytes, site_offset: int) -> tuple[int | None, str | None]:
    """Read literal at `site_offset`, handling at most 1 level of GF-header indirection."""
    ptr = _u32(rom, site_offset)
    if ptr == 0:
        return None, "null_pointer"
    if not is_valid_rom_pointer(ptr, len(rom)):
        return None, "pointer_out_of_range"
    if GF_HEADER_WINDOW[0] <= ptr < GF_HEADER_WINDOW[1]:
        cell_off = ptr - ROM_BASE
        if cell_off + 4 > len(rom):
            return None, "pointer_out_of_range"
        deref = _u32(rom, cell_off)
        if not is_valid_rom_pointer(deref, len(rom)):
            return None, "pointer_out_of_range"
        return deref, None
    return ptr, None


def extract_tables(rom: bytes, base: bytes | None = None) -> dict:
    """Inspect a CFRU FireRed ROM against an optional FireRed rev 0 base ROM."""
    require(MIN_ROM_HEADER <= len(rom) <= MAX_ROM, "ROM size out of bounds")
    if base is not None:
        require(MIN_ROM_HEADER <= len(base) <= MAX_ROM, "base ROM size out of bounds")

    tables = {}
    valid_count = 0
    for name, validator in VALIDATORS.items():
        sites = _candidate_sites_for_table(name, rom, base)
        chosen = None
        saw_out_of_range = False
        saw_sanity_fail = False
        last_error = "no_anchor_sites"
        last_addr = None

        # Prefer repointed candidates (when base is present and differs) before un-repointed ones.
        ordered_candidates: list[tuple[bool, int, int]] = []
        for site in sites:
            ptr, err = _resolve_candidate_pointer(rom, site)
            if err is not None:
                if err == "pointer_out_of_range":
                    saw_out_of_range = True
                last_error = err
                continue
            assert ptr is not None
            repointed = base is None or site + 4 > len(base) or _u32(base, site) != ptr
            ordered_candidates.append((repointed, site, ptr))

        ordered_candidates.sort(key=lambda item: (not item[0], item[1]))

        tried_ptrs: set[int] = set()
        for _, _, ptr in ordered_candidates:
            if ptr in tried_ptrs:
                continue
            tried_ptrs.add(ptr)
            offset = ptr - ROM_BASE
            ok, stride, count, err = validator(rom, offset)
            if ok:
                span = rom[offset:offset + count * stride]
                chosen = {
                    "gba_address": f"0x{ptr:08x}",
                    "stride": stride,
                    "count_estimate": count,
                    "sanity_ok": True,
                    "table_sha256": _sha256(span),
                }
                valid_count += 1
                break
            saw_sanity_fail = True
            last_addr = f"0x{ptr:08x}"
            last_error = err or "sanity_check_failed"

        if chosen is None:
            reason = (
                "pointer_out_of_range"
                if saw_out_of_range and not saw_sanity_fail
                else last_error
            )
            chosen = {
                "gba_address": last_addr if saw_sanity_fail else None,
                "stride": ANCHORS[name]["stride"] or 0,
                "count_estimate": 0,
                "sanity_ok": False,
                "table_sha256": None,
                "reason": reason,
            }
        tables[name] = chosen

    if valid_count == len(VALIDATORS):
        status = "OK"
    elif valid_count > 0:
        status = "PARTIAL"
    else:
        status = "NO_TABLES_FOUND"

    return {
        "schema_version": 1,
        "rom_sha256": _sha256(rom),
        "rom_size": len(rom),
        "base_rom_sha256": _sha256(base) if base is not None else None,
        "tables": tables,
        "status": status,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", type=Path, required=True, help="Local CFRU FireRed ROM (<= 64 MiB)")
    parser.add_argument("--base", type=Path, help="Optional local 16 MiB vanilla FireRed rev 0 ROM")
    args = parser.parse_args(argv)
    try:
        rom = read_local(args.rom, MAX_ROM)
        base = read_local(args.base, MAX_ROM) if args.base else None
        report = extract_tables(rom, base)
    except (ValueError, OSError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
