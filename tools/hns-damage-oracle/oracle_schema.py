"""Scenario and golden-corpus schema for the H&S 2.0.5 differential damage oracle (issue #90).

This module is deliberately self-contained (standard library only) and shares no code with the
DualDex calculator under test (`tools/calc-bundler/entry.js`, `calc_bundle.js`, `@smogon/calc` or the
Kotlin damage code). It defines:

* the *scenario* format: the authoritative battle operands a single measured hit depends on, named by
  pinned H&S symbols (``SPECIES_*``, ``MOVE_*``, ``ABILITY_*``, ``ITEM_*``) plus human-readable labels;
* the *observation* format: what the pinned H&S battle engine reported about that hit (numeric IDs,
  battle types, base stats, move type/power/category, target count, badge-boost verdicts);
* the *corpus* format: provenance plus one ``{scenario, observed, rolls}`` entry per scenario;
* strict validation (fail closed on anything unexpected) and a canonical, byte-deterministic
  serialisation with no timestamps or machine paths.

Roll order is fixed: ``rolls[k]`` is the damage dealt with the random factor ``(85 + k)%``. The pinned
engine computes ``dmg *= 100 - RandomUniform(RNG_DAMAGE_MODIFIER, 0, 15)``, so ``rolls[k]`` is the
hit measured with ``WITH_RNG(RNG_DAMAGE_MODIFIER, 15 - k)``. Index 0 is the minimum roll, 15 the maximum.
"""

from __future__ import annotations

import json
import re
from typing import Any

SCHEMA_VERSION = 1
ROLL_COUNT = 16

HNS_REPOSITORY = "PokemonHnS-Development/pokehns-expansion"
HNS_PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
HNS_PINNED_TREE = "586946f21e9322e8d837654d9e07cf6b8239feed"

ORACLE_BACKEND_KIND = "pinned-expansion-battle-test-runner"
ORACLE_TOOL_VERSION = 2

ROLL_ORDER = (
    "rolls[k] is the damage at random factor (85+k)%, i.e. the pinned hit measured with "
    "WITH_RNG(RNG_DAMAGE_MODIFIER, 15-k); index 0 = minimum, 15 = maximum"
)

# The defender (and, in Doubles, both defending battlers) are given this much HP so that no roll
# can be capped by fainting; a measured damage at or above it is rejected as saturated.
MAX_MEASURABLE_DAMAGE = 30000

TYPE_NAMES = (
    "Normal", "Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel",
    "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark", "Fairy",
)

ID_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
MAX_ID_LENGTH = 60
SYMBOL_RES = {
    "species": re.compile(r"^SPECIES_[A-Z0-9_]+$"),
    "move": re.compile(r"^MOVE_[A-Z0-9_]+$"),
    "ability": re.compile(r"^ABILITY_[A-Z0-9_]+$"),
    "item": re.compile(r"^ITEM_[A-Z0-9_]+$"),
}
TAG_RE = re.compile(r"^[a-z0-9]+(?:[-:.][a-z0-9]+)*$")
LABEL_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9 .'\-]*[A-Za-z0-9.]$|^[A-Za-z0-9]$")

SCENARIO_KEYS = (
    "id", "tags", "surface", "format", "attackerSide", "rules", "badges",
    "attacker", "defender", "move", "crit", "field", "doubles", "expect",
)
RULE_KEYS = ("fairyTypes", "optionStyle")
OPTION_STYLES = ("perMoveSplit", "typeBased")
SURFACES = ("modelled", "engine-only")
FORMATS = ("singles", "doubles")
SIDES = ("player", "opponent")
STATUSES = ("none", "burn", "poison")
WEATHERS = ("none", "rain", "sun")
EXPECTS = ("damage", "immune")
BATTLER_KEYS = (
    "species", "speciesLabel", "level", "stats", "ability", "abilityLabel",
    "item", "itemLabel", "status", "stages",
)
STAT_KEYS = ("maxHp", "hp", "attack", "defense", "spAttack", "spDefense", "speed")
ATTACKER_STAGE_KEYS = ("attack", "spAttack")
DEFENDER_STAGE_KEYS = ("defense", "spDefense")
FIELD_KEYS = ("weather", "reflect", "lightScreen")
DOUBLES_KEYS = ("defenderPartner",)
DEFENDER_PARTNER_STATES = ("present", "fainted")

