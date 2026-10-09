# H&S 2.0.5 calculator capability matrix (issue #9)

## Final pre-beta state (Slice 17, Rapid Spin)

The H&S calculator has completed its planned pre-beta feature development. New mechanic coverage is deferred until after the first public beta. Only correctness and beta-blocking fixes are allowed during the freeze, which takes effect when the reviewed final-calculator PR merges and lasts until after the public `0.9.0-beta.1` release.

- Admitted: 422 H&S move IDs across the families listed in [CALCULATOR_BETA_FREEZE.md](CALCULATOR_BETA_FREEZE.md) §2. Rapid Spin (229) is the final admission, as a Singles selected hit; Mortal Spin is not admitted.
- Census after Slice 17: 21,576 FULLY_MODELLED, 462 CAVEATED_ESTIMATE, 2,240 REFUSED of 24,278 eligible requests. Displayable lead pairs 684/1302. See [HNS_CALCULATOR_FINAL_PREBETA_ACCEPTANCE.md](HNS_CALCULATOR_FINAL_PREBETA_ACCEPTANCE.md).
- Support remains ESTIMATED. Hardware acceptance remains `NOT_RUN`.

## Slice 10: observed Rollout / Ice Ball selected hit

## Slice 10: observed Rollout / Ice Ball selected hit

See [the slice-10 authority and evidence](HNS_MOVE_COVERAGE_SLICE_10.md). Only
Rollout 205 and Ice Ball 301 are admitted with exact stable source chain/Curl/lock
operands. Unknown packets, raw timers outside 0–4, nonzero recharge and
contradictory active locks refuse. Tuple indices 165–173 add compiled raw fields;
existing slot, type, modifier and switch-in authority remains required. H&S stays
ESTIMATED; Hardware: NOT_RUN.

## Singles Electro Ball — slice 9 (#139)

Electro Ball (486), EFFECT_ELECTRO_BALL, has its own generated fixed-single-hit
family. It reuses the slice-8 effective-Speed authority and maps integer A/D to
`[40,60,80,120,150][min(A // D,4)]`. Attacker zero yields BP40; exact defender
zero refuses with HNS_ELECTRO_BALL_DEFENDER_SPEED_ZERO before calculator execution.
Technician reads dynamic power (40/60 boost, 80 does not), and shared Electric
modifiers and ballistic immunity remain independent. Raw Electric Terrain Speed
activation and grounded terrain damage use separate predicates. Source placeholder
1 displays **Variable**. Neutral status, Singles, no Substitute and neutral
semi-state remain required; Unburden and unsupported weather refuse.
See [slice 9 source, oracle and census evidence](HNS_MOVE_COVERAGE_SLICE_9.md).
H&S remains **ESTIMATED**.


## Singles Gyro Ball — slice 8 (#137)

Gyro Ball (360), EFFECT_GYRO_BALL, uses its separate generated family and exact
boundary-owned effective Speeds. Dynamic integer power precedes Technician;
source power 1 is only a placeholder. Speed authority preserves stages, weather
suppression/Umbrella, Slow Start/Paradox, badge, active speed items, Tailwind and
Swamp in source order. Neutral observed status is required; Unburden, unsupported
weather, Substitute, nonneutral semi-state and Doubles remain refused. No caller
Speed or turn order authorizes the ratio. See [slice 8 evidence](HNS_MOVE_COVERAGE_SLICE_8.md).
The overall ceiling remains **ESTIMATED**.


## Status-dependent Singles base power (slice 6, #132)

The separate FIXED_SINGLE_HIT_STATUS_DOUBLE family admits Smelling Salts (265),
Wake-Up Slap (358), Venoshock (474), Hex (506), Barb Barrage (767) and Infernal
Parade (772), using generated source masks and validated boundary-owned defender
status1. Integer doubling precedes Technician and Sheer Force. Defender Comatose
contributes only the source sleep predicate for this exact family; other Comatose
behavior remains outside scope. Raw status is preserved, post-hit removal/secondary
status is excluded from the displayed hit, and active Substitute/Doubles remain
refused. See [slice 6 evidence](HNS_MOVE_COVERAGE_SLICE_6.md). H&S remains ESTIMATED.


## Current move coverage — #130

Authoritative Singles admits exactly Surf (57) and Whirlpool (250) in the separate
source-generated FIXED_SINGLE_HIT_UNDERWATER family. Defender neutral (0) and
underwater (2) states use the existing trusted JNI index-51 observation. Other
positive states, unknown/invalid state and Doubles remain refused. The source
underwater flag permits Dive-state breakthrough without guaranteeing accuracy;
×2 enters the ordered Dive final-modifier slot, after Underground and before
Airborne/screens. Water immunity remains independent. Whirlpool wrap, duration,
residual and escape are outside the selected-hit result; Binding Band receives
only the named Whirlpool proof, with its global damage-relevant category intact.
H&S remains **ESTIMATED**, one selected hit × sixteen damage rolls.
See [slice 5 evidence](HNS_MOVE_COVERAGE_SLICE_5.md).

## Move coverage slice 4 — #128

Authoritative Singles admits exactly Self-Destruct (120) and Explosion (153) in a
separate source-generated family. Effective Damp is a hard execution refusal,
self-KO derives attacker HP=0 before damage, Defense follows pinned GEN_LATEST,
and Parental Bond is banned. The displayed result remains one defender hit ×
sixteen rolls; H&S remains ESTIMATED. Gastro Acid/Neutralizing Gas and Wonder Room
retain their existing independent refusal/caveat contracts. See
[the slice 4 evidence](HNS_MOVE_COVERAGE_SLICE_4.md).

## Move coverage slice 1 — #122

The separately classified fixed single-hit recoil family is admitted in authoritative
Singles contexts: Take Down, Double-Edge, Submission, Volt Tackle, Flare Blitz, Brave Bird,
Wood Hammer, Head Smash, Wild Charge, Head Charge, Light of Ruin and Wave Crash.
Reckless uses the exact pinned attacker base-power ×1.2 slot. The ordinary move set is
unchanged. Doubles recoil, active resist-berry admission on recoil, pre-hit Flare Blitz
Freeze/Frostbite clearing and independent unknown/unsupported contexts remain refused.
The selected-hit output and H&S `ESTIMATED` trust ceiling are unchanged.
See [the dedicated source, authority and coverage evidence](HNS_MOVE_COVERAGE_SLICE_1.md).


## Group D damage-modifier stages — #91 slice

The H&S path in `tools/calc-bundler/entry.js` now has named pipeline boundaries for the
effective move type, base-power UQ4.12 accumulation, Attack-stat UQ4.12 accumulation,
Defense-stat UQ4.12 accumulation, base damage, and final-damage modifiers. Stat-stage products
continue to use their pinned half-down operation. `CalcMoveBasePowerAfterModifiers` composes its
base-power modifier product with `uq4_12_multiply` (half-up), then applies that product once to the
integer source move power with `uq4_12_multiply_by_int_half_down`. This preserves pinned modifier
composition, including combined ability, Dry Skin, and Wise Glasses cases. Final damage steps still
round in their pinned sequence; only modifiers that upstream itself combines inside
`GetOtherModifiers` use a final-stage accumulator.

The stat modifier accumulator preserves each source factor's multiplication operator and exact
`CalcAttackStat` order. The completed modifier is applied once to the integer staged stat with
`uq4_12_multiply_by_int_half_down`:

| Ability | Stage | Predicate | Factor | Product operator |
|---|---|---|---:|---|
| Solar Power | Attack stat | Final authoritative category is Special and the attacker is affected by ordinary Sun | ×1.5 | `uq4_12_multiply_half_down` |
| Defeatist | Attack stat | Authoritative live HP `<= floor(maxHP/2)` | ×0.5 | `uq4_12_multiply_half_down` |
| Hustle / Guts | Attack stat | Physical; Guts also requires status | ×1.5 | `uq4_12_multiply_half_down` |
| Transistor | Attack stat | Final effective type is Electric | pinned ×1.3 (`5325` / `0x14CD`) | `uq4_12_multiply` (half-up) |
| Dragon's Maw | Attack stat | Final effective type is Dragon | ×1.5 (`6144` / `0x1800`) | `uq4_12_multiply` (half-up) |
| Rocky Payload | Attack stat | Final effective type is Rock | ×1.5 (`6144` / `0x1800`) | `uq4_12_multiply` (half-up) |
| Orichalcum Pulse | Attack stat | Physical + active Sun context + no Utility Umbrella | pinned ×1.3333 (`5461` / `0x1555`) | `uq4_12_multiply` (half-up) |
| Flower Gift | Attack stat | Live Cherrim-Sunshine holder, Sun-affected, Physical | ×1.5 | `uq4_12_multiply_half_down` |
| Fur Coat | Defense stat | The supported hit uses Defense | ×2.0 | `uq4_12_multiply_half_down` |
| Marvel Scale | Defense stat | Defender has raw live status and the selected hit uses Defense | ×1.5 (`6144`) | `uq4_12_multiply_half_down` |
| Flower Gift | Defense stat | Live Cherrim-Sunshine defender, Sun-affected, selected hit uses Sp. Def | ×1.5 | `uq4_12_multiply_half_down` |

Half-up is not generalized to all stat modifiers. Solar Power, Defeatist, Hustle, Guts, pinch
abilities, Thick Fat, and badges retain their source half-down operator. Every factor is composed
in source order, and the product is applied to the staged integer once.

There is no source-valid single-hit vector in this pinned `CalcAttackStat` slice where changing one
of these four abilities from half-up to half-down changes the final damage. The attacker-ability
subgroup begins at identity (`4096`) and these abilities are mutually exclusive slot values, so its
first multiplication is exact under either operator; later Attack-stat factors are half-down. The
badge and stage oracle vectors verify real source composition, while
`tools/calc-bundler/test_fixed_point.js` exercises a synthetic multi-factor sequence that separates
the two operators. The synthetic arithmetic check is not presented as a battle-valid discrepancy.

Solar Power's selected-hit Special Attack boost is modelled; its end-of-turn HP loss is outside the
displayed single-hit result. Its weather predicate honors weather suppression by Cloud Nine/Air Lock
and attacker Utility Umbrella. Those identities are consumed only to evaluate this predicate:
Utility Umbrella is not promoted, and the suppressors' independent capability rules remain active.
Fur Coat is breakable under the centralized Mold Breaker and literal `ignoresTargetAbility`
authority; unshielded relevant suppression remains refused, while Ability Shield preserves it.
Active Wonder Room and nonordinary alternate-defense selections remain blocked.

### Remaining low-state Group D — this slice

The pinned-source conditional rules now model Marvel Scale (63), holder-side Singles Flower Gift
(122), Sheer Force (125), Tough Claws (181), and Fluffy (218). Their request-local predicates use
live effective ability/item, raw status, current species/form, selected effective type/category,
existing weather/field, and source-generated move metadata. Marvel Scale and defender Flower Gift
use the selected Defense/Sp. Def stat; Wonder Room stays blocked. Flower Gift's partner branch
(`src/battle_util.c:7141-7149`) remains deferred with Doubles.

Tough Claws and Fluffy share `HnsContactAuthority`: exact pinned move ID/effect, generated
`MoveMakesContact` and punching metadata, effective attacker ability/item, and the ordinary-move
gate produce CONTACT, NON_CONTACT, or UNKNOWN. Punching Glove suppresses contact only for source
punching moves; Long Reach suppresses it; Protective Pads does not alter this helper. Shell Side
Arm is explicitly outside the ordinary supported move surface. Sheer Force uses the source-derived
`MoveIsAffectedBySheerForce` predicate, generated from pinned `AdditionalEffect` chance/override
data; caller secondary-effect/contact assertions cannot override either fact. Punching Glove's own
damage-item modifier remains an independent #92 blocker even when it suppresses contact.

| Ability | Supported request-local outcome |
|---|---|
| Reckless (120) | Provably irrelevant for ordinary moves; recoil effects remain outside the ordinary gate. |
| Sand Force (159) | Provably irrelevant outside Sandstorm; Sandstorm itself remains unsupported in production. |
| Battery (217), Power Spot (249) | Provably irrelevant in observed Singles; partner/Doubles branch remains deferred. |

The base-power ability accumulator uses `uq4_12_multiply` (half-up), then applies the composed
product once to integer move power:

| Ability | Source stage and exact predicate | Factor |
|---|---|---:|
| Sheer Force (125) | Attacker ability slot; generated `MoveIsAffectedBySheerForce(move)` is true | ×1.3 (`5325` / `0x14CD`) |
| Sand Force (159) | Attacker slot; raw Sandstorm context and final effective type is Rock, Ground, or Steel | ×1.3 (`5325` / `0x14CD`) |
| Tough Claws (181) | Attacker slot; shared contact authority returns CONTACT | ×1.3 (`5325` / `0x14CD`) |

Fluffy (218) is a defender final-damage modifier: Fire/non-contact ×2.0 (`8192`), Fire/contact
×1.0, non-Fire/contact ×0.5 (`2048`), and non-Fire/non-contact ×1.0. Its suppression follows
the centralized breakable-ability policy and Ability Shield handling. Reckless (120), Battery
(217), and Power Spot (249) add no positive production context here; their known false predicates
clear only that ability's request-local limitation.

The next state-reader-dependent Group D slice remains Slow Start (timer), Rivalry (gender),
Analytic (actual move order), Stakeout (switch-in history), Supreme Overlord (fainted-party count),
Protosynthesis and Quark Drive (boosted-stat/Booster Energy payload), Gorilla Tactics (selected
Dynamax authority), Dark Aura/Fairy Aura/Aura Break (field-wide identities), and the four Ruin
abilities (field-wide activation). Positive Battery/Power Spot and partner Steely Spirit also remain
outside Singles. Parental Bond, Skill Link, and item-dependent move formulas remain outside this
slice; direct-damage held-item modifiers are covered by issue #92.

Orichalcum Pulse checks `ctx->weather & B_WEATHER_SUN`, final Physical category, and the attacker's
Utility Umbrella hold effect. It does not call `IsBattlerWeatherAffected`; however,
`CalculateAndSetMoveDamage` initializes `ctx.weather` with `GetWeather()`, which returns
`B_WEATHER_NONE` when `HasWeatherEffect()` is false. Thus a live Cloud Nine or Air Lock suppresses
this branch through the upstream context, even though the ability case reads the context word
directly; positive HP is required to prove the suppressor is active because `HasWeatherEffect()`
skips fainted battlers. Its separate weather-setting battle event is not modelled; the calculator
uses only already-observed live weather and never invents Sun. Since an active holder sets Sun on entry, the
no-Sun oracle control observes Rain Dance replacing that weather before the measured hit. Utility
Umbrella is not globally promoted by this ability rule.

Pinned `ApplyModifiersAfterDmgRoll` applies STAB, type effectiveness, burn, then the accumulated
`GetOtherModifiers` value. That product starts with target/screen modifiers; the ability and item
subgroups then use unmodified battler speed to order attacker abilities → defender abilities →
defender-partner abilities → attacker items → defender items when attacker speed is at least
defender speed, and defender abilities → defender-partner abilities → attacker abilities →
defender items → attacker items otherwise. The current Singles slice models Screens, Punk Rock,
Neuroforce, Sniper, Tinted Lens, Filter, Solid Rock, Prism Armor, Multiscale, Shadow Shield, and
Ice Scales in those source slots; unsupported partner and item branches remain gated.

The #105 slice added nine base-power ability decisions, #106 added six move-type rewrite decisions,
and #107 added Punk Rock plus holder-side Steely Spirit in the final-stage scaffold. This #91 slice
adds defender Water Bubble and Heatproof to the target base-power slot and promotes Adaptability's
exact STAB branch. Water Bubble's attacker Water ×2 and defender Fire ×0.5 branches remain separate;
its burn-prevention/status-clearing path is still deferred. Heatproof's burn residual and switch-AI
behavior remain outside the selected-hit result. Steely Spirit's separate attacker-partner branch
remains deferred with unsupported Doubles topology.

This stat slice models Solar Power, Defeatist, Fur Coat, Transistor, Dragon's Maw, Rocky Payload,
Orichalcum Pulse, and holder-side Flower Gift in their pinned Attack/Defense stages. It consumes only boundary-owned live
HP, item, ability, weather, field, and HnsMoveAuthority final type/category facts. It does not
model Solar Power residual damage, Utility Umbrella as a general damage item, Orichalcum Pulse's
separate weather-setting event, or Fur Coat under Wonder Room/nonordinary alternate-defense selection.

| Ability | Exact modelled context | Request-local irrelevant contexts | Missing or conflicting evidence |
|---|---|---|---|
| Technician (101) | Pinned ordinary move has source power ≤60; ×1.5 | Defender role or source power >60 | Unresolved move/power or nonordinary move stays unknown/refused |
| Iron Fist (89) | Pinned `punchingMove`; ×1.2 | Defender or known ordinary move without the bit | Conditional/computed move flag stays unknown/refused |
| Strong Jaw (173) | Pinned `bitingMove`; ×1.5 | Defender or known ordinary move without the bit | Conditional/computed move flag stays unknown/refused |
| Mega Launcher (178) | Pinned `pulseMove`; ×1.5 | Defender or known ordinary move without the bit | Conditional/computed move flag stays unknown/refused |
| Sharpness (292) | Pinned `slicingMove`; ×1.5 | Defender or known ordinary move without the bit | Conditional/computed move flag stays unknown/refused |
| Water Bubble (199), direct-hit branches | Attacker final Water move → ×2 BP; defender incoming final Fire move → ×0.5 BP | Attacker known non-Water; defender known non-Fire | Unknown final type stays unknown; burn prevention/status clearing remains separately deferred |
| Heatproof (85), direct-hit branch | Defender incoming final Fire move → ×0.5 BP, regardless of Physical/Special category | Attacker Heatproof; defender known non-Fire move | Unknown final type stays unknown; burn residual/status and switch-AI behavior remain outside the selected-hit result |
| Dry Skin (87), neighboring control | Water immunity remains in Group C; defender incoming Fire move → ×1.25 BP | Attacker role for the Fire BP branch; defender known non-Fire move | Its Fire modifier is not a reduction; missing final type stays unknown |
| Steelworker (200) | Attacker uses authoritative effective Steel move; ×1.5 | Defender or attacker non-Steel move | Unknown effective type stays unknown/refused |
| Punk Rock (244), attacker | Source-proven sound move; ×1.3 base power composed half-up | Nonsound ordinary move; any defender-side nonsound hit | Unknown/computed sound metadata stays unknown/refused |
| Punk Rock (244), defender | Incoming source-proven sound move; ×0.5 defender-ability modifier inside the `GetOtherModifiers` product; screens precede that ability slot, and the completed product applies after STAB/effectiveness/burn | Nonsound ordinary incoming move | Unknown/computed sound metadata stays unknown/refused; an attacking Mold Breaker-family ability keeps suppression interactions refused |
| Steely Spirit (252), holder | Final `HnsMoveAuthority.effectiveType` is Steel; ×1.5 base power composed half-up | Known final non-Steel move; defender-side Singles holder | Unknown final type stays unknown/refused; attacker-partner branch deferred |
| Toxic Boost (137) | Attacker's authoritative category is Physical and raw `status1 & STATUS1_PSN_ANY != 0`; ×1.5 | Defender, Special move, or known status without either poison bit | Unread/undefined status or conflicting status stays unknown/refused; Toxic Counter bits are allowed only with `STATUS1_TOXIC_POISON` |
| Flare Boost (138) | Attacker's authoritative category is Special and raw `status1 & STATUS1_BURN != 0`; ×1.5 | Defender, Physical move, or known status without burn | Unread/undefined status or conflicting status stays unknown/refused |
| Adaptability (91), STAB | `HnsMoveAuthority.effectiveType` is in authoritative live attacker types; STAB ×2.0 (UQ4.12 8192 / `0x2000`) | Defender side or known nonmatching final type | Unknown move/type/live typing/topology or active unsupported gimmick stays blocked |
| Tinted Lens (110), attacker slot | Exact H&S effectiveness ≤0.5; ×2.0 (8192 / `0x2000`) | Defender role or effectiveness >0.5 | Unknown effectiveness stays blocked; a type/ability immunity still returns zero |
| Sniper (97), attacker slot | Selected hit's authoritative calculator crit flag; additional ×1.5 (6144 / `0x1800`) after the normal crit stage | Defender role or selected noncritical hit | Missing crit selection stays blocked; this does not change crit odds or the existing crit multiplier |
| Neuroforce (233), attacker slot | Exact H&S effectiveness ≥2.0; ×1.25 (5120 / `0x1400`) | Defender role or effectiveness <2.0 | Unknown effectiveness stays blocked |
| Filter / Solid Rock (111/116), defender slot | Exact H&S effectiveness ≥2.0; ×0.75 (3072 / `0x0C00`) | Attacker role or effectiveness <2.0 | Unknown effectiveness stays blocked; both are breakable and shield-aware (policy below) |
| Prism Armor (232), defender slot | Exact H&S effectiveness ≥2.0; ×0.75 (3072 / `0x0C00`) | Attacker role or effectiveness <2.0 | Unknown effectiveness stays blocked; pinned metadata does not mark it breakable (policy below) |
| Multiscale / Shadow Shield (136/231), defender slot | Exact authoritative live `hp == maxHP`; ×0.5 (2048 / `0x0800`) | Attacker role or `0 < hp < maxHP` | Missing/invalid HP stays blocked; Multiscale is breakable, Shadow Shield is not (policy below) |
| Ice Scales (246), defender slot | `HnsMoveAuthority` final category is Special; ×0.5 (2048 / `0x0800`) | Attacker role or final Physical category | Unknown category stays blocked; it is breakable and shield-aware (policy below) |
| Solar Power (94), attacker Attack-stat slot | `HnsMoveAuthority` final category is Special and live `IsBattlerWeatherAffected(attacker, B_WEATHER_SUN)` is true; ×1.5 Sp. Atk (6144 / `0x1800`) | Defender role, final Physical category, no ordinary Sun, Cloud Nine/Air Lock suppression, or attacker Utility Umbrella | Missing live weather/category/ability/item evidence stays unknown; Utility Umbrella's independent item limitation and weather-suppressor limitations remain; residual maxHP/8 loss is outside selected-hit output |
| Defeatist (129), attacker Attack-stat slot | Authoritative live HP `<= floor(maxHP/2)`; ×0.5 to selected Attack or Sp. Atk (2048 / `0x0800`) | Defender role or valid live HP above integer half | Missing, invalid, or out-of-range HP/maxHP stays unknown |
| Fur Coat (169), defender Defense-stat slot | Ordinary move, authoritative final Physical category, Wonder Room inactive; ×2.0 Defense (8192 / `0x2000`) | Attacker role or final Special category selecting Sp. Def | Wonder Room and nonordinary alternate-defense selection stay blocked; Fur Coat is breakable, with unshielded suppression refused and Ability Shield preserving it |
| Transistor (262), attacker Attack-stat slot | Final `HnsMoveAuthority.effectiveType` is Electric; pinned Gen 9 ×1.3 (5325 / `0x14CD`, half-up product) | Defender role or known final non-Electric type | Unknown final type/live ability state stays unknown |
| Dragon's Maw (263), attacker Attack-stat slot | Final effective type is Dragon; ×1.5 (6144 / `0x1800`, half-up product) | Defender role or known final non-Dragon type | Unknown final type/live ability state stays unknown |
| Rocky Payload (276), attacker Attack-stat slot | Final effective type is Rock; ×1.5 (6144 / `0x1800`, half-up product) | Defender role or known final non-Rock type | Unknown final type/live ability state stays unknown |
| Orichalcum Pulse (288), attacker Attack-stat slot | Final category Physical + active Sun from `GetWeather` + attacker not holding Utility Umbrella; ×1.3333 (5461 / `0x1555`, half-up product) | Defender role, final Special category, known no-Sun context, active Cloud Nine/Air Lock, or Utility Umbrella | Missing category/weather/item or suppressor liveness stays unknown; weather-setting event is represented only by observed weather |

All source-proven damaging sound moves in the pinned ordinary-move surface are Special; the pinned
corpus contains no supported Physical sound move, so Punk Rock is deliberately not category-gated.
Howl's computed `soundMove` field stays unknown and is refused. The #106 rewrite targets do not
include Steel; Normalize rewriting Iron Head from Steel to Normal verifies the negative Steely Spirit
cross-product, and there is no positive rewrite-into-Steel case in the current supported set.

The final-slot operands are bound from live battle observations and `HnsMoveAuthority`: exact
effective type, defender effectiveness, final category, and HP/max HP do not come from caller
overrides. Sniper uses the calculator's selected-hit critical flag; that selection chooses the
hypothetical crit damage line and does not claim to predict the game's crit roll.

| Defender ability | Pinned breakable flag | Unshielded Mold Breaker-family attacker | Ability Shield |
|---|---|---|---|
| Filter | Breakable | Relevant suppression remains refused by the existing production policy | Preserves Filter and its ×0.75 modifier |
| Solid Rock | Breakable | Relevant suppression remains refused by the existing production policy | Preserves Solid Rock and its ×0.75 modifier |
| Multiscale | Breakable | Relevant suppression remains refused by the existing production policy | Preserves Multiscale and its ×0.5 modifier |
| Ice Scales | Breakable | Relevant suppression remains refused by the existing production policy | Preserves Ice Scales and its ×0.5 modifier |
| Prism Armor | Not breakable | Does not suppress Prism Armor and adds no Mold Breaker blocker | Preserves Prism Armor and its ×0.75 modifier |
| Shadow Shield | Not breakable | Does not suppress Shadow Shield and adds no Mold Breaker blocker | Preserves Shadow Shield and its ×0.5 modifier |

Filter, Solid Rock, Prism Armor, Multiscale, Shadow Shield, and Ice Scales are defender-slot
factors inside the speed-ordered `GetOtherModifiers` product. Adaptability remains in STAB. The
production boundary does not admit Doubles, partner abilities, final-item branches, unsupported
gimmicks, or requests missing any operand required by a condition.

### Canonical census change from the starting-main baseline

