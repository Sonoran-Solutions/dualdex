# H&S 2.0.5: state-backed Group D

This slice starts at `main` **724916188e6aa9530be7f961a530c7505feb9306** (PR #115).
Mechanics and structures are pinned to `PokemonHnS-Development/pokehns-expansion`
**1f42b74dff0e9fe942419845d040663dd829a973**. Runtime evidence uses official ROM SHA-256
**edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b**.

## Newly observed operands

`tools/hns-layout/generate_hns_live_battle_layout.py` compiles ARM designated initializers,
`offsetof`, `sizeof`, and enum constants against the pinned headers. It generates
`native/src/hns_live_battle_layout_gen.h`, `HnsGroupDLayout.kt`, and `group_d_domains.json`.
All bit offsets below are relative to `BattlePokemon.volatiles`, at BattlePokemon byte 84.

| Source operand | Generated layout/domain |
|---|---|
| `BattlePokemon.personality` | byte 76, size 4, unsigned bit pattern preserved through JNI |
| `Volatiles.slowStartTimer` | bit 264, width 3, raw 0–7 |
| `flashFireBoosted` | bit 307, width 1 |
| `transformed` | bit 37, width 1 |
| `boosterEnergyActivated` | bit 308, width 1 |
| `paradoxBoostedStat` | bit 328, width 3; valid enum values 0–5 (`NUM_STATS=6`), 6/7 invalid |
| `vesselOfRuin`, `swordOfRuin`, `tabletsOfRuin`, `beadsOfRuin` | bits 91, 92, 93, 94 |
| `neutralizingGas` | bit 320; needed by the source Ruin/global-suppression predicates |
| `BattleStruct.battlerState[b].isFirstTurn` | array byte 0, stride 12; bit 82, width 2, raw 0–3 |
| `BattleStruct.supremeOverlordCounter[b]` | byte 864, stride 1, count 4; only 0–5 valid |
| `BattleStruct.gimmick.usableGimmick[b]` | gimmick byte 668 + byte 0 + battler index |
| `BattleStruct.gimmick.playerSelect` | gimmick byte 668 + byte 4; Boolean |
| selected Dynamax | the preceding two operands, `usableGimmick == 4 && playerSelect` |
| `gMain.callback1` | compiled Main offset 0; existing release-verified gMain base |
| current-action globals | table below; published as UNKNOWN/LAST/NOT_LAST plus current move |
| species `genderRatio` | source-generated data for every H&S species/form record |

The volatile window expands **41 → 42 bytes**, the minimum covering bit 330.
Existing active gimmick storage (gimmick byte 11, side × 6 + party slot) is reused.

The backwards-compatible JNI tuple expands **76 → 97 integers**:

- 76–77: personality observed/value;
- 78–86: Slow Start, Flash Fire, transformed, Booster activation, Paradox selector, four Ruin flags;
- 87–90: first-turn observed/raw, Supreme counter observed/raw;
- 91–94: selected Dynamax observed/result, Analytic observed/result;
- 95–96: Neutralizing Gas volatile, current move bound to Analytic.

The volatile observation flag and tuple length jointly authorize volatile payloads. Other new
families have their own flags. Short tuples, failed reads, malformed Boolean flags, invalid
selectors/counters, mismatched slots, fainted participants and untrusted builds cannot become
neutral observations. An observed zero remains distinct from an unread value. Vanilla readers
and their tuple consumers retain their existing behavior.

## Analytic: release binding and current-turn proof

Runtime probing found that the earlier `gBattleMainFunc=0x03002F5C` and action-selection
`0x080893D9` claims actually described a **source build**, not the official ROM. That evidence is
withdrawn. Merely reading a plausible code pointer did not establish its identity.

`generate_hns_release_phase_evidence.py` now uniquely matches named pinned-source Thumb function
prefixes against the SHA-pinned release. Only call targets, branch displacements and PC-relative
literal distances are masked; instruction/register structure is retained. It extracts the
`gBattleMainFunc` storage address from the actual `BattleMainCB1` literal load. The generated
`release_phase_evidence.json` records unique-match counts, normalized instruction hashes,
source-file hashes, source-build hashes, and the release hash. No ROM bytes are committed.
Live input traces independently confirm the resulting phase transitions.

| Identity | Verified release address |
|---|---:|
| `BattleMainCB1` Thumb callback | `0x080822F1` |
| `gBattleMainFunc` storage | `0x03002F74` |
| `HandleTurnActionSelectionState` Thumb callback | `0x08088DED` |
| `RunTurnActionsFunctions` Thumb callback | `0x0808AA35` |
| `gBattlersCount` | `0x020000B0` |
| `gBattlerAttacker` / `gCurrentActionFuncId` | `0x02000124` / `0x02000125` |
| `gCurrentTurnActionNumber` / `gActionsByTurnOrder` | `0x02000302` / `0x02000304` |
| `gBattlerByTurnOrder` / `gCurrentMove` | `0x020003B8` / `0x020003A0` |

The native authority requires the **executing BattleMainCB1**, RunTurnActionsFunctions, and
`B_ACTION_EXEC_SCRIPT=10`. Pinned `HandleAction_UseMove` establishes the attacking battler and
current move before entering that action. The current index must name the requested attacker,
whose action is `B_ACTION_USE_MOVE=0`; Singles count and unique/in-range order must be valid.
A living, non-absent later battler with USE_MOVE means NOT_LAST; later non-moves, absent battlers,
and fainted battlers are skipped. The Kotlin boundary also requires the observed current move ID
to equal the pinned selected move ID. Menus, suspended dispatchers, turn setup, mismatches and
missing reads remain UNKNOWN. No Speed prediction or preceding-turn fallback exists.

Native tests exercise all six requested turn-order controls plus suspended dispatch. Retained
ROM traces prove positive/negative action results, Tackle move binding, menu UNKNOWN, and
invalidation between turns. The move generator also asserts that every `EFFECT_FUTURE_SIGHT`
move stays outside the ordinary surface.

## Exact mechanics

All factors are UQ4.12 integers. Normal `uq4_12_multiply` is half-up; `half_down` is the distinct
pinned operator. Factors enter their source accumulator, before that stage's integer conversion.

| Ability | Stage | Factor and operator | Authoritative predicate |
|---|---|---|---|
| Rivalry 79 | base power | 5120 / 3072, normal | same/opposite non-genderless live gender |
| Analytic 148 | base power | 5325, normal | phase-proven LAST for this move |
| Supreme Overlord 293 | base power | 4096, 4506, 4915, 5325, 5734, 6144; normal | stored counter 0–5 |
| Dark Aura 186 / Fairy Aura 187 | base power, field abilities | 5448, normal | matching final Dark/Fairy type and an active aura on either participant |
| Aura Break 188 | same field branch | 3072, normal | replaces the matching aura factor; neutral without an aura |
| Slow Start 112 | selected Attack | 2048, half-down | Physical and positive timer |
| Flash Fire 18 | selected Attack | 6144, half-down | final Fire type and observed boost flag |
| Stakeout 198 | selected Attack | 8192, half-down | defender raw first-turn value exactly 2 |
| Gorilla Tactics 255 | selected Attack | 6144, normal | Physical, neither selected nor active Dynamax |
| Protosynthesis 281 / Quark Drive 282 | selected Attack or Defense | 5325, normal | active, untransformed, matching boosted stat |
| Vessel 284 / Tablets 286 | Special/Physical Attack, field effects | 3072, half-down | active Ruin flag elsewhere, subject not self-excluded |
| Sword 285 / Beads 287 | Defense/Sp. Def, field effects | 3072, half-down | same authority; Wonder Room selects the raw defensive stat |

Source locations are `battle_util.c`'s `CalcMoveBasePower`, `GetSupremeOverlordModifier`,
`CalcAttackStat`, `CalcDefenseStat`, `IsRuinStatusActive`, `GetParadoxHighestStatId`,
`GetParadoxBoostedStatId`, and `IsLastMonToMove` at the pinned commit.

**Rivalry:** current battle species/form plus live personality select the generated gender ratio.
Ratios 0/254/255 give male/female/genderless directly; otherwise `ratio > (personality & 255)`
means female. Genderless is neutral; missing species/ratio/personality is unknown. Party/display
gender is never used.

**Supreme Overlord:** the stored battler counter is read directly. Switch-in stores
`min(5, side faint counter)`; a later party faint-count reconstruction would change semantics.
DualDex rejects invalid values instead of clamping them. Factors reproduce
`4096 + PercentToUQ4_12(counter * 10)` exactly.

**Gorilla:** selected and active gimmicks are independent observations. Selected/active Dynamax
makes the ability factor inactive but retains the independent gimmick refusal. Other unsupported
gimmicks also retain their own blocker. The official release sets `B_FLAG_DYNAMAX_BATTLE=0`, so
`CanDynamax` always rejects player activation. Runtime proves observed selected=false/active=NONE;
no official-ROM selected=true transition is claimed. Positive selected/active controls are
compiled-layout native tests and engine-only oracle setups, including both the TESTING selector
and the release `playerSelect` operand. No patched ROM or fabricated RAM is presented as evidence.

**Paradox:** a nonzero stored selector wins. Zero recomputes staged raw stats in strict tie order
Attack, Defense, Sp. Atk, Sp. Def, Speed, replacing only on `>`. Wonder Room swaps the raw Defense
and Sp. Def inputs while retaining the source stage indices. Transformed is inactive. Proto uses
global effective Sun or the activation flag, without Utility Umbrella; Quark uses the Electric
Terrain bit or the flag. Both offensive and defensive category branches are covered. Consumed
ITEM_NONE plus observed activation is sufficient; a held Booster Energy remains subject to #92.

**Aura:** one shared Singles authority requires two living observed participants, authoritative
ability identities, and observed suppression state. Either side can supply Aura or Aura Break.
Unknown/global Gas suppression stays refused. Final effective move type is used.

**Ruin:** flags, not raw ability labels, drive the field effect. The subject's own matching flag
excludes it. Source Gastro Acid and Neutralizing Gas/Ability Shield checks are preserved. The
source Ruin loop does not independently filter holder HP; production restricts the topology to
two living observed participants. Global flags are required even when the holder's ability has
since been replaced. Positive Gas still retains the independent production suppression blocker.

## Runtime evidence and reproduction

See `tools/hns-runtime-probe/evidence/group-d/` and the probe README. The verifier checks retained
observations for timer 5→0, Flash Fire 0→1, raw first-turn 2/1/0, stored Supreme 0→1 after Memento,
Booster held→consumed with selector 2, Paradox clearing on replacement, Beads 0→1, selected/active
neutral Gorilla, and Analytic current-action/menu transitions. Every run has zero script errors
and invariant violations. Party fixtures are explicitly synthetic party data prepared in a copy
of a normally saved battery; the official ROM creates the battle payloads through ordinary input.
No runtime volatile, counter, turn order, expected damage, ROM or live RAM is patched.

These are reader/state-transition measurements. Exact damage is established separately by the
pinned-source differential oracle; the two kinds of evidence are not interchangeable.

## PR #115 corrections and oracle

Duplicate defender `hnsEffectiveItemId`, `status1`, and `hnsSpeciesId` writes are removed, retaining
the authoritative values. Flower Gift now has independent form, Sun, category, Umbrella and
weather-suppressor controls; the Sunshine form is retained in the controls meant to isolate
another predicate. Sand Force uses effective global weather with Cloud Nine/Air Lock suppression,
without holder Umbrella. Production Sandstorm remains independently unsupported.

The oracle contains **1,720 scenarios: 1,582 modelled, 138 engine-only**, each with all 16 rolls.
There are **1,718 exact comparisons and the same two registered #100 divergences**. It was
regenerated in canonical order and verified byte-identical in reversed order. Schema v7 records
the source-observed runtime payload at the pre-damage critical-hit RNG hook; details and the
TESTING/release selected-gimmick distinction are in the oracle README.

## Full census: exact starting main → this slice

Both sides were regenerated with `DUALDEX_CENSUS_GENERATE=true DUALDEX_CENSUS_FULL=true ./ci.sh all`.
The before run used an isolated checkout of the exact starting SHA. The neutral fixture now
explicitly observes new zero/false operands and personality 255, while Analytic remains UNKNOWN
at the menu. These are census inputs, never production defaults.

| Metric | Before | After | Change |
|---|---:|---:|---:|
| FULLY_MODELLED | 18,864 | 18,916 | +52 |
| CAVEATED_ESTIMATE | 388 | 388 | 0 |
| REFUSED | 5,026 | 4,974 | −52 |
| Displayable lead requests | 6,690 | 6,708 | +18 |
| Fully displaying lead pairs | 372 | 374 | +2 |
| Random Ability refused | 1,193,722 | 1,048,172 | −145,550 |
| Random Ability caveated | 16,820 | 16,820 | 0 |
| Random Ability clear | 2,567,118 | 2,712,668 | +145,550 |
| Ability-effect-not-modelled battles / requests | 218 / 1,674 | 217 / 1,650 | −1 / −24 |
| Ability-condition-unverified battles / requests | 82 / 286 | 90 / 316 | +8 / +30 |
| Old Flash Fire limitation battles / requests | 14 / 58 | 0 / 0 | −14 / −58 |

Random Ability counts below aggregate both roles/categories for each identity. They count the
ability's causal verdict; a clear ability verdict cannot remove an independent move/item blocker.
All caveated counts for these 16 identities remain zero.

| ID / ability | Refused before → after | Clear before → after | Refusal delta |
|---|---:|---:|---:|
| 18 Flash Fire | 0 → 1,876 | 12,186 → 10,310 | +1,876 |
| 79 Rivalry | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 112 Slow Start | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 148 Analytic | 12,186 → 9,100 | 0 → 3,086 | −3,086 |
| 198 Stakeout | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 255 Gorilla Tactics | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 281 Protosynthesis | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 282 Quark Drive | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 293 Supreme Overlord | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 186 Dark Aura | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 187 Fairy Aura | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 188 Aura Break | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 284 Vessel of Ruin | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 285 Sword of Ruin | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 286 Tablets of Ruin | 12,186 → 1,876 | 0 → 10,310 | −10,310 |
| 287 Beads of Ruin | 12,186 → 1,876 | 0 → 10,310 | −10,310 |

Flash Fire's +1,876 causal refusals are outside the proven ordinary move surface; they were already
independently refused by move policy. Its ordinary attacker boost now works, and the old dedicated
limitation is retired (enum retained for historical reports). Analytic still refuses an attacking
menu request rather than inventing turn order.

## Remaining contract before #92

All 16 identities are `MODELLED_HNS_CONDITIONAL`; the generated inventory is 81 conditional,
84 proven-no-damage-effect, 146 unsupported-damage-relevant, zero unclassified. Missing operands,
unknown phases, unsupported fields/status/grounding, global Gas, unsupported move shapes,
unknown items, and selected/active unsupported gimmicks continue to refuse independently.

All currently identified ordinary selected-hit H&S ability damage branches are either exact
request-locally or explicitly outside the supported contract. This is not blanket support for
all abilities or battle mechanics. No further ordinary Group D damage-factor implementation is
identified before #92. #92 held items (including pending Booster Energy), Doubles partner Battery /
Power Spot / Steely Spirit, Parental Bond, Skill Link, multi-hit totals and full gimmick move
semantics remain outside this slice. #83 and #92 stay open; #91 stays historical. Human review and
merge authority remain required.
