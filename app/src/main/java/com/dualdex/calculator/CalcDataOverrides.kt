package com.dualdex.calculator

import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.MoveCategory
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.hasMoveByName
import com.dualdex.pokemon.hasSpeciesByName
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.pokemon.hns.HnsFairyTypeMappings
import com.dualdex.pokemon.hns.HnsOptionStyle
import com.dualdex.romhack.RomHackProfile

/**
 * Builds authoritative calculator species and move overrides from a [GameDataPack] or active [RomHackProfile].
 *
 * For exact Heart & Soul 2.0.5, this extracts authoritative base stats, types, base power, and category
 * directly from [HeartAndSoul205DataPack] so that `@smogon/calc` consumes the hack's exact records rather than
 * substituting its own generation table entries.
 *
 * When Fairy mode is disabled ([CalcHnsRuntimeRules.fairyTypesEnabled] is false), species typings revert
 * to their pre-Fairy typings ([HnsFairyTypeMappings.getPreFairyTypes]) and Fairy moves retype to their
 * alternate types ([HnsFairyTypeMappings.getFairyMoveAltType]).
 *
 * For vanilla FireRed / Emerald, this returns null so vanilla calculations remain a clean no-op without
 * injecting later-generation records.
 *
 * Ambiguous form names (which resolve to null in [HeartAndSoul205DataPack.getSpeciesByName]) produce null
 * and fail closed.
 */
object CalcDataOverrides {

    /**
     * Builds a [CalcSpeciesOverride] for [speciesName] using [dataPack], taking into account [fairyTypesEnabled].
     * Returns null if the pack does not authoritatively supply overrides for this species.
     */
    fun buildSpeciesOverride(
        speciesName: String,
        dataPack: GameDataPack,
        fairyTypesEnabled: Boolean? = true
    ): CalcSpeciesOverride? {
        // Vanilla Gen 3 requests do not use overrides (preserves verified vanilla behavior)
        if (dataPack !is HeartAndSoul205DataPack) return null

        if (!dataPack.hasSpeciesByName(speciesName)) return null
        val info = dataPack.getSpeciesByName(speciesName) ?: return null
        if (!dataPack.isSpeciesAuthoritative(info.id)) return null

        // Canonical type mapping by display name, never raw integer ID.
        // If Fairy mode is observed OFF (false), check for pre-Fairy species typing
        val types = if (fairyTypesEnabled == false) {
            val preFairy = HnsFairyTypeMappings.getPreFairyTypes(speciesName)
            if (preFairy != null) {
                preFairy
            } else {
                val packTypes = mutableListOf(info.type1.displayName)
                info.type2?.let { t2 -> packTypes.add(t2.displayName) }
                packTypes
            }
        } else {
            val packTypes = mutableListOf(info.type1.displayName)
            info.type2?.let { t2 -> packTypes.add(t2.displayName) }
            packTypes
        }

        return CalcSpeciesOverride(
            baseStats = StatBlock(
                hp = info.baseHP,
                atk = info.baseAtk,
                def = info.baseDef,
                spa = info.baseSpA,
                spd = info.baseSpD,
                spe = info.baseSpe
            ),
            types = types
        )
    }

    /**
     * Builds a [CalcMoveOverride] for [moveName] using [dataPack], taking into account [fairyTypesEnabled].
     * Returns null if the pack does not authoritatively supply overrides for this move.
     */
    fun buildMoveOverride(
        moveName: String,
        dataPack: GameDataPack,
        fairyTypesEnabled: Boolean? = true
    ): CalcMoveOverride? {
        if (dataPack !is HeartAndSoul205DataPack) return null

        if (!dataPack.hasMoveByName(moveName)) return null
        val info = dataPack.getMoveByName(moveName) ?: return null
        if (!dataPack.isMoveAuthoritative(info.id)) return null

        // If Fairy mode is observed OFF (false), Fairy moves are retyped according to sFairyMoveAltTypes
        val isFairy = info.type.displayName.equals("Fairy", ignoreCase = true)
        val moveType = if (fairyTypesEnabled == false && isFairy) {
            HnsFairyTypeMappings.getFairyMoveAltType(moveName, true) ?: "Normal"
        } else {
            info.type.displayName
        }

        return CalcMoveOverride(
            basePower = info.power,
            type = moveType,
            category = info.category.displayName
        )
    }

    /**
     * The boundary-owned H&S move override used for both authorization and presentation.
     * Under TYPE_BASED the category is intentionally omitted so the calculator derives it from
     * the effective move type, matching H&S `GetBattleMoveCategory`.
     */
    fun buildHnsMoveOverride(
        moveName: String,
        profile: RomHackProfile,
        hnsRuntimeRules: CalcHnsRuntimeRules?
    ): CalcMoveOverride? {
        val pack = GameDataPackRegistry.getForProfile(profile) as? HeartAndSoul205DataPack ?: return null
        val raw = buildMoveOverride(moveName, pack, hnsRuntimeRules?.fairyTypesEnabled) ?: return null
        val move = pack.getMoveByName(moveName) ?: return null
        return when {
            move.category == MoveCategory.STATUS -> raw
            hnsRuntimeRules?.optionStyle == HnsOptionStyle.TYPE_BASED ->
                raw.copy(category = null)
            else -> raw
        }
    }

