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

SCHEMA_VERSION = 10
ROLL_COUNT = 16

HNS_REPOSITORY = "PokemonHnS-Development/pokehns-expansion"
HNS_PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
HNS_PINNED_TREE = "586946f21e9322e8d837654d9e07cf6b8239feed"

ORACLE_BACKEND_KIND = "pinned-expansion-battle-test-runner"
ORACLE_TOOL_VERSION = 10

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
# Pinned H&S enum IDs include TYPE_NONE=0 and TYPE_MYSTERY=10. Mystery has no ordinary move/item
# type operand, but its enum slot remains between Steel and Fire; do not infer item secondary IDs
# from the compact damage-chart ordering above.
TYPE_SECONDARY_IDS = {
    name: index + 1 + (index >= 9) for index, name in enumerate(TYPE_NAMES)
}
ITEM_SECONDARY_TYPE_EFFECTS = {
    "HOLD_EFFECT_GEMS", "HOLD_EFFECT_PLATE", "HOLD_EFFECT_TYPE_POWER",
}

# IDs used by the oracle's transformed-species state setup. Generated C assertions compare these
# values with the pinned species constants before a corpus entry can be assembled.
SPECIES_ID_EXPECTATIONS = {
    "SPECIES_CUBONE": 104,
    "SPECIES_SNORLAX": 143,
}

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
    "attacker", "defender", "move", "crit", "field", "doubles", "stateSetup", "expect",
)
RULE_KEYS = ("fairyTypes", "optionStyle")
OPTION_STYLES = ("perMoveSplit", "typeBased")
SURFACES = ("modelled", "engine-only")
FORMATS = ("singles", "doubles")
SIDES = ("player", "opponent")
STATUSES = ("none", "burn", "poison", "toxic", "paralysis")
WEATHERS = ("none", "rain", "sun", "sandstorm")
EXPECTS = ("damage", "immune")
BATTLER_KEYS = (
    "species", "speciesLabel", "level", "stats", "ability", "abilityLabel",
    "item", "itemLabel", "status", "stages",
)
STAT_KEYS = ("maxHp", "hp", "attack", "defense", "spAttack", "spDefense", "speed")
ATTACKER_STAGE_KEYS = ("attack", "spAttack")
DEFENDER_STAGE_KEYS = ("defense", "spDefense")
FIELD_KEYS = ("weather", "reflect", "lightScreen", "terrain", "gravity")
TERRAINS = ("none", "grassy", "electric", "misty", "psychic")
DOUBLES_KEYS = ("defenderPartner",)
STATE_SETUP_KEYS = (
    "attackerSpeciesForm", "defenderSpeciesForm", "attackerTransformedMonSpecies",
    "defenderTransformedMonSpecies", "attacker", "defender", "attackerStatStages",
    "defenderStatStages", "gastroAcidBeforeHit", "doubles", "capture", "underground", "underwater", "wonderRoom", "magicRoom", "laterAction", "statusDoubleStatus1",
)
RUNTIME_DOMAINS = {
    "personality": (0, 0xffffffff), "gender": (0, 255), "slowStartTimer": (0, 7),
    "flashFireBoosted": (0, 1), "transformed": (0, 1), "boosterEnergyActivated": (0, 1),
    "paradoxBoostedStat": (0, 5), "vesselOfRuin": (0, 1), "swordOfRuin": (0, 1),
    "tabletsOfRuin": (0, 1), "beadsOfRuin": (0, 1), "gastroAcid": (0, 1),
    "neutralizingGas": (0, 1), "isFirstTurn": (0, 3), "supremeOverlordCounter": (0, 5),
    "selectedGimmick": (0, 5), "activeGimmick": (0, 5), "lastToMove": (0, 1),
    "abilityShield": (0, 1), "embargo": (0, 1), "metronomeItemCounter": (0, 255),
    "transformedMonSpecies": (0, 65535), "holdEffectActive": (0, 1),
    "baseSpeciesId": (0, 65535), "evioliteCanEvolve": (0, 1),
    "holdEffectParam": (0, 65535), "itemSecondaryId": (0, 255), "itemIdAtHit": (0, 900), "chargeTimer": (0, 2),
}
RUNTIME_SETUP_DOMAINS = {**RUNTIME_DOMAINS, "dynamaxSelected": (0, 1)}
RUNTIME_SETUP_KEYS = set(RUNTIME_SETUP_DOMAINS) - {
    "gender", "lastToMove", "abilityShield", "transformedMonSpecies", "holdEffectActive",
    "baseSpeciesId", "evioliteCanEvolve", "holdEffectParam", "itemSecondaryId", "itemIdAtHit",
}
DEFENDER_PARTNER_STATES = ("present", "fainted")

