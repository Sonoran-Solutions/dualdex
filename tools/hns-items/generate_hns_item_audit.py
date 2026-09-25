#!/usr/bin/env python3
"""Validate the reviewed H&S 2.0.5 held-item capability audit and emit its artifacts.

The audit is organised by compiled hold effect: every one of the 901 pinned item identities is
classified by the reviewed decision for its `holdEffect` family, except explicit identity
exceptions. The generator only COLLECTS and CHECKS; it never decides that an item is safe:

  * the exact item domain, symbols, names, hold effects and params are re-derived from the pinned
    source (the same extraction as generate_hns_items.py) and must match the committed
    Hns205ItemCatalogue.kt byte-for-byte (missing/extra/duplicate IDs, renamed symbols or names,
    hold-effect or param drift all fail);
  * every catalogue hold effect must have exactly one reviewed family decision, and no decision may
    name a hold effect the pinned build does not use;
  * each family's `reviewed_refs` must EQUAL the set of pinned `src/**/*.c` references to that
    HOLD_EFFECT_* symbol (AI, debug and animation sources excluded). A new, moved or removed read
    fails the check instead of silently inheriting a decision; a family with no references is
    recorded as such by the reviewer, never inferred from a grep miss;
  * literal ITEM_* catalogue symbols in pinned `src/battle_*.c` must occur only inside reviewed
    enclosing definitions (`identity_reference_sites`), so an identity-specific read cannot appear
    unreviewed;
  * every ITEM_* identity in pinned src/data/pokemon/form_change_tables.h, with its form-change
    methods, must equal the reviewed `form_change_item_identities`, and an identity with a
    battle-time held method (FORM_CHANGE_BEGIN_BATTLE or FORM_CHANGE_BATTLE_*) may never be
    PROVEN_NO_ORDINARY_DAMAGE_EFFECT: a held item can change battle form/moves through these tables
    independently of its hold effect (e.g. Rusted Sword/Shield);
  * identity exceptions and request-local context rules must refer to real identities/families, and
    every context rule must be implemented by name in HnsItemContextPolicy.kt and cite pinned source
    lines that still contain the quoted text.

Outputs: tools/hns-items/item_inventory.tsv (one reviewed row per item ID) and the generated
Kotlin data app/src/main/java/com/dualdex/pokemon/hns/HnsItemAuditData.kt. `--check` compares both
instead of writing them.
"""
import argparse
import csv
import importlib.util
import io
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
DECISIONS = HERE / "decisions.json"
CONTEXT_RULES = HERE / "context_rules.json"
INVENTORY = HERE / "item_inventory.tsv"
CATALOGUE_KT = ROOT / "app/src/main/java/com/dualdex/pokemon/hns/Hns205ItemCatalogue.kt"
AUDIT_KT = ROOT / "app/src/main/java/com/dualdex/pokemon/hns/HnsItemAuditData.kt"
POLICY_KT = ROOT / "app/src/main/java/com/dualdex/calculator/HnsItemContextPolicy.kt"
PIN = "1f42b74dff0e9fe942419845d040663dd829a973"

CATEGORIES = (
    "PROVEN_NO_ORDINARY_DAMAGE_EFFECT",
    "MODELLED_EQUIVALENT",
    "MODELLED_HNS_SPECIFIC",
    "UNSUPPORTED_DAMAGE_RELEVANT",
    "UNCLASSIFIED",
)
# Families whose request-local rules compare the effective move type with an item type operand,
# and the gItemsInfo field that carries that operand in the pinned source.
TYPE_OPERAND_FIELD = {
    "HOLD_EFFECT_TYPE_POWER": "secondaryId",
    "HOLD_EFFECT_PLATE": "secondaryId",
    "HOLD_EFFECT_GEMS": "secondaryId",
    "HOLD_EFFECT_RESIST_BERRY": "holdEffectParam",
}
EXCLUDED_SOURCE = re.compile(r"^(battle_ai.*|battle_debug\.c|battle_anim.*)$")
DEFINITION = re.compile(
    r"^(?:static\s+|const\s+|inline\s+|extern\s+)*[A-Za-z_][\w\s\*]*?\b([A-Za-z_]\w*)\s*(\[[^\]]*\])*\s*(\(|=|$)"
)


