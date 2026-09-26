package com.dualdex.pokemon.hns

/**
 * Capability classification of an ability under Heart & Soul 2.0.5 damage calculation (Gap C2).
 *
 * Each classification is grounded in an audit of the pinned H&S source
 * (`pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`) versus
 * the embedded calculator's Gen 3 (ADV) engine.
 */
enum class HnsAbilityCategory {
    /**
     * Proven from pinned H&S source to have no effect on damage calculation
     * (e.g. accuracy-only, status-prevention, or out-of-battle logic).
     */
    PROVEN_NO_DAMAGE_EFFECT,

    /**
     * Proven from pinned H&S source to have identical damage arithmetic, trigger conditions,
     * modifier placement, and integer/fixed-point rounding to the ADV engine.
     */
    MODELLED_EQUIVALENT,

    /**
     * Pinned H&S implements an ability-specific modifier that differs from ADV or requires
     * custom handling, successfully modelled specifically for H&S.
     */
    MODELLED_HNS_SPECIFIC,

    /**
     * Pinned H&S implements an ability-specific modifier that is modelled for H&S, but only when
     * its live condition is authoritatively observed (Gap C4e). The pinch abilities
     * (`Overgrow`/`Blaze`/`Torrent`/`Swarm`) are the current members: H&S applies their x1.5 as an
     * Attack-stat modifier when `hp <= maxHP/3` and the move type matches, so the policy must
     * either read the live HP or prove the ability irrelevant (wrong move type) before the
     * request may proceed.
     */
    MODELLED_HNS_CONDITIONAL,

    /**
     * Known damage-relevant ability in H&S whose mechanics are not faithfully modelled by
     * the current calculation engine (e.g. modern generation arithmetic divergence, unmodelled
     * type alteration, stat modification, etc.).
     */
    UNSUPPORTED_DAMAGE_RELEVANT,

    /**
     * Pinned ability deliberately unresolved, or an ID/name outside the pinned catalogue.
     * Fails closed.
     */
    UNCLASSIFIED;

    val isSupportedForDamage: Boolean
        get() = this == PROVEN_NO_DAMAGE_EFFECT || this == MODELLED_EQUIVALENT ||
            this == MODELLED_HNS_SPECIFIC || this == MODELLED_HNS_CONDITIONAL
}

/**
 * Registration entry for an audited H&S ability.
 */
data class HnsAbilityEntry(
    val abilityId: Int?,
    val canonicalName: String,
    val titleCaseName: String,
    val category: HnsAbilityCategory,
    val rationale: String
)

/**
 * Authoritative registry of investigated abilities for Heart & Soul 2.0.5 (Issue #9, Gap C2).
 *
 * Source: `pokehns-expansion` commit `1f42b74dff0e9fe942419845d040663dd829a973`.
 */
object HnsAbilityRegistry {

    private val entriesById = HashMap<Int, HnsAbilityEntry>()
    private val entriesByName = HashMap<String, HnsAbilityEntry>()

    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase().replace("_", " ").replace("-", " ")

    init {
        HnsAbilityAuditData.entries.forEach { entry ->
            require(entry.abilityId != null && entriesById.putIfAbsent(entry.abilityId, entry) == null) {
                "Duplicate H&S ability ID ${entry.abilityId}"
            }
            val symbolKey = normalizeKey(entry.canonicalName)
            require(entriesByName.putIfAbsent(symbolKey, entry) == null) {
                "Duplicate H&S ability symbol ${entry.canonicalName}"
            }
        }
        // Some distinct numeric abilities share a displayed name (for example As One).
        // Name-only lookup of those entries must remain unresolved; live reads use IDs.
        HnsAbilityAuditData.entries.groupBy { normalizeKey(it.titleCaseName) }.forEach { (key, matches) ->
            if (matches.size == 1) entriesByName.putIfAbsent(key, matches.single())
        }
    }

    /**
     * Classifies an ability by its display name (case-insensitive).
     * Returns an unclassified entry if not found.
     */
    fun classify(name: String?): HnsAbilityEntry {
        if (name == null || name.isBlank()) {
            return HnsAbilityEntry(
                abilityId = null,
                canonicalName = "",
                titleCaseName = "",
                category = HnsAbilityCategory.UNCLASSIFIED,
                rationale = "Ability name is null or blank."
            )
        }
        val key = normalizeKey(name)
        return entriesByName[key] ?: HnsAbilityEntry(
            abilityId = null,
            canonicalName = name.trim().uppercase(),
            titleCaseName = name.trim(),
            category = HnsAbilityCategory.UNCLASSIFIED,
            rationale = "Ability $name is not classified in H&S registry."
        )
    }

    /**
     * Classifies an ability by its numeric H&S ability ID.
     */
    fun classify(id: Int?): HnsAbilityEntry {
        if (id == null) {
            return HnsAbilityEntry(
                abilityId = null,
                canonicalName = "",
                titleCaseName = "",
                category = HnsAbilityCategory.UNCLASSIFIED,
                rationale = "Ability ID is null."
            )
        }
        return entriesById[id] ?: HnsAbilityEntry(
            abilityId = id,
            canonicalName = "ABILITY_$id",
            titleCaseName = "Ability $id",
            category = HnsAbilityCategory.UNCLASSIFIED,
            rationale = "Ability ID $id is not classified in H&S registry."
        )
    }

    /**
     * Returns the canonical TitleCase name if the ability is supported or recognized.
     */
    fun canonicalTitleCaseName(name: String): String? {
        val key = normalizeKey(name)
        return entriesByName[key]?.titleCaseName
    }

    /**
     * Returns the canonical TitleCase name by numeric ability ID.
     */
    fun canonicalTitleCaseName(id: Int?): String? =
        id?.let { entriesById[it]?.titleCaseName }

    /**
     * The complete pinned H&S 2.0.5 ability domain as `(id, TitleCase name)` pairs, ordered by ID.
     *
     * This is the exact domain the generated audit covers - every `enum Ability` identity,
     * `0 .. ABILITY_ID_MAX` - not only the abilities that happen to appear on a normal trainer
     * party. The issue #84 Random Abilities census needs the whole domain, because under Random
     * Abilities any ability can be installed on any battler.
     */
    fun pinnedAbilityDomain(): List<Pair<Int, String>> =
        entriesById.values
            .mapNotNull { entry -> entry.abilityId?.let { it to entry.titleCaseName } }
            .sortedBy { it.first }
}
