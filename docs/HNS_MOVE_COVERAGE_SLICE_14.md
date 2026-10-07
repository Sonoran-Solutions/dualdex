# H&S Move Coverage Slice 14: Scale Shot

## Scope and starting state

Slice 14 starts from `c91f024ad50f428fdd99b537e2caa6dab3abd431`, the merge commit for PR #157 (Slice 13). PR #157 was merged and senior-reviewed; issue #149 was closed with its requested completion record. The implementation is tracked by [issue #158](https://github.com/Sonoran-Solutions/dualdex/issues/158) and [PR #159](https://github.com/Sonoran-Solutions/dualdex/pull/159), which is open for senior review. The branch is based on the fetched `origin/main` at that exact SHA.

The baseline census is the immutable Slice-13 artifact: 651 battles, 24,278 eligible requests, 21,448 `FULLY_MODELLED`, 462 `CAVEATED_ESTIMATE`, and 2,368 `REFUSED`; 664/1,302 lead pairs are fully displayable, 7,550/8,450 lead requests display, and no battle is blank. The opportunity contains two Scale Shot requests in one battle, affecting no lead pairs or lead requests; this is a ceiling, not a promised unlock.

## Frozen move and family

The pinned authority is PokemonHnS-Development/pokehns-expansion Release-v2.0.5 at `1f42b74dff0e9fe942419845d040663dd829a973`.

| Field | Pinned value |
|---|---|
| ID / name | 727 / Scale Shot |
| Effect / power | `EFFECT_HIT` / 25 |
| Type / category | Dragon / Physical |
| Accuracy / PP | 90 / 20 |
| Target / priority | `TARGET_SELECTED` / 0 |
| Repeated strike | `multiHit = TRUE`; no fixed `strikeCount` |
| Contact / punching / ballistic / Sheer Force | false / false / false / false |
| Additional effect / pre-attack effect | exactly `MOVE_EFFECT_SCALE_SHOT` / none |
| Descriptor SHA-256 | `cf06a2be103ecbb1f1ba4e1a47d48b8f06d45e37844514d65b7e361ee7d0f42a` |

The exact move belongs to `VARIABLE_MULTI_HIT_SCALE_SHOT`, whose only member is ID 727. It is not in `VARIABLE_MULTI_HIT_PLAIN`. Twineedle and the other special repeated-strike families remain refused. The descriptor generator and 25 source/script mutations fail closed on changes to the descriptor, flags, additional effect, or completion-script anchors.

## Shared damage and count authority

Scale Shot reuses `HnsRepeatedStrikeCountAuthority` and `HnsRepeatedStrikeAuthority`; it introduces no second count engine or weaker stability policy. Ordinary count choices are `[2,3,4,5]` with source weights 35%, 35%, 15%, 15%; effective Skill Link selects `[5]` without count RNG; effective Loaded Dice selects `[4,5]` uniformly; Skill Link takes precedence over Loaded Dice. Existing Gastro Acid, Neutralizing Gas, Ability Shield, Klutz, Embargo, Magic Room, hold-effect, and unknown-state gates remain shared with Slice 13.

The result retains the Slice-13 `RepeatedStrikeResult`: sixteen first-strike rolls, one executable HP-loss total per nominal count, executed-hit bounds, and a separately labelled aggregate across count possibilities. Each strike uses the existing damage pipeline (including Technician); totals use the stable-sequence formula `min(H, N × endpoint)` with wide arithmetic and HP clamping. Immunity and zero damage remain zero-loss/zero-hit cases. Initial accuracy is checked once; later ordinary strikes do not reroll accuracy. Count selection precedes damage. Product crit modes remain all noncritical or all critical per executed strike; mixed-crit traces are evidence only. No probability output or whole-move legacy roll array was added.

## Why completion does not change current damage

In pinned `src/battle_move_resolution.c`, `MoveEndMultihitMove` decrements `gMultiHitCounter`, sets it to zero if the target has fainted, and branches on `gMultiHitCounter == 0`. Only that loop-exit branch checks `MoveHasAdditionalEffect(gCurrentMove, MOVE_EFFECT_SCALE_SHOT)` and `!NoAliveMonsForEitherParty()` before queuing `BattleScript_ScaleShot`. The between-hit re-entry is in the other branch and requires live attacker and target HP. The source contract fails if the completion call moves into that between-hit path.

Pinned `data/battle_scripts_1.s` defines `BattleScript_ScaleShot` as printing the multihit result and then going to `BattleScript_DefDownSpeedUp`. That script attempts Defense and Speed stage changes using the ordinary stat-change engine, stage-bound checks, and `STAT_CHANGE_CERTAIN`. Its exact script body and its timing relationship are hashed into generated metadata (`2fb16df90c8ce3fcc8f441154bd00493485405db9668226ef639c483497c7f39`).

This is a damage-scope proof: the completion effect occurs after the damaging sequence and is not an operand to any current-move strike, conditional total, or executed-hit bound. The calculator does not predict post-action Defense or Speed, and does not simulate generic stat stages, Contrary, Simple, or later turns. `FULLY_MODELLED` therefore means exact current Scale Shot damage under the existing H&S capability contract, not a prediction of the resulting future battle state. No new live ABI or memory reads were required.

## Completion and early-stop evidence

