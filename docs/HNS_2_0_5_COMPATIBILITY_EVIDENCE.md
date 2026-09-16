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

> **Read this section as SOURCE-BUILD evidence, not as release-ROM truth.**
>
> Every address in §2.1–§2.3 comes from a local `make hns` build of the tagged commit. That is
> sufficient for **struct layout** and for the ordering of EWRAM arrays, and it is what DualDex
> used while the layout foundation was being established. It is **not** sufficient for a symbol
> *address* in the official binary: §5.3 showed the IWRAM block is offset by `0x18`, and §11.5/§11.6
> showed the same class of shift moved `gMain` and the whole party group in EWRAM as well.
>
> The distinction the rest of this document relies on:
>
> ```text
> COMPILED SYMBOL VERIFIED  !=  OFFICIAL-RELEASE RUNTIME ADDRESS
> ```
>
> Where the two differ, §11 carries the runtime-verified value and is authoritative.

### 2.1 EWRAM symbols (`0x02000000`-based)

| Symbol | Absolute GBA address | Size | EWRAM-relative |
|---|---|---|---|
| `gBattleTypeFlags` | `0x020000AC` | 4 | `0xAC` |
| `gBattlersCount` | `0x020000B0` | 1 | `0xB0` |
| `gBattleOutcome` | `0x0200012C` | 1 | `0x12C` |
| `gBattlerPartyIndexes` | `0x02000144` | 8 | `0x144` |
| `gBattlerPositions` | `0x02000238` | 4 | `0x238` |
| `gBattleControllerExecFlags` | `0x020002F4` | 4 | `0x2F4` |
| `gAbsentBattlerFlags` | `0x0200030A` | 1 | `0x30A` |
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

**Which of these are also the official-release addresses.** The battle globals above
(`gBattleTypeFlags`, `gBattlersCount`, `gBattleOutcome`, `gBattlerPartyIndexes`,
`gBattlerPositions`, `gAbsentBattlerFlags`, `gBattleMons`) and the SaveBlock arrays were confirmed
unchanged on the release ROM at runtime in §11.5. **The party group was not.** Both sets are true —
for different binaries — and the document keeps both:

```text
SOURCE-BUILD / COMPILED (this section):

    gPlayerPartyCount            0x020342A8
    gEnemyPartyCount             0x020342A9
    gEnemyParty                  0x020342B8
    gPlayerParty                 0x02034768

OFFICIAL RELEASE / RUNTIME (§11.5, §11.6 — what DualDex ships):

    gPlayerPartyCount            0x020342A4
    gEnemyPartyCount             0x020342A5
    gEnemyParty                  0x020342B4
    gPlayerParty                 0x02034764
```

The whole group sits **4 bytes lower** in the official binary. The source-build values are not
rewritten above because they were never false for the source build; they are simply not the
addresses the release ROM uses.

### 2.2 IWRAM symbols (`0x03000000`-based)

| Symbol | Absolute GBA address | Source build | Release ROM (runtime) |
|---|---|---|---|
| `gSaveBlock3Ptr` | `0x03000178` | `0x03000178` | not used by DualDex |
| `gSaveBlock2Ptr` | `0x030041BC` | `0x030041BC` | `0x030041D4` |
| `gSaveBlock1Ptr` | `0x030041C0` | `0x030041C0` | **`0x030041D8`** |
| `gPokemonStoragePtr` | `0x030041C4` | `0x030041C4` | `0x030041DC` |
| `gMain` (`struct Main`, `sizeof == 0x43C`) | `0x03005BC0` | `0x03005BC0` | **`0x03005BD8`** |

The from-source build and the official release binary disagree on IWRAM placement by 24 bytes
(`+0x18`) for **both** the SaveBlock pointer triple and `gMain`. DualDex ships the
**runtime-verified** `0x030041D8` for the pointer and `0x03005BD8` for `gMain`.

> **SUPERSEDED BY §11 RUNTIME VALIDATION**
>
> An earlier revision of this table recorded `gMain` as `0x03005BC0` "same on both", on the strength
> of a boot-only probe: over 20,000 frames the byte at `0x03005FF9` was readable on every frame and
> held only `0x00`, which was read as confirmation that the address was the release ROM's
> `inBattle` byte.
>
> That conclusion is **withdrawn**. Readability plus a constant-zero value was not sufficient
> semantic evidence that the address belonged to `gMain` — an unrelated zero-filled IWRAM byte
> satisfies it equally well. Later runtime validation with real input and real battle transitions
> (§11.5, §11.6) established that the official release places `gMain` at **`0x03005BD8`**, `+0x18`
> from the source-build symbol, so the release `inBattle` byte is **`0x03006011`**, not
> `0x03005FF9`. The decisive test was semantic: the `heldKeys` words tracked live input only at the
> release address, and `callback1` was `NULL` only there.
>
> The methodological lesson, which applies to every address in this document:
>
> > **Readable memory and a plausible constant value are not enough to establish symbol identity;
> > release-ROM addresses require semantic correlation with game behaviour.**
>
> The boot-only observation is retained above as superseded evidence, not as current truth.

### 2.3 Symbols DualDex deliberately does **not** derive

`gBattlerPartyIndexes` is **732 bytes** away from `gBattleMons` on 2.0.5
(`0x420 - 0x144 = 0x2DC`). The previous `gBattleMons - 24` / `gBattleMons - 22` arithmetic, which
happened to be used for H&S, is wrong by hundreds of bytes and has been replaced with an explicit
configuration field.

`gEnemyPartyCount` is **not** `gEnemyParty - 4`. In the source build the count is `0x342A9`, 15
bytes before `gEnemyParty` at `0x342B8`; in the official release it is `0x342A5`, 15 bytes before
`gEnemyParty` at `0x342B4`. In neither layout is it 4 bytes before the array. It is likewise an
explicit field.

> **Runtime consumption note (updated by §11)**: this note originally recorded that
> `pokemon_read_enemy_party()` did not yet consume `gEnemyPartyCount` as authoritative input. That
> gap was closed in PR #43/#45 and the behaviour is now **runtime verified** in §11.3: during a live
> wild battle the enemy party is bounded by `gEnemyPartyCount` (observed value `1`), and after
> `gMain.inBattle` clears the production surface is empty on every frame even while the raw count
> byte still reads a stale value. The addresses used are the official-release ones below.

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

