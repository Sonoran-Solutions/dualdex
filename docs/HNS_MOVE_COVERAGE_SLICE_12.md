# H&S move coverage slice 12: fixed two-hit repeated strikes

Refs #147. Implements the result contract merged through #146; #145 was closed after its merge. Starting main is `32c23e858cd9cf38867df668166f18d9167dc495`, with no intervening commits. Mechanics remain pinned to `1f42b74dff0e9fe942419845d040663dd829a973` (Release-v2.0.5). H&S confidence remains **ESTIMATED**. Hardware: **NOT_RUN**.

## Frozen family and arithmetic

`FIXED_TWO_HIT_PLAIN` contains exactly these six moves. Every row has EFFECT_HIT, TARGET_SELECTED, priority zero, strikeCount two, multiHit false, punching false, Sheer Force false, no additional/pre-attack effects and empty damage-relevant ability/immunity flag lists. Twin Beam alone is metronomeBanned. Full whitespace-normalized MoveInfo hashes freeze even omitted zero-valued members; metadata is generated, never hand-edited.

| ID | Move | BP | Type | Category | Accuracy | PP | Contact |
|---:|---|---:|---|---|---:|---:|---|
|155|Bonemerang|50|Ground|Physical|90|10|No|
|458|Double Hit|35|Normal|Physical|90|10|Yes|
|24|Double Kick|30|Fighting|Physical|100|30|Yes|
|530|Dual Chop|40|Dragon|Physical|90|15|Yes|
|742|Dual Wingbeat|40|Flying|Physical|90|10|Yes|
|814|Twin Beam|40|Psychic|Special|100|10|No|

`GetMoveStrikeCount` reads the fixed count; `IsMultiHitMove` distinguishes random multiHit. `CancelerMultihitMoves` takes the fixed-count branch independently of Skill Link or Loaded Dice, which only affect the random branch. Neither changes these six to five or four/five. Twineedle, random 2–5 moves, Scale Shot, Triple Kick/Axel, Population Bomb, Beat Up and Parental Bond remain excluded.

The existing `isSupportedFixedSingleHit` predicate is unchanged. A separate `isSupportedSelectedStrike` arithmetic predicate permits reuse of the existing type/category/BP/stat/ability/item/field pipeline. It is never a sequence verdict. Source powers stay per strike; Technician modifies each calculation before summing. Contact, Tough Claws, Fluffy, Long Reach and effective hold-effect suppression use the existing authorities. Normalize and category policy retain their existing ordering.

## Structured result and scope

`RepeatedStrikeResult` carries `nominalCounts=[2]`, sixteen `firstStrikeRolls`, one executable `RepeatedStrikeTotal` (nominal count, min/max HP loss, min/max executed hits), explicit assumptions and `totalUnavailableReasons`. Unsupported requests never execute and produce no speculative total. Current admitted results have no unavailable reasons.

First rolls are **calculated damage before current-HP overkill clamping**, not the whole move. Top-level min/max are executable total HP loss; legacy `damage`/`range` arrays are empty. Kotlin parsing and authorized execution require the structured scope and independently recompute its endpoints. Missing, malformed or contradictory scope fails closed. There is no KO, accuracy, crit or expected-damage probability.

For positive damage with stable operands and authoritative current target HP H:

```
minHpLoss = min(H, 2*lo)
maxHpLoss = min(H, 2*hi)
minExecutedHits = if H <= hi then 1 else 2
maxExecutedHits = if H <= lo then 1 else 2
```

Endpoints use independent rolls; the interval does not assert every interior integer is reachable. No second strike is manufactured after a target faints. A source immunity is a separate zero-execution case: `CancelerMultihitMoves` clears the counter when unaffected. Its first calculated scope and HP loss are zero and executed-hit bounds zero, rather than applying the positive-damage hit-count theorem.

HP comes from the slot-matched runtime observation, including a positive valid current/max pair, never caller overrides, percentages or computed species HP. Missing HP refuses. A caller's prefilled live-state object is overwritten by the trusted boundary. The JSON calculator validates domains and all explicit family/descriptor/live-state metadata; it rejects injected sequence results or `sequenceStable` flags.

