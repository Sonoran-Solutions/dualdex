# H&S 2.0.5 calculator capability matrix (issue #9)

This document is the **authority** for what DualDex's damage calculator may honestly claim about
Pokemon Heart & Soul (H&S) 2.0.5, and for how the two verified vanilla targets differ from it.

It is derived from the pinned upstream source, not from behaviour observed in the app:

| Item | Value |
|---|---|
| Repository | `PokemonHnS-Development/pokehns-expansion` |
| Tag | `Release-v2.0.5` |
| Commit | `1f42b74dff0e9fe942419845d040663dd829a973` |
| Local checkout used | `/home/dq/Projects/upstream-hns/pokehns-expansion` (outside this repository) |
| Engine | pokeemerald-expansion `1.15.2` |
| Embedded calculator | `@smogon/calc` 0.11.0 via `app/src/main/assets/calc_bundle.js` |
| Bridge source | `tools/calc-bundler/entry.js` |

No upstream source is vendored into DualDex. No ROM bytes are committed, copied, or reproduced.
Line references below are `file:line` into that pinned checkout.

**Vocabulary.** *SOURCE VERIFIED* means read directly from the pinned source (or from the vendored
library source). *NOT FOUND* means the evidence does not exist and is never treated as true.

---

## 1. The three answers, in one paragraph

