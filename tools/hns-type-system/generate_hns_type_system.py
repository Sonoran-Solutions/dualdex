#!/usr/bin/env python3
"""Generate the H&S 2.0.5 type chart and Fairy mappings from the pinned upstream source.

This is the authoritative source-check for the H&S 2.0.5 calculator type system
(issue #9, Gap C1).

It extracts:
  1. gTypeEffectivenessTable from src/data/types_info.h (with B_UPDATED_TYPE_MATCHUPS == GEN_9
     from include/config/battle.h).
  2. sPreFairyTypes (20 species) from src/pokemon.c.
  3. sFairyMoveAltTypes (34 moves) from src/pokemon.c.

Outputs:
  1. tools/calc-bundler/hns_type_chart.json
  2. app/src/main/java/com/dualdex/pokemon/hns/HnsFairyTypeMappings.kt

Usage:
  python3 tools/hns-type-system/generate_hns_type_system.py --upstream-dir <pokehns-expansion> [--verify]

Pinned upstream revision: 1f42b74dff0e9fe942419845d040663dd829a973
(tag Release-v2.0.5).
"""

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"

TYPES_ENUM = [
    "TYPE_NONE", "TYPE_NORMAL", "TYPE_FIGHTING", "TYPE_FLYING", "TYPE_POISON",
    "TYPE_GROUND", "TYPE_ROCK", "TYPE_BUG", "TYPE_GHOST", "TYPE_STEEL",
    "TYPE_MYSTERY", "TYPE_FIRE", "TYPE_WATER", "TYPE_GRASS", "TYPE_ELECTRIC",
    "TYPE_PSYCHIC", "TYPE_ICE", "TYPE_DRAGON", "TYPE_DARK", "TYPE_FAIRY", "TYPE_STELLAR"
]

TYPE_MAP = {
    "TYPE_NONE": "None",
    "TYPE_NORMAL": "Normal",
    "TYPE_FIGHTING": "Fighting",
    "TYPE_FLYING": "Flying",
    "TYPE_POISON": "Poison",
    "TYPE_GROUND": "Ground",
    "TYPE_ROCK": "Rock",
    "TYPE_BUG": "Bug",
    "TYPE_GHOST": "Ghost",
    "TYPE_STEEL": "Steel",
    "TYPE_MYSTERY": "???",
    "TYPE_FIRE": "Fire",
    "TYPE_WATER": "Water",
    "TYPE_GRASS": "Grass",
    "TYPE_ELECTRIC": "Electric",
    "TYPE_PSYCHIC": "Psychic",
    "TYPE_ICE": "Ice",
    "TYPE_DRAGON": "Dragon",
    "TYPE_DARK": "Dark",
    "TYPE_FAIRY": "Fairy",
    "TYPE_STELLAR": "Stellar",
}

STANDARD_19_TYPES = [
    "Normal", "Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel",
    "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark", "Fairy", "???"
]

# Canonical species name mapping for sPreFairyTypes
SPECIES_NAME_MAP = {
    "SPECIES_CLEFFA": "Cleffa",
    "SPECIES_CLEFAIRY": "Clefairy",
    "SPECIES_CLEFABLE": "Clefable",
    "SPECIES_IGGLYBUFF": "Igglybuff",
    "SPECIES_JIGGLYPUFF": "Jigglypuff",
    "SPECIES_WIGGLYTUFF": "Wigglytuff",
    "SPECIES_TOGEPI": "Togepi",
    "SPECIES_TOGETIC": "Togetic",
    "SPECIES_TOGEKISS": "Togekiss",
    "SPECIES_AZURILL": "Azurill",
    "SPECIES_MARILL": "Marill",
    "SPECIES_AZUMARILL": "Azumarill",
    "SPECIES_SNUBBULL": "Snubbull",
    "SPECIES_GRANBULL": "Granbull",
    "SPECIES_RALTS": "Ralts",
    "SPECIES_KIRLIA": "Kirlia",
    "SPECIES_GARDEVOIR": "Gardevoir",
    "SPECIES_MIME_JR": "Mime Jr.",
    "SPECIES_MR_MIME": "Mr. Mime",
    "SPECIES_MAWILE": "Mawile",
}

