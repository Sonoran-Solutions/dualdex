#!/usr/bin/env python3
"""Additive pinned-engine execution and selected-roll evidence for Slice 15."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

import oracle_backend as backend

ROOT = Path(__file__).resolve().parents[2]
TARGET = Path(__file__).with_name("semi-invulnerable-evidence.json")
MOVES = ("FLY", "DIG", "DIVE", "BOUNCE", "PHANTOM_FORCE")
MOVE_IDS = {"FLY": 19, "DIG": 91, "DIVE": 291, "BOUNCE": 340, "PHANTOM_FORCE": 566}
ROLLS = tuple(range(16))

SPECIAL_SOURCE = r'''
extern void (*gDdxrBefore)(struct BattleContext *ctx);
extern void (*gDdxrOperands)(struct BattleContext *ctx, u32 attack, u32 defense);
extern void (*gDdxrCalculated)(struct BattleContext *ctx, s32 raw, s32 adjusted);
extern void (*gDdxrScreens)(struct BattleContext *, u32, bool32, bool32, bool32);
extern void (*gDdxrOtherModifier)(struct BattleContext *, uq4_12_t);
static u16 sObservedItem;
static bool32 sObservedProtect;
static bool32 sForceMiss;
static u32 sObservedReflect;
static u32 sObservedSideStatus;
static bool32 sObservedCritical;
static u32 sObservedAtk, sObservedDef, sObservedDefSide, sObservedScreenFlags, sObservedOtherModifier;
static s32 sObservedRaw, sObservedAdjusted;
static u32 sObservedAttack, sObservedDefense;
static void DdxsBefore(struct BattleContext *ctx)
{
    if (gCurrentMove != sMove || ctx->aiCalc || !ctx->randomFactor) return;
    sObservedItem = gBattleMons[0].item;
    sObservedProtect = gProtectStructs[1].protected;
    sObservedReflect = gSideTimers[1].reflectTimer;
    sObservedSideStatus = gSideStatuses[1];
    sObservedAtk = ctx->battlerAtk; sObservedDef = ctx->battlerDef; sObservedDefSide = GetBattlerSide(ctx->battlerDef);
}
static void DdxsScreens(struct BattleContext *ctx,u32 sideStatus,bool32 reflect,bool32 lightScreen,bool32 isCrit)
{
    if (ctx->aiCalc || !ctx->randomFactor || ctx->battlerAtk != 0 || ctx->battlerDef != 1 || ctx->move != sMove) return;
    sObservedScreenFlags = (reflect ? 1 : 0) | (lightScreen ? 2 : 0) | (isCrit ? 4 : 0) | ((sideStatus & SIDE_STATUS_REFLECT) ? 8 : 0) | (ctx->isSelfInflicted ? 16 : 0) | (ctx->abilityAtk == ABILITY_INFILTRATOR ? 32 : 0);
}
static void DdxsOperands(struct BattleContext *ctx,u32 attack,u32 defense)
{
    if (ctx->aiCalc || !ctx->randomFactor || ctx->battlerAtk != 0 || ctx->battlerDef != 1 || ctx->move != sMove) return;
    sObservedAttack=attack; sObservedDefense=defense;
}
static void DdxsOtherModifier(struct BattleContext *ctx,uq4_12_t modifier)
{
    if (ctx->aiCalc || !ctx->randomFactor || ctx->battlerAtk != 0 || ctx->battlerDef != 1 || ctx->move != sMove) return;
    sObservedOtherModifier = modifier;
}
static void DdxsCalculated(struct BattleContext *ctx,s32 raw,s32 adjusted)
{
    if (gCurrentMove == sMove && ctx->battlerAtk == 0 && ctx->battlerDef == 1 && !ctx->aiCalc && ctx->randomFactor) { sObservedCritical = ctx->isCrit; sObservedRaw = raw; sObservedAdjusted = adjusted; }
}

SINGLE_BATTLE_TEST("DDXO semi-invulnerable-bounce-sheer-force", s16 damage)
{
    u32 caseId, ability, item;
    PARAMETRIZE { caseId=0; ability=ABILITY_INSOMNIA; item=ITEM_NONE; }
    PARAMETRIZE { caseId=1; ability=ABILITY_SHEER_FORCE; item=ITEM_NONE; }
    PARAMETRIZE { caseId=2; ability=ABILITY_INSOMNIA; item=ITEM_LIFE_ORB; }
    PARAMETRIZE { caseId=3; ability=ABILITY_SHEER_FORCE; item=ITEM_LIFE_ORB; }
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(ability); Item(item); Attack(151); Level(50); Speed(200); HP(60000); MaxHP(60000); }
        OPPONENT(SPECIES_SNORLAX) { Ability(ABILITY_INSOMNIA); Defense(109); Level(50); Speed(100); HP(60000); MaxHP(60000); }
        sCase=100+caseId; sMove=MOVE_BOUNCE; sState=STATE_ON_AIR; sRoll=8;
        sPrepCalls=0; sReleaseCalls=0; sDamageDraws=0; sPrepHp=0; sForceCrit=0; sObservedItem=ITEM_NONE; sObservedProtect=FALSE;
        gDdxsPhase=DdxsPhaseObserve; gDdxrRng=DdxsRng; gDdxrBefore=DdxsBefore; gDdxrCalculated=DdxsCalculated; gDdxrScreens=DdxsScreens; gDdxrOtherModifier=DdxsOtherModifier; gDdxrOperands=DdxsOperands; gFieldStatuses=0;
    } WHEN {
        TURN { MOVE(player, MOVE_BOUNCE); }
        TURN { SKIP_TURN(player); }
    } SCENE {
        ANIMATION(ANIM_TYPE_MOVE, MOVE_BOUNCE, player);
        NONE_OF { HP_BAR(opponent); }
        ANIMATION(ANIM_TYPE_MOVE, MOVE_BOUNCE, player);
        HP_BAR(opponent, captureDamage:&results[i].damage);
    } THEN {
        EXPECT_EQ(sPrepCalls,1); EXPECT_EQ(sReleaseCalls,1); EXPECT_EQ(sDamageDraws,1);
        DebugPrintf("DDXSS|BOUNCE|%u|%u|%u|%u|%u|%u\n",caseId,results[i].damage,sObservedItem,
            gBattleMons[1].status1,gBattleMons[0].hp,sObservedProtect);
        gDdxsPhase=NULL; gDdxrRng=NULL; gDdxrBefore=NULL; gDdxrCalculated=NULL; gDdxrScreens=NULL; gDdxrOtherModifier=NULL; gDdxrOperands=NULL;
    }
}

SINGLE_BATTLE_TEST("DDXO semi-invulnerable-phantom-force-protect", s16 damage)
{
    u32 caseId;
    PARAMETRIZE { caseId=0; }
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Attack(151); Level(50); Speed(200); HP(60000); MaxHP(60000); }
        OPPONENT(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Defense(109); Level(50); Speed(250); HP(60000); MaxHP(60000); }
        sCase=110; sMove=MOVE_PHANTOM_FORCE; sState=STATE_PHANTOM_FORCE; sRoll=8;
        sPrepCalls=0; sReleaseCalls=0; sDamageDraws=0; sPrepHp=0; sForceCrit=0; sObservedItem=ITEM_NONE; sObservedProtect=FALSE;
        gDdxsPhase=DdxsPhaseObserve; gDdxrRng=DdxsRng; gDdxrBefore=DdxsBefore; gDdxrCalculated=DdxsCalculated; gDdxrScreens=DdxsScreens; gDdxrOtherModifier=DdxsOtherModifier; gDdxrOperands=DdxsOperands; gFieldStatuses=0;
    } WHEN {
        TURN { MOVE(player, MOVE_PHANTOM_FORCE); }
        TURN { MOVE(opponent, MOVE_PROTECT); SKIP_TURN(player); }
    } SCENE {
        ANIMATION(ANIM_TYPE_MOVE, MOVE_PHANTOM_FORCE, player);
        NONE_OF { HP_BAR(opponent); }
        ANIMATION(ANIM_TYPE_MOVE, MOVE_PHANTOM_FORCE, player);
        HP_BAR(opponent, captureDamage:&results[i].damage);
    } THEN {
        EXPECT_EQ(caseId,0);
        EXPECT_EQ(sPrepCalls,1); EXPECT_EQ(sReleaseCalls,1); EXPECT_EQ(sDamageDraws,1);
        EXPECT_GT(results[i].damage,0);
        DebugPrintf("DDXSS|PHANTOM_PROTECT|%u|%u|%u\n",results[i].damage,sObservedProtect,gBattleMons[1].hp);
        gDdxsPhase=NULL; gDdxrRng=NULL; gDdxrBefore=NULL;
    }
}

SINGLE_BATTLE_TEST("DDXO semi-invulnerable-gravity-bans")
{
    u32 move;
    PARAMETRIZE { move=MOVE_FLY; }
    PARAMETRIZE { move=MOVE_BOUNCE; }
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Attack(151); Level(50); Speed(100); HP(60000); MaxHP(60000); }
        OPPONENT(SPECIES_SNORLAX) { Ability(ABILITY_INSOMNIA); Speed(200); HP(60000); MaxHP(60000); }
        sMove=move; sState=GetMoveTwoTurnAttackStatus(move); sPrepCalls=0; sReleaseCalls=0; sForceCrit=0;
        gDdxsPhase=DdxsPhaseObserve; gFieldStatuses=0;
    } WHEN { TURN { MOVE(opponent, MOVE_GRAVITY); MOVE(player, move); } }
    THEN {
        EXPECT_EQ(sPrepCalls,0); EXPECT_EQ(sReleaseCalls,0);
        EXPECT_EQ((u32)gBattleMons[0].volatiles.semiInvulnerable,(u32)STATE_NONE);
        EXPECT_EQ(gBattleMons[1].hp,60000);
        DebugPrintf("DDXSS|GRAVITY|%u|%u|%u|%u|%u\n",move,sPrepCalls,sReleaseCalls,
            gBattleMons[0].volatiles.semiInvulnerable,gBattleMons[1].hp);
        gDdxsPhase=NULL;
    }
}

SINGLE_BATTLE_TEST("DDXO semi-invulnerable-active-power-herb", s16 damage)
{
    u32 caseId;
    PARAMETRIZE { caseId=0; }
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Item(ITEM_POWER_HERB); Attack(151); Level(50); Speed(200); HP(60000); MaxHP(60000); }
        OPPONENT(SPECIES_SNORLAX) { Ability(ABILITY_INSOMNIA); Defense(109); Level(50); Speed(100); HP(60000); MaxHP(60000); }
        sCase=111; sMove=MOVE_FLY; sState=STATE_ON_AIR; sRoll=8; sForceCrit=0;
        sPrepCalls=0; sReleaseCalls=0; sDamageDraws=0; sPrepHp=0; sObservedItem=ITEM_POWER_HERB; sObservedProtect=FALSE;
        gDdxsPhase=DdxsPhaseObserve; gDdxrRng=DdxsRng; gDdxrBefore=DdxsBefore; gDdxrCalculated=DdxsCalculated; gDdxrScreens=DdxsScreens; gDdxrOtherModifier=DdxsOtherModifier; gDdxrOperands=DdxsOperands; gFieldStatuses=0;
    } WHEN { TURN { MOVE(player, MOVE_FLY); } }
    SCENE { HP_BAR(opponent, captureDamage:&results[i].damage); }
    THEN {
        EXPECT_EQ(caseId,0);
        EXPECT_GT(results[i].damage,0); EXPECT_EQ(sObservedItem,ITEM_NONE); EXPECT_EQ(gBattleMons[0].item,ITEM_NONE);
        DebugPrintf("DDXSS|POWER_HERB|%u|%u|%u\n",results[i].damage,sObservedItem,gBattleMons[0].item);
        gDdxsPhase=NULL; gDdxrRng=NULL; gDdxrBefore=NULL;
    }
}


SINGLE_BATTLE_TEST("DDXO semi-invulnerable-miss")
{
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Attack(151); Level(50); Speed(200); HP(60000); MaxHP(60000); }
        OPPONENT(SPECIES_SNORLAX) { Ability(ABILITY_INSOMNIA); Defense(109); Level(50); Speed(100); HP(60000); MaxHP(60000); }
        sCase=112; sMove=MOVE_FLY; sState=STATE_ON_AIR; sRoll=8; sForceCrit=0; sForceMiss=1;
        sPrepCalls=0; sReleaseCalls=0; sDamageDraws=0;
        gDdxsPhase=DdxsPhaseObserve; gDdxrRng=DdxsRng;
    } WHEN { TURN { MOVE(player, MOVE_FLY); } TURN { SKIP_TURN(player); } }
    SCENE { ANIMATION(ANIM_TYPE_MOVE, MOVE_FLY, player); NONE_OF { HP_BAR(opponent); } }
    THEN {
        EXPECT_EQ(sPrepCalls,1); EXPECT_EQ(sReleaseCalls,1); EXPECT_EQ(sDamageDraws,0);
        EXPECT_EQ(gBattleMons[1].hp,60000);
        DebugPrintf("DDXSS|MISS|%u|%u|%u|%u\n",sPrepCalls,sReleaseCalls,sDamageDraws,gBattleMons[1].hp);
        sForceMiss=0; gDdxsPhase=NULL; gDdxrRng=NULL;
    }
}

SINGLE_BATTLE_TEST("DDXO semi-invulnerable-type-immunity")
{
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Attack(151); Level(50); Speed(200); HP(60000); MaxHP(60000); }
        OPPONENT(SPECIES_PIDGEOT) { Ability(ABILITY_INSOMNIA); Defense(109); Level(50); Speed(100); HP(60000); MaxHP(60000); }
        sCase=113; sMove=MOVE_DIG; sState=STATE_UNDERGROUND; sRoll=8; sForceCrit=0; sForceMiss=0;
        sPrepCalls=0; sReleaseCalls=0; sDamageDraws=0;
        gDdxsPhase=DdxsPhaseObserve; gDdxrRng=DdxsRng;
    } WHEN { TURN { MOVE(player, MOVE_DIG); } TURN { SKIP_TURN(player); } }
    SCENE { ANIMATION(ANIM_TYPE_MOVE, MOVE_DIG, player); NONE_OF { HP_BAR(opponent); } }
    THEN {
        EXPECT_EQ(sPrepCalls,1); EXPECT_EQ(sReleaseCalls,1); EXPECT_EQ(sDamageDraws,0);
        EXPECT_EQ(gBattleMons[1].hp,60000);
        DebugPrintf("DDXSS|TYPE_IMMUNITY|%u|%u|%u|%u\n",sPrepCalls,sReleaseCalls,sDamageDraws,gBattleMons[1].hp);
        gDdxsPhase=NULL; gDdxrRng=NULL;
    }
}

SINGLE_BATTLE_TEST("DDXO semi-invulnerable-faint-replacement-cleanup")
{
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Speed(100); HP(1); MaxHP(1); }
        PLAYER(SPECIES_PIDGEOT) { Speed(100); }
        OPPONENT(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Attack(500); Speed(200); HP(60000); MaxHP(60000); }
        sCase=114; sMove=MOVE_DIG; sState=STATE_UNDERGROUND; sRoll=8; sForceCrit=0;
        sPrepCalls=0; sReleaseCalls=0; sDamageDraws=0;
        gDdxsPhase=DdxsPhaseObserve; gDdxrRng=DdxsRng;
    } WHEN {
        TURN { MOVE(player, MOVE_DIG); MOVE(opponent, MOVE_CELEBRATE); }
        TURN { MOVE(opponent, MOVE_EARTHQUAKE); SKIP_TURN(player); SEND_OUT(player, 1); }
    } THEN {
        EXPECT_EQ(sPrepCalls,1); EXPECT_EQ(sReleaseCalls,0); EXPECT_EQ(sDamageDraws,0);
        EXPECT_EQ(player->species, SPECIES_PIDGEOT);
        EXPECT_EQ((u32)gBattleMons[0].volatiles.multipleTurns, (u32)FALSE);
        EXPECT_EQ((u32)gBattleMons[0].volatiles.semiInvulnerable, STATE_NONE);
        EXPECT_EQ(gLockedMoves[0], MOVE_DIG);
        DebugPrintf("DDXSS|FAINT_REPLACEMENT|%u|%u|%u|%u|%u\n",sPrepCalls,sReleaseCalls,
            gBattleMons[0].volatiles.multipleTurns,gBattleMons[0].volatiles.semiInvulnerable,gLockedMoves[0]);
        gDdxsPhase=NULL; gDdxrRng=NULL;
    }
}

'''


def composition_source():
    variants = [
        ("SPECIES_MACHAMP", "SPECIES_SNORLAX", "ITEM_NONE", 0, 0, "ABILITY_INSOMNIA", "ABILITY_INSOMNIA"),
        ("SPECIES_PIDGEOT", "SPECIES_SNORLAX", "ITEM_NONE", 0, 0, "ABILITY_INSOMNIA", "ABILITY_INSOMNIA"),
        ("SPECIES_MACHAMP", "SPECIES_SNORLAX", "ITEM_NONE", 1, 0, "ABILITY_INSOMNIA", "ABILITY_INSOMNIA"),
        ("SPECIES_MACHAMP", "SPECIES_SNORLAX", "ITEM_NONE", 0, 0, "ABILITY_INSOMNIA", "ABILITY_INSOMNIA"),
        ("SPECIES_MACHAMP", "SPECIES_SNORLAX", "ITEM_NONE", 0, 1, "ABILITY_INSOMNIA", "ABILITY_INSOMNIA"),
        ("SPECIES_MACHAMP", "SPECIES_BULBASAUR", "ITEM_NONE", 0, 0, "ABILITY_INSOMNIA", "ABILITY_INSOMNIA"),
        ("SPECIES_MACHAMP", "SPECIES_SNORLAX", "ITEM_LIFE_ORB", 0, 0, "ABILITY_INSOMNIA", "ABILITY_INSOMNIA"),
        ("SPECIES_PIDGEOT", "SPECIES_BULBASAUR", "ITEM_LIFE_ORB", 1, 1, "ABILITY_INSOMNIA", "ABILITY_INSOMNIA"),
        ("SPECIES_MACHAMP", "SPECIES_SNORLAX", "ITEM_NONE", 0, 0, "ABILITY_TOUGH_CLAWS", "ABILITY_INSOMNIA"),
        ("SPECIES_MACHAMP", "SPECIES_SNORLAX", "ITEM_NONE", 0, 0, "ABILITY_INSOMNIA", "ABILITY_FLUFFY"),
        ("SPECIES_MACHAMP", "SPECIES_SNORLAX", "ITEM_NONE", 0, 0, "ABILITY_LONG_REACH", "ABILITY_FLUFFY"),
    ]
    rows = []
    for mode, (atk, defender, item, critical, stage, attacker_ability, defender_ability) in enumerate(variants):
        reflect = int(mode in (3, 7))
        for roll in ROLLS:
            rows.append(f"    PARAMETRIZE {{ mode={mode}; roll={roll}; attackerSpecies={atk}; defenderSpecies={defender}; item={item}; critical={critical}; reflect={reflect}; stage={stage}; attackerAbility={attacker_ability}; defenderAbility={defender_ability}; }}")
    template = r'''
SINGLE_BATTLE_TEST("DDXO semi-invulnerable-source-compositions", s16 damage)
{
    u32 mode, roll, attackerSpecies, defenderSpecies, item, critical, reflect, stage, attackerAbility, defenderAbility;
__PARAMS__
    GIVEN {
        PLAYER(attackerSpecies) { Ability(attackerAbility); Item(item); Attack(151); Level(50); Speed(200); HP(60000); MaxHP(60000); }
        OPPONENT(defenderSpecies) { Ability(defenderAbility); Defense(109); Level(50); Speed(100); HP(60000); MaxHP(60000); }
        sCase=200+mode; sMove=MOVE_FLY; sState=STATE_ON_AIR; sRoll=roll; sForceCrit=critical;
        sPrepCalls=0; sReleaseCalls=0; sDamageDraws=0; sPrepHp=0; sObservedItem=ITEM_NONE; sObservedProtect=FALSE;
        gDdxsPhase=DdxsPhaseObserve; gDdxrRng=DdxsRng; gDdxrBefore=DdxsBefore; gDdxrCalculated=DdxsCalculated; gDdxrScreens=DdxsScreens; gDdxrOtherModifier=DdxsOtherModifier; gDdxrOperands=DdxsOperands; gFieldStatuses=0;
    } WHEN {
        if (stage) TURN { MOVE(player, MOVE_SWORDS_DANCE); }
        if (reflect) TURN { MOVE(opponent, MOVE_REFLECT); }
        TURN { MOVE(player, MOVE_FLY); }
        TURN { SKIP_TURN(player); }
    } SCENE {
        if (stage) ANIMATION(ANIM_TYPE_MOVE, MOVE_SWORDS_DANCE, player);
        if (reflect) ANIMATION(ANIM_TYPE_MOVE, MOVE_REFLECT, opponent);
        ANIMATION(ANIM_TYPE_MOVE, MOVE_FLY, player);
        NONE_OF { HP_BAR(opponent); }
        ANIMATION(ANIM_TYPE_MOVE, MOVE_FLY, player);
        HP_BAR(opponent, captureDamage:&results[i].damage);
    } THEN {
        EXPECT_EQ(sPrepCalls,1); EXPECT_EQ(sReleaseCalls,1); EXPECT_EQ(sDamageDraws,1);
        DebugPrintf("DDXSS|COMPOSITION|%u|%u|%u|%u|%u|%u|%u|%u|%u|%u|%u|%u|%u|%d|%d|%u|%u\n",mode,roll,results[i].damage,sObservedItem,gBattleMons[1].hp,sObservedReflect,sObservedSideStatus,sObservedCritical,sObservedAtk,sObservedDef,sObservedDefSide,sObservedScreenFlags,sObservedOtherModifier,sObservedRaw,sObservedAdjusted,sObservedAttack,sObservedDefense);
        gDdxsPhase=NULL; gDdxrRng=NULL; gDdxrBefore=NULL; gDdxrCalculated=NULL; gDdxrScreens=NULL; gDdxrOtherModifier=NULL; gDdxrOperands=NULL;
    }
}
'''
    return SPECIAL_SOURCE + template.replace("__PARAMS__", "\n".join(rows))


def source(order="canonical"):
    cases = [(move, roll) for move in MOVES for roll in ROLLS]
    case_ids = {case: i for i, case in enumerate(cases)}
    if order == "reversed":
        cases.reverse()
    params = []
    states = {"FLY": "STATE_ON_AIR", "DIG": "STATE_UNDERGROUND", "DIVE": "STATE_UNDERWATER",
              "BOUNCE": "STATE_ON_AIR", "PHANTOM_FORCE": "STATE_PHANTOM_FORCE"}
    targets = {"FLY": "SNORLAX", "DIG": "SNORLAX", "DIVE": "SNORLAX",
               "BOUNCE": "SNORLAX", "PHANTOM_FORCE": "MACHAMP"}
    for move, roll in cases:
        case_id = case_ids[(move, roll)]
        params.append(f"    PARAMETRIZE {{ caseId={case_id}; move=MOVE_{move}; state={states[move]}; species=SPECIES_{targets[move]}; roll={roll}; }}")
    return r'''#include "global.h"
#include "battle.h"
#include "battle_util.h"
#include "move.h"
#include "test/battle.h"
extern bool32 (*gDdxrRng)(u32 tag, u32 lo, u32 hi, u32 *value);
void (*gDdxsPhase)(u32 phase, u32 move, u32 battler);
static u32 sCase, sMove, sState, sRoll, sPrepCalls, sReleaseCalls, sPrepHp, sDamageDraws;
static u32 sForceCrit;
static bool32 sForceMiss;
static bool32 DdxsRng(u32 tag,u32 lo,u32 hi,u32 *value)
{
    if (gCurrentMove != sMove) return FALSE;
    if (tag == RNG_ACCURACY && sForceMiss) {
        *value = lo; EXPECT_GE(*value,lo); EXPECT_LE(*value,hi); return TRUE;
    }
    if (tag == RNG_CRITICAL_HIT) {
        *value = sForceCrit ? hi : lo;
        EXPECT_GE(*value,lo); EXPECT_LE(*value,hi); return TRUE;
    }
    if (tag != RNG_DAMAGE_MODIFIER) return FALSE;
    sDamageDraws++;
    *value = 15-sRoll;
    EXPECT_GE(*value,lo); EXPECT_LE(*value,hi);
    return TRUE;
}
static void DdxsPhaseObserve(u32 phase,u32 move,u32 battler)
{
    if (battler != 0 || move != sMove) return;
    if (phase == 1) {
        EXPECT_EQ((u32)gBattleMons[0].volatiles.multipleTurns, (u32)FALSE);
        EXPECT_EQ((u32)gBattleMons[0].volatiles.semiInvulnerable, sState);
        EXPECT_EQ(gLockedMoves[0], sMove);
        EXPECT_EQ(gBattleMons[1].hp, 60000);
        sPrepCalls++;
        sPrepHp=gBattleMons[1].hp;
        DebugPrintf("DDXS|%u|PREP|%u|%u|%u|%u|%u\n", sCase, sMove,
            gBattleMons[0].volatiles.multipleTurns, gBattleMons[0].volatiles.semiInvulnerable,
            gLockedMoves[0], gBattleMons[1].hp);
        return;
    }
    EXPECT_EQ(phase, 2);
    EXPECT_EQ((u32)gBattleMons[0].volatiles.multipleTurns, (u32)FALSE);
    EXPECT_EQ((u32)gBattleMons[0].volatiles.semiInvulnerable, STATE_NONE);
    EXPECT_EQ(gLockedMoves[0], sMove);
    sReleaseCalls++;
    DebugPrintf("DDXS|%u|RELEASE|%u|%u|%u|%u|%u|%u\n", sCase, sMove,
        gBattleMons[0].volatiles.multipleTurns, gBattleMons[0].volatiles.semiInvulnerable,
        gLockedMoves[0], gBattleMons[1].hp, gBattleMons[1].status1);
}
SINGLE_BATTLE_TEST("DDXO semi-invulnerable-neutral", s16 damage)
{
    u32 caseId, move, state, species, roll;
''' + "\n".join(params) + r'''
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); Attack(151); SpAttack(151); Level(50); Speed(200); HP(60000); MaxHP(60000); }
        OPPONENT(species) { Ability(ABILITY_INSOMNIA); Defense(109); SpDefense(109); Level(50); Speed(100); HP(60000); MaxHP(60000); }
        sCase=caseId; sMove=move; sState=state; sRoll=roll; sPrepCalls=0; sReleaseCalls=0; sPrepHp=0; sDamageDraws=0; sForceCrit=0;
        gDdxsPhase=DdxsPhaseObserve;
        gDdxrRng=DdxsRng;
        gFieldStatuses=0;
    } WHEN {
        TURN { MOVE(player, move); }
        TURN { SKIP_TURN(player); }
    } SCENE {
        ANIMATION(ANIM_TYPE_MOVE, move, player);
        NONE_OF { HP_BAR(opponent); }
        ANIMATION(ANIM_TYPE_MOVE, move, player);
        HP_BAR(opponent, captureDamage:&results[i].damage);
    } THEN {
        EXPECT_EQ(sPrepCalls,1);
        EXPECT_EQ(sReleaseCalls,1);
        EXPECT_EQ(sDamageDraws,1);
        EXPECT_GT(results[i].damage,0);
        EXPECT_EQ(sPrepHp,60000);
        EXPECT_EQ(gBattleMons[1].hp,sPrepHp-results[i].damage);
        DebugPrintf("DDXS|%u|DAMAGE|%u|%u|%u\n",caseId,move,roll,results[i].damage);
        gDdxsPhase=NULL;
        gDdxrRng=NULL;
    }
}
''' + composition_source()


def run(args):
    backend.verify_upstream(args.upstream_dir)
    if args.command == "run":
        backend.export_worktree(args.upstream_dir, args.work_dir)
    elif not (args.work_dir / ".histignore").is_file():
        raise RuntimeError("resume requires a previously exported pinned engine worktree")
    runner = args.work_dir / "test/test_runner_battle.c"
    resolution = args.work_dir / "src/battle_move_resolution.c"
    if "gDdxrRng" not in runner.read_text():
        subprocess.run(["patch", "-p1", "--forward", "--batch", "-i",
                        str(Path(__file__).with_name("patches") / "0010-repeated-strike-observation.patch")],
                       cwd=args.work_dir, check=True)
    if "gDdxsPhase" not in resolution.read_text():
        subprocess.run(["patch", "-p1", "--forward", "--batch", "-i",
                        str(Path(__file__).with_name("patches") / "0011-slice15-phase-observation.patch")],
                       cwd=args.work_dir, check=True)
    battle_util = args.work_dir / "src/battle_util.c"
    if "gDdxrOtherModifier != NULL" not in battle_util.read_text():
        subprocess.run(["patch", "-p1", "--forward", "--batch", "-i",
                        str(Path(__file__).with_name("patches") / "0012-slice15-screen-observation.patch")],
                       cwd=args.work_dir, check=True)
    sources = backend.render_sources([])
    generated = source(args.order)
    sources["test/dualdex_oracle/semi_invulnerable.c"] = generated
    log = backend.build_and_run(args.work_dir, args.toolchain_bin, sources, args.jobs)
    args.log.write_text(log)
    clean = backend.ANSI_RE.sub("", log)
    cases = {}
    special = []
    for line in clean.splitlines():
        match = re.search(r"DDXS\|(\d+)\|(PREP|RELEASE|DAMAGE)\|([\d|]+)", line)
        if match and int(match[1]) < 80:
            cases.setdefault(int(match[1]), []).append({"kind": match[2], "values": [int(v) for v in match[3].split("|")]})
        extra = re.search(r"DDXSS\|([^|]+)\|([\d|]+)", line)
        if extra:
            special.append({"kind": extra[1], "values": [int(v) for v in extra[2].split("|")]})
    statuses = [m.group("result") for line in clean.splitlines()
                if (m := backend.RESULT_RE.match(line.strip())) and m.group("name").startswith("DDXO semi-invulnerable-")]
    assert statuses == ["PASS"] * 9, statuses
    assert set(cases) == set(range(80)), (len(cases), sorted(set(range(80))-set(cases)))
    damage_vectors = {move: [] for move in MOVES}
    execution = {move: None for move in MOVES}
    for case_id, (move, roll) in enumerate((x for x in ((m, r) for m in MOVES for r in ROLLS))):
        events = cases[case_id]
        prep = [e for e in events if e["kind"] == "PREP"]
        release = [e for e in events if e["kind"] == "RELEASE"]
        damage = [e for e in events if e["kind"] == "DAMAGE"]
        assert len(prep) == len(release) == len(damage) == 1
        move_id = MOVE_IDS[move]
        assert prep[0]["values"][0] == move_id and prep[0]["values"][1] == 0
        assert prep[0]["values"][2] != 0 and prep[0]["values"][3:] == [move_id, 60000]
        assert release[0]["values"][0] == move_id and release[0]["values"][1:3] == [0, 0]
        assert release[0]["values"][3] == move_id
        assert damage[0]["values"][:2] == [move_id, roll] and damage[0]["values"][2] > 0
        phase_pair = {"preparation": prep[0]["values"], "releaseBeforeDamage": release[0]["values"]}
        if execution[move] is None:
            execution[move] = phase_pair
        else:
            assert execution[move] == phase_pair
        damage_vectors[move].append(damage[0]["values"][2])
    assert all(len(values) == 16 for values in damage_vectors.values())
    canonical = source("canonical")
    bounce = sorted((row for row in special if row["kind"] == "BOUNCE"), key=lambda row: row["values"][0])
    phantom = [row for row in special if row["kind"] == "PHANTOM_PROTECT"]
    gravity = sorted((row for row in special if row["kind"] == "GRAVITY"), key=lambda row: row["values"][0])
    herb = [row for row in special if row["kind"] == "POWER_HERB"]
    compositions = sorted((row for row in special if row["kind"] == "COMPOSITION"), key=lambda row: row["values"][:2])
    assert len(bounce) == 4 and len(phantom) == 1 and len(gravity) == 2 and len(herb) == 1 and len(compositions) == 176
    assert len([row for row in special if row["kind"] == "MISS"]) == 1
    assert len([row for row in special if row["kind"] == "TYPE_IMMUNITY"]) == 1
    lifecycle = [row for row in special if row["kind"] == "FAINT_REPLACEMENT"]
    assert len(lifecycle) == 1 and lifecycle[0]["values"] == [1, 0, 0, 0, MOVE_IDS["DIG"]]
    assert bounce[1]["values"][1] > bounce[0]["values"][1] and bounce[3]["values"][1] > bounce[2]["values"][1]
    assert bounce[2]["values"][2] != 0 and bounce[3]["values"][2] != 0
    assert bounce[0]["values"][3] != 0 and bounce[1]["values"][3] == 0
    assert bounce[2]["values"][4] < 60000 and bounce[3]["values"][4] == 60000
    assert phantom[0]["values"][0] > 0 and phantom[0]["values"][1] != 0
    assert all(row["values"][1:4] == [0, 0, 0] and row["values"][4] == 60000 for row in gravity)
    assert herb[0]["values"][0] > 0 and herb[0]["values"][1:] == [0, 0]
    composition_vectors = {mode: [row["values"][2] for row in compositions if row["values"][0] == mode] for mode in range(11)}
    assert all(len(values) == 16 for values in composition_vectors.values())
    assert all(a < b for a, b in zip(composition_vectors[0], composition_vectors[1]))
    assert all(a < b for a, b in zip(composition_vectors[0], composition_vectors[2]))
    assert all(a > b for a, b in zip(composition_vectors[0], composition_vectors[3]))
    assert all(a < b for a, b in zip(composition_vectors[0], composition_vectors[4]))
    assert all(a < b for a, b in zip(composition_vectors[0], composition_vectors[5]))
    assert all(row["values"][3] != 0 for row in compositions if row["values"][0] in (6, 7))
    assert all(a < b for a, b in zip(composition_vectors[1], composition_vectors[7]))
    assert all(a < b for a, b in zip(composition_vectors[0], composition_vectors[8]))
    assert all(a > b for a, b in zip(composition_vectors[0], composition_vectors[9]))
    assert composition_vectors[0] == composition_vectors[10]
    special.sort(key=lambda row: (row["kind"], row["values"]))
    doc = {"schemaVersion": 1, "pinnedCommit": backend.HNS_PINNED_COMMIT,
           "sourceSha256": hashlib.sha256(canonical.encode()).hexdigest(),
           "backend": {**backend.backend_provenance(args.work_dir), "slice15ObservationPatches": [
               {"path": f"patches/{name}", "sha256": hashlib.sha256((Path(__file__).with_name("patches") / name).read_bytes()).hexdigest()}
               for name in ("0010-repeated-strike-observation.patch", "0011-slice15-phase-observation.patch", "0012-slice15-screen-observation.patch")]},
           "executionEvidence": [{"move": move, **execution[move]} for move in MOVES],
           "damageVectors": [{"move": move, "caseCount": 16, "rolls": damage_vectors[move]} for move in MOVES],
           "specialEngineEvidence": special}
    rendered = json.dumps(doc, sort_keys=True, separators=(",", ":")) + "\n"
    if args.command == "verify":
        assert TARGET.read_text() == rendered, "Canonical and reversed-order original-engine results differ"
        print("Slice 15 reversed-order replay matches committed original-engine evidence")
    else:
        TARGET.write_text(rendered)
        print("Slice 15 original-engine neutral path: 80 actual two-turn executions across five moves")


def check_artifact():
    doc = json.loads(TARGET.read_text())
    canonical = source("canonical")
    assert doc["schemaVersion"] == 1 and doc["pinnedCommit"] == backend.HNS_PINNED_COMMIT
    assert doc["sourceSha256"] == hashlib.sha256(canonical.encode()).hexdigest()
    assert [r["move"] for r in doc["executionEvidence"]] == list(MOVES)
    assert [r["move"] for r in doc["damageVectors"]] == list(MOVES)
    for phase, vector in zip(doc["executionEvidence"], doc["damageVectors"]):
        move_id = MOVE_IDS[phase["move"]]
        prep = phase["preparation"]
        release = phase["releaseBeforeDamage"]
        assert prep[0] == move_id and prep[1] == 0 and prep[2] != 0 and prep[3:] == [move_id, 60000]
        assert release[0] == move_id and release[1:3] == [0, 0] and release[3] == move_id
        assert vector["caseCount"] == len(vector["rolls"]) == 16 and all(v > 0 for v in vector["rolls"])
    special = doc["specialEngineEvidence"]
    counts = {kind: sum(row["kind"] == kind for row in special)
              for kind in ("COMPOSITION", "BOUNCE", "PHANTOM_PROTECT", "GRAVITY", "POWER_HERB", "MISS", "TYPE_IMMUNITY", "FAINT_REPLACEMENT")}
    assert counts == {"COMPOSITION": 176, "BOUNCE": 4, "PHANTOM_PROTECT": 1,
                      "GRAVITY": 2, "POWER_HERB": 1, "MISS": 1, "TYPE_IMMUNITY": 1, "FAINT_REPLACEMENT": 1}
    assert all(len([row for row in special if row["kind"] == "COMPOSITION" and row["values"][0] == mode]) == 16
               for mode in range(11))
    miss = next(row for row in special if row["kind"] == "MISS")
    immunity = next(row for row in special if row["kind"] == "TYPE_IMMUNITY")
    assert miss["values"] == immunity["values"] == [1, 1, 0, 60000]
    patches = doc["backend"]["slice15ObservationPatches"]
    names = [p["path"].split("/")[-1] for p in patches]
    assert names == ["0010-repeated-strike-observation.patch", "0011-slice15-phase-observation.patch", "0012-slice15-screen-observation.patch"]
    for item in patches:
        assert item["sha256"] == hashlib.sha256((Path(__file__).with_name("patches") / item["path"].split("/")[-1]).read_bytes()).hexdigest()
    print("Slice 15 artifact checks: 80 actual-move damage rolls, 176 composition rolls, 11 special executions, and five phase traces (no engine executed)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("run", "resume", "verify", "check", "emit"), default="run", nargs="?")
    parser.add_argument("--upstream-dir", type=Path, default=ROOT.parent / "upstream-hns/pokehns-expansion")
    parser.add_argument("--work-dir", type=Path, default=Path("/tmp/hns15-engine"))
    parser.add_argument("--toolchain-bin", type=Path, default=Path.home() / "opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin")
    parser.add_argument("--log", type=Path, default=Path("/tmp/hns15-engine.log"))
    parser.add_argument("--jobs", type=int, default=8)
    parser.add_argument("--order", choices=("canonical", "reversed"), default="canonical")
    args = parser.parse_args()
    if args.command == "emit":
        print(source(args.order))
        return
    if args.command == "check":
        check_artifact()
        return
    run(args)


if __name__ == "__main__":
    main()
