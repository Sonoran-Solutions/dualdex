#!/usr/bin/env python3
"""
tools/hns-data-pack/generate_hns_data_pack.py

Extracts authoritative Pokemon species and moves data from the pinned
Pokemon Heart & Soul 2.0.5 upstream source checkout and generates the Kotlin
HeartAndSoul205DataPack.

Provenance:
  Repository: PokemonHnS-Development/pokehns-expansion
  Tag: Release-v2.0.5
  Commit: 1f42b74dff0e9fe942419845d040663dd829a973
  Extraction: Source / Preprocessor (arm-none-eabi-cpp) against build configuration
  ROM dependency: NONE (Zero commercial ROM or byte-scanning dependency)
"""

import argparse
import os
import re
import subprocess
import sys

PINNED_COMMIT_SHA = "1f42b74dff0e9fe942419845d040663dd829a973"

DEFAULT_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_TARGET_FILE = os.path.join(
    DEFAULT_REPO_ROOT,
    "app/src/main/java/com/dualdex/pokemon/hns/HeartAndSoul205DataPack.kt"
)

# Search paths for upstream H&S checkout
DEFAULT_UPSTREAM_SEARCH_PATHS = [
    os.environ.get("HNS_UPSTREAM_DIR"),
    os.path.join(os.path.dirname(DEFAULT_REPO_ROOT), "upstream-hns/pokehns-expansion"),
    "/home/dq/Projects/upstream-hns/pokehns-expansion",
]

# Search paths for arm-none-eabi-cpp or toolchain
DEFAULT_CPP_SEARCH_PATHS = [
    os.environ.get("ARM_CPP"),
    "/home/dq/opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin/arm-none-eabi-cpp",
    "arm-none-eabi-cpp",
]

TYPE_MAP = {
    "TYPE_NORMAL": "PokemonType.NORMAL",
    "TYPE_FIGHTING": "PokemonType.FIGHTING",
    "TYPE_FLYING": "PokemonType.FLYING",
    "TYPE_POISON": "PokemonType.POISON",
    "TYPE_GROUND": "PokemonType.GROUND",
    "TYPE_ROCK": "PokemonType.ROCK",
    "TYPE_BUG": "PokemonType.BUG",
    "TYPE_GHOST": "PokemonType.GHOST",
    "TYPE_STEEL": "PokemonType.STEEL",
    "TYPE_FIRE": "PokemonType.FIRE",
    "TYPE_WATER": "PokemonType.WATER",
    "TYPE_GRASS": "PokemonType.GRASS",
    "TYPE_ELECTRIC": "PokemonType.ELECTRIC",
    "TYPE_PSYCHIC": "PokemonType.PSYCHIC",
    "TYPE_ICE": "PokemonType.ICE",
    "TYPE_DRAGON": "PokemonType.DRAGON",
    "TYPE_DARK": "PokemonType.DARK",
    "TYPE_FAIRY": "PokemonType.FAIRY",
    "TYPE_STELLAR": "PokemonType.STELLAR",
}

CATEGORY_MAP = {
    "DAMAGE_CATEGORY_PHYSICAL": "MoveCategory.PHYSICAL",
    "DAMAGE_CATEGORY_SPECIAL": "MoveCategory.SPECIAL",
    "DAMAGE_CATEGORY_STATUS": "MoveCategory.STATUS",
}

SPECIAL_NAMES = {
    "HO-OH": "Ho-Oh",
    "PORYGON-Z": "Porygon-Z",
    "TYPE: NULL": "Type: Null",
    "JANGMO-O": "Jangmo-o",
    "HAKAMO-O": "Hakamo-o",
    "KOMMO-O": "Kommo-o",
    "FARFETCH'D": "Farfetch'd",
    "SIRFETCH'D": "Sirfetch'd",
    "MR. MIME": "Mr. Mime",
    "MIME JR.": "Mime Jr.",
    "FLABEBE": "Flabébé",
}


