# H&S 2.0.5 battle lifecycle: issue #1 audit

## Starting point

Audited before production changes on 2026-09-22 against clean, fetched main
`e33a727ddc831af35021be7b7ac22a09ff93a083`. PR #71 is merged at that commit;
Actions run `35734338048` succeeded for its exact head
`102664c24172a1ed36cba391c0f11f86abff31fa` (all three jobs). Issue #9 is closed
for its documented bounded calculator scope.

## Pre-change acceptance audit

References below are to [the compatibility evidence](HNS_2_0_5_COMPATIBILITY_EVIDENCE.md)
and the production code/tests on the starting main, not assumptions from the old issue body.

| Issue #1 criterion | Starting-main evidence / disposition |
|---|---|
| Exact H&S hash and battle offsets/layout | Met for the observed layouts: §§1–3, 11.5–11.6 distinguish compiled symbols from release addresses. Exact profile hash is `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b`; `battleStateReadVerified=true`. |
| Invalid/ambiguous enemy never defaults to slot 0 | Native resolver initializes slot/battler to -1; lifecycle authority gates resolution, two opponents return AMBIGUOUS. Native tests cover unreadable authority, invalid mapping, position-based sides and two-opponent ambiguity. Real four-battler observation still missing. |
| Opponent switches/faints follow correct party member | Met: Scenario 41 asserts faint/absent/replacement; Scenario 44 (§11.10) asserts voluntary switch with outgoing Ledyba alive and incoming Spinarak bound to `gBattlerPartyIndexes`. Historical runtime evidence is retained; §14.5 records later fixture replay difficulty, not a retraction of the observation. |
| Battle end clears live defender | Met: Scenario 20 (§11.3) observes ENDING → INACTIVE despite stale raw battle words. Production presence becomes ABSENT, enemy slot -1. Native teardown tests and companion session tests cover clearing. |
| Doubles/partner track or honestly degrade | Source + synthetic evidence only at start (§§6.4, 11.3 K/L). Both sides with two present battlers withhold a unique active slot; this is the remaining runtime gap. |
| battleUiVerified remains false | Met: exact profile remains false; developer probe menu-driving does not establish app/controller authority. |
| interactiveControlsVerified remains false | Met: exact profile remains false. |
| Exact FireRed/Emerald regressions | Existing tests remain in the canonical suite; rerun required for this PR. Neither vanilla battle-state gate is promoted. |

Additional required validation is already documented in §11.3: authoritative `gMain.inBattle`,
wild entry/live HP/faint/exit, trainer entry (40), opponent replacement (41), player voluntary
switch (42), player faint/forced replacement and unknown window (43), active player/enemy
battler indices, and representative stat-stage transition. No repetition is needed solely to
replace these existing observations.

## Source-first encounter selection

