# H&S 2.0.5 move coverage, slice 4 — Explosion/Self-Destruct

Issue #128 follows #122/#123, #124/#125 and #126/#127 without changing historical
acceptance criteria. Actual starting main: `5c2242e90a6c511c307ba304ebda38a63f21336f`.
Mechanics source: PokemonHnS-Development/pokehns-expansion,
`1f42b74dff0e9fe942419845d040663dd829a973`, Release-v2.0.5.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen family

| Move | ID | Source effect | Power | Type/category | Target | Priority |
|---|---:|---|---:|---|---|---:|
| Self-Destruct | 120 | EFFECT_HIT | 200 | Normal/Physical | TARGET_FOES_AND_ALLY | 0 |
| Explosion | 153 | EFFECT_HIT | 250 | Normal/Physical | TARGET_FOES_AND_ALLY | 0 |

Both have explosion, dampBanned and parentalBondBanned TRUE; no contact, multiple
strikes, additional effects, other damage-shape flags or ability-move flags.
The generator freezes all initializer names and exact execution/damage values,
including B_UPDATED_MOVE_DATA=GEN_LATEST and B_EXPLOSION_DEFENSE=GEN_LATEST.
Mutations reject changed effect/power/type/category/target/priority, removed bans,
contact, strikes, state flags, unknown initializers and unresolved configuration.
It emits a distinct FIXED_SINGLE_HIT_EXPLOSION family, power map and JSON contract.
Neither move enters ordinaryMoveIds. Caller power/category/type cannot authorize:
the exact pinned ID/data pack is rebound by the real production boundary.

Ranking: 188 blocked requests / 66 battles / 72 lead requests and affected lead
pairs. The preliminary 168 no-other-known-blocker count is an opportunity estimate.
Repeated strikes (392 requests / 138 battles) require a different result contract.

## Execution and state ordering

Pinned battle_move_resolution.c:1459–1481 orders CancelerExplodingDamp before
CancelerExplosion. IsAbilityOnField (battle_util.c:5044) requires a living battler
and GetBattlerAbility, not raw identity. Effective Damp causes the hard limitation
HNS_DAMP_BLOCKS_EXPLOSION: no request is exposed to QuickJS, no zero range is shown.
Unknown field authority/settlement yields HNS_EXPLOSION_EXECUTION_UNKNOWN.

The existing HnsFieldAbilityAuthority now applies its breakable-defender branch
to Damp as well as Aura Break. Own Damp is never bypassed. Mold Breaker, Teravolt
and Turboblaze suppress breakable defender Damp; effective Ability Shield preserves
it. The helper shares HnsHoldEffectAuthority's IgnoreAbility shield predicate.
Gastro Acid and active Neutralizing Gas remain independently refused by existing
production suppression gates. Shield never clears those unrelated blockers.
Unread suppression windows, unknown ability identity, stale slots, missing trust
and caller-forged ability/state cannot authorize. Damp's reviewed classification
is MODELLED_HNS_CONDITIONAL: explosion execution is separately gated, other
supported selected hits have no Damp damage modifier, and Aftermath is excluded.
Parental Bond gets only the request-local false-predicate proof for these two bans.

After the Damp gate, BattleScript_Explosion executes tryexplosion/setatkhptozero
before selected-hit damage. The live observation is retained unchanged; the ability
context derives attackerHp=0 for the frozen family and the production move JSON
carries hnsExplosionUserHpAtDamage=0. QuickJS requires that descriptor and uses
zero for Defeatist/pinch thresholds. Max HP remains observed, never synthesized.

Explosion/Self-Destruct self-KO is source-modelled only insofar as it affects
whether the move executes and the damage-time operands. The displayed result
remains defender damage: one selected hit × sixteen rolls.

## Consumer audit

A = shared fixed-hit proof remains valid; B = previous special families only;
C = ordinary-only contract; D = explosion-specific interpretation/gate.