# Canonical move name mapping for sFairyMoveAltTypes
MOVE_NAME_MAP = {
    "MOVE_FAIRY_WIND": "Fairy Wind",
    "MOVE_DISARMING_VOICE": "Disarming Voice",
    "MOVE_DAZZLING_GLEAM": "Dazzling Gleam",
    "MOVE_MOONBLAST": "Moonblast",
    "MOVE_DRAINING_KISS": "Draining Kiss",
    "MOVE_PLAY_ROUGH": "Play Rough",
    "MOVE_BABY_DOLL_EYES": "Baby-Doll Eyes",
    "MOVE_AROMATIC_MIST": "Aromatic Mist",
    "MOVE_CRAFTY_SHIELD": "Crafty Shield",
    "MOVE_FAIRY_LOCK": "Fairy Lock",
    "MOVE_FLOWER_SHIELD": "Flower Shield",
    "MOVE_FLORAL_HEALING": "Floral Healing",
    "MOVE_MISTY_TERRAIN": "Misty Terrain",
    "MOVE_MISTY_EXPLOSION": "Misty Explosion",
    "MOVE_GEOMANCY": "Geomancy",
    "MOVE_LIGHT_OF_RUIN": "Light Of Ruin",
    "MOVE_FLEUR_CANNON": "Fleur Cannon",
    "MOVE_SPIRIT_BREAK": "Spirit Break",
    "MOVE_NATURES_MADNESS": "Nature's Madness",
    "MOVE_DECORATE": "Decorate",
    "MOVE_STRANGE_STEAM": "Strange Steam",
    "MOVE_SPARKLY_SWIRL": "Sparkly Swirl",
    "MOVE_ALLURING_VOICE": "Alluring Voice",
    "MOVE_SPRINGTIDE_STORM": "Springtide Storm",
    "MOVE_GUARDIAN_OF_ALOLA": "Guardian Of Alola",
    "MOVE_LETS_SNUGGLE_FOREVER": "Let's Snuggle Forever",
    "MOVE_TWINKLE_TACKLE": "Twinkle Tackle",
    "MOVE_MAX_STARFALL": "Max Starfall",
    "MOVE_G_MAX_FINALE": "G-Max Finale",
    "MOVE_G_MAX_SMITE": "G-Max Smite",
    "MOVE_MAGICAL_TORQUE": "Magical Torque",
    "MOVE_SWEET_KISS": "Sweet Kiss",
    "MOVE_CHARM": "Charm",
    "MOVE_MOONLIGHT": "Moonlight",
}


def extract_type_chart(upstream_dir: Path) -> dict:
    battle_h = (upstream_dir / "include/config/battle.h").read_text(encoding="utf-8")
    general_h = (upstream_dir / "include/config/general.h").read_text(encoding="utf-8")
    assert re.search(r"#define\s+GEN_LATEST\s+GEN_9", general_h), (
        "Expected GEN_LATEST == GEN_9 in include/config/general.h"
    )
    assert re.search(r"#define\s+B_UPDATED_TYPE_MATCHUPS\s+(GEN_9|GEN_LATEST)", battle_h), (
        "Expected B_UPDATED_TYPE_MATCHUPS == GEN_9 or GEN_LATEST in include/config/battle.h"
    )

    types_info_h = (upstream_dir / "src/data/types_info.h").read_text(encoding="utf-8")
    table_match = re.search(
        r"const uq4_12_t gTypeEffectivenessTable\[NUMBER_OF_MON_TYPES\]\[NUMBER_OF_MON_TYPES\]\s*=\s*\{([\s\S]*?)\};",
        types_info_h
    )
    assert table_match, "gTypeEffectivenessTable not found in src/data/types_info.h"
    table_body = table_match.group(1)

    macros = {
        "______": 1.0,
        "STL_RS": 1.0,  # GEN_9 >= GEN_6 (Steel does not resist Ghost/Dark)
        "PSN_RS": 0.5,  # GEN_9 >= GEN_2
        "BUG_RS": 1.0,  # GEN_9 >= GEN_2
        "PSY_RS": 2.0,  # GEN_9 >= GEN_2
        "FIR_RS": 0.5,  # GEN_9 >= GEN_2
    }

    full_matrix = {}
    for line in table_body.strip().splitlines():
        line = line.strip()
        if not line.startswith("["):
            continue
        m = re.match(r"\[(\w+)\]\s*=\s*\{([^}]+)\}", line)
        if not m:
            continue
        atk_type = m.group(1)
        values_str = m.group(2)
        tokens = [t.strip() for t in values_str.split(",") if t.strip()]
        assert len(tokens) == len(TYPES_ENUM), (
            f"Expected {len(TYPES_ENUM)} tokens for {atk_type}, got {len(tokens)}"
        )

        atk_name = TYPE_MAP[atk_type]
        full_matrix[atk_name] = {}
        for idx, token in enumerate(tokens):
            def_type = TYPES_ENUM[idx]
            def_name = TYPE_MAP[def_type]
            if token in macros:
                val = macros[token]
            elif token.startswith("X(") and token.endswith(")"):
                val = float(token[2:-1])
            else:
                raise ValueError(f"Unknown effectiveness token: {token}")
            full_matrix[atk_name][def_name] = val

    # Filter down to the standard 18 types + ???
    chart = {}
    for atk in STANDARD_19_TYPES:
        chart[atk] = {}
        for defn in STANDARD_19_TYPES:
            chart[atk][defn] = full_matrix[atk][defn]

    return chart


