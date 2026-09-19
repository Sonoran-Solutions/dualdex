# H&S 2.0.5 runtime battle-state probe (developer tool)

Developer-only diagnostic used to produce the runtime half of the Heart & Soul 2.0.5 battle
lifecycle evidence recorded in `docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md` (§11).

It is **not** built by `ci.sh`, **not** part of the APK, and **not** a product feature.

## What it does

1. loads a locally built/bundled mGBA libretro core, a **legally obtained** H&S 2.0.5 ROM and
   optionally a locally produced battery save;
2. drives the machine frame by frame under a small input script while sampling the exact compiled
   battle globals DualDex reads;
3. feeds the **live** emulated memory into the **production** readers
   (`pokemon_read_battle_lifecycle`, `pokemon_resolve_active_enemy`,
   `pokemon_read_player_party_gba`, `pokemon_read_enemy_party_gba`);
4. checks the runtime invariants on **every** frame and **fails the run** (non-zero exit) if **any**
   invariant is violated — violations are accumulated so a single run shows every problem it found,
   rather than stopping at the first one:
   - `ACTIVE` only while `gMain.inBattle` is authoritatively true,
   - production battle presence agrees with the lifecycle,
   - an unresolved opponent never becomes slot 0 (or any slot),
   - no stale opponent survives battle exit,
   - a party slot always comes from `gBattlerPartyIndexes[battler]`,
   - the resolved battler index is the real battler,
   - enemy slots stay inside `gEnemyPartyCount`,
   - a doubles battle never silently selects one opponent,
   - unreadable authority degrades to `UNKNOWN`;
5. prints `[MATRIX]` lines — the full runtime tuple — at every `matrix` step, so each evidence row
   in the compatibility document can be traced back to one observed frame.

## Exit status

A scripted run exits **non-zero** when either of these is non-zero:

```text
runtime invariant violations
script errors
```

A "script error" is any deterministic script action that did not do what the scenario claims it
did. This is what stops a reproduction run from going green when the scenario never actually
happened. These are all fatal:

| Situation | Command |
|---|---|
| the wild encounter was never reached | `hunt` timeout |
| a modal UI was never entered | `await` timeout |
| a modal UI never released | `untilout` timeout |
| the player was never freed from a script lock | `escape` timeout |
| a step could not be taken | `walk` blocked (opt out per-step with the trailing `optional` token) |
| the battery save could not be written | `savsave` failure |
| missing file, wrong size, or a rejected image | `savload` failure |
| a scenario assertion did not hold | `assert-battle` / `assert-party-count` / `assert-map` / `assert-in-battle-flag` |
| a typo in the script | unknown command, reported with its line number |

```text
script error line 42: unknown command 'foo'
```

It never writes to the emulated machine, never applies cheats, never patches the ROM, and never
prints or stores ROM bytes. No ROM, save file or save state is committed to this repository.

## Build

```bash
./build.sh
```

## Run

```bash
./runtime_battle_probe <mgba_libretro.so> <hns_2.0.5.gba> \
    [--sav <path.sav>] [--script <path>] [--frames n] [--quiet]
```

Without `--script` the probe falls back to mashing START/A for `--frames` frames, which can only
ever observe the boot/intro state. That fallback mode has no scenario to fail, so it exits non-zero
only on a runtime invariant violation.

## Creating the battery save (issue #1 phase 1)

`scenarios/00-fresh-rom-to-starter-save.txt` drives a **fresh** ROM to a real in-game save with a
starter, using nothing but ordinary button presses:

```bash
./make-save.sh <mgba_libretro.so> <hns_2.0.5.gba> $HOME/hns205.sav
```

That is the supported way to obtain a legal save on a new machine. It is normal game progression —
no RAM poking, no cheats, no ROM patching, no fabricated save structure. Keep the `.sav` outside
the repository (the provided `.gitignore` does not need to know about it because it lives in
`$HOME`/`/tmp`); never commit it.

`make-save.sh` runs the probe with `set -e`, so any scripted progression failure aborts it, and it
then requires the output to be exactly 131072 bytes (`0x20000`) and not a blank all-`0xFF` battery
image before reporting success.

The core expects a plain 128 KiB (`0x20000`) battery image, which is what mGBA's libretro port
publishes through `RETRO_MEMORY_SAVE_RAM` for this ROM. Any emulator that writes a standard GBA
`.sav` works; no save state is involved.

## Scenarios