| Consumer | Class | Result |
|---|---|---|
| Registry, CalcDataOverrides, HnsMoveAuthority | A/D | Source power, effective type/category, separate exact family. |
| Capability/boundary/serialization | A/D | Singles topology, effective Damp gate, independent HP-at-damage fact. |
| Defeatist, Overgrow/Blaze/Torrent/Swarm | D | Damage-time HP zero; pinch still requires matching final type. Defeatist halves attacking stat even above half pre-action HP. |
| IsAbilityOnField, auras, weather suppressors | D | Fainted attacker is excluded after self-KO; Damp evaluates before self-KO. Boundary weather forwarding uses this same distinction. |
| Ruin | A | IsRuinStatusActive:6874 scans stored volatiles and suppression, without IsBattlerAlive; do not erase attacker's stored field flags. |
| Solar Power, Orichalcum Pulse, Flower Gift | D | Existing liveness-sensitive conditional contexts fail closed where positive damage-time HP is required; no proof weakened. |
| Raw stats/stages/badges, critical stage handling | A | Existing selected-hit arithmetic and fixed crit. |
| Normalize/-ate, Liquid Voice | A | Existing effective type authority; Liquid Voice predicate false because neither move is sound. Damp remains source-flag based. |
| Technician/Sheer Force/contact | A | Source power 200/250, no additional effects/contact; flags stay generated. |
| Terrain/grounding | A | IsBattlerTerrainAffected:5140 has field/semi/grounding checks, no HP/alive check. |
| Screens/Wonder Room/defensive abilities/items | A | Modern physical Defense selection, existing independent authority/refusals. |
| Held-item HP rules/Focus Sash/survival | A | Current item HP checks are defender-side; result/caveats unchanged. |
| Resist berries / Doubles authority | C | Existing ordinary-only activation/admission remains. |
| Analytic/current action | A | Existing actual-action authority; no Speed inference. |
| Parental Bond / Damp | D | Narrow second-hit ban proof / hard execution gate. |
| Recoil/Reckless/thaw, drain/Heal Block/Triage/Big Root | B | No inherited explosion behavior. |

All direct consumers of isSupportedFixedSingleHit were traced: registry admission,
CalcDataOverrides, DamageCalculator, CalcCapabilityPolicy and both HnsGroupCPolicy
paths. Their ability/item/field context consumers were audited by the table above.
No mechanical ordinary-to-fixed predicate replacement was made.

Modern Defense: battle_util.c:7254 halves selected Defense only when
B_EXPLOSION_DEFENSE < GEN_5. Pinned GEN_LATEST makes that branch false. The shipped
H&S selected-hit path computes ordinary modern Defense, without fake power or
legacy Gen III halving. The high-Defense engine control distinguishes the rules.

Singles requires two observed participants and no ally; GetTargetDamageModifier
uses spread count only under IsDoubleBattle. No target-count operand/spread
reduction is introduced for this family. Doubles retains hard refusal.

## Evidence and validation

Starting-head negative control: HnsExplosionAdmissionTest compiled unchanged on
immutable starting main; both moves failed solely with HNS_MOVE_MECHANICS_NOT_MODELLED.
Canonical ./ci.sh test ran 1,129 Kotlin tests with only that admission test failing.
Machine-readable evidence: tools/hns-calc-census/explosion-negative-controls.json.

The pinned engine adds **24 scenarios**: 22 production-modelled and two engine-only
(Gastro Acid suppression and the existing Wonder Room production caveat). They cover
both moves/directions, high Defense, Defense/Attack stages, Reflect, Fur Coat,
Life Orb, fixed crit, rounding composition, Defeatist above half pre-action HP,
Parental Bond, Normalize and all four -ate rewrites, Ghost immunity and Pixilate
versus Ghost, Mold Breaker versus Damp and Gastro Acid-suppressed Damp.

Every new vector captures actual attacker HP **0** at the same critical/damage
boundary as its source operands, including Ghost immunity. Faint cleanup clears
gLastMoves; the harness records actual gCurrentMove and battler operands at the
boundary instead of reconstructing an attacker that has fainted. No hook writes
HP or damage. The historical 2,008 entry objects remain identical.

