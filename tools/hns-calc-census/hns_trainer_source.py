#!/usr/bin/env python3
"""Fail-closed readers for the pinned Heart & Soul 2.0.5 trainer-party source.

Issue #84 needs every trainer Pokemon of the pinned build resolved from authoritative
data: trainer identity, party slot, species, level, effective ability, held item and
moveset. This module contains ONLY source reading and pinned-semantics resolution. It
never classifies damage capability, never talks to Kotlin, and never invents a value it
could not read: a construct it does not understand raises [SourceError].

Inputs (all inside the pinned checkout of
`PokemonHnS-Development/pokehns-expansion` @
`1f42b74dff0e9fe942419845d040663dd829a973`, tag `Release-v2.0.5`):

  * `src/data/trainers.h`      - the trainer table the pinned build compiles for
                                 `POKEMON_HNS` (`src/data.c` selects it through `IS_HNS`).
                                 It is auto-generated from `src/data/trainers.party`, which
                                 is NOT committed upstream, so the generated header is the
                                 authoritative committed source. Every member is written as
                                 a C designated initializer by upstream's own generator.
  * preprocessed `src/pokemon.c` - the `gSpeciesInfo[]` ability slots and level-up learnset
                                 bindings, and the level-up learnset tables themselves.
                                 The upstream build-time headers it needs are materialized
                                 by `regen_upstream_headers` in `ci.sh`.

Pinned resolution semantics reproduced here (each is a direct reading of the pinned
source, quoted in the docstrings below):

  * an omitted `.ability` is NOT zero: `CreateNPCTrainerPartyFromTrainer`
    (`src/battle_main.c:2004`) leaves `abilityNum = 0` when the party entry declares
    `ABILITY_NONE`, and `GetAbilityBySpecies` (`src/pokemon.c:5548`) then resolves
    species ability SLOT 0. `B_TRAINER_MON_RANDOM_ABILITY` is `FALSE` in the pinned
    config (`include/config/battle.h:374`), so no other slot is chosen.
  * an omitted or all-`MOVE_NONE` `.moves` is NOT an empty moveset:
    `CustomTrainerPartyAssignMoves` (`src/battle_main.c:1975`) calls
    `GiveMonInitialMoveset`, whose body is `GiveBoxMonInitialMoveset`
    (`src/pokemon.c:3931`): iterate the species' level-up learnset in order, stop above
    the mon's level, skip level-0 entries, skip an already-known move, and once four moves
    are known drop the oldest (a rolling window). The pinned `RANDOMIZE_LEARNSET` rewrite
    is challenge-setting driven and OFF in the census baseline, so the plain learned order
    is what the game produces there.
  * an omitted `.heldItem` is `ITEM_NONE` (0) by C designated-initializer semantics, and
    `SetMonData(..., MON_DATA_HELD_ITEM, ...)` (`src/battle_main.c:2069`) stores exactly
    that. An omitted field in a designated initializer is zero-initialized, which is a
    language guarantee, not an assumption about the data.
  * party ORDER is the file order unless the trainer opts into pools. The pinned table
    declares no `.poolSize`/`.poolRuleIndex`/`.poolPickIndex`/`.poolPruneIndex` and no
    `AI_FLAG_RANDOMIZE_PARTY_INDICES`, so `DoTrainerPartyPool` (`src/trainer_pools.c:370`)
    leaves `monIndices[i] = i`. A trainer that starts using either construct makes the
    lead ambiguous, so this module refuses it instead of guessing.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field as dataclass_field
from typing import Dict, List, Optional, Sequence, Tuple

PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"

TRAINERS_HEADER = "src/data/trainers_hns.party"

MAX_MON_MOVES = 4

# `struct TrainerMon` members (include/data.h:59) and `struct Trainer` members
# (include/data.h:107). Any field outside these sets is a source shape this reader has not
# been written for, and is refused rather than skipped.
_TRAINER_FIELDS = frozenset({
    "aiFlags", "party", "items", "startingStatus", "trainerClass", "encounterMusic",
    "gender", "trainerPic", "trainerName", "battleType", "mugshotColor", "partySize",
    "poolSize", "poolRuleIndex", "poolPickIndex", "poolPruneIndex", "overrideTrainer",
    "trainerBackPic",
})
_TRAINER_MON_FIELDS = frozenset({
    "nickname", "ev", "iv", "moves", "species", "heldItem", "ability", "lvl", "ball",
    "friendship", "nature", "gender", "isShiny", "teraType", "gigantamaxFactor",
    "shouldUseDynamax", "dynamaxLevel", "tags",
})

# `DoTrainerPartyPool` changes which party index is the lead. The census reads the pinned
# table's own ordering, so a trainer that turns pooling on is refused.
_POOL_FIELDS = ("poolSize", "poolRuleIndex", "poolPickIndex", "poolPruneIndex")
_PARTY_ORDER_AI_FLAG = "AI_FLAG_RANDOMIZE_PARTY_INDICES"

_SPECIES_CONST_RE = re.compile(r"^SPECIES_[A-Z0-9_]+$")
_MOVE_CONST_RE = re.compile(r"^MOVE_[A-Z0-9_]+$")
_ITEM_CONST_RE = re.compile(r"^ITEM_[A-Z0-9_]+$")
_ABILITY_CONST_RE = re.compile(r"^ABILITY_[A-Z0-9_]+$")

_TRAINER_ENTRY_RE = re.compile(
    r"\[(DIFFICULTY_[A-Z_]+)\]\s*\[\s*([A-Za-z_][A-Za-z0-9_]*)\s*\]\s*=\s*\{"
)
_LEVEL_UP_ARRAY_RE = re.compile(
    r"static\s+const\s+struct\s+LevelUpMove\s+(s[A-Za-z0-9_]+LevelUpLearnset)\s*\[\s*\]\s*=\s*\{"
)
_LEVEL_UP_MOVE_RE = re.compile(
    r"LEVEL_UP_MOVE\s*\(\s*([0-9]+)\s*,\s*(MOVE_[A-Z0-9_]+)\s*\)"
)
_LEVEL_UP_MOVE_EXPANDED_RE = re.compile(
    r"\{\s*\.move\s*=\s*(MOVE_[A-Z0-9_]+)\s*,\s*\.level\s*=\s*([0-9]+)\s*\}"
)
_LEVEL_UP_END_RE = re.compile(r"LEVEL_UP_MOVE_END")


class SourceError(Exception):
    """A pinned source construct this reader could not resolve. Never swallowed."""


# Species names that are not real Pokemon identities: the empty table row, the egg, and the
# `??????????` placeholders the pinned source uses for an unnamed form parent. They are
# skipped explicitly (recorded as absent), never resolved to a substitute value.
PLACEHOLDER_SPECIES_NAMES = frozenset({"??????????", "Egg", "EGG", "-", ""})


def is_placeholder_species_name(name: str) -> bool:
    return name.strip() in PLACEHOLDER_SPECIES_NAMES


# --------------------------------------------------------------------------- C scanning


def strip_line_markers(text: str) -> str:
    """Drop the `#line` GNU line markers upstream's generator emits.

    `src/data/trainers.h` contains nothing else preprocessor-shaped: its only `#` directives
    are `#line` markers. Anything else that looks like a directive is refused, so a future
    `#if` cannot silently hide part of the table.
    """
    kept: List[str] = []
    for number, line in enumerate(text.split("\n"), start=1):
        stripped = line.lstrip()
        if stripped.startswith("#line "):
            continue
        if stripped.startswith("#"):
            raise SourceError(
                f"unexpected preprocessor directive at stripped line {number}: {line!r}; "
                "the trainer source reader only understands plain initializers"
            )
        kept.append(line)
    return "\n".join(kept)


def match_brace(text: str, open_index: int) -> int:
    """Return the index just past the `}` matching the `{` at [open_index].

    String, character and comment bodies are skipped so a brace inside a string literal
    cannot unbalance the walk.
    """
    if text[open_index] != "{":
        raise SourceError(f"expected '{{' at offset {open_index}, found {text[open_index]!r}")
    depth = 0
    i = open_index
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
        elif c == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
        elif c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
        elif c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 1
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise SourceError("unbalanced braces: reached end of input while scanning an initializer")


def _skip_literal(body: str, i: int) -> int:
    quote = body[i]
    i += 1
    n = len(body)
    while i < n and body[i] != quote:
        i += 2 if body[i] == "\\" else 1
    return i + 1


def split_initializers(body: str, where: str) -> List[Tuple[str, str]]:
    """Split one initializer body into its top-level `.field = value` pairs, in order."""
    fields: List[Tuple[str, str]] = []
    i = 0
    n = len(body)
    while i < n:
        c = body[i]
        if c == '"' or c == "'":
            i = _skip_literal(body, i)
            continue
        if c == "/" and i + 1 < n and body[i + 1] == "/":
            while i < n and body[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and body[i + 1] == "*":
            i += 2
            while i + 1 < n and not (body[i] == "*" and body[i + 1] == "/"):
                i += 1
            i += 1
            continue
        if c == ".":
            m = re.match(r"\.([A-Za-z_][A-Za-z0-9_]*)\s*=\s*", body[i:])
            if not m:
                raise SourceError(f"malformed designator in {where} near {body[i:i + 40]!r}")
            i += m.end()
            end = _value_end(body, i)
            fields.append((m.group(1), body[i:end].strip()))
            i = end
            continue
        if c.isspace() or c == "," or c in "{}[]()":
            i += 1
            continue
        end = _value_end(body, i)
        raise SourceError(
            f"unexpected token in {where}: {body[i:end].strip()!r}; this reader only "
            "understands designated initializers"
        )
    return fields


def _value_end(body: str, start: int) -> int:
    """End index (exclusive) of the top-level value beginning at [start]."""
    i = start
    n = len(body)
    depth = 0
    while i < n:
        c = body[i]
        if c == '"' or c == "'":
            i = _skip_literal(body, i)
            continue
        if c == "/" and i + 1 < n and body[i + 1] == "/":
            while i < n and body[i] != "\n":
                i += 1
            continue
        if c in "{([":
            depth += 1
        elif c in "})]":
            depth -= 1
        elif c == "," and depth == 0:
            break
        i += 1
    return i


def _split_top_level_commas(body: str) -> List[str]:
    parts: List[str] = []
    start = 0
    i = 0
    depth = 0
    n = len(body)
    while i < n:
        c = body[i]
        if c == '"' or c == "'":
            i = _skip_literal(body, i)
            continue
        if c == "/" and i + 1 < n and body[i + 1] == "*":
            i += 2
            while i + 1 < n and not (body[i] == "*" and body[i + 1] == "/"):
                i += 1
            i += 1
            continue
        if c in "{([":
            depth += 1
        elif c in "})]":
            depth -= 1
        elif c == "," and depth == 0:
            parts.append(body[start:i])
            start = i + 1
        i += 1
    parts.append(body[start:])
    return parts


_ENUM_MEMBER_RE = re.compile(r"^(SPECIES_[A-Z0-9_]+)$")

# The species enum's own bookkeeping members. They are enum members, not macros, so they
# survive preprocessing as symbols and must resolve like any other member; they simply are
# not species identities.
_SPECIES_ENUM_BOOKKEEPING = frozenset({
    "SPECIES_NONE", "SPECIES_EGG", "NUM_SPECIES", "SPECIES_COUNT",
})


def parse_species_enum(enum_output: str, where: str) -> Tuple[Dict[str, int], int]:
    """Resolve the preprocessed `enum Species` to a `{symbol: id}` map and its count.

    The same shape the item and ability extractors use: explicit integers, aliases of an
    already-defined member, and implicit previous+1 members. An assignment this parser
    cannot resolve is a hard error; a numeric ID is never invented from the running counter.
    """
    start = enum_output.find("enum Species")
    if start == -1:
        raise SourceError(f"could not find `enum Species` in {where}")
    brace = enum_output.find("{", start)
    if brace == -1:
        raise SourceError(f"could not find the opening brace of `enum Species` in {where}")
    end = match_brace(enum_output, brace)
    body = enum_output[brace + 1:end - 1]
    body = re.sub(r"(?m)^\s*#.*$", "", body)

    member_ids: Dict[str, int] = {}
    next_implicit: Optional[int] = None
    for raw in _split_top_level_commas(body):
        decl = re.sub(r"/\*.*?\*/", " ", raw, flags=re.S).split("//")[0].strip()
        if not decl:
            continue
        name, eq, raw_value = decl.partition("=")
        name = name.strip()
        if not _ENUM_MEMBER_RE.match(name):
            raise SourceError(f"unsupported species enum declaration in {where}: {decl!r}")
        value = raw_value.strip() if eq else None
        if value is None:
            if next_implicit is None:
                raise SourceError(
                    f"species enum member {name} has an implicit value but no previous value"
                )
            resolved = next_implicit
        elif value.isdigit():
            resolved = int(value)
        elif re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value):
            if value not in member_ids:
                raise SourceError(
                    f"unresolvable species enum assignment {name} = {value}; refusing to invent "
                    "a sequential ID"
                )
            resolved = member_ids[value]
        else:
            raise SourceError(
                f"unsupported species enum assignment {name} = {value!r}; refusing to invent a "
                "sequential ID"
            )
        if name in member_ids and member_ids[name] != resolved:
            raise SourceError(f"conflicting species enum values for {name}")
        member_ids[name] = resolved
        next_implicit = resolved + 1

    if member_ids.get("SPECIES_NONE") != 0:
        raise SourceError(f"SPECIES_NONE must be 0, got {member_ids.get('SPECIES_NONE')}")
    identities = [v for k, v in member_ids.items() if k not in _SPECIES_ENUM_BOOKKEEPING]
    if not identities:
        raise SourceError(f"no species identities resolved from `enum Species` in {where}")
    return member_ids, max(identities) + 1


# ------------------------------------------------------------------------ data classes


@dataclass(frozen=True)
class TrainerMonRecord:
    """One party entry, resolved to the values the game initializes the mon with."""

    party_slot: int
    species_id: int
    """The pinned `SPECIES_*` symbol `trainerproc` generated for this entry."""
    species_symbol: str
    species_name: str
    species_name_raw: str
    level: int
    ability_id: int
    ability_symbol: str
    ability_name: str
    ability_source: str  # "party-entry" | "species-slot-0"
    item_id: int
    item_symbol: str
    item_name: Optional[str]
    moves: Tuple[str, ...]  # move display names, in the order the game assigns them
    moves_source: str  # "party-entry" | "level-up-learnset"
    source_line: int

    def as_json(self) -> dict:
        return {
            "partySlot": self.party_slot,
            "speciesId": self.species_id,
            "species": self.species_name,
            "speciesRaw": self.species_name_raw,
            "level": self.level,
            "abilityId": self.ability_id,
            "ability": self.ability_name,
            "abilitySource": self.ability_source,
            "itemId": self.item_id,
            "item": self.item_name,
            "moves": list(self.moves),
            "movesSource": self.moves_source,
            "sourceLine": self.source_line,
        }


@dataclass(frozen=True)
class TrainerRecord:
    """One trainer battle, with its party in the pinned source's own order."""

    key: str
    difficulty: str
    trainer_class: str
    battle_type: str
    name: Optional[str]
    party_size: int
    party: Tuple[TrainerMonRecord, ...]
    source_line: int

    @property
    def is_battle(self) -> bool:
        """TRAINER_NONE is the table's unused placeholder row, not a battle."""
        return self.party_size > 0

    def as_json(self) -> dict:
        return {
            "key": self.key,
            "difficulty": self.difficulty,
            "trainerClass": self.trainer_class,
            "battleType": self.battle_type,
            "name": self.name,
            "partySize": self.party_size,
            "sourceLine": self.source_line,
            "party": [mon.as_json() for mon in self.party],
        }


