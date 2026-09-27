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
| `gen3_spread_move_proto_rejected` | `__proto__` must not pass the whitelist |
| `gen3_spread_move_constructor_rejected` | `constructor` must not pass the whitelist |
| `gen3_spread_move_to_string_rejected` | `toString` must not pass the whitelist |
| `gen3_spread_move_has_own_property_rejected` | `hasOwnProperty` must not pass the whitelist |
| `gen3_special_type_split_crunch` | Gen III is type-based: Dark Crunch is special |
| `gen3_stab_and_type_effectiveness` | STAB ×1.5 then per-type effectiveness, floored in order |
| `gen3_choice_band_item` | Choice Band ×1.5 attack, natures, dual-type effectiveness |
| `gen3_ability_thick_fat_fire` | control case: no ability specified |
| `gen3_ability_thick_fat_halves_fire` | Thick Fat halves the Fire attack form |
| `gen3_reflect_physical_singles` | singles Reflect is ×1/2 |
| `gen3_reflect_doubles_pipeline_arithmetic` | the shipped `@smogon/calc` Doubles screen arithmetic (post-roll `×2/3`) — deliberately NOT the cartridge's `2 * (damage / 3)`, which is why production refuses this shape for exact vanilla (`VANILLA_DOUBLES_SCREEN_NOT_MODELLED`) |
| `gen3_light_screen_special_singles` | singles Light Screen is ×1/2 |
| `gen3_crit_doubles_the_attack_form` | the Crit checkbox path: Generation III crits are ×2 |
| `gen3_explicit_guts_boosts_a_statused_attacker` | a whitelisted ability must reach the pipeline |
| `gen3_unmodelled_ability_is_silently_ignored` | the silent-degradation path the policy gates |
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

### Vanilla FireRed / Emerald golden matrix

`run_vanilla_golden_matrix()` in this suite reads
`tools/calc-goldens/vanilla_gen3_goldens.json`, executes each fixture's **exact**
production-serialised request against the shipped bundle, and asserts the full 16-roll
vector for the neutral physical, neutral special (Gen III type split), STAB + type
effectiveness, critical hit (and crit stage-ignore), Singles Reflect / Light Screen,
a Doubles single-target (unreduced) control, Rain/Sun, burn + Guts, and stat-stage branches.

Doubles **screens** and Doubles **spread** moves are deliberately absent from this matrix: the
cartridge conditions both on how many opposing battlers are actually present, which the request shape
cannot carry, so production refuses them
(`CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED`, `CalcLimitation.VANILLA_DOUBLES_SPREAD_NOT_MODELLED`)
and their source-exact vectors live in the fixture file's `cartridgeReferences` block instead. The
same suite also proves the move table the spread gate rests on, over the whole Generation III move
list, and asserts that the shipped engine does not produce either committed cartridge vector.

The same fixture file is used by two other checks, so none of the three can drift:

* `tools/calc-goldens/verify_goldens.py` re-derives every expected roll from the
  independent Generation III oracle `tools/calc-goldens/gen3_reference.py` and checks that
  the bundled FireRed/Emerald profile SHA-256s match the golden provenance;
* `app/src/test/java/com/dualdex/calculator/CalcVanillaGoldenBoundaryTest.kt` drives the
  real `CalcRequestBoundary` for both exact profiles, proves `Ready` / `VERIFIED`, and
  asserts the production serialisation is identical to the executed request.

The matrix, its pinned pret commits, and the exact supported ROM hashes are documented in
[VANILLA_CALCULATOR_EVIDENCE.md](VANILLA_CALCULATOR_EVIDENCE.md).

### The numeric oracle and the parser are tested too

The assertion helpers, the response-validation path, and the test-only reader
are part of the fail-closed contract, so the suite also runs self-tests over
them (`oracle_self_test`, `smoke_path_self_test`, `parser_self_test`).

**What every successful response must satisfy** (with or without a golden
vector), enforced by `validate_success_response()` — the single path used both
for real engine responses and for the synthetic self-test responses:

| Requirement | When |
|---|---|
| every damage element is a finite integral number in range | always |
| `minDamage` / `maxDamage` present and valid | always |
| `minDamage == damage[0]`, `maxDamage == damage[last]` | always without a golden; exact golden equality with one |
| `range` is a 2-element array equal to the vector endpoints | always |
| exact equality with the independently derived golden values | only when a golden vector exists |

