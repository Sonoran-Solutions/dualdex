package com.dualdex.calculator

import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.hasMoveByName
import com.dualdex.pokemon.hasSpeciesByName
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust

/**
 * How much confidence the application is allowed to show for one damage calculation.
 *
 * The order is the trust order: a verdict may only be lowered, never raised.
 */
enum class CalcSupport {
    /**
     * The exact running ROM is a supported build and every input of this calculation is
     * representable by the bridge, so the number may be presented as verified.
     */
    VERIFIED,

    /**
     * The calculation comes from the documented ruleset, but at least one input is not covered by
     * evidence for this exact build (or the running ROM is not exact-verified). The number must be
     * presented as an approximation, never as a verified result.
     *
     * This is the ceiling for Heart & Soul 2.0.5.
     */
    ESTIMATED,

    /**
     * No damage number may be produced. Either the build has no documented calculator capability,
     * the request names data the active build cannot resolve, or it depends on an input the games
     * cannot produce.
     */
    UNSUPPORTED
}

/**
 * Why a calculation is not fully verified.
 *
 * Every value is a documented, testable reason. [blocks] separates "no honest number exists" from
 * "a number exists, but it is not verified".
 */
enum class CalcLimitation(val blocks: Boolean) {
    /** The running ROM bytes are not the exact build this profile was verified against. */
    ROM_NOT_EXACT_VERIFIED(false),

    /**
     * This build has no verified SHA-256 in its profile, so no runtime can ever satisfy the exact
     * ROM gate and the build can never reach [CalcSupport.VERIFIED].
     */
    BUILDS_NOT_HASH_VERIFIED(false),

    /**
     * H&S 2.0.5 challenge settings (`SaveBlock3.challengeSettings`) can change damage-relevant
     * state (EV application, base-stat equalization, species types, ability and move data) and
     * could not be read from live memory, hold an invalid status, or have required fields unobserved.
     */
    CHALLENGE_SETTINGS_UNREADABLE(false),

    /** H&S 2.0.5 held items are not an authoritative table in this build. */
    HELD_ITEM_DATA_NOT_AUTHORITATIVE(false),

    /** The named ability's damage effect is not modelled for the active ruleset. */
    ABILITY_NOT_MODELLED(false),

    /** The named item's damage effect is not modelled for the active ruleset. */
    ITEM_NOT_MODELLED(false),

    /**
     * The build's damage-rule toggle for move category (`challengeSettings.optionStyle`, bound to
     * the "PHYS/SP SPLIT" row of the in-game Mode tab). Raw value 0 (`PER_MOVE_SPLIT`) selects
     * per-move category where a move's own `category` field decides physical/special; raw value 1
     * (`TYPE_BASED`) selects generation III's type-based damage category where the move's TYPE
     * decides. Unread, so the active category rule is unknown.
     */
    CATEGORY_SPLIT_TOGGLE_UNREADABLE(true),

    /**
     * The build's "ADD FAIRY TYPE" toggle. Turning it off deletes the Fairy type: species revert to
     * their pre-Fairy typings and Fairy moves are retyped. Unread, so the defender's types are
     * unknown.
     */
    FAIRY_TOGGLE_UNREADABLE(true),

    /** The build's "RANDOM TYPES" toggle rewrites species typings at random. Unread. */
    RANDOM_TYPES_UNREADABLE(true),

    /** The build's "RANDOM TYPE EFFECTIVENESS" toggle remaps the attacking type in the chart. Unread. */
    RANDOM_TYPE_EFFECTIVENESS_UNREADABLE(true),

    /**
     * The Heart & Soul 2.0.5 modern type chart (Fairy type present; Steel does not resist Ghost/Dark)
     * is not modelled by the generation III calculation pipeline (Gap C).
     */
    HNS_TYPE_CHART_NOT_MODELLED(true),

    /**
     * An authoritative live effective ability could not be read from live memory (unobserved,
     * party slot mismatch, bench Pokemon, faint window, or out-of-domain ID), or an H&S manual
     * participant has an unspecified ability.
     */
    HNS_EFFECTIVE_ABILITY_UNREADABLE(true),

    /**
     * The Heart & Soul 2.0.5 ability's damage effect is not modelled by the calculator pipeline.
     */
    HNS_ABILITY_EFFECT_NOT_MODELLED(true),

    /**
     * An authoritative live current held item could not be read from live memory (unobserved,
     * party-slot mismatch, faint window, doubles ambiguity, or out-of-domain ID), so the current
     * battle item is unknown. Distinct from an observed `ITEM_NONE`, which means explicitly no item
     * (issue #9, Gap C3).
     */
    HNS_EFFECTIVE_ITEM_UNREADABLE(true),

    /**
     * The Heart & Soul 2.0.5 held item is identity-known but its damage effect is not modelled by
     * the calculator pipeline (issue #9, Gap C3).
     */
    HNS_ITEM_EFFECT_NOT_MODELLED(true),

    /**
     * A supplied item identity could not be tied to the exact H&S 2.0.5 item catalogue, so no
     * capability can be established for it (issue #9, Gap C3).
     */
    HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE(true),

    /**
     * The selected H&S 2.0.5 move's damage semantics read held-item state (attacker item
     * identity, defender item presence, or item absence) and that interaction is not
     * explicitly reproduced by this calculator (issue #9, Gap C3).
     *
     * Static item capability is not context-free: an item whose own hold effect never
     * touches the ordinary damage path can still determine Fling's base power, double
     * Acrobatics in the absence of an item, or add Knock Off's x1.5. The authorized
     * request strips supported items before the engine, which destroys exactly that
     * input, so the move must be refused before it ever reaches the engine rather than
     * allowed to no-op.
     */
    HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED(true),

    /**
     * The Heart & Soul 2.0.5 held-item system is not modelled by the calculator pipeline (Gap C3).
     */
    @Deprecated("Superseded by HNS_EFFECTIVE_ITEM_UNREADABLE, HNS_ITEM_EFFECT_NOT_MODELLED, and HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE")
    HNS_HELD_ITEM_SYSTEM_NOT_MODELLED(true),

    /**
     * The Heart & Soul 2.0.5 ability system is not modelled by the calculator pipeline (Gap C2).
     */
    @Deprecated("Superseded by HNS_EFFECTIVE_ABILITY_UNREADABLE and HNS_ABILITY_EFFECT_NOT_MODELLED")
    HNS_ABILITY_SYSTEM_NOT_MODELLED(true),

    /**
     * The request contains a type that cannot be represented in the type chart.
     */
    UNREPRESENTABLE_TYPE_NOT_MODELLED(true),

    /**
     * The Heart & Soul 2.0.5 "RANDOM TYPES" challenge is active in live memory, which is not modelled
     * by the calculator.
     */
    RANDOM_TYPES_ACTIVE_NOT_MODELLED(true),

    /**
     * The Heart & Soul 2.0.5 "RANDOM TYPE EFFECTIVENESS" challenge is active in live memory, which is
     * not modelled by the calculator.
     */
    RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED(true),

    /**
     * The build's generation III badge boost (a flat x1.1 damage modifier for the player's side)
     * is active and has no equivalent in the request shape. This remains the next production
     * blocker now that the blanket held-item blocker has been replaced by conditional item
     * capability (issue #9, Gap C3 -> Gap C4).
     *
     * C4a audited the exact insertion point (post-stat-stage, composed in UQ4.12 and applied once
     * to the stat) and deliberately leaves this blocker in place: the authoritative badge flag
     * state lives in save memory behind `FlagGet` and is not read by DualDex, so a caller cannot
     * supply proven runtime badge state and the calculator must not fabricate it. See
     * docs/HNS_2_0_5_CALCULATOR_CAPABILITY.md §8.1.
     */
    BADGE_BOOST_NOT_MODELLED(true),

    /**
     * The H&S `tx_Challenges_BaseStatEqualizer` challenge replaces every non-HP base stat with a
     * fixed value (100 / 255 / 500) when it is active
     * (`GetBaseStatEqualizerValue`, `[src/challenge_menu.c:2359]`,
     * `[src/pokemon.c:3750]`). DualDex passes the pinned species' ordinary base stats, so an
     * active equalizer silently changes every damage-relevant stat. Blocked fail-closed (issue #9,
     * Gap C4a).
     */
    HNS_BASE_STAT_EQUALIZER_NOT_MODELLED(true),

    /**
     * The H&S `tx_Random_Moves` challenge rerolls a Pokemon's learned moves at acquisition
     * (`RandomizerFeatureEnabled(RANDOMIZE_LEARNSET)`, `[src/pokemon.c:3954]`). The calculator
     * never consults the observed party moveset, so when the challenge is active the selected move
     * cannot be proven to be the authoritative current learned move. Blocked fail-closed
     * (issue #9, Gap C4a).
     */
    HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED(true),

    /**
     * The selected H&S move's effect is not the ordinary `EFFECT_HIT` damage path, or its effect
     * could not be resolved from the pinned source, so the calculator cannot prove that the
     * generation III pipeline reproduces its damage semantics (issue #9, Gap C4a).
     *
     * This is the move-mechanics counterpart of the item-interaction gate: the bridge forwards
     * only `power` / `type` / `category`, which describes an ordinary fixed-base-power attack and
     * nothing else. Moves that read state, scale with HP/friendship/weight/speed, hit multiple
     * times, deal fixed damage, or otherwise alter the base power must fail here rather than let
     * the engine compute a confident but wrong number.
     */
    HNS_MOVE_MECHANICS_NOT_MODELLED(true),

    /**
     * The request would exercise a damage modifier whose H&S placement/rounding differs from the
     * ADV pipeline (issue #9, Gap C4a).
     *
     * The C4a arithmetic audit proved the bare base formula matches
     * (`power * Atk * (2L/5+2) / Def / 50 + 2`), but H&S applies the random roll *before* STAB,
     * type effectiveness, burn and screens, and composes each modifier in UQ4.12 half-down,
     * whereas `@smogon/calc` 0.11.0 applies burn/screens/weather before adding +2 and applies
     * STAB/type before the roll. Source goldens show the two produce different integer ranges for
     * STAB, super-effective and burned cases, so a request that exercises any of those modifiers
     * cannot be published from this host.
     */
    HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED(true),

    /**
     * An active H&S battle's mutable damage operands are not authoritatively observed, so the
     * static species/move request is not the live truth (issue #9, Gap C4a R1).
     *
     * H&S rewrites damage operands during battle that the request shape does not represent:
     * `SET_BATTLER_TYPE` changes the current effective types (Soak), Power Trick swaps the raw
     * `gBattleMons` battle stat words with unchanged stat stages and unchanged effect ID, and
     * `SetTypeBeforeUsingMove` can force the current move's type to Electric (Ion Deluge /
     * Electrify) without changing its static effect ID. A request that exercises one of these
     * classes without authoritative live observation cannot be shown to reproduce the running
     * calculation, so it fails closed here. This gate is deliberately coarse: Gap C4b must
     * consume effective battler types, battle stat words, the dynamic move type, and transient
     * damage state before any of these classes may clear (§10.5).
     */
    HNS_LIVE_BATTLE_STATE_NOT_MODELLED(true),

    /**
     * An H&S Doubles request cannot establish the runtime target count
     * (`GetMoveTargetCount(ctx)`) that decides the Gen-III spread reduction.
     *
     * H&S halves a spread move only when the count of currently present targets is exactly 2, so
     * Rock Slide against a single remaining foe in a Doubles battle must NOT be halved. The static
     * request carries no target-presence state and no runtime reader supplies the count yet
     * (Gap C4b), so a Doubles request fails closed here rather than inferring the modifier from
     * `field.gameType` plus the move's static target class. This is deliberately coarse: the whole
     * Doubles format stays blocked until the count is represented.
     */
    HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED(true),

    /**
     * An active H&S battle's live format (`gBattlersCount`) was not authoritatively established
     * as the Singles topology the production subset models, or the request's caller/UI-supplied
     * `field.gameType` contradicts the observed topology.
     *
     * H&S selects different arithmetic by battle format: `GetScreensModifier` composes Reflect /
     * Light Screen with `UQ_4_12(0.667)` in a Doubles battle and `UQ_4_12(0.5)` in Singles, the
     * spread reduction depends on the observed target count, and the partner-dependent branches
     * (`GetDefenderPartnerAbilitiesModifier`, Helping Hand) are Doubles-only. The request format
     * label is caller/UI-owned, so a genuine Doubles battle could otherwise be computed with the
     * Singles arithmetic. The native observation already carries the real topology; the boundary
     * now binds the agreed `gBattlersCount` from both battle-level observations and this gate
     * refuses the entire live calculation when that observed format is not the Singles `2` the
     * subset models. An unread word, a player/enemy disagreement, an observed `4`, or a request
     * label that contradicts the observed topology all fail closed.
     *
     * This is deliberately not [HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED]: the entire live
     * calculation is under an unmodelled format, not just a spread move. The C4e production
     * subset models Singles only; all live Doubles requests (whether observed 4 or mislabelled,
     * and regardless of target-count resolution) fail closed with this limitation because
     * production Doubles is not implemented and carries unobserved live operands (e.g. Helping Hand).
     */
    HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED(true),