@dataclass
class MoveRecord:
    id: int
    symbol: str
    name: str
    type: str
    category: str
    power: int

    @property
    def is_damaging(self) -> bool:
        """True when the pinned record can produce direct damage.

        `power == 0` is the pinned build's own marker for a move with no base power, i.e. a
        status move; a damaging move always declares a positive base power. Category is kept
        for reporting (Physical/Special/Status) but is not the discriminator, because H&S
        can be configured to derive the category from the move type
        (`optionStyle == TYPE_BASED`), so the pinned record's own `power` is the only stable
        statement about whether the move deals damage.
        """
        return self.power > 0

    def as_json(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "type": self.type,
            "category": self.category,
            "power": self.power,
        }


@dataclass(frozen=True)
class SpeciesRecord:
    id: int
    name: str
    ability_symbols: Tuple[str, ...]
    learnset_symbol: str

    def as_json(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "abilitySymbols": list(self.ability_symbols),
            "learnset": self.learnset_symbol,
        }


@dataclass
class PinnedData:
    """Everything this reader resolved from the pinned checkout."""

    moves_by_symbol: Dict[str, MoveRecord]
    moves_by_id: Dict[int, MoveRecord]
    species_by_id: Dict[int, SpeciesRecord]
    species_by_symbol: Dict[str, SpeciesRecord]
    learnset_by_symbol: Dict[str, Tuple[Tuple[int, str], ...]]
    ability_ids_by_symbol: Dict[str, int]
    items_by_symbol: Dict[str, dict]
    trainers: List[TrainerRecord] = dataclass_field(default_factory=list)