OBSERVED_KEYS = ("attacker", "defender", "move", "targetCount")
OBSERVED_BATTLER_KEYS = ("speciesId", "types", "baseStats", "abilityId", "itemId", "hpAtHit", "badgeBoosts")
BASE_STAT_KEYS = ("hp", "attack", "defense", "spAttack", "spDefense", "speed")
BADGE_BOOST_KEYS = ("attack", "defense", "spAttack", "spDefense")
OBSERVED_MOVE_KEYS = ("id", "type", "power", "category", "target")
CATEGORIES = ("physical", "special")
MOVE_TARGETS = ("selected", "both", "foesAndAlly", "other")

ENTRY_KEYS = ("scenario", "observed", "rolls")
CORPUS_KEYS = ("schemaVersion", "rollOrder", "provenance", "entries")
PROVENANCE_KEYS = ("hnsUpstream", "backend", "toolchain", "generator")
UPSTREAM_KEYS = ("repository", "commit", "tree")
BACKEND_KEYS = ("kind", "build", "runner", "mgbaRomTestSha256", "harnessPatches", "prunedUpstreamTests")
TOOLCHAIN_KEYS = ("gcc", "gccSha256", "cc1Sha256")
GENERATOR_KEYS = ("tool", "version", "testSourceSha256")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

# Anything that looks like a machine path or a wall-clock timestamp must never be committed: the
# corpus has to be byte-identical wherever and whenever it is regenerated.
FORBIDDEN_ARTIFACT_RES = (
    re.compile(r"/home/|/tmp/|/Users/|/root/|/opt/|[A-Za-z]:\\\\"),
    re.compile(r"\b(19|20)\d\d-[01]\d-[0-3]\d[T ][0-2]\d:[0-5]\d"),
)


class SchemaError(ValueError):
    """Raised when a scenario, observation or corpus violates the schema."""


def _fail(path: str, message: str) -> None:
    raise SchemaError(f"{path}: {message}")


def _require_keys(obj: Any, keys: tuple[str, ...], path: str) -> None:
    if not isinstance(obj, dict):
        _fail(path, f"expected an object, got {type(obj).__name__}")
    missing = [k for k in keys if k not in obj]
    extra = sorted(k for k in obj if k not in keys)
    if missing:
        _fail(path, f"missing field(s) {missing}")
    if extra:
        _fail(path, f"unknown field(s) {extra}")


def _require_bool(value: Any, path: str) -> None:
    if not isinstance(value, bool):
        _fail(path, f"expected a boolean, got {value!r}")


def _require_int(value: Any, path: str, lo: int, hi: int) -> None:
    # bool is an int subclass in Python; a JSON true/false is never a valid integer operand.
    if isinstance(value, bool) or not isinstance(value, int):
        _fail(path, f"expected an integer, got {value!r}")
    if not lo <= value <= hi:
        _fail(path, f"{value} outside [{lo}, {hi}]")


def _require_enum(value: Any, allowed: tuple[str, ...], path: str) -> None:
    if value not in allowed:
        _fail(path, f"{value!r} is not one of {list(allowed)}")


def _require_label(value: Any, path: str) -> None:
    if not isinstance(value, str) or not LABEL_RE.match(value):
        _fail(path, f"invalid label {value!r}")


def _require_symbol(value: Any, kind: str, path: str) -> None:
    if not isinstance(value, str) or not SYMBOL_RES[kind].match(value):
        _fail(path, f"invalid {kind} symbol {value!r}")