    /**
     * A vanilla Generation III **Doubles** request carries an active Reflect or Light Screen, whose
     * cartridge arithmetic this pipeline does not reproduce.
     *
     * The pinned engines apply the Doubles screen inside `CalculateBaseDamage` with a deliberately
     * *integer* operation order —
     * `if ((gBattleTypeFlags & BATTLE_TYPE_DOUBLE) && CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2)
     *      damage = 2 * (damage / 3);` (identical text in `pret/pokefirered src/pokemon.c:2547`
     * and `pret/pokeemerald src/pokemon.c:3270`) — so the division by 3
     * happens on the pre-roll value and is floored *before* the multiply. The shipped
     * `@smogon/calc` 0.11.0 ADV pipeline instead applies `floor(damage * 2 / 3)` to the rolled
     * value, which differs by one on fourteen of the sixteen rolls (for the Strength fixture:
     * the cartridge yields 35-42, the pipeline yields 36-43).
     *
     * The branch is also conditional on live state the request shape cannot express: the cartridge
     * takes `2 * (damage / 3)` only while **both** defending battlers are present, and falls back to
     * `damage / 2` otherwise. There is no target-presence operand, so a request cannot select the
     * correct branch even in principle.
     *
     * Because `VANILLA_GEN3` advertises `CalcSupport.VERIFIED`, leaving this shape computable would
     * let the application publish a number that differs from the cartridge under a Verified label.
     * The gate therefore refuses the request instead of presenting a confident wrong vector. It is
     * scoped to Doubles plus an active screen: Singles screens remain on the verified surface, the
     * vanilla engine keeps its documented pipeline order, and the Doubles **spread** reduction is
     * refused separately by [VANILLA_DOUBLES_SPREAD_NOT_MODELLED].
     */
    VANILLA_DOUBLES_SCREEN_NOT_MODELLED(true),

    /**
     * A vanilla Generation III **Doubles** request uses a move the shipped pipeline reduces as a
     * spread move, and the request carries no authoritative target count.
     *
     * The pinned engines apply the reduction inside `CalculateBaseDamage` only while both opposing
     * battlers are actually present —
     * `if ((gBattleTypeFlags & BATTLE_TYPE_DOUBLE) && gBattleMoves[move].target == MOVE_TARGET_BOTH
     *      && CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2) damage /= 2;`
     * (`pret/pokefirered src/pokemon.c:2553` physical, `:2604` special; identical at
     * `pret/pokeemerald src/pokemon.c:3276` and `:3327`) — so Rock Slide against a single remaining
     * foe is NOT halved. The shipped `@smogon/calc` 0.11.0 ADV pipeline reduces whenever
     * `field.gameType` says Doubles, and the static request has no target-presence operand, so a
     * lone opponent would be reduced where the cartridge does not. That is the same epistemic hole
     * as [VANILLA_DOUBLES_SCREEN_NOT_MODELLED], reached through the spread branch.
     *
     * [Gen3DoublesSpreadMoves] identifies the moves the shipped pipeline actually reduces (derived
     * from the pinned decompilation and checked against the shipped bundle by the host suite). H&S
     * Doubles already fails closed for its own reasons
     * ([HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED]); this limitation is the vanilla half.
     */
    VANILLA_DOUBLES_SPREAD_NOT_MODELLED(true),

    /**
     * The move's effective type was authoritatively observed to be rewritten to Electric by an
     * active dynamic-type mechanism (`gFieldStatuses & STATUS_FIELD_ION_DELUGE` on a Normal move,
     * or the attacker's `volatiles.electrified` on any move).
     *
     * C4e observes the field word and the attacker volatile, so the *neutral* case is now
     * authoritative. The *active* Electric retype is deliberately not published: the ordinary
     * subset's arithmetic/type evidence does not cover the forced Electric typing, so the request
     * fails closed rather than compute it with the static type (issue #9, Gap C4e).
     */
    HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED(true),

    /**
     * The defender's `volatiles.glaiveRush` was authoritatively observed true. `GetGlaiveRushModifier`
     * doubles the damage of any incoming move, and that x2 is not part of the ordinary-subset
     * arithmetic, so the request fails closed (issue #9, Gap C4e).
     */
    HNS_GLAIVE_RUSH_ACTIVE_NOT_MODELLED(true),

    /**
     * The battle-global `gFieldStatuses` word was authoritatively observed to carry a bit outside
     * the explicitly supported Ion Deluge mask. Every other field status alters ordinary damage or
     * the defensive stat for the supported subset: Wonder Room swaps Defense / Sp.Def inside
     * `CalcDefenseStat`, the four terrains apply a x1.3 / x0.5 type modifier, Mud/Water Sport
     * reduce their type, Gravity changes Ground immunity / groundedness, and Trick/Magic Room /
     * Fairy Lock gate abilities and items. None of those is modelled here, so the request fails
     * closed rather than silently computing without the modifier (issue #9, Gap C4e correction).
     */
    HNS_FIELD_STATUS_NOT_MODELLED(true),

    /**
     * The attacker's `volatiles.chargeTimer` was authoritatively observed non-zero and the
     * effective move type is Electric. `src/battle_util.c` doubles an Electric move while the
     * timer is positive, and that x2 is not part of the ordinary-subset arithmetic, so the
     * request fails closed (issue #9, Gap C4e correction).
     */
    HNS_CHARGE_ACTIVE_NOT_MODELLED(true),

    /**
     * The defender's `volatiles.tarShot` was authoritatively observed true and the effective move
     * type is Fire. `src/battle_util.c` doubles a Fire move against a tar-shotted defender, and
     * that x2 is not part of the ordinary-subset arithmetic, so the request fails closed
     * (issue #9, Gap C4e correction).
     */
    HNS_TAR_SHOT_ACTIVE_NOT_MODELLED(true),

    /**
     * The persistent `volatiles.foresight` was authoritatively observed true on a participant. The
     * pinned `MulByTypeEffectiveness` bypasses a Ghost immunity for Normal/Fighting moves while it
     * is set, which the static type chart cannot express, so the request fails closed rather than
     * compute the static immunity (review round 4).
     */
    HNS_FORESIGHT_ACTIVE_NOT_MODELLED(true),

    /**
     * The persistent `volatiles.miracleEye` was authoritatively observed true on a participant. The
     * pinned `MulByTypeEffectiveness` bypasses a Dark immunity for Psychic moves while it is set,
     * which the static type chart cannot express, so the request fails closed (review round 4).
     */
    HNS_MIRACLE_EYE_ACTIVE_NOT_MODELLED(true),

    /**
     * One of the persistent grounding volatiles (`root` / `smackDown` ground the holder;
     * `telekinesis` / `magnetRise` unground it) was authoritatively observed true on a participant.
     * The pinned `IsBattlerGrounded` reads them, so a Ground-type immunity can depend on live
     * volatile state the static chart does not carry; the request fails closed (review round 4).
     */
    HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED(true),

    /**
     * The persistent `volatiles.roostActive` was authoritatively observed true on a participant.
     * The pinned `GetBattlerTypes` removes that battler's Flying type for the turn, so the raw
     * `gBattleMons[].types` bytes are no longer the engine's effective types and the static-type
     * comparison cannot prove the request neutral. The request fails closed (review round 4).
     */
    HNS_ROOST_ACTIVE_NOT_MODELLED(true),

    /**
     * The persistent `volatiles.gastroAcid` was authoritatively observed true, so the pinned
     * `GetBattlerAbility()` returns `ABILITY_NONE` and the engine's effective ability is not the
     * raw `gBattleMons[].ability` identity (for example a suppressed Overgrow pinch boost). The
     * request fails closed rather than classify the suppressed identity (review round 4).
     */
    HNS_ABILITY_SUPPRESSED_NOT_MODELLED(true),

    /**
     * The persistent `volatiles.substitute` was authoritatively observed true on a participant.
     * The pinned `GetAdjustedDamage` redirects the computed damage away from the battler, an
     * outcome this ordinary-damage calculation does not model, so the request fails closed
     * (review round 4).
     */
    HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED(true),

    /**
     * The persistent `volatiles.endured` was authoritatively observed true on a participant. The
     * pinned `GetAdjustedDamage` caps incoming damage at HP-1, an outcome this ordinary-damage
     * calculation does not model, so the request fails closed (review round 4).
     */
    HNS_ENDURED_ACTIVE_NOT_MODELLED(true),

    /**
     * An active H&S battle's gimmick state (`gBattleStruct->gimmick.activeGimmick`) could not be
     * read, so Tera/Dynamax/Z/Mega could be silently active and change STAB, stats or type
     * semantics. Unreadable fails closed (issue #9, Gap C4e).
     */
    HNS_GIMMICK_STATE_UNREADABLE(true),

    /**
     * A live gimmick (Tera/Dynamax/Z/Mega/Ultra Burst) was authoritatively observed active for a
     * participant. The ordinary-subset arithmetic does not model any gimmick's damage effect, so
     * the request fails closed (issue #9, Gap C4e).
     */
    HNS_GIMMICK_ACTIVE_NOT_MODELLED(true),

    /**
     * The attacker's authoritative live `status1` is non-zero (a status condition is active) or
     * could not be read. The ordinary subset models only a neutral status from live state, so a
     * live status fails closed rather than letting a stale party snapshot decide (issue #9, Gap C4e).
     */
    HNS_LIVE_STATUS_NOT_MODELLED(true),

    /**
     * A conditionally-supported pinch ability (`Overgrow`/`Blaze`/`Torrent`/`Swarm`) applies to the
     * selected move's type, but the authoritative live HP/max HP needed to decide its 1/3-HP
     * condition was not observed. The condition cannot be assumed inactive, so the request fails
     * closed (issue #9, Gap C4e).
     */
    HNS_ABILITY_CONDITION_UNVERIFIED(true),

    /**
     * An active H&S battle's battle-global weather word (`gBattleWeather`) was not authoritatively
     * read. The ordinary arithmetic applies Rain/Sun modifiers, so an unread word cannot be
     * assumed clear: a live Rain or Sun battle would otherwise compute as neutral (Gap C4e
     * correction).
     */
    HNS_LIVE_WEATHER_UNKNOWN(true),

    /**
     * The battle-global weather word was observed, but it carries a condition the ordinary
     * arithmetic does not model (Sandstorm, Hail, Snow, Fog, Strong Winds). The request fails
     * closed instead of silently computing it as clear (Gap C4e correction).
     */
    HNS_LIVE_WEATHER_NOT_MODELLED(true),

    /**
     * An active H&S battle's defender-side status word (`gSideStatuses[side]`) was not
     * authoritatively read, so Reflect / Light Screen could be active and halve the incoming move.
     * An unread word cannot be assumed screenless (Gap C4e correction).
     */
    HNS_LIVE_SCREENS_UNKNOWN(true),

    /**
     * The defender-side status word was observed, but it carries a bit the ordinary arithmetic
     * does not model (Aurora Veil or any other side status). The request fails closed rather than
     * silently computing it without that modifier (Gap C4e correction).
     */
    HNS_LIVE_SIDE_STATUS_NOT_MODELLED(true),

    /**
     * The build scales type-boost held items to a later-generation percentage than the generation
     * III pipeline applies.
     */
    ITEM_BOOST_PERCENTAGE_DIFFERS(false),

    /** The request asks for a different generation than the resolved ruleset uses. */
    MECHANICS_GENERATION_MISMATCH(false),

    /** The species is not present in the pinned data for this build. */
    SPECIES_NOT_IN_PINNED_DATA(true),

    /** The move is not present in the pinned data for this build. */
    MOVE_NOT_IN_PINNED_DATA(true),

    /** Supplied stat boosts are outside the range the games can produce. */
    BOOSTS_OUT_OF_RANGE(true),

    /** Supplied IVs/EVs are outside the range the active build can produce. */
    STAT_VALUES_OUT_OF_RANGE(true),

    /**
     * Supplied level is outside the range the games can produce. The engine accepts any level and
     * returns a normal-looking number for it.
     */
    LEVEL_OUT_OF_RANGE(true),

    /**
     * The status string is not one the engine models. It stores an unrecognised status verbatim and
     * then treats the Pokemon as simply "has a status", which turns on the Guts and Marvel Scale
     * modifiers while skipping the burn halving.
     */
    STATUS_NOT_MODELLED(true),

    /**
     * A field condition was supplied that the generation III pipeline does not model.
     *
     * This is a blocking refusal rather than a downgrade because the engine is worst-behaved here:
     * it compares weather names exactly and silently ignores anything it does not recognise (so
     * "Snow" would compute as *no weather*), and it ignores `terrain` entirely.
     */
    FIELD_CONDITION_NOT_MODELLED(true),

