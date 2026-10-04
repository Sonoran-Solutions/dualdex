# H&S 2.0.5 move coverage, slice 1 — fixed single-hit recoil

Issue #122 follows #83/#93; it does not change their historical acceptance criteria.
Starting main is `3821066e898652509b826526bb8418e8c42adbe2`, including #120 and #121.
Mechanics remain pinned to `PokemonHnS-Development/pokehns-expansion`
`1f42b74dff0e9fe942419845d040663dd829a973` / Release-v2.0.5.

## Selection and frozen scope

Admit the twelve source-proven fixed single-hit `EFFECT_RECOIL` moves in **Singles**:
Take Down (36), Double-Edge (38), Submission (66), Volt Tackle (344),
Flare Blitz (394), Brave Bird (413), Wood Hammer (452), Head Smash (457),
Wild Charge (528), Head Charge (543), Light of Ruin (617), Wave Crash (762).
All select one target. Light of Ruin is source Special; the others are source Physical.
Neutral status is admitted; existing conditional Physical-Guts status, Toxic Boost
poison and Flare Boost burn branches retain their exact status gates. Other active
status branches still refuse. Full/partial HP, source stat stages, fixed crit, supported
Rain/Sun/screens/terrain and admitted ability/item modifiers use their existing live
operands; no execution-history or recoil-history operand is added.
The authoritative challenge option can still choose type-based categories. Fairy-off
retyping, live Electrify/Ion Deluge and the admitted ability rewrites retain their
existing authority path. Reckless's attacker BP modifier is included.

No multi-hit total, accuracy/crit chance, recoil total, recovery, KO probability,
subsequent action or turn prediction is displayed. The selected hit still has sixteen
rolls and the request's fixed critical setting. Recoil can make the attacker faint
**after** this damage; the range is not a recommendation that it survives.

Doubles recoil, Struggle, recoil-on-miss, Chloroblast and other effects remain refused.
Active resist-berry admission remains restricted to the old ordinary move surface.
Unknown/invalid/mismatched observations, independent ability/item/field/gimmick
blockers, survival caveats and the H&S `ESTIMATED` support ceiling are preserved.
Flare Blitz Freeze/Frostbite is refused even in a Guts context: self-thaw is before
hit damage, so a menu-time status operand cannot be reused across that transition.

## Reproducible ranking

Run `python3 tools/hns-calc-census/report_move_coverage.py` (or `--check`). It reads
an immutable starting census using `git show` and the current production-generated
census. The JSON report contains **every constituent move ID/name**, its generated
source flags, distinct keys/battles, lead requests/pairs, overlapping blockers,
actual old/new tiers/reasons/causes and remaining selected-family refusals.

A = requests with the move blocker. B = an **unproven preliminary upper bound**
where no other limitation was already recorded. B is not a production verdict:
ability/item decisions themselves depend on move admission. C is measured only by
regenerating the unchanged population through the real boundary. C can exceed B
when a newly valid contextual proof resolves an overlapping blocker.

| Rank | Effect/group | A | B | Battles | Lead requests / affected pairs | Operands, scope and disposition |
|---:|---|---:|---:|---:|---:|---|
| 1 | HIT / repeated strikes | 392 | 336 | 138 | 166 / 160 | Multi-hit and strike-count flags distinguish members. Multiple rolls/strikes need a separate output contract; deferred. |
| 2 | RECOIL | 322 | 228 | 129 | 110 / 106 | Existing power/type/category, effective abilities/items, live stats/status/field. Small separate admission plus Reckless BP slot. **Selected**: highest-impact coherent bounded single-hit family. |
| 3 | EARTHQUAKE (Earthquake/Bulldoze) | 294 | 202 | 106 | 68 / 68 | Underground doubling and Grassy Terrain's semi-invulnerability predicate. Reader state is not yet a validated calculation operand; needs boundary binding plus a BP stage. Medium scope, fewer requests than recoil. |
| 4 | ABSORB | 196 | 174 | 84 | 72 / 72 | Fixed hit followed by recovery; healing/Triage, Big Root and Liquid Ooze interactions need distinct proofs. Next strongest bounded candidate. |
| 5 | HIT / explosion | 188 | 168 | 66 | 72 / 72 | Explosion flag, modern Defense rule, Damp execution prohibition and post-hit self-KO need separate proof. Smaller benefit. |
| 6 | HIT / damagesUnderwater | 128 | 90 | 55 | 34 / 34 | Surf/Whirlpool: source target phase and doubling, plus spread shape. Validate/bind semi-invulnerability and modifier order; deferred. |
| 7 | DOUBLE_POWER_ON_ARG_STATUS | 102 | 94 | 48 | 28 / 28 | Exact target status is observed, but argument-specific status masks, doubling and cleanup need proof. Medium scope. |
| 8 | GYRO_BALL | 90 | 78 | 39 | 42 / 42 | Effective speed, stages, ability/item/field ordering; raw speed alone is insufficient. Larger arithmetic scope. |
| 9 | ROLLOUT | 90 | 72 | 39 | 38 / 38 | Consecutive use, Defense Curl and execution history. Unobserved history; deferred. |
| 10 | SEMI_INVULNERABLE | 80 | 70 | 36 | 34 / 34 | Charge/attack phase, target interaction and Power Herb. Execution state is not authoritative; deferred. |

