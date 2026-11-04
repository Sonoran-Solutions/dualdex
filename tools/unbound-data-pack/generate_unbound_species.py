#!/usr/bin/env python3
"""Generate the numeric Unbound species table from the owner's exact private ROM.

Python stdlib only. Refuses every ROM except the pinned candidate, and derives the
table through tools/rom-evidence/unbound_species_map.py (accessor/literal/table gates)
instead of re-deriving it. The committed artifact holds ONLY numbers: internal species
ID -> National Dex number and a form-class code, plus a provenance header. No names,
text or other ROM data. `--verify` checks the committed artifact without any ROM.
"""
import argparse
from collections import Counter
import hashlib
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "tools" / "rom-evidence"))

import unbound_species_map as species_map  # noqa: E402
from unbound_data import HOLES, RESERVED, ROM_BASE, SPECIES_COUNT, sha  # noqa: E402
from unbound_identity import ROM_SHA256, ROM_SIZE  # noqa: E402
from ups_identity import read_local, require  # noqa: E402

PINNED_ROM_SHA256 = "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"
assert PINNED_ROM_SHA256 == ROM_SHA256, "identity pin drifted from rom-evidence"
CUSTOM_IDS = frozenset({706})
MAX_DEX = 905
DEFAULT_OUTPUT = ROOT / "app/src/main/java/com/dualdex/pokemon/unbound/UnboundSpeciesTable.kt"

# Code order is the committed contract; the Kotlin constants below mirror it.
FORM_CLASSES = ("base", "regional", "mega_primal", "gigantamax", "cosmetic",
                "other_alternate", "custom", "reserved")
EVIDENCE_CLASS = {"base_species": "base", "regional_form": "regional", "mega_primal": "mega_primal",
                  "gigantamax": "gigantamax", "cosmetic_form": "cosmetic",
                  "other_alternate_form": "other_alternate", "custom_unbound": "custom",
                  "reserved_hole": "reserved"}
# docs/UNBOUND_SPECIES_MAPPING_EVIDENCE.md, per-ID classification table.
EXPECTED_COUNTS = {"base": 905, "regional": 59, "mega_primal": 50, "gigantamax": 34,
                   "cosmetic": 102, "other_alternate": 116, "custom": 1, "reserved": 27}
KOTLIN_NAMES = ("FORM_BASE", "FORM_REGIONAL", "FORM_MEGA_PRIMAL", "FORM_GIGANTAMAX",
                "FORM_COSMETIC", "FORM_OTHER_ALTERNATE", "FORM_CUSTOM", "FORM_RESERVED")
PER_LINE = 16


def derive(rom, source_root):
    """Exact ROM + pinned CFRU labels -> [(dex, class_code)] indexed by internal ID."""
    require(len(rom) == ROM_SIZE and sha(rom) == PINNED_ROM_SHA256,
            "refusing ROM: not the pinned Unbound candidate")
    # All exact-ROM gates live here: identity, accessor spans, literals, table hash,
    # numeric domain, zero set, runtime pins and the source-label classification.
    report = species_map.inspect(rom, source_root)
    require(report["classification"]["status"] == "OBSERVED", "form classification unavailable")
    offset = int(report["table"]["file_offset"], 16)
    mapping = species_map.read_dex_table(rom, offset, SPECIES_COUNT)
    sources = {}
    for key in ("cfru_species", "cfru_dex"):
        relative, digest = species_map.SOURCE_FILES[key]
        sources[key] = read_local(Path(source_root) / relative, 128 * 1024)
        require(sha(sources[key]) == digest, f"pinned source mismatch: {key}")
    labels = species_map.parse_species_header(sources["cfru_species"])
    dex = species_map.parse_dex_header(sources["cfru_dex"])
    classes = species_map.classify(mapping, labels, dex)
    require(dict(Counter(classes.values())) == report["classification"]["counts"],
            "classification disagrees with the inspector report")
    rows = [(mapping[i], FORM_CLASSES.index(EVIDENCE_CLASS[classes[i]])) for i in range(SPECIES_COUNT)]
    check_rows(rows)
    provenance = dict(rom_sha256=report["rom"]["sha256"], table_file_offset=offset,
                      table_rom_address=ROM_BASE + offset, table_sha256=report["table"]["sha256"],
                      generator_sha256=sha(Path(__file__).read_bytes()),
                      inspector_sha256=report["inspector_sha256"],
                      cfru_species_sha256=species_map.SOURCE_FILES["cfru_species"][1])
    return rows, provenance


