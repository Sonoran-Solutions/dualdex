#!/usr/bin/env python3
"""Additive original-engine repeated-strike traces; never rewrites the single-hit corpus."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
import oracle_backend as backend

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
PATCH = HERE / 'patches/0010-repeated-strike-observation.patch'
TARGET = HERE / 'repeated-strike-evidence.json'
HISTORICAL_SHA = '9f38413dd64ce0f73162f84db1f7f3cd7d84a6bf74bb38d09fb5b336947e1580'
MOVES = ['BONEMERANG', 'DOUBLE_HIT', 'DOUBLE_KICK', 'DUAL_CHOP', 'DUAL_WINGBEAT', 'TWIN_BEAM']

def scenarios():
    rows = []
    def add(name, move='DOUBLE_HIT', a='INSOMNIA', d='INSOMNIA', item='NONE', di='NONE',
            hp=60000, ahp=200, species='SNORLAX', r0=0, r1=0, c0=0, c1=0, trigger=1,
            sturdy=0, setup=0, beak=0, style=0):
        rows.append(dict(id=name, move=move, attackerAbility=a, defenderAbility=d, attackerItem=item,
            defenderItem=di, hp=hp, attackerHp=ahp, defenderSpecies=species,
            rolls=[r0,r1], crits=[c0,c1], trigger=trigger, sturdy=sturdy, setup=setup, beak=beak, style=style))
    for move in MOVES:
        for composition in ['neutral','technician','claws','fluffy','long-reach-fluffy','skill-link','loaded-dice','normalize','life-orb','crit']:
            for r in range(16):
                add(f'{move.lower()}-{composition}-{r}', move,
                    a={'technician':'TECHNICIAN','claws':'TOUGH_CLAWS','long-reach-fluffy':'LONG_REACH','skill-link':'SKILL_LINK','normalize':'NORMALIZE'}.get(composition,'INSOMNIA'),
                    d='FLUFFY' if 'fluffy' in composition else 'INSOMNIA',
                    item={'loaded-dice':'LOADED_DICE','life-orb':'LIFE_ORB'}.get(composition,'NONE'), r0=r, r1=r,
                    c0=int(composition=='crit'), c1=int(composition=='crit'))
    for name, move, a, item in [('contact','DOUBLE_HIT','INSOMNIA','NONE'),
            ('non-contact','BONEMERANG','INSOMNIA','NONE'),('special','TWIN_BEAM','INSOMNIA','NONE'),
            ('rounding','DOUBLE_KICK','TECHNICIAN','EXPERT_BELT')]:
        for r0 in range(16):
            for r1 in range(16): add(f'pairs-{name}-{r0}-{r1}',move,a=a,item=item,r0=r0,r1=r1)
        for c0,c1 in [(0,1),(1,0)]: add(f'mixed-{name}-{c0}-{c1}',move,a=a,item=item,c0=c0,c1=c1)
    for hp in [49,50,51,59,60,61,99,100,101,119,120,121]:
        for r0 in range(16):
            for r1 in range(16): add(f'early-{hp}-{r0}-{r1}','DOUBLE_KICK',hp=hp,r0=r0,r1=r1)
    for move in ['DUAL_CHOP','BONEMERANG']:
        for r in range(16): add('type-based-'+move.lower()+'-'+str(r),move,a='TECHNICIAN',style=1,r0=r,r1=r)
    for name, params in [
        ('poison-touch-marvel',dict(a='POISON_TOUCH',d='MARVEL_SCALE')),
        ('toxic-chain-marvel',dict(move='BONEMERANG',a='TOXIC_CHAIN',d='MARVEL_SCALE')),
        ('poison-touch-noncontact',dict(move='BONEMERANG',a='POISON_TOUCH',d='MARVEL_SCALE')),
        ('poison-touch-ineligible',dict(a='POISON_TOUCH',d='MARVEL_SCALE',species='METAGROSS')),
        ('toxic-chain-ineligible',dict(move='BONEMERANG',a='TOXIC_CHAIN',d='MARVEL_SCALE',species='METAGROSS')),
        ('poison-touch-no-trigger',dict(a='POISON_TOUCH',d='MARVEL_SCALE',trigger=0)),
        ('toxic-chain-no-trigger',dict(move='BONEMERANG',a='TOXIC_CHAIN',d='MARVEL_SCALE',trigger=0)),
        ('multiscale',dict(d='MULTISCALE')),('shadow-shield',dict(d='SHADOW_SHIELD')),
        ('resist-berry',dict(di='CHILAN_BERRY')),('sturdy-on',dict(d='STURDY',hp=20,sturdy=1)),
        ('sturdy-off',dict(d='STURDY',hp=20)),('focus-sash',dict(di='FOCUS_SASH',hp=20)),
        ('stamina',dict(d='STAMINA')),('weak-armor',dict(d='WEAK_ARMOR')),
        ('flame-body',dict(d='FLAME_BODY')),('effect-spore-sleep',dict(a='TECHNICIAN',d='EFFECT_SPORE')),
        ('rough-skin',dict(d='ROUGH_SKIN')),('rocky-helmet',dict(di='ROCKY_HELMET')),
        ('retaliation-ko',dict(d='ROUGH_SKIN',ahp=1)),
        ('life-orb',dict(item='LIFE_ORB')),('shell-bell',dict(item='SHELL_BELL',ahp=100)),
        ('kee-after-loop',dict(di='KEE_BERRY')),('maranga-after-loop',dict(move='TWIN_BEAM',di='MARANGA_BERRY')),
        ('beak-blast',dict(beak=1)),('beak-long-reach',dict(a='LONG_REACH',beak=1)),
        ('beak-protective-pads',dict(item='PROTECTIVE_PADS',beak=1)),
        ('screen-stage-item',dict(a='TECHNICIAN',item='LIFE_ORB',setup=1)),
        ('miss',dict(trigger=2)),
    ]: add(name,**params)
    return rows

PRELUDE = r'''#include "global.h"
#include "battle.h"
#include "battle_util.h"
#include "move.h"
#include "random.h"
#include "test/battle.h"
#include "constants/battle_move_effects.h"
extern bool32 (*gDdxrRng)(u32,u32,u32,u32 *);
extern void (*gDdxrBefore)(struct BattleContext *);
extern void (*gDdxrOperands)(struct BattleContext *,u32,u32);
extern void (*gDdxrCalculated)(struct BattleContext *,s32,s32);
extern void (*gDdxrHp)(enum BattlerId);
extern void (*gDdxrBetween)(void);
static u32 sCase,sMove,sHits,sDamageDraws,sCritDraws,sAccuracyDraws,sTrigger;
static u32 sRolls[2],sCrits[2],sBeforeHp[2],sRaw[2],sAdjusted[2],sApplied[2],sDefense[2];
static bool32 Selected(void) { return gBattlerAttacker==0 && gCurrentMove==sMove; }
static bool32 Rng(u32 tag,u32 lo,u32 hi,u32 *value) {
    if (gBattlerAttacker==1 && gCurrentMove==MOVE_BEAK_BLAST) {
        if (tag==RNG_DAMAGE_MODIFIER || tag==RNG_CRITICAL_HIT) { *value=0; return TRUE; }
    }
    if (!Selected()) return FALSE;
    if (tag==RNG_DAMAGE_MODIFIER) {
        EXPECT_LT(sDamageDraws,2); *value=sRolls[sDamageDraws++];
    } else if (tag==RNG_CRITICAL_HIT) {
        EXPECT_LT(sCritDraws,2); *value=sCrits[sCritDraws++];
    } else if (tag==RNG_ACCURACY) {
        EXPECT_EQ(sAccuracyDraws++,0); *value=sTrigger==2 ? 0 : 1;
    } else if (tag==RNG_POISON_TOUCH || tag==RNG_TOXIC_CHAIN || tag==RNG_FLAME_BODY) {
        *value=sTrigger==1;
    } else if (tag==RNG_SLEEP_TURNS) {
        *value=2;
    } else if (tag==RNG_EFFECT_SPORE) {
        *value=20;
    } else return FALSE;
    EXPECT_GE(*value,lo); EXPECT_LE(*value,hi);
    DebugPrintf("DDXR|%u|RNG|%u|%u|%u|%u\n",sCase,tag,*value,lo,hi);
    return TRUE;
}
static void Before(struct BattleContext *ctx) {
    if (!Selected()) return;
    EXPECT_LT(sHits,2); sBeforeHp[sHits]=gBattleMons[1].hp; sHits++;
    DebugPrintf("DDXR|%u|PRE|%u|%u|%u|%u|%lu|%lu|%u|%u|%u|%u|%u|%lu\n",sCase,sHits,gMultiHitCounter,
        gBattleMons[0].hp,gBattleMons[1].hp,gBattleMons[0].status1,gBattleMons[1].status1,
        ctx->abilityAtk,ctx->abilityDef,ctx->holdEffectAtk,ctx->holdEffectDef,gBattleWeather,gFieldStatuses);
}
static void Operands(struct BattleContext *ctx,u32 attack,u32 defense) {
    if (Selected()) DebugPrintf("DDXR|%u|STATE|%u|%u|%u|%u|%u|%u|%u|%u|%u|%u|%u|%u\n",sCase,sHits,
        gBattleMons[0].types[0],gBattleMons[0].types[1],gBattleMons[0].types[2],
        gBattleMons[1].types[0],gBattleMons[1].types[1],gBattleMons[1].types[2],
        gBattleMons[0].statStages[STAT_ATK],gBattleMons[0].statStages[STAT_SPATK],
        gBattleMons[1].statStages[STAT_DEF],gBattleMons[1].statStages[STAT_SPDEF],gBattleMons[1].statStages[STAT_SPEED]);
    if (!Selected()) return;
    sDefense[sHits-1]=defense;
    DebugPrintf("DDXR|%u|OPERANDS|%u|%u|%u|%u|%u|%u\n",sCase,sHits,attack,defense,
        gBattleMovePower,ctx->moveType,GetBattleMoveCategory(ctx->move));
}
static void Calculated(struct BattleContext *ctx,s32 raw,s32 adjusted) {
    if (!Selected()) return;
    sRaw[sHits-1]=raw; sAdjusted[sHits-1]=adjusted;
    DebugPrintf("DDXR|%u|CALC|%u|%ld|%ld|%u\n",sCase,sHits,raw,adjusted,ctx->isCrit);
}
static void Hp(enum BattlerId battler) {
    if (!Selected() || battler!=1) return;
    sApplied[sHits-1]=sBeforeHp[sHits-1]-gBattleMons[1].hp;
    DebugPrintf("DDXR|%u|HP|%u|%u|%u|%u\n",sCase,sHits,sBeforeHp[sHits-1],gBattleMons[1].hp,sApplied[sHits-1]);
}
static void Between(void) {
    if (!Selected()) return;
    u32 stop=0;
    if (sHits==0) stop=1; // observed miss/unaffected
    else if (gBattleMons[1].hp==0) stop=2;
    else if (gMultiHitCounter==1) stop=3; // this strike exhausts fixed nominal count
    else if (gBattleMons[0].hp==0) stop=4;
    else if (gBattleMons[0].status1 & STATUS1_SLEEP) stop=5;
    else if (gBattleMons[0].status1 & STATUS1_FREEZE) stop=6;
    if (stop) DebugPrintf("DDXR|%u|STOP|%u|%u\n",sCase,sHits,stop);
    DebugPrintf("DDXR|%u|BETWEEN|%u|%u|%u|%u|%lu|%lu|%u|%u|%u|%u|%u|%lu|%u|%u\n",sCase,sHits,gMultiHitCounter,
        gBattleMons[0].hp,gBattleMons[1].hp,gBattleMons[0].status1,gBattleMons[1].status1,
        GetBattlerAbility(0),GetBattlerAbility(1),GetBattlerHoldEffect(0),GetBattlerHoldEffect(1),
        gBattleWeather,gFieldStatuses,gBattleMons[1].statStages[STAT_DEF],gBattleMons[0].statStages[STAT_ATK]);
}
'''

def source(rows):
    params=[]
    for i,s in enumerate(rows):
        params.append('    PARAMETRIZE { '+f'caseId={i}; move=MOVE_{s["move"]}; a=ABILITY_{s["attackerAbility"]}; d=ABILITY_{s["defenderAbility"]}; '+
            f'item=ITEM_{s["attackerItem"]}; di=ITEM_{s["defenderItem"]}; hp={s["hp"]}; ahp={s["attackerHp"]}; species=SPECIES_{s["defenderSpecies"]}; '+
            f'r0={s["rolls"][0]}; r1={s["rolls"][1]}; c0={s["crits"][0]}; c1={s["crits"][1]}; trigger={s["trigger"]}; sturdy={s["sturdy"]}; setup={s["setup"]}; beak={s["beak"]}; style={s["style"]}; }}')
    tail = r'''
    GIVEN {
        PLAYER(SPECIES_MACHAMP) { Ability(a); Item(item); Attack(151); SpAttack(style ? 211 : 151); HP(ahp); MaxHP(200); Level(50); Speed(200); }
        PLAYER(SPECIES_MACHAMP) { Ability(ABILITY_INSOMNIA); HP(200); MaxHP(200); Speed(90); }
        OPPONENT(species) { Ability(d); Item(di); Defense(109); SpDefense(style ? 131 : 109); HP(hp); MaxHP(hp); Level(50); Speed(100); }
        gSaveBlock3Ptr->challengeSettings.tx_Mode_Sturdy=sturdy;
        gSaveBlock3Ptr->challengeSettings.optionStyle=style;
        gSaveBlock3Ptr->challengeSettings.tx_Mode_Fairy_Types=1;
        memset(sApplied,0,sizeof(sApplied)); memset(sBeforeHp,0,sizeof(sBeforeHp));
        sCase=caseId; sMove=move; sHits=0; sDamageDraws=0; sCritDraws=0; sAccuracyDraws=0;
        sRolls[0]=r0; sRolls[1]=r1; sCrits[0]=c0; sCrits[1]=c1; sTrigger=trigger;
        gDdxrRng=Rng; gDdxrBefore=Before; gDdxrOperands=Operands; gDdxrCalculated=Calculated; gDdxrHp=Hp; gDdxrBetween=Between;
    } WHEN {
        if (setup) TURN { MOVE(player,MOVE_SWORDS_DANCE); MOVE(opponent,MOVE_REFLECT); }
        TURN { MOVE(player,move); if (beak) MOVE(opponent,MOVE_BEAK_BLAST,criticalHit:FALSE); if (ahp==1) SEND_OUT(player,1); }
    } THEN {
        if (trigger==2) { EXPECT_EQ(sHits,0); EXPECT_EQ(sDamageDraws,0); EXPECT_EQ(sCritDraws,0); }
        else { EXPECT_GE(sHits,1); EXPECT_EQ(sHits,sDamageDraws); EXPECT_EQ(sHits,sCritDraws); }
        EXPECT_EQ(sAccuracyDraws,GetMoveAccuracy(move)==100 ? 0 : 1);
        EXPECT_EQ(hp-(sHits ? sBeforeHp[sHits-1]-sApplied[sHits-1] : hp),sApplied[0]+(sHits>1 ? sApplied[1] : 0));
        DebugPrintf("DDXR|%u|FINAL|%u|%u|%u|%u|%u|%u|%u|%u|%u\n",sCase,sHits,sDamageDraws,sCritDraws,sAccuracyDraws,gBattleMons[0].hp,(sHits ? sBeforeHp[sHits-1]-sApplied[sHits-1] : hp),gBattleMons[1].hp,gBattleMons[1].statStages[STAT_DEF],gBattleMons[1].statStages[STAT_SPDEF]);
        gDdxrRng=NULL; gDdxrBefore=NULL; gDdxrOperands=NULL; gDdxrCalculated=NULL; gDdxrHp=NULL; gDdxrBetween=NULL;
    }
}
'''
    return PRELUDE+'\n'.join(('SINGLE_BATTLE_TEST("DDXO repeated-strike %u") {\n    u32 caseId,move,a,d,item,di,hp,ahp,species,r0,r1,c0,c1,trigger,sturdy,setup,beak,style;\n' % (offset//640))+'\n'.join(params[offset:offset+640])+tail for offset in range(0,len(params),640))


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def check(doc,rows,generated):
    assert sha(HERE/'corpus.json') == HISTORICAL_SHA, 'Historical corpus changed'
    assert doc['pinnedCommit']==backend.HNS_PINNED_COMMIT
    assert doc['historicalCorpusSha256']==HISTORICAL_SHA
    historical=json.loads((HERE/'corpus.json').read_text())['provenance']
    assert doc['toolchain']==historical['toolchain']
    assert doc['backend']==historical['backend']
    assert doc['backend']['harnessPatches']==[dict(path='patches/'+p,sha256=sha(backend.PATCH_DIR/p)) for p in backend.HARNESS_PATCHES]
    assert doc['sourceSha256']==hashlib.sha256(generated.encode()).hexdigest()
    assert doc['patchSha256']==sha(PATCH)
    assert len(doc['cases'])==len(rows)
    for row,case in zip(rows,doc['cases']):
        assert case['scenario']==row
        assert case['pass'] is True
        events=case['events']
        widths={'RNG':4,'PRE':12,'STATE':12,'OPERANDS':6,'CALC':4,'HP':4,'STOP':2,'BETWEEN':14,'FINAL':9}
        assert all(e['kind'] in widths and len(e['values'])==widths[e['kind']] and
                   all(type(v) is int and v>=0 for v in e['values']) for e in events)
        assert all(v[2]<=v[1]<=v[3] for v in (e['values'] for e in events if e['kind']=='RNG'))
        final=[e['values'] for e in events if e['kind']=='FINAL']
        assert len(final)==1
        hits,damage,crit,accuracy,_,_,_=final[0][:7]
        assert 0<=hits<=2 and hits==damage==crit and accuracy==(0 if row["move"] in ("DOUBLE_KICK","TWIN_BEAM") else 1)
        calc=[e['values'] for e in events if e['kind']=='CALC']
        hp=[e['values'] for e in events if e['kind']=='HP']
        assert len(calc)==len(hp)==hits
        operands=[e['values'] for e in events if e['kind']=='OPERANDS']
        expected_category=1 if row['move']=='TWIN_BEAM' or row['style']==1 and row['move']=='DUAL_CHOP' else 0
        assert all(v[5]==expected_category for v in operands), 'effective category authority'
        assert all(v[4]==(1 if row['attackerAbility']=='NORMALIZE' else {'BONEMERANG':5,'DOUBLE_HIT':1,'DOUBLE_KICK':2,'DUAL_CHOP':17,'DUAL_WINGBEAT':3,'TWIN_BEAM':15}[row['move']]) for v in operands), 'effective type authority'
        for c,h in zip(calc,hp):
            assert h[3]==min(h[1],c[2]) and h[1]-h[2]==h[3]
        pre=[e['values'] for e in events if e['kind']=='PRE']
        rng=[e['values'] for e in events if e['kind']=='RNG']
        assert [r[1] for r in rng if r[0]==7]==row['rolls'][:hits], 'damage queue order/exhaustion'
        assert [r[1] for r in rng if r[0]==4]==row['crits'][:hits], 'critical queue order/exhaustion'
        assert [c[3] for c in calc]==row['crits'][:hits]
        assert [p[0] for p in pre]==list(range(1,hits+1))
        assert [p[1] for p in pre]==[2,1][:hits]
        assert len([e for e in events if e['kind']=='STOP'])==1
        for i in range(hits):
            assert pre[i][3]>0
            if i: assert pre[i][3]==hp[i-1][2], 'target HP chronology'
            start=next(j for j,e in enumerate(events) if e['kind']=='PRE' and e['values'][0]==i+1)
            segment=events[start:]
            positions=[next(j for j,e in enumerate(segment) if e['kind']==kind) for kind in ('PRE','OPERANDS','CALC','HP','BETWEEN')]
            assert positions==sorted(positions), 'execution chronology'
        if row['id'].startswith('pairs-'):
            assert hits==2
            assert calc[0][1]==calc[1][1] or row['rolls'][0]!=row['rolls'][1]
    for name in ['contact','non-contact','special','rounding']:
        selected=[c for c in doc['cases'] if c['scenario']['id'].startswith('pairs-'+name+'-')]
        assert len(selected)==256 and len({tuple(c['scenario']['rolls']) for c in selected})==256
    positive=['poison-touch-marvel','toxic-chain-marvel']
    for name in positive:
        events=next(c['events'] for c in doc['cases'] if c['scenario']['id']==name)
        pre=[e['values'] for e in events if e['kind']=='PRE']
        operands=[e['values'] for e in events if e['kind']=='OPERANDS']
        assert len(pre)==2 and pre[0][5]==0 and pre[1][5]!=0
        assert operands[1][2]>operands[0][2]
        calculated=[e['values'][1] for e in events if e['kind']=='CALC']
        assert calculated[1]<calculated[0] and sum(calculated)!=2*calculated[0]
    for name in ['poison-touch-noncontact','poison-touch-ineligible','toxic-chain-ineligible','poison-touch-no-trigger','toxic-chain-no-trigger']:
        events=next(c['events'] for c in doc['cases'] if c['scenario']['id']==name)
        assert all(e['values'][5]==0 for e in events if e['kind']=='PRE')
        defenses=[e['values'][2] for e in events if e['kind']=='OPERANDS']
        assert len(defenses)==2 and defenses[0]==defenses[1]
    by_id={c['scenario']['id']:c for c in doc['cases']}
    def values(name,kind): return [e['values'] for e in by_id[name]['events'] if e['kind']==kind]
    for move in MOVES:
        def vector(composition): return [values(move.lower()+'-'+composition+'-'+str(r),'CALC')[0][1] for r in range(16)]
        neutral=vector('neutral')
        assert vector('technician')[-1]>neutral[-1]
        assert vector('long-reach-fluffy')==neutral
        assert vector('skill-link')==vector('loaded-dice')==neutral
        if move in ('BONEMERANG','TWIN_BEAM'): assert vector('claws')==vector('fluffy')==neutral
        else: assert vector('claws')[-1]>neutral[-1] and vector('fluffy')[-1]<neutral[-1]
    for name in ('contact','non-contact','special','rounding'):
        group=[c for c in doc['cases'] if c['scenario']['id'].startswith('pairs-'+name+'-')]
        first=[next(e['values'][1] for e in c['events'] if e['kind']=='CALC') for c in group]
        loss=[sum(e['values'][3] for e in c['events'] if e['kind']=='HP') for c in group]
        assert min(loss)==2*min(first) and max(loss)==2*max(first)
    early=[c for c in doc['cases'] if c['scenario']['id'].startswith('early-')]
    for target in sorted({c['scenario']['hp'] for c in early}):
        group=[c for c in early if c['scenario']['hp']==target]
        first=[next(e['values'][1] for e in c['events'] if e['kind']=='CALC') for c in group]
        loss=[sum(e['values'][3] for e in c['events'] if e['kind']=='HP') for c in group]
        count=[next(e['values'][0] for e in c['events'] if e['kind']=='FINAL') for c in group]
        lo,hi=min(first),max(first)
        assert (min(loss),max(loss))==(min(target,2*lo),min(target,2*hi))
        assert (min(count),max(count))==(1 if target<=hi else 2,1 if target<=lo else 2)
    for name in ('life-orb','shell-bell'):
        initial=by_id[name]['scenario']['attackerHp']
        assert all(v[2]==initial for v in values(name,'BETWEEN'))
        assert values(name,'FINAL')[0][4]!=initial
    for name,index in [('kee-after-loop',7),('maranga-after-loop',8)]:
        defense=[v[2] for v in values(name,'OPERANDS')]
        assert defense[0]==defense[1] and values(name,'FINAL')[0][index]==7
    for name in ('multiscale','shadow-shield','resist-berry'):
        damage=[v[1] for v in values(name,'CALC')]; assert damage[1]>damage[0]
    for name in ('sturdy-on','focus-sash'): assert values(name,'CALC')[0][2]==19 and values(name,'FINAL')[0][0]==2
    assert values('sturdy-off','FINAL')[0][0]==1
    assert values('stamina','OPERANDS')[1][2]>values('stamina','OPERANDS')[0][2]
    assert values('weak-armor','OPERANDS')[1][2]<values('weak-armor','OPERANDS')[0][2]
    for name in ('flame-body','beak-blast'): assert values(name,'PRE')[1][4]&16 and values(name,'CALC')[1][1]<values(name,'CALC')[0][1]
    for name in ('beak-long-reach','beak-protective-pads'): assert all(v[4]==0 for v in values(name,'PRE'))
    assert values('effect-spore-sleep','FINAL')[0][0]==1 and values('effect-spore-sleep','STOP')[0][1]==5
    for name in ('rough-skin','rocky-helmet'): assert values(name,'BETWEEN')[0][2]<200
    assert values('retaliation-ko','FINAL')[0][0]==1 and values('retaliation-ko','STOP')[0][1]==4
    assert values('miss','FINAL')[0][0]==0 and values('miss','STOP')[0][1]==1
    print(f'Repeated-strike evidence: {len(rows)} original-engine cases; four complete 256-pair grids; historical SHA unchanged')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command',choices=['generate','verify','check','emit-sources','record'])
    p.add_argument('--upstream-dir',type=Path,default=ROOT.parent/'upstream-hns/pokehns-expansion')
    p.add_argument('--work-dir',type=Path,default=Path('/tmp/hns12-engine'))
    p.add_argument('--toolchain-bin',type=Path,default=Path.home()/'opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin')
    p.add_argument('--log',type=Path,default=Path('/tmp/hns12-engine.log'))
    p.add_argument('--order',choices=['canonical','reversed'],default='canonical')
    p.add_argument('--jobs',type=int,default=8)
    args=p.parse_args(); rows=scenarios(); generated=source(rows)
    if args.command=='emit-sources': args.log.write_text(generated); return
    if args.command=='check': check(json.loads(TARGET.read_text()),rows,generated); return
    backend.verify_upstream(args.upstream_dir)
    if args.command != "record":
        backend.export_worktree(args.upstream_dir,args.work_dir)
        subprocess.run(['patch','-p1','--forward','--batch','-i',str(PATCH)],cwd=args.work_dir,check=True)
    sources=backend.render_sources([])
    sources['test/dualdex_oracle/repeated_strike.c']=source(list(reversed(rows))) if args.order=='reversed' else generated
    if args.command == 'record':
        # Recovery after a completed runner and failed postprocessing: no calculated values
        # are supplied by the caller; the exact emitted source and all runner rows are checked.
        assert (args.work_dir/'test/dualdex_oracle/repeated_strike.c').read_text()==sources['test/dualdex_oracle/repeated_strike.c']
        log=args.log.read_text()
        for name in backend.EXECUTION_NAMES:
            assert any(m and m.group('name')==backend.TEST_PREFIX+name and m.group('result')=='PASS'
                       for m in (backend.RESULT_RE.match(line.strip()) for line in backend.ANSI_RE.sub('',log).splitlines()))
    else:
        log=backend.build_and_run(args.work_dir,args.toolchain_bin,sources,args.jobs)
    args.log.write_text(log)
    events={i:[] for i in range(len(rows))}; passes=0
    for line in backend.ANSI_RE.sub('',log).splitlines():
        m=re.search(r'DDXR\|(\d+)\|(\w+)\|([\d|]+)',line)
        if m: events[int(m[1])].append(dict(kind=m[2],values=[int(v) for v in m[3].split('|')]))
        status=backend.RESULT_RE.match(line.strip())
        if status and status.group('name').startswith('DDXO repeated-strike '):
            assert status.group('result')=='PASS',line
            passes+=1
    assert passes==(len(rows)+639)//640,(passes,(len(rows)+639)//640)
    ordered=list(reversed(rows)) if args.order=='reversed' else rows
    by_id={r['id']:dict(scenario=r,pass_=True,events=events[i]) for i,r in enumerate(ordered)}
    cases=[]
    for r in rows:
        c=by_id[r['id']]; c['pass']=c.pop('pass_'); cases.append(c)
    doc=dict(schemaVersion=1,pinnedCommit=backend.HNS_PINNED_COMMIT,historicalCorpusSha256=HISTORICAL_SHA,
        sourceSha256=hashlib.sha256(generated.encode()).hexdigest(),patchSha256=sha(PATCH),
        toolchain=backend.toolchain_identity(args.toolchain_bin),backend=backend.backend_provenance(args.work_dir),cases=cases)
    check(doc,rows,generated)
    text=json.dumps(doc,sort_keys=True,separators=(',',':'))+'\n'
    if args.command=='verify': assert TARGET.read_text()==text,'Reversed engine traces differ'
    else: TARGET.write_text(text)

if __name__=='__main__': main()
