# H&S calculator final pre-beta acceptance report

**Scope:** the bounded H&S 2.0.5 damage calculator as it stands after Slice 17 (Rapid Spin). This is a beta-scope acceptance, not a claim of full coverage.
**Pinned source:** `PokemonHnS-Development/pokehns-expansion`, `Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.
**Pinned ROM:** SHA-256 `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b` (the only H&S build trusted for live reads; see [README.md](../README.md)).
**Starting main:** `225dfcdce0d98651e1a9c85bb3c311cf71e1c01b` (PR #164 merged; expected merge SHA matches).
**Final candidate:** the head of the reviewed PR `feat/hns-final-calculator-slice-and-beta-freeze`. The pushed head SHA and hosted CI results are recorded on the PR, not here, so this document does not need amending after every follow-up commit.
**Hardware:** `NOT_RUN`. No AYN Thor or other device run has been performed for this slice.
**Freeze:** the freeze in [CALCULATOR_BETA_FREEZE.md](CALCULATOR_BETA_FREEZE.md) is **not in force** until the reviewed PR merges.

## 0. Evidence provenance: local versus hosted

Three kinds of evidence are kept apart throughout this report.

- **Original-engine executed locally.** The pinned `Release-v2.0.5` battle-test runner was built here with the ARM GNU toolchain and run under mGBA hydra. This produced the Rapid Spin evidence (`tools/hns-damage-oracle/rapid-spin-evidence.json`), and its reversed-order replay matched byte-for-byte. The Belch, semi-invulnerable, scale shot, and earlier families were produced by the same harness in their own slices and are re-validated here by artifact checks.
- **Production executed locally.** Kotlin unit and boundary tests, the QuickJS bundle run through the shipped boundary, the calc-suite host tests, and the census regeneration all ran on this machine.
- **Hosted artifact-only validation.** The hosted GitHub Actions jobs re-verify committed artifacts and generated sources. They do **not** rebuild the original engine. Hosted results are attached to the PR, and their exact scope is stated there.

No original-engine run is claimed from hosted CI.

## 1. Rapid Spin decision: GO (move 229 only)

Recorded in full in [HNS_MOVE_COVERAGE_SLICE_17_RAPID_SPIN.md](HNS_MOVE_COVERAGE_SLICE_17_RAPID_SPIN.md). Summary:

- The selected hit uses existing single-strike arithmetic (power 50, Normal, Physical, one strike, selected target, contact).
- The Speed boost and move-end cleanup occur **after** the damage calculation (`MOVEEND_SHEER_FORCE` at `battle_move_resolution.c` line 109 jumps past `MOVEEND_MOVE_BLOCK`, which holds the Rapid Spin cleanup). Neither changes the calculated damage.
- Sheer Force is source-proven TRUE (the Speed effect has `chance = 100` and no override), so its ×1.3 applies. Sheer Force also suppresses the cleanup and the Speed boost.
- The only blocker was a fail-closed unknown flag (`unknownSheerForceMoveIds`). The generator now resolves that predicate under a verified `B_SPEED_BUFFING_RAPID_SPIN = GEN_LATEST` config.
- Mortal Spin (ID 794) is not admitted.

## 2. Final admitted move-family inventory

422 admitted IDs, read from the production registry into [calculator_beta_freeze.json](../tools/hns-move-mechanics/calculator_beta_freeze.json). The per-family counts are in the freeze document §2. Rapid Spin is the one new ID in this slice, and nothing else changed admission status. The admitted-set guard (`HnsCalculatorBetaFreezeGuardTest`) passes against that snapshot.

## 3. Supported and unsupported execution scope

Supported: the admitted families in §2, under Singles with an observed two-battler topology, on live state the boundary can authorize.

Unsupported and refused, fail-closed:

- Doubles and ambiguous topologies (Rapid Spin: the two Doubles requests in the census).
- Gimmicks (Dynamax, Terastallize, Z-moves): refused by the gimmick gate.
- Unobserved or out-of-domain live state, including unread side or field statuses.
- Every move family not in the admitted set (see the freeze document's backlog table).

## 4. Current census and lead-matchup metrics

Census: 651 battles; 24,278 eligible requests (unchanged between starting main and Slice 17).

| Metric | Starting main (`225dfcd`) | Slice 17 candidate |
|---|---:|---:|
| FULLY_MODELLED | 21,526 | 21,576 (+50) |
| CAVEATED_ESTIMATE | 462 | 462 |
| REFUSED | 2,290 | 2,240 (−50) |
| Displayable lead pairs | 680 / 1,302 | 684 / 1,302 |
| Displayable lead requests | 7,582 / 8,450 | 7,596 / 8,450 |

Rapid Spin reconciliation (`tools/hns-calc-census/rapid-spin-coverage.json`): 52 opportunities across 22 battles and 14 lead pairs (50 Singles, 2 Doubles). After the change: 50 FULLY_MODELLED, 2 REFUSED (the Doubles requests), 0 CAVEATED. Outside-family tier transitions: **none** other than Rapid Spin's own 50. The Slice 15 and Slice 16 reports were narrowed to allow only that Rapid Spin set, and each still fails on any other outside transition.

The exact request keys are listed in the same JSON under `opportunity.requestKeys`.

## 5. Historical oracle integrity

Corpus `tools/hns-damage-oracle/corpus.json` (pinned `1f42b74`): 2,523 scenarios (2,399 modelled, 124 engine-only); 2,522 direct production replays; 2,522 exact 16-roll matches; 1 explicit Slice-13 migration; 0 registered divergences; 33 fixture cross-references reproduced; 17 self-checks. These are the counts printed by `hns-damage-oracle` in `./ci.sh test`, not hand-counted here.

Roll order: `rolls[k]` is the damage at random factor `(85+k)%`, i.e. `WITH_RNG(RNG_DAMAGE_MODIFIER, i)` yields roll index `15 − i`. The Rapid Spin engine-roll test checks this exact mapping for all ten vectors.

## 6. Source-engine artifact inventory and checksums

SHA-256 values are recorded in `calculator_beta_freeze.json` under `evidenceInventory`, which includes the corpus, the census, the calculator bundle, the generated `Hns205MoveEffects.kt`, and every committed `*evidence*.json`. Rapid Spin evidence (`rapid-spin-evidence.json`) was produced **locally** from the pinned engine, and its reversed-order replay matched the committed artifact. Earlier slices' evidence artifacts are carried forward and re-checked by the `check` commands in `ci.sh`.

## 6a. Evidence-integrity enforcement

- The frozen evidence inventory (16 SHA-256 entries) is verified in the canonical test path by `tools/hns-move-mechanics/check_calculator_beta_freeze.py` (run from `./ci.sh test`). A corrupted or missing evidence file fails CI; `test_calculator_beta_freeze_check.py` proves it with mutation tests.
- The Rapid Spin lifecycle observations are verified exactly (Speed stage, Leech Seed flag, target HP) by `rapid_spin_evidence.py check`, and `test_rapid_spin_evidence_check.py` proves that a corrupted observation fails.
- The production freeze guard (`HnsCalculatorBetaFreezeGuardTest`) continues to protect the admitted move set.

## 7. Source generation and ABI integrity

- `generate_hns_move_effects.py --verify` against the pinned upstream: passes, reporting 928 resolved effects, 357 ordinary, and 6 unresolved (fail-closed) for the current generator. The starting-head count was not re-captured in this slice.
- Rapid Spin source contracts (`RAPID_SPIN_SOURCE_CONTRACTS`) match the pinned upstream. The contract suite refuses all 20 mutations it applies.
- The existing move-mechanics contracts (gyro, electro, rollout, escape, fixed two, variable multi-hit, scale shot, semi-invulnerable preview, Belch) pass against the same upstream.
- **No native ABI change in this slice.** Rapid Spin reuses existing operands (`Sheer Force` ability, held item, and selected-hit arithmetic). The native reader source is not modified by this slice.

## 8. Kotlin and QuickJS parity

- Kotlin boundary tests for Rapid Spin: 4 tests, passing. They cover admission, family tagging, Sheer Force and Technician eligibility, forged-metadata refusal in the bundle, and the forged-base-power refusal.
- Engine-roll parity: all 10 original-engine damage vectors (16 rolls each) reproduce through the shipped QuickJS bundle and Kotlin boundary, with the exact `15 − i` mapping. The Reflect vector is set through the observed defender side-status word, not the caller request, which the boundary strips.
- Bundle reproducibility: rebuilding `calc_bundle.js` from `tools/calc-bundler` changes exactly the committed single minified line.
- Freeze guard: `HnsCalculatorBetaFreezeGuardTest` passes against the recorded admitted map.

## 8a. Evidence correction made during review

The first Rapid Spin lifecycle scenarios used 60,000-HP fixtures. Review showed that Leech Seed heals overflowed the 16-bit HP field before the cap was checked (for example, 60,000 + 7,500 mod 65,536 = 1,964), so the recorded target HP values (1,934 and 9,427) were artifacts of the fixture, not evidence. Those values were discarded. The four lifecycle scenarios were regenerated at 200 HP with explicit bounds on both sides (0 < HP ≤ max). The corrected artifact was regenerated and re-verified in reversed order. The ten damage vectors were not changed.

## 9. Known limitations

- **Hardware:** `NOT_RUN`. Nothing in this report is device-verified.
- **Doubles:** refused for Rapid Spin; the calculator's Doubles family support is unchanged.
- **Rapid Spin scope:** the calculator reports current-strike damage only. It does not forecast whether Wrap, Leech Seed, or hazards are removed, and it does not model future Speed.
- **Repeated-strike caveats and the backlog items** in the freeze document §5 remain refused.
- **Performance:** no profiling was performed for this slice. The admission adds one set lookup; no latency or caching change is claimed.

## 10. Known correctness bugs and open questions

No calculator correctness defect is known in the admitted set. The following are open **test-framework** observations, not calculator defects, and are not resolved here:

- In the pinned test framework, `MOVE(..., hit: FALSE)` on the 100-accuracy Rapid Spin did not produce a miss: the user's Speed still rose. The miss scenario was therefore removed rather than treated as evidence.
- Zero-damage strikes (Protect-blocked Rapid Spin) produced an HP bar in the engine's animation stream, so the `NONE_OF { HP_BAR }` assertion was removed. The HP and Speed end-state assertions still pass. The cause is not established.

Both are recorded so that a future reviewer does not mistake them for validated behaviour.

## 11. Explicit beta acceptance disposition

- **Accepted for the bounded beta feature set**, subject to senior review of the reviewed PR.
- **Not accepted as a hardware acceptance.** AYN Thor and other device acceptance remain required and are `NOT_RUN`.
- **Not general beta readiness.** Save safety, ROM switching, controller shortcuts, Assistant fallback, and release-candidate validation remain open per [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md).
- H&S remains **ESTIMATED**.
- This acceptance does not lift or start the feature freeze. The freeze takes effect only when the reviewed PR merges.
