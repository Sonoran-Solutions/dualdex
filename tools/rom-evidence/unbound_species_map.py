#!/usr/bin/env python3
"""Exact-ROM species/dex evidence, never a production or full-table exporter.

Python stdlib only; local public sources are optional and pinned by file hash.
Form classes describe source labels, not verified runtime transformations.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import re
import struct
import sys

from unbound_data import (HOLES, RESERVED, ROM_BASE, SOURCES, SPECIES_COUNT, TABLES,
                          names, parse_species_header, sha, span, u32)
from unbound_identity import ROM_SHA256, ROM_SIZE, exact_rom
from ups_identity import read_local, require

TABLE_OFFSET = 0x1A41FEC
TABLE_HASH = "412e36320450f467b0f25208f82e6e3bee89b0590ad98a0a7247b6d4da9b3d9b"
ACCESSORS = {
    "species_to_national": (0x43298, 0x24, "f39c323ae1455155cec8e74185107905116e184420b238fb08063d3e62d95ae1"),
    "inverse_entry_trampoline": (0x43200, 8, "b06070f9c4f872fbe2fff47369a87f6a52bcec4724098f69fc607c1c2339e994"),
    "inverse_search_and_bound": (0x165BB34, 0x94, "a47a5aea921f515302e703620e2aadb20408cea50fef5c2c78629320fc7325d3"),
}
POINTERS = {0x432B0: ROM_BASE + TABLE_OFFSET, 0x43204: 0x0965BB35,
            0x165BBB4: ROM_BASE + TABLE_OFFSET, 0x165BBC0: SPECIES_COUNT}
# Public metadata only. These files are not distributed with the tool.
SOURCE_FILES = {
    "cfru_species": ("cfru/include/constants/species.h", SOURCES["cfru"][1]),
    "cfru_dex": ("cfru/include/constants/pokedex.h", "5bafc6e53329ba75048848e4aa9a17e1ec20e8c4204d359d500d008b17488a51"),
    "dpe_species": ("dpe/include/species.h", SOURCES["dpe"][1]),
    "dpe_dex": ("dpe/include/pokedex.h", "b8e1f6e288482dd150f9c851cf90d27074d4e787bba09aeb5cb72f0faef7458b"),
    "dpe_table": ("dpe/src/Species_To_Pokdex_Table.c", "91cfa0c092ae55da81b6cf564e385e6d6a7d549a87edcac02381117a6d659b58"),
}
RUNTIME_PINS = {130: 130, 230: 230, 186: 186, 1002: 785, 1035: 89, 942: 725}
SELECTED = (0, 252, 254, 277, 413, 440, 706, 707, 713, 737, 758, 869,
            909, 919, 920, 942, 1002, 1020, 1035, 1036, 1102, 1194,
            1212, 1218, 1234, 1251, 1254, 1255, 1260, 1293)
# Exact label exceptions, not fuzzy name matching or canonical fallbacks.
LABEL_ALIASES = {"DARMANITANZEN": "DARMANITAN", "ASHGRENINJA": "GRENINJA"}
COSMETIC_PREFIXES = ("UNOWN_", "BURMY_", "SHELLOS_", "GASTRODON_", "DEERLING_",
                     "SAWSBUCK_", "FLABEBE_", "FLOETTE_", "FLORGES_", "FURFROU_",
                     "VIVILLON_", "PIKACHU_CAP_", "ALCREMIE_")
COSMETIC_LABELS = {"UNFEZANT_F", "FRILLISH_F", "JELLICENT_F", "HIPPOPOTAS_F",
                   "HIPPOWDON_F", "PYROAR_FEMALE", "MAGEARNA_P", "PICHU_SPIKY",
                   "XERNEAS_NATURAL", "SINISTEA_CHIPPED", "POLTEAGEIST_CHIPPED", "ZARUDE_DADA"}


def validate_layout(rom):
    for label, (offset, size, digest) in ACCESSORS.items():
        require(sha(span(rom, offset, size)) == digest, f"accessor mismatch: {label}")
    for offset, expected in POINTERS.items():
        require(u32(rom, offset) == expected, f"literal mismatch at {offset:#x}")


def read_dex_table(rom, offset, species_count):
    """The accessor treats zero specially; physical row zero represents ID one."""
    require(type(species_count) is int and 2 <= species_count <= 4096,
            "invalid species domain")
    raw = span(rom, offset, (species_count - 1) * 2)
    return [0, *struct.unpack(f"<{species_count - 1}H", raw)]


def parse_dex_header(raw):
    require(len(raw) <= 128 * 1024, "dex header exceeds size limit")
    result = {}
    for label, number in re.findall(r"^#define NATIONAL_DEX_([A-Z0-9_]+)\s+(0x[0-9a-fA-F]+|[0-9]+)\b",
                                    raw.decode("utf-8"), re.M):
        value = int(number, 0)
        require(0 <= value <= 1025 and result.get(label, value) == value,
                "invalid/conflicting dex constant")
        result[label] = value
    require(bool(result), "no numeric dex constants")
    return result


def parse_dpe_table(raw, species, dex):
    """Accept the pinned designated-initializer grammar only; never evaluate C."""
    require(len(raw) <= 128 * 1024, "DPE table exceeds size limit")
    text = raw.decode("utf-8")
    require(text.count("{") == text.count("}") == 1, "unexpected table structure")
    body = text.split("{", 1)[1].split("}", 1)[0]
    by_label = {label: index for index, labels in species.items() for label in labels}
    pattern = r"\[\s*(SPECIES_[A-Z0-9_]+|[0-9]+)\s*-\s*1\s*\]\s*=\s*NATIONAL_DEX_([A-Z0-9_]+)\s*,"
    values = {}
    for match in re.finditer(pattern, body):
        source, target = match.groups()
        if source.startswith("SPECIES_"):
            require(source[8:] in by_label, "unknown species constant")
            index = by_label[source[8:]]
        else:
            index = int(source)
        require(1 <= index < 4096 and index not in values, "invalid/duplicate table index")
        require(target in dex, "unknown dex constant")
        values[index] = dex[target]
    require(bool(values) and not re.sub(pattern, "", body).strip(), "unparsed DPE initializer")
    # No inferred zero initialization outside the explicitly supplied source domain.
    require(set(values) == set(range(1, max(values) + 1)), "DPE initializer has holes")
    return values


def canonical_label(label, dex, aliases=False):
    if aliases:
        label = LABEL_ALIASES.get(label, label)
    matches = [base for base in dex if label == base or label.startswith(base + "_")]
    return max(matches, key=len) if matches else None


def compare_mapping(mapping, reference):
    covered = sorted(set(range(len(mapping))) & reference.keys())
    mismatches = [dict(id=i, rom_dex=mapping[i], source_dex=reference[i])
                  for i in covered if mapping[i] != reference[i]]
    return dict(status="VERIFIED", compared_ids=len(covered), agreeing_ids=len(covered) - len(mismatches),
                disagreements=mismatches, uncovered_ids=[i for i in range(len(mapping)) if i not in reference],
                outside_rom_domain_ids=sorted(reference.keys() - set(covered)))


def classify(mapping, labels, dex):
    """Provisional source-label taxonomy; base = first ROM row for each dex.

    Cosmetic is a visual-label bucket, NOT proof of identical stats/moves or
    interchangeable forms. Regional includes source-labelled precursor slots.
    Other alternate forms deliberately keep their own class.
    """
    first = {}
    for i, number in enumerate(mapping):
        if number:
            first.setdefault(number, i)
    classes = {}
    for i, number in enumerate(mapping):
        if i in RESERVED or i in HOLES:
            require(number == 0, "reserved species has nonzero dex")
            category = "reserved_hole"
        elif labels.get(i) == ["SHADOW_WARRIOR"]:
            require(number == 0, "custom species has unexpected dex")
            category = "custom_unbound"
        else:
            require(number > 0 and i in labels and len(labels[i]) == 1,
                    f"unresolved source identity at {i}")
            label = labels[i][0]
            base = canonical_label(label, dex, aliases=True)
            require(base is not None and dex[base] == number, f"source/ROM dex conflict at {i}")
            if first[number] == i:
                category = "base_species"
            elif label.endswith("_GIGA"):
                category = "gigantamax"
            elif "MEGA" in label.split("_") or label.endswith("_PRIMAL"):
                category = "mega_primal"
            elif (label in COSMETIC_LABELS or label.startswith(COSMETIC_PREFIXES)) and label != "FLOETTE_ETERNAL":
                category = "cosmetic_form"
            elif re.search(r"_(A|G|H)(?:_ZEN)?$", label):
                category = "regional_form"
            else:
                category = "other_alternate_form"
        classes[i] = category
    return classes


def compare_sources(mapping, species_names, source_root):
    raw, metadata = {}, {}
    for key, (relative, digest) in SOURCE_FILES.items():
        raw[key] = read_local(source_root / relative, 128 * 1024)
        require(sha(raw[key]) == digest, f"pinned source mismatch: {key}")
        project, path = relative.split("/", 1)
        _, _, commit, repo, _ = SOURCES[project]
        metadata[key] = dict(sha256=digest, commit=commit,
                             url=f"https://github.com/{repo}/blob/{commit}/{path}")
    labels = {p: parse_species_header(raw[p + "_species"]) for p in ("dpe", "cfru")}
    dex = {p: parse_dex_header(raw[p + "_dex"]) for p in labels}
    direct = parse_dpe_table(raw["dpe_table"], labels["dpe"], dex["dpe"])
    comparisons = {"dpe_table": compare_mapping(mapping, direct)}
    for project in labels:
        implied, unresolved = {}, []
        for i, aliases in labels[project].items():
            candidates = {dex[project][base] for label in aliases
                          if (base := canonical_label(label, dex[project])) is not None}
            require(len(candidates) <= 1, "conflicting source aliases")
            if candidates:
                implied[i] = candidates.pop()
            else:
                unresolved.append(i)
        comparisons[project + "_constants"] = dict(compare_mapping(mapping, implied),
            method="Longest exact canonical label or underscore-delimited suffix; no aliases/guesses",
            declared_species_ids=len(labels[project]), unresolved_label_ids=sorted(unresolved))
    classes = classify(mapping, labels["cfru"], dex["cfru"])
    label_differences = [dict(id=i, cfru=labels["cfru"][i], dpe=labels["dpe"][i])
                         for i in sorted(labels["cfru"].keys() & labels["dpe"].keys())
                         if set(labels["cfru"][i]) != set(labels["dpe"][i])]
    # Reuse the established text-only comparison; numeric agreement does not settle form names.
    from unbound_data import compare_source
    return dict(status="OBSERVED", files=metadata, numeric=comparisons,
                label_disagreements=label_differences,
                text_only={p: compare_source(species_names, values) for p, values in labels.items()},
                reviewed_noncanonical_labels=dict(
                    alias_dex_agreement=[dict(id=i, label=k, canonical_label=v, dex=mapping[i])
                                         for i, values in labels["cfru"].items()
                                         for k, v in LABEL_ALIASES.items() if k in values],
                    reserved_egg="Zero in ROM and DPE table",
                    shadow_warrior="Zero in ROM; DPE assigns Cresselia (488), not accepted")), classes, labels["cfru"]


def check_runtime_pins(mapping, raw):
    require(len(raw) <= 256 * 1024, "runtime evidence exceeds limit")
    evidence = json.loads(raw)
    require(evidence["inputs"]["rom"]["sha256"] == ROM_SHA256, "runtime evidence ROM mismatch")
    require(len(evidence["party"]) == len(RUNTIME_PINS), "runtime evidence party count changed")
    pins = {int(row["raw_selected"]["species_word"], 16): row["ui_transcription"]["dex_no"]
            for row in evidence["party"]}
    require(pins == RUNTIME_PINS, "runtime evidence pins changed")
    require(all(mapping[i] == n for i, n in pins.items()), "runtime dex pin disagreement")
    return dict(status="OBSERVED", evidence="unbound-runtime-observation.json", sha256=sha(raw),
                reused=True, new_core_run=False, agreeing_pins=len(pins),
                pins=[dict(id=i, dex=n) for i, n in sorted(pins.items())])


def inspect(rom, source_root=None):
    identity = exact_rom(rom)  # Gate BEFORE offsets, sources, or runtime evidence.
    validate_layout(rom)
    offset = u32(rom, 0x432B0) - ROM_BASE
    mapping = read_dex_table(rom, offset, SPECIES_COUNT)
    raw_table = span(rom, offset, (SPECIES_COUNT - 1) * 2)
    require(sha(raw_table) == TABLE_HASH, "dex table hash mismatch")
    require(max(mapping) == 905 and set(mapping) == set(range(906)), "unexpected dex domain")
    require({i for i, n in enumerate(mapping) if n == 0} == RESERVED | HOLES | {706},
            "unexpected zero-dex species")
    name_offset, count, stride = TABLES["species_names"]
    species_names = names(rom, name_offset, count, stride)
    require(all(row["status"] == "VERIFIED" for row in species_names), "invalid species names")
    comparisons, classes, labels = dict(status="NOT_VERIFIED"), {}, {}
    if source_root is not None:
        comparisons, classes, labels = compare_sources(mapping, species_names, source_root)
    evidence = Path(__file__).with_name("evidence") / "unbound-runtime-observation.json"
    pins = check_runtime_pins(mapping, read_local(evidence, 256 * 1024))
    selected = [dict(id=i, dex=mapping[i], name=species_names[i]["name"],
                     numeric_status="VERIFIED", source_labels=labels.get(i, []),
                     classification=classes.get(i), form_semantics="NOT_VERIFIED") for i in SELECTED]
    multiplicity = Counter(n for n in mapping if n)
    return dict(schema_version=1, task_id="unbound-species", status="VERIFIED",
        scope="Static numeric mapping only; source form classes provisional; no production support",
        inspector_sha256=sha(Path(__file__).read_bytes()),
        dependency_sha256={name: sha(Path(__file__).with_name(name).read_bytes())
                           for name in ("unbound_data.py", "unbound_identity.py", "rowe_text.py", "ups_identity.py")},
        rom=identity, release_label=dict(owner_claim="2.1.1.1", status="NOT_VERIFIED"),
        table=dict(status="VERIFIED", file_offset=f"0x{offset:08x}", rom_address=f"0x{ROM_BASE + offset:08x}",
                   end_file_offset_exclusive=f"0x{offset + len(raw_table):08x}", physical_entries=SPECIES_COUNT - 1,
                   stride=2, encoding="little-endian u16", index="species - 1; species 0 returns 0 without read",
                   bytes=len(raw_table), sha256=sha(raw_table), logical_species_count=SPECIES_COUNT,
                   accessor_upper_bound="NONE; inspector enforces internal IDs 0..1293",
                   count_proof="Inverse search at 0x0965bb34 uses same pointer and exclusive bound 1294"),
        accessors={key: dict(file_offset=f"0x{o:08x}", bytes=n, sha256=h) for key, (o, n, h) in ACCESSORS.items()},
        numeric_domain=dict(status="VERIFIED", minimum_positive_dex=1, maximum_dex=905,
                            distinct_positive_dex=len(multiplicity), nonzero_rows=sum(multiplicity.values()),
                            zero_dex_ids=[i for i, n in enumerate(mapping) if n == 0],
                            shared_dex_numbers=sum(n > 1 for n in multiplicity.values()),
                            extra_rows_sharing_dex=sum(n - 1 for n in multiplicity.values()),
                            maximum_rows_per_dex=max(multiplicity.values())),
        classification=dict(status="OBSERVED" if classes else "NOT_VERIFIED",
                            counts=dict(sorted(Counter(classes.values()).items())),
                            classified_rows=len(classes), runtime_form_semantics="NOT_VERIFIED",
                            base_rule="First internal row per positive dex; canonical default form NOT_VERIFIED",
                            cosmetic_rule="Source visual-label candidates only; mechanical equivalence NOT_VERIFIED",
                            regional_rule="Source A/G/H labels, including precursor slots; runtime purpose NOT_VERIFIED"),
        source_comparisons=comparisons, reused_runtime_pins=pins, representative_rows=selected,
        production_support="NOT_VERIFIED", form_display_names="NOT_VERIFIED")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom", required=True, type=Path)
    parser.add_argument("--source-root", type=Path, help="Private parent of pinned cfru/ and dpe/ checkouts")
    args = parser.parse_args()
    try:
        report = inspect(read_local(args.rom, ROM_SIZE), args.source_root)
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
