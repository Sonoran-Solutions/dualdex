# Unbound exact-candidate party layout evidence

Task `unbound-runtime`, issues [#60](https://github.com/Sonoran-Solutions/dualdex/issues/60)
and [#26](https://github.com/Sonoran-Solutions/dualdex/issues/26). Inspected 2026-11-03
(America/Phoenix), base `d592f559e0eb97159513551fc05ea6a663532a9a`.

**Runtime evidence is BLOCKED.** This change supplies a diagnostic observer and
identity-bound static findings, not two observed party slots or permission to enable
support. Production profiles, hashes, readers, Kotlin, CI and the shared README are
unchanged. The release label is **NOT_VERIFIED**; the inherited FireRed header does
not establish a version. No ROM, save, state, RAM, screenshot, extracted table or
instruction bytes are included.

The [sanitized manifest](../tools/rom-evidence/evidence/unbound-runtime-static.json)
records exact input identities, adapter/source pins, selected instruction-span
hashes and the limitations. **VERIFIED** below means the stated static operation
or hash was checked, **OBSERVED** means an actual runtime/UI observation,
**NOT_VERIFIED** means insufficient evidence, and **BLOCKED** names a prerequisite.
There are **no OBSERVED runtime findings** in this revision. `NOT_RUN` describes a
scenario that was not exercised.

## Identity and prerequisite results

| Input | Result |
|---|---|
| Candidate | **VERIFIED** 33,554,432 bytes, SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`; header `POKEMON FIRE` / `BPRE` |
| Thor battery | **VERIFIED** 131,072 bytes, SHA-256 `a561cab2c9f4ecd32997d34a6f6b82ca592ec71f28d8713a16c9950e797e5236` |
| Thor quicksave | **VERIFIED** 528,448 bytes, SHA-256 `eb64974477c908ab5a46f52642047fd55db2ee02b7999c67191bff7615727482`; host acceptance **BLOCKED**, not tried |
| Pinned host core | **VERIFIED** SHA-256 `fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`; existing developer documentation attributes mGBA commit `e31759b24e7a4e3899285ff720d7b573ac328ae7`; no independent rebuild here |
| Developer adapter | **VERIFIED** canonical developer build reproduced `aeefe0a47b5350e8c006d50eba5db2ef4227c23beb6449fc0b006ccdfc5f0c99`; existing `adapter-pin.json` source/build inputs all matched |
| Exact release / publisher patch | **NOT_VERIFIED**; publisher patch/source build was not supplied; no download attempted |
| Issue #24 | **BLOCKED**: required `gh issue view 24 --json body,comments` failed connecting to GitHub; web fallback also failed. Its comments were not read or treated as authority |

The required private directory `/home/dq/dualdex-private/unbound/unbound-runtime/`
is outside this sandbox's writable roots (only this worktree and `/tmp`). The
owner was asked about using `/tmp`; no authorization was received during this
revision. Independently, `/tmp/.git` exists in this environment, and the inherited
private-directory guard rejects every `/tmp` capture path. Removing that marker,
bypassing the guard or writing private captures into the checkout was not attempted.
Required remedy: run in an environment permitted to write the prescribed private
directory, outside any Git checkout. No battery/state core run was launched.

## Selected exact-ROM instruction evidence

Read-only bounded Thumb disassembly used local Capstone 5.0.7. No public CFRU/DPE
source revision was fetched or substituted for the exact cartridge. Function names
here describe behavior; they are not claims of exact-release symbols. The observer's
`inspect_rom()` rechecks the full ROM identity and all seven inspected span hashes;
it emits numerical facts and descriptions, never disassembly or game tables.
This is an identity-bound manual inspection record, not an independent generic
disassembler, executed-PC trace or proof of all callers/modes.

* `0x08040C3C..0x08040C80`: literal at `0x08040C48` is `0x02024029`.
  The function clears/increments it with byte stores and reads it with byte loads;
  it bounds the index at 5. At `0x08040C5E..64` it multiplies the index by 100 and
  adds the literal player base `0x02024284` from `0x08040C7C`, then calls the
  party getter with selector 11. This establishes the candidate count/base/stride
  statically; it does not establish a coherent occupied party after Continue.
* `0x0803F940` forces permutation index zero before the jump table. Its zero case
  at `0x0803F9C8` and shared return tails at `0x0803FBCC..E8` return the four
  pointers at record offsets `0x20`, `0x2C`, `0x38`, `0x44`. The caller at
  `0x0803FD44..C2` obtains these as G/A/E/M. Personality is not used to permute
  these selected paths.
* `0x0803F8F8..0x0803F940`: both historical crypto loops load and store the same
  twelve words; the XOR sites are no-ops. In the field getter, `0x0803FD94..A6`
  are no-ops and `0x0803FDA8` skips the former validation block. Thus fixed order,
  unchanged secure words and bypassed getter validation are **VERIFIED static**.
  This does **not** prove that every stored checksum is zero or what that word
  means elsewhere in the battle/save engine.
* The field dispatch at `0x0803FDD8` sends selector 11 to `0x0804002E`, 12 to
  `0x08040046`, 13..16 to `0x0804005E` and 17..20 to `0x0804006A`.
  The non-bad-egg species path loads the full G+0 halfword, item loads G+2,
  moves load indexed A halfwords, PP loads A bytes, experience loads the G+4
  word, and the six EV selectors load E+0..5 bytes. Species has a bad-egg
  substitution branch; these are stored-field paths, not every effective value.
  No mask or constant internal-ID subtraction is present on the selected normal
  species-load path. A complete identity mapping cannot be inferred from that.

## Field status matrix

Offsets below are relative to a hypothesized 100-byte record. Byte/halfword/word
mean 8/16/32 bits, little-endian. Runtime findings, including the two-slot manual
UI transcription, remain **BLOCKED** pending the output-directory prerequisite.

| Area | Exact-candidate evidence and status | Production comparison / remaining gate |
|---|---|---|
| Party count | **VERIFIED static** u8 at `0x02024029`, loop maximum six | Config matches. Loaded count, count changes and coherent snapshot **NOT_VERIFIED** |
| Stride/base | **VERIFIED static** 100 bytes at `0x02024284` | Config/profile agree (`33702532`). Two occupied slots, switch/permutation experiment **NOT_VERIFIED** |
| Encryption/order | **VERIFIED static** fixed G/A/E/M at `+32/+44/+56/+68`; XOR disabled on inspected paths | Production selects this only when `+28` checksum is zero; actual checksum-dependent disagreement **NOT_VERIFIED** |
| Checksum | **VERIFIED static** getter validation bypass; stored word at `+28` exists | Word value, reuse and save semantics **NOT_VERIFIED**. Zero is not an identity/support credential |
| Species width / ID domain | **VERIFIED static** normal getter reads full 16-bit G+0 | Production masks `0x07FF`; raw high-bit loss is possible. Internal IDs, dex mapping, forms and any national-ID offset **NOT_VERIFIED** |
| Moves / PP | **VERIFIED static** four A halfwords and four A+8..11 bytes | Production masks moves `0x07FF` and PP `0x7F`; exact high-bit semantics and UI names **NOT_VERIFIED** |
| Item | **VERIFIED static** G+2 halfword | Production masks `0x03FF`; full ID domain and held-item UI **NOT_VERIFIED** |
| Level | **NOT_VERIFIED runtime**; inspected stored getter uses byte `+84` | Production uses `+84`; no level/UI correlation or override coverage |
| HP/stats | **NOT_VERIFIED runtime**; selected stored getter paths use halfwords `+86..98` (stat getters have override calls) | Production uses these raw party words; no UI/mutation correlation or effective-stat guarantee |
| IVs/EVs | **VERIFIED static** six EV bytes at E+0..5; IV semantic contract **NOT_VERIFIED** | Observer reports M+4 word and six 5-bit hypotheses; production uses the same Gen III hypothesis; no IV/EV UI comparison |
| Ability slot | **NOT_VERIFIED** | Observer labels M+4 bit31 only as a hypothesis; production vanilla path treats it as a known ability slot. Hidden/alternate abilities unresolved |
| Nickname / OT | **NOT_VERIFIED runtime** | Observer preserves nickname `+8..17` and OT `+20..26` hex plus full PID/OTID; exact text, padding, maximums and UI identity unresolved |
| Enemy party | **NOT_VERIFIED** diagnostic base/count `0x0202402C/0x02024028` | Inherited config and ROM literal occurrences are leads, not lifecycle authority; no enemy scan or fallback. Battle lifecycle **NOT_RUN** |
| Save-block pointers | **NOT_VERIFIED** diagnostic pointer locations `0x03005008/0x0300500C` | Literal occurrences alone do not identify live blocks, copy offsets or serialization; no saved-copy layout claimed |

There are **no measured runtime disagreements**. The mask differences and
checksum-dependent decoding are source/static contract differences, not a claim
that these particular saved Pokémon exercise those cases. Species names, an
internal-to-national offset, the observed checksum word and at least two UI-bound
identities must be established in a later runtime run.

## Observer contract and reproduction

[`unbound_observe.py`](../tools/rom-evidence/unbound_observe.py) composes the existing
ROWE input/read host, movie parser, private-directory guard, video ABI and adapter
verifier. It does not modify shared helpers. It requires the exact candidate ROM,
core, source-pinned adapter and either the exact Thor battery or exact quicksave.
It creates an exclusive private cartridge directory and copies the already hashed
ROM/save/state bytes there; originals are never passed to writable cartridge APIs.
No RAM-write, scan, in-game save automation or cartridge-flush command is exposed.

Movies support bounded `run`, `mash`, `observe`, `save` and `restore`; they reject
`cartridge_save`, unknown commands, excess frames (>500,000), excess commands
(>4,096), duplicate labels and >64 captures. Temporary state save/restore refers
only to this run's private state. Add a released frame after restore before a
screenshot to refresh cached video. Ordinary gameplay inputs can naturally change
RAM and private cartridge RAM; “read-only” means no injected gameplay RAM writes.

At each paused observation the tool reads count, two bounded 300-byte party
chunks, count, both chunks again, then count. Any mismatch or count >6 fails the
whole report and clears earlier observations. Only counted slots are decoded;
all six record digests are retained, with no stale-slot discovery. Raw 600-byte
regions and exclusive PPMs remain private. The JSON contains selected numeric
fields, name hex and digests. It reports fixed raw GAEM and PID-permuted raw/XOR
alternatives separately, including both checksum sums; none is automatically
declared correct. It records ROM/core/adapter/tool/helper/movie/bootstrap hashes.
The output is initialized to FAIL, refuses aliases to named inputs/tools, and is
OBSERVED only after the whole input movie finishes; `runtime_layout` remains
NOT_VERIFIED even then.

The adapter exports `pokemon_parse_single()`, so the observer calls the actual
production parser for **each counted 100-byte record** through its source-pinned
`ParsedPokemon` ABI. Failed native parses expose only `accepted: false`, never
partially populated Pokémon. This is a decoder comparison, not the product's
heuristic party discovery, lifecycle resolver or full Android pipeline.

After resolving the directory prerequisite, use an owner-reviewed movie for the
actual boot/Continue/party-summary route. No untested route is committed as a
successful fixture. For example (these commands are **NOT_RUN**):

```bash
./tools/vanilla-runtime-probe/build.sh
timeout 600 python3 tools/rom-evidence/unbound_observe.py \
  --rom /home/dq/dualdex-private/unbound/unbound-candidate.gba \
  --core /home/dq/Projects/upstream-hns/mgba-host/mgba_libretro.so \
  --battery-save /home/dq/dualdex-private/unbound/thor-2026-09-06.sav \
  --movie /home/dq/dualdex-private/unbound/unbound-runtime/continue.movie \
  --private-dir /home/dq/dualdex-private/unbound/unbound-runtime/run01 \
  --output /home/dq/dualdex-private/unbound/unbound-runtime/run01.json
# Wrap with the coordinator's bounded-output supervisor, not unlimited capture.
# For the separate compatibility attempt, replace --battery-save with:
# --initial-state /home/dq/dualdex-private/unbound/thor-2026-09-06-quicksave.state
# Use a new private directory/report. Do not force a rejected state to load.
```

Inspect private PPMs directly and transcribe at least two slots' summary, stats,
item and move pages, binding the transcription to frame/screen/record hashes.
Compare raw and native fields and distinguish dex number from internal species.
Enemy/battler lifecycle remains a separate optional experiment, not inferred from
nonzero enemy bytes outside a confirmed battle.

## Validation and outstanding work

Validation owner: this task; combined CI owner: coordinator. Required full
`ci.sh test/build/all` were not run. Before execution, the canonical CI header,
test entry points and adapter build script were read; no reusable local binary or
same-input test result was present. Free disk was about 70 GiB. Supervised jobs
used `/tmp/dualdex-host-heavy.lock`, finite 120-second deadlines, process-group
TERM/grace/KILL cleanup, 1 MiB head + 4 MiB rolling tail, 64 MiB produced-output
limit and 10 GiB disk stop threshold. No core/engine job was launched.

* **VERIFIED** `./tools/vanilla-runtime-probe/build.sh` via `timeout 120`:
  exit 0, 0.735 s, no console output; binary and all source pins matched.
* **VERIFIED** ROM-free actual-native ABI/mask self-check: synthetic raw slot,
  parser accepted species 1234, item `0xBCDE & 1023 = 222`, full synthetic move
  words reduced with `& 2047`, expected HP/stats/IVs/EVs. It checked ctypes ABI
  offsets through the real parser, not only a mock; no emulator/core initialized.
* **VERIFIED** focused discovery selected 14 new tests and passed, 0.165 s:
  `python3 -m unittest discover -s tools/rom-evidence -p test_unbound_observe.py -v`.
  Cases include full raw widths, all 24 permutations, XOR/checksums, malformed
  movies, snapshot coherence/stale tails, aliases, core/save identity refusal,
  partial-host cleanup, failed-run invalidation, native projection and static pins.
* **BLOCKED required validation** initial full command
  `python3 -m unittest discover -s tools/rom-evidence -v` ran 77 cases in 0.530 s:
  76 passed (including all then-existing 12 new cases), one existing case errored:
  `test_rowe_observe.ObserveTest.test_private_capture_guard_checkout_worktree_and_symlinks`.
  It expects an outside-checkout tempfile path, but `/tmp/.git` makes that path
  fail the existing guard. No test was weakened/skipped and the marker was not
  removed. Later changes added two focused cases; they do not fix that environment.
* Initial focused attempt selected 10 tests, with two fixture errors caused by
  the same `/tmp/.git` guard. Synthetic-only main-path tests were isolated with a
  mocked guard, and the wrong-ROM CLI case now refuses before the guard/core.
  Actual captures still use the unmodified real guard. Failure logs are preserved.

Bounded non-private validation metadata/logs are under
`/tmp/unbound-runtime-validation/{adapter-build,focused-v1,focused-v2,focused-v3,discovery-v1}/`.
These are synthetic diagnostics, not ROM/core capture outputs. No jobs or owned
descendants remain. The new observer source hash is bound by the manifest; current
revision remains `d592f559e0eb97159513551fc05ea6a663532a9a` with the four new
task files untracked. **BLOCKED checkpoint commit**: `git add` could not create
`/home/dq/Projects/dualdex/.git/worktrees/unbound-runtime/index.lock` because the
worktree metadata is read-only in this sandbox. No commit, push or PR was made.
The coordinator must stage/commit these files from its writable checkout.

Outstanding gates: writable private directory; successful full ROM-free discovery
in a normal tempfile environment; actual battery bootstrap and Continue; coherent
two-slot party capture and manually inspected summaries; checksum semantics and
internal-ID correlations; live save-block/serialized-layout correlation; optional
quicksave compatibility and battle lifecycle. No runtime or support gate is closed.
