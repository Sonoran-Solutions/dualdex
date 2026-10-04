# H&S 2.0.5 move coverage, slice 2 — fixed single-hit drain

Issue #124 follows #83, #93, #122 and merged PR #123. Starting main is
`f2acf455cbe10670c9a73e06cbb9a27384c8c1e3`; reviewed #123 head
`7a3ed73c007e02983a2462f2b35f9fda93ca68d4` is its ancestor. No dependent
branch, cherry-pick or historical evidence rewrite was used. Mechanics remain
pinned to `PokemonHnS-Development/pokehns-expansion`
`1f42b74dff0e9fe942419845d040663dd829a973` / Release-v2.0.5.

## Frozen scope and source proof

| Move | ID | Power | Category | Contact | Punch | Absorb percentage |
|---|---:|---:|---|---|---|---:|
| Absorb | 71 | 20 | Special | false | false | 50 |
| Mega Drain | 72 | 40 | Special | false | false | 50 |
| Leech Life | 141 | 80 | Physical | true | false | 50 |
| Giga Drain | 202 | 75 | Special | false | false | 50 |
| Drain Punch | 409 | 75 | Physical | true | true | 50 |
| Horn Leech | 532 | 75 | Physical | true | false | 50 |
| Draining Kiss | 577 | 50 | Special | true | false | 75 |

All seven use `EFFECT_ABSORB`, `TARGET_SELECTED`, literal base priority zero,
one strike and the normal `BattleScript_EffectHit`
(`src/data/battle_move_effects.h:27-31`). `CalcMoveBasePower` and its modifier
pipeline introduce no ABSORB-specific dynamic power, HP read, fixed damage,
item-dependent formula or special Defense selection. The existing source power,
effective type/category, raw stats, stages, weather/screens/terrain, fixed crit,
ability/item modifier slots and sixteen-roll rounding pipeline are reused.

`tools/hns-move-mechanics/generate_hns_move_effects.py` derives a separate
`fixedSingleHitDrainMoveIds` and `absorbPercentageById`. The ordinary set stays
unchanged. JSON metadata includes effect, target, priority, healing identity,
contact, punching, ability/immunity flags and source percentage. Giga Drain and
Drain Punch's conditional 75 power is accepted only under verified
`B_UPDATED_MOVE_DATA=GEN_LATEST`; the healing initializer is resolved only under
verified `B_HEAL_BLOCKING=GEN_LATEST`. The percentage comes exclusively from
`MoveInfo.argument.absorbPercentage`, not a parallel name map.

Mutation regressions reject changed/unresolved effects, target/priority,
missing/unresolved power/type/category/healing/percentage, multiple strikes,
explosion, unreviewed damage/execution flags and unresolved contact/punch flags.
The frozen symbolic family explicitly excludes the adjacent ABSORB members
Oblivion Wing and Bitter Blade. No other healing flag can expand admission.

Singles topology must be observed as two battlers. Doubles drain, Dream Eater,
Strength Sap, Leech Seed, Aqua Ring, Parabolic Charge, recovery-only/status moves,
multi-hit or multi-target recovery and other effects remain refused. No healing,
net attacker HP, survival, KO probability or future-turn result is displayed.
H&S's ceiling remains **ESTIMATED**.

## Execution authority, priority and post-hit effects

**Heal Block:** `src/battle_util.c:1380-1385,1523,1716` uses
`IsHealBlockPreventingMove`; `src/battle_move_resolution.c:324-332` cancels the
move before damage. All seven are healing moves in this configuration. The layout
generator compiles a designated `.healBlock=1` probe and extracts bit **86** of
`BattlePokemon.volatiles`. This bit is inside the existing compiled read window;
no memory offset, window size or profile changes are needed.

