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
| 1 | Damage formula | Generation III arithmetic with modern data | yes | **SUPPORTED** (§3) |
| 2 | Move category | Per-move by default (`B_PHYSICAL_SPECIAL_SPLIT GEN_LATEST`) `[include/config/battle.h:76]`, decided by `GetBattleMoveCategory` `[src/battle_util.c:9173]` | **no** | **REFUSED** — it is also a live toggle (§4.1) |
| 3 | Type chart | Modern chart: Fairy present, Steel does **not** resist Ghost/Dark `[src/data/types_info.h:8]`, `:25`, `:35`, `:36` | partially | **SUPPORTED for Gen III type pairs** (§3); Fairy/Stellar rows are unreachable in the ADV pipeline |
| 4 | Species base stats / typings | Modern (`P_UPDATED_STATS`/`P_UPDATED_TYPES GEN_LATEST`) `[include/config/pokemon.h:5]`, from the pinned data pack | yes, by name | **SUPPORTED** with the pinned pack only — 1427 species, and ambiguous form names are refused (§5) |
| 5 | Move properties (power/type/category) | Explicit per move, 848 numbered moves incl. Gen IX `[src/data/moves_info.h:121]`, `[include/constants/moves.h:905]` | yes, by name | **SUPPORTED** with the pinned pack only — 934 moves (§5) |
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

## 3. Why the ADV pipeline is the right arithmetic for all three builds

This is the least obvious decision in the policy and it is load-bearing, so the reasoning is
recorded explicitly.

H&S 2.0.5 is **not** a Generation III game. `GEN_LATEST` is Generation IX
(`[include/config/general.h:70]`, `:73`), and the hack carries modern type matchups, modern move
data, per-move categories and modern items. What it *also* carries is a set of damage constants
deliberately pinned to Generation III:

| Constant | H&S value | Consequence |
|---|---|---|
| `B_CRIT_MULTIPLIER` | `GEN_3` `[include/config/battle.h:6]` | a critical hit is **×2**, not the modern ×1.5 |
| `B_MULTIPLE_TARGETS_DMG` | `GEN_3` `[include/config/battle.h:47]` | a two-target hit is **×0.5**, not ×0.75 |
| `B_BADGE_BOOST` | `GEN_3` `[include/config/battle.h:30]` | badge boost is active (unmodelled, §8) |
| Thick Fat placement | Generation III | it halves the **attack stat** `[src/battle_util.c:7121]`, inside `CalcAttackStat` `[src/battle_util.c:7191]` |

A modern-generation run would therefore get critical hits and spread moves wrong. The ADV pipeline
gets all four right. The remaining question is the type chart, and here the hack's modern table and
Generation III's table agree on every type pair Generation III has:

* `STL_RS` (`Ghost/Dark → Steel`) is a no-op under `B_UPDATED_TYPE_MATCHUPS == GEN_9`
  `[src/data/types_info.h:8]`, `[include/config/battle.h:53]` — and the hack's table resolves it to
  ×1.0 `[src/data/types_info.h:25]`, `:35`, which is exactly what `@smogon/calc`'s `ADV = GSC` chart
  produces (`tools/calc-bundler/node_modules/@smogon/calc/src/data/types.ts:313`, `:333`).
* Generation III has no Fairy interaction to disagree about; the modern chart only *adds* Fairy.

So the ADV pipeline reproduces H&S's arithmetic for the content Generation III shares with it. That
licenses `gen: 3` and nothing more: it does **not** license ignoring the rows above that the bridge
cannot express. The same pipeline is trivially correct for exact vanilla FireRed/Emerald.

**Vanilla Gen III verified set.** A vanilla request is presented as *Verified* only when every field
is covered: the running ROM is the exact build the profile was verified against, the species and
move names resolve in that build's data, and no ability, held item, status or field value outside
the modelled sets appears. The badge boost (row 14) is **outside** that set and is not modelled for
any build, so the verified claim is scoped to calculations from the unbadged state the golden
fixtures encode — see §8.

---

## 4. Heart & Soul challenge settings

`struct ChallengeSettings` is 32 bytes at `[include/global.h:253]`, a member of `SaveBlock3`
`[include/global.h:359]`, reached as `gSaveBlock3Ptr->challengeSettings` `[include/global.h:363]`.
DualDex reads none of it.

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

Because DualDex cannot read any of these, it cannot say which rule applies to a given battle. The
policy therefore refuses H&S with exactly these four reasons
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

In order, and each step is independently verifiable:

1. Read `SaveBlock3.challengeSettings` (offset and size ABI-verified; §4) and expose
   `optionStyle`, `tx_Mode_Fairy_Types`, `tx_Random_Type`, `tx_Random_TypeEffectiveness`.
2. Replace the four blocking limitations in the H&S capability row with conditional ones, so a run
   with all four in their known state reaches `ESTIMATED` (never `VERIFIED` while the §4.2 fields
   remain unread).
3. Add golden fixtures for H&S calculations whose inputs are covered, verified against known in-game
   or upstream results.
4. Only then consider making `gen: 3` conditional, if any covered rule turns out to need a different
   pipeline.
