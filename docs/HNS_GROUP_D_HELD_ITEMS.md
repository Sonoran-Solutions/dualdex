# Group D held items — issue #92

This document is the current contract for ordinary selected-hit held-item effects in Heart & Soul
2.0.5. All item IDs, hold effects, parameters, item types, species rules, suppression behavior,
pipeline stages and fixed-point operators come from the pinned
`PokemonHnS-Development/pokehns-expansion` commit
`1f42b74dff0e9fe942419845d040663dd829a973`.

The production surface is a single ordinary `EFFECT_HIT` selected hit with authoritative live
participants. The item never comes from a caller-provided display name. A consumed item is the live
numeric `ITEM_NONE`; the calculator does not infer a previous item or simulate consumption.

## One hold-effect authority

`HnsHoldEffectAuthority` keeps the raw current numeric item ID separate from the effective hold
effect consumed by the damage pipeline. It returns one of:

* `ACTIVE_EXACT`: the generated item record and the exact hold effect are known to be active;
* `SUPPRESSED_NONE`: the source resolves the hold effect to `HOLD_EFFECT_NONE`;
* `UNKNOWN`: at least one required live operand is unread or out of domain.

The resolution uses current item identity, observed `gFieldStatuses` / Magic Room, per-battler
Embargo, effective ability, Gastro Acid and the live Neutralizing Gas / Ability Shield interactions
needed to determine whether Klutz is effective. Magic Room, Embargo and unsuppressed Klutz suppress
all hold-effect consumers. A raw item can still matter to identity-sensitive gates while its hold
effect is suppressed. Kotlin policy and QuickJS receive the same resolved state, so they cannot
disagree about applying a modifier.

Species-gated branches use `Hns205SpeciesMechanics`, generated from the pinned species and evolution
tables. It supplies base-species identity and `CanEvolve`; it is not a curated species allow-list.
Eviolite uses the source-selected species: the stored transformed source species when transformed,
otherwise the current battle species. The live `transformedMonSpecies` field is observed separately
from the transformed flag.

## Modeled damage factors

All stat modifiers use the pinned stat-stage accumulator position and `uq4_12_multiply_half_down`.
Base-power modifiers use the source-ordered `CalcMoveBasePowerAfterModifiers` accumulator and
`uq4_12_multiply` (half-up); they are applied once to the integer move power with the source
half-down conversion. Final modifiers use the source-ordered `GetOtherModifiers` slots after damage
roll, STAB, effectiveness and burn, with `uq4_12_multiply_half_down`. These are Q4.12 factors, not
floating-point multipliers reconstructed from labels.

| Source stage | Current hold-effect families | Pinned predicate and factor |
|---|---|---|
| Attack stat | Choice Band, Choice Specs | ×1.5 (`6144`) to physical Attack or special Attack respectively; the source omits this while active Dynamax is selected. |
| Attack stat | Thick Club | ×2 (`8192`) to physical Attack for generated base species Cubone / Marowak (`104`, `105`). |
| Attack stat | Light Ball | ×2 (`8192`) to both Attack-stat paths for generated base species Pikachu (`25`). |
| Attack stat | Deep Sea Tooth | ×2 (`8192`) to special Attack for current Clamperl (`366`). |
| Defense stat | Assault Vest | ×1.5 (`6144`) to Sp. Def only. |
| Defense stat | Deep Sea Scale | ×2 (`8192`) to Sp. Def for current Clamperl (`366`). |
| Defense stat | Metal Powder | ×2 (`8192`) to Defense for current Ditto (`132`) only while untransformed. |
| Defense stat | Eviolite | ×1.5 (`6144`) to the selected Defense or Sp. Def when generated `CanEvolve` is true for the source-selected live/transformed species. |
| Base power | Muscle Band | Physical moves use the exact floored-percent factor (`4505` for the pinned 10% parameter). |
| Base power | Wise Glasses | Special moves use the existing pinned factor (`4505`). |
| Base power | Type Power items, Plates | Matching authoritative effective move type and generated item secondary type; `PercentToUQ4_12AddOne`, `4915` for parameter 20. |
| Base power | Gems | Matching authoritative effective move type and generated item secondary type; `PercentToUQ4_12AddOne`, `5325` for parameter 30. The selected hit is evaluated while the current Gem is still held; a prior consumption is represented by live `ITEM_NONE`. |
| Base power | Lustrous Orb / Globe, Adamant Orb / Crystal, Griseous Orb / Core | ×1.2 with source rounded-percent conversion (`4915`) on matching Water/Dragon, Steel/Dragon, or Ghost/Dragon moves and generated Palkia (`484`), Dialga (`483`), or Giratina (`487`) base species respectively. |
| Base power | Soul Dew | ×1.2 (`4915`) for current Latias (`380`) or Latios (`381`) on Psychic/Dragon moves under pinned `B_SOUL_DEW_BOOST` (`GEN_LATEST`). |
| Base power | Punching Glove | ×1.1 (`4506`) for generated `punchingMove` moves; the same active hold effect suppresses contact in the contact authority. |
| Base power | Ogerpon Masks | ×1.2 (`4915`) for generated Ogerpon base species (`1416`). |
| Final damage | Life Orb | Exact floored 1.3 factor (`5324`). |
| Final damage | Expert Belt | ×1.2 (`4915`) when the authoritative type-effectiveness result is at least 2.0. |
| Final damage | Metronome | Additive final factor `4096 + PercentToUQ4_12(param) × min(counter, 5)`; the live `metronomeItemCounter` is required and is not inferred from the move name/history. |
| Final damage | Resist berries | Matching berry type on a Normal hit or effectiveness at least 2.0; ×0.5 (`2048`), or ×0.25 (`1024`) with Ripen. A living opposing effective Unnerve or As One (Ice/Shadow Rider) blocks activation. |

