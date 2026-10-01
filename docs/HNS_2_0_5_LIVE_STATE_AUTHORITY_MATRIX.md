# H&S 2.0.5 live-state authority matrix (#88)

This matrix is the shared provenance reference for request-local Group B rules. It describes what
DualDex samples from the active battle, what the request boundary accepts, and what the shipped
damage engine actually uses. It is pinned to upstream H&S `Release-v2.0.5`, commit
`1f42b74dff0e9fe942419845d040663dd829a973`; native layout fields are generated from that checkout
and checked by `./ci.sh source-check`.

## Observation and binding contract

`pokemon_read_battler_runtime_state_gba` samples the current `gBattleMons[battler]` selected for the
player/opponent role on each runtime read. It carries the active battler index and party slot, and
reads shared battle words (`gFieldStatuses`, `gBattleWeather`) from the same observation. The JNI
tuple preserves a separate read/observed bit for values where zero is meaningful. The Kotlin decoder
keeps short or unread fields unobserved; it does not fill them from party data.

For exact H&S, `CalcRequestBoundary` requires the participant's requested party slot to equal the
observed active slot, a clean observed state, and in-domain values. Global weather and field words
must be readable on both observations and match. Per-battler values are rebound from that participant's
observation. `CalcRequestBoundary` strips caller-crafted live-state fields before binding them. A
missing, stale, invalid, or mismatched value remains unknown and cannot grant a Group B clearance.

## Switch-in phase gate

The event counter and `BattlerState.switchIn` flags alone are insufficient during a replacement.
Pinned H&S installs the new `gBattleMons` data in `Cmd_switchindataupdate`, then marks
`BattlerState.switchIn` and resets `eventState.switchIn` in `switchineffects`. The opponent
switch-in animation can also clear `monToSwitchIntoId` before those effects start. Native
therefore treats the phase as settled only when the event counter is complete, all active flags are
clear, and `gBattleMainFunc` points at `HandleTurnActionSelectionState`. That callback remains in
action/script/controller work throughout the replacement sequence; an unread IWRAM callback fails
closed. Official v2.0.5 release symbols for this gate are recorded in
[`hns205-field-layout-symbols.txt`](../tools/hns-runtime-probe/evidence/hns205-field-layout-symbols.txt).

## Operand matrix

