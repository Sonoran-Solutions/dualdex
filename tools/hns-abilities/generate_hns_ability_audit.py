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
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
TARGET = HERE / "ability_inventory.tsv"
KOTLIN = ROOT / "app/src/main/java/com/dualdex/pokemon/hns/HnsAbilityAuditData.kt"
PIN = "1f42b74dff0e9fe942419845d040663dd829a973"
CONTEXT_RULES = HERE / "context_rules.json"
CONTEXT_CANDIDATES = {
    5, 277, 280,
    18, 79, 112, 148, 198, 255, 281, 282, 293, 186, 187, 188, 284, 285, 286, 287,
    2, 3, 4, 16, 22, 24, 26, 33, 34, 36, 37, 47, 54, 57, 58, 62, 64, 70, 74, 75,
    55, 80, 83, 84, 85, 86, 88, 91, 95, 97, 105, 106, 124, 128, 132, 133, 139, 140, 141,
    146, 152, 153, 154, 155, 160, 167, 168, 172, 192, 195, 201, 202, 215, 221, 222,
    223, 224, 234, 235, 236, 238, 243, 247, 250, 254, 259, 268, 271, 275, 290, 291,
    220, 244, 252, 264, 265, 270, 308, 89, 94, 96, 101, 110, 111, 116, 129, 136, 137, 138, 169, 173, 174, 179,
    178, 182, 184, 199, 200, 204, 206, 231, 232, 233, 246, 262, 263, 276, 288, 289, 292,
    63, 103, 120, 122, 125, 127, 159, 181, 217, 218, 249, 266, 267,
    13, 45, 76, 117, 226, 227, 228, 229, 245, 269,
}
POLICY = ROOT / "app/src/main/java/com/dualdex/calculator/HnsAbilityContextPolicy.kt"
GROUP_C_POLICY = ROOT / "app/src/main/java/com/dualdex/calculator/HnsGroupCPolicy.kt"


def pinned_species_constant(cpp_bin, upstream, macro):
    """Resolve one SPECIES_* macro through the pinned header with the ARM preprocessor.

    The Tera Shell rule keys on the exact live species ID of SPECIES_TERAPAGOS_TERASTAL, so the
    value is exported from the pinned catalogue instead of being maintained as a Kotlin literal.
    """
    with tempfile.NamedTemporaryFile("w", suffix=".c", delete=False) as handle:
        handle.write('#include "constants/species.h"\nint dualdex_species_probe = %s;\n' % macro)
        probe = handle.name
    try:
        out = subprocess.run([cpp_bin, "-P", "-I", str(upstream / "include"), probe],
                             capture_output=True, text=True, check=True).stdout
    finally:
        pathlib.Path(probe).unlink()
    match = re.search(r"dualdex_species_probe = (\d+);", out)
    if not match:
        raise SystemExit(f"could not resolve pinned {macro}")
    return int(match.group(1))


