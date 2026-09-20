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
| 1 | Damage formula | Generation III arithmetic with modern data | partially | **INDIVIDUALLY DEMONSTRATED ONLY** — the crit multiplier, spread reduction, category rule (Gap A/B), and type chart (Gap C1) match; modern modifiers/abilities/items do not (§3.2) |
| 2 | Move category | Per-move by default (`B_PHYSICAL_SPECIAL_SPLIT GEN_LATEST`) `[include/config/battle.h:76]`, decided by `GetBattleMoveCategory` `[src/battle_util.c:9173]` | **yes** — bridge expresses both behaviors via `move.overrides.category` (retained for PER_MOVE_SPLIT, omitted for damaging moves in TYPE_BASED to trigger Gen 3 type derivation; Status moves retain Status in both); `optionStyle` is consumed by `CalcRequestBoundary` | **PLUMBED / REFUSED** — `optionStyle` selects category behavior with Status prioritized, but H&S calculations remain refused because the generation III badge boost is unmodelled (`BADGE_BOOST_NOT_MODELLED`) and the pinned H&S damage-modifier order is not reproduced (`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`) (Gap C4a) (§3.1, §9) |
| 3 | Type chart | Modern chart: Fairy present, Steel does **not** resist Ghost/Dark `[src/data/types_info.h:8]`, `:25`, `:35`, `:36` | **yes** — custom H&S type chart matrix (`hns_type_chart.json`) executed via request-local facade when `typeSystem: "hns_2_0_5"` without mutating global library state. Fairy toggle ON/OFF handled via `sPreFairyTypes` and `sFairyMoveAltTypes`. | **MODELLED / REFUSED (GAP C1 CLOSED)** — type chart is exact and verified in QuickJS. H&S calculations remain refused because the generation III badge boost is unmodelled (`BADGE_BOOST_NOT_MODELLED`), the pinned H&S damage-modifier order is not reproduced (`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`), and mutable live battle state is not observed (`HNS_LIVE_BATTLE_STATE_NOT_MODELLED`) (Gap C4a). (§3.1, §9) |
| 4 | Species base stats / typings | Modern (`P_UPDATED_STATS`/`P_UPDATED_TYPES GEN_LATEST`) `[include/config/pokemon.h:5]`, from the pinned data pack | **yes** — authoritative overrides forwarded via `CalcDataOverrides` and consumed by `@smogon/calc` constructor (§3.3, §9) | **PLUMBED / REFUSED** — overrides are extracted and forwarded, but H&S calculations remain refused because the generation III badge boost is unmodelled (`BADGE_BOOST_NOT_MODELLED`) and the pinned H&S damage-modifier order is not reproduced (`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`) (Gap C4a) (§3.1, §9) |
| 5 | Move properties (power/type/category) | Explicit per move, 848 numbered moves incl. Gen IX `[src/data/moves_info.h:121]`, `[include/constants/moves.h:905]` | **yes** — authoritative power, type, and category forwarded via `CalcDataOverrides` and consumed by bridge (§3.3, §9) | **PLUMBED / REFUSED** — overrides are extracted and forwarded, but H&S calculations remain refused because the generation III badge boost is unmodelled (`BADGE_BOOST_NOT_MODELLED`) and the pinned H&S damage-modifier order is not reproduced (`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`) (Gap C4a) (§3.1, §9) |
| 6 | Abilities that affect damage | Full modern roster, ~80 post-Gen-III modifiers `[src/battle_util.c:6655]`, `:6989`, `:7562` | **partially** — authoritative effective abilities consumed from `gBattleMons`; strict per-ability capability gating (`PROVEN_NO_DAMAGE_EFFECT`, `MODELLED_EQUIVALENT`, `UNSUPPORTED_DAMAGE_RELEVANT`) in `HnsAbilityRegistry`. Engine default ability substitution prevented via `'(other)'`. | **CONDITIONALLY MODELLED / REFUSED (GAP C2 CLOSED)** — live read and manual abilities are verified or fail-closed. H&S calculations remain refused because the generation III badge boost is unmodelled (`BADGE_BOOST_NOT_MODELLED`) and the pinned H&S damage-modifier order is not reproduced (`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`) (Gap C4a). (§3.2, §6, §9) |
| 7 | Held items that affect damage | Modern: type-boost ×1.2 `[src/data/items.h:10]`, gems ×1.3 `[src/data/items.h:9]`, Choice Specs/Life Orb/Expert Belt/Eviolite/Assault Vest `[include/constants/items.h:557]`–`:629` | **identity yes; damage effects no** — exact item identity and the current battle item are consumed; no damage item is modelled; the static item audit is combined with a per-move item-interaction audit | **CONDITIONALLY MODELLED (GAP C3 CLOSED for an explicit, contextual subset)** — no item blocker only for `ITEM_NONE`/proven no-*ordinary*-damage items **and** a move that does not read item state; damage items fail closed with `HNS_ITEM_EFFECT_NOT_MODELLED`, and item-dependent moves (Fling, Knock Off, Acrobatics, Natural Gift, Poltergeist, Judgment, Techno Blast, Multi-Attack) fail closed with `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` (§7) |
| 8 | Critical hits | Odds are Gen 7+ (1/24 base) `[src/battle_util.c:7975]`; **multiplier ×2** (`B_CRIT_MULTIPLIER GEN_3`) `[include/config/battle.h:6]`, `[src/battle_util.c:7474]` | multiplier yes, odds no | **INDIVIDUALLY DEMONSTRATED, COMPOSED REQUEST REFUSED** — the constant is Gen III ×2 and a boolean `isCrit` carries it, but the composed H&S pipeline applies the crit step at a different point/rounding than ADV, so any crit request adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` (§10.4). The H&S crit *odds* (Gen 7+) are not modelled either |
| 9 | Weather | Rain/Sun ×1.5 and ×0.5 `[src/battle_util.c:7443]`; Sand/Hail give no move-damage multiplier; Sand gives Rock SpD ×1.5 `[src/battle_util.c:7386]` | yes | **INDIVIDUALLY DEMONSTRATED, COMPOSED REQUEST REFUSED** — the Sun/Rain/Sand/Hail constants are source-verified, but weather is composed at a different point than ADV, so any request carrying weather adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` (§10.4) |
| 10 | Snow | Ice Defense ×1.5 `[src/battle_util.c:7389]`; Snow never chips, Hail chips 1/16 `[src/battle_end_turn.c:155]` | **no** | **REFUSED** when asked for — the ADV pipeline has no Snow concept and would compute it as *no weather* |
| 11 | Terrain | Implemented; ×1.3 (`B_TERRAIN_TYPE_BOOST GEN_LATEST`) `[src/battle_util.c:6640]` | accepted but dead | **REFUSED** when asked for — the ADV pipeline ignores `terrain` entirely |
| 12 | Reflect / Light Screen | ×0.5 singles, ×0.667 doubles `[src/battle_util.c:7544]` | yes | **INDIVIDUALLY DEMONSTRATED, COMPOSED REQUEST REFUSED** — the ×0.5 / ×0.667 constants are source-verified, but screens are composed at a different point than ADV, so a request with Reflect/Light Screen adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` (§10.4) |
| 13 | Multi-target reduction | Generation III value: ×0.5 for two targets (`B_MULTIPLE_TARGETS_DMG GEN_3`) `[include/config/battle.h:47]`, `[src/battle_util.c:7403]` | yes (`Doubles`) | **INDIVIDUALLY DEMONSTRATED, COMPOSED REQUEST REFUSED** — ×0.5 is another constant the hack deliberately keeps Gen III and is independently demonstrated, but a Doubles request adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` because the spread step is composed at a different point (§10.4) |
| 14 | Badge boost | Active: player-side ×1.1 Atk/SpA/Def/SpD/Speed (`B_BADGE_BOOST GEN_3`) `[include/config/battle.h:30]`, `[src/battle_util.c:9135]`, eligibility-gated `[src/battle_util.c:9143]` | **no** — no badge-state field and no authoritative flag reader | **not modelled** — `BADGE_BOOST_NOT_MODELLED` (audited in §10.2) |
| 15 | Move-specific mechanics (multi-hit, weight, fixed damage, Hidden Power, Return) | Modern | **gated by effect ID** | **CONDITIONALLY GATED (Gap C4a)** — `HnsMoveMechanicsRegistry` allows only the source-proven ordinary `EFFECT_HIT` subset; every other effect fails closed with `HNS_MOVE_MECHANICS_NOT_MODELLED` (§10.3) |
| 16 | Challenge settings that change stats | No EVs `[include/global.h:309]`, Base Stat Equalizer `[:304]`, trainer IV/EV scaling `[:312]`, Max Party IVs `[:314]`, Mirror `[:307]` | partly | **CLASSIFIED (Gap C4a)** — value-changing fields that only alter stored values are captured downstream; Base Stat Equalizer and Random Moves block precisely (§10.1) |
| 17 | Challenge settings that change the rule | `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type`, `tx_Random_TypeEffectiveness` | **yes** — `CalcRequestBoundary` consumes exact-trusted runtime snapshot into `CalcHnsRuntimeRules`; exact type chart and Fairy toggle modelled (Gap C1); active randomizers block | **CONSUMED / REFUSED** — runtime rules are known and unreadable blockers cleared when observed, but active unsupported rules and the C4a mechanics/arithmetic blockers keep calculation refused (§4.1, §10) |
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
- Coupling with `optionStyle`: under `TYPE_BASED` (`optionStyle == 1`), status moves retain `Status` (Status wins before
  `optionStyle` in H&S `GetBattleMoveCategory`), while non-status moves derive damage category from their effective
  (retyped) type (e.g. Moonblast -> Dark -> Special; Dazzling Gleam -> Normal -> Physical).
