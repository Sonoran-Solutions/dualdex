# H&S repeated-strike result contract — issue #145

## Decision and scope

**GO for a bounded first implementation: six plain fixed-two-hit EFFECT_HIT moves,
with structured initial-hit damage and executable target HP-loss totals only under
an affirmative sequence-stability proof.** No repeated-strike support ships here.
Do not obtain that proof by reusing a single-hit ability/item irrelevance verdict.
Do not multiply a selected-hit range and call it the full move.

Starting main: `1757fbfd59c3a39e4975dcbce0ebfa44182f55aa`. `git fetch origin main`
completed before inspection; [PR #144](https://github.com/Sonoran-Solutions/dualdex/pull/144)
was MERGED (merge time `2026-10-06T02:56:31Z`) and its merge commit is this SHA.
There are no subsequent starting-main commits. Work starts on
`design/hns-repeated-strike-contract`; unrelated changes are retained.
[Issue #145](https://github.com/Sonoran-Solutions/dualdex/issues/145) stays open for review.

Mechanics authority: `PokemonHnS-Development/pokehns-expansion`, release
`Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.
All upstream references below use that immutable commit. H&S remains **ESTIMATED**;
FULLY_MODELLED is a request coverage tier, not a VERIFIED profile claim.
Hardware is **NOT_REQUIRED / NOT_RUN**. No ROM, save, production code, generated
metadata, census population, neutral assumptions, or historical oracle entry changes.

## Verified census and inventory

Inputs are current-main `census.json.gz`, `trainer_inventory.json`, and
`hns_move_damage_metadata.json`; the runnable check is
`python3 tools/hns-calc-census/check_repeated_strike_design.py`.
It independently selects EFFECT_HIT metadata with `multiHit` or `strikeCount`,
checks exact membership and IDs, and counts actual move-blocked requests.
It does not simulate removal of production policy gates.

392 blocked requests; 138 battles; 160 affected lead pairs; 166 lead requests;
336 requests have only the recorded move blocker. These are opportunities and
preliminary ceilings, never promised unlocks. A lead pair is the existing
trainer/reference-team pair at party slot zero; affected does not mean that the
whole pair becomes displayable. Counts of battles/pairs overlap across slices. Recorded overlaps: 36 ability-condition
requests (Swarm 28, Technician 8), 14 ability-effect requests (Sturdy 12, Heavy
Metal 2), and six item-effect requests (attacker Scope Lens). These are existing
limitations, not a complete future transition audit; Dice already present in
some requests is not thereby admitted for count handling.

| Move | ID | Count kind | Requests | Battles | Lead pairs | Lead requests | Move-only ceiling |
|---|---:|---|---:|---:|---:|---:|---:|
| Arm Thrust | 292 | variable2to5 | 2 | 1 | 0 | 0 | 2 |
| Bone Rush | 198 | variable2to5 | 8 | 4 | 0 | 0 | 8 |
| Bonemerang | 155 | fixed2 | 6 | 2 | 2 | 2 | 6 |
| Bullet Seed | 331 | variable2to5 | 32 | 12 | 16 | 16 | 32 |
| Comet Punch | 4 | variable2to5 | 2 | 1 | 0 | 0 | 0 |
| Double Hit | 458 | fixed2 | 40 | 16 | 22 | 22 | 30 |
| Double Kick | 24 | fixed2 | 34 | 13 | 12 | 12 | 32 |
| Double Slap | 3 | variable2to5 | 30 | 14 | 18 | 18 | 30 |
| Dual Chop | 530 | fixed2 | 16 | 8 | 2 | 2 | 12 |
| Dual Wingbeat | 742 | fixed2 | 4 | 2 | 2 | 2 | 0 |
| Fury Attack | 31 | variable2to5 | 44 | 20 | 16 | 16 | 38 |
| Fury Swipes | 154 | variable2to5 | 48 | 23 | 30 | 30 | 42 |
| Icicle Spear | 333 | variable2to5 | 12 | 5 | 8 | 8 | 12 |
| Pin Missile | 42 | variable2to5 | 30 | 15 | 16 | 16 | 18 |
| Rock Blast | 350 | variable2to5 | 52 | 18 | 12 | 12 | 50 |
| Scale Shot | 727 | variable2to5 | 2 | 1 | 0 | 0 | 2 |
| Spike Cannon | 131 | variable2to5 | 22 | 9 | 10 | 10 | 18 |
| Tail Slap | 541 | variable2to5 | 2 | 1 | 0 | 0 | 0 |
| Twin Beam | 814 | fixed2 | 4 | 2 | 0 | 0 | 4 |
| Twineedle | 41 | fixed2 | 2 | 1 | 0 | 0 | 0 |

## Count authority and separate families

`MoveInfo.strikeCount` is a four-bit field; `multiHit` takes precedence.
The actual ordinary repeated family contains seven fixed-two-hit and thirteen
variable 2–5 moves. `CancelerMultihitMoves` selects the counter once in the
attack-canceler path. Re-entering the move script does not reset the completed
attack-canceler cursor; `MoveValuesCleanUp` clears effect/message bookkeeping,
not HP, stages, held items, ability identity, or the selected count.
`MoveEndMultihitMove` decrements the remaining counter; actual executed hits can
be fewer. Losing Skill Link or Dice later does not reselect the nominal count.
A live counter halfway through execution is remaining count, not initial count;
recovering initial count requires observation/history. Future pre-move prediction
needs neither that counter nor RNG prediction.

| Family | Pinned count/execution rule | Contract boundary |
|---|---|---|
| Fixed EFFECT_HIT | metadata `strikeCount=2`; Skill Link does not turn it into five; Dice does not alter it | six plain moves first; Twineedle separate |
| Ordinary variable EFFECT_HIT | `multiHit=TRUE`: effective Skill Link → 5, else effective Dice → uniform 4 or 5, else weighted 2/3/4/5 | plain twelve first; Scale Shot separate |
| Triple Kick / Triple Axel | `EFFECT_TRIPLE_KICK`, `strikeCount=3`; power multiplied by `1 + strikeCount - remainingCounter` | escalating power; normally accuracy each hit; Skill Link/Dice skip later accuracy but do not change count |
| Population Bomb | `strikeCount=10`, distinct effect; effective Dice chooses uniform integer 4–10 | normally accuracy each hit; Skill Link skips later accuracy, does not force five; Dice also skips later accuracy |
| Beat Up | counter counts eligible living, non-egg, non-statused party members; power uses party data | party authority; separate family, not this schema's first implementation |
| Parental Bond | separate eligibility predicate and two-hit state; second-hit damage modifier | separate created extra hit; exclusions and phase state, not ordinary two-hit admission |
| Species override / smart targets | separate `EFFECT_SPECIES_POWER_OVERRIDE` branch and TARGET_SMART retargeting | no blanket admission; doubles remains excluded |

Pinned `B_MULTI_HIT_CHANCE=GEN_LATEST=GEN_9`: ordinary counts have probabilities
2:35%, 3:35%, 4:15%, 5:15%. The older config branch is 37.5%,37.5%,12.5%,12.5%.
`GetConfigInternal` reads compiled `sConfigChanges` outside TESTING; the override
pointer is test-only. This count distribution is not a live H&S challenge option.
The audit did find a separate H&S survival option: `tx_Mode_Sturdy == 1` gates
modern Sturdy in `GetAdjustedDamage`. Do not assume Sturdy solely from its ID.

**Useful exact results do not require predicting count RNG.** Initial-hit damage
can be exact for its stated hit/crit condition. Conditional totals for N=2,3,4,5
can be exact when execution is modelled; an aggregate min/max is the extrema of
those valid conditional branches. Count probabilities alone do not justify
expected damage or KO probability. Neither probability feature is proposed.

### Skill Link

The effective attacker ability in the canceler context forces five only for
`IsMultiHitMove`. It precedes Loaded Dice and species override. For fixed count,
Triple Kick/Axel and Population Bomb it instead affects the later-accuracy skip
predicate. It has no generic power to repair Beat Up or Parental Bond authority.
`GetBattlerAbilityInternal` handles Gastro Acid, global Neutralizing Gas, Ability
Shield and Mold Breaker. Mold Breaker does not bypass the attacker's own ability
(`CanBreakThroughAbility` excludes attacker==defender).

Reuse observed runtime identity, persistent volatile authority and the existing
field/suppression gates. Runtime identity alone is not the suppressed verdict:
`HnsFieldAbilityAuthority` deliberately leaves global Gas cases unknown. A future
request-local count rule needs an authoritative effective Skill Link verdict,
known move metadata and existing live settlement/format gates, not a new RNG or
hit-history read. Unknown suppression remains refused; do not generalize the
field helper's currently reviewed ability set without evidence.

### Loaded Dice

Authority is `HOLD_EFFECT_LOADED_DICE`, not an item label. Ordinary variable moves
select uniform 4/5 (50% each), unless Skill Link already chose five. Fixed two-hit
moves are unchanged. Population Bomb selects uniform 4–10 (each 1/7); Triple
Kick/Axel retain three. Those latter two families additionally use Dice to skip
later accuracy checks. Beat Up is not assigned a Dice count branch.

Reuse `HnsHoldEffectAuthority` / `HnsHoldEffectResolution` used by capability policy
and `DamageCalculator`'s `hnsEffectiveHoldEffect` serialization. Pinned
`GetBattlerHoldEffectInternal` returns NONE for Embargo, Magic Room or unsuppressed
Klutz. Known suppressed Dice restores ordinary count rules; unknown is not NONE.
No new live state is needed for initial count if the existing shared resolution
is complete. Add request-local relevance for this family; don't globally relax
item gates or ignore effective ability authority needed by Klutz.

## Execution flow and hit properties

1. Move setup (`battle_util.c` selected-move path) sets the move's battle type and
   clears damage results. `DoAttackCanceler` runs ordered cancellation/pre-attack
   handlers, including count selection. Count is nominal, before damage.
2. `BattleScript_EffectHit` runs accuracy, pre-attack additional effects and
   `Cmd_damagecalc`, then hit animation, HP bar and `datahpupdate`.
3. `Cmd_damagecalc` constructs a fresh BattleContext. `CalculateMoveDamage`
   refreshes effective attacker/defender abilities and items, effectiveness,
   crit, power/stat modifiers, random damage and survival adjustment.
4. `MoveDamageDataHpUpdate` mutates target HP, clamps applied loss to available HP,
   records damage tracking; Disguise/Ice Face can replace target damage with zero.
   Calculated damage and applied HP loss must be separate evidence fields.
5. Additional effects run after HP application. `moveendall` visits the ordered
   handlers: defender and attacker reactions, on-hit forms, target/attacker items,
   faint block, target threshold items, then the repeated-hit handler.
6. `MoveEndMultihitMove` decrements count and checks target fainting. Another strike
   requires both HP values nonzero and attacker not asleep (unless usable asleep)
   or frozen. Failure/unaffected/unable-to-use state also prevents continuation.
   It resets move-end cursor and re-enters the move script for another damage calc.
7. Once the loop ends, move-level recoil/block effects, Color Change,
   Kee/Maranga/threshold handling, eject items, Life Orb/Shell Bell and other
   after-move handlers run. Scale Shot calls its stat-change script at loop exit,
   conditional on `!NoAliveMonsForEitherParty()`.

The selected effective move **type is cached from move setup**, not unconditionally
re-run by the ordinary loop. Category reads `GetBattleMoveCategory`, including
H&S `optionStyle` and cached battle type. Ability replacement can change the next
hit's modifiers without necessarily changing this cached type. Any later support
for type/ability-changing reactions must observe the actual stage-specific source
values; recomputing a generic move from scratch is not automatically faithful.

Critical is decided per strike by `IsCriticalHit`; ordinary random damage is
independently requested per strike by `DoMoveDamageCalcVars` with
`RNG_DAMAGE_MODIFIER` in 0..15, giving 100..85%. Independent strikes may have
different crit flags and random values even with unchanged battle state. “Sixteen
rolls for two hits” with the same index twice samples only a diagonal of 256
noncrit pairs. It is not the full sum distribution.

Ordinary EFFECT_HIT repeated strikes check accuracy once; the skip helper bypasses
later accuracy when `multiHitOn` is set. Triple Kick/Axel and Population Bomb
normally check each hit, except effective Skill Link/Dice bypass later checks.
Twineedle's 20% poison additional effect is evaluated per strike, subject to
eligibility/status/immunity/Sheer Force. Scale Shot's marker has dedicated loop-end
handling rather than a per-hit stat drop. Crit and accuracy probabilities remain
outside product scope; show damage **conditional on successful hit(s)** and on the
explicit crit mode. Current single crit toggle must mean “all executed strikes
noncritical” or “all executed strikes critical”; never “the move rolled one crit.”
Mixed per-strike crit vectors belong in oracle evidence now, UI controls later.

## Between-hit state matrix

Classes refer to the work required for **truthful executable totals**, not merely
initial-hit arithmetic: A=current authority suffices under a stated stable proof;
B=small bounded additional authority; C=state evolution/branching; D=refused in
these proposed slices. C mechanics are refused until explicitly implemented.
An existing single-hit irrelevant/stage-observed classification does not prove A.
Timing below follows ordered move-end handlers; eligibility, suppression, contact
and survival predicates still apply on every attempted activation.

| Mechanic | Source timing / consequence | Class / first-slice disposition |
|---|---|---|
| Target HP, faint | applied each hit; faint block before loop; loop requires live target | A for stable damage + known HP clamp; no hit to fainted target |
| Sturdy | lethal-hit adjustment at full HP, modern config AND H&S Sturdy option | B for option authority, C for survival; refuse active/unknown |
| Focus Sash | lethal full-HP hit reduced to HP−1; hung-on message consumes sash | C; refuse relevant/unknown, don't repeat full-HP protection |
| Focus Band | `Random()%100` on lethal adjustment, effect parameter threshold | C stochastic survival; D in first slices |
| Endure | volatile checked each lethal hit, leaves one HP | B for complete volatile authority then C bounded survival; refuse |
| Affection survival | config-gated in adjusted damage; pinned config disabled | A only source-proven disabled config, never invent active support |
| Multiscale / Shadow Shield | full-HP predicate read per damage calc; changes after damage | C; do not multiply first-hit reduction |
| Defeatist; Overgrow/Blaze/Torrent/Swarm | attacker HP predicate read per hit in attack modifiers | A if attacker HP cannot change; C with contact loss/heal |
| HP-threshold healing/stat berries, Enigma | target threshold handler before next hit; attacker threshold handler also before loop | C, may heal or change stages and survival; refuse |
| Disguise / Ice Face | zero target loss then form reaction before loop; Disguise may lose HP | C, initial form/transformation required; refuse |
| Weak Armor / Stamina / Water Compaction | target move-end ability before each next hit; write defense/stages | C; observed initial stages alone insufficient |
| Gooey / Tangling Hair | contact reaction before next strike, attacker Speed drop | C state writing; possible speed-dependent modifiers; first slice refuse unless complete request-local irrelevance proof |
| Weakness Policy; Snowball / Absorb Bulb / Cell Battery / Luminous Moss | target on-hit item stage changes/consumption before loop | C; can interact with defender offensive-stat mechanics; refuse |
| Kee Berry / Maranga Berry | dedicated handler AFTER multihit loop; boost once on consumption | A for sequence arithmetic if existing item gate affirmatively proves scope; no claim of after-move state |
| Resist berries | damage modifier sets berryReduced; pre-attack animation script consumes before HP application and next strike | C; existing initial-hit berry authority is insufficient for totals |
| Rough Skin / Iron Barbs / Rocky Helmet | each eligible contact strike, before next hit; attacker HP loss/faint possible | C, including threshold modifiers; refuse unless complete suppression/contact proof |
| Flame Body / Static / Poison Point | each eligible contact strike, RNG/status eligibility; burn can change next damage | C branching; refuse |
| Poison Touch / Toxic Chain | attacker move-end status handling before repeated-hit loop; Poison Touch requires eligible contact, Toxic Chain priority is rolled in `Cmd_setadditionaleffects` without a contact requirement; can poison defender before next strike | C branching/state evolution; Marvel Scale can raise the Defense used by physical hit two; Slice A refuses unless status application is source-proven impossible or irrelevant to every later-hit damage operand |
| Effect Spore | per-contact status selection incl. sleep; can terminate loop | C branching; refuse |
| Mummy / Lingering Aroma / Wandering Spirit | contact ability replacement/swapping before next strike | C; may change modifiers/suppression, count itself stays selected |
| Color Change / Berserk / Anger Shell | ABILITYEFFECT_COLOR_CHANGE in dedicated handler after loop; type or threshold stage changes | A for sequence if existing initial operands/gates exact; no post-move state claim |
| Anger Point / Justified / Rattled / Steam Engine / Thermal Exchange | move-end ability dispatch/stat-writing reactions before loop when predicates match | C unless source-proven irrelevant to every later hit; do not accept blanket stage snapshot proof |
| Sand Spit / Seed Sower / Wind Power / Electromorphosis / Toxic Debris | target ability dispatch before next hit; weather/terrain/Charge/hazard changes | C or D; weather/terrain can change later damage, no blanket irrelevant proof |
| Life Orb | multiplier each hit; HP cost at dedicated post-loop handler | A for target loss if exact effective item and other gates clear; no attacker final-HP claim |
| Shell Bell | healing after loop from accumulated saved damage | A for target sequence; post-move healing outside result scope |
| Recoil / drain | move-specific scripts/handlers; no recoil/drain metadata in six plain fixed moves | A absent for first family; D other move families pending separate audit |
| Item consumption / transfer (gems, Sticky Barb, Pickpocket/Magician, Symbiosis) | source-specific phase; on-hit transfers and flags may persist | C/D; shared effective hold effect for hit one is not a transition model |
| Critical / damage RNG | fresh request each strike | A conditional crit mode, independent roll combinations; no probability output |
| Accuracy | ordinary later-hit skip; distinct exceptions above | A conditional hits for ordinary family; D accuracy probabilities |
| Secondary effects / Twineedle | post-HP each strike; poison/other trigger interactions | C; Twineedle separate slice, even if poison alone does not modify this hit's damage |
| Scale Shot | defense down/speed up at sequence end, battle-continuation guard | B bounded dedicated move-level scope proof; separate slice |
| Type / category / suppression | type cached at setup, category source accessor; abilities/items refreshed per calc | A when stable; C when ability/item/state changes; unknown bypass D |
| Substitute, Doubles/smart targets, unsupported gimmicks/weather/execution | existing authority exclusions plus independent sequence concerns | D; preserve all existing refusals |

### Attacker-side status transition counterexample

Poison Touch + physical Double Hit against a healthy, initially unstatused Marvel
Scale defender is not a stable sequence. Hit one uses unstatused Defense.
`ABILITYEFFECT_MOVE_END_ATTACKER` checks damage, contact, poison eligibility and
its 30% RNG roll, then calls `BattleScript_AbilityStatusEffect`, which applies
nonvolatile status before `MoveEndMultihitMove` can start hit two.
`CalcDefenseStat` now sees `status1 & STATUS1_ANY` and, when `usesDefStat` is true,
applies Marvel Scale's 1.5× Defense modifier. With effective abilities active,
physical category and no Wonder Room, this can change hit two's actual damage.
A repeated first-hit range therefore cannot represent the executable total.

Toxic Chain is broader: `SetToxicChainPriority` checks a living, damaged, poisonable
target and uses `RandomWeighted(RNG_TOXIC_CHAIN, 7, 3)`. The attacker move-end
handler clears that priority and applies toxic status unless target effects are
blocked. It does not require contact, so physical Bonemerang can produce the
same Marvel Scale transition. These are status-triggered damage dependencies,
not merely end-of-move status annotations. The downstream audit must include
status-sensitive defensive operands and any status-triggered item/ability
reaction before the next strike. Do not clear these abilities through a current
single-hit irrelevance rule or an initially healthy defender snapshot.

## Product display and target survival

Recommend **hybrid structured result** (D): primary value is executable target
HP loss when exact for the stated conditions; otherwise an explicitly scoped
initial-strike result may be shown only under approved caveat policy.

- Per-hit range alone: useful as “first strike: 18–22”; insufficient as move total.
- Per-hit × count: allowed only as a secondary **nominal** arithmetic explanation
  under stable-state proof. Never a default damage headline.
- Total range: allowed as executable applied HP loss, not uncapped overkill damage.
- Structured result: shows nominal possible count(s), first-strike range,
  conditional executable totals when valid, assumptions and refusal/caveat reasons.

For stable two-hit damage with no survival/healing/reactive mechanics and known
positive target HP H, executable total extrema are `min(H, 2*lo)` and
`min(H, 2*hi)`. This is a theorem under the stable proof and explicit crit mode,
not a general multi-hit algorithm. A path stops after first-hit KO; damage is
never applied to a dead target. Full sequence evidence must validate this bounded
optimization against all 256 independent roll pairs. Potential executions can
have one or two hits despite nominal count two. Exact endpoint interval does not
claim every interior integer is reachable and does not encode probabilities.

Example with H=30 and first-hit 18–22: “30 HP lost total · up to 2 hits”; details
“First strike 18–22; successful-hit, all noncritical scenario.” With H=20 some
paths terminate on strike one; never render a guaranteed “22 on hit two.”
Without HP authority there is no executable total. Do not silently change to
nominal full-sequence semantics or use max HP as current HP.

For variable counts, render “2–5 nominal hits” plus totals conditional on N under
stable proof; optional aggregate range is labelled “across possible counts.”
It is not an expected value. Early stopping still applies within each branch.
For changing state, first-strike-only output is potentially useful, but the UI
must say “First strike only; later strikes/total unavailable” and must not label
that range “per hit.” No total is present until sequence evolution is exact.

## Minimal proposed typed contract (design, not shipping code)

Keep existing single-hit behavior; add an optional structured sequence member to
`DamageCalculationResponse`/the H&S JSON response in the later implementation.
For a sequence the old top-level single-range fields cannot be treated as move
whole-range by legacy consumers. Update parser and every rendering caller together;
if a consumer cannot render the structured result, it must show unsupported.

```kotlin
// Proposed only; names can follow existing project conventions.
data class RepeatedStrikeResult(
    val nominalCounts: List<Int>,          // sorted, unique, positive; [2] or [2,3,4,5]
    val firstStrikeRolls: List<Int>,       // 16 ordered calculated rolls for stated crit condition
    val totals: List<ConditionalTotal>?,   // null = unavailable; never a fake zero range
    val assumptions: List<String>,
    val totalUnavailableReasons: List<CalcLimitation>
)
data class ConditionalTotal(
    val nominalCount: Int,
    val minHpLoss: Int,
    val maxHpLoss: Int,
    val minExecutedHits: Int,
    val maxExecutedHits: Int
)
```

Invariants: totals are executable target HP loss, stop on either participant's
source-defined termination; each count has one entry when totals are complete;
min≤max≤initial target HP for the first stable no-healing slice. Missing totals
must have reasons; refused result contains no displayable number. Count uncertainty
is expressed by the list, no redundant fixed/variable enum. No common-per-hit field
that implies later hits share the first range. No probability or nominal-total
field. Internal H&S computation retains a stable-proof outcome (or supported
transition model identity later), explicit crit condition and effective operand
provenance; those proofs are not user strings or trusted UI booleans.

UI-facing adapter maps this to first-strike label, count text, total text/conditional
rows, executed-hit bounds and visible assumptions/limitations. Reuse capability
headline/presentation; H&S still says Approximate. No automatic KO-chance text from
the legacy range. Generic model owns optional sequence payload and damage scope;
H&S owns count rules, suppression proofs, stability guards and source transitions.
Do not build a generic battle simulator or one-implementation interface. Later
state-changing support can compute conditional totals behind this contract without
claiming identical per-hit ranges; full traces remain evidence, not UI payload.

## Capability policy

**FULLY_MODELLED:** exact admitted move metadata and count alternatives; initial
operands/suppression all authoritative under current gates; every path in stated
hit/crit conditions has exact applied-loss endpoints and stopping semantics;
source/engine evidence for independent rolls and all admitted reactions. For first
slice require affirmative stability of all damage-relevant operands through both
strikes, and no healing, survival or attacker-termination mechanism. The stable proof
must enumerate applicable source handlers using exact metadata/effective ability
and item identities on both sides, not only currently reported blockers. Deny
unknown reactions by default. Check HP predicates, forms, contact, type/category,
status, stages, Charge, gem/berry consumption, weather/terrain and ability/item
replacement. Explicitly include attacker Poison Touch/Toxic Chain and downstream
Marvel Scale Defense changes; reject them in Slice A unless complete source-backed
status impossibility or irrelevance to every later-hit operand is established.
Source-proven inapplicable reactions may be excluded request-locally;
that exclusion needs both predicate authority and negative engine controls. A successful
hit condition is explicit, not an accuracy forecast. H&S support stays ESTIMATED.

**CAVEATED_ESTIMATE:** only after explicit request-local evidence and review permit
initial-strike-only calculation, with exact initial operands and clear missing-total
reason. Relevant later-state changes may explain unavailable totals; they cannot
be silently neutralized. This design does not globally authorize caveat mode for
all C mechanics. First implementation may simply refuse nonstable requests; any
initial-hit caveat admission requires separate boundary tests and UI scope tests.
A known irrelevant after-move effect can retain existing scope without a new caveat.

**REFUSED:** unknown initial damage/count/suppression authority, unsupported initial
modifier, unread HP when executable totals are required, unsupported relevant
survival/reaction, unapproved caveat, phase-ambiguous partial-move snapshot, or any
existing hard gate. Never clear Substitute, Doubles, execution, ability/item,
weather/gimmick, stale-state or runtime-authority blockers to admit a sequence.
First-strike arithmetic is not a loophole around a hard refusal. Single-hit
request-local rules depending on `fixedSingleHitMove` must be re-audited, not widened.

## Additive original-engine oracle design

Preserve `corpus.json` and every historical canonical line byte-for-byte. Introduce
a separate `repeated-strike-evidence.json` and schema/checker, using the pinned
runner backend, test-only observation patches and provenance conventions already
in `tools/hns-damage-oracle/README.md`. Production calculator is never the oracle.
No full engine run was performed for this design; these are implementation requirements.

Each scenario/run records:

- pinned source/tree, config, test-source/patch hashes, forced RNG plan and its
  consumption log; initial setup and successful-hit/crit assumptions;
- selected nominal count captured immediately after canceler, count selector kind,
  effective Skill Link/Dice verdict, initial HP and full battler/field operands;
- ordered `strikes[]`: index, remaining count before/after, target/attacker HP before,
  raw and effective ability/item IDs/effects, types/category/power, raw stats/stages,
  status and relevant volatiles (Endure, transformation, suppression, Charge, etc.),
  crit flag, random modifier, calculated damage before survival, survival-adjusted
  damage, applied target HP loss, HP after hit;
- ordered `betweenStrikeEvents[]`: handler phase, subject, ability/item activation,
  item consumed/transferred, HP/status/stage/type/form/ability deltas, effective
  state after reactions and before next strike;
- final executed-hit count, applied-loss sum, stop reason (count exhausted, target
  faint, attacker faint, sleep/freeze, failure/miss), plus separately labelled
  post-sequence events. Never infer an event solely from final HP.

Hook test runner observations at damage/HP and phase boundaries, without modifying
production battle arithmetic. Existing pre-damage callback alone is insufficient
for selected-count, post-HP and after-reaction capture. The test runner records
HP-bar loss; paired live-state observations validate applied loss and chronology.
Unobserved fields or unexpected RNG consumption fail generation, not default to zero.

Deterministic RNG strategy: use named tags for count (`RNG_HITS`, `RNG_LOADED_DICE`),
accuracy, crit, damage and secondary/contact events. Existing single-value
`WITH_RNG(RNG_DAMAGE_MODIFIER, v)` must be extended by a test-only per-tag ordered
queue or a verified runner facility that supports per-occurrence values. Prove queue
ordering, exhaustion and unexpected draws; raw `Random()%100` (Focus Band) needs
separate controlled raw RNG evidence if ever admitted. No assumed seed-only mapping.
Inspect runner support before choosing the minimal hook; this task does not claim
an existing per-occurrence queue is already implemented.

Evidence plan:

| Set | Required deterministic controls and assertions |
|---|---|
| Fixed first slice | all six metadata IDs; each hit's 16 rolls; all 256 independent noncrit pairs in representative modifier compositions, plus each admitted move's endpoint and mixed-roll checks; all-crit and mixed crit controls; type-based/per-move split; contact and noncontact |
| Variable plain | force each 2/3/4/5 count; assert weights from source, not statistical estimate; independently varied hit damage values and exact endpoints under stable proof |
| Skill Link | five with/without Dice; Gastro Acid suppression and unknown/Gas negative controls; fixed-two count stays two; separate Triple/Population accuracy controls |
| Loaded Dice | force 4 and 5; suppress via Klutz/Embargo/Magic Room; Skill Link precedence; distinct Population 4..10 controls kept engine-only |
| Early KO | HP below first-hit minimum, between rolls, exactly first-hit damage, between first and total; no hit after KO; overkill calculated versus applied loss |
| State change | engine-only Multiscale, resist berry, Sturdy option on/off, Sash, Stamina/Weak Armor, contact burn/sleep, Helmet attacker KO; complete event ordering |
| Attacker status / Marvel Scale | physical contact Double Hit + Poison Touch; physical noncontact Bonemerang + Toxic Chain; force trigger/no-trigger RNG with identical noncrit damage-roll plans and ample target HP. Observe healthy hit-one status/Defense, poison/toxic application before next hit, hit-two status and effective Defense, and damage. Choose non-rounding-degenerate stats: positive total must differ from first-hit-based multiplication. Poison Touch + Bonemerang is a no-contact negative; add a poison-ineligible target control for both abilities, with eligibility proven by pinned status predicates. Confirm no status/Defense transition and stable totals only where all other gates clear. Keep positive cases engine-only/refused until supported. |
| Negative policy | same source scenarios remain refused until transition supported; no unlock via initial stages, hard caveat, Substitute, Doubles or unknown state |

For N>2 do not store a fake sixteen-entry total roll vector or enumerate 16^N
merely for ceremony. Record actual independent RNG plans/traces and prove endpoint
composition only under the stable theorem. Branching state mechanics need their own
bounded exhaustive/transition evidence; simple low/high diagonal trials do not
prove extrema where threshold reactions make damage nonmonotonic. Future probability
convolution is outside scope. Keep historical corpus hash/bytes regression controls,
canonical serialization, reversed scenario order verification and production replay.

## Implementation order and opportunity

| Group | Requests | Battles | Lead pairs | Lead requests | Move-only ceiling | Other recorded blockers |
|---|---:|---:|---:|---:|---:|---|
| fixed | 106 | 42 | 40 | 40 | 84 | {'HNS_ABILITY_CONDITION_UNVERIFIED': 16, 'HNS_ITEM_EFFECT_NOT_MODELLED': 6} |
| variable | 286 | 110 | 122 | 126 | 252 | {'HNS_ABILITY_CONDITION_UNVERIFIED': 20, 'HNS_ABILITY_EFFECT_NOT_MODELLED': 14} |
| sliceA_plainFixed | 104 | 41 | 40 | 40 | 84 | {'HNS_ABILITY_CONDITION_UNVERIFIED': 14, 'HNS_ITEM_EFFECT_NOT_MODELLED': 6} |
| sliceB_plainVariable | 284 | 109 | 122 | 126 | 250 | {'HNS_ABILITY_CONDITION_UNVERIFIED': 20, 'HNS_ABILITY_EFFECT_NOT_MODELLED': 14} |
| scaleShot | 2 | 1 | 0 | 0 | 2 | {} |
| twineedle | 2 | 1 | 0 | 0 | 0 | {'HNS_ABILITY_CONDITION_UNVERIFIED': 2} |

1. **Slice A: six plain fixed-two EFFECT_HIT moves**, Bonemerang, Double Hit,
   Double Kick, Dual Chop, Dual Wingbeat, Twin Beam. Stable sequences in existing
   Singles contract only; exact initial-hit rolls + capped executable totals;
   independent-roll and early-KO oracle proof; affirmative reaction gates.
   Census value is enough to establish the result contract without random-count
   policy or secondary effects. Current-only-blocker upper bound 84 is not a
   prediction after stability guards.
2. **Slice B: twelve plain variable-count EFFECT_HIT moves**, excluding Scale Shot.
   Same stability contract, conditional N totals, Skill Link/Dice effective count
   authority and suppression controls. Larger opportunity, but reuse Slice A's
   proven parser/UI/survival handling rather than ship two contracts at once.
3. **Scale Shot dedicated delta**: two requests; guarded end-of-sequence stat effects
   and scope/evidence; can be small after B, never per-hit defense decrement.
4. **Twineedle dedicated delta**: two requests, both currently overlap ability
   condition blockers, zero move-only ceiling. Poison each strike and reactions;
   keep separate until exact transition/irrelevance proofs exist.
5. State-changing ordinary repeats: select narrow reactions by new census/replay
   evidence, not a generic simulator. Then separately evaluate Triple Kick/Axel,
   Population Bomb, Beat Up and Parental Bond. They are not parts of the 392.

No current census unlock is claimed. The 392 partition is 104 + 284 + 2 + 2.
The six plain fixed moves can still be refused in many contexts; run a fresh
production-boundary census after implementation to measure actual transitions and
newly displayable whole lead pairs. Do not change trainers/reference teams to
manufacture opportunity. The current remaining-family ranking separately records Triple Kick/Axel
(EFFECT_TRIPLE_KICK): 10 requests, 5 battles, 6 lead pairs, 6 lead requests,
move-only ceiling 6; Beat Up: 10 requests, 5 battles, 6 lead pairs, 6 lead
requests, ceiling 8. Population Bomb has zero move-blocked requests in this
population. Parental Bond has zero participant requests in the current population. It is
an ability-created hit, not a move-gate family; any future opportunity needs
ability-policy replay and is not credited to these 392. Source family separation is not a promise of census coverage.

## Risks, unresolved evidence, and handoff

Source semantics are sufficient to start **bounded Slice A**, not sequential
support for every C row. Open implementation obligations are the exhaustive
stable-reaction allowlist, per-occurrence runner RNG control, legacy consumer
scope migration, and real production census replay. No engine execution evidence
for repeated totals exists in this PR. Current observed stages cannot certify that
an ability already finished reacting to a future selected hit. Runtime mid-move
snapshots must not be mistaken for pre-move state.

Handoff: implement only IDs **155,458,24,530,742,814** with exact pinned EFFECT_HIT /
strikeCount=2 descriptor checks. Reuse current arithmetic for first-hit rolls;
add structured sequence parser/presentation and a source-backed stable guard;
produce executable HP-loss totals only with positive authoritative current HP,
independent-roll proof and all current gates retained. Reject relevant/unknown
between-hit changes, including Poison Touch/Toxic Chain → defender status →
Marvel Scale Defense. Add the positive/negative oracle cases above before claiming
a stable-sequence admission rule. Explicitly label hit/crit conditions.
Add additive pinned engine trace evidence, mutation/negative controls, UI scope checks and production
census replay; historical oracle bytes unchanged. Run canonical `./ci.sh all` for
that shipping change. No Twineedle, random counts, probability, Doubles/Substitute,
new profile or hardware claims. Human approval controls merge.

## Validation actually performed

Pinned local source was read at the recorded commit, including each referenced
function/script and move metadata, configuration getter and upstream test examples.
The census check (`python3 tools/hns-calc-census/check_repeated_strike_design.py
--source /path/to/pinned/checkout`) passes against starting-main generated artifacts; pinned-source
move flag cross-check and reference anchor verification pass. `git diff --check`
passes. No production code changes; **`./ci.sh all` NOT_RUN**, per this task's
explicit design-validation instruction. No original-engine regeneration, battle
tests or hardware run claimed. No prototype was necessary.
Senior review revision adds attacker-side Poison Touch/Toxic Chain status transitions, the Marvel Scale dependency and future
engine controls. These future engine cases have not been run; source checks do
not substitute for their execution evidence.

## Pinned source-reference table

All links are exact commit paths and line anchors, not moving branches. Functions
are the authority; upstream test files corroborate intent but were not run here.

| Authority | Exact pinned file / function or script |
|---|---|
| Move fields and accessors | [include/move.h:93](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/include/move.h#L93) — `u32 strikeCount:4;` |
| Count selection; Skill Link; Dice; Beat Up; Parental Bond | [src/battle_move_resolution.c:1897](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L1897) — `static void SetRandomMultiHitCounter()` |
| Counter selection order | [src/battle_move_resolution.c:1907](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L1907) — `static enum CancelerResult CancelerMultihitMoves(` |
| Attack-canceler cursor persistence | [src/battle_move_resolution.c:2038](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L2038) — `enum CancelerResult DoAttackCanceler(` |
| Repeated loop, stopping, Scale Shot | [src/battle_move_resolution.c:2821](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L2821) — `static enum MoveEndResult MoveEndMultihitMove(` |
| Ordered per-hit and after-loop handlers | [src/battle_move_resolution.c:3863](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L3863) — `static enum MoveEndResult (*const sMoveEndHandlers` |
| Per-hit target ability triggers | [src/battle_move_resolution.c:2282](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L2282) — `static enum MoveEndResult MoveEndAbilities(` |
| On-hit target items | [src/battle_move_resolution.c:2424](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L2424) — `static enum MoveEndResult MoveEndItemEffectsTarget(` |
| Target threshold items before loop | [src/battle_move_resolution.c:2807](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L2807) — `static enum MoveEndResult MoveEndHpThresholdItemsTarget(` |
| Kee/Maranga after loop | [src/battle_move_resolution.c:3274](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L3274) — `static enum MoveEndResult MoveEndKeeMarangaHpThresholdItemTarget(` |
| Life Orb/Shell Bell after loop | [src/battle_move_resolution.c:3380](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L3380) — `static enum MoveEndResult MoveEndLifeOrbShellBell(` |
| Faint processing | [src/battle_move_resolution.c:2490](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L2490) — `static enum MoveEndResult MoveEndFaintBlock(` |
| Loop cleanup does not reset battle state | [src/battle_move_resolution.c:4020](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L4020) — `void MoveValuesCleanUp(` |
| Move script HP and secondary order | [data/battle_scripts_1.s:2203](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/data/battle_scripts_1.s#L2203) — `BattleScript_EffectHit::` |
| Scale Shot completion script | [data/battle_scripts_1.s:2565](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/data/battle_scripts_1.s#L2565) — `BattleScript_ScaleShot::` |
| Focus Sash consumption | [data/battle_scripts_1.s:7212](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/data/battle_scripts_1.s#L7212) — `BattleScript_HangedOnMsg::` |
| Resist berry consumption | [data/battle_scripts_1.s:7066](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/data/battle_scripts_1.s#L7066) — `BattleScript_BerryReduceDmg::` |
| Fresh damage context | [src/battle_script_commands.c:1306](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_script_commands.c#L1306) — `static void Cmd_damagecalc(void)` |
| HP clamping/update, Disguise/Ice Face | [src/battle_script_commands.c:1757](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_script_commands.c#L1757) — `static void MoveDamageDataHpUpdate(` |
| Accuracy each-hit exceptions | [src/battle_script_commands.c:1095](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_script_commands.c#L1095) — `static bool32 ShouldSkipAccuracyCalcPastFirstHit(` |
| Additional effect eligibility and RNG | [src/battle_script_commands.c:3713](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_script_commands.c#L3713) — `static bool32 CanApplyAdditionalEffect(` |
| Resist berry phase | [src/battle_script_commands.c:1484](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_script_commands.c#L1484) — `static inline bool32 TryActivateWeaknessBerry(` |
| Per-strike effective state refresh | [src/battle_util.c:8227](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L8227) — `s32 CalculateMoveDamage(` |
| Per-strike critical | [src/battle_util.c:8124](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L8124) — `bool32 IsCriticalHit(` |
| Per-strike random damage | [src/battle_util.c:7754](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L7754) — `static inline s32 DoMoveDamageCalcVars(` |
| Survival including H&S Sturdy setting | [src/battle_util.c:8163](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L8163) — `s32 GetAdjustedDamage(` |
| Full-HP damage modifiers | [src/battle_util.c:7580](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L7580) — `static inline uq4_12_t GetDefenderAbilitiesModifier(` |
| Attacker HP modifiers | [src/battle_util.c:7002](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L7002) — `case ABILITY_DEFEATIST:` |
| Contact/status/stage/ability reactions | [src/battle_util.c:3891](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L3891) — `case ABILITYEFFECT_MOVE_END: // Think contact abilities.` |
| Disguise/Ice Face form reaction | [src/battle_util.c:4436](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L4436) — `case ABILITY_DISGUISE:` |
| Hold-effect suppression | [src/battle_util.c:5823](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L5823) — `enum HoldEffect GetBattlerHoldEffectInternal(` |
| Ability suppression | [src/battle_util.c:4996](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L4996) — `enum Ability GetBattlerAbilityInternal(` |
| Mold Breaker excludes own ability | [src/battle_util.c:4974](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L4974) — `static inline bool32 CanBreakThroughAbility(` |
| Cached move type setup | [src/battle_util.c:439](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L439) — `SetTypeBeforeUsingMove(gChosenMove, gBattlerAttacker);` |
| H&S category rules | [src/battle_util.c:9171](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L9171) — `enum DamageCategory GetBattleMoveCategory(` |
| Resist berry multiplier | [src/battle_util.c:7682](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L7682) — `static inline uq4_12_t GetDefenderItemsModifier(` |
| Escalating Triple Kick power | [src/battle_util.c:6377](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L6377) — `case EFFECT_TRIPLE_KICK:` |
| Parental Bond second-hit modifier | [src/battle_util.c:7417](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L7417) — `if (gSpecialStatuses[battlerAtk].parentalBondState != PARENTAL_BOND_2ND_HIT)` |
| On-hit item dispatcher | [src/battle_hold_effects.c:1082](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_hold_effects.c#L1082) — `case HOLD_EFFECT_ROCKY_HELMET:` |
| Rocky Helmet | [src/battle_hold_effects.c:245](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_hold_effects.c#L245) — `static enum ItemEffect TryRockyHelmet(` |
| Weakness Policy | [src/battle_hold_effects.c:264](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_hold_effects.c#L264) — `static enum ItemEffect TryWeaknessPolicy(` |
| Shell Bell and Life Orb | [src/battle_hold_effects.c:536](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_hold_effects.c#L536) — `static enum ItemEffect TryShellBell(` |
| Count config | [include/config/battle.h:9](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/include/config/battle.h#L9) — `#define B_MULTI_HIT_CHANCE` |
| Generation constant | [include/config/general.h:73](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/include/config/general.h#L73) — `#define GEN_LATEST GEN_9` |
| H&S Sturdy config | [include/config/battle.h:178](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/include/config/battle.h#L178) — `#define B_STURDY` |
| Affection disabled | [include/config/battle.h:372](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/include/config/battle.h#L372) — `#define B_AFFECTION_MECHANICS` |
| Compiled/test-only config selection | [src/generational_changes.c:37](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/generational_changes.c#L37) — `u32 GetConfigInternal(` |
| Move metadata (all inventory and distinct families) | [src/data/moves_info.h:1184](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/data/moves_info.h#L1184) — `[MOVE_TWINEEDLE] =` |
| Scale Shot metadata | [src/data/moves_info.h:18661](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/data/moves_info.h#L18661) — `[MOVE_SCALE_SHOT] =` |
| Loaded Dice item record | [src/data/items.h:14966](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/data/items.h#L14966) — `[ITEM_LOADED_DICE] =` |
| Upstream count tests (read, not run) | [test/battle/move_effect/multi_hit.c:28](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/test/battle/move_effect/multi_hit.c#L28) — `SINGLE_BATTLE_TEST("Multi hit Moves hit twice` |
| Toxic Chain priority selection | [src/battle_script_commands.c:3738](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_script_commands.c#L3738) — `static void SetToxicChainPriority(void)` |
| Attacker move-end dispatch before loop | [src/battle_move_resolution.c:2307](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_move_resolution.c#L2307) — `static enum MoveEndResult MoveEndAbilitiesAttacker(void)` |
| Attacker-side status reaction phase | [src/battle_util.c:4340](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L4340) — `case ABILITYEFFECT_MOVE_END_ATTACKER:` |
| Poison Touch predicates/RNG | [src/battle_util.c:4343](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L4343) — `case ABILITY_POISON_TOUCH:` |
| Toxic Chain status application | [src/battle_util.c:4360](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L4360) — `case ABILITY_TOXIC_CHAIN:` |
| Status application script | [data/battle_scripts_1.s:6929](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/data/battle_scripts_1.s#L6929) — `BattleScript_AbilityStatusEffect::` |
| Poison eligibility authority | [src/battle_util.c:5310](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L5310) — `bool32 CanBePoisoned(` |
| Defensive stat selection | [src/battle_util.c:7211](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L7211) — `static inline u32 CalcDefenseStat(` |
| Marvel Scale status-dependent Defense | [src/battle_util.c:7279](https://github.com/PokemonHnS-Development/pokehns-expansion/blob/1f42b74dff0e9fe942419845d040663dd829a973/src/battle_util.c#L7279) — `case ABILITY_MARVEL_SCALE:` |
