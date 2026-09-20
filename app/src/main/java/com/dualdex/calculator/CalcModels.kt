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
    val unknownFields: List<CalcInputField> = emptyList()
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
    val randomTypeEffectivenessEnabled: Boolean? = null
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
    val hnsRuntimeRules: CalcHnsRuntimeRules? = null
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