Selected source rolls: neutral Explosion **130–154**, Self-Destruct **104–123**;
Defeatist with pre-action HP 200/200 **65–77**; high Defense 503 **29–35**;
Parental Bond **130–154** (one hit); Pixilate versus Ghost **156–184**;
Normal versus Ghost **0**, with attacker still self-KO'd. The production regression
substitutes half Defense and requires a different vector, not a fake power change.

The generated real-engine execution suite has **seven parameters**, all PASS:
defender Damp blocks each move, own Damp blocks Explosion, Mold Breaker bypasses
breakable defender Damp, effective Ability Shield preserves Damp, neutral damage
and Ghost immunity both self-KO. Blocked attempts leave attacker HP 200 and defender
HP 60000, and never reach the damage hook. Both successful/immune attempts reach
the hook exactly once with attacker HP zero; Ghost leaves defender HP unchanged.
Runner PASS is mandatory for regeneration and reversed verification, and generated
execution source is included in corpus provenance. Gastro Acid/NG production
branches are explicitly refused; the Gastro Acid engine-only vector uses a real
setup move rather than a late write to cached ability state.

Final corpus: **2,032 scenarios** (1,924 modelled, 108 engine-only), **2,032 exact
sixteen-roll shipped-bundle matches**, zero registered divergences.

| Metric | Starting main | Slice 4 |
|---|---:|---:|
| Fully modelled | 20,654 | 20,842 |
| Caveated estimate | 462 | 462 |
| Refused | 3,162 | 2,974 |
| Fully displayable lead pairs / 1,302 | 524 | 536 |
| Displayed lead requests / 8,450 | 7,264 | 7,336 |

The census preserves **651 battles / 24,278 requests**, teams, move slots,
directions, lead definitions and neutral runtime assumptions. All **188** family
requests become FULLY_MODELLED; no family requests remain refused. This is a
neutral offline census, not a promise that live Damp/suppression/format states will
execute. The 168 opportunity estimate was not a cap: the new source-proven fixed
family makes 14 contextual ability and six item blockers decidable by their
existing request-local predicates: 14 attacker Sturdy cases (defender-only survival),
four Normal Gem cases (matching effective type; SetTypeBeforeUsingMove:6440–6450
activates the gem before self-KO without an HP predicate), and two Quick Claw cases
(order is damage-irrelevant without Analytic). No independent blocker was globally
removed.

The Damp reclassification initially introduced ten redundant ability causes on
already-refused Surf/Fury Swipes requests. A generated source Damp-ban map now
proves those moves cannot invoke CancelerExplodingDamp, preserving their original
refusal causes without admitting their mechanics. The generated comparison asserts **zero outside-slice tier/reason/cause changes**
and **zero blank battles**. **66 battles gain requests**.

Validation: full-population `DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true
./ci.sh test`, non-generating `./ci.sh all`, and `./ci.sh source-check`; source audits,
move-generator mutation tests, production-boundary regressions, native differential
checks and census byte-for-byte checks run through these canonical commands.
Reversed engine verification reproduced the committed corpus byte-for-byte.
Full census tests: 1,137 Kotlin tests, zero failures/errors/skips. `./ci.sh all`
passed, including the debug APK build. `./ci.sh source-check` passed, including
source audits, regeneration checks and upstream Kotlin validation.
Final command outcomes and exact-head Actions/tree evidence are recorded in the PR.

## Remaining scope and safe Thor checklist

Excluded: repeated/multiple hits, Doubles explosion, Misty Explosion, Final Gambit,
Memento, Steel Beam, Mind Blown, Chloroblast and all other self-KO/self-damage
shapes. No attacker-survival display, net HP, KO/accuracy probability, Protect
prediction, Aftermath or generic battle simulation. Independent ability/item/
field/gimmick/survival gates remain; no release signing or real-save edits.

Use an existing verified H&S 2.0.5 state without altering a save solely to create
these cases:

- Neutral Self-Destruct and Explosion.
- Defender Damp and attacker Damp refusals.
- Mold Breaker versus defender Damp; Ability Shield preservation.
- Normal Ghost immunity control and one existing type rewrite.
- Battle transition/reload clears stale ability/execution state.

Next bounded family: Surf/Whirlpool underwater authority, subject to source target
and effect audit. Repeated strikes still need their own multiple-hit product contract.
