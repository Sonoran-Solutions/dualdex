# H&S 2.0.5 move coverage, slice 3 — Earthquake/Bulldoze

Issue #126 follows merged #123 and #125. Starting main is
`3703d94fd94c06b63656aa5a46f193b6273a0df1`, fetched before implementation.
Mechanics are pinned to `PokemonHnS-Development/pokehns-expansion`
`1f42b74dff0e9fe942419845d040663dd829a973` / Release-v2.0.5.
H&S remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen source contract

| Move | ID | Effect | Power | Category | Target | Priority | Underground | Sheer Force |
|---|---:|---|---:|---|---|---:|---|---|
| Earthquake | 89 | EFFECT_EARTHQUAKE | 100 | Physical Ground | TARGET_FOES_AND_ALLY | 0 | true | false |
| Bulldoze | 523 | EFFECT_EARTHQUAKE | 60 | Physical Ground | TARGET_FOES_AND_ALLY | 0 | false | true |

Selection: 294 blocked requests / 106 battles / 68 lead requests and affected
lead pairs. The 202 move-only requests are an opportunity bound, not an acceptance
target. Repeated strikes rank higher (392 / 138) but require a multiple-hit output
contract; this slice preserves one selected hit × sixteen damage rolls.

`src/data/battle_move_effects.h:801` uses `BattleScript_EffectHit`.
`CalcMoveBasePower` has no Earthquake-specific power selection. The effect branch
in `CalcMoveBasePowerAfterModifiers:6614-6618` checks the **raw Grassy field bit**
and `!IsSemiInvulnerable(defender, CHECK_ALL)`, applying Q12 2048 (×0.5).
This slot precedes Helping Hand, Gems, Charge, ordinary terrain and ability slots.
It does not use `IsGrassyTerrainAffected` or groundedness. Neutral Earthquake and
Bulldoze get the reduction even for an ungrounded target; Normalize/Flying
regressions isolate that distinction without a Ground immunity hiding the damage.

`GetUndergroundModifier:7506-7511` checks the source `damagesUnderground` flag and
exact `STATE_UNDERGROUND`. Q12 8192 (×2) enters `GetOtherModifiers:7723`, after
Minimize and before Dive, Airborne, screens, Collision Course, speed-ordered
ability/partner/item slots. The accumulated product is applied after roll, STAB,
type effectiveness and burn. It is never a base-power doubling. Underground
Earthquake skips Grassy ×0.5 and retains this ×2. Reflect, Life Orb, critical,
stages, type effectiveness and Filter/Life Orb/Reflect composition exercise order.

Bulldoze's guaranteed `MOVE_EFFECT_SPD_MINUS_1` additional effect makes the source
`MoveIsAffectedBySheerForce` predicate true. Its existing BP slot applies Q12 5325
for effective Sheer Force. Earthquake's predicate is false. Post-hit Speed changes
and future turn order are outside this selected-hit result.

The generator resolves Earthquake's conditional `damagesUnderground` only under
verified `B_UPDATED_MOVE_FLAGS=GEN_LATEST`. It emits a separate
`FIXED_SINGLE_HIT_EARTHQUAKE` family and exact underground descriptor; ordinary,
recoil and drain sets are unchanged. Mutations reject changed effect, target,
priority, power, category, type, strikes, multiHit, explosion, state flags,
contact/punch flags, additional effect and unresolved underground configuration.

## Live operand and execution authority

The existing native volatile window and legacy JNI index **51** already carry
`BattlePokemon.volatiles.semiInvulnerable`. JNI stays 165 words; no duplicate
operand or read window is added. The live-layout generator source-pins the enum
ordering/count and emits the same constants for native C and Kotlin:

| Source state | Value |
|---|---:|
| STATE_NONE | 0 |
| STATE_UNDERGROUND | 1 |
| STATE_UNDERWATER | 2 |
| STATE_ON_AIR | 3 |
| STATE_PHANTOM_FORCE | 4 |
| STATE_SKY_DROP | 5 |
| STATE_COMMANDER | 6 |
| SEMI_INVULNERABLE_COUNT (exclusive sentinel) | 7 |

