# R.O.W.E. Phase 7B — host-only player identity adapter

Implements the bounded composition in the [Phase 7A design](ROWE_PLAYER_PARTY_ADAPTER_DESIGN.md).
Starting main: `b1eb3be4a1589de95334d9af864b6d6e0b279a54` (merged PR #202).
Tracking [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59) remains OPEN;
production compatibility remains **HOLD**. No private ROM, save or capture is required.

## Interface and ownership

[`rowe_identity_adapter.h`](../native/tests/rowe_identity_adapter.h) exposes one
`rowe_identity_read_player_party(admission, metadata, read, read_context, out)` operation.
It composes the unchanged `rowe_raw_read_player_party`; there is no second packed decoder.
All code is C11 and standard library only, under `native/tests/`.

Inputs and callback storage must remain valid, immutable where specified, and disjoint
from output throughout the request. Both callback contexts are required. Output owns
six fixed member slots, names (64 bytes including NUL), and each accepted record's
complete 76 bytes. No emulator, stack or resolver pointer escapes. Caller supplies a
complete writable output; NULL returns `ROWE_ID_REQUEST_INVALID` without callbacks.

Each member carries its original slot, raw species/form, resolved static species,
species-table metadata key, name candidate and static status. The metadata key must
equal resolved species under this narrow interface. Raw records remain private host
diagnostics. Display authority and runtime-effective identity are always
`ROWE_NOT_VERIFIED`. No ability, typing, nature, IV/EV, shiny, egg, gender, item,
battle stats or battle-form interpretation is provided. Raw aliases are never normalized.

## Synthetic admission and resolver contract

Admission compares an externally supplied intended release against an observed release:
nonempty bounded release identifier, nonzero size, and 32 nonzero-as-a-whole SHA-256
digest bytes. Metadata must bind to that same release and the requested nonempty
revision. The test uses invented identifiers, digest bytes and names, including a
correct-size/wrong-SHA-256 negative control. This proves **fingerprint comparison**,
not ROM-byte hashing, fingerprint-source authenticity or real metadata provenance.
No trusted hash or real metadata pack is registered.

Only `ROWE_CONFIG_SYNTHETIC_ADMITTED` is eligible; unknown, unsupported and unrecognized
configuration values refuse. Immutable-generation ownership and player-only scope
must also be explicit. These are synthetic caller assertions, not production tokens,
a lock implementation, or proof of startup guard eligibility. Correct hash alone
never admits an unknown configuration. All admission completes before any memory or
resolver callback; counters test the refusal ordering.

The externally supplied resolver owns bounded exact-table/domain/name validation,
including evidence for nonzero forms without a form table. It must return a typed
status and zero payload on refusal. Success echoes the requested raw pair and provides
a nonzero resolved species within the 12-bit species domain, matching metadata key
and nonempty NUL-terminated name. The adapter rejects invalid status values, echoed
pair mismatches, missing/unterminated names, invalid targets, mismatched keys and
refusal-with-payload contradictions. It copies names through their terminator into
zeroed owned storage. It cannot detect a structurally valid but dishonest resolver.

Resolver reasons distinguish unknown species, unsupported species record, invalid
form, malformed form table, missing name, unsupported resolved species, unavailable
metadata and contradictory resolution. The synthetic table plus injected typed
refusals test composition; they do not authenticate a real R.O.W.E. data pack.
Python's `rowe_data.py` remains a research specification, never a C dependency.

## Snapshot and publication

The raw reader alone owns count / full 456 bytes / count / full 456 bytes / final
count, matching counts 0..6 and identical regions. Even zero count requires all
five reads. Only occupied slots are decoded/resolved; original indices remain intact.
The reader accepts only its fixed player addresses. It never searches for alternative
parties or reads enemy/battle data.

**External precondition:** the callback must operate on one immutable emulator memory
generation for the entire call. Repeated reads cannot prevent concurrent writes or
ABA. Future production ownership must use `LibretroCoreCoordinator.executeExclusive`
around admission and the complete request, with the same lock used by frame execution
and load/unload/reset/state/SRAM mutations. No such integration exists in this slice.

A private candidate is published only after every occupied identity succeeds.
Any failure clears count and every member, including prior success, and retains
only typed status/reason. No successful subset escapes. Valid count zero publishes
`ROWE_ID_PARTY_EMPTY` immediately; it is distinct from refusal.

| Status | Meaning |
|---|---|
| `PARTY_AVAILABLE` / `PARTY_EMPTY` | Complete admitted synthetic composition / coherent empty |
| `REQUEST_INVALID` | Missing admission, reader, read context or output |
| `ROM_UNVERIFIED` | Intended/observed identity invalid or mismatched |
| `CONFIGURATION_UNVERIFIED` | Configuration is not explicitly synthetic-admitted |
| `OWNERSHIP_UNVERIFIED` / `SCOPE_UNSUPPORTED` | Missing immutable-generation assertion / player-only scope |
| `METADATA_UNAVAILABLE` | Missing/mismatched provider, revision, release, context, or resolver refusal |
| `IDENTITY_UNSUPPORTED` | Occupied species zero, resolver refusal or contradiction |
| `SNAPSHOT_INVALID` | Raw reader failure: invalid count, incomplete/failed read or observed incoherence |

Status names above omit the common `ROWE_ID_` prefix. The existing raw reader returns
bool, so read failure and incoherence deliberately share `SNAPSHOT_INVALID` rather
than inventing a cause. Resolver details are retained separately in `refusal`.

## Tests, CI and shipping exclusion

`test_rowe_identity_adapter.c` uses invented bytes and a two-entry form table.
It covers distinct raw `1/1` and `3/1` aliases resolving to artificial key 3,
form zero, original bytes/reserved bits, counts 0..6, ignored invalid excluded slots,
all admission failures and callback ordering, all resolver refusals at every slot,
contradictory payloads, bounded names, every short region length, failed/overreported
reads, middle/final count changes, excluded-byte mutations, counts 7/255,
stale-output clearing, and empty versus failed snapshots. Every callback checks fixed
player addresses and lengths. Existing raw tests retain exhaustive slot-length,
packed-bit, full-memory map, enemy-shaped negative-control and production-selection coverage.

`ci.sh rowe-test` runs both binaries; `fixture-test`, hence `test`/`all`, requires them.
`rowe-sanitize` compiles and runs both with unchanged ASan/UBSan flags. Adapter
compilation adds `-std=c11 -Wall -Wextra -Werror`. No dependency was added.

Local validation (2026-10-15):

| Check | Result |
|---|---|
| `./ci.sh all` | PASS — native, calculator/H&S oracle, tooling, Kotlin and debug APK |
| `source-check` | PASS — pinned upstream generation/verification, bootstrap controls and Kotlin |
| `fixture-test` | PASS — 73 production replay/rejection cases plus both ROWE binaries |
| `rowe-test` and direct binary execution | PASS — 686 adapter requests / 11,156 assertions; 422,771 raw assertions |
| ROM-evidence unittest discovery | PASS — 65 synthetic tests |
| `rowe-sanitize`, ASan/UBSan | PASS |
| `rowe-sanitize`, LeakSanitizer | PASS outside sandbox with `detect_leaks=1` |
| `git diff --check` | PASS |

The initial sandbox sanitizer run failed because LeakSanitizer cannot operate under
ptrace. A separate `detect_leaks=0` run passed ASan/UBSan; it is not a leak result.
The full outside-sandbox run then passed both binaries with leak detection enabled.
Evidence discovery uses `TMPDIR=/var/tmp` because the sandbox `/tmp/.git` marker
otherwise correctly triggers the existing private-capture checkout guard.

Shipping CMake's explicit source list is unchanged and includes neither host module.
There are no adapter/raw/tracing references in production app/native source targets.
All eight packaged `.so` files (four each for `arm64-v8a` and `x86_64`) passed scans
of dynamic defined symbols and complete library bytes for `rowe_raw_`,
`rowe_identity_` and `retro_rowe_trace_`: `libdualdex_native.so`,
`libc++_shared.so`, `libmgba_libretro.so`, and `mgba_libretro.so` in each ABI.
FireRed, Emerald and H&S production regressions remain green. Unrelated pre-existing
untracked files are preserved and excluded; local Kotlin execution can include the
pre-existing untracked starting-head test.
Exact final head, draft PR and hosted CI links belong in the PR handoff to avoid
self-referential commit hashes. Historical Phase 6 evidence is unchanged.

## Remaining evidence and smallest Slice 7C boundary

This implementation establishes no caller guard eligibility, general mode semantics,
all-form support, runtime-effective abilities/stats, enemy/battle/boxed parsing,
calculator compatibility, production ROM trust or Android device acceptance.
Startup guard verification remains **BLOCKED_EXTERNALLY**. Static lookup success
never promotes runtime display/effective authority or production permission.

For a separately authorized 7C, bind a freshly calculated loaded-ROM exact hash and
`stateGeneration` under the coordinator execution lock at load/switch, invalidating
on failed replacement, unload and every existing generation mutation. The private
cheat-ROM hash binding must not become general ROM authority without a reviewed API.
Add a deny-by-default `mayReadPlayerIdentity` requiring exact loaded identity,
verified configuration, storage evidence and explicit player capability; leave
`mayReadLiveMemory` and unrelated permissions unchanged.

Return a separate identity-only JNI DTO carrying slot, raw pair, static resolved
species/key/name, static status, explicit unavailable display/effective axes, typed
empty/refusal, core generation and companion session revision. Raw record bytes stay
host-private; unavailable semantic fields do not become placeholder Pokémon values.
Capture tokens before waiting, recheck under the lock, then atomically compare both
tokens and publish under the same owner/lock as session invalidation. Discard old
results even on same-hash reload. Do not reuse stale-observation retention for refusal.
Coordinator latch tests, JNI/ABI tests and device/session acceptance remain future work.
No Slice 7C implementation or activation is included or automatically started.
