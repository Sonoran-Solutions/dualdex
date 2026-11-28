# Vanilla & In-Place Hack Onboarding Playbook

Covers retail Gen 3 games (**LeafGreen**, **Ruby**, **Sapphire**) and vanilla/in-place ROM hacks (**Emerald Legacy**, **Emerald Kaizo**, **Ultra Violet**, **Universal Pokémon Randomizer ZX**).

> **Guardrails:** Keep ROMs, saves, and copyrighted game data outside Git (`AGENTS.md` §4). A 16 MiB retail header does NOT prove an unmodified ROM — in-place hacks and randomizers keep the retail size and game code, so exact SHA-256 pinning is mandatory for Tier A trust.

## 1. Identify the ROM
```bash
python3 tools/rom-evidence/rom_identify.py /private/hack.gba --json
```
- `vanilla-retail`: 16 MiB with retail title/code (`firered_rev0`, `leafgreen`, `emerald`, `ruby`, `sapphire`). Compare SHA-256 before assuming vanilla data.
- `firered-inplace` / `emerald-inplace`: non-retail size or modified header title without `RHHEXP`/`RHH_EXP` or 32 MiB CFRU layout.

## 2. Static Data Strategy (In-Place & UPR ZX)
- **Fixed vanilla/in-place hacks** (e.g., Emerald Legacy, Emerald Kaizo, Ultra Violet): EWRAM party offsets typically match the base (`firered_rev0` or `emerald`), while species stats, types, learnsets, or moves may be edited in-place.
- **UPR ZX randomizers:** Randomizers modify `gBaseStats`, `gBattleMoves`, and encounters in-place while leaving RAM layouts unchanged. Use `RuntimeRomTableReader` (`app/src/main/java/com/dualdex/pokemon/RuntimeRomTableReader.kt`) with a verified `RuntimeRomTableSpec` (`allowRandomizerTypeStatChanges = true`) so randomized stats, types, and moves are read fail-closed from the active ROM bytes instead of guessed from static tables.
- If a FireRed in-place hack repoints tables, run `tools/rom-evidence/cfru_extract.py` to check whether standard literal-pool anchors were redirected.

## 3. Native Memory Config (`gamedata/gen3/`)
1. Verify whether `gamedata/gen3/<hack>.json` can inherit `"base": "emerald"`, `"firered_rev0"`, `"leafgreen"`, `"ruby"`, or `"sapphire"`.
2. Regenerate and verify the C header:
   ```bash
   python3 tools/gamedata/generate_gamedata.py --write
   python3 tools/gamedata/generate_gamedata.py --check
   ```

## 4. Observe Dynamic RAM Stability
```bash
python3 tools/rom-evidence/rom_observe.py \
  --rom /private/hack.gba --core /private/mgba_libretro.so \
  --movie tools/rom-evidence/rowe-starter.movie --boots 3 --boot-jitter 7 \
  --probe player_party=0x020244ec:100 --output /tmp/inplace-stability.json
```
Confirm `player_party`, `player_party_count`, `enemy_party`, and `enemy_party_count` remain `stable` across boots and movie labels.

## 5. Scaffold Profile, Fixture & Gate
1. Add a Tier-B scaffold in `app/src/main/assets/profiles/<hack>.json` (`isVerified: false`, `memoryLayoutVerified: false`, `sha256Hashes: []`, `gameId: 0`, hack-specific `headerTitles` only — never bare `"BPRE"`/`"BPEE"`).
2. Add or extend a ROM-free fixture in `native/tests/` and run `./ci.sh fixture-test` and `./ci.sh test`.
3. Keep `battleStateReadVerified: false` unless battle globals (`gBattleMons`, `gBattlersCount`, `gBattleTypeFlags`, `gBattleOutcome`) are explicitly verified on the exact ROM.
