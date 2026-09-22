# Vanilla FireRed / Emerald calculator evidence (issue #9)

This document is the evidence record for the **exact vanilla FireRed and Emerald** half of
[issue #9](https://github.com/Sonoran-Solutions/dualdex/issues/9): the exact supported builds, the
independent Generation III oracle, the golden damage matrix, the production trust/boundary path,
direct controlled retail-ROM observations, and the precise scope of the `VERIFIED` claim.

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

What that establishes is deliberately narrower than "these strings are not SHA-256 of any ROM":

* the old values are **not the SHA-256 of the intended supported dumps**, which is the claim that
  matters, and the genuine No-Intro digests replace them;
* the shared prefixes are **extremely strong evidence that the old strings were malformed or
  transcribed values** rather than independent digests of some ROM.

They are not a proof that no ROM anywhere has one of them as its SHA-256. Two distinct hash
functions can agree on a 64-bit prefix by coincidence, and no finite set of digests can establish
that a 256-bit string is "not the SHA-256 of any ROM" — only that it is not the SHA-256 of any file
that has been hashed for comparison. The defect this slice fixes is the one the evidence supports:
`RomHackDetector` computes a real SHA-256, so with those values
`RuntimeRomTrust.exactRuntimeVerified` could never be true for the genuine FireRed/Emerald dumps,
which silently made the advertised `VERIFIED` state unreachable for the vanilla targets. The
correction replaces each string with the No-Intro authenticated SHA-256 of the exact dump it was
meant to identify; the accepted set is unchanged (same two FireRed revisions, same Emerald
revision).

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

Damage arithmetic is not the only thing the shared profile authorizes, and §7 is the layout audit
that closes that gap.

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
* the request generation is 3 (a mismatch is disclosed and downgrades to `ESTIMATED`);
* **not a Doubles battle with Reflect or Light Screen active**, and **not a Doubles request whose
  move the shipped pipeline reduces as a spread move** — see §2.2.

The claim is exactly: **the shipped `@smogon/calc` 0.11.0 Generation III pipeline reproduces the
exact ROM's ordinary damage arithmetic for those inputs.** It is not a claim to know the live
battle state (see §8).

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

### 2.2 Doubles screens and Doubles spread moves are refused, not published (senior-review P1 corrections)

The pinned engines express a Doubles Reflect / Light Screen as an **integer-division-first**
operation on the pre-roll damage, in `CalculateBaseDamage`:

```c
/* pret/pokefirered src/pokemon.c:2547 CalculateBaseDamage (Reflect branch; Light Screen
   at :2598); identical text in pret/pokeemerald src/pokemon.c:3270 and :3321 */
if ((sideStatus & SIDE_STATUS_REFLECT) && gCritMultiplier == 1)
{
    if ((gBattleTypeFlags & BATTLE_TYPE_DOUBLE)
     && CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2)
        damage = 2 * (damage / 3);
    else
        damage /= 2;
}
```

Three separate facts follow, and each one is enough to keep this shape off the `VERIFIED` surface:

1. **Operation order.** `2 * (damage / 3)` is not `floor(damage * 2 / 3)`: the two floor
   separately. For the Strength fixture (pre-screen value 62) the cartridge computes
   `2 * floor(62/3) = 40`, then `+2 = 42`, giving rolls **35-42**; writing the same intent as a
   single fraction gives `floor(62 * 2/3) = 41`, then `+2 = 43`, giving **36-43**. Fourteen of the
   sixteen rolls differ, each by one.
2. **Target presence.** The cartridge takes the `2 * (damage / 3)` branch only while
   `CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2` and otherwise halves. The request shape
   carries no target-presence operand, so no request can select the correct branch even in
   principle.
3. **Where the screen is applied.** The cartridge applies it to the pre-roll value; the shipped
   `@smogon/calc` 0.11.0 ADV pipeline applies `floor(rolled * 2/3)` inside its per-roll loop.

Because `VANILLA_GEN3` advertises `CalcSupport.VERIFIED`, the exact-trusted
`gameType = Doubles` + `defenderSide.isReflect`/`isLightScreen` shape is now **refused** with
`CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED` rather than computed. The gate is scoped to
that shape only:

* Singles Reflect and Light Screen stay `VERIFIED` (they are two committed golden fixtures);
* a Doubles request **without** a screen stays `VERIFIED` (the committed Doubles spread fixture);
* `CalcVanillaGoldenBoundaryTest` asserts all three halves: the refusal, the Singles negative
  control, and the screenless-Doubles control.

#### 2.2.1 The spread reduction has the same hole, and the same treatment

The identical condition governs the **spread** reduction, in the same function
(`pret/pokefirered src/pokemon.c:2553` physical and `:2604` special; `pret/pokeemerald
src/pokemon.c:3276` and `:3327`):

```c
if ((gBattleTypeFlags & BATTLE_TYPE_DOUBLE)
 && gBattleMoves[move].target == MOVE_TARGET_BOTH
 && CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2)
    damage /= 2;
```

Two consequences, and both were reachable as `VERIFIED` before this correction:

1. **Target presence again.** Rock Slide against a single remaining foe is **not** reduced: the
   cartridge vector is the Singles one (58 → `+2` = 60), while the shipped pipeline halves whenever
   the format label says Doubles (29 → `+2` = 31). The `vg3_e_doubles_spread_format_sensitive`
   fixture asserted the pipeline's reduced vector as `VERIFIED`, which is the same class of error
   the screen fix closed.
2. **The move set is narrower than "any spread move".** The cartridge also requires
   `MOVE_TARGET_BOTH`, which is the *opposing* pair only. `MOVE_TARGET_FOES_AND_ALLY` moves
   (Earthquake, Explosion, Magnitude, Selfdestruct, Teeter Dance) are spread-capable in the games
   but the shipped pipeline does not reduce them, so they are already correct and must not be
   refused.

So the Doubles spread shape now fails closed for `VANILLA_GEN3` with
`CalcLimitation.VANILLA_DOUBLES_SPREAD_NOT_MODELLED`, and the set of moves it applies to is derived
from the pinned source rather than guessed: `Gen3DoublesSpreadMoves` lists the 17 damaging
`MOVE_TARGET_BOTH` moves, and
`native/tests/test_js_calc.c :: check_gen3_spread_move_table_matches_engine()` proves the table
against the engine itself over the **whole** Generation III move list (354 moves, accounted for
entry by entry) by recovering each move's pre-roll damage in Singles and in Doubles and requiring
the engine to reduce exactly those 17.

The previous spread golden is therefore no longer in the verified matrix. Its place is taken by
`vg3_e_doubles_single_target_unreduced` — the only Doubles geometry left on the surface — and the
two target-presence states of the spread branch are recorded as source-exact references (§2.2.2).
The pipeline's own H&S Doubles behaviour is still pinned at the engine level by
`gap_c4b_doubles_spread_two_targets_halved`, `gap_c4b_doubles_spread_one_target_not_halved` and
`gap_c4b_doubles_spread_missing_target_count_refused`, which exercise the engine's explicit
`field.targetCount` contract directly.

#### 2.2.2 The cartridge arithmetic is recorded, not published

The cartridge's own arithmetic for both branches is recorded, as evidence rather than as an engine
golden, in the `cartridgeReferences` block of `vanilla_gen3_goldens.json`. It is derived by
`gen3_reference.doubles_cartridge_rolls(request, both_defenders_present)`, which encodes the integer
operation order *and* the presence operand for the screen branch and the spread branch together:

| Reference | Branch | Both defenders present | Vector |
|---|---|---|---|
| `cartridge_doubles_reflect_both_defenders_present` | screen | yes — `2 * (damage / 3)` | 35-42 |
| `cartridge_doubles_reflect_one_defender_present` | screen | no — `damage / 2` | 28-33 |
| `cartridge_doubles_spread_both_defenders_present` | spread | yes — `damage /= 2` | 26-31 |
| `cartridge_doubles_spread_one_defender_present` | spread | no — no reduction | 51-60 |

`gen3_reference.calculate()` **raises** for both Doubles shapes instead of guessing which arithmetic
a caller means, and the verifier fails if any branch's two presence states stop differing (which
would mean the gate no longer rests on an observable operand) or if a branch stops matching the
source-derived operation.