def _validate_battler(b: Any, path: str, role: str) -> None:
    _require_keys(b, BATTLER_KEYS, path)
    _require_symbol(b["species"], "species", f"{path}.species")
    _require_label(b["speciesLabel"], f"{path}.speciesLabel")
    _require_int(b["level"], f"{path}.level", 1, 100)
    stats = b["stats"]
    _require_keys(stats, STAT_KEYS, f"{path}.stats")
    for key in STAT_KEYS:
        _require_int(stats[key], f"{path}.stats.{key}", 1, 65535)
    if stats["hp"] > stats["maxHp"]:
        _fail(f"{path}.stats.hp", f"hp {stats['hp']} exceeds maxHp {stats['maxHp']}")
    _require_symbol(b["ability"], "ability", f"{path}.ability")
    if b["ability"] == "ABILITY_NONE":
        _fail(f"{path}.ability", "ABILITY_NONE cannot be forced by the pinned test runner; use a reviewed neutral ability")
    _require_label(b["abilityLabel"], f"{path}.abilityLabel")
    _require_symbol(b["item"], "item", f"{path}.item")
    if b["item"] == "ITEM_NONE":
        if b["itemLabel"] is not None:
            _fail(f"{path}.itemLabel", "ITEM_NONE must have a null label")
    else:
        _require_label(b["itemLabel"], f"{path}.itemLabel")
    _require_enum(b["status"], STATUSES, f"{path}.status")
    if role == "defender" and b["status"] != "none":
        # A defender status would add end-of-turn HP changes to the measured battler and break the
        # HP-delta cross-check; no currently modelled defender mechanic reads it.
        _fail(f"{path}.status", "defender status must be none")
    stage_keys = ATTACKER_STAGE_KEYS if role == "attacker" else DEFENDER_STAGE_KEYS
    _require_keys(b["stages"], stage_keys, f"{path}.stages")
    for key in stage_keys:
        _require_int(b["stages"][key], f"{path}.stages.{key}", -6, 6)


def validate_scenario(s: Any, path: str = "scenario") -> None:
    """Validate one scenario; raises SchemaError naming the offending field."""
    _require_keys(s, SCENARIO_KEYS, path)
    sid = s["id"]
    if not isinstance(sid, str) or not ID_RE.match(sid) or len(sid) > MAX_ID_LENGTH:
        _fail(f"{path}.id", f"invalid scenario id {sid!r}")
    path = f"scenario[{sid}]"
    tags = s["tags"]
    if not isinstance(tags, list) or not tags:
        _fail(f"{path}.tags", "expected a non-empty list")
    for tag in tags:
        if not isinstance(tag, str) or not TAG_RE.match(tag):
            _fail(f"{path}.tags", f"invalid tag {tag!r}")
    if tags != sorted(set(tags)):
        _fail(f"{path}.tags", "tags must be sorted and unique")
    _require_enum(s["surface"], SURFACES, f"{path}.surface")
    _require_enum(s["format"], FORMATS, f"{path}.format")
    _require_enum(s["attackerSide"], SIDES, f"{path}.attackerSide")
    _require_keys(s["rules"], RULE_KEYS, f"{path}.rules")
    _require_bool(s["rules"]["fairyTypes"], f"{path}.rules.fairyTypes")
    _require_enum(s["rules"]["optionStyle"], OPTION_STYLES, f"{path}.rules.optionStyle")
    badges = s["badges"]
    if not isinstance(badges, list):
        _fail(f"{path}.badges", "expected a list")
    for badge in badges:
        _require_int(badge, f"{path}.badges[]", 1, 8)
    if badges != sorted(set(badges)):
        _fail(f"{path}.badges", "badges must be sorted and unique")
    _validate_battler(s["attacker"], f"{path}.attacker", "attacker")
    _validate_battler(s["defender"], f"{path}.defender", "defender")
    _require_keys(s["move"], ("symbol", "label"), f"{path}.move")
    _require_symbol(s["move"]["symbol"], "move", f"{path}.move.symbol")
    _require_label(s["move"]["label"], f"{path}.move.label")
    _require_bool(s["crit"], f"{path}.crit")
    _require_keys(s["field"], FIELD_KEYS, f"{path}.field")
    _require_enum(s["field"]["weather"], WEATHERS, f"{path}.field.weather")
    _require_bool(s["field"]["reflect"], f"{path}.field.reflect")
    _require_bool(s["field"]["lightScreen"], f"{path}.field.lightScreen")
    if s["format"] == "doubles":
        _require_keys(s["doubles"], DOUBLES_KEYS, f"{path}.doubles")
        _require_enum(s["doubles"]["defenderPartner"], DEFENDER_PARTNER_STATES, f"{path}.doubles.defenderPartner")
    elif s["doubles"] is not None:
        _fail(f"{path}.doubles", "must be null for a Singles scenario")
    _require_enum(s["expect"], EXPECTS, f"{path}.expect")
    if s["defender"]["stats"]["hp"] <= MAX_MEASURABLE_DAMAGE:
        _fail(f"{path}.defender.stats.hp", f"defender HP must exceed {MAX_MEASURABLE_DAMAGE} so no roll is capped by fainting")