`isCrit=false` means **all executed strikes noncritical**; true means **all executed strikes critical**. Natural mixed-critical probability is outside this product condition, although independent mixed-crit execution is observed in the evidence. The normal first accuracy path runs; ordinary continuation skips another check. The 100%-accuracy records need no RNG draw. A first miss has no successful-hit total. Results are conditional on successful connection from unchanged observed operands (including chosen move/protection), with no intervening action before the selected move.

## Affirmative stability authority

`HnsRepeatedStrikeAuthority` returns STABLE, UNSUPPORTED_TRANSITION or UNKNOWN_TRANSITION, plus a hard limitation. It requires exact Singles topology, settled observed phase, positive live participant HP, known valid statuses and persistent volatiles, no Substitute/Endure, ordinary semi-state, known effective contact and explicit ability/hold-effect allowlists. Sleep/freeze, Gastro Acid and Neutralizing Gas fail closed. Existing arithmetic, type, suppression, identity and field gates apply independently. No ignored-mechanic caveat can authorize repeated strikes. Bonemerang preserves exact Levitate immunity and the existing Iron Ball grounding arithmetic. Active Smack Down retains its old hard refusal; Gravity on a Ground move retains its old caveat and is therefore refused for repeated totals.

The generated contract freezes all shared execution/damage scripts and handlers, plus `include/move.h`, `src/battle_hold_effects.c`, `src/pokemon.c` and `src/data/pokemon/form_change_tables.h`. On-hit form changes require the table’s exact ability identity (`pokemon.c:9117–9120`); the only rows use Disguise, Gulp Missile or Ice Face, all excluded. The reviewed allowlists are conservative for both participant roles. Source dispatch for an admitted identity has no earlier reaction that writes a later damage operand or stops the repeat. Unlisted identities/effects never inherit single-hit harmlessness.

Affirmative ability identities (both roles): None (0), Insomnia (15), Wonder Guard (25), Levitate (26), Marvel Scale (63), Overgrow (65), Blaze (66), Torrent (67), Swarm (68), Heatproof (85), Adaptability (91), Skill Link (92), Normalize (96), Technician (101), Filter (111), Solid Rock (116), Sheer Force (125), Defeatist (129), Fur Coat (169), Refrigerate (174), Tough Claws (181), Pixilate (182), Aerilate (184), Water Bubble (199), Long Reach (203), Liquid Voice (204), Galvanize (206), Fluffy (218), Ice Scales (246). Effective hold effects (both roles): ABILITY_SHIELD, ASSAULT_VEST, CHOICE_BAND, CHOICE_SCARF, CHOICE_SPECS, EVIOLITE, EXPERT_BELT, LIFE_ORB, LOADED_DICE, MUSCLE_BAND, NONE, PROTECTIVE_PADS, IRON_BALL, PUNCHING_GLOVE, SHELL_BELL, TYPE_POWER, WISE_GLASSES. These lists prove sequence stability only; the existing selected-strike relevance/arithmetic gates may still refuse a particular composition.

| Source class, before continuation | Slice-12 disposition |
|---|---|
|Damage/survival adjustment: Sturdy, Focus Sash/Band, Endure|Refused; first survival adjustment need not repeat|
|Full-HP modifiers: Multiscale, Shadow Shield, Tera Shell|Refused; target HP changes|
|Target stage writes: Stamina, Weak Armor, Water Compaction and other reactive stages|Refused|
|Contact HP damage: Rough Skin, Iron Barbs, Rocky Helmet and retaliation KO|Refused; attacker HP and continuation can change|
|Contact status: Flame Body, Static, Poison Point, Effect Spore|Refused; burn changes damage, sleep/freeze can stop continuation|
|Ability replacement/swap: Mummy, Lingering Aroma, Wandering Spirit|Refused|
|Weather/terrain/Charge writes: Sand Spit, Seed Sower, Wind Power, Electromorphosis|Refused|
|Attacker status application: Poison Touch, Toxic Chain|Refused, including initially healthy Marvel Scale defenders|
|Early item effects: resist berries, Weakness Policy, Snowball, Absorb Bulb, Cell Battery, Luminous Moss, threshold berries, gems, theft/transfer|Refused|
|Protect-like effects and Beak Blast burn|Refused unless exact existing contact suppression/active Protective Pads proves them inactive|
|Dancer, Symbiosis, form-change, rage, emergency switching and other unreviewed dispatch identities|Refused by the closed identity list|
|Life Orb / Shell Bell|Multiplier/hold identity may be admitted; recoil/healing occurs after the repeated loop|
|Kee/Maranga|Observed after-loop controls, but deliberately excluded from production in this slice|
|HP-threshold attacker modifiers: Defeatist/Overgrow/Blaze/Torrent/Swarm|Only the stable subset: no admitted earlier reaction changes attacker HP; Life Orb/Shell Bell writes occur later|

