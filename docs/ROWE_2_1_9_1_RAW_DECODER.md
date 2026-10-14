# R.O.W.E. 2.1.9.1 Experimental — isolated raw C decoder

Issue [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59), stacked on reviewed
[#192](https://github.com/Sonoran-Solutions/dualdex/pull/192) at
`356588ab263ddbe200faffebe9415c7d6f582475`. This implements the revision-3 **stored player
layout** as a host-only diagnostic module. It activates no ROM support and adds no native/JNI
production dispatch, trust/hash, memory permission, game data or calculator capability.

Evidence authority: [v3 player ABI](ROWE_2_1_9_1_PARTY_LAYOUT_EVIDENCE.md),
[v2 release provenance](ROWE_2_1_9_1_RELEASE_EVIDENCE.md), and the separate
[v1 hypothesis](https://github.com/Sonoran-Solutions/dualdex/blob/f5aa9b0aaf91e685fbcb1f81243604ea3e8a274b/docs/ROWE_2_1_9_1_SPIKE.md).
Historical classifications/capture JSON stay unchanged. #189's synthetic prototype is not
linked, copied or required. Two runtime slots are established by v3; synthetic 3..6-slot tests
here establish bounds and extraction only. This task does not run a ROM or add gameplay evidence.

## API and failure contract

Sources: [rowe_raw_decoder.h](../native/tests/rowe_raw_decoder.h) and
[rowe_raw_decoder.c](../native/tests/rowe_raw_decoder.c). C11, standard library only.

```c
bool rowe_raw_decode_slot(const uint8_t *bytes, size_t length, RoweRawSlot *out);
bool rowe_raw_decode_party(const uint8_t *bytes, size_t length, uint8_t count,
                           RoweRawParty *out);
bool rowe_raw_read_player_party(RoweRawRead read, void *context, RoweRawParty *out);
```

Slot input is **exactly 76** bytes; party input is **exactly 456**, count 0..6. Null/short/
oversized input or invalid count fails before byte extraction. A complete writable typed output
is required. NULL output returns false without reading input/calling a callback. Every failure
clears **every output byte**, including padding and a previously successful party. No partial
publication, cached results, gap compaction, plausibility scan or checksum/decryption exists.
Input/output overlap is supported via temporary decoding before committing output. On failure,
clearing an overlapping output necessarily clears the overlapping input too.

`RoweRawSlot` is a typed host value, not a packed wire structure or `RawGbaPokemon`. It retains
all 76 original `bytes` for unknown/reserved bits, plus numeric fields and twelve nickname bytes.
Never serialize the host structure/padding as a game record; use `bytes` for exact-record
comparison. `RoweRawParty` contains count and six members; only counted members are decoded,
and every excluded member is zero. A zero count remains empty even with stale records/species.

`RoweRawRead` returns the actual **size_t byte count copied**, unlike the existing native bool
callback. A fixture/native-map wrapper returns the requested length only after a successful
bounded read, otherwise zero. Any short/failed/over-reported result fails the snapshot. Callback
and context must be non-NULL; the callback must not write past its supplied capacity and must
read an immutable/paused generation on the owner's thread for the entire operation. This API
cannot enforce a caller's ROM identity or stop a running core; explicit diagnostic invocation
is not authorization to read any production ROM.

The player adapter reads absolute diagnostic addresses in this order:

1. u8 count at `0x02025125`; reject >6 immediately.
2. All 456 bytes at `0x02025128`.
3. Count again; require equality.
4. All 456 bytes again; require whole-region equality, including excluded slots.
5. Count a third time; require equality, catching a late count change during the second copy.

Only then decode exactly the count-selected slots. Even count zero requires all coherence reads.
Repeated equality detects observed mutations, not ABA or arbitrary running-core races; the
paused-generation requirement remains essential. No addresses/layout parameters permit enemy
selection. Player-region end `0x020252F0` is exclusive. The v3 wild enemy-count contradiction
remains a blocker for enemy support; a nonzero synthetic enemy count grants no capability here.

## Stored raw layout

Every load uses explicit little-endian byte operations. Field widths are storage facts from
v3's exact-ROM accessors, not inferred from normal values or compiler bitfield placement.

| Host field | Slot-relative storage |
|---|---|
| `pid`, `otid` | u32 +00, +04 (zero values are retained) |
| `nickname[12]` | twelve raw bytes +08; no text/terminator interpretation |
| `moves[0]` | word +14 bits 0..9 |
| `experience` | word +14 bits 10..30; bit31 excluded but retained in `bytes` |
| `moves[1]`, `moves[2]` | word +18 bits 0..9, 10..19 |
| `species`, `form`, `moves[3]` | word +1C bits 0..11, 12..16, 17..26 |
| `nature` | byte +27 bits 2..6; independent of PID |
| `pp[4]` | four full u8 bytes +34..37 |
| `status` | u32 +38; no condition labels |
| `level` | stored u8 +3C |
| `hp`, `max_hp` | u16 +3E, +40 |
| `attack`, `defense`, `speed`, `sp_attack`, `sp_defense` | u16 +42, +44, +46, +48, +4A |

Success means complete raw extraction/coherent reads, **not** a valid or supported Pokémon.
Values such as nature 31, level 255, species zero and HP 65535 are preserved without semantic
judgments. Effective moves/level can differ through the conditional accessor paths noted in v3.
No SID/shiny/gender/PID-derived nature, species/form names, abilities/items, IV/EVs, status labels,
experience curve, PP maxima/bonuses, egg/mode interpretation or H&S mechanics are supplied.
There is no `ParsedPokemon` adapter; future presentation must explicitly refuse unknown mapping/
critical semantics rather than applying vanilla/global defaults.

## Synthetic fixtures and canonical validation

[test_rowe_raw_decoder.c](../native/tests/test_rowe_raw_decoder.c) builds invented full 256 KiB
EWRAM and 32 KiB IWRAM files under a new temporary directory, then loads them using #190's
unchanged `memory_fixture_load` and production `gba_memory_map` bounds. Files are deleted;
no binary fixtures, captured identity, ROM, save or screenshot belongs in Git.

Four focused groups verify all fields and unknown bytes; every packed-word bit; all 256 nature
bytes; PID/OTID/status high bits; PP 0/127/128/255; zero/max level/HP; all four maximum moves;
experience bit30 versus reserved bit31; unaligned/overlapping inputs; all 76 truncated slot
lengths and oversized/SIZE_MAX requests; null arguments; all short party lengths; counts 0..6,
7/255; stale excluded slots; two whole records swapping; each required read failing, returning
short or over-reporting; count mutation including the second-copy window; party mutation even
in excluded bytes; unmapped/short/split-region spans; and complete clearing after prior success.
An existing-production selection negative control leaves ROWE-shaped records unused under
Emerald and rejects unknown game IDs. Callback assertions forbid enemy/scan addresses.
Byte-by-byte clearing assertions are not additional gameplay cases.

```bash
./ci.sh rowe-test       # focused test-only decoder + synthetic full-memory fixtures
./ci.sh rowe-sanitize   # same suite, ASan/UBSan, fail on any diagnostic
./ci.sh fixture-test    # existing 73 production replay cases plus new raw suite
./ci.sh all
HNS_UPSTREAM_DIR=/path/to/pinned/pokehns-expansion ./ci.sh source-check
python3 -m unittest discover -s tools/rom-evidence -v
git diff --check
```

The new targets reuse canonical host-compiler selection and fail if the compiler/sanitizer
cannot run. `fixture-test`, therefore `test`/`all` and hosted Native & Unit Tests, require the
raw suite. Shipping build commands are unchanged. Exact-head local/hosted results and shipping
symbol inspection are recorded in the draft PR handoff; synthetic CI is not runtime validation.

## Production integration boundary audit

No file in this table is changed by the decoder implementation.

| Current boundary | Audit / future integration requirement |
|---|---|
| `native/include/pokemon_structs.h:RawGbaPokemon` | Packed 100-byte Gen III party / 80-byte box, 10-byte nickname, secure container/tail; incompatible with this raw 76-byte type. Keep `ParsedPokemon` separate. |
| `native/src/pokemon_reader.c:pokemon_parse_single_layout` | Casts to the 100-byte structure, handles vanilla/expansion encrypted substructures and existing CFRU/plaintext behavior, derives semantics. Do not route a 76-byte slot here. Private LE helpers are not exposed; the isolated decoder uses its own small byte loads without changing production code. |
| `read_authoritative_player_party` | Bounds `count * sizeof(RawGbaPokemon)`, steps by that size, parses all-or-nothing. Future stride/decoder dispatch needs its own review; no change here. |
| `read_heuristic_player_party`, `scan_ewram_for_party_layout` | Static/cached candidates and heuristic scanning use the 100-byte layout. Never use these to discover ROWE slots. |
| `pokemon_read_player_party_gba`, `pokemon_get_game_config` | Supported-config guard and storage-layout dispatch. Unknown IDs have NULL config; no ROWE enum/config is introduced. Player decoder selection must eventually be explicit and gated. |
| `app/src/main/cpp/dualdex_jni.c` | `nativeReadPartyFromCore` calls existing native readers and `create_parsed_pokemon_object` creates Kotlin semantic values. No new JNI method, constructor field or conversion here. |
| `RomHackProfile.kt`, `RomHackDetector.kt`, `RomCompatibility.kt`, `RuntimeRomTrust.kt` | Profile identification and exact-hash compatibility/read capabilities precede live reads. Runtime trust requires the active hash to match both detection and verified-profile hashes; address/battle-read flags remain separate. Diagnostic addresses/decoded bytes cannot confer those permissions; no registry/profile/flag change. |
| `PokemonModels.kt`, `GameDataPack.kt`, companion party presentation | Semantic Pokémon types and isolated data-pack lookup/presentation need legitimate exact-release mappings and explicit unknown states. Raw IDs/form/name bytes are not automatically National Dex/vanilla values. |
| `app/src/main/cpp/CMakeLists.txt` | Explicit shipping source list includes native/src, not native/tests. Neither decoder/test source nor header is referenced; no glob or new link path. Host ci.sh is the only build entry for this module. |

Shipping proof consists of that unchanged explicit source list, no production references, and
absence of `rowe_raw_*` symbols from both packaged native ABIs after canonical debug build.
FireRed/Emerald/H&S production code, existing failure semantics and fixture suite are unchanged.
The new test binary links existing fixture/reader sources only to reuse #190 and its negative
selection controls; this does not make the decoder part of the app library.

## Review hygiene and remaining gates

`rowe_static.py`'s comment now says “Scope” with the same selected-path/all-mode qualification.
The observer rejects `--screens` and `--cartridge-dir` inside any Git checkout, resolving symlinks
and recognizing `.git` files for linked worktrees. Rejection occurs before ROM/core loading;
direct observation checks the private directory too. Sanitized JSON reports may still live in
a repository. This guard is not a filesystem race sandbox; use owned private directories.
Tests cover checkout/worktree markers, symlinks, and CLI failure without creating the directory.

The v3 capture source hashes remain those at reviewed parent `356588a`; hygiene changes here
are not claimed as the scripts used to make those historical observations. Their metadata
regressions now check the immutable captured-source pins; new generic/extraction/guard tests
exercise current tooling. No captured JSON is regenerated or its statuses strengthened.

The temporary stacked PR-base CI exceptions are removed by the reconciliation below.
The native fixture suite, ROM-evidence suite and raw decoder suite remain mandatory in the
canonical test path. No test command or shipping target changes in this housekeeping revision.

## Research stack reconciliation — 2026-10-13

This is an owner-controlled merge preparation, not production integration or merge approval.
Current main is `94e21aeeb2f85ae5036f6ce4e79a7514c1c1c431` (merged #190).
Repository settings allow merge commits, squash merges and rebase merges; auto-merge is disabled.
The GitHub API reports main unprotected; that does not supersede the human merge gate.
All four research PRs are open drafts with COMMENT technical-pass reviews, not formal approvals.
Issue #59 remains open. Do not create a successor feature PR during reconciliation.

### #189 disposition: historical, superseded; owner closure recommended

[PR #189](https://github.com/Sonoran-Solutions/dualdex/pull/189) at
`f5aa9b0aaf91e685fbcb1f81243604ea3e8a274b` has exactly three changed files:
`ci.sh`, `docs/ROWE_2_1_9_1_SPIKE.md`, `tools/rowe-layout-spike/decoder_selftest.c`.
A non-mutating `git merge-tree --write-tree origin/main <189-head>` reports a **content conflict
only in ci.sh**, in the command-header description: main says “native reader + memory fixture
replay”; #189 says “native reader suite + host-only R.O.W.E. hypothesis selfcheck”. The
underlying separate test hooks could coexist, but importing the old decoder is unnecessary.

| Unique material in #189 | Preservation / disposition |
|---|---|
| Full v1 source inventory, creator's 1.9.4/closed-2.0 announcement, pinned third-party reports, failed H&S tooling experiments, detailed reuse matrix and original plan | Preserve the immutable [v1 document](https://github.com/Sonoran-Solutions/dualdex/blob/f5aa9b0aaf91e685fbcb1f81243604ea3e8a274b/docs/ROWE_2_1_9_1_SPIKE.md) and PR history. The v2 release document retains the wrong-release boundary, native/H&S reuse audit and remaining gates. These older observations are historical provenance, not a missing build input. |
| Publisher patch and unresolved CRC/ROM identity hypothesis | Superseded by #191's pinned patch verification, all UPS CRCs and bytewise reproduced ROM identity. Preserve v1 as the earlier state; never relabel it independently verified. |
| 76-byte synthetic C decoder and CI hook | Superseded by #193's independently written raw extraction and #190 fixture map, with packed-bit, zero/max, length, alignment, overlap, count, mutation, callback and failure-clearing tests. Do not import a duplicate decoder. |
| Nickname preview / full-width and terminator checks | Tests of the existing Gen III codec under a hypothesis, not exact-release text evidence. #193 preserves all 12 nickname bytes; #59's next task must prove their interpretation. No compatibility coverage is lost by withholding preview. |
| Plausibility refusals for species zero, level outside 1..100, impossible HP, zero stats and a reported egg flag | Deliberate v1 scope assumptions, not raw-layout invariants. #193 instead preserves these values without declaring a valid Pokémon. Semantic refusals require exact-release evidence in a later task. |

**No essential unique code, test or reproduction dependency needs to be merged.** Recommend the
owner close #189 as superseded after accepting this finding; leave it open until authorized.
Keep its reviewed branch/commit reachable, links and historical evidence. Do not rewrite its
claims or resolve its conflict merely to merge redundant material. The #191 review's earlier
suggestion to retain both CI hooks predates the replacement #193 raw suite; the audit above
explains why that suggestion no longer requires the v1 hook.

### Merge sequence and ancestry

| PR | Exact reviewed head | Parent and exclusive scope |
|---|---|---|
| [#191](https://github.com/Sonoran-Solutions/dualdex/pull/191) | `37eec9cba83f68115d8bd4d01c96bfaf51c8a682` | Direct child of main `94e21ae`; one commit, 13 files: identity/static/runtime evidence, host tools/tests, canonical CI hook. |
| [#192](https://github.com/Sonoran-Solutions/dualdex/pull/192) | `356588ab263ddbe200faffebe9415c7d6f582475` | Direct child of #191; one commit, 13 files: v3 two-slot/persistence evidence, accessor tools, adapter pin, input movies/tests, temporary CI target. |
| [#193](https://github.com/Sonoran-Solutions/dualdex/pull/193) | `3eb5afcccd3b577827c7c953fa1193e43eafee9f` before reconciliation | Direct child of #192; one decoder commit, 10 files. Reconciliation adds only this document and the main-only CI trigger, with no decoder or evidence changes. Current final head and fresh gates are recorded in the PR handoff. |

Recommended owner order: **#191 into main, then #192 into main, then #193 into main**.
Prefer **merge commits**: they preserve reviewed ancestry, so retarget each child to main after
its parent merges without rewriting any branch. Verify that the parent's SHA is an ancestor
of origin/main and check the resulting exclusive diff before the next merge. Preserve branches
until child retargeting is complete. If main advances beyond the validated baseline, validate
that actual new merge candidate before proceeding; old green checks are not proof of a new tree.

If the owner chooses **squash or rebase merge**, parents get different SHAs. After #191 is merged,
with a clean checkout and freshly fetched origin/main, replay ONLY #192's exclusive commit:

```bash
git fetch origin
git rebase --onto origin/main 37eec9cba83f68115d8bd4d01c96bfaf51c8a682 chore/issue-59-rowe-party-abi
git push --force-with-lease=chore/issue-59-rowe-party-abi:356588ab263ddbe200faffebe9415c7d6f582475 origin chore/issue-59-rowe-party-abi
gh api --method PATCH repos/Sonoran-Solutions/dualdex/pulls/192 -f base=main
```

After the rewritten #192 is reviewed/validated and owner-merged, fetch main again and replay
ONLY #193's decoder and reconciliation commits, excluding its ORIGINAL #192 parent:

```bash
git fetch origin
git rebase --onto origin/main 356588ab263ddbe200faffebe9415c7d6f582475 feat/issue-59-rowe-raw-decoder
# EXPECTED_193_HEAD is the exact remote head recorded in the final PR handoff before this rebase.
git push --force-with-lease=feat/issue-59-rowe-raw-decoder:$EXPECTED_193_HEAD origin feat/issue-59-rowe-raw-decoder
gh api --method PATCH repos/Sonoran-Solutions/dualdex/pulls/193 -f base=main
```

Do not use a plain `git rebase origin/main`, merge main into a squashed child, or use the
rewritten #192 SHA as #193's old cutoff: those can replay inherited research commits.
Recheck exclusive diffs and all applicable local/hosted gates on each rewritten candidate;
obtain new review if conflict resolution changes behavior. Existing technical-pass comments
name the original reviewed SHAs and must not be represented as review of rewritten code.
No rebase or merge has been performed by this reconciliation task.

Capture digests, original tool revisions, adapter manifest and observation JSON remain
byte-for-byte unchanged. A rewritten branch SHA is not a new capture/tool provenance SHA.

### CI cleanup and validation boundary

Before removing either exception, exact stacked heads #191/#192/#193 had passed runs
[38425188430](https://github.com/Sonoran-Solutions/dualdex/actions/runs/38425188430),
[38462537894](https://github.com/Sonoran-Solutions/dualdex/actions/runs/38462537894) and
[38475614938](https://github.com/Sonoran-Solutions/dualdex/actions/runs/38475614938).
These establish the exceptions served their purpose; they do not certify the housekeeping head.
Fresh #191/#192 reruns and new #193 exact-head CI results belong in the final PR handoff.
For the main-only housekeeping head, #193 is temporarily retargeted to main to trigger full
CI on publication of the housekeeping head (the synchronize event), then restored to #192
after completion. Main is an ancestor of the candidate; the combined
merge tree equals its head tree. Temporary display of inherited files during validation is
not a merge, cherry-pick or duplication in history. The final stacked diff remains #193-only.
Future pushes while #193 is based on #192 will need main retargeting for CI; after the owner
merges parents, main is its permanent base and ordinary CI applies.

Run `./ci.sh all`, `./ci.sh source-check`, `./ci.sh fixture-test` and `git diff --check`
on each unchanged candidate. `rowe-test` and `rowe-sanitize` are **not applicable** on #191
or #192: neither target nor the raw C module exists there. Do not backport the decoder merely
to manufacture a passing target. Both focused targets, plus all other gates, are mandatory
on the final #193 combined tree. Hosted CI must use the exact current head, not an old run.

### Combined-tree safety proof

Relative to current main, changes are confined to docs, host tools, native/tests, ci.sh and the
workflow. `git diff origin/main HEAD -- app/src/main native/src native/include` is empty,
as is the diff of every other production path. No profile, trusted hash, memory permissions,
JNI/Android dispatch, enemy decoder, game-data pack or calculator capability is added.
Existing FireRed/Emerald/H&S production behavior is unchanged and regression gates remain.
The native CMake explicit shipping list is unchanged and excludes native/tests; freshly built
arm64-v8a and x86_64 APK libraries must contain the existing reader positive control and no
`rowe_raw_` symbol. Record that inspection against the final head/APK in the handoff.

All added artifacts are text documentation, independently written code/tests, scripted button
movies or limited sanitized numeric/hash evidence. No ROM, save/state, screenshot, full memory
capture, patch binary, generated game table, upstream GPL implementation or copyrighted asset
is staged. Diagnostic ROM hashes in host evidence are not production trust entries. Research
merge cannot authorize live R.O.W.E. access; #59 remains open and full integration remains HOLD.

### Proposed next task — do not start here

**R.O.W.E. 2.1.9.1 Experimental: Exact-Release Data and Text Mapping Evidence.**
Pin legitimate exact-release source/build inputs or a documented independent extraction from
the identified private ROM. Establish species ID/name and form mapping, move ID/name mapping,
12-byte nickname encoding/terminators/full-length/control/glyph behavior, stored versus
mode-dependent effective nature and overrides. Record per-field provenance and contradictory
or unavailable evidence; test unknown IDs/forms/text/modes with explicit safe refusal and no
global-table fallback. Keep raw observation separate from semantic authority. Older R.O.W.E.
1.9.4 source and generic Pokémon tables are leads only, never authoritative 2.1.9.1 mappings.
Deliver a versioned mapping/refusal matrix and ROM-free synthetic checks in a separately
authorized task, with no profile/trust/production player/enemy/calculator activation. Do not
begin mapping implementation or open its issue/PR during this reconciliation.
