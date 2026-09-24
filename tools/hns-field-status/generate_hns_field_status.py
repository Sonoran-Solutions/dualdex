#!/usr/bin/env python3
"""Validate the reviewed H&S 2.0.5 `gFieldStatuses` audit and emit its generated constants.

`gFieldStatuses` is the battle-global field word. The calculator reads it live and must decide,
bit by bit, whether an active field condition can change the displayed single-hit damage range.
This generator never decides that a bit is safe. It only COLLECTS and CHECKS:

  * the twelve `STATUS_FIELD_*` bit definitions and the `STATUS_FIELD_TERRAIN_ANY` composition are
    parsed from the pinned `include/constants/battle.h` and must equal the reviewed bit table in
    `field_audit.json` (a moved, renamed, added or removed bit fails);
  * every pinned `src/**/*.c` reference to a field token (the bit symbols, `gFieldStatuses`,
    `ctx->fieldStatuses`, the terrain/grounding/sport helpers, `IsGravityPreventingMove` and
    `IsLastMonToMove`) is grouped by its enclosing definition and must EQUAL the reviewed
    `reference_sites` (AI, debug and animation sources excluded). A new, moved or removed read fails
    the check instead of silently inheriting a review;
  * the move facts the request-local rules consume are derived from the pinned move table: the
    ordinary moves (the same extraction as tools/hns-move-mechanics) that are Gravity-banned, whose
    priority is not provably <= 0, or that declare a terrain boost. An ordinary terrain-boost move
    fails the check, because the terrain rules assume none exists;
  * the ability IDs the rules compare against are read from the pinned `enum Ability`;
  * every reviewed context rule must cite pinned lines that still contain the quoted text and must
    be implemented by name in HnsFieldContextPolicy.kt (and vice versa).

Outputs: app/src/main/java/com/dualdex/pokemon/hns/HnsFieldStatusData.kt and
native/src/hns_field_status_gen.h. `--check` compares both instead of writing them.
"""
import argparse
import importlib.util
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
AUDIT = HERE / "field_audit.json"
KOTLIN_OUT = ROOT / "app/src/main/java/com/dualdex/pokemon/hns/HnsFieldStatusData.kt"
HEADER_OUT = ROOT / "native/src/hns_field_status_gen.h"
POLICY_KT = ROOT / "app/src/main/java/com/dualdex/calculator/HnsFieldContextPolicy.kt"
MOVE_GENERATOR = ROOT / "tools/hns-move-mechanics/generate_hns_move_effects.py"
PIN = "1f42b74dff0e9fe942419845d040663dd829a973"

BATTLE_H = "include/constants/battle.h"
ABILITIES_H = "include/constants/abilities.h"
MOVES_H = "include/constants/moves.h"
MOVES_INFO = "src/data/moves_info.h"

EXCLUDED_SOURCE = re.compile(r"^(battle_ai.*|battle_debug\.c|battle_anim.*|battle_bg\.c)$")
DEFINITION = re.compile(
    r"^(?:static\s+|const\s+|inline\s+|extern\s+)*[A-Za-z_][\w\s\*]*?\b([A-Za-z_]\w*)\s*(\[[^\]]*\])*\s*(\(|=|$)"
)
FIELD_TOKEN = re.compile(
    r"\b(STATUS_FIELD_[A-Z_]+|gFieldStatuses|fieldStatuses"
    r"|Is(?:Electric|Misty|Grassy|Psychic|Any|Battler)TerrainAffected"
    r"|IsBattlerGrounded(?:InverseCheck)?|IsGravityPreventingMove|IsMoveGravityBanned"
    r"|IsFieldMudSportAffected|IsFieldWaterSportAffected|IsLastMonToMove)\b"
)
BIT_DEFINE = re.compile(r"^#define\s+(STATUS_FIELD_[A-Z_]+)\s+\(1\s*<<\s*(\d+)\)\s*$")
ANY_DEFINE = re.compile(r"^#define\s+(STATUS_FIELD_[A-Z_]+)\s+(.*)$")
ABILITY_ENUM = re.compile(r"^\s*(ABILITY_[A-Z0-9_]+)\s*=\s*(\d+)\s*,")
AFFECTS = (
    "move_type", "move_category", "base_power", "attack_stat", "defense_stat", "final_damage",
    "type_effectiveness", "turn_order", "move_failure", "item_activation", "ability_activation",
)


