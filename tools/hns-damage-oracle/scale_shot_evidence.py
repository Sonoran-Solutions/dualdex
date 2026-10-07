#!/usr/bin/env python3
"""Additive original-engine traces for Scale Shot count, damage, and completion timing."""
import argparse
import copy
import hashlib
import json
import re
import subprocess
from pathlib import Path

import oracle_backend as backend
import variable_multi_hit_evidence as base

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
PATCH = HERE / "patches/0012-scale-shot-completion-observation.patch"
TARGET = HERE / "scale-shot-evidence.json"
RNG_ACCURACY_TAG=base.RNG_ACCURACY_TAG
RNG_CRITICAL_TAG=base.RNG_CRITICAL_TAG
RNG_DAMAGE_TAG=base.RNG_DAMAGE_TAG
RNG_HITS_TAG=base.RNG_HITS_TAG
RNG_LOADED_DICE_TAG=base.RNG_LOADED_DICE_TAG
HISTORICAL_SHA="9f38413dd64ce0f73162f84db1f7f3cd7d84a6bf74bb38d09fb5b336947e1580"
SLICE12_SHA="4b5fa96b4ecfd77b45cfa6c3e196c13fca22b4fa0c87e7cba9c62597b89d3b8a"
SLICE13_SHA="9f5e357ea88bb9baed43b7f3befc879748a745b629ac309370f8c528634b1fdb"


def scenarios():
    original = {r["id"]: r for r in base.scenarios()}
    rows = []
    def add(source_id, new_id, **changes):
        row=copy.deepcopy(original[source_id]); row["id"]=new_id; row["move"]="SCALE_SHOT"; row.update(changes)
        row.setdefault("attackerHp",200); row.setdefault("reserve",False); rows.append(row)
    for n in range(2,6):
        for pattern in ("low","high","low-high","high-low","mixed"):
            add(f"bullet_seed-{n}-{pattern}",f"scale-shot-{n}-{pattern}")
        add(f"technician-bullet_seed-{n}",f"scale-shot-technician-{n}")
        for roll in range(16):
            add(f"first-roll-{n}-{roll}",f"scale-shot-first-roll-{n}-{roll}")
    add("bullet_seed-2-low", "scale-shot-all-critical", nominalCount=5,
        rolls=[0]*5, crits=[1]*5)
    add("skill-link","scale-shot-skill-link")
    add("loaded-dice-four","scale-shot-loaded-dice-four")
    add("loaded-dice-five","scale-shot-loaded-dice-five")
    add("skill-link-loaded-dice","scale-shot-skill-link-loaded-dice")
    add("skill-link-gastro-acid","scale-shot-skill-link-gastro-acid")
    add("skill-link-neutralizing-gas","scale-shot-skill-link-neutralizing-gas")
    add("skill-link-gas-ability-shield","scale-shot-skill-link-gas-ability-shield")
    add("loaded-dice-magic-room","scale-shot-loaded-dice-magic-room")
    add("loaded-dice-embargo","scale-shot-loaded-dice-embargo")
    add("loaded-dice-klutz","scale-shot-loaded-dice-klutz")
    add("rounding-technician-punching-life-orb","scale-shot-technician-life-orb",attackerItem="LIFE_ORB")
    for count in range(2,6):
        for hit in range(1,count+1):
            # Exact source damage endpoints are re-measured from the engine trace.
            add("bullet_seed-2-low",f"scale-shot-early-{count}-{hit}",nominalCount=count,
                rolls=[0]*count,crits=[0]*count,targetHp=max(1,hit*14))
    add("initial-miss","scale-shot-initial-miss",move="SCALE_SHOT")
    add("bullet-seed-bulletproof","scale-shot-source-immunity",defenderAbility="WONDER_GUARD",
        defenderSpecies="SPECIES_CLEFABLE",sourceImmune=True)
    for row in rows:
        row.setdefault("attackerHp",200); row.setdefault("reserve",False)
    return rows


