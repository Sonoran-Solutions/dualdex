package com.dualdex.calculator

import com.dualdex.pokemon.ItemDatabase
import com.dualdex.pokemon.ParsedPokemon

/**
 * Where a calculation participant's fields came from.
 *
 * This exists because "the field is absent" and "the field is present and neutral" are different
 * facts, and the engine cannot tell them apart: it treats an omitted status as no status, an omitted
 * boost set as stage 0, and an omitted ability as the *species'* first bundled ability.
 *
 * Without this distinction a live memory read that simply did not carry a field would be
 * indistinguishable from an observation that the field is neutral, and an exact-verified ROM would
 * promote that silence to "Verified". A burned attacker that drops its status while being prepared
 * computes as unburned, and an attacker with Guts computes as if it had no ability.
 */
enum class CalcInputOrigin {
    /** Entered by the user as a hypothetical matchup. Nothing was read from the running game. */
    MANUAL,

    /**
     * Read from the running game. Every damage-relevant field that the reader could not supply is
     * recorded as *unknown* rather than neutral, and an unknown field blocks a verified result.
     */
    LIVE_READ
}

/**
 * The damage-relevant state of one participant, with unknown fields represented explicitly.
 *
 * A null field means **unknown**, never "neutral". Use the neutral value ([CalcParticipantState.NONE])
 * when the state was actually observed to be neutral.
 */
data class CalcParticipantState(
    val species: String,
    val level: Int,
    val nature: String? = null,
    val item: String? = null,
    val ability: String? = null,
    val status: String? = null,
    val boosts: StatBlock? = null,
    val curHP: Int? = null,
    val ivs: StatBlock? = null,
    val evs: StatBlock? = null,
    val origin: CalcInputOrigin = CalcInputOrigin.MANUAL,
    /**
     * Fields this participant's reader could not supply, recorded at read time.
     *
     * Kept separately from "the value is null" because a null field can also mean "observed to be
     * neutral" (no status, no held item, stage 0). Only this list is evidence that a field was
     * genuinely unavailable.
     */
    val unknownFields: List<CalcInputField> = emptyList()
) {
    companion object {
        /**
         * The state of a participant that was observed to have no status, no boosts and no held
         * item. Used by manual entry, where the user asserts these values rather than reading them.
         */
        val NONE: StatBlock = StatBlock()
    }
}

/**
 * Result of preparing a calculation from observed participant state.
 *
 * [request] is always produced, so a caller can present the estimate; [unknownLiveFields] records
 * what a live read did not carry, and [preparationLimitations] carries the limitations that follow.
 * The boundary turns those into a refusal for a verified result, which is the point: preparation
 * never decides support, and support never treats silence as evidence.
 */
data class CalcParticipantPreparation(
    val request: DamageCalculationRequest,
    val unknownLiveFields: List<CalcInputField>,
    val preparationLimitations: List<CalcLimitation>
)

/** A damage-relevant field of a participant. Used to name what a live read did not carry. */
enum class CalcInputField {
    NATURE, ITEM, ABILITY, STATUS, BOOSTS, CURRENT_HP, IVS, EVS;

    /** User-facing description of the field. */
    val displayName: String
        get() = when (this) {
            NATURE -> "nature"
            ITEM -> "held item"
            ABILITY -> "ability"
            STATUS -> "status condition"
            BOOSTS -> "stat stages"
            CURRENT_HP -> "current HP"
            IVS -> "IVs"
            EVS -> "EVs"
        }
}

/**
 * True when either participant's values came from a live memory read.
 *
 * The origin is carried per participant, so a caller cannot claim "this was manual" for a request
 * that contains a live read: the request itself is the evidence.
 */
fun DamageCalculationRequest.isFromLiveRead(): Boolean =
    attacker.origin == CalcInputOrigin.LIVE_READ || defender.origin == CalcInputOrigin.LIVE_READ

/**
 * Every damage-relevant field the live reads behind this request did not carry.
 *
 * Read from the request rather than from a side channel, so provenance cannot be dropped by a caller
 * that chooses a different boundary overload.
 */
fun DamageCalculationRequest.unknownLiveFields(): List<CalcInputField> {
    val fields = LinkedHashSet<CalcInputField>()
    listOf(attacker, defender).forEach { participant ->
        if (participant.origin == CalcInputOrigin.LIVE_READ) fields += participant.unknownFields
    }
    return fields.toList()
}

