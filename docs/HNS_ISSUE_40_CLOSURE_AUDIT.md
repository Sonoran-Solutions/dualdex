# Heart & Soul 2.0.5: issue #40 closure audit

**Audit date:** 2026-09-22
**Starting `main`:** `e67ee76a76c892061fd8c8416894f2c26f097161` (merge commit of PR #73; fetched and
verified clean before the audit began)
**Requested by:** the #40 closure gate — "is exact Pokémon Heart & Soul 2.0.5 now complete for the
bounded companion capabilities DualDex intentionally supports, such that #40 can close and the
project can enter the general pre-beta hardening phase?"

This document is the **disposition**. It is not a second evidence record: every claim below names the
primary artifact it rests on. The evidence itself lives in
[HNS_2_0_5_COMPATIBILITY_EVIDENCE.md](HNS_2_0_5_COMPATIBILITY_EVIDENCE.md) (§0-§19),
[HNS_2_0_5_CALCULATOR_CAPABILITY.md](HNS_2_0_5_CALCULATOR_CAPABILITY.md) (§14.14) and
[HNS_ISSUE_1_CLOSURE_AUDIT.md](HNS_ISSUE_1_CLOSURE_AUDIT.md), with the code, generated data and tests
on `main` as the final authority.

Issue #40's own body was written before most of this work landed. Its "Current DualDex state" section
(empty `sha256Hashes`, H&S unable to reach runtime `VERIFIED`, battle lifecycle unresolved, maps
unresolved, calculator unresolved) is **materially stale**, and its Section D/E/F checkboxes are
superseded by #1, #11 and #9 respectively. Where the body conflicts with this document, this document
wins. The body is annotated rather than rewritten only where an edit cannot destroy the historical
record; the checkbox list itself is left as the historical planning artifact.

Verification vocabulary is unchanged from the evidence document: **SOURCE / COMPILED SYMBOL / ABI /
RUNTIME VERIFIED**, plus **HOST VERIFIED** (executed in the canonical host suites, no ROM) and
**TEST-ONLY** (asserted by a test over code/data, no live observation).

---

## 0. Prerequisite bookkeeping (before the audit)

| Item | Result |
|---|---|
| PR #73 exact-head CI | **GREEN.** `gh pr checks 73` → `Native & Unit Tests`, `H&S Source Validation`, `Build Debug APK` all `pass` on head `1ac25ff20c38086a54d95d7a2ab2619bcb7b5846` (run `35761578908`, `conclusion: success`) |
| PR #73 merged? | **Already merged** (no action needed): state `MERGED`, merge commit `e67ee76`, `mergedAt 2026-09-22T18:07:47Z` |
| Issue #11 closed? | **CLOSED** at `2026-09-22T18:07:48Z`, one second after the PR #73 merge |
| `origin/main` fetched | Yes; `main` == `origin/main` == `e67ee76a76c892061fd8c8416894f2c26f097161` |
| Working tree | **CLEAN** at the start of the audit (`git status --porcelain` empty) |
| Issue #1 | CLOSED `2026-09-22T15:11:39Z` |
| Issue #9 | CLOSED `2026-09-22T13:40:41Z` (PR #71 merged `e33a727` at `13:39:58Z`; run `35734338048` success on head `102664c`) |
| Issue #29 | CLOSED `2026-09-19T23:34:19Z` |

---

## A. Exact ROM identity / trust

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| A1 | Supported target is exactly `Release-v2.0.5` | **MET** | Pinned tag/commit in `native/src/pokemon_reader.c:101`, the three generated ABI headers (`hns_battle_pokemon_layout_gen.h:9`, `hns_challenge_settings_layout_gen.h:9`, `hns_live_battle_layout_gen.h:9`), `Hns205MapData.kt` (`UPSTREAM_TAG`), `HeartAndSoul205DataPack.kt:17`, and `Hns205MapDataIntegrityTest.kt:117` which asserts the pinned SHA as a test constant |
| A2 | Upstream commit is exactly `1f42b74dff0e9fe942419845d040663dd829a973` | **MET** | `native/src/pokemon_reader.c:101`; `Hns205MapData.UPSTREAM_COMMIT_SHA` asserted at `Hns205MapDataIntegrityTest.kt:117` against the literal `PINNED_UPSTREAM_COMMIT` at `:493`; `hns_route.py` / `generate_hns_map_data.py` refuse any other revision (`test_hns_route.py` provenance tests) |
| A3 | Supported ROM SHA-256 is `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b` | **MET** | Recorded in the evidence document §1.2 (lineage via the UPS footer) and asserted live during this audit: `sha256sum` of the local release ROM returns exactly that value |
| A4 | That hash is intentionally allowlisted in `heart_and_soul.json` | **MET** | `app/src/main/assets/profiles/heart_and_soul.json:17-19` — a one-element `sha256Hashes` array containing that exact digest, with `isVerified: true` (`:20`) and `memoryLayoutVerified: true` (`:21`) |
| A5 | Exact hash reaches verified compatibility / live-memory trust | **MET** | `RomCompatibility.kt:55-56` (status requires `profile.isVerified && memoryLayoutVerified`, then the SHA match); `RuntimeRomTrust.kt:56-63` (`exactRuntimeVerified` requires `EXACT_SHA256`, profile verified, layout verified, and the runtime hash in `profileSha256Hashes`) and `:82-83` (`mayReadLiveMemory == exactRuntimeVerified`). `battleStateReadVerified: true` (`heart_and_soul.json:22`) additionally opens `mayReadBattleState` (`RuntimeRomTrust.kt:95-96`). Test: `RomCompatibilityTest.kt:569` `bundledHeartAndSoulExactPromotedHashUnlocksLiveMemory` |
| A6 | Recognized H&S builds with another hash remain fail-closed | **MET** | `RomCompatibilityTest.kt:715` (`bundledHeartAndSoulOneBitDifferentHashStaysUnverified`) and `:735` (`bundledHeartAndSoulRecognizedHeaderWithoutExactHashStaysUnverified`); header recognition alone yields `RECOGNIZED_UNVERIFIED` (`RuntimeRomTrust.kt:66-71`) with `mayReadLiveMemory` false. Native detection is title-based only (`pokemon_reader.c:386-393`) and never grants trust |
| A7 | No ROM bytes are committed | **MET** | `git ls-files` contains no `.gba`/`.ups`/`.sav`/`.srm`/`.elf` (only the two ~6.5 MB bundled mGBA cores). The probe's capture scripts copy saves to a temporary directory and discard them; only digests and JSON/log evidence are committed |
| A8 | The UPS patch digest is never confused with the patched-ROM SHA | **MET** | `0ec228d5…f5e` appears in exactly two places, both labelled as the *patch* digest (`HNS_2_0_5_COMPATIBILITY_EVIDENCE.md:62-63`); it appears nowhere in any profile. The profile hash is the ROM digest above. `HNS_2_0_5_COMPATIBILITY_EVIDENCE.md:950` re-states the rule |