# ------------------------------------------------------------- species/learnset reading


def parse_species(
    entries: Sequence[Tuple[str, str]], where: str, name_filter=None
) -> Tuple[Dict[int, SpeciesRecord], Dict[int, str]]:
    """Resolve each `gSpeciesInfo` entry's ability slots and level-up learnset binding.

    Returns `(resolved, skipped)`: [skipped] maps the ID of a placeholder entry
    ([name_filter] rejected its name) to the name that was rejected. Such entries are
    recorded as explicitly absent, never resolved to a substitute value.
    """
    species: Dict[int, SpeciesRecord] = {}
    skipped: Dict[int, str] = {}
    for key, body in entries:
        if not key.isdigit():
            raise SourceError(f"gSpeciesInfo entry key {key!r} is not a numeric species ID")
        species_id = int(key)
        if species_id in species or species_id in skipped:
            raise SourceError(f"duplicate species ID {species_id} in {where}")
        name_m = re.search(r'\.speciesName\s*=\s*_\("([^"]+)"\)', body)
        if not name_m:
            raise SourceError(f"species {species_id} has no .speciesName in {where}")
        name = name_m.group(1).strip()
        if name_filter is not None and name_filter(name):
            skipped[species_id] = name
            continue
        learn_m = re.search(r"\.levelUpLearnset\s*=\s*([A-Za-z_][A-Za-z0-9_]*)", body)
        if not learn_m:
            raise SourceError(f"species {species_id} ({name}) has no .levelUpLearnset in {where}")
        abilities_m = re.search(r"\.abilities\s*=\s*\{([^{}]*)\}", body)
        if not abilities_m:
            raise SourceError(f"species {species_id} ({name}) has no .abilities in {where}")
        slot_values = [v.strip() for v in abilities_m.group(1).split(",") if v.strip()]
        if not slot_values:
            raise SourceError(f"species {species_id} ({name}) declares no ability slot in {where}")
        if len(slot_values) > 3:
            raise SourceError(
                f"species {species_id} ({name}) declares {len(slot_values)} ability slots; the "
                "pinned struct holds NUM_ABILITY_SLOTS = 3"
            )
        for slot, value in enumerate(slot_values):
            if not _ABILITY_CONST_RE.match(value):
                raise SourceError(
                    f"species {species_id} ({name}) ability slot {slot} is not an ability "
                    f"constant: {value!r}"
                )
        species[species_id] = SpeciesRecord(
            id=species_id,
            name=name,
            ability_symbols=tuple(slot_values),
            learnset_symbol=learn_m.group(1),
        )
    if not species:
        raise SourceError(f"no gSpeciesInfo entries parsed from {where}")
    return species, skipped