`native/tests/test_js_calc.c :: check_vanilla_doubles_cartridge_divergence()` then scores the engine
against each reference in its own right: three of the four states must diverge (the both-defenders
spread state legitimately agrees, because both the cartridge and the pipeline halve there), each
committed pair must differ, and any agreement in a state that must diverge fails the suite as the
signal that the gate — not the fixture — should be revisited.

### 2.3 "Cartridge-exact" means values and constants, not the whole operation order

`VANILLA_GEN3` runs `@smogon/calc` 0.11.0's ADV pipeline, so where this document calls a branch
"cartridge-exact" it means the **values and constants** the pinned source specifies for that branch,
on the surface §2 lists. It is not a claim that every intermediate step is performed in the
cartridge's order. The known order differences are:

| Step | Pinned cartridge | Shipped pipeline | On the `VERIFIED` surface? |
|---|---|---|---|
| Reflect / Light Screen | `damage / 2`, or `2 * (damage / 3)` while both defenders are present, applied to the **pre-roll** value inside `CalculateBaseDamage` | `floor(rolled / 2)` or `floor(rolled * 2/3)` inside the per-roll loop | Singles screens: yes, asserted against committed vectors that encode the pipeline's order. Doubles: **no** — refused (§2.2) |
| `+2` | inside `CalculateBaseDamage`, before crit / STAB / type | same placement | yes |
| Critical `×2` | `Cmd_damagecalc`, immediately after `CalculateBaseDamage`, before STAB / type | same placement | yes |
| STAB and type | `Cmd_typecalc`, after crit | after crit | yes |
| 85-100 roll | `ApplyRandomDmgMultiplier`, applied **after** STAB and type | after STAB and type | yes |
| Attack form (burn, Thick Fat, stages) | inside `CalculateBaseDamage`, before the `+2` | same placement | yes |

The Singles screen row is the one to keep in mind when reading the golden vectors: those two fixtures
are exact, reproducible vectors for the shipped pipeline's order, which is why they carry HOST
VERIFIED evidence rather than a per-step claim about the cartridge. Re-implementing the cartridge's
exact screen order in the bundle is a larger change than this evidence slice, and the corrected
hashes do not imply it.

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
* burn `÷2` (unless Guts), Singles screens `÷2`, spread `÷2`, weather `×1.5`/`÷2`, then `+2` and
  the critical `×2`;
* STAB `×15/10` and per-defending-type flooring (`Cmd_typecalc`);
* 16 rolls `floor(damage·r/100)` for `r = 85..100`, minimum 1
  (`ApplyRandomDmgMultiplier`, `src/battle_script_commands.c:1639` Emerald, `:1558` FireRed);
* the Generation III **type-based** physical/special partition, so fixture B does not assume a
  modern per-move category.

Two things the oracle deliberately refuses rather than guesses:

* **Doubles Reflect / Light Screen.** `calculate()` raises for that shape, because the cartridge's
  `2 * (damage / 3)` and the pipeline's `floor(rolled * 2/3)` are different arithmetic (§2.2). The
  cartridge branch is available explicitly through
  `gen3_reference.doubles_cartridge_rolls(request, both_defenders_present)`, which encodes the
  integer operation order *and* the target-presence condition, and is what the committed
  `cartridgeReferences` vectors are derived from.
