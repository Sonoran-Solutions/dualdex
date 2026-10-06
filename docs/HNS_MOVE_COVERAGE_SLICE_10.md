# H&S 2.0.5 move coverage slice 10 — Rollout / Ice Ball

Issue [#141](https://github.com/Sonoran-Solutions/dualdex/issues/141).
Actual starting main: `f6c300e508a33e3d58c8c0d4c423febec1cbe429`.
PR #140 merged 2026-10-05 21:26:45 UTC; reviewed head
`e2f66e87f4cae9456eeb34b5f097a03c42063939` and merge have identical tree
`88cf4ba9c1a5ca92703fcf218a73c084f897497b`. No intervening commits.
Mechanics authority: PokemonHnS-Development/pokehns-expansion Release-v2.0.5,
`1f42b74dff0e9fe942419845d040663dd829a973`.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen selected-hit family

Only Rollout (205) and Ice Ball (301), EFFECT_ROLLOUT (91), enter
FIXED_SINGLE_HIT_ROLLOUT. Full source MoveInfo entries are pinned: power 30,
Physical, Rock / Ice respectively, accuracy 90, PP 20, TARGET_SELECTED,
priority 0, one strike, contact, not punching, no additional/pre-attack effects,
Sheer Force=false. Ice Ball is ballistic; Rollout is not. Neither enters the
ordinary set. Generated H&S presentation reads **Variable (Defense Curl ×2)**;
vanilla power 30 and other power-30 moves retain their existing presentation.
This calculates the displayed selected hit, without simulating subsequent turns
or changing accuracy/hit/KO probability contracts.

Original `CalcRolloutBasePower` gives:

`BP = 30 × 2^rolloutTimer × (defenseCurl ? 2 : 1)`

Reviewed timer values 0–4 give 30,60,120,240,480 or 60,120,240,480,960 with
Defense Curl. The packed source domain is eight bits, 0–255; raw 5–255 remains
observed but refuses. Defense Curl is the source volatile flag, not Defense
stage: real Harden controls raise Defense without doubling power. Technician
uses computed power before later Q12 BP modifiers: 30/60 qualify, 120–960 do not.

## Live authority and phase

The existing read-only battler reader transports compiled rolloutTimer,
defenseCurl, multipleTurns and rechargeTimer from the generated volatile window,
plus the source/release-map-verified gLockedMoves (0x02000378, u16 stride 2).
The shared packed-field extractor reads across byte boundaries; no declaration
layout is guessed. Existing tuple indices 0–164 remain unchanged. Rollout v1
appends indices 165–173: version, observed, raw timer, Curl, multiple-turn flag,
locked move, timer width, recharge timer and recharge width. Absent, short,
malformed, wrong-version or unread packets resolve to null, never neutral values.

The production boundary rebuilds these operands from exact role/slot/species
matched observations. It reuses the existing compiled action-selection callback,
completed switch-in event sentinel and cleared switch-in flags. Both participants
must agree on a stable Singles/two-battler snapshot. An unknown or intermediate
phase refuses. During an active chain, multipleTurns must be true, timer must be
1–4 and gLockedMoves must identify the requested move exactly. Timer 0 requires
multipleTurns=false; stale inactive lock storage is ignored only when recharge=0.
`HandleAction_UseMove` also selects a stored lock while recharging, so nonzero
recharge refuses. No local turn count, move history, base-power override or
caller-provided chain state authorizes execution.

| Source point | Meaning for the selected hit |
|---|---|
| Before first use, settled selection | Timer 0, multipleTurns false. Curl reflects actual source flag. |
| At damage | Current timer is consumed before the move-end increment. |
| Successful move end | `SetSameMoveTurnValues` increments only when target affected, attacker able and last resulting move equals current move. Values 1–4 set active lock to current move. |
| Fifth successful use | Increment reaches 5, resets timer to 0 and clears multipleTurns. Lock storage may remain stale. |
| Protect / unaffected target / inability | Original predicate or CancelMultiTurnMoves resets timer and active chain. Defense Curl persists until volatile teardown. |
| Miss | Same source target-affected predicate resets; Protect is the executed interruption witness. |
| Switch / faint replacement | Original volatile clearing removes chain and Defense Curl. Real source switch and faint witnesses cover both moves. |
| Battle teardown / reload | Existing observation invalidation requires a fresh stable packet; absent packets refuse. Native teardown regression proves observed state clears. |

## Request-local boundaries and evidence

Existing source-authorized Attack/Defense, stages, type rewrites, STAB,
Adaptability, contact, hold-effect suppression, weather, terrain, Charge,
Reflect, crit and final Q12 arithmetic are reused. Normalize and effective-type
consumers remain separate from chain power. Tough Claws and Fluffy see contact;
Bulletproof blocks Ice Ball only. Sheer Force remains inactive. Substitute,
unknown/nonneutral semi-state, unsupported format/status, missing runtime state,
Mold Breaker bypass and independently unknown modifier operands still refuse.
No unrelated category or operand default is broadened.

The source contract pins MoveInfo, base-power helper, Technician threshold/order,
lock selection, move-end writers, cancellation, volatile clearing, compiled
layout and stable callback. Mutation tests reject drift. Native raw-domain,
nullable decoder, forged request and authorized-execution tests prove unread or
contradictory state never runs the calculator. Direct JS independently checks
family, frozen descriptor and raw state before even immunity handling.

The engine oracle calls original power code through a TESTING-only accessor.
Arithmetic vectors seed raw state only; their source-observed actual attacker,
move/effect, timer, Curl, multipleTurns, lock, recharge and computed power are
recorded and checked independently. Effective type is captured at the damage
boundary before temporary flags clear; the assembler and host adapter consume
that source value. Real Electrify + Charge + Electric Terrain + Technician
engine-only witnesses use dynamic BP60 and source rolls 244–288 for both moves.
Active Electrify still refuses in production under the existing dynamic-type
boundary; unshielded Mold Breaker bypass of Fluffy / Bulletproof is also engine-only. Normalize
controls retain Normal type, proving Charge and Electric Terrain are inactive.
Long Reach has a request-local clearance only for this family: original
IsMoveMakingContact clears attacker contact, and the existing calculator applies
that predicate before Tough Claws / Fluffy. Defender Long Reach is inactive for
this incoming selected hit; the global ability classification is unchanged.
Sequential lifecycle witnesses execute real
Defense Curl / Harden / Celebrate and real chained moves without seeding their
chain state. Read-only hooks observe before damage and after original move-end
writers. Six-hit chains prove 0,1,2,3,4,0 and post-state 1,2,3,4,0,1; Protect,
sleep inability, Bulletproof, forced switch and faint replacement exercise resets.

Machine-readable artifacts:

- `tools/hns-calc-census/rollout-negative-control.json`: the unchanged test runs
  on actual starting SHA through `./ci.sh test`, proving both moves previously
  refused solely as mechanics not modelled.
- `tools/hns-damage-oracle/rollout-evidence.json`: historical object/line equality,
  all counter/Curl powers, Technician, Bulletproof, real lifecycle and fresh
  reversed replay.
- `tools/hns-calc-census/move-coverage-slice-10.json`: identical population and
  lead definition, exact transitions, residual causes and next-family ranking.

## Starting census opportunity

The immutable starting census contains 651 battles and 24,278 eligible damaging
requests: 21,278 fully modelled, 462 caveated and 2,538 refused. It displays all
moves in 630/1,302 lead pairs and 7,482/8,450 lead requests, with no wholly blank
battle. The family contributes 90 blocked requests in 39 battles: 86 Rollout and
4 Ice Ball, 38 lead requests / affected lead pairs. Removing only the recorded
move gate gives a selection upper-bound estimate of 72; overlapping known causes
are 2 ability conditions, 10 ability effects and 6 item effects. These counts do
not authorize any production request. The full census report measures actual
transitions after admission and preserves the exact population and definitions.

## Measured coverage and next bounded family

The full census gains **90 fully modelled requests across 39 battles**:
21,278 → 21,368 FULLY_MODELLED, 462 → 462 CAVEATED_ESTIMATE and
2,538 → 2,448 REFUSED. All 86 Rollout and 4 Ice Ball requests gain coverage;
none of this family remains refused in the defined neutral fixture. Displayable
lead pairs rise **630 → 656 / 1,302**, and lead requests **7,482 → 7,520 / 8,450**.
No battle is wholly blank. Outside-family changes: **0**; every historical Gyro
and Electro Ball request record remains equal. Actual gains exceed the 72
selection estimate because admission lets 18 overlapping existing ability/item
rules prove their request-local predicates; no global ability/item category is
upgraded.

The largest remaining family is repeated strikes (392 requests), whose multi-hit
state and survival semantics require a separate contract. The recommended next
bounded selected-hit family is **EFFECT_HIT_ESCAPE**: U-Turn (369), Volt Switch
(521), Flip Turn (740), 74 requests in 29 battles, with a 68-request selection
estimate. Review its damage before post-hit switching without predicting the
replacement. Semi-invulnerable moves, OHKO, Pursuit and Belch remain outside this
slice, as do unknown live state and independently unsupported modifiers.

## Safe AYN Thor follow-up

Hardware is not required for software completion and has not been tested here.
Use a naturally obtained Pokemon and move, ordinary battles and your normal save.
Observe a fresh use, successive hits, fifth-hit reset and another fresh use.
Naturally use Defense Curl first and compare its presentation; Harden must not
show a Curl multiplier. Interrupt with Protect or a naturally occurring miss,
then inspect the reset. Switch out/back and inspect cleared Curl/chain. Reload
normally and verify fresh observation replaces previous state. During transitions
or unavailable reads, expect a truthful refusal rather than assumed first-use
power. Do not edit a real save or manufacture timer/lock state.

## Final software evidence

The independent corpus adds 72 scenarios (68 modelled, 4 engine-only) to the
unchanged 2,394 historical entries (2,274 modelled, 120 engine-only). Final totals
are 2,466 / 2,342 / 124. The shipped calculator reproduces all 2,466 sixteen-roll
vectors exactly; registered divergences remain zero. Fresh reversed-order engine
regeneration is byte-identical. Six real lifecycle test families pass, including
36 separate pre-hit/post-action chain observations. No lifecycle initializer
stands in for those sequential witnesses.

Canonical verification also runs the machine-readable census and oracle report
checks. Pinned metadata/layout verification and 37 source mutations reject drift.
Production tests assert zero calculator invocations for refused state and reject
forged direct QuickJS descriptors and operands. H&S remains ESTIMATED; Hardware:
NOT_RUN.

Reproduce from the repository root with the pinned upstream checkout and the
ARM toolchain configured as described in the oracle README:

```bash
python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py regenerate --upstream-dir ../upstream-hns/pokehns-expansion --jobs 8 --keep-log /tmp/hns10-engine.log
python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py verify --upstream-dir ../upstream-hns/pokehns-expansion --order reversed --jobs 8 --keep-log /tmp/hns10-reversed-engine.log > /tmp/hns10-reversed-summary.log 2>&1
python3 tools/hns-damage-oracle/report_rollout_evidence.py --canonical-log /tmp/hns10-engine.log --reversed-log /tmp/hns10-reversed-engine.log --reversed-summary /tmp/hns10-reversed-summary.log
DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh test
./ci.sh all
./ci.sh source-check
python3 tools/hns-calc-census/report_rollout_coverage.py --check
python3 tools/hns-damage-oracle/report_rollout_evidence.py --check
git diff --check
```

Run the Gradle-based gates sequentially in one checkout. The starting-head
negative-control source is retained under `tools/hns-calc-census/starting-head/`;
copy it into the calculator test directory of a detached starting-SHA checkout
and run its canonical `./ci.sh test`. Its APIs belong to that immutable head.
