# H&S Group E disposition audit — issue #93

This is a production disposition audit, **not a completion claim for #93 or #83**. The final
zero-no-result gate remains unmet: nine pinned Doubles battles still refuse every request.
The implementation preserves that authority boundary instead of guessing partners or target counts.

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
The already compiled/read volatile has domain 0–3. `battle_util.c:6635–6636` multiplies the
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