    /** Resolve the presentation category from the same pinned override and runtime rule. */
    fun resolveHnsMoveCategory(
        moveName: String,
        profile: RomHackProfile,
        hnsRuntimeRules: CalcHnsRuntimeRules?
    ): MoveCategory? {
        val pack = GameDataPackRegistry.getForProfile(profile) as? HeartAndSoul205DataPack ?: return null
        val move = pack.getMoveByName(moveName) ?: return null
        if (move.category == MoveCategory.STATUS) return MoveCategory.STATUS
        val override = buildHnsMoveOverride(moveName, profile, hnsRuntimeRules) ?: return null
        return when (hnsRuntimeRules?.optionStyle) {
            HnsOptionStyle.PER_MOVE_SPLIT -> when (override.category?.lowercase()) {
                "physical" -> MoveCategory.PHYSICAL
                "special" -> MoveCategory.SPECIAL
                else -> null
            }
            HnsOptionStyle.TYPE_BASED ->
                PokemonType.fromString(override.type)?.let { type ->
                    when (type) {
                        PokemonType.NORMAL,
                        PokemonType.FIGHTING,
                        PokemonType.FLYING,
                        PokemonType.POISON,
                        PokemonType.GROUND,
                        PokemonType.ROCK,
                        PokemonType.BUG,
                        PokemonType.GHOST,
                        PokemonType.STEEL -> MoveCategory.PHYSICAL
                        else -> MoveCategory.SPECIAL
                    }
                }
            else -> null
        }
    }

    /**
     * Enriches [request] with authoritative species and move overrides for [profile], taking into
     * account boundary-resolved [hnsRuntimeRules].
     *
     * Overrides, runtime rules, and typeSystem are strictly boundary-owned: caller-supplied values
     * are ALWAYS discarded.
     * - For non-H&S / vanilla profiles, any supplied overrides, rules, or typeSystem are stripped to null so external
     *   callers cannot inject arbitrary stat, move power, rule, or type-chart changes into a VERIFIED request.
     * - For H&S, overrides are rebuilt ONLY from [HeartAndSoul205DataPack] and boundary-resolved rules.
     *   If a species is ambiguous (e.g. multi-form "Eevee") or missing, the override is null and cannot be smuggled in.
     *   `typeSystem` is set strictly to "hns_2_0_5".
     * - When [CalcHnsRuntimeRules.optionStyle] is [com.dualdex.pokemon.hns.HnsOptionStyle.TYPE_BASED],
     *   non-status move override category is omitted (null) so the engine derives category from the effective move type.
     *   Status moves retain "Status" in both modes (Status wins before optionStyle in H&S GetBattleMoveCategory).
     *   When [com.dualdex.pokemon.hns.HnsOptionStyle.PER_MOVE_SPLIT], the pinned per-move category is retained.
     */
    fun enrichRequest(
        profile: RomHackProfile,
        request: DamageCalculationRequest,
        hnsRuntimeRules: CalcHnsRuntimeRules? = null
    ): DamageCalculationRequest {
        val pack = GameDataPackRegistry.getForProfile(profile)
        if (pack !is HeartAndSoul205DataPack) {
            // Overrides and typeSystem are boundary-owned: callers cannot inject overrides, rules, or typeSystem
            // into non-H&S / vanilla profiles. Live battle state is likewise H&S-only.
            return if (request.attackerOverride != null || request.defenderOverride != null || request.moveOverride != null || request.hnsRuntimeRules != null || request.typeSystem != null || request.hnsLiveBattleState != null) {
                request.copy(
                    attackerOverride = null,
                    defenderOverride = null,
                    moveOverride = null,
                    hnsRuntimeRules = null,
                    typeSystem = null,
                    hnsLiveBattleState = null
                )
            } else {
                request
            }
        }

        // Overrides, runtime rules, and typeSystem are boundary-owned: caller-supplied values are discarded
        // and rebuilt ONLY from HeartAndSoul205DataPack and boundary-resolved rules.
        val fairyEnabled = hnsRuntimeRules?.fairyTypesEnabled
        val attackerOverride = buildSpeciesOverride(request.attacker.species, pack, fairyEnabled)
        val defenderOverride = buildSpeciesOverride(request.defender.species, pack, fairyEnabled)
        val moveOverride = buildHnsMoveOverride(request.move.name, profile, hnsRuntimeRules)

        return request.copy(
            typeSystem = "hns_2_0_5",
            attackerOverride = attackerOverride,
            defenderOverride = defenderOverride,
            moveOverride = moveOverride,
            hnsRuntimeRules = hnsRuntimeRules,
            // Boundary-owned exactly like the rules above: stripped here and re-bound from the
            // exact-trusted runtime observation by CalcRequestBoundary.
            hnsLiveBattleState = null
        )
    }
}