| Operand | Pinned H&S field and timing | Runtime reader / slot authority | Boundary binding | Damage-engine consumption and current gate |
|---|---|---|---|---|
| Attack, Defense, Sp. Atk, Sp. Def and Speed stages | `BattlePokemon.statStages[8]` (slot 0 is unused HP; 1–5 are the five damage stats, then Accuracy/Evasion). Ability/item triggers write stages through `SET_STATCHANGER`; `Simple` doubles the change in the stat-write path (`src/battle_script_commands.c:7792-7796`), not when damage interprets the stage. | `pokemon_read_battler_runtime_state_gba` reads all eight stage bytes from the active `BattlePokemon`; the decoder validates each raw stage 0–12 and translates to −6…+6. | `authoritativeObservedStatStages` requires exact trust, OBSERVED state, matching active party slot, observed eight-element array, and range −6…+6 for both participants. | `DamageCalculator` serializes the observed stage arrays. `tools/calc-bundler/entry.js` selects the physical/special Attack and Defense stages for its formula. This is the proof operand for stat-stage writers; it is not reconstructed from species or move history. |
| Raw Attack, Defense, Sp. Atk, Sp. Def and Speed | Current `gBattleMons[battler]` battle-stat fields; these are the battle values after nature/EV/stat setup and battle transformations. | The same live battler reader samples the five current stat words for the role and slot. | `authoritativeObservedRawStats` uses the same exact-ROM, observed-state, and party-slot checks as stages. | The request serializes them as `rawStats`; `entry.js` prefers these values over stats recalculated from static species data. |
| Current HP / max HP | `gBattleMons[battler].hp` and `.maxHP`, sampled at the current battle frame. | The active role's current `BattlePokemon`; slot/battler provenance is carried with the observation. | `authoritativeObservedHp` requires exact trust, OBSERVED state, matching slot, an explicit HP-read bit, positive max HP, and a valid HP range. | The bound HP pair drives pinch-condition checks and max-HP percentage display. It does not authorize an ability or item whose separate live boost flags are absent. |
| Current status | `gBattleMons[battler].status1`. | The active battler reader samples the 32-bit status word. | `authoritativeObservedStatus1` binds it only to the matching observed active slot. | `DamageCalculator` forwards the raw word. Exact Guts, Toxic Boost (`STATUS1_PSN_ANY`), Flare Boost (`STATUS1_BURN`), and defender Marvel Scale (`STATUS1_ANY`) paths use the bound raw word; other positive or unread statuses still fail closed with `HNS_LIVE_STATUS_NOT_MODELLED`. |
| Source move power and ability/contact metadata | `gMovesInfo[move].power`, `MoveInfo` bits `makesContact`, `punchingMove`, `bitingMove`, `pulseMove`, `slicingMove`, and the pinned `MoveIsAffectedBySheerForce(move)` helper (`include/move.h`; `src/battle_util.c:9430`). | These are immutable pinned data-table fields, not battle memory. | `HeartAndSoul205DataPack` resolves the exact move name to pinned move ID; source generators produce the move-effect, contact, punching, ability-flag, and Sheer Force maps. Conditional/computed initializers are marked unknown. | `DamageCalculator` serializes only generated facts for that exact move ID; caller JSON cannot override contact or Sheer Force. `HnsContactAuthority` also applies the ordinary gate, effective attacker Long Reach, and current Punching Glove. Protective Pads is not part of `IsMoveMakingContact`; Shell Side Arm remains outside the ordinary move surface. Unknown metadata remains blocked. |
| Effective ability | `gBattleMons[battler].ability` is the current battle ability ID. `GetBattlerAbilityInternal` additionally returns `ABILITY_NONE` under Gastro Acid (`src/battle_util.c:4996-5027`). Trace/Receiver/Power of Alchemy replace the battle ability before later requests. | Native reads the active `BattlePokemon.ability`; the same volatile window observes Gastro Acid. | `HnsBattlerRuntimeState.effectiveAbilityId` applies observed Gastro Acid suppression. `reconcileParticipantAbility` requires exact hash, in-domain ID, OBSERVED state and matching slot, then names that ID from the pinned registry. A declaration/name mismatch does not override it. | The effective ID is consumed by request-local ability/field/item policy and presentation. Group D request rules now use it for source-backed Marvel Scale, Flower Gift, Sheer Force, Tough Claws, Fluffy, and exact false-predicate clearance for Reckless, Sand Force, Battery, and Power Spot. Missing effective ID remains `HNS_EFFECTIVE_ABILITY_UNREADABLE`. |
| Current effective types | `gBattleMons[battler].types[3]`, updated by type-setting effects such as Color Change (`src/battle_util.c:3850-3859`), Mimicry, and Protean/Libero. Protean/Libero can also change types in the selected move's pre-damage canceler (`src/battle_script_commands.c:942-953`); their `usedProteanLibero` flag is not currently observed. | Native reads the active battler's three current type slots verbatim, including sentinels and a third type. | `authoritativeObservedTypes` requires exact trust, OBSERVED state, the matched party slot, and every slot observed/in-domain; unsupported partial or third-type shapes fail closed. | `DamageCalculator` serializes these as attacker/defender type overrides. `entry.js` uses them for STAB and type effectiveness. Color Change/Mimicry can use the live current types. Protean/Libero clear only when the current type set is exactly the move's one effective type, which proves the selected move cannot change its damage-relevant type; other cases remain UNKNOWN because the pending-use flag is absent. |
| Weather | Battle-global `gBattleWeather` (flags word). `HasWeatherEffect()` can suppress effects while the raw word remains set, including under Cloud Nine/Air Lock (`src/battle_util.c:10060-10071`). Ordinary Drizzle/Drought establish Rain/Sun; several other setters and primal weather have extra weather semantics. | Native reads the battle-global word independently alongside each active battler observation; an observed zero is distinct from unread. | `authoritativeObservedWeather` requires exact trust and matching readable words from both sides. The boundary maps only exact clear, ordinary Rain, and ordinary Sun to the calculator field. | `entry.js` applies ordinary Rain/Sun damage multipliers. Flower Gift reuses the pinned holder-specific `IsBattlerWeatherAffected` semantics, including Cloud Nine/Air Lock and Utility Umbrella on the actual Cherrim holder. Sand, Snow/Hail, Fog, Strong Winds, and primal bits remain outside production weather support; Sand Force arithmetic does not clear that independent weather gate. |
| `gFieldStatuses` and terrain | Battle-global `gFieldStatuses`, including terrain bits. The selected-hit base-power path applies Grassy/Electric/Psychic attacker boosts and Misty defender reduction at `src/battle_util.c:6639-6645`; terrain queries call `IsBattlerTerrainAffected` at `:5140-5148`. | Native reads the actual field word at the verified field-status address and retains every bit. Both active observations must agree. | `authoritativeObservedFieldStatuses` binds the full word only when exact-ROM reads from both roles are readable and equal. `HnsFieldContextPolicy` decides each known bit independently; unknown bits remain blocked. | The policy-adjusted raw word reaches `calculateHnsDamage` only as `field.hnsFieldStatuses`. One shared `HnsTerrainAuthority` resolves attacker-vs-defender applicability from observed effective ability, item, types, Gravity, suppression and neutral volatile state. Request-local `MODELLED` cases keep the bit and apply the direct modifier (and any exact Grass Pelt or Hadron Engine composition); known relevant terrain effects are neutralized. Caller `field.terrain` text and generic @smogon terrain handling cannot activate these H&S branches. |
| Semi-invulnerability and terrain | `IsBattlerTerrainAffected` returns false for `IsSemiInvulnerable(battler, CHECK_ALL)` before asking groundedness (`src/battle_util.c:5140-5148`). | The existing native volatile window supplies `volatileSemiInvulnerable` for each active battler. | `HnsTerrainAuthority` requires the volatile window observed and returns `UNKNOWN` when the semi-invulnerable mask is nonzero. No new volatile reader or neutral-state inference is added. | A nonzero state cannot accidentally receive a terrain modifier; unread volatile state keeps the existing hard live-state gate. |
| Persistent volatiles already represented in `CalcHnsPersistentVolatiles` | Current `BattlePokemon.volatiles`: Foresight, Miracle Eye, Root, Smack Down, Telekinesis, Magnet Rise, Gastro Acid, Roost active, Substitute, and Endured. Each is read because pinned ordinary damage or type/grounding/ability logic can consume it (`src/battle_util.c:5015, 6006-6038, 8165, 8177, 8263, 8276, 9806`). | Native reads the generated 41-byte volatile window from the active battle struct and marks the window observed only when the read succeeds. | `authoritativeObservedPersistentVolatiles` binds each per-battler value only for the matching active slot and an observed window. | The request policy consumes these values to refuse positive states that the damage engine does not model. They are not independently serialized as modifiers. An unread window never means neutral. |
| Charge / Wind Power / Electromorphosis | `BattlePokemon.volatiles.chargeTimer`; Wind Power and Electromorphosis set it for a later Electric move (`data/battle_scripts_1.s:4947-4952`). | Current `chargeTimer` is read from the same volatile window (two-bit value). | The boundary binds the active attacker's current timer. | The JS engine does not consume Charge. A positive timer with an Electric move remains `HNS_CHARGE_ACTIVE_NOT_MODELLED`; Wind Power/Electromorphosis remain independently blocked. The active reader does not authorize the missing arithmetic. |
| State-backed Group D / live gender | Generated `BattlePokemon.personality`, Slow Start/Flash Fire/transformed/Booster/Paradox/Ruin/NG volatiles; `BattlerState.isFirstTurn`; stored Supreme counter; usable gimmick/playerSelect; current action globals. | ARM compiled layout; 42-byte volatile window; 97-int native/JNI tuple with observation flags and domains. Generated species gender ratios. | Exact trust, observed matching active slots and independent phase/move binding; old tuples remain unobserved. | All 16 identities conditionally modelled; see [complete layouts, predicates and runtime evidence](HNS_STATE_BACKED_GROUP_D.md). Held Booster Energy stays under #92. |
| Gimmick / form state | Active gimmick is `gBattleStruct->gimmick.activeGimmick[side][partyIndex]` (`include/battle.h:453`); current species/form is `gBattleMons[battler].species`. | Native reads the gimmick through a freshly validated `gBattleStruct` pointer and reads the active battler's current species ID. | Gimmick and species reads are slot-bound by the same participant observation. Active gimmicks are refused. Current species is bound for form-dependent policy such as Tera Shell and Cherrim-Sunshine Flower Gift; it is not inferred from party species. | The engine receives live raw stats and effective types as overrides, but no generic gimmick/form transform input. Species ID is a policy operand, not a license to use static form defaults. Unmodeled gimmick/form behavior remains blocked. |
| Current held item | `gBattleMons[battler].item`, the active battle item's numeric ID (including authoritative `ITEM_NONE` after consumption). | Native reads the current item from the same active battler and reports observed/domain status. | The boundary reconciles the live item ID/name and provenance from the matching active slot; caller item claims are stripped. | `HnsItemContextPolicy` decides request-local item relevance. The calculator receives no arbitrary held-item name for an ignored/unsupported item; a proven stat-writing item's already-current stage is not written a second time. |