* **Non-neutral natures.** None of the committed fixtures use one, and the oracle does not implement
  the `×1.1`/`×0.9` stat modifiers, so a request naming Adamant or Modest **raises** instead of
  silently computing the neutral value. A future fixture cannot exceed the oracle without that gap
  being closed deliberately.

`tools/calc-goldens/verify_goldens.py` re-derives every committed expected vector from this oracle
and fails closed on any drift. It also fails if the bundled profile hashes differ from the golden
provenance, if a `cartridgeReferences` vector drifts from the source-exact derivation, or if the two
target-presence reference vectors ever become identical (which would mean the branch is no longer
observable).

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
| `vg3_e_doubles_single_target_unreduced` | E Doubles single-target move is not reduced | 51-60 |
| — (refused, not a golden) | E Doubles **with** Reflect / Light Screen | not computed: see §2.2 |
| — (refused, not a golden) | E Doubles **spread** move (Rock Slide and the other 16) | not computed: see §2.2.1 |
| `vg3_f_weather_rain_halves_fire` | F Rain ×1/2 on Fire | 12-15 |
| `vg3_f_weather_sun_boosts_fire` | F Sun ×1.5 on Fire | 35-42 |
| `vg3_g_burn_guts_boosts_attack` | G burn + Guts ×1.5, no burn halving | 75-89 |
| `vg3_h_stat_stage_attack_plus_two` | H +2 Attack stage | 100-118 |

**The Doubles row is the only Doubles surface on the matrix, and the gate is what keeps it that
way:** it uses no screen, so it stays on the verified surface, while every Doubles request *with* a
screen is refused (§2.2). Any future Doubles fixture must state which of the two screen arithmetics
it means and may only be added to the engine matrix if the engine reproduces that one.

Fixture inputs use Machamp (Hardy L50, 31 IV / 0 EV) versus Snorlax (Hardy L50, 31 IV / 0 EV), so
the stat arithmetic is explicit and reproducible. Every fixture is asserted against **both**
`vanilla_firered` and `vanilla_emerald` at the production boundary. All twelve use a neutral nature,
which is exactly the set the oracle implements (§3).

Evidence level per fixture:

* **SOURCE VERIFIED** — the arithmetic and constants come from the pinned pret sources above.
* **HOST VERIFIED** — the shipped `calc_bundle.js` engine produces the exact vector:
  `native/tests/test_js_calc.c :: run_vanilla_golden_matrix()` reads the same fixture file, executes
  the exact request, and asserts all 16 rolls plus move type/category/power.
* **RUNTIME OBSERVED (controlled operands)** — fixtures A and B now have direct retail-ROM
  HP-delta observations for FireRed Rev 0 and Emerald (§5). The other fixtures and FireRed Rev 1
  retain SOURCE + HOST evidence; no full-vector runtime sweep or lifecycle promotion is claimed.
* The committed `cartridgeReferences` block carries the same SOURCE VERIFIED evidence level but is
  deliberately **outside** the engine matrix: it records what the cartridge does for the shape
  production refuses (§2.2).

---

## 5. Direct exact-ROM runtime evidence

Obtained on 2026-09-22 with the developer-only
[`tools/vanilla-runtime-probe/`](../tools/vanilla-runtime-probe/README.md), starting from merged
PR #70 / `origin/main` **`aeace0300355895279b016f3bf3c9824183e7e5a`**. Both legally available local
inputs were SHA-256 checked against the bundled exact profiles **before** initializing the core.
No ROM was downloaded or committed. FireRed Rev 1 has no legal local dump and remains
**runtime-unobserved**; its source/host fixtures remain in place.

This is a **controlled RAM-operand experiment in a real retail-ROM battle**. Fixed controller
movies start with blank save RAM and reach FireRed's first rival battle or Emerald's Birch rescue.
The probe verifies the exact battle-menu framebuffer hash and matches both original battle
records against the production party decrypt/checksum reader. It then stages the existing
Machamp/Snorlax fixture in the two BattlePokemon records. It does not patch the cartridge or
write damage, RNG, critical, weather, badge, screen, callback or lifecycle variables. Once staging
ends, writes are sealed. Ordinary input selects the move; the retail engine computes damage and
updates HP. Thus this is not an organic progression/party-legality demonstration.

Both sides are Hardy level 50, all 31 IV / 0 EV intent, neutral stages, no held item or status.
Machamp has Atk/Def/Speed/SpA/SpD **150/100/75/85/105**, HP **165**, Fighting typing and inactive
Guts; Snorlax has **130/85/50/85/130**, HP **235**, Normal typing and Immunity. These are the
committed requests' default Gen III abilities. The fresh opening scene supplies unbadged Singles,
clear weather, no screens and no prior-turn effects. The defender has Splash; the first player
attack lands before it acts. These setup-derived conditions are not a new general live-state
reader. The exact staged state and original party checkpoint are in each JSON record.

| Build / fixture | Frame | Observed HP | Damage | Committed vector | Result / record |
|---|---:|---|---:|---|---|
| FireRed Rev 0 / A Rock Slide | 31828 | 235 → 176 | 59 | 51,51,52,52,53,54,54,55,55,56,57,57,58,58,59,60 | [PASS](../tools/vanilla-runtime-probe/evidence/firered-physical.json) |
| FireRed Rev 0 / B Crunch | 31828 | 235 → 211 | 24 | 21,21,21,22,22,22,22,23,23,23,23,24,24,24,24,25 | [PASS](../tools/vanilla-runtime-probe/evidence/firered-special.json) |
| Emerald / A Rock Slide | 45987 | 235 → 177 | 58 | same A vector | [PASS](../tools/vanilla-runtime-probe/evidence/emerald-physical.json) |
| Emerald / B Crunch | 45976 | 235 → 214 | 21 | same B vector | [PASS](../tools/vanilla-runtime-probe/evidence/emerald-special.json) |

