# R.O.W.E. 2.1.9.1 Experimental — save/load identity evidence

Phase 6E, 2026-10-14 (America/Phoenix), tracking [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59).
PR #198 was verified merged before work; fetched starting main is
`b23a4eb4adc9110d02cc1fa4b2c8cfaa479d2c9f`. Branch: `research/59-rowe-save-load-identity`.

**The cartridge stores `1560/1`, not `52/1`.** Its complete player-party region matches
the pre-reload observation. After restoration the SaveBlock party copy still has
that representation, while live party RAM has `52/1`. A new cartridge-only process
already has the normalized record digest at the Continue menu, before selecting
Continue. After Continue, the coherent loaded party confirms `52/1`.

This localizes the change to startup restoration before Continue selection, rather
than cartridge serialization. The exact executed conversion instruction remains
**NOT_VERIFIED**: a statically verified conversion branch explains the result, but
there is no executed-PC trace. Do not equate this with normalization on every load.

The [sanitized manifest](../tools/rom-evidence/evidence/save-load-identity-v1.json)
contains identities, hashes, bounded instruction spans, raw numeric representations,
UI evidence, private artifact digests and classifications. Historical
[Phase 6D](ROWE_2_1_9_1_FORM_RUNTIME_EVIDENCE_V3.md) and its manifest are unchanged.
No application, trust, profile, native decoder, JNI or calculator behavior changes.

## Source and reused evidence

- ROM: 33,554,432 bytes; SHA-1 `81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77`;
  SHA-256 `fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3`.
- Core SHA-256: `fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`.
- Adapter SHA-256: `aeefe0a47b5350e8c006d50eba5db2ef4227c23beb6449fc0b006ccdfc5f0c99`.
  Binary and pinned source inputs were verified without rebuilding.
- Cape Brink checkpoint: `5f54fd36f379e8ac0421bc056ed5ee08d68540989ee0c6f00fa18c00c8654364`.
- Cape pre-capture cartridge: `7ec171e34648b983c7b293eabddc9a572ed7a253b22ef906c2b74785c43860bc`.
- Captured cartridge: `e626ee3675b51b5e07b86983f44c5770789edf1a09d7105de49a3cdad0d62cb2`.
- Captured checkpoint: `6876613f49014d851f2b10da224dc52c94df22a47aaa07f73e66876a21501413`.

All 28 private artifacts named by the Phase 6D manifest were rehashed and matched,
including input movies, UI transcriptions and comparison reports. Raw party files
were additionally checked against their bound comparison digests. Originals were
read only. The new run used copies of the exact ROM and captured cartridge, no
emulator state, no RAM writes and no save edits. Acquisition was not repeated.
Private evidence remains under the established `dualdex-private` directory outside Git.

## Lifecycle boundaries

| Boundary | Evidence | Finding |
|---|---|---|
| A: after capture | Phase 6D `reload/captured_party`, frame 10176 | `1560/1` |
| B: before Save | `reload/menu_after_party`, frame 12708 | `1560/1` |
| C: save confirmed | `reload/captured_save_complete`, frame 13099 | Live `1560/1`; SaveBlock copy same record digest |
| D: SRAM export | Verified captured cartridge digest | Serialized `1560/1`; immediate post-export RAM not separately sampled |
| E: startup before Continue | New process, frame 1653, visible Continue menu | Startup record digest equals later normalized record; **not loaded-party authority** |
| F: Continue finished | New process, frame 2044, visible Cape Brink overworld | Coherent loaded `52/1`; saved copy still pre-normalization digest |
| G: summary after Continue | Reused Phase 6D bound summary, independently re-inspected | Gray regional Meowth, No052, male, Lv5, DARK badge |
| H: second save/reload | NOT_RUN | No repeated-save stability claim |

New inputs reproduce the Phase 6D startup: 780 released frames, three Start presses
(each one pressed frame and 90 released), then 600 released frames. Capture E.
Press A for one frame, release for 90 + 300 frames. Capture F. New frame/screen/party
hashes are in the manifest. Startup RAM is only a temporal digest observation; F
establishes loaded-party authority. Existing A/B/C/G evidence was reused.