| Script | What it exercises |
|---|---|
| `scenarios/00-fresh-rom-to-starter-save.txt` | Fresh ROM → starter → in-game save |
| `scenarios/10-wild-battle-entry.txt` | Overworld baseline → tall grass → wild encounter |
| `scenarios/20-wild-battle-full.txt` | Entry → damage → opponent faint → teardown → exit |
| `scenarios/45-route30-to-violet-city.txt` | **PASSES.** Route 30 → Route 31 → gate → Violet City → Pokémon Center heal → in-game save |
| `scenarios/44-opponent-voluntary-switch.txt` | **PASSES.** Bug Catcher Don (Route 30) voluntary opponent switch: Ledyba stays alive at 3/15 HP while the engine moves the opponent to party slot 1 and the production reader follows it |
| `scenarios/34-route30-don-ready.txt` | Route 30 → Youngster Mikey → Don's approach → in-game save |
| `scenarios/47-don-damage-probe.txt` | **PASSES.** Diagnostic that asserts nothing about switching: bounded Razor Leaf damage trajectory against Don's Ledyba Lv3 (measured 4 HP per hit). It is the evidence behind the withdrawn one-shot rejection |
| `deferred/46-sprout-tower-3f.txt` | **DEFERRED, does not pass.** Kept out of `scenarios/` on purpose: it localises an unresolved field-state lock after the Sprout Tower 2F trainer battle. Not run by any gate; see §11.10.7 of the compatibility evidence |

## Overworld navigation: what the source gets right and what it does not

Legs 45 and 46 are generated offline from the pinned decomp (`map.bin` collision + elevation,
`metatile_attributes.bin` behaviours, `map.json` connections/warps/coord events) and every run end
is asserted against the engine's own coordinates. Four source-derived predictions turned out to be
**wrong on the running ROM** and had to be corrected by measurement; they are documented here
because each one silently breaks a naive route:

1. **Sideways staircases.** A `MB_SIDEWAYS_STAIRS_*` metatile has collision 0 / elevation 0 in
   `map.bin`, but the engine turns a horizontal press *onto* it into a **diagonal** move
   (`src/event_object_movement.c: GetSidewaysStairsCollision`). Route 31's crossing at x=25..27,
   y=10..13 is the measured chain `(27,11) -LEFT-> (26,11) -LEFT-> (25,12) -LEFT-> (24,13)`.
2. **Arrow warps.** `MB_WEST_ARROW_WARP` does **not** fire when the player steps onto it. It fires
   when the player is standing on it and presses the arrow direction. Measured at Route 31 (4,10)
   and Gate_Route31_VioletCity (1,5).
3. **Doors settle late.** Stepping onto a door changes the coordinates immediately but the warp
   resolves a few frames later, eating the next press. The generator therefore ends a run at every
   transition and inserts `wait 75` before asserting.
4. **Cutscene traps.** Route 32's coord triggers at (27,10), (28,10), (29,10) are the *only*
   corridor south of Route 32; `Route32_EventScript_BaldingManCheck` applies
   `Route32_Movement_Turnback` until `FLAG_HIDE_SPROUT_TOWER_SILVER`, `FLAG_DEFEATED_VIOLET_GYM`
   and `FLAG_RECEIVED_TOGEPI_EGG` are all set. Route 32 south is a mandatory story gate, not an
   optional route.


```bash
./runtime_battle_probe <core> <rom> --sav $HOME/hns205.sav \
    --script scenarios/20-wild-battle-full.txt
```

Each scenario asserts the state it claims to reach (`assert-battle`, `assert-party-count`,
`assert-map`, `assert-in-battle-flag`), so a run that exits 0 has demonstrated the scenario, not
merely run past it.

## Self-test baseline save

`selftest.sh` must be run against a **fresh starter save** (`./make-save.sh <core> <rom> <out.sav>`),
which stands the player in New Bark Town at `(10, 10)` on map `0/0`. Four of its cases are
position-dependent and walk into Elm's lab or hit its interior walls, so running it against a
mid-progression save makes them fail for the wrong reason. With the correct baseline the suite is
**20 cases, 0 failures**.

## Self-test: proving the harness can fail

The strictness above is itself covered by a developer-only self-test. It runs deliberately-failing
scenarios and requires a **non-zero** exit from each, plus one control that must still exit 0:

```bash
./selftest.sh <mgba_libretro.so> <hns_2.0.5.gba> --sav $HOME/hns205.sav
```

It covers: unknown command, missing `savload` file, failing `savsave`, an unopenable `--script`,
unloadable `--sav`, false `assert-battle`, `await` timeout, `untilout` timeout, blocked `walk`, the
`walk ... optional` control, `escape` timeout, and `hunt` timeout. The malformed scripts are
generated into a temporary directory and are not committed.