def parse_learnsets(output: str, where: str) -> Dict[str, Tuple[Tuple[int, str], ...]]:
    """Parse every `s<Species>LevelUpLearnset[]` table from a preprocessed translation unit."""
    learnsets: Dict[str, Tuple[Tuple[int, str], ...]] = {}
    for m in _LEVEL_UP_ARRAY_RE.finditer(output):
        symbol = m.group(1)
        brace = output.index("{", m.end() - 1)
        end = match_brace(output, brace)
        body = output[brace + 1:end - 1]
        entries: List[Tuple[int, str]] = []
        for em in _LEVEL_UP_MOVE_RE.finditer(body):
            entries.append((int(em.group(1)), em.group(2)))
        for em in _LEVEL_UP_MOVE_EXPANDED_RE.finditer(body):
            entries.append((int(em.group(2)), em.group(1)))
        if not entries:
            raise SourceError(
                f"learnset {symbol} in {where} carries no LEVEL_UP_MOVE entry; refusing to record "
                "an empty level-up learnset"
            )
        if symbol in learnsets:
            raise SourceError(f"duplicate learnset table {symbol} in {where}")
        learnsets[symbol] = tuple(entries)
    if not learnsets:
        raise SourceError(f"no level-up learnset tables found in {where}")
    return learnsets


def initial_moveset(learnset: Sequence[Tuple[int, str]], level: int) -> Tuple[str, ...]:
    """`GiveBoxMonInitialMoveset` (`src/pokemon.c:3931`) for the census baseline.

    Entries above [level] stop the scan, level-0 entries are skipped, an already-known move
    is not added twice, and once four moves are known the oldest is dropped. The result is
    the four most recently learned distinct level-up moves at or below [level].
    """
    moves: List[str] = []
    for move_level, move_symbol in learnset:
        if move_level > level:
            break
        if move_level == 0:
            continue
        if move_symbol in moves:
            continue
        moves.append(move_symbol)
        if len(moves) > MAX_MON_MOVES:
            del moves[0]
    return tuple(moves)


# ---------------------------------------------------------------------- trainer reading

# The pinned H&S 2.0.5 build compiles `src/data/trainers_hns.party` into its trainer table:
# `include/constants/global.h` maps `-DPOKEMON_HNS` to `IS_HNS 1`, `src/data.c` then includes
# `data/trainers_hns.h`, and that header is generated by `tools/trainerproc/trainerproc` from
# `src/data/trainers_hns.party`. The generated header is GITIGNORED upstream, so the committed
# `.party` file is the authoritative source a checkout actually carries -- and reading it also
# means the census never depends on a build artifact that CI's sparse checkout does not have.
PARTY_SOURCE = "src/data/trainers_hns.party"

_TRAINER_HEADER_RE = re.compile(r"^===\s*(TRAINER_[A-Z0-9_]+)\s*===\s*$")
_KEY_VALUE_RE = re.compile(r"^([A-Za-z][A-Za-z ]*?):\s*(.*)$")
_ITEM_BULLET_RE = re.compile(r"^-\s+(.+?)\s*$")
_IVS_RE = re.compile(
    r"^(\d+)\s+HP\s*/\s*(\d+)\s+Atk\s*/\s*(\d+)\s+Def\s*/\s*"
    r"(\d+)\s+SpA\s*/\s*(\d+)\s+SpD\s*/\s*(\d+)\s+Spe$"
)
# `0 Atk` / `31 Spe`: one stat written on its own inside a `/`-separated list.
_SINGLE_STAT_RE = re.compile(r"^(\d+)\s+(HP|Atk|Def|SpA|SpD|Spe)$")
_STAT_NAMES = frozenset({"HP", "Atk", "Def", "SpA", "SpD", "Spe"})


