# Heart & Soul 2.0.5 compatibility evidence (issue #40, phase 1)

This document records the evidence behind DualDex's Heart & Soul (H&S) 2.0.5 memory/layout
foundation. It is deliberately explicit about **how** each value was obtained, because the whole
point of the phase is to replace inherited guesses with reproducible evidence.

Verification vocabulary used throughout:

| Label | Meaning |
|---|---|
| **SOURCE VERIFIED** | Read directly from the tagged upstream source. |
| **COMPILED SYMBOL VERIFIED** | Read from `.sym`/`.map`/`nm` of a build of the tagged commit. |
| **ABI VERIFIED** | Produced by a probe compiled against the tagged headers with the upstream flags. |
| **RUNTIME VERIFIED** | Observed by running the official 2.0.5 ROM and reading the value live. |
| **NOT YET VERIFIED** | Evidence does not exist yet. Never treated as true. |

A compiled symbol is **not** automatically runtime proof of a DualDex reader, and an ABI fact is
**not** automatically a symbol address. The two are tracked separately below.

---

## 1. Exact upstream target

| Item | Value |
|---|---|
| Repository | `PokemonHnS-Development/pokehns-expansion` |
| Tag | `Release-v2.0.5` |
| Commit | `1f42b74dff0e9fe942419845d040663dd829a973` |
| Release asset | `pokemonHnS_v2.0.5.ups` (32,040,054 bytes) |

`master` was **not** used as a proxy for 2.0.5. The checkout was made in a sibling directory
outside this repository; no upstream source is vendored into DualDex.

### 1.1 Release asset integrity

