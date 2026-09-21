package com.dualdex.calculator

data class CalcPokemonInput(
    val species: String,
    val level: Int = 50,
    val item: String? = null,
    val nature: String? = null,
    val ability: String? = null,
    val curHP: Int? = null,
    val ivs: StatBlock? = null,
    val evs: StatBlock? = null,
    val boosts: StatBlock? = null,
    val status: String? = null,
    /**
     * Where these values came from. A live memory read must never be able to pass for a complete
     * observation: the engine cannot distinguish an omitted status from a neutral one, so the
     * capability policy needs the origin to decide whether silence may be reported as verified.
     *
     * Defaults to [CalcInputOrigin.MANUAL] for callers that are asserting values rather than
     * reading them.
     */
    val origin: CalcInputOrigin = CalcInputOrigin.MANUAL,
    /**
     * Damage-relevant fields this participant's source could not supply, carried on the request so
     * the boundary can evaluate completeness without a side channel.
     *
     * An empty list means nothing was known to be missing; it is not a claim that the values are
     * complete. Only a reader that reports what it could not carry populates this.
     */
    val unknownFields: List<CalcInputField> = emptyList(),
    /**
     * Party slot provenance for live reads (0-indexed party slot, or null if manual/unknown).
     * Used by [CalcRequestBoundary] to verify that live runtime observations match this
     * specific participant rather than another party member or stale battler.
     */
    val partySlot: Int? = null,
    /**
     * Authoritative numeric ability ID for Heart & Soul 2.0.5 live reads (or null if manual/unknown).
     * Used by [CalcCapabilityPolicy] to determine ability capability directly from the authoritative
     * runtime ID rather than from a display name string.
     */
    val abilityId: Int? = null,
    /**
     * Authoritative numeric item ID for Heart & Soul 2.0.5 (or null if manual/unknown). For a live
     * read this is the engine's current held-item identity (`gBattleMons[battler].item`) when the
     * participant is the active battler, or the exact parsed party held-item ID for a bench
     * participant. Capability is decided from this numeric ID, never from [item].
     */
    val itemId: Int? = null,
    /** Where [itemId] came from; only [CalcItemProvenance.BATTLE_EFFECTIVE] is current battle state. */
    val itemProvenance: CalcItemProvenance = CalcItemProvenance.MANUAL,
    /** True when an observed item ID was outside the exact H&S item domain. */
    val itemOutOfDomain: Boolean = false
)

data class StatBlock(
    val hp: Int = 0,
    val atk: Int = 0,
    val def: Int = 0,
    val spa: Int = 0,
    val spd: Int = 0,
    val spe: Int = 0
)

data class CalcMoveInput(
    val name: String,
    val isCrit: Boolean = false
)

data class SideConditions(
    val isReflect: Boolean = false,
    val isLightScreen: Boolean = false
)

/**
 * Canonical `@smogon/calc` battle-format identifiers.
 *
 * The embedded engine compares `field.gameType` case-sensitively against
 * `'Singles'` / `'Doubles'` (its own `Field` default is `'Singles'`). Sending
 * the lowercase spelling selected the doubles damage path, which halved Gen III
 * spread moves such as Rock Slide in single battles (issue #29). Build requests
 * from these constants; the bundle boundary additionally normalises legacy
 * lowercase input and rejects unknown formats.
 */
object CalcGameTypes {
    const val SINGLES = "Singles"
    const val DOUBLES = "Doubles"
}

data class CalcFieldInput(
    val gameType: String = CalcGameTypes.SINGLES,
    val weather: String? = null,
    val terrain: String? = null,
    val defenderSide: SideConditions? = null
)

data class CalcSpeciesOverride(
    val baseStats: StatBlock,
    val types: List<String>
)

data class CalcMoveOverride(
    val basePower: Int,
    val type: String,
    val category: String? = null
)

/**
 * Calculator-owned immutable representation of Heart & Soul 2.0.5 runtime challenge rules.
 *
 * Resolved and owned by [CalcRequestBoundary] strictly from an authoritative, exact-trusted
 * [com.dualdex.pokemon.hns.HnsChallengeSettingsSnapshot].
 *
 * Source defaults are NEVER used: unobserved or out-of-domain fields are represented as
 * unknown ([com.dualdex.pokemon.hns.HnsOptionStyle.UNAVAILABLE] or `null`).
 */