The native reader exports `heal_block_observed` and `volatile_heal_block` from
that successful read, with all existing identity, lifecycle and layout checks.
JNI preserves the first 162 words and appends `[162]=version 1`, `[163]=observed`,
`[164]=boolean payload` (165 words). Old/short tuples, foreign versions, unread
windows, invalid booleans or inconsistent presence leave authority unknown.
`CalcRequestBoundary` binds the operand only from the current exact-trusted,
slot-matched, OBSERVED runtime state. Prior request state and caller overrides
are overwritten; no trainer default or previous-frame cache is added.
Observed false permits execution, active true yields `HNS_HEAL_BLOCK_ACTIVE`,
and unknown yields `HNS_HEAL_BLOCK_STATE_UNKNOWN`, both hard refusals.
Existing ROM/profile-switch and participant invalidation apply to this same read.

**Triage:** pinned `src/battle_main.c:5049-5052` adds **+3** to healing moves.
The existing effective-priority helper incorrectly compared against ID 261;
pinned Triage is **205**. It now requires known effective attacker ability and
uses the resolved source healing flag. A request-local Triage proof is restricted
to the generated drain family, observed effective abilities and settled Singles.
Defender Triage does not raise this move's priority. Unrelated healing moves do
not gain admission. Gastro Acid, Neutralizing Gas, Ability Shield, gimmicks and
other existing effective-ability restrictions remain independent.

Dazzling, Queenly Majesty and Armor Tail consume the same effective priority.
A positive-priority drain they prevent emits hard `HNS_PRIORITY_BLOCKED`, never
a zero damage roll. Psychic Terrain uses the source-authoritative defender
terrain applicability: grounded Triage drain is a hard execution refusal;
a proven unaffected target clears this specific field branch; unknown remains
unknown. This explicit drain hard gate is necessary because the historical
field policy can caveat a relevant condition. Analytic still requires the
reader's move-bound current-action result; neither base Speed nor +3 is used to
guess actual order. Defender Triage with attacker Analytic cannot clear that gate.

**Post-hit:** `MoveEndAbsorb` (`src/battle_move_resolution.c:2221-2239`) computes
`floor(actual inflicted damage * absorbPercentage / 100)` after the selected hit.
`SetHealScript` (`2167-2185`) first invokes `GetDrainedBigRootHp`
(`src/battle_util.c:1837-1845`): active Big Root applies
`floor(healing * 1300 / 1000)`, then zero becomes one. Effective defender Liquid
Ooze converts that amount into passive attacker damage; otherwise it is recovery.
These writes do not feed back into defender hit arithmetic. Engine checks use
**measured hit damage**, never an expected damage formula, and separately verify
Life Orb residual ordering. Recovery and possible attacker fainting remain outside
the result contract. Item suppression and actual effective ability remain bound.

Big Root has a named drain-local post-hit proof. Liquid Ooze's existing after-hit
proof safely includes this fixed hit after independent execution authorization.
Their global unsupported classifications remain: unrelated mechanics do not gain
move admission or a generalized recovery model. Reviewed ability/item inputs and
generated audits are regenerated; no Group E global hard-refusal disposition is
removed. Historical #83/#93/#123 documents and oracle entries are preserved.

`IsHealingMove` also appears in AI, battle-dome scoring and animation helpers;
those do not modify this selected hit. The move-selection/canceler and priority
paths above are the additional pre-hit uses. Status, accuracy, transient state,
item/ability suppression, exact-ROM trust, gimmicks and current-action gates
remain enforced by the existing boundary.

## Audit of the shared fixed-hit consumers

A = safe for ordinary + recoil + drain; B = ordinary + recoil only;
C = ordinary only; D = drain-specific branch. No bulk predicate rename was used.

