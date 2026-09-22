# Vanilla FireRed / Emerald calculator evidence (issue #9)

This document is the evidence record for the **exact vanilla FireRed and Emerald** half of
[issue #9](https://github.com/Sonoran-Solutions/dualdex/issues/9): the exact supported builds, the
independent Generation III oracle, the golden damage matrix, the production trust/boundary path,
and the precise scope of the `VERIFIED` claim.

It is deliberately separate from
[HNS_2_0_5_CALCULATOR_CAPABILITY.md](HNS_2_0_5_CALCULATOR_CAPABILITY.md), which owns the Heart &
Soul 2.0.5 capability matrix; that document now links here for the shared vanilla targets.

---

## 1. Exact supported builds (identity evidence)

The bundled profiles `app/src/main/assets/profiles/vanilla_firered.json` and
`vanilla_emerald.json` are the authority. A game is only "exactly supported" when the running ROM's
SHA-256 is one of these values:

| Profile | Exact SHA-256 | Kind | Authority |
|---|---|---|---|
| `vanilla_firered` | `3d0c79f1627022e18765766f6cb5ea067f6b5bf7dca115552189ad65a5c3a8ac` | FireRed (USA, Europe), Rev 0 | No-Intro record 1616; `sha1: 41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc` is the build `pret/pokefirered` names as `pokefirered.gba` |
| `vanilla_firered` | `729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059` | FireRed (USA, Europe), Rev 1 | No-Intro record 1672; `sha1: dd5945db9b930750cb39d00c84da8571feebf417` is `pokefirered_rev1.gba` |
| `vanilla_emerald` | `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af` | Emerald (USA, Europe) | No-Intro record 1961; `sha1: f3ae088181bf583e55daf962a92bb46f4f1d07b7` is `pokeemerald.gba` |

Provenance for the revisions is **not** guessed:

* `pret/pokefirered` `README.md` lists the SHA-1 of the two English FireRed revisions it builds and
  decompiles: `41cb23d8…` (Rev 0) and `dd5945db…` (Rev 1). `pret/pokeemerald` `README.md` lists
  `f3ae0881…` for Emerald.
* The No-Intro DAT-o-MATIC dump records for game numbers [1616](https://datomatic.no-intro.org/index.php?page=show_record&s=23&n=1616)
  (FireRed Rev 0), [1672](https://datomatic.no-intro.org/index.php?page=show_record&s=23&n=1672)
  (FireRed Rev 1) and [1961](https://datomatic.no-intro.org/index.php?page=show_record&s=23&n=1961)
  (Emerald) publish the genuine **SHA-256** for exactly those SHA-1 dumps.
* A locally supplied FireRed (USA, Europe) dump reproduces both `sha1: 41cb23d8…` and
  `sha256: 3d0c79f1…`; a locally supplied Emerald dump reproduces `sha1: f3ae0881…` and
  `sha256: a9dec84d…`, so the SHA-1↔SHA-256 correspondence is observed directly, not inferred.

### 1.1 Defect corrected in this slice

The two profiles previously carried **non-genuine SHA-256 values** that had been transcribed from
the public SHA-1 digests:

| Profile | Old value | Genuine value |
|---|---|---|
| FireRed Rev 0 | `41cb23d8dccc8ebd7fe649618a31316863b305243f166c0f941a6b9670f958a3` | `3d0c79f1627022e18765766f6cb5ea067f6b5bf7dca115552189ad65a5c3a8ac` |
| FireRed Rev 1 | `dd5945db94be8075841e2d634304a24c9600a9fa9e9282367d5cf20d23c52e46` | `729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059` |
| Emerald | `f3ae088681bfbfed0314f85243d1a2d376236f18e938ec9f270a834fb22c5409f` | `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af` |

Each old value **shares its leading hex digits with the pret SHA-1 of the same ROM** (FireRed Rev 0:
the first 64 bits; Rev 1: the first 32 bits; Emerald: the first 28 bits) and differs in the tail.
Two independent cryptographic digests of the same file cannot share a 64-bit prefix by chance, so
the old strings were demonstrably not SHA-256 of any ROM. Because the production detector
(`RomHackDetector`) computes a real SHA-256, `RuntimeRomTrust.exactRuntimeVerified` could **never**
be true for a genuine FireRed/Emerald dump, which silently made the advertised `VERIFIED` state
unreachable for the vanilla targets. The correction replaces each string with the No-Intro
authenticated SHA-256 of the exact dump it was meant to identify; the accepted set is unchanged
(same two FireRed revisions, same Emerald revision).

`tools/calc-goldens/verify_goldens.py` fails closed if the bundled profile hashes ever drift from
the values recorded in the golden provenance.

### 1.2 Do the two FireRed revisions share damage behaviour?

Yes, for this capability. FireRed Rev 0 and Rev 1 are the same Generation III engine; the
decompilation at `pret/pokefirered` handles both. The arithmetic the calculator reproduces
(`CalculateBaseDamage`, `gStatStageRatios`, critical hits, STAB, type effectiveness and
`ApplyRandomDmgMultiplier`) is textually the same structure in `src/pokemon.c:2385+` and
`src/battle_script_commands.c:1274/1558` for the supported revision. Both accepted hashes therefore
route to the **same** `VANILLA_GEN3` capability row, and
`CalcVanillaGoldenBoundaryTest` asserts both produce an identical normalised production request for
the same fixture. No revision-specific damage difference is modelled, because none is evidenced.

---

## 2. What `VANILLA_GEN3` claims

For an exact-trusted ROM, `CalcCapabilityPolicy` resolves `CalcRuleset.VANILLA_GEN3` with
`mechanicsGeneration = 3`, `contentSource = "gen3_vanilla"` and `ceiling = CalcSupport.VERIFIED`.
A request becomes `VERIFIED` only when it is trusted (exact SHA-256) **and** every input is inside
the modelled surface:

* species and move names resolve in the pinned `gen3_vanilla` pack;
* ability (if supplied) is one the ADV pipeline applies (`GEN3_MODELLED_ABILITIES`);
* held item (if supplied) is one the ADV pipeline applies (`GEN3_MODELLED_ITEM_NAMES`);
* status is one of `brn`, `par`, `slp`, `frz`, `psn`, `tox`;
* weather is one of `Sun`, `Rain`, `Sand`, `Hail` (canonical spelling); terrain is refused;
* stat stages are within `-6..+6`; IVs `0..31`; EVs `0..255`; level `1..100`;
* the request generation is 3 (a mismatch is disclosed and downgrades to `ESTIMATED`).

The claim is exactly: **the shipped `@smogon/calc` 0.11.0 Generation III pipeline reproduces the
exact ROM's ordinary damage arithmetic for those inputs.** It is not a claim to know the live
battle state (see §6).

### 2.1 Scope caveats that are disclosed, not silently assumed

* **Badge boost.** The Generation III engine multiplies attack/defense by 1.1 when the player owns
  the relevant badge (`ShouldGetStatBadgeBoost`, `pret/pokeemerald src/pokemon.c:3161-3168`;
  `pret/pokefirered src/pokemon.c:2440-2447`). `@smogon/calc`'s ADV pipeline does not model it, and
  the request shape cannot express it, so every verified vanilla headline carries
  `CalcResultPresentation.BADGE_BOOST_NOTE` ("unbadged; badge boost is not applied"). The golden
  matrix is asserted in that unbadged state.
* **Omitted ability is the engine's species default.** `@smogon/calc` substitutes the species'
  first Gen III ability when no ability is supplied (for example Machamp's default is Guts). A
  fixture whose result depends on the ability therefore states it explicitly (see the burn+Guts
  fixture). A manual request that omits the ability is reported `VERIFIED` only for the pipeline's
  documented default-ability semantics; it is not evidence about an arbitrary in-game Machamp.
* **Live reads.** A live-read participant missing a damage-relevant field is refused
  (`LIVE_PARTICIPANT_STATE_UNKNOWN`); a live read on a non-exact ROM is refused
  (`LIVE_INPUTS_NOT_VERIFIED`). These paths are unchanged by this slice.

---

## 3. Independent oracle

`tools/calc-goldens/gen3_reference.py` is a small, transparent Generation III reference
implementation. It does **not** import or execute `calc_bundle.js` / `@smogon/calc`; the calculator
under test is not its own oracle.

Pinned sources:

| Project | Commit | Used for |
|---|---|---|
| `pret/pokeemerald` | `5eff78649e7170a877b961ef0b3da13b81a16038` | `CalculateBaseDamage`, `gStatStageRatios`, `ApplyRandomDmgMultiplier`, crit / STAB / type order |
| `pret/pokefirered` | `c75f352304d529f6ba92d4f74b9cf8b5c3810788` | the same Generation III arithmetic for FireRed Rev 0/1 |

Key facts the oracle encodes, with source anchors:

* base damage order: `damage = floor(floor((floor(2·L/5)+2)·A·BP / D) / 50)`
  (`src/pokemon.c` `CalculateBaseDamage`);
* stat stages: `gStatStageRatios` (`src/pokemon.c:1868` Emerald, `:1442` FireRed) —
  `+n → (10+n)/10`, `-n → 10/(10+n)`, floored, with a critical hit ignoring the attacker's negative
  stages and the defender's positive stages;
* burn `÷2` (unless Guts), screens `÷2` singles / `×2/3` doubles, spread `÷2`, weather `×1.5`/`÷2`,
  then `+2` and the critical `×2`;
* STAB `×15/10` and per-defending-type flooring (`Cmd_typecalc`);
* 16 rolls `floor(damage·r/100)` for `r = 85..100`, minimum 1
  (`ApplyRandomDmgMultiplier`, `src/battle_script_commands.c:1639` Emerald, `:1558` FireRed);
* the Generation III **type-based** physical/special partition, so fixture B does not assume a
  modern per-move category.

`tools/calc-goldens/verify_goldens.py` re-derives every committed expected vector from this oracle
and fails closed on any drift. It also fails if the bundled profile hashes differ from the golden
provenance.

---

## 4. Golden damage matrix

`tools/calc-goldens/vanilla_gen3_goldens.json` is one structured fixture format shared by FireRed
and Emerald. Every fixture carries: game(s), exact profile SHA-256s, pinned oracle commits, the
input state, the exact engine request, the expected 16-roll vector, the covered branch and the
evidence level.

| Fixture | Branch | Expected rolls |
|---|---|---|
| `vg3_a_neutral_physical` | A neutral physical, no STAB, no field | 51-60 |
| `vg3_b_neutral_special_type_split` | B neutral special, Gen III type-based category (Dark) | 21-25 |
| `vg3_c_stab_and_type_effectiveness` | C STAB ×1.5 then ×2 type effectiveness | 102-120 |
| `vg3_d_critical_hit` | D critical ×2 | 102-120 |
| `vg3_d_crit_ignores_negative_attack_and_positive_defense` | D crit ignores unfavourable stages | 102-120 |
| `vg3_e_screen_reflect_singles` | E Reflect ×1/2 (Singles) | 28-33 |
| `vg3_e_screen_light_screen_singles` | E Light Screen ×1/2 (Singles) | 16-19 |
| `vg3_e_doubles_spread_format_sensitive` | E format-sensitive Doubles spread ×1/2 | 26-31 |
| `vg3_f_weather_rain_halves_fire` | F Rain ×1/2 on Fire | 12-15 |
| `vg3_f_weather_sun_boosts_fire` | F Sun ×1.5 on Fire | 35-42 |
| `vg3_g_burn_guts_boosts_attack` | G burn + Guts ×1.5, no burn halving | 75-89 |
| `vg3_h_stat_stage_attack_plus_two` | H +2 Attack stage | 100-118 |

Fixture inputs use Machamp (Hardy L50, 31 IV / 0 EV) versus Snorlax (Hardy L50, 31 IV / 0 EV), so
the stat arithmetic is explicit and reproducible. Every fixture is asserted against **both**
`vanilla_firered` and `vanilla_emerald` at the production boundary.

Evidence level per fixture:

* **SOURCE VERIFIED** — the arithmetic and constants come from the pinned pret sources above.
* **HOST VERIFIED** — the shipped `calc_bundle.js` engine produces the exact vector:
  `native/tests/test_js_calc.c :: run_vanilla_golden_matrix()` reads the same fixture file, executes
  the exact request, and asserts all 16 rolls plus move type/category/power.
* **RUNTIME VERIFIED** — not obtained in this slice; see §5.

---

## 5. Direct exact-ROM runtime evidence

Not obtained. The exact FireRed Rev 0 and Emerald dumps are present in the development environment
and now hash-match the corrected profiles, but there is **no vanilla runtime battle harness**:
`tools/hns-runtime-probe/` is entirely Heart & Soul-specific (its invariants, scripts and save
creation target the H&S map/battle layout), and no vanilla battery save or battle-entry scenario
exists. Building a vanilla equivalent is a separate, bounded developer-tool slice; it was not
faked or approximated here.

This is called out for senior review because the profiles advertise `VERIFIED`: the existing
`VERIFIED` claim rests on SOURCE + HOST evidence plus the production trust gate, not on an
in-emulator damage observation. The `native/tests/test_js_calc.c` host suite is the same engine the
APK ships, and the production boundary test proves the exact request it executes, but that is not
the same as observing a hit on the exact ROM.

Next bounded slice (if runtime evidence is required): a `tools/vanilla-runtime-probe/` modelled on
the H&S probe that (a) verifies the supplied ROM's SHA-256 against the bundled profile, (b) drives a
deterministic wild battle on a locally produced battery save, (c) reads `gBattleMons[].hp` through
the production vanilla reader offsets, and (d) asserts the observed damage is one of the golden
rolls with a PASS/FAIL exit.

---

## 6. Trust and boundary behaviour

The production path is unchanged except for the corrected hashes:

* `RomHackDetector.detectCompatibility` — only a real exact SHA-256 match to a verified, layout-
  verified profile yields `VERIFIED`; filename, header and base-game heuristics yield
  `RECOGNIZED_UNVERIFIED` and never grant authorization.
* `RuntimeRomTrust.exactRuntimeVerified` — requires an exact match method, a runtime hash equal to
  the detected hash, and membership in the profile's own hash list.
* `CalcCapabilityPolicy.evaluate` — caps at `ESTIMATED` with `ROM_NOT_EXACT_VERIFIED` unless
  `exactRuntimeVerified`; `VERIFIED` is reached only with the `VANILLA_GEN3` ceiling and no
  limitations.
* `CalcRequestBoundary` — the only path from application state to an engine request; the authorized
  request is the one the verdict was computed from.

`CalcVanillaGoldenBoundaryTest` drives this real boundary (not the policy directly) and asserts, for
both exact profiles and both FireRed revisions: `Ready` → `VERIFIED` → no limitations → the
production serialisation is byte-identical to the golden request. It also covers near-miss hashes,
header-only recognition, wrong running bytes, and unsupported ability/item/status/weather.

---

## 7. CI coverage

`./ci.sh test` now runs, in order:

1. `calc_test` — the shipped engine executes the golden matrix
   (`run_vanilla_golden_matrix`), asserting every roll;
2. `calc_goldens_check` — `verify_goldens.py` re-derives every expected roll from the independent
   oracle and checks that the bundled profile hashes match the golden provenance;
3. the Kotlin suite — `CalcVanillaGoldenBoundaryTest` drives the production boundary for both
   profiles and compares the serialisation with the executed request.

The fixture file is the single source of truth for all three, so a profile edit, an oracle change or
a bundle change that alters a golden fails the canonical gate.

---

## 8. Remaining limitations

* Direct exact-ROM runtime damage goldens are not obtained (§5). Both local dumps cover FireRed
  Rev 0 and Emerald; no FireRed Rev 1 dump is present, so even a future runtime harness would leave
  Rev 1 runtime-untested.
* The profile's `memoryLayoutVerified` flag and party offsets cover the accepted revision set as
  declared by the profile; this slice re-derives the **damage** arithmetic per revision but does not
  re-derive the memory layout for FireRed Rev 1.
* Badge boosts are unmodelled and disclosed on every verified headline (§2.1).
* Vanilla `Doubles` shares the verified ruleset and is covered by one format-sensitive spread
  fixture; it is not a claim about live Doubles battle state.
* The matrix covers the ordinary single-hit damage path; exotic move mechanics (multi-hit, fixed
  damage, HP-scaled power, etc.) remain outside the vanilla `VERIFIED` surface and are not asserted
  here.
* A manual request that omits a damage-relevant ability/item/status field relies on the pipeline's
  documented default semantics, not on an observation of the running game (§2.1).