The JSON retains all remaining ranks and exact overlapping causes. These numbers
are neutral-state offline measurements, not live-play percentages. Trainer data,
reference moves, eligible request keys and lead definitions are unchanged.

## Source → operand → authority → formula → tests

| Pinned source | Operand and production authority | Selected-hit calculation / evidence |
|---|---|---|
| `src/data/moves_info.h`; `include/constants/moves.h` | Generated `fixedSingleHitRecoilMoveIds` separate from unchanged `ordinaryMoveIds`; exact pack name/ID, power/type/category rebound by `CalcRequestBoundary` and `CalcDataOverrides` | Same fixed power hit. Generator rejects changed effects/targets, strike count and every excluded damage flag; mutation tests cover these. |
| `src/data/battle_move_effects.h:2198-2202` | `EFFECT_RECOIL` selects `BattleScript_EffectHit`; no special execution/history operand | Normal damage script. All twelve moves are measured on both attacker directions in the engine oracle. |
| `src/battle_util.c:CalcMoveBasePower`, `CalcMoveBasePowerAfterModifiers` | Exact move power, effective type/category and current raw stats/stages/status come from existing live readers | Recoil adds no dynamic base-power rule. The existing UQ4.12 composition/rounding stages remain in place. |
| `src/battle_util.c:6667-6670` | Observed attacker effective Reckless (120), admitted recoil effect; unknown effective ability is not clearable | `uq4_12_multiply(modifier, UQ_4_12(1.2))` at attacker ability BP stage, Q12 4915; apply once at the existing power rounding step. Defender and ordinary-hit branches are inactive. Rounding grid, crit, stages and combined-modifier oracle controls. |
| `include/move.h`; `MoveIsAffectedBySheerForce`, `IsMoveMakingContact` | Generated contact/punch/secondary flags, live source Long Reach contact predicate (the independent Long Reach ability gate remains refused), held-item authority and exact pinned config | Tough Claws/Fluffy/Sheer Force retain shared attribution and formula slots. Volt Tackle's GEN_4 conditional secondary is resolved only after checking `B_UPDATED_MOVE_DATA=GEN_LATEST`; other unresolved initializers stay unknown. |
| `src/battle_move_resolution.c:2971-2995` | Selected hit, recoil percentage; Rock Head/Magic Guard | Recoil follows damage. Harness checks post-hit HP against measured damage and source-generated percentages; it never creates expected damage rolls. Life Orb and Sheer Force combinations test distinct residuals. |
| `src/battle_move_resolution.c:574-599` | Generated `recoilThawsUserMoveIds`, authoritative status1 | Flare Blitz's pre-hit Freeze/Frostbite clearing is explicitly excluded by the status gate. Burn/poison support retains existing conditional status rules. |
| `TrySetAteType`, `SetTypeBeforeUsingMove`, `GetMoveTargetCount` | Existing move-type authority, live field and abilities; actual Singles topology agreed by both observations | Recoil has none of the effect-specific type/target exceptions. Doubles cannot inherit Singles admission. |

### Audit of former ordinary-predicate consumers

The ordinary set is unchanged. The separate `FIXED_SINGLE_HIT_RECOIL` category
and explicitly named `fixedSingleHitMove` operand are used in contextual proofs:

- **Move/category/type override and immunity:** recoil uses ordinary stat selection,
  fixed source power and the same type/category pipeline. No fixed damage, defense
  selection, item-dependent power, multiple strikes, target bypass or execution-history
  exception is admitted. Group C keeps independent immunity/suppression restrictions.