| Consumer/proof | Class | Why |
|---|---|---|
| Move registry, override construction, effective type/category | A + D | Same fixed source hit; separate drain category and frozen source metadata. Caller power/effect/healing facts cannot establish membership. |
| Raw stats, stages, HP thresholds, fixed crit, damage/survival caps | A | Same normal hit formula and defender HP adjustment. Post-hit attacker HP is excluded. Existing survival caveats remain. |
| Technician, Iron Fist and other move-flag BP slots | A | Fixed source power and exact generated flags; Drain Punch is punching. Missing flags stay unknown. |
| Tough Claws, Fluffy, contact authority | A | Exact per-move contact; Long Reach/Punching Glove change that predicate through effective ability/item authority. Non-contact controls retain false. |
| Sheer Force, Life Orb, final modifiers, type rewrites | A | Exact source additional-effect flags and existing arithmetic slots. Drain adds no new damage multiplier. |
| After-hit abilities, Liquid Ooze | A | Damage has already been inflicted; later HP/stage/item writes cannot change these rolls. No subsequent hit or attacker survival is predicted. |
| Big Root | D | Explicit generated-drain proof names the post-hit recovery pipeline. Unsupported adjacent move effects retain their own gates. |
| Triage | D | Exact +3, independent priority blockers/terrain; no unrelated healing admission. |
| Heal Block | D | New observed execution operand, active/unknown hard refusal. |
| Group C ability/type immunity and suppression | A + D | Same source type/target/flags. Drain priority prevention is a hard refusal rather than a numeric immunity result. Unknown effective abilities remain refused. |
| Psychic Terrain and grounding | A + D | Normal modifier/grounding proofs are reusable; Triage drain separately requires target applicability and execution authorization. |
| Other field, weather, screens, Gravity | A | No family member is Gravity-banned or has a terrain/dynamic targeting/power exception. Existing unsupported field bits remain independent. |
| Analytic, turn-order abilities/items | A | Actual move-bound action authority remains required. No base-speed or priority shortcut, including defender-side Triage. |
| Resist-berry activation/consumption authority | C | Retains the existing ordinary-only gate; drain does not inherit its narrower proof. |
| Reckless BP, recoil percentages and self-thaw | B | No drain is recoil or self-thawing; existing recoil-only branches and metadata stay unchanged. |
| Doubles partner/target/action authority | C | The current Doubles support surface remains ordinary only; drain cannot inherit it. |
| Serialization and QuickJS contact guard | A + D | Explicit `hnsIsOrdinary=false`, `hnsFixedSingleHit=true`, `hnsIsDrain=true`, move ID and `EFFECT_ABSORB`; no name-based authorization or separate damage formula. |

## Oracle, regressions and negative controls

36 added differential scenarios bring the corpus to **1,981**:
**1,876 modelled**, **105 engine-only**, **1,981 exact sixteen-roll matches**,
**zero registered divergences**. All 1,945 historical entry objects are unchanged.
Cases include every move in both directions; Iron Fist, Tough Claws, Technician,
Fluffy contact/non-contact, Normalize, crit, stages, Sun, screens, Life Orb,
Big Root, Draining Kiss 75%, Liquid Ooze, their composition, minimum recovery,
Embargo and Gastro Acid controls, and both Triage roles. Long Reach and defender
Gastro Acid controls are engine-only because independent production blockers remain.

The generated separate `execution.c` runs seven Heal Block move-prevention
parameters plus a slower Triage Drain Punch acting before faster Tackle and an
exact engine priority assertion. Runner PASS is mandatory, skips/failures/missing
results abort regeneration. These tests are included in the source provenance
hash but are deliberately absent from the differential damage scenario schema:
failed execution is not damage zero.

Native/layout tests preserve observed false/true and unread state. Kotlin tuple
tests exercise old lengths, foreign version, presence/window mismatch and malformed
payloads. Production tests run the real boundary, serialization and shipped bundle;
they cover every admission, exact rolls, exclusions, Doubles, Heal Block, caller
forgery, priority prevention, Analytic unknown, independent missing operands,
contact/punching, Big Root and Liquid Ooze. Existing ordinary and recoil tests remain.

The unchanged `HnsDrainAdmissionTest` compiles on starting main and fails for all
seven intended `HNS_MOVE_MECHANICS_NOT_MODELLED` refusals, with no other failures
or fixture blockers. It passes on this implementation. The full canonical baseline
command exited 1 due solely to that assertion; missing corpus IDs, setup errors
and compilation failures are not counted. Machine-readable exact failure text is
in `tools/hns-calc-census/drain-negative-controls.json`.

## Unchanged-population census

651 battles, the same trainer inventory/reference teams, 24,278 eligible request
keys and 8,450 lead requests. Neutral volatile assumptions now explicitly carry
observed false Heal Block; this is a host measurement fixture, never a live default.
Exact trust, Singles/Doubles classification, lead definition and all independent
production gates are preserved.

