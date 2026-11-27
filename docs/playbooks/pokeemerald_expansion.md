# `pokeemerald-expansion` Engine Onboarding Playbook

Covers RHH `pokeemerald-expansion` hacks built on Emerald (`BPEE`), including **Heart & Soul (2.0.6+)**, **Emerald Imperium**, **Emerald Seaglass**, and **Pokemon Lazarus**.

> **Guardrails:** Keep ROMs, saves, save states, and copyrighted text outside Git at all times (`AGENTS.md` §4). Every expansion build relocates symbols and can change struct strides; never copy offsets or hashes across releases.

## 1. Identify the ROM
```bash
python3 tools/rom-evidence/rom_identify.py /private/hack.gba --json
```
Expect `family: "pokeemerald-expansion"` with an `RHHEXP` / `RHH_EXP` marker and `base_config_id: "emerald"`.

## 2. Extract GF ROM Header & Expansion Tables
```bash
python3 tools/rom-evidence/expansion_extract.py --rom /private/hack.gba > /tmp/expansion-tables.json
```
`expansion_extract.py` verifies the `RHHEXP` / `RHH_EXP` marker, parses the GF ROM Header at `0x08000100` (`0x1BC` `gSpeciesInfo`, `0x1CC` `gMovesInfo`, `0x1C8` `gItemsInfo`, and optional `gTypesInfo` / type chart slots), probes bounded struct strides, and validates structural invariants (e.g., species 1 Grass/Poison or non-zero stats, move 33 Tackle power 40).

## 3. Inherit a Native Memory Config (`gamedata/gen3/`)
1. Create `gamedata/gen3/<hack>.json` inheriting `"base": "emerald"` (or `"heart_and_soul_2_0_5"` when starting from an expansion layout) and record evidence for every changed EWRAM/IWRAM address.
2. Regenerate and verify the C header:
   ```bash
   python3 tools/gamedata/generate_gamedata.py --write
   python3 tools/gamedata/generate_gamedata.py --check
   ```

## 4. Observe Dynamic RAM Stability
Run 2–3 varied-boot captures with `rom_observe.py` (outputs outside Git):
```bash
python3 tools/rom-evidence/rom_observe.py \
  --rom /private/hack.gba --core /private/mgba_libretro.so \
  --movie tools/rom-evidence/rowe-starter.movie --boots 3 --boot-jitter 7 \
  --probe sb1_ptr=0x03005d8c:4 --output /tmp/expansion-stability.json
```
- **Expansion gotchas:**
  - `gPlayerParty` / `gEnemyParty` / `gBattleMons` shift per build due to linker placement and expansion features.
  - `SaveBlock1` / `SaveBlock3` are heap/ASLR-shifted (`save_block1_ptr_gba_address` must be verified rather than hard-coded).
  - Expansion Pokémon records pack `hiddenNatureModifier`, `shinyModifier`, and `gigantamaxFactor` into bits that vanilla Gen 3 uses for ability slots, and `gTypesInfo` / `gTypeEffectivenessTable` uses `uq4_12_t` (`0x1000` = 1.0x).

## 5. Scaffold Profile, Fixture & Gate
1. Add a Tier-B scaffold in `app/src/main/assets/profiles/<hack>.json` (`isVerified: false`, `memoryLayoutVerified: false`, `sha256Hashes: []`, `gameId: 0`, hack-specific `headerTitles` only — never bare `"BPEE"`).
2. Add a ROM-free fixture in `native/tests/` and verify with `./ci.sh fixture-test` and `./ci.sh test`.
3. Only promote to Tier A after exact-ROM runtime evidence (and keep `battleStateReadVerified: false` until live battle globals are independently proven).
