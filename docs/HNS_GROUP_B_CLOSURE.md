# H&S Group B live-state closure audit

Pinned mechanics authority: `PokemonHnS-Development/pokehns-expansion` at `1f42b74dff0e9fe942419845d040663dd829a973`. This is a current-state audit on main after PRs #91, #92, #116, and #117; earlier #102 descriptions are historical.

A Group B clearance means the ability or item has already written/suppressed a current battle operand and that exact operand is consumed by the request. It does not mean the mechanic has no effect. A clearance removes only its own blocker; move, weather, field, format, and other capability blockers stay independent.

## Abilities

| Candidate | ID | Pinned behavior and resulting state | Live authority and timing | Disposition / remaining boundary | Source |
|---|---:|---|---|---|---|
| Intimidate | 22 | Entry event writes the opponent's Attack stage through the stat-change machinery. | Both live stage arrays; switch-in/event driver must be settled. | Conditional live-stage clearance, including unsupported selected moves; unread or pending entry remains UNKNOWN. | `battle_util.c:3451-3459` |
| Download | 88 | Entry event chooses and writes Attack or Sp. Atk stage. | Both live stage arrays; switch-in/event driver settled. | Conditional live-stage clearance. | `battle_util.c:3269` |
| Intrepid Sword / Dauntless Shield | 234 / 235 | Entry writes Attack / Defense stage. | Both live stage arrays; switch-in/event driver settled. | Conditional live-stage clearance. | `battle_util.c:3488`, `3502` |
| Speed Boost | 3 | End-turn Speed stage write affects only current Speed/order state. | Live stages; current-action Analytic order authority is used for ordinary moves. | Conditional live-stage clearance; UNKNOWN Analytic phase stays independently conservative. | `battle_util.c:3738-3746`; `battle_end_turn.c:1276-1278` |
| Chlorophyll / Swift Swim | 34 / 33 | Weather-conditioned effective Speed changes turn order. | Exact current-action order already reflects live speed; with an Analytic attacker the order must be observed. | Conditional clearance; independently unsupported moves remain refused, and unread Analytic order remains UNKNOWN for ordinary moves. | `battle_main.c:4948`, `4946`; `battle_util.c:6691` |
| Steadfast, Anger Point, Simple, Defiant, Weak Armor, Moody, Moxie, Justified, Rattled, Competitive, Water Compaction, Stamina, Berserk, Soul-Heart, Beast Boost, Steam Engine, Chilling Neigh, Grim Neigh, Anger Shell, Opportunist, Thermal Exchange, Guard Dog | 80, 83, 86, 128, 133, 141, 153, 154, 155, 172, 192, 195, 201, 220, 224, 243, 264, 265, 271, 290, 270, 275 | Their damage-relevant results use the live stage arrays. Defiant and Competitive can react to Sticky Web during entry, before the stat writer event sequence is finished. | Both exact live stage arrays and `switchInEventsSettled == true` for every stat-stage writer. | Conditional live-stage clearance. Unsettled or unread entry remains UNKNOWN; unsupported move mechanics remain independently refused. Guard Dog's move-blocking and other move-specific behavior is outside this stage proof. | Ability-specific rows in `tools/hns-abilities/ability_inventory.tsv`; policy source mapping in `HnsAbilityContextPolicy.statWriterSource` |
| Supersweet Syrup | 306 | Entry lowers Evasion, which is not a damage operand. | No damage-relevant live input. | Remains `PROVEN_NO_DAMAGE_EFFECT`; not a stat-stage damage writer. | `battle_util.c:3461` |
| Wind Power / Electromorphosis | 277 / 280 | Event writes a Charge-style payload, not a stat stage. | Current `chargeTimer` observation is the move Charge volatile; it does not prove these ability-specific event payloads. | Handed to #93: add/read the exact ability charge payload or keep hard refusal; no Group B clearance yet. | `battle_util.c:4309`, `4313`; `battle_script_commands.c:12881` |
| Drizzle / Drought | 2 / 70 | Entry attempts ordinary Rain / Sun. | Exact live weather; switch-in/event driver settled. | Setter identity clears; supported Rain/Sun runs from current weather. Cloud Nine/Air Lock may suppress it. | `battle_util.c:3354`, `3383` |
| Sand Stream / Snow Warning | 45 / 117 | Entry attempts Sandstorm / Snow or Hail. | Exact live weather; switch-in/event driver settled. | Setter identity clears, while unsupported Sand/Snow arithmetic remains `HNS_LIVE_WEATHER_NOT_MODELLED`. | `battle_util.c:3368-3380`, `3397` |
| Sand Spit | 245 | Writes Sandstorm after the hit that triggers it. | Current weather word. The trigger cannot retroactively change its own hit. | Setter identity clears; unsupported Sand arithmetic remains an independent weather refusal. | `battle_util.c:4252-4268` |
| Electric / Psychic / Misty / Grassy Surge | 226 / 227 / 228 / 229 | Entry writes the corresponding terrain field bit. | Exact `gFieldStatuses`; switch-in/event driver settled. | Setter identity clears; current terrain applicability and modifier are supplied by live field authority. | `battle_util.c:3414`, `3442`, `3433`, `3424` |
| Seed Sower | 269 | A hit writes Grassy Terrain after that hit. | Exact `gFieldStatuses`; current hit uses the pre-trigger snapshot and later requests use the new field. | Setter identity clears; terrain arithmetic remains owned by field authority. | `battle_util.c:4288-4294` |
| Hadron Engine / Orichalcum Pulse | 287 / 288 | Field/weather setter plus a direct damage-time stat modifier. | Current field/weather plus Group D ability rule. | Setter consequence is already live-state-backed; retain their exact Group D damage rule. Not treated as globally pure writers. | `battle_util.c:3414`; `battle_util.c:7105-7110` |
| Primordial Sea / Desolate Land / Delta Stream | 189 / 190 / 191 | Set primal weather and also block or change move/weather behavior. | Raw weather is observable, but primal weather's complete move and modifier surface is not implemented. | Handed to #93 for a separate exact-weather/move pipeline slice; retain weather and move blockers. | `battle_util.c` primal weather branches; see pinned-source audit in `tools/hns-abilities/ability_inventory.tsv` |
| Cloud Nine / Air Lock | 13 / 76 | `HasWeatherEffect` suppresses effective weather while a living ability is active. | Current effective abilities, live HP, and weather; request field passes no effective Rain/Sun under an active suppressor. | Conditional effective-weather clearance for the supported selected-hit surface; unsupported weather/move blockers remain independent. | `battle_util.c:10053-10071` |
| Color Change / Mimicry | 16 / 250 | Completed type rewrite is the battler's current type. | Exact live `gBattleMons[].types`, not species defaults. | Conditional live-type clearance. | `battle_util.c:3850`, `4896`; Mimicry also `1815` |
| Trace / Receiver / Power of Alchemy | 36 / 222 / 223 | Completed copy/replacement is the current effective ability ID. | Effective live ability; switch-in settlement for Trace. Singles topology excludes ally-dependent Receiver/Power of Alchemy triggers. | Conditional current-ability clearance; never infer a copy from species declarations. | `battle_util.c:3097`; `battle_script_commands.c:14126`, `14149` |
| Protean / Libero | 168 / 236 | Move pre-damage path may rewrite type after the current snapshot. | Live types prove no change only when already monotyped to the selected move's effective type. | Narrow same-type request-local clearance; otherwise handed to #93 for the missing `usedProteanLibero`/pre-use state. | `battle_script_commands.c:944` |