class AuditError(Exception):
    pass


def kt(value):
    return json.dumps(value, ensure_ascii=False)


def load_move_generator():
    spec = importlib.util.spec_from_file_location("hns_move_effects", MOVE_GENERATOR)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def verify_commit(upstream):
    try:
        head = subprocess.check_output(
            ["git", "-C", str(upstream), "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (subprocess.CalledProcessError, OSError) as exc:
        raise AuditError(f"cannot resolve git HEAD in {upstream}: {exc}") from exc
    if head != PIN:
        raise AuditError(f"upstream checkout is at {head}, expected pinned {PIN}")


def parse_field_constants(battle_h):
    """Return ({symbol: bit} for single-bit defines, {symbol: [member symbols]} for compositions).

    Every `STATUS_FIELD_*` define must be either `(1 << n)` or an OR of other defines; any other
    form is refused rather than evaluated.
    """
    bits, compositions = {}, {}
    for line in battle_h.splitlines():
        line = line.split("//", 1)[0].rstrip()
        match = BIT_DEFINE.match(line)
        if match:
            bits[match.group(1)] = int(match.group(2))
            continue
        match = ANY_DEFINE.match(line)
        if not match:
            continue
        body = match.group(2).strip()
        inner = re.fullmatch(r"\((.*)\)", body)
        members = [m.strip() for m in (inner.group(1) if inner else "").split("|")]
        if not inner or not all(re.fullmatch(r"STATUS_FIELD_[A-Z_]+", m) for m in members):
            raise AuditError(f"unrecognised STATUS_FIELD definition form: {line!r}")
        compositions[match.group(1)] = members
    if not bits:
        raise AuditError("no STATUS_FIELD_* bit definitions found in the pinned battle header")
    for name, members in compositions.items():
        missing = [m for m in members if m not in bits]
        if missing:
            raise AuditError(f"{name} names undefined field bits {missing}")
    if len(set(bits.values())) != len(bits):
        raise AuditError("two STATUS_FIELD_* symbols share a bit position")
    return bits, compositions


def check_bit_table(bits, compositions, audit):
    reviewed = audit["statuses"]
    by_symbol = {s["symbol"]: s for s in reviewed}
    if [s["symbol"] for s in sorted(reviewed, key=lambda s: s["bit"])] != [s["symbol"] for s in reviewed]:
        raise AuditError("reviewed statuses must be listed in bit order")
    if set(bits) != set(by_symbol):
        raise AuditError(
            f"pinned STATUS_FIELD_* bits changed: unreviewed {sorted(set(bits) - set(by_symbol))}, "
            f"stale {sorted(set(by_symbol) - set(bits))}")
    for symbol, bit in bits.items():
        if by_symbol[symbol]["bit"] != bit:
            raise AuditError(f"{symbol} moved from reviewed bit {by_symbol[symbol]['bit']} to pinned bit {bit}")
    if compositions != audit["compositions"]:
        raise AuditError(f"STATUS_FIELD_* compositions changed: pinned {compositions}")


def scan_reference_sites(upstream):
    sites = {}
    for path in sorted((upstream / "src").rglob("*.c")):
        if EXCLUDED_SOURCE.match(path.name):
            continue
        rel = str(path.relative_to(upstream))
        enclosing = None
        for n, line in enumerate(path.read_text(errors="replace").splitlines(), 1):
            if line and not line[0].isspace() and line[0] not in "{}#/":
                match = DEFINITION.match(line)
                if match:
                    enclosing = match.group(1)
            tokens = set(FIELD_TOKEN.findall(line))
            if tokens:
                site = sites.setdefault(f"{rel}:{enclosing}", {"lines": [], "tokens": set()})
                site["lines"].append(n)
                site["tokens"].update(tokens)
    return {k: {"lines": v["lines"], "tokens": sorted(v["tokens"])} for k, v in sites.items()}


def check_reference_sites(scanned, audit):
    reviewed = audit["reference_sites"]
    if set(scanned) != set(reviewed):
        raise AuditError(
            f"pinned field-status reference sites changed; review {sorted(set(scanned) - set(reviewed))}, "
            f"stale {sorted(set(reviewed) - set(scanned))}")
    statuses = {s["symbol"] for s in audit["statuses"]}
    for key, site in scanned.items():
        entry = reviewed[key]
        if entry.get("lines") != site["lines"] or entry.get("tokens") != site["tokens"]:
            raise AuditError(f"{key}: pinned lines/tokens changed to {site['lines']} {site['tokens']}; review them")
        if not entry.get("use") or entry.get("damage") not in ("ordinary", "excluded", "none"):
            raise AuditError(f"{key}: needs a reviewed use and damage disposition (ordinary/excluded/none)")
        for symbol in entry.get("statuses", []):
            if symbol not in statuses:
                raise AuditError(f"{key}: names unknown status {symbol}")


def check_evidence(upstream, owner, evidence):
    if not evidence:
        raise AuditError(f"{owner} needs pinned source evidence")
    for item in evidence:
        match = re.fullmatch(r"(.+):(\d+)", item.get("source", ""))
        if not match:
            raise AuditError(f"invalid source reference for {owner}: {item.get('source')!r}")
        path = upstream / match.group(1)
        if not path.is_file():
            raise AuditError(f"missing pinned source file for {owner}: {path}")
        lines = path.read_text(errors="replace").splitlines()
        line_no = int(match.group(2))
        if line_no < 1 or line_no > len(lines):
            raise AuditError(f"source line out of range for {owner}: {item['source']}")
        needle = item.get("contains", "")
        if not needle or needle not in lines[line_no - 1]:
            raise AuditError(f"pinned source evidence changed for {owner}: {item['source']}")


def check_statuses(upstream, audit, policy_text):
    sites = audit["reference_sites"]
    rule_names = []
    for status in audit["statuses"]:
        symbol = status["symbol"]
        for key in ("kotlin_name", "display_name", "rationale", "fallback"):
            if not status.get(key):
                raise AuditError(f"{symbol} needs {key}")
        affects = status.get("affects", {})
        if set(affects) != set(AFFECTS) or not all(isinstance(v, bool) for v in affects.values()):
            raise AuditError(f"{symbol} must record every affects flag {AFFECTS} as a boolean")
        for key in ("set_by", "cleared_by", "read_by"):
            refs = status.get(key)
            if not refs:
                raise AuditError(f"{symbol} needs {key}")
            for ref in refs:
                if ref not in sites:
                    raise AuditError(f"{symbol} {key} names a site that is not a pinned reference: {ref}")
        for ref, site in sites.items():
            if symbol in site.get("statuses", []) and ref not in status["read_by"] + status["set_by"] + \
                    status["cleared_by"]:
                raise AuditError(f"{symbol}: reference site {ref} is attributed to it but not listed")
        for rule in status.get("rules", []):
            name = rule.get("rule", "")
            if not name or name in rule_names:
                raise AuditError(f"missing or duplicate field rule name {name!r}")
            if rule.get("relevance") not in ("PROVEN_IRRELEVANT", "RELEVANT", "UNKNOWN"):
                raise AuditError(f"field rule {name} needs a relevance")
            if not rule.get("predicate") or not isinstance(rule.get("operands"), list):
                raise AuditError(f"field rule {name} needs a predicate and its operands")
            if f'"{name}"' not in policy_text:
                raise AuditError(f"field rule {name} is not implemented in HnsFieldContextPolicy.kt")
            rule_names.append(name)
        for rule in status.get("rules", []):
            check_evidence(upstream, rule["rule"], rule.get("evidence", []))
        check_evidence(upstream, symbol, status.get("damage_evidence", []))
    unknown = audit.get("unknown_bits", {})
    name = unknown.get("rule", "")
    if not name or name in rule_names or unknown.get("relevance") != "UNKNOWN" or not unknown.get("predicate"):
        raise AuditError("the unknown-bit rule must be a named, UNKNOWN, reviewed rule")
    if f'"{name}"' not in policy_text:
        raise AuditError(f"field rule {name} is not implemented in HnsFieldContextPolicy.kt")
    check_evidence(upstream, name, unknown.get("evidence", []))
    rule_names.append(name)
    for name in re.findall(r'rule = "([a-z0-9_]+)"', policy_text):
        if name not in rule_names:
            raise AuditError(f"HnsFieldContextPolicy.kt implements unreviewed field rule {name}")
    return rule_names


def parse_ability_ids(abilities_h, wanted):
    ids = {}
    for line in abilities_h.splitlines():
        match = ABILITY_ENUM.match(line)
        if match and match.group(1) in wanted:
            ids[match.group(1)] = int(match.group(2))
    missing = sorted(set(wanted) - set(ids))
    if missing:
        raise AuditError(f"abilities not found with an explicit value in the pinned enum: {missing}")
    return ids


def move_facts(upstream):
    """Ordinary move IDs that are Gravity-banned, may have positive priority, or declare a terrain boost."""
    gen = load_move_generator()
    ids = gen.parse_move_enum((upstream / MOVES_H).read_text(errors="replace"))
    lines = (upstream / MOVES_INFO).read_text(errors="replace").splitlines()
    start = next((i for i, l in enumerate(lines) if "gMovesInfo[MOVES_COUNT_ALL]" in l), None)
    if start is None:
        raise AuditError("gMovesInfo[MOVES_COUNT_ALL] not found in the pinned move table")
    end = next((i for i in range(start, len(lines)) if lines[i].strip() == "};"), None)
    gravity, priority, terrain = set(), set(), set()
    ordinary_count = 0
    for symbol, body in gen._entry_body(lines, start, end):
        effect, complication, _ = gen.classify_body(body)
        if effect != "EFFECT_HIT" or complication is not None:
            continue
        move_id = ids.get(symbol)
        if move_id is None:
            raise AuditError(f"move table references {symbol}, absent from enum Move")
        if move_id == 0:
            continue
        ordinary_count += 1
        text = "\n".join(line for line in body if ".zMove" not in line)
        banned = re.search(r"\.gravityBanned\s*=\s*([^,\n]+)", text)
        if banned and banned.group(1).strip() != "FALSE":
            gravity.add(move_id)  # TRUE, or any conditional value, fails closed
        prio = re.search(r"\.priority\s*=\s*([^,\n]+)", text)
        if prio:
            value = prio.group(1).strip()
            if not re.fullmatch(r"-?\d+", value) or int(value) > 0:
                priority.add(move_id)  # positive, or not a provable literal <= 0
        healing = re.search(r"\.healingMove\s*=\s*([^,\n]+)", text)
        if healing and healing.group(1).strip() != "FALSE":
            priority.add(move_id)  # Triage adds +3 to a healing move (GetBattleMovePriority)
        if re.search(r"\.terrainBoost\b|\.terrain\s*=", text):
            terrain.add(move_id)
    if ordinary_count == 0:
        raise AuditError("no ordinary moves parsed from the pinned move table")
    return sorted(gravity), sorted(priority), sorted(terrain)


def render_kotlin(audit, bits, compositions, ability_ids, gravity, priority, rule_names):
    known = 0
    for bit in bits.values():
        known |= 1 << bit
    lines = [
        "package com.dualdex.pokemon.hns",
        "",
        "// Generated by tools/hns-field-status/generate_hns_field_status.py from the pinned",
        f"// include/constants/battle.h and the reviewed tools/hns-field-status/field_audit.json",
        f"// (pokehns-expansion {PIN}). Do not edit.",
        "",
        "/** One pinned `gFieldStatuses` bit, in bit order (`include/constants/battle.h`). */",
        "enum class HnsFieldStatus(val bit: Int, val sourceSymbol: String, val displayName: String) {",
    ]
    statuses = audit["statuses"]
    for i, status in enumerate(statuses):
        end = "," if i < len(statuses) - 1 else ";"
        lines.append(f"    {status['kotlin_name']}({status['bit']}, {kt(status['symbol'])}, "
                     f"{kt(status['display_name'])}){end}")
    lines += [
        "",
        "    /** The pinned single-bit mask. */",
        "    val mask: Int get() = 1 shl bit",
        "}",
        "",
        "internal object HnsFieldStatusData {",
        f"    const val PINNED_COMMIT: String = {kt(PIN)}",
        "",
    ]
    for status in statuses:
        lines.append(f"    const val {status['symbol']}: Int = 0x{1 << status['bit']:08X}")
    for name, members in compositions.items():
        value = 0
        for member in members:
            value |= 1 << bits[member]
        lines.append(f"    const val {name}: Int = 0x{value:08X} // {' | '.join(members)}")
    lines += [
        "",
        "    /** Union of every reviewed pinned bit; any other bit is unknown and fails closed. */",
        f"    const val KNOWN_MASK: Int = 0x{known:08X}",
        "",
    ]
    for name in sorted(ability_ids):
        lines.append(f"    const val {name}: Int = {ability_ids[name]}")
    lines += [
        "",
        "    /** Ordinary moves whose pinned `gravityBanned` is not FALSE (Gravity makes them fail). */",
        "    val gravityBannedOrdinaryMoveIds: Set<Int> = setOf(" + ", ".join(map(str, gravity)) + ")",
        "",
        "    /** Ordinary moves whose pinned priority is positive, not a provable literal <= 0, or a Triage healing move. */",
        "    val positivePriorityOrdinaryMoveIds: Set<Int> = setOf(" + ", ".join(map(str, priority)) + ")",
        "",
        "    /** Every reviewed request-local field rule name (implemented by HnsFieldContextPolicy). */",
        "    val contextRuleNames: Set<String> = setOf(",
    ]
    for name in rule_names:
        lines.append(f"        {kt(name)},")
    lines += ["    )", "}", ""]
    return "\n".join(lines)


def render_header(audit, bits, compositions):
    known = 0
    for bit in bits.values():
        known |= 1 << bit
    lines = [
        "/*",
        " * GENERATED FILE - do not edit by hand.",
        " *",
        " * Pinned Heart & Soul 2.0.5 `gFieldStatuses` bits, parsed from include/constants/battle.h",
        " * by tools/hns-field-status/generate_hns_field_status.py.",
        f" * Pinned upstream: PokemonHnS-Development/pokehns-expansion {PIN}",
        " *",
        " * The native reader stores the raw word unmasked; these masks exist for the reader config",
        " * and the host tests, never to filter the observed value.",
        " */",
        "#ifndef DUALDEX_HNS_FIELD_STATUS_GEN_H",
        "#define DUALDEX_HNS_FIELD_STATUS_GEN_H",
        "",
    ]
    for status in audit["statuses"]:
        lines.append(f"#define HNS_{status['symbol']} (1u << {status['bit']})")
    for name, members in compositions.items():
        lines.append(f"#define HNS_{name} ({' | '.join('HNS_' + m for m in members)})")
    lines += [
        f"#define HNS_STATUS_FIELD_KNOWN_MASK 0x{known:08X}u",
        "",
        "#endif /* DUALDEX_HNS_FIELD_STATUS_GEN_H */",
        "",
    ]
    return "\n".join(lines)


def build(upstream, audit, policy_text):
    if audit.get("pinned_commit") != PIN:
        raise AuditError("field_audit.json is not pinned to the audited commit")
    bits, compositions = parse_field_constants((upstream / BATTLE_H).read_text(errors="replace"))
    check_bit_table(bits, compositions, audit)
    check_reference_sites(scan_reference_sites(upstream), audit)
    rule_names = check_statuses(upstream, audit, policy_text)
    ability_ids = parse_ability_ids((upstream / ABILITIES_H).read_text(errors="replace"),
                                    audit["rule_abilities"])
    gravity, priority, terrain = move_facts(upstream)
    if terrain:
        raise AuditError(f"ordinary moves declare a terrain boost {terrain}; the terrain rules assume none")
    return (render_kotlin(audit, bits, compositions, ability_ids, gravity, priority, rule_names),
            render_header(audit, bits, compositions), gravity, priority, rule_names)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream-dir", required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    upstream = pathlib.Path(args.upstream_dir).resolve()
    try:
        verify_commit(upstream)
        audit = json.loads(AUDIT.read_text())
        kotlin, header, gravity, priority, rule_names = build(upstream, audit, POLICY_KT.read_text())
    except Exception as exc:  # noqa: BLE001 - every extraction or review failure is fatal
        print(f"error: {exc}", file=sys.stderr)
        sys.exit(1)
    if args.check:
        if KOTLIN_OUT.read_text() != kotlin or HEADER_OUT.read_text() != header:
            print("error: generated field-status Kotlin/C differs from the pinned source/review", file=sys.stderr)
            sys.exit(1)
    else:
        KOTLIN_OUT.write_text(kotlin)
        HEADER_OUT.write_text(header)
    print(f"{len(audit['statuses'])} pinned field bits, {len(audit['reference_sites'])} reviewed reference sites, "
          f"{len(rule_names)} request-local rules; gravity-banned ordinary {gravity}, "
          f"positive-priority ordinary {priority}")


if __name__ == "__main__":
    main()