**Section A: MET.**

---

## B. Memory / layout

Audited against the **verified release-ROM layout**, not the from-source build. §11.6 records the
systematic reason this matters (the from-source build's EWRAM is shifted; its IWRAM `gSaveBlock1Ptr`
is 24 bytes low), and every configured address below is a release-derived value.

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| B1 | Player party / count | **MET** | `pokemon_reader.c` `.player_party_offset = 0x34764`, `.player_party_count_offset = 0x342A4` (release values, §11.5); `PARTY_DISCOVERY_AUTHORITATIVE_STATIC` so the count symbol is authoritative and no blind scan runs; `test_hns_config_matches_release_runtime_evidence`, `test_hns_party_counts_are_independent_symbols`, `test_hns_release_rom_party_fixture`, `test_hns_authoritative_zero_count_defeats_decoy_scan` |
| B2 | Enemy party / count | **MET** | `.enemy_party_offset = 0x342B4`, `.enemy_party_count_offset = 0x342A5` — the two counts are *independent adjacent symbols*, not `party - 4`; `test_hns_enemy_party_count_is_authoritative`, `test_hns_authoritative_enemy_count_requires_reader_gate` |
| B3 | `BattlePokemon` stride / HP / stats | **MET** | `.battle_mons_size = 136` (not 88), `.battle_mons_hp_offset = 0x2A` (not `0x29`), stat stages `0x18`; attack/defense/speed/spatk/spdef offsets come from the generated ABI table (`HNS_BATTLE_POKEMON_*`), so production and source-check cannot drift without a compile failure; `test_hns_battle_pokemon_layout_fields`, `test_hns_battle_pokemon_live_layout_pins` |
| B4 | battler → party index | **MET** | Explicit `.battler_party_indexes_offset = 0x144` with a decoy planted at the old derived `gBattleMons - 24` form; `test_hns_active_battler_index_is_the_real_battler`, `test_hns_trainer_opponent_slot_resolves_from_battler_index`, `test_hns_player_switch_slot_follows_battler_indexes`, `test_hns_invalid_battler_indexes_fail_closed` |
| B5 | Battle type / count / positions / absent flags | **MET** | `.battle_type_flags_offset = 0xAC`, `.battlers_count_offset = 0xB0`, `.battler_positions_offset = 0x238`, `.absent_battler_flags_offset = 0x30A`, `.battle_outcome_offset = 0x12C`; consumed by `pokemon_read_battle_lifecycle`; `test_hns_lifecycle_maps_to_distinct_active_enemy_states`, `test_hns_trainer_battle_classification`, `test_hns_single_to_multi_clears_prior_enemy` |
| B6 | SaveBlock1 pointer / ASLR | **MET** | `.save_block1_ptr_gba_address = 0x030041D8` (IWRAM), base `EWRAM + 0x124A8`, 128-byte window, size 15760. `resolve_save_block1_base` (`pokemon_reader.c:2042-2084`) reads the pointer fresh every call and rejects non-EWRAM, unaligned, out-of-window, and non-fitting bases; `test_hns_saveblock1_pointer_resolution`, `test_hns_saveblock1_invalid_pointer_fails_closed`, `test_hns_saveblock1_aslr_window_is_not_fixed`. Runtime-observed across two distinct ASLR offsets (`88`, `116`) |
| B7 | SaveBlock3 challenge settings | **MET** | `.save_block3_ptr_gba_address = 0x03000178`, base `EWRAM + 0x9218`, 32-byte `ChallengeSettings` from the generated layout; the pointer **value must equal** the compiled base exactly, so a stale pointer or wrong ROM can only fail closed; `test_hns_challenge_settings_zero_is_observed_not_unknown`, `..._representative_enabled`, `..._option_style_and_isolation`, `..._out_of_domain`, `..._fail_closed`, `..._no_stale_across_games` |
| B8 | GBA IWRAM / EWRAM mapping | **MET** | `libretro_host.c` consumes `RETRO_ENVIRONMENT_SET_MEMORY_MAPS` (11 regions observed on the bundled core), copies only descriptor scalars + base pointers, and is the only production caller of the pure bounds-checked `gba_memory_map` module; `test_gba_memory_region_translation`, `test_gba_memory_bounds_rejection`; a read after unload is rejected |
| B9 | Reset / unload / switch clearing | **MET** | `gba_memory_map_clear` on load, a NULL map (including a core that publishes nothing), reset and cleanup (`libretro_host.c:67,73-79`); `pokemon_reader_reset()` (`pokemon_reader.h:327-329`); `CompanionViewModel.clearLiveMemoryObservations()` called on load/failure/ROM switch/session clear (`:194,209,221,229,252,269`) plus a re-assert after publishing new trust (`:243-244`); `RomCompatibilityTest.kt:227` `switchingVerifiedRomToUnsupportedRom_clearsAllObservationsImmediately`, `:377` `pollTick_underUnverifiedOrUnsupported_neverInvokesLiveMemoryReaders` |
| B10 | Fail-closed for invalid pointers / unreadable state / trust loss | **MET** | Every reader returns false / unknown rather than a substituted value: `config_is_usable` guards; `resolve_save_block1_base` and the SaveBlock3 pointer check are all-or-nothing; the battle path requires `gMain.inBattle` (`pokemon_reader.c` lifecycle gate) with `test_hns_unreadable_lifecycle_gate_never_active`; `mayReadLiveMemory`/`mayReadBattleState` gate all profile-dependent reads (`RuntimeRomTrust.kt:76-96`); `test_hns_battle_lifecycle_gates_enemy_state`, `test_hns_stale_battle_mon_cannot_invent_opponent`, `test_unknown_game_fails_closed` |

