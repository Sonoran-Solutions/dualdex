#!/usr/bin/env python3
"""Generate the exact H&S 2.0.5 held-item identity catalogue from pinned source.

Issue #9, Gap C3: the calculator must classify held items by the pinned build's own
numeric `enum Item` identity, never by a generic expansion-derived item table. This
generator is the authoritative derivation of:

  * the exact numeric item domain (`ITEM_NONE = 0` .. `ITEMS_COUNT - 1`, plus the
    out-of-domain `ITEM_FIELD_ARROW` alias);
  * the canonical source symbol and display name of every item;
  * each item's `holdEffect` and `holdEffectParam`, as compiled into
    `gItemsInfo[]` from the build's own configuration macros.

Sources (both preprocessed with the build's own configuration):
  * include/constants/items.h : `enum __attribute__((packed)) Item`
  * src/data/items.h          : `const struct ItemInfo gItemsInfo[]`

Pinned upstream: PokemonHnS-Development/pokehns-expansion
commit 1f42b74dff0e9fe942419845d040663dd829a973 (tag Release-v2.0.5).

The committed Kotlin artifact is self-contained, so ordinary `./ci.sh test` never
needs the upstream checkout. Re-verification against pinned upstream belongs to
`./ci.sh source-check` via `--verify`.

Usage:
  python3 tools/hns-items/generate_hns_items.py \
      --upstream-dir <pokehns-expansion checkout> [--cpp-bin <arm-none-eabi-cpp>] [--verify]
"""

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
PINNED_TAG = "Release-v2.0.5"

ITEMS_ENUM_HEADER = "include/constants/items.h"
# Both the enum and the table are preprocessed through src/item.c, the translation
# unit that includes global.h and every constant/config header their initializers
# depend on. Preprocessing include/constants/items.h alone would evaluate the
# `IS_HNS`-gated FOREACH_HM list with IS_HNS unset and derive the wrong HM identity.
ITEMS_TABLE_SOURCE = "src/item.c"

DEFAULT_TARGET_FILE = "app/src/main/java/com/dualdex/pokemon/hns/Hns205ItemCatalogue.kt"

# Names the enum parser accepts as members. The item enum shares the ITEM_ prefix
# with a few non-item enumerations (e.g. Pocket); the parser is anchored to the
# `enum Item` body, so this only guards against a malformed declaration. The three
# `*_INDEX` members are bookkeeping anchors for the mail/berry sub-ranges, exactly
# like the ABILITIES_COUNT_GEN* anchors of the ability enum; they resolve like any
# other member and are excluded from the identity catalogue.
ITEM_ENUM_MEMBER_NAME_RE = re.compile(
    r"^(ITEM_[A-Za-z0-9_]+|ITEMS_COUNT|FIRST_MAIL_INDEX|FIRST_BERRY_INDEX|LAST_BERRY_INDEX)$"
)

ITEM_ENUM_BOOKKEEPING = {
    "ITEMS_COUNT", "ITEM_FIELD_ARROW",
    "FIRST_MAIL_INDEX", "FIRST_BERRY_INDEX", "LAST_BERRY_INDEX",
}

CHUNK_SIZE = 160


class GenerationError(Exception):
    pass


def find_upstream_dir(provided):
    if provided:
        if os.path.isdir(provided):
            return os.path.abspath(provided)
        raise GenerationError(f"Specified upstream directory does not exist: {provided}")
    candidates = [
        os.environ.get("HNS_UPSTREAM_DIR"),
        "upstream-hns/pokehns-expansion",
        os.path.expanduser("~/Projects/upstream-hns/pokehns-expansion"),
        "../upstream-hns/pokehns-expansion",
    ]
    for candidate in candidates:
        if candidate and os.path.isdir(candidate):
            return os.path.abspath(candidate)
    raise GenerationError(
        "Could not locate the pinned pokehns-expansion checkout. "
        "Set HNS_UPSTREAM_DIR or pass --upstream-dir."
    )


