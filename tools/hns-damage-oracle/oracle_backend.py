"""Pinned H&S battle-engine backend for the differential damage oracle (issue #90).

Option A of the issue: the pinned H&S 2.0.5 tree (pokeemerald-expansion based) ships the expansion
battle test runner. For every scenario this module generates one ``SINGLE_BATTLE_TEST`` /
``DOUBLE_BATTLE_TEST`` with sixteen ``PARAMETRIZE`` runs; run ``i`` executes exactly one measured hit
with ``WITH_RNG(RNG_DAMAGE_MODIFIER, i)`` and ``criticalHit`` forced, captures the HP-bar damage and
prints the battle state it was computed from. Each parameter is a fresh battle, so nothing carries
over between rolls or scenarios.

The build is the upstream ``make BUILD=hns TEST=1 pokehns-test.elf`` target of an exported copy of
the pinned commit, run headlessly by the upstream ``mgba-rom-test-hydra`` + ``mgba-rom-test`` pair.
No ROM, BIOS or save is involved: the test ELF is compiled from source and never leaves the work
directory. Two things differ from a plain ``make check``, both documented and hashed into the
corpus provenance:

* ``patches/0001-test-runner-include-order.patch``: at the pinned commit H&S's ``include/fake_rtc.h``
  dereferences ``struct SaveBlock3`` while ``test/test_runner.c`` includes it before ``global.h``, so
  the stock test runner does not compile. The patch swaps two include lines in the test harness; it
  touches nothing under ``src/`` and no battle code;
* the upstream test *cases* are pruned (only the runner, its self-test and the headers ``src/`` needs
  are kept) so the ELF contains only the generated oracle tests. Upstream's own damage tests assert
  Emerald-English battle messages that H&S changed, so they cannot pass unmodified anyway.

This module never calls the DualDex calculator, ``calc_bundle.js``, ``@smogon/calc`` or Kotlin code.
"""

from __future__ import annotations

import hashlib
import os
import re
import shutil
import subprocess
import io
import json
import tarfile
from pathlib import Path

from oracle_schema import (
    RUNTIME_DOMAINS,
    HNS_PINNED_COMMIT,
    HNS_PINNED_TREE,
    MAX_MEASURABLE_DAMAGE,
    ROLL_COUNT,
    TYPE_NAMES,
)

MOVE_DAMAGE_METADATA = json.loads((Path(__file__).resolve().parent.parent /
                                   "hns-move-mechanics/hns_move_damage_metadata.json").read_text())["moves"]

TOOL_DIR = Path(__file__).resolve().parent
PATCH_DIR = TOOL_DIR / "patches"
HARNESS_PATCHES = ("0001-test-runner-include-order.patch", "0002-pre-damage-state-hook.patch")
TEST_SUBDIR = "test/dualdex_oracle"
TEST_PREFIX = "DDXO "
SCENARIOS_PER_FILE = 100
BUILD_COMMAND = "make BUILD=hns TEST=1 pokehns-test.elf"
RUNNER_DESCRIPTION = "pinned tools/mgba-rom-test-hydra + tools/mgba/mgba-rom-test, headless test ELF"

KEEP_TEST_FILES = {
    "test/test_runner.c", "test/test_runner_args.c", "test/test_runner_battle.c", "test/test_test_runner.c",
    "test/battle/trainer_control.c", "test/battle/trainer_control.party", "test/battle/trainer_control.h",
    "test/battle/partner_control.party", "test/battle/partner_control.h", "test/battle/trainer_slides.h",
}

# Setup moves that establish stat stages. Keys: (battler whose stage changes, stat, delta).
STAGE_MOVES = {
    ("attacker", "attack", +2): ("attacker", "MOVE_SWORDS_DANCE"),
    ("attacker", "attack", +1): ("attacker", "MOVE_MEDITATE"),
    ("attacker", "attack", -2): ("defender", "MOVE_FEATHER_DANCE"),
    ("attacker", "attack", -1): ("defender", "MOVE_GROWL"),
    ("attacker", "spAttack", +2): ("attacker", "MOVE_NASTY_PLOT"),
    ("attacker", "spAttack", +1): ("attacker", "MOVE_CALM_MIND"),
    ("attacker", "spAttack", -2): ("defender", "MOVE_EERIE_IMPULSE"),
    ("attacker", "spAttack", -1): ("defender", "MOVE_CONFIDE"),
    ("defender", "defense", +2): ("defender", "MOVE_IRON_DEFENSE"),
    ("defender", "defense", +1): ("defender", "MOVE_HARDEN"),
    ("defender", "defense", -2): ("attacker", "MOVE_SCREECH"),
    ("defender", "defense", -1): ("attacker", "MOVE_LEER"),
    ("defender", "spDefense", +2): ("defender", "MOVE_AMNESIA"),
    ("defender", "spDefense", +1): ("defender", "MOVE_CALM_MIND"),
    ("defender", "spDefense", -2): ("attacker", "MOVE_FAKE_TEARS"),
}
PARTNER_KO_MOVE = "MOVE_KARATE_CHOP"

C_TYPE_TABLE = {name: f"TYPE_{name.upper()}" for name in TYPE_NAMES}


class OracleError(RuntimeError):
    """Raised when the oracle cannot produce a trustworthy result. Never converted to a value."""


# --------------------------------------------------------------------------------------------
# Setup-turn planning
# --------------------------------------------------------------------------------------------