def _parse_ivs(value: str, where: str) -> Tuple[int, ...]:
    """Parse an `IVs:` value into a six-slot tuple.

    Accepts the full spread (`0 HP / 0 Atk / 0 Def / 0 SpA / 0 SpD / 0 Spe`), a partial one
    (`0 Atk / 0 Spe`), a single named stat (`31 Spe`), or a bare uniform value (`31`). Every part
    must be an in-range value optionally followed by a known stat name; anything else is refused
    rather than silently defaulted, because a misread IV line would change the measured Pokemon.
    """
    value = value.strip()
    if not value:
        raise SourceError(f"{where} has an empty IVs value")
    if value.isdigit():
        uniform = int(value)
        if not 0 <= uniform <= 31:
            raise SourceError(f"{where} has an out-of-range IVs value {value!r}")
        return tuple([uniform] * 6)
    slots: Dict[str, int] = {}
    unnamed: List[int] = []
    for part in value.split("/"):
        part = part.strip()
        if not part:
            raise SourceError(f"{where} has an empty entry in its IVs value {value!r}")
        single = _SINGLE_STAT_RE.match(part)
        if single is not None:
            iv, stat = int(single.group(1)), single.group(2)
            if stat in slots:
                raise SourceError(f"{where} repeats stat {stat} in its IVs value {value!r}")
            slots[stat] = iv
            continue
        if part.isdigit():
            unnamed.append(int(part))
            continue
        raise SourceError(f"{where} has an unrecognised IVs entry {part!r} in {value!r}")
    values = list(slots.values()) + unnamed
    if any(iv < 0 or iv > 31 for iv in values):
        raise SourceError(f"{where} has an out-of-range IVs value {value!r}")
    if len(values) == 6 and len(unnamed) == 0:
        # Named full spread: return it in the pinned stat order.
        return tuple(slots[stat] if stat in slots else 0 for stat in ("HP", "Atk", "Def", "SpA", "SpD", "Spe"))
    if len(values) == 1:
        return tuple([values[0]] * 6)
    return tuple(values)

# Trainer-level properties `trainerproc` understands. Each is either consumed below or recorded
# without affecting the census' damage operands; any other key fails closed.
_TRAINER_PROPERTIES = frozenset({
    "Name", "Class", "Pic", "Gender", "Music", "Double Battle", "AI", "Items", "Mugshot",
})
# Party-member properties. `Moves` is a bullet list, and a nature is a bare `Modest Nature` line,
# so both are handled separately from the `Key: value` properties.
_MON_PROPERTIES = frozenset({
    "Level", "IVs", "EVs", "Ability", "Nature", "Ball", "Happiness", "Shiny", "Tera Type",
    "Friendship", "Nickname", "Gender", "Dynamax Level", "Gigantamax", "Should Use Dynamax",
    "Tags", "Moves",
})

# The only list property a party member carries. A bullet under any other property would be a
# source shape this reader has not been written for.
_MOVE_LIST_PROPERTY = "Moves"

# A nature is written as a bare `Modest Nature` line (no colon), so it needs its own shape.
_NATURE_RE = re.compile(r"^([A-Z][a-z]+) Nature$")
_NATURES = frozenset({
    "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed", "Impish",
    "Lax", "Timid", "Hasty", "Serious", "Jolly", "Naive", "Modest", "Mild", "Quiet", "Bashful",
    "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky",
})

# Normalized spellings of the values `trainerproc` accepts for `Double Battle`.
_DOUBLES_VALUES = {"yes": True, "no": False}

MAX_PARTY_SIZE = 6


# The pinned `enum Species` identity table, injected by the generator before `resolve_trainers`
# runs. It maps a `SPECIES_*` symbol to its numeric ID exactly as the preprocessed header does.
SPECIES_ID_BY_SYMBOL: Dict[str, int] = {}

# Ability and item display-name indices, built from the pinned tables by the generator.
ABILITY_SYMBOL_BY_NAME: Dict[str, str] = {}
ITEM_SYMBOL_BY_NAME: Dict[str, str] = {}
MOVE_SYMBOL_BY_NAME: Dict[str, str] = {}
HNS_ABILITY_TITLE_CASE: Dict[str, str] = {}


class PartyLine:
    """One physical line of the `.party` source, with its 1-based number."""

    __slots__ = ("number", "text")

    def __init__(self, number: int, text: str) -> None:
        self.number = number
        self.text = text

    @property
    def is_blank(self) -> bool:
        return not self.text.strip()


def read_party_blocks(text: str, where: str) -> List[Tuple[str, List[PartyLine]]]:
    """Split a `.party` file into `(trainer key, body lines)` blocks.

    The format is line-oriented and flat: a `=== TRAINER_X ===` header, its `Key: value`
    properties, a blank line, then one block per party member whose first line is the species
    and whose following lines are `Key: value` properties or `- item` bullets. Anything outside
    that shape -- an unexpected `===` header, text before the first header, a duplicate trainer
    key -- raises rather than being skipped.
    """
    blocks: List[Tuple[str, List[PartyLine]]] = []
    seen: Dict[str, int] = {}
    current_key: Optional[str] = None
    current: List[PartyLine] = []
    for number, raw in enumerate(text.split("\n"), start=1):
        line = PartyLine(number, raw)
        header = _TRAINER_HEADER_RE.match(raw)
        if header is not None:
            if current_key is not None:
                blocks.append((current_key, current))
            current_key = header.group(1)
            if current_key in seen:
                raise SourceError(
                    f"{where}:{number} duplicate trainer block {current_key} (first at line "
                    f"{seen[current_key]})"
                )
            seen[current_key] = number
            current = []
            continue
        if raw.startswith("==="):
            raise SourceError(f"{where}:{number} unrecognised block header: {raw!r}")
        if current_key is None:
            if line.is_blank or raw.startswith("/*"):
                continue
            raise SourceError(
                f"{where}:{number} content before the first trainer block: {raw!r}"
            )
        current.append(line)
    if current_key is not None:
        blocks.append((current_key, current))
    if not blocks:
        raise SourceError(f"{where} declares no trainer block")
    return blocks


