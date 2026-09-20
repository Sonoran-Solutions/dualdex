package com.dualdex.calculator

import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.GameDataPackRegistry
import com.dualdex.pokemon.hasMoveByName
import com.dualdex.pokemon.hasSpeciesByName
import com.dualdex.pokemon.hns.HeartAndSoul205DataPack
import com.dualdex.romhack.RomHackProfile

/**
 * Builds authoritative calculator species and move overrides from a [GameDataPack] or active [RomHackProfile].
 *
 * For exact Heart & Soul 2.0.5, this extracts authoritative base stats, types, base power, and category
 * directly from [HeartAndSoul205DataPack] so that `@smogon/calc` consumes the hack's exact records rather than
 * substituting its own generation table entries.
 *
 * For vanilla FireRed / Emerald, this returns null so vanilla calculations remain a clean no-op without
 * injecting later-generation records.
 *
 * Ambiguous form names (which resolve to null in [HeartAndSoul205DataPack.getSpeciesByName]) produce null
 * and fail closed.
 */
object CalcDataOverrides {

    /**
     * Builds a [CalcSpeciesOverride] for [speciesName] using [dataPack], or returns null if the pack
     * does not authoritatively supply overrides for this species.
     */
    fun buildSpeciesOverride(speciesName: String, dataPack: GameDataPack): CalcSpeciesOverride? {
        // Vanilla Gen 3 requests do not use overrides (preserves verified vanilla behavior)
        if (dataPack !is HeartAndSoul205DataPack) return null

        if (!dataPack.hasSpeciesByName(speciesName)) return null
        val info = dataPack.getSpeciesByName(speciesName) ?: return null
        if (!dataPack.isSpeciesAuthoritative(info.id)) return null

        // Canonical type mapping by display name, never raw integer ID.
        // Single-type species produce [type1.displayName]; TYPE_NONE/null is omitted and never mapped to Normal.
        val types = mutableListOf(info.type1.displayName)
        info.type2?.let { t2 ->
            types.add(t2.displayName)
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
     * Builds a [CalcMoveOverride] for [moveName] using [dataPack], or returns null if the pack
     * does not authoritatively supply overrides for this move.
     */
    fun buildMoveOverride(moveName: String, dataPack: GameDataPack): CalcMoveOverride? {
        if (dataPack !is HeartAndSoul205DataPack) return null

        if (!dataPack.hasMoveByName(moveName)) return null
        val info = dataPack.getMoveByName(moveName) ?: return null
        if (!dataPack.isMoveAuthoritative(info.id)) return null

        return CalcMoveOverride(
            basePower = info.power,
            type = info.type.displayName,
            category = info.category.displayName
        )

    }

    /**
     * Enriches [request] with authoritative species and move overrides for [profile], taking into
     * account boundary-resolved [hnsRuntimeRules].
     *
     * Overrides and runtime rules are strictly boundary-owned: caller-supplied overrides and rules
     * are ALWAYS discarded.
     * - For non-H&S / vanilla profiles, any supplied overrides or rules are stripped to null so external
     *   callers cannot inject arbitrary stat, move power, or rule changes into a VERIFIED request.
     * - For H&S, overrides are rebuilt ONLY from [HeartAndSoul205DataPack]. If a species is ambiguous
     *   (e.g. multi-form "Eevee") or missing, the override is null and cannot be smuggled in.
     * - When [CalcHnsRuntimeRules.optionStyle] is [com.dualdex.pokemon.hns.HnsOptionStyle.TYPE_BASED],
     *   the move override category is omitted (null) so the engine derives category from move type.
     *   When [com.dualdex.pokemon.hns.HnsOptionStyle.PER_MOVE_SPLIT], the pinned per-move category is retained.
     */
    fun enrichRequest(
        profile: RomHackProfile,
        request: DamageCalculationRequest,
        hnsRuntimeRules: CalcHnsRuntimeRules? = null
    ): DamageCalculationRequest {
        val pack = GameDataPackRegistry.getForProfile(profile)
        if (pack !is HeartAndSoul205DataPack) {
            // Overrides are boundary-owned: callers cannot inject overrides or rules into non-H&S / vanilla profiles.
            return if (request.attackerOverride != null || request.defenderOverride != null || request.moveOverride != null || request.hnsRuntimeRules != null) {
                request.copy(
                    attackerOverride = null,
                    defenderOverride = null,
                    moveOverride = null,
                    hnsRuntimeRules = null
                )
            } else {
                request
            }
        }

        // Overrides and runtime rules are boundary-owned: caller-supplied values are discarded
        // and rebuilt ONLY from HeartAndSoul205DataPack and boundary-resolved rules.
        val attackerOverride = buildSpeciesOverride(request.attacker.species, pack)
        val defenderOverride = buildSpeciesOverride(request.defender.species, pack)
        val rawMoveOverride = buildMoveOverride(request.move.name, pack)

        // optionStyle semantics:
        // - PER_MOVE_SPLIT (raw 0): move's own category decides Physical/Special (category retained from pack).
        // - TYPE_BASED (raw 1): move type decides Physical/Special as in Gen 3 (category omitted/null).
        // - UNAVAILABLE / unknown: category split is unreadable; calculation remains refused.
        val moveOverride = if (hnsRuntimeRules?.optionStyle == com.dualdex.pokemon.hns.HnsOptionStyle.TYPE_BASED) {
            rawMoveOverride?.copy(category = null)
        } else {
            rawMoveOverride
        }

        return request.copy(
            attackerOverride = attackerOverride,
            defenderOverride = defenderOverride,
            moveOverride = moveOverride,
            hnsRuntimeRules = hnsRuntimeRules
        )
    }
}