    /**
     * A participant whose values came from a live memory read is missing at least one
     * damage-relevant field, and the engine cannot distinguish that gap from a neutral value.
     *
     * This is the reason an exact-verified ROM alone must not produce a verified result: a burned
     * attacker whose status the reader did not carry would otherwise be calculated as unburned and
     * labelled verified. Blocking, because there is no honest number for a participant whose state
     * is incomplete - an omitted ability is not the species' default, and an omitted stat stage is
     * not stage zero.
     */
    LIVE_PARTICIPANT_STATE_UNKNOWN(true),

    /**
     * A participant came from a live memory read whose values are, alone, not enough to authorize
     * the calculation. Distinct from [LIVE_PARTICIPANT_STATE_UNKNOWN] and from
     * [ROM_NOT_EXACT_VERIFIED]: this says the inputs were read but are not verified, not that a
     * field is missing and not that the ROM is unrecognised.
     */
    LIVE_INPUTS_NOT_VERIFIED(true);

    /** True when no damage number may be produced at all from this request. */
    val blocksCalculation: Boolean get() = blocks
}

/** The exact battle ruleset family a calculation is being produced for. */
enum class CalcRuleset {
    /** Exact vanilla FireRed/Emerald: the Gen III engine and Gen III data. */
    VANILLA_GEN3,

    /** Pokemon Heart & Soul 2.0.5 at commit 1f42b74dff0e9fe942419845d040663dd829a973. */
    HNS_2_0_5
}

/**
 * One row of the calculator capability matrix.
 *
 * [mechanicsGeneration] and [contentSource] are deliberately separate fields because they answer
 * different questions:
 *  - [mechanicsGeneration] is the `@smogon/calc` generation whose damage pipeline the build's
 *    engine is *individually demonstrated* to share constants with. It is the number sent as `gen`.
 *  - [contentSource] names where this build's species/move/item records come from. It is a pinned
 *    data-pack id, never a bundled dex, because the bridge selects content by name and must be able
 *    to prove a name belongs to this build.
 *
 * Two claims must NOT be read into these fields, because neither is true today:
 *
 *  1. **Authoritative data consumption is not mechanics equivalence.** The bridge forwards
 *     authoritative base stats, types, power, and category overrides from the pinned pack,
 *     which `@smogon/calc` consumes (Gap B closed). However, the engine still executes Gen 3
 *     pipeline arithmetic.
 *  2. **[mechanicsGeneration] is not an equivalence verdict.** For H&S 2.0.5 only the critical-hit
 *     multiplier, the two-target reduction and Thick Fat's placement are demonstrated to match. The
 *     generation III chart does **not** match the hack's (it makes Steel resist Ghost and Dark and
 *     has no Fairy), and abilities, items, the category rule, terrain and badge boost all differ.
 *
 * H&S 2.0.5 is nevertheless the case where the two fields diverge, which is why they are separate:
 * its content is the hack's own pinned pack (1427 species, 934 moves) while its damage arithmetic
 * shares individual generation III constants. What the pipeline cannot express is the hack's
 * *player-configurable rules*, which is why that row can never be verified. See
 * docs/HNS_2_0_5_CALCULATOR_CAPABILITY.md §§3-4.
 */
data class CalcCapability(
    val ruleset: CalcRuleset,
    val mechanicsGeneration: Int,
    /** Pinned data-pack id whose records describe this build's content. */
    val contentSource: String,
    /** Highest confidence this ruleset may ever reach. */
    val ceiling: CalcSupport,
    /** Limitations that hold for every request against this ruleset. */
    val alwaysLimitations: List<CalcLimitation>,
    /** Human-readable build identity used in labels and diagnostics. */
    val label: String
)

/**
 * The production decision for one damage-calculation request, together with the request the
 * application is allowed to send.
 *
 * The Calc screen presents exactly this value and never decides support on its own, so the screen
 * and the engine gate cannot drift apart.
 */
data class CalcCapabilityVerdict(
    val support: CalcSupport,
    val capability: CalcCapability,
    val limitations: List<CalcLimitation>,
    val request: DamageCalculationRequest?,
    /** Why the build itself is unsupported; empty for a recorded capability row. */
    val unsupportedReason: String = "",
    /** Request-local decisions for globally unsupported or unresolved H&S abilities. */
    val hnsAbilityDecisions: List<HnsAbilityRequestDecision> = emptyList(),
    /**
     * Request-local decisions for globally unsupported or unresolved H&S held items. A decision
     * that is not PROVEN_IRRELEVANT is the structured cause of an
     * [CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED] limitation.
     */
    val hnsItemDecisions: List<HnsItemRequestDecision> = emptyList()
) {
    /** True only when the result may be shown as verified. */
    val isVerified: Boolean get() = support == CalcSupport.VERIFIED

    /** True when a number may be shown at all, carrying an explicit estimate label. */
    val isCalculable: Boolean get() = support != CalcSupport.UNSUPPORTED && request != null

    val ruleset: CalcRuleset get() = capability.ruleset

    /** Short user-facing label for a result produced from this verdict. */
    val supportLabel: String
        get() = when (support) {
            CalcSupport.VERIFIED -> "Verified"
            CalcSupport.ESTIMATED -> "Approximate"
            CalcSupport.UNSUPPORTED -> "Unsupported"
        }

    /**
     * User-facing explanation. Never empty for anything except a fully verified vanilla
     * calculation, so an unverified result cannot be presented without its reason.
     */
    val supportDetail: String
        get() {
            // Several stages add reasons (the capability row, request validation, the production
            // preparation path and the live-read refusal), so the same reason can legitimately be
            // reached twice. De-duplicating here keeps a headline from repeating itself without
            // making every caller remember to.
            val distinct = limitations.distinct()
            return when {
                distinct.isNotEmpty() -> distinct.joinToString("; ") { describe(it) }
                support == CalcSupport.UNSUPPORTED -> unsupportedReason
                else -> ""
            }
        }

    companion object {
        fun describe(limitation: CalcLimitation): String = when (limitation) {
            CalcLimitation.ROM_NOT_EXACT_VERIFIED ->
                "the running ROM is not the exact verified build"
            CalcLimitation.BUILDS_NOT_HASH_VERIFIED ->
                "this build has no verified ROM hash, so a result cannot be confirmed against your ROM"
            CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE ->
                "H&S challenge settings could not be read from live memory or are invalid"
            CalcLimitation.HELD_ITEM_DATA_NOT_AUTHORITATIVE ->
                "H&S 2.0.5 held items are not an authoritative table"
            CalcLimitation.ABILITY_NOT_MODELLED ->
                "the ability's damage effect is not modelled for this build"
            CalcLimitation.ITEM_NOT_MODELLED ->
                "the held item's damage effect is not modelled for this build"
            CalcLimitation.MECHANICS_GENERATION_MISMATCH ->
                "the request asked for a different generation than this build uses"
            CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE ->
                "this build can switch between per-move and per-type damage categories at any time and the setting is not read"
            CalcLimitation.FAIRY_TOGGLE_UNREADABLE ->
                "this build can remove the Fairy type at any time and the setting is not read"
            CalcLimitation.RANDOM_TYPES_UNREADABLE ->
                "this build can randomize species types and the setting is not read"
            CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE ->
                "this build can randomize type effectiveness and the setting is not read"
            CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED ->
                "the H&S modern type chart (Fairy type and neutral Steel vs Ghost/Dark) is not modelled by the generation III calculation pipeline"
            CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE ->
                "an authoritative live effective ability could not be read or ability is unspecified"
            CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED ->
                "the Heart & Soul 2.0.5 ability's damage effect is not modelled by the calculator"
            CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE ->
                "an authoritative live current held item could not be read"
            CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED ->
                "the Heart & Soul 2.0.5 held item's damage effect is not modelled by the calculator"
            CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE ->
                "the item could not be tied to the exact Heart & Soul 2.0.5 item catalogue"
            CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED ->
                "the selected move's damage depends on held-item state, which is not modelled for this build"
            CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED ->
                "the Heart & Soul 2.0.5 held-item system is not modelled by the calculator (Gap C3)"
            CalcLimitation.HNS_ABILITY_SYSTEM_NOT_MODELLED ->
                "the Heart & Soul 2.0.5 ability system is not modelled by the calculator (Gap C2)"
            CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED ->
                "the request contains an unrepresentable type not present in the type chart"
            CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED ->
                "this battle has the Random Types challenge active, which is not modelled"
            CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED ->
                "this battle has the Random Type Effectiveness challenge active, which is not modelled"
            CalcLimitation.BADGE_BOOST_NOT_MODELLED ->
                "the generation III badge boost is not part of the calculation"
            CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED ->
                "this battle has the Base Stat Equalizer challenge active, which the calculator does not model"
            CalcLimitation.HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED ->
                "this battle has the Random Moves challenge active, so the selected move is not proven to be the current learned move"
            CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED ->
                "the selected move's damage mechanics are not proven equivalent to the generation III pipeline"
            CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED ->
                "the pinned H&S damage modifier order and fixed-point rounding differ from the generation III pipeline for this request"
            CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED ->
                "this is an active battle whose current effective types, battle stat words, or dynamic move type are not authoritatively observed"
            CalcLimitation.HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED ->
                "this is a Doubles battle whose current target count is not authoritatively observed, so the spread-move reduction cannot be determined"
            CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED ->
                "the live battle format is not an authoritatively observed Singles battle, so the Singles damage arithmetic cannot be applied"
            CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED ->
                "this is a Doubles battle with Reflect or Light Screen active, whose cartridge arithmetic (2 * (damage / 3) only while both defenders are present) this calculation does not reproduce"
            CalcLimitation.VANILLA_DOUBLES_SPREAD_NOT_MODELLED ->
                "this Doubles move hits both opponents, and the reduction the games apply depends on how many opposing battlers are actually present, which this request does not establish"
            CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED ->
                "the current move's type is being rewritten to Electric by Ion Deluge or Electrify, which this calculation does not model"
            CalcLimitation.HNS_GLAIVE_RUSH_ACTIVE_NOT_MODELLED ->
                "the defender has the Glaive Rush volatile, which doubles incoming damage and is not modelled"
            CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED ->
                "the battle's live field status (Wonder Room, terrain, Gravity, Mud/Water Sport or another unmodelled field effect) is not modelled by this calculation"
            CalcLimitation.HNS_CHARGE_ACTIVE_NOT_MODELLED ->
                "the attacker is charging, which doubles its Electric moves and is not modelled by this calculation"
            CalcLimitation.HNS_TAR_SHOT_ACTIVE_NOT_MODELLED ->
                "the defender is tar-shotted, which doubles incoming Fire damage and is not modelled by this calculation"
            CalcLimitation.HNS_FORESIGHT_ACTIVE_NOT_MODELLED ->
                "a participant has the Foresight volatile, which bypasses Ghost immunity and is not modelled by this calculation"
            CalcLimitation.HNS_MIRACLE_EYE_ACTIVE_NOT_MODELLED ->
                "a participant has the Miracle Eye volatile, which bypasses Dark immunity and is not modelled by this calculation"
            CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED ->
                "a participant's grounding volatile (Ingrain, Smack Down, Telekinesis or Magnet Rise) changes Ground immunity and is not modelled by this calculation"
            CalcLimitation.HNS_ROOST_ACTIVE_NOT_MODELLED ->
                "a participant has the Roost volatile, so the engine's effective types differ from the raw types and are not modelled by this calculation"
            CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED ->
                "a participant's ability is suppressed by Gastro Acid, so the engine's effective ability is not the raw ability identity"
            CalcLimitation.HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED ->
                "a participant has a substitute, which redirects the computed damage and is not modelled by this calculation"
            CalcLimitation.HNS_ENDURED_ACTIVE_NOT_MODELLED ->
                "a participant has the Endure volatile, which caps incoming damage at 1 HP and is not modelled by this calculation"
            CalcLimitation.HNS_GIMMICK_STATE_UNREADABLE ->
                "the battle's gimmick state (Tera/Dynamax/Z) could not be read"
            CalcLimitation.HNS_GIMMICK_ACTIVE_NOT_MODELLED ->
                "a battle gimmick (Tera/Dynamax/Z) is active and is not modelled by this calculation"
            CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED ->
                "the attacker's live status condition is not modelled by this ordinary-damage calculation"
            CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED ->
                "a pinch ability applies to this move but its live HP condition could not be verified"
            CalcLimitation.HNS_LIVE_WEATHER_UNKNOWN ->
                "the battle's live weather could not be read, so it cannot be assumed clear"
            CalcLimitation.HNS_LIVE_WEATHER_NOT_MODELLED ->
                "the battle's live weather is not modelled by this ordinary-damage calculation"
            CalcLimitation.HNS_LIVE_SCREENS_UNKNOWN ->
                "the defender's live Reflect / Light Screen state could not be read, so it cannot be assumed screenless"
            CalcLimitation.HNS_LIVE_SIDE_STATUS_NOT_MODELLED ->
                "the defender's live side status (for example Aurora Veil) is not modelled by this calculation"
            CalcLimitation.ITEM_BOOST_PERCENTAGE_DIFFERS ->
                "this build scales type-boost items differently from the generation III pipeline"
            CalcLimitation.LIVE_INPUTS_NOT_VERIFIED ->
                "these values were read from a game state that is not verified"
            CalcLimitation.SPECIES_NOT_IN_PINNED_DATA ->
                "the species is not in this build's pinned data"
            CalcLimitation.MOVE_NOT_IN_PINNED_DATA ->
                "the move is not in this build's pinned data"
            CalcLimitation.BOOSTS_OUT_OF_RANGE ->
                "the stat boosts are outside the range the games can produce"
            CalcLimitation.STAT_VALUES_OUT_OF_RANGE ->
                "the IVs or EVs are outside the range this build can produce"
            CalcLimitation.LEVEL_OUT_OF_RANGE ->
                "the level is outside the range the games can produce"
            CalcLimitation.STATUS_NOT_MODELLED ->
                "the status condition is not one this calculation models"
            CalcLimitation.FIELD_CONDITION_NOT_MODELLED ->
                "this calculation's engine does not model that weather or terrain"
            CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN ->
                "a live-read participant is missing a damage-relevant field, and a missing field is not a neutral one"
        }
    }
}

