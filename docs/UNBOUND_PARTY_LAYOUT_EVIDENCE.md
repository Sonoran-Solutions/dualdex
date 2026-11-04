# Unbound exact-candidate party layout evidence

Task `unbound-runtime`, issues [#60](https://github.com/Sonoran-Solutions/dualdex/issues/60)
and [#26](https://github.com/Sonoran-Solutions/dualdex/issues/26). Static inspection
2026-11-03 (attempt 1, base `d592f559e0eb97159513551fc05ea6a663532a9a`); runtime
observation 2026-11-03 (attempt 3, on top of `27da0d9`).

**Six party records from one owner save were OBSERVED at runtime and matched the
in-game summary screens.** This is layout evidence for one exact cartridge and one
save, not a support verdict: production profiles, hashes, readers, Kotlin, CI and the
shared README are unchanged. The release label is **NOT_VERIFIED** (the owner says
v2.1.1.1; no publisher patch exists on the host, and the `POKEMON FIRE`/`BPRE` header
does not identify a version). No ROM, save, state, RAM, screenshot, full record,
extracted table or instruction bytes are committed.

Evidence files:

* [static manifest](../tools/rom-evidence/evidence/unbound-runtime-static.json):
  input identities, adapter/source pins, selected instruction-span hashes (attempt 1).
* [runtime observation](../tools/rom-evidence/evidence/unbound-runtime-observation.json):
  input/tool/movie hashes, a frame timeline, and, per slot, the manual UI
  transcription, the selected raw fields, the production parser's output, screen
  hashes and the comparison results. Raw 600-byte party regions and PPM screens stay private
  under `/home/dq/dualdex-private/unbound/runtime-opus/` (mode 700, no Git ancestor).
* [route movie](../tools/rom-evidence/unbound-continue-summary.movie): boot, Continue,
  Start menu, Pokémon, Summary, all three pages for all six slots.

Labels: **VERIFIED** means a stated static operation or hash was checked;
**OBSERVED** means seen at runtime and/or on screen for the records named;
**NOT_VERIFIED** means the evidence is insufficient; **NOT_RUN** means the scenario
was not exercised. One save with six Pokémon cannot prove every value domain or mode.

## Identity and prerequisite results

| Input | Result |
|---|---|
| Candidate | **VERIFIED** 33,554,432 bytes, SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`; header `POKEMON FIRE` / `BPRE` |
| Thor battery | **VERIFIED** 131,072 bytes, SHA-256 `a561cab2c9f4ecd32997d34a6f6b82ca592ec71f28d8713a16c9950e797e5236`; loaded from a private copy and **accepted** by the host core |
| Thor quicksave | **VERIFIED** 528,448 bytes, SHA-256 `eb64974477c908ab5a46f52642047fd55db2ee02b7999c67191bff7615727482`; **OBSERVED accepted** by the pinned host core (private copy) |
| Pinned host core | **VERIFIED** SHA-256 `fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e` |
| Developer adapter | **VERIFIED** `probe_host.so` `aeefe0a47b5350e8c006d50eba5db2ef4227c23beb6449fc0b006ccdfc5f0c99`; all `adapter-pin.json` source inputs matched (checked by the observer each run) |
| Observer | SHA-256 `0c57a1bdc98666a808228c8b708cae04b686a4acf0d1e0c50a1745614b9c474a` (bound by both manifests and a unit test) |
| Exact release / publisher patch | **NOT_VERIFIED**; owner claim v2.1.1.1 recorded as a claim only |
| Issue #24 | Offline copy read as prior claims, not as authority. Fixed order and the zero checksum match this run. Its “BADC” name is wrong: the order is G/A/E/M. Its “IDs > 898” scope for the species map does not match either: internal 942 (Litten) is national 725 (see species row) |
| Public source | CFRU `b637a27898b14e25dd24d0f69a3e302f0069deb8`, DPE `cdfc053a56326a13dc5311b24488445e17536b7e` (local, approximate; not the exact build). Used only to name the observed raw IDs and the hidden-ability bit |

## Selected exact-ROM instruction evidence

Read-only bounded Thumb disassembly used local Capstone 5.0.7. No public CFRU/DPE
source was substituted for the exact cartridge. Function names
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

## Runtime observation (battery path)

Route: the committed movie run by `unbound_observe.py --battery-save`. It produced 26
observations from frame 600 to frame 4607. All 26 paused snapshots were coherent:
the count was read three times and the region twice. The player party digest was the
same at every observation, so menus did not change party RAM.

* At frame 600, during the title logo and **before** Continue, `gPlayerPartyCount` was
  already 6 and the party region was populated. The bytes were already equal to the
  SaveBlock1 copy. The Continue screen showed “Annie, 147:31, Pokédex 274, Badges 8”.
  After Continue, the overworld was Hidden Grotto.
* `gSaveBlock1Ptr`=`0x0202552C`, `gSaveBlock2Ptr`=`0x02024588` at every observation.
  With the vanilla FireRed hypothesis (count u8 at SB1+0x34, party at SB1+0x38), the
  copy count was 6 and all six 100-byte copies were byte-identical to `gPlayerParty`.
* Both enemy-count candidates (`0x02024028`, production Unbound config; `0x0202402A`,
  production FireRed config) read 0 out of battle. No enemy records were decoded.
* Stored checksum word (`+28`) was `0x0000` in all six records. Neither the raw
  16-bit sum of the 48 substructure bytes nor the PID^OTID-XOR sum equals it. Slots
  have `pid % 24` = 10, 6, 12, 16, 22, 15, so a personality permutation would have
  moved every field. Every UI value below matched the fixed G/A/E/M reading.

### Manual summary-screen transcription (all six slots)

The agent viewed the private PPMs, converted to PNG with a stdlib script. Screen
SHA-256 values (RGB565) and frames for every page are in the runtime manifest.
“Raw” is the fixed-GAEM word. Natures matched `pid % 25` for all six. The IDNo
(53340) matched the low 16 bits of the OT ID. Nicknames and the OT name “Annie”
matched the Gen III charset bytes.

| Slot | UI: nickname / No. / species (form) / item / ability | Raw species / item | Lv, HP, stats, EXP, PP | Production parser |
|---|---|---|---|---|
| 1 | Gary / 130 / Gyarados / Gyaradosite / Intimidate; shiny star | `0x0082` / `0x01C9` | all match | species 130, item 457; **is_shiny false** (shiny value 15) |
| 2 | Reyna / 230 / Kingdra / Choice Specs / Swift Swim; shiny star | `0x00E6` / `0x00B1` | all match | 230, 177; shiny true (value 5) |
| 3 | Toady / 186 / Politoed / Damp Rock / **Drizzle** | `0x00BA` / `0x00F1` | all match | 186, 241; **ability_slot 1** (IV-word bit31 = 1) |
| 4 | Trippy / **785** / Tapu Koko / Focus Sash / Electric Surge | **`0x03EA` (1002)** / `0x023A` | all match | **species 1002** |
| 5 | Frank / **089** / Muk, Poison/Dark (Alolan) / Black Sludge / Poison Touch | **`0x040B` (1035)** / `0x003A` | all match | **species 1035** |
| 6 | Litten / **725** / Litten / None / Blaze | **`0x03AE` (942)** / `0x0000` | all match | **species 942** |

Moves (UI name: raw word): slot 1 Waterfall `0x7F`, Dragon Dance `0x15D`, Crunch
`0xF2`, Protect `0xB6`. Slot 2 Muddy Water `0x14A`, **Draco Meteor `0x171`** (national 434),
Ice Beam `0x3A`, **Hurricane `0x19C`** (542). Slot 3 **Scald `0x1CB`** (503), Encore `0xE3`,
Icy Wind `0xC4`, Helping Hand `0x10E`. Slot 4 Thunderbolt `0x55`, **Volt Switch `0x1BB`**
(521), **Dazzling Gleam `0x1D2`** (605), Protect `0xB6`. Slot 5 Toxic `0x5C`, Crunch `0xF2`,
Acid Armor `0x97`, Poison Fang `0x131`. Slot 6 Flamethrower `0x35`, Thrash `0x25`,
**Flare Blitz `0x1B2` = 434** (national 394), Outrage `0xC8`. All observed IDs ≤ 354
equal the national number. All observed IDs above that are CFRU-internal and match the
public CFRU `MOVE_*` constants. Note that internal Flare Blitz (434) equals the
national number of Draco Meteor.

The skills page shows a letter grade per stat (S … E-). For all 36 stat
positions, the grades rank in the same order as the six 5-bit IVs at M+4 in
production bit order (HP, Atk, Def, Spe, SpA, SpD). Examples: Kingdra's Atk IV 0 is
`E-`; Tapu Koko's (31,14,31,5,12,31) shows S/C/S/E+/C-/S. This is consistency evidence,
not an exact numeric IV readout. EVs are not displayed and were not compared.

## Runtime observation (quicksave path)

`--initial-state` with a private copy of the quicksave: the state was **accepted** and the
game resumed in the Hidden Grotto overworld. There were six records with the same
PIDs, species, levels and HP. Only `+41` (G+9, friendship) differed: slot 3 251→252,
slot 6 50→52. So the quicksave was taken after later play than the battery save. Its
SaveBlock1 party copy was not equal to the live party, which fits play after the
last in-game save. This is one host-core load, not a claim about Android-core state
compatibility in general.

## Field status matrix

Offsets are relative to the 100-byte party record; G/A/E/M blocks start at
`+32/+44/+56/+68`. "Production" means `pokemon_parse_single()` (vanilla-layout entry,
the layout `CONFIG_UNBOUND` uses). The observer calls it host-side through the
source-pinned adapter for each counted record; that is the real C parser, not a
Python model. Product party discovery, game detection and Kotlin mapping were
reasoned about from source only.

| Area | Status and evidence | Production comparison |
|---|---|---|
| Party count | **OBSERVED** u8 `0x02024029` = 6, stable over 26 observations; equals SB1+0x34; already set at title (frame 600) | Config `0x24029` agrees |
| Stride/base | **OBSERVED** 6 × 100 bytes at `0x02024284`, each record bound to its UI slot | Config/profile `0x24284` agree |
| Encryption/order | **OBSERVED** fixed G/A/E/M, unencrypted (6/6 records across 6 different `pid % 24` values) + **VERIFIED static** | Zero-checksum branch takes fixed order: agrees for these records |
| Checksum | **OBSERVED** stored `+28` = `0x0000` in 6/6; matches neither raw nor XOR sum | Production keys its CFRU branch on zero. The branch ran for all six. A non-zero stored word (e.g. in battle, as #24 claims) is **NOT_RUN**, and then production would try XOR+permutation |
| Species width / ID domain | **OBSERVED** 16-bit internal ID ≠ displayed dex for Tapu Koko 1002→785, Litten 942→725, Alolan Muk 1035→089 (forms are separate IDs); Gen 1–2 samples 130/186/230 equal dex. **NOT_VERIFIED**: Gen 3–6 internal numbering, full map | **Disagreement**: production outputs internal IDs (1002, 942, 1035) as `species`. If downstream treats them as national dex numbers, three of six slots are wrong. `& 0x07FF` did not change any observed word (max `0x040B`) |
| Moves / PP | **OBSERVED** four A halfwords, four A+8 bytes = current PP (24/24 match); IDs are CFRU-internal above 354 | Parser output = raw words. **Disagreement** if consumers use national move IDs (6 of 24 moves; Flare Blitz 434 collides with national Draco Meteor). Masks `0x07FF`/`0x7F` changed nothing observed. Max PP / PP-ups **NOT_VERIFIED** |
| Item | **OBSERVED** G+2 halfword, CFRU-internal IDs (e.g. Gyaradosite `0x1C9`, Focus Sash `0x23A`, Black Sludge `0x3A`) | `& 0x03FF` changed nothing observed. Item-ID domain mapping downstream **NOT_VERIFIED** |
| Level | **OBSERVED** byte `+84` (6/6) | Agrees |
| HP/stats | **OBSERVED** halfwords `+86` current HP, `+88..98` max HP/Atk/Def/Spe/SpA/SpD (36/36 values + 6 current HP) | Agrees |
| Experience | **OBSERVED** G+4 word (6/6) | Agrees (`& 0x1FFFFF` not exercised above 1,250,000) |
| IVs/EVs | IVs **OBSERVED consistent** with UI grade letters (36/36 rank-consistent); EVs **NOT_VERIFIED** (not shown in UI) | Same bit layout; agrees as far as observed |
| Nature | **OBSERVED** `pid % 25` (6/6, including the stat colours) | Agrees |
| Ability slot | **OBSERVED (one discriminating record)**: Politoed with M+4 bit31 = 1 shows hidden ability Drizzle. Public CFRU defines that bit as `hiddenAbility`, with the normal slot selected by `personality & 1`. The other five have bit31 = 0 and show ability 1 (Litten's pid bit0 = 1, but it has no ability 2). `personality & 1` choosing ability 2 is **NOT_VERIFIED** | **Disagreement**: production vanilla path reports bit31 as `ability_slot` 1 for Politoed, i.e. its second regular ability (Damp). It never uses `personality & 1`. Hidden ability is not represented |
| Shiny | **OBSERVED** UI star on slot 1 (shiny value 15) and slot 2 (value 5); none on values ≥ 2596 | **Disagreement**: production vanilla rule `< 8` reports slot 1 not shiny. Exact Unbound threshold **NOT_VERIFIED** (≥ 16 implied) |
| Nickname / OT | **OBSERVED** nickname `+8..17`, OT `+20..26`, Gen III charset, `0xFF` terminator; bytes after the terminator are not always `0xFF` (Litten). OT ID low half = IDNo | Production decodes up to the terminator; strings agree (6/6) |
| Enemy party | **NOT_RUN** (no battle). Both count candidates read 0 in the overworld | Unbound config uses `0x24028`; the FireRed config uses `0x2402A`. Neither was disambiguated |
| Save-block pointers | **OBSERVED** SB1 `0x0202552C`, SB2 `0x02024588` (stable); SB1+0x34/+0x38 party copy byte-identical to live after the battery load | Production does not read SaveBlocks for party. Serialized flash layout **NOT_VERIFIED** |
| Game detection | Source-only: the header is `POKEMON FIRE`, so `pokemon_detect_game()` would return FireRed, not Unbound. `unbound.json` matches only `UNBOUND` titles and has no hashes | **Disagreement (reasoned, not run)**: this exact cartridge would not select `CONFIG_UNBOUND`. Party offsets happen to be the same; the enemy count offset is not |

Every disagreement above is about field meaning or domain, not byte location.
Production reads the right bytes for all six records. Its output differs from what the
game displays for species identity (3/6), CFRU-internal move IDs (6/24 moves),
ability slot (1/6) and shiny (1/6), if consumers assume vanilla/national meanings.
How the Kotlin or calculator layer maps these IDs was **not** examined at runtime.

## Observer contract and reproduction

[`unbound_observe.py`](../tools/rom-evidence/unbound_observe.py) composes the existing
ROWE host, movie parser, private-directory guard, video ABI and adapter verifier
without modifying them. It requires the exact candidate ROM, core and source-pinned
adapter, plus either the exact Thor battery or the exact quicksave. It copies the
already hashed bytes into a new exclusive private directory. Originals are never passed
to writable cartridge APIs. There is no RAM-write, scan or `cartridge_save` command.
Movies support bounded `run`, `mash`, `observe`, `save` and `restore`. Ordinary inputs can
change emulated RAM. The committed route never confirms the in-game save prompt.

Each observation reads count, party (2 × 300 bytes), count, party again, count, and fails
the whole report on any mismatch or count > 6. It then reads both SaveBlock pointers,
the SB1+0x34/+0x38 copy (bounded and range-checked; digests and a per-counted-slot
equality only), both enemy-count candidates and the video frame. Only counted slots
are decoded. Each one gets fixed-GAEM raw fields, PID-permuted raw/XOR alternatives, both
checksum sums and the production parser result. Raw regions and PPMs stay in the
private directory. The report starts as FAIL and becomes OBSERVED only after the whole
movie finishes.

Commands run in attempt 3 (each with `timeout 600`, output redirected to private files):

```bash
P=/home/dq/dualdex-private/unbound
timeout 600 python3 tools/rom-evidence/unbound_observe.py --rom $P/unbound-candidate.gba \
  --core /home/dq/Projects/upstream-hns/mgba-host/mgba_libretro.so \
  --battery-save $P/thor-2026-09-06.sav --movie tools/rom-evidence/unbound-continue-summary.movie \
  --private-dir $P/runtime-opus/run04 --output $P/runtime-opus/run04.json
timeout 600 python3 tools/rom-evidence/unbound_observe.py --rom $P/unbound-candidate.gba \
  --core /home/dq/Projects/upstream-hns/mgba-host/mgba_libretro.so \
  --initial-state $P/thor-2026-09-06-quicksave.state --movie $P/runtime-opus/mstate.movie \
  --private-dir $P/runtime-opus/run05 --output $P/runtime-opus/run05.json
```

`mstate.movie` is `run 1`, `observe state_loaded`, `run 120`, `observe state_120`. Three
earlier exploratory battery runs (run01–03) found the route. One of them reached the
in-game “overwrite save?” prompt with an extra A press but did not confirm it. The
private cartridge copy was not saved, and the original `.sav` is never written. The
runtime manifest was derived from `run04.json`/`run05.json`, whose SHA-256 values it records. The
transcription fields are manual.

## Validation and outstanding work

* ROM/core runs: 5 (run01–05), all exit 0, total about 12 s of wall time
  (3.4 + 1.6 + 3.4 + 3.0 + 0.3 s), within the 40-minute budget. No process outlived its run.
* `TMPDIR=/home/dq/dualdex-private/unbound/runtime-opus python3 -m unittest discover -s tools/rom-evidence -v`:
  exit 0, 82 cases in 0.65 s (17 Unbound, 65 existing). The Unbound cases include the
  sanitized-manifest self-consistency check, which recomputes UI agreement from the
  committed raw fields, plus the movie-hash binding and SB1-copy bounds. The `TMPDIR` is set
  because an unrelated `/tmp/.git` exists on this host. The ROWE guard test is unchanged.
* `./ci.sh` and Gradle were not run (the coordinator owns combined validation). No
  production file changed.

Outstanding: exact release identity (publisher patch); internal→national species map
beyond the six sampled IDs (Gen 3–6 internal numbering is untested); move/item ID
mapping for consumers; ability-2 selection by `personality & 1`; exact shiny threshold;
EVs vs UI; checksum-word use during battle; enemy party and battler lifecycle
(**NOT_RUN**); serialized flash layout; whether product game detection selects
`CONFIG_UNBOUND` for this header (source says no); Android-core quicksave behaviour.