| # | Assumption before | Source-build symbol | Official-release runtime address | Evidence |
|---|---|---|---|---|
| 1 | H&S `playerPartyOffset = 0x340F4` | `0x34768` (`gPlayerParty`) | **`0x34764`** | COMPILED SYMBOL VERIFIED; release address RUNTIME VERIFIED (§11.5, §11.6) |
| 2 | H&S `playerPartyCountOffset = 0x340F0` | `0x342A8` (`gPlayerPartyCount`) | **`0x342A4`** | COMPILED SYMBOL VERIFIED; release address RUNTIME VERIFIED (§11.5, §11.6) |
| 3 | H&S `enemyPartyOffset = 0x345A4` | `0x342B8` (`gEnemyParty`) | **`0x342B4`** | COMPILED SYMBOL VERIFIED; release address RUNTIME VERIFIED (§11.5, §11.6) |
| 4 | H&S `enemyPartyCountOffset = 0x345A0` | `0x342A9` (`gEnemyPartyCount`) | **`0x342A5`** | COMPILED SYMBOL VERIFIED; release address RUNTIME VERIFIED (§11.5, §11.6) |
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
| H&S battle lifecycle *in a live battle* (enter, wild, trainer, switch, faint, exit edge, doubles) | Wild battle enter/HP/faint/exit RUNTIME VERIFIED (§11.3); trainer battle, opponent faint replacement, player switch, and player faint SYNTHETIC UNIT VERIFIED (`test_pokemon_reader.c`); live trainer/switch/replacement NOT RUNTIME VERIFIED (tracked in #1) |
| H&S battle lifecycle in the inactive/overworld state | RUNTIME VERIFIED — 20,000 frames of the official ROM: `INACTIVE`/`NONE_ACTIVE` on every sample, 0 invariant failures (§6.5) |
| Battle UI / interactive controls | NOT YET VERIFIED — `battleUiVerified` and `interactiveControlsVerified` remain `false` |
| `gSaveblock3` exact EWRAM address | NOT YET VERIFIED (a 4-byte discrepancy was observed between builds; DualDex does not read it) |
| H&S `gEnemyPartyCount` runtime consumption | RESOLVED in this PR — `0x342A9` is now the sole authority for the enemy party (`0` -> empty, `1..6` -> exact bounds, `>6` -> fail closed, corrupt slot -> fail closed) and the player-party blind scan is unreachable for H&S. The *values* it takes during a live battle remain NOT RUNTIME VERIFIED |
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

## 6. Battle lifecycle and active-battler evidence (issue #1)

The primary invariant this section exists to establish:

> DualDex must never infer the active enemy or active party slot from coincidence, stale state, or
> "slot 0 by default".

and, for battle presence:

> Heart & Soul 2.0.5 has exactly one production source of truth for battle presence — the
> authoritative lifecycle. If that lifecycle cannot be read, DualDex reports unknown rather than
> reconstructing a battle from stale EWRAM.

### 6.1 Symbols the lifecycle decision is built from

Added to the compiled-symbol tables in §2.1 (EWRAM) and §2.2 (IWRAM):

| Symbol | Absolute GBA address | Size | Unit | Source declaration |
|---|---|---|---|---|
| `gBattleTypeFlags` | `0x020000AC` | 4 | EWRAM | `include/battle.h:960` |
| `gBattlersCount` | `0x020000B0` | 1 | EWRAM | `include/battle.h:965` |
| `gBattleOutcome` | `0x0200012C` | 1 | EWRAM | `include/battle.h:1011` |
| `gBattlerPartyIndexes` | `0x02000144` | 8 | EWRAM | `include/battle.h:966` (`u16[MAX_BATTLERS_COUNT]`) |
| `gBattleControllerExecFlags` | `0x020002F4` | 4 | EWRAM | `include/battle.h:964` |
| `gAbsentBattlerFlags` | `0x0200030A` | 1 | EWRAM | `include/battle.h:988` |
| `gBattleMons` | `0x02000420` | `0x220` | EWRAM | `include/battle.h:973` |
| `gPlayerPartyCount` | `0x020342A8` | 1 | EWRAM | `src/pokemon.c:113` |
| `gEnemyPartyCount` | `0x020342A9` | 1 | EWRAM | `src/pokemon.c:114` |
| `gPlayerParty` | `0x02034768` | `0x258` | EWRAM | `src/pokemon.c` |
| `gEnemyParty` | `0x020342B8` | `0x258` | EWRAM | `src/pokemon.c` |
| `gMain` | `0x03005BC0` (source build) / **`0x03005BD8`** (official release, §11.5) | `0x43c` | IWRAM | `src/main.c:67` (`COMMON_DATA struct Main gMain = {0}`) |

The rows in this table are `make hns` symbol values. The party rows and `gMain` are **not** the
official-release addresses — see §2.1 and §11.5.

### 6.2 `gMain.inBattle`: the authoritative "a battle is running" flag

Upstream `struct Main` (`include/main.h`) ends with three bit-fields:

```c
/*0x438*/ u8 state;              // enum MainState
/*0x439*/ u8 oamLoadDisabled:1;
/*0x439*/ u8 inBattle:1;
/*0x439*/ u8 anyLinkBattlerHasFrontierPass:1;
```

DWARF from the tagged headers under the upstream target flags reports
`DW_AT_data_bit_offset: 8649` for `inBattle` with `DW_AT_bit_size: 1`, i.e. **bit 1 of the byte at
struct offset `0x439`**. Preceding members, all ABI VERIFIED from the same probe object:

| Quantity | Value | How it was measured |
|---|---|---|
| `offsetof(struct Main, oamBuffer)` | `0x038` | `offsetof` constant in the probe |
| `sizeof(oamBuffer)` | `0x400` (128 × 8) | `sizeof` in the probe |
| `offsetof(struct Main, state)` | `0x438` | `offsetof` constant in the probe |
| byte holding the bit-field unit | `0x439` | `DW_AT_data_bit_offset 8648/8649` for `oamLoadDisabled`/`inBattle` |
| highest occupied byte | `0x439` (3 bits used) | `DW_AT_bit_size 1` per flag |
| `sizeof(struct Main)` | **`0x43c`** | `sizeof` constant in the probe |
| symbol size emitted for `gMain` | **`0x43c`** | `arm-none-eabi-nm -S` on `pokehns.elf` (`03005bc0 0000043c B gMain`) |

`0x438` is the offset of the last ordinary member (`state`), **not** the size of the struct. The
struct is `0x43c` because the 3-bit flag unit at `0x439` is padded to the struct's 4-byte
alignment. The probe object and the linked `pokehns.elf` agree on `0x43c` exactly. The **struct
layout** (`state` at `0x438`, the flag unit at `0x439`, `inBattle` at bit 1 of it) is therefore
identical in both binaries; only the struct's *base address* differs.

Absolute IWRAM address of the flag byte:

```text
source-build symbol:        0x03005BC0 + 0x439 = 0x03005FF9
official 2.0.5 release:     0x03005BD8 + 0x439 = 0x03006011   <- what DualDex ships
```

> **SUPERSEDED BY §11 RUNTIME VALIDATION** — an earlier revision of this section stated that the
> `0x03005BC0` base "was confirmed against the release ROM at runtime" and concluded that the source
> build and the official release "do **not** differ for `gMain`". **That is withdrawn.**
>
> The earlier confirmation rested on the boot-only probe described in §6.5: the byte at
> `0x03005FF9` was readable on all 20,000 sampled frames and held `0x00`. Readability and a
> constant-zero value do not identify a symbol — any zero-filled IWRAM byte satisfies both. §11.5
> later established, with real input and real battle transitions, that the official release places
> `gMain` at `0x03005BD8`, `+0x18` from the source-build symbol, exactly like the SaveBlock pointer
> triple in §2.2. The release `inBattle` byte is `0x03006011`.
>
> General principle, applied consistently throughout this document:
>
> > **Readable memory and a plausible constant value are not enough to establish symbol identity;
> > release-ROM addresses require semantic correlation with game behaviour.**

Why this flag is the authority, from the tagged source:

| Event | Location | Source line |
|---|---|---|
| set | `CB2_InitBattleInternal()`, immediately after the enemy party is built and `CalculateEnemyPartyCount()` has run | `src/battle_main.c:681` |
| cleared | `ReturnFromBattleToOverworld()` (victory / loss / run paths) | `src/battle_main.c:6087` |
| cleared | `FreeRestoreBattleData()` alongside `ZeroEnemyPartyMons()` | `src/battle_main.c:1886` |
| cleared | link/safari/oak/opponent controllers that exit a battle directly | `src/battle_controller_*.c` |
| cleared | `reload_save.c:22` on save reload | `src/reload_save.c` |

`gMain.inBattle` therefore distinguishes "the battle engine owns the frame" from "an old
`gBattleMons` species word is still lying in EWRAM `.bss`", which is what DualDex used before.

### 6.3 What is authoritative for each question

| Question | Authoritative evidence | Rule applied |
|---|---|---|
| battle active vs inactive | `gMain.inBattle` | clear => `INACTIVE`; unreadable => never `INACTIVE` |
| battle initialised | `gBattlersCount` | only `2` or `MAX_BATTLERS_COUNT` (4) are engine-produced values |
| battle ending | `gBattleOutcome` | non-zero => `ENDING` (teardown has begun; treated as not-presentable) |
| battler side | `gBattlerPositions[b] & BIT_SIDE` (`BIT_SIDE == 1`) | `B_SIDE_PLAYER == 0`, `B_SIDE_OPPONENT == 1` |
| battler position/flank | `gBattlerPositions[b] & BIT_FLANK` (`BIT_FLANK == 2`) | used only for side consistency checks |
| battler -> party slot | `gBattlerPartyIndexes[b]` | the slot itself; never derived from an address |
| absent battlers | `gAbsentBattlerFlags` bit `b` | a set bit means the battler is not present |
| fainted battlers | `gBattleMons[b].hp` (`0x2A`, u16) | `hp == 0` => fainted (reported, not resolved) |
| partner / doubles relationship | `gBattleTypeFlags` bits + `gBattlersCount` | `MULTI`/`TWO_OPPONENTS`/`INGAME_PARTNER`/`LINK` => unsupported shape |
| `sizeof(struct BattlePokemon)` | `gBattleMons` size `0x220 / 4` + ABI probe | 136 |

There is deliberately **no** authoritative source for "which enemy the player picked" in the
globals DualDex reads. That is why doubles degrades to ambiguous (§6.6) instead of guessing.

### 6.4 Active battler -> party slot algorithm (as implemented)

```text
battle active?            <- gMain.inBattle set AND gBattlersCount in {2,4} AND gBattleOutcome == 0
                           AND gBattlerPositions/gBattlerPartyIndexes readable and self-consistent

for each present opponent battler b (position side bit == 1, not in gAbsentBattlerFlags,
                                        gBattleMons[b].species in 1..1999):
    slot = gBattlerPartyIndexes[b]          # u16; must be 0..5
    if slot >= gEnemyPartyCount:            # authoritative enemy bounds
        -> active enemy UNKNOWN             # fail closed, never clamp
    else:
        -> active enemy = slot (HP synced from gBattleMons[b].hp)

if two opponent battlers are present:
    -> active enemy AMBIGUOUS (no slot)

reported alongside the slot:
    battler_index = the loop index b that produced the slot   # NOT opponent_battlers
    opponent_battlers = number of present opponent-side battlers
```

Lifecycle -> `ActiveEnemyState` mapping, which keeps "no opponent" and "cannot tell" apart:

| Lifecycle | `ActiveEnemyState` | Why |
|---|---|---|
| `INACTIVE` | `NONE_ACTIVE` | the engine's own flag says no battle is running |
| `INITIALIZING` | `NONE_ACTIVE` | the engine holds a battle but no opponent is presentable yet |
| `ENDING` | `NONE_ACTIVE` | an outcome is recorded; teardown is under way |
| `UNKNOWN` | `UNKNOWN` | the gate was unreadable, so neither claim is justified |
| `ACTIVE`, one opponent | `SLOT` | authoritative battler -> party slot |
| `ACTIVE`, two opponents | `AMBIGUOUS` | a single-opponent surface must not pick one |
| `ACTIVE`, enemy party unreadable | `UNKNOWN` | a real battle whose enemy side cannot be read |

`AMBIGUOUS` and `SLOT` are only ever produced from a **proven** `ACTIVE` battle. An unreadable gate
over doubles-shaped EWRAM reports `UNKNOWN`, not `AMBIGUOUS`, because the engine has not been shown
to be in a battle at all.

`ActiveEnemyInfo.battler_index` is the actual index into `gBattlerPositions[]` /
`gBattlerPartyIndexes[]` / `gBattleMons[]`. It is taken from the same iteration that established the
slot; it is never derived from `opponent_battlers` (which happens to be `1` in an ordinary single
battle and would therefore mask an error). The player side is resolved the same way: the active
player battler is whichever battler `gBattlerPositions` places on the player side, and that index is
recorded too.

Explicitly **not** used anywhere on that path: `gBattleMons` address arithmetic, species equality,
HP equality, "first living enemy", slot-0 fallback, or a cached prior slot.

The legacy `gBattleMons - 24` derivation still exists as a fallback for layouts that declare no
`battler_party_indexes_offset`, but every use of it now passes through the same validation, so it
can only ever produce an unknown result unless the derived address happens to hold valid data for
that game.

`gEnemyPartyCount` contract:

| `gEnemyPartyCount` | Behaviour |
|---|---|
| `0` | empty enemy party; nothing scanned |
| `1..6` | exactly those slots at `gEnemyParty` (`0x020342B8`) |
| `> 6` | fail closed (no scan, no truncation) |
| corrupt claimed slot | fail closed, all-or-nothing |

The player-party blind-scan fallback is **never** reachable for H&S: `enemy_party_count_offset != 0`
selects the authoritative reader unconditionally.

### 6.4.1 Production battle presence is the lifecycle (no second source of truth)

DualDex previously answered "is a battle running?" for **every** layout with one heuristic:

```c
pokemon_read_battle_presence() -> gBattleMons[0].species is non-zero and < 2000
```

That word is EWRAM `.bss`, so after a battle ends it still holds the previous opponent and the app
would keep `isInBattle == true` with a stale enemy on screen. The new contract for a layout that
declares the authoritative gate:

| Lifecycle | `pokemon_read_battle_presence_gba()` | App meaning |
|---|---|---|
| `ACTIVE` | `1` OBSERVED | battle running |
| `INACTIVE` | `0` NOT_OBSERVED | no battle running |
| `INITIALIZING` | `2` UNKNOWN | starting up; not presentable |
| `ENDING` | `2` UNKNOWN | tearing down; not presentable |
| `UNKNOWN` | `2` UNKNOWN | the authority is unreadable; not evidence either way |

Production path after this change:

```text
CompanionViewModel.pollVerifiedRomMemory()
    -> coreCoordinator.readBattlePresence(gameId)
    -> LibretroHost.nativeReadBattlePresence(gameId)
    -> pokemon_read_battle_presence_gba(dualdex_jni_gba_read, ...)
         -> pokemon_read_battle_lifecycle()          [gMain.inBattle gate]
              ACTIVE     -> PRESENT
              INACTIVE   -> ABSENT
              otherwise  -> UNKNOWN
```

`pokemon_read_battle_presence()` (the reader-less entry point) reports `UNKNOWN` for such a layout
rather than falling back to the species word, so there is exactly one source of truth.

Layouts that declare **no** lifecycle gate — FireRed, Emerald, LeafGreen, Ruby, Sapphire, Ghost
Grey, Radical Red, Unbound — keep the historical EWRAM-only reading unchanged, so no vanilla
behaviour depends on an H&S-only IWRAM symbol.

`pokemon_read_battle_ui_state()` is deliberately **not** changed: it still reports "active battle,
menu state not authoritatively verified" (`5`) from the same EWRAM word, because menu/cursor state
has no authoritative source yet. It is not the presence signal.

### 6.5 Runtime scenario matrix

Runtime runs were performed with the developer tool `tools/hns-runtime-probe/` against the locally
present official 2.0.5 ROM (`sha256 edf76ecf…7679b`, never committed) in the bundled mGBA libretro
core, feeding live emulated memory into the **production** readers.

No legal save file with a party was available at the time of this section, and DualDex's trust
boundary is deliberately closed for H&S, so **no scenario that requires reaching an actual battle
could be driven at runtime**. Those rows are `NOT RUNTIME VERIFIED` — the absence of evidence is
recorded as absence, not as verification.

> **PARTIALLY SUPERSEDED BY §11.** The `Runtime evidence` cells below that read
> "RUNTIME VERIFIED for the inactive case: 20,000 frames, the byte at `0x03005FF9` was readable and
> stayed zero" are **withdrawn as evidence of that address being `gMain.inBattle`**. §11.5 shows the
> release `inBattle` byte is `0x03006011`, so those runs were reading an unrelated zero-filled byte.
> The observation itself (the official ROM stays out of battle during boot and the production
> readers report `INACTIVE`/`ABSENT`) still stands; the address attribution does not. The
> corresponding rows in §11.3 are the current, semantically validated account.

| Scenario | Source evidence | Compiled symbol evidence | Runtime evidence | DualDex behaviour | Confidence |
|---|---|---|---|---|---|
| battle enter | `CB2_InitBattleInternal` sets `gMain.inBattle` (`battle_main.c:681`) after `CalculateEnemyPartyCount()`; `InitBtlControllersInternal` sets `gBattlersCount` (`battle_controllers.c:205-207`) | `gMain` **`0x03005BD8`**+`0x439` bit 1 (release address; `0x03005BC0` is the source-build symbol, not the release address); `gBattlersCount` `0x020000B0` | `NOT RUNTIME VERIFIED` (no save file; no battle reachable) | `INITIALIZING` until the battler set is complete and self-consistent, then `ACTIVE` | SOURCE + COMPILED SYMBOL VERIFIED; runtime NOT YET VERIFIED |
| wild single | `AssignBattlersToParty` + `gBattlersCount = 2`; wild encounters leave `BATTLE_TYPE_TRAINER` clear | `gBattleTypeFlags` `0x020000AC`, `gBattlersCount` `0x020000B0`, `gBattlerPartyIndexes` `0x02000144` | `NOT RUNTIME VERIFIED` | `BATTLE_KIND_WILD_SINGLE`; opponent resolved as `gBattlerPartyIndexes[1]` | SOURCE + COMPILED SYMBOL VERIFIED; runtime NOT YET VERIFIED |
| trainer single | `CB2_InitBattleInternal` builds `gEnemyParty` then `CalculateEnemyPartyCount()` (`battle_main.c:661-665`) | `gEnemyPartyCount` `0x020342A5`, `gEnemyParty` `0x020342B4` (release addresses, §11.5) | `NOT RUNTIME VERIFIED` | `BATTLE_KIND_TRAINER_SINGLE`; enemy party bounded by `gEnemyPartyCount` | SOURCE + COMPILED SYMBOL + SYNTHETIC UNIT VERIFIED (`test_hns_trainer_battle_classification`, `test_hns_trainer_opponent_slot_resolves_from_battler_index`); runtime NOT RUNTIME VERIFIED |
| opponent switch | `gBattlerPartyIndexes[battler]` is rewritten when the send-out completes | `0x02000144 + 2*b` | `NOT RUNTIME VERIFIED` | slot follows the rewritten index; species/HP are never re-matched | SOURCE + COMPILED SYMBOL VERIFIED; SYNTHETIC UNIT VERIFIED for post-switch slot remapping through `gBattlerPartyIndexes`; full voluntary opponent-switch lifecycle remains NOT RUNTIME VERIFIED |
| opponent faint | `FaintClearSetData` resets stages/volatiles; `gBattleMons[b].hp == 0` while the forced switch resolves | `hp` at `BattlePokemon+0x2A` | faint + fail-closed clearing: **RUNTIME VERIFIED** (§11.3); replacement: `NOT RUNTIME VERIFIED` | when HP reaches zero, authoritative opponent slot remains available and is marked `fainted=true`; when engine marks battler absent during replacement window, reader returns `NONE_ACTIVE` with no slot; once replacement is committed through `gBattlerPartyIndexes`, reader resolves new authoritative party slot without retaining old one | SOURCE + COMPILED SYMBOL + SYNTHETIC UNIT VERIFIED (`test_hns_trainer_multi_party_faint_transition`, `test_hns_stale_enemy_slot_cannot_survive_replacement`); faint/clearing runtime VERIFIED in singles (§11.3); multi-mon replacement NOT RUNTIME VERIFIED |
| player switch | same `gBattlerPartyIndexes[battler]` mechanism for the player side | `0x02000144 + 2*b` | single-party slot resolution: **RUNTIME VERIFIED** (§11.3); party switch: `NOT RUNTIME VERIFIED` | the active player battler is whichever battler `gBattlerPositions` puts on the player side; its slot follows `gBattlerPartyIndexes[battler]` | SOURCE + COMPILED SYMBOL + SYNTHETIC UNIT VERIFIED (`test_hns_player_switch_slot_follows_battler_indexes`); partial runtime |
| player faint | `hp == 0` on the active player battler while the party menu replacement resolves | `hp` at `BattlePokemon+0x2A` | `NOT RUNTIME VERIFIED` | `PartySnapshot.active_battler_known` set to `false` and `active_battler_slot` set to `-1` while `hp == 0` and during absent-battler window, until replacement is committed through `gBattlerPartyIndexes` | SOURCE + COMPILED SYMBOL + SYNTHETIC UNIT VERIFIED (`test_hns_player_faint_forces_unknown_until_replacement`, `test_hns_stale_player_slot_cannot_survive_faint`); runtime NOT RUNTIME VERIFIED |
| **stale battle state + `gMain.inBattle == false`** | `gMain.inBattle = FALSE` on exit; `gBattleMons` is EWRAM `.bss` and is not always cleared | `gMain` **`0x03005BD8`**+`0x439` bit 1 (release address; `0x03005BC0` is the source-build symbol, not the release address) | ~~**RUNTIME VERIFIED for the inactive case**~~ (**address attribution SUPERSEDED — see §11.5/§11.6**): with the real ROM the production presence path returned `ABSENT` on every one of 20,000 frames while reading the then-configured byte | production presence reports `NOT_OBSERVED`; the stale species word is ignored | RUNTIME VERIFIED (inactive); the post-battle edge itself NOT RUNTIME VERIFIED |
| battle exit | `gMain.inBattle = FALSE` in `ReturnFromBattleToOverworld` / `FreeRestoreBattleData`; `ZeroEnemyPartyMons()`; `CalculatePlayerPartyCount()` | `gMain` **`0x03005BD8`**+`0x439` bit 1 (release address; `0x03005BC0` is the source-build symbol, not the release address) | ~~**RUNTIME VERIFIED for the pre-battle/overworld case**~~ (**address attribution SUPERSEDED — see §11.5/§11.6**: this run read `0x03005FF9`, which is not the release `inBattle` byte): over 20,000 emulated frames of the official ROM the then-configured byte was readable on every frame and **never** observed set; `gBattlersCount == 0`, `gBattleTypeFlags == 0`, `gBattleOutcome == 0`, `gBattlerPartyIndexes[0..3] == 0`, `gBattleMons[0..3].species == 0`, `gPlayerPartyCount == 0`, `gEnemyPartyCount == 0`; the production readers returned `INACTIVE`, `NONE_ACTIVE`, `enemyParty = 0`, `activeEnemySlot = -1`, `known = false`, and the production **battle-presence** path returned `ABSENT` on every sample, with **0 invariant failures** | enemy party, active slot and production presence are all cleared; no stale opponent can be presented | runtime VERIFIED for the inactive state; the exit *edge* itself NOT RUNTIME VERIFIED |
| doubles | `IsDoubleBattle()` is `gBattleTypeFlags & BATTLE_TYPE_MORE_THAN_TWO_BATTLERS`; `gBattlersCount = 4` | `gBattlersCount` `0x020000B0`, `gBattlerPositions` `0x02000238` | `NOT RUNTIME VERIFIED` | `BATTLE_KIND_DOUBLES` => active enemy `AMBIGUOUS`, no slot, no enemy shown | SOURCE + COMPILED SYMBOL VERIFIED; runtime NOT YET VERIFIED |
| partner / multi | `BATTLE_TYPE_MULTI` (bit 6), `BATTLE_TYPE_INGAME_PARTNER` (bit 22), `BATTLE_TYPE_TWO_OPPONENTS` (bit 15), `BATTLE_TYPE_LINK` (bit 1) | `gBattleTypeFlags` `0x020000AC` | `NOT RUNTIME VERIFIED` — no practical scenario was reachable in this PR | `BATTLE_KIND_MULTI_OR_PARTNER` => active enemy `AMBIGUOUS`, no slot | SOURCE + COMPILED SYMBOL VERIFIED; runtime NOT YET VERIFIED |
| transition windows (init / faint / teardown) | `gBattlersCount` is set after `gMain.inBattle`; `BattleStartClearSetData` clears `gBattleOutcome`/`gAbsentBattlerFlags` at intro start | the whole global set above | runtime VERIFIED only for the boot/intro window, which is consistently `INACTIVE` | `INITIALIZING` / `ENDING` / `UNKNOWN` never expose a slot | SOURCE + COMPILED SYMBOL VERIFIED; partial runtime |

Runtime raw observation (exact command and full output reproducible via
`tools/hns-runtime-probe`):

```text
frames                                 : 20000
observed distinct states               : 1
gMain byte frames sampled              : 20000
gMain byte frames unreadable           : 0
gMain.inBattle readable always         : yes
gMain.inBattle observed true           : no
gMain raw byte values seen             : 0x00
distinct raw byte values               : 1
observed bytes with inBattle bit clear : 1 of 1
production battle presence             : ABSENT on every sample
reader invariant failures              : 0
read after unload                      : rejected (correct)
```

An earlier revision of the probe tracked the raw byte with a 32-bit bitmask (`1u << byte`), which
is undefined behaviour for byte values >= 32, and reported eight distinct values. The tracker is
now a `bool seen[256]` table and the run was repeated. The corrected values above supersede the
earlier ones: on this ROM and build the byte at `0x03005FF9` was readable on all 20,000 sampled
frames and held `0x00` throughout, with the `inBattle` bit clear on every observed frame.

Reading `0x00` on every frame was taken at the time as the expected result and as evidence that the
address was not dead: the flag byte is the same byte that also carries `oamLoadDisabled` and
`anyLinkBattlerHasFrontierPass`, the read path is the same region-checked IWRAM reader that
independently resolved `gSaveBlock1Ptr` in §5.2, and a bogus address would have to be readable
*and* consistently zero for 20,000 frames.

> **That reasoning is superseded.** "Readable and consistently zero" is satisfied by any unused
> zero-filled IWRAM byte, so it cannot establish which symbol the byte belongs to. §11.5 identified
> the release `gMain` at `0x03005BD8` by a **semantic** test instead — the `heldKeys` words there
> track live button input, which a dead byte cannot do. `0x03005FF9` is not the release `inBattle`
> byte.

Bounded conclusion (superseded on the address, retained on the observation): the run confirms the
*inactive* case for the official ROM — it stays out of battle during boot and the production readers
report `INACTIVE`/`ABSENT` throughout. It does **not** confirm that the `inBattle` bit asserts on
battle entry, and it does **not** establish that `0x03005FF9` was the release `inBattle` byte. §11.3
now supplies the battle-entry evidence against the corrected address.

### 6.6 Doubles / partner decision

H&S 2.0.5 does support doubles, and DualDex's battle surface has exactly one opponent concept.
Testing a "selected target" abstraction was considered and rejected for this PR because the
authoritative selected-target state lives behind `gBattleStruct` (a pointer) and
`gBattleStruct->moveTarget[]`, which is a target-selection field that changes per turn, not a
stable "which enemy is active" answer. Rather than invent one, DualDex reports:

```text
doubles / multi / partner -> active enemy AMBIGUOUS, no slot, no enemy shown,
                             notice: "Multiple active enemies"
```

Honest unsupported degradation, never the wrong Pokémon. Issue #1 explicitly allows this.

### 6.7 BattlePokemon stride and field validation

| Field | Compiled offset | Runtime evidence |
|---|---|---|
| `sizeof(struct BattlePokemon)` | 136 (`gBattleMons` `0x220 / 4`; also ABI-probed) | `NOT RUNTIME VERIFIED` as a live double-battler read (no battle reachable); the synthetic suite verifies that an 88-byte stride misreads battler 1, so a wrong stride is detectable |
| `species` | `0x00` | boot-time `0x00` for all four battlers (RUNTIME VERIFIED in the inactive state) |
| `moves` | `0x0C` | ABI VERIFIED |
| `statStages` | `0x18` (`s8[8]`, neutral `6`) | ABI VERIFIED; synthetic `+Atk` / `-Def` / `-Spe` / `+Eva` reads asserted; not observed in a live battle |
| `hp` | `0x2A` (`u16`) | boot-time observed; live in-battle change NOT RUNTIME VERIFIED |
| `level` | `0x2C` | ABI VERIFIED |
| `maxHP` | `0x2E` (`u16`) | ABI VERIFIED |
| `status1` | `0x50` (`u32`) | ABI VERIFIED |
| `ability` | `0x20`, `types` `0x22`, `pp` `0x25`, `item` `0x30`, `personality` `0x4C`, `otId` `0x80` | ABI VERIFIED; no unrelated parser field was changed |

The upstream `/*0xNN*/` offset comments in `struct BattlePokemon` remain stale (they say `hp 0x29`,
`maxHP 0x2D`, `status1 0x4D`); the compiled and runtime-checked values in the table are the ones
DualDex uses.

### 6.8 Cache and stale-state cleanup

| Cache | Cleared on |
|---|---|
| `s_last_battle_lifecycle` | `pokemon_reader_reset()` (ROM load, core reset, unload) |
| `s_cached_enemy_party_offset` | battle-exit edge (`ACTIVE` -> anything else), any failed/rejected enemy read, `pokemon_reader_reset()` |
| enemy party snapshot | rebuilt on every read from `gEnemyPartyCount`; empty outside an active battle |
| active enemy / active player slot | recomputed from battler state on every read; `-1` + `known = false` when unreadable |
| `active_battler_index` (the real resolved battler) | recomputed on every read; `-1` whenever no battler produced a slot |
| production battle presence | derived from the lifecycle on every call; `ABSENT` when the engine's gate is clear, `UNKNOWN` when it is unreadable, never reconstructed from EWRAM |
| native `s_last_active_*` in `dualdex_jni.c` | overwritten on every read; forced to `-1`/unknown when the game has no supported layout or trust is withdrawn |
| Kotlin `_activeEnemyResolution` | reset on battle exit, on trust loss (`clearLiveMemoryObservations`), on ROM session change, and by any negative manual selection |
| player party scan/cache | **unchanged** by this PR (authoritative-count behaviour from #42/#43 is untouched) |

### 6.9 Native API / Kotlin contract

The old shape was `nativeGetActiveEnemyBattlerSlot() -> Int`, where `0` could mean "slot 0" or
"unknown". That ambiguity is removed:

* native: `pokemon_resolve_active_enemy()` returns an `ActiveEnemyState` **plus** a slot, and the
  JNI entry point `nativeResolveActiveEnemy(gameId)` returns
  `[state, battlerIndex, partySlot, opponentBattlers, fainted]`;
* Kotlin: `ActiveEnemyResolution` carries an `ActiveEnemyState` and a **nullable** `partySlot`;
  `hasResolvedSlot` is the only way a UI surface is allowed to show an opponent;
* `nativeGetActiveBattlerSlot` / `nativeGetActiveEnemyBattlerSlot` now return `-1` unless the
  authoritative read succeeded in the same call;
* `nativeReadBattlePresence` calls `pokemon_read_battle_presence_gba()`, so the app's `isInBattle`
  is built from the lifecycle for H&S and from the legacy EWRAM reading only for layouts that
  declare no gate;
* element `[1]` of the `nativeResolveActiveEnemy` tuple is the **actual resolved battler index**
  (an index into `gBattlerPositions[]` / `gBattlerPartyIndexes[]` / `gBattleMons[]`), not the
  opponent battler count, which is element `[3]`.

No broad architecture rewrite was performed (issue #8 is untouched).

---

## 7. Capability verification status

| Capability | Status |
|---|---|
| H&S 2.0.5 ROM identity | RUNTIME VERIFIED (hash + UPS footer lineage) |
| `heart_and_soul.json` `sha256Hashes` | **intentionally empty** — see §7 |
| GBA memory-region capture (IWRAM/EWRAM) | RUNTIME VERIFIED |
| Bounds-checked absolute-address reads | RUNTIME VERIFIED (incl. rejection after unload) + unit tested |
| SaveBlock1 ASLR resolution | RUNTIME VERIFIED |
| SaveBlock1 field offsets | ABI VERIFIED; reader path RUNTIME VERIFIED |
| Party symbol addresses (`gPlayerParty`, `gPlayerPartyCount`) | source build COMPILED SYMBOL VERIFIED (`0x02034768`/`0x020342A8`); **official release RUNTIME VERIFIED at `0x02034764`/`0x020342A4`** (§11.5, §11.6); party content RUNTIME VERIFIED (level 5 Chikorita decoded live) |
| Enemy party symbols (`gEnemyParty`, `gEnemyPartyCount`) | source build COMPILED SYMBOL VERIFIED (`0x020342B8`/`0x020342A9`); **official release RUNTIME VERIFIED at `0x020342B4`/`0x020342A5`** (§11.5, §11.6); live in-battle content RUNTIME VERIFIED (wild opponent decoded during a battle) |
| `BattlePokemon` size / HP / stat-stage offsets | ABI VERIFIED; live in-battle values RUNTIME VERIFIED — species/HP for both sides and a real `6 -> 5` stat-stage transition at offset `0x18` (§11.3) |
| `gBattlerPartyIndexes` explicit address | COMPILED SYMBOL VERIFIED + release address RUNTIME VERIFIED; used as the only active-battler -> party-slot source; live singles faint/teardown RUNTIME VERIFIED; trainer battles, multi-party faint/replacement, player switch, and player faint SYNTHETIC UNIT VERIFIED (`test_hns_trainer_*`, `test_hns_player_*`, `test_hns_stale_*`); live multi-mon switch/replacement NOT RUNTIME VERIFIED |
| Expansion `abilityNum` / `gigantamaxFactor` decoding | ABI VERIFIED + SOURCE VERIFIED; unit tested |
| Expansion nature / shiny semantics | SOURCE VERIFIED; unit tested; UI policy NOT YET VERIFIED |
| H&S 2.0.5 Species / Move Data Pack (`HeartAndSoul205DataPack`) | SOURCE VERIFIED (extracted via `arm-none-eabi-cpp` preprocessor directly from pinned upstream checkout `1f42b74dff0e9fe942419845d040663dd829a973` with zero commercial ROM dependency); unit tested |
| Profile Custom Species Isolation (Ghost Grey vs H&S 500-502) | SOURCE VERIFIED; unit tested |
| H&S 2.0.5 Held Items | NON-AUTHORITATIVE (held items marked unverified in live party presentation pending dedicated item audit) |
| Battle lifecycle (`gMain.inBattle`, `gBattlersCount`, `gBattleOutcome`, `gBattlerPositions`, `gAbsentBattlerFlags`) | COMPILED SYMBOL + ABI VERIFIED; `gMain` release base **corrected to `0x03005BD8`** by runtime evidence (§11.6); battle enter, HP, faint, teardown and exit-edge RUNTIME VERIFIED with 0 invariant violations (§11.3). Trainer battle classification, multi-party faint transition, player switch, and player faint SYNTHETIC UNIT VERIFIED (`test_hns_trainer_*`, `test_hns_player_*`); live trainer/switch/doubles NOT RUNTIME VERIFIED |
| Production battle presence for H&S | `gMain.inBattle` lifecycle only (no `gBattleMons[0].species`); lifecycle-null semantics unit tested; inactive, active, ending and post-exit (stale-state) cases all RUNTIME VERIFIED (§11.3) |
| Active battler index contract (`ActiveEnemyInfo.battler_index`) | SOURCE + unit tested; **RUNTIME VERIFIED** — resolved battler `1` for the opponent and `0` for the player in a live wild battle (§11.3) |
| Active-enemy / active-battler mapping | SOURCE + COMPILED SYMBOL VERIFIED; fail-closed contract unit tested (native + Kotlin); wild battle RUNTIME VERIFIED (`SLOT`, slot `0`, battler `1`) and proven to stay `NONE_ACTIVE`/`-1` while stale `gBattleMons` words remain (§11.3); trainer battle opponent slot resolution, multi-party replacement, player switch, and player faint SYNTHETIC UNIT VERIFIED (`test_hns_trainer_*`, `test_hns_player_*`, `test_hns_stale_*`); live trainer multi-party NOT RUNTIME VERIFIED |
| Battle UI / interactive controls | NOT YET VERIFIED — `battleUiVerified` and `interactiveControlsVerified` remain `false` (unchanged by this PR) |
| H&S maps / regions | NOT YET VERIFIED (explicitly out of scope; #11) |
| H&S calculator correctness | NOT YET VERIFIED (explicitly out of scope; #9) |

---

## 8. Why `sha256Hashes` stays empty

The exact 2.0.5 ROM is present locally and its SHA-256 is recorded above, and the SaveBlock1 /
memory-region reader paths did survive runtime checks. It is nonetheless **not** added to
`heart_and_soul.json` in this phase, because:

* the party, battle and map capabilities that H&S support advertises are still NOT YET VERIFIED, so
  adding the hash would unlock authoritative live-memory reads for a ROM whose party and battle
  interpretation has not been validated against real game state;
* `battleUiVerified` and `interactiveControlsVerified` are false and #1/#11/#9 are unresolved;
* the battle lifecycle work in §6 could not be driven through a live battle at runtime (no legal
  save file), so the battle rows in §6.5 remain NOT RUNTIME VERIFIED.

The trust boundary is unchanged and still requires an exact SHA-256 match, so H&S remains
`RECOGNIZED_UNVERIFIED` and `mayReadLiveMemory` stays false. **The UPS patch digest is never used as
a ROM hash.**

Promotion requires, at minimum: a legal save state with a party, a wild battle, and a trainer battle,
with the observed party/location/battle values compared against known game state.

---

## 9. Discovered blockers for follow-up work

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
6. **[RESOLVED in PR #46] No save file was available**, so every party-, battle- and map-dependent
   capability was unverified. §11 records the resolution: a legal save with a party was produced by
   driving a fresh ROM through normal game progression, and wild-battle enter / HP / faint /
   teardown / exit-edge were captured at runtime against the exact official ROM. What this blocker
   left behind, and what remains for #1, is the *reach* of that save: trainer battles, opponent and
   player switches, player faint replacement, doubles and partner/multi still need a save progressed
   further than Route 29 (§11.7).
7. **Vanilla behaviour note (not a change in this PR's scope).** The legacy
   `gBattlerPartyIndexes = gBattleMons - 24` derivation is retained only for layouts that do not
   declare `battler_party_indexes_offset` (FireRed, Emerald, LeafGreen, Ruby, Sapphire, Ghost Grey,
   Radical Red, Unbound). All reads through it now pass the same validation as the H&S path, so a
   vanilla active-battler slot is reported only when that derived address happens to hold valid
   data. FireRed/Emerald party-discovery and battle regression tests are unchanged and pass. The
   suspected vanilla enemy-count offset issues are still **not fixed** here, as they are not
   required by this PR and lack runtime evidence.

---

## 10. Reproducing this evidence

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

# 4. Struct layout and battle ABI: compile a probe against the tagged headers with the
#    flags in §3. The battle-lifecycle probe template used for this section is:
#      <repo>/tools/hns-runtime-probe/  (source committed; binaries and ROMs are not)
arm-none-eabi-gcc -iquote include -DMODERN=1 -DTESTING=0 -DPOKEMON_HNS -DEMERALD \
  -std=gnu17 -mthumb -mthumb-interwork -O2 -mabi=apcs-gnu -mtune=arm7tdmi -march=armv4t -g \
  -c <probe>.c -o /tmp/probe.o
arm-none-eabi-nm -S --defined-only /tmp/probe.o | sort     # constants
arm-none-eabi-readelf --debug-dump=info /tmp/probe.o       # bitfield placement (gMain.inBattle)

# 5. Runtime battle-state observation against the official 2.0.5 ROM (developer tool, not CI).
#    Requires a locally built mGBA libretro core and a legally obtained ROM. No ROM is shipped.
#    See §11.8 for the save-generation and scenario commands; a scripted run exits non-zero if a
#    required script action fails or any runtime invariant is violated.
cd tools/hns-runtime-probe && ./build.sh
./runtime_battle_probe <mgba_libretro.so> "<legal hns 2.0.5 rom>.gba" --frames 20000

# 6. DualDex regression coverage (no ROM required)
./ci.sh test
```

Most committed regression fixtures are synthetic or source-derived. PR #46 additionally commits two
100-byte **runtime-captured** Pokémon structures read out of EWRAM while the official ROM was
running (`kHnsReleasePlayerParty0`, `kHnsReleaseEnemyParty0`). These are decoded runtime state — not
ROM bytes, not save files, not save states, and not copyrighted game assets — and they are the only
runtime-captured bytes in the repository. No ROM, no ROM-derived data, and no copyrighted content is
committed. The runtime probe is a developer tool: it is not built by `ci.sh`, not shipped in the APK,
and does not add the H&S SHA-256 to any profile.

---

## 11. Runtime battle validation against the official release ROM (issue #1)

**Validation date: 2026-09-15.** This section is additive; nothing above is rewritten. It replaces
the `NOT RUNTIME VERIFIED` rows of §6.5 only where a live run actually produced the transition.

Phase 1 of issue #1 — "no save with a party and reachable battles" — is resolved in this section.

### 11.1 Exact environment

| Item | Value |
|---|---|
| ROM | Pokémon Heart & Soul 2.0.5 (UPS patch applied to Emerald (U)) |
| ROM SHA-256 | `edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b` |
| mGBA core | libretro host build of the bundled `cores/mgba-src` checkout (`e31759b24e7a4e3899285ff720d7b573ac328ae7`), x86-64, BuildID `150660aa40aa7f42f9cde07d0589197f07acb414` |
| Tool | `tools/hns-runtime-probe` (production readers compiled unchanged into the host harness) |
| Save format | plain 128 KiB (`0x20000`) GBA battery save via `RETRO_MEMORY_SAVE_RAM` |

`sha256Hashes` in `heart_and_soul.json` is **still empty**; the hash above is used only as a local
precondition for this evidence.

### 11.2 Save provenance (issue #1 phase 1)

The blocker recorded in §4.4 and §9.6 was that no save with a party existed. It was removed by
driving the official ROM through **normal game progression** with scripted button input:

```text
copyright -> title -> main menu -> H&S challenge menu -> H&S/Oak speech -> naming screen
-> bedroom (mandatory wall-clock setup) -> downstairs (mom dialogue) -> New Bark Town
-> Elm's laboratory -> starter selection (Chikorita) -> nickname -> Elm again -> save
```

* No RAM writes, no cheats, no ROM patching, no save editing. Every step is an ordinary key press.
* The save is written by the game's own save routine (`START` → `SAVE` → `YES`) and then flushed
  from the core's battery RAM to a `.sav`. The resulting file is 131,072 bytes
  (`sha256 c434b491d03473d7fa2a2ff6a9e97275eac105b6bf624255bf1c3d41d24222a3` for the run recorded
  here; the bytes depend on the generated trainer ID, so that digest identifies one local run
  rather than a required value).
* The `.sav`, the ROM and any save state stay outside the repository and are never committed.
* Driver: `tools/hns-runtime-probe/scenarios/00-fresh-rom-to-starter-save.txt`, wrapped by
  `tools/hns-runtime-probe/make-save.sh`. Verified end to end: the freshly generated save was
  loaded back into the same ROM and drove scenarios 10 and 20 to a live wild battle (§11.3).

Two H&S-specific input facts were required and are worth recording:

1. **The challenge menu is exited with `R`, not `A`.** `A` cycles the highlighted option's value
   (the menu never advances), while `R` walks the tabs; `R` on the last tab jumps straight to the
   save-and-continue path (`SwitchTab` in `src/challenge_menu.c`). A pure `A` mash stalls on this
   screen forever, which is why the earlier boot-only runs never left it.
2. **Yes/No prompts default to `NO`.** `CreateYesNoMenu(..., initialCursorPos = 1)` selects `NO`
   for the clock confirmation, and the same `NO` default makes Elm's dialogue loop. Pressing
   `UP` before `A` is therefore required; a blind `A` mash can answer `YES` to the starter prompt
   and still fall back into the refusal loop.

### 11.3 Runtime scenario matrix (replaces the `NOT RUNTIME VERIFIED` rows of §6.5)

Result vocabulary: `PASS`, `IMPLEMENTATION BUG`, `SOURCE/RELEASE LAYOUT MISMATCH`,
`NOT RUNTIME VERIFIED`.

| Scenario | Expected (PR #45 architecture) | Runtime observed | DualDex output | Result |
|---|---|---|---|---|
| A. Overworld baseline | `gMain.inBattle == false`, lifecycle `INACTIVE`, presence `ABSENT`, active enemy `NONE_ACTIVE`, empty enemy surface | `inBattle=false`; `gBattlersCount=0`; `gBattleTypeFlags=0`; `gBattleOutcome=0`; `gBattlerPositions=0,0,0,0`; `gBattleMons` species `0,0,0,0`; `playerPartyCount=1` | `INACTIVE` / `NONE_ACTIVE` / `enemyParty=0` / `slot=-1` / presence `ABSENT` | **PASS** |
| B. Wild battle entry | `ACTIVE` only when `gMain.inBattle` is set; `gBattlersCount == 2`; presence `PRESENT`; one player + one opponent battler; resolved opponent slot from `gBattlerPartyIndexes` | observed `INACTIVE` → `INITIALIZING` (frame 1606, `battlers=0`, positions `255,255,255,255`) → `ACTIVE` (frame 1609, `battlers=2`, `typeFlags=0x4`, positions `0,1`, `partyIndexes[0..1] = 0,0`, species `152,19`) | `ACTIVE` / `WILD_SINGLE` / `PRESENT` / `activeEnemy=SLOT` / `resolvedBattler=1` / `resolvedEnemySlot=0` / `opponentBattlers=1` / `activePlayerSlot=0` | **PASS** |
| C. Wild battle HP update | live HP in `gBattleMons[b].hp` updates and DualDex applies it to the authoritative party slot | opponent HP `13/15` → `8` → `2` → `0` and player HP `20` → `17` across turns, all read from `gBattleMons[b]` (stride 136, `hp` at `0x2A`, player at battler 0 and opponent at battler 1) | player and enemy snapshots follow `gBattleMons[resolvedBattler].hp` for their resolved party slots | **PASS** |
| D. Wild battle exit | `ACTIVE` → `ENDING` → `INACTIVE`; stale `gBattleMons`/`gBattlerPartyIndexes`/`gEnemyPartyCount` cannot keep presence or an active enemy alive | at frame 2878 `gMain.inBattle` clears while `gBattlersCount` **still reads 2**, `gBattlerPositions` **still reads 0,1** and `gBattleMons[0].hp` **still reads 19** | production presence `ABSENT`, lifecycle `INACTIVE`, active-enemy `NONE_ACTIVE` with `slot=-1` on every frame after the clear — the stale words are ignored | **PASS** |
| E. Trainer battle | `BATTLE_TYPE_TRAINER`, `gEnemyPartyCount` bounds, initial active enemy slot | not driven — the first accessible trainer with two Pokémon is well beyond Route 29 (§11.7) | `BATTLE_KIND_TRAINER_SINGLE`; enemy party bounded by `gEnemyPartyCount`; slot follows `gBattlerPartyIndexes[resolvedBattler]` | **SYNTHETIC UNIT VERIFIED** (`test_hns_trainer_battle_classification`, `test_hns_trainer_opponent_slot_resolves_from_battler_index`); runtime **NOT RUNTIME VERIFIED** |
| F. Opponent faint + replacement | fainted opponent never displays a stale previous opponent; replacement resolves to the new exact enemy party index | opponent faint observed: `gBattleMons[1].hp == 0`, `fainted=1`, lifecycle `ENDING`, enemy surface cleared (`enemyParty=0`, `slot=-1`) — the **replacement** half needs a trainer with a second Pokémon | when HP reaches zero, authoritative opponent slot remains available with `fainted=true`; absent-battler replacement window returns `NONE_ACTIVE` with no slot/battler; once replacement is committed, new party slot resolves without retaining old slot | faint + fail-closed clearing: **PASS**; multi-party replacement: **SYNTHETIC UNIT VERIFIED** (`test_hns_trainer_multi_party_faint_transition`, `test_hns_stale_enemy_slot_cannot_survive_replacement`); runtime **NOT RUNTIME VERIFIED** |
| G. Opponent switch without faint | slot follows the rewritten `gBattlerPartyIndexes` | not reachable on a wild-only save | slot follows rewritten index; species/HP are never re-matched | **SYNTHETIC UNIT VERIFIED** for post-switch slot remapping through `gBattlerPartyIndexes`; full voluntary opponent-switch lifecycle remains **NOT RUNTIME VERIFIED** |
| H. Player switch | player side from `gBattlerPositions`, slot from `gBattlerPartyIndexes` | only one party member exists on this save, so no switch was possible | active player slot resolved to `0` with `known=true` throughout | single-party slot resolution: **PASS**; party switch: **SYNTHETIC UNIT VERIFIED** (`test_hns_player_switch_slot_follows_battler_indexes`); runtime **NOT RUNTIME VERIFIED** |
| I. Player faint + forced replacement | `hp == 0`, active player slot withheld during replacement, no stale slot survives | not driven (the starter did not faint in the observed battles) | active player slot in `PartySnapshot` marked unknown (`known=false`, `slot=-1`) while `hp == 0` and during absent-battler window, until replacement is committed through `gBattlerPartyIndexes` | **SYNTHETIC UNIT VERIFIED** (`test_hns_player_faint_forces_unknown_until_replacement`, `test_hns_stale_player_slot_cannot_survive_faint`); runtime **NOT RUNTIME VERIFIED** |
| J. Stat-stage runtime evidence | `BattlePokemon.statStages` at offset `0x18` produces expected stage transitions | observed a live transition `6 → 5` at `statStages[2]` of `gBattleMons[0]` while the message "…" ran, i.e. a real stage drop read at the declared offset (neutral `6`) | stages are read from the declared offset; the reader does not interpret them further | **PASS** (representative, not exhaustive) |
| K. Doubles | `gBattlersCount == 4`, two opponent-side battlers, `AMBIGUOUS`, no slot | not reachable on this save | — | **NOT RUNTIME VERIFIED** |
| L. Partner / multi | `MULTI_OR_PARTNER`, `AMBIGUOUS`, no slot | not reachable on this save | — | **NOT RUNTIME VERIFIED** |

Runtime counts for the two full runs (save regenerated from the fresh ROM, then loaded back):

```text
scenarios/00-fresh-rom-to-starter-save.txt :  frames run 20699   invariant violations 0
scenarios/10-wild-battle-entry.txt         :  frames run 1845    invariant violations 0
scenarios/20-wild-battle-full.txt          :  frames run 3871    invariant violations 0
```

The distinct state rows observed by scenario 20 give the whole lifecycle in one trace
(`eParty` is `gEnemyPartyCount` raw / production, `ae` is the active-enemy state):

```text
    1  INACTIVE      NONE        ABSENT   inB=false  bat=0  eParty=0/0  pParty=0
  209  INACTIVE      NONE        ABSENT   inB=false  bat=0  eParty=1/0  pParty=1   <- stale raw count, production still empty
 1558  INITIALIZING  UNKNOWN     UNKNOWN  inB=true   bat=0  pos=255,255,255,255
 1561  ACTIVE        WILD_SINGLE PRESENT  inB=true   bat=2  pos=0,1  eParty=1/1
 1586  ACTIVE        WILD_SINGLE PRESENT  species=152,19  hp=20,13  ae=SLOT slot=0 bat=1 opp=1
 2156  ACTIVE        WILD_SINGLE PRESENT  hp=20,8
 2299  ACTIVE        WILD_SINGLE PRESENT  hp=20,8   statStages[0][2] 6 -> 5
 2580  ACTIVE        WILD_SINGLE PRESENT  hp=20,2
 2730  ACTIVE        WILD_SINGLE PRESENT  hp=17,2                         <- player side damaged
 2895  ACTIVE        WILD_SINGLE PRESENT  hp=17,0   fainted=1
 3070  ENDING        UNKNOWN     UNKNOWN  eParty=1/0  ae=NONE_ACTIVE slot=-1
 3120  INACTIVE      NONE        ABSENT   inB=false  bat=2  pos=0,1  hp=17,0  <- all stale, all ignored
```

### 11.4 Runtime invariant results

The probe asserts these on **every** frame and exits non-zero on the first violation. Across all
runs of the official ROM (including the ~328,000-frame fresh-ROM save-generation run) the count was
**0 violations**:

| Invariant | Result |
|---|---|
| lifecycle `ACTIVE` only while `gMain.inBattle` is authoritatively true | held on every frame |
| production presence agrees with the lifecycle | held on every frame (incl. the `ENDING` window) |
| no unresolved opponent becomes slot 0 (or any slot) | held — outside a battle the slot stayed `-1` even while `gBattleMons` still held the fainted opponent |
| no stale opponent survives battle exit | held — see scenario D |
| party slot always comes from `gBattlerPartyIndexes[battler]` | held — `resolvedEnemySlot == gBattlerPartyIndexes[resolvedBattler]` on every sampled frame |
| resolved battler index is the actual battler | held — `1` for the opponent, `0` for the player |
| enemy slots stay inside `gEnemyPartyCount` | held |
| doubles never silently select one opponent | not exercised (no doubles battle reached) |
| unreadable authority degrades to `UNKNOWN` | held |

### 11.5 Official-release address revalidation (issue #1 phase 5)

§5.3 warned that a from-source build is not sufficient evidence for a symbol address. This section
acts on that warning for the battle globals. Method: read the address live and require **semantic
correlation**, never proximity.

| Symbol | Config address | Runtime behaviour on the release ROM | Verdict |
|---|---|---|---|
| `gMain.inBattle` | `0x03005BD8 + 0x439` bit 1 | clear while overworld; set (`0x02`) for the whole wild battle; clear again after teardown | **CONFIRMED** |
| `gBattleTypeFlags` | `0x020000AC` | `0x00000000` overworld; `0x00000004` (no `BATTLE_TYPE_TRAINER` bit) during the wild battle | **CONFIRMED** |
| `gBattlersCount` | `0x020000B0` | `0` overworld; `2` during singles | **CONFIRMED** |
| `gBattleOutcome` | `0x0200012C` | `0` while fighting; `1` once the battle resolved | **CONFIRMED** |
| `gBattlerPartyIndexes` | `0x02000144` | `0,0` during singles, matching the player slot 0 and enemy slot 0 actually in use | **CONFIRMED** |
| `gBattlerPositions` | `0x02000238` | `0,1` during singles (battler 0 player side, battler 1 opponent side) | **CONFIRMED** |
| `gAbsentBattlerFlags` | `0x0200030A` | `0x00` for singles | **CONFIRMED** |
| `gBattleMons` | `0x02000420`, stride 136 | species `152,19` / HP `19,13` matching the party Pokémon and the encountered wild Pokémon | **CONFIRMED** |
| `gPlayerPartyCount` | `0x020342A4` | `0` before the starter, `1` afterwards | **CONFIRMED — config was wrong (see §11.6)** |
| `gEnemyPartyCount` | `0x020342A5` | `1` during the wild battle | **CONFIRMED — config was wrong (see §11.6)** |
| `gPlayerParty` | `0x02034764` | the level 5 Chikorita handed out by Elm decodes here (BoxPokemon checksum validates, nickname decodes to `CHIKORITA`, species word `152`) | **CONFIRMED — config was wrong (see §11.6)** |
| `gEnemyParty` | `0x020342B4` | the wild Pokémon decodes here during the battle (checksum validates, species `19`) | **CONFIRMED — config was wrong (see §11.6)** |

No address was moved merely because a nearby alternative also changed; each row above required the
value to correlate semantically with live game state.

### 11.6 Production bugs found and fixed

Two discrepancies were found by runtime evidence. Both are layer 2 in the issue #1 taxonomy —
**source-build symbol vs official-release address** — the same class of error §5.3 already caught
once for `gSaveBlock1Ptr`.

**Discrepancy 1 — `gMain` (`SOURCE/RELEASE LAYOUT MISMATCH`).** The config read
`gMain.inBattle` at `0x03005BC0 + 0x439 = 0x03005FF9`, the address a from-source `make hns` build
reports. On the release ROM that is the wrong word:

* the release `struct Main` begins at **`0x03005BD8`**, the same `+0x18` shift that moves
  `gSaveBlock1Ptr` from `0x030041C0` (local) to `0x030041D8` (release);
* decisive semantic test — `struct Main` begins with `callback1`, which `src/main.c` sets to `NULL`
  and never reassigns, and it carries `heldKeysRaw`/`newKeysRaw` at `0x028`. Holding `RIGHT` set
  `0x03005BD8 + 0x02C` to `0x0010`, `LEFT` to `0x0020`, `START` to `0x0008`, each cleared on
  release. The word at `0x03005BC0` never tracked input and its first word was a ROM pointer, so
  it cannot be `gMain`;
* impact: with the old address the production lifecycle could never report `ACTIVE` on the release
  ROM, so H&S battle presence was permanently `ABSENT`/`UNKNOWN`;
* fix: `main_struct_gba_address = 0x03005BD8` (in-battle byte `0x03006011`).

**Discrepancy 2 — the party group (`SOURCE/RELEASE LAYOUT MISMATCH`).** The config read
`gPlayerPartyCount` at `0x020342A8`, `gPlayerParty` at `0x02034768`, `gEnemyPartyCount` at
`0x020342A9` and `gEnemyParty` at `0x020342B8`. On the release ROM the whole group sits **4 bytes
lower**:

* an EWRAM diff taken before and after `givemon` showed the player party count byte appearing at
  `0x020342A4` (and `0x020342A5` for the enemy count) and the party structure at `0x02034764`;
* three independent checks on the captured bytes confirm the base: the `BoxPokemon` checksum
  validates (`stored 0xC2BA == computed 0xC2BA`), the nickname decodes to `CHIKORITA`, and the
  decrypted substruct word is species `152` (`SPECIES_CHIKORITA`). The compiled base fails the same
  checksum test (`stored 0x50B9 != computed 0x6DB4`);
* during a live wild battle `0x020342B4` decoded the opponent (checksum valid, species `19`) while
  `0x020342B8` did not;
* impact: DualDex read a 4-byte-shifted party for the release ROM and saw an empty enemy party
  during a live battle — the active enemy degraded to `NONE_ACTIVE` even while the battle was
  `ACTIVE`;
* fix: `player_party_offset = 0x34764`, `player_party_count_offset = 0x342A4`,
  `enemy_party_offset = 0x342B4`, `enemy_party_count_offset = 0x342A5`, and `playerPartyOffset` /
  `enemyPartyOffset` in `app/src/main/assets/profiles/heart_and_soul.json`.

Both fixes are guarded by `test_hns_release_rom_party_fixture`, which embeds the party bytes
captured from the running release ROM and asserts (a) they decode at the release addresses and
(b) they do **not** decode at the from-source addresses — so the fixture reproduces the exact
discrepancy before the fix and fails if the addresses drift back.

Both fixes are **fail-closed**: a wrong address produced `UNKNOWN`/empty state, never a wrong
Pokémon. No UI surface displayed a fabricated opponent at any point.

### 11.7 What is still not runtime verified

The following rows in §11.3 remain `NOT RUNTIME VERIFIED` and must not be read as passing live on the official ROM:

* trainer battle (`BATTLE_TYPE_TRAINER`, multi-member enemy party),
* opponent switch without a faint,
* player switch and player faint + forced replacement (needs at least two party members),
* doubles and partner/multi battles.

`battleUiVerified` and `interactiveControlsVerified` remain `false`, and `sha256Hashes` remains
empty, because the trust promotion is a separate #40 step.

#### 11.7.1 Synthetic unit verification of reader contracts

While live runtime execution of multi-party trainer battles and party switches could not be driven on the official ROM with the starter-only save, the production reader logic for these transitions is rigorously verified against the official H&S 2.0.5 EWRAM layout in `native/tests/test_pokemon_reader.c` (7 dedicated tests):

1. `test_hns_trainer_battle_classification`: Verifies `gBattleTypeFlags = BATTLE_TYPE_TRAINER` classifies as `BATTLE_KIND_TRAINER_SINGLE` and `gEnemyPartyCount` (at `0x020342A5`) correctly bounds the enemy party snapshot.
2. `test_hns_trainer_opponent_slot_resolves_from_battler_index`: Verifies that with an enemy party of 2 (`gEnemyPartyCount = 2`), when the active opponent is slot 1 (`gBattlerPartyIndexes[1] = 1`), `pokemon_resolve_active_enemy` resolves slot 1 (battler index 1) without defaulting to slot 0 or relying on species matching.
3. `test_hns_trainer_multi_party_faint_transition`: Verifies the multi-party faint/replacement reader contract: while the fainted opponent still has an authoritative battler mapping, the reader preserves that slot with `fainted=true`; once `gAbsentBattlerFlags` marks the battler absent, the active enemy becomes `NONE_ACTIVE` with no slot; when the replacement is committed through `gBattlerPartyIndexes`, the new party slot becomes authoritative with no stale-slot retention.
4. `test_hns_player_switch_slot_follows_battler_indexes`: Verifies that `pokemon_read_player_party_gba()` populates `PartySnapshot.active_battler_*` from `gBattlerPositions` + `gBattlerPartyIndexes` and follows the new player party slot after the switch is committed.
5. `test_hns_player_faint_forces_unknown_until_replacement`: Verifies that when the active player Pokémon faints (`hp = 0`) and during the absent-battler replacement window, `pokemon_read_player_party_gba()` sets `PartySnapshot.active_battler_known = false` and `PartySnapshot.active_battler_slot = -1`; once replaced (`gBattlerPartyIndexes[0] = 1`, `hp > 0`), the new slot is recognized with updated HP.
6. `test_hns_stale_enemy_slot_cannot_survive_replacement`: Verifies that stale `gBattleMons` data or previous slot information cannot leak into subsequent opponent evaluations.
7. `test_hns_stale_player_slot_cannot_survive_faint`: Verifies that cached player party slot indices cannot persist after a player Pokémon faints.

All 7 tests run under AddressSanitizer and UndefinedBehaviorSanitizer with zero errors (native test suite: 62 passed, 0 failed).

#### 11.7.2 Analysis of game progression blockers for live runtime validation

Reaching a multi-party trainer battle and achieving player party switching legally on the official H&S 2.0.5 ROM was investigated via upstream source analysis (`pokehns-expansion`):

* **Starter save inventory**: The player starts with 0 Poké Balls in `00-fresh-rom-to-starter-save.txt`.
* **Poké Ball availability**:
  - The Cherrygrove Mart clerk does not sell Poké Balls until Professor Elm's errand is complete (`VAR_NEWBARK_TOWN_STATE >= 5`).
  - There are no ground item Poké Balls on Route 29 or Cherrygrove City.
  - Obtaining Poké Balls requires: traversing Route 29 to Cherrygrove City, heading north along Route 30 to Mr. Pokémon's house, receiving the Mystery Egg from Mr. Pokémon, walking back south to Cherrygrove, battling the rival (Passerby Boy / Silver), returning to New Bark Town to deliver the Egg to Elm, and talking to Elm's aide to receive 5 Poké Balls.
* **Trainer progression gates on Route 30**:
  - The first trainers with multi-Pokémon parties are Youngster Mikey (Hoothoot Lv2, Sentret Lv4) and Bug Catcher Don (Ledyba Lv3, Spinarak Lv3) on Route 30.
  - Access to Route 30 north of Cherrygrove is physically blocked by two battling NPCs until the Mystery Egg has been delivered to Elm.
* **Scope**: Driving the official ROM from Elm's lab through Cherrygrove, Mr. Pokémon's house, the rival battle, back to New Bark Town, catching a wild Pokémon to obtain a 2-Pokémon player party, and navigating to Route 30 requires a complex multi-stage script traversing several maps and cutscenes.

Consequently, while the reader contract is fully **SYNTHETIC UNIT VERIFIED**, live runtime execution of multi-party trainer battles remains **NOT RUNTIME VERIFIED** until a legally progressed Route 30 save is created in a future task.

### 11.8 Reproducing this section

```bash
cd tools/hns-runtime-probe && ./build.sh

# Phase 1: produce a legal save from a fresh ROM (normal progression, ~5 minutes).
./make-save.sh <mgba_libretro.so> "<legal hns 2.0.5 rom>.gba" "$HOME/hns205.sav"

# Scenarios. Each run fails (non-zero exit) on any invariant violation.
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/hns205.sav" --script scenarios/10-wild-battle-entry.txt
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/hns205.sav" --script scenarios/20-wild-battle-full.txt

# Deterministic regression coverage (no ROM, no save).
cd ../.. && ./ci.sh test
```
