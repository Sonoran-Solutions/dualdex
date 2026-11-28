# R.O.W.E. player-party adapter design — Phase 7A

Design for review, 2026-10-15. Tracking [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59)
stays OPEN. Production ROM support: **HOLD**. No production integration is implemented or authorized.
Fetched main: `bc4fa9efff03d99175f260881e137c9727a46a3d`;
[PR #201](https://github.com/Sonoran-Solutions/dualdex/pull/201) merged at
2026-10-15 18:52:41 UTC with that merge commit. Phase 6D–6G reports, manifests,
raw decoder and startup validator are present at this base.

## Decision

Use the existing host-only `RoweRawParty` decoder, followed by a narrow, exact-release
player identity result. Do not return `ParsedPokemon`, translate into fake Gen III
records, copy the H&S reader, or build a generic parser framework. Later production
routing must explicitly select a distinct 76-byte storage format before any legacy
parser can run. This document specifies that boundary; it does not add it.

Recommend **Option B for future integration**: an independently verified player-identity
capability, bound to exact ROM, loaded session and evidenced configuration, with every
other capability denied. Until separately approved implementation and evidence exist,
preserve today's universal gate and keep all R.O.W.E. live reads closed (Option A).
Neither the historical hash nor static resolver success grants admission.
[RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md)'s general pre-beta hardening phase
still precedes R.O.W.E. integration. This design cannot authorize that transition.

## A. Existing flow and authority boundaries

Paths below are relative to the repository; function names identify the audited seams.

| Stage | Actual files / functions | Current behavior and implication |
|---|---|---|
| Detection | `romhack/RomHackDetector.kt`: `detectCompatibility`, `detectCompatibilityFromBytes`; `ProfileLoader.kt`: `loadProfilesFromAssets`, `parseProfile` | Kotlin hashes file bytes; exact profile hash plus verification flags can verify. Filename/header/base-game matches only recognize. Profile loader defaults verification flags false. No R.O.W.E. active profile. |
| Runtime binding | `emulator/RomSessionManager.kt`: switch transaction; `CompanionViewModel.setRomSession` | Switch mutates core under coordinator lock, then publishes compatibility/identity. Native `game_id` comes from profile, not a fresh native hash check. |
| Admission | `romhack/RuntimeRomTrust.kt`: `exactRuntimeVerified`, `mayReadLiveMemory`, `mayReadBattleState`; `CompanionViewModel.pollTick` | Universal gate requires exact detection, verified profile/layout and active = detected = allowed hash. Battle gate additionally requires proven battle offsets. Party and location share universal admission. |
| Core ownership | `emulator/LibretroCoreCoordinator.kt`: `readPartyFromCore`, `executeExclusive`, `stepFrame` | One fair reentrant lock covers each JNI read and frame execution, load/unload/reset/state/SRAM mutations. Each read uses a 50 ms timeout and returns null on failure. |
| JNI | `app/src/main/cpp/dualdex_jni.c`: `Java_com_dualdex_emulator_LibretroHost_nativeReadPartyFromCore`, `dualdex_jni_gba_read` | Obtains EWRAM, selects compiled `pokemon_get_game_config(game_id)`, calls player reader; caches active slot; constructs Java array. Missing RAM/config returns null. Zero count produces empty array, also on many invalid reads. No session tag. |
| Memory | `native/src/libretro_host.c`: `libretro_host_get_ewram`, `libretro_host_read_gba_address`, `capture_memory_map`; `gba_memory_map.c`: `gba_memory_map_resolve/read` | EWRAM is a core-owned pointer. Absolute reads require one mapped bounded span; reject overflow, missing/split/short regions. Map mutex protects descriptor access/copy, not emulator writes. |
| Decode | `native/src/pokemon_reader.c`: `pokemon_read_player_party_gba`, `read_authoritative_player_party`, `read_heuristic_player_party`, `parse_party_mon`, `pokemon_parse_single_layout` | Config chooses discovery policy and vanilla/expansion bit interpretation. All party stepping uses `sizeof(RawGbaPokemon)` = 100. |
| Kotlin data | `dualdex_jni.c`: `create_parsed_pokemon_object`, `PARSED_POKEMON_SIG`; `pokemon/PokemonModels.kt`: `ParsedPokemon` | Fixed positional constructor carries required semantic fields; native known-ability flag is not transported as a Kotlin property. |
| Observation | `companion/CompanionViewModel.kt`: `pollVerifiedRomMemory`, `clearLiveMemoryObservations`; `LiveObservationStabilizer.kt` | Filters valid/nonempty members and publishes nonempty list. Empty/null counts as invalid; retains previous party until five invalid reads. Default polling parameter is 200 ms (some prose says 100 ms). |
| Presentation | `companion/ui/PartyScreenView.kt`: `updateSlots`, `bindDetail`; `PartySlotModel.kt`: `PartySlotState`, `statusLabel` | Resolves species/moves from active pack; displays HP, stats, IV/EV totals, nature, item, types and defenses. Slot requires level/HP/egg/shiny values. No identity-only state. |
| Static metadata | `pokemon/GameDataPack.kt`: `GameDataPackRegistry`, `resolveSpecies`, `resolveMove` | Exact packs can disable global fallback. Unknown pack IDs fall back to vanilla; even no-fallback missing species placeholder has Normal typing. Neither route is suitable for R.O.W.E. refusal display. |

Kotlin paths in the table start at `app/src/main/java/com/dualdex/`.
`pokemon_detect_game` is a native title heuristic used by diagnostic/raw parsing
entry points; it is not exact-ROM trust. Production live JNI selects compiled
`GameMemoryConfig` by the Kotlin profile's `gameId`. Profile JSON offsets are
not applied as native configuration. `pokemon_get_game_config` returns no usable
layout for unknown IDs; do not reuse Emerald/H&S game IDs for this adapter.

Current player validation has two materially different policies:

- Authoritative-static: declared count/address required; 0 means empty; >6 fails;
  bounds-check count × 100; every occupied slot must parse and have species 1..1999;
  any failure clears the entire snapshot/cache, no scan.
- Heuristic: static candidate, cached candidate, then EWRAM scan; species/level/HP
  plausibility, checksum and candidate scoring help locate records. This is not
  permission to scan R.O.W.E. RAM. Do not reuse the ceiling 1999 as its species domain.

The parser XOR-decrypts 48 bytes with PID ^ OTID, checks sums and applies PID % 24
substructure permutations. It also has plaintext/checksum fallbacks and a zero-checksum
CFRU fixed-order branch. H&S's `PKMN_STORAGE_EXPANSION` only changes selected bit
interpretations (ability, mint nature, shiny modifier); it still uses 100-byte records.
None of these branches establishes the observed packed 76-byte ABI.

Player snapshots may call `pokemon_read_battle_lifecycle` and
`sync_live_player_battle_mon` to attach active slot/live HP. Enemy reads separately
use `pokemon_read_enemy_party_gba` and authoritative battler-position/party-index
resolution, or legacy heuristics for other configs. The player-only adapter must
invoke none of these battle/enemy helpers, even if stale enemy bytes look plausible.

Failures are not uniformly typed today: native false/zero, cleared snapshots and
JNI null/empty arrays overlap; `gba_memory_map_read` leaves its destination unchanged
on failure, so callers must clear/ignore it. The R.O.W.E. raw reader already clears
all output on failure. Future JNI must keep failed read distinct from valid empty party.

## B–D. Reuse, storage and minimum future changes

Reuse unchanged: bounded memory map, coordinator lock/frame ownership, raw decoder
and its five-read protocol, raw byte preservation, session clearing hooks, existing
legacy parser/tests, and exact-hash comparison principles. Reuse the algorithms in
`tools/rom-evidence/rowe_data.py` (`resolve_form`, `lookup_party_identity`) as the
research specification, not as a Python dependency shipped into Android.

`native/tests/rowe_raw_decoder.{c,h}` decodes species (12 bits), form (5), moves
(10 each), experience (21), stored nature, PP, status, level, HP and five stat words;
retains the complete 76 bytes, PID/OTID and 12 raw nickname bytes. Capacity six,
full region 456 bytes, count `0x02025125`, base `0x02025128`. These are exact-release
research addresses, not approved production reads. No encryption, checksum,
permutation, string decoding or effective semantics are asserted by that decoder.

Native `ParsedPokemon.species/moves` are uint16_t and Kotlin uses Int: both can
hold these raw numeric IDs. That does not make their meanings compatible.
`ParsedPokemon` has no separate raw form, static metadata key, authority or raw
record. OT name, held item, friendship, IV/EVs, PP bonuses, egg/gender/ability,
mint modifier and shiny verdict cannot safely be supplied by this decoder.
Stored nature/stats/status/level words are extracted, but their effective game
semantics are not thereby verified. PID-derived vanilla rules must not fill the gaps.

Future changes, only after authorization:

| Files / seam | Minimum change |
|---|---|
| `native/tests/rowe_raw_decoder.*`, future host-only identity helper | Compose existing raw decode with validated exact-release metadata and explicit identity result; keep diagnostic target only in 7B. |
| `native/include/pokemon_structs.h`, `pokemon_reader.h`, `pokemon_reader.c` routing, if shared routing is chosen | Add explicit distinct packed-layout selection that routes before any 100-byte access; unknown discriminator refuses. Do not extend `pokemon_parse_single_layout` with fake 100-byte translation. A dedicated player entry point is smaller initially and avoids changing legacy structs. |
| `RuntimeRomTrust.kt`, `RomCompatibility.kt`, `RomHackProfile.kt`, `ProfileLoader.kt`, detector/tests | Separate exact identity evidence from per-capability authorization; absent flags deny. Do not mark whole layout verified to get one party reader. |
| `LibretroCoreCoordinator.kt`, `RomSessionManager.kt`, `LibretroHost.kt`, `dualdex_jni.c` | Bind admission and returned session/generation under lock; one player-only JNI call, independent DTO constructor, typed empty/refusal, no battle caches. |
| `CompanionViewModel.kt`, `PokemonModels.kt` or one new identity DTO, `PartySlotModel.kt`, `PartyScreenView.kt` | Separate identity observation flow; nullable/unavailable presentation fields and atomic session-aware publication. Preserve existing ParsedPokemon/manual/calculator consumers. |
| `GameDataPack.kt` and future exact metadata provider | Explicit exact-pack lookup that returns absence, no registry/global/Normal placeholder fallback; identity metadata must not implicitly enable calculator. |
| `app/src/main/cpp/CMakeLists.txt` | Only a later approved shipping slice may include reviewed adapter code; test-only tracing/fixture code stays excluded. |

A one-function player-only adapter and one result type suffice; an adapter registry,
virtual parser interface or generalized ROM-hack framework has no demonstrated need.
Shared presentation can eventually consume optional fields from both sources; do
not refactor every ParsedPokemon constructor to make this design executable today.
`shinyState=UNKNOWN` already exists, but boolean `isShiny`, mandatory stats/IV/EVs,
`hiddenNature=nature`, and absent Kotlin ability-known flag prevent honest general
conversion. PartySlotState also lacks unknown level/HP/egg/shiny distinctions.
Future identity-only UI should show “Static identity: <name>”, and “Unavailable”
for unverified effective fields; omit type defenses/calculator handoff. Static
types, if displayed later, must be labelled static, never current battle typing.

## E. Trust decision and capability comparison

| Policy | Safety / unrelated feature risk | Change and regression cost | Tests / future use / release ordering |
|---|---|---|---|
| A: keep universal verification | Safest immediate state; no live player-only support | No production changes | Existing tests unchanged; diagnostics remain useful, but eventual broad verification would delay partial support. Fits current hardening. |
| B: explicit capability later (recommended design) | Exact session + evidence scope + player flag only; universal/battle/location/calculator stay denied | More seams than A, bounded to admission, DTO and rendering; preserve legacy paths | Add deny-by-default and call-count regressions; supports a real partial feature. Implementation still requires owner/release gate. |
| C: set verified profile/hash, zero unrelated offsets | Unsafe: universal gate invokes other readers and pack/UI semantics; zero offsets do not prove isolation | Superficially fewer changes, high silent-promotion risk | Reject; existing universal-gate tests are insufficient to prove partial support. |

Future B uses a new narrowly named `mayReadPlayerIdentity`, not a redefinition of
`mayReadLiveMemory`. Permission requires a verified exact-release identity binding,
player storage evidence, admitted mode, current session and explicit player capability.
Global status must explain partial support, not reuse “Verified” as a claim for all
surfaces. Legacy H&S/vanilla gates keep their existing semantics and regressions.
Migrate every caller of the universal gate only where necessary for the new player
surface; enumerate all callers with `rg 'mayReadLiveMemory|mayReadBattleState'` in 7C.
A player branch must not enter `pollVerifiedRomMemory`, which also reads location and
battle state. Player permission implies no enemy/battle/map/calculator/control permission.
No profile, flag, pack, trusted hash or permission is changed in Phase 7A.

## F. Identity contract

Identity result belongs to a complete player snapshot, tagged with exact ROM hash,
monotonic core generation, session-publication revision, slot index, configuration
scope and static metadata revision. Keep separate:

1. Original raw species and raw form (never overwrite on resolution).
2. Static resolved identity (exact table result, including form context).
3. Static metadata key and name candidate (validated in exact table/name domain).
4. Runtime display corroboration (optional evidence reference bound to subject,
   record/lifecycle and independently inspected game UI).
5. Runtime-effective battle identity (unavailable until separately verified).

Use independent `staticStatus`, `displayEvidenceStatus`, and `effectiveStatus` axes
rather than one confidence enum that accidentally promotes all fields. Suggested
static statuses align with the existing research vocabulary below. Snapshot refusals
have no member list. Trusted diagnostic raw values never become production UI on refusal.

| Result / refusal | Permitted payload / presentation |
|---|---|
| `STATIC_VERIFIED` | Preserve raw pair/bytes, resolved species, metadata key/name candidate. Static label only; display evidence and effective fields remain NOT_VERIFIED. |
| `RUNTIME_CONFIRMED` (display axis only) | Above plus independently bound display evidence. Historical matching species alone cannot grant this to a new live record. No effective ability/type/stat claim. |
| `UNSUPPORTED_SPECIES_ID` | Raw diagnostics only; resolved/name/key absent; no global fallback. |
| `UNSUPPORTED_SPECIES_RECORD` / `MISSING_NAME` | Exact record/name absent or reserved; no named identity, even if integer is in range. |
| `UNSUPPORTED_FORM_ID` | Out of packed range or beyond bounded form list; no resolved/key/name. |
| `UNSUPPORTED_FORM_TABLE` / `UNSUPPORTED_RESOLVED_SPECIES` | Malformed, unterminated, out-of-range or unsupported target; no named identity. |
| `UNSUPPORTED_MODE` | No party presentation; private diagnostic raw extraction does not authorize semantics. |
| `INCOHERENT_SNAPSHOT` / `MEMORY_UNAVAILABLE` | Entire result refused and member output cleared; never publish the good subset. |
| `ROM_UNVERIFIED` / `STALE_SESSION` | No memory read for failed admission; discard all late members and clear immediately. |
| `EMPTY` | Valid admitted coherent count 0, complete reads succeeded; publish empty immediately, distinct from invalid. |

If any occupied slot fails identity validation, refuse the whole named party; keep
slot indices stable, never compact it using `filterNotNull`. Zero/unset species in
an occupied slot is partial initialization, not an excuse to drop a slot. Retain
all unknown/reserved bits in the raw record; don't reject merely for nonzero unknown
bits or assign them meaning. Numeric plausibility is not effective-field authority.

Static resolver rules: exact artifact/metadata provenance first; refuse unknown or
reserved IDs, invalid names, invalid indices and unsupported tables. Bound table
reads and sentinel search to the verified limit. The research resolver's null form
pointer returns the stored species even for an in-range nonzero form: preserve that
as static accessor behavior only. A future adapter must not infer a meaningful
nonzero form from that branch without specific evidence; otherwise refuse its
presentation. Do not silently tighten historical research behavior in this task.
Configuration authority must come from verified runtime/configuration provenance;
a caller boolean or assuming “Normal” from a ROM hash is insufficient.

Exact release: **Pokémon R.O.W.E. 2.1.9.1 Experimental**, SHA-256
`fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3`.
Historical regional Meowth: capture and serialized cartridge `1560/1`, after startup
`52/1`; both static metadata key 1560. Phase 6F's setter at `0x0809B980` changed
`0x38581618` to `0x38581034`; only bytes 28–29 changed. Form index was recomputed
as 1, not proved universally preserved. Accept both representations inside that
exact evidence scope; no adapter mutation or universal normalize-on-load rule.
Unknown alternate modes and startup eligibility remain refused even though a
static mapping can be computed.

## G. Coherence and session lifecycle

The host alone does not guarantee immutable RAM. `g_memory_map_mutex` cannot stop
`retro_run`; repeated reads could agree across a torn write or ABA. The Android
coordinator **does** serialize each protected read with frames and core mutations.
Reuse that lock for the entire future player request, not separately per callback:

```text
Emulator memory (between frames)
  → coordinator.executeExclusive (same owner as stepFrame/load/unload)
  → verify loaded exact identity + generation + player capability + mode
  → bounded count → all 456 bytes → count → all 456 bytes → final count
  → equal counts in 0..6, equal regions, every read complete
  → rowe_raw_decode_party → exact static identity resolution
  → capability/field-authority evaluation → tagged JNI identity DTO
  → atomic session/generation validation and Kotlin publication
  → identity-aware companion presentation
```

Use the existing callback protocol in `rowe_raw_read_player_party` on the immutable
core generation; no guest writes, frame stepping or unlocked callbacks inside it.
Copy raw output before releasing ownership; static lookup may occur on the copy
if the metadata revision is immutable and the tag is checked again on publication.
No raw host pointers may escape. Lock order is coordinator then native map mutex;
never acquire coordinator from a native map callback.

`stateGeneration` already increments on core/ROM load, unload, reset, state load,
SRAM load and cleanup, including failed replacement attempts. It is an invalidation
token, not a frame counter or an exact ROM binding. Current private cheat hash
binding cannot be read/reused as general identity without an explicit API.
Future admission needs loaded identity under this same lock. Also version companion
session publications/trust changes (profile changes need not mutate core generation).
Capture both tokens before waiting; verify both after acquiring and on publishing.

Existing `setRomSession`, `setRomIdentity`, `setRomInfo`, `setProfile` and
`clearRomSession` clear observations/stabilizers. Native load/unload clears memory
regions and calls `pokemon_reader_reset`. However clearing is insufficient against
a poll admitted under old trust, waiting across a switch, or returning after the
clear: current JNI arrays carry no token, and StateFlow assignments are separate.
Serialize token comparison plus publication with session invalidation (same small
publication lock or one owner dispatcher); a check then unlocked assignment is
still a race. Clear before switch starts, on failure/unload and on trust revocation.
Even reloading the same hash must invalidate earlier generation results.

For this initial identity surface clear immediately on refusal; do not reuse
`LiveObservationStabilizer.RETAIN` to display old authoritative identity after mode,
trust or coherence failure. Existing legacy stabilizer behavior need not change.
An explicitly labelled stale cache could be a later separately reviewed feature.

| Required case | Future acceptance behavior / test boundary |
|---|---|
| Count changes (middle/final), region differs | Refuse entire snapshot, zero output; existing native synthetic callbacks cover observed mismatches. |
| Unload during polling | Lock prevents concurrent free; pending request rechecks generation/loaded state, returns unavailable. |
| Switch after admission / same-hash reload | Admission bound under lock; old generation refused, not parsed with new layout. |
| JNI finishes before switch but publishes afterward | Atomic session-revision/generation check discards result; deterministic latch test, no sleeps. |
| Empty party | All five reads still required; count 0 publishes valid empty without delay. |
| Partially initialized occupied slot | Raw decode may succeed; semantic identity adapter refuses whole party. |
| Count 7 or 255 | Refuse, never clamp/scan. |
| Invalid/unmapped/split/short RAM | Refuse, no cached pointer or prior output reuse. |
| Wrong exact hash or missing mode authority | No decoder/memory callback invoked; no fallback to native title/game ID. |
| Enemy bytes at plausible offsets | Only fixed player addresses read; no enemy parameter or inferred adjacent layout. |

## H. Evidence and capability matrix

Sources: [Phase 6D](ROWE_2_1_9_1_FORM_RUNTIME_EVIDENCE_V3.md),
[6E](ROWE_2_1_9_1_SAVE_LOAD_IDENTITY_EVIDENCE.md),
[6F](ROWE_2_1_9_1_STARTUP_NORMALIZATION_TRACE.md),
[6G](ROWE_2_1_9_1_STARTUP_GUARD_EVIDENCE.md), and their linked sanitized manifests
`form-runtime-v3.json`, `save-load-identity-v1.json`, `startup-normalization-v1.json`,
`startup-guards-v1.json`. Also `static-symbols.json`, `party-accessors-v3.json`,
[raw decoder](ROWE_2_1_9_1_RAW_DECODER.md) and [static data/text](ROWE_2_1_9_1_DATA_TEXT_EVIDENCE.md).
Later phases refine earlier statuses; historical files remain unchanged.

| Capability | Evidence actually established | Production status |
|---|---|---|
| ROM identification | Exact diagnostic SHA-1/SHA-256 and size | Not trusted |
| Player count/layout | Research runtime snapshots, static accessors, 76 × 6 | Disabled |
| Occupied raw decoding | Host-only C/Python parity and synthetic fixtures | Disabled |
| Static species/form resolution | Exact-ROM bounded accessor/table/name evidence | Disabled |
| Regional Meowth | One captured subject, reopened and reloaded UI corroboration | Disabled; no general form coverage |
| Startup normalization | Executed selected setter/return, parity checkpoints, one cartridge | No general contract |
| Caller/configuration guards | 6G BLOCKED; historical callsite hit lacks guard chain/operand provenance | Disabled |
| General form coverage | Static mappings plus narrow runtime examples; alternate modes unverified | Disabled |
| Effective stats/ability/types | Stored words and narrow visible UI values; no general effective accessor/configuration proof | Disabled |
| Enemy-party tracking | No approved reader/layout/lifecycle | Disabled |
| Battle lifecycle | No production evidence contract | Disabled |
| Map/location | Research map/coordinate observations and static names in selected route | Disabled; no production routing authority |
| Calculator | No exact ruleset/effective-field integration | Disabled |
| Interactive companion game controls | No verified command/controller semantics | Disabled; ordinary emulator buttons remain available |

## I. Regression work and external dependencies

Phase 7A adds two ROM-free tests to `tools/rom-evidence/test_rowe_data.py` using
invented names, pointers and bytes. The public `lookup_party_identity` composition
resolves distinct pairs `1/1` and `3/1` to artificial key 3, preserves raw pairs,
keeps runtime display/effective authority absent, and refuses unknown species/invalid
form without metadata. A second test verifies exact-ROM rejection precedes metadata
access. Synthetic provenance is mocked only in the composition test; it is not
fresh runtime confirmation, trust activation, mode validation or a production adapter.

Existing native tests already cover sizes/nulls, clear-on-error, every count 0..6,
invalid counts, raw/reserved preservation, player-only addresses, count/region
mismatch and missing/split mapping. Existing research tests cover exact hashes,
invalid tables/names/forms and cross-phase evidence. Do not duplicate the parser
or manufacture a mock production adapter to claim 7B complete.

Before activation require native identity tests for per-slot refusal and unsupported
mode; Kotlin trust tests with reader call counters proving every unrelated reader
stays closed; coordinator latch tests for frame/mutation exclusion and generation
races; JNI constructor/array/tag tests for both ABIs; UI tests for unavailable fields,
empty versus refused and unknown shiny/ability/nature; device ROM-switch/reset/state
load/fast-forward stress; exact-ROM comparison against independently inspected game
UI for every advertised field and configuration. Re-run FireRed/Emerald/H&S canonical
regressions. No synthetic pass can replace that acceptance evidence.

Private artifacts are **NOT_REQUIRED_FOR_THIS_TASK**. The prior Phase 6H access
block is environmental; no new guard investigation or private-file search is
performed here. Startup caller guard verification remains **BLOCKED_EXTERNALLY**.
The future evidence task must launch on a host with an explicitly authorized private
location outside Git, supplied at launch rather than guessed from checkout paths.
Require exact artifact hashes against the sanitized manifests before consumption,
read-only historical evidence and separate disposable writable experiments. No ROM,
cartridge, raw capture or private pathname goes into Git or public posts. Diagnose
an inaccessible/unavailable mount separately from a missing file; neither proves
the historical artifact was lost. Do not reconstruct missing evidence from reports.

## J. Independently reviewable slices

| Slice | Prerequisites | Acceptance / stopping boundary |
|---|---|---|
| 7B — host-only player identity adapter | Senior acceptance of this design and explicit next-task authorization; use artificial metadata for unresolved runtime semantics | Compose existing raw decoder; explicit static/raw/display/effective separation; exact identity/mode/slot/count/size/refusal tests; no JNI, profile, pack activation or shipping source inclusion. Unknown mode remains refusal. Stop at host-only PR. |
| 7C — capability, session and presentation seams | Owner authorizes production implementation after release hardening gate; reviewed 7B; design of verified loaded/configuration identity | Add deny-by-default player permission and independent result channel, lock-bound admission, atomic tagged publication and honest identity UI; prove all other reader call counts zero; legacy regressions green. New path disabled, no trusted R.O.W.E. hash or active profile. Stop for review. |
| 7D — runtime/device acceptance | Authorized exact-ROM/private host; startup eligibility/configuration evidence resolved or narrower explicitly reviewed admission proof; reviewed disabled implementation | Verify advertised scope against independent UI; test capture/reload/both pairs, full snapshot/session failure matrix and Thor lifecycle/fast-forward. Keep unsupported modes and all unrelated capabilities closed; sanitize evidence. Testing uses explicitly authorized diagnostic access, not shipped trust. |
| 7E — production activation decision | Complete capability matrix, source/native/Kotlin/JNI/device checks, owner compatibility/release approval | Owner reviews exact hash, metadata provenance/licensing, mode predicate and supported field scope; only separately approved player identity permission may open. No implicit battle/enemy/map/calculator/interactive promotion; no auto-merge. |

Separate evidence work may unblock startup guards on the configured private host;
it is not automatically started by any slice. Recommended next authorized task is
senior design review, then an explicitly scoped host-only 7B if approved. Do not
start 7B, change release order, close #59, or treat this design as support activation.

## Determinations

| Determination | Result |
|---|---|
| CURRENT PARTY PIPELINE AUDIT | COMPLETE |
| STORAGE-LAYOUT ADAPTER DESIGN | READY |
| JNI/KOTLIN PRESENTATION CONTRACT | READY (future DTO/UI changes specified) |
| SNAPSHOT COHERENCE DESIGN | READY |
| ROM TRUST/CAPABILITY STRATEGY | DECIDED_FOR_REVIEW |
| SPECIES/FORM IDENTITY CONTRACT | READY_FOR_IMPLEMENTATION (host-only first; runtime gates closed) |
| SYNTHETIC REGRESSION PLAN | COMPLETE |
| PRIVATE EVIDENCE ACCESS | NOT_REQUIRED_FOR_THIS_TASK |
| STARTUP GUARD VERIFICATION | BLOCKED_EXTERNALLY |
| PRODUCTION ROM SUPPORT | HOLD |
| ISSUE #59 | OPEN |

## Phase 7A validation handoff

Local checks passed: `./ci.sh all`, `./ci.sh source-check`, `./ci.sh fixture-test`,
`./ci.sh rowe-test`, ROM-evidence unittest discovery (65 tests), focused
`test_rowe_data.py` (12 tests), relative document links and `git diff --check`.
Fixture replay passed 73 cases; raw decoder passed 422,771 assertions; canonical
QuickJS calculator passed 7,423 checks and the H&S differential oracle passed.
FireRed, Emerald and H&S production code is unchanged and their canonical regressions passed.
The debug APK has four native libraries for each of `arm64-v8a` and `x86_64`;
none contain `rowe_raw_` or `retro_rowe_trace_` symbol names, and CMake still excludes
the diagnostic sources. No native code changed, so `rowe-sanitize` was not warranted:
ASan/UBSan and LeakSanitizer were not rerun; historical passes are not new results.

The existing `/tmp/.git` marker causes the private-capture guard test to refuse its
ordinary temporary directory. Discovery and `all` passed with `TMPDIR=/var/tmp`,
without weakening the guard. Source-check needed normal access to regenerate
headers in its configured pinned upstream checkout. Existing unrelated untracked
files were preserved and excluded from the commit; local Kotlin execution could
include the pre-existing untracked test. Exact-head hosted CI results, branch/head
SHA and review envelope belong in the draft PR handoff, avoiding self-referential
commit hashes in this document. No private emulator run was needed or performed.