All party captures use count/data/count/repeated-data/final-count coherence at
`0x02025125` and `0x02025128`, with six 76-byte slots and the complete 456-byte region.
The new private driver uses the pinned Observer read API with an occupied-only
snapshot override; it decodes no unused slots. Historical tools remain unchanged.

## Independent cartridge finding

Read-only analysis locates the complete 456-byte pre-reload region at file offset
`0x12238`, in the sector starting at `0x12000`. Its count byte at `0x12234` is 2.
Slot 1 begins at `0x12284`; its full 76 bytes match the captured Meowth, including
`1560/1`. The loaded `52/1` record does not match that serialized record.

The sector footer at +`0xFF4` identifies section 1, checksum `0xA5EF`, signature
`0x08012025`, counter 3. Summing 1021 little-endian words (+0 through +`0xFF3`) modulo
2^32, then adding the low and high halves modulo 2^16, reproduces the checksum.
The prior section-1 sector at `0x3000`, counter 2, also checksums correctly but does
not contain this captured party. These offsets were corroborated against exact-ROM
copy code and the independently observed restored SaveBlock digest, not assumed
from generic Emerald layout. No general save-generation selection algorithm is claimed.

`rowe_save_identity.inspect_cartridge` requires the exact ROM, 128 KiB cartridge,
complete independently observed party and occupied count. It refuses absent,
duplicate or checksum-invalid matching sections and emits hashes/numeric fields,
never raw bytes. It deliberately does not select a generation, recover damaged
saves, rewrite data or assert an executed loader path. The verified conclusion is
**SERIALIZED_REPRESENTATION_VERIFIED: `1560/1`** for this cartridge.

## Exact-ROM code evidence and limits

Every span below has its exact SHA-256 in the manifest; function names are descriptive
labels, not claims of an exact-release source symbol. Analysis used local Capstone
5.0.7 on the hash-verified ROM, with explicit Thumb entry points and literal-pool
checks. No older source implementation was treated as authority.

| Address | Static behavior and relationship |
|---|---|
| `0x080B2DEC` | Copies live count to SaveBlock1+`0x234`, and six unchanged 76-byte records to +`0x238`; calls copy primitive `0x083AEBA4` |
| `0x080B2E34` | Reverse copy from SaveBlock to live party; no species transformation in this function |
| `0x080B2F40` | Restoration wrapper calls `0x080B2E34`, `0x080B2EC8`, `0x0812728C` |
| `0x081B3D28` | Calls restoration work at `0x081B3624`, then wrapper at call site `0x081B3D60`; full sector-selection logic not established |
| `0x080A4044` | Initial six-slot party loop: skip species zero; for species >1499 with nonnull form pointer, write base species and recomputed form index, then recalculate stats |
| `0x080A18E8` | Base helper: form pointer indexed by input species; null returns input; otherwise returns entry zero |
| `0x080A18A0` | Index helper: find input species in its own form list, stopping at `0xFFFF`; null gives zero |
| `0x0803014A` | Direct call to `0x080A4044` inside a conditional startup/menu branch; guards are not fully resolved |

The initial conversion branch reads stored species, not the prior form field. For
1560 its form list starts with 52 and has 1560 at index 1: output is 52/1. It writes
selectors 11 and 89 through `0x0809B57C` and calls `0x08099674` for stats. Thus it
**recomputes**, rather than universally preserves, the form field; the observed
form happens to remain 1. This is **STATIC_VERIFIED**, corroborated by the observed
endpoints, but execution of this specific branch remains **NOT_VERIFIED**.

The conversion branch itself has no mode check, but its caller is conditional and
later branches in the same routine perform additional transformations. The helper
has no species bounds check; the index search does not reject a missing species
before returning the sentinel index. Neither helper provides a safe invalid-form
contract. Research tooling continues to refuse unsupported table/name/form inputs.
A null form pointer skips this initial branch. Base IDs at or below 1499 skip it.
Form-zero static lookup remains distinct from nonzero-form identity.