def validate_scenarios(scenarios: Any) -> None:
    if not isinstance(scenarios, list) or not scenarios:
        raise SchemaError("scenarios: expected a non-empty list")
    seen: set[str] = set()
    for index, scenario in enumerate(scenarios):
        validate_scenario(scenario, f"scenarios[{index}]")
        sid = scenario["id"]
        if sid in seen:
            raise SchemaError(f"scenarios: duplicate scenario id {sid!r}")
        seen.add(sid)


def _validate_observed_battler(b: Any, path: str) -> None:
    _require_keys(b, OBSERVED_BATTLER_KEYS, path)
    _require_int(b["speciesId"], f"{path}.speciesId", 1, 65535)
    types = b["types"]
    if not isinstance(types, list) or not 1 <= len(types) <= 2:
        _fail(f"{path}.types", "expected 1 or 2 types")
    for t in types:
        _require_enum(t, TYPE_NAMES, f"{path}.types[]")
    if len(types) == 2 and types[0] == types[1]:
        _fail(f"{path}.types", "a mono-type battler is recorded with one type")
    _require_keys(b["baseStats"], BASE_STAT_KEYS, f"{path}.baseStats")
    for key in BASE_STAT_KEYS:
        _require_int(b["baseStats"][key], f"{path}.baseStats.{key}", 1, 255)
    _require_int(b["abilityId"], f"{path}.abilityId", 1, 65535)
    _require_int(b["itemId"], f"{path}.itemId", 0, 65535)
    _require_int(b["hpAtHit"], f"{path}.hpAtHit", 1, 65535)
    _require_keys(b["badgeBoosts"], BADGE_BOOST_KEYS, f"{path}.badgeBoosts")
    for key in BADGE_BOOST_KEYS:
        _require_bool(b["badgeBoosts"][key], f"{path}.badgeBoosts.{key}")


