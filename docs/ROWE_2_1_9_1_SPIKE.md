# R.O.W.E. 2.1.9.1 compatibility spike — evidence revision 1

Audit date: **2026-10-12 (America/Phoenix)**. Related: [#59](https://github.com/Sonoran-Solutions/dualdex/issues/59),
[#152](https://github.com/Sonoran-Solutions/dualdex/issues/152).
DualDex baseline: `5148bf28dc08cdd0f093a9741f420f6f24ca313a`.

**Decision: continue evidence gathering; do not activate full integration.** The publisher's
patcher identifies the provisional candidate as **2.1.9.1 Experimental**, not “Expansion”.
The patch can be pinned, but an exact-release source tree/build configuration, independently
validated runtime layout, and resulting ROM identity have not been established here. The public
1.9.4 source is not a substitute. No R.O.W.E. game ID, profile, trusted hash, production decoder,
or memory-read permission is added. This spike is the expressly authorized exception to #59's
normal scheduling order; it does not change the remaining integration/release gates.

## 1. Evidence vocabulary and provenance

| Status | Meaning in this document |
|---|---|
| Inspected | Directly checked metadata, text, patch integrity, or DualDex code at the stated revision. Limited to that artifact. |
| Reported | Another project's observation, even when it describes a runtime capture. Not DualDex runtime verification. |
| Synthetic | Behavior of our own invented byte arrays under a stated hypothesis. Does not prove a game layout. |
| Unverified | Missing independent exact-release evidence. Never enables support. |

### Sources inspected

| ID | Pinned artifact / primary reference | Finding and limit |
|---|---|---|
| S1 | [Iamdivinefox/Rowe README](https://github.com/Iamdivinefox/Rowe/blob/e8c86a4a6119376e41771c3cbf99178633e63c58/README.md) at `e8c86a4a6119376e41771c3cbf99178633e63c58` | Identifies 1.9.4 Source Edition. GitHub reports a standalone repository, not a fork relationship. Its name is not proof of publisher authority or later-release provenance. |
| S2 | [BelialClover/RoweSource README](https://github.com/BelialClover/RoweSource/blob/292eab691310e8b0cdb91a3905c34105261e24e1/README.md) at `292eab691310e8b0cdb91a3905c34105261e24e1` | Also identifies 1.9.4 Source Edition. This is the repository linked by the creator in S3. README and `include/pokemon.h` are byte-identical to S1; this comparison does not establish whole-tree identity. |
| S3 | [Creator rioluwott's announcement, 2024-01-22, post #1,126](https://www.pokecommunity.com/threads/pokemon-r-o-w-e-2-0-an-open-world-version-of-pok%C3%A9mon-emerald-gen-9-16-badges-sevii-islands-following-pok%C3%A9mon-costumes-and-much-more.442592/page-57) | Releases the old source via S2 and explains that 2.0 returns to closed source and changes save compatibility. Historical primary evidence for the version boundary, not proof that no later source could exist elsewhere. |
| S4 | [Publisher patcher index](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/index.html#L66-L76) at `8c38977519f0a3c1726bf75567f718fee5581cca` | Active patch configuration names `Pokemon ROWE v2.1.9.1 - Experimental`, selecting `patches/pkmn_rowe_experimental.ups`. S1/S2 link this publisher's patcher. The separate direct-download button points to `pkmn_rowe.bps`; do not confuse the two artifacts. |
| S5 | [Exact experimental patch](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/patches/pkmn_rowe_experimental.ups) | Downloaded into memory for hash/footer inspection only. No patch or derived ROM is committed; no game was run. Measurements below. |
| S6 | [PokeDaisey historical native config](https://github.com/lidor30/pokedaisey/blob/957fa674f6b857a19e55fbbd3f9b8224614cc83b/app/src/main/kotlin/com/pokedaisey/app/companion/data/NativeReader.kt#L689-L694) | The revision named in #152 still aliases R.O.W.E. to Emerald with a warning that the addresses are guesses. The issue's custom-layout observations are an additional research report, not a working decoder proven by that historical config. |
| S7 | [Newer PokeDaisey layout research](https://github.com/lidor30/pokedaisey/blob/e99fcb43b05cf8be4a33f5b74fadf7b0bde0651d/app/src/main/kotlin/com/pokedaisy/app/companion/data/Gen3Mon.kt#L73-L84) and [address research](https://github.com/lidor30/pokedaisey/blob/e99fcb43b05cf8be4a33f5b74fadf7b0bde0651d/app/src/main/kotlin/com/pokedaisy/app/companion/data/NativeReader.kt#L1011-L1077) | Pinned at `e99fcb43b05cf8be4a33f5b74fadf7b0bde0651d` (commit dated 2026-10-11 UTC). Reports a 76-byte plaintext packed layout, field accessors inspected in the ROM, and an embedded symbol table. This is newer than #152's cited revision and contradicts its level/stats offsets. Facts are cited; GPL implementation and generated tables are not copied. |
| S8 | [Party capture description](https://github.com/lidor30/pokedaisey/blob/e99fcb43b05cf8be4a33f5b74fadf7b0bde0651d/app/src/test/resources/fixtures/rowe/README.txt), [battle capture description](https://github.com/lidor30/pokedaisey/blob/e99fcb43b05cf8be4a33f5b74fadf7b0bde0651d/app/src/test/resources/fixtures/rowe_battle/README.txt), [test assertions](https://github.com/lidor30/pokedaisey/blob/e99fcb43b05cf8be4a33f5b74fadf7b0bde0651d/app/src/test/kotlin/com/pokedaisy/app/companion/data/RoweDecodeTest.kt) | Descriptions dated 2026-10-09 call the game Experimental and give only SHA-1 prefix `81bd0f4b...`. They describe one occupied player slot and a scripted wild battle. Read text/assertions only; no captured RAM or ROM tables imported or replayed. The prefix is not enough to bind these observations to S5. |

GitHub API branch/tag/release and recursive-tree inventories were checked for S1/S2: each had
only `main`, no tags/releases, and no `.elf`, `.map`, or `.sym` output. Their `sym_ewram.txt`,
`sym_common.txt`, `sym_bss.txt` and linker scripts are source/build inputs, not an exact 2.1.9.1
compiled symbol map. S4's tags belong to the patcher repository; they do not identify the game's
source release. Web searches and the above linked repositories did not supply an exact 2.1.9.1
source commit, compiler configuration, or publisher ELF. This is a bounded search result, not a
claim that private or undiscovered artifacts do not exist.

Other publisher repositories were checked to avoid mistaking an older similarly named tree
for this release: [PokemonROWE at `cdbb429650cf516484ddbad25b229a1f2f734ed0`](https://github.com/BelialClover/PokemonROWE/tree/cdbb429650cf516484ddbad25b229a1f2f734ed0)
has release tags `1.0.7.2` / `1.0.5`, while [pokeemerald_rowe at `752638c80d7157cf380520f605d8e617cbeaa218`](https://github.com/BelialClover/pokeemerald_rowe/tree/752638c80d7157cf380520f605d8e617cbeaa218)
has no releases and documents the vanilla Emerald build. Neither supplies a 2.1.9.1 pin.
The [publisher's companion Lua file at S4](https://github.com/BelialClover/Rowe-Patcher/blob/8c38977519f0a3c1726bf75567f718fee5581cca/files/ROWE_Companion.lua)
was also inspected: it implements cry/gift/outbreak/password communication through scratch
addresses and labels the companion itself 1.2. It is not an exact-release party ABI declaration.
The Lua and debug companion were not executed or copied into this project.

### Patch measurements (S5)

| Property | Inspected value |
|---|---|
| Artifact bytes / format | `30,599,197` / `UPS1` |
| Patch SHA-256 | `41431249ec06033e77950603be22091bd0ea465dbf573a645eec642d397d26f9` |
| UPS input / output size | `16,777,216` / `33,554,432` bytes |
| UPS source / destination CRC32 | `1f1c08fb` / `ad252fd3` |
| UPS patch CRC32 | Stored `e31147fe`; recomputed over all but the final four bytes: `e31147fe` |
| Resulting ROM SHA-256 / SHA-1 | **Not derived or verified** |

These identify the publisher-hosted patch, not a trusted ROM. The patcher's configured base CRC
is `d5ec24e4`, differing from the UPS source CRC; record and resolve this discrepancy using the
actual legally obtained base and UPS validation before constructing a runtime target. Do not
override the footer check or infer a ROM hash from a filename, patch hash, or partial SHA-1.
The “Expansion” label and a pokeemerald-expansion ABI are not independently established.

## 2. Versioned party evidence matrix

**Hypothesis ID: `rowe-2.1.9.1-experimental-reported-v1`.** All addresses below are absolute GBA
addresses; `+` offsets and bit positions are relative to one party slot. Multi-byte values are
reported little-endian. None of the rows is DualDex exact-release runtime verified.

| Field | #152 / old-source claim | Newer reported candidate (S7/S8) | Disposition / required validation |
|---|---|---|---|
| Player party address | #152: stable slot 0 `0x02025128` | Same; S7 distinguishes SaveBlock1's `+0x238` saved copy | Reported agreement, **unverified**. Repeat boots/load timing, menus, party mutation; prove live global vs drifting save copy. |
| Player count | Not established in #152 | `gPlayerPartyCount` at `0x02025125`; assumed one-byte count | **Unverified**. Independently identify named symbol and width; exercise counts 0–6. Do not infer address as party-minus-three. |
| Slot stride / size | No proven stride; native reader assumes 100 bytes | `0x4C` = 76 bytes; enemy start reported `0x020252F0`, 6 strides after player | **Unverified**. One-mon fixture does not establish all six slots. Confirm accessor indexing and distinct slots/reordering. Adjacency is corroboration, not proof. |
| PID | #152's “PID/OTID +0” is ambiguous shorthand | u32 `+0x00` personality | **Unverified**. Check independently chosen identities. Zero is not intrinsically invalid. Do not derive nature/shiny from it. |
| OTID | No separately verified offset | u32 `+0x04` trainer identity; declaration in S7's layout comment | **Unverified**, weaker than tested PID. Validate full width and TID/SID semantics, including zero. |
| Species ID | #152: u16 National Dex at `+0x1C` | Low **12 bits** of u32 `+0x1C`; form in bits 12–16 | **Unverified**. Raw u16 would mix form bits. Preserve ID/form separately; National Dex equivalence, forms and custom IDs need exact data. |
| Nickname | #152: `+0x08` | 12 bytes `+0x08..+0x13` | **Unverified** length/encoding/control codes. Prototype retains raw bytes and offers a bounded Gen III preview only. Full-length and terminated synthetic names tested. |
| OT name | #152: `+0x2B` | No independently confirmed width/encoding in this audit | **Unverified**, omitted from prototype. Do not borrow the seven-byte Gen III field or confuse it with OTID. |
| Level | #152: `+0x44` | u8 `+0x3C` | **Conflicting, unverified**. S7's newer hypothesis used by prototype. No consensus or runtime-proof claim. |
| Current / max HP | #152: HP/stats starting `+0x46` | u16 `+0x3E` / `+0x40` | **Conflicting, unverified**. In a 76-byte slot, #152's implied full stat tail would overrun. Validate damage, healing and fainting against UI and snapshots. |
| Atk / Def / Spe / SpA / SpD | #152 implies tail after `+0x46` HP | u16 `+0x42`, `+0x44`, `+0x46`, `+0x48`, `+0x4A` | **Conflicting, unverified**. Distinct synthetic values test ordering; game summary/battle stats still needed. |
| Move 1 | “bit-packed near +0x14” | u32 `+0x14`, bits 0–9; shares word with reported experience | **Unverified**. Synthetic upper-bit sentinels demonstrate masking only. |
| Moves 2 / 3 | Not specified | u32 `+0x18`, bits 0–9 / 10–19 | **Unverified**. Validate each slot independently, move learning/reordering/deletion and high IDs. |
| Move 4 | Not specified | u32 `+0x1C`, bits 17–26 (u16 `+0x1E`, bits 1–10) | **Unverified**. Shares word with species/form; synthetic cross-byte isolation checked. |
| PP | #152: bytes `+0x34` | Four full bytes `+0x34..+0x37` | Reported agreement, **unverified** semantics/maxima. Preserve 8 bits; do not apply H&S's 7-bit mask. Validate use, zero PP, restoration and PP Ups. |
| Encryption / checksum | #152 says unencrypted custom layout | S7 reports no Gen III secure substructures/checksum | **Unverified on our target**, but sufficient reason to reject Gen III parser reuse. Plausibility cannot provide integrity or identity. |
| Enemy party / count | Not established | Party `0x020252F0`; authoritative enemy count not established by this audit | **Unverified**; do not derive count from player count, adjacency, active battlers or a species scan. |

S7 additionally reports an embedded table at `0x08FB0670`, 150 `{name pointer, address}` entries.
This could provide release-specific symbol leads without a source release. It was **not read
from an independently identified ROM here**. Validate table bounds, names, addresses, accessor
references, and observed runtime behavior before relying on it. Symbol addresses alone cannot
establish packed field widths or the coherence of a party snapshot.

The old [S1 `include/pokemon.h`](https://github.com/Iamdivinefox/Rowe/blob/e8c86a4a6119376e41771c3cbf99178633e63c58/include/pokemon.h#L96-L133)
instead declares a BoxPokemon header with checksum and secure substructures, followed by the
traditional party tail. Its [pokemon implementation](https://github.com/Iamdivinefox/Rowe/blob/e8c86a4a6119376e41771c3cbf99178633e63c58/src/pokemon.c)
contains EncryptBoxMon/DecryptBoxMon and declares the party globals. These are inspected
**1.9.4 source facts**, not compiled ABI or 2.1.9.1 field evidence. Compiling them would answer
the wrong release question.

## 3. Native flow and decoder boundary

The audited production path is `RomHackDetector` / `RomCompatibility` / `RuntimeRomTrust` →
emulator/JNI (`app/src/main/cpp/dualdex_jni.c`) → `pokemon_get_game_config(gameId)` →
`pokemon_read_player_party[_gba]` or `pokemon_read_enemy_party[_gba]` → `parse_party_mon` →
`pokemon_parse_single_layout`. Relevant source: [reader](../native/src/pokemon_reader.c),
[structs](../native/include/pokemon_structs.h), [config](../native/include/pokemon_reader.h).

Both `PKMN_STORAGE_VANILLA_GEN3` and `PKMN_STORAGE_EXPANSION` use the 100-byte RawGbaPokemon:
10-byte nickname, checksum at `+0x1C`, 48-byte secure area at `+0x20`, level at `+0x54`,
HP at `+0x56`. Expansion changes bit interpretations *within* that container. The parser's
plaintext/CFRU fallbacks still depend on that container and GAEM substructures. They cannot
decode the reported R.O.W.E. representation by disabling XOR or changing a mask.

The authoritative party loops, heuristic/cache scans and enemy discovery also advance and
bound by `sizeof(RawGbaPokemon)`; some probes use hard-coded 100-byte reads. The core parser has
no input-length parameter. Passing a 76-byte slot risks reading beyond it; padding it to 100
would merely manufacture data. Production also caps party species below 2000 and derives
nature/shiny/ability semantics that are not transferable (S8 specifically reports a nature
different from PID modulo 25).

**A separate slot decoder is required if this hypothesis holds.** Keep only bounded pieces:
the existing text codec as a provisional preview, memory-region bounds checks, and the
authoritative-count/all-or-nothing policy. A future narrow dispatch must choose both decoder
and stride from an exact verified layout before any dereference; it must not add R.O.W.E. to
the checksum scanner. Audit both JNI entry paths, player/enemy loops, battle overlays and
synthetic/runtime tools at that time. This spike changes none of them.

### Host-only experiment

[decoder_selftest.c](../tools/rowe-layout-spike/decoder_selftest.c) contains one private decoder
and its executable self-check. It takes exactly one caller-owned 76-byte immutable buffer;
there is no ROM loader, address reader, count discovery, network input, production enum or
`ParsedPokemon` validity output. It returns candidate fields under the S7 hypothesis and reuses
DualDex's `pokemon_decode_string`. The byte/bit extraction is independently written; no GPL
algorithm, source fragment, script, generated table or captured fixture is vendored.

Synthetic checks cover packed move/species/form neighbours, IDs above the existing 11-bit
mask, raw 8-bit PP, PID/OTID, all five distinct stats, fainted HP, zero identity words, full-width
and terminated names, and unaligned input. Failures include null pointers, every truncated
length, oversized/100-byte/SIZE_MAX lengths, empty species, zero/out-of-range level, impossible
HP, zero max HP/stats and the reported egg bit. Failed outputs are completely cleared.

The 1–100 level and positive-stat checks deliberately limit the experiment to ordinary
non-egg slots; they are not claims about every supported R.O.W.E. mode. Width-valid species,
forms, moves and PP are retained as raw candidate values, not asserted to exist in a verified
data pack. No ability, nature, shiny, IV, EV, item or status interpretation is offered. No
checksum is invented: a plausible wrong layout or an inconsistent live snapshot can pass.
Synthetic success never upgrades the evidence status in the matrix.

Run through **`./ci.sh test`** or **`./ci.sh all`**. The native-test stage builds this host-only
C11 executable with warnings as errors and assertions enabled; Android CMake is unchanged.

## 4. H&S infrastructure reuse audit

“Small adapter” below describes the implementation boundary **after evidence exists**; it is
not an estimate that obtaining the missing evidence is small. The audit does not propose #8's
general profile-engine rewrite.

| Component | Classification | R.O.W.E. consequence |
|---|---|---|
| Exact-hash trust and unknown-build refusal (`RomHackDetector`, `RuntimeRomTrust`) | **Reusable** | Keep the gate unchanged. Patch provenance, source lineage and runtime validation must precede an eventual ROM SHA-256 allowlist entry. |
| `gba_memory_map.c`, bounded read callback, libretro memory descriptors | **Reusable** | Addresses must resolve through an actual published EWRAM/IWRAM/ROM region; a source symbol is not permission to read it. No extension now. |
| `pokemon_text.c` bounded codec | **Reusable with validation** | Reused by this experiment; exact R.O.W.E. glyphs/language/control codes remain unverified. Retain raw nickname bytes. |
| Gen III/H&S storage decoder, checksum scan, 100-byte stride | **Incompatible** | New 76-byte packed slot decoder if confirmed. Never weaken the old decoder's integrity tests to accept this representation. |
| Authoritative party count/snapshot policy | **Small adapter** | Preserve all-or-nothing and no-scan behavior, but pass verified stride/decoder and review ID/form limits. Need coherent reads without relying on a checksum. |
| H&S source provenance/digest checks | **Small adapter** | Reuse pin/dirty-tree/digest/regen discipline with a separate target manifest. There is no exact R.O.W.E. source pin to supply today. |
| `tools/hns-data-pack` extractor | **Incompatible as-is** | Expects the H&S commit, `gSpeciesInfo` and H&S source shapes. Inspected 1.9.4 uses `gBaseStats` / `data/battle_moves.h`; porting those would still target the wrong version. Exact-source extraction or independently validated local-binary extraction is needed. |
| `GameDataPack` lookup/authority/fallback contract and registry | **Small adapter** | Separate versioned pack, `allowGlobalFallback=false`, exact ID/form lookup. Do not import PokeDaisey generated tables or equate low species bits to modern dex entries. |
| ARM ABI constant probes / bitfield byte probes (`tools/hns-layout`) | **Small adapter, currently blocked** | Method is reusable with exact headers, target flags and compiler. H&S `-DPOKEMON_HNS`, member lists and generated values are incompatible. No 1.9.4 result may be labeled 2.1.9.1 ABI. |
| H&S generated layout headers, battle menu/controller addresses, JNI tuples | **Incompatible** | Every field/width/lifecycle mapping needs a R.O.W.E. contract. S7's reported battle symbols are discovery leads only. |
| Battle lifecycle / active-slot authority and unknown/ambiguous policy | **Small adapter** | Retain lifecycle and battler-to-slot principles; prove R.O.W.E. counters, positions, absent flags, party indexes, outcome/teardown, singles/doubles/multi independently. |
| H&S runtime probe harness | **Small adapter** | Reuse libretro stepping/capture plumbing and failure-state methodology; current harness selects `GAME_HEART_AND_SOUL` and embeds H&S scenarios. Do not run it unchanged and label output R.O.W.E. evidence. |
| H&S challenge settings / runtime rules / move/ability/item decision tables | **Incompatible** | Audit R.O.W.E. option semantics and effective runtime state separately (randomizers, difficulty/scaling, split/type modes, doubles, forms/gimmicks). H&S flags and capability allowlists prove nothing here. |
| Calculator QuickJS engine and explicit species/move override transport | **Reusable** | Existing transport can carry verified data; it does not establish matching mechanics. |
| Calculator capability policy and oracle/census infrastructure | **Small adapter** for refusal/reporting; H&S fixtures **incompatible** | Add a separate evidence-backed capability branch only after rules/data exist; no inherited H&S confidence, ordinary-damage goldens or source-built oracle. Without exact source, an equivalent oracle remains an open research problem. |
| Map generation/location bounds/fallback behavior | **Small adapter** for plumbing; H&S geography **incompatible** | Independently establish SaveBlock location/pointers and R.O.W.E. map/region IDs. Johto/H&S map tables and warp audits cannot be reused. |

### Tool experiments, not hypothetical portability claims

An isolated sparse checkout of S1 was used; no upstream source was placed in DualDex.

1. Invoking `tools/hns-data-pack/generate_hns_data_pack.py --upstream-dir <S1>` with a temporary
   output path stopped at missing `arm-none-eabi-cpp` in this environment. No extraction ran.
2. Invoking its existing `verify_git_commit(<S1>)` directly failed as expected: actual commit
   `e8c86a4...` does not match pinned H&S commit `1f42b74...`. No guard was disabled.
3. Invoking `tools/hns-layout/generate_hns_battle_pokemon_layout.py --upstream-dir <S1>` with a
   temporary output path (required headers present) rejected the same commit mismatch before
   compilation. No generated file was produced.

These prove the existing tools fail closed on this wrong-release source, **not** that the
extractor/probes successfully ported. No compiler substitution, fake source pin, H&S output
regeneration, or old-source game build was performed.

## 5. Concrete remaining #59 plan and gates

| Order / #59 area | Work product | Exit condition |
|---|---|---|
| 1 — Target/source identity | Confirm Experimental is the desired candidate; retain S4/S5 pin. Ask publisher for exact source/build configuration or release ELF/map and licensing. Independently patch a legally obtained base, validate all UPS checks and record full resulting SHA-256 plus header/size. | Proven patch-to-ROM lineage; resolve configured-base CRC discrepancy. Bind any third-party evidence to the full same ROM identity. No trust registration yet. |
| 2 — Memory evidence | Independently inspect the reported embedded symbol table and field accessor/indexing code; record symbol/ABI facts separately. Capture 3–5 varied boot timings plus save/load, player counts 0–6, six distinct slots, reorder/deposit/withdraw/evolution, nickname lengths, high species/form/move IDs, PP changes, damage/heal/faint/egg. | Matrix rows replaced with reproducible exact-release observations and contradictions resolved. Immutable snapshots or repeated-read consistency must reject mutation windows; retain unknowns explicitly. Keep ROM/save/full RAM private, commit only lawful minimal evidence summaries and synthetic regressions. |
| 3 — Narrow native integration (C) | Implement dedicated bounded decoder and stride dispatch; retain known/unknown semantics for omitted fields. Update only proven player/enemy paths and ID/form handling. | Host regressions plus exact-release runtime match. No scan fallback or borrowed H&S fields. Missing authoritative enemy count keeps enemy features disabled. |
| 4 — Battle/runtime/maps (C/E/F) | Establish enemy count, lifecycle, battler mapping, switches/faints/replacement/teardown; independently audit doubles/multi. Inventory all mode/rule state and map/location contracts. | Each supported feature runtime proven; unsupported state fails closed, not a plausible slot-0/default-rule fallback. |
| 5 — Data/mechanics (D/E/G) | Produce a target-specific source manifest/data pack or a documented independent extraction path from the exact local binary. Resolve species+form, move/ability/item/types, effective ability/nature/stats, settings and rule changes. Build a separate capability matrix and narrowly justified golden cases. | No global fallback for authoritative claims; no calculator result in unsupported modes. Source-less mechanics remain explicitly unproven rather than “covered by expansion”. |
| 6 — Trust/review (B/H) | Only after runtime gates, propose the exact ROM hash and per-feature permissions in a separately reviewable PR; leave all other hashes unverified. Run `./ci.sh all`, runtime replay and existing FireRed/Emerald/H&S regressions. Record actual seam changes before Unbound. | Human-reviewed exact-build support. No merge or activation as part of this draft spike. |

If exact source remains unavailable, the H&S source-built oracle and compiled ABI route cannot
be claimed for this release. Re-scope later work to independently validated features or select
another candidate; keep full calculator/live integration blocked until the claimed evidence
standard can be met. The useful result here is the narrower boundary: shared trust, bounded
memory and data contracts, with a distinct storage representation and release-specific rules.

## 6. Validation record

**`./ci.sh all` passed (exit 0)** on this spike branch: 100 native reader tests, the new
R.O.W.E. hypothesis self-check, 93 H&S tracker checks, 7,423 QuickJS calculator assertions,
the required oracle/generator/tooling suites, and 1,395 Kotlin tests across 105 classes
(zero failures/errors/skips). Debug `assembleDebug` succeeded. `git diff --check` also passed.
No R.O.W.E. runtime, hardware, source-build reproduction or production support verification
is claimed.
