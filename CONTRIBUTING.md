# Contributing to DualDex

Thank you for your interest in contributing to **DualDex**! This document provides technical guidelines for contributing code, adding new features, and especially adding support for new **Pokémon ROM hacks**.

---

## 1. Development Setup

### Prerequisites
- **Android SDK & NDK**:
  - `Android SDK Platform 34` (`android-34`)
  - `Android NDK r27` (`27.2.12479018`)
  - `CMake 3.22.1`
- **JDK**: OpenJDK 17
- **Node.js**: Node 18+ (for modifying or rebuilding `@smogon/calc` bundles)
- **Host C Compiler**: GCC or Clang (for running native standalone test runners)

### Building the Project
```bash
# Clone the repository (the QuickJS submodule is required by build and test)
git clone --recurse-submodules https://github.com/Sonoran-Solutions/dualdex.git
cd dualdex

# Canonical contract: native reader + H&S tracker + QuickJS calculator
# (real host execution of the shipped bundle) + Kotlin unit tests
./ci.sh test

# Assemble debug APK
./ci.sh build
```

Ad-hoc equivalents, when you only want one suite:

```bash
# Native reader suite
gcc -O2 -I native/include native/src/pokemon_reader.c native/src/pokemon_text.c native/tests/test_pokemon_reader.c -o native/test_runner && ./native/test_runner

# Kotlin unit tests
./gradlew testDebugUnitTest
```

The QuickJS calculator suite is driven by `./ci.sh test` (it needs the pinned
submodule and specific defines); see
[docs/QUICKJS_CALCULATOR_TESTS.md](docs/QUICKJS_CALCULATOR_TESTS.md).
`tools/calc-bundler/entry.js` is the source of truth for
`app/src/main/assets/calc_bundle.js`; regenerate the committed bundle with the
pinned toolchain as described there instead of editing it by hand.

---

## 2. Adding a New ROM Hack Profile

DualDex uses modular JSON configuration files in `app/src/main/assets/profiles/` to adapt the companion UI, ROM detection, damage calculator rules, and documentation.

> **Memory offsets are not read from JSON.** Live reads use the compiled `GameMemoryConfig` tables in `native/src/pokemon_reader.c`, selected by `gameId` (the `GameType` enum in `native/include/pokemon_reader.h`). `playerPartyOffset`/`enemyPartyOffset` in the JSON are documentation only and must be kept in sync with the native config by hand. Supporting a new memory layout means adding a native config (with evidence), not just a profile.

### Step 1: Create the Profile JSON
Create a new file in `app/src/main/assets/profiles/<your_hack_id>.json`:

```json
{
  "id": "my_rom_hack",
  "name": "Pokemon Custom Hack Name",
  "baseGame": "FireRed",
  "gameId": 2,
  "developer": "Hack Author Name",
  "engine": "CFRU",
  "hasEvs": true,
  "hasIvs": true,
  "hasPhysSpecSplit": true,
  "steelResistsGhostDark": false,
  "cfruOffsets": true,
  "playerPartyOffset": 33702532,
  "enemyPartyOffset": 33701932,
  "docsUrl": "https://example.com/pokedex",
  "headerTitles": ["BPRE", "CUSTOMHACK"],
  "sha256Hashes": [
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  ],
  "customSpecies": {
    "500": {
      "name": "Delta Charizard",
      "type1": "Ghost",
      "type2": "Dragon",
      "hp": 78,
      "atk": 84,
      "def": 78,
      "spa": 109,
      "spd": 85,
      "spe": 100
    }
  }
}
```

### Configuration Fields Reference

| Field | Type | Description |
|---|---|---|
| `id` | String | Unique lowercase identifier (e.g. `ghost_grey`, `radical_red`). |
| `name` | String | User-facing display title. |
| `baseGame` | String | Base ROM name (`FireRed`, `Emerald`, `Ruby`, `Sapphire`). |
| `gameId` | Integer | Native `GameType` value that selects the compiled memory layout (`0` = unknown, `1` = Emerald, `2` = FireRed, `3` = LeafGreen, `4` = Ruby, `5` = Sapphire, `6` = Ghost Grey, `7` = Radical Red, `8` = Heart & Soul, `9` = Unbound). |
| `engine` | String | Engine used (`Vanilla`, `HexManiacAdvance`, `CFRU`, `decomp`). |
| `hasEvs` | Boolean | Set `false` if the hack removes Effort Values (e.g. Ghost Grey). Hides EV displays. |
| `hasIvs` | Boolean | Set `false` if Individual Values are removed or normalized. |
| `hasPhysSpecSplit` | Boolean | `true` if moves have individual Physical/Special categories instead of Gen 3 type categories. |
| `steelResistsGhostDark`| Boolean | `true` if Steel retains pre-Gen 6 resistance to Ghost and Dark. |
| `cfruOffsets` | Boolean | Set `true` if built with Complete FireRed Upgrade (expanded memory structures). |
| `playerPartyOffset` | Long | Documentation only (not read at runtime): decimal GBA address of the player party (`0x02024284` = `33702532` for FireRed). |
| `enemyPartyOffset` | Long | Documentation only (not read at runtime): decimal GBA address of the opponent party (`0x0202402C` = `33701932` for FireRed). |
| `docsUrl` | String? | Web dex or spreadsheet URL. If provided, loaded in the Docs WebView tab. |
| `headerTitles` | List<String> | Recognition keywords matched against the 12-byte header title (`0xA0..0xAB`) and the filename. Short keywords must be a whole word; 4-letter game codes (e.g. `BPRE`) never pick a hack. Recognition never unlocks memory reads (only an exact `sha256Hashes` match can). |
| `sha256Hashes` | List<String> | SHA-256 hashes of known patched ROM releases for exact auto-detection. |
| `customSpecies` | Object | Map of custom species IDs to base stats and typings for regional forms. |