Move-end order matters: protect-like effects, target abilities/forms, attacker abilities/status, early target/attacker items and HP-threshold target items occur before `MoveEndMultihitMove`. Defrost/move-block recoil, attacker item pass two, Kee/Maranga and Life Orb/Shell Bell occur afterward. Continuation decrements the counter, checks target/attacker liveness and attacker sleep/freeze, resets move-local values and re-enters the ordinary damage script. Status, stages, effective identities and global field state are retained and re-read. Observed current stages alone therefore cannot prove stability.

### Beak Blast live extension

The source audit found an additional pre-repeat writer: `MoveEndProtectLikeEffect` can burn a contact attacker using `IsBattlerUsingBeakBlast`. The user explicitly authorized adding verified live authority. The version-1 four-int JNI tail adds observed flag, chosen move and protected-method value to the existing battler observation; current HP already existed and its ABI was not expanded.

The reader binds `gChosenMoveByBattler` (EWRAM `0x020002DC`, u16 stride two) and the first seven bits of the battler's `ProtectStruct` (`0x020000B8`, stride twelve). Compiled pinned headers verify widths/domains/bit positions; The existing committed official-release map excerpt independently pins the chosen-move address; the generator requires it even without local build symbols. Pinned production symbols additionally verify the address bindings when available. Independent host fixtures verify both indexed participants, nonzero and observed-zero values, short tuples, wrong versions, unread/torn reads and out-of-domain values. Two identical reads are required. This is source/software evidence; physical hardware remains NOT_RUN.

The existing action-selection reader does not establish live action-kind/order authority; `gChosenActionByBattler` is never read by this extension. Rather than guessing it or turn order, any observed chosen Beak Blast refuses contact totals, even if an action/order predicate might ultimately make it inactive. Missing chosen/protection state and observed MOVE_NONE remain unknown: a read zero is not a committed current move. Exact Long Reach or active Protective Pads can prove contact burn inactive, but do not clear unrelated reaction identities. The new observations are slot-bound and overwritten by the boundary, never accepted as caller permission.

## Engine evidence, negative control and consumer audit

The additive `tools/hns-damage-oracle/repeated-strike-evidence.json` is generated by `repeated_strike_evidence.py` from a clean archive of the pinned commit. The prior oracle backend and its historical patch list remain unchanged. A separate TESTING-only patch observes real damage operands, calculated/survival-adjusted damage, HP application and move-end chronology. It does not modify production damage or execution. Harness-only RNG callbacks provide ordered damage/critical values per occurrence (source damage RNG value 0 is 100%, value 15 is 85%; first-strike vectors are published in ascending damage order), reject extra draws, validate bounds and check consumption against observed executed hits (unused draws on early stop are explicit).

The trace records nominal/remaining count, strike index, status/HP, effective identities, types/category/power, effective attack/defense, stages, independent RNG, applied loss, between-hit state and the observed continuation/stop predicates. Complete 256-pair grids cover contact Physical, non-contact Physical, Special and a Technician/Expert Belt rounding composition. All six have full first-strike vectors for neutral, Technician, Tough Claws, Fluffy, Long Reach+Fluffy, Skill Link, Loaded Dice, Normalize, Life Orb and all-critical conditions. Mixed-crit controls are evidence only. Early-KO grids cover below/equal/between first-hit rolls, below minimum total, between total endpoints and above maximum total.

Poison Touch + Double Hit and Toxic Chain + Bonemerang into initially healthy Marvel Scale observe status application before strike two, increased effective Defense and changed damage. Their engine-only traces remain production-refused. Controls include non-contact Poison Touch, poison-ineligible targets and forced no-trigger RNG. Other engine-only controls observe full-HP loss, berry consumption, Sturdy ON/OFF, Sash, stages, burn/sleep, retaliation and attacker KO. Life Orb, Shell Bell, Kee/Maranga and Beak suppression controls establish source timing.


