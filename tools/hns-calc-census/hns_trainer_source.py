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

TRAINERS_HEADER = "src/data/trainers.h"

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


def _require(fields: Dict[str, str], name: str, where: str) -> str:
    if name not in fields:
        raise SourceError(f"{where} does not declare .{name}")
    return fields[name]


def _check_fields(fields: Dict[str, str], allowed: frozenset, where: str) -> None:
    unknown = sorted(set(fields) - allowed)
    if unknown:
        raise SourceError(
            f"{where} declares field(s) this reader does not understand: {', '.join(unknown)}; "
            "refusing to skip them"
        )


def _strip_outer_parens(value: str) -> str:
    value = value.strip()
    while value.startswith("(") and value.endswith(")"):
        depth = 0
        matched = True
        for index, c in enumerate(value):
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0 and index != len(value) - 1:
                    matched = False
                    break
        if not matched:
            break
        value = value[1:-1].strip()
    return value


# The compound-literal cast upstream's generator writes in front of every party initializer:
# `.party = (const struct TrainerMon[]) { ... }`.
_COMPOUND_LITERAL_RE = re.compile(
    r"^\(\s*const\s+struct\s+TrainerMon\s*\[\s*\]\s*\)\s*"
)


def _strip_initializer_prefix(value: str, where: str) -> str:
    """Remove an optional compound-literal cast, then refuse any other prefix."""
    text = value.strip()
    match = _COMPOUND_LITERAL_RE.match(text)
    if match:
        return text[match.end():].strip()
    inner = _strip_outer_parens(text)
    if inner != text and not inner.startswith("{"):
        raise SourceError(
            f"{where} has an unsupported initializer prefix: {text.splitlines()[0][:80]!r}"
        )
    return text


def _parse_string_literal(value: str, where: str) -> str:
    m = re.search(r'_\(\s*"((?:[^"\\]|\\.)*)"\s*\)', value)
    if not m:
        m = re.search(r'"((?:[^"\\]|\\.)*)"', value)
    if not m:
        raise SourceError(f"{where} is not a string literal: {value!r}")
    return m.group(1)


def _parse_int(value: str, where: str) -> int:
    text = _strip_outer_parens(value)
    if not text.isdigit():
        raise SourceError(f"{where} is not a decimal integer literal: {value!r}")
    return int(text)


def parse_braced_list(value: str, where: str) -> List[str]:
    """Split a brace-enclosed initializer into its top-level elements.

    Upstream's own generator writes the party as `(const struct TrainerMon[]) { ... }`, so a
    compound-literal cast is stripped first. Any other prefix is refused.
    """
    text = _strip_initializer_prefix(value, where)
    if not text.startswith("{"):
        raise SourceError(f"{where} is not a brace-enclosed initializer: {value!r}")
    end = match_brace(text, 0)
    if text[end:].strip():
        raise SourceError(f"{where} has trailing content after its initializer: {value!r}")
    inner = text[1:end - 1]
    items: List[str] = []
    i = 0
    while True:
        value_end = _value_end(inner, i)
        chunk = inner[i:value_end].strip()
        if chunk:
            items.append(chunk)
        if value_end >= len(inner):
            break
        i = value_end + 1
    return items