- Host QuickJS tests in `native/tests/test_js_calc.c:check_gap_c1_type_system` verify Ghost/Dark -> Steel neutrality,
  Fairy offensive 2x, Dragon -> Fairy 0x immunity, Charm status category preservation, and request isolation.

### 3.2 What is demonstrated, and what is not

Only these individual behaviours are source-and-test demonstrated:

> **"matches" below is an *isolated-behaviour* finding, never a support verdict.** A composed H&S
> request that exercises the critical-hit, two-target, Thick Fat, weather, or screen behaviour is
> currently **refused**: the H&S modifier order/rounding differs from ADV, so the request adds
> `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` (§10.4). The individual constant remains demonstrated and
> visible; the composed calculation is not supported.

| Behaviour | H&S (pinned source) | Calculator at `gen: 3` + `typeSystem: "hns_2_0_5"` | Status |
|---|---|---|---|
| Critical-hit multiplier | ×2 (`B_CRIT_MULTIPLIER GEN_3`) `[include/config/battle.h:6]`, applied `[src/battle_util.c:7477]` | ×2 | **isolated match** — pinned by `gen3_crit_doubles_the_attack_form`; a composed crit request is refused (§10.4) |
| Two-target reduction | ×0.5 (`B_MULTIPLE_TARGETS_DMG GEN_3`) `[include/config/battle.h:47]` | ×0.5 | **isolated match** — pinned by the existing spread-move fixtures; a composed Doubles request is refused (§10.4) |
| Thick Fat placement | halves the attack stat `[src/battle_util.c:7121]`, `:7191` | halves the attack form | **matches** |
| Type chart | modern (Fairy present; Steel does not resist Ghost/Dark) | modern 19x19 H&S matrix via request-local facade | **MATCHES (Gap C1 closed)** |
| Move category rule | per-move default, switchable to type-based via `optionStyle` | `move.overrides.category` handling in `entry.js` | **MATCHES (Gap A/B closed)** |
| Abilities (supported subset) | Keen Eye, Insomnia, None (`PROVEN_NO_DAMAGE_EFFECT`) | Gen 3 pipeline + `resolveAbility` guard in `entry.js` | **MATCHES (Gap C2 closed)** — zero move-damage effect in H&S battle engine; unmodelled/divergent abilities fail-closed via `HNS_ABILITY_EFFECT_NOT_MODELLED` (§6) |
| Abilities (temporarily unsupported) | Guts, Thick Fat, Huge Power, Pure Power, starter pinch abilities, modern abilities | Gen 3 pipeline | **does not match** — modifier composition (compound fixed-point multiplier vs ADV sequential floor) and stat-stage application order (stages before abilities vs abilities before stages) diverge; blocked fail-closed by `HNS_ABILITY_EFFECT_NOT_MODELLED` (§6) |
| Held items (Gap C3) | exact H&S item identity + current battle item; type-boost ×1.2, gems ×1.3, modern items | identity consumed; no damage item modelled; static item audit combined with a move-interaction audit | **CONDITIONALLY MODELLED (GAP C3 CLOSED for an explicit, contextual subset)** — `ITEM_NONE` and a small source-proven no-ordinary-damage set clear the item blockers only for an item-independent move; every damage-relevant item fails closed with `HNS_ITEM_EFFECT_NOT_MODELLED`; every item-dependent move fails closed with `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED`; unreadable current item is `HNS_EFFECTIVE_ITEM_UNREADABLE` (§7) |
| Badge boost | player-side ×1.1 stats | not modelled | **does not match** — see §8 |

Matching the critical-hit, spread-damage, data overrides, type chart, and audited abilities does **not** establish
equivalence of the whole calculation pipeline, and this document no longer claims it does. `gen: 3` remains what the policy
sends. H&S calculations remain strictly refused (`UNSUPPORTED`) due to the remaining Gap C blockers: `BADGE_BOOST_NOT_MODELLED` (the generation III badge boost, §8), `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` (the audited modifier-order/staged-stat divergence, §10.4), and `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` (mutable live battle state, §10.5).

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
receives authoritative H&S base stats, move properties, exact modern type chart matchups (Gap C1 closed),
consumed runtime challenge settings (Gap A closed), and authoritative effective abilities with conditional
capability gating (Gap C2 closed), H&S calculations remain **refused** because the rest of Gap C remains open:
the generation III badge boost has no equivalent in the request shape (§8), blocked fail-closed by
`BADGE_BOOST_NOT_MODELLED` (Gap C4).


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

**Current status (Gap A and Gap C1 closed):**
- **Runtime settings are observed:** whenever the ROM is exactly trusted, the snapshot is delivered to
  calculator preparation.
- **Four rule fields are now consumed:** `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type`, and
  `tx_Random_TypeEffectiveness` are mapped into `CalcHnsRuntimeRules` by `CalcRequestBoundary`.
- **`optionStyle` selects category behavior:** raw 0 (`PER_MOVE_SPLIT`) retains boundary-owned move category
  overrides. Raw 1 (`TYPE_BASED`) retains `Status` for status moves (Status wins before `optionStyle` in H&S),
  while omitting category for damaging moves to trigger type-based category derivation.
- **Randomizer activation is known:** observed raw 0 clears unreadable blockers; observed raw 1 blocks with
  explicit active-not-modelled limitations.
