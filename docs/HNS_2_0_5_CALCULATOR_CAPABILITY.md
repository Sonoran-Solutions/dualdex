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
species typings, and the type chart itself, and DualDex does not read those settings, so there is no
honest number to show. Every other build — CFRU hacks, split-mechanics vanilla builds, any
unidentified ROM — is **refused** with a stated reason rather than given a Gen III number.

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
| 2 | Move category | Per-move by default (`B_PHYSICAL_SPECIAL_SPLIT GEN_LATEST`) `[include/config/battle.h:76]`, decided by `GetBattleMoveCategory` `[src/battle_util.c:9173]` | **no** | **REFUSED** — it is also a live toggle (§4.1), and the engine derives category from type (§3.3) |
| 3 | Type chart | Modern chart: Fairy present, Steel does **not** resist Ghost/Dark `[src/data/types_info.h:8]`, `:25`, `:35`, `:36` | **no** | **DOES NOT MATCH** — the generation III chart resists Ghost and Dark with Steel and has no Fairy (§3.1) |
| 4 | Species base stats / typings | Modern (`P_UPDATED_STATS`/`P_UPDATED_TYPES GEN_LATEST`) `[include/config/pokemon.h:5]`, from the pinned data pack | **no** — the bridge forwards a name and the library resolves it against its own tables (§3.3) | **IDENTITY ONLY** — the name is proven to belong to this build (1427 species; ambiguous form names refused, §5), but the engine computes from its own record, so this is *not* consumption of the H&S record |
| 5 | Move properties (power/type/category) | Explicit per move, 848 numbered moves incl. Gen IX `[src/data/moves_info.h:121]`, `[include/constants/moves.h:905]` | **no** — same name-resolution path as row 4 | **IDENTITY ONLY** — 934 moves proven to belong to this build, but power/type/category are read from the library's own generation III table, which derives category from **type** (`.../src/move.ts:129`) |
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
| 17 | Challenge settings that change the rule | `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type`, `tx_Random_TypeEffectiveness` | **no** | **REFUSED** — this is why H&S cannot reach even "approximate" (§4.1) |
| 18 | Legendary ability overrides | `tx_Mode_Legendary_Abilities` default **ON**, substitutes abilities for slot 0 `[src/pokemon.c:5551]`, `[src/new_game.c:147]` | no | folds into row 6 |

---

## 3. Which mechanics are individually demonstrated, and which are not

An earlier revision of this document claimed the hack's type chart "agrees with Generation III's
chart on every Generation III type pair", and concluded from that plus the critical-hit and
spread-damage constants that the ADV pipeline was "the right arithmetic" for H&S. **That claim was
wrong and is withdrawn.** The correction matters because the matrix below is the foundation any
future H&S support will be built on.

### 3.1 The type chart does not match

| Matchup | Pinned H&S | `@smogon/calc` 0.11.0 at `gen: 3` |
|---|---|---|
| Ghost → Steel | **×1** | ×0.5 |
| Dark → Steel | **×1** | ×0.5 |

H&S sets `B_UPDATED_TYPE_MATCHUPS` to `GEN_9` `[include/config/battle.h:53]`, so its `STL_RS` macro
resolves to ×1.0 `[src/data/types_info.h:8]`, used by the Ghost and Dark rows
`[src/data/types_info.h:25]`, `:35`. The calculator's chart assigns ×0.5 to both
(`.../src/data/types.ts:313`, `:333`) and then aliases the generation III chart to it with
`const ADV = GSC;` (`.../src/data/types.ts:357`). `ADV`, `DPP` and `BW` are all the same object.

The precise finding is that the **selected generation III chart is incompatible** on this row: the
chart the policy actually sends makes Steel resist Ghost and Dark while H&S does not. It is *not*
that no later generation has the interaction — the library's `XY` chart does set Ghost → Steel and
Dark → Steel to ×1 (`.../src/data/types.ts:378`, `:380`). What remains unestablished is full H&S
pipeline equivalence; these two rows alone would not settle it, because the same chart change comes
bundled with everything else generation VI altered.

