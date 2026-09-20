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
     * Enriches [request] with authoritative species and move overrides for [profile] if not already present.
     */
    fun enrichRequest(profile: RomHackProfile, request: DamageCalculationRequest): DamageCalculationRequest {
        val pack = GameDataPackRegistry.getForProfile(profile)
        if (pack !is HeartAndSoul205DataPack) return request

        val attackerOverride = request.attackerOverride ?: buildSpeciesOverride(request.attacker.species, pack)
        val defenderOverride = request.defenderOverride ?: buildSpeciesOverride(request.defender.species, pack)
        val moveOverride = request.moveOverride ?: buildMoveOverride(request.move.name, pack)

        return request.copy(
            attackerOverride = attackerOverride,
            defenderOverride = defenderOverride,
            moveOverride = moveOverride
        )
    }
}
