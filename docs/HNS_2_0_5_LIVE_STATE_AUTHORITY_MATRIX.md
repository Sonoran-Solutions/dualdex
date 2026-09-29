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
| Current status | `gBattleMons[battler].status1`. | The active battler reader samples the 32-bit status word. | `authoritativeObservedStatus1` binds it only to the matching observed active slot. | `DamageCalculator` forwards the raw word. Exact Guts, Toxic Boost (`STATUS1_PSN_ANY`), and Flare Boost (`STATUS1_BURN`) category/status paths are modelled; all other positive or unread statuses still fail closed with `HNS_LIVE_STATUS_NOT_MODELLED`. |
| Source move power and ability flags | `gMovesInfo[move].power` plus `MoveInfo` bits `punchingMove`, `bitingMove`, `pulseMove`, and `slicingMove` (`include/move.h:343, 348, 353, 383`). | These are immutable pinned data-table fields, not battle memory. | `HeartAndSoul205DataPack` resolves the exact move name to pinned move ID; generated ID maps supply its power and literal flag values. Conditional/computed source flag initializers are marked unknown. | `DamageCalculator` serializes only generated facts for that exact move ID; the request has no caller-owned ability-flag override. Technician uses pinned source power; Iron Fist, Strong Jaw, Mega Launcher, and Sharpness consume the matching source flag. Unknown move identity or flag remains blocked. |
| Effective ability | `gBattleMons[battler].ability` is the current battle ability ID. `GetBattlerAbilityInternal` additionally returns `ABILITY_NONE` under Gastro Acid (`src/battle_util.c:4996-5027`). Trace/Receiver/Power of Alchemy replace the battle ability before later requests. | Native reads the active `BattlePokemon.ability`; the same volatile window observes Gastro Acid. | `HnsBattlerRuntimeState.effectiveAbilityId` applies observed Gastro Acid suppression. `reconcileParticipantAbility` requires exact hash, in-domain ID, OBSERVED state and matching slot, then names that ID from the pinned registry. A declaration/name mismatch does not override it. | The effective ID is consumed by request-local ability/field/item policy and presentation. Group B does not ask the engine to reapply a cleared ability; only supported mechanics such as the bounded pinch path have their own calculator handling. Missing effective ID remains `HNS_EFFECTIVE_ABILITY_UNREADABLE`. |
| Current effective types | `gBattleMons[battler].types[3]`, updated by type-setting effects such as Color Change (`src/battle_util.c:3850-3859`), Mimicry, and Protean/Libero. Protean/Libero can also change types in the selected move's pre-damage canceler (`src/battle_script_commands.c:942-953`); their `usedProteanLibero` flag is not currently observed. | Native reads the active battler's three current type slots verbatim, including sentinels and a third type. | `authoritativeObservedTypes` requires exact trust, OBSERVED state, the matched party slot, and every slot observed/in-domain; unsupported partial or third-type shapes fail closed. | `DamageCalculator` serializes these as attacker/defender type overrides. `entry.js` uses them for STAB and type effectiveness. Color Change/Mimicry can use the live current types. Protean/Libero clear only when the current type set is exactly the move's one effective type, which proves the selected move cannot change its damage-relevant type; other cases remain UNKNOWN because the pending-use flag is absent. |
| Weather | Battle-global `gBattleWeather` (flags word). `HasWeatherEffect()` can suppress effects while the raw word remains set, including under Cloud Nine/Air Lock (`src/battle_util.c:10060-10071`). Ordinary Drizzle/Drought establish Rain/Sun; several other setters and primal weather have extra weather semantics. | Native reads the battle-global word independently alongside each active battler observation; an observed zero is distinct from unread. | `authoritativeObservedWeather` requires exact trust and matching readable words from both sides. The boundary maps only exact clear, ordinary Rain, and ordinary Sun to the calculator field. | `entry.js` applies ordinary Rain/Sun damage multipliers. Sand, Snow/Hail, Fog, Strong Winds, and primal bits are not consumed. Cloud Nine/Air Lock dynamically suppress raw weather and stay blocked; ordinary weather setter proof refuses if either is observed. |
| `gFieldStatuses` and terrain | Battle-global `gFieldStatuses`, including terrain bits. H&S reads it in the damage path (including `CalcDefenseStat` at `src/battle_util.c:7296` and `CalcAttackStat` at `:7111`); switch-in and move effects may establish terrain before a selected hit. | Native reads the actual field word at the verified field-status address and retains every bit. Both active observations must agree. | `authoritativeObservedFieldStatuses` binds the full word only when exact-ROM reads from both roles are readable and equal. `HnsFieldContextPolicy` decides each known bit independently; unknown bits remain blocked. | The policy-adjusted raw word reaches `calculateHnsDamage` only as `field.hnsFieldStatuses`. Request-local `MODELLED` cases keep their bit and apply Grass Pelt or Hadron Engine with the pinned stat-stage operator. Relevant caveats are neutralized. Caller `field.terrain` text and generic @smogon terrain handling cannot activate these H&S branches; other terrain effects remain outside this slice. |
| Persistent volatiles already represented in `CalcHnsPersistentVolatiles` | Current `BattlePokemon.volatiles`: Foresight, Miracle Eye, Root, Smack Down, Telekinesis, Magnet Rise, Gastro Acid, Roost active, Substitute, and Endured. Each is read because pinned ordinary damage or type/grounding/ability logic can consume it (`src/battle_util.c:5015, 6006-6038, 8165, 8177, 8263, 8276, 9806`). | Native reads the generated 41-byte volatile window from the active battle struct and marks the window observed only when the read succeeds. | `authoritativeObservedPersistentVolatiles` binds each per-battler value only for the matching active slot and an observed window. | The request policy consumes these values to refuse positive states that the damage engine does not model. They are not independently serialized as modifiers. An unread window never means neutral. |
| Charge / Wind Power / Electromorphosis | `BattlePokemon.volatiles.chargeTimer`; Wind Power and Electromorphosis set it for a later Electric move (`data/battle_scripts_1.s:4947-4952`). | Current `chargeTimer` is read from the same volatile window (two-bit value). | The boundary binds the active attacker's current timer. | The JS engine does not consume Charge. A positive timer with an Electric move remains `HNS_CHARGE_ACTIVE_NOT_MODELLED`; Wind Power/Electromorphosis remain independently blocked. The active reader does not authorize the missing arithmetic. |
| Flash Fire and Booster Energy / Paradox payloads | `flashFireBoosted`, `boosterEnergyActivated`, and `paradoxBoostedStat` are separate damage-time fields in `BattlePokemon.volatiles` (`include/constants/battle.h:283-293`; relevant damage reads include `src/battle_util.c:7087,7310`). | These exact flags/stat selector are not in the current native runtime tuple/window. | There is no authoritative boundary binding for them. | The engine cannot consume them, and ordinary raw stats/stages do not substitute for these payloads. Flash Fire stays blocked for #91; Booster Energy stays UNKNOWN and goes to item Group D #92. |
| Gimmick / form state | Active gimmick is `gBattleStruct->gimmick.activeGimmick[side][partyIndex]` (`include/battle.h:453`); current species/form is `gBattleMons[battler].species`. | Native reads the gimmick through a freshly validated `gBattleStruct` pointer and reads the active battler's current species ID. | Gimmick and species reads are slot-bound by the same participant observation. Active gimmicks are refused. Current species is bound for form-dependent policy such as Tera Shell; it is not inferred from party species. | The engine receives live raw stats and effective types as overrides, but no generic gimmick/form transform input. Species ID is a policy operand, not a license to use static form defaults. Unmodeled gimmick/form behavior remains blocked. |
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
- Electric/Psychic/Misty/Grassy Surge, Seed Sower, and Hadron Engine stay blocked because terrain is
  decoded but not applied by the damage engine; #91 owns terrain damage modifiers. Wind Power and
  Electromorphosis stay blocked because Charge's flag is read but not consumed; #91 owns that damage
  modifier. Cloud Nine/Air Lock and Flash Fire stay blocked because their live damage-time state or
  suppression semantics are not represented.
