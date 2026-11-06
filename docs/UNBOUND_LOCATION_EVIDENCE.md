# Unbound player-location evidence (#60, item D)

Evidence only. No production profile, reader, strategy or data-pack change. Exact candidate ROM
SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` (owner-stated v2.1.1.1;
release label NOT_VERIFIED). Committed record: [`unbound-location.json`](../tools/rom-evidence/evidence/unbound-location.json)
(numbers, addresses and SHA-256 only). Tool: [`unbound_location.py`](../tools/rom-evidence/unbound_location.py),
tests: `test_unbound_location.py`, movie: `unbound-location-grotto-warp.movie`.

## Method

* **Static.** `unbound_location.py` refuses any other ROM, then decodes the exact Thumb instructions
  of the functions named below (public CFRU `BPRE.ld` addresses). It checks each instruction
  and takes every address from the ROM's own PC-relative literals. It does not use symbol names
  alone. Map-name bytes are hashed and length-counted, never decoded into the report.
* **Runtime.** The same tool replays the movie on a private copy of the Thor battery save
  (SHA-256 `a561cab2…5236`) with the pinned core (`fc7394f7…203e`) and the adapter that
  `rowe_observe.verify_adapter()` accepts (binary `d693404b…b361`, source revision `ff51c01`).
  At each observation, with the core paused, it reads twice and requires identical results:
  `gSaveBlock1Ptr` and `gSaveBlock2Ptr`, SaveBlock1 bytes `+0x00..+0x2B`, `gMapHeader` (0x1C bytes),
  and the 0x2C bytes that today's native legacy reader would treat as SaveBlock1. It then
  resolves the observed (group, num) through the ROM map table and compares that header with
  `gMapHeader`. No RAM writes, no cartridge flush, no in-game save. The private report is
  `/home/dq/dualdex-private/unbound/e3-location/final.json` (SHA-256 `7e017a54…b51a`). An earlier
  run (`run3.json`) of the same route gave identical observations and screen hashes.
* **Route.** The movie runs Continue, then walks south out of the starting map through its exit
  warp. It steps south and back, then uses the A-button field script north of the arrival tile,
  which warps back into the starting map. That gives 13 observations and two map transitions,
  with observations at frames 8, 32 and 64 after the exit warp is triggered.

## 1. SaveBlock1 location layout: VERIFIED

| Item | Value | Basis |
|---|---|---|
| `gSaveBlock1Ptr` / `gSaveBlock2Ptr` / `gPokemonStoragePtr` | IWRAM `0x03005008` / `0x0300500C` / `0x03005010` | `SetSaveBlocksPointers` `0x0804C058` literals |
| SaveBlock1 / SaveBlock2 / storage | `0x0202552C` / `0x02024588` / `0x02029314`, **fixed** | same function: `bl Random`, then `movs r1,#0` twice; the Random result is discarded and the offset added to every base is the constant 0 |
| `pos` (Coords16) | SB1 `+0x00` s16 x, `+0x02` s16 y | CFRU `global.h` SaveBlock1; runtime |
| `location` (WarpData) | `+0x04` s8 mapGroup, `+0x05` s8 mapNum, `+0x06` s8 warpId, `+0x08` s16 x, `+0x0A` s16 y | exact-ROM `GetCurrentRegionMapSectionId` `0x08056260` (`ldrsb [sb1,#4]`, `ldrb [sb1,#5]`); runtime |
| `continueGameWarp` / `dynamicWarp` / `lastHealLocation` / `escapeWarp` | `+0x0C` / `+0x14` / `+0x1C` / `+0x24` | CFRU `global.h`; `dynamicWarp` also from exact-ROM `GetSavedWarpRegionMapSectionId` `0x08056238` (`#0x14`, `#0x15`) |

Runtime, 13 observations at frames 1882 to 2803: SB1 was `0x0202552C` and SB2 was `0x02024588` at
every observation, including the warp-fade frames. This agrees with the 26 party-route
observations and the battle-route observations in
[UNBOUND_PARTY_LAYOUT_EVIDENCE.md](UNBOUND_PARTY_LAYOUT_EVIDENCE.md). There is no FireRed-style
128-byte relocation in this ROM.

