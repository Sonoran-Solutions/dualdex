# H&S move coverage Slice 17: Rapid Spin audit (GO decision)

**Starting main:** `225dfcdce0d98651e1a9c85bb3c311cf71e1c01b` (PR #164 merged).
**Pinned source:** `PokemonHnS-Development/pokehns-expansion`, release `Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.
**Status:** GO for the bounded selected-hit admission of move 229 only. Implementation, original-engine evidence, census, and CI are **not yet done**; see §8.

## 1. Descriptor (source-proven)

`src/data/moves_info.h`, `[MOVE_RAPID_SPIN]`:

| Field | Value |
|---|---|
| ID / effect | 229 / `EFFECT_RAPID_SPIN` |
| Power | `B_UPDATED_MOVE_DATA >= GEN_8 ? 50 : 20` → **50** under the pinned config |
| Type / category | `TYPE_NORMAL` / `DAMAGE_CATEGORY_PHYSICAL` |
| Accuracy / PP / priority | 100 / 40 / 0 |
| Target | `TARGET_SELECTED` |
| Contact | `TRUE` |
| Strikes | 1 |
| Additional effects | `MOVE_EFFECT_SPD_PLUS_1`, `.self = TRUE`, `.chance = 100`, guarded by `#if B_SPEED_BUFFING_RAPID_SPIN >= GEN_8` |

`B_SPEED_BUFFING_RAPID_SPIN` is `GEN_LATEST` (`include/config/battle.h:99`), so the Speed effect is compiled in.

## 2. Event order (source-proven)

`src/battle_move_resolution.c`, `MOVEEND_*` enum (`include/constants/battle_move_resolution.h`):

1. Selection and execution gates, accuracy, immunity, Substitute block.
2. Power modifiers, ability/item modifiers, damage calculation (`CalculateMoveDamage` path).
3. Damage application.
4. `MOVEEND_SHEER_FORCE` (line 109): if `IsSheerForceAffected(move, attackerAbility)`, the state jumps past `MOVEEND_MOVE_BLOCK`.
5. `MOVEEND_MOVE_BLOCK` (line 110): contains the `EFFECT_RAPID_SPIN` case (`battle_move_resolution.c:3154`), which runs `BattleScript_RapidSpinAway` only when `IsBattlerTurnDamaged(target, INCLUDING_SUBSTITUTES) && IsBattlerAlive(attacker)`.
6. Additional effects (the Speed boost) are applied after damage, in the additional-effect path.
7. `MOVEEND_LIFE_ORB_SHELL_BELL` (line 117): Life Orb recoil, after damage.

**Answer to the task's explicit question:** No. Speed increase, wrap removal, Leech Seed removal, and hazard removal all happen at steps 5–6, after step 2 has fixed the damage. None of them is read by the damage arithmetic. The selected-hit damage can reuse the existing single-strike arithmetic.

## 3. Sheer Force (source-proven)

- `MoveIsAffectedBySheerForce` (`src/battle_util.c:9807`) returns TRUE when any additional effect has `(chance > 0) != sheerForceOverride`. Rapid Spin's Speed effect has `chance = 100` and no `sheerForceOverride`, so the predicate is **TRUE**.
- Sheer Force therefore applies its **×1.3** damage multiplier (`battle_util.c:6726`).
- Sheer Force suppresses cleanup (step 4 jumps past step 5) and the Speed effect (step 6), so both are suppressed under Sheer Force.
- The DualDex metadata already holds the correct value: `Hns205MoveEffects.sheerForceAffectedById[229] = true` (line 1196).

**Conflict to resolve in implementation:** `Hns205MoveEffects.unknownSheerForceMoveIds` (line 2835) also contains 229. That list is a fail-closed gate for moves whose Sheer Force status was not proven, and it currently refuses Rapid Spin. The source now proves the status, so 229 can be removed from that list. This is a correction of a proven predicate, not a relaxed gate. The generator must derive it, not a hand edit.

## 4. Cleanup and fainting

- Cleanup requires `IsBattlerTurnDamaged(target, INCLUDING_SUBSTITUTES)`. It is a post-hit effect. It does not change the current strike's damage, so the calculator does not forecast it.
- `IsBattlerAlive(attacker)` gates only the cleanup. A fainting attacker can lose the cleanup but not the damage already calculated.
- Substitute: cleanup counts a Substitute hit as damage. This is post-hit, so the existing Substitute gate is unchanged.

## 5. GO conditions

| Condition | Result |
|---|---|
| Selected hit uses existing single-strike arithmetic | Yes: power 50, one strike, `TARGET_SELECTED`, physical |
| Effects relevant to the strike come from existing authoritative observations | Yes: Speed and cleanup are post-damage; no new operand |
| Speed and cleanup occur after damage calculation | Yes (§2) |
| Sheer Force and other pre-damage modifiers modelled exactly | Yes (×1.3 via the existing predicate, §3) |
| Original-engine evidence through the established harness | Yes (Escape/Belch harness pattern exists, `tools/hns-damage-oracle`) |
| No new native ABI or simulator | Yes: no new native operand is needed |
| Existing capability gates reused without weakening | Yes, with the 229 correction in §3 |

**Decision: GO**, for move ID 229 only. The `EFFECT_RAPID_SPIN` family (Mortal Spin, ID 794) is **not** admitted.

## 6. Original-engine evidence plan

Required scenarios (each a real Rapid Spin in the pinned test ELF):

- Neutral 16-roll damage vector; STAB; critical; Technician (power 50); Tough Claws; Reflect.
- Sheer Force with and without Life Orb.
- Ghost/type immunity; miss.
- Cleanup present and absent, with and without Sheer Force, observed by the cleanup/Speed script effects, kept separate from the damage vectors.
- Attacker fainting where practical.

Damage evidence and post-hit effect evidence are kept in separate artifacts.

## 7. Caveat

This slice is the most expensive pre-beta addition still open. The implementation touches the generator, metadata, Kotlin registry, capability policy, the QuickJS bundle, the native test ELF, the census, and the freeze documentation. The GO decision above stands on the source audit. Engine evidence has not been run yet.

## 8. Not done

- No Kotlin, generator, bundle, or census change.
- No original-engine Rapid Spin run.
- No starting-head negative control for move 229.
- No hosted CI result for any candidate head.