## Group B decisions supported by this matrix

- Stat-stage writers are clearable only with both current eight-entry stage arrays. The reviewed
  set includes Speed Boost, Intimidate, Steadfast, Stamina, Download, Moxie, Beast Boost, Defiant,
  Competitive, Weak Armor, Anger Point, Justified, Rattled, Berserk, Anger Shell, Moody, Guard Dog,
  Intrepid Sword, Dauntless Shield, Opportunist, Water Compaction, Steam Engine, Thermal Exchange,
  Soul-Heart, Chilling Neigh, Grim Neigh, and Simple. Simple's source applies its multiplier when
  writing the stage. Unaware and any direct-multiplier ability remain blocked.
- Drizzle and Drought clear only for observed unsuppressed clear/Rain/Sun. Sand Stream, Snow Warning,
  Sand Spit, primal weather setters, Orichalcum Pulse, and Hadron Engine remain blocked because the
  observed weather is unsupported or the ability also has unmodelled behavior; send weather damage
  and special cases to #91.
- Color Change and Mimicry use authoritative effective types. Protean and Libero additionally need
  the selected move's pre-damage state; because `usedProteanLibero` is not read, only the exact
  already-monotyped same-move-type case clears. Trace, Receiver, and Power of Alchemy use the
  authoritative effective runtime ability ID.
