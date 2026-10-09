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
import hashlib
import json
import os
from pathlib import Path
import csv
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


STATUS_DOUBLE_HELPERS = {('src/battle_util.c', 'CalcMoveBasePower'): '7054a315e10b964585b4598db925b8f94c51a8c4aeb5e6768029c4d7a52ab8cb', ('src/battle_util.c', 'CalcMoveBasePowerAfterModifiers'): 'd8fb52f85f014a3801dfbd41a8a6c61db1c5b460ef58fdc14ebf3f81298509d4', ('src/battle_script_commands.c', 'SetMoveEffect'): 'f7230d09f25725a3c134ba799af6147c0a9304ac6df348ba04b2d1bea8ca95bb'}

UNDERWATER_HELPERS = {
    ("src/battle_util.c", "GetDiveModifier"): "7eec29c898d9e9bb11aff43dbf8fe1c14bcd432c0416e2e8ceb401aada12a386",
    ("src/battle_util.c", "SetWrapTurns"): "fd93da3c9445971e436a45072f66e70d9cf5f426090b37416c155fc5f6c2595e",
    ("src/battle_util.c", "BreaksThroughSemiInvulnerablity"): "c81a2c8f32fd770657f85e1a442af8c126c0ca707600b98114d26bc425d7e1be",
    ("src/battle_end_turn.c", "HandleEndTurnWrap"): "10ff0d3ba6665405ae9b513ac1a907375a33c2c0902ab223675411a4fddfba54",
}

def verify_underwater_helper(text, name, expected):
    """Pin reviewed function bodies as well as MoveInfo: wrap semantics cannot drift silently."""
    match = re.search(r"\b" + name + r"\([^;{}]*\)\s*\{", text)
    if not match:
        raise ValueError("Missing underwater source helper: " + name)
    end, depth = match.end(), 1
    while depth and end < len(text):
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    if depth or hashlib.sha256(text[match.start():end].encode()).hexdigest() != expected:
        raise ValueError("Changed underwater source helper: " + name)


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


def parse_recoil_symbols(text):
    """Separate fixed single-hit recoil surface; never add these moves to ordinary.

    Reject unresolved execution/damage flags, target changes and multiple strikes even
    when their effect is still RECOIL. Power/type/category remain pack-owned operands.
    """
    result = set()
    for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())):
        effect, _, target, _, _, priority = classify_body(body, include_immunity_metadata=True)
        if effect != "EFFECT_RECOIL" or target != "TARGET_SELECTED" or priority != 0:
            continue
        joined = "\n".join(body)
        if any(not re.search(rf"\.{field}\s*=\s*[^,\n]+", joined)
               for field in ("power", "type", "category")):
            continue
        forbidden = (*STATE_DEPENDENT_FLAGS, "multiHit", "explosion", "ignoresTargetAbility", "gravityBanned", "healingMove")
        if any(re.search(rf"\.{flag}\s*=", joined) for flag in forbidden):
            continue
        strikes = re.findall(r"\.strikeCount\s*=\s*([^,\n]+)", joined)
        if strikes and strikes != ["1"]:
            continue
        result.add(symbol)
    return result


def parse_drain_metadata(text, heal_blocking_latest=False, updated_move_data_latest=False):
    """Bounded ABSORB family; unresolved execution or damage initializers fail closed."""
    result = {}
    frozen = {"MOVE_ABSORB", "MOVE_MEGA_DRAIN", "MOVE_LEECH_LIFE", "MOVE_GIGA_DRAIN",
              "MOVE_DRAIN_PUNCH", "MOVE_HORN_LEECH", "MOVE_DRAINING_KISS"}
    for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())):
        if symbol not in frozen:
            continue
        effect, _, target, _, _, priority = classify_body(body, include_immunity_metadata=True)
        if effect != "EFFECT_ABSORB" or target != "TARGET_SELECTED" or priority != 0:
            continue
        joined = "\n".join(body)
        if any(line.lstrip().startswith("#") for line in body):
            continue
        def values(field):
            return re.findall(rf"\.{field}\s*=\s*([^,\n}}]+)", joined)
        if any(values(flag) for flag in (*STATE_DEPENDENT_FLAGS, "multiHit", "explosion",
               "ignoresTargetAbility", "gravityBanned", "thawsUser", "cantUseTwice",
               "noAffectOnSameTypeTarget", "ignoresSubstitute", "dampBanned")):
            continue
        if values("strikeCount") not in ([], ["1"]):
            continue
        power = values("power")
        if updated_move_data_latest and power == ["B_UPDATED_MOVE_DATA >= GEN_5 ? 75 : 60"]:
            power = ["75"]
        if len(power) != 1 or not re.fullmatch(r"[1-9][0-9]*", power[0]):
            continue
        if len(values("type")) != 1 or not re.fullmatch(r"TYPE_[A-Z]+", values("type")[0]):
            continue
        if values("category") not in (["DAMAGE_CATEGORY_PHYSICAL"], ["DAMAGE_CATEGORY_SPECIAL"]):
            continue
        if any(values(flag) not in ([], ["TRUE"], ["FALSE"])
               for flag in (*ABILITY_MOVE_FLAGS, *CONTACT_FLAGS, "soundMove", "ballisticMove", "windMove")):
            continue
        healing = values("healingMove")
        if healing != ["TRUE"] and not (heal_blocking_latest and healing == ["B_HEAL_BLOCKING >= GEN_6"]):
            continue
        percentage = values("absorbPercentage")
        if len(percentage) != 1 or not percentage[0].strip().isdigit() or not 0 < int(percentage[0]) <= 100:
            continue
        result[symbol] = int(percentage[0])
    return result


def parse_earthquake_metadata(text, updated_move_flags_latest=False):
    """Frozen two-move contract. Any changed or unresolved operand removes admission."""
    result = {}
    _, _, sheer, unknown = parse_contact_and_sheer_force(text)
    for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())):
        if symbol not in ("MOVE_EARTHQUAKE", "MOVE_BULLDOZE"):
            continue
        joined = "\n".join(body)
        def values(field):
            return re.findall(rf"\.{field}\s*=\s*([^,\n}}]+)", joined)
        effect, _, target, _, _, priority = classify_body(body, include_immunity_metadata=True)
        if (effect != "EFFECT_EARTHQUAKE" or target != "TARGET_FOES_AND_ALLY" or priority != 0
                or any(line.lstrip().startswith("#") for line in body)
                or values("power") != (["100"] if symbol == "MOVE_EARTHQUAKE" else ["60"])
                or values("type") != ["TYPE_GROUND"] or values("category") != ["DAMAGE_CATEGORY_PHYSICAL"]
                or values("strikeCount") not in ([], ["1"])):
            continue
        if any(values(flag) for flag in (*[f for f in STATE_DEPENDENT_FLAGS if f != "damagesUnderground"],
                "multiHit", "explosion", "ignoresTargetAbility", "gravityBanned", "thawsUser",
                "cantUseTwice", "noAffectOnSameTypeTarget", "ignoresSubstitute", "dampBanned")):
            continue
        if any(values(flag) not in ([], ["FALSE"]) for flag in
               (*ABILITY_MOVE_FLAGS, *CONTACT_FLAGS, *IMMUNITY_FLAGS)):
            continue
        # Freeze all initializer fields: an added damage/execution flag cannot hide in metadata.
        allowed = {"name", "description", "effect", "power", "type", "accuracy", "pp", "target",
                   "priority", "category", "ignoresKingsRock", "damagesUnderground", "skyBattleBanned",
                   "contestEffect", "contestCategory", "contestComboStarterId", "contestComboMoves",
                   "battleAnimScript", "validApprenticeMove", "additionalEffects", "moveEffect", "chance"}
        if set(re.findall(r"\.([A-Za-z]\w*)\s*=", joined)) - allowed:
            continue
        underground = values("damagesUnderground")
        if symbol == "MOVE_EARTHQUAKE":
            if not updated_move_flags_latest or underground != ["B_UPDATED_MOVE_FLAGS >= GEN_2"]:
                continue
        elif underground:
            continue
        if symbol == "MOVE_BULLDOZE":
            if values("moveEffect") != ["MOVE_EFFECT_SPD_MINUS_1"] or values("chance") != ["100"]:
                continue
        elif values("additionalEffects"):
            continue
        if symbol in unknown or sheer.get(symbol) != (symbol == "MOVE_BULLDOZE"):
            continue
        result[symbol] = symbol == "MOVE_EARTHQUAKE"
    return result


def parse_explosion_metadata(text, updated_move_data_latest=False, modern_defense=False):
    """Only the reviewed two-move contract; unknown/new initializers cannot authorize."""
    result = {}
    allowed = {"name", "description", "effect", "power", "type", "accuracy", "pp", "target",
               "priority", "category", "explosion", "parentalBondBanned", "dampBanned",
               "contestEffect", "contestCategory", "contestComboStarterId", "contestComboMoves",
               "battleAnimScript", "validApprenticeMove"}
    if not updated_move_data_latest or not modern_defense:
        return result
    for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())):
        power = {"MOVE_SELF_DESTRUCT": 200, "MOVE_EXPLOSION": 250}.get(symbol)
        if power is None:
            continue
        joined = "\n".join(body)
        expected = {"effect": "EFFECT_HIT", "power": f"B_UPDATED_MOVE_DATA >= GEN_2 ? {power} : {130 if power == 200 else 170}",
                    "type": "TYPE_NORMAL", "category": "DAMAGE_CATEGORY_PHYSICAL",
                    "target": "TARGET_FOES_AND_ALLY", "priority": "0", "explosion": "TRUE",
                    "parentalBondBanned": "TRUE", "dampBanned": "TRUE"}
        if (any(line.lstrip().startswith("#") for line in body)
                or set(re.findall(r"\.([A-Za-z]\w*)\s*=", joined)) - allowed
                or any(re.findall(rf"\.{field}\s*=\s*([^,\n}}]+)", joined) != [value]
                       for field, value in expected.items())):
            continue
        result[symbol] = power
    return result


def parse_underwater_metadata(text, updated_move_data_latest=False, updated_move_flags_latest=False):
    """Freeze just Surf/Whirlpool, including omitted additional-effect fields (zero)."""
    if not updated_move_data_latest or not updated_move_flags_latest:
        return {}
    result = {}
    _, _, sheer, unknown = parse_contact_and_sheer_force(text)
    cosmetic = {"name", "description", "pp", "contestEffect", "contestCategory",
                "contestComboStarterId", "contestComboMoves", "battleAnimScript", "validApprenticeMove"}
    for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())):
        if symbol not in ("MOVE_SURF", "MOVE_WHIRLPOOL"):
            continue
        surf = symbol == "MOVE_SURF"
        joined = "\n".join(body)
        # Surf's only preprocessor branch is display text; mechanics must remain unconditional.
        mechanics = joined[joined.index(".effect"):]
        expected = {"effect": "EFFECT_HIT", "power": "B_UPDATED_MOVE_DATA >= GEN_6 ? 90 : 95" if surf else "B_UPDATED_MOVE_DATA >= GEN_5 ? 35 : 15",
            "type": "TYPE_WATER", "category": "DAMAGE_CATEGORY_SPECIAL", "priority": "0",
            "target": "B_UPDATED_MOVE_DATA >= GEN_4 ? TARGET_FOES_AND_ALLY : TARGET_BOTH" if surf else "TARGET_SELECTED",
            "accuracy": "100" if surf else "B_UPDATED_MOVE_DATA >= GEN_5 ? 85 : 70", "damagesUnderwater": "TRUE"}
        if surf:
            expected["skyBattleBanned"] = "TRUE"
        else:
            expected.update(ignoresKingsRock="B_UPDATED_MOVE_FLAGS < GEN_3", moveEffect="MOVE_EFFECT_WRAP")
        allowed = cosmetic | set(expected) | ({"additionalEffects", "wrapped"} if not surf else set())
        if ("#" in mechanics or set(re.findall(r"\.([A-Za-z]\w*)\s*=", joined)) - allowed
                or any(re.findall(rf"\.{field}\s*=\s*([^,\n}}]+)", joined) != [value]
                       for field, value in expected.items())
                or symbol in unknown or sheer.get(symbol) is not False):
            continue
        if not surf:
            effects = re.findall(r"\.additionalEffects\s*=\s*ADDITIONAL_EFFECTS\((.*?)\),", joined, re.S)
            if len(effects) != 1 or re.sub(r"\s+", "", effects[0]) != "{.moveEffect=MOVE_EFFECT_WRAP,.multistring.wrapped=B_MSG_WRAPPED_WHIRLPOOL,}":
                continue
        result[symbol] = 90 if surf else 35
    return result