---

## 3. Testing Your Profile

Add a unit test in `app/src/test/java/com/dualdex/romhack/RomHackProfileTest.kt`:

```kotlin
@Test
fun testParseMyCustomHackProfile() {
    val jsonStr = "...your json..."
    val profile = ProfileLoader.parseProfile(jsonStr)
    assertEquals("my_rom_hack", profile.id)
    assertEquals("Pokemon Custom Hack Name", profile.name)
}
```

Run tests to ensure everything builds and passes:
```bash
./gradlew testDebugUnitTest
```

---

## 4. Pull Request Checklist

1. [ ] Code compiles without warnings (`./gradlew assembleDebug`).
2. [ ] All unit tests pass (`./gradlew testDebugUnitTest`).
3. [ ] Code follows Kotlin and C11 styling standards.
4. [ ] Any new ROM hack profiles include valid `headerTitles` and verified memory offsets.

## Exporting H&S calculator playtest coverage

1. Install and run a debug build (`./ci.sh build`).
2. Play H&S normally with the Battle tab open. Its calculator evaluations are collected locally.
3. Open **Settings → About & Diagnostics** to find the debug export actions.
4. Choose **Export H&S Coverage Log**.
5. Save or share the JSON using Android's share sheet.
6. Attach it to [#83](https://github.com/Sonoran-Solutions/dualdex/issues/83) or the relevant future calculator issue.

Release builds do not collect, persist or export this log and have no coverage actions.
The standalone Calc tab does not collect coverage. No network or additional permission is used.
The log contains species/move identities, selected slots/engine indices, observed battle context,
structured mechanics and exact/caveated/refused authorization counts. It excludes ROM/save paths,
trainer/nickname/account identities and raw memory. Trainer/wild is currently `UNKNOWN` because
that value is not exposed authoritatively by the app reader; unread topology or Random Abilities
also stays `UNKNOWN`.

Selected party slots (or engine indices when a slot is unavailable) identify participants; newly
readable live identities do not create another row. Participant labels retain the latest known identity.
One participant pair/move/direction produces one logical row per battle. Recalculations increment
`seenCount`, update first/last-seen Unix millisecond timestamps, retain all observed outcome counts and merge distinct
structured mechanics; they do not count as independent blocker incidents. Leaving and entering a
battle starts a new session. Rows survive navigation and restart, in app-private
`filesDir/hns-coverage/log.json`. The cap is **2,000 rows** (typically a few MB), evicting oldest
first-seen rows first, even if they were recently refreshed. Each row retains at most 512 distinct
mechanic identities; any overflow is explicitly reported as `droppedMechanicObservations`.
Exported summaries count affected logical rows and distinct retained sessions, sorted by affected
row count then stable identity. Exactly modelled decisions are retained as `MODELLED` and excluded from the blocker summary,
including applicable conditional abilities and exact Doubles Plus/Minus. Earlier false blocker
dispositions are repaired when loading stored logs, preserving observations.
Exact/caveated/refused row totals can overlap when a row changed tier.
`evictedRecords` and storage failures are reported; the summary covers retained rows only.
**Clear H&S Coverage Log** asks for confirmation before deleting the collected observations.

For a focused ROM-free check, run `./ci.sh coverage-test`. The canonical test gate also runs
`python3 tools/ci/check_hns_coverage_release.py`: it pins the literal release no-op factory,
Android's default main+release source-set isolation, and absence of debug store/worker/export/UI
and FileProvider resources from those source sets. The real implementation and narrow cache
provider exist only in `src/debug`. This avoids the human-only production-signing gate that
release Gradle tasks depend on.