"No precomputed golden" therefore means *validate structure and internal
consistency*, not *skip the numeric checks* — which is what the Gen 8 smoke
fixture relies on.

- **Exact, non-truncating comparisons.** `check_number_shape()` requires the
  JSON number type, a finite value, exact integrality, and representable bounds
  *before* any conversion, and `check_number()` then compares the original
  parsed value against the expected integer. A fractional response
  (`minDamage: 51.9` where 51 is expected - exactly what a lost flooring step
  produces) fails instead of being truncated into a pass, and a missing or
  wrong-typed field fails even when the expectation is zero, so `jl_num()`'s
  zero fallback can never fake a match.
  Self-tests cover fractional scalars and array elements, missing fields,
  missing elements, wrong types (string/boolean/null/array/object), values
  outside the representable range, wrong-length roll vectors, and - as positive
  controls, so a checker that rejected everything could not pass - exact
  integral values including zero, negative values, and the range limit.
- **The no-golden (smoke) branch is covered too.** `smoke_path_self_test` feeds
  `validate_success_response()` synthetic responses with `expect_rolls == NULL`:
  an internally consistent response must be accepted, while wrong/missing/typed
  `minDamage` and `maxDamage`, string or fractional interior elements, a missing
  element, a non-array `damage` field, a `range` that disagrees with the vector,
  a missing `range`, and wrong `movePower`/`moveType` must all be rejected.
- **Strict JSON (see `json_lite.h`).** Rejected: leading zeros (`051`), a
  trailing decimal point (`51.`), a bare fraction (`-.1`), a leading `+`,
  `1e` with no digits, `1e999` (non-finite), `NaN`, embedded NUL escapes
  (which would otherwise alias a shorter value or key through C string
  comparison), trailing commas, missing colons, unterminated arrays, and
  trailing garbage. Accepted, with positive controls: `0`, negative and
  fractional numbers, exponent forms, nested objects/arrays, escapes, and
  UTF-8 escapes.

## H&S 2.0.5 differential damage oracle (issue #90)

`./ci.sh test` also runs `native/build/test_hns_damage_oracle`: every scenario of the committed golden
corpus `tools/hns-damage-oracle/corpus.json` is turned into the H&S request production builds
(`DamageCalculator.kt` `buildCalcRequestJson` shape: pinned species/move overrides, live raw stats,
stat stages, badge boosts, attacker HP), executed by the same QuickJS engine and bundle as the suite
above, and compared **roll by roll, all 16, exactly**. Each mismatch prints one
`HNS_ORACLE_MISMATCH {...}` JSON line (scenario, surface, first differing roll, expected and actual
vectors, the exact request) and fails the gate unless the scenario is registered in
`tools/hns-damage-oracle/known_divergences.json` with its tracking issue and its current QuickJS
16-roll output pinned. Only that exact wrong vector is accepted; a different wrong vector fails even
for an already registered scenario. A registered scenario that starts to match fails too. The runner
self-tests its own mismatch detection (one altered roll at each of the 16 indices,
refused/short/fractional/string responses, duplicate IDs, wrong commit, wrong backend).

