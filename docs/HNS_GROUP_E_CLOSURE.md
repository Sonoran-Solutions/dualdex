# H&S Group E disposition audit — issue #93

This audit records the Group E disposition work and the subsequent **authoritative
ordinary-hit Doubles subset** on PR #119. The census now has zero no-result battles;
[the Doubles source/reader audit](HNS_DOUBLES_AUTHORITY.md) documents the new authority,
remaining refusals and measurement limits. The earlier nine-battle table below is
retained as the starting-head evidence. Tracker #83 has a separate unfinished #85
logging/export work item and must not be declared complete from this census alone.

Starting main: `929f075bd25a99756ea0d2fe1158e69e8386424e` (merged PR #118; #88 closed).
Mechanics authority is unchanged:
`PokemonHnS-Development/pokehns-expansion@1f42b74dff0e9fe942419845d040663dd829a973`.

## One disposition authority

[`tools/hns-group-e/decisions.json`](../tools/hns-group-e/decisions.json) assigns every remaining
ability, hold-effect family and identity exception. Its generator reconciles the complete domains
against both generated audit inventories and produces the production `HnsGroupEData` map and
[the exhaustive identity matrix](HNS_GROUP_E_MATRIX.md). The matrix records ID, symbol, name,
family, audit blocker, tier, observed/missing state, production reason, source and tests. It also
includes the two exact Charge handoffs. The generator fails on missing or extra assignments;
`./ci.sh test` checks the artifacts and negative domain-mutation tests, and `source-check` verifies
additional pinned source assertions. Form-table references supplement the original C-file census.

The order is deliberate: existing exact arithmetic and request-local irrelevant proofs run first.
A Group E tier cannot invent a positive proof. Only `RELEVANT` with an explicit
`CAVEATED_ESTIMATE` disposition can reach the existing neutralization path; missing operands stay
`UNKNOWN` and refuse. `HARD_REFUSAL` converts even a positive relevance decision into a refusal
when ignoring it would invalidate the move or authority. The original identity, side, source and
narrower policy rationale remain in the structured decision. Battle cards name the side, mechanic
and concrete reason, including in multi-blocker lists. Caveat cards retain every ignored identity.

No Group E clearance removes a separate move, item, ability, format, field, challenge or live-state
blocker. Audit categories remain global capability categories; a caveated or contextually irrelevant
identity is not relabelled globally exact.

## Exact selected-hit additions

**Charge, including Wind Power (277) and Electromorphosis (280).** The Group B handoff's suspected
separate payload does not exist on this pinned path. `battle_util.c:4309–4317` calls
`BattleScript_WindPowerActivates`; `data/battle_scripts_1.s:4947–4952` writes
`VOLATILE_CHARGE_TIMER`; `include/constants/battle.h:217` maps that enum to `chargeTimer`.
The declaration is a 3-bit field (raw range 0–7), but the pinned scripts write only 1 or 2
and turn logic decrements it, so the production boundary accepts the semantic range 0–2.
The compiled native read width is validated independently by the layout generator.
`battle_util.c:6635–6636` multiplies the
base-power accumulator by Q4.12 `8192`, after Gems and before terrain. The bridge now forwards
the boundary-owned timer to precisely that stage. It never infers activation from the ability,
move name, current stats or history. Missing/out-of-domain observation still refuses. A defender's
reactive write happens after the incoming hit; non-Electric final types are unaffected.

Fifteen new oracle cases cover the two abilities and ordinary move Charge's shared payload,
zero/positive/maximum timers, Physical/Special Electric attacks and a non-Electric control. The
pinned runner records the actual timer at the damage boundary. Native differential tests compare
all 16 rolls; production-boundary tests verify serialization, unknown/invalid inputs and independent
Disguise refusal. No new native live operand or ABI layout was needed.

**Authoritative numeric species identity.** The baseline's three entirely refused Singles battles
(Brooke, Joshua, Ruth) use Pikachu. The generated name lookup deliberately rejects ambiguous form
names, including Pikachu and Terapagos. The boundary now builds source-data overrides using its
slot-matched live numeric species ID when the catalogue display name also matches. Manual names,
caller overrides, missing IDs, out-of-domain IDs and mismatched species cannot choose a form.
Live types and raw stats remain the damage operands. This repairs an identity-resolution gate,
not a new damage formula or reconstruction of a form transition. Tests include forged overrides
and stale/missing species IDs; existing Tera Shell full/below-full-HP behavior is preserved.

## Caveated families

The identity matrix is exhaustive; caveats remain conditional on the existing relevant branch.

- **Sturdy (new):** for an ordinary hit against observed full HP, display base damage while naming
  the ignored survival cap, including the Sturdy challenge option. Below-full HP and attacker-side
  ordinary-hit proofs clear the ability. Unknown HP, OHKO moves and other unsupported move effects
  still refuse. This follows `battle_util.c:8186`; it does not assume the option is enabled/disabled.
- **Huge Power, Pure Power, Thick Fat, Sand Force:** retain existing named modifier estimates where
  positively relevant; unknown operands and unsupported Sandstorm remain independent refusals.
- **Battle Armor / Shell Armor:** preserve the existing fixed-critical-flag conflict caveat.
- **Tera Shell:** preserve the full-HP Terapagos-Terastal distortion caveat, and existing irrelevant
  side/species/HP proofs. Observed numeric identity now resolves its ambiguous display name.
- **Unburden, Quick Feet, Sand Rush, Slush Rush, Quick Draw and turn-order items:** preserve existing
  positive Analytic-related caveats. Missing current-action order still independently refuses.
- **Focus Sash / Focus Band:** preserve named survival-cap estimates. Focus Sash's observed
  below-full-HP proof remains exact. Family-level Ring Target assignment is subordinate to its
  exact identity exception, which is unchanged.

A caveat records base damage with a specified consequence omitted; it is not a promise that the
omitted consequence is inactive, nor that another independent blocker will allow a displayed result.

## Explicit hard-refusal families

- **Suppression/replacement:** Mold Breaker, Teravolt, Turboblaze and Neutralizing Gas cannot be
  ignored when effective defender ability authority is unresolved. Shared Group C suppression and
  Group D item authority remain primary. Klutz, Unnerve, As One and Ripen retain all existing
  supported hold-effect/berry proofs. Unresolved Ability Shield interactions now refuse rather
  than neutralizing an authority-changing item.
- **Truant and execution/operand changes:** unread execution state may prevent the selected move.
  Truant's attacker path therefore changes from a caveat to an explicit refusal; defender-side
  clearance remains. Other unresolved hit-count, stat-selection, contact, priority, status or
  immunity consequences are listed individually in the matrix. Their existing narrow proofs remain.
- **Transform / Imposter:** live species, stats, types, effective ability, transformed flag and
  original transformed species are read, but these alone do not prove complete copied move/state
  settlement. Transform the move remains independently unsupported. Trace's settled effective-ID
  proof, and Receiver / Power of Alchemy Singles proofs, remain unchanged.
- **Forms:** Forecast, Multitype, RKS System, Zen Mode, Stance Change, Shields Down, Schooling,
  Battle Bond, Power Construct, Gulp Missile, Hunger Switch, Zero to Hero and Tera Shift retain
  explicit transition refusals outside existing proofs. Current species alone cannot certify a
  pending form/type/stat or move rewrite. Source form-table references are in the matrix.
- **Disguise / Ice Face:** the current hit can be intercepted and change form. A species read is
  not authority to skip the shield. Their explicit refusal is separate from ordinary form identity.
- **Protean / Libero:** retain same-type live proofs; positive rewrite still needs the unobserved
  `usedProteanLibero` pre-use authority (`battle_script_commands.c:944`).
- **Primal weather:** Primordial Sea, Desolate Land and Delta Stream retain move-cancellation /
  effectiveness refusals. A raw weather word does not implement those semantics.
- **Partner / redirection:** Friend Guard, Plus/Minus, Telepathy, Battery and Power Spot keep Singles
  proofs. Dancer, Commander, Costar, Hospitality and unresolved partner effects refuse when missing
  topology or copied-state authority matters. Lightning Rod / Storm Drain retain Group C's exact
  selected-target Singles behavior; Doubles redirection is not inferred from a two-battler request.
- **State writers / Tera:** Screen Cleaner, Curious Medicine, Toxic Debris, Embody Aspect and
  Teraform Zero retain explicit missing-settlement refusals. Mimicry's live-type proof is unchanged.
- **Held-item activation:** generated switch-in activation metadata remains authoritative. Unknown
  entry settlement, still-held Terrain Seeds and Berserk Gene cannot be cleared by stage snapshots.
  Ordinary post-hit/crit-rate effects retain their existing exact request-local proofs.
- **Mega Stones / Z Crystals:** preserve the ordinary-move plus selected **and** active `NONE`
  proof. Suppressed hold effect alone now explicitly cannot bypass that proof. Active/unknown
  gimmicks and item-dependent moves remain refused. Rusted Sword/Shield still require unresolved
  form and move-replacement settlement.
- **e-Reader Enigma Berry (581):** now explicitly `UNSUPPORTED_DAMAGE_RELEVANT`, not unclassified.
  `GetBattlerHoldEffectInternal` reads `gEnigmaBerries[battler].holdEffect` at `battle_util.c:5834`.
  The runtime effect/parameter is unread; catalogue `HOLD_EFFECT_NONE` is not its battle effect.

## Other production refusal boundaries

The matrix covers every audit remainder; it does not replace the independent `CalcLimitation`
authority. The surviving reasons fall into these groups, with the precise enum and request records
retained in the generated production census:

| Boundary | Why a trustworthy result is unavailable |
|---|---|
| Effective identity, pinned species/move, live participant, exact-ROM trust | Missing/unverified identity or data cannot be replaced with another game's defaults. |
| Runtime challenge/type/category settings, badges, base-stat equalizer, Random Moves/Types/Type Effectiveness | Missing rules or unimplemented operand-changing settings invalidate the chosen formula. |
| Unsupported move effects and item-dependent moves | The ordinary fixed-power surface does not authorize alternative power, hit-count, item or execution semantics. |
| Doubles format and target count | Missing additional participants / live spread-target count; no Singles proof can authorize Doubles. |
| Live state, HP/status, stages/raw-stat bounds, level | Missing, stale, out-of-range or unsupported operands remain hard refusals. |
| Dynamic typing, Electrify/Ion Deluge, Foresight/Miracle Eye, grounding, Roost | Effective type/immunity must be authoritative; existing exact and irrelevant proofs remain. |
| Weather, side statuses, field bits | Unsupported weather/screens or unknown bits remain refused; existing named field caveats require known relevant bits. |
| Substitute, Endure, Glaive Rush, Tar Shot, active/unknown gimmicks | Unimplemented redirection, caps, multipliers or identity/typing semantics keep their independent gates. |
| Conditional modelled abilities / suppression / immunity context / modifier order | Exact formulas still require all their source operands; a Group E label cannot supply them. |

The legacy `HNS_CHARGE_ACTIVE_NOT_MODELLED` enum remains for compatibility but is no longer emitted:
positive observed Charge now uses the exact stage; unknown Charge remains a live-state refusal.

## Reproduction and completion gate

Use the canonical commands in `HNS_CALC_CENSUS.md`, including
`DUALDEX_CENSUS_FULL=true DUALDEX_CENSUS_GENERATE=true ./ci.sh test`.
`python3 tools/hns-group-e/compare_census.py` compares the exact immutable starting commit with the
regenerated artifact, asserts unchanged request keys, and writes
[`census-comparison.json`](../tools/hns-group-e/census-comparison.json). Every remaining refused
request, with all independent causes, is enumerated in `tools/hns-calc-census/census.json.gz`;
the comparison also lists every no-result battle and ranks all surviving named causes.

The final census tables below are measured results, not altered fixtures. The nine Doubles battles
remain a concrete unmet acceptance criterion for both #93 and #83. Neither issue is ready to close.

### Measured before / after

| Measure | Starting main | Group E |
|---|---:|---:|
| Fully modelled requests | 19,336 | 19,568 |
| Caveated requests | 224 | 446 |
| Refused requests | 4,718 | 4,264 |
| Battles with no displayed result | 12 | 9 |
| All-moves-displayable lead pairs / 1,302 | 376 | 384 |
| Displayable lead requests / 8,450 | 6,724 | 6,914 |

All 651 battles and 24,278 eligible request keys are unchanged. “No displayed result” means every measured request in that battle refuses; it is distinct from the stricter all-moves-displayable lead-pair metric.

| Random Abilities measure | Starting main | Group E |
|---|---:|---:|
| Refused trials | 821,104 | 797,124 |
| Caveated trials | 16,820 | 12,640 |
| Clear trials | 2,939,736 | 2,967,896 |
| Identities with any refusal | 133 | 134 |
| Identities caveated | 4 | 4 |
| Identities clear-only | 174 | 174 |

These are sums of the exact starting-main artifact, not copied from the historical Group B prose (whose Random Abilities totals differ). Refusal-identity count rises because Truant is now correctly refused on its unread execution branch; numerical trial refusals fall overall.

### Remaining entirely refused battles

Every row is Doubles, with a missing production topology/target-count contract. Additional overlapping causes are in the machine-readable comparison.

| Trainer | Refused requests |
|---|---:|
| `TRAINER_AMY_AND_MAY_HNS` | 24 |
| `TRAINER_ANN_AND_ANNE_HNS` | 24 |
| `TRAINER_DUFF_AND_EDA_HNS` | 26 |
| `TRAINER_FINLEY_HNS` | 56 |
| `TRAINER_JO_AND_ZOE_HNS` | 28 |
| `TRAINER_LEA_AND_PIA_HNS` | 56 |
| `TRAINER_MEG_AND_PEG_HNS` | 28 |
| `TRAINER_MUALANI_HNS` | 84 |
| `TRAINER_THOM_AND_KAE_HNS` | 26 |

### Audit totals

| Inventory | Before | After |
|---|---|---|
| Abilities | 84 proven no-effect; 127 modelled conditional; 100 unsupported; 0 unclassified | 84 proven no-effect; 129 modelled conditional; 98 unsupported; 0 unclassified |
| Item identities | 585 proven no-effect; 108 modelled; 207 unsupported; 1 unclassified | 585 proven no-effect; 108 modelled; 208 unsupported; 0 unclassified |
| Hold-effect families | 32 proven no-effect; 27 modelled; 71 unsupported; 0 unclassified | unchanged |

All 98 unsupported abilities, 71 unsupported item families and 208 unsupported numeric items have explicit Group E assignments. Existing exact item identity exceptions take precedence over family assignments. No unclassified pinned identity remains.

### Validation

The pinned oracle has 1,845 scenarios (1,744 modelled, 101 engine-only): 1,843 exact 16-roll matches and the same two registered #100 divergences. All 15 newly added Charge cases match exactly. Local validation passed: `./ci.sh all`, `./ci.sh source-check`, `python3 tools/hns-damage-oracle/generate_hns_damage_oracle.py check`, `node tools/calc-bundler/test_fixed_point.js`, and `git diff --check`. All 1,830 pre-existing oracle scenario definitions and roll vectors are unchanged. The PR records exact-head Actions results.


## Doubles continuation from ce4acbe

Starting subtask head: `ce4acbe59918ee311aab472732e2771bfafa7319`.
The minimal packet and explicit indexed reader make topology, partner suppression,
Helping Hand, target count and relevant global operands real before narrowing gates.
See [the complete authority audit](HNS_DOUBLES_AUTHORITY.md) for source citations,
compiled layouts, exact arithmetic stages, fail-closed boundaries and oracle cases.
Plus/Minus are exact only in the bound ordinary Doubles branch; Friend Guard,
Battery, Power Spot and Telepathy holders may be irrelevant to their own opposing
hit. Global Group E tiers are unchanged. Costar, Hospitality, Dancer and Commander
gain no general Doubles exemption. Missing partner state never becomes a caveat.

The final census preserves all 24,278 request keys, trainer inventory and reference
teams. Its host-only neutral runtime context now explicitly supplies the new
required partner fields; these are not production defaults or trainer-derived
partner modifiers.

| Metric | ce4acbe | Doubles subset |
|---|---:|---:|
| Fully modelled | 19,568 | 19,856 |
| Caveated | 446 | 462 |
| Refused | 4,264 | 3,960 |
| Battles with no display | 9 | 0 |
| Fully displayable lead pairs | 384/1,302 | 390/1,302 |
| Displayable lead requests | 6,914/8,450 | 7,016/8,450 |
| Singles lead pairs | 384/1,284 | 384/1,284 |
| Doubles lead pairs | 0/18 | 6/18 |
| Random Abilities refused trials | 797,124 | 794,208 |
| Random Abilities caveated trials | 12,640 | 12,640 |
| Random Abilities clear trials | 2,967,896 | 2,970,812 |

Every first displayed request below is `ref-physical` Chikorita -> pinned trainer
slot 0, **Body Slam**. Finley's first result caveats Focus Sash under the existing
Group E disposition; the others are fully modelled. Before displayed requests
were zero in each row. Independent blockers remain for other requests.

| Trainer | Before | After (displayed / eligible) | First target | Remaining blockers |
|---|---:|---:|---|---|
| TRAINER_AMY_AND_MAY_HNS | 0 | 22/24 | Ledyba | move mechanics, ability condition |
| TRAINER_ANN_AND_ANNE_HNS | 0 | 20/24 | Clefairy | move mechanics |
| TRAINER_DUFF_AND_EDA_HNS | 0 | 20/26 | Onix | move mechanics |
| TRAINER_FINLEY_HNS | 0 | 50/56 | Flygon (caveated) | move/item mechanics, ability condition, random target |
| TRAINER_JO_AND_ZOE_HNS | 0 | 22/28 | Victreebel | move/item-dependent mechanics, random target |
| TRAINER_LEA_AND_PIA_HNS | 0 | 48/56 | Dragonair | move mechanics |
| TRAINER_MEG_AND_PEG_HNS | 0 | 22/28 | Ursaring | move/ability mechanics |
| TRAINER_MUALANI_HNS | 0 | 76/84 | Politoed | move/item mechanics |
| TRAINER_THOM_AND_KAE_HNS | 0 | 24/26 | Electabuzz | move mechanics |

Source-safe displayed requests meet the zero-battle target without removing
independent blockers or implementing complex moves. The remaining generic-format
and target-count enums still refuse unread/unsupported shapes, while specific
partner/target/suppression limitations explain newly distinguishable failures.
The native and production-bundle tests include topology/version/length negatives,
spread counts and liveness, screens, stack/partner arithmetic, suppression,
redirection, priority protection, Pledge and independent unsupported Surf/Facade.
A mixed Plus/Sturdy regression preserves the exact modifier alongside a caveat.

### Tracker audit

All inventory identities have explicit dispositions and no UNCLASSIFIED rows.
The new ordinary arithmetic has pinned-engine oracle coverage. Census and canonical
validation gates are recorded on PR #119. #93 is ready to close **after reviewed
merge** when those checks are green; this branch remains open for human review.

#83's Group A (#87), Group C (#89) and oracle (#90) implementations landed in merged
PRs #96, #103 and #101 respectively; their still-open issue/checklist states are
stale. #91 is closed, with follow-up ability coverage in #115/#116; #92 is closed
through merged #117; their #83 checkboxes are stale. #88 closed through #118.
#85 is genuinely unfinished: there is no debug outcome aggregation/export flow,
release-no-op test or export documentation. Its acceptance must be handled before
claiming the complete tracker is ready to close. No historical issue state or
checklist was edited to disguise that gap.

### PR #119 review correction

The ordinary-hit Doubles gate now also requires the existing agreed native
switch-in settlement proof before deriving partner state. A complete packet
with pending, unread or disagreeing phase hard-refuses as
`HNS_DOUBLES_SWITCH_IN_UNSETTLED`; partner entry writers cannot be treated as
settled merely because topology is readable. Native and production-boundary
regressions cover partner flags, replacement callback, both observations and
caller replay. The settled census baseline and original request population are
unchanged. This correction changes authority admission, not damage arithmetic.

## Slice 4 request-local supersession

[Explosion/Self-Destruct slice 4](HNS_MOVE_COVERAGE_SLICE_4.md) adds a narrow
Parental Bond false-predicate proof for generated IDs 120/153: both source moves
are parentalBondBanned, so a second hit cannot occur. The global Parental Bond
remainder disposition stays unsupported. Damp is now MODELLED_HNS_CONDITIONAL:
effective field Damp forbids these moves before self-KO, while a generated
non-Damp-ban proof preserves other moves' prior causes. Aftermath prevention
remains separate and unmodelled; this does not reopen historical closure claims.
