# H&S move coverage Slice 13: plain random 2–5 strikes

## Scope and provenance

This slice starts at `2d57cf8b991b04e676fd6d920ddcdf52159f1735`, the merged Slice 12 head from PR #148. It reuses Slice 12's repeated-strike result, pinned engine hooks, and sequence-stability authority. Implementation issue: [#149](https://github.com/Sonoran-Solutions/dualdex/issues/149). Review PR: [#157](https://github.com/Sonoran-Solutions/dualdex/pull/157), kept open and unmerged for senior review.

Mechanics are pinned to `PokemonHnS-Development/pokehns-expansion`, `Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`. The generated source contract freezes `SetRandomMultiHitCounter`, `CancelerMultihitMoves`, `MoveEndMultihitMove`, the move table descriptors, `GetBattlerAbility`, `GetBattlerHoldEffect`, and the generation configuration that selects `B_MULTI_HIT_CHANCE`. Source authority is checked by `tools/hns-move-mechanics/generate_hns_move_effects.py` and its mutation tests.

## Exact family and source descriptors

The family is `VARIABLE_MULTI_HIT_PLAIN`, generated for exactly these twelve source move IDs. All have `EFFECT_HIT`, `multiHit = TRUE`, no fixed `strikeCount`, target `TARGET_SELECTED`, priority 0, `Sheer Force` inapplicable, and no additional or pre-attack effects. Descriptor hashes cover the complete frozen source descriptor; ballistic and punching flags are shown separately because they affect behavior.

| Move (ID) | Power / type / category | Accuracy / PP | Contact | Punching | Ballistic | Descriptor SHA-256 |
|---|---|---:|---|---|---|---|
| Arm Thrust (292) | 15 / Fighting / Physical | 100 / 20 | yes | no | no | `68908eecef1cf68a19f1fbe07bb0fa762e19b1540bfc832b818273ffa3ac7d29` |
| Bone Rush (198) | 25 / Ground / Physical | 90 / 10 | no | no | no | `68e17169bfe0bc96e47836b9db35fa5ca2480f584a531727686e8250b3ba3100` |
| Bullet Seed (331) | 25 / Grass / Physical | 100 / 30 | no | no | yes | `403f0cc0df44c4994a8cb81039fafc9e33873179491a594df5b9185964000cc9` |
| Comet Punch (4) | 18 / Normal / Physical | 85 / 15 | yes | yes | no | `45efa36822888907fdfa562b16087aba3ce4ef400673b2672eefbb8def020731` |
| Double Slap (3) | 15 / Normal / Physical | 85 / 10 | yes | no | no | `37f1f7ef3c2e33fc86f1daad1abec9e425fedd32778ddc0d42cfc64d7b61cb8e` |
| Fury Attack (31) | 15 / Normal / Physical | 85 / 20 | yes | no | no | `060f699afe5322f95999041a1d82251c12e49403fa8d6ee8f6b45dbc674f8397` |
| Fury Swipes (154) | 18 / Normal / Physical | 80 / 15 | yes | no | no | `e772a1809fb7e5b77bfcffda2d4808b7e6a8ede6b6be1971edf93b3fd4a8216d` |
| Icicle Spear (333) | 25 / Ice / Physical | 100 / 30 | no | no | no | `a229be72d4e28c36a1d0f765137ee3f31e9158a2019f49067847e06a6c0a832f` |
| Pin Missile (42) | 25 / Bug / Physical | 95 / 20 | no | no | no | `0a46abdbc00ec2ae9cdd555cf517840a5abdd71ead66e10c744d33434b8c1acd` |
| Rock Blast (350) | 25 / Rock / Physical | 90 / 10 | no | no | yes | `5c4665229897c6efcf12894630b467e0a126e8a61e8910a0154a941ae3973e18` |
| Spike Cannon (131) | 20 / Normal / Physical | 100 / 15 | no | no | no | `ba1b14d4b373733088b525325148a5c737cf8a8be648fcb8f9bce4f03e206ba9` |
| Tail Slap (541) | 25 / Normal / Physical | 85 / 10 | yes | no | no | `d9d37df2f2f4c52f7148cdf3f1745d309c9236dcde23779d2cd7c8b2649f5394` |

The descriptor boundary rejects drift in ID/name, effect, power, type, category, accuracy, PP, target, priority, contact, punching, ballistic status, multi-hit flag, fixed count, or secondary/pre-attack effects. `Scale Shot` (727), `Twineedle` (41), fixed-two moves, and other repeated-strike families do not carry this marker.

## Count authority and displayed result

