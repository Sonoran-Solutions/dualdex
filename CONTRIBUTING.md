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
# Clone the repository
git clone https://github.com/Sonoran-Solutions/dualdex.git
cd dualdex

# Run native C test suites
gcc -O2 -I native/include native/src/pokemon_reader.c native/src/pokemon_text.c native/tests/test_pokemon_reader.c -o native/test_runner && ./native/test_runner

# Run Kotlin unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

---

## 2. Adding a New ROM Hack Profile

DualDex uses modular JSON configuration files in `app/src/main/assets/profiles/` to adapt the companion UI, real-time memory offsets, damage calculator rules, and documentation.

### Step 1: Create the Profile JSON
Create a new file in `app/src/main/assets/profiles/<your_hack_id>.json`:

```json
{
  "id": "my_rom_hack",
  "name": "Pokemon Custom Hack Name",
  "baseGame": "FireRed",
  "gameId": 1,
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
| `gameId` | Integer | Internal engine ID (`0` = Emerald, `1` = FireRed, `5` = Radical Red, `6` = Ghost Grey). |
| `engine` | String | Engine used (`Vanilla`, `HexManiacAdvance`, `CFRU`, `decomp`). |
| `hasEvs` | Boolean | Set `false` if the hack removes Effort Values (e.g. Ghost Grey). Hides EV displays. |
| `hasIvs` | Boolean | Set `false` if Individual Values are removed or normalized. |
| `hasPhysSpecSplit` | Boolean | `true` if moves have individual Physical/Special categories instead of Gen 3 type categories. |
| `steelResistsGhostDark`| Boolean | `true` if Steel retains pre-Gen 6 resistance to Ghost and Dark. |
| `cfruOffsets` | Boolean | Set `true` if built with Complete FireRed Upgrade (expanded memory structures). |
| `playerPartyOffset` | Long | Decimal address in EWRAM for player party (`0x02024284` = `33702532` for FireRed). |
| `enemyPartyOffset` | Long | Decimal address in EWRAM for opponent party (`0x0202402C` = `33701932` for FireRed). |
| `docsUrl` | String? | Web dex or spreadsheet URL. If provided, loaded in the Docs WebView tab. |
| `headerTitles` | List<String> | ASCII strings present in bytes `0xA0..0xAB` of the GBA header. |
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