| Measurement | Value |
|---|---|
| Published UPS digest (from issue #40) | `0ec228d53e2d707a4cd779bc9a1560f462eb91892d0e74d7080fdea473ba0f5e` |
| SHA-256 of the downloaded `pokemonHnS_v2.0.5.ups` | `0ec228d53e2d707a4cd779bc9a1560f462eb91892d0e74d7080fdea473ba0f5e` |
| UPS self-CRC32 (patch prefix) | `c76f81e2` — matches the digest stored in the patch footer |

**That digest is the SHA-256 of the UPS patch, not of the patched ROM.** It is never used as a
ROM hash anywhere in DualDex.

### 1.2 Lineage of the locally present ROM

A legally obtained H&S 2.0.5 ROM and a legally obtained Emerald (U) base ROM were already present
on this machine. Their lineage was confirmed through the UPS footer, which stores three CRC32
values (source, destination, patch):

| UPS footer field | Recorded | Locally present file | Match |
|---|---|---|---|
| Source CRC32 | `1f1c08fb` | `1986 - Pokemon Emerald (U)(TrashMan).gba` | yes |
| Destination CRC32 | `ffa22690` | `Pokémon Heart and Soul (v2.0.5).gba` | yes |
| Patch CRC32 | `c76f81e2` | the `.ups` itself | yes |

The locally present ROM is therefore exactly the official 2.0.5 release result.

| ROM property | Value |
|---|---|
| SHA-256 | `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b` |
| Size | 33,554,432 bytes (32 MiB) |
| Header title (`0x0A0`) | `POKEMON HNS` |
| Game code (`0x0AC`) | `BPEE` |
| Revision | `0x01` |

`POKEMON HNS` is exactly the `TITLE` the upstream Makefile assigns to the `hns` build
(`gbafix -t"POKEMON HNS"`), which independently corroborates the identity.

No ROM bytes are committed, copied into the repository, or reproduced in this document.

### 1.3 Upstream build reproduced

`make hns` was run against the pinned tag with the ARM GNU Toolchain 13.2.rel1
(`arm-none-eabi-gcc 13.2.1`), producing `.elf`, `.map`, `.sym` and `.gba` artifacts.

| Item | Value |
|---|---|
| Command | `make -j hns TOOLCHAIN=<arm-gnu-toolchain-13.2.rel1>` then `make BUILD=hns syms` |
| ABI | ARM7TDMI, `-mthumb -mthumb-interwork -O2 -mabi=apcs-gnu -mtune=arm7tdmi -march=armv4t` |
| Result | **succeeded** |
| Linker report | EWRAM 248,100 B / 256 KB (94.64%), IWRAM 25,680 B / 32 KB (78.37%), ROM 31,707,124 B (94.49%) |

A **release** configuration was also built (`make BUILD=hns release`, `RELEASE=1`, LTO via
`USE_LTO_ON_RELEASE=1`) to test whether the official release used it. It does **not** reproduce the
release binary's layout (see §5), so the plain `make hns` build is used for struct/ABI evidence and
the runtime measurements settle every address that matters.

---

## 2. Compiled symbol evidence

Addresses are labelled with their **unit**; absolute GBA addresses and EWRAM-relative offsets are
never mixed implicitly.

### 2.1 EWRAM symbols (`0x02000000`-based)

| Symbol | Absolute GBA address | Size | EWRAM-relative |
|---|---|---|---|
| `gBattleTypeFlags` | `0x020000AC` | 4 | `0xAC` |
| `gBattlersCount` | `0x020000B0` | 1 | `0xB0` |
| `gBattleOutcome` | `0x0200012C` | 1 | `0x12C` |
| `gBattlerPartyIndexes` | `0x02000144` | 8 | `0x144` |
| `gBattlerPositions` | `0x02000238` | 4 | `0x238` |
| `gBattleControllerExecFlags` | `0x020002F4` | 4 | `0x2F4` |
| `gBattleMons` | `0x02000420` | `0x220` | `0x420` |
| `gSaveblock3` | `0x0200921C` | `0x34` | `0x921C` |
| `gSaveblock1` | `0x020124A8` | `0x3E10` | `0x124A8` |
| `gSaveblock2` | `0x020162B8` | `0xFB4` | `0x162B8` |
| `gPlayerPartyCount` | `0x020342A8` | 1 | `0x342A8` |
| `gEnemyPartyCount` | `0x020342A9` | 1 | `0x342A9` |
| `gEnemyParty` | `0x020342B8` | `0x258` | `0x342B8` |
| `gPlayerPartyBackup` | `0x02034510` | `0x258` | `0x34510` |
| `gPlayerParty` | `0x02034768` | `0x258` | `0x34768` |

Cross-checks that make these self-consistent rather than merely asserted:

* `gBattleMons` is `0x220` bytes over `MAX_BATTLERS_COUNT == 4`, so
  `sizeof(struct BattlePokemon) == 0x220 / 4 == 136` — matches the independently compiled probe.
* `gPlayerParty` / `gEnemyParty` / `gPlayerPartyBackup` are `0x258 == 6 × 100`, so
  `sizeof(struct Pokemon) == 100` — matches the probe.
* `gSaveblock1` is `0x3E10 == 15888 == 15760 + 128`, i.e. `sizeof(struct SaveBlock1)` plus the
  128-byte ASLR window — matches the probe (15760).
* `gSaveblock2` is `0xFB4 == 4020 == 3892 + 128`, i.e. `sizeof(struct SaveBlock2)` plus the window —
  matches the probe (3892).
* `gSaveblock3` is `0x34 == 52 == sizeof(struct SaveBlock3)` — matches the probe.

### 2.2 IWRAM symbols (`0x03000000`-based)

| Symbol | Absolute GBA address | Source build | Release ROM (runtime) |
|---|---|---|---|
| `gSaveBlock3Ptr` | `0x03000178` | `0x03000178` | not used by DualDex |
| `gSaveBlock2Ptr` | `0x030041BC` | `0x030041BC` | `0x030041D4` |
| `gSaveBlock1Ptr` | `0x030041C0` | `0x030041C0` | **`0x030041D8`** |
| `gPokemonStoragePtr` | `0x030041C4` | `0x030041C4` | `0x030041DC` |

The from-source build and the official release binary disagree on the IWRAM placement of the
SaveBlock pointer triple by 24 bytes. See §5 for the runtime measurement that settles it; DualDex
ships the **runtime-verified** `0x030041D8`.

### 2.3 Symbols DualDex deliberately does **not** derive

`gBattlerPartyIndexes` is **732 bytes** away from `gBattleMons` on 2.0.5
(`0x420 - 0x144 = 0x2DC`). The previous `gBattleMons - 24` / `gBattleMons - 22` arithmetic, which
happened to be used for H&S, is wrong by hundreds of bytes and has been replaced with an explicit
configuration field.

`gEnemyPartyCount` is **not** `gEnemyParty - 4` (`0x342B4`); it is `0x342A9`, 15 bytes before
`gEnemyParty`. It is likewise an explicit field.

> **Runtime consumption note**: While `enemy_party_count_offset = 0x342A9` is compiled and verified from symbols, the current reader (`pokemon_read_enemy_party()`) does not consume `gEnemyPartyCount` as authoritative runtime input to bound party parsing or prune stale slots. Consumption of `gEnemyPartyCount` and verification of stale-slot behavior remain pending live battle/runtime validation (tracked in #1) before H&S can become VERIFIED.

---

## 3. Struct layout evidence (ABI)

Derived by compiling a probe translation unit against the **tagged** headers with the same compiler
and the same relevant flags the upstream Makefile uses, then reading the emitted constants and the
DWARF. Host `sizeof()` was never used.

Probe invocation (outside the upstream tree, upstream source unmodified):

```
arm-none-eabi-gcc -iquote <tag>/include -DMODERN=1 -DTESTING=0 -DPOKEMON_HNS -std=gnu17 \
  -mthumb -mthumb-interwork -O2 -mabi=apcs-gnu -mtune=arm7tdmi -march=armv4t -g -c probe.c
```

### 3.1 Sizes

| Type | Size (bytes) | DualDex before | Status |
|---|---|---|---|
| `struct BoxPokemon` | 80 | 80 | confirmed |
| `struct Pokemon` | 100 | 100 | confirmed |
| `struct BattlePokemon` | **136** | 88 | **changed** |
| `struct ChallengeSettings` | 32 | (unused) | recorded |
| `struct SaveBlock1` | 15760 | (assumed vanilla) | recorded |
| `struct SaveBlock1ASLR` | 15888 | — | recorded |
| `struct SaveBlock2` | 3892 | — | recorded |
| `struct SaveBlock3` | 52 | — | recorded |
| `struct Volatiles` | 44 | — | recorded |
| `MAX_BATTLERS_COUNT` | 4 | (assumed 2) | recorded |
| `SAVEBLOCK_MOVE_RANGE` | 128 | — | confirmed |

### 3.2 `struct BattlePokemon` member offsets

| Member | Compiled offset | Upstream source comment | DualDex before | Status |
|---|---|---|---|---|
| `species` | `0x00` | `0x00` | — | confirmed |
| `attack` | `0x02` | `0x02` | — | confirmed |
| `defense` | `0x04` | `0x04` | — | confirmed |
| `speed` | `0x06` | `0x06` | — | confirmed |
| `spAttack` | `0x08` | `0x08` | — | confirmed |
| `spDefense` | `0x0A` | `0x0A` | — | confirmed |
| `moves` | `0x0C` | `0x0C` | — | confirmed |
| `statStages` | `0x18` | `0x18` | `0x18` | confirmed |
| `ability` | `0x20` | `0x20` | — | confirmed |
| `types` | `0x22` | `0x22` | — | confirmed |
| `pp` | `0x25` | `0x25` | — | confirmed |
| **`hp`** | **`0x2A`** | `0x29` | `40` (`0x28`) | **changed** |
| `level` | `0x2C` | `0x2B` | — | recorded |
| `friendship` | `0x2D` | `0x2C` | — | recorded |
| **`maxHP`** | **`0x2E`** | `0x2D` | — | **changed (recorded)** |
| `item` | `0x30` | `0x2F` | — | recorded |
| `nickname` | `0x32` | `0x31` | — | recorded |
| `ppBonuses` | `0x3F` | `0x3C` | — | recorded |
| `otName` | `0x40` | `0x3D` | — | recorded |
| `experience` | `0x48` | `0x45` | — | recorded |
| `personality` | `0x4C` | `0x49` | — | recorded |
| **`status1`** | **`0x50`** | `0x4D` | — | **changed (recorded)** |
| `volatiles` | `0x54` | `0x51` | — | recorded |
| `otId` | `0x80` | `0x5D` | — | recorded |
| `metLevel` | `0x84` | `0x61` | — | recorded |
| `isShiny` | `0x85` | `0x62` | — | recorded |

Important: **the upstream `/*0xNN*/` offset comments in `struct BattlePokemon` do not match the
compiled layout.** They are hand-written and were not updated when the struct changed. The
differences are explained by ordinary alignment: `pp[4]` ends at `0x29`, so the `u16 hp` is padded
to `0x2A`, and everything after shifts by one; `struct Volatiles` is 44 bytes, not the 12 bytes the
comments imply, which pushes `otId` to `0x80`. The compiled values are authoritative and are what
DualDex now uses.

### 3.3 `struct SaveBlock1` member offsets

| Member | Compiled offset | Vanilla Gen 3 comment | Status |
|---|---|---|---|
| `saveVersion` | `0x00` | (does not exist) | **new field in H&S 2.0.5** |
| `pos` | **`0x04`** | `0x00` | **changed** |
| `location` | **`0x08`** | `0x04` | **changed** |
| `continueGameWarp` | `0x10` | `0x0C` | recorded |
| `dynamicWarp` | `0x18` | `0x14` | recorded |
| `lastHealLocation` | `0x20` | `0x1C` | recorded |
| `escapeWarp` | **`0x28`** | `0x24` | **changed** |
| `mapLayoutId` | `0x36` | `0x32` | recorded |
| `playerPartyCount` | `0x238` | `0x234` | recorded |
| `playerParty` | `0x23C` | `0x238` | recorded |

Root cause: H&S 2.0.5 added `u16 saveVersion` as the first member, and under `-mabi=apcs-gnu`
`_Alignof(struct Coords16) == 4` (APCS-GNU word-aligns aggregates), so `pos` starts at `0x04`, not
at `0x02`. Everything after `pos` shifts by 4. The `/*0x00*/`-style comments in the tagged header are
inherited from vanilla pokeemerald and are stale for this fork.

### 3.4 `struct BoxPokemon` / substructure bit placement

Compiled bit placement under `-mabi=apcs-gnu` (measured by emitting a union image with one field set
at a time; allocation is LSB-first, same as AAPCS):

`PokemonSubstruct3` (12 bytes):

| Offset | Contents |
|---|---|
| `0` | `pokerus` |
| `1` | `metLocation` |
| `2..3` | u16 word: `metLevel` bits 0-6, `metGame` 7-10, `dynamaxLevel` 11-14, `otGender` 15 |
| `4..7` | u32 word: `hpIV` 0-4, `attackIV` 5-9, `defenseIV` 10-14, `speedIV` 15-19, `spAttackIV` 20-24, `spDefenseIV` 25-29, `isEgg` **30**, **`gigantamaxFactor` 31** |
| `8..11` | u32 word: `coolRibbon` 0-2 … `isShadow` 27, `unused_0B` 28, **`abilityNum` 29-30**, `modernFatefulEncounter` 31 |

`BoxPokemon`:

| Offset | Contents |
|---|---|
| `0x12` | `language` bits 0-2, **`hiddenNatureModifier` bits 3-7** |
| `0x13` | `isBadEgg` bit 0, `hasSpecies` 1, `isEgg` 2, `blockBoxRS` 3, `daysSinceFormChange` 4-6 |
| `0x1B` | `markings` bits 0-3, `compressedStatus` 4-7 |
| `0x1E..0x1F` | u16: `hpLost` bits 0-13, **`shinyModifier` bit 14**, `unused_1E` 15 |

`BattlePokemon` IV word (`0x14..0x17`): the six IV fields occupy bits 0-29 and
**`abilityNum` occupies bits 30-31**. Note this differs from `PokemonSubstruct3`, where the ability
lives in a different word at bits 29-30 — the two structs must not share one extraction routine.

---

## 4. DualDex assumptions: confirmed, changed, unverified

### 4.1 Confirmed (proof recorded, value intentionally unchanged)

| Assumption | Evidence |
|---|---|
| `sizeof(struct BoxPokemon) == 80`, `sizeof(struct Pokemon) == 100` | ABI VERIFIED; consistent with `gPlayerParty` size `0x258 / 6` |
| `BattlePokemon.statStages` at `0x18`, neutral stage 6, range 0-12 | ABI VERIFIED (`NUM_BATTLE_STATS == 8`); SOURCE VERIFIED default |
| Species 11 bits, held item 10 bits, move IDs 11 bits | SOURCE VERIFIED in `PokemonSubstruct0`/`1` |
| `isEgg` is bit 30 of the IV word | ABI VERIFIED |
| Displayed nature is `pid % 25` | SOURCE VERIFIED: `GetNature()` is `personality % NUM_NATURES`, and `pokemon_summary_screen.c` stores `GetNature(mon)` in `sum->nature`. DualDex's displayed nature was **already correct** and is unchanged. |
| Vanilla FireRed/Emerald layouts | Unchanged throughout; asserted by regression tests |
| Fail-closed trust rule (only an exact SHA match unlocks live reads) | Unchanged; asserted by regression tests |

### 4.2 Changed

| # | Assumption before | Evidence-backed value | Evidence |
|---|---|---|---|
| 1 | H&S `playerPartyOffset = 0x340F4` | `0x34768` (`gPlayerParty`) | COMPILED SYMBOL VERIFIED + runtime EWRAM confirmation (§5) |
| 2 | H&S `playerPartyCountOffset = 0x340F0` | `0x342A8` (`gPlayerPartyCount`) | COMPILED SYMBOL VERIFIED |
| 3 | H&S `enemyPartyOffset = 0x345A4` | `0x342B8` (`gEnemyParty`) | COMPILED SYMBOL VERIFIED |
| 4 | H&S `enemyPartyCountOffset = 0x345A0` | `0x342A9` (`gEnemyPartyCount`) | COMPILED SYMBOL VERIFIED (symbol configured; runtime reader consumption & stale-slot truncation pending #1) |
| 5 | H&S `battleMonsOffset = 0x3A5A4` | `0x420` (`gBattleMons`) | COMPILED SYMBOL VERIFIED |
| 6 | `battle_mons_size = 88` | `136` | ABI VERIFIED + `gBattleMons` size cross-check |
| 7 | `battle_mons_hp_offset = 40` | `0x2A` (42) | ABI VERIFIED |
| 8 | `gBattlerPartyIndexes = gBattleMons - 24` (and `- 22` for battler 1) | explicit `0x144`, indexed `+0` / `+2` | COMPILED SYMBOL VERIFIED; the derived form is off by 732 bytes |
| 9 | H&S SaveBlock1 base is `ewram` (EWRAM base) | active base read from `gSaveBlock1Ptr` in IWRAM, validated against the 128-byte ASLR window around `gSaveblock1` | SOURCE VERIFIED (`SetSaveBlocksPointers`) + RUNTIME VERIFIED |
| 10 | `save_block1_ptr` at IWRAM `0x030041C0` | `0x030041D8` | RUNTIME VERIFIED against the release ROM |
| 11 | SaveBlock1 `pos` `0x00`, `location` `0x04`, `escapeWarp` `0x24` | `0x04`, `0x08`, `0x28` | ABI VERIFIED |
| 12 | expansion `ability_slot` = bit 31 of the IV word | bit 31 is `gigantamaxFactor`; `abilityNum` is bits 29-30 of the ribbon word | ABI VERIFIED + SOURCE VERIFIED |
| 13 | expansion shiny = classic `(tid^sid^pidHi^pidLo) < 8` | `(shinyValue < shinyOdds) ^ shinyModifier`, with `shinyOdds` from `SaveBlock3.challengeSettings` | SOURCE VERIFIED |
| 14 | profile JSON party offsets were EWRAM-relative while every other profile is absolute | absolute `0x02034768` / `0x020342B8` | COMPILED SYMBOL VERIFIED |
| 15 | `RETRO_ENVIRONMENT_SET_MEMORY_MAPS` ignored; only `RETRO_MEMORY_SYSTEM_RAM` (EWRAM) available | memory-map capture + bounds-checked absolute-address reader (IWRAM and EWRAM) | SOURCE VERIFIED (mGBA publishes the map) + RUNTIME VERIFIED (11 regions captured) |

### 4.3 Nature and shiny: what is and is not claimed

**Nature.** H&S 2.0.5 has two natures:

* `GetNature(mon) == pid % 25` — the nature the summary screen displays (`sum->nature`).
* `MON_DATA_HIDDEN_NATURE == (pid % 25) ^ hiddenNatureModifier` — the nature
  `CalculateMonStats()` actually applies (`sum->mintNature`, i.e. the Mint nature).

DualDex now reports both: `nature` stays `pid % 25` (displayed) and `hiddenNature` carries the
stat-effective value, with `natureModified` flagging that a mint changed it. Which of the two the
party UI should present is **NOT YET VERIFIED**; the calculator must use `hiddenNature`, and that
work belongs to #9.

**Shiny.** H&S computes `(shinyValue < shinyOdds) ^ shinyModifier`, where `shinyOdds` is selected
from `{8, 16, 32, 64, 128}` by `SaveBlock3.challengeSettings.tx_Features_ShinyChance`. DualDex does
not read SaveBlock3 challenge settings yet, so a confident verdict is only possible when it is
independent of the odds:

* `shinyValue < 8` → shiny for every possible odds value → `SHINY_YES`
* `shinyValue >= 128` → not shiny for every possible odds value → `SHINY_NO`
* otherwise → `SHINY_UNKNOWN` (never guessed)

### 4.4 Still unverified

| Item | Status |
|---|---|
| H&S party content with a real save (levels, species, forms) | NOT YET VERIFIED — no save file was available |
| H&S battle lifecycle, active-battler semantics, HP sync in a live battle | NOT YET VERIFIED (tracked in #1) |
| Battle UI / interactive controls | NOT YET VERIFIED — `battleUiVerified` and `interactiveControlsVerified` remain `false` |
| `gSaveblock3` exact EWRAM address | NOT YET VERIFIED (a 4-byte discrepancy was observed between builds; DualDex does not read it) |
| H&S `gEnemyPartyCount` runtime consumption & stale-slot behavior under a live battle | NOT YET VERIFIED — symbol offset (`0x342A9`) is recorded from compiled symbols, but `pokemon_read_enemy_party()` does not yet consume it to prune stale slots; pending battle lifecycle (#1) before H&S can become VERIFIED |
| Which nature the party UI should display | NOT YET VERIFIED |
| H&S map group/map number semantics | NOT YET VERIFIED (tracked in #11) |

---

## 5. Runtime evidence

The production native reader sources were compiled unchanged into a throwaway host harness that
drives a host build of the bundled mGBA libretro core (`cores/mgba-src`, `BUILD_LIBRETRO=ON`)
against the locally present official 2.0.5 ROM. The harness lives outside this repository and is not
shipped; no ROM bytes are stored.

### 5.1 Memory-map capture

| Observation | Result |
|---|---|
| Regions published at `retro_load_game` | 0 (mGBA defers setup to the first `retro_run`) |
| Regions after the first frame | **11** — exactly mGBA's GBA descriptor count (IWRAM, EWRAM, SRAM/Flash, 3× ROM mirror, BIOS, VRAM, palette, OAM, I/O) |
| IWRAM region | `start=0x03000000`, `len=0x8000`, `select=0xFF000000` |
| EWRAM region | `start=0x02000000`, `len=0x40000`, `select=0xFF000000` |
| ROM read through the region reader (`0x080000A0`) | `POKEMON HNS` — correct bytes |
| Regions after `retro_unload_game` | 0, and a subsequent read is rejected (fail-closed) |

This confirms the issue #40 premise: the bundled core does publish IWRAM/EWRAM descriptors, and the
old `RETRO_MEMORY_SYSTEM_RAM`-only path could never reach the IWRAM SaveBlock pointer.

### 5.2 SaveBlock pointer location and ASLR

A full IWRAM dump after entering the game showed three adjacent pointer words:

| IWRAM address | Run A | Run B | Minus compiled EWRAM base |
|---|---|---|---|
| `0x030041D4` | `0x02016310` | `0x0201632C` | `gSaveblock2` `0x020162B8` + **88 / 116** |
| `0x030041D8` | `0x02012500` | `0x0201251C` | `gSaveblock1` `0x020124A8` + **88 / 116** |
| `0x030041DC` | `0x02009F20` | `0x02009F3C` | `gPokemonStorage` `0x02009EC8` + **88 / 116** |

Three independent conclusions follow:

1. **`gSaveBlock1Ptr` is at IWRAM `0x030041D8` in the official release ROM**, not `0x030041C0`.
2. **The EWRAM layout of the from-source build matches the release binary exactly**: all three
   pointers independently land on `compiled_base + offset` with the *same* offset.
3. **The ASLR is real and is followed correctly.** `SetSaveBlocksPointers` produced offset `88`
   (`0x58`) in one run and `116` (`0x74`) in another — 4-byte-aligned values inside the 128-byte
   window, applied simultaneously to all three pointers, exactly as the tagged source describes.

With the corrected pointer address, the **production** reader resolved the location end to end
against the release ROM:

```
pokemon_read_player_location_gba: OK
  map_group=0 map_num=0 warp_id=0 pos=(0,0) warp=(0,0) escape=(0,0)
```

That is `RUNTIME VERIFIED` for: memory-map capture, IWRAM pointer read, window/alignment/range
validation, EWRAM translation, and SaveBlock1 field decoding at the compiled offsets. The field
*values* are zero because the probe reaches the game with no save file, so no party or map state
exists yet.

### 5.3 Why the from-source build cannot be trusted for IWRAM addresses

| Build | `gSaveBlock1Ptr` (IWRAM) | `gSaveblock1` (EWRAM) | `gSaveblock2` | `gPokemonStorage` | `gPlayerParty` |
|---|---|---|---|---|---|
| `make hns` (no LTO) | `0x030041C0` | `0x020124A8` | `0x020162B8` | `0x02009EC8` | `0x02034768` |
| `make BUILD=hns release USE_LTO_ON_RELEASE=0` | `0x030041C0` | `0x020124A4` | `0x020162B4` | `0x02009EC4` | `0x02034764` |
| `make BUILD=hns release` (LTO) | `0x03002EF0` | `0x020351A4` | — | — | — |
| **Official 2.0.5 release ROM (runtime)** | **`0x030041D8`** | `0x020124A8` | `0x020162B8` | `0x02009EC8` | not observed at boot |

The plain `make hns` build reproduces the release's **EWRAM** ordering exactly — §5.2 confirms
`gSaveblock1`, `gSaveblock2` and `gPokemonStorage` all sit at the plain build's addresses, each
independently offset by the same live ASLR delta. Neither release build does: `-DRELEASE` shifts
EWRAM down by 4 bytes, and LTO rearranges everything. The official binary's **IWRAM** ordering
differs from every local build by 24 bytes, which points at a different compiler/toolchain revision
rather than at different make flags.

The methodological conclusion is what matters: **a local build is good evidence for struct layout
and for EWRAM ordering, but it is not sufficient evidence for a symbol address.** Every address that
matters must be confirmed against the release ROM itself, as `gSaveBlock1Ptr` was here. This is
recorded as a durable constraint for follow-up work, not just as a one-off value correction.

---

## 6. Capability verification status

| Capability | Status |
|---|---|
| H&S 2.0.5 ROM identity | RUNTIME VERIFIED (hash + UPS footer lineage) |
| `heart_and_soul.json` `sha256Hashes` | **intentionally empty** — see §7 |
| GBA memory-region capture (IWRAM/EWRAM) | RUNTIME VERIFIED |
| Bounds-checked absolute-address reads | RUNTIME VERIFIED (incl. rejection after unload) + unit tested |
| SaveBlock1 ASLR resolution | RUNTIME VERIFIED |
| SaveBlock1 field offsets | ABI VERIFIED; reader path RUNTIME VERIFIED |
| Party symbol addresses (`gPlayerParty`, `gPlayerPartyCount`) | COMPILED SYMBOL VERIFIED; EWRAM ordering RUNTIME VERIFIED; party content NOT YET VERIFIED |
| Enemy party symbols | COMPILED SYMBOL VERIFIED; NOT YET VERIFIED at runtime |
| `BattlePokemon` size / HP / stat-stage offsets | ABI VERIFIED; NOT YET VERIFIED at runtime |
| `gBattlerPartyIndexes` explicit address | COMPILED SYMBOL VERIFIED; NOT YET VERIFIED at runtime |
| Expansion `abilityNum` / `gigantamaxFactor` decoding | ABI VERIFIED + SOURCE VERIFIED; unit tested |
| Expansion nature / shiny semantics | SOURCE VERIFIED; unit tested; UI policy NOT YET VERIFIED |
| H&S 2.0.5 Species / Move Data Pack (`HeartAndSoul205DataPack`) | SOURCE VERIFIED (extracted via `arm-none-eabi-cpp` preprocessor directly from pinned upstream checkout `1f42b74dff0e9fe942419845d040663dd829a973` with zero commercial ROM dependency); unit tested |
| Profile Custom Species Isolation (Ghost Grey vs H&S 500-502) | SOURCE VERIFIED; unit tested |
| H&S 2.0.5 Held Items | NON-AUTHORITATIVE (held items marked unverified in live party presentation pending dedicated item audit) |
| Battle lifecycle / UI / interactive controls | NOT YET VERIFIED (explicitly out of scope; #1) |
| H&S maps / regions | NOT YET VERIFIED (explicitly out of scope; #11) |
| H&S calculator correctness | NOT YET VERIFIED (explicitly out of scope; #9) |

---

## 7. Why `sha256Hashes` stays empty

The exact 2.0.5 ROM is present locally and its SHA-256 is recorded above, and the SaveBlock1 /
memory-region reader paths did survive runtime checks. It is nonetheless **not** added to
`heart_and_soul.json` in this phase, because:

* the party, battle and map capabilities that H&S support advertises are still NOT YET VERIFIED, so
  adding the hash would unlock authoritative live-memory reads for a ROM whose party and battle
  interpretation has not been validated against real game state;
* `battleUiVerified` and `interactiveControlsVerified` are false and #1/#11/#9 are unresolved.

The trust boundary is unchanged and still requires an exact SHA-256 match, so H&S remains
`RECOGNIZED_UNVERIFIED` and `mayReadLiveMemory` stays false. **The UPS patch digest is never used as
a ROM hash.**

Promotion requires, at minimum: a legal save state with a party, a wild battle, and a trainer battle,
with the observed party/location/battle values compared against known game state.

---

## 8. Discovered blockers for follow-up work

These were found during this phase and are **not** fixed here.

1. **[RESOLVED in #42 / #43] Blind-EWRAM-scan false positive when the party is empty.** Authoritative
   party count policies were established for H&S 2.0.5, Emerald, and FireRed, preventing blind pattern
   scans when `gPlayerPartyCount == 0` or bounding party interpretation to `[0..count-1]`.
2. **[RESOLVED in compat/hns-2.0.5-data-pack] Species database collision & data pack isolation.**
   - Global `SpeciesDatabase.registerCustom()` pollution was eliminated: Ghost Grey custom entries (IDs 500-502) no longer globally overwrite canonical entries.
   - Profile custom species are isolated as an overlay (`ProfileOverlayDataPack`) on top of the active profile's game data pack.
   - An exact, version-pinned H&S 2.0.5 data pack (`HeartAndSoul205DataPack`, pack ID `hns_2_0_5`) was generated directly from the pinned upstream source checkout (`PokemonHnS-Development/pokehns-expansion` at commit `1f42b74dff0e9fe942419845d040663dd829a973`, tag `Release-v2.0.5`) using a preprocessor-derived extractor (`tools/hns-data-pack/generate_hns_data_pack.py`) with zero commercial ROM / `.gba` dependencies.
   - Generation accurately extracts 1,427 species and 934 moves. Mechanics follow Gen 8 baseline (Fairy type enabled, Physical/Special split enabled, modern type chart without Steel resisting Ghost/Dark), with Gen 9 species (e.g. Terapagos) and Gen 9 moves (e.g. Tera Starstorm with `PokemonType.STELLAR` and Malignant Chain) fully resolved.
   - Global fallback is strictly blocked (`allowGlobalFallback = false`): unknown species or move IDs render safe placeholders (`"Unknown Species #<id>"` / `"Unknown Move #<id>"`) rather than polluting or substituting with generic entries.
   - Held items are explicitly marked non-authoritative (`(unverified)`) in live party presentation pending dedicated item table extraction.
3. **Stale map groups.** `map_groups_hns.json` does not match 2.0.5, which adds/reorders groups
   including `OutdoorAlola`, `IndoorAlola`, `IndoorDynamic`, `Sinjoh`, `IndoorSinjoh`, `SpecialArea`.
   Groups 25+ must not be treated as authoritative, and unknown H&S map IDs should fail to an unmapped
   state rather than defaulting to Johto. (#11)
4. **Runtime challenge settings change the answers.** `SaveBlock3.challengeSettings`
   (`sizeof == 32`, ABI VERIFIED) carries random types / type effectiveness / abilities / moves and
   modes such as mints, Fairy types, modern moves, legendary abilities, Base Stat Equalizer and No EVs.
   Shiny odds are among them, which is why the shiny verdict is tri-state. Calculator work must read
   these or explicitly degrade. (#9)
5. **Toolchain/layout reconciliation.** A local build does not reproduce the release binary's IWRAM
   ordering (§5.3). Any future symbol-dependent work must confirm addresses against the release ROM.
6. **No save file was available**, so every party-, battle- and map-dependent capability remains
   unverified; obtaining a legal save with a party and a battle is the next concrete step for #40.

---

## 9. Reproducing this evidence

```bash
# 1. Pinned upstream checkout (outside this repository)
git clone https://github.com/PokemonHnS-Development/pokehns-expansion.git
cd pokehns-expansion
git checkout 1f42b74dff0e9fe942419845d040663dd829a973   # tag Release-v2.0.5

# 2. Build with the ARM GNU Toolchain 13.2.rel1
make -j"$(nproc)" hns TOOLCHAIN=/path/to/arm-gnu-toolchain-13.2.rel1-x86_64-arm-none-eabi
make BUILD=hns syms TOOLCHAIN=/path/to/arm-gnu-toolchain-13.2.rel1-x86_64-arm-none-eabi

# 3. Symbols
arm-none-eabi-nm -S --defined-only pokehns.elf | sort

# 4. Struct layout: compile a probe against the tagged headers with the flags in §3.

# 5. DualDex regression coverage (no ROM required)
./ci.sh test
```

All committed regression tests use synthetic memory buffers and source-derived fixtures. No ROM,
no ROM-derived data, and no copyrighted content is committed.
