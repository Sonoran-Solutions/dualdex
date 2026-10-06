#!/usr/bin/env python3
"""Additive pinned-engine traces for Slice 13 variable multi-hit moves."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

import oracle_backend as backend

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
COUNT_PATCH = HERE / "patches/0011-variable-multihit-count-observation.patch"
FIXED_PATCH = HERE / "patches/0010-repeated-strike-observation.patch"
TARGET = HERE / "variable-multihit-evidence.json"
HISTORICAL_SHA = "9f38413dd64ce0f73162f84db1f7f3cd7d84a6bf74bb38d09fb5b336947e1580"
SLICE12_SHA = "4b5fa96b4ecfd77b45cfa6c3e196c13fca22b4fa0c87e7cba9c62597b89d3b8a"
MOVES = [
    ("ARM_THRUST", 292), ("BONE_RUSH", 198), ("BULLET_SEED", 331),
    ("COMET_PUNCH", 4), ("DOUBLE_SLAP", 3), ("FURY_ATTACK", 31),
    ("FURY_SWIPES", 154), ("ICICLE_SPEAR", 333), ("PIN_MISSILE", 42),
    ("ROCK_BLAST", 350), ("SPIKE_CANNON", 131), ("TAIL_SLAP", 541),
]
RNG_ACCURACY_TAG = 1
RNG_CRITICAL_TAG = 4
RNG_DAMAGE_TAG = 7
RNG_HITS_TAG = 18
RNG_LOADED_DICE_TAG = 21


def scenarios():
    rows = []

    def add(name, move="BULLET_SEED", count=2, ability="INSOMNIA", defender="INSOMNIA",
            item="NONE", defender_item="NONE", hp=60000, rolls=None, crits=None,
            setup="NONE", mode="ORDINARY", miss=False, no_damage=False):
        rolls = list(rolls or [0] * count)
        crits = list(crits or [0] * count)
        assert len(rolls) == len(crits) == count
        rows.append(dict(id=name, move=move, nominalCount=count, attackerAbility=ability,
                         defenderAbility=defender, attackerItem=item, defenderItem=defender_item,
                         targetHp=hp, rolls=rolls, crits=crits, setup=setup, mode=mode,
                         initialMiss=miss, sourceImmune=no_damage))

    # All target moves at each ordinary count; directed vectors cover low/high endpoints,
    # alternating and non-monotonic rolls, all-crit and mixed-crit sequences.
    patterns = {
        "low": (lambda n: [0] * n, lambda n: [0] * n),
        "high": (lambda n: [15] * n, lambda n: [1] * n),
        "low-high": (lambda n: [0 if i % 2 == 0 else 15 for i in range(n)], lambda n: [0] * n),
        "high-low": (lambda n: [15 if i % 2 == 0 else 0 for i in range(n)], lambda n: [1 if i % 2 == 0 else 0 for i in range(n)]),
        "mixed": (lambda n: ([0, 15, 4, 11, 7])[:n], lambda n: ([0, 1, 1, 0, 1])[:n]),
    }
    for move, _move_id in MOVES:
        for count in range(2, 6):
            for label, (rolls, crits) in patterns.items():
                add(f"{move.lower()}-{count}-{label}", move, count, rolls=rolls(count), crits=crits(count))

    # Count selection precedence and request-local suppression source scenarios.
    add("skill-link", ability="SKILL_LINK", count=5, mode="SKILL_LINK")
    add("skill-link-loaded-dice", ability="SKILL_LINK", item="LOADED_DICE", count=5, mode="SKILL_LINK")
    add("loaded-dice-four", item="LOADED_DICE", count=4, mode="LOADED_DICE")
    add("loaded-dice-five", item="LOADED_DICE", count=5, mode="LOADED_DICE")
    add("loaded-dice-magic-room", item="LOADED_DICE", count=3, setup="MAGIC_ROOM")
    add("loaded-dice-embargo", item="LOADED_DICE", count=3, setup="EMBARGO")
    add("loaded-dice-klutz", item="LOADED_DICE", ability="KLUTZ", count=3)
    add("skill-link-gastro-acid", ability="SKILL_LINK", count=3, setup="GASTRO_ACID")
    add("skill-link-neutralizing-gas", ability="SKILL_LINK", defender="NEUTRALIZING_GAS", count=3)
    add("skill-link-gas-ability-shield", ability="SKILL_LINK", defender="NEUTRALIZING_GAS",
        item="ABILITY_SHIELD", count=5, mode="SKILL_LINK")
    add("skill-link-gas-loaded-dice", ability="SKILL_LINK", defender="NEUTRALIZING_GAS",
        item="LOADED_DICE", count=4, mode="LOADED_DICE")

    # Freeze per-strike Technician and contact/non-contact behavior across every exact move
    # descriptor and every possible count. All twelve selected source moves are Physical.
    for move, _move_id in MOVES:
        for count in range(2, 6):
            add(f"technician-{move.lower()}-{count}", move, count, ability="TECHNICIAN")
            add(f"tough-claws-{move.lower()}-{count}", move, count, ability="TOUGH_CLAWS")
            add(f"fluffy-{move.lower()}-{count}", move, count, defender="FLUFFY")
    for count in range(2, 6):
        add(f"life-orb-{count}", "BULLET_SEED", count, item="LIFE_ORB")
    add("long-reach-contact-authority", "COMET_PUNCH", 3, ability="LONG_REACH")
    add("protective-pads-contact-authority", "COMET_PUNCH", 3, item="PROTECTIVE_PADS")
    add("punching-glove-comet-punch", "COMET_PUNCH", 3, item="PUNCHING_GLOVE")
    add("rounding-technician-punching-life-orb", "COMET_PUNCH", 3,
        ability="TECHNICIAN", item="LIFE_ORB")
    add("bullet-seed-bulletproof", defender="BULLETPROOF", no_damage=True)

    # First-strike damage roll is independent of count: cross all 16 source rolls with N=2..5.
    for count in range(2, 6):
        for roll in range(16):
            add(f"first-roll-{count}-{roll}", "BULLET_SEED", count, rolls=[roll] * count,
                crits=[0] * count)

    # Target HP just below/at/above the minimum and maximum endpoints for 1..N strikes.
    # Measured one-strike endpoints from first-roll-2-0 and first-roll-2-15 in the pinned engine.
    endpoint = {"low": 14, "high": 17}
    for count in range(2, 6):
        for prefix_hits in range(1, count + 1):
            for edge, delta in (("below", -1), ("at", 0), ("above", 1)):
                for side in ("low", "high"):
                    hp = max(1, min(60000, endpoint[side] * prefix_hits + delta))
                    add(f"early-{count}-{prefix_hits}-{side}-{edge}", "BULLET_SEED", count,
                        hp=hp, rolls=([15] if side == "low" else [0]) * count)
    # Accuracy control: this target has sub-100 accuracy; first miss must do no damage.
    add("initial-miss", "TAIL_SLAP", 3, miss=True)
    return rows


PRELUDE = r'''#include "global.h"
#include "battle.h"
#include "battle_util.h"
#include "move.h"
#include "random.h"
#include "test/battle.h"
#include "constants/battle_move_effects.h"
extern bool32 (*gDdxrRng)(u32,u32,u32,u32 *);
void (*gDdxrCountSelected)(struct BattleContext *);
extern void (*gDdxrBefore)(struct BattleContext *);
extern void (*gDdxrOperands)(struct BattleContext *,u32,u32);
extern void (*gDdxrCalculated)(struct BattleContext *,s32,s32);
extern void (*gDdxrHp)(enum BattlerId);
extern void (*gDdxrBetween)(void);
static u32 sCase,sMove,sExpectedCount,sMode,sHits,sDamageDraws,sCritDraws,sAccuracyDraws,sMiss,sNoDamage;
static u32 sRolls[5],sCrits[5],sBeforeHp[5],sApplied[5];
static bool32 Selected(void) { return gBattlerAttacker==0 && gCurrentMove==sMove; }
static void CountSelected(struct BattleContext *ctx) {
    u32 ability=GetBattlerAbility(0), hold=GetBattlerHoldEffect(0), mode=0;
    if (ability==ABILITY_SKILL_LINK) mode=2;
    else if (hold==HOLD_EFFECT_LOADED_DICE) mode=1;
    EXPECT_EQ(gMultiHitCounter,sExpectedCount); EXPECT_EQ(mode,sMode);
    DebugPrintf("DDXV|%u|COUNT|%u|%u|%u|%u|%u\n",sCase,mode,ability,hold,gMultiHitCounter,ctx->abilityAtk);
}
static bool32 Rng(u32 tag,u32 lo,u32 hi,u32 *value) {
    if (!Selected()) return FALSE;
    if (tag==RNG_HITS) *value=sExpectedCount;
    else if (tag==RNG_LOADED_DICE) *value=sExpectedCount;
    else if (tag==RNG_DAMAGE_MODIFIER) { EXPECT_LT(sDamageDraws,5); *value=sRolls[sDamageDraws++]; }
    else if (tag==RNG_CRITICAL_HIT) { EXPECT_LT(sCritDraws,5); *value=sCrits[sCritDraws++]; }
    else if (tag==RNG_ACCURACY) { EXPECT_EQ(sAccuracyDraws++,0); *value=sMiss ? 0 : 1; }
    else return FALSE;
    EXPECT_GE(*value,lo); EXPECT_LE(*value,hi);
    DebugPrintf("DDXV|%u|RNG|%u|%u|%u|%u\n",sCase,tag,*value,lo,hi);
    return TRUE;
}
static void Before(struct BattleContext *ctx) {
    if (!Selected()) return;
    EXPECT_LT(sHits,5); sBeforeHp[sHits]=gBattleMons[1].hp; sHits++;
    DebugPrintf("DDXV|%u|PRE|%u|%u|%u|%u|%lu|%lu|%u|%u|%u|%u|%u|%lu\n",sCase,sHits,gMultiHitCounter,
        gBattleMons[0].hp,gBattleMons[1].hp,gBattleMons[0].status1,gBattleMons[1].status1,
        ctx->abilityAtk,ctx->abilityDef,ctx->holdEffectAtk,ctx->holdEffectDef,gBattleWeather,gFieldStatuses);
}
static void Operands(struct BattleContext *ctx,u32 attack,u32 defense) {
    if (!Selected()) return;
    DebugPrintf("DDXV|%u|OPERANDS|%u|%u|%u|%u|%u|%u\n",sCase,sHits,attack,defense,
        gBattleMovePower,ctx->moveType,GetBattleMoveCategory(ctx->move));
}
static void Calculated(struct BattleContext *ctx,s32 raw,s32 adjusted) {
    if (Selected()) DebugPrintf("DDXV|%u|CALC|%u|%ld|%ld|%u\n",sCase,sHits,raw,adjusted,ctx->isCrit);
}
static void Hp(enum BattlerId battler) {
    if (!Selected() || battler!=1) return;
    sApplied[sHits-1]=sBeforeHp[sHits-1]-gBattleMons[1].hp;
    DebugPrintf("DDXV|%u|HP|%u|%u|%u|%u\n",sCase,sHits,sBeforeHp[sHits-1],gBattleMons[1].hp,sApplied[sHits-1]);
}
static void Between(void) {
    if (!Selected()) return;
    u32 stop=0;
    if (sHits==0) stop=1;
    else if (gBattleMons[1].hp==0) stop=2;
    else if (gMultiHitCounter==1) stop=3;
    else if (gBattleMons[0].hp==0) stop=4;
    else if (gBattleMons[0].status1 & STATUS1_SLEEP) stop=5;
    else if (gBattleMons[0].status1 & STATUS1_FREEZE) stop=6;
    if (stop) DebugPrintf("DDXV|%u|STOP|%u|%u\n",sCase,sHits,stop);
    DebugPrintf("DDXV|%u|BETWEEN|%u|%u|%u|%u|%lu|%lu|%u|%u|%u|%u|%u|%lu|%u|%u\n",sCase,sHits,gMultiHitCounter,
        gBattleMons[0].hp,gBattleMons[1].hp,gBattleMons[0].status1,gBattleMons[1].status1,
        GetBattlerAbility(0),GetBattlerAbility(1),GetBattlerHoldEffect(0),GetBattlerHoldEffect(1),
        gBattleWeather,gFieldStatuses,gBattleMons[1].statStages[STAT_DEF],gBattleMons[0].statStages[STAT_ATK]);
}
'''


def source(rows):
    params = []
    for i, s in enumerate(rows):
        r = (s["rolls"] + [0] * 5)[:5]
        c = (s["crits"] + [0] * 5)[:5]
        params.append("    PARAMETRIZE { " +
            f'caseId={i}; move=MOVE_{s["move"]}; a=ABILITY_{s["attackerAbility"]}; d=ABILITY_{s["defenderAbility"]}; ' +
            f'item=ITEM_{s["attackerItem"]}; di=ITEM_{s["defenderItem"]}; hp={s["targetHp"]}; count={s["nominalCount"]}; ' +
            f'mode={ {"ORDINARY":0,"LOADED_DICE":1,"SKILL_LINK":2}[s["mode"]] }; setup={ {"NONE":0,"MAGIC_ROOM":1,"EMBARGO":2,"GASTRO_ACID":3}[s["setup"]] }; miss={int(s["initialMiss"])}; noDamage={int(s["sourceImmune"])}; ' +
            " ".join(f"r{j}={r[j]}; c{j}={c[j]};" for j in range(5)) + " }" )
    tail = r'''
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(a); Item(item); Attack(151); SpAttack(151); HP(200); MaxHP(200); Level(50); Speed(200); }
        OPPONENT(SPECIES_SNORLAX) { Ability(d); Item(di); Defense(109); SpDefense(109); HP(hp); MaxHP(hp); Level(50); Speed(100); }
        sCase=caseId; sMove=move; sExpectedCount=count; sMode=mode; sMiss=miss; sNoDamage=noDamage;
        sHits=sDamageDraws=sCritDraws=sAccuracyDraws=0;
        memset(sBeforeHp,0,sizeof(sBeforeHp)); memset(sApplied,0,sizeof(sApplied));
        sRolls[0]=r0; sRolls[1]=r1; sRolls[2]=r2; sRolls[3]=r3; sRolls[4]=r4;
        sCrits[0]=c0; sCrits[1]=c1; sCrits[2]=c2; sCrits[3]=c3; sCrits[4]=c4;
        gDdxrRng=Rng; gDdxrCountSelected=CountSelected; gDdxrBefore=Before; gDdxrOperands=Operands;
        gDdxrCalculated=Calculated; gDdxrHp=Hp; gDdxrBetween=Between;
    } WHEN {
        if (setup==1) TURN { MOVE(player,MOVE_MAGIC_ROOM); }
        if (setup==2) TURN { MOVE(opponent,MOVE_EMBARGO,hit:TRUE); }
        if (setup==3) TURN { MOVE(opponent,MOVE_GASTRO_ACID,hit:TRUE); }
        TURN { MOVE(player,move); }
    } THEN {
        if (miss || noDamage) { EXPECT_EQ(sHits,0); EXPECT_EQ(sDamageDraws,0); EXPECT_EQ(sCritDraws,0); }
        else { EXPECT_GE(sHits,1); EXPECT_EQ(sHits,sDamageDraws); EXPECT_EQ(sHits,sCritDraws); EXPECT_LE(sHits,count); }
        EXPECT_EQ(sAccuracyDraws,GetMoveAccuracy(move)==100 ? 0 : 1);
        DebugPrintf("DDXV|%u|FINAL|%u|%u|%u|%u|%u|%u|%u|%u\n",sCase,sHits,sDamageDraws,sCritDraws,sAccuracyDraws,
            gBattleMons[0].hp,gBattleMons[1].hp,gBattleMons[1].statStages[STAT_DEF],gBattleMons[0].statStages[STAT_ATK]);
        gDdxrRng=NULL; gDdxrCountSelected=NULL; gDdxrBefore=NULL; gDdxrOperands=NULL;
        gDdxrCalculated=NULL; gDdxrHp=NULL; gDdxrBetween=NULL;
    }
}
'''
    tests = []
    for offset in range(0, len(params), 600):
        tests.append('SINGLE_BATTLE_TEST("DDXO variable-multihit %u") {\n' % (offset // 600) +
                     '    u32 caseId,move,a,d,item,di,hp,count,mode,setup,miss,noDamage,r0,r1,r2,r3,r4,c0,c1,c2,c3,c4;\n' +
                     "\n".join(params[offset:offset + 600]) + tail)
    return PRELUDE + "\n".join(tests)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(doc, rows, generated):
    assert sha(HERE / "corpus.json") == HISTORICAL_SHA
    assert sha(HERE / "repeated-strike-evidence.json") == SLICE12_SHA
    assert doc["pinnedCommit"] == backend.HNS_PINNED_COMMIT
    assert doc["historicalCorpusSha256"] == HISTORICAL_SHA
    assert doc["slice12EvidenceSha256"] == SLICE12_SHA
    assert doc["countPatchSha256"] == sha(COUNT_PATCH)
    assert doc["fixedTwoPatchSha256"] == sha(FIXED_PATCH)
    assert doc["sourceSha256"] == hashlib.sha256(generated.encode()).hexdigest()
    assert len(doc["cases"]) == len(rows)
    expected_moves = {move for move, _ in MOVES}
    selected = set()
    for row, case in zip(rows, doc["cases"]):
        assert case["scenario"] == row and case["pass"] is True
        events = case["events"]
        widths = {"RNG": 4, "COUNT": 5, "PRE": 12, "OPERANDS": 6, "CALC": 4,
                  "HP": 4, "STOP": 2, "BETWEEN": 14, "FINAL": 8}
        assert all(e["kind"] in widths and len(e["values"]) == widths[e["kind"]] and
                   all(type(v) is int and v >= 0 for v in e["values"]) for e in events)
        final = [e["values"] for e in events if e["kind"] == "FINAL"]
        assert len(final) == 1
        hits, damage, crit, accuracy = final[0][:4]
        assert hits == damage == crit
        assert 0 <= hits <= row["nominalCount"]
        assert accuracy == (1 if row["move"] in {"BONE_RUSH", "DOUBLE_SLAP", "COMET_PUNCH", "FURY_ATTACK", "FURY_SWIPES", "PIN_MISSILE", "ROCK_BLAST", "TAIL_SLAP"} else 0)
        counts = [e["values"] for e in events if e["kind"] == "COUNT"]
        rng = [e["values"] for e in events if e["kind"] == "RNG"]
        accuracy_events = [r for r in rng if r[0] == RNG_ACCURACY_TAG]
        assert len(accuracy_events) == accuracy
        if row["sourceImmune"]:
            assert hits == 0 and damage == crit == 0 and not counts
            assert not [r for r in rng if r[0] in (RNG_HITS_TAG, RNG_LOADED_DICE_TAG, RNG_DAMAGE_TAG, RNG_CRITICAL_TAG)]
            assert not [e for e in events if e["kind"] in ("PRE", "CALC", "HP")]
            assert [e["values"] for e in events if e["kind"] == "STOP"] == [[0, 1]]
            selected.add(row["move"])
            continue
        assert len(counts) == 1
        mode, _ability, hold, nominal, ctx_ability = counts[0]
        expected_mode = {"ORDINARY": 0, "LOADED_DICE": 1, "SKILL_LINK": 2}[row["mode"]]
        assert mode == expected_mode and nominal == row["nominalCount"]
        tags = [r[0] for r in rng]
        expected_count_tag = {0: RNG_HITS_TAG, 1: RNG_LOADED_DICE_TAG, 2: None}[mode]
        count_tags = [t for t in tags if t in (RNG_HITS_TAG, RNG_LOADED_DICE_TAG)]
        assert count_tags == ([] if expected_count_tag is None else [expected_count_tag])
        if expected_count_tag is not None:
            draw = next(r for r in rng if r[0] == expected_count_tag)
            assert draw[1] == row["nominalCount"]
            assert draw[2:] == ([0, 5] if mode == 0 else [4, 5])
        assert ctx_ability == _ability
        if row["initialMiss"]:
            assert hits == 0 and damage == crit == 0
            assert accuracy_events[0][1] == 0
            assert not [r for r in rng if r[0] in (RNG_DAMAGE_TAG, RNG_CRITICAL_TAG)]
            assert [e["values"] for e in events if e["kind"] == "STOP"] == [[0, 1]]
            assert not [e for e in events if e["kind"] in ("PRE", "CALC", "HP")]
            selected.add(row["move"])
            continue
        assert len([e for e in events if e["kind"] == "STOP"]) == 1
        calcs = [e["values"] for e in events if e["kind"] == "CALC"]
        hp_events = [e["values"] for e in events if e["kind"] == "HP"]
        pre = [e["values"] for e in events if e["kind"] == "PRE"]
        assert len(calcs) == len(hp_events) == len(pre) == hits
        assert [v[0] for v in pre] == list(range(1, hits + 1))
        assert [v[1] for v in pre] == list(range(row["nominalCount"], row["nominalCount"] - hits, -1))
        damage_rng = [r for r in rng if r[0] == RNG_DAMAGE_TAG]
        crit_rng = [r for r in rng if r[0] == RNG_CRITICAL_TAG]
        assert len(damage_rng) == len(crit_rng) == hits
        assert [r[1] for r in damage_rng] == row["rolls"][:hits]
        assert [r[1] for r in crit_rng] == row["crits"][:hits]
        assert all(r[1] == 1 for r in accuracy_events)
        for calc, hp in zip(calcs, hp_events):
            assert hp[3] == min(hp[1], calc[2]) and hp[1] - hp[2] == hp[3]
        if row["move"] in expected_moves:
            selected.add(row["move"])
    assert selected == expected_moves
    # Identical first-strike inputs produce identical calculated damage regardless of later count.
    by_id = {c["scenario"]["id"]: c for c in doc["cases"]}
    endpoints = {}
    for side, roll in (("low", 15), ("high", 0)):
        event = next(e["values"] for e in by_id[f"first-roll-2-{roll}"]["events"] if e["kind"] == "CALC")
        endpoints[side] = event[2]
    assert endpoints == {"low": 14, "high": 17}
    for roll in range(16):
        first = []
        for count in range(2, 6):
            ev = by_id[f"first-roll-{count}-{roll}"]["events"]
            first.append(next(e["values"][1] for e in ev if e["kind"] == "CALC"))
        assert len(set(first)) == 1
    for row in rows:
        if not row["id"].startswith("early-"):
            continue
        _tag, count_text, prefix_text, side, _edge = row["id"].split("-")
        count, prefix = int(count_text), int(prefix_text)
        one_hit = endpoints[side]
        assert one_hit > 0
        expected_hits = min(count, (row["targetHp"] + one_hit - 1) // one_hit)
        case = by_id[row["id"]]
        events = case["events"]
        final = next(e["values"] for e in events if e["kind"] == "FINAL")
        assert final[0] == expected_hits
        calcs = [e["values"] for e in events if e["kind"] == "CALC"]
        assert all(calc[2] == one_hit for calc in calcs)
        stop = [e["values"] for e in events if e["kind"] == "STOP"]
        assert len(stop) == 1 and stop[0][0] == expected_hits
        assert stop[0][1] == (2 if row["targetHp"] <= expected_hits * one_hit else 3)
        damage_rng = [e["values"] for e in events if e["kind"] == "RNG" and e["values"][0] == RNG_DAMAGE_TAG]
        crit_rng = [e["values"] for e in events if e["kind"] == "RNG" and e["values"][0] == RNG_CRITICAL_TAG]
        assert len(damage_rng) == len(crit_rng) == expected_hits
        assert (len(damage_rng) < count) == (expected_hits < count)
    contact_moves = {"ARM_THRUST", "COMET_PUNCH", "DOUBLE_SLAP", "FURY_ATTACK", "FURY_SWIPES", "TAIL_SLAP"}
    by_id = {c["scenario"]["id"]: c for c in doc["cases"]}
    for move, _move_id in MOVES:
        for count in range(2, 6):
            neutral = by_id[f"{move.lower()}-{count}-low"]["events"]
            technician = by_id[f"technician-{move.lower()}-{count}"]["events"]
            claws = by_id[f"tough-claws-{move.lower()}-{count}"]["events"]
            fluffy = by_id[f"fluffy-{move.lower()}-{count}"]["events"]
            def strike_damage(events):
                return [e["values"][2] for e in events if e["kind"] == "CALC"]
            base_damage = strike_damage(neutral)
            tech_damage = strike_damage(technician)
            claws_damage = strike_damage(claws)
            fluffy_damage = strike_damage(fluffy)
            assert len(base_damage) == len(tech_damage) == len(claws_damage) == len(fluffy_damage) == count
            assert len(set(tech_damage)) == 1 and len(set(claws_damage)) == 1 and len(set(fluffy_damage)) == 1
            assert tech_damage[0] > base_damage[0]  # Technician is applied to each low-BP strike.
            assert (claws_damage[0] != base_damage[0]) == (move in contact_moves)
            assert (fluffy_damage[0] != base_damage[0]) == (move in contact_moves)
            assert all(value == tech_damage[0] for value in tech_damage)
    assert len(rows) >= 350
    print(f"Variable multi-hit evidence: {len(rows)} original-engine cases; all twelve moves; count selector and per-hit RNG observed")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("command", choices=["generate", "verify", "check", "emit-sources", "record"])
    p.add_argument("--upstream-dir", type=Path, default=ROOT.parent / "upstream-hns/pokehns-expansion")
    p.add_argument("--work-dir", type=Path, default=Path("/tmp/hns13-engine"))
    p.add_argument("--toolchain-bin", type=Path, default=Path.home() / "opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin")
    p.add_argument("--log", type=Path, default=Path("/tmp/hns13-engine.log"))
    p.add_argument("--order", choices=["canonical", "reversed"], default="canonical")
    p.add_argument("--jobs", type=int, default=8)
    args = p.parse_args()
    rows = scenarios()
    generated = source(rows)
    if args.command == "emit-sources":
        args.log.write_text(generated)
        return
    if args.command == "check":
        check(json.loads(TARGET.read_text()), rows, generated)
        return
    backend.verify_upstream(args.upstream_dir)
    if args.command != "record":
        backend.export_worktree(args.upstream_dir, args.work_dir)
        subprocess.run(["patch", "-p1", "--forward", "--batch", "-i", str(FIXED_PATCH)], cwd=args.work_dir, check=True)
        backend._apply_patch(args.work_dir, COUNT_PATCH)
    ordered = list(reversed(rows)) if args.order == "reversed" else rows
    sources = backend.render_sources([])
    sources["test/dualdex_oracle/variable_multi_hit.c"] = source(ordered)
    if args.command == "record":
        assert (args.work_dir / "test/dualdex_oracle/variable_multi_hit.c").read_text() == sources["test/dualdex_oracle/variable_multi_hit.c"]
        log = args.log.read_text()
    else:
        log = backend.build_and_run(args.work_dir, args.toolchain_bin, sources, args.jobs)
    args.log.write_text(log)
    events = {i: [] for i in range(len(rows))}
    passes = 0
    for line in backend.ANSI_RE.sub("", log).splitlines():
        m = re.search(r"DDXV\|(\d+)\|(\w+)\|([\d|]+)", line)
        if m:
            events[int(m[1])].append(dict(kind=m[2], values=[int(v) for v in m[3].split("|")]))
        status = backend.RESULT_RE.match(line.strip())
        if status and status.group("name").startswith("DDXO variable-multihit "):
            assert status.group("result") == "PASS", line
            passes += 1
    assert passes == (len(rows) + 599) // 600, (passes, (len(rows) + 599) // 600)
    ordered_ids = [r["id"] for r in ordered]
    event_by_id = {ordered_ids[i]: events[i] for i in range(len(rows))}
    doc = dict(schemaVersion=1, pinnedCommit=backend.HNS_PINNED_COMMIT,
               historicalCorpusSha256=HISTORICAL_SHA, slice12EvidenceSha256=SLICE12_SHA,
               sourceSha256=hashlib.sha256(generated.encode()).hexdigest(),
               countPatchSha256=sha(COUNT_PATCH), fixedTwoPatchSha256=sha(FIXED_PATCH),
               toolchain=backend.toolchain_identity(args.toolchain_bin), backend=backend.backend_provenance(args.work_dir),
               cases=[dict(scenario=row, **{"pass": True}, events=event_by_id[row["id"]]) for row in rows])
    check(doc, rows, generated)
    encoded = json.dumps(doc, sort_keys=True, separators=(",", ":")) + "\n"
    if args.command == "verify":
        assert TARGET.read_text() == encoded, "Reversed variable-count engine traces differ"
    else:
        TARGET.write_text(encoded)


if __name__ == "__main__":
    main()
