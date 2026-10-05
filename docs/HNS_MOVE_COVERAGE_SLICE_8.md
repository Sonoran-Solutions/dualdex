# H&S 2.0.5 move coverage slice 8 — Gyro Ball effective Speed

Issue [#137](https://github.com/Sonoran-Solutions/dualdex/issues/137).
Actual fetched starting main: `0c821f012b0e73e8cc50a7a8074e8aa2dcb623cc`;
it equals the requested immutable negative-control head. No intervening commits.
Source authority: PokemonHnS-Development/pokehns-expansion,
Release-v2.0.5, `1f42b74dff0e9fe942419845d040663dd829a973`.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen move and selection

Exactly Gyro Ball (360), EFFECT_GYRO_BALL, joins the separate
FIXED_SINGLE_HIT_GYRO_BALL family; it never joins the ordinary set.
Source MoveInfo: power **1 (placeholder)**, Steel, Physical, accuracy 100,
TARGET_SELECTED, priority zero, one strike, contact and ballistic, not punching,
no additional effect, Sheer Force false. The complete MoveInfo initializer is
hash-pinned, including omitted/default fields. EFFECT_GYRO_BALL maps to the
selected-hit script. The generated descriptor preserves ballisticMove independently
of contact. Damage never uses the placeholder as the effective power.
Calculator, party and battle move displays label that placeholder **Variable**.

The unchanged starting population contains 90 blocked Gyro Ball requests, 39
battles, 42 affected lead pairs/requests. The preliminary 78-request upper bound
is selection evidence, not a target. Repeated strikes (392 / 138 battles) need
another result contract; neither those moves nor Facade are admitted here.

## Source arithmetic and authority

`HnsEffectiveSpeedAuthority` resolves one battler from the boundary-owned live
request and the shared effective ability/hold-effect and global weather authorities.
It returns a source-ordered integer Speed or null with a specific diagnostic.
The capability layer adds hard refusal HNS_EFFECTIVE_SPEED_UNKNOWN when either
participant's required authority fails. There is no guessed neutral result.

QuickJS independently repeats the same source arithmetic from original bound
operands. No caller-derived effective Speed, action-order Boolean, UI stat estimate,
party stat, or cached final Speed is serialized. Generated numeric ability/name and
item/hold-effect identities are checked before Speed arithmetic. The ABI is unchanged.

Source order in GetBattlerTotalSpeedStat:

1. Raw BattlePokemon.speed times the source stage numerator, divided by its denominator.
2. Weather abilities, conditional on the shared HasWeatherEffect result.
3. The source other-ability `if` / `else if` chain.
4. Gen III Speed badge using Q12 **4506**, integer half-down `(4506*S+2047)/4096`.
5. Effective held-item modifier.
6. Tailwind ×2.
7. Paralysis /4 except Quick Feet (source B_PARALYSIS_SPEED = GEN_3).
8. Swamp /4.

Every division floors at its own source stage. Effective Speed can be zero.
Gyro Ball then uses integer `BP = 1` when attacker Speed is zero; otherwise
`BP = min(25*defenderSpeed/attackerSpeed + 1, 150)`, with integer division.
QuickJS uses BigInt for the ratio, rather than a floating ratio/table or modern helper.
Validated raw u16 Speed and source modifiers keep every intermediate integer exact.

This dynamic power enters the existing CalcMoveBasePowerAfterModifiers path.
Technician checks the computed power: **60 boosts, 61 does not**. Screens, type,
crit, Attack/Defense stages, STAB, weather and final modifiers remain in their
existing ordered slots.

| Operand | Authority / failure boundary |
|---|---|
| Raw Speed / stage, both battlers | Exact ROM trust, observed active slot match, existing CalcRawStats.speed and index 3 of live statStages; raw 1..65535, stage -6..6. No new JNI fields. |
| Ability | Existing observed effective ability. Unknown/suppressed unresolved identities retain existing independent refusals. |
| Hold effect | HnsHoldEffectAuthority; current numeric item, Magic Room, Embargo, Klutz and global suppression. Never raw item-name arithmetic. |
| Weather effectiveness | HnsFieldAbilityAuthority's Cloud Nine/Air Lock result from both observed living Singles participants. Clear/Rain/Sun only; unsupported weather remains refused. |
| Status | Both raw status1 words must be observed **zero** in production. Positive/unknown status refuses; no sleep/freeze execution expansion. |
| Field | Agreed exact live field word; Surge Surfer / Quark Drive read the raw Electric Terrain bit, independently of groundedness. Gyro-local field policy retains this bit. |
| Slow Start | Existing group-D observed timer for either holder, in 0..7; positive divides Speed by two. Attacker Attack modifier remains independent. |
| Paradox | Existing transformed, Booster Energy and boosted-stat operands; activation follows source Sun/HasWeatherEffect or raw Electric Terrain. Only Speed stat 3 receives integer ×150/100. Existing Attack/Defense capability remains independent. |
| Unburden | Activation bit is not transported. An effective production Unburden holder always refuses. Item absence/history cannot authorize it. |
| Badge | Boundary-owned badgeBoostSpe. Opponent exclusion is proven from exact, slot-matched live battler ID, never UI side or badge ownership inference. Missing player result refuses. |
| Species / gimmick | Current observed battle species/transformed bit for Ditto Quick Powder; current gimmick for Choice Scarf, with Dynamax excluded. Existing general gimmick policy remains. |
| Side word, both battlers | Existing observed per-side gSideStatuses; new attackerSideStatuses binding, existing defender word. Tailwind 1<<4 and Swamp 1<<10 are Gyro-local Speed operands. Other defender-side bits retain their policy. |

Swift Swim in Rain and Chlorophyll in Sun double Speed only when weather effects
are active and the holder's effective effect is not Utility Umbrella. Suppressed
weather and active Umbrella make those branches inactive. Sand Rush/Slush Rush
are inactive in clear/Rain/Sun; active Sand/Hail/Snow remains refused.
Quick Feet is proven inactive by observed zero status. Neutral Slow Start and
transformed/otherwise inactive Paradox cases use their exact negative predicates.

Macho Brace, every effective Power Item and Iron Ball divide Speed by two.
Choice Scarf multiplies by 150/100 outside Dynamax. Quick Powder doubles only
current Ditto when observed untransformed; non-Ditto is an exact negative control.
Suppressed effects resolve to NONE, retaining raw identity separately.

Trick Room, Quick Claw, Quick Draw, Stall, Lagging Tail and Full Incense do not
enter GetBattlerTotalSpeedStat. They cannot change the ratio. Analytic's current
selected-action requirement remains separate; no general turn-order simulation
is introduced.

## Fixed-hit consumer audit

| Class | Disposition |
|---|---|
| A — safe for Gyro | Source data overrides, serialization, effective type/category rewrites; generated Bulletproof ballistic immunity and contact; Tough Claws, Fluffy; Steelworker/Steely Spirit; Reflect, STAB/Adaptability, Life Orb and Metal Coat; ordinary weather damage, crit and Attack/Defense stages. They consume computed power in existing later stages. |
| A — independent conditions remain | Analytic still needs its current-action proof. Sand Force's supported-weather/inactive proof remains. Shared suppression, Ability Shield, persistent volatiles, terrain damage applicability, status execution and badge requirements are retained. Positive/unknown semi-state and Substitute remain refused. |
| B — earlier families only | Recoil/Reckless, drain/Triage/Heal Block, Explosion/Damp/HP=0, underground Earthquake, underwater Surf/Whirlpool, status-double masks and Brine live-HP predicates. Gyro inherits none of their special execution. |
| C — ordinary only | Doubles selected-target/partner/spread contract and current live resist-berry authority. Gyro does not clear an unsupported berry or Doubles by joining the broad fixed-hit set. |
| D — Gyro-specific | Separate generated family, both exact effective Speeds, integer zero/cap/ratio arithmetic, dynamic Technician threshold, side-word Speed operands and raw Electric Terrain retention. |

Bulletproof is zero damage with neutral selected-hit execution. Mold Breaker and
Ability Shield retain their existing suppression semantics. Long Reach's contact
removal is measured engine-only and retains its independent production limitation.
No global Bulletproof/contact gate is weakened. Gyro has no semi-state breakthrough:
only observed neutral state 0 is admitted. Active Substitute and Doubles refuse.

## Evidence and checks

Machine-readable evidence lives in:

- tools/hns-calc-census/gyro-ball-negative-control.json
- tools/hns-calc-census/move-coverage-slice-8.json
- tools/hns-damage-oracle/corpus.json (gyro-ball-* records)

The minimal admission test compiles unchanged on starting main and asserts exactly
HNS_MOVE_MECHANICS_NOT_MODELLED. Its immutable-head full canonical test run passes;
the evidence records the source hash and result.

Each new engine scenario records actual raw Speed, actual stage, actual source
GetBattlerTotalSpeedStat result, side word, badge result, Unburden activation and
source CalcMoveBasePower result. The engine's static power function is exposed by
a TESTING-only read accessor; no arithmetic is replaced. The critical-hit boundary
captures damaging controls; the existing pre-ability-popup observer handles
immunity before damage exits. A separate test-harness Quick Claw RNG control keeps
its proc independent from all sixteen damage-roll inputs.

Unburden and paralysis/Quick Feet are engine-only witnesses. Paralysis is explicitly
installed at the damage boundary after execution-prevention checks to isolate the
source Speed /4 and Quick Feet exception; it is not an execution-probability test.
Production never transports an Unburden activation claim and never admits positive
status. The arithmetic engine can consume these disclosed oracle operands, while
the live request boundary remains narrower.

The assembler and corpus checker independently recompute dynamic power from the
source-observed totals. Historical entry objects and canonical entry lines must
remain byte-identical. Canonical and reversed fresh engine runs must produce the
same final corpus. No expected damage or total Speed is supplied by the scenario
matrix. The source mutation check refuses formula, zero branch, cap, order/operator,
weather/item predicate, paralysis, badge, stage-table and MoveInfo flag mutations.

The full production-boundary regression binds every modelled new engine vector
and compares exact totals and all sixteen damage rolls through the shipped bundle.
Missing/out-of-domain raw values, unread stages/status, stale slots, positive
status and Unburden refuse. Dynamic BP 60/61 and the stage-to-zero branch also
have direct regressions.

Census population, trainer inventory, reference teams, move choices/directions and
lead definition remain unchanged at **651 battles / 24,278 requests**. The slice
report compares the immutable starting census, lists every remaining refusal/cause,
every transition and affected battle, and asserts zero outside-slice transitions.
The report, rather than the preliminary upper bound, determines unlock counts.

## Thor checklist and remaining scope

Hardware: **NOT_RUN**. In a normal unmodified save, check neutral Gyro Ball, slow
attacker/fast defender, fast attacker/slow defender, Speed stages, Iron Ball,
Choice Scarf, Tailwind, Rain + Swift Swim, and naturally available Electric Terrain
+ Surge Surfer, Bulletproof and Technician thresholds. Transition/reload battles
to ensure stale Speed state is never reused. Do not modify a real save for test state.

Remaining limits: neutral production status only, no Unburden activation ABI,
no Sand/Hail/Snow expansion, no Doubles, no semi-state breakthrough/Substitute,
no accuracy/KO probability or future-turn prediction. No release/signing work.
The next bounded candidate is Electro Ball, using the same effective-Speed
operands with a separately reviewed formula; repeated strikes still need a new
result contract. Facade remains a small later cleanup.

## Measured results

The final pinned-engine matrix adds **76 scenarios** (70 modelled, 6 engine-only).
All **2,265 historical entry objects and canonical entry lines are unchanged**.
Final corpus: **2,341 total / 2,224 modelled / 117 engine-only**, all exact
sixteen-roll matches, **zero registered divergences**. Fresh canonical and reversed
engine runs are byte-identical. `tools/hns-damage-oracle/gyro-ball-evidence.json`
records independently reassembled engine replay and every observed Speed/BP pair.

| Source control | Actual attacker / defender total Speed | BP | Damage range |
|---|---:|---:|---:|
| Neutral BP 60 | 100 / 236 | 60 | 16–19 |
| Technician BP 60 | 100 / 236 | 60 | 23–28 |
| Neutral / Technician BP 61 | 100 / 240 | 61 | both 16–19 |
| Raw 1, stage -6 attacker | 0 / 100 | 1 | 1–1 |
| Raw 1, stage -6 defender | 100 / 0 | 1 | 1–1 |
| Raw 103, Tailwind then Swamp | 51 / 100 | 50 | 13–16 |
| Quick Claw proc / inactive | both 100 / 100 | both 26 | both 7–8 |
| Bulletproof | 100 / 100 | 26 | all sixteen zero |

Quick Claw's source-observed `lastToMove` changes from 1 (inactive) to 0 (proc),
while both source effective Speeds, BP and all sixteen damage rolls stay equal.

The rounding witness would give Speed 50/BP 51 if Swamp preceded Tailwind;
source and production give Speed 51/BP 50. Player Speed badge gives 110 from
100; an opponent attacker stays 100 even with the player's badge set (the player
defender correctly becomes 110). Trick Room and all reviewed turn-order-only
controls preserve effective Speed/BP. Stall and Quick Draw have a Gyro-local
selected-hit proof; no global execution-policy clearance is introduced.