def resolve_trainers(
    text: str,
    species_by_symbol: Dict[str, SpeciesRecord],
    moves_by_symbol: Dict[str, MoveRecord],
    items_by_symbol: Dict[str, dict],
    learnset_by_symbol: Dict[str, Tuple[Tuple[int, str], ...]],
    ability_ids_by_symbol: Dict[str, int],
    display_names: Optional[Dict[int, str]] = None,
    where: str = TRAINERS_HEADER,
) -> List[TrainerRecord]:
    """Parse `src/data/trainers.h` into resolved trainer battles.

    Every construct is resolved from the pinned data passed in. Anything this function
    cannot resolve raises [SourceError] naming the trainer and the party slot, so a source
    shape it was not written for can never silently shrink the census.
    """
    source = strip_line_markers(text)
    trainers: List[TrainerRecord] = []
    pos = 0
    while True:
        m = _TRAINER_ENTRY_RE.search(source, pos)
        if not m:
            break
        difficulty, key = m.group(1), m.group(2)
        brace = source.index("{", m.end() - 1)
        end = match_brace(source, brace)
        body = source[brace + 1:end - 1]
        source_line = source.count("\n", 0, m.start()) + 1
        trainer_where = f"{where}:{source_line} trainer {key}"
        pos = end

        fields = dict(split_initializers(body, trainer_where))
        _check_fields(fields, _TRAINER_FIELDS, trainer_where)

        for pool_field in _POOL_FIELDS:
            value = fields.get(pool_field)
            if value is not None and _parse_int(value, f"{trainer_where} .{pool_field}") != 0:
                raise SourceError(
                    f"{trainer_where} uses .{pool_field}, which makes the party order (and "
                    "therefore the lead) runtime-selected; refusing to guess a lead"
                )
        ai_flags = fields.get("aiFlags")
        if ai_flags is not None and _PARTY_ORDER_AI_FLAG in ai_flags:
            raise SourceError(
                f"{trainer_where} sets {_PARTY_ORDER_AI_FLAG}, which shuffles party order; "
                "refusing to guess a lead"
            )

        party_value = fields.get("party")
        if party_value is None:
            raise SourceError(f"{trainer_where} has no .party initializer")
        party_entries = parse_braced_list(party_value, f"{trainer_where} .party")
        party_size = _parse_int(
            _require(fields, "partySize", trainer_where), f"{trainer_where} .partySize"
        )
        if party_size != len(party_entries):
            raise SourceError(
                f"{trainer_where} declares partySize {party_size} but initializes "
                f"{len(party_entries)} party entries"
            )
        trainer_class = _require(fields, "trainerClass", trainer_where)
        battle_type = _require(fields, "battleType", trainer_where)
        name = (
            _parse_string_literal(fields["trainerName"], f"{trainer_where} .trainerName")
            if "trainerName" in fields
            else None
        )

        party: List[TrainerMonRecord] = []
        for slot, entry in enumerate(party_entries):
            mon_where = f"{trainer_where} party slot {slot}"
            mon_fields = dict(split_initializers(entry, mon_where))
            _check_fields(mon_fields, _TRAINER_MON_FIELDS, mon_where)
            species_symbol = _require(mon_fields, "species", mon_where)
            if not _SPECIES_CONST_RE.match(species_symbol):
                raise SourceError(
                    f"{mon_where} .species is not a species constant: {species_symbol!r}"
                )
            species = lookup_species(species_by_symbol, species_symbol, mon_where)
            level = _parse_int(_require(mon_fields, "lvl", mon_where), f"{mon_where} .lvl")

            ability_symbol, ability_source = resolve_ability(mon_fields, species, mon_where)
            ability_id = ability_ids_by_symbol.get(ability_symbol)
            if ability_id is None:
                raise SourceError(
                    f"{mon_where} ability {ability_symbol} has no ID in the pinned ability domain"
                )

            item_symbol = mon_fields.get("heldItem", "ITEM_NONE")
            if not _ITEM_CONST_RE.match(item_symbol):
                raise SourceError(f"{mon_where} .heldItem is not an item constant: {item_symbol!r}")
            if item_symbol not in items_by_symbol:
                raise SourceError(
                    f"{mon_where} .heldItem {item_symbol} is not in the pinned gItemsInfo table"
                )

            move_symbols, moves_source = resolve_moves(
                mon_fields, species, level, learnset_by_symbol, mon_where
            )
            move_names: List[str] = []
            for move_symbol in move_symbols:
                record = moves_by_symbol.get(move_symbol)
                if record is None:
                    raise SourceError(
                        f"{mon_where} names move {move_symbol}, which is not in the pinned "
                        "gMovesInfo table"
                    )
                move_names.append(record.name)

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
                    species_name_raw=species.name,
                    level=level,
                    ability_id=ability_id,
                    ability_symbol=ability_symbol,
                    ability_name=ability_symbol,
                    ability_source=ability_source,
                    item_id=items_by_symbol[item_symbol]["id"],
                    item_symbol=item_symbol,
                    item_name=items_by_symbol[item_symbol]["source_name"],
                    moves=tuple(move_names),
                    moves_source=moves_source,
                    source_line=source_line,
                )
            )

        trainers.append(
            TrainerRecord(
                key=key,
                difficulty=difficulty,
                trainer_class=trainer_class,
                battle_type=battle_type,
                name=name,
                party_size=party_size,
                party=tuple(party),
                source_line=source_line,
            )
        )
    if not trainers:
        raise SourceError(f"no trainer entries found in {where}")
    return trainers


