# H&S Group C immunity support (issue #89)

This note records the request-local rules implemented by the H&S 2.0.5 calculator. The audited
upstream checkout is `PokemonHnS-Development/pokehns-expansion` at
`1f42b74dff0e9fe942419845d040663dd829a973`.

## Ability immunities

The pinned `CanAbilityAbsorbMove` switch in `src/battle_util.c:2438-2499` supports the listed
type and flag matches. An immunity is a successful hit result with sixteen zero rolls,
`effectiveness = 0`, and an internal cause containing kind, source, and name.

| Ability | Exact trigger | Source |
|---|---|---|
| Volt Absorb (10) | Electric move | `src/battle_util.c:2444` |
| Motor Drive (78) | Electric move | `src/battle_util.c:2457` |
| Lightning Rod (31) | Electric move | `src/battle_util.c:2461-2464` |
| Water Absorb (11), Dry Skin (87) | Water move | `src/battle_util.c:2448-2451` |
| Storm Drain (114) | Water move | `src/battle_util.c:2465-2468` |
| Dry Skin (87) | The separate defender Fire move base-power modifier is ×1.25 in the target-ability BP slot | `src/battle_util.c:6796-6798` |
| Sap Sipper (157) | Grass move | `src/battle_util.c:2469-2472` |
| Earth Eater (297) | Ground move | `src/battle_util.c:2453-2456` |
| Well-Baked Body (273) | Fire move | `src/battle_util.c:2473-2476` |
| Flash Fire (18) | Fire move; unconditional in the pinned configuration because `B_FLASH_FIRE_FROZEN` is `GEN_LATEST` | `src/battle_util.c:2481-2484`; `include/config/battle.h:174` |
| Soundproof (43) | `soundMove` flag | `src/battle_util.c:2485-2488` |
| Bulletproof (171) | `ballisticMove` flag | `src/battle_util.c:2489-2492` |
| Wind Rider (274) | `windMove` flag | `src/battle_util.c:2477-2480` |
| Queenly Majesty (214), Dazzling (219), Armor Tail (296) | Effective priority greater than zero; excludes field targets | `src/battle_move_resolution.c:1403-1410` |
| Wonder Guard (25) | Ordinary powered move with type effectiveness at or below 1x | `src/battle_util.c:8421-8430` |
| Levitate (26) | Ground move while not grounded | `src/battle_util.c:8382-8389` |

The existing type chart is still evaluated independently. A type-chart zero, ability immunity, or
item grounding immunity can therefore contribute separate causal records when more than one rule
matches. Ring Target only converts type-chart zero cells to neutral; it does not clear an ability
immunity.

## Move metadata and priority

`tools/hns-move-mechanics/generate_hns_move_effects.py` now extracts literal move flags and base
priority from the pinned `gMovesInfo` table. The application resolves move names to the exact
pinned move ID before serializing these fields; caller-provided values cannot create or remove an
immunity. Computed or conditional fields are marked unknown and cause a hard refusal only when the
defender's relevant rule needs that field.

`HnsGroupCPolicy` combines the generated move data with exact live attacker HP, live field terrain,
and authoritative ability identity to derive dynamic priority. If that priority or the target class
cannot be proved for a priority-blocking defender, the boundary refuses the request.

## Items and ability suppression

| Item | Group C behavior | Source |
|---|---|---|
| Air Balloon (497) | Grounds are immune while held | `src/battle_util.c:8392` |
| Iron Ball (484) | Grounds the holder and applies the pinned Flying-vs-Ground override; no other ordinary hit modifier | `src/battle_util.c:8413-8418` |
| Ring Target (499) | Changes type-chart immunity cells to neutral before groundedness checks | `src/battle_util.c:8257` |
| Float Stone (495) | No ordinary damage effect; weight-dependent moves remain outside this rule | pinned item hold-effect table |
| Ability Shield (758) | Preserves the defender ability against Mold Breaker-family and literal `ignoresTargetAbility` checks; it does not prevent Gastro Acid | `src/battle_util.c:4974-5023`, `:9980` |

Mold Breaker (104), Teravolt (164), and Turboblaze (163) are hard blockers only when a relevant
defender immunity would otherwise participate and Ability Shield does not prevent suppression.
The pinned `ignoresTargetAbility` move flag sets the same ability-suppression state, so it normally
bypasses defender abilities but Ability Shield preserves the holder's ability. For example,
Sunsteel Strike bypasses Wonder Guard without Ability Shield and is blocked by Wonder Guard when
the target holds Ability Shield. Only literal source-backed move flags are admitted; conditional or
computed bypass cases remain fail-closed. Unrelated defenders and moves do not receive an ability
bypass blocker. `flashFireBoosted` is not part of the current live state: Flash Fire's defender-side
immunity is modeled, but an attacker with Flash Fire using a Fire move is refused until issue #91
supplies that boost state.

Purifying Salt's Ghost damage reduction is deferred to issue #91. Air Balloon, Iron Ball, Ring
Target, and Float Stone do not claim generic ordinary damage multipliers.

## Verification artifacts

The generated ability, item, and move inventories are committed beside their source decisions.
The issue #90 differential oracle now records upstream move flags, effective priority, target
class, and raw attacker/defender `status1` in corpus schema v3. Its Group C matrix includes the listed absorptions, flag matches,
priority blockers, Wonder Guard, Levitate, grounding items, Ring Target, and positive/negative
controls. Native calculator tests assert exact zero rolls and causal records; production boundary
tests cover authorization, current ability/item authority, Mold Breaker relevance, and pinned move
metadata. Wonder Guard's oracle matrix separately covers neutral, resisted, chart-immune, and
super-effective hits.
