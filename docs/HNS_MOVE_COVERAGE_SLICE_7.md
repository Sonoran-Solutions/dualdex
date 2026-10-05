# H&S 2.0.5 move coverage slice 7 — Brine live HP

Issue #135 follows #123, #125, #127, #129, #131 and #134 without changing
historical acceptance criteria. Actual fetched starting main:
`9f9405c919b98dde000b349724b15ec2f3f2366d` (also the immutable negative-control head).
No intervening main commits existed. Mechanics authority is
PokemonHnS-Development/pokehns-expansion, Release-v2.0.5,
`1f42b74dff0e9fe942419845d040663dd829a973`.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Selection and source contract

Brine was blocked in 74 requests across 33 battles, including 26 lead requests/pairs.
The preliminary move-only upper bound was 72, with two ability-condition and two
item-effect overlaps. These are selection evidence, not required unlock counts.
Repeated strikes remain larger (392 requests / 138 battles) but require another
product result; Gyro Ball (90 / 39) needs additional speed arithmetic.

Exactly **Brine, ID 362, EFFECT_BRINE** is admitted in authoritative Singles.
Its separate `FIXED_SINGLE_HIT_BRINE` category is never ordinary. The generator
freezes the entire pinned MoveInfo initializer and exact enum ID, then extracts:
BP **65**, Water, Special, accuracy 100, TARGET_SELECTED, priority zero, one strike,
no multiHit, contact, punching, additional effects, Sheer Force or unreviewed
damage flags. Production power/type/category continue to come from the existing
source-generated data pack; QuickJS checks power against generated Brine metadata.
No move-name test authorizes the family. QuickJS also rejects forged contact,
Sheer Force and move/ability flags against the frozen empty-flag contract.

`src/data/battle_move_effects.h` maps EFFECT_BRINE to BattleScript_EffectHit.
The pinned selected-hit script performs damage normally. No special execution,
secondary effect, recoil, recovery or future turn is included in this result.
The generator verifies this script mapping and pins the full CalcMoveBasePower
and CalcMoveBasePowerAfterModifiers bodies, preserving placement and ordering.
MoveInfo mutations (including new flags/additional effects), changed ID, HP
comparison/divisor, multiplier/rounding or a moved source branch fail verification.

## Source → operand → boundary → formula → regression

1. MoveInfo supplies exact ID/effect/65/Water/Special and normal selected-hit shape.
2. `CalcMoveBasePowerAfterModifiers`' move-effect switch reads the active
   `gBattleMons[battlerDef].hp` and `.maxHP` (battle_util.c:6594–6597).
3. Existing `hpObserved`, `hp`, `maxHp` carry this pair without JNI growth.
   `CalcRequestBoundary.authoritativeObservedHp` requires exact ROM trust,
   OBSERVED current participant, matching active party slot, `hpObserved` and positive
   maxHP. Native maxHP read failure leaves zero, so an unread maxHP cannot pass
   the positive-maxHP check; no separate maxHP bit or fallback is added.
   Caller `curHP`, party HP, UI text/percentages and caller live-state overrides
   cannot supply it. The boundary binds `defenderHp` / `defenderMaxHp` once.
4. Brine additionally requires both operands, source-domain maxHP 1..65535 and
   **1 <= HP <= maxHP**. Unread/null, maxHP <= 0, negative/zero HP, HP > maxHP,
   stale slot or unverified trust fail closed. `HNS_DEFENDER_HP_UNKNOWN` identifies
   the missing/invalid HP operand after recognition of the move.
5. The exact predicate is **HP <= floor(maxHP / 2)**. Kotlin's integer/source
   division corresponds to QuickJS's `Math.floor(maxHpAtHit / 2)` on the validated
   positive integer domain. No rounded percentage or ceiling-half is used.
6. Source starts Q12 modifier at 4096, multiplies it by **8192** at the Brine
   move-effect slot, accumulates later modifiers in that same accumulator, then
   applies the result to source power 65 with integer half-down rounding.
   Brine is **not implemented as fake 130 BP**. Its factor uses the existing
   half-up Q12 operator before Helping Hand, Gem, Charge, terrain, ability and
   attacker item factors. Weather/stat/other/final stages stay in their shared paths.
7. Production boundary/forged-JSON tests, generator mutations, full-function
   source hashes, an explicit production ordering regression and pinned-engine
   vectors independently exercise the links above.

| Actual defender HP / maxHP | Integer half | Brine factor | Pinned selected-hit range |
|---|---:|---:|---:|
| 100 / 100 | 50 | 4096 | 17–20 |
| 51 / 100 | 50 | 4096 | 17–20 |
| 50 / 100 | 50 | 8192 | 34–40 |
| 49 / 100 | 50 | 8192 | 34–40 |
| 51 / 101 | 50 | 4096 | 17–20 |
| 50 / 101 | 50 | 8192 | 34–40 |

