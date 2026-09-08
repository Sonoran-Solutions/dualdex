# DualDex

DualDex is an open-source Android GBA emulator and live Pokémon companion built primarily for the **AYN Thor** dual-screen handheld.

The project started from a pretty simple idea: if the Thor already has a second screen, it should be doing something useful while you play. The top display runs the game, while the bottom display can show live party information, battle tools, damage calculations, documentation, saves, and other game-specific companion features.

DualDex reads game state directly from the emulator for supported ROM profiles, so information like your party or current opponent can be pulled into the companion without having to enter everything by hand.

> **Project status:** DualDex is currently being prepared for its first public beta. The main focus right now is save safety, ROM/version detection, battle-state accuracy, calculator correctness, controller behavior, and release infrastructure. Expect rough edges until the beta checklist is complete.

See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) for the current release plan and the [open issues](https://github.com/Sonoran-Solutions/dualdex/issues) for known bugs and engineering work.

## What DualDex does

### GBA emulation

DualDex uses an embedded **mGBA libretro core** for Game Boy Advance emulation.

Current emulator features include:

- Android `arm64-v8a` support for the AYN Thor, plus `x86_64` builds for development/testing
- OpenGL ES rendering
- Pixel-perfect, sharp bilinear, LCD-grid, and CRT-style display options
- Audio through Android `AudioTrack`
- Fast-forward support
- Battery saves and save states
- ROM loading from Android storage
- Battery-save import/export support

The emulator runs on the Thor's main display while the companion UI is presented on the second display.

### Live Pokémon companion

For recognized and supported game profiles, DualDex can read Pokémon data directly from emulated memory and surface it in the companion UI.

Depending on the game/profile, that can include:

- Current party
- Levels, moves, HP, stats, IVs, and EVs where applicable
- Active battle opponent
- Type matchups
- Location/map information
- Automatic calculator attacker/defender data
- ROM-specific mechanics and custom species data

One of the current beta-hardening goals is making sure DualDex never shows confident-looking live data when the exact ROM layout has not been verified. Unsupported or unverified games should still be playable as normal GBA games without unsafe companion parsing.

### Damage calculator

DualDex embeds `@smogon/calc` locally through QuickJS-NG. Calculations run on-device and do not require a network connection.

The calculator can use live party and opponent data when that information is available from the active game profile. It also supports common field settings such as weather, screens, and critical hits.

ROM hacks can change a lot more than species names, so calculator behavior is being moved toward explicit per-game/version rules rather than assuming every GBA Pokémon game follows vanilla Gen 3 mechanics.

### Companion tools

The bottom-screen companion currently includes tools for things such as:

- Party information
- Damage calculations
- Type matchups
- Map/location data
- Save management
- Game documentation
- Optional Gemini-powered assistance
- Settings and emulator options

The Assistant is optional and is not required for emulation, live party reading, calculations, or the other local companion features.

## ROM profiles

DualDex currently includes profiles for:

- Pokémon FireRed
- Pokémon Emerald
- Pokémon Ghost Grey
- Pokémon Radical Red
- Pokémon Heart & Soul
- Pokémon Unbound

Profile presence does **not** currently mean every release of that game or hack is fully verified. Exact-version detection, memory-layout validation, and per-hack calculator accuracy are part of the work being completed before the public beta.

The long-term goal is to clearly distinguish between:

- **Verified** — exact game/version is tested and supported
- **Recognized / Unverified** — DualDex recognizes the game, but the exact build is not confirmed
- **Unsupported** — emulation works, but live companion features are disabled

## Controls

Standard GBA controls are mapped to the AYN Thor's physical controls, including the D-pad/sticks, A/B, L/R, Start, and Select.

DualDex also has plans for app-level controller shortcuts such as quick save, quick load, fast-forward, and companion navigation. Those shortcuts are still part of the current beta-hardening work and should not be considered final until the first public release.

The companion itself can be controlled through the Thor's bottom touchscreen.

## Building DualDex

### Requirements

- Android SDK Platform 34 (`android-34`)
- Android NDK `27.2.12479018`
- CMake `3.22.1`
- OpenJDK 17

### Clone and build

```bash
git clone https://github.com/Sonoran-Solutions/dualdex.git
cd dualdex

./ci.sh test     # native C parser tests + Kotlin unit tests
./ci.sh build    # assemble the debug APK
./ci.sh all      # test, then build
```

`./ci.sh` is the canonical, fail-closed build/test contract and is what GitHub
Actions runs. The underlying Gradle tasks are `testDebugUnitTest` and
`assembleDebug`.

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Native parser tests

```bash
gcc -O2 \
  -I native/include \
  native/src/pokemon_reader.c \
  native/src/pokemon_text.c \
  native/tests/test_pokemon_reader.c \
  -o native/test_runner

./native/test_runner
```

## ROMs, saves, and game files

DualDex does not include Pokémon ROMs, BIOS files, or other commercial game data. You are responsible for supplying your own legally obtained game files.

Because DualDex is still pre-beta, keep external backups of any save files you care about. Save safety and recovery are release blockers for the first public beta.

## Project roadmap

The immediate goal is a stable **AYN Thor GBA beta**. Broader Android layouts and additional systems come later.

- [Public beta release checklist](RELEASE_CHECKLIST.md)
- [UI design audit](UI_DESIGN_AUDIT.md)
- [Future platform roadmap](FUTURE_PLATFORM_ROADMAP.md)
- [Post-beta Enhanced Battle Console](POST_BETA_ENHANCED_BATTLE_CONSOLE.md)

Long term, the goal is to make DualDex a multi-generation Pokémon companion platform rather than keeping it permanently tied to GBA. That work is intentionally separated from the current beta so it does not turn into feature creep before the first release.

## Technical documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — current emulator, native-memory, rendering, and companion architecture
- [CONTRIBUTING.md](CONTRIBUTING.md) — information for contributing code and ROM profiles

## Contributing

DualDex is still early, so bug reports and testing feedback are useful, especially around save behavior, ROM compatibility, battle tracking, and AYN Thor hardware behavior.

If you're reporting a ROM-hack compatibility issue, include the exact hack version whenever possible. Do not upload ROM files to GitHub issues.

## License

DualDex is licensed under the [MIT License](LICENSE).

The bundled mGBA libretro core is licensed separately under the [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/). Additional third-party notices and dependency provenance are being prepared as part of the public release process.

DualDex is an independent open-source project and is not affiliated with Nintendo, The Pokémon Company, Game Freak, AYN, or individual ROM-hack authors.