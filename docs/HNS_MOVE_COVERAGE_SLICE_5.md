# H&S 2.0.5 move coverage, slice 5 — Surf/Whirlpool

Issue #130 follows #123, #125, #127 and #129 without changing historical criteria.
Actual fetched starting main: `5831e4f797e38d98610e17b48ab6f56efa8ed340`.
Mechanics: PokemonHnS-Development/pokehns-expansion,
`1f42b74dff0e9fe942419845d040663dd829a973`, Release-v2.0.5.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen source contract and selection

| Move | ID | Effect | Power | Type/category | Target | Accuracy |
|---|---:|---|---:|---|---|---:|
| Surf | 57 | EFFECT_HIT | 90 | Water/Special | TARGET_FOES_AND_ALLY (11) | 100 |
| Whirlpool | 250 | EFFECT_HIT | 35 | Water/Special | TARGET_SELECTED (1) | 85 |

Both have priority zero, `damagesUnderwater=TRUE`, one strike, no multiHit,
contact, punching or other damage-shape flag. Neither is ordinary. The source
parser freezes all mechanic initializer fields and admits only these exact IDs
into `FIXED_SINGLE_HIT_UNDERWATER`. It emits Kotlin family/power metadata and
JSON with target, category, type, accuracy, flags and exact additional effects.
Surf has no additional effect and `skyBattleBanned=TRUE`. Whirlpool has just
`MOVE_EFFECT_WRAP`, wrapped message B_MSG_WRAPPED_WHIRLPOOL, omitted chance=0,
sheerForceOverride=false and preAttackEffect=false. The existing generated
`MoveIsAffectedBySheerForce` predicate is false for both, without a name-based
boost exception.

Conditional power, accuracy, Surf target and Whirlpool ignoresKingsRock resolve
only under source-verified `B_UPDATED_MOVE_DATA=GEN_LATEST` and
`B_UPDATED_MOVE_FLAGS=GEN_LATEST`. `B_FLAG_SKY_BATTLE=0` is checked; enabling
scripted Sky Battles fails source validation. No runtime Sky Battle field is added.
Helper body checks freeze the reviewed breakthrough, Dive modifier, wrap-duration
and end-turn residual functions. Source checks also verify final-modifier order
and selected damage preceding additional effects. Mutations reject changed
effect, target, power, category/type, underwater flag, strikes, multiHit, other
state flags, contact/punching, pre-attack/additional effects, wrap semantics,
unresolved conditions and Sheer Force classification.

Selection was 128 move-blocked requests across 55 battles, 34 lead requests and
34 affected lead pairs. The preliminary 90 move-only requests are an opportunity
estimate, not an expected unlock count. Repeated strikes rank higher (392 requests,
138 battles), but require a multiple-hit result contract. This slice retains
**one selected hit × sixteen damage rolls**.

## Execution, targets and exact damage slot

The existing native volatile read and JNI index **51** supply
`BattlePokemon.volatiles.semiInvulnerable`. The tuple remains **165 words**:
no ABI growth, duplicate Dive operand, clamping, chosen-move inference or new
read window. `CalcRequestBoundary` publishes only exact-trusted OBSERVED,
slot-matched, in-domain current defender state. Caller live state is overwritten.
Unread, raw invalid, untrusted and stale observations cannot authorize execution.

| Source state | Value | Surf/Whirlpool Singles |
|---|---:|---|
| STATE_NONE | 0 | admitted, independent policies still apply |
| STATE_UNDERGROUND | 1 | refused |
| STATE_UNDERWATER | 2 | admitted, exact Dive final modifier |
| STATE_ON_AIR | 3 | refused |
| STATE_PHANTOM_FORCE | 4 | refused |
| STATE_SKY_DROP | 5 | refused |
| STATE_COMMANDER | 6 | refused |
| unread/unknown/out-of-domain, including sentinel 7 | — | refused |

Existing `HNS_SEMI_INVULNERABLE_STATE_UNKNOWN` and
`HNS_SEMI_INVULNERABLE_EXECUTION_NOT_MODELLED` limitations are reused.
No Guard, sure-hit and other bypass paths do not broaden positive-state admission.

`BreaksThroughSemiInvulnerablity`, battle_util.c:10661, dispatches exact underwater
state to `MoveDamagesUnderWater(move)`. This permits execution to reach the target;
it neither bypasses Water immunity nor guarantees ordinary accuracy. Whirlpool
can still miss. The damage corpus forces successful selected hits; the execution
suite separately exercises hit/miss outcomes. No probability or accuracy percentage
is displayed, and failed execution is never a zero-damage oracle vector.

