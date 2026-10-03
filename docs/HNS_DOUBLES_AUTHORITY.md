# H&S 2.0.5 ordinary-hit Doubles source audit

Subtask starting head: `ce4acbe59918ee311aab472732e2771bfafa7319` on
`feat/hns-group-e-closure`, existing draft PR #119. Source references below are
relative to `PokemonHnS-Development/pokehns-expansion` at
`1f42b74dff0e9fe942419845d040663dd829a973` (Release-v2.0.5).

## Selected-hit operands (audit before implementation)

| Surface | Pinned source | Authority and arithmetic |
|---|---|---|
| Topology | `include/battle.h:1062-1134`, `include/constants/battle.h:57-58` | `gBattlersCount`, positions, party indices, absent flags and HP identify live participants. `IsDoubleBattle` additionally requires `gBattleTypeFlags & BATTLE_TYPE_MORE_THAN_TWO_BATTLERS` (`include/battle.h:1157-1159`); both packet reads must agree with the original flags word. Side is position & 1; the damage functions use **index xor 2** for partners. Do not replace that with a guessed flank. `IsBattlerAlive` checks index bounds, HP, then absent flags. |
| Target count | `src/battle_util.c:6122-6149` | BOTH counts defender and index-xor-2 partner using absent flags; FOES_AND_ALLY additionally counts attacker partner. SELECTED/DEPENDS/RANDOM/OPPONENT require defender liveness; USER requires attacker liveness. OPPONENTS_FIELD returns 1; remaining classes return 0. |
| Spread | `src/battle_util.c:7403-7415,7768`, `include/config/battle.h:47` | Gen III: exactly two targets gives 2048/4096, three targets gives 4096/4096. Apply half-down to base damage before weather, crit and random. This predicate is **not limited to Smogon allAdjacentFoes metadata**. |
| Screens | `src/battle_util.c:7527-7550,7724-7725` | Reflect, Light Screen, Aurora Veil use 2732/4096 in Doubles, 2048/4096 in Singles. No live-defender-count distinction. Crit/self-inflicted bypass; opposing Infiltrator bypass. Screens enter the Other accumulator, never an early standalone multiplication. Aurora Veil remains independently gated unless its side-status authority is supported. |
| Helping Hand | `src/battle_util.c:6630-6632`, `include/battle.h:89` | Current `gProtectStructs[attacker].helpingHand` is a 3-bit stack count. Compose one 6144/4096 half-up multiplication per stack at the beginning of the BP modifier accumulator, before Gems and Charge. Historical actions cannot establish the operand. |
| Attacker partner BP | `src/battle_util.c:6763-6784` | Live effective partner Battery (special) / Power Spot (all ordinary hits) adds 5325/4096; Steely Spirit (Steel) adds 6144/4096, after field auras and before defender BP ability. |
| Plus/Minus | `src/battle_util.c:7026-7044`, `include/config/battle.h:179` | Special attacker with either Plus/Minus and a live effective Plus/Minus partner: 6144/4096 in attacker-ability Attack accumulator slot (half-down). Same-ability pair is enabled by GEN_LATEST. Physical/defender roles do not receive it. |
| Flower Gift | `src/battle_util.c:7046-7049,7141-7153,7296-7299,7329-7341` | Self and partner modifiers independently depend on effective ability, **live CHERRIM_SUNSHINE species**, affected Sun, and physical Attack or special Defense. Partner slot follows opponent ability slot and precedes Ruin and items. |
| Field auras | `src/battle_util.c:6753-6761,5051-5061` | Any live effective Dark/Fairy Aura holder activates matching type BP; any Aura Break changes that single multiplier from 5448/4096 to 3072/4096. Holder count does not stack. Must inspect all active holders. |
| Ruin | `src/battle_util.c:7155-7160,7343-7348,10732-10760` | Any unsuppressed Ruin volatile in the count slots activates the field, including fainted/absent slots; subject's own matching flag exempts it. 3072/4096 at the Attack/Defense accumulator after partner Flower Gift. Raw effective ability alone cannot prove current Ruin flag state. |
| Friend Guard | `src/battle_util.c:7640-7655,7733,7740` | Live effective defender partner yields 3072/4096 in Other accumulator immediately after defender ability, on either raw-speed ordering branch; before items. |
| Partner priority immunity | `src/battle_move_resolution.c:1391-1440` | Queenly Majesty, Dazzling and Armor Tail can cancel a hit targeting their partner. Only an authoritative nonpositive effective priority clears that holder; positive/unread priority retains a specific selected-hit refusal. |
| Pledge STAB | `src/battle_util.c:7426-7427`, `include/battle.h:664` | A current `BattleStruct.pledgeMove` flag substitutes partner typing for STAB even though the local modifier does not inspect the move effect. Observe and refuse this execution state; ordinary move identity alone cannot prove the flag false. |
| Effective ability | `src/battle_util.c:4996-5032` | Gastro Acid, unsuppressible metadata, transformed Comatose exception, Neutralizing Gas, active Ability Shield and `CanBreakThroughAbility` all participate. Raw partner ability is insufficient. Unresolved suppression must refuse. |
| Weather holders | `src/battle_util.c:HasWeatherEffect`, `IsBattlerWeatherAffected` | Cloud Nine/Air Lock and primal weather on **any** live holder can change weather applicability; a two-participant weather proof is insufficient in Doubles. |
| Selected target | `src/battle_util.c:GetBattleMoveTarget`, `IsAffectedByFollowMe`; `src/battle_script_commands.c` targeting/redirection | Follow Me/Rage Powder and Lightning Rod/Storm Drain can replace the chosen target. Menu snapshots do **not** prove that `gBattlerTarget` is the resolved target of an arbitrary future request. Retain a specific refusal for unresolved redirecting branches; do not consume stale action globals. Spread requests retain their selected per-hit defender. |
| Commander | `src/battle_util.c:4783-4805,10650-10689` | Partner species/state and semi-invulnerability change execution/target availability; already-written stat stages alone do not prove selectable target identity. Retain refusal for unresolved Commander state. |
| Copied/trigger state | Costar, Hospitality, Dancer, Telepathy and switch-in scripts | Costar copies live stages, Hospitality writes HP, Dancer schedules another move, Telepathy can prevent ally damage. Opposing selected hits after settled writers can be request-locally irrelevant; unresolved execution and ally-target requests must still refuse. |

