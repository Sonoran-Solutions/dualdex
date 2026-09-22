# Vanilla retail-ROM calculator runtime probe

Developer-only controlled experiment, modeled on `../hns-runtime-probe/`: production libretro
host, frame-scripted input, production party decrypt/checksum reader, explicit nonzero failures.
It is not shipped in the APK. CI runs only its ROM-free controls and evidence-record checks.

This probe **stages combatant operands in emulator RAM**. It is not an unmodified-playthrough
golden. The ROM file and its code are never patched. No damage result, RNG value, critical flag,
battle script, callback, weather, screen, badge, or lifecycle variable is written. After the two
BattlePokemon records are staged, all writes are sealed and ordinary A input selects the move.
The actual retail battle engine computes damage and updates HP. No hit is retried or selected
according to its damage: the first defender HP change must pass, or the run fails.

## Reproduce

Use your own legally obtained, local dumps. Nothing downloads a ROM. No ROM, save, save state,
rendered game image, or compiled binary belongs in this directory's committed evidence.

```bash
./tools/vanilla-runtime-probe/build.sh
python3 tools/vanilla-runtime-probe/test_probe.py

python3 tools/vanilla-runtime-probe/probe.py \
  --core /path/to/mgba_libretro.so --game firered \
  --rom /path/to/legal-firered-rev0.gba --case physical --output /tmp/fr-physical.json
# Repeat with --case special, then both cases with --game emerald and its legal dump.
```

Requirements: Linux, Python 3 standard library, a C11 compiler, and an mGBA libretro core. The
adapter build uses the same production host/reader sources as the H&S developer tool; no alternate
Gradle/CMake invocation is needed. Canonical project validation remains `./ci.sh all`.

The recorded core is mGBA source commit `e31759b24e7a4e3899285ff720d7b573ac328ae7`, Linux x86_64,
Release, libretro enabled (SDL/Qt disabled), SHA-256
`fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`.
The host supplies no external BIOS or battery save. Each process starts a fresh cartridge with
blank save RAM. A temporary copy of the **already hashed bytes** prevents a path re-read race;
it is deleted on exit. Both the bundled profile and the scenario's exact revision must match
before any core is initialized. FireRed Rev 1 is deliberately refused by this tool: no legal
local dump was available for runtime observation. Its existing source/host evidence is unchanged.

The scenario files are fixed input movies from boot: `run FRAMES BUTTON_MASK`, or
`mash COUNT [BUTTON_MASK]` (one pressed frame, 90 neutral frames; default A). Masks use the
production libretro host: A=256, B=1, Start=8, Up=16, Down=32, Left=64, Right=128. A fresh replay
must reach the exact recorded RGB565 battle-menu SHA-256 at frame 31071 (FireRed) or 45553
(Emerald). Core/version differences that change this checkpoint **fail**; do not waive the
checkpoint to get a green result. There is no required external save-state artifact or wall-clock
input. Emerald's new-game clock is accepted by the fixed input script; damage RNG is not seeded
or edited by the harness.

## Experiment and assertions

The input movie reaches FireRed's first rival battle (Bulbasaur vs Charmander) or Emerald's Birch
rescue (Torchic vs Zigzagoon). Before staging, **both** battle records must agree on species,
five stats, four moves, level, HP and max HP with `pokemon_parse_single()` on the original party
slots at production-configured addresses. Neutral stages/status and two-battler topology are
also required. This is a diagnostic cross-check in this fixed scene, not active-party resolution.

The experiment then writes just the two 88-byte BattlePokemon records, retaining non-operand
names/OT/experience. The staged damage operands match the two existing committed fixtures:

| Operand | Attacker | Defender |
|---|---|---|
| Species | Machamp (68) | Snorlax (143) |
| Level / nature / IV / EV intent | 50 / Hardy / all 31 / all 0 | same |
| HP | 165/165 | 235/235 |
| Atk / Def / Speed / SpA / SpD | 150 / 100 / 75 / 85 / 105 | 130 / 85 / 50 / 85 / 130 |
| Types | Fighting / Fighting | Normal / Normal |
| Effective ability | Guts (62), inactive with status=0 | Immunity (17) |
| Item / status1 / status2 | 0 / 0 / 0 | 0 / 0 / 0 |
| All stat-stage bytes | 6 (neutral) | 6 (neutral) |
| Move slot 0 | Rock Slide (157) or Crunch (242) | Splash (150) |