def validate_observed(observed: Any, scenario: dict, path: str) -> None:
    _require_keys(observed, OBSERVED_KEYS, path)
    _validate_observed_battler(observed["attacker"], f"{path}.attacker")
    _validate_observed_battler(observed["defender"], f"{path}.defender")
    move = observed["move"]
    _require_keys(move, OBSERVED_MOVE_KEYS, f"{path}.move")
    _require_int(move["id"], f"{path}.move.id", 1, 65535)
    _require_enum(move["type"], TYPE_NAMES, f"{path}.move.type")
    _require_int(move["power"], f"{path}.move.power", 1, 255)
    _require_enum(move["category"], CATEGORIES, f"{path}.move.category")
    _require_enum(move["target"], MOVE_TARGETS, f"{path}.move.target")
    # The raw pinned GetMoveTargetCount. The engine consults it only inside IsDoubleBattle()
    # (GetTargetDamageModifier); in Singles a spread move still reports its empty partner slot.
    _require_int(observed["targetCount"], f"{path}.targetCount", 1, 3)
    if observed["attacker"]["hpAtHit"] > scenario["attacker"]["stats"]["hp"]:
        _fail(f"{path}.attacker.hpAtHit", "attacker HP cannot rise before the measured hit")
    if observed["defender"]["hpAtHit"] != scenario["defender"]["stats"]["hp"]:
        _fail(f"{path}.defender.hpAtHit", "defender HP must be untouched before the measured hit")


def validate_rolls(rolls: Any, scenario: dict, path: str) -> None:
    if not isinstance(rolls, list) or len(rolls) != ROLL_COUNT:
        _fail(path, f"expected exactly {ROLL_COUNT} rolls")
    for index, roll in enumerate(rolls):
        _require_int(roll, f"{path}[{index}]", 0, MAX_MEASURABLE_DAMAGE - 1)
    if rolls != sorted(rolls):
        _fail(path, "rolls must be non-decreasing in roll order (85%..100%)")
    if scenario["expect"] == "immune":
        if any(rolls):
            _fail(path, "an immune scenario must record sixteen zero rolls")
    elif rolls[0] < 1:
        # The pinned engine floors a non-immune hit at 1; a zero here is a failed measurement.
        _fail(path, "a damaging scenario recorded a zero roll")


def validate_entry(entry: Any, path: str = "entry") -> None:
    _require_keys(entry, ENTRY_KEYS, path)
    validate_scenario(entry["scenario"], f"{path}.scenario")
    sid = entry["scenario"]["id"]
    validate_observed(entry["observed"], entry["scenario"], f"entry[{sid}].observed")
    validate_rolls(entry["rolls"], entry["scenario"], f"entry[{sid}].rolls")


def validate_provenance(provenance: Any) -> None:
    path = "provenance"
    _require_keys(provenance, PROVENANCE_KEYS, path)
    upstream = provenance["hnsUpstream"]
    _require_keys(upstream, UPSTREAM_KEYS, f"{path}.hnsUpstream")
    if upstream["repository"] != HNS_REPOSITORY:
        _fail(f"{path}.hnsUpstream.repository", f"expected {HNS_REPOSITORY}")
    if upstream["commit"] != HNS_PINNED_COMMIT:
        _fail(f"{path}.hnsUpstream.commit", f"corpus was not generated from the pinned commit {HNS_PINNED_COMMIT}")
    if upstream["tree"] != HNS_PINNED_TREE:
        _fail(f"{path}.hnsUpstream.tree", f"corpus was not generated from the pinned tree {HNS_PINNED_TREE}")
    backend = provenance["backend"]
    _require_keys(backend, BACKEND_KEYS, f"{path}.backend")
    if backend["kind"] != ORACLE_BACKEND_KIND:
        _fail(f"{path}.backend.kind", f"expected {ORACLE_BACKEND_KIND}")
    for key in ("build", "runner"):
        if not isinstance(backend[key], str) or not backend[key]:
            _fail(f"{path}.backend.{key}", "expected a non-empty string")
    if not isinstance(backend["mgbaRomTestSha256"], str) or not SHA256_RE.match(backend["mgbaRomTestSha256"]):
        _fail(f"{path}.backend.mgbaRomTestSha256", "expected a sha256")
    patches = backend["harnessPatches"]
    if not isinstance(patches, list):
        _fail(f"{path}.backend.harnessPatches", "expected a list")
    for patch in patches:
        _require_keys(patch, ("path", "sha256"), f"{path}.backend.harnessPatches[]")
        if not isinstance(patch["sha256"], str) or not SHA256_RE.match(patch["sha256"]):
            _fail(f"{path}.backend.harnessPatches[].sha256", "expected a sha256")
    _require_bool(backend["prunedUpstreamTests"], f"{path}.backend.prunedUpstreamTests")
    toolchain = provenance["toolchain"]
    _require_keys(toolchain, TOOLCHAIN_KEYS, f"{path}.toolchain")
    if not isinstance(toolchain["gcc"], str) or "arm-none-eabi-gcc" not in toolchain["gcc"]:
        _fail(f"{path}.toolchain.gcc", "expected the arm-none-eabi-gcc version line")
    for key in ("gccSha256", "cc1Sha256"):
        if not isinstance(toolchain[key], str) or not SHA256_RE.match(toolchain[key]):
            _fail(f"{path}.toolchain.{key}", "expected a sha256")
    generator = provenance["generator"]
    _require_keys(generator, GENERATOR_KEYS, f"{path}.generator")
    if generator["tool"] != "tools/hns-damage-oracle":
        _fail(f"{path}.generator.tool", "unexpected generator")
    if generator["version"] != ORACLE_TOOL_VERSION:
        _fail(f"{path}.generator.version", f"expected {ORACLE_TOOL_VERSION}")
    if not isinstance(generator["testSourceSha256"], str) or not SHA256_RE.match(generator["testSourceSha256"]):
        _fail(f"{path}.generator.testSourceSha256", "expected a sha256")