| Unchanged census metric | Starting | Final |
|---|---:|---:|
| FULLY_MODELLED | 21,144 | 21,232 |
| CAVEATED_ESTIMATE | 462 | 462 |
| REFUSED | 2,672 | 2,584 |
| Fully displayable lead pairs / 1,302 | 582 | 614 |
| Displayed lead requests / 8,450 | 7,424 | 7,464 |
| Blank battles | 0 | 0 |

All **90** Gyro Ball requests were evaluated: **88 REFUSED → FULLY_MODELLED**,
zero → CAVEATED, **2 REFUSED → REFUSED with updated causes**, **38 battles gain
requests**. The two remaining keys are Duff and Eda's Doubles trainer-to-reference
requests, one per reference team. Their complete causes are
HNS_MOVE_MECHANICS_NOT_MODELLED (unsupported format contract) and
HNS_EFFECTIVE_SPEED_UNKNOWN (no reviewed Singles Speed authority in that format).
Population stays **651 battles / 24,278 requests**; inventory, teams, moves,
directions, neutral assumptions and lead definition are unchanged.
**Zero outside-slice transitions**.

The measured 88 exceeds the preliminary 78 because all twelve overlapping
ability/item blockers clear through existing request-local fixed-hit proofs:

- **Eight attacker Sturdy requests** (Bugsy 2, Bugsy post-OBC, Irwin 4 and Irwin 5,
  both references): Sturdy is a target survival/immunity predicate, contributes
  no Speed modifier, and cannot alter this attacker's selected hit. The existing
  attacker-role proof now applies to the recognized fixed-hit family.
- **Two attacker Steely Spirit requests** (Jasmine post-OBC, both references):
  exact two-battler Singles, effective Steel move type and observed holder prove
  the source Steel base-power modifier; no partner multiplier is guessed.
- **Two attacker Silk Scarf requests** (Whitney 2, both references): exact active
  hold effect boosts Normal, while the authoritative current move type is Steel.
  The existing type-mismatch proof makes this effect inactive; the Speed helper
  independently has no Silk Scarf branch.

The report lists every transition, remaining cause and affected lead pair. It is
checked against the immutable starting census rather than an assumed unlock target.

Validation: canonical full census generation, `./ci.sh all`, `./ci.sh source-check`,
move-generator `--verify` (including the full Speed source contract), 23 source
mutations, corpus check, evidence `--check`, slice report `--check`, and
`git diff --check` pass. The final checks are non-generating; generated artifacts
are current. Exact pushed-head Actions and any synthetic-merge tree equivalence
are recorded in the PR review handoff after the remote run finishes.
