#!/usr/bin/env python3
"""Independent Generation III damage reference (vanilla FireRed / Emerald).

This module is a deliberately small, transparent re-implementation of the
Generation III damage calculation used to derive the expected roll vectors in
``vanilla_gen3_goldens.json``. It is NOT the DualDex calculator and it does not
import, execute, or read the shipped ``calc_bundle.js`` / ``@smogon/calc``
bundle. It is the oracle, so it must be independent of the thing under test.

Provenance
----------
Every constant and every step below is taken from the vanilla Generation III
engines, pinned to the public decompilation projects:

  * pret/pokeemerald  (Microsoft, and the canonical Emerald decompilation)
      - base damage  src/pokemon.c :: CalculateBaseDamage
      - stat stages  src/pokemon.c :: gStatStageRatios / ApplyStatStage
      - crit / STAB / type / roll order  src/battle_script_commands.c
        (Cmd_damagecalc, Cmd_critcalc) and src/battle_util.c
        (CalcTypeEffectivenessMultiplier)
  * pret/pokefirered   (the FireRed/LeafGreen decompilation; shares the
        Generation III engine and damage arithmetic with Emerald)

The exact pinned revisions are recorded in the golden fixture file and in
``docs/VANILLA_CALCULATOR_EVIDENCE.md``. FireRed and Emerald are the same
Generation III engine for this arithmetic, so one reference covers both; the
per-game identity claim is established at the trust/boundary layer, not here.

What this reference intentionally does NOT model
------------------------------------------------
Only the ordinary single-hit damage path the DualDex VERIFIED claim is bounded
to: neutral stat values, the six modelled statuses, Gen III abilities the
pipeline applies, the Gen III type-based physical/special split, weather, burn,
screens, critical hits and the 85..100 roll. It models no later-generation
mechanic.

Usage
-----
    import gen3_reference
    result = gen3_reference.calculate(request_dict)
    result["damage"]  # 16 rolls

See ``verify_goldens.py`` for the committed-fixture verification entry point.
"""

from __future__ import annotations

from math import floor

# ---------------------------------------------------------------------------
# Generation III type partition (physical vs special).
#
# Gen III decides the damage category from the MOVE TYPE, not from a per-move
# category field. Encoding the partition here - instead of trusting a modern
# per-move category - is what makes fixture B a real check of generation
# behaviour. Source: pret/pokeemerald include/constants/battle.h
# (IS_TYPE_PHYSICAL) and src/pokemon.c.
# ---------------------------------------------------------------------------
PHYSICAL_TYPES = {
    "Normal", "Fighting", "Flying", "Poison", "Ground", "Rock", "Bug",
    "Ghost", "Steel",
}
SPECIAL_TYPES = {
    "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark",
}


def category_for_type(type_name: str) -> str:
    if type_name in PHYSICAL_TYPES:
        return "Physical"
    if type_name in SPECIAL_TYPES:
        return "Special"
    raise ValueError(f"unknown Generation III type: {type_name!r}")


# ---------------------------------------------------------------------------
# Base stats (HP, Atk, Def, SpA, SpD, Spe) for the species used by the matrix.
# Source: pret/pokeemerald src/data/pokemon/base_stats.h and
# pret/pokefirered src/data/pokemon/base_stats.h (identical values across the
# two Generation III games).
# ---------------------------------------------------------------------------
SPECIES = {
    "Machamp": (90, 130, 80, 65, 85, 55),
    "Snorlax": (160, 110, 65, 65, 110, 30),
}

# Species typings, derived from the same pinned base-stat tables as the stats
# above (src/data/pokemon/base_stats.h). The engine derives typings from the
# species record, so the reference does too; no fixture supplies a type.
SPECIES_TYPES = {
    "Machamp": ["Fighting"],
    "Snorlax": ["Normal"],
}

# Base power, type and target class for the moves used by the matrix. Category
# is derived from the type above, never supplied. Source: pret/pokeemerald
# src/data/moves_info.h (gMovesInfo) and pret/pokefirered src/data/moves.h.
# `spread=True` marks a move whose Generation III target is both opposing
# battlers (Rock Slide), which the engine halves in a non-Singles format.
MOVES = {
    "Rock Slide": ("Rock", 75, True),
    "Crunch": ("Dark", 80, False),
    "Karate Chop": ("Fighting", 50, False),
    "Strength": ("Normal", 80, False),
    "Hydro Pump": ("Water", 120, False),
    "Flamethrower": ("Fire", 95, False),
    "Thunderbolt": ("Electric", 95, False),
}