- Terrain Seed and Berserk Gene remain blocked while their current item is held. An active battle
  can be between switch-in events, and a successful Seed/Gene activation consumes the item, so a
  stage snapshot cannot prove that a held item has already run. The authoritative current
  `ITEM_NONE` value after consumption leaves the resulting stage in the live array.
  Booster Energy stays UNKNOWN because its separate activation/stat payload is not read; #92 owns it.
- Terrain-setting abilities never substitute for the observed field word: if a terrain is removed,
  replaced, expired, or absent in `gFieldStatuses`, caller text and setter identity cannot restore it.
  The ordinary direct move modifiers are now exact only when the matching battler's terrain
  applicability is authoritative; Quark Drive, terrain-dependent move effects, priority changes,
  and other terrain consequences remain outside this slice. Wind Power and
  Electromorphosis stay blocked because Charge's flag is read but not consumed; #91 owns that damage
  modifier. Earlier Group B-only clearance for Cloud Nine/Air Lock and Flash Fire was limited because their live damage-time state or
  suppression semantics are not represented.

## Current-turn authority and shared field state

The [state-backed Group D evidence](HNS_STATE_BACKED_GROUP_D.md) supersedes the historical unread
payload caveats above. Analytic requires live BattleMainCB1 → RunTurnActionsFunctions →
B_ACTION_EXEC_SCRIPT, consistent current attacker/order/action and the exact requested move.
Selection/menu/new-turn frames are UNKNOWN. Runtime traces prove both action verdicts and reset.

Aura uses one shared two-living-battler ability authority. Ruin uses the observed four field flags,
self-exclusion, Gastro Acid and NG/Ability Shield predicates, even after raw ability replacement.
Unknown field payloads and positive global suppression preserve independent refusal gates.