Measured timing controls (calculated damage remains distinct from applied target HP loss):

| Control | Calculated strikes | Target HP loss | Executed hits |
|---|---|---:|---:|
|Poison Touch → Marvel Scale|23, 16|39|2|
|Toxic Chain → Marvel Scale|32, 22|54|2|
|Multiscale / Shadow Shield / Chilan Berry|11, 23|34|2|
|Sturdy ON / Focus Sash, target HP 20|23, 23 (first adjusted to 19)|20|2|
|Sturdy OFF, target HP 20|23|20|1|
|Stamina|23, 16|39|2|
|Weak Armor|23, 34|57|2|
|Flame Body / Beak Blast|23, 11|34|2|
|Effect Spore sleep|33|33|1|
|Retaliation KO, attacker HP 1|23|23|1|
|Life Orb|30, 30|60|2|
|Shell Bell / Kee Berry|23, 23|46|2|
|Maranga Berry|26, 26|52|2|
|Beak Blast + Long Reach / Protective Pads|23, 23|46|2|
|Swords Dance + Reflect + Technician + Life Orb|42, 42|84|2|
|First accuracy miss|none|0|0|

The first two controls re-read effective Defense **109 → 163** before strike two: blind doubling would give 46/64 instead of 39/54. Initially healthy status is not a stability proof. All five poison negative controls preserve equal effective Defense and avoid poison before hit two. Rough Skin reduces attacker HP 200→175→150; Helmet 200→167→134. Life Orb leaves attacker HP unchanged through both strikes and applies recoil afterward (200→180); Shell Bell heals afterward (100→105). Kee/Maranga keep defense operands unchanged through the loop and only then increase a stage. Production admission is independent of an engine trace passing: all reaction-changing controls stay refused.

The immutable starting-head test compiles unchanged on `32c23e8`; all six neutral requests refuse solely with HNS_MOVE_MECHANICS_NOT_MODELLED. `fixed-two-negative-control.json` records the passing canonical run and test hash.

Consumers audited with repository-wide searches: DamageCalculator parser/model; CalcAuthorizedExecution; calculator result/roll labels; BattleHnsDamagePresenter; BattleModels comparison/estimated summaries; BattleConsoleScreenView. Each repeated result renders **First strike**, **Total HP loss**, nominal two and executed-hit bounds, retaining approximate H&S confidence. Plain Compose Text also supplies the accessibility text. First-strike rolls never populate the legacy whole-move range. Census displayability evaluates the same trusted capability boundary; exports of numeric totals receive total HP loss. Tests fail closed on missing or forged sequence scope.

## Reproduction

```
python3 tools/hns-damage-oracle/repeated_strike_evidence.py generate
python3 tools/hns-damage-oracle/repeated_strike_evidence.py verify --order reversed
python3 tools/hns-damage-oracle/repeated_strike_evidence.py check
python3 tools/hns-move-mechanics/test_fixed_two_contract.py --upstream-dir ../upstream-hns/pokehns-expansion
DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh test
python3 tools/hns-calc-census/report_fixed_two_coverage.py --check
./ci.sh all
./ci.sh source-check
git diff --check
```

The historical corpus remains exactly 2,523 entries: 2,399 modelled, 124 engine-only, zero registered divergences. SHA-256 remains `9f38413dd64ce0f73162f84db1f7f3cd7d84a6bf74bb38d09fb5b336947e1580`. The non-generating trace checker rejects twelve malformed/provenance/chronology/RNG mutations; the frozen-source check rejects 105 descriptor/handler/lifecycle mutations. Generated evidence contains **5,125 original-engine cases**: 960 six-move/composition diagonal-roll cases, 1,024 four-composition independent roll pairs, eight mixed-critical controls, 3,072 early-KO cases, 32 type-based category controls and 29 timing/reaction controls. All cases pass; a fresh reversed-order build/run reproduces the artifact byte for byte. The toolchain and mGBA/historical nine-patch provenance match the unchanged historical corpus; the separate tenth observation patch has its own checked hash. The nine runner groups collectively cover every parameter; trace checks independently require exactly one final observation per case. The trusted production comparison covers 4,064 cases grouped into 74 generated requests, checked again by native QuickJS.