The audit also checked the whole `CalcMoveBasePowerAfterModifiers`, `CalcAttackStat`,
`CalcDefenseStat`, `GetOtherModifiers`, `DoMoveDamageCalcVars`, effective-type,
effective-hold-effect, immunity and adjusted-damage paths. Existing complex moves,
Me First, multi-hit/Parental Bond, shields, item activation and unresolved action
order retain their independent gates. Adding Doubles topology does not authorize them.

## Implementation contract

Use a versioned live battle operand packet rather than four complete calculator
Pokemon. It must carry all four indexed identity/liveness/suppression records,
positions and party mapping, current Helping Hand counts and redirection state.
The existing participant observations still provide full stats for the explicitly
selected attacker and defender. Read explicit battler indices through the same
lifecycle/ABI checks; retain legacy single-role ambiguity semantics. Bind every
derived multiplier at `CalcRequestBoundary`, reject disagreeing packets, and
preserve missing/old tuple refusal. No trainer or census data is production authority.

## Runtime packet and selected participants

The legacy JNI prefix of 103 words remains intact. The extended tuple has **162
words**: `[103]` explicitly marks a readable packet, `[104]` is version **1**,
`[105]` is count, `[106]` absent flags, `[107..108]` side Follow Me timers and
`[109]` action flags (bit 0 Mold Breaker active, bit 1 combined Pledge active).
`[110..161]` contains four indexed 13-word records: index, position, party slot,
HP, current species, raw ability, current item, Gastro Acid, stored Neutralizing
Gas, transformed, semi-invulnerability, four-bit Ruin mask and Helping Hand count.

`generate_hns_live_battle_layout.py` compiles the exact pinned ARM ABI:
ProtectStruct 12 bytes, Helping Hand bit 32 / width 3; SideTimer 32 bytes,
Follow Me bit 144 / width 4; BattleStruct Mold Breaker bit 7559 and Pledge bit
6725. Pinned EWRAM symbol bindings are `gProtectStructs=0x020000B8` and
`gSideTimers=0x02000258`, cross-checked against the pinned source symbol map
where present. The BattleStruct pointer uses the existing release-bound profile
address. The same generator extracts the complete unsuppressible ability set
from pinned `gAbilitiesInfo`, checking the GEN_LATEST configuration. Source checks
regenerate both the C header and Kotlin set. This subtask adds no new ROM or save
transition evidence and reads/commits no game binary.