/**
 * The single, explicit calculator-capability policy for the beta targets.
 *
 * Rules, in order:
 *  1. Resolve the exact ruleset from the active [RomHackProfile]. A profile with no documented row
 *     is [CalcSupport.UNSUPPORTED] — never silently Gen III.
 *  2. Apply that row's capability matrix (mechanics generation, confidence ceiling, unconditional
 *     limitations).
 *  3. Add per-request limitations for anything the request asks for that the row does not cover.
 *     Unknown abilities, items, species and moves are refused rather than guessed, because the
 *     bridge selects content by name and the engine would otherwise substitute its own record.
 *  4. Cap the result at ESTIMATED unless the runtime ROM identity is the exact verified build.
 *
 * This policy reads no live memory, changes no trust hash, and extends no progression.
 */
object CalcCapabilityPolicy {

    /**
     * Vanilla Gen III: exactly the abilities `@smogon/calc`'s ADV pipeline applies to a damage
     * calculation it is asked to run.
     *
     * This is a whitelist rather than "every Gen III ability" on purpose. The engine does not error
     * on an ability it does not model - it silently ignores it - so the only way to keep an
     * unmodelled ability out of a *verified* result is to enumerate what is modelled. Anything
     * outside this set is refused for vanilla.
     *
     * Deliberately absent, with reasons:
     *  - `Air Lock` / `Cloud Nine`: recognised, but the engine only nulls weather for a Pokemon it
     *    was told is currently on the field (`abilityOn`), which this request shape cannot express.
     *  - `Intimidate`: the engine gates it behind `abilityOn` and never applies the Gen III
     *    switch-in trigger itself, so an Intimidate request would be under-modelled, not modelled.
     *  - the `abilityOn` group (`Flash Fire`, `Plus`, `Minus`): the boost needs a flag this request
     *    shape does not carry.
     *  - `Forecast`: it rewrites Castform's typing from supplied weather, but the bridge passes a
     *    chosen ability name rather than the ability the running game actually has.
     */
    val GEN3_MODELLED_ABILITIES: Set<String> = setOf(
        // Ability immunities resolved before the damage formula runs.
        "Levitate", "Volt Absorb", "Water Absorb", "Wonder Guard", "Soundproof",
        // Attack- and defense-stat modifiers.
        "Huge Power", "Pure Power", "Hustle", "Guts", "Marvel Scale", "Thick Fat",
        // Pinch boosts, whose condition the engine derives from the supplied current HP.
        "Blaze", "Torrent", "Overgrow", "Swarm",
        // Critical-hit immunity.
        "Battle Armor", "Shell Armor"
    )

    /** Vanilla Gen III items with a direct damage effect in the ADV pipeline. */
    val GEN3_DIRECT_EFFECT_ITEMS: Set<String> = setOf(
        "Choice Band", "Deep Sea Scale", "Deep Sea Tooth", "Light Ball", "Metal Powder",
        "Soul Dew", "Thick Club"
    )

    /**
     * Vanilla Gen III type-boost items.
     *
     * `Sea Incense` is deliberately absent: the engine models it as its own case at x1.05 for Water
     * rather than as the generic type-boost item, so listing it here would overstate what happens.
     */
    val GEN3_TYPE_BOOST_ITEMS: Set<String> = setOf(
        "Black Belt", "Black Glasses", "Charcoal", "Dragon Fang", "Hard Stone", "Magnet",
        "Metal Coat", "Miracle Seed", "Mystic Water", "Never-Melt Ice", "Pink Bow",
        "Poison Barb", "Polkadot Bow", "Sharp Beak", "Silk Scarf", "Silver Powder",
        "Soft Sand", "Spell Tag", "Twisted Spoon"
    )

    /** Every held item whose damage effect the ADV pipeline applies for vanilla Gen III. */
    val GEN3_MODELLED_ITEM_NAMES: Set<String> =
        GEN3_DIRECT_EFFECT_ITEMS + GEN3_TYPE_BOOST_ITEMS + "Sea Incense"

    /** Stat stages every supported build shares: -6..+6. */
    val BOOST_RANGE: IntRange = -6..6

    /** Gen III IV/EV model, shared by both supported builds. */
    val IV_RANGE: IntRange = 0..31
    val EV_RANGE: IntRange = 0..255

    /** Level range both supported builds share. */
    val LEVEL_RANGE: IntRange = 1..100

    /**
     * The status conditions the engine models for the generation III pipeline.
     *
     * The engine stores an unrecognised string verbatim and then treats the Pokemon as simply
     * having *a* status: that turns on Guts and Marvel Scale while skipping the burn halving, so an
     * unknown condition silently produces a wrong number rather than an error.
     */
    val MODELLED_STATUSES: Set<String> = setOf("brn", "par", "slp", "frz", "psn", "tox")

    /**
     * The weather names the generation III pipeline applies.
     *
     * The engine compares weather strings exactly (`'Sun'`, `'Rain'`, `'Sand'`, `'Hail'`), so a
     * near-miss or a later-generation name is silently treated as *no weather* rather than
     * rejected. `Snow` in particular is a real, reachable condition in Heart & Soul 2.0.5
     * (`B_PREFERRED_ICE_WEATHER == B_ICE_WEATHER_BOTH`) that this pipeline cannot express, so it
     * must be refused, not ignored.
     */
    val MODELLED_WEATHER: Set<String> = setOf("Sun", "Rain", "Sand", "Hail")

    /**
     * The Heart & Soul 2.0.5 rule toggles that must be read before any H&S damage number may be
     * presented, in the canonical order they are reported when absent or unread.
     *
     * Each is `SaveBlock3.challengeSettings`, is player-settable on a free tab, and changes which
     * rule the engine applies rather than merely which values it uses:
     *  - `optionStyle` - the "PHYS/SP SPLIT" row. Raw value 0 (`PER_MOVE_SPLIT`) selects the move's
     *    own category; raw value 1 (`TYPE_BASED`) selects generation III's type-based damage category.
     *  - `tx_Mode_Fairy_Types` - "ADD FAIRY TYPE". Off deletes the type: species revert to their
     *    pre-Fairy typings and Fairy moves are retyped.
     *  - `tx_Random_Type` - "RANDOM TYPES" rewrites species typings.
     *  - `tx_Random_TypeEffectiveness` - "RANDOM TYPE EFFECTIVENESS" remaps the attacking type
     *    inside the type chart at damage time.
     */
    val HNS_REQUIRED_RULE_READS: List<CalcLimitation> = listOf(
        CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE,
        CalcLimitation.FAIRY_TOGGLE_UNREADABLE,
        CalcLimitation.RANDOM_TYPES_UNREADABLE,
        CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE
    )

    val HNS_REPRESENTABLE_TYPES: Set<String> = setOf(
        "Normal", "Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel",
        "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark", "Fairy",
        "???"
    )

    /** `STATUS_FIELD_ION_DELUGE` from the pinned `include/constants/battle.h`. */
    const val HNS_STATUS_FIELD_ION_DELUGE: Int =
        com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.STATUS_FIELD_ION_DELUGE

    /**
     * The only `gFieldStatuses` bit the ordinary subset models. Ion Deluge can force a Normal
     * move to Electric, which the policy handles explicitly; every other bit (Wonder Room,
     * Gravity, the four terrains, Mud/Water Sport, Trick/Magic Room, Fairy Lock) changes ordinary
     * damage, the type chart or the defensive stat and must fail closed with
     * [CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED].
     */
    const val HNS_SUPPORTED_FIELD_STATUS_MASK: Int =
        com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.FIELD_STATUS_SUPPORTED_MASK

    /**
     * The pinch abilities and the move type each boosts, keyed by the pinned numeric ability ID.
     *
     * Pinned H&S `CalcAttackStat` (`src/battle_util.c:7023`) applies x1.5 as an Attack-stat
     * modifier when `moveType == TYPE_X && hp <= maxHP/3`. These are the only conditional
     * abilities C4e promotes; every other ability stays on the generic capability gate.
     */
    val HNS_PINCH_ABILITY_TYPES: Map<Int, String> = mapOf(
        65 to "Grass", // Overgrow
        66 to "Fire",  // Blaze
        67 to "Water", // Torrent
        68 to "Bug"    // Swarm
    )

    const val HNS_DATA_PACK_ID = "hns_2_0_5"
    const val HNS_ENGINE = "pokeemerald-expansion"
    const val HNS_PINNED_COMMIT = "1f42b74dff0e9fe942419845d040663dd829a973"
    const val HNS_LABEL = "Pokemon Heart & Soul 2.0.5 (Release-v2.0.5, $HNS_PINNED_COMMIT)"

    private val UNSUPPORTED_CAPABILITY = CalcCapability(
        ruleset = CalcRuleset.VANILLA_GEN3,
        mechanicsGeneration = 3,
        contentSource = "none",
        ceiling = CalcSupport.UNSUPPORTED,
        alwaysLimitations = emptyList(),
        label = "Unsupported build"
    )

    /**
     * Resolve the capability matrix row for [profile], or null when no row exists.
     *
     * Recognition is by explicit profile identity only: engine, physical/special split, pinned
     * data-pack id, and the absence of an unsupported overlay. A display name or file name can
     * never select a ruleset.
     */
    fun capabilityFor(profile: RomHackProfile): CalcCapability? {
        val packId = profile.gameDataPackId?.trim()?.lowercase()
        val expansionEngine = profile.engine.trim().equals(HNS_ENGINE, ignoreCase = true)

        if (packId == HNS_DATA_PACK_ID && expansionEngine && profile.hasPhysSpecSplit) {
            return CalcCapability(
                ruleset = CalcRuleset.HNS_2_0_5,
                // 3 is chosen because the hack is INDIVIDUALLY DEMONSTRATED to share three
                // generation III constants with this pipeline: the critical-hit multiplier is x2
                // (B_CRIT_MULTIPLIER GEN_3), a two-target hit is halved (B_MULTIPLE_TARGETS_DMG
                // GEN_3), and Thick Fat halves the attack stat. It is NOT an equivalence finding:
                // the generation III chart makes Steel resist Ghost and Dark while the hack's does
                // not, and the hack has Fairy. This value currently reaches no H&S result at all,
                // because the row below refuses every H&S request.
                // See docs/HNS_2_0_5_CALCULATOR_CAPABILITY.md §§3.2, 3.3.
                mechanicsGeneration = 3,
                // Pinned pack proves which names belong to this build. Data overrides are
                // forwarded via CalcDataOverrides (Gap B closed).
                contentSource = HNS_DATA_PACK_ID,
                ceiling = CalcSupport.ESTIMATED,
                // Gap C4e promotes the exact 2.0.5 ROM SHA into the profile, so this row no
                // longer carries a blanket "no hash" limitation. An untrusted or non-exact ROM
                // still adds ROM_NOT_EXACT_VERIFIED per request in evaluate().
                alwaysLimitations = emptyList(),
                label = HNS_LABEL
            )
        }

        // Exact vanilla Gen III only. A split-mechanics vanilla build, a CFRU hack, or any other
        // unnamed ROM must not inherit these numbers: it gets its own row or nothing.
        if (profile.isSupportedVanillaGen3() && !profile.hasPhysSpecSplit) {
            return CalcCapability(
                ruleset = CalcRuleset.VANILLA_GEN3,
                mechanicsGeneration = 3,
                contentSource = "gen3_vanilla",
                ceiling = CalcSupport.VERIFIED,
                // The generation III ruleset has no player-configurable damage rule, so a request
                // that matches the verified fixture inputs can be presented as verified.
                alwaysLimitations = emptyList(),
                label = "Vanilla ${profile.baseGame} (Generation III)"
            )
        }

        return null
    }

