# R.O.W.E. 2.1.9.1 Experimental — form runtime evidence, revision 1

Audit: 2026-10-14 (America/Phoenix). Tracking [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59), which stays open.
Prerequisite [#195](https://github.com/Sonoran-Solutions/dualdex/pull/195) was confirmed merged
through GitHub metadata before work began. Starting main and merge commit:
`e7d028595f951ae3a1eb6adc3197d7bba68e93b7`; reviewed predecessor head:
`56526451fffff82c4f70acad98deb48f0c660764`. This branch starts directly from that main.

**Result: BLOCKED acquisition; nonzero-form runtime identity remains NOT_VERIFIED.**
Three ordinary Route 101 encounters did not produce an eligible target. No nonzero-form player
record was obtained. There is no supporting or contradictory gameplay result for the proposed
19/form 1 → 1551 mapping. Static resolution must not be upgraded on that basis. Production
R.O.W.E. compatibility, trusted hashes, Android/JNI, native production readers, calculator,
enemy support and historical #191/#192/#193/#195 evidence are unchanged.

## Feasibility assessment and bounded acquisition

The initial candidate was stored species **19/form 1**, statically resolved to **1551**, name
candidate **Rattata**, base-stat key **1551**, type bytes **[17, 0]**. Form zero instead resolves
to 19 with **[0, 0]**. Both name rows read “Rattata”; name equality cannot distinguish them.
A distinct summary sprite plus independently visible type labels would be the minimum useful
UI evidence. Type-label correspondence to numeric IDs must be established or explicitly assessed;
this investigation does not import a vanilla/H&S type table.

The available authorized checkpoint was the disposable trainer A's ordinary-gameplay two-member
Route 101 checkpoint from #192, not a private nonzero-form checkpoint. Intended route: walk the
nearby grass normally, inspect encounters, acquire an eligible form with ordinary battle/ball
controls if one appears, and inspect its summary. This was a feasibility probe, **not an
established exact-release Rattata encounter location**. A secondary static candidate was stored
263/form 1 → 1582, name “Zigzagoon”, type bytes [17, 0]; it was not encountered either.

The publisher's [Experimental release announcement](https://www.reddit.com/r/PokemonROMhacks/comments/1uvsile/pokemon_rowe_update_219_experimental/)
describes configurable modes; it does not establish this checkpoint's encounter roster. Public
search leads and the older locally retained FAQ supplied no independently verified exact-release
acquisition route. No third-party encounter table or generated species table was used as expected
runtime identity. Full mode configuration of the inherited checkpoint is **NOT_VERIFIED**.
The field initially appeared night-tinted; that is a visual observation, not a decoded clock/mode.

The bounded attempt stopped after three visible encounters:

| Observer frame / label | Independently read game display | Action/result |
|---|---|---|
| 945 / `encounter3` | Oddish ♂, level 5, 19/19 HP; ordinary Oddish sprite | Defeated through ordinary inputs; no capture. An attempted flee input sequence arrived before the action menu and did not flee. |
| 4170 / `encounter5` | Skwovet ♂, level 4, 19/19 HP; ordinary Skwovet sprite | Waited for the action menu, chose Run, observed “Got away safely!” |
| 6315 / `encounter7` | Patrat ♂, level 4, 17/17 HP; ordinary Patrat sprite | Stopped at encounter text; no capture attempted. |

The two player records retained stored form zero at every captured checkpoint. Opponent identity
above comes exclusively from the rendered UI; enemy RAM was never read. These are three encounters
in one bounded gameplay attempt, not three independent catches or three nonzero-form experiments.
Further random walking had no new evidence-based target route, so it stopped. The exact blocker
is absence of an eligible acquired player Pokémon and of a proven exact-release acquisition route,
not lack of ROM/core access. Ordinary legitimate acquisition remains possible but unverified here.

[`rowe-form-route101.movie`](../tools/rom-evidence/rowe-form-route101.movie) preserves the input
sequence from this checkpoint. Intermediate labels named `encounter1`, `encounter2`, etc. are
capture labels; only the three rows above were encounters. The original exploratory driver and
input log are retained privately with hashes; a separate process replays the movie with the checked
observer. This is not a fresh-boot-to-catch script and does not create a Pokémon or set form bits.

## Identity, authority and baseline

Exact ROM SHA-1: `81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77`.
SHA-256: `fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3`.
Both hashes and size 33,554,432 are required for comparison. No other release is admitted.
Core SHA-256: `fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`,
previously attributed to source `e31759b24e7a4e3899285ff720d7b573ac328ae7`; not rebuilt here.
The existing adapter was rebuilt with its existing script and reproduced SHA-256
`aeefe0a47b5350e8c006d50eba5db2ef4227c23beb6449fc0b006ccdfc5f0c99`.
Its source/build-input pin remains unchanged. Current observer/comparison/text/data hashes,
movie/cartridge/checkpoint hashes and private report bindings are in the sanitized
[`form-runtime-v1.json`](../tools/rom-evidence/evidence/form-runtime-v1.json).
Historical capture hashes were not rewritten to match the current observer.

The baseline ran in a new emulator process, loading a copy of the #192 experiment cartridge
through its hash-bound marker, then Continue, party roster and both Pokémon's info/stats/moves
pages using the existing `rowe-party-reload.movie`. It did not load an emulator state. Independent
UI inspection reproduced Fletchling level 5, NORMAL/FLYING, 12/19 HP, Peck/Growl/Tackle/Quick Attack;
and Sentret level 10, NORMAL, 19/27 HP, Foresight/Tackle/Defense Curl/Quick Attack.

| Baseline player slot | Stored species/form → static key/name/types | Complete 76-byte record SHA-256 |
|---|---|---|
| 0, Fletchling | 661/0 → 661 / Fletchling / [0, 2] | `4457cadca2069e0db66f924865ac4a09391984128fdd151ba94c2a38f6895630` |
| 1, Sentret | 161/0 → 161 / Sentret / [0, 0] | `d69a6c30253f1c377a70c8b7ab14ea1581816c692eb36f49da40b4e9465538d2` |

Count was 2 after Continue; all occupied slots, slot positions and six record hashes were retained
privately. Count before / after / final agreed. The 456-byte region at `0x02025128` was read twice
with no emulator frame running; count at `0x02025125` was independently read three times.
Only counted records are decoded at stride 76. The shared observer now performs the previously
missing final count recheck after the repeated read. A failed run clears every observation from
its report. `--player-only` avoids enemy-party reads. These changes do not alter old evidence.

The existing Python raw extractor parsed the expected fields. A small private host driver also
called the existing #193 C `rowe_raw_decode_party`, comparing full PID/OTID, stored species/form,
level, HP/maxHP and four moves against both occupied baseline slots. All agreed. Driver/source/output
hashes are retained in the manifest. Private nicknames and full identity words remain outside Git;
new committed comparisons retain their hashes. Neither baseline proves nonzero-form behavior.

## Effective-form boundary: exact-ROM facts and unresolved display path

The static inspection used GNU ARM objdump 13.2.Rel1 on the exact hashed ROM. No exact-release
source/ELF is available and no older-source implementation was copied. Bounded instruction span
hashes are in the manifest; raw instructions/disassembly stay private.

* Box accessor selector **89** dispatches via `0x0809B13C + 89*4` to `0x0809B344`.
  It reads the word at record +28 and extracts bits 12..16. This selected path returns the stored
  five-bit form directly; it has no conditional transformation before its common return.
* Species selector **11** dispatches to `0x0809B4D0`, extracting 12 bits at +28.
  Selector **65** starts at `0x0809B4BC` and can substitute 2300 before that path. That distinct
  accessor cannot be treated as the same raw-species contract; its UI applicability remains unverified.
* Resolver `0x080A1874..0x080A189E` narrows species to 16 bits, form argument to 8 bits, reads
  `0x085E700C + species*4`, returns stored species for a null pointer, otherwise returns the
  halfword at `list + form*2`. This leaf contains no mode read or form-list bounds/sentinel check.
  The research resolver adds bounded list/sentinel/name validation; it does not reproduce unsafe
  arbitrary game reads. A null list is a verified static identity branch, not proof a naturally
  stored nonzero form on that species has meaningful gameplay semantics.
* One exact player caller at `0x081BA81C..0x081BA890` reads a selected index, scales it by 76,
  adds player base `0x02025128`, calls party getter `0x0809AF5C` with selector 11, then 89,
  and calls the resolver at `0x081BA864` with those results. There is no intervening mode branch
  between these getter results and that resolver call. The consumer after it is not established
  here as the summary renderer. This proves a stored-pair caller exists, **not that every UI uses it**.
* The prior type-check accessor near `0x080A191E` branches after a call to `0x080E10BC` with
  argument 47 before selecting table paths. Its flag/mode semantics and actual display lookup
  path remain **NOT_VERIFIED**; no numeric flag meaning is inferred from ordinary values.

Thus raw stored form, table-resolved identity, effective runtime form, displayed form and metadata
lookup identity remain separate. A mode-free leaf does not establish mode-free callers or UI.
Tracing the actual summary/sprite/type consumers for a captured nonzero record is the remaining
runtime gate; this blocker report does not invent that missing result.

## Runtime evidence matrix

| Gate | Status | Evidence / ceiling |
|---|---|---|
| Exact-ROM candidate 19/1 → 1551, Rattata, [17,0] | STATIC_VERIFIED | Exact-ROM bounded table/name/base-stat reads; no runtime Rattata |
| Candidate 263/1 → 1582, Zigzagoon, [17,0] | STATIC_VERIFIED | Static secondary candidate only |
| Baseline Sentret/Fletchling form-zero summaries | RUNTIME_OBSERVED | Coherent paused player captures, independent UI, matching historical record digests |
| Three bounded ordinary encounters | RUNTIME_OBSERVED | UI identities above; all player forms zero; no opponent RAM |
| Acquisition of any nonzero-form player record | BLOCKED | No eligible target in the bounded attempt; no proven local target route |
| Nonzero stored pair vs actual form/types display | NOT_VERIFIED | No nonzero record/UI pair; no confirmation or contradiction claimed |
| Nonzero summary close/reopen | NOT_RUN | No eligible subject |
| Nonzero whole-record party reorder | NOT_RUN | No eligible subject; historical form-zero reorder is separate |
| Nonzero in-game save + fresh-process cartridge reload | NOT_RUN | Baseline reload succeeded only for the pre-existing form-zero cartridge |
| Nonzero boxing/count change | NOT_RUN | No eligible subject |
| All other forms, eggs, modes, effective metadata paths | NOT_VERIFIED | No generalization from the baseline or static leaves |
| Comparator/refusal and snapshot regressions | SYNTHETIC_TESTED | Invented bytes only; no gameplay proof |

`RUNTIME_CONFIRMED` requires coherent player data plus independently corroborating UI for this
specific record/mode. There are **zero** such nonzero examples in this report. The comparison tool
can preserve a future manual form-cue assessment; synthetic confirmation tests test that logic,
not an observed game result. Raw type IDs are reported beside manually read labels, without an
automatic global type-label fallback. Shared name equality alone yields `RUNTIME_OBSERVED` only.

## Reproduce and complete the capture

Keep all ROMs, states, cartridge data, captures, UI transcriptions and comparison outputs in a
private directory outside **any Git checkout**. This environment exposes an empty `/tmp/.git`
marker, so `/tmp` is unsuitable for guarded capture paths; this guard was preserved. The new private
experiment used `/home/dq/dualdex-private/phase6`. Existing personal saves are never overwritten.

Rebuild the pinned adapter and replay a copy of your compatible own-gameplay checkpoint:

```bash
./tools/vanilla-runtime-probe/build.sh
python3 tools/rom-evidence/rowe_observe.py \
  --rom /private/exact-rowe.gba --core /private/pinned-mgba.so \
  --initial-state /private/own-two-slot.state --player-only \
  --movie tools/rom-evidence/rowe-form-route101.movie \
  --output /private/route.json --screens /private/new-route-screens
```

The committed movie is a failed-route reproducer, not a promised form-acquisition method.
For baseline reproduction use the existing `rowe-party-reload.movie`, `--load-cartridge` and
`--cartridge-dir /private/copied-experiment-cartridge` instead of `--initial-state`.

For a decisive future observation, first legitimately obtain a distinguishable nonzero-form
Pokémon and retain that authorized checkpoint. Freeze a short movie opening its actual summary
and observing `form_info`; use the same observer with `--player-only`. Independently inspect the
private PPM and transcribe its name, level, type labels, distinctive sprite/form cue and selected
slot. Do not copy those observations from RAM. Bind the UI JSON to the observation's label,
`rgb565_sha256` and full selected `record_sha256`:

```json
{
  "source": "MANUAL_GAME_UI",
  "label": "form_info",
  "slot_index": 0,
  "rgb565_sha256": "<paused screen digest from report>",
  "record_sha256": "<selected complete record digest>",
  "species_name": "<actual species display, not nickname>",
  "level": 5,
  "type_labels": "<actual visible type labels>",
  "form_cue": "<independently observed distinguishing appearance/label/types>",
  "form_cue_matches_static": null,
  "party_summary_context": "<actual selected slot and summary page>",
  "mode": "<actual configuration and any unknowns>",
  "mode_verified": false,
  "process": "<unique capture process label>",
  "checkpoint": "<private source hash>",
  "observer": "<person inspecting the rendered UI>"
}
```

`null` means no corroborating assessment yet; it cannot confirm a form. Set the assessment to
`true` only after independently reconciling a distinctive cue with this exact-ROM static result;
set `false` for contradiction and retain it. This is a human evidence assertion, not an automatic
sprite classifier or proof that an arbitrary transcription is truthful. Set `mode_verified` to true only after recording the exact configuration; an unknown mode cannot confirm the result. Record limits before claiming confirmation; unknown modes stay unsupported.

```bash
python3 tools/rom-evidence/rowe_form_observe.py \
  --rom /private/exact-rowe.gba --report /private/observed.json \
  --party /private/new-screens/form_info_player.party --ui /private/ui.json \
  --label form_info --slot 0 --output /private/comparison.json
```

The comparator reuses the existing raw extractor and exact-ROM resolver, admits the pinned
core/adapter and current observer, verifies all counted slots and complete record/region digests,
refuses missing UI/provenance/invalid mappings and clears prior output on invalid observations.
A mismatch returns `CONTRADICTED` and a nonzero exit, preserving both channels. No fallback tables.
It cannot independently authenticate a human transcription or establish uncaptured effective form
semantics. Keep the full private report; publish only selected numerical facts, digests, statuses
and source identities after checking privacy.

Close/reopen the summary first; capture again without assuming it persisted. If practical, use
ordinary party Switch and compare **complete** record hashes/slot positions. For saving, perform
the game's actual Save action before `cartridge_save`, exit that process, then use
`--load-cartridge` in a new process without `--initial-state`. Boxing is a separate optional gate.
None of these operations should be marked successful until actually executed on the nonzero subject.

## Validation and next slice

Local validation passed:

* `./ci.sh all` — complete native/calculator/tooling/Kotlin contract and debug APK build.
* `HNS_UPSTREAM_DIR=/home/dq/Projects/upstream-hns/pokehns-expansion ./ci.sh source-check` —
  pinned source/bootstrap/generator/Kotlin checks, exit 0.
* `./ci.sh fixture-test` — 73 synthetic replay/rejection cases plus 422,771 raw-decoder assertions.
* `./ci.sh rowe-test` — 422,771 assertions.
* `ASAN_OPTIONS=detect_leaks=0 ./ci.sh rowe-sanitize` — ASan/UBSan passed.
  LeakSanitizer was **not run**; this environment has the previously documented ptrace limitation.
* Final ROM-evidence discovery — 40 tests passed; focused form suite — 6 tests passed.
* Exact-ROM `rowe_data.py` report regenerated byte-for-byte equal to the merged contract.
* Final comparator CLI accepted the baseline only as `RUNTIME_OBSERVED`, with mode unverified.
* `git diff --check` — passed. Both ABI unstripped `libdualdex_native.so` outputs have no
  `rowe_raw_` symbols; corresponding APK libraries equal the checked stripped build outputs.
  The shipping CMake target still excludes the diagnostic decoder.

`TMPDIR` was set to the private directory outside Git for the local test runs, preserving the
privacy guard rather than disabling it. Exact-head hosted CI is reported in the draft PR after
the final push; CI validates ROM-free tooling, never an uncaptured form.

The next slice should establish an evidence-based exact-release acquisition route or provide an
authorized ordinary-gameplay nonzero-form checkpoint, then capture one record plus its distinctive
summary UI and summary reopen. Investigate that UI's effective identity/metadata consumers only
as required by the actual result. Further decoder/production integration is not justified yet.
