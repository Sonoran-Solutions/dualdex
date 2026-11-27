# CFRU Engine Onboarding Playbook

Covers Complete FireRed Upgrade (`CFRU` / `DPE`) hacks built on 32 MiB FireRed (`BPRE`), including **Radical Red**, **GS Chronicles**, **Pokemon Odyssey**, and **Pokemon Amethyst**.

> **Guardrails:** Keep ROMs, saves, save states, and extracted game text outside Git at all times (`AGENTS.md` §4). Never fabricate SHA-256 pins or enable `isVerified`/`memoryLayoutVerified` before exact-ROM runtime verification.

## 1. Identify the ROM
```bash
python3 tools/rom-evidence/rom_identify.py /private/hack.gba --json
```
Expect `family: "cfru-inplace"` (32 MiB `BPRE`), `base_config_id: "firered_rev0"`, and sibling references `radical_red` / `unbound`.

## 2. Extract Repointed CFRU Tables
```bash
python3 tools/rom-evidence/cfru_extract.py \
  --rom /private/hack.gba \
  --base /private/firered-rev0.gba > /tmp/cfru-tables.json
```
`cfru_extract.py` compares known FireRed rev 0 literal-pool anchor sites (and matching sites in `--base`) to locate repointed `gSpeciesNames`, `gBaseStats`, `gBattleMoves`, `gItems`, `gTypeEffectiveness`, and `gMonIconTable` within `[0x08000000, 0x0A000000)`, outputting only addresses, strides, counts, `sanity_ok` flags, and table SHA-256 digests.

## 3. Inherit a Native Memory Config (`gamedata/gen3/`)
1. Create `gamedata/gen3/<hack>.json` with `"base": "firered_rev0"` (or `"radical_red"` / `"unbound"` where layout matches) and override only changed fields.
2. Start in unverified storage mode (`PKMN_STORAGE_CFRU_UNVERIFIED`) if SaveBlock/XOR or party substructure layout is not yet proven.
3. Regenerate and verify the C header:
   ```bash
   python3 tools/gamedata/generate_gamedata.py --write
   python3 tools/gamedata/generate_gamedata.py --check
   ```

## 4. Observe Dynamic RAM Stability
Run 2–3 jittered headless boots with `rom_observe.py` from outside any Git checkout:
```bash
python3 tools/rom-evidence/rom_observe.py \
  --rom /private/hack.gba --core /private/mgba_libretro.so \
  --movie tools/rom-evidence/rowe-starter.movie --boots 3 --boot-jitter 7 \
  --probe player_party=0x02024284:100 --output /tmp/cfru-stability.json
```
- **CFRU gotchas:** CFRU hacks use internal species IDs (non-contiguous with National Dex; reserved/hole IDs exist), frequently move `SaveBlock1`/`SaveBlock2` or patch out `Random()`/`personality` XOR, and may indirect `gBaseStats`/`gItems` through `0x08000100..0x08000200` header pointer cells.

## 5. Scaffold Profile, Fixture & Gate
1. Add a Tier-B scaffold in `app/src/main/assets/profiles/<hack>.json` (`isVerified: false`, `memoryLayoutVerified: false`, `sha256Hashes: []`, `gameId: 0`, hack-specific `headerTitles` only — never bare `"BPRE"`).
2. Add a ROM-free fixture in `native/tests/` and run `./ci.sh fixture-test` and `./ci.sh test`.
3. Only promote to Tier A (`VERIFIED`) after human review of pinned SHA-256, static spans, and runtime capture evidence.