`SetRandomMultiHitCounter` first honors effective Skill Link, then effective Loaded Dice, then ordinary random selection. The pinned Gen 9 configuration gives ordinary weights 7:7:3:3 over counts 2/3/4/5, or 35%/35%/15%/15%. The older configuration branch is separately pinned as 3:3:1:1, so a generation/config drift fails source validation. Probability weights are evidence only; the product does not predict a count or show count-weighted expected damage.

- Ordinary count state: `nominalCounts = [2, 3, 4, 5]`.
- Effective Skill Link: `[5]`, with no count RNG draw; it takes precedence over Loaded Dice.
- Effective Loaded Dice: `[4, 5]`, uniform at the source selector; it applies only when Skill Link is not effective.
- Fixed-two Slice 12 moves remain `[2]` with either modifier.

The Kotlin live-state authorities and shipped QuickJS independently derive the count mode from effective ability/item identity and suppression state. Skill Link uses effective ability state with Gastro Acid, Neutralizing Gas, Ability Shield, and unknown suppression handled by existing policy. Loaded Dice uses the shared effective hold-effect authority; Klutz, Embargo, Magic Room, active item identity, suppressed-none, and unknown suppression are not inferred from an item label. Unknown count or suppression state refuses. No new live ABI was added.

The product reuses `RepeatedStrikeResult`; the registry keeps selected-strike arithmetic, repeated-family kind, count authority, and sequence stability as separate decisions. It reports the first-strike 16-roll vector separately, then one executable total branch for each allowed nominal count, plus an explicitly labeled “Across possible hit counts” aggregate derived from those branches. It does not emit the legacy whole-move damage vector, expected damage, accuracy probability, critical probability, or KO probability.

For positive stable per-hit endpoints `lo..hi`, authoritative target HP `H`, and nominal count `N`, the conditional HP-loss endpoints use wide integer multiplication before clamping:

```text
minHpLoss(N) = min(H, N * lo)
maxHpLoss(N) = min(H, N * hi)
minExecutedHits(N) = min(N, ceil(H / hi))
maxExecutedHits(N) = min(N, ceil(H / lo))
ceil(H / D) = (H + D - 1) / D
```

Source immunity/zero damage takes the existing zero-damaging-hit path; division by zero is not attempted. Source traces show the nominal count selector runs once before initial accuracy; a first miss executes no damaging strikes or per-hit RNG. A successful ordinary accuracy check occurs once before strikes, and continuation does not repeat it. Each strike independently draws critical and damage RNG. The app's existing crit mode remains all-noncritical or all-critical for the executable branches; mixed critical traces are evidence only. No production RNG changes were made.

## Sequence stability and contact

This family extends `HnsRepeatedStrikeAuthority` rather than introducing another transition policy. A conditional sequence total is emitted only when every damage-relevant operand is proven stable across all possible strikes. Unsupported/unknown transitions remain `REFUSED`; this slice adds no repeated-strike caveat mode. The gate continues to refuse relevant faint-prevention/threshold effects, reactive abilities and items, on-hit item transfer, substitute, doubles, unsettled gimmicks/execution state, and unreviewed reactions. This includes Sturdy, Focus Sash/Focus Band, Endure, Multiscale, Shadow Shield, Tera Shell, Stamina, Weak Armor, Water Compaction, Rough Skin, Iron Barbs, Rocky Helmet, Flame Body, Static, Poison Point, Effect Spore, Poison Touch, Toxic Chain, Mummy, Lingering Aroma, Wandering Spirit, Sand Spit, Seed Sower, Wind Power, Electromorphosis, stat-change reactions such as Weakness Policy, resist/threshold berries, and equivalent unreviewed transitions.

For contact moves, the reviewed Slice 12 Beak Blast rule still requires a committed nonzero defender move when relevant. `MOVE_NONE` remains unknown; selected Beak Blast refuses. Exact Long Reach or active Protective Pads independently proves the contact reaction cannot occur. Non-contact moves need no chosen-move witness. Slice 12's reset → action selection → move commitment lifecycle checks remain in force; no `gChosenActionByBattler` or turn-order state was added. Comet Punch is the sole punching move in this family and retains both punching and contact flags; Bullet Seed and Rock Blast retain ballistic immunity flags.

## Original-engine and production evidence

The additive `tools/hns-damage-oracle/variable-multihit-evidence.json` preserves the historical single-hit corpus and Slice 12 fixed-two evidence byte-for-byte. It contains 553 passing source-engine traces for all twelve moves. Vectors cover N=2/3/4/5 with low, high, alternating, non-monotonic, all-critical, noncritical, and mixed-critical sequences; all 16 Bullet Seed first-strike rolls crossed with every N; Skill Link/Loaded Dice precedence and suppression; and Technician, Tough Claws, and Fluffy for every move/count combination. Life Orb, immunity, initial miss, and low-HP execution boundaries are also covered. Every selected move is Physical, so this exact family has no Special move for a separate Special-category composition.

