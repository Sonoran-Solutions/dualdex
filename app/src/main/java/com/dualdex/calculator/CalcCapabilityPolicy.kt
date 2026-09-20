package com.dualdex.calculator

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
     * The Heart & Soul 2.0.5 held-item system is not modelled by the calculator pipeline (Gap C3).
     */
    HNS_HELD_ITEM_SYSTEM_NOT_MODELLED(true),

    /**
     * The Heart & Soul 2.0.5 ability system is not modelled by the calculator pipeline (Gap C2).
     */
    @Deprecated("Superseded by HNS_EFFECTIVE_ABILITY_UNREADABLE, HNS_ABILITY_EFFECT_NOT_MODELLED, and HNS_HELD_ITEM_SYSTEM_NOT_MODELLED")
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
     * is active and has no equivalent in the request shape.
     */
    BADGE_BOOST_NOT_MODELLED(false),

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
    val unsupportedReason: String = ""
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
                alwaysLimitations = listOf(
                    // Gap C3 blocker: the Heart & Soul 2.0.5 held-item system is not modelled by the calculator (Gap C3).
                    CalcLimitation.HNS_HELD_ITEM_SYSTEM_NOT_MODELLED,
                    CalcLimitation.BADGE_BOOST_NOT_MODELLED,
                    CalcLimitation.BUILDS_NOT_HASH_VERIFIED
                ),
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

        // Limitations the production preparation path already discovered are part of the decision,
        // not a separate opinion: an incomplete live read must be able to lower the verdict even
        // when every supplied field looks fine.
        limitations.addAll(request.preparationLimitations)

        if (request.gen != capability.mechanicsGeneration) {
            limitations.add(CalcLimitation.MECHANICS_GENERATION_MISMATCH)
        }

        collectRequestLimitations(profile, capability, request, limitations)

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
            }
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
     * The limitation a held item adds for [ruleset], or null when the item's damage effect is
     * faithfully represented.
     */
    fun itemLimitation(ruleset: CalcRuleset, item: String): CalcLimitation? = when (ruleset) {
        // H&S 2.0.5 item ids are expansion ids and its item table is not authoritative, so no item
        // name may be trusted for that build yet.
        CalcRuleset.HNS_2_0_5 -> CalcLimitation.HELD_ITEM_DATA_NOT_AUTHORITATIVE
        CalcRuleset.VANILLA_GEN3 ->
            if (canonicalItem(item) != null) null else CalcLimitation.ITEM_NOT_MODELLED
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
                }
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

    private fun collectRequestLimitations(
        profile: RomHackProfile,
        capability: CalcCapability,
        request: DamageCalculationRequest,
        limitations: MutableSet<CalcLimitation>
    ) {
        val pack = GameDataPackRegistry.getForProfile(profile)

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

        listOf(request.attacker, request.defender).forEach { input ->
            if (capability.ruleset == CalcRuleset.HNS_2_0_5) {
                if (input.origin == CalcInputOrigin.LIVE_READ) {
                    if (input.unknownFields.contains(CalcInputField.ABILITY) || input.ability.isNullOrBlank() || input.abilityId == null) {
                        limitations.add(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE)
                    } else if (input.abilityId !in 0..com.dualdex.pokemon.hns.HnsBattlerRuntimeStateIds.ABILITY_ID_MAX) {
                        limitations.add(CalcLimitation.HNS_EFFECTIVE_ABILITY_UNREADABLE)
                    } else {
                        // Authoritative numeric ability ID determines capability verdict,
                        // NEVER the display name string.
                        val classification = com.dualdex.pokemon.hns.HnsAbilityRegistry.classify(input.abilityId)
                        if (!classification.category.isSupportedForDamage) {
                            limitations.add(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED)
                        }
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
                        if (!classification.category.isSupportedForDamage) {
                            limitations.add(CalcLimitation.HNS_ABILITY_EFFECT_NOT_MODELLED)
                        }
                    }
                }

                input.item?.takeIf { it.isNotBlank() }?.let { item ->
                    itemLimitation(capability.ruleset, item)?.let(limitations::add)
                }
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
