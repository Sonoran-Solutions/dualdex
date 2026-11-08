# Unbound battle globals and compiled damage rules

Static portion of calculator plan Phase 0.2e / 0.3c. The inspector is
[`unbound_battle_globals.py`](../tools/rom-evidence/unbound_battle_globals.py);
its generated numeric record is
[`unbound-battle-globals.json`](../tools/rom-evidence/evidence/unbound-battle-globals.json).
Only these two files, the ROM-free test, and this document are added. No production
reader, oracle, profile, source checkout, ROM, save, or runtime state is changed.

**VERIFIED** below means measured statically in the 33,554,432-byte ROM with SHA-256
`7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.
**UNVERIFIED** means the stated question is open. Runtime values, allocation
lifetimes, battle readiness, and full damage parity remain **UNVERIFIED**.
CFRU commit `b637a27898b14e25dd24d0f69a3e302f0069deb8` is
**SOURCE_APPROXIMATE**; source declarations alone never establish a ROM address,
layout, flag ID, or compiled switch.

## Method and limits

The initial search used the verified `gBattleMons` (`0x02023BE4`, stride `0x58`),
`gBattlersCount` (`0x02023BCC`), and `gBattleTypeFlags` (`0x02022B4C`) anchors.
The crit routine contains all three; the damage command also indexes battlers by
`0x58`. Following the script dispatcher establishes command identities: the words
at `0x08014C1C`, `0x08015A28`, `0x08015C6C`, `0x08015C98`, and `0x0801D054`
all point to table `0x0899FFFC`. Entries 4, 5, 6, and 7 are odd Thumb pointers to
the corresponding routines below. This table/control-flow correspondence is
stronger than merely finding a RAM literal somewhere in the cartridge.

Capstone 5.0.7 in Thumb mode was used for manual inspection. Calls, conditions,
literal pools, operand widths, index arithmetic, and arithmetic branch destinations
were compared with the approximate source's control flow. The delivered inspector
needs only Python and existing repository helpers: `exact_rom`, `span`,
`thumb_literal`, `literal_references`, `inspect_save_blocks`, and
`require_private_directory`. It rechecks the exact identity before interpretation,
29 inspected-span digests, 54 instruction probes (each pinned as a PC, a halfword
count and the SHA-256 of those bytes; no instruction words are stored), and 28
call/branch edges. The JSON contains addresses, constants, counts, IDs and digests;
no game text, ROM bytes, private source, timestamp, or host path.

This is an exact-candidate inspection record, not a general symbol-recovery tool.
Names are manual semantic attributions checked against pinned instructions.
`inspected_end_exclusive` bounds a hashed span, including pools and out-of-line
blocks; it is not a claim that every byte is code or that every branch has been
interpreted. Whole-ROM literal counts are corroboration, not caller proofs.
Array strides below come from the cited indexing instructions. The 2-side/4-battler
index bounds do not prove allocation extents. RAM pointees are not dereferenced.

## Damage routines

| Routine | Entry | Identification / distinguishing path | Status |
|---|---|---|---|
| `atk04_critcalc` | `0x089CE360` | Command 4; battler loop, ability/status checks, crit-byte writes | VERIFIED |
| `atk05_damagecalc` | `0x089D3310` | Command 5; zeroes a `0x5C`-byte calculation record, calls base damage, applies crit /10 | VERIFIED |
| `CalculateBaseDamage` | `0x089D1940` | Calls at `0x089D3406` / `0x089D3446`; stats, power, weather, screens, status cuts | VERIFIED |
| `GetBasePower` | `0x089D06B4` | Call at `0x089D1C74`; move-dependent power, disable/protect accesses | VERIFIED |
| `AdjustBasePower` | `0x089CD4E4` | Call at `0x089D1C7C`; ability/item, gem, charge, terrain, sport paths | VERIFIED |
| `atk06_typecalc` | `0x089CE8E4` | Command 6; dynamic type, same-type checks, type modulation | VERIFIED |
| `ModulateDmgByType` | `0x089CDC9E` | Inverse check at `0x089CDCB8`, multipliers `5`/`20`, immunity/status/weather paths | VERIFIED |
| `ApplyRandomDmgMultiplier` | `0x089CD33C` | Command 7 calls it at `0x089CEF5A`; mask 15, subtract from 100, divide by 100, minimum 1 | VERIFIED |
| `ScreensWeakenDamage` | `0x089D18D4` | Call at `0x089D27BE`; crit bypass, doubles count, /2 or 2/3 | VERIFIED |

## Globals

`ldr literal` identifies the instruction loading the RAM address; the last column
also cites a consumer proving width or indexing. Pointer widths describe the RAM
pointer cell, not a fixed pointee address. All twelve locations are **VERIFIED**.

| Global | RAM address | Width / stride proved | Referencing routine; literal-load PC; consumer |
|---|---|---|---|
| `gBattleWeather` | `0x02023F1C` | u16 | Base damage; `0x089D1DC4`; `ldrh` at `0x089D1DC6` |
| `gTerrainType` | `0x0203E028` | u8, standalone | Adjust power; `0x089CD5C4`; `ldrb` at `0x089CD5C6` |
| `gSideStatuses` | `0x02023DDE` | u16, stride 2 | Defender population `0x089CF1BC`; load `0x089CF2BA`; index <<1 and `ldrh` at `0x089CF2BE` |
| `gSideTimers` | `0x02023DE4` | byte field +`0xA`, record stride 12 | Attacker population `0x089CEFEC`; load `0x089CF0C2`; `movs #12` at `0x089CF0BC`, byte read `0x089CF0C6` |
| `gStatuses3` | `0x02023DFC` | u32, stride 4 | Type modulation; `0x089CDD28`; index <<2 and word read `0x089CDD2C` |
| `gDisableStructs` | `0x02023E0C` | byte field +`0x11`, record stride `0x1C` | Base power; `0x089D08B0`; multiply by 28 at `0x089D08AA..AE`, byte read `0x089D08B6` |
| `gProtectStructs` | `0x02023E8C` | byte bit field, record stride `0x10` | Base damage; `0x089D23E2`; index <<4, byte read `0x089D23E6` |
| `gBattleStruct` | `0x02023FE8` | 32-bit pointer; dynamic type byte +`0x13` | Damage command; `0x089D332E`; pointer read `0x089D3330`, byte store `0x089D333E` |
| `gBattleResources` | `0x02023FF4` | 32-bit pointer | Base damage; `0x089D1CC2`; pointer read `0x089D1CC4` |
| `gNewBS` | `0x0203E038` | 32-bit pointer | Damage command; `0x089D3340`; pointer read `0x089D3342` |
| `gBattleCircusFlags` | `0x0203E044` | u32 | Inverse helper `0x089C1F50`; load `0x089C1F6A`, word read `0x089C1F6C` |
| `gCritMultiplier` | `0x02023D71` | u8, units of tenths | Crit command; `0x089CE404`; byte store `0x089CE406` |