`#8`'s generic layout-authority rewrite was **not** required and was not performed — the audit
confirms the current architecture is exact-version-safe with the release-derived values above.

**Section B: MET.**

---

## C. Party / data fidelity

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| C1 | Party count and slots | **MET** | Authoritative count policy (B1/B2); stale cache cannot override a zero count and is bounded by the count (`test_hns_stale_cache_cannot_override_zero`, `test_hns_authoritative_count_bounds_stale_slots`, `test_hns_corrupt_authoritative_slot_fails_closed`) |
| C2 | Widened species / move IDs are not truncated or mislabelled | **MET FOR BOUNDED SCOPE** | `HeartAndSoul205DataPack` carries the pinned domain (1,427 species, 934 moves, 310 abilities) and sets `allowGlobalFallback = false`, so an ID the pack does not define resolves to *unknown* rather than to a vanilla entry |
| C3 | Species / form naming | **MET FOR BOUNDED SCOPE** | Generated from the pinned checkout; `isSpeciesAuthoritative`; unknown IDs degrade rather than substitute |
| C4 | Ghost Grey ID isolation | **MET** | The earlier global `SpeciesDatabase.registerCustom()` pollution of IDs 500-502 was removed and replaced by profile-scoped overlays (`a57800f`); `GameDataPackTest` covers collision prevention and cross-profile purity; `HeartAndSoul205DataPack.allowGlobalFallback = false` |
| C5 | Move names / data | **MET FOR BOUNDED SCOPE** | Same generated pack; `Hns205MoveEffects` / `HnsMoveMechanicsRegistry` carry only the source-proven ordinary `EFFECT_HIT` subset and fail closed for everything else |
| C6 | Ability identity / stored ability slot | **MET** | `expansion_ability_num_*` reads `abilityNum` from the correct bit field, not the Gigantamax bit; `test_expansion_ability_num_is_not_the_gigantamax_bit`, `test_vanilla_ability_slot_parsing_unchanged` |
| C7 | Live effective battle ability / types, where claimed | **MET FOR BOUNDED SCOPE** | Live effective ability + types are read from `gBattleMons` (`BattlerRuntimeState`, H&S-only config fields left zero for every other game) and are boundary-owned in the calculator; host + synthetic covered by `CalcHnsAbilityTest`, `HnsBattlerRuntimeStateTest`, `test_hns_battler_state_unresolved_ability_stays_raw`, `test_hns_battler_state_type_representations`. The `HeartAndSoul205DataPack` header explicitly declares that *declared* abilities are not evidence of a live Pokémon's ability |
| C8 | Held items | **MET FOR BOUNDED SCOPE** | Exact identity from the pinned item catalogue (`Hns205ItemCatalogue`, `ITEM_ID_MAX = 900`); the live current item is read at `BattlePokemon.item` (`0x30`) with the current-item-over-stored-item precedence, and the faint window clears it (`test_hns_battler_state_item_none_is_observed_zero`, `..._current_item_follows_rewrite`, `..._out_of_domain_item_stays_raw`). Consumption of an item's *damage effect* refuses (Section F) |
| C9 | Level / HP / stats | **MET** | Decoded from the release layout; live HP is runtime-verified in battle; `test_heart_and_soul_party_and_battle_hp_sync` |
| C10 | IV / EV | **MET** | read from the substructs with `has_evs`/`has_ivs = true`; vanilla bit-for-bit behaviour asserted unchanged (`test_vanilla_ability_slot_parsing_unchanged` and the shared-extractor tests) |
| C11 | Hidden nature vs displayed nature | **MET, with an explicit UI limit** | Both are reported — `nature` (displayed `GetNature()`), `hiddenNature` (`pid % 25 ^ hiddenNatureModifier`) and `natureModified` (`PokemonModels.kt:53-58`); `test_expansion_nature_and_shiny_are_reported_honestly`. Which one the party UI *should* show is explicitly **NOT YET VERIFIED** in the evidence document. The product represents the uncertainty rather than asserting one; that satisfies C11 at the confidence level the UI actually promises |
| C12 | Shiny state policy | **MET (intentionally tri-state)** | `SHINY_UNKNOWN`/`SHINY_NO`/`SHINY_YES` with `shinyIsKnown` (`PokemonModels.kt:65-80`); the ambiguous band between the possible odds values is reported `UNKNOWN` rather than guessed, and the provider comment now states precisely why (the per-Pokémon verdict is derived from the party slot and is not handed the challenge-settings snapshot) |
| C13 | Eggs / fainted / invalid reads | **MET** | Counts include eggs while battle roles do not (the four-battler audit records a Togepi egg in a count of 3 with 2 battlers); fainted and transition windows degrade to unknown (`test_hns_player_faint_forces_unknown_until_replacement`, `test_hns_stale_player_slot_cannot_survive_faint`, `test_hns_stale_enemy_slot_cannot_survive_replacement`, `test_hns_corrupt_authoritative_slot_fails_closed`) |
| C14 | Clearing stale state | **MET** | Sustain-invalid-read debounce clears observations (`RomCompatibilityTest.kt:260,281`); battle teardown clears battler state and stat stages (`CompanionViewModel.kt:412-420,530+`); `test_hns_battle_exit_clears_production_presence`, `test_hns_single_to_multi_clears_prior_enemy`, `test_hns_battler_state_teardown_and_profile_switch` |

**Section C: MET FOR BOUNDED SUPPORTED SCOPE.** Nothing here is promoted beyond what the UI promises.
Unknown / tri-state / fail-closed representation is used deliberately and documented as such.

---

## D. Battle lifecycle (issue #1 is authoritative)

Issue #1 is **CLOSED**, and [the issue #1 closure audit](HNS_ISSUE_1_CLOSURE_AUDIT.md) is the
authority for this section. It was audited before production changes against clean main `e33a727`.