DualDex supports **exact verified FireRed**, **exact verified Emerald**, and **exact H&S 2.0.5**. All
three share one damage pipeline (`@smogon/calc`'s ADV implementation, sent as `gen: 3`), for a
reason that is documented in §3 and is not a default. Vanilla FireRed/Emerald requests that stay
inside the verified input set are presented as **Verified**. H&S 2.0.5 requests are **refused**:
H&S lets the player change the *rule* that decides damage category, whether the Fairy type exists,
species typings, and the type chart itself; while DualDex consumes those settings at runtime via
`CalcRequestBoundary` (§4.1), the H&S type chart (Fairy type, modern Steel interactions) and modern
mechanics are not yet modelled by the Gen 3 ADV pipeline (Gap C, §9), so there is no honest number
to show. Every other
build — CFRU hacks, split-mechanics vanilla builds, any unidentified ROM — is **refused** with a
stated reason rather than given a Gen III number.

The single decision point is `CalcCapabilityPolicy`
(`app/src/main/java/com/dualdex/calculator/CalcCapabilityPolicy.kt`); the only way to turn
application state into a request is `CalcRequestBoundary`
(`app/src/main/java/com/dualdex/calculator/CalcRequestBoundary.kt`).

---

## 2. Capability matrix by mechanic

This is the inventory issue #9 asks for. "Bridge can express" asks whether **any** value of an
existing request field reproduces this build's rule, not whether the current UI happens to set it.

| # | Mechanic / state | H&S 2.0.5 (pinned) | Bridge can express | Verdict |
|---|---|---|---|---|
| 1 | Damage formula | Generation III arithmetic with modern data | partially | **INDIVIDUALLY DEMONSTRATED ONLY** — the crit multiplier and spread reduction match; the chart, category rule and modifiers do not (§3.2) |
| 2 | Move category | Per-move by default (`B_PHYSICAL_SPECIAL_SPLIT GEN_LATEST`) `[include/config/battle.h:76]`, decided by `GetBattleMoveCategory` `[src/battle_util.c:9173]` | **yes** — bridge expresses both behaviors via `move.overrides.category` (retained for PER_MOVE_SPLIT, omitted for TYPE_BASED to trigger Gen 3 type derivation); `optionStyle` is consumed by `CalcRequestBoundary` | **PLUMBED / REFUSED** — `optionStyle` selects between explicit category override and type-derived category, but H&S calculations remain refused due to Gap C type-chart incompatibility (§3.1, §9) |
| 3 | Type chart | Modern chart: Fairy present, Steel does **not** resist Ghost/Dark `[src/data/types_info.h:8]`, `:25`, `:35`, `:36` | **yes** — custom H&S type chart matrix (`hns_type_chart.json`) executed via request-local facade when `typeSystem: "hns_2_0_5"` without mutating global library state. Fairy toggle ON/OFF handled via `sPreFairyTypes` and `sFairyMoveAltTypes`. | **SUPPORTED / REFUSED (GAP C1 CLOSED)** — type chart is exact and verified in QuickJS. H&S calculations remain refused due to Gap C2 (`HNS_ABILITY_SYSTEM_NOT_MODELLED`). (§3.1, §9) |
| 4 | Species base stats / typings | Modern (`P_UPDATED_STATS`/`P_UPDATED_TYPES GEN_LATEST`) `[include/config/pokemon.h:5]`, from the pinned data pack | **yes** — authoritative overrides forwarded via `CalcDataOverrides` and consumed by `@smogon/calc` constructor (§3.3, §9) | **PLUMBED / REFUSED** — overrides are extracted and forwarded, but H&S calculations remain refused due to Gap C (§3.1, §9) |
| 5 | Move properties (power/type/category) | Explicit per move, 848 numbered moves incl. Gen IX `[src/data/moves_info.h:121]`, `[include/constants/moves.h:905]` | **yes** — authoritative power, type, and category forwarded via `CalcDataOverrides` and consumed by bridge (§3.3, §9) | **PLUMBED / REFUSED** — overrides are extracted and forwarded, but H&S calculations remain refused due to Gap C (§3.1, §9) |
| 6 | Abilities that affect damage | Full modern roster, ~80 post-Gen-III modifiers `[src/battle_util.c:6655]`, `:6989`, `:7562` | **no** | **REFUSED** — the ADV pipeline models only its Gen III list and silently ignores the rest (§6) |
| 7 | Held items that affect damage | Modern: type-boost ×1.2 `[src/data/items.h:10]`, gems ×1.3 `[src/data/items.h:9]`, Choice Specs/Life Orb/Expert Belt/Eviolite/Assault Vest `[include/constants/items.h:557]`–`:629` | **no** | **REFUSED** — item identity is not authoritative and the percentages differ (§7) |
| 8 | Critical hits | Odds are Gen 7+ (1/24 base) `[src/battle_util.c:7975]`; **multiplier ×2** (`B_CRIT_MULTIPLIER GEN_3`) `[include/config/battle.h:6]`, `[src/battle_util.c:7474]` | multiplier yes, odds no | **SUPPORTED** as a boolean crit (`isCrit`), which is what the request shape carries |
| 9 | Weather | Rain/Sun ×1.5 and ×0.5 `[src/battle_util.c:7443]`; Sand/Hail give no move-damage multiplier; Sand gives Rock SpD ×1.5 `[src/battle_util.c:7386]` | yes | **SUPPORTED** for Sun/Rain/Sand/Hail |
| 10 | Snow | Ice Defense ×1.5 `[src/battle_util.c:7389]`; Snow never chips, Hail chips 1/16 `[src/battle_end_turn.c:155]` | **no** | **REFUSED** when asked for — the ADV pipeline has no Snow concept and would compute it as *no weather* |
| 11 | Terrain | Implemented; ×1.3 (`B_TERRAIN_TYPE_BOOST GEN_LATEST`) `[src/battle_util.c:6640]` | accepted but dead | **REFUSED** when asked for — the ADV pipeline ignores `terrain` entirely |
| 12 | Reflect / Light Screen | ×0.5 singles, ×0.667 doubles `[src/battle_util.c:7544]` | yes | **SUPPORTED** |
| 13 | Multi-target reduction | Generation III value: ×0.5 for two targets (`B_MULTIPLE_TARGETS_DMG GEN_3`) `[include/config/battle.h:47]`, `[src/battle_util.c:7403]` | yes (`Doubles`) | **SUPPORTED** — this is another constant the hack deliberately keeps Gen III |
| 14 | Badge boost | Active: player-side ×1.1 Atk/SpA/Def/SpD/Speed (`B_BADGE_BOOST GEN_3`) `[include/config/battle.h:30]`, `[src/battle_util.c:9135]` | **no** | **not modelled** — see §8 |
| 15 | Move-specific mechanics (multi-hit, weight, fixed damage, Hidden Power, Return) | Modern | partially | **not modelled** beyond the ADV pipeline's own support (§8) |
| 16 | Challenge settings that change stats | No EVs `[include/global.h:309]`, Base Stat Equalizer `[:304]`, trainer IV/EV scaling `[:312]`, Max Party IVs `[:314]`, Mirror `[:307]` | **no** | **REFUSED** (§4.2) |
| 17 | Challenge settings that change the rule | `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type`, `tx_Random_TypeEffectiveness` | **partially** — `CalcRequestBoundary` consumes exact-trusted runtime snapshot into `CalcHnsRuntimeRules`; active randomizer and type-chart differences remain unmodelled | **CONSUMED / REFUSED** — runtime rules are known and unreadable blockers cleared when observed, but active unsupported rules and Gap C block calculation (§4.1, §9) |
| 18 | Legendary ability overrides | `tx_Mode_Legendary_Abilities` default **ON**, substitutes abilities for slot 0 `[src/pokemon.c:5551]`, `[src/new_game.c:147]` | no | folds into row 6 |

---

## 3. Which mechanics are individually demonstrated, and which are not

An earlier revision of this document claimed the hack's type chart "agrees with Generation III's
chart on every Generation III type pair", and concluded from that plus the critical-hit and
spread-damage constants that the ADV pipeline was "the right arithmetic" for H&S. **That claim was
wrong and is withdrawn.** The correction matters because the matrix below is the foundation any
future H&S support will be built on.

### 3.1 The type chart and Gap C1 resolution

| Matchup | Pinned H&S | `@smogon/calc` 0.11.0 at `gen: 3` | H&S Type System (`typeSystem: "hns_2_0_5"`) |
|---|---|---|---|
| Ghost → Steel | **×1** | ×0.5 | **×1** |
| Dark → Steel | **×1** | ×0.5 | **×1** |
| Fairy → Dragon/Fighting/Dark | **×2** | N/A (type absent) | **×2** |
| Dragon → Fairy | **×0 (immune)** | N/A (type absent) | **×0 (immune)** |

H&S sets `B_UPDATED_TYPE_MATCHUPS` to `GEN_9` `[include/config/battle.h:53]`, so its `STL_RS` macro
resolves to ×1.0 `[src/data/types_info.h:8]`, used by the Ghost and Dark rows
`[src/data/types_info.h:25]`, `:35`. The library's Gen 3 chart assigns ×0.5 to both.

**Gap C1 Resolution:**
DualDex extracts the exact 19x19 type effectiveness matrix deterministically from `src/data/types_info.h`
(`tools/hns-type-system/generate_hns_type_system.py`) into `tools/calc-bundler/hns_type_chart.json`.
When `typeSystem: "hns_2_0_5"` is specified on the request, `tools/calc-bundler/entry.js` constructs a
request-local duck-typed Generation facade inheriting Gen 3 arithmetic (`num = 3`) but supplying the exact
H&S type provider (`gen.types.get(...)`). Global library state is never mutated.

Additionally, runtime Fairy toggle behavior (`tx_Mode_Fairy_Types`) is modelled:
- When Fairy is ON (`fairyTypesEnabled == true`), species and moves retain their authoritative H&S Fairy typings.
- When Fairy is OFF (`fairyTypesEnabled == false`), species retype according to upstream `sPreFairyTypes` (20 species)
  and moves retype according to `sFairyMoveAltTypes` (34 moves) via `HnsFairyTypeMappings.kt`.
- Coupling with `optionStyle`: under `TYPE_BASED` (`optionStyle == 1`), the move's damage category is derived
  from its effective (retyped) type (e.g. Moonblast -> Dark -> Special; Dazzling Gleam -> Normal -> Physical).
- Host QuickJS tests in `native/tests/test_js_calc.c:check_gap_c1_type_system` verify Ghost/Dark -> Steel neutrality,
  Fairy offensive 2x, Dragon -> Fairy 0x immunity, and request isolation.

### 3.2 What is demonstrated, and what is not

Only these individual behaviours are source-and-test demonstrated:

| Behaviour | H&S (pinned source) | Calculator at `gen: 3` + `typeSystem: "hns_2_0_5"` | Status |
|---|---|---|---|
| Critical-hit multiplier | ×2 (`B_CRIT_MULTIPLIER GEN_3`) `[include/config/battle.h:6]`, applied `[src/battle_util.c:7477]` | ×2 | **matches** — pinned by `gen3_crit_doubles_the_attack_form` |
| Two-target reduction | ×0.5 (`B_MULTIPLE_TARGETS_DMG GEN_3`) `[include/config/battle.h:47]` | ×0.5 | **matches** — pinned by the existing spread-move fixtures |
| Thick Fat placement | halves the attack stat `[src/battle_util.c:7121]`, `:7191` | halves the attack form | **matches** |
| Type chart | modern (Fairy present; Steel does not resist Ghost/Dark) | modern 19x19 H&S matrix via request-local facade | **MATCHES (Gap C1 closed)** |
| Move category rule | per-move default, switchable to type-based via `optionStyle` | `move.overrides.category` handling in `entry.js` | **MATCHES (Gap A/B closed)** |
| Species & move data | modern stats, types, power from pinned pack | authoritative overrides via `CalcDataOverrides` | **MATCHES (Gap B closed)** |
| Weather, screens | see §2 rows 9, 12 | supported | **matches** |
| Abilities, items, badge boost | modern abilities (~80 modifiers), items, badge boost | Gen 3 pipeline | **does not match (Gap C2 open)** |

Matching the critical-hit, spread-damage, data overrides, and type chart does **not** establish equivalence of the whole
calculation pipeline, and this document no longer claims it does. `gen: 3` remains what the policy
sends. H&S calculations remain strictly refused (`UNSUPPORTED`) due to the remaining Gap C blocker: `HNS_ABILITY_SYSTEM_NOT_MODELLED`.

### 3.3 Three different claims that must not be conflated

The matrix in §2 uses these distinctions, and any future H&S work must keep them separate:

1. **Identity exists in pinned H&S data.** `HeartAndSoul205DataPack` can name species 1433 and move
   847, and `CalcRequestBoundary` proves a name belongs to the build.
2. **The calculator consumes that H&S record.** The plumbing gap (Gap B) is now closed:
   `CalcDataOverrides` extracts authoritative base stats, types, base power, type, and category
   from `HeartAndSoul205DataPack`, serializes them as `overrides` on `attacker`, `defender`, and
   `move`, and `tools/calc-bundler/entry.js` strictly validates and passes them to `@smogon/calc`'s
   `Pokemon` and `Move` constructors (`options.overrides`).
3. **The calculator reproduces the H&S mechanic.** It does so only where §3.2 says "matches".

**Consuming the record (2) does not imply reproducing the mechanic (3).** Even though the engine now
receives authoritative H&S base stats and move properties, H&S calculations remain **refused**
because Gap C remains open: the generation III type chart lacks Fairy and disagrees on Steel
resistances (§3.1), abilities and items differ (§6, §7), and runtime challenge settings rule toggles
(Gap A) are unconsumed by the calculator.


### 3.4 Vanilla Gen III verified set

A vanilla request is presented as *Verified* only when the running ROM is the exact build the profile
was verified against, every participant's damage-relevant state is **carried or explicitly recorded
as unknown** (`CalcInputPreparation`), the species and move names resolve in that build's data, and
no ability, held item, status, stat stage, level or field value outside the modelled sets appears.

Two exclusions are enforced or disclosed rather than merely documented:

* An incomplete **live read** can never be verified: a field the reader could not carry is reported,
  not shortened to neutral, and blocks the result (§4 of the review's R1 finding).
* The generation III **badge boost** is unmodelled for every build and the request shape cannot
  express it. It is not enforced in code, so the verified headline states the scope explicitly
  (`CalcResultPresentation.BADGE_BOOST_NOTE`) instead of leaving it to this document.

---

## 4. Heart & Soul challenge settings

`struct ChallengeSettings` is 32 bytes at `[include/global.h:253]`, a member of `SaveBlock3`
`[include/global.h:359]`, reached as `gSaveBlock3Ptr->challengeSettings` `[include/global.h:363]`.
DualDex reads this struct at runtime — `pokemon_read_challenge_settings_gba` decodes it into a typed snapshot
(`HnsChallengeSettingsSnapshot`) that the companion publishes only while the running ROM is exactly trusted,
and the runtime verification in `HNS_2_0_5_COMPATIBILITY_EVIDENCE.md` §13 observed live values (including a
challenge-menu toggle flipping exactly `tx_Mode_Fairy_Types`).

**Current status (Gap A closed):**
- **Runtime settings are observed:** whenever the ROM is exactly trusted, the snapshot is delivered to
  calculator preparation.
- **Four rule fields are now consumed:** `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type`, and
  `tx_Random_TypeEffectiveness` are mapped into `CalcHnsRuntimeRules` by `CalcRequestBoundary`.
- **`optionStyle` selects category behavior:** raw 0 (`PER_MOVE_SPLIT`) retains boundary-owned move category
  overrides, while raw 1 (`TYPE_BASED`) omits them to trigger type-based category derivation.
- **Randomizer activation is known:** observed raw 0 clears unreadable blockers; observed raw 1 blocks with
  explicit active-not-modelled limitations.
- **Unsupported mechanics still block:** live battler abilities (observed via `gBattleMons`, PR #56) and
  authoritative data overrides (PR #57) are plumbed, but the H&S type chart (Gap C) remains unmodelled,
  so all H&S calculations remain refused (`CalcSupport.UNSUPPORTED`).

Two properties make challenge settings decisive rather than a caveat:

1. **Some fields change the rule, not just the values.** They dictate category derivation, typing rules, and chart lookups.
2. **Several are ON by default, and the Mode tab is free.** `TAB_MODE` has no lock entries
   `[src/challenge_menu.c:183]`, so those rows can be flipped at any time, including mid-run. The
   defaults come from the challenge menu, not from `new_game.c`: `NewGameInitData` snapshots the
   menu's choices, clears `SaveBlock3`, and restores the snapshot `[src/new_game.c:221]`, `:235`,
   `:238`, `:239`. "The randomizer is off" therefore does **not** imply "vanilla behaviour".

### 4.1 Rule-changing fields — consumption and distinction

| Field | Default | What it changes |
|---|---|---|
| `optionStyle` | 0 = per-move split `[src/new_game.c:128]` | `GetBattleMoveCategory` `[src/battle_util.c:9183]`: when 1, the move's **type** decides physical/special, as in Generation III. Bound to the "PHYS/SP SPLIT" row `[src/challenge_menu.c:486]`, `:1973`. The *same* species, move and level has two different correct answers. |
| `tx_Mode_Fairy_Types` | 1 = ON `[src/new_game.c:146]` | When 0 the Fairy type is deleted: species revert to pre-Fairy typings via `sPreFairyTypes` `[src/pokemon.c:5734]`, and Fairy moves are retyped via `sFairyMoveAltTypes` (Moonblast → Dark) `[src/pokemon.c:5788]`. Bound to "ADD FAIRY TYPE" `[src/challenge_menu.c:456]`. |
| `tx_Random_Type` | 0 `[include/global.h:283]` | `GetSpeciesType` returns a randomized type `[src/pokemon.c:5731]`, `[include/randomizer.h:91]`. |
| `tx_Random_TypeEffectiveness` | 0 `[include/global.h:284]` | `GetTypeModifier` remaps the **attacking** type inside the chart at damage time `[src/battle_util.c:8533]`. |

**Update (Gap A closed):** DualDex now consumes these fields through `CalcRequestBoundary.resolveHnsRuntimeRules`,
converting the authoritative `HnsChallengeSettingsSnapshot` from `CompanionViewModel.challengeSettings`
into an immutable `CalcHnsRuntimeRules` model.

The calculator truthfully distinguishes three states:
1. **Unreadable / Untrusted (`*_UNREADABLE`):** When the snapshot is missing, the ROM trust is not
   exact-verified, the snapshot status is not `OBSERVED`, or an individual field is unobserved or
   out-of-domain. In this state, `CATEGORY_SPLIT_TOGGLE_UNREADABLE`, `FAIRY_TOGGLE_UNREADABLE`,
   `RANDOM_TYPES_UNREADABLE`, `RANDOM_TYPE_EFFECTIVENESS_UNREADABLE`, and `CHALLENGE_SETTINGS_UNREADABLE`
   block the calculation.
2. **Observed OFF:**
   - `optionStyle == 0` (`PER_MOVE_SPLIT`): The boundary-owned H&S move override retains its explicit
     pinned category (`category = "Physical" / "Special"`).
   - `optionStyle == 1` (`TYPE_BASED`): The boundary-owned H&S move override omits category (`category = null`),
     letting `@smogon/calc` ADV derive the category from the move's type.
   - `tx_Random_Type == 0` (raw 0): Observed OFF. No random-type blocker is added.
   - `tx_Random_TypeEffectiveness == 0` (raw 0): Observed OFF. No random effectiveness blocker is added.
   - `tx_Mode_Fairy_Types`: Observed ON (1) or OFF (0); `FAIRY_TOGGLE_UNREADABLE` is cleared.
3. **Observed ON but unsupported:**
   - `tx_Random_Type == 1`: Observed ON. Blocks calculation with `RANDOM_TYPES_ACTIVE_NOT_MODELLED`.
   - `tx_Random_TypeEffectiveness == 1`: Observed ON. Blocks calculation with `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`.
   - Fairy mode (ON or OFF): While the toggle state is now known, the H&S type chart itself is not modelled
     by the Gen 3 ADV pipeline (Fairy absent, Steel resists Ghost/Dark), so calculation remains blocked with
     `HNS_TYPE_CHART_NOT_MODELLED`.

**Every H&S calculation remains refused (`CalcSupport.UNSUPPORTED`) after Gap A.**

### 4.2 Value-changing fields

These would downgrade a calculation from *Verified* to *Approximate* if the rule fields above were
read; today they are additional supporting reasons in the same refusal.

| Field | Default | What it changes |
|---|---|---|
| `tx_Challenges_NoEVs` | 0 | Blocks EV gain `[src/pokemon.c:7808]`; EV items `[src/party_menu.c:4941]` |
| `tx_Challenges_BaseStatEqualizer` | 0 | Replaces every non-HP base stat with 100/255/500 `[src/challenge_menu.c:2359]`, `[src/pokemon.c:3750]` |
| `tx_Challenges_Mirror` / `_Thief` | 0 | Copies the enemy party over the player's `[src/battle_main.c:667]`, `:5823` |
| `tx_Challenges_TrainerScalingIVs` / `_EVs` | 0 | Rewrites opponent IVs/EVs `[src/battle_main.c:2145]`, `:2156` |
| `tx_Challenges_MaxPartyIVs` | 0 | Forces player IVs to 31 (or 30/31) `[src/pokemon.c:3232]` |
| `tx_Random_Abilities` / `tx_Random_Moves` | 0 | Rerolls ability `[src/pokemon.c:5585]` and moves `[src/pokemon.c:3954]` |
| `tx_Mode_Sturdy` | 1 | Gates Gen V+ Sturdy endure-at-1-HP `[src/battle_util.c:8186]` |
| `tx_Mode_Legendary_Abilities` | 1 | Substitutes abilities for slot 0 `[src/pokemon.c:5551]` |
| `tx_Challenges_LevelCap`, `tx_Challenges_ExpMultiplier` | 0 | Change which level/EV states are reachable at all `[src/caps.c:64]` |

**Not damage-relevant, recorded so it is not re-investigated:** `tx_Challenges_OneTypeChallenge`
only gates which species may be obtained `[src/challenge_menu.c:2441]`, `[src/starter_choose.c:370]`.
`RANDOMIZE_BASE_STATS` is declared `[include/randomizer.h:27]` but has no case in
`RandomizerFeatureEnabled` `[src/randomizer.c:58]` and is referenced nowhere else, so base stats are
never randomized. Move power, accuracy and type are never randomized either.

---

## 5. How content is proven to belong to the build

The calculator bridge selects content **by name**
(`tools/calc-bundler/entry.js:60`, `:74`, `:78`), and the engine looks that name up in its own
bundled tables without validating it against anything. Two failure modes follow, and both are
closed by the boundary:

1. **A name the engine does not carry** throws — but with an undiagnosable message
   (`"…(reading 'hp')"` for a species). It never silently substitutes vanilla data for an unknown
   species or move name.
2. **A name the engine carries with different data resolves to the engine's data.** This is the real
   silent-wrong-number path: a hack-modified species or move is computed from vanilla values, and a
   hack-only move like `Malignant Chain` resolves against the engine's own Gen IX record.

`CalcRequestBoundary` therefore requires every species and move name to resolve in the *active
build's* data (`GameDataPack.hasSpeciesByName` / `hasMoveByName`). For a build whose pinned pack
blocks global fallback — H&S 2.0.5 — that is the pinned pack and nothing else.

`HeartAndSoul205DataPack` gained an authoritative name index for this, generated by
`tools/hns-data-pack/generate_hns_data_pack.py` from the same pinned checkout (1427 species, 934
moves; `--verify` reproduces the committed file with zero drift). The index deliberately **omits
species whose forms differ in type or base stats** — `Eevee`, `Pikachu`, `Deoxys`, `Castform`,
`Terapagos`, and 90-odd others — because a name-only request cannot say which form is meant. Those
names resolve to `null` and are listed in `HeartAndSoul205DataPack.multiFormNames`, so the boundary
refuses them instead of computing one form's damage for another form's name.

---

## 6. Abilities

The 0.11.0 ADV pipeline applies a fixed, **exact-name-match** list of abilities and calls
`gen.abilities.get()` nowhere in the damage path. An ability it does not model is silently ignored —
it does not throw, and it does not fall back. The classic demonstration is a statused attacker with
an unrecognised ability string: the number changes but the ability does not. The native suite pins
both halves of this in `gen3_explicit_guts_boosts_a_statused_attacker` and
`gen3_unmodelled_ability_is_silently_ignored`.

`CalcCapabilityPolicy.GEN3_MODELLED_ABILITIES` is therefore a whitelist of what the pipeline
*actually applies*, not a list of Generation III abilities. Deliberately excluded, with reasons:

| Ability | Why it is not whitelisted |
|---|---|
| `Air Lock`, `Cloud Nine` | Recognised, but weather is only nulled for a Pokemon the engine was told is on the field (`abilityOn`), which the bridge never forwards (`entry.js:48`, `:62`) |
| `Intimidate` | Gated behind `abilityOn`, and the engine never applies the Generation III switch-in trigger itself |
| `Flash Fire`, `Plus`, `Minus` | Their boosts need the same unforwarded flag |
| `Forecast` | It rewrites Castform's typing from supplied weather, but the bridge passes a chosen ability name rather than the ability the running game actually has |

For H&S, nothing is treated as modelled: the hack uses later-generation ability implementations, so
even a name that shares a Generation III string is not the same mechanic.

**Spelling.** Ability and item names are matched leniently but *sent* canonically:
`CalcCapabilityPolicy.normaliseNames` rewrites an accepted name to the exact spelling the engine
compares against (`"GUTS"` → `"Guts"`). Without that rewrite a leniently-spelled name would be
passed through as written, silently ignored by the engine, and still reported as verified. An
unrecognised name is never rewritten — it passes through unchanged and is refused or downgraded.

Held-item names follow the same rule: `"choice band"` is accepted and sent as `"Choice Band"`.
`Sea Incense` is intentionally *not* in the type-boost list: the engine models it as its own ×1.05
Water case rather than as the generic ×1.1 type-boost item.

---

## 7. Held items

Two independent problems, either of which is disqualifying for a verified claim:

* **Identity is not authoritative for H&S.** H&S item ids are expansion ids with H&S-restyled
  display names, and its item table is not an audited source in this phase. A held-item id cannot be
  turned into a name the engine will match.
* **The percentages differ.** H&S scales type-boost items to ×1.2 `[src/data/items.h:10]` and gems
  to ×1.3 `[src/data/items.h:9]`; the ADV pipeline applies ×1.1 to the attack stat. Later-generation
  damage items (Choice Specs, Life Orb, Expert Belt, Eviolite, Assault Vest) have no ADV equivalent
  at all and would be silently inert.

For vanilla Gen III the policy whitelists exactly the items the ADV pipeline applies
(`CalcCapabilityPolicy.GEN3_MODELLED_ITEM_NAMES`); anything else downgrades a vanilla result to
*Approximate*. Note that pinch berries (Liechi/Salac/Petaya/…) and Sitrus are modelled by no
generation in this library, so they are never treated as modelled.
---

## 8. Known limitations that are not fixed here

Recorded so they are not mistaken for oversights. Each is a deliberate scope boundary.

1. **Badge boost.** Active in both H&S (`B_BADGE_BOOST GEN_3`) and vanilla Gen III, worth ×1.1 to
   the player's attacking and defensive stats. The bridge has no field for it. The verified vanilla
   claim is scoped to the unbadged state the golden fixtures encode; a badged player's real damage
   is about 10% higher than a verified result shows.
2. **Ability defaults when no ability is supplied.** The engine applies the species' first bundled
   ability when the caller omits one. For vanilla Gen III that is the same data the profile
   describes, so it is correct there. For H&S it would not be, which is one more reason H&S is
   refused.
3. **Move mechanics beyond the ADV pipeline.** Multi-hit turn-doubling, weight-based power, fixed
   damage, Hidden Power's IV-derived base power and Return/Frustration's happiness scaling are not
   modelled; the second and third also collide with the native validator's 16-roll response shape.
4. **No H&S golden damage fixtures.** Issue #9 asks for "supported H&S calculations" to have golden
   fixtures against known in-game or upstream results. This change supports **no** H&S calculations,
   so there are none to write; the H&S deliverable is the refusal above. Fixtures become possible in
   the same commit that reads the §4.1 fields and promotes H&S to *Approximate*.
5. **Snow, terrain and modern side conditions** are refused rather than approximated, because the
   ADV pipeline accepts the fields and ignores them — the worst possible failure mode. The Calc
   screen only offers Sun/Rain/Sand/Hail, so this gate is invisible in the UI today; it exists to
   stop a future screen (or the battle console) from sending a value the engine would treat as *no
   weather*.

---

## 9. How to promote H&S from refused to approximate

**Reading the challenge toggles is necessary but not sufficient.** An earlier revision of this
document treated it as the whole promotion path. It is one of three independent gaps, and §3.3 shows
why: proving a name belongs to the build does not make the engine compute from that build's record,
and matching two constants does not make it reproduce the build's mechanics.

The three gaps, in dependency order:

### Gap A — the rule is unknown (CLOSED)

**Update (issue #9, Gap A slice):** The runtime challenge rules consumption gap is now closed.
DualDex resolves authoritative runtime challenge rules from `HnsChallengeSettingsSnapshot` via
`CalcRequestBoundary.resolveHnsRuntimeRules`. When exactly trusted (`isExactRuntimeVerified == true`
and `snapshot.status == OBSERVED`), the boundary constructs `CalcHnsRuntimeRules` (`optionStyle`,
`fairyTypesEnabled`, `randomTypesEnabled`, `randomTypeEffectivenessEnabled`) and attaches it to
`DamageCalculationRequest`.

`CalcDataOverrides.kt` consumes `hnsRuntimeRules`:
- Under `optionStyle == 0` (`PER_MOVE_SPLIT`), the boundary-owned move override retains its explicit
  pinned category (`category = "Physical" / "Special"`).
- Under `optionStyle == 1` (`TYPE_BASED`), the move override category is set to `null`, allowing
  `@smogon/calc` Gen 3 ADV to derive move category from move type.

`CalcCapabilityPolicy.kt` evaluates `request.hnsRuntimeRules`:
- If missing, unreadable, or untrusted, calculation is blocked with `FAIRY_TOGGLE_UNREADABLE`,
  `CATEGORY_SPLIT_TOGGLE_UNREADABLE`, `RANDOM_TYPES_UNREADABLE`, `RANDOM_TYPE_EFFECTIVENESS_UNREADABLE`, and
  `CHALLENGE_SETTINGS_UNREADABLE`.
- If observed OFF (`tx_Random_Type == 0`, `tx_Random_TypeEffectiveness == 0`), the unreadable
  limitations are cleared without introducing active-not-modelled blockers.
- If observed ON (`tx_Random_Type == 1`, `tx_Random_TypeEffectiveness == 1`), calculation is blocked
  with `RANDOM_TYPES_ACTIVE_NOT_MODELLED` and/or `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`.
- Because the H&S type chart (Fairy type, Steel neutral to Ghost/Dark) is not yet modelled in the
  Gen 3 ADV pipeline, all H&S calculations remain blocked with `HNS_TYPE_CHART_NOT_MODELLED`.

**Why H&S calculations remain refused:**
Gap A connects challenge rules to calculator requests and capability evaluation, but all H&S calculations
remain strictly refused (`CalcSupport.UNSUPPORTED`) because Gap C remains open (type chart / mechanics
incompatibility).

Two further *input observability* facts now exist alongside Gap A, still without changing any
verdict (issue #9, live-battler slice; see §14 of the compatibility evidence):

* the **effective live ability** of an authoritative active battler is observed from
  `gBattleMons[battler].ability` and can be named against the pinned PR #54 catalogue;
* the battler's **current effective types** are observed from the same live battle state.

These close part of the “what would the battler's inputs even be?” question, but nothing wires
them to live battle state yet: `ABILITY` remains in `CalcInputPreparation.unknownFields`, live
effective types are not yet mapped to species overrides (authoritative static pack overrides are now
forwarded in Gap B below), and no ability is promoted to modelled because its catalogue name resolves
(§6). Naming an observed ID is identity bookkeeping, not mechanic support.


### Gap B — the engine does not consume H&S data (CLOSED)

**Update (issue #9, Gap B slice):** The data-consumption plumbing gap is now closed.
DualDex extracts authoritative species base stats and types, as well as move base power, type,
and category, from `HeartAndSoul205DataPack` via `CalcDataOverrides.kt`. When an H&S request passes
through `CalcRequestBoundary`, it is enriched with `attackerOverride`, `defenderOverride`, and
`moveOverride`. `DamageCalculator.kt` serializes these under `overrides` in the QuickJS JSON payload.

In `tools/calc-bundler/entry.js`, strict runtime validation (`validateSpeciesOverrides`,
`validateMoveOverrides`) verifies that stats, types, basePower, and category match schema constraints,
and passes them to `@smogon/calc`'s `Pokemon` and `Move` constructors (`options.overrides`).
The host QuickJS native test suite (`native/tests/test_js_calc.c:check_gap_b_data_overrides`)
asserts that overrides modify damage arithmetic, type effectiveness, and stat scaling.

**Category behavior under Gen 3:**
In `@smogon/calc` 0.11.0, `Move` stores `options.overrides.category`. When `category` is explicitly
provided in `overrides`, the Gen 3 ADV pipeline (`calculateADV`) uses `move.category` to select
`atk`/`def` vs `spa`/`spd`. When `category` is omitted from `overrides`, it falls back to Gen 3
type-based category derivation (`SPECIAL.includes(data.type)`).

**Why H&S calculations remain refused:**
Gap B closes the **data-consumption plumbing** only. H&S calculations remain strictly **refused**
(`UNSUPPORTED`) because Gap C remains open:
- The Gen 3 type chart lacks Fairy (`gen.types.get('Fairy')` is undefined in Gen 3, crashing on
  moves and producing `NaN` rolls against Fairy defenders).
- Steel resists Ghost and Dark in Gen 3 (`STL_RS` in H&S is neutral).
- Modern abilities and items remain unmodelled by the Gen 3 pipeline.
- Challenge settings rule toggles (Gap A) were not previously consumed.

### Gap C — the calculator does not reproduce H&S mechanics

#### Gap C1 — exact type system + Fairy toggle behavior (CLOSED)

**Update (issue #9, Gap C1 slice):** The type-system mismatch is resolved:
- The exact 19x19 H&S type chart (extracted from `src/data/types_info.h` at commit `1f42b74dff0e9fe942419845d040663dd829a973`)
  is packaged into `hns_type_chart.json`.
- When `typeSystem: "hns_2_0_5"` is specified, `entry.js` provides a request-local facade `createHnsGeneration(baseGen)`
  that substitutes the H&S type provider (`HNS_TYPES_PROVIDER`) while retaining Gen 3 arithmetic (`num = 3`).
  Ghost/Dark -> Steel is 1.0x neutral; Fairy type exists with modern affinities (2x against Dragon/Fighting/Dark,
  0.5x against Fire/Poison/Steel, 0x immunity from Dragon).
- `tx_Mode_Fairy_Types` toggle behavior:
  - Fairy ON (`fairyTypesEnabled == true`): species and moves retain H&S Fairy typings.
  - Fairy OFF (`fairyTypesEnabled == false`): species retype to pre-Fairy typings (`sPreFairyTypes`, 20 species)
    and Fairy moves retype to alternate typings (`sFairyMoveAltTypes`, 34 moves) via `HnsFairyTypeMappings.kt`.
- Coupling with `optionStyle`: under `TYPE_BASED` (`optionStyle == 1`), damage category is derived from the
  effective (retyped) move type (e.g. Moonblast -> Dark -> Special; Dazzling Gleam -> Normal -> Physical).
- Verified via host QuickJS suite (`native/tests/test_js_calc.c:check_gap_c1_type_system`), Kotlin unit tests
  (`CalcDataOverridesTest.kt`, `CalcCapabilityPolicyTest.kt`), and type-system generator tests (`test_generate_hns_type_system.py`).

**Why H&S calculations remain refused:**
Gap C1 closes the type-system gap. When challenge settings are observed, randomizers are OFF, and types are
representable, `HNS_TYPE_CHART_NOT_MODELLED` is cleared. However, H&S calculations remain strictly refused
(`CalcSupport.UNSUPPORTED`, `request == null`) by the next precise Gap C blocker: `HNS_ABILITY_SYSTEM_NOT_MODELLED` (Gap C2).

#### Gap C2 — ability system and remaining mechanics (OPEN)

H&S features ~80 modern abilities affecting damage that the Gen 3 ADV calculation pipeline does not model.
Until the ability system is modelled, all H&S calculations remain refused with `HNS_ABILITY_SYSTEM_NOT_MODELLED`.

### Then, concretely

1. Gap A is CLOSED: authoritative runtime challenge rules are consumed by `CalcRequestBoundary` into
   `CalcHnsRuntimeRules`, selecting category behavior (`PER_MOVE_SPLIT` vs `TYPE_BASED`), clearing
   unreadable limitations when observed, and blocking on unsupported active modes
   (`RANDOM_TYPES_ACTIVE_NOT_MODELLED`, `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`).
2. Gap B is CLOSED: authoritative H&S species and move data are consumed via `overrides`.
3. Gap C1 is CLOSED: exact modern type chart, Fairy toggle ON/OFF, and optionStyle category coupling.
4. Gap C2 is OPEN: ability system and remaining mechanics (`HNS_ABILITY_SYSTEM_NOT_MODELLED`).
5. Add golden fixtures for H&S calculations whose inputs are covered, verified against known in-game
   or upstream results. Only calculations that survive all gaps may reach `ESTIMATED`, and
   `VERIFIED` stays out of reach while the §4.2 fields are unread.
