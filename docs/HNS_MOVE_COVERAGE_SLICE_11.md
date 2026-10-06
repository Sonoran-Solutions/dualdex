# H&S move coverage slice 11 — selected-hit pivot damage

Issue [#143](https://github.com/Sonoran-Solutions/dualdex/issues/143).
Actual starting main: `27205d4a1f84521c55e038dbe3ca0db8d0657ea6`.
This is #142's merge commit; fetched main had no intervening commits. #141
was closed with a completion comment without changing its historical body.
Mechanics authority: PokemonHnS-Development/pokehns-expansion,
Release-v2.0.5, `1f42b74dff0e9fe942419845d040663dd829a973`.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen family and product contract

| Move | ID | Effect | Power | Type | Category | Contact |
|---|---:|---|---:|---|---|---|
| U-Turn | 369 | EFFECT_HIT_ESCAPE | 70 | Bug | Physical | Yes |
| Volt Switch | 521 | EFFECT_HIT_ESCAPE | 70 | Electric | Special | No |
| Flip Turn | 740 | EFFECT_HIT_ESCAPE | 60 | Water | Physical | Yes |

All three have accuracy 100, PP 20, TARGET_SELECTED, priority 0, one strike,
no punching flag, no additional/pre-attack effects and no Sheer Force boost.
The full MoveInfo bodies are frozen by hashes, rather than checked only for power.
Their presentation retains numeric powers 70/70/60. They enter
`FIXED_SINGLE_HIT_ESCAPE`, never the ordinary `EFFECT_HIT` set.

DualDex calculates the exact damage of the selected hit. Later pivot/switch
behavior is outside the result and is not predicted. Repeated strikes remain
the largest opportunity; their strike-count and survival semantics require a
separate product contract. Hit-escape was the next bounded family: 74 blocked
requests, 29 battles, 28 affected lead pairs and 28 lead requests at starting head.
The preliminary 68-request upper bound was a selection metric, not an unlock target.

## Source path: damage first, pivot afterward

Pinned `src/data/moves_info.h:9870,13555,18980` declares the descriptors.
`include/constants/battle_move_effects.h` declares EFFECT_HIT_ESCAPE.
`src/data/battle_move_effects.h:1110` maps it to **BattleScript_EffectHit**.
That script (`data/battle_scripts_1.s:2203`) runs attack cancellation, accuracy,
pre-attack effects and `damagecalc`, then calls the normal hit script.
`BattleScript_Hit_RetFromAtkAnimation` updates the HP bar and performs
`datahpupdate BS_TARGET, MOVE_DAMAGE_HP_UPDATE` before returning to move end.

Only later does `src/battle_move_resolution.c:3457`'s MoveEndHitEscape call
BattleScript_EffectHitEscape. It requires an able, living attacker, a damaged
target (including Substitute in the engine) and a remaining living target-side
Pokémon. BattleScript_EffectHitEscape (`data/battle_scripts_1.s:2189`) handles
faint/experience and calls BattleScript_MoveSwitchPursuitRet (`:237`). This
later script checks arena and `jumpifcantswitch SWITCH_IGNORE_ESCAPE_PREVENTION |
BS_ATTACKER`, then Pursuit and party selection. Subsequent switch data updates,
switch-in animation, switch-in abilities/effects and events occur later still.
The escape-prevention flag is recorded as source evidence only. The complete
EFFECT_HIT_ESCAPE reference search found no selected-hit damage arithmetic
branch: source power enters CalcMoveBasePower's default fixed-power path.
The other battle-util use (`:3949`) follows Weak Armor's damaged-target predicate
and controls post-hit Eject Pack sequencing. Remaining references are AI choice
logic and MoveInfo/effect declarations, not damage operands.

Consequently, bench availability, replacement identity, hazards, trapping,
Pursuit, switch-in abilities and future field state are not selected-hit
operands. No new live ABI, memory packet, tuple field or switch-state transport
was added. Engine-only Substitute behavior does not reopen production support.

The metadata generator verifies the complete pinned source files containing
these paths, damage ordering, Technician and contact. Mutation checks reject
changed descriptors, damage HP updates, pivot predicates, switch flags,
contact authority and Technician's threshold.

## Shared consumer audit

| Consumer | Admission and source reason |
|---|---|
| Registry, capability/item relevance, CalcDataOverrides, serializer | Dedicated fixed hit reaches the existing damage boundary. Frozen descriptor is looked up by pinned name/ID; caller overrides are discarded. No switch gate. |
| Technician | CalcMoveBasePowerAfterModifiers tests integer BP <=60 (`src/battle_util.c:6655`). Flip Turn qualifies; U-Turn and Volt Switch do not. No move-name ability special case. |
| Contact, Tough Claws, Fluffy, Long Reach | IsMoveMakingContact (`src/battle_util.c:5880`) uses MoveInfo contact and effective attacker Long Reach/Punching Glove. Shared authority is reused. Only reviewed hit-escape and existing Rollout requests gain the Long Reach request-local rule; the global ability category remains unchanged. Volt Switch is non-contact. |
| STAB, type rewrites, immunity | The standard pipeline consumes GetBattleMoveType/GetBattleMoveCategory, source type chart and effective abilities/items. Existing HnsMoveAuthority and Group C gates apply. Normalize is covered; active Electrify remains refused. |
| Screens, terrain, weather, Charge, critical, stat stages | Same ordinary hit script and CalculateMoveDamage pipeline. Existing authoritative field/state and category rules apply unchanged. Electric Charge/terrain and Water Rain/Sun compose at their existing stages. |
| HP thresholds, resist berries, final items | Same selected hit and Q12 modifier stages; effective item, HP/status and suppression authority remain required. Post-hit item reactions are not modelled as selected-hit damage. |
| Current-action modifiers | Existing observed phase/order predicates remain required. Pivot availability cannot authorize an unread action operand. |
| Sheer Force | No additional effect, source MoveIsAffectedBySheerForce=false; no boost. Existing Life Orb residual rules remain separate. |
| Mold Breaker | Existing breakable-ability/Ability Shield rules and unresolved bypass refusals remain unchanged. Dedicated family does not authorize suppression. |

The exact hit-escape Long Reach decision bypasses both the unsupported-ability
limitation and caveat neutralization; retaining the effective ability is required
when Fluffy consumes contact. Existing Rollout decisions are unchanged.

All direct `isSupportedFixedSingleHit` callers were traced: DamageCalculator,
HnsGroupCPolicy (both sites), CalcDataOverrides and CalcCapabilityPolicy.
`isSupportedForOrdinaryDamage` remains ordinary-only. Ability/item policies use
request-local `fixedSingleHitMove`, with no mechanical expansion of unrelated
state predicates. JS's explicit contact family gate accepts the three frozen
IDs and independently validates their metadata before immunity or damage.

## Evidence and validation

Generated evidence:

- `tools/hns-calc-census/hit-escape-negative-control.json`: immutable starting
  API admission checks for all three moves, with only the move-mechanics refusal.
- `tools/hns-damage-oracle/hit-escape-evidence.json`: historical object and
  canonical-line equality, Technician/contact controls, independent reversed
  regeneration and separate successful/failed/faint execution witnesses.
- `tools/hns-calc-census/move-coverage-slice-11.json`: unchanged population,
  exact request-key transitions, remaining causes, lead displays and ranking.

The execution tests use real original-engine moves and party switching. Two TESTING-only read-only hooks capture original calculated damage before HP
clamping and HP/party state at entry to MoveEndHitEscape; neither writes engine
operands or damage. They
compare bench absent/present and two replacement species at equal damage
operands, observe HP damage before Drizzle's switch-in popup, check actual party
indices, exercise target faint replacement and assert no pivot for miss,
Protect and absorption. These execution results are separate from the sixteen
roll matrix; miss/Protect never become fake zero-damage vectors.

The production-boundary test replays every new admitted engine vector through
CalcRequestBoundary and the shipped calculator. It also covers fixed powers,
Technician, contact/Long Reach, numeric presentation, forged descriptors and
power, ignored forged switch data, existing format/Substitute/semi-state/type
refusals and adjacent unsupported moves.

## Measured census and oracle results

The immutable starting-head canonical test passed; all three admission-only
controls refused solely with HNS_MOVE_MECHANICS_NOT_MODELLED. The final full
census generation passed on the same 651 battles and 24,278 exact request keys,
trainer/reference teams, selected moves, directions and neutral assumptions.

| Metric | Starting | Slice 11 |
|---|---:|---:|
| FULLY_MODELLED | 21,368 | 21,440 |
| CAVEATED_ESTIMATE | 462 | 462 |
| REFUSED | 2,448 | 2,376 |
| Fully displayable lead pairs / 1,302 | 656 | 662 |
| Displayed lead requests / 8,450 | 7,520 | 7,548 |
| Blank battles | 0 | 0 |

72 requests transition REFUSED -> FULLY_MODELLED, zero to CAVEATED; 28 battles
gain results. The two remaining hit-escape refusals are Mualani party slot 2
Volt Switch, both reference teams, trainer-to-reference: Doubles, solely
HNS_MOVE_MECHANICS_NOT_MODELLED. The dedicated family is explicitly Singles-only
in the existing format gate. Outside-slice transitions are zero; historical
Speed requests are unchanged. Newly fully displayable lead pairs are Boris,
Heidi and Michelle against each reference team.

Six overlapping starting blockers disappear through existing request-local
rules, without global category upgrades:

- Mankey U-Turn's two Flying Gem item blockers: authoritative Bug type proves
  a nonmatching gem has no selected-hit boost (HnsItemContextPolicy's existing
  HOLD_EFFECT_GEMS rule; pinned battle_util.c:6841).
- Tentacruel Flip Turn's two Liquid Ooze ability blockers: the known fixed
  single hit proves the after-hit effect is outside this damage result
  (existing ability id 64 fixed-hit rule).
- Scyther U-Turn's two Swarm condition blockers: authoritative Bug type and
  observed full HP prove the pinch threshold inactive (existing HP/type rule;
  pinned battle_util.c:7023).

The original-engine corpus adds 57 modelled scenarios: **2,523 total, 2,399
modelled, 124 engine-only, zero divergences**. All 2,466 historical objects and
canonical lines are unchanged. Fresh reversed-order engine regeneration is
byte-identical. All new production-admitted vectors match sixteen rolls.
Technician leaves U-Turn and Volt Switch's neutral vector unchanged (37–44);
Flip Turn changes 32–38 to 47–56. Contact/Tough Claws/Fluffy/Long Reach controls
are engine-backed, including the non-contact Volt Switch negative controls.

Separate execution suites pass for all three moves: no bench, Machamp bench,
and Blastoise/Drizzle bench have identical uncapped selected-hit damage
(44/44/38). HP updates are observed before pivot handling while attacker party
index remains 0; successful legal replacement later becomes party index 1.
Target-faint tests observe calculated 44/44/38 but applied HP loss 1 and target
HP 0 before pivot handling. Miss, Protect and Electric/Water absorption tests
assert no successful pivot. These observations do not imply switch simulation.

Updated remaining ranking starts with repeated strikes (392 requests, 138
battles, 160 affected lead pairs; preliminary upper bound 336), semi-invulnerable
(80; upper bound 70), OHKO (70; 66), Pursuit (68; 48), Belch (64; 64), and
Assurance (62; 58). These are selection bounds, not authorizations. The full
remaining ranking is in move-coverage-slice-11.json.

Validation: full canonical census generation, original-engine regeneration,
fresh reversed verification, corpus and generated evidence checks, source
metadata verification, 39 source/descriptor mutation controls, and the real
production-boundary replay all pass. `./ci.sh all` and `./ci.sh source-check` pass on implementation commit
`3648dde6365000bf7a33763565dfd4d4201c9a28`; all runs have zero Kotlin failures
or skips. Source-check ran in an independent checkout of that commit.
`git diff --check` passes. The only subsequent change records these results;
final-head Actions and synthetic merge-tree evidence are in the PR review handoff.

## Remaining scope and optional hardware validation

No switch outcome, replacement choice, hazards, Pursuit, trapping, post-hit
attacker damage, future state, multi-hit totals, KO/accuracy probability,
Substitute damage or expanded Doubles contract is provided. Unread live state,
unsupported weather/status/type states and unresolved bypass remain fail-closed.
Vanilla and unrelated H&S moves retain existing behavior.

Optional human Thor checklist: U-Turn, Volt Switch and Flip Turn with a legal
replacement; one no-replacement case; Flip Turn with Technician; contact
comparison for U-Turn/Flip Turn versus Volt Switch; reload/new battle to check
stale-state invalidation. Do not manufacture evidence by modifying a real save.

Recommend a separate repeated-strike result-contract/design task before
implementation: fixed two-hit versus random 2–5 hits, per-hit versus total
damage, Skill Link/Loaded Dice, target survival and per-hit ability/item effects.
This PR does not design that contract.
