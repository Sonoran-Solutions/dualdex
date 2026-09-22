#!/usr/bin/env python3
"""Generate / verify the vanilla FireRed+Emerald Generation III golden matrix.

The committed fixture file ``vanilla_gen3_goldens.json`` is the single
machine-readable source of truth shared by:

  * the independent oracle           (``gen3_reference.py``, this verifier),
  * the shipped-engine host suite    (``native/tests/test_js_calc.c``), and
  * the production-boundary tests    (``CalcVanillaGoldenBoundaryTest.kt``).

Each fixture records game/profile identity, exact ROM SHA-256s, pinned oracle
commits, the exact engine request, and the expected 16-roll damage vector, so a
reviewer can reproduce every number without running the app.

Usage:
    python3 tools/calc-goldens/verify_goldens.py            # verify (CI)
    python3 tools/calc-goldens/verify_goldens.py --emit     # regenerate
"""

from __future__ import annotations

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen3_reference  # noqa: E402

FIXTURE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            "vanilla_gen3_goldens.json")

# Pinned decompilation revisions the oracle arithmetic was read from.
POKEEMERALD_COMMIT = "5eff78649e7170a877b961ef0b3da13b81a16038"
POKEFIRERED_COMMIT = "c75f352304d529f6ba92d4f74b9cf8b5c3810788"

# Genuine SHA-256 digests of the exact supported dumps, authenticated by the
# No-Intro DAT-o-MATIC dump records whose SHA-1 values are the ones pret's own
# READMEs name for the builds it decompiles.
PROFILE_SHA256 = {
    "vanilla_firered": [
        # 1616 - Pokemon - FireRed Version (USA, Europe)  (pret pokefirered.gba)
        "3d0c79f1627022e18765766f6cb5ea067f6b5bf7dca115552189ad65a5c3a8ac",
        # 1672 - Pokemon - FireRed Version (USA, Europe) (Rev 1) (pret pokefirered_rev1.gba)
        "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
    ],
    "vanilla_emerald": [
        # 1961 - Pokemon - Emerald Version (USA, Europe)  (pret pokeemerald.gba)
        "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
    ],
}

IVS_MAX = {"hp": 31, "atk": 31, "def": 31, "spa": 31, "spd": 31, "spe": 31}
EVS_ZERO = {"hp": 0, "atk": 0, "def": 0, "spa": 0, "spd": 0, "spe": 0}


def participant(species, **overrides):
    base = {
        "species": species,
        "level": 50,
        "nature": "Hardy",
        "item": None,
        "ability": None,
        "curHP": None,
        "status": None,
        "boosts": None,
        "ivs": dict(IVS_MAX),
        "evs": dict(EVS_ZERO),
    }
    base.update(overrides)
    return base


def fixture(fixture_id, covers, move, attacker=None, defender=None,
            game_type="Singles", weather=None, defender_side=None,
            provenance=""):
    attacker = attacker or participant("Machamp")
    defender = defender or participant("Snorlax")
    return {
        "id": fixture_id,
        "covers": covers,
        # Both bundled vanilla profiles are the same Generation III ruleset and
        # the same pinned content pack; the matrix is asserted against each of
        # them at the boundary, and the per-game distinction is identity/trust.
        "games": ["vanilla_firered", "vanilla_emerald"],
        "evidence": "SOURCE VERIFIED (oracle) + HOST VERIFIED (shipped engine)",
        "provenance": provenance,
        "input": {
            "attacker": attacker,
            "defender": defender,
            "move": {"name": move["name"], "isCrit": bool(move.get("isCrit", False))},
            "field": {
                "gameType": game_type,
                "weather": weather,
                "defenderSide": defender_side,
            },
        },
    }


