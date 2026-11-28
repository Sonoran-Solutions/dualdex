#!/usr/bin/env python3
"""Exact-candidate, read-only Unbound static contract; no production support.

Only counts, hashes, offsets and selected rows leave the process. Optional public
headers are supplied locally and hash checked; this tool never uses the network.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import unicodedata

from rowe_text import VALID_TEXT, decode_text
from unbound_identity import ROM_SIZE, exact_rom
from ups_identity import read_local, require

ROM_BASE = 0x08000000
SPECIES_COUNT = 1294
# File offsets, not CPU addresses. Counts include reserved row zero.
TABLES = {
    "species_names": (0x166A98C, SPECIES_COUNT, 11),
    "base_stats": (0x19E0C9C, SPECIES_COUNT, 28),
    "move_names": (0xA40A10, 923, 13),
    "ability_names": (0xA36398, 293, 17),
    "items": (0x876200, 729, 44),
}
POINTERS = {
    0x144: 0x0966A98C, 0x40FEC: 0x0966A98C,
    0x1BC: 0x099E0C9C, 0x3F7C8: 0x099E0C9C,
    0x148: 0x08A40A10, 0x308A4: 0x08A40A10,
    0x1C0: 0x08A36398, 0x9A9CDC: 0x08A36398,
    0x1C8: 0x08876200, 0x9F3284: 0x080001C8,
    0x9A8A8: 0x089F3241, 0x9A8C0: 0x089F324D,
}
# Independently disassembled Thumb spans, then pinned without a disassembler dependency.
ACCESSORS = {
    "species_name_x11": (0x40FD0, 0x4A, "2770a9c070f208f2a5e93441be2ce4665742b351e673b3d0c92e042945eccd70"),
    "base_stats_x28_gender": (0x3F78C, 0x40, "9c22a1176cea51270fd19978fb318b8a7dfd4fadd1ebdea4afae4c102f7ba814"),
    "move_name_x13": (0x30858, 0xE, "176760a7da332dfe623b25cf8189f7a444094a12c722aaa7ad85f8832d11f986"),
    "ability_name_x17_default_path": (0x9A9CB4, 0x2C, "055ed80ed187e84674883628c25c73bdbebf4be21e556884c79a839796a3126b"),
    "item_clamp_x44_indirect_name": (0x9F3240, 0x48, "8f784d9a2e92faf2573f299b83dc816c41067618dfe80ab6c20cac9eaa12c7ef"),
    "species_search_exclusive_bound": (0x165BB50, 0x74, "4fcba6e1e15d0e703d1eac348b09bddc25a732b17b8132d93f0b47724e57bfa0"),
}
SOURCES = {
    "dpe": ("dpe-species.h", "153baad3b2d13ad2264ddaf734cfc9b675ea82e46849c1c18ec272e79d4f0914",
            "cdfc053a56326a13dc5311b24488445e17536b7e", "Skeli789/Dynamic-Pokemon-Expansion", "include/species.h"),
    "cfru": ("cfru-species.h", "8c5acd30a4f06cbfe224ee282b60bac1c8a4898d3c75f632225c2200d8a59c43",
             "b637a27898b14e25dd24d0f69a3e302f0069deb8", "Skeli789/Complete-Fire-Red-Upgrade", "include/constants/species.h"),
}
SELECTED = {
    0: "reserved", 252: "egg", 253: "missingno", 254: "placeholder",
    277: "gen3", 412: "bad_egg", 413: "unown_form", 440: "gen4",
    548: "gen5", 706: "custom_shadow_warrior_candidate", 758: "gen6",
    869: "mega_candidate", 919: "conflicting_source_form_labels", 939: "gen7",
    1020: "alolan_candidate", 1102: "gen8_above_national_dex_range",
    1212: "galarian_candidate", 1234: "hisuian_candidate", 1260: "gigantamax_candidate",
    1293: "last_table_row", 1294: "out_of_domain",
}
RESERVED = {0, 252, 253, 412}
HOLES = set(range(254, 277))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def span(rom, offset, length):
    require(type(offset) is int and type(length) is int, "non-integer span")
    require(0 <= offset <= len(rom) and 0 <= length <= len(rom) - offset,
            "span outside ROM")
    return rom[offset:offset + length]


def u32(rom, offset):
    return struct.unpack("<I", span(rom, offset, 4))[0]


def fixed_name(raw):
    """Strict terminated field; do not silently discard unknown trailing data."""
    require(0 < len(raw) <= 64, "invalid name width")
    if 255 not in raw:
        return {"status": "NOT_VERIFIED", "name": None, "reason": "unterminated"}
    end = raw.index(255)
    if any(b not in (0, 255) for b in raw[end + 1:]):
        return {"status": "NOT_VERIFIED", "name": None, "reason": "nonpadding_suffix"}
    decoded = decode_text(raw[:end + 1], "map_section_name")
    if decoded.status != VALID_TEXT:
        return {"status": "NOT_VERIFIED", "name": None, "reason": decoded.status}
    return {"status": "VERIFIED", "name": decoded.text}


def names(rom, offset, count, stride):
    require(type(count) is int and 0 < count <= 4096 and 0 < stride <= 64,
            "invalid name table dimensions")
    raw = span(rom, offset, count * stride)
    return [fixed_name(raw[i * stride:(i + 1) * stride]) for i in range(count)]


def lookup_species(records, index):
    if type(index) is not int or not 0 <= index < len(records):
        return {"status": "NOT_VERIFIED", "name": None, "reason": "out_of_domain"}
    if index in RESERVED or index in HOLES:
        return {"status": "NOT_VERIFIED", "name": None, "reason": "reserved_or_placeholder"}
    return records[index].copy()


def item_name(rom, record):
    """Follow the exact accessor's first-word pointer test, with strict bounds."""
    require(len(record) == 44, "invalid item record width")
    if record[3] not in (8, 9):
        return fixed_name(record[:14]), "inline"
    address = int.from_bytes(record[:4], "little")
    offset = address - ROM_BASE
    # All pointer names must end within 64 bytes and the ROM. No RAM dereferences.
    require(0 <= offset < len(rom), "item name pointer outside ROM")
    raw = span(rom, offset, min(64, len(rom) - offset))
    require(255 in raw, "item pointer name lacks bounded terminator")
    return fixed_name(raw[:raw.index(255) + 1]), "pointer"


