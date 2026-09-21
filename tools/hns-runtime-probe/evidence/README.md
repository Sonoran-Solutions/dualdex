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
| `rom-damage-goldens.json` | Machine-readable record of every golden: operands, observed damage, DualDex roll range. |
| `golden-a-neutral-tackle.log` | Full probe log for Golden A (neutral ordinary damage). |
| `golden-b-stab-type-razor-leaf.log` | Full probe log for Golden B (STAB + type resistance). Also contains the grinding turns. |
| `golden-c-stat-stage-scratch.log` | Full probe log for Golden C (live non-neutral Defense stage). |

The host half of each golden lives in `native/tests/test_js_calc.c` (`check_gap_c4d_rom_damage_goldens`)
and is executed by `./ci.sh test`: it recomputes the roll vector with an independent oracle and
asserts the observed ROM damage is one of the calculator's rolls.

## Reproducing

The probe needs a legally obtained H&S 2.0.5 ROM and an mGBA libretro core, exactly as described in
`../README.md`. CI never runs any of this.

```bash
cd tools/hns-runtime-probe
./build.sh

# Starter saves (ordinary progression; the C4c/C4d scenario 00 selects Chikorita).
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

# Golden B (STAB + resistance) — grinds the starter to level 6 (Razor Leaf) and uses the move on
# the first encounter after it is learned. Wild species are RNG; the committed log records the
# encounter that actually happened.
./runtime_battle_probe <mgba_libretro.so> <hns-2.0.5.gba> \
    --sav /tmp/hns205-chikorita.sav --script scenarios/62-golden-b-stab-grind.txt
```

## Honest limits

* Golden B's scenario is a developer aid: it tolerates `hunt` timeouts and move-not-known errors
  while it levels the starter, so its exit status is non-zero even when it captures a good hit.
  The committed log shows the captured hit; the JSON records its operands.
* Goldens D, F and G are not claimed (see `rom-damage-goldens.json` `deferred`).
* The ROM SHA-256 in the JSON is **evidence only**. It is deliberately not added to
  `heart_and_soul.json`, so the product trust model is unchanged.