    /**
     * The production decision for [request] on [profile].
     *
     * [trust] is the same runtime trust value the rest of the companion uses. When it is null, or
     * does not hold [RuntimeRomTrust.exactRuntimeVerified] for this profile's hashes, the verdict
     * is capped at ESTIMATED: the mechanics are known, but the running bytes are not proven to be
     * that build.
     */
    fun evaluate(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        request: DamageCalculationRequest
    ): CalcCapabilityVerdict {
        val capability = capabilityFor(profile) ?: return unsupported(profile)

        val limitations = LinkedHashSet(capability.alwaysLimitations)
        val abilityDecisions = mutableListOf<HnsAbilityRequestDecision>()
        val itemDecisions = mutableListOf<HnsItemRequestDecision>()

        // Limitations the production preparation path already discovered are part of the decision,
        // not a separate opinion: an incomplete live read must be able to lower the verdict even
        // when every supplied field looks fine.
        limitations.addAll(request.preparationLimitations)

        if (request.gen != capability.mechanicsGeneration) {
            limitations.add(CalcLimitation.MECHANICS_GENERATION_MISMATCH)
        }

        collectRequestLimitations(profile, capability, request, limitations, abilityDecisions, itemDecisions)

        if (capability.ruleset == CalcRuleset.HNS_2_0_5) {
            val exactTrusted = isExactRuntimeVerified(profile, trust)
            val rules = if (exactTrusted) request.hnsRuntimeRules else null

            // 1. optionStyle (CATEGORY_SPLIT)
            if (rules?.optionStyle != com.dualdex.pokemon.hns.HnsOptionStyle.PER_MOVE_SPLIT &&
                rules?.optionStyle != com.dualdex.pokemon.hns.HnsOptionStyle.TYPE_BASED) {
                limitations.add(CalcLimitation.CATEGORY_SPLIT_TOGGLE_UNREADABLE)
            }

            // 2. Fairy Mode
            if (rules?.fairyTypesEnabled == null) {
                limitations.add(CalcLimitation.FAIRY_TOGGLE_UNREADABLE)
            }

            // 3. Random Types
            when (rules?.randomTypesEnabled) {
                null -> limitations.add(CalcLimitation.RANDOM_TYPES_UNREADABLE)
                true -> limitations.add(CalcLimitation.RANDOM_TYPES_ACTIVE_NOT_MODELLED)
                false -> { /* Observed OFF: no limitation */ }
            }

            // 4. Random Type Effectiveness
            when (rules?.randomTypeEffectivenessEnabled) {
                null -> limitations.add(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_UNREADABLE)
                true -> limitations.add(CalcLimitation.RANDOM_TYPE_EFFECTIVENESS_ACTIVE_NOT_MODELLED)
                false -> { /* Observed OFF: no limitation */ }
            }

            // 4b. Base Stat Equalizer rewrites every non-HP battle stat while the request still
            // carries the pinned species' ordinary base stats. The setting therefore changes a
            // value this request does not capture and cannot be cleared by downstream observation.
            // Unobserved / out-of-domain fails closed; observed nonzero blocks precisely.
            when (rules?.baseStatEqualizerMode) {
                null -> limitations.add(CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE)
                0 -> { /* Observed OFF: ordinary base stats */ }
                else -> limitations.add(CalcLimitation.HNS_BASE_STAT_EQUALIZER_NOT_MODELLED)
            }

            // 4c. Random Moves rerolls the learned moveset at acquisition. The calculator never
            // consults the observed party moveset, so an active challenge means the selected move
            // cannot be proven to be the authoritative current learned move. Unobserved fails
            // closed; observed ON blocks precisely.
            when (rules?.randomMovesEnabled) {
                null -> limitations.add(CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE)
                true -> limitations.add(CalcLimitation.HNS_RANDOM_MOVES_ACTIVE_NOT_MODELLED)
                false -> { /* Observed OFF: the party's own moveset is the authority */ }
            }

            // 5. Generic challenge settings unreadable
            val allRequiredObserved = rules != null &&
                (rules.optionStyle == com.dualdex.pokemon.hns.HnsOptionStyle.PER_MOVE_SPLIT ||
                 rules.optionStyle == com.dualdex.pokemon.hns.HnsOptionStyle.TYPE_BASED) &&
                rules.fairyTypesEnabled != null &&
                rules.randomTypesEnabled != null &&
                rules.randomTypeEffectivenessEnabled != null
            if (!allRequiredObserved) {
                limitations.add(CalcLimitation.CHALLENGE_SETTINGS_UNREADABLE)
            }

            // 6. Type chart modelling (Gap C1):
            // Types must be representable in the H&S type chart (standard 18 types or ???)
            val pack = GameDataPackRegistry.getForProfile(profile)
            val requestTypes = mutableListOf<String>()
            if (request.attackerOverride != null) {
                requestTypes.addAll(request.attackerOverride.types)
            } else {
                val s = pack.getSpeciesByName(request.attacker.species)
                if (s != null) {
                    requestTypes.add(s.type1.displayName)
                    s.type2?.let { requestTypes.add(it.displayName) }
                }
            }
            if (request.defenderOverride != null) {
                requestTypes.addAll(request.defenderOverride.types)
            } else {
                val s = pack.getSpeciesByName(request.defender.species)
                if (s != null) {
                    requestTypes.add(s.type1.displayName)
                    s.type2?.let { requestTypes.add(it.displayName) }
                }
            }
            if (request.moveOverride != null) {
                requestTypes.add(request.moveOverride.type)
            } else {
                val m = pack.getMoveByName(request.move.name)
                if (m != null) {
                    requestTypes.add(m.type.displayName)
                }
            }

            val unrepresentable = requestTypes.any { it !in HNS_REPRESENTABLE_TYPES }
            if (unrepresentable) {
                limitations.add(CalcLimitation.UNREPRESENTABLE_TYPE_NOT_MODELLED)
            }

            val typeChartModelled = exactTrusted &&
                allRequiredObserved &&
                rules != null &&
                rules.randomTypesEnabled == false &&
                rules.randomTypeEffectivenessEnabled == false &&
                !unrepresentable &&
                request.typeSystem == "hns_2_0_5"

            if (!typeChartModelled) {
                limitations.add(CalcLimitation.HNS_TYPE_CHART_NOT_MODELLED)
            }

            // 7. Ordinary-damage modifier ordering (Gap C4b).
            // calculateHnsDamage in QuickJS implements the exact H&S 2.0.5 UQ4.12 roll-first
            // pipeline, supporting neutral base damage, STAB, type effectiveness, crits, stat
            // stages (-6..+6), burn, screens (Reflect / Light Screen), doubles, and rain/sun weather.
            if (hnsModifierOrderDiverges(pack, request)) {
                limitations.add(CalcLimitation.HNS_DAMAGE_MODIFIER_ORDER_NOT_MODELLED)
            }

            // 8. Badge boost observability (Gap C4b).
            // Badge possession is not a neutral default. When badge applicability is unspecified
            // (manual / out-of-battle request with no boundary-owned live state) the request must
            // fail closed, never silently compute with every badge boost false. In an active battle
            // the player attacker's badge boosts must be authoritatively observed from save memory.
            if (hnsBadgeBoostNotModelled(request)) {
                limitations.add(CalcLimitation.BADGE_BOOST_NOT_MODELLED)
            }

            // 8b. Doubles runtime target count (Gap C4b R2). H&S halves a spread move only when
            // GetMoveTargetCount(ctx) == 2, which is live target-presence state the static request
            // does not carry. Without an authoritative count, a Doubles request fails closed rather
            // than halving every spread move.
            if (hnsDoublesTargetCountNotModelled(request)) {
                limitations.add(CalcLimitation.HNS_DOUBLES_TARGET_COUNT_NOT_MODELLED)
            }

            // 8c. Live battle format (Gap C4e review round 5). H&S selects format-dependent
            // arithmetic (screens x0.667 vs x0.5, the spread target count, partner-dependent
            // branches). The request's `field.gameType` is caller/UI-owned and cannot be the
            // authority, so the boundary owns the observed `gBattlersCount` and this gate refuses
            // the whole live calculation when that topology is not the Singles the subset models.
            if (hnsLiveBattleFormatNotModelled(request)) {
                limitations.add(CalcLimitation.HNS_LIVE_BATTLE_FORMAT_NOT_MODELLED)
            }

            // 9. Live battle state (Gap C4a R1 / C4b). The request shape carries static species/move
            // operands, but H&S mutates them during battle (current effective types, raw battle
            // stat words, dynamic move type, transient state). An active battle whose mutable
            // classes are not authoritatively observed fails closed here rather than letting the
            // static view clear the ordinary-safe path.
            if (hnsLiveBattleStateNotModelled(pack, request)) {
                limitations.add(CalcLimitation.HNS_LIVE_BATTLE_STATE_NOT_MODELLED)
            }

            // 9b. Gap C4e live-operand disposition. The boundary observed the field word,
            // electrified volatile, Glaive Rush volatile, gimmick state, HP and status; a positive
            // (or unread) value that the ordinary arithmetic does not model fails closed with its
            // own precise limitation rather than the coarse live-state blocker.
            collectHnsLiveOperandLimitations(request, limitations)
        }

        if (!isExactRuntimeVerified(profile, trust)) {
            limitations.add(CalcLimitation.ROM_NOT_EXACT_VERIFIED)
        }

        val blocked = limitations.any { it.blocksCalculation }
        val support = when {
            blocked -> CalcSupport.UNSUPPORTED
            // VERIFIED is reached only for a ruleset whose every damage rule is known and whose
            // request is fully covered - which today is only exact vanilla Gen III. What it claims
            // is that this pipeline reproduces that build's damage for the supplied inputs; it does
            // NOT claim to know the running game's full battle state. The known exclusions are the
            // generation III badge boost and move mechanics the engine does not model, all recorded
            // in docs/HNS_2_0_5_CALCULATOR_CAPABILITY.md §8. The golden fixtures in
            // native/tests/test_js_calc.c encode the unbadged state that claim is scoped to.
            limitations.isEmpty() && capability.ceiling == CalcSupport.VERIFIED -> CalcSupport.VERIFIED
            else -> CalcSupport.ESTIMATED
        }

        return CalcCapabilityVerdict(
            support = support,
            capability = capability,
            limitations = limitations.toList(),
            // The request is normalised before it is authorised: the generation is forced to the
            // resolved ruleset, and ability/item names are rewritten to the exact spelling the
            // engine matches, so what reaches the engine is what the verdict was computed from.
            request = if (blocked) {
                null
            } else {
                normaliseNames(
                    capability.ruleset,
                    request.copy(gen = capability.mechanicsGeneration)
                )
            },
            hnsAbilityDecisions = abilityDecisions.toList(),
            hnsItemDecisions = itemDecisions.toList()
        )
    }

    /** The verdict for a build with no documented calculator capability. */
    fun unsupported(profile: RomHackProfile): CalcCapabilityVerdict = CalcCapabilityVerdict(
        support = CalcSupport.UNSUPPORTED,
        capability = UNSUPPORTED_CAPABILITY,
        limitations = emptyList(),
        request = null,
        unsupportedReason = unsupportedReason(profile)
    )

    /**
     * True only when the running ROM is the exact build [profile] was verified against.
     *
     * Deliberately the same rule the rest of the companion uses: an exact match method, a profile
     * that asserts verification, a verified memory layout, and a runtime hash present in the
     * profile's own hash list.
     */
    fun isExactRuntimeVerified(profile: RomHackProfile, trust: RuntimeRomTrust?): Boolean {
        if (trust == null) return false
        if (!trust.exactRuntimeVerified) return false
        val active = trust.activeRomSha256 ?: return false
        return profile.sha256Hashes.any { it.equals(active, ignoreCase = true) }
    }

    /**
     * True only when the named ability's damage effect reaches the pipeline for [ruleset].
     *
     * A name the pipeline does not recognise is not an error there: it is silently ignored, which
     * is why an unrecognised ability must be refused rather than passed through.
     *
     * Names are compared on a normalised form. The engine's own comparison is case-sensitive, so the
     * normalisation happens here and the request is rewritten to the canonical spelling by
     * [normaliseNames] before it is serialised - otherwise "thick fat" would be passed through as
     * written, silently do nothing, and still be reported as verified.
     */
    fun isAbilityModelled(ruleset: CalcRuleset, ability: String): Boolean = when (ruleset) {
        CalcRuleset.VANILLA_GEN3 ->
            canonicalAbility(ability) != null
        CalcRuleset.HNS_2_0_5 ->
            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(ability).category.isSupportedForDamage
    }