def lookup_species(
    species_by_symbol: Dict[str, SpeciesRecord], symbol: str, where: str
) -> SpeciesRecord:
    """Resolve a `SPECIES_*` symbol through the pinned `enum Species` -> `gSpeciesInfo` map."""
    record = species_by_symbol.get(symbol)
    if record is None:
        raise SourceError(
            f"{where} .species {symbol} is not a species identity of the pinned build"
        )
    return record


def resolve_ability(
    mon_fields: Dict[str, str],
    species: SpeciesRecord,
    where: str,
) -> Tuple[str, str]:
    """The effective ability SYMBOL for a party entry.

    A party entry that declares `.ability` uses it (the pinned build asserts it is legal for
    the species). An entry that does not is `ABILITY_NONE`, and
    `CreateNPCTrainerPartyFromTrainer` then leaves `abilityNum = 0`, so the mon gets species
    ability SLOT 0 through `GetAbilityBySpecies`; `B_TRAINER_MON_RANDOM_ABILITY` is FALSE in
    the pinned config, so no other slot is selected.
    """
    declared = mon_fields.get("ability")
    if declared is not None and declared != "ABILITY_NONE":
        if not _ABILITY_CONST_RE.match(declared):
            raise SourceError(f"{where} .ability is not an ability constant: {declared!r}")
        if declared not in species.ability_symbols:
            raise SourceError(
                f"{where} declares ability {declared}, which is not an ability slot of "
                f"{species.name} ({', '.join(species.ability_symbols)}); the pinned build "
                "asserts this case never happens"
            )
        return declared, "party-entry"
    if not species.ability_symbols:
        raise SourceError(f"{where} species {species.name} declares no ability slot")
    slot0 = species.ability_symbols[0]
    if slot0 == "ABILITY_NONE":
        raise SourceError(
            f"{where} species {species.name} declares ABILITY_NONE in ability slot 0; the game "
            "would give the mon no ability and this reader will not invent one"
        )
    return slot0, "species-slot-0"


def resolve_moves(
    mon_fields: Dict[str, str],
    species: SpeciesRecord,
    level: int,
    learnset_by_symbol: Dict[str, Tuple[Tuple[int, str], ...]],
    where: str,
) -> Tuple[Tuple[str, ...], str]:
    """The moves the game assigns, from the party entry or the level-up learnset."""
    declared = mon_fields.get("moves")
    if declared is not None:
        raw = [_strip_outer_parens(v) for v in parse_braced_list(declared, f"{where} .moves")]
        resolved: List[str] = []
        for value in raw:
            if not _MOVE_CONST_RE.match(value):
                raise SourceError(f"{where} .moves entry is not a move constant: {value!r}")
            if value == "MOVE_NONE":
                continue
            resolved.append(value)
        if resolved:
            if len(resolved) > MAX_MON_MOVES:
                raise SourceError(
                    f"{where} .moves declares {len(resolved)} moves; MAX_MON_MOVES is "
                    f"{MAX_MON_MOVES}"
                )
            return tuple(resolved), "party-entry"
        # An initializer of nothing but MOVE_NONE is `noMoveSet` in the pinned source, so the
        # game falls through to the learned moveset rather than leaving the mon with no moves.

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
    return moves, "level-up-learnset"