Upstream checkout is exactly `PokemonHnS-Development/pokehns-expansion`, tag
`Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.
`include/config/battle.h` disables random and forced wild Doubles (`B_DOUBLE_WILD_CHANCE=0`,
`B_FLAG_FORCE_DOUBLE_WILD=0`). Early Route 29–31 trainer sight lines do not intersect.
The first explicit paired-trainer Doubles script on the ordinary Johto route is Amy & May
in Azalea Gym (`data/maps/AzaleaTown_Gym_hns/scripts.inc`). Route 32's story gate requires
Sprout Tower, Violet Gym and the Togepi egg first (§11.10.7).

An earlier candidate is the overlapping sight lines of Jin and Troy on Sprout Tower 3F:
Jin faces right from (8,14), Troy can face down from (11,11), sight range 4. The ordinary
two-trainer approach in `src/trainer_see.c` invokes `SetUpTwoTrainersBattle`;
`src/battle_setup.c` sets DOUBLE | TWO_OPPONENTS | TRAINER. This is initially a source
candidate, not a runtime claim. It reuses the existing legal progression and tower route.

## Natural four-battler runtime result

**PASS — Amy & May, Azalea Gym, 2026-09-22.** Scenario
[`70-amy-may-doubles.txt`](../tools/hns-runtime-probe/scenarios/70-amy-may-doubles.txt)
uses the right spider ride (`AzaleaTown_Gym_EventScript_Trigger_3`) and May's ordinary
`trainerbattle_double TRAINER_AMY_AND_MAY_HNS` script (pinned source lines 512–520).
It lands at (6,27), map 4/4, with two healthy non-egg party members. No attacks are needed.
The complete successful [capture log](../tools/hns-runtime-probe/evidence/issue1-amy-may-2026-09-22.txt)
contains two assertions 180 frames apart, 3,655 checked frames, **zero invariant violations and
zero script errors**, and exit status 0. A packaged replay of the Gym-entry checkpoint also passed (10,435 frames), followed by this
final Scenario 70 replay.

| Live field / production result | Observed value (both assertion samples) |
|---|---|
| Exact ROM SHA-256 | `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b` |
| `gMain.inBattle` | readable, true |
| `gBattlersCount` / `gBattleTypeFlags` | `4` / `0x0000000D` (DOUBLE + IS_MASTER + TRAINER) |
| `gBattlerPositions[0..3]` | `0,1,2,3` |
| `gAbsentBattlerFlags` / outcome | readable `0x00` / `0` |
| `gBattlerPartyIndexes[0..3]` (full u16 words) | `0,0,1,1` |
| Player / enemy party counts, raw and production | `3 / 2`; player count includes the Togepi egg, which is not a battler |
| BattlePokemon species, battler order | `152,165,161,167` (Chikorita, Ledyba, Sentret, Spinarak) |
| Live HP / max HP, battler order | `42/42,31/31,27/27,31/31` |
| `pokemon_read_battle_lifecycle` | `ACTIVE`, kind `DOUBLES` |
| Production battle presence | `PRESENT` |
| Production enemy resolution | `AMBIGUOUS`, 2 present opponents, enemy slot `-1`, enemy battler `-1` |
| Production player resolution | known=false, player slot `-1`, player battler `-1` |
| Production runtime role observations | both `BATTLER_RUNTIME_STATE_AMBIGUOUS`; neither publishes an ability/type payload from one arbitrary battler |

The pure contract rejects absent/unpopulated battlers, invalid/duplicate positions, duplicate or
out-of-range party indexes (including a nonzero high byte), unreadable authority, inconsistent
raw/production counts, wrong kind/flags, and invented role identities. The assertion cannot pass
on an overworld or Singles snapshot. It samples the actual production readers; it does not write
battle flags, positions, indexes, species or HP.

### Legal provenance and practical scope

The old temporary saves no longer existed, and no later ordinary-play save was available. The
chain was regenerated with `make-save.sh` and existing scenarios 30–34 and 45, then the bounded
[progression checkpoints](../tools/hns-runtime-probe/progression-issue1/README.md): Sprout Tower's
Silver event, Violet Gym, Togepi egg, Route 32, Union Cave, Azalea/Kurt, Slowpoke Well/Proton,
Pokémon Center healing, and Azalea Gym. Battery files came only from the game's save menu and
core SRAM flush. No ROM, save, save state, binary or screenshot is committed.

Fresh starter save SHA-256 for this local chain:
`60268ca876211f5d577ea756717f559300aabeaac39942ebed6e606fa8a2e427`.
The successful capture's Gym input SHA-256 was
`ddce596b142a7f1b4aa814b26749a4dde0bacc070db9e6adf013c63a5a60eb9a`.
These identify local saves, not distributable fixtures or mandatory hashes for future players.

Core: x86-64 mGBA libretro host build, version `0.11-1-e31759b`, source commit
`e31759b24e7a4e3899285ff720d7b573ac328ae7` (generated `version.c`), library SHA-256
`fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`.
Local paths are recorded in the capture log; no core or ROM bytes are included.

The earlier Jin/Troy candidate produced only Singles in the attempted ordinary-input timing
variants; none was accepted as multi-battler proof. Amy & May was selected because its explicit
script guarantees the intended shape once reached. No second partner/multi encounter was
runtime-proven. Pinned source distinguishes the TWO_OPPONENTS/MULTI/INGAME_PARTNER flags;
Battle Frontier multi/partner rooms require substantially later progression. The source candidate
at Sprout Tower was not reliably triggered. Extending the game further solely for a second
ambiguity case would exceed this bounded slice. Those flag variants retain source and synthetic
evidence, **not a runtime-verification claim**.

### What changed, and replay limits

**No production code or profile changed.** Native and view-model behavior already matched the
required honest degradation. Probe-only changes add the exact-hash/four-battler assertions,
full-width party-index sampling, and the controller progression needed to obtain the legal save.
The progression driver selects available early-game moves, handles replacement, dismisses story
text, bounds battle work by actual frames, and requires a victory outcome. `spamb` is only an
abbreviation for the exact recorded B/release sequence. The wrapper accepts a battery output only
after a successful run.

The checkpoint table records the successful runs used to build this chain. Battle RNG and roaming
NPCs can still make an earlier progression replay fail; Falkner and Proton required ordinary retries
from unchanged input saves. This is not a claim that a fresh full-chain replay wins every time.
Rejected runs were not runtime evidence. Scenario 70 from the accepted Gym save directly and
reproducibly exercises the missing contract; earlier Singles evidence is retained rather than
unnecessarily reproduced. Probe menu input does not verify the Android battle UI or controls.

## Regression and mutation evidence

- Native `test_hns_single_to_multi_clears_prior_enemy`: reused snapshot, Singles slot 1 → ambiguous
  four-battler DOUBLES and TWO_OPPONENTS shapes, all 24 battler-position permutations; then one
  opponent absent → correct remaining opponent identity, and next-iteration Singles recovery.
- View-model `singlesToAmbiguous_clearsPriorLiveDefenderAndRecovers`: a prior selected enemy is
  cleared to index -1 and no selected party member, then Singles recovers. This exercises real
  polling with a fake coordinator; it is not a rendered UI/device claim. The production defender
  binding uses `enemyParty.getOrNull(activeEnemyMemberIndex)`, without a slot-0 fallback.
- Existing native tests still cover unreadable lifecycle/battler authority, invalid mappings,
  teardown, replacement, player ambiguity, and exact FireRed/Emerald regressions.
- 18 additional pure four-battler contract controls (93 pure tracker checks total); developer-local
  strictness suite includes missing four-battler encounter, invalid kind and wrong ROM SHA rejection.
- Mutation: force ambiguous enemy to slot 0 → canonical native suite fails (5 failures including
  the new regression). Restored afterward.
- Mutation: infer opponent side from battler-index parity → the original all-present permutation
  case survived; adding an absent-opponent permutation exposed it (the new regression fails).
  Restored afterward. The strengthened test is the committed version.
- Mutation: suppress a view-model update when the new enemy index is negative → the new Kotlin
  transition test fails (739 tests, 1 failure). Restored afterward.

## Final criterion disposition

| Written issue #1 criterion | Disposition and evidence |
|---|---|
| Exact H&S hash and offsets/layout revalidated | **MET**: retained source/symbol/ABI/release evidence §§1–3, 11.5–11.6, plus exact-hash live four-battler matrix above. |
| Invalid/ambiguous enemy never silently becomes slot 0 | **MET**: actual Twins returns AMBIGUOUS/-1; native permutations, malformed/unreadable authority and slot-0 mutation rejection. |
| Opponent switches/faints follow the correct party member | **MET**: retained Scenarios 41 and 44; authoritative index mapping and stale-slot tests remain. |
| Battle end promptly clears enemy/live defender state | **MET**: retained Scenario 20 exit despite stale raw words, production gating, and view-model clearing/recovery regression. |
| Doubles/partner track or honestly degrade | **MET for the written degradation criterion**: natural four-battler Doubles runtime-proven; partner/multi flag variants remain explicitly source + synthetic only, with no unique enemy claimed. |
| `battleUiVerified` remains false | **MET**, unchanged. No Android UI/controller authority promotion. |
| `interactiveControlsVerified` remains false | **MET**, unchanged. |
| Exact FireRed/Emerald regressions remain green | **MET in canonical host/Kotlin regression coverage**; no new physical-device RC run is claimed. Neither vanilla production profile changed. |

Recommendation: **CLOSE #1 after senior review and merge** of this evidence/regression PR.
Leave #1 open for that review. #11 and #40 remain open; no calculator, app map, architecture or
interactive-control scope is added. Final check/PR identities are recorded in the review handoff.

## Final local validation

| Check | Result |
|---|---|
| `./ci.sh test` | PASS: 91 native reader tests, 93 pure tracker checks, 2,505 QuickJS assertions, 739 Kotlin/JVM tests (0 failures/errors/skips). |
| `./ci.sh source-check` | PASS against the pinned H&S checkout, including bootstrap negative/positive/guard controls. |
| `./ci.sh all` | PASS: canonical test suites and debug APK build. |
| `git diff --check` | PASS. |
| Probe build + strictness selftest | PASS: 25 developer-local cases, including wrong SHA and missing four-battler encounter rejection. |
| Packaged progression stage 20 | PASS: 10,435 frames; zero invariant/script errors; failed preliminary route did not overwrite the accepted output. |
| Packaged Scenario 70 | PASS: 3,655 frames; two strict live Doubles assertions; zero invariant/script errors. |

GitHub exact-head CI is recorded in the PR handoff after pushing. The production reader,
view-model, profile SHA allowlist, and both false UI/control capability flags are unchanged.