    /**
     * True only when the numeric H&S ability ID's damage effect reaches the pipeline for [ruleset].
     */
    fun isAbilityModelled(ruleset: CalcRuleset, abilityId: Int): Boolean = when (ruleset) {
        CalcRuleset.VANILLA_GEN3 -> false
        CalcRuleset.HNS_2_0_5 ->
            abilityId in 0..com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.ABILITY_ID_MAX &&
                com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(abilityId).category.isSupportedForDamage
    }

    /**
     * The canonical spelling of [ability] when the ADV pipeline models it, else null.
     *
     * The engine matches ability names exactly, so a differently-cased name is not the ability - it
     * is silently ignored. Matching leniently here and rewriting to the canonical spelling is the
     * only way to accept a reasonably-spelled name without publishing an unscaled number.
     */
    fun canonicalAbility(ability: String): String? =
        GEN3_MODELLED_ABILITIES.firstOrNull { it.equals(ability.trim(), ignoreCase = true) }

    /**
     * The limitation a held item NAME adds for [ruleset], or null when the item's damage effect
     * is faithfully represented.
     *
     * This is the manual/hypothetical name path. Live H&S participants are classified by numeric
     * ID in [collectHnsItemLimitation]; this helper exists for callers that only have a name and
     * resolves it against the exact H&S catalogue, never the generic expansion table.
     */
    fun itemLimitation(ruleset: CalcRuleset, item: String): CalcLimitation? = when (ruleset) {
        CalcRuleset.HNS_2_0_5 -> {
            val trimmed = item.trim()
            when {
                trimmed.equals("None", ignoreCase = true) -> null
                else -> {
                    val entry = com.dualdex.pokemon.hns.HnsItemRegistry.classifyByName(trimmed)
                    when {
                        entry.category.isSupportedForDamage -> null
                        entry.itemId == null -> CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE
                        else -> CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED
                    }
                }
            }
        }
        CalcRuleset.VANILLA_GEN3 ->
            if (canonicalItem(item) != null) null else CalcLimitation.ITEM_NOT_MODELLED
    }

    /**
     * Records [CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED] when the selected H&S
     * move's damage semantics read held-item state and this calculator does not explicitly
     * reproduce that interaction.
     *
     * The move is resolved to the exact pack's numeric ID so a differently-cased or
     * differently-spelled name cannot dodge the audit. A move absent from the pack is already
     * refused by [CalcLimitation.MOVE_NOT_IN_PINNED_DATA]; this gate deliberately does not
     * double-report an unknown move, because it has no item interaction to reason about.
     */
    private fun collectHnsItemDependentMoveLimitation(
        pack: GameDataPack,
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>
    ) {
        val move = pack.getMoveByName(request.move.name) ?: return
        if (com.dualdex.pokemon.hns.HnsMoveItemInteractionRegistry.classify(move.id).requiresBlock) {
            limitations.add(CalcLimitation.HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED)
        }
    }

    /**
     * True when [request] would exercise a damage modifier not supported by the H&S calculator
     * engine (Gap C4b arithmetic parity).
     *
     * In Gap C4b, calculateHnsDamage executes the exact H&S 2.0.5 pipeline: roll-first UQ4.12
     * half-down composition, STAB (including Adaptability), type effectiveness, crits (with drop
     * ignore rules), stat stages (-6..+6), burn, screens (Reflect / Light Screen, singles/doubles),
     * and weather (Rain / Sun).
     *
     * Requests with unsupported weather (e.g. Sandstorm / Hail), out-of-range stat stages, or
     * missing typeSystem fail closed.
     */
    private fun hnsModifierOrderDiverges(
        pack: GameDataPack,
        request: DamageCalculationRequest
    ): Boolean {
        if (request.typeSystem != "hns_2_0_5") return true
        val weather = request.field.weather?.trim()?.lowercase()
        if (!weather.isNullOrBlank() && weather != "none" &&
            !weather.contains("rain") && !weather.contains("sun")
        ) {
            return true
        }
        if (hnsHasOutOfRangeStages(request.attacker.boosts) ||
            hnsHasOutOfRangeStages(request.defender.boosts)
        ) {
            return true
        }
        return false
    }

    /** True when any stat stage is outside the supported -6..+6 range. */
    private fun hnsHasOutOfRangeStages(boosts: StatBlock?): Boolean {
        if (boosts == null) return false
        return boosts.atk !in -6..6 || boosts.def !in -6..6 ||
            boosts.spa !in -6..6 || boosts.spd !in -6..6 ||
            boosts.spe !in -6..6
    }

    /**
     * True when badge boost applicability/state is unmodelled or unobserved (Gap C4b R7).
     *
     * Badge boosts in H&S are player-side only: upstream `ShouldGetStatBadgeBoost` always returns
     * FALSE for non-player-side battlers (`!IsOnPlayerSide(battler)`). The enemy defender never
     * receives a badge boost, so only the attacker's badge state is relevant.
     *
     * A manual / out-of-battle request has no boundary-owned live state, which means badge
     * applicability is simply unspecified - it is NOT a neutral "the player owns no badges" state.
     * That fails closed here rather than silently computing with every badge boost false. In an
     * active battle the player attacker's badge state must be authoritatively observed from save
     * memory.
     */
    private fun hnsBadgeBoostNotModelled(request: DamageCalculationRequest): Boolean {
        val live = request.hnsLiveBattleState ?: return true
        // Only gate on attacker badge boosts: badges are player-side only. The defender
        // never has badge boosts regardless of battle position.
        if (request.attacker.partySlot != null && live.attackerBadgeBoosts == null) return true
        if (request.attacker.partySlot == null && request.defender.partySlot == null) return true
        return false
    }

    /**
     * True when an H&S Doubles request cannot establish the runtime target count (Gap C4b R2).
     *
     * H&S applies the Gen-III spread reduction only when `GetMoveTargetCount(ctx)` is exactly 2, so
     * the modifier depends on how many opposing battlers are currently present - live state the
     * static request does not carry. No runtime reader supplies it yet, so the boundary always
     * binds null and every H&S Doubles request fails closed instead of halving spread moves
     * unconditionally. This is deliberately coarse: the whole Doubles format stays blocked until
     * the count is represented.
     */
    private fun hnsDoublesTargetCountNotModelled(request: DamageCalculationRequest): Boolean {
        if (!request.field.gameType.equals(CalcGameTypes.DOUBLES, ignoreCase = true)) return false
        val live = request.hnsLiveBattleState ?: return true
        val count = live.moveTargetCount ?: return true
        return count < 1
    }

    /**
     * True when a live H&S request's battle format is not the authoritatively observed Singles
     * topology the production subset models (review round 5).
     *
     * The live topology is boundary-owned [CalcHnsLiveBattleState.observedBattlersCount], never
     * the request's caller/UI-supplied `field.gameType`. The C4e production subset models
     * Singles only: the gate returns false only when both battle-level observations read
     * `gBattlersCount`, agreed on it, the agreed value is the Singles value `2`, and the request
     * label agrees it is Singles. Any non-Singles live battle (observed `4`, disagreement, unread
     * word, or a request label that is not Singles) fails closed here. Letting a Doubles request
     * clear would expose unmodelled live operands (e.g. Helping Hand) and wrong-format screen
     * multipliers (`UQ_4_12(0.5)` instead of `UQ_4_12(0.667)`). Returns false when there is no
     * live battle state: a manual/out-of-battle request has no active format to observe.
     */
    private fun hnsLiveBattleFormatNotModelled(
        request: DamageCalculationRequest
    ): Boolean {
        val live = request.hnsLiveBattleState ?: return false
        val observed = live.observedBattlersCount ?: return true

        val requestIsSingles =
            request.field.gameType.equals(
                CalcGameTypes.SINGLES,
                ignoreCase = true
            )

        return observed != 2 || !requestIsSingles
    }

    /**
     * True when an active H&S battle carries a mutable damage operand this calculator does not
     * authoritatively observe (Gap C4a R1).
     *
     * Returns false when [DamageCalculationRequest.hnsLiveBattleState] is null: a manual
     * hypothetical, or a live read of stored party data, is not an active battle and none of these
     * operands can have been rewritten. When it is present, each class is checked independently so
     * a class that is provably neutral (observed live types that match the static record) does not
     * itself block, while every unobserved class does.
     */
    private fun hnsLiveBattleStateNotModelled(
        pack: GameDataPack,
        request: DamageCalculationRequest
    ): Boolean {
        val live = request.hnsLiveBattleState ?: return false

        // 1. Current effective battler types. An authoritative observation that matches the static
        //    record is the only way this class is provably neutral; unobserved, out-of-domain,
        //    typeless, third non-empty, or mismatching typing blocks.
        if (!hnsLiveTypesProven(request.attacker, request.attackerOverride, live.attackerTypes, pack)) return true
        if (!hnsLiveTypesProven(request.defender, request.defenderOverride, live.defenderTypes, pack)) return true

        // 2. Raw battle stat words (Power Trick swaps gBattleMons attack/defense with unchanged
        //    stages). No runtime reader supplies this yet: C4b.
        if (!live.attackerBattleStatWordsObserved || !live.defenderBattleStatWordsObserved) return true

        // 3. Dynamic move type (Ion Deluge / Electrify via SetTypeBeforeUsingMove). The move keeps
        //    its static effect ID, so the generated effect map cannot see it. C4b.
        if (!live.dynamicMoveTypeObserved) return true

        // 4. Other transient damage state reachable by the supported ordinary subset. C4b.
        if (!live.transientStateObserved) return true

        // 5. Persistent volatile state the pinned ordinary-damage path reads (Foresight / Miracle
        //    Eye immunity bypass, Ingrain / Smack Down / Telekinesis / Magnet Rise grounding,
        //    Gastro Acid ability suppression, Roost effective-type change, and the
        //    GetAdjustedDamage substitute / endured states). An unread window cannot be assumed
        //    neutral, so it blocks here (review round 4).
        val attackerPersistent = live.attackerPersistentVolatiles
        val defenderPersistent = live.defenderPersistentVolatiles
        if (attackerPersistent == null || !attackerPersistent.observed) return true
        if (defenderPersistent == null || !defenderPersistent.observed) return true

        return false
    }

    /**
     * True only when [observed] is an authoritative current-type observation that the static
     * two-type request already represents: observed, in-domain, representable, at most two
     * non-empty slots, and equal as a set to the static record.
     */
    private fun hnsLiveTypesProven(
        input: CalcPokemonInput,
        override: CalcSpeciesOverride?,
        observed: List<String>?,
        pack: GameDataPack
    ): Boolean {
        if (observed == null || observed.isEmpty()) return false
        // The request shape carries at most two types; a third non-empty live type (e.g. a layered
        // AddType) cannot be represented and must block rather than be truncated.
        if (observed.size > 2) return false
        if (observed.any { it !in HNS_REPRESENTABLE_TYPES }) return false
        val static = hnsEffectiveTypes(input, override, pack, null)
        if (static.isEmpty()) return false
        return observed.map { it.lowercase() }.toSet() == static.map { it.lowercase() }.toSet()
    }

    /**
     * The effective species types: the authoritative live observation when present, else the
     * boundary-owned override, else the pinned pack.
     */
    private fun hnsEffectiveTypes(
        input: CalcPokemonInput,
        override: CalcSpeciesOverride?,
        pack: GameDataPack,
        liveTypes: List<String>? = null
    ): List<String> {
        if (liveTypes != null && liveTypes.isNotEmpty()) return liveTypes
        if (override != null) return override.types
        val species = pack.getSpeciesByName(input.species) ?: return emptyList()
        return listOfNotNull(species.type1.displayName, species.type2?.displayName)
    }

    /**
     * Records [CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED] when the selected H&S move's
     * damage is not the source-proven ordinary `EFFECT_HIT` path this calculator reproduces.
     *
     * The lookup is by the exact pack's numeric move ID, so a differently-cased or renamed move
     * cannot dodge the audit. A move absent from the pinned pack is already refused by
     * [CalcLimitation.MOVE_NOT_IN_PINNED_DATA]; this gate deliberately does not double-report it.
     * An item-dependent move is refused by [collectHnsItemDependentMoveLimitation] instead, and
     * the mechanics registry reports it as handled elsewhere rather than adding a second blocker.
     */
    private fun collectHnsMoveMechanicsLimitation(
        pack: GameDataPack,
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>
    ) {
        val move = pack.getMoveByName(request.move.name) ?: return
        if (com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry.classify(move.id).requiresBlock) {
            limitations.add(CalcLimitation.HNS_MOVE_MECHANICS_NOT_MODELLED)
        }
    }

