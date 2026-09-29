# H&S 2.0.5 differential damage oracle (issue #90)

Ground-truth damage for Heart & Soul 2.0.5, produced by the **real pinned H&S battle engine**, and a
differential test that compares DualDex's shipped QuickJS calculator against it over all 16 damage
rolls.

```text
pinned H&S battle code (1f42b74d)  ->  this oracle  ->  corpus.json (committed, ROM-free)
                                                          -> native/tests/test_hns_damage_oracle.c
                                                             (shipped calc_bundle.js via QuickJS)
```

## What it is (and is not)

* **Is:** a generator that builds the pinned H&S tree's own battle test runner and measures, for each
  scenario, the HP actually removed by one hit for each of the 16 damage-roll values. Every roll is a
  separate, fresh battle. The corpus stores the scenario inputs, what the engine reported about the
  hit (IDs, battle types, base stats, effective move type/power/category/flags/effective priority/
  target class, explicit `ateBoost`, target count, raw `status1` words, badge-boost verdicts)
  and the 16 measured rolls.
* **Is not:** `@smogon/calc`, DualDex's `calculateHnsDamage`, `calc_bundle.js` or any Kotlin damage
  code. None of those are imported or executed by the generator. The type tables in
  `oracle_matrix.py` only choose interesting scenarios; they are never expected values.
* **Is not** a ROM runtime probe. No ROM, BIOS, save file or commercial binary is used, read or
  committed. The test ELF is compiled from the pinned source in a scratch directory and never leaves it.

## Backend choice: Option A (pinned expansion test runner)

Evaluated in the order issue #90 prescribes.

**Option A — accepted.** The pinned tree (`PokemonHnS-Development/pokehns-expansion` @
`1f42b74dff0e9fe942419845d040663dd829a973`, tree `586946f2…`) contains the pokeemerald-expansion
battle test framework (`test/test_runner_battle.c`, `include/test/battle.h`). With it a test can:

* create battlers with any species, level, raw stats (`Attack(n)` etc.), HP/max HP, ability, item and
  `Status1`, in Singles or Doubles, on either side;
* rig the damage roll per hit: `MOVE(..., WITH_RNG(RNG_DAMAGE_MODIFIER, v))`, where the pinned
  `DoMoveDamageCalcVars` multiplies by `(100 - v)%`, and force `criticalHit: TRUE/FALSE`;
* capture the damage from the HP-bar event (`HP_BAR(..., captureDamage: &results[i].damage)`);
* read the live `gBattleMons`, `gBattleWeather`, `gSideStatuses` after the hit.

Proof before the framework was built: `make BUILD=hns TEST=1 pokehns-test.elf` with the Arm GNU
Toolchain 13.2.rel1, run headlessly by the pinned `tools/mgba-rom-test-hydra` + `tools/mgba/mgba-rom-test`
(no ROM), reproduced DualDex's hand-derived `gap_c4a` vector (Machamp Rock Slide, 51..60) and upstream's
own Bulbapedia-derived `damage_formula.c` vector (Glaceon Ice Fang vs Garchomp, 168..196).

Two deviations from a stock `make check`, both hashed into the corpus provenance:

1. `patches/0001-test-runner-include-order.patch` — at the pinned commit H&S's `include/fake_rtc.h`
   dereferences `struct SaveBlock3`, but `test/test_runner.c` includes it before `global.h`, so the
   stock runner **does not compile**. The patch swaps the two include lines. It touches only
   `test/test_runner.c`; the generator refuses any patch outside `test/`.
2. Upstream test *cases* are removed from the scratch export (the runner, its self-test and the headers
   `src/` includes are kept). Upstream's damage tests assert English battle messages that H&S changed,
   so they fail on `MESSAGE` matching even though their damage values reproduce.

**Option B (runtime probe) — not needed**, so it was not built. Option A needs no ROM and controls
every operand directly. After the selected hit, the harness calls pinned `SetTypeBeforeUsingMove`
again to recapture the exact effective type and `ateBoost`, which battle cleanup clears before the
test's `THEN` block.

### Harness boundaries found while building it

Each of these was caught by the oracle's own fail-closed checks, not assumed:

