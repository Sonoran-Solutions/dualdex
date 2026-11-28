package com.dualdex.pokemon.unbound

import com.dualdex.pokemon.DeclaredAbility
import com.dualdex.pokemon.ItemDatabase
import com.dualdex.pokemon.GameDataPack
import com.dualdex.pokemon.MoveDatabase
import com.dualdex.pokemon.MoveInfo
import com.dualdex.pokemon.PokemonType
import com.dualdex.pokemon.SpeciesDatabase
import com.dualdex.pokemon.SpeciesInfo
import com.dualdex.pokemon.TypeChart
import com.dualdex.pokemon.hns.HnsAbilityRegistry

/**
 * Pinned data pack for the owner's exact Pokemon Unbound candidate (issue #26).
 *
 * Unbound stores CFRU-internal species and move IDs, not National Dex numbers, so nothing here
 * may fall back to a same-numbered global entry ([allowGlobalFallback] is false):
 * - Species: internal ID -> National Dex via the generated exact-ROM [UnboundSpeciesTable]. Only the
 *   "base" form class takes the shared database's name; other classes keep the base name plus
 *   [FORM_UNVERIFIED_SUFFIX], because exact form display names and stats are NOT_VERIFIED. Custom,
 *   reserved and out-of-range IDs return null, so [com.dualdex.pokemon.resolveSpecies] shows
 *   "Unknown Species #<internal ID>".
 * - Moves, ability IDs and held items: exact normalized identity through [UnboundNameTables],
 *   using bundled catalogue values for presentation. Unmapped IDs keep the existing placeholders.
 * - Species ability slots remain Absent: the name table does not establish slot declarations.
 *
 * Nothing here is authoritative: dex numbers are exact-ROM, but types, stats and move values are the
 * shared databases' presentation values, not Unbound data.
 */
object UnboundDataPack : GameDataPack {
    override val id: String = "unbound_exact"
    override val generation: Int = 8
    override val hasFairyType: Boolean = true
    override val hasStellarType: Boolean = false
    override val hasPhysicalSpecialSplit: Boolean = true
    override val allowGlobalFallback: Boolean = false

    const val FORM_UNVERIFIED_SUFFIX = " (form unverified)"

    override fun getSpecies(id: Int): SpeciesInfo? {
        if (id !in 0 until UnboundSpeciesTable.SPECIES_COUNT) return null
        val dex = UnboundSpeciesTable.NATIONAL_DEX[id]
        if (dex == 0) return null
        val base = SpeciesDatabase.getRaw(dex) ?: return null
        return if (UnboundSpeciesTable.FORM_CLASS[id] == UnboundSpeciesTable.FORM_BASE) {
            base.copy(id = id)
        } else {
            // A form's typing and stats differ from its base entry; keep only the base name.
            SpeciesInfo(id = id, name = base.name + FORM_UNVERIFIED_SUFFIX, type1 = PokemonType.NORMAL,
                baseHP = 0, baseAtk = 0, baseDef = 0, baseSpA = 0, baseSpD = 0, baseSpe = 0)
        }
    }

    override fun getMove(id: Int): MoveInfo? =
        UnboundNameTables.MOVE.getOrNull(id)?.takeIf { it > 0 }
            ?.let { MoveDatabase.getRaw(it)?.copy(id = id) }

    /** Numeric ability identity only; not a declaration of a species slot or effective state. */
    fun getAbility(id: Int): DeclaredAbility {
        val appId = UnboundNameTables.ABILITY.getOrNull(id)?.takeIf { it > 0 }
            ?: return DeclaredAbility.Absent
        val name = HnsAbilityRegistry.canonicalTitleCaseName(appId) ?: return DeclaredAbility.Absent
        return DeclaredAbility.Declared(id, name)
    }

    fun getItem(id: Int): ItemDatabase.ItemInfo {
        if (id <= 0) return ItemDatabase.ItemInfo(0, "None", "⚪")
        val appId = UnboundNameTables.ITEM.getOrNull(id)?.takeIf { it > 0 }
            ?: return ItemDatabase.ItemInfo(id, "Item #$id")
        return ItemDatabase.get(appId, isExpansion = true).copy(id = id)
    }

    // Names cannot be mapped back to internal species IDs without a reviewed inverse; refuse.
    override fun getSpeciesByName(name: String): SpeciesInfo? = null

    override fun getMoveByName(name: String): MoveInfo? {
        val appId = MoveDatabase.getByName(name)?.id ?: return null
        val internalId = UnboundNameTables.MOVE.indices.singleOrNull { UnboundNameTables.MOVE[it] == appId }
            ?: return null
        return getMove(internalId)
    }

    override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double =
        TypeChart.getEffectiveness(attackType, defType, steelResistsGhostDark = false).toDouble()
}