All item factors compose in the exact H&S stage and source order. For example, a stat item modifies
the selected raw stat before the matching badge stage, while Life Orb is applied after STAB and
type effectiveness. The request's live `usesDefStat` result includes Wonder Room and Psyshock
selection.

## Utility Umbrella, activation state and forms

Utility Umbrella has no direct damage factor. When its hold effect is active on a battler it removes
that holder's weather effects from the source predicates used for weather damage, Solar Power and
Flower Gift, and it makes weather damage neutral against the holder. Its suppression is resolved by
the shared hold-effect authority.

Booster Energy has no second multiplier. Its request-local item rule requires the observed
Protosynthesis / Quark Drive activation flag and source-selected boosted-stat payload; those live
ability/stat operands carry the modeled modifier. A still-held Booster Energy item is not treated as
activated, and a consumed item does not reconstruct activation from historical item state.

Red Orb (`290`) and Blue Orb (`291`) are classified as the pinned Primal Orb family. They do not add
a separate modifier after form settlement: current species, ability, weather and raw stats are
already live inputs. An unsettled or unobserved form transition remains unknown. This records the
post-settlement damage contract; it does not classify the orbs as having no effect.

Mega Stones and Z Crystals remain globally damage-relevant unsupported identities. The current
request may clear their item blocker only when the move is ordinary and both selected and active
gimmicks are authoritatively `NONE`. An active or unknown gimmick, or an independently item-dependent
move, still blocks. Remaining rare-mechanic handling is tracked in [issue #93](https://github.com/Sonoran-Solutions/dualdex/issues/93).

## Move and consumption boundary

The ordinary move allow-list is generated from the pinned `EFFECT_HIT` data and is strict. This
slice does not model Pledge or OHKO formulas: move IDs `518–520` (`EFFECT_PLEDGE`) and `12`, `32`,
`90`, `329` (`EFFECT_OHKO`) remain outside the ordinary surface. A source-derived Kotlin regression
asserts that boundary. Gem/item effects cannot authorize a non-ordinary move.

For Gems and resist berries, the current item identity is the timing authority. The modeled selected
hit sees the item before its on-hit consumption. If a previous hit or event already consumed it, the
live current item is `ITEM_NONE`; there is no synthetic consumed flag or inferred prior item. Move
specific item interactions such as Acrobatics keep their own move gate.

## Coverage delta from #116

| Audit | Starting main | Current #92 | Change |
|---|---:|---:|---:|
| Item families: proven no ordinary damage effect | 32 | 32 | 0 |
| Item families: unsupported damage-relevant | 96 | 71 | −25 |
| Item families: modeled H&S-specific | 1 | 27 | +26 |
| Item families: unclassified | 1 | 0 | −1 |
| Item identities: proven no ordinary damage effect | 585 | 585 | 0 |
| Item identities: modeled equivalent | 0 | 0 | 0 |
| Item identities: modeled H&S-specific | 4 | 108 | +104 |
| Item identities: unsupported damage-relevant | 309 | 207 | −102 |
| Item identities: unclassified | 3 | 1 | −2 |

The 26 newly modeled families are Choice Band, Choice Specs, Muscle Band, Type Power, Plates, Gems,
Light Ball, Thick Club, Deep Sea Tooth, Life Orb, Expert Belt, Metronome, Soul Dew, Lustrous Orb,
Adamant Orb, Griseous Orb, Punching Glove, Ogerpon Mask, Assault Vest, Eviolite, Deep Sea Scale,
Metal Powder, Resist Berry, Utility Umbrella, Booster Energy and Primal Orb. (Type Power, Plates,
Gems and signature orbs each cover multiple pinned numeric items.) Red and Blue Orb identities were
also reclassified from unclassified to the modeled Primal Orb family. The four previously modeled
identity exceptions remain Wise Glasses, Air Balloon, Iron Ball and Ring Target.

Rusted Sword/Shield identity cases remain unsupported. The e-Reader Enigma Berry remains
unclassified. The largest remaining item blockers are Quick Claw (28 battles / 78 requests),
Leftovers (25 / 70), Sitrus Berry (21 / 54), Scope Lens (17 / 52) and Silk Scarf (12 / 30); the
census remains the generated record in [HNS_CALC_CENSUS.md](HNS_CALC_CENSUS.md). Scope and decisions
for the remaining rare mechanics continue in #93; issue #83 stays open.

## Runtime evidence

The three retained official-ROM traces and hashes are documented in
[`tools/hns-runtime-probe/evidence/group-d-items/`](../tools/hns-runtime-probe/evidence/group-d-items/)
and checked by `python3 tools/hns-runtime-probe/verify_group_d_evidence.py`. They show Embargo
clear→active→expired, repeated Metronome-item counter growth/reset, and Transform preserving the
source species while changing the live species. The traces use the official ROM SHA recorded in
their provenance and contain no ROM, save, or state bytes.

## Group E follow-through

[Group E](HNS_GROUP_E_CLOSURE.md) gives every remaining family/identity an explicit disposition.
The e-Reader Enigma Berry is now audited unsupported with an unread runtime-effect refusal, so no
pinned item remains unclassified. Mega/Z selected and active NONE proofs remain mandatory even
when the hold effect is suppressed. Unresolved Ability Shield suppression is a hard refusal;
existing exact shared hold-effect/berry proofs remain intact. Historical blocker counts above are
superseded by the regenerated production census.