def source(rows):
    generated=base.source(rows)
    generated=generated.replace('EXPECT_EQ(sAccuracyDraws,GetMoveAccuracy(move)==100 ? 0 : 1);',
        'EXPECT_EQ(sAccuracyDraws,(GetMoveAccuracy(move)==100 || noDamage) ? 0 : 1);')
    def add_parameters(match):
        index=int(match.group(1));row=rows[index]
        return match.group(0)+f' attackerHp={row.get("attackerHp",200)}; reserve={int(row.get("reserve",False))};'
    generated=re.sub(r'caseId=(\d+);(?=[^\n}]* count=\d+;)',add_parameters,generated)
    def add_species(match):
        index=int(match.group(1));return match.group(0)+f' species={rows[index].get("defenderSpecies","SPECIES_SNORLAX")};'
    generated=re.sub(r'caseId=(\d+);(?=[^\n}]* count=\d+;)',add_species,generated)
    generated=generated.replace('extern void (*gDdxrBetween)(void);',
        'extern void (*gDdxrBetween)(void);\nvoid (*gDdxrScaleShotComplete)(void);')
    generated=generated.replace('static u32 sRolls[5],sCrits[5],sBeforeHp[5],sApplied[5];',
        'static u32 sRolls[5],sCrits[5],sBeforeHp[5],sApplied[5];\n'
        'static void ScaleShotComplete(void) { DebugPrintf("DDXV|%u|SCALE_SHOT_COMPLETE|%u|%u|%u\\n",sCase,sHits,gBattleMons[0].statStages[STAT_DEF],gBattleMons[0].statStages[STAT_SPEED]); }')
    generated=generated.replace('u32 caseId,move,a,d,item,di,hp,count,mode,setup,miss,noDamage,r0',
        'u32 caseId,move,a,d,item,di,hp,count,attackerHp,reserve,species,mode,setup,miss,noDamage,r0')
    generated=generated.replace('HP(200); MaxHP(200); Level(50); Speed(200); }',
        'HP(attackerHp); MaxHP(200); Level(50); Speed(200); }')
    generated=generated.replace('OPPONENT(SPECIES_SNORLAX) { Ability(d); Item(di); Defense(109); SpDefense(109); HP(hp); MaxHP(hp); Level(50); Speed(100); }',
        'OPPONENT(species) { Ability(d); Item(di); Defense(109); SpDefense(109); HP(hp); MaxHP(hp); Level(50); Speed(100); }\n'
        '        if (reserve) OPPONENT(SPECIES_PIKACHU) { Attack(100); Defense(100); SpAttack(100); SpDefense(100); Speed(100); HP(200); MaxHP(200); Level(50); Moves(MOVE_TACKLE, MOVE_CELEBRATE); }\n'
        '')
    generated=generated.replace('gDdxrRng=Rng; gDdxrCountSelected=CountSelected;',
        'gDdxrScaleShotComplete=ScaleShotComplete; gDdxrRng=Rng; gDdxrCountSelected=CountSelected;')
    generated=generated.replace('gDdxrRng=NULL; gDdxrCountSelected=NULL;',
        'gDdxrScaleShotComplete=NULL; gDdxrRng=NULL; gDdxrCountSelected=NULL;')
    generated=generated.replace('DebugPrintf("DDXV|%u|FINAL|%u|%u|%u|%u|%u|%u|%u|%u\\n",sCase,sHits,sDamageDraws,sCritDraws,sAccuracyDraws,',
        'DebugPrintf("DDXV|%u|SCALE_SHOT_FINAL|%u|%u|%u\\n",sCase,gBattleMons[0].statStages[STAT_DEF],gBattleMons[0].statStages[STAT_SPEED],gBattleMons[1].hp);\n'
        '        DebugPrintf("DDXV|%u|FINAL|%u|%u|%u|%u|%u|%u|%u|%u\\n",sCase,sHits,sDamageDraws,sCritDraws,sAccuracyDraws,')
    return generated


def check(doc, rows, generated):
    assert hashlib.sha256((HERE/"corpus.json").read_bytes()).hexdigest()==HISTORICAL_SHA
    assert hashlib.sha256((HERE/"repeated-strike-evidence.json").read_bytes()).hexdigest()==SLICE12_SHA
    assert hashlib.sha256((HERE/"variable-multihit-evidence.json").read_bytes()).hexdigest()==SLICE13_SHA
    assert len(doc["cases"]) == len(rows)
    assert doc["pinnedCommit"] == backend.HNS_PINNED_COMMIT
    assert doc["historicalCorpusSha256"]==HISTORICAL_SHA and doc["slice12EvidenceSha256"]==SLICE12_SHA and doc["slice13EvidenceSha256"]==SLICE13_SHA
    assert doc["sourceSha256"] == hashlib.sha256(generated.encode()).hexdigest()
    assert doc["completionPatchSha256"] == hashlib.sha256(PATCH.read_bytes()).hexdigest()
    by_id={c["scenario"]["id"]:c for c in doc["cases"]}
    assert len(by_id)==len(rows)
    for row in rows:
        case=by_id[row["id"]]; assert case["scenario"]==row and case["pass"] is True
        events=case["events"]
        finals=[i for i,e in enumerate(events) if e["kind"]=="FINAL"]
        after_indexes=[i for i,e in enumerate(events) if e["kind"]=="SCALE_SHOT_FINAL"]
        assert len(finals)==len(after_indexes)==1 and after_indexes[0]<finals[0]
        complete=[e for e in events if e["kind"]=="SCALE_SHOT_COMPLETE"]
        assert len(complete)<=1
        after=[e for e in events if e["kind"]=="SCALE_SHOT_FINAL"]
        assert len(after)==1
        if row["initialMiss"] or row["sourceImmune"]:
            assert not complete
        elif row["targetHp"]==60000:
            assert len(complete)==1
        if complete:
            assert complete[0]["values"][0] >= 1
            assert events.index(complete[0]) > max(i for i,e in enumerate(events) if e["kind"] in ("HP","BETWEEN","STOP"))
            # Completion invocation records the stages before the script; final records after it.
            assert complete[0]["values"][1:] != after[0]["values"][:2]
        between = [e for e in events if e["kind"] == "BETWEEN"]
        assert all(e["values"][-2:] == [6, 6] for e in between)
        selected=[e for e in events if e["kind"]=="COUNT"]
        if row["sourceImmune"]: assert not selected
        else:
            assert len(selected)==1 and selected[0]["values"][3]==row["nominalCount"]
            tag=RNG_HITS_TAG if row["mode"]=="ORDINARY" else RNG_LOADED_DICE_TAG if row["mode"]=="LOADED_DICE" else None
            count_draws=[e for e in events if e["kind"]=="RNG" and e["values"][0] in (RNG_HITS_TAG,RNG_LOADED_DICE_TAG)]
            assert [e["values"][0] for e in count_draws]==([] if tag is None else [tag])
            assert not [e for e in events if e["kind"]=="RNG" and e["values"][0]==RNG_ACCURACY_TAG] if row["sourceImmune"] else True
    assert {r["nominalCount"] for r in rows if r["id"].startswith("scale-shot-")} >= {2,3,4,5}