The earlier note that "Generation III has no Fairy interaction to disagree about" is also
misleading: H&S *does* have Fairy, so those rows are not a shared-generation question at all — the
hack's chart has interactions the generation III chart cannot express.

### 3.2 What is demonstrated, and what is not

Only these individual behaviours are source-and-test demonstrated:

| Behaviour | H&S (pinned source) | Calculator at `gen: 3` | Status |
|---|---|---|---|
| Critical-hit multiplier | ×2 (`B_CRIT_MULTIPLIER GEN_3`) `[include/config/battle.h:6]`, applied `[src/battle_util.c:7477]` | ×2 | **matches** — pinned by `gen3_crit_doubles_the_attack_form` |
| Two-target reduction | ×0.5 (`B_MULTIPLE_TARGETS_DMG GEN_3`) `[include/config/battle.h:47]` | ×0.5 | **matches** — pinned by the existing spread-move fixtures |
| Thick Fat placement | halves the attack stat `[src/battle_util.c:7121]`, `:7191` | halves the attack form | **matches** |
| Type chart | modern (Fairy present; Steel does not resist Ghost/Dark) | generation III (no Fairy; Steel resists both) | **does not match** |
| Weather, screens, category rule, abilities, items, badge boost | see §2 rows 2, 6, 7, 9–14 | partial | **does not match** |

Matching the critical-hit and spread-damage constants does **not** establish equivalence of the whole
calculation pipeline, and this document no longer claims it does. `gen: 3` remains what the policy
sends, but the justification is narrower than before: it is the arithmetic whose *individual*
generation III constants H&S demonstrably shares, and H&S calculations are refused anyway, so the
value currently reaches no H&S result at all. It is evidence for a starting point, not a
compatibility finding.

### 3.3 Three different claims that must not be conflated

The matrix in §2 uses these distinctions, and any future H&S work must keep them separate:

1. **Identity exists in pinned H&S data.** `HeartAndSoul205DataPack` can name species 1433 and move
   847, and `CalcRequestBoundary` proves a name belongs to the build.
2. **The calculator consumes that H&S record.** It does **not**. The bridge passes a *name*:
   `new Pokemon(gen, input.attacker.species, ...)` and `new Move(gen, input.move.name, ...)`
   (`tools/calc-bundler/entry.js:60`, `:78`), and the library resolves it against **its own**
   generation tables. `@smogon/calc` 0.11.0 exposes an `overrides` escape hatch on both
   (`.../src/pokemon.ts:53`, `.../src/move.ts:58`) but `entry.js` never forwards it, so no H&S
   base-stat, typing or move-property data reaches the engine.
3. **The calculator reproduces the H&S mechanic.** It does so only where §3.2 says "matches".

