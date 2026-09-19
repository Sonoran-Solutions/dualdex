# QuickJS calculator tests

The damage calculator is production code: `native/src/js_calc_engine.c` embeds
QuickJS-NG and evaluates `app/src/main/assets/calc_bundle.js`, which wraps
`@smogon/calc` behind the `DualDexCalc.calculateDamage(json)` global.

`./ci.sh test` compiles that engine for the host against the pinned QuickJS
submodule and **executes** real damage calculations, so a calculator regression
fails the canonical gate instead of only surfacing on a handheld.

```bash
./ci.sh test        # native reader + H&S tracker + QuickJS calculator + Kotlin
./ci.sh all         # the same, then `./ci.sh build`
```

The QuickJS submodule is required by `test` as well as `build`. `ci.sh`
initializes it and verifies it against the commit recorded by the superproject
before anything is compiled, so a fresh checkout can run `./ci.sh test` without
`./ci.sh build` as an undocumented setup step. A missing compiler, a missing or
stale submodule, a compile/link failure, a bundle that fails to evaluate, and a
failing assertion all exit non-zero. Nothing is silently skipped.

## What the host build links

`calc_test()` in `ci.sh` mirrors `app/src/main/cpp/CMakeLists.txt`:

| | Android (`CMakeLists.txt`) | Host (`ci.sh calc_test`) |
|---|---|---|
| Engine | `native/src/js_calc_engine.c` | same file |
| Dependency | `native/quickjs` submodule @ recorded commit | same commit, verified |
| Defines | `_GNU_SOURCE`, `CONFIG_VERSION="2024-01-13"`, `CONFIG_BIGNUM` | same |
| QuickJS sources | `quickjs.c`, `libregexp.c`, `libunicode.c`, `dtoa.c` | same |
| Compile option | `-fno-strict-aliasing` | same |
| Bundle | `assets/calc_bundle.js` from the APK | `app/src/main/assets/calc_bundle.js` |
| Output | `libdualdex_native.so` | `native/build/test_js_calc` (gitignored) |

`js_calc_engine.c` needs Android logging on one error path. That boundary is a
per-file conditional in the same style as `native/src/pokemon_reader.c`:
`__ANDROID__` builds keep `__android_log_print(..., "DualDex_JNI", ...)`; host
builds print the same tag and message to `stderr`. No header is shadowed, no
second engine exists, and no damage logic is duplicated.

`native/tests/json_lite.h` is a small test-only JSON reader (no new dependency).
It lives in `native/tests/`, which is not on the Android include path, so it can
never leak into production builds. It rejects malformed input instead of
partially accepting it, and it is what lets the suite assert typed values
(numbers, strings, arrays) rather than substrings - a substring check such as
`"minDamage":22` also matches `"minDamage":220`.

## Fixture coverage

| Fixture | Purpose |
|---|---|
| `gen3_spread_move_no_field` | no `field` at all behaves as singles |
| `gen3_spread_move_empty_field` | `field` present, `gameType` omitted |
| `gen3_spread_move_null_game_type` | explicit `null` means "not specified" |
| `gen3_spread_move_empty_game_type` | explicit `""` means "not specified" |
| `gen3_spread_move_lowercase_singles` | the lowercase spelling the app used to send |
| `gen3_spread_move_canonical_singles` | core golden for the spread-move regression |
| `gen3_spread_move_lowercase_doubles` | lowercase doubles keeps doubles behaviour |
| `gen3_spread_move_canonical_doubles` | library doubles behaviour preserved |
| `gen3_spread_move_uppercase_rejected` | other casings are rejected, not coerced |
| `gen3_spread_move_unsupported_rejected` | unknown format is an error |
| `gen3_spread_move_non_string_rejected` | non-string format is an error |
| `gen3_special_type_split_crunch` | Gen III is type-based: Dark Crunch is special |
| `gen3_stab_and_type_effectiveness` | STAB ×1.5 then per-type effectiveness, floored in order |
| `gen3_choice_band_item` | Choice Band ×1.5 attack, natures, dual-type effectiveness |
| `gen3_ability_thick_fat_fire` | control case: no ability specified |
| `gen3_ability_thick_fat_halves_fire` | Thick Fat halves the Fire attack form |
| `gen3_reflect_physical_singles` | singles Reflect is ×1/2 |
| `gen3_reflect_doubles_uses_two_thirds` | doubles screens are ×2/3: format-dependent |
| `gen3_light_screen_special_singles` | singles Light Screen is ×1/2 |
| `gen8_generic_engine_smoke` | generic modern-generation coverage (not H&S) |
| `invalid_json_input`, `unknown_species`, `unknown_move` | error contract |