def main():
    p=argparse.ArgumentParser();p.add_argument("command",choices=["generate","verify","check","emit-sources","record"])
    p.add_argument("--upstream-dir",type=Path,default=ROOT.parent/"upstream-hns/pokehns-expansion")
    p.add_argument("--work-dir",type=Path,default=Path("/tmp/hns14-engine"));p.add_argument("--toolchain-bin",type=Path,default=Path.home()/"opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin")
    p.add_argument("--log",type=Path,default=Path("/tmp/hns14-engine.log"));p.add_argument("--order",choices=["canonical","reversed"],default="canonical");p.add_argument("--jobs",type=int,default=8)
    a=p.parse_args();rows=scenarios();generated=source(rows)
    if a.command=="emit-sources": a.log.write_text(generated);return
    if a.command=="check": check(json.loads(TARGET.read_text()),rows,generated);return
    backend.verify_upstream(a.upstream_dir)
    if a.command!="record":
        backend.export_worktree(a.upstream_dir,a.work_dir)
        subprocess.run(["patch","-p1","--forward","--batch","-i",str(base.FIXED_PATCH)],cwd=a.work_dir,check=True)
        backend._apply_patch(a.work_dir,base.COUNT_PATCH);backend._apply_patch(a.work_dir,PATCH)
    ordered=list(reversed(rows)) if a.order=="reversed" else rows
    sources=backend.render_sources([]);sources["test/dualdex_oracle/scale_shot.c"]=source(ordered)
    if a.command=="record":
        assert (a.work_dir/"test/dualdex_oracle/scale_shot.c").read_text()==sources["test/dualdex_oracle/scale_shot.c"];log=a.log.read_text()
    else: log=backend.build_and_run(a.work_dir,a.toolchain_bin,sources,a.jobs)
    a.log.write_text(log);events={i:[] for i in range(len(rows))};passes=0
    for line in backend.ANSI_RE.sub("",log).splitlines():
        m=re.search(r"DDXV\|(\d+)\|(\w+)\|([\d|]+)",line)
        if m: events[int(m[1])].append(dict(kind=m[2],values=[int(v) for v in m[3].split("|")]))
        status=backend.RESULT_RE.match(line.strip())
        if status and status.group("name").startswith("DDXO variable-multihit "):
            assert status.group("result")=="PASS",line;passes+=1
    assert passes==(len(rows)+599)//600,(passes,len(rows))
    ids=[r["id"] for r in ordered];by_id={ids[i]:events[i] for i in range(len(rows))}
    doc=dict(schemaVersion=1,pinnedCommit=backend.HNS_PINNED_COMMIT,historicalCorpusSha256=HISTORICAL_SHA,
        slice12EvidenceSha256=SLICE12_SHA,slice13EvidenceSha256=SLICE13_SHA,sourceSha256=hashlib.sha256(generated.encode()).hexdigest(),
        completionPatchSha256=hashlib.sha256(PATCH.read_bytes()).hexdigest(),toolchain=backend.toolchain_identity(a.toolchain_bin),
        backend=backend.backend_provenance(a.work_dir),cases=[dict(scenario=r,**{"pass":True},events=by_id[r["id"]]) for r in rows])
    check(doc,rows,generated);encoded=json.dumps(doc,sort_keys=True,separators=(",",":"))+"\n"
    if a.command=="verify": assert TARGET.read_text()==encoded,"Scale Shot evidence differs"
    else: TARGET.write_text(encoded)
    print(f"Scale Shot evidence: {len(rows)} source-engine cases; completion event and post-sequence stage state recorded")

if __name__=="__main__": main()
