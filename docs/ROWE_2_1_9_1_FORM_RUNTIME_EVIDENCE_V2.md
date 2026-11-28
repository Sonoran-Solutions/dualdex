# R.O.W.E. 2.1.9.1 Experimental — form acquisition evidence, revision 2

Audit: 2026-10-14, America/Phoenix. References [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59), which remains open.
[Predecessor #196](https://github.com/Sonoran-Solutions/dualdex/pull/196) was confirmed merged through GitHub metadata
before fetching main and creating `research/59-rowe-form-acquisition` directly from
`959a418e4ada65e5e70ac51c7bcaf384b7a71c5c`. No stacked PR, merge or auto-merge.

**ACQUISITION ROUTE: BLOCKED; NONZERO FORM ACQUIRED: NO; NONZERO RUNTIME IDENTITY: NOT_VERIFIED;
MODE SEMANTICS: NOT_VERIFIED; PERSISTENCE: NOT_RUN; PRODUCTION COMPATIBILITY: HOLD; ISSUE #59: OPEN.**

This revision establishes specific exact-release encounter-table candidates and a narrower blocker:
Route 1's alternate Zigzagoon roster and Cape Brink's alternate Rattata/Zigzagoon roster exist,
but neither an authorized checkpoint at those maps nor a verified ordinary travel path from the
available Route 101 checkpoint was established. Generation flags and clock configuration are also
unverified. This is an **evidence/access blocker**, not proof those maps are unreachable or forms
unobtainable. No acquisition gameplay was attempted. There was no new grass search, RAM modification,
enemy RAM read, catch, summary observation, save or persistence experiment.

Numerical results, selected entry/region hashes, checkpoint bindings and bounded instruction-span
hashes are in [`form-acquisition-v2.json`](../tools/rom-evidence/evidence/form-acquisition-v2.json).
The [revision-1 report](ROWE_2_1_9_1_FORM_RUNTIME_EVIDENCE.md), its manifest, movies and historical
#192/#195/#196 observations remain unchanged.

## Comparator corrections

`rowe_form_observe.compare` is the shared comparator; its only non-test caller is its host-only
CLI. Its other callers are ROM-free tests. `RUNTIME_CONFIRMED` now requires stored form >0 and <32,
successful exact-ROM static resolution, coherent occupied player slot, full region and record hashes,
a valid matching paused-screen digest, independently supplied UI provenance, a positive explicit
human distinctive-form assessment, verified configuration, matching species name and matching level.
A form-zero baseline remains `RUNTIME_OBSERVED` even with otherwise positive human evidence.
A shared name never confirms a form. The human assessment is not an automatic sprite/type classifier.

Species-name or assessed form-cue disagreement remains `CONTRADICTED`, including when the mode is
unknown or the level differs. Level comparison is now separate:

| Level result | Meaning and acceptance |
|---|---|
| `MATCH` | Raw stored and independently transcribed UI values agree. Other gates still apply. |
| `LEVEL_DIFFERENCE_UNVERIFIED_MODE` | Potential effective-level override; mode unknown. Observation only. |
| `LEVEL_DIFFERENCE_VERIFIED_MODE` | Configuration is assessed, but its effective-level accessor is not established. Observation only. |

Both discrepancies block confirmation. Neither alone claims the species/form mapping is wrong.
`stored_level` and the original `ui.level` are preserved. Confirmation of configuration is insufficient
to classify its level accessor. Historical results are not reclassified retroactively. The old comparator
hash in the historical-evidence regression is deliberately pinned to the old source, rather than
rewriting the historical manifest to match this correction.

## Exact-release identity and investigation method

Only the authorized local ROM was read. `load_exact_rom` verified all three requirements:

- Size: **33,554,432 bytes**.
- SHA-1: `81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77`.
- SHA-256: `fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3`.

Existing core SHA-256: `fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`;
attributed source: `e31759b24e7a4e3899285ff720d7b573ac328ae7` (not rebuilt).
Existing adapter SHA-256: `aeefe0a47b5350e8c006d50eba5db2ef4227c23beb6449fc0b006ccdfc5f0c99`;
source revision `37eec9cba83f68115d8bd4d01c96bfaf51c8a682`; its source/build pin was verified before initialization. Observer, comparator, data and symbol tool
hashes are in the new manifest. Capstone 5.0.7 decoded bounded Thumb regions for manual inspection;
it is an installed local analysis aid, not a new repository/runtime dependency. The manifest records
the actual installed version and hashes of the private trace/checkpoint scripts.

The older [RoweSource wild encounter file](https://github.com/BelialClover/RoweSource/blob/292eab691310e8b0cdb91a3905c34105261e24e1/src/wild_encounter.c)
and [level scaling file](https://github.com/BelialClover/RoweSource/blob/292eab691310e8b0cdb91a3905c34105261e24e1/src/level_scaling.c)
were used only as search hints. No older-source encounter list or flag name is admitted as exact-release
proof. In particular, the old `CreateWildMon` contains a randomized-mode form-reset clause that is
**not present in the inspected exact-release constructor span**. Copying that behavior would be wrong.
No PokeDaisy generator or extracted tables were imported.

## Exact-ROM acquisition path

The embedded 150-entry symbol table has no named wild encounter table. Its absence does not imply
that encounters use vanilla Emerald tables. These facts were established from exact-ROM instructions:

1. Map-header lookup at `0x080C28DC` indexes a group pointer and map pointer through root
   `0x0883937C` (literal at `0x080C28F0`). Group 0 begins at `0x08838800`.
   The loaded Route 101 header matches ROM header `0x0883395C` over its first 20 bytes.
   Target map headers' section byte at +20 indexes the existing exact-ROM region-map table
   `0x08FD278C`, stride 8, name pointer +4. Its bounded text decoder independently reads
   **Route 1**, **Route 22**, **Cape Brink**, and **Canyon Entrance**. These names are exact-ROM
   data, not UI transcriptions and not copied old map names.
2. Encounter-header search `0x080FBF44` reads table `0x08F27970` (literal `0x080FBF90`),
   compares map group/number against save-block offsets +4/+5, and advances by **32 bytes**.
   There are 193 entries before the group-255 sentinel at `0x08F29190`. The special duplicate-map
   branch at `0x080FBF70..0x080FBF8A` must be retained when investigating its affected map;
   the selected map IDs do not match that special comparison.
3. Land header fields +4 and +8 are distinct info pointers. Each info has encounter rate at +0
   and roster pointer at +4. The land selectors reach 12 slots; each slot is four bytes, with
   two level bytes and a 16-bit species identifier at +2. This is a **32-byte expanded header**,
   not ordinary Emerald's four-method header. Other method pointers were not decoded as land rosters.
4. Normal land dispatch `0x080FCA64` calls selector `0x08244FBC`: nonzero selects header +4;
   zero selects +8 at `0x080FCAD8`. That selector tests flag **0x470**, then a byte at +2 of
   a shared time structure. With the flag clear it returns 1 for values 6..17 inclusive; with
   the flag set it inverts that outcome. Older source calls the second roster night, but this
   task's exact requirement is **selector returns zero**. No host-time/night-tint assumption is used.
5. The land selection path `0x080FC490..0x080FC4AA` reads the chosen identifier, passes it
   through `0x080A3FE0`, and calls wild constructor `0x080FC09C`. The former special-cases
   three other identifiers; neither 1551 nor 1582 is one of them. The constructor gets leading
   player held item through selector 12, calls generation transform `0x08037E14`, derives the
   returned identifier's form index via `0x080A18A0`, and passes that index into its construction
   wrappers. The gender/ability branch chooses a wrapper; both pass the derived form onward.
6. Transform `0x08037E14` has conditional flag paths, including selectors 0x28, 0x30, 0x3C,
   0x2B, and additional conditions, and consults evolution/level/held-item state. Consequently
   a roster ID is **not an unconditional final stored identity**. Those flags were not assigned
   English mode names from old source and were not verified in the inherited checkpoint.
7. Form-index helper `0x080A18A0` searches the identifier's shared list for its own index.
   1551's list `0x085E6984` starts with base 19 and has 1551 at index 1; 1582's list
   `0x085E6D9A` starts with base 263 and has 1582 at index 1. Base-species helper
   `0x080A18E8` returns the first list entry, but **the inspected nature wrapper passes the
   form-species identifier directly onward**, not through that helper. Therefore do not promise
   stored 19/1 or 263/1. Candidate constructor arguments can instead be 1551/1 or 1582/1;
   both aliases also statically resolve back to 1551/1582 respectively. The actual full captured
   player record must decide. This alias distinction is material.
8. Main constructor `0x08098060` forwards its form argument to box constructor `0x080980F8`.
   The latter writes species through selector 11 at `0x080982DC`, then form through selector 89
   at `0x080982E6`. Setter dispatch table `0x0809B72C` maps those selectors to `0x0809B95E`
   (12-bit species at +28) and `0x0809B98C` (five form bits at +28, shift 12). This establishes
   a static path for a form argument to be stored. Capture transfer, summary display and actual
   post-transform/catch identity were not executed and remain unverified.

All quoted spans have address/length/SHA-256 bindings in the manifest. No disassembly, complete
roster, screenshot, save, state or raw memory is committed.

## Route matrix and selection

Only two serious acquisition routes were selected; the other matching entries below are incidental
inventory findings for the same two species, not further gameplay projects.

| Candidate | Acquisition method | Exact-release evidence | Requirements | Status |
|---|---|---|---|---|
| Primary: alternate Zigzagoon, form-species 1582 | Route 1 land, second roster | map 0/87; header 138 at `0x08F28AB0`, field +8; info `0x08F263BC`; slot 2 at `0x08F26394`; level bytes [5,5] | Legitimately reach map 0/87; selector `0x08244FBC` = 0; inspect generation configuration, acquire with ordinary controls | CANDIDATE; execution BLOCKED |
| Fallback: alternate Rattata, form-species 1551 | Cape Brink land, either roster | map 1/6; header 154 at `0x08F28CB0`, fields +4/+8 share info `0x08F26B5C`; slot 6 at `0x08F26B44`; [5,5] | Legitimately reach map 1/6; inspect generation configuration; ordinary capture | CANDIDATE; execution BLOCKED |
| Same Zigzagoon candidate | Cape Brink land | same info; slots 9 and 11 at `0x08F26B50`, `0x08F26B58` | Same fallback map/configuration | CANDIDATE only |
| Same Zigzagoon candidate, incidental | Route 22 second roster; Canyon Entrance both rosters | map 0/95 header 139 slots 8/10; map 1/73 header 173 slot 6; addresses/hashes in manifest | Travel, clock and generation gates unverified | CANDIDATE only |
| Both selected identifiers at available checkpoint | Route 101 land rosters | map 0/16; header 0; rosters `0x08F23464` and `0x08F2349C`; bounded 12-slot scans contain neither 1551 nor 1582 | No table-supported direct spawn for these two candidates here | BLOCKED for this proposed route |
| Either selected form through starter/gift/trade/form change/evolution | Alternative deterministic acquisition | No exact-release script/choice/trigger-to-player-record chain established in this bounded investigation | Would require an exact script/table target plus verified access/configuration | NOT_ESTABLISHED; no gameplay attempted |

Route 1 is primary because its exact land slot is known and its expected alternate cue is useful;
Cape Brink is fallback because the selected roster is shared across both time branches. Neither is
claimed reachable from the checkpoint. The row for Route 101 does not rule out other nonzero forms,
randomized transformations, gifts, or undocumented mechanics. No exhaustive world/reachability search
was performed. A static name shared with form zero is insufficient confirmation.

## Authorized checkpoint check and stopping decision

The pinned observer/adapter infrastructure loaded the existing #192 ordinary-gameplay checkpoint
`multi_before.state`, SHA-256 `bfd0a0f354e776e03c022cefa73944a4fed06d5f6b9f6e4e9cecb886c92f6aaa`,
in a disposable fresh process. There were **no input/frame steps**. The check read save-block pointer,
six location bytes, the 28-byte loaded map header, and the player snapshot only. The selected scripts'
hashes are in the manifest. No new UI observation is claimed from this static paused checkpoint.

Location was map **0/16**, exactly named Route 101 by ROM data. Player count was 2; forms [0,0].
The full 456-byte party region SHA-256 was
`af993d77de513673f19ae4eaa3af298debbc70479222dd3620e42ab1a1cf3eae`.
Occupied complete-record hashes were:

- slot 0: `d69a6c30253f1c377a70c8b7ab14ea1581816c692eb36f49da40b4e9465538d2`;
- slot 1: `4457cadca2069e0db66f924865ac4a09391984128fdd151ba94c2a38f6895630`.

The snapshot uses count / all 456 bytes / count / all 456 bytes again / final count, with identical
counts and data. Count address `0x02025125`, region `0x02025128`, stride 76, maximum six records.
No occupancy was inferred from species bytes. The record ordering is this checkpoint's ordering;
it is not the historical cartridge-reload ordering, and no reorder was performed here.

**Stop:** the available checkpoint supplies neither a form candidate nor a verified travel and
configuration route to the selected maps. Required next input is an authorized own-gameplay checkpoint
at Route 1 or Cape Brink (with its ordinary progression provenance), or an exact-release-supported
ordinary travel itinerary from this checkpoint, plus independently inspected configuration. Merely
making the ROM roster present or loading an unproven save is insufficient. There is no justified
local acquisition movie to execute yet, so no substitute random attempt was made.

## Static/runtime comparison and persistence

| Field | Result/source |
|---|---|
| Baseline static candidate | stored 19/1 → 1551, Rattata, metadata key 1551, raw types [17,0] |
| Primary static candidate | stored 263/1 → 1582, Zigzagoon, metadata key 1582, raw types [17,0] |
| Actual acquired nonzero stored species/form | **Unavailable** |
| Actual acquired static resolved ID | **Unavailable**; do not substitute the static candidates |
| Independently transcribed acquired summary name/types/level/appearance | **Unavailable** |
| Distinguishing form evidence | **Unavailable**; shared names are not distinguishing evidence |
| Runtime configuration | NOT_VERIFIED |
| Nonzero screen/record/process/checkpoint bindings | **Unavailable** |
| Nonzero identity verdict | NOT_VERIFIED; no contradiction or confirmation observed |
| Summary reopen / whole-record reorder / cartridge save and fresh-process reload / boxing | all NOT_RUN |

No numeric type-ID-to-English-label mapping was assumed. There are zero nonzero UI observations.
The comparison corrections and synthetic confirmation examples are tooling evidence only.

## Reproduce the bounded route findings

Use the existing exact-ROM loader and bounded readers. This ROM-free committed command contains
only addresses and selected numerical assertions; set the local path to your authorized exact ROM.
It emits no raw table or game bytes:

```bash
PYTHONPATH=tools/rom-evidence python3 - /private/exact-rowe.gba <<'PY'
import sys
from rowe_data import load_exact_rom, read_u16, read_u32, read_span
from rowe_observe import digest
rom = load_exact_rom(sys.argv[1])
assert read_u32(rom, 0x080FBF90) == 0x08F27970
assert read_u32(rom, 0x080C28F0) == 0x0883937C
for header, group, number, field, info, index, species in [
    (138, 0, 87, 8, 0x08F263BC, 2, 1582),
    (154, 1, 6, 4, 0x08F26B5C, 6, 1551),
]:
    address = 0x08F27970 + header * 32
    assert read_span(rom, address, 2) == bytes([group, number])
    assert read_u32(rom, address + field) == info
    entry = read_u32(rom, info + 4) + index * 4
    assert read_u16(rom, entry + 2) == species
    print(group, number, index, species, hex(entry), digest(read_span(rom, entry, 4)))
for info in [0x08F23494, 0x08F234CC]:
    entries = read_u32(rom, info + 4)
    assert not any(read_u16(rom, entries + i*4 + 2) in (1551,1582) for i in range(12))
print('Selected roster facts reproduced; runtime acquisition remains unverified')
PY
```

For a later authorized target-map checkpoint, follow the revision-1 `--player-only` capture and
independent UI binding procedure with this corrected comparator. Observe configuration before
asserting `mode_verified`; capture the actual stored pair, not the expected alias. First obtain one
coherent nonzero record/summary pair, then summary reopen, ordinary reorder if supported, actual
in-game save and fresh-process cartridge reload. Mark only executed operations PASS. Stop if the
selected roster is transformed or inaccessible; do not write form bits or start broader integration.

## Validation

Local checks used the existing private `TMPDIR` outside Git, preserving capture privacy guards.
Exact-head hosted CI belongs to the final PR head; no historical CI run is substituted.

- `./ci.sh all` — PASS: canonical native/calculator/tooling/Kotlin suites and debug APK.
- `HNS_UPSTREAM_DIR=/home/dq/Projects/upstream-hns/pokehns-expansion ./ci.sh source-check` — PASS.
- `./ci.sh fixture-test` — PASS: 73 replay/rejection cases and 422,771 ROWE assertions.
- `./ci.sh rowe-test` — PASS: 422,771 assertions.
- `ASAN_OPTIONS=detect_leaks=0 ./ci.sh rowe-sanitize` — PASS: ASan/UBSan, 422,771 assertions.
- Default `./ci.sh rowe-sanitize` — LeakSanitizer UNSUPPORTED: exits 1 with the explicit
  “LeakSanitizer does not work under ptrace” diagnostic. No leak-free claim is made.
- ROM-evidence discovery — PASS: 42 tests. Focused form suite — PASS: 8 tests, including
  positive form-zero refusal, both level caveats, identity contradictions in either mode,
  missing/mismatched/malformed hashes, static refusals and prior-success clearing. Existing
  snapshot tests cover changed counts and mismatched repeated reads; resolver/CLI cases cover
  invalid IDs/list entries, unknown species and refusal of global Emerald/H&S fallback.
- Exact-ROM evidence — PASS: all 11 selected entries, header-to-map-name chains, 14 instruction
  span hashes and current tool hashes rechecked. The report's reproduction command ran successfully.
- Shipping ABI exclusion — PASS: arm64-v8a and x86_64 unstripped native libraries contain no
  `rowe_raw_` symbols; corresponding packaged APK libraries match checked stripped build outputs.
  Digests are in the manifest. Shipping CMake still excludes the diagnostic decoder.
- `git diff --check` — PASS. Only comparator/tests and this versioned report/manifest change.
  No production hash registration, profile permission, JNI dispatch, data pack or calculator feature.
- Exact-head hosted CI: the final draft PR records the commit SHA, run URL and job conclusions
  after push; the previous PR's successful run is not this task's validation.

The next bounded task should verify ordinary access to **Route 1 map 0/87** with its second land
roster selected, or **Cape Brink map 1/6**, record configuration and generation conditions, then
capture one ordinary catch and its distinctive summary plus summary reopen. The current evidence
justifies that focused acquisition/access investigation, not production integration or another
Route 101 search. No subsequent task is started here.