    /**
     * Classifies one H&S participant's held item and records the exact limitation.
     *
     * The numeric ID is the capability authority:
     * - live read with battle-effective or party-storage provenance: an in-domain ID is classified
     *   by [com.dualdex.pokemon.hns.HnsItemRegistry.classify], an out-of-domain/absent ID is
     *   [CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE];
     * - live read with unknown provenance is unreadable;
     * - manual identity resolves through the exact H&S catalogue by ID or name; a name that does not
     *   resolve is [CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE];
     * - an omitted manual item is the request semantics' explicit no-item and adds no blocker.
     *
     * This is the STATIC half of the contextual capability. The move/item interaction is audited
     * separately by [collectHnsItemDependentMoveLimitation]; the final capability is the union, so a
     * statically-supported item is not silently trusted when the selected move reads item state.
     */
    private fun collectHnsItemLimitation(
        input: CalcPokemonInput,
        isAttacker: Boolean,
        request: DamageCalculationRequest,
        ordinaryMove: Boolean?,
        limitations: MutableSet<CalcLimitation>,
        decisions: MutableList<HnsItemRequestDecision>
    ) {
        // Global capability first; a globally unsupported/unresolved item then gets exactly one
        // request-local decision, and only PROVEN_IRRELEVANT removes its blocker.
        fun classify(id: Int) {
            if (com.dualdex.pokemon.hns.HnsItemRegistry.isSupportedForDamage(id)) return
            val side = if (isAttacker) HnsItemSide.ATTACKER else HnsItemSide.DEFENDER
            val decision = HnsItemContextPolicy.assess(
                itemId = id,
                context = HnsItemContextPolicy.contextForRequest(request, side, ordinaryMove)
            )
            decisions += decision
            if (decision.relevance != HnsItemRequestRelevance.PROVEN_IRRELEVANT) {
                limitations.add(CalcLimitation.HNS_ITEM_EFFECT_NOT_MODELLED)
            }
        }

        if (input.origin == CalcInputOrigin.LIVE_READ) {
            when (input.itemProvenance) {
                CalcItemProvenance.BATTLE_EFFECTIVE,
                CalcItemProvenance.PARTY_STORAGE -> {
                    val id = input.itemId
                    if (id == null || input.itemOutOfDomain ||
                        !com.dualdex.pokemon.hns.HnsItemRegistry.isInDomain(id)
                    ) {
                        limitations.add(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE)
                    } else {
                        classify(id)
                    }
                }
                CalcItemProvenance.UNKNOWN,
                CalcItemProvenance.MANUAL ->
                    limitations.add(CalcLimitation.HNS_EFFECTIVE_ITEM_UNREADABLE)
            }
            return
        }

        if (input.itemOutOfDomain) {
            limitations.add(CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE)
            return
        }
        val id = input.itemId
        if (id != null) {
            if (!com.dualdex.pokemon.hns.HnsItemRegistry.isInDomain(id)) {
                limitations.add(CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE)
            } else {
                classify(id)
            }
            return
        }
        val name = input.item?.takeIf { it.isNotBlank() } ?: return
        if (name.trim().equals("None", ignoreCase = true)) return
        // A manual name is only an identity once it resolves through the exact catalogue; the
        // numeric ID it resolves to is then the capability key, exactly as for a live read.
        val resolved = com.dualdex.pokemon.hns.HnsItemRegistry.resolveIdByName(name)
        if (resolved == null) {
            limitations.add(CalcLimitation.HNS_ITEM_IDENTITY_NOT_AUTHORITATIVE)
        } else {
            classify(resolved)
        }
    }

    /**
     * True when the selected H&S move is a single-hit ordinary move whose damage does not read item
     * state, false for any other pinned move, and null when the move is not in the pinned pack.
     * This is an OPERAND of item relevance only; it never clears the move's own gates.
     */
    private fun hnsOrdinaryMove(pack: GameDataPack, request: DamageCalculationRequest): Boolean? {
        val move = pack.getMoveByName(request.move.name) ?: return null
        return com.dualdex.pokemon.hns.HnsMoveMechanicsRegistry.classify(move.id).category ==
            com.dualdex.pokemon.hns.HnsMoveMechanicsCategory.ORDINARY_PROVEN_EQUIVALENT &&
            !com.dualdex.pokemon.hns.HnsMoveItemInteractionRegistry.classify(move.id).isItemDependent
    }

    /** The canonical spelling of [item] when the ADV pipeline models it, else null. */
    fun canonicalItem(item: String): String? =
        GEN3_MODELLED_ITEM_NAMES.firstOrNull { it.equals(item.trim(), ignoreCase = true) }

    /**
     * Rewrites ability, held-item and weather names to the exact spelling the engine matches
     * against.
     *
     * This is not cosmetic. The engine compares all three exactly:
     *  - ability and item names are compared as strings, so a differently-cased name is silently
     *    ignored;
     *  - `Field.hasWeather` is `weathers.includes(this.weather)`, so `"rain"` and `"RAIN"` are not
     *    Rain - they behave exactly like *no weather at all* while still passing a lenient check.
     *
     * Validating leniently and forwarding verbatim would therefore reproduce the singles/Singles
     * defect this policy exists to prevent: an approved request that the engine silently reads
     * differently. Called by [evaluate] for an authorised request only, so what reaches the engine
     * is the same value the verdict was computed from.
     */
    fun normaliseNames(ruleset: CalcRuleset, request: DamageCalculationRequest): DamageCalculationRequest {
        fun fix(input: CalcPokemonInput): CalcPokemonInput = when (ruleset) {
            CalcRuleset.VANILLA_GEN3 -> input.copy(
                ability = input.ability?.let { canonicalAbility(it) ?: it },
                item = input.item?.let { canonicalItem(it) ?: it }
            )
            CalcRuleset.HNS_2_0_5 -> input.copy(
                ability = if (input.abilityId != null) {
                    com.dualdex.pokemon.hns.HnsAbilityRegistry.canonicalTitleCaseName(input.abilityId)
                        ?: input.ability?.let { com.dualdex.pokemon.hns.HnsAbilityRegistry.canonicalTitleCaseName(it) ?: it }
                } else {
                    input.ability?.let { com.dualdex.pokemon.hns.HnsAbilityRegistry.canonicalTitleCaseName(it) ?: it }
                },
                // Gap C3: no H&S item's damage effect is modelled today. Every item that reaches
                // this point is either ITEM_NONE or a proven no-ordinary-damage item, so the engine
                // must receive no item at all. Forwarding the raw H&S source name could silently
                // match an unrelated ADV item name. A modelled item would be mapped explicitly here.
                //
                // Stripping the item is only safe because a move whose damage reads item state is
                // already refused by HNS_ITEM_DEPENDENT_MOVE_NOT_MODELLED in
                // collectHnsItemDependentMoveLimitation; this method is reached only for an
                // authorized request. See that gate for the interaction audit.
                item = com.dualdex.pokemon.hns.HnsItemRegistry.engineItemName(input.itemId)
            )
        }
        return request.copy(
            attacker = fix(request.attacker),
            defender = fix(request.defender),
            field = request.field.copy(
                weather = request.field.weather?.let { canonicalWeather(it) ?: it }
            )
        )
    }

    /**
     * The canonical spelling of [weather] when the generation III pipeline models it, else null.
     *
     * Lenient on input, canonical on output, for the reason in [normaliseNames].
     */
    fun canonicalWeather(weather: String): String? =
        MODELLED_WEATHER.firstOrNull { it.equals(weather.trim(), ignoreCase = true) }

    /**
     * Records the ability limitation for one authoritative H&S classification (Gap C4e).
     *
     * A pinch ability (`Overgrow`/`Blaze`/`Torrent`/`Swarm`) is conditionally supported:
     *  - for the defender it is irrelevant (pinch abilities only modify the holder's Attack);
     *  - for the attacker, if the effective move type does not match the boosted type, the
     *    ability is provably irrelevant and adds no blocker;
     *  - otherwise the 1/3-HP condition is live state: an authoritative observed HP/max HP pair
     *    clears it (the arithmetic is modelled), while an unobserved pair is refused precisely
     *    ([CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED]) rather than assumed inactive.
     *
     * Any non-pinch ability keeps the original rule: an unsupported classification blocks.
     */
    private fun collectHnsAbilityCapabilityLimitation(
        classification: com.dualdex.pokemon.hns.HnsAbilityEntry,
        isAttacker: Boolean,
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>,
        decisions: MutableList<HnsAbilityRequestDecision>
    ) {
        val pinchType = classification.abilityId?.let { HNS_PINCH_ABILITY_TYPES[it] }
        if (pinchType != null) {
            if (!isAttacker) return // defender pinch abilities never modify incoming damage
            val moveType = request.moveOverride?.type
            if (moveType == null) {
                // The effective move type could not be resolved from the pinned pack; the
                // ability's relevance cannot be proven, so fail closed on the ability.
                limitations.add(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED)
            } else if (!moveType.equals(pinchType, ignoreCase = true)) {
                // Provably irrelevant for this move's type.
            } else {
                val hp = request.hnsLiveBattleState?.attackerHp
                val maxHp = request.hnsLiveBattleState?.attackerMaxHp
                if (hp == null || maxHp == null || maxHp <= 0) {
                    limitations.add(CalcLimitation.HNS_ABILITY_CONDITION_UNVERIFIED)
                }
            }
            return
        }
        if (classification.category == com.dualdex.pokemon.hns.HnsAbilityCategory.UNSUPPORTED_DAMAGE_RELEVANT) {
            val side = if (isAttacker) HnsAbilitySide.ATTACKER else HnsAbilitySide.DEFENDER
            val decision = HnsAbilityContextPolicy.assess(
                abilityId = classification.abilityId ?: -1,
                context = HnsAbilityContextPolicy.contextForRequest(request, side)
            )
            decisions += decision
            if (decision.relevance != HnsAbilityRequestRelevance.PROVEN_IRRELEVANT) {
                limitations.add(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED)
            }
        } else if (!classification.category.isSupportedForDamage) {
            decisions += HnsAbilityRequestDecision(
                abilityId = classification.abilityId,
                abilityName = classification.titleCaseName,
                side = if (isAttacker) HnsAbilitySide.ATTACKER else HnsAbilitySide.DEFENDER,
                globalCategory = classification.category,
                relevance = HnsAbilityRequestRelevance.UNKNOWN,
                rationale = "Unresolved global ability classification always fails closed."
            )
            limitations.add(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED)
        }
    }

