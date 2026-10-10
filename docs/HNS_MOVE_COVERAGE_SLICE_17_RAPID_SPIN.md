# H&S move coverage Slice 17: Rapid Spin (ID 229)

**Status:** Complete. Decision **GO** for move 229 only. Implementation, original-engine evidence, census reconciliation, and the freeze record are finished in PR #167 (`feat/hns-final-calculator-slice-and-beta-freeze`), which is open for senior review and not merged.
**Starting main:** `225dfcdce0d98651e1a9c85bb3c311cf71e1c01b` (PR #164 merged).
**Pinned source:** `PokemonHnS-Development/pokehns-expansion`, `Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.
**Hardware:** `NOT_RUN`. H&S remains ESTIMATED.
**Freeze:** this slice is the final admission in the calculator feature freeze. The freeze takes effect on merge of PR #167 (see [CALCULATOR_BETA_FREEZE.md](CALCULATOR_BETA_FREEZE.md)).

## 1. Descriptor (source-proven)

`src/data/moves_info.h`, `[MOVE_RAPID_SPIN]`: ID 229, `EFFECT_RAPID_SPIN`, power `B_UPDATED_MOVE_DATA >= GEN_8 ? 50 : 20` (50 under the pinned config), `TYPE_NORMAL`, `DAMAGE_CATEGORY_PHYSICAL`, accuracy 100, PP 40, priority 0, `TARGET_SELECTED`, contact `TRUE`, one strike. Additional effect: `MOVE_EFFECT_SPD_PLUS_1`, `.self = TRUE`, `.chance = 100`, guarded by `B_SPEED_BUFFING_RAPID_SPIN >= GEN_8` (the pinned value is `GEN_LATEST`). The MoveInfo digest is frozen in `RAPID_SPIN_MOVEINFO_SHA256`.

## 2. Event order (source-proven)

`include/constants/battle_move_resolution.h` puts `MOVEEND_SHEER_FORCE` (line 109) before `MOVEEND_MOVE_BLOCK` (line 110). The `EFFECT_RAPID_SPIN` cleanup branch is in `MoveEndMoveBlock` (`src/battle_move_resolution.c` around line 3154), which runs `BattleScript_RapidSpinAway` only when the target was damaged and the attacker is alive. Life Orb recoil runs later, at `MOVEEND_LIFE_ORB_SHELL_BELL`.

Order: selection and gates, accuracy and immunity, damage calculation, damage application, Sheer Force jump, cleanup, additional Speed effect, Life Orb.

**Question answered:** no. A Speed increase, wrap removal, Leech Seed removal, or hazard removal all happen after the damage calculation. None of them changes the current strike's damage, so the calculator reuses the selected-hit arithmetic and does not forecast cleanup.

## 3. Sheer Force (source-proven)

`MoveIsAffectedBySheerForce` (`src/battle_util.c`) returns TRUE because the Speed effect has `chance = 100` and no `sheerForceOverride`. Sheer Force therefore applies ×1.3 to the damage, and it suppresses both the cleanup (the jump at `MOVEEND_SHEER_FORCE` skips `MOVEEND_MOVE_BLOCK`) and the Speed boost. The DualDex metadata holds this as `sheerForceAffectedById[229] = true`.

The earlier unknown flag came from the generator, which could not resolve the GEN_8-gated Speed effect. The generator now resolves it only under a verified `B_SPEED_BUFFING_RAPID_SPIN = GEN_LATEST` config.

## 4. Implementation

- Generator: `tools/hns-move-mechanics/generate_hns_move_effects.py` gains `parse_rapid_spin_metadata`, `verify_rapid_spin_contract`, and the GEN_LATEST Speed-config resolution. Regenerated `Hns205MoveEffects.kt` and `hns_move_damage_metadata.json` differ only by the Rapid Spin entry.
- Contract suite: `tools/hns-move-mechanics/test_rapid_spin_contract.py` refuses 20 mutations and refuses parsing without the verified config.
- Registry and wiring: `FIXED_SINGLE_HIT_RAPID_SPIN` in `HnsMoveMechanicsRegistry`, `DamageCalculator`, `CalcCapabilityPolicy`, and `HnsAbilityContextPolicy`. `entry.js` admits only `EFFECT_RAPID_SPIN` with `hnsMoveFamily === 'FIXED_SINGLE_HIT_RAPID_SPIN'` and metadata id 229 (power 50, contact, Sheer Force TRUE, no unknown flags, Singles, selected hit).
- No new damage formula, prediction field, or native ABI. `native/` is unchanged relative to `origin/main`.

## 5. Original-engine evidence (produced locally)

Produced by `tools/hns-damage-oracle/rapid_spin_evidence.py` from the pinned Release-v2.0.5 runner, built with the ARM GNU toolchain and run under mGBA hydra. The committed artifact is `tools/hns-damage-oracle/rapid-spin-evidence.json`. Its reversed-order replay (`verify --order reversed`) reproduces the committed artifact.

### 5.1 Damage vectors (10 × 16 rolls)

Each vector is one battle per roll (`WITH_RNG(RNG_DAMAGE_MODIFIER, i)`, forced critical where stated). Engine rolls are indexed by RNG value `i`; `rolls[i]` maps to the shipped calculator's roll `15 − i` (see the corpus `rollOrder`).

| Vector | Setup | Engine rolls, min–max (index order) |
|---|---|---|
| neutral | Muk → Blastoise | 24 … 20 |
| stab | Greedent (Normal) → Blastoise | 36 … 30 |
| critical | Muk, forced crit | 48 … 40 |
| technician | Muk (Technician) | 35 … 29 |
| tough-claws | Muk (Tough Claws, contact) | 30 … 25 |
| reflect | Muk → Blastoise behind Reflect (set via the defender's observed side status) | 12 … 10 |
| sheer-force | Muk (Sheer Force) | 30 … 25 |
| sheer-force-life-orb | Muk (Sheer Force, Life Orb) | 39 … 32 |
| composition-rounding | Greedent (137 Atk) → Blastoise (89 Def), forced crit | 105 … 88 |
| immune | Greedent → Gengar (Ghost) | 0 (immune) |

The shipped calculator reproduces all 160 rolls through the QuickJS bundle with the exact `15 − i` mapping (`HnsRapidSpinEngineRollTest`).

### 5.2 Lifecycle scenarios (4, HP 200/200, realistic)

The first version of these scenarios used 60,000-HP fixtures. Review found that Leech Seed heals overflowed the 16-bit HP field before the cap was checked (for example, 60,000 + 7,500 mod 65,536 = 1,964), so those values were not valid evidence. They were discarded, and the scenarios were regenerated at 200 HP with explicit bound checks on both sides.

| Scenario | User Speed stage | User Leech Seed | Target HP | What it shows |
|---|---:|---|---:|---|
| speed-boost | 7 (+1) | no | 170 | Unseeded hit: Speed rises and the hit lands (200 − 30). |
| cleanup-leech | 7 (+1) | no (removed) | 170 | Seed on the user is removed by the hit, so no end-of-turn heal occurs. |
| sheer-force-suppresses | 6 (unchanged) | yes (kept) | 188 | Sheer Force suppresses cleanup and Speed; the hit still lands (≈37) and the seed heal (+25) applies. |
| blocked-protect | 6 (unchanged) | no | 200 | The target's Protect blocks the strike completely. |

Every scenario asserts `0 < hp <= maxHP` on both sides, which rules out 16-bit wraparound.

### 5.3 Scenarios removed and why

- **Miss (`hit: FALSE`)** was removed. In the pinned framework, `hit: FALSE` on this 100-accuracy move did not prevent the hit: the user's Speed still rose. That result contradicts the source's expectation for a miss, so it is recorded as an open framework question (below), not evidence.
- **`NONE_OF { HP_BAR }` assertions** were removed from zero-damage scenes. The engine emitted an HP bar in those cases. The end-state HP and Speed assertions still run. The cause is not established.

### 5.4 Limitations of the evidence

- Attacker-fainting termination was not separately executed.
- Only one defender and attacker per scenario (Singles); Doubles behaviour is refused by design.
- The engine HP-bar behaviour on zero-damage strikes and the `hit: FALSE` result are unexplained framework observations.

## 6. Census

52 Rapid Spin opportunities across 22 battles and 14 lead pairs (50 Singles, 2 Doubles). Starting: all refused (`HNS_MOVE_MECHANICS_NOT_MODELLED`, sometimes with ability caveats). Slice 17: 50 Singles requests become FULLY_MODELLED; the 2 Doubles requests stay REFUSED.

The exact expected transitions are committed in `tools/hns-calc-census/rapid-spin-approved-transitions.json`: the 50 approved Singles upgrades (REFUSED → FULLY_MODELLED) and the 2 Doubles keys that must remain REFUSED. `report_rapid_spin_coverage.py` asserts that set exactly, and the historical Slice 15 and Slice 16 reports accept only those same transitions. Any additional, missing, or reversed transition fails.

Census-wide: FULLY_MODELLED 21,526 → 21,576; CAVEATED_ESTIMATE 462 → 462; REFUSED 2,290 → 2,240; displayable lead pairs 680 → 684 of 1,302; displayable lead requests 7,582 → 7,596 of 8,450.

## 7. Starting-head negative control

`tools/hns-calc-census/starting-head/HnsRapidSpinStartingHeadTest.kt` and `rapid-spin-negative-control.json`. Run in a detached worktree at `225dfcd` (with `DUALDEX_RAPID_SPIN_OLD_HEAD=true`), neutral Rapid Spin is refused with `HNS_MOVE_MECHANICS_NOT_MODELLED`. On the Slice 17 head, the same request is admitted (`CalcRequestOutcome.Ready`). This control was run locally.

## 8. Historical integrity

The 2,523-entry oracle corpus is unchanged: 2,399 modelled, 124 engine-only, 2,522 direct production replays with exact 16-roll matches, one explicit Slice-13 migration, zero registered divergences. Earlier slices' original-engine artifacts are unchanged.

## 9. Open items

- Test-framework questions above (`hit: FALSE` on Rapid Spin; HP bars on zero-damage strikes). Neither changes the calculator's damage arithmetic, and neither is claimed as validated behaviour.
- Attacker-fainting termination is not separately executed (see 5.4).