data class CalcHnsRuntimeRules(
    val optionStyle: com.dualdex.pokemon.hns.HnsOptionStyle = com.dualdex.pokemon.hns.HnsOptionStyle.UNAVAILABLE,
    val fairyTypesEnabled: Boolean? = null,
    val randomTypesEnabled: Boolean? = null,
    val randomTypeEffectivenessEnabled: Boolean? = null,
    // Remaining value-changing / behaviour-changing challenge settings (Gap C4a). Each is the
    // directly observed raw source value (multi-bit fields) or boolean meaning (1-bit fields), or
    // null when the field was unobserved / out of the pinned source's domain. Source defaults are
    // never substituted: null means "not known", and the policy decides whether it must block.
    /** `tx_Random_Abilities` (1-bit). Effect captured downstream by the observed effective ability. */
    val randomAbilitiesEnabled: Boolean? = null,
    /** `tx_Random_Moves` (1-bit). Active blocks: the current learned move is not authoritative. */
    val randomMovesEnabled: Boolean? = null,
    /** `tx_Challenges_NoEvs` (1-bit). Effect captured downstream by the observed EV values. */
    val noEvsEnabled: Boolean? = null,
    /** `tx_Challenges_BaseStatEqualizer` raw index into the 0/100/255/500 table. Nonzero blocks. */
    val baseStatEqualizerMode: Int? = null,
    /** `tx_Challenges_Mirror` (1-bit). Effect captured downstream by the observed party. */
    val mirrorEnabled: Boolean? = null,
    /** `tx_Challenges_Mirror_Thief` (1-bit). Effect captured downstream by the observed party. */
    val mirrorThiefEnabled: Boolean? = null,
    /** `tx_Challenges_TrainerScalingIVs` raw mode. Effect captured downstream by observed IVs. */
    val trainerScalingIvsMode: Int? = null,
    /** `tx_Challenges_TrainerScalingEVs` raw mode. Effect captured downstream by observed EVs. */
    val trainerScalingEvsMode: Int? = null,
    /** `tx_Challenges_MaxPartyIVs` raw mode. Effect captured downstream by observed IVs. */
    val maxPartyIvsMode: Int? = null,
    /** `tx_Mode_Sturdy` (1-bit). Only reachable through the Sturdy ability, which is blocked. */
    val sturdyEnabled: Boolean? = null,
    /** `tx_Challenges_LevelCap` raw mode. Effect captured downstream by the observed level. */
    val levelCapMode: Int? = null,
    /** `tx_Challenges_ExpMultiplier` raw mode. Effect captured downstream by the observed level. */
    val expMultiplierMode: Int? = null,
    /** `tx_Mode_Legendary_Abilities` (1-bit). Effect captured downstream by the observed ability. */
    val legendaryAbilitiesEnabled: Boolean? = null
)

/**
 * Authoritative live H&S battle state that can differ from the static request operands (Gap C4a R1).
 *
 * H&S mutates damage operands during battle: `SET_BATTLER_TYPE` rewrites current effective types
 * (Soak), Power Trick swaps the raw `gBattleMons` battle stat words, and `SetTypeBeforeUsingMove`
 * can force the current move's type to Electric (Ion Deluge / Electrify) without changing its
 * static effect ID. The static species/move request is therefore not the live truth.
 *
 * Bound by [CalcRequestBoundary] from the exact-trusted runtime observation. A null
 * [DamageCalculationRequest.hnsLiveBattleState] means the request does not claim to be an active
 * battle (manual hypothetical, or a live read of stored party data), so the live-battle-state gate
 * does not apply. When it is present, every mutable class must be authoritatively observed:
 *
 *  - [attackerTypes] / [defenderTypes]: the engine's current effective types for that participant,
 *    or null when unobserved. A third non-empty type, an out-of-domain/typeless value, or a set
 *    that differs from the static record cannot be represented by the two-type calculator, so it
 *    blocks rather than silently using the static typing.
 *  - [attackerBattleStatWordsObserved] / [defenderBattleStatWordsObserved]: true only when an
 *    authoritative observation of the engine's current raw stat words exists. No runtime reader
 *    produces this yet (Gap C4b), so it is false in production.
 *  - [dynamicMoveTypeObserved]: true only when the current move's effective type was authoritatively
 *    observed. No runtime reader produces this yet (Gap C4b).
 *  - [transientStateObserved]: true only when other transient damage state reachable by the
 *    supported ordinary subset was authoritatively observed. No runtime reader produces this yet.
 *  - [moveTargetCount]: the authoritative number of currently present targets
 *    (`GetMoveTargetCount(ctx)`), or null when unobserved. H&S halves a spread move only when this
 *    is exactly 2, so a Doubles spread move without an observed count fails closed rather than
 *    guessing from `field.gameType`. No runtime reader produces this yet (Gap C4b).
 */