STATUS_DOUBLE_CONTRACTS = {'MOVE_SMELLING_SALTS': '8bfd0435a17c8f759473f9c3ce128ecd3cf981ae42f5c418b65f2a76eea589cc', 'MOVE_WAKE_UP_SLAP': '0cd8b5f0b6063b5bc4e8734e0acc644b0f92431c29db647797cf9961462120df', 'MOVE_VENOSHOCK': '10bb92d124dbe95f2e0e39bc77a006372df26280736b458be872a4496392eeef', 'MOVE_HEX': '2eb4796da7268a67bca1c121f5828f9604d9ebd30886de5196a2f7315f71e6d4', 'MOVE_BARB_BARRAGE': '2451ca20b933caecbd0ea6f7ca5eca9534a682b85ac77226c8825a59d021858a', 'MOVE_INFERNAL_PARADE': 'a401a3a1c9c33f2a0394df709b2786a44271aef8577b01f798a983bc76a15561'}

# These are complete source MoveInfo records, whitespace-normalized, not merely move names.
# The separate family marker is intentionally narrower than the shared EFFECT_SEMI_INVULNERABLE.
SEMI_INVULNERABLE_PREVIEW_CONTRACTS = {
    "MOVE_FLY": "6f3bcf41418beeac4dd3e154f7149304a26fdf2b4b79564fc17bd7d94b43b5e6",
    "MOVE_DIG": "7f7a5440045542acee2123f4fa324db10d3b5d2d2e9e4dc6d39f1b9f77da59fa",
    "MOVE_DIVE": "6ea24fae80f857b432743632e9bd0da385f0a88555d6390833bdd3b42c5e2d0d",
    "MOVE_BOUNCE": "e3bf76e47df433ac3419472b73c3ac98a4650396ab6d2ed9fa6cf6b76cf61003",
    "MOVE_PHANTOM_FORCE": "3cd3800475fae9367111d7fe02df58e7c4d04009453730a3e2bf8df1dcd96f81",
}
SEMI_INVULNERABLE_PHASE_CONTRACTS = {
    "CanTwoTurnMoveFireThisTurn": "dd7e6c7cc6a3fc251abe81c764e047a35275f721ff9f5af0731e4fdd1deb2e2b",
    "CancelerCharging": "21f17e12a9d37e46ae7782d29cd08306a80863d4c6f8f6944bfd802498300410",
    "Cmd_setsemiinvulnerablebit": "6cd28e71af4d5303a0ed9357bd68793cb4401eafdff51224157c1e3a61f7a7ae",
    "IsGravityPreventingMove": "d0e78fa7cb02fef0b3a9a37ee0c963fde291f4cb03c984fec45771ce2d7ec7ea",
    "MoveIsAffectedBySheerForce": "77936ec8e3ded496da3987a351b250ff89a67a92af1625952513124b6636e31f",
    "CalcMoveBasePowerAfterModifiers": "ecb21db144bb0b58f76cb5b48519e75a6bcbbc839f277531d921d1ecded5a3d5",
    "MoveEndSheerForce": "e56dae7d0f6cc343c72c3464c9cacd955ea0f19e490c3b0b0dde1a8a989cd715",
    "MoveEndLifeOrbShellBell": "0f9b31345756110877bdc35fd435e4fa2725b9962f1eaf22fea87297a75b36a9",
    "MoveIgnoresProtect": "8e435300cee630427f03c544a99669cb4d24bb3e0af8a94d49d988f7c7ebb7e3",
    "IsMoveGravityBanned": "7c5d0be7e2999c1b4e57da91b2bb1484d7128a47db7e2844179d40efb26e613b",
    "BattleScript_TwoTurnMoveCharging": "804eab61efcbc7aba911064965274eb33b033c44d2fd153939f2d1648a89480d",
    "BattleScript_PowerHerbActivation": "20d5f14fdf770adc0113543b950ff27e0eac6810f14667b3e5bf8ec9a16b5c15",
    "BattleScript_TwoTurnMovesSecondTurnRet": "9e5ecb37a59e34b0fdc3292a892f5e6801358cce0a5c7dcb911b1ec210a27ec7",
    "BattleScript_EffectHit": "3257f2edc8cbb458a3ab6d2e479c7276b2774a1e3bf2c46451458de9437d0b7d",
    "BattleScript_HitFromAccCheck": "17e15e2b276321c4876c370c1438efce62c043e36dec722c337716e49e71084b",
    "BattleScript_HitFromDamageCalc": "a1220ba6ca28fa1fe69fdd5027f23aa4e115b7652b31215564451171d90fdb72",
    "BattleScript_Hit_RetFromAtkAnimation": "37ec35e091afb4d086ff74db5b287c95c05f0203325f0941ea0a2ef5cba856c8",
}

def _extract_c_function(source, name):
    match = re.search(r"\b" + re.escape(name) + r"\s*\([^;{}]*\)\s*\{", source, re.S)
    if not match:
        raise ValueError("Missing phase function " + name)
    start = source.index("{", match.start())
    depth = 0
    for index in range(start, len(source)):
        if source[index] == "{": depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0: return source[match.start():index + 1]
    raise ValueError("Unclosed phase function " + name)

def verify_semi_invulnerable_phase_contract(upstream_dir):
    resolution = Path(upstream_dir, "src/battle_move_resolution.c").read_text(encoding="utf-8")
    commands = Path(upstream_dir, "src/battle_script_commands.c").read_text(encoding="utf-8")
    battle_util = Path(upstream_dir, "src/battle_util.c").read_text(encoding="utf-8")
    move_header = Path(upstream_dir, "include/move.h").read_text(encoding="utf-8")
    scripts = Path(upstream_dir, "data/battle_scripts_1.s").read_text(encoding="utf-8")
    actual = {name: _extract_c_function(resolution, name) for name in ("CanTwoTurnMoveFireThisTurn", "CancelerCharging")}
    actual["Cmd_setsemiinvulnerablebit"] = _extract_c_function(commands, "Cmd_setsemiinvulnerablebit")
    actual["IsGravityPreventingMove"] = _extract_c_function(battle_util, "IsGravityPreventingMove")
    actual["MoveIsAffectedBySheerForce"] = _extract_c_function(battle_util, "MoveIsAffectedBySheerForce")
    actual["CalcMoveBasePowerAfterModifiers"] = _extract_c_function(battle_util, "CalcMoveBasePowerAfterModifiers")
    actual["MoveEndSheerForce"] = _extract_c_function(resolution, "MoveEndSheerForce")
    actual["MoveEndLifeOrbShellBell"] = _extract_c_function(resolution, "MoveEndLifeOrbShellBell")
    actual["MoveIgnoresProtect"] = _extract_c_function(move_header, "MoveIgnoresProtect")
    actual["IsMoveGravityBanned"] = _extract_c_function(move_header, "IsMoveGravityBanned")
    for name in ("BattleScript_PowerHerbActivation", "BattleScript_TwoTurnMoveCharging", "BattleScript_TwoTurnMovesSecondTurnRet",
                 "BattleScript_EffectHit", "BattleScript_HitFromAccCheck", "BattleScript_HitFromDamageCalc",
                 "BattleScript_Hit_RetFromAtkAnimation"):
        match = re.search(r"^" + name + r"::?$", scripts, re.M)
        if not match: raise ValueError("Missing phase script " + name)
        following = re.search(r"^BattleScript_\w+::?$", scripts[match.end():], re.M)
        actual[name] = scripts[match.start():match.end() + following.start()]
    for name, expected in SEMI_INVULNERABLE_PHASE_CONTRACTS.items():
        digest = hashlib.sha256(re.sub(r"\s+", "", actual[name]).encode()).hexdigest()
        if digest != expected: raise ValueError("Changed semi-invulnerable phase/damage timing: " + name)

def parse_semi_invulnerable_preview_metadata(text, ids, config, contact_by_id, sheer_by_id,
                                            ability_flags_by_id, flags_by_id):
    """Freeze the five reviewed damaging-turn preview descriptors, resolving latest-gen data."""
    for macro in ("B_UPDATED_MOVE_DATA", "B_UPDATED_MOVE_FLAGS", "B_PHYSICAL_SPECIAL_SPLIT"):
        if not re.search(rf"^#define {macro}\s+GEN_LATEST\b", config, re.M):
            raise ValueError(f"Semi-invulnerable preview requires {macro}=GEN_LATEST")
    entries = dict(_entry_body(text.splitlines(), 0, len(text.splitlines())))
    result = {}
    expected = {
        "MOVE_FLY": (19, 90, "TYPE_FLYING", "STATE_ON_AIR", 95, 15, True, True, False, []),
        "MOVE_DIG": (91, 80, "TYPE_GROUND", "STATE_UNDERGROUND", 100, 10, True, False, False, []),
        "MOVE_DIVE": (291, 80, "TYPE_WATER", "STATE_UNDERWATER", 100, 10, True, False, False, []),
        "MOVE_BOUNCE": (340, 85, "TYPE_FLYING", "STATE_ON_AIR", 85, 5, True, True, False,
                        [dict(moveEffect="MOVE_EFFECT_PARALYSIS", chance=30, sheerForceOverride=False, preAttackEffect=False)]),
        "MOVE_PHANTOM_FORCE": (566, 90, "TYPE_GHOST", "STATE_PHANTOM_FORCE", 100, 10, True, False, True,
                               [dict(moveEffect="MOVE_EFFECT_FEINT", chance=0, sheerForceOverride=False, preAttackEffect=False)]),
    }
    for symbol, digest in SEMI_INVULNERABLE_PREVIEW_CONTRACTS.items():
        if symbol not in entries:
            raise ValueError("Missing semi-invulnerable preview move " + symbol)
        body = "\n".join(entries[symbol])
        if hashlib.sha256(re.sub(r"\s+", "", body).encode()).hexdigest() != digest:
            raise ValueError("Changed pinned semi-invulnerable MoveInfo: " + symbol)
        move_id, power, move_type, state, accuracy, pp, contact, gravity, ignores_protect, effects = expected[symbol]
        if ids.get(symbol) != move_id:
            raise ValueError("Changed semi-invulnerable preview move ID: " + symbol)
        if move_id not in contact_by_id or ("makesContact" in contact_by_id[move_id]) != contact:
            raise ValueError("Changed source contact authority for semi-invulnerable preview: " + symbol)
        if sheer_by_id.get(move_id) is not (symbol == "MOVE_BOUNCE"):
            raise ValueError("Changed source Sheer Force eligibility for semi-invulnerable preview: " + symbol)
        move_flags = flags_by_id.get(move_id, set())
        result[move_id] = dict(semiInvulnerablePreview=True, name=symbol[5:].replace("_", " ").title(),
            family="FIXED_SINGLE_HIT_SEMI_INVULNERABLE_PREVIEW", power=power, type=move_type,
            category="DAMAGE_CATEGORY_PHYSICAL", accuracy=accuracy, pp=pp,
            target="TARGET_SELECTED", priority=0, strikeCount=1, multiHit=False,
            makesContact="makesContact" in contact_by_id[move_id], punchingMove=False,
            ballisticMove="ballisticMove" in move_flags,
            gravityBanned=gravity, preparationState=state,
            sheerForceAffected=sheer_by_id.get(move_id), descriptorSha256=digest,
            ignoresProtect=ignores_protect,
            minimizeDoubleDamage=(symbol == "MOVE_PHANTOM_FORCE" and False),
            abilityFlags=sorted(ability_flags_by_id.get(move_id, set())),
            immunityFlags=sorted(move_flags - {"ballisticMove"}),
            additionalEffects=effects)
    return result

