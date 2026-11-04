#!/usr/bin/env python3
"""Read-only identity evidence for the owner's exact Unbound candidate.

No patch application, production trust changes, ROM output or network access.
Version strings are observations, never proof of publisher provenance.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

from rowe_text import VALID_TEXT, decode_text
from ups_identity import identity, read_local, require

ROM_SIZE = 33_554_432
ROM_SHA256 = "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"
BASE_SIZE = 16_777_216


def exact_rom(data):
    require(len(data) == ROM_SIZE, "exact diagnostic ROM size mismatch")
    result = identity(data)
    require(result["sha256"] == ROM_SHA256, "exact diagnostic ROM SHA-256 mismatch")
    return result


def header(data):
    require(len(data) >= 0xC0, "truncated GBA header")
    calculated = (-sum(data[0xA0:0xBD]) - 0x19) & 255
    return dict(title=data[0xA0:0xAC].decode("ascii", errors="replace").rstrip("\0"),
                game_code=data[0xAC:0xB0].decode("ascii", errors="replace"),
                maker=data[0xB0:0xB2].decode("ascii", errors="replace"),
                fixed_byte=data[0xB2], unit_code=data[0xB3], device_type=data[0xB4],
                reserved_hex=data[0xB5:0xBC].hex(), revision=data[0xBC],
                checksum=data[0xBD], calculated_checksum=calculated,
                checksum_valid=data[0xBD] == calculated,
                reserved_tail_hex=data[0xBE:0xC0].hex())


def version_candidates(data):
    """Bounded Gen III digit/dot search; do not mislabel arbitrary ASCII bytes.

    Retain at most 16 short version observations. An in-ROM label can be stale;
    title/credits runtime correlation and official patch reproduction are separate.
    """
    pattern = rb"[\xa1-\xaa]{1,2}(?:\xad[\xa1-\xaa]{1,2}){2,3}\xff"
    candidates = []
    for match in re.finditer(pattern, data):
        start, end = match.span()
        # Only include a short, fully decodable prefix following a terminator.
        prefix = max(data.rfind(b"\xff", max(0, start - 32), start) + 1,
                     max(0, start - 32))
        decoded = decode_text(data[prefix:end], "map_section_name")
        if decoded.status != VALID_TEXT:
            prefix = start
            decoded = decode_text(data[start:end], "map_section_name")
        if decoded.status == VALID_TEXT:
            candidates.append(dict(offset=f"0x{prefix:08x}", text=decoded.text,
                                   status="OBSERVED", encoding="GEN_III"))
            require(len(candidates) <= 16, "too many version candidates; manual review required")
    return candidates


def base_comparison(candidate, base):
    require(0 < len(base) <= len(candidate), "invalid base comparison size")
    block_size = 4096
    unchanged = []
    count = 0
    for offset in range(0, len(base), block_size):
        block = base[offset:offset + block_size]
        if block == candidate[offset:offset + len(block)] and len(set(block)) > 1:
            count += 1
            if len(unchanged) < 6:
                unchanged.append(dict(offset=f"0x{offset:08x}", bytes=len(block),
                                      sha256=hashlib.sha256(block).hexdigest()))
    return dict(status="OBSERVED", compared_bytes=len(base),
                equal_bytes=sum(a == b for a, b in zip(candidate, base)),
                block_size=block_size, unchanged_nonuniform_blocks=count,
                selected_unchanged_blocks=unchanged,
                conclusion="Shared bytes are consistent with derivation, not patch provenance")


def inspect(candidate, base):
    candidate_identity = exact_rom(candidate)
    require(len(base) == BASE_SIZE, "expected unheadered 16 MiB FireRed base")
    base_header = header(base)
    require(base_header["game_code"] == "BPRE" and base_header["revision"] == 0
            and base_header["checksum_valid"], "base is not checksum-valid BPRE revision zero")
    return dict(schema_version=1, task_id="unbound-static", status="VERIFIED",
                scope="Exact bytes and static observations only",
                candidate=dict(**candidate_identity, gba_header=header(candidate)),
                base=dict(**identity(base), gba_header=base_header),
                version_candidates=version_candidates(candidate),
                base_comparison=base_comparison(candidate, base),
                patch_to_rom_identity="NOT_VERIFIED", runtime_version="NOT_VERIFIED",
                production_support="NOT_VERIFIED")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", type=Path, required=True)
    parser.add_argument("--base", type=Path, required=True)
    args = parser.parse_args()
    try:
        report = inspect(read_local(args.rom, ROM_SIZE), read_local(args.base, BASE_SIZE))
    except (ValueError, OSError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
