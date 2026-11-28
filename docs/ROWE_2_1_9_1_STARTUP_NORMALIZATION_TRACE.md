# R.O.W.E. 2.1.9.1 — executed startup normalization

Phase 6F, 2026-10-14 (America/Phoenix), research for [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59).
Fetched starting main: `87ccaf6300c22ee84cc276a0d2d509e91be08297`, containing merged
[#199](https://github.com/Sonoran-Solutions/dualdex/pull/199). Its latest senior review
was read before choosing instrumentation. Historical Phase 6D/6E files are unchanged.

**EXECUTED_PATH_VERIFIED, for this cartridge and selected startup only.** The conditional
caller reaches `0x080A4044`, its initial six-slot loop processes occupied slot 1, and
the species setter's Thumb instruction at **`0x0809B980`** writes the selected record.
The form index is **recomputed** as 1, not generally preserved from the old form field.
The full word changes **`0x38581618 → 0x38581034`**, representing `1560/1 → 52/1`.
Both representations resolve to metadata key 1560. Production compatibility remains HOLD.

The [sanitized manifest](../tools/rom-evidence/evidence/startup-normalization-v1.json)
contains exact identities, ordered numerical events, digests, control checkpoints,
source/build bindings and classifications. Private ROMs, cartridges, registers,
records and screenshots remain outside Git under `dualdex-private/phase6f`.

## Admission and source identity

| Input | Exact identity |
|---|---|
| ROM size | 33,554,432 bytes |
| ROM SHA-1 | `81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77` |
| ROM SHA-256 | `fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3` |
| Reference mGBA SHA-256 | `fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e` |
| mGBA source revision | `e31759b24e7a4e3899285ff720d7b573ac328ae7` |
| Existing adapter SHA-256 | `aeefe0a47b5350e8c006d50eba5db2ef4227c23beb6449fc0b006ccdfc5f0c99` |
| Adapter source revision | `37eec9cba83f68115d8bd4d01c96bfaf51c8a682` |
| Copied cartridge SHA-256 | `e626ee3675b51b5e07b86983f44c5770789edf1a09d7105de49a3cdad0d62cb2` |
| Diagnostic mGBA SHA-256 | `ca73e52df7d4c699c43c0f1d58dc757b9f40455f02bcc4db6f15f5566748eceb` |

All were hashed locally. The source checkout is clean at the exact revision. The
adapter's current source inputs pass `verify_adapter`; it was neither rebuilt nor
changed. All 28 historical private artifacts bound by Phase 6E were rehashed.
`inspect_cartridge` independently reproduces Phase 6E's uniquely matching,
checksum-valid serialized party and slot 1 digest. No gameplay acquisition,
emulator-state load, cartridge edit, guest RAM poke or ROM patch was used.

## Feasibility and isolated instrumentation

The existing host, `rowe_observe.py` and `probe_host.c` expose frame stepping and
bounded memory reads, without a guest-PC interface. At this exact mGBA revision:

- `CMakeLists.txt` constructs the libretro target from `CORE_SRC`, omitting
  `DEBUGGER_SRC` and `ENABLE_DEBUGGERS`. Its cache's `ENABLE_DEBUGGERS=ON` is insufficient.
- `src/platform/libretro/link.T` exports only `retro_*`; `core` is static.
  The pinned binary's dynamic symbols expose no debugger/core access API.
- The supported debugger API provides `mDebuggerAttach`, `BREAKPOINT_HARDWARE`,
  `WATCHPOINT_WRITE` and `mDebuggerRunFrame`. ARM hardware breakpoints compare
  addresses; they do not replace instructions. Memory watchpoints install a host
  memory-function shim, then call the original memory function unchanged.
- `GBAAttachDebugger` hot-plugs the component without resetting the guest.
  ROM-load reset performs HLE BIOS setup without executing guest instructions.
  Tracing was attached after SRAM import and before the first `retro_run`.

The developer-only [patch](../tools/rom-evidence/rowe_startup_debugger.patch) changes
only an isolated source archive's CMake target and libretro frontend. It adds debugger
sources/definition, a `retro_rowe_trace_enable` callback, fixed hardware breakpoints,
a write watchpoint over `[0x02025190,0x02025194)`, and routes diagnostic frames through
`mDebuggerRunFrame`. Shipping sources, JNI, host targets and core binaries are unchanged.
No modified emulator tree or binary is committed.

Tracing replaces `ARMRunLoop` frame execution with debugger instruction stepping.
Debugger entry sets `nextEvent=cycles`. Host performance and event scheduling can
differ. SaveBlock relocation differs in the control; its cause was not investigated.
This is a **different binary**, whose evidence is accepted only with the parity
checks below. It is not an executed-PC result from the unmodified pinned core.

Rebuild recipe for a new, private, isolated source directory (never apply to the
shipping `cores/mgba-src` checkout):

```bash
# trace_src and trace_build must name fresh developer-only directories.
git -C cores/mgba-src archive e31759b24e7a4e3899285ff720d7b573ac328ae7 | tar -x -C "$trace_src"
patch -d "$trace_src" -p1 < tools/rom-evidence/rowe_startup_debugger.patch
cmake -S "$trace_src" -B "$trace_build" \
  -DBUILD_LIBRETRO=ON -DBUILD_QT=OFF -DBUILD_SDL=OFF -DBUILD_GL=OFF \
  -DBUILD_GLES2=OFF -DBUILD_GLES3=OFF -DBUILD_TEST=OFF -DBUILD_LTO=ON \
  -DENABLE_SCRIPTING=OFF -DENABLE_GDB_STUB=OFF -DUSE_EDITLINE=OFF \
  -DUSE_FFMPEG=OFF -DUSE_SQLITE3=OFF -DUSE_DISCORD_RPC=OFF -DUSE_ELF=OFF \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$trace_build" --target mgba_libretro -j4
```

The manifest records compiler, complete target flags, source archive digest,
modified-source digests, patch digest and CMake cache digest. Another compiler/path
may produce a different binary; the validator deliberately refuses it pending a
separately reviewed build identity. CMake success alone is never a parity result.

The retained private `capture.py` uses the pinned Observer, an occupied-only coherent
snapshot override, and the exported callback. It imports only a copied cartridge,
installs tracing before running, records event order, selected record and restored
SaveBlock digest at callbacks, and replays the same input batches as the control.
Its source digest is in the manifest. Errors invalidate the whole capture.
Batch frame counters are lower bounds, so no exact instruction-frame timestamp is
claimed. The API supports one enable in one process; reset/reuse is outside scope.

## PC conventions and decisive events

Thumb boundaries were checked against the hash-verified exact ROM using Capstone.
At a hardware breakpoint, mGBA compares `gprs[15]-2`: the next instruction is reached
but has not yet executed. At a store watchpoint the pipelined PC is four bytes ahead
of the writing Thumb instruction. The callback occurs **before** the original store;
the next setter-return breakpoint independently confirms the completed write.
Function entry alone is never accepted as write attribution.

| Event order | Executed-path observation |
|---|---|
| 0–4 | Startup clearing/restoration touches selected word. These early watchpoints do not establish complete-record restoration. `storeMultiple` watch values are unusable and excluded from attribution. |
| 5 | Caller `0x0803014A` reached with original complete selected-record digest; restored SaveBlock copy also original. This proves this conditional path was taken, without assigning general meanings to all caller guards. |
| 6 | Entry `0x080A4044`, LR `0x0803014F`, linking it to the caller. |
| 7–10 | Loop slots 0 and 1; at `0x080A407C` slot 1 getter returns R0=1560, record address `0x02025174`. |
| 11–13 | Selected branch `0x080A4092`; base helper `0x080A18E8` receives 1560, returns R0=52 at `0x080A4098`. |
| 14–15 | Index helper `0x080A18A0` receives 1560, returns R0=1 at `0x080A40A2`. Exact-ROM table puts 52 at index 0 and 1560 at index 1. |
| 16–17 | Call at `0x080A40AA`, dispatch entry `0x0809B57C`, R0=`0x02025174`, selector R1=11; matching R2 source linkage. |
| **18** | Watchpoint at destination **`0x02025190`**, width 2; pipelined PC `0x0809B984` attributes **`strh r0,[r5,#0x1C]` at `0x0809B980`**. Old low half `0x1618`, new low half `0x1034`, full pre-word `0x38581618`. |
| **19** | Setter return `0x080A40AE`: full word **`0x38581034`** and complete normalized-record digest. |
| 20–23 | Form setter call `0x080A40B4`, selector 89, same record/source linkage. `str r0,[r5,#0x1C]` at `0x0809B9AE` writes width 4, old=new=`0x38581034`. Returned checkpoint `0x080A40B8` remains identical. |
| 24–27 | Remaining loop slots 2,3,4,5 reached at `0x080A4062`, all with 76-byte stride. |
| 28 | Caller return `0x0803014E`: selected complete normalized digest; SaveBlock copy still original. |

The separate stats-entry callback was filtered by the private driver; no separate
`0x08099674` function-entry hit is claimed. The stats call instruction remains in
the verified code following `0x080A40B8`. No boxed-slot trace or runtime claim is made.

## Identity and reference-core parity

Original selected-record SHA-256:
`af20b2255c921973b4ad9c4229256058b67679f8e707a42e480284aeec5e84d7`.
Normalized selected-record SHA-256:
`db78be9c01606da1caa7718662fad661a95c84052df5d784b07e196ddbb5a175`.

Only offsets **28–29** change. The other 74 bytes are equal at all captured checkpoints
inside this call, including PID, OTID, nickname, form 1, level 5, HP/max HP 19/19 and
moves `[252,45,10,44]`. Metadata key 1560, Meowth and types `[17,17]` match.
Raw words retain unrelated high bits. The original `1560/1` diagnostic value is kept;
there is no global normalization policy. The historical regional Meowth summary was
visually re-inspected: Meowth No052, male, Lv5, gray-blue portrait, DARK badge.

Both fresh processes used identical copied cartridge inputs, no emulator state,
780 released frames, three Start presses (one pressed +90 released each), 600 released
frames, then A for one frame and 390 released frames. At frames 1653 and 2044:

| Comparison | Both diagnostic and unmodified control |
|---|---|
| Player count / map | 2 / Cape Brink `1/6`; coordinates match |
| Live party-region SHA-256 | `2416edc7322cb2e094a4b4d5605d1b033472eadb9eb37231f4dad7d6798b60ad` |
| Serialized/original party SHA-256 | `229216c6340e9957f82dd20456888eb9d5de948b763f74e14477949e95927d2c` |
| Selected record | Normalized digest above, `52/1` |
| SaveBlock selected copy | Original digest above, `1560/1` |
| Video | Exact screen digests match at both checkpoints |

Continue-menu and subsequent overworld captures were inspected. The game progresses
normally past Continue in both runs. Frame 1653 is **startup-only temporal evidence**;
frame 2044 is the coherent loaded-player snapshot. SaveBlock pointers differ, recorded
explicitly in the manifest; the copy is not treated as live authority. No full RAM,
timing determinism, or general equivalence of different emulator builds is asserted.

Earlier feasibility attempts are retained separately: one prototype hit the private
capture guard and had callback errors; another diagnostic iteration read an unused,
uninitialized breakpoint width. Neither is the accepted run. The final binary masks
breakpoint width to zero, has no callback errors, and reproduces the control results.

## Acceptance and validation

`rowe_startup_trace.py` admits only the exact ROM, diagnostic core/source and captured
cartridge. Its parser requires ordered, complete events, getter/helper/setter linkage,
the selected destination/width/PC, matching before/after complete records, six-slot
iteration, routine return and restored-copy binding. Missing evidence stays partial;
unexpected/conflicting evidence refuses. Output success is cleared before input
admission. Its synthetic PASS is not proof of an emulator run.

The new identity gate requires explicit static metadata equality, supported form
mapping, stable decoded fields, exact changed offsets and preserved unrelated packed
bits. It does not treat historical `compare_records().status=STATIC_VERIFIED` as PASS.
The negative regression mutates each unrelated record byte. A ROM-free cross-phase
contract test binds full prior manifests and explicitly checks cartridge, party and
record hashes, pairs, key 1560, prior RUNTIME_CONFIRMED status and current tool/adapter
source hashes. Historical statuses are preserved.

Local validation passed: `./ci.sh all`, `source-check`, `fixture-test` (73 replay
cases), `rowe-test` (422,771 assertions), ROM-evidence discovery (60 tests), focused
trace/contract tests (12) and identity tests (6). ASan/UBSan passed with
`detect_leaks=0`; the full `rowe-sanitize` with LeakSanitizer separately passed
outside the sandbox. APK inspection confirmed both shipping ABIs, `arm64-v8a` and
`x86_64`, exclude diagnostic decoder/tracing symbols in all four libraries each.
FireRed, Emerald and H&S production sources are unchanged; canonical synthetic,
calculator and Kotlin regressions passed. No new gameplay compatibility claim is made.
Exact-head hosted validation results are recorded in the PR handoff.
Canonical targets: `./ci.sh all`, `source-check`, `fixture-test`, `rowe-test`,
`rowe-sanitize`; ROM-evidence discovery, focused trace/identity tests, ABI symbol
exclusion and `git diff --check`. ASan/UBSan and LeakSanitizer are reported separately.
The initial sandbox attempt failed the existing temp-directory guard because `/tmp`
has a sandbox `.git` marker; the rerun uses a private TMPDIR outside Git. Source-check
also needs write access to regenerate the pinned upstream headers. These initial
failures are not clean validation results.

| Determination | Status |
|---|---|
| GUEST-PC TRACE CAPABILITY | VERIFIED |
| STARTUP CALLSITE EXECUTION | VERIFIED |
| NORMALIZATION FUNCTION EXECUTION | VERIFIED |
| SELECTED MEOWTH BRANCH | VERIFIED |
| SPECIES/FORM WRITE ATTRIBUTION | VERIFIED |
| CONVERSION BOUNDARY | EXECUTED_PATH_VERIFIED |
| UNMODIFIED-CORE PARITY | PASS |
| STORED IDENTITY STABILITY | PASS |
| NORMALIZATION SCOPE | VERIFIED_NARROW |
| PRODUCTION COMPATIBILITY | HOLD |
| ISSUE #59 | OPEN |

Alternate modes, other forms, boxing, reorder, a second save/reload, generalized
caller conditions, invalid inputs, enemy party and calculator support remain unverified.
Recommendation: a separate bounded read-only analysis of the startup caller's guards
and configuration provenance before considering a production identity contract.
Do not activate production support or begin that task automatically.