def build_fixtures():
    fighters = (
        "Machamp (Hardy L50, 31 IV / 0 EV, Atk 150 / SpA 85) vs "
        "Snorlax (Hardy L50, 31 IV / 0 EV, Def 85 / SpD 130, HP 235)"
    )
    return [
        fixture(
            "vg3_a_neutral_physical",
            ["A neutral physical (no STAB, no field)"],
            {"name": "Rock Slide"},
            provenance=(
                "Gen III base damage; Rock is physical by type; Rock vs Normal is 1.0; "
                "Machamp is not a Rock type, so there is no STAB. " + fighters),
        ),
        fixture(
            "vg3_b_neutral_special_type_split",
            ["B neutral special (Gen III type-based category)"],
            {"name": "Crunch"},
            provenance=(
                "Dark is a SPECIAL type in Gen III, so SpA/SpD are used even though "
                "Machamp is a physical attacker. This fails if the pipeline reads a "
                "modern per-move category instead of the generation's partition. " + fighters),
        ),
        fixture(
            "vg3_c_stab_and_type_effectiveness",
            ["C STAB + type effectiveness"],
            {"name": "Karate Chop"},
            provenance=(
                "Fighting is physical; Machamp's Fighting STAB is x1.5 floored, then "
                "Fighting vs Normal x2 floored. Both materially change the result. " + fighters),
        ),
        fixture(
            "vg3_d_critical_hit",
            ["D critical hit x2"],
            {"name": "Rock Slide", "isCrit": True},
            provenance=(
                "Gen III critical multiplier is x2 (gCritMultiplier == 2). A drift to "
                "the modern x1.5 would understate the vector. " + fighters),
        ),
        fixture(
            "vg3_d_crit_ignores_negative_attack_and_positive_defense",
            ["D critical hit ignores unfavourable stat stages"],
            {"name": "Rock Slide", "isCrit": True},
            attacker=participant("Machamp", boosts={"atk": -2, "def": 0, "spa": 0, "spd": 0, "spe": 0}),
            defender=participant("Snorlax", boosts={"atk": 0, "def": 2, "spa": 0, "spd": 0, "spe": 0}),
            provenance=(
                "On a critical hit the engine ignores the attacker's NEGATIVE stages "
                "and the defender's POSITIVE stages, so the vector equals the neutral "
                "critical fixture. " + fighters),
        ),
        fixture(
            "vg3_e_screen_reflect_singles",
            ["E Reflect (Singles x1/2)"],
            {"name": "Strength"},
            defender_side={"isReflect": True, "isLightScreen": False},
            provenance=(
                "Singles Reflect halves the physical base damage before +2. " + fighters),
        ),
        fixture(
            "vg3_e_screen_light_screen_singles",
            ["E Light Screen (Singles x1/2)"],
            {"name": "Hydro Pump"},
            defender_side={"isReflect": False, "isLightScreen": True},
            provenance=(
                "Singles Light Screen halves the special base damage before +2. " + fighters),
        ),
        fixture(
            "vg3_e_doubles_spread_format_sensitive",
            ["E format-sensitive Doubles spread x1/2"],
            {"name": "Rock Slide"},
            game_type="Doubles",
            provenance=(
                "Rock Slide targets both foes; in a non-Singles format the engine halves "
                "the base damage. This is the format-sensitive counterpart of the Singles "
                "neutral fixture and fails if field.gameType is normalised away. " + fighters),
        ),
        fixture(
            "vg3_f_weather_rain_halves_fire",
            ["F Rain x1/2 on Fire"],
            {"name": "Flamethrower"},
            weather="Rain",
            provenance=(
                "Rain halves a Fire move's base damage before +2. " + fighters),
        ),
        fixture(
            "vg3_f_weather_sun_boosts_fire",
            ["F Sun x1.5 on Fire"],
            {"name": "Flamethrower"},
            weather="Sun",
            provenance=(
                "Sun multiplies a Fire move's base damage by 1.5 (floored) before +2. " + fighters),
        ),
        fixture(
            "vg3_g_burn_guts_boosts_attack",
            ["G burn + Guts x1.5 attack, no burn halving"],
            {"name": "Rock Slide"},
            attacker=participant("Machamp", status="brn", ability="Guts"),
            provenance=(
                "Guts skips the burn halving and multiplies attack by 1.5 (floored) in "
                "the stat step, so the vector is higher than the unburned neutral case. "
                "The ability is explicit: an omitted ability is the engine's species "
                "default (Machamp's is Guts), so a burn fixture must state it. " + fighters),
        ),
        fixture(
            "vg3_h_stat_stage_attack_plus_two",
            ["H non-neutral stat stage (+2 Atk)"],
            {"name": "Rock Slide"},
            attacker=participant("Machamp", boosts={"atk": 2, "def": 0, "spa": 0, "spd": 0, "spe": 0}),
            provenance=(
                "Gen III +2 Attack uses gStatStageRatios {20,10}, i.e. x2 floored, before "
                "the base formula. This is the positive-stage branch. " + fighters),
        ),
    ]


