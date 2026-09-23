#!/usr/bin/env python3
"""Validate the reviewed ability decisions against the pinned H&S enum and table.

The generator collects identities and source references. It never infers safety from
an absent reference: only the reviewed decisions file can promote an ability.
"""
import argparse
import csv
import importlib.util
import json
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
TARGET = HERE / "ability_inventory.tsv"
KOTLIN = ROOT / "app/src/main/java/com/dualdex/pokemon/hns/HnsAbilityAuditData.kt"
PIN = "1f42b74dff0e9fe942419845d040663dd829a973"


def load_extractor():
    path = ROOT / "tools/hns-data-pack/generate_hns_data_pack.py"
    spec = importlib.util.spec_from_file_location("hns_data_pack", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def kt(value):
    return json.dumps(value, ensure_ascii=False)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream-dir", required=True)
    parser.add_argument("--cpp-bin")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    upstream = pathlib.Path(args.upstream_dir).resolve()
    if subprocess.check_output(["git", "-C", str(upstream), "rev-parse", "HEAD"], text=True).strip() != PIN:
        raise SystemExit("wrong upstream commit")
    extractor = load_extractor()
    extractor.verify_git_commit(str(upstream))
    abilities = extractor.extract_abilities(extractor.find_cpp_bin(args.cpp_bin), str(upstream))
    abilities[0] = {"id": 0, "constant": "ABILITY_NONE", "name": "-------"}
    if set(abilities) != set(range(311)):
        raise SystemExit("ability IDs are missing, duplicated, or outside the pinned domain")
    decisions = json.loads((HERE / "decisions.json").read_text())
    if set(map(int, decisions)) - set(abilities):
        raise SystemExit("decision refers to a nonexistent ability")
    source_refs = {}
    info_text = (upstream / "src/data/abilities.h").read_text()
    for path in (upstream / "src").rglob("*.c"):
        for line_no, line in enumerate(path.read_text(errors="replace").splitlines(), 1):
            for symbol in set(re.findall(r"\bABILITY_[A-Z0-9_]+\b", line)):
                source_refs.setdefault(symbol, []).append(f"{path.relative_to(upstream)}:{line_no}")
    categories = {"PROVEN_NO_DAMAGE_EFFECT", "MODELLED_EQUIVALENT", "MODELLED_HNS_SPECIFIC",
                  "MODELLED_HNS_CONDITIONAL", "UNSUPPORTED_DAMAGE_RELEVANT", "UNCLASSIFIED"}
    baseline_safe = {0, 15, 51, 77}
    baseline_conditional = {65, 66, 67, 68}
    baseline_unsupported = {37, 47, 62, 74, 91, 137, 168, 255, 262, 282}
    rows = []
    for aid, ability in sorted(abilities.items()):
        decision = decisions.get(str(aid), {})
        cat = decision.get("category", "UNCLASSIFIED")
        if cat not in categories:
            raise SystemExit(f"invalid category for {aid}")
        if cat != "UNCLASSIFIED" and not decision.get("rationale"):
            raise SystemExit(f"missing review rationale for {aid}")
        symbol = ability["constant"]
        raw = ability["name"]
        display = "None" if aid == 0 else raw.title().replace("'S", "'s")
        if symbol == "ABILITY_RKS_SYSTEM":
            display = "RKS System"
        info_entry = re.search(r"\[" + symbol + r"\]\s*=\s*\{(.*?)(?=\n    \[ABILITY_|\Z)", info_text, re.S)
        description = (re.search(r'\.description\s*=\s*COMPOUND_STRING\("([^\"]+)', info_entry.group(1))
                       if info_entry else None)
        if not description:
            raise SystemExit(f"missing pinned description for {symbol}")
        # Retain canonical punctuation (notably Dragon's Maw and Soul-Heart).
        refs = source_refs.get(symbol, []).copy()
        path_priority = ("src/battle_util.c:", "src/battle_move_resolution.c:",
                         "src/battle_script_commands.c:", "src/battle_end_turn.c:",
                         "src/battle_main.c:", "src/battle_hold_effects.c:")
        refs.sort(key=lambda x: (next((n for n, prefix in enumerate(path_priority)
                                       if x.startswith(prefix)), len(path_priority)), x))
        evidence = decision.get("evidence", "")
        if evidence and not (upstream / evidence.split(":")[0]).exists():
            raise SystemExit(f"missing evidence path for {aid}: {evidence}")
        current = ("PROVEN_NO_DAMAGE_EFFECT" if aid in baseline_safe else
                   "MODELLED_HNS_CONDITIONAL" if aid in baseline_conditional else
                   "UNSUPPORTED_DAMAGE_RELEVANT" if aid in baseline_unsupported else "UNCLASSIFIED")
        rationale = decision.get("rationale", "Unresolved: no source-backed safety decision yet; fail closed.")
        rows.append((str(aid), symbol, display, current, cat,
                     evidence or ", ".join(refs[:4]) or "src/data/abilities.h (catalogue only)",
                     f"Pinned description: {description.group(1)} {rationale}"))
    if len({r[1] for r in rows}) != len(rows) or len({r[0] for r in rows}) != len(rows):
        raise SystemExit("duplicate canonical symbol or ID")
    import io
    output = io.StringIO()
    writer = csv.writer(output, delimiter="\t", lineterminator="\n")
    writer.writerow(("id", "symbol", "display_name", "current_category", "proposed_category", "evidence", "rationale"))
    writer.writerows(rows)
    generated = output.getvalue()
    kotlin = ["package com.dualdex.pokemon.hns", "", "// Generated by tools/hns-abilities/generate_hns_ability_audit.py. Do not edit.",
              "internal object HnsAbilityAuditData {", "    val entries: List<HnsAbilityEntry> = listOf("]
    for aid, symbol, display, current, cat, evidence, rationale in rows:
        # Use the enum symbol as the unique canonical name; the display name can repeat.
        kotlin.append(f"        HnsAbilityEntry({aid}, {kt(symbol.removeprefix('ABILITY_'))}, {kt(display)}, HnsAbilityCategory.{cat}, {kt(rationale)}),")
    kotlin.extend(["    )", "}", ""])
    generated_kt = "\n".join(kotlin)
    if args.check:
        if TARGET.read_text() != generated or KOTLIN.read_text() != generated_kt:
            raise SystemExit("H&S ability inventory or Kotlin registry differs from pinned source/review decisions")
    else:
        TARGET.write_text(generated)
        KOTLIN.write_text(generated_kt)
    counts = {cat: sum(r[4] == cat for r in rows) for cat in sorted(categories)}
    print(f"{len(rows)} pinned abilities: {counts}")


if __name__ == "__main__":
    main()