# Belch (EFFECT_BELCH, move 562). The MoveInfo digest covers the complete whitespace-normalized
# descriptor, including the selection-restriction flags. The phase digests freeze the source
# predicate, the move-limitation check that consumes it, the selection gate, and every writer of
# PartyState.ateBerry. The pinned source names the limitation check CheckMoveLimitations.
BELCH_CONTRACTS = {"MOVE_BELCH": "475e0fe0ad728f96ecba7baea8aefa98c733d8d173708cd1b39dfdb9ce03baae"}
BELCH_PHASE_CONTRACTS = {
    "IsBelchPreventingMove": "51176d8dc8bdd6cd69200dbc3ff20d416eea94b1ed55936d347dd06109857c51",
    "CheckMoveLimitations": "81eb2167eab5d185fcd6c4290bb1f75081fa34a5f346995b93d9073adeb2b2ef",
    "TrySetCantSelectMoveBattleScript": "e2eb351f117e3916009b200f40a43ec58f4b63d34717a70f82e11d4c4ff2a5e5",
    "BS_ConsumeBerry": "1ffe4201c29ea747016ce50ff3d110010d46928ac25938257427b3dd4e4b6e93",
    "TryActivateWeaknessBerry": "c48e002b1027e83780d58c27ce2825e6dfee667122704ad81e88c04eea66174a",
    "ItemBattleEffects": "a2c8176d1c1a03de2910784a717af4625d6c837e5e4f0803c725367f65127b1d",
}
# Exactly these battle sources write PartyState.ateBerry = TRUE, with these counts.
BELCH_BERRY_WRITERS = {"battle_hold_effects.c": 1, "battle_script_commands.c": 2}

def verify_belch_source_contract(upstream_dir):
    util = Path(upstream_dir, "src/battle_util.c").read_text(encoding="utf-8")
    commands = Path(upstream_dir, "src/battle_script_commands.c").read_text(encoding="utf-8")
    hold = Path(upstream_dir, "src/battle_hold_effects.c").read_text(encoding="utf-8")
    actual = {name: _extract_c_function(util, name) for name in
              ("IsBelchPreventingMove", "CheckMoveLimitations", "TrySetCantSelectMoveBattleScript")}
    actual["BS_ConsumeBerry"] = _extract_c_function(commands, "BS_ConsumeBerry")
    actual["TryActivateWeaknessBerry"] = _extract_c_function(commands, "TryActivateWeaknessBerry")
    actual["ItemBattleEffects"] = _extract_c_function(hold, "ItemBattleEffects")
    for name, expected in BELCH_PHASE_CONTRACTS.items():
        if hashlib.sha256(re.sub(r"\s+", "", actual[name]).encode()).hexdigest() != expected:
            raise ValueError("Changed Belch source authority: " + name)
    writers = {}
    for path in sorted(Path(upstream_dir, "src").glob("battle*.c")):
        count = len(re.findall(r"ateBerry\s*=\s*TRUE", path.read_text(encoding="utf-8")))
        if count: writers[path.name] = count
    if writers != BELCH_BERRY_WRITERS:
        raise ValueError("Changed PartyState.ateBerry writers: " + repr(writers))

def parse_belch_metadata(text, ids, contact_by_id, sheer_by_id, ability_flags_by_id, flags_by_id):
    """Freeze the single Belch descriptor and its complete selection-restriction set."""
    entries = dict(_entry_body(text.splitlines(), 0, len(text.splitlines())))
    body = "\n".join(entries["MOVE_BELCH"])
    digest = hashlib.sha256(re.sub(r"\s+", "", body).encode()).hexdigest()
    if digest != BELCH_CONTRACTS["MOVE_BELCH"]:
        raise ValueError("Changed pinned Belch MoveInfo descriptor")
    move_id = ids["MOVE_BELCH"]
    if move_id != 562:
        raise ValueError("Changed Belch move ID")
    if ".additionalEffects" in body or ".strikeCount" in body or ".multihit" in body.lower():
        raise ValueError("Belch gained an additional effect or multi-strike descriptor")
    if move_id in contact_by_id and "makesContact" in contact_by_id[move_id]:
        raise ValueError("Belch gained a contact descriptor")
    if sheer_by_id.get(move_id):
        raise ValueError("Belch gained Sheer Force eligibility")
    selection_bans = sorted(set(re.findall(r"\.(\w+Banned)\s*=\s*TRUE", body)))
    if selection_bans != sorted(["assistBanned", "copycatBanned", "instructBanned", "meFirstBanned",
                                 "metronomeBanned", "mimicBanned", "mirrorMoveBanned", "sleepTalkBanned"]):
        raise ValueError("Changed Belch selection-restriction flags")
    expected = ("EFFECT_BELCH", 120, "TYPE_POISON", "DAMAGE_CATEGORY_SPECIAL", 90, 10, "TARGET_SELECTED", 0)
    actual = (re.search(r"\.effect\s*=\s*(\w+)", body).group(1),
              int(re.search(r"\.power\s*=\s*(\d+)", body).group(1)),
              re.search(r"\.type\s*=\s*(\w+)", body).group(1),
              re.search(r"\.category\s*=\s*(\w+)", body).group(1),
              int(re.search(r"\.accuracy\s*=\s*(\d+)", body).group(1)),
              int(re.search(r"\.pp\s*=\s*(\d+)", body).group(1)),
              re.search(r"\.target\s*=\s*(\w+)", body).group(1),
              int(re.search(r"\.priority\s*=\s*(-?\d+)", body).group(1)))
    if actual != expected:
        raise ValueError("Changed Belch descriptor fields: " + repr(actual))
    return {move_id: dict(belchEligibility=True, name="Belch", family="FIXED_SINGLE_HIT_BELCH",
        effect="EFFECT_BELCH", power=120, type="TYPE_POISON", category="DAMAGE_CATEGORY_SPECIAL",
        accuracy=90, pp=10, target="TARGET_SELECTED", priority=0, strikeCount=1, multiHit=False,
        makesContact=False, punchingMove=False, ballisticMove=False, descriptorSha256=digest,
        selectionRestrictions=selection_bans, additionalEffects=[],
        abilityFlags=sorted(ability_flags_by_id.get(move_id, set())),
        immunityFlags=sorted(flags_by_id.get(move_id, set())))}

def parse_status_constants(text):
    names = ('SLEEP', 'POISON', 'BURN', 'FREEZE', 'PARALYSIS', 'TOXIC_POISON', 'TOXIC_COUNTER', 'FROSTBITE', 'PSN_ANY', 'ANY')
    expected = (7, 8, 16, 32, 64, 128, 3840, 4096, 136, 4351)
    result = {}
    for name, value in zip(names, expected):
        match = re.search(r'^#define STATUS1_' + name + r'\s+([^\n]+)', text, re.M)
        if not match:
            raise ValueError('Missing STATUS1_' + name)
        expression = match.group(1).split('//')[0].strip()
        for symbol, operand in result.items():
            expression = re.sub(r'\bSTATUS1_' + symbol + r'\b', str(operand), expression)
        if not re.fullmatch(r'[0-9()<>| \t]+', expression) or eval(expression, {'__builtins__': {}}, {}) != value:
            raise ValueError('Changed STATUS1_' + name)
        result[name] = value
    return result


def parse_status_double_metadata(text, statuses, updated_move_data_latest=False):
    if not updated_move_data_latest:
        raise ValueError('Status-double requires GEN_LATEST move data')
    result = {}
    _, _, sheer, unknown = parse_contact_and_sheer_force(text)
    for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())):
        if symbol not in STATUS_DOUBLE_CONTRACTS:
            continue
        joined = '\n'.join(body)
        mechanic = joined
        if hashlib.sha256(re.sub(r'\s+', '', joined).encode()).hexdigest() != STATUS_DOUBLE_CONTRACTS[symbol]:
            raise ValueError('Changed status-double MoveInfo: ' + symbol)
        if symbol in unknown or sheer.get(symbol) != (symbol in ('MOVE_BARB_BARRAGE', 'MOVE_INFERNAL_PARADE')):
            raise ValueError('Changed status-double Sheer Force: ' + symbol)
        def field(name):
            return re.search(r'\.' + name + r'\s*=\s*([^,\n}]+)', mechanic).group(1).strip()
        power = field('power')
        if '?' in power:
            power = power.split('?')[1].split(':')[0].strip()
        mask_name = field('status').removeprefix('STATUS1_')
        effects = re.findall(r'\.moveEffect\s*=\s*(MOVE_EFFECT_\w+)', mechanic)
        chance = re.search(r'\.chance\s*=\s*(\d+)', mechanic)
        result[symbol] = dict(power=int(power), type=field('type'), category=field('category'),
            statusDoubleMask=statuses[mask_name], target=field('target'), priority=int(field('priority')),
            strikeCount=1, multiHit=False, makesContact=bool(re.search(r'\.makesContact\s*=\s*TRUE', mechanic)),
            punchingMove=False, sheerForceAffected=sheer[symbol], preAttackEffect=False,
            additionalEffects=[dict(moveEffect=e, chance=int(chance.group(1)) if chance else 0,
                sheerForceOverride=False, preAttackEffect=False) for e in effects], fixedSingleHitStatusDouble=True)
    if set(result) != set(STATUS_DOUBLE_CONTRACTS):
        raise ValueError('Missing frozen status-double move')
    return result




def parse_gyro_metadata(text, ids):
    if ids.get("MOVE_GYRO_BALL") != 360:
        raise ValueError("Changed Gyro Ball ID")
    bodies = ["\n".join(body) for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())) if symbol == "MOVE_GYRO_BALL"]
    if len(bodies) != 1 or hashlib.sha256(re.sub(r"\s+", "", bodies[0]).encode()).hexdigest() != "5ca2a06678bacf9d33eaf40aeee5ac260659ea3da8bf33dfefb398263dfe5bbe":
        raise ValueError("Changed Gyro Ball MoveInfo")
    return {360: dict(fixedSingleHitGyroBall=True, effect="EFFECT_GYRO_BALL", power=1,
        type="TYPE_STEEL", category="DAMAGE_CATEGORY_PHYSICAL", accuracy=100,
        target="TARGET_SELECTED", priority=0, strikeCount=1, multiHit=False,
        makesContact=True, ballisticMove=True, punchingMove=False, sheerForceAffected=False,
        additionalEffects=[], abilityFlags=[], immunityFlags=["ballisticMove"])}

ROLLOUT_MOVE_CONTRACTS = {'MOVE_ROLLOUT': '901416b9d69c812171c02b5cef475864e57914970b27928501ee9189015f0b20', 'MOVE_ICE_BALL': '98177fa478322fc4cd8717f697991d98e3a9c7f8fbacfafb156570a9de1966d1'}
ROLLOUT_SOURCE_CONTRACTS = {'src/battle_util.c': 'dfe173ebef230849e789be6b417a316c359ec6fa7d0ecae2b1a59b5885903377', 'src/battle_move_resolution.c': 'a009128b0640ae34d564ff392f19f52f040e23fe2c55837352fa6779f4783c5e', 'src/battle_main.c': '36020a55dfdd8838ccdfe50be9ea4d69e9fa479dceed41a6725cbdb81d248c93', 'src/battle_script_commands.c': '93db4b3716e64cfdde3c9b53ca0dfe931df6a5af183596f3098ac03f262676aa', 'data/battle_scripts_1.s': '7892b8db3e1de6e088a51d99865c1db137aac9c5a147900826a3fe56f9518875', 'src/data/battle_move_effects.h': '9b0c8aaef1d86dec3075b2df3ec5fe257262a31aa232e2de702480e362e1f7f5', 'include/constants/battle.h': '112fc5afcc8dd55a6954f6c701b6939f543cdde8dbb809efdc1f379d08a1ab79', 'include/constants/battle_move_effects.h': 'c735d9a568690b5df4f895f7a9d445be26f613a00e2e0f217d96aba61f8dee33'}


def verify_rollout_contract(upstream_dir):
    # Frozen full source files include cancellation, move-end, phase and reset writers.
    for path, digest in ROLLOUT_SOURCE_CONTRACTS.items():
        if hashlib.sha256(open(os.path.join(upstream_dir, path), 'rb').read()).hexdigest() != digest:
            raise ValueError("Changed Rollout source contract: " + path)