Kotlin preserves the raw value instead of `coerceIn(0,6)`. Native and Kotlin mark
an observed out-of-domain payload invalid. Raw 7 stays 7, never Commander.
Unread windows remain unread. `CalcRequestBoundary` binds
`defenderSemiInvulnerableState` only from the current exact-trusted OBSERVED,
slot-matched active defender and a valid domain. It overwrites caller live state;
forged neutral, stale slot, missing trust and unread/domain-invalid observations
cannot authorize execution. Existing ROM/battle/participant invalidation applies.

`BreaksThroughSemiInvulnerablity:10661-10704` distinguishes every state and reads
`MoveDamagesUnderground` for underground. Production admits only observed neutral
for either move and underground for Earthquake. Other valid positive states and
Bulldoze underground yield `HNS_SEMI_INVULNERABLE_EXECUTION_NOT_MODELLED`.
Unread/out-of-domain state yields `HNS_SEMI_INVULNERABLE_STATE_UNKNOWN`.
Both are hard, reflected refusals. No Guard, sure-hit and accuracy-bypass
exceptions do not broaden this gate.

Both source targets are ally-inclusive spread targets. In Singles there is one
opposing active battler and no ally. `GetTargetDamageModifier` consults target
count only under `IsDoubleBattle`; the raw count can include an empty partner
slot, but no count operand is required for the supported Singles branch. The
boundary requires observed two-battler Singles. Doubles retains hard refusal.

## Shared-consumer and Ground authority audit

A = reusable for ordinary/recoil/drain/Earthquake; B = earlier families only;
C = ordinary only; D = family-specific branch.

| Consumer | Class | Contract |
|---|---|---|
| Registry/CalcDataOverrides/HnsMoveAuthority | A + D | Source fixed power; same effective type/category, separate family descriptor. |
| CalcCapabilityPolicy, boundary and serialization | A + D | Independent live gates; new semi execution and Singles gate. |
| Raw stats/stages/badges, HP, crit, survival caps | A | Same selected hit; no later Speed or HP prediction. |
| Technician, Sheer Force, contact/Tough Claws/Fluffy | A | Frozen generated flags; both noncontact/nonpunching; Bulldoze Sheer Force true. |
| HnsGroupCPolicy / immunity / Mold Breaker | A | Uses effective type, exact flags and independent effective ability/item authority. |
| HnsAbilityContextPolicy / final and stat slots | A | Same source predicates, rounding and suppression; Analytic still requires actual action authority. |
| HnsItemContextPolicy / final modifiers | A | Same effective hold effects; unknown/suppressed item rules retained. |
| HnsFieldContextPolicy / terrain/screens/weather/Wonder Room | A + D | Separate raw-Grassy/semi branch; existing field exclusions retained. |
| HnsContactRules / QuickJS contact predicate | A + D | Explicit descriptor enables the same noncontact decision; names never authorize it. |
| Resist berries | C | Existing ordinary-only activation gate remains. |
| HnsDoublesAuthority | C | New category cannot enter ordinary-only Doubles support. |
| Reckless/recoil/thaw; drain Heal Block/Triage/Big Root | B | Existing family-specific predicates remain; Earthquake inherits none. |

Flying type, Levitate, Earth Eater, Air Balloon and Iron Ball remain independent
Ground immunity operands. Underground does not remove an immunity. Iron Ball's
source Flying effectiveness override remains unchanged. Group C Mold Breaker,
Teravolt/Turboblaze and Ability Shield keep their existing suppression proof;
Ring Target remains governed by its existing item gate. Gravity Ground effects
remain outside exact production field support; Root, Smack Down, Magnet Rise,
Telekinesis, Gastro Acid and Roost retain existing conservative volatile gates.
Grounding never substitutes for semi-state. Terrain applicability can remain
unknown underground without blocking this effect-specific raw-Grassy branch;
independent item/ability terrain predicates still require their existing authority.

## Evidence and validation

The admission-only test compiles unchanged on starting main and fails for both
moves solely with `HNS_MOVE_MECHANICS_NOT_MODELLED`. Exact failure evidence and
canonical command exit status are in
`tools/hns-calc-census/earthquake-negative-controls.json`.

