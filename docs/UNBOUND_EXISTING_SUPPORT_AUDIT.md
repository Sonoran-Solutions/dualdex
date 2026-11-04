# Unbound existing-support gap audit (#60 checklist A–G)

Read-only code audit at `ccc43d6` (identical application code to `d592f55`), 2026-11-04 UTC.
No code changed, no ROM read. Candidate under study: header `POKEMON FIRE` / `BPRE`, 32 MiB,
SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`
([release evidence](UNBOUND_RELEASE_EVIDENCE.md)). The coordinator independently re-read the
`unbound.json` flags, the header-title detection step, `isSupportedVanillaGen3`, the
zero-checksum decode branch and `CONFIG_UNBOUND`. Items marked UNCONFIRMED were not traced.

**Production status is unchanged: HOLD.** Nothing below is fixed by this research branch.

## What the app does today with this ROM

- **Profile selection is filename-driven**; the hash is in no profile.
  - Filename containing "Unbound": profile `unbound`, `RECOGNIZED_UNVERIFIED` (`RomHackDetector.kt:99-114`).
  - Any other filename: the header title matches `vanilla_firered` (`RomHackDetector.kt:119-128`),
    so the ROM is presented as FireRed. `unbound.json` `headerTitles` (`UNBOUND`, `POKEMON UNBOUND`)
    can never match the real header; `RomHackProfileTest.kt:300-306` tests an invented header.
- **No live memory reads today**: `mayReadLiveMemory` requires VERIFIED via an exact SHA-256
  (`RomCompatibility.kt:53-56`, `RuntimeRomTrust.kt:56-63`) plus the #204 BOUND/SHA binding.
- **Latent fail-open**: `unbound.json` asserts `isVerified: true` and `memoryLayoutVerified: true`
  with no evidence. Adding the hash alone would open party and location reads. `radical_red.json`
  and `ghost_grey.json` share the pattern. Battle reads stay closed only because
  `battleStateReadVerified` defaults to false.
- `cfruOffsets`, `playerPartyOffset` and `enemyPartyOffset` are parsed (`ProfileLoader.kt:44-46`)
  but never consumed; native layout is chosen by `gameId` only.

## Native CFRU path

- `CONFIG_UNBOUND` (`pokemon_reader.c:424-434`) copies FireRed party offsets, keeps enemy count
  `0x24028` (FireRed was corrected to `0x2402A`, `:347-349`), uses `PARTY_DISCOVERY_HEURISTIC`
  (EWRAM scan, `:1622-1677`) and the default vanilla storage layout.
- **The zero-checksum plaintext shortcut applies to every game** (`pokemon_reader.c:575-590`):
  any record whose checksum word is 0 is read as fixed GAEM plaintext with checksum validation
  skipped. "Attempt B" (`:602-616`) likewise accepts unencrypted data for all layouts.
  [Party layout evidence](UNBOUND_PARTY_LAYOUT_EVIDENCE.md) records the exact-ROM decode paths.
- Field masks are expansion-derived for all layouts: species/moves `& 0x7FF`, item `& 0x3FF`
  (`:633`, `:636`); the exact ROM loads full 16-bit fields.
- Legacy location (`:2236-2261`) uses the Emerald `0x238` offset for any non-FireRed config and
  falls back to `sb1 = ewram` instead of failing.
- Tests are synthetic only (`test_pokemon_reader.c:77-92`, `:3568-3604`, `:2174-2176`, `:6828-6833`);
  fixture replay lists `GAME_UNBOUND` as unsupported (`test_memory_fixture.c:282`).

## Naming (#26)

- `ModernDataPack` (`GameDataPack.kt:162-183`) looks up species by raw ID in the National-Dex-keyed
  database with `allowGlobalFallback = true` (`:25`). Unbound internal IDs differ (Treecko is 277,
  Grookey is 1102; see [data evidence](UNBOUND_DATA_TEXT_EVIDENCE.md)), so same-number substitution
  would show wrong names. UNCONFIRMED: whether vanilla FireRed/Emerald Hoenn IDs have the same gap.
- gameId 9 selects the pokeemerald-expansion item table (`PartyScreenView.kt:487-488`,
  `ClassicPartyScreenView.kt:521-522`), the wrong ID domain for CFRU.
- Abilities resolve to Absent (`GameDataPack.kt:62`): safe, not useful.

## Battle, location, calculator, optional features

- Battle: no battle globals in `CONFIG_UNBOUND`; legacy readers return UNKNOWN. Closed.
- Location: profile `unbound` → `UNVERIFIED` strategy (`LocationResolver.kt:42-47`), safe; the FireRed
  fallback presentation would select the Kanto strategy.
- **Calculator leak today**: profile `unbound` is refused (no capability row). But an Unbound file not
  named "Unbound" is presented as `vanilla_firered` and receives the `VANILLA_GEN3` row
  (`CalcCapabilityPolicy.kt:1263-1273`), with `ROM_NOT_EXACT_VERIFIED` non-blocking (`:583-588`).
  See the [capability inventory](UNBOUND_CALCULATOR_CAPABILITY_INVENTORY.md).
- Smart fast-forward (`MainActivity.kt:384-397`) reads location and main-struct memory without a
  trust gate (speed heuristic only).
- Cheats: catalog empty, SHA-scoped (met). Assistant: removed (met). Docs tab shows FireRed facts or
  an unversioned `docsUrl`; cover art searches by profile name for any non-UNSUPPORTED status
  (`LibraryStore.kt:83`).

## Checklist state

| #60 item | State |
|---|---|
| A layout, counts, save blocks | absent / risky (copied FireRed offsets, heuristic scan, wrong enemy count) |
| A exact hash → VERIFIED | absent; latent fail-open through pre-asserted profile flags |
| A other hashes fail closed | exists generically (tested synthetically) |
| B species/forms, items, abilities | absent / risky (same-number substitution, expansion item table, masks) |
| C battle lifecycle | absent (closed) |
| D maps/location | absent / risky (legacy offset and `ewram` fallback) |
| E static data/name mapping | absent (no generator; `modern` pack non-authoritative) |
| F calculator | refused for `unbound`; leaks via the FireRed fallback |
| G cheats / Assistant / labelling | met / met / risky |
| H hardware | absent |

## Fixes to make before any Unbound hash is trusted (owner-approved production work)

1. Set `isVerified` / `memoryLayoutVerified` false in `unbound.json` (and review Radical Red,
   Ghost Grey); add a bundled-profile invariant test: verified flags require hashes and an
   authoritative native layout.
2. Stop presenting non-retail-size BPRE/BPEE ROMs as vanilla (pass ROM length into detection),
   or make `ROM_NOT_EXACT_VERIFIED` blocking for `VANILLA_GEN3` (owner decision).
3. Gate the zero-checksum/plaintext decode on an explicit `PKMN_STORAGE_CFRU` layout; restore
   checksum rejection elsewhere; add a regression test that a vanilla record with checksum 0 fails.
4. Replace the heuristic scan with evidence-derived static addresses for Unbound; fix enemy count.
5. Return false from legacy location instead of falling back to `ewram`.
6. Pinned Unbound data pack with `allowGlobalFallback = false` and an explicit internal-ID table.
7. Data-pack-owned item/ability domains (no gameId 9 expansion item table).
8. Trust-gate smart fast-forward memory reads (owner call).
9. Label Docs/cover art for unverified profiles.
10. Replace tests that pin invented headers or unverified premises.

Suggested parallel split: trust hardening (Kotlin romhack + profile) ∥ native CFRU storage layout
(native reader + tests) ∥ #26 data pack generator; location, battle lifecycle and calculator
capability follow their evidence and are serialized after the native layout work.

Reusable as-is: exact-ROM trust (`RomCompatibility`, `RuntimeRomTrust`, #204 binding),
`battleStateReadVerified`, `CalcCapabilityPolicy` structure, fixture replay harness, pinned-pack
pattern, runtime-probe host design. Not reusable: `PKMN_STORAGE_EXPANSION` bit semantics, H&S
SaveBlock readers, `hns_*` generators and layouts, the ROWE raw decoder and symbol tables.
