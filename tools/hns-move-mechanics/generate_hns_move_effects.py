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
    the state-dependent damage flags.

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

DEFAULT_UPSTREAM_SEARCH_PATHS = [
    os.environ.get("HNS_UPSTREAM_DIR"),
    os.path.join(os.path.dirname(DEFAULT_REPO_ROOT), "upstream-hns/pokehns-expansion"),
]

MOVE_ENUM_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*(?:=\s*([^,]+))?,?$")
MOVE_ENTRY_RE = re.compile(r"^\s*\[(MOVE_[A-Z0-9_]+)\]\s*=")
EFFECT_VALUE_RE = re.compile(r"\.effect\s*=\s*([^,\n]+)")
TARGET_VALUE_RE = re.compile(r"\.target\s*=\s*([^,\n]+)")
PLAIN_EFFECT_RE = re.compile(r"EFFECT_[A-Z0-9_]+")
PLAIN_TARGET_RE = re.compile(r"TARGET_[A-Z0-9_]+")

# Flags that make an otherwise-EFFECT_HIT move's damage depend on battle state the
# request shape cannot express, so the ADV pipeline cannot be trusted to reproduce it.
STATE_DEPENDENT_FLAGS = (
    "ignoresTargetDefenseEvasionStages",
    "ignoresTargetAbility",
    "damagesUnderground",
    "damagesUnderwater",
    "damagesAirborne",
    "damagesAirborneDoubleDamage",
    "minimizeDoubleDamage",
    "ignoreTypeIfFlyingAndUngrounded",
    "alwaysCriticalHit",
)


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


def classify_body(body):
    """Return (effect, complication, target) for one move body.

    ``effect`` is the single unambiguous ``EFFECT_*`` symbol, or None when the effect is
    conditional, computed or declared more than once. ``complication`` is a short reason
    string when an ``EFFECT_HIT`` move must not be treated as ordinary, else None.
    ``target`` is the single unambiguous ``TARGET_*`` symbol, or None.
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
        depth += line.count("{") - line.count("}")

    effect = next(iter(plain_effects)) if len(plain_effects) == 1 and not computed_effects else None
    target = next(iter(plain_targets)) if len(plain_targets) == 1 and not computed_targets else None
    if effect != "EFFECT_HIT":
        return effect, None, target
    if multi_hit:
        return effect, "multiHit", target
    if strike_count > 1:
        return effect, f"strikeCount={strike_count}", target
    if explosion:
        return effect, "explosion", target
    if flags:
        return effect, flags[0], target
    return effect, None, target


def parse_move_table(text):
    """Return (effect_by_symbol, ordinary_symbols, unresolved_symbols)."""
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
    for symbol, body in _entry_body(lines, start, end):
        effect, complication, target = classify_body(body)
        if effect is None:
            unresolved.add(symbol)
            continue
        effect_by_symbol[symbol] = effect
        if target is not None:
            target_by_symbol[symbol] = target
        if effect == "EFFECT_HIT" and complication is None:
            ordinary_symbols.add(symbol)
    return effect_by_symbol, target_by_symbol, ordinary_symbols, unresolved


def build_maps(upstream_dir):
    """Resolve the pinned checkout into {move_id: effect} and an ordinary move-ID set."""
    moves_header = os.path.join(upstream_dir, "include/constants/moves.h")
    move_table = os.path.join(upstream_dir, "src/data/moves_info.h")
    ids = parse_move_enum(open(moves_header, encoding="utf-8", errors="replace").read())
    effect_by_symbol, target_by_symbol, ordinary_symbols, unresolved = parse_move_table(
        open(move_table, encoding="utf-8", errors="replace").read()
    )

    effect_by_id = {}
    target_by_id = {}
    ordinary = set()
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
    return effect_by_id, target_by_id, ordinary, unresolved


def generate_kotlin(effect_by_id, target_by_id, ordinary):
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
    lines.append(" * state-dependent damage flag. See generate_hns_move_effects.py for the exact rule.")
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
    lines.append("")
    lines.append("    /** Moves proven to be ordinary fixed-base-power attacks in the pinned source. */")
    lines.append("    val ordinaryMoveIds: Set<Int> = setOf(")
    for move_id in sorted(ordinary):
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-dir", default=None)
    parser.add_argument("--output", default=DEFAULT_TARGET_FILE)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()

    upstream_dir = find_upstream_dir(args.upstream_dir)
    verify_git_commit(upstream_dir)
    effect_by_id, target_by_id, ordinary, unresolved = build_maps(upstream_dir)
    generated = generate_kotlin(effect_by_id, target_by_id, ordinary)

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
        return 0

    with open(args.output, "w", encoding="utf-8") as handle:
        handle.write(generated)
    print(
        f"wrote {args.output}: {len(effect_by_id)} resolved effects, "
        f"{len(ordinary)} ordinary, {len(unresolved)} unresolved (fail-closed)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