| Observation | Frame | pos | location (group, num, warp, x, y) | `gMapHeader` mapsec |
|---|---|---|---|---|
| start_map (after Continue) | 1882 | 6,10 | 1, 7, 0, −1, −1 | `0x80` |
| warp_8 (exit warp triggered) | 1986 | 6,16 | 1, 7, 0, −1, −1 | `0x80` |
| warp_32 | 2010 | 46,5 | 3, 6, 5, 46, 5 | `0x93` |
| step_south | 2130 | 46,6 | 3, 6, 5, 46, 5 | `0x93` |
| back_inside (scripted warp) | 2803 | 6,16 | 1, 7, 0, −1, −1 | `0x80` |

`location.mapGroup` and `location.mapNum` track the current map across both transitions. The live
tile is `pos`. `location.x` and `location.y` are the warp destination, and are −1 when the game
enters by warp id or by Continue, so a reader must not use them as the player position. `escapeWarp`
(3, 105) and `dynamicWarp` (3, 6) did not change. Within this window, SB1 `location` and
`gMapHeader` both changed between frames 1986 and 2010. Finer ordering inside the fade was not
sampled.

**NOT_VERIFIED:** a route-boundary (map connection) crossing was not exercised. A movie that
crosses a connection from a known map is still needed. Other ROM literals of `0x0202552C` (at
`0x08000508`, `0x088BC1A4`, `0x088C0F34` and `0x093A1A00`/`31C8`/`3578`) were not individually
decoded. The runtime evidence above covers only the warp, scripted-warp, battle and party routes.

## 2. (group, num) → map header → region-map section: VERIFIED

* `Overworld_GetMapHeaderByGroupAndId` `0x08055238` is patched to `ldr r2,=0x09ED15D5; bx r2`.
  The hook at `0x09ED15D4` computes
  `header = *(u32 *)((groups[group] ^ 0x00B749DE) + 4 * num)` with `groups` at
  **`0x08B70498`**. Group-list pointers are **XOR-obfuscated**; header pointers are not.
  The original code left behind at `0x0805524C` also points at `0x08B70498`, and the vanilla FireRed
  table literal `0x083526A8` does not occur in the ROM. A reader that skips the XOR follows the
  wrong addresses.
* `regionMapSectionId` is the u8 at header **`+0x14`**: `GetCurrentRegionMapSectionId` ends in
  `ldrb r0,[r0,#0x14]`. The rest of the CFRU `MapHeader` matches FireRed (0x1C bytes).
* Structural enumeration: groups continue until a decoded group pointer leaves ROM, and a group's
  maps end at the next list or at the first entry that is not a header. This gives **57 groups and
  645 maps**, with map numbers up to 122 (per-group counts and the row hash are in the JSON). This
  is an evidence bound, not a game-defined count.
* `gMapHeader` is at **`0x02036DFC`** (CFRU `BPRE.ld`; 170 PC-literal loads in the exact ROM).
  At all 13 observations its 0x1C bytes equal the ROM header resolved from SB1 `location`
  through the XOR path (`0x0834F2D8` for (1, 7) and `0x0834FB0C` for (3, 6)), so the two mapsecs
  agree.
* Private cross-checks, with no text committed: the starting map's `0x80` equals the public CFRU
  `MAPSEC_HIDDEN_GROTTO` constant. The on-screen map-name popup after the exit warp equals the ROM
  name-table entry for `0x93`.

## 3. Region-map section ID range and name table: VERIFIED (numeric)

`GetMapName` `0x080C4D78` computes `index = mapsec − 0x58` and indexes a table only when
`index <= 0x6C`:

