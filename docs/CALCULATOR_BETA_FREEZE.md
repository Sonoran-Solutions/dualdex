# Damage Calculator Feature Freeze: First Public Beta

**Status:** The calculator freeze takes effect automatically upon merge of PR #167 and remains active until explicitly lifted by the project owner after the first public `0.9.0-beta.1` release. Until that merge, it is a decision record only. A release candidate, a local build, a successful CI run, or an internal device test does not lift or start the freeze.

**Milestone:** `0.9.0-beta.1` (first public beta).
**Duration:** from merge of the reviewed final-calculator PR until **after** the first public `0.9.0-beta.1` release.
**Lifting:** only the project owner may explicitly approve lifting the freeze, and only after the public beta release. No other agent, reviewer, or CI result lifts it.

Machine-readable record: [tools/hns-move-mechanics/calculator_beta_freeze.json](../tools/hns-move-mechanics/calculator_beta_freeze.json). The enforcing test is `HnsCalculatorBetaFreezeGuardTest`, which compares the production registry's admitted H&S move IDs and families with that snapshot.

Pinned source: `PokemonHnS-Development/pokehns-expansion`, `Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.

## 1. What the freeze covers

The freeze covers new calculator capability across DualDex, particularly H&S:

- No new damage move families.
- No additional H&S conditional-move coverage slices.
- No new speculative battle-state forecasts.
- No new probability or damage-simulation features.
- No broad new gimmick support.
- No optional ability or item mechanic expansions.
- No unnecessary calculator UI feature expansion.
- No reopening of 100%-coverage ambitions before beta.

Existing supported behaviour remains maintained. Unimplemented move families stay explicitly unsupported unless separately approved as necessary correctness work. Existing refusals must not be turned into optimistic estimates to raise census coverage.

## 2. Final admitted scope at freeze

The admitted set is 422 H&S move IDs, recorded in the snapshot from the production registry (`HnsMoveMechanicsRegistry.classify`):

| Family | Admitted IDs |
|---|---:|
| `ORDINARY_PROVEN_EQUIVALENT` | 357 |
| `VARIABLE_MULTI_HIT_PLAIN` | 12 |
| `FIXED_SINGLE_HIT_RECOIL` | 12 |
| `FIXED_SINGLE_HIT_DRAIN` | 7 |
| `FIXED_TWO_HIT_PLAIN` | 6 |
| `FIXED_SINGLE_HIT_STATUS_DOUBLE` | 6 |
| `FIXED_SINGLE_HIT_SEMI_INVULNERABLE_PREVIEW` | 5 |
| `FIXED_SINGLE_HIT_ESCAPE` | 3 |
| `FIXED_SINGLE_HIT_EARTHQUAKE` | 2 |
| `FIXED_SINGLE_HIT_ROLLOUT` | 2 |
| `FIXED_SINGLE_HIT_EXPLOSION` | 2 |
| `FIXED_SINGLE_HIT_UNDERWATER` | 2 |
| `VARIABLE_MULTI_HIT_SCALE_SHOT` | 1 |
| `FIXED_SINGLE_HIT_BELCH` | 1 |
| `FIXED_SINGLE_HIT_RAPID_SPIN` | 1 |
| `FIXED_SINGLE_HIT_GYRO_BALL` | 1 |
| `FIXED_SINGLE_HIT_BRINE` | 1 |
| `FIXED_SINGLE_HIT_ELECTRO_BALL` | 1 |

Rapid Spin (ID 229) is the final admission in this freeze. Its decision and evidence are in [HNS_MOVE_COVERAGE_SLICE_17_RAPID_SPIN.md](HNS_MOVE_COVERAGE_SLICE_17_RAPID_SPIN.md). Mortal Spin (ID 794, same effect family) is **not** admitted.

Census at freeze: 24,278 eligible requests; FULLY_MODELLED 21,576; CAVEATED_ESTIMATE 462; REFUSED 2,240. Displayable lead pairs 684/1302; displayable lead requests 7,596/8,450. See [HNS_CALCULATOR_FINAL_PREBETA_ACCEPTANCE.md](HNS_CALCULATOR_FINAL_PREBETA_ACCEPTANCE.md) for the reconciliation.

## 3. Permitted exceptions

These changes remain allowed during the freeze.

**Correctness fixes**: incorrect damage results; wrong source metadata; incorrect type effectiveness or immunity; incorrect ability or item arithmetic; invalid acceptance or refusal decisions; malformed calculator responses; misleading damage labels; regressions in existing supported mechanics.

**Safety and reliability**: crashes; stale live-memory state; out-of-domain native reads; trust-boundary violations; corrupt or incomplete JNI observations; security and stability defects; severe performance regressions.

**Necessary beta compatibility repairs**: exact-version corrections for already-approved beta ROM integrations; source or ABI fixes needed to keep existing beta compatibility promises truthful; regression fixes found during AYN Thor testing; release-blocking corrections for the documented beta feature set.

Every exception requires:

1. a concrete issue;
2. a demonstrated problem;
3. a reproducible failing test or equivalent evidence;
4. a bounded fix;
5. regression validation;
6. senior review;
7. explicit documentation of why the change is a bugfix or beta requirement rather than optional expansion.

This exception is **not** permission to add unrelated H&S move families while developing R.O.W.E. or Unbound. A nominal bugfix must not smuggle in a new feature family.

## 4. Enforcement

- `HnsCalculatorBetaFreezeGuardTest` fails when the production registry admits a new move ID, changes an admitted ID's family, or removes an admission. Correctness fixes that leave the admitted set unchanged pass.
- Any intentionally changed admission scope requires a reviewed exception that names the added or removed IDs. `DUALDEX_FREEZE_RECORD=true` regenerates the snapshot and is only for that reviewed change.
- Historical census reports accept only the exact approved Rapid Spin transitions in [tools/hns-calc-census/rapid-spin-approved-transitions.json](../tools/hns-calc-census/rapid-spin-approved-transitions.json): the 50 Singles REFUSED → FULLY_MODELLED upgrades, with the two Doubles requests required to remain REFUSED. Any other transition, including a reversal, fails.
- The guard does not cover ability or item expansion, gimmick support, or UI feature growth. Those are covered by this document and by review.

## 5. Post-beta backlog (DEFERRED UNTIL AFTER PUBLIC BETA)

Every item below is **DEFERRED UNTIL AFTER PUBLIC BETA**. No implementation branch exists or should be created for them. This list is a backlog reference, not an active work queue.

Baseline counts are REFUSED census requests at Slice 17 (24,278 eligible requests), read from `tools/hns-calc-census/census.json.gz`. The "limitation" column summarizes earlier census reasons and general mechanics notes; it was **not** re-audited against the pinned source in this slice and must be source-audited before any post-beta work starts.

| Item | Refused census requests | Source or model limitation |
|---|---:|---|
| Pursuit | 68 | Pursuit's damage doubles against a switching target; switch timing is battle-phase state the calculator does not forecast. |
| OHKO moves (Fissure 8, Sheer Cold 8, Guillotine 10, Horn Drill 44) | 70 | Fixed-result OHKO accuracy and level-based formula are not the ordinary damage path. |
| Assurance | 62 | Doubles the power when the target took damage this turn; that is earlier-turn battle state. |
| Last Resort | 48 | Requires the user's full moveset state; not modelled. |
| Fixed / level-based damage (Night Shade 34, Seismic Toss 18, Dragon Rage 30, Psywave 12, Super Fang 12, Endeavor 36, Wring Out 36) | 178 | Fixed, level-based, HP-percent, or HP-dependent damage formulas. |
| Flail / Reversal | 52 | HP-dependent base power. |
| Sucker Punch | 46 | Depends on the target's chosen move and whether it attacks this turn. |
| Counter / Mirror Coat / Metal Burst / Bide | 38 | Depend on the previous hit received in battle. |
| Belch | 64 | Admitted (Slice 16). The refusals are Berry-state gated (no Berry eaten in those battles), not a family gap. |
| Remaining repeated-strike caveats | not counted separately | Documented in the repeated-strike contract; no new strike sequences are admitted. |
| Additional ability and item support | not counted separately | Each requires its own source audit and is frozen out until after beta. |
| Full battle-phase simulation | not applicable | Out of scope for the calculator; a separate architecture decision. |
| Broader gimmick support (Dynamax, Terastallize, Z-moves) | not counted separately | Refused by the gimmick gate; no support is planned before beta. |

## 6. Relationship to the pre-beta roadmap

The calculator's planned pre-beta feature development is complete with this freeze. The immediate next priorities remain the general beta hardening work in [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md) and [README.md](../README.md):

1. AYN Thor hardware acceptance.
2. Save protection, backup, recovery, and ROM-switch soak testing.
3. Battle/Party/Map presentation verification.
4. Controller shortcuts.
5. Assistant cross-ROM fallback correctness and Gemini credential/backup security.
6. Exact R.O.W.E. and Unbound integration in the established serial order.
7. Release-candidate and public-beta validation.

Those tasks stay open until their own acceptance criteria pass. Hardware status for the calculator is `NOT_RUN`.