def _stage_actions(role: str, stat: str, value: int) -> list[tuple[str, str]]:
    """Moves (actor, MOVE_*) that take `role`'s `stat` stage from 0 to `value`.

    One move per stat and direction keeps every battler within the four-move limit: an even value
    repeats the two-stage move, an odd one repeats the one-stage move. Sp. Def has no one-stage
    status drop, so an odd drop overshoots with the two-stage move and recovers with +1.
    """
    if value == 0:
        return []
    sign = 1 if value > 0 else -1
    remaining = abs(value)
    if remaining % 2 == 0:
        return [STAGE_MOVES[(role, stat, 2 * sign)]] * (remaining // 2)
    if (role, stat, sign) in STAGE_MOVES:
        return [STAGE_MOVES[(role, stat, sign)]] * remaining
    return [STAGE_MOVES[(role, stat, -2)]] * (remaining // 2 + 1) + [STAGE_MOVES[(role, stat, +1)]]


MAX_MON_MOVES = 4


def plan_setup(scenario: dict) -> tuple[list[str], list[str], bool]:
    """Return (attacker moves per setup turn, defender moves per setup turn, partner KO on turn 1).

    Both lists have the same length (the number of setup turns); ``""`` means "no explicit action"
    (the runner's default, Celebrate). Weather and screens are placed on the final setup turn so
    they are active for the measured hit well within their five-turn duration.
    """
    atk_actions: list[str] = []
    def_actions: list[str] = []
    for role, stats in (("attacker", scenario["attacker"]["stages"]), ("defender", scenario["defender"]["stages"])):
        for stat in sorted(stats):
            for actor, move in _stage_actions(role, stat, stats[stat]):
                (atk_actions if actor == "attacker" else def_actions).append(move)
    weather = scenario["field"]["weather"]
    # A live Drought holder establishes Sun on entry without a setup turn. This matters for
    # Solar Power oracle vectors: spending a turn on Sunny Day would trigger Solar Power's
    # explicitly out-of-scope end-of-turn HP loss before the selected hit is measured.
    drought_establishes_sun = weather == "sun" and any(
        battler["ability"] == "ABILITY_DROUGHT"
        for battler in (scenario["attacker"], scenario["defender"])
    )
    if weather == "rain":
        atk_actions.append("MOVE_RAIN_DANCE")
    elif weather == "sun" and not drought_establishes_sun:
        atk_actions.append("MOVE_SUNNY_DAY")
    elif weather == "sandstorm":
        atk_actions.append("MOVE_SANDSTORM")
    if scenario["field"]["reflect"]:
        def_actions.append("MOVE_REFLECT")
    if scenario["field"]["lightScreen"]:
        def_actions.append("MOVE_LIGHT_SCREEN")
    if scenario["field"]["terrain"] == "grassy":
        atk_actions.append("MOVE_GRASSY_TERRAIN")
    elif scenario["field"]["terrain"] == "electric":
        atk_actions.append("MOVE_ELECTRIC_TERRAIN")
    elif scenario["field"]["terrain"] == "misty":
        atk_actions.append("MOVE_MISTY_TERRAIN")
    elif scenario["field"]["terrain"] == "psychic":
        atk_actions.append("MOVE_PSYCHIC_TERRAIN")
    if scenario["field"]["gravity"]:
        atk_actions.append("MOVE_GRAVITY")
    if (scenario.get("stateSetup") or {}).get("wonderRoom"):
        atk_actions.append("MOVE_WONDER_ROOM")
    partner_ko = scenario["format"] == "doubles" and scenario["doubles"]["defenderPartner"] == "fainted"
    turns = max(len(atk_actions), len(def_actions), 1 if partner_ko else 0)
    atk_actions = [""] * (turns - len(atk_actions)) + atk_actions
    def_actions = [""] * (turns - len(def_actions)) + def_actions
    return atk_actions, def_actions, partner_ko


def _solar_power_weather_affected(scenario: dict) -> bool:
    battlers = (scenario["attacker"], scenario["defender"])
    return (
        scenario["attacker"]["ability"] == "ABILITY_SOLAR_POWER"
        and scenario["field"]["weather"] == "sun"
        and not any(b["ability"] in ("ABILITY_CLOUD_NINE", "ABILITY_AIR_LOCK") for b in battlers)
        and scenario["attacker"]["item"] != "ITEM_UTILITY_UMBRELLA"
    )


def _solar_power_setup_ticks(scenario: dict, setup_turns: int) -> int:
    """Solar Power EOT ticks before the measured attack, for HP-at-hit capture planning."""
    if setup_turns <= 0 or not _solar_power_weather_affected(scenario):
        return 0
    battlers = (scenario["attacker"], scenario["defender"])
    if any(b["ability"] == "ABILITY_DROUGHT" for b in battlers):
        return setup_turns
    # Without Drought the planner establishes Sun with Sunny Day as its last setup action.
    return 1


# --------------------------------------------------------------------------------------------
# C test generation
# --------------------------------------------------------------------------------------------

C_PRELUDE = r'''// GENERATED by tools/hns-damage-oracle (DualDex issue #90). DO NOT EDIT.
// Each test measures one damaging hit sixteen times, once per damage roll, in a fresh battle.
#include "global.h"
#include "battle_util.h"
#include "event_data.h"
#include "battle_main.h"
#include "battle_gimmick.h"
#include "battle_dynamax.h"
#include "pokemon.h"
#include "test/battle.h"

// Non-static in the pinned src/battle_util.c but not declared in any pinned header.
u32 GetMoveTargetCount(struct BattleContext *ctx);

// Test-runner hook reached inside IsCriticalHit, before DoMoveDamageCalc.
extern void (*gDdxoBeforeCriticalHit)(void);
static u16 sDdxoSpeciesAtHit[MAX_BATTLERS_COUNT];
static u32 sDdxoSetupCalls;
static bool32 sDdxoUseHitSpecies;
static u32 sDdxoRoll;
static u32 sDdxoBasePowerAtHit;

static inline void DdxoRuntime(const char *id, const char *role, enum BattlerId b)
{
    const struct BattlePokemon *m = &gBattleMons[b];
    Test_MgbaPrintf("DDXO|%%s|%%d|%%sG|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d",
        id, sDdxoRoll, role, m->personality, GetGenderFromSpeciesAndPersonality(m->species, m->personality),
        m->volatiles.slowStartTimer, m->volatiles.flashFireBoosted, m->volatiles.transformed,
        m->volatiles.boosterEnergyActivated, m->volatiles.paradoxBoostedStat,
        m->volatiles.vesselOfRuin, m->volatiles.swordOfRuin, m->volatiles.tabletsOfRuin,
        m->volatiles.beadsOfRuin, m->volatiles.gastroAcid, m->volatiles.neutralizingGas,
        gBattleStruct->battlerState[b].isFirstTurn, gBattleStruct->supremeOverlordCounter[b],
        IsGimmickSelected(b, GIMMICK_DYNAMAX), GetActiveGimmick(b), IsLastMonToMove(b),
        GetBattlerHoldEffectIgnoreAbility(b) == HOLD_EFFECT_ABILITY_SHIELD);
}

static const char *const sDdxoTypeNames[NUMBER_OF_MON_TYPES] =
{
%(type_table)s
    [TYPE_NONE] = "None",
    [TYPE_MYSTERY] = "Mystery",
    [TYPE_STELLAR] = "Stellar",
};

static inline const char *DdxoType(u32 type)
{
    return (type < NUMBER_OF_MON_TYPES && sDdxoTypeNames[type] != NULL) ? sDdxoTypeNames[type] : "Invalid";
}

static inline const char *DdxoStatus(u32 status1)
{
    if (status1 == 0)
        return "none";
    if (status1 == STATUS1_PARALYSIS)
        return "paralysis";
    if (status1 == STATUS1_BURN)
        return "burn";
    if (status1 & STATUS1_TOXIC_POISON)
        return "toxic";
    if (status1 == STATUS1_POISON)
        return "poison";
    return "other";
}

static inline const char *DdxoTarget(u32 target)
{
    switch (target)
    {
    case TARGET_SELECTED: return "selected";
    case TARGET_BOTH: return "both";
    case TARGET_FOES_AND_ALLY: return "foesAndAlly";
    default: return "other";
    }
}

static inline const char *DdxoWeather(u32 weather)
{
    if (weather == 0)
        return "none";
    if ((weather & B_WEATHER_RAIN) && !(weather & ~B_WEATHER_RAIN))
        return "rain";
    if ((weather & B_WEATHER_SUN) && !(weather & ~B_WEATHER_SUN))
        return "sun";
    if (weather == B_WEATHER_SANDSTORM)
        return "sandstorm";
    return "other";
}

static inline const char *DdxoCategory(u32 category)
{
    if (category == DAMAGE_CATEGORY_PHYSICAL)
        return "physical";
    if (category == DAMAGE_CATEGORY_SPECIAL)
        return "special";
    return "other";
}

static void DdxoBattler(const char *id, u32 roll, const char *role, u32 battler)
{
    const struct BattlePokemon *mon = &gBattleMons[battler];
    u32 species = sDdxoUseHitSpecies ? sDdxoSpeciesAtHit[battler] : mon->species;
    Test_MgbaPrintf("DDXO|%%s|%%d|%%s1|%%d|%%d|%%s|%%s|%%s|%%d|%%d|%%s|%%d", id, roll, role, species, mon->level,
        DdxoType(mon->types[0]), DdxoType(mon->types[1]), DdxoType(mon->types[2]), mon->ability, mon->item,
        DdxoStatus(mon->status1), (s32)mon->status1);
    Test_MgbaPrintf("DDXO|%%s|%%d|%%s2|%%d|%%d|%%d|%%d|%%d|%%d|%%d", id, roll, role, mon->hp, mon->maxHP, mon->attack,
        mon->defense, mon->spAttack, mon->spDefense, mon->speed);
    Test_MgbaPrintf("DDXO|%%s|%%d|%%s3|%%d|%%d|%%d|%%d", id, roll, role, mon->statStages[STAT_ATK] - DEFAULT_STAT_STAGE,
        mon->statStages[STAT_DEF] - DEFAULT_STAT_STAGE, mon->statStages[STAT_SPATK] - DEFAULT_STAT_STAGE,
        mon->statStages[STAT_SPDEF] - DEFAULT_STAT_STAGE);
    Test_MgbaPrintf("DDXO|%%s|%%d|%%s4|%%d|%%d|%%d|%%d|%%d|%%d", id, roll, role, GetSpeciesBaseHP(species),
        GetSpeciesBaseAttack(species), GetSpeciesBaseDefense(species), GetSpeciesBaseSpAttack(species),
        GetSpeciesBaseSpDefense(species), GetSpeciesBaseSpeed(species));
    Test_MgbaPrintf("DDXO|%%s|%%d|%%s5|%%d|%%d|%%d|%%d", id, roll, role,
        ShouldGetStatBadgeBoost(B_FLAG_BADGE_BOOST_ATTACK, battler) ? 1 : 0,
        ShouldGetStatBadgeBoost(B_FLAG_BADGE_BOOST_DEFENSE, battler) ? 1 : 0,
        ShouldGetStatBadgeBoost(B_FLAG_BADGE_BOOST_SPATK, battler) ? 1 : 0,
        ShouldGetStatBadgeBoost(B_FLAG_BADGE_BOOST_SPDEF, battler) ? 1 : 0);
    Test_MgbaPrintf("DDXO|%%s|%%d|%%s6|%%d", id, roll, role,
        IsBattlerTerrainAffected(battler, GetBattlerAbility(battler), GetBattlerHoldEffect(battler),
                                 gFieldStatuses, STATUS_FIELD_TERRAIN_ANY) ? 1 : 0);
}

static void DdxoHit(const char *id, u32 roll, enum Move move, u32 battlerAtk, u32 battlerDef, s32 damage,
    s32 defenderDelta, u32 hpAtHit)
{
    struct BattleContext ctx = {0};
    ctx.battlerAtk = battlerAtk;
    ctx.battlerDef = battlerDef;
    ctx.move = move;
    // The battle turn cleanup clears dynamicMoveType/ateBoost before THEN. Re-run the pinned
    // source entry point with the same live battler/move state so the corpus records the exact
    // effective type and explicit boost predicate the production adapter must serialize.
    SetTypeBeforeUsingMove(move, battlerAtk);
    DdxoBattler(id, roll, "A", battlerAtk);
    DdxoBattler(id, roll, "D", battlerDef);
    Test_MgbaPrintf("DDXO|%%s|%%d|M|%%d|%%s|%%d|%%s|%%s|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d|%%d", id, roll, move, DdxoType(GetBattleMoveType(move)),
        sDdxoUseHitSpecies ? sDdxoBasePowerAtHit : GetMovePower(move), DdxoCategory(GetBattleMoveCategory(move)), DdxoTarget(GetBattlerMoveTargetType(battlerAtk, move)),
        GetMoveTargetCount(&ctx), gLastMoves[battlerAtk], IsSoundMove(move), IsBallisticMove(move), IsWindMove(move),
        IsHealingMove(move), MoveIgnoresTargetAbility(move),
        GetBattleMovePriority(battlerAtk, gBattleMons[battlerAtk].ability, move), GetMoveTarget(move),
        IsPunchingMove(move), IsBitingMove(move), IsPulseMove(move), IsSlicingMove(move),
        gBattleStruct->battlerState[battlerAtk].ateBoost);
    Test_MgbaPrintf("DDXO|%%s|%%d|F|%%s|%%d|%%d|%%d|%%d|%%d|%%d", id, roll, DdxoWeather(gBattleWeather),
        (gSideStatuses[GetBattlerSide(battlerDef)] & SIDE_STATUS_REFLECT) ? 1 : 0,
        (gSideStatuses[GetBattlerSide(battlerDef)] & SIDE_STATUS_LIGHTSCREEN) ? 1 : 0,
        IsDoubleBattle() ? 1 : 0, gSaveBlock3Ptr->challengeSettings.tx_Mode_Fairy_Types,
        gSaveBlock3Ptr->challengeSettings.optionStyle, gFieldStatuses);
    Test_MgbaPrintf("DDXO|%%s|%%d|R|%%d|%%d|%%d", id, roll, damage, defenderDelta, hpAtHit);
}
'''

STATUS_C = {"none": "0", "burn": "STATUS1_BURN", "poison": "STATUS1_POISON",
            "toxic": "STATUS1_TOXIC_POISON", "paralysis": "STATUS1_PARALYSIS"}
BADGE_FLAG_C = "FLAG_BADGE0%d_GET"


def _c_mon(b: dict) -> str:
    st = b["stats"]
    parts = [f"Level({b['level']});", f"Ability({b['ability']});"]
    if b["item"] != "ITEM_NONE":
        parts.append(f"Item({b['item']});")
    parts += [f"MaxHP({st['maxHp']});", f"HP({st['hp']});", f"Attack({st['attack']});",
              f"Defense({st['defense']});", f"SpAttack({st['spAttack']});", f"SpDefense({st['spDefense']});",
              f"Speed({st['speed']});"]
    if b["status"] != "none":
        parts.append(f"Status1({STATUS_C[b['status']]});")
    return " ".join(parts)


def _partner_mon(hp: int) -> str:
    return (f"Level(50); Ability(ABILITY_INSOMNIA); MaxHP(60000); HP({hp}); Attack(200); Defense(100); "
            f"SpAttack(100); SpDefense(100); Speed(10);")


def render_scenario(s: dict) -> str:
    sid = s["id"]
    doubles = s["format"] == "doubles"
    atk_is_player = s["attackerSide"] == "player"
    if doubles and s["badges"]:
        raise OracleError(f"{sid}: Doubles battle tests are link battles, where badge boosts never apply")
    if doubles:
        atk_ref, def_ref = ("playerLeft", "opponentLeft") if atk_is_player else ("opponentLeft", "playerLeft")
        atk_partner, def_partner = ("playerRight", "opponentRight") if atk_is_player else ("opponentRight", "playerRight")
        atk_pos, def_pos = ("B_POSITION_PLAYER_LEFT", "B_POSITION_OPPONENT_LEFT") if atk_is_player else (
            "B_POSITION_OPPONENT_LEFT", "B_POSITION_PLAYER_LEFT")
        macro = "DOUBLE_BATTLE_TEST"
    else:
        atk_ref, def_ref = ("player", "opponent") if atk_is_player else ("opponent", "player")
        atk_partner = def_partner = None
        atk_pos, def_pos = ("B_POSITION_PLAYER_LEFT", "B_POSITION_OPPONENT_LEFT") if atk_is_player else (
            "B_POSITION_OPPONENT_LEFT", "B_POSITION_PLAYER_LEFT")
        # SINGLE_BATTLE_TEST is a recorded *link* battle, and the pinned ShouldGetStatBadgeBoost never
        # boosts in link battles (src/battle_util.c:9147). Badge scenarios therefore run as a wild
        # battle, as upstream's own test/battle/badge_boost.c does; the link flag gates nothing else
        # in the damage pipeline.
        macro = "WILD_BATTLE_TEST" if s["badges"] else "SINGLE_BATTLE_TEST"

    atk_c = _c_mon(s["attacker"])
    def_c = _c_mon(s["defender"])
    atk_species = s["attacker"]["species"]
    def_species = s["defender"]["species"]
    partner_hp = 1 if doubles and s["doubles"]["defenderPartner"] == "fainted" else 60000

    player_lines, opponent_lines = [], []
    atk_line = f"{'PLAYER' if atk_is_player else 'OPPONENT'}({atk_species}) {{ {atk_c} }}"
    def_line = f"{'OPPONENT' if atk_is_player else 'PLAYER'}({def_species}) {{ {def_c} }}"
    (player_lines if atk_is_player else opponent_lines).append(atk_line)
    (opponent_lines if atk_is_player else player_lines).append(def_line)
    if doubles:
        a_partner = f"{'PLAYER' if atk_is_player else 'OPPONENT'}(SPECIES_MACHAMP) {{ {_partner_mon(60000)} }}"
        d_partner = f"{'OPPONENT' if atk_is_player else 'PLAYER'}(SPECIES_SNORLAX) {{ {_partner_mon(partner_hp)} }}"
        (player_lines if atk_is_player else opponent_lines).append(a_partner)
        (opponent_lines if atk_is_player else player_lines).append(d_partner)

    atk_actions, def_actions, partner_ko = plan_setup(s)
    turns = []
    for t, (a_move, d_move) in enumerate(zip(atk_actions, def_actions)):
        cmds = []
        if a_move:
            cmds.append(f"MOVE({atk_ref}, {a_move});")
        if d_move:
            cmds.append(f"MOVE({def_ref}, {d_move});")
        if partner_ko and t == 0:
            cmds.append(f"MOVE({atk_partner}, {PARTNER_KO_MOVE}, target: {def_partner});")
        turns.append("TURN { " + " ".join(cmds) + " }")
    move = s["move"]["symbol"]
    if macro == "WILD_BATTLE_TEST":
        # The wild opponent repeats its first scripted action on every later turn (observed: a
        # turn-2 MOVE is replaced by the turn-1 one), so it may only act in a single-turn battle.
        opp_actions = def_actions if atk_is_player else atk_actions
        opp_acts = any(opp_actions) or not atk_is_player
        if opp_acts and len(atk_actions) > 0:
            raise OracleError(f"{sid}: a wild-battle opponent can only act in a single-turn scenario")
    for who, actions, measured in (("attacker", atk_actions, move), ("defender", def_actions, "")):
        known = {a or "MOVE_CELEBRATE" for a in actions} | {measured or "MOVE_CELEBRATE"}
        if len(known) > MAX_MON_MOVES:
            raise OracleError(f"{sid}: the {who} would need {len(known)} moves ({sorted(known)}); "
                              f"a battler knows at most {MAX_MON_MOVES}")
    crit = "TRUE" if s["crit"] else "FALSE"
    hit = (f"MOVE({atk_ref}, {move}, WITH_RNG(RNG_DAMAGE_MODIFIER, i), criticalHit: {crit}, "
           f"secondaryEffect: FALSE);")
    if doubles and not _is_spread_capable(s):
        hit = hit[:-2] + f", target: {def_ref});"
    turns.append("TURN { " + hit + " }")

    ticking = s["attacker"]["status"] in ("burn", "poison", "toxic")
    setup_turns = len(atk_actions)
    scene = []
    solar_setup_ticks = _solar_power_setup_ticks(s, setup_turns)
    hp_capture_events = (setup_turns if ticking else 0) + solar_setup_ticks
    scene += [f"HP_BAR({atk_ref}, captureHP: &results[i].hpAtHit);"] * hp_capture_events
    if s["expect"] == "immune":
        scene.append(f"NONE_OF {{ HP_BAR({def_ref}); }}")
    else:
        scene.append(f"HP_BAR({def_ref}, captureDamage: &results[i].damage);")
    hp0 = s["attacker"]["stats"]["hp"]
    hp_expr = "results[i].hpAtHit" if hp_capture_events else str(hp0)
    def_hp = s["defender"]["stats"]["hp"]
    damage_expr = "results[i].damage" if s["expect"] == "damage" else "0"
    rules = s["rules"]
    # The runner allows one FLAG_SET per test; further badge flags are set directly. Save blocks
    # (and with them every flag) are cleared before each test, and the engine's own badge-boost
    # verdicts are recorded per battler, so a leaked flag could not go unnoticed.
    badges = " ".join((f"FLAG_SET({BADGE_FLAG_C % b});" if n == 0 else f"FlagSet({BADGE_FLAG_C % b});")
                      for n, b in enumerate(s["badges"]))
    ind = " " * 8
    lines = [
        f'{macro}("{TEST_PREFIX}{sid}", s16 damage, u16 hpAtHit)',
        "{",
        "    " + " ".join(["PARAMETRIZE { }"] * ROLL_COUNT),
        f"    gSaveBlock3Ptr->challengeSettings.tx_Mode_Fairy_Types = {1 if rules['fairyTypes'] else 0};",
        f"    gSaveBlock3Ptr->challengeSettings.optionStyle = {1 if rules['optionStyle'] == 'typeBased' else 0};",
        "    GIVEN {",
    ]
    if badges:
        lines.append(ind + badges)
    for line in player_lines + opponent_lines:
        lines.append(ind + line)
    # `gFieldStatuses` is global across test scenarios in the headless runner. Reset it after the
    # battlers are initialized so each vector starts from its declared field and test ordering
    # cannot leak a previous scenario's terrain.
    lines.append(ind + "gFieldStatuses = 0;")
    lines.append(ind + "sDdxoUseHitSpecies = FALSE; sDdxoSetupCalls = 0; sDdxoRoll = i;")
    callback_name = "DdxoSetup_" + sid.replace("-", "_")
    lines.append(ind + f"gDdxoBeforeCriticalHit = {callback_name if s.get('stateSetup') else 'NULL'};")
    lines.append("    } WHEN {")
    for turn in turns:
        lines.append(ind + turn)
    lines.append("    } SCENE {")
    for line in scene:
        lines.append(ind + line)
    lines += [
        "    } THEN {",
        f"{ind}EXPECT_EQ({f'sDdxoSpeciesAtHit[{atk_pos}]' if s.get('stateSetup') else f'gBattleMons[{atk_pos}].species'}, {atk_species});",
        f"{ind}EXPECT_EQ({f'sDdxoSpeciesAtHit[{def_pos}]' if s.get('stateSetup') else f'gBattleMons[{def_pos}].species'}, {def_species});",
        f"{ind}EXPECT_EQ(gBattleMons[{atk_pos}].ability, {s['attacker']['ability']});",
        f"{ind}EXPECT_EQ(gBattleMons[{def_pos}].ability, {s['defender']['ability']});",
        f"{ind}EXPECT_EQ(gBattleMons[{atk_pos}].item, {s['attacker']['item']});",
        f"{ind}EXPECT_EQ(gBattleMons[{def_pos}].item, {s['defender']['item']});",
        f"{ind}EXPECT_EQ(gLastMoves[{atk_pos}], {move});",
        f'{ind}DdxoHit("{sid}", i, {move}, {atk_pos}, {def_pos}, {damage_expr}, '
        f"{def_hp} - gBattleMons[{def_pos}].hp, {hp_expr});",
        "    }",
        "}",
        "",
    ]
    if s.get("stateSetup"):
        lines.insert(lines.index("    } THEN {") + 1, ind + "EXPECT_EQ(sDdxoSetupCalls, 1);")
        setup = [f"static void {callback_name}(void)", "{",
                 f"    if (gCurrentMove != {move} || gBattlerAttacker != (enum BattlerId){atk_pos}",
                 f"        || gBattleResults.battleTurnCounter != {setup_turns}) return;"]
        for role, pos in (("attackerSpeciesForm", atk_pos), ("defenderSpeciesForm", def_pos)):
            form = s["stateSetup"].get(role)
            if form is not None:
                setup.append(f"    gBattleMons[{pos}].species = {form};")
        for role, pos in (("attacker", atk_pos), ("defender", def_pos)):
            b = f"(enum BattlerId){pos}"
            for key, value in s["stateSetup"].get(role, {}).items():
                if key == "personality":
                    expression = f"gBattleMons[{pos}].personality = {value};"
                elif key == "isFirstTurn":
                    expression = f"gBattleStruct->battlerState[{pos}].isFirstTurn = {value};"
                elif key == "supremeOverlordCounter":
                    expression = f"gBattleStruct->supremeOverlordCounter[{pos}] = {value};"
                elif key == "activeGimmick":
                    expression = f"SetActiveGimmick({b}, (enum Gimmick){value});"
                elif key == "dynamaxSelected":
                    expression = (f"gBattleStruct->gimmick.usableGimmick[{pos}] = GIMMICK_DYNAMAX; "
                                  f"gBattleStruct->gimmick.toActivate = {value} << {pos}; "
                                  f"gBattleStruct->gimmick.playerSelect = {value};")
                else:
                    expression = f"gBattleMons[{pos}].volatiles.{key} = {value};"
                setup.append("    " + expression)
        if "laterAction" in s["stateSetup"]:
            setup.append(f"    gActionsByTurnOrder[1] = {s['stateSetup']['laterAction']};")
        setup += [f"    sDdxoBasePowerAtHit = GetActiveGimmick((enum BattlerId){atk_pos}) == GIMMICK_DYNAMAX ? GetMaxMovePower({move}) : GetMovePower({move});"]
        setup += [f'    DdxoRuntime("{sid}", "A", (enum BattlerId){atk_pos});',
                  f'    DdxoRuntime("{sid}", "D", (enum BattlerId){def_pos});']
        setup += ["    for (u32 b = 0; b < gBattlersCount; b++)",
                  "        sDdxoSpeciesAtHit[b] = gBattleMons[b].species;",
                  "    sDdxoUseHitSpecies = TRUE;", "    sDdxoSetupCalls++;",
                  "    gDdxoBeforeCriticalHit = NULL;", "}", ""]
        lines = setup + lines
    return "\n".join(lines)


def _is_spread_capable(s: dict) -> bool:
    # A Doubles hit is aimed explicitly at the defender unless the move is a pinned spread move;
    # the spread-ness itself is observed (M line target / target count), never assumed here.
    return s["move"]["label"] in {"Rock Slide", "Heat Wave", "Hyper Voice", "Dazzling Gleam", "Razor Leaf",
                                  "Earthquake", "Surf"}


def render_sources(scenarios: list[dict]) -> dict[str, str]:
    """Generated C sources, keyed by path relative to the pinned tree root. Deterministic."""
    type_table = "\n".join(f'    [{C_TYPE_TABLE[n]}] = "{n}",' for n in TYPE_NAMES)
    prelude = C_PRELUDE % {"type_table": type_table}
    files = {}
    for index in range(0, len(scenarios), SCENARIOS_PER_FILE):
        chunk = scenarios[index:index + SCENARIOS_PER_FILE]
        name = f"{TEST_SUBDIR}/oracle_{index // SCENARIOS_PER_FILE:03d}.c"
        files[name] = prelude + "\n" + "\n".join(render_scenario(s) for s in chunk)
    return files


def sources_sha256(files: dict[str, str]) -> str:
    digest = hashlib.sha256()
    for name in sorted(files):
        digest.update(name.encode() + b"\0" + files[name].encode() + b"\0")
    return digest.hexdigest()


# --------------------------------------------------------------------------------------------
# Output parsing (fail closed)
# --------------------------------------------------------------------------------------------

ANSI_RE = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
RESULT_RE = re.compile(r"^\[\d+\] (?P<name>.+?)(?: \d+/\d+)?: (?P<result>[A-Z_]+)\s*$")
INT_RE = re.compile(r"^-?\d+$")


def _int(text: str, what: str) -> int:
    if not INT_RE.match(text):
        raise OracleError(f"malformed integer {text!r} in {what}")
    return int(text)


LINE_FIELDS = {"A1": 9, "A2": 7, "A3": 4, "A4": 6, "A5": 4, "A6": 1,
               "D1": 9, "D2": 7, "D3": 4, "D4": 6, "D5": 4, "D6": 1,
               "M": 19, "F": 7, "R": 3}


def parse_runner_output(text: str, scenario_ids: list[str]) -> dict[str, dict[int, dict[str, list[str]]]]:
    """Parse hydra output into {scenario id: {rng value: {line kind: fields}}}.

    Fails when any oracle test did not PASS, when a scenario or roll is missing, duplicated or
    unknown, or when any line is malformed. Nothing is defaulted.
    """
    text = ANSI_RE.sub("", text)
    wanted = set(scenario_ids)
    records: dict[str, dict[int, dict[str, list[str]]]] = {sid: {} for sid in scenario_ids}
    statuses: dict[str, list[str]] = {}
    for raw in text.splitlines():
        line = raw.strip()
        m = RESULT_RE.match(line)
        if m and m.group("name").startswith(TEST_PREFIX):
            sid = m.group("name")[len(TEST_PREFIX):]
            statuses.setdefault(sid, []).append(m.group("result"))
            continue
        if not line.startswith("DDXO|"):
            continue
        parts = line.split("|")
        if len(parts) < 4:
            raise OracleError(f"malformed oracle line {line!r}")
        _, sid, roll_text, kind, *fields = parts
        if sid not in wanted:
            raise OracleError(f"oracle line for unknown scenario {sid!r}")
        roll = _int(roll_text, f"{sid} roll index")
        if not 0 <= roll < ROLL_COUNT:
            raise OracleError(f"{sid}: roll index {roll} out of range")
        if kind not in LINE_FIELDS and kind not in ("AG", "DG"):
            raise OracleError(f"{sid}: unknown oracle line kind {kind!r}")
        expected_length = len(RUNTIME_DOMAINS) if kind in ("AG", "DG") else LINE_FIELDS[kind]
        if len(fields) != expected_length:
            raise OracleError(f"{sid}: {kind} line has {len(fields)} fields, expected {expected_length}")
        slot = records[sid].setdefault(roll, {})
        if kind in slot:
            raise OracleError(f"{sid}: duplicate {kind} line for roll {roll}")
        slot[kind] = fields
    for sid in scenario_ids:
        results = statuses.get(sid)
        if not results:
            raise OracleError(f"{sid}: the pinned test runner reported no result")
        if any(r != "PASS" for r in results):
            raise OracleError(f"{sid}: pinned test runner result {results} (expected PASS)")
        rolls = records[sid]
        if sorted(rolls) != list(range(ROLL_COUNT)):
            missing = sorted(set(range(ROLL_COUNT)) - set(rolls))
            raise OracleError(f"{sid}: missing oracle output for rng value(s) {missing}")
        for roll, slot in rolls.items():
            if set(slot) - {"AG", "DG"} != set(LINE_FIELDS):
                raise OracleError(f"{sid}: roll {roll} is missing line(s) {sorted(set(LINE_FIELDS) - set(slot))}")
    unexpected = sorted(set(statuses) - wanted)
    if unexpected:
        raise OracleError(f"runner reported unexpected oracle tests {unexpected[:5]}")
    return records


def _battler_view(slot: dict[str, list[str]], role: str, sid: str) -> dict:
    f1, f2, f3, f4, f5, f6 = (slot[f"{role}{n}"] for n in range(1, 7))
    what = f"{sid} {role}"
    types = list(f1[2:5])
    if types[2] != "Mystery":
        raise OracleError(f"{what}: unsupported third battle type {types[2]!r}")
    for t in types[:2]:
        if t not in TYPE_NAMES:
            raise OracleError(f"{what}: unsupported battle type {t!r}")
    battle_types = [types[0]] if types[0] == types[1] else [types[0], types[1]]
    return {
        "speciesId": _int(f1[0], what), "level": _int(f1[1], what), "types": battle_types,
        "abilityId": _int(f1[5], what), "itemId": _int(f1[6], what), "status": f1[7],
        "status1": _int(f1[8], what),
        "hp": _int(f2[0], what), "maxHp": _int(f2[1], what), "attack": _int(f2[2], what),
        "defense": _int(f2[3], what), "spAttack": _int(f2[4], what), "spDefense": _int(f2[5], what),
        "speed": _int(f2[6], what),
        "stages": {"attack": _int(f3[0], what), "defense": _int(f3[1], what), "spAttack": _int(f3[2], what),
                   "spDefense": _int(f3[3], what)},
        "baseStats": {"hp": _int(f4[0], what), "attack": _int(f4[1], what), "defense": _int(f4[2], what),
                      "spAttack": _int(f4[3], what), "spDefense": _int(f4[4], what), "speed": _int(f4[5], what)},
        "badgeBoosts": {k: _int(v, what) == 1 for k, v in zip(("attack", "defense", "spAttack", "spDefense"), f5)},
        "terrainAffected": _int(f6[0], what) == 1,
    }


def _check_battler(sid: str, role: str, scen: dict, seen: dict,
                   post_hit_stage_deltas: dict[str, int] | None = None) -> None:
    what = f"{sid} {'attacker' if role == 'A' else 'defender'}"
    st = scen["stats"]
    if seen["level"] != scen["level"]:
        raise OracleError(f"{what}: battle level {seen['level']} != scenario {scen['level']}")
    for key in ("maxHp", "attack", "defense", "spAttack", "spDefense", "speed"):
        if seen[key] != st[key]:
            raise OracleError(f"{what}: battle {key} {seen[key]} != scenario {st[key]}")
    if seen["status"] != scen["status"]:
        raise OracleError(f"{what}: battle status {seen['status']!r} != scenario {scen['status']!r}")
    if scen["status"] == "toxic":
        # The test runner's setup status is STATUS1_TOXIC_POISON. Before the selected hit,
        # H&S advances the toxic counter once; only its 0x80 poison bit and 0x0f00 counter bits
        # are expected at this observation point.
        toxic_word = seen["status1"]
        if toxic_word & 0x80 == 0 or toxic_word & ~(0x80 | 0x0f00) != 0:
            raise OracleError(f"{what}: invalid pinned toxic status word {toxic_word}")
    else:
        expected_status1 = {"none": 0, "poison": 8, "burn": 16, "paralysis": 64}[scen["status"]]
        if seen["status1"] != expected_status1:
            raise OracleError(f"{what}: raw status1 {seen['status1']} != expected pinned status1 {expected_status1}")
    post_hit_stage_deltas = post_hit_stage_deltas or {}
    for stat, value in scen["stages"].items():
        # The runner reports battler stages after the move's hit hooks. These exact H&S
        # abilities raise a stage after absorbing a matching move; damage still used the
        # scenario's pre-hit stage, which is what the corpus input records.
        expected = max(-6, min(6, value + post_hit_stage_deltas.get(stat, 0)))
        if seen["stages"][stat] != expected:
            raise OracleError(
                f"{what}: battle {stat} stage {seen['stages'][stat]} != expected post-hit stage {expected}"
            )


def _defender_post_hit_stage_deltas(scenario: dict, move_fields: list[str]) -> dict[str, int]:
    """Return only source-verified, matching move-hook boosts reported after the hit."""
    ability = scenario["defender"]["abilityLabel"]
    move_type = move_fields[1]
    flags = {name for name, present in zip(
        ("soundMove", "ballisticMove", "windMove", "healingMove", "ignoresTargetAbility"), move_fields[7:12]
    ) if _int(present, f"{scenario['id']} M flags") == 1}
    if ability == "Lightning Rod" and move_type == "Electric":
        return {"spAttack": 1}
    if ability == "Storm Drain" and move_type == "Water":
        return {"spAttack": 1}
    if ability == "Sap Sipper" and move_type == "Grass":
        return {"attack": 1}
    if ability == "Well-Baked Body" and move_type == "Fire":
        return {"defense": 2}
    if ability == "Wind Rider" and "windMove" in flags:
        return {"attack": 1}
    return {}


def assemble_entry(scenario: dict, per_roll: dict[int, dict[str, list[str]]]) -> dict:
    """Turn sixteen verified runs into one corpus entry, or raise OracleError."""
    sid = scenario["id"]
    rolls = [None] * ROLL_COUNT
    canonical = None
    for rng in range(ROLL_COUNT):
        slot = per_roll[rng]
        atk = _battler_view(slot, "A", sid)
        dfn = _battler_view(slot, "D", sid)
        m = slot["M"]
        f = slot["F"]
        r = slot["R"]
        damage, delta, hp_at_hit = (_int(x, f"{sid} R") for x in r)
        if scenario["expect"] == "damage" and damage != delta:
            raise OracleError(f"{sid} rng {rng}: HP-bar damage {damage} != defender HP delta {delta}")
        if scenario["expect"] == "immune" and (damage != 0 or delta != 0):
            raise OracleError(f"{sid} rng {rng}: immune hit changed defender HP by {delta}")
        if scenario["expect"] == "damage" and damage <= 0:
            raise OracleError(f"{sid} rng {rng}: damaging hit measured {damage}")
        if damage >= MAX_MEASURABLE_DAMAGE:
            raise OracleError(f"{sid} rng {rng}: damage {damage} saturates the measurable range")
        _check_battler(sid, "A", scenario["attacker"], atk)
        _check_battler(sid, "D", scenario["defender"], dfn,
                       _defender_post_hit_stage_deltas(scenario, m))
        if scenario["attacker"]["status"] == "none":
            # Solar Power's end-of-turn passive damage is outside the selected-hit damage
            # contract. hpAtHit is captured after the setup turns; permit only the exact single
            # post-hit 1/8 max HP loss, which cannot affect the measured damage value.
            solar_residual = (scenario["attacker"]["stats"]["maxHp"] // 8
                              if _solar_power_weather_affected(scenario) else 0)
            expected_hp = hp_at_hit - solar_residual
            if atk["hp"] != expected_hp:
                raise OracleError(f"{sid}: attacker HP changed without a status or exact Solar Power residual ({atk['hp']}, expected {expected_hp})")
        if hp_at_hit <= 0 or hp_at_hit > scenario["attacker"]["stats"]["hp"]:
            raise OracleError(f"{sid}: implausible attacker HP at the hit {hp_at_hit}")
        move_id = _int(m[0], f"{sid} M")
        source_move = MOVE_DAMAGE_METADATA.get(str(move_id))
        if source_move is None:
            raise OracleError(f"{sid}: oracle matrix selected move without generated source metadata {move_id}")
        if _int(m[6], f"{sid} M") != move_id:
            raise OracleError(f"{sid}: attacker's last move {m[6]} is not the measured move {move_id}")
        if m[1] not in TYPE_NAMES:
            raise OracleError(f"{sid}: unsupported move type {m[1]!r}")
        if m[3] not in ("physical", "special"):
            raise OracleError(f"{sid}: measured move has category {m[3]!r}")
        flags = sorted(
            name for name, present in zip(
                ("soundMove", "ballisticMove", "windMove", "healingMove", "ignoresTargetAbility"), m[7:12]
        ) if _int(present, f"{sid} M flags") == 1
        )
        ability_flags = sorted(
            name for name, present in zip(
                ("punchingMove", "bitingMove", "pulseMove", "slicingMove"), m[14:18]
            ) if _int(present, f"{sid} M ability flags") == 1
        )
        weather, reflect, light_screen, is_doubles, fairy, style, field_statuses = f
        field = scenario["field"]
        if weather != field["weather"]:
            raise OracleError(f"{sid}: battle weather {weather!r} != scenario {field['weather']!r}")
        observed_field_statuses = _int(field_statuses, f"{sid} F fieldStatuses")
        expected_field_statuses = {"none": 0, "grassy": 1 << 6, "electric": 1 << 8,
                                   "misty": 1 << 7, "psychic": 1 << 9}[field["terrain"]]
        if field["gravity"]:
            expected_field_statuses |= 1 << 5
        if (scenario.get("stateSetup") or {}).get("wonderRoom"):
            expected_field_statuses |= 4
        if observed_field_statuses != expected_field_statuses:
            raise OracleError(f"{sid}: battle field word {observed_field_statuses:#x} != scenario {expected_field_statuses:#x}")
        if (reflect == "1") != field["reflect"] or (light_screen == "1") != field["lightScreen"]:
            raise OracleError(f"{sid}: defender screens reflect={reflect} lightScreen={light_screen} disagree")
        if (is_doubles == "1") != (scenario["format"] == "doubles"):
            raise OracleError(f"{sid}: battle format disagrees with the scenario")
        if (fairy == "1") != scenario["rules"]["fairyTypes"]:
            raise OracleError(f"{sid}: fairy rule did not reach the battle")
        if (style == "1") != (scenario["rules"]["optionStyle"] == "typeBased"):
            raise OracleError(f"{sid}: option style did not reach the battle")
        runtime = {}
        for role, prefix in (("attacker", "A"), ("defender", "D")):
            values = slot.get(prefix + "G")
            if bool(scenario.get("stateSetup")) != (values is not None):
                raise OracleError(f"{sid}: missing/unexpected pre-damage runtime capture for {role}")
            runtime[role] = (dict(zip(RUNTIME_DOMAINS, (_int(v, f"{sid} runtime") for v in values)))
                             if values is not None else None)
            if runtime[role] is not None:
                runtime[role]["personality"] &= 0xffffffff
            for key, value in (scenario.get("stateSetup") or {}).get(role, {}).items():
                if runtime[role][key] != value:
                    raise OracleError(f"{sid}: runtime {role}.{key} disagrees with state setup")
        observed = {
            "attacker": {"speciesId": atk["speciesId"], "types": atk["types"], "baseStats": atk["baseStats"],
                         "abilityId": atk["abilityId"], "itemId": atk["itemId"], "hpAtHit": hp_at_hit,
                         "status1": atk["status1"],
                         "badgeBoosts": atk["badgeBoosts"], "terrainAffected": atk["terrainAffected"], "runtime": runtime["attacker"]},
            "defender": {"speciesId": dfn["speciesId"], "types": dfn["types"], "baseStats": dfn["baseStats"],
                         "abilityId": dfn["abilityId"], "itemId": dfn["itemId"],
                         "hpAtHit": scenario["defender"]["stats"]["hp"], "status1": dfn["status1"],
                         "badgeBoosts": dfn["badgeBoosts"], "terrainAffected": dfn["terrainAffected"], "runtime": runtime["defender"]},
            "move": {"id": move_id, "type": m[1], "power": _int(m[2], f"{sid} M"), "category": m[3],
                     "target": m[4], "flags": flags, "abilityFlags": ability_flags,
                     "effect": source_move["effect"], "ordinary": source_move["ordinary"],
                     "makesContact": source_move["makesContact"],
                     "punchingMove": source_move["punchingMove"],
                     "sheerForceAffected": source_move["sheerForceAffected"],
                     "priority": _int(m[12], f"{sid} M priority"),
                     "targetClass": _int(m[13], f"{sid} M target class"),
                     "ateBoost": _int(m[18], f"{sid} M ateBoost") == 1},
            "targetCount": _int(m[5], f"{sid} M"),
            "fieldStatuses": observed_field_statuses,
        }
        if canonical is None:
            canonical = observed
        elif observed != canonical:
            raise OracleError(f"{sid}: battle operands differ between rolls (state leaked between runs)")
        # rng value v multiplies by (100 - v)%, so rolls[k] (factor 85+k) is rng value 15-k.
        rolls[ROLL_COUNT - 1 - rng] = damage
    return {"scenario": scenario, "observed": canonical, "rolls": rolls}


# --------------------------------------------------------------------------------------------
# Pinned tree export, build and run
# --------------------------------------------------------------------------------------------

def _run(cmd: list[str], cwd: Path | None = None, env: dict | None = None, capture: bool = True) -> str:
    proc = subprocess.run(cmd, cwd=cwd, env=env, text=True, stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.STDOUT if capture else None)
    if proc.returncode != 0:
        tail = (proc.stdout or "")[-4000:]
        raise OracleError(f"command failed ({proc.returncode}): {' '.join(cmd)}\n{tail}")
    return proc.stdout or ""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_upstream(upstream: Path) -> None:
    """The upstream checkout must be the pinned commit with a clean tracked tree."""
    if not (upstream / ".git").exists():
        raise OracleError(f"{upstream} is not a git checkout of the pinned H&S source")
    head = _run(["git", "-C", str(upstream), "rev-parse", "HEAD"]).strip()
    if head != HNS_PINNED_COMMIT:
        raise OracleError(f"upstream HEAD {head} is not the pinned commit {HNS_PINNED_COMMIT}")
    tree = _run(["git", "-C", str(upstream), "rev-parse", f"{HNS_PINNED_COMMIT}^{{tree}}"]).strip()
    if tree != HNS_PINNED_TREE:
        raise OracleError(f"pinned commit tree {tree} != expected {HNS_PINNED_TREE}")
    dirty = _run(["git", "-C", str(upstream), "status", "--porcelain", "--untracked-files=no"]).strip()
    if dirty:
        raise OracleError("upstream checkout has modified tracked files; refusing to use it")


def _apply_patch(work: Path, patch: Path) -> None:
    text = patch.read_text()
    targets = re.findall(r"^\+\+\+ b/(\S+)", text, re.M)
    if not targets or any(not t.startswith("test/") for t in targets):
        raise OracleError(f"{patch.name} must only touch the upstream test harness, touches {targets}")
    _run(["patch", "-p1", "--forward", "--batch", "-i", str(patch)], cwd=work)


def export_worktree(upstream: Path, work: Path) -> None:
    """Cleanly export the pinned commit into `work`, patch the harness, and prune tests.

    Every corpus-producing run starts from `git archive` at the pinned commit. A scratch-tree marker
    cannot establish source provenance because files below `src/` could have changed after it was
    written.
    """
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    archive = subprocess.run(["git", "-C", str(upstream), "archive", "--format=tar", HNS_PINNED_COMMIT],
                             stdout=subprocess.PIPE, check=False)
    if archive.returncode != 0:
        raise OracleError("git archive of the pinned commit failed")
    with tarfile.open(fileobj=io.BytesIO(archive.stdout)) as tar:
        tar.extractall(work, filter="data")
    for patch in HARNESS_PATCHES:
        _apply_patch(work, PATCH_DIR / patch)
    for path in sorted(work.joinpath("test").rglob("*")):
        rel = path.relative_to(work).as_posix()
        if path.is_file() and rel not in KEEP_TEST_FILES:
            path.unlink()
    # The pinned Makefile refuses to build outside a git clone unless history checks are waived.
    (work / ".histignore").write_text("")


def toolchain_identity(toolchain_bin: Path) -> dict:
    gcc = toolchain_bin / "arm-none-eabi-gcc"
    if not gcc.exists():
        raise OracleError(f"arm-none-eabi-gcc not found in {toolchain_bin}")
    version = _run([str(gcc), "--version"]).splitlines()[0].strip()
    cc1 = Path(_run([str(gcc), "-print-prog-name=cc1"]).strip())
    if not cc1.is_file():
        raise OracleError("cannot locate the toolchain's cc1")
    return {"gcc": version, "gccSha256": sha256_file(gcc), "cc1Sha256": sha256_file(cc1)}


def build_and_run(work: Path, toolchain_bin: Path, sources: dict[str, str], jobs: int) -> str:
    for name, text in sources.items():
        target = work / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    env = dict(os.environ)
    env["PATH"] = f"{toolchain_bin}{os.pathsep}{env.get('PATH', '')}"
    env.pop("DEVKITARM", None)
    _run(["make", f"-j{jobs}", "BUILD=hns", "TEST=1", "pokehns-test.elf"], cwd=work, env=env)
    elf = work / "pokehns-test.elf"
    headless = work / "pokehns-test-headless.elf"
    shutil.copyfile(elf, headless)
    patchelf = str(work / "tools/patchelf/patchelf")
    _run([patchelf, str(headless), "gTestRunnerHeadless", "\\x01", "gTestRunnerSkipIsFail", "\\x01"], env=env)
    _run([patchelf, str(headless), "gTestRunnerArgv", TEST_PREFIX + "\\0"], env=env)
    hydra = str(work / "tools/mgba-rom-test-hydra/mgba-rom-test-hydra")
    mgba = str(work / "tools/mgba/mgba-rom-test")
    # Hydra takes its parallelism from MAKEFLAGS, exactly as under `make check -jN`.
    env["MAKEFLAGS"] = f"-j{jobs}"
    proc = subprocess.run([hydra, mgba, "arm-none-eabi-objcopy", str(headless)], cwd=work, env=env, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if proc.returncode != 0:
        tail = "\n".join(proc.stdout.splitlines()[-40:])
        detail = f"\n{tail}" if tail else ""
        raise OracleError(f"hydra runner exited with status {proc.returncode}; refusing its output{detail}")
    return proc.stdout


def backend_provenance(work: Path) -> dict:
    return {
        "kind": "pinned-expansion-battle-test-runner",
        "build": BUILD_COMMAND,
        "runner": RUNNER_DESCRIPTION,
        "mgbaRomTestSha256": sha256_file(work / "tools/mgba/mgba-rom-test"),
        "harnessPatches": [{"path": f"patches/{p}", "sha256": sha256_file(PATCH_DIR / p)} for p in HARNESS_PATCHES],
        "prunedUpstreamTests": True,
    }