There is no EV array in BattlePokemon: the raw stats are the calculated values for the stated
IV/EV/nature fixture. This is a battle arithmetic experiment, not an experiment in generating
legal party Pokémon or constructing a save. Names/sprites can still show the original starter;
the machine-readable combatant records are the staged calculator inputs.

The opening scenes provide unbadged Singles, clear weather, no screens and no prior-turn
volatiles. That state follows from the fixed fresh-game route and upstream opening-battle
initialization, not a new general-purpose weather/badge/lifecycle reader. The first action is
selected immediately after staging. The relevant combatant fields must remain unchanged through
the hit (except HP/PP). The observed current move, attacker, target and crit multiplier must match;
the engine's damage word must equal `235 - postHP`, and that non-fainting delta must be a member
of the **committed** 16-roll vector. The independent oracle must separately reproduce the entire
committed vector, including all metadata. The oracle is never used as an emulator or to write HP.

Results are JSON with ROM/core/tool/scenario/golden hashes, original party checkpoints, readable
staged combatants, exact request/vector, frames, pre/post HP, engine damage and attribution.
Only a completed run writes `verdict: PASS`. A failed run exits nonzero and leaves a FAIL output,
so a previously successful output cannot masquerade as a new successful run.

## Diagnostic address provenance and trust boundary

Struct ABI: pinned `pret/pokefirered@c75f352304d529f6ba92d4f74b9cf8b5c3810788` and
`pret/pokeemerald@5eff78649e7170a877b961ef0b3da13b81a16038`, `include/pokemon.h`,
`struct BattlePokemon`; globals' declaration order in `src/battle_main.c`. Production reader
config supplies and checks the shared stride=88, HP offset=40, stages offset=24. All actual reads
use the production bounds-checked `libretro_host_read_gba_address()`.

| Diagnostic | Relative to BattlePokemon base, both builds |
|---|---|
| gBattlersCount / gBattlerPositions | -24 / -14 |
| gCurrentMove / gBattleMoveDamage | +0x166 (u16) / +0x16c (s32, positive here) |
| gBattlerAttacker / gBattlerTarget / gCritMultiplier | +0x187 / +0x188 / +0x18d |

Retail bases discovered in the initial scene by matching both complete structured combatants,
then corroborated by the production party parser and the attributed HP transition:
FireRed Rev 0 `0x02023BE4`, Emerald `0x02024084`. These are **probe-local** constants. They differ
from the production `0x02023F90` / `0x02024064`. A literal-address occurrence in a cartridge,
as used by the earlier static audit, proves address use, not that it names `gBattleMons`.
The checked menu plus structural/party/hit correlation is the evidence here; merely finding a
species at an address would not be sufficient.

Neither `battleStateReadVerified` flag is changed. No legacy presence heuristic or production
lifecycle/enemy-party resolver is called. This demonstrates neither general lifecycle correctness
nor safe live autofill, stat stages, opponent tracking, or UI control in arbitrary vanilla battles.
It supplies two damage observations per build, not all 16 RNG rolls, all moves/modifiers, physical
hardware equivalence, or FireRed Rev 1 runtime coverage. FireRed's opening tutorial suppresses
critical hits; crit=1 is also explicitly checked. Critical-hit runtime coverage is not claimed.

## Failure controls

`test_probe.py` exercises the same verdict functions with wrong ROM identity, a removed profile
hash, a wrong vector, a changed golden roll that still contains the observed hit, an oracle with
all rolls shifted by +1, a changed request, HP/damage/move/critical/attacker/target mutations,
no-hit timeout, unknown input commands and attempted writes after sealing. It also checks every
committed observation against the current goldens and scenario/tool hashes. These tests require
no core/ROM and run under `./ci.sh test` and `all`.

The runtime identity negative control is to run the FireRed scenario with your Emerald dump:
it must print `FAIL ROM identity mismatch`, exit 1 before core initialization, and leave FAIL
in the requested output. The recorded experiment repeats each fresh-ROM case; matching JSON
records demonstrate repeatability with the recorded core. The evidence files are observations,
not substitutes for a new emulator run.