The Flash Fire path at `0x089D1CC2..D0` dereferences `gBattleResources`, reads its
pointer member at **+4**, indexes that array by **4 × battler**, loads a **u32**,
and tests **bit 0**. These offsets and the mask are **VERIFIED**; the allocated
flags-array address at runtime is **UNVERIFIED**.

The selected `gNewBS` offsets are **VERIFIED**: damage words at `+0x180 + 4*bank`,
crit bytes at `+0x1A0 + bank`, signed Dynamax counter bytes at `+0x1EA + bank`,
and consumed-gem flag at byte `+0x106`, mask `2`. Crit copying is visible at
`0x089CE430..442` and damage copying at `0x089D3382..39E`.
Terrain is read directly from `0x0203E028` on the damage path; it is not obtained
by adding an offset to `gNewBS` there. Other `gNewBS` fields remain **UNVERIFIED**.

## Build switches and arithmetic

The labels describe binary behavior equivalent to the source branches; the ROM
cannot recover the original preprocessor spelling. All multipliers below use
integer arithmetic at the cited stage, not a claim about a single final multiplier.

| Requested switch | Compiled behavior and decisive instructions | Status |
|---|---|---|
| `OLD_CRIT_DAMAGE` | Disabled: `0x089CE5FA` selects **15**, neutral path selects **10** at `0x089CE402`; damage divides by **10** at `0x089D344E` | VERIFIED |
| `OLD_TERRAIN_BOOST` | Disabled: **13/10**. Electric path uses type 13 as multiplier at `0x089CD9D8..E2`; grassy uses `12+1` at `0x089CDA12..1E`; psychic uses `14-1` at `0x089CDA5C..62` | VERIFIED |
| `OLD_ATE_BOOST` | Enabled: conversion helper call `0x089CD692`; success branch `0x089CD834` reaches `0x089CD620` (`movs #13`), then multiply/divide by 10 at `0x089CD6FC..702`, `0x089CD76C` | VERIFIED |
| `OLD_GEM_BOOST` | Enabled: consumed-gem branch selects **15/10** at `0x089CD592..59A`; held-gem branch reaches `movs #15` at `0x089CD9BA` and common /10 path `0x089CD924..92A` | VERIFIED |
| `BADGE_BOOSTS` | No complete semantic proof for enabled/disabled; no decision inferred from missing source literals | UNVERIFIED |
| `PORTAL_POWER` | Enabled: ability comparison **`0xD0`** at `0x089D1FEE`, contact check, then both offenses ×**75/100** at `0x089D200C..2020` | VERIFIED |
| `NO_MOLD_BREAKERS` | This is a function-like macro in the approximate source, not an `#ifdef`. Exact macro equivalence remains open. ROM helper `0x089A9520` does test attacker ability **`0x98`** and per-move flag bit 7 at 7-byte record +3 before a defender-ability table test | UNVERIFIED (macro); VERIFIED (helper instructions) |
| `FROSTBITE` | Enabled: nonphysical path `0x089D27D2` requires split **1**, tests status bit **`0x20`** via shift 26, branches at `0x089D27DC` to halving instruction `0x089D2568` | VERIFIED |
| `OLD_MOVE_SPLIT` | Disabled: live split routine `0x089C0584` ordinary fallback indexes 12-byte move records and reads **+`0xA`** at `0x089C061A`; it does not classify ordinary moves by type | VERIFIED |