OBSERVED_KEYS = ("attacker", "defender", "move", "targetCount", "fieldStatuses")
OBSERVED_BATTLER_KEYS = (
    "speciesId", "types", "baseStats", "abilityId", "itemId", "itemRecord", "hpAtHit", "status1",
    "badgeBoosts", "terrainAffected", "runtime",
)
ITEM_RECORD_KEYS = ("holdEffect", "holdEffectParam", "itemType")
BASE_STAT_KEYS = ("hp", "attack", "defense", "spAttack", "spDefense", "speed")
BADGE_BOOST_KEYS = ("attack", "defense", "spAttack", "spDefense")
OBSERVED_MOVE_KEYS = ("id", "type", "power", "category", "target", "flags", "abilityFlags",
                      "effect", "ordinary", "makesContact", "punchingMove", "sheerForceAffected",
                      "priority", "targetClass", "ateBoost")
MOVE_IMMUNITY_FLAGS = ("soundMove", "ballisticMove", "windMove", "healingMove", "ignoresTargetAbility")
MOVE_ABILITY_FLAGS = ("punchingMove", "bitingMove", "pulseMove", "slicingMove")
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


def expected_item_secondary_id(item_record: dict[str, Any]) -> int:
    """Pinned gItemsInfo.secondaryId; resist-berry types live in holdEffectParam instead."""
    item_type = item_record["itemType"]
    if item_type is None or item_record["holdEffect"] not in ITEM_SECONDARY_TYPE_EFFECTS:
        return 0
    return TYPE_SECONDARY_IDS[item_type]


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
    if role == "defender" and b["status"] not in ("none", "paralysis"):
        # A defender status would add end-of-turn HP changes to the measured battler and break the
        # HP-delta cross-check; no currently modelled defender mechanic reads it.
        _fail(f"{path}.status", "defender status may only be non-residual paralysis")
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
    _require_enum(s["field"]["terrain"], TERRAINS, f"{path}.field.terrain")
    _require_bool(s["field"]["gravity"], f"{path}.field.gravity")
    if s["format"] == "doubles":
        _require_keys(s["doubles"], DOUBLES_KEYS, f"{path}.doubles")
        _require_enum(s["doubles"]["defenderPartner"], DEFENDER_PARTNER_STATES, f"{path}.doubles.defenderPartner")
    elif s["doubles"] is not None:
        _fail(f"{path}.doubles", "must be null for a Singles scenario")
    if s["stateSetup"] is not None:
        if not isinstance(s["stateSetup"], dict) or not s["stateSetup"] or not set(s["stateSetup"]).issubset(STATE_SETUP_KEYS):
            _fail(f"{path}.stateSetup", f"expected a non-empty subset of {STATE_SETUP_KEYS}")
        for role, value in s["stateSetup"].items():
            loc = f"{path}.stateSetup.{role}"
            if role == "statusDoubleStatus1":
                _require_int(value, loc, 0, 8191)
                if "move-coverage-slice-6" not in s["tags"]: _fail(loc, "requires status-double slice")
            elif role == "underground":
                _require_bool(value, loc)
                if s["move"]["symbol"] != "MOVE_EARTHQUAKE": _fail(loc, "only Earthquake damage is admitted underground")
            elif role == "underwater":
                _require_bool(value, loc)
                if s["move"]["symbol"] not in ("MOVE_SURF", "MOVE_WHIRLPOOL"):
                    _fail(loc, "only frozen Surf/Whirlpool damage is admitted underwater")
            elif role == "gastroAcidBeforeHit":
                _require_enum(value, ("attacker", "defender"), loc)
                if s["stateSetup"].get(value, {}).get("gastroAcid") != 1:
                    _fail(loc, "requires a matching observed suppression operand")
            elif role == "doubles":
                if s["format"] != "doubles" or not isinstance(value, dict) or not value:
                    _fail(loc, "Doubles setup requires a nonempty Doubles operand map")
                allowed = {"attackerPartnerAbility", "defenderPartnerAbility", "attackerPartnerSpecies",
                           "defenderPartnerSpecies", "helpingHand", "attackerPartnerGastroAcid",
                           "defenderPartnerGastroAcid", "attackerPartnerRuinFlags", "defenderPartnerRuinFlags"}
                if not set(value) <= allowed:
                    _fail(loc, "unknown Doubles operand")
                for key, operand in value.items():
                    if key.endswith("Ability"): _require_symbol(operand, "ability", loc + "." + key)
                    elif key.endswith("Species"): _require_symbol(operand, "species", loc + "." + key)
                    else: _require_int(operand, loc + "." + key, 0,
                        7 if key == "helpingHand" else 15 if key.endswith("RuinFlags") else 1)
            elif role.endswith("SpeciesForm") or role.endswith("TransformedMonSpecies"):
                _require_symbol(value, "species", loc)
            elif role in ("capture", "underground", "underwater", "wonderRoom", "magicRoom"):
                _require_bool(value, loc)
            elif role == "laterAction":
                _require_int(value, loc, 0, 14)
            elif role in ("attackerStatStages", "defenderStatStages"):
                allowed = ("attack", "spAttack") if role == "attackerStatStages" else ("defense", "spDefense")
                if not isinstance(value, dict) or not set(value).issubset(allowed):
                    _fail(loc, f"expected a subset of {allowed}")
                participant = s["attacker"] if role == "attackerStatStages" else s["defender"]
                for stat, stage in value.items():
                    _require_int(stage, f"{loc}.{stat}", -6, 6)
                    if participant["stages"].get(stat) != stage:
                        _fail(f"{loc}.{stat}", "does not match the scenario's selected-hit stat stage")
            else:
                if not isinstance(value, dict) or not set(value).issubset(RUNTIME_SETUP_KEYS):
                    _fail(loc, "invalid runtime setup keys")
                for key, operand in value.items():
                    _require_int(operand, f"{loc}.{key}", *RUNTIME_SETUP_DOMAINS[key])
    _require_enum(s["expect"], EXPECTS, f"{path}.expect")
    if "move-coverage-slice-7" in s["tags"] and (s["move"]["symbol"] != "MOVE_BRINE" or s["format"] != "singles"):
        _fail(path, "Brine small-HP exception is Singles Brine only")
    if "move-coverage-slice-7" not in s["tags"] and s["defender"]["stats"]["hp"] <= MAX_MEASURABLE_DAMAGE:
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
    _require_keys(b, OBSERVED_BATTLER_KEYS + (("maxHpAtHit",) if "maxHpAtHit" in b else ()), path)
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
    item_record = b["itemRecord"]
    _require_keys(item_record, ITEM_RECORD_KEYS, f"{path}.itemRecord")
    if not isinstance(item_record["holdEffect"], str) or not re.fullmatch(
        r"HOLD_EFFECT_[A-Z0-9_]+", item_record["holdEffect"]
    ):
        _fail(f"{path}.itemRecord.holdEffect", "expected a pinned hold-effect symbol")
    _require_int(item_record["holdEffectParam"], f"{path}.itemRecord.holdEffectParam", 0, 65535)
    if item_record["itemType"] is not None:
        _require_enum(item_record["itemType"], TYPE_NAMES, f"{path}.itemRecord.itemType")
    if "maxHpAtHit" in b:
        _require_int(b["maxHpAtHit"], f"{path}.maxHpAtHit", 1, 65535)
    _require_int(b["hpAtHit"], f"{path}.hpAtHit", 1, 65535)
    _require_int(b["status1"], f"{path}.status1", 0, 65535)
    _require_bool(b["terrainAffected"], f"{path}.terrainAffected")
    _require_keys(b["runtime"], RUNTIME_DOMAINS, f"{path}.runtime")
    for key, limits in RUNTIME_DOMAINS.items():
        _require_int(b["runtime"][key], f"{path}.runtime.{key}", *limits)
    if item_record["holdEffectParam"] != b["runtime"]["holdEffectParam"]:
        _fail(f"{path}.itemRecord.holdEffectParam", "does not match the pre-damage source runtime operand")
    expected_secondary_id = expected_item_secondary_id(item_record)
    if b["runtime"]["itemSecondaryId"] != expected_secondary_id:
        _fail(f"{path}.runtime.itemSecondaryId", "does not match the generated item type operand")
    if item_record["holdEffect"] == "HOLD_EFFECT_RESIST_BERRY" and item_record["itemType"] is not None:
        if item_record["holdEffectParam"] != TYPE_SECONDARY_IDS[item_record["itemType"]]:
            _fail(f"{path}.itemRecord.itemType", "does not match the resist-berry hold-effect type parameter")
    _require_keys(b["badgeBoosts"], BADGE_BOOST_KEYS, f"{path}.badgeBoosts")
    for key in BADGE_BOOST_KEYS:
        _require_bool(b["badgeBoosts"][key], f"{path}.badgeBoosts.{key}")