The additive artifact [`scale-shot-evidence.json`](../tools/hns-damage-oracle/scale-shot-evidence.json) contains 116 passing cases against the pinned engine, including 100 observed completion-script invocations. Its TESTING-only observation hook is adjacent to the actual `BattleScriptCall(BattleScript_ScaleShot)` and records attacker stages at invocation plus stages after the sequence. It records no production state and does not mutate battle mechanics. Across ordinary counts 2–5, the hook observes neutral stages (6/6) at invocation and the source script's Defense/Speed result (5/7) after script execution. The evidence includes 16 first-roll cases per count, low/high/alternating/mixed damage vectors, all-critical and mixed-critical patterns, Technician, rounding-sensitive modifiers, Skill Link, Loaded Dice, precedence and suppression cases, and HP clamping.

Early target KOs across each nominal count show the selected nominal count remains recorded while executed hits stop at the KO; when the target is its party's final living Pokémon, `NoAliveMonsForEitherParty()` prevents the completion call. A final executed hit that leaves the target alive invokes completion. Initial miss and source immunity do not invoke completion. Runtime coverage for a target KO with an opposing living reserve was attempted, but the upstream runner requires a forced-replacement action and reports the turn incomplete; that control is therefore not claimed as a passing engine case. The source branch itself is hash-pinned and shows that the guard allows completion when both parties still have a living Pokémon. If the attacker faints while the counter is nonzero, the pinned between-hit branch does not re-enter the move; it prints the hit count and the common cleanup clears the counter without visiting the zero-counter Scale Shot branch. This follows from the pinned source branch and is not inferred from nominal count.

The new evidence reproduced byte-for-byte in reversed case order. Mutation checks reject missing, duplicate, or reordered completion events and missing final stage state. The existing Slice-12 and Slice-13 evidence files remain byte-identical.

## Production boundary and regression limits

QuickJS derives this family from the generated exact descriptor, timing digest, and current request facts; it rejects plain-family relabeling, descriptor/field/effect/count/stability forgeries, fake Skill Link or Loaded Dice, fake suppression, caller-injected totals, and caller completion-state claims. Kotlin uses the same dedicated descriptor family and the shared count and sequence-stability authorities. Scale Shot remains non-contact: it needs no Beak Blast chosen-move witness, gets no Tough Claws contact boost, and receives no Fluffy contact reduction. Long Reach and Protective Pads do not change its source non-contact classification.

No new result schema, ABI, caveat tier, post-sequence state simulator, or migration was added. Exact supported sequences may be fully modeled; unsupported or unresolved sequences remain refused. Slice 12's six fixed-two moves and Slice 13's twelve plain variable moves retain their prior count contracts. Historical `corpus.json` remains 2,523 entries (2,399 modeled, 124 engine-only), with 2,522 direct production replays, exactly one explicit migration (`group-c-flag-bulletproof` → `bullet-seed-bulletproof`), and zero divergences. No Scale Shot historical case was added. Slice 12 remains 5,125 cases; Slice 13 remains 553 cases.

The admission-only negative control compiled and passed on detached starting head `c91f024ad50f428fdd99b537e2caa6dab3abd431`: one test, zero failures, refusing Scale Shot with exactly `HNS_MOVE_MECHANICS_NOT_MODELLED` through the pre-existing move-mechanics gate. Machine-readable record: [`scale-shot-negative-control.json`](../tools/hns-calc-census/starting-head/scale-shot-negative-control.json).

## Census and next work

The full census was generated with `DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh test`. The global population and results are unchanged: 651 battles, 24,278 eligible requests, 21,448 `FULLY_MODELLED`, 462 `CAVEATED_ESTIMATE`, and 2,368 `REFUSED`; lead display remains 664/1,302 pairs and 7,550/8,450 requests; blank battles remain zero.

Both Scale Shot requests started `REFUSED` with `HNS_MOVE_MECHANICS_NOT_MODELLED`; both remain `REFUSED` with `HNS_REPEATED_STRIKE_TRANSITION_NOT_MODELLED`. They are Seaking (Lightning Rod, King's Rock) into Chikorita (Overgrow, no item) and Cyndaquil (Blaze, Wise Glasses), both in trainer battle `TRAINER_AKALA_SWIMMER_1_HNS#1`. The exact request facts and transitions are recorded in [`move-coverage-slice-14.json`](../tools/hns-calc-census/move-coverage-slice-14.json). Newly fully modeled requests: 0. Refused-to-caveated: 0. Battles gaining results: 0. Newly displayable lead pairs/requests: 0/0. Displayed leads remain 7,550 and blank battles remain 0. Unexplained outside-slice changes: 0. No neutral live state was invented to promote either request.

The original 288 repeated-strike requests still have 280 refused: 276 plain variable requests, two Scale Shot requests, and two Twineedle requests. The highest-value global family in the measured remaining ranking is `EFFECT_SEMI_INVULNERABLE` (Bounce, Dig, Dive, Fly, Phantom Force): 80 blocked requests across 36 battles, 34 lead pairs, with a 70-request move-only upper bound. The next global family is `EFFECT_OHKO` with 70 blocked requests. The full ranking is in the linked census artifact.

Hardware was not run (`NOT_RUN`). H&S remains `ESTIMATED`. After Slice 14 merges, the next repeated-strike cleanup is Twineedle, with its own per-strike poison/additional-effect audit. The next global task to evaluate is the `EFFECT_SEMI_INVULNERABLE` family.
