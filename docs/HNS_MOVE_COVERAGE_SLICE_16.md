# H&S move coverage Slice 16: Belch party Berry-consumption authority

**Starting main:** `cd81bcc42a7916b62a7d2649549de43aa98a0425` (PR #162 merged; Slice 15 complete).
**Pinned source:** `PokemonHnS-Development/pokehns-expansion`, release `Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.
**Issue:** [#163](https://github.com/Sonoran-Solutions/dualdex/issues/163) (open). Slice 15 issue [#160](https://github.com/Sonoran-Solutions/dualdex/issues/160) was closed with a completion comment.
**Status:** ESTIMATED. Hardware `NOT_RUN`. Original-engine Belch evidence **not produced in this slice** (see §15 and §19).

## Scope and claim boundary

Belch (move 562) is admitted only when the attacker's party member has an observed `PartyState.ateBerry == true` on its authoritative party slot. Nothing else authorizes it: not the held item, an empty item slot, `usedHeldItem`, inventory, or a reconstructed history.

Two kinds of evidence are kept apart throughout:

- **Source-proven** facts come from reading the pinned source and from source-digest contracts generated from it.
- **Executed** facts come from the native reader on synthetic EWRAM, Kotlin boundary tests, and the shipped QuickJS bundle. None of these is original-engine lifecycle evidence.

## 1. Starting main and dependency

- Starting main was verified with `git fetch origin`: `origin/main` = `cd81bcc42a7916b62a7d2649549de43aa98a0425`, the merge of PR #162.
- Commits between the Slice 14 head `d9aca83` and `cd81bcc`, all inspected: `440aa45` (CI redundancy reduction, PR #161 via merge `a80c7fb`), then the Slice 15 commits `18aa5de`, `d41f50f`, `f6dfedd`, and the merge `cd81bcc`. None touch Berry, party-state, or Belch code. Separately, `chore/reduce-ci-minutes` was fetched and not touched.
- Slice 15 admitted five semi-invulnerable damaging-turn previews. Its immune-response correction is preserved; see [HNS_MOVE_COVERAGE_SLICE_15.md](HNS_MOVE_COVERAGE_SLICE_15.md).

## 2. Issue and PR

- #160 was closed with a completion comment covering PR #162, the five admitted previews, the accepted immunity-response correction, ESTIMATED status, and hardware `NOT_RUN`.
- No dedicated Belch issue existed. [#163](https://github.com/Sonoran-Solutions/dualdex/issues/163) was created.
- Branch: `feat/hns-move-coverage-slice-16-belch`. The PR is left OPEN and UNMERGED for senior review; `Refs #163` is used rather than a closing keyword.

## 3. Frozen Belch descriptor

Source: `src/data/moves_info.h`, `[MOVE_BELCH]`. The generator freezes the whitespace-normalized entry digest `475e0fe0ad728f96ecba7baea8aefa98c733d8d173708cd1b39dfdb9ce03baae`.

| Field | Value |
|---|---|
| ID / effect | 562 / `EFFECT_BELCH` |
| Power / type / category | 120 / `TYPE_POISON` / `DAMAGE_CATEGORY_SPECIAL` |
| Accuracy / PP / priority | 90 / 10 / 0 |
| Target | `TARGET_SELECTED` |
| Contact / punching / ballistic | none (no flags) |
| Strikes | 1 (not repeated, not multi-hit) |
| Additional effects | none |
| Selection restrictions | `mirrorMoveBanned`, `meFirstBanned`, `metronomeBanned`, `mimicBanned`, `copycatBanned`, `sleepTalkBanned`, `instructBanned`, `assistBanned` (all `TRUE`) |

The source defines no sound-move flag for Belch, and none is invented. The generator fails if the digest, effect, power, type, category, accuracy, PP, target, priority, strike count, contact, or additional-effect set changes.

## 4. Source eligibility predicate and selection dependency

`src/battle_util.c`, `IsBelchPreventingMove`:

```c
if (GetMoveEffect(move) != EFFECT_BELCH) return FALSE;
return !GetBattlerPartyState(battler)->ateBerry;
```

Selection dependency (source-proven):

- The pinned source has **no function named `GetBattlerMoveLimitations`**. The limitation check is `CheckMoveLimitations(battler, unusableMoves, check)` at `src/battle_util.c:1665`, which tests `MOVE_LIMITATION_BELCH` (`1 << 11`) and calls `IsBelchPreventingMove`. The task's name does not exist at this pin; the generator freezes `CheckMoveLimitations` instead.
- Its callers pass `MOVE_LIMITATIONS_ALL` (`0xFFFF`) for AI, GUI, and selection. `battle_move_resolution.c:4188` masks out only PP and Choice-item limitations, so the Belch bit stays in force at execution.
- `TrySetCantSelectMoveBattleScript` (`src/battle_util.c:1399`, called from `battle_main.c:4662`) applies a second Belch gate. That gate is wrapped in `DYNAMAX_BYPASS_CHECK`, which is false while Dynamax is selected or active. Under Dynamax the source bypasses this Belch refusal. This slice therefore refuses Belch under any non-NONE gimmick, as the unsupported-gimmick rule requires.
- Disable, Choice locks, Taunt, Imprison, and Encore are not separately modelled for Belch in this slice. Existing policy applies, and nothing here claims immunity to them.

## 5. Complete Berry-state lifecycle (source audit)

All sites that read or write `PartyState.ateBerry` in the pinned source:

**Writers** (exactly these, enforced by the generator through `BELCH_BERRY_WRITERS = {battle_hold_effects.c: 1, battle_script_commands.c: 2}`):

1. `ItemBattleEffects` (`src/battle_hold_effects.c:1235`). Runs when a berry's effect fires (`if (effect)`), and sets the flag on `itemBattler`. A berry whose effect does not fire does not set it here.
2. `TryActivateWeaknessBerry` (`src/battle_script_commands.c:1495`). Sets the flag on the **defender** (`battlerDef`) when a super-effective weakness berry activates.
3. `BS_ConsumeBerry` (`src/battle_script_commands.c:14912`). Sets the flag on the battler before calling `ItemBattleEffects`, unconditionally for berry-pocket items. The Stuff Cheeks path reaches it through `gBattleScripting.overrideBerryRequirements`.

The reads are `IsBelchPreventingMove` (`battle_util.c:1393`, reached from the selection gate at `:1538` and `CheckMoveLimitations` at `:1719`, and from `battle_ai_util.c:692`), and `TryCheekPouch` (`battle_script_commands.c:6575`, which heals only after a berry was eaten).

**Resets**: no battle-side code clears `ateBerry`. The only other writes are in `src/dodrio_berry_picking.c`, which uses its own `sGame->players[]` struct, not `PartyState`.

**Allocation and teardown**: `AllocateBattleResources` allocates `gBattleStruct` with `AllocZeroed` (`src/battle_util2.c:23`) and frees it at `src/battle_util2.c:63`. `partyState` therefore starts at zero for every battle.

**Party-slot switching, fainting, replacement, voluntary switch, transformation**: no code in the battle sources clears or copies `partyState` on these transitions. Reads and writes index by `[side][gBattlerPartyIndexes[battler]]`, through `GetBattlerPartyState` (`include/battle.h:1152`).

Lifecycle claims proven from source:

1. A fresh battle starts with `ateBerry = FALSE` for every party member (zeroed struct).
2. A qualifying berry event sets the flag on the consuming party member, not the battler position.
3. The flag stays with that party member when it switches out and back in, because the entry is indexed by party slot.
4. Another party member does not inherit it, because the entry is per slot.
5. A new battle does not inherit it, because `gBattleStruct` is re-allocated zeroed.

There is no source exception to these claims. Berry restoration (Recycle) does **not** clear the flag, consistent with `test/battle/move_effect/belch.c`.

## 6. Layout authority

`tools/hns-layout/generate_hns_live_battle_layout.py` compiles compiler-backed probes with the pinned ARM toolchain and emits into `native/src/hns_live_battle_layout_gen.h`:

- `offsetof(struct BattleStruct, partyState)` = **48**
- `sizeof(struct PartyState)` = **8**
- side stride = **48** (`sizeof(partyState[0])`), sides = **2**, party size = **6**
- `PartyState.ateBerry` = **bit 2** (from a designated-initializer probe; `intrepidSwordBoost` is bit 0 and `dauntlessShieldBoost` is bit 1, matching declaration order)

No bit offset was hand-typed. The generator also guards that the bit lies inside the entry and that the side stride equals `party_count * entry_size`.

## 7. Native and JNI packet changes

Native (`native/include/pokemon_reader.h`, `native/src/pokemon_reader.c`):

- New `GameMemoryConfig` fields appended after `battle_gimmick_count`: `battle_struct_party_state_offset`, `party_state_entry_size`, `party_state_side_stride`, `party_state_side_count`, `party_state_party_count`, `party_state_ate_berry_bit`.
- New `BattlerRuntimeState` fields: `ate_berry_observed` and `ate_berry`. Both are read only inside the existing validated `gBattleStruct` pointer block.
- The read resolves the side from `battle.position & 1` and the slot from the already-authoritative `party_slot`. It reads one byte, `entry = bs_ptr + offset + side*stride + slot*entry_size + bit/8`, after an EWRAM bounds check.
- The layout-equivalence gate also compares the six new constants. A mismatched layout refuses the entire observation.

JNI (`app/src/main/cpp/dualdex_jni.c`): `BATTLER_RUNTIME_STATE_TUPLE_LEN` 178 → **180**. Indexes **178** (`ate_berry_observed`) and **179** (`ate_berry`) are appended. Indexes 0–177 are unchanged; `ATE_BERRY_TUPLE_LEN` in Kotlin pins the new length.

## 8. Three-state contract

| Live observation | Kotlin model (`HnsBattlerRuntimeState.ateBerry`) | Belch result |
|---|---|---|
| Observed `ateBerry == true`, authoritative slot | `true` | Eligibility established; exact selected-hit damage |
| Observed `ateBerry == false`, authoritative slot | `false` | Refused with `HNS_BELCH_BERRY_NOT_EATEN` |
| Unread, invalid, mismatched, or stale | `null` | Refused with `HNS_BELCH_BERRY_STATE_UNKNOWN` |

An observed false is never the unread default. Refusal is never a zero-damage result. A failed or out-of-domain read leaves the flag `null`, because the decoder accepts only `observed == 1` with a value in `0..1`.

The boundary copies `attackerRuntime?.ateBerry`. `authoritativeObservedRuntimeState` already requires `OBSERVED` status and a matching request party slot, so a stale party member cannot supply the flag.

## 9. Eligibility is not held-item identity

Covered by `HnsBelchProductionBoundaryTest`:

- A held Oran Berry with `ateBerry=false` is refused with `NOT_EATEN`. Holding a berry does not prove consumption.
- `ateBerry=true` with no held item is eligible. A consumed berry leaves no required item.
- Item-loss, knock-off, and restoration produce no Kotlin inference, because the decoder reads only the flag.

The inputs `usedHeldItem`, inventory, and empty item slots never reach the Belch decision.

## 10. Gimmick, format, and move-selection exclusions

The admission policy (`CalcCapabilityPolicy`, `FIXED_SINGLE_HIT_BELCH` block) refuses:

- any format other than Singles, or an observed battler count other than 2 (`HNS_MOVE_MECHANICS_NOT_MODELLED`);
- an active or selected gimmick (Dynamax, Tera, Z, Mega). An unread gimmick is `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`;
- defender semi-invulnerability, which QuickJS also requires to be NONE.

Trusted Singles selected-hit behavior is the only admitted execution path. No generic move-selection simulator was added.

## 11. Kotlin and shipped QuickJS admission

Kotlin (`DamageCalculator.kt`): the Belch packet carries `hnsMoveFamily = FIXED_SINGLE_HIT_BELCH`, the generated descriptor digest, the source fields, and `hnsAttackerAteBerry` inside the attacker object.

Shipped QuickJS (`tools/calc-bundler/entry.js`, regenerated bundle `app/src/main/assets/calc_bundle.js`) independently requires:

- `move.hnsMoveId === 562`, name `belch`, family and effect equal to the pinned metadata;
- `hnsDescriptorSha256` equal to the generated digest;
- fixed single hit, not ordinary, `hnsSourceStrikeCount === 1`, no multi-hit or repeated strike;
- no contact, punching, unknown-contact, or Sheer Force flags;
- `attacker.hnsAttackerAteBerry === true` (strict boolean; missing, `false`, or any other value throws);
- `attacker.hnsActiveGimmick === 0` and `hnsSelectedGimmick === 0`;
- `defender.hnsSemiInvulnerableState === 0`;
- Singles.

A contradictory or incomplete packet throws in the shipped bundle, and `calculateDamage` returns `success: false`.

## 12. Move-family generator

`tools/hns-move-mechanics/generate_hns_move_effects.py` adds `parse_belch_metadata`, `verify_belch_source_contract`, and the `BELCH_CONTRACTS` / `BELCH_PHASE_CONTRACTS` / `BELCH_BERRY_WRITERS` digests. It emits `Hns205MoveEffects.fixedSingleHitBelchMoveIds = setOf(562)`, its descriptor digest, and a `belchEligibility` record in `hns_move_damage_metadata.json`. The regeneration diff is three Kotlin lines plus the Belch JSON record, and `--verify` passes.

Phase digests frozen: `IsBelchPreventingMove`, `CheckMoveLimitations`, `TrySetCantSelectMoveBattleScript`, `BS_ConsumeBerry`, `TryActivateWeaknessBerry`, `ItemBattleEffects`.

## 13. Sixteen-roll damage

Belch uses the existing selected-hit H&S damage engine. No new formula, Berry multiplier, or repeated-strike result was added. The eligibility flag authorizes the move; it is not a damage modifier.

Executed in `HnsBelchProductionBoundaryTest` against the shipped bundle:

- an eligible neutral strike returns **16 damage rolls** with no `repeatedStrike` output;
- an immune target (Steelix, Poison vs Steel) with established eligibility returns **16 zero rolls**, `success: true`, a zero `maxDamage`.

Not executed: the pinned engine's own sixteen rolls for STAB, critical, Poison resistance or weakness, item or ability modifiers, and rounding-sensitive compositions. The §15 vectors are therefore **not** produced by this slice.

## 14. Negative controls and starting-head record

Starting-head control (`tools/hns-calc-census/starting-head/HnsBelchStartingHeadTest.kt`):

- Compiled against the starting API only. The Slice 16 codes are compared by name so the file compiles on `cd81bcc`. A first version referenced `HNS_BELCH_BERRY_STATE_UNKNOWN` directly, failed to compile on the old head, and was corrected rather than counted.
- On the detached worktree at `cd81bcc` with `DUALDEX_BELCH_OLD_HEAD=true`: **PASSED**. A neutral Belch is refused with exactly `HNS_MOVE_MECHANICS_NOT_MODELLED`.
- On the current head (copied in only for the run): **PASSED**, refused with exactly `HNS_BELCH_BERRY_STATE_UNKNOWN`.

The machine-readable record is `tools/hns-calc-census/starting-head/belch-negative-control.json`.

## 15. Original-engine evidence: NOT RUN

The task required original-engine Belch consumption, per-party identity, new-battle, item-state, and sixteen-roll damage vectors. **None were produced in this slice.** The Slice 15 semi-invulnerable harness drives scenarios through an injected source patch and `mgba-host`; building scripted Berry consumption (Stuff Cheeks, Recycle, Bug Bite) and party switches on that harness is a separate task. Nothing in this document claims that lifecycle was observed in the engine.

The per-party persistence and fresh-battle claims in §5 are **source-proven**. The native reader tests in §12 below cover the production read on synthetic memory. They are not engine lifecycle evidence.

## 16. Native read-boundary tests

`native/tests/test_pokemon_reader.c`, `test_hns_belch_party_state_ate_berry` (98 tests pass total):

- fresh zeroed struct → observed `false` on the authoritative slot;
- the neighbouring `intrepidSwordBoost` bit set → `ateBerry` stays `false` (exact bit);
- the player's slot 0 flag → only that battler is authorized; the opponent side is independent;
- a party switch to slot 1 → slot 1 does not inherit slot 0; returning to slot 0 keeps `true`;
- opponent side reads `partyState[1]`;
- null `gBattleStruct` pointer and out-of-EWRAM pointer → unread, never `false`;
- a layout with a zero `partyState` offset → the whole observation is refused, with no `ateBerry` published;
- out-of-domain authoritative slot 6 → the observation is refused.

Not covered by native tests: a short read, a side outside `{0,1}` (unreachable, because the side is `position & 1`), and battle teardown and re-entry. The first and last are unexercised at unit level; the side case is unreachable by construction.

## 17. Historical evidence integrity

Validated by the canonical suite `./ci.sh test` with `DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true` (exit 0), which includes the historical oracle and prior additive evidence:

- historical corpus: 2,523 entries, 2,522 direct production replays, the single Slice 13 migration, zero divergences;
- Slice 12 repeated-strike evidence: 5,125 original-engine cases;
- Slice 13 variable multi-hit evidence and Slice 14 scale-shot evidence: unchanged;
- Slice 15 semi-invulnerable original-engine evidence (`semi-invulnerable-evidence.json`): **unchanged** (`git status` shows it untouched).

The Slice 15 **reconciliation** file `tools/hns-calc-census/semi-invulnerable-coverage.json` was regenerated by the existing pipeline. Only its `currentCensusSha256` pointer and its after-census `eligibleRequestsRefusedByMoveMechanics` count (1516 → 1452) changed. These follow from the 64 Belch rows leaving that bucket. Its population, opportunity, and transition content are unchanged.

## 18. Census

Generated with `DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh test`, then reconciled by `tools/hns-calc-census/report_belch_coverage.py` (`--write` in generate mode, check mode otherwise).

Population (unchanged):

| Metric | Expected | Measured |
|---|---:|---:|
| Trainer battles | 651 | 651 |
| Eligible damaging requests | 24,278 | 24,278 |
| `FULLY_MODELLED` | 21,526 | 21,526 |
| `CAVEATED_ESTIMATE` | 462 | 462 |
| `REFUSED` | 2,290 | 2,290 |

Belch opportunity (starting census): **64 requests, 21 battles, 20 lead requests, 20 lead pairs**. Lead means `partySlot == 0` under the census's existing definition. All 64 were `REFUSED` with `HNS_MOVE_MECHANICS_NOT_MODELLED` (Singles, observed battler count 2).

Outcome of the 64 requests:

- Before: `REFUSED`, `HNS_MOVE_MECHANICS_NOT_MODELLED`.
- After: `REFUSED`, `HNS_BELCH_BERRY_STATE_UNKNOWN`, for all 64.
- Tier transitions: **0**. Outside-family transitions: **0**. Newly `FULLY_MODELLED` requests: **0**. No caveat mode added.
- Berry eligibility is **not observable** in the census fixture. The fixture does not record party Berry consumption, so the flag is unread, which is correct. Nothing was set to `true` to meet a coverage target.
- Lead impact: the 20 Belch lead pairs and 20 lead requests remain refused for the same new reason. Overall lead displayability is unchanged: **680 / 1,302** pairs and **7,582 / 8,450** requests displayable, blank battles **0**, identical to the starting census.

Limitation-only transitions (`HNS_MOVE_MECHANICS_NOT_MODELLED` → `HNS_BELCH_BERRY_STATE_UNKNOWN`) are the expected residual and were reconciled exactly. `eligibleRequestsRefusedByMoveMechanics` fell from 1516 to 1452 (64 fewer).

## 19. Hardware and remaining limitations

- Hardware: `NOT_RUN`.
- Original-engine Belch evidence (§15): **not produced**. Sixteen-roll vectors against the pinned engine (§13) are **not produced**.
- Belch can be admitted only from a live observation of `ateBerry == true`. The census can never produce it, by design.
- Assurance, Pursuit, and other conditional-use families are out of scope and were not touched.
- Disable, Choice locks, and Taunt-like limitations are not separately re-audited for Belch; existing policy applies.
- The pinned source has no `GetBattlerMoveLimitations`; the equivalent is `CheckMoveLimitations`.

## 20. Next global coverage recommendation

Remaining mechanics-refused requests after this slice: **1,452 requests across 366 battles**. The highest-volume families:

| Move | Requests | Battles |
|---|---:|---:|
| Pursuit | 68 | 33 |
| Last Resort | 48 | 24 |
| Rapid Spin | 52 | 22 |
| Assurance | 62 | 21 |
| Sucker Punch | 46 | 20 |
| Solar Beam | 36 | 18 |
| Wring Out | 36 | 18 |
| Horn Drill | 44 | 16 |
| Endeavor | 36 | 15 |
| Night Shade | 34 | 15 |

**Recommendation:** Pursuit needs switch-prediction and is out of scope; it stays last. Next, a **Rapid Spin** slice, to verify the source before scoping: the census count (52 requests, 22 battles) is large and the move is a 50-BP physical strike, so its refusal may be a classification gap rather than missing arithmetic. This is a hypothesis to test against the pinned `EFFECT_RAPID_SPIN` path, not a finding. Solar Beam and Night Shade need weather and charging-state authority, so they are a later slice.