The ROM SHA values are exactly §1's Rev 0 and Emerald hashes. The recorded mGBA core is
`e31759b24e7a4e3899285ff720d7b573ac328ae7`, binary SHA-256
`fc7394f718d213131d5ec9002ddea6c72a9cd2094e0a2993c3ea3c9c964f203e`.
Each JSON pins the core, probe, input movie and unchanged golden-file hashes. Each case was
replayed in a second fresh process and produced byte-identical JSON. Reproduction commands and
framebuffer checkpoints are in the tool README; no private battery save is needed.

The verdict uses the **first** defender HP transition, requires a non-fainting positive delta,
requires that delta to equal the engine's damage word, and checks move ID, attacker=0, target=1,
crit multiplier=1 and unchanged relevant operands. It checks membership in the committed vector,
never a vector generated from the observed damage. Before emulation, the independent oracle must
also reproduce the entire committed vector. No retries discard an inconvenient roll. Crunch's
Dark typing exercises Gen III's special category despite Machamp's much higher Attack.

**Failure evidence.** The ROM-free suite tests wrong identity, removal from the profile hash list,
a wrong expected vector, a changed golden roll even when the actual hit remains in range, an oracle
shifted by +1, wrong request operands, corrupted HP/damage/attribution/crit/move, a no-hit timeout,
unknown script commands and writes after sealing. It validates all four committed records too.
An actual Emerald dump supplied to the FireRed scenario with a nonexistent core returned
`FAIL ROM identity mismatch`, exit 1 before core loading, and a FAIL output. Failed runs cannot
leave a stale PASS artifact. These controls are maintained under `./ci.sh test` and `all`.

### 5.1 Runtime addresses are diagnostic evidence, not product authorization

The runtime battle records are at **FireRed `0x02023BE4`** and **Emerald `0x02024084`**. Both
were corroborated against full original combatant records parsed from the production party
addresses, then by move-attributed HP deltas. The shared 88-byte stride, HP `+0x28` and stages
`+0x18` are checked against the production configs; the remaining struct members and neighboring
globals follow the pinned upstream declarations listed in the probe README. Bounds-checked
production libretro memory reads are reused. The probe does not call a presence/lifecycle reader.

These bases differ from the shipped `0x02023F90` / `0x02024064` constants. In particular, the
previous §7.3 literal scan **overstated** what its Emerald positive result established: the ROM
references `0x02024064`, but that does not identify the symbol at that address as `gBattleMons`.
The old statement that it proved Emerald's battle base is withdrawn. No production offset is
changed here: finding these records in one opening scene is not a general lifecycle validation.

Both bundled profiles keep **`battleStateReadVerified=false`**. The legacy
`gBattleMons[0].species` presence heuristic is neither consulted nor promoted. This evidence
supports two calculator arithmetic cases per exact build only: it does not authorize battle
presence, active-slot selection, enemy-party polling, battle UI, live autofill, or arbitrary
post-battle reads. FireRed's opening tutorial suppresses critical hits; none are claimed here.
There is no physical-hardware comparison, runtime field-modifier sweep, complete 16-roll runtime
sweep, or Rev 1 observation.

---

## 7. Read-only layout audit: what the corrected hashes newly authorize

Repairing the hashes does more than make calculator trust reachable. `RuntimeRomTrust`'s
`exactRuntimeVerified` drives `mayReadLiveMemory`, which is the **universal** gate for every
profile-dependent read in the companion:

| Read | Reader offset(s) that must hold | Enabled by |
|---|---|---|
| player party | `player_party_offset`, `player_party_count_offset`, `battle_mons_offset + count` | `CompanionViewModel.pollVerifiedRomMemory` |
| enemy party | `enemy_party_offset`, `enemy_party_count_offset` | same |
| battle presence | `battle_mons_offset` (the vanilla layouts read `gBattleMons[0].species`) | same |
| active battler / active enemy | `battle_mons_offset`, `battle_mons_stat_stages_offset`, party offsets | same |
| stat stages | `battle_mons_offset + battle_mons_stat_stages_offset` / `+ battle_mons_hp_offset` | same |
| player location | `player_party_offset` (the legacy SaveBlock1 base is derived from it) plus `save_block1_pos_offset` / `location_offset` / `escape_warp_offset` | same |

Interactive paths stay closed (`battleUiVerified = false`, `interactiveControlsVerified = false`), so
this is a read-surface question, not a write-safety one. But FireRed Rev 0 and Rev 1 share **one**
profile and therefore **one** memory-layout table, so "both revisions are accepted" is only sound if
both revisions really place every one of those symbols at the same address.

### 7.1 The audit and its result

`tools/calc-goldens/audit_vanilla_layout.py` turns that into a check. It reads the ELF symbol table
of builds made from the **pinned** decompilations (`pret/pokefirered @ c75f352`, both revisions;
`pret/pokeemerald @ 5eff786`), scrapes the expected offsets out of `native/src/pokemon_reader.c` and
the bundled profiles rather than restating them, and asserts:

```
$ python3 tools/calc-goldens/audit_vanilla_layout.py \
      --firered-rev0 pokefirered.elf --firered-rev1 pokefirered_rev1.elf --emerald pokeemerald.elf
  [OK] FireRed Rev 0: party group matches the shipped offsets (player 0x02024284, enemy 0x0202402C)
  [OK] FireRed Rev 0: gPlayerParty and gEnemyParty are exactly 600 bytes apart (enemy first) ...
  [OK] FireRed Rev 0: count block 0x02024029..0x0202402A heads the party TU's EWRAM ...
  [OK] FireRed Rev 0: reader-derived SaveBlock1 base 0x0202424C (gPlayerParty == SaveBlock1+0x38) ...
  [OK] FireRed Rev 0: gBattleMons stride is 88 bytes x 4 battlers; reader hp=0x28 statStages=0x18
  [OK] FireRed Rev 1: (identical to Rev 0 for every line above)
  [OK] FireRed Rev 0 and Rev 1: 889 EWRAM+IWRAM symbols, all at identical addresses
  [OK] Emerald: party group matches the shipped offsets (player 0x020244EC, enemy 0x02024744)
  ...
```

| Requirement | Classification | Evidence |
|---|---|---|
| FireRed Rev 1 shares Rev 0's entire RAM layout | **RUNTIME VERIFIED** (build-level) | both revisions built from the pinned commit produce 889 EWRAM+IWRAM symbols at byte-identical addresses; every one of the 45043 differing symbols is a ROM address (the rev1 change shifts code/rodata), no RAM symbol moves |
| FireRed player/enemy party addresses | **RUNTIME VERIFIED** (build-level) | the pinned build resolves `gPlayerParty` at `0x02024284` and `gEnemyParty` at `0x0202402C`, exactly the profile's declared values |
| Emerald player/enemy party addresses | **RUNTIME VERIFIED** (build-level) | `0x020244EC` / `0x02024744` from the pinned build, matching the profile |
| `sizeof(struct BattlePokemon)`, `hp`, `statStages` | **SOURCE VERIFIED** | `include/pokemon.h` field offsets (`0x28`, `0x18`, 88 bytes) equal the reader's compiled values, and `gBattleMons` is 88 x 4 bytes in both games |
| SaveBlock1 base and `pos`/`location`/`escapeWarp` for the legacy location path | **SOURCE + build VERIFIED** | `struct SaveBlock1` places `playerParty` at `+0x38` (FireRed, `include/global.h:773`) and `+0x238` (Emerald); the audited `0x00`/`0x04`/`0x24` fields match, and the derived base is inside EWRAM in both builds |
| `battle_mons_offset`, the battle-lifecycle offsets, and the Battle UI offsets | **UNPROVEN by build audit**, both left unauthorized — §§5.1, 7.4 | These are linker-ordered EWRAM placements inside the section that also holds stubbed asset arrays, so a build with stubbed graphics cannot confirm them; §7.3 probes the retail images and reports per build. Their consumption is gated by `battleStateReadVerified`, not by the hash, and §§5.1/7.4 explain why neither literal occurrence nor a diagnostic battle record authorizes the block |

### 7.3 The battle-state addresses: probed against the retail images, and not assumed

§7.1's `UNPROVEN` row is not a documentation nicety. Correcting the hashes makes
`RuntimeRomTrust.exactRuntimeVerified` reachable for the genuine dumps, and that flag is the
**universal** live-memory gate: the poller then calls vanilla battle presence, which dereferences
`battle_mons_offset`, and the enemy-party, stat-stage and battler-state readers share that base.
Rev 0/Rev 1 equality proves the two revisions agree with each other; it says nothing about whether
the shared constant is the retail address.

The audit therefore probes the retail images directly, with
`--firered-rom` / `--emerald-rom`. A GBA program reaches an EWRAM global by loading a literal word
holding its address, and those literal pools are in the cartridge, so finding the configured address
as a little-endian word in the accepted dump is evidence that the retail program uses that address.
It does **not** establish which symbol it names; the runtime experiment in §5.1 demonstrates why. The
method is validated in the same run against the party addresses §7.1 already proved.

**What the negative case does and does not establish.** An address that does *not* appear as a whole
word only means this scan did **not prove** it. It is not proof that the address is wrong: a compiler
may materialise a nearby base or group address and reach the symbol with an added offset, and it may
keep the address in a table the scan does not reach. The neighbouring-reference count below is
reported as context, not as a verdict — a cluster of referenced addresses around an unproven one is
consistent both with the symbol being reached through an offset and with the configured value being
wrong, and this scan cannot tell those apart. Deciding between them needs the retail build's debug
symbols, which `pret/pokefirered` does not publish. So the tool reports UNPROVEN, changes nothing,
and a guessed replacement never enters production.

| Build | Address | Result |
|---|---|---|
| FireRed Rev 0 (retail) | `gPlayerParty` `0x02024284` (control) | referenced at 745 ROM offsets |
| FireRed Rev 0 (retail) | `gEnemyParty` `0x0202402C` (control) | referenced at 392 ROM offsets |
| FireRed Rev 0 (retail) | `battle_mons_offset` `0x02023F90` | **not proved**: no literal in the image holds it, while 51 addresses within ±0x200 of it are referenced |
| Emerald (retail) | `gPlayerParty` `0x020244EC` / `gEnemyParty` `0x02024744` (controls) | referenced at 1091 / 532 ROM offsets |
| Emerald (retail) | `battle_mons_offset` `0x02024064` | address referenced at 1147 ROM offsets, first at `0x00033214`; **symbol identity not proved**, and runtime records are at `0x02024084` (§5.1) |

The literal results differ, but neither establishes that the configured address names the live
BattlePokemon records. §5.1 supersedes the earlier Emerald 'proved' classification. Both stay
closed (§7.4).

**The authorization is therefore decoupled, which is the reviewer's stated alternative.**
`RomHackProfile.battleStateReadVerified` (and `RuntimeRomTrust.mayReadBattleState`) is a gate
*independent of the hash trust*: an exact hash establishes **which** build is running, never that a
configured address is that build's address. The bundled vanilla profiles set it to `false` and the
H&S 2.0.5 profile — whose battle globals are compiled-symbol verified and runtime cross-checked
(`docs/HNS_2_0_5_COMPATIBILITY_EVIDENCE.md` §5 rows 5-8) — sets it to `true`.