* `SINGLE_BATTLE_TEST`/`DOUBLE_BATTLE_TEST` run as recorded **link** battles, and the pinned
  `ShouldGetStatBadgeBoost` never boosts in link battles (`src/battle_util.c:9147`). Scenarios holding
  badges therefore run as `WILD_BATTLE_TEST` (as upstream's own `badge_boost.c` does); within the
  damage pipeline the link flag gates nothing else. Found because the `gap_c4b_badge_boosts`
  cross-reference disagreed.
* A wild opponent repeats its first scripted action on every later turn, so wild scenarios may only
  let the opponent act in a single-turn battle (the generator refuses anything else). Found by the
  stat-stage verification.
* `FLAG_SET` may be used once per test (further badge flags use `FlagSet`; save blocks, and so all
  flags, are cleared before every test), and a battler knows at most four moves (the setup planner
  uses one move per stat and the generator refuses a fifth).
* For a spread move in Singles the raw `GetMoveTargetCount` still counts the empty partner slot; the
  engine only consults it inside `IsDoubleBattle()`. The corpus records the raw value and the
  differential adapter sends it only for Doubles.

## How rolls are controlled and captured

For scenario `S` the generator emits one `SINGLE_BATTLE_TEST("DDXO S", ...)` (or `DOUBLE_BATTLE_TEST`)
with sixteen `PARAMETRIZE` runs. Run `i`:

1. sets the H&S challenge settings the scenario names (`tx_Mode_Fairy_Types`, `optionStyle`) and the
   player's badge flags;
2. establishes stat stages, weather and screens with real in-battle setup moves (Swords Dance, Leer,
   Rain Dance, Reflect, …) placed so that weather/screens are set on the last setup turn;
3. in Doubles with a fainted defender partner, KOs that partner on the first setup turn;
4. executes the measured move with `WITH_RNG(RNG_DAMAGE_MODIFIER, i)`, `criticalHit` forced and
   `secondaryEffect: FALSE`;
5. captures the HP-bar damage and the attacker's HP after setup-turn damage ticks (status damage and,
   for an affected Solar Power attacker in Sun, its maxHP/8 loss), so `hpAtHit` is the actual live HP
   at the measured move. It then validates only the separate post-hit Solar Power residual and
   prints the battle state used for the damage calculation.

`rolls[k]` in the corpus is the hit at random factor `(85+k)%` = `WITH_RNG(..., 15-k)`. Index 0 is the
minimum roll, 15 the maximum. The generator then verifies, per roll and fail-closed:

* the runner reported `PASS` for the test (skips count as failures);
* all 16 rolls and every observation line are present exactly once;
* species, ability, item and last-used move equal the scenario's pinned symbols (checked in C);
* level, raw stats, status, stat stages, weather, screens, format, Fairy rule and option style
  equal the scenario (checked in Python);
* HP-bar damage equals the defender's HP delta; immunities remove no HP; no damaging roll is 0;
  no roll is near the 60000-HP defender's capacity;
* every observation is identical across the 16 rolls (no state leaks between runs).
* the Hydra process exits with status 0; complete-looking output from a failed runner is rejected.

Any violation aborts regeneration with the scenario ID. Nothing is defaulted or turned into zero.

## Corpus schema (v5)

Defined and validated by `oracle_schema.py`. Each scenario names only authoritative operands:

| Field | Meaning |
|---|---|
| `id` | stable, descriptive, `[a-z0-9-]`, unique |
| `tags`, `surface` | `modelled` (production claims exact) or `engine-only` (production refuses/strips: Doubles, Thick Fat, Guts outside the physical/status path, Huge/Pure Power, type-boost items, and relevant Mold Breaker suppression cases) |
| `format`, `attackerSide`, `doubles.defenderPartner` | Singles/Doubles, which side attacks, Doubles target presence |
| `rules` | H&S challenge settings that change damage: `fairyTypes`, `optionStyle` (`perMoveSplit`/`typeBased`) |
| `badges` | player badge flags held (1..8) |
| `attacker`/`defender` | pinned `SPECIES_*`/`ABILITY_*`/`ITEM_*` symbols + labels, level, raw stats incl. HP/max HP, status, relevant stat stages |
| `move` | pinned `MOVE_*` symbol + label |
| `crit`, `field` | forced crit flag; weather (`none`/`rain`/`sun`), Reflect, Light Screen |
| `expect` | `damage` or `immune` (a declared immunity must remove no HP) |