FORM_CHANGE_TABLES = "src/data/pokemon/form_change_tables.h"
BATTLE_HELD_FORM_METHODS = re.compile(r"^FORM_CHANGE_(BEGIN_BATTLE|BATTLE_.*)$")


class AuditError(Exception):
    pass


def load_item_generator():
    spec = importlib.util.spec_from_file_location("hns_items", HERE / "generate_hns_items.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def kt(value):
    return json.dumps(value, ensure_ascii=False)


def scan_hold_effect_refs(upstream):
    refs = {}
    for path in sorted((upstream / "src").rglob("*.c")):
        if EXCLUDED_SOURCE.match(path.name):
            continue
        rel = str(path.relative_to(upstream))
        for n, line in enumerate(path.read_text(errors="replace").splitlines(), 1):
            for sym in set(re.findall(r"\bHOLD_EFFECT_[A-Z0-9_]+\b", line)):
                refs.setdefault(sym, []).append(f"{rel}:{n}")
    return refs


def scan_identity_sites(upstream, item_symbols):
    sites = {}
    for path in sorted((upstream / "src").glob("battle_*.c")):
        if EXCLUDED_SOURCE.match(path.name):
            continue
        rel = str(path.relative_to(upstream))
        enclosing = None
        for line in path.read_text(errors="replace").splitlines():
            if line and not line[0].isspace() and line[0] not in "{}#/":
                match = DEFINITION.match(line)
                if match:
                    enclosing = match.group(1)
            found = set(re.findall(r"\bITEM_[A-Z0-9_]+\b", line)) & item_symbols
            if found:
                sites.setdefault(f"{rel}:{enclosing}", set()).update(found)
    return sites


def scan_form_change_items(upstream):
    """{ITEM_* symbol: sorted form-change methods} from the pinned form-change tables."""
    text = (upstream / FORM_CHANGE_TABLES).read_text(errors="replace")
    found = {}
    for match in re.finditer(r"\{\s*(FORM_CHANGE_[A-Z_]+)\s*,[^}]*?\b(ITEM_[A-Z0-9_]+)", text):
        found.setdefault(match.group(2), set()).add(match.group(1))
    return {symbol: sorted(methods) for symbol, methods in found.items()}


def check_form_change_identities(scanned, reviewed, category_of):
    if set(scanned) != set(reviewed):
        raise AuditError(
            f"held-item form-change identities changed; review {sorted(set(scanned) - set(reviewed))}, "
            f"stale {sorted(set(reviewed) - set(scanned))}")
    for symbol, methods in scanned.items():
        entry = reviewed[symbol]
        if entry.get("methods") != methods or not entry.get("disposition"):
            raise AuditError(f"form-change methods for {symbol} changed or lack a disposition: {methods}")
        if any(BATTLE_HELD_FORM_METHODS.match(m) for m in methods) and \
                category_of(symbol) == "PROVEN_NO_ORDINARY_DAMAGE_EFFECT":
            raise AuditError(f"{symbol} changes battle form while held ({methods}) but is classified neutral")


def check_evidence(upstream, rule_name, evidence):
    if not evidence:
        raise AuditError(f"context rule {rule_name} needs pinned source evidence")
    for item in evidence:
        match = re.fullmatch(r"(.+):(\d+)", item.get("source", ""))
        if not match:
            raise AuditError(f"invalid source reference for {rule_name}: {item.get('source')!r}")
        path = upstream / match.group(1)
        if not path.is_file():
            raise AuditError(f"missing pinned source file for {rule_name}: {path}")
        lines = path.read_text(errors="replace").splitlines()
        line_no = int(match.group(2))
        if line_no < 1 or line_no > len(lines):
            raise AuditError(f"source line out of range for {rule_name}: {item['source']}")
        needle = item.get("contains", "")
        if not needle or needle not in lines[line_no - 1]:
            raise AuditError(f"pinned source evidence changed for {rule_name}: {item['source']}")


def validate_context_rules(upstream, decisions):
    data = json.loads(CONTEXT_RULES.read_text())
    if data.get("pinned_commit") != PIN:
        raise AuditError("context_rules.json is not pinned to the audited commit")
    policy = POLICY_KT.read_text()
    groups = {}
    for he, family in decisions["families"].items():
        groups.setdefault(family["group"], []).append(he)
    seen = set()
    rule_names = []
    for group, entry in data["families"].items():
        members = groups.get(group)
        if not members:
            raise AuditError(f"context rules refer to unknown family group {group}")
        for he in members:
            if decisions["families"][he]["category"] not in ("UNSUPPORTED_DAMAGE_RELEVANT", "MODELLED_HNS_SPECIFIC"):
                raise AuditError(f"context rules may only refine UNSUPPORTED or MODELLED families ({group}: {he})")
        if not entry.get("fallback"):
            raise AuditError(f"context group {group} needs a fail-closed fallback")
        for rule in entry.get("context_rules", []) + entry.get("always_blocking", []):
            name = rule.get("rule", "")
            if not name or name in seen:
                raise AuditError(f"missing or duplicate item context rule name: {name!r}")
            seen.add(name)
            rule_names.append(name)
            if f'"{name}"' not in policy:
                raise AuditError(f"item context rule {name} is not implemented in HnsItemContextPolicy.kt")
            if not rule.get("predicate") or not rule.get("rationale"):
                raise AuditError(f"item context rule {name} needs a predicate and rationale")
            for he in rule.get("families", []):
                if he not in members:
                    raise AuditError(f"item context rule {name} names {he} outside group {group}")
            check_evidence(upstream, name, rule.get("evidence", []))
        for rule in entry.get("context_rules", []):
            if rule.get("side") not in ("ATTACKER", "DEFENDER", "EITHER"):
                raise AuditError(f"item context rule {rule['rule']} needs a side")
    for name in re.findall(r'rule = "([a-z0-9_]+)"', policy):
        if name not in seen:
            raise AuditError(f"HnsItemContextPolicy.kt implements unreviewed rule {name}")
    return rule_names


def display_name(source_name):
    if source_name is None:
        return "None"
    keep_upper = {"HP", "PP", "TM", "HM", "EXP.", "X", "S.S.", "RM."}
    words = []
    for word in source_name.split(" "):
        if word.upper() in keep_upper:
            words.append(word.upper())
        else:
            words.append(word[:1].upper() + word[1:].lower())
    return " ".join(words)


def extract(upstream, cpp_bin):
    gen = load_item_generator()
    gen.verify_git_commit(str(upstream))
    itemc = gen.run_cpp(cpp_bin, str(upstream), gen.ITEMS_TABLE_SOURCE)
    enum_ids, count = gen.parse_item_enum(itemc)
    symbols = gen.parse_all_enum_symbols(itemc)
    table = gen.extract_item_table(itemc, symbols)
    catalogue = gen.build_catalogue(enum_ids, count, table)
    if gen.generate_kotlin(catalogue, count) != CATALOGUE_KT.read_text():
        raise AuditError("Hns205ItemCatalogue.kt does not match the pinned item enum/table "
                         "(ID, symbol, name, holdEffect or holdEffectParam drift)")
    # Raw type-operand tokens, before numeric evaluation, from the same preprocessed table.
    start = re.search(r"gItemsInfo\s*\[\s*\]\s*=", itemc).start()
    raw = {}
    for symbol, body in gen.extract_designated_entries(itemc, start, r"ITEM_[A-Za-z0-9_]+", "gItemsInfo"):
        raw[symbol] = {f: gen._field_value(body, f) for f in ("secondaryId", "holdEffectParam")}
    return catalogue, count, raw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream-dir", required=True)
    parser.add_argument("--cpp-bin")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    upstream = pathlib.Path(args.upstream_dir).resolve()
    gen = load_item_generator()
    try:
        catalogue, count, raw = extract(upstream, gen.find_cpp_bin(args.cpp_bin))
        ids = [e["id"] for e in catalogue]
        if ids != list(range(count)) or len(set(e["symbol"] for e in catalogue)) != count:
            raise AuditError("item IDs are missing, duplicated, or not contiguous")
        by_symbol = {e["symbol"]: e for e in catalogue}

        decisions = json.loads(DECISIONS.read_text())
        if decisions.get("pinned_commit") != PIN:
            raise AuditError("decisions.json is not pinned to the audited commit")
        families = decisions["families"]
        used = {e["hold_effect"] for e in catalogue}
        if set(families) != used:
            raise AuditError(
                f"family decisions must cover exactly the pinned hold effects; "
                f"missing={sorted(used - set(families))} extra={sorted(set(families) - used)}")
        refs = scan_hold_effect_refs(upstream)
        for he, family in families.items():
            if family.get("category") not in CATEGORIES:
                raise AuditError(f"invalid category for {he}")
            if not family.get("rationale") or not family.get("group"):
                raise AuditError(f"missing review rationale/group for {he}")
            if sorted(family.get("reviewed_refs", [])) != sorted(refs.get(he, [])):
                raise AuditError(
                    f"{he}: pinned references changed; review them before updating reviewed_refs "
                    f"(pinned={sorted(refs.get(he, []))})")
        exceptions = decisions["identity_exceptions"]
        for symbol, exc in exceptions.items():
            if symbol not in by_symbol:
                raise AuditError(f"identity exception refers to nonexistent item {symbol}")
            if exc.get("category") not in CATEGORIES or not exc.get("rationale"):
                raise AuditError(f"invalid identity exception for {symbol}")
        item_symbols = set(by_symbol) - {"ITEM_NONE"}
        sites = scan_identity_sites(upstream, item_symbols)
        reviewed_sites = decisions["identity_reference_sites"]
        if set(sites) != set(reviewed_sites):
            raise AuditError(
                f"literal item identity reads changed; missing review for "
                f"{sorted(set(sites) - set(reviewed_sites))}, stale {sorted(set(reviewed_sites) - set(sites))}")
        def category_of(symbol):
            exc = exceptions.get(symbol)
            return exc["category"] if exc else families[by_symbol[symbol]["hold_effect"]]["category"]
        form_items = scan_form_change_items(upstream)
        unknown = sorted(set(form_items) - set(by_symbol))
        if unknown:
            raise AuditError(f"form-change tables name non-catalogue items {unknown}")
        check_form_change_identities(form_items, decisions.get("form_change_item_identities", {}), category_of)
        rule_names = validate_context_rules(upstream, decisions)
    except Exception as exc:  # noqa: BLE001 - every extraction or review failure is fatal
        print(f"error: {exc}", file=sys.stderr)
        sys.exit(1)

    rows = []
    item_types = {}
    for entry in catalogue:
        he = entry["hold_effect"]
        family = families[he]
        exc = exceptions.get(entry["symbol"])
        category = exc["category"] if exc else family["category"]
        rationale = exc["rationale"] if exc else family["rationale"]
        group = "identity_exception" if exc else family["group"]
        item_type = ""
        field = TYPE_OPERAND_FIELD.get(he)
        if field:
            token = (raw.get(entry["symbol"]) or {}).get(field) or ""
            match = re.fullmatch(r"TYPE_([A-Z]+)", token.strip())
            if not match:
                print(f"error: {entry['symbol']} {field} is not a TYPE_* token: {token!r}", file=sys.stderr)
                sys.exit(1)
            item_type = match.group(1)
            item_types[entry["id"]] = item_type
        rows.append((entry["id"], entry["symbol"], display_name(entry["source_name"]), he,
                     entry["hold_effect_param"], item_type, category, group, rationale))

    out = io.StringIO()
    writer = csv.writer(out, delimiter="\t", lineterminator="\n")
    writer.writerow(("id", "symbol", "display_name", "hold_effect", "hold_effect_param", "item_type",
                     "category", "family_group", "rationale"))
    writer.writerows(rows)
    inventory = out.getvalue()

    counts = {cat: sum(1 for r in rows if r[6] == cat) for cat in CATEGORIES}
    lines = [
        "package com.dualdex.pokemon.hns",
        "",
        "// Generated by tools/hns-items/generate_hns_item_audit.py from the reviewed",
        "// tools/hns-items/decisions.json against pokehns-expansion " + PIN + ". Do not edit.",
        "internal object HnsItemAuditData {",
        f"    const val PINNED_COMMIT: String = {kt(PIN)}",
        "",
        "    /** Reviewed decision for every hold effect the pinned catalogue uses. */",
        "    val families: Map<String, HnsItemFamilyDecision> = mapOf(",
    ]
    for he in sorted(families):
        f = families[he]
        lines.append(f"        {kt(he)} to HnsItemFamilyDecision({kt(he)}, {kt(f['group'])}, "
                     f"HnsItemCategory.{f['category']}, {kt(f['rationale'])}),")
    lines += [
        "    )",
        "",
        "    /** Identity-specific decisions that override the hold-effect family, keyed by item ID. */",
        "    val identityExceptions: Map<Int, HnsItemFamilyDecision> = mapOf(",
    ]
    for symbol in sorted(exceptions):
        exc = exceptions[symbol]
        lines.append(f"        {by_symbol[symbol]['id']} to HnsItemFamilyDecision({kt(symbol)}, "
                     f"\"identity_exception\", HnsItemCategory.{exc['category']}, {kt(exc['rationale'])}),")
    lines += [
        "    )",
        "",
        "    /** Pinned type operand (secondaryId or resist-berry param) for type-matched families. */",
        "    val itemTypes: Map<Int, String> = mapOf(",
    ]
    for item_id in sorted(item_types):
        lines.append(f"        {item_id} to {kt(item_types[item_id])},")
    lines += [
        "    )",
        "",
        "    /** Category totals over the 0..ITEM_ID_MAX domain, for drift checks. */",
        "    val categoryCounts: Map<HnsItemCategory, Int> = mapOf(",
    ]
    for cat in CATEGORIES:
        lines.append(f"        HnsItemCategory.{cat} to {counts[cat]},")
    lines += [
        "    )",
        "",
        "    /** Every reviewed request-local rule name (implemented by HnsItemContextPolicy). */",
        "    val contextRuleNames: Set<String> = setOf(",
    ]
    for name in rule_names:
        lines.append(f"        {kt(name)},")
    lines += ["    )", "}", ""]
    audit_kt = "\n".join(lines)

    if args.check:
        if INVENTORY.read_text() != inventory or AUDIT_KT.read_text() != audit_kt:
            print("error: H&S item inventory or generated Kotlin audit differs from pinned source/review",
                  file=sys.stderr)
            sys.exit(1)
    else:
        INVENTORY.write_text(inventory)
        AUDIT_KT.write_text(audit_kt)
    families_by_cat = {cat: sorted(he for he, f in families.items() if f["category"] == cat) for cat in CATEGORIES}
    print(f"{len(rows)} pinned items, {len(families)} hold-effect families: {counts}")
    print(f"families by category: { {c: len(v) for c, v in families_by_cat.items()} }")
    print(f"{len(rule_names)} reviewed request-local rules")


if __name__ == "__main__":
    main()