| Item | Value |
|---|---|
| Valid sections | `0x58`..`0xC4` (109); anything else, including `0xC5` (CFRU `MAPSEC_NONE`), yields no table name |
| Name pointer table | `0x083F1CAC` (FireRed's location, not repointed), stride 4, `0xFF`-terminated strings of 7..16 bytes |
| Alternative | a ROM predicate (`bl`; `cmp r0,#1`) can substitute one fixed name at `0x083EF22E` |
| Hashes | pointer table `c3fc0a35…ee20`; concatenated terminated names `0838c37a…43f1` |
| Map headers | all 645 enumerated headers use mapsecs in `0x58`..`0xC4`; 92 distinct |

Unbound reuses FireRed's Kanto mapsec numbers with its own meanings: CFRU
`region_map_sections.h` redefines them under `#ifdef UNBOUND`. FireRed section names, map tables
and region-map geometry therefore do not apply. **NOT_VERIFIED:** region-map grid geometry
(`sMapSectionTopLeftCorners` `0x083F1E60` and `sMapSectionDimensions` `0x083F2178` in CFRU
`BPRE.ld`) was not inspected, and neither was whether Unbound's town map draws from those tables.
The design meaning of each section (town, route or dungeon) is also not established.

## 4. Existing native reader against Unbound: unsuitable as configured

* `CONFIG_UNBOUND` has no `save_block1_ptr_gba_address`, so `pokemon_read_player_location_gba`
  takes the legacy path. Because `game_id != GAME_FIRERED`, it computes `sb1 = 0x02024284 − 0x238`
  = **`0x0202404C`**, which lies inside `gEnemyParty` (`0x0202402C`..). At all 13 observations
  these bytes were zero, and the production bounds accepted them as a **valid location (0, 0) at
  pos (0, 0)**: a plausible but wrong answer rather than a failure. The FireRed `0x38` branch would
  give `0x0202424C`, which is not SaveBlock1 either.
* The legacy *field offsets* (pos `+0x00`, location `+0x04`, escape `+0x24`) are correct for
  Unbound. The pointer-path offsets used by Heart & Soul (`+0x04`/`+0x08`/`+0x28`) are not.
* Decoding SB1 `0x0202552C` with offsets 0/4/0x24 under the production bounds gave the observed
  (1, 7) and (3, 6) at every observation. However, the bound `0 <= mapGroup <= 35` rejects
  Unbound groups 36..56 (21 groups, 143 maps). `mapNum <= 130` holds (maximum 122).
* `is_indoors = (mapGroup != 0)` and the app's FireRed strategy (group 3 → Kanto towns) have no
  meaning for Unbound. Group 3 holds Unbound maps such as (3, 6) with section `0x93`.

## Production implications (recommendations only)

1. **Native SaveBlock1 base.** Give `CONFIG_UNBOUND` the pointer path: pointer `0x03005008`,
   base `0x0202552C`, a window that accepts only the exact base (offset is constant 0), pos
   `0x00`, location `0x04`, escape `0x24`. Size is the CFRU/FireRed SaveBlock1 extent (bounds
   only; Unbound's extent is not runtime-verified). Make the group/num validity bound per-config
   (Unbound: groups 0..56, with per-group counts from a generated table) instead of the global
   0..35. Return false, never a legacy `ewram` guess (audit fix 5).
2. **Map → section.** Prefer a generator that reads the exact ROM through the XOR path
   (`0x08B70498`, key `0x00B749DE`, header `+0x14`) and emits a numeric
   (group, num) → mapsec table pinned by `rows_sha256` `b1f061ca…bfb4`. As a cross-check, or as
   the cheaper alternative, read `gMapHeader.regionMapSectionId` at `0x02036E10`
   (`0x02036DFC + 0x14`). It matched the ROM table at every observation, but it is only meaningful
   after map load completes.
3. **Names and geometry.** Section names live in the ROM (`0x083F1CAC`, 109 entries). Names must
   not be committed. Displaying them needs an owner decision: user-side ROM extraction, or an
   approved data pack. Do not reuse the FireRed Kanto table or the `RegionMapDatabase` FireRed
   strategy; region-map geometry needs its own evidence.
4. **App strategy.** Add a separate `LocationStrategy` for the exact Unbound hash only. Other
   CFRU hacks (Radical Red, Ghost Grey) need their own evidence, and the XOR key and table address
   are ROM-specific.
5. **Still needed before production:** a connection-crossing capture, a capture in a group ≥ 36
   (to exercise the widened bound), and fixture-replay tests built from these numeric values.
   Encounter tables are out of scope here.

## Reproduce

```bash
export TMPDIR=/private/tmp
./tools/vanilla-runtime-probe/build.sh
python3 tools/rom-evidence/unbound_location.py --rom /private/unbound.gba --static-only \
  --output /private/unbound-location-static.json
python3 tools/rom-evidence/unbound_location.py --rom /private/unbound.gba \
  --core /private/mgba_libretro.so --battery-save /private/own-unbound.sav \
  --movie tools/rom-evidence/unbound-location-grotto-warp.movie \
  --private-dir /private/unbound-location-run --output /private/unbound-location.json
```
