#!/usr/bin/env python3
"""H&S 2.0.5 differential damage oracle: corpus regeneration and ROM-free checks (issue #90).

Commands:

  check        ROM-free, upstream-free, toolchain-free validation of the committed corpus. Run by
               ./ci.sh test. Fails when the corpus is malformed, not canonical, not generated from the
               pinned commit, stale relative to the scenario matrix or the generated test source, when
               an existing hand-derived / ROM-observed fixture disagrees with the oracle, or when the
               known-divergence register is inconsistent.

  regenerate   Developer command. Exports the pinned H&S commit, builds its battle test runner with the
               ARM toolchain, measures every scenario over all 16 rolls and rewrites corpus.json.

  verify       Developer command. Same as regenerate but compares the result byte-for-byte with the
               committed corpus instead of writing it (use --order reversed to also prove that test
               order does not change any result).

  emit-sources Write the generated C test sources for review without building anything.

The trust chain is: pinned H&S battle code -> this oracle -> committed corpus.json -> the DualDex
QuickJS differential test (native/tests/test_hns_damage_oracle.c). Nothing here reads DualDex's
calculator, its bundle, @smogon/calc or Kotlin damage code.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import oracle_backend as backend  # noqa: E402
import oracle_matrix as matrix  # noqa: E402
import oracle_schema as schema  # noqa: E402

TOOL_DIR = Path(__file__).resolve().parent
CORPUS_PATH = TOOL_DIR / "corpus.json"
CROSSREF_PATH = TOOL_DIR / "fixture_crossref.json"
DIVERGENCE_PATH = TOOL_DIR / "known_divergences.json"
DEFAULT_TOOLCHAIN = Path.home() / "opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin"


def fail(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


# --------------------------------------------------------------------------------------------
# Hand-derived reference arithmetic (transcribed from native/tests/test_js_calc.c)
# --------------------------------------------------------------------------------------------

def _half_down(modifier: int, value: int) -> int:
    return (modifier * value + 2047) // 4096


def hand_rolls(level: int, bp: int, atk: int, dfn: int, stab: bool = False, effectiveness: float = 1.0,
               crit: bool = False, burn: bool = False, weather_mod: int = 0, screen_mod: int = 0,
               spread_mod: int = 0) -> list[int]:
    """The pre-existing independent H&S fixture arithmetic (hns_calc_rolls / Gap C4b spread case).

    This is the hand derivation the existing fixtures were written against. It is kept here only
    to prove, per mapped fixture, that the oracle reproduces that evidence; it is never used to
    produce corpus values.
    """
    base = bp * atk * (2 * level // 5 + 2) // dfn // 50 + 2
    if spread_mod:
        base = _half_down(spread_mod, base)
    if weather_mod:
        base = _half_down(weather_mod, base)
    if crit:
        base = _half_down(8192, base)
    out = []
    for r in range(85, 101):
        x = base * r // 100
        if stab:
            x = _half_down(6144, x)
        if effectiveness != 1.0:
            x = _half_down(int(effectiveness * 4096 + 0.5), x)
        if burn:
            x = _half_down(2048, x)
        if screen_mod:
            x = _half_down(screen_mod, x)
        if x == 0 and effectiveness > 0:
            x = 1
        out.append(x)
    return out


CROSSREF_KEYS = ("fixture", "scenario", "evidence", "relation", "handDerivation", "rolls", "romObservation", "note")
EVIDENCE_KINDS = ("hand-derived", "rom-observed", "upstream-test")
RELATIONS = ("exact-operands", "arithmetic-equivalent", "decomposed")


def check_crossref(entries_by_id: dict[str, dict]) -> int:
    doc = json.loads(CROSSREF_PATH.read_text())
    if not isinstance(doc, dict) or set(doc) != {"schemaVersion", "records"} or doc["schemaVersion"] != 1:
        fail("fixture_crossref.json: unexpected header")
    records = doc["records"]
    if not isinstance(records, list) or not records:
        fail("fixture_crossref.json: no records")
    seen = set()
    for rec in records:
        if not isinstance(rec, dict) or set(rec) != set(CROSSREF_KEYS):
            fail(f"fixture_crossref.json: bad record keys {sorted(rec) if isinstance(rec, dict) else rec!r}")
        key = (rec["fixture"], rec["scenario"])
        if key in seen:
            fail(f"fixture_crossref.json: duplicate record {key}")
        seen.add(key)
        if rec["evidence"] not in EVIDENCE_KINDS or rec["relation"] not in RELATIONS:
            fail(f"{key}: unknown evidence/relation")
        entry = entries_by_id.get(rec["scenario"])
        if entry is None:
            fail(f"{key}: scenario is not in the corpus")
        oracle = entry["rolls"]
        expected = rec["rolls"]
        if not isinstance(expected, list) or len(expected) != schema.ROLL_COUNT:
            fail(f"{key}: evidence must list all 16 rolls")
        derivation = rec["handDerivation"]
        if derivation is not None:
            if hand_rolls(**derivation) != expected:
                fail(f"{key}: recorded rolls do not follow the stated hand derivation {derivation}")
        elif rec["evidence"] == "hand-derived":
            fail(f"{key}: hand-derived evidence needs its derivation")
        if oracle != expected:
            fail(f"{key}: ORACLE DISAGREES WITH EXISTING EVIDENCE\n  evidence {expected}\n  oracle   {oracle}\n"
                 "  Do not update either side: investigate the discrepancy (see README).")
        rom = rec["romObservation"]
        if rom is not None:
            kind, value = rom.get("kind"), rom.get("damage")
            if kind == "roll" and value not in oracle:
                fail(f"{key}: ROM-observed damage {value} is not one of the oracle's rolls")
            if kind == "faint-capped" and not oracle[0] >= value:
                fail(f"{key}: ROM faint of {value} HP is not explained by every oracle roll")
            if kind not in ("roll", "faint-capped"):
                fail(f"{key}: unknown ROM observation kind {kind!r}")
    return len(records)


DIVERGENCE_KEYS = ("scenario", "issue", "summary", "calculatorRolls")


def validate_divergences(doc: dict, entries_by_id: dict[str, dict]) -> list[dict]:
    if not isinstance(doc, dict) or set(doc) != {"schemaVersion", "divergences"} or doc["schemaVersion"] != 2:
        fail("known_divergences.json: unexpected header")
    ids = set()
    for rec in doc["divergences"]:
        if not isinstance(rec, dict) or set(rec) != set(DIVERGENCE_KEYS):
            fail(f"known_divergences.json: bad record {rec!r}")
        if rec["scenario"] not in entries_by_id:
            fail(f"known_divergences.json: {rec['scenario']} is not a corpus scenario")
        if rec["scenario"] in ids:
            fail(f"known_divergences.json: duplicate {rec['scenario']}")
        if not str(rec["issue"]).startswith("https://github.com/Sonoran-Solutions/dualdex/issues/"):
            fail(f"known_divergences.json: {rec['scenario']} must link its tracking issue")
        if entries_by_id[rec["scenario"]]["scenario"]["id"] != rec["scenario"]:
            fail("known_divergences.json: internal id mismatch")
        rolls = rec["calculatorRolls"]
        if (not isinstance(rolls, list) or len(rolls) != schema.ROLL_COUNT or
                any(type(value) is not int or value < 0 or value > schema.MAX_MEASURABLE_DAMAGE for value in rolls)):
            fail(f"known_divergences.json: {rec['scenario']} must pin exactly {schema.ROLL_COUNT} integral calculator rolls")
        if rolls != sorted(rolls):
            fail(f"known_divergences.json: {rec['scenario']} calculator rolls must be non-decreasing")
        if rolls == entries_by_id[rec["scenario"]]["rolls"]:
            fail(f"known_divergences.json: {rec['scenario']} is no longer a calculator divergence")
        ids.add(rec["scenario"])
    if [r["scenario"] for r in doc["divergences"]] != sorted(ids):
        fail("known_divergences.json: records must be sorted by scenario id")
    return doc["divergences"]


def load_divergences(entries_by_id: dict[str, dict]) -> list[dict]:
    return validate_divergences(json.loads(DIVERGENCE_PATH.read_text()), entries_by_id)


def cmd_check(_args: argparse.Namespace) -> None:
    text = CORPUS_PATH.read_text()
    try:
        doc = schema.load_corpus_text(text)
    except (schema.SchemaError, json.JSONDecodeError) as exc:
        fail(f"corpus.json: {exc}")
    scenarios = matrix.build_scenarios()
    try:
        schema.validate_scenarios(scenarios)
    except schema.SchemaError as exc:
        fail(f"scenario matrix: {exc}")
    committed = [e["scenario"] for e in doc["entries"]]
    if committed != scenarios:
        added = sorted({s["id"] for s in scenarios} - {s["id"] for s in committed})
        removed = sorted({s["id"] for s in committed} - {s["id"] for s in scenarios})
        changed = sorted(s["id"] for s, c in zip(scenarios, committed) if s != c and s["id"] == c["id"])
        fail(f"corpus.json is stale relative to oracle_matrix.py (added {added[:5]}, removed {removed[:5]}, "
             f"changed {changed[:5]}); regenerate it with the pinned oracle")
    source_hash = backend.sources_sha256(backend.render_sources(scenarios))
    if doc["provenance"]["generator"]["testSourceSha256"] != source_hash:
        fail("corpus.json was produced from a different generated test source; regenerate it")
    by_id = {e["scenario"]["id"]: e for e in doc["entries"]}
    xrefs = check_crossref(by_id)
    divergences = load_divergences(by_id)
    modelled = sum(1 for s in scenarios if s["surface"] == "modelled")
    print(f"hns-damage-oracle: corpus OK: {len(scenarios)} scenarios ({modelled} modelled, "
          f"{len(scenarios) - modelled} engine-only), {xrefs} fixture cross-references reproduced, "
          f"{len(divergences)} registered divergence(s); pinned {schema.HNS_PINNED_COMMIT}")


# --------------------------------------------------------------------------------------------
# Regeneration
# --------------------------------------------------------------------------------------------

def default_work_dir() -> Path:
    base = os.environ.get("XDG_CACHE_HOME") or str(Path.home() / ".cache")
    return Path(base) / "dualdex" / "hns-damage-oracle"


def produce_corpus(args: argparse.Namespace) -> str:
    upstream = Path(args.upstream_dir).resolve()
    toolchain = Path(args.toolchain_bin).resolve()
    work = Path(args.work_dir).resolve()
    backend.verify_upstream(upstream)
    scenarios = matrix.build_scenarios()
    schema.validate_scenarios(scenarios)
    canonical_sources = backend.render_sources(scenarios)
    run_order = list(reversed(scenarios)) if args.order == "reversed" else scenarios
    run_sources = backend.render_sources(run_order)
    backend.export_worktree(upstream, work)
    toolchain_id = backend.toolchain_identity(toolchain)
    print(f"hns-damage-oracle: building and running {len(scenarios)} scenarios x {schema.ROLL_COUNT} rolls "
          f"in {work} ({args.order} order)", file=sys.stderr)
    output = backend.build_and_run(work, toolchain, run_sources, args.jobs)
    if args.keep_log:
        Path(args.keep_log).write_text(output)
    ids = [s["id"] for s in scenarios]
    records = backend.parse_runner_output(output, ids)
    entries = [backend.assemble_entry(s, records[s["id"]]) for s in scenarios]
    doc = {
        "schemaVersion": schema.SCHEMA_VERSION,
        "rollOrder": schema.ROLL_ORDER,
        "provenance": {
            "hnsUpstream": {"repository": schema.HNS_REPOSITORY, "commit": schema.HNS_PINNED_COMMIT,
                            "tree": schema.HNS_PINNED_TREE},
            "backend": backend.backend_provenance(work),
            "toolchain": toolchain_id,
            "generator": {"tool": "tools/hns-damage-oracle", "version": schema.ORACLE_TOOL_VERSION,
                          "testSourceSha256": backend.sources_sha256(canonical_sources)},
        },
        "entries": entries,
    }
    schema.validate_corpus(doc)
    return schema.canonical_dumps(doc)


def cmd_regenerate(args: argparse.Namespace) -> None:
    try:
        text = produce_corpus(args)
    except (backend.OracleError, schema.SchemaError) as exc:
        fail(str(exc))
    CORPUS_PATH.write_text(text)
    print(f"wrote {CORPUS_PATH.relative_to(TOOL_DIR.parent.parent)}")


def cmd_verify(args: argparse.Namespace) -> None:
    try:
        text = produce_corpus(args)
    except (backend.OracleError, schema.SchemaError) as exc:
        fail(str(exc))
    if text != CORPUS_PATH.read_text():
        fail("regenerated corpus differs from the committed corpus.json")
    print("hns-damage-oracle: regenerated corpus is byte-identical to the committed corpus.json")


def cmd_emit_sources(args: argparse.Namespace) -> None:
    out = Path(args.out)
    scenarios = matrix.build_scenarios()
    schema.validate_scenarios(scenarios)
    if out.exists():
        shutil.rmtree(out)
    for name, text in backend.render_sources(scenarios).items():
        target = out / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    print(f"wrote generated oracle tests under {out}")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("check", help="ROM-free validation of the committed corpus (CI)")
    for name in ("regenerate", "verify"):
        p = sub.add_parser(name)
        p.add_argument("--upstream-dir", default=os.environ.get("HNS_UPSTREAM_DIR"),
                       required=os.environ.get("HNS_UPSTREAM_DIR") is None,
                       help="checkout of the pinned H&S commit (default: $HNS_UPSTREAM_DIR)")
        p.add_argument("--toolchain-bin", default=os.environ.get("HNS_ARM_TOOLCHAIN_BIN", str(DEFAULT_TOOLCHAIN)),
                       help="directory containing arm-none-eabi-gcc (Arm GNU Toolchain 13.2.rel1)")
        p.add_argument("--work-dir", default=str(default_work_dir()),
                       help="scratch directory for the exported pinned tree and build (never committed)")
        p.add_argument("--jobs", type=int, default=os.cpu_count() or 4)
        p.add_argument("--order", choices=("canonical", "reversed"), default="canonical",
                       help="test execution order; results must not depend on it")
        p.add_argument("--keep-log", help="also save the raw runner output to this file")
    p = sub.add_parser("emit-sources")
    p.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    {"check": cmd_check, "regenerate": cmd_regenerate, "verify": cmd_verify,
     "emit-sources": cmd_emit_sources}[args.command](args)


if __name__ == "__main__":
    main()