def parse_rollout_metadata(text, ids):
    entries = {symbol: "\n".join(body) for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines()))}
    _, _, sheer, unknown = parse_contact_and_sheer_force(text)
    result = {}
    for symbol, move_id in (("MOVE_ROLLOUT", 205), ("MOVE_ICE_BALL", 301)):
        body = entries.get(symbol, "")
        if ids.get(symbol) != move_id or hashlib.sha256(re.sub(r"\s+", "", body).encode()).hexdigest() != ROLLOUT_MOVE_CONTRACTS[symbol]:
            raise ValueError("Changed Rollout family MoveInfo: " + symbol)
        if symbol in unknown or sheer.get(symbol) is not False:
            raise ValueError("Unknown Rollout Sheer Force classification")
        def field(name):
            return re.search(r"\." + name + r"\s*=\s*([^,\n}]+)", body).group(1).strip()
        ballistic = bool(re.search(r"\.ballisticMove\s*=\s*TRUE", body))
        result[move_id] = dict(fixedSingleHitRollout=True, effect=field("effect"), power=int(field("power")),
            type=field("type"), category=field("category"), accuracy=int(field("accuracy")), pp=int(field("pp")),
            target=field("target"), priority=int(field("priority")), strikeCount=1, multiHit=False,
            makesContact=True, ballisticMove=ballistic, punchingMove=False, sheerForceAffected=False,
            additionalEffects=[], preAttackEffects=[], abilityFlags=[], immunityFlags=["ballisticMove"] if ballistic else [])
    return result


def parse_electro_metadata(text, ids):
    if ids.get("MOVE_ELECTRO_BALL") != 486:
        raise ValueError("Changed Electro Ball ID")
    bodies = ["\n".join(body) for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())) if symbol == "MOVE_ELECTRO_BALL"]
    if len(bodies) != 1 or hashlib.sha256(re.sub(r"\s+", "", bodies[0]).encode()).hexdigest() != "42b607263a3488d4237763dd2fb693b4d770c685302015becdc7b755e0b72814":
        raise ValueError("Changed Electro Ball MoveInfo")
    _, _, sheer, unknown = parse_contact_and_sheer_force(text)
    if "MOVE_ELECTRO_BALL" in unknown or sheer.get("MOVE_ELECTRO_BALL") is not False:
        raise ValueError("Changed Electro Ball Sheer Force")
    return {486: dict(fixedSingleHitElectroBall=True, effect="EFFECT_ELECTRO_BALL", power=1,
        type="TYPE_ELECTRIC", category="DAMAGE_CATEGORY_SPECIAL", accuracy=100, pp=10,
        target="TARGET_SELECTED", priority=0, strikeCount=1, multiHit=False,
        makesContact=False, ballisticMove=True, punchingMove=False, sheerForceAffected=False,
        additionalEffects=[], abilityFlags=[], immunityFlags=["ballisticMove"])}


def electro_power_table(text):
    table = re.search(r"static const u8 sSpeedDiffPowerTable\[\] = \{(.*?)\};", text, re.S)
    if not table or [int(n) for n in re.findall(r"\d+", table.group(1))] != [40, 60, 80, 120, 150]:
        raise ValueError("Changed Electro Ball Speed power table")
    return [int(n) for n in re.findall(r"\d+", table.group(1))]

GYRO_SPEED_HELPERS = {
    ("src/battle_main.c", "GetBattlerTotalSpeedStat"): "e362608a2b8e5d21102646ca0410bdc54a8ae34d2d8b247563b0eb58d5adb16d",
    ("src/battle_util.c", "GetBadgeBoostModifier"): "74ddab434d133b95eae047c343408caf129e52bf3beb0171c380f720043c181d",
    ("src/battle_util.c", "CalcMoveBasePower"): "7054a315e10b964585b4598db925b8f94c51a8c4aeb5e6768029c4d7a52ab8cb",
    ("src/battle_util.c", "CalcMoveBasePowerAfterModifiers"): "d8fb52f85f014a3801dfbd41a8a6c61db1c5b460ef58fdc14ebf3f81298509d4",
}

def verify_gyro_speed_contract(upstream_dir):
    for (path, name), digest in GYRO_SPEED_HELPERS.items():
        verify_underwater_helper(open(os.path.join(upstream_dir, path)).read(), name, digest)
    electro_power_table(open(os.path.join(upstream_dir, "src/battle_util.c")).read())
    pokemon = open(os.path.join(upstream_dir, "src/pokemon.c")).read()
    stages = re.search(r"const u8 gStatStageRatios\[.*?\n};", pokemon, re.S)
    if not stages or hashlib.sha256(stages.group(0).encode()).hexdigest() != "fd5f1f5fd35088585deb145deb802bfd14d30e2a69455dacc3535f6cd131fd1b":
        raise ValueError("Changed Speed stage ratios")
    config = open(os.path.join(upstream_dir, "include/config/battle.h")).read()
    for name in ("B_BADGE_BOOST", "B_PARALYSIS_SPEED"):
        if not re.search(r"#define " + name + r"\s+GEN_3\b", config):
            raise ValueError("Changed Speed config: " + name)
    battle = open(os.path.join(upstream_dir, "include/constants/battle.h")).read()
    for name, bit in (("TAILWIND", 4), ("SWAMP", 10)):
        if not re.search(r"#define SIDE_STATUS_" + name + r"\s+\(1 << " + str(bit) + r"\)", battle):
            raise ValueError("Changed Speed side status: " + name)
    constants = open(os.path.join(upstream_dir, "include/constants/pokemon.h")).read()
    for name, value in (("MIN_STAT_STAGE", 0), ("DEFAULT_STAT_STAGE", 6), ("MAX_STAT_STAGE", 12)):
        if not re.search(r"#define " + name + r"\s+" + str(value) + r"\b", constants):
            raise ValueError("Changed Speed stage domain: " + name)
    if not re.search(r"STAT_HP,\s*STAT_ATK,\s*STAT_DEF,\s*STAT_SPEED,\s*STAT_SPATK,\s*STAT_SPDEF,", constants):
        raise ValueError("Changed source Speed stat index")
    layout = open(os.path.join(upstream_dir, "include/pokemon.h")).read().split("struct BattlePokemon\n{", 1)[1].split("};", 1)[0]
    if not re.search(r"/\*0x06\*/ u16 speed;", layout):
        raise ValueError("Changed BattlePokemon Speed domain")
    items = open(os.path.join(upstream_dir, "src/data/items.h")).read()
    anklet = items.split("[ITEM_POWER_ANKLET] =", 1)[1].split("\n    },", 1)[0]
    if not re.search(r"\.holdEffect\s*=\s*HOLD_EFFECT_POWER_ITEM", anklet) or not re.search(r"\.secondaryId\s*=\s*STAT_SPEED", anklet):
        raise ValueError("Changed representative Power Item")


def verify_hit_escape_contract(upstream_dir):
    # Shared pinned files contain damage, contact, Technician and the later pivot path.
    for path in ("src/battle_util.c", "src/battle_move_resolution.c", "src/battle_script_commands.c",
                 "data/battle_scripts_1.s", "src/data/battle_move_effects.h",
                 "include/constants/battle_move_effects.h"):
        if hashlib.sha256(open(os.path.join(upstream_dir, path), "rb").read()).hexdigest() != ROLLOUT_SOURCE_CONTRACTS[path]:
            raise ValueError("Changed hit-escape selected-hit/pivot contract: " + path)


def parse_hit_escape_metadata(text, ids):
    """Only the three reviewed fixed hits; switching is a later move-end effect."""
    entries = {symbol: "\n".join(body) for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines()))}
    contracts = {'MOVE_U_TURN': '10b3918ebc129c62ef1c0638f7712d83b22926b3fcbac7e4cae54f18a2bd04b0', 'MOVE_VOLT_SWITCH': '8509eab2cc078ec09ff1edb0cbbc264bc72b79d986912a1113a2f6ecd322def1', 'MOVE_FLIP_TURN': '127a89e448b5df34beeb1246b6c7a90b6f930d2e8e4503e68037fa013ea776a2'}
    _, _, sheer, unknown = parse_contact_and_sheer_force(text)
    result = {}
    for symbol, move_id in (("MOVE_U_TURN",369), ("MOVE_VOLT_SWITCH",521), ("MOVE_FLIP_TURN",740)):
        body = entries.get(symbol, "")
        if ids.get(symbol) != move_id or hashlib.sha256(re.sub(r"\s+", "", body).encode()).hexdigest() != contracts[symbol]:
            raise ValueError("Changed hit-escape MoveInfo: " + symbol)
        if symbol in unknown or sheer.get(symbol) is not False:
            raise ValueError("Changed hit-escape Sheer Force: " + symbol)
        def field(name):
            return re.search(r"\." + name + r"\s*=\s*([^,\n}]+)", body).group(1).strip()
        result[move_id] = dict(fixedSingleHitEscape=True, effect=field("effect"), power=int(field("power")),
            type=field("type"), category=field("category"), accuracy=int(field("accuracy")), pp=int(field("pp")),
            target=field("target"), priority=int(field("priority")), strikeCount=1, multiHit=False,
            makesContact=move_id != 521, punchingMove=False, sheerForceAffected=False,
            additionalEffects=[], preAttackEffects=[], abilityFlags=[], immunityFlags=[])
    return result

REPEATED_STRIKE_STABLE_ABILITIES = ['None', 'Damp', 'Insomnia', 'Technician', 'Tough Claws', 'Fluffy', 'Long Reach', 'Skill Link', 'Marvel Scale', 'Overgrow', 'Blaze', 'Torrent', 'Swarm', 'Defeatist', 'Adaptability', 'Normalize', 'Refrigerate', 'Pixilate', 'Aerilate', 'Galvanize', 'Liquid Voice', 'Levitate', 'Wonder Guard', 'Filter', 'Solid Rock', 'Ice Scales', 'Fur Coat', 'Heatproof', 'Water Bubble', 'Sheer Force']
REPEATED_STRIKE_STABLE_HOLD_EFFECTS = ['HOLD_EFFECT_NONE', 'HOLD_EFFECT_LIFE_ORB', 'HOLD_EFFECT_SHELL_BELL', 'HOLD_EFFECT_CHOICE_BAND', 'HOLD_EFFECT_CHOICE_SPECS', 'HOLD_EFFECT_CHOICE_SCARF', 'HOLD_EFFECT_EXPERT_BELT', 'HOLD_EFFECT_MUSCLE_BAND', 'HOLD_EFFECT_WISE_GLASSES', 'HOLD_EFFECT_TYPE_POWER', 'HOLD_EFFECT_EVIOLITE', 'HOLD_EFFECT_ASSAULT_VEST', 'HOLD_EFFECT_LOADED_DICE', 'HOLD_EFFECT_PROTECTIVE_PADS', 'HOLD_EFFECT_IRON_BALL', 'HOLD_EFFECT_PUNCHING_GLOVE', 'HOLD_EFFECT_ABILITY_SHIELD']

FIXED_TWO_MOVE_CONTRACTS = {'MOVE_DOUBLE_KICK': (24, '625afd319e60a891ea88fbb65f34b825719fa60303b724182228a460fd45a01b'), 'MOVE_BONEMERANG': (155, '93bd69719b1636c336a0a4e9c1e4a2bd31d0fa4e5cd657b5ee298f7f6aada387'), 'MOVE_DOUBLE_HIT': (458, '621d0bc7f46f32601aaca79cb980fa102e07d4edb537ce07a65a6042ffb9e868'), 'MOVE_DUAL_CHOP': (530, '305c7a8c8742479a3e2a98fc7383bdde5c2e1e61f39f4feb4acb7524080adbd6'), 'MOVE_DUAL_WINGBEAT': (742, 'a0663cc82cf78b1b92430cabb6622c62ab783f084b99750968a47c2153bfef02'), 'MOVE_TWIN_BEAM': (814, 'ce79b7466407cbe2bfc92dff62f4992f4f5b63cdd34023dddb265ea2d58cbb32')}

FIXED_TWO_SOURCE_CONTRACTS = {'include/move.h': '015bcf307ec360ce82b0c999647522f2572042261fdecbd83b701ac339debf00', 'src/battle_hold_effects.c': 'db60d458f9de2a30cadbc1e311f0814a6c5439ef8d828bdaa5518ca9b0795ebe', 'src/pokemon.c': '87450aec502e906a3c2ccc5d4051ffc05796c09de0ef6b3b785b55d882721e62', 'src/data/pokemon/form_change_tables.h': 'c28ff4c8195421e09262d78b134b0ae030397b0899116537daf736849431c0d8'}

