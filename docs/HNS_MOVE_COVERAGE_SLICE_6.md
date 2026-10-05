# H&S 2.0.5 move coverage, slice 6 — status-dependent base power

Issue #132 follows #123, #125, #127, #129 and #131 without changing historical acceptance criteria.
Actual fetched starting main: `edc26cc476cabd0fe2ba52ac892741a1883ac59d`.
Authority: PokemonHnS-Development/pokehns-expansion,
`1f42b74dff0e9fe942419845d040663dd829a973`, Release-v2.0.5.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen family and source generation

| Move | ID | Power | Type/category | Source status mask | Contact | Additional effect | Sheer Force |
|---|---:|---:|---|---|---|---|---|
| Smelling Salts | 265 | 70 | Normal/Physical | PARALYSIS (64) | yes | REMOVE_STATUS | no |
| Wake-Up Slap | 358 | 70 | Fighting/Physical | SLEEP (7) | yes | REMOVE_STATUS | no |
| Venoshock | 474 | 65 | Poison/Special | PSN_ANY (136) | no | none | no |
| Hex | 506 | 65 | Ghost/Special | ANY (4351) | no | none | no |
| Barb Barrage | 767 | 60 | Poison/Physical | PSN_ANY (136) | no | POISON, 50% | yes |
| Infernal Parade | 772 | 60 | Ghost/Special | ANY (4351) | no | BURN, 30% | yes |

All six have EFFECT_DOUBLE_POWER_ON_ARG_STATUS, TARGET_SELECTED, priority zero,
one strike, no punching flag and no pre-attack additional effect. The separate
FIXED_SINGLE_HIT_STATUS_DOUBLE category admits only authoritative Singles with
two observed participants. None is ordinary. The generator pins the entire MoveInfo
initializer, resolves power only with source-verified GEN_LATEST configuration,
and emits Kotlin masks plus JSON metadata. Unknown fields and changed arguments,
flags, additional effects, preAttackEffect or Sheer Force classification fail closed.
Function body checks pin CalcMoveBasePower, CalcMoveBasePowerAfterModifiers and
SetMoveEffect. Existing source checks preserve the damage-before-additional-effects script.

The source-generated status constants are SLEEP=7, POISON=8, BURN=16, FREEZE=32,
PARALYSIS=64, TOXIC_POISON=128, TOXIC_COUNTER=3840, FROSTBITE=4096,
PSN_ANY=136 and ANY=4351. ANY excludes toxic counter bits. Constant drift fails
source validation; production consumes generated constants.

Selection: 102 move-blocked requests, 48 battles, 28 lead requests/pairs;
94 was only a preliminary move-only upper bound. Repeated strikes rank higher
(392 requests in 138 battles), but require a new result contract. This slice keeps
one selected hit × sixteen rolls.

## Authority, validation and arithmetic

The existing statusObserved/status1 reader and participant tuple carry the operand.
CalcRequestBoundary rebinds defenderStatus1 only from exact-trusted OBSERVED,
slot-matched live state. No JNI growth, duplicate status field, display-string inference,
party inference or caller status override is introduced.

HNS_DEFENDER_STATUS_UNKNOWN hard-refuses unread, negative, undefined-bit and
out-of-domain words, toxic counter without Toxic Poison, and mutually exclusive
primary status combinations. Sleep is the source three-bit turn count 1..7;
neutral, individual primary statuses, Toxic Poison with counter 0..15 and frostbite
are valid. Validation preserves the exact word. Existing attacker status gates remain.

CalcMoveBasePower computes `(rawStatus | ComatoseSleepBits) & sourceMask` and,
when nonzero, doubles integer power before any base-power modifier. QuickJS verifies
family, exact move ID/effect/mask/source power, Singles, fixed-hit authorization,
status domain, effective ability identity and absence of Substitute. The boundary
owns all these fields. Caller base-power/type/category overrides are rebound.

Technician therefore sees neutral Barb Barrage/Infernal Parade at 60 (eligible),
and matching status at 120 (ineligible). Sheer Force consumes the later base-power
modifier slot, including on matching-status Barb Barrage/Infernal Parade; their
secondary effects cannot create the pre-hit condition.

Comatose ID 213 contributes source sleep bits only locally to this family's power
predicate. Wake-Up Slap, Hex and Infernal Parade double on raw zero; Smelling Salts,
Venoshock and Barb Barrage do not. The raw status is never synthesized or modified.
Attacker Comatose is irrelevant to this selected-hit predicate. Source metadata pins
its inability to be copied, swapped, traced, suppressed or overwritten; global
Comatose classification remains UNSUPPORTED_DAMAGE_RELEVANT.

Smelling Salts and Wake-Up Slap read matching status before damage, then remove
that matching raw status afterward. Comatose Wake-Up Slap leaves raw zero and its
ability intact. Barb Barrage poison and Infernal Parade burn are post-hit effects;
forced secondary-success execution tests separate pre-hit status, damage, and final
status, including Sheer Force suppression. Failed post-hit changes never become
zero damage vectors.

Active Substitute remains refused. The source exception suppresses remove-status
doubling when DoesSubstituteBlockMove is true; separate engine execution comparisons
cover the exception without admitting Substitute damage.

## Shared fixed-hit consumer audit

