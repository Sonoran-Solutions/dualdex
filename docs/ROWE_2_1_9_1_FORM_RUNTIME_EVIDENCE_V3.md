# R.O.W.E. 2.1.9.1 Experimental — fresh-game form evidence, revision 3

Phase 6D, 2026-10-14 (America/Phoenix). Tracking [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59) remains open; production compatibility remains **HOLD**.
Started from fetched main `02cdd92a87af741f30180758c2ce9e2d140091f3` (#197).
The preceding [Phase 6C result](https://github.com/Sonoran-Solutions/dualdex/issues/59#issuecomment-6184915449) was an issue-only reachability update, not another merged PR.
Historical [revision 1](ROWE_2_1_9_1_FORM_RUNTIME_EVIDENCE.md) and [revision 2](ROWE_2_1_9_1_FORM_RUNTIME_EVIDENCE_V2.md) are unchanged.

A legitimate fresh game reached Two Island, reloaded its cartridge save, walked to Cape Brink, and caught a nonzero-form Meowth on the second visible encounter. Its independently inspected summary matches the exact-release resolver before and after a fresh-process cartridge reload. **The stored species representation changes across that save/reload boundary: `1560/1` becomes `52/1`.** Both resolve to metadata key 1560. This is scoped runtime evidence, not production support or a general normalization rule.

## Results

| Required status | Result |
|---|---|
| NEW-GAME TARGET START | VERIFIED |
| FRESH CHECKPOINT | CREATED |
| FRESH-PROCESS RELOAD | PASS |
| TARGET MAP ACCESS | VERIFIED |
| ACTIVE ENCOUNTER ROSTER | VERIFIED |
| NONZERO FORM ACQUIRED | YES |
| RUNTIME FORM IDENTITY | RUNTIME_CONFIRMED |
| MODE SEMANTICS | PARTIAL |
| PERSISTENCE | PASS — form identity; raw species word changes |
| PRODUCTION COMPATIBILITY | HOLD |
| ISSUE #59 | OPEN |

The versioned [sanitized manifest](../tools/rom-evidence/evidence/form-runtime-v3.json) contains exact identities, source/tool hashes, all input commands, screen/region/record digests, selected configuration, comparison results and private artifact hashes. No ROM bytes, raw memory, cartridge saves, states or screenshots are included. All diagnostic reads remained player-only; gameplay used ordinary buttons. No historical owner checkpoint was loaded or changed.

## Fresh setup and reproducible route

Use the authorized exact local ROM with the pinned core and adapter below, in a new private directory outside Git. Start a fresh emulator process with no save or state. The initial menu must show New Game, not Continue.

1. Complete the normal introduction; this run used male trainer **A**. In the setup room, speak to the NPC. Select **Normal** difficulty and confirm it.
2. Inspect Change Game Modes and Other Modes; enable none. Choose Save, then Yes. Choose Start Game and confirm. Select **Spring**, accept **10:00 AM**, choose **Chinchou** (level 10), and accept the ordinary starter items.
3. Scroll the starting-location list. **Two Island is offered without an unlock or optional mode.** Select it and confirm. The observed list includes the Hoenn towns/cities, One through Eight Island, and Surprise Me; it does not offer Pallet Town/Kanto. Other mode configurations were not tested.
4. Spawn outside the Two Island Pokémon Center. Player map reads **0/76**, coordinates **21,8**. Open the menu with Start, wait for it to finish opening, then Start to save. Wait for **“A saved the game.”** The save panel independently identifies Two Island, trainer A, zero badges, and version 2.1.9.1.
5. Flush the completed cartridge save, close the process, create another process and load only that save. Continue returns to 0/76 at 21,8. The complete 456-byte player region matches the first process; Chinchou remains `170/0`, level 10, 35/35 HP.
6. Walk east around the center and market fences to the northern stairs, then north. Actual transition reaches **Cape Brink, 1/6**, at **12,38**. No ferry, taxi, BP grind or progression unlock was needed. A completed in-game Save independently names Cape Brink.
7. Reach the grass along the west side of the pond. This run briefly entered the water using the supplied ordinary traversal mechanics, returned west to land, and walked north into grass. Evaluate the exact-ROM roster from the observed map/time/flag before acquisition.
8. Walk in grass. Encounter 1: Bellsprout, level 5; fled. Encounter 2: gray regional Meowth, level 5; use Inventory → Poké Balls → Poké Ball → Use. Ten balls were available; the first throw caught it at full HP. Decline a nickname. Stop sampling after this successful subject: **2 visible encounters**, below the approximately 20 limit.
9. Open Pokémon → second slot → Summary. Independently transcribe the visible identity and distinguishing cue, bind it to the paused screen and complete record digests, then run the existing comparator. Close and reopen the summary, repeat the comparison, perform an actual in-game save, exit, and repeat in a fresh process loaded from that cartridge save only.

The manifest's `inputs` retains the exact frame/button sequence for each of the three processes. `run N mask` holds a button for N frames; `mash N mask` repeats one pressed frame followed by 90 released frames; default mash is A. Masks: A 256, B 1, Start 8, Up 16, Down 32, Left 64, Right 128. `observe` pauses and captures through the existing observer; `state` is output-only; `cartridge_save` exports current SRAM after the game's Save. These are private-driver commands, not an unmodified observer-CLI movie. RTC and encounter randomness can change on another run: use visible menus and map checks rather than assuming identical encounters from a frame replay.

One premature export in process 2 still contained the Two Island save. It was retained separately and excluded as a Cape checkpoint. The corrected Cape export followed a visibly completed Save; no prior artifact was discarded. The final capture export likewise followed the game's completed Save.

## Active roster and configuration

At Cape Brink, the observed player map is 1/6, time byte at `0x03004A5A` is 10, and flag `0x470` is clear. Applying the previously established exact-ROM selector at `0x08244FBC` yields **1**, choosing header field **+4**. Header index **154**, `0x08F28CB0`, has **both +4 and +8 = `0x08F26B5C`**; its land roster is `0x08F26B2C`. Selected entries:

| Entry | Address | Exact form-species ID |
|---|---|---|
| 2 | `0x08F26B34` | 1560, captured Meowth candidate |
| 6 | `0x08F26B44` | 1551, alternate Rattata candidate |
| 9 / 11 | `0x08F26B50` / `0x08F26B58` | 1582, alternate Zigzagoon candidate |

This verifies selection by evaluating the pinned dispatch against runtime inputs; it is **not** an executed-PC trace. Both land branches have identical data. The generation-transform behavior at `0x08037E14` remains **NOT_VERIFIED**; one captured outcome does not establish its general behavior.

Normal difficulty and unchanged optional-mode selections were observed through the complete fresh setup. No optional randomization, type or form mode was selected. The bounded generation flags `0x28`, `0x30`, `0x3C`, `0x2B` remain clear; no unverified English flag names are assigned. The comparator's `mode_verified=true` means this selected configuration has direct provenance. Overall MODE SEMANTICS remains **PARTIAL**: alternate configurations and general effective-form/level accessors are not established. Stored and displayed level agree here, so no level-override explanation is needed.

## Captured record and independent UI

Reads use the existing coherent player-only protocol: count, complete 456-byte region, count, repeated complete region, final count; require all counts and both regions to agree. Party base `0x02025128`, count `0x02025125`, stride 76, capacity six. The captured subject occupies slot index **1**, with player count **2**. The standalone C raw decoder independently agrees with the Python observer for both pre-save and reloaded records.

| Observation | Stored species/form | Static species / metadata key | Level | HP | Result |
|---|---|---|---|---|---|
| First summary | 1560 / 1 | 1560 / 1560 | 5 | 19/19 | RUNTIME_CONFIRMED |
| Summary reopen | 1560 / 1 | 1560 / 1560 | 5 | 19/19 | RUNTIME_CONFIRMED |
| Cartridge reload summary | 52 / 1 | 1560 / 1560 | 5 | 19/19 | RUNTIME_CONFIRMED |

All three summaries independently display **Meowth No052, male, Lv5**, a gray-blue regional portrait with dark ear tufts, half-lidded eyes and curled tail, and one **DARK** type badge. Pickup, Balanced Style, Docile, and Quick Claw also remain visible. Exact-ROM static name is Meowth; numeric type IDs are **[17,17]**. Stored move IDs are **[252,45,10,44]**. No generic Pokédex mapping substitutes for exact-release data.

The corrected `rowe_form_observe.compare` accepts these bound observations. Its cue assessment is an explicit independent visual assessment, not automatic sprite or type classification. The repeated shared name alone would not suffice. Comparison outputs retain `effective_form_status=NOT_VERIFIED` and restrict confirmation to this player record and observed configuration.

The full 76-byte record is identical on summary reopen. Across the save/fresh-process reload, only offsets **28–29**, the packed species/form word, differ; the other **74 bytes match**. PID, OTID and nickname fingerprints, form 1, level, HP and moves match. The source representation is `1560/1`, the reloaded representation `52/1`; both are supported by the unchanged exact-ROM static resolver. The exact normalization function and whether conversion occurs during save or load are **NOT_VERIFIED**. Do not rewrite either observation to force a single stored representation. Party reorder and boxing were **NOT_RUN**.

## Identity and retained checkpoint hashes

ROM: **33,554,432 bytes**; SHA-1 `81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77`; SHA-256 `fdf6a69e9556efd4b7ff96829c0fba85072e6d91c3ec96faed744765c4a772d3`.

Core SHA-256 `fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`; attributed source `e31759b24e7a4e3899285ff720d7b573ac328ae7`, reused without rebuild.
Adapter SHA-256 `aeefe0a47b5350e8c006d50eba5db2ef4227c23beb6449fc0b006ccdfc5f0c99`; pinned source revision `37eec9cba83f68115d8bd4d01c96bfaf51c8a682`, build/source hashes verified before use.
Observer SHA-256 `992be2acc8a92909eba5db1ab4334ad5de1eafe90600702f8ca4069c0d671941`;
comparator SHA-256 `328644e4ac2d0b1f1e3c5ecc6b581f16efde297ad2f24db55d8b728ebd653070`.
Remaining exact tool, decoder, private-driver and bounded instruction/data-span hashes are in the manifest.

| Private artifact | SHA-256 |
|---|---|
| Two Island saved checkpoint state | `a7b0fcaf5a61ae9524b0a5d5da04aa85d86fc533805cd26eb2336e81120a13ac` |
| Two Island cartridge | `187705c4d1a930655fa2e18a2b1870134e62b5fd8260957c5d013142a405aa1e` |
| Completed Cape Brink checkpoint state | `5f54fd36f379e8ac0421bc056ed5ee08d68540989ee0c6f00fa18c00c8654364` |
| Cape Brink cartridge before capture | `7ec171e34648b983c7b293eabddc9a572ed7a253b22ef906c2b74785c43860bc` |
| Captured-subject saved state | `6876613f49014d851f2b10da224dc52c94df22a47aaa07f73e66876a21501413` |
| Captured-subject cartridge, reloaded in process 3 | `e626ee3675b51b5e07b86983f44c5770789edf1a09d7105de49a3cdad0d62cb2` |
| First/reopened subject record | `af20b2255c921973b4ad9c4229256058b67679f8e707a42e480284aeec5e84d7` |
| Reloaded subject record | `db78be9c01606da1caa7718662fad661a95c84052df5d784b07e196ddbb5a175` |

Private artifacts remain under the isolated Phase 6D evidence directory outside Git. States are retained as evidence only; neither reload used one. No additional owner save or checkpoint is required for this route.

## Validation and remaining gate

Validation results are recorded in the PR handoff. The canonical test/build contract, focused fixture/raw-decoder checks, ROM-evidence discovery, comparator regressions and evidence consistency check are run for this report. Shipping Android targets must continue to exclude `rowe_raw_decoder`; no production code or test behavior is changed by this evidence-only PR.

The smallest next step is human review of this scoped evidence and the observed representation change. Production trust/profile activation, JNI/native-reader integration, enemy-party reads, calculator work, alternate-mode semantics and generalized persistence remain unsupported. Keep the PR draft, auto-merge disabled, and issue #59 open.