# Attack-type -> defender-type multipliers for the matchups the matrix uses.
# Generation III chart. Source: pret/pokeemerald src/battle_util.c
# gTypeEffectiveness and the per-type tables in src/data/types.h.
# Only matchups exercised by the committed fixtures are listed; an unlisted
# pair means 1.0 (neutral) and is deliberately explicit rather than implied.
TYPE_EFFECTIVENESS = {
    ("Fighting", "Normal"): 2.0,
    ("Rock", "Normal"): 1.0,
    ("Dark", "Normal"): 1.0,
    ("Normal", "Normal"): 1.0,
    ("Water", "Normal"): 1.0,
    ("Fire", "Normal"): 1.0,
    ("Electric", "Normal"): 1.0,
}


def _normalise_types(types):
    if isinstance(types, str):
        return [types]
    return list(types)


def type_multiplier(attack_type: str, defender_types) -> float:
    multiplier = 1.0
    for defender_type in _normalise_types(defender_types):
        multiplier *= TYPE_EFFECTIVENESS.get((attack_type, defender_type), 1.0)
    return multiplier


# ---------------------------------------------------------------------------
# Stat computation. Source: pret/pokeemerald src/pokemon.c CalculateMonStats.
# ---------------------------------------------------------------------------
def _stat_value(base: int, iv: int, ev: int, level: int) -> int:
    return floor((2 * base + iv + ev // 4) * level / 100) + 5


def _hp_value(base: int, iv: int, ev: int, level: int) -> int:
    return floor((2 * base + iv + ev // 4) * level / 100) + level + 10


def raw_stats(species: str, level: int, ivs: dict, evs: dict) -> dict:
    base = SPECIES[species]
    return {
        "hp": _hp_value(base[0], ivs.get("hp", 31), evs.get("hp", 0), level),
        "atk": _stat_value(base[1], ivs.get("atk", 31), evs.get("atk", 0), level),
        "def": _stat_value(base[2], ivs.get("def", 31), evs.get("def", 0), level),
        "spa": _stat_value(base[3], ivs.get("spa", 31), evs.get("spa", 0), level),
        "spd": _stat_value(base[4], ivs.get("spd", 31), evs.get("spd", 0), level),
        "spe": _stat_value(base[5], ivs.get("spe", 31), evs.get("spe", 0), level),
    }


# ---------------------------------------------------------------------------
# Generation III stat-stage application. Source: pret/pokeemerald
# src/pokemon.c gStatStageRatios and the integer conversion used there:
#   stage >= 0 : floor(stat * (2 + stage) / 2)
#   stage <  0 : floor(stat * 2 / (2 - stage))
# A critical hit ignores the attacker's negative stages and the defender's
# positive stages (handled by the caller).
# ---------------------------------------------------------------------------
def modified_stat(stat: int, stage: int) -> int:
    if stage >= 0:
        return floor(stat * (2 + stage) / 2)
    return floor(stat * 2 / (2 - stage))


# ---------------------------------------------------------------------------
# Naturals: the matrix uses Hardy (neutral) only.
#
# A nature multiplies one stat by 1.1 and another by 0.9 with the result floored, and the exact
# rounding is observable in the final rolls. This reference deliberately does not implement that
# (none of the committed fixtures need it) but it must not silently ignore the field either: a
# future fixture with `"nature": "Adamant"` would otherwise be "verified" against an oracle that
# quietly computed the neutral value.
# ---------------------------------------------------------------------------
NEUTRAL_NATURES = {None, "", "Hardy", "Docile", "Serious", "Bashful", "Quirky"}


def _require_neutral_nature(participant: dict, role: str) -> None:
    nature = participant.get("nature")
    if nature not in NEUTRAL_NATURES:
        raise ValueError(
            f"unsupported nature {nature!r} for the {role}: this reference implements only the "
            "neutral natures (Hardy/Docile/Serious/Bashful/Quirky) that every committed fixture "
            "uses; add nature handling before using it as an oracle for a non-neutral fixture"
        )


# Ability handling for the modelled vanilla Gen III subset. The names and the
# constants come from the same decompilation (src/battle_util.c
# CalcAttackStat / CalcDefenseStat and src/pokemon.c).
def _attacker_stat(attacker, defender, move_type, is_physical, is_crit):
    stats = raw_stats(attacker["species"], attacker["level"],
                      attacker.get("ivs", {}), attacker.get("evs", {}))
    stat = stats["atk" if is_physical else "spa"]
    ability = attacker.get("ability")
    item = attacker.get("item")
    status = attacker.get("status")

    if is_physical and ability in ("Huge Power", "Pure Power"):
        stat *= 2
    if item == "Choice Band" and is_physical:
        stat = floor(stat * 1.5)
    if defender.get("ability") == "Thick Fat" and move_type in ("Fire", "Ice"):
        stat = floor(stat / 2)
    if (is_physical and ability == "Hustle") or (ability == "Guts" and status):
        stat = floor(stat * 1.5)

    stage = (attacker.get("boosts") or {}).get("atk" if is_physical else "spa", 0)
    if stage > 0 or (not is_crit and stage < 0):
        stat = modified_stat(stat, stage)
    return stat, stats


def _defender_stat(defender, is_physical, is_crit, move_name):
    stats = raw_stats(defender["species"], defender["level"],
                      defender.get("ivs", {}), defender.get("evs", {}))
    stat = stats["def" if is_physical else "spd"]
    if is_physical and defender.get("ability") == "Marvel Scale" and defender.get("status"):
        stat = floor(stat * 1.5)
    if move_name in ("Explosion", "Self-Destruct"):
        stat = floor(stat / 2)

    stage = (defender.get("boosts") or {}).get("def" if is_physical else "spd", 0)
    if stage < 0 or (not is_crit and stage > 0):
        stat = modified_stat(stat, stage)
    return stat, stats


def calculate(request: dict) -> dict:
    """Compute the 16-roll Generation III damage vector for one request.

    ``request`` is the same shape ``buildCalcRequestJson`` sends the engine:
    attacker/defender carry species, level, optional ivs/evs/boosts/status/
    ability/item; move carries name/isCrit; field carries gameType/weather/
    defenderSide.
    """
    attacker = request["attacker"]
    defender = request["defender"]
    _require_neutral_nature(attacker, "attacker")
    _require_neutral_nature(defender, "defender")
    move_input = request["move"]
    field = request.get("field", {})

    move_name = move_input["name"]
    move_type, base_power, spread = MOVES[move_name]
    category = category_for_type(move_type)
    is_physical = category == "Physical"
    is_crit = bool(move_input.get("isCrit"))

    attacker_types = SPECIES_TYPES[attacker["species"]]
    defender_types = SPECIES_TYPES[defender["species"]]

    # Type effectiveness is computed before the roll, and the two defender
    # types are floored separately (gen3.js applies type1 then type2).
    eff = type_multiplier(move_type, defender_types)

    attack, _ = _attacker_stat(attacker, defender, move_type, is_physical, is_crit)
    defense, _ = _defender_stat(defender, is_physical, is_crit, move_name)

    level = attacker["level"]
    base = floor(floor(floor((2 * level) / 5 + 2) * attack * base_power) / defense / 50)

    # calculateFinalModsADV order, pinned to the Generation III engine.
    status = attacker.get("status")
    if status == "brn" and is_physical and attacker.get("ability") != "Guts":
        base = floor(base / 2)

    # Battle-format arithmetic. Two different things live here and they must not be conflated:
    #
    #  * the Doubles SCREEN is a cartridge operation with integer division done first
    #    (`damage = 2 * (damage / 3)`), which is what `doubles_cartridge_rolls` below
    #    implements. This reference computes it exactly, because the source does.
    #  * the shipped `@smogon/calc` 0.11.0 pipeline this module mirrors for the *pipeline*
    #    fixtures applies `floor(value * 2 / 3)` to the rolled value instead.
    #
    # A request that reaches the screen branch in Doubles is therefore rejected by the caller:
    # `calculate` cannot say which of the two a consumer means, and guessing would silently
    # produce a vector that differs from the cartridge on ten of sixteen rolls.
    game_type = field.get("gameType", "Singles")
    defender_side = field.get("defenderSide") or {}
    doubles_screen = (
        game_type != "Singles"
        and ((is_physical and defender_side.get("isReflect"))
             or (not is_physical and defender_side.get("isLightScreen")))
    )
    if doubles_screen and not is_crit:
        raise ValueError(
            "Doubles Reflect/Light Screen: this reference models the cartridge's "
            "2 * (damage / 3) only through doubles_cartridge_rolls(), because the shipped "
            "@smogon/calc pipeline applies the screen to the ROLLED value as floor(x * 2/3) and "
            "the two disagree. Pass the request to doubles_cartridge_rolls() when the cartridge "
            "arithmetic is what you mean, and use only Singles screen fixtures here."
        )

    if not is_crit:
        if is_physical and defender_side.get("isReflect"):
            base = floor(base / 2)
        elif not is_physical and defender_side.get("isLightScreen"):
            base = floor(base / 2)

    if game_type != "Singles" and spread:
        base = floor(base / 2)

    weather = field.get("weather")
    if (weather == "Sun" and move_type == "Fire") or (weather == "Rain" and move_type == "Water"):
        base = floor(base * 1.5)
    elif (weather == "Sun" and move_type == "Water") or (weather == "Rain" and move_type == "Fire"):
        base = floor(base / 2)

    if is_physical:
        base = max(1, base)
    base += 2
    if is_crit:
        base *= 2

    if move_type in attacker_types:
        base = floor(base * 1.5)

    # Type effectiveness is applied per defending type with a floor each time.
    for defender_type in defender_types:
        base = floor(base * TYPE_EFFECTIVENESS.get((move_type, defender_type), 1.0))

    damage = [max(1, floor(base * roll / 100)) for roll in range(85, 101)]
    return {
        "damage": damage,
        "minDamage": damage[0],
        "maxDamage": damage[-1],
        "moveType": move_type,
        "moveCategory": category,
        "movePower": base_power,
        "effectiveness": eff,
    }


# ---------------------------------------------------------------------------
# Cartridge Doubles screen arithmetic (source-exact, independent of the bundle).
#
# `pret/pokefirered src/pokemon.c :: CalculateBaseDamage` (identical text in
# `pret/pokeemerald src/pokemon.c`), inside the physical branch for Reflect and
# the special branch for Light Screen:
#
#     if ((sideStatus & SIDE_STATUS_REFLECT) && gCritMultiplier == 1)
#     {
#         if ((gBattleTypeFlags & BATTLE_TYPE_DOUBLE)
#          && CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2)
#             damage = 2 * (damage / 3);
#         else
#             damage /= 2;
#     }
#
# Three facts are encoded here because they are exactly what the shipped
# `@smogon/calc` 0.11.0 pipeline does not reproduce:
#
#   1. the division by 3 is INTEGER division on the pre-roll damage, so
#      `2 * (damage / 3)` is NOT `floor(damage * 2 / 3)`;
#   2. the branch is selected by how many defending battlers are PRESENT, not by
#      the format label alone;
#   3. the screen is applied to the pre-roll value, before `+2` and the roll.
#
# `bothDefendersPresent` is that target-presence operand. A caller that cannot
# supply it cannot ask for the Doubles branch.
# ---------------------------------------------------------------------------
def doubles_cartridge_rolls(request: dict, both_defenders_present: bool) -> dict:
    """The Doubles Reflect / Light Screen roll vector the cartridge produces.

    ``request`` has the same shape :func:`calculate` takes and must be a Doubles
    request with an active Reflect (physical move) or Light Screen (special
    move). ``both_defenders_present`` is
    ``CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2``: when it is false the
    cartridge falls back to ``damage /= 2`` and no screen multiplier can be
    inferred from the format label.

    Raises ValueError for anything this function does not model, so it can never
    return a confident wrong vector.
    """
    attacker = request["attacker"]
    defender = request["defender"]
    move_input = request["move"]
    field = request.get("field", {})

    _require_neutral_nature(attacker, "attacker")
    _require_neutral_nature(defender, "defender")

    game_type = field.get("gameType", "Singles")
    if game_type == "Singles":
        raise ValueError("doubles_cartridge_rolls models the Doubles branch only")
    if bool(move_input.get("isCrit")):
        raise ValueError("a critical hit skips the screen branch entirely (gCritMultiplier == 1)")

    move_name = move_input["name"]
    move_type, base_power, spread = MOVES[move_name]
    category = category_for_type(move_type)
    is_physical = category == "Physical"

    defender_side = field.get("defenderSide") or {}
    if is_physical and not defender_side.get("isReflect"):
        raise ValueError("a physical Doubles request needs defenderSide.isReflect for this branch")
    if not is_physical and not defender_side.get("isLightScreen"):
        raise ValueError("a special Doubles request needs defenderSide.isLightScreen for this branch")

    attack, _ = _attacker_stat(attacker, defender, move_type, is_physical, False)
    defense, _ = _defender_stat(defender, is_physical, False, move_name)
    level = attacker["level"]
    pre_roll = floor(floor(floor((2 * level) / 5 + 2) * attack * base_power) / defense / 50)

    status = attacker.get("status")
    if status == "brn" and is_physical and attacker.get("ability") != "Guts":
        pre_roll = floor(pre_roll / 2)

    # The screen, with the cartridge's operation order and branch condition.
    if both_defenders_present:
        screened = 2 * (pre_roll // 3)
    else:
        screened = pre_roll // 2

    # Moves hitting both targets do half damage in a double battle, also in the pre-roll value, and
    # also only while both defending battlers are present.
    if spread and both_defenders_present:
        screened = screened // 2

    value = screened
    if is_physical:
        value = max(1, value)
    value += 2

    if move_type in SPECIES_TYPES[attacker["species"]]:
        value = floor(value * 1.5)
    for defender_type in SPECIES_TYPES[defender["species"]]:
        value = floor(value * TYPE_EFFECTIVENESS.get((move_type, defender_type), 1.0))

    damage = [max(1, floor(value * roll / 100)) for roll in range(85, 101)]
    return {
        "damage": damage,
        "screenedPreRollDamage": screened,
        "preRollDamage": pre_roll,
        "screenOperation": "2 * (damage / 3)" if both_defenders_present else "damage / 2",
        "bothDefendersPresent": both_defenders_present,
        "minDamage": damage[0],
        "maxDamage": damage[-1],
        "moveType": move_type,
        "moveCategory": category,
        "movePower": base_power,
    }
