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

CI temporarily allows the #191 parent (`chore/issue-59-rowe-release-evidence`) and #192 parent
(`chore/issue-59-rowe-party-abi`) as PR bases. Keep these narrowly scoped exceptions while stacked
exact-head validation depends on them. **Stack reconciliation must remove both exceptions and
restore the main-only trigger** after dependencies are merged/retargeted and required CI completes.
Do not merge/enable auto-merge on this task's behalf; #59 and the evidence PRs remain open.

Next bounded task: independently gated player-party integration and exact-release data/text/
mode mapping, with fresh captures for counts 3..6/decreases, boxing, faint/PP restoration,
level/move changes and egg/nonzero-form/override cases. Outer exact-ROM trust and production
memory permissions require separate review. Enemy authority, battle lifecycle and calculator
mechanics require separate research. This decoder is ready for isolated correctness review,
not full ROM support or automatic integration.