- **Unsupported mechanics still block:** live battler abilities (observed via `gBattleMons`, PR #56),
  authoritative data overrides (PR #57), runtime rules (PR #61), the exact H&S type chart (Gap C1, PR #62),
  authoritative effective abilities with conditional capability gating (Gap C2), and exact current held-item
  identity with conditional item capability (Gap C3) are plumbed, but the generation III badge boost remains
  unmodelled, so all H&S calculations remain refused (`CalcSupport.UNSUPPORTED`).

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
2. **Observed and modelled:**
   - `optionStyle == 0` (`PER_MOVE_SPLIT`): The boundary-owned H&S move override retains its explicit
     pinned category (`category = "Physical" / "Special" / "Status"`).
   - `optionStyle == 1` (`TYPE_BASED`): Status moves retain `"Status"` (Status wins before `optionStyle` in H&S),
     while non-status moves omit category (`category = null`), letting the engine derive Physical or Special
     from the effective move type.
   - `tx_Mode_Fairy_Types`: Observed ON (1) or OFF (0); species and moves are retyped according to H&S source mappings
     when OFF (`sPreFairyTypes`, `sFairyMoveAltTypes`), and `typeSystem: "hns_2_0_5"` selects the modern 19x19 type chart (Gap C1 closed).
     Under exact trust, observed rules, randomizers OFF, representable types, and `request.typeSystem == "hns_2_0_5"`,
     `HNS_TYPE_CHART_NOT_MODELLED` is cleared.
   - `tx_Random_Type == 0` (raw 0): Observed OFF. No random-type blocker is added.
   - `tx_Random_TypeEffectiveness == 0` (raw 0): Observed OFF. No random effectiveness blocker is added.
3. **Observed ON but unsupported:**
   - `tx_Random_Type == 1`: Observed ON. Blocks calculation with `RANDOM_TYPES_ACTIVE_NOT_MODELLED`.
   - `tx_Random_TypeEffectiveness == 1`: Observed ON. Blocks calculation with `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`.

**Every H&S calculation remains refused (`CalcSupport.UNSUPPORTED`) after Gap A, C1, C2, C3, and C4a because the generation III badge boost remains unmodelled (`BADGE_BOOST_NOT_MODELLED`), the pinned H&S damage-modifier order and staged-stat rounding are not reproduced (`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`), and mutable live battle state is not authoritatively observed (`HNS_LIVE_BATTLE_STATE_NOT_MODELLED`).**

### 4.2 Value-changing fields

The rule fields above (`optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_*`) are read and consumed.
The value-changing challenge settings below modify participant stats, moves, or abilities directly.
**Gap C4a classified every one of them** (see §10.1): most are captured downstream because the
calculator consumes the final observed level / IV / EV / ability / party values, and only Base Stat
Equalizer and Random Moves need an independent blocker.

| Field | Default | What it changes | C4a disposition |
|---|---|---|---|
| `tx_Challenges_NoEVs` | 0 | Blocks EV gain `[src/pokemon.c:7808]`; EV items `[src/party_menu.c:4941]` | captured downstream (EVs are request values) |
| `tx_Challenges_BaseStatEqualizer` | 0 | Replaces every non-HP base stat with 100/255/500 `[src/challenge_menu.c:2359]`, `[src/pokemon.c:3750]` | **blocker** `HNS_BASE_STAT_EQUALIZER_NOT_MODELLED` when nonzero |
| `tx_Challenges_Mirror` / `_Thief` | 0 | Copies the enemy party over the player's `[src/battle_main.c:667]`, `:5823` | captured downstream (the copied party is observed) |
| `tx_Challenges_TrainerScalingIVs` / `_EVs` | 0 | Rewrites opponent IVs/EVs `[src/battle_main.c:2145]`, `:2156` | captured downstream (observed IVs/EVs) |
| `tx_Challenges_MaxPartyIVs` | 0 | Forces player IVs to 31 (or 30/31) `[src/pokemon.c:3232]` | captured downstream (observed IVs) |
| `tx_Random_Abilities` | 0 | Rerolls ability `[src/pokemon.c:5585]` | captured downstream (effective ability from `gBattleMons`) |
| `tx_Random_Moves` | 0 | Rerolls learned moves `[src/pokemon.c:3954]` | **blocker** `HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED` when ON |
| `tx_Mode_Sturdy` | 1 | Gates Gen V+ Sturdy endure-at-1-HP `[src/battle_util.c:8186]` | irrelevant (Sturdy is not in the supported ability set) |
| `tx_Mode_Legendary_Abilities` | 1 | Substitutes abilities for slot 0 `[src/pokemon.c:5551]` | captured downstream (effective ability from `gBattleMons`) |
| `tx_Challenges_LevelCap`, `tx_Challenges_ExpMultiplier` | 0 | Change which level/EV states are reachable at all `[src/caps.c:64]` | captured downstream (observed level) |

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

### 6.1 Generation III Modelled Abilities
`CalcCapabilityPolicy.GEN3_MODELLED_ABILITIES` is a whitelist of what the ADV pipeline
*actually applies*, not a list of Generation III abilities. Deliberately excluded, with reasons:

| Ability | Why it is not whitelisted |
|---|---|
| `Air Lock`, `Cloud Nine` | Recognised, but weather is only nulled for a Pokemon the engine was told is on the field (`abilityOn`), which the bridge never forwards (`entry.js:48`, `:62`) |
| `Intimidate` | Gated behind `abilityOn`, and the engine never applies the Generation III switch-in trigger itself |
| `Flash Fire`, `Plus`, `Minus` | Their boosts need the same unforwarded flag |
| `Forecast` | It rewrites Castform's typing from supplied weather, but the bridge passes a chosen ability name rather than the ability the running game actually has |

### 6.2 Heart & Soul 2.0.5 Ability Capability (Gap C2 Closed)

H&S features ~80 post-Gen-III abilities affecting damage. DualDex replaces the blanket statement
that the H&S ability system is unmodelled with a strict per-ability and per-participant capability decision:

1. **Authoritative Runtime Effective Ability Input & Boundary Slot Provenance:**
   Effective abilities are read live from `gBattleMons[battler].ability` (PR #56) and delivered as
   `BattlerRuntimeObservation`. `CalcParticipantPresenter` maps this observation to `EffectiveAbilityResolution`:
   - Active-party-slot matching: The player's live ability is accepted **only** when the selected party index
     matches the observed `partySlot`. The opponent's live ability is accepted **only** when the observed enemy
     party slot matches `activeEnemySlot`. Mismatches leave `ability = null` with `CalcInputField.ABILITY` in
     `unknownFields`.
   - Central boundary slot provenance binding: `CalcPokemonInput` carries `partySlot` provenance.
     `CalcRequestBoundary.reconcileParticipantAbility` directly verifies `participant.partySlot == observation.state.partySlot`.
     Any slot mismatch or missing provenance strips caller-supplied abilities to `null` and marks `ABILITY` unknown.
   - Single snapshot per recalculate: `CalcTabScreenView.recalculate()` captures `playerBattlerState` and
     `enemyBattlerState` once per cycle, eliminating race conditions across presenter and boundary passes.
   - Authoritative numeric ID drives capability verdict: For live observations, capability is determined strictly
     from the authoritative runtime numeric `abilityId` (`HnsAbilityRegistry.classify(abilityId)`). The catalogue
     display name (`abilityIdentity.name`) is preserved for UI display and diagnostics, but never determines capability.
     A malformed observation masquerading as supported under a false name fails closed via `abilityId`.
   - Domain range check: `abilityId` is directly range-checked against the pinned ability domain (`0..ABILITY_ID_MAX`,
     i.e. 0..310) at native array decoding, presenter resolution, boundary reconciliation, and policy evaluation.
   - Faint/replacement/unavailable windows, `AMBIGUOUS` (doubles), and `OBSERVED_INVALID` fail closed to unknown
     ability (`HNS_EFFECTIVE_ABILITY_UNREADABLE`).
   - Anti-spoofing in `CalcRequestBoundary`: Live-read participants enforce boundary ownership; caller-supplied
     abilities cannot override or fabricate authoritative observations.

2. **Per-Ability Capability Audit (`HnsAbilityRegistry`):**
   - **Supported in C2:** `PROVEN_NO_DAMAGE_EFFECT` (`ABILITY_NONE`, `KEEN EYE`, `INSOMNIA`): Proven to have zero move-damage effect in
     the H&S battle engine. No ability blocker is added.
   - **Temporarily Unsupported:** `UNSUPPORTED_DAMAGE_RELEVANT` (`GUTS`, `THICK FAT`, `HUGE POWER`, `PURE POWER`, starter pinch abilities,
     and modern abilities): While isolated multipliers match for some Gen 3 abilities, H&S fixed-point ability composition
     and stat-stage ordering diverge from ADV:
     - H&S combines ability multipliers together in fixed-point (`UQ_4_12`) and applies stat stages *before* ability multipliers,
       whereas ADV applies ability modifiers sequentially with intermediate flooring *before* stat stages.
     - Example: with raw Attack 105, a statused Guts attacker vs Thick Fat defender produces effective Attack 79 in H&S
       (combined modifier $1.5 \times 0.5 = 0.75$ applied once) versus 78 in ADV ($\lfloor 105/2 \rfloor = 52 \rightarrow \lfloor 52 \times 1.5 \rfloor = 78$).
     - Non-neutral stat stages compound this divergence.
     - Starter pinch abilities modify Attack stat in H&S vs Base Power in ADV (17,750 diverging damage spreads).
     - All damage-relevant abilities remain strictly fail-closed with `HNS_ABILITY_EFFECT_NOT_MODELLED` until the damage-order
       and rounding layer is modelled.

3. **Prevention of `@smogon/calc` Default Ability Substitution:**
   `@smogon/calc`'s `Pokemon` constructor defaults missing or `"None"` abilities to `species.abilities[0]`.
   In H&S, this would silently grant abilities (e.g. giving Machamp Guts when it has No Guard or None).
   In `tools/calc-bundler/entry.js`, `resolveAbility()` maps omitted, empty, `"None"`, or `"(other)"` abilities
   to `'(other)'` when `typeSystem === 'hns_2_0_5'`, preventing default substitution. Pinned in native tests
   (`native/tests/test_js_calc.c:check_gap_c2_abilities`).

4. **Spelling and Normalization:**
   For vanilla, ability and item names are matched leniently but sent canonically: `CalcCapabilityPolicy.normaliseNames`
   rewrites accepted names to exact Title Case (`"GUTS"` → `"Guts"`, `"HUGE POWER"` → `"Huge Power"`).
   Unmodelled names are left intact and blocked. `Sea Incense` is intentionally *not* in the type-boost list:
   the engine models it as its own ×1.05 Water case rather than as the generic ×1.1 type-boost item.
   For H&S, item capability is decided by the exact numeric item ID, never by a name, and the authorized
   request omits the item entirely because no H&S damage item is modelled (§7).

---

## 7. Held items (Gap C3 closed for an explicit, contextual subset)

Final item capability is **contextual**: it is the combination of the static item audit
(`HnsItemRegistry`) with the current move/item interaction audit
(`HnsMoveItemInteractionRegistry`). An item's own hold effect can be proven harmless for the ordinary
damage path and still determine the result of a move that reads item identity, presence or absence, so
the two audits are never collapsed.

H&S item identity is now exact and independent of the generic expansion table:

* **Exact source-derived catalogue.** `tools/hns-items/generate_hns_items.py` preprocesses the pinned
  build's own `src/item.c` translation unit and derives, from `include/constants/items.h` (`enum Item`)
  and `src/data/items.h` (`gItemsInfo[]`):
  - the exact item domain: `ITEM_NONE = 0` … `ITEM_ID_MAX = 900` (`ITEMS_COUNT = 901`);
  - every canonical `ITEM_*` symbol, its exact `gItemsInfo` display name, and its compiled
    `holdEffect` / `holdEffectParam`.
  The generated Kotlin artifact is `app/src/main/java/com/dualdex/pokemon/hns/Hns205ItemCatalogue.kt`.
  Aliases in the enum (e.g. `ITEM_ENERGYPOWDER = ITEM_ENERGY_POWDER`) resolve to the one canonical
  table identity and never produce a second entry; an unresolved alias or a missing table entry is a
  hard generation error. `ItemDatabase.expansionMap` is **never** H&S authority.
* **Current battle item, not stored party item.** For a live active battler the authority is the engine's
  own current item word, `gBattleMons[battler].item`. The battle engine rewrites that word when an item is
  consumed (`[src/battle_script_commands.c:6607]`), knocked off (`[src/battle_move_resolution.c:3055]`),
  stolen (`[src/battle_script_commands.c:2227]`, `:2239`), swapped (Trick/Switcheroo,
  `[src/battle_script_commands.c:9818-9819]`) or flung. The controller also pushes in-battle item changes
  back into the party record immediately through `REQUEST_HELDITEM_BATTLE`
  (`[src/battle_controllers.c:1804-1806]` writes `MON_DATA_HELD_ITEM`; emitted on consume
  `[src/battle_script_commands.c:6611]`, Knock Off `[src/battle_move_resolution.c:3063]`, steal
  `[src/battle_script_commands.c:2242-2249]`, Trick/Switcheroo `[src/battle_script_commands.c:9824-9827]`
  and Fling `[src/battle_util.c:10216]`), so `ParsedPokemon.heldItem` is **not** guaranteed to be frozen
  until battle end; it is a separate, asynchronously-updated copy, while `gBattleMons[battler].item` is the
  synchronous battle-engine authority. The current state wins whenever the participant is the authoritative
  active battler.
  The damage-path derivation is `CalculateMoveDamage → GetBattlerHoldEffect → GetItemHoldEffect →
  gItemsInfo[...].holdEffect` and `GetBattlerHoldEffectParam` (`[src/battle_util.c:8231-8234]`,
  `:5813-5855]`, `[src/item.c:860-867]`).
* **Precedence rules.** Active player/enemy slot match → current battle item, including an authoritative
  `ITEM_NONE` that overrides a stale nonzero party item. Bench player → exact parsed party held-item ID
  (no `gBattleMons` observation required). Slot mismatch for the opponent, faint/replacement windows,
  doubles ambiguity, and unverified reads → unreadable current item, never a party fallback. The
  `activeBattle` boolean is a hint, not the authority: any supplied runtime observation is itself battle
  evidence, so a raw LIVE_READ caller cannot pass `activeBattle = false` while handing over a current
  battler and thereby downgrade to the party item.
* **Numeric-ID capability.** `HnsItemRegistry.classify(itemId)` is the capability authority; a display
  name can never authorize capability, and an unknown/out-of-domain ID is `UNCLASSIFIED`. The registry
  resolves its own entries to IDs through the generated catalogue at construction, so a stale registry
  entry fails at class initialisation instead of silently degrading.
* **Contextual interaction audit.** A move whose damage calculation reads held-item state is audited
  separately by `HnsMoveItemInteractionRegistry`, keyed by the exact pack's numeric move ID. Because no
  item-dependent interaction is modelled, every such move adds
  `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` and no item-dependent request reaches the engine. This is
  required, not optional: the authorized request strips every supported item before `@smogon/calc`
  (`HnsItemRegistry.engineItemName` returns null), which destroys exactly the input Fling/Knock
  Off/Acrobatics need, so the move must be refused first rather than allowed to no-op.

### 7.1 Supported static subset (no item blocker for item-independent moves)

This table is the **static** half of the contextual decision. It proves only that the item's own hold
effect does not touch the ordinary damage path; a request using any of these items is still refused if
the selected move is item-dependent (§7.2).

| Item | H&S numeric ID | Identity source | H&S source effect | ADV behavior | DualDex category | Reason |
|---|---|---|---|---|---|---|
| `ITEM_NONE` | 0 | `gItemsInfo[0]` | none | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Authoritatively no held item. |
| Exp. Share | 461 | `gItemsInfo` | `HOLD_EFFECT_EXP_SHARE` (EXP only, `[src/battle_script_commands.c:11969-11987]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | No crit/power/type/stat/survival/HP/status/speed interaction. Fling-gated like every other item (§7.2). |
| Soothe Bell | 463 | `gItemsInfo` | `HOLD_EFFECT_FRIENDSHIP_UP` (`[src/pokemon.c:7777]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Friendship only. Fling power 10. |
| Amulet Coin | 466 | `gItemsInfo` | `HOLD_EFFECT_DOUBLE_PRIZE` (`[src/battle_main.c:3189]`, `[src/battle_hold_effects.c:48]`, `:1055]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Prize money only. Fling power 30. |
| Cleanse Tag | 467 | `gItemsInfo` | `HOLD_EFFECT_REPEL` (`[src/wild_encounter.c:1334]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Wild encounters only. Fling power 30. |
| Lucky Egg | 470 | `gItemsInfo` | `HOLD_EFFECT_LUCKY_EGG` (EXP ×1.5, `[src/battle_script_commands.c:11982]`) | none | `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` | Earned EXP only. Fling power 30. |

### 7.2 Move/item interaction audit (the contextual half)

`HnsMoveItemInteractionRegistry` audits every pinned damage-path read of held-item state. Every move
below is present in the exact H&S pack and is blocked with `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED`
because this calculator does not reproduce the interaction. The lookup key is the pack's numeric move
ID, so a moved or renamed move fails a test rather than silently dropping out of the gate.

| Move | H&S numeric ID | Interaction | Pinned source | Effect on damage |
|---|---|---|---|---|
| Fling | 374 | attacker item identity | `CalcMoveBasePower EFFECT_FLING` `[src/battle_util.c:6344-6346]` | `basePower = GetFlingPowerFromItemId(gBattleMons[battlerAtk].item)`; harmless items are flingable (Soothe Bell 10; Amulet Coin / Cleanse Tag / Lucky Egg / Exp. Share 30). |
| Natural Gift | 363 | attacker item identity | `[src/battle_util.c:6395-6397]`, `[src/battle_main.c:6327-6330]` | base power and **type** derive from the attacker's held berry. |
| Acrobatics | 512 | attacker item absence | `[src/battle_util.c:6421-6424]` | doubles base power when `gBattleMons[battlerAtk].item == ITEM_NONE`; therefore even `ITEM_NONE` is not context-free. |
| Knock Off | 282 | defender item presence | `[src/battle_util.c:6619-6623]` | ×1.5 when the defender holds a removable item. |
| Poltergeist | 737 | defender item presence | `[src/battle_move_resolution.c:1301-1305]` | fails outright when the defender holds no item. |
| Judgment | 449 | attacker item identity | `GetDynamicMoveType EFFECT_CHANGE_TYPE_ON_ITEM` `[src/battle_main.c:6284-6286]` | the held plate selects the move type. |
| Techno Blast | 546 | attacker item identity | `[src/battle_main.c:6284-6286]` | the held Drive selects the move type. |
| Multi-Attack | 672 | attacker item identity | `[src/battle_main.c:6284-6286]` | the held Memory selects the move type. |

Deliberately excluded, after auditing the whole damage path: `Weather Ball` (its only item read is the
Utility Umbrella hold effect at `[src/battle_main.c:6212-6240]`, and no supported item has that hold
effect); Low Kick / Heat Crash (weight only via the Float Stone hold effect,
`GetBattlerWeight` `[src/battle_util.c:6053-6087]`, and Float Stone is refused statically);
Pluck/Bug Bite/Thief/Covet (the item is moved after the damage formula); Sucker Punch (it reads the
defender's chosen move, not an item); and the gem/plate/choice/pinch-berry hold effects (they multiply
an ordinary move and are already refused by the static audit).

### 7.3 Representative refused items (fail closed with `HNS_ITEM_EFFECT_NOT_MODELLED`)

| Item | H&S numeric ID | H&S source effect | ADV behavior | DualDex category | Reason |
|---|---|---|---|---|---|
| Charcoal (and the other traditional type boosters) | 426 | ×1.2 to base power (`TYPE_BOOST_PARAM = 20`, `[src/battle_util.c:6808]`, `:6839-6843]`, `:6871]`) | ×1.1 applied to the attack stat | `UNSUPPORTED_DAMAGE_RELEVANT` | Multiplier and application stage differ. |
| Choice Band | 442 | ×1.5 post-stage Attack, composed in fixed point (`[src/battle_util.c:7177-7180]`, `:7191]`) | sequential integer floors | `UNSUPPORTED_DAMAGE_RELEVANT` | Modifier ordering/composition diverge off neutral stages. |
| Choice Specs / Choice Scarf | 443 / 444 | same composition | special / Speed only | `UNSUPPORTED_DAMAGE_RELEVANT` | Same ordering concern; Scarf changes speed-dependent power/order. |
| Light Ball | 392 | ×2 Atk/SpA for Pikachu (`[src/battle_util.c:7173-7176]`) | ×2 Attack | `UNSUPPORTED_DAMAGE_RELEVANT` | H&S condition and composition not proven equal. |
| Thick Club | 394 | ×2 physical Attack for Cubone/Marowak (`[src/battle_util.c:7165-7168]`) | ×2 Attack | `UNSUPPORTED_DAMAGE_RELEVANT` | Not proven ADV-equivalent in H&S composition. |
| Deep Sea Tooth / Scale | 399 / 398 | ×2 SpA / SpD for Clamperl (`[src/battle_util.c:7169-7172]`, `:7353-7356]`) | ×2 | `UNSUPPORTED_DAMAGE_RELEVANT` | Not proven ADV-equivalent. |
| Soul Dew | 400 | ×1.2 Psychic/Dragon base power for the Lati twins (`[src/battle_util.c:6833-6838]`) | ×1.5 SpD (ADV) | `UNSUPPORTED_DAMAGE_RELEVANT` | H&S and ADV semantics differ. |
| Life Orb / Expert Belt | 479 / 477 | ×1.3 after the roll + 1/10 recoil / ×1.2 on super-effective hits (`[src/battle_util.c:7673-7674]`, `:7669-7671]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Post-Gen-III items. |
| Muscle Band / Wise Glasses | 475 / 476 | ≈×1.1 physical / special base power (`[src/battle_util.c:6813-6819]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Post-Gen-III items. |
| Eviolite / Assault Vest | 494 / 503 | ×1.5 Def / SpD (`[src/battle_util.c:7361-7373]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Evolution state is not in the request shape. |
| Normal Gem / Fire Gem | 339 / 340 | ×1.3 matching-type base power and consumed (`[src/battle_util.c:6633-6634]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Gem consumption state is not modelled. |
| Occa Berry | 550 | ×0.5 super-effective Fire damage and consumed (`[src/battle_util.c:7686-7696]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | Consumption state is not modelled. |
| Focus Sash / Focus Band | 481 / 469 | survive at 1 HP (Sash consumed) (`[src/battle_util.c:8193-8206]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | KO presentation would be wrong. |
| Leftovers / Shell Bell / Rocky Helmet | 472 / 473 / 496 | between-turn heal / heal on damage / recoil on contact (`[src/battle_hold_effects.c:642-656]`, `:536-555]`, `:245-262]`) | none | `UNSUPPORTED_DAMAGE_RELEVANT` | KO/HP presentation would be wrong. |

`ITEM_NONE` and the supported subset above pass the **static** item gate for an item-independent move;
every other in-domain item is `UNCLASSIFIED` by default and therefore fails closed with
`HNS_ITEM_EFFECT_NOT_MODELLED`. Independently, any request whose move is item-dependent (§7.2) fails
closed with `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED`, even when the item itself is `ITEM_NONE` or a
supported static item. An observed current item that cannot be authoritatively read is
`HNS_EFFECTIVE_ITEM_UNREADABLE`, and a manual name that does not resolve in the exact H&S catalogue is
`HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE`.

Because no H&S item's damage effect is modelled, the engine request omits the item entirely for every
supported participant (`HnsItemRegistry.engineItemName` returns null). This is safe: the QuickJS host
suite proves an omitted item and `"None"` are the same calculation, and that a name the engine does not
model is a silent no-op, while a name it does model changes damage — which is exactly why a raw H&S
source name must never be forwarded.

For vanilla Gen III the policy whitelists exactly the items the ADV pipeline applies
(`CalcCapabilityPolicy.GEN3_MODELLED_ITEM_NAMES`); anything else downgrades a vanilla result to
*Approximate*. The H&S catalogue and numeric item capability never leak into a vanilla calculation.
Note that pinch berries (Liechi/Salac/Petaya/…) and Sitrus are modelled by no generation in this
library, so they are never treated as modelled.

---


## 8. Known limitations that are not fixed here

Recorded so they are not mistaken for oversights. Each is a deliberate scope boundary.

1. **Badge boost (audited in §10.2, still blocked).** Active in H&S (`B_BADGE_BOOST GEN_3`), worth
   ×1.1 to the player's attacking and defensive stats through an eligibility-gated, UQ4.12-composed
   modifier. DualDex does not read the authoritative badge flag state from save memory, so it cannot
   prove the live badge state and refuses every H&S request with `BADGE_BOOST_NOT_MODELLED`. The
   verified vanilla claim is scoped to the unbadged state the golden fixtures encode.
2. **Ability defaults when no ability is supplied.** The engine applies the species' first bundled
   ability when the caller omits one. For vanilla Gen III that is the same data the profile
   describes, so it is correct there. For H&S, `resolveAbility()` in `entry.js` maps omitted/empty/None
   to `'(other)'` to prevent default substitution, while DualDex policy strictly refuses unmodelled abilities.
3. **Move mechanics beyond the ADV pipeline (gated in §10.3).** Multi-hit turn-doubling, weight-based
   power, fixed damage, Hidden Power's IV-derived base power, Return/Frustration's happiness scaling
   and the rest of the special-effect families are not modelled. Gap C4a now derives each move's
   exact `enum BattleMoveEffects` value from the pinned source and refuses every move outside the
   source-proven ordinary subset with `HNS_MOVE_MECHANICS_NOT_MODELLED`; it is no longer an implicit
   assumption that an unclassified move is ordinary.
4. **Ordinary-damage modifier ordering (audited in §10.4, blocked).** The bare base formula matches
   the host exactly, but H&S applies the random roll *before* STAB, type effectiveness, burn and
   screens and composes each modifier with UQ4.12 half-down, while `@smogon/calc` 0.11.0 applies
   burn/screens/weather before `+2` and STAB/type before the roll. Requests that exercise any
   non-identity modifier, including any non-neutral stat stage (whose H&S rounding is not
   independently proven), are refused with `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`.
4a. **Mutable live battle state (gated in §10.5, blocked).** H&S rewrites operands the request shape
   does not carry — current effective types (`SET_BATTLER_TYPE`), raw battle stat words (Power
   Trick), the dynamic move type (`SetTypeBeforeUsingMove`), and transient damage state. An active
   battle whose mutable classes are not authoritatively observed is refused with
   `HNS_LIVE_BATTLE_STATE_NOT_MODELLED`; a live type observation that matches the static record is
   still bound and used by the modifier detector.
5. **No H&S golden damage fixtures against the running ROM.** Gap C4a adds **source/host goldens**
   for the ordinary physical and special base path and divergences (native `gap_c4a_*` fixtures), but
   no number has been validated against a running official H&S 2.0.5 battle. That is Gap C4b.
6. **Snow, terrain and modern side conditions** are refused rather than approximated, because the
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
- With exact runtime settings observed, randomizers OFF, and representable types, the type chart is
  modelled via `typeSystem: "hns_2_0_5"` (Gap C1 closed).

**Why H&S calculations remain refused:**
Gap A connected challenge rules to calculator requests and capability evaluation, but all H&S calculations
remain strictly refused (`CalcSupport.UNSUPPORTED`) because Gap C2 remains open (ability system /
mechanics incompatibility, §9).

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

**Why H&S calculations remained refused after Gap B:**
Gap B closed data-consumption plumbing only. At the time of Gap B, calculations remained refused
pending Gap A (resolved in PR #61) and Gap C. Following Gap C1 (exact type system + Fairy toggle),
H&S calculations remain strictly **refused** (`UNSUPPORTED`, `request == null`) due to Gap C2
(`HNS_ABILITY_SYSTEM_NOT_MODELLED`).

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

**Why H&S calculations remained refused after Gap C1:**
Gap C1 closed the type-system gap. When challenge settings were observed, randomizers were OFF, and types were
representable, `HNS_TYPE_CHART_NOT_MODELLED` was cleared. However, calculations remained refused
due to unmodelled abilities (`HNS_ABILITY_SYSTEM_NOT_MODELLED`).

#### Gap C2 — authoritative effective ability input + conditional ability support (CLOSED)

**Update (issue #9, Gap C2 slice):** The blanket ability blocker is replaced with a precise, per-participant,
per-ability capability decision:
- **Authoritative live effective abilities:** Live active battler ability is read from `gBattleMons[battler].ability`
  (PR #56) and delivered as `BattlerRuntimeObservation`.
- **Active-party-slot matching:** The player's ability is accepted only when `partySlot == selectedPartyIndex`.
  The enemy's ability is accepted only when `partySlot == activeEnemySlot`. Slot mismatches, faint windows,
  `AMBIGUOUS` (doubles), and `OBSERVED_INVALID` fail closed to unknown ability (`HNS_EFFECTIVE_ABILITY_UNREADABLE`).
- **Anti-spoofing ownership:** In `CalcRequestBoundary`, caller-supplied abilities on `LIVE_READ` participants
  cannot override or fabricate authoritative observations.
- **Conditional ability capability (`HnsAbilityRegistry`):**
  - `PROVEN_NO_DAMAGE_EFFECT` (`ABILITY_NONE`, `KEEN EYE`, `INSOMNIA`): zero move-damage effect in H&S. Cleared with no ability blocker.
  - `MODELLED_EQUIVALENT` (`GUTS`, `THICK FAT`, `HUGE POWER`, `PURE POWER`): exact arithmetic parity proven against ADV pipeline. Cleared with no ability blocker.
  - `UNSUPPORTED_DAMAGE_RELEVANT` (`OVERGROW`, `BLAZE`, `TORRENT`, `SWARM`, modern abilities): damage-relevant but divergent or unmodelled. Fails closed with `HNS_ABILITY_EFFECT_NOT_MODELLED`.
- **Default ability substitution prevention:** `@smogon/calc` defaulting to `species.abilities[0]` is prevented
  by setting `options.ability = '(other)'` when ability is omitted, empty, or `"None"` under `typeSystem === 'hns_2_0_5'`.
- Verified via QuickJS host tests (`native/tests/test_js_calc.c:check_gap_c2_abilities`) and Kotlin unit tests (`CalcHnsAbilityTest.kt`).

**Why H&S calculations remain refused:**
Gap C2 resolves ability input and capability gating. When participants have modelled or proven-no-effect abilities,
no ability blockers are added. However, H&S calculations remain strictly **refused** (`CalcSupport.UNSUPPORTED`,
`request == null`) by the C4a mechanics blockers: `BADGE_BOOST_NOT_MODELLED` (§10.2),
`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` (§10.4), and `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` (§10.5).
The blanket held-item blocker was removed by Gap C3 below.

#### Gap C3 — authoritative held items + contextual item capability (CLOSED for an explicit subset)

**Update (issue #9, Gap C3 slice):** The blanket `HNS_HELD_ITEM_SYSTEM_NOT_MODELLED` blocker is replaced
with a precise, per-participant, per-item, **per-move** capability decision:

- **Exact item catalogue.** `tools/hns-items/generate_hns_items.py` derives the exact `enum Item` domain
  (`ITEM_ID_MAX = 900`, `ITEMS_COUNT = 901`), canonical symbols, display names, `holdEffect` and
  `holdEffectParam` from the pinned build's `src/item.c` translation unit; the generated
  `Hns205ItemCatalogue.kt` is source-checked byte-for-byte. `ItemDatabase.expansionMap` is not authority.
- **Authoritative current item.** `HnsBattlerRuntimeState` gains `itemId` / `itemOutOfDomain`, read from
  `gBattleMons[battler].item` through the pinned ABI probe (`HNS_BATTLE_POKEMON_ITEM_OFFSET 0x30`,
  width 2). The item word is current battle state and is updated on consume/knock-off/steal/swap/fling.
- **Stored party item vs current battle item.** `CalcParticipantPresenter.resolveEffectiveItem` and
  `CalcRequestBoundary.reconcileParticipantItem` implement: active-slot match → current battle item wins
  (including authoritative `ITEM_NONE` over a stale nonzero party item); bench player → parsed party item;
  opponent slot mismatch, faint window, doubles ambiguity, or unverified read → unreadable, never a fallback.
  Battle context is authoritative, not caller-declared: a supplied runtime observation establishes it, so
  `activeBattle = false` cannot downgrade an observed battler to the party item.
- **Static item capability (`HnsItemRegistry`), decided by numeric ID:**
  - `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` (`ITEM_NONE`, Exp. Share, Soothe Bell, Amulet Coin, Cleanse Tag,
    Lucky Egg): the item's own hold effect adds no item blocker for an item-independent move.
  - `UNSUPPORTED_DAMAGE_RELEVANT` (type boosters, Choice items, species items, Life Orb, Expert Belt,
    gems, resist berries, Focus Sash/Band, Leftovers/Shell Bell/Rocky Helmet): `HNS_ITEM_EFFECT_NOT_MODELLED`.
  - Any other in-domain, unreadable, or out-of-domain identity: `HNS_ITEM_EFFECT_NOT_MODELLED` or
    `HNS_EFFECTIVE_ITEM_UNREADABLE` respectively; an unresolvable manual name is
    `HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE`.
- **Contextual interaction audit (`HnsMoveItemInteractionRegistry`), decided by numeric move ID.**
  Fling, Natural Gift, Acrobatics, Knock Off, Poltergeist, Judgment, Techno Blast and Multi-Attack read
  held-item state in the pinned damage path (§7.2). None is modelled, so each adds
  `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` and is refused before the engine. The final capability is the union
  of the static and interaction audits, so `PROVEN_NO_ORDINARY_DAMAGE_EFFECT` is never trusted context-free.
- **No raw H&S name is forwarded.** Every statically-supported item is ordinary-damage-free, so the
  authorized request omits the item; no H&S source name can silently match an unrelated ADV item. This
  stripping is safe only because every item-dependent move was refused first.
- Verified via Kotlin unit tests (`Hns205ItemCatalogueTest.kt`, `HnsItemRegistryTest.kt`,
  `HnsMoveItemInteractionTest.kt`, `CalcHnsItemTest.kt`), native ABI/reader tests, generator tests
  (`test_generate_hns_items.py`) and the QuickJS host suite (`native/tests/test_js_calc.c:check_gap_c3_items`).

**Why H&S calculations remain refused after Gap C3:** identity and current-vs-stored item state are now
truthful and item capability is conditional, but the generation III badge boost still has no equivalent in
the request shape, so `BADGE_BOOST_NOT_MODELLED` (Gap C4) keeps H&S strictly refused.

### Then, concretely

1. Gap A is CLOSED: authoritative runtime challenge rules are consumed by `CalcRequestBoundary` into
   `CalcHnsRuntimeRules`, selecting category behavior (`PER_MOVE_SPLIT` vs `TYPE_BASED`), clearing
   unreadable limitations when observed, and blocking on unsupported active modes
   (`RANDOM_TYPES_ACTIVE_NOT_MODELLED`, `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED`).
2. Gap B is CLOSED: authoritative H&S species and move data are consumed via `overrides`.
3. Gap C1 is CLOSED: exact modern type chart, Fairy toggle ON/OFF, and optionStyle category coupling.
4. Gap C2 is CLOSED: authoritative effective ability input, active-slot matching, and conditional ability support.
5. Gap C3 is CLOSED for an explicit, contextual subset: exact item identity, current battle item
   authority, static item capability, and a per-move item-interaction audit; all damage items and all
   item-dependent moves fail closed precisely.
6. Gap C4a is CLOSED as an audit for an explicit mechanics set: the remaining challenge-settings
   fields are classified, move mechanics are gated from the pinned effect IDs, badge boost is
   precisely blocked, the ordinary physical/special base arithmetic has source/host goldens, and
   non-neutral stat stages plus unobserved mutable live battle state fail closed. H&S is still
   refused by `BADGE_BOOST_NOT_MODELLED`, `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`, **and**
   `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` (§10).
7. Gap C4b is OPEN: no ordinary H&S damage result has been validated against the running official
   2.0.5 ROM, the modifier-order/staged-stat divergence must be resolved, and effective battler
   types, battle stat words, the dynamic move type, and transient state must be authoritatively
   consumed (or proven neutral) before any H&S result can be published. `VERIFIED` stays out of
   reach while the §4.2 fields are unread.

---

## 10. Gap C4a — remaining mechanics, badge audit, move gating and arithmetic parity

This section is the C4a deliverable (issue #9). It does **not** promote H&S: production still
returns `CalcSupport.UNSUPPORTED` with `request == null`.

### 10.1 Remaining challenge-settings inventory (all 17 fields)

`HnsChallengeSettingInventory` (`app/src/main/java/com/dualdex/calculator/HnsChallengeSettingInventory.kt`)
has exactly one row per field the runtime reader exposes. `HnsChallengeSettingInventoryTest` asserts
the row count, that no field is missing, and every row's disposition.

| Field | Source semantics | Damage relevance | Captured downstream? | Extra blocker required? | Reason |
|---|---|---|---|---|---|
| `optionStyle` | 0 per-move, 1 type-based category `[src/battle_util.c:9183]` | selects physical/special stats | no (consumed) | no | consumed as a rule (Gap A) |
| `tx_Mode_Fairy_Types` | Fairy ON/OFF retypes species/moves `[src/pokemon.c:5734]`, `:5788` | typing/effectiveness | no (consumed) | no | consumed as a rule (Gap C1) |
| `tx_Random_Type` | randomizes species type `[src/pokemon.c:5731]` | typing/effectiveness | no (consumed) | `RANDOM_TYPES_ACTIVE_NOT_MODELLED` when ON | observed ON cannot be modelled |
| `tx_Random_TypeEffectiveness` | remaps attacking type `[src/battle_util.c:8533]` | effectiveness | no (consumed) | `RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED` when ON | observed ON cannot be modelled |
| `tx_Random_Abilities` | rerolls ability `[src/pokemon.c:5585]` | effective ability | **yes** | no | the effective ability is read from `gBattleMons` (Gap C2) |
| `tx_Random_Moves` | rerolls learned moves `[src/pokemon.c:3954]` | move authority | no | `HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED` when ON | the calculator never consults the party moveset |
| `tx_Challenges_NoEVs` | blocks EV gain `[src/pokemon.c:7808]` | EVs → stats | **yes** | no | the request carries the EV values |
| `tx_Challenges_BaseStatEqualizer` | replaces non-HP base stats with 0/100/255/500 `[src/challenge_menu.c:2359]`, `[src/pokemon.c:3750]` | every battle stat | **no** | `HNS_BASE_STAT_EQUALIZER_NOT_MODELLED` when nonzero | the request carries ordinary pinned base stats |
| `tx_Challenges_Mirror` | copies enemy party over the player's `[src/battle_main.c:667]` | party stats/moves | **yes** | no | the copied party is the observed party |
| `tx_Challenges_Mirror_Thief` | Mirror variant `[src/battle_main.c:5823]` | party stats/moves | **yes** | no | same as Mirror |
| `tx_Challenges_TrainerScalingIVs` | rewrites opponent IVs `[src/battle_main.c:2145]` | opponent stats | **yes** | no | rewritten IVs are the observed IVs |
| `tx_Challenges_TrainerScalingEVs` | rewrites opponent EVs `[src/battle_main.c:2156]` | opponent stats | **yes** | no | rewritten EVs are the observed EVs |
| `tx_Challenges_MaxPartyIVs` | forces player IVs to 31 `[src/pokemon.c:3232]` | player stats | **yes** | no | forced IVs are the observed IVs |
| `tx_Mode_Sturdy` | gates Sturdy endure-at-1-HP `[src/battle_util.c:8186]` | KO behaviour only | n/a | no | only reachable through Sturdy, which is not in the supported ability set |
| `tx_Challenges_LevelCap` | caps reachable level `[src/caps.c:64]` | stats | **yes** | no | the request carries the level |
| `tx_Challenges_ExpMultiplier` | scales EXP `[src/caps.c:64]` | stats | **yes** | no | the request carries the level |
| `tx_Mode_Legendary_Abilities` | substitutes slot-0 abilities `[src/pokemon.c:5551]` | effective ability | **yes** | no | the effective ability is read from `gBattleMons` (Gap C2) |

Unobserved `baseStatEqualizerMode` / `randomMovesEnabled` fail closed with
`CHALLENGE_SETTINGS_UNREADABLE`; no source default is substituted.

### 10.2 Badge boost — source, authority and why it stays blocked

Pinned source: `B_BADGE_BOOST = GEN_3` `[include/config/battle.h:30]`;
`GetBadgeBoostModifier()` returns `UQ_4_12(1.1)` `[src/battle_util.c:9135]`;
`ApplyOffensiveBadgeBoost` / `ApplyDefensiveBadgeBoost` compose it into the attack/defence modifier
`[src/battle_util.c:6894]`, `:6903`; eligibility is `ShouldGetStatBadgeBoost` `[src/battle_util.c:9143]`
(badge flag set, player side, and not link / e-Reader / recorded-link / Frontier / secret-base
trainer battle).

Insertion point (SOURCE VERIFIED): stat stages are applied to the raw stat first, then the ability/
item modifiers, then the badge is composed in UQ4.12 (`uq4_12_multiply_half_down`), and the combined
modifier is applied to the stat once (`uq4_12_multiply_by_int_half_down`) `[src/battle_util.c:7189]`,
`:7392]`. It is **not** `finalDamage × 1.1` and **not** `floor(stat × 1.1)`.

Authority: the four flags resolve to `FLAG_BADGE01_GET`, `FLAG_BADGE06_GET` (H&S; `FLAG_BADGE05_GET`
elsewhere), `FLAG_BADGE07_GET` and `FLAG_BADGE07_GET` (SpA/SpD share one flag) and are read through
`FlagGet` from SaveBlock1 `[include/constants/flags.h:1363]`, `:1368]`, `:1369]`. DualDex does not
read that flag storage, so caller-supplied badge state could not be proven and the calculator does
not expose a badge field. **C4a therefore keeps `BADGE_BOOST_NOT_MODELLED` rather than modelling a
value it cannot authoritatively observe.** The exact ordering above is recorded so a later slice can
implement it request-locally once an ABI-backed badge reader exists.

### 10.3 Move-mechanics capability

`tools/hns-move-mechanics/generate_hns_move_effects.py` parses the pinned `enum Move` and the raw
`.effect` / `.multiHit` / `.strikeCount` / `.explosion` / state-flag initializers of
`src/data/moves_info.h` into `Hns205MoveEffects.kt` (928 resolved effects, 357 ordinary, 6 unresolved).
`HnsMoveMechanicsRegistry` classifies by the pack's numeric move ID:

| Category | Meaning | Blocker |
|---|---|---|
| `ORDINARY_PROVEN_EQUIVALENT` | `EFFECT_HIT`, no multi-hit/explosion/always-crit/state flag | none |
| `UNSUPPORTED_STATE_DEPENDENT` | reads HP/friendship/weight/speed/consecutive-use/target state | `HNS_MOVE_MECHANICS_NOT_MODELLED` |
| `UNSUPPORTED_FORMULA_DIFFERENT` | fixed damage, OHKO, level/percent, defence selection, per-hit sequence | `HNS_MOVE_MECHANICS_NOT_MODELLED` |
| `ITEM_DEPENDENT_HANDLED_ELSEWHERE` | C3 item-interaction audit owns it | `HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED` (C3) |
| `UNCLASSIFIED` | effect missing/conditional/computed or unknown ID | `HNS_MOVE_MECHANICS_NOT_MODELLED` |

Representative blocked families: Return/Hidden Power/Low Kick (effect), multi-hit (`multiHit` /
`strikeCount > 1`, including Bullet Seed and Double Kick, which hide behind `EFFECT_HIT`), Explosion/
Self-Destruct (H&S keeps `B_EXPLOSION_DEFENSE` at `GEN_LATEST` while ADV halves Defence), Sacred
Sword/Chip Away (`ignoresTargetDefenseEvasionStages`), fixed damage/OHKO/Endeavor/Final Gambit, and
the unresolved Low Kick/Struggle conditionals. A simple `EFFECT_HIT` move such as Tackle clears the
gate; the C3 item-dependent moves keep their C3 blocker and are not double-reported.

### 10.4 Ordinary-damage arithmetic parity audit

The independent oracle is in `native/tests/test_js_calc.c` (`check_gap_c4a_arithmetic_parity`),
transcribed from the pinned source rather than calling the engine twice. Ordering:

| Stage | Pinned H&S | `@smogon/calc` 0.11.0 ADV |
|---|---|---|
| base | `bp·Atk·(2L/5+2)/Def/50 + 2` | `bp·Atk·(2L/5+2)/Def/50`, `+2` later |
| spread → weather → crit | applied to the value **including** `+2` | burn/screens/spread/weather applied **before** `+2`, crit after |
| random roll | **before** STAB/type/burn/screens | **after** STAB/type |
| STAB / type / burn / screens | after the roll, UQ4.12 half-down | before the roll, per-type floor |

**Finding:** the bare base path matches exactly (`gap_c4a_parity_neutral_base_matches`: Machamp Rock
Slide vs Snorlax is 51–60 on both). The positive proof now also covers the **neutral special** stat
pair (`gap_c4a_parity_neutral_special_matches`: Alakazam Thunderbolt vs Snorlax is 43–51 on both,
SpA 155 / SpD 130). STAB/type and crit+STAB/burn cases differ by 1 or more
(`gap_c4a_divergence_stab_detected`, `gap_c4a_divergence_crit_stab_detected`): e.g. Karate Chop vs
Snorlax is `102,103,…,120` in ADV but `102,102,102,104,…,120` in H&S. Therefore any request that
exercises a non-identity modifier adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` via
`CalcCapabilityPolicy.hnsModifierOrderDiverges` (crit, weather, doubles, screens, burn, STAB, or
type effectiveness ≠ 1).

**Stat stages (R2).** Non-neutral stat stages are **blocked** by the same gate. H&S applies stages
before its fixed-point ability/item composition while the ADV host applies ability modifiers before
stages, and the staged-stat rounding has not been independently proven for H&S, so the only positive
parity cases are neutral-stage physical and special requests. Any Atk/Def/SpA/SpD/Spe stage on
either participant adds `HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`; C4b must prove the staged-stat
arithmetic before a staged request may clear. The policy change is covered by
`non-neutral stat stages are refused by the modifier-order gate`,
`neutral stat stages clear the modifier-order gate`, and the native oracle's comment explaining why
no positive stage fixture is fabricated.

**Random damage roll:** both engines use the same 16 endpoints `85..100` and the same
`floor(damage · r / 100)` shape, but at a different point in the chain, which is exactly why the
non-neutral cases diverge.

### 10.5 Live battle state (R1)

`hnsModifierOrderDiverges` evaluates the *static* species/move operands. H&S instead consumes
battle-mutated operands that the request shape does not carry, so C4a adds
`HNS_LIVE_BATTLE_STATE_NOT_MODELLED`:

| State class | Pinned H&S mutation | C4a treatment |
|---|---|---|
| Current effective battler types | `GetBattlerTypes()` reads `gBattleMons[battler].types`; `SET_BATTLER_TYPE` (Soak) and Roost/Tera layer over the raw bytes | An exact-trusted, slot-matched, in-domain live type observation is bound by `CalcRequestBoundary` and used by the modifier detector. A set that differs from the static record, a third non-empty type, a typeless/out-of-domain value, an unobserved battler, or a slot mismatch blocks |
| Raw battle stat words | Power Trick swaps `gBattleMons[attacker].attack` and `.defense` directly, with unchanged stages and effect ID | Unobserved (no runtime reader yet): blocks. C4b owns an authoritative stat-word observation |
| Dynamic move type | `SetTypeBeforeUsingMove()` forces an otherwise ordinary move to Electric under Ion Deluge / Electrify without changing the static effect ID | Unobserved (no runtime reader yet): blocks. C4b owns it |
| Other transient damage state | Volatiles/screens/weather reachable by the supported subset that are not represented by the request fields | Unobserved: blocks. C4b owns it |

The gate fires only when the request is an **active battle**: `CalcRequestBoundary` marks it from an
explicit `activeBattle` hint or from any supplied runtime observation, and binds the current types
for a slot-matched OBSERVED battler. A manual hypothetical, or a live read of stored party data, is
not an active battle and is unaffected. `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` is checked per class,
so an observed class that matches the static record does not itself block; today the stat-word,
dynamic-move-type, and transient-state classes have no reader, so every active battle still fails
closed. `CalcCapabilityPolicyTest`/`CalcHnsLiveBattleStateTest` pin the blocked cases (unobserved
active battler, Soak Water vs static Normal — which also makes the detector add
`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED` for the live 2×, third non-empty type, slot mismatch,
out-of-domain type) and the policy-level fully-observed positive control.

### 10.6 Evidence vocabulary and the final blockers

The arithmetic goldens are **SOURCE/HOST VERIFIED** (pinned source transcribed into the native
oracle, executed against the committed bundle). They are **NOT RUNTIME VERIFIED**: no official ROM
battle has produced them.

The remaining production blockers are `BADGE_BOOST_NOT_MODELLED`,
`HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED`, and `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` (plus the
C2/C3/type/setting blockers where applicable). Gap C4b must:

1. provide an ABI-backed, boundary-owned badge-state reader so the exact §10.2 placement can be
   modelled request-locally;
2. either reproduce the H&S modifier order/rounding in the host or validate a source-mounted H&S
   arithmetic path against the running official 2.0.5 ROM, and prove the staged-stat arithmetic
   before non-neutral stages may clear (§10.4 R2); and
3. consume and validate authoritative effective battler types, battle stat words, the dynamic move
   type, and transient damage state (or prove each class neutral) before
   `HNS_LIVE_BATTLE_STATE_NOT_MODELLED` may clear (§10.5 R1).

Until then, H&S remains `UNSUPPORTED` with `request == null`, and `BUILDS_NOT_HASH_VERIFIED` is
untouched: no ROM hash or trust is promoted here.