def load_extractor():
    path = ROOT / "tools/hns-data-pack/generate_hns_data_pack.py"
    spec = importlib.util.spec_from_file_location("hns_data_pack", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def kt(value):
    return json.dumps(value, ensure_ascii=False)


def validate_context_rules(upstream, abilities, decisions):
    """Check the manually reviewed request-local rules against exact pinned source lines.

    This file is deliberately hand-authored: source search results never generate a clearance.
    The checker only verifies that each reviewed predicate still points at the pinned text.
    """
    data = json.loads(CONTEXT_RULES.read_text())
    if set(map(int, data)) != CONTEXT_CANDIDATES:
        raise SystemExit("context_rules.json must cover exactly the reviewed first-wave abilities")
    policy = POLICY.read_text() + "\n" + GROUP_C_POLICY.read_text()
    all_rules = set()
    seen_rules = set()
    for raw_id, entry in data.items():
        aid = int(raw_id)
        expected = entry.get("global")
        if expected not in ("UNSUPPORTED_DAMAGE_RELEVANT", "MODELLED_HNS_CONDITIONAL") or \
                decisions.get(raw_id, {}).get("category") != expected:
            raise SystemExit(f"context rule {aid} must agree with its reviewed global ability category")
        if aid not in abilities:
            raise SystemExit(f"context rule refers to nonexistent ability {aid}")
        if not entry.get("fallback"):
            raise SystemExit(f"context rule {aid} needs a fail-closed fallback")

        rules = entry.get("context_rules", []) + entry.get("always_blocking", [])
        if not rules:
            raise SystemExit(f"context rule {aid} has no reviewed rule or blocking rationale")
        for rule in rules:
            name = rule.get("rule", "")
            if not name or (aid, name) in seen_rules:
                raise SystemExit(f"missing or duplicate contextual rule name: {name!r}")
            seen_rules.add((aid, name))
            all_rules.add(name)
            if f'"{name}"' not in policy and name not in policy:
                raise SystemExit(f"context rule {name} is not implemented in a reviewed H&S ability policy")
            if not rule.get("predicate") or not rule.get("rationale"):
                raise SystemExit(f"context rule {name} needs a predicate and source rationale")
            evidence = rule.get("evidence", [])
            if not evidence:
                raise SystemExit(f"context rule {name} needs pinned source evidence")
            for item in evidence:
                match = re.fullmatch(r"(.+):(\d+)", item.get("source", ""))
                if not match:
                    raise SystemExit(f"invalid source reference for {name}: {item.get('source')!r}")
                source_path = upstream / match.group(1)
                if not source_path.is_file():
                    raise SystemExit(f"missing pinned source file for {name}: {source_path}")
                source_lines = source_path.read_text(errors="replace").splitlines()
                line_no = int(match.group(2))
                if line_no < 1 or line_no > len(source_lines):
                    raise SystemExit(f"source line out of range for {name}: {item['source']}")
                if item.get("contains", "") not in source_lines[line_no - 1]:
                    raise SystemExit(f"pinned source evidence changed for {name}: {item['source']}")
                if "absentWithin" in item:
                    end_line = item.get("endLine")
                    if not isinstance(end_line, int) or end_line < line_no or end_line > len(source_lines):
                        raise SystemExit(f"invalid absence range for {name}: {item['source']}..{end_line}")
                    if any(item["absentWithin"] in line for line in source_lines[line_no - 1:end_line]):
                        raise SystemExit(f"pinned absence evidence changed for {name}: {item['source']}..{end_line}")

    required = {
        "attacker_move_execution_state_unobserved",
        "defender_armor_fixed_noncritical_hit",
        "defender_armor_critical_hit_conflict",
        "terapagos_full_hp_relevant",
        "hustle_physical_move",
        "guts_physical_move_with_status",
        "technician_attacker_bp_at_most_60",
        "iron_fist_punching_move",
        "water_bubble_attacker_water_move",
        "toxic_boost_physical_poison",
        "flare_boost_special_burn",
        "punk_rock_attacker_sound_move",
        "punk_rock_attacker_nonsound_move",
        "punk_rock_defender_sound_move",
        "punk_rock_defender_nonsound_move",
        "steely_spirit_holder_effective_steel_move",
        "steely_spirit_holder_effective_nonsteel_move",
        "steely_spirit_defender_singles_irrelevant",
        "steely_spirit_attacker_partner_deferred",
        "adaptability_without_stab",
        "adaptability_with_stab",
        "adaptability_stab_operands_unknown",
        "defender_adaptability_does_not_boost_incoming_damage",
        "sniper_critical_hit",
        "sniper_noncritical_hit",
        "sniper_defender_side",
        "sniper_crit_unknown",
        "tinted_lens_resisted_hit",
        "tinted_lens_not_resisted",
        "tinted_lens_defender_side",
        "tinted_lens_effectiveness_unknown",
        "neuroforce_super_effective_hit",
        "neuroforce_not_super_effective",
        "neuroforce_defender_side",
        "neuroforce_effectiveness_unknown",
        "filter_super_effective_hit",
        "filter_not_super_effective",
        "filter_attacker_side",
        "filter_effectiveness_unknown",
        "solid_rock_super_effective_hit",
        "solid_rock_not_super_effective",
        "solid_rock_attacker_side",
        "solid_rock_effectiveness_unknown",
        "prism_armor_super_effective_hit",
        "prism_armor_not_super_effective",
        "prism_armor_attacker_side",
        "prism_armor_effectiveness_unknown",
        "multiscale_full_hp",
        "multiscale_below_full_hp",
        "multiscale_attacker_side",
        "multiscale_hp_unknown",
        "shadow_shield_full_hp",
        "shadow_shield_below_full_hp",
        "shadow_shield_attacker_side",
        "shadow_shield_hp_unknown",
        "ice_scales_special_move",
        "ice_scales_physical_move",
        "ice_scales_attacker_side",
        "ice_scales_category_unknown",
        "filter_mold_breaker_unshielded",
        "filter_ability_shield_preserves",
        "solid_rock_mold_breaker_unshielded",
        "solid_rock_ability_shield_preserves",
        "multiscale_mold_breaker_unshielded",
        "multiscale_ability_shield_preserves",
        "prism_armor_mold_breaker_preserves",
        "shadow_shield_mold_breaker_preserves",
        "ice_scales_mold_breaker_unshielded",
        "ice_scales_ability_shield_preserves",
        "attack_stat_type_ability_defender_side",
        "attack_stat_type_ability_nonmatching_type",
        "attack_stat_type_ability_matching_type",
        "orichalcum_pulse_defender_side",
        "orichalcum_pulse_special_move",
        "orichalcum_pulse_without_raw_sun",
        "orichalcum_pulse_utility_umbrella",
        "orichalcum_pulse_physical_raw_sun",
    }
    if not required <= all_rules:
        raise SystemExit("context rules must retain live-state, type/category, Guts, Hustle, and bypass safety predicates")


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
    validate_context_rules(upstream, abilities, decisions)
    terapagos_terastal = pinned_species_constant(
        extractor.find_cpp_bin(args.cpp_bin), upstream, "SPECIES_TERAPAGOS_TERASTAL")
    cherrim_sunshine = pinned_species_constant(
        extractor.find_cpp_bin(args.cpp_bin), upstream, "SPECIES_CHERRIM_SUNSHINE")
    if "TERAPAGOS_TERASTAL_SPECIES_ID = HnsAbilityAuditData.TERAPAGOS_TERASTAL_SPECIES_ID" not in POLICY.read_text():
        raise SystemExit("HnsAbilityContextPolicy must take the Terapagos-Terastal ID from the generated audit data")
    source_refs = {}
    info_text = (upstream / "src/data/abilities.h").read_text()
    for path in (upstream / "src").rglob("*.c"):
        for line_no, line in enumerate(path.read_text(errors="replace").splitlines(), 1):
            for symbol in set(re.findall(r"\bABILITY_[A-Z0-9_]+\b", line)):
                source_refs.setdefault(symbol, []).append(f"{path.relative_to(upstream)}:{line_no}")
    categories = {"PROVEN_NO_DAMAGE_EFFECT", "MODELLED_EQUIVALENT", "MODELLED_HNS_SPECIFIC",
                  "MODELLED_HNS_CONDITIONAL", "UNSUPPORTED_DAMAGE_RELEVANT", "UNCLASSIFIED"}
    baseline_safe = {0, 15, 51, 77}
    baseline_conditional = {65, 66, 67, 68, 55, 62, 89, 101, 137, 138, 173, 178, 199, 200, 292}
    baseline_unsupported = {37, 47, 74, 91, 168, 255, 262, 282}
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
              "internal object HnsAbilityAuditData {",
              "    /** Pinned SPECIES_TERAPAGOS_TERASTAL (include/constants/species.h), resolved by the ARM preprocessor. */",
              f"    const val TERAPAGOS_TERASTAL_SPECIES_ID: Int = {terapagos_terastal}",
              "    /** Pinned SPECIES_CHERRIM_SUNSHINE (include/constants/species.h), resolved by the ARM preprocessor. */",
              f"    const val CHERRIM_SUNSHINE_SPECIES_ID: Int = {cherrim_sunshine}",
              "",
              "    val entries: List<HnsAbilityEntry> = listOf("]
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