## Script commands

| Command | Meaning |
|---|---|
| `press <BTN\|NONE> <frames>` | hold buttons for N frames (`A`, `B`, `START`, `DOWN`, … or `A+B`) |
| `wait <frames>` | hold nothing |
| `mash <frames>` | hold A on a 4-on / 6-off cycle |
| `spama <count>` | press A a bounded number of times with long gaps |
| `walk <DIR> <tiles> [optional] [battle]` | walk tile by tile; presses A when a script lock blocks progress; a blocked step is fatal unless `optional` is given; a wild encounter is cleared automatically, and a **trainer** battle intercepted by the step is fatal unless `battle` is given, in which case it is reported and finished with ordinary input |
| `engage <DIR> <n>` | make a trainer start a battle without polluting its first action menu: turns to face `DIR`, then presses A until `gMain.inBattle` asserts — and presses **nothing** if a battle is already active, so a sight-line trigger and a talk both lead to a clean menu |
| `damage-probe <moveId> <turns>` | DIAGNOSTIC, asserts nothing: drives `turns` turns of an already-active single battle with one named move (found in the live move list) and prints the opponent's HP trajectory after every change. Critical status is reported UNKNOWN — detecting it would need a speculative address |
| `party-stats player\|enemy` | print every party member exactly as the **production** reader parsed it — species, level, live HP, atk/def/**speed**/spa/spd, nature, IVs, EVs and the live move list. Used to MEASURE facts a scenario must not assume (turn order depends on the Speed stat) |
| `hunt <iterations>` | wander until `gMain.inBattle` asserts |
| `escape <DIR> <iterations>` | press UP+A then try to move (for re-triggerable dialogue and Yes/No prompts) |
| `await <cb2> <n>` | press A until `gMain.callback2` becomes `cb2` |
| `untilout <cb2> <n>` | press UP+A while `gMain.callback2` is `cb2`, stop when it changes |
| `walkto <x> <y> [<maxIterations>]` | move the player to a named tile on the current map, planning on a 4-connected grid and re-reading the player's tile after every step so the engine is the authority on whether a step was legal. A failed step is only believed after several spaced attempts (NPCs wander); a destination that survives them is recorded per map and routed around |
| `await-enemy-voluntary-switch <oldSlot> <newSlot> <oldSpecies> <maxFrames>` | drive turns until an AI **voluntary** switch is observed: one opponent battler active at `oldSlot` with HP > 0, then the authoritative `gBattlerPartyIndexes[opponent]` rewrite to `newSlot` resolving through the production reader with the outgoing mon **still alive**. Fails if the outgoing mon ever reaches HP 0 (that is the faint path, Scenario 41). `gChosenActionByBattler` / `B_ACTION_SWITCH` are **not** read and **not** claimed — see the compatibility evidence §11.10.8 |
| `matrix <label>` | print the full runtime tuple for the current frame |
| `shot <path.ppm>` | dump the current video frame |
| `savsave <path>` / `savload <path>` | flush / load battery save RAM |
| `assert-battle inactive\|active` | assert the authoritative lifecycle state |
| `assert-in-battle-flag true\|false` | assert `gMain.inBattle` |
| `assert-party-count player <n>` | assert `gPlayerPartyCount` |
| `assert-map <group> <number>` | assert the map the player is standing on |
| `reject-encounter` | fail the run if a battle started in a scenario that must stay out of battle |

## Trust boundary

The probe deliberately runs **outside** DualDex's trust model: it does not add the H&S SHA-256 to
`heart_and_soul.json` and it does not unlock live memory for the product. It only reads emulated
memory from a throwaway host process. `sha256Hashes` stays empty, `battleUiVerified` stays false
and `interactiveControlsVerified` stays false (see issue #40).

## Address provenance

`gMain` is read at the **release-ROM** address `0x03005BD8` (`HNS_RELEASE_GMAIN_BASE` in the probe
source), not at the `0x03005BC0` a from-source `make hns` build reports. The two differ by the same
`0x18` shift that moves `gSaveBlock1Ptr` to `0x030041D8`, and the release address is the one whose
key-register words track live input. If a future ROM or build moves it again, the probe's
"`lifecycle ACTIVE only while gMain.inBattle`" invariant is what will catch it.

> An earlier revision of this tool read `0x03005BC0 + 0x439 = 0x03005FF9` and treated "readable and
> constantly zero across 20,000 boot frames" as confirmation. That reasoning is **superseded**: any
> unused zero-filled IWRAM byte passes the same test. Symbol identity here rests on semantic
> correlation with game behaviour, not on readability.