`docs/HNS_CALC_CENSUS.md` and `tools/hns-calc-census/census.json.gz` were regenerated from the
production policy and pinned 651-battle trainer inventory. The baseline is starting `main` at
`69d5eab757b7ca56e76b6c98369c17ea5eb8c99e` (after #108):

| Metric | Starting main | This slice | Change |
|---|---:|---:|---:|
| Eligible damaging requests | 24,278 | 24,278 | unchanged |
| `FULLY_MODELLED` requests | 18,720 | 18,790 | +70 |
| `CAVEATED_ESTIMATE` requests | 376 | 370 | -6 |
| `REFUSED` requests | 5,182 | 5,118 | -64 |
| Fully displaying lead matchups | 370 / 1,302 | 372 / 1,302 | +2 pairs |
| Displayable requests in lead matchups | 6,652 | 6,676 | +24 |
| Newly displayable trainer requests | 0 | 64 | +64; all fully modelled |
| Battles gaining a newly displayable request | 0 / 651 | 5 / 651 | Clair: 16; Falkner: 14; Lance: 14; Blaine: 10; Blue: 10 |
| `HNS_MOVE_MECHANICS_NOT_MODELLED` | 557 battles / 3,578 requests | 557 / 3,578 | unchanged |
| `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` | 68 / 152 | 68 / 152 | unchanged |
| `HNS_ABILITY_EFFECT_NOT_MODELLED` | 222 battles / 1,862 requests | 221 / 1,786 | -1 battle / -76 requests |
| `HNS_ABILITY_CONDITION_UNVERIFIED` | 77 / 268 | 80 / 276 | +3 battles / +8 requests |

The current ten highest-ranked blockers remain `HNS_MOVE_MECHANICS_NOT_MODELLED` (557 battles /
3,578 requests), `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` (68 / 152), Chlorophyll (66 / 228),
Intimidate (64 / 218), Swift Swim (38 / 150), defender Sturdy (29 / 232), attacker Sturdy
(29 / 176), Swarm (28 / 118), Quick Claw (28 / 78), and Leftovers (25 / 70).

| Ability | Pinned trainer holders | Trainer request effect | Random Abilities refused / caveated / clear trials, before → after |
|---|---:|---|---|
| Adaptability | 3 Porygon-Z | 6 caveated → fully modelled | 80 / 2,709 / 9,397 → 80 / 0 / 12,106 |
| Tinted Lens | 1 Noctowl | 14 refused → fully modelled | 12,186 / 0 / 0 → 0 / 0 / 12,186 |
| Sniper | 6 Pokémon | 8 refusals narrowed to unknown-crit condition; still refused | 1,758 / 0 / 10,428 → 0 / 0 / 12,186 |
| Neuroforce | 0 | No trainer holders | 12,186 / 0 / 0 → 0 / 0 / 12,186 |
| Filter | 0 | No trainer holders | 12,186 / 0 / 0 → 1,758 / 0 / 10,428 |
| Solid Rock | 2 | 20 refused → fully modelled; 4 still refused by other limits | 12,186 / 0 / 0 → 1,758 / 0 / 10,428 |
| Prism Armor | 0 | No trainer holders | 12,186 / 0 / 0 → 1,758 / 0 / 10,428 |
| Multiscale | 2 Dragonite | 30 refused → fully modelled | 12,186 / 0 / 0 → 1,758 / 0 / 10,428 |
| Shadow Shield | 0 | No trainer holders | 12,186 / 0 / 0 → 1,758 / 0 / 10,428 |
| Ice Scales | 0 | No trainer holders | 12,186 / 0 / 0 → 1,758 / 0 / 10,428 |

Random Abilities weighted request trials changed from 1,503,008 refused / 19,529 caveated /
2,255,123 clear to 1,414,310 / 16,820 / 2,346,530. Identities with at least one refusal changed
from 185 to 182; identities with caveated contexts from 5 to 4; clear-only identities from 122 to
125. The six defender reductions remain refused in 1,758 trials because effectiveness is unknown;
the other 10,428 trials prove the ability irrelevant. The pinned trainer corpus has two fully
displayable new lead pairs, both Falkner's, and 64 total request-level refusals became fully modelled.
The ambiguous-opposite-ability cohort exclusions remain 16,974 attacker-side and 19,396
defender-side requests.

### This PR's low-state Group D census delta

This comparison uses the exact starting `main` SHA `f822410fdb7d7798ca23b757a0cdc313266ec74f`
and the full production-policy census over the same 651 pinned trainer battles and 24,278 eligible
requests. `docs/HNS_CALC_CENSUS.md` and `tools/hns-calc-census/census.json.gz` contain the generated
after-state; neither generated artifact was hand-edited.

| Metric | Starting main | This PR | Change |
|---|---:|---:|---:|
| `FULLY_MODELLED` requests | 18,800 | 18,864 | +64 |
| `CAVEATED_ESTIMATE` requests | 372 | 388 | +16 |
| `REFUSED` requests | 5,106 | 5,026 | -80 |
| Fully displaying lead matchups | 372 / 1,302 | 372 / 1,302 | unchanged |
| Displayable requests in lead matchups | 6,676 | 6,690 | +14 |
| `HNS_ABILITY_EFFECT_NOT_MODELLED` | 221 battles / 1,772 requests | 218 / 1,674 | -3 battles / -98 requests |
| `HNS_ABILITY_CONDITION_UNVERIFIED` | 80 battles / 276 requests | 82 / 286 | +2 battles / +10 requests |

Random Abilities weighted trials change from 1,297,908 refused / 16,820 caveated / 2,462,932 clear
to 1,193,722 / 16,820 / 2,567,118. Identities with at least one refusal fall from 180 to 176;
caveated identities remain 4; clear-only identities rise from 127 to 131.

| Ability | Random Abilities refused / caveated / clear, starting main → this PR |
|---|---|
| Marvel Scale (63) | 12,186 / 0 / 0 → 1,758 / 0 / 10,428 |
| Flower Gift (122) | 12,186 / 0 / 0 → 1,758 / 0 / 10,428 |
| Sheer Force (125) | 12,186 / 0 / 0 → 0 / 0 / 12,186 |
| Tough Claws (181) | 12,186 / 0 / 0 → 0 / 0 / 12,186 |
| Fluffy (218) | 12,186 / 0 / 0 → 1,812 / 0 / 10,374 |
| Reckless (120) | 12,186 / 0 / 0 → 0 / 0 / 12,186 |
| Sand Force (159) | 12,186 / 0 / 0 → 0 / 0 / 12,186 |
| Battery (217) | 12,186 / 0 / 0 → 80 / 0 / 12,106 |
| Power Spot (249) | 12,186 / 0 / 0 → 80 / 0 / 12,106 |

Battery and Power Spot retain 80 refused trials each because the census includes unsupported
Doubles contexts. Marvel Scale and Flower Gift retain only their unresolved active contexts; Fluffy
retains unknown contact contexts. The remaining trial counts are policy-proven clear outcomes, not
claims that the positive branch is always inactive.

The preceding Attack-stat slice remains in scope: Hustle (55) applies ×1.5 to an attacker-side
Physical move; Guts (62) does the same when authoritative raw `status1 & STATUS1_ANY != 0` and
suppresses burn's physical-damage halving. Defender role, Special category, and source-proven
neutral statuses are request-local irrelevant; unread categories/status words remain refused.

`CalcRequestBoundary` binds effective ability, source move ID/power/flags, effective type/category,
and raw live status word to the active battler. `DamageCalculator` serializes generated move flags
and raw `status1`; display text, caller-owned flag fields, or a species-default ability cannot
authorize a branch. The pinned test runner records the same flags and status word in the oracle.

Pinned source order (`src/battle_util.c:6547-6871`) is: move-effect modifiers, general battle-state
modifiers, attacker ability, field aura, living attacker-partner ability, defender ability, attacker
held item, then the Tera weak-move floor before the integer base power is returned. Every ability
modifier in this batch uses `uq4_12_multiply` (half-up) while composing the product; the final
product uses `uq4_12_multiply_by_int_half_down` once against integer base power. The admitted path
preserves relative order for the modifiers it calculates: attacker ability, existing defender Dry
Skin, then held-item modifier. Move-effect/general-state, aura/partner, other defender-ability,
unsupported item, and Tera contexts remain fail-closed under their existing gates.

### Historical candidate audit (pre-#111 triage snapshot; current-state note)

This table records historical triage decisions and does not describe the current implementation
status. PR #111 implemented Solar Power, Defeatist, and Fur Coat. This PR implements Transistor,
Dragon's Maw, Rocky Payload, and Orichalcum Pulse as documented in the current stat-stage table;
their historical deferral rows below are retained only to preserve the original snapshot.

| Candidate | Decision | Source-backed reason |
|---|---|---|
| Solar Power | Defer — live state required | Special move plus effective, unsuppressed sun (`IsBattlerWeatherAffected`); the current damage request does not establish all weather-suppression effects for this ability modifier. |
| Defeatist | Defer — semantics not yet proven | Source predicate is live HP ≤ maxHP/2 (`src/battle_util.c:7002`); this first slice does not yet have the HP threshold boundary matrix and combined-modifier oracle proof. |
| Gorilla Tactics | Defer — live state required | Physical move modifier is disabled by selected or active Dynamax (`src/battle_util.c:7073-7075`); active gimmicks are refused by current production policy. |
| Transistor | Defer — semantics not yet proven | The pinned `B_TRANSISTOR_BOOST` config selects the Gen 9 ratio (`src/battle_util.c:7060-7067`, `include/config/battle.h:185`); exact configuration/rounding cross-cases are not in this oracle slice. |
| Dragon's Maw, Rocky Payload | Defer — semantics not yet proven | Each is an effective-type Attack-stat modifier (`src/battle_util.c:7069-7081`); matching and nonmatching type controls plus badge-composition vectors remain to be proven. |
| Flower Gift | Defer — doubles/partner state and live state required | The attacker branch requires Cherrim Sunshine form, effective sun, and Physical category; a separate ally branch reads the partner (`src/battle_util.c:7044-7046`, `:7146`). |
| Orichalcum Pulse | Defer — live state required | Requires effective sun, Physical category, and no Utility Umbrella (`src/battle_util.c:7105-7107`); weather suppression and the item side are not admitted here. |
| Sword of Ruin / Beads of Ruin | Defer — later pipeline stage and doubles/side state | These modify the relevant Defense stat through Ruin field state (`src/battle_util.c:10746-10749`), which belongs to a Defense/field batch with exact side and multi-ability semantics. |
| Reckless | Defer — move-effect classification follow-up | The positive branch is limited to `EFFECT_RECOIL` and `EFFECT_RECOIL_IF_MISS` (`src/battle_util.c:6667-6670`). The current batch does not promote a move-effect negative proof into a new ability clearance. |
| Sheer Force | Defer — exact helper predicate absent | Source calls `MoveIsAffectedBySheerForce(move)` (`src/battle_util.c:6675-6677`, helper at `:9430`); “has a secondary effect” is not an equivalent source predicate. |
| Tough Claws | Defer — contact semantics exceed static move metadata | Source calls `IsMoveMakingContact` (`src/battle_util.c:6694-6696`), which also reads Shell Side Arm category, Punching Glove, and Long Reach (`:5868-5890`). |
| Sand Force | Defer — weather semantics unsupported | It requires `ctx->weather & B_WEATHER_SANDSTORM` plus a Rock/Ground/Steel move (`src/battle_util.c:6679-6682`); production admits only clear, Rain, or Sun. |
| Rivalry | Defer — live gender missing | Its source branch distinguishes same/opposite battler gender (`src/battle_util.c:6684-6688`); the active runtime tuple carries neither gender operand. |
| Analytic | Defer — turn order missing | It calls `IsLastMonToMove` (`src/battle_util.c:6690-6692`), which reads battler order, alive battlers, and queued actions (`:1183-1199`). |
| Stakeout | Defer — switch-in history missing | Its damage branch depends on the defender's just-switched-in flag (`src/battle_util.c:7052-7054`), not merely on the settled switch-in event driver. |
| Supreme Overlord | Defer — fainted-party count missing | Its modifier reads `GetSupremeOverlordModifier` (`src/battle_util.c:6746-6748`), sourced from the active side's faint counter. |
| Battery, Power Spot, partner Steely Spirit | Defer — partner/Doubles state | The partner modifier reads the alive battle partner's effective ability (`src/battle_util.c:6762-6785`); production Doubles/partner topology remains refused. |
| Dark Aura, Fairy Aura, Aura Break | Defer — field-wide ability interaction | `IsAbilityOnField` combines multiple active battlers and the Aura Break override (`src/battle_util.c:6753-6760`); those field-wide identities are not authoritative inputs. |
| Normalize, Refrigerate, Pixilate, Aerilate, Galvanize, Liquid Voice | Implemented in the #91 move-type slice (§17) | The pinned dynamic type, later field rewrite, TYPE_BASED category and explicit `ateBoost` now share `HnsMoveAuthority`; the ordinary move gate remains in force. |
| Solar Power, Defeatist, and later stat-stage abilities | Defer — later targeted batch | Their live operands and source-ordered composition need their own request rules and oracle coverage. |

The pinned ordinary move table has no 61-power move. The threshold boundary is therefore covered at
60 and the next pinned ordinary power above it in the differential corpus; a low-level QuickJS
arithmetic test separately exercises the exact 61 boundary. That override exists only in the
engine-level test input; production power remains rebound from pinned move ID and data.

An exact modifier is a **modelled damage-time modifier**. A source-backed context that cannot
activate one is **request-local proven irrelevant**. A known relevant effect may only be
neutralized under the separate **caveated estimate** policy when its operands are complete.
Missing or conflicting evidence is a **hard refusal**; it never silently becomes an inactive
condition. Punk Rock is a breakable defender ability, so an attacking Mold Breaker-family ability
keeps a sound hit refused while that suppression interaction is not modelled. Existing Group C
move-bypass and Ability Shield interpretation is unchanged.

## Pinned ability audit (Random Abilities)

The exact `Release-v2.0.5` source at commit
`1f42b74dff0e9fe942419845d040663dd829a973` defines **311 ability IDs (0–310)**.
The reviewed inventory is
[`tools/hns-abilities/ability_inventory.tsv`](../tools/hns-abilities/ability_inventory.tsv):
each row records the numeric ID, enum symbol, pinned display name, category before this
audit, proposed category, source references, pinned description, and reviewer rationale.
`decisions.json` is the explicit manual decision input. The generator validates the enum
and `gAbilitiesInfo` with the pinned source, writes the inventory and Kotlin registry,
and its `--check` mode runs in `./ci.sh source-check`. Missing/extra IDs, changed
names, duplicate IDs or symbols, and a decision for an absent ID fail the check.
Source references are an index for review, never automatic proof of neutrality. The separate
[`tools/hns-abilities/context_rules.json`](../tools/hns-abilities/context_rules.json) tracks reviewed
request-local proofs. [`HNS_2_0_5_LIVE_STATE_AUTHORITY_MATRIX.md`](HNS_2_0_5_LIVE_STATE_AUTHORITY_MATRIX.md)
is the shared provenance reference for the operands those rules can consume. `./ci.sh source-check`
validates each referenced source line against the same pinned checkout.

| Category | IDs |
|---|---:|
| `PROVEN_NO_DAMAGE_EFFECT` | 84 |
| `MODELLED_EQUIVALENT` | 0 |
| `MODELLED_HNS_SPECIFIC` | 0 |
| `MODELLED_HNS_CONDITIONAL` | 58 |
| `UNSUPPORTED_DAMAGE_RELEVANT` | 169 |
| `UNCLASSIFIED` | 0 |

These are the current generated audit totals for all 311 pinned abilities. The 58 conditionally
modelled abilities include the Group C immunity subset, the four pinch abilities, Hustle/Guts, the
nine #105 base-power abilities, defender Heatproof, both direct-hit Water Bubble branches, the six
#106 move-type abilities, Punk Rock, holder-side Steely Spirit, Adaptability, and the final-damage
batch Tinted Lens, Sniper, Neuroforce, Filter, Solid Rock, Prism Armor, Multiscale, Shadow Shield,
and Ice Scales, plus Transistor, Dragon's Maw, Rocky Payload, and Orichalcum Pulse. Conditional classification does not grant
blanket support: the request-local predicates and their required evidence still gate each use.
Steely Spirit's attacker-partner branch is not modeled.

The audit follows the ordinary `EFFECT_HIT` dependency path through attack and defense
stats, base power, final modifiers, STAB, type effectiveness, effective battler and
move types, weather, field and side states, critical hits, status, HP thresholds,
held items, partner effects, grounding, and effective ability replacement. Principal
pinned paths include `src/battle_util.c` (`CalcAttackStat`, `CalcDefenseStat`,
`CalcMoveBasePowerAfterModifiers`, `CalcFinalDmg`, and `GetBattleMoveType`),
`src/battle_main.c`, `src/battle_move_resolution.c`, `src/battle_script_commands.c`,
`src/battle_end_turn.c`, and `src/data/abilities.h`. A source description or a
missing symbol in one function is insufficient proof. The current live-operand and
ordinary-move gates remain in force: a neutral ability does not authorize an
unsupported move, item, field, Doubles state, or random type setting.

New globally neutral examples include Static (post-hit contact status), Compound Eyes and Sand Veil
(accuracy/evasion), Inner Focus (flinch prevention), Hyper Cutter and Full Metal Body (stat-drop
prevention with live stages), Run Away (escape), Pickup and Ball Fetch (overworld/after-battle), and
Prankster (status-move priority). Group C adds request-local immunity support for absorbers,
move flags, priority blockers, Wonder Guard, Levitate, and the relevant grounding items; see
[`HNS_GROUP_C_IMMUNITIES.md`](HNS_GROUP_C_IMMUNITIES.md). Group A changes only Pickpocket's global category from
`UNCLASSIFIED` to `UNSUPPORTED_DAMAGE_RELEVANT`; Group C later promoted the specifically audited
immunity abilities listed in its audit, and Group D later added only abilities with reviewed
request-local rules. A separate
`HnsAbilityContextPolicy` now makes a three-state decision (`PROVEN_IRRELEVANT`, `RELEVANT`, or
`UNKNOWN`) for reviewed globally unsupported IDs. Only a source-backed proof using operands rebound by `CalcRequestBoundary` removes that one
ability's blocker; unknown context still refuses, and all independent calculation limitations still
apply.

Examples: attacker-side Tera Shell and defender-side Truant are irrelevant to ordinary outgoing
damage; defender Tera Shell clears only when live species proves it is not Terapagos-Terastal or
live HP proves the Terastal form is below full HP. Full-HP Terapagos-Terastal remains refused.
Telepathy, Friend Guard, Plus, and Minus clear only when exact live battler topology proves Singles.
Levitate (26) now uses the Group C Ground-immunity calculation; Guts and Hustle are modelled only
at their exact Attack-stat predicates, while Huge/Pure Power and Thick Fat clear only under their
source-checked side and move predicates. Adaptability now calculates its pinned STAB branch when
the final effective type and live attacker types are authoritative; unsupported gimmicks and
missing type authority remain blocked. Group D also models Tinted Lens, Sniper, Neuroforce, Filter,
Solid Rock, Prism Armor, Multiscale, Shadow Shield, and Ice Scales in their pinned final-ability
slots. Truant on the
attacker remains blocked because `truantCounter` is not observed. Defender Battle Armor and Shell
Armor clear for a fixed noncritical ordinary hit; a critical request remains relevant because it
conflicts with their prevention effect. The source-checkable artifact records both clearance rules
and deliberately blocked contexts. The four conditional pinch abilities retain their existing
separate condition gate. Group D adds the #105 base-power predicates for Technician, Iron Fist,
Strong Jaw, Mega Launcher, Sharpness, attacker Water Bubble, defender Water Bubble, defender
Heatproof, Steelworker, Toxic Boost, and Flare Boost; #106 adds Normalize, Refrigerate, Pixilate,
Aerilate, Galvanize, and Liquid Voice through the shared move-type authority. Exact predicates and
fail-closed cases are summarized in the opening
slice tables and detailed in §§6.3–6.4, 11.2, and 17.

In Random Abilities battles, the boundary takes the effective numeric ID from
`gBattleMons[battler].ability` (or observed suppression), checks the matched live
slot and identity, and never grants capability from the species default. It also reads the
current `BattlePokemon.species` word for form-dependent predicates such as Tera Shell; this is
request evidence, not a species-default ability lookup. The Battle tab keeps every remaining
ability blocker as structured data and shows multiple blockers on separate lines (for example,
`You: Huge Power` / `Foe: Tera Shell`); a single blocker keeps the compact one-line label. An
unresolved ID says “not yet audited”. A known relevant unsupported ability is neutralized and shown
as an estimate caveat when the request evidence is complete; unknown or unclassified ability states
remain hard refusals and never reach the calculator.

This document is the **authority** for what DualDex's damage calculator may honestly claim about
Pokemon Heart & Soul (H&S) 2.0.5, and for how the two verified vanilla targets differ from it.

## H&S displayed-range output contract (#87)

The H&S Battle damage display represents **one ordinary hit's 16 integer damage rolls**, its
minimum and maximum, and percentages of the defender's observed maximum HP. The request's
already-selected `move.isCrit` value is the sole critical-hit operand: the range is conditional on
that value. It contains no critical-hit probability, accuracy probability, KO probability,
turn-selection probability, multi-turn simulation, end-of-turn result, after-hit/contact aftermath,
or future stat/status change. `calculateHnsDamage` emits `koChanceText: ""` in both its ordinary and
zero-damage branches. The generic vanilla calculator's KO path is separate.

The 16 rolls are **raw damage before HP-based survival clamps**. Whether a user-facing range should
account for Sturdy, Focus Sash, or Focus Band remains an unresolved product decision; #87 keeps
their applicable contexts blocked or caveated pending maintainer confirmation. This output contract
does not authorize unsupported move shapes, unknown live state, or a mechanic that changes the
current hit's damage formula or forces that hit to be critical.

`native/tests/test_js_calc.c` executes the shipped H&S bundle for noncritical, critical, and
zero-damage requests. Its Group A regression asserts the exact response field set, an empty
`koChanceText`, and 16 rolls selected by `isCrit`; a new probability output or other field makes
CI fail until these proofs are revisited. The contextual ability/item tests assert each Group A
clearance beside a nearby relevant or unknown case. `./ci.sh source-check` checks every cited
H&S source line in both contextual rule files against the pinned checkout.

The Group A ability rules cover Super Luck's critical stage; Battle Armor and Shell Armor for a
fixed noncritical hit; Sniper only when its critical damage multiplier cannot apply; post-hit
Rough Skin, Iron Barbs, Aftermath, Innards Out, Liquid Ooze, Mummy, Lingering Aroma, Wandering
Spirit, Cotton Down, Gooey-like Tangling Hair, and Pickpocket; berry recovery from Harvest, Cheek
Pouch, and Cud Chew; and speed or priority effects from Swift Swim, Chlorophyll, Sand Rush, Slush
Rush, Quick Feet, Unburden, and Quick Draw when Analytic cannot depend on their turn order.
`Pickpocket` moves from `UNCLASSIFIED` to globally unsupported with a request-local after-hit
proof. Group B (#88) now classifies Speed Boost, Steadfast, and Stamina as globally unsupported
with request-local live-stage rules; their identity blockers clear only when the authoritative
current stage arrays are present. Merciless remains uncleared because it can force the current hit critical. Ripen clears
attacker contexts and defender contexts without a resist berry; its defender resist-berry path
remains relevant. Ability Shield is irrelevant only when observed Singles state rules out Gastro
Acid and every suppression source: Neutralizing Gas, a relevant Mold Breaker-family attack, and a
move carrying the pinned `ignoresTargetAbility` flag. Group C models the Shield preserving the
defender ability against Mold Breaker-family and literal move-flag suppression; Gastro Acid remains
unsupported. Ground-relevant Iron Ball remains relevant. New damage-time modifiers and immunities
remain with #89–#93.

It is derived from the pinned upstream source, not from behaviour observed in the app:

| Item | Value |
|---|---|
| Repository | `PokemonHnS-Development/pokehns-expansion` |
| Tag | `Release-v2.0.5` |
| Commit | `1f42b74dff0e9fe942419845d040663dd829a973` |
| Local checkout used | `/home/dq/Projects/upstream-hns/pokehns-expansion` (outside this repository) |
| Engine | pokeemerald-expansion `1.15.2` |
| Embedded calculator | `@smogon/calc` 0.11.0 via `app/src/main/assets/calc_bundle.js` |
| Bridge source | `tools/calc-bundler/entry.js` |

No upstream source is vendored into DualDex. No ROM bytes are committed, copied, or reproduced.
Line references below are `file:line` into that pinned checkout.

> **Gap C4e status (current).** The exact official H&S 2.0.5 ROM SHA-256 is now promoted into
> `heart_and_soul.json`, and a **narrow ordinary Singles subset reaches `Ready` / `ESTIMATED`**
> through the real `CalcRequestBoundary`. Everything outside that subset still fails closed. See
> §14 for the authorized subset, the new live readers and the exact hash decision; §13 is the
> historical C4d record, which deliberately did not promote.

> **Current status note:** Gap sections below preserve point-in-time history. Statements that no
> production request reaches `Ready` / `ESTIMATED` describe pre-C4e states and are superseded by
> this C4e status and §14.9.

> **Current #86 result policy.** Every request ends as one of three outcomes: **Fully modelled**
> (calculation and active damage modifiers are represented), **Caveated estimate** (a trustworthy
> base request runs after one or more known, named mechanics are neutralized), or **Refused** (no
> trustworthy base calculation exists). An estimate does not mean DualDex guessed a missing modifier;
> it means the engine calculated the known request after deliberately removing each named mechanic
> whose effect is not modelled. Known relevant abilities, items, and supported field modifiers may
> be ignored this way. Unknown or unread identity/state remains a hard refusal, and any independent
> hard limitation wins. See `CalcLimitation.disposition` and §14.9 / §15.5.

**Vocabulary.** *SOURCE VERIFIED* means read directly from the pinned source (or from the vendored
library source). *NOT FOUND* means the evidence does not exist and is never treated as true.

---

## 1. The three answers

DualDex supports **exact verified FireRed**, **exact verified Emerald**, and **exact H&S 2.0.5**.
Vanilla FireRed/Emerald requests that stay inside the verified input set are presented as **Verified**
using `@smogon/calc`'s ADV pipeline (`gen: 3`). That input set excludes two Doubles shapes the
cartridge conditions on how many opposing battlers are actually present, which this pipeline neither
reproduces nor can be told: a battle with Reflect or Light Screen active (the cartridge applies
`2 * (damage / 3)` on the pre-roll value only while both defenders are present), refused as
`CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED`, and a move the pipeline reduces as a spread
move (the cartridge halves it only while both opponents are present, so a lone remaining foe is not
reduced at all), refused as `CalcLimitation.VANILLA_DOUBLES_SPREAD_NOT_MODELLED`.
The exact supported SHA-256s, the independent
Generation III oracle, the shared golden fixture matrix, the production-boundary evidence, the
read-only memory layout audit and the corrected vanilla profile hashes are recorded in
[VANILLA_CALCULATOR_EVIDENCE.md](VANILLA_CALCULATOR_EVIDENCE.md). H&S 2.0.5 calculator support is a **partial, fail-closed
slice (Gap C4b/PARTIAL, Gap C4c/OPEN)**: the UQ4.12 roll-first damage arithmetic is implemented in QuickJS
(`calculateHnsDamage`, §11) and host-verified against the native C oracle. After Gap C4e the supported
ordinary Singles subset receives current effective types, raw battle stat words, stat stages,
transient volatiles, gimmick state, and the attacker's live HP/status from exact-trusted runtime
state. Dynamic move retypes remain refused when active, and a Doubles request still needs the
runtime `GetMoveTargetCount` value. The supported subset is published as **Estimated** (§14). Issue
#86 adds a second bounded path: when the base
request is still trustworthy, a known, relevant but unmodelled ability, item, or supported field
modifier is removed from the authorized execution request, and its identity is shown with the
estimate. Unknown ability/item identity, unread state, unsupported move mechanics, Doubles, active
dynamic-type retypes, Glaive Rush, active gimmicks, non-neutral live status, active Random Types /
Random Type Effectiveness / Random Moves, and other hard limitations remain **refused**. H&S lets the player change rules (category split, Fairy type, randomizers), applies modern
base data and type matchups, and executes a distinct UQ4.12 pipeline with Gen III badge boosts; DualDex
consumes challenge settings at runtime via `CalcRequestBoundary` (§4.1), executes the exact 19x19 H&S
type chart (Gap C1, §3.1), and audits authoritative abilities (Gap C2, §6) and held items (Gap C3, §7).
Requests with no trustworthy base calculation (unmodelled moves, unknown ability/item identity,
unread live state, out-of-range stat stages, unmodelled weather, unsupported active randomizers,
unspecified badge applicability, or unavailable Doubles arithmetic) remain strictly **refused** with
a stated reason. Every other build — CFRU hacks, split-mechanics vanilla builds, any
unidentified ROM — is **refused** rather than given a Gen III number.

The single decision point is `CalcCapabilityPolicy`
(`app/src/main/java/com/dualdex/calculator/CalcCapabilityPolicy.kt`); the only way to turn
application state into a request is `CalcRequestBoundary`
(`app/src/main/java/com/dualdex/calculator/CalcRequestBoundary.kt`).

---

## 2. Capability matrix by mechanic

This is the inventory issue #9 asks for. "Bridge can express" asks whether **any** value of an
existing request field reproduces this build's rule, not whether the current UI happens to set it.

| # | Mechanic / state | H&S 2.0.5 (pinned) | Bridge can express | Verdict |
|---|---|---|---|---|
| 1 | Damage formula | Generation III arithmetic with modern data; UQ4.12 roll-first order | **yes** — dedicated `calculateHnsDamage` pipeline in `entry.js` implements exact UQ4.12 arithmetic, stat stages, badge boosts, pre-roll modifiers, roll step, and post-roll modifiers | **ARITHMETIC MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — full parity across all 16 damage rolls with native C oracle (§11), direct C4d official-ROM goldens A/B/C bind the arithmetic, and the exact-trusted live Singles ordinary subset defined in §14 is exposed as `Ready` / `CalcSupport.ESTIMATED`; all other requests remain fail-closed. (The `GAP C4b PARTIAL / OPEN` verdict and "live-operand classes remain unobserved" were the historical pre-C4e state.) |
| 2 | Move category | Per-move by default (`B_PHYSICAL_SPECIAL_SPLIT GEN_LATEST`) `[include/config/battle.h:76]`, decided by `GetBattleMoveCategory` `[src/battle_util.c:9173]` | **yes** — bridge expresses both behaviors via `move.overrides.category`; PER_MOVE_SPLIT uses the pinned per-move category, while TYPE_BASED materializes `gTypesInfo[effectiveType].damageCategory` (Ghost Special, Dark Physical). Status moves retain Status in both; `optionStyle` is consumed by `CalcRequestBoundary` | **MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — `optionStyle` selects category behavior with Status prioritized; the supported ordinary subset executes in `calculateHnsDamage` and is exposed as `Ready` / `ESTIMATED` for the exact-trusted live Singles ordinary subset defined in §14; all other requests remain fail-closed. (The `GAP A/B/C4b` "production refused" verdict was the historical pre-C4e state.) |
| 3 | Type chart | Modern chart: Fairy present, Steel does **not** resist Ghost/Dark `[src/data/types_info.h:8]`, `:25`, `:35`, `:36` | **yes** — custom H&S type chart matrix (`hns_type_chart.json`) executed via request-local facade when `typeSystem: "hns_2_0_5"` without mutating global library state. Fairy toggle ON/OFF handled via `sPreFairyTypes` and `sFairyMoveAltTypes`. | **MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — type chart is exact and verified in QuickJS. Used by `calculateHnsDamage` for post-roll type effectiveness; the §14 subset is exposed as `Ready` / `ESTIMATED`, while all other requests remain fail-closed. (The `GAP C1/C4b` "production refused" verdict was the historical pre-C4e state.) |
| 4 | Species base stats / typings | Modern (`P_UPDATED_STATS`/`P_UPDATED_TYPES GEN_LATEST`) `[include/config/pokemon.h:5]`, from the pinned data pack | **yes** — authoritative overrides forwarded via `CalcDataOverrides` and consumed by `calculateHnsDamage` / `@smogon/calc` constructor (§3.3, §9) | **MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — overrides are extracted and forwarded, computing exact base stats or overridden by live `rawStats`; the §14 subset is exposed as `Ready` / `ESTIMATED`, while all other requests remain fail-closed. (The `GAP B/C4b` "production refused" verdict was the historical pre-C4e state.) |
| 5 | Move properties (power/type/category) | Explicit per move, 848 numbered moves incl. Gen IX `[src/data/moves_info.h:121]`, `[include/constants/moves.h:905]` | **yes** — authoritative power, type, and category forwarded via `CalcDataOverrides` and consumed by bridge (§3.3, §9) | **MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — overrides are extracted and forwarded, executing with ordinary move effects in `calculateHnsDamage`; the §14 subset is exposed as `Ready` / `ESTIMATED`, while all other requests remain fail-closed. (The `GAP B/C4b` "production refused" verdict was the historical pre-C4e state.) |
| 6 | Abilities that affect damage | Full modern roster, ~80 post-Gen-III modifiers `[src/battle_util.c:6655]`, `:6989`, `:7562` | **partially** — authoritative effective abilities consumed from `gBattleMons`; global capability in `HnsAbilityRegistry`, with separate request-local source-backed relevance in `HnsAbilityContextPolicy` and Groups C–D. Engine default ability substitution is prevented. | **CONDITIONALLY MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e / Groups C–D)** — current audit: 84 `PROVEN_NO_DAMAGE_EFFECT`, 65 `MODELLED_HNS_CONDITIONAL`, 162 `UNSUPPORTED_DAMAGE_RELEVANT`, and 0 unclassified. Conditional support now includes Group C immunities, pinch abilities, Hustle/Guts, #105/#106 groups, Punk Rock, holder-side Steely Spirit, Adaptability, the final-modifier batch, Solar Power, Defeatist, Fur Coat, Transistor, Dragon's Maw, Rocky Payload, Orichalcum Pulse, Marvel Scale, holder-side Singles Flower Gift, Sheer Force, Tough Claws, and Fluffy; each uses its pinned request predicate. Reckless, Sand Force, Battery, and Power Spot gain request-local false-predicate clearance without broadening recoil, Sandstorm, or partner support. Water Bubble burn prevention/status clearing and Steely Spirit's attacker-partner branch remain deferred. A known `RELEVANT` unsupported ability may be neutralized and named in a caveated estimate; `UNKNOWN`, unclassified, unread, and unauthoritative identity remain hard. Any independent hard limitation still refuses. |
| 7 | Held items that affect damage | Pinned item IDs, hold effects, parameters, and item types; Group D direct-hit families use exact H&S pipeline stages | **yes, for the explicit item contract** — current numeric item identity is separate from the effective hold effect; one source-backed authority covers Magic Room, Embargo, Klutz and required ability suppression | **CONDITIONALLY MODELLED (#92)** — current audit: 585 `PROVEN_NO_ORDINARY_DAMAGE_EFFECT`, 108 `MODELLED_HNS_SPECIFIC`, 207 `UNSUPPORTED_DAMAGE_RELEVANT`, and 1 `UNCLASSIFIED`. The 27 modeled families use generated item/species operands and exact H&S fixed-point stages. Known relevant unsupported items can still become named caveats only for item-independent moves; unread, unknown and unclassified state remains hard. Item-dependent moves remain blocked with `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` (§7; [Group D held-item contract](HNS_GROUP_D_HELD_ITEMS.md)). |
| 8 | Critical hits | Odds are Gen 7+ (1/24 base) `[src/battle_util.c:7975]`; **multiplier ×2** (`B_CRIT_MULTIPLIER GEN_3`) `[include/config/battle.h:6]`, `[src/battle_util.c:7474]` | multiplier yes, odds no | **MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — multiplier ×2 evaluated at pre-roll step via UQ4.12 `halfDown(8192, dmg)` in `calculateHnsDamage`, with stat stage drop-ignore rules modelled. Pinned in `test_js_calc.c`. Crit odds are not modelled. The §14 subset is exposed as `Ready` / `ESTIMATED`; all other requests remain fail-closed. (The `GAP C4b` "production refused" verdict was the historical pre-C4e state.) |
| 9 | Weather | Rain/Sun ×1.5 and ×0.5 `[src/battle_util.c:7443]`; Sand/Hail give no move-damage multiplier; Sand gives Rock SpD ×1.5 `[src/battle_util.c:7386]` | yes — live battle weather is boundary-owned via the `gBattleWeather` reader | **MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — Rain/Sun ×1.5 and ×0.5 evaluated at pre-roll step via UQ4.12 `halfDown(6144/2048, dmg)` in `calculateHnsDamage`. For the §14 subset `CalcRequestBoundary` rebinds `field.weather` from the observed battle-global `gBattleWeather` word (ordinary Rain/Sun bits only); an unread word refuses with `HNS_LIVE_WEATHER_UNKNOWN` and an unmodelled word (Sand/Hail/Snow/Fog/Strong Winds, and the primal Rain/Sun bits) refuses with `HNS_LIVE_WEATHER_NOT_MODELLED`, so clear can never be assumed. All other requests remain fail-closed. (The `GAP C4b` "production refused" verdict was the historical pre-C4e state.) |
| 10 | Snow | Ice Defense ×1.5 `[src/battle_util.c:7389]`; Snow never chips, Hail chips 1/16 `[src/battle_end_turn.c:155]` | **no** | **REFUSED** when asked for — fails closed |
| 11 | Terrain | Grassy/Electric/Psychic attacker-type ×1.3 and Misty Dragon-vs-defender ×0.5 when `IsBattlerTerrainAffected`; `B_TERRAIN_TYPE_BOOST GEN_LATEST` `[src/battle_util.c:6639-6645]`, `[include/config/battle.h:325]` | yes — raw field word plus boundary-derived per-battler terrain applicability | **CONDITIONALLY MODELLED** for exact ordinary selected hits; independent priority, ability, move and live-state limitations remain gated (§15.5) |
| 12 | Reflect / Light Screen | ×0.5 singles, ×0.667 doubles `[src/battle_util.c:7544]` | yes — the defender-side status word is boundary-owned via the `gSideStatuses[side]` reader | **MODELLED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — singles (2048) / doubles (2732) evaluated post-roll via UQ4.12 `halfDown` in `calculateHnsDamage`. Pinned in `test_js_calc.c`. For the §14 subset `CalcRequestBoundary` rebinds `field.defenderSide` from the observed defender-side `gSideStatuses[side]` word; an unread word refuses with `HNS_LIVE_SCREENS_UNKNOWN` and an unmodelled bit refuses with `HNS_LIVE_SIDE_STATUS_NOT_MODELLED`, so screenless can never be assumed. All other requests remain fail-closed. (The `GAP C4b` "production refused" verdict was the historical pre-C4e state.) |
| 13 | Multi-target reduction | Generation III value: ×0.5 when `GetMoveTargetCount(ctx) == 2` (`B_MULTIPLE_TARGETS_DMG GEN_3`) `[include/config/battle.h:47]`, `[src/battle_util.c:7403]` — the runtime count, not the static move class | **only with an authoritative runtime target count** (no reader supplies it yet) | **BLOCKED / GAP C4b PARTIAL / OPEN** — `calculateHnsDamage` applies `halfDown(2048, dmg)` only for an explicit `field.targetCount == 2`; a Doubles request without an observed count is refused with `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED` rather than halving every spread move |
| 14 | Badge boost | Active: player-side ×1.1 Atk/SpA/Def/SpD/Speed (`B_BADGE_BOOST GEN_3`) `[include/config/battle.h:253]`, eligibility-gated `[src/battle_util.c:9143]` — player battler only (`IsOnPlayerSide`); enemy never boosted | **only in an active battle with authoritative SaveBlock1 badge state** | **CONDITIONALLY MODELLED / GAP C4b PARTIAL / OPEN** — player-side badge boost flags are read from SaveBlock1 bytes `0x1A98`+`0x1A99` and evaluated via UQ4.12 `halfDown(4506, stat)` in QuickJS. A manual/out-of-battle request has no authoritative applicability, so missing badge state is **not** read as "badges off": it fails closed with `BADGE_BOOST_NOT_MODELLED` (§11) |
| 15 | Move-specific mechanics (multi-hit, weight, fixed damage, Hidden Power, Return) | Modern | **gated by effect ID** | **CONDITIONALLY GATED (Gap C4a)** — `HnsMoveMechanicsRegistry` allows only the source-proven ordinary `EFFECT_HIT` subset; every other effect fails closed with `HNS_MOVE_MECHANICS_NOT_MODELLED` (§10.3) |
| 16 | Challenge settings that change stats | No EVs `[include/global.h:309]`, Base Stat Equalizer `[:304]`, trainer IV/EV scaling `[:312]`, Max Party IVs `[:314]`, Mirror `[:307]` | partly | **CLASSIFIED (Gap C4a)** — value-changing fields that only alter stored values are captured downstream; Base Stat Equalizer and Random Moves block precisely (§10.1) |
| 17 | Challenge settings that change the rule | `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type`, `tx_Random_TypeEffectiveness` | **yes** — `CalcRequestBoundary` consumes exact-trusted runtime snapshot into `CalcHnsRuntimeRules`; exact type chart and Fairy toggle modelled (Gap C1); active randomizers block | **CONSUMED / HOST VERIFIED; CONDITIONALLY PRODUCTION AUTHORIZED (Gap C4e)** — runtime rules are known and unreadable blockers cleared when observed; active unsupported rules block fail-closed; the supported ordinary subset executes in `calculateHnsDamage` (§4.1, §11) and is exposed as `Ready` / `ESTIMATED` for the exact-trusted live Singles subset defined in §14; all other requests remain fail-closed. (The `GAP A/C1/C4b` "production refused" verdict was the historical pre-C4e state.) |
| 18 | Legendary ability overrides | `tx_Mode_Legendary_Abilities` default **ON**, substitutes abilities for slot 0 `[src/pokemon.c:5551]`, `[src/new_game.c:147]` | no | folds into row 6 |

---

## 3. Which mechanics are individually demonstrated, and which are not

An earlier revision of this document claimed the hack's type chart "agrees with Generation III's
chart on every Generation III type pair", and concluded from that plus the critical-hit and
spread-damage constants that the ADV pipeline was "the right arithmetic" for H&S. **That claim was
wrong and is withdrawn.** The correction matters because the matrix below is the foundation any
future H&S support will be built on.

### 3.1 The type chart and Gap C1 resolution

| Matchup | Pinned H&S | `@smogon/calc` 0.11.0 at `gen: 3` | H&S Type System (`typeSystem: "hns_2_0_5"`) |
|---|---|---|---|
| Ghost → Steel | **×1** | ×0.5 | **×1** |
| Dark → Steel | **×1** | ×0.5 | **×1** |
| Fairy → Dragon/Fighting/Dark | **×2** | N/A (type absent) | **×2** |
| Dragon → Fairy | **×0 (immune)** | N/A (type absent) | **×0 (immune)** |

H&S sets `B_UPDATED_TYPE_MATCHUPS` to `GEN_9` `[include/config/battle.h:53]`, so its `STL_RS` macro
resolves to ×1.0 `[src/data/types_info.h:8]`, used by the Ghost and Dark rows
`[src/data/types_info.h:25]`, `:35`. The library's Gen 3 chart assigns ×0.5 to both.

**Gap C1 Resolution:**
DualDex extracts the exact 19x19 type effectiveness matrix deterministically from `src/data/types_info.h`
(`tools/hns-type-system/generate_hns_type_system.py`) into `tools/calc-bundler/hns_type_chart.json`.
When `typeSystem: "hns_2_0_5"` is specified on the request, `tools/calc-bundler/entry.js` constructs a
request-local duck-typed Generation facade inheriting Gen 3 arithmetic (`num = 3`) but supplying the exact
H&S type provider (`gen.types.get(...)`). Global library state is never mutated.

Additionally, runtime Fairy toggle behavior (`tx_Mode_Fairy_Types`) is modelled:
- When Fairy is ON (`fairyTypesEnabled == true`), species and moves retain their authoritative H&S Fairy typings.
- When Fairy is OFF (`fairyTypesEnabled == false`), species retype according to upstream `sPreFairyTypes` (20 species)
  and moves retype according to `sFairyMoveAltTypes` (34 moves) via `HnsFairyTypeMappings.kt`.
- Coupling with `optionStyle`: under `TYPE_BASED` (`optionStyle == 1`), status moves retain `Status` (Status wins before
  `optionStyle` in H&S `GetBattleMoveCategory`), while non-status move category comes from pinned
  `gTypesInfo[effectiveType]` (e.g. Moonblast -> Dark -> Physical; Dazzling Gleam -> Normal -> Physical).
- Host QuickJS tests in `native/tests/test_js_calc.c:check_gap_c1_type_system` verify Ghost/Dark -> Steel neutrality,
  Fairy offensive 2x, Dragon -> Fairy 0x immunity, Charm status category preservation, and request isolation.

### 3.2 What is demonstrated, and what is not

Only these individual behaviours are source-and-test demonstrated:

> **Gap C4b status: PARTIAL / OPEN.** With the implementation of `calculateHnsDamage` in
> `tools/calc-bundler/entry.js` (§11), the H&S UQ4.12 roll-first calculation order is implemented and
> proven against the native C oracle. That is **SOURCE + HOST VERIFIED arithmetic, not ROM-result
> validation**. No official H&S 2.0.5 battle result has been compared against the new output. Raw
> battle stat words and stat stages have exact-trusted runtime readers. The relevant dynamic move-type
> causes and selected transient states are also observed, but active retypes and damage modifiers
> remain unsupported; the Doubles runtime target count still has no reader. As a result,
> production requests are fail-closed: a manual /
> out-of-battle request is refused because badge applicability is unspecified, and a Doubles request
> is refused because the runtime target count is unavailable. C4b stays **PARTIAL / OPEN** until
> reproducible official-ROM result validation and the remaining live operands are complete.

| Behaviour | H&S (pinned source) | Calculator (`calculateHnsDamage` + `typeSystem: "hns_2_0_5"`) | Status |
|---|---|---|---|
| Critical-hit multiplier | ×2 (`B_CRIT_MULTIPLIER GEN_3`) `[include/config/battle.h:6]`, applied `[src/battle_util.c:7477]` | ×2 pre-roll UQ4.12 `halfDown(8192, dmg)`, crit drop-ignore rules | **HOST-ORACLE MATCHES (Gap C4b partial / open)** |
| Two-target reduction | ×0.5 only when `GetMoveTargetCount(ctx) == 2` (`B_MULTIPLE_TARGETS_DMG GEN_3`) `[include/config/battle.h:47]` | ×0.5 pre-roll UQ4.12 `halfDown(2048, dmg)` only with an explicit `field.targetCount == 2`; missing count refuses | **HOST-ORACLE MATCHES, RUNTIME COUNT UNAVAILABLE (Gap C4b partial / open)** |
| Thick Fat placement | halves the attack stat `[src/battle_util.c:7121]`, `:7191` | halves the attack/spAttack stat in `calculateHnsDamage` | **HOST-ORACLE MATCHES (Gap C4b partial / open)** |
| Type chart | modern (Fairy present; Steel does not resist Ghost/Dark) | modern 19x19 H&S matrix via request-local facade | **MATCHES (Gap C1 closed)** |
| Move category rule | per-move default, switchable to type-based via `optionStyle`; TYPE_BASED uses H&S `gTypesInfo` for the final effective type (Ghost Special, Dark Physical) | `move.overrides.category` is explicitly materialized by `CalcDataOverrides` and consumed by `entry.js` | **MATCHES (Gap A/B closed)** |
| Abilities (supported subset) | 84 `PROVEN_NO_DAMAGE_EFFECT` and 65 `MODELLED_HNS_CONDITIONAL` abilities | Conditional support also covers Marvel Scale, holder-side Singles Flower Gift, Sheer Force, Tough Claws, and Fluffy. Reckless, Sand Force, Battery, and Power Spot have request-local relevance rules; unsupported recoil/weather/partner contexts remain independently gated. Water Bubble burn prevention/status clearing and Steely Spirit partner support remain deferred. | **CONDITIONALLY AUTHORIZED** under the live operand gates and pinned inventory |
| Abilities (globally unsupported) | Thick Fat, Huge Power, Pure Power, modern modifiers | effects not generally modelled | **CONTEXTUAL** — known relevant unsupported abilities are named caveats after neutralization; unknown/unread/unclassified abilities remain hard, and proven-irrelevant contexts do not become caveats (§6.3, #86) |
| Held items (#92, building on Gap C3) | exact H&S item ID, raw item record, and effective hold effect; scoped type, stat, and final modifiers | shared hold-effect authority; generated species rules; hit-time Embargo, Metronome counter, and transformed species; exact source oracle | **CONDITIONALLY MODELLED** — 27 H&S-specific families are exact on the admitted ordinary selected-hit surface. Unsupported or unknown item contexts remain caveated or refused by their independent rule; item-dependent moves remain blocked (§7, #92) |
| Badge boost | player-side ×1.1 stats | SaveBlock1 reader (bytes `0x1A98`+`0x1A99`), UQ4.12 `halfDown(4506, stat)` in QuickJS; manual/unspecified applicability fails closed | **HOST-ORACLE MATCHES, MANUAL STATE UNAVAILABLE (Gap C4b partial / open)** — see §11 |

### 3.3 Three different claims that must not be conflated

The matrix in §2 uses these distinctions, and any future H&S work must keep them separate:

1. **Identity exists in pinned H&S data.** `HeartAndSoul205DataPack` can name species 1433 and move
   847, and `CalcRequestBoundary` proves a name belongs to the build.
2. **The calculator consumes that H&S record.** The plumbing gap (Gap B) is now closed:
   `CalcDataOverrides` extracts authoritative base stats, types, base power, type, and category
   from `HeartAndSoul205DataPack`, serializes them as `overrides` on `attacker`, `defender`, and
   `move`, and `tools/calc-bundler/entry.js` strictly validates and passes them to `@smogon/calc`'s
   `Pokemon` and `Move` constructors (`options.overrides`).
3. **The calculator reproduces the H&S mechanic.** It does so where §3.2 says "MATCHES".

**Historical C4b snapshot (superseded by C4e):** Consuming the record (2) did not imply reproducing
the mechanic (3). At C4b, the engine executed the H&S UQ4.12 pipeline, but the fail-closed policy meant
no production request was promoted to `CalcSupport.ESTIMATED`. The current narrow production path is
documented above and in §14.9; unsupported mechanics and unobserved live state still fail closed.


### 3.4 Vanilla Gen III verified set

A vanilla request is presented as *Verified* only when the running ROM is the exact build the profile
was verified against, every participant's damage-relevant state is **carried or explicitly recorded
as unknown** (`CalcInputPreparation`), the species and move names resolve in that build's data, and
no ability, held item, status, stat stage, level or field value outside the modelled sets appears.

Two exclusions are enforced or disclosed rather than merely documented:

* An incomplete **live read** can never be verified: a field the reader could not carry is reported,
  not shortened to neutral, and blocks the result (§4 of the review's R1 finding).
* The generation III **badge boost** is unmodelled for every build and the request shape cannot
  express it. It is not enforced in code, so the verified headline states the scope explicitly
  (`CalcResultPresentation.BADGE_BOOST_NOTE`) instead of leaving it to this document.

The exact supported vanilla SHA-256s, the independent Generation III oracle, the golden damage
matrix, the correction of the previously non-genuine vanilla profile hashes, the read-only memory
layout audit behind the two accepted FireRed revisions, the retail-image probe of the battle-state
addresses, the resulting decoupling of calculator trust from battle-state read authorization, and the
vanilla Doubles Reflect/Light Screen and spread exclusions are recorded in
[VANILLA_CALCULATOR_EVIDENCE.md](VANILLA_CALCULATOR_EVIDENCE.md).

---

## 4. Heart & Soul challenge settings

`struct ChallengeSettings` is 32 bytes at `[include/global.h:253]`, a member of `SaveBlock3`
`[include/global.h:359]`, reached as `gSaveBlock3Ptr->challengeSettings` `[include/global.h:363]`.
DualDex reads this struct at runtime — `pokemon_read_challenge_settings_gba` decodes it into a typed snapshot
(`HnsChallengeSettingsSnapshot`) that the companion publishes only while the running ROM is exactly trusted,
and the runtime verification in `HNS_2_0_5_COMPATIBILITY_EVIDENCE.md` §13 observed live values (including a
challenge-menu toggle flipping exactly `tx_Mode_Fairy_Types`).

**Current status (Gap A and Gap C1 closed):**
- **Runtime settings are observed:** whenever the ROM is exactly trusted, the snapshot is delivered to
  calculator preparation.
- **Four rule fields are now consumed:** `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type`, and
  `tx_Random_TypeEffectiveness` are mapped into `CalcHnsRuntimeRules` by `CalcRequestBoundary`.
- **`optionStyle` selects category behavior:** raw 0 (`PER_MOVE_SPLIT`) retains boundary-owned move category
  overrides. Raw 1 (`TYPE_BASED`) retains `Status` for status moves (Status wins before `optionStyle` in H&S),
  while omitting category for damaging moves to trigger type-based category derivation.
- **Randomizer activation is known:** observed raw 0 clears unreadable blockers; observed raw 1 blocks with
  explicit active-not-modelled limitations.
- **Historical pre-C4e status:** live battler abilities (observed via `gBattleMons`, PR #56),
  authoritative data overrides (PR #57), runtime rules (PR #61), the exact H&S type chart (Gap C1, PR #62),
  authoritative effective abilities with conditional capability gating (Gap C2), and exact current held-item
  identity with conditional item capability (Gap C3) are plumbed, but the generation III badge boost remains
  unmodelled, so all H&S calculations remained refused (`CalcSupport.UNSUPPORTED`) at that point. This
  snapshot is superseded by C4e and the #86 three-tier result policy above.

Two properties make challenge settings decisive rather than a caveat:

1. **Some fields change the rule, not just the values.** They dictate category derivation, typing rules, and chart lookups.
2. **Several are ON by default, and the Mode tab is free.** `TAB_MODE` has no lock entries
   `[src/challenge_menu.c:183]`, so those rows can be flipped at any time, including mid-run. The
   defaults come from the challenge menu, not from `new_game.c`: `NewGameInitData` snapshots the
   menu's choices, clears `SaveBlock3`, and restores the snapshot `[src/new_game.c:221]`, `:235`,
   `:238`, `:239`. "The randomizer is off" therefore does **not** imply "vanilla behaviour".

### 4.1 Rule-changing fields — consumption and distinction

| Field | Default | What it changes |
|---|---|---|
| `optionStyle` | 0 = per-move split `[src/new_game.c:128]` | `GetBattleMoveCategory` `[src/battle_util.c:9183]`: when 1, H&S uses its pinned `gTypesInfo` category for the effective move type. This is not the classic Generation III split: H&S assigns Ghost to Special and Dark to Physical. Bound to the "PHYS/SP SPLIT" row `[src/challenge_menu.c:486]`, `:1973`. The *same* species, move and level has two different correct answers. |
| `tx_Mode_Fairy_Types` | 1 = ON `[src/new_game.c:146]` | When 0 the Fairy type is deleted: species revert to pre-Fairy typings via `sPreFairyTypes` `[src/pokemon.c:5734]`, and Fairy moves are retyped via `sFairyMoveAltTypes` (Moonblast → Dark) `[src/pokemon.c:5788]`. Bound to "ADD FAIRY TYPE" `[src/challenge_menu.c:456]`. |
| `tx_Random_Type` | 0 `[include/global.h:283]` | `GetSpeciesType` returns a randomized type `[src/pokemon.c:5731]`, `[include/randomizer.h:91]`. |
| `tx_Random_TypeEffectiveness` | 0 `[include/global.h:284]` | `GetTypeModifier` remaps the **attacking** type inside the chart at damage time `[src/battle_util.c:8533]`. |

**Update (Gap A closed):** DualDex now consumes these fields through `CalcRequestBoundary.resolveHnsRuntimeRules`,
converting the authoritative `HnsChallengeSettingsSnapshot` from `CompanionViewModel.challengeSettings`
into an immutable `CalcHnsRuntimeRules` model.

The calculator truthfully distinguishes three states:
1. **Unreadable / Untrusted (`*_UNREADABLE`):** When the snapshot is missing, the ROM trust is not
   exact-verified, the snapshot status is not `OBSERVED`, or an individual field is unobserved or
   out-of-domain. In this state, `CATEGORY_SPLIT_TOGGLE_UNREADABLE`, `FAIRY_TOGGLE_UNREADABLE`,
   `RANDOM_TYPES_UNREADABLE`, `RANDOM_TYPE_EFFECTIVENESS_UNREADABLE`, and `CHALLENGE_SETTINGS_UNREADABLE`
   block the calculation.
2. **Observed and modelled:**
   - `optionStyle == 0` (`PER_MOVE_SPLIT`): The boundary-owned H&S move override retains its explicit
     pinned category (`category = "Physical" / "Special" / "Status"`).
   - `optionStyle == 1` (`TYPE_BASED`): Status moves retain `"Status"` (Status wins before `optionStyle` in H&S).
     For non-status moves, `CalcDataOverrides` explicitly sets category from pinned
     `gTypesInfo[effectiveType].damageCategory` after the ability and later field type rewrites; the
     engine does not derive it from the original type or a Gen III split.
   - `tx_Mode_Fairy_Types`: Observed ON (1) or OFF (0); species and moves are retyped according to H&S source mappings
     when OFF (`sPreFairyTypes`, `sFairyMoveAltTypes`), and `typeSystem: "hns_2_0_5"` selects the modern 19x19 type chart (Gap C1 closed).
     Under exact trust, observed rules, randomizers OFF, representable types, and `request.typeSystem == "hns_2_0_5"`,
     `HNS_TYPE_CHART_NOT_MODELLED` is cleared.
   - `tx_Random_Type == 0` (raw 0): Observed OFF. No random-type blocker is added.
   - `tx_Random_TypeEffectiveness == 0` (raw 0): Observed OFF. No random effectiveness blocker is added.
3. **Observed ON but unsupported:**
   - `tx_Random_Type == 1`: Observed ON. Blocks calculation with `RANDOM_TYPES_ACTIVE_NOT_MODELLED`.
   - `tx_Random_TypeEffectiveness == 1`: Observed ON. Blocks calculation with `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`.

**Historical pre-C4e summary (superseded):** every H&S calculation remained refused (`CalcSupport.UNSUPPORTED`) after Gap A, C1, C2, C3, and C4a because the generation III badge boost remained unmodelled (`BADGE_BOOST_NOT_MODELLED`), the pinned H&S damage-modifier order and staged-stat rounding were not reproduced (`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`), and mutable live battle state was not authoritatively observed (`HNS_LIVE_BATTLE_STATE_NOT_MODELLED`). Current production outcomes are described at the top of this document and in §14.9.

### 4.2 Value-changing fields

The rule fields above (`optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_*`) are read and consumed.
The value-changing challenge settings below modify participant stats, moves, or abilities directly.
**Gap C4a classified every one of them** (see §10.1): most are captured downstream because the
calculator consumes the final observed level / IV / EV / ability / party values, and only Base Stat
Equalizer and Random Moves need an independent blocker.

| Field | Default | What it changes | C4a disposition |
|---|---|---|---|
| `tx_Challenges_NoEVs` | 0 | Blocks EV gain `[src/pokemon.c:7808]`; EV items `[src/party_menu.c:4941]` | captured downstream (EVs are request values) |
| `tx_Challenges_BaseStatEqualizer` | 0 | Replaces every non-HP base stat with 100/255/500 `[src/challenge_menu.c:2359]`, `[src/pokemon.c:3750]` | **blocker** `HNS_BASE_STAT_EQUALIZER_NOT_MODELLED` when nonzero |
| `tx_Challenges_Mirror` / `_Thief` | 0 | Copies the enemy party over the player's `[src/battle_main.c:667]`, `:5823` | captured downstream (the copied party is observed) |
| `tx_Challenges_TrainerScalingIVs` / `_EVs` | 0 | Rewrites opponent IVs/EVs `[src/battle_main.c:2145]`, `:2156` | captured downstream (observed IVs/EVs) |
| `tx_Challenges_MaxPartyIVs` | 0 | Forces player IVs to 31 (or 30/31) `[src/pokemon.c:3232]` | captured downstream (observed IVs) |
| `tx_Random_Abilities` | 0 | Rerolls ability `[src/pokemon.c:5585]` | captured downstream (effective ability from `gBattleMons`) |
| `tx_Random_Moves` | 0 | Rerolls learned moves `[src/pokemon.c:3954]` | **blocker** `HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED` when ON |
| `tx_Mode_Sturdy` | 1 | Gates Gen V+ Sturdy endure-at-1-HP `[src/battle_util.c:8186]` | irrelevant (Sturdy is not in the supported ability set) |
| `tx_Mode_Legendary_Abilities` | 1 | Substitutes abilities for slot 0 `[src/pokemon.c:5551]` | captured downstream (effective ability from `gBattleMons`) |
| `tx_Challenges_LevelCap`, `tx_Challenges_ExpMultiplier` | 0 | Change which level/EV states are reachable at all `[src/caps.c:64]` | captured downstream (observed level) |

**Not damage-relevant, recorded so it is not re-investigated:** `tx_Challenges_OneTypeChallenge`
only gates which species may be obtained `[src/challenge_menu.c:2441]`, `[src/starter_choose.c:370]`.
`RANDOMIZE_BASE_STATS` is declared `[include/randomizer.h:27]` but has no case in
`RandomizerFeatureEnabled` `[src/randomizer.c:58]` and is referenced nowhere else, so base stats are
never randomized. Move power, accuracy and type are never randomized either.

---

## 5. How content is proven to belong to the build

The calculator bridge selects content **by name**
(`tools/calc-bundler/entry.js:60`, `:74`, `:78`), and the engine looks that name up in its own
bundled tables without validating it against anything. Two failure modes follow, and both are
closed by the boundary:

1. **A name the engine does not carry** throws — but with an undiagnosable message
   (`"…(reading 'hp')"` for a species). It never silently substitutes vanilla data for an unknown
   species or move name.
2. **A name the engine carries with different data resolves to the engine's data.** This is the real
   silent-wrong-number path: a hack-modified species or move is computed from vanilla values, and a
   hack-only move like `Malignant Chain` resolves against the engine's own Gen IX record.

`CalcRequestBoundary` therefore requires every species and move name to resolve in the *active
build's* data (`GameDataPack.hasSpeciesByName` / `hasMoveByName`). For a build whose pinned pack
blocks global fallback — H&S 2.0.5 — that is the pinned pack and nothing else.

`HeartAndSoul205DataPack` gained an authoritative name index for this, generated by
`tools/hns-data-pack/generate_hns_data_pack.py` from the same pinned checkout (1427 species, 934
moves; `--verify` reproduces the committed file with zero drift). The index deliberately **omits
species whose forms differ in type or base stats** — `Eevee`, `Pikachu`, `Deoxys`, `Castform`,
`Terapagos`, and 90-odd others — because a name-only request cannot say which form is meant. Those
names resolve to `null` and are listed in `HeartAndSoul205DataPack.multiFormNames`, so the boundary
refuses them instead of computing one form's damage for another form's name.

---

## 6. Abilities

The 0.11.0 ADV pipeline applies a fixed, **exact-name-match** list of abilities and calls
`gen.abilities.get()` nowhere in the damage path. An ability it does not model is silently ignored —
it does not throw, and it does not fall back. The classic demonstration is a statused attacker with
an unrecognised ability string: the number changes but the ability does not. The native suite pins
both halves of this in `gen3_explicit_guts_boosts_a_statused_attacker` and
`gen3_unmodelled_ability_is_silently_ignored`.

### 6.1 Generation III Modelled Abilities
`CalcCapabilityPolicy.GEN3_MODELLED_ABILITIES` is a whitelist of what the ADV pipeline
*actually applies*, not a list of Generation III abilities. Deliberately excluded, with reasons:

| Ability | Why it is not whitelisted |
|---|---|
| `Air Lock`, `Cloud Nine` | Recognised, but weather is only nulled for a Pokemon the engine was told is on the field (`abilityOn`), which the bridge never forwards (`entry.js:48`, `:62`) |
| `Intimidate` | Gated behind `abilityOn`, and the engine never applies the Generation III switch-in trigger itself |
| `Flash Fire`, `Plus`, `Minus` | Their boosts need the same unforwarded flag |
| `Forecast` | It rewrites Castform's typing from supplied weather, but the bridge passes a chosen ability name rather than the ability the running game actually has |

### 6.2 Heart & Soul 2.0.5 Ability Capability (Gap C2 Closed)

H&S features ~80 post-Gen-III abilities affecting damage. DualDex replaces the blanket statement
that the H&S ability system is unmodelled with a strict per-ability and per-participant capability decision:

1. **Authoritative Runtime Effective Ability Input & Boundary Slot Provenance:**
   Effective abilities are read live from `gBattleMons[battler].ability` (PR #56) and delivered as
   `BattlerRuntimeObservation`. `CalcParticipantPresenter` maps this observation to `EffectiveAbilityResolution`:
   - Active-party-slot matching: The player's live ability is accepted **only** when the selected party index
     matches the observed `partySlot`. The opponent's live ability is accepted **only** when the observed enemy
     party slot matches `activeEnemySlot`. Mismatches leave `ability = null` with `CalcInputField.ABILITY` in
     `unknownFields`.
   - Central boundary slot provenance binding: `CalcPokemonInput` carries `partySlot` provenance.
     `CalcRequestBoundary.reconcileParticipantAbility` directly verifies `participant.partySlot == observation.state.partySlot`.
     Any slot mismatch or missing provenance strips caller-supplied abilities to `null` and marks `ABILITY` unknown.
   - Single snapshot per recalculate: `CalcTabScreenView.recalculate()` captures `playerBattlerState` and
     `enemyBattlerState` once per cycle, eliminating race conditions across presenter and boundary passes.
   - Authoritative numeric ID drives capability verdict: For live observations, capability is determined strictly
     from the authoritative runtime numeric `abilityId` (`HnsAbilityRegistry.classify(abilityId)`). The catalogue
     display name (`abilityIdentity.name`) is preserved for UI display and diagnostics, but never determines capability.
     A malformed observation masquerading as supported under a false name fails closed via `abilityId`.
   - Domain range check: `abilityId` is directly range-checked against the pinned ability domain (`0..ABILITY_ID_MAX`,
     i.e. 0..310) at native array decoding, presenter resolution, boundary reconciliation, and policy evaluation.
   - Faint/replacement/unavailable windows, `AMBIGUOUS` (doubles), and `OBSERVED_INVALID` fail closed to unknown
     ability (`HNS_EFFECTIVE_ABILITY_UNREADABLE`).
   - Anti-spoofing in `CalcRequestBoundary`: Live-read participants enforce boundary ownership; caller-supplied
     abilities cannot override or fabricate authoritative observations.

2. **Per-Ability Capability Audit (`HnsAbilityRegistry`):**
   - **Historical C2 supported set:** `PROVEN_NO_DAMAGE_EFFECT` included `ABILITY_NONE`, `KEEN EYE`, and `INSOMNIA`; the pinned audit above supersedes the old incomplete list.
     the H&S battle engine. No ability blocker is added.
   - **Historical at the C2 snapshot:** `UNSUPPORTED_DAMAGE_RELEVANT` (`GUTS`, `THICK FAT`, `HUGE POWER`, `PURE POWER`, starter pinch abilities,
     and modern abilities): While isolated multipliers match for some Gen 3 abilities, H&S fixed-point ability composition
     and stat-stage ordering diverge from ADV:
     - H&S combines ability multipliers together in fixed-point (`UQ_4_12`) and applies stat stages *before* ability multipliers,
       whereas ADV applies ability modifiers sequentially with intermediate flooring *before* stat stages.
     - Example: with raw Attack 105, a statused Guts attacker vs Thick Fat defender produces effective Attack 79 in H&S
       (combined modifier $1.5 \times 0.5 = 0.75$ applied once) versus 78 in ADV ($\lfloor 105/2 \rfloor = 52 \rightarrow \lfloor 52 \times 1.5 \rfloor = 78$).
     - Non-neutral stat stages compound this divergence.
     - Starter pinch abilities modify Attack stat in H&S vs Base Power in ADV (17,750 diverging damage spreads).
     - At this C2 snapshot, damage-relevant abilities were fail-closed. Request-local clearances were added later and are documented in §6.3; conditional pinch support remains documented in §14.6.

3. **Prevention of `@smogon/calc` Default Ability Substitution:**
   `@smogon/calc`'s `Pokemon` constructor defaults missing or `"None"` abilities to `species.abilities[0]`.
   In H&S, this would silently grant abilities (e.g. giving Machamp Guts when it has No Guard or None).
   In `tools/calc-bundler/entry.js`, `resolveAbility()` maps omitted, empty, `"None"`, or `"(other)"` abilities
   to `'(other)'` when `typeSystem === 'hns_2_0_5'`, preventing default substitution. Pinned in native tests
   (`native/tests/test_js_calc.c:check_gap_c2_abilities`).

4. **Spelling and Normalization:**
   For vanilla, ability and item names are matched leniently but sent canonically: `CalcCapabilityPolicy.normaliseNames`
   rewrites accepted names to exact Title Case (`"GUTS"` → `"Guts"`, `"HUGE POWER"` → `"Huge Power"`).
   Unmodelled names are left intact and blocked. `Sea Incense` is intentionally *not* in the type-boost list:
   the engine models it as its own ×1.05 Water case rather than as the generic ×1.1 type-boost item.
   For H&S, item capability is decided by the exact numeric item ID, never by a name. The authorized
   request preserves modelled items such as Wise Glasses, omits proven-neutral items, and removes a
   known relevant unsupported item only for a named #86 estimate (§7).

### 6.3 Source-backed contextual ability relevance

The global ability registry answers whether an ability is supported in general. For an
`UNSUPPORTED_DAMAGE_RELEVANT` ability, or a conditionally modelled ability with a reviewed
request-local rule, `HnsAbilityContextPolicy` assesses the current request using explicit operands.
`PROVEN_IRRELEVANT` adds no caveat, `RELEVANT` is either calculated exactly for a modelled
damage-time modifier or can become a named #86 caveat after neutralization, and `UNKNOWN` remains a
hard refusal. This does not clear any unrelated `CalcLimitation`.

The reviewed rules cover Solar Power (94), Defeatist (129), Fur Coat (169), Tera Shell (ID 308), Truant (54), Telepathy (140), Levitate (26), Hustle
(55), Guts (62), Huge Power (37), Pure Power (74), Thick Fat (47), Adaptability (91), Battle Armor (4), Shell
Armor (75), Friend Guard (132), Plus (57), Minus (58), and the final-modifier batch Tinted Lens
(110), Sniper (97), Neuroforce (233), Filter (111), Solid Rock (116), Prism Armor (232), Multiscale
(136), Shadow Shield (231), and Ice Scales (246). The source-checkable predicates are
tracked in [`context_rules.json`](../tools/hns-abilities/context_rules.json) and validated against
the exact pinned commit during `./ci.sh source-check`.

The rule examples are intentionally request-specific:

| Ability | Proven irrelevant examples | Still blocked |
|---|---|---|
| Tera Shell | Attacker side; defender's current form is not Terapagos-Terastal; live Terapagos-Terastal HP is below maxHP | Full-HP Terapagos-Terastal; missing live species or required HP authority |
| Truant | Defender side for incoming damage | Attacker side because `truantCounter` is not observed |
| Telepathy, Friend Guard, Plus, Minus | Exact live battler count is two (authoritative Singles, no partner) | Doubles or unknown topology |
| Levitate | Attacker side; defender with an authoritative non-Ground effective move | Defender versus Ground or unknown effective move type |
| Hustle | Defender side or authoritative Special move | Physical attacker move; missing category authority |
| Solar Power | Defender side, final Physical category, or authoritative no-Sun / suppressed-Sun context | Special move in Sun with missing live weather, either effective active ability, attacker item, or liveness evidence |
| Defeatist | Defender side or valid live HP above `floor(maxHP/2)` | Attacker at/below threshold is modelled; missing/invalid live HP pair |
| Fur Coat | Attacker side or final Special category with Wonder Room off | Physical defender hit with Wonder Room active/unread or nonordinary alternate defense selection |
| Guts | Defender side; attacker using a Special move; observed neutral status for an authoritative physical move | Statused Physical move is modelled with raw `status1 & STATUS1_ANY`; unread/invalid status or missing category authority |
| Huge Power, Pure Power | Defender side; attacker using an authoritative Special move | Physical attacker move or missing category/type authority |
| Thick Fat | Attacker side; defender with a known non-Fire/non-Ice effective move | Fire/Ice move or missing effective-type authority |
| Adaptability | Defender side; final effective move type is absent from the exact live attacker types | Missing final type, missing live types, unsupported active gimmick, or unknown topology |
| Tinted Lens | Defender side; exact effectiveness is above 0.5 | Attacker with effectiveness at or below 0.5; unknown effectiveness |
| Sniper | Defender side; attacker hit is noncritical | Attacker on a critical hit; unknown critical flag |
| Neuroforce | Defender side; exact effectiveness is below 2.0 | Attacker on a super-effective hit; unknown effectiveness |
| Filter, Solid Rock, Prism Armor | Attacker side; exact effectiveness is below 2.0 | Defender on a super-effective hit; unknown effectiveness |
| Multiscale, Shadow Shield | Attacker side; exact live HP is below maxHP | Defender at full HP; missing/invalid live HP pair |
| Ice Scales | Attacker side; final category is Physical | Defender hit by a Special move; unknown final category |
| Battle Armor, Shell Armor | Attacker side; defender on an ordinary fixed noncritical hit | Defender on a critical request; missing move-shape authority |

For Tera Shell, `CalcRequestBoundary` reads the current `BattlePokemon.species` form word from the
live active battler and binds the defender's observed HP/maxHP. It does not use the party species
default. Missing species or HP on Terapagos-Terastal yields `UNKNOWN`, never clearance. The pinned
data pack uses the ambiguous display name `Terapagos` for all three forms, so its independent
`SPECIES_NOT_IN_PINNED_DATA` gate still refuses a Terapagos request even when below-full HP clears
the Tera Shell blocker; contextual clearance never clears that separate species limitation. On the
Battle tab, remaining blockers are retained structurally and multiple blocker names render on
separate lines; a refused request never invokes the calculator. `TERAPAGOS_TERASTAL_SPECIES_ID` is
generated from the pinned `SPECIES_TERAPAGOS_TERASTAL` macro (resolved by the ARM preprocessor) into
`HnsAbilityAuditData.kt`, so `./ci.sh source-check` fails if the Kotlin constant and the pinned species
header disagree. The QuickJS host suite proves the Tera Shell + Truant pair reaches the shipped
`calculateHnsDamage` unmodified (echoed ability names, no default-ability substitution) and equals the
neutral-control vector and the independent H&S oracle.

### 6.4 Low-state final modifiers (issue #91 slice)

These branches occupy the exact stages in the pinned damage pipeline. Their operands are rebound by
`CalcRequestBoundary`; no additional memory reader is introduced. `HnsMoveAuthority.effectiveType`
is shared by STAB, effectiveness, and type-dependent modifiers, while its final category feeds Ice
Scales.

| Stage / ability | Exact predicate and factor | Relevant side and authority | Mold Breaker metadata / Ability Shield | Unknown remains blocked when |
|---|---|---|---|---|
| STAB — Adaptability | Final effective move type is in live attacker types: ×2.0 (`UQ4.12 8192`); ordinary STAB is ×1.5 (`6144`) | Attacker; exact live types plus `HnsMoveAuthority.effectiveType` | Not a defender effect; not applicable | Move/type identity, live attacker types, Singles topology, or inactive gimmick proof is missing |
| Attacker slot — Tinted Lens | Effectiveness ≤0.5: ×2.0 (`8192`); immune hits still return zero before this slot | Attacker; exact H&S effectiveness from final type, live defender types and observed Ring Target/Iron Ball handling | Not a defender effect; not applicable | Final type or exact effectiveness is unresolved |
| Attacker slot — Sniper | `isCrit`: ×1.5 (`6144`) in addition to the normal ×2 critical stage | Attacker; selected critical flag and live attacker ability | Not a defender effect; not applicable | Ordinary move, live ability, or selected critical flag is unresolved |
| Attacker slot — Neuroforce | Effectiveness ≥2.0: ×1.25 (`5120`) | Attacker; exact H&S effectiveness | Not a defender effect; not applicable | Final type or exact effectiveness is unresolved |
| Defender slot — Filter / Solid Rock | Effectiveness ≥2.0: ×0.75 (`3072`) | Defender; exact H&S effectiveness | `breakable = TRUE`; Ability Shield preserves it. Relevant unshielded Mold Breaker remains hard-refused by the existing suppression policy. | Final type or exact effectiveness is unresolved |
| Defender slot — Prism Armor | Effectiveness ≥2.0: ×0.75 (`3072`) | Defender; exact H&S effectiveness | Not marked breakable; Mold Breaker does not suppress it. Ability Shield is not needed for this distinction. | Final type or exact effectiveness is unresolved |
| Defender slot — Multiscale | Live defender HP equals live maxHP: ×0.5 (`2048`) | Defender; exact live integer HP/maxHP pair | `breakable = TRUE`; Ability Shield preserves it. Relevant unshielded Mold Breaker remains hard-refused. | HP/maxHP is unread, invalid, or not authoritative |
| Defender slot — Shadow Shield | Live defender HP equals live maxHP: ×0.5 (`2048`) | Defender; exact live integer HP/maxHP pair | Not marked breakable; Mold Breaker does not suppress it. Ability Shield is not needed for this distinction. | HP/maxHP is unread, invalid, or not authoritative |
| Defender slot — Ice Scales | Final category is Special: ×0.5 (`2048`) | Defender; final `HnsMoveAuthority.category`, including TYPE_BASED resolution after a supported type rewrite | `breakable = TRUE`; Ability Shield preserves it. Relevant unshielded Mold Breaker remains hard-refused. | Final move category or live defender ability is unresolved |

Pinned source explicitly distinguishes the defender flags: **Filter, Solid Rock, Multiscale, and Ice
Scales are breakable; Prism Armor and Shadow Shield are not marked breakable.** The same source's
`CanBreakThroughAbility` path checks Ability Shield before applying Mold Breaker. The calculator
models Shield preservation for this batch and continues to refuse the four relevant unshielded
breakable cases rather than silently applying a modifier the battle would suppress.

High-level order remains:

```text
pre-roll modifiers → random roll → STAB → effectiveness → burn/frostbite
→ Z/Max protection → GetOtherModifiers → minimum-damage floor
```

Within `GetOtherModifiers`, target modifiers and Singles screens precede ability slots, and the
attacker/defender ability order follows unmodified raw Speed (`attacker ability → defender ability`
when attacker Speed is greater than or equal; otherwise defender then attacker). Partner and item
slots retain their pinned positions. The accumulator and final integer application both use the
pinned half-down UQ4.12 operations. The separate #105 base-power product still uses half-up
accumulation. Adaptability stays in STAB and never enters `GetOtherModifiers`.

The oracle exercises Tinted Lens ×0.5/×0.25 and immunity, Neuroforce threshold controls, Sniper with
the normal critical multiplier, each defender reduction and HP/category controls, Mold Breaker and
Ability Shield distinctions, low-damage flooring, and raw-Speed slot order. Adaptability is tested
against final Fairy-toggle types. The rewrite matrix exercises Normalize, all four -ate abilities,
and Liquid Voice with matching and nonmatching live types. Those rewrite abilities are separate
attacker identities because one battler cannot simultaneously have Adaptability and a second
rewriting ability. Unsupported active gimmicks continue to refuse.

---

## 7. Held items (Gap C3 closed for an explicit, contextual subset)

Final item capability is **contextual**: it is the combination of the static item audit
(`HnsItemRegistry`) with the current move/item interaction audit
(`HnsMoveItemInteractionRegistry`). An item's own hold effect can be proven harmless for the ordinary
damage path and still determine the result of a move that reads item identity, presence or absence, so
the two audits are never collapsed.

H&S item identity is now exact and independent of the generic expansion table:

* **Exact source-derived catalogue.** `tools/hns-items/generate_hns_items.py` preprocesses the pinned
  build's own `src/item.c` translation unit and derives, from `include/constants/items.h` (`enum Item`)
  and `src/data/items.h` (`gItemsInfo[]`):
  - the exact item domain: `ITEM_NONE = 0` … `ITEM_ID_MAX = 900` (`ITEMS_COUNT = 901`);
  - every canonical `ITEM_*` symbol, its exact `gItemsInfo` display name, and its compiled
    `holdEffect` / `holdEffectParam`.
  The generated Kotlin artifact is `app/src/main/java/com/dualdex/pokemon/hns/Hns205ItemCatalogue.kt`.
  Aliases in the enum (e.g. `ITEM_ENERGYPOWDER = ITEM_ENERGY_POWDER`) resolve to the one canonical
  table identity and never produce a second entry; an unresolved alias or a missing table entry is a
  hard generation error. `ItemDatabase.expansionMap` is **never** H&S authority.
* **Current battle item, not stored party item.** For a live active battler the authority is the engine's
  own current item word, `gBattleMons[battler].item`. The battle engine rewrites that word when an item is
  consumed (`[src/battle_script_commands.c:6607]`), knocked off (`[src/battle_move_resolution.c:3055]`),
  stolen (`[src/battle_script_commands.c:2227]`, `:2239`), swapped (Trick/Switcheroo,
  `[src/battle_script_commands.c:9818-9819]`) or flung. The controller also pushes in-battle item changes
  back into the party record immediately through `REQUEST_HELDITEM_BATTLE`
  (`[src/battle_controllers.c:1804-1806]` writes `MON_DATA_HELD_ITEM`; emitted on consume
  `[src/battle_script_commands.c:6611]`, Knock Off `[src/battle_move_resolution.c:3063]`, steal
  `[src/battle_script_commands.c:2242-2249]`, Trick/Switcheroo `[src/battle_script_commands.c:9824-9827]`
  and Fling `[src/battle_util.c:10216]`), so `ParsedPokemon.heldItem` is **not** guaranteed to be frozen
  until battle end; it is a separate, asynchronously-updated copy, while `gBattleMons[battler].item` is the
  synchronous battle-engine authority. The current state wins whenever the participant is the authoritative
  active battler.
  The damage-path derivation is `CalculateMoveDamage → GetBattlerHoldEffect → GetItemHoldEffect →
  gItemsInfo[...].holdEffect` and `GetBattlerHoldEffectParam` (`[src/battle_util.c:8231-8234]`,
  `:5813-5855]`, `[src/item.c:860-867]`).
* **Precedence rules.** Active player/enemy slot match → current battle item, including an authoritative
  `ITEM_NONE` that overrides a stale nonzero party item. Bench player → exact parsed party held-item ID
  (no `gBattleMons` observation required). Slot mismatch for the opponent, faint/replacement windows,
  doubles ambiguity, and unverified reads → unreadable current item, never a party fallback. The
  `activeBattle` boolean is a hint, not the authority: any supplied runtime observation is itself battle
  evidence, so a raw LIVE_READ caller cannot pass `activeBattle = false` while handing over a current
  battler and thereby downgrade to the party item.
* **Numeric-ID capability.** `HnsItemRegistry.classify(itemId)` is the global capability authority; a
  display name can never authorize capability, and an unknown/out-of-domain ID is `UNCLASSIFIED`. Every
  in-domain ID is classified by the reviewed decision for its compiled `holdEffect` family (§7.4), unless
  an identity exception overrides it; the decisions are generated into `HnsItemAuditData.kt` and
  re-verified against the pinned source by `./ci.sh source-check`.
* **Request-local relevance.** A globally unsupported item is then assessed by `HnsItemContextPolicy`
  for the exact request (§7.5). `PROVEN_IRRELEVANT` adds no caveat; a known `RELEVANT` item gets a
  soft disposition and is removed from the execution request for a named estimate. `UNKNOWN` stays
  hard, and no other limitation is touched.
* **Contextual interaction audit.** A move whose damage calculation reads held-item state is audited
  separately by `HnsMoveItemInteractionRegistry`, keyed by the exact pack's numeric move ID. Because no
  item-dependent interaction is modelled, every such move adds
  `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` and no item-dependent request reaches the engine. This is
  required, not optional: the authorized request strips every supported item before `@smogon/calc`
  (`HnsItemRegistry.engineItemName` returns null), which destroys exactly the input Fling/Knock
  Off/Acrobatics need, so the move must be refused first rather than allowed to no-op.

### 7.1 Representative globally supported items (no item blocker for item-independent moves)

This table is the **static** half of the contextual decision. It proves only that the item's own hold
effect does not touch the ordinary damage path; a request using any of these items is still refused if
the selected move is item-dependent (§7.2). These six rows are examples of the 32 globally neutral
hold-effect families (585 items) audited in §7.4.

| Item | H&S numeric ID | Identity source | H&S source effect | ADV behavior | DualDex category | Reason |
|---|---|---|---|---|---|---|
| `ITEM_NONE` | 0 | `gItemsInfo[0]` | none | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Authoritatively no held item. |
| Exp. Share | 461 | `gItemsInfo` | `HOLD_EFFECT_EXP_SHARE` (EXP only, `[src/battle_script_commands.c:11969-11987]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | No crit/power/type/stat/survival/HP/status/speed interaction. Fling-gated like every other item (§7.2). |
| Soothe Bell | 463 | `gItemsInfo` | `HOLD_EFFECT_FRIENDSHIP_UP` (`[src/pokemon.c:7777]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Friendship only. Fling power 10. |
| Amulet Coin | 466 | `gItemsInfo` | `HOLD_EFFECT_DOUBLE_PRIZE` (`[src/battle_main.c:3189]`, `[src/battle_hold_effects.c:48]`, `:1055]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Prize money only. Fling power 30. |
| Cleanse Tag | 467 | `gItemsInfo` | `HOLD_EFFECT_REPEL` (`[src/wild_encounter.c:1334]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Wild encounters only. Fling power 30. |
| Lucky Egg | 470 | `gItemsInfo` | `HOLD_EFFECT_LUCKY_EGG` (EXP ×1.5, `[src/battle_script_commands.c:11982]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Earned EXP only. Fling power 30. |

### 7.2 Move/item interaction audit (the contextual half)

`HnsMoveItemInteractionRegistry` audits every pinned damage-path read of held-item state. Every move
below is present in the exact H&S pack and is blocked with `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED`
because this calculator does not reproduce the interaction. The lookup key is the pack's numeric move
ID, so a moved or renamed move fails a test rather than silently dropping out of the gate.

| Move | H&S numeric ID | Interaction | Pinned source | Effect on damage |
|---|---|---|---|---|
| Fling | 374 | attacker item identity | `CalcMoveBasePower EFFECT_FLING` `[src/battle_util.c:6344-6346]` | `basePower = GetFlingPowerFromItemId(gBattleMons[battlerAtk].item)`; harmless items are flingable (Soothe Bell 10; Amulet Coin / Cleanse Tag / Lucky Egg / Exp. Share 30). |
| Natural Gift | 363 | attacker item identity | `[src/battle_util.c:6395-6397]`, `[src/battle_main.c:6327-6330]` | base power and **type** derive from the attacker's held berry. |
| Acrobatics | 512 | attacker item absence | `[src/battle_util.c:6421-6424]` | doubles base power when `gBattleMons[battlerAtk].item == ITEM_NONE`; therefore even `ITEM_NONE` is not context-free. |
| Knock Off | 282 | defender item presence | `[src/battle_util.c:6619-6623]` | ×1.5 when the defender holds a removable item. |
| Poltergeist | 737 | defender item presence | `[src/battle_move_resolution.c:1301-1305]` | fails outright when the defender holds no item. |
| Judgment | 449 | attacker item identity | `GetDynamicMoveType EFFECT_CHANGE_TYPE_ON_ITEM` `[src/battle_main.c:6284-6286]` | the held plate selects the move type. |
| Techno Blast | 546 | attacker item identity | `[src/battle_main.c:6284-6286]` | the held Drive selects the move type. |
| Multi-Attack | 672 | attacker item identity | `[src/battle_main.c:6284-6286]` | the held Memory selects the move type. |

Deliberately excluded, after auditing the whole damage path: `Weather Ball` (its only item read is the
Utility Umbrella hold effect at `[src/battle_main.c:6212-6240]`, and no supported item has that hold
effect); Low Kick / Heat Crash (weight only via the Float Stone hold effect,
`GetBattlerWeight` `[src/battle_util.c:6053-6087]`, and Float Stone is refused statically);
Pluck/Bug Bite/Thief/Covet (the item is moved after the damage formula); Sucker Punch (it reads the
defender's chosen move, not an item); and the gem/plate/choice/pinch-berry hold effects (they multiply
an ordinary move and are already refused by the static audit).

### 7.3 Representative items from the pre-#92 audit snapshot

The following table records why these items were initially classified unsupported during the Gap C3
audit. Issue #92 has since promoted the direct-damage families in the first part of the table; use
the current category totals and exact predicates in §7.4, §7.5, and
[`HNS_GROUP_D_HELD_ITEMS.md`](HNS_GROUP_D_HELD_ITEMS.md).

| Item | H&S numeric ID | H&S source effect | ADV behavior | DualDex category | Reason |
|---|---|---|---|---|---|
| Charcoal (and the other traditional type boosters) | 426 | ×1.2 to base power (`TYPE_BOOST_PARAM = 20`, `[src/battle_util.c:6808]`, `:6839-6843]`, `:6871]`) | ×1.1 applied to the attack stat | `UNSUPPORTED_DAMAGE_RELEVANT` | Multiplier and application stage differ. |
| Choice Band | 442 | ×1.5 post-stage Attack, composed in fixed point (`[src/battle_util.c:7177-7180]`, `:7191]`) | sequential integer floors | `UNSUPPORTED_DAMAGE_RELEVANT` | Modifier ordering/composition diverge off neutral stages. |
| Choice Specs / Choice Scarf | 443 / 444 | same composition | special / Speed only | `UNSUPPORTED_DAMAGE_RELEVANT` | Same ordering concern; Scarf changes speed-dependent power/order. |
| Light Ball | 392 | ×2 Atk/SpA for Pikachu (`[src/battle_util.c:7173-7176]`) | ×2 Attack | `UNSUPPORTED_DAMAGE_RELEVANT` | H&S condition and composition not proven equal. |
| Thick Club | 394 | ×2 physical Attack for Cubone/Marowak (`[src/battle_util.c:7165-7168]`) | ×2 Attack | `UNSUPPORTED_DAMAGE_RELEVANT` | Not proven ADV-equivalent in H&S composition. |
| Deep Sea Tooth / Scale | 399 / 398 | ×2 SpA / SpD for Clamperl (`[src/battle_util.c:7169-7172]`, `:7353-7356]`) | ×2 | `UNSUPPORTED_DAMAGE_RELEVANT` | Not proven ADV-equivalent. |
| Soul Dew | 400 | ×1.2 Psychic/Dragon base power for the Lati twins (`[src/battle_util.c:6833-6838]`) | ×1.5 SpD (ADV) | `UNSUPPORTED_DAMAGE_RELEVANT` | H&S and ADV semantics differ. |
| Life Orb / Expert Belt | 479 / 477 | ×1.3 after the roll + 1/10 recoil / ×1.2 on super-effective hits (`[src/battle_util.c:7673-7674]`, `:7669-7671]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Post-Gen-III items. |
| Muscle Band | 475 | ≈×1.1 physical base power (`[src/battle_util.c:6813-6816]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Post-Gen-III item. |
| Wise Glasses | 476 | ×1.1 special base power via `halfDown(4505, bp)` (`[src/battle_util.c:6817-6819]`) | none | `MODELLED_HNS_SPECIFIC` | Modelled in QuickJS engine; host verified against independent C oracle fixtures. |
| Eviolite / Assault Vest | 494 / 503 | ×1.5 Def / SpD (`[src/battle_util.c:7361-7373]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Evolution state is not in the request shape. |
| Normal Gem / Fire Gem | 339 / 340 | ×1.3 matching-type base power and consumed (`[src/battle_util.c:6633-6634]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Gem consumption state is not modelled. |
| Occa Berry | 550 | ×0.5 super-effective Fire damage and consumed (`[src/battle_util.c:7686-7696]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Consumption state is not modelled. |
| Focus Sash / Focus Band | 481 / 469 | survive at 1 HP (Sash consumed) (`[src/battle_util.c:8193-8206]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | KO presentation would be wrong. |
| Leftovers / Shell Bell / Rocky Helmet | 472 / 473 / 496 | between-turn heal / heal on damage / recoil on contact (`[src/battle_hold_effects.c:642-656]`, `:536-555]`, `:245-262]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Globally: later HP/KO changes. Request-locally irrelevant to a single ordinary hit (§7.5). |

At the time of the original Gap C3 audit these rows were `UNSUPPORTED_DAMAGE_RELEVANT`; §7.5 lists
the exact request shapes in which a remaining unsupported item is proven irrelevant. A known
relevant item may instead be neutralized as a named #86 caveat when its identity and contextual
decision are authoritative and the move is item-independent. Red Orb and Blue Orb are now assigned
to the modeled Primal Orb family. The e-Reader Enigma Berry remains the one `UNCLASSIFIED` identity
and fails closed.
Independently, any request whose move is item-dependent (§7.2) fails
closed with `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED`, even when the item itself is `ITEM_NONE` or a
supported static item. An observed current item that cannot be authoritatively read is
`HNS_EFFECTIVE_ITEM_UNREADABLE`, and a manual name that does not resolve in the exact H&S catalogue is
`HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE`.

The engine request omits the generic ADV item name for neutral items and unsupported items proven
irrelevant to that exact request. Group D `MODELLED_HNS_SPECIFIC` items also use no generic engine
item spelling; instead, `DamageCalculator` serializes the current numeric item ID and generated
effective hold-effect descriptor for the dedicated H&S QuickJS branches. Explicit engine adapters
remain item-specific. This is safe only because a move whose formula reads held-item identity/presence
is refused first. The QuickJS
host suite proves an omitted item and `"None"` are the same calculation, and that a name the engine
does not model is a silent no-op, while a name it does model changes damage — which is exactly why a raw
H&S source name must never be forwarded. The PR #78 follow-up fixture
(`check_pr78_tera_shell_truant_and_item_stripping`) additionally proves, on the shipped bundle, that the
stripped request reaches `calculateHnsDamage` with `attackerItem`/`defenderItem` null and equals the
explicit `ITEM_NONE` request and the independent H&S oracle, while a forwarded engine-known name (Silk
Scarf on Tackle) would change the vector.

### 7.4 Complete hold-effect family audit (all 901 identities)

Methodology. The 901 pinned identities share 130 compiled `holdEffect` values. Each value is a
reviewed **family** in [`tools/hns-items/decisions.json`](../tools/hns-items/decisions.json) with a
category, a group and a rationale; every item takes its family's decision unless an identity exception
overrides it. `tools/hns-items/generate_hns_item_audit.py --check` (run by `./ci.sh source-check`)
never decides a category; it fails closed when:

* the re-derived item domain, symbols, names, `holdEffect` or `holdEffectParam` differ from
  `Hns205ItemCatalogue.kt` (missing/extra/duplicate IDs included);
* a catalogue hold effect has no family decision, or a decision names a hold effect the build does not use;
* a family's `reviewed_refs` differ from the pinned `src/**/*.c` references to that `HOLD_EFFECT_*`
  symbol (AI, debug and animation sources excluded) — a new or moved read cannot inherit a decision;
* a literal `ITEM_*` catalogue symbol appears in pinned `src/battle_*.c` outside the 49 reviewed
  enclosing definitions (`identity_reference_sites`: bag/reward/pickup tables, key-item checks, message
  text, the Natural Gift table, and the e-Reader Enigma indirection);
* an identity exception or context rule names a nonexistent identity/family, a rule refines a
  family outside the allowed categories (currently `UNSUPPORTED_DAMAGE_RELEVANT` or `MODELLED_HNS_SPECIFIC`),
  a rule is not implemented by name in `HnsItemContextPolicy.kt`, or a rule's cited pinned line no
  longer contains its quoted text;
* the generated `item_inventory.tsv` / `HnsItemAuditData.kt` differ from what the review produces.

Results (`HnsItemAuditData.categoryCounts`, also asserted by `HnsItemAuditTest` without the upstream):

| Category | Items | Families |
|---|---|---|
| `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | 585 | 32 |
| `MODELLED_EQUIVALENT` | 0 | 0 |
| `MODELLED_HNS_SPECIFIC` | 108 | 27 |
| `UNSUPPORTED_DAMAGE_RELEVANT` | 205 hold-effect-family items + 2 identity exceptions = 207 | 71 |
| `UNCLASSIFIED` | 1 | 0 families + 1 identity exception (e-Reader Enigma Berry) |

Globally neutral groups: `no_battle_effect` (`NONE`, `REPEL`, `DOUBLE_PRIZE`, `FRIENDSHIP_UP`,
`LUCKY_EGG`, `EXP_SHARE`, `CAN_ALWAYS_RUN`, `PREVENT_EVOLVE`, `DESTINY_KNOT`, `ADRENALINE_ORB`),
`accuracy_only` (`WIDE_LENS`, `ZOOM_LENS`, `EVASION_UP`), `non_damage_utility` (`FLINCH`, `SHED_SHELL`,
`RED_CARD`, `EJECT_BUTTON`, `EJECT_PACK`, `LIGHT_CLAY`, the four weather rocks, `TERRAIN_EXTENDER`,
`GRIP_CLAW`, `HEAVY_DUTY_BOOTS`, `COVERT_CLOAK`, `PROTECTIVE_PADS`, `POWER_HERB`, `LOADED_DICE`) and
`form_hold_no_read` (`MEMORY`, `DRIVE`). The remaining unclassified identity is the e-Reader Enigma Berry (ID 581):
its catalogue hold effect is `NONE`, but `GetBattlerHoldEffectInternal` returns the runtime
`gEnigmaBerries[battler].holdEffect` for that exact ID (`[src/battle_util.c:5834]`), so it is
`UNCLASSIFIED`.

Red Orb and Blue Orb are identity exceptions mapped to the modeled Primal Orb family. Their damage
state is the live settled form, ability, weather, and stats; an unsettled or unobserved transformation
remains unknown.

Rusted Sword (288) and Rusted Shield (289) are also `HOLD_EFFECT_NONE` in the catalogue, but pinned
`src/data/pokemon/form_change_tables.h` uses them in `FORM_CHANGE_BEGIN_BATTLE`: at battle start a
holding Zacian/Zamazenta becomes its Crowned form and Iron Head becomes Behemoth Blade/Bash
(`[src/battle_main.c:689-690]`). They are `UNSUPPORTED_DAMAGE_RELEVANT` identity exceptions with no
request-local clearance. Because a held item can act through form-change tables independently of its
hold effect, source-check also requires `form_change_item_identities` in `decisions.json` to equal
every `ITEM_*` identity (with its methods) in the pinned form-change tables, and it refuses any identity
with a battle-time held method (`FORM_CHANGE_BEGIN_BATTLE` or `FORM_CHANGE_BATTLE_*`) that is classified
neutral. Bag-use rows (`FORM_CHANGE_ITEM_USE*`, e.g. Gracidea, Rotom Catalog) and `FORM_CHANGE_ITEM_HOLD`
rows (Plates, Memories, Drives, orbs, masks, Z-Crystals, whose form is fixed before the move and read
live) are reviewed dispositions.

Family groups with remaining unsupported members (with request-local rules in §7.5 unless noted)
include `attacker_offense`, `defender_defense`, `post_hit_or_residual`, `turn_order`, `weight_only`
(Float Stone), `grounding` (Air Balloon, Iron Ball), `weather_shield` (Utility Umbrella), and
`form_or_ability_changer` (`MEGA_STONE` and `Z_CRYSTAL` remain blocking because they change
form or move state before the hit; `ABILITY_SHIELD` clears only for an observed ordinary Singles
hit with no Gastro Acid, Neutralizing Gas, or defender-side Mold Breaker suppression source). The
full per-item table is
[`tools/hns-items/item_inventory.tsv`](../tools/hns-items/item_inventory.tsv).

Several families under `attacker_offense` and `defender_defense` were promoted by #92. The exact
current category for every item is in `item_inventory.tsv`; the full modeled-family contract and
audit totals are in [HNS_GROUP_D_HELD_ITEMS.md](HNS_GROUP_D_HELD_ITEMS.md).
`HnsMoveItemInteractionRegistry` (§7.2) is unchanged and independent: a globally neutral or
request-locally irrelevant hold effect never clears `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED`.

### 7.5 Request-local item relevance (`HnsItemContextPolicy`)

The reviewed rules, predicates and pinned evidence are in
[`tools/hns-items/context_rules.json`](../tools/hns-items/context_rules.json) (48 rules). The displayed
H&S contract is the single-hit damage range and its percentage of the defender's max HP for the current
observed state; the shipped H&S engine emits no KO text (`calculateHnsDamage` returns
`koChanceText: ""`). Operands are request-owned and rebound by `CalcRequestBoundary`:

* **ordinary move** — `HnsMoveMechanicsRegistry` `ORDINARY_PROVEN_EQUIVALENT` (single hit) and no
  move/item interaction;
* **effective type / category** — from `HnsMoveAuthority`, each with only the evidence it needs (§17):
  the type needs an ordinary move, an observed GIMMICK_NONE attacker, an authoritative ability and
  source move metadata, Electrify observed false and a fully decoded field word; the ability stage
  applies the pinned Normalize, -ate or Liquid Voice predicate before the later field stage. Active
  Electrify and relevant Ion Deluge remain refused. The category is the pinned per-move category
  under PER_MOVE_SPLIT and the effective type's pinned category under TYPE_BASED. An unrelated field bit (terrain, a room,
  Gravity, ...) no longer makes either unknown;
* the decoded live `gFieldStatuses` word (Wonder Room / terrain bits where a rule actually reads them),
  `gBattleWeather`, defender HP/maxHP, and the effective attacker ability ID.

| Family | Proven irrelevant / modelled | Relevant or unknown |
|---|---|---|
| Choice Band, Muscle Band, Thick Club | Defender side; attacker with an authoritative Special move | Known relevant modifier becomes a caveat; unknown category or identity remains hard |
| Choice Specs, Deep Sea Tooth | Defender side; attacker with an authoritative Physical move | Known relevant modifier becomes a caveat; unknown category or identity remains hard |
| Wise Glasses | Defender side; attacker with an authoritative Physical move (PROVEN_IRRELEVANT); attacker with an authoritative Special move (MODELLED) | Unknown category |
| Type boosters, Plates, Gems | Defender side; attacker whose authoritative effective type differs from the pinned `secondaryId` | Matching type becomes a caveat; unknown effective type remains hard |
| Lustrous/Adamant/Griseous Orb, Soul Dew | Defender side; attacker whose effective type is outside the two boosted types | Boosted type (species unobserved) |
| Light Ball, Ogerpon masks, Punching Glove | Defender side | Attacker side (species / punching flag unobserved) |
| Life Orb, Expert Belt, Metronome | Defender side | Attacker side |
| Scope Lens / Razor Claw, Lucky Punch, Leek | Defender side; attacker on an ordinary hit with fixed `isCrit` | Unknown or nonordinary move |
| Assault Vest, Deep Sea Scale | Attacker side; defender vs an authoritative Physical move with Wonder Room observed inactive | Special move; Wonder Room; unknown category, unread field word or unknown field bit |
| Metal Powder | Attacker side; defender vs an authoritative Special move with Wonder Room observed inactive | Physical move (species); Wonder Room |
| Eviolite, Ring Target | Attacker side | Defender side |
| Resist berries | Attacker side; defender vs a different authoritative effective type | Matching type (effectiveness not proven); unknown type |
| Focus Sash | Attacker side; defender whose live HP < maxHP | Full-HP defender; unknown HP |
| Focus Band | Attacker side | Defender side |
| Post-hit / residual (Leftovers, Black Sludge, Shell Bell, Rocky Helmet, HP/status/confusion/pinch berries, Weakness Policy, herbs, orbs, …) | Either side for an ordinary single-hit move, after current damage or outside the selected hit | Non-ordinary or unknown move; Terrain Seed and Berserk Gene remain unresolved pre-hit stat writers. Booster Energy is resolved in issue #92 from its settled live activation payload. |
| Blunder Policy, Room Service | Ordinary move with known non-Analytic attacker | Analytic or unknown attacker ability; nonordinary move |
| Turn order (Choice Scarf, Quick Claw, Custap Berry, Lagging Tail, Macho Brace, Power items, Quick Powder) | Either side for an ordinary move when the live attacker ability is known and not Analytic | Attacker Analytic; unknown ability; non-ordinary move |
| Float Stone | Either side for an ordinary move | Non-ordinary move |
| Air Balloon, Iron Ball | Terrain applicability is resolved from observed grounding when terrain is active; Iron Ball's separate turn-order effect remains gated | Ground move; unread battler/field state; unknown grounding authority (Iron Ball also needs its turn-order predicate) |
| Utility Umbrella | Either side for an ordinary move with observed clear weather | Sun/rain; unobserved weather |

Survival items are handled conservatively: a defender Focus Sash at full HP and any defender Focus Band
keep blocking, because the displayed range/percentage would overstate the HP lost; the raw-damage/KO
split was deliberately not introduced.

On the Battle tab every refusal keeps its blockers structured (`DamageBlockerPresentation`: State,
Field, Weather, SideStatus, Ability, Item, Mechanic — §15.7). One blocker renders as e.g. `Damage unavailable · Your Charcoal not modelled`,
`Damage unavailable · Foe's Focus Sash not modelled` or `Damage unavailable · Your Red Orb not yet
audited` (the name is the pinned `gItemsInfo` name of the exact numeric ID). Several render as a typed
count and one short line each (`2 item blockers` / `You: Silk Scarf` / `Foe: Focus Sash`, or `3 blockers`
for mixed classes), so an ability blocker never hides an item or move blocker.

For vanilla Gen III the policy whitelists exactly the items the ADV pipeline applies
(`CalcCapabilityPolicy.GEN3_MODELLED_ITEM_NAMES`); anything else downgrades a vanilla result to
*Approximate*. The H&S catalogue and numeric item capability never leak into a vanilla calculation.
Note that pinch berries (Liechi/Salac/Petaya/…) and Sitrus are modelled by no generation in this
library, so they are never treated as modelled.

---


## 8. Known limitations that are not fixed here

Recorded so they are not mistaken for oversights. Each is a deliberate scope boundary.

1. **Badge boost (audited in §10.2, resolved in §11).** Active in H&S (`B_BADGE_BOOST GEN_3`), worth
   ×1.1 to the player's attacking and defensive stats through an eligibility-gated, UQ4.12-composed
   modifier. DualDex now reads the authoritative badge flag state from SaveBlock1 (bytes `0x1A98` and `0x1A99`) at runtime
   (`pokemon_read_hns_badge_state_gba`), binds player badge boosts when eligible, and evaluates them
   via UQ4.12 `halfDown(4506, stat)` in `calculateHnsDamage`. If in an active battle and the player's
   badge flags are unobserved, `BADGE_BOOST_NOT_MODELLED` blocks fail-closed.
2. **Ability defaults when no ability is supplied.** The engine applies the species' first bundled
   ability when the caller omits one. For vanilla Gen III that is the same data the profile
   describes, so it is correct there. For H&S, `resolveAbility()` in `entry.js` maps omitted/empty/None
   to `'(other)'` to prevent default substitution. For H&S, a known relevant unmodelled ability is
   neutralized and named in a caveated estimate (§14.9); unread identity or unknown relevance still
   refuses.
3. **Move mechanics beyond the ADV pipeline (gated in §10.3).** Multi-hit turn-doubling, weight-based
   power, fixed damage, Hidden Power's IV-derived base power, Return/Frustration's happiness scaling
   and the rest of the special-effect families are not modelled. Gap C4a derives each move's
   exact `enum BattleMoveEffects` value from the pinned source and refuses every move outside the
   source-proven ordinary subset with `HNS_MOVE_MECHANICS_NOT_MODELLED`; it is no longer an implicit
   assumption that an unclassified move is ordinary.
4. **Ordinary-damage modifier ordering (resolved in §11).** The modifier ordering divergence is resolved
   by `calculateHnsDamage` in `tools/calc-bundler/entry.js`. It applies the random roll *before* STAB,
   type effectiveness, burn and screens, composing each modifier with UQ4.12 half-down matching the pinned
   H&S source. Requests within the supported modifier set (neutral, STAB, type effectiveness, crits, stat
   stages -6..+6, rain/sun weather, screens, burn) clear the modifier-order gate. Unsupported modifiers
   (unmodelled weather, out-of-range stat stages) continue to fail closed with `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`.
4a. **Mutable live battle state (gated in §10.5, expanded in §11).** H&S rewrites operands the request shape
   does not carry — current effective types (`SET_BATTLER_TYPE`), raw battle stat words (Power Trick),
   the dynamic move type (`SetTypeBeforeUsingMove`), and transient damage state. In Gap C4b, live battler
    effective types, raw battle stats (`0x02..0x0A`), and stat stages (`0x18`) are read live from `gBattleMons`.
   An active battle whose mutable classes are not authoritatively observed is refused with
   `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`.
5. **No H&S golden damage fixtures against the running ROM.** Gap C4a/C4b provide **source/host goldens**
   for the ordinary physical, special, STAB, crit, stat stage, badge boost, weather, and screen paths
   (native `test_js_calc.c` fixtures matching the independent C oracle), but full end-to-end loop
   validation against an official running ROM remains an open beta gate.
6. **Snow, terrain and modern side conditions** in a *manual* request are refused rather than approximated, because the
   ADV pipeline accepts the fields and ignores them — the worst possible failure mode. The Calc
   screen only offers Sun/Rain/Sand/Hail, so this gate is invisible in the UI today; it exists to
   stop a future screen (or the battle console) from sending a value the engine would treat as *no
   weather*. (Live H&S terrain and room state is the separate, per-request decision of §15.)

---

## 9. How to promote H&S from refused to approximate

**Reading the challenge toggles is necessary but not sufficient.** An earlier revision of this
document treated it as the whole promotion path. It is one of three independent gaps, and §3.3 shows
why: proving a name belongs to the build does not make the engine compute from that build's record,
and matching two constants does not make it reproduce the build's mechanics.

The three gaps, in dependency order:

### Gap A — the rule is unknown (CLOSED)

**Update (issue #9, Gap A slice):** The runtime challenge rules consumption gap is now closed.
DualDex resolves authoritative runtime challenge rules from `HnsChallengeSettingsSnapshot` via
`CalcRequestBoundary.resolveHnsRuntimeRules`. When exactly trusted (`isExactRuntimeVerified == true`
and `snapshot.status == OBSERVED`), the boundary constructs `CalcHnsRuntimeRules` (`optionStyle`,
`fairyTypesEnabled`, `randomTypesEnabled`, `randomTypeEffectivenessEnabled`) and attaches it to
`DamageCalculationRequest`.

`CalcDataOverrides.kt` consumes `hnsRuntimeRules`:
- Under `optionStyle == 0` (`PER_MOVE_SPLIT`), the boundary-owned move override retains its explicit
  pinned category (`category = "Physical" / "Special"`).
- Under `optionStyle == 1` (`TYPE_BASED`), the move override category is explicitly set from the
  pinned H&S `gTypesInfo` category for the effective type. Omitting it would make `@smogon/calc`
  apply the Generation III Ghost/Dark split instead.

`CalcCapabilityPolicy.kt` evaluates `request.hnsRuntimeRules`:
- If missing, unreadable, or untrusted, calculation is blocked with `FAIRY_TOGGLE_UNREADABLE`,
  `CATEGORY_SPLIT_TOGGLE_UNREADABLE`, `RANDOM_TYPES_UNREADABLE`, `RANDOM_TYPE_EFFECTIVENESS_UNREADABLE`, and
  `CHALLENGE_SETTINGS_UNREADABLE`.
- If observed OFF (`tx_Random_Type == 0`, `tx_Random_TypeEffectiveness == 0`), the unreadable
  limitations are cleared without introducing active-not-modelled blockers.
- If observed ON (`tx_Random_Type == 1`, `tx_Random_TypeEffectiveness == 1`), calculation is blocked
  with `RANDOM_TYPES_ACTIVE_NOT_MODELLED` and/or `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`.
- With exact runtime settings observed, randomizers OFF, and representable types, the type chart is
  modelled via `typeSystem: "hns_2_0_5"` (Gap C1 closed).

**Why H&S calculations remained refused at Gap C2/C4a (historical):**
Gap A connected challenge rules to calculator requests and capability evaluation, but all H&S calculations
remain strictly refused (`CalcSupport.UNSUPPORTED`) because Gap C2 remains open (ability system /
mechanics incompatibility, §9).

Two further *input observability* facts now exist alongside Gap A, still without changing any
verdict (issue #9, live-battler slice; see §14 of the compatibility evidence):

* the **effective live ability** of an authoritative active battler is observed from
  `gBattleMons[battler].ability` and can be named against the pinned PR #54 catalogue;
* the battler's **current effective types** are observed from the same live battle state.

These close part of the “what would the battler's inputs even be?” question, but nothing wires
them to live battle state yet: `ABILITY` remains in `CalcInputPreparation.unknownFields`, live
effective types are not yet mapped to species overrides (authoritative static pack overrides are now
forwarded in Gap B below), and no ability is promoted to modelled because its catalogue name resolves
(§6). Naming an observed ID is identity bookkeeping, not mechanic support.


### Gap B — the engine does not consume H&S data (CLOSED)

**Update (issue #9, Gap B slice):** The data-consumption plumbing gap is now closed.
DualDex extracts authoritative species base stats and types, as well as move base power, type,
and category, from `HeartAndSoul205DataPack` via `CalcDataOverrides.kt`. When an H&S request passes
through `CalcRequestBoundary`, it is enriched with `attackerOverride`, `defenderOverride`, and
`moveOverride`. `DamageCalculator.kt` serializes these under `overrides` in the QuickJS JSON payload.

In `tools/calc-bundler/entry.js`, strict runtime validation (`validateSpeciesOverrides`,
`validateMoveOverrides`) verifies that stats, types, basePower, and category match schema constraints,
and passes them to `@smogon/calc`'s `Pokemon` and `Move` constructors (`options.overrides`).
The host QuickJS native test suite (`native/tests/test_js_calc.c:check_gap_b_data_overrides`)
asserts that overrides modify damage arithmetic, type effectiveness, and stat scaling.

**Category behavior under Gen 3:**
In `@smogon/calc` 0.11.0, `Move` stores `options.overrides.category`. When `category` is explicitly
provided in `overrides`, the Gen 3 ADV pipeline (`calculateADV`) uses `move.category` to select
`atk`/`def` vs `spa`/`spd`. For a generic caller that omits category, that library falls back to its
Gen 3 type-based derivation (`SPECIAL.includes(data.type)`). The current H&S path does not use that
fallback: since #104, `CalcDataOverrides` explicitly materializes TYPE_BASED category from pinned
H&S `gTypesInfo` after resolving the effective move type.

**Why H&S calculations remained refused after Gap B (historical):**
Gap B closed data-consumption plumbing only. At the time of Gap B, calculations remained refused
pending Gap A (resolved in PR #61) and Gap C. Following Gap C1 (exact type system + Fairy toggle),
H&S calculations remain strictly **refused** (`UNSUPPORTED`, `request == null`) due to Gap C2
(`HNS_ABILITY_SYSTEM_NOT_MODELLED`).

### Gap C — the calculator does not reproduce H&S mechanics

#### Gap C1 — exact type system + Fairy toggle behavior (CLOSED)

**Update (issue #9, Gap C1 slice):** The type-system mismatch is resolved:
- The exact 19x19 H&S type chart (extracted from `src/data/types_info.h` at commit `1f42b74dff0e9fe942419845d040663dd829a973`)
  is packaged into `hns_type_chart.json`.
- When `typeSystem: "hns_2_0_5"` is specified, `entry.js` provides a request-local facade `createHnsGeneration(baseGen)`
  that substitutes the H&S type provider (`HNS_TYPES_PROVIDER`) while retaining Gen 3 arithmetic (`num = 3`).
  Ghost/Dark -> Steel is 1.0x neutral; Fairy type exists with modern affinities (2x against Dragon/Fighting/Dark,
  0.5x against Fire/Poison/Steel, 0x immunity from Dragon).
- `tx_Mode_Fairy_Types` toggle behavior:
  - Fairy ON (`fairyTypesEnabled == true`): species and moves retain H&S Fairy typings.
  - Fairy OFF (`fairyTypesEnabled == false`): species retype to pre-Fairy typings (`sPreFairyTypes`, 20 species)
    and Fairy moves retype to alternate typings (`sFairyMoveAltTypes`, 34 moves) via `HnsFairyTypeMappings.kt`.
- Coupling with `optionStyle`: under `TYPE_BASED` (`optionStyle == 1`), damage category comes from the
  pinned H&S category for the effective (retyped) move type (e.g. Moonblast -> Dark -> Physical;
  Dazzling Gleam -> Normal -> Physical).
- Verified via host QuickJS suite (`native/tests/test_js_calc.c:check_gap_c1_type_system`), Kotlin unit tests
  (`CalcDataOverridesTest.kt`, `CalcCapabilityPolicyTest.kt`), and type-system generator tests (`test_generate_hns_type_system.py`).

**Why H&S calculations remained refused after Gap C1:**
Gap C1 closed the type-system gap. When challenge settings were observed, randomizers were OFF, and types were
representable, `HNS_TYPE_CHART_NOT_MODELLED` was cleared. However, calculations remained refused
due to unmodelled abilities (`HNS_ABILITY_SYSTEM_NOT_MODELLED`).

#### Gap C2 — authoritative effective ability input + conditional ability support (CLOSED)

**Update (issue #9, Gap C2 slice):** The blanket ability blocker is replaced with a precise, per-participant,
per-ability capability decision:
- **Authoritative live effective abilities:** Live active battler ability is read from `gBattleMons[battler].ability`
  (PR #56) and delivered as `BattlerRuntimeObservation`.
- **Active-party-slot matching:** The player's ability is accepted only when `partySlot == selectedPartyIndex`.
  The enemy's ability is accepted only when `partySlot == activeEnemySlot`. Slot mismatches, faint windows,
  `AMBIGUOUS` (doubles), and `OBSERVED_INVALID` fail closed to unknown ability (`HNS_EFFECTIVE_ABILITY_UNREADABLE`).
- **Anti-spoofing ownership:** In `CalcRequestBoundary`, caller-supplied abilities on `LIVE_READ` participants
  cannot override or fabricate authoritative observations.
- **Conditional ability capability (`HnsAbilityRegistry`):**
  - `PROVEN_NO_DAMAGE_EFFECT` (`ABILITY_NONE`, `KEEN EYE`, `INSOMNIA`, and the audited neutral set above): zero move-damage effect in H&S. Cleared with no ability blocker.
  - `MODELLED_HNS_CONDITIONAL`: the four starter pinch abilities use the live HP gate (§14.6); Hustle/Guts use the Attack-stat stage only with verified category and (for Guts) status operands; Group C immunities use the request-local conditions in [HNS_GROUP_C_IMMUNITIES.md](HNS_GROUP_C_IMMUNITIES.md). Group D includes the nine #105 base-power abilities (Technician, Iron Fist, Strong Jaw, Mega Launcher, Sharpness, attacker Water Bubble, defender Water Bubble, Heatproof, Steelworker, Toxic Boost, and Flare Boost), the six #106 move-type abilities (Normalize, Refrigerate, Pixilate, Aerilate, Galvanize, and Liquid Voice), Punk Rock, holder-side Steely Spirit, Adaptability, and the #91 final batch (Tinted Lens, Sniper, Neuroforce, Filter, Solid Rock, Prism Armor, Multiscale, Shadow Shield, and Ice Scales); each remains gated by its pinned request predicate. Water Bubble burn prevention/status clearing and Steely Spirit's attacker-partner branch remain deferred.
  - `UNSUPPORTED_DAMAGE_RELEVANT` (including Thick Fat, Huge Power, Pure Power, and other abilities without an admitted request rule): damage-relevant but divergent or unmodelled. Fails closed with `HNS_ABILITY_EFFECT_NOT_MODELLED` unless a request-local rule can prove irrelevance.
- **Default ability substitution prevention:** `@smogon/calc` defaulting to `species.abilities[0]` is prevented
  by setting `options.ability = '(other)'` when ability is omitted, empty, or `"None"` under `typeSystem === 'hns_2_0_5'`.
- Verified via QuickJS host tests (`native/tests/test_js_calc.c:check_gap_c2_abilities`) and Kotlin unit tests (`CalcHnsAbilityTest.kt`).

**Why H&S calculations remained refused at Gap C2/C4a (historical):**
Gap C2 resolves ability input and capability gating. When participants have modelled or proven-no-effect abilities,
no ability blockers are added. However, H&S calculations remain strictly **refused** (`CalcSupport.UNSUPPORTED`,
`request == null`) by the C4a mechanics blockers: `BADGE_BOOST_NOT_MODELLED` (§10.2),
`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` (§10.4), and `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` (§10.5).
The blanket held-item blocker was removed by Gap C3 below.

#### Gap C3 — authoritative held items + contextual item capability (CLOSED for an explicit subset)

**Update (issue #9, Gap C3 slice):** The blanket `HNS_HELD_ITEM_SYSTEM_NOT_MODELLED` blocker is replaced
with a precise, per-participant, per-item, **per-move** capability decision:

- **Exact item catalogue.** `tools/hns-items/generate_hns_items.py` derives the exact `enum Item` domain
  (`ITEM_ID_MAX = 900`, `ITEMS_COUNT = 901`), canonical symbols, display names, `holdEffect` and
  `holdEffectParam` from the pinned build's `src/item.c` translation unit; the generated
  `Hns205ItemCatalogue.kt` is source-checked byte-for-byte. `ItemDatabase.expansionMap` is not authority.
- **Authoritative current item.** `HnsBattlerRuntimeState` gains `itemId` / `itemOutOfDomain`, read from
  `gBattleMons[battler].item` through the pinned ABI probe (`HNS_BATTLE_POKEMON_ITEM_OFFSET 0x30`,
  width 2). The item word is current battle state and is updated on consume/knock-off/steal/swap/fling.
- **Stored party item vs current battle item.** `CalcParticipantPresenter.resolveEffectiveItem` and
  `CalcRequestBoundary.reconcileParticipantItem` implement: active-slot match → current battle item wins
  (including authoritative `ITEM_NONE` over a stale nonzero party item); bench player → parsed party item;
  opponent slot mismatch, faint window, doubles ambiguity, or unverified read → unreadable, never a fallback.
  Battle context is authoritative, not caller-declared: a supplied runtime observation establishes it, so
  `activeBattle = false` cannot downgrade an observed battler to the party item.
- **Static item capability (`HnsItemRegistry`), decided by numeric ID:**
  - `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` (`ITEM_NONE`, Exp. Share, Soothe Bell, Amulet Coin, Cleanse Tag,
    Lucky Egg): the item's own hold effect adds no item blocker for an item-independent move.
  - `UNSUPPORTED_DAMAGE_RELEVANT` (type boosters, Choice items, species items, Life Orb, Expert Belt,
    gems, resist berries, Focus Sash/Band, Leftovers/Shell Bell/Rocky Helmet): `HNS_ITEM_EFFECT_NOT_MODELLED`.
  - Any other in-domain, unreadable, or out-of-domain identity: `HNS_ITEM_EFFECT_NOT_MODELLED` or
    `HNS_EFFECTIVE_ITEM_UNREADABLE` respectively; an unresolvable manual name is
    `HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE`.
- **Contextual interaction audit (`HnsMoveItemInteractionRegistry`), decided by numeric move ID.**
  Fling, Natural Gift, Acrobatics, Knock Off, Poltergeist, Judgment, Techno Blast and Multi-Attack read
  held-item state in the pinned damage path (§7.2). None is modelled, so each adds
  `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` and is refused before the engine. The final capability is the union
  of the static and interaction audits, so `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` is never trusted context-free.
- **No raw H&S name is forwarded.** Every statically-supported item is ordinary-damage-free, so the
  authorized request omits the item; no H&S source name can silently match an unrelated ADV item. This
  stripping is safe only because every item-dependent move was refused first.
- Verified via Kotlin unit tests (`Hns205ItemCatalogueTest.kt`, `HnsItemRegistryTest.kt`,
  `HnsMoveItemInteractionTest.kt`, `CalcHnsItemTest.kt`), native ABI/reader tests, generator tests
  (`test_generate_hns_items.py`) and the QuickJS host suite (`native/tests/test_js_calc.c:check_gap_c3_items`).

**Why H&S calculations remain refused after Gap C3:** identity and current-vs-stored item state are now
truthful and item capability is conditional, but the generation III badge boost still has no equivalent in
the request shape, so `BADGE_BOOST_NOT_MODELLED` (Gap C4) keeps H&S strictly refused.

### Then, concretely

1. Gap A is CLOSED: authoritative runtime challenge rules are consumed by `CalcRequestBoundary` into
   `CalcHnsRuntimeRules`, selecting category behavior (`PER_MOVE_SPLIT` vs `TYPE_BASED`), clearing
   unreadable limitations when observed, and blocking on unsupported active modes
   (`RANDOM_TYPES_ACTIVE_NOT_MODELLED`, `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`).
2. Gap B is CLOSED: authoritative H&S species and move data are consumed via `overrides`.
3. Gap C1 is CLOSED: exact modern type chart, Fairy toggle ON/OFF, and optionStyle category coupling.
4. Gap C2 is CLOSED: authoritative effective ability input, active-slot matching, and conditional ability support.
5. Gap C3 is CLOSED for an explicit, contextual subset: exact item identity, current battle item
   authority, static item capability, and a per-move item-interaction audit; all damage items and all
   item-dependent moves fail closed precisely.
6. Gap C4a is CLOSED as an audit for an explicit mechanics set: the remaining challenge-settings
   fields are classified, move mechanics are gated from the pinned effect IDs, badge boost is
   precisely blocked, the ordinary physical/special base arithmetic has source/host goldens, and
   non-neutral stat stages plus unobserved mutable live battle state fail closed. H&S is still
   refused by `BADGE_BOOST_NOT_MODELLED`, `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`, **and**
   `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` (§10).
7. Gap C4b is **PARTIAL / OPEN** — a source+host verified arithmetic and reader slice, not ROM-result
   validation and not a closed gap:
   - Dedicated QuickJS calculation engine (`calculateHnsDamage`) implements the H&S 2.0.5 UQ4.12
     roll-first arithmetic sequence with half-down rounding.
   - Exact parity across all 16 damage rolls demonstrated against the native C oracle in `test_js_calc.c`
     (SOURCE + HOST VERIFIED, **not** RUNTIME VERIFIED).
   - SaveBlock1 badge boost flags (bytes `0x1A98` and `0x1A99`) are read live and evaluated via UQ4.12
     `halfDown(4506, stat)`. A manual / out-of-battle request has no authoritative badge applicability,
     so it fails closed with `BADGE_BOOST_NOT_MODELLED` rather than assuming badges off.
   - Live raw battle stats (`0x02..0x0A`) and stat stages (`0x18`) are read live from
     `gBattleMons[battler]` and evaluated with critical-hit drop-ignore rules (out-of-domain stages
     reject fail-closed).
   - The Gen-III two-target reduction uses the runtime `GetMoveTargetCount(ctx)` count: a Doubles
     request without an observed `field.targetCount` fails closed with
     `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED` rather than halving every spread move.
   - Dynamic-move-type causes and selected transient states are observed, but active retypes and
     damage modifiers remain unsupported. The Doubles target count still has no reader, so
     production H&S requests remain fail-closed, and no official H&S 2.0.5 battle result has been
     validated against the new output.
   - Unmodelled mechanics, unsupported abilities/items, out-of-range stages, unmodelled weather, active
     randomizers, and unobserved active battle states remain strictly fail-closed
     (`CalcSupport.UNSUPPORTED`). (§11)

---

## 10. Gap C4a — remaining mechanics, badge audit, move gating and arithmetic parity

This section is the C4a deliverable (issue #9). It does **not** promote H&S: production still
returns `CalcSupport.UNSUPPORTED` with `request == null`.

### 10.1 Remaining challenge-settings inventory (all 17 fields)

`HnsChallengeSettingInventory` (`app/src/main/java/com/dualdex/calculator/HnsChallengeSettingInventory.kt`)
has exactly one row per field the runtime reader exposes. `HnsChallengeSettingInventoryTest` asserts
the row count, that no field is missing, and every row's disposition.

| Field | Source semantics | Damage relevance | Captured downstream? | Extra blocker required? | Reason |
|---|---|---|---|---|---|
| `optionStyle` | 0 per-move, 1 type-based category `[src/battle_util.c:9183]` | selects physical/special stats | no (consumed) | no | consumed as a rule (Gap A) |
| `tx_Mode_Fairy_Types` | Fairy ON/OFF retypes species/moves `[src/pokemon.c:5734]`, `:5788` | typing/effectiveness | no (consumed) | no | consumed as a rule (Gap C1) |
| `tx_Random_Type` | randomizes species type `[src/pokemon.c:5731]` | typing/effectiveness | no (consumed) | `RANDOM_TYPES_ACTIVE_NOT_MODELLED` when ON | observed ON cannot be modelled |
| `tx_Random_TypeEffectiveness` | remaps attacking type `[src/battle_util.c:8533]` | effectiveness | no (consumed) | `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED` when ON | observed ON cannot be modelled |
| `tx_Random_Abilities` | rerolls ability `[src/pokemon.c:5585]` | effective ability | **yes** | no | the effective ability is read from `gBattleMons` (Gap C2) |
| `tx_Random_Moves` | rerolls learned moves `[src/pokemon.c:3954]` | move authority | no | `HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED` when ON | the calculator never consults the party moveset |
| `tx_Challenges_NoEVs` | blocks EV gain `[src/pokemon.c:7808]` | EVs → stats | **yes** | no | the request carries the EV values |
| `tx_Challenges_BaseStatEqualizer` | replaces non-HP base stats with 0/100/255/500 `[src/challenge_menu.c:2359]`, `[src/pokemon.c:3750]` | every battle stat | **no** | `HNS_BASE_STAT_EQUALIZER_NOT_MODELLED` when nonzero | the request carries ordinary pinned base stats |
| `tx_Challenges_Mirror` | copies enemy party over the player's `[src/battle_main.c:667]` | party stats/moves | **yes** | no | the copied party is the observed party |
| `tx_Challenges_Mirror_Thief` | Mirror variant `[src/battle_main.c:5823]` | party stats/moves | **yes** | no | same as Mirror |
| `tx_Challenges_TrainerScalingIVs` | rewrites opponent IVs `[src/battle_main.c:2145]` | opponent stats | **yes** | no | rewritten IVs are the observed IVs |
| `tx_Challenges_TrainerScalingEVs` | rewrites opponent EVs `[src/battle_main.c:2156]` | opponent stats | **yes** | no | rewritten EVs are the observed EVs |
| `tx_Challenges_MaxPartyIVs` | forces player IVs to 31 `[src/pokemon.c:3232]` | player stats | **yes** | no | forced IVs are the observed IVs |
| `tx_Mode_Sturdy` | gates Sturdy endure-at-1-HP `[src/battle_util.c:8186]` | KO behaviour only | n/a | no | only reachable through Sturdy, which is not in the supported ability set |
| `tx_Challenges_LevelCap` | caps reachable level `[src/caps.c:64]` | stats | **yes** | no | the request carries the level |
| `tx_Challenges_ExpMultiplier` | scales EXP `[src/caps.c:64]` | stats | **yes** | no | the request carries the level |
| `tx_Mode_Legendary_Abilities` | substitutes slot-0 abilities `[src/pokemon.c:5551]` | effective ability | **yes** | no | the effective ability is read from `gBattleMons` (Gap C2) |

Unobserved `baseStatEqualizerMode` / `randomMovesEnabled` fail closed with
`CHALLENGE_SETTINGS_UNREADABLE`; no source default is substituted.

### 10.2 Badge boost — source, authority and why it stays blocked

Pinned source: `B_BADGE_BOOST = GEN_3` `[include/config/battle.h:30]`;
`GetBadgeBoostModifier()` returns `UQ_4_12(1.1)` `[src/battle_util.c:9135]`;
`ApplyOffensiveBadgeBoost` / `ApplyDefensiveBadgeBoost` compose it into the attack/defence modifier
`[src/battle_util.c:6894]`, `:6903`; eligibility is `ShouldGetStatBadgeBoost` `[src/battle_util.c:9143]`
(badge flag set, player side, and not link / e-Reader / recorded-link / Frontier / secret-base
trainer battle).

Insertion point (SOURCE VERIFIED): stat stages are applied to the raw stat first, then the ability/
item modifiers, then the badge is composed in UQ4.12 (`uq4_12_multiply_half_down`), and the combined
modifier is applied to the stat once (`uq4_12_multiply_by_int_half_down`) `[src/battle_util.c:7189]`,
`:7392]`. It is **not** `finalDamage × 1.1` and **not** `floor(stat × 1.1)`.

Authority: the four flags resolve to `FLAG_BADGE01_GET`, `FLAG_BADGE06_GET` (H&S; `FLAG_BADGE05_GET`
elsewhere), `FLAG_BADGE07_GET` and `FLAG_BADGE07_GET` (SpA/SpD share one flag) and are read through
`FlagGet` from SaveBlock1 `[include/constants/flags.h:1363]`, `:1368]`, `:1369]`. At the C4a audit
DualDex did not read that flag storage, so caller-supplied badge state could not be proven and the
calculator did not expose a badge field. **C4a therefore kept `BADGE_BOOST_NOT_MODELLED` rather than
modelling a value it could not authoritatively observe.** C4b later added the live SaveBlock1 badge
reader (§11.1), but a manual / out-of-battle request still has no hypothetical battle context in which
to apply it, so `BADGE_BOOST_NOT_MODELLED` continues to keep those requests fail-closed rather than
assuming badges off.

### 10.3 Move-mechanics capability

`tools/hns-move-mechanics/generate_hns_move_effects.py` parses the pinned `enum Move` and the raw
`.effect` / `.multiHit` / `.strikeCount` / `.explosion` / state-flag initializers of
`src/data/moves_info.h` into `Hns205MoveEffects.kt` (928 resolved effects, 353 ordinary, 6 unresolved).
`HnsMoveMechanicsRegistry` classifies by the pack's numeric move ID:

| Category | Meaning | Blocker |
|---|---|---|
| `ORDINARY_PROVEN_EQUIVALENT` | `EFFECT_HIT`, no multi-hit/explosion/always-crit/state flag; a literal source-backed target-ability bypass flag is delegated to Group C | none |
| `UNSUPPORTED_STATE_DEPENDENT` | reads HP/friendship/weight/speed/consecutive-use/target state, or has a conditional/computed target-ability bypass | `HNS_MOVE_MECHANICS_NOT_MODELLED` |
| `UNSUPPORTED_FORMULA_DIFFERENT` | fixed damage, OHKO, level/percent, defence selection, per-hit sequence | `HNS_MOVE_MECHANICS_NOT_MODELLED` |
| `ITEM_DEPENDENT_HANDLED_ELSEWHERE` | C3 item-interaction audit owns it | `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` (C3) |
| `UNCLASSIFIED` | effect missing/conditional/computed or unknown ID | `HNS_MOVE_MECHANICS_NOT_MODELLED` |

Representative blocked families: Return/Hidden Power/Low Kick (effect), multi-hit (`multiHit` /
`strikeCount > 1`, including Bullet Seed and Double Kick, which hide behind `EFFECT_HIT`), Explosion/
Self-Destruct (H&S keeps `B_EXPLOSION_DEFENSE` at `GEN_LATEST` while ADV halves Defence), Sacred
Sword/Chip Away (`ignoresTargetDefenseEvasionStages`), conditional/computed target-ability bypasses,
fixed damage/OHKO/Endeavor/Final Gambit, and the unresolved Low Kick/Struggle conditionals. Literal
source-backed `ignoresTargetAbility` moves such as Sunsteel Strike and Moongeist Beam clear this
gate and delegate their bypass to Group C, where Ability Shield preserves the defender ability. A
simple `EFFECT_HIT` move such as Tackle also clears the gate; the C3 item-dependent moves keep their
C3 blocker and are not double-reported.

### 10.4 Ordinary-damage arithmetic parity audit

The independent oracle is in `native/tests/test_js_calc.c` (`check_gap_c4a_arithmetic_parity`),
transcribed from the pinned source rather than calling the engine twice. Ordering:

| Stage | Pinned H&S | `@smogon/calc` 0.11.0 ADV |
|---|---|---|
| base | `bp·Atk·(2L/5+2)/Def/50 + 2` | `bp·Atk·(2L/5+2)/Def/50`, `+2` later |
| spread → weather → crit | applied to the value **including** `+2` | burn/screens/spread/weather applied **before** `+2`, crit after |
| random roll | **before** STAB/type/burn/screens | **after** STAB/type |
| STAB / type / burn / screens | after the roll, UQ4.12 half-down | before the roll, per-type floor |

**Finding:** the bare base path matches exactly (`gap_c4a_parity_neutral_base_matches`: Machamp Rock
Slide vs Snorlax is 51–60 on both). The positive proof now also covers the **neutral special** stat
pair (`gap_c4a_parity_neutral_special_matches`: Alakazam Thunderbolt vs Snorlax is 43–51 on both,
SpA 155 / SpD 130). STAB/type and crit+STAB/burn cases differ by 1 or more
(`gap_c4a_divergence_stab_detected`, `gap_c4a_divergence_crit_stab_detected`): e.g. Karate Chop vs
Snorlax is `102,103,…,120` in ADV but `102,102,102,104,…,120` in H&S. Therefore any request that
exercises a non-identity modifier adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` via
`CalcCapabilityPolicy.hnsModifierOrderDiverges` (crit, weather, doubles, screens, burn, STAB, or
type effectiveness ≠ 1).

**Stat stages (R2).** Non-neutral stat stages are **blocked** by the same gate. H&S applies stages
before its fixed-point ability/item composition while the ADV host applies ability modifiers before
stages, and the staged-stat rounding has not been independently proven for H&S, so the only positive
parity cases are neutral-stage physical and special requests. Any Atk/Def/SpA/SpD/Spe stage on
either participant adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`; C4b must prove the staged-stat
arithmetic before a staged request may clear. The policy change is covered by
`non-neutral stat stages are refused by the modifier-order gate`,
`neutral stat stages clear the modifier-order gate`, and the native oracle's comment explaining why
no positive stage fixture is fabricated.

**Random damage roll:** both engines use the same 16 endpoints `85..100` and the same
`floor(damage · r / 100)` shape, but at a different point in the chain, which is exactly why the
non-neutral cases diverge.

### 10.5 Live battle state (R1; historical pre-C4e snapshot)

`hnsModifierOrderDiverges` evaluates the *static* species/move operands. H&S instead consumes
battle-mutated operands that the request shape does not carry, so C4a adds
`HNS_LIVE_BATTLE_STATE_NOT_MODELLED`:

The treatment column records the earlier C4a state. The raw-stat reader was added in C4b; current
runtime behavior is described in §14.9.

| State class | Pinned H&S mutation | C4a treatment |
|---|---|---|
| Current effective battler types | `GetBattlerTypes()` reads `gBattleMons[battler].types`; `SET_BATTLER_TYPE` (Soak) and Roost/Tera layer over the raw bytes | An exact-trusted, slot-matched, in-domain live type observation is bound by `CalcRequestBoundary` and used by the modifier detector. A set that differs from the static record, a third non-empty type, a typeless/out-of-domain value, an unobserved battler, or a slot mismatch blocks |
| Raw battle stat words | Power Trick swaps `gBattleMons[attacker].attack` and `.defense` directly, with unchanged stages and effect ID | Unobserved (no runtime reader yet): blocks. C4b owns an authoritative stat-word observation |
| Dynamic move type | `SetTypeBeforeUsingMove()` forces an otherwise ordinary move to Electric under Ion Deluge / Electrify without changing the static effect ID | Unobserved (no runtime reader yet): blocks. C4b owns it |
| Other transient damage state | Volatiles/screens/weather reachable by the supported subset that are not represented by the request fields | Unobserved: blocks. C4b owns it |

The gate fires only when the request is an **active battle**: `CalcRequestBoundary` marks it from an
explicit `activeBattle` hint or from any supplied runtime observation, and binds the current types
for a slot-matched OBSERVED battler. A manual hypothetical, or a live read of stored party data, is
not an active battle and is unaffected. `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` is checked per class,
so an observed class that matches the static record does not itself block; today the stat-word,
dynamic-move-type, and transient-state classes have no reader, so every active battle still fails
closed. `CalcCapabilityPolicyTest`/`CalcHnsLiveBattleStateTest` pin the blocked cases (unobserved
active battler, Soak Water vs static Normal — which also makes the detector add
`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` for the live 2×, third non-empty type, slot mismatch,
out-of-domain type) and the policy-level fully-observed positive control.

### 10.6 Evidence vocabulary and the final blockers (historical pre-C4e)

The arithmetic goldens are **SOURCE/HOST VERIFIED** (pinned source transcribed into the native
oracle, executed against the committed bundle). They are **NOT RUNTIME VERIFIED**: no official ROM
battle has produced them.

At the time of the C4b slice, the remaining production blockers were `BADGE_BOOST_NOT_MODELLED`,
`HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`, `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`, and
`HNS_LIVE_BATTLE_STATE_NOT_MODELLED` (plus the C2/C3/type/setting blockers where applicable). The
C4b slice has since bound the badge reader and the arithmetic, but Gap C4b remains **PARTIAL / OPEN**
until:

1. the boundary can bind authoritative badge applicability for manual / out-of-battle requests (the
   reader exists, but there is no hypothetical battle context to bind it to), so `BADGE_BOOST_NOT_MODELLED`
   may clear without silently assuming badges off;
2. the runtime `GetMoveTargetCount(ctx)` operand is represented, so `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`
   may clear without guessing the spread modifier from the move class;
3. the H&S arithmetic is validated against the running official 2.0.5 ROM (source/host oracle parity is
   not ROM-result validation); and
4. authoritative effective battler types, battle stat words, the dynamic move type, and transient
   damage state are consumed and validated (or proven neutral) before
   `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` may clear (§10.5 R1).

That paragraph describes the pre-C4e state and is superseded by §14.9: the supported live Singles
subset now calculates as an estimate, and complete, named ability/item/field relevance can be
neutralized as a caveat. `BUILDS_NOT_HASH_VERIFIED` remains untouched: no ROM hash or trust is
promoted here.

---

## 11. Gap C4b — Partial Arithmetic + Reader Slice (historical pre-C4e; SOURCE + HOST VERIFIED)

This section records the Gap C4b slice (issues #9 and #40), which advanced H&S 2.0.5 calculator support
from source-audited but refused to **source-verified + host-verified calculation** for the supported
ordinary mechanics subset. It is a historical **partial C4b slice, not C4b completion**: the arithmetic, stat
stages, badge reader, and target-count operand are source- and host-verified, but no official H&S
2.0.5 battle result has been validated against the new output (SOURCE/HOST VERIFIED, **not** RUNTIME
VERIFIED). At that historical stage, the live operands (`battleStatWords`, `dynamicMoveType`,
`transientState`, and the runtime target count) had no reader and production requests remained
fail-closed. Gap C4b's arithmetic is still partial; current production outcomes are described in
§14.9.

### 11.1 ABI-backed Battle Mons layout and SaveBlock1 badge state reader

1. **Battle Mons Layout (`gBattleMons[battler]`):**
   - Derived and verified via `tools/hns-layout/generate_hns_battle_pokemon_layout.py`.
   - The probe generates `native/src/hns_battle_pokemon_layout_gen.h` with the following
     **`BattlePokemon` struct member offsets** (relative to the start of the `BattlePokemon` struct):
     - Raw battle stats: `attack` **0x02**, `defense` **0x04**, `speed` **0x06**, `spAttack` **0x08**, `spDefense` **0x0A** (10 bytes total).
     - Stat stages: `statStages[NUM_BATTLE_STATS]` at offset **0x18** (8 bytes: hp, atk, def, speed, spAtk, spDef, acc, evasion).
       Default neutral stage is 6 (`DEFAULT_STAT_STAGE`), valid range 0..12 corresponding to -6..+6.
       Any byte outside 0..12 is rejected as out-of-domain (not coerced).
   - Pinned in `native/src/hns_battle_pokemon_layout_gen.h` and verified with `--verify`.

2. **SaveBlock1 Badge State Reader:**
   - Badge flags are derived from the pinned upstream source (`pokehns-expansion` commit `1f42b74`,
     `include/constants/flags.h` and `include/config/battle.h`):
     - `SYSTEM_FLAGS = 0x860`; `SaveBlock1.flags` starts at offset `0x198C` from sb1 base.
     - `FLAG_BADGE01_GET` (Atk)     = `0x867` → `flags[0x10C]`, byte **`0x1A98`**, bit **7**
     - `FLAG_BADGE03_GET` (Spe)     = `0x869` → `flags[0x10D]`, byte **`0x1A99`**, bit **1**
     - `FLAG_BADGE06_GET` (Def)     = `0x86C` → `flags[0x10D]`, byte **`0x1A99`**, bit **4**
     - `FLAG_BADGE07_GET` (SpA+SpD) = `0x86D` → `flags[0x10D]`, byte **`0x1A99`**, bit **5**
   - The reader (`pokemon_read_hns_badge_state_gba` in `native/src/pokemon_reader.c`) reads **two bytes**
     at `sb1_base + 0x1A98` and `sb1_base + 0x1A99` and extracts the correct bit per flag.
   - Badge boosts apply to the **player battler only** (`ShouldGetStatBadgeBoost` returns FALSE for
     `!IsOnPlayerSide(battler)`). The enemy defender never receives badge boosts.
   - Eligible battle types are gated by `HNS_BATTLE_TYPE_BADGE_EXCLUSIONS`.
   - Populated into `HnsBattlerRuntimeState` and forwarded via JNI (38-element array) and unpacked
     in `HnsBattlerRuntimeState.kt`.

### 11.2 The H&S QuickJS calculation engine (`calculateHnsDamage`)

Rather than relying on `@smogon/calc`'s ADV `calculateADV` (which applies STAB and type before roll and evaluates modifiers in a different order), `tools/calc-bundler/entry.js` implements a dedicated `calculateHnsDamage(gen, attacker, defender, move, field)` pipeline matching upstream pokeemerald-expansion `CalculateBaseDamage` / `DoMoveDamageCalcVars` / `ApplyModifiersAfterDmgRoll`:

1. **Effective Stat Resolution:**
   - Uses `rawStats` if observed, otherwise computes from base stats, IVs, EVs, and nature.
   - Applies stat stages (-6..+6) using upstream `HNS_STAT_STAGE_RATIOS` with crit drop-ignore rules (target defensive boosts ignored, attacker offensive drops ignored).
   - Uses the effective move type resolved by `GetBattleMoveType` as the shared operand for type-sensitive stages.
   - The engine composes Attack-modifier branches (Huge/Pure Power, pinch abilities, Hustle, Guts, and its existing Thick Fat path) and the offensive badge in one UQ4.12 accumulator in `CalcAttackStat` order, then applies once to the integer stage-adjusted Attack stat. Production capability remains narrower: Huge/Pure Power and Thick Fat are still unsupported; the authorized pinch and Hustle/Guts cases require their exact live/category predicates. Guts is physical-only and tests the exact observed `status1 & STATUS1_ANY` word; Hustle is physical-only.
   - Composes Defense-stage modifiers and the defensive badge in their own UQ4.12 accumulator, then applies once to the integer stage-adjusted Defense stat.
   - Composes base-power modifiers in a separate accumulator using pinned `uq4_12_multiply` half-up product composition, then applies the product once to source base power with `uq4_12_multiply_by_int_half_down`. The admitted ability modifiers include #105's Technician, Iron Fist, Strong Jaw, Mega Launcher, Sharpness, attacker Water Bubble, defender Water Bubble, defender Heatproof, Dry Skin, Steelworker, Toxic Boost, and Flare Boost, #106's explicit Normalize/-ate `ateBoost`, Punk Rock's ×1.3 sound boost, and holder Steely Spirit's ×1.5 effective-Steel boost; Liquid Voice has no BP multiplier. Their pinned predicates gate production authorization. The target-ability slot follows attacker ability, field, and attacker-partner slots, then precedes attacker held-item modifiers. Water Bubble's attacker Water ×2 and defender Fire ×0.5 branches remain role-specific. Dry Skin remains the distinct defender Fire ×1.25 control. The final type comes from `HnsMoveAuthority.effectiveType` (§17).
2. **Base Damage:**
   - The named base-damage helper follows the pinned integer order: `floor(floor(floor(bp * attack * (floor(2 * level / 5) + 2)) / defense) / 50) + 2`.
3. **Pre-Roll Modifiers (applied to damage including +2):**
   - Doubles spread reduction: `halfDown(2048, dmg)` **only** when the request carries an explicit
     `field.targetCount === 2` (`GetMoveTargetCount(ctx)`). A Doubles spread move with one present foe
     (`targetCount === 1`) is not reduced, and a Doubles spread move with no target count **fails
     closed** rather than being halved unconditionally.
   - Weather: Rain/Sun `halfDown(6144/2048, dmg)`
   - Critical hit: `halfDown(8192, dmg)`
4. **16-Step Damage Roll:**
   - For `r = 85..100`: `x = Math.floor((dmg * r) / 100)`.
5. **Post-Roll Modifiers (per-roll UQ4.12 half-down):**
   - STAB: `halfDown(6144, x)` (or 8192 for Adaptability)
   - Type effectiveness: `halfDown(Math.round(eff * 4096), x)` using the 19x19 H&S type matrix
   - Burn: `halfDown(2048, x)`
   - `GetOtherModifiers` accumulates its preceding target modifiers and screens, followed by ability and item slots in the source's speed-dependent order described in §17. Defender Punk Rock's ×0.5 sound reduction is inside this product. The product is applied once after STAB, type effectiveness, and burn; those post-roll calls retain their pinned sequential half-down rounding.
6. **Minimum Damage Floor:**
   - `if (x === 0 && eff > 0) x = 1`, after Punk Rock and the other post-roll stages.

### 11.3 Host Parity and Evidence

In `native/tests/test_js_calc.c`:
- `check_gap_c4a_arithmetic_parity`: Karate Chop STAB and Crit STAB now assert 100% exact parity (`rolls_equal(engine, oracle) == 1`) across all 16 rolls.
- `check_gap_c4b_arithmetic_coverage`: Asserts exact parity across all 16 rolls for:
  - Stat stages (+2 Atk, -1 Def)
  - Critical hits ignoring adverse stat stages
  - Offensive and defensive badge boosts (`badgeBoosts: { attack: true, defense: true }`)
  - Weather modifier (Sun boost on Fire move)
  - Screens (Reflect reduction)
  - Explicit raw battle stats (`rawStats`)
  - Doubles target count: `field.targetCount: 2` halves a spread move; `field.targetCount: 1`
    does **not**; a missing count is refused (fail-closed).

### 11.4 Historical snapshot (superseded by C4e): Fail-Closed Policy Boundaries

In `app/src/main/java/com/dualdex/calculator/CalcCapabilityPolicy.kt`:
- `BADGE_BOOST_NOT_MODELLED` is no longer an unconditional `alwaysLimitations` entry, but it still
  blocks: an active battle whose player attacker's badge state is unobserved blocks, and a manual /
  out-of-battle request (no boundary-owned live state) blocks because badge applicability is
  unspecified. Missing badge state is **not** read as "badges off" (`hnsBadgeBoostNotModelled`).
- `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED` blocks any H&S Doubles request whose runtime
  `GetMoveTargetCount(ctx)` is unobserved (which is every Doubles request today), rather than halving
  spread moves from `field.gameType` alone (`hnsDoublesTargetCountNotModelled`).
- `hnsModifierOrderDiverges` returns false for the supported ordinary pipeline (neutral, STAB, type
  effectiveness, crits, burn, screens, rain/sun, and stat stages -6..+6).
- Because the badge and Doubles gates are closed for every request that has no authoritative live
  state, **no production H&S request is promoted to `Ready` / `ESTIMATED` today**. The arithmetic is
  modelled and host-verified; the missing live operands keep C4b **PARTIAL / OPEN**.
- Strict fail-closed gates remain:
  - Unmodelled move mechanics: `HNS_MOVE_MECHANICS_NOT_MODELLED`
  - Unmodelled / divergent abilities: `HNS_ABILITY_EFFECT_NOT_MODELLED`
  - Damage-relevant items: `HNS_ITEM_EFFECT_NOT_MODELLED`
  - Item-dependent moves: `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED`
  - Unmodelled weather (Hail, Snow, Sand, Fog): `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`
  - Out-of-range stat stages: `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`
  - Active randomizers: `RANDOM_TYPES_ACTIVE_NOT_MODELLED`, `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`
  - Active battles with unobserved battler state: `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`
  - Manual / unspecified badge applicability: `BADGE_BOOST_NOT_MODELLED`
  - Doubles without an observed target count: `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`

---

## 12. Gap C4c — Runtime Validation + Remaining Live Operand Authority (historical; superseded by C4e)

This section documents the Gap C4c slice (issues #9 and #40), advancing H&S 2.0.5 calculator support
toward **runtime verification** against the official H&S 2.0.5 ROM. C4c is a **foundation slice**:
the authority *plumbing* for the remaining live operands is in place (target-count computation,
readability-carrying JNI tuple, fail-closed gates), but no production H&S request can reach
`Ready` / `ESTIMATED` yet. Runtime golden validation against the official ROM is explicitly
deferred to the next slice.

### 12.1 Target Count Authority (`GetMoveTargetCount`) — BLOCKED in production

**C4b status:** The runtime `GetMoveTargetCount(ctx)` operand had no reader. Every H&S Doubles spread
move failed closed with `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`.

**C4c resolution (foundation):** The target count is now *computable* from authoritative battle
state:

1. **`gAbsentBattlerFlags`** (EWRAM 0x0200030A): marks battlers that are absent (fainted/forced-out).
   Read by `pokemon_read_battle_lifecycle`, carried through the JNI tuple with an explicit
   readability bit (slot 39) so "the read produced 0" is never conflated with "the read never
   happened".
2. **`gBattlersCount`** (EWRAM 0x020000B0): the observed topological count (2 singles / 4 doubles).
   Carried through the JNI tuple (slots 40–41) with its own readability bit. It is **battle-level
   state carried with the observation, never inferred from `field.gameType`** — topology inference
   from the request shape is exactly what the C4c authority rule forbids.
3. **Attacker/defender battler indices**: resolved from `gBattlerPositions` and
   `gBattlerPartyIndexes` by `resolve_single_player_battler` and `pokemon_resolve_active_enemy`.
4. **Move target class**: from `gMovesInfo[move].target` (static move data), extracted by
   `generate_hns_move_effects.py` into `Hns205MoveEffects.targetClassByMoveId` as the internal
   `SpreadTargetClass` values — the EXACT values of the pinned `enum MoveTarget`
   (`TARGET_BOTH=6`, `TARGET_FOES_AND_ALLY=11`, `TARGET_OPPONENTS_FIELD=13`, ...). They are not a
   renumbered enum and must never be.

The computation follows the upstream `GetMoveTargetCount` logic for the spread classes:
- `TARGET_BOTH` (6): `!(absent & (1<<def)) + !(absent & (1<<partner(def)))`
- `TARGET_FOES_AND_ALLY` (11): adds `!(absent & (1<<partner(atk)))`
- `TARGET_OPPONENTS_FIELD` (13): always 1
- **every other class fails closed** — `TARGET_SELECTED` (1), `TARGET_RANDOM` (5),
  `TARGET_USER` (7) and friends dispatch to `IsBattlerAlive(...)` upstream, which requires
  per-battler HP state the pure function and the boundary do not read. Native
  `pokemon_compute_hns_target_count()` returns 0 for them and the Kotlin boundary returns
  null; neither fabricates a "1". This is the single agreed rule, pinned by tests on both sides.

**Implementation:**
- Native: `pokemon_compute_hns_target_count()` in `native/src/pokemon_reader.c` (fail-closed for
  all non-spread classes).
- JNI: `BATTLER_RUNTIME_STATE_TUPLE_LEN == 42`; slots 38–41 carry `absentBattlerFlags`,
  `absentFlagsReadable`, `battlersCount`, `battlersCountReadable`.
- Kotlin: `HnsBattlerRuntimeState.fromNativeArray` decodes the new fields; the decoder refuses
  a count when its readability bit is clear.
- Boundary: `CalcRequestBoundary.authoritativeMoveTargetCount()` computes the count from the
  two battle-level observations and requires **both** to be OBSERVED, both to have read the
  absent flags AND the battler count, the two observations to **agree** on both battle-level
  words, and the agreed observed count to be exactly **4** (the observed Doubles value).

**Anti-spoofing:** The boundary overwrites `moveTargetCount` from the exact-trusted runtime
observation. A caller-supplied value is stripped. Only the observed `gAbsentBattlerFlags` and
`gBattlersCount` from both battle-level observations can authorize the count; a disagreement
between the two sides, an unreadable word, or an observed count of 2 (singles) fails closed —
the request's `gameType` label never substitutes for the observed state.

**Review round 5:** the same agreed `gBattlersCount` is now also the boundary-owned live battle
**format** for every live calculation, not only the Doubles spread count. It is bound through the
shared `authoritativeObservedBattlersCount()` helper, and a live request whose observed topology is
not the Singles `2` refuses with `HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED` (§14.7.2). This closes the
late-Doubles hole where both per-side observations resolve (one present battler each) while
`gBattlersCount` stays 4 and a caller Singles label would otherwise apply the Singles x0.5 screen
multiplier instead of the engine's x0.667.

**Production status: BLOCKED.** The real boundary's per-side observations are
`BATTLER_RUNTIME_STATE_AMBIGUOUS` whenever two battlers are present on a side — which is
precisely the Doubles shape that has a count to compute — and an AMBIGUOUS observation
publishes no field at all. `authoritativeMoveTargetCount` therefore requires two OBSERVED
single-active-battler observations, which a genuine four-battler doubles battle cannot
produce. The two-target Doubles case cannot get through the real boundary today, so
**production target-count authority is BLOCKED by the Doubles battler observation** and stays
fail-closed: every production H&S Doubles request is refused with
`HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`. The authority path itself is fully tested at the
boundary level with observed four-battler observation pairs (count bound for the count-2 and
count-1 shapes; fail-closed for count disagreement, unreadable count, observed-singles count,
non-spread classes, and AMBIGUOUS side observations). Unblocking requires a battle-level
target-count observation that the native reader publishes from the AMBIGUOUS doubles shape
(deferred, along with the runtime goldens, to the next slice).

### 12.2 Dynamic Move Type — Fails Closed for ALL Moves

**C4b status:** `dynamicMoveTypeObserved` was always false. Every active battle failed closed.

**C4c analysis (corrected in the round-two review):** Pinned
`src/battle_main.c SetTypeBeforeUsingMove()` at 1f42b74d:

```c
if ((gFieldStatuses & STATUS_FIELD_ION_DELUGE && moveType == TYPE_NORMAL)
 || gBattleMons[battler].volatiles.electrified)
    gBattleStruct->dynamicMoveType = TYPE_ELECTRIC | F_DYNAMIC_TYPE_SET;
```

Ion Deluge is Normal-only, but **Electrify affects ANY move type** — there is no type check on
the volatile. A Fighting-type Karate Chop against an Electrified attacker becomes Electric.
The earlier claim that non-Normal moves are immune to Electrify was **withdrawn**.

**C4c resolution:** `CalcRequestBoundary.authoritativeDynamicMoveTypeObserved()` **always
returns false** — the gate fails closed for **ALL** moves until a runtime volatile reader
(`gBattleMons[battler].volatiles.electrified` + `gFieldStatuses`) exists. No move type, static
or live, may claim provable immunity.

### 12.3 Transient State — Fails Closed for ALL Moves

**C4b status:** `transientStateObserved` was always false. Every active battle failed closed.

**C4c analysis (corrected in the round-two review):** Pinned
`src/battle_util.c DoMoveDamageCalcVars()` at 1f42b74d:

```c
DAMAGE_APPLY_MODIFIER(GetGlaiveRushModifier(ctx->battlerDef));
```

where `GetGlaiveRushModifier` returns ×2 when the defender has the Glaive Rush volatile —
**for ANY incoming move type**. The earlier claim that transient state is irrelevant for
non-Normal `EFFECT_HIT` moves was **withdrawn**.

Other transient state the request shape cannot express: Minimize (×2), Underground/Airborne
(×2), and the defense-side volatile bytes generally. None are observable from Kotlin yet.

**C4c resolution:** `CalcRequestBoundary.authoritativeTransientStateObserved()` **always
returns false** — the gate fails closed for **ALL** moves until the relevant defense volatiles
are read at runtime.

### 12.4 Transient State Classification

| State class | Relevance to ordinary EFFECT_HIT | C4c disposition |
|---|---|---|
| Current effective types | Soak changes typing | **Handled**: authoritative types observation matches static record or blocks |
| Raw battle stat words | Power Trick swaps Atk/Def | **Handled**: authoritative raw stats observation |
| Dynamic move type | Ion Deluge / Electrify | **FAILS CLOSED FOR ALL MOVES**: Electrify is type-agnostic (round-two correction) |
| Glaive Rush / Minimize / semi-invulnerable | ×2 damage on ANY incoming move | **FAILS CLOSED FOR ALL MOVES**: defense volatiles are type-agnostic and unreadable (round-two correction) |
| Weather | Rain/Sun multiplier | **Handled**: `request.field.weather` carries the value |
| Screens | Reflect / Light Screen | **Handled**: `request.field.defenderSide` carries the value |
| Critical hit | ×2 multiplier | **Handled**: `request.move.isCrit` carries the value |
| Burn | ×0.5 physical | **Handled**: `request.attacker.status` carries the value |

### 12.5 Production Promotion Status

> **Historical (Gap C4c, pre-C4e).** The paragraph below records the state *at C4c*. It was
> superseded by Gap C4d (official-ROM goldens) and then by **Gap C4e**, which opens a narrow
> production `Ready` / `ESTIMATED` path for the exact-trusted live Singles ordinary subset defined
> in §14. Read "today" below as "at C4c".

**No production H&S request reaches `Ready` or `ESTIMATED` at C4c.** The fail-closed dynamic-move-
type and transient-state gates (12.2 / 12.3) apply to every move in every active battle, and
Doubles spread moves are additionally blocked by the target-count authority (12.1) because the
real boundary cannot observe the four-battler doubles shape. Even a fully observed Singles
battle cannot clear `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`, because the volatile readers do not
exist. The authority plumbing is in place and unit-tested at the boundary level, but the
production operand path is BLOCKED, and C4c therefore remains **PARTIAL / OPEN** as a
foundation slice.

Specifically BLOCKED, with the blocking cause named:
- **All moves, active battle:** dynamic move type gate (Electrify volatile unreadable) and
  transient state gate (Glaive Rush et al. unreadable) — fail-closed by policy.
- **Doubles spread moves:** target-count authority — the native battle contract degrades to
  AMBIGUOUS for two-battler sides, so the two OBSERVED single-active-battler observations the
  boundary requires cannot be produced in a genuine doubles battle (BLOCKED by Doubles
  battler observation; fail-closed).
- **Manual / out-of-battle requests:** badge applicability and live-state gates still apply
  per the boundary-owned live-state contract.

### 12.6 Evidence Status

| Evidence level | What it means | C4c status |
|---|---|---|
| SOURCE VERIFIED | Behavior established from pinned H&S 2.0.5 source | ✅ Target count spread logic, fail-closed class rule, dynamic move type / transient state analysis (round-two corrected) |
| HOST VERIFIED | DualDex/QuickJS behavior matches native oracle | ✅ Target count native computation (fail-closed rule pinned), arithmetic parity |
| RUNTIME VERIFIED | Compared with official H&S 2.0.5 ROM behavior | ❌ Not yet complete — the runtime golden matrix is explicitly OUT OF SCOPE for this pass and deferred to the next slice |

---

## 13. Gap C4d — official-ROM damage goldens + full live-dependency audit (historical; superseded by C4e)

This section records the Gap C4d slice (issues #9 and #40). C4d does the part C4c deliberately did
not: it produces **official H&S 2.0.5 ROM damage goldens** and re-audits the complete live damage
dependency chain. It does **not** open any production `Ready` request; the production blockers named
in §13.8 remain, so C4 remains **PARTIAL / OPEN**.

### 13.0 Baseline (post-#67)

* Starting `main`: `4fd4d33cf970174f9fda3814a720893bf7935f85` (merge of PR #67, which includes
  `aa2b709`, `54e16c7`, `340c0b9`).
* `./ci.sh all` green: native reader `87 passed`, pure tracker selftests `75 passed`, QuickJS
  calculator `1987 passed, 0 failed`, Gradle unit tests green, `assembleDebug` green.
  (`./ci.sh` itself leaves `GRADLE_USER_HOME` to the caller; the run used a writable workspace
  Gradle cache. This is an environment detail, not a repository change.)
* `git diff --check` clean.
* Post-#67 state verified: `authoritativeDynamicMoveTypeObserved()` and
  `authoritativeTransientStateObserved()` both return false; production Doubles target count is
  blocked by the `AMBIGUOUS` battler-observation contract; a caller-supplied `hnsLiveBattleState`
  is rebound by `CalcRequestBoundary`; no production H&S request reaches `Ready / ESTIMATED`; no
  official-ROM golden was claimed.
* Active H&S profile trust (`app/src/main/assets/profiles/heart_and_soul.json`): `sha256Hashes: []`,
  `isVerified: true`, `memoryLayoutVerified: true`, `battleUiVerified: false`,
  `interactiveControlsVerified: false`.
* Issues #9 and #40 remain **open**.

### 13.1 Evidence vocabulary

* **SOURCE VERIFIED** — established from pinned H&S 2.0.5 source
  (`1f42b74dff0e9fe942419845d040663dd829a973`).
* **HOST VERIFIED** — DualDex calculator agrees with the independent host/native oracle.
* **RUNTIME VERIFIED** — observed on the exact official H&S 2.0.5 release ROM.
* **PRODUCTION AUTHORIZED** — all operands for the exact request are boundary-owned and proven
  enough to expose an executable request. The historical post-C4d state is superseded by the C4e
  authorization in §14; the #91 move-type extension is documented in §17.

### 13.2 Full dynamic-move-type path audit

This mechanism audit began in C4c. The table below now includes the current #91 disposition for
Normalize, the four -ate abilities, Liquid Voice, Ion Deluge and Electrify; §17 gives their exact
predicates and source evidence.

`SetTypeBeforeUsingMove(move, battler)` (`src/battle_main.c:6418`) is the only writer of
`gBattleStruct->dynamicMoveType`. It:

1. clears `dynamicMoveType`, `battlerState[battler].ateBoost` and `gemBoost`;
2. calls `GetDynamicMoveType(GetBattlerMon(battler), move, battler, MON_IN_BATTLE)` and, when that
   is not `TYPE_NONE`, stores `moveType | F_DYNAMIC_TYPE_SET`;
3. independently forces `TYPE_ELECTRIC` when
   `(gFieldStatuses & STATUS_FIELD_ION_DELUGE && GetBattleMoveType(move) == TYPE_NORMAL) ||
   gBattleMons[battler].volatiles.electrified` (`src/battle_main.c:6436-6439`);
4. arms the gem boost (`src/battle_main.c:6443-6449`).

`GetBattleMoveType(move)` (`src/battle_util.c:9907`) returns the stored dynamic type in battle and
otherwise the static `GetMoveType(move)`. So a dynamic override is active exactly when one of the
two writers above fired.

`GetDynamicMoveType` (`src/battle_main.c:6176`) can return non-`TYPE_NONE` only through the
following mechanisms. Each is dispositioned for the already-supported ordinary `EFFECT_HIT` subset:

| Mechanism (`GetDynamicMoveType`) | Trigger | Supported-subset disposition |
|---|---|---|
| `EFFECT_STRUGGLE` | Struggle | Non-`EFFECT_HIT`; the move-mechanics allow-list refuses it. |
| `EFFECT_WEATHER_BALL`, `EFFECT_HIDDEN_POWER` | weather / IVs | Non-`EFFECT_HIT`; refused by the move allow-list. |
| `EFFECT_CHANGE_TYPE_ON_ITEM` (Techno Blast/Judgment/etc.) | held item | Non-`EFFECT_HIT`; refused. |
| `EFFECT_REVELATION_DANCE` | user type / Tera / Roost | Non-`EFFECT_HIT`; refused. |
| `EFFECT_RAGING_BULL`, `EFFECT_IVY_CUDGEL` | species form | Non-`EFFECT_HIT`; refused. |
| `EFFECT_NATURAL_GIFT`, `EFFECT_TERRAIN_PULSE`, `EFFECT_NATURE_POWER` | berry / terrain / map | Non-`EFFECT_HIT`; refused. |
| `EFFECT_TERA_BLAST`, `EFFECT_TERA_STARSTORM` | Tera gimmick / species | Non-`EFFECT_HIT`; refused. |
| `ABILITY_LIQUID_VOICE` (sound moves) | attacker ability | #91 models Water rewrite only for source-proven sound moves; unknown sound metadata stays fail-closed. |
| `EFFECT_AURA_WHEEL` + Morpeko-Hangry | species + ability | Non-`EFFECT_HIT`; refused. |
| ate-type abilities (`Pixilate`, `Refrigerate`, `Aerilate`, `Galvanize` via `TrySetAteType`, `src/battle_main.c:6128`) | attacker ability | #91 models Normal source type → pinned target type and carries the explicit `ateBoost` result. |
| `ABILITY_NORMALIZE` | attacker ability | #91 models the supported ordinary path as Normal with explicit `ateBoost`, subject to the pinned exclusions. |
| **Ion Deluge (`gFieldStatuses & STATUS_FIELD_ION_DELUGE`)** | field status | **Read from boundary-owned decoded field status; rewrites pre-field Normal to Electric. Unknown or unsupported field bits remain fail-closed.** |
| **`gBattleMons[battler].volatiles.electrified`** (Electrify) | attacker volatile | **Read from the boundary-owned live volatile and rewrites any pre-field type to Electric. Missing state remains fail-closed.** |
| Tera/Dynamax/Z gimmick | `GetActiveGimmick` | Gimmick state is not carried by the request and not read → FAIL CLOSED. |

At C4c, dynamic move type was deliberately refused because its operands were unavailable. C4e added
boundary-owned field and volatile authority; issue #91 then added the six supported ability
rewrites. The remaining dynamic-type gate is the active gimmick: any non-none or unavailable
attacker gimmick still refuses the request. The request-local `HnsMoveAuthority` resolves the
supported ability and later field stages once, and unknown operands still fail closed.

`gBattleStruct->dynamicMoveType` itself is **not** used as an authority: it survives from the last
executed move (`SetTypeBeforeUsingMove` is what rewrites it), so it describes a past action, not the
hypothetical request on screen.

### 13.3 Ordinary-damage transient-state classification

`DoMoveDamageCalcVars` (`src/battle_util.c:7754`) and `ApplyModifiersAfterDmgRoll`
(`src/battle_util.c:7794`) reach the following modifiers. Each is dispositioned for the supported
ordinary `EFFECT_HIT` subset:

| Modifier | Pinned location | Disposition |
|---|---|---|
| `GetTargetDamageModifier` (spread reduction) | `battle_util.c:7403` | Depends on `GetMoveTargetCount(ctx)`; the target-count authority exists but production Doubles is blocked (§13.6). Singles is always 1.0. The live format itself is now boundary-owned from `gBattlersCount` (§14.7.2), so a Doubles battle mislabelled Singles refuses with `HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED`. |
| `GetParentalBondModifier` | `battle_util.c:7415` | Only reachable via the Parental Bond ability (unclassified); ability gate refuses it. |
| `GetWeatherDamageModifier` | `battle_util.c:7434` | Rain/Sun carried by `request.field.weather`; any other weather is refused by `hnsModifierOrderDiverges`. |
| `GetCriticalModifier` | `battle_util.c:7474` | Carried by `request.move.isCrit`; **runtime observed (indirect)** (golden E: the crit bit was not read directly and the faint caps the exact roll). |
| `GetGlaiveRushModifier` | `battle_util.c:7481` | Defender `volatiles.glaiveRush`, type-agnostic ×2. Unread → **FAIL CLOSED**. |
| `GetSameTypeAttackBonusModifier` | `battle_util.c:7422` | Uses exact live attacker types and `HnsMoveAuthority.effectiveType`; Adaptability is ×2.0 STAB when they match. |
| `ctx->typeEffectivenessModifier` | type chart | Handled by the exact H&S chart (Gap C1); **runtime validated** (golden B). |
| `GetBurnOrFrostBiteModifier` | `battle_util.c:7458` | `brn` is carried by `request.attacker.status`; frostbite is not in `MODELLED_STATUSES` and is refused. |
| `GetZMaxMoveAgainstProtectionModifier` | `battle_util.c:7488` | Only Z/Max moves; not ordinary `EFFECT_HIT`; gimmick unread. |
| `GetMinimizeModifier` | `battle_util.c:7499` | Gated by `MoveIncreasesPowerToMinimizedTargets(move)`; the defender volatile is unread → **FAIL CLOSED**. |
| `GetUndergroundModifier` / `GetDiveModifier` / `GetAirborneModifier` | `battle_util.c:7506-7525` | Gated by move flags; defender `volatiles.semiInvulnerable` unread → **FAIL CLOSED**. |
| `GetScreensModifier` | `battle_util.c:7527` | `IsDoubleBattle()` selects `UQ_4_12(0.667)` (Doubles) vs `UQ_4_12(0.5)` (Singles). Carried by `request.field.defenderSide` only after the live format gate confirms the observed Singles topology (§14.7.2); a Doubles battle refuses rather than applying the Singles multiplier. **Runtime not separately validated.** |
| `GetCollisionCourseElectroDriftModifier` | `battle_util.c:7551` | Only `EFFECT_COLLISION_COURSE`; refused. |
| `GetAttackerAbilitiesModifier` (`Neuroforce`/`Sniper`/`Tinted Lens`) | `battle_util.c:7558` | Modeled in the attacker final slot using exact effectiveness or the selected critical flag. |
| `GetDefenderAbilitiesModifier` (`Multiscale`, `Shadow Shield`, `Filter`, `Solid Rock`, `Prism Armor`, `Punk Rock`, `Ice Scales`, `Fluffy`) | `battle_util.c:7580` | Low-state predicates are calculated in the defender final slot, including Fluffy's exact effective-type/contact matrix. Mold Breaker follows pinned ability breakability metadata. |
| `GetDefenderPartnerAbilitiesModifier` (`Friend Guard`) | `battle_util.c:7640` | Doubles-only; format blocked and ability unclassified. |
| `GetAttackerItemsModifier` (`Metronome`, `Expert Belt`, `Life Orb`) | `battle_util.c:7656` | A known relevant item may be neutralized as a named estimate caveat; unknown item identity or relevance remains hard. |
| `GetDefenderItemsModifier` (resist berries) | `battle_util.c:7682` | A known relevant item may be neutralized as a named estimate caveat; unknown item identity or relevance remains hard. |
| `CalcMoveBasePowerAfterModifiers` state/power effects (`Facade`, `Brine`, …) | `battle_util.c:6573` | Slice 7 admits only Brine through its separate Singles category, valid authoritative defender HP pair and exact floor-half Q12 move-effect slot. Other state/power effects retain their existing per-family gates. |
| `CalcAttackStat` / `CalcDefenseStat` (stages, raw words, Power Trick, badge, ability stat mods) | `battle_util.c:6912`, `7211` | Stat stages and raw battle stat words are observed and **runtime validated** (golden C); badge state is player-side-only; pinch abilities, Hustle, and Guts use the accumulated Attack-stage pipeline under exact live/category gates; Huge/Pure Power and Thick Fat remain unsupported for production. |
| `GetActiveGimmick` / Tera multiplier | `battle_terastal.c:134` | Not carried and not read → **FAIL CLOSED**. |
| Pledge state (`gBattleStruct->pledgeMove`) | `battle_util.c:7426` | Pledge moves are non-ordinary; refused. |

**Conclusion (current).** C4e observes enough state to authorize the narrow ordinary Singles subset
documented in §14.9. The remaining relevant live-state classes here are dynamic move type (Ion
Deluge / Electrify), defense-side volatiles (Glaive Rush, Minimize, semi-invulnerable), and active
gimmick/Tera state. Those contexts remain refused; this is no longer a blanket refusal for every
active battle.

### 13.4 Official-ROM goldens

The goldens were produced with `tools/hns-runtime-probe` on the official H&S 2.0.5 release ROM,
using only ordinary controller input. The full records, operands and reproduction steps are in
`tools/hns-runtime-probe/evidence/` (`rom-damage-goldens.json`, `README.md`, and the four probe
logs). Each evidence run prints the SHA-256 of the ROM file it actually loaded
(`[ROM] path=... sha256=... loaded=1`), so the committed logs are bound to the same bytes the JSON
records (`rom.sha256`, and a per-golden `rom_sha256`); that is evidence bookkeeping only and does
not promote the product trust hash (§13.7). The direct goldens are captured by the `golden-grind`
command: it is permissive about the RNG grind but exits 0 only on one machine-asserted
`[GOLDEN-HIT] PASS` hit satisfying the live species, live raw Defense, attacker level and exact
damage, and never on a fainting target. The host half lives in `native/tests/test_js_calc.c`
(`check_gap_c4d_rom_damage_goldens`) and runs in `./ci.sh test`.

| Golden | Move / setup | Observed ROM damage | DualDex rolls | Result |
|---|---|---|---|---|
| A — neutral ordinary | Chikorita L5 Tackle vs Pidgey L3 (Def 7) | 6 (15→9) | 5..7 | RUNTIME VERIFIED (direct roll golden) |
| B — STAB + type resistance | Chikorita L6 Razor Leaf vs Pidgey L3 (Def 7, Normal/Flying) | 6 (15→9) | 6..7 | RUNTIME VERIFIED (direct roll golden) |
| C — live non-neutral stat stage | Totodile L5 Leer (Def −1) then Scratch vs Pidgey L2 (Def 6) | 10 (13→3) | 9..11 | RUNTIME VERIFIED (direct roll golden) |
| E — critical hit (indirect) | Chikorita L5 Tackle, critical | ≥10 (10→0) | non-crit max 7; crit 11..14 | RUNTIME OBSERVED (indirect; the faint caps the exact roll). **Not** one of the A/B/C direct roll goldens |
| D — badge boost | — | — | — | NOT VALIDATED: no badge is reachable from the fresh-starter progression used here. Retained as an explicit blocker; not overclaimed. |
| F — new volatile reader | — | — | — | NOT CLAIMED: no new reader was added, so no positive runtime fixture exists. |
| G — Doubles | — | — | — | DEFERRED: production Doubles remains blocked (§13.6). |

The host test does not merely check membership: it recomputes the roll vector with an independent
oracle and asserts the engine matches it exactly, then asserts the observed ROM damage is one of
those rolls. For golden C it additionally asserts that the stage-0 rolls (6..8) **exclude** the
observed 10, so a regression that ignored the live stage fails.

### 13.5 Live volatile authority

C4d does **not** add a reader for the electrified/Ion-Deluge or defense-volatile bytes. Per §8/§14 of
the task, a reader whose positive state cannot be produced by a runtime fixture must not be labelled
RUNTIME VERIFIED, and clearing the gate on the strength of a synthetic unit test alone would be
exactly the fail-open pattern C4c corrected. The relevant live classes therefore remain fail-closed
and are documented in §§13.2-13.3. No new JNI tuple fields were added, so the C4c tuple/decode
contract is unchanged.

### 13.6 Doubles

Production Doubles remains **BLOCKED**, unchanged from C4c. C4d adds no battle-level observation, so
the two `OBSERVED` single-active-battler observations that `authoritativeMoveTargetCount` requires
still cannot be produced in a genuine four-battler battle. `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`
continues to refuse every production Doubles request. No artificial two-battler representation was
manufactured.

### 13.7 Exact-ROM trust decision

The exact H&S 2.0.5 ROM SHA-256 **is not added** to the profile. Doing so would set
`RuntimeRomTrust.mayReadLiveMemory == true` and unlock every live-memory feature in the companion,
which is an issue-#40 closure decision and not a calculator slice. It is also unnecessary for this
slice: `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` and the challenge-settings gate already keep production
H&S fail-closed, and the goldens were produced by a developer-only probe that runs outside the trust
model. `battleUiVerified` and `interactiveControlsVerified` are unchanged (`false`).

### 13.8 Historical production subset decision (superseded by §14.9)

**No H&S request reaches `Ready` / `ESTIMATED` after C4d.** The candidate first subset named in the
task (exact-trust Singles `EFFECT_HIT` with observed types/stats/badges/move type and every
transient either observed or impossible) is not yet genuinely satisfied, for three independent
reasons:

1. **Effective move type** — Ion Deluge / Electrify / gimmick state is unread (§13.2).
2. **Transient state** — Glaive Rush / Minimize / semi-invulnerable state is unread (§13.3).
3. **Exact-ROM trust and challenge rules** — the profile has no hash, so the runtime rules resolve
   to null and the challenge toggles read as unreadable; and the observed attackers' own abilities
   (`Overgrow` 65, `Torrent` 67) are classified `UNSUPPORTED_DAMAGE_RELEVANT` because H&S modifies
   the Attack stat where ADV modifies base power.

The first two are the real live-authority blockers; the third means there is no honest subset to
promote yet. C4d is therefore a successful **evidence** slice, not a production-promotion slice.

### 13.9 Evidence status after C4d

| Evidence level | What it means | C4d status |
|---|---|---|
| SOURCE VERIFIED | Established from pinned H&S 2.0.5 source | ✅ Full `SetTypeBeforeUsingMove` / `GetDynamicMoveType` / `DoMoveDamageCalcVars` / `GetOtherModifiers` audit (§§13.2-13.3). |
| HOST VERIFIED | DualDex/QuickJS agrees with the independent oracle | ✅ C4a/C4b/C4c fixtures unchanged; **new** `check_gap_c4d_rom_damage_goldens` asserts engine == oracle for every golden operand. |
| RUNTIME VERIFIED | Observed on the official H&S 2.0.5 release ROM | ✅ Direct roll goldens A (neutral), B (STAB + resistance), C (live stat stage). Golden E is RUNTIME OBSERVED (indirect) only and is **not** counted with the direct roll goldens. |
| PRODUCTION AUTHORIZED | All operands boundary-owned and proven | ❌ None **at C4d** — see §13.8. (Superseded by Gap C4e §14: the exact-trusted live Singles ordinary subset is now conditionally production authorized.) |

---

## 14. Gap C4e — first production-authorized exact H&S 2.0.5 live Singles subset

C4e is the first slice that **opens** a production `Ready` path for H&S. It follows the required
order — source proof, authoritative runtime observation, runtime verification, trust audit, boundary
authorization — and it does **not** weaken any fail-closed gate. The strongest honest claim is:

> Exact H&S 2.0.5 live **Singles** ordinary-damage requests satisfying every condition in §14.9 are
> production-authorized as `Ready` / `CalcSupport.ESTIMATED`. Every request outside that subset still
> fails closed with a precise limitation, and no H&S request is ever presented as `VERIFIED`.

### 14.0 Baseline (post-#68)

* Starting `main`: `9b769cebf8b4c3c76042fc9d126d31786b64859a` (merge of PR #68).
* `./ci.sh all` green after the changes in this slice (see the PR for exact-head Actions).
* Post-#68 state verified before C4e: dynamic move type and transient state were fail-closed; Doubles
  remained blocked; `sha256Hashes` was empty; no H&S request reached `Ready` / `ESTIMATED`.
* The C4d official-ROM evidence SHA is `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b`,
  re-verified from `tools/hns-runtime-probe/evidence/rom-damage-goldens.json` and the raw probe logs.

### 14.1 Source audit

The C4d mechanism-by-mechanism audit (§§13.2-13.3) is reused rather than re-derived. C4e closes the
three classes it left open:

1. **Effective move type.** `SetTypeBeforeUsingMove` (`src/battle_main.c:6418`) has exactly two
   writers that can retype an otherwise-supported ordinary `EFFECT_HIT` move, both independent of the
   move-mechanics allow-list:
   * `gFieldStatuses & STATUS_FIELD_ION_DELUGE && GetBattleMoveType(move) == TYPE_NORMAL` (Normal-only);
   * `gBattleMons[battler].volatiles.electrified` (any type).
   Both are read. C4e originally left `Liquid Voice`, `Normalize` and the ate abilities outside the
   modelled set; issue #91 now models those six ability branches for the proven ordinary subset.
   Other non-`TYPE_NONE` returns still require a non-`EFFECT_HIT` effect (refused by
   `ordinaryMoveIds`) or an unsupported ability/effect and remain fail-closed.
2. **Transient damage state.** Three generic modifiers apply to ordinary `EFFECT_HIT` moves
   independently of the move allow-list and are now all read:
   * `GetGlaiveRushModifier` (`src/battle_util.c:7481`) doubles any incoming move from the
     defender's `volatiles.glaiveRush`;
   * `moveType == TYPE_ELECTRIC && gBattleMons[attacker].volatiles.chargeTimer > 0` doubles the
     move in `CalcDamagePerHit` (`src/battle_util.c:6635`);
   * `ctx->moveType == TYPE_FIRE && gBattleMons[defender].volatiles.tarShot` doubles the move in
     `CalcTypeEffectivenessMultiplierInternal` (`src/battle_util.c:8365`).
   `GetMinimizeModifier`, `GetUndergroundModifier`, `GetDiveModifier` and `GetAirborneModifier` are
   gated by move flags that `tools/hns-move-mechanics` already excludes from `ordinaryMoveIds`
   (`minimizeDoubleDamage`, `damagesUnderground`, `damagesUnderwater`, `damagesAirborne`,
   `damagesAirborneDoubleDamage`), so no reader is added for those.
3. **Field statuses.** The battle-global `gFieldStatuses` word carries far more than Ion Deluge.
   Pinned source proves the other bits change ordinary damage or the defensive stat independent of
   `EFFECT_HIT`: `STATUS_FIELD_WONDER_ROOM` swaps Defense / Sp.Def inside `CalcDefenseStat`, the four
   terrains apply a x1.3 / x0.5 type modifier, `STATUS_FIELD_MUDSPORT` / `STATUS_FIELD_WATERSPORT`
   reduce their type, `STATUS_FIELD_GRAVITY` changes Ground immunity / groundedness, and the
   Trick/Magic Room / Fairy Lock bits gate abilities and items. The original C4e rule admitted only
   `STATUS_FIELD_ION_DELUGE`; §15 supersedes that mask with a source-backed decision for each active
   bit. Ion Deluge keeps its Normal-only rewrite and hard refusal when relevant. Other bits are
   cleared only when their own effect is source-proven irrelevant; relevant unmodelled effects and
   unknown bits still refuse with `HNS_FIELD_STATUS_NOT_MODELLED`.
4. **Weather variants.** Pinned `B_WEATHER_RAIN` (0x7) and `B_WEATHER_SUN` (0x18) are aggregate
   masks that include the Primal variants (`Primordial Sea` / `Desolate Land`). The engine treats
   those specially (Water blocked under extreme sun, Fire blocked under heavy rain), so the modelled
   mask is narrowed to the ordinary bits only (`B_WEATHER_RAIN_NORMAL` 0x1, `B_WEATHER_SUN_NORMAL`
   0x8); a primal bit refuses with `HNS_LIVE_WEATHER_NOT_MODELLED` instead of collapsing onto the
   ordinary name (Gap C4e correction).
5. **Conditional pinch abilities.** `CalcAttackStat` (`src/battle_util.c:7023`) applies x1.5 as an
   **Attack-stat** modifier when `moveType == TYPE_X && hp <= maxHP/3` for `Overgrow` (Grass),
   `Blaze` (Fire), `Torrent` (Water) and `Swarm` (Bug). The modifier is composed with
   `uq4_12_multiply_half_down` after the stat stage, which is exactly where
   `calculateHnsDamage` already applies its ability modifiers.
6. **Gimmick.** `GetActiveGimmick(battler)` (`src/battle_gimmick.c:60`) is
   `gBattleStruct->gimmick.activeGimmick[GetBattlerSide(battler)][gBattlerPartyIndexes[battler]]`.
   The pinned config sets `P_MEGA_EVOLUTIONS FALSE`, `P_PRIMAL_REVERSIONS FALSE`,
   `P_ULTRA_BURST_FORMS FALSE`, `P_GIGANTAMAX_FORMS FALSE` and `B_FLAG_DYNAMAX_BATTLE 0`
   (Dynamax unreachable), but Tera and Z-Moves remain reachable through the Tera Orb / Z-Power Ring.
   Because the state is reachable, it is **observed** rather than declared unreachable.

### 14.2 New ABI members (generated, not hand-maintained)

`tools/hns-layout/generate_hns_live_battle_layout.py` compiles a probe against the pinned headers with
the pinned ARM toolchain and emits `native/src/hns_live_battle_layout_gen.h`. Ordinary members are
`offsetof`/`sizeof` scalars; bitfield members are located by compiling one designated-initializer
object per bit and reading back the set bit (the `semiInvulnerable` probe uses
`SEMI_INVULNERABLE_COUNT` so both the base bit and the width are recovered). Values for the pinned
commit:

| Member | Value |
|---|---|
| `struct BattlePokemon.hp` / `.maxHP` | 42 / 46 |
| `struct BattlePokemon.status1` | 80 |
| `struct BattlePokemon.volatiles` | 84 |
| volatile `electrified` bit | 54 |
| volatile `glaiveRush` bit | 64 |
| volatile `minimize` bit | 72 |
| volatile `semiInvulnerable` | bit 51, width 3 |
| volatile `chargeTimer` | bit 73, width 2 |
| volatile `tarShot` bit | 299 |
| volatile read window | 42 bytes (`HNS_LIVE_BP_VOLATILE_WINDOW_BYTES`; the current maximum is the existing Paradox bit at 328. Group D item bits `embargo` 81, `transformedMonSpecies` 128–138, and `metronomeItemCounter` 232–239 fit inside this window.) |
| `struct BattleStruct.gimmick` | 668 |
| `struct BattleGimmickData.activeGimmick` | 11 (side stride 6) |

`./ci.sh source-check` regenerates the header and fails on any drift. The EWRAM global addresses
(shared by the already-pinned battle globals) are:

| Global | EWRAM offset |
|---|---|
| `gFieldStatuses` | `0x2E8` (corrected from `0x2F4`; see §15.7) |
| `gBattleWeather` | `0x390` |
| `gSideStatuses[NUM_BATTLE_SIDES]` | `0x324` (4-byte stride) |
| `gBattleStruct` pointer | `0xB4` |

`gBattleStruct` is a heap pointer: the reader reads the pointer afresh, requires it to fall inside the
EWRAM window it was handed, and only then dereferences `activeGimmick`. A null, stale or out-of-EWRAM
pointer leaves the gimmick **unobserved** (never `NONE`).

### 14.3 Runtime reader semantics

`pokemon_read_battler_runtime_state_gba` now also decodes, per OBSERVED authoritative battler:

* `hp` / `maxHP` (`hp_observed`, `max_hp`);
* `status1` (`status_observed`; the raw word, `0` is an observed neutral);
* the damage-relevant volatile bits (`volatiles_observed`, `volatile_electrified`,
  `volatile_glaive_rush`, `volatile_charge_timer`, `volatile_tar_shot`, plus the recorded
  `volatile_minimize` / `volatile_semi_invulnerable`); the reader reads the generated 42-byte
  volatile window, because `tarShot` sits at bit 299 (and review-round-4 `roostActive`/`endured` at
  318/322) so the original 10-byte window silently truncated them;
* the review-round-4 persistent volatile bits (`persistent_volatiles_observed`, `volatile_foresight`,
  `volatile_miracle_eye`, `volatile_root`, `volatile_smack_down`, `volatile_telekinesis`,
  `volatile_magnet_rise`, `volatile_gastro_acid`, `volatile_roost_active`, `volatile_substitute`,
  `volatile_endured`); `roostActive` (318) and `endured` (322) extend the generated read window to
  41 bytes, and the already-existing Paradox bit at 328 makes the current generated window 42 bytes;
* the gimmick byte (`gimmick_observed`, `active_gimmick`);
* the battle-global `gFieldStatuses` word (`field_statuses_readable`, `field_statuses`);
* the battle-global `gBattleWeather` flags word (`weather_readable`, `battle_weather`; 0 is an
  observed clear weather);
* this battler's own side status word `gSideStatuses[side]` (`side_statuses_readable`,
  `side_statuses`; the side comes from the authoritative `gBattlerPositions` bit, not from the
  caller's role, and 0 is an observed screenless side).

Issue #92 adds item-sensitive volatile operands to the same generated window: Embargo at bit 81,
the eight-bit Metronome item counter at bits 232–239, and the eleven-bit stored transformed species
at bits 128–138. Their observed flags are independent of payload zero. The native/JNI battler tuple
therefore grows from 97 to 103 integers; the older 97-integer tuple remains decodable but cannot
authorize an item whose effect depends on these new operands.

Every `*_observed` / `*_readable` bit separates **observed neutral** (bit set, payload zero) from
**never read** (bit clear), so `false` is never collapsed with `unreadable`. The tuple grew from 42 to
72 ints (`BATTLER_RUNTIME_STATE_TUPLE_LEN`): the pre-C4e 42-int contract still decodes the older
fields, a 56-int tuple additionally decodes the C4e live operands, a 60-int tuple carries the live
weather / side-status words, a 62-int tuple carries the correction-pass `chargeTimer` / `tarShot`
operands, and only a 72-int tuple carries the review-round-4 persistent volatiles - a short tuple
leaves the later fields unobserved rather than defaulted, and the policy then fails closed. All new
reads share the existing fail-closed lifecycle
(ACTIVE only), battler resolution, party-slot binding, teardown clearing and profile-switch clearing;
nothing is cached across battles.

### 14.4 Effective move-type resolution

For the supported subset the boundary binds `fieldStatuses` (both battle-level observations must read
it and agree) and the attacker's `electrified` bit. `dynamicMoveTypeObserved` becomes true only when
both were read. The policy then decides:

* `electrified == true` → `HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED` (any type);
* Ion Deluge set **and** the move's effective type is Normal → the same limitation;
* otherwise static type is source-proven unchanged and the ordinary arithmetic applies.

The active Electric retype is deliberately **not** published: the ordinary-subset arithmetic/type
evidence does not cover the forced typing, so it stays refused rather than computed with the static
type. This closes the C4d blocker "dynamic move type no longer uses a blanket unobserved blocker".

### 14.5 Transient-state resolution

`transientStateObserved` is bound from the slot-matched volatile window (the `chargeTimer`/`tarShot`
extension) and is a conjunction over exactly the operands below; the pinned source has twice disproven
any claim that the generic ordinary-damage transient set is only three fields, so the set is described
exhaustively rather than counted:

* `glaiveRush` (defender) - `true` → `HNS_GLAIVE_RUSH_ACTIVE_NOT_MODELLED` (x2 not modelled);
* `chargeTimer` (attacker) - a positive value **and** an Electric effective move type →
  `HNS_CHARGE_ACTIVE_NOT_MODELLED`; a positive value on an irrelevant type is provably inert and
  does not block, and `0` is the observed neutral;
* `tarShot` (defender) - `true` **and** a Fire effective move type → `HNS_TAR_SHOT_ACTIVE_NOT_MODELLED`;
  an irrelevant type is provably inert, and `false` is the observed neutral.

A short tuple that does not carry `chargeTimer` / `tarShot` leaves `transientStateObserved` false, so
the request fails closed with `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` rather than assuming the volatiles
were zero. Minimize and the semi-invulnerable states are proven unreachable for the ordinary subset by
the move-flag allow-list (§14.1.2), so no live reader is needed for them.

### 14.5.2 Persistent volatile resolution (review round 4)

The review-round-4 source dependency audit (see the evidence doc's *Source dependency ledger*) found
that the pinned ordinary `EFFECT_HIT` path also reads **persistent** volatiles that survive a prior
status move: `GetBattlerTypes` drops Flying under `roostActive`; `MulByTypeEffectiveness` bypasses the
Ghost / Dark immunities under `foresight` / `miracleEye`; `IsBattlerGrounded` reads `root`, `smackDown`,
`telekinesis` and `magnetRise`; `GetBattlerAbilityInternal` returns `ABILITY_NONE` under `gastroAcid`;
and `GetAdjustedDamage` reads `substitute` and `endured`. The generated volatile ABI now classifies all
ten bits (`substitute` 39, `foresight` 45, `root` 75, `gastroAcid` 80, `smackDown` 82, `telekinesis`
83, `miracleEye` 84, `magnetRise` 85, `roostActive` 318, `endured` 322; the derived read window is 41
bytes).

`persistentVolatilesObserved` is true only when both battlers' windows were read. The first production
subset does **not** model any positive behavior of these states, so each observed-active class refuses
with its own precise limitation:

* `foresight` → `HNS_FORESIGHT_ACTIVE_NOT_MODELLED`;
* `miracleEye` → `HNS_MIRACLE_EYE_ACTIVE_NOT_MODELLED`;
* `root` / `smackDown` / `telekinesis` / `magnetRise` →
  `HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED`;
* `roostActive` → `HNS_ROOST_ACTIVE_NOT_MODELLED` (the raw `gBattleMons[].types` bytes are no longer
  the engine's effective types, so the static-type comparison cannot prove the request neutral);
* `gastroAcid` → `HNS_ABILITY_SUPPRESSED_NOT_MODELLED` (the boundary publishes the engine's
  **effective** ability, `ABILITY_NONE` while suppressed, so the pinch logic can never apply the raw
  identity's x1.5);
* `substitute` → `HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED`;
* `endured` → `HNS_ENDURED_ACTIVE_NOT_MODELLED`.

A short tuple that does not carry the persistent window leaves `persistentVolatilesObserved` false and
refuses with `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`. Every field is observed-neutral on the Golden-A
path, so the positive control is unchanged.

### 14.5.1 Field-status resolution

> **Superseded by §15.** The global "only Ion Deluge is supported" mask below was the C4e rule. Each
> active bit is now decided per request by `HnsFieldContextPolicy`; the historical text is kept for
> provenance.

The boundary binds the battle-global `gFieldStatuses` word (both battle-level observations must read
it and agree). The policy then applies the explicit supported mask from §14.1.3:

* `fieldStatuses == 0` - eligible;
* Ion Deluge bit set - the existing Normal-only retype logic in §14.4 applies;
* any other bit set - `HNS_FIELD_STATUS_NOT_MODELLED`, regardless of the move type.

This preserves the Golden-A Ready path unchanged (`fieldStatuses == 0`) while making Wonder Room,
Gravity, all four terrains and Mud/Water Sport fail closed instead of silently computing with the
unmodified defensive stat or type.

### 14.6 Conditional pinch ability support

`Overgrow` (65), `Blaze` (66), `Torrent` (67) and `Swarm` (68) are now
`HnsAbilityCategory.MODELLED_HNS_CONDITIONAL`. `calculateHnsDamage` applies
`halfDown(6144, userFinalAttack)` when the effective move type matches the boosted type **and**
`input.attacker.hp <= floor(input.attacker.maxHP / 3)`, mirroring `CalcAttackStat`. The capability
policy:

* for the defender, treats a pinch ability as irrelevant (pinch abilities do not affect incoming
  damage);
* for the attacker, proves irrelevance when the effective move type differs;
* otherwise requires the authoritative live HP/maxHP pair and refuses with
  `HNS_ABILITY_CONDITION_UNVERIFIED` when it was not read — it never assumes the condition inactive.

Live HP is boundary-reconciled: a caller-crafted `curHP` is overwritten from
`gBattleMons[battler].hp` whenever the engine HP was read, and the engine JSON receives both `hp` and
`maxHP`. Host tests (`check_gap_c4e_pinch_abilities`) cover inactive, active-at-threshold, one-HP-above
threshold and wrong-type cases against the independent H&S oracle.

### 14.7 Gimmick decision

Gimmick state is **observed**, not declared `NONE`. `attackerGimmick` / `defenderGimmick` are bound
from the exact-trusted observation; unreadable → `HNS_GIMMICK_STATE_UNREADABLE`; any non-`NONE` value
→ `HNS_GIMMICK_ACTIVE_NOT_MODELLED`. Tera/Dynamax/Z/Mega/Ultra Burst damage semantics stay
unmodelled and fail closed. The neutral `GIMMICK_NONE` case is runtime-verified on the official ROM
(§14.11).

### 14.7.1 Live field-condition resolution (Gap C4e correction)

Weather and defender-side screens were previously caller-supplied request fields: the calculator
screen defaulted to `weather = null` / `hasScreens = false` and the policy only rejected explicitly
unsupported values, so a live Rain or Reflect battle in which the user touched nothing silently
computed as clear / screenless. That unknown-to-neutral conversion is removed.

* The native reader now decodes the battle-global `gBattleWeather` word and the observed battler's
  own `gSideStatuses[side]` word, each with its own readability bit (0 is an observed neutral,
  never "unread").
* `CalcRequestBoundary` rebinds `field.weather` (clear / ordinary `Rain` / ordinary `Sun`) and
  `field.defenderSide` (Reflect / Light Screen bits) from the observed words for an active exact-H&S
  battle; any caller-supplied value is discarded, so a crafted neutral cannot stand in for an
  unobserved live state. The aggregate `B_WEATHER_RAIN` / `B_WEATHER_SUN` primal bits are deliberately
  not mapped to the ordinary names.
* `CalcCapabilityPolicy` refuses an unread word (`HNS_LIVE_WEATHER_UNKNOWN`,
  `HNS_LIVE_SCREENS_UNKNOWN`) or an observed word carrying a bit the ordinary arithmetic does not
  model (`HNS_LIVE_WEATHER_NOT_MODELLED` for Sand/Hail/Snow/Fog/Strong Winds **and the primal
  Rain/Sun bits**, `HNS_LIVE_SIDE_STATUS_NOT_MODELLED` for Aurora Veil and every other side status).
* Out of battle the controls remain manual hypotheticals (the screen labels them as such), which is
  why the manual/out-of-battle path keeps the request's field values.

### 14.7.2 Live battle format resolution (review round 5)

Battle format was previously caller/UI-owned: the calculator screen hardcoded
`gameType = CalcGameTypes.SINGLES` and the boundary only consulted the observed topology inside
`authoritativeMoveTargetCount` when the request *already* said Doubles. That is not sound, because
H&S selects different arithmetic by format even for a non-spread ordinary hit:
`GetScreensModifier` (`src/battle_util.c:7527`) composes Reflect / Light Screen with
`UQ_4_12(0.667)` in a Doubles battle and `UQ_4_12(0.5)` in Singles, and the partner-dependent
branches are Doubles-only. The native observation already carries the real topology
(`gBattlersCount`: 2 Singles / 4 Doubles), so the boundary now owns it:

* `CalcRequestBoundary` binds `observedBattlersCount` from **both** battle-level observations,
  requiring the readability bit on each and agreement between them; a one-sided read or a
  disagreement is null (unobserved), never a side picked.
* `CalcCapabilityPolicy` refuses a live request whose observed format is not the Singles topology
  the subset models, with the precise limitation `HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED`. An unread
  word, a player/enemy disagreement, an observed count of `4` (including the late-Doubles shape
  where each side has only one present battler but `gBattlersCount` stays 4), or a request label
  that contradicts the observed topology all fail closed.
* A caller-crafted Singles label cannot override an observed four-battler topology, and a
  caller-crafted Doubles label against an observed Singles topology fails closed rather than
  redefining reality. The request's `field.gameType` is only a claim that must agree with the
  observed count.
* Any live Doubles request (observed count of `4`, or mislabelled Singles/Doubles, regardless of
  whether a target count could be resolved) is refused by this gate with
  `HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED` because the C4e production subset models Singles only.
  Production Doubles carries unobserved live operands (e.g. Helping Hand) and remains blocked.

This is deliberately a separate limitation from the Doubles target-count gate: the whole live
calculation is under an unmodelled format, not just a spread move.

### 14.8 Exact-ROM trust audit and hash promotion

Promoting the exact SHA makes `RuntimeRomTrust.mayReadLiveMemory == true`, which unlocks every
live-memory feature in the companion. The C4e audit dispositions each unlocked surface:

| Unlocked surface | Disposition |
|---|---|
| Player/enemy party reads | Exact H&S 2.0.5 layout evidence already exists (`memoryLayoutVerified`, C1-C4 dossiers); no new claim. |
| Location / map reads | Exact layout evidence exists (H&S SaveBlock1 handling); fail closed on unknown IDs. |
| Battle lifecycle / active battler | Runtime-validated in the C4d probe run and native tests. |
| Calculator live observations | Have their own per-capability gates (§§4-13 plus §14.9); the hash does not bypass any of them. |
| Badge state | Read from SaveBlock1; player-side only; the calculator gate is independent. |
| Assistant / cheats / interactive controls | `battleUiVerified` and `interactiveControlsVerified` remain `false`; interactive controls are gated by `BattleInteractionPolicy`, not by the hash. |

No unlocked surface becomes unsafe or overclaimed, so C4e **promotes only the exact C4d SHA** into
`app/src/main/assets/profiles/heart_and_soul.json`. No broader hash, no header-based trust, and
`battleUiVerified` / `interactiveControlsVerified` are unchanged. Regression tests pin: exact hash →
exact trust; one-bit-different hash → `RECOGNIZED_UNVERIFIED` and no live memory; recognized header
without the exact hash → unverified.

### 14.9 Authorized production subset

A request reaches `Ready` / `ESTIMATED` only when **all** of the following hold:

* the running ROM is the exact promoted H&S 2.0.5 SHA and the profile is exact-verified;
* an active **Singles** battle with both battler observations OBSERVED and party-slot-matched;
* an ordinary `EFFECT_HIT` move from `Hns205MoveEffects.ordinaryMoveIds` that does not read item
  state (`HnsMoveItemInteractionRegistry`);
* attacker and defender effective types observed and equal to the pinned static record, at most two
  represenable types;
* raw battle stat words and stat stages observed (`-6..+6`);
* attacker badge boosts observed (defender badge state is irrelevant);
* supported challenge settings: optionStyle / Fairy observed, Random Types and Random Type
  Effectiveness observed **off**, Base Stat Equalizer observed **off**, Random Moves observed **off**.
  Random Abilities may be on or off because effective numeric IDs are read from the live battlers;
* ability and item identities are authoritative and classified; modelled/proven-irrelevant mechanics
  follow their normal paths, while a known relevant unsupported ability or item may be neutralized as a
  named caveat when the move itself has a supported base calculation; ability capability combines the
  unchanged global `HnsAbilityRegistry` with the source-backed contextual rules in §6.3 (pinch abilities
  use §14.6);
* effective move type fully resolved (no active Electrify / Ion Deluge);
* defender Glaive Rush, attacker `chargeTimer` and defender `tarShot` observed neutral (`false` /
  `0` / `false`); a positive relevant Charge / Tar Shot refuses with its precise limitation and a
  short tuple that does not carry them refuses with `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`;
* both battlers' **persistent** volatile windows observed neutral: `foresight`, `miracleEye`, `root`,
  `smackDown`, `telekinesis`, `magnetRise`, `gastroAcid`, `roostActive`, `substitute` and `endured`
  all false; each observed-active class refuses with its precise limitation (§14.5.2), and a short
  tuple that does not carry them refuses with `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`;
* gimmick state observed `GIMMICK_NONE` for both participants;
* attacker live `status1` observed; nonzero status is accepted only for effective Guts on an
  authoritative Physical move when the raw word intersects pinned `STATUS1_ANY`; other active
  statuses remain refused;
* the battle-global `gFieldStatuses` word observed and fully decoded (0 is the neutral word; a known
  relevant supported field modifier may be cleared as a named caveat; unknown bits and Ion Deluge's
  active Normal-type rewrite remain hard);
* the battle-global `gBattleWeather` word observed (clear, ordinary Rain or ordinary Sun; any other
  word - including the primal Rain/Sun bits - refuses with `HNS_LIVE_WEATHER_NOT_MODELLED`, and an
  unread word refuses with `HNS_LIVE_WEATHER_UNKNOWN`);
* the defender-side `gSideStatuses[side]` word observed with only the Reflect / Light Screen bits
  (an unread word refuses with `HNS_LIVE_SCREENS_UNKNOWN`, any other bit with
  `HNS_LIVE_SIDE_STATUS_NOT_MODELLED`);
* the live battle format observed as Singles: both battle-level observations read
  `gBattlersCount` and agreed on `2`, and the request label agrees it is Singles. An unread word, a
  disagreement, an observed `4`, or a contradictory label refuses with
  `HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED` (§14.7.2);
* no unmodelled weather/terrain/status/move mechanic.

Every other hard limitation remains in force. A globally unsupported ability or item may be cleared
only when its identity is known, its contextual decision is `RELEVANT`, and the authorized request
neutralizes it; `UNKNOWN`, unclassified, unread, or unauthoritative identities remain hard. A known
relevant field modifier is likewise cleared only through its reviewed neutral operand. No caveat
permits an unsupported move, invented identity, or missing base operand. This is a deliberately small
capability class, not "H&S is supported".

**Provenance of every mutable operand for that subset** (task §3 audit). "Boundary-owned" means
`CalcRequestBoundary` rebinds it from the exact-trusted runtime observation and strips any caller
value; "source-proven" means the pinned source/data proves it cannot vary for this request.

| Operand | Provenance |
|---|---|
| attacker effective types | boundary-owned `gBattleMons[a].types`; must equal the pinned static record |
| defender effective types | boundary-owned `gBattleMons[d].types`; must equal the pinned static record |
| raw battle stat words | boundary-owned `gBattleMons` attack/defense/speed/spA/spD |
| stat stages | boundary-owned `gBattleMons.statStages` |
| current item | boundary-owned `gBattleMons[battler].item`; modelled/proven-irrelevant items stay intact, while known relevant unsupported items are neutralized as named caveats |
| effective ability | boundary-owned numeric `abilityId`; global classification plus contextual request rules (§6.3). Under observed `gastroAcid` the boundary publishes `ABILITY_NONE` (the engine's `GetBattlerAbility()`), and the suppression is separately refused (§14.5.2) |
| current HP / max HP | boundary-owned `gBattleMons.hp` / `.maxHP`; required for a relevant pinch ability |
| badge applicability | boundary-owned player-side badge state; enemy badges source-proven irrelevant |
| move type / effective type | pinned pack override + observed Ion Deluge field word and Electrify volatile |
| weather | boundary-owned battle-global `gBattleWeather` (both observations must agree); only clear / ordinary Rain / ordinary Sun accepted; the primal Rain/Sun bits refuse |
| screens | boundary-owned defender-side `gSideStatuses[side]`; only Reflect/Light Screen bits |
| burn / status | boundary-owned raw `status1`; only physical Guts with `status1 & STATUS1_ANY != 0` is modelled, including burn-cancellation; every other active status refuses |
| crit flag | request `isCrit`; C4d indirect observation |
| game format | boundary-owned `gBattlersCount` agreed by both battle-level observations; must be the observed Singles `2`, and the request label must agree; an unread word, a disagreement, an observed `4`, or a contradictory label refuses with `HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED` (§14.7.2) |
| field statuses | boundary-owned battle-global `gFieldStatuses` (both observations must agree); known relevant supported modifiers are cleared as named caveats; unknown bits and Ion Deluge's active Normal retype remain hard |
| attacker volatiles | boundary-owned `electrified` must be false; boundary-owned `chargeTimer` must be 0; the persistent window (`foresight`/`miracleEye`/`root`/`smackDown`/`telekinesis`/`magnetRise`/`gastroAcid`/`roostActive`/`substitute`/`endured`) must be observed all-false |
| defender volatiles | boundary-owned `glaiveRush` must be false; boundary-owned `tarShot` must be false; the persistent window must be observed all-false |
| gimmick / Tera | boundary-owned `gBattleStruct->gimmick.activeGimmick` must be `GIMMICK_NONE` |

Every caller-supplied field on `hnsLiveBattleState` (and `curHP`) is discarded and rebound, so no
caller value can independently authorize an H&S live calculation.

### 14.10 Production-boundary evidence

`CalcHnsC4eProductionBoundaryTest` drives the real `CalcRequestBoundary`:

* **Positive control** — a C4d Golden-A-equivalent live state (Chikorita + Tackle vs Pidgey, live
  HP 14/20, Overgrow irrelevant to a Normal move) returns `Ready`, a non-null request and
  `ESTIMATED`; the emitted JSON carries the live HP/maxHP and the pinned move override.
* **Adjacent negatives** — removing exactly one authority refuses with a precise limitation: wrong
  ROM hash, unreadable volatile, unreadable extended transient volatiles (`chargeTimer`/`tarShot`),
  unreadable field status, an observed unmodelled field status (Wonder Room, terrain), unreadable
  weather word (`HNS_LIVE_WEATHER_UNKNOWN`), unreadable defender-side status word
  (`HNS_LIVE_SCREENS_UNKNOWN`), an observed unmodelled weather word including a primal Rain/Sun bit
  (`HNS_LIVE_WEATHER_NOT_MODELLED`), an observed unmodelled side-status bit
  (`HNS_LIVE_SIDE_STATUS_NOT_MODELLED`), unreadable/active gimmick, active Electrify, active Ion Deluge
  on a Normal move, active Glaive Rush, active Charge with an Electric move, active Tar Shot with a
  Fire move, unsupported ability, unverified pinch HP, stale participant slot, unobserved badge state,
  unsupported move, active live status. Charge / Tar Shot on an irrelevant move type stay Ready.
  Review round 4 adds one negative per persistent volatile (Foresight, Miracle Eye, each grounding
  volatile, Roost, Gastro Acid suppression, Substitute, Endure) plus an unread-persistent-window
  refusal; the all-neutral Golden-A path stays Ready.
* **Observed field conditions** — observed Rain and observed defender Reflect / Light Screen are bound
  into the request and reach the engine JSON; observed clear / no screens binds no weather / no
  defender side.
* **Anti-spoofing** — a caller-crafted `hnsLiveBattleState`, a caller-crafted `curHP`, and caller-supplied
  weather / screen values are stripped and rebound from the runtime observations; a caller-spoofed
  ability is overridden by the authoritative numeric ID. Review round 4 adds an anti-spoof loop over
  all ten persistent volatile bits (a crafted neutral window cannot clear an observed-active bit) and
  the inverse (a crafted active bit cannot refuse a neutral runtime window). No caller-provided
  `hnsLiveBattleState` field can independently authorize an H&S live calculation.
* **Live battle format (review round 5)** — the observed Singles `2` keeps the positive control
  `Ready`; an observed `4` (including with Reflect), a player/enemy disagreement, an unread count
  (either side or both), a caller-crafted Singles label against observed `4`, and a caller-crafted
  Doubles label against observed `2` all refuse; the boundary strips a crafted
  `observedBattlersCount`.

### 14.11 Runtime verification

`tools/hns-runtime-probe/evidence/golden-c4e-live-operands.log` is a raw official-ROM run (ROM SHA
`edf76ec...7679b`) that prints the new fields through the production reader at the Golden A hit frame:
HP/maxHP observed (20/20 and 16/16), `status1 == 0`, volatiles observed with `electrified` and
`glaiveRush` false, `activeGimmick == GIMMICK_NONE`, `gFieldStatuses == 0`, `weatherReadable=1
weather=0x0000` (observed clear) and `sideStatusesReadable=1 sideStatuses=0x00000000` (observed
screenless defender side). That is the neutral state the first production subset depends on, so the
neutral case is **RUNTIME VERIFIED**. No positive transition (an actually-active volatile/gimmick, or
an active Rain / Reflect frame) was manufactured in this neutral baseline. Active ordinary Rain/Sun and
Reflect/Light Screen are SOURCE + HOST reasoned / conditionally production-authorized, but do not have
retained positive runtime verification (unsupported weather or side-status bits continue to fail closed);
active unmodelled volatiles and gimmicks remain refused at runtime rather than claimed verified.

The correction pass extends the same volatile read window to cover `chargeTimer` and `tarShot` (a
reader-only change); `golden-c4e-live-operands.log` was produced before that extension and does not
print the two new operands, so they are **HOST VERIFIED only** (decoder, native reader and
production-boundary negative tests). The production path requires them observed and fails closed on a
short tuple, so no unverified positive value can be published.

### 14.12 Reuse of the C4d goldens

The positive control is a Golden-A-equivalent live request (Chikorita + ordinary Normal move vs
Pidgey) constructed through normal product authority; the engine's independent host oracle still
produces the 5..7 range and the observed ROM damage 6 remains a valid roll. Golden B's Overgrow is
conditionally supported and inactive at 11/23 HP, and Golden C's Torrent is irrelevant to a Normal
move, so both shapes are compatible with the new subset. The C4d native golden test is unchanged and
still green; C4e adds the pinch-ability fixtures alongside it.

### 14.13 Doubles, badge Golden D, and remaining limitations

* **Doubles** remains **BLOCKED** by the AMBIGUOUS per-side battler observation. C4e adds no
  battle-level Doubles plumbing and does not make production Doubles an acceptance requirement. The
  review-round-5 format gate additionally refuses any live calculation whose observed `gBattlersCount`
  is not the Singles `2`, so a Doubles battle (including the late-Doubles shape where each side has
  one present battler) can never be computed with the Singles screen multiplier even though the UI
  labels it Singles (§14.7.2).
* **Badge Golden D** remains unvalidated: negative/neutral badge state is observed false and the
  request is correctly computed without a boost, but no positive badge-boost arithmetic is claimed
  RUNTIME VERIFIED. The C4b host oracle coverage remains HOST VERIFIED.
* **Golden F** is partially satisfied: the new readers have a neutral-state runtime observation
  (§14.11) but no positive transition.
* Active dynamic-type retypes, active Glaive Rush, active Charge on an Electric move, active Tar
  Shot on a Fire move, any active persistent volatile (Foresight, Miracle Eye, Ingrain/Smack
  Down/Telekinesis/Magnet Rise, Roost, Gastro Acid suppression, Substitute, Endure), unknown field
  bits, field states with unknown contextual relevance, and Ion Deluge's Normal-type retype (§15),
  active gimmicks, non-neutral live status, unread or unmodelled live weather (including the primal
  bits) / defender-side screens, a live topology that is not the observed Singles `2` (an unread or
  disagreeing `gBattlersCount`, or an observed `4`), unknown or unclassified ability relevance,
  unread/unresolved item identity, and unsupported moves remain
  refused so a confident wrong number is never published.

### 14.14 Issues #9 and #40

The accepted #9 closure scope is now the **bounded exact H&S 2.0.5 ordinary live Singles subset**
defined in §14.9, capped at **`ESTIMATED`**, together with the exact vanilla FireRed/Emerald
calculator evidence. This does not promote H&S calculations to `VERIFIED`; contextually irrelevant
abilities add no caveat, while known relevant unsupported mechanics produce named estimates under #86.
The supported H&S subset has the pinned data, upstream/host fixtures and direct A/B/C
runtime observations documented here; unsupported mechanics and states remain refused.

[VANILLA_CALCULATOR_EVIDENCE.md §11](VANILLA_CALCULATOR_EVIDENCE.md#11-issue-9-acceptance-audit-current-main)
audits all nine #9 criteria across both targets, including the controlled vanilla runtime
observations in §5. With that cross-target audit, the recommendation is to **close #9 after senior
review and merge of PR #71**. The earlier statement that broader H&S support leaves a remainder
open under #9 is superseded by this accepted bounded scope. Until review/merge, #9 stays open.

Issue #89 extends the request-local supported subset with source-backed Group C immunities and the
Air Balloon, Iron Ball, and Ring Target rules; the full audit is in
[HNS_GROUP_C_IMMUNITIES.md](HNS_GROUP_C_IMMUNITIES.md). This #91 slice promotes defender-side Water
Bubble Fire and Heatproof Fire base-power scenarios after exact oracle parity; it keeps Water Bubble
burn prevention separately deferred and does not close issue #91. The registered divergence list
now contains only the two #100 Doubles Dazzling Gleam cases.

The limits in §14.13 remain unchanged: H&S Doubles, other damage items and unsupported mechanics
are not newly supported; positive badge runtime Golden D and positive transient transitions are
not newly proven, and crit Golden E remains indirect. These are follow-up evidence/support work,
not additional requirements for the accepted #9 closure. #40 is a separate, broader gate
(hardware, maps, lifecycle, cheats, Assistant, release) and remains open.

---

## 15. Live field state (`gFieldStatuses`) — decoder, source audit and request-local relevance

**Trigger.** On an AYN Thor, a fresh Random Abilities battle showed `Damage unavailable · 2
blockers` / `Field condition not modelled` / `You: Wise Glasses`. The user had not set a terrain or
room. Two defects are fixed here. First, the UI collapsed every field, weather and side condition into
one label and never said which bit it read. Second, the item and ability context policies required
`gFieldStatuses == 0` before they would treat the move's type **or category** as authoritative. That
made an obvious "Wise Glasses cannot boost a Physical move" undecidable whenever any unrelated field
bit was set.

Authority: `PokemonHnS-Development/pokehns-expansion` `Release-v2.0.5`
(`1f42b74dff0e9fe942419845d040663dd829a973`), ROM SHA-256
`edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b`. The reader address was
previously configured at EWRAM `+0x2F4` based on an experimental LTO build (`pokehns-release.elf`).
Following the AYN Thor real-device observation of a bogus Magic Room blocker on fresh battle entry,
official ROM literal pools, disassembly, and positive semantic transitions (Magic Room 0->1->0,
Trick Room 2, Electric Terrain 0x100) proved that `0x2F4` is `gBattleControllerExecFlags` and
`gFieldStatuses` is at EWRAM `+0x2E8` (see §15.7). The production reader has been updated to `0x2E8`,
and the legacy `0x2F4` attribution has been withdrawn.

### 15.1 Pinned bit table (generated, source-checked)

| Bit | Mask | Pinned symbol | Condition | Damage-path reads for an ordinary hit (`field_audit.json`) |
|---:|---|---|---|---|
| 0 | `0x00000001` | `STATUS_FIELD_MAGIC_ROOM` | Magic Room | `GetBattlerHoldEffectInternal` returns `HOLD_EFFECT_NONE` for every holder (items, grounding, speed) |
| 1 | `0x00000002` | `STATUS_FIELD_TRICK_ROOM` | Trick Room | turn order only (`GetWhichBattlerFasterArgs`); reaches damage only via the attacker's Analytic |
| 2 | `0x00000004` | `STATUS_FIELD_WONDER_ROOM` | Wonder Room | `CalcDefenseStat` swaps Defense/Sp. Def **and** `usesDefStat` (Marvel Scale, Fur Coat, Grass Pelt, Assault Vest, Metal Powder, Deep Sea Scale, Ruin, weather Def/SpDef boosts); paradox stat choice |
| 3 | `0x00000008` | `STATUS_FIELD_MUDSPORT` | Mud Sport | Electric move ×0.33 |
| 4 | `0x00000010` | `STATUS_FIELD_WATERSPORT` | Water Sport | Fire move ×0.33 |
| 5 | `0x00000020` | `STATUS_FIELD_GRAVITY` | Gravity | groundedness (Ground-move immunity/effectiveness, terrain checks); `gravityBanned` moves fail; accuracy (not part of the range) |
| 6 | `0x00000040` | `STATUS_FIELD_GRASSY_TERRAIN` | Grassy Terrain | grounded attacker's Grass ×1.3; defender Grass Pelt; Grassy Glide priority (turn order) |
| 7 | `0x00000080` | `STATUS_FIELD_MISTY_TERRAIN` | Misty Terrain | Dragon ×0.5 against a grounded target |
| 8 | `0x00000100` | `STATUS_FIELD_ELECTRIC_TERRAIN` | Electric Terrain | grounded attacker's Electric ×1.3; Quark Drive (attacker/defender); Hadron Engine (attacker); Surge Surfer / Quark Drive speed |
| 9 | `0x00000200` | `STATUS_FIELD_PSYCHIC_TERRAIN` | Psychic Terrain | grounded attacker's Psychic ×1.3; a positive-priority move fails against a grounded target |
| 10 | `0x00000400` | `STATUS_FIELD_ION_DELUGE` | Ion Deluge | `SetTypeBeforeUsingMove`: Normal → Electric (and so TYPE_BASED category) |
| 11 | `0x00000800` | `STATUS_FIELD_FAIRY_LOCK` | Fairy Lock | `CanBattlerEscape` only |
| — | `0x000003C0` | `STATUS_FIELD_TERRAIN_ANY` | any terrain | composition of bits 6–9 |

`tools/hns-field-status/generate_hns_field_status.py` parses these `#define`s from the pinned
`include/constants/battle.h` (it refuses any form other than `(1 << n)` or an OR of field symbols). It
emits the `HnsFieldStatus` enum and `HnsFieldStatusData` constants
(`app/src/main/java/com/dualdex/pokemon/hns/HnsFieldStatusData.kt`) and the native
`native/src/hns_field_status_gen.h`. No mask is hand-written anywhere else: `HnsBattlerRuntimeStateIds`
and the reader config's Ion Deluge mask use the generated values. `--check` runs in `./ci.sh
source-check` and fails on any of these:
- a moved, renamed, added, removed or bit-sharing symbol, or a changed `TERRAIN_ANY` composition;
- a new, moved or removed pinned field-status reference;
- stale evidence text;
- a rule that is reviewed but not implemented, or implemented but not reviewed;
- an ordinary move that declares a terrain boost;
- a regenerated artifact that differs from the committed one.

The self-contained drift tests (`test_generate_hns_field_status.py`, in `./ci.sh test`) prove that
each of those mutations fails.

### 15.2 Source dependency audit

`tools/hns-field-status/field_audit.json` records, for each of the 12 bits:
- the set, clear and read sites;
- pinned damage evidence;
- the `affects` matrix (move type, category, base power, Attack/Sp. Atk, Defense/Sp. Def, final damage,
  type effectiveness, turn order, move failure, item activation, ability activation);
- the request operands a proof needs;
- the reviewed rules and the fail-closed fallback.

The reference inventory covers every pinned `src/**/*.c` reference (AI, debug, animation and bg
excluded) to any of these tokens, grouped by enclosing definition (91 sites):
- the field symbols, `gFieldStatuses` and `ctx->fieldStatuses`;
- the terrain helpers, `IsBattlerGrounded[InverseCheck]`, `IsGravityPreventingMove`/`IsMoveGravityBanned`;
- the sport helpers and `IsLastMonToMove`.

Each site carries a reviewed `use` and a damage disposition:
- `ordinary`: on the single-hit `EFFECT_HIT` path;
- `excluded`: reachable only through a non-ordinary effect such as Terrain Pulse, Grav Apple, Nature
  Power, Expanding Force, Steel Roller, Natural Gift or Fling;
- `none`: set/clear, status prevention, selection, speed, post-damage or end-of-turn.

Relevance is never inferred from a field's name.

Move facts are derived from the pinned move table with the same ordinary-move extraction as
`tools/hns-move-mechanics`:
- Gravity-banned ordinary moves: `{Floaty Fall}`;
- ordinary moves whose priority can be positive (literal > 0, an unprovable expression, or a
  healing move that Triage raises by +3): Quick Attack, Mach Punch, Extreme Speed, Feint, Vacuum Wave,
  Bullet Punch, Ice Shard, Shadow Sneak, Aqua Jet, Accelerock, Jet Punch;
- ordinary terrain-boost moves: none, and the generator fails if one appears.

### 15.3 Decoder and raw-word preservation

The native reader stores the 32-bit word **unmasked** (a readable `0` is an observed clear field,
never "unread"). JNI passes it bit-for-bit as `jint`, and `CalcRequestBoundary` binds it only when both
battle-level observations read it and agree (a torn read stays null).
`HnsFieldState.decode(raw)` = `(raw, active: Set<HnsFieldStatus>, unknownMask = raw & ~0x00000FFF)`.
Every verdict for a live H&S request carries the raw word in
`CalcCapabilityVerdict.hnsFieldDiagnostics`, next to the separate `gBattleWeather` word, the defender
`gSideStatuses` word and the attacker Electrify volatile. `hnsFieldDecisions` holds one decision per
active pinned bit, plus one for any unknown bits. Native host tests (`test_hns_field_statuses_raw_word`) and device regression tests
(`test_hns_field_statuses_thor_regression`) prove all of the following:
- the address is verified at `+0x2E8` (the legacy `+0x2F4` was withdrawn as `gBattleControllerExecFlags`);
- zero stays readable;
- each of the 12 bits, combinations, `0x2000`, `0x80000100` and `0xFFFFFFFF` survive unchanged;
- both observations agree.

### 15.4 Move-type vs move-category authority (`HnsMoveAuthority`)

The old single `typeAuthoritative` flag (which included `fieldStatuses == 0`) authorised both type and
category. It is split into three operands. Item, ability and field rules each consume only what they
need.

| Operand | Authoritative when | Why (pinned) |
|---|---|---|
| `sourceType` | Pinned move identity plus the observed Fairy toggle; Fairy moves use their pinned alternate type when Fairy is OFF | `GetMoveType` and `sFairyMoveAltTypes` |
| `preFieldType` | Ordinary move, live state, observed `GIMMICK_NONE`, authoritative attacker ability, and source metadata; pinned ability predicate is either applied or proven inactive | `GetDynamicMoveType`: Normalize, the four -ate abilities and Liquid Voice run before the field rewrite; unsupported effects remain outside the ordinary allow-list |
| `effectiveType` | `preFieldType` known, Electrify observed false, field word read and fully decoded; active Electrify and relevant Ion Deluge remain refused by policy | `SetTypeBeforeUsingMove` later Electrify / Ion Deluge ordering remains represented; unknown field bits are never assumed harmless |
| `category` | **PER_MOVE_SPLIT**: pinned source move category. **TYPE_BASED**: `gTypesInfo[effectiveType].damageCategory` | `GetBattleMoveCategory` consumes the final effective type |
| `ateBoost` | Explicit result from the ability branch; false for Liquid Voice and proven-inactive predicates, unknown when required authority is missing | `SetTypeBeforeUsingMove` clears it; `GetDynamicMoveType` sets it; the later base-power stage consumes it with the final type |

Consequences:
- Choice Band / Wise Glasses / Muscle Band need only the category.
- Charcoal / Plates / Gems / resist berries / signature orbs need only the effective type.
- Focus Sash needs only defender HP/maxHP.
- Assault Vest / Metal Powder / Deep Sea Scale need the category and Wonder Room observed inactive.
- Air Balloon / Iron Ball need no terrain bit.

The ability rules (Guts, Huge/Pure Power, Thick Fat, Levitate, Adaptability, and the final damage
modifiers) use the same authority, so a terrain no longer makes those ability decisions unknown
merely because it is active. A known relevant ability or item can be ignored only when its full
context is known; unknown relevance can still keep a request refused.

### 15.5 Request-local field rules (`HnsFieldContextPolicy`)

Every active bit is decided independently. `PROVEN_IRRELEVANT` contributes no limitation. The
fourth outcome, `MODELLED`, is request-local: for this exact supported selected hit, every applicable
damage-changing consequence of this active bit is reproduced with authoritative operands. A
`MODELLED` bit adds no field limitation, is not shown under `Ignores:`, and remains in the execution
request. This does not declare an entire terrain globally supported.

A known `RELEVANT` field modifier receives a soft disposition: its named raw bit is removed from the
authorized request and it appears in the estimate's ignored-mechanics list. `UNKNOWN` stays a hard
refusal. A non-ordinary or unknown move leaves every contextual bit `UNKNOWN` (the move blocker also
applies independently). A terrain is never `MODELLED` when one applicable effect remains outside the
request's exact model.

The four direct move modifiers are now evaluated with boundary-owned operands: Grassy boosts a
terrain-affected attacker's Grass move by 1.3; Misty halves a Dragon move against a terrain-affected
defender; Electric boosts a terrain-affected attacker's Electric move by 1.3; Psychic boosts a
terrain-affected attacker's Psychic move by 1.3. The pinned source uses `uq4_12_multiply` for each
modifier (`src/battle_util.c:6639-6645`), with `UQ_4_12(1.3) == 5325` and `UQ_4_12(0.5) == 2048`
under `include/fpmath.h:15` and `B_TERRAIN_TYPE_BOOST GEN_LATEST` in
`include/config/battle.h:325`. The JavaScript pipeline composes these with the existing base-power
modifiers using half-up UQ4.12 arithmetic before the base power enters the ordinary damage formula.
Raw `gFieldStatuses` reaches the engine only as boundary-owned `field.hnsFieldStatuses`; terrain
applicability booleans are derived from observed battler state, never caller terrain text.
Misty Terrain's status prevention, confusion prevention, and Yawn behavior do not change the
selected-hit damage range and are outside this arithmetic slice.

`HnsTerrainAuthority` follows the pinned grounding precedence and fails closed when the runtime
reader cannot prove the result. It requires observed ability, item, types, and volatile state; any
active semi-invulnerability or existing grounding volatile gate is `UNKNOWN`. Iron Ball and Gravity
ground before Air Balloon, Levitate, or Flying type can exempt a battler. Magic Room suppresses the
two grounding items; when their active/suppressed state is uncertain, applicability is unknown.
Levitate under Neutralizing Gas or an unknown opposing effective ability also remains unknown.
The same authority is shared by terrain, Levitate, and grounding-item request rules.

| Condition | Proven irrelevant or exact-modelled case (rule) | Relevant effect / current handling |
|---|---|---|
| Magic Room | both authoritative live battle-effective items are `ITEM_NONE` or globally `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` (`magic_room_held_items_neutral`) | A damage-relevant or unread item makes suppression context unknown, so this field remains hard. |
| Trick Room | attacker's effective ability known and not Analytic (`trick_room_attacker_not_analytic`) | Known Analytic makes Trick Room relevant; the ability's own unknown effect can independently keep the request hard. |
| Wonder Room | — | Always relevant (`wonder_room_swaps_defensive_stat`); cleared and named as a caveat when the rest of the request is complete. |
| Mud Sport / Water Sport | effective type not Electric / not Fire | Electric / Fire; cleared and named as a caveat when complete. |
| Gravity | effective type not Ground and the move not `gravityBanned`; or Gravity is the authoritative reason the selected terrain modifier applies | Ground move; Floaty Fall. Gravity's accuracy change does not alter the damage range. |
| Grassy Terrain | direct Grass move modifier and Grass Pelt's physical Defense branch compose exactly when applicable | Analytic remains relevant because Grassy Glide priority can change its turn order; unknown terrain applicability remains hard. |
| Misty Terrain | Dragon move against a terrain-affected or proven ungrounded defender | Unknown defender terrain applicability remains hard. |
| Electric Terrain | direct Electric move modifier and Hadron Engine's Special Attack branch compose exactly when applicable | Quark Drive and Analytic remain relevant; unknown attacker terrain applicability remains hard for Electric moves. |
| Psychic Terrain | direct Psychic modifier when applicable and priority is proven non-positive | Positive-priority move; Gale Wings attacker (UNKNOWN); unknown attacker terrain applicability remains hard for Psychic moves. |
| Ion Deluge | pre-field type not Normal (`ion_deluge_non_normal_move`) | Normal move changes effective move type and remains hard with `HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED`. |
| Fairy Lock | always (`fairy_lock_escape_only`) | — |
| any bit outside `0x00000FFF` | — | always UNKNOWN (`unknown_field_bits`), mask preserved |

The exact field-backed stat branches use the pinned source order and operators:

- **Grass Pelt (ability 179):** `CalcDefenseStat` selects the defensive stat and `usesDefStat` first
  (`src/battle_util.c:7224-7250`). In its defender-ability switch, Grass Pelt follows Marvel Scale
  and Fur Coat (`:7295-7298`); when `ctx->fieldStatuses` contains Grassy Terrain and
  `usesDefStat` is true, it composes `UQ_4_12(1.5) = 6144` with
  `uq4_12_multiply_half_down`. The source predicate is the field bit plus `usesDefStat`; it has no
  grounding check. With Wonder Room clear, an ordinary Physical move selects Defense and enables
  the predicate; an ordinary Special move selects Sp. Def and disables it. Wonder Room flips
  `usesDefStat`, so Grass Pelt's ability decision stays unknown and the independent Wonder Room
  field condition stays caveated.
- **Hadron Engine (ability 289):** `CalcAttackStat` checks the attacker ability after Quark Drive and
  Orichalcum Pulse (`src/battle_util.c:7110-7112`). Its predicate is the already-observed Electric
  Terrain bit plus `IsBattleMoveSpecial(move)`. It composes `UQ_4_12(1.3333) = 5461` using
  `uq4_12_multiply` (half-up). The calculator does not reproduce Hadron Engine's switch-in event;
  if terrain was replaced or expired, the observed live field word wins.

The final verdict is the union of independent limitations. A neutralized field bit never clears an
ability, item, item-dependent-move, move-mechanic, status, weather, screen, volatile, gimmick,
challenge or species/type limitation. A neutralized item or ability never clears a field bit. If any
hard limitation remains, the policy refuses the whole request while retaining known caveats for
diagnostics.

**Still unmodelled.** Caveat mode can display a known relevant modifier without applying it. Quark
Drive, Booster Energy, Analytic itself, terrain-dependent moves (including Terrain Pulse, Nature
Power, Rising Voltage, Expanding Force, Psyblade and Misty Explosion), Grassy Terrain's
Earthquake/Magnitude halving, terrain seeds, Surge Surfer, and terrain turn-order effects remain
outside this slice. Grassy Glide is exact only for the direct damage modifier; Analytic keeps Grassy
Terrain relevant because priority can change its turn-order predicate. Grass Pelt, Hadron Engine,
and each direct move modifier compose only when every applicable consequence is covered.
Wonder Room arithmetic, Magic Room suppression of damage-relevant items, Trick Room turn simulation
for Analytic, Gravity on Ground moves, Ion Deluge's active Electric rewrite, positive-priority moves
under Psychic Terrain, and unknown field bits also remain unmodelled. Unknown or base-shape
limitations remain hard. Weather and side statuses keep their own C4e rules (§14.7.1). Doubles
remains refused.

### 15.6 Wise Glasses regression (the Thor symptom)

Setup: exact H&S, ordinary Singles, attacker Wise Glasses, a non-zero live field word, PER_MOVE_SPLIT.

| Move | Field word | Result |
|---|---|---|
| Tackle (Physical) | Wonder Room `0x00000004` | Wise Glasses **PROVEN_IRRELEVANT** (`special_only_item_physical_move`); card: `42-50 (Estimate)` / `Ignores: Field: Wonder Room` |
| Water Gun (Special) | Wonder Room `0x00000004` | Wise Glasses **MODELLED** (`wise_glasses_special_move`); card: `42-50 (Estimate)` / `Ignores: Field: Wonder Room` — Wonder Room is the only ignored mechanic |
| Tackle | Electric Terrain `0x00000100` | both proven irrelevant → **Ready** (estimate shown) |
| Water Gun | Electric Terrain `0x00000100` | terrain irrelevant; Wise Glasses **MODELLED** → **Ready** (estimate shown) |
| Water Gun | Clean / neutral field `0x00000000` | Wise Glasses **MODELLED** → **Ready** (estimate shown) |
| Thunder Shock (Special, Electric) | Electric Terrain `0x00000100` | Wise Glasses **MODELLED**; card: `42-50 (Estimate)` / `Ignores: Field: Electric Terrain` |

These are asserted through the real `CalcRequestBoundary`
(`CalcHnsC4eProductionBoundaryTest`) and the Battle move-card model (`BattleConsoleTest`).

### 15.7 Battle-tab presentation and on-device diagnosis

`DamageBlockerPresentation` has separate `Field`, `Weather` and `SideStatus` classes alongside State,
Ability, Item and Mechanic. Terrain, weather and a screen are therefore never the same label:

- `Damage unavailable · Electric Terrain not modelled` / `Field: Electric Terrain (0x00000100)`. A
  single field blocker always shows its observed mask.
- `Damage unavailable · 2 field blockers` / `Wonder Room (0x00000004)` / `Mud Sport (0x00000008)`
- `Damage unavailable · Unknown field state 0x00002000` / `Field: Unknown bits 0x00002000`
- `Damage unavailable · 4 blockers` / `Field: Wonder Room (0x00000004)` / `You: Analytic` /
  `You: Silk Scarf` / `Move effect not modelled`
- `Weather: not modelled (0x0020)` and `Foe side: not modelled (0x00000100)` are separate blockers.

The Battle status panel gains a **Field** row, e.g. `Trick Room + Electric Terrain (0x00000102)`,
`clear (0x00000000)`, or `Unread` for a torn or unread word. The word is logged (tag
`DualDexHnsField`, `H&S gFieldStatuses …`) once per change. The next device run therefore records
exactly which bit DualDex reads.

### 15.8 Why a fresh battle can already have field state

`gFieldStatuses = 0` at `BattleStartClearSetData` (`src/battle_main.c:3232`) and at
`FreeBattleResources`. Before the first move it can then be set by:

- **Trainer starting statuses** (`TryFieldEffects(FIELD_EFFECT_TRAINER_STATUSES)`, which reads
  `gStartingStatuses`, filled from the trainer's `startingStatus`). No pinned H&S trainer declares
  one (`src/data/trainers_hns.h` / `.party`), so this does not occur in H&S 2.0.5.
- **Overworld terrain** (`FIELD_EFFECT_OVERWORLD_TERRAIN`). This is compiled off: `B_THUNDERSTORM_TERRAIN
  FALSE` and `B_OVERWORLD_FOG GEN_3` in `include/config/battle.h`.
- **Switch-in abilities**: Electric Surge and Hadron Engine (Electric Terrain), Grassy Surge, Misty
  Surge and Psychic Surge, via `AbilityBattleEffects` → `TryChangeBattleTerrain`. **With Random
  Abilities either lead can carry one**, so a "fresh" battle can legitimately start with a terrain bit.
- Rooms, Gravity, the Sports, Fairy Lock, Ion Deluge, Seed Sower and terrain moves only set bits once a
  move or its effect runs.

"Fresh battle" therefore does not imply "no field state" in a Random Abilities run.

### 15.9 The Thor observation

The raw bit has not been captured, so the exact bit cannot be named. In pinned H&S 2.0.5 the only
pre-move sources are the switch-in terrain abilities (§15.8), so under Random Abilities the leading
explanation is a terrain bit (most likely Electric Terrain `0x00000100` from Electric Surge or Hadron
Engine, or another Surge's terrain).

The observed card is consistent with any non-zero word. Before this change, *every* non-zero word made
Wise Glasses UNKNOWN whatever the move's category, and added the generic field blocker.

After this change the same battle exposed the exact raw word: `Field: Magic Room (0x00000001)`.
This real-device capture exposed the underlying reader defect: `0x2F4` was reading controller flags,
not `gFieldStatuses`. The resolution is detailed in §15.7 below.

### 15.7 Real-device resolution: AYN Thor finding, 0x2E8 verification, and 0x2F4 withdrawal

#### 15.7.1 The AYN Thor finding
On real hardware (AYN Thor running the exact official H&S 2.0.5 ROM
`edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b`), a fresh battle was opened:
- Player: Porygon, held item Wise Glasses, live randomized ability Tera Shell.
- Opponent: Croconaw, live randomized ability Truant.
- No move had yet established Magic Room, no terrain, no weather.

DualDex reported:
```text
Damage unavailable · 2 blockers
Field: Magic Room (0x00000001)
You: Wise Glasses
```
Neither Tera Shell nor Truant can create Magic Room. Magic Room suppresses held item effects
(`GetBattlerHoldEffectInternal` returns `HOLD_EFFECT_NONE`), which caused Wise Glasses to be blocked.

#### 15.7.2 Investigation and layout authority
Investigation proved that the reader offset `0x2F4` was wrong:
1. **Official Release ROM Layout**:
   In the official ROM binary (`edf76ecf...`) and its symbol map (`artifacts-default/pokehns.map`):
   - `0x020002E8` is `gFieldStatuses` (107 literal pool matches across battle routines, e.g. `0x08006A28` loading alongside `gAiLogicData` at `0x2EC`, `gBattlersCount` at `0xB0`, and `gAbsentBattlerFlags` at `0x30A`).
   - `0x020002F4` is `gBattleControllerExecFlags`. Disassembly of `0x08056C00` proves this address is checked with `tst r2, r3` (`1 << battler`) during battle controller execution. When battler 0 executes its controller loop, bit 0 is set (`0x00000001`), which DualDex decoded as `STATUS_FIELD_MAGIC_ROOM`.
2. **Provenance of the Faulty 0x2F4 Offset**:
   An experimental build `pokehns-release.elf` built on Sep 14 with `-flto=auto` had reordered global symbols, placing `gFieldStatuses` at `0x2F4` and `gBattleControllerExecFlags` at `0x300`. This experimental layout did not match the official release ROM.
3. **Withdrawal**:
   The attribution of EWRAM offset `0x2F4` to `gFieldStatuses` is **WITHDRAWN / MISATTRIBUTED**. The read occurred, but semantic attribution of that memory location to `gFieldStatuses` was unproven and false.

#### 15.7.3 Semantic positive runtime verification
Using `tools/hns-runtime-probe/runtime_battle_probe` with normal controller inputs on the official release ROM:
- **Magic Room (move 478)**:
  - Fresh battle: `candidate_0x2E8 == 0x00000000`, `legacy_0x2F4 == 0x00000000`
  - Turn 1 (Magic Room used): `candidate_0x2E8 == 0x00000001`, `legacy_0x2F4 == 0x00000001`
  - Turn 2 (Magic Room toggled off): `candidate_0x2E8 == 0x00000000`, `legacy_0x2F4 == 0x00000001` (stuck on 1 due to battler 0 controller exec)
- **Trick Room (move 433)**: Turn 1 sets `candidate_0x2E8 == 0x00000002`, `legacy_0x2F4 == 0x00000001`.
- **Electric Terrain (move 604)**: Turn 2 sets bit 8 (`0x100`, `STATUS_FIELD_ELECTRIC_TERRAIN`); because Trick Room is still active from Turn 1, `candidate_0x2E8 == 0x00000102` (combined state `0x100 | 0x2`), proving Electric Terrain adds bit `0x100`. `legacy_0x2F4 == 0x00000001`.
- **Field Isolation across Weather & Screens (Scenario 64)**:
  - Turn 1 (Rain Dance, move 240): `candidate_0x2E8 == 0x00000000`, `legacy_0x2F4 == 0x00000001`. Demonstrates that weather activation does not mutate or bleed into `gFieldStatuses`.
  - Turn 2 (Reflect, move 115): `candidate_0x2E8 == 0x00000000`, `legacy_0x2F4 == 0x00000001`. Demonstrates that side status activation does not mutate or bleed into `gFieldStatuses`.
  - Turn 3 (Magic Room, move 478): `candidate_0x2E8 == 0x00000001`, `legacy_0x2F4 == 0x00000001`. Confirms positive field transition occurs independently at `0x2E8`.

#### 15.7.4 Retained evidence artifacts
All evidence is checked into the repository and bound to the official release ROM SHA-256 (`edf76ecf...`):
- `tools/hns-runtime-probe/evidence/hns205-field-status-positive-transitions.log`: full execution log of Scenario 63 (0 -> 1 -> 0 for Magic Room, 2 for Trick Room, bit 0x100 set / 0x102 combined for Electric Terrain, with memory window dumps).
- `tools/hns-runtime-probe/evidence/hns205-field-weather-screens-isolation.log`: full execution log of Scenario 64 (field isolation across weather and side-status move executions).
- `tools/hns-runtime-probe/scenarios/63-field-status-transitions.txt`: reproducible scripted probe scenario.
- `tools/hns-runtime-probe/scenarios/64-field-weather-screens-isolation.txt`: reproducible field isolation scenario across weather and side status moves.
- `tools/hns-runtime-probe/evidence/hns205-field-layout-symbols.txt`: exact symbol map extract from `upstream-hns/pokehns-expansion/pokehns.map`, disassembly excerpts of `IsBattleControllerActive` (`0x08056C00`) and field status AI (`0x08006874`), and literal pool frequency counts.
- `tools/hns-runtime-probe/prepare_field_test_save.py`: helper script to generate test saves with target moves.

#### 15.7.5 Device-shaped regression & anti-regression tests
`native/tests/test_pokemon_reader.c` adds `test_hns_field_statuses_thor_regression`, which reproduces the Thor device state:
- Sets `gBattleControllerExecFlags` (`0x2F4`) = `0x00000001`.
- Sets `gFieldStatuses` (`0x2E8`) = `0x00000000`.
- Asserts the reader reports `field_statuses == 0` (clean field).
- Asserts an anti-regression check that legacy `0x2F4` would have read `0x00000001` (Magic Room).
- Tests positive controls (Magic Room = 1, Trick Room = 2, Electric Terrain = 0x100 at `0x2E8`) with controller active and idle.

#### 15.7.6 Evidence tiers
- `gFieldStatuses` @ `EWRAM + 0x2E8`: **RELEASE SYMBOL + POSITIVE RUNTIME VERIFIED** (0 -> 1 -> 0 for Magic Room, 2 for Trick Room, bit 0x100 set / 0x102 combined for Electric Terrain; retained in `hns205-field-status-positive-transitions.log`)
- `gBattleWeather` @ `EWRAM + 0x390`: **SOURCE + HOST REASONED** (neutral clear-weather 0x0000 RUNTIME VERIFIED in `golden-c4e-live-operands.log`; active rain execution isolated from `0x2E8` in Scenario 64; active ordinary Rain/Sun is conditionally production-authorized but lacks retained positive runtime verification, while unsupported weather bits continue to fail closed)
- `gSideStatuses` @ `EWRAM + 0x324`: **SOURCE + HOST REASONED** (neutral screenless defender side RUNTIME VERIFIED in `golden-c4e-live-operands.log`; active reflect execution isolated from `0x2E8` in Scenario 64; active ordinary Reflect/Light Screen is conditionally production-authorized but lacks retained positive runtime verification, while unsupported side-status bits continue to fail closed)
- `gBattleControllerExecFlags` @ `EWRAM + 0x2F4`: **RELEASE SYMBOL + RUNTIME VERIFIED** (0x300 withdrawn; reconciled with §11.5/§11.6)


## 16. Group B live-state writers (#88)

[`HNS_2_0_5_LIVE_STATE_AUTHORITY_MATRIX.md`](HNS_2_0_5_LIVE_STATE_AUTHORITY_MATRIX.md) is the
single provenance matrix for current stats/stages, HP/status, effective ability and types, weather,
field/terrain, persistent volatiles, charge and boost payloads, gimmick/form, species, and held item.
It names each pinned H&S field, the native/Kotlin observation and slot check, the boundary binding,
and whether `calculateHnsDamage` consumes that operand.

Group B clears stage writers only after the exact live stages are rebound, ordinary Drizzle/Drought
only for supported unsuppressed clear/Rain/Sun, and type/ability replacements only under the
request-local evidence in the matrix. It does not treat captured terrain or power flags as
calculated effects. Subsequent Group D slices model terrain, Charge, Flash Fire and Paradox
payloads request-locally; see [the current state-backed contract](HNS_STATE_BACKED_GROUP_D.md).
Issue #92 resolves Booster Energy's request-local item rule from the current numeric item and the
settled Transform/activation/boosted-stat payload; unread or contradictory state remains blocked.
Protean/Libero are
narrower than Color Change/Mimicry because their pre-damage type change can occur after the state
snapshot and `usedProteanLibero` is not observed.

## 17. Issue #91 — move-type rewrites and `ateBoost`

This section records the current implementation after the #91 move-type slice. The pinned source
audit follows the actual H&S order:

```text
selected move
→ GetMoveType source type (including Fairy-off alternate)
→ GetDynamicMoveType ability rewrite
→ SetTypeBeforeUsingMove later Electrify / Ion Deluge rewrite
→ GetBattleMoveType effective type
→ GetBattleMoveCategory (TYPE_BASED reads the effective type)
→ CalcMoveBasePowerAfterModifiers (explicit ateBoost)
→ STAB and type effectiveness in the damage path
```

The request-local `HnsMoveAuthority` resolves the pinned move identity, source type, authoritative
live attacker ability, source-derived move flags, and live rewrite operands once. Its effective type
flows through ability relevance, QuickJS JSON, TYPE_BASED category, STAB, type effectiveness,
Group C immunity, type-sensitive base-power abilities, and supported type-power items. The engine
input includes the exact `ateBoost` fact; QuickJS does not infer it from ability name and final type.

Pinned H&S behavior is:

| Ability | Source predicate on the supported ordinary `EFFECT_HIT` path | Result | `ateBoost` |
|---|---|---|---|
| Normalize | Its own branch, except the pinned item / terrain / variable-type effects and Z moves | Normal, even when the source type is already Normal | Set; the configured multiplier branch applies |
| Refrigerate | Source type is Normal | Ice | Set only when rewritten |
| Pixilate | Source type is Normal | Fairy | Set only when rewritten |
| Aerilate | Source type is Normal | Flying | Set only when rewritten |
| Galvanize | Source type is Normal | Electric | Set only when rewritten |
| Liquid Voice | Pinned `IsSoundMove(move)` (`MoveInfo.soundMove`) | Water | Not set; no separate BP multiplier exists |

Pinned source references: `GetDynamicMoveType`, `TrySetAteType` and `SetTypeBeforeUsingMove`
ordering are in `src/battle_main.c:6128-6412, 6418-6449`; `IsSoundMove` reads the source
`soundMove` field in `include/move.h:358-360`; `GetBattleMoveCategory` is in
`src/battle_util.c:9171-9184`; the -ate and Normalize base-power predicates are in
`src/battle_util.c:6715-6732`. The pinned config defines `B_ATE_MULTIPLIER GEN_LATEST`
(`include/config/battle.h:198`), so both the -ate branch and Normalize's Gen-7-or-later branch use
×1.2. `entry.js` composes this with the existing half-up UQ4.12 base-power accumulator before it
applies the integer source power.

Every supported rewrite still requires a source-proven ordinary damaging move. Hidden Power,
Weather Ball, Natural Gift, Terrain Pulse, item-typed effects and other nonordinary move effects
remain blocked by the existing move-mechanics gate. Active/unread Z, Max or Tera gimmicks remain
unknown/refused. Conditional or computed `soundMove` metadata remains UNKNOWN; literal true and
zero-initialized omitted false values reuse the existing generated `Hns205MoveEffects` inventory.

Fairy mode is applied before the ability predicate. Fairy Wind's pinned Fairy-off alternate is
Normal, so Pixilate rewrites it to Fairy and sets `ateBoost`; with Fairy enabled, the source move is
already Fairy, so Pixilate does not run and `ateBoost` stays false. Pixilate's target remains Fairy
when Fairy mode is OFF. Electrify and Ion Deluge remain later in the authority ordering, and their
active production blockers are unchanged.

## 18. Ordinary terrain move modifiers (request-local)

Terrain support remains request-local; it is not a general terrain simulation. The selected-hit
base-power stage now models the four pinned `CalcMoveBasePowerAfterModifiers` terrain branches when
the live H&S field word and the relevant battler's terrain applicability are authoritative:

| Terrain | Subject checked by H&S | Final effective move type | UQ4.12 factor | Pinned source |
|---|---|---|---:|---|
| Grassy | attacker | Grass | `addHalfUp(5325)` (×1.3) | `src/battle_util.c:6639` |
| Misty | defender | Dragon | `addHalfUp(2048)` (×0.5) | `src/battle_util.c:6641` |
| Electric | attacker | Electric | `addHalfUp(5325)` (×1.3) | `src/battle_util.c:6643` |
| Psychic | attacker | Psychic | `addHalfUp(5325)` (×1.3) | `src/battle_util.c:6645` |

The pinned `B_TERRAIN_TYPE_BOOST` is `GEN_LATEST`; the source's `UQ_4_12(1.3)` therefore resolves
to 5325, while Misty's `UQ_4_12(0.5)` is 2048. Each factor is composed by the existing half-up
UQ4.12 accumulator before conversion to integer base power. The modifiers use the boundary-owned
`field.hnsFieldStatuses` and do not consult caller terrain strings or terrain-setting abilities.

`HnsTerrainAuthority` is the shared `IsBattlerTerrainAffected` authority for field, ability, and
item policies. It requires an active raw terrain bit, observed battler ability/item/effective types
and volatile state, and no semi-invulnerable state. The pinned `IsBattlerGrounded` precedence is
preserved for supported neutral volatile states: Iron Ball grounds first, Gravity grounds before
Air Balloon/Levitate/Flying, then Air Balloon and Levitate unground, then Flying type; otherwise
the battler is grounded. Gravity therefore overrides Levitate, Air Balloon, and Flying typing.
Positive root, Smack Down, Telekinesis, Magnet Rise, Gastro Acid, Roost, and semi-invulnerable
observations remain fail-closed under the existing volatile gate. The ordinary-move allow-list does
not prove a defender cannot retain a semi-invulnerable state from another turn, so an observed
semi-invulnerable flag returns UNKNOWN rather than assuming false. Levitate suppression and
Magic Room's unresolved grounding-item suppression also remain UNKNOWN where those operands are
ambiguous.

The whole-bit field contract still applies: Psychic Terrain remains relevant for unproven positive
priority; Grassy Glide's turn-order effect remains deferred; Gravity is modelled only when its
grounding override is the exact terrain consequence and no other Gravity consequence applies.
Electric Terrain can compose with Hadron Engine's raw-bit Special Attack effect independently of
grounding. Grassy Terrain can compose with Grass Pelt when both are exact. Remaining deferred
terrain mechanics include terrain priority blocking, Grassy Glide, Rising Voltage, Expanding
Force, Terrain Pulse, Misty Explosion, Psyblade, terrain-dependent Nature Power, Grassy Terrain's
Earthquake/Magnitude reduction, terrain seeds, Surge Surfer, Analytic and turn-order effects,
terrain-setting ability events, and expiration/timer simulation. Quark Drive and Booster Energy's
selected-hit Paradox stat payload are resolved only for the source-backed states in issue #92; this
does not model their other terrain consequences.

### Census impact

The full census regenerated from starting `main` SHA
`5883f129213c1fdd1671982853f5395584de1764` is unchanged by this runtime slice. The fixed trainer
inventory has no active terrain, so the checked-in machine-readable census bytes remain identical
after regeneration.

| Metric | Starting main | This slice | Change |
|---|---:|---:|---:|
| `FULLY_MODELLED` / `CAVEATED_ESTIMATE` / `REFUSED` | 18,800 / 372 / 5,106 | 18,800 / 372 / 5,106 | unchanged |
| Fully displaying lead matchups | 372 / 1,302 | 372 / 1,302 | unchanged |
| Displayable lead requests | 6,676 | 6,676 | unchanged |
| `HNS_FIELD_STATUS_NOT_MODELLED` | 0 battles / 0 requests | 0 / 0 | unchanged |
| `HNS_ABILITY_EFFECT_NOT_MODELLED` | 247 battles / 1,870 requests | 247 / 1,870 | unchanged |
| Random Abilities trials (refused / caveated / clear) | 1,297,908 / 16,820 / 2,462,932 | 1,297,908 / 16,820 / 2,462,932 | unchanged |

Item blockers are unchanged as well. The JSON gzip was regenerated by the canonical full-census run;
its bytes match starting `main`, so there is no machine-readable census file change to commit.


## State-backed Group D (after PR #115)

[The complete source/runtime/operator/census handoff](HNS_STATE_BACKED_GROUP_D.md) is the current
contract for Flash Fire, Rivalry, Slow Start, Analytic, Stakeout, Gorilla Tactics, Protosynthesis,
Quark Drive, Supreme Overlord, both Auras, Aura Break and all four Ruin abilities. Earlier
historical sections describing these operands as unread are superseded by that contract.

New observations are live personality, the Group D volatile payloads including Neutralizing Gas,
raw two-bit first-turn state, the stored Supreme counter, selected Dynamax and phase-proven current
turn order/current move. Generated gender ratios complete the immutable species authority.
Existing raw stats/stages, current species/types/ability/item, HP, weather, terrain, Wonder Room,
active gimmick and Gastro Acid are reused. New fields never default to neutral at the boundary.

The release callback binding correction is part of this slice: source-build symbols were
previously mislabelled as release addresses. Generated instruction matching and retained official
ROM transitions now bind the live dispatcher and invalidate Analytic at menus and turn changes.
The release disables player Dynamax; selected=true is covered by source/native/engine-only
controls, with no claim of a positive official-ROM transition.

The oracle is 1,720 scenarios / all 16 rolls / two existing #100 divergences. Full census totals
are 18,916 fully modelled, 388 caveated, 4,974 refused; per-ability changes and remaining blockers
are in the handoff. #92 items, Doubles partners, Parental Bond, Skill Link and full gimmick move
semantics remain outside the supported contract.

## Group D held items (issue #92)

The issue #92 item slice now implements the pinned ordinary selected-hit factors using one shared
`HnsHoldEffectAuthority` for Kotlin policy and QuickJS. It distinguishes current numeric item
identity from the effective hold effect and fails closed when Magic Room, Embargo, Klutz/Gastro
Acid, Ability Shield or Neutralizing Gas operands are unread. Embargo, the Metronome item counter
and transformed source species are read from generated volatile fields; the volatile window is
42 bytes and the native/JNI tuple is 103 integers.

The exact factors, source order, supported families, form/state rules, Pledge/OHKO exclusions,
request-local Mega/Z clearance, audit/census deltas, and official-ROM transition evidence are in
[HNS_GROUP_D_HELD_ITEMS.md](HNS_GROUP_D_HELD_ITEMS.md). Issue #93 remains the follow-up for rare
mechanics; issue #83 stays open.


## Group B live-state writer completion

The source-backed, request-local live-state writer dispositions and current timing contracts are recorded in [`HNS_GROUP_B_CLOSURE.md`](HNS_GROUP_B_CLOSURE.md). A cleared writer removes only its own ability/item attribution; unsupported moves, weather formulas, field states, and format remain independent refusals.

## Group E explicit dispositions — issue #93

The current [Group E closure audit](HNS_GROUP_E_CLOSURE.md) and its
[generated identity matrix](HNS_GROUP_E_MATRIX.md) supersede generic unsupported ability/item
messages for every remaining audited identity. `tools/hns-group-e/decisions.json` is the reviewed
source for production tiers and named reasons; CI reconciles it against both generated inventories.
Request-local proofs still run first; UNKNOWN never becomes a caveat.

Charge's exact base-power stage now consumes the observed shared `chargeTimer`. The pinned Wind
Power / Electromorphosis script explicitly writes that same volatile, resolving the Group B handoff
without a new memory operand. Sturdy's potential survival cap receives a labelled base-damage
estimate on ordinary full-HP hits. Truant's unread execution branch and unresolved Ability Shield
suppression now explicitly refuse. e-Reader Enigma Berry is audited unsupported, with a named
runtime-effect refusal. Mega/Z hold-effect suppression cannot bypass the selected/active NONE proof.

Ambiguous source display names such as Pikachu and Terapagos can resolve through boundary-owned,
slot-matched numeric species IDs with matching catalogue names. Caller overrides, species defaults,
and missing/stale IDs do not substitute for that authority. Existing live types/stats still drive damage.

The full census improves from 19,336 fully modelled / 224 caveated / 4,718 refused requests to
19,568 / 446 / 4,264 over the same 24,278 requests. Entirely refused battles fall from 12 to 9;
all remaining ones are Doubles. Therefore **#93 and #83 are not ready to close**. The detailed audit
records all hard-refusal families, named request causes, Random Abilities before/after totals and
remaining no-result battles. The 1,845-case oracle retains only the two existing #100 divergences.


## 21. Authoritative ordinary-hit Doubles subset (PR #119)

The historical Singles-only format gates above are superseded for ordinary
opposing hits with complete live authority. The current participant ABI has
**165 words total**: `[0..161]` remain the unchanged legacy/Doubles prefix, and
`[162..164]` are the additive Heal Block extension. The minimal four-index Doubles
packet remains version **1**, with no version bump, as described in
[HNS_DOUBLES_AUTHORITY.md](HNS_DOUBLES_AUTHORITY.md). Native indexed observations,
actual position/party mapping, HP/presence, source battle-type flags and repeated
matching partner/global reads replace the prior per-side ambiguity for explicit
selected participants. The legacy role APIs remain ambiguous in four-battler play.
Battle and Calc let the user select the observed attacker/target; no flank is guessed.

The boundary owns target counts and effective partner/global operands. Supported
classes use absent flags for BOTH/FOES_AND_ALLY, selected live HP/presence for
SELECTED/DEPENDS/OPPONENT, attacker liveness for USER and the pinned constant for
OPPONENTS_FIELD. Random target identity still refuses even with a known count.
Surf and other independently unsupported moves do not become supported merely
because their target class can be counted. Petal Blizzard covers ally-inclusive
ordinary spread counts 3 and 2: Gen III applies no reduction at 3 and halves at 2.

Exact arithmetic includes source spread, Doubles screens (2732/4096 in Other),
Helping Hand stacks, Battery/Power Spot/Steely Spirit, Plus/Minus, partner Flower
Gift, Friend Guard, field aura/Aura Break, all-slot Ruin and live Cloud Nine/Air
Lock weather suppression. The audit lists every pipeline slot and rounding mode.
No generic format blocker is emitted for an agreed supported four-battler shape;
missing tuple/partner/target/suppression state remains a hard refusal. Active Gas,
Mold Breaker action suppression, ignored-target-ability moves, Commander, combined
Pledge, unresolved redirection/random/ally targets, partner priority protection
with positive/unread effective priority, and partner Flower Gift + unresolved
Utility Umbrella activation remain unsupported. Aurora Veil and complex moves,
multi-hit totals and gimmicks retain independent gates.

Plus/Minus gain an exact request-local Doubles rule; Friend Guard, Battery, Power
Spot and Telepathy holders can be irrelevant for their own opposing hit while
partner effects are consumed separately. Global Group E dispositions and prior
Singles proofs remain unchanged. Costar, Hospitality, Dancer and Commander gain
no general Doubles clearance. Exact Plus/Minus remains active even beside another
caveated ability, as verified through the production boundary and shipped bundle.

The unchanged 24,278-key census now reports **19,856 fully modelled / 462 caveated /
3,960 refused**, zero no-result battles, all 642 Singles battles displaying and all
nine Doubles battles displaying at least one request. Fully displayable Doubles
lead pairs are 6/18; independent move/item blockers keep the other pairs incomplete.
The documented neutral host context explicitly supplies neutral partner observations;
production derives no partner effect from trainer or census data. The 49 new pinned
oracle cases bring the corpus to 1,894 scenarios, all matching across all 16 rolls,
with all 1,845 historical entry objects unchanged and the two #100 cases resolved.

See [Group E closure](HNS_GROUP_E_CLOSURE.md) for each original battle's first
new display, remaining blockers, before/after counts and tracker audit. #93 is
ready to close after reviewed merge and green final checks. #83 additionally has
unfinished #85 debug logging/export acceptance; stale Group A/C/oracle issue states
and Group D checkboxes must be distinguished from that genuine remaining work.

### PR #119 review correction: settled Doubles entry state

Doubles additionally requires the boundary-owned switch-in settlement proof to
be **true** before deriving any partner/global operand. The native proof already
reads the completed event sentinel, all four count-slot switch-in flags and the
stable action-selection callback. A full packet can precede an Intimidate, Drizzle
or Trace partner entry effect; it cannot substitute for that phase proof.
False, unread or disagreeing observations emit hard refusal
`HNS_DOUBLES_SWITCH_IN_UNSETTLED` with a named switch-in-state message. Existing
Singles rules, arithmetic and tuple layout are unchanged. Boundary regressions
cover both sides, pending/unread/disagreeing phases, prior authorized caller
state and partner writers; native regressions cover each partner flag and the
earlier replacement callback window. The census already assumes settled entry
state, so this gate does not require fixture or generated-artifact changes.

## Passive debug playtest coverage (#85)

The Battle presenter observes the final structured `CalcRequestOutcome` immediately after
`CalcRequestBoundary.build`; telemetry never changes the request, verdict or arithmetic.
Debug source sets supply a local atomic JSON store, per-battle deduplication, blocker aggregation
and a narrow share-sheet cache export. Release resolves directly to a no-op and contains none
of that implementation or UI/provider configuration. See [contributor export instructions](../CONTRIBUTING.md#exporting-hs-calculator-playtest-coverage)
for collection, schema/count semantics, caps, export and the automated release proof.

## Fixed single-hit drain in Singles (slice 2, #124)

The separate source-generated `FIXED_SINGLE_HIT_DRAIN` category admits Absorb,
Mega Drain, Leech Life, Giga Drain, Drain Punch, Horn Leech and Draining Kiss only
in authoritative Singles. The ordinary set stays unchanged. The existing fixed-hit
arithmetic is reused, with an observed pre-hit Heal Block operand and exact Triage
+3 effective priority. Active/unread Heal Block and priority-prevented drain hits
are hard refusals. Big Root and Liquid Ooze receive request-local post-hit proofs;
no healing or attacker survival is displayed. Old runtime tuples cannot authorize
this family. H&S remains ESTIMATED. See [slice 2 evidence](HNS_MOVE_COVERAGE_SLICE_2.md)
for source contracts, consumer audit, oracle execution/damage proofs, regressions,
unchanged-population census and hardware NOT_RUN checklist.


## Move coverage slice 3 — authoritative semi-state

Earthquake (89) and Bulldoze (523) have a separate fixed-single-hit category in
observed Singles. Both admit source `STATE_NONE`; Earthquake additionally admits
`STATE_UNDERGROUND`. JNI's existing index 51 is preserved raw and checked against
the generated 0..6 domain. Raw 7 makes the observation invalid; unread never means
neutral. Exact trust, current OBSERVED battler, matching slot and observed volatile
window bind `defenderSemiInvulnerableState` through `CalcRequestBoundary`.
Caller state cannot override it. Other positive states and Bulldoze underground
are hard execution refusals; Doubles and adjacent flag-sharing families stay out.

The raw Grassy Terrain bit halves this family's BP only for a neutral semi-state,
before Gems/ordinary terrain/abilities, independently of groundedness. Underground
Earthquake skips that reduction and adds Q12 ×2 in the ordered other-modifier
accumulator before screens/abilities/items. Bulldoze uses the existing source-backed
Sheer Force slot. Ground immunity remains independent. H&S remains ESTIMATED;
hardware NOT_RUN. See [slice 3 source, authority and evidence](HNS_MOVE_COVERAGE_SLICE_3.md).


## Move coverage slice 7 — authoritative Brine HP

Brine (362, EFFECT_BRINE) has a separate Singles fixed-hit category. Exact-trusted,
current slot-matched defender HP/maxHP must satisfy 1 <= HP <= maxHP, with positive
source-domain maxHP; unread/invalid operands refuse with HNS_DEFENDER_HP_UNKNOWN.
The predicate HP <= floor(maxHP/2) adds 8192 in the existing Q12 base-power
accumulator's move-effect slot, before later modifiers, while source BP stays 65.
Odd maxHP 101 boosts at HP 50, never HP 51. Technician and Sheer Force are inactive.
The result uses the currently observed battle state; later healing/damage/actions
are not predicted. Substitute, positive semi-states and Doubles remain refused.
H&S remains ESTIMATED. See [slice 7 evidence](HNS_MOVE_COVERAGE_SLICE_7.md).