VARIABLE_MULTI_HIT_MOVE_CONTRACTS = {
    'MOVE_DOUBLE_SLAP': (3, '37f1f7ef3c2e33fc86f1daad1abec9e425fedd32778ddc0d42cfc64d7b61cb8e'),
    'MOVE_COMET_PUNCH': (4, '45efa36822888907fdfa562b16087aba3ce4ef400673b2672eefbb8def020731'),
    'MOVE_FURY_ATTACK': (31, '060f699afe5322f95999041a1d82251c12e49403fa8d6ee8f6b45dbc674f8397'),
    'MOVE_PIN_MISSILE': (42, '0a46abdbc00ec2ae9cdd555cf517840a5abdd71ead66e10c744d33434b8c1acd'),
    'MOVE_SPIKE_CANNON': (131, 'ba1b14d4b373733088b525325148a5c737cf8a8be648fcb8f9bce4f03e206ba9'),
    'MOVE_FURY_SWIPES': (154, 'e772a1809fb7e5b77bfcffda2d4808b7e6a8ede6b6be1971edf93b3fd4a8216d'),
    'MOVE_BONE_RUSH': (198, '68e17169bfe0bc96e47836b9db35fa5ca2480f584a531727686e8250b3ba3100'),
    'MOVE_ARM_THRUST': (292, '68908eecef1cf68a19f1fbe07bb0fa762e19b1540bfc832b818273ffa3ac7d29'),
    'MOVE_BULLET_SEED': (331, '403f0cc0df44c4994a8cb81039fafc9e33873179491a594df5b9185964000cc9'),
    'MOVE_ICICLE_SPEAR': (333, 'a229be72d4e28c36a1d0f765137ee3f31e9158a2019f49067847e06a6c0a832f'),
    'MOVE_ROCK_BLAST': (350, '5c4665229897c6efcf12894630b467e0a126e8a61e8910a0154a941ae3973e18'),
    'MOVE_TAIL_SLAP': (541, 'd9d37df2f2f4c52f7148cdf3f1745d309c9236dcde23779d2cd7c8b2649f5394'),
}

# Scale Shot remains a distinct family: its only additional effect is source-proven to run
# after MoveEndMultihitMove exits the repeated damage loop.
SCALE_SHOT_MOVE_CONTRACT = (727, 'cf06a2be103ecbb1f1ba4e1a47d48b8f06d45e37844514d65b7e361ee7d0f42a')
SCALE_SHOT_SCRIPT_HASHES = {
    'BattleScript_ScaleShot': '1854ecbd952ecd549cb27fc36b103a2217d74d1341b939bcd87456fc785b6d2e',
    'BattleScript_DefDownSpeedUp': '7d05afc899772eefedc185f07a448018b46d5ed1ea31bccaee946654c23715dc',
}
SCALE_SHOT_TIMING_CONTRACT_SHA256 = '2fb16df90c8ce3fcc8f441154bd00493485405db9668226ef639c483497c7f39'

def parse_scale_shot_metadata(text, ids, contact_by_id, sheer_by_id, ability_flags_by_id):
    symbol, (move_id, digest) = 'MOVE_SCALE_SHOT', SCALE_SHOT_MOVE_CONTRACT
    entries = {s: '\n'.join(body) for s, body in _entry_body(text.splitlines(), 0, len(text.splitlines()))}
    body = entries.get(symbol, '')
    if ids.get(symbol) != move_id or hashlib.sha256(re.sub(r'\s+', '', body).encode()).hexdigest() != digest:
        raise ValueError('Changed Scale Shot MoveInfo descriptor')
    def field(name):
        values = re.findall(r'\.' + re.escape(name) + r'\s*=\s*([^,\n}]+)', body)
        if len(values) != 1:
            raise ValueError('Missing/duplicate Scale Shot field: ' + name)
        return values[0].strip()
    expected = {'effect':'EFFECT_HIT','power':'25','type':'TYPE_DRAGON','accuracy':'90','pp':'20',
        'target':'TARGET_SELECTED','priority':'0','category':'DAMAGE_CATEGORY_PHYSICAL','multiHit':'TRUE'}
    if any(field(name) != value for name, value in expected.items()) or re.search(r'\.strikeCount\s*=', body):
        raise ValueError('Scale Shot is no longer the reviewed random 2-5 move')
    effect_body = re.search(r'\.additionalEffects\s*=\s*ADDITIONAL_EFFECTS\(\s*\{(.*?)\}\s*\)', body, re.S)
    if not effect_body or re.findall(r'\.moveEffect\s*=\s*([^,\n}]+)', effect_body.group(1)) != ['MOVE_EFFECT_SCALE_SHOT']:
        raise ValueError('Scale Shot additional effect changed')
    if re.search(r'\.preAttackEffect\s*=', body) or 'MOVE_EFFECT_SCALE_SHOT' not in body:
        raise ValueError('Scale Shot pre-attack/additional-effect boundary changed')
    if move_id in ability_flags_by_id and ability_flags_by_id[move_id] or sheer_by_id.get(move_id) is not False:
        raise ValueError('Scale Shot punching/Sheer Force authority changed')
    if 'makesContact' in contact_by_id.get(move_id, set()):
        raise ValueError('Scale Shot contact authority changed')
    return {move_id: dict(scaleShot=True, name='Scale Shot', family='VARIABLE_MULTI_HIT_SCALE_SHOT',
        descriptorSha256=digest, effect='EFFECT_HIT', power=25, type='TYPE_DRAGON',
        category='DAMAGE_CATEGORY_PHYSICAL', accuracy=90, pp=20, target='TARGET_SELECTED', priority=0,
        strikeCount=None, multiHit=True, makesContact=False, punchingMove=False, ballisticMove=False,
        sheerForceAffected=False, additionalEffects=[{'moveEffect':'MOVE_EFFECT_SCALE_SHOT'}],
        preAttackEffects=[], abilityFlags=[], immunityFlags=[],
        postSequenceTimingSha256=SCALE_SHOT_TIMING_CONTRACT_SHA256)}

def verify_scale_shot_script_contract(upstream_dir):
    scripts = open(os.path.join(upstream_dir, 'data/battle_scripts_1.s'), encoding='utf-8').read()
    verify_scale_shot_script_contract_text(scripts)

def verify_scale_shot_script_contract_text(scripts):
    for name, digest in SCALE_SHOT_SCRIPT_HASHES.items():
        start = scripts.find(name + '::')
        if start < 0:
            raise ValueError('Missing Scale Shot completion script: ' + name)
        if name == 'BattleScript_ScaleShot':
            end = scripts.find('\n\n', start)
            block = scripts[start:end + 2]
        else:
            end = scripts.find('BattleScript_DefDownSpeedUpRet::\n\treturn', start)
            if end < 0: raise ValueError('Missing Scale Shot stat script return')
            block = scripts[start:end + len('BattleScript_DefDownSpeedUpRet::\n\treturn\n')]
        if hashlib.sha256(block.encode()).hexdigest() != digest:
            raise ValueError('Scale Shot completion timing/stat script changed: ' + name)
    scale = scripts[scripts.index('BattleScript_ScaleShot::'):scripts.index('\n\n', scripts.index('BattleScript_ScaleShot::'))]
    if scale != 'BattleScript_ScaleShot::\n\tcall BattleScript_MultiHitPrintStrings\n\tgoto BattleScript_DefDownSpeedUp':
        raise ValueError('Scale Shot must print hit count before its post-sequence stat script')

VARIABLE_MULTI_HIT_SOURCE_CONTRACTS = {
    ('src/battle_move_resolution.c', 'SetRandomMultiHitCounter'): '23d8dee234194d5c3fc6ec8c378734ca72f89a084fb3eb658c2bb9026b43a721',
    ('src/battle_move_resolution.c', 'CancelerMultihitMoves'): 'f8acc7eac9753d553fe2da97540938b6a0d61ffadc30c87e7a646d88ca8d0547',
    ('src/battle_move_resolution.c', 'MoveEndMultihitMove'): 'c6c7bdaea6091968eb3f98e16236bbd72e686b74c3c51e3617ddff59760f731c',
    ('src/battle_util.c', 'GetBattlerAbilityInternal'): '128a64d0947026c079a6ff2c462962d73dc83f15bda02fed56a237ff90b32f62',
    ('src/battle_util.c', 'GetBattlerHoldEffect'): 'c7fa75c0f4c7ab73ec8ec271a890e7c28eda0b727c884384ae386fc5c5cfad6a',
    ('src/battle_util.c', 'GetBattlerHoldEffectIgnoreAbility'): 'f25fd3fcd9ec831a721d5d4756dce6176de8ca208d3358ab2b57bf7de2211136',
}

def verify_fixed_two_selection_lifecycle(text):
    """Observed MOVE_NONE precedes commitment during the permitted selection callback."""
    turn = text[text.index('void BattleTurnPassed(void)'):text.index('u8 IsRunningFromBattleImpossible(')]
    reset = turn.find('gChosenMoveByBattler[battler] = MOVE_NONE;')
    selection = turn.find('gBattleMainFunc = HandleTurnActionSelectionState;')
    commitment = 'gBattleStruct->chosenMovePositions[battler] = gBattleResources->bufferB[battler][2] & ~RET_GIMMICK;\n                            gChosenMoveByBattler[battler] = GetBattlerChosenMove(battler);'
    if not 0 <= reset < selection or commitment not in text:
        raise ValueError('Changed chosen-move reset/selection/commit lifecycle')

def verify_fixed_two_contract(upstream_dir):
    verify_rollout_contract(upstream_dir)
    verify_fixed_two_selection_lifecycle(open(os.path.join(upstream_dir, 'src/battle_main.c')).read())
    for path, digest in FIXED_TWO_SOURCE_CONTRACTS.items():
        if hashlib.sha256(open(os.path.join(upstream_dir, path), "rb").read()).hexdigest() != digest:
            raise ValueError("Changed fixed-two execution contract: " + path)


def parse_fixed_two_metadata(text, ids):
    """Freeze complete MoveInfo initializers, including omitted zero-valued flags."""
    entries = {symbol: "\n".join(body) for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines()))}
    _, _, sheer, unknown = parse_contact_and_sheer_force(text)
    result = {}
    for symbol, (move_id, digest) in FIXED_TWO_MOVE_CONTRACTS.items():
        body = entries.get(symbol, "")
        if ids.get(symbol) != move_id or hashlib.sha256(re.sub(r"\s+", "", body).encode()).hexdigest() != digest:
            raise ValueError("Changed fixed-two MoveInfo: " + symbol)
        if symbol in unknown or sheer.get(symbol) is not False:
            raise ValueError("Changed fixed-two Sheer Force: " + symbol)
        def field(name):
            return re.search(r"\." + name + r"\s*=\s*([^,\n}]+)", body).group(1).strip()
        result[move_id] = dict(fixedTwoHitPlain=True, name=symbol[5:].replace("_", " ").title(),
            effect=field("effect"), power=int(field("power")), type=field("type"), category=field("category"),
            accuracy=int(field("accuracy")), pp=int(field("pp")), target=field("target"), priority=int(field("priority")),
            strikeCount=int(field("strikeCount")), multiHit=False, makesContact=bool(re.search(r"\.makesContact\s*=\s*TRUE", body)),
            punchingMove=False, sheerForceAffected=False, additionalEffects=[], preAttackEffects=[],
            abilityFlags=[], immunityFlags=[], metronomeBanned=move_id == 814)
    return result

def _source_macro(text, name):
    values = re.findall(r'^\s*#define\s+' + re.escape(name) + r'\s+([^\s/]+)', text, re.M)
    if len(values) != 1:
        raise ValueError('Missing or duplicate source macro: ' + name)
    return values[0]