The traces observe count selector tag, mode, effective ability/item, the one selected nominal count, every per-strike RNG draw, damage calculation, pre/post HP, between-strike state, applied HP loss, executed-hit count, and stop reason. They show ordinary count is selected once, Loaded Dice uses its selector, Skill Link consumes no count RNG, and early KO leaves later RNG queue entries untouched. Bullet Seed's measured neutral first-strike endpoints are 14–17 at the fixture stats. For each N=2..5, directed low/high HP thresholds around prefixes of one through N strikes validate immediate stopping and the executed-hit formula without enumerating 16^N roll combinations.

Production QuickJS replay asserts all twelve exact descriptors and the four ordinary conditional branches per move (48 move/count branches), adds low-HP-clamp and zero-damage immunity controls, and rejects 11 forged count/mode/distribution/ability/item/suppression/stability/total/descriptor/Scale-Shot-relabel requests. The artifact SHA-256 is `9f5e357ea88bb9baed43b7f3befc879748a745b629ac309370f8c528634b1fdb`. Fixed-two count, Scale Shot, Twineedle, Triple Kick, Triple Axel, Population Bomb, Beat Up, and Parental Bond-created extra hits remain outside this family. Starting-head admission control runs all twelve move refusals against the immutable Slice 12 head and records the result at `tools/hns-calc-census/variable-multi-hit-negative-control.json`.

The historical corpus entry `group-c-flag-bulletproof` remains byte-identical and is explicitly migrated out of the legacy single-hit production replay. The C runner validates that this is the original modelled Bullet Seed (ID 331, Grass, 25 BP, ballistic) versus Bulletproof case with its original zero vector; it never substitutes another move. The sole replacement is `bullet-seed-bulletproof` in the Slice-13 evidence artifact. Its passing trace proves immunity stops before count selection, with zero executed hits, no count/RNG/calculation/HP events, and zero damage/crit rolls. A one-to-one regression check requires exactly this historical ID and exactly one matching passing replacement. The differential now reports 2,522 direct production replays plus one explicitly migrated scenario; the immutable corpus still contains 2,523 entries.

## Census and remaining work

The full neutral production census remains 651 trainer battles and 24,278 eligible requests, with request keys, teams, direction, lead definition, and neutral assumptions unchanged. Before Slice 13 there were 21,440 fully modeled, 462 caveated, and 2,376 refused requests; after it there are 21,448, 462, and 2,368. The target family has 284 requests over 109 battles, including 122 affected lead pairs and 126 lead requests. Its move-only upper bound is 250, with 20 overlapping ability-condition blockers and 14 overlapping ability-effect blockers. Eight target requests became fully modeled; 276 remain refused: 130 for `HNS_REPEATED_STRIKE_STATE_UNKNOWN`, 146 for `HNS_REPEATED_STRIKE_TRANSITION_NOT_MODELLED`, and 2 for `HNS_ABILITY_EFFECT_NOT_MODELLED` (reason counts can overlap). Target `REFUSED -> CAVEATED_ESTIMATE` is zero. Four battles gained requests; fully displayable lead pairs move from 662 to 664, displayed lead requests from 7,548 to 7,550, and blank battles remain zero. The report records zero unexplained outside-slice changes. The post-Slice-12 opportunity set was 288 requests: 284 plain-variable, Scale Shot (2), and Twineedle (2). After Slice 13, 280 of that set remain refused: 276 plain-variable, Scale Shot (2), and Twineedle (2).

Historical corpus: 2,523 immutable entries (2,399 modeled, 124 engine-only); 2,522 direct production replays match exactly, with one explicit Slice-13 migration and zero registered divergences. Historical corpus SHA-256: `9f38413dd64ce0f73162f84db1f7f3cd7d84a6bf74bb38d09fb5b336947e1580`. Slice 12 evidence SHA-256: `4b5fa96b4ecfd77b45cfa6c3e196c13fca22b4fa0c87e7cba9c62597b89d3b8a`. H&S compatibility remains **ESTIMATED**; hardware is **NOT_RUN**.

After this slice merges, rerun the remaining-family ranking. Recommended next: Scale Shot as a bounded move-level post-sequence stat-effect slice, then Twineedle as a fixed-two per-strike secondary-effect slice. Do not begin either here.