def extract_pre_fairy_types(upstream_dir: Path) -> list:
    pokemon_c = (upstream_dir / "src/pokemon.c").read_text(encoding="utf-8")
    m = re.search(
        r"static const struct\s*\{[^{}]*\}\s*sPreFairyTypes\[\]\s*=\s*\{([\s\S]*?)\};",
        pokemon_c
    )
    assert m, "sPreFairyTypes not found in src/pokemon.c"
    body = m.group(1)

    results = []
    for line in body.strip().splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        entry_match = re.search(
            r"\{\s*(\w+)\s*,\s*\{\s*(\w+)\s*,\s*(\w+)\s*\}\s*\}",
            line
        )
        if not entry_match:
            continue
        species_const = entry_match.group(1)
        t1_const = entry_match.group(2)
        t2_const = entry_match.group(3)

        assert species_const in SPECIES_NAME_MAP, f"Unknown species constant: {species_const}"
        assert t1_const in TYPE_MAP, f"Unknown type constant: {t1_const}"
        assert t2_const in TYPE_MAP, f"Unknown type constant: {t2_const}"

        species_name = SPECIES_NAME_MAP[species_const]
        t1 = TYPE_MAP[t1_const]
        t2 = TYPE_MAP[t2_const]

        types = [t1] if t1 == t2 else [t1, t2]
        results.append({
            "constant": species_const,
            "name": species_name,
            "types": types
        })

    assert len(results) == 20, f"Expected exactly 20 pre-Fairy species, got {len(results)}"
    return results


def extract_fairy_move_alt_types(upstream_dir: Path) -> list:
    pokemon_c = (upstream_dir / "src/pokemon.c").read_text(encoding="utf-8")
    m = re.search(
        r"static const struct\s*\{[^{}]*\}\s*sFairyMoveAltTypes\[\]\s*=\s*\{([\s\S]*?)\};",
        pokemon_c
    )
    assert m, "sFairyMoveAltTypes not found in src/pokemon.c"
    body = m.group(1)

    results = []
    for line in body.strip().splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        entry_match = re.search(
            r"\{\s*(\w+)\s*,\s*(\w+)\s*\}",
            line
        )
        if not entry_match:
            continue
        move_const = entry_match.group(1)
        alt_type_const = entry_match.group(2)

        assert move_const in MOVE_NAME_MAP, f"Unknown move constant: {move_const}"
        assert alt_type_const in TYPE_MAP, f"Unknown type constant: {alt_type_const}"

        move_name = MOVE_NAME_MAP[move_const]
        alt_type = TYPE_MAP[alt_type_const]

        results.append({
            "constant": move_const,
            "name": move_name,
            "altType": alt_type
        })

    assert len(results) == 34, f"Expected exactly 34 Fairy moves in sFairyMoveAltTypes, got {len(results)}"
    return results


def render_type_chart_json(chart: dict) -> str:
    return json.dumps(chart, indent=2) + "\n"


