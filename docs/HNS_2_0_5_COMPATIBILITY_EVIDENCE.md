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

## 0. Current status (read this first)

**Last updated by:** the Gap C4b slice (issues #9, #40), branched
from `17a2b0476bc25d996266e85ac30cfc84b966f709` (the PR #65 merge).

| Area | State |
|---|---|
| Exact ROM identity / trust gate | RUNTIME VERIFIED; `sha256Hashes` still **intentionally empty** (§1.2, §8) |
| SaveBlock1 / party / enemy / battle symbols | RUNTIME VERIFIED on the official release ROM (§11.5) |
| Wild + trainer battle lifecycle, switches, faints | RUNTIME VERIFIED (Scenarios 40-44, §11.9) |
| Opponent voluntary switch without a faint | RUNTIME VERIFIED (Scenario 44, §11.10); the 44 fixture chain (Scenario 34) did not reproduce in this slice's environment — see §14.5 |
| Live battler effective ability + effective types (**#9 slice**) | **RUNTIME VERIFIED (Scenarios 20 and 42, §14)** through the production reader; wired into calculator participant preparation with active-slot validation (§16) |
| Maps / multi-region location routing (**#11**) | **SOURCE VERIFIED + unit tested**; 3 Johto runtime checkpoints RUNTIME VERIFIED; cross-region transitions and app/UI NOT YET VERIFIED (§12) |
| Map screen presentation | NOT YET APP/UI VERIFIED (§12.8) |
| Calculator correctness (#9, #40 Gap C4b) | **SOURCE VERIFIED + HOST VERIFIED + unit tested (partial C4b slice, PARTIAL / OPEN; no official-ROM result validation)** for damage arithmetic, stat stages, badge reader, ability, and held-item capability: H&S 2.0.5 calculations consume authoritative runtime abilities, current held items, live battle stats (`0x02..0x0A`), stat stages (`0x18`), and SaveBlock1 badge boosts (`0x1A98`, `0x1A99`) with active-slot validation; dedicated `calculateHnsDamage` QuickJS engine achieves 100% arithmetic parity across all 16 rolls with the native C oracle for neutral, STAB, crits, stages, badge boosts, weather, screens, raw stats, and explicit doubles target counts. Production H&S requests remain fail-closed: manual / out-of-battle badge applicability is unspecified (`BADGE_BOOST_NOT_MODELLED`), Doubles runtime target count is unobserved (`HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`), and dynamic move type / transient state / battle stat words are unobserved (`HNS_LIVE_BATTLE_STATE_NOT_MODELLED`). Unsupported mechanics, unmodelled items/moves, out-of-range stages, unmodelled weather, and active randomizers remain strictly fail-closed. See [HNS_2_0_5_CALCULATOR_CAPABILITY.md](HNS_2_0_5_CALCULATOR_CAPABILITY.md) |
| `battleUiVerified` / `interactiveControlsVerified` | still `false`, unchanged |

Sections 1-11 are the historical record of the memory/layout phase and the battle-lifecycle phase,
and are preserved as written. **§12 is the authority for maps and locations**; where an earlier map
statement conflicts with §12, §12 wins. §7 now carries the updated map rows.

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
| `gSaveblock3` | `0x0200921C` | `0x34` | `0x921C` — see the challenge-settings section (§13): the **release ROM's** `gSaveblock3` is `0x02009218`, 4 bytes lower, the same −4 shift §11.6 found for the party group; `0x0200921C` is the from-source build's value |
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
| `gSaveBlock3Ptr` | `0x03000178` | `0x03000178` | `0x03000178` — runtime-observed (challenge-settings reader, §13); the pointer's value is `0x02009218` |
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
| `item` | `0x30` | `0x2F` | — | **ABI VERIFIED; production reader reads offset `0x30`, width `2`, ID domain `0..900` (Gap C3)** |
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
| H&S battle lifecycle *in a live battle* (enter, wild, trainer, switch, faint, exit edge, doubles) | Wild battle enter/HP/faint/exit RUNTIME VERIFIED (§11.3); trainer battle entry, opponent faint replacement, player voluntary switch, player faint with forced replacement RUNTIME VERIFIED (§11.3, §11.9); doubles and partner/multi still NOT RUNTIME VERIFIED (tracked in #1) |
| H&S battle lifecycle in the inactive/overworld state | RUNTIME VERIFIED — 20,000 frames of the official ROM: `INACTIVE`/`NONE_ACTIVE` on every sample, 0 invariant failures (§6.5) |
| Battle UI / interactive controls | NOT YET VERIFIED — `battleUiVerified` and `interactiveControlsVerified` remain `false` |
| `gSaveblock3` exact EWRAM address | NOT YET VERIFIED (a 4-byte discrepancy was observed between builds; DualDex does not read it) |
| H&S `gEnemyPartyCount` runtime consumption | RESOLVED in this PR — `0x342A9` is now the sole authority for the enemy party (`0` -> empty, `1..6` -> exact bounds, `>6` -> fail closed, corrupt slot -> fail closed) and the player-party blind scan is unreachable for H&S. The *values* it takes during a live battle remain NOT RUNTIME VERIFIED |
| Which nature the party UI should display | NOT YET VERIFIED |
| H&S map group/map number semantics | SUPERSEDED by §12 — SOURCE VERIFIED and unit tested for the exact 2.0.5 build; cross-region runtime transitions still pending (§12.8) |

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
| opponent faint | `FaintClearSetData` resets stages/volatiles; `gBattleMons[b].hp == 0` while the forced switch resolves | `hp` at `BattlePokemon+0x2A` | faint + fail-closed clearing: **RUNTIME VERIFIED** (§11.3); replacement: **RUNTIME VERIFIED** (Scenario 41) | when HP reaches zero, authoritative opponent slot remains available and is marked `fainted=true`; when engine marks battler absent during replacement window, reader returns `NONE_ACTIVE` with no slot; once replacement is committed through `gBattlerPartyIndexes`, reader resolves new authoritative party slot without retaining old one | SOURCE + COMPILED SYMBOL + SYNTHETIC UNIT VERIFIED (`test_hns_trainer_multi_party_faint_transition`, `test_hns_stale_enemy_slot_cannot_survive_replacement`); faint/clearing runtime VERIFIED in singles (§11.3); multi-mon replacement RUNTIME VERIFIED (Scenario 41) |
| player switch | same `gBattlerPartyIndexes[battler]` mechanism for the player side | `0x02000144 + 2*b` | single-party slot resolution: **RUNTIME VERIFIED** (§11.3); party switch: **RUNTIME VERIFIED** (Scenario 42) | the active player battler is whichever battler `gBattlerPositions` puts on the player side; its slot follows `gBattlerPartyIndexes[battler]` | SOURCE + COMPILED SYMBOL + SYNTHETIC UNIT VERIFIED (`test_hns_player_switch_slot_follows_battler_indexes`); RUNTIME VERIFIED (Scenario 42) |
| player faint | `hp == 0` on the active player battler while the party menu replacement resolves | `hp` at `BattlePokemon+0x2A` | **RUNTIME VERIFIED** (§11.9): `hp=0` at frame 8150 with `gAbsentBattlerFlags == 0x00`, production fail-closed on the same frame and for the whole window, commit at frame 8747 | `PartySnapshot.active_battler_known` set to `false` and `active_battler_slot` set to `-1` while `hp == 0`, until replacement is committed through `gBattlerPartyIndexes` | SOURCE + COMPILED SYMBOL + SYNTHETIC UNIT VERIFIED (`test_hns_player_faint_forces_unknown_until_replacement`, `test_hns_stale_player_slot_cannot_survive_faint`) + RUNTIME VERIFIED (Scenario 43); the runtime fail-closed mechanism is `hp == 0`, not `gAbsentBattlerFlags` |
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
| `gBattlerPartyIndexes` explicit address | COMPILED SYMBOL VERIFIED + release address RUNTIME VERIFIED; used as the only active-battler -> party-slot source; live singles faint/teardown RUNTIME VERIFIED; trainer battle entry, multi-party opponent replacement, player voluntary switch and player faint with forced replacement RUNTIME VERIFIED (Scenarios 40-43, §11.9); opponent voluntary switch without a faint still NOT RUNTIME VERIFIED; synthetic contracts in `test_hns_trainer_*`, `test_hns_player_*`, `test_hns_stale_*` |
| Expansion `abilityNum` / `gigantamaxFactor` decoding | ABI VERIFIED + SOURCE VERIFIED; unit tested |
| Expansion nature / shiny semantics | SOURCE VERIFIED; unit tested; UI policy NOT YET VERIFIED |
| H&S 2.0.5 Species / Move Data Pack (`HeartAndSoul205DataPack`) | SOURCE VERIFIED (extracted via `arm-none-eabi-cpp` preprocessor directly from pinned upstream checkout `1f42b74dff0e9fe942419845d040663dd829a973` with zero commercial ROM dependency); unit tested |
| Profile Custom Species Isolation (Ghost Grey vs H&S 500-502) | SOURCE VERIFIED; unit tested |
| H&S 2.0.5 Held Items | **SOURCE VERIFIED** — exact `enum Item` domain (`ITEM_ID_MAX = 900`, `ITEMS_COUNT = 901`) and every `gItemsInfo` symbol/name/`holdEffect`/`holdEffectParam` generated from the pinned `src/item.c` and source-checked byte-for-byte (`tools/hns-items/generate_hns_items.py --verify`). **ABI VERIFIED** — `BattlePokemon.item` offset `0x30`, width `2`, domain via the compiled ARM probe (`native/src/hns_battle_pokemon_layout_gen.h`). **UNIT/HOST VERIFIED** — native observation (`test_pokemon_reader.c`), Kotlin identity/capability (`Hns205ItemCatalogueTest.kt`, `HnsItemRegistryTest.kt`, `CalcHnsItemTest.kt`), generator tests, and the QuickJS no-op/name-safety contract (`test_js_calc.c:check_gap_c3_items`). The current-battle-item-over-stored-party-item precedence and faint-window clearing are **synthetic-fixture UNIT VERIFIED**. Reading `gBattleMons[battler].item` live on the official ROM is **NOT YET RUNTIME VERIFIED** |
| Battle lifecycle (`gMain.inBattle`, `gBattlersCount`, `gBattleOutcome`, `gBattlerPositions`, `gAbsentBattlerFlags`) | COMPILED SYMBOL + ABI VERIFIED; `gMain` release base **corrected to `0x03005BD8`** by runtime evidence (§11.6); battle enter, HP, faint, teardown and exit-edge RUNTIME VERIFIED with 0 invariant violations (§11.3). Trainer battle classification, opponent replacement, player switch and player faint with forced replacement RUNTIME VERIFIED (Scenarios 40-43, §11.9); doubles/partner-multi and opponent voluntary switch NOT RUNTIME VERIFIED |
| Production battle presence for H&S | `gMain.inBattle` lifecycle only (no `gBattleMons[0].species`); lifecycle-null semantics unit tested; inactive, active, ending and post-exit (stale-state) cases all RUNTIME VERIFIED (§11.3) |
| Active battler index contract (`ActiveEnemyInfo.battler_index`) | SOURCE + unit tested; **RUNTIME VERIFIED** — resolved battler `1` for the opponent and `0` for the player in a live wild battle (§11.3) |
| Active-enemy / active-battler mapping | SOURCE + COMPILED SYMBOL VERIFIED; fail-closed contract unit tested (native + Kotlin); wild battle RUNTIME VERIFIED (`SLOT`, slot `0`, battler `1`) and proven to stay `NONE_ACTIVE`/`-1` while stale `gBattleMons` words remain (§11.3); trainer battle opponent slot resolution and multi-party replacement RUNTIME VERIFIED (Scenarios 40/41); player switch and player faint with forced replacement RUNTIME VERIFIED (Scenarios 42/43, §11.9); doubles/partner-multi NOT RUNTIME VERIFIED |
| Battle UI / interactive controls | NOT YET VERIFIED — `battleUiVerified` and `interactiveControlsVerified` remain `false` (unchanged by this PR) |
| H&S maps / regions (#11) | **SOURCE VERIFIED** — mapGroup/mapNum identity and region for the exact 2.0.5 build are generated from the pinned upstream checkout (560 locations, 120 sections) and cross-checked against upstream source by an independent oracle test; location routing is fail-closed and unit tested (§12) |
| H&S Johto/Kanto region-map canvas | **SOURCE VERIFIED** — canvas geometry is generated from the pinned H&S `sRegionMapSections_Johto` / `_Kanto` layout grids; the legacy hand-written canvas shipped 61 coordinates that match no H&S layout (§12.2) |
| H&S location reads at runtime | **RUNTIME VERIFIED (3 checkpoints)** — New Bark Town `0/0`, Route 30 `0/12`, Violet City `0/2`, decoded by the production reader on the official ROM and matching the pinned table (§12.6). Johto/Kanto/Sinjoh/Alola *transitions* are still NOT RUNTIME VERIFIED |
| H&S Map screen presentation | **NOT YET APP/UI VERIFIED** — no on-device run of the Map tab against 2.0.5 was performed for this PR |
| H&S calculator correctness | **SOURCE VERIFIED + unit tested** for the capability policy (#9). The mechanics inventory and provenance are in [HNS_2_0_5_CALCULATOR_CAPABILITY.md](HNS_2_0_5_CALCULATOR_CAPABILITY.md). H&S calculations are **refused**, not approximated: `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type` and `tx_Random_TypeEffectiveness` can change the damage rule itself and DualDex reads none of `SaveBlock3.challengeSettings`. H&S golden damage fixtures therefore do not exist yet, and no H&S number is presented. This also holds the empty `sha256Hashes` line below: adding the exact 2.0.5 hash would still not make an H&S damage result verified |

---

## 8. Why `sha256Hashes` stays empty

The exact 2.0.5 ROM is present locally and its SHA-256 is recorded above, and the SaveBlock1 /
memory-region reader paths did survive runtime checks. It is nonetheless **not** added to
`heart_and_soul.json` in this phase, because:

* party and battle capabilities that H&S support advertises are still NOT YET VERIFIED, so adding
  the hash would unlock authoritative live-memory reads for a ROM whose party and battle
  interpretation has not been validated against real game state everywhere it matters. The
  *location* reader is now runtime-checked at three Johto points (§12.6), but cross-region
  transitions, Sinjoh/Alola and the Map screen itself are not (§12.8);
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
   - Held items have an exact source-derived identity catalogue for the calculator (Gap C3: `Hns205ItemCatalogue` + numeric-ID `HnsItemRegistry`), and current battle items are read from `gBattleMons[battler].item`; the live party presentation display was not changed by this slice and still does not use that catalogue.
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
   left behind, and what remained for #1, was the *reach* of that save: doubles and partner/multi
   still need a save progressed further than Route 29. Earlier work also provisionally believed that
   no usable voluntary-switch target existed on early Route 30; **Scenario 44 subsequently disproved
   that**, using Bug Catcher Don (see §11.10), so the single-battle lifecycle is now complete for the
   rows §11.7 tracks. Trainer battles, player switches, the player faint with forced replacement and
   the opponent voluntary switch were closed by Scenarios 40-45 (§11.9, §11.10). Doubles, partner and
   multi battles remain **NOT RUNTIME VERIFIED**.
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
committed. The runtime probe has two modes with different standing. Its **runtime/emulator mode** is
developer-only: it needs a ROM and an mGBA core, is never run by CI, and does not add the H&S SHA-256
to any profile. Its **pure selftest mode** (`--selftest`, no ROM, no core, no environment) is part of
the canonical gate: `ci.sh test` compiles `runtime_battle_probe.c` into
`native/build/runtime_battle_probe_selftest` and runs it, alongside the production-reader suite.

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
| E. Trainer battle | `BATTLE_TYPE_TRAINER`, `gEnemyPartyCount` bounds, initial active enemy slot | observed Youngster Mikey trainer battle: `inBattle=true`, `battlers=2`, `typeFlags=0x0000000C` (`BATTLE_TYPE_TRAINER` bit 3 set), positions `0,1`, player slot 0 (Chikorita Lv6), enemy slot 0 (Hoothoot Lv2 species 163 HP 14/14, `eParty=2/2`, `pParty=2/2`) | `ACTIVE` / `TRAINER_SINGLE` / `PRESENT` / `activeEnemy=SLOT` / `resolvedBattler=1` / `resolvedEnemySlot=0` / `opponentBattlers=1` / `activePlayerSlot=0` | **PASS** (Scenario 40) |
| F. Opponent faint + replacement | fainted opponent never displays a stale previous opponent; replacement resolves to the new exact enemy party index | observed Mikey's Hoothoot KO'd (`hp=25,0`, `fainted=1`); absent-battler replacement window observed (`ae=NONE_ACTIVE, slot=-1, bat=-1, opp=0, fnt=0`); replacement Sentret (slot 1, species 161, HP 16/16) entry observed (`idx=0,1,0,0`, `ae=SLOT, slot=1, bat=1, opp=1, fnt=0`). Hardened Scenario 41 executably enforces all 4 phases via `await-enemy-replacement 0 1 3500` (Phase A: old slot active; Phase B: old slot fainted; Phase C: absent battler window `NONE_ACTIVE`/`slot=-1`; Phase D: replacement slot active with new species and HP) | when HP reached zero, authoritative opponent slot was preserved with `fainted=true`; absent-battler replacement window returned `NONE_ACTIVE` with no slot; once replacement entered, new party slot resolved to slot 1 without retaining old slot 0 | **PASS** (Scenario 41) |
| G. Opponent switch without faint | slot follows the rewritten `gBattlerPartyIndexes` | observed with Bug Catcher Don (Ledyba Lv3 lead / Spinarak Lv3 bench, Route 30): `gBattlerPartyIndexes[1]` went `0 -> 1` while Ledyba was still alive in enemy party slot 0 (`hp>0`), production resolved `activeEnemy=SLOT slot=1` species 167 (Spinarak) on the commit frame, and the battle stayed `ACTIVE`. An earlier note here claimed this was "not reachable on early Route 30 AI"; that was disproven (see §11.10) | slot follows the rewritten index; species/HP are never re-matched | **PASS** (Scenario 44, §11.10) — RUNTIME VERIFIED for the single-battle lifecycle |
| H. Player switch | player side from `gBattlerPositions`, slot from `gBattlerPartyIndexes` | observed during active trainer battle with 2-Pokémon party (Chikorita slot 0, Hoothoot slot 1): normal controller input navigated battle action menu to POKÉMON (cursor 2), opened party menu (`gPartyMenu` at `0x020341FC`), selected slot 1 via D-pad DOWN (`slotId` changed `0 -> 1`), confirmed SHIFT sub-menu, returned to battle; `gBattlerPartyIndexes[0]` updated `0 -> 1`, battler 0 species updated `152 -> 163`, HP `25 -> 14` | `activePlayerSlot` transitioned `0 -> 1`, `playerKnown=true`, `PartySnapshot.active_battler_slot = 1`, species and HP updated cleanly | **PASS** (Scenario 42) |
| I. Player faint + forced replacement | `hp == 0`, active player slot withheld during replacement, no stale slot survives | observed with the 2-Pokémon party (Chikorita slot 0, Hoothoot slot 1) against Youngster Mikey: Chikorita attacked only with the non-damaging Growl and reached `hp=0` at frame 8150 while `absent=0x00` and `indexes=0,0,0,0`; the production surface was fail-closed (`known=false`, `slot=-1`) on that same frame and for the entire replacement window; the ROM then showed the ordinary in-battle party menu (`gPartyMenu` `slotId` `0 -> 1` after D-pad DOWN) and two ordinary `A` presses on slot 1 committed the replacement at frame 8747 (`indexes=1,0,0,0`, species `163`, HP `14/14`) | `activePlayerSlot` `0 -> (unknown, slot -1) -> 1`; `PartySnapshot.active_battler_slot` equalled `gBattlerPartyIndexes[0]` on the commit frame and on every frame after it; the opponent was never damaged and never left slot 0 | **PASS** (Scenario 43, §11.9) |
| J. Stat-stage runtime evidence | `BattlePokemon.statStages` at offset `0x18` produces expected stage transitions | observed a live transition `6 → 5` at `statStages[2]` of `gBattleMons[0]` while the message "…" ran, i.e. a real stage drop read at the declared offset (neutral `6`) | stages are read from the declared offset; the reader does not interpret them further | **PASS** (representative, not exhaustive) |
| K. Doubles | `gBattlersCount == 4`, two opponent-side battlers, `AMBIGUOUS`, no slot | not reachable on this save | — | **NOT RUNTIME VERIFIED** |
| L. Partner / multi | `MULTI_OR_PARTNER`, `AMBIGUOUS`, no slot | not reachable on this save | — | **NOT RUNTIME VERIFIED** |

Runtime counts for the legal progression and battle verification runs:

```text
scenarios/00-fresh-rom-to-starter-save.txt :  frames run 20699   invariant violations 0
scenarios/10-wild-battle-entry.txt         :  frames run 1845    invariant violations 0
scenarios/20-wild-battle-full.txt          :  frames run 3871    invariant violations 0
scenarios/30-starter-to-egg.txt            :  frames run ~11500  invariant violations 0
scenarios/31-egg-to-pokeballs.txt          :  frames run ~14500  invariant violations 0
scenarios/32-catch-second-party-member.txt :  frames run ~3500   invariant violations 0
scenarios/33-route30-trainer-ready.txt     :  frames run ~9800   invariant violations 0
scenarios/40-trainer-battle-entry.txt      :  frames run 1579    invariant violations 0
scenarios/41-opponent-replacement.txt      :  frames run 3658    invariant violations 0
scenarios/42-voluntary-switch.txt          :  frames run 2321    invariant violations 0
scenarios/43-player-faint-forced-replacement.txt : frames run ~8.6k  invariant violations 0
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
| `gBattleControllerExecFlags` | `0x02000300` | bit 0 is cleared when waiting for controller input (action selection, move selection, party menu) and set while executing animations/scripts | **CONFIRMED** |
| `gPartyMenu` | `0x020341FC` | base structure for in-battle party menu; `menuType/layout` at `+0x08 = 0x02034204`, `slotId` cursor at `+0x09 = 0x02034205` transitions `0 -> 1` on D-pad DOWN, `action` at `+0x0B = 0x02034207`. Contrast with `0x020341F8`, which is the EWRAM storage for the file-static `sPartyMenuInternal` pointer (`static EWRAM_DATA struct PartyMenuInternal *sPartyMenuInternal = NULL;`). | **CONFIRMED** |
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

**Discrepancy 3 — `gBattleControllerExecFlags` address and input wait states.** The source build places `gBattleControllerExecFlags` at `0x02000304`, while the release binary places it at **`0x02000300`** (4 bytes earlier, matching the general EWRAM shift observed across the battle and party groups). Crucially, upstream battle controller logic sets bit 0 of `gBattleControllerExecFlags` when the controller task is actively executing animations or script commands, and *clears* bit 0 when the battle controller is idling waiting for player controller input (`bcmd == 17` Action Selection, `bcmd == 19` Move Selection, `bcmd == 18` Yes/No Box, and `bcmd == 21` Party Menu). Probing routines that require `(exec & 1)` during user-input states will fail to recognize that the game is awaiting input.

**Discrepancy 4 — `gPartyMenu` layout disambiguation (`0x020341FC` vs `0x020341F8`).** In-battle party switching requires tracking the party menu cursor to select a replacement Pokémon. Candidate A (`0x020341F8`) corresponds to the EWRAM storage for the file-static `sPartyMenuInternal` pointer (`static EWRAM_DATA struct PartyMenuInternal *sPartyMenuInternal = NULL;`) in `src/party_menu.c`. Probing offsets relative to `0x020341F8` (such as `+0x09 = 0x02034201`) sampled across the pointer and into the early fields of `gPartyMenu`, leaving them unaffected by cursor navigation. Candidate B (**`0x020341FC`**) corresponds to the exported `gPartyMenu` struct (`struct PartyMenu`), where `menuType`/`layout` sits at `+0x08 = 0x02034204`, `slotId` sits at `+0x09 = 0x02034205`, and `action` sits at `+0x0B = 0x02034207`. Live semantic correlation during Scenario 42 confirmed this conclusively: upon entering the party menu, `0x02034205` read `0` (slot 0, Chikorita); after pressing D-pad DOWN, `0x02034205` transitioned to `1` (slot 1, Hoothoot), whereas Candidate A remained unchanged at `0`.

### 11.7 What is still not runtime verified

The following rows in §11.3 remain `NOT RUNTIME VERIFIED` and must not be read as passing live on the official ROM:

* doubles and partner/multi battles.

Multi-party trainer battles (`BATTLE_TYPE_TRAINER`), opponent faint with replacement by a subsequent party member, in-battle player party switching, the player's own faint with the ROM's forced replacement (§11.9), and — since §11.10 — the opponent's own **voluntary** switch without a faint are now fully **RUNTIME VERIFIED** against the official release ROM. Notably, Scenario 41 executably enforces the complete 4-phase opponent replacement state machine (`await-enemy-replacement 0 1 3500`), proving strict sequential progression through Phase A (old mon active), Phase B (old mon fainted), Phase C (absent battler window with `NONE_ACTIVE`), and Phase D (replacement mon active with new party slot, species, and HP); Scenario 43 enforces the equivalent 4-phase player-side transition (`await-player-forced-replacement 0 1 40000`).

`battleUiVerified` and `interactiveControlsVerified` remain `false`, and `sha256Hashes` remains
empty, because the trust promotion is a separate #40 step.

#### 11.7.1 Synthetic unit verification of reader contracts

All reader contracts remain rigorously guarded by synthetic unit tests in `native/tests/test_pokemon_reader.c` (62 passing tests), running under AddressSanitizer and UndefinedBehaviorSanitizer.

The opponent-side slot remapping that a voluntary opponent switch depends on — a rewritten
`gBattlerPartyIndexes[battler]` becoming the authoritative slot with no residue of the previous one —
is covered by the **opponent** tests `test_hns_trainer_multi_party_faint_transition`,
`test_hns_stale_enemy_slot_cannot_survive_replacement` and
`test_hns_trainer_opponent_slot_resolves_from_battler_index`. Those are post-faint replacement
tests, not a voluntary switch, so they do not make row G of §11.3 runtime verified:
**opponent voluntary switch without a faint is now runtime verified by Scenario 44 (§11.10)**, so the synthetic tests are corroboration rather than the only support. The player-side switch
test (`test_hns_player_switch_slot_follows_battler_indexes`) is a different code path on a different
side of the field and is not evidence for the opponent one.

The player faint and forced-replacement contracts are guarded by
`test_hns_player_faint_forces_unknown_until_replacement` and
`test_hns_stale_player_slot_cannot_survive_faint` (`native/tests/test_pokemon_reader.c`), and by the
pure state-machine tests that drive the same trackers the runtime commands use
(`player_replacement_*` and `vsw_*` / opponent voluntary switch in
`tools/hns-runtime-probe/runtime_battle_probe.c --selftest`): never reaches faint, faints but stays
authoritative, commit without an observed faint, window without a commit, wrong new slot,
`PartySnapshot` lagging or leading `gBattlerPartyIndexes`, the positive A→B→C→D sequence for both
sides, and — for the opponent voluntary switch — the fail-closed faint discriminator plus the
separation that a player-side switch can never satisfy the opponent tracker.

##### Two suites, two entrypoints, one gate

`ci.sh test` runs **two disjoint C test suites** and reports their counts separately, so neither can
be mistaken for the other:

```text
native/build/test_runner                 native/tests/test_pokemon_reader.c   62 tests
                                         -> the PRODUCTION reader: parsing, substructure
                                            permutations, party discovery policy, battle
                                            lifecycle, live-HP sync
native/build/runtime_battle_probe_selftest
                                         tools/hns-runtime-probe/runtime_battle_probe.c
                                         --selftest                           75 tests
                                         -> the EVIDENCE HARNESS: autobattle, opponent
                                            replacement, opponent voluntary switch, player
                                            forced replacement state machines
```

The tracker suite previously ran only from the probe's own `build.sh`/`--selftest` and was therefore
outside the canonical gate. Since `--selftest` is pure — it returns before any ROM, core or
environment is touched — it is now a required `ci.sh test` step (`tracker_selftest()` in `ci.sh`),
and `ci.sh all` runs it too. The two suites are still counted separately on purpose: the 62 guard
shipped reader behaviour, the 75 guard the fail-closed behaviour of the harness whose output this
document treats as evidence, and conflating them would hide which one broke.

#### 11.7.2 Legal game progression pipeline (issue #1 phase 1 complete)

The game progression blockers described in the initial investigation (§4.4) were resolved by implementing a complete 4-stage legal playthrough driving the official ROM with controller input only:

1. **Stage 30 (`30-starter-to-egg.txt`)**: Traverses Route 29, declines Guide Gent's tour to enter Cherrygrove City, heads north on Route 30 to Mr. Pokémon's house, receives the Mystery Egg and Pokédex, exits and handles Elm's emergency phone call, and saves (`stage30_egg.sav`).
2. **Stage 31 (`31-egg-to-pokeballs.txt`)**: Navigates south back through Cherrygrove, defeats the rival (Silver's Cyndaquil) with normal battle inputs, returns east across Route 29 to New Bark Town, names the rival at Elm's Lab, receives 5 Poké Balls from the aide, visits Mom to unblock Route 30, and saves (`stage31_pokeballs.sav`).
3. **Stage 32 (`32-catch-second-party-member.txt`)**: Steps onto Route 29, encounters a wild Pokémon in tall grass, throws a Poké Ball via normal controller input, captures the wild Pokémon (Hoothoot), expanding the player party from 1 to 2 members, and saves (`stage32_party2.sav`).
4. **Stage 33 (`33-route30-trainer-ready.txt`)**: Walks west to Cherrygrove City, heals the full party at the Pokémon Center, traverses Route 30 north past the unblocked bottleneck, defeats Youngster Joey (Rattata Lv4), and positions the player directly before Youngster Mikey at `(23, 25)` facing north, then saves (`stage33_trainer_ready.sav`).

This established a fully legal baseline with a 2-Pokémon player party facing a 2-Pokémon trainer, unblocking Scenarios 40, 41, and 42.

### 11.8 Reproducing this section

```bash
cd tools/hns-runtime-probe && ./build.sh

# Phase 1: produce a legal save from a fresh ROM (normal progression, ~5 minutes).
./make-save.sh <mgba_libretro.so> "<legal hns 2.0.5 rom>.gba" "$HOME/hns205.sav"

# Wild battle scenarios. Each run fails (non-zero exit) on any invariant violation.
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/hns205.sav" --script scenarios/10-wild-battle-entry.txt
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/hns205.sav" --script scenarios/20-wild-battle-full.txt

# Legal progression pipeline. Run these IN ORDER from the starter save; each one writes the save
# the next one boots from:
#   30-starter-to-egg.txt                 -> stage30_egg.sav
#   31-egg-to-pokeballs.txt               -> stage31_pokeballs.sav
#   32-catch-second-party-member.txt      -> stage32_party2.sav
#   33-route30-trainer-ready.txt          -> stage33_trainer_ready.sav
#   34-route30-don-ready.txt              -> stage34_don_ready.sav
#   45-route30-to-violet-city.txt         -> stage44a_violet_city.sav   (independent leg, see 11.10.7)
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage32_party2.sav" --script scenarios/33-route30-trainer-ready.txt
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage33_trainer_ready.sav" --script scenarios/34-route30-don-ready.txt

# Trainer battle and party switch scenarios (requires stage33_trainer_ready.sav).
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage33_trainer_ready.sav" --script scenarios/40-trainer-battle-entry.txt
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage33_trainer_ready.sav" --script scenarios/41-opponent-replacement.txt
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage33_trainer_ready.sav" --script scenarios/42-voluntary-switch.txt
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage33_trainer_ready.sav" --script scenarios/43-player-faint-forced-replacement.txt

# Opponent VOLUNTARY switch (Scenario 44, section 11.10) and its damage diagnostic, both from
# stage34_don_ready.sav. Scenario 44 is PROBABILISTIC: the source-supported default switch path uses
# a 33% roll on eligible turns, while the exact runtime ShouldSwitch...() branch remains NOT
# VERIFIED / NOT CLAIMED (11.10.8). An attempt that never rolls fails closed with a clean timeout and
# zero invariant violations; retry from the same battery save and take a single PASS as the evidence.
# No per-attempt probability is claimed. The
# `--quiet` flag is NOT used here so the Phase A/B/C/D lines stay on the record.
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage34_don_ready.sav" --script scenarios/44-opponent-voluntary-switch.txt
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage34_don_ready.sav" --script scenarios/47-don-damage-probe.txt

# Overworld navigation regression coverage (multi-map traversal, arrow warp, gate, sideways
# staircase, wild-encounter clearing inside `walk`). Independent of Scenario 44.
./runtime_battle_probe <mgba_libretro.so> "<rom>.gba" \
    --sav "$HOME/stage34_don_ready.sav" --script scenarios/45-route30-to-violet-city.txt

# Deferred, does NOT pass, and run by nothing: kept as a labelled artifact only. See 11.10.7.
#   ./runtime_battle_probe <core> <rom> --sav <save> --script deferred/46-sprout-tower-3f.txt

# Deterministic regression coverage: the production-reader suite (62) AND the runtime tracker
# selftests (75). Needs no ROM, no core, no save and no emulator.
cd ../.. && ./ci.sh test
```

### 11.9 Player faint and forced replacement (issue #1, Scenario 43)

**Validation date: 2026-09-17.** This section records the last player-side transition of issue #1:
the active player Pokémon genuinely fainting in a real trainer battle, DualDex failing closed while
no player battler is authoritative, and the ROM's own forced replacement being completed with
ordinary controller input.

Everything below was produced by
`tools/hns-runtime-probe/scenarios/43-player-faint-forced-replacement.txt` from the same legal
`stage33_trainer_ready.sav` that Scenarios 40–42 use, against the same official 2.0.5 ROM
(`sha256 edf76ecf…7679b`) in the same host mGBA libretro core (BuildID
`150660aa40aa7f42f9cde07d0589197f07acb414`). No RAM writes, no cheats, no save editing, no ROM
patching, no party injection; every input is an ordinary D-pad/A press. Run result: **PASS**,
0 runtime invariant violations, 0 script errors (the representative run took 8,867 frames).

#### 11.9.1 How the faint was produced without defeating the opponent

Chikorita (slot 0) is far stronger than Mikey's level-2 Hoothoot, so any damaging move would have
ended the battle the wrong way. The scenario therefore selects the one non-damaging move, Growl,
and the command proves that choice three ways instead of assuming a menu position:

| Fact | Runtime observation |
|---|---|
| Which moves the active battler actually has | `gBattleMons[0].moves` (offset `0x0C`, §3.2) read live at frame 1985: `33, 45, 75, 0` — Tackle, **Growl**, Razor Leaf, empty |
| Which menu position that move is in | Growl is move id `45`; the live list puts it at menu position `1`. `surrender-to-faint`'s original hardcoded "move 1 = Growl" assumption happened to be right for this save, but it was re-derived from the live move list rather than trusted |
| That the controller cursor really corresponds to that move | raw move-menu cursor `0x020003A8` read `0` while the menu opened, `1` after one `RIGHT` press, and stayed `1` for all 18 Growl turns; the `RIGHT` press is what moved it |
| That the selected move does not damage the opponent | the opponent's HP was `14/14` at frame 1579 and **`14`** at every later frame of the whole run (a hard failure condition inside the command) |
| That the selected move really was Growl | Growl's actual effect was observed: `gBattleMons[1].statStages[1]` (Attack) `6 -> 5` at frame 2096 |

18 Growl turns were needed: with the opponent's Attack reduced, Hoothoot's Tackle does the minimum
1 damage per hit against Chikorita's 25 max HP (16 HP remaining when the battle started). Frame
numbers below are from one representative run; the turn count — and therefore the frame at which
the player faints — varies between runs because the opponent's damage roll does, while every
asserted state transition is the same.

#### 11.9.2 The faint frame (Phase B)

```text
frame 8150
  lifecycle                     ACTIVE   (gMain.inBattle still set)
  gBattlerPositions             0,1
  gBattlerPartyIndexes          0,0,0,0     <- unchanged; the engine has not sent anyone out
  gAbsentBattlerFlags           0x00        <- the fainted player battler is NOT marked absent
  player battler                0
  gBattleMons[0].species        152 (Chikorita)
  gBattleMons[0].hp             0 / 25
  PartySnapshot.active_battler_known   false
  PartySnapshot.active_battler_slot    -1
```

So the fail-closed transition came from `hp == 0` alone. H&S does **not** set
`gAbsentBattlerFlags` for a fainted *player* battler during the replacement window, unlike the
opponent side where Scenario 41 observed `absentFlags=0x02`. The synthetic test
`test_hns_player_faint_forces_unknown_until_replacement` models exactly this case first (hp 0 with
the absent flags clear) and then the absent-flag variant as a defensive extra; the runtime agreed
with the decisive part of the model and never exercised the absent-flag variant. The reader needed
no change: it withholds the slot on `hp == 0` before the absent flag ever matters.

#### 11.9.3 The fail-closed replacement window (Phase C)

```text
frames 8150 .. 8747  (597 frames, the whole party-menu interaction)
  lifecycle                     ACTIVE
  PartySnapshot.active_battler_known   false   on every sampled frame
  PartySnapshot.active_battler_slot    -1      on every sampled frame
  gBattlerPartyIndexes          0,0,0,0        on every sampled frame (still the fainted slot)
  gBattleMons[0].hp             0 -> 14        (14 only once the replacement was sent out)
```

The old slot 0 was never presented as a live active Pokémon: the production surface stayed unknown
for the entire window, and the raw `gBattlerPartyIndexes[0]` stayed `0` until the send-out completed.
A stale-slot carryover would have been recorded as a violation by the tracker and failed the run.

#### 11.9.4 The replacement input path (Phase D)

The ROM's real flow, as observed (not as predicted):

```text
frame 8150   Chikorita's HP reaches 0; battle-controller command sequence 2 -> 46 -> 40 -> 10 -> 2
             (faint messages). No Yes/No prompt was observed: the harness's player Yes/No state
             (bcmd 18) never appeared during this window.
frame 8279   party menu open: gPartyMenu (0x020341FC) type=1 layout=0 slotId=0 action=0;
             in_party_menu via bcmd == 21 / gMain.callback2 == 0x0819D77D
frame 8363   D-pad DOWN once -> slotId 1 (Hoothoot), action=1
frame 8363   A press 1 on slot 1 -> still in the party menu at frame 8399
             (gBattlerPartyIndexes[0]=0, hp[0]=0, PartySnapshot still unknown/-1)
frame 8663   A press 2 on slot 1 -> at frame 8699 gMain.callback2 has left the party menu
             (0x081FA091) but gBattlerPartyIndexes[0] is still 0: the send-out is under way
frame 8747   replacement committed, accepted by the tracker
```

Two ordinary `A` presses were required on the target slot — the same "open the sub-menu, then
confirm `SHIFT`" path Scenario 42 recorded for a voluntary switch. The command does not hardcode
that: it presses `A` once on the target slot and, if the transition has not committed after a long
settle, retries a bounded number of times, so a ROM that needed one press would simply show one.

#### 11.9.5 The committed replacement

```text
frame 8747 (and the final matrix at frame 8867)
  lifecycle                           ACTIVE
  BattleKind                          TRAINER_SINGLE
  player party count                  2
  enemy party count                   2
  active player slot (PartySnapshot)  1        known = true
  active player battler               0
  gBattlerPartyIndexes                1,0,0,0
  gBattleMons[0].species              163 (Hoothoot)
  gBattleMons[0].hp                   14 / 14
  gPlayerParty[1].species             163 (cross-check: the party mon of the slot the index names)
  gPlayerParty[1].current_hp          14 (cross-check: production synced the live HP into that slot)
  old slot 0                           never authoritative again
  opponent                             still slot 0, Hoothoot, HP 14 (never damaged)
```

The key invariant therefore held on the commit frame and every frame after it:

```text
PartySnapshot.active_battler_slot (1) == gBattlerPartyIndexes[resolved player battler] (1)
```

#### 11.9.6 What the new command refuses to accept

`await-player-forced-replacement <old_slot> <new_slot> <max_frames>` is not satisfied by the
replacement appearing. The pure `PlayerReplacementTracker` it drives requires the four phases in
order and records a violation — fatal to the run — for any of these:

* a known active player slot whose value is not `gBattlerPartyIndexes[active_battler]`;
* the old slot reported as a known active slot while the battler holding it has `hp == 0`;
* after that faint has been observed, the old player slot becoming authoritative again at any point
  before replacement completes — even if `gBattleMons` already contains the replacement species/HP
  while `gBattlerPartyIndexes` still names the old slot;
* a live `gBattlerPartyIndexes` entry (new slot, `hp > 0`, species present, not absent-flagged) that
  production reports as no slot, or as a different slot;
* a commit whose `gBattleMons` species/HP do not match the player party member of the slot the index
  names.

A timeout that never reached the commit is a `script error` and fails the run even with zero
invariant violations, which is what
`tools/hns-runtime-probe/selftest.sh`'s `player-replacement-timeout` case asserts.

#### 11.9.7 Runtime vs synthetic

The live run **matched** the synthetic model on both decisive points — a fainted active player
battler leaves `PartySnapshot` unknown (`slot == -1`), and a committed replacement resolves to the
new slot through `gBattlerPartyIndexes` with HP synced — and contradicted nothing. The one detail
the runtime added is that the fail-closed mechanism on the player side is `hp == 0` rather than
`gAbsentBattlerFlags`, which H&S leaves clear for a fainted player battler. **No production reader
bug was found and no production code was changed by this PR**; the changes are confined to the
developer probe, its scenarios, its self-tests and this document.

### 11.10 Opponent voluntary switch (issue #1, Scenario 44)

**Status: RUNTIME VERIFIED.** `scenarios/44-opponent-voluntary-switch.txt` passes against the
official release ROM from `/tmp/hns_baseline/stage34_don_ready.sav`, and the switch it observes is a
genuine voluntary opponent switch, not a faint replacement.

#### 11.10.1 The correction that had to be made first

**Status of the earlier Don rejection: WITHDRAWN.** The history is recorded rather than edited away,
because the provisional rejection shaped several later decisions:

```text
1. An initial runtime observation appeared lethal for Ledyba Lv3 and Don was provisionally rejected.
2. Subsequent source review exposed a contradiction: the same damage reasoning used elsewhere in
   this document predicts only 3-4 HP per Razor Leaf against that Ledyba.
3. Bounded remeasurement with scenarios/47-don-damage-probe.txt produced 15 -> 11 -> 7 -> 3,
   i.e. 4 HP per Razor Leaf hit.
4. The original one-shot was NOT reproducible and remains an UNEXPLAINED OUTLIER. No cause is
   asserted for it here: it was not proven to be a critical hit, and the surviving explanations
   (a different move having been selected, or a different battle state) are speculation.
5. The provisional rejection is therefore withdrawn and Don is used as the Scenario 44 target.
```

```text
turn 1   15 -> 11   (delta 4)
turn 2   11 ->  7   (delta 4)
turn 3    7 ->  3   (delta 4)
```

Ledyba survives three qualifying hits and is still alive at 3 HP. The original "single hit killed
it" observation is therefore not reproducible from the pinned sources, and no explanation for it was
found; it is recorded here as an unexplained outlier rather than as evidence. The history is kept
deliberately: the provisional rejection was wrong, and a later source-only preflight built on top of
it (`TRAINER_AL_HNS`) was wrong for an independent reason described in 11.10.6.

Source model for the measurement (pinned H&S 2.0.5):

```text
Razor Leaf   src/data/moves_info.h:2086   power 55, TYPE_GRASS, DAMAGE_CATEGORY_PHYSICAL,
                                          accuracy 95, target TARGET_BOTH, criticalHitStage 1
                                          (B_UPDATED_MOVE_DATA >= GEN_3, GEN_LATEST = GEN_9)
Don          src/data/trainers_hns.h:8406 TRAINER_BATTLE_TYPE_SINGLES,
                                          aiFlags = AI_FLAG_CHECK_BAD_MOVE
             slot 0  SPECIES_LEDYBA   Lv3, TRAINER_PARTY_IVS(0,0,0,0,0,0), NATURE_HARDY
             slot 1  SPECIES_SPINARAK Lv3, TRAINER_PARTY_IVS(0,0,0,0,0,0), NATURE_HARDY
```

Measured player state (`party-stats player`, production reader):

```text
slot 0 Chikorita Lv7  hp 21/25  atk 14  def 15  spe 9   nature 2 (Brave)
       ivs 27/24/19/4/29/2   moves 33,45,75 (Tackle, Growl, Razor Leaf)
slot 1 Hoothoot  Lv3  hp 17/17  spe 7    moves 33,45,193
```

Ledyba Lv3 with zero IVs has Defence 6 and HP 15; the arithmetic gives a base of 12, x1.5 STAB,
x0.25 type (Grass against Bug/Flying), i.e. 3-4 after the damage roll — matching the 4 measured.
A critical (Razor Leaf is a high-critical-ratio move, stage 1 -> 1/8 under `B_CRIT_CHANCE =
GEN_LATEST`) computes to roughly 7-9, which is also inside the observed range and would still not
one-shot Ledyba. Critical-hit status itself is **not** read from memory: the core would need a
speculative `gSpecialStatuses` address for that, so it is reported as UNKNOWN and inferred only from
the measured magnitude.

#### 11.10.2 Why the switch is voluntary, not a faint replacement

`FindMonWithFlagsAndSuperEffective` (`src/battle_ai_switch.c:1041-1102`, called at `:1381`) reads
`gLastLandedMoves[battler]`, which is indexed **by target** and written by
`MoveEndUpdateLastMoves` (`src/battle_move_resolution.c:2691-2704`):

* Ledyba knows only Tackle, whose target is the player's battler, so Ledyba never writes
  `gLastLandedMoves[opponent]`. The arming value is therefore order-independent and stays RAZOR_LEAF
  for as long as the player keeps using it.
* Razor Leaf against Spinarak (Bug/Poison) is 0.25x, so `UpdateMoveResultFlags`
  (`src/battle_util.c:8330-8352`) sets `MOVE_RESULT_NOT_VERY_EFFECTIVE` and not
  `MOVE_RESULT_DOESNT_AFFECT_FOE`: the **33% resistance branch** is the live one, not the 50%
  immunity branch.
* Spinarak's Poison Sting is 2.0x against Grass Chikorita, so the bench condition holds.
* Don has no `AI_FLAG_SMART_SWITCHING`, no ACE flags, and Ledyba has no stat-raising or
  super-effective move, so the `:1374` / `:1376` early returns do not fire and
  `AreStatsRaised` never trips — there is no two-turn budget here.

#### 11.10.3 Exact runtime observation

`scenarios/44-opponent-voluntary-switch.txt`, passing run:

```text
Phase A  frame 1543  old slot 0 ACTIVE  battler=1 species=165 (Ledyba) HP=15/15
                    (TRAINER_SINGLE, battlers=2, opponentBattlers=1)
Phase B  frame 4145  the authoritative transition has begun (partyIndexes[1]=1, activeEnemy=2);
                    outgoing species 165 is still at HP=3 > 0.
                    No AI decision byte is read.
Phase C  frame 4146  gBattlerPartyIndexes[1] rewritten to 1
Phase D  frame 4147  VOLUNTARY SWITCH committed: slot 0 -> 1, battler=1, species 165 -> 167;
                    gBattlerPartyIndexes=1 == production slot=1
                    ENEMY PARTY AT COMMIT: slot 0 = species 165 HP=3/15 STILL ALIVE
                                          slot 1 = species 167 HP=15/15
```

Phase D is a fatal contract, not a print: the commit is rejected unless the production enemy party
snapshot shows both of those rows on the commit frame. In an earlier passing run the same fields read
`slot 0 = species 165 HP=11/15` with `slot 1 = species 167 HP=15/15`.

The frame at which the commit is accepted carries the decisive evidence, read through the
**production** reader on that same frame:

```text
party-stats enemy  activeSlot=1 activeKnown=1 activeBattler=1
  slot 0 species=165 (Ledyba)   lvl=3 hp=3/15    <-- OUTGOING MON, STILL ALIVE
  slot 1 species=167 (Spinarak) lvl=3 hp=15/15   <-- new authoritative opponent
MATRIX  lifecycle=ACTIVE kind=TRAINER_SINGLE inBattle=true
        indexes=1,1  activeEnemy=SLOT resolvedBattler=1 resolvedEnemySlot=1 fainted=0
```

So: the outgoing Ledyba was at 3/15 HP in party slot 0 while `gBattlerPartyIndexes[1]` became 1, the
production reader resolved slot 1 / Spinarak, and the battle stayed ACTIVE. No faint-based
transition was accepted.

#### 11.10.4 Attempt statistics (bounded legal attempts, one battery save)

| attempt | Phase A | qualifying Razor Leaf hits | player stall switches | outcome | outgoing HP at commit |
|---|---|---|---|---|---|
| 1 | frame 1543, HP 15/15 | 2 | 1 | timeout | - |
| 2 | frame 1543, HP 15/15 | 2 | 1 | timeout | - |
| 3 | frame 1589, HP 15/15 | 3 | 1 | **PASS** | 3 |
| 4 | frame 1543, HP 15/15 | 2 | 1 | timeout | - |
| 5 | frame 1589, HP 15/15 | 2 | 0 | **PASS** | 7 |

Two further bounded batches were run after the tracker fixes were final, to confirm the pass is
reproducible rather than a single lucky seed. Every timeout was a clean miss: zero runtime invariant
violations, no faint accepted, no switch claimed, and the tracker still refused to advance.

```text
batch 1 (5 attempts)   2 PASS / 3 clean timeout
batch 2 (6 attempts)   4 PASS / 2 clean timeout
batch 3 (4 attempts)   3 PASS / 1 clean timeout     <- after the cleanup pass
batch 4 (1 attempt)    1 PASS                       <- first attempt, with the fatal commit-time
                                                       party contract in place
-----------------------------------------------
total                 10 PASS / 14 attempts
```

A single PASS is sufficient for the gate: what makes the result trustworthy is that the accept and
reject paths are deterministically covered by the pure selftests (`vsw_*`), not the hit rate of the
roll.

The "qualifying Razor Leaf hits" column above is deliberately a **lower bound**, not an eligible-roll
count. The tracker can observe that a damaging hit landed and left the outgoing mon alive; it cannot
observe how many of the intervening AI action-selections satisfied the complete predicate of the
source-supported resistance path. Player-switching stall turns in particular are ordinary
action-selection opportunities that preserve the previous landed-move state — they are **not**
claimed to be eligible rolls, because while Hoothoot is the active player Pokemon the bench-move
comparison is evaluated against a different active Pokemon than it is for Chikorita. Because the
battle's RNG is seeded from the booted save, attempts whose in-battle input schedule is bit-identical
replay the same roll; the scenario is therefore retried from the battery save, exactly as a player
retries a battle. No RNG was read, seeded or manipulated, and no savestate was used.

#### 11.10.5 Probe defects found and fixed while making Scenario 44 pass

Two probe defects were found and fixed while making Scenario 44 pass. Both are harness defects, not
reader defects; **no production reader, profile or Kotlin code was changed**.

1. **A status follow-up cannot keep the arming value.** The tracker's original turn plan was "Razor
   Leaf until it lands once, then Growl", on the assumption that a status move does not overwrite
   `gLastLandedMoves`. `MoveEndUpdateLastMoves` sets it for *any* landed move, so Growl replaced
   RAZOR_LEAF with a status move and the heuristic bailed on `IsBattleMoveStatus` (`:1059`). The plan
   was therefore incapable of arming a second eligible turn. It is now "the arming move on every
   turn", and once the outgoing mon is within one observed hit of fainting the tracker alternates the
   player's active slot instead — `SwitchInClearSetData` (`src/battle_main.c:3414`) only clears the
   *switching-in* battler's own index entries, so a switch preserves `gLastLandedMoves[opponent]`
   while burning the turn. That is also a strictly stronger form of the evidence: the outgoing mon is
   held alive on purpose rather than by luck.
2. **Two unproven addresses were in the evidence path at all.** Phase B once required
   `gBattleStruct->monToSwitchIntoId` to report the new slot before a transition could be accepted,
   with the pointer derived by applying the documented -4 battle-global shift to a source-build
   address. That pointer was never confirmed by semantic correlation, and on the passing run it read
   back as `-1` while a real switch was in progress. It is now **deleted, not downgraded**: together
   with the already-absent `gChosenActionByBattler`, the probe no longer contains either address, so
   no verdict can depend on an unverified read and no future edit can quietly promote one. Phase B
   proves only that the authoritative transition has begun, and the phase is named
   `VSW_PHASE_B_AWAIT_TRANSITION` accordingly.

3. **The commit-time party contract is now fatal, not a print.** The liveness of the outgoing mon can
   only be read from the enemy PARTY slot, because by the commit frame `gBattleMons[old_battler]`
   already describes the replacement. A commit is therefore accepted only if, on that frame, the
   production enemy party snapshot shows the old slot holding the old species with `current_hp > 0`
   **and** the new slot holding the species the engine just made authoritative with `current_hp > 0`
   — on top of the index rewrite, the reader agreement and the battle still being `ACTIVE`. Any of
   those failing is a fatal violation with its own message, not a diagnostic. `vsw_old_party_dead_must_not_complete`
   covers the case the `hp == 0` latch alone cannot: the party slot reads 0 while the sampled battler
   HP never does.

#### 11.10.6 `TRAINER_AL_HNS` remains rejected, for an independent reason

The Azalea Gym lead is not a viable substitute, and this was established from the pinned source
before any Azalea run. Metapod's `Harden` is a `TARGET_USER` move, so
`MoveEndUpdateLastMoves` writes `gLastLandedMoves[opponent]` with a status move every time Metapod
acts after the player — and the measured player Chikorita (Brave, Speed 9 at level 7) out-speeds
Metapod (Speed 10) from level 8 onward. The only escape, an `MOVE_RESULT_NO_EFFECT` flag on the
user, cannot occur: `IsTargetingSelf` returns `skipFailure` without setting any result flag
(`src/battle_move_resolution.c:656-659`) and the attack canceler clears `moveResultFlags` immediately
before the move script runs (`:1010`). Additionally `AreStatsRaised` (`:230-241`, threshold
`STAY_IN_STATS_RAISED` = 2 in `include/config/ai.h:20`) would cap a Harden lead at two eligible
turns even if the ordering worked out.

#### 11.10.7 Route 32 south is a mandatory story gate (deferred work)

Discovered while attempting Azalea via the Violet City route, and recorded here because any future
Azalea attempt must budget for it. Route 32 has exactly one corridor between its northern and
southern halves, at `y = 10`, `x = 26..29`. `x = 25` is blocked, `x = 26` is an NPC, and `x = 27..29`
carry coordinate triggers running `Route32_EventScript_BaldingManCheck`, which messages the player
and applies `Route32_Movement_Turnback` until `FLAG_HIDE_SPROUT_TOWER_SILVER`,
`FLAG_DEFEATED_VIOLET_GYM` and `FLAG_RECEIVED_TOGEPI_EGG` are all set. Measured at runtime: walking
down column 27 the player reaches `(27,10)` and is turned back to `(27,9)`, oscillating
indefinitely. A breadth-first search over the full pinned connection graph finds no path from Violet
City to Azalea Town with those tiles excluded, and the Ruins of Alph connection (which does reach
Union Cave B1F) does not reach Route 33 or Azalea either.

The legal progression leg that got as far as Violet City is verified and preserved:

```text
scenarios/45-route30-to-violet-city.txt   result PASS
    Route 30 (19,9) -> Route 31 -> Gate Route31/VioletCity -> Violet City (39,46)
    -> Pokemon Center -> Nurse Joy heal -> in-game save
    0 script errors, 0 invariant violations
    output /tmp/hns_baseline/stage44a_violet_city.sav (131072 bytes, re-boot verified:
    Violet City (39,46), Chikorita Lv7 25/25)
```

The Violet City / Sprout Tower investigation continues as a **deferred artifact**:
`tools/hns-runtime-probe/deferred/46-sprout-tower-3f.txt`. It is deliberately kept out of
`scenarios/`, which is the set of scripts this document cites as passing, so that nothing in the
completed gate is a script that does not pass. It is not run by any gate and no claim above depends
on it.

What it established, and why it is worth keeping: it navigates Violet City -> Sprout Tower 1F -> 2F
correctly, with every source-predicted tile asserting OK, and then wins Sage Nico's battle at
`SproutTower_2F (14,3)`. After that the field accepts **no** direction for the rest of the run
(135k frames observed), with `lifecycle=INACTIVE`, `inBattle=false`, `outcome=B_OUTCOME_WON`, zero
runtime invariant violations, and a production reader that still resolves the party. That is a
harness / field-state problem, not a reader problem, and it is left explicitly unresolved rather
than papered over or worked around.

#### 11.10.8 Claim matrix

Every row states the strongest level actually supported. Source inference is never promoted into
runtime evidence.

| Claim | Level | Where |
|---|---|---|
| Opponent active-slot read at battle start (Don: slot 0, Ledyba) | **RUNTIME VERIFIED** | `s44-engaged` / `s44-initial-ledyba` MATRIX + `party-stats enemy` |
| Trainer battle lifecycle (`TRAINER_SINGLE`, 2 battlers, `ACTIVE`, still active after the switch) | **RUNTIME VERIFIED** | every `[MATRIX]` line of the run |
| Opponent voluntary slot transition `0 -> 1` | **RUNTIME VERIFIED** | Phase C/D, `gBattlerPartyIndexes[1]` `0 -> 1` |
| Outgoing opponent alive in its own enemy PARTY slot at commit | **RUNTIME VERIFIED + MACHINE-ENFORCED** | Phase D now fails the run unless the production enemy party snapshot shows the old slot still holding the old species with `current_hp > 0` on the commit frame; the passing run recorded `slot 0 = species 165 HP=3/15` (and `11/15` in another run) while the new slot held species 167 |
| Production reader follows the new authoritative slot | **RUNTIME VERIFIED** | commit-frame `activeSlot=1`, species 167, `enemy_slot == party_index[1]` |
| The accepted Scenario 44 transition is not a faint | **RUNTIME VERIFIED** | the outgoing party member was still alive at commit (row above), so the accepted transition cannot have been a faint-based replacement |
| Voluntary tracker `hp == 0` rejection logic | **SYNTHETIC VERIFIED / RUNTIME-CONSISTENT** | selftests `vsw_negative_old_fainted_latched`, `vsw_faint_latched_order_independent`, `vsw_faint_replacement_never_accepted`, `vsw_old_party_dead_must_not_complete`. Scenario 41 proves the game's faint-replacement lifecycle at runtime, but through the dedicated replacement path; **no preserved run of this exact voluntary tracker has been observed rejecting a live faint**, so the rejection logic itself is not labelled runtime verified |
| Player-side switch cannot satisfy the opponent tracker | **SYNTHETIC VERIFIED** | selftest `vsw_player_switch_not_opponent_switch` |
| Don Razor Leaf nonlethal damage (4 HP/hit) | **RUNTIME VERIFIED** | `scenarios/47-don-damage-probe.txt` |
| `FindMonWithFlagsAndSuperEffective` 33% resistance path applicability | **SOURCE VERIFIED / RUNTIME-CONSISTENT** | `src/battle_ai_switch.c:1041-1102,1381`; `src/battle_util.c:8330-8352`; the observation is consistent with it |
| Exact `ShouldSwitch...()` branch that returned TRUE | **NOT VERIFIED / NOT CLAIMED** | not directly instrumented. `AI_TrySwitchOrUseItem` evaluates several earlier predicates first, and this document does not claim direct runtime exclusion of every one of them |
| `gChosenActionByBattler` address | **NOT VERIFIED / NOT CLAIMED** | constant removed from the probe source |
| `B_ACTION_SWITCH` direct runtime observation | **NOT VERIFIED / NOT CLAIMED** | no code path reads it |
| `gBattleStruct->monToSwitchIntoId` release address | **NOT USED / NOT CLAIMED** | constant and reader removed from the probe source |
| Don provisional "one-shot" rejection | **WITHDRAWN** | unreproducible; measured 4 HP per hit |
| `TRAINER_AL_HNS` as a voluntary-switch target | **SOURCE + MEASUREMENT DISPROVEN** | see 11.10.6 |
| Route 32 south gated on Sprout Tower + Violet Gym + Togepi | **SOURCE + RUNTIME VERIFIED** | see 11.10.7 (deferred work) |

**On the AI branch specifically.** The observed switch is *source-consistent* with the 33%
resistance path of `FindMonWithFlagsAndSuperEffective`: the arming value is a damaging move the bench
resists (Razor Leaf at 0.25x on Spinarak), the 50% immunity call does not fire because 0.25x sets
`MOVE_RESULT_NOT_VERY_EFFECTIVE` and not `MOVE_RESULT_DOESNT_AFFECT_FOE`, and the two unconditional
early returns at `:1374` / `:1376` can be shown inert for this lead (Ledyba has no `>= 2.0x` move
against Grass and no stat-raising move).

That is a source argument about *applicability*, not a runtime attribution, and it is deliberately
weaker than "the only reachable mechanism": **the exact `ShouldSwitch...()` predicate that returned
TRUE was not runtime-attributed.** `AI_TrySwitchOrUseItem` evaluates several earlier switch predicates
before the default path, and this PR does not claim direct runtime exclusion of every one of them
from the captured state. Instrumenting the dispatch would require an address this project has not
established, so the claim rests on the authoritative lifecycle transition instead.

---

## 12. Map and location routing (issue #11)

This section records how DualDex resolves a Heart & Soul 2.0.5 location, what was wrong before, and
what is still outstanding. It was added after PR
[#50](https://github.com/Sonoran-Solutions/dualdex/pull/50) merged as
`867d220ca65f0ca53c255b50861a296ddffe8e08`; that commit is the starting point for this work.

### 12.0 What changed, in one paragraph

H&S is a multi-region build, but DualDex resolved all of it through a single hand-written Johto table
and a `gameId != Emerald/FireRed -> Johto` fallback. Location identity is now generated from the
pinned 2.0.5 source, selected by an explicit per-game strategy, validated as a `(mapGroup, mapNum)`
**pair**, and returns "no authoritative location" instead of a fabricated New Bark Town whenever the
pair is unknown. Region identity, canvas availability and runtime visit are tracked as three
separate facts.

### 12.1 Provenance of the map data

| Item | Value |
|---|---|
| Repository | `PokemonHnS-Development/pokehns-expansion` |
| Tag | `Release-v2.0.5` |
| Commit | `1f42b74dff0e9fe942419845d040663dd829a973` |
| Generated file | `app/src/main/java/com/dualdex/pokemon/hns/Hns205MapData.kt` |
| Generator | `tools/hns-map-data/generate_hns_map_data.py` |
| ROM dependency | **none** |
| Extracted from | `data/maps/map_groups.json`, `data/maps/<map>/map.json`, `src/data/region_map/region_map_sections.json` (names), `src/data/region_map/region_map_layout_{johto,kanto,jk}.h` |
| Result | 560 H&S locations, 120 sections (90 presentable, 30 named-without-canvas) |

**SOURCE VERIFIED.** `mapGroup` is the index into `group_order` in `data/maps/map_groups.json`, and
`mapNum` is the index within that group. Identities are never alphabetically sorted: the generator
walks the file order. Only maps whose `map.json` declares `"game_version": "hns"` become locations.
The same table also carries the Emerald and FireRed map groups that DualDex models separately, and
admitting them would let a non-H&S `mapGroup` look like a valid H&S location.

Regenerate and verify:

```bash
python3 tools/hns-map-data/generate_hns_map_data.py                 # write
python3 tools/hns-map-data/generate_hns_map_data.py --print-summary
python3 tools/hns-map-data/generate_hns_map_data.py --check         # byte-identical vs upstream
python3 tools/hns-map-data/generate_hns_map_data.py --verify-digests  # no upstream needed
```

Only **tracked** upstream files are read. `region_map_entries.h` is deliberately not used: upstream
generates it from `region_map_sections.json` and gitignores it, so a git-only checkout of the pinned
commit cannot reproduce it. Names therefore come from the tracked Inja input, and positions from the
tracked layout grids.

**Geometry contract.** A generated `(gridX, gridY, width, height)` is a rectangle whose origin is its
**top-left tile**, i.e. the bounding box minimum. Consumers add `width/2` and `height/2` themselves
when they need the visual centre. An earlier revision of this PR emitted the bounding box *centre*
alongside the full extent, which shifted all 26 multi-tile sections by half their size — Route 29
drew from x=16 instead of x=15 and so reached into New Bark Town's tile, and Route 30 sat two rows
south. The upstream oracle below now compares the **full numeric rectangle** for every presentable
section, not merely whether a section appears on the right canvas.

The generated file records two digests, which is what makes freshness checkable without a network:

| Constant | Meaning |
|---|---|
| `SOURCE_DIGEST` | SHA-256 over the exact pinned upstream inputs (the map table, every H&S `map.json`, the sections JSON and the three layout grids) |
| `MAPPING_DIGEST` | SHA-256 over the generated mapping content, independent of file formatting |

`--verify-digests` reads **only** the committed generated file, re-derives `MAPPING_DIGEST` from the
records inside it and compares. It needs no ROM, no network and no upstream checkout, so the
canonical gate always performs a real integrity check instead of skipping one, and a hand-edited
generated record or a stale regeneration fails `./ci.sh test`. This was confirmed by tampering with a
single generated record and observing the check reject it.

`--check` upgrades that to a byte-for-byte regeneration against the pinned checkout and is the
explicit developer command. Regeneration is deterministic and was confirmed byte-identical across
runs; the generated file embeds no developer path and no timestamp.

### 12.1.1 Two gates: self-contained canonical, explicit source validation

The canonical gate must work from a plain DualDex checkout, so the upstream comparison is **not** part
of it and no canonical test reaches for a network or an external tree:

| Command | Needs | Content |
|---|---|---|
| `./ci.sh test` | nothing external | native runner, tracker selftests, offline digest check, data-pack generator tests, self-contained Kotlin suite |
| `./ci.sh source-check` | the pinned upstream checkout + `arm-none-eabi-cpp` | byte-for-byte regeneration of the map data **and** the data pack (species, moves, ability catalogue and per-species slots) via `generate_hns_data_pack.py --verify`, **and** the Kotlin oracle with `-Pdualdex.hns.upstreamCheck=true` |

`source-check` **fails loudly** when its required inputs are missing, unreachable or at the wrong
revision; it never degrades to a silent skip. The Kotlin oracle tests read the
`dualdex.hns.upstreamCheck` system property (or `DUALDEX_HNS_UPSTREAM_CHECK`), which only
`./ci.sh source-check` sets, so they cannot quietly pass as if they had validated the source. That
the oracle genuinely asserts was confirmed by tampering with a generated record: with the flag set
the run fails, without it the same tamper is caught by `--verify-digests` instead.

In CI (`.github/workflows/ci.yml`) the canonical `test` job stays self-contained, and a separate
`source-validation` job fetches the pinned public upstream at
`1f42b74dff0e9fe942419845d040663dd829a973` (sparse checkout of `data/maps`, `include`, `src/data`,
`src/pokemon.c` and `src/move.c`) and runs `./ci.sh source-check`, having installed
`gcc-arm-none-eabi` so the data-pack verification has its preprocessor. If that fetch ever fails,
the job fails visibly rather than the cross-check disappearing.

### 12.2 Discrepancies found and fixed

Four independent defects existed in the committed H&S assets and resolver. All four were reproduced
against the pinned source before being changed.

| # | Defect | Evidence | Disposition |
|---|---|---|---|
| 1 | `map_groups_hns.json` group ordering diverged from 2.0.5 from group 25 onward: the two Alola groups and the two Sinjoh groups were absent, so `IndoorDynamic`, `SpecialArea` and the Emerald groups were shifted down | Pinned `group_order`: 25 `gMapGrouop_OutdoorAlola_Hns`, 26 `gMapGroup_IndoorAlola_Hns`, 27 `gMapGroup_IndoorDynamic_Hns`, 28 `gMapGroup_Sinjoh_Hns`, 29 `gMapGroup_IndoorSinjoh_Hns`, 30 `gMapGroup_SpecialArea_Hns`. Committed file had 32 groups and no Alola/Sinjoh group at all | Superseded by generated data (`Hns205MapData.locationGroups`) |
| 2 | `map_groups_hns.json` group 19 (`IndoorFuchsia`) listed five `FuchsiaCity_SafariZone*` maps that 2.0.5 does not define there, so map numbers 7-11 were bogus | Pinned group 19 has exactly 7 members | Superseded by generated data |
| 3 | `map_groups_hns.json` group 22 (`IndoorJohtoRoutes`) omitted seven `*BattleTent*` maps, shifting every later map number by seven. Map 13 is `SlateportCity_BattleTentLobby_hns`, which the legacy resolver reported as `TrainerHill_Courtyard` | Pinned group 22 has 35 members; committed had 27 | Superseded by generated data |
| 4 | `region_map_sections_johto.json` coordinates match **no** H&S region-map view: 61 of 115 differ from the pinned Johto/Kanto grids, and every Kanto city used its vanilla FireRed position (Pallet Town `19,11` instead of `4,11`) | Compared against `sRegionMapSections_Johto/_Kanto/_JK` | Superseded by generated data derived from the layout grids |
| 5 | `RegionMapView` selected its canvas with `getSections(region)`, which returns Heart & Soul Kanto geometry for every game, so a FireRed session drew Pallet Town at `4,11` instead of its own `5,11` | `MapScreenPresenter.sectionsFor` vs the two tables | Canvas is now selected by the active strategy **and** the browsed region |
| 6 | The legacy `JOHTO_SECTIONS` table was the single "not Hoenn/Kanto" dumping ground: entries tagged `RegionId.JOHTO` also included FireRed Kanto, Sevii/event sections 2.0.5 does not define, and four Hoenn cities, which made a strategy appear to own canvases it cannot draw | Region ids cross-checked against the generated table | Vanilla canvases are filtered per region so a strategy draws only its own |

Two further defects were in the resolver rather than the assets:

* `resolveHeartAndSoulLocation` ended in `return JOHTO_DEFAULT`, so an unknown H&S map became **New
  Bark Town**. `resolveLocationOrNull` therefore also returned a fabricated known town, defeating its
  own "null when unknown" contract. Both paths now resolve to nothing.
* Indoor groups 1-21 returned the group's parent town without validating `mapNum`, so any map number
  inside a valid indoor group inherited a real town. A map number is now only valid if the pinned
  table defines it.

`RegionMapDatabase.JOHTO_DEFAULT` is removed, and the legacy `JOHTO_SECTIONS`/`KANTO_SECTIONS` tables
are retained only as optional narrative metadata (description, landmarks, gym leader). Identity,
region and canvas geometry always come from the generated table, so hand-written data can no longer
override pinned source evidence.

### 12.3 Canvas geometry: which coordinate source is authoritative

Three region-map layouts exist in the H&S build and `src/region_map.c` selects between them
(`region_map_layout_johto.h`, `_kanto.h`, `_jk.h`). The layout **grid** is what the game renders and
what the player marker's cursor position is derived from, so the grids are the authoritative canvas
source for Johto and Kanto.

`region_map_entries.h` was deliberately **not** used for positions. Its `#if IS_HNS` table carries
the FireRed/Johto map canvas, and its Sinjoh/Alola entries are unresolved `(0, 0)` placeholders. It
is used only for display names.

Verification of the legacy coordinate defect (grid bbox centre, which is how the legacy values for
Johto towns were evidently derived): Pallet Town `4,11`, Viridian City `4,8`, Pewter City `4,4`,
Cinnabar Island `4,14`, New Bark Town `19,10`, Violet City `12,4`, Route 29 `16,10 x4`.

### 12.4 Resolution and region policy

`LocationStrategy` makes the map-table selection explicit and typed:

| Strategy | Selected when | Table |
|---|---|---|
| `EMERALD` | profile id `vanilla_emerald` | Hoenn, `mapGroup == 0` |
| `FIRERED` | profile id `vanilla_firered` | Kanto, `mapGroup == 3` |
| `HEART_AND_SOUL_205` | profile id `heart_and_soul` | generated 2.0.5 table |
| `UNVERIFIED` | anything else, including `RomHackProfile.UNSUPPORTED` | none — no authoritative location |

Selection keys on the **typed profile id**, never a name substring such as
`name.contains("Heart")`, and there is no `else -> Johto` branch. A profile literally named
"Heart and Soul Remix" selects `UNVERIFIED`.

Outcomes are explicit (`LocationResolution`): `NO_STRATEGY`, `INVALID_READ`, `UNKNOWN_MAP_ID`, or a
resolved section. Two properties are enforced by construction and by test:

* a negative, out-of-range or undefined `(mapGroup, mapNum)` pair resolves to nothing;
* an invalid `mapNum` inside an otherwise valid indoor group does not inherit that group's town.

An arbitrary `escapeMapGroup`/`escapeMapNum` is **not** treated as the current map's identity. The
legacy resolver used the escape warp as a fallback; the pinned 2.0.5 data already carries the correct
parent section for every gate and interior, so no inference is needed.

Three concepts are kept separate, which is what makes the multi-region behaviour safe:

| Concept | Where it lives | Changed by |
|---|---|---|
| Native memory layout / map-table selection | `CompanionViewModel.locationStrategy` | active ROM profile only |
| The player's actual region | `RegionMapSection.region` + `RegionId` | the generated H&S table |
| The region the user is browsing | `RegionMapView.currentRegion` | region tab / canvas only |

`RegionId` gains `SINJOH` and `ALOLA` because 2.0.5 genuinely places the player there
(`region_map_sections.constants.json.txt` defines an explicit Sinjoh/Hisui range and an Alola range).
Its `hasCanvas` flag records that DualDex has no canvas for them yet.

### 12.5 Presentation policy for each region

| Region | Identity | Canvas | Policy |
|---|---|---|---|
| Johto | `RegionId.JOHTO` | 56 sections, pinned H&S Johto grid | Fully presented; live marker supported |
| Kanto | `RegionId.KANTO` | 34 sections, pinned H&S Kanto grid | Fully presented; live marker supported. **Tagged Kanto, never Johto** |
| Sinjoh | `RegionId.SINJOH` | none | Named with its true region; `presentable = false`; **no marker** |
| Alola | `RegionId.ALOLA` | none | Named with its true region; `presentable = false`; **no marker** |
| Dynamic / link / contest | `region = null` | none | `MAPSEC_DYNAMIC`; no region claimed, no marker |

Sinjoh and Alola are `presentable = false` rather than being placed at their combined-canvas
coordinates because DualDex does not render the 28x15 combined Johto/Kanto canvas; drawing a marker
there would place it at coordinates in a grid the app never shows. This is the intentional
"known but not graphically supported" outcome, and it requires no new artwork.

The mark is applied through a single gate, `RegionMapView.liveMarkerSection`, which requires a valid
read, a presentable section, matching region, and non-negative coordinates. `centerOnPlayer` is a
no-op when that gate is closed, so the Center control can never imply a position DualDex does not
have.

### 12.6 Runtime observations actually obtained

**RUNTIME VERIFIED**, read-only, on the official ROM
(`sha256 edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b`, §1.2) with the
developer-only probe (`tools/hns-runtime-probe/`, not run by CI). No RAM writes, no cheats, no save
editing, no savestate injection. Saves were copied to `/tmp/hns_obs/` first; source and copy digests
were identical before and after every run.

```bash
# boot sequence matches the existing scenarios: title -> load the battery save
printf 'wait 120\nmash 800\nwait 200\nassert-map <group> <num>\n' > /tmp/obs.txt
./runtime_battle_probe <mgba_libretro.so> "<official 2.0.5>.gba" \
    --sav /tmp/hns_obs/<copy>.sav --script /tmp/obs.txt
```

| Save | Raw read (production reader) | Pinned table identity | Region |
|---|---|---|---|
| `hns205.sav` | `0/0` | `NewBarkTown_hns` -> `MAPSEC_NEW_BARK_TOWN` | Johto |
| `stage34_don_ready.sav` | `0/12` | `Route30_hns` -> `MAPSEC_ROUTE_30` | Johto |
| `stage44a_violet_city.sav` | `0/2` | `VioletCity_hns` -> `MAPSEC_VIOLET_CITY` | Johto |

All three assert clean (`script errors: 0`). The `0/12` and `0/13` Route 30/31 values independently
agree with the already-passing Scenario 45, which asserts them against live game state.

**What this does and does not show.** It shows the production reader decodes the exact mapGroup/mapNum
pair that the pinned H&S table keys on, at three real checkpoints, and that the generated table maps
those pairs to the right Johto identities. It does **not** show a Kanto, Sinjoh or Alola *transition*:
no save in `/tmp/hns_baseline/` is past Johto, and manufacturing one would require either long
progression or a RAM write, both of which are out of scope here.

A separate, unrelated observation: the **from-source** build in
`~/Projects/upstream-hns/artifacts-default/pokehns.gba` (`sha256 250ca294…c255b`) fails closed in the
production reader (`0/0` decodes as `255/255`, `gSaveBlock1Ptr` reads `0x00000000` at the release
address `0x030041D8`). That is expected and correct: only the official release ROM is the supported
target, and a from-source build has different IWRAM symbol addresses (§2.2, §5.3). The bundled x86_64
core in `cores/build-x86_64/` cannot load on this host (`libm.so: invalid ELF header`); the host-built
core at `~/Projects/upstream-hns/mgba-host/mgba_libretro.so` was used instead.

### 12.7 Tests

All canonical (`./ci.sh test`), no ROM and no network required. The upstream cross-check is an
independent oracle: it re-reads `map_groups.json`, every `map.json`, and the layout grids, and
**fails loudly** if the pinned checkout is not present rather than silently skipping.

| Suite | Covers |
|---|---|
| `Hns205MapDataIntegrityTest` (13) | unique in-range keys, every location resolves to a declared section, independently pinned identities/regions/canvas positions, presentable sections have usable geometry, non-presentable sections have no anchor, generated source is self-describing and path/timestamp free, both digests are recorded and distinct, and the independent upstream source + canvas oracle |
| `HnsLocationRoutingTest` (27) | Johto towns/routes/interiors/dungeons and Kanto; reordered groups 25-30 and the group 22 shift; Kanto-is-Kanto; invalid/negative/out-of-range ids in both dimensions; invalid `mapNum` inside a valid indoor group; escape warp is not identity; Sinjoh/Alola resolve with their own region but no canvas; dynamic maps; strategy isolation; profile-identity (not name) selection; legacy overload agrees with the typed strategy |
| `RegionMapDatabaseTest` (11) | section sourcing from the pinned table; H&S canvas sizes (56 Johto / 34 Kanto); Sinjoh/Alola have no canvas; fail-closed resolution incl. the **regression that an unknown H&S map is not New Bark Town** |
| `HnsLocationTrustBoundaryTest` (10) | recognised-but-unverified H&S, header/profile-name impostors, static data pack is not trust, strategy switches clear live state, H&S -> FireRed -> Emerald -> H&S leaks nothing, exact-hash prerequisite, denied reads vs denied presentation |
| `MapScreenPresentationTest` (36) | geometry contract (multi-tile rectangles use their top-left origin and do not overlap neighbours); the canvas uses the active strategy's table (H&S Pallet Town x=4 vs FireRed x=5); per-strategy drawable regions; follow-live canvas versus explicit browsing override, including Johto -> Kanto following and a strategy switch returning to follow mode; the live/browsing/none selection model, live-follows-every-change, invalidation clearing, and a browsing selection surviving both live updates and invalidation; header state for no-location/unavailable/live; the production live-marker gate; and view-model invalidation and browsing isolation |

Both suites exercise `MapScreenPresenter`, which is the production object `MapScreenView` and
`RegionMapView` consume, so the view and the tests cannot drift apart. An earlier revision kept a
test-local copy of the marker gate, which could pass while the real view was wired incorrectly; that
included migrating the marker gate into the presenter and removing `RegionMapView`'s own copy.

The upstream cross-check is not skippable under `./ci.sh source-check`. It resolves the pinned
checkout from `HNS_UPSTREAM_DIR` (accepting both the repo-root-relative value CI uses and an absolute
path), then the repo root found by walking up to `settings.gradle.kts`, then the conventional sibling
layout. If none is present the test **fails** with the exact commit and tag to fetch, because an
oracle that quietly skips is indistinguishable from one that verified nothing.

One regression test fails against the old behaviour by construction:
`RegionMapDatabaseTest.unknownHnsMapDoesNotBecomeNewBarkTown` asserts that nine unknown pairs resolve
to `null` and not to the New Bark Town section the old code returned.

`sha256Hashes`, `memoryLayoutVerified`, `battleUiVerified` and `interactiveControlsVerified` are
**unchanged** by this work, and no native offset was modified.

### 12.8 Remaining #11 acceptance work

* **Kanto, Sinjoh and Alola runtime transitions.** Source and synthetic coverage exists; no runtime
  checkpoint does. Requires a legal save in Kanto, which requires ordinary progression.
* **App/UI verification.** No on-device Map-tab run against 2.0.5 was performed for this PR. The
  browsing/live separation is asserted at the logic level, not on hardware.
* **Sinjoh and Alola presentation.** Region identity is correct and explicit; a dedicated canvas (or
  a deliberate decision to keep them name-only) is still open.
* **Interior-to-parent fidelity.** Interiors resolve to their parent section because that is what
  `region_map_section` says upstream. DualDex does not present a per-interior name, which is a
  presentation choice, not a routing defect.
* **On-device confirmation of the corrected geometry.** The rectangle origins and the strategy-aware
  canvas are verified against pinned source and by the presentation suite, but no hardware run
  rendered the corrected map for this PR.
* **The 30 named-without-canvas sections.** Each is region-known and unmarked; if any should be
  drawn, that is new presentation work (explicitly out of scope for #11).

---

## 13. Runtime challenge settings (issue #9 slice)

DualDex now reads the pinned H&S 2.0.5 `SaveBlock3.challengeSettings` at runtime through the
production native reader (`pokemon_read_challenge_settings_gba`). This section records the ABI
evidence and the runtime verification. **This is observability only**: no calculator capability
changed, no trust flag changed, and no live ability is established.

### 13.1 ABI re-derived from the pinned source and the official release ROM

The layout was re-derived with three independent methods that agree:

1. **Compiled probe with the pinned toolchain.** `struct ChallengeSettings` was extracted verbatim
   from the pinned `include/global.h` and compiled with the ARM GNU Toolchain 13.2.Rel1 and the §10
   flags (`-mabi=apcs-gnu` et al.). With those flags `sizeof(struct ChallengeSettings) == 32` (31
   used bytes plus alignment padding) and `offsetof(struct SaveBlock3, challengeSettings) == 16`;
   an AAPCS/clang host probe gives 31 @ 11, which is why earlier hand calculations that used the
   wrong ABI disagreed. The generator
   (`tools/hns-layout/generate_hns_challenge_layout.py`) regenerates the committed layout table
   (`native/src/hns_challenge_settings_layout_gen.h`) from the pinned checkout byte-for-byte under
   `./ci.sh source-check`.
2. **The official release build's own code.** `SetDefaultChallengeSettings` in both
   `build/hns/src/new_game.o` and `build/hns-release/src/new_game.o` RMWs `gSaveblock3 + 0x10/0x14/
   0x1b/0x2c`, and the merged bit masks (`0x010010ac`, `0xffff800c`, `0x1c`, `0x3fb`) match a host
   clang reimplementation of the same statements bit-for-bit — so the internal bit layout is
   identical across compilers (LSB-first within each byte). `GetBattleMoveCategory`,
   `GetCurrentLevelCap`, `GetBaseStatEqualizerValue` and `RandomizerFeatureEnabled` in
   `pokehns.elf` and `pokehns-release.elf` read `gSaveBlock3Ptr + 0x11` bit 1 (`optionStyle`),
   `+ 0x18` bits 3-4 (BaseStatEqualizer) / bits 5-6 (LevelCap), and `+ 0x14/0x15` bits
   (randomizer fields), pinning challengeSettings at SaveBlock3 + 16 and several field positions
   independently of any table.
3. **Source value domains.** Multi-bit domains come from the pinned source's own choice tables:
   LevelCap OFF/NORMAL/HARD, TrainerScalingIVs OFF/SCALE/HARD, MaxPartyIVs' three choices,
   ExpMultiplier x1.0/x1.5/x2.0/x0.0, BaseStatEqualizer's 0/100/255/500 table. A value outside a
   field's domain (LevelCap == 3) is reported out-of-domain, never coerced. `optionStyle` is a
   1-bit source field (0 = per-move split, 1 = type-decided), so it has no out-of-domain encoding.

Field positions (byte.bit, from the start of `ChallengeSettings`; add 16 for SaveBlock3-relative
bytes): optionStyle 1.1; tx_Random_Type 4.5; tx_Random_TypeEffectiveness 4.6;
tx_Random_Abilities 4.7; tx_Random_Moves 5.0; tx_Challenges_BaseStatEqualizer 8.3-4;
tx_Challenges_LevelCap 8.5-6; tx_Challenges_ExpMultiplier 9.0-1; tx_Challenges_Mirror 9.2;
tx_Challenges_Mirror_Thief 9.3; tx_Challenges_NoEVs 9.4; tx_Challenges_TrainerScalingIVs 10.3-4;
tx_Challenges_TrainerScalingEVs 10.5-6; tx_Challenges_MaxPartyIVs 11.0-1; tx_Mode_Fairy_Types
28.6; tx_Mode_Sturdy 28.7; tx_Mode_Legendary_Abilities 29.1.

### 13.2 Release-ROM address correction (runtime-observed)

The production reader reads `gSaveBlock3Ptr` from IWRAM `0x03000178` on every call and requires its
value to equal the compiled `gSaveblock3` base exactly (SaveBlock3 is never re-based by
`SetSaveBlocksPointers`). The first runtime run **failed closed**: the reader declined to read
because the from-source `gSaveblock3` address `0x0200921C` never appears as the pointer's value.
Runtime observation on the official release ROM: the word at `0x03000178` is **`0x02009218`**, which
is exactly `pokehns-release.elf`'s `gSaveblock3` symbol — 4 bytes below the from-source build, the
same −4 EWRAM shift §11.6 found for the party group. §2.1's earlier claim that the SaveBlock arrays
were "confirmed unchanged on the release ROM" was true only for the windows the pointers move
inside, not for the base addresses themselves; the table row is corrected above. DualDex ships the
runtime-verified `0x02009218`.

### 13.3 Runtime verification (scenario 50, read-only)

`tools/hns-runtime-probe/scenarios/50-challenge-settings-fairy-toggle.txt` drives a fresh ROM
through ordinary progression to the new-game challenge menu (the only place 2.0.5 exposes the
menu), sets GAMEMODE → CUSTOM (rows are locked while RECOMMENDED is selected), walks to
ADD FAIRY TYPE, cycles it ON → OFF with DPAD RIGHT, and confirms. The production reader observed:

| Point | optionStyle | fairyTypes | sturdy | legendaryAbilities | everything else |
|---|---|---|---|---|---|
| menu open (boot defaults) | 0 | **1** | 1 | 1 | 0 |
| after confirm | 0 | **0** | 1 | 1 | 0 |

Exactly one decoded field changed — the one option the player changed — and every other field kept
its observed value, including zeros staying observed-zeros. The game was abandoned without saving,
so no battery state persists from the run. Read at the two menu-stage points also demonstrated that
the menu's selections live outside SaveBlock3 until `Task_ConfirmSaveYes` writes them.

`./ci.sh source-check` regenerates the layout table from the pinned checkout; `./ci.sh test` stays
self-contained (the table is committed and the native suite does not need the upstream checkout).

---

## 14. Live battler effective ability + effective types (issue #9 slice)

DualDex now reads the **effective ability and current type state of an authoritative active
battler** directly from the running H&S 2.0.5 battle engine's `gBattleMons[battler]` through the
production reader `pokemon_read_battler_runtime_state_gba()`. **This is LIVE COMBAT STATE /
OBSERVABILITY only**: no calculator capability changed, no trust flag changed, and no ability
mechanic is claimed.

The three ability claims this document now distinguishes explicitly:

| Claim | Source | Status |
|---|---|---|
| Party `abilityNum` | stored party slot/index information | available (party parsing, unchanged) |
| Declared species-slot ability | PR #54's pinned static catalogue | available (identity only) |
| **Effective live `BattlePokemon` ability** | `gBattleMons[battler].ability`, read from the running engine | **established by this section** |

Likewise, species typings are static content and the challenge settings (§13) describe active
rules; `gBattleMons[battler].types` is current combat state, and none of the three substitutes for
another.

### 14.1 Compiled ABI (re-derived, not inherited)

The layout was re-derived from the pinned upstream with the same compiled-probe method as §13, by
`tools/hns-layout/generate_hns_battle_pokemon_layout.py` →
`native/src/hns_battle_pokemon_layout_gen.h` (regenerated byte-for-byte under
`./ci.sh source-check`):

| Item | Compiled value | Source-text agreement |
|---|---|---|
| `sizeof(struct BattlePokemon)` | **136** | the pinned member comments stop at `/*0x62*/ isShiny` (which would suggest 100 bytes); the compiled probe is the evidence and wins, consistent with §6.7's finding that the later comments are stale. No source-comment value is claimed for the stride |
| `ability` | offset **0x20**, width **2** (`enum __attribute__((packed)) Ability`) | agrees with the pinned `/*0x20*/` member comment |
| `types` | offset **0x22**, **3** elements × **1** byte (`enum __attribute__((packed)) Type`) | agrees with the pinned `/*0x22*/ enum Type types[3];` |
| `enum Ability` domain | `ABILITIES_COUNT` = **311** → highest in-domain ID **310** | agrees with the pinned enum text |
| `enum Type` domain | `NUMBER_OF_MON_TYPES` = **21** → highest in-domain ID **20** | agrees with the pinned enum text |

Unlike `hp`/`maxHP`/`status1` (whose upstream comments §6.7 found stale), the `ability` and `types`
comments **agree** with the compiled ABI for this slice; the generator records the cross-check and
would record a discrepancy with the compiled value winning.

**Type sentinel semantics (pinned source, runtime-corroborated):**

* `TYPE_NONE` (0) is the empty-slot sentinel. `MON_TYPES(type1)` duplicates `type1` into the
  second slot (`DEFAULT(type1, ...)`), so a monotype stores `{type1, type1, ...}` at switch-in —
  the empty-slot encoding is **not** what a live battler carries for its second slot.
* `TYPE_MYSTERY` (10) is a battle-only value: `Battle_SwitchIn`-path code
  (`src/battle_main.c:3574-3576`, also the form-change path at `:3671-3673`) sets
  `types[0..1]` from the species and **`types[2] = TYPE_MYSTERY`** on every send-out. The third
  slot is battle state, not species data.
* Duplicates are meaningful (Terastallization sets all three slots equal via `GetBattlerTypes`
  consumers), so values are exposed verbatim: no unknown→Normal mapping, no sentinel→empty
  mapping, no de-duplication.
* `ABILITY_NONE` (0) is part of the pinned `enum Ability`; an observed 0 is preserved as an
  observed zero and never substituted.

The reader flags any observed value outside these domains (`ability > 310`, a type byte > 20) as
`OBSERVED_INVALID` and still reports the raw bytes — it never coerces.

### 14.2 Production reader and trust contract

`pokemon_read_battler_runtime_state_gba(read, user, ewram, ewram_size, config, role, out)` returns
`BattlerRuntimeState` and publishes an observation only when **all** of these hold:

1. the config declares the live layout (only the exact H&S 2.0.5 config does; every vanilla config
   leaves the five new `battle_mons_ability/types_*` fields zero, so no vanilla `BattlePokemon` is
   ever reinterpreted through the H&S structure);
2. the declared layout equals the generated ABI table exactly (a runtime drift guard; the config
   itself is initialised from the generated macros so compile-time drift is also impossible);
3. `pokemon_read_battle_lifecycle()` reports **ACTIVE** — `INACTIVE`, `INITIALIZING`, `ENDING` and
   `UNKNOWN` all return nothing, so battle teardown and pre-battle frames cannot publish state;
4. the battler comes from the same authoritative machinery as every other battle surface: the
   player role resolves the single present player-side battler through `gBattlerPositions` (the
   shared `resolve_single_player_battler()` helper), which is **AMBIGUOUS with two player-side
   battlers** and **UNAVAILABLE while that battler is fainted** — never a defaulted battler 0;
   the opponent role goes through `pokemon_resolve_active_enemy()`, which is **AMBIGUOUS in
   doubles** (nothing is named), never "the first enemy", and **UNAVAILABLE while the resolved
   enemy is at 0 HP** (the forced-switch window);
5. the battler index is inside the compiled battler count and the battler is not absent
   (`gAbsentBattlerFlags`);
6. the complete ability and types bytes are readable through the bounds-checked reader.

There is no battler-0 default, no party-slot-0 default, no reuse of the previous battler's ability,
no retention through faint/replacement transitions, and no retention after battle teardown: every
failure path returns a zeroed `UNAVAILABLE` (or a zeroed `AMBIGUOUS`) snapshot.

Kotlin: `HnsBattlerRuntimeState` (JNI tuple `nativeReadBattlerRuntimeState(gameId, role)`),
published per poll as `CompanionViewModel.playerBattlerState` / `enemyBattlerState` and cleared on
battle exit, trust loss and ROM/profile switch. The observed numeric ability ID is named against
the active data pack's pinned PR #54 catalogue (`resolveAbilityIdentity`); the catalogue is used
**only to name** the observed ID and is never the source of it. `ABILITY` remains in the
calculator's `unknownFields`; nothing in this slice feeds calculator preparation.

### 14.3 Runtime verification (Scenarios 20 and 42, read-only)

Official 2.0.5 release ROM, host mGBA core, fresh legal battery save. The probe now samples both
roles through the production reader on every matrix row and via a `battler-state` script command.

**Scenario 20 (wild battle, full lifecycle).** Wild Hoothoot vs. starter Chikorita:

| Point | role | battler | party slot | ability | types | status |
|---|---|---|---|---|---|---|
| battle active | player | 0 | 0 | **65 (OVERGROW)** | **13,13,10** | OBSERVED |
| battle active | enemy | 1 | 0 | **51 (KEEN EYE)** | **1,3,10** | OBSERVED |
| teardown complete | both | — | — | — | — | UNAVAILABLE (nothing retained) |

Both IDs resolve through the PR #54 catalogue. The wild Hoothoot is also the optional proof this
slice wanted for cheap: its effective ability (51, KEEN EYE) is the species' **slot-1** declaration
(`abilityNum` from the wild IVs), so the live observation differs from the naive slot-0
assumption (INSOMNIA, 15) without any manufactured save state.

The types rows corroborate the §14.1 engine semantics on the running ROM: Chikorita (monotype
Grass) carries `{13,13,10}`, Hoothoot (Normal/Flying) carries `{1,3,10}` — third slot
`TYPE_MYSTERY` written by the engine at send-out.

**Scenario 42 (trainer battle, player voluntary switch).** Mikey's Hoothoot vs. Chikorita; the
player switches Chikorita → Rattata through ordinary controller input:

| Point | role | battler | party slot | species | ability | types | status |
|---|---|---|---|---|---|---|---|
| battle active | player | 0 | 0 | Chikorita | 65 (OVERGROW) | 13,13,10 | OBSERVED |
| battle active | enemy | 1 | 0 | Hoothoot | 15 (INSOMNIA) | 1,3,10 | OBSERVED |
| after the player's switch commits | player | 0 | **1** | **Rattata** | **62 (GUTS)** | **1,1,10** | OBSERVED |
| after the player's switch commits | enemy | 1 | 0 | Hoothoot | 15 (INSOMNIA) | 1,3,10 | OBSERVED |

The observation followed the authoritative party index (`gBattlerPartyIndexes[0]` 0 → 1) and the
rewritten `gBattleMons[0]`; Chikorita's ability 65 appears nowhere after the switch.

### 14.4 Mutation control

With the production reader committed, the ability offset in the generated table was mutated
(0x20 → 0x22) and the suite re-run: **9 semantic tests failed** (wrong effective ability observed,
layout-pin mismatches, out-of-domain verdicts) — not a checksum failure. The types offset was
mutated the same way with the same result. The table was then regenerated from the pinned upstream
and the suite returned to **78/78 PASS**.

### 14.5 What this slice could not verify

* **Scenario 44 (opponent voluntary switch) was not re-run here.** Its fixture chain requires a
  `stage34_don_ready.sav` produced by Scenario 34, and Scenario 34 currently fails deterministically
  in this environment at its own overworld step (`walk LEFT 1` out of (23,24) after Mikey's battle
  is scripted complete, two attempts, independent of this slice's readers — the same run's Scenario
  33 produced its save normally and Scenario 42 above consumed it successfully). The
  opponent-switch behaviour of the live-battler reader remains covered by the production reader's
  native tests (`test_hns_battler_state_opponent_switch_follows_authority`) and by §11.10's
  Scenario 44 evidence for the underlying battler-resolution machinery. Fixing Scenario 34's walk
  is out of this slice's scope.
* **Doubles** were not exercised on the ROM; the AMBIGUOUS verdict is synthetic-suite verified.
* Nothing here establishes engine **compatibility**: the observations say what the engine holds,
  not that DualDex can model the mechanics that depend on them.

---

## 15. Calculator Gap C1 — exact type system + Fairy toggle evidence (issue #9)

**Added by:** the Gap C1 slice (type chart + Fairy mode toggle), branched from commit `5e6b46de1d82982c6912d78019406e016b58137c` (PR #61 merge).

This section documents the upstream source provenance and verification evidence for closing the type-system gap in H&S 2.0.5.

### 15.1 Upstream Source Evidence

All mappings are extracted deterministically from `PokemonHnS-Development/pokehns-expansion` at commit `1f42b74dff0e9fe942419845d040663dd829a973`:

1. **Modern Type Chart:**
   - In `include/config/battle.h:53`: `#define B_UPDATED_TYPE_MATCHUPS GEN_LATEST` where `GEN_LATEST == GEN_9` (`include/config/general.h:17`).
   - In `src/data/types_info.h:8`: `#define STL_RS 1.0` (evaluated at `GEN_9`, so Ghost and Dark against Steel are neutral 1.0x).
   - The 21x21 upstream matrix (`src/data/types_info.h:18-40`) is extracted by `tools/hns-type-system/generate_hns_type_system.py` into `tools/calc-bundler/hns_type_chart.json` (19x19 representable types).
2. **Fairy Toggle Mode (`tx_Mode_Fairy_Types`):**
   - In `src/pokemon.c:5704`: `sPreFairyTypes` maps 20 species to their pre-Fairy typings when `tx_Mode_Fairy_Types == 0`.
   - In `src/pokemon.c:5748`: `sFairyMoveAltTypes` maps 34 Fairy moves to their alternate typings when `tx_Mode_Fairy_Types == 0` (falling back to `TYPE_NORMAL` per `src/pokemon.c:5795`).
   - Extracted into `app/src/main/java/com/dualdex/pokemon/hns/HnsFairyTypeMappings.kt`.
   - `--verify` verified against local upstream checkout with zero drift.

### 15.2 Request-Local Engine Isolation

In `tools/calc-bundler/entry.js`:
- Incoming request specifies `typeSystem: "hns_2_0_5"`.
- A request-local duck-typed generation facade (`num = 3`, `types = HNS_TYPES_PROVIDER`) is passed to `calculate()`.
- `@smogon/calc` ADV arithmetic (`gen.num == 3`) runs unchanged; type effectiveness is resolved from the custom provider without modifying global library tables or changing generations.
- If `typeSystem` is omitted or null, standard Gen 3 tables apply.

### 15.3 Move Category Coupling and Status Move Invariant

In H&S `GetBattleMoveCategory()` (`src/battle_util.c:9183`):
```c
if (IsBattleMoveStatus(move))
    return DAMAGE_CATEGORY_STATUS;

if (B_PHYSICAL_SPECIAL_SPLIT < GEN_4 ||
    gSaveBlock3Ptr->challengeSettings.optionStyle == 1)
    return gTypesInfo[GetBattleMoveType(move)].damageCategory;

return GetMoveCategory(move);
```
**Status wins before `optionStyle`**. Under `optionStyle == 1` (`TYPE_BASED`), only non-status moves derive damage category from the effective move type.

DualDex models this invariant faithfully:
- In `CalcDataOverrides.kt`:
  - If a move is Status (e.g. Charm, Fairy / Status / 0 BP), `category = "Status"` is **retained** under both `PER_MOVE_SPLIT` and `TYPE_BASED`.
  - Non-status moves under `TYPE_BASED` have `category = null`, triggering type-based Physical/Special derivation.
- In `tools/calc-bundler/entry.js`:
  - When `typeSystem === 'hns_2_0_5'` and `overrides.type === 'Fairy'` with category omitted, `category = 'Special'` is applied only for damaging moves (`!isBaseStatus`).
- Verified for Charm across Fairy ON, Fairy OFF, and PER_MOVE_SPLIT in both `CalcDataOverridesTest.kt` and `test_js_calc.c`.

### 15.4 Policy Invariant: Request TypeSystem Requirement

`CalcCapabilityPolicy.kt` evaluates `typeChartModelled` to conditionally clear `HNS_TYPE_CHART_NOT_MODELLED`. To prevent latent promotion bugs, the policy strictly requires:
```kotlin
request.typeSystem == "hns_2_0_5"
```
If the evaluated request is missing `typeSystem` or supplies any other value, `HNS_TYPE_CHART_NOT_MODELLED` remains active, even if exact trust and challenge rules are observed (tested in `CalcCapabilityPolicyTest` test 15).

### 15.5 Verification Evidence

- `native/tests/test_js_calc.c:check_gap_c1_type_system`:
  - Ghost -> Steel: ADV = 20..24 (eff 0.5) vs H&S = 41..49 (eff 1.0).
  - Dark -> Steel: ADV = 17..21 (eff 0.5) vs H&S = 35..42 (eff 1.0).
  - Fairy -> Dragon/Flying: Clefable Moonblast vs Dragonite = 107..126 (eff 2.0).
  - Dragon -> Fairy immunity: Dragonite Dragon Claw vs Clefable = 0..0 (eff 0.0).
  - OptionStyle category coupling: Dazzling Gleam Special 48..57 vs Physical 38..45.
  - Charm status preservation: Charm Fairy ON = Status (0 dmg), Charm Fairy OFF = Status (0 dmg, Normal), Charm no-category = Status (0 dmg).
  - Request isolation: H&S -> ADV -> H&S -> ADV (zero table mutation).
  - Unsupported typeSystem returns `success: false`.
- CI contract updated:
  - `./ci.sh test` includes `test_generate_hns_type_system.py` unit tests and QuickJS suite (1887 checks).
  - `./ci.sh source-check` includes `generate_hns_type_system.py --verify`.
- **Behavioral Semantic Mutation Control:**
  - Mutated Ghost -> Steel in `hns_type_chart.json` from `1.0` to `0.5` and rebuilt bundle.
  - Executed `./ci.sh test`: `gap_c1_ghost_steel_matchup` and `gap_c1_request_isolation` failed immediately with 5 failed assertions:
    - `[FAIL] gap_c1_ghost_steel_matchup / H&S effectiveness is 1.0: expected 1.0000, got 0.5000`
    - `[FAIL] gap_c1_ghost_steel_matchup / H&S minDamage is 41: expected 41, got 20`
    - `[FAIL] gap_c1_ghost_steel_matchup / H&S maxDamage is 49: expected 49, got 24`
    - `[FAIL] gap_c1_request_isolation / run 1 H&S minDamage: expected 41, got 20`
    - `[FAIL] gap_c1_request_isolation / run 3 H&S minDamage: expected 41, got 20`
  - Restoring `1.0` and rebuilding bundle restored 100% passing results (1887 passed, 0 failed).

---

## 16. Gap C2: Authoritative Effective Ability Input and Conditional Capability Support

This section documents the evidence, mechanics comparison, and verification for H&S 2.0.5 Calculator Gap C2.

### 16.1 Source Audit: H&S Battle Engine vs `@smogon/calc` ADV Pipeline

In pinned H&S `Release-v2.0.5` (`1f42b74dff0e9fe942419845d040663dd829a973`):

1. **Zero-Damage-Effect Abilities (`ABILITY_NONE`, ID 0; `ABILITY_KEEN_EYE`, ID 51; `ABILITY_INSOMNIA`, ID 15)**:
   Inspected in `src/battle_util.c`: None of these abilities modify attack stat, defense stat, base power, or damage modifiers. They have zero move-damage effect in both H&S and `@smogon/calc`. Classified as `PROVEN_NO_DAMAGE_EFFECT` and supported in C2.

2. **Compound Modifier and Stat-Stage Ordering Divergence for Damage-Relevant Abilities**:
   While isolated single-ability multipliers match between H&S and ADV in isolation (e.g. Guts $1.5\times$, Thick Fat $0.5\times$, Huge Power / Pure Power $2.0\times$), H&S and ADV compose modifiers differently and apply stat stages in a different order:
   - **Fixed-point composition vs sequential flooring**:
     In H&S (`src/battle_util.c:6980-7210`), ability modifiers accumulate into a single 4.12 fixed-point `modifier` via `uq4_12_multiply_half_down`, which is applied to the stat once. In `@smogon/calc` Gen 3 (`calc/src/mechanics/gen3.ts`), ability modifiers are applied sequentially with intermediate integer flooring.
     *Concrete Counter-Example*:
     Raw Attack 105, statused Guts attacker ($1.5\times$) using a Fire move against a Thick Fat defender ($0.5\times$):
     - **H&S**: Combined modifier $= 1.5 \times 0.5 = 0.75$ (`UQ_4_12(0.75) = 3072`). Applied to Attack 105:
       `((105 * 3072) + 2047) >> 12 = 324607 >> 12 = 79`.
     - **ADV**: Thick Fat applied first: $\lfloor 105 / 2 \rfloor = 52$. Guts applied second: $\lfloor 52 \times 1.5 \rfloor = 78$.
     - Result: **H&S = 79, ADV = 78**. Arithmetic equivalence disproven in multi-ability interactions.
   - **Stat stage application order**:
     In H&S, stat stages are applied to base stats *before* ability multipliers are applied. In `@smogon/calc` ADV, these ability modifiers are applied *before* stat stages. Because DualDex carries live stat stages from RAM, non-neutral stat stages compound intermediate flooring divergence.
   - **Starter Pinch Abilities Divergence (`OVERGROW`, ID 65; `BLAZE`, ID 66; `TORRENT`, ID 67; `SWARM`, ID 68)**:
     In `src/battle_util.c:7010-7025`: H&S modifies **Attack Stat** in `CalcAttackStat`:
     ```c
     if (gBattleMons[battlerAtk].hp <= gBattleMons[battlerAtk].maxHP / 3)
         modifier = uq4_12_multiply_half_down(modifier, UQ_4_12(1.5));
     ```
     In `@smogon/calc` Gen 3 (`gen3.ts`): Pinch abilities modify **Base Power**: `bp = Math.floor(bp * 1.5);`.
     An exhaustive sweep reveals 17,750 damage range mismatches between stat modification and BP modification.

   *Verdict*: All damage-affecting abilities (`GUTS`, `THICK FAT`, `HUGE POWER`, `PURE POWER`, starter pinch abilities, and ~80 modern abilities) are classified as `UNSUPPORTED_DAMAGE_RELEVANT` and strictly fail-closed with `HNS_ABILITY_EFFECT_NOT_MODELLED`. Gap C2 establishes the conditional ability capability model by safely supporting proven-zero-damage abilities while strictly refusing all unmodelled damage modifiers.

### 16.2 Runtime Active-Party-Slot Matching, Boundary Ownership, and Race Prevention

1. **Party Slot Provenance Invariant:**
   `CalcPokemonInput` carries `partySlot: Int? = null` provenance so the central boundary can verify slot identity independently of the presenter:
   - In `CalcParticipantPresenter.resolveEffectiveAbility`:
     - Attacker: accepted only when `state.partySlot == selectedPartyIndex`.
     - Defender: accepted only when `state.partySlot == activeEnemySlot`.
     - Any slot mismatch, unobserved state, `OBSERVED_INVALID`, or `AMBIGUOUS` (doubles) resolves to `EffectiveAbilityResolution.UnknownAbility`, setting `ability = null` and recording `CalcInputField.ABILITY` in `unknownFields`.
2. **Central Boundary Slot Verification:**
   In `CalcRequestBoundary.reconcileParticipantAbility`:
   - Requires `participant.partySlot != null && state?.partySlot != null && state.partySlot == participant.partySlot`.
   - If the observation's party slot does not match the participant's provenance, or if provenance is missing on a live read, boundary clears `ability = null` and marks `ABILITY` in `unknownFields`.
   - Live-read participants enforce boundary ownership against the authoritative runtime observation; caller-supplied abilities cannot override or fabricate observed abilities.
3. **Atomic Snapshotting in Recalculate:**
   `CalcTabScreenView.recalculate()` captures `playerBattlerState` and `enemyBattlerState` StateFlows into immutable local values once at method entry and passes those identical snapshots to both `CalcParticipantPresenter` and `CalcRequestBoundary`. This eliminates time-of-check to time-of-use race conditions between presenter UI formatting and boundary validation during live polling or party switches.
4. **Authoritative Numeric ID Determines Capability:**
   In `CalcCapabilityPolicy.collectRequestLimitations`, capability for live observations is determined strictly by the authoritative runtime numeric `abilityId` (`HnsAbilityRegistry.classify(input.abilityId)`). The PR #54 catalogue name is preserved for UI display and diagnostics, but never determines capability. A malformed observation with an unsupported numeric ID (e.g. 65 Overgrow) paired with a supported catalogue name (e.g. "Keen Eye") fails closed with `HNS_ABILITY_EFFECT_NOT_MODELLED`.
5. **Direct Domain Range Checking:**
   `abilityId` is directly range-checked against the pinned ability domain (`0..HnsBattlerRuntimeStateIds.ABILITY_ID_MAX`, i.e. 0..310) across native tuple decoding (`HnsBattlerRuntimeState.fromNativeArray`), presenter resolution (`CalcParticipantPresenter.resolveEffectiveAbility`), central boundary reconciliation (`CalcRequestBoundary.reconcileParticipantAbility`), and capability policy evaluation (`CalcCapabilityPolicy.collectRequestLimitations`). Out-of-domain IDs fail closed with `HNS_EFFECTIVE_ABILITY_UNREADABLE`.
6. **Defense-in-Depth ID/Name Binding Verification:**
   `BattlerRuntimeObservation` carries both `state.abilityId` and `abilityIdentity` (`DeclaredAbility.Declared?`).
   Both `CalcParticipantPresenter` and `CalcRequestBoundary` verify that:
   `observation.abilityIdentity.abilityId == state.abilityId` (and if `state.abilityId == 0`, that `abilityIdentity` is not a declared non-zero ability).
   Any mismatch immediately treats the observation as unreadable (`EffectiveAbilityResolution.UnknownAbility`), clearing `ability = null` and recording `HNS_EFFECTIVE_ABILITY_UNREADABLE`.

### 16.3 Default Ability Substitution Prevention

`@smogon/calc`'s `Pokemon` constructor defaults missing or `"None"` abilities to `species.abilities[0]`:
```javascript
this.ability = options.ability || (species.abilities ? species.abilities[0] : undefined);
```
In vanilla Gen 3, this matches profile data. In H&S 2.0.5, this would erroneously assign species slot 0 abilities (e.g., Guts to a Machamp that has No Guard or None).
- In `tools/calc-bundler/entry.js`:
  ```javascript
  function resolveAbility(rawAbility, isHns) {
    if (!isHns) return rawAbility;
    if (rawAbility === undefined || rawAbility === null || rawAbility === '' ||
        rawAbility === 'None' || rawAbility === '(other)') {
      return '(other)';
    }
    return rawAbility;
  }
  ```
  Passing `'(other)'` informs `@smogon/calc` that an ability is explicitly set to an unrecognized/neutral ability, preventing fallback to `species.abilities[0]`.

### 16.4 Verification Evidence

1. **Host QuickJS Tests (`native/tests/test_js_calc.c:check_gap_c2_abilities`)**:
   - `check_gap_c2_abilities()` executed under QuickJS:
     - Default ability substitution prevention: Burned Machamp (Cross Chop vs Dusclops):
       - With `ability = null`: deals 40..48 damage (burn halved, Guts NOT substituted).
       - In vanilla Gen 3 (control): deals 117..138 damage (Guts substituted by default).
     - Documented in test suite why Guts and Thick Fat are not modelled for H&S in policy due to compound modifier composition and stat-stage application order.
     - Proven-no-damage-effect abilities: Keen Eye, Insomnia, and None produce exact identical damage (33..39).
   - All 1947 checks in `test_js_calc.c` pass with 0 failures.

2. **Kotlin Unit Test Suite (`CalcHnsAbilityTest.kt`)**:
   - 21 comprehensive tests verifying:
     - Registry classification of `PROVEN_NO_DAMAGE_EFFECT` (`None`, `Keen Eye`, `Insomnia`) vs `UNSUPPORTED_DAMAGE_RELEVANT` (`Guts`, `Thick Fat`, `Huge Power`, `Pure Power`, `Overgrow`, etc.).
     - Presenter defense-in-depth ID/name mismatch rejection.
     - Presenter active slot matching and partySlot provenance.
     - Boundary anti-spoofing overriding with live read on matching slot.
     - Boundary rejecting observation on partySlot mismatch and clearing ability.
     - Boundary rejecting observation when LIVE_READ participant has null partySlot provenance.
     - Boundary defense-in-depth ID/name mismatch rejection.
     - Authoritative numeric `abilityId` driving capability verdict rather than identity name string.
     - Direct ability domain range-checking at boundary and policy layers.
     - Policy gating to `HNS_ABILITY_EFFECT_NOT_MODELLED`, `HNS_EFFECTIVE_ABILITY_UNREADABLE`, `HNS_ITEM_EFFECT_NOT_MODELLED`, `HNS_EFFECTIVE_ITEM_UNREADABLE`, `HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE`, and the next mechanics blocker `BADGE_BOOST_NOT_MODELLED` (the blanket `HNS_HELD_ITEM_SYSTEM_NOT_MODELLED` is removed).
   - All 529 unit tests pass.

3. **Behavioral Semantic Mutation Controls:**
   - **Control 1 (Slot Matching)**: Mutated `CalcParticipantPresenter.kt` active slot matching (`state.partySlot != expectedPartySlot + 1`). Executed unit tests: 4 tests failed immediately (`attacker slot mismatch leaves ability unreadable`, `defender slot mismatch leaves ability unreadable`, presenter tests).
   - **Control 2 (Boundary Provenance)**: Mutated `CalcRequestBoundary.reconcileParticipantAbility` to ignore slot mismatch (`true || state.partySlot == participant.partySlot`). Executed unit tests: `CalcHnsAbilityTest` test `reconcileLiveBattlerAbilities rejects observation on partySlot mismatch and clears ability` failed immediately.
   - **Control 3 (Defense-in-depth)**: Mutated ID/name verification in boundary. Executed unit tests: `reconcileLiveBattlerAbilities rejects observation with mismatched abilityId and abilityIdentity` failed immediately.
   - **Control 4 (Numeric ID Authority)**: Mutated `CalcCapabilityPolicy.collectRequestLimitations` to classify by display name (`classify(input.ability)`) rather than numeric ID (`classify(input.abilityId)`). Executed unit tests: `live read capability verdict derives from authoritative abilityId not identity name` failed immediately with `AssertionError`.
   - **Control 5 (Domain Range Check)**: Mutated `CalcRequestBoundary.reconcileParticipantAbility` and `CalcCapabilityPolicy.collectRequestLimitations` to omit the `0..ABILITY_ID_MAX` check. Executed unit tests: `live read abilityId directly range-checked against pinned ability domain` failed immediately with `AssertionError`.
   - All mutants reverted and test suite confirmed 100% green.

---

## 17. Gap C3: authoritative held-item identity and conditional item capability (issue #9)

Status vocabulary for this section: **SOURCE VERIFIED**, **ABI VERIFIED**, **UNIT/HOST VERIFIED**,
**RUNTIME VERIFIED**, **NOT YET RUNTIME VERIFIED**. Synthetic fixtures are labelled UNIT/HOST, never
runtime.

### 17.1 Source audit (pinned commit `1f42b74dff0e9fe942419845d040663dd829a973`)

- **Current item identity is `gBattleMons[battler].item`.** It is rewritten when an item is consumed
  (`[src/battle_script_commands.c:6607]`), stolen (`[src/battle_script_commands.c:2227]`, `:2239]`;
  `[src/battle_move_resolution.c:3095]`, `:3100]`), knocked off (`[src/battle_move_resolution.c:3055]`),
  swapped by Trick/Switcheroo (`[src/battle_script_commands.c:9818-9819]`), or flung
  (`[data/battle_scripts_1.s:528]` removeitem). The controller ALSO pushes in-battle item changes back
  into the party record immediately through `REQUEST_HELDITEM_BATTLE`:
  `BtlController_EmitSetMonData(..., REQUEST_HELDITEM_BATTLE, ...)` is emitted on consume
  (`[src/battle_script_commands.c:6611]`), Knock Off (`[src/battle_move_resolution.c:3063]`), steal
  (`[src/battle_script_commands.c:2242-2249]`), Trick/Switcheroo (`[src/battle_script_commands.c:9824-9827]`)
  and Fling (`[src/battle_util.c:10216]`), and the controller handler writes it into party storage with
  `SetMonData(&party[monId], MON_DATA_HELD_ITEM, ...)` (`[src/battle_controllers.c:1804-1806]`). The party
  record is therefore **not** frozen until battle end — it is a separate, asynchronously-updated copy.
  `gBattleMons[battler].item` remains the synchronous battle-engine authority.
- **Damage-path derivation:** `CalculateMoveDamage` sets `holdEffectAtk/Def = GetBattlerHoldEffect(...)`
  (`[src/battle_util.c:8231-8234]`) → `GetBattlerHoldEffectInternal` (`:5813-5838`) →
  `GetItemHoldEffect(gBattleMons[battler].item)` (`[src/item.c:860-863]`); the parameter comes from
  `GetBattlerHoldEffectParam` (`[src/battle_util.c:5849-5855]`, `[src/item.c:865-867]`).
- **Type boosters are ×1.2 in H&S, not ×1.1.** `I_TYPE_BOOST_POWER = GEN_LATEST = GEN_9`
  (`[include/config/item.h:15]`, `[include/config/general.h:73]`, `:70]`) ⇒ `TYPE_BOOST_PARAM = 20`
  (`[src/data/items.h:10]`) ⇒ base-power ×1.2 (`[src/battle_util.c:6808]`, `:6839-6843]`, `:6871]`).
  The generated catalogue records Charcoal `holdEffectParam = 20`, confirming the source value.
- **Choice Band is ×1.5 but composition diverges.** Applied in `CalcAttackStat` after stat stages and
  abilities via fixed-point `uq4_12_multiply_half_down` (`[src/battle_util.c:7177-7180]`), floored once at
  `:7191]`; the ADV pipeline applies item transforms with intermediate integer floors. Not equivalent.
- **Gems are ×1.3 and consumed.** `GEM_BOOST_PARAM = 30` (`[src/data/items.h:9]`,
  `[include/config/item.h:13]`), applied to base power (`[src/battle_util.c:6633-6634]`), queued for
  consumption (`[src/battle_script_commands.c:1347-1353]`, `[data/battle_scripts_1.s:7058-7064]`).
  The generated catalogue records `holdEffectParam = 30`.
- **Resist berries are ×0.5 and consumed** (`[src/battle_util.c:7686-7696]`,
  `[src/battle_script_commands.c:1484-1501]`); **Focus Sash/Band, Leftovers, Shell Bell and Rocky Helmet**
  affect survival/HP/KO presentation (`[src/battle_util.c:8193-8206]`,
  `[src/battle_hold_effects.c:536-656]`, `:245-262]`).
- **Harmless utility (static, ordinary-damage-only):** Exp. Share (`HOLD_EFFECT_EXP_SHARE`), Soothe Bell
  (`HOLD_EFFECT_FRIENDSHIP_UP`, `[src/pokemon.c:7777]`), Amulet Coin (`HOLD_EFFECT_DOUBLE_PRIZE`,
  `[src/battle_main.c:3189]`), Cleanse Tag (`HOLD_EFFECT_REPEL`, `[src/wild_encounter.c:1334]`) and
  Lucky Egg (`HOLD_EFFECT_LUCKY_EGG`, `[src/battle_script_commands.c:11982]`) have no crit/power/type/
  stat/survival/KO/HP/status/speed interaction and are `PROVEN_NO_ORDINARY_DAMAGE_EFFECT`. This is a
  context-free statement only about their OWN hold effect; the item's identity is still damage-relevant
  to the item-dependent moves below.

**Move/item interaction audit (SOURCE VERIFIED).** The pinned damage path reads held-item state for a
complete, audited set of moves, so the static item classification above is not sufficient on its own.
Every read found:

| Move | ID | Interaction | Pinned source |
|---|---|---|---|
| Fling | 374 | attacker item identity | `CalcMoveBasePower EFFECT_FLING` `[src/battle_util.c:6344-6346]` |
| Natural Gift | 363 | attacker item identity (power + type) | `[src/battle_util.c:6395-6397]`, `[src/battle_main.c:6327-6330]` |
| Acrobatics | 512 | attacker item absence | `[src/battle_util.c:6421-6424]` |
| Knock Off | 282 | defender item presence | `[src/battle_util.c:6619-6623]` |
| Poltergeist | 737 | defender item presence (move fails) | `[src/battle_move_resolution.c:1301-1305]` |
| Judgment | 449 | attacker item identity (type) | `GetDynamicMoveType EFFECT_CHANGE_TYPE_ON_ITEM` `[src/battle_main.c:6284-6286]` |
| Techno Blast | 546 | attacker item identity (type) | `[src/battle_main.c:6284-6286]` |
| Multi-Attack | 672 | attacker item identity (type) | `[src/battle_main.c:6284-6286]` |

Audited and deliberately excluded: Weather Ball (only the Utility Umbrella hold effect, which no
supported item has, `[src/battle_main.c:6212-6240]`), Low Kick / Heat Crash (weight via the Float Stone
hold effect, `GetBattlerWeight` `[src/battle_util.c:6053-6087]`, and Float Stone is refused statically),
Pluck/Bug Bite/Thief/Covet (item moved after the formula), Sucker Punch (reads the defender's chosen
move), and the gem/plate/choice/pinch-berry hold effects (ordinary-move multipliers already refused
statically). Because the authorized request strips supported items before the engine, every audited move
is refused with `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` before it can no-op on the wrong item state. See
`HnsMoveItemInteractionRegistry` and `docs/HNS_2_0_5_CALCULATOR_CAPABILITY.md` §7.2.

### 17.2 Exact item catalogue (SOURCE VERIFIED)

`tools/hns-items/generate_hns_items.py` preprocesses the pinned `src/item.c` with the pinned
`arm-none-eabi-cpp` and derives the exact `enum Item` domain (`ITEMS_COUNT = 901`, `ITEM_ID_MAX = 900`)
plus every `gItemsInfo[]` symbol, display name, `holdEffect` and `holdEffectParam`. The committed
`app/src/main/java/com/dualdex/pokemon/hns/Hns205ItemCatalogue.kt` regenerates byte-for-byte
(`--verify`). Enum aliases (e.g. `ITEM_ENERGYPOWDER = ITEM_ENERGY_POWDER`) resolve to one canonical
identity; an unresolved alias, missing table entry, duplicated ID, or out-of-domain constant is a hard
error. `ItemDatabase.expansionMap` is not H&S authority.

### 17.3 ABI (ABI VERIFIED)

The compiled probe adds `__builtin_offsetof(struct BattlePokemon, item)`,
`sizeof(((struct BattlePokemon *)0)->item)` and `ITEMS_COUNT`, compiled with the §3 flags:

| Constant | Generated value | Upstream source comment |
|---|---|---|
| `HNS_BATTLE_POKEMON_ITEM_OFFSET` | `0x30` (48) | `0x2F` (stale hand-written comment) |
| `HNS_BATTLE_POKEMON_ITEM_SIZE` | `2` | — |
| `HNS_BATTLE_POKEMON_ITEM_ID_MAX` | `900` (`ITEMS_COUNT - 1`) | — |

The compiled offset `0x30` agrees with the independently recorded §3.2 row and is explained by natural
alignment (`u16 maxHP` rounds to `0x2E`, pushing `item` to `0x30`); the `/*0x2F*/` comment is stale, and
the same compiled layout already gives `hp = 0x2A`, which the production config uses. The native
layout-pin test independently pins `136 / 0x30 / 2 / 900`, so a generated-table or reader drift fails
the semantic tests, and source-check regenerates the header byte-for-byte.

### 17.4 Live current item path (UNIT/HOST VERIFIED, NOT YET RUNTIME VERIFIED)

`BattlerRuntimeState` gains `item_observed` / `item_invalid` / `item_id`; the reader decodes
`gBattleMons[battler].item` through the bounds-checked reader under the same lifecycle/battler
resolution as the ability/types read, and reports an out-of-domain ID as `OBSERVED_INVALID` with the raw
value preserved. The JNI tuple is 16 ints (`[13..15]` = item observed/invalid/id). Kotlin
`HnsBattlerRuntimeState.fromNativeArray` decodes them and `resolveItemIdentity()` names the ID from the
generated catalogue. Reading `gBattleMons[battler].item` on the official ROM was not performed for this
slice.

### 17.5 Stored party item vs current battle item (UNIT/HOST VERIFIED)

`CalcParticipantPresenter.resolveEffectiveItem` and `CalcRequestBoundary.reconcileParticipantItem`
implement and test: active player/enemy slot match → current battle item, including authoritative
`ITEM_NONE` overriding a stale nonzero party item; bench player → parsed party item; opponent slot
mismatch, faint window, doubles ambiguity and unverified reads → unreadable, never a fallback. The
`activeBattle` flag is a hint, not the authority: `reconcileLiveBattlerItems` treats any supplied runtime
observation as battle evidence, so a raw LIVE_READ caller cannot declare `activeBattle = false` while
handing over an observed battler and thereby downgrade to the party item. Capability is
`HnsItemRegistry.classify(itemId)` (static) combined with `HnsMoveItemInteractionRegistry` (contextual),
never a display name; manual names resolve through the exact catalogue. These are synthetic-fixture unit
tests, not runtime evidence.

### 17.6 Verification evidence

1. **Kotlin unit tests** (`Hns205ItemCatalogueTest.kt`, `HnsItemRegistryTest.kt`,
   `HnsMoveItemInteractionTest.kt`, `CalcHnsItemTest.kt`): exact catalogue identity/domain/alias/name
   resolution, numeric-ID authority, generic-`ItemDatabase` mutation independence, presenter
   active/bench/out-of-battle/faint behaviour, boundary anti-spoofing, opponent slot matching and doubles
   refusal, ITEM_NONE clearing, manual resolution, the context-free static subset (Amulet Coin + ordinary
   move) versus the contextual gate (Amulet Coin + Fling, ITEM_NONE + Acrobatics, defender item + Knock
   Off) and the raw-boundary battle-context regression, and the next-blocker assertion
   (`BADGE_BOOST_NOT_MODELLED` present, item blockers and the blanket absent, UNSUPPORTED,
   `request == null`).
2. **Native tests** (`native/tests/test_pokemon_reader.c`): item offset/width/domain pins, `ITEM_NONE` as
   observed zero, switch/consume rewrite clearing the old item, out-of-domain raw preservation, faint
   window publishing no stale item.
3. **Generator tests** (`tools/hns-items/test_generate_hns_items.py`): explicit/alias/implicit/macro-list
   enum resolution, bookkeeping anchors, fail-closed unresolved aliases and arithmetic, contiguous
   catalogue construction, and Kotlin rendering.
4. **QuickJS host tests** (`native/tests/test_js_calc.c:check_gap_c3_items`): an omitted item and
   `"item": "None"` are the same calculation; an unmodelled item name is a silent no-op; a name the engine
   models changes damage — which is why the policy never forwards a raw H&S source name. Suite total:
   1954 checks, 0 failures.
5. **Source-check:** `tools/hns-items/generate_hns_items.py --verify` and the extended
   `tools/hns-layout/generate_hns_battle_pokemon_layout.py --verify` both report zero drift against the
   pinned checkout.

### 17.7 Mutation controls

- **Control 1 (authority).** Mutated `CalcRequestBoundary.reconcileParticipantItem` so the party stored
  item wins (`participant.itemId ?: state.itemId`). `CalcHnsItemTest` failed 3 tests, including
  `authoritative ITEM_NONE after a battle mutation is not overridden by the party item`.
- **Control 2 (numeric identity).** Mutated `CalcCapabilityPolicy.collectHnsItemLimitation` to classify a
  live item by its display name (`classifyByName(input.item)`) instead of its numeric ID. `CalcHnsItemTest`
  failed 4 tests, including `numeric item id is the authority when the display name disagrees`.
- **Control 3 (contextual interaction gate, review R1).** Mutated
  `HnsMoveItemInteraction.requiresBlock` from `isItemDependent && !modelled` to
  `isItemDependent && modelled`, i.e. the audit still identified item-dependent moves but stopped adding
  the blocker. Running `CalcHnsItemTest` + `HnsMoveItemInteractionTest` failed 5 tests:
  `the same utility item with Fling is refused as item dependent`,
  `ITEM_NONE with Acrobatics is refused as item dependent`,
  `defender held utility item with Knock Off is refused as item dependent`,
  `manual utility item with an item dependent move is refused`, and
  `every audited move still resolves to its exact H and S pack identity`.
- All mutants were reverted and the full suite returned green.

### 17.8 What this slice does not verify

Live on-ROM item observation, item consumption/Knock-Off transitions in a running battle, and any H&S
damage number remain **NOT YET RUNTIME VERIFIED**. No H&S calculation is produced: `BADGE_BOOST_NOT_MODELLED`
(Gap C4) keeps the result `UNSUPPORTED` with `request == null`.

---

## 18. Gap C4a: remaining-mechanics inventory, badge audit, move gating, arithmetic parity (issue #9)

Pinned source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973` (tag
`Release-v2.0.5`). No ROM bytes are used by any item in this section.

### 18.1 Challenge-settings inventory (SOURCE VERIFIED, UNIT VERIFIED)

All 17 `SaveBlock3.challengeSettings` fields are classified by `HnsChallengeSettingInventory`
(one row per field, asserted by `HnsChallengeSettingInventoryTest`). The 4 Gap A rule fields are
consumed; `tx_Random_Moves` and `tx_Challenges_BaseStatEqualizer` are the only new conditional
blockers (`HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED` and `HNS_BASE_STAT_EQUALIZER_NOT_MODELLED`).
The remaining value-changing fields (`NoEVs`, `Mirror`, `Mirror_Thief`, `TrainerScalingIVs/EVs`,
`MaxPartyIVs`, `LevelCap`, `ExpMultiplier`, `Random_Abilities`, `Legendary_Abilities`) are captured
downstream because the calculator consumes the final observed level / IV / EV / ability / party
values; `tx_Mode_Sturdy` is irrelevant to the supported ability subset. See capability doc §10.1.

### 18.2 Badge boost (SOURCE VERIFIED, still blocked)

`B_BADGE_BOOST = GEN_3`; `GetBadgeBoostModifier() = UQ_4_12(1.1)`; the badge is composed into the
attack/defence modifier in UQ4.12 (`uq4_12_multiply_half_down`) after stat stages and ability/item
multipliers, then applied once (`uq4_12_multiply_by_int_half_down`) `[src/battle_util.c:6894]`,
`:6903]`, `:7189]`, `:7392]`, `:9135]`. Eligibility is `ShouldGetStatBadgeBoost` `[src/battle_util.c:9143]`
(player side; link / e-Reader / recorded-link / Frontier / secret-base trainer excluded). The four
flags resolve to `FLAG_BADGE01_GET`, `FLAG_BADGE06_GET` (H&S), `FLAG_BADGE07_GET` (SpA and SpD share
it) via `FlagGet`/SaveBlock1 `[include/constants/flags.h:1363]`, `:1368]`, `:1369]`.

DualDex has **no ABI-backed badge-state reader**, so a caller cannot supply proven live badge state
and the calculator must not fabricate it. `BADGE_BOOST_NOT_MODELLED` is therefore retained. This is a
deliberate fail-closed decision, not an oversight.

### 18.3 Move mechanics (SOURCE VERIFIED, HOST VERIFIED)

`tools/hns-move-mechanics/generate_hns_move_effects.py` extracts the exact effect/flag data from the
pinned `src/data/moves_info.h` into `Hns205MoveEffects.kt` (928 resolved effects, 357 ordinary, 6
unresolved). The generator is verified byte-for-byte by `./ci.sh source-check` and unit-tested by
`test_generate_hns_move_effects.py` against synthetic fixtures. `HnsMoveMechanicsRegistry` refuses
everything outside the ordinary set with `HNS_MOVE_MECHANICS_NOT_MODELLED`; C3 item-dependent moves
keep `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` and are not double-reported.

### 18.4 Arithmetic parity (SOURCE/HOST VERIFIED, NOT RUNTIME VERIFIED)

`native/tests/test_js_calc.c:check_gap_c4a_arithmetic_parity` contains an independent oracle
transcribed from `CalculateBaseDamage` / `DoMoveDamageCalcVars` / `ApplyModifiersAfterDmgRoll` /
`fpmath.h`. Fixtures:

* `gap_c4a_parity_neutral_base_matches` — Machamp Rock Slide (non-STAB, 1x) vs Snorlax is
  51–60 on both the oracle and the committed bundle.
* `gap_c4a_parity_neutral_special_matches` — Alakazam Thunderbolt (special, non-STAB, 1x) vs
  Snorlax is 43–51 on both, using the independently transcribed SpA/SpD pair (155/130). This
  broadens the positive proof beyond the physical case.
* `gap_c4a_divergence_stab_detected` — Karate Chop (STAB, 2x) is `102,102,102,104,…,120` in H&S
  versus `102,103,…,120` in ADV; the fixture requires the divergence so the gate stays warranted.
* `gap_c4a_divergence_crit_stab_detected` — crit + STAB/type also diverges.

The divergence is ordering/rounding: H&S applies the random roll before STAB/type/burn/screens and
composes modifiers in UQ4.12 half-down; ADV applies burn/screens/weather before `+2` and STAB/type
before the roll. A request exercising any non-identity modifier, **including any non-neutral stat
stage**, adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`. Non-neutral stages are blocked rather than
given a fabricated positive fixture: H&S applies stages before its fixed-point ability/item
composition while ADV applies ability modifiers before stages, and the staged-stat rounding is not
independently proven. The only positive parity cases are neutral-stage physical and special requests.

### 18.5 Live battle state (R1)

`CalcCapabilityPolicy.hnsLiveBattleStateNotModelled` refuses an active H&S battle whose mutable
damage operands are not authoritatively observed with `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`.
`CalcRequestBoundary.bindHnsLiveBattleState` marks battle context from an explicit `activeBattle`
hint or any supplied runtime observation, and binds the current effective types for a slot-matched,
OBSERVED, in-domain battler; the stat-word, dynamic-move-type, and transient-state authority flags
stay false until Gap C4b provides readers.

Evidence:

* A static Normal defender (Snorlax) with an observed live Water type (Soak) cannot clear the
  ordinary-safe path: `CalcHnsLiveBattleStateTest` asserts `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`,
  and that the modifier detector adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` for the live 2×
  Electric matchup rather than seeing the static 1×.
* A third non-empty live type, a slot-mismatched observation, an out-of-domain type, and an
  unobserved active battler each block.
* A policy-level fully-observed shape (types matching the static record plus all class-authority
  flags) clears the gate, proving it is per-class rather than an unconditional live-read refusal.
* A manual hypothetical or stored-party read is not an active battle and is unaffected.

### 18.6 Mutation controls

* **Control 1 (arithmetic rounding).** Changed the oracle's base-damage `+2` to `+3`. The native
  suite failed `gap_c4a_parity_neutral_base_matches` (1963 passed / 1 failed). Reverted.
* **Control 2 (move gate).** Added Return (move ID 216) to `Hns205MoveEffects.ordinaryMoveIds`.
  `CalcHnsMechanicsTest` and `HnsMoveMechanicsRegistryTest` failed 3 tests, including
  `Return and Hidden Power are refused by the move mechanics gate`. Reverted and re-verified
  byte-for-byte against the pinned source.
* **Control 3 (live battle state gate, R1).** Removed the `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`
  limitation emission from `CalcCapabilityPolicy`. `CalcHnsLiveBattleStateTest` failed on the
  unobserved-active-battler and Soak/Water regressions. Reverted.
* **Control 4 (staged-stat handling, R2).** Removed the `hnsHasNonNeutralStages` check from
  `hnsModifierOrderDiverges`. `CalcHnsMechanicsTest` failed
  `non-neutral stat stages are refused by the modifier-order gate`. Reverted.

### 18.7 What this slice did not verify

No official H&S 2.0.5 ROM battle result had been compared with any value in C4a. The ordinary base
arithmetic was **SOURCE/HOST VERIFIED**, not runtime verified. C4a kept H&S production
`CalcSupport.UNSUPPORTED` with `request == null`; `BADGE_BOOST_NOT_MODELLED`,
`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`, and `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` kept it refused.
Gap C4b below addresses the ABI-backed readers, the modifier-order/staged-stat fix, and the QuickJS arithmetic engine.

---

## 19. Gap C4b: runtime battle-stat, stat-stage, badge-boost reading and exact H&S 2.0.5 calculator arithmetic parity (issues #9, #40)

Pinned source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973` (tag `Release-v2.0.5`).

### 19.1 Battle Mons ABI layout probing (raw stats and stat stages)

1. **Layout probing (`tools/hns-layout/generate_hns_battle_pokemon_layout.py`):**
   - Probed `struct BattlePokemon` member offsets in pinned pokeemerald-expansion
     (relative to the start of `struct BattlePokemon`, generated in `native/src/hns_battle_pokemon_layout_gen.h`):
     - `attack`: offset `0x02`, width 2
     - `defense`: offset `0x04`, width 2
     - `speed`: offset `0x06`, width 2
     - `spAttack`: offset `0x08`, width 2
     - `spDefense`: offset `0x0A`, width 2
     - `statStages`: offset `0x18`, width 8 (array of 8 `u8` bytes for hp, atk, def, speed, spAtk, spDef, acc, evasion)
   - Neutral stat stage is 6 (`DEFAULT_STAT_STAGE`), domain is 0..12 corresponding to -6..+6.
   - Pinned layout header `native/src/hns_battle_pokemon_layout_gen.h` regenerated and confirmed with `--verify`.

2. **Native reader & JNI integration:**
   - In `native/src/pokemon_reader.c`, `pokemon_read_battler_runtime_state_gba` extracts raw battle stats (10 bytes)
     and stat stages (8 bytes) from `gBattleMons[battler]`.
   - Bounds checking validates stat stages (0..12); values outside the domain are rejected as out-of-domain
     (`stages_invalid`, producing `BATTLER_RUNTIME_STATE_OBSERVED_INVALID` so the observation fails closed without coercion).
   - `dualdex_jni.c` expands the battler runtime array from 16 to 38 integers, carrying all 5 battle stats,
     8 stat stages, and badge boost flags.
   - Decoded into Kotlin data classes `HnsBattlerRuntimeState`, `CalcRawStats`, and `CalcHnsLiveBattleState`.

### 19.2 SaveBlock1 badge state reading and eligibility

1. **Badge flag addresses and reader:**
   - Derived from the pinned upstream source (`pokehns-expansion` commit `1f42b74`,
     `include/constants/flags.h` and `include/config/battle.h`):
     - `SYSTEM_FLAGS = 0x860` (`SaveBlock1.flags` starts at offset `0x198C` from SaveBlock1 base)
     - `FLAG_BADGE01_GET` (Atk)     = `0x867` -> `flags[0x10C]`, byte `0x1A98`, bit 7
     - `FLAG_BADGE03_GET` (Spe)     = `0x869` -> `flags[0x10D]`, byte `0x1A99`, bit 1
     - `FLAG_BADGE06_GET` (Def)     = `0x86C` -> `flags[0x10D]`, byte `0x1A99`, bit 4
     - `FLAG_BADGE07_GET` (SpA+SpD) = `0x86D` -> `flags[0x10D]`, byte `0x1A99`, bit 5
   - The reader (`pokemon_read_hns_badge_state_gba` in `native/src/pokemon_reader.c`) reads two bytes
     at `sb1_base + 0x1A98` and `sb1_base + 0x1A99` and extracts the authoritative bit per flag.
2. **Battle eligibility rules:**
   - Per upstream `ShouldGetStatBadgeBoost` (`src/battle_util.c:9143`):
     - Only applies to player-side battlers (`IsOnPlayerSide(battler)`). The enemy defender never receives badge boosts.
     - Ineligible in link battles, Frontier, e-Reader, recorded link (`HNS_BATTLE_TYPE_BADGE_EXCLUSIONS`), or secret base battles (`TRAINER_BATTLE_PARAM.opponentA == TRAINER_SECRET_BASE`).
   - Populated into `HnsBattlerRuntimeState.badgeBoostAttack`, `.badgeBoostDefense`, etc. for player battlers only.
   - In active battles (`request.hnsLiveBattleState != null`), if the player's badge state is unobserved, `BADGE_BOOST_NOT_MODELLED` blocks fail-closed. The enemy defender's badge state is never required.
   - In manual / hypothetical requests, badge state does not exist, and missing badge state is **not**
     interpreted as "badges off": badge applicability is boundary-owned live state, so a manual
     request fails closed with `BADGE_BOOST_NOT_MODELLED` (commit `c87d80a`, R7).

### 19.3 Dedicated H&S QuickJS calculation engine (`calculateHnsDamage`)

In `tools/calc-bundler/entry.js`, `calculateHnsDamage` implements the exact pokeemerald-expansion damage pipeline:
- Base damage: $\lfloor \lfloor \lfloor bp \times Atk_{final} \times (\lfloor 2L/5 \rfloor + 2) \rfloor / Def_{final} \rfloor / 50 \rfloor + 2$.
- Stat resolution: uses live `rawStats` if present; otherwise standard formula.
- Stat stages: applies `HNS_STAT_STAGE_RATIOS` (-6..+6) with crit drop-ignore rules.
- Badge boost: applies UQ4.12 `halfDown(4506, stat)` ($\times 1.1$) when badge boost flag is set.
- Pre-roll modifiers: Doubles spread (`halfDown(2048, dmg)` applied only when the request carries an explicit boundary-owned `field.targetCount == 2` from the live `GetMoveTargetCount(ctx)` observation; a count of 1 is NOT reduced, and a missing/unobserved count fails closed with `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`), Weather (`halfDown(6144/2048, dmg)`), Crit (`halfDown(8192, dmg)`).
- 16 damage rolls: for $r = 85..100$, $x = \lfloor dmg \times r / 100 \rfloor$.
- Post-roll modifiers: STAB (`halfDown(6144, x)`), Type effectiveness (`halfDown(Math.round(eff \times 4096), x)`), Burn (`halfDown(2048, x)`), Screens (`halfDown(2048/2732, x)`).
- Minimum floor: $x = 1$ if damage is 0 and effectiveness $> 0$.

### 19.4 Host test suite and parity evidence

1. **Exact Parity with Independent C Oracle (`native/tests/test_js_calc.c`):**
   - Neutral physical & special base damage: 100% exact parity across all 16 rolls.
   - Karate Chop STAB: 100% exact parity (`rolls_equal(engine, oracle) == 1`).
   - Karate Chop Crit STAB: 100% exact parity (`rolls_equal(engine, oracle) == 1`).
   - `check_gap_c4b_arithmetic_coverage`:
     - Stat stages (+2 Atk, -1 Def): exact match across all 16 rolls.
     - Crit drop-ignore: exact match across all 16 rolls.
     - Badge boosts (Atk + Def): exact match across all 16 rolls.
     - Weather boost (Sun on Fire): exact match across all 16 rolls.
     - Screens (Reflect): exact match across all 16 rolls.
     - Explicit raw stats (`rawStats`): exact match across all 16 rolls.
2. **Fail-Closed Policy (no `ESTIMATED` promotion):**
   - No production H&S request is promoted to `CalcRequestOutcome.Ready` with `CalcSupport.ESTIMATED` today.
     Badge applicability is boundary-owned live state (missing state → `BADGE_BOOST_NOT_MODELLED`, R7), and the
     Doubles spread requires an observed runtime `GetMoveTargetCount` (missing count → `HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED`, R2);
     since no reader supplies those live operands yet, the arithmetic remains host-verified and Gap C4b stays PARTIAL / OPEN.
   - Non-ordinary move effects, unsupported abilities/items, out-of-range stat stages, unmodelled weather, active randomizers, and unobserved active-battle state remain strictly fail-closed (`CalcSupport.UNSUPPORTED`).
3. **Continuous Integration:**
   - All 85 native reader/tracker tests pass.
   - QuickJS test suite passes with 0 failures.
   - All data pack, type system, item catalogue, and move effect generators pass `--verify`.
   - All 616 Gradle Kotlin unit tests pass with 0 failures.

---

## Gap C4c — Runtime Validation + Remaining Live Operand Authority

**Status: PARTIAL / OPEN**

### What C4c proved (SOURCE VERIFIED)

1. **Target count computation from authoritative battle state:**
   - `GetMoveTargetCount(ctx)` is computable from `gAbsentBattlerFlags` + `gBattlersCount` +
     the move's static target class + attacker/defender battler indices.
   - All input data is already read by the native reader (`BattleStateRaw`).
   - The computation follows the upstream `battle_util.c:6122` logic exactly.
   - Native function `pokemon_compute_hns_target_count()` pinned with 14 test cases covering:
     - Singles (count always 1)
     - Doubles both opponents present (count = 2)
     - Doubles one opponent fainted (count = 1)
     - TARGET_FOES_AND_ALLY with attacker partner (count = 3)
     - Invalid inputs fail closed (return 0)

2. **Dynamic move type irrelevance for non-Normal EFFECT_HIT moves:**
   - `SetTypeBeforeUsingMove` can only change move type via Ion Deluge or Electrify.
   - Both convert Normal-type moves to Electric.
   - Non-Normal-type EFFECT_HIT moves are provably immune.
   - Normal-type EFFECT_HIT moves retain the fail-closed gate.

3. **Transient state irrelevance for the supported ordinary subset:**
   - The ordinary EFFECT_HIT subset has no state-dependent flags.
   - All relevant transient state is carried by existing request fields.
   - Non-Normal moves: transient state is provably irrelevant.
   - Normal moves: only Ion Deluge/Electrify volatile remains unobserved.

### What C4c proved (HOST VERIFIED)

1. **Target count native computation matches upstream semantics:**
   - 14 test cases in `test_hns_target_count_computation` verify exact parity.
   - Anti-spoof test `test_hns_target_count_anti_spoof` verifies fail-closed behavior.

### What C4c did NOT prove (RUNTIME VERIFIED)

1. No official H&S 2.0.5 battle result has been compared against DualDex output.
2. Runtime golden fixtures are not yet implemented.
3. The production H&S request path has not been validated end-to-end against the running ROM.

### Remaining blockers for C4c completion

1. **Runtime golden validation:** Official-ROM battle outcomes must be compared with DualDex predictions.
2. **Normal-type dynamic move type:** Reading `gFieldStatuses` (Ion Deluge) and the electrified volatile
   would close the Normal-type gap but is not implemented.
3. **Badge boost manual support:** Manual/out-of-battle requests still lack authoritative badge applicability.

### CI status (C4c)

- All 87 native reader/tracker tests pass (including 2 new target count tests).
- QuickJS test suite passes with 0 failures.
- All 618 Gradle Kotlin unit tests pass with 0 failures (including 4 new C4c tests).
- `./ci.sh all` passes.
- `git diff --check` passes.