| Constant | Exact compiled value / witness | Status |
|---|---|---|
| Critical | neutral 10/10; critical 15/10, applied after base damage | VERIFIED |
| Burn | physical split 0, status mask `0x10`, ability exception `0x3E`, move exception `0x107`; /2 at `0x089D2568` | VERIFIED |
| Frostbite | special split 1, status mask `0x20`; same /2 instruction | VERIFIED |
| STAB | ordinary **15/10** at `0x089CEAB6..ABE`; ability `0x58` selects **×2** at `0x089CE9A8..B2`; checks three type bytes | VERIFIED |
| Screens | side masks **1** / **2** at `0x089D27AE..D0`; singles **/2** at `0x089D192C`; doubles **×2/3** at `0x089D1908..90C`; crit >10 bypasses | VERIFIED |
| Rain / sun | weather masks **`0x1007`** / **`0x0860`**; water/fire paths select **15/10** or **/2** at `0x089D2410..42A`, `0x089D2638..2668` | VERIFIED |
| Sand group | mask **`0x4018`** loaded at `0x089D1E32`; complete weather-bit nomenclature is not established here | VERIFIED (mask) |
| Random | **100 − (return value & 15)**, multiply then /100, minimum 1 when incoming damage is nonzero (`0x089CD33C..36A`) | VERIFIED |

In particular, type-conversion boosts and gems are both the older boost values in
this ROM, while critical damage and terrain use the newer values. No generation-wide
default safely substitutes for these independent measurements.

## Reachable rule reads and storage

| Rule | Damage-path caller → helper | Reads / storage | Status |
|---|---|---|---|
| Inverse | `0x089CDCB8` → `0x089C1F50` | Flag **`0x23D`**, `*gSaveBlock1Ptr + 0xF27`, mask `0x20`; alternatively battle-type bit `0x04000000` plus Circus mask `0x20000` | VERIFIED |
| Raid | Final-damage adjustment `0x089CE162` → `0x089DA900` | Flag **`0x2FE`**, `*gSaveBlock1Ptr + 0xF3F`, mask `0x40`; battle-type bit 8 excludes trainer cases | VERIFIED |
| Scalemons | `0x089D1EBE` / `0x089D208E` → `0x089E6EEC` | `(flag 0x9F0 && var 0x50C4 == 11) || flag 0x16E2`; flags at **`0x0203B192`** mask 1 and **`0x0203B330`** mask 4; var at **`0x0203B4FC`**, u16 | VERIFIED |
| Active Dynamax | `0x089D25D6` → `0x089D9EDC` | Signed byte `(*gNewBS)[0x1EA + bank]`, nonzero test; no SaveBlock enable flag proved by this path | VERIFIED (counter); UNVERIFIED (enable flag) |

