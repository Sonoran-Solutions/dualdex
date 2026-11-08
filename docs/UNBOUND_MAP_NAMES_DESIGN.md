> Status (2026-11-08): owner approved Option C on 2026-11-08. Slice step 1 (CONFIG_UNBOUND SaveBlock1 pointer and group bound) is already on main. Numeric-table generation is VERIFIED against the exact ROM; Kotlin runtime tests remain NOT_RUN pending coordinator validation. The Thor device check of ROM-region reads is OUTSTANDING until the Thor is back. Historical line references below are to the pre-change `1c16ced` sources.

# Design Study: Showing Unbound Map Names Without Committing Game Text

## Context & Baseline Requirements
In Pokémon Unbound (exact candidate SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`), `SaveBlock1` is fixed at `0x0202552C` with pointer at `0x03005008` ([`docs/UNBOUND_LOCATION_EVIDENCE.md:30-45`](docs/UNBOUND_LOCATION_EVIDENCE.md#L30-L45)). Currently `CONFIG_UNBOUND` in [`native/src/pokemon_reader.c:425-437`](native/src/pokemon_reader.c#L425-L437) omits `save_block1_ptr_gba_address`, causing [`pokemon_read_player_location_gba`](native/src/pokemon_reader.c#L2177-L2238) to fall back to an invalid offset inside `gEnemyParty`. Furthermore, [`decode_location_fields`](native/src/pokemon_reader.c#L2159) rejects `map_group > 35` (Unbound has 57 groups, 645 maps). Repository rules strictly forbid committing proprietary ROM text.

---

## Option Comparison

### Option A: Pure Runtime ROM Dereferencing
* **Mechanism**: On every location update, chase the pointer chain directly in the emulator core's mapped ROM (`0x08000000`):
  1. Read group pointer from table `0x08B70498 + 4 * mapGroup` and XOR with `0x00B749DE` ([`docs/UNBOUND_LOCATION_EVIDENCE.md:68-74`](docs/UNBOUND_LOCATION_EVIDENCE.md#L68-L74)).
  2. Read map header pointer at `group_ptr + 4 * mapNum`.
  3. Read `regionMapSectionId` at byte `header_ptr + 0x14` (`0x58..0xC4`).
  4. Read string pointer from name table `0x083F1CAC + 4 * (sectionId - 0x58)` ([`docs/UNBOUND_LOCATION_EVIDENCE.md:97`](docs/UNBOUND_LOCATION_EVIDENCE.md#L97)).
  5. Read raw string bytes from ROM and decode via [`pokemon_decode_string`](native/src/pokemon_text.c#L4-L78).
* **Files & APIs Touched**:
  - `native/src/pokemon_reader.c:425-437`: Configure `save_block1_ptr_gba_address = 0x03005008`, `save_block1_base_gba_address = 0x0202552C`, `save_block1_aslr_range = 4`, `save_block1_size = 0x3A00`, offsets `0x00`/`0x04`/`0x24`.
  - `native/src/pokemon_reader.c:2159`: Relax group validation bound from `35` to `56`.
  - `native/src/libretro_host.c:132-140` ([`libretro_host_read_gba_address`](native/src/libretro_host.c#L132-L140)) & `native/src/gba_memory_map.c:89` ([`gba_memory_map_read`](native/src/gba_memory_map.c#L89)).
  - `app/src/main/cpp/dualdex_jni.c:1261-1271` (`nativeReadGbaMemory`) and Kotlin caller [`LibretroCoreCoordinator.readGbaMemory`](app/src/main/java/com/dualdex/emulator/LibretroCoreCoordinator.kt#L472-L479) via [`LibretroHost.nativeReadGbaMemory`](app/src/main/java/com/dualdex/emulator/LibretroHost.kt#L70).
  - JNI addition in `dualdex_jni.c` to expose `pokemon_decode_string` ([`native/include/pokemon_text.h:20`](native/include/pokemon_text.h#L20)), or a new Kotlin GBA decoder.
  - `app/src/main/java/com/dualdex/pokemon/LocationResolver.kt:15-53,110-122`: Add `LocationStrategy.UNBOUND` and live ROM resolver.
* **Effort**: Medium (requires JNI expansion or Kotlin decoder, plus a caching layer to avoid 4–5 sequential JNI memory reads per poll).
* **Risks**:
  - Brittle multi-hop pointer chasing across JNI during live emulation; intermediate states during map fades can yield invalid pointers.
  - Performance penalty: uncached polling at 60 Hz causes repeated JNI transitions and mutex acquisition in [`libretro_host.c:135`](native/src/libretro_host.c#L135).
  - ROM mapping dependency: Relies on mGBA publishing ROM descriptors in `RETRO_ENVIRONMENT_SET_MEMORY_MAPS` ([`native/src/libretro_host.c:145-150`](native/src/libretro_host.c#L145-L150)) on all target Android platforms (UNVERIFIED across all Android devices).

### Option B: Numeric Identifiers Only ("Map 3.6" / "Section 0x93")
* **Mechanism**: Fix `CONFIG_UNBOUND` SaveBlock1 reading; format location using raw numeric tuples without string lookup.
* **Files & APIs Touched**:
  - `native/src/pokemon_reader.c:425-437` (`CONFIG_UNBOUND` fields) & line 2159 (group bound).
  - `app/src/main/java/com/dualdex/pokemon/LocationResolver.kt:15-53,110`: Add `LocationStrategy.UNBOUND` producing `RegionMapSection(name = "Map ${loc.mapGroup}.${loc.mapNum}", presentable = false)`.
* **Effort**: Very Low (< 30 lines diff).
* **Risks**: Poor UX. Fails user expectations by showing cryptic numbers rather than recognizable game locations.

### Option C: Pinned Numeric Table Generator + Cached Runtime ROM Name Extraction
* **Mechanism**: Follow existing tooling patterns ([`tools/hns-map-data/generate_hns_map_data.py`](tools/hns-map-data/generate_hns_map_data.py) and [`tools/unbound-data-pack/generate_unbound_species.py`](tools/unbound-data-pack/generate_unbound_species.py)):
  1. Offline tool `generate_unbound_map_data.py` decodes the exact ROM statically ([`tools/rom-evidence/unbound_location.py:156-183`](tools/rom-evidence/unbound_location.py#L156-L183)) and generates `UnboundMapTable.kt`.
  2. `UnboundMapTable.kt` contains ONLY numeric arrays: `(group, num) -> sectionId` (645 maps, 57 groups, section IDs `0x58..0xC4`), pinned to `rows_sha256 = b1f061cac23143d81ec030b89dc04776219a1e2faac3f5ab3ba1bc8e86c4bfb4` ([`tools/rom-evidence/evidence/unbound-location.json:913`](tools/rom-evidence/evidence/unbound-location.json#L913)). Zero ROM text committed.
  3. At runtime, when an Unbound session attaches, read the 109 section name pointers from ROM `0x083F1CAC` (stride 4) via [`LibretroCoreCoordinator.readGbaMemory`](app/src/main/java/com/dualdex/emulator/LibretroCoreCoordinator.kt#L472) and decode them once into an in-memory `Array<String>` cache (~1.5 KB total).
  4. `LocationResolver` does an O(1) in-memory lookup: `name = nameCache[UnboundMapTable.getSection(group, num) - 0x58]`.
* **Files & APIs Touched**:
  - Tooling: `tools/unbound-data-pack/generate_unbound_map_data.py` & synthetic test `test_generate_unbound_map_data.py`.
  - Pinned table: `app/src/main/java/com/dualdex/pokemon/unbound/UnboundMapTable.kt` (new, numbers only).
  - `native/src/pokemon_reader.c:425-437` (`CONFIG_UNBOUND`) & line 2159 (group bound).
  - `app/src/main/java/com/dualdex/pokemon/LocationResolver.kt:15,42,110` (`LocationStrategy.UNBOUND`).
  - `app/src/main/java/com/dualdex/emulator/LibretroCoreCoordinator.kt:472` (`readGbaMemory`) for session-start cache population.
* **Effort**: Medium. Generator logic already exists in `unbound_location.py`; runtime cache is < 50 lines.
* **Risks**:
  - If ROM read fails on session attach, names degrade to numeric section IDs ("Section 0x93").
  - ROM-hash gated: Only works for the exact verified ROM candidate (standard across DualDex Unbound support).

---

## Recommendation & Minimal First Slice

* **Recommendation**: **Option C**. It adheres strictly to repository policy (zero committed text), guarantees O(1) in-memory resolution without frame-time JNI polling overhead, and replaces fragile runtime pointer chases with statically verified topology pinned by `rows_sha256`.
* **Minimal First Slice**:
  1. Fix `CONFIG_UNBOUND` in `native/src/pokemon_reader.c:425` and group bound check at line 2159.
  2. Implement `tools/unbound-data-pack/generate_unbound_map_data.py` and commit generated `UnboundMapTable.kt` (numeric mapping only).
  3. Add `LocationStrategy.UNBOUND` in `LocationResolver.kt:15` with numeric fallback ("Section 0x${sectionId}").
  4. Add lazy/one-time ROM extraction for the 109 section names via `readGbaMemory` on session load.

---

## Required Tests
1. **Native C Fixtures** (`native/tests/test_pokemon_reader.c`):
   - Fixture test with synthetic IWRAM/EWRAM: verify `pokemon_read_player_location_gba` resolves Unbound SaveBlock1 pointer `0x03005008` to base `0x0202552C` and extracts coords/location accurately.
   - Bounds test: verify groups `0..56` pass and group `57` fails closed.
2. **Offline Generator Verification** (`tools/unbound-data-pack/test_generate_unbound_map_data.py`):
   - Standalone `--verify` test without ROM: assert `rows_sha256` matches `b1f061ca...`, map count is 645, all section IDs in `0x58..0xC4`, and generated file matches byte-for-byte.
3. **Resolver & Decoupling Tests** (`app/src/test/java/com/dualdex/pokemon/unbound/`):
   - Unit test `LocationResolver`: verify `forProfile(unboundProfile)` yields `LocationStrategy.UNBOUND`.
   - Table mapping test: verify (1, 7) -> `0x80` and (3, 6) -> `0x93`.
   - Live memory read test: verify single-byte and block reads from ROM address `0x083F1CAC` decode correctly into UTF-8.
   - Degradation test: verify null ROM read cleanly degrades to numeric section display without throwing.

---

## Verification Status
- **VERIFIED**: SaveBlock1 addresses, XOR key `0x00B749DE`, table `0x08B70498`, name table `0x083F1CAC`, `rows_sha256` (`docs/UNBOUND_LOCATION_EVIDENCE.md:30-100`).
- **VERIFIED**: `readGbaMemory` (`LibretroCoreCoordinator.kt:472`) and `libretro_host_read_gba_address` (`libretro_host.c:132`).
- **UNVERIFIED**: ROM region availability at `0x08000000` via mGBA memory descriptors across all Android device architectures.
- **UNVERIFIED**: Town map geometry (`sMapSectionTopLeftCorners` `0x083F1E60`) and connection-crossing SaveBlock1 updates.