These ranges are measured engine rolls for neutral level-50 Machamp versus
Blastoise (Sp. Atk 151 / Sp. Def 109); the corpus stores every roll. Production
also checks maxHP 3: HP 2 is neutral, HP 1 boosts, proving floor semantics at a
small denominator without measuring a hit that could be capped by fainting.

The result means **damage for the currently observed authoritative battle state**.
It does not predict healing, recoil, switching, Substitute, opponent actions or
future sequencing before Brine eventually executes. A changed live observation
must produce a new request/result; the menu-time calculator cannot predict future HP.
The existing BattleMovePresentationCacheKey includes the complete battler observations,
so changed live HP invalidates cached move results and clears the previous range
while recalculating. CalcTab also refreshes on battler observation changes. No new
cache or UI state is introduced.

## Fixed-hit consumer audit

Every broad consumer in CalcCapabilityPolicy, CalcDataOverrides, DamageCalculator,
HnsMoveAuthority, HnsAbilityContextPolicy, HnsItemContextPolicy, HnsContactRules,
HnsGroupCPolicy and HnsFieldContextPolicy was inspected. Classes below describe
existing authority; no shared ordinary-only predicate was mechanically broadened.

| Class | Consumers and disposition |
|---|---|
| A — safe for Brine | Fixed selected-hit data overrides/serialization; generated contact, punching and ability flags; Group C Water immunity; effective-type/category rewrites; screens, terrain, weather and Utility Umbrella; Water Bubble; Torrent; STAB/Adaptability; stat stages, crit, Life Orb and supported Water boosters. These use the existing ordered paths and their existing live operands. |
| A — safe with shared HP | Multiscale/Shadow Shield consume the same live defender HP/maxHP. Full HP: no Brine boost, defensive factor active. Half HP: Brine boosts, defensive factor inactive. Sturdy below-full proof stays valid; at full HP its existing caveated survival estimate remains. Focus Sash below-full proof stays valid; full-HP Sash and random Focus Band retain their named survival-cap estimate caveats. |
| A — safe with existing conditions | Tera Shell's live species/full-HP predicate and unmodelled relevant form gate remain; Brine does not authorize its distortion. Analytic/current action order and field-holder/suppression authority are unchanged. Non-contact Brine cannot activate Tough Claws or contact Fluffy reduction. |
| B — previous special families only | Recoil/Reckless, drain/Heal Block/Triage/Liquid Ooze/Big Root, Explosion/Damp/HP=0, Earthquake underground/Grassy, Surf/Whirlpool underwater/wrap and status-double/Comatose remain keyed to their original effects/IDs. Brine inherits none of their execution exceptions. |
| C — ordinary only | Doubles selected-target/partner/spread authority and live resist-berry authority remain ordinary-only. Brine does not clear a resist-berry operand solely by joining the broad fixed-hit predicate. |
| D — Brine-specific | Positive valid live defender HP pair, floor-half predicate, separate family, Singles admission, neutral-only semi-state, strict QuickJS descriptor/operand checks and Q12 move-effect modifier. |

**Technician** tests source power against <=60. Brine remains 65 in
CalcMoveBasePower at every HP, so Technician never applies. Brine's factor lives
in the later accumulator. **Sheer Force** is inactive because generated source
metadata proves no qualifying secondary effect. Both controls match neutral
ability damage at full/half HP and at the even/odd threshold boundaries.

Rain, Sun, Utility Umbrella in Rain, Water Bubble, Mystic Water, Splash Plate,
Torrent, STAB/Adaptability, Normalize, Life Orb, Light Screen, fixed critical hits
and Sp. Atk/Sp. Def stages use the existing Water/effective-type pipeline. Brine's
threshold precedes these stages; no special Water formula bypasses them.

Water Absorb, Dry Skin and Storm Drain independently yield zero selected-hit
damage at half HP. Mold Breaker has an engine-only pinned bypass control and retains
its existing production suppression refusal. Ability Shield preserves Water
immunity. Gastro Acid and Neutralizing Gas retain independent production refusal;
the real Gastro Acid arithmetic witness is engine-only. The oracle separates
Water Absorb/Dry Skin recovery and Storm Drain's post-hit stat write from damage.

Active Substitute remains `HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED`; pinned Brine
reads underlying defender HP, never substitute HP. Underground, underwater,
airborne, Phantom Force, Sky Drop and Commander all remain conservative execution
refusals. Brine has no breakthrough flags. Unknown semi-state also refuses.
Doubles and every adjacent HP-scaling/fixed-percent/repeated-strike family stay out.

