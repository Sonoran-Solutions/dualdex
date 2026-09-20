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
]

# Search paths for arm-none-eabi-cpp or toolchain
DEFAULT_CPP_SEARCH_PATHS = [
    os.environ.get("ARM_CPP"),
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

    status = subprocess.run(
        ["git", "-C", upstream_dir, "status", "--porcelain", "--untracked-files=no"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
        text=True,
    )
    if status.stdout.strip():
        raise ValueError(
            "FATAL: Upstream Git checkout has tracked working-tree changes. "
            "Refusing to generate from a dirty source checkout."
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


def require_field(body, field_name, move_id, move_constant, move_name):
    """Extract a required move initializer field without conflating absence with zero."""
    match = re.search(r'\.' + re.escape(field_name) + r'\s*=\s*([^,\n]+)', body)
    if not match:
        raise ValueError(
            f"Missing required field '.{field_name}' for move {move_id} "
            f"({move_constant}, {move_name})"
        )
    expression = match.group(1).strip()
    if not expression:
        raise ValueError(
            f"Empty required field '.{field_name}' for move {move_id} "
            f"({move_constant}, {move_name})"
        )
    return eval_simple_c_expr(expression)


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


def extract_designated_entries(output, table_start, key_pattern, table_name):
    """Extract only direct children of a designated C initializer table."""
    table_open = output.find("{", table_start)
    if table_open == -1:
        raise ValueError(f"Could not find opening brace for {table_name}")

    entries = []
    pos = table_open + 1
    depth = 1
    entry_pattern = re.compile(r"\[\s*(" + key_pattern + r")\s*\]\s*=\s*\{")

    while pos < len(output) and depth > 0:
        if depth == 1:
            match = entry_pattern.match(output, pos)
            if match:
                key = match.group(1)
                brace_start = match.end() - 1
                body_depth = 1
                body_pos = brace_start + 1
                while body_pos < len(output) and body_depth > 0:
                    if output[body_pos] == "{":
                        body_depth += 1
                    elif output[body_pos] == "}":
                        body_depth -= 1
                    body_pos += 1
                if body_depth != 0:
                    raise ValueError(f"Unclosed initializer for {table_name} entry {key}")
                entries.append((key, output[brace_start + 1:body_pos - 1]))
                pos = body_pos
                continue

        if output[pos] == "{":
            depth += 1
        elif output[pos] == "}":
            depth -= 1
        pos += 1

    return entries


def extract_species(cpp_bin, upstream_dir):
    output = run_cpp(cpp_bin, upstream_dir, "src/pokemon.c")
    start = output.find("gSpeciesInfo[] =")
    if start == -1:
        start = output.find("gSpeciesInfo")
    if start == -1:
        raise ValueError("Could not find gSpeciesInfo in preprocessed src/pokemon.c")

    species_dict = {}
    seen_species_ids = set()

    entries = extract_designated_entries(output, start, r"\d+", "gSpeciesInfo")
    for species_id_raw, body in entries:
        species_id = int(species_id_raw)
        if species_id in seen_species_ids:
            raise ValueError(f"Duplicate species ID {species_id} in preprocessed gSpeciesInfo")
        seen_species_ids.add(species_id)

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

    entries = extract_designated_entries(
        moves_src_out,
        start,
        r"MOVE_[A-Za-z0-9_]+|\d+",
        "gMovesInfo",
    )

    moves_dict = {}
    seen_move_ids = set()

    for mconst, body in entries:
        if mconst.isdigit():
            mid = int(mconst)
        else:
            mid = move_ids.get(mconst)

        if mid is None or mid == 0:  # MOVE_NONE
            continue

        if mid in seen_move_ids:
            raise ValueError(
                f"Duplicate move ID {mid} in preprocessed gMovesInfo "
                f"({mconst})"
            )
        seen_move_ids.add(mid)

        name_m = re.search(r'\.name\s*=\s*(?:\(const u8\[\]\)\s*)?_\(\"([^\"]+)\"\)', body)
        if not name_m:
            continue
        raw_name = name_m.group(1).strip()
        if raw_name in ["-", "???"] or not raw_name:
            continue

        power = int(require_field(body, "power", mid, mconst, raw_name))
        acc = int(require_field(body, "accuracy", mid, mconst, raw_name))
        pp = int(require_field(body, "pp", mid, mconst, raw_name))
        type_raw = require_field(body, "type", mid, mconst, raw_name)
        cat_raw = require_field(body, "category", mid, mconst, raw_name)

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


# ----------------------------- Abilities -----------------------------
#
# The H&S ability catalogue and per-species ability-slot declarations. Extracted
# from the same pinned checkout as the species/move tables; IDs are the build's
# own `enum Ability` values, never inferred from ordering or external databases.

# Ability enum constants whose meaning is fixed by the build's own headers.
ABILITY_SENTINELS = {"ABILITY_NONE"}

# The build's generation-boundary bookkeeping members. They are ENUM MEMBERS, not
# preprocessor macros (the header declares e.g. `ABILITIES_COUNT_GEN3,` and
# `ABILITY_TANGLED_FEET = ABILITIES_COUNT_GEN3,`), so they survive preprocessing
# as symbolic names and must be resolved like any other member. They are then
# excluded from the identity catalogue, which holds only real ability constants.
ABILITY_COUNT_ANCHORS = (
    "ABILITIES_COUNT_GEN3",
    "ABILITIES_COUNT_GEN4",
    "ABILITIES_COUNT_GEN5",
    "ABILITIES_COUNT_GEN6",
    "ABILITIES_COUNT_GEN7",
    "ABILITIES_COUNT_GEN8",
    "ABILITIES_COUNT_GEN9",
    "ABILITIES_COUNT",
)

# Names the enum parser accepts as members: real ability constants plus the
# count anchors above.
ABILITY_ENUM_MEMBER_NAME_RE = re.compile(
    r"^(ABILITY_[A-Za-z0-9_]+|ABILITIES_COUNT(_GEN[3-9])?)$"
)


def parse_ability_enum(enum_out):
    """Resolve a preprocessed `enum Ability` to a {name: numeric ID} map.

    Exactly three declaration forms are understood - the forms the pinned header
    actually uses:
      - `NAME = <integer>` : explicit value (post-Gen-3 abilities, explicit anchors);
      - `NAME`            : implicit value, previous member's value + 1 (Gen-3 names
                            and the ABILITIES_COUNT_GEN* anchors);
      - `NAME = <symbol>` : alias of an already-defined member, which the header
                            uses for the first ability after each count anchor
                            (e.g. `ABILITY_TANGLED_FEET = ABILITIES_COUNT_GEN3`).

    Anything else is an unsupported assignment and raises: an undefined symbol
    (e.g. `ABILITY_STENCH = UNRESOLVED_ALIAS`), a parenthesized initializer
    (`ABILITY_SPEED_BOOST = (1)`), arithmetic, or a macro that survived
    preprocessing. An unsupported assignment must NEVER fall back to the running
    counter: that would invent a sequential ID and silently corrupt the name->ID
    mapping in a way contiguity checks cannot detect.
    """
    member_ids = {}
    next_implicit = None
    in_enum = False
    for line in enum_out.split("\n"):
        stripped = line.strip()
        if stripped.startswith("enum ") and "Ability" in stripped:
            in_enum = True
            continue
        if not in_enum:
            continue
        if stripped.startswith("}"):
            in_enum = False
            continue
        if not stripped or stripped.startswith("#") or stripped == "{":
            # Preprocessor line markers, the enum's opening brace, and blank lines.
            continue

        decl = stripped[:-1].strip() if stripped.endswith(",") else stripped
        name, eq, raw_value = decl.partition("=")
        name = name.strip()
        if not ABILITY_ENUM_MEMBER_NAME_RE.match(name):
            raise ValueError(f"Unsupported ability enum declaration: {stripped!r}")
        value = raw_value.strip() if eq else None

        if value is None:
            # No assignment at all: the implicit previous+1 rule. This is only
            # valid C when a previous member exists.
            if next_implicit is None:
                raise ValueError(
                    f"Ability enum member {name} has an implicit value but no previous "
                    "explicit value to continue from"
                )
            resolved = next_implicit
        elif re.fullmatch(r"[0-9]+", value):
            resolved = int(value)
        elif re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value):
            # Assignment present and symbolic: only an alias of an already-defined
            # member resolves. An undefined symbol is a hard error, never a
            # sequential ID.
            if value not in member_ids:
                raise ValueError(
                    f"Unresolvable ability enum assignment {name} = {value}; refusing "
                    "to invent a sequential ID"
                )
            resolved = member_ids[value]
        else:
            raise ValueError(
                f"Unsupported ability enum assignment {name} = {value!r}; refusing to "
                "invent a sequential ID"
            )

        if name in member_ids and member_ids[name] != resolved:
            raise ValueError(f"Conflicting enum values for {name}")
        member_ids[name] = resolved
        next_implicit = resolved + 1
    return member_ids


def extract_abilities(cpp_bin, upstream_dir):
    """Extract the numeric ID and display name of every ability in the pinned build.

    Sources, both preprocessed with the build's own configuration:
      - include/constants/abilities.h : `enum Ability` gives the numeric ID for every
        ABILITY_* constant. Assignments the parser cannot resolve (unknown symbols,
        parenthesized initializers, arithmetic) are hard errors - a value is never
        invented from the running counter. The ABILITIES_COUNT_GEN* anchors are
        enum members, not macros, and are resolved explicitly so the symbolic
        references that follow them (e.g. ABILITY_TANGLED_FEET = ABILITIES_COUNT_GEN3)
        resolve to the anchor's real value instead of a coincidental counter value.
      - src/data/abilities.h          : `gAbilitiesInfo[ABILITIES_COUNT]` gives the
        display name for each designated `[ABILITY_X]` entry.
    Every non-sentinel constant must have a table entry and vice versa; anything else
    is data drift and a hard failure.
    """
    # 1. Numeric IDs from the preprocessed enum.
    enum_out = run_cpp(cpp_bin, upstream_dir, "include/constants/abilities.h")
    enum_ids = parse_ability_enum(enum_out)

    if enum_ids.get("ABILITY_NONE") != 0:
        raise ValueError(f"ABILITY_NONE must be 0, got {enum_ids.get('ABILITY_NONE')}")

    # The count anchors are bookkeeping members, not catalogue identities. Capture
    # the total before dropping them so it can be checked against the real data
    # below instead of being parsed and discarded.
    abilities_count = enum_ids.pop("ABILITIES_COUNT", None)
    for anchor in ABILITY_COUNT_ANCHORS[:-1]:
        enum_ids.pop(anchor, None)

    # 2. Display names from the ability info table.
    table_out = run_cpp(cpp_bin, upstream_dir, "src/data/abilities.h")
    start = table_out.find("gAbilitiesInfo[ABILITIES_COUNT]")
    if start == -1:
        start = table_out.find("gAbilitiesInfo")
    if start == -1:
        raise ValueError("Could not find gAbilitiesInfo in preprocessed src/data/abilities.h")

    table_ids = {}
    entries = extract_designated_entries(table_out, start, r"ABILITY_[A-Za-z0-9_]+", "gAbilitiesInfo")
    for aconst, body in entries:
        if aconst in table_ids:
            raise ValueError(f"Duplicate gAbilitiesInfo entry for {aconst}")
        name_m = re.search(r'\.name\s*=\s*_\("([^"]+)"\)', body)
        if not name_m:
            raise ValueError(f"Missing .name for gAbilitiesInfo entry {aconst}")
        raw_name = name_m.group(1).strip()
        if aconst == "ABILITY_NONE":
            if raw_name != "-------":
                raise ValueError(f"ABILITY_NONE display name drifted: {raw_name}")
            continue  # sentinel: kept in slot tables, not in the identity catalogue
        table_ids[aconst] = raw_name

    # 3. Cross-check: every real constant must appear exactly once in the table.
    # Note: display names are stored verbatim from the build's table (e.g. "DRIZZLE",
    # "AS ONE"); no case or wording transformation is applied, and names are not
    # unique across abilities (AS ONE covers both As One Ice/Shadow Rider).
    real_constants = {c for c in enum_ids if c not in ABILITY_SENTINELS}
    missing = sorted(real_constants - set(table_ids))
    extra = sorted(set(table_ids) - real_constants)
    if missing:
        raise ValueError(f"Abilities in enum but not in gAbilitiesInfo: {missing}")
    if extra:
        raise ValueError(f"Abilities in gAbilitiesInfo but not in enum: {extra}")

    abilities = {}
    for aconst, raw_name in table_ids.items():
        aid = enum_ids[aconst]
        if aid in abilities:
            raise ValueError(f"Duplicate ability ID {aid} ({aconst})")
        abilities[aid] = {
            "id": aid,
            "name": raw_name,
            "constant": aconst,
        }

    # The count anchor must agree with the extracted data rather than being parsed
    # and discarded: the declared ABILITIES_COUNT is the value just past the highest
    # real ability ID in the pinned header.
    if abilities_count is not None and abilities and abilities_count != max(abilities) + 1:
        raise ValueError(
            f"ABILITIES_COUNT anchor is {abilities_count}, but the highest extracted "
            f"ability ID is {max(abilities)}; the enum no longer matches its own count"
        )
    return abilities


def extract_species_abilities(cpp_bin, upstream_dir):
    """Extract the ordered per-species/form ability slots from the pinned build.

    Reads `gSpeciesInfo` from preprocessed src/pokemon.c (the same table the species
    extractor uses). Each entry's `.abilities` initializer is a fixed 3-slot array
    (NUM_NORMAL_ABILITY_SLOTS 2 + NUM_HIDDEN_ABILITY_SLOTS 1, defined in
    include/constants/pokemon.h). All three slots are preserved in position, including
    explicit ABILITY_NONE sentinels, so slot numbers never shift.

    Species whose declared slots are not source-established (the nameless "??????????"
    placeholder species 0-form entries) are recorded as an explicit absence rather than
    guessed. Macros like MEOWTH_ABILITIES are already expanded by the preprocessor.
    """
    abilities = extract_abilities(cpp_bin, upstream_dir)
    constant_to_id = {a["constant"]: a["id"] for a in abilities.values()}

    output = run_cpp(cpp_bin, upstream_dir, "src/pokemon.c")
    start = output.find("gSpeciesInfo[] =")
    if start == -1:
        start = output.find("gSpeciesInfo")
    if start == -1:
        raise ValueError("Could not find gSpeciesInfo in preprocessed src/pokemon.c")

    # Preprocessed .abilities initializers are flat `{ A, B, C }` lists; this stricter
    # pattern refuses to match anything unexpected (e.g. a macro that survived expansion).
    # A few species (the four Ogerpon base forms) declare fewer than all three slots;
    # C zero-fills the remaining designated-initializer members, i.e. they hold
    # ABILITY_NONE. Those are padded with the sentinel so slot positions always align
    # with NUM_ABILITY_SLOTS (include/constants/pokemon.h:393).
    abilities_re = re.compile(r"\.abilities\s*=\s*\{([^{}]*)\}")
    split_re = re.compile(r"\s*,\s*")
    const_re = re.compile(r"^(ABILITY_[A-Za-z0-9_]+)$")

    species_abilities = {}
    entries = extract_designated_entries(output, start, r"\d+", "gSpeciesInfo")
    for species_id_raw, body in entries:
        species_id = int(species_id_raw)
        if species_id in species_abilities:
            raise ValueError(f"Duplicate species ID {species_id} in preprocessed gSpeciesInfo")

        name_m = re.search(r'\.speciesName\s*=\s*_\("([^"]+)"\)', body)
        if not name_m:
            raise ValueError(f"Missing .speciesName for species {species_id} in ability extraction")
        raw_name = name_m.group(1).strip()
        if raw_name in ["??????????", "Egg", "EGG"] or not raw_name:
            species_abilities[species_id] = None  # explicit absence
            continue

        am = abilities_re.search(body)
        if not am:
            raise ValueError(f"Missing .abilities initializer for species {species_id} ({raw_name})")
        slot_values = [v.strip() for v in split_re.split(am.group(1).strip()) if v.strip()]
        if not 0 < len(slot_values) <= 3:
            raise ValueError(
                f"Species {species_id} ({raw_name}) declared {len(slot_values)} ability slots, "
                "expected 1-3"
            )
        # C zero-fills designated-initializer array members beyond the written ones:
        # a short initializer means the unwritten slots hold ABILITY_NONE.
        slot_values += ["ABILITY_NONE"] * (3 - len(slot_values))
        slots = []
        for slot, v in enumerate(slot_values):
            cm = const_re.match(v)
            if not cm:
                raise ValueError(
                    f"Unresolved expression '{v}' in species {species_id} ({raw_name}) slot {slot}; "
                    "refusing to coerce to an ability ID"
                )
            c = cm.group(1)
            if c in ABILITY_SENTINELS:
                slots.append(None)  # explicit empty/sentinel slot, position preserved
                continue
            aid = constant_to_id.get(c)
            if aid is None:
                raise ValueError(f"Ability constant {c} in species {species_id} has no catalogue ID")
            slots.append(aid)
        species_abilities[species_id] = slots

    return abilities, species_abilities


def validate_extracted_data(species_dict, moves_dict, abilities_dict, species_abilities):
    # Required canonical IDs
    if 500 not in species_dict or species_dict[500]["name"] != "Emboar":
        raise AssertionError(f"ID 500 expected Emboar, got {species_dict.get(500)}")
    if 501 not in species_dict or species_dict[501]["name"] != "Oshawott":
        raise AssertionError(f"ID 501 expected Oshawott, got {species_dict.get(501)}")
    if 502 not in species_dict or species_dict[502]["name"] != "Dewott":
        raise AssertionError(f"ID 502 expected Dewott, got {species_dict.get(502)}")

    # Tera Starstorm must match the static source table. Runtime Tera Starstorm
    # semantics are dynamic and intentionally out of scope for this data pack.
    tera_starstorm = None
    for m in moves_dict.values():
        if m["name"] == "Tera Starstorm":
            tera_starstorm = m
            break
    if not tera_starstorm:
        raise AssertionError("Tera Starstorm not found in moves")
    if tera_starstorm["type"] != "PokemonType.NORMAL":
        raise AssertionError(f"Tera Starstorm type must be PokemonType.NORMAL, got {tera_starstorm['type']}")
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

    # Independent ability-catalogue expectations, pinned by hand directly from
    # src/data/abilities.h and include/constants/abilities.h of the pinned checkout
    # (not derived from this generator's own output). Display names are stored
    # verbatim as the build writes them (uppercase in the source table).
    expected_abilities = {
        2: "DRIZZLE",         # ABILITY_DRIZZLE
        7: "LIMBER",          # ABILITY_LIMBER
        26: "LEVITATE",       # ABILITY_LEVITATE
        65: "OVERGROW",       # ABILITY_OVERGROW
        88: "DOWNLOAD",       # ABILITY_DOWNLOAD (post-Gen-3 anchor range)
        112: "SLOW START",    # ABILITY_SLOW_START
        130: "CURSED BODY",   # ABILITY_CURSED_BODY
        185: "PARENTAL BOND",
        224: "BEAST BOOST",   # ABILITY_BEAST_BOOST
        248: "ICE FACE",      # ABILITY_ICE_FACE (Gen 8 range)
        266: "AS ONE",        # ABILITY_AS_ONE_ICE_RIDER (same display name as Shadow Rider)
        310: "POISON PUPPETEER",  # highest enum value
    }
    for aid, expected_name in expected_abilities.items():
        actual = abilities_dict.get(aid)
        assert actual is not None, f"Expected ability ID {aid} ({expected_name}) missing from catalogue"
        assert actual["name"] == expected_name, (
            f"Ability ID {aid}: expected '{expected_name}', got '{actual['name']}'"
        )
    assert 0 not in abilities_dict, "ABILITY_NONE sentinel must not appear in the identity catalogue"
    ids_sorted = sorted(abilities_dict)
    assert ids_sorted == list(range(1, len(abilities_dict) + 1)), (
        "Ability catalogue must be exactly the contiguous non-zero enum range"
    )

    # Independent per-species slot expectations, pinned by hand from the source.
    # Species IDs are the build's national-dex-numbered SPECIES_* IDs (e.g. species
    # 500 is Emboar, species 1 is Bulbasaur):
    # - Bulbasaur (1):   { OVERGROW, NONE, CHLOROPHYLL } -> one empty middle slot
    # - Meowth (52):     { PICKUP, TECHNICIAN, UNNERVE }
    # - Gengar (94):     { LEVITATE, CURSED_BODY, NONE }
    # - Abomasnow (460): { SNOW_WARNING, NONE, SOUNDPROOF }
    # - Ogerpon Teal (1416): { DEFIANT, NONE } with C zero-fill -> { DEFIANT, NONE, NONE }
    # (ability IDs per include/constants/abilities.h of the pinned checkout)
    expected_slots = {
        1: [65, None, 34],
        52: [53, 101, 127],
        94: [26, 130, None],
        460: [117, None, 43],
        1416: [128, None, None],
    }
    for sid, slots in expected_slots.items():
        assert sid in species_dict, f"Expected species {sid} missing"
        actual_slots = species_abilities.get(sid)
        assert actual_slots == slots, f"Species {sid} slots: expected {slots}, got {actual_slots}"

    declared = {sid: s for sid, s in species_abilities.items() if s is not None}
    assert len(declared) >= 1400, f"Expected at least 1400 declared species, got {len(declared)}"
    assert len(species_abilities) >= len(species_dict), (
        "Slot table must cover every species entry the pack exposes"
    )
    for sid, s in species_dict.items():
        assert sid in species_abilities, f"Species {sid} missing from ability-slot table"
    for sid, slots in declared.items():
        assert len(slots) == 3, f"Species {sid} must have 3 preserved slots"
        for slot, aid in enumerate(slots):
            assert aid is None or aid in abilities_dict, (
                f"Species {sid} slot {slot} references unknown ability ID {aid}"
            )
            if aid is not None:
                assert aid != 0, f"Species {sid} slot {slot} uses numeric 0 instead of a sentinel"


def generate_kotlin_source(species_dict, moves_dict, abilities_dict, species_abilities):
    lines = []
    lines.append("package com.dualdex.pokemon.hns")
    lines.append("")
    lines.append("import com.dualdex.pokemon.DeclaredAbility")
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
    lines.append(f" * Total Abilities: {len(abilities_dict)}")
    lines.append(" * Species with declared ability slots: "
                 f"{sum(1 for s in species_abilities.values() if s is not None)}")
    lines.append(" *")
    lines.append(" * Runtime-ability boundary (identity only, not effective battle state):")
    lines.append(" *   GetAbilityBySpecies [src/pokemon.c:5548] overrides declared slot 0 with")
    lines.append(" *   sLegendaryCustomAbilities [src/pokemon.c:5535] whenever the challenge")
    lines.append(" *   setting tx_Mode_Legendary_Abilities is ON (default ON, [src/new_game.c:147],")
    lines.append(" *   challenge menu item LEGEN. ABILITIES [src/challenge_menu.c:462], TAB_MODE is")
    lines.append(" *   always unlocked [src/challenge_menu.c:183]); tx_Random_Abilities rerolls")
    lines.append(" *   abilities entirely [src/pokemon.c:5585]. DualDex reads neither setting, so")
    lines.append(" *   these declarations are not evidence of a live Pokemon's ability.")
    lines.append(" *")
    lines.append(" * DO NOT EDIT DIRECTLY. Regenerate using:")
    lines.append(" *   python3 tools/hns-data-pack/generate_hns_data_pack.py")
    lines.append(" */")
    lines.append("object HeartAndSoul205DataPack : GameDataPack {")
    lines.append('    override val id: String = "hns_2_0_5"')
    lines.append("    override val generation: Int = 8")
    lines.append("    override val hasFairyType: Boolean = true")
    lines.append("    override val hasStellarType: Boolean = true")
    lines.append("    override val hasPhysicalSpecialSplit: Boolean = true")
    lines.append("    override val allowGlobalFallback: Boolean = false")
    lines.append("")
    lines.append("    private val speciesMap = HashMap<Int, SpeciesInfo>()")
    lines.append("    private val movesMap = HashMap<Int, MoveInfo>()")
    lines.append("")
    lines.append("    /** The build's own slot layout: 2 normal slots + 1 hidden slot. */")
    lines.append("    private const val ABILITY_SLOT_COUNT = 3")
    lines.append("")
    lines.append("    /** Ability ID -> display name, for the catalogue lookup. */")
    lines.append("    private val abilityNameMap = HashMap<Int, String>()")
    lines.append("")
    lines.append("    /** Species/form ID -> declared ability ID per slot (null = ABILITY_NONE sentinel). */")
    lines.append("    private val speciesAbilityMap = HashMap<Int, Array<Int?>>()")
    lines.append("")
    lines.append("    init {")

    # Chunk registration to stay well under JVM 64KB method bytecode limits
    species_sorted = sorted(species_dict.items())
    species_chunk_size = 200
    species_chunks = [species_sorted[i:i + species_chunk_size] for i in range(0, len(species_sorted), species_chunk_size)]

    moves_sorted = sorted(moves_dict.items())
    moves_chunk_size = 200
    moves_chunks = [moves_sorted[i:i + moves_chunk_size] for i in range(0, len(moves_sorted), moves_chunk_size)]

    abilities_sorted = sorted(abilities_dict.items())
    ability_chunk_size = 200
    ability_chunks = [abilities_sorted[i:i + ability_chunk_size] for i in range(0, len(abilities_sorted), ability_chunk_size)]

    species_abilities_sorted = sorted(
        ((sid, s) for sid, s in species_abilities.items() if s is not None),
        key=lambda pair: pair[0],
    )
    species_ability_chunk_size = 400
    species_ability_chunks = [species_abilities_sorted[i:i + species_ability_chunk_size] for i in range(0, len(species_abilities_sorted), species_ability_chunk_size)]

    for i in range(len(species_chunks)):
        lines.append(f"        registerSpeciesChunk{i + 1}()")
    for i in range(len(moves_chunks)):
        lines.append(f"        registerMoveChunk{i + 1}()")
    for i in range(len(ability_chunks)):
        lines.append(f"        registerAbilityChunk{i + 1}()")
    for i in range(len(species_ability_chunks)):
        lines.append(f"        registerSpeciesAbilityChunk{i + 1}()")

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
    lines.append("    /**")
    lines.append("     * The ability identity catalogue for this exact build.")
    lines.append("     *")
    lines.append("     * Maps this build's own numeric ability IDs (the `enum Ability` values of")
    lines.append("     * include/constants/abilities.h) to display names from the build's")
    lines.append("     * gAbilitiesInfo table. Slot declarations reference these IDs.")
    lines.append("     *")
    lines.append("     * This is identity data only: it proves that ability ID N exists in this build")
    lines.append("     * and what the build calls it. It does NOT model any ability's battle effect,")
    lines.append("     * and it is never evidence that the damage engine implements the ability.")
    lines.append("     */")
    lines.append("    fun getAbility(id: Int): DeclaredAbility =")
    lines.append("        if (id <= 0) DeclaredAbility.Absent else abilityNameMap[id]")
    lines.append("            ?.let { DeclaredAbility.Declared(id, it) } ?: DeclaredAbility.Absent")
    lines.append("")
    lines.append("    /**")
    lines.append("     * The ability the pinned build's static data declares for one ability slot of")
    lines.append("     * one exact species/form ID.")
    lines.append("     *")
    lines.append("     * Slots are positional: slot 0 and 1 are the normal slots, slot 2 is the hidden")
    lines.append("     * slot (NUM_ABILITY_SLOTS = 2 + 1, include/constants/pokemon.h:393). A declared")
    lines.append("     * ABILITY_NONE sentinel is reported as [DeclaredAbility.EmptySlot] and the slot")
    lines.append("     * position is always preserved, so a slot number never shifts.")
    lines.append("     *")
    lines.append("     * This is a declaration lookup, not a battle read: it is not proof of a live")
    lines.append("     * Pokemon's current ability, because the build's own challenge settings (see the")
    lines.append("     * file header) can change which ability actually applies. Unknown species, out of")
    lines.append("     * range slots, and packs without ability declarations return")
    lines.append("     * [DeclaredAbility.Absent] - never a substitute.")
    lines.append("     */")
    lines.append("    override fun getDeclaredAbilityForSlot(id: Int, slot: Int): DeclaredAbility {")
    lines.append("        if (slot < 0 || slot >= ABILITY_SLOT_COUNT) return DeclaredAbility.Absent")
    lines.append("        val slots = speciesAbilityMap[id] ?: return DeclaredAbility.Absent")
    lines.append("        return when (val abilityId = slots[slot]) {")
    lines.append("            null -> DeclaredAbility.EmptySlot")
    lines.append("            else -> abilityNameMap[abilityId]?.let { DeclaredAbility.Declared(abilityId, it) }")
    lines.append("                ?: DeclaredAbility.Absent")
    lines.append("        }")
    lines.append("    }")
    lines.append("")
    lines.append("    /**")
    lines.append("     * Authoritative name -> entry lookups for this exact build.")
    lines.append("     *")
    lines.append("     * The damage-calculator bridge selects content BY NAME, so a caller must be able to")
    lines.append("     * prove that a name belongs to this build before the request is sent. Resolving")
    lines.append("     * against a shared later-generation dex instead would silently compute a number from")
    lines.append("     * another game's base stats, typing or base power.")
    lines.append("     *")
    lines.append("     * Matching is case-insensitive and whitespace-trimmed. Generated from the pinned")
    lines.append("     * upstream source; returns null rather than a substitute entry.")
    lines.append("     */")
    lines.append("    override fun getSpeciesByName(name: String): SpeciesInfo? =")
    lines.append("        speciesByName(name.trim().lowercase())")
    lines.append("")
    lines.append("    override fun getMoveByName(name: String): MoveInfo? =")
    lines.append("        movesByName(name.trim().lowercase())")
    lines.append("")

    # Group by lowercase name. Species whose forms differ in type or base stats cannot be named by
    # a name-only lookup: the engine would silently use one form's numbers for the other. Those
    # names must resolve to null so the calculator boundary refuses the request instead of
    # publishing a confident number for the wrong form.
    def name_signature(s):
        return (s["type1"], s["type2"], s["hp"], s["atk"], s["def"], s["spa"], s["spd"], s["spe"])

    species_groups = {}
    for sid, s in species_dict.items():
        species_groups.setdefault(s["name"].lower(), []).append((sid, s))

    unambiguous_species = {}
    ambiguous_species = []
    for lower_name, entries in species_groups.items():
        entries.sort(key=lambda pair: pair[0])
        signatures = {name_signature(s) for _, s in entries}
        if len(signatures) == 1:
            # Identical forms (e.g. Unown letters, Alcremie decorations): the lowest id is the
            # base form and shares every damage-relevant value with the others.
            unambiguous_species[lower_name] = entries[0][0]
        else:
            ambiguous_species.append((lower_name, [sid for sid, _ in entries]))

    lines.append("    private fun speciesByName(key: String): SpeciesInfo? = when (key) {")
    for lower_name in sorted(unambiguous_species):
        key_escaped = lower_name.replace("\\", "\\\\").replace('"', '\\"')
        lines.append(f'        "{key_escaped}" -> speciesMap[{unambiguous_species[lower_name]}]')
    lines.append("        else -> null")
    lines.append("    }")
    lines.append("")
    lines.append("    /**")
    lines.append("     * Names that map to more than one form with DIFFERENT types or base stats.")
    lines.append("     *")
    lines.append("     * A name-only calculation request cannot say which form is meant, so these names are")
    lines.append("     * deliberately absent from [speciesByName] and resolve to null. That is what makes")
    lines.append("     * the calculator boundary refuse them instead of computing one form's damage for")
    lines.append("     * another form's species name.")
    lines.append("     */")
    lines.append("    val multiFormNames: Set<String> = setOf(")
    for lower_name, ids in sorted(ambiguous_species):
        key_escaped = lower_name.replace("\\", "\\\\").replace('"', '\\"')
        id_list = ", ".join(str(i) for i in ids)
        lines.append(f'        "{key_escaped}", // ids {id_list}')
    lines.append("    )")
    lines.append("")
    lines.append("    private fun movesByName(key: String): MoveInfo? = when (key) {")

    moves_by_name = sorted(
        ((m["name"].lower(), mid) for mid, m in moves_dict.items()),
        key=lambda pair: pair[0],
    )
    for lower_name, mid in moves_by_name:
        key_escaped = lower_name.replace("\\", "\\\\").replace('"', '\\"')
        lines.append(f'        "{key_escaped}" -> movesMap[{mid}]')
    lines.append("        else -> null")
    lines.append("    }")
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

    for i, chunk in enumerate(ability_chunks):
        lines.append(f"    private fun registerAbilityChunk{i + 1}() {{")
        for aid, a in chunk:
            name_escaped = a["name"].replace('"', '\\"')
            lines.append(f'        abilityNameMap[{aid}] = "{name_escaped}"')
        lines.append("    }")
        lines.append("")

    for i, chunk in enumerate(species_ability_chunks):
        lines.append(f"    private fun registerSpeciesAbilityChunk{i + 1}() {{")
        for sid, slots in chunk:
            slot_args = ", ".join("null" if s is None else str(s) for s in slots)
            lines.append(f"        speciesAbilityMap[{sid}] = arrayOf({slot_args})")
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

    print("Extracting ability catalogue and species ability slots via preprocessor...")
    abilities_dict, species_abilities = extract_species_abilities(cpp_bin, upstream_dir)
    declared_count = sum(1 for s in species_abilities.values() if s is not None)
    print(f"Extracted {len(abilities_dict)} abilities and {declared_count} species slot declarations.")

    print("Validating data integrity...")
    validate_extracted_data(species_dict, moves_dict, abilities_dict, species_abilities)
    print("Validation passed.")

    kotlin_code = generate_kotlin_source(species_dict, moves_dict, abilities_dict, species_abilities)

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