def _variable_multi_hit_generation_contract(config, general):
    expected = {
        'B_MULTI_HIT_CHANCE': 'GEN_LATEST',
        'B_UPDATED_MOVE_DATA': 'GEN_LATEST',
        'B_UPDATED_MOVE_FLAGS': 'GEN_LATEST',
    }
    for name, value in expected.items():
        if _source_macro(config, name) != value:
            raise ValueError('Variable multi-hit descriptor/count config changed: ' + name)
    if _source_macro(general, 'GEN_LATEST') != 'GEN_9':
        raise ValueError('Variable multi-hit source config is no longer Gen 9')

def _parse_updated_move_value(value, field, config, general):
    value = value.strip()
    if value.isdigit():
        return int(value)
    match = re.fullmatch(r'B_UPDATED_MOVE_DATA\s*>=\s*GEN_(\d+)\s*\?\s*(\d+)\s*:\s*(\d+)', value)
    if not match:
        raise ValueError('Unreviewed variable multi-hit ' + field + ' expression: ' + value)
    threshold, latest, older = map(int, match.groups())
    latest_gen = int(_source_macro(general, 'GEN_LATEST').removeprefix('GEN_'))
    if latest_gen < threshold:
        return older
    return latest

def _parse_variable_move_flag(body, name, config):
    values = re.findall(r'\.' + re.escape(name) + r'\s*=\s*([^,\n}]+)', body)
    if not values:
        return False
    if len(values) != 1:
        raise ValueError('Duplicate variable multi-hit move flag: ' + name)
    value = values[0].strip()
    if value in ('TRUE', 'FALSE'):
        return value == 'TRUE'
    match = re.fullmatch(r'B_UPDATED_MOVE_FLAGS\s*>=\s*GEN_(\d+)', value)
    if match:
        latest = int(_source_macro(config, 'B_UPDATED_MOVE_FLAGS').removeprefix('GEN_')) if _source_macro(config, 'B_UPDATED_MOVE_FLAGS') != 'GEN_LATEST' else 9
        return latest >= int(match.group(1))
    raise ValueError('Unreviewed variable multi-hit flag expression: ' + name + '=' + value)

def parse_variable_multi_hit_metadata(text, ids, config, general, contact_by_id,
                                      unknown_contact_by_id, sheer_by_id,
                                      unknown_sheer_by_id, ability_flags_by_id,
                                      unknown_ability_flags_by_id, immunity_flags_by_id,
                                      unknown_immunity_flags_by_id):
    """Freeze the exact plain random-count EFFECT_HIT subset and compiled descriptor."""
    _variable_multi_hit_generation_contract(config, general)
    entries = {symbol: '\n'.join(body) for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines()))}
    result = {}
    for symbol, (move_id, digest) in VARIABLE_MULTI_HIT_MOVE_CONTRACTS.items():
        body = entries.get(symbol, '')
        if ids.get(symbol) != move_id or hashlib.sha256(re.sub(r'\s+', '', body).encode()).hexdigest() != digest:
            raise ValueError('Changed variable multi-hit MoveInfo: ' + symbol)
        def field(name):
            values = re.findall(r'\.' + re.escape(name) + r'\s*=\s*([^,\n}]+)', body)
            if len(values) != 1:
                raise ValueError('Missing/duplicate variable multi-hit field ' + name + ': ' + symbol)
            return values[0].strip()
        if field('effect') != 'EFFECT_HIT' or field('multiHit') != 'TRUE' or re.search(r'\.strikeCount\s*=', body):
            raise ValueError('Variable multi-hit family boundary changed: ' + symbol)
        if field('target') != 'TARGET_SELECTED' or field('priority') != '0':
            raise ValueError('Variable multi-hit target/priority changed: ' + symbol)
        if re.search(r'\.additionalEffects\s*=|\.preAttackEffect\s*=', body):
            raise ValueError('Variable multi-hit additional/pre-attack effects changed: ' + symbol)
        if move_id in unknown_sheer_by_id or unknown_ability_flags_by_id.get(move_id):
            raise ValueError('Variable multi-hit descriptor has unknown source flags: ' + symbol)
        if unknown_immunity_flags_by_id.get(move_id, set()) - {'ballisticMove'}:
            raise ValueError('Variable multi-hit descriptor has unreviewed immunity flags: ' + symbol)
        if move_id not in sheer_by_id:
            raise ValueError('Variable multi-hit Sheer Force authority missing: ' + symbol)
        # Omitted MoveInfo.makesContact is an exact false for these pinned source
        # descriptors; a present initializer must be a single literal TRUE/FALSE.
        ability_flags = sorted(ability_flags_by_id.get(move_id, set()))
        ballistic = _parse_variable_move_flag(body, 'ballisticMove', config)
        immunity_flags = set(immunity_flags_by_id.get(move_id, set()))
        if ballistic:
            immunity_flags.add('ballisticMove')
        else:
            immunity_flags.discard('ballisticMove')
        immunity_flags = sorted(immunity_flags)
        if ('ballisticMove' in immunity_flags) != ballistic or ('punchingMove' in ability_flags) != _parse_variable_move_flag(body, 'punchingMove', config):
            raise ValueError('Variable multi-hit move flags disagree with helper metadata: ' + symbol)
        immunity_flags_by_id[move_id] = set(immunity_flags)
        unknown_immunity_flags_by_id[move_id] = unknown_immunity_flags_by_id.get(move_id, set()) - {'ballisticMove'}
        if sheer_by_id[move_id] is not False:
            raise ValueError('Variable multi-hit move unexpectedly has Sheer Force effects: ' + symbol)
        descriptor = dict(
            variableMultiHitPlain=True,
            name=symbol[5:].replace('_', ' ').title(),
            family='VARIABLE_MULTI_HIT_PLAIN',
            descriptorSha256=digest,
            effect=field('effect'),
            power=_parse_updated_move_value(field('power'), 'power', config, general),
            type=field('type'),
            category=field('category'),
            accuracy=_parse_updated_move_value(field('accuracy'), 'accuracy', config, general),
            pp=int(field('pp')),
            target=field('target'),
            priority=int(field('priority')),
            strikeCount=None,
            multiHit=True,
            makesContact='makesContact' in contact_by_id[move_id],
            punchingMove='punchingMove' in ability_flags,
            ballisticMove=ballistic,
            sheerForceAffected=sheer_by_id[move_id],
            additionalEffects=[],
            preAttackEffects=[],
            abilityFlags=ability_flags,
            immunityFlags=immunity_flags,
        )
        result[move_id] = descriptor
    if set(result) != {move_id for move_id, _ in VARIABLE_MULTI_HIT_MOVE_CONTRACTS.values()}:
        raise ValueError('Variable multi-hit descriptor set is not exact')
    return result

def _verify_function_hash(source, function_name, expected):
    # Anchor at a C definition line. An unanchored search can mistake a call in an
    # `if (...) {` condition for the helper definition and hash the wrong block.
    pattern = (r'(?m)^[ \t]*(?:(?:static|inline)[ \t]+)*'
               r'(?:enum[ \t]+\w+|[A-Za-z_]\w*(?:[ \t]*\*)?)[ \t]+'
               + re.escape(function_name) + r'[ \t]*\([^;{}]*\)\s*\{')
    matches = list(re.finditer(pattern, source))
    if len(matches) != 1:
        raise ValueError('Missing variable multi-hit source helper: ' + function_name)
    match = matches[0]
    end, depth = match.end(), 1
    while depth and end < len(source):
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    if depth or hashlib.sha256(source[match.start():end].encode()).hexdigest() != expected:
        raise ValueError('Changed variable multi-hit source helper: ' + function_name)

def verify_variable_multi_hit_source_contract(upstream_dir):
    config = open(os.path.join(upstream_dir, 'include/config/battle.h'), encoding='utf-8').read()
    general = open(os.path.join(upstream_dir, 'include/config/general.h'), encoding='utf-8').read()
    _variable_multi_hit_generation_contract(config, general)
    for (path, function), digest in VARIABLE_MULTI_HIT_SOURCE_CONTRACTS.items():
        source = open(os.path.join(upstream_dir, path), encoding='utf-8').read()
        _verify_function_hash(source, function, digest)

def parse_brine_metadata(text, ids):
    """Freeze the entire reviewed MoveInfo; omitted damage flags are source zeroes."""
    if ids.get("MOVE_BRINE") != 362:
        raise ValueError("Changed Brine ID")
    bodies = ["\n".join(body) for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())) if symbol == "MOVE_BRINE"]
    if len(bodies) != 1 or hashlib.sha256(re.sub(r"\s+", "", bodies[0]).encode()).hexdigest() != "3e98b9b2c851f7ae08057c181a1086179b68e0a09cf977c2a88d54350ccc4c2e":
        raise ValueError("Changed Brine MoveInfo")
    body = bodies[0]
    def field(name):
        return re.search(r"\." + name + r"\s*=\s*([^,\n}]+)", body).group(1).strip()
    _, _, sheer, unknown = parse_contact_and_sheer_force(text)
    if "MOVE_BRINE" in unknown or sheer.get("MOVE_BRINE") is not False:
        raise ValueError("Changed Brine Sheer Force")
    return {362: dict(fixedSingleHitBrine=True, effect=field("effect"), power=int(field("power")),
        type=field("type"), category=field("category"), accuracy=int(field("accuracy")),
        target=field("target"), priority=int(field("priority")), strikeCount=1, multiHit=False,
        makesContact=False, punchingMove=False, sheerForceAffected=False, additionalEffects=[],
        abilityFlags=[], immunityFlags=[])}

def parse_damp_bans(text):
    banned, unknown = set(), set()
    for symbol, body in _entry_body(text.splitlines(), 0, len(text.splitlines())):
        values = re.findall(r"\.dampBanned\s*=\s*([^,\n}]+)", "\n".join(body))
        if not values:
            continue
        if values not in (["TRUE"], ["FALSE"]) or any(line.lstrip().startswith("#") for line in body):
            unknown.add(symbol)
        elif values == ["TRUE"]:
            banned.add(symbol)
    return banned, unknown


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


