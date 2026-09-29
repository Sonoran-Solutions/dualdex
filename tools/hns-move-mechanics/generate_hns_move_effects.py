#!/usr/bin/env python3
"""Generate the H&S 2.0.5 move-effect and ordinary-move map from the pinned source.

This is the source-check for the move-mechanics capability gate (issue #9, Gap C4a).

Why this exists: the calculator bridge forwards only a move's power, type and category.
That describes an ordinary fixed-base-power attack and nothing else. H&S acts on a move's
``enum BattleMoveEffects`` value, its multi-hit fields and its damage-relevant flags to
scale base power, read HP/friendship/weight/speed/state, hit multiple times, or deal fixed
damage. None of that is present in the generated data pack.

Extraction strategy: the pinned ``src/data/moves_info.h`` writes each move's own effect as
a designated initializer ``.effect = EFFECT_*`` and its flags as ``.flag = TRUE``. This
script:

  * resolves ``enum Move`` from the pinned header (explicit values, aliases, implicit
    progression);
  * treats any move whose own effect is not a single unambiguous ``EFFECT_*`` symbol as
    **unresolved** and omits it, so the runtime gate fails closed rather than guessing a
    build configuration;
  * computes the ordinary set as the moves whose effect is exactly ``EFFECT_HIT`` and that
    do not carry a damage-relevant complication: multi-hit (``multiHit`` or
    ``strikeCount > 1``), the ``explosion`` flag (H&S keeps ``B_EXPLOSION_DEFENSE`` at
    ``GEN_LATEST`` while the ADV pipeline halves Defense), ``alwaysCriticalHit``, or any of
    the unmodelled state-dependent damage flags. ``ignoresTargetAbility`` is retained as
    source-derived metadata and handled by the request-local Group C layer.

The generated artifact is `app/src/main/java/com/dualdex/pokemon/hns/Hns205MoveEffects.kt`.
It is a self-contained Kotlin object, so ordinary `./ci.sh test` never needs the upstream
checkout. Re-verification belongs to `./ci.sh source-check` via `--verify`.

Usage:
  python3 tools/hns-move-mechanics/generate_hns_move_effects.py \
      --upstream-dir <pokehns-expansion checkout> [--verify]

Pinned upstream revision: 1f42b74dff0e9fe942419845d040663dd829a973
(tag Release-v2.0.5).
"""

import argparse
import json
import os
import re
import subprocess
import sys

PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"

DEFAULT_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_TARGET_FILE = os.path.join(
    DEFAULT_REPO_ROOT,
    "app/src/main/java/com/dualdex/pokemon/hns/Hns205MoveEffects.kt",
)
DEFAULT_JSON_FILE = os.path.join(
    DEFAULT_REPO_ROOT, "tools/hns-move-mechanics/hns_move_damage_metadata.json"
)

DEFAULT_UPSTREAM_SEARCH_PATHS = [
    os.environ.get("HNS_UPSTREAM_DIR"),
    os.path.join(os.path.dirname(DEFAULT_REPO_ROOT), "upstream-hns/pokehns-expansion"),
]

MOVE_ENUM_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*(?:=\s*([^,]+))?,?$")
MOVE_ENTRY_RE = re.compile(r"^\s*\[(MOVE_[A-Z0-9_]+)\]\s*=")
EFFECT_VALUE_RE = re.compile(r"\.effect\s*=\s*([^,\n]+)")
TARGET_VALUE_RE = re.compile(r"\.target\s*=\s*([^,\n]+)")
PRIORITY_VALUE_RE = re.compile(r"\.priority\s*=\s*([^,\n]+)")
PLAIN_EFFECT_RE = re.compile(r"EFFECT_[A-Z0-9_]+")
PLAIN_TARGET_RE = re.compile(r"TARGET_[A-Z0-9_]+")

# Flags that make an otherwise-EFFECT_HIT move's damage depend on battle state the
# request shape cannot express, so the ADV pipeline cannot be trusted to reproduce it.
STATE_DEPENDENT_FLAGS = (
    "ignoresTargetDefenseEvasionStages",
    "damagesUnderground",
    "damagesUnderwater",
    "damagesAirborne",
    "damagesAirborneDoubleDamage",
    "minimizeDoubleDamage",
    "ignoreTypeIfFlyingAndUngrounded",
    "alwaysCriticalHit",
)

# Exact pinned MoveInfo flags consumed by Group C's immunity/suppression path.
IMMUNITY_FLAGS = ("soundMove", "ballisticMove", "windMove", "healingMove", "ignoresTargetAbility")
ABILITY_MOVE_FLAGS = ("punchingMove", "bitingMove", "pulseMove", "slicingMove")
CONTACT_FLAGS = ("makesContact", "punchingMove")