def validate_corpus(doc: Any) -> None:
    """Validate a whole corpus document (header, provenance, every entry, unique IDs, order)."""
    _require_keys(doc, CORPUS_KEYS, "corpus")
    if doc["schemaVersion"] != SCHEMA_VERSION:
        _fail("corpus.schemaVersion", f"expected {SCHEMA_VERSION}, got {doc['schemaVersion']!r}")
    if doc["rollOrder"] != ROLL_ORDER:
        _fail("corpus.rollOrder", "roll order statement does not match the schema")
    validate_provenance(doc["provenance"])
    entries = doc["entries"]
    if not isinstance(entries, list) or not entries:
        _fail("corpus.entries", "expected a non-empty list")
    ids = []
    for index, entry in enumerate(entries):
        validate_entry(entry, f"corpus.entries[{index}]")
        ids.append(entry["scenario"]["id"])
    if len(set(ids)) != len(ids):
        dupes = sorted({i for i in ids if ids.count(i) > 1})
        _fail("corpus.entries", f"duplicate scenario id(s) {dupes}")
    if ids != sorted(ids):
        _fail("corpus.entries", "entries must be sorted by scenario id")


def _dump(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def canonical_dumps(doc: dict) -> str:
    """Byte-deterministic serialisation: sorted keys, one entry per line, trailing newline."""
    head = {k: doc[k] for k in ("schemaVersion", "rollOrder", "provenance")}
    lines = ["{"]
    for key in sorted(head):
        lines.append(f"{json.dumps(key)}:{_dump(head[key])},")
    lines.append('"entries":[')
    entries = doc["entries"]
    for index, entry in enumerate(entries):
        suffix = "," if index + 1 < len(entries) else ""
        lines.append(_dump(entry) + suffix)
    lines.append("]}")
    text = "\n".join(lines) + "\n"
    check_no_machine_artifacts(text)
    return text


def check_no_machine_artifacts(text: str) -> None:
    for pattern in FORBIDDEN_ARTIFACT_RES:
        match = pattern.search(text)
        if match:
            raise SchemaError(f"corpus text contains a machine path or timestamp: {match.group(0)!r}")


def load_corpus_text(text: str) -> dict:
    """Parse and fully validate corpus text; the text must also be in canonical form."""
    doc = json.loads(text)
    validate_corpus(doc)
    if canonical_dumps(doc) != text:
        raise SchemaError("corpus is not in canonical serialisation (regenerate it with the oracle tool)")
    return doc