| # | #40 Section D requirement | Disposition | Evidence |
|---|---|---|---|
| D1 | Wild battle entry | **MET** | `scenarios/10-wild-battle-entry.txt`, `20-wild-battle-full.txt`; RUNTIME (§11.3) |
| D2 | Trainer battle entry | **MET** | `scenarios/40-trainer-battle-entry.txt`; RUNTIME (§11.3, PR #48) |
| D3 | Exit / stale clearing | **MET** | Per-frame probe invariant (no stale opponent survives exit) + committed log `evidence/issue1-amy-may-2026-09-22.txt` showing `battle=inactive`; HOST `test_hns_battle_exit_clears_production_presence` |
| D4 | Opponent faint + replacement | **MET** | `scenarios/41-opponent-replacement.txt` enforces the full 4-phase state machine (`await-enemy-replacement 0 1 3500`); RUNTIME (§11.3/§11.9) |
| D5 | Opponent voluntary switch | **MET, weakest link** | `scenarios/44-opponent-voluntary-switch.txt`; §11.10.8 claim matrix with a fatal commit-frame contract (the outgoing mon must still be alive). Single probabilistic PASS (10/14 attempts, §11.10.4); the probe deliberately disclaims reading any AI decision byte |
| D6 | Player voluntary switch | **MET** | `scenarios/42-voluntary-switch.txt`; RUNTIME (§11.3, PR #48) |
| D7 | Player faint + forced replacement | **MET** | `scenarios/43-player-faint-forced-replacement.txt` (`await-player-forced-replacement 0 1 40000`); RUNTIME (§11.9, PR #49) |
| D8 | Live HP | **MET** | Committed goldens `golden-a-neutral-tackle.log`, `golden-c-stat-stage-scratch.log`; slot binding in `pokemon_reader.c` `apply_battle_mon_hp` |
| D9 | Representative stat-stage transitions | **MET FOR THE WRITTEN "REPRESENTATIVE" SCOPE** | Committed `golden-c-stat-stage-scratch.log` (`stages=0,0,-1,0,0,0,0,0`, Def −1 from Leer) and §11.3's `6 -> 5` transition |
| D10 | Active battler / party-slot authority | **MET** | Per-frame probe invariant; native contracts `test_hns_active_battler_index_is_the_real_battler`, `test_hns_trainer_opponent_slot_resolves_from_battler_index`, `test_hns_invalid_battler_indexes_fail_closed` |
| D11 | Natural four-battler Doubles | **MET FOR BOUNDED SUPPORTED SCOPE** | `scenarios/70-amy-may-doubles.txt` + committed log `evidence/issue1-amy-may-2026-09-22.txt` (`kind=DOUBLES battlers=4 flags=0x0000000D indexes=0,0,1,1`, 0 invariant violations) |
| D12 | Ambiguous multi-opponent behavior | **MET (honestly degraded)** | `pokemon_read_battle_lifecycle` → `AMBIGUOUS` with slot/battler `-1`; `BattleModels.kt:71-82,110-111`; render guard; calculator refuses the live format. Partner/multi flag variants remain source + synthetic and are represented as ambiguous, which is the product's written criterion, not a gap |

`battleUiVerified` and `interactiveControlsVerified` are **`false`** in
`heart_and_soul.json:23-24` and are **not** promoted by this audit — no Android-UI or controller
authority evidence exists, and `RomHackProfileTest.kt` enforces the flags for every bundled profile.

**Section D: MET**, with two bounded qualifications stated honestly rather than promoted: stat-stage
evidence is representative, not exhaustive; and partner/multi (non-natural) battles remain source +
synthetic and honestly degraded.

> **Quoted for accuracy in #40 (does not change the disposition):** most Section D runtime PASSes are
> recorded in the evidence document and the merged PRs rather than as raw captures on `main`. Only the
> Amy & May Doubles log and the C4d/C4e goldens are committed logs. D1, D2, D4-D7 and D10 are backed by
> committed scenario scripts whose assertions are machine-enforced, plus their recorded PASS in the
> evidence document and the merged PR — reproducible scripts, not independently re-runnable evidence
> inside the repository. See "Remaining work" item R2.

---

## E. Maps / multi-region (issue #11 is authoritative)

Issue #11 is **CLOSED** at the PR #73 merge. §12 (and its runtime half §12.9) is the authority.

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| E1 | Exact H&S location strategy, explicit — not `else -> Johto`, not name-based | **MET** | `LocationResolver.kt:42-47` selects by profile id with `UNVERIFIED` as the fallback; `:110-122` is an exhaustive `when` over the strategy; `:132-137` maps an unknown pair to `UNKNOWN_MAP_ID`. A source-guard test asserts no production path names `JOHTO_DEFAULT`/`NewBarkTown`/`RegionId.JOHTO` as a default (`HnsLocationRuntimeEvidenceTest.kt:872-901`), with a mutation control that restores an `else -> Johto` default and must fail |
| E2 | Release map group/number table for 2.0.5, including groups 25-30 | **MET** | Pinned `group_order` read directly from upstream `data/maps/map_groups.json`: 25 Alola outdoor, 26 Alola indoor, 27 IndoorDynamic, 28 Sinjoh, 29 IndoorSinjoh, 30 SpecialArea — exactly the sequence #40's reconnaissance said the stale asset lacked. Generated `Hns205MapData.kt` covers groups 0..30 + 56 (560 locations, 120 sections). Byte-identical regeneration verified from the pinned checkout; the table is cross-checked against every upstream group `map.json` (`Hns205MapDataIntegrityTest.kt:298-338`) and the 90 presentable rectangles against the three layout grids (`:341-406`) under `./ci.sh source-check` |
| E3 | Runtime Johto location reads | **MET (bounded: 1 ROM, 6 checkpoints)** | `evidence/location-runtime-evidence.json` + `location-captures-20260922.txt`: New Bark Town `0/0` (×2), Route 29 `0/11`, Route 30 `0/12`, Violet City `0/2`, Azalea interior `4/4`, each asserted over 120-240-frame windows with 0 unreadable frames, read through the **production** reader. One checkpoint is the first runtime observation of an interior map group |
| E4 | Real Johto transition evidence | **MET** | `scenarios/80-location-map-transition.txt` drives New Bark Town `(0,11)` LEFT → Route 29 `(69,16)` and back RIGHT → `(0,0)` at `(10,10)`, both `240/240` matched, 0 invariant violations. The generator encodes two measured facts a naive route gets wrong (edge-triggered `connections`, and arrival x ≠ departure x) |
| E5 | Source-backed Kanto routing | **MET FOR BOUNDED SUPPORTED SCOPE — literally source-only** | Kanto identity, canvas rectangles and the ReceptionGate↔Route 22 edges come from the pinned source plus the engine-predicate inventory (`hns_route.py inventory`, machine-checked by `inventory --check` in `./ci.sh source-check`). **There is no runtime Kanto observation anywhere**, and the Johto-only runtime coverage is machine-asserted (`HnsLocationRuntimeEvidenceTest.kt:940-953`). This is the exact meaning of the preserved limit below |
| E6 | Sinjoh / Alola intentional no-canvas presentation | **MET** | `RegionId.hasCanvas` false for both (`LocationModels.kt:19-21`); the generated table marks 30 sections non-presentable with `gridX/gridY = -1`; `LocationResolver.kt:162` forces `presentable = generated.presentable && region.hasCanvas`; browse override is refused for undrawable regions. Tests assert no marker on any canvas and that a reconciled section is removed rather than relocated |
| E7 | Unknown maps fail closed | **MET** | `Hns205MapData.kt:1015-1017` returns null for a negative or undefined group/number; `LocationResolver.kt:132-137`; four machine-readable controls in the runtime-evidence JSON (57/0, 68/1, 0/200, 1/99) assert null/`UNKNOWN_MAP_ID` with no header and no marker; native side fails closed too |
| E8 | Visible browsing never changes native layout interpretation | **MET** | `MapScreenBrowsingIsolationTest.kt` drives the full browse sequence on pairs that legitimately mean different things in different games ((0,9) is Mahogany Town for H&S and Littleroot Town for Emerald), covers every `RegionId`, and structurally asserts that the map screen state cannot reach the profile/`CompanionViewModel` and that `strategy` has a `private set`; production derives the strategy only from `_activeProfile` |
| E9 | No invented marker / canvas state | **MET** | Single marker gate in `MapScreenPresenter.kt:316-334` re-derives the resolution from `(strategy, playerLocation)` and requires it to own the presented section; consumed by the real view |

**Preserved limits, both still stated and still accurate** (this audit did **not** erase them, and
treats them as non-failures because #40's product contract does not require them):

* **No cross-region H&S transition was runtime-verified.** Machine-readable
  (`any_edge_runtime_verified: false`, asserted by a test) and now stated explicitly in §12.9.7.
* **The Map tab was not device-verified.** No Android device, emulator package or AVD is reachable
  from the agent environment. The remaining human check is named in §12.9.7 and is carried into the
  general hardening phase (Section H).

**Section E: MET FOR BOUNDED SUPPORTED SCOPE.** Kanto routing is source-backed, not runtime-verified,
exactly as the preserved limit says.

---

## F. Calculator / mechanics (closed #9 scope, not the original aspiration)

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| F1 | Exact H&S ordinary live Singles reaches at most `ESTIMATED` | **MET (structurally enforced)** | H&S ruleset declares `ceiling = CalcSupport.ESTIMATED` (`CalcCapabilityPolicy.kt:988`) while only the vanilla ruleset declares `VERIFIED` (`:1004`); assembly at `:1215-1216` reaches `VERIFIED` only when the ceiling is `VERIFIED` and no limitations remain; presentation derives from support only; asserted by `CalcCapabilityPolicyTest.kt:415` and `CalcHnsC4eProductionBoundaryTest.kt:344-345` (`assertFalse("H&S may never be presented as verified", …)`) |
| F2 | Source/runtime operands for the admitted subset are present | **MET FOR BOUNDED SCOPE** | Each admitted operand maps to a named native `BattlerRuntimeState` field (effective types, raw stats, stat stages, current item, effective ability, HP/max HP, badge boosts, attacker `status1`, `gBattleWeather`, `gSideStatuses`, `gFieldStatuses`, four safety volatiles, ten persistent volatiles, gimmick, live format, Doubles target count) with a positive test in `native/tests/test_pokemon_reader.c`, `CalcHns*Test.kt` or `test_js_calc.c`, and neutral values runtime-observed on the official ROM (`evidence/golden-c4e-live-operands.log`) |
| F3 | Unsupported / unresolved dynamics refuse | **MET** | 55 of 63 `CalcLimitation` values are blocking; the request boundary returns `request = null` on refusal (`CalcRequestBoundary.kt:1326,1344,1353`); the engine itself throws on a Doubles spread without a target count (`entry.js:459-465`) |
| F4 | Challenge settings affecting damage are observed and gate | **MET** | `pokemon_read_challenge_settings_gba` reads `SaveBlock3.challengeSettings` at the release address; only `OBSERVED` yields runtime rules (`CalcRequestBoundary.kt:158-212`); `optionStyle`, Fairy toggle, Random Types, Random Type Effectiveness (unreadable **or** active blocks), Base Stat Equalizer and Random Moves are gated (`CalcCapabilityPolicy.kt:1035-1098`); native + Kotlin + inventory tests. An **inventory test asserts every required rule read is blocking** |
| F5 | Doubles spread fail-closed where operands are insufficient | **MET** | Live format gate (`gBattlersCount` must be the observed Singles `2`) refuses with `HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED`; spread damage requires an explicit `field.targetCount == 2`, otherwise `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`; engine-level throw as the last line of defence; ~10 boundary tests plus a host fixture |
| F6 | No H&S result labelled `VERIFIED` beyond documented capability | **MET** | Same sites as F1; F1's cap is structural, so no request shape can bypass it |
| F7 | FireRed/Emerald calculator regressions intact | **MET** | `./ci.sh test` runs the QuickJS host calculator suite (`ci.sh:491` → `calc_test`, `ci.sh:201/231`); the vanilla oracle matrix (`verify_goldens.py`, `test_audit_vanilla_layout.py`, `verify_move_targets.py`) re-derives every committed golden independently; `CalcVanillaGoldenBoundaryTest.kt` drives them through the production boundary; vanilla profiles are unchanged. Green locally and in Actions on `e67ee76` |

Closing #40 does **not** require making every H&S mechanic calculable, and this audit does not promote
any mechanic. The intentionally unsupported set (Doubles computation, damage items, non-ordinary move
effects, dynamic-type/volatile positive states, active gimmicks, non-neutral live status, unmodelled
weather/screens/topology) refuses by design.

*Post-#40 note (field-status layout repair)*: The live `gFieldStatuses` runtime address has been
corrected from legacy `0x2F4` (withdrawn as `gBattleControllerExecFlags`, which caused a bogus Magic
Room blocker `0x00000001` on the AYN Thor) to the official release ROM layout `0x2E8`, backed by
release symbols (`artifacts-default/pokehns.map` and literal pool disassembly) and positive runtime
transitions (0 -> 1 -> 0 for Magic Room, 2 for Trick Room, 0x100 for Electric Terrain via `runtime_battle_probe`)
and pinned in `test_hns_field_statuses_thor_regression`.

**Section F: MET FOR THE ACCEPTED BOUNDED SCOPE.**

---

## G. Cheats / Assistant / security optionality

This is the section that most needed reconciling, because #40's own roadmap places the **general
hardening pass after #40**, and #40's own compatibility rule is:

> "Optional features may be explicitly unavailable for H&S if they fail closed and the UI is honest;
> 'fully compatible' does not require inventing unsafe support for every optional feature."

Exact observed behaviour on current `main`:

| Feature | H&S behaviour on current main | Certified for H&S? |
|---|---|---|
| **Cheats (#17)** | `CheatManager.getPresetsForGame()` selects built-in presets by **display-name substring** (`CheatManager.kt:182-186`: "heart"/"soul"/"emer" → the Emerald/H&S family), so exact H&S 2.0.5 is offered codes that were never verified against it. The Cheats UI calls them "standard codes for this game" (`CheatsScreenView.kt:175`) and badges each row `PRESET` (`:219-241`), and a **single toggle** calls `toggleCheat()` → `applyCheats()`, which sends the enabled payload to the core (`CheatManager.kt:112-121`, `:132-155`). Presets are forced `enabled = false` (`:39`, `:126`), so nothing is applied automatically — but one explicit user click can apply an unverified code and mutate the running game and its save. The unknown-game branch also still returns the `XXXXXXXX XXXXXXXX` placeholder (`:288-297`) | **No — known uncertified surface.** Disabled-by-default prevents *automatic* application; it does **not** make the offer fail-closed, verified, or harmless |
| **Assistant (#13)** | `RomHackAssistant.generateOfflineKnowledgeResponse()` still contains FireRed item/location text interpolating `$gameName` and Ghost Grey species facts gated on the game name, so an offline fallback can present FireRed-style facts under the H&S name | **No — known uncertified surface.** It reads no memory and cannot corrupt a save, but it is not an honest unavailable/not-verified fallback either |
| **Security / privacy / backup (#12)** | Unchanged; general beta requirement, no H&S-specific coupling | General, not H&S |
| **Thor shortcuts (#14)** | Unchanged (advertised but not implemented); independent of ROM identity | General, not H&S |
| **gameId fixture drift (#15)** | Contributor example + profile test fixtures; does not affect H&S runtime paths | General, not H&S |

**Section G disposition: DELEGATED TO GENERAL PRE-BETA HARDENING — and these surfaces are explicitly
NOT certified by closing #40.**

To state the rationale precisely, because "delegated" and "safe" are not the same claim:

* **#17 and #13 remain known, user-facing, unsafe/unverified optional surfaces.** They are not
  fail-closed, not "unavailable", and not honestly labelled as unverified. An enabled H&S preset can
  mutate the running game and its save; an offline Assistant answer can present wrong base-game facts
  under the H&S name.
* They are allowed to remain open **because #40 is defined as the exact H&S core / live-companion
  integration gate**, and the repository intentionally schedules optional-feature hardening as the
  *next* phase (`RELEASE_CHECKLIST.md:33-38`). That is a phase-ordering decision, not a safety claim.
* **Closing #40 does not certify Cheats or Assistant for H&S.** It certifies the core H&S live
  companion, battle, map and calculator integration audited in Sections A-F.
* The closure rule "unsupported capabilities fail closed" therefore applies to **the H&S
  memory/battle/map/calculator capability surfaces audited in A-F**. It does **not** extend to these
  separately delegated optional features, and this audit does not assert that it does.

This audit deliberately did **not** implement #12, #13, #14 or #17; implementing them here would
expand scope rather than close this gate.

---

## H. Hardware validation scope reconciliation

Issue #40's body carries a large AYN Thor RC matrix, while its own "What happens after this gate"
section says the general hardening/hardware-soak phase follows the gate and uses H&S as the primary
complex ROM. **This audit resolves that contradiction against repository intent, and does not claim
any hardware test was run.**

The repository's own current roadmap resolves it unambiguously in favour of the second reading:

* `RELEASE_CHECKLIST.md:30-35` — step 1 is "Finish exact Heart & Soul 2.0.5 completely"; step 2 is
  "Finish the remaining general pre-beta product/release blockers using H&S as the primary complex
  validation ROM", and step 2's bullets are exactly the RC matrix: save safety and recovery, ROM
  switching, process death/reboot, suspend/resume, fast-forward, save states, cheat gating, Assistant
  fallback behavior, credential/backup policy, controller shortcuts, second-display reconnect, and
  **extended Thor soak testing**.
* `RELEASE_CHECKLIST.md:103` requires the Battle tab and profile-aware presentation to be validated
  **on-device** before the beta release — which is the beta gate, not the H&S integration gate.
* `RELEASE_CHECKLIST.md:66-73,127` place device rings and soak before `0.9.0-beta.1`.
* `README.md` "Project status" states the same sequence: finish H&S → general pre-beta hardening
  **using H&S as the primary complex validation ROM** → R.O.W.E. → Unbound → RC.

**Disposition: DELEGATED TO GENERAL PRE-BETA HARDENING (the preferred interpretation, and the one the
repository actually encodes).**

* H&S 2.0.5 is the **primary complex validation ROM** for the next phase.
* Hardware soak on the AYN Thor **is required before beta** and is **not** a prerequisite for declaring
  the H&S software integration complete; the Thor matrix moves wholesale into the hardening phase.
* **No hardware test was run and none is claimed.** No device, AVD or emulator package is available in
  this environment, so every row of #40's Section H remains unperformed. This audit explicitly does not
  mark any of them complete.
* The one hardware-adjacent item that *is* H&S-specific and remains open — a device run of the Map tab
  against the exact ROM — is carried into that phase with its exact remaining human check named in
  §12.9.7.

---

## Stale criteria corrected by this audit

The following #40 / repository statements were stale or wrong and are corrected here and in the
referenced files. None of them changes a disposition; all of them were misleading a reader of the
evidence.

| # | Stale statement | Correction |
|---|---|---|
| 1 | #40 body: "the profile currently has an empty `sha256Hashes` list … no H&S ROM can currently become runtime VERIFIED" | Superseded: the exact hash is allowlisted with `isVerified`/`memoryLayoutVerified`/`battleStateReadVerified` true (Section A) |
| 2 | #40 body: "`battleUiVerified`/`interactiveControlsVerified` false, so H&S is scaffolding, not a release-grade verified contract" | The flags are still false **by design**, but they are not the contract for memory-derived companion features; those are governed by `mayReadLiveMemory`/`mayReadBattleState` (Sections A-D) |
| 3 | #40 body Sections D/E/F are pre-work planning text and their checkboxes are unchecked | Superseded by the closed #1, #11 and #9 with their own audits (Sections D/E/F) |
| 4 | Evidence §7 table claimed `sha256Hashes` "intentionally empty" and carried stale map/battle/calculator rows | §7 now carries a historical banner and a corrected `sha256Hashes` row |
| 5 | Evidence §8 "Why `sha256Hashes` stays empty" read as current product behaviour | §8 now carries an explicit "SUPERSEDED BY C4e" banner; the pre-C4e reasoning is retained as history |
| 6 | Evidence §11.1 and §11.7 repeated "`sha256Hashes` is still empty" | Both struck through with the C4e promotion named |
| 7 | Evidence §11.7 still listed Doubles as NOT RUNTIME VERIFIED while §0/§11.3 claimed the opposite | §11.7 banner corrects it; partner/multi remains the honest gap |
| 8 | Evidence §14.5 said Scenario 34 "fails deterministically" and that Doubles "were not exercised on the ROM" | Reality: the Scenario 34 walk was fixed (`7b64c5e`, `spamb 8`), and Doubles was exercised by Scenario 70. §14.5 now carries corrections |
| 9 | Evidence §12.2 claimed `RegionMapDatabase.JOHTO_DEFAULT` "is removed" | **False** — it still exists at `RegionMapDatabase.kt:1739` as an unreferenced metadata lookup. Only its use as a resolution default was removed, which is what the fail-closed contract requires. Corrected in §12.2 |
| 10 | Evidence §12.3 claimed `region_map_entries.h` "is used only for display names" | **False, and it contradicted §12.1.** The generator reads none of that file; names come from the tracked `region_map_sections.json`. Corrected in §12.3 |
| 11 | Committed generated asset `Hns205MapData.kt` named `region_map_entries.h` as an extraction input | The provenance header was wrong; the generator now emits the real inputs (`region_map_sections.json` for names, the three layout grids for canvases) and the asset was regenerated byte-consistently (`--verify-digests`, `--check` both pass) |
| 12 | Calculator capability §2 row 6 listed Thick Fat, Guts and Huge Power as supported abilities that "execute in `calculateHnsDamage`" | **False** — all three are `UNSUPPORTED_DAMAGE_RELEVANT` and refuse. Only the pinch abilities are damage-relevant and admitted. Corrected |
| 13 | Calculator capability §14.2 said the volatile read window is 38 bytes | The generated value is 41 (`HNS_LIVE_BP_VOLATILE_WINDOW_BYTES`) |
| 14 | Calculator capability §7 said "no H&S item's damage effect is modelled" | The engine *does* implement the pinned ×1.2 type-boost branch; those items are refused by policy, so no number is affected. Reworded to "not authorized" |
| 15 | `native/src/pokemon_reader.c` shiny comment said DualDex "does not read SaveBlock3 challenge settings in this phase" | The reader exists; the per-Pokémon verdict is simply not handed the snapshot. Comment corrected |
| 16 | Evidence §12.9.7 did not state that "source-backed Kanto routing" means *no runtime Kanto observation* | Added explicitly, so E5 cannot be over-read |
| 17 | #11's closing comment said "six functional cross-region transitions … ten more as script warp commands" | The committed inventory has **4** functional edges, 5 undecided-with-manual-verdict edges and 14 script-warp candidates. The artifact is authoritative |
| 18 | PR #73 prose said "6 mutations" in one table and "17 mutations" in another | `mutation-check.sh` has 17; the "6" figure was stale |

Also confirmed as accurate and deliberately left in place: the two preserved map limits (§12.9.7), the
`battleUiVerified`/`interactiveControlsVerified` flags, the tri-state shiny policy, and the
source-only status of partner/multi battles.

---

## Closure rule evaluation

| # | Closure rule | Result |
|---|---|---|
| 1 | Exact H&S 2.0.5 is intentionally trusted | **YES** — exact SHA allowlisted, `VERIFIED` reachable, other hashes fail closed (Section A) |
| 2 | Every live companion feature H&S currently exposes is evidence-backed | **YES for the bounded scope** — party/data (C), lifecycle (D), maps (E) and calculator (F) each have runtime, host or source+test evidence at a level matching what the UI promises, with the exceptions named above represented as uncertainty rather than as fact |
| 3 | Unsupported capabilities fail closed | **YES — scoped to the H&S capability surfaces audited in A-F**: trust gate, pointer validation, lifecycle gate, `AMBIGUOUS` degradation, 55 blocking calculator limitations, no-canvas map policy, unknown-map null. **This rule does not cover the separately delegated optional features** — Cheats (#17) and Assistant (#13) are openly *not* fail-closed today (Section G) and closing #40 does not certify them |
| 4 | #1, #9 and #11 are closed | **YES** — all closed, with #1's and #9's audits and #11's runtime slice merged |
| 5 | Remaining open work is genuinely general pre-beta hardening, not unfinished H&S integration | **YES** — G and H both resolve to the post-#40 hardening phase per `RELEASE_CHECKLIST.md:30-38` and `README.md`; no open item requires H&S-specific integration work |

**What closing #40 does and does not certify.** It certifies the **exact H&S 2.0.5 core live-companion,
battle, map and calculator integration** (Sections A-F) for the bounded capability set audited here. It
does **not** certify the Cheats or Assistant surfaces, which remain known, uncertified, user-facing
optional features deliberately handed to the next phase, and it does not certify any hardware run
(Section H).

---

## Remaining work (delegated, not H&S blockers)

| id | Item | Owner phase | Why it is not an H&S blocker |
|---|---|---|---|
| R1 | Cheat presets must be gated by exact verified ROM rather than display-name substring, and the placeholder preset removed (#17) | Post-#40 hardening | **This is a known uncertified user-facing surface, not a fail-closed one.** It is not an H&S *integration* blocker because it is an optional feature outside the A-F capability surfaces, and it is scheduled as step-2 work by the checklist. Until it is fixed, exact H&S can be offered unverified codes that a single explicit toggle can apply to the core |
| R2 | Commit the raw capture logs for Scenarios 20 and 40-44 so Section D's runtime claims stop depending on PR/doc prose | Post-#40 hardening | The PASSes are recorded with committed, machine-asserting scenario scripts and merged PRs; this is evidence self-containment, not missing verification |
| R3 | Add a lifecycle/battler-authority gate to `pokemon_read_battle_stat_stages` | Post-#40 hardening | The Battle Console reader always samples battler 1 and has no lifecycle gate; in a Doubles battle `_enemyStatStages` therefore comes from an arbitrary battler. Presentation is already guarded (enemy stat stages render only when a single opponent is resolved), and the H&S calculator path uses the separately gated `BattlerRuntimeState`, so this is defence-in-depth on the legacy console path, not a user-visible wrong value |
| R4 | Re-derive runtime cross-region (Kanto) location evidence on a legal end-game save | Post-#40 hardening | #40/#11 accept source-backed routing for Kanto; the preserved limit says so, and the exact remaining run is named |
| R5 | Device run of the Map tab and the battle/party/field presentation on the AYN Thor | Post-#40 hardening (the Thor matrix) | Hardware soak is required before beta, not before declaring the H&S software integration complete (Section H) |
| R6 | Assistant offline fallback truthfulness (#13), credential/privacy/backup policy (#12), Thor shortcuts (#14), gameId fixture drift (#15) | Post-#40 hardening | General beta requirements; none reads or writes H&S memory state. **#13 is a known uncertified user-facing surface** — like R1 it is delegated by phase order, not declared safe |
| R7 | Positive (active-state) H&S calculator runtime observations: active volatile/gimmick/weather/screen frames, positive badge Golden D, a direct crit golden | Post-#40 hardening | The admitted subset is defined by *neutral* observations and every positive state refuses; more positive goldens would widen capability, not repair a wrong number |

---

## Validation and a correction to the audit's own tooling note

Run locally for this audit with `JAVA_HOME=/home/dq/.local/jdks/jdk-17.0.20.1+1`:

| Check | Result |
|---|---|
| `./ci.sh test` | **PASS** — 91 native reader tests, 93 pure tracker selftests, 2,505 QuickJS calculator assertions, 760 Kotlin/JVM tests, plus the map-data, golden-matrix and four data-pack generator suites |
| `./ci.sh source-check` | **PASS** against the pinned H&S checkout; generated data pack matches disk with zero drift |
| `./ci.sh all` | **PASS**, including the debug APK |
| `git diff --check` | **PASS** |
| GitHub exact-head CI (PR #74) | **GREEN** — run `35767300599`, all three jobs |

**Correction — `./ci.sh` does *not* mask Gradle failures, and an earlier draft of this audit implied
it might.** The first exploratory run of this audit wrapped the command as
`./ci.sh all 2>&1 | tail -60`, saw Gradle report `JAVA_HOME is not set`, and still saw a zero status.
The zero came from **the caller's own shell pipeline**, not from `ci.sh`: without `pipefail`, a
pipeline's status is the status of its last command, so the exit code observed was `tail`'s.
Reproduced directly:

```bash
env -u JAVA_HOME bash -c './ci.sh test > /tmp/x 2>&1; echo $?'                     # 1  <- ci.sh is correct
env -u JAVA_HOME bash -c './ci.sh test 2>&1 | tail -5 >/dev/null; echo $?'         # 0  <- tail's status
env -u JAVA_HOME bash -c 'set -o pipefail; ./ci.sh test 2>&1 | tail -5 >/dev/null; echo $?'  # 1
```

`ci.sh` begins with `set -euo pipefail` (`ci.sh:22`), `gradle_test()` is a plain `./gradlew
testDebugUnitTest` (`ci.sh:234-237`), and the direct invocation above returns **1** when no JDK is
available. The canonical contract is intact and no CI regression or follow-up issue is warranted. The
only real finding is a caller-side hazard worth remembering in this repository's own workflow: piping
the canonical script into `tail`/`grep`/`tee` without `set -o pipefail` silently discards its exit
status. This audit's own validation was therefore re-run with the status captured directly.

---

## Recommendation

**CLOSE #40** and begin the general pre-beta hardening phase.

All five closure conditions hold on `e67ee76`, **with closure condition 3 scoped to the H&S capability
surfaces audited in A-F.** Nothing in Sections A-H is an unfinished H&S *integration* task; the
remaining work is the hardware soak, the general beta-hardening issues (including the still-uncertified
Cheats and Assistant surfaces), and evidence self-containment, all of which #40's own roadmap places
after the gate.

H&S 2.0.5 becomes the **primary complex validation ROM** for that phase, and R.O.W.E. (#59) does not
begin until the hardening pass is sufficiently complete.