def validate_observed(observed: Any, scenario: dict, path: str) -> None:
    extended = "doubles" in (scenario.get("stateSetup") or {})
    _require_keys(observed, OBSERVED_KEYS + (("doubles",) if extended else ()) + (("defenderSemiInvulnerableState",) if any(t in scenario["tags"] for t in ("move-coverage-slice-3", "move-coverage-slice-5")) else ()) + (("explosionUserHpAtDamage",) if "move-coverage-slice-4" in scenario["tags"] else ()), path)
    if "move-coverage-slice-4" in scenario["tags"]:
        _require_int(observed["explosionUserHpAtDamage"], path + ".explosionUserHpAtDamage", 0, 0)
    if extended:
        d = observed["doubles"]
        _require_keys(d, ("helpingHand", "attackerPartnerAbility", "defenderPartnerAbility",
            "attackerPartnerSpecies", "defenderPartnerSpecies", "fieldAbilities", "ruinFlags"), path + ".doubles")
        for key in ("attackerPartnerAbility", "defenderPartnerAbility"):
            _require_int(d[key], path + "." + key, 0, 310)
        for key in ("attackerPartnerSpecies", "defenderPartnerSpecies"):
            _require_int(d[key], path + "." + key, 0, 1572)
        _require_int(d["helpingHand"], path + ".helpingHand", 0, 7)
        _require_int(d["ruinFlags"], path + ".ruinFlags", 0, 15)
        if d["fieldAbilities"] != sorted(set(d["fieldAbilities"])) or not set(d["fieldAbilities"]) <= {13, 76, 186, 187, 188}:
            _fail(path + ".fieldAbilities", "unknown or noncanonical field ability IDs")
    _validate_observed_battler(observed["attacker"], f"{path}.attacker")
    _validate_observed_battler(observed["defender"], f"{path}.defender")
    if any(t in scenario["tags"] for t in ("move-coverage-slice-3", "move-coverage-slice-5")):
        _require_int(observed["defenderSemiInvulnerableState"], path + ".defenderSemiInvulnerableState", 0, 6)
        if observed["defenderSemiInvulnerableState"] != (2 if (scenario.get("stateSetup") or {}).get("underwater") else int(bool((scenario.get("stateSetup") or {}).get("underground")))):
            _fail(path, "actual hit-time semi state disagrees with setup")
    move = observed["move"]
    _require_keys(move, OBSERVED_MOVE_KEYS + (("statusDoubleMask",) if "move-coverage-slice-6" in scenario["tags"] else ()), f"{path}.move")
    _require_int(move["id"], f"{path}.move.id", 1, 65535)
    _require_enum(move["type"], TYPE_NAMES, f"{path}.move.type")
    _require_int(move["power"], f"{path}.move.power", 1, 255)
    _require_enum(move["category"], CATEGORIES, f"{path}.move.category")
    _require_enum(move["target"], MOVE_TARGETS, f"{path}.move.target")
    flags = move["flags"]
    if not isinstance(flags, list) or any(flag not in MOVE_IMMUNITY_FLAGS for flag in flags):
        _fail(f"{path}.move.flags", f"expected a list of supported immunity flags, got {flags!r}")
    if flags != sorted(set(flags)):
        _fail(f"{path}.move.flags", "flags must be sorted and unique")
    ability_flags = move["abilityFlags"]
    if not isinstance(ability_flags, list) or any(flag not in MOVE_ABILITY_FLAGS for flag in ability_flags):
        _fail(f"{path}.move.abilityFlags", f"expected a list of supported base-power ability flags, got {ability_flags!r}")
    if ability_flags != sorted(set(ability_flags)):
        _fail(f"{path}.move.abilityFlags", "abilityFlags must be sorted and unique")
    if not isinstance(move["effect"], str) or not move["effect"].startswith("EFFECT_"):
        _fail(f"{path}.move.effect", "expected a pinned source move-effect symbol")
    _require_bool(move["ordinary"], f"{path}.move.ordinary")
    exact_group_d = {"ability:marvel-scale", "ability:flower-gift", "ability:sheer-force",
                     "ability:tough-claws", "ability:fluffy"}
    if scenario["surface"] == "modelled" and exact_group_d.intersection(scenario["tags"]) and not move["ordinary"]:
        _fail(f"{path}.move.ordinary", "modelled low-state Group D scenarios must pass the source ordinary-move gate")
    for key in ("makesContact", "punchingMove", "sheerForceAffected"):
        if move[key] is not None and not isinstance(move[key], bool):
            _fail(f"{path}.move.{key}", "source-derived tri-state metadata must be boolean or null")
    _require_int(move["priority"], f"{path}.move.priority", -8, 10)
    _require_int(move["targetClass"], f"{path}.move.targetClass", 0, 255)
    _require_bool(move["ateBoost"], f"{path}.move.ateBoost")
    # The raw pinned GetMoveTargetCount. The engine consults it only inside IsDoubleBattle()
    # (GetTargetDamageModifier); in Singles a spread move still reports its empty partner slot.
    _require_int(observed["targetCount"], f"{path}.targetCount", 1, 3)
    _require_int(observed["fieldStatuses"], f"{path}.fieldStatuses", 0, 0xFFF)
    expected_terrain = {"none": 0, "grassy": 1 << 6, "electric": 1 << 8,
                        "misty": 1 << 7, "psychic": 1 << 9}[scenario["field"]["terrain"]]
    if scenario["field"]["gravity"]:
        expected_terrain |= 1 << 5
    state_setup = scenario.get("stateSetup") or {}
    if state_setup.get("wonderRoom"):
        expected_terrain |= 4
    if state_setup.get("magicRoom"):
        expected_terrain |= 1
    if observed["fieldStatuses"] != expected_terrain:
        _fail(f"{path}.fieldStatuses", f"observed field word {observed['fieldStatuses']:#x} != scenario terrain {expected_terrain:#x}")
    if observed["attacker"]["hpAtHit"] > scenario["attacker"]["stats"]["hp"]:
        _fail(f"{path}.attacker.hpAtHit", "attacker HP cannot rise before the measured hit")
    if "move-coverage-slice-7" in scenario["tags"]:
        if observed["defender"].get("maxHpAtHit") != scenario["defender"]["stats"]["maxHp"]:
            _fail(path, "Brine hit-boundary maxHP missing or changed")
    if observed["defender"]["hpAtHit"] != scenario["defender"]["stats"]["hp"]:
        _fail(f"{path}.defender.hpAtHit", "defender HP must be untouched before the measured hit")


def validate_rolls(rolls: Any, scenario: dict, path: str) -> None:
    if not isinstance(rolls, list) or len(rolls) != ROLL_COUNT:
        _fail(path, f"expected exactly {ROLL_COUNT} rolls")
    for index, roll in enumerate(rolls):
        _require_int(roll, f"{path}[{index}]", 0, MAX_MEASURABLE_DAMAGE - 1)
    if rolls != sorted(rolls):
        _fail(path, "rolls must be non-decreasing in roll order (85%..100%)")
    if "move-coverage-slice-7" in scenario["tags"] and scenario["expect"] == "damage" and max(rolls) >= scenario["defender"]["stats"]["hp"]:
        _fail(path, "Brine damage might be capped by fainting")
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