    /**
     * Records the Gap C4e live-operand limitations for an active H&S battle.
     *
     * The boundary has already observed the operands ([CalcHnsLiveBattleState]); this decides
     * whether the observed values are usable:
     *  - an active dynamic-type retype (Electrify, or Ion Deluge on a Normal move) blocks;
     *  - an active defender Glaive Rush volatile blocks (x2 not modelled);
     *  - an unread gimmick blocks; an active gimmick blocks;
     *  - an unread or non-zero live attacker status blocks.
     */
    private fun collectHnsLiveOperandLimitations(
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>
    ) {
        val live = request.hnsLiveBattleState ?: return
        val staticType = request.moveOverride?.type
        val fieldStatuses = live.fieldStatuses
        val electrified = live.attackerElectrified
        // Explicit supported field-status mask. Only Ion Deluge is modelled; ANY other bit
        // (Wonder Room, Gravity, terrain, Mud/Water Sport, Trick/Magic Room, Fairy Lock) changes
        // ordinary damage or the defensive stat and must fail closed rather than clear the Ion
        // Deluge check. This is independent of the move type: the mere presence of an unmodelled
        // field status is disqualifying for the ordinary subset.
        if (fieldStatuses != null &&
            (fieldStatuses and HNS_SUPPORTED_FIELD_STATUS_MASK.inv()) != 0
        ) {
            limitations.add(CalcLimitation.HNS_FIELD_STATUS_NOT_MODELLED)
        }
        if (fieldStatuses != null && electrified != null && staticType != null) {
            val ionDelugeActive = (fieldStatuses and HNS_STATUS_FIELD_ION_DELUGE) != 0 &&
                staticType.equals("Normal", ignoreCase = true)
            if (electrified || ionDelugeActive) {
                limitations.add(CalcLimitation.HNS_DYNAMIC_MOVE_TYPE_ACTIVE_NOT_MODELLED)
            }
        }
        if (live.defenderGlaiveRush == true) {
            limitations.add(CalcLimitation.HNS_GLAIVE_RUSH_ACTIVE_NOT_MODELLED)
        }
        // Charge doubles an Electric move; only a positive timer on a relevant type is refused.
        // A zero timer is the observed neutral the first Ready subset requires.
        val chargeTimer = live.attackerChargeTimer
        if (chargeTimer != null && chargeTimer > 0 &&
            staticType != null && staticType.equals("Electric", ignoreCase = true)
        ) {
            limitations.add(CalcLimitation.HNS_CHARGE_ACTIVE_NOT_MODELLED)
        }
        // Tar Shot doubles a Fire move against the observed defender; an irrelevant move type
        // cannot be affected, so only the relevant positive case is refused.
        if (live.defenderTarShot == true &&
            staticType != null && staticType.equals("Fire", ignoreCase = true)
        ) {
            limitations.add(CalcLimitation.HNS_TAR_SHOT_ACTIVE_NOT_MODELLED)
        }
        val attackerGimmick = live.attackerGimmick
        val defenderGimmick = live.defenderGimmick
        if (attackerGimmick == null || defenderGimmick == null) {
            limitations.add(CalcLimitation.HNS_GIMMICK_STATE_UNREADABLE)
        } else if (attackerGimmick != 0 || defenderGimmick != 0) {
            limitations.add(CalcLimitation.HNS_GIMMICK_ACTIVE_NOT_MODELLED)
        }
        val status1 = live.attackerStatus1
        if (status1 == null || status1 != 0) {
            limitations.add(CalcLimitation.HNS_LIVE_STATUS_NOT_MODELLED)
        }
        // Live field conditions (Gap C4e correction). The boundary binds these from the observed
        // battle-global weather word and defender-side status word; an unread word must never be
        // treated as an observed neutral one.
        if (!live.weatherObserved) {
            limitations.add(CalcLimitation.HNS_LIVE_WEATHER_UNKNOWN)
        } else if (live.weatherWord and
            com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.B_WEATHER_MODELLED.inv() != 0
        ) {
            limitations.add(CalcLimitation.HNS_LIVE_WEATHER_NOT_MODELLED)
        }
        if (!live.defenderScreensObserved) {
            limitations.add(CalcLimitation.HNS_LIVE_SCREENS_UNKNOWN)
        } else if (live.defenderSideStatuses and
            com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.SIDE_STATUS_MODELLED.inv() != 0
        ) {
            limitations.add(CalcLimitation.HNS_LIVE_SIDE_STATUS_NOT_MODELLED)
        }
        // Persistent volatile state (review round 4). The first production subset does not model
        // any positive behavior of these states, so each observed-active class refuses with its own
        // precise limitation. Both battlers are checked: Foresight / Miracle Eye / grounding are
        // read on the defender, Gastro Acid suppresses either battler's effective ability, and
        // Roost changes either battler's effective types.
        val attackerPersistent = live.attackerPersistentVolatiles
        val defenderPersistent = live.defenderPersistentVolatiles
        if (attackerPersistent != null && defenderPersistent != null) {
            if (attackerPersistent.foresight || defenderPersistent.foresight) {
                limitations.add(CalcLimitation.HNS_FORESIGHT_ACTIVE_NOT_MODELLED)
            }
            if (attackerPersistent.miracleEye || defenderPersistent.miracleEye) {
                limitations.add(CalcLimitation.HNS_MIRACLE_EYE_ACTIVE_NOT_MODELLED)
            }
            if (attackerPersistent.root || defenderPersistent.root ||
                attackerPersistent.smackDown || defenderPersistent.smackDown ||
                attackerPersistent.telekinesis || defenderPersistent.telekinesis ||
                attackerPersistent.magnetRise || defenderPersistent.magnetRise
            ) {
                limitations.add(CalcLimitation.HNS_GROUNDING_VOLATILE_ACTIVE_NOT_MODELLED)
            }
            if (attackerPersistent.roostActive || defenderPersistent.roostActive) {
                limitations.add(CalcLimitation.HNS_ROOST_ACTIVE_NOT_MODELLED)
            }
            if (attackerPersistent.gastroAcid || defenderPersistent.gastroAcid) {
                limitations.add(CalcLimitation.HNS_ABILITY_SUPPRESSED_NOT_MODELLED)
            }
            if (attackerPersistent.substitute || defenderPersistent.substitute) {
                limitations.add(CalcLimitation.HNS_SUBSTITUTE_ACTIVE_NOT_MODELLED)
            }
            if (attackerPersistent.endured || defenderPersistent.endured) {
                limitations.add(CalcLimitation.HNS_ENDURED_ACTIVE_NOT_MODELLED)
            }
        }
    }

    /**
     * True when a vanilla Generation III request is a **Doubles** battle with an active Reflect or
     * Light Screen, the one screen shape whose cartridge arithmetic this pipeline does not
     * reproduce (see [CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED]).
     *
     * Both the physical screen (Reflect, read for a physical move) and the special screen (Light
     * Screen, read for a special move) are refused, and the check deliberately does not narrow by
     * move category: the request shape cannot express the cartridge's own target-presence
     * condition (`CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2`), so there is no input that
     * would make the reproduced branch provable.
     */
    private fun vanillaDoublesScreenNotModelled(request: DamageCalculationRequest): Boolean {
        if (!request.field.gameType.equals(CalcGameTypes.DOUBLES, ignoreCase = true)) return false
        val side = request.field.defenderSide ?: return false
        return side.isReflect || side.isLightScreen
    }

    /**
     * True when a vanilla Generation III request is a **Doubles** battle whose move the shipped
     * pipeline reduces as a spread move (see
     * [CalcLimitation.VANILLA_DOUBLES_SPREAD_NOT_MODELLED]).
     *
     * The cartridge's condition is `CountAliveMonsInBattle(BATTLE_ALIVE_DEF_SIDE) == 2`, which the
     * static request cannot carry: there is no target-presence operand, and for vanilla there is no
     * live reader that supplies one either. The request therefore fails closed whenever the move is
     * one the pipeline would reduce, which [Gen3DoublesSpreadMoves] enumerates from the pinned
     * source.
     *
     * The table is a superset-safe construction rather than a guess about the engine: the host
     * suite (`check_gen3_spread_move_table_matches_engine`) recovers every Generation III move's
     * pre-roll damage in Singles and Doubles and fails if the engine reduces any move this table
     * omits, so a table that drifted out of date cannot silently let a reduced move through.
     */
    private fun vanillaDoublesSpreadNotModelled(request: DamageCalculationRequest): Boolean {
        if (!request.field.gameType.equals(CalcGameTypes.DOUBLES, ignoreCase = true)) return false
        return Gen3DoublesSpreadMoves.isSpread(request.move.name)
    }

    private fun collectRequestLimitations(
        profile: RomHackProfile,
        capability: CalcCapability,
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>,
        abilityDecisions: MutableList<HnsAbilityRequestDecision>,
        itemDecisions: MutableList<HnsItemRequestDecision>
    ) {
        val pack = GameDataPackRegistry.getForProfile(profile)
        val ordinaryMove = if (capability.ruleset == CalcRuleset.HNS_2_0_5) hnsOrdinaryMove(pack, request) else null

        // The bridge selects content by name. If a name is not in this build's pinned data, the
        // engine would fall back to its own record and produce a confident number from another
        // game's base stats, typing, or base power.
        if (!pack.hasSpeciesByName(request.attacker.species)) {
            limitations.add(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA)
        }
        if (!pack.hasSpeciesByName(request.defender.species)) {
            limitations.add(CalcLimitation.SPECIES_NOT_IN_PINNED_DATA)
        }
        if (!pack.hasMoveByName(request.move.name)) {
            limitations.add(CalcLimitation.MOVE_NOT_IN_PINNED_DATA)
        }

        // Gap C3 item capability is contextual, not a property of the item alone. The static
        // item audit answers only whether an item's OWN hold effect touches the ordinary
        // damage path; a move whose damage semantics read item identity, presence or absence
        // is a separate interaction this calculator does not reproduce. The authorized request
        // strips supported items before the engine, which destroys exactly that input, so the
        // move must be refused here: before it reaches the engine and silently no-ops on the
        // wrong item state.
        if (capability.ruleset == CalcRuleset.HNS_2_0_5) {
            collectHnsItemDependentMoveLimitation(pack, request, limitations)
            collectHnsMoveMechanicsLimitation(pack, request, limitations)
        }

        // Field conditions the generation III pipeline cannot express. The engine accepts these
        // keys and then ignores the value, which would turn a Snow or terrain battle into a
        // confident no-weather, no-terrain number.
        request.field.weather?.takeIf { it.isNotBlank() }?.let { weather ->
            if (canonicalWeather(weather) == null) {
                limitations.add(CalcLimitation.FIELD_CONDITION_NOT_MODELLED)
            }
        }
        if (!request.field.terrain.isNullOrBlank()) {
            limitations.add(CalcLimitation.FIELD_CONDITION_NOT_MODELLED)
        }

        // Field conditions the generation III pipeline expresses with the wrong operation order.
        // The vanilla ruleset advertises VERIFIED, so a Doubles screen whose cartridge arithmetic
        // this pipeline does not reproduce must fail closed rather than be published as verified.
        if (capability.ruleset == CalcRuleset.VANILLA_GEN3 && vanillaDoublesScreenNotModelled(request)) {
            limitations.add(CalcLimitation.VANILLA_DOUBLES_SCREEN_NOT_MODELLED)
        }

        // The Doubles spread reduction has the same shape of problem: the games decide it from the
        // number of opposing battlers actually present, and this request cannot express that.
        if (capability.ruleset == CalcRuleset.VANILLA_GEN3 &&
            vanillaDoublesSpreadNotModelled(request)
        ) {
            limitations.add(CalcLimitation.VANILLA_DOUBLES_SPREAD_NOT_MODELLED)
        }

        listOf(request.attacker to true, request.defender to false).forEach { (input, isAttacker) ->
            if (capability.ruleset == CalcRuleset.HNS_2_0_5) {
                if (input.origin == CalcInputOrigin.LIVE_READ) {
                    if (input.unknownFields.contains(CalcInputField.ABILITY) || input.ability.isNullOrBlank() || input.abilityId == null) {
                        limitations.add(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE)
                    } else if (input.abilityId !in 0..com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.ABILITY_ID_MAX) {
                        limitations.add(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE)
                    } else {
                        // Authoritative numeric ability ID determines capability verdict,
                        // NEVER the display name string.
                        collectHnsAbilityCapabilityLimitation(
                            classification = com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(input.abilityId),
                            isAttacker = isAttacker,
                            request = request,
                            limitations = limitations,
                            decisions = abilityDecisions
                        )
                    }
                } else {
                    if (input.ability.isNullOrBlank()) {
                        limitations.add(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE)
                    } else {
                        val classification = if (input.abilityId != null) {
                            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(input.abilityId)
                        } else {
                            com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(input.ability)
                        }
                        collectHnsAbilityCapabilityLimitation(
                            classification = classification,
                            isAttacker = isAttacker,
                            request = request,
                            limitations = limitations,
                            decisions = abilityDecisions
                        )
                    }
                }

                collectHnsItemLimitation(input, isAttacker, request, ordinaryMove, limitations, itemDecisions)
            } else {
                input.ability?.takeIf { it.isNotBlank() }?.let { ability ->
                    if (!isAbilityModelled(capability.ruleset, ability)) {
                        limitations.add(CalcLimitation.ABILITY_NOT_MODELLED)
                    }
                }

                input.item?.takeIf { it.isNotBlank() }?.let { item ->
                    itemLimitation(capability.ruleset, item)?.let(limitations::add)
                }
            }

            input.boosts?.let { boosts ->
                if (listOf(boosts.atk, boosts.def, boosts.spa, boosts.spd, boosts.spe)
                        .any { it !in BOOST_RANGE }
                ) {
                    limitations.add(CalcLimitation.BOOSTS_OUT_OF_RANGE)
                }
            }
            input.ivs?.let { ivs ->
                if (listOf(ivs.hp, ivs.atk, ivs.def, ivs.spa, ivs.spd, ivs.spe)
                        .any { it !in IV_RANGE }
                ) {
                    limitations.add(CalcLimitation.STAT_VALUES_OUT_OF_RANGE)
                }
            }
            input.evs?.let { evs ->
                if (listOf(evs.hp, evs.atk, evs.def, evs.spa, evs.spd, evs.spe)
                        .any { it !in EV_RANGE }
                ) {
                    limitations.add(CalcLimitation.STAT_VALUES_OUT_OF_RANGE)
                }
            }
            if (input.level !in LEVEL_RANGE) {
                limitations.add(CalcLimitation.LEVEL_OUT_OF_RANGE)
            }
            input.status?.takeIf { it.isNotBlank() }?.let { status ->
                // Case-sensitive on purpose: the engine's status comparison is exact, so "BRN"
                // is not burn to it. It would count as *a* status and turn on Guts and Marvel
                // Scale while skipping the burn halving.
                if (status !in MODELLED_STATUSES) {
                    limitations.add(CalcLimitation.STATUS_NOT_MODELLED)
                }
            }
        }
    }

    private fun unsupportedReason(profile: RomHackProfile): String = when {
        profile.id.isBlank() -> "no active ROM profile; no calculator capability is documented"
        profile.customSpecies.isNotEmpty() ->
            "profile '${profile.id}' defines custom species; no calculator capability is documented for it"
        profile.engine.equals("Vanilla", ignoreCase = true) && profile.hasPhysSpecSplit ->
            "profile '${profile.id}' is a split-mechanics vanilla build; no calculator capability is documented for it"
        !profile.isVerified || !profile.memoryLayoutVerified ->
            "profile '${profile.id}' is not a verified build; no calculator capability is documented for it"
        else -> "profile '${profile.id}' has no documented calculator capability"
    }
}