def check_rows(rows):
    """Structural invariants every committed table must satisfy (ROM-free)."""
    require(len(rows) == SPECIES_COUNT, "row count mismatch")
    for i, (number, code) in enumerate(rows):
        require(type(number) is int and 0 <= number <= MAX_DEX, f"dex out of range at {i}")
        require(type(code) is int and 0 <= code < len(FORM_CLASSES), f"bad form class at {i}")
        unmapped = FORM_CLASSES[code] in ("custom", "reserved")
        require((number == 0) == unmapped, f"zero dex must be custom/reserved at {i}")
    require({i for i, (_, c) in enumerate(rows) if FORM_CLASSES[c] == "reserved"} == RESERVED | HOLES,
            "reserved set mismatch")
    require({i for i, (_, c) in enumerate(rows) if FORM_CLASSES[c] == "custom"} == CUSTOM_IDS,
            "custom set mismatch")
    first = {}
    for i, (number, _) in enumerate(rows):
        if number:
            first.setdefault(number, i)
    require(set(first) == set(range(1, MAX_DEX + 1)), "dex domain is not 1..905")
    for i, (number, code) in enumerate(rows):
        if number:
            require((FORM_CLASSES[code] == "base") == (first[number] == i),
                    f"base must be the first row for its dex at {i}")
    counts = Counter(FORM_CLASSES[c] for _, c in rows)
    require(dict(counts) == EXPECTED_COUNTS, f"form-class counts changed: {dict(counts)}")


def mapping_digest(rows):
    digest = hashlib.sha256()
    for i, (number, code) in enumerate(rows):
        digest.update(f"{i}|{number}|{FORM_CLASSES[code]}\n".encode("ascii"))
    return digest.hexdigest()


def int_array(values):
    lines = []
    for start in range(0, len(values), PER_LINE):
        chunk = ", ".join(str(v) for v in values[start:start + PER_LINE])
        lines.append(f"        /* {start} */ {chunk},")
    return "\n".join(lines)


def render(rows, p):
    counts = Counter(FORM_CLASSES[c] for _, c in rows)
    count_text = ", ".join(f"{name} {counts[name]}" for name in FORM_CLASSES)
    constants = "\n".join(f"    const val {k}: Int = {i}" for i, k in enumerate(KOTLIN_NAMES))
    return f"""package com.dualdex.pokemon.unbound

/**
 * Exact-ROM numeric species table for the owner's private Pokemon Unbound candidate.
 *
 * Numbers only: internal species ID (array index) -> National Dex number and form-class code.
 * No names, text or other ROM data. Form classes are the pinned CFRU source-label taxonomy of
 * docs/UNBOUND_SPECIES_MAPPING_EVIDENCE.md; runtime form semantics and display names are NOT_VERIFIED.
 *
 * Provenance:
 *   ROM SHA-256: {p['rom_sha256']}
 *   Table: file offset 0x{p['table_file_offset']:08X} (GBA 0x{p['table_rom_address']:08X}), u16 LE, index = internal ID - 1
 *   Table SHA-256: {p['table_sha256']}
 *   Generator SHA-256 (tools/unbound-data-pack/generate_unbound_species.py): {p['generator_sha256']}
 *   Inspector SHA-256 (tools/rom-evidence/unbound_species_map.py): {p['inspector_sha256']}
 *   CFRU species.h SHA-256 (form-class labels): {p['cfru_species_sha256']}
 *   Form classes: {count_text}
 *
 * DO NOT EDIT DIRECTLY. Regenerate (private ROM and pinned sources required) with:
 *   python3 tools/unbound-data-pack/generate_unbound_species.py --rom <rom> --source-root <src>
 * ROM-free integrity check (./ci.sh test):
 *   python3 tools/unbound-data-pack/generate_unbound_species.py --verify
 */
internal object UnboundSpeciesTable {{
    const val ROM_SHA256: String = "{p['rom_sha256']}"
    const val TABLE_FILE_OFFSET: Int = 0x{p['table_file_offset']:08X}
    const val TABLE_SHA256: String = "{p['table_sha256']}"
    const val GENERATOR_SHA256: String = "{p['generator_sha256']}"
    const val INSPECTOR_SHA256: String = "{p['inspector_sha256']}"
    const val CFRU_SPECIES_SHA256: String = "{p['cfru_species_sha256']}"
    const val MAPPING_DIGEST: String = "{mapping_digest(rows)}"
    const val SPECIES_COUNT: Int = {len(rows)}

{constants}

    /** National Dex number by internal species ID; 0 = no National Dex identity. */
    val NATIONAL_DEX: IntArray = intArrayOf(
{int_array([n for n, _ in rows])}
    )

    /** Form-class code (FORM_*) by internal species ID. */
    val FORM_CLASS: IntArray = intArrayOf(
{int_array([c for _, c in rows])}
    )
}}
"""