The scope of the closure is precise, and is asserted by
`RomCompatibilityTest.vanillaBattleStateReadsStayClosedWhilePartyAndLocationStayOpen`:

| Read | Proven by | Enabled for exact-trusted vanilla? |
|---|---|---|
| player party (`player_party_offset`, `player_party_count_offset`) | §7.1 build evidence + `src/pokemon.c` declaration order | yes |
| player location (SaveBlock1 base derived from `gPlayerParty`, `pos`/`location`/`escapeWarp`) | §7.1 | yes |
| battle presence (`gBattleMons[0]`) | §7.3: not proved on FireRed; Emerald would still use the legacy heuristic | **no** |
| enemy party (needs the battle lifecycle) | §7.4: no vanilla layout declares the lifecycle gate | **no** |
| active battler / player stat stages / battle UI | §7.3, §7.4 | **no** |
| live battler ability, effective types, held item | §7.3, §7.4 | **no** |

When the flag is false the poller does not invoke those readers at all, battle presence is published
as `UNKNOWN` (the answer is withheld, never defaulted to "no battle"), and no battle-derived state is
published.

### 7.4 Why Emerald stays closed too

Emerald's literal-address result never established symbol identity (§5.1). Even a proven battle
record address would be **insufficient** to enable the block:

* the whole battle-state block opens together, not just `gBattleMons`. Vanilla would still run
  `read_legacy_battle_presence()`, whose own contract records that `gBattleMons[0].species` can remain
  stale after a battle — that is a heuristic, not an authoritative lifecycle signal, which is
  precisely why the H&S path uses `gMain.inBattle`;
* Emerald's configuration does not declare any of the lifecycle offsets
  (`battlers_count_offset`, `battle_type_flags_offset`, `battle_outcome_offset`) that the authoritative
  gate needs, and `pokemon_read_enemy_party_gba()` refuses to publish the authoritative enemy-party
  count for a layout without that gate. So "enabling Emerald" would switch on the legacy presence
  heuristic while the enemy-party path still lacked the authority it requires — it would not restore
  the enemy party at all.

Vanilla battle-lifecycle support is therefore deliberately left as a **separate, bounded piece of
work**: either establish an authoritative lifecycle for Emerald (the `gMain.inBattle` route the H&S
profile already uses, with the same cross-checking), or split `battleStateReadVerified` into narrower
per-capability flags so that proven `gBattleMons`-based reads can be enabled without also enabling
the presence heuristic. Neither belongs in an evidence-repair pass, so `battleStateReadVerified` stays
`false` for both vanilla profiles at this head.

Two caveats are recorded rather than hidden:

* The builds used for the audit stub the repository's non-free graphics assets (the upstream
  `data/tilesets/**` and `graphics/**` files are not redistributable), so ROM addresses and the
  absolute placement of later EWRAM objects differ from the retail image. That does **not** affect
  the Rev 0/Rev 1 equivalence result — which is a differential claim about one build pair — and the
  party group lands on the profile's exact addresses, which is a positive reproduction of the retail
  layout for those symbols.
* Rev 1's ROM-address map differs from Rev 0's by design (the revision string), which is why the
  audit asserts RAM equality and *reports* ROM movement instead of failing on it.

**Conclusion: FireRed Rev 1 stays in the profile's `sha256Hashes`.** It shares Rev 0's RAM layout,
including every symbol the exact-trust read surface touches, so the shared memory-layout table is
correct for it and no hash needs to be withheld.

### 7.2 Defect found by the audit: the enemy party COUNT offset

The audit's strict form — "the reader's declared offset for a count must be the symbol the pinned
build resolves" — failed against the reader as it stood before this slice, and the failure is real:

```
native/src/pokemon_reader.c declares enemy_party_count_offset EWRAM+0x24028 = 0x02024028,
but the pinned build places gEnemyPartyCount at 0x0202402A
```

The linker map for `src/pokemon.o` shows why:

```
0x02024024                gBattleMonForms
0x02024028                        . = ALIGN (0x4)      <- padding, not a symbol
ewram_data 0x02024028  0x4d0 src/pokemon.o
0x02024029                gPlayerPartyCount
0x0202402a                gEnemyPartyCount
0x0202402c                gEnemyParty
0x02024284                gPlayerParty
```

`gPlayerPartyCount` and `gEnemyPartyCount` are the first two EWRAM objects of `src/pokemon.c`
(`pokefirered src/pokemon.c:59-60`, `pokeemerald src/pokemon.c:76-77`), so the enemy count is always
`player_count + 1` and never `gEnemyParty - 4`. The previous values (FireRed/LeafGreen `0x24028`,
Emerald `0x24740`) named that alignment padding byte and the byte after the enemy party array.

This was **latent, not a live misread**, because `pokemon_read_enemy_party_gba` returns before
reading any count for a layout that does not declare the full battle-lifecycle gate — which today is
every vanilla title — so no enemy party was being published from those offsets. The values are
corrected to the exact symbols (`0x2402A`, `0x244EA`) with that reasoning recorded next to the config,
because a symbol offset that points at padding is a defect regardless of whether a guard currently
hides it. The reader's `enemy_party_count_offset` now also participates in the audit's strict check,
so it cannot silently drift back.

The heuristic-layout hack configurations (`CONFIG_GHOST_GREY`, `CONFIG_RADICAL_RED`, `CONFIG_UNBOUND`)
retain `0x24028`: they are not part of this audit's pinned-build evidence, and changing offsets for
unbuilt ROM hacks would be a guess.