def _split_trainer_body(
    key: str, body: List[PartyLine], where: str
) -> Tuple[Dict[str, Tuple[str, int]], List[Tuple[str, List[PartyLine], int]]]:
    """Split one trainer body into its property map and its party-member blocks."""
    properties: Dict[str, Tuple[str, int]] = {}
    members: List[Tuple[str, List[PartyLine], int]] = []
    index = 0
    # Trainer properties come first and are terminated by the first blank line.
    while index < len(body):
        line = body[index]
        if line.is_blank:
            index += 1
            break
        match = _KEY_VALUE_RE.match(line.text)
        if match is None:
            raise SourceError(
                f"{where}:{line.number} trainer {key} has a line that is not a key/value "
                f"property: {line.text!r}"
            )
        name, value = match.group(1).strip(), match.group(2).strip()
        if name not in _TRAINER_PROPERTIES:
            raise SourceError(
                f"{where}:{line.number} trainer {key} declares trainer property {name!r}, which "
                "this reader does not understand; refusing to skip it"
            )
        if name in properties:
            raise SourceError(f"{where}:{line.number} trainer {key} repeats property {name!r}")
        properties[name] = (value, line.number)
        index += 1

    while index < len(body):
        line = body[index]
        if line.is_blank:
            index += 1
            continue
        if ":" in line.text:
            raise SourceError(
                f"{where}:{line.number} trainer {key}: expected a species name, found "
                f"{line.text!r}"
            )
        species = line.text.strip()
        species_line = line.number
        index += 1
        member_lines: List[PartyLine] = []
        while index < len(body) and not body[index].is_blank:
            member_lines.append(body[index])
            index += 1
        members.append((species, member_lines, species_line))
    return properties, members


def _parse_member(
    trainer_key: str,
    species_raw: str,
    lines: List[PartyLine],
    where: str,
) -> Tuple[str, int, Optional[str], Optional[str], Optional[Tuple[int, ...]], List[str]]:
    """Parse one party member into its raw `.party` values.

    Returns `(species, level, ability, held item, IVs, moves)`. Omitted fields stay `None` so the
    caller can apply the game's own initialization semantics instead of guessing.
    """
    level: Optional[int] = None
    ability: Optional[str] = None
    item: Optional[str] = None
    ivs: Optional[Tuple[int, ...]] = None
    moves: List[str] = []
    seen: set = set()
    index = 0
    list_property: Optional[str] = None
    while index < len(lines):
        line = lines[index]
        bullet = _ITEM_BULLET_RE.match(line.text)
        if bullet is not None:
            moves.append(bullet.group(1).strip())
            index += 1
            continue
        nature = _NATURE_RE.match(line.text.strip())
        if nature is not None:
            if nature.group(1) not in _NATURES:
                raise SourceError(
                    f"{where}:{line.number} trainer {trainer_key} party member {species_raw} "
                    f"declares unknown nature {line.text!r}"
                )
            index += 1
            continue
        match = _KEY_VALUE_RE.match(line.text)
        if match is None:
            raise SourceError(
                f"{where}:{line.number} trainer {trainer_key} party member {species_raw} has an "
                f"unrecognised line: {line.text!r}"
            )
        name, value = match.group(1).strip(), match.group(2).strip()
        if name not in _MON_PROPERTIES:
            raise SourceError(
                f"{where}:{line.number} trainer {trainer_key} party member {species_raw} declares "
                f"property {name!r}, which this reader does not understand; refusing to skip it"
            )
        if name in seen and name != _MOVE_LIST_PROPERTY:
            raise SourceError(
                f"{where}:{line.number} trainer {trainer_key} party member {species_raw} repeats "
                f"property {name!r}"
            )
        seen.add(name)
        if name == _MOVE_LIST_PROPERTY:
            if list_property is not None:
                raise SourceError(
                    f"{where}:{line.number} trainer {trainer_key} party member {species_raw} "
                    "declares Moves twice"
                )
            list_property = name
            index += 1
            continue
        if name == "Level":
            if not value.isdigit():
                raise SourceError(
                    f"{where}:{line.number} trainer {trainer_key} {species_raw} has a non-numeric "
                    f"Level {value!r}"
                )
            level = int(value)
        elif name == "Ability":
            ability = value
        elif name == "IVs":
            # Several spellings are in use: the full six-stat spread (`0 HP / 0 Atk / ...`), a
            # partial one (`0 Atk / 0 Spe`), a single stat (`IVs: 31`), or a bare uniform value.
            # IVs are not a damage-calculator operand for this census (the capability policy
            # consumes the request's own stat operands), so only the shape and range are checked.
            ivs = _parse_ivs(value, f"{where}:{line.number} trainer {trainer_key} {species_raw}")
        index += 1
    if level is None:
        raise SourceError(
            f"{where}:{lines[0].number if lines else 0} trainer {trainer_key} party member "
            f"{species_raw} declares no Level; the pinned generator requires one"
        )
    return species_raw, level, ability, item, ivs, moves