/**
 * Outcome of resolving the authoritative live effective ability for a calculation participant (issue #9).
 *
 * Distinct states:
 * - [ObservedAbility]: Live engine currently uses this named ability;
 * - [ObservedNone]: Live engine explicitly has ABILITY_NONE (0);
 * - [UnknownAbility]: Ability could not be authoritatively resolved (mismatching slot, bench, faint, etc.).
 */
sealed class EffectiveAbilityResolution {
    data class ObservedAbility(val name: String) : EffectiveAbilityResolution()
    object ObservedNone : EffectiveAbilityResolution()
    object UnknownAbility : EffectiveAbilityResolution()
}

/**
 * The single production transformation from parsed participant state to a calculation request.
 *
 * Both the Calc screen and the regression tests drive this function, so the tests exercise the
 * production path rather than a hand-authored request that happens to look like it.
 *
 * The rules it enforces:
 *  - A LIVE_READ participant carries every field the reader supplied, plus an explicit record of
 *    every damage-relevant field it did not. Unknown fields are never shortened to neutral.
 *  - A LIVE_READ participant's ability is recorded as unknown unless an authoritative live battle
 *    observation is supplied. The generic party parser supplies only an ability slot, not an
 *    effective ability; Heart & Soul 2.0.5 has a separate authoritative battle-state reader (PR #56),
 *    and only that exact-trusted runtime observation from gBattleMons may fill the live effective ability.
 *    Vanilla builds have no live runtime ability reader, and the library's own species default is
 *    evidence about the species, not about this Pokemon.
 *  - A manual participant keeps whatever the caller supplied, because the caller is asserting those
 *    values rather than reading them, and the screen's benchmark defaults are part of that
 *    assertion.
 */
object CalcInputPreparation {

    /**
     * Prepare a request for one participant against [defender].
     *
     * [attacker] and [defender] are already-resolved states. Use [fromParsed] to build one from a
     * live read.
     */
    fun prepare(
        attacker: CalcParticipantState,
        defender: CalcParticipantState,
        move: CalcMoveInput,
        field: CalcFieldInput,
        gen: Int = 3
    ): CalcParticipantPreparation {
        val unknown = LinkedHashSet<CalcInputField>()
        val limitations = LinkedHashSet<CalcLimitation>()

        listOf(attacker, defender).forEach { participant ->
            if (participant.origin == CalcInputOrigin.LIVE_READ) {
                unknown += participant.unknownLiveFields()
            }
        }

        if (unknown.isNotEmpty()) {
            limitations.add(CalcLimitation.LIVE_PARTICIPANT_STATE_UNKNOWN)
        }

        return CalcParticipantPreparation(
            request = DamageCalculationRequest(
                gen = gen,
                attacker = attacker.toInput(),
                defender = defender.toInput(),
                move = move,
                field = field,
                // Carried on the request so the policy cannot evaluate the inputs without seeing
                // what the preparation already knew about them.
                preparationLimitations = limitations.toList()
            ),
            unknownLiveFields = unknown.toList(),
            preparationLimitations = limitations.toList()
        )
    }