---

## 8. Trust and boundary behaviour

The preceding PR #70 corrected hashes and added the Doubles screen gate. Its other production
change was the enemy party **count** offset correction in
`native/src/pokemon_reader.c` (§7.2), which the existing guard makes behaviour-preserving.
The runtime-probe slice changes **no production code, profiles, or golden vectors**:

* `RomHackDetector.detectCompatibility` — only a real exact SHA-256 match to a verified, layout-
  verified profile yields `VERIFIED`; filename, header and base-game heuristics yield
  `RECOGNIZED_UNVERIFIED` and never grant authorization.
* `RuntimeRomTrust.exactRuntimeVerified` — requires an exact match method, a runtime hash equal to
  the detected hash, and membership in the profile's own hash list.
* `CalcCapabilityPolicy.evaluate` — caps at `ESTIMATED` with `ROM_NOT_EXACT_VERIFIED` unless
  `exactRuntimeVerified`; `VERIFIED` is reached only with the `VANILLA_GEN3` ceiling and no
  limitations, and the two Doubles format gates of §2.2 apply on top of that.
* `RuntimeRomTrust.mayReadBattleState` — strictly narrower than `mayReadLiveMemory`: it additionally
  requires `RomHackProfile.battleStateReadVerified`. Calculator trust and the battle-state read
  authorization are separate decisions, so correcting a profile's hashes cannot promote a read whose
  address was never proven (§7.3).
* `CalcRequestBoundary` — the only path from application state to an engine request; the authorized
  request is the one the verdict was computed from.

`CalcVanillaGoldenBoundaryTest` drives this real boundary (not the policy directly) and asserts, for
both exact profiles and both FireRed revisions: `Ready` → `VERIFIED` → no limitations → the
production serialisation exactly matches the golden request. It also covers near-miss hashes,
header-only recognition, wrong running bytes, unsupported ability/item/status/weather, and — added by
this correction pass — the three Doubles-screen verdicts of §2.2: the Doubles + screen refusal, the
Singles + same-screen negative control, and the screenless-Doubles control.

---

## 9. CI coverage

`./ci.sh test` now runs, in order:

1. `calc_test` — the shipped engine executes the golden matrix
   (`run_vanilla_golden_matrix`), asserting every roll, and then
   `check_vanilla_doubles_cartridge_divergence()` asserts that the engine does **not** produce
   either committed cartridge Doubles-screen vector;
2. `calc_goldens_check` — `verify_goldens.py` re-derives every expected roll from the independent
   oracle, re-derives all four `cartridgeReferences` vectors through `doubles_cartridge_rolls()`,
   requires each refused branch to be recorded in both of its target-presence states with the two
   states differing, and checks that the bundled profile hashes match the golden provenance;
   `generate_gen3_move_targets.py --check` re-derives `gen3_move_targets.json`;
3. the native reader suite — includes the party-layout assertions that pin the corrected enemy count
   offsets;
   the engine suites also run the whole-move-list spread-table check and the per-state cartridge
   divergence check described in §2.2.1/§2.2.2;
4. the Kotlin suite — `CalcVanillaGoldenBoundaryTest` drives the production boundary for both
   profiles, compares the serialisation with the executed request, and pins the Doubles-screen
   fail-closed verdicts.

The fixture file is the single source of truth for all four, so a profile edit, an oracle change or a
bundle change that alters a golden fails the canonical gate.

`tools/calc-goldens/audit_vanilla_layout.py` (§7) is deliberately **not** part of `./ci.sh test`: its
ELF mode needs builds of the pinned upstream decompilations and its ROM mode needs a legally obtained
retail dump, neither of which the canonical gate fetches. It is the recorded, re-runnable command
behind §7's evidence, not a hidden CI step — and its ROM mode exits non-zero for FireRed, which is
how §7.3's `UNPROVEN` finding is reproducible rather than asserted.

---

## 10. Remaining limitations

* Two controlled-operand runtime observations per exact FireRed Rev 0 / Emerald build now pass
  (§5). This is not an unmodified-playthrough, full-vector or all-modifier runtime sweep. No legal
  FireRed Rev 1 dump is present; Rev 1 remains runtime-untested.
* The **damage** arithmetic is re-derived per revision; the read-only memory layout is audited at
  build level for FireRed Rev 0, FireRed Rev 1 and Emerald (§7), and the battle-state addresses are
  probed against the retail images (§7.3). That literal scan does not prove symbol identity; §5.1
  observes different diagnostic bases in both games. The battle-state read surface stays unauthorized for **both**
  vanilla profiles (`battleStateReadVerified = false`), because opening it for Emerald would switch
  on the legacy presence heuristic without giving the enemy-party path the lifecycle authority it
  requires (§7.4).
* Vanilla `Doubles` shares the verified ruleset and is covered by one single-target, screenless
  fixture; it is a claim about that geometry only, not about live Doubles battle state. Doubles
  **with** a screen or **with** a move the pipeline reduces is refused (§2.2, §2.2.1).
* The enemy party count offset correction (§7.2) is evidenced but its *effect* is currently hidden
  twice over: no vanilla title declares the full battle-lifecycle gate, and the battle-state reads
  that would consume it are unauthorized (§7.3). Restoring that read surface is a separate slice and
  must not be inferred from this correction.
* Badge boosts are unmodelled and disclosed on every verified headline (§2.1).
* The matrix covers the ordinary single-hit damage path; exotic move mechanics (multi-hit, fixed
  damage, HP-scaled power, etc.) remain outside the vanilla `VERIFIED` surface and are not asserted
  here.
* A manual request that omits a damage-relevant ability/item/status field relies on the pipeline's
  documented default semantics, not on an observation of the running game (§2.1).


