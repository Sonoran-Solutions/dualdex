# H&S move coverage Slice 15: semi-invulnerable strike previews

**Starting main:** `d9aca830d0ebcd89cbf6d1dcb47d19e8040c8bf9` (PR #159 merged; issue #158 remained closed).
**Pinned source:** `PokemonHnS-Development/pokehns-expansion`, release `Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.
**Issue:** [#160](https://github.com/Sonoran-Solutions/dualdex/issues/160).

## Product scope

The calculator shows a **damaging-turn preview** and labels it: “Damaging-turn preview. Assumes current conditions when the strike occurs.” It reports one selected strike as the normal sixteen-roll result. It does not report preparation-turn damage, per-turn averages, future conditions, an intervening move or switch, survival, accuracy, or KO probability. It does not multiply or divide the strike by two. The Battle and calculator surfaces carry the same label; the response model derives the label from the trusted H&S move ID, not response JSON.

The bounded admitted planning state is Singles with an observed neutral attacker and defender semi-invulnerable state, an observed `multipleTurns=false`, a stable exact battle snapshot, no effective active Power Herb, and known clear Gravity for Fly and Bounce. A non-neutral/unknown phase or unsupported field/item operand refuses. No live continuation is normalized into a hit phase. `MOVE_NONE` is not treated as action evidence. No ABI fields were added. H&S remains **ESTIMATED**.

## Frozen source descriptors

All five entries are `EFFECT_SEMI_INVULNERABLE`, physical, `TARGET_SELECTED`, priority 0, one damaging strike, no multihit, no punching or ballistic flag, and contact. `B_UPDATED_MOVE_DATA`, `B_UPDATED_MOVE_FLAGS`, and `B_PHYSICAL_SPECIAL_SPLIT` resolve to `GEN_LATEST`. The generated metadata pins the complete whitespace-normalized `MoveInfo` digest, the source contact/SF predicates, ability flags, damage flags, and additional effects. The only generated family members are these IDs:

| ID | Move | Power | Type | Accuracy / PP | Preparation state | Gravity banned | `ignoresProtect` | Sheer Force | Additional effect |
|---:|---|---:|---|---:|---|---|---|---|---|
| 19 | Fly | 90 | Flying | 95 / 15 | `STATE_ON_AIR` | yes | no | no | none |
| 91 | Dig | 80 | Ground | 100 / 10 | `STATE_UNDERGROUND` | no | no | no | none |
| 291 | Dive | 80 | Water | 100 / 10 | `STATE_UNDERWATER` | no | no | no | none |
| 340 | Bounce | 85 | Flying | 85 / 5 | `STATE_ON_AIR` | yes | no | yes | 30% paralysis, after damage |
| 566 | Phantom Force | 90 | Ghost | 100 / 10 | `STATE_PHANTOM_FORCE` | no | yes | no | Feint effect, chance 0 |

Phantom Force’s pinned `minimizeDoubleDamage` expression resolves to false under this build. Its `ignoresProtect` descriptor is carried and independently validated; it does not authorize ignoring other execution or defensive state. No other member shares this exception. No Shadow Force, Sky Drop, Solar Beam, Sky Attack, Twineedle, or other move is generated into the family.

## Phase and damage-time source path

Source review and digest guards cover `CanTwoTurnMoveFireThisTurn`, `CancelerCharging`, `Cmd_setsemiinvulnerablebit`, `IsGravityPreventingMove`, `IsMoveGravityBanned`, `MoveIgnoresProtect`, the two-turn and hit battle scripts, `CalcMoveBasePowerAfterModifiers`, `MoveIsAffectedBySheerForce`, `MoveEndSheerForce`, and `MoveEndLifeOrbShellBell`.

The normal path selects and locks the move, sets its source-defined semi-invulnerable state, and uses the charging script. On a successful continuation, the source clears the multi-turn and semi-invulnerable state before the accuracy/damage path. Accuracy, damage calculation, HP application, and subsequent effects occur in the normal hit pipeline. A miss or interruption does not produce the selected strike. The request contract starts at an otherwise-supported neutral planning snapshot; it does not infer that a live nonzero semi-state is a committed release. A nonzero attacker `multipleTurns` or semi-state refuses. No damage-time operand is rewritten.

The selected strike reuses the established exact H&S single-hit arithmetic and its existing ability, item, type, stages, weather, terrain, screens, critical, immunity, contact, and fixed-point authorities. This is arithmetic reuse only; it does not admit unrelated execution conclusions. Earthquake/Bulldoze underground and Surf/Whirlpool underwater defender-state gates are unchanged.

Bounce keeps its post-damage 30% paralysis descriptor. The source’s `MoveIsAffectedBySheerForce` and pre-damage 1.3 modifier remain distinct from `MoveEndSheerForce` skipping the suppressed additional-effect path. The Life Orb item step remains after the Sheer Force suppression point and is not stripped. Fly, Dig, and Dive do not receive a move-name Technician exception; source-resolved power determines the existing Technician predicate.

Only Fly and Bounce are Gravity-banned in this set. The existing effective-item authority is used for Power Herb; an active effective Power Herb path refuses because source consumes it before damage. Suppressed Power Herb uses the shared suppression authority. No source shortcut is guessed or globally neutralized.

## Census reconciliation

The current full population keeps 651 trainer battles and 24,278 eligible keys. `tools/hns-calc-census/semi-invulnerable-coverage.json` records every exact request key, the before/after tier and blocker transitions, and current residuals. The report is generated/checked with `tools/hns-calc-census/report_semi_invulnerable_coverage.py`.

| Metric | Starting main | Current generated census |
|---|---:|---:|
| `FULLY_MODELLED` | 21,448 | 21,526 |
| `CAVEATED_ESTIMATE` | 462 | 462 |
| `REFUSED` | 2,368 | 2,290 |
| Displayable lead pairs | 664 / 1,302 | 680 / 1,302 |
| Displayed lead requests | 7,550 / 8,450 | 7,582 / 8,450 |
| Blank battles | 0 | 0 |

The target opportunity was 80 Singles requests in 36 battles: Bounce 22, Dig 38, Dive 6, Fly 10, Phantom Force 4. All 80 had the move-mechanics refusal; overlapping pre-existing occurrences were six ability and eight item blockers. 78 requests transition to `FULLY_MODELLED`: Dig 38, Bounce 22, Fly 10, Phantom Force 4, Dive 4. The other eight transitions retain source-reviewed existing decisions: four Quick Claw turn-order items are proven irrelevant to selected-strike damage; two Sheer Force / Life Orb combinations use the existing source predicate and modeled final item modifier; two Blaze conditions are irrelevant to Ground damage. No caveat mode was added. There are zero outside-family tier transitions and zero refused-to-caveated transitions.

There were 34 target lead requests in 34 lead pairs; 32 transition and two remain refused. Both residual keys are `Dive` for `TRAINER_AKALA_SWIMMER_1_HNS#0`, against `ref-physical` and `ref-special`; each retains `HNS_ABILITY_CONDITION_UNVERIFIED` for attacker Analytic and `HNS_ITEM_EFFECT_NOT_MODELLED` for attacker Lagging Tail (`turn_order_item_attacker_analytic`). The original 80 request keys and complete residual records are in the machine-readable report.

## Evidence and validation status

The additive evidence in `tools/hns-damage-oracle/semi-invulnerable-evidence.json` is freshly regenerated by `semi_invulnerable_evidence.py run` against the pinned original engine. It contains 80 normal two-turn executions (16 damage rolls for each actual target move), 176 selected-strike composition rolls, and 11 special executions. The composition matrix includes neutral, STAB, critical, Reflect, stat stages, type effectiveness, Life Orb, a mixed rounding-sensitive composition, Tough Claws, Fluffy, and Long Reach plus Fluffy. Production models the Tough Claws and Fluffy cases through shared contact authority. Long Reach’s engine vector confirms non-contact behavior, while production retains its existing `HNS_ABILITY_EFFECT_NOT_MODELLED` execution refusal for that ability. The TESTING-only phase observer is read-only. It records no defender HP loss on preparation, the source state and locked move at preparation, and the cleared attacker semi-state with the locked move preserved before the release hit. Miss and Ground-type immunity cases each record one preparation and release, no damage roll, and unchanged defender HP. A Dig user then takes a source-effective Earthquake while underground, faints before release, and is replaced by Pidgeot; the engine clears `multipleTurns` and semi-invulnerability but leaves `gLockedMoves` as Dig. That stale move lock is not release evidence, and no production continuation is admitted from it. Bounce records paralysis when Sheer Force is absent, suppresses it when present, and shows Life Orb recoil only without Sheer Force. Phantom Force damages through active Protect. Source Gravity bans block Fly and Bounce preparation. Power Herb Fly hits immediately after consuming the item, which supports the production refusal for that active path. The separated Reflect vector uses Reflect without a simultaneous stat boost; the harness also captures the source screen modifier.

The reversed-order engine replay passed against the committed evidence. `semi_invulnerable_evidence.py check` checks committed evidence structure and hashes without executing the engine; it is not a fresh engine run. Source-descriptor/phase mutation tests likewise validate source integrity without running battle scenarios. The historical H&S corpus remains byte-for-byte unchanged (2,523 entries, 2,522 direct production replays, exactly the prior `group-c-flag-bulletproof` → `bullet-seed-bulletproof` migration, zero divergences), as do Slice 12/13/14 evidence artifacts (5,125 / 553 / 116 cases). The census is a production admission reconciliation, not original-engine evidence.

The production test suite covers neutral requests for all five moves, damaging-turn labels, forged metadata/phase claims, unsupported active phase and Power Herb refusals, Gravity, Bounce Sheer Force/Life Orb, and the existing Earthquake/Bulldoze and Surf/Whirlpool defender-state gates. The original-engine lifecycle case confirms faint/replacement cleanup while documenting that the source leaves a stale locked-move value; the production phase gate does not use that value as proof of a committed release. No switch-continuation preview is admitted. Two Dive keys remain refused for the pre-existing Analytic unknown condition and relevant Lagging Tail item. H&S remains ESTIMATED.

The immune-response regression executes the shipped bundle from boundary-authorized Phantom Force/Snorlax, Dig/Pidgeot, and Dive/Water Absorb requests, then runs the actual production response parser. It requires sixteen zero rolls, the exact damaging-turn scope, and preserved type-chart or ability attribution. All five family moves also reject missing, incorrect, null, and non-string response scopes; Kotlin never supplies a missing engine label. The Battle presentation regression executes its own authorized requests through that same bundle and parser and requires a labelled `0-0 (Estimate)` result. Successful zero damage is a displayable result, not a calculation failure. The bundle was regenerated with the pinned lockfile; the census and original-engine evidence are unchanged by this response-contract fix.

The starting-head negative control ran in a detached checkout at `d9aca830d0ebcd89cbf6d1dcb47d19e8040c8bf9` with `DUALDEX_SEMI_OLD_HEAD=true ./ci.sh test`. The old API compiled; one JUnit control passed and each move retained exactly `HNS_MOVE_MECHANICS_NOT_MODELLED`. The machine-readable record is `tools/hns-calc-census/starting-head/semi-invulnerable-negative-control.json`. Hardware status: `NOT_RUN`.
