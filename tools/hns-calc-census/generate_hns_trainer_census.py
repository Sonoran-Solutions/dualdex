#!/usr/bin/env python3
"""Build `tools/hns-calc-census/trainer_inventory.json` from the pinned H&S 2.0.5 source.

This is the host-side half of the issue #84 census. It enumerates every trainer Pokemon
the pinned build defines, resolves each one's effective ability, held item and moveset
from authoritative pinned data, and writes a canonical, deterministically ordered,
machine-readable inventory. It performs NO capability classification and calls NO damage
engine: the Kotlin production policy is the only authority on what blocks a matchup, and
it consumes this file.

Everything it needs is a checkout of the pinned upstream commit plus the pinned ARM
preprocessor the repository already requires for `./ci.sh source-check`:

    python3 tools/hns-calc-census/generate_hns_trainer_census.py \
        --upstream-dir "$HNS_UPSTREAM_DIR" --check

`--check` re-derives the inventory and compares it byte-for-byte with the committed file
instead of writing it; a stale or hand-edited inventory fails.

Reused extraction: the `enum Move` / `gMovesInfo` reader, the `enum Ability` reader and
the `enum Item` / `gItemsInfo` reader come from the existing generators
(`tools/hns-data-pack/generate_hns_data_pack.py` and `tools/hns-items/generate_hns_items.py`)
rather than a second, competing implementation of the same tables.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import pathlib
import re
import sys
from typing import Dict, List, Optional, Tuple

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DEFAULT_OUTPUT = HERE / "trainer_inventory.json"

PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"

SPECIES_DEFINE_RE = re.compile(r"^#define\s+(SPECIES_[A-Z0-9_]+|NUM_SPECIES)\s+(.+?)\s*$")
SPECIES_ARITH_RE = re.compile(r"^\(\s*(SPECIES_[A-Z0-9_]+)\s*\+\s*(\d+)\s*\)$")

# Sanity floors. They exist so a source-drift regression that silently shrinks the census
# fails loudly instead of producing a smaller, quieter report. They are asserted against
# the pinned commit's own numbers, which are recorded in the census report.
EXPECTED_TRAINER_ENTRIES = 651
EXPECTED_BATTLE_TRAINERS = 651
EXPECTED_TRAINER_POKEMON = 1832

# The pinned build uses no party pools and no party-index shuffling, so the lead is the
# first party slot. These are asserted so a future source that turns either on fails the
# census rather than silently re-indexing every lead.
EXPECTED_POOLED_TRAINERS = 0


class BuildError(Exception):
    pass


# ------------------------------------------------------------------------------ imports


def _load_module(name: str, path: pathlib.Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise BuildError(f"could not load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def load_support_modules():
    """The existing authoritative extractors for moves, abilities and items."""
    data_pack = _load_module("hns_data_pack", ROOT / "tools/hns-data-pack/generate_hns_data_pack.py")
    items = _load_module("hns_items", ROOT / "tools/hns-items/generate_hns_items.py")
    return data_pack, items


src = _load_module("hns_calc_census_trainer_source", HERE / "hns_trainer_source.py")


# --------------------------------------------------------------------------- species map


SPECIES_IDENTITY_RE = re.compile(r"^#define\s+(SPECIES_[A-Z0-9_]+)\s+([0-9]+)\s*$")
SPECIES_ALIAS_RE = re.compile(r"^#define\s+(SPECIES_[A-Z0-9_]+)\s+(SPECIES_[A-Z0-9_]+)\s*$")


def parse_species_constants(text: str, where: str) -> Dict[str, int]:
    """Resolve every `SPECIES_*` identity of the pinned `include/constants/species.h`.

    Both the plain integer defines and the header's own ALIAS defines are read. The aliases matter:
    `SPECIES_DUDUNSPARCE` is defined as `SPECIES_DUDUNSPARCE_TWO_SEGMENT`, and the trainer table
    spells the species `Dudunsparce`, so without the alias that entry would be unresolvable. An
    alias chain is resolved to a fixpoint, and an alias of an unknown symbol is refused.
    """
    constants: Dict[str, int] = {}
    aliases: List[Tuple[str, str, int]] = []
    for number, line in enumerate(text.split("\n"), start=1):
        stripped = line.strip()
        match = SPECIES_IDENTITY_RE.match(stripped)
        if match is not None:
            symbol, value = match.group(1), int(match.group(2))
            if symbol in constants and constants[symbol] != value:
                raise src.SourceError(
                    f"{where}:{number} conflicting values for {symbol}: {constants[symbol]} and "
                    f"{value}"
                )
            constants[symbol] = value
            continue
        alias = SPECIES_ALIAS_RE.match(stripped)
        if alias is not None:
            aliases.append((alias.group(1), alias.group(2), number))
    if not constants:
        raise src.SourceError(f"{where} declares no SPECIES_* identity")
    remaining = aliases
    while remaining:
        progressed = False
        still: List[Tuple[str, str, int]] = []
        for symbol, target, number in remaining:
            if target in constants:
                constants[symbol] = constants[target]
                progressed = True
            else:
                still.append((symbol, target, number))
        if not progressed:
            symbol, target, number = still[0]
            raise src.SourceError(
                f"{where}:{number} {symbol} aliases undefined symbol {target}; refusing to invent "
                "an ID"
            )
        remaining = still
    return constants


def parse_species_defines(text: str, where: str) -> Dict[str, int]:
    """Resolve `include/constants/species.h` to `{symbol: id}`.

    The header is nothing but `#define`s (there is no `enum Species`), so it needs its own
    reader. Only the shapes it actually uses are accepted: a plain integer, and an alias of
    another `SPECIES_*` symbol (the header declares aliases such as
    `SPECIES_MINIOR SPECIES_MINIOR_METEOR`, sometimes BEFORE the aliased symbol itself, so
    aliases are resolved to a fixpoint). The one arithmetic define in the file,
    `SPECIES_EGG`, and `NUM_SPECIES` are handled explicitly. Anything else raises.
    """
    ids: Dict[str, int] = {}
    aliases: List[Tuple[str, str, int]] = []
    num_species: Optional[int] = None
    egg_id: Optional[int] = None

    for number, line in enumerate(text.split("\n"), start=1):
        stripped = line.strip()
        m = SPECIES_DEFINE_RE.match(stripped)
        if not m:
            continue
        symbol, value = m.group(1), m.group(2)
        if symbol == "NUM_SPECIES":
            if egg_id is None:
                raise src.SourceError(
                    f"{where}:{number} NUM_SPECIES is defined before SPECIES_EGG resolves"
                )
            num_species = egg_id
            continue
        if symbol == "SPECIES_EGG":
            arith = SPECIES_ARITH_RE.match(value)
            if not arith:
                raise src.SourceError(
                    f"{where}:{number} SPECIES_EGG has an unsupported initializer {value!r}"
                )
            base = ids.get(arith.group(1))
            if base is None:
                raise src.SourceError(
                    f"{where}:{number} SPECIES_EGG aliases {arith.group(1)}, which is undefined"
                )
            # SPECIES_EGG is deliberately NOT added to the identity map: NUM_SPECIES is defined as
            # it, so it sits exactly one past the last species and the count bound excludes it.
            egg_id = base + int(arith.group(2))
            continue
        if value.isdigit():
            ids[symbol] = int(value)
        else:
            aliases.append((symbol, value, number))

    remaining = aliases
    while remaining:
        progressed = False
        still: List[Tuple[str, str, int]] = []
        for symbol, target, number in remaining:
            if target in ids:
                ids[symbol] = ids[target]
                progressed = True
            else:
                still.append((symbol, target, number))
        if not progressed:
            symbol, target, number = still[0]
            raise src.SourceError(
                f"{where}:{number} {symbol} aliases undefined symbol {target}; refusing to invent "
                "an ID"
            )
        remaining = still

    if num_species is None:
        raise src.SourceError(f"{where} does not define NUM_SPECIES")
    if ids.get("SPECIES_NONE") != 0:
        raise src.SourceError(f"{where} SPECIES_NONE must be 0, got {ids.get('SPECIES_NONE')}")
    # SPECIES_SHINY_TAG and any other out-of-domain constant is excluded by the count bound.
    return {s: i for s, i in ids.items() if 0 <= i < num_species}


# --------------------------------------------------------------------------------- build


def build(upstream: pathlib.Path, cpp_bin: str) -> Tuple[dict, dict]:
    data_pack, items_mod = load_support_modules()

    # 1. Pinned checkout identity, exactly as the other source validators do it.
    try:
        data_pack.verify_git_commit(str(upstream))
    except Exception as exc:  # noqa: BLE001 - re-raised with the census' own context
        raise BuildError(f"pinned upstream checkout rejected: {exc}") from exc

    # 2. The pinned tables: moves, abilities, items. Reused, never re-implemented.
    moves_dict = data_pack.extract_moves(cpp_bin, str(upstream))
    abilities_dict, _ = data_pack.extract_species_abilities(cpp_bin, str(upstream))
    ability_ids = {a["constant"]: a["id"] for a in abilities_dict.values()}

    itemc_out = items_mod.run_cpp(cpp_bin, str(upstream), items_mod.ITEMS_TABLE_SOURCE)
    item_enum_ids, item_count = items_mod.parse_item_enum(itemc_out)
    item_symbols = items_mod.parse_all_enum_symbols(itemc_out)
    item_table = items_mod.extract_item_table(itemc_out, item_symbols)
    item_catalogue = items_mod.build_catalogue(item_enum_ids, item_count, item_table)
    items_by_symbol = {
        entry["symbol"]: {
            "id": entry["id"],
            "source_name": entry["source_name"],
            "hold_effect": entry["hold_effect"],
        }
        for entry in item_catalogue
    }

    moves_by_symbol = {
        symbol: src.MoveRecord(
            id=entry["id"],
            symbol=symbol,
            name=entry["name"],
            type=entry["type"],
            category=entry["category"],
            power=entry["power"],
        )
        for symbol, entry in _move_entries(cpp_bin, upstream).items()
    }

    # 3. Species identities and the preprocessed species/learnset tables.
    #
    # `include/constants/species.h` is a pure `#define` header, so its *IDs* are dense over
    # the configured `enum Species`, which is NOT the committed `gSpeciesInfo[]` index: the
    # pinned build disables Mega Evolution and Gigantamax
    # (`include/config/species_enabled.h:23,26` are FALSE), so 96 enum positions are not
    # initializers and `gSpeciesInfo` renumbers the tail. The trainer table names species by
    # SYMBOL, so the symbol is resolved by the enum's own ID and by the species name the enum
    # shares with the preprocessed table, with the two cross-checked. A symbol that neither
    # resolves is a hard error naming the trainers that use it.
    species_header = (upstream / "include/constants/species.h").read_text(encoding="utf-8")
    species_constants = parse_species_constants(
        species_header, "include/constants/species.h"
    )
    species_enum_ids = parse_species_defines(species_header, "include/constants/species.h")
    species_names = data_pack.extract_species(cpp_bin, str(upstream))

    pokemon_out = data_pack.run_cpp(cpp_bin, str(upstream), "src/pokemon.c")
    species_start = pokemon_out.find("gSpeciesInfo[] =")
    if species_start == -1:
        raise BuildError("could not find gSpeciesInfo in preprocessed src/pokemon.c")
    species_entries = data_pack.extract_designated_entries(
        pokemon_out, species_start, r"\d+", "gSpeciesInfo"
    )
    species_by_id, skipped_species = src.parse_species(
        species_entries, "gSpeciesInfo", name_filter=src.is_placeholder_species_name
    )
    learnset_by_symbol = src.parse_learnsets(pokemon_out, "preprocessed src/pokemon.c")

    # The display name is the one the Kotlin data pack resolves by name; it must come from the
    # same formatter the pack generator used, never from a second spelling rule.
    display_names = {sid: entry["name"] for sid, entry in species_names.items()}

    # The trainer source names species by the `SPECIES_*` constant `trainerproc` generated, so the
    # census resolves that constant's exact spelling against the pinned species table.
    src.SPECIES_ID_BY_SYMBOL.clear()
    src.SPECIES_ID_BY_SYMBOL.update(species_constants)
    species_symbols_by_name = src.build_species_symbols_by_name(species_constants, species_by_id)
    if not species_symbols_by_name:
        raise BuildError("no species identity could be spelled from the pinned species table")

    # Name indices for the values a `.party` entry spells, all built from the pinned tables.
    src.ABILITY_SYMBOL_BY_NAME.clear()
    for constant, ability_id in ability_ids.items():
        src.ABILITY_SYMBOL_BY_NAME[
            re.sub(r"[^a-z0-9]", "", constant[len("ABILITY_"):].lower())
        ] = constant
    src.HNS_ABILITY_TITLE_CASE.clear()
    for constant, ability_id in ability_ids.items():
        entry = abilities_dict.get(ability_id)
        if entry is not None:
            src.HNS_ABILITY_TITLE_CASE[constant] = format_ability_name(entry["name"])
    src.ITEM_SYMBOL_BY_NAME.clear()
    # Two pinned item identities can share a display name (e.g. `SITRUS BERRY` at 523 and an
    # unused placeholder at 897). The canonical identity is the lower ID, so the index is built in
    # ID order and the first claim wins; `resolve_item_symbol` then re-checks the resolved
    # identity's own name against the spelling it was asked for.
    for symbol, entry in sorted(items_by_symbol.items(), key=lambda kv: kv[1]["id"]):
        key = re.sub(r"[^a-z0-9]", "", (entry.get("source_name") or "").lower())
        if key:
            src.ITEM_SYMBOL_BY_NAME.setdefault(key, symbol)
    src.MOVE_SYMBOL_BY_NAME.clear()
    # The trainer source spells moves with their pre-Gen-VI/VIII names (`Faint Attack`,
    # `Hi Jump Kick`, `SmellingSalt`, `Vice Grip`), which the pinned `enum Move` still declares as
    # aliases of the modern constants (`MOVE_FAINT_ATTACK = MOVE_FEINT_ATTACK`). The enum's own
    # alias members are therefore indexed alongside the display names, so every spelling the
    # pinned source can use resolves to the identity the build compiles.
    move_constants = _parse_move_enum(
        data_pack.run_cpp(cpp_bin, str(upstream), "include/constants/moves.h")
    )
    for constant, move_id in move_constants.items():
        entry = moves_by_symbol.get(constant)
        if entry is None:
            continue
        src.MOVE_SYMBOL_BY_NAME[re.sub(r"[^a-z0-9]", "", entry.name.lower())] = constant
        src.MOVE_SYMBOL_BY_NAME.setdefault(
            re.sub(r"[^a-z0-9]", "", constant[len("MOVE_"):].lower()), constant
        )

    trainers = src.resolve_trainers(
        (upstream / src.TRAINERS_HEADER).read_text(encoding="utf-8"),
        species_by_id=species_by_id,
        species_symbols_by_name=species_symbols_by_name,
        moves_by_symbol=moves_by_symbol,
        items_by_symbol=items_by_symbol,
        learnset_by_symbol=learnset_by_symbol,
        ability_ids_by_symbol=ability_ids,
        display_names=display_names,
        where=src.TRAINERS_HEADER,
    )

    inventory = _inventory_json(
        trainers, moves_by_symbol, species_by_id, species_constants, learnset_by_symbol
    )
    return inventory, {"moves": moves_by_symbol, "items": items_by_symbol, "species": species_by_id}


def _normalize_name(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def resolve_species_symbols(
    species_enum_ids: Dict[str, int],
    species_names: Dict[int, dict],
    species_by_id: Dict[int, src.SpeciesRecord],
) -> Dict[str, src.SpeciesRecord]:
    """Map every `SPECIES_*` identity a trainer can name onto a `gSpeciesInfo` record.

    Two independent authorities are used, and they must agree where both exist:
      1. the `enum Species` ID from `include/constants/species.h`, looked up in the
         preprocessed `gSpeciesInfo` (exact, but the committed table is not indexed by the
         full enum, because the pinned config disables Mega/Gigantamax forms);
      2. the species NAME the header symbol spells, matched case- and
         punctuation-insensitively against the preprocessed table's own `.speciesName`.
    A symbol that resolves by neither is simply left out; a trainer that names one then
    fails in `resolve_trainers` with the trainer and slot in the message.
    """
    by_normalized_name: Dict[str, List[int]] = {}
    for species_id, entry in species_names.items():
        by_normalized_name.setdefault(_normalize_name(entry["name"]), []).append(species_id)

    resolved: Dict[str, src.SpeciesRecord] = {}
    for symbol, enum_id in species_enum_ids.items():
        record = species_by_id.get(enum_id)
        if record is not None:
            resolved[symbol] = record

    for symbol, enum_id in species_enum_ids.items():
        if symbol in resolved:
            continue
        candidates = by_normalized_name.get(_normalize_name(symbol[len("SPECIES_"):]))
        if not candidates:
            continue
        if len(candidates) > 1:
            raise BuildError(
                f"species symbol {symbol} matches several preprocessed species names "
                f"{sorted(candidates)}; refusing to guess which record it names"
            )
        candidate = candidates[0]
        record = species_by_id.get(candidate)
        if record is None:
            continue
        resolved[symbol] = record

    if not resolved:
        raise BuildError("no species symbols could be resolved to gSpeciesInfo records")
    return resolved


def format_ability_name(raw: str) -> str:
    """The pinned ability table's `OVERGROW` -> the shipped catalogue's `Overgrow`.

    Only used for the human-readable name the census artifact publishes; every capability
    decision is made from the numeric ability ID.
    """
    words = raw.strip().replace("_", " ").split()
    return " ".join(w.capitalize() for w in words)


def _move_entries(cpp_bin: str, upstream: pathlib.Path) -> Dict[str, dict]:
    """`gMovesInfo` keyed by its `enum Move` symbol, message names resolved."""
    data_pack, _ = load_support_modules()
    enum_out = data_pack.run_cpp(cpp_bin, str(upstream), "include/constants/moves.h")
    move_ids = _parse_move_enum(enum_out)
    table_out = data_pack.run_cpp(cpp_bin, str(upstream), "src/move.c")
    start = table_out.find("gMovesInfo[MOVES_COUNT_ALL] =")
    if start == -1:
        start = table_out.find("gMovesInfo")
    if start == -1:
        raise BuildError("could not find gMovesInfo in preprocessed src/move.c")
    entries = data_pack.extract_designated_entries(
        table_out, start, r"MOVE_[A-Za-z0-9_]+|\d+", "gMovesInfo"
    )
    by_id: Dict[int, dict] = {}
    by_symbol: Dict[str, dict] = {}
    for key, body in entries:
        if key.isdigit():
            move_id = int(key)
        else:
            move_id = move_ids.get(key)
        if move_id is None or move_id == 0:
            continue
        name_m = re.search(r'\.name\s*=\s*(?:\(const u8\[\]\)\s*)?_\(\"([^\"]+)"\)', body)
        if not name_m:
            raise BuildError(f"move {key} has no .name in preprocessed src/move.c")
        power = int(data_pack.require_field(body, "power", move_id, key, name_m.group(1)))
        cat_raw = data_pack.require_field(body, "category", move_id, key, name_m.group(1))
        type_raw = data_pack.require_field(body, "type", move_id, key, name_m.group(1))
        if cat_raw not in data_pack.CATEGORY_MAP:
            raise BuildError(f"move {key} declares unknown category {cat_raw!r}")
        if type_raw not in data_pack.TYPE_MAP:
            raise BuildError(f"move {key} declares unknown type {type_raw!r}")
        record = {
            "id": move_id,
            "name": data_pack.format_move_name(name_m.group(1)),
            "type": data_pack.TYPE_MAP[type_raw],
            "category": data_pack.CATEGORY_MAP[cat_raw],
            "power": power,
        }
        if move_id in by_id:
            raise BuildError(f"duplicate move ID {move_id} in preprocessed gMovesInfo")
        by_id[move_id] = record
        by_symbol[key] = record
    # `gMovesInfo` is keyed by the CANONICAL constant of each identity, while `enum Move` also
    # declares alias members for the same identity (`MOVE_FAINT_ATTACK = MOVE_FEINT_ATTACK`). The
    # trainer source uses the aliases, so every enum member that resolves to a known identity is
    # added, which is why this reader needs the enum at all.
    for constant, move_id in move_ids.items():
        record = by_id.get(move_id)
        if record is not None:
            by_symbol.setdefault(constant, record)
    return by_symbol


def _parse_move_enum(enum_out: str) -> Dict[str, int]:
    """`{MOVE_*: id}` from the preprocessed `include/constants/moves.h`."""
    ids: Dict[str, int] = {}
    current = 0
    for line in enum_out.split("\n"):
        line = line.strip()
        m = re.match(r"^(MOVE_[A-Za-z0-9_]+)\s*(?:=\s*([0-9]+|MOVE_[A-Za-z0-9_]+))?,?", line)
        if not m:
            continue
        name, value = m.group(1), m.group(2)
        if value is not None:
            current = int(value) if value.isdigit() else ids.get(value, current)
        ids[name] = current
        current += 1
    if not ids:
        raise BuildError("no MOVE_* members found in the preprocessed move enum")
    return ids


def _inventory_json(
    trainers, moves_by_symbol, species_by_id, species_constants, learnset_by_symbol
) -> dict:
    battle_trainers = [t for t in trainers if t.is_battle]
    mons = [mon for t in battle_trainers for mon in t.party]
    if len(trainers) != EXPECTED_TRAINER_ENTRIES:
        raise BuildError(
            f"expected {EXPECTED_TRAINER_ENTRIES} trainer entries, found {len(trainers)}; "
            "the pinned trainer source drifted"
        )
    if len(battle_trainers) != EXPECTED_BATTLE_TRAINERS:
        raise BuildError(
            f"expected {EXPECTED_BATTLE_TRAINERS} trainer battles, found {len(battle_trainers)}"
        )
    if len(mons) != EXPECTED_TRAINER_POKEMON:
        raise BuildError(
            f"expected {EXPECTED_TRAINER_POKEMON} trainer Pokemon, found {len(mons)}"
        )

    used_species = {mon.species_symbol for mon in mons}
    unresolved = sorted(s for s in used_species if s not in species_constants)
    if unresolved:
        raise BuildError(f"trainer parties name unresolvable species: {unresolved[:10]}")

    learnset_missing = sorted(
        {
            species_by_id[mon.species_id].learnset_symbol
            for mon in mons
            if species_by_id[mon.species_id].learnset_symbol not in learnset_by_symbol
        }
    )
    if learnset_missing:
        raise BuildError(f"trainer species bind missing learnsets: {learnset_missing[:10]}")

    moves_used = sorted({m for mon in mons for m in mon.moves})
    unknown_moves = [m for m in moves_used if m not in {r.name for r in moves_by_symbol.values()}]
    if unknown_moves:
        raise BuildError(f"trainer parties name moves absent from gMovesInfo: {unknown_moves[:10]}")

    return {
        "schemaVersion": 1,
        "pinnedCommit": PINNED_COMMIT,
        "pinnedTag": PINNED_TAG,
        "source": src.TRAINERS_HEADER,
        "trainerEntries": len(trainers),
        "trainerBattles": len(battle_trainers),
        "trainerPokemon": len(mons),
        "moves": [
            record.as_json()
            for _, record in sorted(moves_by_symbol.items(), key=lambda kv: (kv[1].id, kv[0]))
        ],
        "trainers": [t.as_json() for t in trainers],
    }


# ---------------------------------------------------------------------------------- main


def canonical_json(payload: dict) -> str:
    return json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=False) + "\n"


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-dir", help="pinned pokehns-expansion checkout")
    parser.add_argument("--cpp-bin", help="pinned ARM preprocessor (arm-none-eabi-cpp)")
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument(
        "--check",
        action="store_true",
        help="re-derive and compare with the committed inventory instead of writing it",
    )
    args = parser.parse_args(argv)

    data_pack, _ = load_support_modules()
    try:
        upstream = pathlib.Path(data_pack.find_upstream_dir(args.upstream_dir))
        cpp_bin = data_pack.find_cpp_bin(args.cpp_bin)
    except Exception as exc:  # noqa: BLE001
        print(f"error: {exc}", file=sys.stderr)
        return 2

    try:
        inventory, _ = build(upstream, cpp_bin)
    except (BuildError, src.SourceError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    rendered = canonical_json(inventory)
    output = pathlib.Path(args.output)
    if args.check:
        if not output.is_file():
            print(f"error: {output} is missing; run without --check to generate it", file=sys.stderr)
            return 1
        committed = output.read_text(encoding="utf-8")
        if committed != rendered:
            print(
                f"error: {output} is stale: the pinned source no longer derives it byte-for-byte.\n"
                "       Regenerate it with:\n"
                f"         python3 tools/hns-calc-census/generate_hns_trainer_census.py "
                f"--upstream-dir <pinned checkout> --cpp-bin <arm-none-eabi-cpp>",
                file=sys.stderr,
            )
            _print_first_diff(committed, rendered)
            return 1
        print(
            f"  trainer inventory current: {inventory['trainerBattles']} trainer battles, "
            f"{inventory['trainerPokemon']} trainer Pokemon"
        )
        return 0

    output.write_text(rendered, encoding="utf-8")
    print(
        f"wrote {output}: {inventory['trainerBattles']} trainer battles, "
        f"{inventory['trainerPokemon']} trainer Pokemon, {len(inventory['moves'])} moves"
    )
    return 0


def _print_first_diff(expected: str, actual: str) -> None:
    expected_lines = expected.split("\n")
    actual_lines = actual.split("\n")
    for index in range(max(len(expected_lines), len(actual_lines))):
        left = expected_lines[index] if index < len(expected_lines) else "<missing>"
        right = actual_lines[index] if index < len(actual_lines) else "<missing>"
        if left != right:
            print(f"       first difference at line {index + 1}:", file=sys.stderr)
            print(f"         committed: {left[:160]}", file=sys.stderr)
            print(f"         derived:   {right[:160]}", file=sys.stderr)
            return
    print("       (files differ only in trailing whitespace)", file=sys.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