Native INDEX_0 through INDEX_3 select engine indices directly. Each observation
uses that index's authoritative position and party mapping; badge and gimmick
side reads use the observed position. The lifecycle/ABI and living-participant
checks still apply. Two full minimal-packet reads must match and agree with the
original count, absent flags, battle-type flags and selected identity, HP, item,
position and party slot. A four-count without the pinned IsDoubleBattle flags
cannot publish the packet. The legacy PLAYER/OPPONENT role APIs retain their
AMBIGUOUS behavior when multiple participants are present.

The coordinator freezes emulation once for the indexed batch. Calc and Battle
expose explicit attacker/target selection from observed slots; neither selects
the first enemy or assumes a flank. `CalcRequestBoundary` rejects differing
packets, malformed indices/positions/party topology, invalid enum domains,
missing participant volatile authority and mismatched identity/slot/HP state.
Old, short and unknown-version tuples never acquire neutral partner state.
The boundary then derives the seven minimal engine operands, using live HP and
absence for partner abilities/weather/aura holders, and all count slots for
source Ruin flags. The full four Pokemon are never calculator inputs.

## Newly exact and remaining request-local branches

Ordinary opposing-hit requests with complete authority consume spread count,
Doubles screen factors, Helping Hand, Battery, Power Spot, Steely Spirit,
Plus/Minus, partner Flower Gift, Friend Guard, field auras/Aura Break, weather
suppression and Ruin at the stages in the audit table. Spread target count comes
from the existing boundary helper; it is not derived in JS from move defaults.
HP-zero without an absent flag still counts for BOTH/FOES_AND_ALLY, exactly as
the pinned source does, while it deactivates live-holder partner abilities.

Plus/Minus retain their global Group E hard assignments; the boundary-owned
ordinary Doubles branch models their exact special Attack slot. Holder-side
Friend Guard, Battery, Power Spot and Telepathy can be request-locally irrelevant
for an opposing hit, while actual partner modifiers are consumed separately.
A separate caveated mechanic never neutralizes exactly modelled Plus/Minus.
Existing Singles proofs and all independent move/item/ability/field/gimmick
blockers remain in force.

Unresolved branches retain hard refusal: random/ally targets, observed Follow
Me/Rage Powder or Lightning Rod/Storm Drain redirection on nonspread requests,
Commander, active combined Pledge, positive/unread effective priority protected
by a partner's Queenly Majesty/Dazzling/Armor Tail, Gas/Mold Breaker action
suppression and ability-ignoring moves. Partner Flower Gift with Utility Umbrella
requires active hold-effect authority that this minimal record does not carry,
so it refuses. Costar, Hospitality and Dancer gain no general Doubles clearance;
their existing narrower proofs still apply where available. Aurora Veil,
unsupported moves, multi-hit totals and complex execution/gimmicks remain
independently gated. This is an **authoritative ordinary-hit Doubles subset**.

## Oracle and census contract

The 49 new pinned-engine scenarios cover target counts 1/2/3, ally-inclusive Petal Blizzard and nonspread controls,
Reflect/Light Screen with either partner topology, Helping Hand 1/2/7, all new
partner multipliers, Gastro Acid and absent-partner controls, Flower Gift,
auras/Aura Break, each Ruin flag, partner Cloud Nine/Air Lock in Rain/Sun and
modifier composition. All comparisons use the shipped bundle and all 16 rolls.
Weather-holder suppression is established by an earlier real Gastro Acid turn,
before the engine caches weather; the late critical-hit hook is insufficient
for that operand. All 1,845 historical entry objects remain unchanged. The
source spread predicate genuinely resolves the two historical #100 vectors.

The host-only census extends its documented neutral runtime context to supply
the new required fields: explicit neutral Insomnia partners, no positive
partner/action/field state. It changes neither trainer inventory nor reference
teams nor the 24,278 request keys, and is never called by the production reader.
These measurements describe each original matchup under the stated neutral
context; they are not a claim about every possible live ally state. Production
partner arithmetic comes exclusively from the native packet.

## Local verification

The full census was regenerated with
`DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh test`, followed
by a passing non-generating `./ci.sh all` (1,039 Kotlin tests, native reader and
calculator suites, tooling and debug APK). The final `./ci.sh source-check`, oracle check and fixed-point
self-tests pass. All 1,894 oracle scenarios match all 16 rolls; the historical
1,845 entry objects and all 24,278 census request keys compare unchanged against
the subtask starting head. Trainer inventory and reference-team fixtures are
unchanged. Exact-head source validation and Actions evidence is recorded on
PR #119. No live ROM/UI session was performed for this extension.