Each fixture states generation, level, nature, IVs, EVs, item, ability, move,
and field conditions explicitly, so no expectation depends on a hidden default.

### Expected-value provenance

Expectations are derived from the documented Generation III mechanics - stat
formula, then

```text
base   = floor(floor(floor(2*L/5 + 2) * BP * A / D) / 50)
damage = base -> attack-form mods routed through the damage value
              (burn, Reflect/Light Screen, spread penalty)
              -> +2 -> critical -> STAB -> per-type effectiveness
rolls  = floor(damage * r / 100) for r = 85..100, minimum 1
```

and verified against the pinned library's ADV implementation
(`calculateADV`/`calculateAttackADV`/`calculateDefenseADV` in the bundle) so the
rounding order is modelled exactly rather than assumed. The arithmetic for each
fixture is written next to it in `native/tests/test_js_calc.c`, and every Gen III
fixture asserts the complete 16-entry roll vector, so a rounding change cannot
hide behind a matching min/max pair.

These numbers were computed independently and then cross-checked against the
engine; they were not produced by running the engine and copying its output.

## `field.gameType` input contract

`@smogon/calc` compares `field.gameType` **case-sensitively** against
`"Singles"` / `"Doubles"` (its own `Field` default is `"Singles"`). Anything
else selects the non-singles damage path, which halves Gen III/IV spread moves
(Rock Slide, Earthquake, Surf, ...) and weakens Reflect/Light Screen.

`normalizeGameType()` at the `entry.js` boundary is the single place that
decides the format:

| Input | Result |
|---|---|
| omitted / `null` / `""` / whitespace-only | `Singles` |
| `"singles"` / `"Singles"` | `Singles` |
| `"doubles"` / `"Doubles"` | `Doubles` |
| any other string, or a non-string | `{"success": false, "error": ...}` |

Unknown values are never coerced. Kotlin builds requests from
`CalcGameTypes.SINGLES` / `CalcGameTypes.DOUBLES`, and
`CalcGameTypeRequestTest` pins the value the application actually serialises.

### Defect record (issue #29)

The application sent `"singles"`, so every spread move was treated as doubles:

```text
Machamp (Hardy L50, 0 EV) Rock Slide vs Snorlax (Hardy L50, 0 EV)
  before: 26-31  (accidentally the doubles value)
  after:  51-60  (correct singles value)
```

`gen3_spread_move_lowercase_singles` reproduces the historical request and now
requires the corrected singles result.

## Regenerating the shipped bundle

`tools/calc-bundler/entry.js` is the source of truth; `calc_bundle.js` is
generated and committed. With the pinned lockfile:

```bash
cd tools/calc-bundler
npm ci
./node_modules/.bin/esbuild entry.js --bundle --minify --format=iife \
  --outfile=../../app/src/main/assets/calc_bundle.js
```

Pinned versions: `@smogon/calc` 0.11.0, `esbuild` 0.28.2 (from
`package-lock.json`). Verification performed for this change:

- rebuilding the pre-change `entry.js` reproduces the committed bundle
  byte-for-byte (`sha256 f9f0075c8612408bc215826ea55bc53db07d92a87ebc141287ec19fc2aa07546`),
  confirming the toolchain and flags;
- two consecutive regenerations of the changed bundle are identical
  (`sha256 e618708d8fb37d1b809e62ec800f68aff13d6c54790a0f65b44ef65aa8d7a93d`);
- running the pre- and post-change bundles side by side over 3360 request
  variants shows zero differences for explicit canonical `Singles`/`Doubles`
  inputs (280/280 byte-identical outputs), so the library damage path is
  untouched; all differences are the intended format normalisation.

Regeneration is a deliberate, reviewable step. `./ci.sh test` uses the
committed bundle and never requires Node.

## Scope

This suite establishes calculator execution and regression coverage for the
input contract and vanilla Gen III rules. It does **not** establish H&S 2.0.5
compatibility: those fixtures need the hack's own species/move/item data and
belong to issue #9. `gen8_generic_engine_smoke` is generic engine coverage, not
H&S coverage.