| Consumers | Classification and disposition |
|---|---|
| HnsMoveAuthority: type/category rewrites; Group C immunity | Valid fixed selected hit; source effect has no excluded ate/type branch. Status power does not bypass immunity. |
| Contact, Tough Claws, Fluffy, Long Reach, Punching Glove | Valid with generated contact/punching metadata. Only the two removal moves contact. |
| Technician | Status-specific pre-modifier power branch; request-local decision also accounts for doubling. |
| Sheer Force, Iron Fist, Strong Jaw, Mega Launcher, Sharpness | Valid generated metadata; secondary presence alone does not imply Sheer Force. |
| Screens, terrain, weather, HP thresholds, resist berries, final items | Valid shared damage stages, with existing authoritative operand requirements. |
| Analytic/current action, field holders and suppression | Valid shared topology/authority requirements; no new inferred state. |
| Recoil/Reckless, drain/Liquid Ooze/Big Root, Explosion/Damp, underwater/wrap | Earlier-family-specific gates remain keyed to their exact effect/IDs. |
| Doubles partner/spread authority | Ordinary-only; new family stays Singles-only. |
| Defender status, Comatose | New family-specific validation/predicate and narrow ability proofs. |
| Substitute, attacker status and persistent volatiles | Existing hard refusal boundaries retained. |
| Post-hit abilities/items | Existing selected-hit-only proofs remain local; no future-turn state is displayed. |

All broad fixed-hit callers in CalcCapabilityPolicy, HnsAbilityContextPolicy,
HnsItemContextPolicy, HnsContactRules, HnsMoveAuthority, HnsGroupCPolicy and
DamageCalculator were inspected. The enum's broad predicate supplies damage shape;
ordinary-only admission is not mechanically broadened.

## Evidence

The oracle adds 126 scenarios covering every move in both directions, all primary
status masks, Toxic Poison with a nonzero counter, Comatose positives/negatives,
Technician and Sheer Force neutral/matching branches, screens, crits, stages,
Life Orb and Normalize. Pre-hit raw status and effective ability identity are
verified inside the real damage-boundary callback. Post-hit removal and residual
HP changes are captured separately. Pinned GEN_3 burn/frostbite residual is maxHP/8;
large Toxic residual HP-bar animation is source-capped at 10,000 while the full
HP delta is checked independently. Neither residual can replace the selected hit.
The execution suites have seven removal/Comatose/Substitute comparisons and six
forced secondary/Sheer Force cases. Source-generated mask mutation tests,
production boundary and forged JSON regressions cover admission and ordering.
Final corpus: **2,214** exact sixteen-roll matches (**2,105 modelled / 109 engine-only**),
**zero divergences**. All **2,088** starting-head corpus entry lines and objects are
unchanged; canonical regeneration and full reversed verification are byte-identical.
Machine-readable execution/integrity evidence: `tools/hns-damage-oracle/status-double-evidence.json`.

| Census measure | Before | After |
|---|---:|---:|
| Fully modelled requests | 20,968 | 21,070 |
| Caveated requests | 462 | 462 |
| Refused requests | 2,848 | 2,746 |
| Fully displayable lead pairs | 554 / 1,302 | 564 / 1,302 |
| Displayed lead requests | 7,370 / 8,450 | 7,398 / 8,450 |

The population remains **651 battles / 24,278 requests**. **102** requests move from
REFUSED to FULLY_MODELLED, **48** battles gain results, **10** lead pairs and **28**
lead requests become displayable. Family requests still refused: **0**; unexplained
outside-slice transitions: **0**; blank battles: **0 before and after**. Eight old
Blaze/Swarm unknown-condition causes clear because authoritative Ghost/Poison move
types cannot activate those attacker abilities. Global classifications are unchanged.
The frozen comparison is `tools/hns-calc-census/move-coverage-slice-6.json`.
Starting-head admission-only test compiles unchanged at the immutable starting SHA;
all six requests refuse specifically with HNS_MOVE_MECHANICS_NOT_MODELLED.
Machine-readable evidence: tools/hns-calc-census/status-double-negative-controls.json.

## Validation

- `DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh all`: PASS;
  1,151 Kotlin/JVM tests, 3,127 calculator assertions, native/tooling suites and debug APK.
- `./ci.sh source-check` with the pinned upstream and ARM preprocessor: PASS.
- Full canonical engine regeneration and reversed-order verification: PASS.
- Move/status-constant generator verification, mutation tests and ability audit: PASS.
- Oracle corpus and both slice reports in non-generating check mode: PASS.
- `git diff --check`: PASS.

Final-head GitHub Actions and synthetic-merge tree evidence are recorded in the PR
after the final push; local validation does not substitute for those checks.

## Remaining scope and safe Thor checklist

No multi-hit, Facade, Brine, Gyro Ball, generic status chance/removal UI, future turns,
Doubles expansion, Substitute damage or general Comatose support. No release signing.
Recommend **Brine** next: 74 blocked requests in 33 battles, using an authoritative
defender HP/maxHP threshold within the existing fixed-hit result. Repeated strikes
remain the largest family (392 requests, 138 battles), but need a separate product/result
contract; Gyro Ball and Rollout each block 90 requests in 39 battles and require
additional speed arithmetic or history authority.

On Thor, without modifying a save to manufacture evidence, check neutral/statused
Hex; Venoshock on Poison/Toxic; Smelling Salts on paralysis; Wake-Up Slap on sleep
and Comatose; neutral/statused Barb Barrage and Infernal Parade; a Technician
threshold case; a Sheer Force case; and reload/battle transitions for stale status.
Hardware is not required for software completion.