Flag reads call `0x0806E6D0`, whose resolver `0x0806E5C0` jumps through hook
`0x089A2F6C` to extended resolver `0x08A0722C`. IDs `0x900..0x18FF` map to
`0x0203B174 + ((id - 0x900) >> 3)`; lower nonzero IDs use the original
SaveBlock1 flag array at **+`0xEE0`**. The bit mask is `1 << (id & 7)`.
Var reads use `0x0806E568` → resolver `0x0806E454` → hook `0x089A2F44` →
`0x08A0727C`; IDs `0x5000..0x51FF` map to **`0x02031374 + 2*id`**.
The apparently low base is intentional: the compiled instruction doubles the full ID.
These selected resolver domains and reads are **VERIFIED**; uninspected domains
must not be generalized from them.

`gSaveBlock1Ptr` is the 32-bit cell at **`0x03005008`**. The existing location
inspector rechecks the initialization routine assigning **`0x0202552C`**, with
random offset forced to zero. At that assigned base, the two ordinary flag bytes
would be `0x02026453` and `0x0202646B`; a live reader must still dereference the
pointer. Current flag values, save persistence, complete Dynamax enable policy,
and additional difficulty/custom rule reads remain **UNVERIFIED**. The flag IDs
above differ from several approximate-source defaults; no source flag IDs are
silently substituted.

## Host-oracle comparison

Compared with `tools/unbound-damage-oracle/corpus.json` `provenance.constants`;
the JSON records that complete file's SHA-256. Agreement here is constant agreement,
not execution parity or validation of the oracle's modeled behavior.

| Oracle key | Oracle | Exact-ROM comparison | Result |
|---|---|---|---|
| `critical_multiplier` | 15 | 15 tenths | VERIFIED agreement |
| `burn_status` | 16 | bit `0x10` | VERIFIED agreement |
| `fairy_type` | 23 | type comparison `0x17` at `0x089D243E` | VERIFIED agreement |
| `reflect` | 1 | side-status mask 1 | VERIFIED agreement |
| `light_screen` | 2 | side-status mask 2 | VERIFIED agreement |
| `rain_weather` | 1 | nonzero subset of compiled rain mask `0x1007` | VERIFIED agreement for selected bit |
| `sun_weather` | 32 | nonzero subset of compiled sun mask `0x0860` | VERIFIED agreement for selected bit |

No disagreement in the seven recorded values. The corpus does not declare terrain,
conversion, gem, frostbite, Portal, badge, STAB, screen fractions, or weather
fractions in `provenance.constants`; those comparisons remain **UNVERIFIED**.

## Regeneration and validation

Keep the private ROM outside every Git checkout. Set `TMPDIR` to an external
scratch directory. The inspector refuses size/SHA mismatches and refuses outputs
that alias the input ROM, oracle corpus, or Python tools (including hardlinks).
It invalidates a stale output before inspecting inputs. The commands accept local
paths only and do not run an emulator:

```sh
python3 tools/rom-evidence/unbound_battle_globals.py --rom "$ROM" \
  --out tools/rom-evidence/evidence/unbound-battle-globals.json
python3 tools/rom-evidence/unbound_battle_globals.py --rom "$ROM" \
  --out "$TMPDIR/unbound-battle-globals-repeat.json"
cmp tools/rom-evidence/evidence/unbound-battle-globals.json \
  "$TMPDIR/unbound-battle-globals-repeat.json"
python3 -m unittest discover -s tools/rom-evidence -p 'test_unbound_battle_globals.py' -v
python3 -m unittest discover -s tools/rom-evidence -p 'test_unbound_*.py'
```

Validation is limited to the requested ROM-free suites and two static inspections;
each command has a 120-second wall deadline and five-second kill grace. No CI,
Gradle, build, emulator, or host damage-oracle execution is part of this task.

Recorded results on base revision `7e6fff1` plus the new inspector and tests:
focused command **Ran 14 tests in 0.541s — OK**; combined command **Ran 97 tests
in 4.641s — OK**. Expected rejection fixtures print diagnostic failure messages;
the combined process nevertheless finished with exit 0 and unittest `OK`.
The two final ROM inspections finished successfully and `cmp` returned 0.
Both outputs contain 35,860 bytes, SHA-256
`9bc09682b2c01260ae0ef476f69b6bdab511f0a9dcf5f92cfb431cba5a93ccd9`
(regenerated after the review replaced raw probe halfwords with per-probe digests).
Only this inert validation note was added after those checks.