### State-writer timing contracts

- **Switch-in / pre-action:** Every stat-stage writer requires `switchInEventsSettled == true`, including Defiant and Competitive, which may react to a pending Sticky Web drop during entry. Intimidate, Download, Intrepid Sword, Dauntless Shield, Drizzle, Drought, Sand Stream, Snow Warning, and the four Surges also require settled entry. False or unread settlement remains UNKNOWN.
- **After-hit / end-turn:** Speed Boost and audited reactive/stat-response writers consume the current live stages once entry is settled; no historical trigger flag is needed. A not-yet-fired future trigger cannot change the selected hit.
- **Reactive field writers:** Sand Spit and Seed Sower update weather/terrain after the triggering damage event; that hit uses the prior snapshot. Subsequent requests consume the new observed field word.
- **Speed/order:** Speed Boost does not recompute order. The exact current-action `HnsAnalyticTurnOrder` is authoritative for ordinary Analytic requests. UNKNOWN order and unsupported speed-dependent move blockers remain independent.

## Items

| Candidate family | Final disposition | Evidence / boundary |
|---|---|---|
| `ATTACK_UP`, `DEFENSE_UP`, `SPEED_UP`, `SP_ATTACK_UP`, `SP_DEFENSE_UP`, `RANDOM_STAT_UP`; Kee, Maranga, Weakness Policy, Absorb Bulb, Cell Battery, Luminous Moss, Snowball, Throat Spray | The future write cannot change the current hit, including on independently unsupported move shapes; an already-fired write is consumed through live stages. Any hold effect marked `gHoldEffectsInfo.onSwitchIn` remains UNKNOWN until entry settles. | `tools/hns-items/context_rules.json`; activation metadata and item inventory are generated from pinned references. |
| Blunder Policy / Room Service | Their Speed write follows a miss or Trick Room event, after the current hit's order is established. Clear for the current hit without requiring an ordinary move or known non-Analytic attacker. | `battle_hold_effects.c:1058`, `1109`; current-action order authority remains independent. |
| Terrain Seeds | A consumed item plus exact live stage is represented; a currently held matching seed remains UNKNOWN while a pre-hit activation could still execute; unread stage remains UNKNOWN. | `tools/hns-items/decisions.json`, `context_rules.json`; switch-in settlement and current terrain are inputs. |
| Berserk Gene | Consumed item plus exact live Attack stage is represented; currently held gene remains UNKNOWN until switch-in activation settles. | `tools/hns-items/decisions.json`, `context_rules.json`. |
| Booster Energy | Resolved by #92/#116/#117. Current item plus `boosterEnergyActivated` and `paradoxBoostedStat` are authoritative; no duplicate Group B path is added. | `docs/HNS_GROUP_D_HELD_ITEMS.md`; Group D volatile payload and current item. |

