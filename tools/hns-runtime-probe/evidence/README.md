# Official H&S 2.0.5 ROM damage goldens (Gap C4d)

These are the **RUNTIME VERIFIED** halves of the Gap C4d calculator goldens: the operands and the
damage were observed on the exact official Heart & Soul 2.0.5 release ROM by the *production*
native readers (`pokemon_read_battler_runtime_state_gba`, `pokemon_read_player_party_gba`,
`pokemon_read_enemy_party_gba`), driven entirely by ordinary controller input through
`tools/hns-runtime-probe`.

Nothing here is a ROM, a save file, a save state, or a cheat. The probe never writes to emulated
memory and never patches the ROM.

## Files

| File | What it is |
|---|---|
| `rom-damage-goldens.json` | Machine-readable record of every golden: operands, observed damage, DualDex roll range, ROM SHA. |
| `golden-a-neutral-tackle.log` | Full probe log for direct Golden A (neutral ordinary damage). |
| `golden-b-stab-type-razor-leaf.log` | Full probe log for direct Golden B (STAB + type resistance). Contains the leveling grind and the asserted hit. |
| `golden-c-stat-stage-scratch.log` | Full probe log for direct Golden C (live non-neutral Defense stage). |
| `golden-e-crit-indirect.log` | The **preserved original** Golden A capture. It contains the indirect critical observation E (the second Tackle took 10 HP -> 0); the direct Golden A golden was re-captured with the SHA-emitting harness. |
| `golden-e-crit-indirect.rom.sha256` | The `sha256sum` captured alongside that preserved capture (it predates the in-log SHA emission), binding it to the same ROM bytes. |

The host half of each golden lives in `native/tests/test_js_calc.c` (`check_gap_c4d_rom_damage_goldens`)
and is executed by `./ci.sh test`: it recomputes the roll vector with an independent oracle and
asserts the observed ROM damage is one of the calculator's rolls.

## Golden assertion contract

Scenarios 60/61/62 use the `golden-grind` command instead of a fixed linear capture. Wild
encounters, their stats and the damage roll are RNG, so a fixed scenario cannot name the encounter
that carries a golden. `golden-grind` is therefore permissive about the grind (it wanders and
retries, and flees encounters that are not the golden so the attacker's level and HP are preserved)
but strict about the golden: it stops with exit 0 only on ONE hit that simultaneously satisfies the
live species, the live raw Defense word, the attacker's live level, the setup move (Golden C's
Leer -> Def -1) and the exact observed damage, and that does **not** faint the target. That hit is
printed as a single machine-readable line, for example:

```text
[GOLDEN-HIT] PASS setup=43 move=10 attackerSpecies=158 attackerLevel=5 defenderSpecies=16 defenderLevel=2 defenderHpMax=13 defenderDef=6 defenderDefStage=-1 hpBefore=13 hpAfter=3 damage=10 frame=5305
```

The `[GOLDEN] label=grind-hit ...` snapshot immediately above it carries the full production-reader
operand set (raw stat words, stat stages, types, ability, item, badges, HP). A run that never lands
an acceptable hit is a script error, so the command fails closed rather than passing on a
near-miss. There is no manual log spelunking and no non-zero-exit "by design" scenario.

## ROM SHA binding

Every run prints the SHA-256 of the ROM file that was actually loaded, on the run header and as a
machine-readable line:

```text
rom sha256 : edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b
[ROM] path=/path/to/hns.gba sha256=edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b loaded=1
```

so the chain is **raw log -> exact SHA-256 -> evidence JSON -> host oracle**. The same value is
recorded per golden as `rom_sha256` in `rom-damage-goldens.json` and as the top-level `rom.sha256`.
The direct A/B/C runs emit it in-log; the preserved indirect capture E predates that emission and
is bound by the captured `golden-e-crit-indirect.rom.sha256` sidecar. It is **evidence bookkeeping
only**: it is deliberately not added to
`app/src/main/assets/profiles/heart_and_soul.json` and does not touch `RuntimeRomTrust`.

## Reproducing

The probe needs a legally obtained H&S 2.0.5 ROM and an mGBA libretro core, exactly as described in
`../README.md`. CI never runs any of this.

```bash
cd tools/hns-runtime-probe
./build.sh

# Starter saves (ordinary progression; scenario 00 selects Chikorita).
./make-save.sh  <mgba_libretro.so> <hns-2.0.5.gba> /tmp/hns205-chikorita.sav

# Golden A (neutral Tackle) — boot the Chikorita save.
./runtime_battle_probe <mgba_libretro.so> <hns-2.0.5.gba> \
    --sav /tmp/hns205-chikorita.sav --script scenarios/60-golden-a-neutral.txt

# Golden C (Leer -> Scratch) — needs a TOTODILE starter save. Scenario 01 is the same progression
# as scenario 00 with the ball selection moved two tiles right (Totodile ball at map (10,4)):
./runtime_battle_probe <mgba_libretro.so> <hns-2.0.5.gba> \
    --script scenarios/01-fresh-rom-to-totodile-save.txt   # writes /tmp/hns205-totodile.sav
./runtime_battle_probe <mgba_libretro.so> <hns-2.0.5.gba> \
    --sav /tmp/hns205-totodile.sav --script scenarios/61-golden-c-stat-stage.txt

# Golden B (STAB + resistance) — grinds the starter to level 6 (Razor Leaf), then grinds the
# asserted Razor Leaf hit on a wild Pidgey.
./runtime_battle_probe <mgba_libretro.so> <hns-2.0.5.gba> \
    --sav /tmp/hns205-chikorita.sav --script scenarios/62-golden-b-stab-grind.txt
```

Because the RNG is not reproducible across runs, a re-run will usually observe a different wild
encounter; the command searches until it finds one that satisfies the asserted operands. If a run
does not find one within its encounter budget it exits non-zero, so a failure is visible rather
than silently reported as success.

## Honest limits

* Golden E is **RUNTIME OBSERVED (indirect)**: no critical-hit bit or message was read, and the
  fainting target caps the delta at its remaining HP, so the exact critical roll is unobservable.
  It is recorded under `indirect_observations` and is **not** counted among the direct A/B/C roll
  goldens. Its raw capture is the preserved `golden-e-crit-indirect.log`.
* Goldens D, F and G are not claimed (see `rom-damage-goldens.json` `deferred`).
* The ROM SHA-256 in the JSON is **evidence only**. It is deliberately not added to
  `heart_and_soul.json`, so the product trust model is unchanged.
