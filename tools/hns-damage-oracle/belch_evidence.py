#!/usr/bin/env python3
"""Original-engine Belch evidence for Slice 16 (pinned H&S Release-v2.0.5 battle test runner).

Every scenario is a real gameplay sequence on the pinned engine. Berry consumption is never set
directly: `ateBerry` becomes true only through Stuff Cheeks (`BS_ConsumeBerry`) or stays false
because nothing consumed a Berry. The harness reads `gBattleStruct->partyState` inside the test
after the battle, it does not write it.

Lifecycle scenarios (ordered, one battle each):
  * fresh battle with an unconsumed held Berry: Belch selection refused (`allowed: FALSE`);
  * Berry consumed through Stuff Cheeks: Belch executes;
  * switch to a second Belch user: that member stays ineligible; switch back: eligibility persists;
  * a second fresh battle after the consuming battle: eligibility does not carry over;
  * Recycle restores the consumed Berry: the flag stays true and Belch executes.

Damage scenarios: each is one battle per roll (sixteen `PARAMETRIZE` runs). Berry consumption is
the real Stuff Cheeks turn, and the measured Belch hit uses `WITH_RNG(RNG_DAMAGE_MODIFIER, i)`
with the stated critical flag. Each run prints the observed state and the HP-bar damage.

Results are written to `belch-evidence.json` only after every assertion passes. The reversed
order run emits the damage scenarios in the opposite order; its artifact must match the canonical
one exactly, which checks order independence.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

import oracle_backend as backend

ROOT = Path(__file__).resolve().parents[2]
TARGET = Path(__file__).with_name("belch-evidence.json")
ROLLS = tuple(range(16))
TEST_FILE = "test/dualdex_oracle/belch_evidence.c"

# Damage vectors. Attacker and defender stats are the stats the engine computed before the hit.
DAMAGE = [
    dict(id="neutral", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", spAttack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", spDefense=100), crit=False),
    dict(id="critical", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", spAttack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", spDefense=100), crit=True),
    dict(id="stab", attacker=dict(species="SPECIES_MUK", ability="ABILITY_INSOMNIA", spAttack=113),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", spDefense=97), crit=False),
    dict(id="super-effective", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", spAttack=100),
         defender=dict(species="SPECIES_BELLOSSOM", ability="ABILITY_INSOMNIA", spDefense=101), crit=False),
    dict(id="not-very-effective", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", spAttack=100),
         defender=dict(species="SPECIES_KOFFING", ability="ABILITY_INSOMNIA", spDefense=101), crit=False),
    dict(id="immune", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", spAttack=100),
         defender=dict(species="SPECIES_STEELIX", ability="ABILITY_INSOMNIA", spDefense=101), crit=False, immune=True),
    dict(id="filter-ability", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", spAttack=100),
         defender=dict(species="SPECIES_BELLOSSOM", ability="ABILITY_FILTER", spDefense=101), crit=False),
    dict(id="composition-rounding", attacker=dict(species="SPECIES_MUK", ability="ABILITY_INSOMNIA", spAttack=137),
         defender=dict(species="SPECIES_BELLOSSOM", ability="ABILITY_FILTER", spDefense=89), crit=True),
]

# The engine's Belch selection is refused for the fresh battle and every party member that never ate a Berry.
LIFECYCLE = ["fresh-refused", "consumed-executes", "switch-slot0",
             "second-battle-fresh-refused", "recycle-keeps-flag"]


def _damage_test(v, order_index):
    a, d = v["attacker"], v["defender"]
    crit = "TRUE" if v["crit"] else "FALSE"
    # Zero damage is a successful Belch use (its animation plays) with no HP change, never a refusal.
    if v.get("immune"):
        # The engine does not play a Belch animation against an immune target. Belch is proven used by
        # gLastMoves and by its immunity result flag, and the defender's HP must not change.
        scene = ['ANIMATION(ANIM_TYPE_MOVE, MOVE_STUFF_CHEEKS, player);',
                 'NONE_OF { HP_BAR(opponent); }']
        damage_expr = "0"
    else:
        scene = ['ANIMATION(ANIM_TYPE_MOVE, MOVE_STUFF_CHEEKS, player);',
                 'ANIMATION(ANIM_TYPE_MOVE, MOVE_BELCH, player);',
                 'HP_BAR(opponent, captureDamage: &results[i].damage);']
        damage_expr = "results[i].damage"
    lines = [
        f'SINGLE_BATTLE_TEST("DDXO belch-damage-{v["id"]}", s16 damage)',
        "{",
        "    " + " ".join(["PARAMETRIZE { }"] * len(ROLLS)),
        "    GIVEN {",
        f"        PLAYER({a['species']}) {{ Ability({a['ability']}); Item(ITEM_ORAN_BERRY); Level(50); "
        f"SpAttack({a['spAttack']}); HP(60000); MaxHP(60000); Speed(40); "
        f"Moves(MOVE_STUFF_CHEEKS, MOVE_BELCH, MOVE_RECYCLE, MOVE_CELEBRATE); }}",
        f"        OPPONENT({d['species']}) {{ Ability({d['ability']}); Level(50); SpDefense({d['spDefense']}); "
        f"HP(60000); MaxHP(60000); Speed(20); Moves(MOVE_CELEBRATE); }}",
        "    } WHEN {",
        "        TURN { MOVE(player, MOVE_STUFF_CHEEKS); MOVE(opponent, MOVE_CELEBRATE); }",
        f"        TURN {{ MOVE(player, MOVE_BELCH, hit: TRUE, criticalHit: {crit}, WITH_RNG(RNG_DAMAGE_MODIFIER, i)); "
        "MOVE(opponent, MOVE_CELEBRATE); }",
        "    } SCENE {",
    ]
    lines += ["        " + s if s.endswith("}") else "        " + s + ";" for s in [x.rstrip(";") for x in scene]]
    lines += [
        "    } THEN {",
        "        EXPECT_EQ(DdxbAteBerry(gBattlerPartyIndexes[0]), TRUE);",
        "        EXPECT_EQ(gBattleMons[0].item, ITEM_NONE);",
    ]
    if v.get("immune"):
        lines += [
            "        EXPECT_EQ(gLastMoves[0], MOVE_BELCH);",
            "        EXPECT_EQ(gBattleMons[1].hp, 60000);",
        ]
    lines += [
        f'        Test_MgbaPrintf("DDXB|damage|{v["id"]}|%d|%d|%d|%d|%d|%d|%d|%d", i, {damage_expr}, '
        "gBattleMons[0].spAttack, gBattleMons[1].spDefense, gBattleMons[0].species, gBattleMons[1].species, "
        "gBattleMons[0].ability, gBattleMons[1].ability);",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def source(order="canonical"):
    damage = list(DAMAGE)
    if order == "reversed":
        damage.reverse()
    head = r'''#include "global.h"
#include "battle.h"
#include "battle_util.h"
#include "move.h"
#include "test/battle.h"

// `ateBerry` is a bitfield, and EXPECT_EQ cannot take typeof of one, so the flag is read by value.
static bool32 DdxbAteBerry(u32 partySlot)
{
    return gBattleStruct->partyState[B_SIDE_PLAYER][partySlot].ateBerry;
}

'''
    lifecycle = r'''
SINGLE_BATTLE_TEST("DDXO belch-fresh-refused")
{
    GIVEN {
        PLAYER(SPECIES_GREEDENT) { Ability(ABILITY_INSOMNIA); Item(ITEM_ORAN_BERRY); Moves(MOVE_STUFF_CHEEKS, MOVE_BELCH, MOVE_RECYCLE, MOVE_CELEBRATE); }
        OPPONENT(SPECIES_WOBBUFFET) { Moves(MOVE_CELEBRATE); }
    } WHEN {
        TURN { MOVE(player, MOVE_BELCH, allowed: FALSE); MOVE(player, MOVE_CELEBRATE); MOVE(opponent, MOVE_CELEBRATE); }
    } SCENE {
        ANIMATION(ANIM_TYPE_MOVE, MOVE_CELEBRATE, player);
    } THEN {
        EXPECT_EQ(DdxbAteBerry(gBattlerPartyIndexes[0]), FALSE);
        EXPECT_EQ(gBattleMons[0].item, ITEM_ORAN_BERRY);
        Test_MgbaPrintf("DDXB|lifecycle|fresh-refused|%d|%d", DdxbAteBerry(gBattlerPartyIndexes[0]), gBattleMons[0].item);
    }
}

SINGLE_BATTLE_TEST("DDXO belch-consumed-executes")
{
    GIVEN {
        PLAYER(SPECIES_GREEDENT) { Ability(ABILITY_INSOMNIA); Item(ITEM_ORAN_BERRY); Moves(MOVE_STUFF_CHEEKS, MOVE_BELCH, MOVE_RECYCLE, MOVE_CELEBRATE); }
        OPPONENT(SPECIES_WOBBUFFET) { Moves(MOVE_CELEBRATE); }
    } WHEN {
        TURN { MOVE(player, MOVE_STUFF_CHEEKS); MOVE(opponent, MOVE_CELEBRATE); }
        TURN { MOVE(player, MOVE_BELCH); MOVE(opponent, MOVE_CELEBRATE); }
    } SCENE {
        ANIMATION(ANIM_TYPE_MOVE, MOVE_STUFF_CHEEKS, player);
        ANIMATION(ANIM_TYPE_MOVE, MOVE_BELCH, player);
    } THEN {
        EXPECT_EQ(DdxbAteBerry(gBattlerPartyIndexes[0]), TRUE);
        EXPECT_EQ(gBattleMons[0].item, ITEM_NONE);
        Test_MgbaPrintf("DDXB|lifecycle|consumed-executes|%d|%d", DdxbAteBerry(gBattlerPartyIndexes[0]), gBattleMons[0].item);
    }
}

SINGLE_BATTLE_TEST("DDXO belch-switch-slot0")
{
    GIVEN {
        PLAYER(SPECIES_GREEDENT) { Item(ITEM_ORAN_BERRY); Moves(MOVE_STUFF_CHEEKS, MOVE_BELCH, MOVE_RECYCLE, MOVE_CELEBRATE); }
        PLAYER(SPECIES_SKWOVET) { Moves(MOVE_BELCH, MOVE_CELEBRATE); }
        OPPONENT(SPECIES_WOBBUFFET);
    } WHEN {
        TURN { MOVE(player, MOVE_STUFF_CHEEKS); }
        TURN { SWITCH(player, 1); }
        TURN { MOVE(player, MOVE_BELCH, allowed: FALSE); MOVE(player, MOVE_CELEBRATE); }
        TURN { SWITCH(player, 0); }
        TURN { MOVE(player, MOVE_BELCH); }
    } SCENE {
        ANIMATION(ANIM_TYPE_MOVE, MOVE_STUFF_CHEEKS, player);
        ANIMATION(ANIM_TYPE_MOVE, MOVE_CELEBRATE, player);
        ANIMATION(ANIM_TYPE_MOVE, MOVE_BELCH, player);
    } THEN {
        EXPECT_EQ(gBattlerPartyIndexes[0], 0);
        EXPECT_EQ(DdxbAteBerry(0), TRUE);
        EXPECT_EQ(DdxbAteBerry(1), FALSE);
        Test_MgbaPrintf("DDXB|lifecycle|switch-slot0|%d|%d", DdxbAteBerry(0), DdxbAteBerry(1));
    }
}

SINGLE_BATTLE_TEST("DDXO belch-second-battle-fresh-refused")
{
    GIVEN {
        PLAYER(SPECIES_GREEDENT) { Ability(ABILITY_INSOMNIA); Item(ITEM_ORAN_BERRY); Moves(MOVE_STUFF_CHEEKS, MOVE_BELCH, MOVE_RECYCLE, MOVE_CELEBRATE); }
        OPPONENT(SPECIES_WOBBUFFET) { Moves(MOVE_CELEBRATE); }
    } WHEN {
        TURN { MOVE(player, MOVE_BELCH, allowed: FALSE); MOVE(player, MOVE_CELEBRATE); MOVE(opponent, MOVE_CELEBRATE); }
    } SCENE {
        ANIMATION(ANIM_TYPE_MOVE, MOVE_CELEBRATE, player);
    } THEN {
        EXPECT_EQ(DdxbAteBerry(gBattlerPartyIndexes[0]), FALSE);
        Test_MgbaPrintf("DDXB|lifecycle|second-battle-fresh-refused|%d", DdxbAteBerry(gBattlerPartyIndexes[0]));
    }
}

SINGLE_BATTLE_TEST("DDXO belch-recycle-keeps-flag")
{
    GIVEN {
        PLAYER(SPECIES_GREEDENT) { Ability(ABILITY_INSOMNIA); Item(ITEM_ORAN_BERRY); Moves(MOVE_STUFF_CHEEKS, MOVE_BELCH, MOVE_RECYCLE, MOVE_CELEBRATE); }
        OPPONENT(SPECIES_WOBBUFFET) { Moves(MOVE_CELEBRATE); }
    } WHEN {
        TURN { MOVE(player, MOVE_STUFF_CHEEKS); MOVE(opponent, MOVE_CELEBRATE); }
        TURN { MOVE(player, MOVE_RECYCLE); MOVE(opponent, MOVE_CELEBRATE); }
        TURN { MOVE(player, MOVE_BELCH); MOVE(opponent, MOVE_CELEBRATE); }
    } SCENE {
        ANIMATION(ANIM_TYPE_MOVE, MOVE_STUFF_CHEEKS, player);
        ANIMATION(ANIM_TYPE_MOVE, MOVE_RECYCLE, player);
        ANIMATION(ANIM_TYPE_MOVE, MOVE_BELCH, player);
    } THEN {
        EXPECT_EQ(DdxbAteBerry(gBattlerPartyIndexes[0]), TRUE);
        EXPECT_EQ(gBattleMons[0].item, ITEM_ORAN_BERRY);
        Test_MgbaPrintf("DDXB|lifecycle|recycle-keeps-flag|%d|%d", DdxbAteBerry(gBattlerPartyIndexes[0]), gBattleMons[0].item);
    }
}
'''
    damage_tests = "\n".join(_damage_test(v, i) for i, v in enumerate(damage))
    return head + lifecycle + "\n" + damage_tests


def sources():
    files = backend.render_sources([])
    files[TEST_FILE] = source("canonical")
    return files


def run(args):
    backend.verify_upstream(args.upstream_dir)
    backend.export_worktree(args.upstream_dir, args.work_dir)
    generated = source(args.order)
    files = backend.render_sources([])
    files[TEST_FILE] = generated
    log = backend.build_and_run(args.work_dir, args.toolchain_bin, files, args.jobs)
    args.log.parent.mkdir(parents=True, exist_ok=True)
    args.log.write_text(log)
    clean = backend.ANSI_RE.sub("", log)
    statuses = {}
    for line in clean.splitlines():
        match = backend.RESULT_RE.match(line.strip())
        if match and match.group("name").startswith("DDXO belch-"):
            statuses.setdefault(match.group("name"), []).append(match.group("result"))
    expected_names = {f"DDXO belch-{name}" for name in LIFECYCLE} | {f"DDXO belch-damage-{v['id']}" for v in DAMAGE}
    if set(statuses) != expected_names:
        raise SystemExit(f"runner reported {sorted(set(statuses) ^ expected_names)} unexpectedly")
    failing = {name: results for name, results in statuses.items() if any(r != "PASS" for r in results)}
    if failing:
        raise SystemExit(f"engine assertions failed: {failing}")
    lifecycle = {}
    damage = {v["id"]: [] for v in DAMAGE}
    details = {v["id"]: {} for v in DAMAGE}
    for line in clean.splitlines():
        match = re.search(r"DDXB\|lifecycle\|([^|]+)\|([\d|]+)", line)
        if match:
            lifecycle[match[1]] = [int(v) for v in match[2].split("|")]
        match = re.search(r"DDXB\|damage\|([^|]+)\|(\d+)\|(-?\d+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)", line)
        if match:
            vid, roll = match[1], int(match[2])
            damage[vid].append((roll, int(match[3]), dict(attackerSpAttack=int(match[4]), defenderSpDefense=int(match[5]),
                                                          attackerSpecies=int(match[6]), defenderSpecies=int(match[7]),
                                                          attackerAbility=int(match[8]), defenderAbility=int(match[9]))))
    assert set(lifecycle) == set(LIFECYCLE), sorted(lifecycle)
    for vid, rows in damage.items():
        assert sorted(r[0] for r in rows) == list(ROLLS), (vid, len(rows))
        rows.sort(key=lambda r: r[0])
        observed = {tuple(sorted(r[2].items())) for r in rows}
        assert len(observed) == 1, (vid, "engine state differs between rolls", observed)
        details[vid] = rows[0][2]
    vectors = []
    for v in DAMAGE:
        rows = sorted(damage[v["id"]])
        vectors.append({
            "id": v["id"],
            "immune": bool(v.get("immune")),
            "critical": v["crit"],
            "attacker": {**v["attacker"], "observed": details[v["id"]]["attackerSpAttack"]},
            "defender": {**v["defender"], "observed": details[v["id"]]["defenderSpDefense"]},
            "rolls": [r[1] for r in rows],
        })
    doc = {
        "schemaVersion": 1,
        "pinnedCommit": backend.HNS_PINNED_COMMIT,
        "sourceSha256": hashlib.sha256(source("canonical").encode()).hexdigest(),
        "backend": backend.backend_provenance(args.work_dir),
        "lifecycle": [{"id": name, "observed": lifecycle[name]} for name in LIFECYCLE],
        "damageVectors": vectors,
    }
    rendered = json.dumps(doc, sort_keys=True, indent=2) + "\n"
    if args.command == "verify":
        assert TARGET.read_text() == rendered, "canonical and reversed-order original-engine results differ"
        print("Slice 16 reversed-order replay matches committed original-engine Belch evidence")
    else:
        TARGET.write_text(rendered)
        print(f"Slice 16 original-engine Belch evidence: {len(LIFECYCLE)} lifecycle scenarios, "
              f"{len(DAMAGE)} damage vectors x {len(ROLLS)} rolls")


def check_artifact():
    doc = json.loads(TARGET.read_text())
    assert doc["schemaVersion"] == 1 and doc["pinnedCommit"] == backend.HNS_PINNED_COMMIT
    assert doc["sourceSha256"] == hashlib.sha256(source("canonical").encode()).hexdigest()
    assert [row["id"] for row in doc["lifecycle"]] == LIFECYCLE
    assert [row["id"] for row in doc["damageVectors"]] == [v["id"] for v in DAMAGE]
    for row in doc["damageVectors"]:
        assert len(row["rolls"]) == len(ROLLS)
        if row["immune"]:
            assert all(r == 0 for r in row["rolls"])
        else:
            assert all(r > 0 for r in row["rolls"]), row["id"]
    print("Slice 16 Belch artifact check: no engine executed; source digest, lifecycle ids and 16-roll vectors verified")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("run", "verify", "check", "emit"), default="run", nargs="?")
    parser.add_argument("--upstream-dir", type=Path, default=ROOT.parent / "upstream-hns/pokehns-expansion")
    parser.add_argument("--work-dir", type=Path, default=ROOT / ".scratch/hns16-engine")
    parser.add_argument("--toolchain-bin", type=Path,
                        default=Path.home() / "opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin")
    parser.add_argument("--log", type=Path, default=ROOT / ".scratch/hns16-engine.log")
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
    sys.exit(main())