The pinned-engine matrix adds neutral hits in both directions; Grassy and Sheer
Force controls for both moves, Grassy/Technician and Grassy/Sheer Force composition; Normalize/Flying Grassy controls; underground
Earthquake in both directions; underground Grassy, Reflect, Life Orb, crit,
stages, type effectiveness and rounding composition; Ground ability/type/item
immunity, Iron Ball and an engine-only neutral-target Gravity control. The defender uses real
Dig, faster than the selected attacker, on the actual measured turn. A dedicated
source-domain Q observation captures semi-state at the critical-hit boundary;
every damaging roll validates the observed state, and no hook writes that state.
Neutral immunity controls capture the state after the turn because immunity can
return before the critical-hit callback. Neutral grounded Grassy defenders also
heal after the measured hit; the harness validates that separate maxHP/16 recovery
from measured damage and the source applicability predicate, never from an expected
damage formula.
Historical scenarios and entry objects are preserved.

Separate deterministic execution tests use real Dig, Dive, Fly and Phantom Force
actions. They prove Earthquake's underground hit, Bulldoze's underground miss,
and Earthquake misses for underwater/on-air/Phantom Force, checking the actual
volatile state and source breakthrough helper. Failed execution is never encoded
as zero damage in the differential corpus.

Production tests exercise the real boundary, explicit descriptor, serialization
and shipped bundle across all sixteen engine rolls, active/unknown/invalid semi
states, caller forgery, stale slot, Grassy groundedness distinction, Sheer Force,
Doubles and adjacent excluded moves. Native and Kotlin decoder tests preserve
0..6 and reject raw 7. Existing ordinary/recoil/drain tests remain.

Final engine corpus: **2,008 scenarios** (1,902 modelled, 106 engine-only),
**27 new**, all 16 shipped-calculator rolls match, **zero divergences**.
Canonical and reversed full regenerations are byte-identical; the 1,981 historical
entry objects remain identical. All five Earthquake semi-state execution parameters
pass, as do retained Heal Block and Triage execution proofs.

Unchanged production census: **651 battles / 24,278 requests**, same teams, move
slots, directions, definition and inventory; zero unexplained outside-slice changes.

| Metric | Starting main | Slice 3 |
|---|---:|---:|
| Fully modelled | 20,362 | 20,654 |
| Caveated estimate | 462 | 462 |
| Refused | 3,454 | 3,162 |
| Fully displayable lead pairs / 1,302 | 504 | 524 |
| Displayed lead requests / 8,450 | 7,196 | 7,264 |
| Completely blank battles | 0 | 0 |

**105 battles gain requests**, 292 Earthquake-family requests become fully modelled;
all 294 family verdicts change, including two that retain refusal. Those two are
Earthquake in `TRAINER_MEG_AND_PEG_HNS` slot 1, one per reference team, refused
because it is Doubles. The starting 202 move-only opportunity was an upper bound,
not a cap: re-evaluation removes obsolete field/ability predicates where the new
source-proven family supplies the missing authority. No blocker was globally
removed and no unrelated request changed.

The admission-only negative controls compiled on the immutable starting SHA and
failed solely with the intended move-mechanics refusal; all 1,118 other Kotlin
tests passed. Hardware remains **NOT_RUN**. Final non-generating `./ci.sh all` and pinned `./ci.sh source-check` pass,
including **1,128 Kotlin tests, zero failures/skips**, generator verification,
31 move-generator mutation tests, 62 oracle-tool tests and census freshness.
The oracle and slice report checks plus `git diff --check` pass. PR-head Actions
are reported with the review handoff.

## Remaining scope and safe Thor checklist

Excluded: Doubles; Bulldoze underground; both moves underwater, airborne,
Phantom Force, Sky Drop and Commander; unknown semi-state; Magnitude, Dig,
Fissure, Surf/Whirlpool and all other flag-sharing moves. No arbitrary No Guard
breakthrough, accuracy probability, multiple hits, spread totals or Speed result.
All independent ability/item/field/gimmick/survival limitations remain.

Use an existing exact-verified H&S 2.0.5 image/state without modifying a real save
solely to manufacture evidence:

- Neutral Earthquake and neutral Bulldoze.
- Grassy Terrain Earthquake and Bulldoze.
- Underground target with Earthquake; Bulldoze refusal.
- One unsupported positive semi-state refusal.
- Ground immunity / Levitate control.
- Effective Sheer Force Bulldoze.
- Reload/battle transition/participant replacement clears stale semi-state.

Next bounded family: Explosion, with Damp execution and modern Defense proof.
Repeated strikes still need their separate multiple-hit product contract.