A temporary source-generated shortcut probe replaced only Brine's Q12 factor with
integer power doubling, leaving the production source and generated asset intact.
All 51 tested Brine compositions were numerically identical under that deliberately
wrong implementation; no practical rounding difference was found in this matrix.
This numerical coincidence does not satisfy the source contract. Full-function
source pinning and production source-order regressions enforce the exact accumulator
stage so later modifier additions cannot silently legitimize the shortcut.

## Oracle, census and validation

Final machine-readable evidence: `tools/hns-damage-oracle/brine-evidence.json`,
`tools/hns-calc-census/brine-negative-control.json` and
`tools/hns-calc-census/move-coverage-slice-7.json`.

The final corpus has **2,265 scenarios: 2,154 modelled / 111 engine-only**.
It adds **51 Brine scenarios**; all **2,214 historical entry objects and canonical
entry lines are byte-for-byte unchanged**. Every Brine roll records actual live
HP/maxHP at the damage boundary. A test-only critical-hit callback captures damaging
cases; an ability-popup callback captures immunities before recovery/stat writes.
Both callbacks only observe HP. The assembler requires the callback's D2 record
before DG (the post-turn fallback emits the opposite order), and rejects pairs that
differ from the intended threshold. Canonical and reversed logs replay to the same
entries under these strict checks. Water Absorb/Dry Skin's maxHP/4 recovery is
validated separately, never encoded as damage; Storm Drain's later stage write also
cannot alter the pre-hit snapshot. All measured damage is below current HP in the
small threshold cases, so fainting cannot cap their rolls.

The unchanged admission-only test compiles at starting main and fails specifically
with **HNS_MOVE_MECHANICS_NOT_MODELLED**, without new enum/field references or
compilation errors. Its canonical run has 1,152 Kotlin tests, exactly one intended
failure; all other Kotlin tests pass. The machine record retains the source hash,
actual assertion text and command exit status.

| Census measure | Before | After |
|---|---:|---:|
| FULLY_MODELLED | 21,070 | 21,144 |
| CAVEATED_ESTIMATE | 462 | 462 |
| REFUSED | 2,746 | 2,672 |
| Fully displayable lead pairs | 564 / 1,302 | 582 / 1,302 |
| Displayed lead requests | 7,398 / 8,450 | 7,424 / 8,450 |

Population remains **651 trainer battles / 24,278 eligible requests**, with identical
inventory, teams, moves, directions, lead definition and neutral runtime assumptions.
All **74 Brine requests** move REFUSED → FULLY_MODELLED; **0** become caveated;
**0** remain refused with changed causes; **0** remain refused overall. **33 battles**
gain results, 18 lead pairs and 26 lead requests become displayable. Blank battles:
**0 before / 0 after**. Outside-slice transitions: **0**.

Both previously overlapping requests belong to TRAINER_AKALA_SWIMMER_3_HNS,
party slot 2, Kingdra, trainer-to-reference against ref-physical and ref-special.
Attacker **Sniper (97)** clears through `sniper_noncritical_hit`: the fixed noncrit
input proves its extra critical multiplier inactive. Attacker **Scope Lens (471)**
clears through `fixed_crit_stage_item`: it changes only critical-hit probability,
which cannot affect the fixed selected-hit critical flag. No global ability/item
classification is changed. No Brine refusal causes remain in this neutral census;
live unsupported states remain refused as described above.

Validation passed: canonical full census generation, `./ci.sh all`, move-generator
verification and 39 mutation tests, 68 oracle infrastructure tests, all six Brine
admission/production tests, corpus/evidence/slice-report non-generating checks,
canonical and reversed pinned-engine generation, and `git diff --check`. The final
canonical run has 1,157 passing Kotlin tests and 2,265 exact sixteen-roll oracle
matches, with zero registered divergences. Final `./ci.sh source-check` also passed, including the pinned upstream
cross-checks and Kotlin non-generating validation.

## Remaining limits and safe Thor checklist

Hardware: **NOT_RUN**. No release/signing work; no real save is modified to create
fixtures. H&S remains ESTIMATED. No future HP/action prediction, hit/KO probability,
Substitute damage, Doubles expansion, generic HP scaling, repeated strikes or
another ROM is admitted. Recommend **Facade** as the next bounded family (20 blocked requests / 10 battles),
with the existing attacker status operand and the same move-effect Q12 slot; repeated
strikes still need a separately approved multi-hit result contract.

On Thor, using naturally reachable state, check Brine at full HP, just above half,
exactly half, below half, and odd-maxHP floor-half if practical; Rain/Sun; Water
immunity; Multiscale/Shadow Shield threshold interaction if available; then battle
transition/reload to verify HP never survives into a stale participant. Record
actual observations rather than changing a save solely to manufacture test state.