def find_upstream_dir(provided):
    """Resolve an upstream checkout, failing closed when none is present."""
    candidates = []
    if provided:
        candidates.append(provided)
    candidates.extend(p for p in DEFAULT_UPSTREAM_SEARCH_PATHS if p)
    for candidate in candidates:
        if os.path.isfile(os.path.join(candidate, "src/data/moves_info.h")):
            return os.path.abspath(candidate)
    raise FileNotFoundError(
        "Could not locate the pinned H&S checkout (src/data/moves_info.h missing). "
        "Set HNS_UPSTREAM_DIR or pass --upstream-dir."
    )


def verify_git_commit(upstream_dir):
    """Refuse to extract from anything but the pinned revision."""
    try:
        head = subprocess.check_output(
            ["git", "-C", upstream_dir, "rev-parse", "HEAD"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (subprocess.CalledProcessError, OSError) as exc:
        raise RuntimeError(f"Could not resolve git HEAD in {upstream_dir}: {exc}") from exc
    if head != PINNED_COMMIT:
        raise RuntimeError(
            f"Upstream checkout is at {head}, expected pinned {PINNED_COMMIT} ({PINNED_TAG})."
        )


def verify_helper_contract(upstream_dir, ordinary):
    """Pin the helper expressions whose semantics back the generated contact/Sheer Force data."""
    move_h = open(os.path.join(upstream_dir, "include/move.h"), encoding="utf-8").read()
    battle = open(os.path.join(upstream_dir, "src/battle_util.c"), encoding="utf-8").read()
    contracts = (
        (move_h, "return gMovesInfo[SanitizeMoveId(moveId)].makesContact;"),
        (move_h, "return gMovesInfo[SanitizeMoveId(moveId)].punchingMove;"),
        (battle, "if ((additionalEffect->chance > 0) != additionalEffect->sheerForceOverride)"),
        (battle, "if (!(MoveMakesContact(move) || (GetMoveEffect(move) == EFFECT_SHELL_SIDE_ARM"),
        (battle, "else if (holdEffectAtk == HOLD_EFFECT_PUNCHING_GLOVE && IsPunchingMove(move))"),
        (battle, "else if (abilityAtk == ABILITY_LONG_REACH)"),
    )
    for source, text in contracts:
        if text not in source:
            raise ValueError(f"pinned contact/Sheer Force helper changed; review source contract: {text}")
    enum_text = open(os.path.join(upstream_dir, "include/constants/moves.h"), encoding="utf-8").read()
    move_ids = parse_move_enum(enum_text)
    shell_side_arm = move_ids.get("MOVE_SHELL_SIDE_ARM")
    if shell_side_arm in ordinary:
        raise ValueError("Shell Side Arm must remain outside the ordinary move surface")


def parse_move_enum(text):
    """Parse ``enum Move`` into name -> numeric ID.

    Explicit values, symbolic aliases and implicit progression are all honoured, and every
    member (including non-``MOVE_`` anchors such as ``MOVES_COUNT_GEN1``) consumes one slot.
    """
    match = re.search(r"enum[^{]*\bMove\b[^{]*\{(.*?)\}", text, re.S)
    if not match:
        raise ValueError("Could not find enum Move in the pinned moves header")
    ids = {}
    current = 0
    for raw in match.group(1).splitlines():
        line = raw.split("//", 1)[0].strip()
        entry = MOVE_ENUM_RE.match(line)
        if not entry:
            continue
        name, value = entry.group(1), entry.group(2)
        if value is not None:
            value = value.strip()
            if value.isdigit():
                current = int(value)
            elif value in ids:
                current = ids[value]
            else:
                raise ValueError(f"Unresolved enum alias {value!r} for {name}")
        ids[name] = current
        current += 1
    if not any(name.startswith("MOVE_") for name in ids):
        raise ValueError("Parsed enum Move but found no MOVE_* members")
    return ids


def _entry_body(lines, start, end):
    """Yield (move_symbol, body_lines) for each ``[MOVE_*]`` entry in the table."""
    i = start
    while i < end:
        entry = MOVE_ENTRY_RE.match(lines[i])
        if not entry:
            i += 1
            continue
        symbol = entry.group(1)
        body = []
        depth = 0
        started = False
        while i < end:
            body.append(lines[i])
            depth += lines[i].count("{") - lines[i].count("}")
            if started and depth <= 0:
                i += 1
                break
            started = True
            i += 1
        yield symbol, body


def classify_body(body, *, include_immunity_metadata=False):
    """Return the effect classification for one move body.

    ``effect`` is the single unambiguous ``EFFECT_*`` symbol, or None when the effect is
    conditional, computed or declared more than once. ``complication`` is a short reason
    string when an ``EFFECT_HIT`` move must not be treated as ordinary, else None.
    ``target`` is the single unambiguous ``TARGET_*`` symbol, or None.

    The original three-field result remains the default for other checked-in source readers.
    The move-effect artifact generator opts into the appended immunity flags and priority facts.
    """
    if_depth = 0
    depth = 0
    plain_effects = set()
    computed_effects = set()
    plain_targets = set()
    computed_targets = set()
    multi_hit = False
    strike_count = 1
    explosion = False
    flags = []
    immunity_flags = set()
    unknown_immunity_flags = set()
    plain_priorities = set()
    computed_priorities = set()
    for line in body:
        stripped = line.strip()
        if stripped.startswith("#if"):
            if_depth += 1
        elif stripped.startswith("#endif"):
            if_depth -= 1
        if stripped.startswith(".multiHit") and "TRUE" in stripped:
            multi_hit = True
        strike = re.search(r"\.strikeCount\s*=\s*(\d+)", stripped)
        if strike:
            strike_count = max(strike_count, int(strike.group(1)))
        if stripped.startswith(".explosion") and "TRUE" in stripped:
            explosion = True
        for flag in STATE_DEPENDENT_FLAGS:
            if re.match(rf"\.{re.escape(flag)}\s*=\s*TRUE\b", stripped):
                flags.append(flag)
        for flag in IMMUNITY_FLAGS:
            found_flag = re.match(rf"\.{re.escape(flag)}\s*=\s*([^,]+)", stripped)
            if found_flag:
                value = found_flag.group(1).strip()
                if if_depth == 0 and value == "TRUE":
                    immunity_flags.add(flag)
                elif value not in ("FALSE", "0") or if_depth != 0:
                    unknown_immunity_flags.add(flag)
        if ".zMove" not in line:
            found = EFFECT_VALUE_RE.search(line)
            if found and depth <= 1:
                value = found.group(1).strip()
                if if_depth == 0 and PLAIN_EFFECT_RE.fullmatch(value):
                    plain_effects.add(value)
                else:
                    computed_effects.add(value)
            found_target = TARGET_VALUE_RE.search(line)
            if found_target and depth <= 1:
                tvalue = found_target.group(1).strip()
                if if_depth == 0 and PLAIN_TARGET_RE.fullmatch(tvalue):
                    plain_targets.add(tvalue)
                else:
                    computed_targets.add(tvalue)
            found_priority = PRIORITY_VALUE_RE.search(line)
            if found_priority and depth <= 1:
                value = found_priority.group(1).strip()
                if if_depth == 0 and re.fullmatch(r"-?\d+", value):
                    plain_priorities.add(int(value))
                else:
                    computed_priorities.add(value)
        depth += line.count("{") - line.count("}")

    effect = next(iter(plain_effects)) if len(plain_effects) == 1 and not computed_effects else None
    target = next(iter(plain_targets)) if len(plain_targets) == 1 and not computed_targets else None
    priority = None if computed_priorities or len(plain_priorities) > 1 else next(iter(plain_priorities), 0)
    if effect != "EFFECT_HIT":
        complication = None
    elif multi_hit:
        complication = "multiHit"
    elif strike_count > 1:
        complication = f"strikeCount={strike_count}"
    elif explosion:
        complication = "explosion"
    elif flags:
        complication = flags[0]
    else:
        complication = None
    legacy = (effect, complication, target)
    if include_immunity_metadata:
        return (*legacy, immunity_flags, unknown_immunity_flags, priority)
    return legacy


def parse_move_table(text):
    """Return source-derived effect/target/ordinary/flag/priority maps by move symbol."""
    lines = text.splitlines()
    start = next(
        (i for i, line in enumerate(lines) if "gMovesInfo[MOVES_COUNT_ALL]" in line),
        None,
    )
    if start is None:
        raise ValueError("Could not find gMovesInfo[MOVES_COUNT_ALL] in the pinned move table")
    end = next(
        (i for i in range(start, len(lines)) if lines[i].strip() == "};"),
        None,
    )
    if end is None:
        raise ValueError("Could not find the end of the pinned gMovesInfo table")

    effect_by_symbol = {}
    target_by_symbol = {}
    ordinary_symbols = set()
    unresolved = set()
    flags_by_symbol = {}
    unknown_flags_by_symbol = {}
    priority_by_symbol = {}
    unknown_priority_symbols = set()
    for symbol, body in _entry_body(lines, start, end):
        effect, complication, target, flags, unknown_flags, priority = classify_body(
            body, include_immunity_metadata=True
        )
        flags_by_symbol[symbol] = flags
        unknown_flags_by_symbol[symbol] = unknown_flags
        if priority is None:
            unknown_priority_symbols.add(symbol)
        else:
            priority_by_symbol[symbol] = priority
        if effect is None:
            unresolved.add(symbol)
            continue
        effect_by_symbol[symbol] = effect
        if target is not None:
            target_by_symbol[symbol] = target
        # Literal target-ability bypass is modelled by the request-local Group C layer. A
        # conditional/computed bypass remains unknown and must not enter the ordinary allow-list.
        if effect == "EFFECT_HIT" and complication is None and "ignoresTargetAbility" not in unknown_flags:
            ordinary_symbols.add(symbol)
    return (effect_by_symbol, target_by_symbol, ordinary_symbols, unresolved, flags_by_symbol,
            unknown_flags_by_symbol, priority_by_symbol, unknown_priority_symbols)


def parse_ability_move_flags(text):
    """Read the literal source predicates used by four CalcMoveBasePower abilities.

    The fields are bitfields on MoveInfo, read directly by IsPunchingMove, IsBitingMove,
    IsPulseMove, and IsSlicingMove. Omitted fields are source-proven false (zero-initialized);
    conditional or computed initializers are unknown and must fail closed.
    """
    lines = text.splitlines()
    start = next((i for i, line in enumerate(lines) if "gMovesInfo[MOVES_COUNT_ALL]" in line), None)
    if start is None:
        raise ValueError("Could not find gMovesInfo[MOVES_COUNT_ALL] in the pinned move table")
    end = next((i for i in range(start, len(lines)) if lines[i].strip() == "};"), None)
    if end is None:
        raise ValueError("Could not find the end of the pinned gMovesInfo table")

    flags_by_symbol = {}
    unknown_by_symbol = {}
    for symbol, body in _entry_body(lines, start, end):
        depth = 0
        values = {}
        unknown = set()
        for line in body:
            stripped = line.strip()
            if stripped.startswith("#if"):
                depth += 1
            for flag in ABILITY_MOVE_FLAGS:
                found = re.match(rf"\.{re.escape(flag)}\s*=\s*([^,]+)", stripped)
                if not found:
                    continue
                value = found.group(1).strip()
                if depth != 0:
                    unknown.add(flag)
                elif value in ("TRUE", "1"):
                    values[flag] = True
                elif value in ("FALSE", "0"):
                    values[flag] = False
                else:
                    unknown.add(flag)
            if stripped.startswith("#endif"):
                depth -= 1
        unknown.update(flag for flag, value in values.items() if flag in unknown)
        flags_by_symbol[symbol] = {flag for flag, value in values.items() if value and flag not in unknown}
        unknown_by_symbol[symbol] = unknown
    return flags_by_symbol, unknown_by_symbol


def parse_contact_and_sheer_force(text):
    """Extract the exact MoveMakesContact and MoveIsAffectedBySheerForce operands.

    MoveInfo bitfields default to zero when omitted. Sheer Force iterates every literal
    AdditionalEffect and returns true when ``(chance > 0) != sheerForceOverride``.
    Conditional or computed initializers are unknown; consumers fail closed.
    """
    lines = text.splitlines()
    start = next((i for i, line in enumerate(lines) if "gMovesInfo[MOVES_COUNT_ALL]" in line), None)
    if start is None:
        raise ValueError("Could not find gMovesInfo[MOVES_COUNT_ALL] in the pinned move table")
    end = next((i for i in range(start, len(lines)) if lines[i].strip() == "};"), None)
    if end is None:
        raise ValueError("Could not find the end of the pinned gMovesInfo table")
    contact, unknown_contact, sheer, unknown_sheer = {}, {}, {}, {}
    for symbol, body in _entry_body(lines, start, end):
        text_body = "\n".join(body)
        depth = 0
        values, unknown = set(), set()
        for line in body:
            stripped = line.strip()
            if stripped.startswith("#if"):
                depth += 1
            for flag in CONTACT_FLAGS:
                found = re.match(rf"\.{re.escape(flag)}\s*=\s*([^,]+)", stripped)
                if found:
                    value = found.group(1).strip()
                    if depth or value not in ("TRUE", "FALSE", "1", "0"):
                        unknown.add(flag)
                    elif value in ("TRUE", "1"):
                        values.add(flag)
            if stripped.startswith("#endif"):
                depth -= 1
        contact[symbol] = values
        unknown_contact[symbol] = unknown

        initializer = re.search(r"\.additionalEffects\s*=", text_body)
        active_condition = 0 if initializer is None else (
            len(re.findall(r"^\s*#if\b", text_body[:initializer.start()], re.M)) -
            len(re.findall(r"^\s*#endif\b", text_body[:initializer.start()], re.M))
        )
        match = re.search(r"\.additionalEffects\s*=\s*ADDITIONAL_EFFECTS\s*\((.*?)\)\s*,", text_body, re.S)
        if not match:
            if re.search(r"\.additionalEffects\s*=", text_body):
                unknown_sheer[symbol] = True
            else:
                sheer[symbol] = False
            continue
        effects = match.group(1)
        if active_condition != 0 or "#if" in effects or "#else" in effects or "#endif" in effects:
            unknown_sheer[symbol] = True
            continue
        effect_bodies = re.findall(r"\{([^{}]*)\}", effects, re.S)
        if not effect_bodies:
            unknown_sheer[symbol] = True
            continue
        result = False
        unresolved = False
        for effect in effect_bodies:
            chance_match = re.search(r"\.chance\s*=\s*([^,\n}]+)", effect)
            override_match = re.search(r"\.sheerForceOverride\s*=\s*([^,\n}]+)", effect)
            chance = chance_match.group(1).strip() if chance_match else "0"
            override = override_match.group(1).strip() if override_match else "FALSE"
            if override not in ("TRUE", "FALSE", "1", "0"):
                unresolved = True
                break
            if re.fullmatch(r"\d+", chance):
                chance_positive = int(chance) > 0
            else:
                # Some pinned initializers use a version-dependent ternary. We do not
                # evaluate its condition: if both literal outcomes are positive (or
                # both zero), the Sheer Force predicate is nevertheless proven.
                outcomes = re.search(r"\?\s*(\d+)\s*:\s*(\d+)\s*$", chance)
                if not outcomes:
                    unresolved = True
                    break
                branch_values = (int(outcomes.group(1)) > 0, int(outcomes.group(2)) > 0)
                if branch_values[0] != branch_values[1]:
                    unresolved = True
                    break
                chance_positive = branch_values[0]
            if chance_positive != (override in ("TRUE", "1")):
                result = True
        if unresolved:
            unknown_sheer[symbol] = True
        else:
            sheer[symbol] = result
    return contact, unknown_contact, sheer, unknown_sheer


def build_maps(upstream_dir):
    """Resolve the pinned checkout into {move_id: effect} and an ordinary move-ID set."""
    moves_header = os.path.join(upstream_dir, "include/constants/moves.h")
    move_table = os.path.join(upstream_dir, "src/data/moves_info.h")
    ids = parse_move_enum(open(moves_header, encoding="utf-8", errors="replace").read())
    move_table_text = open(move_table, encoding="utf-8", errors="replace").read()
    (effect_by_symbol, target_by_symbol, ordinary_symbols, unresolved, flags_by_symbol,
     unknown_flags_by_symbol, priority_by_symbol, unknown_priority_symbols) = parse_move_table(
        move_table_text
    )
    ability_flags_by_symbol, unknown_ability_flags_by_symbol = parse_ability_move_flags(move_table_text)
    contact_by_symbol, unknown_contact_by_symbol, sheer_by_symbol, unknown_sheer_by_symbol = parse_contact_and_sheer_force(move_table_text)

    effect_by_id = {}
    target_by_id = {}
    ordinary = set()
    flags_by_id = {}
    unknown_flags_by_id = {}
    priority_by_id = {}
    unknown_priority_ids = set()
    ability_flags_by_id = {}
    unknown_ability_flags_by_id = {}
    contact_by_id = {}
    unknown_contact_by_id = {}
    sheer_by_id = {}
    unknown_sheer_by_id = {}
    for symbol, effect in effect_by_symbol.items():
        if symbol not in ids:
            raise ValueError(f"Move table references {symbol}, absent from enum Move")
        move_id = ids[symbol]
        if move_id == 0:
            continue
        if move_id in effect_by_id:
            if effect_by_id[move_id] != effect:
                raise ValueError(
                    f"Conflicting effects for move ID {move_id}: "
                    f"{effect_by_id[move_id]} vs {effect} ({symbol})"
                )
            continue
        effect_by_id[move_id] = effect
        if symbol in target_by_symbol:
            target_by_id[move_id] = target_by_symbol[symbol]
        if symbol in ordinary_symbols:
            ordinary.add(move_id)
    for symbol, flags in flags_by_symbol.items():
        if symbol not in ids or ids[symbol] == 0:
            continue
        move_id = ids[symbol]
        flags_by_id[move_id] = flags
        unknown_flags_by_id[move_id] = unknown_flags_by_symbol[symbol]
        if symbol in unknown_priority_symbols:
            unknown_priority_ids.add(move_id)
        elif symbol in priority_by_symbol:
            priority_by_id[move_id] = priority_by_symbol[symbol]
    for symbol, flags in ability_flags_by_symbol.items():
        if symbol not in ids or ids[symbol] == 0:
            continue
        move_id = ids[symbol]
        ability_flags_by_id[move_id] = flags
        unknown_ability_flags_by_id[move_id] = unknown_ability_flags_by_symbol[symbol]
    for symbol, move_id in ids.items():
        if move_id == 0 or not symbol.startswith("MOVE_"):
            continue
        if symbol in contact_by_symbol:
            contact_by_id[move_id] = contact_by_symbol[symbol]
            unknown_contact_by_id[move_id] = unknown_contact_by_symbol[symbol]
        if symbol in sheer_by_symbol:
            sheer_by_id[move_id] = sheer_by_symbol[symbol]
        if symbol in unknown_sheer_by_symbol:
            unknown_sheer_by_id[move_id] = True
    return (effect_by_id, target_by_id, ordinary, unresolved, flags_by_id, unknown_flags_by_id,
            priority_by_id, unknown_priority_ids, ability_flags_by_id,
            unknown_ability_flags_by_id, contact_by_id, unknown_contact_by_id,
            sheer_by_id, unknown_sheer_by_id)


def generate_kotlin(effect_by_id, target_by_id, ordinary, flags_by_id, unknown_flags_by_id,
                    priority_by_id, unknown_priority_ids, ability_flags_by_id,
                    unknown_ability_flags_by_id, contact_by_id, unknown_contact_by_id,
                    sheer_by_id, unknown_sheer_by_id):
    """Render the committed Kotlin artifact, sorted by numeric move ID."""
    # Map from TARGET_* symbols to their EXACT values in the pinned H&S 2.0.5
    # `enum MoveTarget` (pokehns-expansion 1f42b74d, include/constants/battle.h):
    #   TARGET_NONE=0, TARGET_SELECTED=1, TARGET_SMART=2, TARGET_DEPENDS=3,
    #   TARGET_OPPONENT=4, TARGET_RANDOM=5, TARGET_BOTH=6, TARGET_USER=7,
    #   TARGET_ALLY=8, TARGET_USER_AND_ALLY=9, TARGET_USER_OR_ALLY=10,
    #   TARGET_FOES_AND_ALLY=11, TARGET_FIELD=12, TARGET_OPPONENTS_FIELD=13,
    #   TARGET_ALL_BATTLERS=14.
    # These are internal SpreadTargetClass values, NOT a renumbered MoveTarget
    # enum; the Kotlin consumer must compare against these exact numbers.
    TARGET_CLASS_MAP = {
        "TARGET_NONE": 0,
        "TARGET_SELECTED": 1,
        "TARGET_SMART": 2,
        "TARGET_DEPENDS": 3,
        "TARGET_OPPONENT": 4,
        "TARGET_RANDOM": 5,
        "TARGET_BOTH": 6,
        "TARGET_USER": 7,
        "TARGET_ALLY": 8,
        "TARGET_USER_AND_ALLY": 9,
        "TARGET_USER_OR_ALLY": 10,
        "TARGET_FOES_AND_ALLY": 11,
        "TARGET_FIELD": 12,
        "TARGET_OPPONENTS_FIELD": 13,
        "TARGET_ALL_BATTLERS": 14,
    }
    lines = []
    lines.append("package com.dualdex.pokemon.hns")
    lines.append("")
    lines.append("/**")
    lines.append(" * Exact H&S 2.0.5 move ID -> `enum BattleMoveEffects` symbol, plus the")
    lines.append(" * source-derived ordinary-damage move set.")
    lines.append(" *")
    lines.append(" * Provenance:")
    lines.append(" *   Repository: PokemonHnS-Development/pokehns-expansion")
    lines.append(f" *   Tag: {PINNED_TAG}")
    lines.append(f" *   Commit: {PINNED_COMMIT}")
    lines.append(" *   Extraction: raw designated initializers from src/data/moves_info.h")
    lines.append(" *   ROM dependency: NONE")
    lines.append(" *")
    lines.append(" * `effectById` omits a move whose own effect is conditional or computed: the")
    lines.append(" * capability gate treats an unknown effect as unsupported rather than guess the")
    lines.append(" * build configuration.")
    lines.append(" *")
    lines.append(" * `ordinaryMoveIds` is the subset whose damage the generation III pipeline is proven")
    lines.append(" * to reproduce: `EFFECT_HIT` with no multi-hit, explosion, always-crit or")
    lines.append(" * unmodelled state-dependent damage flag. `ignoresTargetAbility` is delegated to")
    lines.append(" * the request-local Group C ability layer.")
    lines.append(" *")
    lines.append(" * `targetClassByMoveId` maps move IDs to the INTERNAL `SpreadTargetClass`")
    lines.append(" * values (see Hns205MoveEffects.SpreadTargetClass below). Those are the EXACT")
    lines.append(" * values of the pinned H&S `enum MoveTarget` members, carried verbatim; the")
    lines.append(" * map is NOT the pinned enum itself and must never be renumbered.")
    lines.append(" * Only spread classes (BOTH=6, FOES_AND_ALLY=11) affect the damage formula's")
    lines.append(" * spread reduction. Other classes are preserved with their exact enum values;")
    lines.append(" * unsupported/ambiguous classes fail closed on the consumer side.")
    lines.append(" *")
    lines.append(" * DO NOT EDIT DIRECTLY. Regenerate using:")
    lines.append(" *   python3 tools/hns-move-mechanics/generate_hns_move_effects.py")
    lines.append(" */")
    lines.append("internal object Hns205MoveEffects {")
    lines.append("    /** Pinned move ID -> the build's own effect symbol. */")
    lines.append("    val effectById: Map<Int, String> = buildMap {")
    for move_id in sorted(effect_by_id):
        lines.append(f'        put({move_id}, "{effect_by_id[move_id]}")')
    lines.append("    }")
    lines.append("    /** Pinned MoveMakesContact(move), with unresolved initializers kept distinct from false. */")
    lines.append("    val makesContactById: Map<Int, Boolean> by lazy { buildMap {")
    for move_id, flags in sorted(contact_by_id.items()):
        lines.append(f"        put({move_id}, {'true' if 'makesContact' in flags else 'false'})")
    lines.append("    } }")
    lines.append("    val unknownContactMoveIds: Set<Int> = setOf(")
    for move_id, flags in sorted(unknown_contact_by_id.items()):
        if "makesContact" in flags:
            lines.append(f"        {move_id},")
    lines.append("    )")
    lines.append("    /** Pinned MoveIsAffectedBySheerForce(move), derived from every AdditionalEffect. */")
    lines.append("    val sheerForceAffectedById: Map<Int, Boolean> by lazy { buildMap {")
    for move_id, affected in sorted(sheer_by_id.items()):
        lines.append(f"        put({move_id}, {'true' if affected else 'false'})")
    lines.append("    } }")
    lines.append("    val unknownSheerForceMoveIds: Set<Int> = setOf(")
    for move_id in sorted(unknown_sheer_by_id):
        lines.append(f"        {move_id},")
    lines.append("    )")
    lines.append("    /** Exact source-derived flags used by Iron Fist, Strong Jaw, Mega Launcher, and Sharpness. */")
    lines.append("    val abilityMoveFlagsById: Map<Int, Set<String>> = buildMap {")
    for move_id, flags in sorted(ability_flags_by_id.items()):
        if flags:
            encoded = ", ".join(json.dumps(flag) for flag in sorted(flags))
            lines.append(f"        put({move_id}, setOf({encoded}))")
    lines.append("    }")
    lines.append("    /** Conditional/config-derived ability flags are not treated as false. */")
    lines.append("    val unknownAbilityMoveFlagsById: Map<Int, Set<String>> = buildMap {")
    for move_id, flags in sorted(unknown_ability_flags_by_id.items()):
        if flags:
            encoded = ", ".join(json.dumps(flag) for flag in sorted(flags))
            lines.append(f"        put({move_id}, setOf({encoded}))")
    lines.append("    }")
    lines.append("")
    lines.append("")
    lines.append("    /** Moves proven to be ordinary fixed-base-power attacks in the pinned source. */")
    lines.append("    val ordinaryMoveIds: Set<Int> = setOf(")
    for move_id in sorted(ordinary):
        lines.append(f"        {move_id},")
    lines.append("    )")
    lines.append("")
    lines.append("    /** Exact pinned MoveInfo flags used by Group C immunity and suppression rules. */")
    lines.append("    val immunityFlagsById: Map<Int, Set<String>> = buildMap {")
    for move_id, flags in sorted(flags_by_id.items()):
        if flags:
            encoded = ", ".join(json.dumps(flag) for flag in sorted(flags))
            lines.append(f"        put({move_id}, setOf({encoded}))")
    lines.append("    }")
    lines.append("    /** Conditional/config-derived flag initializers fail closed here. */")
    lines.append("    val unknownImmunityFlagsById: Map<Int, Set<String>> = buildMap {")
    for move_id, flags in sorted(unknown_flags_by_id.items()):
        if flags:
            encoded = ", ".join(json.dumps(flag) for flag in sorted(flags))
            lines.append(f"        put({move_id}, setOf({encoded}))")
    lines.append("    }")
    lines.append("")
    lines.append("    /** Literal MoveInfo priority; omitted entries have conditional/computed priority. */")
    lines.append("    val basePriorityById: Map<Int, Int> = buildMap {")
    for move_id, priority in sorted(priority_by_id.items()):
        lines.append(f"        put({move_id}, {priority})")
    lines.append("    }")
    lines.append("    val unknownPriorityMoveIds: Set<Int> = setOf(")
    for move_id in sorted(unknown_priority_ids):
        lines.append(f"        {move_id},")
    lines.append("    )")
    lines.append("")
    lines.append("    /**")
    lines.append("     * Internal spread/target classes, carried with the EXACT values of the pinned")
    lines.append("     * H&S 2.0.5 `enum MoveTarget` (1f42b74d). This is not the pinned enum itself;")
    lines.append("     * it exists so the boundary can dispatch `GetMoveTargetCount` semantics without")
    lines.append("     * importing the game's full target vocabulary.")
    lines.append("     */")
    lines.append("    object SpreadTargetClass {")
    for name in sorted(TARGET_CLASS_MAP):
        lines.append(f"        const val {name} = {TARGET_CLASS_MAP[name]}")
    lines.append("    }")
    lines.append("")
    lines.append("    /**")
    lines.append("     * Pinned move ID -> the move's static target class from `gMovesInfo[move].target`,")
    lines.append("     * as a [SpreadTargetClass] value (exact pinned `enum MoveTarget` number).")
    lines.append("     * Only spread classes (BOTH=6, FOES_AND_ALLY=11) affect the damage formula;")
    lines.append("     * every other class fails closed on the consumer side.")
    lines.append("     */")
    lines.append("    val targetClassByMoveId: Map<Int, Int> = buildMap {")
    for move_id in sorted(target_by_id):
        target_sym = target_by_id[move_id]
        target_val = TARGET_CLASS_MAP.get(target_sym, 0)
        lines.append(f"        put({move_id}, SpreadTargetClass.{target_sym}) // pinned enum value {target_val}")
    lines.append("    }")
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def generate_metadata_json(effect_by_id, ordinary, ability_flags_by_id, unknown_ability_flags_by_id,
                           contact_by_id, unknown_contact_by_id, sheer_by_id, unknown_sheer_by_id):
    """Render a compact source-derived move metadata map for the ROM-free oracle harness."""
    result = {}
    for move_id, effect in sorted(effect_by_id.items()):
        result[str(move_id)] = {
            "effect": effect,
            "ordinary": move_id in ordinary,
            "makesContact": None if "makesContact" in unknown_contact_by_id.get(move_id, set())
                else bool(contact_by_id.get(move_id, set()) and "makesContact" in contact_by_id[move_id]),
            "punchingMove": None if "punchingMove" in unknown_ability_flags_by_id.get(move_id, set())
                else "punchingMove" in ability_flags_by_id.get(move_id, set()),
            "sheerForceAffected": None if move_id in unknown_sheer_by_id else sheer_by_id.get(move_id),
        }
    return json.dumps({"pinnedCommit": PINNED_COMMIT, "moves": result}, indent=2) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-dir", default=None)
    parser.add_argument("--output", default=DEFAULT_TARGET_FILE)
    parser.add_argument("--json-output", default=DEFAULT_JSON_FILE)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()

    upstream_dir = find_upstream_dir(args.upstream_dir)
    verify_git_commit(upstream_dir)
    (effect_by_id, target_by_id, ordinary, unresolved, flags_by_id, unknown_flags_by_id,
     priority_by_id, unknown_priority_ids, ability_flags_by_id,
     unknown_ability_flags_by_id, contact_by_id, unknown_contact_by_id,
     sheer_by_id, unknown_sheer_by_id) = build_maps(upstream_dir)
    verify_helper_contract(upstream_dir, ordinary)
    generated = generate_kotlin(effect_by_id, target_by_id, ordinary, flags_by_id,
                                unknown_flags_by_id, priority_by_id, unknown_priority_ids,
                                ability_flags_by_id, unknown_ability_flags_by_id,
                                contact_by_id, unknown_contact_by_id,
                                sheer_by_id, unknown_sheer_by_id)
    generated_json = generate_metadata_json(effect_by_id, ordinary, ability_flags_by_id,
                                            unknown_ability_flags_by_id, contact_by_id,
                                            unknown_contact_by_id, sheer_by_id, unknown_sheer_by_id)

    if args.verify:
        if not os.path.isfile(args.output):
            print(f"error: generated artifact missing: {args.output}", file=sys.stderr)
            return 1
        with open(args.output, encoding="utf-8") as handle:
            committed = handle.read()
        if committed != generated:
            print(
                "error: committed move-effect map does not match the pinned source; "
                "regenerate with tools/hns-move-mechanics/generate_hns_move_effects.py",
                file=sys.stderr,
            )
            return 1
        print(
            f"  Hns205MoveEffects.kt verified: {len(effect_by_id)} resolved effects, "
            f"{len(ordinary)} ordinary, {len(unresolved)} unresolved (fail-closed)"
        )
        if not os.path.isfile(args.json_output) or open(args.json_output, encoding="utf-8").read() != generated_json:
            print("error: oracle move metadata JSON differs from the pinned source; regenerate it", file=sys.stderr)
            return 1
        return 0

    with open(args.output, "w", encoding="utf-8") as handle:
        handle.write(generated)
    with open(args.json_output, "w", encoding="utf-8") as handle:
        handle.write(generated_json)
    print(
        f"wrote {args.output}: {len(effect_by_id)} resolved effects, "
        f"{len(ordinary)} ordinary, {len(unresolved)} unresolved (fail-closed)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