| Metric | Starting main | Slice 2 |
|---|---:|---:|
| FULLY_MODELLED | 20,172 | 20,362 |
| CAVEATED_ESTIMATE | 462 | 462 |
| REFUSED | 3,644 | 3,454 |
| Fully displayable lead pairs / 1,302 | 444 | 504 |
| Displayed lead requests / 8,450 | 7,124 | 7,196 |
| Blank battles | 0 | 0 |

190 requests transition REFUSED → FULLY_MODELLED, none to caveated. 81 distinct
battles gain requests. 60 additional lead pairs become fully displayable. There
are **zero outside-slice changes**, including causes, and no population changes.
Four family requests remain refused while some redundant causes clear; all such
changes are retained in the report rather than hidden in the tier totals.

The original selection population is exactly 196 requests / 84 battles / 72 lead
requests / 72 affected lead pairs. The preliminary 174 move-only count was not a
production forecast: two of those requests are excluded Doubles, leaving 172.
Admission additionally proves 18 formerly overlapping requests through existing
conditional ability/item slots (14 ability-reason occurrences: Swarm 8, Overgrow 4,
Iron Fist 2; six item-reason occurrences: Life Orb, Miracle Seed, Quick Claw two
each; two requests carry both). Thus actual authorization unlocks **172 + 18 = 190**.
No independent gate was weakened to obtain that result.

Six drain requests remain refused:

- Amy and May slot 1 Absorb, both reference teams: Doubles drain excluded.
- Jo and Zoe slot 1 Giga Drain, both reference teams: Doubles drain excluded.
- Janine PostOBC slot 3 Leech Life, both reference teams: attacker Infiltrator
  remains `HNS_ABILITY_EFFECT_NOT_MODELLED` / UNKNOWN.

`report_drain_coverage.py` reads the immutable starting artifact through Git and
checks identical request keys, inventory, team and lead definitions. It generates
`move-coverage-slice-2.json`, including request-level old/new tiers/reasons/causes,
remaining refusals, gains and current remaining-family ranking. Reproduce/check:

```sh
DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh test
python3 tools/hns-calc-census/report_drain_coverage.py
python3 tools/hns-calc-census/report_drain_coverage.py --check
```

The next bounded candidate is **Earthquake/Bulldoze**: 294 blocked requests,
preliminary move-only 202, requiring exact underground/semi-invulnerable and
Grassy Terrain source predicates. Repeated strikes rank higher numerically
(392 / 336) but need a separate multi-hit output contract. Explosion (188 / 168)
is another bounded candidate needing Damp/execution and modern Defense proofs.
These are selection counts, not promised unlocks.

## Validation and hardware

Final non-generating `./ci.sh all` passes, including native readers, calculator,
Kotlin regressions and the debug APK. Final `./ci.sh source-check` and
`git diff --check` pass. Full canonical census regeneration, drain report `--check`,
move/layout generator `--verify` and oracle `check` pass. Oracle regeneration and
`verify --order reversed` produce byte-identical artifacts. Final-head GitHub Actions
status and tested-tree identity are recorded in the PR handoff.

Hardware: **NOT_RUN**. Host/oracle evidence is not hardware acceptance.
Safe Thor smoke checklist, using an existing exact-verified H&S 2.0.5 image/state:

- Neutral Singles Absorb/Giga Drain and a contact Leech Life/Horn Leech display.
- Drain Punch punching/contact modifiers and Draining Kiss display damage only.
- Actually observed Triage raises priority; priority immunity/Psychic Terrain
  prevents display when the move cannot execute.
- Big Root and Liquid Ooze preserve the defender selected-hit range; no healing,
  net attacker HP or attacker survival result appears.
- Active Heal Block gives its named refusal; unread state never means false.
- Reload, battle exit, participant replacement and ROM/profile switch invalidate
  stale authority until new complete reads arrive.
- Doubles drain stays refused. Independent unknown state, suppression, active
  gimmicks, unsupported abilities/items and survival caveats stay visible.

Do not edit real saves or use codes solely to construct acceptance states.