def parse_artifact(text):
    """Recover rows and provenance from a committed artifact; refuse anything malformed."""
    def const(name, pattern):
        match = re.search(rf"^    const val {name}: \w+ = {pattern}$", text, re.M)
        require(match is not None, f"missing constant {name}")
        return match.group(1)

    def array(name):
        match = re.search(rf"val {name}: IntArray = intArrayOf\(\n(.*?)\n    \)", text, re.S)
        require(match is not None, f"missing array {name}")
        body = re.sub(r"/\* \d+ \*/", "", match.group(1))
        values = [v.strip() for v in body.split(",") if v.strip()]
        require(all(re.fullmatch(r"\d+", v) for v in values), f"non-numeric entry in {name}")
        return [int(v) for v in values]

    hexdigest = r'"([0-9a-f]{64})"'
    p = dict(rom_sha256=const("ROM_SHA256", hexdigest),
             table_file_offset=int(const("TABLE_FILE_OFFSET", r"(0x[0-9A-F]{8})"), 16),
             table_sha256=const("TABLE_SHA256", hexdigest),
             generator_sha256=const("GENERATOR_SHA256", hexdigest),
             inspector_sha256=const("INSPECTOR_SHA256", hexdigest),
             cfru_species_sha256=const("CFRU_SPECIES_SHA256", hexdigest))
    p["table_rom_address"] = ROM_BASE + p["table_file_offset"]
    dex, classes = array("NATIONAL_DEX"), array("FORM_CLASS")
    require(len(dex) == len(classes), "array length mismatch")
    return list(zip(dex, classes)), p, const("MAPPING_DIGEST", hexdigest)


def verify(text):
    rows, p, recorded = parse_artifact(text)
    require(p["rom_sha256"] == PINNED_ROM_SHA256, "artifact is not bound to the pinned ROM")
    require(p["table_file_offset"] == species_map.TABLE_OFFSET, "table location changed")
    require(p["table_sha256"] == species_map.TABLE_HASH, "table hash changed")
    check_rows(rows)
    require(mapping_digest(rows) == recorded, "mapping digest mismatch: hand-edited or stale")
    require(render(rows, p) == text, "artifact is not byte-for-byte generator output")
    return rows, recorded


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", type=Path, help="Owner's private exact Unbound ROM")
    parser.add_argument("--source-root", type=Path, help="Private parent of pinned cfru/ and dpe/ checkouts")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--verify", action="store_true", help="ROM-free check of the committed artifact")
    args = parser.parse_args()
    try:
        if args.verify:
            rows, digest = verify(read_local(args.output, 1024 * 1024).decode("utf-8"))
            counts = Counter(FORM_CLASSES[c] for _, c in rows)
            print(f"Unbound species table verified: {len(rows)} rows, digest {digest}")
            print("  " + ", ".join(f"{name} {counts[name]}" for name in FORM_CLASSES))
            return 0
        require(args.rom is not None and args.source_root is not None, "--rom and --source-root are required")
        rows, provenance = derive(read_local(args.rom, ROM_SIZE), args.source_root)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(render(rows, provenance), encoding="utf-8")
        print(f"wrote {args.output} ({len(rows)} rows, digest {mapping_digest(rows)})")
        return 0
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