    /**
     * Build a participant state from a live read of [parsed].
     *
     * The generic party reader reliably reports nature, level, IVs, EVs, current HP, the HP slot and
     * the held-item id. It reports an ability slot rather than an ability, and does not report stat
     * stages, so those are recorded as unknown unless the caller supplies authoritative live observations.
     *
     * [boosts] must be supplied only when the reader actually reports this participant's stat
     * stages; pass null when they were not observed. A null is recorded as unknown, which is what
     * stops an observed-but-unread stage change from being calculated as neutral.
     *
     * [effectiveAbility] must be supplied only from an authoritative live observation (e.g. H&S
     * `gBattleMons[battler].ability` for a slot-matching active battler); pass [EffectiveAbilityResolution.UnknownAbility]
     * when the ability was not read or when slot matching fails.
     */
    fun fromParsed(
        parsed: ParsedPokemon,
        speciesName: String,
        boosts: StatBlock? = null,
        itemName: String? = null,
        isExpansionItems: Boolean = false,
        effectiveAbility: EffectiveAbilityResolution = EffectiveAbilityResolution.UnknownAbility
    ): CalcParticipantState {
        val resolvedItem = itemName
            ?: if (parsed.heldItem > 0) {
                ItemDatabase.get(parsed.heldItem, isExpansionItems).name
            } else {
                // An empty held-item slot is an observation, not a gap: the Pokemon holds nothing.
                NO_ITEM
            }

        val (resolvedAbility, abilityUnknown) = when (effectiveAbility) {
            is EffectiveAbilityResolution.ObservedAbility -> effectiveAbility.name to false
            is EffectiveAbilityResolution.ObservedNone -> "None" to false
            is EffectiveAbilityResolution.UnknownAbility -> null to true
        }

        return CalcParticipantState(
            species = speciesName,
            level = parsed.level,
            nature = parsed.natureName.takeIf { it.isNotBlank() },
            item = resolvedItem,
            ability = resolvedAbility,
            status = statusNameOf(parsed),
            boosts = boosts,
            curHP = parsed.currentHp.takeIf { it > 0 },
            ivs = StatBlock(
                hp = parsed.hpIv,
                atk = parsed.attackIv,
                def = parsed.defenseIv,
                spa = parsed.spAttackIv,
                spd = parsed.spDefenseIv,
                spe = parsed.speedIv
            ),
            evs = StatBlock(
                hp = parsed.hpEv,
                atk = parsed.attackEv,
                def = parsed.defenseEv,
                spa = parsed.spAttackEv,
                spd = parsed.spDefenseEv,
                spe = parsed.speedEv
            ),
            origin = CalcInputOrigin.LIVE_READ,
            // The generic party reader supplies only an ability slot. Only an exact-trusted H&S
            // battle-state reader (gBattleMons) can name the live effective ability; when none was
            // supplied, or when stat stages were not observed, those fields are genuinely uncarried.
            unknownFields = buildList {
                if (abilityUnknown) add(CalcInputField.ABILITY)
                if (boosts == null) add(CalcInputField.BOOSTS)
                // A non-zero condition matching no known bit is lost information, not a healthy
                // Pokemon, so it is recorded as unknown as well as sent for rejection.
                if (statusNameOf(parsed) == UNKNOWN_STATUS) add(CalcInputField.STATUS)
            }
        )
    }

    /**
     * The status string a live read carries: a real status name, null when the Pokemon has none, or
     * [UNKNOWN_STATUS] for a non-zero condition matching no known bit.
     *
     * The bit layout is the one the battle console already uses for the same purpose. A condition
     * that matches none of these bits is reported as unknown rather than as no status, so it is
     * refused instead of being computed as healthy.
     */
    fun statusNameOf(parsed: ParsedPokemon): String? = when {
        parsed.statusCondition == 0L -> null
        (parsed.statusCondition and (1L shl 7)) != 0L -> "tox"
        (parsed.statusCondition and (1L shl 3)) != 0L -> "psn"
        (parsed.statusCondition and (1L shl 4)) != 0L -> "brn"
        (parsed.statusCondition and (1L shl 5)) != 0L -> "frz"
        (parsed.statusCondition and (1L shl 6)) != 0L -> "par"
        (parsed.statusCondition and 0x7L) != 0L -> "slp"
        else -> UNKNOWN_STATUS
    }

    /** The sentinel a live read uses for an empty held-item slot. */
    const val NO_ITEM: String = "None"

    /**
     * Returned by [statusNameOf] for a non-zero condition that matches no known bit. It is
     * deliberately not a real status name, so the policy refuses it instead of computing it.
     */
    const val UNKNOWN_STATUS: String = "unrecognised-status-bits"

    private fun CalcParticipantState.toInput(): CalcPokemonInput = CalcPokemonInput(
        species = species,
        level = level,
        item = item?.takeIf { it != NO_ITEM },
        nature = nature,
        ability = ability,
        curHP = curHP,
        ivs = ivs,
        evs = evs,
        boosts = boosts,
        // [UNKNOWN_STATUS] is deliberately PRESERVED here. Substituting null for it would assert
        // "this Pokemon has no status", which is the unknown-versus-neutral confusion this class
        // exists to prevent - and the engine would then compute a healthy Pokemon. The sentinel is
        // not one of the modelled status names, so the policy refuses it.
        status = status,
        origin = origin,
        unknownFields = unknownFields
    )

    /**
     * The damage-relevant fields a live read did not carry.
     *
     * [CalcParticipantState.unknownFields] is the authoritative record, populated at read time by
     * [fromParsed]. A null field is deliberately NOT treated as a gap here: null means the reader
     * observed nothing there (no status, no held item) and such a field is an observation of
     * neutrality, not an absence of evidence. Only fields the reader could not supply are reported.
     */
    private fun CalcParticipantState.unknownLiveFields(): List<CalcInputField> =
        if (origin == CalcInputOrigin.LIVE_READ) unknownFields else emptyList()
}
