#!/usr/bin/env python3
"""Original-engine Rapid Spin (ID 229) evidence for Slice 17 (pinned H&S Release-v2.0.5 runner).

Every scenario is real gameplay on the pinned engine with the real move. Damage evidence and
post-hit evidence are kept in separate scenario groups:

Damage vectors (one battle per roll, sixteen `PARAMETRIZE` runs, `WITH_RNG(RNG_DAMAGE_MODIFIER, i)`):
  neutral, stab, critical, technician, tough-claws, reflect, sheer-force, sheer-force-life-orb,
  composition-rounding, immune (Ghost).

Post-hit lifecycle (one battle each, asserted on the engine's observed state):
  * speed-boost: an unseeded, non-Sheer-Force hit raises the user's Speed by one stage;
  * cleanup-leech: a Leech Seed on the user is removed by the hit; the Speed boost still applies;
  * sheer-force-suppresses: Sheer Force suppresses both the Leech Seed cleanup and the Speed boost,
    while the damage is still boosted;
  * blocked-protect: the target's Protect blocks the strike, so neither HP nor the Speed boost changes.

Results are written to `rapid-spin-evidence.json` only after every assertion passes. The
reversed-order run emits the damage vectors in the opposite order; its artifact must match the
canonical one exactly.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

import oracle_backend as backend

ROOT = Path(__file__).resolve().parents[2]
TARGET = Path(__file__).with_name("rapid-spin-evidence.json")
ROLLS = tuple(range(16))
TEST_FILE = "test/dualdex_oracle/rapid_spin_evidence.c"

PLAYER_BASE = "Level(50); HP(60000); MaxHP(60000); Speed(40);"
# Lifecycle fixtures use realistic HP. Leech Seed heals add to the seeder's HP field before the cap is
# checked, so 60000-HP fixtures overflow the 16-bit field and invalidate HP-based lifecycle evidence.
LIFECYCLE_PLAYER_BASE = "Level(50); HP(200); MaxHP(200); Speed(40);"
LIFECYCLE_OPPONENT_BASE = "Level(50); HP(200); MaxHP(200); Speed(20);"
OPPONENT_BASE = "Level(50); HP(60000); MaxHP(60000); Speed(20);"

# Each vector: attacker (species, ability, item, attack), defender (species, ability, defense), crit,
# optional setup (defender uses Reflect on turn 1), and immunity.
DAMAGE = [
    dict(id="neutral", attacker=dict(species="SPECIES_MUK", ability="ABILITY_INSOMNIA", item="ITEM_NONE", attack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=100), crit=False),
    dict(id="stab", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", item="ITEM_NONE", attack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=100), crit=False),
    dict(id="critical", attacker=dict(species="SPECIES_MUK", ability="ABILITY_INSOMNIA", item="ITEM_NONE", attack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=100), crit=True),
    dict(id="technician", attacker=dict(species="SPECIES_MUK", ability="ABILITY_TECHNICIAN", item="ITEM_NONE", attack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=100), crit=False),
    dict(id="tough-claws", attacker=dict(species="SPECIES_MUK", ability="ABILITY_TOUGH_CLAWS", item="ITEM_NONE", attack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=100), crit=False),
    dict(id="reflect", attacker=dict(species="SPECIES_MUK", ability="ABILITY_INSOMNIA", item="ITEM_NONE", attack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=100), crit=False, reflect=True),
    dict(id="sheer-force", attacker=dict(species="SPECIES_MUK", ability="ABILITY_SHEER_FORCE", item="ITEM_NONE", attack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=100), crit=False),
    dict(id="sheer-force-life-orb", attacker=dict(species="SPECIES_MUK", ability="ABILITY_SHEER_FORCE", item="ITEM_LIFE_ORB", attack=100),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=100), crit=False),
    dict(id="composition-rounding", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", item="ITEM_NONE", attack=137),
         defender=dict(species="SPECIES_BLASTOISE", ability="ABILITY_INSOMNIA", defense=89), crit=True),
    dict(id="immune", attacker=dict(species="SPECIES_GREEDENT", ability="ABILITY_INSOMNIA", item="ITEM_NONE", attack=100),
         defender=dict(species="SPECIES_GENGAR", ability="ABILITY_INSOMNIA", defense=101), crit=False, immune=True),
]

LIFECYCLE = ["speed-boost", "cleanup-leech", "sheer-force-suppresses", "blocked-protect"]


def _damage_test(v, order_index):
    a, d = v["attacker"], v["defender"]
    crit = "TRUE" if v["crit"] else "FALSE"
    if v.get("immune"):
        # A Ghost-type target is immune to Normal Rapid Spin: the move is used, no HP changes, no animation.
        scene = ["NONE_OF { HP_BAR(opponent); }"]
        damage_expr = "0"
    else:
        scene = ["ANIMATION(ANIM_TYPE_MOVE, MOVE_RAPID_SPIN, player)",
                 "HP_BAR(opponent, captureDamage: &results[i].damage)"]
        damage_expr = "results[i].damage"
    turns = []
    if v.get("reflect"):
        turns.append("TURN { MOVE(player, MOVE_CELEBRATE); MOVE(opponent, MOVE_REFLECT); }")
        scene.insert(0, "ANIMATION(ANIM_TYPE_MOVE, MOVE_REFLECT, opponent)")
    else:
        turns.append("TURN { MOVE(player, MOVE_CELEBRATE); MOVE(opponent, MOVE_CELEBRATE); }")
    turns.append(f"TURN {{ MOVE(player, MOVE_RAPID_SPIN, hit: TRUE, criticalHit: {crit}, WITH_RNG(RNG_DAMAGE_MODIFIER, i)); "
                 "MOVE(opponent, MOVE_CELEBRATE); }")
    lines = [
        f'SINGLE_BATTLE_TEST("DDXO rapid-spin-damage-{v["id"]}", s16 damage)',
        "{",
        "    " + " ".join(["PARAMETRIZE { }"] * len(ROLLS)),
        "    GIVEN {",
        f"        PLAYER({a['species']}) {{ Ability({a['ability']}); Item({a['item']}); {PLAYER_BASE} "
        f"Attack({a['attack']}); Moves(MOVE_RAPID_SPIN, MOVE_CELEBRATE); }}",
        f"        OPPONENT({d['species']}) {{ Ability({d['ability']}); {OPPONENT_BASE} "
        f"Defense({d['defense']}); Moves(MOVE_CELEBRATE, MOVE_REFLECT); }}",
        "    } WHEN {",
    ]
    lines += ["        " + t for t in turns]
    lines += [
        "    } SCENE {",
    ]
    lines += ["        " + s + ";" for s in scene]
    lines += [
        "    } THEN {",
        "        EXPECT_EQ(gLastMoves[0], MOVE_RAPID_SPIN);",
    ]
    if v.get("immune"):
        lines += ["        EXPECT_EQ(gBattleMons[1].hp, 60000);"]
    lines += [
        f'        Test_MgbaPrintf("DDXB|damage|{v["id"]}|%d|%d|%d|%d|%d|%d|%d|%d", i, {damage_expr}, '
        "gBattleMons[0].attack, gBattleMons[1].defense, gBattleMons[0].species, gBattleMons[1].species, "
        "gBattleMons[0].ability, gBattleMons[1].ability);",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def _lifecycle_test(name):
    # Each scenario prints the user's Speed stage, the user's Leech Seed flag, and the target's HP.
    common_print = (f'Test_MgbaPrintf("DDXB|lifecycle|{name}|%d|%d|%d", '
                    "gBattleMons[0].statStages[STAT_SPEED], gBattleMons[0].volatiles.leechSeed ? 1 : 0, "
                    "gBattleMons[1].hp);")
    if name == "speed-boost":
        player_ability, seeded, hit = "ABILITY_INSOMNIA", False, "TRUE"
        expect = ["EXPECT_EQ(gBattleMons[0].statStages[STAT_SPEED], DEFAULT_STAT_STAGE + 1);",
                  "EXPECT_EQ(DdxRsLeechSeed(0), FALSE);",
                  "EXPECT_LT(gBattleMons[1].hp, 200);"]
    elif name == "cleanup-leech":
        player_ability, seeded, hit = "ABILITY_INSOMNIA", True, "TRUE"
        expect = ["EXPECT_EQ(gBattleMons[0].statStages[STAT_SPEED], DEFAULT_STAT_STAGE + 1);",
                  "EXPECT_EQ(DdxRsLeechSeed(0), FALSE);",
                  "EXPECT_LT(gBattleMons[1].hp, 200);"]
    elif name == "sheer-force-suppresses":
        player_ability, seeded, hit = "ABILITY_SHEER_FORCE", True, "TRUE"
        expect = ["EXPECT_EQ(gBattleMons[0].statStages[STAT_SPEED], DEFAULT_STAT_STAGE);",
                  "EXPECT_EQ(DdxRsLeechSeed(0), TRUE);",
                  "EXPECT_LT(gBattleMons[1].hp, 200);"]
    else:  # blocked-protect: the target's Protect blocks the strike entirely
        player_ability, seeded, hit = "ABILITY_INSOMNIA", False, "PROTECT"
        expect = ["EXPECT_EQ(gBattleMons[0].statStages[STAT_SPEED], DEFAULT_STAT_STAGE);",
                  "EXPECT_EQ(gBattleMons[1].hp, 200);"]
    setup = []
    when = []
    opponent_base = "Level(50); HP(200); MaxHP(200); Speed(60);" if hit == "PROTECT" else LIFECYCLE_OPPONENT_BASE
    if seeded:
        setup = ["TURN { MOVE(player, MOVE_CELEBRATE); MOVE(opponent, MOVE_LEECH_SEED); }"]
        scene_setup = ["ANIMATION(ANIM_TYPE_MOVE, MOVE_LEECH_SEED, opponent)"]
    elif hit == "PROTECT":
        # Protect lasts one turn, so the target protects in the same turn as the strike; it is faster
        # (Speed 60 > 40) so Protect is already active when Rapid Spin resolves.
        scene_setup = []
    else:
        scene_setup = []
    if hit == "PROTECT":
        when = ["TURN { MOVE(player, MOVE_RAPID_SPIN, hit: TRUE); MOVE(opponent, MOVE_PROTECT); }"]
    else:
        when = setup + [f"TURN {{ MOVE(player, MOVE_RAPID_SPIN, hit: TRUE); MOVE(opponent, MOVE_CELEBRATE); }}"]
    scene = scene_setup
    if hit != "PROTECT":
        scene += ["ANIMATION(ANIM_TYPE_MOVE, MOVE_RAPID_SPIN, player)", "HP_BAR(opponent)"]
    lines = [
        f'SINGLE_BATTLE_TEST("DDXO rapid-spin-{name}")',
        "{",
        "    GIVEN {",
        f"        PLAYER(SPECIES_GREEDENT) {{ Ability({player_ability}); {LIFECYCLE_PLAYER_BASE} Attack(100); "
        "Moves(MOVE_RAPID_SPIN, MOVE_CELEBRATE, MOVE_LEECH_SEED); }",
        f"        OPPONENT(SPECIES_BLASTOISE) {{ Ability(ABILITY_INSOMNIA); {opponent_base} Defense(100); "
        "Moves(MOVE_CELEBRATE, MOVE_LEECH_SEED, MOVE_PROTECT); }",
        "    } WHEN {",
    ]
    lines += ["        " + t for t in when]
    lines += ["    } SCENE {"]
    lines += ["        " + s + ";" for s in scene]
    lines += ["    } THEN {"]
    bounds = ["EXPECT_GT(gBattleMons[0].hp, 0);", "EXPECT_LE(gBattleMons[0].hp, gBattleMons[0].maxHP);",
              "EXPECT_GT(gBattleMons[1].hp, 0);", "EXPECT_LE(gBattleMons[1].hp, gBattleMons[1].maxHP);"]
    lines += ["        " + e for e in bounds + expect]
    lines += ["        " + common_print, "    }", "}", ""]
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

// Bitfields cannot be passed to EXPECT_EQ (typeof), so the flag is read by value.
static bool32 DdxRsLeechSeed(u32 battler)
{
    return gBattleMons[battler].volatiles.leechSeed != 0;
}

'''
    body = "\n".join(_damage_test(v, i) for i, v in enumerate(damage))
    body += "\n" + "\n".join(_lifecycle_test(name) for name in LIFECYCLE)
    return head + body


def sources():
    files = backend.render_sources([])
    files[TEST_FILE] = source("canonical")
    return files


def run(args):
    backend.verify_upstream(args.upstream_dir)
    backend.export_worktree(args.upstream_dir, args.work_dir)
    files = backend.render_sources([])
    files[TEST_FILE] = source(args.order)
    log = backend.build_and_run(args.work_dir, args.toolchain_bin, files, args.jobs)
    args.log.parent.mkdir(parents=True, exist_ok=True)
    args.log.write_text(log)
    clean = backend.ANSI_RE.sub("", log)
    statuses = {}
    for line in clean.splitlines():
        match = backend.RESULT_RE.match(line.strip())
        if match and match.group("name").startswith("DDXO rapid-spin-"):
            statuses.setdefault(match.group("name"), []).append(match.group("result"))
    expected_names = {f"DDXO rapid-spin-{name}" for name in LIFECYCLE} | {f"DDXO rapid-spin-damage-{v['id']}" for v in DAMAGE}
    if set(statuses) != expected_names:
        raise SystemExit(f"runner reported {sorted(set(statuses) ^ expected_names)} unexpectedly")
    failing = {name: results for name, results in statuses.items() if any(r != "PASS" for r in results)}
    if failing:
        raise SystemExit(f"engine assertions failed: {failing}")
    lifecycle = {}
    damage = {v["id"]: [] for v in DAMAGE}
    details = {v["id"]: {} for v in DAMAGE}
    for line in clean.splitlines():
        match = re.search(r"DDXB\|lifecycle\|([^|]+)\|(-?\d+)\|(\d+)\|(\d+)", line)
        if match:
            lifecycle[match[1]] = [int(match[2]), int(match[3]), int(match[4])]
        match = re.search(r"DDXB\|damage\|([^|]+)\|(\d+)\|(-?\d+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)", line)
        if match:
            vid, roll = match[1], int(match[2])
            damage[vid].append((roll, int(match[3]), dict(attack=int(match[4]), defense=int(match[5]),
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
            "reflect": bool(v.get("reflect")),
            "attacker": {**v["attacker"], "observed": details[v["id"]]["attack"]},
            "defender": {**v["defender"], "observed": details[v["id"]]["defense"]},
            "rolls": [r[1] for r in rows],
        })
    doc = {
        "schemaVersion": 1,
        "pinnedCommit": backend.HNS_PINNED_COMMIT,
        "sourceSha256": hashlib.sha256(source("canonical").encode()).hexdigest(),
        "backend": backend.backend_provenance(args.work_dir),
        "lifecycle": [{"id": name, "observed": {
            "userSpeedStage": lifecycle[name][0], "userLeechSeed": bool(lifecycle[name][1]),
            "targetHp": lifecycle[name][2]}} for name in LIFECYCLE],
        "damageVectors": vectors,
    }
    rendered = json.dumps(doc, sort_keys=True, indent=2) + "\n"
    if args.command == "verify":
        assert TARGET.read_text() == rendered, "canonical and reversed-order original-engine results differ"
        print("Slice 17 reversed-order replay matches committed original-engine Rapid Spin evidence")
    else:
        TARGET.write_text(rendered)
        print(f"Slice 17 original-engine Rapid Spin evidence: {len(LIFECYCLE)} lifecycle scenarios, "
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
    print("Slice 17 Rapid Spin artifact check: no engine executed; source digest, lifecycle ids and 16-roll vectors verified")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("run", "verify", "check", "emit"), default="run", nargs="?")
    parser.add_argument("--upstream-dir", type=Path, default=ROOT.parent / "upstream-hns/pokehns-expansion")
    parser.add_argument("--work-dir", type=Path, default=ROOT / ".scratch/hns17-engine")
    parser.add_argument("--toolchain-bin", type=Path,
                        default=Path.home() / "opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin")
    parser.add_argument("--log", type=Path, default=ROOT / ".scratch/hns17-engine.log")
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
