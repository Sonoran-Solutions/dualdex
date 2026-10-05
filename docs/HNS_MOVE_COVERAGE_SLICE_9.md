# H&S 2.0.5 move coverage slice 9 — Electro Ball

Issue [#139](https://github.com/Sonoran-Solutions/dualdex/issues/139).
Starting main: `9ab694b99915f13956f0cf652c792bdc92d10409`, the actual merge of
PR #138. Its tree `6de949f04626fd477d4a8dfe1355b656a30ab3bf` equals reviewed
slice-8 head `b88a67431d9f93bd388ff84456ab91252063bf03`; no intervening work.
Mechanics authority: PokemonHnS-Development/pokehns-expansion, Release-v2.0.5,
`1f42b74dff0e9fe942419845d040663dd829a973`.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen move and integer power

Only Electro Ball (486), EFFECT_ELECTRO_BALL, joins its own
FIXED_SINGLE_HIT_ELECTRO_BALL category. MoveInfo is hash-pinned in full:
placeholder power 1, Electric, Special, accuracy 100, PP 10, TARGET_SELECTED,
priority 0, one strike, noncontact, ballisticMove=true, not punching, no
additional effect, Sheer Force=false. The selected-hit battle script is checked.
The move stays outside the ordinary set. Starting census opportunity was 48
blocked requests / 22 battles / 20 affected lead pairs / 20 lead requests; the
no-other-known-blocker estimate was 44, an upper-bound selection metric. Generated reviewed-variable-power IDs
are exactly Gyro Ball 360 and Electro Ball 486; all shared battle, calculator and
party `powerDisplay` consumers show **Variable**. Other source-power-1 moves do
not gain that presentation.

For authoritative effective Speeds A and D, with D > 0:

`BP = [40, 60, 80, 120, 150][min(A // D, 4)]`

The table is extracted and pinned from `sSpeedDiffPowerTable`; the complete
`CalcMoveBasePower` source helper is hash-pinned by the existing Speed contract.
The JS ratio uses unsigned nonnegative integer operands and BigInt division.
BP 40 covers ratio 0, BP 60 ratio 1, BP 80 ratio 2, BP 120 ratio 3, BP 150 ratios
4 and above. Dynamic power enters the existing later Q12 modifier pipeline.

## Zero divisor is a compatibility boundary

Positive raw Speed 1 at stage -6 gives source total Speed 0. With attacker total
0 and defender total 100, the real Electro Ball power accessor observes BP40;
normal damage rolls are 44–52. This request is admitted.

The pinned Electro Ball branch divides directly by defender total Speed with no
guard. Production resolves both Speeds before QuickJS. Unknown operands retain
HNS_EFFECTIVE_SPEED_UNKNOWN. Exactly zero defender Speed refuses with
**HNS_ELECTRO_BALL_DEFENDER_SPEED_ZERO**: the pinned formula has no reviewed
zero-divisor branch. The authorized-execution regression asserts zero calculator
invocations; a forged direct JS request also fails closed. No clamp, guessed cap
or upstream repair is introduced.

The historical `gyro-ball-defender-zero` source observer proves defender total
Speed 0 using raw 1/stage -6. This evidence never calls Electro Ball. The new
TESTING-only accessor calls original CalcMoveBasePower for safe requests; the
harness checks defender total Speed before the callback or later damage path.
No engine test intentionally executes division by zero.

## Shared authority and request-local policy

Electro Ball consumes unchanged **HnsEffectiveSpeedAuthority**; no Speed arithmetic
is duplicated. Both bound neutral status1 words, raw u16 Speed, live Speed stage,
badge verdict, exact slot identities, effective abilities and effective hold
effects, weather authority, side and field words are required. No caller-derived
final Speed is serialized or trusted. JS independently resolves the same original
operands through the existing `hnsEffectiveSpeedAuthority`.

Only the generated set {360,486} extends the existing Speed-related ability,
item and field gates. Historical Gyro rule IDs remain intact; equivalent Electro
rule IDs are reviewed in the ability/item/field audit inputs and regenerated.
Gyro arithmetic, zero-attacker BP1, Technician 60/61, UI and corpus entries are
unchanged; the census report checks every Gyro request unchanged.

| Consumer | Electro Ball disposition |
|---|---|
| Swift Swim / Chlorophyll | Rain/Sun double Speed in the existing source order. Utility Umbrella disables each holder's branch; Cloud Nine/Air Lock suppress global weather. |
| Sand Rush / Slush Rush | Exact inactive proofs under supported clear/Rain/Sun. Sand/Hail/Snow still refuse. |
| Quick Feet | Observed neutral status proves inactivity. Positive status remains engine-only/refused. |
| Slow Start | Existing observed timer divides Speed when active; separate Attack/category rules remain. |
| Protosynthesis / Quark Drive | Existing transformed, Booster Energy and boosted-stat operands; only selected Speed receives ×150/100. Quark activation reads raw Electric Terrain. |
| Surge Surfer | Raw Electric Terrain bit doubles Speed regardless of groundedness. |
| Unburden | Activation is not transported; always HNS_EFFECTIVE_SPEED_UNKNOWN in production. |
| Speed items | Shared hold-effect authority: Macho Brace/Power Items/Iron Ball ÷2; Choice Scarf ×150/100 outside Dynamax; Quick Powder ×2 for untransformed Ditto only. Magic Room/Klutz/Embargo suppression remains independent. |
| Quick Claw / Quick Draw / Stall / Lagging Tail / Full Incense / Trick Room | No source total-Speed modifier; existing request-local proofs and independent Analytic requirements remain. No turn-order simulation. |
| Technician | Uses computed BP: 40 and 60 boost; 80/120/150 do not. Placeholder 1 never decides the threshold. |
| Bulletproof | Ballistic flag independently produces source-proven zero damage. Noncontact does not remove ballistic immunity. |
| Volt Absorb / Motor Drive / Lightning Rod | Existing Group C zero-damage immunity; Mold Breaker family, Ability Shield and suppression boundaries remain. Unshielded bypass witness remains engine-only under the existing production policy. |
| Charge | Observed chargeTimer doubles the later effective-Electric BP accumulator; it never changes Speed or the bucket. |
| Electric Terrain | Exact Speed plus the separate authoritative attacker terrain-applicability predicate when final type is Electric; only grounded damage gains ×1.3. |
| Hadron Engine | Existing Special Attack authority remains independent of Speed and terrain damage. |
| Type rewrites | HnsMoveAuthority applies existing Normalize/other admitted rewrites. Dynamic BP is independent of final type; Charge/terrain follow effective type. Existing Electrify and Ion Deluge field/volatile paths retain their exact authority and boundaries. |
| STAB / Adaptability / Magnet / Life Orb / Light Screen / crit / Sp. Atk and Sp. Def stages | Existing ordered fixed-hit pipeline after dynamic BP. |
| Aftermath | Existing post-hit-only proof; cannot modify the displayed selected hit. |
| Sheer Force | Generated false; no additional effect, no damage boost. |
| Resist berries | Existing ordinary-only live berry authority remains restricted; no new blanket clearance. |
| Substitute / semi-state / Doubles | Active Substitute, unknown/nonneutral semi-state, Doubles refuse. No breakthrough or hit/KO probability contract is added. |

## Independent terrain and modifier evidence

Source-observed Surge Surfer grounded and Air Balloon ungrounded controls both
have A=200, D=100 and BP80. Grounded rolls are 110–130; ungrounded rolls 84–100.
The grounded Insomnia control has A=100, D=100, BP60 and rolls 82–98. These prove
raw field Speed activation and grounded terrain damage are separate predicates.
A Speed-selected Quark Drive control and a Hadron Engine control retain their
independent stat authorities.

Technician witnesses use A=99/100/200, D=100, source BP40/60/80, with sixteen
rolls checked through the real production boundary. The Charge + Electric
Terrain + Technician witness composes later Q12 modifiers after source BP60.
Every admitted engine vector binds captured runtime/Speed operands and compares
all sixteen shipped-calculator rolls.

## Evidence and validation

- `tools/hns-calc-census/electro-ball-negative-control.json`: unchanged test on
  actual starting SHA; canonical test passes while asserting the sole old blocker
  HNS_MOVE_MECHANICS_NOT_MODELLED.
- `tools/hns-damage-oracle/electro-ball-evidence.json`: unchanged historical entry
  lines, source effective Speeds and BP, terrain/Technician/zero witnesses and
  reversed-order replay.
- `tools/hns-calc-census/move-coverage-slice-9.json`: unchanged population,
  transitions, remaining refusal causes, lead coverage and next-family ranking.

53 new scenarios: 50 modelled, 3 engine-only (Mold Breaker bypass, Unburden,
positive status). Total **2,394: 2,274 modelled, 120 engine-only**, zero registered
divergences. All **2,341 historical entry objects and canonical entry lines**,
including every Gyro entry, remain byte-identical. Canonical regeneration and
fresh reversed-order verification produced byte-identical corpus bytes.
The assembler and schema independently recompute the table index from source
observed totals, never a matrix-supplied expected BP. Source mutation checks
reject MoveInfo ID/effect/type/category/power/ballistic/contact/additional-effect
drift, ratio operand/direction/cap/index/branch drift and table changes, while
existing Speed helper mutation tests cover source order and operands.

Full census population remains **651 trainer battles / 24,278 eligible damaging
requests**, identical trainers, reference teams, directions and lead definition.
There are zero outside-slice transitions, and all Gyro request records are equal.

| Result | Starting main | Slice 9 |
|---|---:|---:|
| FULLY_MODELLED | 21,232 | 21,278 |
| CAVEATED_ESTIMATE | 462 | 462 |
| REFUSED | 2,584 | 2,538 |
| Fully displayable lead pairs / 1,302 | 614 | 630 |
| Displayed lead requests / 8,450 | 7,464 | 7,482 |
| Blank battles | 0 | 0 |

All 48 Electro Ball requests are accounted for: **46 REFUSED → FULLY_MODELLED**,
none to caveated, two still refused. **21 battles** gain results. The preliminary
44-request upper bound was not a target: two Aftermath ability blockers clear
through the existing `after_hit_ability_outside_single_hit` proof: Aftermath runs
after the selected hit and cannot alter its rolls. Four item blockers
clear through existing effective-type proofs (two Miracle Seed off-type and two
Normal Gem off-type). These are request-local negative proofs, not global ability
or item reclassification. The only remaining Electro requests are trainer-to-
reference for both teams at `TRAINER_THOM_AND_KAE_HNS#0`: authoritative Singles
Speed cannot be established for that Doubles battle, so
HNS_EFFECTIVE_SPEED_UNKNOWN and HNS_MOVE_MECHANICS_NOT_MODELLED remain.

The regenerated ranking still has repeated strikes largest (392 requests / 138
battles), requiring a separate result-shape contract. The next bounded family to
consider is Rollout/Ice Ball (90 requests / 39 battles), with its own authoritative
consecutive-use state requirements; it is not implemented here.

## Reproduce the evidence

With `HNS_UPSTREAM_DIR` pointing at the pinned clean source checkout and the
existing prerequisites installed:

```bash
python3 tools/hns-move-mechanics/generate_hns_move_effects.py --upstream-dir "$HNS_UPSTREAM_DIR" --verify
python3 tools/hns-move-mechanics/test_electro_speed_contract.py --upstream-dir "$HNS_UPSTREAM_DIR"
python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py regenerate
python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py verify --order reversed --keep-log /tmp/electro-reversed-engine.log > /tmp/electro-reversed-summary.log
python3 tools/hns-damage-oracle/report_electro_ball_evidence.py --reversed-log /tmp/electro-reversed-engine.log --reversed-summary /tmp/electro-reversed-summary.log
DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh test
python3 tools/hns-calc-census/report_electro_ball_coverage.py
./ci.sh all
./ci.sh source-check
python3 tools/hns-calc-census/report_electro_ball_coverage.py --check
python3 tools/hns-damage-oracle/report_electro_ball_evidence.py --check
git diff --check
```

The recorded immutable starting-head negative control used the unchanged
`HnsElectroBallStartingHeadTest.kt` on starting main with
`DUALDEX_ELECTRO_OLD_HEAD=true ./ci.sh test`. The ordinary current-head test instead
requires successful admission.

## Safe Thor checklist and limits

Hardware: **NOT_RUN**. In an unmodified save, check slow, equal, twice-defender and
very fast Electro Ball attackers; Choice Scarf, Iron Ball, Tailwind, Rain + Swift
Swim, Electric Terrain + Surge Surfer (grounded and naturally airborne), Charge,
Bulletproof, Volt Absorb/Motor Drive/Lightning Rod. Transition and reload battles
to check stale Speed state is never reused. Do not modify a real save to manufacture
state or attempt defender-zero engine execution.

Remaining limits: authoritative Singles and neutral status only, no Unburden ABI,
no Sand/Hail/Snow expansion, no Substitute/semi-state breakthrough/Doubles,
no probability or turn-order simulation. Other ROMs and dynamic-power families
remain unchanged. No release/signing work. Leave issue and PR open/unmerged for
senior review.