def serialise_request(request_input):
    """Mirror ``buildCalcRequestJson`` for the fields the matrix uses."""
    def mon(p):
        out = {"species": p["species"], "level": p["level"]}
        for key in ("item", "nature", "ability", "curHP", "status"):
            if p.get(key) is not None:
                out[key] = p[key]
        if p.get("ivs") is not None:
            out["ivs"] = {k: p["ivs"][k] for k in ("hp", "atk", "def", "spa", "spd", "spe")}
        if p.get("evs") is not None:
            out["evs"] = {k: p["evs"][k] for k in ("hp", "atk", "def", "spa", "spd", "spe")}
        if p.get("boosts") is not None:
            out["boosts"] = {k: p["boosts"][k] for k in ("atk", "def", "spa", "spd", "spe")}
        return out

    field = {"gameType": request_input["field"].get("gameType", "Singles")}
    if request_input["field"].get("weather") is not None:
        field["weather"] = request_input["field"]["weather"]
    if request_input["field"].get("terrain") is not None:
        field["terrain"] = request_input["field"]["terrain"]
    side = request_input["field"].get("defenderSide")
    if side is not None:
        encoded = {}
        if side.get("isReflect"):
            encoded["isReflect"] = True
        if side.get("isLightScreen"):
            encoded["isLightScreen"] = True
        field["defenderSide"] = encoded

    return json.dumps({
        "gen": 3,
        "attacker": mon(request_input["attacker"]),
        "defender": mon(request_input["defender"]),
        "move": {"name": request_input["move"]["name"],
                 "isCrit": bool(request_input["move"].get("isCrit", False))},
        "field": field,
    }, separators=(",", ":"))