def resolve_trainers(
    text: str,
    species_by_id: Dict[int, SpeciesRecord],
    species_symbols_by_name: Dict[str, str],
    moves_by_symbol: Dict[str, MoveRecord],
    items_by_symbol: Dict[str, dict],
    learnset_by_symbol: Dict[str, Tuple[Tuple[int, str], ...]],
    ability_ids_by_symbol: Dict[str, int],
    display_names: Optional[Dict[int, str]] = None,
    where: str = PARTY_SOURCE,
) -> List[TrainerRecord]:
    """Parse the pinned `.party` trainer source into resolved trainer battles.

    Every construct is resolved from the pinned data passed in. Anything this function cannot
    resolve raises [SourceError] naming the trainer and the party slot, so a source shape it was
    not written for can never silently shrink the census.
    """
    trainers: List[TrainerRecord] = []
    for key, body in read_party_blocks(text, where):
        properties, members = _split_trainer_body(key, body, where)
        if not members:
            raise SourceError(
                f"{where} trainer {key} declares no party member; an empty trainer block cannot "
                "be measured"
            )
        if len(members) > MAX_PARTY_SIZE:
            raise SourceError(
                f"{where} trainer {key} declares {len(members)} party members; PARTY_SIZE is "
                f"{MAX_PARTY_SIZE}"
            )
        if "Name" not in properties:
            raise SourceError(f"{where} trainer {key} declares no Name")
        if "Double Battle" not in properties:
            raise SourceError(f"{where} trainer {key} declares no 'Double Battle' format")
        doubles_value = properties["Double Battle"][0].strip().lower()
        if doubles_value not in _DOUBLES_VALUES:
            raise SourceError(
                f"{where} trainer {key} has an unrecognised 'Double Battle' value "
                f"{properties['Double Battle'][0]!r}"
            )
        trainer_class = properties.get("Class", ("", 0))[0]
        name = properties["Name"][0]
        source_line = body[0].number - 1 if body else 0
        party: List[TrainerMonRecord] = []
        for slot, (species_raw, member_lines, species_line) in enumerate(members):
            mon_where = f"{where}:{species_line} trainer {key} party slot {slot}"
            # The species line may carry a held item (`Absol @ Leftovers`), which `trainerproc`
            # turns into the entry's `.heldItem`; a separate `Item:` property is the other spelling.
            species_only, inline_item = split_species_and_item(species_raw, mon_where)
            species_symbol, species = resolve_species(
                species_only, species_symbols_by_name, species_by_id, mon_where
            )
            _, level, ability_raw, declared_item, ivs, move_names = _parse_member(
                key, species_only, member_lines, where
            )
            del ivs  # recorded by the `.party` source; not a damage-calculator operand
            ability_symbol, ability_source = resolve_ability_symbol(
                species, ability_raw, ability_ids_by_symbol, mon_where
            )
            ability_id = ability_ids_by_symbol[ability_symbol]
            item_symbol, item_id = resolve_item_symbol(
                declared_item if declared_item is not None else inline_item,
                items_by_symbol,
                mon_where,
            )
            resolved_moves, moves_source = resolve_move_names(
                move_names, species, level, learnset_by_symbol, moves_by_symbol, mon_where
            )
            display_name = (display_names or {}).get(species.id)
            if display_names is not None and display_name is None:
                raise SourceError(
                    f"{mon_where} species {species.name} (id {species.id}) has no name in the "
                    "pinned generated species table"
                )
            party.append(
                TrainerMonRecord(
                    party_slot=slot,
                    species_id=species.id,
                    species_symbol=species_symbol,
                    species_name=(display_name if display_name is not None else species.name),
                    species_name_raw=species_raw,
                    level=level,
                    ability_id=ability_id,
                    ability_symbol=ability_symbol,
                    ability_name=HNS_ABILITY_TITLE_CASE.get(ability_symbol, ability_symbol),
                    ability_source=ability_source,
                    item_id=item_id,
                    item_symbol=item_symbol,
                    item_name=items_by_symbol[item_symbol]["source_name"],
                    moves=resolved_moves,
                    moves_source=moves_source,
                    source_line=species_line,
                )
            )
        trainers.append(
            TrainerRecord(
                key=key,
                difficulty="DIFFICULTY_NORMAL",
                trainer_class=trainer_class,
                battle_type=(
                    "TRAINER_BATTLE_TYPE_DOUBLES" if _DOUBLES_VALUES[doubles_value]
                    else "TRAINER_BATTLE_TYPE_SINGLES"
                ),
                name=name,
                party_size=len(party),
                party=tuple(party),
                source_line=source_line,
            )
        )
    return trainers


def party_species_key(spelling: str) -> str:
    """The normalized species identity a `.party` spelling names.

    The spelling may carry a held item (`Absol @ Leftovers`) and a gender marker
    (`Blastoise (M)`); both are stripped. The remainder is reduced to letters and digits, matching
    the pinned `SPECIES_*` symbol spelling (`SPECIES_MR_MIME` -> `mrmime`).
    """
    base = spelling.split("@", 1)[0].strip()
    base = re.sub(r"\((?:M|F)\)", "", base).strip()
    return re.sub(r"[^a-z0-9]", "", base.lower())


def split_species_and_item(spelling: str, where: str) -> Tuple[str, Optional[str]]:
    """Split `Species @ Item` into its two halves. At most one `@` is accepted."""
    parts = spelling.split("@")
    if len(parts) > 1 and len(parts) != 2:
        raise SourceError(f"{where} malformed species line {spelling!r}")
    species = parts[0].strip()
    if not species:
        raise SourceError(f"{where} species line {spelling!r} has no species")
    item = parts[1].strip() if len(parts) == 2 else None
    if item is not None and not item:
        raise SourceError(f"{where} species line {spelling!r} has an empty held item")
    return species, item


def resolve_species(
    spelling: str,
    species_symbols_by_name: Dict[str, str],
    species_by_id: Dict[int, SpeciesRecord],
    where: str,
) -> Tuple[str, SpeciesRecord]:
    """Resolve a `.party` species spelling to its pinned `SPECIES_*` identity and record.

    `trainerproc` maps the spelling to a species constant; the census resolves it through the
    pinned `enum Species` table's own symbol spellings rather than re-implementing the generator's
    naming rules. An ambiguous or absent spelling is a hard error naming the trainer, never a
    silent skip.
    """
    key = party_species_key(spelling)
    symbol = species_symbols_by_name.get(key)
    if symbol is None:
        raise SourceError(
            f"{where} species spelling {spelling!r} does not match any `SPECIES_*` identity of "
            "the pinned species table"
        )
    species_id = SPECIES_ID_BY_SYMBOL.get(symbol)
    if species_id is None:
        raise SourceError(
            f"{where} species spelling {spelling!r} resolved to {symbol}, which carries no ID in "
            "the pinned species table"
        )
    record = species_by_id.get(species_id)
    if record is None:
        raise SourceError(
            f"{where} species spelling {spelling!r} resolved to {symbol} (ID {species_id}), which "
            "has no record in the pinned species table"
        )
    return symbol, record


def build_species_symbols_by_name(
    species_constants: Dict[str, int], species_by_id: Dict[int, SpeciesRecord]
) -> Dict[str, str]:
    """Index every pinned `SPECIES_*` identity by its normalized symbol spelling.

    A spelling claimed by several identities is kept ONLY when exactly one of them is the
    unqualified base identity (an exact normalized symbol match). That is the rule that resolves
    `Pikachu` to `SPECIES_PIKACHU` rather than to one of its sixteen cosplay forms, and it is the
    same convention the pinned header uses for its aliases (`SPECIES_DUDUNSPARCE` is defined as
    `SPECIES_DUDUNSPARCE_TWO_SEGMENT`). An identity set with no such winner is an ambiguity the
    census refuses rather than guesses.
    """
    candidates: Dict[str, List[str]] = {}
    for symbol, species_id in species_constants.items():
        if species_id not in species_by_id:
            continue
        key = re.sub(r"[^a-z0-9]", "", symbol[len("SPECIES_"):].lower())
        candidates.setdefault(key, []).append(symbol)
    resolved: Dict[str, str] = {}
    for key, symbols in candidates.items():
        if len(symbols) == 1:
            resolved[key] = symbols[0]
            continue
        exact = [s for s in symbols if re.sub(r"[^a-z0-9]", "", s[len("SPECIES_"):].lower()) == key]
        bases = [s for s in exact if s == "SPECIES_" + key.upper()]
        if len(bases) == 1:
            resolved[key] = bases[0]
            continue
        # No unqualified base identity: the spelling is genuinely ambiguous, and every member of
        # it stays unresolvable so a `.party` entry using it fails with its own name.
    return resolved