`GetDiveModifier`, battle_util.c:7514, returns Q12 8192 (×2) only for
source damagesUnderwater and exact STATE_UNDERWATER. `GetOtherModifiers:7724`
orders Minimize → Underground → **Dive** → Airborne → screens → Collision
Course/Electro Drift → speed-ordered ability/partner/item slots. The accumulated
modifier acts after roll/STAB/type/burn. The shipped bundle adds just this Dive
slot, with fail-closed descriptor/ID/effect/state/format validation. No doubled
power, STAB rewrite, Water boost or caller power override implements Dive.

Both moves require two observed Singles participants. Surf's ally-inclusive target
has exactly one opposing active recipient and no ally in Singles.
`GetTargetDamageModifier` consults target count only under `IsDoubleBattle`, so
Singles has no spread reduction or extra target-count dependency. Whirlpool's
TARGET_SELECTED is the explicitly bound opposing defender. Neither enters the
ordinary-only Doubles authority; both Doubles requests remain refused.

## Whirlpool post-hit and item/ability audit

BattleScript_EffectHit (battle_scripts_1.s:2203) resolves damage and HP update
before `setadditionaleffects`. `Cmd_setpreattackadditionaleffect` skips effects
without preAttackEffect. Whirlpool's omitted field is zero. `SetMoveEffect`,
battle_script_commands.c:2650, creates wrapped state only afterward, recording
wrappedMove, wrappedBy and duration. If already wrapped, it simply resumes the
script: that failure to create a fresh wrap cannot alter damage already dealt.
Neither existing wrap nor wrap residual is an operand of its selected-hit formula.

`SetWrapTurns`, battle_util.c:10866, gives Grip Claw duration 7 under GEN_LATEST,
versus normal 4–5. Its existing no-selected-hit classification is retained and
reviewed rationale extended. `HandleEndTurnWrap`, battle_end_turn.c:601, decrements
remaining turns, then Magic Guard prevents residual. Otherwise Binding Band
changes residual maxHP/8 to maxHP/6 under the pinned configuration. The Binding
Band global category stays UNSUPPORTED_DAMAGE_RELEVANT; a named request-local
Whirlpool proof clears only its selected hit. Existing generic post-hit proofs
are preserved for previously supported requests; unmodelled residual moves cannot
inherit Whirlpool admission. Inputs and context rules are reviewed and outputs
regenerated through the item generator, with no unrelated reclassification.

Magic Guard prevents later passive loss, not the Water hit. Shed Shell affects
escape; Rapid Spin clears wrap during its own later move; the escape checks read
wrapped state outside this hit. None changes the displayed Whirlpool damage.
No wrap duration, escape chance, residual, future total or net HP is displayed.
Separate engine observations assert selected hit and residual independently;
Grip Claw/Binding Band/Magic Guard controls preserve the initial hit.

## Shared fixed-hit consumer and Water audit

A = shared fixed-hit proof; B = prior special families; C = ordinary only;
D = Surf/Whirlpool-specific proof.

| Consumer | Class | Disposition |
|---|---|---|
| Registry, CalcDataOverrides, HnsMoveAuthority | A/D | Exact pinned IDs/source power; independent effective type/category rewrite; distinct family. |
| CalcCapabilityPolicy, request boundary, serialization, QuickJS | A/D | Observed Singles/semi-state gate, frozen descriptor and Dive slot. |
| HnsGroupCPolicy, type/ability immunity | A | Effective type/ability/item remain independent from execution and ×2. |
| Raw stats, stages, badges, crit, HP-threshold abilities | A | Current selected-hit operands and source rounding retained; no attacker HP reinterpretation. |
| Normalize/-ate, Liquid Voice, Water Bubble, Adaptability | A | Existing effective type predicates; neither move is sound, so Liquid Voice is irrelevant. |
| Technician, Sheer Force, contact, Tough Claws/Fluffy | A/D | Generated fixed power/flags; noncontact and no Sheer Force boost. |
| Analytic/current action, final abilities and held items | A | Existing action/HP/suppression authority; no Speed-based action inference. |
| Screens, rain/sun, Utility Umbrella, Water items | A | Existing stages; Umbrella affects weather, never Water immunity. |
| HnsTerrainAuthority, field/terrain-sensitive contexts | A/D | Observed underwater proves IsBattlerTerrainAffected false before grounding; execution uses its own helper, not terrain or generic ungroundedness. Other semi-states retain existing conservative handling. |
| Resist berries, HnsDoublesAuthority | C | Ordinary-only activation/admission remains unchanged. |
| Recoil/Reckless/thaw, drain/Heal Block/Triage/Big Root, explosion/self-KO/Damp | B | No inherited family-specific semantics. |
| Whirlpool wrap/Binding Band/Grip Claw/Magic Guard | D | Selected hit separated from later duration/residual/escape. |