def build_cartridge_references():
    """Source-exact Doubles screen goldens, deliberately NOT part of the engine matrix.

    These carry the cartridge's own arithmetic (`2 * (damage / 3)` computed on the pre-roll value
    while both defending battlers are present, `damage / 2` otherwise) for a shape the shipped
    `@smogon/calc` 0.11.0 pipeline expresses differently and which the production gate therefore
    refuses (`CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED`).

    They are committed so the two facts that keep that gate honest are machine-checked rather than
    asserted in prose: the cartridge vector really is different from the pipeline's, and the
    cartridge branch really does depend on the target-presence operand.
    """
    fighters = (
        "Machamp (Hardy L50, 31 IV / 0 EV, Atk 150) vs "
        "Snorlax (Hardy L50, 31 IV / 0 EV, Def 85, HP 235)"
    )
    cases = [
        (
            "cartridge_doubles_reflect_both_defenders_present",
            "Strength",
            True,
            "Reflect in a Doubles battle with both defending battlers present: CalculateBaseDamage "
            "runs `damage = 2 * (damage / 3)` on the pre-roll value, so 2 * floor(62/3) = 40 and "
            "the post-+2 value is 42. " + fighters,
        ),
        (
            "cartridge_doubles_reflect_one_defender_present",
            "Strength",
            False,
            "The same Reflect with only one defending battler present falls back to `damage /= 2`, "
            "so floor(62/2) = 31 and the post-+2 value is 33: the format label alone cannot select "
            "the branch. " + fighters,
        ),
    ]

    references = []
    for reference_id, move_name, both_present, provenance in cases:
        raw = {
            "attacker": participant("Machamp"),
            "defender": participant("Snorlax"),
            "move": {"name": move_name},
            "field": {
                "gameType": "Doubles",
                "weather": None,
                "defenderSide": {"isReflect": True},
            },
        }
        result = gen3_reference.doubles_cartridge_rolls(raw, both_present)
        references.append({
            "id": reference_id,
            "covers": [
                "Doubles Reflect cartridge arithmetic (deliberately outside the VERIFIED surface)"
            ],
            "evidence": "SOURCE VERIFIED (pinned pret CalculateBaseDamage)",
            "sourceAnchor": (
                "pret/pokefirered src/pokemon.c:2547 (CalculateBaseDamage, Reflect branch) and "
                ":2598 (Light Screen branch); identical text at pret/pokeemerald "
                "src/pokemon.c:3270 and :3321"
            ),
            "refusedBy": "CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED",
            "provenance": provenance,
            "request": serialise_request(raw),
            "bothDefendersPresent": result["bothDefendersPresent"],
            "screenOperation": result["screenOperation"],
            "preRollDamage": result["preRollDamage"],
            "screenedPreRollDamage": result["screenedPreRollDamage"],
            "expected": {
                "damage": result["damage"],
                "minDamage": result["minDamage"],
                "maxDamage": result["maxDamage"],
                "moveType": result["moveType"],
                "moveCategory": result["moveCategory"],
                "movePower": result["movePower"],
            },
        })
    return references


def build_document():
    fixtures = []
    for raw in build_fixtures():
        request_str = serialise_request(raw["input"])
        result = gen3_reference.calculate(json.loads(request_str))
        fixtures.append({
            **raw,
            "request": request_str,
            "expected": {
                "damage": result["damage"],
                "minDamage": result["minDamage"],
                "maxDamage": result["maxDamage"],
                "moveType": result["moveType"],
                "moveCategory": result["moveCategory"],
                "movePower": result["movePower"],
            },
        })

    return {
        "schema": "dualdex.vanilla_gen3_goldens.v1",
        "ruleset": "VANILLA_GEN3",
        "mechanicsGeneration": 3,
        "cartridgeReferences": build_cartridge_references(),
        "provenance": {
            "oracle": {
                "kind": "independent Generation III reference implementation",
                "path": "tools/calc-goldens/gen3_reference.py",
                "verified_by": "tools/calc-goldens/verify_goldens.py",
                "sources": [
                    {
                        "project": "pret/pokeemerald",
                        "commit": POKEEMERALD_COMMIT,
                        "use": "CalculateBaseDamage, gStatStageRatios, ApplyRandomDmgMultiplier, critical hits and STAB",
                    },
                    {
                        "project": "pret/pokefirered",
                        "commit": POKEFIRERED_COMMIT,
                        "use": "same Generation III arithmetic and constants as Emerald for every fixture",
                    },
                ],
            },
            "engine": {
                "bundle": "app/src/main/assets/calc_bundle.js",
                "library": "@smogon/calc 0.11.0",
                "hostSuite": "native/tests/test_js_calc.c",
            },
            "profiles": {
                profile_id: {
                    "sha256": hashes,
                    "dataPackId": "gen3_vanilla",
                }
                for profile_id, hashes in PROFILE_SHA256.items()
            },
        },
        "fixtures": fixtures,
    }


def _bundled_hashes(profile_id):
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    path = os.path.join(repo_root, "app", "src", "main", "assets", "profiles",
                        f"{profile_id}.json")
    with open(path, encoding="utf-8") as handle:
        return json.load(handle).get("sha256Hashes", [])