def find_cpp_bin(provided):
    if provided:
        return provided
    candidates = [
        os.environ.get("ARM_CPP"),
        "arm-none-eabi-cpp",
        os.path.expanduser(
            "~/opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin/arm-none-eabi-cpp"
        ),
        "/opt/devkitpro/devkitARM/bin/arm-none-eabi-cpp",
        "/usr/bin/arm-none-eabi-cpp",
    ]
    for candidate in candidates:
        if not candidate:
            continue
        if os.path.isabs(candidate):
            if os.path.exists(candidate):
                return candidate
        elif subprocess.run(
            ["which", candidate], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        ).returncode == 0:
            return candidate
    raise GenerationError(
        "Could not locate arm-none-eabi-cpp. Set ARM_CPP or pass --cpp-bin."
    )


def verify_git_commit(upstream_dir):
    head = subprocess.run(
        ["git", "-C", upstream_dir, "rev-parse", "HEAD"],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    if head != PINNED_COMMIT:
        raise GenerationError(
            f"Upstream checkout is at {head}, expected the pinned {PINNED_COMMIT} ({PINNED_TAG})"
        )
    dirty = subprocess.run(
        ["git", "-C", upstream_dir, "status", "--porcelain", "--untracked-files=no"],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    if dirty:
        raise GenerationError(
            "Upstream checkout has tracked working-tree changes; refusing to derive "
            "item identity from a dirty checkout"
        )


def run_cpp(cpp_bin, upstream_dir, src_rel_path):
    src_abs = os.path.join(upstream_dir, src_rel_path)
    inc_abs = os.path.join(upstream_dir, "include")
    if not os.path.exists(src_abs):
        raise GenerationError(f"Source file not found: {src_abs}")
    cmd = [
        cpp_bin, "-iquote", inc_abs,
        "-DMODERN=1", "-DTESTING=0", "-DPOKEMON_HNS", "-std=gnu17",
        src_abs,
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise GenerationError(f"Preprocessor failed on {src_rel_path}:\n{proc.stderr}")
    return proc.stdout


def _split_top_level_commas(body):
    """Split a C initializer body on commas that are not nested in (), [] or {}."""
    parts = []
    current = []
    depth = 0
    for ch in body:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    if current:
        parts.append("".join(current))
    return parts


def parse_item_enum(enum_out):
    """Resolve the preprocessed `enum Item` to a {name: value} map and the count.

    The item enum mixes explicit integer assignments, aliases of already-defined
    members, implicit previous+1 members, and preprocessor macro expansions that
    emit many comma-separated declarations on a single physical line (the TM/HM
    lists). The body is therefore split on top-level commas before individual
    declarations are parsed. An assignment the parser cannot resolve is a hard
    error: a value is never invented from the running counter.
    """
    start = enum_out.find("enum __attribute__((packed)) Item")
    if start == -1:
        start = enum_out.find("enum Item")
    if start == -1:
        raise GenerationError("Could not find `enum Item` in preprocessed include/constants/items.h")
    brace = enum_out.find("{", start)
    if brace == -1:
        raise GenerationError("Could not find the opening brace of `enum Item`")
    depth = 1
    pos = brace + 1
    while pos < len(enum_out) and depth > 0:
        if enum_out[pos] == "{":
            depth += 1
        elif enum_out[pos] == "}":
            depth -= 1
        pos += 1
    if depth != 0:
        raise GenerationError("Unterminated `enum Item` body")
    body = enum_out[brace + 1:pos - 1]
    # Drop preprocessor line markers, which are line-oriented and would otherwise
    # be glued to the declaration that follows them once the body is split on
    # top-level commas (the macro-expanded TM/HM lists produce such markers).
    body = re.sub(r"(?m)^\s*#.*$", "", body)

    member_ids = {}
    next_implicit = None
    for raw in _split_top_level_commas(body):
        decl = raw.strip()
        if not decl:
            continue
        # Preprocessor line markers and stray braces cannot appear here, but a
        # declaration may still carry a `/*...*/` or `//...` comment.
        decl = re.sub(r"/\*.*?\*/", " ", decl, flags=re.S)
        decl = decl.split("//")[0].strip()
        if not decl:
            continue
        name, eq, raw_value = decl.partition("=")
        name = name.strip()
        if not ITEM_ENUM_MEMBER_NAME_RE.match(name):
            raise GenerationError(f"Unsupported item enum declaration: {decl!r}")
        value = raw_value.strip() if eq else None
        if value is None:
            if next_implicit is None:
                raise GenerationError(
                    f"Item enum member {name} has an implicit value but no previous value"
                )
            resolved = next_implicit
        elif re.fullmatch(r"[0-9]+", value):
            resolved = int(value)
        elif re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value):
            if value not in member_ids:
                raise GenerationError(
                    f"Unresolvable item enum assignment {name} = {value}; refusing to "
                    "invent a sequential ID"
                )
            resolved = member_ids[value]
        else:
            raise GenerationError(
                f"Unsupported item enum assignment {name} = {value!r}; refusing to "
                "invent a sequential ID"
            )
        if name in member_ids and member_ids[name] != resolved:
            raise GenerationError(f"Conflicting item enum values for {name}")
        member_ids[name] = resolved
        next_implicit = resolved + 1

    if member_ids.get("ITEM_NONE") != 0:
        raise GenerationError(f"ITEM_NONE must be 0, got {member_ids.get('ITEM_NONE')}")
    count = member_ids.get("ITEMS_COUNT")
    if count is None:
        raise GenerationError("ITEMS_COUNT is missing from `enum Item`")
    highest_identity = max(
        v for k, v in member_ids.items() if k not in ITEM_ENUM_BOOKKEEPING
    )
    if count != highest_identity + 1:
        raise GenerationError("ITEMS_COUNT does not follow the highest item identity")
    return member_ids, count


def extract_designated_entries(output, table_start, key_pattern, table_name):
    table_open = output.find("{", table_start)
    if table_open == -1:
        raise GenerationError(f"Could not find opening brace for {table_name}")
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
                    raise GenerationError(f"Unclosed initializer for {table_name} entry {key}")
                entries.append((key, output[brace_start + 1:body_pos - 1]))
                pos = body_pos
                continue
        if output[pos] == "{":
            depth += 1
        elif output[pos] == "}":
            depth -= 1
        pos += 1
    return entries


def _eval_simple_expr(expr, symbols=None):
    """Evaluate a preprocessor-produced integer or simple ternary expression.

    A surviving identifier is resolved through [symbols] (the enum values lifted
    from the same preprocessed translation unit); an unresolved identifier is a
    hard error, never a guessed value.
    """
    symbols = symbols or {}
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
    if re.fullmatch(r"-?[0-9]+", expr):
        return int(expr)
    if re.fullmatch(r"0[xX][0-9A-Fa-f]+", expr):
        return int(expr, 16)
    if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", expr):
        if expr in symbols:
            return symbols[expr]
        raise GenerationError(f"Cannot resolve item table symbol: {expr!r}")
    if "?" in expr:
        q_pos = expr.find("?")
        colon_pos = expr.rfind(":")
        if q_pos != -1 and colon_pos > q_pos:
            cond = expr[:q_pos].strip()
            true_s = expr[q_pos + 1:colon_pos].strip()
            false_s = expr[colon_pos + 1:].strip()
            py_cond = cond.replace("&&", " and ").replace("||", " or ").replace("!", " not ")
            # Only the numeric comparison forms appear in this table.
            result = bool(eval(py_cond, {"__builtins__": {}}, {}))
            return _eval_simple_expr(true_s if result else false_s, symbols)
    raise GenerationError(f"Cannot evaluate item table expression: {expr!r}")


def parse_all_enum_symbols(output):
    """Best-effort {NAME: value} map for every enum body in a preprocessed TU.

    Only used to resolve symbolic `holdEffectParam` initializers (e.g. `TYPE_FIRE`)
    to the exact numeric value the build's own compiler would use. An enum whose
    body contains a declaration this parser cannot understand is skipped as a
    whole rather than resolved partially; a needed symbol that is not present then
    fails closed at expression evaluation.
    """
    symbols = {}
    for match in re.finditer(r"\benum\b[^{;]*\{", output):
        brace = match.end() - 1
        depth = 1
        pos = brace + 1
        while pos < len(output) and depth > 0:
            if output[pos] == "{":
                depth += 1
            elif output[pos] == "}":
                depth -= 1
            pos += 1
        if depth != 0:
            continue
        body = re.sub(r"(?m)^\s*#.*$", "", output[brace + 1:pos - 1])
        local = {}
        next_implicit = None
        ok = True
        for raw in _split_top_level_commas(body):
            decl = re.sub(r"/\*.*?\*/", " ", raw, flags=re.S).split("//")[0].strip()
            if not decl:
                continue
            name, eq, raw_value = decl.partition("=")
            name = name.strip()
            value = raw_value.strip() if eq else None
            if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
                ok = False
                break
            try:
                if value is None:
                    if next_implicit is None:
                        ok = False
                        break
                    resolved = next_implicit
                elif re.fullmatch(r"-?[0-9]+", value):
                    resolved = int(value)
                elif re.fullmatch(r"0[xX][0-9A-Fa-f]+", value):
                    resolved = int(value, 16)
                elif value in local:
                    resolved = local[value]
                else:
                    ok = False
                    break
            except GenerationError:
                ok = False
                break
            local[name] = resolved
            next_implicit = resolved + 1
        if ok:
            symbols.update(local)
    return symbols


def _field_value(body, field):
    """The text of one designated initializer field, up to the next top-level comma.

    Scans with paren/bracket/brace depth so a nested initializer or a two-argument
    macro (e.g. `COMPOUND_STRING_SIZE_LIMIT("X", LIMIT)`) does not terminate the
    field early, and a final field with no trailing comma is captured too.
    """
    match = re.search(r"\.%s\s*=\s*" % re.escape(field), body)
    if not match:
        return None
    start = match.end()
    depth = 0
    i = start
    while i < len(body):
        ch = body[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            if depth == 0:
                break
            depth -= 1
        elif ch == "," and depth == 0:
            break
        i += 1
    return body[start:i].strip()


def extract_item_table(table_out, symbols):
    # Anchor on the DEFINITION (`gItemsInfo[] =`), never the `extern` declaration,
    # so the brace walker starts at the table body.
    match = re.search(r"gItemsInfo\s*\[\s*\]\s*=", table_out)
    if not match:
        raise GenerationError("Could not find the gItemsInfo definition in preprocessed src/item.c")
    start = match.start()
    entries = extract_designated_entries(table_out, start, r"ITEM_[A-Za-z0-9_]+", "gItemsInfo")
    table = {}
    for symbol, body in entries:
        if symbol in table:
            raise GenerationError(f"Duplicate gItemsInfo entry for {symbol}")
        name_value = _field_value(body, "name")
        source_name = None
        if name_value is not None:
            name_match = re.search(r'"((?:[^"\\]|\\.)*)"', name_value)
            if name_match:
                source_name = name_match.group(1)
            elif not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name_value):
                raise GenerationError(f"Unrecognised .name for {symbol}: {name_value!r}")
        hold_effect_match = re.search(r"\.holdEffect\s*=\s*([A-Za-z_][A-Za-z0-9_]*)", body)
        hold_effect = hold_effect_match.group(1) if hold_effect_match else "HOLD_EFFECT_NONE"
        param_value = _field_value(body, "holdEffectParam")
        if param_value is None:
            hold_effect_param = 0
        else:
            hold_effect_param = _eval_simple_expr(param_value, symbols)
        table[symbol] = {
            "symbol": symbol,
            "source_name": source_name,
            "hold_effect": hold_effect,
            "hold_effect_param": hold_effect_param,
        }
    return table


def build_catalogue(enum_ids, count, table):
    catalogue = []
    for item_id in range(count):
        matching = [s for s, data in table.items() if enum_ids.get(s) == item_id]
        if not matching:
            raise GenerationError(
                f"Item ID {item_id} has no gItemsInfo entry; the table is not contiguous"
            )
        if len(matching) > 1:
            raise GenerationError(
                f"Item ID {item_id} has multiple gItemsInfo entries: {sorted(matching)}"
            )
        symbol = matching[0]
        data = table[symbol]
        catalogue.append({
            "id": item_id,
            "symbol": symbol,
            "source_name": data["source_name"],
            "hold_effect": data["hold_effect"],
            "hold_effect_param": data["hold_effect_param"],
        })

    unexpected = sorted(set(table) - set(enum_ids))
    if unexpected:
        raise GenerationError(f"gItemsInfo entries not present in `enum Item`: {unexpected}")
    out_of_domain = sorted(
        s for s, i in enum_ids.items()
        if i >= count and s not in ITEM_ENUM_BOOKKEEPING
    )
    if out_of_domain:
        raise GenerationError(f"Item constants outside the ITEMS_COUNT domain: {out_of_domain}")
    return catalogue


def normalize_name(raw):
    return re.sub(r"\s+", " ", raw.strip().lower().replace("-", " ")).strip()


def generate_kotlin(catalogue, count):
    id_max = count - 1
    name_counts = {}
    for entry in catalogue:
        if entry["source_name"]:
            name_counts[normalize_name(entry["source_name"])] = (
                name_counts.get(normalize_name(entry["source_name"]), 0) + 1
            )
    ambiguous_names = sorted(k for k, v in name_counts.items() if v > 1)

    lines = []
    lines.append("package com.dualdex.pokemon.hns")
    lines.append("")
    lines.append("/**")
    lines.append(" * GENERATED FILE - do not edit by hand.")
    lines.append(" *")
    lines.append(" * Exact H&S 2.0.5 held-item identity catalogue (issue #9, Gap C3).")
    lines.append(" *")
    lines.append(" * Derived from the pinned upstream source by")
    lines.append(" *   tools/hns-items/generate_hns_items.py")
    lines.append(" *")
    lines.append(" * Pinned upstream: PokemonHnS-Development/pokehns-expansion")
    lines.append(f" *   commit {PINNED_COMMIT} (tag {PINNED_TAG})")
    lines.append(" * Sources: include/constants/items.h (enum Item) and")
    lines.append(" *          src/data/items.h (gItemsInfo[]).")
    lines.append(" *")
    lines.append(" * This is IDENTITY and STATIC HOLD-EFFECT data only. It proves what")
    lines.append(" * numeric item ID this exact build calls what, and which holdEffect /")
    lines.append(" * holdEffectParam the build's own table assigns. It does NOT model any")
    lines.append(" * item's battle behaviour and is never evidence that the damage engine")
    lines.append(" * implements the item.")
    lines.append(" */")
    lines.append("object Hns205ItemCatalogue {")
    lines.append(f"    const val ITEM_COUNT: Int = {count}")
    lines.append(f"    const val ITEM_ID_MAX: Int = {id_max}")
    lines.append("")
    lines.append("    private val byId = HashMap<Int, HnsItemData>()")
    lines.append("    private val bySymbol = HashMap<String, HnsItemData>()")
    lines.append("    private val byNormalizedName = HashMap<String, HnsItemData?>()")
    lines.append("")
    lines.append("    /** The exact item identity for a numeric build item ID, or null. */")
    lines.append("    fun get(id: Int): HnsItemData? = byId[id]")
    lines.append("")
    lines.append("    /** The exact item identity for an `ITEM_*` source symbol, or null. */")
    lines.append("    fun getBySymbol(symbol: String): HnsItemData? = bySymbol[symbol]")
    lines.append("")
    lines.append("    /**")
    lines.append("     * Resolves a user-facing/source item name to its exact build identity.")
    lines.append("     *")
    lines.append("     * Matching is case-insensitive and ignores hyphens and repeated spaces.")
    lines.append("     * A name shared by more than one ID resolves to null (fail closed), and")
    lines.append("     * a name not present in this exact build resolves to null as well.")
    lines.append("     */")
    lines.append("    fun getByName(name: String?): HnsItemData? {")
    lines.append("        if (name.isNullOrBlank()) return null")
    lines.append("        return byNormalizedName[normalize(name)]")
    lines.append("    }")
    lines.append("")
    lines.append("    internal fun normalize(raw: String): String =")
    lines.append("        raw.trim().lowercase().replace('-', ' ').replace(Regex(\"\\\\s+\"), \" \")")
    lines.append("")
    lines.append("    private fun register(")
    lines.append("        id: Int,")
    lines.append("        symbol: String,")
    lines.append("        sourceName: String?,")
    lines.append("        holdEffect: String,")
    lines.append("        holdEffectParam: Int")
    lines.append("    ) {")
    lines.append("        val data = HnsItemData(id, symbol, sourceName, holdEffect, holdEffectParam)")
    lines.append("        byId[id] = data")
    lines.append("        bySymbol[symbol] = data")
    lines.append("        if (sourceName != null) {")
    lines.append("            val key = normalize(sourceName)")
    lines.append("            // A name that is not unique in this build is never resolved.")
    lines.append("            if (byNormalizedName.containsKey(key)) {")
    lines.append("                byNormalizedName[key] = null")
    lines.append("            } else {")
    lines.append("                byNormalizedName[key] = data")
    lines.append("            }")
    lines.append("        }")
    lines.append("    }")
    lines.append("")

    chunks = [catalogue[i:i + CHUNK_SIZE] for i in range(0, len(catalogue), CHUNK_SIZE)]
    for i, chunk in enumerate(chunks):
        lines.append(f"    private fun registerChunk{i + 1}() {{")
        for entry in chunk:
            name = entry["source_name"]
            name_literal = "null" if name is None else '"' + name.replace("\\", "\\\\").replace('"', '\\"') + '"'
            symbol_literal = '"' + entry["symbol"] + '"'
            effect_literal = '"' + entry["hold_effect"] + '"'
            lines.append(
                f"        register({entry['id']}, {symbol_literal}, {name_literal}, "
                f"{effect_literal}, {entry['hold_effect_param']})"
            )
        lines.append("    }")
        lines.append("")

    lines.append("    init {")
    for i in range(len(chunks)):
        lines.append(f"        registerChunk{i + 1}()")
    lines.append("    }")
    lines.append("")
    lines.append(f"    /** Names shared by more than one ID in this build (never resolved): {len(ambiguous_names)}. */")
    lines.append("    internal val ambiguousNames: Set<String> = setOf(")
    for name in ambiguous_names:
        lines.append(f'        "{name}",')
    lines.append("    )")
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-dir", default=None)
    parser.add_argument("--cpp-bin", default=None)
    parser.add_argument("--out-file", default=DEFAULT_TARGET_FILE)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()

    try:
        upstream = find_upstream_dir(args.upstream_dir)
        cpp_bin = find_cpp_bin(args.cpp_bin)
        print(f"Upstream repository: {upstream}")
        print(f"Preprocessor:        {cpp_bin}")
        verify_git_commit(upstream)

        itemc_out = run_cpp(cpp_bin, upstream, ITEMS_TABLE_SOURCE)

        enum_ids, count = parse_item_enum(itemc_out)
        print(f"Extracted enum Item domain: ITEMS_COUNT={count}")

        symbols = parse_all_enum_symbols(itemc_out)
        table = extract_item_table(itemc_out, symbols)
        print(f"Extracted gItemsInfo entries: {len(table)}")

        catalogue = build_catalogue(enum_ids, count, table)
        kotlin = generate_kotlin(catalogue, count)
    except GenerationError as exc:
        print(f"error: {exc}", file=sys.stderr)
        sys.exit(1)

    output = Path(args.out_file)
    if args.verify:
        if not output.is_file():
            print(f"error: {output} does not exist; run without --verify first", file=sys.stderr)
            sys.exit(1)
        if output.read_text() != kotlin:
            print(
                f"error: {output} does not match a fresh generation from {upstream}",
                file=sys.stderr,
            )
            sys.exit(1)
        print(f"  item catalogue verified against {upstream} @ {PINNED_COMMIT}")
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(kotlin)
        print(f"  wrote {output}")


if __name__ == "__main__":
    main()