- **Ability contexts:** Technician examines unchanged power; contact and Sheer Force
  use exact metadata. Iron Fist/Strong Jaw/Mega Launcher/Sharpness remain flag-gated.
  Crit-stage/survival/post-hit/turn-order relevance proofs still describe this one hit.
  Reckless is the sole new arithmetic slot; its old non-recoil clearance is not reused
  for recoil. Unknown effective ability, Analytic order and contextual stat/state
  requirements remain independent. Rock Head/Magic Guard only affect later recoil.
- **Item contexts:** power/type/stat/final modifiers consume the same operands.
  Speed/weight/crit-rate utilities cannot affect fixed power plus fixed crit;
  switch-in activation and effective item/suppression checks remain required.
  Life Orb, Gems, Sheer Force and contact-related modifiers retain separate predicates.
  Resist-berry authority intentionally keeps its narrower ordinary gate.
- **Field contexts and boundary readers:** no recoil move has positive priority,
  spread targeting, Gravity prohibition, Grassy Glide or terrain-specific power
  exceptions. Existing grounding, terrain applicability, screens/weather, dynamic
  type and transient-state authority remain necessary. Self-thaw gets its own refusal.
- **Serialization and attribution:** production emits `hnsIsOrdinary=false` plus the
  explicit `hnsFixedSingleHit=true` and source effect for recoil. The QuickJS contact
  guard admits only the proved effect/descriptor combination. No caller-owned move
  flag or census/debug escape is added. Exact conditional Reckless is removed from
  the current Group E remainder by its generators; historical closure claims are
  not rewritten.

## Measured coverage

The unchanged census has 651 battles and 24,278 eligible damaging requests.

| Metric | Starting head | This slice |
|---|---:|---:|
| Fully modelled requests | 19,856 | 20,172 |
| Caveated estimates | 462 | 462 |
| Refused requests | 3,960 | 3,644 |
| Fully displayable lead pairs / 1,302 | 390 | 444 |
| Displayed lead requests / 8,450 | 7,016 | 7,124 |

316 requests transition REFUSED → FULLY_MODELLED; none transition to a caveated
estimate. 54 lead pairs become fully displayable and 127 distinct battles gain
requests. Zero battles remain completely blank. No request outside this family
changes tier or causes; no regressions are hidden by the denominator.

Six of the 322 family requests remain refused, all by independent ability gates:
Scrappy on Akala Camper's Brave Bird and Milkman's Double-Edge (two reference
teams each), Infiltrator on Janine PostOBC slot 3 Brave Bird (both teams).
The JSON report retains every exact key and reason.

The 228 preliminary move-only counts were not a forecast: admission also proves
inactive pinch abilities, existing item contexts and other contextual operands,
so the real boundary unlocks 316. This is why the report distinguishes A/B/C.

## Validation

The pinned engine adds 51 cases: 49 modelled and two engine-only (Long Reach and
Gastro Acid, whose independent production blockers remain). Total: 1,945 cases,
1,842 modelled, 103 engine-only; all sixteen rolls match, zero divergences.
All 1,894 historical entry objects are unchanged. No historical scenario is promoted.

Starting-head negative control `./ci.sh test` compiled and then reproduced three
intended admission failures: the per-move test, real Aaron/Ivysaur Take Down, and
ready-request override test all hit `HNS_MOVE_MECHANICS_NOT_MODELLED`. Aaron also
hit `HNS_ABILITY_CONDITION_UNVERIFIED:attacker:Overgrow`. The separate arithmetic
test cannot find the new scenario IDs in the old corpus and is **not** counted as
reproduction; earlier compilation errors are not counted either. Exact messages
are retained in `move-coverage-negative-controls.json`.

Final non-generating `./ci.sh all` passes, including the 1,945-case shipped-bundle
differential suite and debug APK. `./ci.sh source-check`, generator `--verify`,
oracle `check`, report `--check` and `git diff --check` pass. Full canonical census
regeneration passes. Oracle regeneration and reversed-order verification produce
byte-identical artifacts. Final-head Actions are recorded in the PR handoff.
Hardware: **NOT_RUN**.

Safe Thor smoke checklist: use an already verified H&S 2.0.5 image and an ordinary
Singles battle; inspect Take Down/Double-Edge and a different-type recoil move in
both directions. Confirm sixteen-roll min/max display, no recoil/KO prediction,
Reckless attribution when actually observed, and unchanged survival caveats.
Confirm refusal for unread participants/state, excluded recoil shapes, suppressed
abilities, Flare Blitz Freeze/Frostbite and Doubles. Do not edit saves or use codes.