`observed` (recorded, not chosen): species/ability/item/move IDs, battle types, pinned base stats,
effective move type/power/category/target class, explicit `ateBoost`, `GetMoveTargetCount`, `hpAtHit`, each battler's raw
`BattlePokemon.status1`, and the engine's own `ShouldGetStatBadgeBoost` verdicts per battler.

## Commands

```bash
# ROM-free check used by ./ci.sh test (no upstream, toolchain or emulator needed)
python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py check
(cd tools/hns-damage-oracle && python3 -m unittest test_hns_damage_oracle -v)

# Differential test: shipped QuickJS calculator vs the committed corpus (also in ./ci.sh test)
./ci.sh test    # stage "H&S differential damage oracle"

# Regenerate the corpus (developer only)
HNS_UPSTREAM_DIR=/path/to/pokehns-expansion \
  python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py regenerate

# Prove regeneration is deterministic and order-independent, without writing anything
HNS_UPSTREAM_DIR=/path/to/pokehns-expansion \
  python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py verify --order reversed

# Inspect the generated battle tests without building
python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py emit-sources --out /tmp/ddxo
```

Requirements for `regenerate`/`verify`: a checkout of the pinned commit with a clean tracked tree
(`HNS_UPSTREAM_DIR`), the Arm GNU Toolchain 13.2.rel1 (`--toolchain-bin`, default
`~/opt/arm-gnu-toolchain-13.2.Rel1-x86_64-arm-none-eabi/bin`, or `HNS_ARM_TOOLCHAIN_BIN`), a host
C/C++ compiler, `make`, `patch` and libpng (the pinned tree's own `tools/` are built). The scratch
tree defaults to `~/.cache/dualdex/hns-damage-oracle` (`--work-dir`). Every `regenerate` or `verify`
run deletes that tree and re-exports the pinned commit with `git archive`; cached source is never
trusted for corpus provenance.

## Provenance and determinism

`corpus.json` records the pinned repository/commit/tree, the backend and build command, the SHA-256 of
the pinned `mgba-rom-test` binary and of each harness patch, the toolchain's `gcc --version` line and
the SHA-256 of `arm-none-eabi-gcc` and `cc1`, the generator version and the SHA-256 of the generated
test source. It contains no timestamps and no machine paths (the serialiser rejects both). The
serialisation is canonical (sorted keys, one entry per line), so `verify` can compare byte-for-byte.

`check` fails when the committed corpus is not canonical, not from the pinned commit, or stale
relative to `oracle_matrix.py` or the generated test source (so a matrix edit without regeneration
cannot pass CI).

## Existing evidence cross-check

`fixture_crossref.json` maps 33 existing fixtures to oracle scenarios and states each fixture's
16-roll evidence: the hand-derived H&S arithmetic from `native/tests/test_js_calc.c` (Gaps C1, C4a, C4b,
C4e, C4f), the official-ROM captures (Gap C4d goldens A, B, C and the indirect crit E) and upstream's
Bulbapedia vector. `check` requires the oracle to reproduce every one exactly (ROM captures: the
observed damage must be one of the oracle's rolls). Relations are labelled: `exact-operands`,
`arithmetic-equivalent` (a fixture forced a non-pinned base power; the oracle carries the same
operands with a pinned move of that power) or `decomposed` (a fixture boosted both sides with badges at
once, which a real battle cannot; each half is reproduced separately).

If the oracle ever disagrees with that evidence, **do not update either side**: treat both as suspect,
minimise the case and investigate.

## Current result and known divergences

The regenerated issue #91 corpus records the effective type and `ateBoost` for each hit. Starting
main at `6f3a50683724bb3fd539bb062e63238d36ed1e6f` had 1,521 scenarios (1,407 production-modelled,
114 engine-only). This slice adds Solar Power, Defeatist, and Fur Coat low-state stat-stage coverage,
including exact Sun suppression, integer HP threshold, final move category, Wonder Room, Mold
Breaker, and Ability Shield controls. This Group D addition contributes 16 production-modelled
and two engine-only cases. The refreshed corpus has **1,562 scenarios: 1,443
production-modelled, 119 engine-only, 1,560 exact calculator matches and two registered
divergences**. Both remaining exact-vector divergences are linked to #100 in
`known_divergences.json`:

| Issue | Surface | Scenarios | Defect |
|---|---|---:|---|
| [#100](https://github.com/Sonoran-Solutions/dualdex/issues/100) | engine-only | 2 | Doubles spread reduction misses post-Gen-III spread moves |

The Attack-stat accumulator makes `badge-pinch-overgrow-a255` exact, resolving #98's only registered
vector. The Guts Physical-category gate makes the burn- and poison-statused Psychic vectors exact,
resolving #99's two registered vectors. The Group D matrix also covers Normalize, Refrigerate,
Pixilate, Aerilate, Galvanize, and Liquid Voice, including source-set `ateBoost`, Fairy-toggle
ordering, category changes, STAB, type-chart and Group C interactions, and negative controls. The
prior Group D scenarios cover Hustle/Guts and base-power cases for Technician, Iron Fist, Strong Jaw,
Mega Launcher, Sharpness, both direct-damage Water Bubble branches, defender Heatproof, Dry Skin's
distinct Fire ×1.25 control, Steelworker, Toxic Boost, and Flare Boost. The stat-ability slices add
23 vectors for Solar Power, Defeatist, and Fur Coat, plus 18 for Transistor, Dragon's Maw, Rocky
Payload, and Orichalcum Pulse; Group D now contains 140 scenarios (132 production-modelled and 8
engine-only). Solar Power's after-turn HP loss remains outside the selected-hit damage contract.
Water Bubble burn prevention and status clearing remain separately deferred. The Toxic Boost toxic
case preserves the live toxic counter in `status1`. The Guts Special active-status control remains
engine-only because production does not need to model a Guts modifier on a Special move. The
Toxic Boost Special-plus-poison control is production-modelled: its authoritative Special category
proves the boost irrelevant, while the status gate admits the exact poison status; a production
boundary regression verifies it reaches authorized calculator execution. The defender-side Water
Bubble Fire scenario now matches the pinned engine across all 16 rolls and is production-modelled.

Each registered scenario also pins its current 16-roll QuickJS calculator output in
`known_divergences.json`. The differential test accepts only those exact wrong vectors; a new wrong
vector fails even when its scenario already has a tracking issue. It still fails on any unregistered
mismatch and on any registered scenario that has started to match. The former #97 Ghost/Dark
category divergences now match the pinned `gTypesInfo` categories.

## Coverage

| Group | Scenarios | What varies |
|---|---|---|
| `chart-mono-*` | 324 | every attacking type x every mono defending type (full H&S chart incl. the 8 immunities), physical/special alternating |
| `chart-dual-*` | 288 | every attacking type x 16 dual-typed defenders, STAB attacker |
| `arith-*`, `min-*` | 182 | level x base power x Attack x Defense rounding grid; floor-to-1 after resistances, crit, burn, Reflect |
| `crit-*`, `stages-*` | 119 | crit x attacker/defender stages (ignored drops/boosts); stages -6..+6 both categories |
| `burn-*`, `weather-*`, `screen-*` | 71 | burn (physical/special/crit/Reflect/stages/Rain), Rain/Sun x Fire/Water/other x crit, Reflect/Light Screen x category x crit |
| `pinch-*` | 76 | Overgrow/Blaze/Torrent/Swarm at HP floor(max/3) and +1 for four max-HP values, off-type, crit |
| `wise-glasses-*`, `badge-*` | 53 | BP rounding, physical/defender negative controls, type-based crossover; badges 1/3/6/7 both sides, pinch+badge modifier accumulation |
| `fairy-*`, `style-*` | 66 | Fairy on/off typings and move retypes, immunity on/off, type-based categories |
| `group-c-*` | 43 | source-backed move/ability immunity causes, including attacker/defender and bypass controls |
| `group-d-*` | 86 | Hustle/Guts attack-stage, base-power, move-type rewrite, Punk Rock, and Steely Spirit cases with threshold, category, move-flag, type, role, status, and composition controls |
| `engine-*`, `doubles-*` | 103 | engine-only: Thick Fat, Guts, Huge/Pure Power, 17 type-boost items, Doubles single-target/spread/partner-fainted/screens/Rain |
| `final-*` | 60 | Adaptability STAB rewrites/Fairy toggle; final ability thresholds, roles, 0.5/0.25/2/4 effectiveness, crit, HP/category, immunity, Mold Breaker/Ability Shield, speed order, rounding, and damage-floor controls |
| `xref-*` | 34 | existing fixture reproductions |