def parse_species_header(raw):
    """Read numeric species constants, preserving aliases; never execute a header."""
    require(len(raw) <= 128 * 1024, "source header exceeds size limit")
    values = {}
    for label, number in re.findall(r"^#define SPECIES_([A-Z0-9_]+)\s+(0x[0-9a-fA-F]+|[0-9]+)\b",
                                    raw.decode("utf-8"), re.M):
        index = int(number, 0)
        require(index < 4096, "source species ID exceeds comparison bound")
        values.setdefault(index, []).append(label)
    require(bool(values), "no numeric species constants")
    return values


def normalized(text):
    text = text.upper().replace("♂", "M").replace("♀", "F")
    return "".join(c for c in unicodedata.normalize("NFKD", text)
                   if c.isascii() and c.isalnum())


def compare_source(records, values):
    """Text-only screen, deliberately not a canonical-form verifier.

    Literal agreement folds case, accents and punctuation. Decorated agreement
    additionally drops one or more whole trailing underscore-separated tokens.
    No spelling corrections, fuzzy matching or custom-label overrides.
    """
    counts = Counter()
    mismatch_ids = []
    out_of_domain = []
    for index, labels in sorted(values.items()):
        if not 0 <= index < len(records):
            out_of_domain.append(index)
            continue
        row = records[index]
        target = normalized(row["name"]) if row["name"] else None
        if target and any(normalized(label) == target for label in labels):
            counts["literal"] += 1
        elif target and any(normalized("_".join(label.split("_")[:n])) == target
                            for label in labels for n in range(1, len(label.split("_")))):
            counts["decorated_base_name_only"] += 1
        else:
            mismatch_ids.append(index)
    covered = len(values) - len(out_of_domain)
    agreement = counts["literal"] + counts["decorated_base_name_only"]
    return dict(status="OBSERVED", meaning="Text agreement only; form semantics NOT_VERIFIED",
                declared_ids=len(values), source_covered_rom_ids=covered,
                complete_rom_domain=len(records), literal=counts["literal"],
                decorated_base_name_only=counts["decorated_base_name_only"],
                agreeing_ids=agreement, mismatch_ids=mismatch_ids,
                source_uncovered_rom_ids=[i for i in range(len(records)) if i not in values],
                source_out_of_domain_ids=out_of_domain,
                source_covered_fraction=f"{agreement}/{covered}",
                complete_domain_fraction=f"{agreement}/{len(records)}")


def validate_layout(rom):
    for offset, expected in POINTERS.items():
        require(u32(rom, offset) == expected, f"pointer mismatch at {offset:#x}")
    require(u32(rom, 0x165BBC0) == SPECIES_COUNT, "species loop bound mismatch")
    for label, (offset, length, expected) in ACCESSORS.items():
        require(sha(span(rom, offset, length)) == expected, f"accessor mismatch: {label}")