## 11. Issue #9 acceptance audit (current main)

Audit baseline: `origin/main` **`aeace0300355895279b016f3bf3c9824183e7e5a`**, PR #70 confirmed
merged before starting, and origin/main rechecked unchanged after validation. This table separates
what is already on main from the new, unmerged runtime observations in §5. It does not equate
H&S exact-ROM recognition with a verified calculation. H&S remains capped at `ESTIMATED`.

| #9 acceptance criterion | Current-main evidence / disposition |
|---|---|
| Exact verified FireRed has golden damage fixtures covering Gen 3 behavior | **Met (source + host + boundary).** Twelve committed fixtures in `tools/calc-goldens/vanilla_gen3_goldens.json`; `run_vanilla_golden_matrix` in `native/tests/test_js_calc.c`; `CalcVanillaGoldenBoundaryTest` covers both exact revisions. Type-based split, STAB/type, crit, stage, screens, weather, burn/Guts are covered. This PR adds direct controlled runtime A/B observations for Rev 0 only. |
| Exact verified Emerald has golden damage fixtures | **Met (source + host + boundary).** The same matrix executes and crosses the production boundary for the exact Emerald hash. This PR adds direct controlled runtime A/B observations. |
| H&S 2.0.5 has a documented calculator capability matrix derived from upstream 2.0.5 evidence | **Met.** `HNS_2_0_5_CALCULATOR_CAPABILITY.md` §2, backed by pinned `Release-v2.0.5 @ 1f42b74d` source/configuration; §§3, 6–7, 10–14 classify type/category, data/forms, ability/item, weather/terrain/screen/crit and challenge settings. |
| Supported H&S calculations have golden fixtures against known in-game/upstream results | **Met for the authorized subset.** `tools/hns-runtime-probe/evidence/rom-damage-goldens.json` and A/B/C raw logs; `check_gap_c4d_rom_damage_goldens` binds direct observed neutral, STAB/resistance and stage hits to the engine/oracle. `check_gap_c4b_arithmetic_coverage` and `check_gap_c4e_pinch_abilities` cover additional upstream-derived arithmetic. Capability §§14.9, 14.12 define/reuse these for the supported ordinary Singles subset. |
| H&S species/moves/items/abilities used by verified calculations resolve to correct 2.0.5 data | **Met for admitted data, with an explicit trust ceiling.** No H&S result is called `VERIFIED`. `CalcDataOverrides` consumes pinned species/move records, while effective abilities and items resolve through the H&S registries and live numeric IDs. `CalcDataOverridesTest`, `CalcHnsAbilityTest`, `CalcHnsItemTest`, native calculator and generator suites test consumption, ambiguity and refusal. This is positive data-path evidence for admitted `ESTIMATED` requests, not a vacuous claim based on zero verified H&S requests. Unsupported/ambiguous forms and damage items remain refused. |
| Recognized/unverified and unsupported ROMs never receive a confident verified result from default Gen 3 assumptions | **Met.** `RuntimeRomTrust`, `CalcCapabilityPolicy`, `CalcRequestBoundary`; `RomCompatibilityTest`, `CalcCapabilityPolicyTest`, `CalcVanillaGoldenBoundaryTest`, `CalcHnsC4eProductionBoundaryTest` cover hash near-misses, header-only recognition, wrong runtime bytes, missing data and the H&S ceiling. |
| Unsupported H&S mechanics/states fail honestly or are clearly labeled approximate/manual | **Met for the audited boundary.** Capability §§14.9–14.13; `CalcHnsMechanicsTest`, `CalcHnsLiveBattleStateTest` and `CalcHnsC4eProductionBoundaryTest` enforce ordinary moves, exact live operands, Singles, unsupported items/abilities, type/gimmick/volatile/status/field/weather/screen/format refusal and caller-state rebinding. Accepted H&S requests are labeled `ESTIMATED`, never `VERIFIED`. |
| Calculator support capability is tied to the active exact ROM/ruleset, not filename/profile-name heuristics | **Met.** `RuntimeRomTrust.exactRuntimeVerified` checks runtime/detection/profile hashes; capability rows bind engine, data pack and mechanics; the production request boundary owns the verdict and request. Exact vanilla and exact H&S boundary/trust tests cover positive and forged/near-miss cases. |
| #29 runs the maintained QuickJS/native calculator suite under canonical `./ci.sh test` | **Met.** `ci.sh` calls `calc_test`, checks the pinned QuickJS submodule and fails closed. This slice's `./ci.sh all` passed 2,505 calculator assertions plus native readers, 75 H&S tracker checks, 12 new probe checks, generator suites, 738 Kotlin tests and the debug build. |

**Closure recommendation:** all nine criteria as written have evidence for the bounded surface;
close #9 after senior review and merge of this runtime-evidence PR. Until then leave #9 and the PR
open. The smallest remaining action for this slice is review/merge and acceptance bookkeeping,
not implementation of broader H&S mechanics. H&S's existing §14.14 warning that its document alone
does not close #9 is respected by this cross-target audit; its broader exclusions are not silently
promoted into support or new closure requirements.

Unchanged limits remain visible: H&S Doubles, damage items, many abilities/moves and active
unmodelled states are refused; positive badge runtime Golden D and positive transient transitions
are not runtime-proven, and crit Golden E is indirect. Those states retain their documented
source/host evidence or refusal and the `ESTIMATED` ceiling. If a follow-up runtime-evidence slice
is desired, positive H&S badge Golden D is narrower than opening more mechanics; it is not
claimed complete here. #40 remains a separate umbrella gate. Vanilla lifecycle support and
FireRed Rev 1 runtime observation are also separate from this closure recommendation.
