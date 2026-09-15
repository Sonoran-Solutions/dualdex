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

```bash
./runtime_battle_probe <core> <rom> --sav $HOME/hns205.sav \
    --script scenarios/20-wild-battle-full.txt
```

Each scenario asserts the state it claims to reach (`assert-battle`, `assert-party-count`,
`assert-map`, `assert-in-battle-flag`), so a run that exits 0 has demonstrated the scenario, not
merely run past it.

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
| `walk <DIR> <tiles> [optional]` | walk tile by tile; presses A when a script lock blocks progress; a blocked step is fatal unless `optional` is given |
| `hunt <iterations>` | wander until `gMain.inBattle` asserts |
| `escape <DIR> <iterations>` | press UP+A then try to move (for re-triggerable dialogue and Yes/No prompts) |
| `await <cb2> <n>` | press A until `gMain.callback2` becomes `cb2` |
| `untilout <cb2> <n>` | press UP+A while `gMain.callback2` is `cb2`, stop when it changes |
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