def verify():
    with open(FIXTURE_PATH, encoding="utf-8") as handle:
        document = json.load(handle)

    errors = []

    expected_doc = build_document()
    if document.get("schema") != expected_doc["schema"]:
        errors.append(f"schema mismatch: {document.get('schema')!r}")

    # Provenance hashes must be exactly the bundled profile hashes; a profile
    # edit that changes trust must fail this check rather than silently drift.
    for profile_id, hashes in PROFILE_SHA256.items():
        bundled = _bundled_hashes(profile_id)
        if bundled != hashes:
            errors.append(
                f"{profile_id}: bundled profile hashes {bundled} != fixture provenance {hashes}")

    committed = {fixture["id"]: fixture for fixture in document["fixtures"]}
    expected = {fixture["id"]: fixture for fixture in expected_doc["fixtures"]}
    if set(committed) != set(expected):
        errors.append(f"fixture ids differ: {sorted(set(expected) ^ set(committed))}")

    for fixture_id, expected_fixture in expected.items():
        actual_fixture = committed.get(fixture_id)
        if actual_fixture is None:
            continue
        if actual_fixture.get("request") != expected_fixture["request"]:
            errors.append(f"{fixture_id}: committed request does not match serializer output")
        actual_expected = actual_fixture.get("expected")
        if actual_expected != expected_fixture["expected"]:
            errors.append(
                f"{fixture_id}: golden {actual_expected} != independent oracle "
                f"{expected_fixture['expected']}")

    # Source-exact Doubles screen references (outside the engine matrix).
    committed_refs = {ref["id"]: ref for ref in document.get("cartridgeReferences", [])}
    expected_refs = {ref["id"]: ref for ref in expected_doc["cartridgeReferences"]}
    if set(committed_refs) != set(expected_refs):
        errors.append(
            f"cartridge reference ids differ: {sorted(set(expected_refs) ^ set(committed_refs))}")
    for reference_id, expected_ref in expected_refs.items():
        actual_ref = committed_refs.get(reference_id)
        if actual_ref is None:
            continue
        for key in ("request", "expected", "screenOperation", "preRollDamage",
                    "screenedPreRollDamage", "bothDefendersPresent", "refusedBy"):
            if actual_ref.get(key) != expected_ref[key]:
                errors.append(
                    f"{reference_id}: committed {key} {actual_ref.get(key)!r} != source-exact "
                    f"oracle {expected_ref[key]!r}")

    # The two committed reference vectors must genuinely differ, which is the whole reason the
    # production gate exists: if they ever agree, the gate is refusing a shape the pipeline now
    # reproduces and this check forces the discussion.
    vectors = {ref["id"]: ref["expected"]["damage"] for ref in expected_refs.values()}
    if len(vectors) == 2:
        distinct = len({tuple(vector) for vector in vectors.values()})
        if distinct != 2:
            errors.append(
                "the two cartridge Doubles Reflect references produced the same vector; the "
                "target-presence branch is no longer observable")

    if errors:
        print("VANILLA GEN III GOLDEN VERIFICATION FAILED:")
        for error in errors:
            print(f"  - {error}")
        return 1

    print(f"vanilla Gen III goldens verified: {len(expected)} fixtures, "
          f"all expected rolls match the independent oracle; "
          f"{len(expected_refs)} source-exact Doubles screen references verified (refused by "
          f"production); profile hashes match the bundled FireRed/Emerald profiles")
    return 0


def emit():
    with open(FIXTURE_PATH, "w", encoding="utf-8") as handle:
        json.dump(build_document(), handle, indent=2)
        handle.write("\n")
    print(f"wrote {FIXTURE_PATH}")
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emit", action="store_true", help="regenerate the fixture file")
    args = parser.parse_args(argv)
    return emit() if args.emit else verify()


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
