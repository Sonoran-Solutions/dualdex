# H&S 2.0.5 runtime battle-state probe (developer tool)

Developer-only diagnostic used to produce the runtime half of the Heart & Soul 2.0.5 battle
lifecycle evidence recorded in `docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md` (§10).

It is **not** built by `ci.sh`, **not** part of the APK, and **not** a product feature.

## What it does

1. loads a locally built/bundled mGBA libretro core and a **legally obtained** H&S 2.0.5 ROM;
2. steps the machine frame by frame while pressing START/A (the same thing a player does) and
   samples the exact compiled battle globals DualDex reads;
3. prints a lifecycle table whose rows appear on every observed state change;
4. feeds the **live** emulated memory into the **production** readers
   (`pokemon_read_battle_lifecycle`, `pokemon_resolve_active_enemy`,
   `pokemon_read_player_party_gba`, `pokemon_read_enemy_party_gba`) and fails if any of them:
   - reports a slot while its `known` flag is false,
   - reports an enemy party while the lifecycle is not `ACTIVE`,
   - names an opponent slot outside the authoritative enemy party bounds.

It never writes to the emulated machine, never applies cheats, never patches the ROM, and never
prints or stores ROM bytes. No ROM, save file or save state is committed to this repository.

## Build

```bash
./build.sh
```

## Run

```bash
./runtime_battle_probe <mgba_libretro.so> <hns_2.0.5.gba> [frames]
```

## Trust boundary

The probe deliberately runs **outside** DualDex's trust model: it does not add the H&S SHA-256 to
`heart_and_soul.json` and it does not unlock live memory for the product. It only reads emulated
memory from a throwaway host process. `sha256Hashes` stays empty, `battleUiVerified` stays false
and `interactiveControlsVerified` stays false (see issue #40).

## Result interpretation

A run with no save file can only ever observe the boot/intro state, so it verifies the
overworld-style **inactive** case and the fail-closed invariants, not the battle transitions.
Reaching wild/trainer battles, switches, faints and doubles requires a legal save file with a
party and reachable encounters; without one those scenarios are reported as
`NOT RUNTIME VERIFIED` rather than assumed.