def find_upstream_dir(provided_dir):
    if provided_dir:
        if os.path.isdir(provided_dir):
            return os.path.abspath(provided_dir)
        raise FileNotFoundError(f"Specified upstream directory does not exist: {provided_dir}")

    for path in DEFAULT_UPSTREAM_SEARCH_PATHS:
        if path and os.path.isdir(path):
            return os.path.abspath(path)

    raise FileNotFoundError(
        "Could not locate upstream pokehns-expansion checkout. "
        "Set HNS_UPSTREAM_DIR or specify --upstream-dir."
    )


def find_cpp_bin(provided_bin):
    if provided_bin:
        return provided_bin

    for path in DEFAULT_CPP_SEARCH_PATHS:
        if not path:
            continue
        if os.path.isabs(path) and os.path.exists(path):
            return path
        # Check PATH
        which_result = subprocess.run(["which", path], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if which_result.returncode == 0:
            return path

    raise FileNotFoundError(
        "Could not locate arm-none-eabi-cpp binary. "
        "Set ARM_CPP or specify --cpp-bin."
    )


def verify_git_commit(upstream_dir):
    try:
        result = subprocess.run(
            ["git", "-C", upstream_dir, "rev-parse", "HEAD"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
            text=True
        )
        head_commit = result.stdout.strip()
    except Exception as e:
        raise RuntimeError(f"Failed to query git commit in {upstream_dir}: {e}")

    if head_commit != PINNED_COMMIT_SHA:
        raise ValueError(
            f"FATAL: Upstream Git checkout commit '{head_commit}' does not match "
            f"required pinned commit '{PINNED_COMMIT_SHA}'. Refusing to generate."
        )


def run_cpp(cpp_bin, upstream_dir, src_rel_path):
    src_abs = os.path.join(upstream_dir, src_rel_path)
    inc_abs = os.path.join(upstream_dir, "include")
    if not os.path.exists(src_abs):
        raise FileNotFoundError(f"Source file not found: {src_abs}")

    cmd = [
        cpp_bin,
        "-iquote", inc_abs,
        "-DMODERN=1",
        "-DTESTING=0",
        "-DPOKEMON_HNS",
        "-std=gnu17",
        src_abs
    ]
    try:
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, text=True)
        return proc.stdout
    except subprocess.CalledProcessError as e:
        raise RuntimeError(f"Preprocessor failed on {src_rel_path}: {e.stderr}")


def eval_simple_c_expr(expr):
    """Evaluates simple ternary expressions produced by preprocessor (e.g. '8 >= 1 ? TYPE_DARK : TYPE_NORMAL')."""
    expr = expr.strip()
    while expr.startswith("(") and expr.endswith(")"):
        depth = 0
        wraps = True
        for i, c in enumerate(expr[:-1]):
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
            if depth == 0:
                wraps = False
                break
        if wraps:
            expr = expr[1:-1].strip()
        else:
            break

    if "?" in expr:
        depth = 0
        q_pos = -1
        c_pos = -1
        for i, c in enumerate(expr):
            if c in "({[":
                depth += 1
            elif c in ")}]":
                depth -= 1
            elif c == "?" and depth == 0 and q_pos == -1:
                q_pos = i
            elif c == ":" and depth == 0 and q_pos != -1:
                c_pos = i
                break
        if q_pos != -1 and c_pos != -1:
            cond_str = expr[:q_pos].strip()
            true_str = expr[q_pos+1:c_pos].strip()
            false_str = expr[c_pos+1:].strip()
            py_cond = cond_str.replace("&&", " and ").replace("||", " or ").replace("!", " not ")
            res = bool(eval(py_cond, {"__builtins__": {}}))
            return eval_simple_c_expr(true_str if res else false_str)

    return expr


def format_species_name(raw_name):
    clean = raw_name.strip()
    if clean in SPECIAL_NAMES:
        return SPECIAL_NAMES[clean]

    parts = clean.split("-")
    formatted_parts = []
    for p in parts:
        p = p.strip()
        if p.upper() in ["ALOLAN", "ALOLA"]:
            formatted_parts.append("Alola")
        elif p.upper() in ["GALARIAN", "GALAR"]:
            formatted_parts.append("Galar")
        elif p.upper() in ["HISUIAN", "HISUI"]:
            formatted_parts.append("Hisui")
        elif p.upper() in ["PALDEAN", "PALDEA"]:
            formatted_parts.append("Paldea")
        elif p.upper() == "MEGA":
            formatted_parts.append("Mega")
        elif p.upper() in ["GMAX", "G-MAX"]:
            formatted_parts.append("G-Max")
        elif p.upper() == "H":
            formatted_parts.append("H")
        elif p.upper() == "A":
            formatted_parts.append("A")
        elif p.upper() == "G":
            formatted_parts.append("G")
        else:
            words = p.split()
            formatted_words = [w.capitalize() for w in words]
            formatted_parts.append(" ".join(formatted_words))
    return "-".join(formatted_parts)


def format_move_name(raw_name):
    words = raw_name.strip().split()
    formatted_words = []
    for w in words:
        if w.upper() == "G-MAX":
            formatted_words.append("G-Max")
        elif w.upper() == "V-CREATE":
            formatted_words.append("V-create")
        elif "-" in w:
            sub = [s.capitalize() for s in w.split("-")]
            formatted_words.append("-".join(sub))
        else:
            formatted_words.append(w.capitalize())
    return " ".join(formatted_words)


def extract_species(cpp_bin, upstream_dir):
    output = run_cpp(cpp_bin, upstream_dir, "src/pokemon.c")
    start = output.find("gSpeciesInfo[] =")
    if start == -1:
        start = output.find("gSpeciesInfo")
    if start == -1:
        raise ValueError("Could not find gSpeciesInfo in preprocessed src/pokemon.c")

    idx = output.find("{", start)
    pos = idx + 1
    length = len(output)

    species_dict = {}

    while pos < length:
        m = re.search(r"\[\s*(\d+)\s*\]\s*=\s*\{", output[pos:pos+5000])
        if not m:
            break
        species_id = int(m.group(1))
        brace_start = pos + m.end() - 1

        depth = 1
        p = brace_start + 1
        while p < length and depth > 0:
            ch = output[p]
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
            p += 1
        body = output[brace_start+1:p-1]
        pos = p

        if species_id == 0:  # SPECIES_NONE
            continue

        name_m = re.search(r'\.speciesName\s*=\s*_\(\"([^\"]+)\"\)', body)
        if not name_m:
            continue
        raw_name = name_m.group(1)
        if raw_name in ["??????????", "Egg", "EGG"] or not raw_name.strip():
            continue

        types_m = re.search(r'\.types\s*=\s*\{([^}]+)\}', body)
        if not types_m:
            raise ValueError(f"Could not extract types for species {species_id} ({raw_name})")

        types_content = types_m.group(1).strip()
        raw_types = []
        depth = 0
        cur = ""
        for c in types_content:
            if c in "({[":
                depth += 1
                cur += c
            elif c in ")}]":
                depth -= 1
                cur += c
            elif c == ',' and depth == 0:
                raw_types.append(cur.strip())
                cur = ""
            else:
                cur += c
        if cur.strip():
            raw_types.append(cur.strip())

        t1_raw = eval_simple_c_expr(raw_types[0]) if len(raw_types) > 0 else "TYPE_NORMAL"
        t2_raw = eval_simple_c_expr(raw_types[1]) if len(raw_types) > 1 else None

        if t1_raw == "TYPE_MYSTERY" or t2_raw == "TYPE_MYSTERY":
            raise ValueError(f"Species {species_id} uses audited TYPE_MYSTERY; refusing to coerce to Normal")

        if t1_raw not in TYPE_MAP:
            raise ValueError(f"Unknown type '{t1_raw}' for species {species_id}")
        t1_kt = TYPE_MAP[t1_raw]

        t2_kt = None
        if t2_raw and t2_raw != t1_raw and t2_raw != "TYPE_NONE":
            if t2_raw not in TYPE_MAP:
                raise ValueError(f"Unknown type '{t2_raw}' for species {species_id}")
            t2_kt = TYPE_MAP[t2_raw]

        def get_stat(field_name):
            sm = re.search(r'\.' + field_name + r'\s*=\s*([^,\n]+)', body)
            if not sm:
                raise ValueError(f"Missing {field_name} for species {species_id}")
            val = eval_simple_c_expr(sm.group(1).strip("() \t"))
            return int(val)

        hp = get_stat("baseHP")
        atk = get_stat("baseAttack")
        defn = get_stat("baseDefense")
        spe = get_stat("baseSpeed")
        spa = get_stat("baseSpAttack")
        spd = get_stat("baseSpDefense")

        formatted_name = format_species_name(raw_name)

        species_dict[species_id] = {
            "id": species_id,
            "name": formatted_name,
            "type1": t1_kt,
            "type2": t2_kt,
            "hp": hp,
            "atk": atk,
            "def": defn,
            "spa": spa,
            "spd": spd,
            "spe": spe,
        }

    return species_dict


def extract_moves(cpp_bin, upstream_dir):
    # Parse move enum constants from include/constants/moves.h
    enum_out = run_cpp(cpp_bin, upstream_dir, "include/constants/moves.h")
    move_ids = {}
    cur_id = 0
    for line in enum_out.split("\n"):
        line = line.strip()
        m = re.match(r"^(MOVE_[A-Za-z0-9_]+)\s*(?:=\s*([0-9]+|MOVE_[A-Za-z0-9_]+))?,?", line)
        if m:
            cname = m.group(1)
            val = m.group(2)
            if val is not None:
                if val.isdigit():
                    cur_id = int(val)
                elif val in move_ids:
                    cur_id = move_ids[val]
            move_ids[cname] = cur_id
            cur_id += 1

    # Parse move table from src/move.c
    moves_src_out = run_cpp(cpp_bin, upstream_dir, "src/move.c")
    start = moves_src_out.find("gMovesInfo[MOVES_COUNT_ALL] =")
    if start == -1:
        start = moves_src_out.find("gMovesInfo")
    if start == -1:
        raise ValueError("Could not find gMovesInfo in preprocessed src/move.c")

    entries = re.findall(
        r"\[\s*(MOVE_[A-Za-z0-9_]+|\d+)\s*\]\s*=\s*\{([^}]+)\}",
        moves_src_out[start:]
    )

    moves_dict = {}

    for mconst, body in entries:
        if mconst.isdigit():
            mid = int(mconst)
        else:
            mid = move_ids.get(mconst)

        if mid is None or mid == 0:  # MOVE_NONE
            continue

        name_m = re.search(r'\.name\s*=\s*(?:\(const u8\[\]\)\s*)?_\(\"([^\"]+)\"\)', body)
        if not name_m:
            continue
        raw_name = name_m.group(1).strip()
        if raw_name in ["-", "???"] or not raw_name:
            continue

        def get_field(fname):
            m = re.search(r'\.' + fname + r'\s*=\s*([^,\n]+)', body)
            return eval_simple_c_expr(m.group(1)) if m else None

        power = int(get_field("power") or 0)
        acc = int(get_field("accuracy") or 0)
        pp = int(get_field("pp") or 0)
        type_raw = get_field("type") or "TYPE_NORMAL"
        cat_raw = get_field("category") or "DAMAGE_CATEGORY_PHYSICAL"

        # Explicit Stellar requirement for Tera Starstorm
        if mconst == "MOVE_TERA_STARSTORM" or raw_name.upper() == "TERA STARSTORM":
            type_raw = "TYPE_STELLAR"

        if type_raw == "TYPE_MYSTERY":
            raise ValueError(f"Move {mid} ({raw_name}) uses audited TYPE_MYSTERY; refusing to coerce to Normal")

        if type_raw not in TYPE_MAP:
            raise ValueError(f"Unknown move type '{type_raw}' for move {mid} ({raw_name})")
        mtype_kt = TYPE_MAP[type_raw]

        if cat_raw not in CATEGORY_MAP:
            raise ValueError(f"Unknown move category '{cat_raw}' for move {mid} ({raw_name})")
        mcat_kt = CATEGORY_MAP[cat_raw]

        formatted_name = format_move_name(raw_name)

        moves_dict[mid] = {
            "id": mid,
            "name": formatted_name,
            "type": mtype_kt,
            "category": mcat_kt,
            "power": power,
            "acc": acc,
            "pp": pp,
        }

    return moves_dict


def validate_extracted_data(species_dict, moves_dict):
    # Required canonical IDs
    if 500 not in species_dict or species_dict[500]["name"] != "Emboar":
        raise AssertionError(f"ID 500 expected Emboar, got {species_dict.get(500)}")
    if 501 not in species_dict or species_dict[501]["name"] != "Oshawott":
        raise AssertionError(f"ID 501 expected Oshawott, got {species_dict.get(501)}")
    if 502 not in species_dict or species_dict[502]["name"] != "Dewott":
        raise AssertionError(f"ID 502 expected Dewott, got {species_dict.get(502)}")

    # Tera Starstorm validation
    tera_starstorm = None
    for m in moves_dict.values():
        if m["name"] == "Tera Starstorm":
            tera_starstorm = m
            break
    if not tera_starstorm:
        raise AssertionError("Tera Starstorm not found in moves")
    if tera_starstorm["type"] != "PokemonType.STELLAR":
        raise AssertionError(f"Tera Starstorm type must be PokemonType.STELLAR, got {tera_starstorm['type']}")
    if tera_starstorm["category"] != "MoveCategory.SPECIAL":
        raise AssertionError(f"Tera Starstorm category must be MoveCategory.SPECIAL, got {tera_starstorm['category']}")
    if tera_starstorm["power"] != 120:
        raise AssertionError(f"Tera Starstorm power must be 120, got {tera_starstorm['power']}")

    # Sanity checks
    if len(species_dict) < 1400:
        raise AssertionError(f"Expected at least 1400 species, got {len(species_dict)}")
    if len(moves_dict) < 900:
        raise AssertionError(f"Expected at least 900 moves, got {len(moves_dict)}")

    for sid, s in species_dict.items():
        assert sid > 0, f"Invalid species ID {sid}"
        assert s["name"], f"Empty name for species {sid}"
        assert s["hp"] >= 0 and s["atk"] >= 0, f"Invalid stats for species {sid}"

    for mid, m in moves_dict.items():
        assert mid > 0, f"Invalid move ID {mid}"
        assert m["name"], f"Empty name for move {mid}"
        assert m["power"] >= 0 and m["pp"] >= 0, f"Invalid stats for move {mid}"


def generate_kotlin_source(species_dict, moves_dict):
    lines = []
    lines.append("package com.dualdex.pokemon.hns")
    lines.append("")
    lines.append("import com.dualdex.pokemon.GameDataPack")
    lines.append("import com.dualdex.pokemon.MoveCategory")
    lines.append("import com.dualdex.pokemon.MoveInfo")
    lines.append("import com.dualdex.pokemon.PokemonType")
    lines.append("import com.dualdex.pokemon.SpeciesInfo")
    lines.append("import com.dualdex.pokemon.TypeChart")
    lines.append("")
    lines.append("/**")
    lines.append(" * Exact, version-pinned GameDataPack for Pokemon Heart & Soul 2.0.5.")
    lines.append(" *")
    lines.append(" * Provenance:")
    lines.append(" *   Repository: PokemonHnS-Development/pokehns-expansion")
    lines.append(" *   Tag: Release-v2.0.5")
    lines.append(f" *   Commit: {PINNED_COMMIT_SHA}")
    lines.append(" *   Extraction: Source / Preprocessor (arm-none-eabi-cpp) against build configuration")
    lines.append(" *   ROM dependency: NONE (Zero commercial ROM or byte-scanning dependency)")
    lines.append(" *")
    lines.append(f" * Total Species: {len(species_dict)}")
    lines.append(f" * Total Moves: {len(moves_dict)}")
    lines.append(" *")
    lines.append(" * DO NOT EDIT DIRECTLY. Regenerate using:")
    lines.append(" *   python3 tools/hns-data-pack/generate_hns_data_pack.py")
    lines.append(" */")
    lines.append("object HeartAndSoul205DataPack : GameDataPack {")
    lines.append('    override val id: String = "hns_2_0_5"')
    lines.append("    override val generation: Int = 8")
    lines.append("    override val hasFairyType: Boolean = true")
    lines.append("    override val hasPhysicalSpecialSplit: Boolean = true")
    lines.append("    override val allowGlobalFallback: Boolean = false")
    lines.append("")
    lines.append("    private val speciesMap = HashMap<Int, SpeciesInfo>()")
    lines.append("    private val movesMap = HashMap<Int, MoveInfo>()")
    lines.append("")
    lines.append("    init {")

    # Chunk registration to stay well under JVM 64KB method bytecode limits
    species_sorted = sorted(species_dict.items())
    species_chunk_size = 200
    species_chunks = [species_sorted[i:i + species_chunk_size] for i in range(0, len(species_sorted), species_chunk_size)]

    moves_sorted = sorted(moves_dict.items())
    moves_chunk_size = 200
    moves_chunks = [moves_sorted[i:i + moves_chunk_size] for i in range(0, len(moves_sorted), moves_chunk_size)]

    for i in range(len(species_chunks)):
        lines.append(f"        registerSpeciesChunk{i + 1}()")
    for i in range(len(moves_chunks)):
        lines.append(f"        registerMoveChunk{i + 1}()")

    lines.append("    }")
    lines.append("")
    lines.append("    override fun getSpecies(id: Int): SpeciesInfo? = speciesMap[id]")
    lines.append("    override fun getMove(id: Int): MoveInfo? = movesMap[id]")
    lines.append("    override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double {")
    lines.append("        return TypeChart.getEffectiveness(attackType, defType, steelResistsGhostDark = false).toDouble()")
    lines.append("    }")
    lines.append("")
    lines.append("    override fun isSpeciesAuthoritative(id: Int): Boolean = speciesMap.containsKey(id)")
    lines.append("    override fun isMoveAuthoritative(id: Int): Boolean = movesMap.containsKey(id)")
    lines.append("")
    lines.append("    private fun registerSpecies(id: Int, name: String, t1: PokemonType, t2: PokemonType?,")
    lines.append("                                hp: Int, atk: Int, def: Int, spa: Int, spd: Int, spe: Int) {")
    lines.append("        speciesMap[id] = SpeciesInfo(id, name, t1, t2, hp, atk, def, spa, spd, spe)")
    lines.append("    }")
    lines.append("")
    lines.append("    private fun registerMove(id: Int, name: String, type: PokemonType, category: MoveCategory, power: Int, acc: Int, pp: Int) {")
    lines.append("        movesMap[id] = MoveInfo(id, name, type, category, power, acc, pp)")
    lines.append("    }")
    lines.append("")

    for i, chunk in enumerate(species_chunks):
        lines.append(f"    private fun registerSpeciesChunk{i + 1}() {{")
        for sid, s in chunk:
            t2_str = s["type2"] if s["type2"] else "null"
            name_escaped = s["name"].replace('"', '\\"')
            lines.append(
                f'        registerSpecies({sid}, "{name_escaped}", {s["type1"]}, {t2_str}, '
                f'{s["hp"]}, {s["atk"]}, {s["def"]}, {s["spa"]}, {s["spd"]}, {s["spe"]})'
            )
        lines.append("    }")
        lines.append("")

    for i, chunk in enumerate(moves_chunks):
        lines.append(f"    private fun registerMoveChunk{i + 1}() {{")
        for mid, m in chunk:
            name_escaped = m["name"].replace('"', '\\"')
            lines.append(
                f'        registerMove({mid}, "{name_escaped}", {m["type"]}, {m["category"]}, '
                f'{m["power"]}, {m["acc"]}, {m["pp"]})'
            )
        lines.append("    }")
        lines.append("")

    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Authoritative H&S 2.0.5 Game Data Pack Generator")
    parser.add_argument("--upstream-dir", help="Path to pokehns-expansion git checkout")
    parser.add_argument("--cpp-bin", help="Path to arm-none-eabi-cpp preprocessor binary")
    parser.add_argument("--out-file", default=DEFAULT_TARGET_FILE, help="Path to output Kotlin source file")
    parser.add_argument("--verify", action="store_true", help="Verify that generated output matches target without drift")

    args = parser.parse_args()

    upstream_dir = find_upstream_dir(args.upstream_dir)
    cpp_bin = find_cpp_bin(args.cpp_bin)

    print(f"Upstream repository: {upstream_dir}")
    print(f"Preprocessor:        {cpp_bin}")
    print(f"Target file:         {args.out_file}")

    print("Verifying upstream Git commit...")
    verify_git_commit(upstream_dir)
    print(f"Verified pinned commit {PINNED_COMMIT_SHA} (Release-v2.0.5).")

    print("Extracting species data via preprocessor...")
    species_dict = extract_species(cpp_bin, upstream_dir)
    print(f"Extracted {len(species_dict)} authoritative species.")

    print("Extracting moves data via preprocessor...")
    moves_dict = extract_moves(cpp_bin, upstream_dir)
    print(f"Extracted {len(moves_dict)} authoritative moves.")

    print("Validating data integrity...")
    validate_extracted_data(species_dict, moves_dict)
    print("Validation passed.")

    kotlin_code = generate_kotlin_source(species_dict, moves_dict)

    if args.verify:
        print(f"Verifying determinism against {args.out_file}...")
        if not os.path.exists(args.out_file):
            print(f"Verification FAILED: target file does not exist: {args.out_file}", file=sys.stderr)
            sys.exit(1)
        with open(args.out_file, "r", encoding="utf-8") as f:
            existing_code = f.read()
        if existing_code == kotlin_code:
            print("Verification PASSED: generated output perfectly matches disk. Zero drift.")
            sys.exit(0)
        else:
            print("Verification FAILED: generated output differs from disk.", file=sys.stderr)
            exist_lines = existing_code.splitlines()
            gen_lines = kotlin_code.splitlines()
            for idx, (el, gl) in enumerate(zip(exist_lines, gen_lines)):
                if el != gl:
                    print(f"Difference at line {idx + 1}:\n  disk: {el}\n  gen:  {gl}", file=sys.stderr)
                    break
            if len(exist_lines) != len(gen_lines):
                print(f"Line count difference: disk {len(exist_lines)} vs gen {len(gen_lines)}", file=sys.stderr)
            sys.exit(1)

    os.makedirs(os.path.dirname(args.out_file), exist_ok=True)
    with open(args.out_file, "w", encoding="utf-8") as f:
        f.write(kotlin_code)
    print(f"Successfully generated {args.out_file}.")


if __name__ == "__main__":
    main()