**Current result.** 1,324 scenarios (1,209 on the production-modelled surface, 115 engine-only);
1,313 match all 16 rolls exactly. The 11 registered divergences are tracked in
[#97](https://github.com/Sonoran-Solutions/dualdex/issues/97) (type-based option style: pinned H&S
makes Ghost special and Dark physical),
[#98](https://github.com/Sonoran-Solutions/dualdex/issues/98) (Attack modifiers must be accumulated in
UQ4.12 before being applied: pinch + badge),
[#99](https://github.com/Sonoran-Solutions/dualdex/issues/99) (engine-only: Guts boosts special moves)
and [#100](https://github.com/Sonoran-Solutions/dualdex/issues/100) (engine-only: Doubles spread
reduction misses post-Generation-III spread moves).

**What the oracle is.** The expected vectors are measured from the **real pinned H&S battle engine**
(`PokemonHnS-Development/pokehns-expansion` @ `1f42b74dff0e9fe942419845d040663dd829a973`): the pinned
tree's own pokeemerald-expansion battle test runner, built with `make BUILD=hns TEST=1` and run
headlessly, executes each hit once per damage roll (`WITH_RNG(RNG_DAMAGE_MODIFIER, v)`, crit forced,
secondary effects off) and captures the HP-bar damage. See
[tools/hns-damage-oracle/README.md](../tools/hns-damage-oracle/README.md) for the backend decision
(Option A; the runtime-probe fallback was not needed), the one harness include-order patch the pinned
test runner needs to compile, how rolls are captured and verified, and the schema.

**What it is not.** It never calls `calculateHnsDamage`, `calc_bundle.js`, `@smogon/calc` or Kotlin
damage code, and it uses no ROM, BIOS or save. Normal CI never builds it: CI only *reads* the committed
corpus.

**Toolchain / upstream dependency.** Regeneration (developer only) needs a clean checkout of the
pinned commit (`HNS_UPSTREAM_DIR`), the Arm GNU Toolchain 13.2.rel1, a host C/C++ compiler, `make`,
`patch` and libpng:

```bash
HNS_UPSTREAM_DIR=<pinned checkout> python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py regenerate
HNS_UPSTREAM_DIR=<pinned checkout> python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py verify --order reversed
```

**Provenance rules.** An oracle golden is only valid with its provenance: pinned repository, commit and
tree; backend kind and build command; SHA-256 of the pinned `mgba-rom-test` binary and of every harness
patch; the ARM `gcc --version` line and SHA-256 of `arm-none-eabi-gcc` and `cc1`; generator version and
SHA-256 of the generated test source; schema version. No timestamps and no machine paths are allowed
(the serialiser rejects them) and the serialisation is canonical, so regeneration is byte-comparable.
`generate_hns_damage_oracle.py check` (in `./ci.sh test`) rejects a corpus from another commit or
backend, a non-canonical file, or one that is stale relative to the scenario matrix or the generated
test source. `HnsDamageOracleAuthorityTest` (Kotlin) proves that the species/move identity, battle
types, base stats, move power/type/category (both option styles, Fairy on/off), ability and item
identities and badge-boost verdicts the engine used agree with DualDex's own production sources.

### Three kinds of expected values

| Kind | Where | Authority | Trust |
|---|---|---|---|
| **ROM/runtime-observed** | Gap C4d goldens (`tools/hns-runtime-probe/evidence/`), vanilla runtime replays | a real hit in the official release ROM, captured by the developer probe | strongest for *that one hit*, but a single roll and sometimes faint-capped |
| **Hand-derived, source-backed** | `native/tests/test_js_calc.c` H&S fixtures (C1, C4a, C4b, C4e, C4f); vanilla `gen3_reference.py` | arithmetic written out from the pinned source by a reviewer | exact over 16 rolls, but only as good as the transcription; one PR per mechanic |
| **Oracle-generated** | `tools/hns-damage-oracle/corpus.json` | the pinned H&S battle code itself, executed | exact over 16 rolls, cheap to extend; trusted because it reproduces the two kinds above |

The oracle does **not** replace the other two. `fixture_crossref.json` maps 33 existing hand-derived,
ROM-observed and upstream-test fixtures to oracle scenarios and `check` requires the oracle to reproduce
each one exactly; those fixtures stay in place as independent controls on the oracle itself. An
oracle/fixture disagreement is investigated, never resolved by editing whichever side is inconvenient.

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

Unknown values are never coerced. The whitelist is a `Map`, not an object
literal: an object lookup also resolves inherited `Object.prototype` members, so
`"__proto__"`, `"constructor"`, `"toString"`, `"valueOf"`, and
`"hasOwnProperty"` returned objects/functions that passed a truthiness check,
were handed to `new Field(...)`, and silently selected the doubles path
(26-31 instead of 51-60) instead of being rejected. Those names now fail with
the unsupported-format error, each guarded by its own fixture.

Kotlin builds requests from `CalcGameTypes.SINGLES` / `CalcGameTypes.DOUBLES`,
and `CalcGameTypeRequestTest` pins the value the application actually
serialises.

### Defect record (issue #29)

The application sent `"singles"`, so every spread move was treated as doubles:

```text
Machamp (Hardy L50, 0 EV) Rock Slide vs Snorlax (Hardy L50, 0 EV)
  before: 26-31  (accidentally the doubles value)
  after:  51-60  (correct singles value)
```

`gen3_spread_move_lowercase_singles` reproduces the historical request and now
requires the corrected singles result. Note the shape of the pre-fix failures:
only the inputs that reached the old `|| 'singles'` fallback were wrong -
omitted `field`, an empty `field`, `null`, `""`, and the lowercase spelling -
while an explicit canonical `"Singles"` was forwarded unchanged and produced
51-60 even before the fix. That is why the pre-fix run failed
`gen3_spread_move_lowercase_singles` (and the equivalence group) but not
`gen3_spread_move_canonical_singles`.

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

- rebuilding the pre-change `entry.js` reproduces the original bundle
  byte-for-byte (`sha256 f9f0075c8612408bc215826ea55bc53db07d92a87ebc141287ec19fc2aa07546`),
  confirming the toolchain and flags;
- consecutive regenerations of the changed bundle are identical
  (`sha256 b60315d5be5e938f7894dd1ea63664161d631e34f718ee07a89e7026e76e8802`);
- running the pre- and post-change bundles side by side over 3360 request
  variants shows zero differences for explicit canonical `Singles`/`Doubles`
  inputs (280/280 byte-identical outputs), so the library damage path is
  untouched; all differences are the intended format normalisation.

The H&S held-item / PR #78 follow-up added four echo fields to the `calculateHnsDamage` result
(`attackerAbility`, `defenderAbility`, `attackerItem`, `defenderItem`) and nothing else; the damage
arithmetic is untouched. Consecutive regenerations of that bundle are identical
(`sha256 d53d93d2b6140f39dd3d5d93e2f0820768f69d55be8ec18757a50bac0050f857`), and every pre-existing
host check still passes against it. `check_pr78_tera_shell_truant_and_item_stripping` uses the echoes to
prove that Tera Shell/Truant reach the H&S path unmodified (no constructor/default-ability substitution)
and equal the neutral-control vector and the independent H&S oracle, and that a stripped H&S item
reaches the engine as no item and equals the explicit `ITEM_NONE` request.

Minified output renames identifiers, so a textual diff of the artifact is
unreadable by construction; the run-time equivalence check above is the drift
evidence.

Regeneration is a deliberate, reviewable step. `./ci.sh test` uses the
committed bundle and never requires Node.

## Scope

This suite establishes calculator execution and regression coverage for the
input contract and vanilla Gen III rules. It does **not** establish H&S 2.0.5
compatibility: those fixtures need the hack's own species/move/item data and
belong to issue #9. `gen8_generic_engine_smoke` is generic engine coverage, not
H&S coverage.

## Which ruleset is sent, and why (`gen` is not a default)

`CalcCapabilityPolicy` decides the `gen` value that
`app/src/main/java/com/dualdex/calculator/CalcRequestBoundary.kt` authorises, and
`CalcCapabilityPolicyTest` asserts it. Two of these fixtures exist specifically to
pin that decision and the silent-degradation and exact-match input contracts around it:

- `gen3_crit_doubles_the_attack_form` pins the ×2 critical-hit multiplier. Heart
  & Soul 2.0.5 deliberately keeps this at its Generation III value
  (`B_CRIT_MULTIPLIER == GEN_3` in the pinned upstream source), so the modern
  pipeline's ×1.5 would understate every critical hit in both supported builds.
- `gen3_unmodelled_ability_is_silently_ignored` is a deliberate negative
  fixture: an ability the ADV pipeline does not model returns an unmodified
  number with no error. That is why `CalcCapabilityPolicy` carries an explicit
  ability whitelist instead of passing ability names through, and why anything
  outside it is refused or downgraded rather than reported as verified.
- `gen3_rain_halves_a_fire_attack` and `gen3_lowercase_rain_is_ignored_by_the_engine`
  pin that weather is an **exact-match** input: the canonical spelling halves a
  Fire attack while `"rain"` is silently read as no weather at all. The capability
  policy therefore rewrites an accepted spelling to its canonical form before
  authorising a request, rather than validating leniently and forwarding verbatim.

The full ruleset decision, the per-mechanic capability matrix for H&S 2.0.5, and the
individual behaviours the ADV pipeline is *demonstrated* to share with that hack are
documented in [HNS_2_0_5_CALCULATOR_CAPABILITY.md](HNS_2_0_5_CALCULATOR_CAPABILITY.md).
That document also records why a resolving H&S name is evidence of identity only, and
not of the engine computing from H&S data.