The rest of `0x080A4044` includes a separate 21-by-30 boxed loop with a 52-byte stride
and box accessors; its existence is static evidence only. Box runtime behavior,
all-party-load execution, other modes, invalid inputs, every species and second
save/reload behavior remain **NOT_VERIFIED / NOT_RUN**. No broad normalization policy
or new serialized format is implemented.

## Identity contract

The original raw representations remain distinct: `1560/1` and `52/1`. Both resolve
through exact-ROM data to metadata key 1560, name Meowth and type IDs `[17,17]`.
PID, OTID and nickname fingerprints match, as do form 1, level 5, moves
`[252,45,10,44]`, HP 19/19 and the independent summary identity.

Before record SHA-256: `af20b2255c921973b4ad9c4229256058b67679f8e707a42e480284aeec5e84d7`.
After: `db78be9c01606da1caa7718662fad661a95c84052df5d784b07e196ddbb5a175`.
Only offsets **28 and 29** differ; all other 74 bytes match. The five-bit form field
spans the packed word but remains 1. Summary reopen had no byte differences.

`compare_records` requires complete records, preserves both raw identities, reports
all changed offsets, compares static names/types/keys and decoded identity fields,
and always leaves runtime confirmation **NOT_VERIFIED**. Independent UI confirmation
comes from the existing bound `rowe_form_observe.compare` evidence, never this static
comparison. Raw unchanged bytes, not only selected decoded fields, establish this
subject's record stability.

## Validation and remaining gate

Focused synthetic tests cover both representations, all record-byte differences
outside the identity word, stable identity fields, form zero, invalid lists and
indices, unknown IDs, name holes, wrong ROM, complete-record length, checksum
corruption, absent/ambiguous cartridge evidence, and no static-to-runtime promotion.
Existing form-comparator tests cover missing/contradictory UI and no name-only
confirmation; existing data tests cover exact-hash refusal and no global fallback.
All fixtures are invented. No real cartridge or record bytes are committed.

Local validation passed: `./ci.sh all`, `source-check`, `fixture-test` (73 replay
cases), `rowe-test` (422771 assertions), ROM-evidence discovery (48 tests), and
focused identity tests (6). ASan/UBSan passed with leak detection disabled; the
full sanitizer including LeakSanitizer then passed outside the ptrace sandbox.
The initial sandbox LSan failure is environmental, not a clean result.
Both shipping ABIs exclude diagnostic symbols. Exact-head hosted CI is recorded
in the PR handoff. The
canonical commands are `./ci.sh all`, `source-check`, `fixture-test`, `rowe-test`,
`rowe-sanitize`; also run ROM-evidence discovery, focused identity tests and
`git diff --check`. ASan/UBSan and LeakSanitizer outcomes are reported separately.
Shipping Android ABI exclusion is checked on the generated debug APK.

| Determination | Status |
|---|---|
| LIVE PRE-SAVE REPRESENTATION | VERIFIED |
| LIVE POST-SAVE REPRESENTATION | VERIFIED |
| SERIALIZED CARTRIDGE REPRESENTATION | VERIFIED |
| FRESH-LOAD REPRESENTATION | VERIFIED |
| CONVERSION BOUNDARY | PARTIAL |
| IDENTITY STABILITY | PASS |
| NORMALIZATION SCOPE | STATIC_ONLY |
| PRODUCTION COMPATIBILITY | HOLD |
| ISSUE #59 | OPEN |

Remaining #59 gates include exact executed-path attribution, caller/configuration
semantics and broader compatibility evidence; production activation remains out of
scope. Recommended next bounded task: trace execution of `0x0803014A` and the initial
party branch at `0x080A4044` during startup from a copy of this same cartridge, recording
before/after selected-record hashes without writes. Do not acquire another subject
or infer a universal post-reload representation.