def render_kotlin_mappings(pre_fairy: list, fairy_moves: list) -> str:
    lines = [
        "package com.dualdex.pokemon.hns",
        "",
        "/**",
        " * Authoritative source-derived Fairy mode transformations for Heart & Soul 2.0.5.",
        " *",
        f" * Extracted deterministically from `PokemonHnS-Development/pokehns-expansion`",
        f" * at commit `{PINNED_COMMIT}` ({PINNED_TAG}).",
        " *",
        " * Covers:",
        " *  - `sPreFairyTypes` (20 species) from `src/pokemon.c:5704`",
        " *  - `sFairyMoveAltTypes` (34 moves) from `src/pokemon.c:5748`",
        " *",
        " * In H&S, when `tx_Mode_Fairy_Types == 0` (Fairy mode disabled):",
        " *  1. `GetSpeciesType` looks up `sPreFairyTypes` for species types;",
        " *  2. `GetMoveType` looks up `sFairyMoveAltTypes` for Fairy moves, falling back to Normal.",
        " */",
        "object HnsFairyTypeMappings {",
        "",
        f"    const val PINNED_COMMIT: String = \"{PINNED_COMMIT}\"",
        f"    const val PINNED_TAG: String = \"{PINNED_TAG}\"",
        "",
        "    /**",
        "     * Pinned pre-Fairy species typing (sPreFairyTypes, 20 species).",
        "     * Maps normalized species name to effective pre-Fairy type list.",
        "     */",
        "    val PRE_FAIRY_SPECIES_TYPES: Map<String, List<String>> = mapOf("
    ]

    for item in pre_fairy:
        types_str = ", ".join(f'"{t}"' for t in item["types"])
        lines.append(f'        "{item["name"].lower()}" to listOf({types_str}), // {item["name"]} ({item["constant"]})')

    lines.extend([
        "    )",
        "",
        "    /**",
        "     * Pinned alternate move typing for Fairy moves when Fairy is disabled (sFairyMoveAltTypes, 34 moves).",
        "     * Maps normalized move name to effective alternate type name.",
        "     */",
        "    val FAIRY_MOVE_ALT_TYPES: Map<String, String> = mapOf("
    ])

    for item in fairy_moves:
        lines.append(f'        "{item["name"].lower()}" to "{item["altType"]}", // {item["name"]} ({item["constant"]})')

    lines.extend([
        "    )",
        "",
        "    /**",
        "     * Returns the pre-Fairy types for [speciesName] if [speciesName] is retyped when Fairy is OFF,",
        "     * or null if this species does not have a pre-Fairy retyping.",
        "     */",
        "    fun getPreFairyTypes(speciesName: String): List<String>? =",
        "        PRE_FAIRY_SPECIES_TYPES[speciesName.trim().lowercase()]",
        "",
        "    /**",
        "     * Returns the alternate type for [moveName] when Fairy is OFF.",
        "     * In H&S line 5795: unlisted Fairy moves fall back to \"Normal\".",
        "     * If [moveIsFairy] is true, returns the mapped alternate type, or \"Normal\" if unlisted.",
        "     * If [moveIsFairy] is false, returns null.",
        "     */",
        "    fun getFairyMoveAltType(moveName: String, moveIsFairy: Boolean): String? {",
        "        if (!moveIsFairy) return null",
        "        return FAIRY_MOVE_ALT_TYPES[moveName.trim().lowercase()] ?: \"Normal\"",
        "    }",
        "}",
        ""
    ])

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Generate H&S 2.0.5 type system and Fairy mappings.")
    parser.add_argument("--upstream-dir", type=Path, default=Path("../upstream-hns/pokehns-expansion"))
    parser.add_argument("--verify", action="store_true", help="Verify committed files match upstream with zero drift")
    args = parser.parse_args()

    upstream_dir = args.upstream_dir
    if not (upstream_dir / "src/data/types_info.h").exists():
        for cand in [Path("/home/dq/Projects/upstream-hns/pokehns-expansion"), Path("upstream-hns/pokehns-expansion"), Path("../upstream-hns/pokehns-expansion")]:
            if (cand / "src/data/types_info.h").exists():
                upstream_dir = cand
                break

    if not (upstream_dir / "src/data/types_info.h").exists():
        print(f"error: upstream checkout not found at {args.upstream_dir}", file=sys.stderr)
        sys.exit(1)

    chart = extract_type_chart(upstream_dir)
    pre_fairy = extract_pre_fairy_types(upstream_dir)
    fairy_moves = extract_fairy_move_alt_types(upstream_dir)

    chart_json = render_type_chart_json(chart)
    kotlin_mappings = render_kotlin_mappings(pre_fairy, fairy_moves)

    repo_root = Path(__file__).resolve().parent.parent.parent
    target_json = repo_root / "tools/calc-bundler/hns_type_chart.json"
    target_kt = repo_root / "app/src/main/java/com/dualdex/pokemon/hns/HnsFairyTypeMappings.kt"

    if args.verify:
        if not target_json.exists():
            print(f"error: {target_json} does not exist", file=sys.stderr)
            sys.exit(1)
        if not target_kt.exists():
            print(f"error: {target_kt} does not exist", file=sys.stderr)
            sys.exit(1)

        curr_json = target_json.read_text(encoding="utf-8")
        curr_kt = target_kt.read_text(encoding="utf-8")

        if curr_json != chart_json:
            print(f"error: {target_json} differs from upstream-derived output", file=sys.stderr)
            sys.exit(1)
        if curr_kt != kotlin_mappings:
            print(f"error: {target_kt} differs from upstream-derived output", file=sys.stderr)
            sys.exit(1)

        print("H&S 2.0.5 type system verification passed with zero drift.")
        sys.exit(0)

    # Write files
    target_json.parent.mkdir(parents=True, exist_ok=True)
    target_json.write_text(chart_json, encoding="utf-8")
    print(f"Wrote {target_json}")

    target_kt.parent.mkdir(parents=True, exist_ok=True)
    target_kt.write_text(kotlin_mappings, encoding="utf-8")
    print(f"Wrote {target_kt}")


if __name__ == "__main__":
    main()