## #93 handoffs

1. **Wind Power / Electromorphosis (277 / 280), exact candidate.** The pinned hit reaction writes Charge-style ability state (`battle_util.c:4309`, `4313`; `battle_script_commands.c:12881`). DualDex reads move `chargeTimer`, not a source-proven ability activation payload, so it cannot prove that a prior hit's ability boost is present or absent. Add the exact runtime operand and pipeline rule, or keep a hard refusal.
2. **Protean / Libero positive rewrite branch, exact candidate.** `battle_script_commands.c:944` can change type in the selected move's pre-damage path. Outside the already-monotyped same-type proof, the current snapshot lacks `usedProteanLibero`; adding only the species/live type does not determine whether the pending write runs. Add that state or retain hard refusal.
3. **Primal weather and its move interactions (189 / 190 / 191), hard refusal until exact pipeline coverage.** The raw primal weather write is visible, but these abilities also block weather and change move/weather outcomes beyond supported ordinary Rain/Sun. Group B cannot clear that independent arithmetic and move behavior from the raw word alone.

## Census impact

The exact starting-main census is the checked-in `origin/main` artifact after #117. Current counts come from the regenerated full census on this branch; no lead pair became newly displayable, so the request-tier gain is not a lead-coverage gain.

| Measure | Starting main | This branch | Change |
|---|---:|---:|---:|
| Fully modelled requests | 19,170 | 19,336 | +166 |
| Caveated estimates | 216 | 224 | +8 |
| Refused requests | 4,892 | 4,718 | -174 |
| Displayable lead pairs | 376 / 1,302 | 376 / 1,302 | 0 |
| Displayable lead requests | 6,724 | 6,724 | 0 |
| Requests moved from refused to displayable | — | 174 | 166 fully modelled; 8 caveated |
| Still-refused requests with at least one Group B cause removed | — | 906 | Another independent cause remains |

After adding the unsettled-entry gates for every stat-stage writer and generated switch-in item activations, the full census was regenerated. The final totals above remain unchanged: 19,336 fully modelled, 224 caveated, 4,718 refused; lead coverage remains 376 / 1,302 pairs and 6,724 displayable requests. The settled census fixture does not include a transient pre-hazard entry frame.

Random Abilities covers 310 identities over weighted request trials. Refused/caveated/clear trials change from **1,001,186 / 16,820 / 2,759,654** to **824,620 / 16,820 / 2,936,220**. Identities with any refusal change **174 → 133**; caveated identities remain **4**; clear-only identities change **133 → 174**.

For each ID in a row below, the displayed refused/clear counts are the same per identity; caveats are zero in these groups. This makes the candidate-level Random Abilities change explicit while keeping the table readable.

| Candidate IDs | Random Abilities refused / clear, before → after |
|---|---|
| Stage writers: 3, 22, 80, 83, 86, 88, 128, 133, 141, 153, 154, 155, 172, 192, 195, 201, 220, 224, 234, 235, 243, 264, 265, 270, 271, 275, 290; Drizzle 2; Drought 70 | 1,876 / 10,310 → 0 / 12,186 |
| Sand Stream 45; Snow Warning 117; Sand Spit 245; Surges 226–229; Seed Sower 269; Cloud Nine 13; Air Lock 76 | 12,186 / 0 → 0 / 12,186 |
| Chlorophyll 34; Swift Swim 33 | 1,758 / 10,428 → 0 / 12,186 |
| Color Change 16; Mimicry 250 | 1,876 / 10,310 → 1,758 / 10,428 |
| Protean 168; Libero 236 | 9,342 / 2,844 → 9,309 / 2,877 |

The former Chlorophyll and Swift Swim leader rows (66 battles / 228 requests and 38 / 150 in the historical census view) no longer appear as ability blockers. Their speed consequences are captured by current-action order; requests with those abilities still refuse when an independent move, format, or item cause remains. The item and ability cause removals also explain why some of the 906 requests remain refused after their Group B attribution disappears.

## Historical context

PR #102's census and handoffs describe its then-current baseline and are historical. Current disposition is governed by this matrix and the generated audits on the current branch.

## Group E follow-through

The [Group E audit](HNS_GROUP_E_CLOSURE.md) resolves the Wind Power / Electromorphosis handoff:
`data/battle_scripts_1.s:4949` writes the same `VOLATILE_CHARGE_TIMER` consumed by
`battle_util.c:6635`, now implemented exactly. No separate ability activation payload is inferred.
Protean/Libero's positive branch and primal-weather move semantics retain explicit refusals.
The Group E comparison reads the exact starting-main census artifact; the Random Abilities totals
in that artifact supersede this document's historical handoff prose.