**A resolving name is therefore evidence of (1) alone.** It says the request names content this build
has; it does not say the number was computed from that content. §2 rows 4 and 5 are labelled
*identity only* for this reason.

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
**Update (challenge-settings reader slice of issue #9):** DualDex now reads this struct at runtime
— `pokemon_read_challenge_settings_gba` decodes it into a typed snapshot (`ChallengeSettingsSnapshot`)
that the companion publishes only while the running ROM is exactly trusted, and the runtime
verification in `HNS_2_0_5_COMPATIBILITY_EVIDENCE.md` §13 observed live values (including a
challenge-menu toggle flipping exactly `tx_Mode_Fairy_Types`). **This changes no calculator
claim**: the policy still refuses H&S (the rule-reads below are still unanswered by any request),
`CalcCapabilityPolicy` is untouched, source defaults are still never substituted for runtime
observations, and no effective live ability is established.

Two properties make this decisive rather than a caveat:

1. **Some fields change the rule, not just the values.** No request shape can absorb them.
2. **Several are ON by default, and the Mode tab is free.** `TAB_MODE` has no lock entries
   `[src/challenge_menu.c:183]`, so those rows can be flipped at any time, including mid-run. The
   defaults come from the challenge menu, not from `new_game.c`: `NewGameInitData` snapshots the
   menu's choices, clears `SaveBlock3`, and restores the snapshot `[src/new_game.c:221]`, `:235`,
   `:238`, `:239`. "The randomizer is off" therefore does **not** imply "vanilla behaviour".

### 4.1 Rule-changing fields — why H&S is refused outright

| Field | Default | What it changes |
|---|---|---|
| `optionStyle` | 0 = per-move split `[src/new_game.c:128]` | `GetBattleMoveCategory` `[src/battle_util.c:9183]`: when 1, the move's **type** decides physical/special, as in Generation III. Bound to the "PHYS/SP SPLIT" row `[src/challenge_menu.c:486]`, `:1973`. The *same* species, move and level has two different correct answers. |
| `tx_Mode_Fairy_Types` | 1 = ON `[src/new_game.c:146]` | When 0 the Fairy type is deleted: species revert to pre-Fairy typings via `sPreFairyTypes` `[src/pokemon.c:5734]`, and Fairy moves are retyped via `sFairyMoveAltTypes` (Moonblast → Dark) `[src/pokemon.c:5788]`. Bound to "ADD FAIRY TYPE" `[src/challenge_menu.c:456]`. |
| `tx_Random_Type` | 0 `[include/global.h:283]` | `GetSpeciesType` returns a randomized type `[src/pokemon.c:5731]`, `[include/randomizer.h:91]`. |
| `tx_Random_TypeEffectiveness` | 0 `[include/global.h:284]` | `GetTypeModifier` remaps the **attacking** type inside the chart at damage time `[src/battle_util.c:8533]`. |

The runtime reader observes these fields (see the update above), but no request path consumes them
yet, so DualDex still cannot say which rule applies to a given battle. The policy therefore refuses
H&S with exactly these four reasons
(`CalcCapabilityPolicy.HNS_REQUIRED_RULE_READS`) rather than showing a number it cannot justify.
**This is the gate that makes an H&S result impossible to mistake for a Gen III result.**

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

### Gap A — the rule is unknown

Read `SaveBlock3.challengeSettings` (offset and size ABI-verified; §4) and expose `optionStyle`,
`tx_Mode_Fairy_Types`, `tx_Random_Type`, `tx_Random_TypeEffectiveness`. **The read itself now
exists** (challenge-settings reader slice of issue #9; runtime-verified, §13 of the compatibility
evidence): the snapshot is available to future calculator logic through the companion's production
state path whenever the running ROM is exactly trusted. Until a later slice consumes it — and Gaps
B and C close — the request still cannot name the rule that will be applied, which is why the four
`*_UNREADABLE` limitations still block.

### Gap B — the engine does not consume H&S data

The bridge resolves names against the library's own tables (§3.3). Closing this means forwarding
`overrides` through `tools/calc-bundler/entry.js` for the species, move, type and base-stat values
the pinned pack already holds — `@smogon/calc` 0.11.0 supports it (`.../src/pokemon.ts:53`,
`.../src/move.ts:58`); the bridge simply never passes it. This is a prerequisite for any H&S number,
not a refinement: without it a "supported" H&S calculation would be computed from generation III
records.

Note the second-order consequence for `gen`: because the library derives a damaging move's category
from its **type** below generation 4 (`.../src/move.ts:129`), forwarding H&S move data while sending
`gen: 3` would give H&S's per-move categories the wrong answer. Gap B and the `gen` choice are
therefore coupled, and neither can be settled before Gap C.

### Gap C — the calculator does not reproduce H&S mechanics

Per §3.2: the type chart disagrees today (Ghost/Dark → Steel), and abilities, items, the category
rule, terrain, badge boost and modern side conditions all differ. Each needs either a positive
demonstration like the crit and spread constants have, or an explicit "not modelled" limitation.

### Then, concretely

1. Close Gap A; replace the four blocking limitations with conditional ones.
2. Close Gap B; add `gen`-selection evidence from Gap C rather than assuming.
3. Add golden fixtures for H&S calculations whose inputs are covered, verified against known in-game
   or upstream results. Only calculations that survive all three gaps may reach `ESTIMATED`, and
   `VERIFIED` stays out of reach while the §4.2 fields are unread.