def inspect(rom, source_dir=None):
    identity = exact_rom(rom)  # Always before interpreting any offset.
    validate_layout(rom)
    tables = {}
    decoded = {}
    for label, (offset, count, stride) in TABLES.items():
        raw = span(rom, offset, count * stride)
        tables[label] = dict(status="VERIFIED", file_offset=f"0x{offset:08x}",
                             rom_address=f"0x{ROM_BASE + offset:08x}", count=count,
                             stride=stride, bytes=len(raw), sha256=sha(raw))
        if label.endswith("names"):
            decoded[label] = names(rom, offset, count, stride)
            require(all(row["status"] == "VERIFIED" for row in decoded[label]),
                    f"unexpected invalid text in {label}")
    # Adjacent bytes alone are not the count proof; check they cannot be another row.
    for label in ("species_names", "move_names", "ability_names"):
        offset, count, stride = TABLES[label]
        require(fixed_name(span(rom, offset + count * stride, stride))["status"] != "VERIFIED",
                f"unexpected additional name at {label} boundary")
    species = decoded["species_names"]
    require(all(species[i]["name"] == "?" for i in HOLES), "placeholder set mismatch")
    require([species[i]["name"] for i in [0, 252, 253, 412]] ==
            ["??????", "Egg", "MISSINGNO.", "Bad Egg"], "reserved names mismatch")
    stats_offset, _, stats_stride = TABLES["base_stats"]
    require(all(not any(span(rom, stats_offset + i * stats_stride, stats_stride)) for i in HOLES),
            "placeholder stats no longer zero")
    selected = []
    for index, role in SELECTED.items():
        row = dict(id=index, role=role, lookup=lookup_species(species, index),
                   form_semantics="NOT_VERIFIED")
        if index < len(species):
            row.update(decoded_name=species[index]["name"], name_status="VERIFIED")
        selected.append(row)
    item_offset, item_count, item_stride = TABLES["items"]
    item_counts = Counter()
    mismatches = []
    for index in range(item_count):
        record = span(rom, item_offset + index * item_stride, item_stride)
        result, storage = item_name(rom, record)
        item_counts[storage] += 1
        item_counts[result.get("reason", result["status"])] += 1
        if int.from_bytes(record[14:16], "little") != index:
            mismatches.append(index)
    tables["items"].update(name_outcomes=dict(item_counts), record_id_mismatch_ids=mismatches,
                           effects="NOT_VERIFIED")
    tables["base_stats"]["field_semantics"] = "NOT_VERIFIED except accessor stride and gender byte +16"
    tables["ability_names"]["effective_name"] = "NOT_VERIFIED: caller has species-dependent overrides"
    comparisons = {}
    if source_dir is not None:
        for label, (filename, digest, commit, repo, path) in SOURCES.items():
            raw = read_local(source_dir / filename, 128 * 1024)
            require(sha(raw) == digest, f"pinned {label} source SHA-256 mismatch")
            comparisons[label] = dict(compare_source(species, parse_species_header(raw)),
                                      sha256=digest, commit=commit,
                                      url=f"https://github.com/{repo}/blob/{commit}/{path}")
    return dict(schema_version=1, task_id="unbound-static", status="VERIFIED",
                scope="Exact static bytes only; not a production mapping or runtime identity",
                inspector_sha256=sha(Path(__file__).read_bytes()),
                rom=identity, tables=tables,
                pointers=[dict(file_offset=f"0x{k:08x}", value=f"0x{v:08x}") for k, v in POINTERS.items()],
                accessors={k:dict(file_offset=f"0x{o:08x}", bytes=n, sha256=h)
                           for k, (o, n, h) in ACCESSORS.items()},
                species_domain=dict(status="VERIFIED", maximum_table_id=1293, count=SPECIES_COUNT,
                                    placeholder_range=[254, 276], reserved_ids=sorted(RESERVED),
                                    nonreserved_name_rows=1267, runtime_legal_ids="NOT_VERIFIED"),
                selected_species=selected, source_comparisons=comparisons,
                source_comparison_status="OBSERVED" if comparisons else "NOT_VERIFIED",
                runtime_forms="NOT_VERIFIED", production_support="NOT_VERIFIED")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", type=Path, required=True)
    parser.add_argument("--source-dir", type=Path, help="directory containing both pinned species headers")
    args = parser.parse_args()
    try:
        report = inspect(read_local(args.rom, ROM_SIZE), args.source_dir)
    except (OSError, ValueError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