The next repeated-strike slice should cover plain random 2–5 EFFECT_HIT moves excluding Scale Shot, reusing the structured/stability contract and adding count alternatives, source 35/35/15/15 evidence, exact effective Skill Link and Loaded Dice/suppression authority. No such implementation is included here.


## Census measured without changing neutral assumptions

The before/after tiers remain FULLY_MODELLED **21,440 → 21,440**, CAVEATED_ESTIMATE **462 → 462**, REFUSED **2,376 → 2,376**. Fully displayable lead pairs remain **662/1,302**, displayed lead requests **7,548/8,450**, and blank battles zero. No battles gain results. There are zero newly fully modelled Slice-A requests, zero REFUSED→CAVEATED transitions and zero unexplained outside-slice changes.

All **104** family requests remain refused with changed reasons: **94 HNS_REPEATED_STRIKE_STATE_UNKNOWN**, **10 HNS_REPEATED_STRIKE_TRANSITION_NOT_MODELLED**. The 94 contact requests have no chosen-move/protection witness in the frozen census baseline; the ten non-contact requests have unlisted source reaction identities. The population, keys, teams, directions, lead definition and neutral assumptions remain unchanged at 651 battles/24,278 requests. No neutral chosen-move value was invented to increase coverage. Verified live production requests supply this observation; the production/engine test fixtures explicitly observe it.

Remaining ranking (blocked requests): repeated EFFECT_HIT **288** (down from 392 move-gated requests; the 104 fixed-two requests now have sequence blockers), semi-invulnerable 80, OHKO 70, Pursuit 68, Belch 64, Assurance 62, fixed HP damage 54, Flail/Reversal 52. The full ranking and every request-local residual reason are in `move-coverage-slice-12.json`. Ranking remains an implementation-family demand measure; it cannot waive missing sequence authority.

## Pinned source anchors

The move-end dispatch table in `src/battle_move_resolution.c:3866–3905` fixes timing. `CancelerMultihitMoves:1907–1944` separates random count overrides from the fixed count; `MoveEndProtectLikeEffect:2075–2170` and `IsBattlerUsingBeakBlast:4010` establish burn timing; target/attacker ability dispatch starts at 2282/2307; threshold target items precede `MoveEndMultihitMove:2821–2880`. The latter decrements, checks liveness/sleep/freeze, cleans local values and re-enters the ordinary script. `MoveValuesCleanUp:4020` resets local move-effect/synchronization/miss state, not persistent status/stages. `TryClearChargeVolatile:3995` is invoked from post-repeat clear-bits at 3746, so the admitted unchanged Charge operand is retained through both strikes. Attacker Glaive Rush is cleared on entry; the defender damage operand remains retained. `src/battle_util.c:7777/7932` draws a fresh damage RNG value, and 8148–8152 draw crit independently.

## Software validation

The full census generation and final `./ci.sh all` passed, including native reader/tracker suites, 7,420 QuickJS checks, the unchanged historical differential oracle, Kotlin regressions, repeated-strike freshness/rejection checks and the debug APK build. Pinned-source validation and final-head GitHub Actions results are recorded in the review PR handoff. Hardware remains NOT_RUN.

## Senior-review correction: uncommitted MOVE_NONE

Pinned `BattleTurnPassed` resets every chosen move to MOVE_NONE before entering `HandleTurnActionSelectionState` (`battle_main.c:4208,4230`). The current move is populated only after move selection returns (`battle_main.c:4680–4681`). Therefore an observed zero remains valid memory evidence but cannot establish that Beak Blast is inactive. Contact sequences require a valid nonzero chosen move and known inactive protection, otherwise STATE_UNKNOWN; chosen Beak Blast remains UNSUPPORTED_TRANSITION. Exact Long Reach or active Protective Pads independently permits an uncommitted chosen move when every other gate passes. No ABI expansion is needed. Kotlin and shipped QuickJS enforce the same rule; regression fixtures use committed Tackle (33), and zero/ordinary/Beak/suppression cases are checked explicitly. The source contract pins reset-before-selection and returned-selection commitment, with three targeted lifecycle mutations. Historical engine traces remain unchanged.