Direct `isSupportedFixedSingleHit` consumers were traced in registry admission,
CalcDataOverrides, DamageCalculator, CalcCapabilityPolicy, and both Group C paths.
Their ability/item/field/contact consumers retain independent request-local gates.
No broad ordinary-to-fixed predicate replacement was made.

Water Absorb, Dry Skin and Storm Drain still yield a source-proven zero for an
underwater Water hit. Mold Breaker/Teravolt/Turboblaze with a relevant breakable
immunity retain the existing production refusal; active Ability Shield preserves
that immunity and allows the source-proven zero. The pinned-engine suppression
witness verifies the engine behavior without widening production admission. Gastro Acid and
Neutralizing Gas retain independent production refusal. The separate source
Gastro Acid arithmetic control uses a real earlier setup move and is engine-only.
Ring Target concerns type immunity rather than Water absorb; it does not bypass
these abilities. Utility Umbrella only changes weather applicability.

## Evidence and validation

Admission-only HnsUnderwaterAdmissionTest compiles unchanged on immutable starting
main. Both neutral attempts fail solely with HNS_MOVE_MECHANICS_NOT_MODELLED;
1138 Kotlin tests ran, only that added admission test failed. Machine evidence:
`tools/hns-calc-census/underwater-negative-controls.json`.

The engine scenarios use actual faster-defender Dive on the measured turn. Actual
STATE_UNDERWATER is captured at the damage boundary; no hook writes semi-state or
expected damage. Neutral and underwater immunity state is captured separately
when the engine returns before the damage callback. Successful Whirlpool stores
initial damage separately from end-turn wrap loss and validates HP delta against
their sum. Historical scenario and entry objects must remain unchanged.

The regenerated corpus has **2,088 scenarios: 1,979 modelled / 109 engine-only**,
with zero registered divergences. This adds 56 scenarios (55 modelled, one real
Gastro Acid engine-only control); all 2,032 historical entry objects and raw entry
lines are unchanged. Reversed-order regeneration is byte-identical. The source
runner passes eight underwater execution parameters and four separate wrap
parameters: reachable accuracy miss, Water Gun/Dig/Fly exclusions, real Dive state,
post-hit wrap, duration, residual and initial-hit equality.

The production census comparison is generated in
`tools/hns-calc-census/move-coverage-slice-5.json`:

| Metric | Starting main | Slice 5 |
|---|---:|---:|
| Fully modelled requests | 20,842 | 20,968 |
| Caveated estimates | 462 | 462 |
| Refused requests | 2,974 | 2,848 |
| Fully displayable lead pairs / 1,302 | 536 | 554 |
| Displayed lead requests / 8,450 | 7,336 | 7,370 |

126 requests unlock across 54 battles; 18 lead pairs and 34 lead requests unlock.
The extra 36 beyond the preliminary 90 have existing item/ability relevance
proofs resolved by fixed-hit admission (32 item blockers and 10 ability-condition
blockers, with overlap). All 128 family requests change only within this slice;
two Finley party-slot-2 Whirlpool requests remain refused because they are
Doubles. Population, reference teams, trainer inventory and lead definition stay
unchanged: **651 battles / 24,278 requests**. Zero outside-slice transitions and
zero blank battles before/after. No claim of coverage for underwater live trainer
states follows from this neutral-state census.

`./ci.sh all`, `./ci.sh source-check`, the slice report `--check`, and
`git diff --check` pass. Kotlin: 1,146 tests, zero failures/errors/skips; QuickJS:
3,127 passing fixtures; native oracle: 2,088 exact sixteen-roll matches and zero
divergences. Generated move mutation tests and item/source audits pass. Final-head
Actions and tree equivalence are recorded in the open PR. Hardware remains NOT_RUN;
source runner, production boundary and census evidence are ROM-free.

## Remaining limits and safe Thor checklist

Excluded: Doubles Surf/Whirlpool, ally/spread totals, Dive damage, other underwater
moves, general semi-invulnerability, No Guard/sure-hit extensions, repeated strikes,
wrap residual/future turns, escape prediction, accuracy/KO probability and all
independent unsupported ability/item/field states. No release signing or save edits.

Hardware **NOT_RUN**. Use an existing verified H&S 2.0.5 state without modifying
real saves to manufacture evidence:

- Neutral Surf and Whirlpool.
- Faster target uses Dive, then Surf; repeat with Whirlpool.
- Underwater Water Absorb/Storm Drain zero-damage control.
- Whirlpool with Binding Band and Grip Claw; compare only initial hit.
- One unsupported Dig/Fly target refusal.
- Reload/battle transition clears stale semi-state and participant bindings.

Next bounded family: source-audit `EFFECT_DOUBLE_POWER_ON_ARG_STATUS` (102 remaining
requests across 48 battles), then select only the status predicates supported by
current authoritative operands. Repeated strikes (392/138) remain a separate
multi-hit product-contract task.