def resolve_ability_symbol(
    species: SpeciesRecord,
    ability_raw: Optional[str],
    ability_ids_by_symbol: Dict[str, int],
    where: str,
) -> Tuple[str, str]:
    """The effective ability SYMBOL for a party entry.

    A party entry that declares `Ability:` uses it (the pinned build asserts it is legal for the
    species). An entry that does not is `ABILITY_NONE`, and `CreateNPCTrainerPartyFromTrainer`
    then leaves `abilityNum = 0`, so the mon gets species ability SLOT 0 through
    `GetAbilityBySpecies`; `B_TRAINER_MON_RANDOM_ABILITY` is FALSE in the pinned config, so no
    other slot is selected.
    """
    if ability_raw is not None and ability_raw.strip():
        symbol = ability_symbol_for_name(ability_raw, ability_ids_by_symbol)
        if symbol is None:
            raise SourceError(
                f"{where} declares ability {ability_raw!r}, which is not an ability of the "
                "pinned ability table"
            )
        if symbol != "ABILITY_NONE" and symbol not in species.ability_symbols:
            raise SourceError(
                f"{where} declares ability {ability_raw!r} ({symbol}), which is not an ability "
                f"slot of {species.name} ({', '.join(species.ability_symbols)}); the pinned build "
                "asserts this case never happens"
            )
        return symbol, "party-entry"
    if not species.ability_symbols:
        raise SourceError(f"{where} species {species.name} declares no ability slot")
    slot0 = species.ability_symbols[0]
    if slot0 == "ABILITY_NONE":
        raise SourceError(
            f"{where} species {species.name} declares ABILITY_NONE in ability slot 0; the game "
            "would give the mon no ability and this reader will not invent one"
        )
    return slot0, "species-slot-0"


def ability_symbol_for_name(
    ability_raw: str, ability_ids_by_symbol: Dict[str, int]
) -> Optional[str]:
    """Resolve an ability display name to its pinned `ABILITY_*` symbol."""
    key = re.sub(r"[^a-z0-9]", "", ability_raw.lower())
    return ABILITY_SYMBOL_BY_NAME.get(key)


def resolve_item_symbol(
    item_raw: Optional[str],
    items_by_symbol: Dict[str, dict],
    where: str,
) -> Tuple[str, int]:
    """Resolve a party entry's `Item:` to the pinned item identity.

    An omitted item is `ITEM_NONE` (0): `trainerproc` emits no `.heldItem` initializer, and a C
    designated initializer zero-fills it, which `SetMonData(..., MON_DATA_HELD_ITEM, ...)` then
    stores verbatim.
    """
    if item_raw is None or not item_raw.strip():
        return "ITEM_NONE", items_by_symbol["ITEM_NONE"]["id"]
    key = re.sub(r"[^a-z0-9]", "", item_raw.lower())
    symbol = ITEM_SYMBOL_BY_NAME.get(key)
    if symbol is None:
        raise SourceError(
            f"{where} declares item {item_raw!r}, which is not an identity of the pinned item table"
        )
    entry = items_by_symbol.get(symbol)
    if entry is None:
        raise SourceError(f"{where} item {item_raw!r} resolved to {symbol}, which has no record")
    resolved_name = re.sub(r"[^a-z0-9]", "", (entry.get("source_name") or "").lower())
    if resolved_name != key:
        raise SourceError(
            f"{where} item {item_raw!r} resolved to {symbol}, whose pinned name is "
            f"{entry.get('source_name')!r}"
        )
    return symbol, entry["id"]


def resolve_move_names(
    move_names: List[str],
    species: SpeciesRecord,
    level: int,
    learnset_by_symbol: Dict[str, Tuple[Tuple[int, str], ...]],
    moves_by_symbol: Dict[str, MoveRecord],
    where: str,
) -> Tuple[Tuple[str, ...], str]:
    """The moves the game assigns, from the party entry or the level-up learnset."""
    if move_names:
        resolved: List[str] = []
        for name in move_names:
            symbol = MOVE_SYMBOL_BY_NAME.get(re.sub(r"[^a-z0-9]", "", name.lower()))
            if symbol is None:
                raise SourceError(
                    f"{where} declares move {name!r}, which is not an identity of the pinned move "
                    "table"
                )
            record = moves_by_symbol.get(symbol)
            if record is None:
                raise SourceError(f"{where} move {name!r} resolved to {symbol}, which has no record")
            resolved.append(record.name)
        if len(resolved) > MAX_MON_MOVES:
            raise SourceError(
                f"{where} declares {len(resolved)} moves; MAX_MON_MOVES is {MAX_MON_MOVES}"
            )
        return tuple(resolved), "party-entry"

    learnset = learnset_by_symbol.get(species.learnset_symbol)
    if learnset is None:
        raise SourceError(
            f"{where} species {species.name} binds learnset {species.learnset_symbol}, which was "
            "not found in the preprocessed level-up learnset tables"
        )
    moves = initial_moveset(learnset, level)
    if not moves:
        raise SourceError(
            f"{where} {species.name} at level {level} learns no move at or below that level, so "
            "the game would leave the party slot without a move; refusing to record a Pokemon "
            "with no moves"
        )
    resolved = []
    for symbol in moves:
        record = moves_by_symbol.get(symbol)
        if record is None:
            raise SourceError(
                f"{where} learnset {species.learnset_symbol} names move {symbol}, which is not in "
                "the pinned gMovesInfo table"
            )
        resolved.append(record.name)
    return tuple(resolved), "level-up-learnset"