def parse_contact_and_sheer_force(text, *, updated_move_data_latest=False):
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
        # Volt Tackle's additional effect is guarded only by this exact reviewed config.
        # Resolve it only after build_maps verifies GEN_LATEST; default parsing stays fail-closed.
        if updated_move_data_latest and symbol == "MOVE_VOLT_TACKLE" and active_condition == 1:
            prefix = text_body[:initializer.start()]
            if re.findall(r"^\s*#if\s+(.+)$", prefix, re.M)[-1] == "B_UPDATED_MOVE_DATA >= GEN_4":
                active_condition = 0
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
    # Analytic explicitly excludes EFFECT_FUTURE_SIGHT. Its production branch is
    # safe only while the ordinary surface excludes EVERY move with that effect.
    future_sight = {symbol for symbol, effect in effect_by_symbol.items()
                    if effect == "EFFECT_FUTURE_SIGHT"}
    if "MOVE_FUTURE_SIGHT" not in future_sight or future_sight & ordinary_symbols:
        raise ValueError("Analytic contract changed: Future Sight entered the ordinary surface")
    config = open(os.path.join(upstream_dir, "include/config/battle.h"), encoding="utf-8").read()
    if not re.search(r"#define B_UPDATED_MOVE_DATA\s+GEN_LATEST", config):
        raise ValueError("Fixed recoil metadata requires reviewed GEN_LATEST move data")
    contact_by_symbol, unknown_contact_by_symbol, sheer_by_symbol, unknown_sheer_by_symbol = parse_contact_and_sheer_force(
        move_table_text, updated_move_data_latest=True)

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
                    sheer_by_id, unknown_sheer_by_id, recoil_ids=(), recoil_thaws=(), drain_percentages=None, earthquake_flags=None, explosion_powers=None, damp_bans=(), unknown_damp_bans=(), underwater_powers=None, status_double=None, status_constants=None, brine=None, dynamic_power=None, speed_power_table=()):
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
    lines.append("    /** Fixed single-hit EFFECT_RECOIL; separate from the ordinary safety boundary. */")
    lines.append("    val fixedSingleHitRecoilMoveIds: Set<Int> = setOf(")
    for move_id in sorted(recoil_ids):
        lines.append(f"        {move_id},")
    lines.append("    )")
    lines.append("")
    lines.append("    /** Fixed single-hit drain, requiring authoritative Heal Block execution state. */")
    lines.append("    val fixedSingleHitDrainMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(drain_percentages or {}))) + ")")
    lines.append("    val absorbPercentageById: Map<Int, Int> = mapOf(" + ", ".join(f"{i} to {v}" for i, v in sorted((drain_percentages or {}).items())) + ")")
    lines.append("")
    lines.append("    val fixedSingleHitEarthquakeMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(earthquake_flags or {}))) + ")")
    lines.append("    val earthquakeDamagesUndergroundById: Map<Int, Boolean> = mapOf(" + ", ".join(f"{i} to {str(v).lower()}" for i, v in sorted((earthquake_flags or {}).items())) + ")")
    lines.append("    /** Singles explosion: Damp gate, HP=0 at damage, modern Defense, Parental Bond banned. */")
    lines.append("    val fixedSingleHitExplosionMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(explosion_powers or {}))) + ")")
    lines.append("    val explosionPowerById: Map<Int, Int> = mapOf(" + ", ".join(f"{i} to {v}" for i, v in sorted((explosion_powers or {}).items())) + ")")
    lines.append("    val fixedSingleHitGyroBallMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("fixedSingleHitGyroBall")))) + ")")
    lines.append("    val fixedSingleHitElectroBallMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("fixedSingleHitElectroBall")))) + ")")
    lines.append("    val fixedSingleHitEscapeMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("fixedSingleHitEscape")))) + ")")
    lines.append("    val fixedTwoHitPlainMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("fixedTwoHitPlain")))) + ")")
    lines.append("    /** Exact plain random-count EFFECT_HIT family; Scale Shot and Twineedle are excluded. */")
    lines.append("    val variableMultiHitPlainMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("variableMultiHitPlain")))) + ")")
    lines.append("    val variableMultiHitDescriptorSha256ById: Map<Int, String> = mapOf(" + ", ".join("{} to {}".format(i, json.dumps(m["descriptorSha256"])) for i, m in sorted((dynamic_power or {}).items()) if m.get("variableMultiHitPlain")) + ")")
    lines.append("    /** Dedicated post-sequence Scale Shot family; never part of the plain variable family. */")
    lines.append("    val variableMultiHitScaleShotMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("scaleShot")))) + ")")
    lines.append("    val scaleShotDescriptorSha256: String = " + json.dumps(next(m["descriptorSha256"] for m in (dynamic_power or {}).values() if m.get("scaleShot"))))
    lines.append("    val scaleShotTimingSha256: String = " + json.dumps(SCALE_SHOT_TIMING_CONTRACT_SHA256))
    lines.append("    /** Bounded damaging-turn preview family; exact IDs only, never the whole shared effect. */")
    lines.append("    val semiInvulnerablePreviewMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("semiInvulnerablePreview")))) + ")")
    lines.append("    val semiInvulnerablePreviewDescriptorSha256ById: Map<Int, String> = mapOf(" + ", ".join(f"{i} to {json.dumps(m['descriptorSha256'])}" for i, m in sorted((dynamic_power or {}).items()) if m.get("semiInvulnerablePreview")) + ")")
    lines.append("    /** Singles fixed hit whose selection is gated by party-member Berry state (PartyState.ateBerry). */")
    lines.append("    val fixedSingleHitBelchMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("belchEligibility")))) + ")")
    lines.append("    val fixedSingleHitBelchDescriptorSha256ById: Map<Int, String> = mapOf(" + ", ".join(f"{i} to {json.dumps(m['descriptorSha256'])}" for i, m in sorted((dynamic_power or {}).items()) if m.get("belchEligibility")) + ")")
    lines.append("    val fixedSingleHitRolloutMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(i for i, m in (dynamic_power or {}).items() if m.get("fixedSingleHitRollout")))) + ")")
    lines.append("    val fixedSingleHitSpeedPowerMoveIds: Set<Int> = fixedSingleHitGyroBallMoveIds + fixedSingleHitElectroBallMoveIds")
    lines.append("    val reviewedVariablePowerMoveIds: Set<Int> = fixedSingleHitSpeedPowerMoveIds")
    lines.append("    val electroBallPowerTable: List<Int> = listOf(" + ", ".join(map(str, speed_power_table)) + ")")
    lines.append("    val fixedSingleHitBrineMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(brine or {}))) + ")")
    lines.append("    val fixedSingleHitStatusDoubleMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(status_double or {}))) + ")")
    lines.append("    val statusDoublePowerMaskById: Map<Int, Int> = mapOf(" + ", ".join(f"{i} to {v['statusDoubleMask']}" for i, v in sorted((status_double or {}).items())) + ")")
    for name, value in (status_constants or {}).items():
        lines.append(f"    const val STATUS1_{name}: Int = {value}")
    lines.append("    /** Frozen Singles Surf/Whirlpool; neutral or underwater selected hit only. */")
    lines.append("    val fixedSingleHitUnderwaterMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(underwater_powers or {}))) + ")")
    lines.append("    val underwaterPowerById: Map<Int, Int> = mapOf(" + ", ".join(f"{i} to {v}" for i, v in sorted((underwater_powers or {}).items())) + ")")
    lines.append("    val dampBannedMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(damp_bans))) + ")")
    lines.append("    val unknownDampBanMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(unknown_damp_bans))) + ")")
    lines.append("    /** Recoil moves which clear Freeze/Frostbite before the selected hit. */")
    lines.append("    val recoilThawsUserMoveIds: Set<Int> = setOf(" + ", ".join(map(str, sorted(recoil_thaws))) + ")")
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
                           contact_by_id, unknown_contact_by_id, sheer_by_id, unknown_sheer_by_id, recoil_percentages=None, damage_shapes=None, drain_percentages=None, target_by_id=None, priority_by_id=None, flags_by_id=None, earthquake_flags=None, explosion_powers=None, underwater_powers=None, status_double=None, status_constants=None, brine=None, dynamic_power=None):
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
    for move_id, shape in (damage_shapes or {}).items():
        if shape:
            result[str(move_id)]["damageFlags"] = shape
    for move_id, percentage in (recoil_percentages or {}).items():
        result[str(move_id)]["recoilPercentage"] = percentage
    for move_id, percentage in (drain_percentages or {}).items():
        result[str(move_id)].update(absorbPercentage=percentage, fixedSingleHitDrain=True,
            healingMove=True, target=target_by_id[move_id], priority=priority_by_id[move_id],
            abilityFlags=sorted(ability_flags_by_id.get(move_id, set())),
            immunityFlags=sorted(flags_by_id.get(move_id, set())))
    for move_id, underground in (earthquake_flags or {}).items():
        result[str(move_id)].update(fixedSingleHitEarthquake=True, damagesUnderground=underground,
            target=target_by_id[move_id], priority=priority_by_id[move_id],
            abilityFlags=sorted(ability_flags_by_id.get(move_id, set())),
            immunityFlags=sorted(flags_by_id.get(move_id, set())))
    for move_id, power in (explosion_powers or {}).items():
        result[str(move_id)].update(fixedSingleHitExplosion=True, power=power, type="TYPE_NORMAL",
            category="DAMAGE_CATEGORY_PHYSICAL", target=target_by_id[move_id], priority=priority_by_id[move_id],
            explosion=True, dampBanned=True, parentalBondBanned=True, strikeCount=1,
            abilityFlags=[], immunityFlags=[], explosionDefense="GEN_LATEST")
    for move_id, power in (underwater_powers or {}).items():
        result[str(move_id)].update(fixedSingleHitUnderwater=True, damagesUnderwater=True,
            power=power, type="TYPE_WATER", category="DAMAGE_CATEGORY_SPECIAL",
            accuracy=100 if move_id == 57 else 85, target=target_by_id[move_id], priority=0,
            strikeCount=1, multiHit=False, abilityFlags=[], immunityFlags=[],
            skyBattleBanned=move_id == 57,
            additionalEffects=[] if move_id == 57 else [{"moveEffect":"MOVE_EFFECT_WRAP", "chance":0,
                "sheerForceOverride":False, "preAttackEffect":False, "multistring":"B_MSG_WRAPPED_WHIRLPOOL"}])
    for move_id, metadata in (dynamic_power or {}).items():
        result[str(move_id)].update(metadata)
    for move_id, metadata in (brine or {}).items():
        result[str(move_id)].update(metadata)
    for move_id, metadata in (status_double or {}).items():
        result[str(move_id)].update(metadata)
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
    move_text = open(os.path.join(upstream_dir, "src/data/moves_info.h"), encoding="utf-8").read()
    ids = parse_move_enum(open(os.path.join(upstream_dir, "include/constants/moves.h"), encoding="utf-8").read())
    recoil_ids = {ids[symbol] for symbol in parse_recoil_symbols(move_text)}
    config = open(os.path.join(upstream_dir, "include/config/battle.h"), encoding="utf-8").read()
    general = open(os.path.join(upstream_dir, "include/config/general.h"), encoding="utf-8").read()
    if not re.search(r"#define B_HEAL_BLOCKING\s+GEN_LATEST", config):
        raise ValueError("Drain healing flag requires reviewed GEN_LATEST Heal Block configuration")
    drain_percentages = {ids[s]: p for s, p in parse_drain_metadata(move_text, True, True).items()}
    if not re.search(r"#define B_UPDATED_MOVE_FLAGS\s+GEN_LATEST", config):
        raise ValueError("Earthquake underground flag requires reviewed GEN_LATEST configuration")
    earthquake_flags = {ids[s]: v for s, v in parse_earthquake_metadata(move_text, True).items()}
    if set(earthquake_flags) != {89, 523}:
        raise ValueError("Frozen Earthquake family contract changed")
    explosion_powers = {ids[s]: p for s, p in parse_explosion_metadata(move_text,
        bool(re.search(r"#define B_UPDATED_MOVE_DATA\s+GEN_LATEST", config)),
        bool(re.search(r"#define B_EXPLOSION_DEFENSE\s+GEN_LATEST", config))).items()}
    if set(explosion_powers) != {120, 153}:
        raise ValueError("Frozen Explosion family/config contract changed")
    if not re.search(r"#define B_FLAG_SKY_BATTLE\s+0\s", config):
        raise ValueError("Surf requires source-disabled Sky Battles")
    underwater_powers = {ids[s]: p for s, p in parse_underwater_metadata(move_text, True, True).items()}
    if underwater_powers != {57: 90, 250: 35}:
        raise ValueError("Frozen Surf/Whirlpool family/config contract changed")
    status_constants = parse_status_constants(open(os.path.join(upstream_dir, "include/constants/battle.h"), encoding="utf-8").read())
    status_double = {ids[s]: v for s, v in parse_status_double_metadata(move_text, status_constants,
        bool(re.search(r"#define B_UPDATED_MOVE_DATA\s+GEN_LATEST", config))).items()}
    if set(status_double) != {265,358,474,506,767,772}:
        raise ValueError("Changed status-double IDs")
    brine = parse_brine_metadata(move_text, ids)
    dynamic_power = parse_gyro_metadata(move_text, ids)
    dynamic_power.update(parse_electro_metadata(move_text, ids))
    dynamic_power.update(parse_rollout_metadata(move_text, ids))
    dynamic_power.update(parse_hit_escape_metadata(move_text, ids))
    dynamic_power.update(parse_fixed_two_metadata(move_text, ids))
    dynamic_power.update(parse_semi_invulnerable_preview_metadata(
        move_text, ids, config, contact_by_id, sheer_by_id, ability_flags_by_id, flags_by_id))
    verify_semi_invulnerable_phase_contract(upstream_dir)
    dynamic_power.update(parse_belch_metadata(move_text, ids, contact_by_id, sheer_by_id, ability_flags_by_id, flags_by_id))
    verify_belch_source_contract(upstream_dir)
    dynamic_power.update(parse_variable_multi_hit_metadata(move_text, ids, config, general,
        contact_by_id, unknown_contact_by_id, sheer_by_id, unknown_sheer_by_id,
        ability_flags_by_id, unknown_ability_flags_by_id, flags_by_id, unknown_flags_by_id))
    dynamic_power.update(parse_scale_shot_metadata(move_text, ids, contact_by_id, sheer_by_id, ability_flags_by_id))
    verify_scale_shot_script_contract(upstream_dir)
    verify_hit_escape_contract(upstream_dir)
    verify_fixed_two_contract(upstream_dir)
    verify_variable_multi_hit_source_contract(upstream_dir)
    verify_rollout_contract(upstream_dir)
    effects_header = open(os.path.join(upstream_dir, "include/constants/battle_move_effects.h")).read()
    effect_names = re.findall(r"^\s*(EFFECT_[A-Z0-9_]+)\s*,", effects_header, re.M)
    if "=" in effects_header: raise ValueError("Review changed move-effect enum assignments")
    for move_id in (205,301): dynamic_power[move_id]["effectId"] = effect_names.index("EFFECT_ROLLOUT")
    speed_power_table = electro_power_table(open(os.path.join(upstream_dir, "src/battle_util.c")).read())
    target_by_id[57] = "TARGET_FOES_AND_ALLY"
    for move_id in drain_percentages:
        flags_by_id.setdefault(move_id, set()).add("healingMove")
        unknown_flags_by_id.get(move_id, set()).discard("healingMove")
    damage_shapes = {}
    recoil_percentages = {}
    recoil_thaws = set()
    for symbol, body in _entry_body(move_text.splitlines(), 0, len(move_text.splitlines())):
        damage_shapes[ids.get(symbol)] = {
            flag: re.findall(rf"\.{flag}\s*=\s*([^,\n]+)", "\n".join(body))
            for flag in (*STATE_DEPENDENT_FLAGS, "multiHit", "strikeCount", "explosion", "thawsUser")
            if re.search(rf"\.{flag}\s*=", "\n".join(body))
        }
        if ids.get(symbol) not in recoil_ids:
            continue
        thaw = re.search(r"\.thawsUser\s*=\s*([^,\n]+)", "\n".join(body))
        if thaw:
            if thaw.group(1) not in ("TRUE", "FALSE"):
                raise ValueError("Unresolved recoil thaw flag")
            if thaw.group(1) == "TRUE":
                recoil_thaws.add(ids[symbol])
        value = re.search(r"\.recoilPercentage = ([^}]+)", "\n".join(body)).group(1).strip()
        if value == "B_UPDATED_MOVE_DATA >= GEN_3 ? 33 : 25":
            config = open(os.path.join(upstream_dir, "include/config/battle.h"), encoding="utf-8").read()
            if not re.search(r"#define B_UPDATED_MOVE_DATA\s+GEN_LATEST", config):
                raise ValueError("Recoil percentage requires reviewed updated move data config")
            value = "33"
        if value not in ("25", "33", "50"):
            raise ValueError("Unresolved recoil percentage: " + value)
        recoil_percentages[ids[symbol]] = int(value)
    effects = open(os.path.join(upstream_dir, "src/data/battle_move_effects.h"), encoding="utf-8").read()
    if not re.search(r"\[EFFECT_RECOIL\]\s*=\s*\{\s*\.battleScript = BattleScript_EffectHit,", effects):
        raise ValueError("Recoil no longer uses the selected-hit script; review source contract")
    if not re.search(r"\[EFFECT_ABSORB\]\s*=\s*\{\s*\.battleScript = BattleScript_EffectHit,", effects):
        raise ValueError("Drain selected-hit script changed")
    if not re.search(r"\[EFFECT_EARTHQUAKE\]\s*=\s*\{\s*\.battleScript = BattleScript_EffectHit,", effects):
        raise ValueError("Earthquake selected-hit script changed")
    if not re.search(r"\[EFFECT_DOUBLE_POWER_ON_ARG_STATUS\]\s*=\s*\{\s*\.battleScript = BattleScript_EffectHit,", effects):
        raise ValueError("Status-double selected-hit script changed")
    if not re.search(r"\[EFFECT_BRINE\]\s*=\s*\{\s*\.battleScript = BattleScript_EffectHit,", effects):
        raise ValueError("Brine selected-hit script changed")
    if not re.search(r"\[EFFECT_GYRO_BALL\]\s*=\s*\{\s*\.battleScript = BattleScript_EffectHit,", effects):
        raise ValueError("Gyro Ball selected-hit script changed")
    if not re.search(r"\[EFFECT_ELECTRO_BALL\]\s*=\s*\{\s*\.battleScript = BattleScript_EffectHit,", effects):
        raise ValueError("Electro Ball selected-hit script changed")
    verify_gyro_speed_contract(upstream_dir)
    verify_helper_contract(upstream_dir, ordinary)
    scripts = open(os.path.join(upstream_dir, "data/battle_scripts_1.s"), encoding="utf-8").read()
    hit_script = scripts[scripts.index("BattleScript_EffectHit::"):scripts.index("BattleScript_MakeMoveMissed::")]
    if hit_script.index("datahpupdate BS_TARGET, MOVE_DAMAGE_HP_UPDATE") > hit_script.index("setadditionaleffects"):
        raise ValueError("Whirlpool additional effects no longer follow selected damage")
    commands = open(os.path.join(upstream_dir, "src/battle_script_commands.c"), encoding="utf-8").read()
    if "if (!additionalEffect->preAttackEffect)" not in commands:
        raise ValueError("Pre-attack additional-effect source predicate changed")
    for (path, name), digest in {**UNDERWATER_HELPERS, **STATUS_DOUBLE_HELPERS}.items():
        verify_underwater_helper(open(os.path.join(upstream_dir, path), encoding="utf-8").read(), name, digest)
    battle = open(os.path.join(upstream_dir, "src/battle_util.c"), encoding="utf-8").read()
    order = [battle.index("DAMAGE_MULTIPLY_MODIFIER(" + name + "(") for name in
             ("GetMinimizeModifier", "GetUndergroundModifier", "GetDiveModifier", "GetAirborneModifier", "GetScreensModifier")]
    if order != sorted(order):
        raise ValueError("Underwater final modifier source order changed")
    ability_text = open(os.path.join(upstream_dir, "src/data/abilities.h"), encoding="utf-8").read()
    comatose = ability_text[ability_text.index("[ABILITY_COMATOSE]"):ability_text.index("[ABILITY_QUEENLY_MAJESTY]")]
    if hashlib.sha256(re.sub(r"\s+", "", comatose).encode()).hexdigest() != "064d0ddce53959bc9298b2c65253c579ee2341f76dd67704c911d72098c7c72b":
        raise ValueError("Changed Comatose source record")
    for flag in ("cantBeCopied", "cantBeSwapped", "cantBeTraced", "cantBeSuppressed", "cantBeOverwritten"):
        if not re.search(r"\." + flag + r"\s*=\s*TRUE", comatose):
            raise ValueError("Changed Comatose identity authority: " + flag)
    damp_bans, unknown_damp_bans = parse_damp_bans(move_text)
    generated = generate_kotlin(effect_by_id, target_by_id, ordinary, flags_by_id,
                                unknown_flags_by_id, priority_by_id, unknown_priority_ids,
                                ability_flags_by_id, unknown_ability_flags_by_id,
                                contact_by_id, unknown_contact_by_id,
                                sheer_by_id, unknown_sheer_by_id, recoil_ids, recoil_thaws, drain_percentages, earthquake_flags, explosion_powers, {ids[s] for s in damp_bans}, {ids[s] for s in unknown_damp_bans}, underwater_powers, status_double, status_constants, brine, dynamic_power, speed_power_table)
    generated_json = generate_metadata_json(effect_by_id, ordinary, ability_flags_by_id,
                                            unknown_ability_flags_by_id, contact_by_id,
                                            unknown_contact_by_id, sheer_by_id, unknown_sheer_by_id, recoil_percentages, damage_shapes, drain_percentages, target_by_id, priority_by_id, flags_by_id, earthquake_flags, explosion_powers, underwater_powers, status_double, status_constants, brine, dynamic_power)

    # Both inventories are independently regenerated/verified from this same pinned commit.
    inventory_root = os.path.join(DEFAULT_REPO_ROOT, "tools")
    with open(os.path.join(inventory_root, "hns-abilities/ability_inventory.tsv")) as handle:
        abilities = {row["id"]: row["display_name"] for row in csv.DictReader(handle, delimiter="\t")}
    with open(os.path.join(inventory_root, "hns-items/item_inventory.tsv")) as handle:
        items = {row["id"]: row["hold_effect"] for row in csv.DictReader(handle, delimiter="\t")}
    speed_json = json.dumps({"pinnedCommit": PINNED_COMMIT, "abilities": abilities, "holdEffects": items, "electroBallPowerTable": speed_power_table}, sort_keys=True, separators=(",", ":")) + "\n"
    speed_path = os.path.join(DEFAULT_REPO_ROOT, "tools/hns-move-mechanics/hns_speed_contract.json")
    if args.verify:
        if not os.path.isfile(speed_path) or open(speed_path).read() != speed_json:
            raise ValueError("Effective-Speed identity contract stale")
    else:
        with open(speed_path, "w") as handle: handle.write(speed_json)

    stable_ids = sorted(int(i) for i, name in abilities.items() if name in REPEATED_STRIKE_STABLE_ABILITIES)
    if len(stable_ids) != len(REPEATED_STRIKE_STABLE_ABILITIES):
        raise ValueError("Missing repeated-strike stable ability identity")
    repeated_contract = {"pinnedCommit": PINNED_COMMIT, "movesCount": ids["MOVES_COUNT_ALL"],
        "beakBlastMoveId": ids["MOVE_BEAK_BLAST"], "stableAbilityIds": stable_ids,
        "stableHoldEffects": sorted(REPEATED_STRIKE_STABLE_HOLD_EFFECTS),
        "variableMultiHitCountRules": {
            "configMacro": "B_MULTI_HIT_CHANCE", "latestGeneration": 9,
            "ordinary": {"nominalCounts": [2, 3, 4, 5], "weights": [7, 7, 3, 3], "rngTag": "RNG_HITS"},
            "loadedDice": {"nominalCounts": [4, 5], "uniform": True, "rngTag": "RNG_LOADED_DICE"},
            "skillLink": {"nominalCounts": [5], "countRng": False},
            "precedence": ["SKILL_LINK", "LOADED_DICE", "ORDINARY_RANDOM"],
            "olderGeneration": {"nominalCounts": [2, 3, 4, 5], "weights": [3, 3, 1, 1]}}
    }
    repeated_json = json.dumps(repeated_contract, indent=2, sort_keys=True) + "\n"
    repeated_path = os.path.join(DEFAULT_REPO_ROOT, "tools/hns-move-mechanics/hns_repeated_strike_contract.json")
    if args.verify:
        if not os.path.isfile(repeated_path) or open(repeated_path).read() != repeated_json:
            raise ValueError("Stale repeated-strike authority contract")
    else:
        with open(repeated_path, "w") as handle: handle.write(repeated_json)
    repeated_kotlin = "    val repeatedStrikeStableAbilityIds: Set<Int> = setOf(" + ", ".join(map(str, stable_ids)) + ")\n"
    repeated_kotlin += "    val repeatedStrikeStableHoldEffects: Set<String> = setOf(" + ", ".join(json.dumps(e) for e in sorted(REPEATED_STRIKE_STABLE_HOLD_EFFECTS)) + ")\n"
    repeated_kotlin += "    val variableMultiHitOrdinaryNominalCounts: List<Int> = listOf(2, 3, 4, 5)\n"
    repeated_kotlin += "    val variableMultiHitOrdinaryWeights: List<Int> = listOf(7, 7, 3, 3)\n"
    repeated_kotlin += "    val variableMultiHitLoadedDiceNominalCounts: List<Int> = listOf(4, 5)\n"
    repeated_kotlin += "    val variableMultiHitSkillLinkNominalCounts: List<Int> = listOf(5)\n"
    repeated_kotlin += f"    const val selectedMoveCount: Int = {ids['MOVES_COUNT_ALL']}\n"
    generated = generated.rsplit("}", 1)[0] + repeated_kotlin + "}\n"

    status_path = os.path.join(DEFAULT_REPO_ROOT, "tools/hns-move-mechanics/hns_status_contract.json")
    status_json = json.dumps({"statuses": status_constants, "moves": status_double}, indent=2, sort_keys=True) + "\n"
    if args.verify:
        if not os.path.isfile(status_path) or open(status_path).read() != status_json:
            raise ValueError("Stale generated status constants")
    else:
        with open(status_path, "w") as handle:
            handle.write(status_json)
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