data class CalcRawStats(
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val spAttack: Int,
    val spDefense: Int
)

data class CalcBadgeBoosts(
    val atk: Boolean = false,
    val def: Boolean = false,
    val spe: Boolean = false,
    val spa: Boolean = false,
    val spd: Boolean = false
)

data class CalcHnsLiveBattleState(
    val attackerTypes: List<String>? = null,
    val defenderTypes: List<String>? = null,
    val attackerBattleStatWordsObserved: Boolean = false,
    val defenderBattleStatWordsObserved: Boolean = false,
    val dynamicMoveTypeObserved: Boolean = false,
    val transientStateObserved: Boolean = false,
    val attackerRawStats: CalcRawStats? = null,
    val defenderRawStats: CalcRawStats? = null,
    val attackerStatStages: List<Int>? = null,
    val defenderStatStages: List<Int>? = null,
    val attackerBadgeBoosts: CalcBadgeBoosts? = null,
    val defenderBadgeBoosts: CalcBadgeBoosts? = null,
    /**
     * The authoritative number of currently present targets for the selected move
     * (`GetMoveTargetCount(ctx)`), or null when unobserved. Only H&S Doubles spread moves read
     * this: the Gen-III reduction applies when the count is exactly 2. No runtime reader supplies
     * it yet, so the production boundary always leaves it null and the policy fails closed.
     */
    val moveTargetCount: Int? = null
)

data class DamageCalculationRequest(
    val gen: Int = 3,
    val typeSystem: String? = null,
    val attacker: CalcPokemonInput,
    val defender: CalcPokemonInput,
    val move: CalcMoveInput,
    val field: CalcFieldInput = CalcFieldInput(),
    /**
     * Limitations discovered while the request was prepared from participant state, before any
     * support decision was made. Carried on the request so the production preparation path and the
     * capability policy cannot disagree about what the inputs actually cover.
     *
     * Defaults to empty for callers that assert complete values.
     */
    val preparationLimitations: List<CalcLimitation> = emptyList(),
    val attackerOverride: CalcSpeciesOverride? = null,
    val defenderOverride: CalcSpeciesOverride? = null,
    val moveOverride: CalcMoveOverride? = null,
    val hnsRuntimeRules: CalcHnsRuntimeRules? = null,
    /**
     * Authoritative live battle state for an active exact-H&S battle, or null when the request is
     * not an active battle (Gap C4a R1). Boundary-owned: [CalcRequestBoundary] overwrites any
     * caller-supplied value from the exact-trusted runtime observation.
     */
    val hnsLiveBattleState: CalcHnsLiveBattleState? = null
)

data class DamageCalculationResponse(
    val success: Boolean,
    val minDamage: Int = 0,
    val maxDamage: Int = 0,
    val range: List<Int> = emptyList(),
    val desc: String = "",
    val moveName: String = "",
    val moveCategory: String = "",
    val moveType: String = "",
    val movePower: Int = 0,
    val attackerName: String = "",
    val defenderName: String = "",
    val defenderMaxHP: Int = 0,
    val koChanceText: String = "",
    val effectiveness: Double? = null,
    val error: String? = null
)
